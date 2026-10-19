# Kindle 翻页器 Android 模拟与工程实现规范 (Android Implementation Specification)

## 1. 规范目标与架构背景

本规范旨在基于对 Kindle Paperwhite 2024 (12th Gen, FW 5.20.1) 逆向工程获得的全部物理与协议证据，提供一套完整的 Android 端模拟实现方案。

### 1.1 核心工程瓶颈与技术现实

在 Android 平台上直接实现 BLE HID 设备存在两个核心系统级限制：

1. **Android 原生 HOGP 权限限制 (`BluetoothGattServer`)**:
   - Android 系统蓝牙协议栈 (`com.android.bluetooth`) 对第三方非系统应用施加了强制安全策略，**显式禁止普通 App 创建 UUID 为 `0x1812` (Human Interface Device) 的 GATT 服务**。调用 `addService(0x1812)` 在无系统签名的普通 App 中会直接报错返回 `GATT_FAILURE`。
2. **`BluetoothHidDevice` API 的 VID/PID 固化限制 (Android 9.0+ Pie / API 28)**:
   - Android 9.0 引入了官方的 `BluetoothHidDevice` API，允许注册标准 HID 外设。
   - 然而，其注册类 `BluetoothHidDeviceAppSdpSettings` **未开放自定义 Vendor ID 和 Product ID 的接口**。Android 协议栈会强制使用宿主手机 OEM 的默认 DID（例如小米 `0x2717`、三星 `0x04E8`、Google `0x18D1`）。
   - **拦截连锁反应**：由 `EVID-01` 与 `EVID-03` 可知，Kindle 的 `input_manager` 在设备接入后通过 `ioctl(fd, EVIOCGID)` 获取 VID/PID 并严格比对 `/etc/input_manager/devices.json`。**若 VID/PID 不为 `0x0171:0x0443`，设备将被立即静默丢弃**。

---

## 2. 三大工程落地路线全景图

针对上述系统限制，本规范设计了三套针对不同应用场景的工程解决方案：

```
                    ┌──────────────────────────────────────────────┐
                    │  Kindle Paperwhite 12th Gen (FW 5.20.1)      │
                    │  [input_manager VID/PID 0x0171:0x0443 拦截]   │
                    └──────────────────────┬───────────────────────┘
                                           │
         ┌─────────────────────────────────┼─────────────────────────────────┐
         ▼                                 ▼                                 ▼
【方案 A: 硬件中继模式】           【方案 B: Root 手机注入】          【方案 C: 免 Root + 越狱 Kindle】
• 成本: <$2 (ESP32-C3)             • 适用: 已获取 Root 权限手机        • 适用: 普通原装 Android 手机
• 宿主: 任何免 Root 安卓手机        • 机制: 补丁 /system bt_did.conf    • 机制: 一行命令解除 Kindle 白名单
• 链路: 手机 UI -> ESP32 桥接       • 链路: 手机原生 BluetoothHidDevice • 链路: 手机原生 BluetoothHidDevice
  -> BLE 发送 0x0171:0x0443          直接发送 0x0171:0x0443             普通 OEM VID/PID 直连 Kindle
• 适用: 商业化产品/普通用户          • 适用: 极客/测试专用               • 适用: 越狱设备用户
```

---

## 3. 方案 A (推荐)：Android Controller + ESP32 硬件桥接规范

这是**无需修改 Android 系统底层、无需越狱 Kindle、100% 确保通过出厂白名单**的商业级交付方案。

### 3.1 总体通信链路
```
[Android 手机 App] 
  │ (手机音量键监听 / 屏幕大按钮 / 手势滑动)
  │
  ▼ [BLE UART 透传服务 / USB-OTG CDC 串口]
[ESP32-C3 极微型蓝牙 Dongle (Coin-cell 供电)]
  │
  ▼ [BLE HOGP (VID=0x0171, PID=0x0443, DIS 0x2A50)]
[Kindle Paperwhite 12th Gen]
  │ (input_manager 验证通过 -> kindle-input-pt -> X11 -> 电子墨水屏翻页)
```

### 3.2 ESP32-C3 固件参考实现 (ESP-IDF / NimBLE)

