# Root 文件事务、备份恢复与故障注入测试报告 (`root-recovery-test-report.md`)

## 1. 测试架构与验证边界声明

### 1.1 测试解耦架构
为确保自动化测试不修改开发机任何真实系统文件，并能精准模拟各类极端故障与进程中断场景，测试套件采用 [`VirtualFsRootCommandExecutor`](../../app/src/test/java/com/kindle/pageturner/root/VirtualFsRootCommandExecutor.kt)：

- 将 Android 受管系统路径（`/data/misc/bluetooth/bt_did.conf` 及 `/system/etc/bluetooth/bt_did.conf`）映射至 JUnit 临时目录 (`Files.createTempDirectory`)。
- 100% 运行生产级 [`RootPathValidator`](../../app/src/main/java/com/kindle/pageturner/root/RootModels.kt)、[`RootShellCommand`](../../app/src/main/java/com/kindle/pageturner/root/RootCommandExecutor.kt)、[`RootFileInspector`](../../app/src/main/java/com/kindle/pageturner/root/RootFileInspector.kt)、[`BtDidConfValidator`](../../app/src/main/java/com/kindle/pageturner/root/BtDidConfValidator.kt)、[`RootBackupManager`](../../app/src/main/java/com/kindle/pageturner/root/RootBackupManager.kt)、[`RootFileTransactionManager`](../../app/src/main/java/com/kindle/pageturner/root/RootFileTransactionManager.kt)、[`RootRecoveryManager`](../../app/src/main/java/com/kindle/pageturner/root/RootRecoveryManager.kt) 与 [`RootAuditLogger`](../../app/src/main/java/com/kindle/pageturner/root/RootAuditLogger.kt)。
- 通过 `onPhasePersistedHook`、`commandInterceptor`、`testBlobTransformHook`、`partialWriteBytesLimit`、`corruptTargetAfterAtomicMove`、`failApplyMetadataOnPathPrefix` 等注入钩子完成故障注入。

### 1.2 模拟测试与实机验证明确区分
- **已实际完成的验证**：全部 JVM 单元测试、故障注入测试、崩溃与重启状态机模拟测试、回归测试以及 Debug APK 实际编译打包。
- **未在当前构建机完成的验证（需真实 Root 手机 + Kindle 实机）**：由于当前构建环境无连接的物理 Root Android 手机 (`adb devices` 为空)，真实 `su` 弹窗交互、目标 ROM 上 `/data/misc/bluetooth` 的 SELinux `chcon` 内核策略放行情况、以及修改 `bt_did.conf` 后宿主蓝牙栈是否将 `0x0171:0x0443` 广播给 Kindle，均属于**待实机验证项**，详见 [`docs/android/root-supported-devices.md`](./root-supported-devices.md)。

---

## 2. 新增 Root 安全与故障注入测试覆盖矩阵

### 2.1 备份完整性与防篡改测试 ([`RootBackupManagerTest.kt`](../../app/src/test/java/com/kindle/pageturner/root/RootBackupManagerTest.kt))

