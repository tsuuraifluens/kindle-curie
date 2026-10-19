package com.kindle.pageturner.root

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [RootBackupManager] covering all Phase 10 backup test scenarios:
 * 1. Original file exists -> backed up and verified accurately.
 * 2. Original file does not exist -> `originalExisted = false` recorded accurately.
 * 3. Empty file (0 bytes) backup.
 * 4. Large file (512 KB) backup.
 * 5. Backup write / hash verification failure on creation.
 * 6. Backup content tampered on disk -> detected and blocked without overwriting stored hash.
 * 7. SHA-256 mismatch detection.
 * 8. Metadata missing, corrupted JSON, or incompatible schema version rejected.
 * 9. Repeated backups and backup ID collision protection.
 * 10. Backup cleanup retains all backups referenced by active/unfinished transactions and baseline backup.
 */
class RootBackupManagerTest {

    private lateinit var tempDir: File
    private lateinit var virtualFsDir: File
    private lateinit var storageDir: File
    private lateinit var executor: VirtualFsRootCommandExecutor
    private lateinit var inspector: RootFileInspector
    private lateinit var auditLogger: RootAuditLogger
    private lateinit var backupManager: RootBackupManager

    private val targetPath = ManagedTargetPath.DATA_BT_DID_CONF.canonicalPath

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("kindle_root_backup_test_").toFile()
        virtualFsDir = File(tempDir, "virtual_fs").apply { mkdirs() }
        storageDir = File(tempDir, "app_storage").apply { mkdirs() }
        executor = VirtualFsRootCommandExecutor(virtualFsDir)
        inspector = RootFileInspector(executor)
        auditLogger = RootAuditLogger(storageDir)
        backupManager = RootBackupManager(storageDir, inspector, auditLogger, appVersion = "1.0.0-test")
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `backup when original file exists records content sha256 and metadata accurately`() {
        val originalText = """
            [DID1]
            primaryRecord = true
            vendorId = 0x18D1
            productId = 0x4EE1
            version = 0x0100
        """.trimIndent() + "\n"
        val originalBytes = originalText.toByteArray(Charsets.UTF_8)
        executor.seedRegularFile(
            canonicalPath = targetPath,
            content = originalBytes,
            ownerUid = 1002,
            ownerUser = "bluetooth",
            groupGid = 1002,
            groupName = "bluetooth",
            permissionsOctal = "0660",
            selinuxContext = "u:object_r:bluetooth_data_file:s0"
        )

        val res = backupManager.createAndVerifyBackup(
            targetPath = targetPath,
            transactionId = "tx_001",
            operationId = "op_001"
        )
        assertTrue("Backup should succeed: ${res.exceptionOrNull()?.message}", res.isSuccess)
        val record = res.getOrThrow()

        assertTrue(record.originalExisted)
        assertEquals(Sha256Utils.sha256Hex(originalBytes), record.contentSha256)
        assertEquals(originalBytes.size.toLong(), record.originalMetadata.sizeBytes)
        assertEquals("0660", record.originalMetadata.permissionsOctal)
        assertEquals(1002, record.originalMetadata.ownerUid)
        assertEquals(1002, record.originalMetadata.groupGid)
        assertEquals("u:object_r:bluetooth_data_file:s0", record.originalMetadata.selinuxContext)

        val verification = backupManager.verifyBackup(record.backupId)
        assertTrue(verification is BackupVerificationResult.Valid)
        val valid = verification as BackupVerificationResult.Valid
        assertArrayEquals(originalBytes, valid.contentBytes)
    }

    @Test
    fun `backup when original file does not exist records NOT_EXIST state without creating blob`() {
        executor.removeSeededFile(targetPath)

        val res = backupManager.createAndVerifyBackup(
            targetPath = targetPath,
            transactionId = "tx_not_exist",
            operationId = "op_not_exist"
        )
        assertTrue(res.isSuccess)
        val record = res.getOrThrow()

        assertFalse(record.originalExisted)
        assertEquals(FileType.NOT_EXIST, record.originalMetadata.fileType)
        assertNull(record.contentFileName)
        assertNull(record.contentSha256)

        val verification = backupManager.verifyBackup(record.backupId)
        assertTrue(verification is BackupVerificationResult.Valid)
        assertNull((verification as BackupVerificationResult.Valid).contentBytes)
    }

    @Test
    fun `backup of empty 0-byte file succeeds and verifies sha256 of empty bytes`() {
        val emptyBytes = ByteArray(0)
        executor.seedRegularFile(targetPath, emptyBytes)

        val res = backupManager.createAndVerifyBackup(targetPath, "tx_empty", "op_empty")
        assertTrue(res.isSuccess)
        val record = res.getOrThrow()

        assertTrue(record.originalExisted)
        assertEquals(0L, record.originalMetadata.sizeBytes)
        assertEquals(Sha256Utils.sha256Hex(emptyBytes), record.contentSha256)

        val verification = backupManager.verifyBackup(record.backupId)
        assertTrue(verification is BackupVerificationResult.Valid)
        assertEquals(0, (verification as BackupVerificationResult.Valid).contentBytes?.size)
    }

