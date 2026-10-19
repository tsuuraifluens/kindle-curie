# 既有通信协议与数据交互规范 (Existing Protocol Specification)

**文档编号**: ENG-SPEC-PROTO-001  
**适用对象**: Kindle Paperwhite 2024 (第 12 代, 固件 Sangria 5.20.1) & PC 调试端  
**版本**: v1.0.0 (基于逆向工程事实与 ESP32-S3 实机固件)

---

## 1. 串口交互协议规范 (UART0 Serial CLI)

### 1.1 物理层参数
- **波特率**: 115200 bps
- **数据位**: 8
- **校验位**: None (无校验)
- **停止位**: 1
- **流控**: 无硬件流控 (RTS/CTS 悬空)
- **物理引脚**: 默认映射 UART0 TXD (IO43), RXD (IO44) 或通过内置 USB-Serial JTAG 转换。

### 1.2 报文格式与帧解析机制
- **输入字符集**: ASCII 文本命令，对大小写不敏感（命令名转为小写处理）。
- **行终止符**: 兼容 `\r` (CR), `\n` (LF) 或 `\r\n` (CRLF)。
- **行缓冲区保护**:
  - 最大单行命令长度: 128 字节 (`CONSOLE_CMD_MAX_LEN`)。
  - 超出长度保护: 自动丢弃超长字符，输出警报提示，不发生缓冲区溢出或崩溃。
- **命令行提示符**: 准备就绪时输出 `kind-curie> `。

### 1.3 核心指令集与返回响应矩阵

| 指令语法 | 参数格式 | 功能描述 | 预期响应示例 |
|:---|:---|:---|:---|
| `help` 或 `?` | 无 | 显示控制台指令帮助列表 | 打印完整帮助列表 |
| `status` | 无 | 查询 BLE 状态、对端 GATT 访问验证计数、按键统计 | 输出 3 段格式化状态报告（含 `[NOT YET READ BY PEER]`） |
| `info` | 无 | 查看当前设备身份信息、逆向证据来源及 GATT 句柄分配 | 输出带有 EVID 来源的表格及句柄明细 |
| `logs` | 无 | 导出最近 64 条环形缓冲区遥测事件日志 | 打印毫秒级带时间戳日志序列 |
| `prev` | 无 | 发送上一页按键（Usage 0x4B -> KEY_PAGEUP） | `CMD prev: SENT` 或 `CMD prev: REJECTED (not connected)` |
| `next` | 无 | 发送下一页按键（Usage 0x4E -> KEY_PAGEDOWN） | `CMD next: SENT` 或 `CMD next: REJECTED (not connected)` |
| `sleep` | 无 | 触发 Kindle 熄屏休眠（单键 Usage 0x66，70ms 保持） | `CMD sleep: SENT (Usage 0x66)` |
| `power` | 无 | 触发电源键（Usage 0x66） | `CMD power: SENT` |
| `bup` / `bright_up` | 无 | 增加屏幕前光亮度（Consumer Control 0x006F） | `CMD bright_up: SENT` |
| `bdown` / `bright_down` | 无 | 降低屏幕前光亮度（Consumer Control 0x0070） | `CMD bright_down: SENT` |
| `battery <0-100>` | 整型百分比 | 设定当前电池电量并向已订阅主机发送 Notification | `CMD battery 85%: OK` |
| `adv_mode <0\|1\|2>` | 0: 0xFF<br>1: 0xFF+0x10<br>2: Generic | 动态切换 BLE 广播包封装格式并热重启广播 | `CMD adv_mode 0: OK (Amazon Manuf 0xFF)` |
| `gpio <p1> <p2>` | 引脚号 (-1 禁用) | 动态分配物理翻页按键 GPIO (内部上拉低电平有效) | `CMD gpio PREV=4, NEXT=5: OK` |
| `bond_status` | 无 | 查询 NVS 存储的 BLE 配对安全信息 | 打印配对设备列表与 MAC 地址 |
| `bond_clear confirm` | 必须带 confirm | 清空所有配对记录（防误触双重确认机制） | `CMD bond_clear confirm: CLEARED` |
| `selftest` | 无 | 执行固件内嵌 7/7 单元测试套件 | `Result: 7 / 7 tests passed (ALL PASSED)` |
| `reboot` | 无 | 安全软复位系统 | 系统立即重启并打印 Banner |

---

## 2. BLE 广播协议规范 (GAP Advertising Protocol)

### 2.1 扫描突破机制 (Discovery Filter Bypass)
针对 Kindle `acsbtfd` 守护进程的 `BtDiscoveryFilter::Classify` 过滤策略，固件必须构造特定的广播数据载荷：

### 2.2 主广播包 (ADV_IND，长度 28 字节)
```text
02 01 06                     ; [Flags]: LE General Discoverable (0x02) + BR/EDR Not Supported (0x04)
03 03 12 18                  ; [16-bit Service UUIDs]: 0x1812 (Human Interface Device)
05 FF 71 01 04 43            ; [Manufacturer Specific Data]:
                             ;   Company ID (LE): 0x0171 (Lab126 / Amazon)
                             ;   Product ID (BE): 0x0443 (Kindle Page Turner Accessories)
0E 09 4B 69 6E 64 6C 65 20 52 65 6D 6F 74 65 ; [Complete Local Name]: "Kindle Remote" (13 字节)
```