| 测试用例 | 覆盖场景 | 预期与验证结果 |
| :--- | :--- | :--- |
| `backup when original file exists records content sha256 and metadata accurately` | 原始文件存在时正确备份 | 完整保存原文件字节、SHA-256、`0660`、`1002:1002` 与 SELinux 上下文并通过复读校验 (`PASS`) |
| `backup when original file does not exist records NOT_EXIST state without creating blob` | 原始文件不存在时正确记录不存在状态 | 记录 `originalExisted = false`、`fileType = NOT_EXIST`、`contentSha256 = null` (`PASS`) |
| `backup of empty 0-byte file succeeds and verifies sha256 of empty bytes` | 空文件备份 | 0 字节文件备份及空字节 SHA-256 (`e3b0c442...`) 校验通过 (`PASS`) |
| `backup of large file 512KB succeeds and verifies exact bytes` | 大文件备份 | >400KB 大配置文件完整备份并逐字节比对一致 (`PASS`) |
| `backup creation fails when blob write verification detects corruption` | 备份写入失败 / 写盘损坏 | 注入写盘字节污染，写后复读 SHA-256 拦截失败并清理 `.tmp`，无脏记录残留 (`PASS`) |
| `tampered backup blob on disk is detected by verifyBackup and never overwrites original hash` | 备份内容被篡改 & SHA-256 不匹配 | 篡改 `.bin` 内容后 `verifyBackup` 返回 `Invalid`，且原始记录中的 `contentSha256` 绝不被覆盖 (`PASS`) |
| `corrupted metadata json or incompatible schema version or checksum mismatch is rejected` | 元数据缺失、格式损坏、Checksum 不匹配、版本不兼容 | 篡改权限字段、`schemaVersion = 999` 或破坏 JSON 语法均被 `verifyBackup` 准确拒绝 (`PASS`) |
| `repeated backups generate distinct IDs and duplicate customBackupId collision is rejected` | 重复备份及备份 ID 冲突 | 相同 `customBackupId` 被拒绝且不覆盖已有备份；自动生成 ID 唯一不冲突 (`PASS`) |
| `cleanupBackups preserves baseline original backup and all active transaction backups` | 清理备份时保留活动事务依赖备份及基线备份 | `protectedBackupIds` 与最早基线原始备份 100% 保留，仅清理冗余历史备份 (`PASS`) |
| `getBaselineOriginalBackup and listBackups preserve exact chronological order across 12 backups in the same millisecond` | 同毫秒 12 个备份的严格时序与基线选择 | 零填充序列号确保同毫秒内 `listBackups` 严格倒序且 `getBaselineOriginalBackup` 精确返回首个备份 (`PASS`) |

### 2.2 事务修改与故障注入测试 ([`RootFileTransactionModifyTest.kt`](../../app/src/test/java/com/kindle/pageturner/root/RootFileTransactionModifyTest.kt))

| 测试用例 | 覆盖场景 | 预期与验证结果 |
| :--- | :--- | :--- |
| `normal modification creates verified backup, transitions through all phases, and commits` | 备份完成后正常修改 | 经历 `PREPARED -> BACKUP_VERIFIED -> APPLYING -> APPLIED -> VERIFYING -> COMMITTED` 全阶段并验证目标哈希 (`PASS`) |
| `modification when Root authorization fails is rejected before touching file` | Root 授权失败 | 立即返回 `RejectedPrecheck`，目标文件未改动、无脏事务 (`PASS`) |
| `modification when directory is read-only or path is read-only system partition is rejected` | 文件不可写 / `/system` 只读分区拦截 | 目录 `READ_ONLY` 或尝试写入 `/system/etc/bluetooth/bt_did.conf` 均被拦截 (`PASS`) |
| `modification when target file changed before execution is rejected` | 目标文件在操作前发生变化 | `expectedBeforeSha256` 乐观锁检测到外部并发改动并拒绝覆盖 (`PASS`) |
| `modification command failure triggers automatic rollback and preserves original file` | 修改命令返回失败 | `AtomicRename` 注入失败退出码，自动回滚至 `ROLLED_BACK`，原文件完整保持 (`PASS`) |
| `command timeout during write triggers automatic rollback` | 命令超时 | `WriteStagingFile` 注入超时 (`timedOut = true`)，自动回滚至 `ROLLED_BACK` (`PASS`) |
| `partial write to staging file is caught by hash check before atomic move and rolled back` | 写入部分内容后失败 | 临时文件仅写入前 12 字节，写后哈希检查在 `mv` 前拦截并清理临时文件、回滚事务 (`PASS`) |
| `post-write target hash mismatch triggers rollback and restores original file` | 文件哈希不符合预期 | 注入替换后内容损坏，`VERIFYING` 阶段捕获哈希不符并自动从备份还原至原始文件 (`PASS`) |
| `metadata or selinux context failure triggers rollback and restores original file` | 权限或安全上下文恢复失败 | 注入元数据未生效，`VERIFYING` 捕获元数据不符并自动回滚 (`PASS`) |
| `rollback metadata failure transitions transaction to RecoveryRequired instead of falsely claiming RolledBack` | 自动回滚期间元数据恢复失败 | 当回滚本身无法还原原始权限/SELinux 时，事务进入 `RECOVERY_REQUIRED` 并保留未完成锁，绝不谎报 `ROLLED_BACK` (`PASS`) |
| `TOCTOU external modification between precheck and backup creation aborts safely without leaving stale lock` | 预检与备份之间的 TOCTOU 外部并发篡改 | 在 `PREPARED` 与备份完成之间检测外部改动并安全中止，无残留未完成锁 (`PASS`) |
| `tampered or corrupted transaction log json is detected via checksum and surfaced as RECOVERY_REQUIRED` | 事务日志 JSON 被篡改或校验和不匹配 | `RootTransactionRecord` 校验和拦截篡改并标记为 `RECOVERY_REQUIRED` 阻断后续写入 (`PASS`) |
| `listTransactions preserves exact chronological sequence across mixed tx_mod and tx_restore in the same millisecond` | 同毫秒混合 `tx_mod`/`tx_restore` 序列排序 | 提取数值序列号比较，避免前缀字典序 (`tx_restore` > `tx_mod`) 打乱真实创建顺序 (`PASS`) |
| `concurrent modifications on the same file reject the second operation safely` | 多个操作同时修改同一个文件 | 双线程并发调用，第二个请求被互斥锁立即拒绝 (`RejectedPrecheck`)，第一个请求正常提交 (`PASS`) |
| `symlink directory special file or unknown format is rejected without modifying target` | 目标文件为符号链接、目录或未知文件格式 | `SYMLINK`、`DIRECTORY` 及未知非 INI 内容均在修改前被拦截 (`PASS`) |

