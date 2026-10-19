# Root 权限安全审计报告与 `bt_did.conf` 专项评估 (`root-security-audit.md`)

## 1. 审计范围与总体结论

本次专项安全审计覆盖 `Kindle Page Turner` Android 工程中与 Root 权限检测、Shell 命令执行、系统文件读写、经典蓝牙 HID 设备身份 (`bt_did.conf`) 相关的全部代码路径与文档规范：

- [`RootHelper.kt`](../../app/src/main/java/com/kindle/pageturner/root/RootHelper.kt)
- [`SettingsActivity.kt`](../../app/src/main/java/com/kindle/pageturner/ui/SettingsActivity.kt)
- [`MainActivity.kt`](../../app/src/main/java/com/kindle/pageturner/ui/MainActivity.kt)
- [`ClassicHidDeviceManager.kt`](../../app/src/main/java/com/kindle/pageturner/classic/ClassicHidDeviceManager.kt)
- [`KindlePeripheralService.kt`](../../app/src/main/java/com/kindle/pageturner/service/KindlePeripheralService.kt)
- [`DiagnosticLogger.kt`](../../app/src/main/java/com/kindle/pageturner/diagnostics/DiagnosticLogger.kt)
- [`android_app_implementation_spec.md`](./android_app_implementation_spec.md)

### 1.1 改造前初始审计核心发现

在本次改造前，工程中与 Root 相关的实现存在以下严重缺陷：

1. **主线程同步阻塞执行 `su -c id` 且无超时保护（高风险 / ANR 缺陷）**：
   - 原 [`SettingsActivity.kt`](../../app/src/main/java/com/kindle/pageturner/ui/SettingsActivity.kt) 在 `onCreate()` 中直接同步调用 `RootHelper.inspectEnvironment()`。
   - 原 `RootHelper` 调用 `Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))` 并立即阻塞调用 `reader.readLine()`，未设置任何超时 (`waitFor(timeout, TimeUnit)`)。当 Magisk/KernelSU 弹出授权对话框等待用户点击或守护进程挂起时，直接阻塞 Android UI 主线程导致 ANR。
   - 违反最小权限原则：用户仅打开“设置”页面修改普通 BLE 广播名称或电量时，也会无条件触发 `su` 授权请求弹窗。
2. **进程与流资源泄漏、忽略退出码与标准错误（中风险）**：
   - 原 `RootHelper.inspectEnvironment()` 未在 `finally` 块中关闭 `BufferedReader`、`inputStream`、`errorStream` 和 `outputStream`，若 `readLine()` 抛出异常则 `process.destroy()` 被跳过。
   - 未检查 `process.exitValue()`，未消费 `errorStream`。
3. **使用普通非特权 `java.io.File` 读取受保护系统目录（高风险 / 逻辑失效）**：
   - 原代码直接调用 `File("/data/misc/bluetooth/bt_did.conf").exists()` 与 `readText()`。
   - 在 Android 系统中，`/data/misc/bluetooth` 目录权限为 `0770`（属主 `bluetooth:bluetooth`，SELinux 上下文 `u:object_r:bluetooth_data_file:s0`）。普通应用进程 (`untrusted_app`) 直接调用 `java.io.File` 必然因 `EACCES` 权限拒绝而返回 `false`/`null`，即使应用已获得 `su` 授权也无法正确检测该文件状态。
4. **`su` 二进制前置判断过严导致漏检（低风险）**：
   - 原代码仅在 10 个硬编码路径通过 `File(path).exists()` 返回 `true` 时才尝试执行 `su`。在现代 Magisk / KernelSU / APatch 环境下，应用沙盒对 `/sbin` 或 `/debug_ramdisk` 可能无 `stat` 权限，导致 `hasSu` 误判为 `false`。
5. **把 Root 授权成功误导为可直接覆盖系统配置，缺乏备份、事务与恢复机制（严重风险）**：
   - 原 `RootHelper.getDidPatchInstructions()` 建议直接运行：
     `cat << 'EOF' > /data/misc/bluetooth/bt_did.conf ... EOF` 及 `su -c "killall com.android.bluetooth"`。
   - **无修改前备份**：直接重定向覆盖 `/data/misc/bluetooth/bt_did.conf`，一旦覆盖无法还原原始文件。
   - **无属主/权限/SELinux 上下文保护**：以 `root:root` 身份重定向创建文件会导致属主变为 `root:root`、SELinux 上下文偏离 `bluetooth_data_file`，致使 `com.android.bluetooth` 无法读取甚至启动异常。
   - **粗暴杀死系统蓝牙进程**：建议无条件执行 `killall com.android.bluetooth`，会中断所有正在活动的蓝牙连接。

---

## 2. 全量系统文件修改与 Root 入口审计清单

对整个代码库检索 `Runtime.exec`、`ProcessBuilder`、`su`、`File`、`chmod`、`chown`、`restorecon`、`mount`、`cp`、`mv`、`rm`、`sed`、`tee` 及 `bt_did.conf` 后的完整审计清单如下：

