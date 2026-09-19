package work.spacecat.twnetoptimizer.optimizer;

import org.bukkit.plugin.java.JavaPlugin;
import work.spacecat.twnetoptimizer.latency.LatencyGuardian;
import work.spacecat.twnetoptimizer.profiler.NetworkProfiler;
import work.spacecat.twnetoptimizer.profiler.ProfileSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

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

    private final ConcurrentHashMap<UUID, ConcurrentHashMap<Integer, PayloadEntry>>
            metadataCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, PayloadEntry>>
            uiStateCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ParticleWindow> particleWindows =
            new ConcurrentHashMap<>();

    private final PlayerEntryBudget metadataBudget =
            new PlayerEntryBudget(16384, 2048);
    private final PlayerEntryBudget uiBudget =
            new PlayerEntryBudget(4096, 512);

    private final AtomicInteger particleHighWater = new AtomicInteger();
    private final LongAdder particleWindowSkipped = new LongAdder();
    private final LongAdder metadataStaleRemoved = new LongAdder();
    private final LongAdder uiStaleRemoved = new LongAdder();
    private final LongAdder particleStaleRemoved = new LongAdder();
    private final LongAdder metadataTrimRemoved = new LongAdder();
    private final LongAdder uiTrimRemoved = new LongAdder();
    private final LongAdder uncacheablePayloads = new LongAdder();
    private final LongAdder uncacheableInvalidations = new LongAdder();

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
    private volatile int metadataMaxEntries;
    private volatile int metadataMaxEntriesPerPlayer;
    private volatile int uiMaxEntries;
    private volatile int uiMaxEntriesPerPlayer;
    private volatile int particleWindowMaxEntries;
    private volatile int maxPayloadBytes;

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

        metadataMaxEntries = Math.max(
                256,
                plugin.getConfig().getInt(
                        "optimizer.cache.metadata-max-entries",
                        16384
                )
        );

        metadataMaxEntriesPerPlayer = Math.max(
                64,
                Math.min(
                        metadataMaxEntries,
                        plugin.getConfig().getInt(
                                "optimizer.cache.metadata-max-entries-per-player",
                                2048
                        )
                )
        );

        uiMaxEntries = Math.max(
                128,
                plugin.getConfig().getInt(
                        "optimizer.cache.ui-max-entries",
                        4096
                )
        );

        uiMaxEntriesPerPlayer = Math.max(
                32,
                Math.min(
                        uiMaxEntries,
                        plugin.getConfig().getInt(
                                "optimizer.cache.ui-max-entries-per-player",
                                512
                        )
                )
        );

        particleWindowMaxEntries = Math.max(
                64,
                plugin.getConfig().getInt(
                        "optimizer.cache.particle-window-max-entries",
                        2048
                )
        );

        maxPayloadBytes = Math.max(
                1024,
                plugin.getConfig().getInt(
                        "optimizer.cache.max-payload-bytes",
                        65536
                )
        );

        metadataBudget.configure(
                metadataMaxEntries,
                metadataMaxEntriesPerPlayer
        );
        uiBudget.configure(
                uiMaxEntries,
                uiMaxEntriesPerPlayer
        );

        trimCachesToLimits();
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
        if (!runtimeEnabled
                || !metadataEnabled
                || playerId == null
                || byteBuf == null) {
            return false;
        }

        ConcurrentHashMap<Integer, PayloadEntry> playerCache =
                metadataCache.get(playerId);

        if (playerCache == null) {
            return false;
        }

        PayloadEntry previous = playerCache.get(entityId);

        if (previous == null) {
            return false;
        }

        previous.lastTouchedAt = now;

        if (!PayloadBuffer.matches(byteBuf, previous.payload)) {
            return false;
        }

        long elapsed = now - previous.lastForwardedAt;

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
        if (!runtimeEnabled
                || !metadataEnabled
                || playerId == null
                || byteBuf == null) {
            return;
        }

        commitPayload(
                metadataCache,
                metadataBudget,
                playerId,
                entityId,
                byteBuf,
                now
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
                || playerId == null
                || stateKey == null
                || byteBuf == null) {
            return false;
        }

        ConcurrentHashMap<String, PayloadEntry> playerCache =
                uiStateCache.get(playerId);

        if (playerCache == null) {
            return false;
        }

        PayloadEntry previous = playerCache.get(stateKey);

        if (previous == null) {
            return false;
        }

        previous.lastTouchedAt = now;

        if (!PayloadBuffer.matches(byteBuf, previous.payload)) {
            return false;
        }

        long elapsed = now - previous.lastForwardedAt;

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
        if (!runtimeEnabled
                || !uiEnabled
                || playerId == null
                || stateKey == null
                || byteBuf == null) {
            return;
        }

        commitPayload(
                uiStateCache,
                uiBudget,
                playerId,
                stateKey,
                byteBuf,
                now
        );
    }

    public boolean shouldSuppressParticle(UUID playerId, long now) {
        if (!runtimeEnabled || !particleEnabled || playerId == null) {
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

        ParticleWindow window = getOrCreateParticleWindow(
                playerId,
                second
        );

        if (window == null) {
            return false;
        }

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

    public LifecycleSnapshot lifecycleSnapshot() {
        PlayerEntryBudget.Snapshot metadata =
                metadataBudget.snapshot(null);
        PlayerEntryBudget.Snapshot ui =
                uiBudget.snapshot(null);

        return new LifecycleSnapshot(
                metadata.total(),
                metadata.globalMax(),
                metadata.perPlayerMax(),
                metadata.highWater(),
                metadata.skippedGlobal(),
                metadata.skippedPlayer(),
                metadataStaleRemoved.sum(),
                metadataTrimRemoved.sum(),
                ui.total(),
                ui.globalMax(),
                ui.perPlayerMax(),
                ui.highWater(),
                ui.skippedGlobal(),
                ui.skippedPlayer(),
                uiStaleRemoved.sum(),
                uiTrimRemoved.sum(),
                particleWindows.size(),
                particleWindowMaxEntries,
                particleHighWater.get(),
                particleWindowSkipped.sum(),
                particleStaleRemoved.sum(),
                uncacheablePayloads.sum(),
                uncacheableInvalidations.sum()
        );
    }

    public PlayerLifecycleSnapshot lifecycleSnapshot(UUID playerId) {
        PlayerEntryBudget.Snapshot metadata =
                metadataBudget.snapshot(playerId);
        PlayerEntryBudget.Snapshot ui =
                uiBudget.snapshot(playerId);

        return new PlayerLifecycleSnapshot(
                metadata.player(),
                metadata.perPlayerMax(),
                ui.player(),
                ui.perPlayerMax(),
                particleWindows.containsKey(playerId)
        );
    }

    public void clearEntity(UUID playerId, int entityId) {
        removePayloadEntry(
                metadataCache,
                metadataBudget,
                playerId,
                entityId
        );
    }

    public void clearPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }

        clearPlayerCache(
                metadataCache,
                metadataBudget,
                playerId
        );
        clearPlayerCache(
                uiStateCache,
                uiBudget,
                playerId
        );
        particleWindows.remove(playerId);
    }

    public void reset() {
        clearCaches();
        stats.reset();
        metadataBudget.resetMetrics();
        uiBudget.resetMetrics();
        particleHighWater.set(particleWindows.size());
        particleWindowSkipped.reset();
        metadataStaleRemoved.reset();
        uiStaleRemoved.reset();
        particleStaleRemoved.reset();
        metadataTrimRemoved.reset();
        uiTrimRemoved.reset();
        uncacheablePayloads.reset();
        uncacheableInvalidations.reset();
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        long cutoff = now - staleEntryMs;

        cleanupPayloadCache(
                metadataCache,
                metadataBudget,
                cutoff,
                metadataStaleRemoved
        );

        cleanupPayloadCache(
                uiStateCache,
                uiBudget,
                cutoff,
                uiStaleRemoved
        );

        long staleSecond = cutoff / 1000L;

        for (Map.Entry<UUID, ParticleWindow> entry
                : particleWindows.entrySet()) {
            if (entry.getValue().second < staleSecond
                    && particleWindows.remove(
                    entry.getKey(),
                    entry.getValue()
            )) {
                particleStaleRemoved.increment();
            }
        }

        trimCachesToLimits();
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

    private <K> void commitPayload(
            ConcurrentHashMap<UUID, ConcurrentHashMap<K, PayloadEntry>> cache,
            PlayerEntryBudget budget,
            UUID playerId,
            K key,
            Object byteBuf,
            long now
    ) {
        ConcurrentHashMap<K, PayloadEntry> playerCache =
                cache.get(playerId);

        if (playerCache != null) {
            synchronized (playerCache) {
                if (playerCache.containsKey(key)) {
                    byte[] payload = PayloadBuffer.copy(byteBuf);

                    if (!cacheable(payload)) {
                        if (playerCache.remove(key) != null) {
                            budget.release(playerId);
                            uncacheableInvalidations.increment();
                        }

                        if (playerCache.isEmpty()) {
                            cache.remove(playerId, playerCache);
                        }

                        uncacheablePayloads.increment();
                        return;
                    }

                    playerCache.put(
                            key,
                            new PayloadEntry(payload, now)
                    );
                    return;
                }
            }
        }

        if (!budget.tryAcquire(playerId)) {
            return;
        }

        byte[] payload = PayloadBuffer.copy(byteBuf);

        if (!cacheable(payload)) {
            budget.release(playerId);
            uncacheablePayloads.increment();
            return;
        }

        synchronized (cache) {
            playerCache = cache.computeIfAbsent(
                    playerId,
                    ignored -> new ConcurrentHashMap<>()
            );

            synchronized (playerCache) {
                PayloadEntry existing = playerCache.get(key);

                if (existing != null) {
                    playerCache.put(
                            key,
                            new PayloadEntry(payload, now)
                    );
                    budget.release(playerId);
                    return;
                }

                playerCache.put(
                        key,
                        new PayloadEntry(payload, now)
                );
            }
        }
    }

    private boolean cacheable(byte[] payload) {
        return payload != null
                && payload.length > 0
                && payload.length <= maxPayloadBytes;
    }

    private ParticleWindow getOrCreateParticleWindow(
            UUID playerId,
            long second
    ) {
        ParticleWindow existing = particleWindows.get(playerId);

        if (existing != null) {
            return existing;
        }

        synchronized (particleWindows) {
            existing = particleWindows.get(playerId);

            if (existing != null) {
                return existing;
            }

            if (particleWindows.size() >= particleWindowMaxEntries) {
                particleWindowSkipped.increment();
                return null;
            }

            ParticleWindow created = new ParticleWindow(second);
            particleWindows.put(playerId, created);
            particleHighWater.accumulateAndGet(
                    particleWindows.size(),
                    Math::max
            );
            return created;
        }
    }

    private <K> void cleanupPayloadCache(
            ConcurrentHashMap<UUID, ConcurrentHashMap<K, PayloadEntry>> cache,
            PlayerEntryBudget budget,
            long cutoff,
            LongAdder removedCounter
    ) {
        for (Map.Entry<UUID, ConcurrentHashMap<K, PayloadEntry>> player
                : cache.entrySet()) {
            UUID playerId = player.getKey();
            ConcurrentHashMap<K, PayloadEntry> playerCache =
                    player.getValue();

            int removed = 0;

            synchronized (playerCache) {
                for (Map.Entry<K, PayloadEntry> entry
                        : playerCache.entrySet()) {
                    if (entry.getValue().lastTouchedAt < cutoff
                            && playerCache.remove(
                            entry.getKey(),
                            entry.getValue()
                    )) {
                        removed++;
                    }
                }

                if (playerCache.isEmpty()) {
                    cache.remove(playerId, playerCache);
                }
            }

            if (removed > 0) {
                budget.release(playerId, removed);
                removedCounter.add(removed);
            }
        }
    }

    private <K> void clearPlayerCache(
            ConcurrentHashMap<UUID, ConcurrentHashMap<K, PayloadEntry>> cache,
            PlayerEntryBudget budget,
            UUID playerId
    ) {
        synchronized (cache) {
            ConcurrentHashMap<K, PayloadEntry> playerCache =
                    cache.remove(playerId);

            if (playerCache == null) {
                return;
            }

            int removed;

            synchronized (playerCache) {
                removed = playerCache.size();
                playerCache.clear();
            }

            budget.release(playerId, removed);
        }
    }

    private <K> void removePayloadEntry(
            ConcurrentHashMap<UUID, ConcurrentHashMap<K, PayloadEntry>> cache,
            PlayerEntryBudget budget,
            UUID playerId,
            K key
    ) {
        ConcurrentHashMap<K, PayloadEntry> playerCache =
                cache.get(playerId);

        if (playerCache == null) {
            return;
        }

        synchronized (playerCache) {
            if (playerCache.remove(key) != null) {
                budget.release(playerId);
            }

            if (playerCache.isEmpty()) {
                cache.remove(playerId, playerCache);
            }
        }
    }

    private void trimCachesToLimits() {
        trimPayloadCache(
                metadataCache,
                metadataBudget,
                metadataTrimRemoved
        );
        trimPayloadCache(
                uiStateCache,
                uiBudget,
                uiTrimRemoved
        );
        trimParticleWindows();
    }

    private <K> void trimPayloadCache(
            ConcurrentHashMap<UUID, ConcurrentHashMap<K, PayloadEntry>> cache,
            PlayerEntryBudget budget,
            LongAdder removedCounter
    ) {
        for (Map.Entry<UUID, ConcurrentHashMap<K, PayloadEntry>> player
                : cache.entrySet()) {
            UUID playerId = player.getKey();
            ConcurrentHashMap<K, PayloadEntry> playerCache =
                    player.getValue();

            int excess =
                    playerCache.size() - budget.perPlayerMax();

            if (excess > 0) {
                int removed = removeOldest(
                        playerCache,
                        excess
                );

                if (removed > 0) {
                    budget.release(playerId, removed);
                    removedCounter.add(removed);
                }
            }

            if (playerCache.isEmpty()) {
                cache.remove(playerId, playerCache);
            }
        }

        int globalExcess =
                budget.totalCount() - budget.globalMax();

        if (globalExcess <= 0) {
            return;
        }

        List<GlobalEntry<K>> all = new ArrayList<>();

        for (Map.Entry<UUID, ConcurrentHashMap<K, PayloadEntry>> player
                : cache.entrySet()) {
            for (Map.Entry<K, PayloadEntry> entry
                    : player.getValue().entrySet()) {
                all.add(
                        new GlobalEntry<>(
                                player.getKey(),
                                entry.getKey(),
                                entry.getValue()
                        )
                );
            }
        }

        all.sort(
                Comparator.comparingLong(
                        item -> item.entry.lastTouchedAt
                )
        );

        int removed = 0;

        for (GlobalEntry<K> item : all) {
            if (removed >= globalExcess) {
                break;
            }

            ConcurrentHashMap<K, PayloadEntry> playerCache =
                    cache.get(item.playerId);

            if (playerCache == null) {
                continue;
            }

            synchronized (playerCache) {
                if (playerCache.remove(
                        item.key,
                        item.entry
                )) {
                    budget.release(item.playerId);
                    removed++;
                }

                if (playerCache.isEmpty()) {
                    cache.remove(item.playerId, playerCache);
                }
            }
        }

        if (removed > 0) {
            removedCounter.add(removed);
        }
    }

    private static <K> int removeOldest(
            ConcurrentHashMap<K, PayloadEntry> cache,
            int count
    ) {
        if (count <= 0) {
            return 0;
        }

        List<Map.Entry<K, PayloadEntry>> oldest =
                cache.entrySet()
                        .stream()
                        .sorted(
                                Comparator.comparingLong(
                                        entry ->
                                                entry.getValue().lastTouchedAt
                                )
                        )
                        .limit(count)
                        .toList();

        int removed = 0;

        synchronized (cache) {
            for (Map.Entry<K, PayloadEntry> entry : oldest) {
                if (cache.remove(
                        entry.getKey(),
                        entry.getValue()
                )) {
                    removed++;
                }
            }
        }

        return removed;
    }

    private void trimParticleWindows() {
        int excess =
                particleWindows.size() - particleWindowMaxEntries;

        if (excess <= 0) {
            return;
        }

        particleWindows.entrySet().stream()
                .sorted(
                        Comparator.comparingLong(
                                entry -> entry.getValue().second
                        )
                )
                .limit(excess)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(particleWindows::remove);
    }

    private void clearCaches() {
        metadataCache.clear();
        uiStateCache.clear();
        particleWindows.clear();
        metadataBudget.clearCounts();
        uiBudget.clearCounts();
    }

    private record GlobalEntry<K>(
            UUID playerId,
            K key,
            PayloadEntry entry
    ) {
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

    public record LifecycleSnapshot(
            int metadataEntries,
            int metadataGlobalMax,
            int metadataPerPlayerMax,
            int metadataHighWater,
            long metadataSkippedGlobal,
            long metadataSkippedPlayer,
            long metadataStaleRemoved,
            long metadataTrimRemoved,
            int uiEntries,
            int uiGlobalMax,
            int uiPerPlayerMax,
            int uiHighWater,
            long uiSkippedGlobal,
            long uiSkippedPlayer,
            long uiStaleRemoved,
            long uiTrimRemoved,
            int particleWindows,
            int particleWindowMax,
            int particleHighWater,
            long particleWindowSkipped,
            long particleStaleRemoved,
            long uncacheablePayloads,
            long uncacheableInvalidations
    ) {
    }

    public record PlayerLifecycleSnapshot(
            int metadataEntries,
            int metadataMax,
            int uiEntries,
            int uiMax,
            boolean particleWindowPresent
    ) {
    }
}