### 2.3 恢复与冲突保护测试 ([`RootFileRestoreTest.kt`](../../app/src/test/java/com/kindle/pageturner/root/RootFileRestoreTest.kt))

| 测试用例 | 覆盖场景 | 预期与验证结果 |
| :--- | :--- | :--- |
| `normal restore and repeated restore verify sha256 and restore original metadata` | 正常恢复与重复恢复 | 100% 还原原文件字节与 `0640`/`1002:1002`/SELinux 元数据，连续二次恢复幂等成功 (`PASS`) |
| `restore applies original metadata to staging file before atomic rename` | 原子重命名前预先还原临时文件元数据 | 先对 `.restore` 临时文件执行 `ApplyMetadata`，再执行 `AtomicRename`，消除 `0600 root:root` 时间窗 (`PASS`) |
| `restore when original file did not exist deletes the created file instead of leaving empty file` | 原始文件不存在时正确恢复不存在状态 | 恢复后删除受管事务创建的文件，确认 `exists == false` 且 `fileType == NOT_EXIST` (`PASS`) |
| `restore is rejected when backup is corrupted and leaves target file untouched` | 备份损坏时拒绝恢复 | 篡改备份文件后拒绝恢复，当前系统文件不受破坏 (`PASS`) |
| `exception or command failure during restore preserves both original backup and pre-restore snapshot` | 恢复期间发生异常 | 恢复中断进入 `FailedRecoveryRequired`，原始备份与恢复前保护快照均完整保留 (`PASS`) |
| `hash mismatch after restore fails verification and preserves backups` | 恢复后哈希不匹配 | 恢复后校验捕获哈希不符，进入 `FailedRecoveryRequired` 并保留全部备份 (`PASS`) |
| `external modification before restore triggers ConflictDetected and only overwrites after confirmation with snapshot` | 恢复前当前文件已被外部修改 | 默认返回 `ConflictDetected` 不覆盖第三方修改；用户确认后先创建第三方状态快照再还原原始备份 (`PASS`) |
| `pre-restore snapshot of external modification does not suppress ConflictDetected when restore fails before replacing target` | 恢复前快照不污染受管哈希集合 | 强制恢复创建外部修改快照但写入失败后，再次非强制恢复仍准确触发 `ConflictDetected` (`PASS`) |
| `metadata restoration failure reports PartialRestoreMetadataWarning instead of full success` | 恢复元数据失败 | 内容恢复但元数据命令失败时返回 `PartialRestoreMetadataWarning`，且保留 `RECOVERY_REQUIRED` 未完成锁 (`PASS`) |
| `restore succeeds on file but surfaces RestoredWithLogWriteFailure when final log write fails` | 恢复成功但日志写入失败 | 目标文件成功还原并校验，同时向调用方明确报告 `RestoredWithLogWriteFailure` (`PASS`) |

