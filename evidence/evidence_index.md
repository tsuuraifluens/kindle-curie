# 证据索引库 (Evidence Index)

本文档系统归档针对 Kindle Paperwhite 2024 (第 12 代, FW 5.20.1) 固件逆向过程中提取的全部原生证据项。每个证据项均分配全局唯一编号，并明确标注文献位置、汇编代码片段、逻辑流分析与确定性等级。

---

## 证据等级分类规范说明
- **[已证实]**：直接得到固件源码配置、ELF 汇编、符号反编译代码或运行时调用逻辑的绝对支持。
- **[强推断]**：多个独立二进制组件交互逻辑一致支持，且完全符合蓝牙/Linux 协议标准，但存在极细微的动态未观测分支。
- **[待验证]**：基于协议推导或 Android API 标准机制得出的结论，需实机验证或蓝牙抓包确认。
- **[已否定]**：固件证据明确证伪的假设或历史推论。

---

### EVID-01: 设备白名单配置文件定义
- **固件路径**: `/etc/input_manager/devices.json`
- **文件 SHA-256**: `2806f3b43d21e0929637f05f13ac10088ddcc21d7caac846974e2834d1a4ffef`
- **文件大小**: 182 Bytes
- **关联符号/组件**: `input_manager` 配置解析模块
- **配置原始内容**:
  ```json
  {
    "devices": [
      {
        "name": "Amazon Kindle Remote",
        "vid": "0x0171",
        "pid": "0x0443",
        "category": "page_turner",
        "match_type": "vid_pid"
      }
    ]
  }
  ```
- **支持结论**: **[已证实]**
  固件出厂配置中仅定义了一条硬件匹配规则，明确将 VID `0x0171`（Amazon.com / Lab126）、PID `0x0443`（Amazon Kindle Remote，商业名称 Kindle Click）绑定为 `page_turner` 类别，匹配模式限定为 `vid_pid`。未预置任何通用第三方设备条目。

---

### EVID-02: 翻页器输入管道与虚拟设备规范
- **固件路径**: `/etc/input_manager/categories.json`
- **文件 SHA-256**: `96208b5c48b4fc1b525e1844253b4794f8fde612c33f5603494ee6ba06e90886`
- **文件大小**: 2184 Bytes
- **关联符号/组件**: `input_manager::CategoryRuntime`
- **核心配置片段**:
  ```json
  "page_turner": {
    "virtual_device": {
      "name": "kindle-input-pt",
      "vendor_id": "0x0001",
      "product_id": "0x0001"
    },
    "pipeline_stages": ["capability_filter", "orientation_swap", "debounce", "rate_limiter"],
    "debounce_window_ms": 50,
    "rate_limit_events_per_sec": 100,
    "rate_limit_burst_size": 10,
    "capability_table": [
      "KEY_PAGEDOWN", "KEY_PAGEUP", "KEY_BRIGHTNESSUP", "KEY_BRIGHTNESSDOWN", "KEY_POWER"
    ],
    "semantic_names": {
      "nav.page_forward": "KEY_PAGEDOWN",
      "nav.page_back": "KEY_PAGEUP"
    },
    "default_mappings": [
      { "event": "KEY_PAGEDOWN", "trigger": "press", "target_type": "emit", "target": "KEY_PAGEDOWN" },
      { "event": "KEY_PAGEUP", "trigger": "press", "target_type": "emit", "target": "KEY_PAGEUP" }
    ],
    "orientation_swap_pairs": [
      { "event1": "nav.page_forward", "event2": "nav.page_back", "swap_on": [180] }
    ]
  }
  ```
