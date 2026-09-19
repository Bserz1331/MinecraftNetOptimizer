# TWNetOptimizer

TWNetOptimizer is a **Paper 1.20.4 / Java 17** network and server profiler with a conservative safe-optimization layer for long-distance Minecraft servers.

## v0.3 scope

This version combines profiling, safe packet optimization, entity attribution, and server-side diagnostics.

### Safe optimization

Enabled by default:

- **ENTITY_METADATA dedupe**
  - keyed per player + entity ID
  - identical metadata payload is suppressed until the state changes
  - periodic refresh pass-through prevents indefinite cache staleness
- **Persistent UI exact-duplicate dedupe**
  - BOSS_BAR
  - SCOREBOARD_OBJECTIVE
  - UPDATE_SCORE
  - DISPLAY_SCOREBOARD
  - TEAMS
  - PLAYER_LIST_HEADER_AND_FOOTER
- **Particle throttle**
  - cosmetic only
  - adaptive lower cap when a player is already in burst/high-ping conditions

### Explicit safety boundary

TWNetOptimizer does **not** optimize or suppress:

- player movement
- entity teleport
- entity velocity
- combat packets
- inventory / transaction / acknowledgement packets
- chunk/world consistency packets
- block state packets
- KeepAlive

Those categories are monitored and traced, not filtered.

## Why there is no arbitrary packet batching

Vanilla clients expect legal Minecraft protocol packets. TWNetOptimizer does not invent a custom combined packet.

Instead it reduces redundant **state updates before transport** where this can be done safely. Netty/TCP may still batch writes at the transport layer.

## Entity trace

Use:

```
/netdebug trace <player> 10
/netdebug entities <player>
```

The trace attributes ENTITY_METADATA / ENTITY_TELEPORT / ENTITY_VELOCITY to entity IDs.

Loaded Bukkit entities are shown by type. IDs that cannot be matched are shown as `UNKNOWN / VIRTUAL`, which is useful for diagnosing packet-only NPC/model systems.

## Server diagnostics

```
/netdebug server
/netdebug advice
```

Server diagnostics include:

- TPS / MSPT
- view distance / simulation distance
- loaded chunk count
- loaded entity count and top entity types
- average player ping
- pending Bukkit scheduler task counts by plugin

The scheduler count is a diagnostic signal only. It does not prove task frequency or CPU cost.

## Commands

```
/netdebug
/netdebug <player>
/netdebug top
/netdebug status
/netdebug server
/netdebug advice
/netdebug trace <player> [seconds]
/netdebug entities <player>
/netdebug optimize <status|on|off|reset>
/netdebug reset
/netdebug reload
```

Permission: `twnetoptimizer.admin` (default: op)

## Requirements

- Paper 1.20.4
- Java 17
- PacketEvents 2.x for packet profiling / optimization

Without PacketEvents, Paper-side server diagnostics remain available but packet-level profiling and optimization are disabled.

## Build

```
mvn verify
```

GitHub Actions builds every push / pull request and uploads `TWNetOptimizer-*.jar` as an artifact.

## Important byte-count caveat

Observed bytes are PacketEvents buffer bytes before transport compression/encryption and are not NIC wire bytes.

## Upstream optimization

Packet suppression can save encoding, Netty, compression, network and client processing, but it cannot undo CPU work that another plugin already performed before creating the packet.

If tracing shows one plugin/system is generating unchanged state every tick, fixing that producer with dirty flags, event-driven updates, lower update cadence, or changed-only synchronization is still the best long-term optimization.
