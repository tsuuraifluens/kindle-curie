# 良值 L1161/L1162 迷你手柄 → Kindle Click 兼容遥控器

本项目在原厂固件基础上做受控二进制补丁，让良值迷你蓝牙手柄被
Kindle Paperwhite 12 识别为 Kindle Click 兼容遥控器。当前采用
“原厂 UFW 解扰 → app.bin 补丁 → JLFS/UFW 校验重算 → 重打包”的
路线。

## 当前状态（2026-10-19）

| 版本 | 状态 | SHA-256 |
|---|---|---|
| v21b | 已实机：连接、翻页、亮度全部正常（旧键位） | `29FDADE0BE86BA0AFD27569970D70620EBCBD8A7CE9DB54E920EEDBF9945729C` |
| v22s | **已实机：新键位全部正常；A+B 未实现** | `2E517183D309BB2754DD30C6B056F0F6FC05A83335AADB2D82AE88C5E7DDB5FC` |
| v23 | 已实机：v22s + A+B 熄屏组合键 (开机回连需近1分钟) | `F4B18CD49CF547CADBE465F2C0F1937495761FF72A4D6F84E2E595CE280A6879` |
| v24 | **★极速回连版：20ms 极速广播 + 直通 ADV_IND (秒连 Kindle)** | `5E7448EF3AFB53671FF4E2A56743659FED9A916FF5F937E2FBE0F235690F2072` |

`release/SHA256SUMS.txt` 包含 release 下全部 UFW 的哈希清单。

v22s 的当前键位：

| 按键 | 功能 |
|---|---|
| A | 上一页 |
| B | 下一页 |
| 方向键上 | 下一页 |
| 方向键下 | 上一页 |
| 方向键左 | 亮度 - |
| 方向键右 | 亮度 + |

v23 额外实现：A+B 同时按住 → Consumer Power `0x0030`（熄屏/睡眠）。

## 硬件与固件事实

- 主控：杰理 **AC6321A**（AC632N/BD19 家族，q32s，73KB SRAM）
- 丝印：`BP3F723-21A4`
- Flash：8 脚 SPI NOR，丝印 `ZJZV`；512KB/1MB 证据存在冲突，
  不影响当前 UFW 重打包
- 原厂升级包：`L1161 L1162.ufw`，645,184 B
- 升级模式 USB：`VID_4C4A&PID_3442`
- App 区加密：SFC 流密码，ChipKey `0x190D`，32B 块
- UFW/JLFS 校验链已独立复现；重打包结果曾与参考 v1.7 逐字节一致

## 原厂只读基线

| 文件 | 大小 | SHA-256 |
|---|---:|---|
| `analysis/firmware/L1161 L1162.ufw` | 645,184 | `73005E7C885AAB2A644ADFDF2DBADC01802B34427E9E6FEBF823F50D4D7745E9` |
| `analysis/firmware/L1161 固件升级程序_1.4.40.exe` | 6,078,288 | `1D4AD683C7229E954A993491F4C11777021A1D40FE26E742F489E067C1CD11B5` |
| `analysis/firmware/升级步骤.doc` | 625,664 | `688456B41C5D0169DA924FA65435E027731BD878BE11652D43AD90368B4EBF43` |

原厂文件只读；所有修改只发生在派生 UFW 中。

## 目录结构

```
.
├─ analysis/                 分析、参考源码、协议、测试证据
│  ├─ firmware/              原厂 UFW / 刷机程序 / 只读清单
│  ├─ protocol/              Kindle Click 协议规格
│  ├─ reference/             Flipper / nRF52840 / Android / AC63 SDK
│  ├─ security/              安全审计材料
│  └─ test/                  离线审计日志
├─ docs/                     项目文档、版本报告、恢复指南
├─ outputs/                  构建产物与原始输出
├─ release/                  按版本整理的候选固件 + README
├─ tools/                    解扰、解密、重打包、审计脚本 + pi32 工具链
├─ work/                     反汇编、解扰中间文件、实验对象
├─ src/                      源码目录（当前为空/预留）
├─ build/                    临时构建
└─ tests/                    自动化测试目录（预留）
```

