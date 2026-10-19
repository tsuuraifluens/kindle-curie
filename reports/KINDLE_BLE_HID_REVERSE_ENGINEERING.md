# Kindle Paperwhite 2024 (12th Gen) 蓝牙翻页器逆向工程综合研究报告 (Master Reverse Engineering Report)

## 1. 执行摘要 (Executive Summary)

本研究针对亚马逊最新发布的 **Kindle Paperwhite 2024 (第 12 代, 内部代号 Bellatrix 4, 固件 Sangria 5.20.1)** 进行了系统化、端到端的底层固件逆向工程分析。

研究核心目标是彻底解构官方蓝牙翻页器（Amazon Kindle Remote，商业名称 Kindle Click）的通信协议与准入鉴权机制，回答“**第三方普通蓝牙翻页器或 Android 手机能否直接模拟并适配 Kindle 翻页**”这一关键工程命题，并输出工业级实现规范。

### 1.1 核心研究结论速览
1. **存在严格的物理硬件白名单准入控制 [已证实]**：
   Kindle 系统中枢 `/usr/bin/input_manager` 严格受 `/etc/input_manager/devices.json` 驱动。出厂固件中**仅预置了一条匹配规则**：
   - **厂商 ID (Vendor ID)**: `0x0171` (Amazon.com / Lab126)
   - **产品 ID (Product ID)**: `0x0443` (Amazon Kindle Remote)
   - **匹配模式**: `vid_pid`
2. **第三方设备被直接拦截与静默丢弃 [已证实]**：
   任何未经授权的第三方蓝牙外设（如市售普通自拍杆、抖音翻页笔、免 Root Android 手机模拟器）在通过蓝牙配对并在 Linux 内核创建 `/dev/input/eventX` 后，`input_manager` 通过 `ioctl(fd, EVIOCGID)` 获取其 VID/PID。比对失败后，立即执行中断退出，**彻底拒绝建立事件监听与处理管道**，物理按键被完全静音。
3. **系统级纵深防御双重隔离 [已证实]**：
   即便绕过用户态逻辑，底层的 `udev` 规则 (`99-kindle-input.rules`) 与 X11 显示服务器 (`makexconfig`) 均被硬编码为**仅监听名为 `kindle-input-pt` 的虚拟中继设备**，原生物理蓝牙节点根本不向图形系统分发事件。
4. **按键能力高度受限 [已证实]**：
   翻页器输入管道仅放行 5 种键码：`KEY_PAGEDOWN` (109), `KEY_PAGEUP` (104), `KEY_BRIGHTNESSUP` (225), `KEY_BRIGHTNESSDOWN` (224), `KEY_POWER` (116)。市售翻页器常见的空格键 (`KEY_SPACE`)、回车键 (`KEY_ENTER`) 均会被静默丢弃。
5. **Android 原生模拟的三大瓶颈与工程破局方案 [已证实/强推断]**：
   - Android 原生禁止非系统应用创建 `0x1812` HID 服务；
   - Android `BluetoothHidDevice` API 无法自定义 VID/PID，强制上报手机 OEM 标识，必然触发 Kindle 白名单拦截；
   - **破局路径**：
     - **方案 A (推荐商业落地)**：基于 ESP32-C3 (<$2) 制作微型 BLE 硬件桥接器，上报 `0x0171:0x0443`，手机作为控制端；
     - **方案 B (极客方案)**：已 Root Android 手机修补 `/system/etc/bluetooth/bt_did.conf` 注入 Amazon VID/PID；
     - **方案 C (越狱方案)**：Kindle 越狱后在 `/etc/input_manager/devices.json` 注入通配规则，普通免 Root Android 手机与市售遥控器即可即插即用。

---

## 2. 固件资产基线与逆向分析环境

### 2.1 固件镜像与提取产物
- **官方更新包文件名**: `update_kindle_all_new_paperwhite_12th_5.20.1 (1).bin`
- **更新包大小**: 411,848,917 字节
- **更新包 SHA-256**: `2720E427A5D2EA55FB534F85BF2B3A27CEC6EE8DA6895E81ACDD1D00EF1D313C`
- **解包 Rootfs 镜像**: `~/kindle_rootfs.img` (805,306,368 字节, ext3 文件系统)
- **Rootfs SHA-256**: `17638e0a6903294f33478e50f47173704cb04a7b7d238c065f414a733d857d89`
- **目标硬件与系统信息**:
  - SoC: MediaTek MT8110
  - 板级代号: Bellatrix 4 (`mt8110_bellatrix4`)
  - 系统版本: Sangria 5.20.1 (`050-juno_2001_sangria_bellatrix4-489390`)
  - 架构: 32-bit ARM (ARMv7-A, Thumb-2, Hard-Float)

