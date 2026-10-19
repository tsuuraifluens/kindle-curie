package com.kindle.pageturner.root

import android.os.Build

/**
 * 维度 1：Root 权限状态
 */
enum class RootPermissionState(val displayName: String, val isGranted: Boolean) {
    UNCHECKED("未检测", false),
    NO_SU_BINARY("未检测到 su 可执行文件", false),
    SU_PRESENT_NOT_GRANTED("检测到 su，但未获得 Root (UID=0) 授权", false),
    GRANTED_UID_0("已获取 (UID=0)", true)
}

/**
 * 维度 2：SELinux 状态
 */
enum class SelinuxState(val displayName: String, val isKnown: Boolean) {
    UNCHECKED("未检测", false),
    UNKNOWN("未知 (Unknown)", false),
    ENFORCING("Enforcing (强制模式 - 严禁关闭或放宽策略)", true),
    PERMISSIVE("Permissive (宽容模式)", true),
    DISABLED("Disabled (已禁用)", true);

    companion object {
        fun fromRawOutput(raw: String?): SelinuxState {
            val normalized = raw?.trim()?.lowercase() ?: return UNKNOWN
            return when {
                normalized == "enforcing" || normalized == "1" -> ENFORCING
                normalized == "permissive" || normalized == "0" -> PERMISSIVE
                normalized == "disabled" -> DISABLED
                normalized.startsWith("unknown (no root)") || normalized == "unchecked" -> UNCHECKED
                else -> UNKNOWN
            }
        }
    }
}

/**
 * 维度 3：目标文件存在状态
 */
enum class TargetFileExistenceState(val displayName: String, val anyConfigExists: Boolean) {
    UNCHECKED("未检测", false),
    BOTH_MISSING("/system 与 /data 下的 bt_did.conf 均不存在", false),
    SYSTEM_TEMPLATE_ONLY("仅 /system/etc/bluetooth/bt_did.conf 存在，/data 下不存在", true),
    DATA_CONF_ONLY("仅 /data/misc/bluetooth/bt_did.conf 存在", true),
    BOTH_EXIST("/system 与 /data 下的 bt_did.conf 均存在", true),
    INVALID_NON_REGULAR_FILE("目标路径存在但非普通文件 (符号链接/目录/特殊文件)", false)
}

/**
 * 维度 4：目标修改路径是否已验证
 */
enum class TargetPathVerificationState(val displayName: String, val isVerifiedModifiable: Boolean) {
    UNCHECKED("未检测", false),
    MISSING_UNVERIFIED_CONFIG("未发现已知配置文件 (修改路径未验证，严禁自动创建)", false),
    NOT_IN_WHITELIST("目标路径不在受管白名单中 (拒绝访问)", false),
    READ_ONLY_SYSTEM_PARTITION("目标路径位于只读系统分区 (/system)，禁止写入或重挂载", false),
    UNKNOWN_OR_CORRUPTED_FORMAT("目标文件格式未知或损坏 (未通过 INI 语法与结构验证，禁止修改)", false),
    DIRECTORY_NOT_WRITABLE("目标目录 /data/misc/bluetooth 不可写或不存在", false),
    SYSTEM_TEMPLATE_ONLY_UNVERIFIED_OVERRIDE("仅存在只读系统模板，/data 下无现有配置文件 (当前不存在的 bt_did.conf 不自动纳入可修改清单)", false),
    VERIFIED_EXISTING_DATA_CONFIG("已验证 /data/misc/bluetooth/bt_did.conf 存在且格式合法", true)
}

/**
 * 维度 5：当前系统是否兼容该方案
 */
enum class SystemCompatibilityState(val displayName: String, val isCompatible: Boolean) {
    UNCHECKED("未检测", false),
    UNSUPPORTED_OR_PENDING_VERIFICATION("经典蓝牙 VID/PID 修改方案不支持或待验证", false),
    INCOMPATIBLE_FORMAT_OR_PERMISSION("目标文件格式不兼容或目录不可写", false),
    PENDING_VERIFICATION_TEMPLATE_ONLY("发现系统只读模板，但当前系统蓝牙栈是否读取 /data 覆盖文件待验证", false),
    COMPATIBLE_CONFIG_PRESENT("检测到现有 /data/misc/bluetooth/bt_did.conf 配置文件", true)
}

/**
 * 维度 6：经典蓝牙 HID 注册状态
 */