| 审计项目 | 改造前现状记录 | 风险等级与评级依据 | 改造后安全实现 |
| :--- | :--- | :--- | :--- |
| **修改入口** | 原代码无受管 UI 修改入口，仅在 `RootHelper.getDidPatchInstructions()` 提供裸 Shell 脚本并在 `SettingsActivity` 提示可执行方案 B | **严重**：引导用户无备份直接覆盖系统文件 | 统一收敛至 [`RootFileTransactionManager`](../../app/src/main/java/com/kindle/pageturner/root/RootFileTransactionManager.kt) 与 [`RootManagementActivity`](../../app/src/main/java/com/kindle/pageturner/ui/RootManagementActivity.kt) |
| **目标文件 1** | `/data/misc/bluetooth/bt_did.conf`（Android Fluoride 蓝牙栈运行时 DID 配置） | **高**：属于系统蓝牙服务数据目录文件，权限/SELinux 错误会影响蓝牙服务 | 纳入 `ManagedTargetPath.DATA_BT_DID_CONF` 白名单，强制备份、格式校验、临时文件写后校验、元数据恢复与原子替换 |
| **目标文件 2** | `/system/etc/bluetooth/bt_did.conf`（只读系统分区蓝牙 DID 默认模板） | **严重**：修改 `/system` 需重挂载只读分区，破坏 dm-verity / AVB 校验 | 纳入 `ManagedTargetPath.SYSTEM_BT_DID_CONF` **仅限只读检查 (`writable = false`)**，严禁直接写入或重挂载 `/system` |
| **修改内容** | 将 `[DID1]` 主记录修改为 `vendorIdSource=0x0002, vendorId=0x0171, productId=0x0443, version=0x0100` | **高**：若原文件为未知格式或包含其他 `[DID2]` 节，盲目全量覆盖会丢失原配置 | [`BtDidConfValidator`](../../app/src/main/java/com/kindle/pageturner/root/BtDidConfValidator.kt) 校验 INI 语法，拒绝未知/二进制格式，保留注释及非冲突附加节，并提供 Unified Diff 预览 |
| **权限需求** | 检测与修改 `/data/misc/bluetooth/bt_did.conf` 需 Root (`uid=0`)；标准 BLE 外设模式无需 Root | **中**：原 `SettingsActivity.onCreate()` 自动请求 Root 违反最小权限原则 | 仅在用户主动点击检测/修改/恢复，或启动时存在未完成中断事务时按需调用 `su` |
| **现有备份** | 改造前：**无任何备份机制** | **严重**：原文件被覆盖或误创建后无法还原 | [`RootBackupManager`](../../app/src/main/java/com/kindle/pageturner/root/RootBackupManager.kt) 强制在 `files/root_backups` 保存原文件字节、SHA-256、存在状态、属主、权限与 SELinux 上下文，并支持外部导出 |
| **失败处理** | 改造前：无超时、无部分写入检测、无回滚 | **高**：命令超时或半截写入会留下损坏的系统配置文件 | 8 秒命令超时 + 临时文件写盘哈希校验 + 原子 `mv -f` + 失败自动回滚 (`ROLLING_BACK -> ROLLED_BACK`) |
| **恢复能力** | 改造前：无恢复功能，未区分“原文件不存在”与“原文件存在” | **严重**：若原文件不存在，简单恢复空文件仍会残留无效配置 | 完整恢复流程：备份校验、外部修改冲突检测、恢复前保护快照、恢复原文件内容与元数据（若原状态为 `NOT_EXIST` 则安全删除新建文件） |
| **重启行为** | 改造前：无持久化事务日志，进程被杀或重启后状态完全丢失 | **高**：崩溃或强制停止后无法得知文件是否已被改动 | [`RootRecoveryManager`](../../app/src/main/java/com/kindle/pageturner/root/RootRecoveryManager.kt) 在应用启动时扫描 `files/root_transactions`，比对当前文件哈希、备份哈希与预期哈希，置于 `RECOVERY_REQUIRED` 并锁定写入 |
| **验证方法** | 改造前：仅看 `su -c id` 是否包含 `uid=0`，未验证文件写入结果 | **高**：将 Root 授权成功等同于修改成功 | 修改与恢复后必须通过 [`RootFileInspector`](../../app/src/main/java/com/kindle/pageturner/root/RootFileInspector.kt) 重新读取文件计算 SHA-256 并核对 `uid/gid/mode/selinux` |

---

## 3. `/data/misc/bluetooth/bt_did.conf` 专项审计（第七阶段）

针对经典蓝牙 HID 进阶模式涉及的 `/data/misc/bluetooth/bt_did.conf`，逐项审计结论如下：

1. **该文件在目标手机上是否实际存在？**
   - **静态分析结论**：并非在所有 Android 手机上都存在。
   - 在 Android 8.0 ~ 12（API 26 ~ 32）采用传统 AOSP Fluoride (`system/bt`) 蓝牙栈的设备上，通常存在只读模板 `/system/etc/bluetooth/bt_did.conf`，而 `/data/misc/bluetooth/bt_did.conf` 默认可能不存在（或由 OEM 按需生成）。
   - 在 **Android 13+（API 33+，AOSP `packages/modules/Bluetooth` / Gabeldorsche 架构）** 中，AOSP 移除了 `bt_did.conf` 配置文件解析逻辑，改用系统属性 `bluetooth.device_id.*`（如 `bluetooth.device_id.vendor_id`、`bluetooth.device_id.product_id`）或编译期常量。
