# 固件档案与文件清单报告 (Firmware Inventory & Manifest)

**报告编号**: EVID-INV-IINE-001  
**分析对象**: 良值 (IINE) 迷你手柄官方固件升级包 (阅星瞳定制版)  
**分析时间**: 2026-10-14  
**分析工具**: REA (Reverse Engineer Anything v6.4.0) / Python 3.13 专用逆向套件  
**分析状态**: **已验证完成 (VERIFIED)**

---

## 1. 原始文件档案清单 (Raw Archive Manifest)

所有原始文件均保存在只读分析副本目录 [`raw_pkg/`](../../raw_pkg) 与官方出厂固件备份目录 [`release/03_IINE_Gamepad_Mod/flasher/stock_backup/`](../../release/03_IINE_Gamepad_Mod/flasher/stock_backup) 中，绝不覆盖或篡改源文件。

| 文件名称 | 相对路径 | 文件大小 (字节) | 文件类型 | SHA-256 校验和 | 证据状态 |
|:---|:---|:---|:---|:---|:---|
| `media_1791988250685_a8a43478.zip` | `raw_pkg/IINE_L1161_L1162_firmware_pkg.zip` | 7,146,266 | ZIP 压缩包 | `6DD5D46599C6E1EEB8BD2623C15D14C7A3AEE61163F46DA0DF38953C690DAC61` | **已证实** |
| `L1161 L1162 IINE-reader.ufw` | `release/03_IINE_Gamepad_Mod/flasher/stock_backup/L1161 L1162.ufw` | 645,184 | JieLi UFW 固件容器 | `73005E7C885AAB2A644ADFDF2DBADC01802B34427E9E6FEBF823F50D4D7745E9` | **已证实** |
| `L1161 固件升级程序_1.4.40.exe` | `release/03_IINE_Gamepad_Mod/flasher/L1161 固件升级程序_1.4.40.exe` | 6,078,288 | Win32 PE (UPX 加壳) | `1D4AD683C7229E954A993491F4C11777021A1D40FE26E742F489E067C1CD11B5` | **已证实** |
| `升级步骤.doc` | `iine_gamepad_jieli_mod/analysis/firmware/升级步骤.doc` | 625,664 | OLE Word 文档 | `688456B41C5D0169DA924FA65435E027731BD878BE11652D43AD90368B4EBF43` | **已证实** |

---

## 2. 升级包解包与派生文件清单 (Derived Unpacked Inventory)

使用自主适配的 JieLi New-FW 解密提取脚本 [`scripts/jieli_tools/fwunpack_newfw.py`](../../scripts/jieli_tools/fwunpack_newfw.py)，成功对 `L1161 L1162.ufw` 完成全量解包与流密码解密，提取产物结构如下：

| 派生文件名称 | 相对路径 | 大小 (字节) | Flash 规划偏移 | SHA-256 校验和 | 属性与功能描述 |
|:---|:---|:---|:---|:---|:---|
| `decrypted.bin` | `extracted/ufw_unpacked/decrypted.bin` | 212,992 | `0x000000` | `81761DDFD1E3C519A799EAF70A52269199585EF5F74046E6A0E5852D5C166248` | **解密全量 Flash 镜像** (前 208KB，含引导、配置与应用) |
| `app.bin` | `extracted/ufw_unpacked/files/app.bin` | 198,824 | `0x002120` | `2FB44F4235BD4ED7183439F31EE2A31FB0052D19DD736E20D1D251FD5E1FF259` | **主应用程序二进制** (入口 `0x01E00120`，q32s 指令集) |
| `cfg_tool.bin` | `extracted/ufw_unpacked/files/cfg_tool.bin` | 722 | `0x0329C8` | `0CCC2FD579597CA9D91E485457673D7F2525E00D5BFE8D081920E95E4062A0F1` | **配置工具参数区** (蓝牙广播名、RF 参数与多模式预设) |
| `p11_code.bin` | `extracted/ufw_unpacked/files/p11_code.bin` | 4,096 | `0x032C9A` | `D37E34CC8200CAE2A372C1A83F46C1F65DB756984E2470B7437734E7B5C5FC4C` | **P11 协处理器固件** (负责超低功耗休眠与按键唤醒) |
| `uboot.boot` | `extracted/ufw_unpacked/top/uboot.boot` | 4,831 | `0x0000A0` | `A64171C3CF41406E258B9C39300F90D3BA0BF72476D42CDF62664575FD78D012` | **二级引导程序 (SPL)** (初始化 PLL、Flash 控制器并加载 App) |
| `isd_config.ini` | `extracted/ufw_unpacked/top/isd_config.ini` | 122 | `0x00137F` | `274A3CAE17F7BD8AE6FA086CF5E5DD472D88ADEBFA51AC661E95D9D2C46027D1` | **烧录配置文件** (内含加密密钥 `ChipKey: 0x190D` 与 UART/引脚配置) |
| `jlfw.yaml` | `extracted/ufw_unpacked/jlfw.yaml` | 209 | 元数据 | `C02825756C67D8F783789EA7EF42A00B99F7C244C03D9A797D004C3E6FF966FF` | **固件拓扑描述文件** (格式、芯片键、入口点与组件列表) |

---

## 3. 固件容器格式与架构特征 (Container Format Facts)

1. **容器类型**:
   - 确认为珠海杰理科技专有的 **Unified Firmware (`.ufw`)** 容器格式（`jl-new-fw` 规范）。
   - 容器由 `0x40` 字节外层头部、每项 `0x50` 字节的文件分段表（Entry List），以及后续的数据载荷构成。
2. **完整固件 vs 增量补丁**:
   - 固件头部明确声明目标 Flash 容量为 `0x07F000` (508 KB)，内部包含完整的 Bootloader (`uboot.boot`)、Flash 文件系统 (`JLFS`)、配置表 (`cfg_tool.bin`)、低功耗内核 (`p11_code.bin`) 以及完整的蓝牙应用程序 (`app.bin`)。
   - 升级说明文档明确要求“勾选擦除整个 FLASH”，证实该升级包为**全量独立固件映像 (Full Standalone Firmware)**，并非依赖旧版本的增量差分补丁。
3. **加解密与混淆特征**:
   - 外层头部与分段表采用固定密钥 `0xFFFF` 的 JieLi ENC 流密码（基于 LFSR 0x1021 多项式）混淆。
   - 应用程序载荷 `app.bin` 采用以 32 字节为块的 `jl_sfc_cipher` 硬件 SPI 控制器流加密，解密密钥为 `0x190D`（从 `isd_config.ini` 的前 32 字节签名中成功解析恢复）。
4. **完整性校验体系**:
   - 全程依赖 **`jl_crc16`** (CCITT-FALSE，多项式 `0x1021`，初始值 `0x0000`)。
   - **完全没有 RSA、ECC 等非对称数字签名，亦无安全启动 (Secure Boot) 公钥鉴权机制**。
