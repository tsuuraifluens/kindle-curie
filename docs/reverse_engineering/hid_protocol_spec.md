# Kindle BLE HID 翻页器通信协议工程规范 (HID Protocol Specification)

## 1. 规范概述与适用范围

本文档基于对 Kindle Paperwhite 2024 (第 12 代, 固件 Sangria 5.20.1, 硬件平台 `mt8110_bellatrix4`) 原生固件二进制（`acsbtfd`, `btmanagerd`, `input_manager`, `libAccessories.so`）的逆向工程结论，详细定义与 Kindle 官方翻页器完全兼容的低功耗蓝牙 (BLE) 人机接口设备 (HOGP, HID over GATT Profile) 通信协议。

- **目标平台**: Kindle Paperwhite 12th Gen (2024) / Sangria FW 5.20.1
- **参考官方设备**: Amazon Kindle Remote (商业名称 Kindle Click, 内部代号 Juno/Bellatrix 翻页配件)
- **协议基准**: Bluetooth SIG HID over GATT Profile (HOGP) v1.0, Device Information Service (DIS) v1.1, Battery Service (BAS) v1.0

---

## 2. BLE GAP (通用访问规范) 与广播数据规范

### 2.1 GAP 角色与连接参数
- **GAP Role**: Peripheral (从机)
- **广播类型**: `ADV_IND` (可连接、无定向广播)
- **广播间隔 (Advertising Interval)**: 
  - 发现/配对模式: 20 ms ~ 50 ms (快速被 Kindle 扫描捕获)
  - 待机重连模式: 100 ms ~ 250 ms (兼顾功耗与重连速度)
- **从机期望连接参数 (Peripheral Preferred Connection Parameters - PPCP)**:
  - 最小连接间隔 (Minimum Connection Interval): 15 ms (12 units)
  - 最大连接间隔 (Maximum Connection Interval): 30 ms (24 units)
  - 从机延迟 (Slave Latency): 4 ~ 6
  - 监督超时 (Supervision Timeout): 3000 ms (300 units)

### 2.2 广播包数据结构 (Advertising Data - 31 字节内)

| 偏移 (Byte) | 长度 (Len) | AD Type | 字段名称 | 数值 (十六进制) | 规范含义说明 |
|:---|:---|:---|:---|:---|:---|
| 0x00 | 0x02 | 0x01 | Flags | `0x06` | LE General Discoverable Mode (`0x02`), BR/EDR Not Supported (`0x04`) |
| 0x03 | 0x03 | 0x19 | Appearance | `0xC1, 0x03` | Category: HID Keyboard (`0x03C1`, Little-Endian) |
| 0x07 | 0x07 | 0x03 | 16-bit Service UUIDs | `0x12, 0x18, 0x0F, 0x18, 0x0A, 0x18` | 完整声明 3 个核心服务：<br>• HID (`0x1812`)<br>• Battery (`0x180F`)<br>• Device Info (`0x180A`) |
| 0x10 | 0x0E | 0x09 | Complete Local Name | `4B 69 6E 64 6C 65 20 52 65 6D 6F 74 65` | ASCII 字符串: `"Kindle Remote"` (13 字节) |

> **广播包原始十六进制字节序列 (29 字节)**:
> `02 01 06 03 19 C1 03 07 03 12 18 0F 18 0A 18 0E 09 4B 69 6E 64 6C 65 20 52 65 6D 6F 74 65`

### 2.3 扫描响应数据结构 (Scan Response Data - 可选)

| 偏移 (Byte) | 长度 (Len) | AD Type | 字段名称 | 数值 (十六进制) | 规范含义说明 |
|:---|:---|:---|:---|:---|:---|
| 0x00 | 0x02 | 0x0A | Tx Power Level | `0x00` | 0 dBm 发射功率 |
| 0x03 | 0x09 | 0xFF | Manufacturer Specific | `0x71, 0x01, 0x01, ...` | Amazon 公司标识 (`0x0171`) 扩展数据 |

---

## 3. GATT 数据库架构 (Services & Characteristics)

设备必须向 GATT Server 暴露以下 5 个标准服务及特征集合：

