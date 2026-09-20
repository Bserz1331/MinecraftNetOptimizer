package work.spacecat.twnetoptimizer;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import work.spacecat.twnetoptimizer.command.NetDebugCommand;
import work.spacecat.twnetoptimizer.latency.LatencyGuardian;
import work.spacecat.twnetoptimizer.optimizer.PacketOptimizationEngine;
import work.spacecat.twnetoptimizer.packetevents.PacketEventsBridge;
import work.spacecat.twnetoptimizer.profiler.NetworkProfiler;
import work.spacecat.twnetoptimizer.trace.EntityTraceService;
import work.spacecat.twnetoptimizer.trace.VirtualEntityRegistry;

import java.util.UUID;

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
        configureLifecycleState();

        registerCommands();
        getServer().getPluginManager().registerEvents(this, this);
        enablePacketEventsBridgeIfAvailable();

        cleanupTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                this,
                this::runMaintenance,
                1200L,
                1200L
        );

        getLogger().info("MinecraftNetOptimizer enabled in OPTIMIZE-SAFE mode.");
        getLogger().info(
                "Latency Guardian is "
                        + (latencyGuardian.isEnabled() ? "enabled." : "disabled.")
        );
        getLogger().info(
                "Critical client movement/attack packets are never cancelled or throttled by MinecraftNetOptimizer."
        );
        getLogger().info(
                "Lifecycle caches are bounded and use event-driven cleanup; explicit GC is never requested."
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

        packetEventsActive = false;
        packetEventsBridge = null;

        if (cleanupTaskId != -1) {
            Bukkit.getScheduler().cancelTask(cleanupTaskId);
            cleanupTaskId = -1;
        }

        if (profiler != null) {
            profiler.stop();
        }

        clearAllRuntimeState();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (latencyGuardian == null || !latencyGuardian.isEnabled()) {
            return;
        }

        if (event.getEntity() instanceof Player victim) {
            latencyGuardian.activateCombat(
                    victim.getUniqueId(),
                    LatencyGuardian.CombatTrigger.DAMAGE_RECEIVED
            );
        }

        Player attacker = resolvePlayerAttacker(event);

        if (attacker != null) {
            latencyGuardian.activateCombat(
                    attacker.getUniqueId(),
                    LatencyGuardian.CombatTrigger.DAMAGE_DEALT
            );
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        clearPlayerLifecycleState(
                event.getPlayer().getUniqueId(),
                true
        );
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        clearPlayerLifecycleState(
                event.getPlayer().getUniqueId(),
                false
        );
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        clearPlayerLifecycleState(
                playerId,
                true
        );

        Bukkit.getScheduler().runTask(
                this,
                () -> clearPlayerLifecycleState(
                        playerId,
                        true
                )
        );
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
        configureLifecycleState();
    }

    private Player resolvePlayerAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }

        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            return player;
        }

        return null;
    }

    private void runMaintenance() {
        if (optimizer != null) {
            optimizer.cleanup();
        }

        if (traceService != null) {
            traceService.cleanup();
        }

        if (virtualEntityRegistry != null) {
            virtualEntityRegistry.cleanup();
        }
    }

    private void configureLifecycleState() {
        if (traceService != null) {
            traceService.configure(
                    getConfig().getInt(
                            "trace.max-entities-per-session",
                            2048
                    ),
                    getConfig().getLong(
                            "trace.result-retention-ms",
                            300000L
                    )
            );
        }

        if (virtualEntityRegistry != null) {
            virtualEntityRegistry.configure(
                    getConfig().getInt(
                            "trace.virtual-max-entities-per-viewer",
                            4096
                    ),
                    getConfig().getLong(
                            "trace.virtual-stale-entry-ms",
                            300000L
                    )
            );
        }
    }

    private void clearPlayerLifecycleState(
            UUID playerId,
            boolean clearProfile
    ) {
        if (optimizer != null) {
            optimizer.clearPlayer(playerId);

            if (clearProfile) {
                optimizer.stats().remove(playerId);
            }
        }

        if (virtualEntityRegistry != null) {
            virtualEntityRegistry.clearPlayer(playerId);
        }

        if (traceService != null) {
            traceService.remove(playerId);
        }

        if (latencyGuardian != null) {
            latencyGuardian.remove(playerId);
        }

        if (clearProfile && profiler != null) {
            profiler.remove(playerId);
        }
    }

    private void clearAllRuntimeState() {
        if (optimizer != null) {
            optimizer.reset();
        }

        if (virtualEntityRegistry != null) {
            virtualEntityRegistry.clearAll();
        }

        if (traceService != null) {
            traceService.clearAll();
        }

        if (latencyGuardian != null) {
            latencyGuardian.reset();
        }

        if (profiler != null) {
            profiler.reset();
        }
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
