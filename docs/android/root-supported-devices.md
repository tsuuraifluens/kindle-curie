# Root 模式兼容设备、系统版本矩阵与已知限制 (`root-supported-devices.md`)

## 1. 验证证据分级说明

为保证工程严谨性，本文档严格区分以下四类结论：

1. **[逆向已证实]**：已由 Kindle Paperwhite 2024 (FW 5.20.1) 固件二进制 (`input_manager`, `acsbtfd`, `devices.json`) 反汇编证实的事实。
2. **[真机已实测基线]**：已在用户真实 Root Android 设备上实测取得的权威环境检测基线：
   - `su` 可执行文件：**存在** (`suBinaryPresent = true`)
   - Root 权限：**已获取 (`UID=0`)** (`RootPermissionState.GRANTED_UID_0`)
   - SELinux：**`Enforcing`** (`SelinuxState.ENFORCING`)
   - `/system` `bt_did.conf` (`/system/etc/bluetooth/bt_did.conf`)：**不存在**
   - `/data` `bt_did.conf` (`/data/misc/bluetooth/bt_did.conf`)：**不存在**
   - 当前环境评估：**经典蓝牙 VID/PID 修改方案不支持或待验证** (`SystemCompatibilityState.UNSUPPORTED_OR_PENDING_VERIFICATION`)
3. **[静态分析与模拟测试已验证]**：已由 AOSP 蓝牙源码分析及本工程 92 项单元测试 + 故障注入模拟测试验证的行为。
4. **[待实机验证]**：需在特定 Root Android 机型及实体 Kindle 上联调确认的硬件/ROM 相关行为。

---

## 2. Android 系统版本与蓝牙栈兼容性矩阵

| Android 版本 | 蓝牙协议栈架构 | `/data/misc/bluetooth/bt_did.conf` 适用性 | 应用内 8 维状态与 `BtDidSupportStatus` 判定 | 推荐翻页方案 |
| :--- | :--- | :--- | :--- | :--- |
| **Android 8.0 ~ 11 (API 26 ~ 30)** | AOSP Fluoride (`system/bt`) | `/system/etc/bluetooth/bt_did.conf` 在部分旧 ROM 存在；仅当 `/data/misc/bluetooth/bt_did.conf` 真实存在且格式通过校验时才纳入 `verifiedModifiableTargets` | 若 `/data` 存在：`VERIFIED_EXISTING_DATA_CONFIG` (`SUPPORTED_DATA_CONF_EXISTS`)；若仅 `/system` 存在：`SYSTEM_TEMPLATE_ONLY_UNVERIFIED_OVERRIDE`（不自动创建未验证文件，`canSafelyModify = false`） | 优先使用免 Root **标准 BLE HOGP 模式** |
| **Android 12 / 12L (API 31 ~ 32)** | Fluoride / Gabeldorsche 过渡期 | 多数 ROM 已移除 `/data/misc/bluetooth/bt_did.conf` | 若双路径均缺失：`BOTH_MISSING` + `MISSING_UNVERIFIED_CONFIG` + `UNSUPPORTED_OR_PENDING_VERIFICATION` | 免 Root **标准 BLE HOGP 模式** 或 ESP32 桥接 |
| **Android 13 ~ 14+ (API 33 ~ 34+，含当前实测真机)** | AOSP `packages/modules/Bluetooth` (Gabeldorsche) | **已证实不适用**：AOSP 13+ 已移除 `bt_did.conf` 解析，改用系统属性 `bluetooth.device_id.*`；实测真机 `/system` 与 `/data` `bt_did.conf` 均不存在 | `BOTH_MISSING` + `UNSUPPORTED_OR_PENDING_VERIFICATION` (`UNSUPPORTED_ANDROID_13_PLUS_NO_CONF`)，`verifiedModifiableTargets = emptyList()`，严禁自行创建文件 | 免 Root **标准 BLE HOGP 模式**、ESP32 桥接或越狱 Kindle 方案 C |

---

## 3. Root 管理器兼容与最小权限边界

| Root 方案 | 兼容性说明 | 注意事项 |
| :--- | :--- | :--- |
| **Magisk (v24+ ~ v27+)** | 支持 `su -c` 调用 | 本应用严格将“拥有 Root 权限 (`UID=0`)”与“目标系统能力可实现”分离。当目标配置文件不存在时，不执行任何系统文件写入，也不挂载或修改 `/system`。 |
| **KernelSU / APatch** | 支持按包名显式授予 Root Profile | 仅当受管目标文件真实存在且通过格式校验时，才使用 `UID=0` 执行受管备份与恢复；绝不执行 `setenforce 0` 或放宽全局权限。 |

---

## 4. 已知限制与风险披露

1. **当前实测真机不具备 `bt_did.conf` 配置文件**：
   - 在已实测的 Root Android 设备上，虽然 `su` 存在且 `UID=0`，但 `/system/etc/bluetooth/bt_did.conf` 与 `/data/misc/bluetooth/bt_did.conf` 均不存在。
   - 本应用严格遵循零假设原则：**不会自动创建未经验证的 `bt_did.conf`，不会将其纳入可修改文件列表 (`verifiedModifiableTargets` 为空)，也不会关闭 `SELinux = Enforcing`**。
2. **只读 `/system` 分区不可直接修改**：
   - Android 10+ 普遍采用动态分区 (`super`)、EROFS 只读文件系统与 `dm-verity` (AVB 2.0)。
   - 本应用显式将 `/system/etc/bluetooth/bt_did.conf` 设为**只读参考 (`writable = false`)**，严禁执行 `mount -o rw,remount /system`。
3. **经典蓝牙 `BluetoothHidDevice` 注册成功不代表设备身份已验证**：
   - `ClassicHidDeviceManager` 通过 Android 标准 API 可注册 HID 描述符与发送按键报告，但无法指定 SDP Device ID (`0x0171:0x0443`)。因此即使 `ClassicHidRegistrationState == REGISTERED`，只要 `DeviceIdentityVerificationState` 未经真实运行态证实，经典蓝牙 HID 功能整体状态仍保持为**不支持 / 待验证**。
4. **免 Root 标准 BLE HOGP 模式独立可用**：
   - 无论经典蓝牙 VID/PID 修改功能是否可用，应用内的免 Root **标准 BLE HOGP 外设模式**（广播 GATT DIS `2A50` PnP ID = `02 71 01 43 04 00 01`、HID Service `1812`、Battery Service `180F`）均保持完整可用。
5. **禁止自动重启蓝牙服务**：
   - 为避免中断用户正在使用的蓝牙耳机、手表或车载连接，本应用在任何情况下**绝不自动执行 `killall com.android.bluetooth`**。
