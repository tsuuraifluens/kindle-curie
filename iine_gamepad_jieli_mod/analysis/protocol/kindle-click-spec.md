# Kindle Click 协议规格 (独立规格文档)

状态标注约定: [已确认]=有直接证据(源码/逆向/实测); [高置信推断]=多证据但未实测; [待验证]=需进一步分析或实机; [未实现]=尚未完成。

数据来源:
- A = Open-Kindle-Click-nRF52840/PROTOCOL.md (基于 Kindle Paperwhite 12 代 5.20.1 二进制逆向 + 实机测试, 作者 zuyan9)
- B = OpenKindleClick.ino / kc_profile.c / KindleClickPeripheral.java (三个参考实现的源码)
- C = kindlemodding.org/kindle-hacking/pageturner.html (Kindle 侧 /etc/input_manager/devices.json)

## 0. 结论摘要

Kindle Click 是一个**普通的 BLE HID-over-GATT (HOGP) 遥控器**。Kindle 通过两处 **Amazon VID 0x0171 / PID 0x0443** 识别它:
1. 广播包 (ADV_IND) 中的 Manufacturer Specific Data (配对扫描阶段);
2. 连接后的 DIS PnP ID (0x2A50)。

无 Class of Device, 无密码学认证。遥控器必须**自行实现**“长按两个侧键 = 睡眠”手势并发送一次 `KEY_POWER`。来源 A/B/C 一致。

## 1. BLE 广播与设备识别 [已确认]

### 1.1 广播包 (ADV_IND) 必须内容
| AD 类型 | 内容 | 作用 |
|---|---|---|
| 0x01 Flags | `0x06` = LE General Discoverable + BR/EDR Not Supported | 让 Kindle 将设备判定为 LE-only (见 1.3) |
| 0xFF Manufacturer Specific | `71 01 04 43` = Company ID `0x0171`(小端) + PID `0x0443`(大端) | 配对扫描时 Kindle 提取 VID/PID 的唯一来源 |
| 0x09 Name (可选) | 任意名称, 可放扫描响应 | 可选; Flipper 版无名称也能配对 |

### 1.2 放置规则 [已确认, 来源 A]
- Flags 与 `71 01 04 43` 必须位于 **ADV_IND 本身**, 不能只放扫描响应 (Scan Response)。
- `71 01 04 43` 必须是**第一个** 0xFF AD, Kindle 只解析第一个。
- **禁止**广播 UUID16 `0xFE03` (会跳过 Amazon 遥控分支)。
- **避免** 8 字节、以 ASCII `1949` 开头的厂商数据 (另一种解析格式)。

### 1.3 LE-only 分类 [已确认, 来源 A]
- 广播中 Flags bit 0x04 (BR/EDR Not Supported) 置位、非定向广播、随机地址 → 被判定为 LE-only。
- 若被误判为双模 (BR/EDR), Kindle 会用 BR/EDR 配对并失败。必须置 Flags bit 0x04。

### 1.4 Kindle 侧的识别逻辑 [已确认, 来源 A]
- Kindle Bluedroid 的 `btif_in_is_amazon_ble_remote(company, pid)` 仅当 VID=`0x0171` 且 PID ∈ {0x0411..0x0415, 0x0418, 0x0419, 0x041C, 0x041E, 0x0420, 0x0421, 0x0423, 0x0424, 0x0443} 时返回真。`0x0443` = Kindle Click。
- 识别后设备名被替换为 "Amazon Fire TV Remote" (UI 显示 devcap 名, 不用广播名)。

### 1.5 扫描与广播时序 [已确认, 来源 A]
- 配对扫描: LE 主动扫描, 8 秒, 无 RSSI 门限, 每台设备每次最多上报一次。设备必须在这 8 秒内广播。
- 重连: 只有 Bluedroid 的 HID host 会重连遥控器, 后台扫描 30ms/1.28s。
- **广播间隔建议 20ms** 等待 Kindle。实测 20ms 广播重连中位 2.5s, 100ms 则 12.5s。
- nRF52840 实现: 快 20ms (0x20 = 32 × 0.625ms), 慢 152.5ms (配对模式 30s 后降速)。[来源 B]

### 1.6 Class of Device / Appearance [已确认, 来源 A]
- **不需要** Class of Device 与 Appearance。Kindle 不读 0x2A01。
- Flipper 实现配置 appearance 0x0180 仅为通用遥控, Kindle 不读。[来源 B]

## 2. 配对、连接与安全 [已确认, 来源 A]

