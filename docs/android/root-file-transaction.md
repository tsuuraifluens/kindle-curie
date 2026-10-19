# Root 系统文件安全事务、备份与恢复架构规范 (`root-file-transaction.md`)

## 1. 架构设计目标

为彻底消除业务模块或临时脚本直接修改系统文件带来的不可逆损坏风险，本工程构建了集中式 Root 文件事务安全引擎，位于 `com.kindle.pageturner.root` 包下。所有受管理系统文件的读取、备份、修改、验证、回滚与崩溃恢复均必须经过统一通道。

### 1.1 模块职责划分

| 组件类名 | 核心职责 |
| :--- | :--- |
| [`RootModels.kt`](../../app/src/main/java/com/kindle/pageturner/root/RootModels.kt) | 定义受管路径白名单 [`ManagedTargetPath`](../../app/src/main/java/com/kindle/pageturner/root/RootModels.kt)、路径规范化校验器 [`RootPathValidator`](../../app/src/main/java/com/kindle/pageturner/root/RootModels.kt)、文件元数据 [`RootFileMetadata`](../../app/src/main/java/com/kindle/pageturner/root/RootModels.kt)、版本化备份记录 [`RootBackupRecord`](../../app/src/main/java/com/kindle/pageturner/root/RootModels.kt) 及事务记录 [`RootTransactionRecord`](../../app/src/main/java/com/kindle/pageturner/root/RootModels.kt)。 |
| [`RootCommandExecutor.kt`](../../app/src/main/java/com/kindle/pageturner/root/RootCommandExecutor.kt) | 将所有 Root Shell 操作封装为强类型密封类 [`RootShellCommand`](../../app/src/main/java/com/kindle/pageturner/root/RootCommandExecutor.kt)，由 [`ProcessRootCommandExecutor`](../../app/src/main/java/com/kindle/pageturner/root/RootCommandExecutor.kt) 执行超时控制（默认 8 秒）、并发流读取、退出码检查及进程销毁清理。 |
| [`RootFileInspector.kt`](../../app/src/main/java/com/kindle/pageturner/root/RootFileInspector.kt) | 读取目标系统文件的存在状态、真实文件类型（不跟随符号链接）、大小、权限模式、UID/GID、SELinux 上下文、SHA-256 哈希与 Base64 字节内容。 |
| [`BtDidConfValidator.kt`](../../app/src/main/java/com/kindle/pageturner/root/BtDidConfValidator.kt) | 校验 `bt_did.conf` 的 INI 语法合法性，拒绝二进制或未知格式文件，生成保留非冲突配置节的补丁内容及 Unified Diff。 |
| [`RootBackupManager.kt`](../../app/src/main/java/com/kindle/pageturner/root/RootBackupManager.kt) | 在应用私有存储 `files/root_backups/` 中创建、复核、列举、导出和受保护清理系统文件备份。 |
| [`RootFileTransactionManager.kt`](../../app/src/main/java/com/kindle/pageturner/root/RootFileTransactionManager.kt) | 统一执行修改前检查、强制备份、分阶段持久化事务日志、临时文件写后校验、原子替换、写后校验、自动回滚与冲突感知恢复。 |
| [`RootRecoveryManager.kt`](../../app/src/main/java/com/kindle/pageturner/root/RootRecoveryManager.kt) | 应用启动或进程重启后扫描未完成事务，核对目标文件当前哈希与备份/预期状态，进入待恢复保护态。 |
| [`RootAuditLogger.kt`](../../app/src/main/java/com/kindle/pageturner/root/RootAuditLogger.kt) | 将所有检测、备份、事务阶段流转和恢复事件持久化追加至 `files/root_audit/audit_log.jsonl`，与普通日志清理完全隔离。 |

---

## 2. 修改前强制备份与完整性验证机制

### 2.1 存储结构与原子写入

备份存储在应用私有内部存储 `<context.filesDir>/root_backups/` 下：

- `records/<backupId>.json`：保存元数据、原始存在标志 `originalExisted`、原始文件 SHA-256、属主 UID/GID、八进制权限模式、SELinux 上下文、事务 ID、操作 ID、Schema 版本号 (`schemaVersion = 1`) 及记录校验和 `recordChecksumSha256`。
- `blobs/<backupId>.bin`：当 `originalExisted == true` 时保存原始文件的完整字节流。

**创建与验证流程**：
1. 检查目标文件类型：仅允许 `REGULAR_FILE` 或 `NOT_EXIST`；对 `SYMLINK`、`DIRECTORY`、`OTHER_SPECIAL` 立即拒绝。
2. 若原文件存在：
   - 读取原始字节并验证其长度、SHA-256 与 `stat` 读取值一致（防止读取期间文件正在变化）。
   - 先写入 `blobs/<backupId>.bin.tmp` 并调用 `fd.sync()` 刷盘。
   - **重新从磁盘读取 `.tmp` 文件并计算 SHA-256**，与原始文件哈希比对一致后，原子重命名为 `blobs/<backupId>.bin`。
3. 若原文件不存在 (`originalExisted == false`)：
   - 明确记录 `originalExisted = false`、`fileType = NOT_EXIST`、`contentFileName = null`、`contentSha256 = null`。
4. 计算元数据记录的 `recordChecksumSha256`，写入 `records/<backupId>.json.tmp` 并 `fd.sync()`，重新解析校验通过后原子重命名为 `.json`。

---

## 3. 事务状态机与持久化日志设计

