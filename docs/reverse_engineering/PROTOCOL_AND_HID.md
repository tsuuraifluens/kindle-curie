# 良值 (IINE L1161/L1162) HID 规范、GATT 拓扑与通信协议规范全集
**HID Specifications, GATT Attribute Database & Private Protocol Reference**

---

## 一、HID 模式矩阵与报告格式全景 (HID Modes & Reports Matrix)

固件内建了 6 大 HID 工作模式，分别使用独立的报告描述符与数据帧格式：

| 模式名称 | 字符串偏移 | 描述符偏移 | 描述符长度 | Report ID | Usage Page | 报告长度 (字节) | 传输接口 | 主要用途 |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---|
| **IINE_reader** | `0x211C6` | `0x2280C` | 58 B | `0x01` | `0x0C` (Consumer) | 3 B (1 ID + 2 Data) | BLE / BT | 电子书翻页器、多媒体按键 |
| **Xbox Controller** | `0x211D2` | `0x2248C` | 380 B | `0x01` | `0x01` (Generic Desktop) | 16 B (1 ID + 15 Data) | BT / USB | PC/iOS 游戏手柄 (X-Input) |
| **IINE_keyboard** | `0x211EB` | `0x22608` | 64 B | `0x02` | `0x01` (Generic Desktop) | 9 B (1 ID + 8 Data) | BLE / BT / USB | 标准 8 字节 Boot 键盘 |
| **IINE_mouse** | `0x211F9` | `0x22648` | 68 B | `0x04` | `0x01` (Generic Desktop) | 7 B (1 ID + 6 Data) | BLE / BT / USB | 相对位移鼠标、滚轮 |
| **IINE_phone** | `0x21204` | `0x226A6` | 192 B | `0x06` | `0x0D` (Digitizers) | 11 B (1 ID + 10 Data) | BLE | 手机多点触控屏幕模拟 |
| **IINE_control** | `0x2120F` | `0x2248C` | 380 B | `0x01` | `0x01` (Generic Desktop) | 16 B (1 ID + 15 Data) | BT / USB | Android 原生 DirectInput 手柄 |

---

## 二、详细 HID 报告帧结构 (HID Report Packet Layouts)

### 2.1 `IINE_reader` 模式输入报告 (Report ID: 0x01, Length: 3 Bytes)
```
Byte 0: [ 0x01 ] (Report ID)
Byte 1: [ Bit 0 .. Bit 7 ]
        - Bit 0: Usage 0x0030 (Power)
        - Bit 1: Usage 0x0040 (Menu)
        - Bit 2: Usage 0x0082 (Sleep)
        - Bit 3: Usage 0x00A0 (Media Select CD)
        - Bit 4: Usage 0x00B5 (Scan Next Track)
        - Bit 5: Usage 0x00B6 (Scan Previous Track)
        - Bit 6: Usage 0x00E9 (Volume Increment / Up)   <-- 方向键上/左 (01 40 00)
        - Bit 7: Usage 0x00EA (Volume Decrement / Down) <-- 方向键下/右 (01 80 00)
Byte 2: [ Bit 8 .. Bit 15 ]
        - Bit 8:  Usage 0x00E2 (Mute)
        - Bit 9:  Usage 0x00CD (Play/Pause)
        - Bit 10: Usage 0x0183 (AL Consumer Control Config)
        - Bit 11: Usage 0x0196 (AL Internet Browser)
        - Bit 12: Usage 0x0223 (AC Home)
        - Bit 13: Usage 0x0224 (AC Back)
        - Bit 14: Usage 0x022D (AC Zoom In)
        - Bit 15: Usage 0x022E (AC Zoom Out)
```

### 2.2 标准键盘输入报告 (Report ID: 0x02, Length: 9 Bytes)
```
Byte 0: [ 0x02 ] (Report ID)
Byte 1: [ Modifiers Bitmask ]
        - Bit 0: Left Control   - Bit 4: Right Control
        - Bit 1: Left Shift     - Bit 5: Right Shift
        - Bit 2: Left Alt       - Bit 6: Right Alt
        - Bit 3: Left GUI       - Bit 7: Right GUI
Byte 2: [ 0x00 ] (Reserved 常量)
Byte 3: Key 1 (HID Usage ID, e.g. 0x4B = PageUp, 0x4E = PageDown)
Byte 4: Key 2
Byte 5: Key 3
Byte 6: Key 4
Byte 7: Key 5
Byte 8: Key 6
```