```cpp
#include <NimBLEDevice.h>
#include <NimBLEHIDDevice.h>

#define VENDOR_ID_AMAZON    0x0171
#define PRODUCT_ID_REMOTE   0x0443
#define PRODUCT_VERSION     0x0100

// 标准 8 字节键盘 HID 描述符 (方案 A)
const uint8_t hidReportMap[] = {
    0x05, 0x01,                    // USAGE_PAGE (Generic Desktop)
    0x09, 0x06,                    // USAGE (Keyboard)
    0xA1, 0x01,                    // COLLECTION (Application)
    0x85, 0x01,                    //   REPORT_ID (1)
    0x05, 0x07,                    //   USAGE_PAGE (Keyboard/Keypad)
    0x19, 0xE0,                    //   USAGE_MINIMUM (224)
    0x29, 0xE7,                    //   USAGE_MAXIMUM (231)
    0x15, 0x00,                    //   LOGICAL_MINIMUM (0)
    0x25, 0x01,                    //   LOGICAL_MAXIMUM (1)
    0x75, 0x01,                    //   REPORT_SIZE (1)
    0x95, 0x08,                    //   REPORT_COUNT (8)
    0x81, 0x02,                    //   INPUT (Data,Var,Abs)
    0x95, 0x01,                    //   REPORT_COUNT (1)
    0x75, 0x08,                    //   REPORT_SIZE (8)
    0x81, 0x03,                    //   INPUT (Cnst,Var,Abs)
    0x95, 0x06,                    //   REPORT_COUNT (6)
    0x75, 0x08,                    //   REPORT_SIZE (8)
    0x15, 0x00,                    //   LOGICAL_MINIMUM (0)
    0x25, 0x65,                    //   LOGICAL_MAXIMUM (101)
    0x05, 0x07,                    //   USAGE_PAGE (Keyboard)
    0x19, 0x00,                    //   USAGE_MINIMUM (0)
    0x29, 0x65,                    //   USAGE_MAXIMUM (101)
    0x81, 0x00,                    //   INPUT (Data,Ary,Abs)
    0xC0                           // END_COLLECTION
};

NimBLEHIDDevice* hid;
NimBLECharacteristic* inputReport;

void setupKindleRemote() {
    NimBLEDevice::init("Kindle Remote");
    NimBLEDevice::setSecurityAuth(true, false, false); // Just Works 绑定
    NimBLEDevice::setSecurityIOCap(BLE_HS_IO_NO_INPUT_OUTPUT);

    NimBLEServer* server = NimBLEDevice::createServer();
    hid = new NimBLEHIDDevice(server);
    inputReport = hid->inputReport(1);

    // 1. 设置制造商标号
    hid->manufacturer()->setValue("Amazon");
    // 2. 关键：设置 0x180A PnP ID (通过白名单的绝对核心)
    hid->pnp(0x02, VENDOR_ID_AMAZON, PRODUCT_ID_REMOTE, PRODUCT_VERSION);
    // 3. 关键：设置 0x2A26 固件版本 (acsbtfd 主动读取)
    NimBLEService* dis = server->getServiceByUUID("180A");
    dis->createCharacteristic("2A26", NIMBLE_PROPERTY::READ)->setValue("1.0.0");

    // 4. 设置 HID 信息与报告描述符
    hid->hidInfo(0x00, 0x02);
    hid->reportMap((uint8_t*)hidReportMap, sizeof(hidReportMap));
    hid->startServices();

    // 5. 设置电池服务 (0x180F)
    NimBLEService* bas = server->createService("180F");
    bas->createCharacteristic("2A19", NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY)->setValue((uint8_t)95);
    bas->start();

    // 6. 配置 GAP 广播
    NimBLEAdvertising* adv = NimBLEDevice::getAdvertising();
    adv->setAppearance(0x03C1); // HID Keyboard
    adv->addServiceUUID(hid->hidService()->getUUID());
    adv->addServiceUUID("180F");
    adv->start();
}

// 模拟发送按键
void sendKey(uint8_t usageCode) {
    uint8_t report[8] = {0x00, 0x00, usageCode, 0x00, 0x00, 0x00, 0x00, 0x00};
    inputReport->setValue(report, sizeof(report));
    inputReport->notify();
    delay(40); // 持续 40ms
    uint8_t release[8] = {0};
    inputReport->setValue(release, sizeof(release));
    inputReport->notify();
}

void pageForward() { sendKey(0x4E); }  // KEY_PAGEDOWN
void pageBackward() { sendKey(0x4B); } // KEY_PAGEUP
```