每次修改或恢复操作均在 `<context.filesDir>/root_transactions/<transactionId>.json` 中维护持久化事务记录，每次状态转换均通过 `.tmp` + `fd.sync()` + 原子重命名落盘：

```
[修改事务正常路径]
PREPARED ──► BACKUP_VERIFIED ──► APPLYING ──► APPLIED ──► VERIFYING ──► COMMITTED

[修改或验证失败路径]
APPLYING / VERIFYING ──(失败)──► ROLLBACK_REQUIRED ──► ROLLING_BACK ──(回滚验证通过)──► ROLLED_BACK
                                                            │
                                                     (回滚无法确认)
                                                            ▼
                                                    RECOVERY_REQUIRED
```

### 3.1 安全写入策略（同目录临时文件 + 原子替换）

针对 `/data/misc/bluetooth/bt_did.conf` 的修改严禁先删除原文件再写入新文件，统一采用以下步骤：

1. **写入同目录隐藏临时文件**：通过 `WriteStagingFile` 将新内容由 `stdin` 写入 `/data/misc/bluetooth/.bt_did.conf.tmp.<txId>` 并 `sync`。
2. **临时文件写后校验**：读取临时文件的大小与 SHA-256，确认与目标哈希 100% 匹配（防止磁盘满或部分写入覆盖原文件）。
3. **预设元数据**：在临时文件上执行 `chown <uid>:<gid>`、`chmod <mode>`、`chcon <selinuxContext>`。
4. **原子重命名替换**：执行 `mv -f <stagingPath> /data/misc/bluetooth/bt_did.conf && sync`（同一文件系统内 POSIX `rename` 具备原子性）。
5. **修改后复读验证 (`VERIFYING`)**：重新通过 `RootFileInspector` 读取目标文件的 `stat` 元数据、SELinux 上下文及全量字节 SHA-256，全部匹配后才标记为 `COMMITTED`。

---

## 4. 完整恢复流程与冲突保护

[`RootFileTransactionManager.restoreFromBackup()`](../../app/src/main/java/com/kindle/pageturner/root/RootFileTransactionManager.kt) 实现了独立可验证的恢复流程：

1. **备份完整性校验**：首先调用 `backupManager.verifyBackup(backupId)`，若备份文件缺失、SHA-256 不匹配、Checksum 损坏或版本不兼容，立即拒绝恢复。
2. **外部修改冲突检测**：
   - 检查当前目标文件的 SHA-256 是否既不等于原始备份哈希，也不属于本应用历史事务写入的预期哈希。
   - 若检测到文件已被第三方修改且用户未显式开启 `forceOverwriteConflict`，返回 `RestoreOutcome.ConflictDetected`，在 UI 弹出冲突对比说明并要求用户二次确认。
3. **恢复前保护快照 (`preRestoreSnapshot`)**：
   - 若当前目标文件存在且可读，恢复前自动为其创建一份经过验证的保护快照备份 (`isPreRestoreSnapshot = true`)。
4. **分类恢复执行**：
   - 若 `backup.originalExisted == true`：通过临时文件 + 原子替换恢复原始字节，并根据备份记录还原 `uid:gid`、权限模式与 SELinux 上下文。
   - 若 `backup.originalExisted == false`：在确认当前文件为普通文件且由受管事务创建（或用户已确认冲突）后，安全删除该文件 (`rm -f`)，绝不留下空文件。
5. **恢复后验证与部分恢复区分**：
   - 重新读取文件校验存在性与 SHA-256。
   - 若文件内容已 100% 还原，但某项元数据（权限/属主/SELinux）恢复失败，返回 `RestoreOutcome.PartialRestoreMetadataWarning` 并保持待检查状态，绝不谎称完全恢复成功。
   - 仅当内容与所有关键元数据全部验证通过后，才标记为 `RESTORE_VERIFIED` (`COMMITTED`)。

---

## 5. 崩溃、强制停止与重启恢复机制

应用启动时（[`KindlePageTurnerApp.onCreate()`](../../app/src/main/java/com/kindle/pageturner/KindlePageTurnerApp.kt)）及进入管理界面时，[`RootRecoveryManager.inspectStartupRecovery()`](../../app/src/main/java/com/kindle/pageturner/root/RootRecoveryManager.kt) 自动执行：

1. 清理 `root_backups/` 与 `root_transactions/` 下未提交的 `.tmp` 临时碎片文件。
2. 扫描所有处于非终态 (`PREPARED`, `BACKUP_VERIFIED`, `APPLYING`, `APPLIED`, `VERIFYING`, `ROLLBACK_REQUIRED`, `ROLLING_BACK`, `RECOVERY_REQUIRED`) 的事务。
3. **不执行任何静默写入或自动覆盖**。
4. 读取目标文件当前哈希，与关联备份及事务预期哈希比对，得出四类明确诊断：
   - `MATCHES_ORIGINAL_BACKUP`：目标文件仍处于修改前原始状态。
   - `MATCHES_EXPECTED_MODIFIED`：目标文件已完成写入，仅事务提交日志被中断。
   - `UNCERTAIN_INTERMEDIATE_OR_EXTERNAL`：目标文件处于不确定的中间状态或被外部修改。
   - `ROOT_UNAVAILABLE_UNVERIFIED`：当前无 Root 权限读取目标文件。
5. 将中断事务统一置为 `RECOVERY_REQUIRED` 并锁定后续修改操作，直到用户在 UI 中完成恢复或确认。
