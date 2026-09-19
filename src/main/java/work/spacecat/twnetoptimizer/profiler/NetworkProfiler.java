package work.spacecat.twnetoptimizer.profiler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NetworkProfiler {
    private final JavaPlugin plugin;
    private final Map<UUID, PlayerNetworkProfile> profiles = new ConcurrentHashMap<>();
    private volatile int taskId = -1;

    public NetworkProfiler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                plugin,
                this::sampleOnlinePlayers,
                20L,
                20L
        );
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    public void recordInbound(
            UUID playerId,
            int observedBytes,
            String packetName
    ) {
        profile(playerId).recordInbound(observedBytes, packetName);
    }

    public void recordRawOutbound(
            UUID playerId,
            int observedBytes,
            String packetName
    ) {
        profile(playerId).recordRawOutbound(observedBytes, packetName);
    }

    public void recordForwardedOutbound(
            UUID playerId,
            int observedBytes,
            String packetName
    ) {
        profile(playerId).recordForwardedOutbound(observedBytes, packetName);
    }

    public ProfileSnapshot snapshot(UUID playerId) {
        PlayerNetworkProfile profile = profiles.get(playerId);
        return profile == null
                ? ProfileSnapshot.empty(playerId)
                : profile.latestSnapshot();
    }

    public List<ProfileSnapshot> topOutbound(int limit) {
        return profiles.values().stream()
                .map(PlayerNetworkProfile::latestSnapshot)
                .sorted(Comparator.comparingLong(
                        ProfileSnapshot::outboundObservedBytes
                ).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    public void reset() {
        profiles.clear();
    }

    public void remove(UUID playerId) {
        profiles.remove(playerId);
    }

    private PlayerNetworkProfile profile(UUID playerId) {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId");
        }

        return profiles.computeIfAbsent(playerId, PlayerNetworkProfile::new);
    }

    private void sampleOnlinePlayers() {
        int topPacketTypes = Math.max(
                1,
                plugin.getConfig().getInt("sampling.top-packet-types", 5)
        );
        long packetThreshold = Math.max(
                1L,
                plugin.getConfig().getLong(
                        "burst.outbound-packets-per-second",
                        800L
                )
        );
        long byteThreshold = Math.max(
                1L,
                plugin.getConfig().getLong(
                        "burst.outbound-observed-bytes-per-second",
                        1_048_576L
                )
        );
        boolean logBursts =
                plugin.getConfig().getBoolean("logging.log-bursts-to-console", false);

        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerNetworkProfile profile =
                    profiles.computeIfAbsent(
                            player.getUniqueId(),
                            PlayerNetworkProfile::new
                    );

            ProfileSnapshot snapshot = profile.rollover(
                    player.getPing(),
                    topPacketTypes,
                    packetThreshold,
                    byteThreshold
            );

            if (logBursts && snapshot.burst()) {
                plugin.getLogger().warning(
                        "Network burst for " + player.getName()
                                + ": forwarded "
                                + snapshot.outboundPackets() + " packets/s, "
                                + snapshot.outboundObservedBytes()
                                + " observed bytes/s"
                );
            }
        }
    }
}
