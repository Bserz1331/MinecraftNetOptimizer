# MinecraftNetOptimizer

## Conservative network optimization and diagnostics for Paper servers

MinecraftNetOptimizer is a compatibility-first Paper plugin that reduces avoidable packet workload and bounds packet-related runtime state while prioritizing gameplay correctness.

### Highlights

- Safe `ENTITY_METADATA` and changed-only logical UI deduplication.
- Raw vs forwarded packet profiling and burst detection.
- Packet workload diagnostics, entity tracing, and Virtual Entity diagnostics.
- Combat-sensitive Latency Guardian with main-thread handoff and Netty backpressure observation.
- Bounded global and per-player lifecycle state with fail-open cache saturation.
- Automatic stale cleanup and join / quit / world-change cleanup.
- `/netdebug lifecycle` for cache, trace, heap, and passive GC metrics.

### Compatibility-first boundaries

- No invasive NMS or CraftBukkit version-specific hacks.
- No forced GC and no JVM GC configuration changes.
- No Netty channel watermark changes.
- No critical packet throttling, dropping, coalescing, or reordering.
- Movement, attack input, knockback, teleport, inventory acknowledgement, chunk / world consistency, block state, and KeepAlive remain untouched.

PacketEvents `2.13.0` is required for packet-level profiling and optimization. The plugin can load without it, but those packet-level features remain inactive. The primary compile baseline is Paper 1.20.4, with Paper 1.20–26.2 as the compatibility target. Validate your exact Paper and PacketEvents combination before production use.

### Commands and permission

Use `/netdebug` and its diagnostic and optimizer subcommands. The required permission is `twnetoptimizer.admin`, defaulting to server operators.

Support and community: https://discord.gg/ukTERDqckB

### Release

`v0.8.0` is the first public-release candidate. License: GPL-3.0.
