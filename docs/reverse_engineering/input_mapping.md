# Kindle 翻页器输入映射与事件处理管道规范 (Input Mapping & Event Pipeline Specification)

## 1. 规范概述

本文档详尽定义 Kindle Paperwhite 2024 (第 12 代, FW 5.20.1) 中，从外设端发送的 BLE HID 原始按键报文，到操作系统内核、用户态输入管道守护进程 (`input_manager`)、X11 视窗系统，最终抵达前台阅读器应用的完整端到端输入映射矩阵与处理管道规范。

---

## 2. 端到端事件转换矩阵 (Event Translation Matrix)

| 物理动作 | 语义动作 | HID Usage (Page 0x07) | Linux evdev 原始键码 | 白名单过滤 (capability_filter) | 语义名称 | 虚拟设备发射键码 (kindle-input-pt) | X11 KeyCode (evdev+8) | X11 KeySym | 前台阅读器应用 (KPP) 行为 |
|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|
| **底按键按下** (标准下一页) | 向前翻页 | `0x4E` (PageDown) | `KEY_PAGEDOWN` (109) | **放行 (PASS)** | `nav.page_forward` | `KEY_PAGEDOWN` (109) | 117 | `XK_Page_Down` (0xFF56) | 翻向下一页 (EPDC 墨水屏刷新) |
| **顶按键按下** (标准上一页) | 向后翻页 | `0x4B` (PageUp) | `KEY_PAGEUP` (104) | **放行 (PASS)** | `nav.page_back` | `KEY_PAGEUP` (104) | 112 | `XK_Page_Up` (0xFF55) | 翻向上一页 (EPDC 墨水屏刷新) |
| **亮度增** | 调亮背光 | `0x6F` (BrightUp) | `KEY_BRIGHTNESSUP` (225) | **放行 (PASS)** | `ui.bright_up` | `KEY_BRIGHTNESSUP` (225) | 233 | `XF86MonBrightnessUp` | 调增前光亮度 (PWM 占空比增) |
| **亮度减** | 调暗背光 | `0x70` (BrightDown) | `KEY_BRIGHTNESSDOWN` (224) | **放行 (PASS)** | `ui.bright_down` | `KEY_BRIGHTNESSDOWN` (224) | 232 | `XF86MonBrightnessDown` | 调减前光亮度 (PWM 占空比减) |
| **电源键** | 唤醒/休眠 | `0x66` (Power) | `KEY_POWER` (116) | **放行 (PASS)** | `sys.power` | `KEY_POWER` (116) | 124 | `XF86PowerOff` | 触发系统休眠或唤醒 |
| **空格键** (第三方常用) | 下一页 | `0x2C` (Space) | `KEY_SPACE` (57) | **丢弃 (DROP)** | 无 | 不发射 | 无 | 无 | **无响应 (被过滤)** |
| **回车键** (第三方常用) | 确认/下页 | `0x28` (Return) | `KEY_ENTER` (28) | **丢弃 (DROP)** | 无 | 不发射 | 无 | 无 | **无响应 (被过滤)** |
| **方向右键** (第三方常用) | 下一页 | `0x4F` (RightArrow) | `KEY_RIGHT` (106) | **丢弃 (DROP)** | 无 | 不发射 | 无 | 无 | **无响应 (被过滤)** |
| **音量加/减** (自拍杆常用) | 媒体控制 | `0xE9`/`0xEA` (Consumer)| `KEY_VOLUMEUP`/`DOWN` | **丢弃 (DROP)** | 无 | 不发射 | 无 | 无 | **无响应 (被过滤)** |

> **关键技术事实**：
> 绝大多数市售廉价翻页器、蓝牙自拍杆默认采用 `KEY_SPACE`、`KEY_ENTER` 或 Consumer 键码（如音量键），在 Kindle 12 代固件中，即便绕过白名单接入，也会在 `capability_filter` 阶段被**静默丢弃**。外设必须严格发送 `KEY_PAGEDOWN` (`0x4E`) 和 `KEY_PAGEUP` (`0x4B`)。

---

## 3. `input_manager` 四级过滤管道详解

当设备通过 `devices.json` 的白名单鉴权后，`input_manager` 会为其建立专用处理管道。该管道在 `/etc/input_manager/categories.json` 中定义为：
```json
"pipeline_stages": [
  "capability_filter",
  "orientation_swap",
  "debounce",
  "rate_limiter"
]
```

### 3.1 阶段一：键码能力过滤 (`capability_filter`)
- **配置项**:
  ```json
  "capability_table": [
    "KEY_PAGEDOWN", "KEY_PAGEUP", "KEY_BRIGHTNESSUP", "KEY_BRIGHTNESSDOWN", "KEY_POWER"
  ]
  ```
- **工作机制**:
  1. `input_manager` 监听物理 `/dev/input/eventX` 传来的 `EV_KEY` 事件。
  2. 提取 `event.code`，在预编译的哈希集合或数组中进行查找。
  3. 若 `code` 不在允许的 5 个键码之中，直接丢弃该事件，不继续传递到下一管道阶段。

---