---

## 4. 方案 B：Root Android 手机系统注入方案

针对已 Root 的 Android 设备，可通过修改系统蓝牙 DID 配置直接使 Android 手机成为合法的 Amazon 翻页器。

### 4.1 配置文件注入
在 Android 设备的 `/system/etc/bluetooth/bt_did.conf` 或 `/data/misc/bluetooth/bt_did.conf` 中追加/替换主记录：

```ini
[DID1]
primaryRecord = true
vendorIdSource = 0x0002
vendorId = 0x0171
productId = 0x0443
version = 0x0100
```
保存后重启蓝牙服务 (`killall com.android.bluetooth`)。

### 4.2 Android 原生 `BluetoothHidDevice` Kotlin 实现

在 Android 手机上运行的官方 HOGP 代码实现结构如下：

#### 4.2.1 `AndroidManifest.xml` 声明
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.kindle.pageturner">

    <uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />

    <application
        android:label="Kindle Turner"
        android:theme="@style/Theme.Material3.DayNight">

        <service
            android:name=".service.KindleHidService"
            android:foregroundServiceType="connectedDevice"
            android:exported="false" />

        <activity
            android:name=".ui.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

#### 4.2.2 `KindleHidService.kt` (核心 HID 状态机与驱动)
```kotlin
package com.kindle.pageturner.service

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.*
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*

class KindleHidService : Service() {

    private val binder = LocalBinder()
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    companion object {
        private const val TAG = "KindleHidService"
        const val REPORT_ID_KEYBOARD = 1.toByte()

        // HID 描述符 (与固件规范完全一致)
        val REPORT_MAP = byteArrayOf(
            0x05.toByte(), 0x01.toByte(),
            0x09.toByte(), 0x06.toByte(),
            0xA1.toByte(), 0x01.toByte(),
            0x85.toByte(), 0x01.toByte(),
            0x05.toByte(), 0x07.toByte(),
            0x19.toByte(), 0xE0.toByte(),
            0x29.toByte(), 0xE7.toByte(),
            0x15.toByte(), 0x00.toByte(),
            0x25.toByte(), 0x01.toByte(),
            0x75.toByte(), 0x01.toByte(),
            0x95.toByte(), 0x08.toByte(),
            0x81.toByte(), 0x02.toByte(),
            0x95.toByte(), 0x01.toByte(),
            0x75.toByte(), 0x08.toByte(),
            0x81.toByte(), 0x03.toByte(),
            0x95.toByte(), 0x06.toByte(),
            0x75.toByte(), 0x08.toByte(),
            0x15.toByte(), 0x00.toByte(),
            0x25.toByte(), 0x65.toByte(),
            0x05.toByte(), 0x07.toByte(),
            0x19.toByte(), 0x00.toByte(),
            0x29.toByte(), 0x65.toByte(),
            0x81.toByte(), 0x00.toByte(),
            0xC0.toByte()
        )
    }

    inner class LocalBinder : Binder() {
        fun getService(): KindleHidService = this@KindleHidService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager?.adapter
        initHidProfile()
    }

    @SuppressLint("MissingPermission")
    private fun initHidProfile() {
        bluetoothAdapter?.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDevice = proxy as BluetoothHidDevice
                    registerApp()
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDevice = null
                }
            }
        }, BluetoothProfile.HID_DEVICE)
    }

    @SuppressLint("MissingPermission")
    private fun registerApp() {
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "Kindle Remote",
            "Kindle Page Turner",
            "Amazon",
            BluetoothHidDevice.SUBCLASS1_KEYBOARD,
            REPORT_MAP
        )

        val qos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800, 9, 0, 11250,
            BluetoothHidDeviceAppQosSettings.MAX
        )

        hidDevice?.registerApp(sdp, qos, null, Runnable::run, hidCallback)
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.i(TAG, "HID App Status Changed: registered=$registered")
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Kindle Connected: ${device?.address}")
                    connectedDevice = device
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Kindle Disconnected")
                    connectedDevice = null
                }
            }
        }
    }

    /**
     * 发送下一页指令 (KEY_PAGEDOWN: Usage 0x4E)
     */
    fun sendPageForward() {
        sendKeySequence(0x4E.toByte())
    }

    /**
     * 发送上一页指令 (KEY_PAGEUP: Usage 0x4B)
     */
    fun sendPageBackward() {
        sendKeySequence(0x4B.toByte())
    }

    @SuppressLint("MissingPermission")
    private fun sendKeySequence(usage: Byte) {
        val target = connectedDevice ?: return
        serviceScope.launch {
            // 1. 发送按下报文 (Press)
            val pressPayload = ByteArray(8).apply {
                this[2] = usage // Key 1
            }
            hidDevice?.sendReport(target, REPORT_ID_KEYBOARD.toInt(), pressPayload)

            // 保持 40ms，避免防抖丢弃
            delay(40)

            // 2. 发送释放报文 (Release)
            val releasePayload = ByteArray(8)
            hidDevice?.sendReport(target, REPORT_ID_KEYBOARD.toInt(), releasePayload)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
```

