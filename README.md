# TWNetOptimizer

TWNetOptimizer is a Paper 1.20.4 / Java 17 network and server profiler with conservative safe optimization for long-distance Minecraft servers.

## v0.5 Latency Guardian

Latency Guardian protects gameplay responsiveness without inventing an unsafe custom packet queue.

Critical client input includes player movement packets and INTERACT_ENTITY attacks.

TWNetOptimizer never cancels or throttles those receive packets.

A real attack activates COMBAT mode for a configurable window.

During COMBAT mode movement, attack, teleport, velocity, inventory, chunk/world consistency, block state and KeepAlive remain untouched.

Only low-value cosmetic particle allowance becomes stricter.

Default particle limits are 500 per second in normal mode, 150 under pressure and 100 in combat.

Pressure mode activates when final forwarded traffic is already bursting or sampled main-thread handoff p95 crosses the configured threshold.

Use:

    /netdebug latency <player>

It reports ping, priority mode, current movement/attack input counts, sampled Netty-arrival to Bukkit-main-thread handoff, raw/forwarded outbound traffic and packet reduction.

Handoff latency is not exact attack resolution time. It is a scheduler diagnostic for separating network RTT from server-side main-thread availability.

Other useful commands:

    /netdebug <player>
    /netdebug virtual <player>
    /netdebug trace <player> 10
    /netdebug entities <player>
    /netdebug server
    /netdebug advice

TWNetOptimizer cannot reduce the physical Taiwan to US West RTT. Latency Guardian reduces avoidable local pressure around critical gameplay traffic and exposes server-side handoff delay.
