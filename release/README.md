# Kindle BLE 翻页器最终发布成果全集 (Kindle Remote Final Deliverables)

欢迎使用 **Kindle BLE 翻页器开源套件**！本目录归档了针对 **Amazon Kindle Paperwhite (含 2024 第 12 代 / FW 5.20.1)** 完美兼容的所有成品固件、客户端工具、刷机程序与一键烧录脚本。

针对不同用户群体与使用场景，本项目提供 **三大工业级解决方案**：

---

## 三大方案选型指南 (Solution Matrix)

```
                            ┌───────────────────────────────────────────────┐
                            │ 你想以何种方式控制 Kindle 翻页？              │
                            └──────────────────────┬────────────────────────┘
                                                   │
                ┌──────────────────────────────────┼──────────────────────────────────┐
                ▼                                  ▼                                  ▼
      【已有良值迷你手柄】               【想制作独立小巧实体翻页器】          【想在电脑上直接用键盘翻页】
                │                                  │                                  │
    ┌───────────────────────┐          ┌───────────────────────┐          ┌───────────────────────┐
    │ 方案三：良值手柄改造  │          │ 方案一：ESP32 硬件方案 │          │ 方案二：电脑控制端方案│
    │  03_IINE_Gamepad_Mod  │          │01_ESP32_Hardware_Remote│          │02_ESP32_PC_Controller │
    └───────────────────────┘          └───────────────────────┘          └───────────────────────┘
```

| 方案编号 | 方案名称 | 适用载体 | 成本 / 门槛 | 核心亮点 | 推荐目录 |
| :---: | :--- | :--- | :---: | :--- | :--- |
| **01** | **ESP32 独立硬件方案** | ESP32-S3 开发板 / ESP32-C3 定制 PCB | 极低成本 (<20元) | 原生工业级固件，功耗极低，开机即连，支持物理微动按键与触觉震动反馈 | [`01_ESP32_Hardware_Remote/`](./01_ESP32_Hardware_Remote) |
| **02** | **ESP32 电脑控制方案** | Windows PC + ESP32 适配器 | 零改造门槛 | 黑暗模式悬浮窗、键盘全局快捷键（空格/回车翻页）、局域网手机浏览器扫码遥控 | [`02_ESP32_PC_Controller/`](./02_ESP32_PC_Controller) |
| **03** | **良值手柄固件修改方案** | 良值 L1161 / L1162 迷你手柄 | 现成手柄即用 | **免越狱、免拆壳、免焊接**，直接用官方 USB 升级程序 10 秒刷入，100% 可退回原厂 | [`03_IINE_Gamepad_Mod/`](./03_IINE_Gamepad_Mod) |

---

## 发布文件结构索引 (Directory Map)

