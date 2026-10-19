# 未决问题、边界盲区与后续验证备忘录 (Open Questions & Remaining Gaps)

## 1. 规范概述

本文档严格按照反向工程验证规范，对 Kindle Paperwhite 2024 (12th Gen, FW 5.20.1) 蓝牙翻页器机制分析中的**已确认事实**、**强推断逻辑**以及**尚存的未决问题与实验盲区**进行体系化归纳。

---

## 2. 事实确认与置信度审计分级

### 2.1 [已证实] 核心技术事实
1. **白名单绝对拦截**:
   - `/etc/input_manager/devices.json` 固件中仅存在单一合法规则：`Amazon Kindle Remote`，VID `0x0171`，PID `0x0443`，模式 `vid_pid` (`EVID-01`)。
   - `input_manager::DeviceClassifier::classify` 严格比对内核 `struct input_id` 中的 `vendor` 与 `product`。若不匹配，直接返回 `is_matched = false` 并记录 `classifier:device rejected (not in allowlist)` 警告 (`EVID-03`)。
   - `InputManager::onDeviceAdded` 检测到 `is_matched == false` 时，彻底销毁设备对象并退出，不建立任何输入管道 (`EVID-05`)。
2. **输入管道限制与虚拟设备桥接**:
   - `input_manager` 创建名称为 `kindle-input-pt` (VID `0x0001`, PID `0x0001`) 的 `/dev/input/uinput` 虚拟节点 (`EVID-02`)。
   - 管道仅放行 5 种键码：`KEY_PAGEDOWN` (109), `KEY_PAGEUP` (104), `KEY_BRIGHTNESSUP` (225), `KEY_BRIGHTNESSDOWN` (224), `KEY_POWER` (116) (`EVID-02`)。
   - 系统设置了 50ms 硬件防抖 (`debounce_window_ms`) 与 100Hz/10突发速率限制 (`rate_limiter`)。
3. **系统层纵深防御**:
   - `/etc/udev/rules.d/99-kindle-input.rules` 仅对 `ATTRS{name}=="kindle-input-pt"` 赋予键盘环境变量 (`ID_INPUT_KEYBOARD=1`)。
   - `/etc/upstart/makexconfig` 仅对 `MatchProduct "kindle-input-pt"` 开启 X11 `Option "SendCoreEvents"` (`EVID-06`)。
4. **蓝牙服务特定策略**:
   - `acsbtfd` 内部类 `BleHidPageTurnerPolicy` 监控外设电池（低电 20%，严重低电 5%）(`EVID-07`)。
   - `acsbtfd` 在连接后主动发起 GATT 读取请求获取外设 DIS 固件版本字符串 (`0x180A:0x2A26`) (`EVID-09`)。
5. **HID 报告描述符动态加载**:
   - Kindle 固件内无硬编码静态 Report Map。由 Linux 内核 `hid-generic` 驱动动态解析从机暴露的 `0x2A4B` 报告描述符 (`EVID-11`)。

---

## 3. 核心未决问题与实验盲区 (Open Questions)

### Q-01: 官方 Kindle Remote 实体硬件主控芯片与 FCC 认证规格
- **问题定义**: Amazon 官方 Kindle Remote (Kindle Click) 采用的物理 BLE 射频芯片方案（Nordic nRF52 系列、Dialog DA14531 还是 Telink）？
- **当前状态**: **[待验证]**
- **证据盲区**: 固件分析只能获取 Kindle 主机端驱动与用户态程序，无法反推配件内部硬件 PCB 方案。
- **后续验证方法**:
  - 检索 Amazon.com Services LLC 在 FCC ID 数据库中的认证申报（如针对 e-reader remote 配件的 FCC 测试报告与拆解照片）。
  - 对官方实体遥控器执行物理拆解并记录芯片 Marking。

