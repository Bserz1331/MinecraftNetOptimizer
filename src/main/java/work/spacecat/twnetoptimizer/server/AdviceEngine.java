package work.spacecat.twnetoptimizer.server;

import work.spacecat.twnetoptimizer.optimizer.OptimizationStats;
import work.spacecat.twnetoptimizer.profiler.ProfileSnapshot;

import java.util.ArrayList;
import java.util.List;

public final class AdviceEngine {
    private AdviceEngine() {
    }

    public static List<String> build(
            ServerDiagnostics.Snapshot server,
            List<ProfileSnapshot> traffic,
            OptimizationStats.Snapshot optimization
    ) {
        List<String> advice = new ArrayList<>();

        long maxOutboundPackets = traffic.stream()
                .mapToLong(ProfileSnapshot::outboundPackets)
                .max()
                .orElse(0L);

        double maxEntityShare = traffic.stream()
                .mapToDouble(AdviceEngine::entityShare)
                .max()
                .orElse(0.0);

        long maxChunkPackets = traffic.stream()
                .mapToLong(ProfileSnapshot::chunkPackets)
                .max()
                .orElse(0L);

        if (maxEntityShare >= 0.70 && maxOutboundPackets >= 1000L) {
            advice.add(
                    "Entity traffic dominates. Use /netdebug trace <player> 10, then /netdebug entities <player> to identify hot entity IDs before changing teleport or velocity behavior."
            );
        }

        if (optimization.metadataSuppressed() > 0L) {
            advice.add(
                    "Identical ENTITY_METADATA is being suppressed successfully. If this number grows quickly, the source plugin is repeatedly emitting unchanged state and should be fixed upstream when possible."
            );
        }

        if (maxChunkPackets >= 100L) {
            advice.add(
                    "Chunk traffic is high for at least one player. Review Paper chunk send rates, pregeneration, view distance, and high-speed exploration behavior."
            );
        }

        if (server.mspt() >= 45.0) {
            advice.add(
                    "MSPT is close to the 50 ms tick budget. Network filtering alone will not solve this; inspect plugin scheduled work, entities, and world simulation."
            );
        }

        if (server.totalEntities() >= 1500L) {
            advice.add(
                    "Loaded entity count is high. Review entity activation/tracking ranges, persistent NPC or display entities, and farms."
            );
        }

        if (server.simulationDistance() >= server.viewDistance() && server.viewDistance() >= 8) {
            advice.add(
                    "Simulation distance is as large as view distance. A lower simulation distance can reduce server-side ticking while preserving visual distance, if gameplay requirements allow it."
            );
        }

        if (server.pendingTasksByPlugin().stream().anyMatch(item -> item.count() >= 20L)) {
            advice.add(
                    "At least one plugin owns many pending Bukkit scheduler tasks. This does not prove a 1-tick loop, but it is worth reviewing that plugin's timers and update cadence."
            );
        }

        if (optimization.particleSuppressed() > 0L) {
            advice.add(
                    "Particle throttling has activated. These drops are cosmetic and are separated from gameplay-critical packets."
            );
        }

        if (advice.isEmpty()) {
            advice.add(
                    "No strong server-side bottleneck is visible from the current counters. Collect samples during the exact lag scenario before changing Paper or plugin settings."
            );
        }

        return advice;
    }

    private static double entityShare(ProfileSnapshot snapshot) {
        long total = snapshot.chunkPackets()
                + snapshot.entityPackets()
                + snapshot.uiPackets()
                + snapshot.otherPackets();

        if (total == 0L) {
            return 0.0;
        }

        return (double) snapshot.entityPackets() / (double) total;
    }
}
