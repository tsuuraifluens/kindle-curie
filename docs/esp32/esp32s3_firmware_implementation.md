# ESP32-S3 BLE HID 翻页器固件实现规格与逆向工程证据映射

**目标设备**：Kindle Paperwhite 2024 (第 12 代, Bellatrix 4, 固件 Sangria 5.20.1)  
**开发目标芯片**：ESP32-S3 (Xtensa Dual-Core 240MHz, BLE 5.0 HOGP, 16MB Flash, 8MB PSRAM)  
**模块路径**：`esp32s3_firmware/`  
**工具链**：官方 ESP-IDF v5.3.2 + NimBLE Bluetooth Host  

---

## 1. 架构总览与设计哲学

本固件不是通用的蓝牙玩具键盘，也不是简单的 `KEY_VOLUMEUP`/`KEY_VOLUMEDOWN` 媒体控制器，而是专门依据 Kindle 5.20.1 固件逆向成果构建的 **高保真 Kindle 专用 BLE HID 翻页器（HOGP Keyboard）固件**。

### 1.1 系统架构图

```
 +-----------------------------------------------------------------------+
 |                         ESP32-S3 Firmware                             |
 |                                                                       |
 |   +---------------------------------------------------------------+   |
 |   |                      Interactive CLI                          |   |
 |   |   (help, status, info, logs, ble_start, ble_stop, prev,       |   |
 |   |    next, key, battery, name, vidpid, adv_mode, gpio, selftest)|   |
 |   +---------------------------------------------------------------+   |
 |              |                                          |             |
 |              v                                          v             |
 |   +-----------------------+                  +--------------------+   |
 |   |  HID State Machine    |                  |  Device Identity   |   |
 |   |  - 40ms hold          |                  |  - VID: 0x0171     |   |
 |   |  - 80ms debounce      |                  |  - PID: 0x0443     |   |
 |   |  - 5/s rate limiter   |                  |  - PnP ID (0x2A50) |   |
 |   |  - Auto key release   |                  |  - ADV 0xFF / 0x10 |   |
 |   +-----------------------+                  +--------------------+   |
 |              |                                          |             |
 |              v                                          v             |
 |   +---------------------------------------------------------------+   |
 |   |                  NimBLE BLE Stack & Security                  |   |
 |   |   - GAP: "Kindle Remote", Appearance: 0x03C1                  |   |
 |   |   - SMP: Just Works + LE Secure Connections + NVS Bonding     |   |
 |   |   - DIS (0x180A): PnP ID, FW Rev, Model, Serial, Mfg, HW/SW  |   |
 |   |   - BAS (0x180F): Battery Level (95%), Read + Notify (0x2902) |   |
 |   |   - HIDS(0x1812): Info, 65B Report Map, Input/Output Reports  |   |
 |   +---------------------------------------------------------------+   |
 |              |                                          |             |
 |              v                                          v             |
 |   +-----------------------+                  +--------------------+   |
 |   | Diagnostics & Logger  |                  | Hardware Buttons   |   |
 |   | - Ring buffer (64)    |                  | - Optional GPIO    |   |
 |   | - Peer read counters  |                  | - Active-low + PU  |   |
 |   +-----------------------+                  +--------------------+   |
 +-----------------------------------------------------------------------+
                                    |  (BLE RF)
                                    v
 +-----------------------------------------------------------------------+
 |                     Kindle Paperwhite 2024 (5.20.1)                   |
 |                                                                       |
 |  [Stage 0: BLE Scan]   libbluetooth.so (0x0ad270..0x0ad60c)           |
 |                        Checks AD 0xFF [71 01 04 43] -> btif_in_is_... |
 |                        acsbtfd BtDiscoveryFilter::Classify (0x22574)  |
 |                        Matches VID 0x0171 & PID 0x0443 -> PageTurner! |
 |                                                                       |
 |  [Stage 1: GATT Auth]  acsbtfd BtGattClient::ReadChar(0x180A, 0x2A26) |
 |                        Probes Firmware Revision String ("1.0.0")      |
 |                        Reads 0x180A 0x2A50 PnP ID -> uhid / input_mgr |
 |                                                                       |
 |  [Stage 2: Input Flow] Linux hid-input: Usage 0x4B/0x4E               |
 |                        -> KEY_PAGEUP (104) / KEY_PAGEDOWN (109)       |
 |                        -> input_manager KeyMapper -> "PageUp"/"Down"  |
 |                        -> Booklet UI Page Turn!                       |
 +-----------------------------------------------------------------------+
```

