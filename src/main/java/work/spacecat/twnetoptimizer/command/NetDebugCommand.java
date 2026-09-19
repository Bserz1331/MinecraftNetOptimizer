package work.spacecat.twnetoptimizer.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import work.spacecat.twnetoptimizer.TWNetOptimizerPlugin;
import work.spacecat.twnetoptimizer.profiler.ProfileSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
            case "reset" -> {
                plugin.getProfiler().reset();
                sender.sendMessage(prefix() + ChatColor.GREEN + "Profiler counters reset.");
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

        sender.sendMessage(ChatColor.DARK_AQUA + "----- TWNetOptimizer -----");
        sender.sendMessage(ChatColor.GRAY + "Mode: " + ChatColor.WHITE + "MONITOR-ONLY");
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
                ChatColor.GRAY + "Online players: "
                        + ChatColor.WHITE + Bukkit.getOnlinePlayers().size()
        );
        sender.sendMessage(
                ChatColor.DARK_GRAY
                        + "v0.1 never cancels, delays, rewrites, or suppresses packets."
        );
    }

    private void showPlayer(CommandSender sender, Player player) {
        ProfileSnapshot snapshot =
                plugin.getProfiler().snapshot(player.getUniqueId());

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
                        + "Observed bytes are PacketEvents buffer bytes before "
                        + "transport compression/encryption, not NIC wire bytes."
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

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        if (args.length != 1) {
            return List.of();
        }

        String input = args[0].toLowerCase(Locale.ROOT);
        List<String> options =
                new ArrayList<>(List.of("status", "top", "reset", "reload"));

        Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .forEach(options::add);

        return options.stream()
                .filter(option ->
                        option.toLowerCase(Locale.ROOT).startsWith(input))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
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
