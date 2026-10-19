package com.kindle.pageturner.root

import com.kindle.pageturner.battery.BatteryServiceManager
import com.kindle.pageturner.classic.ClassicHidDeviceManager
import com.kindle.pageturner.hid.HidKeyAction
import com.kindle.pageturner.hid.HidStateMachine
import com.kindle.pageturner.hid.KeySendResult
import com.kindle.pageturner.identity.PnpIdPayload
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Behavioral unit tests verifying the 8 required scenarios for real-device Root baseline handling,
 * 8-dimension feature state decomposition, Classic Bluetooth HID identity verification boundaries,
 * zero-write safety guarantees in unsupported environments, and BLE HOGP fallback availability.
 */
class ClassicHidAndRootBaselineTest {

    private lateinit var tempDir: File
    private lateinit var virtualFsDir: File
    private lateinit var storageDir: File
    private lateinit var executor: VirtualFsRootCommandExecutor
    private lateinit var inspector: RootFileInspector
    private lateinit var auditLogger: RootAuditLogger
    private lateinit var backupManager: RootBackupManager
    private lateinit var transactionManager: RootFileTransactionManager
    private lateinit var capabilityChecker: RootCapabilityChecker
    private lateinit var testScope: CoroutineScope

    private val dataTargetPath = ManagedTargetPath.DATA_BT_DID_CONF.canonicalPath
    private val systemTemplatePath = ManagedTargetPath.SYSTEM_BT_DID_CONF.canonicalPath

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("kindle_baseline_verification_test_").toFile()
        virtualFsDir = File(tempDir, "virtual_fs").apply { mkdirs() }
        storageDir = File(tempDir, "app_storage").apply { mkdirs() }
        executor = VirtualFsRootCommandExecutor(virtualFsDir).apply {
            suBinaryExists = true
            rootAuthorized = true
            selinuxEnforceMode = "Enforcing"
        }
        inspector = RootFileInspector(executor)
        auditLogger = RootAuditLogger(storageDir)
        backupManager = RootBackupManager(storageDir, inspector, auditLogger)
        transactionManager = RootFileTransactionManager(storageDir, executor, inspector, backupManager, auditLogger)
        capabilityChecker = RootCapabilityChecker(executor, inspector, auditLogger, sdkIntProvider = { 34 })
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        testScope.cancel()
        tempDir.deleteRecursively()
    }

    /**
     * Scenario 1: Root 可用 (su 存在, UID=0, SELinux=Enforcing)，但目标文件 (/system 与 /data bt_did.conf) 均不存在。
     */
    @Test
    fun `scenario 1 - root available with UID 0 but target bt_did_conf files do not exist`() {
        // Ensure neither /system/etc/bluetooth/bt_did.conf nor /data/misc/bluetooth/bt_did.conf exists
        executor.removeSeededFile(systemTemplatePath)
        executor.removeSeededFile(dataTargetPath)

        val report = capabilityChecker.inspectEnvironment()
        val assessment = report.toFeatureAssessment()

        // Verify exact real-device baseline dimensions
        assertTrue(report.suBinaryPresent)
        assertTrue(report.hasRootPermission)
        assertEquals(RootPermissionState.GRANTED_UID_0, report.rootPermissionState)
        assertEquals(SelinuxState.ENFORCING, report.selinuxState)
        assertEquals("Enforcing", report.selinuxMode)
        assertFalse(report.systemBtDidMetadata?.exists ?: false)
        assertFalse(report.dataBtDidMetadata?.exists ?: false)
        assertEquals(TargetFileExistenceState.BOTH_MISSING, report.targetFileExistenceState)
        assertEquals(TargetPathVerificationState.MISSING_UNVERIFIED_CONFIG, report.targetPathVerificationState)
        assertEquals(
            SystemCompatibilityState.UNSUPPORTED_OR_PENDING_VERIFICATION,
            report.systemCompatibilityState
        )

        // Non-existent bt_did.conf must NOT be included in verifiedModifiableTargets
        assertTrue(
            "Non-existent bt_did.conf must not be automatically added to modifiable file list",
            report.verifiedModifiableTargets.isEmpty()
        )
        assertFalse(assessment.isVidPidModificationSupported)
        assertFalse(assessment.isClassicHidFeatureSupported)

        // Verify dynamic status message matches requirement
        val expectedMessage =
            "Root 权限已获取，但当前系统未发现已知的设备身份配置文件。经典蓝牙 VID/PID 修改功能暂不可用，正在等待进一步验证。不会创建未经验证的配置文件，也不会修改系统安全策略。"
        assertEquals(expectedMessage, report.summaryMessage)
        assertEquals(expectedMessage, assessment.dynamicStatusMessage)
    }

    /**
     * Scenario 2: Root 可用，但目标文件格式未知或损坏。
     */
    @Test
    fun `scenario 2 - root available but target file has unknown or corrupted format`() {
        val unknownFormatBytes = "UNKNOWN_PROPRIETARY_XML_OR_BINARY_CONFIG\n<did vid='0171'/>\n".toByteArray(Charsets.UTF_8)
        executor.seedRegularFile(dataTargetPath, unknownFormatBytes)

        val report = capabilityChecker.inspectEnvironment()
        val assessment = report.toFeatureAssessment()

        assertEquals(RootPermissionState.GRANTED_UID_0, report.rootPermissionState)
        assertEquals(TargetFileExistenceState.DATA_CONF_ONLY, report.targetFileExistenceState)
        assertEquals(TargetPathVerificationState.UNKNOWN_OR_CORRUPTED_FORMAT, report.targetPathVerificationState)
        assertEquals(SystemCompatibilityState.INCOMPATIBLE_FORMAT_OR_PERMISSION, report.systemCompatibilityState)
        assertTrue("File with unknown format must not be in verifiedModifiableTargets", report.verifiedModifiableTargets.isEmpty())
        assertFalse(assessment.isVidPidModificationSupported)
        assertFalse(assessment.isClassicHidFeatureSupported)
        assertTrue(report.summaryMessage.contains("格式未知或未通过校验"))

        // Attempting to modify the unknown-format file must be rejected without touching the file
        val validNewBytes = BtDidConfValidator.buildPatchedDidContent(null).getOrThrow().toByteArray(Charsets.UTF_8)
        val outcome = transactionManager.executeModification(
            targetPath = dataTargetPath,
            newContentBytes = validNewBytes,
            userConfirmed = true
        )
        assertTrue(outcome is ModificationOutcome.RejectedPrecheck)
        assertArrayEquals(unknownFormatBytes, executor.resolveHostFile(dataTargetPath).readBytes())
    }

    /**
     * Scenario 3: Root 可用，但目标路径不在白名单中。
     */
    @Test
    fun `scenario 3 - root available but target path is not in whitelist`() {
        val unwhitelistedPaths = listOf(
            "/data/misc/bluedroid/bt_config.conf",
            "/vendor/etc/bluetooth/bt_did.conf",
            "/system/build.prop",
            "/data/local/tmp/bt_did.conf"
        )

        val report = capabilityChecker.inspectEnvironment()
        val validNewBytes = BtDidConfValidator.buildPatchedDidContent(null).getOrThrow().toByteArray(Charsets.UTF_8)

        executor.executedCommands.clear()

        for (badPath in unwhitelistedPaths) {
            val pathVer = capabilityChecker.verifyCandidateTargetPath(badPath, report)
            assertEquals(
                "Unwhitelisted path $badPath must be classified as NOT_IN_WHITELIST",
                TargetPathVerificationState.NOT_IN_WHITELIST,
                pathVer
            )

            val outcome = transactionManager.executeModification(
                targetPath = badPath,
                newContentBytes = validNewBytes,
                userConfirmed = true
            )
            assertTrue("Unwhitelisted path $badPath must be rejected by transactionManager", outcome is ModificationOutcome.RejectedPrecheck)
            assertTrue((outcome as ModificationOutcome.RejectedPrecheck).reason.contains("白名单"))
        }

        // Read-only system template path is in read whitelist but rejected for modification
        assertEquals(
            TargetPathVerificationState.READ_ONLY_SYSTEM_PARTITION,
            capabilityChecker.verifyCandidateTargetPath(systemTemplatePath, report)
        )
        val sysModifyOutcome = transactionManager.executeModification(
            targetPath = systemTemplatePath,
            newContentBytes = validNewBytes,
            userConfirmed = true
        )
        assertTrue(sysModifyOutcome is ModificationOutcome.RejectedPrecheck)

        // Zero shell commands should have been executed during all rejected path checks
        assertTrue("No shell commands should be executed for rejected non-whitelisted paths", executor.executedCommands.isEmpty())
    }

    /**
     * Scenario 4: SELinux 为 Enforcing。
     */
    @Test
    fun `scenario 4 - selinux is Enforcing and no command ever attempts to disable or relax selinux`() {
        executor.selinuxEnforceMode = "Enforcing"
        val report = capabilityChecker.inspectEnvironment()

        assertEquals("Enforcing", report.selinuxMode)
        assertEquals(SelinuxState.ENFORCING, report.selinuxState)
        assertEquals(SelinuxState.ENFORCING, SelinuxState.fromRawOutput("1"))
        assertEquals(SelinuxState.PERMISSIVE, SelinuxState.fromRawOutput("Permissive"))
        assertEquals(SelinuxState.PERMISSIVE, SelinuxState.fromRawOutput("0"))

        // Verify none of the executed commands or their shell scripts ever invoke setenforce or policy relaxations
        for (cmd in executor.executedCommands) {
            val script = cmd.toShellScript()
            assertFalse("Must never execute setenforce: $script", script.contains("setenforce"))
            assertFalse("Must never execute supolicy/magiskpolicy: $script", script.contains("magiskpolicy") || script.contains("supolicy"))
            assertFalse("Must never remount partitions: $script", script.contains("remount"))
        }
    }

    /**
     * Scenario 5: 经典蓝牙 HID 注册成功，但设备身份无法验证。
     */
    @Test
    fun `scenario 5 - classic bluetooth hid registration succeeds but device identity is unverified`() {
        val report = capabilityChecker.inspectEnvironment()
        val classicHidManager = ClassicHidDeviceManager(
            context = null,
            scope = testScope,
            bluetoothAdapterProvider = { null },
            sdkIntProvider = { 34 }
        )

        // Simulate BluetoothHidDevice.Callback.onAppStatusChanged(device, registered = true)
        classicHidManager.onRegistrationStatusUpdated(
            registered = true,
            pluggedDeviceAddress = "11:22:33:44:55:66",
            envReport = report
        )

        assertTrue("BluetoothHidDevice registration flag should be true", classicHidManager.isRegistered.value)
        assertEquals(
            ClassicHidRegistrationState.REGISTERED,
            classicHidManager.registrationState.value
        )
        assertEquals(
            "Device identity must remain UNVERIFIED_DEFAULT_OEM_IDENTITY after SDP registration",
            DeviceIdentityVerificationState.UNVERIFIED_DEFAULT_OEM_IDENTITY,
            classicHidManager.deviceIdentityVerificationState.value
        )
        assertFalse(
            "Identity must not be marked verified merely because BluetoothHidDevice registered",
            classicHidManager.deviceIdentityVerificationState.value.isIdentityVerified
        )
        assertEquals(
            KindleAcceptanceState.REJECTED_OR_UNVERIFIED_CLASSIC_ALLOWLIST,
            classicHidManager.kindleAcceptanceState.value
        )
        assertFalse(
            "Classic HID feature must NOT be marked supported when identity is unverified",
            classicHidManager.isFeatureSupported.value
        )

        val assessment = classicHidManager.evaluateFeatureAssessment(report)
        assertEquals(ClassicHidRegistrationState.REGISTERED, assessment.classicHidRegistrationState)
        assertFalse(assessment.isClassicHidFeatureSupported)
        assertFalse(assessment.isVidPidModificationSupported)

        // Simulate subsequent Android BluetoothHidDevice callback without explicit envReport argument:
        // must retain the previously evaluated RootEnvironmentReport rather than resetting to UNCHECKED.
        classicHidManager.onRegistrationStatusUpdated(
            registered = true,
            pluggedDeviceAddress = "11:22:33:44:55:66"
        )
        val retainedAssessment = classicHidManager.evaluateFeatureAssessment()
        assertEquals(RootPermissionState.GRANTED_UID_0, retainedAssessment.rootPermissionState)
        assertEquals(SelinuxState.ENFORCING, retainedAssessment.selinuxState)
        assertEquals(TargetFileExistenceState.BOTH_MISSING, retainedAssessment.targetFileExistenceState)
        assertFalse(retainedAssessment.isClassicHidFeatureSupported)
    }

    /**
     * Scenario 6: 在高级功能不可用时，普通功能 (免 Root 标准 BLE HOGP 模式) 仍可正常工作。
     */
    @Test
    fun `scenario 6 - standard BLE HOGP mode works normally when root classic hid modification is unavailable`() = runBlocking {
        // 1. Verify Root Classic HID modification is unavailable on the baseline environment
        val report = capabilityChecker.inspectEnvironment()
        val assessment = report.toFeatureAssessment()
        assertFalse(assessment.isVidPidModificationSupported)
        assertFalse(assessment.isClassicHidFeatureSupported)
        assertTrue("Standard BLE HOGP mode must remain available", assessment.bleHogpStandardModeAvailable)

        // 2. Verify BLE GATT DIS PnP ID (0x2A50) payload still produces exact verified 0x0171:0x0443 bytes without Root
        val pnpPayload = PnpIdPayload.OFFICIAL_KINDLE_REMOTE.toByteArray()
        assertArrayEquals(
            byteArrayOf(0x02, 0x71, 0x01, 0x43, 0x04, 0x00, 0x01),
            pnpPayload
        )
        val parsedPnp = PnpIdPayload.fromByteArray(pnpPayload)
        assertEquals(0x0171, parsedPnp.vendorId)
        assertEquals(0x0443, parsedPnp.productId)

        // 3. Verify BLE HOGP HidStateMachine sends Next Page (0x4E) and Prev Page (0x4B) 9-byte reports normally
        val transmittedFrames = mutableListOf<ByteArray>()
        val bleHidStateMachine = HidStateMachine(
            scope = this,
            sendReportDelegate = { frame ->
                transmittedFrames.add(frame)
                true
            },
            isConnectedProvider = { true },
            isNotificationReadyProvider = { true },
            includeReportId = true
        )
        bleHidStateMachine.holdDurationMs = 5L
        bleHidStateMachine.debounceIntervalMs = 0L

        val fwdResult = bleHidStateMachine.sendKey(HidKeyAction.PAGE_DOWN)
        assertTrue(fwdResult is KeySendResult.Success)
        assertEquals(2, transmittedFrames.size)
        // Press frame: Report ID 1 + keycode 0x4E (Page Down)
        assertArrayEquals(
            byteArrayOf(0x01, 0x00, 0x00, HidKeyAction.PAGE_DOWN.usageCode, 0x00, 0x00, 0x00, 0x00, 0x00),
            transmittedFrames[0]
        )
        // Release frame: Report ID 1 + all zeros
        assertArrayEquals(
            byteArrayOf(0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            transmittedFrames[1]
        )

        // 4. Verify BLE Battery Service Manager works normally
        val batteryManager = BatteryServiceManager()
        batteryManager.updateBatteryLevel(85)
        assertArrayEquals(byteArrayOf(85), batteryManager.getBatteryLevelPayload())
    }

    /**
     * Scenario 7: 不支持环境下不会创建配置文件、不会关闭 SELinux、不会执行任何系统写入。
     */
    @Test
    fun `scenario 7 - unsupported environment never creates config files never disables selinux and performs zero writes`() {
        executor.removeSeededFile(systemTemplatePath)
        executor.removeSeededFile(dataTargetPath)
        executor.executedCommands.clear()

        // 1. Run environment inspection via RootCapabilityChecker and RootHelper
        RootHelper.initializeWithDirectory(storageDir, executor)
        val envReport = capabilityChecker.inspectEnvironment()
        val helperResult = RootHelper.inspectEnvironment()

        assertFalse(envReport.toFeatureAssessment().isVidPidModificationSupported)
        assertFalse(helperResult.isClassicHidFeatureSupported)
        assertTrue(helperResult.verifiedModifiableTargets.isEmpty())

        // 2. Attempt modification with allowCreateWhenTemplateMissing = false AND allowCreateWhenTemplateMissing = true
        val patchBytes = BtDidConfValidator.buildPatchedDidContent(null).getOrThrow().toByteArray(Charsets.UTF_8)
        val attempt1 = transactionManager.executeModification(
            targetPath = dataTargetPath,
            newContentBytes = patchBytes,
            userConfirmed = true,
            allowCreateWhenTemplateMissing = false
        )
        val attempt2 = transactionManager.executeModification(
            targetPath = dataTargetPath,
            newContentBytes = patchBytes,
            userConfirmed = true,
            allowCreateWhenTemplateMissing = true
        )

        assertTrue("Attempt 1 must be rejected in precheck", attempt1 is ModificationOutcome.RejectedPrecheck)
        assertTrue("Attempt 2 must also be rejected even if allowCreateWhenTemplateMissing=true", attempt2 is ModificationOutcome.RejectedPrecheck)

        // 3. Assert neither /data/misc/bluetooth/bt_did.conf nor /system/etc/bluetooth/bt_did.conf was created
        assertFalse("Must NOT create /data/misc/bluetooth/bt_did.conf", executor.resolveHostFile(dataTargetPath).exists())
        assertFalse("Must NOT create /system/etc/bluetooth/bt_did.conf", executor.resolveHostFile(systemTemplatePath).exists())

        // 4. Assert zero mutating/write commands were executed
        val mutatingCommands = executor.executedCommands.filter {
            it is RootShellCommand.WriteStagingFile ||
                it is RootShellCommand.ApplyMetadata ||
                it is RootShellCommand.AtomicRename ||
                it is RootShellCommand.DeleteFile
        }
        assertTrue(
            "Zero file write/metadata/rename/delete commands may execute in unsupported environment, got: $mutatingCommands",
            mutatingCommands.isEmpty()
        )

        // 5. Assert zero transactions or backups were created during rejected modification attempts
        assertTrue(transactionManager.listTransactions(dataTargetPath).isEmpty())
        assertTrue(backupManager.listBackups(dataTargetPath).isEmpty())
    }

    /**
     * Scenario 8: 不完整或未验证的检测结果，不会被界面显示为“功能已支持”。
     */
    @Test
    fun `scenario 8 - incomplete or unverified detection results are never evaluated or displayed as supported`() {
        // 1. Unchecked initial state before user triggers Root check
        val unchecked = ClassicBluetoothFeatureAssessment.unchecked()
        assertFalse(unchecked.isVidPidModificationSupported)
        assertFalse(unchecked.isClassicHidFeatureSupported)
        val uncheckedText = unchecked.toMultilineDiagnosticText()
        assertTrue(uncheckedText.contains("暂不可用 / 待验证"))
        assertTrue(uncheckedText.contains("不支持 / 待验证"))

        // 2. Root unavailable (su present, permission denied)
        executor.suBinaryExists = true
        executor.rootAuthorized = false
        val deniedReport = capabilityChecker.inspectEnvironment()
        val deniedAssessment = deniedReport.toFeatureAssessment(ClassicHidRegistrationState.REGISTERED)
        assertFalse(deniedAssessment.isVidPidModificationSupported)
        assertFalse(deniedAssessment.isClassicHidFeatureSupported)

        // 3. Root granted, system template exists, /data bt_did.conf missing -> still unverified, not in modifiable list,
        //    and executeModification rejects creating the missing /data/misc/bluetooth/bt_did.conf by default
        executor.rootAuthorized = true
        executor.seedRegularFile(
            systemTemplatePath,
            "[DID1]\nprimaryRecord = true\nvendorId = 0x18D1\nproductId = 0x4EE1\n".toByteArray(Charsets.UTF_8)
        )
        executor.removeSeededFile(dataTargetPath)
        val templateOnlyReport = capabilityChecker.inspectEnvironment()
        val templateOnlyAssessment = templateOnlyReport.toFeatureAssessment(ClassicHidRegistrationState.REGISTERED)
        assertTrue(
            "Missing /data/misc/bluetooth/bt_did.conf must not be automatically included in verifiedModifiableTargets",
            templateOnlyReport.verifiedModifiableTargets.isEmpty()
        )
        assertFalse(templateOnlyReport.btDidSupportStatus.canSafelyModify)
        assertFalse(templateOnlyAssessment.isVidPidModificationSupported)
        assertFalse(templateOnlyAssessment.isClassicHidFeatureSupported)

        val patchedBytes = BtDidConfValidator.buildPatchedDidContent(null).getOrThrow().toByteArray(Charsets.UTF_8)
        val templateOnlyModifyOutcome = transactionManager.executeModification(
            targetPath = dataTargetPath,
            newContentBytes = patchedBytes,
            userConfirmed = true
        )
        assertTrue(
            "Default executeModification must reject creating missing /data/misc/bluetooth/bt_did.conf even when /system template exists",
            templateOnlyModifyOutcome is ModificationOutcome.RejectedPrecheck
        )

        // 4. Root granted, /data/misc/bluetooth/bt_did.conf exists and contains 0x0171:0x0443, and Classic HID registered ->
        //    File modification is supported, but runtime identity and Kindle acceptance remain unverified so Classic HID
        //    full feature is STILL not marked as supported!
        executor.seedRegularFile(dataTargetPath, patchedBytes)
        val patchedReport = capabilityChecker.inspectEnvironment()
        val patchedAssessment = patchedReport.toFeatureAssessment(ClassicHidRegistrationState.REGISTERED)
        assertEquals(listOf(ManagedTargetPath.DATA_BT_DID_CONF), patchedReport.verifiedModifiableTargets)
        assertTrue(patchedAssessment.isVidPidModificationSupported)
        assertEquals(
            DeviceIdentityVerificationState.CONFIG_PATCHED_RUNTIME_UNVERIFIED,
            patchedAssessment.deviceIdentityVerificationState
        )
        assertEquals(
            KindleAcceptanceState.PENDING_REAL_KINDLE_PAIRING_VERIFICATION,
            patchedAssessment.kindleAcceptanceState
        )
        assertFalse(
            "Even with patched config file, overall Classic HID feature must not be marked supported without runtime identity verification",
            patchedAssessment.isClassicHidFeatureSupported
        )

        // 5. Incomplete SELinux state (UNKNOWN / UNCHECKED) or BLE-only identity states must NEVER mark Classic HID supported
        val unknownSelinuxReport = patchedReport.copy(
            selinuxState = SelinuxState.UNKNOWN,
            deviceIdentityVerificationState = DeviceIdentityVerificationState.VERIFIED_BLE_HOGP_DIS_PNP,
            kindleAcceptanceState = KindleAcceptanceState.SUPPORTED_BLE_HOGP_MODE
        )
        val unknownSelinuxAssessment = unknownSelinuxReport.toFeatureAssessment(ClassicHidRegistrationState.REGISTERED)
        assertFalse(
            "Unknown SELinux state must prevent isVidPidModificationSupported from being true",
            unknownSelinuxAssessment.isVidPidModificationSupported
        )
        assertFalse(
            "BLE HOGP verification states must never mark Classic Bluetooth HID feature as supported",
            unknownSelinuxAssessment.isClassicHidFeatureSupported
        )
    }
}