### 3.2 阶段二：屏幕旋转方向对调 (`orientation_swap`)
- **配置项**:
  ```json
  "semantic_names": {
    "nav.page_forward": "KEY_PAGEDOWN",
    "nav.page_back": "KEY_PAGEUP"
  },
  "orientation_swap_pairs": [
    { "event1": "nav.page_forward", "event2": "nav.page_back", "swap_on": [180] }
  ]
  ```
- **工作机制**:
  - 当用户在横向或倒转（180 度）模式下阅读时，原本物理上方的按键可能在视觉上变为下方按键。
  - `input_manager` 实时监听 Kindle 系统的重力传感器或 UI 旋转状态（通过 Lipc 属性 `com.lab126.winmgr orientation`）。
  - 当检测到当前旋转角度为 `180` 度时，自动将输入的 `KEY_PAGEDOWN` 与 `KEY_PAGEUP` 进行对调，确保用户的空间直觉一致。

#### 用户自定义按键对调支持 (`libAccessories.so`)
除自动旋转对调外，Kindle 官方在设置界面中提供了“按键功能对调”开关。其底层调用链为：
- 设置界面触发 `einkbluetoothsetup::BleAccessoryProvider::swapButtons(buttonId, target)`。
- 通过 IPC 向 `input_manager` 下发动态重映射指令，将 `default_mappings` 中的触发目标交换（`EVID-10`）。

---

### 3.3 阶段三：硬件接触防抖 (`debounce`)
- **配置项**:
  ```json
  "debounce_window_ms": 50
  ```
- **工作机制**:
  - 维护针对每个按键的纳秒级高精度时间戳记录 `last_event_time`。
  - 计算当前事件与上次事件的时间差 $\Delta t = t_{current} - t_{last}$。
  - 若 $\Delta t < 50 \text{ ms}$，判定为机械抖动或蓝牙重传引起的重复事件，予以直接丢弃。

---

### 3.4 阶段四：事件速率限制 (`rate_limiter`)
- **配置项**:
  ```json
  "rate_limit_events_per_sec": 100,
  "rate_limit_burst_size": 10
  ```
- **工作机制**:
  - 采用经典令牌桶（Token Bucket）算法实现。
  - 令牌生成速率为每秒 100 个，桶容量最大为 10 个突发事件。
  - **设计初衷**：电子墨水屏 (E-Ink) 的物理刷新响应周期通常在 100ms ~ 300ms 之间。若外部蓝牙外设由于死循环或异常连发向系统灌注高频按键，会导致 X11 与底层渲染合成器（Pillow / KPP）消息队列积压，引起墨水屏驱动 (EPDC) 崩溃死机。速率限制器在此处作为关键系统保护装置。

---

## 4. 虚拟设备分发与 X11 交互规范

### 4.1 `/dev/input/uinput` 虚拟节点构造
通过所有管道阶段后，`input_manager` 负责将事件转化为虚拟输入发射。
- **设备名称**: `kindle-input-pt`
- **总线类型**: `BUS_VIRTUAL` (`0x0006`)
- **厂商 ID**: `0x0001`
- **产品 ID**: `0x0001`
- **事件生成序列**:
  ```c
  // 1. 发射按键按下/释放
  struct input_event ev_key;
  memset(&ev_key, 0, sizeof(ev_key));
  gettimeofday(&ev_key.time, NULL);
  ev_key.type = EV_KEY;
  ev_key.code = KEY_PAGEDOWN; // 109
  ev_key.value = 1;           // 1=按下, 0=释放
  write(uinput_fd, &ev_key, sizeof(ev_key));

  // 2. 发射同步帧
  struct input_event ev_syn;
  memset(&ev_syn, 0, sizeof(ev_syn));
  gettimeofday(&ev_syn.time, NULL);
  ev_syn.type = EV_SYN;
  ev_syn.code = SYN_REPORT;
  ev_syn.value = 0;
  write(uinput_fd, &ev_syn, sizeof(ev_syn));
  ```

---

### 4.2 X11 核心事件监听与分发

在 `/etc/upstart/makexconfig` 中，Xorg 配置如下：
```xorg
Section "InputClass"
       Identifier "kindle-input-pt"
       Driver "evdev"
       MatchProduct "kindle-input-pt"
       Option "SendCoreEvents"
EndSection
```

- **X11 键码偏移计算公式**:
  $$\text{X11 KeyCode} = \text{Linux evdev code} + 8$$
  - `KEY_PAGEDOWN` (109) $\to$ X11 KeyCode $109 + 8 = 117$
  - `KEY_PAGEUP` (104) $\to$ X11 KeyCode $104 + 8 = 112$
- **X11 KeySym 映射表**:
  - KeyCode 117 映射到符号 `XK_Page_Down` (`0xFF56`)
  - KeyCode 112 映射到符号 `XK_Page_Up` (`0xFF55`)

---

## 5. 前台阅读器应用 (KPP) 响应流程

1. **事件捕获**: KPP 阅读器通过 X11 事件循环监听 `KeyPress` 与 `KeyRelease`。
2. **按键消费**: 
   - 收到 `XK_Page_Down` 时，调用当前电子书阅读渲染引擎的 `goToNextPage()`。
   - 收到 `XK_Page_Up` 时，调用 `goToPreviousPage()`。
