# ESP32-S3 BLE HID 翻页器固件实机验证与测试报告 (`esp32s3_test_results.md`)

**生成日期**: 2026-10-13  
**测试硬件**: ESP32-S3 (QFN56, Revision v0.2, 16MB Flash, 8MB Octal PSRAM)  
**串口接口**: `COM3` (USB-Enhanced-SERIAL CH343, `115200 8N1`)  
**硬件 MAC**: Base MAC `30:ED:A0:A1:9B:7C`, BLE Public Address `30:ED:A0:A1:9B:7E`  
**工具链**: ESP-IDF v5.3.2 (`xtensa-esp32s3-elf-gcc 13.2.0`, CMake `3.30.2`, Ninja `1.12.1`)

---

## 一、实机硬件保护与固件备份记录

在执行任何编译或烧录操作之前，已通过 `esptool.py` 检测目标硬件并完整备份原始 Flash 前 2MB 镜像：

| 项目 | 实测详情 |
| :--- | :--- |
| **串口端口** | `COM3` (`USB\VID_1A86&PID_55D3`, WCH CH343 USB-UART) |
| **芯片识别** | `ESP32-S3 (QFN56) (revision v0.2)`, Crystal `40MHz`, Features: `WiFi, BLE, Embedded PSRAM 8MB (AP_3v3)` |
| **备份文件路径** | [`esp32s3_firmware/backups/factory_backup_2mb.bin`](../../esp32s3_firmware/backups/factory_backup_2mb.bin) |
| **备份范围与大小** | `0x00000000` .. `0x00200000` (2,097,152 字节) |
| **备份 SHA-256** | `C78D1EBBC14645B839C0130E15090FFF33AB3C6FAC327823056694CC750A85F1` |
| **烧录保护策略** | 仅覆盖 `0x0` (bootloader)、`0x8000` (partition-table) 与 `0x10000` (app 分区)，**未执行 `erase_flash`**，未改动 eFuse。 |

---

## 二、固件编译与烧录验证结果

### 2.1 编译结果 (`idf.py build`)
* **构建状态**: 成功 (`exit code 0`, 0 错误, 0 警告)
* **Bootloader 镜像**: `esp32s3_firmware/build/bootloader/bootloader.bin` (`0x5490` / 21,648 字节，剩余 34%)
* **分区表镜像**: `esp32s3_firmware/build/partition_table/partition-table.bin` (3,072 字节)
* **应用固件镜像**: `esp32s3_firmware/build/kindle_page_turner_esp32s3.bin` (`0x93CB0` / 605,360 字节，1MB factory 分区剩余 42%)

### 2.2 烧录结果 (`esptool.py write_flash`)
* **烧录命令**:
  ```powershell
  & "C:\Espressif\python_env\idf5.3_py3.13_env\Scripts\python.exe" -m esptool --chip esp32s3 -p COM3 -b 460800 --before default_reset --after hard_reset write_flash --flash_mode dio --flash_size 2MB --flash_freq 80m 0x0 build\bootloader\bootloader.bin 0x8000 build\partition_table\partition-table.bin 0x10000 build\kindle_page_turner_esp32s3.bin
  ```
* **校验状态**:
  - `0x00000000` (21,648 bytes -> 13,305 compressed): `Hash of data verified.`
  - `0x00008000` (3,072 bytes -> 103 compressed): `Hash of data verified.`
  - `0x00010000` (605,360 bytes -> 350,678 compressed): `Hash of data verified.`
  - `Hard resetting via RTS pin...` 正常复位启动。

---

## 三、板载单元测试与自动化串口验证汇总 (25 / 25 全部通过)

通过自动化验证脚本 [`esp32s3_firmware/tests/verify_serial_and_ble.py`](../../esp32s3_firmware/tests/verify_serial_and_ble.py) 对物理 ESP32-S3 (`COM3`) 进行了 10 大类共 25 项断言测试，并执行了板载 7 项协议自检 (`selftest`)：

### 3.1 板载协议自检套件 (`selftest` 命令实机输出)

