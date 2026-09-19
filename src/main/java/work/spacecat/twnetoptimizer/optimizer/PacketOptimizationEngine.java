package work.spacecat.twnetoptimizer.optimizer;

import org.bukkit.plugin.java.JavaPlugin;
import work.spacecat.twnetoptimizer.latency.LatencyGuardian;
import work.spacecat.twnetoptimizer.profiler.NetworkProfiler;
import work.spacecat.twnetoptimizer.profiler.ProfileSnapshot;

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
    private final LatencyGuardian latencyGuardian;
    private final OptimizationStats stats = new OptimizationStats();

    private final Map<MetadataKey, PayloadEntry> metadataCache =
            new ConcurrentHashMap<>();
    private final Map<UiStateKey, PayloadEntry> uiStateCache =
            new ConcurrentHashMap<>();
    private final Map<UUID, ParticleWindow> particleWindows =
            new ConcurrentHashMap<>();

    private volatile boolean runtimeEnabled;
    private volatile boolean metadataEnabled;
    private volatile long metadataRefreshMs;
    private volatile boolean uiEnabled;
    private volatile long uiRefreshMs;
    private volatile boolean particleEnabled;
    private volatile int particleMax;
    private volatile boolean particleAdaptive;
    private volatile int particleAdaptiveMax;
    private volatile int particleHighPingMs;
    private volatile int pressureParticleMax;
    private volatile int combatParticleMax;
    private volatile long combatRefreshDeferralMs;
    private volatile long staleEntryMs;

    public PacketOptimizationEngine(
            JavaPlugin plugin,
            NetworkProfiler profiler,
            LatencyGuardian latencyGuardian
    ) {
        this.plugin = plugin;
        this.profiler = profiler;
        this.latencyGuardian = latencyGuardian;
        reload();
    }

    public void reload() {
        runtimeEnabled = plugin.getConfig()
                .getBoolean("optimizer.enabled", true);

        metadataEnabled = plugin.getConfig()
                .getBoolean("optimizer.metadata-dedupe.enabled", true);

        metadataRefreshMs = Math.max(
                1000L,
                plugin.getConfig().getLong(
                        "optimizer.metadata-dedupe.refresh-ms",
                        30000L
                )
        );

        uiEnabled = plugin.getConfig()
                .getBoolean("optimizer.ui-dedupe.enabled", true);

        uiRefreshMs = Math.max(
                1000L,
                plugin.getConfig().getLong(
                        "optimizer.ui-dedupe.refresh-ms",
                        30000L
                )
        );

        particleEnabled = plugin.getConfig()
                .getBoolean("optimizer.particle-throttle.enabled", false);

        particleMax = Math.max(
                1,
                plugin.getConfig().getInt(
                        "optimizer.particle-throttle.max-per-second",
                        500
                )
        );

        particleAdaptive = plugin.getConfig()
                .getBoolean("optimizer.particle-throttle.adaptive", true);

        particleAdaptiveMax = Math.max(
                1,
                plugin.getConfig().getInt(
                        "optimizer.particle-throttle.adaptive-max-per-second",
                        250
                )
        );

        particleHighPingMs = Math.max(
                1,
                plugin.getConfig().getInt(
                        "optimizer.particle-throttle.high-ping-ms",
                        180
                )
        );

        pressureParticleMax = Math.max(
                1,
                plugin.getConfig().getInt(
                        "latency-guardian.cosmetic.pressure-particle-max-per-second",
                        150
                )
        );

        combatParticleMax = Math.max(
                1,
                plugin.getConfig().getInt(
                        "latency-guardian.cosmetic.combat-particle-max-per-second",
                        100
                )
        );

        combatRefreshDeferralMs = Math.max(
                0L,
                plugin.getConfig().getLong(
                        "latency-guardian.combat-refresh-deferral-ms",
                        5000L
                )
        );

        staleEntryMs = Math.max(
                60000L,
                plugin.getConfig().getLong(
                        "optimizer.cache.stale-entry-ms",
                        300000L
                )
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
            Object byteBuf,
            long now
    ) {
        if (!runtimeEnabled || !metadataEnabled || byteBuf == null) {
            return false;
        }

        PayloadEntry previous =
                metadataCache.get(new MetadataKey(playerId, entityId));

        if (previous == null) {
            return false;
        }

        previous.lastTouchedAt = now;

        if (!PayloadBuffer.matches(byteBuf, previous.payload)) {
            return false;
        }

        long elapsed =
                now - previous.lastForwardedAt;

        if (elapsed >= metadataRefreshMs
                && !shouldDeferCombatRefresh(
                playerId,
                elapsed,
                metadataRefreshMs
        )) {
            return false;
        }

        stats.metadataSuppressed(playerId);
        return true;
    }

    public void commitMetadata(
            UUID playerId,
            int entityId,
            Object byteBuf,
            long now
    ) {
        if (!runtimeEnabled || !metadataEnabled) {
            return;
        }

        byte[] payload = PayloadBuffer.copy(byteBuf);

        if (payload == null || payload.length == 0) {
            return;
        }

        metadataCache.put(
                new MetadataKey(playerId, entityId),
                new PayloadEntry(payload, now)
        );
    }

    public boolean shouldSuppressUi(
            UUID playerId,
            String stateKey,
            Object byteBuf,
            long now
    ) {
        if (!runtimeEnabled
                || !uiEnabled
                || stateKey == null
                || byteBuf == null) {
            return false;
        }

        PayloadEntry previous =
                uiStateCache.get(new UiStateKey(playerId, stateKey));

        if (previous == null) {
            return false;
        }

        previous.lastTouchedAt = now;

        if (!PayloadBuffer.matches(byteBuf, previous.payload)) {
            return false;
        }

        long elapsed =
                now - previous.lastForwardedAt;

        if (elapsed >= uiRefreshMs
                && !shouldDeferCombatRefresh(
                playerId,
                elapsed,
                uiRefreshMs
        )) {
            return false;
        }

        stats.uiSuppressed(playerId);
        return true;
    }

    public void commitUi(
            UUID playerId,
            String stateKey,
            Object byteBuf,
            long now
    ) {
        if (!runtimeEnabled || !uiEnabled || stateKey == null) {
            return;
        }

        byte[] payload = PayloadBuffer.copy(byteBuf);

        if (payload == null || payload.length == 0) {
            return;
        }

        uiStateCache.put(
                new UiStateKey(playerId, stateKey),
                new PayloadEntry(payload, now)
        );
    }

    public boolean shouldSuppressParticle(UUID playerId, long now) {
        if (!runtimeEnabled || !particleEnabled) {
            return false;
        }

        ProfileSnapshot snapshot = profiler.snapshot(playerId);
        LatencyGuardian.Mode mode =
                latencyGuardian.mode(playerId, snapshot);

        int allowed = particleMax;

        if (mode == LatencyGuardian.Mode.COMBAT) {
            allowed = Math.min(allowed, combatParticleMax);
        } else if (mode == LatencyGuardian.Mode.PRESSURE) {
            allowed = Math.min(allowed, pressureParticleMax);
        } else if (particleAdaptive
                && (snapshot.burst()
                || snapshot.pingMs() >= particleHighPingMs)) {
            allowed = Math.min(allowed, particleAdaptiveMax);
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

    private boolean shouldDeferCombatRefresh(
            UUID playerId,
            long elapsed,
            long refreshMs
    ) {
        return combatRefreshDeferralMs > 0L
                && latencyGuardian.isCombatActive(playerId)
                && elapsed
                < refreshMs + combatRefreshDeferralMs;
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
        metadataCache.keySet().removeIf(
                key -> key.playerId.equals(playerId)
        );

        uiStateCache.keySet().removeIf(
                key -> key.playerId.equals(playerId)
        );

        particleWindows.remove(playerId);
    }

    public void reset() {
        clearCaches();
        stats.reset();
    }

    public void cleanup() {
        long cutoff = System.currentTimeMillis() - staleEntryMs;

        metadataCache.entrySet().removeIf(
                entry -> entry.getValue().lastTouchedAt < cutoff
        );

        uiStateCache.entrySet().removeIf(
                entry -> entry.getValue().lastTouchedAt < cutoff
        );
    }

    private void clearCaches() {
        metadataCache.clear();
        uiStateCache.clear();
        particleWindows.clear();
    }

    private record MetadataKey(UUID playerId, int entityId) {
    }

    private record UiStateKey(UUID playerId, String stateKey) {
    }

    private static final class PayloadEntry {
        private final byte[] payload;
        private final long lastForwardedAt;
        private volatile long lastTouchedAt;

        private PayloadEntry(byte[] payload, long now) {
            this.payload = payload;
            this.lastForwardedAt = now;
            this.lastTouchedAt = now;
        }
    }

    private static final class ParticleWindow {
        private long second;
        private int count;

        private ParticleWindow(long second) {
            this.second = second;
        }
    }
}