3. **EPDC 墨水屏刷新**:
   - 渲染引擎向 Linux 内核帧缓冲 (`/dev/fb0`) 写入渲染好的页面位图。
   - 通过 `ioctl(fb_fd, MTK_EPDC_IOCTL_UPDATE, ...)` 指示 MediaTek 电子墨水屏控制器执行快速局部刷新 (GLR16 或 A2 模式)，完成物理电子纸的黑白粒子翻转。

---

## 6. 浅睡眠 (ScreenSaver) 休眠与电源按键机制逆向事实

### 6.1 核心逆向反汇编事实 (FW 5.20.1 `input_manager`)

经对 Kindle 官方 ARM32 二进制 `/usr/bin/input_manager` 严格反汇编分析：
1. **动作注册机制 (`registerDefaultActions` @ `0x000210b8`)**:
   `input_manager` 仅注册了三个默认动作：`display.brightness_up`、`display.brightness_down` 和 `system.sleep`。
2. **`system.sleep` 底层真实调用链**:
   - 在 `categories.json` 中，`KEY_POWER` (Usage `0x66`, Linux Keycode 116) 映射至动作 `"system.sleep"`（条件: `["press"]`）。
   - 在动作执行函数（Lambda @ `0x00021294`）中，调用的是虚函数表偏移 `[r3, #0x1c]`：
     ```arm
     0x212a8: movs    r1, #1
     0x212ac: ldr     r0, [r3]
     0x212ae: ldr     r3, [r0]
     0x212b0: ldr     r3, [r3, #0x1c]   ; vtable 偏移 0x1c
     0x212b2: blx     r3                ; IPC 调用
     ```
   - 虚函数表对应关系（`0x0003bc6c`）：
     - `+0x14`: `PowerManagementServiceProxy::setWakeUpAsync(int)`
     - `+0x18`: `PowerManagementServiceProxy::setPowerButtonAsync(int)`
     - `+0x1c`: **`PowerManagementServiceProxy::setPowerButtonIfActiveAsync(int)`**
3. **为什么可以熄屏但无法唤醒？**
   - **活跃状态下 (Active)**: 用户正在阅读，`powerd` 判定当前状态为 `active`，执行 `setPowerButtonIfActiveAsync(1)`，系统安全进入屏保 (`screenSaver`) 息屏休眠。
   - **休眠状态下 (ScreenSaver / Suspend)**: Kindle 已在屏保界面，当前状态**不是** `active`！`powerd` 接收到 `setPowerButtonIfActiveAsync(1)` 后检测到条件不满足，**直接作为 No-Op 静默丢弃**！因此 Usage `0x66` 绝对无法唤醒已在屏保状态下的 Kindle。
4. **蓝牙链路功耗管理 (`acsbtfd`)**:
   - 当设备进入屏保浅休眠后，`acsbtfd` 启动空闲计时器。
   - 超时后，Kindle 主动取消订阅全部 CCCD（Battery / Keyboard / Consumer），并发送 HCI 原因 `0x13` (`Remote User Terminated Connection`) 强制断开 BLE 连接。
   - Kindle 系统进入深度休眠 (Suspend to RAM / Standby)，底层射频芯片降频或关断。官方体系中，墨水屏唤醒严格依赖机身物理电源键（GPIO 中断直连）或官方磁吸皮套（Hall 传感器触发 `setWakeUpAsync`）。

### 6.2 全链路映射审计表

| 层级 | 字段 / 标识 | 取值 / 含义 | 固件逆向机制与真实行为 |
|:---|:---|:---|:---|
| **HID Usage Page** | Usage Page | `0x07` (Keyboard/Keypad Page) | 标准 HOGP 键盘页 |
| **HID Usage ID** | Usage ID | `0x66` (`Keyboard Power`) | USB HID 规范电源键 |
| **Linux 内核层** | evdev Keycode | `116` / `0x0074` (`KEY_POWER`) | `hid-input` 驱动标准映射 |
| **应用映射层** | Category Action | `"system.sleep"` | `categories.json` 动作分发 |
| **系统服务调用** | IPC 调用 | `PowerManagementServiceProxy::setPowerButtonIfActiveAsync(1)` | `input_manager` @ `0x00021294` (vtable +0x1c) |
| **电源状态机** | `powerd` 判定 | `active` $\to$ `screenSaver` | **单向生效**：仅活跃时熄屏；屏保状态下直接丢弃，不执行唤醒 |

### 6.3 固件设计结论与规范

1. **熄屏功能 (Sleep)**：外设发送 Usage `0x66`（70ms 按下保持 + 自动释放），Kindle 能够立即响应并进入屏保休眠。
2. **唤醒屏保 (Wakeup)**：受限于 Kindle 固件对外部输入设备的安全性与功耗防御策略，`input_manager` 严格过滤非白名单按键且将 `0x66` 绑定至 `IfActive` 单向休眠接口。屏保唤醒必须通过机身物理按键或磁吸皮套唤醒。

