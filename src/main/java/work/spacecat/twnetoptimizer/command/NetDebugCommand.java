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
import work.spacecat.twnetoptimizer.latency.LatencyGuardian;
import work.spacecat.twnetoptimizer.optimizer.OptimizationStats;
import work.spacecat.twnetoptimizer.profiler.ProfileSnapshot;
import work.spacecat.twnetoptimizer.server.AdviceEngine;
import work.spacecat.twnetoptimizer.server.ServerDiagnostics;
import work.spacecat.twnetoptimizer.trace.EntityTraceService;
import work.spacecat.twnetoptimizer.trace.VirtualEntityRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
            case "virtual" -> showVirtual(sender, args);
            case "latency" -> showLatency(sender, args);
            case "optimize" -> optimize(sender, args);
            case "reset" -> {
                plugin.getProfiler().reset();
                plugin.getOptimizer().reset();
                plugin.getVirtualEntityRegistry().resetAllActivity();
                plugin.getLatencyGuardian().reset();
                sender.sendMessage(
                        prefix() + ChatColor.GREEN
                                + "Profiler, optimizer, virtual-entity and latency counters reset."
                );
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
        sender.sendMessage(ChatColor.GRAY + "Mode: " + ChatColor.WHITE + "OPTIMIZE-SAFE");
        sender.sendMessage(
                ChatColor.GRAY + "Optimizer: "
                        + (plugin.getOptimizer().isEnabled()
                        ? ChatColor.GREEN + "enabled"
                        : ChatColor.YELLOW + "disabled")
        );
        sender.sendMessage(
                ChatColor.GRAY + "Latency Guardian: "
                        + (plugin.getLatencyGuardian().isEnabled()
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
                ChatColor.GRAY + "TWNO suppressed since reset: "
                        + ChatColor.WHITE
                        + optimization.totalSuppressed()
                        + " total (metadata " + optimization.metadataSuppressed()
                        + " / UI " + optimization.uiSuppressed()
                        + " / particles " + optimization.particleSuppressed()
                        + ")"
        );
        sender.sendMessage(
                ChatColor.DARK_GRAY
                        + "Critical movement/attack input is observe-only and never throttled by TWNetOptimizer."
        );
    }

    private void showPlayer(CommandSender sender, Player player) {
        ProfileSnapshot snapshot =
                plugin.getProfiler().snapshot(player.getUniqueId());

        OptimizationStats.Snapshot optimization =
                plugin.getOptimizer().stats().snapshot(player.getUniqueId());

        LatencyGuardian.Snapshot latency =
                plugin.getLatencyGuardian().snapshot(
                        player.getUniqueId(),
                        snapshot
                );

        long categorized =
                snapshot.chunkPackets()
                        + snapshot.entityPackets()
                        + snapshot.uiPackets()
                        + snapshot.otherPackets();

        double chunkShare = categorized == 0
                ? 0.0
                : snapshot.chunkPackets() * 100.0 / categorized;
        double entityShare = categorized == 0
                ? 0.0
                : snapshot.entityPackets() * 100.0 / categorized;
        double uiShare = categorized == 0
                ? 0.0
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
                ChatColor.GRAY + "Priority: "
                        + priorityColor(latency.mode())
                        + latency.mode().name()
        );
        sender.sendMessage(
                ChatColor.GRAY + "Inbound: " + ChatColor.WHITE
                        + snapshot.inboundPackets() + " pkt/s, "
                        + humanBytes(snapshot.inboundObservedBytes()) + "/s"
        );
        sender.sendMessage(
                ChatColor.GRAY + "Raw outbound: " + ChatColor.WHITE
                        + snapshot.rawOutboundPackets() + " pkt/s, "
                        + humanBytes(snapshot.rawOutboundObservedBytes()) + "/s"
        );
        sender.sendMessage(
                ChatColor.GRAY + "Forwarded outbound: " + ChatColor.WHITE
                        + snapshot.outboundPackets() + " pkt/s, "
                        + humanBytes(snapshot.outboundObservedBytes()) + "/s"
        );
        sender.sendMessage(
                ChatColor.GRAY + "Not forwarded: "
                        + reductionColor(snapshot.packetReductionPercent())
                        + snapshot.notForwardedPackets() + " pkt/s ("
                        + format(snapshot.packetReductionPercent()) + "%), "
                        + humanBytes(snapshot.notForwardedObservedBytes()) + "/s"
        );
        sender.sendMessage(
                ChatColor.GRAY + "Forwarded mix: " + ChatColor.WHITE
                        + "chunk " + format(chunkShare)
                        + "% / entity " + format(entityShare)
                        + "% / UI " + format(uiShare) + "%"
        );
        sender.sendMessage(
                ChatColor.GRAY + "Burst after filtering: "
                        + (snapshot.burst()
                        ? ChatColor.RED + "HIGH"
                        : ChatColor.GREEN + "normal")
        );
        sender.sendMessage(
                ChatColor.GRAY + "TWNO suppressed since reset: "
                        + ChatColor.WHITE
                        + optimization.totalSuppressed()
                        + " (metadata " + optimization.metadataSuppressed()
                        + " / UI " + optimization.uiSuppressed()
                        + " / particles " + optimization.particleSuppressed()
                        + ")"
        );

        showPacketTypes(sender, "Top raw outbound:", snapshot.topRawOutboundTypes());
        showPacketTypes(sender, "Top forwarded outbound:", snapshot.topOutboundTypes());
    }

    private void showLatency(CommandSender sender, String[] args) {
        Player target;

        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(
                    prefix() + ChatColor.YELLOW
                            + "Usage: /netdebug latency <player>"
            );
            return;
        }

        if (target == null) {
            sender.sendMessage(prefix() + ChatColor.RED + "Player not found.");
            return;
        }

        ProfileSnapshot profile =
                plugin.getProfiler().snapshot(target.getUniqueId());

        LatencyGuardian.Snapshot latency =
                plugin.getLatencyGuardian().snapshot(
                        target.getUniqueId(),
                        profile
                );

        sender.sendMessage(
                ChatColor.DARK_AQUA
                        + "----- Latency Guardian: "
                        + target.getName() + " -----"
        );
        sender.sendMessage(
                ChatColor.GRAY + "Ping: "
                        + pingColor(profile.pingMs())
                        + profile.pingMs() + " ms"
        );
        sender.sendMessage(
                ChatColor.GRAY + "Priority mode: "
                        + priorityColor(latency.mode())
                        + latency.mode().name()
        );
        sender.sendMessage(
                ChatColor.GRAY + "Critical inbound current second: "
                        + ChatColor.WHITE
                        + "movement " + latency.movementPacketsCurrentSecond()
                        + " / attack " + latency.attackPacketsCurrentSecond()
        );
        sender.sendMessage(
                ChatColor.GRAY + "Main-thread handoff: "
                        + ChatColor.WHITE
                        + "avg " + format(latency.averageHandoffMs())
                        + " ms / p95 " + format(latency.p95HandoffMs())
                        + " ms / max " + format(latency.maxHandoffMs())
                        + " ms"
        );
        sender.sendMessage(
                ChatColor.GRAY + "Handoff samples: "
                        + ChatColor.WHITE + latency.handoffSamples()
        );
        sender.sendMessage(
                ChatColor.GRAY + "Raw / forwarded outbound: "
                        + ChatColor.WHITE
                        + profile.rawOutboundPackets()
                        + " / " + profile.outboundPackets()
                        + " pkt/s"
        );
        sender.sendMessage(
                ChatColor.GRAY + "Current packet reduction: "
                        + reductionColor(profile.packetReductionPercent())
                        + format(profile.packetReductionPercent()) + "%"
        );
        sender.sendMessage(
                ChatColor.GRAY + "Critical input filtered by TWNO: "
                        + ChatColor.GREEN + "0"
        );
        sender.sendMessage(
                ChatColor.DARK_GRAY
                        + "Handoff is sampled Netty arrival -> Bukkit main-thread availability, not exact attack resolution time."
        );
    }

    private void showPacketTypes(
            CommandSender sender,
            String title,
            List<ProfileSnapshot.PacketTypeCount> items
    ) {
        if (items.isEmpty()) return;

        sender.sendMessage(ChatColor.GRAY + title);

        for (ProfileSnapshot.PacketTypeCount item : items) {
            sender.sendMessage(
                    ChatColor.DARK_GRAY + "  - " + ChatColor.WHITE
                            + item.packetName()
                            + ChatColor.GRAY + ": "
                            + item.count() + "/s"
            );
        }
    }

    private void showTop(CommandSender sender) {
        List<ProfileSnapshot> snapshots = plugin.getProfiler().topOutbound(10);

        sender.sendMessage(
                ChatColor.DARK_AQUA + "----- Top forwarded traffic -----"
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
                            + "/s, " + snapshot.outboundPackets()
                            + " pkt/s (raw " + snapshot.rawOutboundPackets() + ")"
                            + (snapshot.burst() ? ChatColor.RED + " BURST" : "")
            );
        }
    }

    private void startTrace(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(
                    prefix() + ChatColor.YELLOW
                            + "Usage: /netdebug trace <player> [seconds]"
            );
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);

        if (target == null) {
            sender.sendMessage(
                    prefix() + ChatColor.RED + "Player not found: " + args[1]
            );
            return;
        }

        int seconds = plugin.getConfig().getInt("trace.default-seconds", 10);

        if (args.length >= 3) {
            try {
                seconds = Integer.parseInt(args[2]);
            } catch (NumberFormatException ignored) {
                sender.sendMessage(
                        prefix() + ChatColor.RED + "Seconds must be a number."
                );
                return;
            }
        }

        int maxSeconds = Math.max(
                1,
                plugin.getConfig().getInt("trace.max-seconds", 60)
        );

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
            sender.sendMessage(
                    prefix() + ChatColor.YELLOW
                            + "Usage: /netdebug entities <player>"
            );
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);

        if (target == null) {
            sender.sendMessage(
                    prefix() + ChatColor.RED + "Player not found: " + args[1]
            );
            return;
        }

        int limit = Math.max(
                1,
                plugin.getConfig().getInt("trace.top-entities", 15)
        );

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

        int virtual = 0;

        for (EntityTraceService.EntityPacketCount item : snapshot.entities()) {
            String type = types.get(item.entityId());

            if (type == null) {
                virtual++;
                type = plugin.getVirtualEntityRegistry()
                        .find(target.getUniqueId(), item.entityId())
                        .map(info ->
                                "VIRTUAL " + info.entityType()
                                        + " [" + info.spawnPacket() + "]")
                        .orElse("UNKNOWN / VIRTUAL");
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
                ChatColor.GRAY + "Top-list virtual IDs: "
                        + ChatColor.WHITE + virtual
        );
    }

    private void showVirtual(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(
                    prefix() + ChatColor.YELLOW
                            + "Usage: /netdebug virtual <player>"
            );
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);

        if (target == null) {
            sender.sendMessage(
                    prefix() + ChatColor.RED + "Player not found: " + args[1]
            );
            return;
        }

        UUID viewerId = target.getUniqueId();
        Set<Integer> bukkitEntityIds = allLoadedBukkitEntityIds();

        List<VirtualEntityRegistry.Snapshot> all =
                plugin.getVirtualEntityRegistry().snapshot(viewerId);

        List<VirtualEntityRegistry.Snapshot> virtual = all.stream()
                .filter(item -> !bukkitEntityIds.contains(item.entityId()))
                .sorted(
                        Comparator.comparingLong(
                                VirtualEntityRegistry.Snapshot::totalActivity
                        ).reversed()
                )
                .toList();

        sender.sendMessage(
                ChatColor.DARK_AQUA
                        + "----- Virtual entities: " + target.getName() + " -----"
        );
        sender.sendMessage(
                ChatColor.GRAY + "Tracked client entity IDs: "
                        + ChatColor.WHITE + all.size()
                        + ChatColor.GRAY + " / virtual: "
                        + ChatColor.WHITE + virtual.size()
        );

        if (virtual.isEmpty()) {
            sender.sendMessage(
                    ChatColor.GRAY + "No currently tracked virtual entity IDs."
            );
            return;
        }

        Map<String, Long> typeCounts = virtual.stream()
                .collect(
                        Collectors.groupingBy(
                                VirtualEntityRegistry.Snapshot::entityType,
                                Collectors.counting()
                        )
                );

        LinkedHashMap<String, Long> sortedTypes = typeCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (left, right) -> left,
                                LinkedHashMap::new
                        )
                );

        sender.sendMessage(ChatColor.GRAY + "Top virtual spawn types:");

        for (Map.Entry<String, Long> entry : sortedTypes.entrySet()) {
            sender.sendMessage(
                    ChatColor.DARK_GRAY + "  - "
                            + ChatColor.WHITE + entry.getKey()
                            + ChatColor.GRAY + ": " + entry.getValue()
            );
        }

        int limit = Math.max(
                1,
                plugin.getConfig().getInt("trace.top-virtual-entities", 15)
        );

        sender.sendMessage(ChatColor.GRAY + "Top virtual entity activity:");

        virtual.stream()
                .limit(limit)
                .forEach(item ->
                        sender.sendMessage(
                                ChatColor.DARK_GRAY + "  #"
                                        + item.entityId()
                                        + ChatColor.WHITE + " "
                                        + item.entityType()
                                        + ChatColor.GRAY
                                        + " uuid=" + item.shortUuid()
                                        + " spawn=" + item.spawnPacket()
                                        + " total=" + item.totalActivity()
                                        + " meta=" + item.metadata()
                                        + " tp=" + item.teleport()
                                        + " vel=" + item.velocity()
                        )
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
                ChatColor.GRAY + "Loaded chunks / entities: "
                        + ChatColor.WHITE
                        + snapshot.loadedChunks() + " / "
                        + snapshot.totalEntities()
        );
        sender.sendMessage(
                ChatColor.GRAY + "Players / avg ping: "
                        + ChatColor.WHITE
                        + snapshot.onlinePlayers() + " / "
                        + format(snapshot.averagePing()) + " ms"
        );
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
                sender.sendMessage(
                        prefix() + ChatColor.YELLOW
                                + "Optimizer disabled and caches cleared."
                );
            }
            case "reset" -> {
                plugin.getOptimizer().reset();
                sender.sendMessage(
                        prefix() + ChatColor.GREEN
                                + "Optimizer counters and caches reset."
                );
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
                    "virtual",
                    "latency",
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
                || args[0].equalsIgnoreCase("entities")
                || args[0].equalsIgnoreCase("virtual")
                || args[0].equalsIgnoreCase("latency"))) {
            String input = args[1].toLowerCase(Locale.ROOT);

            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name ->
                            name.toLowerCase(Locale.ROOT).startsWith(input))
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

    private static Set<Integer> allLoadedBukkitEntityIds() {
        Set<Integer> result = new HashSet<>();

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                result.add(entity.getEntityId());
            }
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

    private static ChatColor priorityColor(LatencyGuardian.Mode mode) {
        return switch (mode) {
            case NORMAL -> ChatColor.GREEN;
            case PRESSURE -> ChatColor.YELLOW;
            case COMBAT -> ChatColor.GOLD;
        };
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

    private static ChatColor reductionColor(double percent) {
        if (percent >= 25.0) return ChatColor.GREEN;
        if (percent > 0.0) return ChatColor.YELLOW;
        return ChatColor.WHITE;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";

        double kib = bytes / 1024.0;

        if (kib < 1024.0) return format(kib) + " KiB";

        return format(kib / 1024.0) + " MiB";
    }
}
