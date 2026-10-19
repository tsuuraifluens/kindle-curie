# 设备接入与事件分发调用链报告 (Device Identification & Event Dispatch Callgraph)

## 1. 总体架构与数据流图

Kindle Paperwhite 2024 (第 12 代, 固件 5.20.1) 中，从第三方蓝牙外设广播、配对连接，到最终触发阅读器电子墨水屏翻页，涉及 **内核驱动层、蓝牙协议栈守护进程 (acsbtfd / btmanagerd)、输入管理管道守护进程 (input_manager)、图形显示服务器 (X11 tinyx) 和 React Native 前台阅读应用 (KPP)** 等 5 大层次的协同交互。

```
[BLE 翻页器外设]
       │
       ▼ (1. BLE GAP 广播)
[MediaTek MT8110 BT 控制器] (wmt_cdev_bt.ko)
       │
       ▼ (2. HCI / ACL 数据流)
[btmanagerd] (ACE BT Manager Daemon / Fluoride 栈)
       │
       ▼ (3. AIPC 进程间通信)
[acsbtfd] (ACE Bluetooth Feature Daemon)
   ├── BtAceAdapter::AceDeviceDiscoveredCb (设备发现)
   ├── BtAceAdapter::AcePairingRequestCb (SMP 配对/绑定)
   ├── BtGattClient::ReadChar (GATT DIS 0x180A:0x2A26 读取固件版本)
   ├── BleHidPageTurnerPolicy (识别为 BleHidPageTurner 设备类别)
   └── AceHidBatteryLevelCb / AceHidBatteryNotifyCb (监控 0x180F 电量)
       │
       ▼ (4. UHID 虚拟输入设备注册)
[Linux 内核输入子系统] (hid-generic.ko / uhid.ko)
   ├── 解析外设 0x1812 Report Map 描述符
   ├── 将 HID Usage 转换为 Linux KeyCode (KEY_PAGEDOWN / KEY_PAGEUP)
   └── 创建字符设备节点 /dev/input/eventX (包含 struct input_id: VID/PID)
       │
       ▼ (5. udev 动态插拔事件通知)
[input_manager] (核心用户态输入中枢)
   ├── EvdevProvider::hotplug_add (/dev/input/eventX)
   ├── EvdevProvider::buildDeviceInfo (ioctl EVIOCGID 采集 VID/PID)
   ├── DeviceClassifier::classify (严格比对 /etc/input_manager/devices.json)
   │     ├── 若 VID/PID == 0x0171:0x0443 -> ACCEPT, 绑定 page_turner 类别
   │     └── 若不匹配 -> REJECT! 打印 "device rejected (not in allowlist)" 并直接丢弃!
   ├── PipelineThread (多级过滤管道):
   │     ├── capability_filter (仅放行 5 种白名单键码)
   │     ├── orientation_swap (180° 旋转自动对调翻页方向)
   │     ├── debounce (50ms 硬件防抖)
   │     └── rate_limiter (100 Hz / 10 突发限流)
   └── VirtualDevice::emit (写入 /dev/input/uinput -> "kindle-input-pt")
       │
       ▼ (6. uinput 虚拟设备事件广播)
[X11 图形显示服务器] (tinyx / Xorg)
   ├── 读取 /etc/upstart/makexconfig 生成的 InputClass:
   │     Identifier: "kindle-input-pt"
   │     Driver: "evdev"
   │     MatchProduct: "kindle-input-pt"
   │     Option: "SendCoreEvents"
   └── 将 evdev KEY_PAGEDOWN/KEY_PAGEUP 转化为 X11 XK_Page_Down / XK_Page_Up
       │
       ▼ (7. X11 核心按键事件)
[前台阅读器应用] (KPP / KPPMainApp / Pillow)
   └── 消费 XK_Page_Down / XK_Page_Up，触发 EPDC (电子纸显示控制器) 局部刷新翻页！
```

---

## 2. 逐级函数调用链还原

