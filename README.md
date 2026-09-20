# MinecraftNetOptimizer

![MinecraftNetOptimizer icon](MinecraftNetOptimizer-icon.png)


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
