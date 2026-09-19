# TWNetOptimizer

TWNetOptimizer is a conservative Paper 1.20.4 / Java 17 packet profiler and network optimizer.

## v0.7 Combat Guardian

v0.7 extends the existing Latency Guardian so combat protection covers both sides of PVE and PVP without dropping or reordering critical gameplay packets.

### Attacker + victim combat detection

Combat mode can now be activated by:

- client ATTACK input
- confirmed Bukkit entity damage dealt by a player
- confirmed Bukkit entity damage received by a player
- projectile damage where the shooter is a player

The Bukkit damage observer runs at MONITOR with `ignoreCancelled = true` and never modifies the damage event.

Examples:

    Player A attacks Player B
    -> A COMBAT
    -> B COMBAT

    Zombie attacks Player
    -> Player COMBAT

    Player shoots an arrow
    -> shooter COMBAT when damage is confirmed
    -> player victim COMBAT when applicable

### Conservative combat queue relief

Particle throttling remains OFF by default for compatibility with Slimefun, RPG and boss visual telegraphs.

To make COMBAT mode useful even with particle throttling disabled, v0.7 can briefly defer only periodic refreshes that are exact duplicates of already-forwarded Metadata or UI state.

Default behavior:

    normal unchanged refresh due at 30s
    + active COMBAT
    -> may defer that duplicate refresh for up to 5s

A real state transition is never delayed.

Examples that always pass immediately:

    health/state A -> B
    scoreboard 100 -> 101
    metadata payload changed

Only an exact duplicate of the client's already-known state is eligible for combat refresh deferral.

### Critical packet safety boundary

TWNetOptimizer still does not throttle, drop, coalesce or reorder:

- movement
- attack input
- entity velocity / knockback
- teleport
- inventory transaction / acknowledgement
- chunk and world consistency
- block state
- KeepAlive

TCP ordering is not modified.

### v0.6 foundations retained

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

Existing server config files are not forcibly overwritten. Missing v0.7 keys use safe code defaults.

Useful commands:

    /netdebug <player>
    /netdebug latency <player>
    /netdebug trace <player> 10
    /netdebug entities <player>
    /netdebug virtual <player>
    /netdebug server
    /netdebug advice

TWNetOptimizer cannot reduce physical network RTT. Combat Guardian is designed to reduce avoidable server/output-queue interference around combat while preserving gameplay correctness.