```text
release/
├── README.md                                    # 本导航指南
│
├── 01_ESP32_Hardware_Remote/                    # 【方案一】ESP32 硬件翻页器方案
│   ├── README.md                                # 硬件制作、引脚定义与烧录说明
│   ├── esp32s3/                                 # ESP32-S3 验证版固件 (已实机通过 25/25 用例)
│   │   ├── binaries/
│   │   │   ├── kindle_page_turner_v1.0.0_merged.bin  # [0x00000] 一键全量镜像
│   │   │   ├── bootloader.bin                        # [0x00000] 引导分区
│   │   │   ├── partition-table.bin                   # [0x08000] 分区表
│   │   │   └── kindle_page_turner_esp32s3.bin        # [0x10000] 应用程序
│   │   ├── flash_firmware.bat                   # Windows 串口一键烧录批处理
│   │   ├── flash_firmware.ps1                   # PowerShell 烧录脚本
│   │   └── SHA256SUMS.txt
│   └── esp32c3/                                 # ESP32-C3 产品化量产固件 (超低成本)
│       ├── binaries/
│       │   ├── kindle_remote_c3_merged.bin           # [0x00000] 一键全量镜像
│       │   ├── bootloader.bin                        # [0x00000] 引导分区
│       │   ├── partition-table.bin                   # [0x08000] 分区表
│       │   └── kindle_remote_c3_firmware.bin         # [0x10000] 应用程序
│       ├── flash_firmware.bat                   # Windows 串口一键烧录批处理
│       ├── flash_firmware.ps1                   # PowerShell 烧录脚本
│       └── SHA256SUMS.txt
│
├── 02_ESP32_PC_Controller/                      # 【方案二】ESP32 电脑控制端方案
│   ├── README.md                                # 电脑控制端详细功能与快捷键手册
│   ├── kindle_remote_gui.py                     # Tkinter 桌面端 + Web 局域网遥控器 Python 程序
│   ├── launch_remote.bat                        # Windows 双击一键启动批处理
│   ├── requirements.txt                         # 运行依赖 (pyserial)
│   └── install_deps.bat                         # 自动安装运行依赖批处理
│
└── 03_IINE_Gamepad_Mod/                         # 【方案三】良值手柄固件修改方案 (L1161/L1162)
    ├── README.md                                # 手柄按键功能图解、DFU 刷机与救砖 SOP
    ├── firmware/                                # 最终修改版 UFW 固件
    │   ├── IINE_Kindle_Click_v24_FastConnect.ufw     # ★极速回连版 (20ms 广播秒连 + A+B 熄屏，首选推荐)
    │   ├── IINE_Kindle_Click_v23_ABchord.ufw         # 进阶功能版 (含 A+B 组合键熄屏)
    │   ├── IINE_Kindle_Click_v22s_keymap.ufw         # 推荐稳定版 (A/B 翻页、方向键翻页/调光，全实机通过)
    │   └── IINE_Kindle_Click_v21b_Composite.ufw      # 备用基准版 (旧键位)
    ├── flasher/                                 # 官方刷机工具与原厂救砖备份
    │   ├── L1161 固件升级程序_1.4.40.exe
    │   ├── 升级步骤说明.md
    │   └── stock_backup/
    │       └── L1161 L1162.ufw                  # 官方原厂出厂固件 (100% 救砖防砖备份)
    └── SHA256SUMS.txt                           # 固件完整性 SHA-256 校验和
```

---

## 全量固件 SHA-256 校验清单 (Integrity Ledger)

在刷写前，可通过对比哈希值确保文件未受任何损坏：

### 1. 良值手柄修改固件 (`03_IINE_Gamepad_Mod/firmware/`)
- `IINE_Kindle_Click_v24_FastConnect.ufw`:  
  `5e7448ef3afb53671ff4e2a56743659fed9a916ff5f937e2fbe0f235690f2072`
- `IINE_Kindle_Click_v23_ABchord.ufw`:  
  `f4b18cd49cf547cadbe465f2c0f1937495761ff72a4d6f84e2e595ce280a6879`
- `IINE_Kindle_Click_v22s_keymap.ufw`:  
  `2e517183d309bb2754dd30c6b056f0f6fc05a83335aadb2d82ae88c5e7ddb5fc`
- `IINE_Kindle_Click_v21b_Composite.ufw`:  
  `29fdade0be86ba0afd27569970d70620ebcbd8a7ce9db54e920eedbf9945729c`
- `stock_backup/L1161 L1162.ufw` (原厂基准):  
  `73005e7c885aab2a644adfdf2dbadc01802b34427e9e6febf823f50d4d7745e9`

### 2. ESP32 硬件全量烧录镜像 (`01_ESP32_Hardware_Remote/`)
- `esp32s3/binaries/kindle_page_turner_v1.0.0_merged.bin`:  
  `25aa6cae137f7c688327d532a4e23cf6347df2da62d04a6018318182df563b71`
- `esp32c3/binaries/kindle_remote_c3_merged.bin`:  
  通过 `flash_firmware.bat` 自动校验烧录。

---

## 关联研发与规范文档导航

- 硬件工程与电路规范：[`docs/hardware/`](../docs/hardware)
- ESP32 固件测试规范：[`docs/esp32/`](../docs/esp32)
- 逆向工程与协议解密：[`docs/reverse_engineering/`](../docs/reverse_engineering)
- 全域技术文档索引表：[`docs/README.md`](../docs/README.md)