### 2.4 崩溃、强制终止与设备重启恢复测试 ([`RootCrashAndRecoveryTest.kt`](../../app/src/test/java/com/kindle/pageturner/root/RootCrashAndRecoveryTest.kt))

| 阶段序号 | 模拟进程终止阶段 | 重启后 `RootRecoveryManager` 诊断结果与保护行为 | 结果 |
| :--- | :--- | :--- | :--- |
| **Stage 1** | 备份写入期间失败 (`DURING_BACKUP_WRITE`) | 自动清理 `.tmp` 碎片文件并关闭未写盘事务，原文件完好无损 | `PASS` |
| **Stage 1b** | 刚落盘 `PREPARED` 尚未完成备份即进程被杀 | 识别中断于 `PREPARED`，通过 `preModifyMetadata` 诊断为 `MATCHES_ORIGINAL_BACKUP` | `PASS` |
| **Stage 2** | 备份已完成但尚未修改 (`BACKUP_VERIFIED`) | 识别未完成事务，诊断为 `MATCHES_ORIGINAL_BACKUP`，置为 `RECOVERY_REQUIRED` 并阻断盲目再次写入 | `PASS` |
| **Stage 3** | 正在修改 (`APPLYING`，原子替换前) | 识别中断于 `APPLYING`，清理残留 Root 临时文件 (`.bt_did.conf.tmp.<txId>`)，诊断为 `MATCHES_ORIGINAL_BACKUP` | `PASS` |
| **Stage 4** | 文件已修改但尚未验证 (`APPLIED`) | 识别中断于 `APPLIED`，诊断为 `MATCHES_EXPECTED_MODIFIED`，不静默覆盖，支持一键恢复至原始备份 | `PASS` |
| **Stage 5** | 文件已验证但事务尚未提交 (`VERIFYING`) | 识别中断于 `VERIFYING`，诊断为 `MATCHES_EXPECTED_MODIFIED`，保留备份并锁定写入 | `PASS` |
| **Stage 6** | 正在回滚且目标处于中间损坏态 (`ROLLING_BACK`) | 识别中断于 `ROLLING_BACK`，诊断为 `UNCERTAIN_INTERMEDIATE_OR_EXTERNAL`，不自动覆盖，支持冲突确认后快照恢复 | `PASS` |
| **Stage 7** | 回滚已在磁盘完成但结果尚未记录 (`BEFORE_ROLLED_BACK_LOGGED`) | 识别日志停留在 `ROLLING_BACK` 但实际文件哈希与元数据均匹配原始备份 (`MATCHES_ORIGINAL_BACKUP`) | `PASS` |
| **Stage 8** | 内容 SHA-256 匹配备份但权限/属主/SELinux 元数据异常 | 联合核验内容哈希与权限/UID/GID/SELinux，准确识别为 `UNCERTAIN_INTERMEDIATE_OR_EXTERNAL` | `PASS` |
| **并发保护** | 事务执行期间并发触发 `inspectStartupRecovery()` | 识别 `isOperationInProgress` 与活动事务 ID，不误删活动 `.tmp` 文件、不劫持正在执行的事务 | `PASS` |

### 2.5 路径安全、Shell 命令与配置校验测试 ([`RootSecurityAndValidatorTest.kt`](../../app/src/test/java/com/kindle/pageturner/root/RootSecurityAndValidatorTest.kt))

