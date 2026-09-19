package work.spacecat.twnetoptimizer.server;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ServerDiagnostics {
    private ServerDiagnostics() {
    }

    public static Snapshot capture() {
        long totalEntities = 0L;
        int loadedChunks = 0;
        Map<String, Long> entityTypes = new HashMap<>();

        for (World world : Bukkit.getWorlds()) {
            loadedChunks += world.getLoadedChunks().length;

            for (Entity entity : world.getEntities()) {
                totalEntities++;
                entityTypes.merge(entity.getType().name(), 1L, Long::sum);
            }
        }

        Map<String, Long> pendingTasks = new HashMap<>();
        for (BukkitTask task : Bukkit.getScheduler().getPendingTasks()) {
            pendingTasks.merge(task.getOwner().getName(), 1L, Long::sum);
        }

        double averagePing = Bukkit.getOnlinePlayers().stream()
                .mapToInt(Player::getPing)
                .average()
                .orElse(0.0);

        return new Snapshot(
                Bukkit.getTPS()[0],
                Bukkit.getAverageTickTime(),
                Bukkit.getViewDistance(),
                Bukkit.getSimulationDistance(),
                loadedChunks,
                totalEntities,
                Bukkit.getOnlinePlayers().size(),
                averagePing,
                top(entityTypes, 8),
                top(pendingTasks, 8)
        );
    }

    private static List<NameCount> top(Map<String, Long> source, int limit) {
        return source.entrySet().stream()
                .map(entry -> new NameCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(NameCount::count).reversed())
                .limit(limit)
                .toList();
    }

    public record Snapshot(
            double tps,
            double mspt,
            int viewDistance,
            int simulationDistance,
            int loadedChunks,
            long totalEntities,
            int onlinePlayers,
            double averagePing,
            List<NameCount> topEntityTypes,
            List<NameCount> pendingTasksByPlugin
    ) {
        public String shortSummary() {
            return String.format(
                    Locale.ROOT,
                    "TPS %.1f / MSPT %.1f / entities %d / chunks %d",
                    tps,
                    mspt,
                    totalEntities,
                    loadedChunks
            );
        }
    }

    public record NameCount(String name, long count) {
    }
}