- 配对: LE Secure Connections **Just Works** (NoInputNoOutput), 无需确认码。HID 属性需要 "加密但无 MITM"。
- **禁止**遥控器主动发 Security Request: Kindle 对 Amazon 遥控列表内的 VID/PID 丢弃 (配对/加密只能由 Kindle 发起)。
- 遥控器使用**静态地址**广播; nRF52840 实现**不分发 IRK**(避免被放进 resolving list 后忽略)。[来源 B]
- 加密失败 (遥控器丢失 bond) → Kindle 断开(reason 5)并从后台重连移除, 但保留 bond。恢复: Kindle 端 Forget + 遥控器端复位。
- 重连: 只有 Kindle HID host 重连; 每次连接都做完整 GATT 重发现 → HID open → uhid 重建 → input_manager 抓取。**之前的按键报告会被静默丢弃**。

连接参数 [已确认, 来源 A]:
- 配对/发现期间: 7.5ms 间隔
- 正常: 20ms 间隔 (CE 5-8.75ms), 后台 30-50ms, 监督超时 20s
- nRF52840 实现主动请求 20ms / latency 4 / 300×10ms 监督超时。[来源 B]

### 2.1 安全评估结论
- 该协议**无密码学认证**(仅 VID/PID 匹配), 这是 Amazon 的兼容性设计, 不是本项目引入的缺陷。
- 实现时不应为了兼容而额外关闭任何安全措施; 参考实现的 Just Works + 静态地址 + 不分发 IRK 即为协议兼容所需, 无更多可裁减项。

## 3. GATT 服务与特征 [已确认, 来源 A/B]

| 服务 | 特征 | 要求 |
|---|---|---|
| HID 0x1812 | Report Map; 每个 input Report 带 Report Reference (非零 ID, Input) 与 CCCD | **必须**。加密无 MITM。HID Info / Control Point / Protocol Mode / boot report 可选 (Kindle 用 Report 模式, 不读写 Protocol Mode) |
| Device Information 0x180A | PnP ID 0x2A50 = `01 71 01 43 04 vv vv` (BT SIG, VID 0x0171, PID 0x0443, 版本) | 必须。HOGP 把 VID/PID/版本交给 uhid, input_manager 据此匹配 |
| Device Information 0x180A | Firmware Revision 0x2A26 | 显示在 Settings > Accessories > Firmware version |
| Battery 0x180F | Battery Level 0x2A19, 可读 + **notify** | 见 §5 |

PnP ID 字节序: `01 71 01 43 04 00 01` = source 0x01, VID 0x0171(小端 71 01), PID 0x0443(小端 43 04), version 0x0100(小端 00 01)。[来源 B]

## 4. HID 报告与按键映射 [已确认, 来源 B/C]

### 4.1 Report Map (三实现完全一致, 一个 10 字节 input report, ID 1)
```
05 01        Usage Page (Generic Desktop)
09 06        Usage (Keyboard)
A1 01        Collection (Application)
85 01        Report ID (1)
05 07        Usage Page (Keyboard/Keypad)
19 E0 29 E7  Usage Min/Max (0xE0..0xE7 修饰键)
15 00 25 01  Logical Min/Max (0..1)
75 01 95 08  Report Size 1, Count 8
81 02        Input (Data,Var,Abs)      <- 修饰键字节
75 08 95 01  Report Size 8, Count 1
81 01        Input (Const)             <- 保留字节
19 00 29 65  Usage Min/Max (0..0x65)
15 00 25 65  Logical Min/Max (0..0x65)
75 08 95 06  Report Size 8, Count 6
81 00        Input (Data,Array,Abs)    <- 6 个按键槽
05 0C        Usage Page (Consumer)
19 00 2A FF 03  Usage Min/Max (0..0x3FF)
15 00 26 FF 03  Logical Min/Max (0..0x3FF)
75 10 95 01  Report Size 16, Count 1
81 00        Input (Data,Array,Abs)    <- 1 个 16 位 Consumer usage
C0           End Collection
```
关键点: 键盘 usage 与 Consumer usage 必须**共用一个 Application Collection**, 这样 Linux hid-generic 只生成一个 evdev 节点且带 EV_REP (亮度长按可自动重复)。

### 4.2 报告格式 (10 字节)
`[ID=1][modifiers=0][reserved=0][key0..key5][consumer_lo][consumer_hi]`
- 翻页用键盘 usage 放进 key 槽; 亮度/电源用 16 位 consumer usage 放最后 2 字节(小端)。
- 一次只发一个 consumer usage ("最后者胜")。