enum class ClassicHidRegistrationState(val displayName: String, val isRegistered: Boolean) {
    NOT_REGISTERED("未注册", false),
    REGISTERING("正在注册 BluetoothHidDevice...", false),
    REGISTERED("已注册 (BluetoothHidDevice SDP 已就绪，但公开 API 不支持设置 VID/PID)", true),
    REGISTRATION_FAILED("BluetoothHidDevice 注册失败", false),
    UNSUPPORTED_API("当前 Android 版本低于 API 28，不支持 BluetoothHidDevice", false)
}

/**
 * 维度 7：设备身份信息 (VID=0x0171, PID=0x0443) 是否已验证
 */
enum class DeviceIdentityVerificationState(val displayName: String, val isIdentityVerified: Boolean) {
    UNVERIFIED_DEFAULT_OEM_IDENTITY(
        "未验证 (经典蓝牙默认暴露系统 OEM 身份，无法证实为 0x0171:0x0443)",
        false
    ),
    CONFIG_PRESENT_NOT_KINDLE_DID(
        "未配置为 Kindle 身份 (bt_did.conf 存在，但当前非 0x0171:0x0443)",
        false
    ),
    CONFIG_PATCHED_RUNTIME_UNVERIFIED(
        "配置文件已含 0x0171:0x0443，但尚未通过 SDP 抓包证实蓝牙协议栈已实际加载生效",
        false
    ),
    VERIFIED_BLE_HOGP_DIS_PNP(
        "仅 BLE HOGP 已验证 (GATT DIS 0x2A50 报告 0x0171:0x0443；经典蓝牙 SDP 身份仍未验证)",
        false
    ),
    RUNTIME_VERIFIED_CLASSIC_SDP_0171_0443(
        "已实测验证 (经典蓝牙 SDP DI 0x1200 实测确认报告 0x0171:0x0443)",
        true
    )
}

/**
 * 维度 8：Kindle 是否可识别并接受该设备
 */
enum class KindleAcceptanceState(val displayName: String, val isAccepted: Boolean) {
    REJECTED_OR_UNVERIFIED_CLASSIC_ALLOWLIST(
        "不支持/待验证 (Kindle btmgr 强制校验 VID=0x0171/PID=0x0443 白名单，未验证身份的经典蓝牙 HID 会被拦截)",
        false
    ),
    PENDING_REAL_KINDLE_PAIRING_VERIFICATION(
        "待实机配对验证 (需在真机上确认 Kindle 蓝牙栈通过 SDP DI 读取到 0x0171:0x0443 并挂接 inputd)",
        false
    ),
    SUPPORTED_BLE_HOGP_MODE(
        "仅免 Root BLE HOGP 模式可识别并接受 (经典蓝牙 HID 模式仍待验证)",
        false
    ),
    ACCEPTED_CLASSIC_HID_BY_KINDLE(
        "已实测可识别并接受 (经典蓝牙 HID 已通过 Kindle 白名单并正常翻页)",
        true
    )
}

/**
 * 8 维度细化功能状态评估模型。
 *
 * 严格遵循设计原则：
 * - 将“Root 权限可用”与“目标系统能力可实现”彻底分离；
 * - 任何检测维度不完整或未验证时，绝不将经典蓝牙 HID 模式或 VID/PID 修改标记为“已支持”；
 * - 当前不存在的 `bt_did.conf` 绝不自动纳入 `verifiedModifiableTargets` 可修改文件清单。
 */
