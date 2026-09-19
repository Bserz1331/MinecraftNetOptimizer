# TWNetOptimizer

TWNetOptimizer is a conservative Paper 1.20.4 / Java 17 packet profiler and network optimizer.

## v0.6 Conservative State Cache

v0.6 focuses on improving safety and optimizer overhead rather than dropping more packet categories.

### Low-allocation metadata dedupe

ENTITY_METADATA comparison now reads the PacketEvents buffer directly against the last forwarded payload.

Repeated duplicate metadata no longer needs a fresh byte array allocation on every packet.

A payload copy is only stored when a packet is actually allowed through and reaches the MONITOR stage.

This is especially useful on servers producing thousands of identical metadata packets per second.

### Final-forwarded cache semantics

Metadata and UI state are committed to cache only from the final MONITOR listener after the packet remains uncancelled.

This makes the cache represent what was actually forwarded rather than what TWNetOptimizer merely intended to forward.

### UI changed-only state

Persistent UI dedupe is now keyed by logical target instead of by a recently seen payload set.

Tracked targets include:

- BossBar UUID
- Scoreboard objective name
- score entry + objective
- display scoreboard slot
- team name
- player-list header/footer

For each target, only an exact repeat of the current client state is suppressed.

A state transition such as 100 -> 101 -> 100 is forwarded correctly.

Unchanged state is still periodically refreshed.

### Netty backpressure observation

Latency Guardian now samples the existing Netty channel writability state.

TWNetOptimizer does not alter Netty watermarks or TCP behavior.

If the channel reports unwritable, the player enters PRESSURE mode and /netdebug latency shows the observed channel state.

### Conservative defaults

For new installations:

- metadata dedupe: ON
- UI changed-only dedupe: ON
- particle throttle: OFF
- Latency Guardian: ON
- movement/attack filtering: NEVER
- teleport/velocity filtering: NEVER
- chunk/world consistency filtering: NEVER
- inventory/ack filtering: NEVER
- KeepAlive filtering: NEVER

Existing server config files are not forcibly overwritten.

### Intentionally not enabled yet

v0.6 does not enable:

- field-level metadata rewriting
- same-tick last-value-wins coalescing
- teleport coalescing
- velocity coalescing
- custom packet reordering

Those mechanisms are more invasive and should only be considered after broader compatibility testing.

Useful commands:

    /netdebug <player>
    /netdebug latency <player>
    /netdebug trace <player> 10
    /netdebug entities <player>
    /netdebug virtual <player>
    /netdebug server
    /netdebug advice

TWNetOptimizer cannot reduce physical network RTT. Its purpose is to reduce redundant traffic, expose packet pressure, and protect gameplay correctness first.
