# 蓝牙模式、HID 描述符与按键逻辑深度逆向报告 (Bluetooth HID & Input Architecture)

**报告编号**: EVID-HID-IINE-001  
**分析对象**: `app.bin` (主固件应用二进制，q32s 架构)  
**分析时间**: 2026-10-14  
**分析技术**: 静态常量表解析、HID 报告描述符重构、按键映射反汇编与状态机建模  
**结论置信度**: **完全证实 (VERIFIED)**

---

## 1. 固件内嵌的多模式控制器系统 (Multi-Mode Profile Architecture)

良值迷你手柄固件设计了一套高度模块化的多功能模式切换框架（由 `apps/hid/modules/bt/ble_multi.c` 管理）。用户通过开机时的长按组合键激活不同的蓝牙配置文件：

| 模式标识名称 | 激活按键组合 | 蓝牙广播名 | 目标应用场景 | 默认传输协议 | 逆向代码锚点 |
|:---|:---|:---|:---|:---|:---|
| **`IINE_reader`** | **配对键 + 方向上** | `IINE-reader` | **阅星瞳/安卓墨水屏阅读器** | **BLE HOGP (`0x1812`)** | 字符串 `0x211C6`, 逻辑 `0x7130` |
| `Xbox Wireless Controller` | 配对键 + A 键 | `Xbox Wireless Controller` | PC / Steam / 手机游戏 | EDR 经典蓝牙 (HID) | 字符串 `0x211D2`, 逻辑 `0x709C` |
| `IINE_keyboard` | 配对键 + 方向左 | `IINE-keyboard` | 办公打字 / 快捷键遥控 | BLE HOGP (Keyboard) | 字符串 `0x211EB`, 逻辑 `0x7196` |
| `IINE_mouse` | 配对键 + 方向右 | `IINE-mouse` | 电脑/电视空中飞鼠 | BLE HOGP (Mouse) | 字符串 `0x211F9`, 逻辑 `0x7128` |
| `IINE_phone` | 配对键 + 方向下 | `IINE_phone` | 抖音短视频/自拍翻页 | BLE HOGP (Consumer/Touch) | 字符串 `0x21204`, 逻辑 `0x719E` |
| `IINE_control` | 配对键 + B 键 | `IINE_control` | Switch / DInput 游戏模式 | 经典蓝牙 / USB HID | 字符串 `0x2120F`, 逻辑 `0x7186` |

---

## 2. 固件内嵌 HID 报告描述符深度解析 (HID Report Descriptors)

固件中固化了三套复合 HID 描述符，位于 `app.bin` 的常量数据段（`0x22400` ~ `0x22900`）：

### 2.1 描述符一：多功能复合桌面外设描述符 (偏移 `0x2260A`)
包含键盘、鼠标、消费类控制与触控板的统一集合：

```text
1. 键盘报告 (Report ID 0x02, 8 字节输入):
   [Byte 0]: Modifier 掩码位 (Ctrl, Shift, Alt, GUI)
   [Byte 1]: 保留位 (0x00)
   [Byte 2~7]: 6 键无冲键码槽 (Usage Page 0x07)
   
2. 鼠标报告 (Report ID 0x04, 5 字节输入):
   [Byte 0]: 5 个鼠标按键位图 (左键、右键、中键等)
   [Byte 1~2]: X 轴相对位移 (16 位有符号数)
   [Byte 3~4]: Y 轴相对位移 (16 位有符号数)

3. 消费类控制 (Report ID 0x05, 4 字节输入):
   [Byte 0~1]: 消费类 Usage 槽 1 (Usage Page 0x0C)
   [Byte 2~3]: 消费类 Usage 槽 2

4. 触控笔/触控板 (Report ID 0x06, 7 字节输入):
   支持接触状态、压力、Tip Switch 以及 X/Y 绝对坐标。
```

### 2.2 描述符二：阅读器专用消费类按键与触控描述符 (偏移 `0x2280C`)
**此描述符为 `IINE_reader` 模式的核心描述符**：