| 测试用例 | 覆盖场景 | 预期与验证结果 |
| :--- | :--- | :--- |
| `RootPathValidator rejects empty relative traversal and injection paths` | 路径白名单、相对路径、`..` 穿越与命令注入字符拦截 | 拒绝所有非白名单及含特殊字符路径，禁止写入 `/system` 只读路径 (`PASS`) |
| `RootShellCommand enforces path validation in constructor and never embeds staging bytes in shell script` | 参数化命令构造与 `stdin` 字节流隔离 | 构造期强制校验路径，写入内容仅通过 `stdinPayload()` 传递 (`PASS`) |
| `BtDidConfValidator validates INI format rejects binary and preserves extra DID sections` | INI 格式校验、二进制 NUL 拦截、多 `[DIDx]` 节保留与 Unified Diff | 拒绝含 `0x00` 字节或未知节/键，保留附加 `[DID2]` 并生成标准 Diff (`PASS`) |
| `RootCapabilityChecker distinguishes Android 13+ unsupported missing config from supported Fluoride config` | Android 13+ 与 Fluoride 适用性状态分级 | 准确区分 `UNSUPPORTED_ANDROID_13_PLUS_NO_CONF`、`SUPPORTED_SYSTEM_TEMPLATE_EXISTS`、`SUPPORTED_DATA_CONF_EXISTS` 与损坏模板 (`PASS`) |
| `StatAndHashFile shell script does not depend on awk for Toybox compatibility` | Android Toybox 兼容性校验 | 使用 `stat -c '%C'` 与 POSIX 参数展开 `${SHALINE%% *}`，零 `awk` 依赖 (`PASS`) |
| `BtDidConfValidator enforces 64KB size limit strict UTF8 decoding and supports AOSP optional DI keys` | 64KB 大小上限、严格 UTF-8 解码、控制字符及 AOSP DI 键集校验 | 拒绝 >64KB、非法 UTF-8 字节与注释行控制字符，支持 AOSP `btif_dm.cc` 标准可选键 (`PASS`) |
| `ProcessRootCommandExecutor executes real OS process streams stdin and enforces timeout without deadlock` | 真实子进程并发流读取、`stdin` 异步写入与超时强杀 | 512KB `stdin` 负载在子进程挂起时不阻塞主线程，按 `timeoutMs` 准时终止并回收进程 (`PASS`) |
| `ReadFileBase64 shell script and RootFileInspector enforce maxBytes limit` | 读取系统文件大小上限 (`maxBytes`) 强制拦截 | Shell 脚本与 `readFileBytes` 均校验 `maxBytes`，超限文件返回 `FILE_TOO_LARGE` 拒绝加载 (`PASS`) |
| `buildPatchedDidContent preserves original comments and remains idempotent across repeated calls` | 原有注释保留与重复生成严格幂等性 | 保留原文件 `#` 与 `;` 注释行，且多次调用不重复追加受管头部注释 (`PASS`) |

### 2.6 真机 Root 检测基线、8 维状态拆分与零写入边界测试 ([`ClassicHidAndRootBaselineTest.kt`](../../app/src/test/java/com/kindle/pageturner/root/ClassicHidAndRootBaselineTest.kt))