- **支持结论**: **[已证实]**
  1. `input_manager` 内部维护虚拟设备 `/dev/input/uinput`，名称为 `kindle-input-pt`（VID `0x0001`, PID `0x0001`）。
  2. 物理事件必须被过滤，仅允许 5 种按键：`KEY_PAGEDOWN` (109), `KEY_PAGEUP` (104), `KEY_BRIGHTNESSUP` (225), `KEY_BRIGHTNESSDOWN` (224), `KEY_POWER` (116)。
  3. 软件支持 180° 旋转自动对调翻页键（`orientation_swap`）。
  4. 防抖时间窗为 50ms，速率上限为 100 events/s（突发 10）。

---

### EVID-03: `input_manager` 设备分类与白名单强制拒绝汇编
- **固件路径**: `/usr/bin/input_manager`
- **文件 SHA-256**: `76ce11b0c41f4685090f600dd44f842708b83867df6d41b675538e03840e6eab`
- **函数符号**: `input_manager::DeviceClassifier::classify`
- **地址范围**: `0x00025990` - `0x00025bc8` (Thumb-2)
- **汇编证据片段**:
  ```arm
  0x00025990: push.w {r4, r5, r6, r7, r8, sb, sl, fp, lr}
  0x000259a4: ldr   r3, [pc, #0x238]    ; 加载字符串 "vid_pid" (0x000349d7)
  0x000259aa: str   r3, [r7, #8]
  0x000259ac: ldr   r3, [pc, #0x234]    ; 加载字符串 "name_prefix" (0x000349e9)
  0x000259b2: str   r3, [r7, #4]
  0x000259b4: ldr   r3, [pc, #0x230]    ; 加载字符串 "heuristic" (0x000349df)
  0x000259b8: str   r3, [r7]

  ; --- 循环遍历 devices.json 中的所有 entry ---
  0x000259ba: ldr   r3, [r7, #0xc]      ; r3 = entries.end()
  0x000259bc: cmp   r4, r3              ; r4 = 当前遍历的 DeviceEntry*
  0x000259be: bne   #0x259ea            ; 若未到末尾，执行单项匹配

  ; --- 未匹配任何条目，进入拒绝逻辑 ---
  0x000259dc: movs  r1, #1
  0x000259de: ldr   r3, [r7, #0x18]
  0x000259e0: movs  r0, #4              ; LOG_WARNING 级别
  0x000259e2: add   r2, pc              ; "W classifier:device rejected (not in allowlist) name=%s::"
  0x000259e4: blx   #0x18988            ; llog 日志输出
  0x00025bb6: ldrh  r3, [r6, #0x1a]     ; 设备实际 PID
  0x00025bba: str   r3, [sp]
  0x00025bbc: ldrh  r3, [r6, #0x18]     ; 设备实际 VID
  0x00025bbe: blx   #0x18988            ; "D def:classifier:classify:rejected vid=0x%04x pid=0x%04x name=%s"
  0x00025bc2: movs  r3, #0
  0x00025bc4: strb.w r3, [r8, #0x30]    ; 结果结构体 is_matched = false (0)
  0x00025bc8: b     #0x25a4e            ; 函数返回

  ; --- match_type == "vid_pid" 比较分支 ---
  0x000259ea: ldr   r1, [r7, #8]        ; "vid_pid"
  0x000259ec: add.w r5, r4, #0x34       ; entry.match_type
  0x000259f2: blx   #0x18410            ; std::string::compare
  0x000259f6: cbnz  r0, #0x25a58        ; 若不为 vid_pid，跳转检查 name_prefix
  0x000259f8: ldr   r2, [r4, #0x18]     ; entry.vid & entry.pid (打包 32-bit: 0x04430171)
  0x000259fa: ldr   r3, [r6, #0x18]     ; incoming_device.vid & incoming_device.pid
  0x000259fc: cmp   r2, r3              ; 严格比较 VID/PID!
  0x000259fe: bne.w #0x25b9c            ; 不匹配则循环下一项 (adds r4, #0x70)
  0x00025a18: add   r2, pc              ; "D def:classifier:classify:VID/PID match: %s"
  0x00025a3e: bl    #0x25f00            ; 匹配成功，拷贝 category/virtual_device 信息
  ```