---

## 2. 逆向工程证据映射（Evidence Traceability Matrix）

| 模块 / 特征 | 固件参数 / 行为 | 逆向证据编号 | 证据源与说明 |
|:---|:---|:---|:---|
| **PnP ID (`0x2A50`)** | `02 71 01 43 04 00 01` (VID Source `0x02`, VID `0x0171`, PID `0x0443`, Ver `0x0100`) | **EVID-03**, **EVID-04** | `uhid` 注入驱动与 `input_manager` 白名单强制比对；`devices.json` 官方注册条目。 |
| **设备白名单与命名** | Name: `"Kindle Remote"`, VID `0x0171`, PID `0x0443` | **EVID-01**, **EVID-02** | `devices.json` 第 34 行注册为 `"Kindle Remote"`，VID `0x0171` (Lab126/Amazon), PID `0x0443`。 |
| **扫描阶段发现过滤 (Discovery Filter)** | 广播携带 AD Type `0xFF`: `71 01 04 43` 或 AD Type `0x10`: `02 00 71 01 43 04 00 01` | **EVID-12**, **EVID-13** | **最新重大突破**：`acsbtfd` `BtDiscoveryFilter::Classify` (`0x22574`) 在未连接前仅过滤 VP ID，不匹配 `0x1812`；`libbluetooth.so` (`0x0ad270`) 从 `0xFF` 广播数据中解析 `0x0171` 与大端 `0x0443`，经 `btif_in_is_amazon_ble_remote` (`0x0f89f8`) 验证后暴露给上层！ |
| **固件版本特征探测** | DIS Char `0x2A26` 读操作返回 `"1.0.0"` | **EVID-09** | `acsbtfd` 反汇编证明其直接调用 `BtGattClient::ReadChar(0x180A, 0x2A26)` 主动嗅探固件版本字符串。 |
| **电量监控与门限** | BAS (`0x180F`) / Char `0x2A19`，默认 95%，支持 Read & Notify | **EVID-07**, **EVID-10** | `acsbtfd` `BleHidPageTurnerPolicy` 虚表确认：电量 `<=20%` 触发 `pageTurnerBatteryLow`，`<=5%` 触发 `pageTurnerBatteryCritical`。 |
| **HID 描述符与映射** | 65 字节标准键盘报告图，Report ID 1，Usage Page `0x07` | **EVID-02**, **EVID-05** | `input_manager` Thumb-2 反汇编 `KeyMapper` 函数，完全依赖标准键盘 Usage Array。 |
| **上一页键码** | Usage `0x4B` (`75`) -> Linux `KEY_PAGEUP` (`104`) | **EVID-02**, **EVID-06** | 内核 `hid-input.c` 将 `0x4B` 转为 `KEY_PAGEUP`，`input_manager` 映射为 `"PageUp"` 动作。 |
| **下一页键码** | Usage `0x4E` (`78`) -> Linux `KEY_PAGEDOWN` (`109`) | **EVID-02**, **EVID-06** | 内核 `hid-input.c` 将 `0x4E` 转为 `KEY_PAGEUP`，`input_manager` 映射为 `"PageDown"` 动作。 |

---

## 3. 固件模块实现细节

