# ESP32-S3 原型项目审计报告 (Project Audit)

**文档编号**: ENG-AUDIT-001  
**生成日期**: 2026-10-14  
**项目代号**: kind-curie / Kindle Remote (Kindle Click)  
**审计目标**: 确认既有硬件、固件、通信协议、外围外挂与结构现状，建立可量产工程转化的绝对基线。

---

## 1. 原型硬件与主控芯片现状审计

### 1.1 主控芯片与模组特征
- **芯片型号**: Espressif Systems ESP32-S3 (Xtensa® 32-bit LX7 双核处理器，最高 240 MHz)。
- **硬件版本与封装**: QFN56，Silicon Revision v0.2。
- **存储配置**: 
  - 外置/内置 Flash: 16 MB Quad SPI Flash (固件当前配置为 2 MB 兼容寻址边界，APP 分区 1 MB)。
  - PSRAM: 8 MB Octal SPI PSRAM (AP_3v3)。
- **板载外围通信**: 
  - 串口桥接芯片: WCH CH343P / CH343G (USB-Enhanced-SERIAL，VID `0x1A86`，PID `0x55D3`)。
  - 原型板载接口: 双 USB Type-C 接口（1 个连接 CH343 UART0 用于调试烧录，1 个连接 ESP32-S3 原生 USB OTG / USB Serial-JTAG 引脚 IO19/IO20）。
- **MAC 与射频物理地址**:
  - Base MAC: `30:ED:A0:A1:9B:7C`
  - BLE Public Address: `30:ED:A0:A1:9B:7E` (Company ID 0x30EDA0 / Espressif Inc.)

### 1.2 原型物理形态与供电限制
- **外形尺寸**: 标准 ESP32-S3-DevKitC-1 开发板尺寸，约 **70.0 mm × 25.5 mm × 12.0 mm**（含 2.54mm 排针），严重超出消费级便携式翻页器的人体工学要求。
- **供电机制**: 仅支持 USB 5V VBUS 供电，通过板载 AMS1117-3.3 或 SGM2211 LDO 降压至 3.3V。
- **静态功耗测试推算**: 
  - 原型板带有 CH343 芯片、电源指示灯及双核高频 CPU 运行态，实测整板待机电流在 **25 mA ~ 38 mA** 之间。
  - 此原型设计**完全无法直接使用纽扣电池 (CR2032)**，若采用微型锂电池（如 150mAh）也只能维持数小时，必须在量产硬件中彻底重构电源域与低功耗睡眠机制。

---

## 2. 固件架构与软件栈审计

### 2.1 工具链与构建系统
- **语言标准**: C99 / C11 标准 C 代码。
- **操作系统 / RTOS**: FreeRTOS (ESP-IDF 移植版，`CONFIG_FREERTOS_HZ=1000`)。
- **SDK 版本**: 官方 Espressif ESP-IDF v5.3.2。
- **构建系统**: CMake 3.30.2 + Ninja 1.12.1 (`idf.py build`)。
- **底层依赖项**:
  - `esp_system`, `esp_log`, `nvs_flash`, `driver/uart`, `driver/gpio`, `esp_timer`
  - `nimble` (Apache NimBLE 蓝牙协议栈主机与控制器)

### 2.2 模块划分与代码资产
现有固件目录 `esp32s3_firmware/main/` 结构清晰、高度解耦：
1. `device_identity.c / .h`: 负责管理 Amazon Lab126 专用白名单凭据（VID `0x0171`、PID `0x0443`、PnP ID、EIR 广播数据等）。
2. `hid_report.c / .h`: 负责 92 字节复合 HID 报告描述符（Keyboard + Consumer Control），管理 40ms 持锁、80ms 防抖及 5Hz 速率限制状态机。
3. `ble_hid.c / .h`: 负责 NimBLE GATT 数据库注册（DIS 0x180A, BAS 0x180F, HIDS 0x1812）、SMP 安全配对（Just Works + LE SC）、广播包动态构建与发送。
4. `battery_service.c / .h`: 负责电量读取与 GATT Notification 推送（当前使用软件模拟参数 95%）。
5. `button_input.c / .h`: 负责 GPIO 输入轮询任务（支持外部引脚动态配置，当前默认 `-1` 禁用）。
6. `serial_console.c / .h`: 负责基于 UART0 的交互式命令行（支持 15+ 条诊断与控制命令）。
7. `diagnostics.c / .h`: 负责 64 节点事件环形缓冲区、对端 GATT 读取计数器（Peer Verified 跟踪）。
8. `self_test.c / .h`: 板载 7 项协议编解码回归单元测试。

---

## 3. 功能验证状态与工程边界

| 功能模块 | 原型验证状态 | 验证手段 | 量产转化差距与需求 |
|:---|:---|:---|:---|
| **BLE 扫描突破 (0xFF 广播)** | **已验证 (PASS)** | 板载自检 + 串口广播报文核验 | 需固化到产品广播逻辑中 |
| **GATT 数据库 (DIS/BAS/HIDS)** | **已验证 (PASS)** | 25/25 自动化串口与自检通过 | 需适配物理电量采集 ADC |
| **翻页状态机 (40ms/80ms/5Hz)** | **已验证 (PASS)** | 自动化脚本时序与防抖断言 | 需接入物理按键中断/消抖 |
| **单键熄屏/唤醒 (Usage 0x66)** | **已验证 (PASS)** | 物理 Kindle 实机验证放行 | 需预留专用按键或长按手势 |
| **前光亮度调节 (Consumer)** | **已验证 (PASS)** | 复合 HID Report ID 2 注册 | 需设计物理长按或双键复用 |
| **串口命令行控制** | **已验证 (PASS)** | 115200 交互控制台正常 | 量产硬件需作为工厂烧录测试接口保留 |
| **物理按键电路** | **未实现 (预留接口)** | 仅有软件轮询代码，无实体按键板 | 需设计实体微动按键与硬件消抖 |
| **电池管理与充电** | **未实现 (软件模拟)** | 固件写死 `95%` | 需设计锂电池、TP4056/TP4057 充电与 ADC 分压 |
| **低功耗休眠 (Deep Sleep)** | **未实现** | 固件全速 240MHz 轮询运行 | 需实现 BLE 保持连接/低占空比广播/按键唤醒 |
| **外壳与机械结构** | **完全缺失** | 仅裸露开发板 | 需设计人体工学紧凑外壳、按键手感与 3D 打印件 |
| **PCB 原理图与生产文件** | **完全缺失** | 仅使用通用开发板 | 需设计完整 2 层量产 PCB、BOM 与制造包 |

---

## 4. 关键结论与指导原则
1. **代码复用原则**: 原型固件中的 `device_identity`、`hid_report`、`ble_hid` 核心逻辑完全契合 Kindle 5.20.1 协议逆向标准，**严禁重写**，量产固件只需补齐硬件按键驱动、真实 ADC 电量测量与低功耗电源管理。
2. **硬件重构原则**: 开发板方案（ESP32-S3 原型）存在体积庞大、成本高昂（模组+PSRAM+CH343 > 25 RMB）、功耗过高的缺点，必须在后续阶段进行严谨的 MCU 选型论证与低功耗紧凑外围设计。