```
GATT Database Hierarchy
├── 0x1800 Generic Access Service
│   ├── 0x2A00 Device Name (Read): "Kindle Remote"
│   └── 0x2A01 Appearance (Read): 0x03C1 (Keyboard)
├── 0x1801 Generic Attribute Service
│   └── 0x2A05 Service Changed (Indicate)
├── 0x180A Device Information Service (DIS) [CRITICAL]
│   ├── 0x2A29 Manufacturer Name String (Read): "Amazon"
│   ├── 0x2A24 Model Number String (Read): "Kindle Remote"
│   ├── 0x2A25 Serial Number String (Read): "G000WM0000000001"
│   ├── 0x2A26 Firmware Revision String (Read): "1.0.0" [Probed by acsbtfd]
│   ├── 0x2A27 Hardware Revision String (Read): "1.0"
│   ├── 0x2A28 Software Revision String (Read): "1.0.0"
│   └── 0x2A50 PnP ID (Read): [0x02, 0x71, 0x01, 0x43, 0x04, 0x00, 0x01] [WHITELIST KEY]
├── 0x180F Battery Service (BAS) [CRITICAL]
│   └── 0x2A19 Battery Level (Read, Notify): 0 ~ 100
│       └── 0x2902 Client Characteristic Configuration Descriptor (CCCD)
└── 0x1812 Human Interface Device Service (HIDS) [CRITICAL]
    ├── 0x2A4A HID Information (Read): [0x11, 0x01, 0x00, 0x02]
    ├── 0x2A4B Report Map (Read): Standard Keyboard Descriptor
    ├── 0x2A4D Report - Input (Read, Notify): Key Press / Release Array
    │   ├── 0x2902 CCCD
    │   └── 0x2908 Report Reference: [0x01, 0x01] (Report ID 1, Type Input)
    ├── 0x2A4C HID Control Point (Write Without Response): 0x00 / 0x01
    └── 0x2A4E Protocol Mode (Read, Write Without Response): 0x01 (Report Mode)
```

---

## 4. 关键服务特征详细定义

### 4.1 Device Information Service (`0x180A`) & PnP ID (`0x2A50`)

PnP ID 是通过 Kindle 白名单鉴权的**唯一最高优先级硬凭证**（见 `EVID-01`, `EVID-03`, `EVID-04`）。内核 Linux UHID 模块依据读取到的 PnP ID 生成 `struct input_id` 并由 `input_manager` 实施 `EVIOCGID` 比对。

#### PnP ID 数据包字段拆解 (7 字节)

| 字节偏移 | 字段名 | 长度 | 数值 | 字节序 | 详细说明 |
|:---|:---|:---|:---|:---|:---|
| Byte 0 | Vendor ID Source | 1 Byte | `0x02` | - | `0x02` 表示来源为 USB Implementer's Forum (USB-IF) |
| Byte 1..2 | Vendor ID (VID) | 2 Bytes | `0x0171` | Little-Endian: `0x71, 0x01` | Amazon.com, Inc. / Lab126 官方厂商标识 |
| Byte 3..4 | Product ID (PID) | 2 Bytes | `0x0443` | Little-Endian: `0x43, 0x04` | Kindle Remote / Click 专用硬件标识 |
| Byte 5..6 | Product Version | 2 Bytes | `0x0100` | Little-Endian: `0x00, 0x01` | 固件版本号 v1.0.0 (BCD) |

> **PnP ID 十六进制只读 Payload**:
> `02 71 01 43 04 00 01`

#### Firmware Revision String (`0x2A26`)
- **属性**: Read
- **内容**: UTF-8 字符串 `"1.0.0"` (或 `"01.00.00"`)
- **证据依据**: `acsbtfd` 在连接建立后主动调用 `BtGattClient::ReadChar(0x180A, 0x2A26)` 读取固件版本字符串（`EVID-09`）。若此特征缺失或返回错误，会导致 Kindle 在日志中标记异常。

---

### 4.2 Battery Service (`0x180F`)

Kindle 蓝牙服务 `acsbtfd` 内部维护 `BleHidPageTurnerPolicy`（`EVID-07`），通过轮询及通知机制监听外设电量。

- **特征 UUID**: `0x2A19` (Battery Level)
- **属性**: `Read | Notify`
- **数据格式**: 1 字节无符号整数 (`uint8_t`), 取值范围 `0 ~ 100` (%)
- **描述符**: 包含标准 CCCD (`0x2902`)，Kindle 在配对后会写入 `0x0001` 开启 Notification
- **Kindle 端告警门限**:
  - `pageTurnerBatteryLow`: 当电量 `<= 20%` (`0x14`) 时，触发系统电量低提示弹窗
  - `pageTurnerBatteryCritical`: 当电量 `<= 5%` (`0x05`) 时，触发严重低电停用保护

---

### 4.3 Human Interface Device Service (`0x1812`)

