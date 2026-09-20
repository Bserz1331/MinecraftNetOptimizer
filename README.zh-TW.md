# MinecraftNetOptimizer

![MinecraftNetOptimizer icon](MinecraftNetOptimizer-icon.png)


以保守策略進行網路最佳化與封包工作量診斷的 Paper 插件。


[![Build](https://github.com/Bserz1331/MinecraftNetOptimizer/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/Bserz1331/MinecraftNetOptimizer/actions/workflows/build.yml)
[![Maven verify](https://github.com/Bserz1331/MinecraftNetOptimizer/actions/workflows/maven-verify.yml/badge.svg?branch=main)](https://github.com/Bserz1331/MinecraftNetOptimizer/actions/workflows/maven-verify.yml)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)


**語言：** [English](README.md) | 繁體中文


## 功能定位


MinecraftNetOptimizer 觀察封包流量，對安全的封包類別採取保守的去重處理，並限制自身診斷與最佳化狀態的成長。它的目標是協助伺服器管理者了解可避免的封包工作，同時優先維持遊戲正確性與插件相容性。


本插件的範圍是網路生命週期最佳化與封包工作量診斷。它不是清理 RAM 的工具、降低 ping 的工具、JVM 垃圾回收管理器、通用 TPS 加速器，也不是強制封包壓縮器。


## 功能


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


## 相容性


| 要求 | 支援基準 / 目標 |
| --- | --- |
| 伺服器 | Paper 1.20–26.2 目標範圍 |
| 編譯基準 | Paper 1.20.4 |
| Java | Java 17 bytecode |
| PacketEvents | 封包層功能需要 2.13.0 |
| Minecraft 26.3 | 本版本不宣稱正式支援 |


Paper 1.20.4 是主要編譯與相容性基準。目標範圍內的其他版本是相容性目標，不代表每個伺服器版本都已個別測試。正式使用前，請在 staging 伺服器驗證實際使用的 Paper、PacketEvents 與插件組合。

