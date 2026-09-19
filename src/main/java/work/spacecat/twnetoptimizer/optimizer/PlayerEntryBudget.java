package work.spacecat.twnetoptimizer.optimizer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class PlayerEntryBudget {
    private final Map<UUID, Integer> perPlayer = new HashMap<>();

    private int globalMax;
    private int perPlayerMax;
    private int total;
    private int highWater;
    private long skippedGlobal;
    private long skippedPlayer;

    PlayerEntryBudget(int globalMax, int perPlayerMax) {
        configure(globalMax, perPlayerMax);
    }

    synchronized void configure(int globalMax, int perPlayerMax) {
        this.globalMax = Math.max(1, globalMax);
        this.perPlayerMax = Math.max(1, perPlayerMax);
    }

    synchronized boolean tryAcquire(UUID playerId) {
        if (playerId == null) {
            return false;
        }

        if (total >= globalMax) {
            skippedGlobal++;
            return false;
        }

        int current = perPlayer.getOrDefault(playerId, 0);

        if (current >= perPlayerMax) {
            skippedPlayer++;
            return false;
        }

        perPlayer.put(playerId, current + 1);
        total++;
        highWater = Math.max(highWater, total);
        return true;
    }

    synchronized void release(UUID playerId) {
        release(playerId, 1);
    }

    synchronized void release(UUID playerId, int count) {
        if (playerId == null || count <= 0) {
            return;
        }

        int current = perPlayer.getOrDefault(playerId, 0);

        if (current <= 0) {
            return;
        }

        int removed = Math.min(current, count);
        int remaining = current - removed;

        if (remaining == 0) {
            perPlayer.remove(playerId);
        } else {
            perPlayer.put(playerId, remaining);
        }

        total = Math.max(0, total - removed);
    }

    synchronized int playerCount(UUID playerId) {
        return perPlayer.getOrDefault(playerId, 0);
    }

    synchronized int totalCount() {
        return total;
    }

    synchronized int globalMax() {
        return globalMax;
    }

    synchronized int perPlayerMax() {
        return perPlayerMax;
    }

    synchronized void clearCounts() {
        perPlayer.clear();
        total = 0;
    }

    synchronized void resetMetrics() {
        highWater = total;
        skippedGlobal = 0L;
        skippedPlayer = 0L;
    }

    synchronized Snapshot snapshot(UUID playerId) {
        return new Snapshot(
                total,
                perPlayer.getOrDefault(playerId, 0),
                globalMax,
                perPlayerMax,
                highWater,
                skippedGlobal,
                skippedPlayer
        );
    }

    record Snapshot(
            int total,
            int player,
            int globalMax,
            int perPlayerMax,
            int highWater,
            long skippedGlobal,
            long skippedPlayer
    ) {
    }
}