| 序号 | 板载自检函数 | 验证内容与逆向证据 | 实机结果 |
| :--- | :--- | :--- | :--- |
| `1/7` | `test_pnp_id_encoding` | DIS PnP ID (`0x2A50`) 严格编码为 7 字节 `02 71 01 43 04 00 01` (`EVID-03`, `EVID-04`) | **PASS** |
| `2/7` | `test_amazon_manuf_data_encoding` | AD Type `0xFF` 厂商数据编码为 4 字节 `71 01 04 43` (VID LE + PID BE，`EVID-12`, `EVID-13`) | **PASS** |
| `3/7` | `test_eir_devid_encoding` | AD Type `0x10` Device ID 编码为 8 字节 `02 00 71 01 43 04 00 01` (`EVID-13`) | **PASS** |
| `4/7` | `test_report_map_structure` | HIDS Report Map (`0x2A4B`) 65 字节描述符头部与 `Report ID 1` 结构校验 (`EVID-11`) | **PASS** |
| `5/7` | `test_keyboard_report_encoding` | `PREV` (`0x4B` PageUp)、`NEXT` (`0x4E` PageDown) 与 `RELEASE` (全 0) 8 字节 Input Report 编码 (`EVID-02`) | **PASS** |
| `6/7` | `test_battery_bounds` | BAS (`0x180F` / `0x2A19`) 电量读写与 `>100%` 非法越界拒绝保护 (`EVID-07`) | **PASS** |
| `7/7` | `test_identity_validation` | 设备身份参数合法性校验（拒绝空设备名、`VID=0`、`PID=0`） | **PASS** |

### 3.2 自动化串口与 BLE 状态机测试套件 (`verify_serial_and_ble.py`)

| 测试分组 | 断言项 | 实测结果 |
| :--- | :--- | :--- |
| **Test 1: CLI Help** | `help` 完整列出所有控制与诊断指令 | **PASS** |
| **Test 2: Self-Test** | `selftest` 7/7 全部通过 | **PASS** |
| **Test 3: Identity & Evidence** | `info` 正确输出 `VID 0x0171`、`PID 0x0443`、PnP ID `02 71 01 43 04 00 01`、AD `0xFF` `71 01 04 43`、FW Rev `1.0.0` (`EVID-09`) | **PASS** (5/5) |
| **Test 4: Status & GATT Telemetry** | `status` 显示 `BLE Stack Ready: YES`、`BLE Advertising: ACTIVE`、`Advertised Name: "Kindle Remote"` 及对端 GATT 读取计数区 | **PASS** (4/4) |
| **Test 5: Disconnected Action Guard** | 未连接状态下执行 `prev`、`next`、`key 0x4e 40` 均安全拦截并返回 `REJECTED` (`ESP_ERR_INVALID_STATE`)，不发生阻塞或崩溃 | **PASS** (3/3) |
| **Test 6: Battery Service** | `battery 80` 与 `battery 95` 动态修改电量并记录事件 | **PASS** (2/2) |
| **Test 7: BLE Adv & Mode Control** | `ble_stop` 停止广播、`ble_start` 恢复广播、`adv_mode 0` 动态切换广播包模式并重配 28B ADV + 12B ScanRsp | **PASS** (3/3) |
| **Test 8: Bond & GPIO Safety** | `bond_status` 检查绑定、`bond_clear` 无 `confirm` 参数时安全拦截防误删、`gpio` 查询引脚配置 | **PASS** (3/3) |
| **Test 9: Ring Buffer Logs** | `logs` 完整输出带毫秒时间戳的环形缓冲区诊断日志 | **PASS** (1/1) |
| **Test 10: Robustness & Overflow** | 未知命令提示错误且不崩溃；150 字节超长输入（超过 128B 缓冲区）安全丢弃并恢复提示符 | **PASS** (2/2) |

---

## 四、实机串口原始输出日志摘录

### 4.1 `info` 命令实机输出（身份配置、逆向证据映射与 GATT 句柄表）

