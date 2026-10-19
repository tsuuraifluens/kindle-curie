# ESP32-S3 已知可行固件功能基准报告 (ESP32-S3 Reference Benchmark)

**报告编号**: EVID-REF-ESP32-001  
**参考工程**: `kind-curie` 现存 ESP32-S3 生产级固件 (ESP-IDF v5.3.2 + NimBLE)  
**验证状态**: **真实硬件 (COM3) 25/25 自动化用例全量通过 (PASS)**  
**测试报告**: 参见 [`docs/esp32/esp32s3_test_results.md`](./esp32s3_test_results.md) 与 [`release/01_ESP32_Hardware_Remote/README.md`](../../release/01_ESP32_Hardware_Remote/README.md)

---

## 1. 参考固件核心架构指标 (Core Parameters)

ESP32-S3 固件作为本次逆向与改造研究的**黄金基准 (Golden Reference)**，详尽记录了已被 Kindle 实测认可的全部外设协议特征：

| 协议层级 | 参数名称 | 参考实现数值 | 作用与 Kindle 对接要求 |
|:---|:---|:---|:---|
| **GAP 广播** | 设备广播名 (Local Name) | `"Kindle Remote"` (13 字节) | Kindle UI 规范显示名称 |
| **GAP 广播** | 外观特征 (Appearance) | `0x03C1` (HID Keyboard) | 辅助标识外设类别 |
| **GAP 广播** | **厂商自定义数据 (0xFF)** | **`0x05, 0xFF, 0x71, 0x01, 0x04, 0x43`** | **核心突破项**：直接命中 `acsbtfd` 扫描白名单 |
| **GATT DIS** | **PnP ID (`0x2A50`)** | `02 71 01 43 04 00 01` (7 字节) | **核心突破项**：命中 `devices.json`，VID=0x0171, PID=0x0443 |
| **GATT DIS** | 厂商名称 (`0x2A29`) | `"Amazon"` | 规范 DIS 特征 |
| **GATT DIS** | 固件版本 (`0x2A26`) | `"1.0.0"` | `acsbtfd` 主动嗅探读取字段 |
| **GATT BAS** | 电池电量 (`0x2A19`) | `95` (uint8, 0~100) | 支持 Read 与 Notify，Kindle 状态栏显示电量 |
| **GATT HIDS** | **报告描述符 (Report Map)**| **92 字节复合描述符** (键盘 + 消费类) | 满足 Linux 内核 `hid-input` 规范 |
| **HID 键盘** | **上一页 (PREV)** | **Report ID 1, Usage `0x4B` (`KEY_PAGEUP`)** | 触发 Kindle `"reader.page_up"` 动作 |
| **HID 键盘** | **下一页 (NEXT)** | **Report ID 1, Usage `0x4E` (`KEY_PAGEDOWN`)** | 触发 Kindle `"reader.page_down"` 动作 |
| **HID 键盘** | **息屏休眠 (SLEEP)** | **Report ID 1, Usage `0x66` (`KEY_POWER`)** | 70ms 脉冲保持，触发 `"system.sleep"` 动作 |
| **HID 消费类** | 前光调亮 (Bright Up) | Report ID 2, Usage `0x006F` (`KEY_BRIGHTNESSUP`)| 调节屏幕背光亮度增 |
| **HID 消费类** | 前光调暗 (Bright Down)| Report ID 2, Usage `0x0070` (`KEY_BRIGHTNESSDOWN`)| 调节屏幕背光亮度减 |

---

## 2. 报文格式与发送时序规范 (Timing & Packet Sequences)

### 2.1 键盘报文封装 (Report ID 1)
```text
[Byte 0]: Report ID (0x01)
[Byte 1]: Modifier (0x00)
[Byte 2]: Reserved (0x00)
[Byte 3]: KeyCode Slot 1 (HID Usage)
[Byte 4~8]: KeyCode Slots 2~6 (0x00)
```
- **上一页完整报文序列**:
  - 按下: `01 00 00 4B 00 00 00 00 00` (硬件持锁保持 40ms)
  - 释放: `01 00 00 00 00 00 00 00 00`
- **下一页完整报文序列**:
  - 按下: `01 00 00 4E 00 00 00 00 00` (硬件持锁保持 40ms)
  - 释放: `01 00 00 00 00 00 00 00 00`
- **单键息屏完整报文序列**:
  - 按下: `01 00 00 66 00 00 00 00 00` (硬件持锁保持 70ms)
  - 释放: `01 00 00 00 00 00 00 00 00` (释放后施加 450ms 冷却保护)

### 2.2 防抖与限流保护机制
1. **按键防抖**: 硬件定时器滤波，门限为 **80ms**（40ms 保持 + 40ms 冷却），完全过滤由于机械触点弹跳导致的重复翻页。
2. **限流保护**: 采用滑动窗口限流器，限制最大事件频率为 **5 次 / 秒**，与 Kindle E-ink 墨水屏 150~300ms 的物理刷新周期完全对齐，从物理层避免 Kindle EPDC 控制器拥塞崩溃。
3. **长期密钥与回连**: NVS 专区保存配对生成的 16 字节 LTK，Kindle 重启或外设开机可在 1 秒内无感瞬间重连。