    @Test
    fun `backup of large file 512KB succeeds and verifies exact bytes`() {
        val sb = StringBuilder()
        repeat(10000) { idx ->
            sb.append("# Comment line $idx for large bt_did.conf test\n")
        }
        sb.append("[DID1]\nprimaryRecord = true\nvendorId = 0x0171\nproductId = 0x0443\n")
        val largeBytes = sb.toString().toByteArray(Charsets.UTF_8)
        assertTrue(largeBytes.size > 400_000)

        executor.seedRegularFile(targetPath, largeBytes)
        val res = backupManager.createAndVerifyBackup(targetPath, "tx_large", "op_large")
        assertTrue(res.isSuccess)
        val record = res.getOrThrow()

        val verification = backupManager.verifyBackup(record.backupId)
        assertTrue(verification is BackupVerificationResult.Valid)
        assertArrayEquals(largeBytes, (verification as BackupVerificationResult.Valid).contentBytes)
    }

    @Test
    fun `backup creation fails when blob write verification detects corruption`() {
        val originalBytes = "[DID1]\nvendorId = 0x18D1\n".toByteArray()
        executor.seedRegularFile(targetPath, originalBytes)

        // Inject fault that corrupts bytes during backup blob write
        backupManager.testBlobTransformHook = { bytes ->
            bytes + "CORRUPTED".toByteArray()
        }

        val res = backupManager.createAndVerifyBackup(targetPath, "tx_fail_write", "op_fail_write")
        assertTrue("Backup should fail when written blob does not match original hash", res.isFailure)
        assertTrue("No invalid backup record should be committed", backupManager.listBackups(targetPath).isEmpty())
    }

    @Test
    fun `tampered backup blob on disk is detected by verifyBackup and never overwrites original hash`() {
        val originalBytes = "[DID1]\nvendorId = 0x18D1\n".toByteArray()
        val expectedOriginalSha = Sha256Utils.sha256Hex(originalBytes)
        executor.seedRegularFile(targetPath, originalBytes)

        val record = backupManager.createAndVerifyBackup(targetPath, "tx_tamper", "op_tamper").getOrThrow()
        val blobFile = File(backupManager.blobsDir, record.contentFileName!!)

        // Tamper with the stored backup blob keeping the same byte length
        val tamperedBytes = "[DID1]\nvendorId = 0x9999\n".toByteArray()
        assertEquals(originalBytes.size, tamperedBytes.size)
        blobFile.writeBytes(tamperedBytes)

        val verification = backupManager.verifyBackup(record.backupId)
        assertTrue(verification is BackupVerificationResult.Invalid)
        val invalid = verification as BackupVerificationResult.Invalid
        assertTrue(invalid.reason.contains("SHA-256"))

        // Verify original record hash was NOT overwritten with tampered hash
        val storedRecord = backupManager.listBackups(targetPath).first { it.backupId == record.backupId }
        assertEquals(expectedOriginalSha, storedRecord.contentSha256)
    }

    @Test
    fun `corrupted metadata json or incompatible schema version or checksum mismatch is rejected`() {
        val originalBytes = "[DID1]\nvendorId = 0x18D1\n".toByteArray()
        executor.seedRegularFile(targetPath, originalBytes)

        val rec1 = backupManager.createAndVerifyBackup(targetPath, "tx_meta1", "op_meta1").getOrThrow()
        val recFile1 = File(backupManager.recordsDir, "${rec1.backupId}.json")

        // 1. Tamper with targetPath or contentSha256 inside metadata without updating checksum
        val rawJson = recFile1.readText()
        recFile1.writeText(rawJson.replace("\"0660\"", "\"0777\""))
        val v1 = backupManager.verifyBackup(rec1.backupId)
        assertTrue("Tampered metadata must be rejected due to checksum mismatch", v1 is BackupVerificationResult.Invalid)

        // 2. Incompatible schemaVersion (even with recomputed checksum)
        val rec2 = backupManager.createAndVerifyBackup(targetPath, "tx_meta2", "op_meta2").getOrThrow()
        val futureVersionRec = rec2.copy(schemaVersion = 999).withComputedChecksum()
        File(backupManager.recordsDir, "${rec2.backupId}.json").writeText(futureVersionRec.toJsonString())
        val v2 = backupManager.verifyBackup(rec2.backupId)
        assertTrue("Incompatible schemaVersion must be rejected", v2 is BackupVerificationResult.Invalid)
        assertTrue((v2 as BackupVerificationResult.Invalid).reason.contains("版本不兼容"))

        // 3. Corrupted malformed JSON
        val rec3 = backupManager.createAndVerifyBackup(targetPath, "tx_meta3", "op_meta3").getOrThrow()
        File(backupManager.recordsDir, "${rec3.backupId}.json").writeText("{broken_json")
        val v3 = backupManager.verifyBackup(rec3.backupId)
        assertTrue("Malformed JSON must be rejected", v3 is BackupVerificationResult.Invalid)
    }