### 2.2 核心二进制组件提取清单
所有核心二进制均已完整提取并镜像至 `binaries\`:

| 组件文件名 | 原始固件路径 | SHA-256 校验和 | 架构/指令集 | 逆向角色定位 |
|:---|:---|:---|:---|:---|
| `input_manager` | `/usr/bin/input_manager` | `76ce11b0c41f4685090f600dd44f842708b83867df6d41b675538e03840e6eab` | ARMv7 Thumb-2 | 输入事件中枢、白名单拦截与虚拟设备创建 |
| `acsbtfd` | `/usr/bin/acsbtfd` | `37f9295a2eaaafd97193c1a2c690cfbfa22b7cef9a585cae0a18179fc3ea0fb7` | ARMv7 Thumb-2 | 蓝牙特性守护进程、配件策略与电量/固件监控 |
| `libace_bt.so` | `/usr/lib/libace_bt.so` | `e035041793740ee3cfafccb94df8a69f64db73d578964d4b29bb8fc3878b668f` | ARMv7 Thumb-2 | 亚马逊 ACE 蓝牙协议栈客户端 API 库 |
| `libace_bt_manager_core.so` | `/usr/lib/libace_bt_manager_core.so` | `295a12a76f2df32c9431bf3bb7d5d7870c9769dae13b8214154fbdbd572cbf42` | ARMv7 Thumb-2 | 蓝牙设备管理与 GATT 客户端核心实现 |
| `libAccessories.so` | `/app/lib/libAccessories.so` | `a6687597a275e1f5f14deaa23334874b10e106d309b0a84abb7f4ab12e44a0c3` | ARMv7 Thumb-2 | 配件 UI 交互、按键重定向 API |
| `libBluetoothSetup.so` | `/app/lib/libBluetoothSetup.so` | `b99e7100b468a5c3bbd4a8e2cb921a97d91cb61d9be594d4d03e9114b3d368e5` | ARMv7 Thumb-2 | 蓝牙扫描、配对向导与前端状态管理 |

---

## 3. 系统体系结构与端到端调用链

整个系统的翻页控制流跨越 5 个层次，其总体交互时序如下：

```
[BLE 翻页器 (或模拟器)]
       │
       ▼ (1. BLE GAP 广播: 0x1812, 0x180F, 0x180A, Appearance=0x03C1)
[MediaTek MT8110 蓝牙芯片] -> 驱动: wmt_cdev_bt.ko
       │
       ▼ (2. HCI / ACL 数据包)
[btmanagerd] (基于 Android Fluoride 架构的底层蓝牙守护进程)
       │
       ▼ (3. AIPC 内部进程间通信)
[acsbtfd] (ACE Bluetooth Feature Daemon)
    ├── 设备分类: BtDeviceManager::OnUnknownConnect -> BleHidPageTurnerPolicy (0x0002bad4)
    ├── 固件探测: BtGattClient::ReadChar -> DIS 0x180A:0x2A26 (0x0002f780)
    └── 电量监控: AceHidBatteryNotifyCb -> 0x180F:0x2A19 (低电 20%, 严重 5%)
       │
       ▼ (4. UHID 虚拟设备注册)
[Linux 内核输入子系统] (uhid.ko / hid-generic.ko)
    ├── 解析 0x1812 Report Map 描述符
    ├── 依据 0x180A:0x2A50 PnP ID 设置 struct input_id (vendor=0x0171, product=0x0443)
    └── 生成字符设备节点 /dev/input/eventX
       │
       ▼ (5. udev 热插拔事件)
[input_manager] (核心用户态输入中枢)
    ├── 采集属性: EvdevProvider::buildDeviceInfo -> ioctl EVIOCGID (0x80084502)
    ├── 白名单鉴权: DeviceClassifier::classify (0x00025990)
    │     ├── 检查 /etc/input_manager/devices.json
    │     ├── 比较 VID/PID 是否等于 0x0171:0x0443
    │     └── 若不匹配: 记录警告日志并立即销毁退出 (SILENT DROP!)
    ├── 处理管道:
    │     ├── capability_filter (仅放行 5 种键码)
    │     ├── orientation_swap (180° 旋转对调翻页)
    │     ├── debounce (50ms 硬件防抖)
    │     └── rate_limiter (100 Hz / 10 突发限流)
    └── 虚拟发射: VirtualDevice::emit -> /dev/input/uinput ("kindle-input-pt")
       │
       ▼ (6. X11 图形服务器监听)