### 阶段一：设备发现与连接初始化
1. **用户操作**: 进入 Kindle 系统设置 -> Bluetooth -> 扫描设备。
2. **扫描发起**:
   `libBluetoothSetup.so` :: `BluetoothScanner::discover(true)`
   -> Lipc 调用 `com.lab126.btfd` 属性 `discover`
   -> `acsbtfd` :: `BtAceAdapter::StartDiscovery()`
   -> `btmanagerd` 驱动底层蓝牙芯片执行 LE Scan。
3. **设备发现回调**:
   `btmanagerd` 接收广播包
   -> 回调 `acsbtfd` :: `BtAceAdapter::AceDeviceDiscoveredCb(bdAddr, prop)`
   -> `acsbtfd` 将设备列表通过 Lipc 事件广播给 UI
   -> React Native UI 呈现外设名称。

### 阶段二：配对、绑定与 GATT 服务探测
1. **配对触发**:
   用户点击设备条目 -> `BluetoothManager::connectToDevice(mac)`
   -> `acsbtfd` :: `BtAceAdapter::Pair(mac)`
   -> 触发 BLE SMP 配对流程。Kindle 支持 Just Works 与 Numeric Comparison 模式。
2. **连接确认与策略决策**:
   `acsbtfd` :: `BtAceAdapter::AceConnStateCb(ACE_STATUS_SUCCESS, connState=CONNECTED)`
   -> `acsbtfd` :: `BtDeviceManager::OnUnknownConnect(device)` (位于 `0x0002ae0a`)
   -> 调用 `0x0002306a` 执行策略匹配
   -> 实例化 `btfd::BleHidPageTurnerPolicy` (位于 `0x0002bad4`)
   -> 验证设备支持 HID Profile，标记设备类别为 `1 (BleHidPageTurner)`。
3. **GATT 特征读取**:
   `acsbtfd` :: `BtService::OnGattcConnectionState(connected=1)` (位于 `0x00020a70`)
   -> 检查 HID 是否已自动挂载，若未挂载则调用 `connectProfile(HID)` (位于 `0x00020ae2`)
   -> 调用 `0x0002f780` 发起固件版本读取:
      - Service: `0x180A` (Device Information Service)
      - Characteristic: `0x2A26` (Firmware Revision String)
   -> 成功后触发 Pillow 弹窗通知 `page_turner_paired_dialog` ("已连接 Kindle Remote 翻页器")。

### 阶段三：内核 UHID 设备注册与 `struct input_id` 生成
1. **HID 枚举**:
   底层协议栈读取外设的 `0x1812` HID 服务：
   - `0x2A4B`: Report Map (HID 报告描述符)
   - `0x2A4A`: HID Information
   - `0x2A50`: PnP ID (隶属于 0x180A DIS 服务)
2. **内核设备节点创建**:
   蓝牙驱动向内核 `/dev/uhid` 发送 `UHID_CREATE2` 命令：
   - `rd_data`: 外设上报的 Report Map 字节流
   - `vendor`: PnP ID 中的 Vendor ID (`0x0171`)
   - `product`: PnP ID 中的 Product ID (`0x0443`)
   - `bus`: `BUS_BLUETOOTH` (0x05)
3. **节点生成**: 内核输入子系统分配并暴露 `/dev/input/eventX` 字符设备。

### 阶段四：`input_manager` 硬件采集与白名单硬拦截 (核心瓶颈)
1. **热插拔监听**:
   `input_manager` 的 `libudev` 监听到 `subsystem=="input"` 的 `add` 动作。
   -> 调用 `input_manager::EvdevProvider::hotplug_add(devnode)` (位于 `0x000267cc`)
2. **设备元数据采集**:
   -> 调用 `input_manager::EvdevProvider::buildDeviceInfo(fd)` (位于 `0x00026254`)
   - `ioctl(fd, 0x81004506, name_buf)` -> `EVIOCGNAME` 获取设备名
   - `ioctl(fd, 0x80084502, &id)` -> `EVIOCGID` 读取内核 `struct input_id`
     - `DeviceInfo.vendor = id.vendor` (位于 `0x000263e8`)
     - `DeviceInfo.product = id.product` (位于 `0x000263ec`)
   - `ioctl(fd, 0x81004508, uniq_buf)` -> `EVIOCGUNIQ` 获取蓝牙 MAC 地址
   - 调用 `0x000261b2` 执行 `ioctl(fd, EVIOCGBIT)` 遍历按键位图 (0 ~ 0x300)。
