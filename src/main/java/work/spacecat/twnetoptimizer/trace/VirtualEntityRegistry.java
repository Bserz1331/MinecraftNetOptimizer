package work.spacecat.twnetoptimizer.trace;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public final class VirtualEntityRegistry {
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<Integer, TrackedEntity>> perViewer =
            new ConcurrentHashMap<>();

    private final AtomicInteger totalEntities = new AtomicInteger();
    private final AtomicInteger highWaterEntities = new AtomicInteger();
    private final LongAdder skippedEntities = new LongAdder();
    private final LongAdder staleRemovedEntities = new LongAdder();
    private final LongAdder trimRemovedEntities = new LongAdder();

    private volatile int maxEntitiesPerViewer = 4096;
    private volatile long staleEntityMs = 300000L;

    public void configure(
            int maxEntitiesPerViewer,
            long staleEntityMs
    ) {
        this.maxEntitiesPerViewer =
                Math.max(256, maxEntitiesPerViewer);
        this.staleEntityMs =
                Math.max(60000L, staleEntityMs);

        trimAllToLimit();
    }

    public void recordSpawn(
            UUID viewerId,
            int entityId,
            UUID entityUuid,
            String spawnPacket,
            String entityType
    ) {
        if (viewerId == null) {
            return;
        }

        long now = System.currentTimeMillis();
        ConcurrentHashMap<Integer, TrackedEntity> entities =
                perViewer.computeIfAbsent(
                        viewerId,
                        ignored -> new ConcurrentHashMap<>()
                );

        TrackedEntity tracked = new TrackedEntity(
                entityId,
                entityUuid,
                safe(spawnPacket, "SPAWN_UNKNOWN"),
                safe(entityType, "UNKNOWN"),
                now
        );

        putBounded(entities, entityId, tracked);
    }

    public void recordActivity(
            UUID viewerId,
            int entityId,
            String packetName
    ) {
        if (viewerId == null) {
            return;
        }

        long now = System.currentTimeMillis();
        ConcurrentHashMap<Integer, TrackedEntity> entities =
                perViewer.computeIfAbsent(
                        viewerId,
                        ignored -> new ConcurrentHashMap<>()
                );

        TrackedEntity entity = entities.get(entityId);

        if (entity == null) {
            entity = createIfCapacity(
                    entities,
                    entityId,
                    now
            );

            if (entity == null) {
                return;
            }
        }

        entity.lastSeenAt = now;

        switch (packetName) {
            case "ENTITY_METADATA" -> entity.metadata.increment();
            case "ENTITY_TELEPORT" -> entity.teleport.increment();
            case "ENTITY_VELOCITY" -> entity.velocity.increment();
            default -> {
            }
        }
    }

    public void recordDestroy(UUID viewerId, int entityId) {
        ConcurrentHashMap<Integer, TrackedEntity> entities =
                perViewer.get(viewerId);

        if (entities == null) {
            return;
        }

        if (entities.remove(entityId) != null) {
            subtractEntities(1);
        }

        if (entities.isEmpty()) {
            perViewer.remove(viewerId, entities);
        }
    }

    public Optional<Snapshot> find(UUID viewerId, int entityId) {
        ConcurrentHashMap<Integer, TrackedEntity> entities =
                perViewer.get(viewerId);

        if (entities == null) {
            return Optional.empty();
        }

        TrackedEntity entity = entities.get(entityId);

        return entity == null
                ? Optional.empty()
                : Optional.of(entity.snapshot());
    }

    public List<Snapshot> snapshot(UUID viewerId) {
        ConcurrentHashMap<Integer, TrackedEntity> entities =
                perViewer.get(viewerId);

        if (entities == null) {
            return List.of();
        }

        return entities.values().stream()
                .map(TrackedEntity::snapshot)
                .sorted(Comparator.comparingLong(Snapshot::totalActivity).reversed())
                .toList();
    }

    public MetricsSnapshot metricsSnapshot() {
        return new MetricsSnapshot(
                perViewer.size(),
                totalEntities.get(),
                maxEntitiesPerViewer,
                highWaterEntities.get(),
                skippedEntities.sum(),
                staleRemovedEntities.sum(),
                trimRemovedEntities.sum()
        );
    }

    public PlayerMetricsSnapshot metricsSnapshot(UUID viewerId) {
        ConcurrentHashMap<Integer, TrackedEntity> entities =
                perViewer.get(viewerId);

        return new PlayerMetricsSnapshot(
                entities == null ? 0 : entities.size(),
                maxEntitiesPerViewer
        );
    }

    public void resetActivity(UUID viewerId) {
        ConcurrentHashMap<Integer, TrackedEntity> entities =
                perViewer.get(viewerId);

        if (entities == null) {
            return;
        }

        for (TrackedEntity entity : entities.values()) {
            entity.metadata.reset();
            entity.teleport.reset();
            entity.velocity.reset();
        }
    }

    public void resetAllActivity() {
        for (UUID viewerId : perViewer.keySet()) {
            resetActivity(viewerId);
        }
    }

    public void cleanup() {
        long cutoff = System.currentTimeMillis() - staleEntityMs;

        for (Map.Entry<UUID, ConcurrentHashMap<Integer, TrackedEntity>> viewer
                : perViewer.entrySet()) {
            ConcurrentHashMap<Integer, TrackedEntity> entities =
                    viewer.getValue();

            int removed = 0;

            for (Map.Entry<Integer, TrackedEntity> entry
                    : entities.entrySet()) {
                if (entry.getValue().lastSeenAt < cutoff
                        && entities.remove(
                        entry.getKey(),
                        entry.getValue()
                )) {
                    removed++;
                }
            }

            if (removed > 0) {
                subtractEntities(removed);
                staleRemovedEntities.add(removed);
            }

            trimToLimit(entities);

            if (entities.isEmpty()) {
                perViewer.remove(viewer.getKey(), entities);
            }
        }
    }

    public void clearPlayer(UUID viewerId) {
        ConcurrentHashMap<Integer, TrackedEntity> removed =
                perViewer.remove(viewerId);

        if (removed != null) {
            subtractEntities(removed.size());
            removed.clear();
        }
    }

    public void clearAll() {
        perViewer.clear();
        totalEntities.set(0);
    }

    private TrackedEntity createIfCapacity(
            ConcurrentHashMap<Integer, TrackedEntity> entities,
            int entityId,
            long now
    ) {
        synchronized (entities) {
            TrackedEntity existing = entities.get(entityId);

            if (existing != null) {
                return existing;
            }

            if (entities.size() >= maxEntitiesPerViewer) {
                skippedEntities.increment();
                return null;
            }

            TrackedEntity created = new TrackedEntity(
                    entityId,
                    null,
                    "SPAWN_NOT_OBSERVED",
                    "UNKNOWN",
                    now
            );

            entities.put(entityId, created);
            incrementEntities();
            return created;
        }
    }

    private void putBounded(
            ConcurrentHashMap<Integer, TrackedEntity> entities,
            int entityId,
            TrackedEntity tracked
    ) {
        synchronized (entities) {
            TrackedEntity existing = entities.get(entityId);

            if (existing != null) {
                entities.put(entityId, tracked);
                return;
            }

            if (entities.size() >= maxEntitiesPerViewer) {
                skippedEntities.increment();
                return;
            }

            entities.put(entityId, tracked);
            incrementEntities();
        }
    }

    private void trimAllToLimit() {
        for (ConcurrentHashMap<Integer, TrackedEntity> entities
                : perViewer.values()) {
            trimToLimit(entities);
        }
    }

    private void trimToLimit(
            ConcurrentHashMap<Integer, TrackedEntity> entities
    ) {
        int excess = entities.size() - maxEntitiesPerViewer;

        if (excess <= 0) {
            return;
        }

        List<Map.Entry<Integer, TrackedEntity>> oldest =
                entities.entrySet()
                        .stream()
                        .sorted(
                                Comparator.comparingLong(
                                        entry ->
                                                entry.getValue().lastSeenAt
                                )
                        )
                        .limit(excess)
                        .toList();

        int removed = 0;

        synchronized (entities) {
            for (Map.Entry<Integer, TrackedEntity> entry : oldest) {
                if (entities.remove(
                        entry.getKey(),
                        entry.getValue()
                )) {
                    removed++;
                }
            }
        }

        if (removed > 0) {
            subtractEntities(removed);
            trimRemovedEntities.add(removed);
        }
    }

    private void incrementEntities() {
        int current = totalEntities.incrementAndGet();
        highWaterEntities.accumulateAndGet(current, Math::max);
    }

    private void subtractEntities(int count) {
        if (count <= 0) {
            return;
        }

        totalEntities.updateAndGet(
                current -> Math.max(0, current - count)
        );
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank()
                ? fallback
                : value;
    }

    private static final class TrackedEntity {
        private final int entityId;
        private final UUID entityUuid;
        private final String spawnPacket;
        private final String entityType;
        private final long firstSeenAt;
        private volatile long lastSeenAt;
        private final LongAdder metadata = new LongAdder();
        private final LongAdder teleport = new LongAdder();
        private final LongAdder velocity = new LongAdder();

        private TrackedEntity(
                int entityId,
                UUID entityUuid,
                String spawnPacket,
                String entityType,
                long firstSeenAt
        ) {
            this.entityId = entityId;
            this.entityUuid = entityUuid;
            this.spawnPacket = spawnPacket;
            this.entityType = entityType;
            this.firstSeenAt = firstSeenAt;
            this.lastSeenAt = firstSeenAt;
        }

        private Snapshot snapshot() {
            return new Snapshot(
                    entityId,
                    entityUuid,
                    spawnPacket,
                    entityType,
                    firstSeenAt,
                    lastSeenAt,
                    metadata.sum(),
                    teleport.sum(),
                    velocity.sum()
            );
        }
    }

    public record Snapshot(
            int entityId,
            UUID entityUuid,
            String spawnPacket,
            String entityType,
            long firstSeenAt,
            long lastSeenAt,
            long metadata,
            long teleport,
            long velocity
    ) {
        public long totalActivity() {
            return metadata + teleport + velocity;
        }

        public String shortUuid() {
            if (entityUuid == null) {
                return "-";
            }

            String value = entityUuid.toString();
            return value.substring(0, Math.min(8, value.length()));
        }
    }

    public record MetricsSnapshot(
            int viewers,
            int entities,
            int maxEntitiesPerViewer,
            int highWaterEntities,
            long skippedEntities,
            long staleRemovedEntities,
            long trimRemovedEntities
    ) {
    }

    public record PlayerMetricsSnapshot(
            int entities,
            int maxEntities
    ) {
    }
}
