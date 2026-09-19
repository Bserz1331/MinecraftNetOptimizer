package work.spacecat.twnetoptimizer.optimizer;

import org.bukkit.plugin.java.JavaPlugin;
import work.spacecat.twnetoptimizer.profiler.NetworkProfiler;
import work.spacecat.twnetoptimizer.profiler.ProfileSnapshot;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PacketOptimizationEngine {
    private static final Set<String> UI_STATE_PACKETS = Set.of(
            "BOSS_BAR",
            "SCOREBOARD_OBJECTIVE",
            "UPDATE_SCORE",
            "DISPLAY_SCOREBOARD",
            "TEAMS",
            "PLAYER_LIST_HEADER_AND_FOOTER"
    );

    private final JavaPlugin plugin;
    private final NetworkProfiler profiler;
    private final OptimizationStats stats = new OptimizationStats();

    private final Map<MetadataKey, CacheEntry> metadataCache = new ConcurrentHashMap<>();
    private final Map<PayloadKey, Long> uiRecent = new ConcurrentHashMap<>();
    private final Map<UUID, ParticleWindow> particleWindows = new ConcurrentHashMap<>();

    private volatile boolean runtimeEnabled;
    private volatile boolean metadataEnabled;
    private volatile long metadataRefreshMs;
    private volatile boolean uiEnabled;
    private volatile long uiWindowMs;
    private volatile boolean particleEnabled;
    private volatile int particleMax;
    private volatile boolean particleAdaptive;
    private volatile int particleAdaptiveMax;
    private volatile int particleHighPingMs;
    private volatile long staleEntryMs;

    public PacketOptimizationEngine(JavaPlugin plugin, NetworkProfiler profiler) {
        this.plugin = plugin;
        this.profiler = profiler;
        reload();
    }

    public void reload() {
        runtimeEnabled = plugin.getConfig().getBoolean("optimizer.enabled", true);
        metadataEnabled = plugin.getConfig().getBoolean("optimizer.metadata-dedupe.enabled", true);
        metadataRefreshMs = Math.max(
                1000L,
                plugin.getConfig().getLong("optimizer.metadata-dedupe.refresh-ms", 30000L)
        );

        uiEnabled = plugin.getConfig().getBoolean("optimizer.ui-dedupe.enabled", true);
        uiWindowMs = Math.max(
                50L,
                plugin.getConfig().getLong("optimizer.ui-dedupe.duplicate-window-ms", 2000L)
        );

        particleEnabled = plugin.getConfig().getBoolean("optimizer.particle-throttle.enabled", true);
        particleMax = Math.max(
                1,
                plugin.getConfig().getInt("optimizer.particle-throttle.max-per-second", 500)
        );
        particleAdaptive = plugin.getConfig().getBoolean("optimizer.particle-throttle.adaptive", true);
        particleAdaptiveMax = Math.max(
                1,
                plugin.getConfig().getInt("optimizer.particle-throttle.adaptive-max-per-second", 250)
        );
        particleHighPingMs = Math.max(
                1,
                plugin.getConfig().getInt("optimizer.particle-throttle.high-ping-ms", 180)
        );
        staleEntryMs = Math.max(
                60000L,
                plugin.getConfig().getLong("optimizer.cache.stale-entry-ms", 300000L)
        );
    }

    public boolean isEnabled() {
        return runtimeEnabled;
    }

    public void setEnabled(boolean enabled) {
        runtimeEnabled = enabled;
        if (!enabled) {
            clearCaches();
        }
    }

    public boolean shouldSuppressMetadata(
            UUID playerId,
            int entityId,
            byte[] payload,
            long now
    ) {
        if (!runtimeEnabled || !metadataEnabled || payload.length == 0) {
            return false;
        }

        MetadataKey key = new MetadataKey(playerId, entityId);
        CacheEntry previous = metadataCache.get(key);

        if (previous != null
                && Arrays.equals(previous.payload, payload)
                && now - previous.lastForwardedAt < metadataRefreshMs) {
            previous.lastTouchedAt = now;
            stats.metadataSuppressed(playerId);
            return true;
        }

        metadataCache.put(key, new CacheEntry(payload, now, now));
        return false;
    }

    public boolean shouldSuppressUi(
            UUID playerId,
            String packetName,
            byte[] payload,
            long now
    ) {
        if (!runtimeEnabled
                || !uiEnabled
                || payload.length == 0
                || !UI_STATE_PACKETS.contains(packetName)) {
            return false;
        }

        PayloadKey key = new PayloadKey(playerId, packetName, payload);
        Long previous = uiRecent.put(key, now);

        if (previous != null && now - previous < uiWindowMs) {
            stats.uiSuppressed(playerId);
            return true;
        }

        return false;
    }

    public boolean shouldSuppressParticle(UUID playerId, long now) {
        if (!runtimeEnabled || !particleEnabled) {
            return false;
        }

        int allowed = particleMax;
        if (particleAdaptive) {
            ProfileSnapshot snapshot = profiler.snapshot(playerId);
            if (snapshot.burst() || snapshot.pingMs() >= particleHighPingMs) {
                allowed = Math.min(allowed, particleAdaptiveMax);
            }
        }

        long second = now / 1000L;
        ParticleWindow window = particleWindows.computeIfAbsent(
                playerId,
                ignored -> new ParticleWindow(second)
        );

        synchronized (window) {
            if (window.second != second) {
                window.second = second;
                window.count = 0;
            }

            window.count++;
            if (window.count > allowed) {
                stats.particleSuppressed(playerId);
                return true;
            }
        }

        return false;
    }

    public boolean isUiStatePacket(String packetName) {
        return UI_STATE_PACKETS.contains(packetName);
    }

    public OptimizationStats stats() {
        return stats;
    }

    public void clearEntity(UUID playerId, int entityId) {
        metadataCache.remove(new MetadataKey(playerId, entityId));
    }

    public void clearPlayer(UUID playerId) {
        metadataCache.keySet().removeIf(key -> key.playerId.equals(playerId));
        uiRecent.keySet().removeIf(key -> key.playerId.equals(playerId));
        particleWindows.remove(playerId);
    }

    public void reset() {
        clearCaches();
        stats.reset();
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        long metadataCutoff = now - staleEntryMs;
        long uiCutoff = now - Math.max(10000L, uiWindowMs * 2L);

        metadataCache.entrySet().removeIf(
                entry -> entry.getValue().lastTouchedAt < metadataCutoff
        );
        uiRecent.entrySet().removeIf(
                entry -> entry.getValue() < uiCutoff
        );
    }

    private void clearCaches() {
        metadataCache.clear();
        uiRecent.clear();
        particleWindows.clear();
    }

    private record MetadataKey(UUID playerId, int entityId) {
    }

    private static final class CacheEntry {
        private final byte[] payload;
        private final long lastForwardedAt;
        private volatile long lastTouchedAt;

        private CacheEntry(byte[] payload, long lastForwardedAt, long lastTouchedAt) {
            this.payload = Arrays.copyOf(payload, payload.length);
            this.lastForwardedAt = lastForwardedAt;
            this.lastTouchedAt = lastTouchedAt;
        }
    }

    private static final class ParticleWindow {
        private long second;
        private int count;

        private ParticleWindow(long second) {
            this.second = second;
        }
    }

    private static final class PayloadKey {
        private final UUID playerId;
        private final String packetName;
        private final byte[] payload;
        private final int hash;

        private PayloadKey(UUID playerId, String packetName, byte[] payload) {
            this.playerId = playerId;
            this.packetName = packetName;
            this.payload = Arrays.copyOf(payload, payload.length);

            int result = playerId.hashCode();
            result = 31 * result + packetName.hashCode();
            result = 31 * result + Arrays.hashCode(this.payload);
            this.hash = result;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof PayloadKey other)) {
                return false;
            }
            return playerId.equals(other.playerId)
                    && packetName.equals(other.packetName)
                    && Arrays.equals(payload, other.payload);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