`analysis/reference/` 和 `tools/toolchain/` 是上游 SDK/工具链，
文件量很大，不属于本项目自研结果；项目自研脚本与文档见
`tools/*.js` 和 `docs/`。

## 关键脚本

| 脚本 | 用途 |
|---|---|
| `tools/descramble.js` | UFW 头/条目 KS 解扰 |
| `tools/dec32.js` | SFC/KS 流密码实验 |
| `tools/app_decrypt.js` | app 区解密 |
| `tools/build_kindle_click.js` | 通用补丁/重打包流水线 |
| `tools/build_v22s.js` | 安全键表版（当前已知可用） |
| `tools/build_v22.js` | v23 A+B 组合键构建脚本（产出 v23） |
| `tools/build_v24.js` | **v24 极速回连版构建脚本 (20ms 广播 + 直通 ADV_IND)** |
| `tools/audit_v22s.js` | v22s 完整性/CRC/描述符审计 |
| `tools/audit_v22.js` | v23/v22 完整性/CRC/描述符/trampoline 审计 |
| `tools/audit_v24.js` | **v24 完整性/CRC/描述符/20ms 广播间隔审计** |
| `tools/elf_vma.js` | 反汇编时修正 .text VMA |

## 重建与审计

```powershell
# 已知可用的安全键表版
node tools/build_v22s.js
node tools/audit_v22s.js

# A+B 组合键版
node tools/build_v22.js analysis/firmware/L1161` L1162.ufw outputs/IINE_Kindle_Click_v23_ABchord.ufw
node tools/audit_v22.js analysis/firmware/L1161` L1162.ufw outputs/IINE_Kindle_Click_v23_ABchord.ufw

# ★v24 极速回连优化版 (推荐)
node tools/build_v24.js analysis/firmware/L1161` L1162.ufw outputs/IINE_Kindle_Click_v24_FastConnect.ufw
node tools/audit_v24.js analysis/firmware/L1161` L1162.ufw outputs/IINE_Kindle_Click_v24_FastConnect.ufw
```

审计覆盖 UFW 主头/列表/条目 0 CRC、JLFS 四层 CRC、补丁集、
app 区外逐字节一致、尾部 0xFF、HID 描述符结构。

## 刷写安全

- Codex **不自动刷写**，只生成候选固件和人工步骤。
- 刷前必须确认工作区外原厂 UFW 备份存在且 SHA-256 正确。
- 使用官方 `L1161 固件升级程序_1.4.40.exe`，人工选择文件。
- 刷写过程中不要断电/拔线；失败立即停止，不反复烧录。
- 原厂恢复：官方工具 + 原厂 UFW；详见 `docs/RECOVERY_GUIDE.md`。

## 已知限制

- Kindle 只能被遥控器睡眠，不能被遥控器唤醒。
- 当前“配对键点按开机”是原厂唤醒源；A/B/方向键不能直接开机。
- 让任意键开机需要电源/唤醒层改动或硬件改线，尚未实现。
- A+B 组合键需要同一扫描周期内两键同时按下；松开不同步时可能
  残留一次翻页（Kindle 已睡眠时通常忽略）。

## 文档索引

- `docs/PROJECT_INDEX.md`：完整文件与文档索引
- `docs/RESULTS.md`：版本结果矩阵与失败原因
- `docs/PROJECT_STATUS.md`：当前阶段与下一步
- `docs/CANDIDATE_V22S_REPORT.md`：当前已知可用键位版
- `docs/CANDIDATE_V23_REPORT.md`：A+B 组合键候选
- `docs/RE_GROUND_TRUTH.md`：逆向证据总表
- `docs/FIRMWARE_ANALYSIS.md`：固件结构
- `docs/CHIP_IDENTIFICATION.md`：芯片识别
- `docs/RECOVERY_GUIDE.md`：恢复步骤
- `docs/FLASH_SAFETY_CHECKLIST.md`：刷写检查表
- `analysis/protocol/kindle-click-spec.md`：协议规格

