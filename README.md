# TWNetOptimizer

TWNetOptimizer is a conservative Paper 1.20.4 / Java 17 packet profiler and network optimizer.

## v0.8 Network Lifecycle Bounds

v0.8 adds bounded lifecycle state so TWNetOptimizer cannot grow its own packet-related caches indefinitely.

This is not a RAM cleaner and does not replace the JVM garbage collector.

TWNetOptimizer never calls `System.gc()`.

### Bounded optimizer caches

The following runtime structures now have hard limits:

- ENTITY_METADATA state cache
- logical UI state cache
- particle rate windows
- virtual entity tracking per viewer
- entity trace counters per session

When an optimization cache reaches its limit, TWNetOptimizer fails open:

    new cache key arrives
    + cache is full
    -> do not retain the new state
    -> forward packets normally

Gameplay correctness is preferred over cache hit rate.

Oversized packet payloads are also not retained for dedupe.

### Lifecycle cleanup

State is released on:

- player quit
- player join, to clear stale state from an abnormal previous session
- world change for world-scoped packet/entity state
- entity destroy where available
- plugin disable
- low-frequency stale-state maintenance

Trace results are retained briefly after a trace ends so `/netdebug entities` can still inspect them, then maintenance removes them.

Virtual entity entries also receive stale cleanup in case a third-party packet path never produces a matching destroy packet.

### Allocation and GC policy

v0.8 only reduces avoidable retention.

It does not:

- call `System.gc()`
- alter JVM GC policy
- change Netty channel watermarks
- retain or release PacketEvents buffers outside TWNetOptimizer ownership
- inspect or mutate other plugins' internal collections

The goal is:

    packet-related state created
    -> used
    -> bounded
    -> released
    -> normal JVM GC

### Combat Guardian retained

v0.7 Combat Guardian remains unchanged in behavior.

Combat mode can be activated by:

- client ATTACK input
- confirmed Bukkit entity damage dealt by a player
- confirmed Bukkit entity damage received by a player
- projectile damage where the shooter is a player

Critical packet safety remains unchanged.

TWNetOptimizer does not throttle, drop, coalesce or reorder:

- movement
- attack input
- entity velocity / knockback
- teleport
- inventory transaction / acknowledgement
- chunk and world consistency
- block state
- KeepAlive

TCP ordering is not modified.

### Existing optimizer foundations retained

- low-allocation ENTITY_METADATA exact-state dedupe
- logical-target UI changed-only cache
- cache commit only after final MONITOR forwarding
- Raw vs Forwarded packet accounting
- Netty channel writability / backpressure observation
- main-thread handoff latency sampling
- virtual entity registry and entity traffic tracing
- server diagnostics and advice engine

### Conservative defaults

For new installations:

- metadata dedupe: ON
- UI changed-only dedupe: ON
- particle throttle: OFF
- Latency Guardian: ON
- combat window: 2500 ms
- duplicate refresh combat deferral: max 5000 ms
- metadata cache: max 16384 entries
- UI cache: max 4096 entries
- cached payload: max 65536 bytes
- virtual entity tracking: max 4096 entries per viewer

Existing server config files are not forcibly overwritten. Missing v0.8 keys use safe code defaults.

Useful commands:

    /netdebug <player>
    /netdebug latency <player>
    /netdebug trace <player> 10
    /netdebug entities <player>
    /netdebug virtual <player>
    /netdebug server
    /netdebug advice

TWNetOptimizer cannot reduce physical network RTT. Its purpose is to reduce avoidable packet work and state retention while preserving gameplay correctness first.