### 2.3 扫描响应包 (SCAN_RSP，长度 12 字节)
```text
03 19 C1 03                  ; [Appearance]: 0x03C1 (HID Keyboard)
07 03 12 18 0F 18 0A 18      ; [Service UUID List]: 0x1812 (HIDS), 0x180F (BAS), 0x180A (DIS)
```

---

## 3. BLE GATT 数据库架构 (GATT Server Architecture)

### 3.1 设备信息服务 (DIS, `0x180A`)
| 特征 UUID | 特征名称 | 权限 | 取值 / 结构说明 | 逆向证据映射 |
|:---|:---|:---|:---|:---|
| `0x2A50` | PnP ID | Read | 7 字节: `02 71 01 43 04 00 01` (VendorID Source: USB-IF, VID: 0x0171, PID: 0x0443, Ver: 0x0100) | **EVID-03**, **EVID-04** (强制白名单核验) |
| `0x2A26` | Firmware Revision | Read | ASCII 字符串: `"1.0.0"` | **EVID-09** (`acsbtfd` 主动嗅探读取) |
| `0x2A29` | Manufacturer Name | Read | ASCII 字符串: `"Amazon"` | 标准 DIS 特征 |
| `0x2A24` | Model Number | Read | ASCII 字符串: `"Kindle Remote"` | **EVID-02** |
| `0x2A25` | Serial Number | Read | ASCII 字符串: `"KR000001"` | 工业设备唯一追踪序列号 |

### 3.2 电池服务 (BAS, `0x180F`)
| 特征 UUID | 特征名称 | 权限 | 取值 / 结构说明 | 逆向证据映射 |
|:---|:---|:---|:---|:---|
| `0x2A19` | Battery Level | Read, Notify | 1 字节 uint8 (0 ~ 100) | **EVID-07** (<=20% 报低电, <=5% 严重警告) |
| `0x2902` | Client Char Config | Read, Write | 2 字节 CCCD (bit 0 = 1 允许 Notification) | 主机订阅电量变化事件 |

### 3.3 人机接口设备服务 (HIDS, `0x1812`)
采用**复合设备结构 (Composite HID Device)**，满足 Linux 内核 `hid-input` 与 Kindle `input_manager`：
- **HID Information (`0x2A4A`)**: BCD HID `0x0111`, Country `0x00`, Flags `0x02` (Normally Connectable).
- **Control Point (`0x2A4C`)**: 支持 Suspend (0) 与 Exit Suspend (1).
- **Protocol Mode (`0x2A4E`)**: 默认 Report Mode (1).
- **Report Map (`0x2A4B`)**: 92 字节标准复合描述符：
  - **Report ID 1 (Keyboard, 67 字节)**:
    - 8 字节标准 6KRO 格式: `[Modifiers (1B)] [Reserved (1B)] [Key1] [Key2] [Key3] [Key4] [Key5] [Key6]`
    - 上一页按下: `[00 00 4B 00 00 00 00 00]` (Usage `0x4B` -> Linux `KEY_PAGEUP` 104)
    - 下一页按下: `[00 00 4E 00 00 00 00 00]` (Usage `0x4E` -> Linux `KEY_PAGEDOWN` 109)
    - 熄屏电源按下: `[00 00 66 00 00 00 00 00]` (Usage `0x66` -> Linux `KEY_POWER` 116)
    - 释放帧: `[00 00 00 00 00 00 00 00]` (全零)
  - **Report ID 2 (Consumer Control, 25 字节)**:
    - 2 字节小端 16 位 Usage 格式:
    - 亮度增加按下: `[6F 00]` (Usage `0x006F` -> Linux `KEY_BRIGHTNESSUP` 225)
    - 亮度减少按下: `[70 00]` (Usage `0x0070` -> Linux `KEY_BRIGHTNESSDOWN` 224)
    - 释放帧: `[00 00]`

---

## 4. 翻页时序与状态机保护参数 (Timing & Rate Limiting)
- **按键保持时间 (Key Hold Time)**: 
  - 普通翻页 (`PREV`/`NEXT`): **40 ms** (通过 `esp_timer` 硬件定时器自动担保发出全零释放帧)。
  - 电源熄屏 (`sleep`/`power`): **70 ms** (确保 Linux 内核 evdev 驱动捕获长击门限)。
- **硬件防抖与冷却周期 (Debounce & Cooldown)**: **80 ms** (40ms 保持 + 40ms 冷却)。
- **滑动窗口速率限制 (Token Bucket Rate Limiter)**: **5 次 / 秒** (1000ms 窗口至多 5 次按键)，彻底防止快速连击导致 Kindle E-ink 墨水屏控制器 (EPDC) 驱动拥塞或进程崩溃。