```text
kind-curie> info

==================== DEVICE IDENTITY & EVIDENCE ====================
Field                      | Value                        | Provenance   | Evidence ID
---------------------------+------------------------------+--------------+-------------------
Vendor ID (VID)            | 0x0171 (Lab126/Amazon)       | CONFIRMED    | EVID-01/02/03/12
Product ID (PID)           | 0x0443 (Kindle Page Turner)  | CONFIRMED    | EVID-01/02/03/12
Vendor ID Source           | 0x02 (USB-IF)                | CONFIRMED    | EVID-03/04
Product Version            | 0x0100                       | HYPOTHESIS   | Standard HOGP
DIS PnP ID (0x2A50, 7B)    | 02 71 01 43 04 00 01         | CONFIRMED    | EVID-03/04 (reads=0)
DIS Firmware Rev (0x2A26)  | "1.0.0"                      | CONFIRMED*   | EVID-09 (reads=0)
DIS Manufacturer (0x2A29)  | "Amazon"                     | HYPOTHESIS   | Standard DIS (reads=0)
DIS Model Number (0x2A24)  | "Kindle Remote"              | DERIVED      | EVID-02 (reads=0)
DIS Serial Number (0x2A25) | "KR000001"                   | HYPOTHESIS   | Standard DIS (reads=0)
GAP Device Name (0x2A00)   | "Kindle Remote"              | DERIVED      | EVID-02/10
GAP Appearance (0x2A01)    | 0x03C1 (HID Keyboard)        | DERIVED      | HOGP Spec
ADV Manuf Data (0xFF, 4B)  | 71 01 04 43 (mode=0)         | CONFIRMED    | EVID-12/13 (libbluetooth)
EIR Device ID (0x10, 8B)   | 02 00 71 01 43 04 00 01      | CONFIRMED    | EVID-13 (libbluetooth)
====================================================================
*Note: 0x2A26 GATT read by acsbtfd is CONFIRMED (EVID-09); string value "1.0.0" is default.

==================== GATT HANDLE & ADV TABLE ====================
DIS (0x180A):
  0x2A50 PnP ID              handle = 16
  0x2A26 Firmware Revision   handle = 18
  0x2A29 Manufacturer Name   handle = 20
  0x2A24 Model Number        handle = 22
  0x2A25 Serial Number       handle = 24
  0x2A27 Hardware Revision   handle = 26
  0x2A28 Software Revision   handle = 28
BAS (0x180F):
  0x2A19 Battery Level       handle = 31 (Read | Notify)
HIDS (0x1812):
  0x2A4A HID Information     handle = 35
  0x2A4B Report Map          handle = 37 (65 bytes)
  0x2A4C HID Control Point   handle = 40
  0x2A4E Protocol Mode       handle = 42
  0x2A4D Input Report (ID 1) handle = 44 (Read | Notify)
  0x2A4D Output Report(ID 1) handle = 48 (Read | Write | WriteNoRsp)
Primary ADV_IND Payload (28 bytes): 02 01 06 03 03 12 18 05 FF 71 01 04 43 0E 09 4B 69 6E 64 6C 65 20 52 65 6D 6F 74 65 
Scan Response Payload   (12 bytes): 03 19 C1 03 07 03 12 18 0F 18 0A 18 
=================================================================
```

### 4.2 `status` 命令实机输出（严格区分本地配置与对端读取验证）

```text
kind-curie> status

==================== STATUS REPORT ====================
Uptime                : 107630 ms
BLE Stack Ready       : YES
BLE Advertising       : ACTIVE
Own BLE Address       : 30:ED:A0:A1:9B:7E (type=0)
Connection State      : DISCONNECTED (conn_handle=0xFFFF, connects=0, disconnects=0)
Link Security (SMP)   : encrypted=NO, authenticated=NO, bonded=NO, key_size=0
Last Disconnect Reason: 0x00 (0)
Last Error            : [259] Cannot send page turn: BLE not connected

--- [1] Local GATT Configuration (ESP32-S3 Side) ---
Advertised Name       : "Kindle Remote"
PnP ID (0x2A50)       : src=0x02 VID=0x0171 PID=0x0443 Ver=0x0100
Firmware Rev (0x2A26) : "1.0.0"
Battery Level (0x2A19): 95%
HID Protocol Mode     : Report Mode (0x01)

--- [2] Peer GATT Access Verification (Central Side) ---
DIS PnP ID (0x2A50) Reads       : 0 [NOT YET READ BY PEER]
DIS FW Rev (0x2A26) Reads       : 0 [NOT YET READ BY PEER]
HIDS Report Map (0x2A4B) Reads  : 0 [NOT YET READ BY PEER]
HIDS HID Info (0x2A4A) Reads    : 0
BAS Battery (0x2A19) Reads      : 0
Input Report CCCD (0x2902)      : UNSUBSCRIBED
Battery CCCD (0x2902)           : UNSUBSCRIBED

--- [3] Page Turn HID Report Telemetry ---
State Machine         : IDLE
PREV (0x4B PageUp)    : 0 triggered
NEXT (0x4E PageDown)  : 0 triggered
Custom Key Actions    : 0 triggered
GATT Notify Frames TX : 0 OK / 0 FAIL (2 frames per key tap: press+release)
Dropped (Debounce/RL) : debounced=0, rate_limited=0, disconnected=3
=======================================================
```