### 4.3 按键映射 [已确认, 来源 A/B/C]
| 手柄输入(建议) | Kindle Click 动作 | HID usage | evdev 键 |
|---|---|---|---|
| 正面主键 A (上) | 下一页(前进) | Keyboard `0x4E` | KEY_PAGEDOWN |
| 正面主键 B (下) | 上一页(后退) | Keyboard `0x4B` | KEY_PAGEUP |
| 侧键上 | 亮度 + (按住重复) | Consumer `0x006F` | KEY_BRIGHTNESSUP |
| 侧键下 | 亮度 - (按住重复) | Consumer `0x0070` | KEY_BRIGHTNESSDOWN |
| 两个侧键同时长按 1s | 睡眠 | Consumer `0x0030` | KEY_POWER |

注意: 这是 Kindle Click 的官方布局映射, **不是**对本手柄按键布局的预设。实际按键需从原厂固件/实测确认。

### 4.4 事件语义 (必须遵守) [已确认, 来源 A]
- 每次按键要有**按下报告 + 释放报告** (报告携带完整键状态, 全 0 = 释放)。
- 按住翻页键只翻一页 (X 驱动丢弃 autorepeat)。
- 亮度: 按下 + 每次 autorepeat 都触发; **释放必须可靠送达**, 否则亮度会一直升/降到下一条 consumer 报告。
- 睡眠: 遥控器自己计时长按(参考实现 1s), 先停掉亮度键, 再发**恰好一次** KEY_POWER。
- Kindle 侧 pipeline: 50ms 内同键重复按下被丢弃; 速率 100/s; 释放与重复不过滤。

## 5. 电池与低功耗 [已确认, 来源 A/B]

- Battery Service 0x180F, Battery Level 0x2A19, 可读 + 通知。
- Kindle 丢弃发现阶段的读取; 连接后每次都会主动 getBatteryLevel, 但**只有通知**才能送达 UI 与低电量告警。
- 通知时机: HID open 且 Kindle 写完 CCCD 之后; 周期性再通知(即使值未变)。
- 低电量阈值: ≤20% 红色提示, ≤5% 严重告警 (Kindle UI)。
- 低功耗策略(参考实现): 无连接时广播; 已绑定且 5 分钟无活动→休眠停广播; 任意按键唤醒; 断连后重新广播。[来源 B]
- 实际电量采样、充电管理需依赖本手柄原厂硬件, **不得伪造电量**。

## 6. 断连、睡眠与唤醒 [已确认, 来源 A]

- Kindle 睡眠后约 100s 断开蓝牙(reason 0x13); 任何遥控器(含原厂 Click)**都无法唤醒已睡眠的 Kindle**。
- Kindle 唤醒后恢复绑定并重新武装后台重连; **唤醒后的第一次按键需要先重连**。
- 参考实现: 翻页键在断开期间按下的, 排队并在 Kindle 就绪后补发(TTL 10s); 睡眠键**不排队**(避免唤醒后误睡眠)。[来源 B]

## 7. 各参考实现差异 [已确认]

| 项 | nRF52840 | Flipper Zero | Android |
|---|---|---|---|
| 广播厂商数据 | addManufacturerData(71 01 04 43) 放 ADV_IND | 借 adv_name 字段编码 `FF 71 01 04 43` 放进 ADV_IND (Flipper GAP 默认把厂商数据放扫描响应, 故用此技巧) | addManufacturerData(0x0171,{04 43}) |
| 名称 | "Open Kindle Click" (扫描响应) | 无名称(仍可配对) | — |
| HID 服务 | Bluefruit BLEHidGeneric | 手写 GATT 服务表 | 自研 BLE HID 库 |
| Report Map | 同上 10 字节 | 完全一致 | 完全一致 |
| PnP ID | 01 71 01 43 04 00 01 | 一致 | 一致 |
| IRK 分发 | 禁用(静态地址) | 默认 | — |
| 长按睡眠 | 1s | (kindle_click.c) | — |
| 已验证 | 是(Paperwhite 12 5.20.1 实测) | 是 | 是(Scribe3/Paperwhite) |

## 8. 移植到杰理平台的实现难度评估 [高置信推断]

- AC632N/BR22 是 BLE SoC, JLBTSDK 提供完整的 GAP 广播(含自定义厂商数据)、GATT、HID over GATT profile。技术上完全支持上述所有字段。
- 需要确认 JLBTSDK 的 HID 实现允许自定义 Report Map 与多 usage (10 字节报告 + consumer), 以及广播数据中厂商数据放 ADV_IND 的能力 (标准 GAP API 支持)。
- 风险点: 杰理 SDK 的 HID profile 是否默认带 boot keyboard / protocol mode / 额外的 0x1812 行为, 需要按 §3 裁剪或验证; 广播间隔 20ms 需 GAP 参数支持。