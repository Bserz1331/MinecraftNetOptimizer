package work.spacecat.twnetoptimizer.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import work.spacecat.twnetoptimizer.TWNetOptimizerPlugin;
import work.spacecat.twnetoptimizer.optimizer.OptimizationStats;
import work.spacecat.twnetoptimizer.profiler.ProfileSnapshot;
import work.spacecat.twnetoptimizer.server.AdviceEngine;
import work.spacecat.twnetoptimizer.server.ServerDiagnostics;
import work.spacecat.twnetoptimizer.trace.EntityTraceService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class NetDebugCommand implements CommandExecutor, TabCompleter {
    private final TWNetOptimizerPlugin plugin;

    public NetDebugCommand(TWNetOptimizerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                showPlayer(sender, player);
            } else {
                showStatus(sender);
            }
            return true;
        }

        String first = args[0].toLowerCase(Locale.ROOT);
        switch (first) {
            case "status" -> showStatus(sender);
            case "top" -> showTop(sender);
            case "server" -> showServer(sender);
            case "advice" -> showAdvice(sender);
            case "trace" -> startTrace(sender, args);
            case "entities" -> showEntities(sender, args);
            case "optimize" -> optimize(sender, args);
            case "reset" -> {
                plugin.getProfiler().reset();
                plugin.getOptimizer().reset();
                sender.sendMessage(prefix() + ChatColor.GREEN + "Profiler and optimizer counters reset.");
            }
            case "reload" -> {
                plugin.reloadRuntimeConfig();
                sender.sendMessage(prefix() + ChatColor.GREEN + "Configuration reloaded.");
            }
            default -> {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    sender.sendMessage(
                            prefix() + ChatColor.RED
                                    + "Player not found: " + args[0]
                    );
                    return true;
                }
                showPlayer(sender, target);
            }
        }

        return true;
    }

    private void showStatus(CommandSender sender) {
        double[] tps = Bukkit.getTPS();
        double mspt = Bukkit.getAverageTickTime();
        OptimizationStats.Snapshot optimization =
                plugin.getOptimizer().stats().totalSnapshot();

        sender.sendMessage(ChatColor.DARK_AQUA + "----- TWNetOptimizer -----");
        sender.sendMessage(
                ChatColor.GRAY + "Mode: " + ChatColor.WHITE + "OPTIMIZE-SAFE"
        );
        sender.sendMessage(
                ChatColor.GRAY + "Optimizer: "
                        + (plugin.getOptimizer().isEnabled()
                        ? ChatColor.GREEN + "enabled"
                        : ChatColor.YELLOW + "disabled")
        );
        sender.sendMessage(
                ChatColor.GRAY + "PacketEvents: "
                        + (plugin.isPacketEventsActive()
                        ? ChatColor.GREEN + "active"
                        : ChatColor.YELLOW + "not installed / inactive")
        );
        sender.sendMessage(
                ChatColor.GRAY + "TPS (1m): "
                        + colorTps(tps[0]) + format(tps[0])
        );
        sender.sendMessage(
                ChatColor.GRAY + "MSPT avg: "
                        + colorMspt(mspt) + format(mspt) + " ms"
        );
        sender.sendMessage(
                ChatColor.GRAY + "Suppressed: " + ChatColor.WHITE
                        + optimization.totalSuppressed()
                        + " total (metadata "
                        + optimization.metadataSuppressed()
                        + " / UI "
                        + optimization.uiSuppressed()
                        + " / particles "
                        + optimization.particleSuppressed()
                        + ")"
        );
        sender.sendMessage(
                ChatColor.DARK_GRAY
                        + "Critical movement/teleport/velocity/combat/inventory/chunk/KeepAlive packets are excluded."
        );
    }

    private void showPlayer(CommandSender sender, Player player) {
        ProfileSnapshot snapshot =
                plugin.getProfiler().snapshot(player.getUniqueId());
        OptimizationStats.Snapshot optimization =
                plugin.getOptimizer().stats().snapshot(player.getUniqueId());

        long categorized =
                snapshot.chunkPackets()
                        + snapshot.entityPackets()
                        + snapshot.uiPackets()
                        + snapshot.otherPackets();

        double chunkShare =
                categorized == 0 ? 0.0
                        : snapshot.chunkPackets() * 100.0 / categorized;
        double entityShare =
                categorized == 0 ? 0.0
                        : snapshot.entityPackets() * 100.0 / categorized;
        double uiShare =
                categorized == 0 ? 0.0
                        : snapshot.uiPackets() * 100.0 / categorized;

        sender.sendMessage(
                ChatColor.DARK_AQUA
                        + "----- NetDebug: " + player.getName() + " -----"
        );
        sender.sendMessage(
                ChatColor.GRAY + "Ping: "
                        + pingColor(snapshot.pingMs())
                        + snapshot.pingMs() + " ms"
        );
        sender.sendMessage(
                ChatColor.GRAY + "Packets: " + ChatColor.WHITE
                        + snapshot.inboundPackets() + " in/s, "
                        + snapshot.outboundPackets() + " out/s"
        );
        sender.sendMessage(
                ChatColor.GRAY + "Observed bytes: " + ChatColor.WHITE
                        + humanBytes(snapshot.inboundObservedBytes()) + " in/s, "
                        + humanBytes(snapshot.outboundObservedBytes()) + " out/s"
        );
        sender.sendMessage(
                ChatColor.GRAY + "Outbound mix: " + ChatColor.WHITE
                        + "chunk " + format(chunkShare)
                        + "% / entity " + format(entityShare)
                        + "% / UI " + format(uiShare) + "%"
        );
        sender.sendMessage(
                ChatColor.GRAY + "Burst: "
                        + (snapshot.burst()
                        ? ChatColor.RED + "HIGH"
                        : ChatColor.GREEN + "normal")
        );
        sender.sendMessage(
                ChatColor.GRAY + "Suppressed since reset: " + ChatColor.WHITE
                        + optimization.totalSuppressed()
                        + " (metadata " + optimization.metadataSuppressed()
                        + " / UI " + optimization.uiSuppressed()
                        + " / particles " + optimization.particleSuppressed() + ")"
        );

        if (!snapshot.topOutboundTypes().isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Top outbound packets:");
            for (ProfileSnapshot.PacketTypeCount item
                    : snapshot.topOutboundTypes()) {
                sender.sendMessage(
                        ChatColor.DARK_GRAY + "  - " + ChatColor.WHITE
                                + item.packetName()
                                + ChatColor.GRAY + ": "
                                + item.count() + "/s"
                );
            }
        }

        sender.sendMessage(
                ChatColor.DARK_GRAY
                        + "Observed bytes are PacketEvents buffer bytes, not NIC wire bytes."
        );
    }

    private void showTop(CommandSender sender) {
        List<ProfileSnapshot> snapshots =
                plugin.getProfiler().topOutbound(10);

        sender.sendMessage(
                ChatColor.DARK_AQUA + "----- Top outbound traffic -----"
        );

        if (snapshots.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No samples yet.");
            return;
        }

        int rank = 1;
        for (ProfileSnapshot snapshot : snapshots) {
            Player player = Bukkit.getPlayer(snapshot.playerId());
            String name = player == null
                    ? snapshot.playerId().toString().substring(0, 8)
                    : player.getName();

            sender.sendMessage(
                    ChatColor.GRAY + String.valueOf(rank++)
                            + ". " + ChatColor.WHITE + name
                            + ChatColor.GRAY + " - "
                            + humanBytes(snapshot.outboundObservedBytes())
                            + "/s, " + snapshot.outboundPackets() + " pkt/s"
                            + (snapshot.burst()
                            ? ChatColor.RED + " BURST"
                            : "")
            );
        }
    }

    private void startTrace(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(prefix() + ChatColor.YELLOW + "Usage: /netdebug trace <player> [seconds]");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(prefix() + ChatColor.RED + "Player not found: " + args[1]);
            return;
        }

        int seconds = plugin.getConfig().getInt("trace.default-seconds", 10);
        if (args.length >= 3) {
            try {
                seconds = Integer.parseInt(args[2]);
            } catch (NumberFormatException ignored) {
                sender.sendMessage(prefix() + ChatColor.RED + "Seconds must be a number.");
                return;
            }
        }

        int maxSeconds = Math.max(1, plugin.getConfig().getInt("trace.max-seconds", 60));
        seconds = Math.max(1, Math.min(seconds, maxSeconds));

        plugin.getTraceService().start(target.getUniqueId(), seconds);
        sender.sendMessage(
                prefix() + ChatColor.GREEN
                        + "Tracing ENTITY_METADATA / TELEPORT / VELOCITY for "
                        + target.getName() + " for " + seconds + "s."
        );
    }

    private void showEntities(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(prefix() + ChatColor.YELLOW + "Usage: /netdebug entities <player>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(prefix() + ChatColor.RED + "Player not found: " + args[1]);
            return;
        }

        int limit = Math.max(1, plugin.getConfig().getInt("trace.top-entities", 15));
        EntityTraceService.TraceSnapshot snapshot =
                plugin.getTraceService().snapshot(target.getUniqueId(), limit);

        sender.sendMessage(
                ChatColor.DARK_AQUA
                        + "----- Entity trace: " + target.getName() + " -----"
        );

        if (snapshot.entities().isEmpty()) {
            sender.sendMessage(
                    ChatColor.GRAY + "No trace data. Run /netdebug trace "
                            + target.getName() + " 10 first."
            );
            return;
        }

        Map<Integer, String> types = loadedEntityTypes(target.getWorld());

        if (snapshot.active()) {
            sender.sendMessage(
                    ChatColor.GRAY + "Trace active, "
                            + Math.max(1L, snapshot.remainingMs() / 1000L)
                            + "s remaining."
            );
        }

        int virtual = 0;
        for (EntityTraceService.EntityPacketCount item : snapshot.entities()) {
            String type = types.get(item.entityId());
            if (type == null) {
                type = "UNKNOWN / VIRTUAL";
                virtual++;
            }

            sender.sendMessage(
                    ChatColor.GRAY + "#" + item.entityId()
                            + " " + ChatColor.WHITE + type
                            + ChatColor.GRAY + " total=" + item.total()
                            + " meta=" + item.metadata()
                            + " tp=" + item.teleport()
                            + " vel=" + item.velocity()
            );
        }

        sender.sendMessage(
                ChatColor.GRAY + "Top-list unknown/virtual IDs: "
                        + ChatColor.WHITE + virtual
        );
    }

    private void showServer(CommandSender sender) {
        ServerDiagnostics.Snapshot snapshot = ServerDiagnostics.capture();

        sender.sendMessage(ChatColor.DARK_AQUA + "----- Server diagnostics -----");
        sender.sendMessage(
                ChatColor.GRAY + "TPS / MSPT: " + ChatColor.WHITE
                        + format(snapshot.tps()) + " / "
                        + format(snapshot.mspt()) + " ms"
        );
        sender.sendMessage(
                ChatColor.GRAY + "View / simulation: " + ChatColor.WHITE
                        + snapshot.viewDistance() + " / "
                        + snapshot.simulationDistance()
        );
        sender.sendMessage(
                ChatColor.GRAY + "Loaded chunks / entities: " + ChatColor.WHITE
                        + snapshot.loadedChunks() + " / "
                        + snapshot.totalEntities()
        );
        sender.sendMessage(
                ChatColor.GRAY + "Players / avg ping: " + ChatColor.WHITE
                        + snapshot.onlinePlayers() + " / "
                        + format(snapshot.averagePing()) + " ms"
        );

        if (!snapshot.topEntityTypes().isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Top entity types:");
            for (ServerDiagnostics.NameCount item : snapshot.topEntityTypes()) {
                sender.sendMessage(
                        ChatColor.DARK_GRAY + "  - "
                                + ChatColor.WHITE + item.name()
                                + ChatColor.GRAY + ": " + item.count()
                );
            }
        }

        if (!snapshot.pendingTasksByPlugin().isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Pending scheduler tasks by plugin:");
            for (ServerDiagnostics.NameCount item : snapshot.pendingTasksByPlugin()) {
                sender.sendMessage(
                        ChatColor.DARK_GRAY + "  - "
                                + ChatColor.WHITE + item.name()
                                + ChatColor.GRAY + ": " + item.count()
                );
            }
        }
    }

    private void showAdvice(CommandSender sender) {
        ServerDiagnostics.Snapshot server = ServerDiagnostics.capture();
        List<String> advice = AdviceEngine.build(
                server,
                plugin.getProfiler().topOutbound(20),
                plugin.getOptimizer().stats().totalSnapshot()
        );

        sender.sendMessage(ChatColor.DARK_AQUA + "----- TWNetOptimizer advice -----");
        int index = 1;
        for (String line : advice) {
            sender.sendMessage(
                    ChatColor.GRAY + String.valueOf(index++)
                            + ". " + ChatColor.WHITE + line
            );
        }
    }

    private void optimize(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
            sender.sendMessage(
                    prefix() + ChatColor.GRAY + "Optimizer is "
                            + (plugin.getOptimizer().isEnabled()
                            ? ChatColor.GREEN + "enabled"
                            : ChatColor.YELLOW + "disabled")
            );
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "on" -> {
                plugin.getOptimizer().setEnabled(true);
                sender.sendMessage(prefix() + ChatColor.GREEN + "Safe optimizer enabled.");
            }
            case "off" -> {
                plugin.getOptimizer().setEnabled(false);
                sender.sendMessage(prefix() + ChatColor.YELLOW + "Optimizer disabled and caches cleared.");
            }
            case "reset" -> {
                plugin.getOptimizer().reset();
                sender.sendMessage(prefix() + ChatColor.GREEN + "Optimizer counters and caches reset.");
            }
            default -> sender.sendMessage(
                    prefix() + ChatColor.YELLOW
                            + "Usage: /netdebug optimize <status|on|off|reset>"
            );
        }
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            List<String> options = new ArrayList<>(List.of(
                    "status",
                    "top",
                    "server",
                    "advice",
                    "trace",
                    "entities",
                    "optimize",
                    "reset",
                    "reload"
            ));
            Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .forEach(options::add);

            return options.stream()
                    .filter(option ->
                            option.toLowerCase(Locale.ROOT).startsWith(input))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }

        if (args.length == 2
                && (args[0].equalsIgnoreCase("trace")
                || args[0].equalsIgnoreCase("entities"))) {
            String input = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("optimize")) {
            String input = args[1].toLowerCase(Locale.ROOT);
            return List.of("status", "on", "off", "reset").stream()
                    .filter(option -> option.startsWith(input))
                    .toList();
        }

        return List.of();
    }

    private static Map<Integer, String> loadedEntityTypes(World world) {
        Map<Integer, String> result = new HashMap<>();
        for (Entity entity : world.getEntities()) {
            result.put(entity.getEntityId(), entity.getType().name());
        }
        return result;
    }

    private static String prefix() {
        return ChatColor.DARK_AQUA + "[TWNetOptimizer] ";
    }

    private static ChatColor pingColor(int ping) {
        if (ping < 0) return ChatColor.GRAY;
        if (ping < 100) return ChatColor.GREEN;
        if (ping < 180) return ChatColor.YELLOW;
        return ChatColor.RED;
    }

    private static ChatColor colorTps(double tps) {
        if (tps >= 19.5) return ChatColor.GREEN;
        if (tps >= 18.0) return ChatColor.YELLOW;
        return ChatColor.RED;
    }

    private static ChatColor colorMspt(double mspt) {
        if (mspt < 40.0) return ChatColor.GREEN;
        if (mspt < 50.0) return ChatColor.YELLOW;
        return ChatColor.RED;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }

        double kib = bytes / 1024.0;
        if (kib < 1024.0) {
            return format(kib) + " KiB";
        }

        return format(kib / 1024.0) + " MiB";
    }
}