data class ClassicBluetoothFeatureAssessment(
    val rootPermissionState: RootPermissionState,
    val selinuxState: SelinuxState,
    val targetFileExistenceState: TargetFileExistenceState,
    val targetPathVerificationState: TargetPathVerificationState,
    val systemCompatibilityState: SystemCompatibilityState,
    val classicHidRegistrationState: ClassicHidRegistrationState,
    val deviceIdentityVerificationState: DeviceIdentityVerificationState,
    val kindleAcceptanceState: KindleAcceptanceState,
    val verifiedModifiableTargets: List<ManagedTargetPath>,
    val isVidPidModificationSupported: Boolean,
    val isClassicHidFeatureSupported: Boolean,
    val bleHogpStandardModeAvailable: Boolean,
    val dynamicStatusMessage: String
) {
    fun toMultilineDiagnosticText(): String = buildString {
        append("1. Root 权限状态: ").append(rootPermissionState.displayName).append("\n")
        append("2. SELinux 状态: ").append(selinuxState.displayName).append("\n")
        append("3. 目标文件存在状态: ").append(targetFileExistenceState.displayName).append("\n")
        append("4. 目标修改路径验证: ").append(targetPathVerificationState.displayName).append("\n")
        append("5. 当前系统兼容性: ").append(systemCompatibilityState.displayName).append("\n")
        append("6. 经典蓝牙 HID 注册状态: ").append(classicHidRegistrationState.displayName).append("\n")
        append("7. 设备身份信息验证: ").append(deviceIdentityVerificationState.displayName).append("\n")
        append("8. Kindle 识别与接受状态: ").append(kindleAcceptanceState.displayName).append("\n")
        append("• 可修改系统文件清单: ").append(
            if (verifiedModifiableTargets.isEmpty()) "无 (当前不存在已验证可修改的配置文件)"
            else verifiedModifiableTargets.joinToString { it.canonicalPath }
        ).append("\n")
        append("• 经典蓝牙 VID/PID 修改支持: ").append(
            if (isVidPidModificationSupported) "已验证支持" else "暂不可用 / 待验证"
        ).append("\n")
        append("• 经典蓝牙 HID 完整模式支持: ").append(
            if (isClassicHidFeatureSupported) "已支持" else "不支持 / 待验证"
        ).append("\n")
        append("• 免 Root 标准 BLE HOGP 模式: ").append(
            if (bleHogpStandardModeAvailable) "正常可用 (GATT DIS 0x2A50 报告 0x0171:0x0443)" else "不可用"
        ).append("\n")
        append("• 评估结论: ").append(dynamicStatusMessage)
    }

    companion object {
        fun unchecked(
            registrationState: ClassicHidRegistrationState = ClassicHidRegistrationState.NOT_REGISTERED
        ): ClassicBluetoothFeatureAssessment {
            return ClassicBluetoothFeatureAssessment(
                rootPermissionState = RootPermissionState.UNCHECKED,
                selinuxState = SelinuxState.UNCHECKED,
                targetFileExistenceState = TargetFileExistenceState.UNCHECKED,
                targetPathVerificationState = TargetPathVerificationState.UNCHECKED,
                systemCompatibilityState = SystemCompatibilityState.UNCHECKED,
                classicHidRegistrationState = registrationState,
                deviceIdentityVerificationState = DeviceIdentityVerificationState.UNVERIFIED_DEFAULT_OEM_IDENTITY,
                kindleAcceptanceState = KindleAcceptanceState.REJECTED_OR_UNVERIFIED_CLASSIC_ALLOWLIST,
                verifiedModifiableTargets = emptyList(),
                isVidPidModificationSupported = false,
                isClassicHidFeatureSupported = false,
                bleHogpStandardModeAvailable = true,
                dynamicStatusMessage = "环境状态尚未完整检测，经典蓝牙 VID/PID 修改功能暂不可用（未验证）。标准免 Root BLE HOGP 模式可正常使用。"
            )
        }
    }
}

enum class BtDidSupportStatus(val displayName: String, val canSafelyModify: Boolean) {
    SUPPORTED_DATA_CONF_EXISTS(
        displayName = "已检测到 /data/misc/bluetooth/bt_did.conf 存在且格式有效 (修改后生效性仍需实机验证)",
        canSafelyModify = true
    ),
    SUPPORTED_SYSTEM_TEMPLATE_EXISTS(
        displayName = "仅检测到 /system/etc/bluetooth/bt_did.conf 只读模板 (/data 下文件不存在，当前不存在的 bt_did.conf 不自动纳入可修改清单)",
        canSafelyModify = false
    ),
    UNSUPPORTED_ANDROID_13_PLUS_NO_CONF(
        displayName = "经典蓝牙 VID/PID 修改方案不支持或待验证 (Android 13+ 且系统不存在 bt_did.conf)",
        canSafelyModify = false
    ),
    UNVERIFIED_NO_CONF_FOUND(
        displayName = "经典蓝牙 VID/PID 修改方案不支持或待验证 (当前系统未发现任何 bt_did.conf 文件)",
        canSafelyModify = false
    ),
    INVALID_TARGET_FORMAT_OR_TYPE(
        displayName = "拒绝修改 (目标文件类型非普通文件或格式无法识别)",
        canSafelyModify = false
    ),
    DIRECTORY_NOT_WRITABLE(
        displayName = "无法修改 (/data/misc/bluetooth 目录不存在或只读)",
        canSafelyModify = false
    ),
    ROOT_UNAVAILABLE(
        displayName = "未就绪 (未获取 Root UID=0 授权，无法访问受保护的系统蓝牙目录)",
        canSafelyModify = false
    )
}

