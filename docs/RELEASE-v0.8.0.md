# SpaceCatNetOptimizer v0.8.0

## First public release

SpaceCatNetOptimizer is a conservative Paper network optimizer and diagnostic toolkit focused on network lifecycle optimization and packet workload optimization.

### Highlights

- Conservative Paper network optimization.
- Raw vs forwarded packet profiling.
- Latency Guardian for combat-sensitive priority handling.
- Safe Metadata and UI deduplication.
- Bounded lifecycle state with global and per-player cache limits.
- Fail-open behavior when cache limits are reached.
- Lifecycle diagnostics through `/netdebug lifecycle`.
- Passive JVM heap and GC observation.
- PacketEvents integration.

### Safety boundaries

SpaceCatNetOptimizer does not call `System.gc()`, modify JVM GC configuration, change Netty watermarks, change TCP ordering, inspect third-party plugin internals, or actively throttle, drop, coalesce, or reorder critical gameplay traffic.

### Compatibility

- Supported target: Paper 1.20–26.2.
- Compile baseline: Paper 1.20.4.
- Java 17 bytecode.
- PacketEvents `2.13.0` required for packet-level features.
- Minecraft 26.3 is not formally supported by this release.

### Artifact

Attach `SpaceCatNetOptimizer-0.8.0.jar` to the GitHub Release after the release PR is merged and the repository owner confirms the public-release and platform-listing steps.

License: GPL-3.0.
