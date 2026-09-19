package work.spacecat.twnetoptimizer.trace;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public final class EntityTraceService {
    private final Map<UUID, TraceSession> sessions = new ConcurrentHashMap<>();

    private final AtomicInteger totalEntities = new AtomicInteger();
    private final AtomicInteger highWaterEntities = new AtomicInteger();
    private final LongAdder skippedEntities = new LongAdder();
    private final LongAdder cleanupRemovedSessions = new LongAdder();
    private final LongAdder trimRemovedEntities = new LongAdder();

    private volatile int maxEntitiesPerSession = 2048;
    private volatile long resultRetentionMs = 300000L;

    public void configure(
            int maxEntitiesPerSession,
            long resultRetentionMs
    ) {
        this.maxEntitiesPerSession =
                Math.max(128, maxEntitiesPerSession);
        this.resultRetentionMs =
                Math.max(60000L, resultRetentionMs);

        trimSessionsToLimit();
    }

    public void start(UUID playerId, int seconds) {
        long expiresAt = System.currentTimeMillis() + seconds * 1000L;
        TraceSession replacement = new TraceSession(expiresAt);
        TraceSession previous = sessions.put(playerId, replacement);

        if (previous != null) {
            subtractEntities(previous.counters.size());
        }
    }

    public boolean isActive(UUID playerId) {
        TraceSession session = sessions.get(playerId);

        return session != null
                && System.currentTimeMillis() <= session.expiresAt;
    }

    public void record(UUID playerId, int entityId, String packetName) {
        TraceSession session = sessions.get(playerId);

        if (session == null
                || System.currentTimeMillis() > session.expiresAt) {
            return;
        }

        EntityCounters counters = session.counters.get(entityId);

        if (counters == null) {
            counters = createCountersIfCapacity(session, entityId);

            if (counters == null) {
                return;
            }
        }

        counters.record(packetName);
    }

    public TraceSnapshot snapshot(UUID playerId, int limit) {
        TraceSession session = sessions.get(playerId);

        if (session == null) {
            return new TraceSnapshot(false, 0L, List.of());
        }

        long remaining = Math.max(
                0L,
                session.expiresAt - System.currentTimeMillis()
        );
        boolean active = remaining > 0L;

        List<EntityPacketCount> top = session.counters.entrySet().stream()
                .map(entry -> entry.getValue().snapshot(entry.getKey()))
                .sorted(Comparator.comparingLong(EntityPacketCount::total).reversed())
                .limit(Math.max(1, limit))
                .toList();

        return new TraceSnapshot(active, remaining, top);
    }

    public MetricsSnapshot metricsSnapshot() {
        long now = System.currentTimeMillis();

        long activeSessions = sessions.values()
                .stream()
                .filter(session -> now <= session.expiresAt)
                .count();

        return new MetricsSnapshot(
                sessions.size(),
                (int) activeSessions,
                totalEntities.get(),
                maxEntitiesPerSession,
                highWaterEntities.get(),
                skippedEntities.sum(),
                cleanupRemovedSessions.sum(),
                trimRemovedEntities.sum()
        );
    }

    public PlayerMetricsSnapshot metricsSnapshot(UUID playerId) {
        TraceSession session = sessions.get(playerId);

        return new PlayerMetricsSnapshot(
                session == null ? 0 : session.counters.size(),
                maxEntitiesPerSession,
                session != null
                        && System.currentTimeMillis() <= session.expiresAt
        );
    }

    public void cleanup() {
        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, TraceSession> entry : sessions.entrySet()) {
            TraceSession session = entry.getValue();

            if (now > session.expiresAt + resultRetentionMs
                    && sessions.remove(entry.getKey(), session)) {
                subtractEntities(session.counters.size());
                cleanupRemovedSessions.increment();
            }
        }

        trimSessionsToLimit();
    }

    public void remove(UUID playerId) {
        TraceSession removed = sessions.remove(playerId);

        if (removed != null) {
            subtractEntities(removed.counters.size());
        }
    }

    public void clearAll() {
        sessions.clear();
        totalEntities.set(0);
    }

    private EntityCounters createCountersIfCapacity(
            TraceSession session,
            int entityId
    ) {
        synchronized (session.counters) {
            EntityCounters existing =
                    session.counters.get(entityId);

            if (existing != null) {
                return existing;
            }

            if (session.counters.size() >= maxEntitiesPerSession) {
                skippedEntities.increment();
                return null;
            }

            EntityCounters created = new EntityCounters();
            session.counters.put(entityId, created);

            int current = totalEntities.incrementAndGet();
            highWaterEntities.accumulateAndGet(current, Math::max);
            return created;
        }
    }

    private void trimSessionsToLimit() {
        for (TraceSession session : sessions.values()) {
            int excess =
                    session.counters.size() - maxEntitiesPerSession;

            if (excess <= 0) {
                continue;
            }

            int removed = 0;

            synchronized (session.counters) {
                for (Integer entityId : session.counters.keySet()) {
                    if (removed >= excess) {
                        break;
                    }

                    if (session.counters.remove(entityId) != null) {
                        removed++;
                    }
                }
            }

            if (removed > 0) {
                subtractEntities(removed);
                trimRemovedEntities.add(removed);
            }
        }
    }

    private void subtractEntities(int count) {
        if (count <= 0) {
            return;
        }

        totalEntities.updateAndGet(
                current -> Math.max(0, current - count)
        );
    }

    private static final class TraceSession {
        private final long expiresAt;
        private final Map<Integer, EntityCounters> counters =
                new ConcurrentHashMap<>();

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

    public record MetricsSnapshot(
            int sessions,
            int activeSessions,
            int entities,
            int maxEntitiesPerSession,
            int highWaterEntities,
            long skippedEntities,
            long cleanupRemovedSessions,
            long trimRemovedEntities
    ) {
    }

    public record PlayerMetricsSnapshot(
            int entities,
            int maxEntities,
            boolean active
    ) {
    }
}