```text
05 0C        Usage Page (Consumer Devices)
09 01        Usage (Consumer Control)
A1 01        Collection (Application)
85 01        Report ID (1)
09 30        Usage (Power)
09 40        Usage (Menu)
09 82        Usage (Sleep Mode)
09 A0        Usage (VCR Plus)
09 B5        Usage (Scan Next Track / 快进)
09 B6        Usage (Scan Previous Track / 快退)
09 E9        Usage (Volume Increment / 音量+)
09 EA        Usage (Volume Decrement / 音量-)
09 E2        Usage (Mute / 静音)
09 CD        Usage (Play/Pause / 播放暂停)
0A 83 01     Usage (AL Consumer Control Configuration)
0A 96 01     Usage (AL Internet Browser)
0A 23 02     Usage (AC Home / 主页)
0A 24 02     Usage (AC Back / 返回)
0A 2D 02     Usage (AC Zoom In / 放大)
0A 2E 02     Usage (AC Zoom Out / 缩小)
15 00        Logical Minimum (0)
25 01        Logical Maximum (1)
75 01        Report Size (1)
95 10        Report Count (16)
81 02        Input (Data, Variable, Absolute)  ; 16 位位图，每位对应一个按键动作
C0           End Collection

05 0D 09 04  Usage Page (Digitizer), Usage (Touch Screen)
A1 01        Collection (Application)
85 02        Report ID (2)
... (触控点模拟，用于在阅读器界面模拟屏幕点击确认)
```

---

## 3. `IINE_reader` 模式按键映射与事件派发逆向 (Key Mapping Analysis)

通过对 `app.bin` 中按键派发关键函数 `0xE4D0` ~ `0xE850` 的反汇编，提取其核心代码逻辑：

```arm
; 位于 0xE7A0 ~ 0xE7BC 处的按键消费类重映射逻辑
0xE7AC: F582 00EA    mov r2, #0x00EA         ; r2 = Volume Decrement (音量-)
0xE7B0: F581 00E8    mov r1, #0x00E8 / 0xE9  ; r1 = Volume Increment (音量+)
0xE7B4: C0FF 2D14E201 mov r0, #0x01E2142D    ; r0 = "remap_key.consumer_key :%x %x\n"
0xE7BA: F380 A909    call printf              ; 打印当前重映射按键
```

### 实际物理按键到逻辑事件的映射表

| 物理按键 | 固件检测键值 | 派发协议 | 实际发射 Usage 键码 | 语义含义 | 设计初衷说明 |
|:---|:---|:---|:---|:---|:---|
| **方向键 左 / 上** | `KEY_LEFT` / `KEY_UP` | Consumer Report ID 1 | `Usage 0x00E9` | **Volume Up (音量+)** | 安卓阅读软件默认支持音量键翻页（上一页） |
| **方向键 右 / 下** | `KEY_RIGHT` / `KEY_DOWN` | Consumer Report ID 1 | `Usage 0x00EA` | **Volume Down (音量-)** | 安卓阅读软件默认支持音量键翻页（下一页） |
| **A 按键** | `KEY_A` | Digitizer Report ID 2 | 屏幕中心轻触 (Tap) | **确认 / 点击 (Enter)** | 在安卓界面中打开菜单或点击屏幕 |
| **B 按键** | `KEY_B` | Consumer Report ID 1 | `Usage 0x0224` | **AC Back (返回键)** | 退出阅读菜单，返回前一个阅读界面 |

---

## 4. 设备信息与广播数据构造 (DIS & BLE Advertising)

### 4.1 设备信息服务 (DIS `0x180A`) 常量
位于 `app.bin` 偏移 `0x22380`：
- **Manufacturer Name**: `"zhuhai_jieli"`
- **Model Number**: `"hid_mouse"`
- **Firmware Revision**: `"0.0.1"`
- **PnP ID (`0x2A50`)**:
  - Vendor ID Source: `0x02` (USB-IF)
  - Vendor ID: `0x053B`
  - Product ID: `0x1007`
  - Product Version: `0x0111`

### 4.2 BLE 广播数据构造 (位于 `0x4E00` ~ `0x4E70`)
- **主广播包 (ADV_IND)**:
  - `Flags`: `0x02, 0x01, 0x06`
  - `Service UUID`: `0x03, 0x03, 0x12, 0x18` (HIDS 0x1812)
- **扫描响应包 (SCAN_RSP)**:
  - `Appearance`: `0x03, 0x19, 0xC1, 0x03` (Keyboard) 或 `0xC2, 0x03` (Mouse)
  - `Complete Local Name`: `"IINE-reader"` (来自当前模式名称)
- **致命缺失**:
  - **完全不包含 Amazon 厂商自定义数据 (`AD Type 0xFF`)**。
  - **完全不包含 Amazon 厂商标识 `0x0171:0x0443`**。
