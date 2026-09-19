package work.spacecat.twnetoptimizer.trace;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class VirtualEntityRegistry {
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<Integer, TrackedEntity>> perViewer =
            new ConcurrentHashMap<>();

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

        if (entities != null) {
            entities.remove(entityId);

            if (entities.isEmpty()) {
                perViewer.remove(viewerId, entities);
            }
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

            entities.entrySet().removeIf(
                    entry -> entry.getValue().lastSeenAt < cutoff
            );

            trimToLimit(entities);

            if (entities.isEmpty()) {
                perViewer.remove(viewer.getKey(), entities);
            }
        }
    }

    public void clearPlayer(UUID viewerId) {
        perViewer.remove(viewerId);
    }

    public void clearAll() {
        perViewer.clear();
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
            return created;
        }
    }

    private void putBounded(
            ConcurrentHashMap<Integer, TrackedEntity> entities,
            int entityId,
            TrackedEntity tracked
    ) {
        TrackedEntity existing = entities.get(entityId);

        if (existing != null
                && entities.replace(entityId, existing, tracked)) {
            return;
        }

        synchronized (entities) {
            existing = entities.get(entityId);

            if (existing != null) {
                entities.put(entityId, tracked);
                return;
            }

            if (entities.size() >= maxEntitiesPerViewer) {
                return;
            }

            entities.put(entityId, tracked);
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

        entities.entrySet().stream()
                .sorted(Comparator.comparingLong(
                        entry -> entry.getValue().lastSeenAt
                ))
                .limit(excess)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(entities::remove);
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
}