2. **文件格式是否已经被源码、设备证据或可信文档确认？**
   - **已确认（限于 AOSP Fluoride 蓝牙栈）**：AOSP `system/bt/btif/src/btif_dm.cc` 与 `system/bt/osi/src/config.cc` 使用标准 INI 键值对格式（`[DID1]`..`[DID9]` 节，支持 `primaryRecord`、`vendorId`、`vendorIdSource`、`productId`、`version` 五个键）。
   - **待实机验证**：部分深度定制 OEM ROM（如特定版本 MIUI/HyperOS、OneUI、ColorOS）可能修改了配置文件路径或字段解析逻辑。
3. **当前系统蓝牙服务是否会读取该文件，以及读取时机？**
   - 在传统 Fluoride 栈中，`btif_dm_load_local_di_record()` 在蓝牙服务 (`com.android.bluetooth`) 启动初始化 SDP 数据库时读取一次。
   - 注意：AOSP Fluoride 默认硬编码路径常量为 `/etc/bluetooth/bt_did.conf`（即 `/system/etc/bluetooth/bt_did.conf`），仅部分支持 `/data/misc/bluetooth/` 覆盖的 ROM 会优先读取 `/data/misc/bluetooth/bt_did.conf`。这一点在未连接目标实机前属于**待实机验证问题**。
4. **修改内容是否与真实目标格式匹配？**
   - [`BtDidConfValidator`](../../app/src/main/java/com/kindle/pageturner/root/BtDidConfValidator.kt) 生成的 `[DID1]` 块严格匹配 Fluoride `btif_dm.cc` 解析的字段名与 16 进制格式（`vendorIdSource = 0x0002`, `vendorId = 0x0171`, `productId = 0x0443`, `version = 0x0100`）。
5. **文件属于系统生成文件、运行时文件还是持久化配置？是否可能被覆盖？**
   - `/system/etc/bluetooth/bt_did.conf` 是编译期生成的只读系统镜像文件；`/data/misc/bluetooth/bt_did.conf` 是持久化数据分区配置文件，蓝牙守护进程正常运行时只读不写，但在系统 OTA 升级或重置网络/蓝牙设置时可能被系统清理或重置。
6. **当前手机是否支持目标 `BluetoothHidDevice` 行为？修改是否真能改变 Kindle 可观察到的 VID/PID？**
   - **静态分析与逆向证据**：Kindle `input_manager` 通过 `ioctl(fd, EVIOCGID)` 检查 Linux 内核 UHID 设备的 `vendor` 与 `product` (`0x0171:0x0443`)。
   - **待实机验证**：修改手机端 `/data/misc/bluetooth/bt_did.conf` 后，手机蓝牙栈是否在 BLE GATT DIS `0x2A50` 或 Classic Bluetooth SDP DI Record 中对外广播 `0x0171:0x0443`，必须在具体型号的 Root 手机与 Kindle 实机配对抓包确认。**严禁将“文件写入返回 0 且 SHA-256 匹配”直接等同于“实机蓝牙 VID/PID 伪装已生效”**。
7. **是否存在更安全且无需修改系统文件的替代方式？**
   - **存在，且作为默认推荐方案**：
     1. **本应用内置模式一（免 Root 标准 BLE HOGP 外设模式）**：通过应用层 `BluetoothGattServer` 直接注册 DIS 服务 (`0x180A`) 的 PnP ID 特征 (`0x2A50`)，动态返回 7 字节负载 `02 71 01 43 04 00 01` (`VID=0x0171, PID=0x0443`)，完全无需 Root，零系统文件修改。
     2. **方案 A（ESP32-C3 硬件桥接器）**：成本 <$2，免 Root、免越狱，100% 稳定通过 Kindle 白名单。
     3. **方案 C（越狱 Kindle 修改 `devices.json`）**：在已越狱 Kindle 上添加 `heuristic` 启发式规则，无需修改手机任何系统文件。
8. **修改是否可能影响系统蓝牙配对、设备识别或其他蓝牙功能？**
   - **有潜在影响**：蓝牙 DID 是手机全局身份标识。将其改为 Amazon `0x0171:0x0443` 后，车载蓝牙、智能手表、PC 或品牌耳机可能无法识别手机原本的 OEM 厂商身份。因此使用完毕后必须能够通过本应用的「恢复原始文件」功能 100% 还原。

### 3.1 代码强制落地的 `bt_did.conf` 专项保护规则

1. **未知格式拒写**：[`BtDidConfValidator.validateExistingBytes()`](../../app/src/main/java/com/kindle/pageturner/root/BtDidConfValidator.kt) 发现二进制 NUL 字节、非 `[DIDx]`/`[General]` 节或未知键名时，直接拒绝修改。
2. **无验证备份拒写**：[`RootFileTransactionManager.executeModification()`](../../app/src/main/java/com/kindle/pageturner/root/RootFileTransactionManager.kt) 在备份创建或 SHA-256 复核失败时立即中止，绝不触碰目标文件。
3. **严禁触碰无关蓝牙配置**：[`RootPathValidator`](../../app/src/main/java/com/kindle/pageturner/root/RootModels.kt) 严格锁定白名单，严禁读写 `/data/misc/bluetooth/bt_config.conf`（防止泄露或破坏蓝牙配对密钥 Link Keys）。
4. **严禁关闭 SELinux 或放宽目录权限**：代码中不含任何 `setenforce 0`、`mount -o rw,remount` 或 `chmod 777` 命令。
5. **缺失模板时标记为不支持/待验证并阻断盲目创建**：当目标系统中既无 `/data/misc/bluetooth/bt_did.conf` 又无 `/system/etc/bluetooth/bt_did.conf` 时，[`RootCapabilityChecker`](../../app/src/main/java/com/kindle/pageturner/root/RootCapabilityChecker.kt) 将状态标记为 `UNSUPPORTED_ANDROID_13_PLUS_NO_CONF` 或 `UNVERIFIED_NO_CONF_FOUND`，事务管理器默认拒绝凭空创建未经证实的系统配置文件。