data class RootEnvironmentReport(
    val suBinaryPresent: Boolean,
    val hasRootPermission: Boolean,
    val uidOutput: String?,
    val selinuxMode: String,
    val androidSdkInt: Int,
    val dataBtDidMetadata: RootFileMetadata?,
    val systemBtDidMetadata: RootFileMetadata?,
    val dataBtDidContent: String?,
    val systemBtDidContent: String?,
    val dataDirWriteStatus: DirectoryWriteStatus,
    val btDidSupportStatus: BtDidSupportStatus,
    val alreadyHasKindleDid: Boolean,
    val summaryMessage: String,
    val rootPermissionState: RootPermissionState = if (hasRootPermission) RootPermissionState.GRANTED_UID_0 else if (suBinaryPresent) RootPermissionState.SU_PRESENT_NOT_GRANTED else RootPermissionState.NO_SU_BINARY,
    val selinuxState: SelinuxState = SelinuxState.fromRawOutput(selinuxMode),
    val targetFileExistenceState: TargetFileExistenceState = TargetFileExistenceState.UNCHECKED,
    val targetPathVerificationState: TargetPathVerificationState = TargetPathVerificationState.UNCHECKED,
    val systemCompatibilityState: SystemCompatibilityState = SystemCompatibilityState.UNCHECKED,
    val deviceIdentityVerificationState: DeviceIdentityVerificationState = DeviceIdentityVerificationState.UNVERIFIED_DEFAULT_OEM_IDENTITY,
    val kindleAcceptanceState: KindleAcceptanceState = KindleAcceptanceState.REJECTED_OR_UNVERIFIED_CLASSIC_ALLOWLIST,
    val verifiedModifiableTargets: List<ManagedTargetPath> = emptyList(),
    val isClassicHidFeatureSupported: Boolean = false
) {
    /**
     * Combines this environment report with the current [ClassicHidRegistrationState] to produce
     * the full 8-dimension [ClassicBluetoothFeatureAssessment].
     */
    fun toFeatureAssessment(
        registrationState: ClassicHidRegistrationState = ClassicHidRegistrationState.NOT_REGISTERED
    ): ClassicBluetoothFeatureAssessment {
        val isComplete = rootPermissionState != RootPermissionState.UNCHECKED &&
            selinuxState.isKnown &&
            targetFileExistenceState != TargetFileExistenceState.UNCHECKED &&
            targetPathVerificationState != TargetPathVerificationState.UNCHECKED &&
            systemCompatibilityState != SystemCompatibilityState.UNCHECKED

        val vidPidModSupported = isComplete &&
            rootPermissionState.isGranted &&
            targetPathVerificationState.isVerifiedModifiable &&
            systemCompatibilityState.isCompatible &&
            verifiedModifiableTargets.isNotEmpty()

        val classicHidSupported = vidPidModSupported &&
            classicHidRegistrationStateIsReady(registrationState) &&
            deviceIdentityVerificationState.isIdentityVerified &&
            kindleAcceptanceState.isAccepted

        return ClassicBluetoothFeatureAssessment(
            rootPermissionState = rootPermissionState,
            selinuxState = selinuxState,
            targetFileExistenceState = targetFileExistenceState,
            targetPathVerificationState = targetPathVerificationState,
            systemCompatibilityState = systemCompatibilityState,
            classicHidRegistrationState = registrationState,
            deviceIdentityVerificationState = deviceIdentityVerificationState,
            kindleAcceptanceState = kindleAcceptanceState,
            verifiedModifiableTargets = verifiedModifiableTargets,
            isVidPidModificationSupported = vidPidModSupported,
            isClassicHidFeatureSupported = classicHidSupported,
            bleHogpStandardModeAvailable = true,
            dynamicStatusMessage = summaryMessage
        )
    }

    private fun classicHidRegistrationStateIsReady(state: ClassicHidRegistrationState): Boolean {
        return state == ClassicHidRegistrationState.REGISTERED && state.isRegistered
    }
}

/**
 * Checks Root availability, SELinux enforcement, target file accessibility,
 * and Android Bluetooth stack `bt_did.conf` applicability across 8 explicit dimensions.
 */