    @Test
    fun `repeated backups generate distinct IDs and duplicate customBackupId collision is rejected`() {
        val originalBytes = "[DID1]\nvendorId = 0x18D1\n".toByteArray()
        executor.seedRegularFile(targetPath, originalBytes)

        val b1 = backupManager.createAndVerifyBackup(
            targetPath = targetPath,
            transactionId = "tx_1",
            operationId = "op_1",
            customBackupId = "bak_custom_fixed_01"
        ).getOrThrow()

        // Attempting to create another backup with the same ID must fail without overwriting b1
        executor.seedRegularFile(targetPath, "[DID1]\nvendorId = 0x0171\n".toByteArray())
        val collisionRes = backupManager.createAndVerifyBackup(
            targetPath = targetPath,
            transactionId = "tx_2",
            operationId = "op_2",
            customBackupId = "bak_custom_fixed_01"
        )
        assertTrue("Duplicate backupId must be rejected", collisionRes.isFailure)

        // Verify original backup b1 was NOT overwritten
        val verB1 = backupManager.verifyBackup(b1.backupId) as BackupVerificationResult.Valid
        assertEquals(Sha256Utils.sha256Hex(originalBytes), verB1.record.contentSha256)

        // Auto-generated IDs never collide across multiple sequential calls
        val auto1 = backupManager.createAndVerifyBackup(targetPath, "tx_a1", "op_a1").getOrThrow()
        val auto2 = backupManager.createAndVerifyBackup(targetPath, "tx_a2", "op_a2").getOrThrow()
        assertNotEquals(auto1.backupId, auto2.backupId)
    }

    @Test
    fun `cleanupBackups preserves baseline original backup and all active transaction backups`() {
        executor.seedRegularFile(targetPath, "[DID1]\nvendorId = 0x1111\n".toByteArray())
        val baseline = backupManager.createAndVerifyBackup(targetPath, "tx_base", "op_base").getOrThrow()
        Thread.sleep(5)

        val b2 = backupManager.createAndVerifyBackup(targetPath, "tx_2", "op_2").getOrThrow()
        Thread.sleep(5)
        val b3Active = backupManager.createAndVerifyBackup(targetPath, "tx_3_active", "op_3").getOrThrow()
        Thread.sleep(5)
        val b4 = backupManager.createAndVerifyBackup(targetPath, "tx_4", "op_4").getOrThrow()
        Thread.sleep(5)
        val b5Latest = backupManager.createAndVerifyBackup(targetPath, "tx_5", "op_5").getOrThrow()

        // Request cleanup keeping only 1 recent backup, while b3Active is referenced by an unfinished transaction
        val cleanupRes = backupManager.cleanupBackups(
            protectedBackupIds = setOf(b3Active.backupId),
            keepRecentCount = 1
        )

        // Baseline, b3Active, and b5Latest must be retained; b2 and b4 can be cleaned
        assertTrue(baseline.backupId in cleanupRes.retainedBackupIds)
        assertTrue(b3Active.backupId in cleanupRes.retainedBackupIds)
        assertTrue(b5Latest.backupId in cleanupRes.retainedBackupIds)
        assertTrue(b2.backupId in cleanupRes.deletedBackupIds)
        assertTrue(b4.backupId in cleanupRes.deletedBackupIds)

        assertTrue(backupManager.verifyBackup(baseline.backupId) is BackupVerificationResult.Valid)
        assertTrue(backupManager.verifyBackup(b3Active.backupId) is BackupVerificationResult.Valid)
    }

    @Test
    fun `getBaselineOriginalBackup and listBackups preserve exact chronological order across 12 backups in the same millisecond`() {
        val createdIds = mutableListOf<String>()
        for (i in 1..12) {
            val content = "[DID1]\nprimaryRecord = true\nvendorId = ${String.format("0x%04X", i)}\nproductId = 0x0443\n".toByteArray()
            executor.seedRegularFile(targetPath, content)
            val rec = backupManager.createAndVerifyBackup(targetPath, "tx_$i", "op_$i").getOrThrow()
            createdIds.add(rec.backupId)
        }

        // Force all 12 records on disk to share the exact same createdAtEpochMs to deterministically test tie-breaking
        val fixedEpochMs = 1760000000000L
        for (id in createdIds) {
            val recFile = File(backupManager.recordsDir, "$id.json")
            val rec = RootBackupRecord.fromJsonString(recFile.readText(Charsets.UTF_8))!!
            val updated = rec.copy(createdAtEpochMs = fixedEpochMs).withComputedChecksum()
            recFile.writeText(updated.toJsonString(), Charsets.UTF_8)
        }

        val listedIds = backupManager.listBackups(targetPath).map { it.backupId }
        assertEquals("listBackups must return exact reverse creation order (12 down to 1)", createdIds.reversed(), listedIds)

        val baseline = backupManager.getBaselineOriginalBackup(targetPath)
        assertNotNull(baseline)
        assertEquals("getBaselineOriginalBackup must return the very first backup (index 0), not the last", createdIds.first(), baseline!!.record.backupId)
    }
}