### Q-02: 官方广播包中是否存在厂商自定义专有字段 (Manufacturer Specific Data)
- **问题定义**: 官方翻页器在 BLE GAP 广播包的 `AD Type 0xFF` 中，是否携带特定的 Amazon 专属加密字节或鉴权 Beacon？
- **当前状态**: **[强推断]**
  - 在 `acsbtfd` 的扫描发现回调 `BtAceAdapter::AceDeviceDiscoveredCb` 反汇编中，未发现针对广播包 Payload 校验特定签名字节的强制分支；Kindle 主要是将广播外设名称及 UUID 呈现在 UI 上供用户点击配对。
  - 但不能完全排除底层 `btmanagerd` 或 `wmt_cdev_bt.ko` 驱动层对特定广播特征存在预过滤。
- **后续验证方法**:
  - 使用 BLE 抓包工具（Ellisys Bluetooth Explorer 或 nRF Sniffer + Wireshark）捕获官方翻页器在广播状态下的 Raw PDU。

### Q-03: 翻页器休眠唤醒与休眠功耗管理策略
- **问题定义**:
  1. 翻页器在静置多少分钟后会主动断开 BLE ACL 链路？
  2. 处于断连状态时，按下物理按键是执行定向广播 (Directed Advertising) 还是普通不可定向广播？
  3. Kindle 处于锁屏/屏保状态 (`powerd` 进入深度休眠模式) 时，蓝牙外设是否能通过 BLE 连接事件或按键直接唤醒系统墨水屏？
- **当前状态**: **[待验证]**
  - 在 `/etc/udev/rules.d/10-hw-wakeup.rules` 与 `/etc/upstart/acsbtfd.conf` 中存在与 `powerd` 休眠互动的脚本（例如退出时 `lipc-set-prop com.lab126.powerd preventScreenSaver 0`）。
- **后续验证方法**:
  - 接入实机后，通过终端命令 `lipc-probe -w com.lab126.powerd` 监听系统休眠事件，测试外设在不同电源状态下的按键唤醒响应。

### Q-04: 配件固件在线升级 (OTA) 机制是否存在强行校验
- **问题定义**: `acsbtfd` 读取 `0x180A:0x2A26` 固件版本后，若版本号低于或不符合特定模式，系统是否会触发后台固件静默升级或提示升级？
- **当前状态**: **[强推断]**
  - `libAccessories.so` 中存在配件管理符号，但目前没有证据表明 Kindle 会强行阻断版本不符的设备翻页；若该读取仅用于遥测与展示，则模拟器上报任意非空版本号（如 `"1.0.0"`）均可通过。
- **后续验证方法**:
  - 在模拟外设的 `0x2A26` 特征中分别返回空字符串、`"0.0.1"`、`"9.9.9"`，在 Kindle 端通过 `tail -f /tmp/btfd-acs.err` 观察其日志分支差异。

---

## 4. 后续研究与实机验证优先级矩阵

| 序号 | 验证任务 | 目标成果 | 优先级 | 依赖工具/环境 |
|:---|:---|:---|:---|:---|
| **P0-1** | ESP32-C3 原型固件烧录与实机配对测试 | 验证在出厂未越狱 Kindle 12th Gen 上是否直接放行并触发翻页 | **最高 (Immediate)** | ESP32-C3 开发板、Kindle 12 实机 |
| **P0-2** | 官方翻页器 BLE 空口抓包 (Over-the-Air Sniffing) | 提取官方广播包全字节序列、连接参数与 SMP 配对交互细节 | **高 (High)** | nRF52840 Dongle / Wireshark |
| **P1-1** | Android Root 手机 DID 注入与 `BluetoothHidDevice` 直连测试 | 验证通过修改 Android `/system/etc/bluetooth/bt_did.conf` 能否成功规避白名单拦截 | **高 (High)** | 已 Root Android 12+ 手机 |
| **P1-2** | 越狱 Kindle 打补丁放行测试 | 验证在 `/etc/input_manager/devices.json` 中注入 `heuristic` 规则后普通蓝牙遥控器的兼容性 | **中 (Medium)** | 已越狱 Kindle 5.20.1 实机 |
| **P2-1** | 休眠唤醒与防抖极限压力测试 | 验证在以 20Hz ~ 150Hz 发送按键时系统的丢帧率与 EPDC 稳定性 | **低 (Low)** | 自动化脚本控制的外设测试桩 |