---

## 4. Root 权限安全与最小权限原则审计（第八阶段）

| 审计检查项 | 审计结果与防护设计 |
| :--- | :--- |
| **1. Root 权限是否仅在必要操作时申请和使用** | **符合**。应用启动（无残留中断事务时）、主界面 BLE 模拟、打开设置页面均**不调用 `su`**。仅当用户在「Root 与系统文件管理」页面主动点击检测/备份/修改/恢复，或启动恢复检查发现存在未完成事务时才按需执行。 |
| **2. 是否存在不必要的长期 Root shell** | **符合（不存在）**。[`ProcessRootCommandExecutor`](../../app/src/main/java/com/kindle/pageturner/root/RootCommandExecutor.kt) 采用单次短生命周期命令执行（带默认 8000ms 超时与 `finally { destroyForcibly() }`），不驻留后台常驻 Root Shell。 |
| **3. 是否有命令注入、路径穿越或不安全的临时文件** | **符合（已消除）**。所有命令封装为密封类 [`RootShellCommand`](../../app/src/main/java/com/kindle/pageturner/root/RootCommandExecutor.kt)，构造时强制通过 [`RootPathValidator`](../../app/src/main/java/com/kindle/pageturner/root/RootModels.kt) 拦截相对路径、`..`、特殊字符；参数经 [`RootShellEscaper.quoteSingle()`](../../app/src/main/java/com/kindle/pageturner/root/RootCommandExecutor.kt) 转义；写入临时文件时通过 `process.outputStream` (stdin) 传输字节流，文件内容绝不拼接进 Shell 命令字符串。 |
| **4. 是否可能对非预期路径执行写入、删除或权限修改** | **符合（已阻断）**。可写白名单唯一限定为 `/data/misc/bluetooth/bt_did.conf` 及其同目录受管隐藏临时文件 `/data/misc/bluetooth/.bt_did.conf.*`。`DeleteFile` 与 `AtomicRename` 在 Shell 层额外校验 `if [ -d "$P" ] || [ -L "$P" ]; then exit 3; fi`，拒绝操作目录或符号链接。 |
| **5. 是否存在备份覆盖、路径混淆或备份与目标文件互相覆盖** | **符合（已阻断）**。备份存储于应用私有目录 `files/root_backups/`，按唯一 ID 分离存储 `.json` 与 `.bin`。当 `customBackupId` 发生冲突时立即报错拒绝覆盖既有备份。 |
| **6. 是否将敏感文件内容、认证信息或系统内部信息写入普通日志** | **符合**。普通诊断日志 (`DiagnosticLogger`) 仅记录操作动作、结果摘要与 SHA-256 哈希摘要，不输出系统文件全量内容或敏感凭证。 |
| **7. 是否存在备份清理时误删有效恢复文件的风险** | **符合（已阻断）**。`DiagnosticLogger.clear()` 仅清理内存环形诊断日志，绝不触碰 `files/root_backups/` 或 `files/root_audit/`。[`RootBackupManager.cleanupBackups()`](../../app/src/main/java/com/kindle/pageturner/root/RootBackupManager.kt) 强制保留所有未完成事务引用的备份 (`protectedBackupIds`) 以及最早的基线原始备份 (`BaselineOriginalBackup`)。 |
| **8. 是否将错误处理简化为吞掉异常或忽略退出码** | **符合（已消除）**。每次命令执行均检查 `timedOut`、`exitCode == 0`、`exceptionMessage`，并在修改/恢复后通过重新读取文件校验 SHA-256 与元数据。任何元数据恢复失败均明确标记为 `PartialRestoreMetadataWarning`。 |
| **9. 是否存在多线程同时修改同一目标文件的竞态** | **符合（已加锁）**。[`RootFileTransactionManager`](../../app/src/main/java/com/kindle/pageturner/root/RootFileTransactionManager.kt) 使用 `AtomicBoolean` 互斥守卫 (`activeOperationGuard`)，并发请求立即返回 `RejectedPrecheck`，并在 UI 执行期间禁用操作按钮防止重复点击。 |
| **10. 是否存在 Root 进程或 shell 未正确结束的问题** | **符合（已加固）**。`ProcessRootCommandExecutor.execute()` 使用独立守护线程并发排空 `stdout`/`stderr` 防止管道死锁，超时后执行 `destroy()` + `destroyForcibly()` 并在 `finally` 中关闭全部流。 |

---

## 5. 当前真机设备真实检测基准与专项排查结论

### 5.1 当前设备真实检测结果（基准事实）

以下为当前目标 Android 真机上已实际取得的检测结果，作为本工程不可篡改的事实基准：

