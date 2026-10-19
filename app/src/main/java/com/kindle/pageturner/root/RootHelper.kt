package com.kindle.pageturner.root

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RootInspectionResult(
    val hasSuBinary: Boolean,
    val hasRootPermission: Boolean,
    val systemBtDidExists: Boolean,
    val dataBtDidExists: Boolean,
    val currentDidContent: String?,
    val selinuxMode: String = "Unknown",
    val supportStatus: BtDidSupportStatus = BtDidSupportStatus.ROOT_UNAVAILABLE,
    val summaryMessage: String = "",
    val rootPermissionState: RootPermissionState = RootPermissionState.UNCHECKED,
    val selinuxState: SelinuxState = SelinuxState.UNKNOWN,
    val targetFileExistenceState: TargetFileExistenceState = TargetFileExistenceState.UNCHECKED,
    val targetPathVerificationState: TargetPathVerificationState = TargetPathVerificationState.UNCHECKED,
    val systemCompatibilityState: SystemCompatibilityState = SystemCompatibilityState.UNCHECKED,
    val deviceIdentityVerificationState: DeviceIdentityVerificationState = DeviceIdentityVerificationState.UNVERIFIED_DEFAULT_OEM_IDENTITY,
    val kindleAcceptanceState: KindleAcceptanceState = KindleAcceptanceState.REJECTED_OR_UNVERIFIED_CLASSIC_ALLOWLIST,
    val verifiedModifiableTargets: List<ManagedTargetPath> = emptyList(),
    val isClassicHidFeatureSupported: Boolean = false,
    val featureAssessment: ClassicBluetoothFeatureAssessment = ClassicBluetoothFeatureAssessment.unchecked()
)

/**
 * Central coordinator for Root capability checking, backup management,
 * file transactions, startup crash/reboot recovery, and audit logging.
 *
 * Replaces the legacy unsafe `RootHelper` implementation with:
 * - Timeout-protected, parameter-whitelisted `ProcessRootCommandExecutor`
 * - Root-privileged file inspection via `RootFileInspector`
 * - Mandatory pre-modification backups via `RootBackupManager`
 * - Transactional writes and verification via `RootFileTransactionManager`
 * - Crash/reboot startup recovery check via `RootRecoveryManager`
 */
object RootHelper {

    @Volatile
    private var initializedStorageDir: File? = null

    lateinit var executor: RootCommandExecutor
        private set

    lateinit var inspector: RootFileInspector
        private set

    lateinit var auditLogger: RootAuditLogger
        private set

    lateinit var backupManager: RootBackupManager
        private set

    lateinit var transactionManager: RootFileTransactionManager
        private set

    lateinit var recoveryManager: RootRecoveryManager
        private set

    lateinit var capabilityChecker: RootCapabilityChecker
        private set

    private val _lastStartupRecoveryReport = MutableStateFlow<StartupRecoveryReport?>(null)
    val lastStartupRecoveryReport: StateFlow<StartupRecoveryReport?> = _lastStartupRecoveryReport.asStateFlow()

    private val _lastEnvironmentReport = MutableStateFlow<RootEnvironmentReport?>(null)
    val lastEnvironmentReport: StateFlow<RootEnvironmentReport?> = _lastEnvironmentReport.asStateFlow()

    @Synchronized
    fun ensureInitialized(context: Context) {
        val targetDir = context.applicationContext.filesDir
        if (initializedStorageDir == targetDir && ::transactionManager.isInitialized) {
            return
        }
        initializeWithDirectory(
            storageDir = targetDir,
            commandExecutor = ProcessRootCommandExecutor()
        )
    }

    @Synchronized
    fun initializeWithDirectory(
        storageDir: File,
        commandExecutor: RootCommandExecutor = ProcessRootCommandExecutor()
    ) {
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        executor = commandExecutor
        inspector = RootFileInspector(executor)
        auditLogger = RootAuditLogger(storageDir)
        backupManager = RootBackupManager(storageDir, inspector, auditLogger)
        transactionManager = RootFileTransactionManager(storageDir, executor, inspector, backupManager, auditLogger)
        recoveryManager = RootRecoveryManager(executor, inspector, backupManager, transactionManager, auditLogger)
        capabilityChecker = RootCapabilityChecker(
            executor = executor,
            inspector = inspector,
            auditLogger = auditLogger,
            onReportGenerated = { report ->
                _lastEnvironmentReport.value = report
            }
        )
        _lastEnvironmentReport.value = null
        initializedStorageDir = storageDir
    }

