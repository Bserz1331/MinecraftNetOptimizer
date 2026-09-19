# TWNetOptimizer

TWNetOptimizer is a **Paper 1.20.4 / Java 17** network and server profiler with a conservative safe-optimization layer for long-distance Minecraft servers.

## v0.4

v0.4 adds two important diagnostic improvements:

1. **Raw vs forwarded traffic**
   - raw outbound packets are counted before TWNetOptimizer filtering
   - forwarded outbound packets are counted from a PacketEvents MONITOR listener only when the final event is not cancelled
   - `/netdebug <player>` now shows raw, forwarded, not-forwarded and reduction percentage

2. **Virtual entity registry**
   - tracks forwarded `SPAWN_ENTITY`, `SPAWN_PLAYER` and `SPAWN_EXPERIENCE_ORB`
   - records entity ID, spawn UUID, spawn packet and protocol entity type
   - continuously counts ENTITY_METADATA / ENTITY_TELEPORT / ENTITY_VELOCITY activity
   - compares tracked client entity IDs with loaded Bukkit entity IDs
   - `/netdebug virtual <player>` shows virtual spawn types and top hot virtual entities

## Safe optimization

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

## Safety boundary

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
/netdebug virtual <player>
/netdebug optimize <status|on|off|reset>
/netdebug reset
/netdebug reload
```

Permission: `twnetoptimizer.admin` (default: op)

## Reading raw vs forwarded

Example:

```
Raw outbound:       30000 pkt/s
Forwarded outbound: 20000 pkt/s
Not forwarded:      10000 pkt/s (33.3%)
```

`Not forwarded` is the difference between the observed raw and final forwarded counts. It can include cancellations by other packet listeners as well as TWNetOptimizer.

The separate `TWNO suppressed since reset` counter reports only suppressions performed by TWNetOptimizer's own metadata/UI/particle modules.

## Virtual entity diagnosis

After a full restart, TWNetOptimizer observes entity spawn packets sent to each client.

Use:

```
/netdebug trace <player> 10
/netdebug entities <player>
/netdebug virtual <player>
```

When a hot entity ID is not a loaded Bukkit entity but its spawn packet was observed, the trace can now report a protocol type such as:

```
VIRTUAL minecraft:item_display [SPAWN_ENTITY]
```

instead of only `UNKNOWN / VIRTUAL`.

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

Pending scheduler task count is a diagnostic signal only. It does not prove task frequency or CPU cost.

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

Observed bytes are PacketEvents-layer buffer bytes and are not NIC wire bytes after compression, encryption, TCP framing or retransmission.

## Upstream optimization

Packet suppression can save later network-path work, but it cannot undo CPU work another plugin already performed before creating a packet.

If the virtual-entity registry shows a plugin-generated entity repeatedly receiving unchanged state, fixing that producer with dirty flags, changed-only synchronization, lower update cadence or event-driven updates remains the preferred long-term optimization.