class RootCapabilityChecker(
    private val executor: RootCommandExecutor,
    private val inspector: RootFileInspector,
    private val auditLogger: RootAuditLogger? = null,
    private val sdkIntProvider: () -> Int = { Build.VERSION.SDK_INT },
    private val onReportGenerated: ((RootEnvironmentReport) -> Unit)? = null
) {

    fun checkRootPermission(): Pair<Boolean, String?> {
        val res = executor.execute(RootShellCommand.CheckSuId)
        if (!res.isSuccess) {
            return false to (res.exceptionMessage ?: res.stderr.takeIf { it.isNotBlank() } ?: "exit=${res.exitCode}")
        }
        val granted = res.stdout.contains("uid=0")
        return granted to res.stdout
    }

    /**
     * Evaluates whether a candidate target path is in the managed whitelist and whether it is
     * verified modifiable on the current system without performing any writes.
     */
    fun verifyCandidateTargetPath(
        rawPath: String,
        envReport: RootEnvironmentReport? = null
    ): TargetPathVerificationState {
        val managed = RootPathValidator.validatePath(rawPath, requireWritable = false).getOrElse {
            return TargetPathVerificationState.NOT_IN_WHITELIST
        }
        if (!managed.writable) {
            return TargetPathVerificationState.READ_ONLY_SYSTEM_PARTITION
        }
        val report = envReport ?: inspectEnvironment()
        if (!report.hasRootPermission) {
            return TargetPathVerificationState.UNCHECKED
        }
        return report.targetPathVerificationState
    }

    fun inspectEnvironment(): RootEnvironmentReport {
        val suPresent = executor.isSuBinaryPresent()
        val (hasRoot, uidOut) = checkRootPermission()
        val sdkInt = try { sdkIntProvider() } catch (_: Throwable) { 34 }

        if (!hasRoot) {
            val permState = if (suPresent) {
                RootPermissionState.SU_PRESENT_NOT_GRANTED
            } else {
                RootPermissionState.NO_SU_BINARY
            }
            val msg = if (suPresent) {
                "检测到 su 二进制，但未获得 Root 授权 (UID=0)。经典蓝牙 VID/PID 修改功能不可用；普通 BLE 模式无需 Root 即可正常使用。"
            } else {
                "设备未 Root (未检测到 su)。经典蓝牙 VID/PID 修改功能不可用；推荐使用免 Root 的标准 BLE 外设模式或 ESP32 桥接方案。"
            }
            val report = RootEnvironmentReport(
                suBinaryPresent = suPresent,
                hasRootPermission = false,
                uidOutput = uidOut,
                selinuxMode = "Unknown (No Root)",
                androidSdkInt = sdkInt,
                dataBtDidMetadata = null,
                systemBtDidMetadata = null,
                dataBtDidContent = null,
                systemBtDidContent = null,
                dataDirWriteStatus = DirectoryWriteStatus.ERROR,
                btDidSupportStatus = BtDidSupportStatus.ROOT_UNAVAILABLE,
                alreadyHasKindleDid = false,
                summaryMessage = msg,
                rootPermissionState = permState,
                selinuxState = SelinuxState.UNCHECKED,
                targetFileExistenceState = TargetFileExistenceState.UNCHECKED,
                targetPathVerificationState = TargetPathVerificationState.UNCHECKED,
                systemCompatibilityState = SystemCompatibilityState.UNCHECKED,
                deviceIdentityVerificationState = DeviceIdentityVerificationState.UNVERIFIED_DEFAULT_OEM_IDENTITY,
                kindleAcceptanceState = KindleAcceptanceState.REJECTED_OR_UNVERIFIED_CLASSIC_ALLOWLIST,
                verifiedModifiableTargets = emptyList(),
                isClassicHidFeatureSupported = false
            )
            auditLogger?.record("ENV_CHECK", "INFO", report.summaryMessage)
            onReportGenerated?.invoke(report)
            return report
        }

        val selinuxRes = executor.execute(RootShellCommand.GetSelinuxEnforce)
        val selinuxMode = if (selinuxRes.isSuccess && selinuxRes.stdout.isNotBlank()) {
            selinuxRes.stdout.lines().first().trim()
        } else {
            "Unknown"
        }
        val selinuxState = SelinuxState.fromRawOutput(selinuxMode)

        val dataPath = ManagedTargetPath.DATA_BT_DID_CONF.canonicalPath
        val sysPath = ManagedTargetPath.SYSTEM_BT_DID_CONF.canonicalPath

        val dataMeta = inspector.inspectFile(dataPath).getOrNull()
        val sysMeta = inspector.inspectFile(sysPath).getOrNull()
        val dirStatus = inspector.checkDirectoryWritable(ManagedTargetPath.DATA_BT_DID_CONF.parentDir)

        val dataBytes = if (dataMeta?.exists == true && dataMeta.fileType == FileType.REGULAR_FILE) {
            inspector.readFileBytes(dataPath).getOrNull()
        } else null
        val dataContent = dataBytes?.let { String(it, Charsets.UTF_8) }

        val sysBytes = if (sysMeta?.exists == true && sysMeta.fileType == FileType.REGULAR_FILE) {
            inspector.readFileBytes(sysPath).getOrNull()
        } else null
        val sysContent = sysBytes?.let { String(it, Charsets.UTF_8) }

        val supportStatus = evaluateSupportStatus(
            sdkInt = sdkInt,
            dataMeta = dataMeta,
            sysMeta = sysMeta,
            dataBytes = dataBytes,
            sysBytes = sysBytes,
            dirStatus = dirStatus
        )

        val existenceState = evaluateExistenceState(dataMeta, sysMeta)
        val pathVerificationState = evaluatePathVerificationState(
            existenceState = existenceState,
            dataBytes = dataBytes,
            sysBytes = sysBytes,
            dirStatus = dirStatus
        )
        val systemCompatState = evaluateSystemCompatibilityState(
            existenceState = existenceState,
            pathVerificationState = pathVerificationState
        )

        val alreadyPatched = BtDidConfValidator.containsKindleRemoteDid(dataContent)
        val identityVerificationState = when {
            pathVerificationState == TargetPathVerificationState.VERIFIED_EXISTING_DATA_CONFIG && alreadyPatched ->
                DeviceIdentityVerificationState.CONFIG_PATCHED_RUNTIME_UNVERIFIED
            pathVerificationState == TargetPathVerificationState.VERIFIED_EXISTING_DATA_CONFIG ->
                DeviceIdentityVerificationState.CONFIG_PRESENT_NOT_KINDLE_DID
            else ->
                DeviceIdentityVerificationState.UNVERIFIED_DEFAULT_OEM_IDENTITY
        }
        val kindleAcceptanceState = when (identityVerificationState) {
            DeviceIdentityVerificationState.CONFIG_PATCHED_RUNTIME_UNVERIFIED ->
                KindleAcceptanceState.PENDING_REAL_KINDLE_PAIRING_VERIFICATION
            else ->
                KindleAcceptanceState.REJECTED_OR_UNVERIFIED_CLASSIC_ALLOWLIST
        }

        // Only existing, format-verified regular files in writable whitelisted paths are included in verifiedModifiableTargets.
        // Non-existent bt_did.conf is NEVER automatically included in the modifiable file list.
        val modifiableTargets = if (pathVerificationState == TargetPathVerificationState.VERIFIED_EXISTING_DATA_CONFIG) {
            listOf(ManagedTargetPath.DATA_BT_DID_CONF)
        } else {
            emptyList()
        }

        val summary = buildDynamicStatusMessage(
            rootPermissionState = RootPermissionState.GRANTED_UID_0,
            existenceState = existenceState,
            pathVerificationState = pathVerificationState,
            alreadyPatched = alreadyPatched
        )

        auditLogger?.record(
            action = "ENV_CHECK",
            outcome = "SUCCESS",
            summary = summary,
            targetPath = dataPath,
            details = "selinux=$selinuxMode, existence=${existenceState.name}, verification=${pathVerificationState.name}, modifiable=${modifiableTargets.map { it.canonicalPath }}"
        )

        val report = RootEnvironmentReport(
            suBinaryPresent = true,
            hasRootPermission = true,
            uidOutput = uidOut,
            selinuxMode = selinuxMode,
            androidSdkInt = sdkInt,
            dataBtDidMetadata = dataMeta,
            systemBtDidMetadata = sysMeta,
            dataBtDidContent = dataContent,
            systemBtDidContent = sysContent,
            dataDirWriteStatus = dirStatus,
            btDidSupportStatus = supportStatus,
            alreadyHasKindleDid = alreadyPatched,
            summaryMessage = summary,
            rootPermissionState = RootPermissionState.GRANTED_UID_0,
            selinuxState = selinuxState,
            targetFileExistenceState = existenceState,
            targetPathVerificationState = pathVerificationState,
            systemCompatibilityState = systemCompatState,
            deviceIdentityVerificationState = identityVerificationState,
            kindleAcceptanceState = kindleAcceptanceState,
            verifiedModifiableTargets = modifiableTargets,
            isClassicHidFeatureSupported = false
        )
        onReportGenerated?.invoke(report)
        return report
    }

    private fun evaluateExistenceState(
        dataMeta: RootFileMetadata?,
        sysMeta: RootFileMetadata?
    ): TargetFileExistenceState {
        if (dataMeta == null && sysMeta == null) {
            return TargetFileExistenceState.UNCHECKED
        }
        val dataInvalidType = dataMeta != null && dataMeta.exists && dataMeta.fileType != FileType.REGULAR_FILE
        val sysInvalidType = sysMeta != null && sysMeta.exists && sysMeta.fileType != FileType.REGULAR_FILE
        if (dataInvalidType || sysInvalidType) {
            return TargetFileExistenceState.INVALID_NON_REGULAR_FILE
        }
        val dataExists = dataMeta?.exists == true && dataMeta.fileType == FileType.REGULAR_FILE
        val sysExists = sysMeta?.exists == true && sysMeta.fileType == FileType.REGULAR_FILE
        return when {
            dataExists && sysExists -> TargetFileExistenceState.BOTH_EXIST
            dataExists && !sysExists -> TargetFileExistenceState.DATA_CONF_ONLY
            !dataExists && sysExists -> TargetFileExistenceState.SYSTEM_TEMPLATE_ONLY
            else -> TargetFileExistenceState.BOTH_MISSING
        }
    }

    private fun evaluatePathVerificationState(
        existenceState: TargetFileExistenceState,
        dataBytes: ByteArray?,
        sysBytes: ByteArray?,
        dirStatus: DirectoryWriteStatus
    ): TargetPathVerificationState {
        if (existenceState == TargetFileExistenceState.UNCHECKED) {
            return TargetPathVerificationState.UNCHECKED
        }
        if (existenceState == TargetFileExistenceState.BOTH_MISSING) {
            return TargetPathVerificationState.MISSING_UNVERIFIED_CONFIG
        }
        if (existenceState == TargetFileExistenceState.INVALID_NON_REGULAR_FILE) {
            return TargetPathVerificationState.UNKNOWN_OR_CORRUPTED_FORMAT
        }
        if (dirStatus != DirectoryWriteStatus.WRITABLE) {
            return TargetPathVerificationState.DIRECTORY_NOT_WRITABLE
        }
        return when (existenceState) {
            TargetFileExistenceState.DATA_CONF_ONLY,
            TargetFileExistenceState.BOTH_EXIST -> {
                if (dataBytes == null ||
                    BtDidConfValidator.validateExistingBytes(dataBytes) !is BtDidConfValidator.FormatValidationResult.Valid
                ) {
                    TargetPathVerificationState.UNKNOWN_OR_CORRUPTED_FORMAT
                } else {
                    TargetPathVerificationState.VERIFIED_EXISTING_DATA_CONFIG
                }
            }
            TargetFileExistenceState.SYSTEM_TEMPLATE_ONLY -> {
                if (sysBytes == null ||
                    BtDidConfValidator.validateExistingBytes(sysBytes) !is BtDidConfValidator.FormatValidationResult.Valid
                ) {
                    TargetPathVerificationState.UNKNOWN_OR_CORRUPTED_FORMAT
                } else {
                    TargetPathVerificationState.SYSTEM_TEMPLATE_ONLY_UNVERIFIED_OVERRIDE
                }
            }
            else -> TargetPathVerificationState.MISSING_UNVERIFIED_CONFIG
        }
    }

    private fun evaluateSystemCompatibilityState(
        existenceState: TargetFileExistenceState,
        pathVerificationState: TargetPathVerificationState
    ): SystemCompatibilityState {
        return when (pathVerificationState) {
            TargetPathVerificationState.UNCHECKED ->
                SystemCompatibilityState.UNCHECKED
            TargetPathVerificationState.MISSING_UNVERIFIED_CONFIG ->
                SystemCompatibilityState.UNSUPPORTED_OR_PENDING_VERIFICATION
            TargetPathVerificationState.UNKNOWN_OR_CORRUPTED_FORMAT,
            TargetPathVerificationState.DIRECTORY_NOT_WRITABLE,
            TargetPathVerificationState.NOT_IN_WHITELIST,
            TargetPathVerificationState.READ_ONLY_SYSTEM_PARTITION ->
                SystemCompatibilityState.INCOMPATIBLE_FORMAT_OR_PERMISSION
            TargetPathVerificationState.SYSTEM_TEMPLATE_ONLY_UNVERIFIED_OVERRIDE ->
                SystemCompatibilityState.PENDING_VERIFICATION_TEMPLATE_ONLY
            TargetPathVerificationState.VERIFIED_EXISTING_DATA_CONFIG ->
                if (existenceState.anyConfigExists) {
                    SystemCompatibilityState.COMPATIBLE_CONFIG_PRESENT
                } else {
                    SystemCompatibilityState.UNSUPPORTED_OR_PENDING_VERIFICATION
                }
        }
    }

    private fun buildDynamicStatusMessage(
        rootPermissionState: RootPermissionState,
        existenceState: TargetFileExistenceState,
        pathVerificationState: TargetPathVerificationState,
        alreadyPatched: Boolean
    ): String {
        if (!rootPermissionState.isGranted) {
            return "未获取 Root 权限 (UID=0)，经典蓝牙 VID/PID 修改功能不可用。请使用免 Root 的标准 BLE 外设模式。"
        }
        return when {
            existenceState == TargetFileExistenceState.BOTH_MISSING -> {
                "Root 权限已获取，但当前系统未发现已知的设备身份配置文件。经典蓝牙 VID/PID 修改功能暂不可用，正在等待进一步验证。不会创建未经验证的配置文件，也不会修改系统安全策略。"
            }
            existenceState == TargetFileExistenceState.INVALID_NON_REGULAR_FILE -> {
                "Root 权限已获取，但检测到目标路径并非普通文件（符号链接、目录或特殊文件）。经典蓝牙 VID/PID 修改功能已拦截，不会覆盖非普通文件，也不会修改系统安全策略。"
            }
            pathVerificationState == TargetPathVerificationState.UNKNOWN_OR_CORRUPTED_FORMAT -> {
                "Root 权限已获取，但当前目标配置文件格式未知或未通过校验。经典蓝牙 VID/PID 修改功能暂不可用，不会覆盖未知格式的配置文件，也不会修改系统安全策略。"
            }
            pathVerificationState == TargetPathVerificationState.DIRECTORY_NOT_WRITABLE -> {
                "Root 权限已获取，但目标目录 /data/misc/bluetooth 不可写或不存在。经典蓝牙 VID/PID 修改功能暂不可用，不会强行修改分区挂载或系统安全策略。"
            }
            pathVerificationState == TargetPathVerificationState.SYSTEM_TEMPLATE_ONLY_UNVERIFIED_OVERRIDE -> {
                "Root 权限已获取，检测到只读系统模板 /system/etc/bluetooth/bt_did.conf，但 /data/misc/bluetooth/bt_did.conf 当前不存在。当前不存在的 bt_did.conf 不会自动纳入可修改文件清单，且经典蓝牙身份生效性仍待验证。"
            }
            pathVerificationState == TargetPathVerificationState.VERIFIED_EXISTING_DATA_CONFIG -> {
                if (alreadyPatched) {
                    "Root 权限已获取，已检测到存在的 /data/misc/bluetooth/bt_did.conf（含 0x0171:0x0443 配置）；但协议栈运行时身份与 Kindle 识别结果仍待实机验证。"
                } else {
                    "Root 权限已获取，已检测到存在的 /data/misc/bluetooth/bt_did.conf。支持在完整备份与校验保护下修改该文件，但修改后协议栈是否生效仍待验证。"
                }
            }
            else -> {
                "Root 权限已获取，但经典蓝牙 VID/PID 修改条件未完成验证，功能暂不可用。"
            }
        }
    }

    private fun evaluateSupportStatus(
        sdkInt: Int,
        dataMeta: RootFileMetadata?,
        sysMeta: RootFileMetadata?,
        dataBytes: ByteArray?,
        sysBytes: ByteArray?,
        dirStatus: DirectoryWriteStatus
    ): BtDidSupportStatus {
        if (dirStatus != DirectoryWriteStatus.WRITABLE) {
            return BtDidSupportStatus.DIRECTORY_NOT_WRITABLE
        }
        if (dataMeta != null && dataMeta.exists) {
            if (dataMeta.fileType != FileType.REGULAR_FILE) {
                return BtDidSupportStatus.INVALID_TARGET_FORMAT_OR_TYPE
            }
            if (dataBytes == null ||
                BtDidConfValidator.validateExistingBytes(dataBytes) !is BtDidConfValidator.FormatValidationResult.Valid
            ) {
                return BtDidSupportStatus.INVALID_TARGET_FORMAT_OR_TYPE
            }
            return BtDidSupportStatus.SUPPORTED_DATA_CONF_EXISTS
        }

        // data bt_did.conf does not exist; check if system template exists and is valid
        if (sysMeta != null && sysMeta.exists) {
            if (sysMeta.fileType != FileType.REGULAR_FILE) {
                return BtDidSupportStatus.INVALID_TARGET_FORMAT_OR_TYPE
            }
            if (sysBytes == null ||
                BtDidConfValidator.validateExistingBytes(sysBytes) !is BtDidConfValidator.FormatValidationResult.Valid
            ) {
                return BtDidSupportStatus.INVALID_TARGET_FORMAT_OR_TYPE
            }
            return BtDidSupportStatus.SUPPORTED_SYSTEM_TEMPLATE_EXISTS
        }

        return if (sdkInt >= 33) {
            BtDidSupportStatus.UNSUPPORTED_ANDROID_13_PLUS_NO_CONF
        } else {
            BtDidSupportStatus.UNVERIFIED_NO_CONF_FOUND
        }
    }
}

