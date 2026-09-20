# SpaceCatNetOptimizer

Conservative network optimization and diagnostics for Paper servers.

## What is SpaceCatNetOptimizer

SpaceCatNetOptimizer reduces avoidable packet workload and manages packet-related runtime state while prioritizing gameplay correctness and plugin compatibility.

Its scope is **network lifecycle optimization / packet workload optimization**. It is not a RAM cleaner, ping booster, JVM GC manager, general-purpose TPS booster, or forced packet compressor.

SpaceCatNetOptimizer observes packet traffic, applies conservative deduplication to safe packet classes, bounds its own diagnostic and optimizer state, and exposes server-side diagnostics through `/netdebug`.

## Features

- Safe `ENTITY_METADATA` exact-state deduplication.
- Changed-only logical UI packet deduplication with periodic refreshes.
- Raw vs forwarded packet profiling.
- Packet workload profiling by packet category and packet type.
- Outbound burst detection.
- Netty backpressure observation.
- Main-thread handoff latency observation.
- Combat-sensitive Latency Guardian.
- Virtual Entity diagnostics.
- Entity packet tracing.
- Bounded optimizer-owned caches.
- Global and per-player cache limits.
- Automatic stale-state cleanup.
- Join, quit, and world-change lifecycle cleanup.
- Passive JVM heap observation.
- Passive GC collection count and collection-time observation.
- `/netdebug lifecycle` and `/netdebug lifecycle <player>` diagnostics.

These features reduce avoidable packet work and allocation / retention pressure. They may indirectly reduce jitter in some workloads, but they do not change physical network RTT and do not guarantee higher TPS, lower RAM usage, or better PvP latency.

## Safety / Compatibility Philosophy

SpaceCatNetOptimizer is compatibility-first:

- No `System.gc()` calls.
- No JVM GC configuration changes.
- No Netty channel watermark changes.
- No NMS or CraftBukkit version-specific hacks.
- No inspection or mutation of third-party plugin internals.
- No PacketEvents buffer retain / release changes outside SpaceCatNetOptimizer ownership.
- No TCP ordering changes.
- New optimizer and diagnostic state is bounded and has lifecycle cleanup.
- Cache saturation is fail-open: when a cache is full, a new key is not retained and the packet is forwarded normally.

SpaceCatNetOptimizer does not actively throttle, drop, coalesce, or reorder critical gameplay traffic:

- Movement.
- Attack input.
- Entity velocity / knockback.
- Teleport.
- Inventory transaction / acknowledgement.
- Chunk / world consistency.
- Block state.
- KeepAlive.

The optional particle limiter is disabled by default and is intended only for cosmetic particle traffic. It does not apply to the critical traffic listed above.

## Requirements

- Paper server.
- Java 17 bytecode compatibility for this build. The actual server JVM must also satisfy the Java requirement of the selected Paper version.
- PacketEvents `2.13.0` is required for packet-level profiling and optimization.

If PacketEvents is not installed or is inactive, SpaceCatNetOptimizer can load and its command surface remains available, but packet-level profiling, safe packet optimization, and Latency Guardian packet observations are disabled. `/netdebug status` reports this state.

## Supported Versions

Supported target: Paper 1.20–26.2

Compile baseline: Paper 1.20.4

Paper 1.20.4 is the primary compatibility baseline for this release. The other versions in the supported target are compatibility targets and are not represented as individually tested by this repository. Validate the selected Paper, PacketEvents, and plugin combination on a staging server before production use.

Minecraft 26.3 support depends on stable PacketEvents protocol support and compatibility verification. This release does not claim formal 26.3 support.

## Installation

1. Install a compatible Paper server.
2. Install PacketEvents `2.13.0` or a verified compatible build.
3. Copy `SpaceCatNetOptimizer-0.8.0.jar` into the server's `plugins/` directory.
4. Start the server.
5. Run `/netdebug status`.
6. Run `/netdebug lifecycle` to verify optimizer-owned lifecycle state.

Keep a backup and test changes on a staging server first. Existing configuration files are not forcibly overwritten; missing configuration keys use safe code defaults.

## Commands

All commands require `twnetoptimizer.admin`.

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

`/netdebug` shows the issuing player's profile when used in-game, or server status from the console. The permission default is `op`.

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `twnetoptimizer.admin` | `op` | Use SpaceCatNetOptimizer diagnostics and optimizer controls. The node is retained for compatibility. |