### 3.1 模块 1：`device_identity` (设备身份管理)
- **文件**：[`device_identity.h`](../../esp32s3_firmware/main/device_identity.h), [`device_identity.c`](../../esp32s3_firmware/main/device_identity.c)
- **核心数据结构**：
  - `device_identity_config_t`：集中保存广播名称、PnP ID 7 字节编码、固件版本字符串、硬件版本、软件版本、序列号等。
  - `ble_adv_identity_mode_t`：
    - `BLE_ADV_MODE_AMAZON_MANUF_0XFF` (0): 默认广播带 AD Type `0xFF` (`71 01 04 43`)，专为绕过 Kindle `BtDiscoveryFilter::Classify` 设计。
    - `BLE_ADV_MODE_EIR_DEVID_0X10` (1): 在 Scan Response 中补充 AD Type `0x10` (8 字节 PnP ID)。
    - `BLE_ADV_MODE_GENERIC_HID_ONLY` (2): 仅发送普通 BLE HID 广播（用于 A/B 对比测试）。
- **函数功能**：
  - `device_identity_get_pnp_id_bytes(uint8_t out[7])`：输出 `[02, 71, 01, 43, 04, 00, 01]`。
  - `device_identity_get_manuf_adv_bytes(uint8_t out[4])`：输出 `[71, 01, 04, 43]`。
  - `device_identity_print_info()`：以格式化表格形式输出所有字段的取值、证据编号与推导级别。

### 3.2 模块 2：`hid_report` (HID 报告状态机与键码编解码)
- **文件**：[`hid_report.h`](../../esp32s3_firmware/main/hid_report.h), [`hid_report.c`](../../esp32s3_firmware/main/hid_report.c)
- **状态机设计**：
  - `HID_REPORT_STATE_IDLE` -> 用户触发 `PREV`/`NEXT`。
  - `HID_REPORT_STATE_PRESSED`：生成 8 字节按下帧，发送 GATT Notification，启动 `esp_timer` 40ms 单次定时器。
  - `HID_REPORT_STATE_COOLDOWN`：40ms 定时器超时，自动发送 8 字节释放帧（全零），进入 40ms 冷却（合计防抖周期 80ms）。
  - 超时后恢复 `IDLE`。
- **保护机制**：
  - **防卡键保护**：无论断连、发生异常还是连续触发，释放帧均受硬件定时器担保，断开连接时立即重置状态机为 `IDLE`。
  - **防抖保护**：两次触发间隔必须 `>= 80 ms`。
  - **速率限制**：采用滑动窗口 / 令牌桶算法，1000ms 内至多允许 5 次翻页操作，防止快速连击导致 Kindle 墨水屏刷新崩溃。

### 3.3 模块 3：`ble_hid` (NimBLE 协议栈与 GATT 服务)
- **文件**：[`ble_hid.h`](../../esp32s3_firmware/main/ble_hid.h), [`ble_hid.c`](../../esp32s3_firmware/main/ble_hid.c)
- **GATT 架构**：
  - 注册 DIS (`0x180A`)、BAS (`0x180F`)、HIDS (`0x1812`) 3 大主服务。
  - **可观测回调**：所有特征读/写均挂载自定义 `access_cb`，精确统计对端读取次数（`dis_pnp_id_reads`、`dis_fw_rev_reads`、`hids_report_map_reads` 等）。
  - **CCCD 订阅跟踪**：捕获 `BLE_GAP_EVENT_SUBSCRIBE` 事件，精确区分对端是否已使能 Input Report (`0x2A4D`) 与 Battery Level (`0x2A19`) 的通知。
  - **安全与配对**：配置 SMP 为 `Just Works`，使能 LE Secure Connections，支持 NVS 密钥持久化与 `BLE_GAP_EVENT_REPEAT_PAIRING`（对端重新配对时自动更替旧秘钥）。

### 3.4 模块 4：`serial_console` (非阻塞 UART0 控制台)
- **文件**：[`serial_console.h`](../../esp32s3_firmware/main/serial_console.h), [`serial_console.c`](../../esp32s3_firmware/main/serial_console.c)
- **健壮性保障**：
  - 运行于独立 FreeRTOS 任务，使用 `uart_read_bytes`，绝对不阻塞蓝牙中断与喂狗。
  - 严谨处理 `CR`、`LF`、`CRLF` 跨平台换行符，过滤控制字符。
  - 缓冲区溢出保护：行长限制 128 字符，超长时丢弃多余字符并安全报警，永不崩溃。
  - 敏感命令保护：`bond_clear` 必须附加 `confirm` 参数才执行，防止误抹除绑定。

