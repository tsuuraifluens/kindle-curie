package com.kindle.pageturner.root

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Security audit and validator unit tests for [RootPathValidator], [RootShellCommand],
 * [BtDidConfValidator], and [RootCapabilityChecker].
 */
class RootSecurityAndValidatorTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("kindle_root_security_test_").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `RootPathValidator rejects empty relative traversal and injection paths`() {
        val maliciousPaths = listOf(
            "",
            "   ",
            "data/misc/bluetooth/bt_did.conf",
            "/data/misc/bluetooth/../bluetooth/bt_did.conf",
            "/data/misc/bluetooth/./bt_did.conf",
            "/data/misc/bluetooth//bt_did.conf",
            "/data/misc/bluetooth/bt_did.conf; rm -rf /",
            "/data/misc/bluetooth/bt_did.conf | id",
            "/data/misc/bluetooth/bt_did.conf\$(id)",
            "/data/misc/bluetooth/bt_did.conf`id`",
            "/data/misc/bluetooth/bt_config.conf",
            "/etc/passwd"
        )

        for (bad in maliciousPaths) {
            val res = RootPathValidator.validatePath(bad, requireWritable = false)
            assertTrue("Malicious or unwhitelisted path must be rejected: '$bad'", res.isFailure)
        }

        // Valid whitelisted writable path
        val dataOk = RootPathValidator.validatePath("/data/misc/bluetooth/bt_did.conf", requireWritable = true)
        assertTrue(dataOk.isSuccess)
        assertEquals(ManagedTargetPath.DATA_BT_DID_CONF, dataOk.getOrThrow())