- **支持结论**: **[已证实]**
  1. `input_manager` 严格要求输入设备匹配 `devices.json` 中的至少一项。
  2. 若 VID/PID 不符且无其他规则命中，`classify` 返回 `false` 并打印 `classifier:device rejected (not in allowlist)`。
  3. 尽管代码实现了 `name_prefix` 与 `heuristic` 匹配算法，但由于 `devices.json` 只有单一条目，在未经固件篡改的情况下，**任何 VID/PID 不等于 `0x0171:0x0443` 的设备均被绝对拒绝**。

---

### EVID-04: `input_manager` 设备节点识别与 ioctl EVIOCGID 采集
- **固件路径**: `/usr/bin/input_manager`
- **函数符号**: `input_manager::EvdevProvider::buildDeviceInfo`
- **地址范围**: `0x00026368` - `0x00026440` (Thumb-2)
- **常量定义**:
  - `0x81004506`: `EVIOCGNAME(256)`
  - `0x80084502`: `EVIOCGID` (`sizeof(struct input_id) == 8`)
  - `0x81004507`: `EVIOCGPHYS(256)`
  - `0x81004508`: `EVIOCGUNIQ(256)`
- **汇编证据片段**:
  ```arm
  0x000263ac: ldr   r1, [pc, #0x80]     ; r1 = 0x81004506 (EVIOCGNAME)
  0x000263b2: blx   #0x1883c            ; ioctl(fd, EVIOCGNAME, name_buf)
  0x000263be: ldr   r1, [pc, #0x74]     ; r1 = 0x80084502 (EVIOCGID)
  0x000263c0: mov   r2, r7              ; r2 = &input_id
  0x000263c2: mov   r0, r5              ; fd
  0x000263cc: blx   #0x1883c            ; ioctl(fd, EVIOCGID, &id)
  0x000263e0: ldrhge r3, [r7, #2]       ; 读取 id.vendor (偏移 2 字节)
  0x000263e8: strhge r3, [r4, #0x18]    ; 写入 DeviceInfo.vendor
  0x000263ea: ldrhge r3, [r7, #4]       ; 读取 id.product (偏移 4 字节)
  0x000263ec: strhge r3, [r4, #0x1a]    ; 写入 DeviceInfo.product
  ```
- **支持结论**: **[已证实]**
  Kindle 校验的 VID/PID 并非应用层自定义协议数据，而是 Linux 内核输入子系统的标准 `struct input_id`。对于 BLE HOGP 设备，该数值由内核 UHID 模块在注册时确定，来源为蓝牙 Device Information Service (0x180A) 中的 PnP ID (0x2A50) 特征。

---

### EVID-05: `input_manager` 未认证设备丢弃与流程中断
- **固件路径**: `/usr/bin/input_manager`
- **函数符号**: `input_manager::InputManager::onDeviceAdded`
- **地址范围**: `0x0001c220` - `0x0001c272` (Thumb-2)
- **汇编证据片段**:
  ```arm
  0x0001c22c: bl    #0x25990            ; 调用 DeviceClassifier::classify
  0x0001c230: ldrb.w r3, [r5, #0x30]    ; 读取 is_matched 标志位
  0x0001c234: cbnz  r3, #0x1c272        ; 若 is_matched != 0，跳转 0x1c272 执行注册
  ; --- 未匹配分支 ---
  0x0001c262: mov   r0, r5
  0x0001c264: bl    #0x1f430            ; 析构并清理临时设备对象
  0x0001c268: add.w r7, r7, #0x19c
  0x0001c26c: mov   sp, r7
  0x0001c26e: pop.w {r4, r5, r6, r7, r8, sb, sl, fp, pc} ; 直接函数返回，彻底丢弃！
  ```
- **支持结论**: **[已证实]**
  若未通过白名单匹配，输入设备完全不会被加入输入事件分发队列，也不会绑定任何管道处理函数。该设备产生的全部物理按键中断在用户态被完全静音。

