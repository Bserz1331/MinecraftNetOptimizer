package work.spacecat.twnetoptimizer.trace;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class EntityTraceService {
    private final Map<UUID, TraceSession> sessions = new ConcurrentHashMap<>();

    public void start(UUID playerId, int seconds) {
        long expiresAt = System.currentTimeMillis() + seconds * 1000L;
        sessions.put(playerId, new TraceSession(expiresAt));
    }

    public boolean isActive(UUID playerId) {
        TraceSession session = sessions.get(playerId);
        if (session == null) {
            return false;
        }
        if (System.currentTimeMillis() > session.expiresAt) {
            return false;
        }
        return true;
    }

    public void record(UUID playerId, int entityId, String packetName) {
        TraceSession session = sessions.get(playerId);
        if (session == null || System.currentTimeMillis() > session.expiresAt) {
            return;
        }
        session.counters.computeIfAbsent(entityId, ignored -> new EntityCounters())
                .record(packetName);
    }

    public TraceSnapshot snapshot(UUID playerId, int limit) {
        TraceSession session = sessions.get(playerId);
        if (session == null) {
            return new TraceSnapshot(false, 0L, List.of());
        }

        long remaining = Math.max(0L, session.expiresAt - System.currentTimeMillis());
        boolean active = remaining > 0L;

        List<EntityPacketCount> top = session.counters.entrySet().stream()
                .map(entry -> entry.getValue().snapshot(entry.getKey()))
                .sorted(Comparator.comparingLong(EntityPacketCount::total).reversed())
                .limit(Math.max(1, limit))
                .toList();

        return new TraceSnapshot(active, remaining, top);
    }

    public void remove(UUID playerId) {
        sessions.remove(playerId);
    }

    private static final class TraceSession {
        private final long expiresAt;
        private final Map<Integer, EntityCounters> counters = new ConcurrentHashMap<>();

        private TraceSession(long expiresAt) {
            this.expiresAt = expiresAt;
        }
    }

    private static final class EntityCounters {
        private final LongAdder metadata = new LongAdder();
        private final LongAdder teleport = new LongAdder();
        private final LongAdder velocity = new LongAdder();

        private void record(String packetName) {
            switch (packetName) {
                case "ENTITY_METADATA" -> metadata.increment();
                case "ENTITY_TELEPORT" -> teleport.increment();
                case "ENTITY_VELOCITY" -> velocity.increment();
                default -> {
                }
            }
        }

        private EntityPacketCount snapshot(int entityId) {
            return new EntityPacketCount(
                    entityId,
                    metadata.sum(),
                    teleport.sum(),
                    velocity.sum()
            );
        }
    }

    public record TraceSnapshot(
            boolean active,
            long remainingMs,
            List<EntityPacketCount> entities
    ) {
    }

    public record EntityPacketCount(
            int entityId,
            long metadata,
            long teleport,
            long velocity
    ) {
        public long total() {
            return metadata + teleport + velocity;
        }
    }
}