#### 4.3.1 HID Information (`0x2A4A`)
- **属性**: Read
- **长度**: 4 字节
- **Payload 结构**:
  - Byte 0..1: `bcdHID` = `0x0111` (HID 版本 1.11, Little-Endian: `0x11, 0x01`)
  - Byte 2: `bCountryCode` = `0x00` (非本地化硬件)
  - Byte 3: `Flags` = `0x02` (`NormallyConnectable` 标志位为 1，指示设备保持可连接)
- **十六进制数据**: `11 01 00 02`

#### 4.3.2 HID Control Point (`0x2A4C`)
- **属性**: Write Without Response
- **取值定义**:
  - `0x00`: Suspend (Kindle 系统休眠时可能下发)
  - `0x01`: Exit Suspend (唤醒)

#### 4.3.3 Protocol Mode (`0x2A4E`)
- **属性**: Read, Write Without Response
- **取值**: 始终返回 `0x01` (Report Mode)。

---

## 5. HID Report Descriptor (报告描述符) 深度规范

基于 Linux 内核 `hid-generic` 驱动解析规则与 Kindle `categories.json` 的按键过滤规范，推荐以下两种工业级标准 Report Map 方案。

### 方案 A：标准键盘 8 字节报告描述符 (推荐方案)

此方案模拟工业标准 101/104 键键盘，具备最广泛的 Linux 内核与 Android HOGP 兼容性。

#### Report Descriptor 十六进制序列 (63 字节)
```hex
05 01        // USAGE_PAGE (Generic Desktop)
09 06        // USAGE (Keyboard)
A1 01        // COLLECTION (Application)
85 01        //   REPORT_ID (1)
05 07        //   USAGE_PAGE (Keyboard/Keypad)
19 E0        //   USAGE_MINIMUM (Keyboard LeftControl)
29 E7        //   USAGE_MAXIMUM (Keyboard Right GUI)
15 00        //   LOGICAL_MINIMUM (0)
25 01        //   LOGICAL_MAXIMUM (1)
75 01        //   REPORT_SIZE (1)
95 08        //   REPORT_COUNT (8)
81 02        //   INPUT (Data,Var,Abs)           ; Modifier 字节 (8 bits)
95 01        //   REPORT_COUNT (1)
75 08        //   REPORT_SIZE (8)
81 03        //   INPUT (Cnst,Var,Abs)           ; 保留字节 (1 byte)
95 06        //   REPORT_COUNT (6)
75 08        //   REPORT_SIZE (8)
15 00        //   LOGICAL_MINIMUM (0)
25 65        //   LOGICAL_MAXIMUM (101)
05 07        //   USAGE_PAGE (Keyboard/Keypad)
19 00        //   USAGE_MINIMUM (Reserved)
29 65        //   USAGE_MAXIMUM (Keyboard Application)
81 00        //   INPUT (Data,Ary,Abs)           ; 按键数组 (6 bytes)
C0           // END_COLLECTION
```

#### 输入报告 (Input Report) 数据帧结构 (9 字节，含 Report ID)

| 偏移 (Byte) | 字段名 | 含义与取值 |
|:---|:---|:---|
| Byte 0 | Report ID | 固定为 `0x01` |
| Byte 1 | Modifier Keys | 组合键位图 (Ctrl, Shift, Alt, GUI)。平时固定为 `0x00` |
| Byte 2 | Reserved | 规范保留，固定为 `0x00` |
| Byte 3 | KeyCode 1 | 主按键 HID Usage 键码 (见下表) |
| Byte 4 | KeyCode 2 | `0x00` (未按下) |
| Byte 5 | KeyCode 3 | `0x00` |
| Byte 6 | KeyCode 4 | `0x00` |
| Byte 7 | KeyCode 5 | `0x00` |
| Byte 8 | KeyCode 6 | `0x00` |

---

### 方案 B：紧凑型专有翻页报告描述符 (无 Report ID / 3 字节)

若为了减少 BLE 空口吞吐开销，可使用无 Report ID 的专用 3 字节报告描述符：