| 检测项 | 真机实际检测结果 | 对应应用内 8 维度状态枚举 |
| :--- | :--- | :--- |
| **`su` 可执行文件** | **存在 (`suBinaryPresent = true`)** | `RootPermissionState.GRANTED_UID_0` |
| **Root 权限** | **已获取 (`UID=0`)** | `RootPermissionState.GRANTED_UID_0` |
| **SELinux** | **`Enforcing`** | `SelinuxState.ENFORCING` |
| **`/system` `bt_did.conf`** (`/system/etc/bluetooth/bt_did.conf`) | **不存在 (`exists = false`)** | `TargetFileExistenceState.BOTH_MISSING` |
| **`/data` `bt_did.conf`** (`/data/misc/bluetooth/bt_did.conf`) | **不存在 (`exists = false`)** | `TargetFileExistenceState.BOTH_MISSING` |
| **目标修改路径验证** | **未发现已知设备身份配置文件 (`MISSING_UNVERIFIED_CONFIG`)** | `TargetPathVerificationState.MISSING_UNVERIFIED_CONFIG` |
| **当前环境评估** | **经典蓝牙 VID/PID 修改方案不支持或待验证** | `SystemCompatibilityState.UNSUPPORTED_OR_PENDING_VERIFICATION` |
| **经证实可修改文件清单 (`verifiedModifiableTargets`)** | **空 (`emptyList()`)** — 当前不存在的 `bt_did.conf` 不自动纳入可修改文件清单 | `isVidPidModificationSupported = false` |

在此真机检测结果下，[`RootCapabilityChecker`](../../app/src/main/java/com/kindle/pageturner/root/RootCapabilityChecker.kt) 基于真实检测状态动态生成如下提示与结论：

> “Root 权限已获取，但当前系统未发现已知的设备身份配置文件。经典蓝牙 VID/PID 修改功能暂不可用，正在等待进一步验证。不会创建未经验证的配置文件，也不会修改系统安全策略。”

---

### 5.2 为什么原有 `bt_did.conf` 方案在当前设备上不能直接使用

1. **目标系统中既无运行时文件也无只读模板文件**：
   - 真机实测确认 `/system/etc/bluetooth/bt_did.conf` 与 `/data/misc/bluetooth/bt_did.conf` **均不存在**。
2. **Android 13+ (`packages/modules/Bluetooth` / Gabeldorsche) 协议栈架构变更**：
   - 早期 Android 8.0 ~ 11 (Fluoride `system/bt`) 在 `btif/src/btif_dm.cc` 的 `btif_dm_load_local_di_record()` 中通过 `osi/src/config.cc` 解析 `/etc/bluetooth/bt_did.conf` 来填充蓝牙 SDP Device Identification (DI, Service `0x1200`) 记录。
   - 在现代 Android 13+ (API 33+) 模块化蓝牙栈（APEX `com.android.btservices` / `packages/modules/Bluetooth`）中，AOSP 已废弃并移除 `bt_did.conf` 配置文件加载路径，改为通过系统属性 `bluetooth.device_id.vendor_id`、`bluetooth.device_id.vendor_id_source`、`bluetooth.device_id.product_id`、`bluetooth.device_id.product_version` 或编译期默认值 (`LMP_COMPID`) 提供本机 SDP DI 信息。
3. **`UID=0` 不等于凭空创建配置文件能够生效**：
   - 当系统蓝牙守护进程 (`libbluetooth_jni.so`) 根本不包含读取 `/data/misc/bluetooth/bt_did.conf` 的代码路径时，即使拥有 `UID=0` 并在 `/data/misc/bluetooth/` 下强行创建 `bt_did.conf`，蓝牙协议栈也完全不会读取该文件。
   - 相反，在 SELinux `Enforcing` 的系统数据目录中凭空创建未知文件违反安全工程原则。因此本应用严格禁止在目标配置文件不存在时自动创建未经验证的 `bt_did.conf`。

---

### 5.3 工程现有代码对经典蓝牙 VID/PID、SDP 与 HID 描述符的处理分析

1. **[`ClassicHidDeviceManager.kt`](../../app/src/main/java/com/kindle/pageturner/classic/ClassicHidDeviceManager.kt) 与 `BluetoothHidDeviceAppSdpSettings` 的真实边界**：
   - `ClassicHidDeviceManager.registerAppInternal()` 调用 Android 公开 API：
     ```kotlin
     BluetoothHidDeviceAppSdpSettings(
         "Kindle Remote",
         "Kindle Page Turner Remote",
         "Amazon",
         BluetoothHidDevice.SUBCLASS1_KEYBOARD,
         HidReportMap.KEYBOARD_REPORT_DESCRIPTOR
     )
     ```
   - **Android 框架层限制**：`BluetoothHidDeviceAppSdpSettings` 仅向底层 `IBluetoothHidDevice.registerApp()` 传递 5 个参数（`name`、`description`、`provider`、`subclass`、`descriptors`），对应 SDP HID Service (`0x1124`) 记录中的 `0x0100` (ServiceName)、`0x0101` (ServiceDescription)、`0x0102` (ProviderName)、`0x0202` (HIDDeviceSubclass) 与 `0x0206` (HIDDescriptorList)。
   - **关键缺失**：`BluetoothHidDeviceAppSdpSettings` **没有任何参数可用于设置 SDP Device Identification (DI, Service `0x1200`) 的 `VendorID` (`0x0201`)、`ProductID` (`0x0202`)、`Version` (`0x0203`) 或 `VendorIDSource` (`0x0205`)**。
   - **Kindle 端拦截原因**：根据本工程固件逆向结论（[`KINDLE_BLE_HID_REVERSE_ENGINEERING.md`](../../reports/KINDLE_BLE_HID_REVERSE_ENGINEERING.md)、[`device_identification_callgraph.md`](../reverse_engineering/device_identification_callgraph.md)、`EVID-01`/`EVID-03`），Kindle 的 `btmgr` (`libbtmgr.so`) 与 `inputd` (`input_manager`) 会读取远端设备的 `vendor_id` 与 `product_id` 并与 `/etc/bt/Input/devices.json` 中的白名单 (`VID=0x0171, PID=0x0443`) 比对。因此，仅靠 `BluetoothHidDevice` 注册成功（`ClassicHidRegistrationState.REGISTERED`），手机在经典蓝牙下仍暴露手机厂商默认 OEM VID/PID，会被 Kindle 白名单拦截。
