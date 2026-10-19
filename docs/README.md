# Kindle BLE 翻页器工程技术文档全集 (Documentation Hub)

本文档库为 **kind-curie** 项目的核心技术文档导航中心。项目涵盖良值（IINE L1161/L1162）手柄逆向分析、ESP32-S3 原型验证、ESP32-C3 产品化硬件与固件、以及 Android 手机端双模蓝牙模拟器。所有规范与设计文档按领域划分为四大专区：

---

## 目录索引 (Documentation Index)

- [一、硬件工程与产品化设计 (`docs/hardware/`)](#一硬件工程与产品化设计-docshardware)
- [二、ESP32 固件与测试规范 (`docs/esp32/`)](#二esp32-固件与测试规范-docsesp32)
- [三、Android 模拟器与 Root 事务规范 (`docs/android/`)](#三android-模拟器与-root-事务规范-docsandroid)
- [四、逆向工程与协议分析专项 (`docs/reverse_engineering/`)](#四逆向工程与协议分析专项-docsreverse_engineering)
- [五、核心交付报告与证据库索引](#五核心交付报告与证据库索引)

---

## 一、硬件工程与产品化设计 (`docs/hardware/`)

针对产品化量产硬件（基于 ESP32-C3-WROOM-02-N4）的完整系统工程与电气/制造规范：

| 文档名称 | 核心主题 | 说明概要 |
| :--- | :--- | :--- |
| [`PROJECT_AUDIT.md`](hardware/PROJECT_AUDIT.md) | 原型技术审计 | 针对 ESP32-S3 开发板原型的功耗、尺寸、外围器件与产品化鸿沟审计 |
| [`EXISTING_PROTOCOL.md`](hardware/EXISTING_PROTOCOL.md) | 协议与交互规范 | 串口 CLI (15条命令)、BLE GAP 广播及 GATT 属性服务体系规范 |
| [`KNOWN_CONSTRAINTS.md`](hardware/KNOWN_CONSTRAINTS.md) | 设计与制造约束 | 机械装配公差、电池仓体积、射频天线净空区及 DFM 规则限制 |
| [`SYSTEM_REQUIREMENTS.md`](hardware/SYSTEM_REQUIREMENTS.md) | 系统需求定义书 | 功能指标、电气特性（超长续航与零漏电测量）、工业可靠性指标 |
| [`SYSTEM_ARCHITECTURE.md`](hardware/SYSTEM_ARCHITECTURE.md) | 软硬件架构设计 | 电源管理拓扑、状态转换机、按键与触觉震动响应时序图 |
| [`INTERFACE_SPEC.md`](hardware/INTERFACE_SPEC.md) | 硬件接口规范 | Type-C 引脚定义、ESD 防护、GPIO 分配矩阵与治具测试点 (TP) 分布 |
| [`MCU_COMPARISON.md`](hardware/MCU_COMPARISON.md) | 主控芯片横向评测 | ESP32-C3 vs ESP32-S3 vs nRF52810 vs 杰理 AC632N 12 维度深度对比 |
| [`COST_MODEL.md`](hardware/COST_MODEL.md) | 阶梯成本核算 | 100台/1000台/5000台规模下的完整 BOM、贴片与机壳开模成本模型 |
| [`MCU_DECISION.md`](hardware/MCU_DECISION.md) | 主控定型决策书 | 综合供货风险、SDK 生态与成本，正式定型 ESP32-C3 为量产主控 |

---

## 二、ESP32 固件与测试规范 (`docs/esp32/`)

ESP32-S3 基准验证固件与 ESP32-C3 固件的核心开发、烧录与测试手册：

| 文档名称 | 核心主题 | 说明概要 |
| :--- | :--- | :--- |
| [`esp32_reference.md`](esp32/esp32_reference.md) | 基准实现参数表 | ESP32-S3 官方模拟基准方案各项电气与 BLE HID 协议参数 |
| [`esp32s3_build_flash_guide.md`](esp32/esp32s3_build_flash_guide.md) | 构建与烧录指南 | ESP-IDF v5.3.2 编译环境配置、CMake 构建与 esptool 串口烧录流程 |
| [`esp32s3_firmware_implementation.md`](esp32/esp32s3_firmware_implementation.md) | 固件实现详述 | NimBLE 协议栈初始化、自定义 GATT 表注入与按键防抖/限流状态机 |
| [`esp32s3_open_questions.md`](esp32/esp32s3_open_questions.md) | 联调未决问题表 | 实机抓包、低功耗休眠模式唤醒延迟与天线匹配后续事项 |
| [`esp32s3_test_results.md`](esp32/esp32s3_test_results.md) | 自动化测试报告 | 真实硬件 `COM3` 上 25/25 项自动化测试通过记录及串口抓取日志 |
| [`experiment_plan.md`](esp32/experiment_plan.md) | 阶段化实验验证 | 阶段化实机连调方案与验证步骤 |

---

## 三、Android 模拟器与 Root 事务规范 (`docs/android/`)

Android 手机端免 Root (BLE 外设) / 经典蓝牙 (Root DID) 模拟器的实现与底层安全机制规范：

| 文档名称 | 核心主题 | 说明概要 |
| :--- | :--- | :--- |
| [`android_app_implementation_spec.md`](android/android_app_implementation_spec.md) | Android App 实现规范 | 双模式切换架构、GATT Server 回调防冲突队列与音量翻页映射规范 |
| [`root-file-transaction.md`](android/root-file-transaction.md) | Root 事务状态机 | 针对 `bt_did.conf` 的强制修改前备份、SHA-256 复核、原子替换与回滚状态机 |
| [`root-recovery-test-report.md`](android/root-recovery-test-report.md) | 故障注入测试报告 | 模拟写盘断电、强行停止与外部并发篡改等全阶段崩溃恢复测试结果 |
| [`root-security-audit.md`](android/root-security-audit.md) | Root 权限安全审计 | 命令注入白名单防护、Toybox 兼容性、SELinux 上下文保持与风险评级 |
| [`root-supported-devices.md`](android/root-supported-devices.md) | 系统兼容性矩阵 | Android 8.0~14 蓝牙协议栈差异、Fluoride vs Fluoride-Next 边界 |

---

## 四、逆向工程与协议分析专项 (`docs/reverse_engineering/`)

良值（IINE L1161/L1162）手柄官方 USB DFU 升级包与 Kindle 12 代 (FW 5.20.1) 固件端到端逆向成果：

| 文档名称 | 核心主题 | 说明概要 |
| :--- | :--- | :--- |
| [`chip_identification.md`](reverse_engineering/chip_identification.md) | 主控芯片鉴定 | 确证主控为杰理 AC632N (BD19 / q32s 32位 RISC) 架构与启动特征 |
| [`device_identity_and_allowlist.md`](reverse_engineering/device_identity_and_allowlist.md) | 白名单与设备分类 | Kindle `/etc/input_manager/devices.json` 与 `DeviceClassifier` 逻辑 |
| [`device_identification_callgraph.md`](reverse_engineering/device_identification_callgraph.md) | 设备识别调用链路 | 从内核 evdev 事件到用户态 `acsbtfd` / `input_manager` 的调用树 |
| [`ERASE_BEHAVIOR_ANALYSIS.md`](reverse_engineering/ERASE_BEHAVIOR_ANALYSIS.md) | 全片擦除行为分析 | 升级器 `eraseFlash` 真实擦除范围（512KB）与基础映像重写机制 |
| [`feasibility_assessment.md`](reverse_engineering/feasibility_assessment.md) | 改造可行性研判 | A/B/C/D/E 五类方案（配置/补丁/重构/Bootloader/硬件替代）可行性评估 |
| [`firmware_diff.md`](reverse_engineering/firmware_diff.md) | 固件差异与补丁表 | 原厂固件 vs 目标 Kindle Click 实现的全维度差异矩阵与修改偏移 |
| [`firmware_inventory.md`](reverse_engineering/firmware_inventory.md) | 固件档案清单 | 升级包哈希指纹、UFW 容器结构、各分区物理/虚拟地址对照表 |
| [`hid_analysis.md`](reverse_engineering/hid_analysis.md) | HID 描述符逆向 | 原厂 6 大模式 HID 报告描述符语法树、按键路由与协议缺陷深入解构 |
| [`hid_protocol_spec.md`](reverse_engineering/hid_protocol_spec.md) | 蓝牙 HID 协议规范 | BLE GAP 广播、GATT 属性数据库与标准 8/9 字节键盘输入报文规范 |
| [`input_mapping.md`](reverse_engineering/input_mapping.md) | 按键过滤与映射 | Kindle `capability_filter` 键码放行白名单 (`KEY_PAGEUP` / `KEY_PAGEDOWN`) |
| [`kindle_compatibility.md`](reverse_engineering/kindle_compatibility.md) | 三重拦截冲突研判 | 扫描过滤、白名单校验、输入管道三重防御与良值手柄冲突碰撞分析 |
| [`open_questions.md`](reverse_engineering/open_questions.md) | 逆向未决问题归纳 | 杰理 SDK 私有服务、触控模拟插值与电量上报细节备忘录 |
| [`PATCH_AUDIT.md`](reverse_engineering/PATCH_AUDIT.md) | 候选固件补丁审计 | Candidate v0.1 逐字节审计、安全性校验与键值重映射有效性复核 |
| [`PERSISTENT_DATA_RISK.md`](reverse_engineering/PERSISTENT_DATA_RISK.md) | 持久化数据风险 | 擦除 Flash 时 VM 用户区、BTIF 蓝牙密钥、MAC 地址区擦除与恢复研判 |
| [`PROTOCOL_AND_HID.md`](reverse_engineering/PROTOCOL_AND_HID.md) | 模式字典与私有协议 | 6 种模式切换状态机、GATT 句柄表与良值 App 私有配置命令字典 |
| [`RECOVERY_CAPABILITY_MATRIX.md`](reverse_engineering/RECOVERY_CAPABILITY_MATRIX.md) | 救砖恢复能力矩阵 | 芯片 Mask ROM（`0x02100000`）故障回退与引脚短接强制下载机制 |
| [`ROM_DOWNLOAD_ANALYSIS.md`](reverse_engineering/ROM_DOWNLOAD_ANALYSIS.md) | 芯片 ROM 下载模式 | Mask ROM 通信协议、USB 枚举特征及官方升级器原生救砖能力验证 |
| [`TEST_PLAN.md`](reverse_engineering/TEST_PLAN.md) | 测试方案与救砖 SOP | 阶梯式验证路线、实机刷写步骤、异常判定与 100% 救砖实操规程 |
| [`update_protocol.md`](reverse_engineering/update_protocol.md) | USB DFU 升级协议 | Qt 升级器 SCSI 穿透命令交互、`loader.bin` 动态注入机制分析 |
| [`UPGRADER_REVERSE_ENGINEERING.md`](reverse_engineering/UPGRADER_REVERSE_ENGINEERING.md) | 升级程序逆向分析 | 升级器二进制结构、动态加解密、校验算法与界面调用链路反编译 |

---

## 五、核心交付报告与证据库索引

- **里程碑交付报告 (`reports/`)**:
  - [`FINAL_REPORT.md`](../reports/FINAL_REPORT.md)：硬件产品化工程最终交付报告（物料、电路、PCB、结构、固件全链路定型）。
  - [`IINE_GAMEPAD_COMPLETE_REVERSE_ENGINEERING_REPORT.md`](../reports/IINE_GAMEPAD_COMPLETE_REVERSE_ENGINEERING_REPORT.md)：十万字级良值手柄原厂固件全功能深度逆向报告。
  - [`FINAL_CONCLUSION.md`](../reports/FINAL_CONCLUSION.md)：固件升级器逆向与 100% 救砖防砖能力最终权威结论。
  - [`FINAL_SUMMARY.md`](../reports/FINAL_SUMMARY.md)：第二阶段深度逆向结论对照、理论突破与产出物清单。
  - [`KINDLE_BLE_HID_REVERSE_ENGINEERING.md`](../reports/KINDLE_BLE_HID_REVERSE_ENGINEERING.md)：Kindle BLE HID 逆向综合总结与输入管道破局方案。

- **机器可复核证据与数据映射表 (`evidence/`)**:
  - [`evidence_index.md`](../evidence/evidence_index.md)：EVID-01 至 EVID-13 原生固件逆向证据索引。
  - [`EVIDENCE_MATRIX.csv`](../evidence/EVIDENCE_MATRIX.csv)：E0~E5 证据可信度评估矩阵。
  - [`ADDRESS_MAP.csv`](../evidence/ADDRESS_MAP.csv)：Flash / UFW / 虚拟内存统一地址空间映射表。
  - [`FLASH_LAYOUT.csv`](../evidence/FLASH_LAYOUT.csv)：512KB SPI Flash 物理分区与擦写属性表。
  - [`firmware_inventory.csv`](../evidence/firmware_inventory.csv)：固件包文件与哈希清单。

---

## 六、最终发布成果物总览 (`release/`)

- [`release/README.md`](../release/README.md)：三大发布方案全景导航与选型指南。
- [`release/01_ESP32_Hardware_Remote/`](../release/01_ESP32_Hardware_Remote)：ESP32 独立硬件实体翻页器方案（ESP32-S3 原型 + ESP32-C3 量产版固件、引导表、一键烧录脚本）。
- [`release/02_ESP32_PC_Controller/`](../release/02_ESP32_PC_Controller)：ESP32 电脑控制端方案（Tkinter 桌面悬浮窗、全局键盘快捷键、局域网手机 Web 遥控器）。
- [`release/03_IINE_Gamepad_Mod/`](../release/03_IINE_Gamepad_Mod)：良值手柄 (L1161/L1162) 固件修改方案（已实测通过的 v22s 稳定版 UFW、v23 熄屏版 UFW、官方 USB 升级程序与 100% 救砖防砖备份）。
- [`iine_gamepad_jieli_mod/`](../iine_gamepad_jieli_mod)：良值手柄杰理 AC6321A 固件端到端逆向分析与自动化补丁流水线工程。
