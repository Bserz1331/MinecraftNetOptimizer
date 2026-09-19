package work.spacecat.twnetoptimizer.trace;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class VirtualEntityRegistry {
    private final Map<UUID, Map<Integer, TrackedEntity>> perViewer =
            new ConcurrentHashMap<>();

    public void recordSpawn(
            UUID viewerId,
            int entityId,
            UUID entityUuid,
            String spawnPacket,
            String entityType
    ) {
        long now = System.currentTimeMillis();

        perViewer
                .computeIfAbsent(viewerId, ignored -> new ConcurrentHashMap<>())
                .put(
                        entityId,
                        new TrackedEntity(
                                entityId,
                                entityUuid,
                                safe(spawnPacket, "SPAWN_UNKNOWN"),
                                safe(entityType, "UNKNOWN"),
                                now
                        )
                );
    }

    public void recordActivity(
            UUID viewerId,
            int entityId,
            String packetName
    ) {
        long now = System.currentTimeMillis();

        TrackedEntity entity = perViewer
                .computeIfAbsent(viewerId, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(
                        entityId,
                        ignored -> new TrackedEntity(
                                entityId,
                                null,
                                "SPAWN_NOT_OBSERVED",
                                "UNKNOWN",
                                now
                        )
                );

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
        Map<Integer, TrackedEntity> entities = perViewer.get(viewerId);
        if (entities != null) {
            entities.remove(entityId);
        }
    }

    public Optional<Snapshot> find(UUID viewerId, int entityId) {
        Map<Integer, TrackedEntity> entities = perViewer.get(viewerId);
        if (entities == null) {
            return Optional.empty();
        }

        TrackedEntity entity = entities.get(entityId);
        return entity == null
                ? Optional.empty()
                : Optional.of(entity.snapshot());
    }

    public List<Snapshot> snapshot(UUID viewerId) {
        Map<Integer, TrackedEntity> entities = perViewer.get(viewerId);
        if (entities == null) {
            return List.of();
        }

        return entities.values().stream()
                .map(TrackedEntity::snapshot)
                .sorted(Comparator.comparingLong(Snapshot::totalActivity).reversed())
                .toList();
    }

    public void resetActivity(UUID viewerId) {
        Map<Integer, TrackedEntity> entities = perViewer.get(viewerId);
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

    public void clearPlayer(UUID viewerId) {
        perViewer.remove(viewerId);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
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