---

### EVID-06: X11 图形服务器与输入驱动硬绑定
- **固件路径**: `/etc/upstart/makexconfig` 及 `/etc/udev/rules.d/99-kindle-input.rules`
- **文件 SHA-256**: `6c72b22080a2b5e28a506161aa55495c6ff641774e1d7463f8d95eb07f0bb150`
- **核心代码片段**:
  ```bash
  # /etc/upstart/makexconfig 中的 page_turner 配置函数
  page_turner()
  {
   if [ "$(devcap-get-feature -a kindleremote)" == "1" ]; then
     echo 'Section "InputClass"' >> $XCONFFILE
     echo '       Identifier "kindle-input-pt"' >> $XCONFFILE
     echo '       Driver "evdev"' >> $XCONFFILE
     echo '       MatchProduct "kindle-input-pt"' >> $XCONFFILE
     echo '       Option "SendCoreEvents"' >> $XCONFFILE
     echo 'EndSection' >> $XCONFFILE
     echo '' >> $XCONFFILE
   fi
  }
  ```
- **支持结论**: **[已证实]**
  1. Kindle 前台阅读器通过 X11 服务器接收输入事件。
  2. Xorg 的 `xorg.conf` 仅针对 `MatchProduct "kindle-input-pt"` 配置了核心输入监听 (`Option "SendCoreEvents"`)。
  3. X11 根本不直接监听底层的蓝牙 `/dev/input/eventX` 节点。任何绕过 `input_manager` 虚拟转发的按键均无法进入阅读器。

---

### EVID-07: `acsbtfd` 官方翻页器策略与低电量告警
- **固件路径**: `/usr/bin/acsbtfd`
- **文件 SHA-256**: `37f9295a2eaaafd97193c1a2c690cfbfa22b7cef9a585cae0a18179fc3ea0fb7`
- **类符号**: `btfd::BleHidPageTurnerPolicy`
- **虚函数表地址**: `0x0002bad4` - `0x0002bb6a`
- **汇编证据片段**:
  ```arm
  0x0002bae4: ldr   r0, [pc, #8] -> 返回类别字符串 "pageturner" (0x00043bbd)
  0x0002bb2c: movs  r0, #0x14   ; 0x14 = 20 (十进制) -> 低电量阈值 20%
  0x0002bb30: pop   {r7, pc}
  0x0002bb34: movs  r0, #5      ; 0x05 = 5 (十进制) -> 严重低电阈值 5%
  0x0002bb38: pop   {r7, pc}
  0x0002bb3c: ldr   r0, [pc, #8] -> 返回事件字符串 "pageTurnerBatteryLow" (0x00043bc8)
  0x0002bb4c: ldr   r0, [pc, #8] -> 返回事件字符串 "pageTurnerBatteryCritical" (0x00043bdd)
  ```
- **支持结论**: **[已证实]**
  1. Kindle 蓝牙服务 `acsbtfd` 主动维护翻页器电量策略。
  2. 当电池电量低于 20% 时触发系统低电通知；低于 5% 时触发严重告警。
  3. 外设必须实现 Battery Service (`0x180F`) 与 Battery Level (`0x2A19`)，否则无法满足官方设备健康监测逻辑。

---