---

## 5. 方案 C：免 Root Android + 越狱 Kindle 解除白名单

若 Kindle 已经由用户越狱（例如通过 WatchThis / LanguageBreak / Factory 工具链），则完全不需要定制任何专用硬件或 Root 手机，只需一条命令即可解除 Kindle 的设备限制。

### 5.1 Kindle 端一键解除白名单补丁
SSH 进入 Kindle 终端，编辑 `/etc/input_manager/devices.json`，在 `devices` 数组中增加通用启发式规则：

```bash
# 挂载根目录为可写
mntroot rw

# 备份原配置
cp /etc/input_manager/devices.json /etc/input_manager/devices.json.bak

# 注入通用放行规则
cat << 'EOF' > /etc/input_manager/devices.json
{
  "devices": [
    {
      "name": "Amazon Kindle Remote",
      "vid": "0x0171",
      "pid": "0x0443",
      "category": "page_turner",
      "match_type": "vid_pid"
    },
    {
      "name": "Generic Page Turner",
      "category": "page_turner",
      "match_type": "heuristic",
      "required_keys": ["KEY_PAGEDOWN", "KEY_PAGEUP"]
    }
  ]
}
EOF

# 重启输入管理器
restart input_manager
mntroot ro
```

### 5.2 效果
打完该补丁后，**方案 B 中的 Android 代码无需 Root 手机即可直接被 Kindle 识别为官方翻页器！** 市面上所有支持 PageUp/PageDown 的无驱蓝牙小键盘、自拍杆、抖音翻页器均可即插即用。

---

## 6. 异常诊断矩阵 (Diagnostic & Troubleshooting Matrix)

| 异常现象 | 核心根因分析 | 固件对应日志/证据 | 针对性排除措施 |
|:---|:---|:---|:---|
| **Kindle 设置中无法扫描到外设** | 广播包中缺少 16-bit Service UUID `0x1812`，或 Appearance 未声明为 `0x03C1` | `acsbtfd` 过滤非 HID 广播 | 检查广播包 Payload，确保包含 `0x1812` 与 `0x03C1` |
| **能配对但无法翻页，日志报 rejected** | 外设 DIS PnP ID 未上报或 VID/PID 不符 `0x0171:0x0443` | `W classifier:device rejected (not in allowlist)` (`EVID-03`) | 检查外设 0x180A 特征 0x2A50，严格匹配 Little-Endian `0x02, 0x71, 0x01, 0x43, 0x04, 0x00, 0x01` |
| **连按翻页偶尔丢帧** | 两次按键发送间隔小于 50 ms，触碰系统硬件防抖时间窗 | `categories.json` 中的 `debounce_window_ms: 50` | 调整外设发送延时，保持时间建议 40ms，连按间隔至少 80ms |
| **连按多下 Kindle 反应极度迟钝或卡顿** | 触发令牌桶限流保护（突发超过 10 次，频率超过 100Hz） | `categories.json` 中的 `rate_limiter` | 避免使用长按自动连发（Auto-repeat），限制按键发送频率 <= 5 次/秒 |
| **翻页方向反了** | Kindle 屏幕处于 180° 倒转状态或开启了上下键对调开关 | `categories.json` 的 `orientation_swap` / `libAccessories.so` | 在 Kindle 设置 -> 配件中切换按键顺序，或旋转 Kindle 屏幕恢复正常朝向 |
| **Kindle 弹出电池电量低告警** | 外设 0x180F 服务上报的电量数值 <= 20% | `BleHidPageTurnerPolicy` (`EVID-07`) | 保持 0x2A19 特征上报电量 >= 50% |