2. **[`BleGattServerManager.kt`](../../app/src/main/java/com/kindle/pageturner/ble/BleGattServerManager.kt) 与 [`PnpIdPayload.kt`](../../app/src/main/java/com/kindle/pageturner/identity/PnpIdPayload.kt)（免 Root BLE HOGP 模式）**：
   - 与经典蓝牙不同，在 BLE HOGP 模式下，Kindle 的 `acsbtfd` / BlueZ `uhid` 通过 GATT Device Information Service (`0x180A`) 的 **PnP ID (`0x2A50`) 特征值**读取 7 字节设备身份负载：`02 71 01 43 04 00 01`（`VendorIDSource=0x02`, `VID=0x0171`, `PID=0x0443`, `Version=0x0100`）。
   - Android 的 `BluetoothGattServer` 允许普通应用在沙盒内直接注册 `0x180A` 服务并响应 `0x2A50` 读请求，因此 **BLE HOGP 模式无需 Root 即可在应用层提供精确的 `0x0171:0x0443` 身份**。

---

### 5.4 经典蓝牙与 BLE VID/PID 候选实现路径排查与严格分类表

以下对所有候选方案严格按照五级分类标准（`已证实可行` / `有证据支持，但尚未实测` / `理论可行，缺少关键证据` / `已证实不适用` / `信息不足，无法判断`）进行归类与评估：