        // System template is valid for read-only inspection, but rejected for write
        val sysReadOk = RootPathValidator.validatePath("/system/etc/bluetooth/bt_did.conf", requireWritable = false)
        assertTrue(sysReadOk.isSuccess)
        val sysWriteBlocked = RootPathValidator.validatePath("/system/etc/bluetooth/bt_did.conf", requireWritable = true)
        assertTrue(sysWriteBlocked.isFailure)
    }

    @Test
    fun `RootShellCommand enforces path validation in constructor and never embeds staging bytes in shell script`() {
        assertThrows(IllegalArgumentException::class.java) {
            RootShellCommand.StatAndHashFile("/data/misc/bluetooth/bt_did.conf; id")
        }
        assertThrows(SecurityException::class.java) {
            RootShellCommand.DeleteFile("/system/etc/bluetooth/bt_did.conf")
        }

        val secretPayload = "echo INJECTED; $(reboot)".toByteArray(Charsets.UTF_8)
        val writeCmd = RootShellCommand.WriteStagingFile(
            stagingPath = "/data/misc/bluetooth/.bt_did.conf.tmp.tx123",
            expectedParentDir = "/data/misc/bluetooth",
            content = secretPayload
        )
        val script = writeCmd.toShellScript()
        assertFalse("File payload must never be embedded in shell command string", script.contains("INJECTED"))
        assertFalse("File payload must never be embedded in shell command string", script.contains("reboot"))
        assertArrayEquals(secretPayload, writeCmd.stdinPayload())
    }

    @Test
    fun `BtDidConfValidator validates INI format rejects binary and preserves extra DID sections`() {
        // 1. Rejects binary NUL bytes
        val binaryCheck = BtDidConfValidator.validateExistingBytes(byteArrayOf(0x5B, 0x44, 0x00, 0x5D))
        assertTrue(binaryCheck is BtDidConfValidator.FormatValidationResult.Invalid)

        // 2. Rejects unknown section or malformed syntax
        val badSection = BtDidConfValidator.validateExistingText("[UnknownSection]\nfoo = bar\n")
        assertTrue(badSection is BtDidConfValidator.FormatValidationResult.Invalid)

        val badKey = BtDidConfValidator.validateExistingText("[DID1]\nmaliciousKey = 123\n")
        assertTrue(badKey is BtDidConfValidator.FormatValidationResult.Invalid)

        // 3. Valid multi-section config preserves [DID2] with primaryRecord = false
        val existingMulti = """
            # OEM DID config
            [DID1]
            primaryRecord = true
            vendorId = 0x18D1
            productId = 0x4EE1

            [DID2]
            primaryRecord = true
            vendorId = 0x004C
            productId = 0x0001
        """.trimIndent()

        val patchedRes = BtDidConfValidator.buildPatchedDidContent(existingMulti)
        assertTrue(patchedRes.isSuccess)
        val patched = patchedRes.getOrThrow()
        assertTrue(BtDidConfValidator.containsKindleRemoteDid(patched))
        assertTrue(patched.contains("[DID2]"))
        assertTrue(patched.contains("vendorId = 0x004C"))

        val diff = BtDidConfValidator.generateUnifiedDiff(true, existingMulti, patched)
        assertTrue(diff.contains("- vendorId = 0x18D1"))
        assertTrue(diff.contains("+ vendorId = 0x0171"))
    }

    @Test
    fun `RootCapabilityChecker distinguishes Android 13+ unsupported missing config from supported Fluoride config`() {
        val vfs = VirtualFsRootCommandExecutor(File(tempDir, "vfs").apply { mkdirs() })
        val inspector = RootFileInspector(vfs)

        // Case 1: Android 14 (SDK 34) with neither /data nor /system bt_did.conf -> UNSUPPORTED_ANDROID_13_PLUS_NO_CONF
        val checker34 = RootCapabilityChecker(vfs, inspector, sdkIntProvider = { 34 })
        val report34 = checker34.inspectEnvironment()
        assertEquals(BtDidSupportStatus.UNSUPPORTED_ANDROID_13_PLUS_NO_CONF, report34.btDidSupportStatus)
        assertFalse(report34.btDidSupportStatus.canSafelyModify)

        // Case 2: System template exists (/system/etc/bluetooth/bt_did.conf), /data does not exist -> SUPPORTED_SYSTEM_TEMPLATE_EXISTS
        vfs.seedRegularFile(
            ManagedTargetPath.SYSTEM_BT_DID_CONF.canonicalPath,
            "[DID1]\nvendorId = 0x18D1\nproductId = 0x4EE1\n".toByteArray()
        )
        val reportWithTemplate = checker34.inspectEnvironment()
        assertEquals(BtDidSupportStatus.SUPPORTED_SYSTEM_TEMPLATE_EXISTS, reportWithTemplate.btDidSupportStatus)
        assertFalse(reportWithTemplate.btDidSupportStatus.canSafelyModify)

        // Case 3: /data/misc/bluetooth/bt_did.conf exists with Kindle DID -> SUPPORTED_DATA_CONF_EXISTS & alreadyHasKindleDid
        val kindleDidBytes = BtDidConfValidator.buildPatchedDidContent(null).getOrThrow().toByteArray()
        vfs.seedRegularFile(ManagedTargetPath.DATA_BT_DID_CONF.canonicalPath, kindleDidBytes)
        val reportPatched = checker34.inspectEnvironment()
        assertEquals(BtDidSupportStatus.SUPPORTED_DATA_CONF_EXISTS, reportPatched.btDidSupportStatus)
        assertTrue(reportPatched.alreadyHasKindleDid)

        // Case 4: /data missing and /system template has corrupted/invalid format -> INVALID_TARGET_FORMAT_OR_TYPE
        vfs.resolveHostFile(ManagedTargetPath.DATA_BT_DID_CONF.canonicalPath).delete()
        vfs.seedRegularFile(
            ManagedTargetPath.SYSTEM_BT_DID_CONF.canonicalPath,
            "CORRUPTED_NON_INI_TEMPLATE\n".toByteArray()
        )
        val reportCorruptTemplate = checker34.inspectEnvironment()
        assertEquals(BtDidSupportStatus.INVALID_TARGET_FORMAT_OR_TYPE, reportCorruptTemplate.btDidSupportStatus)
        assertFalse(reportCorruptTemplate.btDidSupportStatus.canSafelyModify)
    }

    @Test
    fun `StatAndHashFile shell script does not depend on awk for Toybox compatibility`() {
        val cmd = RootShellCommand.StatAndHashFile(ManagedTargetPath.DATA_BT_DID_CONF.canonicalPath)
        val script = cmd.toShellScript()
        assertFalse("StatAndHashFile must not rely on awk (absent on standard Android Toybox)", script.contains("awk"))
        assertTrue(script.contains("stat -c '%C'"))
        assertTrue(script.contains("SHALINE="))
    }

    @Test
    fun `BtDidConfValidator enforces 64KB size limit strict UTF8 decoding and supports AOSP optional DI keys`() {
        // 1. Rejects > 64KB payload
        val oversized = ByteArray(BtDidConfValidator.MAX_CONF_BYTES + 1) { 'a'.code.toByte() }
        val overRes = BtDidConfValidator.validateExistingBytes(oversized)
        assertTrue("Payload > 64KB must be rejected", overRes is BtDidConfValidator.FormatValidationResult.Invalid)

        // 2. Rejects malformed UTF-8 byte sequence (without NUL bytes)
        val invalidUtf8 = byteArrayOf(
            '['.code.toByte(), 'D'.code.toByte(), 'I'.code.toByte(), 'D'.code.toByte(), '1'.code.toByte(), ']'.code.toByte(), '\n'.code.toByte(),
            0xFF.toByte(), 0xFE.toByte(), '\n'.code.toByte()
        )
        val utf8Res = BtDidConfValidator.validateExistingBytes(invalidUtf8)
        assertTrue("Invalid UTF-8 bytes must be rejected", utf8Res is BtDidConfValidator.FormatValidationResult.Invalid)

        // 3. Rejects control characters or replacement character even on comment lines
        val badComment = BtDidConfValidator.validateExistingText("# comment with \u0007 bell control char\n[DID1]\nvendorId = 0x0171\n")
        assertTrue("Control char in comment line must be rejected", badComment is BtDidConfValidator.FormatValidationResult.Invalid)

        // 4. Accepts standard AOSP btif_dm.cc optional DI keys
        val aospFullRecord = """
            [DID1]
            recordNumber = 1
            primaryRecord = true
            vendorId = 0x0171
            vendorIdSource = 0x0002
            productId = 0x0443
            version = 0x0100
            clientExecutableURL = http://www.example.com/client
            serviceDescription = Wireless Page Turner
            documentationURL = http://www.example.com/doc
        """.trimIndent()
        val aospRes = BtDidConfValidator.validateExistingText(aospFullRecord)
        assertTrue("Standard AOSP DI keys must be accepted: $aospRes", aospRes is BtDidConfValidator.FormatValidationResult.Valid)
    }

    @Test
    fun `ProcessRootCommandExecutor executes real OS process streams stdin and enforces timeout without deadlock`() {
        val isWindows = (System.getProperty("os.name") ?: "").lowercase().contains("win")

        // 1. Normal execution capturing stdout
        val echoExecutor = ProcessRootCommandExecutor(
            suBinaryPathCandidates = emptyList(),
            commandBuilder = { _ ->
                if (isWindows) listOf("cmd", "/c", "echo uid=0(root)")
                else listOf("sh", "-c", "echo 'uid=0(root)'")
            }
        )
        val okRes = echoExecutor.execute(RootShellCommand.CheckSuId, timeoutMs = 3_000L)
        assertTrue("Expected process command to succeed: $okRes", okRes.isSuccess)
        assertTrue(okRes.stdout.contains("uid=0"))

        // 2. Hung process with large stdin payload enforces timeout without blocking caller thread
        val largePayload = ByteArray(512 * 1024) { 'X'.code.toByte() }
        val writeCmd = RootShellCommand.WriteStagingFile(
            stagingPath = "/data/misc/bluetooth/.bt_did.conf.tmp.tx_timeout",
            expectedParentDir = "/data/misc/bluetooth",
            content = largePayload
        )
        val hangExecutor = ProcessRootCommandExecutor(
            suBinaryPathCandidates = emptyList(),
            commandBuilder = { _ ->
                if (isWindows) listOf("ping", "-n", "10", "127.0.0.1")
                else listOf("sh", "-c", "sleep 10")
            }
        )
        val startMs = System.currentTimeMillis()
        val timeoutRes = hangExecutor.execute(writeCmd, timeoutMs = 400L)
        val elapsedMs = System.currentTimeMillis() - startMs

        assertTrue("Expected command to time out, got: $timeoutRes", timeoutRes.timedOut)
        assertFalse(timeoutRes.isSuccess)
        assertTrue("Timeout should return promptly without waiting 10s (elapsed=${elapsedMs}ms)", elapsedMs < 5_000L)
    }

    @Test
    fun `ReadFileBase64 shell script and RootFileInspector enforce maxBytes limit`() {
        val path = "/data/misc/bluetooth/bt_did.conf"
        val cmd = RootShellCommand.ReadFileBase64(path, maxBytes = 64L)
        assertTrue(
            "ReadFileBase64 shell script must enforce maxBytes size limit",
            cmd.toShellScript().contains("-gt 64") && cmd.toShellScript().contains("FILE_TOO_LARGE")
        )

        val tempDir = java.nio.file.Files.createTempDirectory("inspector_maxbytes_test_").toFile()
        try {
            val vfs = VirtualFsRootCommandExecutor(tempDir)
            val inspector = RootFileInspector(vfs)
            val oversizedBytes = "# Oversized content\n[DID1]\nprimaryRecord = true\nvendorId = 0x0171\nproductId = 0x0443\n".toByteArray(Charsets.UTF_8)
            vfs.seedRegularFile(path, oversizedBytes)

            val smallLimitRes = inspector.readFileBytes(path, maxBytes = 16L)
            assertTrue("Expected readFileBytes to reject file larger than maxBytes", smallLimitRes.isFailure)
            assertTrue(smallLimitRes.exceptionOrNull()!!.message!!.contains("FILE_TOO_LARGE"))

            val sufficientLimitRes = inspector.readFileBytes(path, maxBytes = 1024L)
            assertTrue("Expected readFileBytes to succeed when within maxBytes", sufficientLimitRes.isSuccess)
            assertArrayEquals(oversizedBytes, sufficientLimitRes.getOrThrow())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `buildPatchedDidContent preserves original comments and remains idempotent across repeated calls`() {
        val originalWithComments = """
            # OEM Factory Comment Line 1
            ; Secondary Semicolon Comment Line 2
            [DID1]
            primaryRecord = true
            vendorId = 0x18D1
            productId = 0x4EE1
        """.trimIndent()

        val patched1 = BtDidConfValidator.buildPatchedDidContent(originalWithComments).getOrThrow()
        assertTrue("Original '#' comment must be preserved", patched1.contains("# OEM Factory Comment Line 1"))
        assertTrue("Original ';' comment must be preserved", patched1.contains("; Secondary Semicolon Comment Line 2"))
        assertTrue(BtDidConfValidator.containsKindleRemoteDid(patched1))

        val patched2 = BtDidConfValidator.buildPatchedDidContent(patched1).getOrThrow()
        assertEquals("Repeated patching must be strictly idempotent without duplicating headers or comments", patched1, patched2)
    }
}
