package com.kindle.pageturner.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kindle.pageturner.R
import com.kindle.pageturner.databinding.ActivityRootManagementBinding
import com.kindle.pageturner.root.*
import com.kindle.pageturner.service.EmulationMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RootManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRootManagementBinding

    private var latestEnvReport: RootEnvironmentReport? = null
    private var isOperationInProgress: Boolean = false

    private val targetPath = ManagedTargetPath.DATA_BT_DID_CONF.canonicalPath
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRootManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        RootHelper.ensureInitialized(this)

        // Hook live transaction stage updates into UI
        RootHelper.transactionManager.onPhasePersistedHook = { txRecord ->
            runOnUiThread {
                updateLiveStageBanner(txRecord.phase, txRecord.transactionId)
            }
        }

        setupClickListeners()
        refreshAllState(inspectRootShell = false)
    }

    override fun onDestroy() {
        RootHelper.transactionManager.onPhasePersistedHook = null
        super.onDestroy()
    }

    private fun setupClickListeners() {
        binding.btnCheckRootEnv.setOnClickListener {
            refreshAllState(inspectRootShell = true)
        }

        binding.btnInspectTargetFile.setOnClickListener {
            refreshAllState(inspectRootShell = true)
        }

        binding.btnCreateBackup.setOnClickListener {
            confirmAndCreateStandaloneBackup()
        }

        binding.btnViewDiff.setOnClickListener {
            showDiffPreviewDialog()
        }

        binding.btnExecuteModify.setOnClickListener {
            confirmAndExecuteModification()
        }

        binding.btnRestoreOriginal.setOnClickListener {
            lifecycleScope.launch {
                val baseline = withContext(Dispatchers.IO) {
                    RootHelper.backupManager.getBaselineOriginalBackup(targetPath)
                        ?: RootHelper.backupManager.getLatestValidBackup(targetPath)
                }
                if (baseline == null) {
                    showInfoDialog("无法恢复", "当前没有找到任何有效的备份记录。请先检查备份列表或创建备份。")
                    return@launch
                }
                confirmAndExecuteRestore(baseline.record, forceOverwriteConflict = false)
            }
        }

        binding.btnRecoveryRestoreNow.setOnClickListener {
            lifecycleScope.launch {
                val recordToRestore = withContext(Dispatchers.IO) {
                    val pending = RootHelper.transactionManager.getUnfinishedTransactions(targetPath).firstOrNull()
                    val backupVer = pending?.let { RootHelper.backupManager.verifyBackup(it.backupId) }
                    (backupVer as? BackupVerificationResult.Valid)?.record
                        ?: RootHelper.backupManager.getBaselineOriginalBackup(targetPath)?.record
                }
                if (recordToRestore == null) {
                    showInfoDialog("无法自动定位有效备份", "未找到有效原始备份，请在备份列表中检查。")
                    return@launch
                }
                confirmAndExecuteRestore(recordToRestore, forceOverwriteConflict = false)
            }
        }

        binding.btnAcknowledgeRecovery.setOnClickListener {
            confirmAcknowledgePendingRecovery()
        }

        binding.btnExportBackup.setOnClickListener {
            confirmAndExportBackup()
        }

        binding.btnBackupList.setOnClickListener {
            showBackupListDialog()
        }

        binding.btnViewAuditLog.setOnClickListener {
            showAuditLogDialog()
        }
    }

    private fun refreshAllState(inspectRootShell: Boolean) {
        if (isOperationInProgress) return
        setOperationBusy(true, "[状态: 正在检测] 正在读取持久化记录与系统状态...")

        lifecycleScope.launch {
            val recoveryReport = withContext(Dispatchers.IO) {
                RootHelper.runStartupRecoveryCheck(this@RootManagementActivity)
            }
            val envReport = if (inspectRootShell) {
                withContext(Dispatchers.IO) {
                    RootHelper.capabilityChecker.inspectEnvironment()
                }
            } else {
                latestEnvReport ?: RootHelper.lastEnvironmentReport.value
            }
            if (envReport != null) {
                latestEnvReport = envReport
            }

            val baselineBackup = withContext(Dispatchers.IO) {
                RootHelper.backupManager.getBaselineOriginalBackup(targetPath)
            }
            val allBackups = withContext(Dispatchers.IO) {
                RootHelper.backupManager.listBackups(targetPath)
            }
            val transactions = withContext(Dispatchers.IO) {
                RootHelper.transactionManager.listTransactions(targetPath)
            }

            renderRecoveryCard(recoveryReport)
            renderEnvironmentCard(envReport)
            renderFileAndBackupCard(envReport, baselineBackup, allBackups, transactions)

            setOperationBusy(
                false,
                if (recoveryReport.hasPendingRecovery) {
                    "[状态: 待恢复处理] 存在未完成事务，已启用写入保护"
                } else {
                    "[状态: 空闲就绪] 状态刷新完成"
                }
            )
        }
    }

    private fun renderRecoveryCard(report: StartupRecoveryReport) {
        if (report.hasPendingRecovery) {
            binding.cardPendingRecovery.visibility = View.VISIBLE
            binding.tvPendingRecoveryDetails.text = report.toSummaryText()
        } else {
            binding.cardPendingRecovery.visibility = View.GONE
        }
    }

    private fun renderEnvironmentCard(env: RootEnvironmentReport?) {
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val modeStr = prefs.getString(SettingsActivity.KEY_EMULATION_MODE, EmulationMode.BLE_PERIPHERAL.name)
        val requiresRootText = if (modeStr == EmulationMode.CLASSIC_HID.name) {
            "当前为高级模式 (Classic HID)：仅在需修改已验证存在的系统配置文件时才使用 Root"
        } else {
            "当前为普通模式 (BLE HOGP)：完全免 Root 运行，无需修改任何系统文件"
        }

        if (env == null) {
            val unchecked = ClassicBluetoothFeatureAssessment.unchecked()
            binding.tvRootEnvSummary.text = buildString {
                append("• 当前模式 Root 需求: ").append(requiresRootText).append("\n")
                append("• 检测状态: [尚未执行 Root 环境检测] 请点击下方「检查 Root 环境」以按需发起检测\n\n")
                append(unchecked.toMultilineDiagnosticText())
            }
            return
        }

        val suText = if (env.suBinaryPresent) "存在" else "不存在"
        val authText = if (env.hasRootPermission) {
            "已获取 (${env.uidOutput ?: "UID=0"})"
        } else {
            "未获取 (${env.uidOutput ?: "无响应"})"
        }
        val sysExistsText = if (env.systemBtDidMetadata?.exists == true) "存在" else "不存在"
        val dataExistsText = if (env.dataBtDidMetadata?.exists == true) "存在" else "不存在"
        val assessment = env.toFeatureAssessment()

        binding.tvRootEnvSummary.text = buildString {
            append("【基础环境检测结果】\n")
            append("• su 可执行文件: ").append(suText).append("\n")
            append("• Root 权限: ").append(authText).append("\n")
            append("• SELinux: ").append(env.selinuxMode).append(" (SDK ").append(env.androidSdkInt).append(")\n")
            append("• /system bt_did.conf: ").append(sysExistsText).append("\n")
            append("• /data bt_did.conf: ").append(dataExistsText).append("\n")
            append("• 当前模式说明: ").append(requiresRootText).append("\n\n")
            append("【8 维度功能状态细化评估】\n")
            append(assessment.toMultilineDiagnosticText())
        }
    }

    private fun renderFileAndBackupCard(
        env: RootEnvironmentReport?,
        baselineValidBackup: BackupVerificationResult.Valid?,
        allBackups: List<RootBackupRecord>,
        transactions: List<RootTransactionRecord>
    ) {
        val dataMeta = env?.dataBtDidMetadata
        val sysMeta = env?.systemBtDidMetadata

        // 1. Target File Status & Verified Modifiable List
        val fileStatusSb = StringBuilder()
        fileStatusSb.append("【经证实可修改的系统文件清单】\n")
        if (env == null) {
            fileStatusSb.append("• 清单状态: [待检测] 请先点击「检查 Root 环境」\n\n")
        } else if (env.verifiedModifiableTargets.isEmpty()) {
            fileStatusSb.append("• 清单状态: [空] 当前不存在经证实可修改的配置文件 (不存在的 bt_did.conf 不自动纳入可修改文件清单)\n\n")
        } else {
            fileStatusSb.append("• 已纳入可修改清单: ")
                .append(env.verifiedModifiableTargets.joinToString { it.canonicalPath })
                .append("\n\n")
        }

        fileStatusSb.append("【候选路径检测详情】 ").append(targetPath).append("\n")
        if (dataMeta == null) {
            fileStatusSb.append("• 文件存在状态: [尚未通过 Root 读取]\n")
            fileStatusSb.append("• 当前文件 SHA-256: [待检查目标文件]\n")
        } else if (!dataMeta.exists) {
            fileStatusSb.append("• 文件存在状态: [不存在] (NOT_EXIST — 未纳入可修改清单)\n")
            fileStatusSb.append("• 最近修改时间: 无 (文件不存在)\n")
            fileStatusSb.append("• 当前文件 SHA-256: 无 (NOT_EXIST)\n")
        } else {
            val mtimeStr = dataMeta.lastModifiedEpochSec?.let { dateFormat.format(Date(it * 1000L)) } ?: "未知"
            fileStatusSb.append("• 文件存在状态: [存在] 类型=").append(dataMeta.fileType.displayName)
                .append(" (").append(dataMeta.sizeBytes).append(" 字节)\n")
            fileStatusSb.append("• 权限/属主/SELinux: ").append(dataMeta.permissionsOctal ?: "?")
                .append(" | ").append(dataMeta.ownerUser ?: dataMeta.ownerUid ?: "?")
                .append(":").append(dataMeta.groupName ?: dataMeta.groupGid ?: "?")
                .append(" | ").append(dataMeta.selinuxContext ?: "?").append("\n")
            fileStatusSb.append("• 系统文件修改时间: ").append(mtimeStr).append("\n")
            fileStatusSb.append("• 当前文件 SHA-256: ").append(dataMeta.sha256 ?: "无").append("\n")
        }

        fileStatusSb.append("\n【系统只读参考路径】 ").append(ManagedTargetPath.SYSTEM_BT_DID_CONF.canonicalPath).append("\n")
        if (sysMeta == null) {
            fileStatusSb.append("• 状态: [尚未读取] (只读参考，禁止直接修改)")
        } else if (!sysMeta.exists) {
            fileStatusSb.append("• 状态: [不存在] (当前 ROM 无 /system/etc/bluetooth/bt_did.conf)")
        } else {
            fileStatusSb.append("• 状态: [存在] 大小=").append(sysMeta.sizeBytes)
                .append("B, SHA-256=").append(sysMeta.sha256?.take(24) ?: "?").append("...")
        }
        binding.tvTargetFileStatus.text = fileStatusSb.toString()

        // 2. Backup & Transaction Status
        val lastTx = transactions.firstOrNull()
        val lastModTx = transactions.firstOrNull { it.operationType == TransactionOperationType.MODIFY_FILE }
        val unfinishedList = transactions.filter { !it.phase.isTerminal && it.recoveryResolution == null }

        val matchesOriginalText = when {
            dataMeta == null -> "[待核实] 需先点击「检查目标文件」读取当前状态"
            baselineValidBackup == null -> "[无基准备份可比对] 尚未创建原始备份"
            !baselineValidBackup.record.originalExisted && !dataMeta.exists -> "[一致] 原始状态与当前状态均为“文件不存在”"
            baselineValidBackup.record.originalExisted && dataMeta.exists &&
                dataMeta.sha256 == baselineValidBackup.record.contentSha256 ->
                "[一致] 当前文件 SHA-256 与原始备份完全匹配"
            else -> "[不一致] 当前文件状态已偏离原始备份"
        }

        val matchesExpectedText = when {
            dataMeta == null -> "[待核实] 需先点击「检查目标文件」读取当前状态"
            dataMeta.exists && env.alreadyHasKindleDid -> "[一致] 当前文件包含有效 Amazon Kindle Remote (0x0171:0x0443) 配置"
            lastModTx?.expectedAfterSha256 != null && dataMeta.sha256 == lastModTx.expectedAfterSha256 ->
                "[一致] 当前文件 SHA-256 与最近一次受管修改预期一致"
            else -> "[未修改/未注入] 当前未对系统文件执行修改"
        }

        val canSafelyRestore = baselineValidBackup != null && (env?.hasRootPermission == true) &&
            (env.dataDirWriteStatus == DirectoryWriteStatus.WRITABLE)
        val canSafelyRestoreText = when {
            baselineValidBackup == null -> "[无需恢复] 尚无系统文件修改或备份记录"
            env == null -> "[待检查 Root] 存在有效备份 (${baselineValidBackup.record.backupId})，请先点击「检查 Root 环境」验证权限"
            !env.hasRootPermission -> "[受阻: 缺少 Root 权限] 原始备份有效，但当前无 UID=0 权限执行恢复"
            canSafelyRestore -> "[可安全恢复] 原始备份已通过 SHA-256 与元数据完整性校验，可随时还原"
            else -> "[受阻] 目标目录不可写 (${env.dataDirWriteStatus})"
        }

        val bakSb = StringBuilder()
        bakSb.append("• 原始备份是否有效: ")
        if (baselineValidBackup != null) {
            val rec = baselineValidBackup.record
            val timeStr = dateFormat.format(Date(rec.createdAtEpochMs))
            bakSb.append("[有效 - 已通过完整性校验] (共 ").append(allBackups.size).append(" 份备份)\n")
            bakSb.append("• 原始备份 ID 与时间: ").append(rec.backupId).append(" (").append(timeStr).append(")\n")
            bakSb.append("• 原始备份状态与哈希: ")
            if (rec.originalExisted) {
                bakSb.append("原文件存在, SHA-256=").append(rec.contentSha256).append("\n")
            } else {
                bakSb.append("原文件不存在 (NOT_EXIST — 恢复时将安全删除新建文件)\n")
            }
        } else {
            bakSb.append("[暂无备份记录] (仅对经证实存在且需修改的系统文件创建备份)\n")
        }

        if (lastTx != null) {
            val txTime = dateFormat.format(Date(lastTx.updatedAtEpochMs))
            bakSb.append("• 最近一次事务记录: ").append(lastTx.transactionId)
                .append(" [").append(lastTx.operationType.displayName).append("]\n")
            bakSb.append("• 最近事务阶段与时间: ").append(lastTx.phase.displayName).append(" (").append(txTime).append(")\n")
            bakSb.append("• 最近修改目标后哈希: ").append(lastModTx?.expectedAfterSha256 ?: "无").append("\n")
        } else {
            bakSb.append("• 最近一次修改记录: 暂无修改事务历史 (未改动任何系统文件)\n")
            bakSb.append("• 修改后文件哈希: 无\n")
        }

        bakSb.append("• 当前文件是否与原始备份一致: ").append(matchesOriginalText).append("\n")
        bakSb.append("• 当前文件是否与预期修改一致: ").append(matchesExpectedText).append("\n")
        bakSb.append("• 是否存在未完成事务: ").append(
            if (unfinishedList.isEmpty()) "[无] 所有历史事务均已终态闭环"
            else "[存在 ${unfinishedList.size} 项未完成事务] 需恢复或确认后才允许新写入"
        ).append("\n")
        bakSb.append("• 当前是否可以安全恢复: ").append(canSafelyRestoreText)

        binding.tvBackupAndTxStatus.text = bakSb.toString()
    }

    private fun confirmAndCreateStandaloneBackup() {
        if (isOperationInProgress) return
        setOperationBusy(true, "[阶段: 正在检查可备份目标] 正在核实目标系统文件是否存在...")

        lifecycleScope.launch {
            val env = withContext(Dispatchers.IO) {
                RootHelper.capabilityChecker.inspectEnvironment()
            }
            latestEnvReport = env
            if (!env.hasRootPermission) {
                setOperationBusy(false, "[状态: 失败] 未获得 Root 权限，无法读取受保护系统目录创建备份")
                binding.tvOperationResultDetail.text = "错误原因：未获得 Root (UID=0) 授权，无法访问 $targetPath。"
                renderEnvironmentCard(env)
                return@launch
            }

            if (env.verifiedModifiableTargets.isEmpty()) {
                setOperationBusy(false, "[状态: 已跳过] 当前不存在已验证的可修改系统文件")
                binding.tvOperationResultDetail.text = buildString {
                    append("[未创建备份 — 目标配置文件不存在]\n")
                    append(env.summaryMessage).append("\n")
                    append("说明：安全备份机制仅适用于经证实存在且确实需要修改的系统文件；当前不存在的 bt_did.conf 不自动纳入可修改文件清单。")
                }
                renderEnvironmentCard(env)
                return@launch
            }

            val res = withContext(Dispatchers.IO) {
                RootHelper.backupManager.createAndVerifyBackup(
                    targetPath = targetPath,
                    transactionId = "standalone_${System.currentTimeMillis()}",
                    operationId = "op_manual_backup_${System.currentTimeMillis()}",
                    isPreRestoreSnapshot = false,
                    note = "用户手动创建的独立备份"
                )
            }

            if (res.isSuccess) {
                val rec = res.getOrThrow()
                binding.tvOperationResultDetail.text = buildString {
                    append("[备份创建并验证成功]\n")
                    append("• 备份 ID: ${rec.backupId}\n")
                    append("• 原始状态: ${if (rec.originalExisted) "文件存在 (${rec.originalMetadata.sizeBytes}B)" else "文件不存在 (NOT_EXIST)"}\n")
                    append("• 原始 SHA-256: ${rec.contentSha256 ?: "NOT_EXIST"}\n")
                    append("• 记录校验和: ${rec.recordChecksumSha256.take(24)}...")
                }
                setOperationBusy(false, "[状态: 备份已验证] 独立备份创建成功")
            } else {
                val err = res.exceptionOrNull()?.message ?: "未知错误"
                binding.tvOperationResultDetail.text = "[备份创建失败] 原因：$err"
                setOperationBusy(false, "[状态: 备份失败] 请查看详细错误信息")
            }
            refreshAllState(inspectRootShell = true)
        }
    }

    private fun showDiffPreviewDialog() {
        if (isOperationInProgress) return
        lifecycleScope.launch {
            val env = withContext(Dispatchers.IO) {
                latestEnvReport ?: RootHelper.capabilityChecker.inspectEnvironment().also { latestEnvReport = it }
            }
            if (env.verifiedModifiableTargets.isEmpty()) {
                showInfoDialog(
                    "无可用修改差异 (目标文件不存在或未验证)",
                    "${env.summaryMessage}\n\n当前系统不存在经证实可修改的 bt_did.conf 配置文件，不会自动纳入可修改文件清单，也不会创建未经验证的配置文件。"
                )
                return@launch
            }
            val currentContent = env.dataBtDidContent
            val baseContentForPatch = env.dataBtDidContent ?: env.systemBtDidContent
            val existed = env.dataBtDidMetadata?.exists == true
            val patchedRes = BtDidConfValidator.buildPatchedDidContent(baseContentForPatch)
            if (patchedRes.isFailure) {
                showInfoDialog(
                    "无法生成差异 (格式校验拦截)",
                    "现有文件格式未知或不合法，根据专项保护规则拒绝生成覆盖补丁：\n${patchedRes.exceptionOrNull()?.message}"
                )
                return@launch
            }
            val diffText = BtDidConfValidator.generateUnifiedDiff(
                originalExists = existed,
                originalContent = currentContent,
                newContent = patchedRes.getOrThrow(),
                targetPath = targetPath
            )
            showInfoDialog("修改差异预览 (Unified Diff)", diffText)
        }
    }

    private fun confirmAndExecuteModification() {
        if (isOperationInProgress) return
        lifecycleScope.launch {
            val env = withContext(Dispatchers.IO) {
                RootHelper.capabilityChecker.inspectEnvironment()
            }
            latestEnvReport = env
            renderEnvironmentCard(env)

            if (!env.hasRootPermission) {
                showInfoDialog("无法执行修改", "当前未获取 Root (UID=0) 授权，无法执行系统文件事务。")
                return@launch
            }
            val assessment = env.toFeatureAssessment()
            if (env.verifiedModifiableTargets.isEmpty() || !assessment.isVidPidModificationSupported) {
                showInfoDialog(
                    "当前系统环境暂不可用或禁止修改",
                    "${env.summaryMessage}\n\n" +
                        "原因说明：安全备份与修改事务仅适用于经证实存在、路径固定、权限明确且修改方式可验证的系统文件。当前不存在的 bt_did.conf 不会自动纳入可修改文件清单，请使用主界面的免 Root 标准 BLE 外设模式。"
                )
                return@launch
            }

            val baseContentForPatch = env.dataBtDidContent
            val patchedRes = BtDidConfValidator.buildPatchedDidContent(baseContentForPatch)
            if (patchedRes.isFailure) {
                showInfoDialog("格式校验未通过", patchedRes.exceptionOrNull()?.message ?: "目标文件格式异常")
                return@launch
            }
            val newContent = patchedRes.getOrThrow()
            val diff = BtDidConfValidator.generateUnifiedDiff(
                originalExists = env.dataBtDidMetadata?.exists == true,
                originalContent = env.dataBtDidContent,
                newContent = newContent,
                targetPath = targetPath
            )

            val confirmMsg = buildString {
                append("即将对已验证存在的受管系统文件执行事务性修改，请仔细核对：\n\n")
                append("• 目标路径: $targetPath\n")
                append("• 当前状态: 存在 (SHA-256=${env.dataBtDidMetadata?.sha256?.take(16)}...)\n")
                append("• 强制保护: 修改前自动创建完整备份并验证 SHA-256；若备份失败则立即中止。\n")
                append("• 写入方式: 临时文件写盘校验 -> 恢复属主/权限/SELinux -> 原子重命名 (mv -f) -> 复读校验。\n")
                append("• 风险提示: 修改全局蓝牙 DID 可能影响手机与其他蓝牙设备的厂商识别；本操作绝不自动杀死蓝牙进程，也不会关闭 SELinux。\n\n")
                append("【变更差异摘要】\n").append(diff)
            }

            AlertDialog.Builder(this@RootManagementActivity)
                .setTitle("二次确认：执行受管理的系统文件修改")
                .setMessage(confirmMsg)
                .setPositiveButton("确认备份并执行修改") { _, _ ->
                    runModificationTransaction(
                        newContentBytes = newContent.toByteArray(Charsets.UTF_8),
                        expectedBeforeSha256 = env.dataBtDidMetadata?.sha256,
                        expectedBeforeExists = env.dataBtDidMetadata?.exists
                    )
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun runModificationTransaction(
        newContentBytes: ByteArray,
        expectedBeforeSha256: String?,
        expectedBeforeExists: Boolean?
    ) {
        if (isOperationInProgress) return
        setOperationBusy(true, "[阶段: PREPARED] 正在准备事务并创建强制备份...")

        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                RootHelper.transactionManager.executeModification(
                    targetPath = targetPath,
                    newContentBytes = newContentBytes,
                    userConfirmed = true,
                    expectedBeforeSha256 = expectedBeforeSha256,
                    expectedBeforeExists = expectedBeforeExists,
                    allowCreateWhenTemplateMissing = false
                )
            }

            when (outcome) {
                is ModificationOutcome.Committed -> {
                    binding.tvOperationResultDetail.text = buildString {
                        append("[修改成功 - 事务已提交 (COMMITTED)]\n")
                        append("• 事务 ID: ${outcome.transaction.transactionId}\n")
                        append("• 关联备份 ID: ${outcome.backup.backupId}\n")
                        append("• 修改前状态: ${if (outcome.backup.originalExisted) "存在 (SHA=${outcome.backup.contentSha256?.take(16)}...)" else "不存在 (NOT_EXIST)"}\n")
                        append("• 修改后验证 SHA-256: ${outcome.afterMetadata.sha256}\n")
                        append("• 修改后权限与 SELinux: ${outcome.afterMetadata.permissionsOctal} | ${outcome.afterMetadata.selinuxContext}\n")
                        append("• 提示: 请点击「导出备份」保存外部副本；如需蓝牙服务重新加载配置，请在系统设置中手动开关蓝牙。")
                    }
                    setOperationBusy(false, "[状态: COMMITTED] 修改并验证成功")
                }
                is ModificationOutcome.RolledBack -> {
                    binding.tvOperationResultDetail.text = buildString {
                        append("[修改失败 - 已自动安全回滚至原始状态 (ROLLED_BACK)]\n")
                        append("• 事务 ID: ${outcome.transaction.transactionId}\n")
                        append("• 失败原因: ${outcome.reason}\n")
                        append("• 回滚验证: 已确认目标文件恢复为备份 ${outcome.backup.backupId} 的原始状态。")
                    }
                    setOperationBusy(false, "[状态: ROLLED_BACK] 修改未完成，已自动回滚")
                }
                is ModificationOutcome.RecoveryRequired -> {
                    binding.tvOperationResultDetail.text = buildString {
                        append("[严重告警 - 待恢复处理 (RECOVERY_REQUIRED)]\n")
                        append("• 事务 ID: ${outcome.transaction.transactionId}\n")
                        append("• 错误原因: ${outcome.reason}\n")
                        append("• 保护措施: 已禁止后续写入并保留原始备份 (${outcome.backup?.backupId ?: "无"})，请检查后手动恢复。")
                    }
                    setOperationBusy(false, "[状态: RECOVERY_REQUIRED] 需人工介入恢复")
                }
                is ModificationOutcome.RejectedPrecheck -> {
                    binding.tvOperationResultDetail.text = "[修改前检查未通过 - 未改动任何系统文件]\n原因：${outcome.reason}"
                    setOperationBusy(false, "[状态: 已拦截] 修改前安全检查未通过")
                }
            }
            refreshAllState(inspectRootShell = true)
        }
    }

    private fun confirmAndExecuteRestore(backup: RootBackupRecord, forceOverwriteConflict: Boolean) {
        if (isOperationInProgress) return
        val backupTime = dateFormat.format(Date(backup.createdAtEpochMs))
        val actionDesc = if (backup.originalExisted) {
            "通过临时文件原子替换还原原始文件内容 (${backup.originalMetadata.sizeBytes} 字节)，并恢复权限 (${backup.originalMetadata.permissionsOctal})、属主 (${backup.originalMetadata.ownerUid}:${backup.originalMetadata.groupGid}) 与 SELinux 上下文 (${backup.originalMetadata.selinuxContext})。"
        } else {
            "原始状态为“文件不存在 (NOT_EXIST)”：在确认安全并创建当前状态保护快照后，将删除本次受管事务创建的文件，而不会留下一份空文件。"
        }

        val message = buildString {
            append("即将执行系统文件恢复操作，请确认以下信息：\n\n")
            append("• 目标路径: ${backup.targetPath}\n")
            append("• 选用备份 ID: ${backup.backupId}\n")
            append("• 备份创建时间: $backupTime\n")
            append("• 原始文件状态: ${if (backup.originalExisted) "存在 (原始 SHA-256: ${backup.contentSha256})" else "不存在 (NOT_EXIST)"}\n")
            append("• 即将执行的操作: $actionDesc\n")
            if (forceOverwriteConflict) {
                append("\n【注意】您已选择强制覆盖外部修改冲突，恢复前仍会自动保存当前文件的保护快照。")
            }
        }

        AlertDialog.Builder(this)
            .setTitle("二次确认：恢复原始系统文件")
            .setMessage(message)
            .setPositiveButton("确认执行恢复") { _, _ ->
                runRestoreTransaction(backup.backupId, forceOverwriteConflict)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun runRestoreTransaction(backupId: String, forceOverwriteConflict: Boolean) {
        if (isOperationInProgress) return
        setOperationBusy(true, "[阶段: 正在恢复] 正在校验备份、创建恢复前保护快照并还原...")

        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                RootHelper.transactionManager.restoreFromBackup(
                    backupId = backupId,
                    userConfirmed = true,
                    forceOverwriteConflict = forceOverwriteConflict
                )
            }

            when (outcome) {
                is RestoreOutcome.VerifiedSuccess -> {
                    binding.tvOperationResultDetail.text = buildString {
                        append("[恢复已验证 (RESTORE_VERIFIED)]\n")
                        append("• 恢复事务 ID: ${outcome.transaction.transactionId}\n")
                        append("• 恢复源备份 ID: ${outcome.restoredBackup.backupId}\n")
                        append("• 恢复前保护快照 ID: ${outcome.preRestoreSnapshot?.backupId ?: "无需快照(原文件不存在)"}\n")
                        if (outcome.restoredBackup.originalExisted) {
                            append("• 恢复后验证 SHA-256: ${outcome.afterMetadata.sha256}\n")
                            append("• 恢复后元数据: ${outcome.afterMetadata.permissionsOctal} | ${outcome.afterMetadata.ownerUid}:${outcome.afterMetadata.groupGid} | ${outcome.afterMetadata.selinuxContext}")
                        } else {
                            append("• 恢复后状态: 已确认文件已安全删除，恢复至原始“不存在 (NOT_EXIST)”状态。")
                        }
                    }
                    setOperationBusy(false, "[状态: 恢复已验证] 原始状态已 100% 还原")
                }
                is RestoreOutcome.PartialRestoreMetadataWarning -> {
                    binding.tvOperationResultDetail.text = buildString {
                        append("[部分恢复警告 (PARTIAL_RESTORE)]\n")
                        append("• 警告详情: ${outcome.metadataWarning}\n")
                        append("• 恢复后文件 SHA-256: ${outcome.afterMetadata.sha256}\n")
                        append("• 恢复前保护快照 ID: ${outcome.preRestoreSnapshot?.backupId ?: "无"}\n")
                        append("• 说明: 文件内容已还原，但因某项元数据未能完全恢复，系统未将其标记为完全恢复成功。")
                    }
                    setOperationBusy(false, "[状态: 部分恢复] 内容已还原但元数据存在差异")
                }
                is RestoreOutcome.RestoredWithLogWriteFailure -> {
                    binding.tvOperationResultDetail.text = buildString {
                        append("[文件恢复已验证，但日志写入告警]\n")
                        append("• 详情: ${outcome.logWriteError}\n")
                        append("• 目标文件状态: ${outcome.afterMetadata.toSummaryString()}")
                    }
                    setOperationBusy(false, "[状态: 文件已恢复(日志告警)]")
                }
                is RestoreOutcome.ConflictDetected -> {
                    setOperationBusy(false, "[状态: 检测到冲突] 等待用户确认是否覆盖第三方修改")
                    binding.tvOperationResultDetail.text = "[恢复暂停：检测到外部修改冲突]\n${outcome.conflictExplanation}"
                    AlertDialog.Builder(this@RootManagementActivity)
                        .setTitle("检测到外部修改冲突")
                        .setMessage(outcome.conflictExplanation)
                        .setPositiveButton("创建快照并强制恢复") { _, _ ->
                            confirmAndExecuteRestore(outcome.backup, forceOverwriteConflict = true)
                        }
                        .setNegativeButton("取消恢复 (保留当前外部修改)", null)
                        .show()
                    return@launch
                }
                is RestoreOutcome.RejectedPrecheck -> {
                    binding.tvOperationResultDetail.text = "[恢复前检查未通过]\n原因：${outcome.reason}"
                    setOperationBusy(false, "[状态: 恢复被拒绝] 校验未通过")
                }
                is RestoreOutcome.FailedRecoveryRequired -> {
                    binding.tvOperationResultDetail.text = buildString {
                        append("[恢复失败 - 原始备份与保护快照已完整保留]\n")
                        append("• 失败原因: ${outcome.reason}\n")
                        append("• 原始备份 ID (未覆盖): ${outcome.restoredBackup.backupId}\n")
                        append("• 恢复前保护快照 ID: ${outcome.preRestoreSnapshot?.backupId ?: "无"}")
                    }
                    setOperationBusy(false, "[状态: 恢复失败 (待恢复)] 原始备份已保留")
                }
            }
            refreshAllState(inspectRootShell = true)
        }
    }

    private fun confirmAcknowledgePendingRecovery() {
        lifecycleScope.launch {
            val unfinished = withContext(Dispatchers.IO) {
                RootHelper.transactionManager.getUnfinishedTransactions(targetPath)
            }
            if (unfinished.isEmpty()) return@launch

            AlertDialog.Builder(this@RootManagementActivity)
                .setTitle("确认当前文件状态并解除待恢复锁定")
                .setMessage(
                    "仅当您已核实当前目标文件状态安全（例如文件仍处于原始状态，或您希望保留当前状态）时，才应标记待恢复事务为已处理。\n\n" +
                        "如果您希望将系统文件还原至修改前状态，请点击「取消」并使用「恢复原始状态」按钮。"
                )
                .setPositiveButton("确认标记为已处理") { _, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            for (tx in unfinished) {
                                RootHelper.transactionManager.markTransactionResolvedByUser(
                                    tx.transactionId,
                                    "USER_ACKNOWLEDGED_CURRENT_STATE"
                                )
                            }
                        }
                        refreshAllState(inspectRootShell = true)
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun confirmAndExportBackup() {
        lifecycleScope.launch {
            val baseline = withContext(Dispatchers.IO) {
                RootHelper.backupManager.getBaselineOriginalBackup(targetPath)
                    ?: RootHelper.backupManager.getLatestValidBackup(targetPath)
            }
            if (baseline == null) {
                showInfoDialog("无可导出的有效备份", "当前尚未创建有效的备份记录，请先点击「创建独立备份」。")
                return@launch
            }

            AlertDialog.Builder(this@RootManagementActivity)
                .setTitle("导出独立备份安全提示")
                .setMessage(
                    "即将导出备份 ${baseline.record.backupId}。\n\n" +
                        "【安全性与访问风险说明】\n" +
                        "1. 导出的备份包包含目标系统文件 (${baseline.record.targetPath}) 的原始内容 Base64、SHA-256 哈希及属主/权限/SELinux 元数据。\n" +
                        "2. 导出到剪贴板或外部存储后，其他拥有文件读取或剪贴板访问权限的应用可能读取该内容。\n" +
                        "3. 请将导出的备份保存在受信任的个人私密目录中，切勿允许未知程序篡改导出文件。"
                )
                .setPositiveButton("知悉风险并导出") { _, _ ->
                    lifecycleScope.launch {
                        val bundleRes = withContext(Dispatchers.IO) {
                            RootHelper.backupManager.exportBackupBundle(baseline.record.backupId)
                        }
                        if (bundleRes.isFailure) {
                            showInfoDialog("导出失败", bundleRes.exceptionOrNull()?.message ?: "未知错误")
                        } else {
                            val bundleText = bundleRes.getOrThrow()
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("KindleRootBackup", bundleText))

                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, bundleText)
                                type = "text/plain"
                            }
                            startActivity(Intent.createChooser(sendIntent, "导出 Root 备份副本 (已复制到剪贴板)"))
                        }
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun showBackupListDialog() {
        lifecycleScope.launch {
            val backups = withContext(Dispatchers.IO) {
                RootHelper.backupManager.listBackups(targetPath)
            }
            if (backups.isEmpty()) {
                showInfoDialog("备份列表", "当前没有任何备份记录。")
                return@launch
            }

            val items = withContext(Dispatchers.IO) {
                backups.map { rec ->
                    val ver = RootHelper.backupManager.verifyBackup(rec.backupId)
                    val verTag = if (ver is BackupVerificationResult.Valid) "[有效]" else "[损坏/无效]"
                    val typeTag = if (rec.isPreRestoreSnapshot) "(恢复前快照)" else "(原始/独立备份)"
                    val timeStr = dateFormat.format(Date(rec.createdAtEpochMs))
                    val stateStr = if (rec.originalExisted) {
                        "存在 (${rec.originalMetadata.sizeBytes}B, SHA=${rec.contentSha256?.take(12)}...)"
                    } else {
                        "原始不存在 (NOT_EXIST)"
                    }
                    "$verTag $typeTag ${rec.backupId}\n时间: $timeStr | $stateStr"
                }.toTypedArray()
            }

            AlertDialog.Builder(this@RootManagementActivity)
                .setTitle("备份列表与详情 (点击任意项可操作)")
                .setItems(items) { _, which ->
                    val selected = backups[which]
                    showSingleBackupDetailDialog(selected)
                }
                .setPositiveButton("关闭", null)
                .show()
        }
    }

    private fun showSingleBackupDetailDialog(record: RootBackupRecord) {
        lifecycleScope.launch {
            val ver = withContext(Dispatchers.IO) {
                RootHelper.backupManager.verifyBackup(record.backupId)
            }
            val detailText = buildString {
                append("• 备份 ID: ${record.backupId}\n")
                append("• 完整性校验结果: ${if (ver is BackupVerificationResult.Valid) "通过 (VALID)" else "失败: ${(ver as BackupVerificationResult.Invalid).reason}"}\n")
                append("• 创建时间: ${dateFormat.format(Date(record.createdAtEpochMs))}\n")
                append("• 关联事务 ID: ${record.transactionId}\n")
                append("• 操作 ID: ${record.operationId}\n")
                append("• 应用版本: ${record.appVersion} (Schema v${record.schemaVersion})\n")
                append("• 目标路径: ${record.targetPath}\n")
                append("• 原始文件是否存在: ${record.originalExisted}\n")
                append("• 原始元数据: ${record.originalMetadata.toSummaryString()}\n")
                append("• 原始 SHA-256: ${record.contentSha256 ?: "NOT_EXIST"}\n")
                append("• 记录校验和: ${record.recordChecksumSha256}\n")
                append("• 备注: ${record.note.ifBlank { "无" }}")
            }

            val builder = AlertDialog.Builder(this@RootManagementActivity)
                .setTitle("备份详情: ${record.backupId}")
                .setMessage(detailText)
                .setNegativeButton("返回", null)

            if (ver is BackupVerificationResult.Valid) {
                builder.setPositiveButton("使用此备份恢复") { _, _ ->
                    confirmAndExecuteRestore(record, forceOverwriteConflict = false)
                }
            }
            builder.show()
        }
    }

    private fun showAuditLogDialog() {
        lifecycleScope.launch {
            val reportText = withContext(Dispatchers.IO) {
                RootHelper.auditLogger.exportAuditReportText()
            }
            AlertDialog.Builder(this@RootManagementActivity)
                .setTitle("Root 持久化安全审计日志")
                .setMessage(reportText)
                .setPositiveButton("复制并分享") { _, _ ->
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("KindleRootAuditLog", reportText))
                    Toast.makeText(this@RootManagementActivity, "审计日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("关闭", null)
                .show()
        }
    }

    private fun updateLiveStageBanner(phase: TransactionPhase, txId: String) {
        binding.tvLiveTransactionStage.text = "[正在执行事务 $txId] 当前阶段: ${phase.displayName}"
        val colorRes = when (phase) {
            TransactionPhase.COMMITTED -> R.color.status_green
            TransactionPhase.ROLLED_BACK, TransactionPhase.RECOVERY_REQUIRED, TransactionPhase.ROLLBACK_REQUIRED -> R.color.status_red
            else -> R.color.status_orange
        }
        binding.tvLiveTransactionStage.setTextColor(getColor(colorRes))
    }

    private fun setOperationBusy(busy: Boolean, stageMessage: String) {
        isOperationInProgress = busy
        binding.tvLiveTransactionStage.text = stageMessage
        val buttons = listOf(
            binding.btnCheckRootEnv,
            binding.btnInspectTargetFile,
            binding.btnCreateBackup,
            binding.btnViewDiff,
            binding.btnExecuteModify,
            binding.btnRestoreOriginal,
            binding.btnExportBackup,
            binding.btnBackupList,
            binding.btnViewAuditLog,
            binding.btnRecoveryRestoreNow,
            binding.btnAcknowledgeRecovery
        )
        for (btn in buttons) {
            btn.isEnabled = !busy
        }
    }

    private fun showInfoDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }
}