### EVID-08: `acsbtfd` 连接分类与设备类型识别
- **固件路径**: `/usr/bin/acsbtfd`
- **函数符号**: `btfd::BtDeviceManager::OnUnknownConnect`
- **地址范围**: `0x0002aef0` - `0x0002af90`
- **汇编证据片段**:
  ```arm
  0x0002aefe: ldrb.w r2, [r7, #0x38]    ; r2: 是否符合 policy 条件
  0x0002af04: cbz    r2, #0x2af6c       ; 若不符合，跳转 AudioSink 降级分支
  0x0002af06: ldr    r1, [r7, #0x3c]    ; r1: device_type
  0x0002af08: cmp    r1, #1             ; 1 == BleHidPageTurner
  0x0002af0a: bne    #0x2af6c
  ; --- 判定为官方翻页器 ---
  0x0002af1c: blx    #0x19660           ; "W BtDeviceManager:OnUnknownConnect:page turner %P name=%P — not paired, tracking only:"
  0x0002af20: movs   r1, #1
  0x0002af24: str.w  r1, [r7, #0xe0]    ; 设置类别为 1 (BleHidPageTurner)
  ; --- 降级分支 ---
  0x0002af7e: blx    #0x19660           ; "I BtDeviceManager:OnUnknownConnect:AudioSink fallback %P name=%P — inserting as paired:"
  0x0002af82: movs   r3, #0
  0x0002af86: str.w  r3, [r7, #0xe0]    ; 设置类别为 0 (AudioSink)
  ```
- **支持结论**: **[已证实]**
  Kindle 在蓝牙底层支持将设备归类为 `BleHidPageTurner`。若未识别为翻页器，甚至可能降级视为音频设备（AudioSink）处理，导致后续 HID 管道完全不初始化。

---

### EVID-09: GATT 固件版本读取 (`0x180A:0x2A26`)
- **固件路径**: `/usr/bin/acsbtfd`
- **函数符号**: `btfd::BtGattClient::ReadChar` 调用入口
- **地址**: `0x0002f780` - `0x0002f798` 及 `0x0003b68e`
- **汇编证据片段**:
  ```arm
  0x0002f78a: movw  r3, #0x2a26         ; Characteristic: Firmware Revision String (0x2A26)
  0x0002f78e: movw  r2, #0x180a         ; Service: Device Information Service (0x180A)
  0x0002f798: b.w   #0x3cf48            ; BtGattClient::ReadChar 发起 GATT 读取请求
  0x0003b68e: add   r2, pc              ; "I BtGattClient:ReadChars:Firmware addr=%P version=%s:"
  ```
- **支持结论**: **[已证实]**
  设备连接后，Kindle 会主动通过 GATT Client 读取外设的固件版本字符串（`0x2A26`）。外设必须在 DIS 服务中提供该特征，否则会在日志中记录 `GATTC read failed, setting unknown`。

---

### EVID-10: 按键重定向与自定义 API
- **固件路径**: `/app/lib/libAccessories.so`
- **文件 SHA-256**: `a6687597a275e1f5f14deaa23334874b10e106d309b0a84abb7f4ab12e44a0c3`
- **类符号**: `einkbluetoothsetup::BleAccessoryProvider`
- **导出方法**:
  - `swapButtons(string buttonId, string target)`
  - `resetCustomizations()`
  - `getCapabilities()`
- **字符串证据**:
  - `loc.button.page_top`
  - `loc.button.page_bottom`
  - `nav.page_forward`
  - `nav.page_back`
- **支持结论**: **[已证实]**
  Kindle 系统通过 `libAccessories.so` 与 `input_manager` 的 IPC 接口（`swapEvents`）进行交互，允许在 UI 设置中对调上下键的功能（即把顶按键映射为后退，底按键映射为前进）。

---

### EVID-11: 固件未预置静态 HID Report Descriptor
- **固件范围**: 全部分区及二进制 (`input_manager`, `acsbtfd`, `libace_bt*.so`)
- **检索内容**: 标准 USB/HID Report Map 引导字序列 (`0x05, 0x01, 0x09, 0x06` / `0x05, 0x0C, 0x09, 0x01`)
- **检索结果**: 未找到任何硬编码静态 HID 报告描述符。
- **支持结论**: **[已证实]**
  Kindle 固件对 HID 报告描述符的处理完全由底层 Linux 内核通用 HID 驱动 (`hid-generic` / `hid-input`) 在 BLE HOGP 枚举阶段动态接收并解析。只要外设上报的 Report Map 符合标准 USB HID 规范并将按键声明在 Keyboard (Usage Page 0x07) 或 Consumer (Usage Page 0x0C) 且能映射到内核 `KEY_PAGEDOWN` / `KEY_PAGEUP`，内核即可正确生成 evdev 事件。

