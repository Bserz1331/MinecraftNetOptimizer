package work.spacecat.twnetoptimizer;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import work.spacecat.twnetoptimizer.command.NetDebugCommand;
import work.spacecat.twnetoptimizer.latency.LatencyGuardian;
import work.spacecat.twnetoptimizer.optimizer.PacketOptimizationEngine;
import work.spacecat.twnetoptimizer.packetevents.PacketEventsBridge;
import work.spacecat.twnetoptimizer.profiler.NetworkProfiler;
import work.spacecat.twnetoptimizer.trace.EntityTraceService;
import work.spacecat.twnetoptimizer.trace.VirtualEntityRegistry;

public final class TWNetOptimizerPlugin extends JavaPlugin implements Listener {
    private NetworkProfiler profiler;
    private LatencyGuardian latencyGuardian;
    private PacketOptimizationEngine optimizer;
    private EntityTraceService traceService;
    private VirtualEntityRegistry virtualEntityRegistry;
    private PacketEventsBridge packetEventsBridge;
    private boolean packetEventsActive;
    private int cleanupTaskId = -1;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        profiler = new NetworkProfiler(this);
        profiler.start();

        latencyGuardian = new LatencyGuardian(this);
        optimizer = new PacketOptimizationEngine(this, profiler, latencyGuardian);

        traceService = new EntityTraceService();
        virtualEntityRegistry = new VirtualEntityRegistry();

        registerCommands();
        getServer().getPluginManager().registerEvents(this, this);
        enablePacketEventsBridgeIfAvailable();

        cleanupTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                this,
                optimizer::cleanup,
                1200L,
                1200L
        );

        getLogger().info("TWNetOptimizer enabled in OPTIMIZE-SAFE mode.");
        getLogger().info(
                "Latency Guardian is "
                        + (latencyGuardian.isEnabled() ? "enabled." : "disabled.")
        );
        getLogger().info(
                "Critical client movement/attack packets are never cancelled or throttled by TWNetOptimizer."
        );
    }

    @Override
    public void onDisable() {
        if (packetEventsBridge != null) {
            try {
                packetEventsBridge.unregister();
            } catch (RuntimeException ex) {
                getLogger().warning(
                        "Could not unregister PacketEvents listeners cleanly: "
                                + ex.getMessage()
                );
            }
        }

        if (cleanupTaskId != -1) {
            Bukkit.getScheduler().cancelTask(cleanupTaskId);
            cleanupTaskId = -1;
        }

        if (profiler != null) {
            profiler.stop();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        clearPlayerState(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        clearPlayerState(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        java.util.UUID playerId = event.getPlayer().getUniqueId();

        clearPlayerState(playerId);
        profiler.remove(playerId);
        optimizer.stats().remove(playerId);
        traceService.remove(playerId);
        virtualEntityRegistry.clearPlayer(playerId);
        latencyGuardian.remove(playerId);
    }

    public NetworkProfiler getProfiler() {
        return profiler;
    }

    public PacketOptimizationEngine getOptimizer() {
        return optimizer;
    }

    public EntityTraceService getTraceService() {
        return traceService;
    }

    public VirtualEntityRegistry getVirtualEntityRegistry() {
        return virtualEntityRegistry;
    }

    public LatencyGuardian getLatencyGuardian() {
        return latencyGuardian;
    }

    public boolean isPacketEventsActive() {
        return packetEventsActive;
    }

    public void reloadRuntimeConfig() {
        reloadConfig();
        profiler.start();
        latencyGuardian.reload();
        optimizer.reload();
    }

    private void clearPlayerState(java.util.UUID playerId) {
        if (optimizer != null) optimizer.clearPlayer(playerId);
        if (virtualEntityRegistry != null) virtualEntityRegistry.clearPlayer(playerId);
        if (latencyGuardian != null) latencyGuardian.remove(playerId);
    }

    private void registerCommands() {
        PluginCommand command = getCommand("netdebug");

        if (command == null) {
            throw new IllegalStateException("netdebug command is missing from plugin.yml");
        }

        NetDebugCommand executor = new NetDebugCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void enablePacketEventsBridgeIfAvailable() {
        Plugin packetEvents = Bukkit.getPluginManager().getPlugin("packetevents");

        if (packetEvents == null || !packetEvents.isEnabled()) {
            packetEventsActive = false;
            getLogger().warning(
                    "PacketEvents is not installed. Packet-level profiling and optimization are disabled."
            );
            return;
        }

        try {
            packetEventsBridge = new PacketEventsBridge(
                    profiler,
                    optimizer,
                    traceService,
                    virtualEntityRegistry,
                    latencyGuardian
            );
            packetEventsBridge.register();
            packetEventsActive = true;

            getLogger().info(
                    "PacketEvents detected. Packet profiler, safe optimizer and Latency Guardian are active."
            );
        } catch (LinkageError | RuntimeException ex) {
            packetEventsActive = false;
            packetEventsBridge = null;

            getLogger().severe(
                    "PacketEvents integration failed: " + ex.getMessage()
            );
        }
    }
}