### 3.5 模块 5：`diagnostics` (遥测与事件环形缓冲区)
- **文件**：[`diagnostics.h`](../../esp32s3_firmware/main/diagnostics.h), [`diagnostics.c`](../../esp32s3_firmware/main/diagnostics.c)
- **核心功能**：
  - 维护容量为 64 条的轻量级环形事件缓冲区，记录带毫秒时间戳的事件（`SYS`、`ID`、`HID`、`BLE`、`GAP`、`GATT`、`SMP`、`BTN`、`CLI`）。
  - 区分 **[1] 本地配置**、**[2] 对端读取状态 (Peer Verified)**、**[3] 翻页动作统计** 3 层状态输出。

### 3.6 模块 6：`self_test` (板载单元测试)
- **文件**：[`self_test.h`](../../esp32s3_firmware/main/self_test.h), [`self_test.c`](../../esp32s3_firmware/main/self_test.c)
- **覆盖测试**：
  1. `test_pnp_id_encoding`：验证 PnP ID 7 字节小端编码与 `EVID-03/04` 一致。
  2. `test_amazon_manuf_data_encoding`：验证 `71 01 04 43` 大小端混合编码与 `libbluetooth.so` 一致。
  3. `test_eir_devid_encoding`：验证 8 字节 EIR Device ID 编码。
  4. `test_report_map_structure`：验证 65 字节 Report Map 结构。
  5. `test_keyboard_report_encoding`：验证 `PREV` (`0x4B`)、`NEXT` (`0x4E`) 与释放帧。
  6. `test_battery_bounds`：验证电量参数边界。
  7. `test_identity_validation`：验证名称与参数校验逻辑。

---

## 4. 关键事实与设计边界

> [!IMPORTANT]
> **真实测试与理论兼容的界限**：
> 1. 本固件在 ESP32-S3 实机上完全实现了逆向分析所确认的全部服务、特征、广播及键码映射。
> 2. `status` 命令输出中的 `[PEER READ VERIFIED]` 字段仅在连接对端（Kindle 或测试主机）实际发起 GATT Read 请求时才会递增。未被对端读取时，状态明确显示为 `[NOT YET READ BY PEER]`，绝不伪造测试通过标志。
> 3. 实机初次配对测试依赖用户将 Kindle 翻页器配对页面开启并触发扫描。

---

## 5. 实机验证重大发现与优化演进 (Kindle 12th Gen, FW 5.20.1)

### 5.1 复合 HID 架构 (Composite HID Device)
* **Report ID 1 (Keyboard, 67 字节)**：负责 `KEY_PAGEUP` (`0x4B`)、`KEY_PAGEDOWN` (`0x4E`) 和 `KEY_POWER` (`0x66`)。
* **Report ID 2 (Consumer Control, 25 字节)**：负责前光亮度调增 Usage `0x006F` (`KEY_BRIGHTNESSUP`) 与调减 Usage `0x0070` (`KEY_BRIGHTNESSDOWN`)。
* 独立注册 Report ID 2 的 Consumer Report 特征（`0x2A4D`，handle=51）及其 CCCD，满足 Linux 内核 `hid-input.c` 和 `input_manager` 严格白名单要求。

### 5.2 原生单键 Usage 0x66 熄屏验证
* **实测事实**：用户在物理 Kindle Paperwhite 12 代实机上验证确认，Kindle 底层 `input_manager` 白名单原生放行 `KEY_POWER` (116)。设备只需发送标准单键 Usage `0x66` 报文，Kindle 会立即执行熄屏/休眠与唤醒。
* **架构优化**：双键组合 (`0x4B` + `0x4E`) 极为繁琐且不必要。固件将 `sleep` 与 `power` 操作彻底精简为单键原子 Usage `0x66` 按下/释放流程，极大降低了蓝牙重传、防抖时序竞争与翻页冲突风险。
* **单元测试**：板载 8 项单元测试保持 100% 通过（8/8 ALL PASSED）。
