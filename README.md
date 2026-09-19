# TWNetOptimizer

TWNetOptimizer is a monitor-first network profiler for **Paper 1.20.4** servers where many players connect over long-distance routes, such as Taiwan to the US West Coast.

The first release is intentionally conservative. It measures packet activity and server health before any optimization logic is introduced.

## Safety boundary

**v0.1 is monitor-only.** It does not cancel, delay, rewrite, reorder, deduplicate, suppress, or queue Minecraft packets.

Critical movement, KeepAlive, teleport confirmation, inventory, interaction, combat, and world-synchronization traffic is untouched.

## What it measures

With PacketEvents installed:

- Per-player inbound / outbound packets per second
- Per-player observed packet-buffer bytes per second
- Top inbound / outbound packet types
- Outbound chunk / entity / UI / other packet mix
- Per-player burst detection

From Paper directly:

- Player ping
- 1-minute TPS
- Average MSPT
- Online player count

## Important byte-count caveat

Observed bytes are the readable bytes exposed by PacketEvents at the packet-event layer. They are useful for relative profiling, but they are **not actual NIC wire bytes** after Minecraft compression, encryption, TCP framing, retransmission, or other transport effects.

## Requirements

- Paper 1.20.4
- Java 17
- PacketEvents 2.x recommended for packet-level profiling

PacketEvents is an optional external dependency. TWNetOptimizer still loads without it, but packet counters will be unavailable.

## Installation

1. Build or download `TWNetOptimizer-*.jar`.
2. Put the jar in `plugins/`.
3. Install a compatible PacketEvents 2.x jar in `plugins/` for packet-level profiling.
4. Restart the server.
5. Run `/netdebug status`.

## Commands

- `/netdebug` - show your own latest sample, or server status from console
- `/netdebug <player>` - show one player's latest network sample
- `/netdebug top` - show players with the highest outbound observed bytes
- `/netdebug status` - show profiler, TPS, MSPT, and PacketEvents status
- `/netdebug reset` - clear profiler state
- `/netdebug reload` - reload configuration

Permission: `twnetoptimizer.admin` (default: op)

## Build

Run `mvn verify`.

GitHub Actions builds every push and pull request and uploads the plugin jar as a workflow artifact.

## Direction after measurement

Optimization should only be added after real production samples identify excess traffic. Candidate areas include duplicate UI suppression, cosmetic throttling, and adaptive bulk-traffic budgets. Critical gameplay packets remain outside optimization paths unless separately verified.