| 测试用例 | 覆盖场景 | 预期与验证结果 |
| :--- | :--- | :--- |
| `scenario 1 - root available with UID 0 but target bt_did_conf files do not exist` | Root 可用 (`su` 存在、`UID=0`、`SELinux=Enforcing`)，但 `/system` 与 `/data` `bt_did.conf` 均不存在（对齐真机实测基线） | 准确判定 `GRANTED_UID_0`、`ENFORCING`、`BOTH_MISSING`、`MISSING_UNVERIFIED_CONFIG`、`UNSUPPORTED_OR_PENDING_VERIFICATION`；`verifiedModifiableTargets` 为空列表；动态生成精确提示文案 (`PASS`) |
| `scenario 2 - root available but target file has unknown or corrupted format` | Root 可用，但目标文件格式未知或损坏 | 判定 `UNKNOWN_OR_CORRUPTED_FORMAT` 与 `INCOMPATIBLE_FORMAT_OR_PERMISSION`，移出可修改列表，且拒绝事务修改并保持原文件字节不变 (`PASS`) |
| `scenario 3 - root available but target path is not in whitelist` | Root 可用，但目标路径不在白名单中（或为 `/system` 只读路径） | 判定 `NOT_IN_WHITELIST` / `READ_ONLY_SYSTEM_PARTITION`，预检直接拦截，零 Shell 命令执行 (`PASS`) |
| `scenario 4 - selinux is Enforcing and no command ever attempts to disable or relax selinux` | `SELinux` 为 `Enforcing` | 准确解析 `Enforcing`/`1` 状态，验证所有已执行 Shell 脚本绝不包含 `setenforce`、`magiskpolicy`、`supolicy` 或 `remount` (`PASS`) |
| `scenario 5 - classic bluetooth hid registration succeeds but device identity is unverified` | 经典蓝牙 HID 注册成功，但设备身份无法验证 | `ClassicHidRegistrationState.REGISTERED` 为真，但 `DeviceIdentityVerificationState` 保持 `UNVERIFIED_DEFAULT_OEM_IDENTITY`，`isFeatureSupported` 严格为 `false` (`PASS`) |
| `scenario 6 - standard BLE HOGP mode works normally when root classic hid modification is unavailable` | 在高级功能不可用时，普通功能（免 Root 标准 BLE HOGP 模式）仍可正常工作 | Root 经典蓝牙修改不可用时，`bleHogpStandardModeAvailable` 为真，GATT DIS `2A50` 载荷 (`02 71 01 43 04 00 01`)、`HidStateMachine` 翻页报告与 `BatteryServiceManager` 全部正常工作 (`PASS`) |
| `scenario 7 - unsupported environment never creates config files never disables selinux and performs zero writes` | 不支持环境下不会创建配置文件、不会关闭 SELinux、不会执行任何系统写入 | 即使传入 `allowCreateWhenTemplateMissing = true` 仍被预检拦截；`/data` 与 `/system` 均未创建文件；零写入/重命名/元数据修改命令执行 (`PASS`) |
| `scenario 8 - incomplete or unverified detection results are never evaluated or displayed as supported` | 不完整或未验证的检测结果，不会被界面显示为“功能已支持” | 覆盖初始未检测态、Root 拒绝态、仅模板存在态、以及配置文件已含 `0x0171:0x0443` 但运行态身份未验证态，确保 `isClassicHidFeatureSupported` 绝不误报为已支持 (`PASS`) |

---

## 3. 全量回归测试与构建验证结果

执行命令：`.\gradlew.bat lintDebug test assembleDebug`

- **单元测试总数**：**92 项测试（13 个测试套件）全部通过（0 失败，0 错误）**
  - Root 基线、8 维状态、安全、备份、事务、恢复与崩溃注入测试：**63 项** (`ClassicHidAndRootBaselineTest` 8 项、`RootFileTransactionModifyTest` 15 项、`RootCrashAndRecoveryTest` 11 项、`RootBackupManagerTest` 10 项、`RootFileRestoreTest` 10 项、`RootSecurityAndValidatorTest` 9 项)
  - 既有 BLE / HID / PnP ID / 电量 / 日志回归测试：**29 项** (`HidStateMachineTest` 6 项、`HidReportBuilderTest` 6 项、`HidKeyCodesTest` 5 项、`HidReportMapTest` 3 项、`PnpIdPayloadTest` 3 项、`BatteryServiceManagerTest` 3 项、`DiagnosticLoggerTest` 3 项)
- **Android Lint 检查 (`lintDebug`)**：**0 errors** (`app\build\reports\lint-results-debug.html`)
- **Debug APK 产物**：`app\build\outputs\apk\debug\app-debug.apk`