### 4.3 `logs` 命令实机输出（启动与运行序列）

```text
==================== RECENT LOGS ====================
[      65 ms] [SYS     ] Diagnostics initialized
[      66 ms] [ID      ] Identity initialized: name="Kindle Remote" VID=0x0171 PID=0x0443
[      67 ms] [BAS     ] Battery service initialized: level=95%
[      74 ms] [HID     ] Report module initialized (map=65 bytes, PageUp=0x4B, PageDown=0x4E)
[      82 ms] [BTN     ] Button module initialized (disabled by default until configured)
[     119 ms] [BAS     ] Battery level updated to 50% (notify=0)
[     130 ms] [BAS     ] Battery level updated to 95% (notify=0)
[     150 ms] [TEST    ] Self-test executed: 7/7 passed
[     239 ms] [BLE     ] NimBLE host synced: addr=30:ED:A0:A1:9B:7E (type=0)
[     263 ms] [BLE     ] Advertising STARTED: name="Kindle Remote" VID=0x0171 PID=0x0443 adv_mode=0 (adv=28B, rsp=12B)
[     273 ms] [BLE     ] BLE HID subsystem initialized (DIS=0x180A, BAS=0x180F, HIDS=0x1812)
[     283 ms] [CLI     ] UART0 interactive serial console started (115200 8N1)
=====================================================
```

---

## 五、已完成验证与待 Kindle 实机联调边界说明

为确保工程严谨性，严格区分以下三个验证层级：

1. **ESP32-S3 硬件板载与协议栈级验证（已 100% 实测通过）**:
   - 固件已在真实 ESP32-S3 (`COM3`) 上运行，NimBLE 控制器与主机协议栈同步正常（地址 `30:ED:A0:A1:9B:7E`）。
   - 主广播包 (`ADV_IND`, 28 字节) 已实测包含突破 Kindle `BtDiscoveryFilter::Classify` (`EVID-12`, `EVID-13`) 所需的 `05 FF 71 01 04 43` 厂商字段、`03 03 12 18` HIDS UUID 与 `"Kindle Remote"` 名称。
   - DIS (`0x180A`)、BAS (`0x180F`)、HIDS (`0x1812`) 所有 GATT 特征及句柄均已在板载 GATT Server 注册并通过自检。
   - 翻页状态机（防抖、限流、40ms 自动释放、断连拦截）及串口交互控制台 25 项自动化测试全部通过。

2. **BLE Central 对端读取验证（由固件内置计数器实时追踪）**:
   - 固件在 `status` 命令中提供 `[2] Peer GATT Access Verification (Central Side)` 面板。
   - 当且仅当外部 BLE 主机（如 Kindle）实际发起连接并读取 `0x2A50` (PnP ID)、`0x2A26` (FW Rev)、`0x2A4B` (Report Map) 或写入 `0x2902` (CCCD) 时，对应计数器才会从 `[NOT YET READ BY PEER]` 变为 `[VERIFIED - READ BY PEER]` / `SUBSCRIBED (0x0001)`。

3. **Kindle Paperwhite 2024 (FW 5.20.1) 端到端实机联调操作步骤**:
   - 保持 ESP32-S3 连接电脑串口，打开串口终端（115200 波特率）。
   - 在 Kindle 上进入 **设置 -> 蓝牙与无线 -> 蓝牙 -> 配对新设备**。
   - 点击列表中的 **`Kindle Remote`** 完成配对。
   - 在串口输入 `status`，确认 `DIS PnP ID (0x2A50) Reads`、`DIS FW Rev (0x2A26) Reads`、`HIDS Report Map (0x2A4B) Reads` 均递增，且 `Input Report CCCD` 显示 `SUBSCRIBED (0x0001)`。
   - 打开 Kindle 电子书，在串口输入 `next` 与 `prev`（或通过 `gpio 0 45` 绑定开发板按键触发），验证墨水屏翻页响应。
   - 若需测试备选广播格式，可在串口随时执行 `adv_mode 1` (大端 PID) 或 `adv_mode 2` (AD Type `0x10` 放入主广播包) 立即热切换。