| 候选方案编号与名称 | 严格分类标记 | 依据来源 | 适用系统范围 | 验证方式 | 潜在风险与工程决策 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **候选方案 1：仅使用 Android 公开 API `BluetoothHidDevice` + `BluetoothHidDeviceAppSdpSettings`（不修改系统）伪装经典蓝牙 VID/PID** | `已证实不适用` | 1. Android SDK `BluetoothHidDeviceAppSdpSettings` 签名与 AOSP `HidDevService.java` 源码（仅含 `name/description/provider/subclass/descriptors`，无 `vendorId/productId` 字段）<br>2. Kindle FW 5.20.1 逆向证据 `EVID-01`/`EVID-03`（`btmgr` 与 `input_manager` 强制校验 `0x0171:0x0443`） | 所有 Android 9.0+ (API 28+) | 静态 API 签名核查 + [`ClassicHidAndRootBaselineTest`](../../app/src/test/java/com/kindle/pageturner/root/ClassicHidAndRootBaselineTest.kt) 场景 5 验证 | 无系统风险，但因只注册 HID 描述符而无法改变本机 SDP DI `VendorID/ProductID`，会被未越狱 Kindle 拒绝。在应用内仅标记为 `REGISTERED` + `UNVERIFIED_DEFAULT_OEM_IDENTITY`，**绝不标记为功能已支持**。 |
| **候选方案 2：在当前目标真机上直接修改或新建 `/data/misc/bluetooth/bt_did.conf` 或 `/system/etc/bluetooth/bt_did.conf`** | `已证实不适用` | 1. 当前真机实测结果：`/system/etc/bluetooth/bt_did.conf` 与 `/data/misc/bluetooth/bt_did.conf` **均不存在**，SELinux 为 `Enforcing`<br>2. AOSP Android 13+ `packages/modules/Bluetooth` 已移除 `bt_did.conf` 解析逻辑 | 当前测试真机及所有无 `bt_did.conf` 的 Android 13+ 设备 | 真机 Root 环境检测 (`inspectEnvironment`) + 单元测试场景 1 & 7 验证 | 若盲目创建文件不仅无效，还会在 `/data/misc/bluetooth` 留下不受控残留文件。应用已**强制拦截创建并从可修改文件清单中排除**。 |
| **候选方案 3：在确实存在 `/data/misc/bluetooth/bt_did.conf` 的旧版 Fluoride ROM 上通过受管事务修改该文件** | `有证据支持，但尚未实测` | 1. AOSP Android 8~11 `system/bt/btif/src/btif_dm.cc` (`btif_dm_load_local_di_record`) 解析 `[DID1]` 的 `vendorId`/`productId`<br>2. 本工程 `RootFileTransactionManager` 8 步备份/校验/回滚事务测试通过，但尚未在存有该文件的旧款实体手机 + Kindle 上抓包实测 | 存在 `/data/misc/bluetooth/bt_did.conf` 且采用传统 Fluoride 栈的旧版 Android ROM | 在存在该文件的设备上执行 `inspectEnvironment` 确认 `VERIFIED_EXISTING_DATA_CONFIG` 后，通过蓝牙 HCI snoop log (`btsnoop_hci.log`) 检查 SDP Attribute `0x0201`/`0x0202` | 修改全局蓝牙 DID 会改变手机对车载/手表等设备的 OEM 标识；必须保留完整修改前备份并在使用后一键恢复。在未完成 SDP 实测前，`isClassicHidFeatureSupported` 仍保持 `false`。 |
| **候选方案 4：Android 13+ 通过 Root `resetprop` / `setprop` 设置 `bluetooth.device_id.vendor_id` 与 `bluetooth.device_id.product_id` 系统属性** | `理论可行，缺少关键证据` | 1. AOSP Android 13+ `packages/modules/Bluetooth/system/btif/src/btif_dm.cc` 使用 `osi_property_get_int32("bluetooth.device_id.vendor_id", ...)` 与 `bluetooth.device_id.product_id`<br>2. 缺少当前真机 ROM 的 `libbluetooth_jni.so` 符号表确认 OEM 是否覆写了该属性名，且缺少 SELinux `Enforcing` 下非破坏性重启蓝牙服务使属性生效的实测证据 | Android 13+ (API 33+) AOSP / 类原生 Gabeldorsche 蓝牙栈设备 | 需在真机上通过只读命令 `getprop \| grep bluetooth.device_id` 及 `strings /apex/com.android.btservices/lib64/libbluetooth_jni.so \| grep device_id` 采集证据（当前尚未在真机采集该项输出） | 修改系统属性若需重启蓝牙进程或使用 Magisk `resetprop`，在未经当前 ROM 实证前不得在应用内自动执行。 |
| **候选方案 5：通过 Frida / Zygisk / Binder Proxy Hook 注入 `com.android.bluetooth` 进程内存篡改 SDP DI 记录** | `理论可行，缺少关键证据` | 蓝牙守护进程在内存中调用 `SDP_SetLocalDiRecord` 注册本机 DI 记录 | 已安装 Zygisk / Frida 的 Root 设备 | 逆向目标手机 ROM 的 `libbluetooth_jni.so` 定位 `SDP_SetLocalDiRecord` 偏移并挂载 Hook | **极高风险**：侵入系统蓝牙守护进程内存极易引发 `com.android.bluetooth` 崩溃重启、触发 SELinux `execmem`/`ptrace` 拦截并破坏系统稳定性，违背安全设计原则，**不予采用**。 |
| **候选方案 6：标准免 Root BLE HOGP 外设模式（`BluetoothGattServer` 暴露 HOGP `0x1812` + DIS `0x180A` PnP ID `0x2A50`）** | `有证据支持，但尚未实测` | 1. Kindle FW 5.20.1 固件逆向证据 `EVID-01`~`EVID-07`（`acsbtfd` + `btmgr` + `input_manager` 完整调用链）<br>2. Android 标准公开 API `BluetoothGattServer` + `BluetoothLeAdvertiser`<br>3. 本工程 `PnpIdPayloadTest`、`HidReportMapTest`、`HidStateMachineTest` 已验证字节级报文匹配，待在用户实机与 Kindle 间完成首次 BLE 配对实测 | Android 8.0+ 支持 BLE 外设广播 (`isMultipleAdvertisementSupported = true`) 的所有设备（免 Root） | 在应用主界面选择「普通模式 (BLE HOGP 免 Root)」，点击「启动模拟」，在 Kindle 蓝牙设置中扫描连接 `Kindle Remote` 并点击翻页按钮 | **零系统风险**：完全运行在普通应用沙盒内，无需 Root、无需 `bt_did.conf`、不触碰 SELinux。部分手机若系统接管内置 `0x180A` 服务导致自定义 `0x2A50` 被遮蔽，则需结合抓包确认。 |
| **候选方案 7：ESP32-C3 / ESP32-S3 独立硬件 BLE 桥接方案（Scheme A）或已越狱 Kindle 修改 `/etc/bt/Input/devices.json`（Scheme C）** | `有证据支持，但尚未实测` | 1. Kindle FW 5.20.1 `/etc/bt/Input/devices.json` 逆向证据 `EVID-01`（白名单结构与 `heuristic` 启发式规则）<br>2. [`android_app_implementation_spec.md`](./android_app_implementation_spec.md) 第 2 节与第 14 节 | 任何 Android 手机（无需 Root，无需修改手机系统） | 烧录 ESP32 NimBLE 固件连接 Kindle，或在已越狱 Kindle 上配置 `devices.json` | 对 Android 手机**零系统风险**；当手机内置 BLE 外设被系统服务遮蔽且无安全经典蓝牙 DID 路径时，是最稳妥的工程方案。 |
| **候选方案 8：当前真机 OEM 专有蓝牙配置文件或 Vendor HAL 注入路径** | `信息不足，无法判断` | 目前仅完成 `/system/etc/bluetooth/bt_did.conf` 与 `/data/misc/bluetooth/bt_did.conf` 的存在性检测，尚未获取当前真机具体 OEM 厂商/机型、Android 版本号及 `/apex/com.android.btservices/` 或 `/vendor/etc/bluetooth/` 下的只读文件列表 | 特定 OEM 定制 ROM | 仅通过只读命令检查 `getprop ro.build.version.release`、`getprop \| grep bluetooth` 及 `/vendor/etc/bluetooth` 目录结构 | 在未获得确切只读证据前，严禁盲目猜测或写入任何厂商路径。 |