3. **白名单校验与分类**:
   调用 `input_manager::InputManager::onDeviceAdded` (位于 `0x0001c1cc`)
   -> 调用 `input_manager::DeviceClassifier::classify(deviceInfo)` (位于 `0x00025990`):
   - 读取 `/etc/input_manager/devices.json` 配置
   - 提取规则：`match_type="vid_pid"`, `vid="0x0171"`, `pid="0x0443"`
   - 汇编指令 `0x000259fc: cmp r2, r3`: 比对 `id.vendor` 与 `id.product`
   - **分支 A (匹配成功)**:
     - 记录日志 `D def:classifier:classify:VID/PID match: Amazon Kindle Remote`
     - 赋予类别 `page_turner`，返回 `is_matched = true`
   - **分支 B (匹配失败/第三方设备)**:
     - 汇编指令 `0x000259e2` 打印告警：
       `W classifier:device rejected (not in allowlist) name=%s::`
     - 汇编指令 `0x00025bbe` 打印拒绝细节：
       `D def:classifier:classify:rejected vid=0x%04x pid=0x%04x name=%s`
     - 汇编指令 `0x00025bc4: strb.w r3, [r8, #0x30]` 写入 `is_matched = false`
     - 调用者 `onDeviceAdded` 在 `0x0001c234` 检查标志位，直接调用析构函数清理并退出！
     - **结果：未列入白名单的设备在此处被彻底物理拦截，不建立任何输入管道！**

### 阶段五：输入管道处理与虚拟中继
匹配成功的设备进入 `input_manager` 的 `page_turner` 运行时管道：
1. **多级阶段执行**:
   - `Stage 1 (capability_filter)`: 校验键码是否在 `[KEY_PAGEDOWN, KEY_PAGEUP, KEY_BRIGHTNESSUP, KEY_BRIGHTNESSDOWN, KEY_POWER]` 白名单中。非法 Usage 直接丢弃。
   - `Stage 2 (orientation_swap)`: 读取加速度计/显示旋转状态。若屏幕旋转 180°，调用 `swapEvents` 自动将 `KEY_PAGEDOWN` 与 `KEY_PAGEUP` 对调。
   - `Stage 3 (debounce)`: 检查按键时间戳。距上次同类事件不足 50ms 者予以过滤。
   - `Stage 4 (rate_limiter)`: 令牌桶算法，上限 100 Hz，单次突发容限 10 个事件。
2. **虚拟设备发射**:
   `input_manager::VirtualDevice::emit` 向 `/dev/input/uinput` 写入：
   ```c
   struct input_event ev;
   ev.type = EV_KEY;
   ev.code = KEY_PAGEDOWN; // 或 KEY_PAGEUP
   ev.value = 1; // 按下 (释放为 0)
   write(uinput_fd, &ev, sizeof(ev));
   // 发送 SYN 报告
   ev.type = EV_SYN;
   ev.code = SYN_REPORT;
   ev.value = 0;
   write(uinput_fd, &ev, sizeof(ev));
   ```
   虚拟节点呈现名称为：`kindle-input-pt`。

### 阶段六：图形界面消费与屏幕刷新
1. **X11 服务器转发**:
   根据 `/etc/upstart/makexconfig` 配置的 `MatchProduct "kindle-input-pt"`，X11 的 evdev 驱动监听到该虚拟设备的按键。
2. **键码映射**:
   - Linux `KEY_PAGEDOWN` (109) -> X11 `XK_Page_Down` (0xFF56)
   - Linux `KEY_PAGEUP` (104) -> X11 `XK_Page_Up` (0xFF55)
3. **阅读器响应**:
   Kindle Reader SDK / KPP 前台页面捕获 X11 KeyPress 事件，解析为阅读导航动作（`nav.page_forward` / `nav.page_back`），计算下一页内容，调用底层 EPDC 驱动刷写 E-Ink 电子纸，完成翻页。