## Configuration

Conservative defaults are provided in `plugins/SpaceCatNetOptimizer/config.yml`:

- `optimizer.metadata-dedupe.enabled`: safe metadata deduplication, enabled by default.
- `optimizer.ui-dedupe.enabled`: changed-only UI deduplication, enabled by default.
- `optimizer.particle-throttle.enabled`: cosmetic particle limiter, disabled by default.
- `latency-guardian.enabled`: combat-sensitive diagnostics and priority state, enabled by default.
- `optimizer.cache.*`: global, per-player, payload-size, and stale-entry bounds.
- `trace.*`: trace duration, result retention, entity limits, and virtual-entity limits.
- `burst.*`: outbound packet and observed-byte burst thresholds.

When a cache reaches a hard limit, SpaceCatNetOptimizer skips caching the new key and forwards the packet normally. Oversized or otherwise uncacheable payloads are not retained for deduplication.

## Lifecycle Diagnostics

`/netdebug lifecycle` reports:

- Metadata cache current size / global limit.
- Metadata per-player limit.
- Metadata high-water mark.
- Metadata fail-open skips.
- Metadata stale removals and trim removals.
- UI cache current size / global limit.
- UI per-player limit.
- UI high-water mark.
- UI fail-open skips.
- UI stale removals and trim removals.
- Particle windows, limit, high-water mark, skipped windows, and stale removals.
- Entity trace sessions, active sessions, bounded entities, skips, cleanup, and trims.
- Virtual Entity viewers, bounded entities, skips, stale removals, and trims.
- Uncacheable payloads and old-state invalidations.
- JVM heap used / committed / max.
- GC collection count and accumulated collection time.

`/netdebug lifecycle <player>` shows the bounded metadata, UI, particle, trace, and Virtual Entity state for one online player.

GC metrics are observation-only. SpaceCatNetOptimizer never requests a GC.

## Compatibility Notes

- PacketEvents is a soft dependency at plugin load time but a runtime requirement for packet-level features.
- Packet listener registration uses PacketEvents and diagnostics are designed to fail safely if optional packet inspection is unavailable.
- Critical movement, attack input, knockback, teleport, inventory acknowledgement, chunk / world consistency, block-state, and KeepAlive traffic is not actively throttled, dropped, coalesced, or reordered by this plugin.
- No NMS or CraftBukkit version-specific implementation is required by the current release.
- The primary compile baseline is Paper 1.20.4. Test newer and older target versions before production rollout.

## Troubleshooting

1. Run `/netdebug status` and confirm that PacketEvents is `active`.
2. Run `/netdebug lifecycle` and capture the output when reporting lifecycle or retention concerns.
3. Check the server log for PacketEvents integration errors.
4. Confirm that the server JVM satisfies the selected Paper version's Java requirement.
5. Temporarily disable a suspected conflicting plugin on a staging server and compare raw / forwarded profiles.
6. Include the Paper, Java, PacketEvents, and SpaceCatNetOptimizer versions, major plugins, diagnostics output, logs, and reproduction steps in a bug report.

SpaceCatNetOptimizer cannot repair another plugin's memory leak, guarantee a fixed RAM reduction, or prove a network RTT improvement. Use the diagnostics to isolate packet workload and lifecycle state, then verify changes with a controlled server comparison.

## Building from Source

The project targets Java 17 and uses Maven:

```text
mvn -B -ntp verify
```

The build filters `src/main/resources/plugin.yml` so `${project.version}` is replaced with the Maven project version. The release artifact is:

```text
target/SpaceCatNetOptimizer-0.8.0.jar
```

## License

SpaceCatNetOptimizer is licensed under the [GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.html).

License: GPL-3.0

## Release / Support Information

This repository is being prepared for its first public release, `v0.8.0`. The release candidate is prepared through a pull request before platform publication.

Support and community: [Discord](https://discord.gg/ukTERDqckB)

Platform-specific listing copy is available in:

- [`docs/HANGAR.md`](docs/HANGAR.md)
- [`docs/SPIGOT.md`](docs/SPIGOT.md)

Please report reproducible compatibility or behavior issues with the diagnostic information requested by the issue templates. Do not describe this plugin as `Zero Lag`, `No Ping`, `FPS Boost`, `Double TPS`, `RAM Cleaner`, or `Ultimate Optimizer`.