---

### EVID-12: `acsbtfd` 扫描阶段设备分类与前置 VP ID 白名单匹配
- **固件路径**: `/usr/bin/acsbtfd`
- **函数符号**: `btfd::BtDiscoveryFilter::Classify`
- **地址范围**: `0x00022574` - `0x000227b2` (Thumb-2)
- **关联表项**:
  - `GOT + 0x5fc` (地址 `0x0005076c`): 静态 `unordered_map`，由全局构造函数 (`0x1a144`) 填入 `VID=0x0171, PID=0x0443, type=1 (PageTurner)`
  - `GOT + 0x528` (地址 `0x00050760`): 静态 `unordered_map`，由全局构造函数 (`0x1a18e`) 填入 `UUID=0x110B, type=2 (AudioSink)`
- **汇编证据片段**:
  ```arm
  0x00022574: push.w  {r4, r5, r6, r7, r8, sb, sl, fp, lr}
  0x0002258c: add.w   r8, r1, #24         ; device.name
  0x00022594: ldrh.w  fp, [r1, #62]       ; device.pid
  ; --- VP ID 白名单匹配循环 ---
  0x0002261e: ldrh.w  r2, [r9, #0]        ; table entry VID (0x0171)
  0x00022622: ldr     r1, [r7, #20]       ; device.vid
  0x00022624: cmp     r2, r1
  0x00022626: bne     #0x22674
  0x00022628: ldrh.w  r2, [r9, #2]        ; table entry PID (0x0443)
  0x0002262c: cmp     r2, fp
  0x0002262e: bne     #0x22674
  ; 匹配成功分支:
  0x00022648: blx     #0x19660            ; "I BtDiscoveryFilter:Classify:VP ID match: name=%P type=%d:"
  ; --- 未匹配丢弃分支 ---
  0x000227b0: blx     #0x19660            ; "D BtDiscoveryFilter:Classify:rejected: name=%P uuids=%zu enabledTypes=%zu:"
  ```
- **支持结论**: **[已证实]**
  1. Kindle 蓝牙搜索在未建立 GATT 连接前，由 `acsbtfd` 前置过滤广播设备。
  2. `BtDiscoveryFilter::Classify` 的 UUID 匹配表只包含 `0x110B` (AudioSink)，**不包含 `0x1812` (HID)**！
  3. 翻页器设备必须通过 VP ID 匹配（`VID 0x0171` + `PID 0x0443`）才能被判定为 `PageTurner`（`type = 1`）并在 Kindle 屏幕上展示；否则被静默丢弃，根本不会显示在设备列表中。

---

### EVID-13: `libbluetooth.so` BLE 广播与 EIR 厂商自定义数据提取
- **固件路径**: `/usr/lib/libbluetooth.so`
- **函数符号**: `btif_dm_search_devices_evt`
- **地址范围**: `0x000ac680` - `0x000ad750` (ARM32)
- **汇编证据片段**:
  ```arm
  ; --- 检查 AD Type 0xFF (Manufacturer Specific Data) ---
  0x000ac6b8: mov     r1, #0xff           ; EIR_MANUFACTURER_SPECIFIC_TYPE
  0x000ac6bc: bl      #0x58594            ; BTA_CheckEirData(p_eir, 0xFF, &len)
  0x000ad270: ldrh    r0, [r10, #0]       ; r0 = p_manuf[0..1] (VID, 0x0171 小端)
  0x000ad278: ldrh    r2, [r10, #2]       ; r2 = p_manuf[2..3] (PID)
  0x000ad600: rev16   r2, r2              ; 大端转小端: p_manuf[2..3] 为 0x04, 0x43!
  0x000ad604: uxth    fp, r2              ; fp = 0x0443
  0x000ad60c: bl      #0x3c38c            ; 调用 btif_in_is_amazon_ble_remote(vid=0x0171, pid=0x0443)
  ; 在 0x0f8b3c: movw r2, #0x0443; cmp r1, r2 -> 返回 1 (True)!
  0x000ad720: mov     r3, #1              ; has_manf_info = true
  0x000ac9d4: ldr     r3, [sp, #0x30]     ; VID (0x0171)
  0x000ac9f0: mov     r3, #0xfe           ; BT_PROPERTY_EIR_MANF_INFO
  0x000aca04: bl      #0x3caac            ; btif_storage_set_remote_device_property
  ```
