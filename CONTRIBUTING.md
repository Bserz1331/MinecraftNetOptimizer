# Contributing to SpaceCatNetOptimizer

SpaceCatNetOptimizer is a compatibility-first Paper plugin. Contributions should preserve gameplay correctness and keep the diagnostic and optimization state bounded.

- Compatibility-first design.
- Never add forced GC or call `System.gc()`.
- Do not add unsafe packet dropping, throttling, coalescing, or reordering.
- Do not add NMS unless it is unavoidable, narrowly scoped, and justified in the pull request.
- Do not inspect or mutate invasive third-party plugin internals.
- Keep critical gameplay packets untouched.
- Bound every new cache, queue, window, or trace state.
- Define cleanup for every new lifecycle state, including join, quit, world-change, disable, and stale-state behavior where applicable.
- Preserve the PacketEvents buffer ownership contract.
- Run `mvn -B -ntp verify` before opening a pull request.

Pull requests should explain compatibility impact, safety boundaries, configuration changes, and the tests used to verify the change.