[X11 / Xorg] (基于 /etc/upstart/makexconfig)
    ├── MatchProduct "kindle-input-pt" -> SendCoreEvents
    └── evdev KEY_PAGEDOWN (109) -> X11 XK_Page_Down (0xFF56)
       │
       ▼ (7. 阅读器按键消费)
[前台 KPP 阅读器应用]
    └── 捕获按键 -> 调度页面排版 -> 调用 EPDC 墨水屏驱动刷新
```

---

## 4. 深度逆向发现与实锤证据

### 4.1 白名单配置文件与匹配代码证据
- **配置固化**: `/etc/input_manager/devices.json` 仅包含单一条目：
  ```json
  {"name": "Amazon Kindle Remote", "vid": "0x0171", "pid": "0x0443", "category": "page_turner", "match_type": "vid_pid"}
  ```
- **汇编实现**: `input_manager::DeviceClassifier::classify` (`0x00025990` - `0x00025bc8`):
  - `0x000259f8: ldr r2, [r4, #0x18]` (加载白名单要求的 `0x04430171`)
  - `0x000259fa: ldr r3, [r6, #0x18]` (加载外设实际的 `vid:pid`)
  - `0x000259fc: cmp r2, r3` (严格相等比较)
  - 若不相等且无后续匹配条目：
    - `0x000259e2`: 输出告警日志 `"W classifier:device rejected (not in allowlist) name=%s::"`
    - `0x00025bc4: strb.w r3, [r8, #0x30]`: 强制置 `is_matched = false`
- **设备销毁**: `InputManager::onDeviceAdded` 在 `0x0001c234` 检测到 `is_matched == false` 时，直接调用 `0x0001c264: bl #0x1f430` 执行析构并返回，完全不注册事件监听。

### 4.2 双重隔离与虚拟设备机制
- **虚拟输入节点**: `input_manager` 在启动时通过 `/dev/input/uinput` 注册设备名为 `kindle-input-pt`（VID `0x0001`, PID `0x0001`）。
- **Udev 限制**: `/etc/udev/rules.d/99-kindle-input.rules` 明确规定：
  ```udev
  SUBSYSTEM=="input", ATTRS{name}=="kindle-input-pt", ENV{ID_INPUT}="1", ENV{ID_INPUT_KEY}="1", ENV{ID_INPUT_KEYBOARD}="1"
  ```
  底层真实的 `/dev/input/eventX` 根本不会被赋予键盘标签。
- **X11 限制**: `/etc/upstart/makexconfig` 仅为 `MatchProduct "kindle-input-pt"` 配置输入类驱动，彻底阻断了图形界面直接访问原生蓝牙节点的可能。

### 4.3 蓝牙协议栈与 GATT 探测行为
- **固件版本主动读取**: `acsbtfd` 包含调用 `BtGattClient::ReadChar` (`0x0002f780`)，连接后立即读取 DIS 服务 (`0x180A`) 下的 `0x2A26` 特征（Firmware Revision String）。
- **电量监控与阈值**: `BleHidPageTurnerPolicy` (`0x0002bad4`) 固化了低电告警阈值：
  - 低电量提示: `<= 20%` (`pageTurnerBatteryLow`)
  - 严重低电保护: `<= 5%` (`pageTurnerBatteryCritical`)

---

## 5. 翻页器通信协议与 HID 规范总结

### 5.1 关键 GATT 服务与特征结构
1. **Generic Access Service (`0x1800`)**:
   - Device Name: `"Kindle Remote"`
   - Appearance: `0x03C1` (Keyboard)
2. **Device Information Service (`0x180A`)** —— **通过鉴权的核心凭证**:
   - Manufacturer: `"Amazon"`
   - Model: `"Kindle Remote"`
   - Firmware Revision (`0x2A26`): `"1.0.0"`
   - **PnP ID (`0x2A50`) [关键 7 字节]**:
     `0x02, 0x71, 0x01, 0x43, 0x04, 0x00, 0x01`
     - Vendor ID Source: `0x02` (USB-IF)
     - Vendor ID: `0x0171` (Lab126)
     - Product ID: `0x0443` (Kindle Remote)
     - Version: `0x0100` (v1.0.0)
3. **Battery Service (`0x180F`)**:
   - Battery Level (`0x2A19`): `0 ~ 100`, 支持 Read 与 Notify (CCCD `0x2902`)
4. **Human Interface Device Service (`0x1812`)**:
   - HID Information (`0x2A4A`): `0x11, 0x01, 0x00, 0x02`
   - Report Map (`0x2A4B`): 工业标准键盘描述符
   - Input Report (`0x2A4D`): 8 字节键盘按键报文 (带 CCCD `0x2902`)

### 5.2 翻页按键报文与发送约束
- **下一页 (Page Down)**:
  - 按下: `01 00 00 4E 00 00 00 00 00` (Usage `0x4E` $\to$ `KEY_PAGEDOWN` 109)
  - 释放: `01 00 00 00 00 00 00 00 00`
- **上一页 (Page Up)**:
  - 按下: `01 00 00 4B 00 00 00 00 00` (Usage `0x4B` $\to$ `KEY_PAGEUP` 104)
  - 释放: `01 00 00 00 00 00 00 00 00`
- **时序与限流**:
  - 按键保持时间推荐 40ms（若 < 20ms 可能丢帧，> 500ms 触发系统自动连发）；
  - 连按间隔必须 `>= 50ms`（防抖时间窗），推荐 `>= 80ms`；
  - 突发连按不得连续超过 10 次，发送频率限制在 `<= 5` 次/秒以内。

---

## 6. Android 模拟方案与产品化落地路径

### 6.1 方案 A：Android App + ESP32-C3 极微型硬件桥接器 (商业推荐)
- **优势**: 100% 确保通过出厂 Kindle 验证；手机无需 Root；Kindle 无需越狱；硬件成本极低（ESP32-C3 模块成本约 8~12 元人民币）。
- **工作机制**:
  - ESP32-C3 烧录定制 NimBLE 固件，作为伪装的 `Amazon Kindle Remote` 与 Kindle 保持 BLE HOGP 连接。
  - ESP32-C3 同时通过 BLE UART 或 USB-OTG 与手机 App 通信。
  - 手机 App 拦截物理音量键、提供屏幕悬浮窗翻页大按钮或配合智能手表，将翻页指令透传给 ESP32-C3，再由 ESP32 发送合法按键报文。

### 6.2 方案 B：已 Root Android 手机系统级 DID 注入 (极客自测)
- **优势**: 无需额外硬件。
- **限制**: 手机必须拥有 Root 权限。
- **实施**:
  - 替换 Android `/system/etc/bluetooth/bt_did.conf` 中 Primary Record 的 `vendorId=0x0171` 与 `productId=0x0443`。
  - 使用 Android 原生 `BluetoothHidDevice` API 运行翻页应用。

### 6.3 方案 C：Kindle 越狱一键解除白名单 (通用零成本)
- **实施**:
  - 在已越狱 Kindle 的 `/etc/input_manager/devices.json` 中追加一条 `"match_type": "heuristic"` 规则，放行声明了 `KEY_PAGEDOWN` / `KEY_PAGEUP` 的所有输入设备。
  - 普通免 Root 手机运行通用 HID App，或市售任意蓝牙翻页器均可直接免驱使用。

---

## 7. 项目完整交付件索引表

本研究产生的全套 9 个核心交付件均保存在当前工程工作区 ``：

| 交付文件 | 文件类型 | 描述与核心内容 | 状态 |
|:---|:---|:---|:---|
| `evidence/firmware_inventory.csv` | 资产清册 | 固件全量二进制、架构、提取大小与 SHA-256 校验和 | **已交付** |
| `evidence/evidence_index.md` | 证据索引库 | 11 条核心反编译证据 (`EVID-01` ~ `EVID-11`)，汇编与偏移地址 | **已交付** |
| `docs/reverse_engineering/device_identification_callgraph.md` | 调用链报告 | 从 BLE 广播、内核 UHID、`input_manager` 到 X11 的端到端调用链 | **已交付** |
| `docs/reverse_engineering/hid_protocol_spec.md` | 协议规范 | BLE GAP/GATT 架构、PnP ID 报文、标准 8 字节 HID 描述符与时序 | **已交付** |
| `docs/reverse_engineering/device_identity_and_allowlist.md` | 白名单剖析 | `devices.json` 规则分析、分类器汇编还原、拒绝逻辑与规避策略 | **已交付** |
| `docs/reverse_engineering/input_mapping.md` | 输入映射规范 | 键码端到端映射矩阵、4 级管道机制 (防抖/限流/旋转) 与 X11 交互 | **已交付** |
| `docs/android/android_app_implementation_spec.md` | Android 实施规范 | 系统级限制剖析、ESP32 固件源码、Kotlin HID 驱动与故障诊断表 | **已交付** |
| `docs/reverse_engineering/open_questions.md` | 盲区备忘录 | 已证实事实归纳、待验证问题 (FCC/OTA/休眠唤醒) 与测试优先级 | **已交付** |
| `reports/KINDLE_BLE_HID_REVERSE_ENGINEERING.md` | 综合总结报告 | 本报告，全景综合研究总结、技术结论与架构全图 | **已交付** |