- **支持结论**: **[已证实]**
  1. 在 BLE 广播中，若携带 AD Type `0xFF` 厂商自定义数据 `[0x71, 0x01, 0x04, 0x43]`（VID `0x0171` 小端 + PID `0x0443` 大端），`libbluetooth.so` 会识别为 Amazon BLE Remote，并将 `VID=0x0171, PID=0x0443` 作为 `BT_PROPERTY_EIR_MANF_INFO` 属性上报给 `acsbtfd`。
  2. 此属性直接供给 `EVID-12` 中的 `BtDiscoveryFilter::Classify` 进行 VP ID 判定，确保在初次蓝牙扫描时被 Kindle 识别并显示在屏幕上。

---

## 第二部分：良值迷你手柄 (IINE L1161/L1162) 原厂固件逆向证据

### EVID-14: IINE 升级包容器规范与 JLFS 分区布局
- **原始文件**: `raw_pkg/IINE_L1161_L1162_firmware_pkg.zip` -> `firmware.ufw`
- **文件 SHA-256**: `6DD5D46599C6E1EEB8BD2623C15D14C7A3AEE61163F46DA0DF38953C690DAC61`
- **文件大小**: 645,184 Bytes
- **支持结论**: **[已证实]**
  1. 升级包采用珠海杰理 (Zhuhai JieLi) UFW 专用封装，头部 64 字节魔数为 `JLFW` (`0x57464C4A`)，格式版本 `0x0100`。
  2. 分区索引表项采用固定 80 字节 (0x50) 结构，包含 `uboot.boot` (4,831 B)、`app.bin` (198,824 B)、`cfg_tool.bin` (722 B)、`p11_code.bin` (4,096 B)。
  3. 描述符与头部使用固定单字节/字掩码 `0xFFFF` 混淆，校验机制为标准 `CRC16` (CCITT-FALSE 0x1021)。

---

### EVID-15: 主控 SoC 架构与指令集认定
- **目标固件**: `extracted/ufw_unpacked/files/app.bin`
- **代码基地址**: `0x01E00120` (RAM 运行基址)
- **支持结论**: **[已证实]**
  1. 主控芯片为杰理科技 **AC632N / BD19** 蓝牙 SoC，CPU 核心为杰理专有 **q32s** 32-bit RISC 指令集。
  2. 具有 16 个通用寄存器 (`r0` ~ `r15`)，混合 16-bit / 32-bit 变长编码，小端字节序，主频最高 96MHz，配置 8KB I-Cache。
  3. 挂载 4KB 独立微控制器 **P11** (`p11_code.bin`) 作为低功耗休眠唤醒协处理器。
  4. 协议栈版本标识：`BTCTRLER-@20241105-$2db2c60` (构建日期 2024-11-05)。

---