---

### 5.5 哪些能力当前可用，哪些能力仍待验证

1. **当前已立即可用的能力**：
   - **免 Root 标准 BLE HOGP 翻页外设模式**：包含 BLE 广播 (`Kindle Remote`, Appearance `0x03C1`, UUID `0x1812`)、GATT Server (`HOGP 0x1812`、`DIS 0x180A` 含 `PnP ID 0x2A50 = 02 71 01 43 04 00 01`、`BAS 0x180F` 电量特征)、按键防抖与 40ms/80ms 状态机 (`0x4E` 下一页 / `0x4B` 上一页 / `0x6F` 亮度增 / `0x6E` 亮度减)、物理音量键翻页、前台常驻服务与诊断日志导出。
   - **8 维度细化环境诊断与安全拦截**：按需检测 `su`、`UID=0`、SELinux、`bt_did.conf` 存在性、路径白名单、格式合法性、HID 注册状态、身份验证状态与 Kindle 接受状态，在不支持环境下零写入安全拦截。
   - **受管系统文件备份、校验、自动回滚、手动恢复与崩溃恢复基础设施**：对经证实存在且需修改的受管配置文件提供完整 8 步事务保护。
2. **当前不可用或仍待验证的能力**：
   - **当前真机上的 `bt_did.conf` 经典蓝牙 VID/PID 修改功能**：**不可用**（因真机上 `/system` 与 `/data` 均不存在 `bt_did.conf`，应用已自动禁止创建并从可修改文件清单中排除）。
   - **经典蓝牙 `BluetoothHidDevice` 模式下的 Kindle 配对识别**：**待验证 / 预计被未越狱 Kindle 拒绝**（因公开 API 无法设置 `0x0171:0x0443`，虽可完成 `REGISTERED` 注册，但设备身份处于 `UNVERIFIED_DEFAULT_OEM_IDENTITY`）。
   - **Android 13+ `bluetooth.device_id.*` 属性方案**：**待进一步只读取证与验证**（尚未在当前真机确认协议栈是否读取该属性）。

---

### 5.6 所有涉及 Root 文件修改时的备份与恢复状态记录

| 检查维度 | 当前真机与测试环境记录 |
| :--- | :--- |
| **当前真机是否执行了任何系统文件写入或新建？** | **否（0 次写入、0 次新建）**。由于真机检测 `/system/etc/bluetooth/bt_did.conf` 与 `/data/misc/bluetooth/bt_did.conf` 均不存在，应用安全预检直接拦截，未创建任何配置文件、未修改任何权限、未更改 SELinux 状态。 |
| **当前不存在的 `bt_did.conf` 是否被纳入可修改文件清单？** | **否**。`RootEnvironmentReport.verifiedModifiableTargets` 在 `/data/misc/bluetooth/bt_did.conf` 不存在时始终返回 `emptyList()`，且 `RootFileTransactionManager.executeModification()` 默认拒绝新建不存在的 `bt_did.conf`。 |
| **对经证实存在的受管文件，修改前备份与恢复状态如何验证？** | 已通过 [`RootBackupManagerTest`](../../app/src/test/java/com/kindle/pageturner/root/RootBackupManagerTest.kt)、[`RootFileTransactionModifyTest`](../../app/src/test/java/com/kindle/pageturner/root/RootFileTransactionModifyTest.kt)、[`RootFileRestoreTest`](../../app/src/test/java/com/kindle/pageturner/root/RootFileRestoreTest.kt)、[`RootCrashAndRecoveryTest`](../../app/src/test/java/com/kindle/pageturner/root/RootCrashAndRecoveryTest.kt) 及 [`ClassicHidAndRootBaselineTest`](../../app/src/test/java/com/kindle/pageturner/root/ClassicHidAndRootBaselineTest.kt) 共 **92 项单元测试**全量验证：所有经证实存在的目标文件修改均 100% 具备修改前 SHA-256 备份、写后复读校验、异常自动回滚 (`ROLLED_BACK`)、冲突检测快照恢复 (`VerifiedSuccess`) 与崩溃重启恢复检查 (`RECOVERY_REQUIRED`)。 |

---

### 5.7 测试验证结果与构建产物

- **构建与测试命令**：`.\gradlew.bat lintDebug testDebugUnitTest assembleDebug`
- **单元测试结果**：**13 个测试套件共 92 项单元测试全部通过（0 失败，0 错误）**，完整覆盖真机基线 8 大必测场景（见 [`ClassicHidAndRootBaselineTest.kt`](../../app/src/test/java/com/kindle/pageturner/root/ClassicHidAndRootBaselineTest.kt) 与 [`docs/android/root-recovery-test-report.md`](./root-recovery-test-report.md)）。
- **Android Lint 静态检查 (`lintDebug`)**：**0 errors, 0 warnings**（报告路径：`app\build\reports\lint-results-debug.html`）。
- **实际生成的 Debug APK 构建产物路径**：[`app/build/outputs/apk/debug/app-debug.apk`](../../app/build/outputs/apk/debug/app-debug.apk)

