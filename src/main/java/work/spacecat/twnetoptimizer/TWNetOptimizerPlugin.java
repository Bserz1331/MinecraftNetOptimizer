package work.spacecat.twnetoptimizer;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import work.spacecat.twnetoptimizer.command.NetDebugCommand;
import work.spacecat.twnetoptimizer.packetevents.PacketEventsBridge;
import work.spacecat.twnetoptimizer.profiler.NetworkProfiler;

public final class TWNetOptimizerPlugin extends JavaPlugin implements Listener {
    private NetworkProfiler profiler;
    private PacketEventsBridge packetEventsBridge;
    private boolean packetEventsActive;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        profiler = new NetworkProfiler(this);
        profiler.start();

        registerCommands();
        getServer().getPluginManager().registerEvents(this, this);
        enablePacketEventsBridgeIfAvailable();

        getLogger().info("TWNetOptimizer enabled in MONITOR-ONLY mode.");
        getLogger().info(
                "This build does not cancel, delay, rewrite, or suppress packets."
        );
    }

    @Override
    public void onDisable() {
        if (packetEventsBridge != null) {
            try {
                packetEventsBridge.unregister();
            } catch (RuntimeException ex) {
                getLogger().warning(
                        "Could not unregister PacketEvents listener cleanly: "
                                + ex.getMessage()
                );
            }
        }

        if (profiler != null) {
            profiler.stop();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (profiler != null) {
            profiler.remove(event.getPlayer().getUniqueId());
        }
    }

    public NetworkProfiler getProfiler() {
        return profiler;
    }

    public boolean isPacketEventsActive() {
        return packetEventsActive;
    }

    public void reloadRuntimeConfig() {
        reloadConfig();
        if (profiler != null) {
            profiler.start();
        }
    }

    private void registerCommands() {
        PluginCommand command = getCommand("netdebug");
        if (command == null) {
            throw new IllegalStateException(
                    "netdebug command is missing from plugin.yml"
            );
        }

        NetDebugCommand executor = new NetDebugCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void enablePacketEventsBridgeIfAvailable() {
        Plugin packetEvents =
                Bukkit.getPluginManager().getPlugin("packetevents");

        if (packetEvents == null || !packetEvents.isEnabled()) {
            packetEventsActive = false;
            getLogger().warning(
                    "PacketEvents is not installed. Packet-level counters are disabled."
            );
            getLogger().warning(
                    "Ping, TPS, MSPT, commands, and Paper-side diagnostics remain available."
            );
            return;
        }

        try {
            packetEventsBridge = new PacketEventsBridge(profiler);
            packetEventsBridge.register();
            packetEventsActive = true;
            getLogger().info(
                    "PacketEvents detected. Packet profiler is active."
            );
        } catch (LinkageError | RuntimeException ex) {
            packetEventsActive = false;
            packetEventsBridge = null;
            getLogger().severe(
                    "PacketEvents integration failed: " + ex.getMessage()
            );
            getLogger().severe(
                    "TWNetOptimizer will continue without packet-level counters."
            );
        }
    }
}