### 2.3 触控数字化仪输入报告 (Report ID: 0x06, Length: 11 Bytes)
```
Byte 0: [ 0x06 ] (Report ID)
Byte 1: [ Status Bits ] (Bit 0: Tip Switch 按下, Bit 1: In-Range 处于感应范围)
Byte 2: Contact Identifier (触点 ID, 0..9)
Byte 3: X Coordinate Low Byte  (0..4095)
Byte 4: X Coordinate High Byte
Byte 5: Y Coordinate Low Byte  (0..4095)
Byte 6: Y Coordinate High Byte
Byte 7: Contact Count (活动触点总数)
Byte 8..10: Reserved / Padding
```

---

## 三、BLE GATT 属性表完整结构 (GATT Profile Topology)

```
[Service] 0x1800 (Generic Access)
  ├─ Handle 0x0003: 0x2A00 (Device Name: "IINE-reader" / "Kindle-PT") [Read]
  └─ Handle 0x0005: 0x2A01 (Appearance: 0x03C1 / 0x03C4) [Read]

[Service] 0x180A (Device Information Service)
  ├─ Handle 0x0012: 0x2A29 (Manufacturer Name: "zhuhai_jieli" / "Amazon") [Read]
  ├─ Handle 0x0014: 0x2A24 (Model Number: "L1161") [Read]
  ├─ Handle 0x0016: 0x2A26 (Firmware Revision: "1.0.0") [Read]
  └─ Handle 0x001E: 0x2A50 (PnP ID: 01 3B 05 07 10 01 00 -> 02 71 01 43 04 00 01) [Read]

[Service] 0x180F (Battery Service)
  ├─ Handle 0x0021: 0x2A19 (Battery Level: 0~100%) [Read, Notify]
  └─ Handle 0x0022: 0x2902 (CCCD: Client Characteristic Configuration) [Read, Write]

[Service] 0x1812 (Human Interface Device)
  ├─ Handle 0x0025: 0x2A4E (Protocol Mode: Boot / Report) [Read, Write No Resp]
  ├─ Handle 0x0027: 0x2A4D (Input Report 1: Consumer/Gamepad) [Read, Notify]
  │   ├─ Handle 0x0028: 0x2902 (CCCD)
  │   └─ Handle 0x0029: 0x2908 (Report Reference: ID=1, Type=Input)
  ├─ Handle 0x002B: 0x2A4D (Input Report 2: Boot Keyboard) [Read, Notify]
  │   ├─ Handle 0x002C: 0x2902 (CCCD)
  │   └─ Handle 0x002D: 0x2908 (Report Reference: ID=2, Type=Input)
  ├─ Handle 0x0036: 0x2A4D (Input Report 4: Mouse) [Read, Notify]
  ├─ Handle 0x003E: 0x2A4D (Input Report 6: Touch Digitizer) [Read, Notify]
  ├─ Handle 0x0048: 0x2A4B (Report Map) [Read]
  └─ Handle 0x004F: 0x2A4C (HID Control Point) [Write No Resp]

[Service] 0xAE40 (良值官方 App 私有配置通道)
  ├─ Handle 0x0052: 0xAE41 (App Command Rx) [Write No Resp]
  └─ Handle 0x0054: 0xAE42 (App Command Tx) [Notify]
      └─ Handle 0x0055: 0x2902 (CCCD)

[Service] 0xAE00 (杰理无线 OTA 固件升级通道)
  ├─ Handle 0x0082: 0xAE01 (OTA Downlink) [Write No Resp]
  └─ Handle 0x0084: 0xAE02 (OTA Uplink / Status) [Notify]
```

---

## 四、良值 App 私有配置通信协议字典 (Private Command Set)

上位机向 `UUID 0xAE41` 下发 ASCII 格式指令帧，手柄通过 `UUID 0xAE42` 回传：

1. **`set_kb`**: `cmd: set_kb map=<base64_data>` -> 设置键盘映射表
2. **`get_kb`**: `cmd: get_kb` -> 请求返回当前键盘键位映射
3. **`set_cs`**: `cmd: set_cs key=<id> cs=<val>` -> 修改消费控制键码
4. **`set_mt`**: `cmd: set_mt key=<id> x=<x> y=<y>` -> 物理按键绑定屏幕绝对触控坐标
5. **`set_sleep`**: `cmd: set_sleep times=<sec>` -> 配置自动休眠延时
6. **`set_screen`**: `cmd: set_screen w=<width> h=<height>` -> 配置移动端屏幕分辨率
7. **`set_restore`**: `cmd: set_restore` -> 恢复出厂设置并清除 NVM 脏数据
8. **`get_info`**: `cmd: get_info` -> 查询手柄芯片型号、固件版本号与剩余电量
