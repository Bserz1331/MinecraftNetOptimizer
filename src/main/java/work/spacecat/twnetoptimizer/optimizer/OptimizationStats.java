package work.spacecat.twnetoptimizer.optimizer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class OptimizationStats {
    private final Map<UUID, Counters> perPlayer = new ConcurrentHashMap<>();
    private final Counters total = new Counters();

    public void metadataSuppressed(UUID playerId) {
        counters(playerId).metadata.increment();
        total.metadata.increment();
    }

    public void uiSuppressed(UUID playerId) {
        counters(playerId).ui.increment();
        total.ui.increment();
    }

    public void particleSuppressed(UUID playerId) {
        counters(playerId).particles.increment();
        total.particles.increment();
    }

    public Snapshot snapshot(UUID playerId) {
        Counters counters = perPlayer.get(playerId);
        return counters == null ? Snapshot.ZERO : counters.snapshot();
    }

    public Snapshot totalSnapshot() {
        return total.snapshot();
    }

    public void reset() {
        perPlayer.clear();
        total.reset();
    }

    public void remove(UUID playerId) {
        perPlayer.remove(playerId);
    }

    private Counters counters(UUID playerId) {
        return perPlayer.computeIfAbsent(playerId, ignored -> new Counters());
    }

    private static final class Counters {
        private final LongAdder metadata = new LongAdder();
        private final LongAdder ui = new LongAdder();
        private final LongAdder particles = new LongAdder();

        private Snapshot snapshot() {
            return new Snapshot(metadata.sum(), ui.sum(), particles.sum());
        }

        private void reset() {
            metadata.reset();
            ui.reset();
            particles.reset();
        }
    }

    public record Snapshot(
            long metadataSuppressed,
            long uiSuppressed,
            long particleSuppressed
    ) {
        public static final Snapshot ZERO = new Snapshot(0L, 0L, 0L);

        public long totalSuppressed() {
            return metadataSuppressed + uiSuppressed + particleSuppressed;
        }
    }
}
