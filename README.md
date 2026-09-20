# MinecraftNetOptimizer

Conservative network optimization and packet-workload diagnostics for Paper servers.

[![Build](https://github.com/Bserz1331/MinecraftNetOptimizer/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/Bserz1331/MinecraftNetOptimizer/actions/workflows/build.yml)
[![Maven verify](https://github.com/Bserz1331/MinecraftNetOptimizer/actions/workflows/maven-verify.yml/badge.svg?branch=main)](https://github.com/Bserz1331/MinecraftNetOptimizer/actions/workflows/maven-verify.yml)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)

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

Paper 1.20.4 is the primary compile and compatibility baseline. Other versions in the target range are compatibility targets, not individually tested claims for every server build. Validate the exact Paper, PacketEvents, and plugin combination on a staging server before production use.

If PacketEvents is missing or inactive, the plugin can still load and its command surface remains available, but packet-level profiling, safe packet optimization, and Latency Guardian packet observations remain inactive. `/netdebug status` reports this state.

### Installation

1. Install a compatible Paper server.
2. Install PacketEvents `2.13.0` or a verified compatible build.
3. Download `MinecraftNetOptimizer-0.8.0.jar` from the [GitHub Releases](https://github.com/Bserz1331/MinecraftNetOptimizer/releases) page.
4. Copy the JAR into the server's `plugins/` directory.
5. Start the server and run `/netdebug status`.
6. Run `/netdebug lifecycle` to inspect optimizer-owned lifecycle state.

Keep a backup and test changes on a staging server first. Existing configuration files are not forcibly overwritten; missing configuration keys use safe code defaults.

### Commands

```text
/netdebug
/netdebug status
/netdebug top
/netdebug server
/netdebug advice
/netdebug latency <player>
/netdebug lifecycle
/netdebug lifecycle <player>
/netdebug trace <player> [seconds]
/netdebug entities <player>
/netdebug virtual <player>
/netdebug optimize <status|on|off|reset>
/netdebug reset
/netdebug reload
```

`/netdebug` shows the issuing player's profile in-game, or server status from the console.

### Permissions and upgrade compatibility

All commands require `twnetoptimizer.admin`, which defaults to server operators.

The permission node, Java package, and plugin implementation class retain their existing technical identifiers so upgrades do not unexpectedly invalidate existing permissions or integrations. The public plugin name is `MinecraftNetOptimizer`.

### Configuration highlights

Configuration is stored under `plugins/MinecraftNetOptimizer/config.yml`.

- `optimizer.metadata-dedupe.enabled`: safe metadata deduplication, enabled by default.
- `optimizer.ui-dedupe.enabled`: changed-only UI deduplication, enabled by default.
- `optimizer.particle-throttle.enabled`: cosmetic particle limiter, disabled by default.
- `latency-guardian.enabled`: combat-sensitive diagnostics and priority state, enabled by default.
- `optimizer.cache.*`: global, per-player, payload-size, and stale-entry bounds.
- `trace.*`: trace duration, result retention, entity limits, and Virtual Entity limits.
- `burst.*`: outbound packet and observed-byte burst thresholds.

When a cache reaches a hard limit, MinecraftNetOptimizer fails open: it does not retain the new key and forwards the packet normally. Oversized or otherwise uncacheable payloads are not retained for deduplication.

### Safety boundaries

MinecraftNetOptimizer:

- Does not call `System.gc()`.
- Does not modify JVM garbage-collection settings.
- Does not change Netty channel watermarks.
- Does not use invasive NMS or CraftBukkit version-specific hacks.
- Does not inspect or mutate third-party plugin internals.
- Does not change TCP ordering.
- Does not actively throttle, drop, coalesce, or reorder movement, attack input, entity velocity / knockback, teleport, inventory acknowledgement, chunk / world consistency, block-state, or KeepAlive traffic.

The optional particle limiter is disabled by default and is intended only for cosmetic particle traffic. It does not apply to the critical traffic listed above.

### Lifecycle diagnostics

`/netdebug lifecycle` reports current entries, configured limits, high-water marks, fail-open skips, stale removals, trace / Virtual Entity retention, heap used / committed / max, and observed garbage-collection count / time.

`/netdebug lifecycle <player>` shows the bounded metadata, UI, particle, trace, and Virtual Entity state for one online player.

High-water values may remain elevated because they describe peak usage. Physical network RTT is outside this plugin's control. Use controlled staging comparisons to evaluate packet workload and lifecycle behavior.

### Building from source

The project targets Java 17 and uses Maven:

```text
mvn -B -ntp verify
```

The release artifact is:

```text
target/MinecraftNetOptimizer-0.8.0.jar
```

### Source, support, and license

- Source code: [GitHub repository](https://github.com/Bserz1331/MinecraftNetOptimizer)
- Releases: [GitHub Releases](https://github.com/Bserz1331/MinecraftNetOptimizer/releases)
- Community support: [Discord](https://discord.gg/ukTERDqckB)
- License: [GNU GPL v3.0](LICENSE)

Please report reproducible compatibility or behavior issues with the Paper, Java, PacketEvents, plugin versions, diagnostics output, logs, and reproduction steps. Do not describe this plugin as `Zero Lag`, `No Ping`, `FPS Boost`, `Double TPS`, `RAM Cleaner`, or `Ultimate Optimizer`.

## 中文

### 功能定位

MinecraftNetOptimizer 觀察封包流量，對安全的封包類別採取保守的去重處理，並限制自身診斷與最佳化狀態的成長。它的目標是協助伺服器管理者了解可避免的封包工作，同時優先維持遊戲正確性與插件相容性。

本插件的範圍是網路生命週期最佳化與封包工作量診斷。它不是清理 RAM 的工具、降低 ping 的工具、JVM 垃圾回收管理器、通用 TPS 加速器，也不是強制封包壓縮器。

### 功能

- 安全的 `ENTITY_METADATA` 精確狀態去重。
- 僅在邏輯 UI 狀態變更時轉送，並保留週期性刷新。
- 原始封包與實際轉送封包的對照分析。
- 依封包類別與封包類型分析工作量。
- 外送封包突發流量偵測。
- Netty 背壓觀察。
- 主執行緒交接延遲觀察。
- 戰鬥敏感的 Latency Guardian 診斷。
- Virtual Entity 診斷與實體封包追蹤。
- 最佳化器自身快取的全域與每玩家上限。
- 快取達到上限時採 fail-open，正常轉送封包。
- 生命週期事件的過期狀態清理。
- 被動 JVM heap 與垃圾回收指標。
- `/netdebug lifecycle`，查看目前狀態、上限、歷史高點、清理與 fail-open 計數。

這些功能在部分工作負載下可能減少可避免的封包工作與配置 / 保留壓力，但不會改變實際網路 RTT，也不保證提高 TPS、降低 RAM 使用量或改善 PvP 延遲。

### 相容性

| 要求 | 支援基準 / 目標 |
| --- | --- |
| 伺服器 | Paper 1.20–26.2 目標範圍 |
| 編譯基準 | Paper 1.20.4 |
| Java | Java 17 bytecode |
| PacketEvents | 封包層功能需要 2.13.0 |
| Minecraft 26.3 | 本版本不宣稱正式支援 |

Paper 1.20.4 是主要編譯與相容性基準。目標範圍內的其他版本是相容性目標，不代表每個伺服器版本都已個別測試。正式使用前，請在 staging 伺服器驗證實際使用的 Paper、PacketEvents 與插件組合。

若 PacketEvents 缺少或未啟用，插件仍可載入，指令介面也會保留，但封包層分析、安全封包最佳化與 Latency Guardian 的封包觀察會停用。`/netdebug status` 會顯示此狀態。

### 安裝

1. 安裝相容的 Paper 伺服器。
2. 安裝 PacketEvents `2.13.0` 或已驗證相容的版本。
3. 從 [GitHub Releases](https://github.com/Bserz1331/MinecraftNetOptimizer/releases) 下載 `MinecraftNetOptimizer-0.8.0.jar`。
4. 將 JAR 複製到伺服器的 `plugins/` 目錄。
5. 啟動伺服器並執行 `/netdebug status`。
6. 執行 `/netdebug lifecycle` 檢查插件自身的生命週期狀態。

請先備份，並在 staging 伺服器測試。既有設定檔不會被強制覆寫，缺少的新設定鍵會使用安全的程式預設值。

### 指令

```text
/netdebug
/netdebug status
/netdebug top
/netdebug server
/netdebug advice
/netdebug latency <player>
/netdebug lifecycle
/netdebug lifecycle <player>
/netdebug trace <player> [seconds]
/netdebug entities <player>
/netdebug virtual <player>
/netdebug optimize <status|on|off|reset>
/netdebug reset
/netdebug reload
```

遊戲內執行 `/netdebug` 會顯示執行玩家的分析資料，從主控台執行則顯示伺服器狀態。

### 權限與升級相容性

所有指令都需要 `twnetoptimizer.admin`，預設給予伺服器管理員 / OP。

為避免升級後既有權限或整合突然失效，權限節點、Java package 與插件實作類別保留既有技術識別名稱。公開插件名稱則是 `MinecraftNetOptimizer`。

### 設定重點

設定檔位於 `plugins/MinecraftNetOptimizer/config.yml`。

- `optimizer.metadata-dedupe.enabled`：安全的 metadata 去重，預設啟用。
- `optimizer.ui-dedupe.enabled`：UI 變更去重，預設啟用。
- `optimizer.particle-throttle.enabled`：僅限裝飾性粒子的限制器，預設停用。
- `latency-guardian.enabled`：戰鬥敏感診斷與優先狀態，預設啟用。
- `optimizer.cache.*`：全域、每玩家、payload 大小與過期項目的上限。
- `trace.*`：追蹤時間、結果保留、實體數量與 Virtual Entity 上限。
- `burst.*`：外送封包與觀察位元組的突發流量門檻。

快取達到硬上限時，MinecraftNetOptimizer 會採 fail-open，不保留新的 key，並正常轉送封包。過大或其他無法快取的 payload 不會被保留作去重。

### 安全邊界

MinecraftNetOptimizer：

- 不呼叫 `System.gc()`。
- 不修改 JVM 垃圾回收設定。
- 不修改 Netty channel watermark。
- 不使用侵入式 NMS 或 CraftBukkit 版本專用技巧。
- 不讀取或修改第三方插件的內部狀態。
- 不改變 TCP 順序。
- 不主動限制、丟棄、合併或重排移動、攻擊輸入、實體速度 / 擊退、傳送、背包確認、區塊 / 世界一致性、方塊狀態與 KeepAlive 封包。

選用的粒子限制器預設停用，且只針對裝飾性粒子，不會套用於上述關鍵流量。

### 生命週期診斷

`/netdebug lifecycle` 會顯示目前項目、設定上限、歷史高點、fail-open 次數、過期移除、trace / Virtual Entity 保留狀態、heap 已使用 / 已配置 / 最大值，以及觀察到的垃圾回收次數與時間。

`/netdebug lifecycle <player>` 會顯示單一線上玩家的 metadata、UI、粒子、trace 與 Virtual Entity 限制狀態。

歷史高點可能維持較高，因為它描述的是峰值使用量。實際網路 RTT 不在本插件控制範圍內，請使用可控的 staging 對照測試評估封包工作量與生命週期行為。

### 從原始碼建置

專案以 Java 17 為目標，使用 Maven：

```text
mvn -B -ntp verify
```

正式 artifact 為：

```text
target/MinecraftNetOptimizer-0.8.0.jar
```

### 原始碼、支援與授權

- 原始碼：[GitHub repository](https://github.com/Bserz1331/MinecraftNetOptimizer)
- 發布版本：[GitHub Releases](https://github.com/Bserz1331/MinecraftNetOptimizer/releases)
- 社群支援：[Discord](https://discord.gg/ukTERDqckB)
- 授權：[GNU GPL v3.0](LICENSE)

回報相容性或行為問題時，請附上 Paper、Java、PacketEvents 與插件版本、診斷輸出、log 與重現步驟。請不要將本插件描述為 `Zero Lag`、`No Ping`、`FPS Boost`、`Double TPS`、`RAM Cleaner` 或 `Ultimate Optimizer`。
