# SpaceCatNetOptimizer

## Conservative network optimization and diagnostics for Paper servers

SpaceCatNetOptimizer focuses on network lifecycle optimization and packet workload diagnostics for Paper servers. It helps identify avoidable packet work and bounds its own packet-related state without promising impossible ping or TPS guarantees.

### Features

- Safe Metadata deduplication and changed-only UI state deduplication.
- Raw / forwarded packet profiling, workload categories, and burst detection.
- Latency Guardian for combat-sensitive priority state.
- Observation of main-thread handoff latency and Netty backpressure.
- Entity packet tracing and Virtual Entity diagnostics.
- Bounded caches with global and per-player limits.
- Fail-open behavior when limits are reached.
- Lifecycle cleanup for stale state, players, worlds, plugin disable, and entity destruction where available.
- `/netdebug lifecycle` with heap and passive GC observations.

### Safety boundaries

SpaceCatNetOptimizer does not call `System.gc()`, modify JVM GC settings, change Netty watermarks, use invasive NMS hacks, or modify third-party plugin internals. It does not actively throttle, drop, coalesce, or reorder movement, attack input, knockback, teleport, inventory acknowledgement, chunk / world consistency, block-state, or KeepAlive traffic.

Particle limiting is disabled by default and applies only to cosmetic particle traffic when explicitly enabled.

### Compatibility

PacketEvents `2.13.0` is required for packet-level profiling and optimization. Without PacketEvents, the plugin can load but packet-level features are inactive. Compile baseline: Paper 1.20.4. Supported target: Paper 1.20–26.2. Minecraft 26.3 is not formally supported by this release.

### Release

First public-release candidate: `v0.8.0`.

License: GPL-3.0.

Support and community: https://discord.gg/ukTERDqckB