```hex
05 01        // USAGE_PAGE (Generic Desktop)
09 06        // USAGE (Keyboard)
A1 01        // COLLECTION (Application)
05 07        //   USAGE_PAGE (Keyboard/Keypad)
19 E0        //   USAGE_MINIMUM (224)
29 E7        //   USAGE_MAXIMUM (231)
15 00        //   LOGICAL_MINIMUM (0)
25 01        //   LOGICAL_MAXIMUM (1)
75 01        //   REPORT_SIZE (1)
95 08        //   REPORT_COUNT (8)
81 02        //   INPUT (Data,Var,Abs)           ; Modifiers (1 Byte)
05 07        //   USAGE_PAGE (Keyboard/Keypad)
19 00        //   USAGE_MINIMUM (0)
2A FF 00     //   USAGE_MAXIMUM (255)
15 00        //   LOGICAL_MINIMUM (0)
26 FF 00     //   LOGICAL_MAXIMUM (255)
75 08        //   REPORT_SIZE (8)
95 01        //   REPORT_COUNT (1)
81 00        //   INPUT (Data,Ary,Abs)           ; 单键键码 (1 Byte)
C0           // END_COLLECTION
```

---

## 6. 按键 Usage 与发送时序规范

### 6.1 核心按键数据包定义 (基于方案 A 带 Report ID 1)

| 物理动作 | 语义动作 | HID Usage Page | HID Usage ID | Linux evdev 映射 | GATT Notification Payload (十六进制) |
|:---|:---|:---|:---|:---|:---|
| **下一页 (下按键)** | `nav.page_forward` | `0x07` (Keyboard) | `0x4E` (PageDown) | `KEY_PAGEDOWN` (109) | `01 00 00 4E 00 00 00 00 00` |
| **上一页 (上按键)** | `nav.page_back` | `0x07` (Keyboard) | `0x4B` (PageUp) | `KEY_PAGEUP` (104) | `01 00 00 4B 00 00 00 00 00` |
| **亮度增 (备用)** | `ui.bright_up` | `0x0C` / `0x07` | `0x006F` (BrightUp) | `KEY_BRIGHTNESSUP` (225) | `02 00 00 6F 00 00` |
| **亮度减 (备用)** | `ui.bright_down` | `0x0C` / `0x07` | `0x0070` (BrightDown)| `KEY_BRIGHTNESSDOWN` (224)| `02 00 00 70 00 00` |

### 6.2 按键发送时序图与防抖约束

```
外设按键按下                外设按键释放
     │                           │
     ▼                           ▼
┌───────────┐               ┌───────────┐
│ 发送 Press│               │ 发送 Rel  │
│  Payload  │               │  Payload  │
└─────┬─────┘               └─────┬─────┘
      │                           │
      ├────── 持锁时间 T_hold ────┤
      │                           │
      ▼ (GATT Notify)             ▼ (GATT Notify)
[Kindle input_manager 接收]
      │
      ├── 防抖检测: 与上一按键间隔 >= 50ms (debounce_window_ms)
      ├── 限流检测: 突发 <= 10 次，频率 <= 100 Hz (rate_limit)
      └── 转发至 /dev/input/uinput (kindle-input-pt)
```

- **推荐持锁时间 (Hold Duration)**: `30 ms <= T_hold <= 80 ms`。
  - 若 `T_hold < 20 ms`：可能因 Linux 内核调度延迟丢失释放事件，导致键位粘滞。
  - 若 `T_hold > 500 ms`：可能触发系统长按重复事件（Auto-Repeat），引发连续翻页。
- **推荐连击间隔 (Repeat Interval)**: `>= 80 ms`（确保高于 Kindle `input_manager` 的 50 ms 硬件防抖窗口）。

---

## 7. SMP (安全管理器) 与配对绑定规范

Kindle 使用基于 Android Fluoride 架构的 `btmanagerd` 协议栈，对 HOGP 外设强制执行 LE Security 绑定。

1. **安全级别 (Security Level)**:
   - **LE Security Mode 1, Level 2** (未通过认证的加密连接，即 **Just Works** 配对模式)。
   - 或 **LE Security Mode 1, Level 3** (带 Passkey 的已认证加密连接，官方设备通常采用无需屏幕交互的 Just Works)。
2. **IO Capabilities (输入输出能力)**:
   - 声明为 `NoInputNoOutput` (`0x03`)。
3. **绑定标志位 (AuthReq)**:
   - `Bonding` 标志位置 1 (`0x01`)。
   - `MITM` (中间人保护) 置 0。
   - `Secure Connections (SC)` 支持但不强制。
4. **密钥分发 (Key Distribution)**:
   - `EncKey` (LTK 长期密钥): 必须由从机生成并交换。
   - `IdKey` (IRK 身份解析密钥): 支持静态随机地址解析。
5. **重连加密规范**:
   - 一旦配对完成，后续重连时从机必须请求启动加密链路 (`Security Request` / `LL_ENC_REQ`)。在加密链路建立前，HIDS 报告特征的读取与通知必须受属性权限保护。