    /**
     * Runs startup recovery check without performing any automatic file writes.
     */
    fun runStartupRecoveryCheck(context: Context): StartupRecoveryReport {
        ensureInitialized(context)
        val report = recoveryManager.inspectStartupRecovery()
        _lastStartupRecoveryReport.value = report
        return report
    }

    /**
     * Inspects Root and Bluetooth DID environment safely using timeout-protected Root commands.
     */
    fun inspectEnvironment(
        context: Context? = null,
        registrationState: ClassicHidRegistrationState = ClassicHidRegistrationState.NOT_REGISTERED
    ): RootInspectionResult {
        if (context != null) {
            ensureInitialized(context)
        } else if (!::capabilityChecker.isInitialized) {
            val fallbackExecutor = ProcessRootCommandExecutor()
            val fallbackInspector = RootFileInspector(fallbackExecutor)
            val fallbackChecker = RootCapabilityChecker(fallbackExecutor, fallbackInspector)
            val report = fallbackChecker.inspectEnvironment()
            _lastEnvironmentReport.value = report
            return mapEnvReport(report, registrationState)
        }
        val report = capabilityChecker.inspectEnvironment()
        _lastEnvironmentReport.value = report
        return mapEnvReport(report, registrationState)
    }

    private fun mapEnvReport(
        env: RootEnvironmentReport,
        registrationState: ClassicHidRegistrationState = ClassicHidRegistrationState.NOT_REGISTERED
    ): RootInspectionResult {
        val assessment = env.toFeatureAssessment(registrationState)
        return RootInspectionResult(
            hasSuBinary = env.suBinaryPresent,
            hasRootPermission = env.hasRootPermission,
            systemBtDidExists = env.systemBtDidMetadata?.exists == true,
            dataBtDidExists = env.dataBtDidMetadata?.exists == true,
            currentDidContent = env.dataBtDidContent ?: env.systemBtDidContent,
            selinuxMode = env.selinuxMode,
            supportStatus = env.btDidSupportStatus,
            summaryMessage = env.summaryMessage,
            rootPermissionState = env.rootPermissionState,
            selinuxState = env.selinuxState,
            targetFileExistenceState = env.targetFileExistenceState,
            targetPathVerificationState = env.targetPathVerificationState,
            systemCompatibilityState = env.systemCompatibilityState,
            deviceIdentityVerificationState = env.deviceIdentityVerificationState,
            kindleAcceptanceState = env.kindleAcceptanceState,
            verifiedModifiableTargets = env.verifiedModifiableTargets,
            isClassicHidFeatureSupported = assessment.isClassicHidFeatureSupported,
            featureAssessment = assessment
        )
    }

    fun getSafeDidManagementGuidance(): String {
        return """
        # --- Amazon Kindle Remote DID 受控事务管理说明 ---
        # 安全原则：
        1. 严禁将“Root 权限可用 (UID=0)”直接等同于“经典蓝牙 VID/PID 修改方案可用”。
        2. 当前不存在的 bt_did.conf 不自动纳入可修改文件清单，严禁在无系统模板或未经证实的系统中凭空创建未知配置文件。
        3. 严禁为了尝试功能而执行 setenforce 0 关闭 SELinux、放宽全局目录权限或重挂载 /system 分区。
        4. 对于经证实存在且格式合法的目标文件，必须通过「Root 与系统文件管理」页面执行修改前强制备份、SHA-256 校验、原子替换与复读验证。
        5. 当高级经典蓝牙修改不可用时，请使用免 Root 的标准 BLE HOGP 模式（GATT DIS 0x2A50 报告 0x0171:0x0443）。
        """.trimIndent()
    }
}