### EVID-16: 固件 SFC 加密与解密算法
- **配置文件**: `extracted/ufw_unpacked/top/isd_config.ini`
- **加解密算法**: `jl_sfc_cipher` (线性同余伪随机流密码)
- **支持结论**: **[已证实]**
  1. Flash 应用区（偏移 0x2000 开始）经 `jl_sfc_cipher` 混淆，密钥 `ChipKey = 0x190D` 明文保存在 `isd_config.ini` 及 Bootloader 扇区。
  2. 升级包仅使用 CRC16 完整性检验，**不存在 RSA / ECC 等非对称数字签名，亦未开启芯片级安全启动 (Secure Boot)**。
  3. 可以在 Python 中 100% 无损解密、修改和重新加密固件。

---

### EVID-17: 蓝牙 DIS PnP ID 特征出厂定义
- **固件偏移**: `0x223BC` (虚拟地址 `0x01E224DC`)
- **函数调用点**: `0x4494` (`[BLE_HOGP]read PnP_ID`)
- **支持结论**: **[已证实]**
  1. 出厂 PnP ID 结构定义为：`01 3B 05 07 10 01 00` (VID=`0x053B`，PID=`0x1007`)。
  2. 对应广州杰理/山湾公版蓝牙手柄设备，无法通过 Kindle 的 `0x0171:0x0443` 白名单检验。

---

### EVID-18: 多模式运行框架与切换机制
- **固件偏移**: `0x211C6` ~ `0x2120F`
- **调度函数**: `0x7128` ~ `0x719E`
- **支持结论**: **[已证实]**
  1. 固件内置 6 种模式："IINE_reader", "Xbox Wireless Controller", "IINE_keyboard", "IINE_mouse", "IINE_phone", "IINE_control"。
  2. 开机时通过读取物理滑动开关与组合键 (`Home + X/Y/B/A`) 分支调度进入不同模式配置。

---

### EVID-19: `IINE_reader` 模式 HID 描述符致命缺陷
- **固件偏移**: `0x2280C` - `0x22845` (长度 58 字节)
- **支持结论**: **[已证实]**
  1. `IINE_reader` 模式使用的 HID Report Map 完全基于 Usage Page `0x0C` (Consumer Devices)。
  2. 翻页按键发送的是 16-bit 消费者控制位掩码，映射为音量加 (`0x00E9`) 和音量减 (`0x00EA`)。
  3. 触发 Kindle `capability_filter` 丢弃规则，所有音量键被静默丢弃，导致无法在 Kindle 原生阅读界面翻页。

---

### EVID-20: 固件内建标准键盘 HID 描述符
- **固件偏移**: `0x22608` - `0x22645` (长度 62 字节)
- **支持结论**: **[已证实]**
  1. 固件内已完整包含标准 8 字节键盘报告描述符 (Usage Page `0x01`, Usage `0x06`, Report ID 2)。
  2. 具备完整的修饰键 (`0xE0` ~ `0xE7`)、保留字节与 6 键按键数组 (`0x00` ~ `0xFF`)。

---

### EVID-21: 按键重映射与消费者控制分发点
- **固件偏移**: `0xE7AC` (`remap_key.consumer_key`)
- **支持结论**: **[已证实]**
  1. `0xE7AC` 处指令加载 `0x00EA` 与 `0x00E9`，但后续调用 `hid_send_report(HID_TYPE_CONSUMER)`。
  2. 仅在原位置修改键值无法改变 Report ID 与 Usage Page，必须通过键盘报文通道分发才能令 Kindle 响应。

---

### EVID-22: 候选修改固件二进制差分与无损重打包验证
- **生成文件**: `candidate/IINE_Kindle_Mod_Candidate_v0.1.ufw`
- **文件大小**: 645,184 Bytes
- **支持结论**: **[已证实]**
  1. 在 `app.bin` 内部完成 PnP ID (`0x223BC`)、厂商名称 (`0x22384`)、模式名称 (`0x211C6`) 的字节级补丁。
  2. 经过 `jl_sfc_cipher` (密钥 `0x190D`) 重新加密与 CRC16 重新计算。
  3. 生成镜像通过 `fwunpack_newfw.py` 全量解包验证，所有扇区与哈希校验 100% 一致。

