# MinecraftNetOptimizer

Conservative network optimization and packet-workload diagnostics for Paper servers.

[![Build](https://github.com/Bserz1331/MinecraftNetOptimizer/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/Bserz1331/MinecraftNetOptimizer/actions/workflows/build.yml)
[![Maven verify](https://github.com/Bserz1331/MinecraftNetOptimizer/actions/workflows/maven-verify.yml/badge.svg?branch=main)](https://github.com/Bserz1331/MinecraftNetOptimizer/actions/workflows/maven-verify.yml)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)

**Language:** English | [繁體中文](README.zh-TW.md)

## English

### What it does

MinecraftNetOptimizer observes packet traffic, applies conservative deduplication to safe packet classes, and bounds its own diagnostic and optimizer state. It is designed to help server owners understand avoidable packet work while keeping gameplay correctness and plugin compatibility first.

Its scope is network lifecycle optimization and packet-workload diagnostics. It is not a RAM cleaner, ping booster, JVM garbage-collection manager, general-purpose TPS booster, or forced packet compressor.

### Features

- Safe `ENTITY_METADATA` exact-state deduplication.
- Changed-only logical UI packet deduplication with periodic refreshes.
- Raw versus forwarded packet profiling.
- Packet workload profiling by category and packet type.
- Outbound burst detection.
- Netty backpressure observation.
- Main-thread handoff latency observation.
- Combat-sensitive Latency Guardian diagnostics.
- Virtual Entity diagnostics and entity packet tracing.
- Bounded optimizer-owned caches with global and per-player limits.
- Fail-open behavior when cache limits are reached.
- Automatic stale-state cleanup on lifecycle events.
- Passive JVM heap and garbage-collection metrics.
- `/netdebug lifecycle` diagnostics for current state, limits, high-water marks, cleanup, and fail-open counters.

These features can reduce avoidable packet work and allocation / retention pressure in some workloads. They do not change physical network RTT and do not guarantee higher TPS, lower RAM usage, or better PvP latency.

### Compatibility

| Requirement | Supported baseline / target |
| --- | --- |
| Server | Paper 1.20–26.2 target |
| Compile baseline | Paper 1.20.4 |
| Java | Java 17 bytecode |
| PacketEvents | 2.13.0 required for packet-level features |
| Minecraft 26.3 | Not formally supported by this release |

Paper 1.20.4 is the primary compile and compatibility baseline. Other versions in the target range are compatibility targets, not individually tested claims for every server build. Validate the exact Paper, PacketEvents, and plugin combination on a staging server before production use.

If PacketEvents is missing or inactive, the plugin can still load and its command surface remains available, but packet-level profiling, safe packet optimization, and Latency Guardian packet observations remain inactive. `/netdebug status` reports this state.

### Installation

1. Install a compatible Paper server.
2. Install PacketEvents `2.13.0` or a verified compatible build.
3. Download `MinecraftNetOptimizer-0.8.0.jar` from the [GitHub Releases](https://github.com/Bserz1331/MinecraftNetOptimizer/releases) page.
4. Copy the JAR into the server's `plugins/` directory.
5. Start the server and run `/netdebug status`.
6. Run `/netdebug lifecycle` to inspect optimizer-owned lifecycle state.

Keep a backup and test changes on a staging server first. Existing configuration files are not forcibly overwritten; missing configuration keys use safe code defaults.

### Commands

```text
/netdebug
/netdebug status
/netdebug top
/netdebug server
/netdebug advice
/netdebug latency <player>
/netdebug lifecycle
/netdebug lifecycle <player>
/netdebug trace <player> [seconds]
/netdebug entities <player>
/netdebug virtual <player>
/netdebug optimize <status|on|off|reset>
/netdebug reset
/netdebug reload
```

`/netdebug` shows the issuing player's profile in-game, or server status from the console.

### Permissions and upgrade compatibility

All commands require `twnetoptimizer.admin`, which defaults to server operators.

The permission node, Java package, and plugin implementation class retain their existing technical identifiers so upgrades do not unexpectedly invalidate existing permissions or integrations. The public plugin name is `MinecraftNetOptimizer`.

### Configuration highlights

Configuration is stored under `plugins/MinecraftNetOptimizer/config.yml`.

- `optimizer.metadata-dedupe.enabled`: safe metadata deduplication, enabled by default.
- `optimizer.ui-dedupe.enabled`: changed-only UI deduplication, enabled by default.
- `optimizer.particle-throttle.enabled`: cosmetic particle limiter, disabled by default.
- `latency-guardian.enabled`: combat-sensitive diagnostics and priority state, enabled by default.
- `optimizer.cache.*`: global, per-player, payload-size, and stale-entry bounds.
- `trace.*`: trace duration, result retention, entity limits, and Virtual Entity limits.
- `burst.*`: outbound packet and observed-byte burst thresholds.

When a cache reaches a hard limit, MinecraftNetOptimizer fails open: it does not retain the new key and forwards the packet normally. Oversized or otherwise uncacheable payloads are not retained for deduplication.

### Safety boundaries

MinecraftNetOptimizer:

- Does not call `System.gc()`.
- Does not modify JVM garbage-collection settings.
- Does not change Netty channel watermarks.
- Does not use invasive NMS or CraftBukkit version-specific hacks.
- Does not inspect or mutate third-party plugin internals.
- Does not change TCP ordering.
- Does not actively throttle, drop, coalesce, or reorder movement, attack input, entity velocity / knockback, teleport, inventory acknowledgement, chunk / world consistency, block-state, or KeepAlive traffic.

The optional particle limiter is disabled by default and is intended only for cosmetic particle traffic. It does not apply to the critical traffic listed above.

### Lifecycle diagnostics

`/netdebug lifecycle` reports current entries, configured limits, high-water marks, fail-open skips, stale removals, trace / Virtual Entity retention, heap used / committed / max, and observed garbage-collection count / time.

`/netdebug lifecycle <player>` shows the bounded metadata, UI, particle, trace, and Virtual Entity state for one online player.

High-water values may remain elevated because they describe peak usage. Physical network RTT is outside this plugin's control. Use controlled staging comparisons to evaluate packet workload and lifecycle behavior.

### Building from source

The project targets Java 17 and uses Maven:

```text
mvn -B -ntp verify
```

The release artifact is:

```text
target/MinecraftNetOptimizer-0.8.0.jar
```

### Source, support, and license

- Source code: [GitHub repository](https://github.com/Bserz1331/MinecraftNetOptimizer)
- Releases: [GitHub Releases](https://github.com/Bserz1331/MinecraftNetOptimizer/releases)
- Community support: [Discord](https://discord.gg/ukTERDqckB)
- License: [GNU GPL v3.0](LICENSE)

Please report reproducible compatibility or behavior issues with the Paper, Java, PacketEvents, plugin versions, diagnostics output, logs, and reproduction steps. Do not describe this plugin as `Zero Lag`, `No Ping`, `FPS Boost`, `Double TPS`, `RAM Cleaner`, or `Ultimate Optimizer`.
