package com.kindle.pageturner.root

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Fault-injection tests simulating abrupt process kill / crash / device reboot at every critical
 * transaction stage and verifying [RootRecoveryManager] startup behavior upon re-initialization.
 */
class RootCrashAndRecoveryTest {

    private class SimulatedProcessKillException(val killedAtStage: String) : RuntimeException("Process killed at $killedAtStage")

    private lateinit var tempDir: File
    private lateinit var virtualFsDir: File
    private lateinit var storageDir: File
    private lateinit var executor: VirtualFsRootCommandExecutor

    private val targetPath = ManagedTargetPath.DATA_BT_DID_CONF.canonicalPath

    private val originalBytes = """
        [DID1]
        primaryRecord = true
        vendorIdSource = 0x0002
        vendorId = 0x18D1
        productId = 0x4EE1
        version = 0x0100
    """.trimIndent().toByteArray(Charsets.UTF_8)

    private val newBytes = BtDidConfValidator.buildPatchedDidContent(String(originalBytes)).getOrThrow().toByteArray(Charsets.UTF_8)

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("kindle_root_crash_test_").toFile()
        virtualFsDir = File(tempDir, "virtual_fs").apply { mkdirs() }
        storageDir = File(tempDir, "app_storage").apply { mkdirs() }
        executor = VirtualFsRootCommandExecutor(virtualFsDir)
        executor.seedRegularFile(targetPath, originalBytes)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private data class ManagerStack(
        val inspector: RootFileInspector,
        val auditLogger: RootAuditLogger,
        val backupManager: RootBackupManager,
        val transactionManager: RootFileTransactionManager,
        val recoveryManager: RootRecoveryManager
    )

    private fun createFreshManagerStack(): ManagerStack {
        val inspector = RootFileInspector(executor)
        val auditLogger = RootAuditLogger(storageDir)
        val backupManager = RootBackupManager(storageDir, inspector, auditLogger)
        val transactionManager = RootFileTransactionManager(storageDir, executor, inspector, backupManager, auditLogger)
        val recoveryManager = RootRecoveryManager(executor, inspector, backupManager, transactionManager, auditLogger)
        return ManagerStack(inspector, auditLogger, backupManager, transactionManager, recoveryManager)
    }

    @Test
    fun `stage 1 - crash before backup completes cleans orphan tmp files on restart and leaves file intact`() {
        val stack1 = createFreshManagerStack()
        // Leave an uncommitted .tmp file and throw during backup creation
        File(stack1.backupManager.blobsDir, "bak_incomplete.bin.tmp").writeBytes("partial".toByteArray())
        stack1.backupManager.testBlobTransformHook = {
            throw SimulatedProcessKillException("DURING_BACKUP_WRITE")
        }

        val outcome = stack1.transactionManager.executeModification(
            targetPath = targetPath,
            newContentBytes = newBytes,
            userConfirmed = true
        )
        assertTrue(outcome is ModificationOutcome.RejectedPrecheck)

        // Simulate app restart with fresh manager instances
        val stack2 = createFreshManagerStack()
        val report = stack2.recoveryManager.inspectStartupRecovery()

        assertFalse("No unfinished transaction should exist if killed before backup finished", report.hasPendingRecovery)
        assertTrue("Orphan .tmp file must be cleaned up", report.orphanTempFilesCleaned >= 1)
        assertArrayEquals(originalBytes, executor.resolveHostFile(targetPath).readBytes())
    }

    @Test
    fun `stage 2 - crash after backup verified before modify detects MATCHES_ORIGINAL_BACKUP and blocks unsafe overwrite`() {
        val stack1 = createFreshManagerStack()
        stack1.transactionManager.onPhasePersistedHook = { tx ->
            if (tx.phase == TransactionPhase.BACKUP_VERIFIED) {
                throw SimulatedProcessKillException("BACKUP_VERIFIED")
            }
        }

        try {
            stack1.transactionManager.executeModification(targetPath, newBytes, userConfirmed = true)
            fail("Expected SimulatedProcessKillException")
        } catch (_: SimulatedProcessKillException) {
        }

        // Re-initialize fresh managers (simulating reboot / process restart)
        val stack2 = createFreshManagerStack()
        val report = stack2.recoveryManager.inspectStartupRecovery()

        assertTrue(report.hasPendingRecovery)
        assertEquals(1, report.pendingItems.size)
        val item = report.pendingItems.first()
        assertEquals(TransactionPhase.BACKUP_VERIFIED, item.interruptedAtPhase)
        assertEquals(TransactionPhase.RECOVERY_REQUIRED, item.transaction.phase)
        assertTrue(item.backupValid)
        assertEquals(TargetStateDiagnosis.MATCHES_ORIGINAL_BACKUP, item.diagnosis)

        // Ensure startup did NOT modify the file
        assertArrayEquals(originalBytes, executor.resolveHostFile(targetPath).readBytes())

        // Attempting a new modification while RECOVERY_REQUIRED is pending must be blocked
        val blockedAttempt = stack2.transactionManager.executeModification(targetPath, newBytes, userConfirmed = true)
        assertTrue(blockedAttempt is ModificationOutcome.RejectedPrecheck)
        assertTrue((blockedAttempt as ModificationOutcome.RejectedPrecheck).reason.contains("未完成或待恢复的事务"))
    }

    @Test
    fun `stage 3 - crash during APPLYING before atomic rename diagnoses MATCHES_ORIGINAL_BACKUP and preserves backup`() {
        val stack1 = createFreshManagerStack()
        stack1.transactionManager.onPhasePersistedHook = { tx ->
            if (tx.phase == TransactionPhase.APPLYING) {
                throw SimulatedProcessKillException("APPLYING")
            }
        }

        try {
            stack1.transactionManager.executeModification(targetPath, newBytes, userConfirmed = true)
            fail("Expected SimulatedProcessKillException")
        } catch (_: SimulatedProcessKillException) {
        }

        val stack2 = createFreshManagerStack()
        val report = stack2.recoveryManager.inspectStartupRecovery()

        assertTrue(report.hasPendingRecovery)
        val item = report.pendingItems.first()
        assertEquals(TransactionPhase.APPLYING, item.interruptedAtPhase)
        assertEquals(TargetStateDiagnosis.MATCHES_ORIGINAL_BACKUP, item.diagnosis)
        assertTrue(item.backupValid)
        assertArrayEquals(originalBytes, executor.resolveHostFile(targetPath).readBytes())
    }

    @Test
    fun `stage 4 - crash at APPLIED after file modified but before verification diagnoses MATCHES_EXPECTED_MODIFIED and allows clean restore`() {
        val stack1 = createFreshManagerStack()
        stack1.transactionManager.onPhasePersistedHook = { tx ->
            if (tx.phase == TransactionPhase.APPLIED) {
                throw SimulatedProcessKillException("APPLIED")
            }
        }

        try {
            stack1.transactionManager.executeModification(targetPath, newBytes, userConfirmed = true)
            fail("Expected SimulatedProcessKillException")
        } catch (_: SimulatedProcessKillException) {
        }

        // Target file on disk now has newBytes, but transaction was interrupted at APPLIED
        assertArrayEquals(newBytes, executor.resolveHostFile(targetPath).readBytes())

        // Restart app
        val stack2 = createFreshManagerStack()
        val report = stack2.recoveryManager.inspectStartupRecovery()

        assertTrue(report.hasPendingRecovery)
        val item = report.pendingItems.first()
        assertEquals(TransactionPhase.APPLIED, item.interruptedAtPhase)
        assertEquals(TargetStateDiagnosis.MATCHES_EXPECTED_MODIFIED, item.diagnosis)
        assertTrue(item.backupValid)

        // Startup must NOT silently overwrite or auto-restore the file
        assertArrayEquals(newBytes, executor.resolveHostFile(targetPath).readBytes())

        // User initiates manual restore from the preserved backup
        val restoreRes = stack2.transactionManager.restoreFromBackup(
            backupId = item.transaction.backupId,
            userConfirmed = true
        )
        assertTrue(restoreRes is RestoreOutcome.VerifiedSuccess)
        assertArrayEquals(originalBytes, executor.resolveHostFile(targetPath).readBytes())

        // After verified restore, pending recovery should be resolved
        assertTrue(stack2.transactionManager.getUnfinishedTransactions(targetPath).isEmpty())
    }

    @Test
    fun `stage 5 - crash at VERIFYING before COMMITTED is persisted diagnoses MATCHES_EXPECTED_MODIFIED`() {
        val stack1 = createFreshManagerStack()
        stack1.transactionManager.onPhasePersistedHook = { tx ->
            if (tx.phase == TransactionPhase.VERIFYING) {
                throw SimulatedProcessKillException("VERIFYING")
            }
        }

        try {
            stack1.transactionManager.executeModification(targetPath, newBytes, userConfirmed = true)
            fail("Expected SimulatedProcessKillException")
        } catch (_: SimulatedProcessKillException) {
        }

        val stack2 = createFreshManagerStack()
        val report = stack2.recoveryManager.inspectStartupRecovery()

        assertTrue(report.hasPendingRecovery)
        val item = report.pendingItems.first()
        assertEquals(TransactionPhase.VERIFYING, item.interruptedAtPhase)
        assertEquals(TargetStateDiagnosis.MATCHES_EXPECTED_MODIFIED, item.diagnosis)
        assertTrue(item.backupValid)
    }

    @Test
    fun `stage 6 - crash during ROLLING_BACK with corrupted intermediate file diagnoses UNCERTAIN_INTERMEDIATE_OR_EXTERNAL`() {
        val stack1 = createFreshManagerStack()
        val intermediateCorruptedBytes = "# PARTIAL CORRUPTED INTERMEDIATE CONTENT\n".toByteArray(Charsets.UTF_8)

        executor.corruptTargetAfterAtomicMove = intermediateCorruptedBytes
        stack1.transactionManager.onPhasePersistedHook = { tx ->
            if (tx.phase == TransactionPhase.ROLLING_BACK) {
                throw SimulatedProcessKillException("ROLLING_BACK")
            }
        }

        try {
            stack1.transactionManager.executeModification(targetPath, newBytes, userConfirmed = true)
            fail("Expected SimulatedProcessKillException")
        } catch (_: SimulatedProcessKillException) {
        }

        executor.corruptTargetAfterAtomicMove = null

        // Restart app
        val stack2 = createFreshManagerStack()
        val report = stack2.recoveryManager.inspectStartupRecovery()

        assertTrue(report.hasPendingRecovery)
        val item = report.pendingItems.first()
        assertEquals(TransactionPhase.ROLLING_BACK, item.interruptedAtPhase)
        assertEquals(TargetStateDiagnosis.UNCERTAIN_INTERMEDIATE_OR_EXTERNAL, item.diagnosis)
        assertTrue(item.backupValid)

        // Startup must NOT automatically overwrite the uncertain file
        assertArrayEquals(intermediateCorruptedBytes, executor.resolveHostFile(targetPath).readBytes())

        // Attempting restore without conflict confirmation detects the divergent state
        val conflictRes = stack2.transactionManager.restoreFromBackup(
            backupId = item.transaction.backupId,
            userConfirmed = true,
            forceOverwriteConflict = false
        )
        assertTrue(conflictRes is RestoreOutcome.ConflictDetected)

        // User confirms conflict -> creates snapshot of intermediate file and restores original backup
        val forcedRes = stack2.transactionManager.restoreFromBackup(
            backupId = item.transaction.backupId,
            userConfirmed = true,
            forceOverwriteConflict = true
        )
        assertTrue(forcedRes is RestoreOutcome.VerifiedSuccess)
        assertArrayEquals(originalBytes, executor.resolveHostFile(targetPath).readBytes())
    }

    @Test
    fun `stage 7 - crash after rollback restored file on disk but before ROLLED_BACK was recorded diagnoses MATCHES_ORIGINAL_BACKUP`() {
        val stack1 = createFreshManagerStack()

        // Trigger rollback by corrupting the first atomic move, then let rollback finish on disk and kill right as ROLLED_BACK is persisted
        executor.corruptTargetAfterAtomicMove = "# CORRUPTED FIRST MOVE\n".toByteArray()
        stack1.transactionManager.onPhasePersistedHook = { tx ->
            if (tx.phase == TransactionPhase.ROLLBACK_REQUIRED) {
                executor.corruptTargetAfterAtomicMove = null
            }
            if (tx.phase == TransactionPhase.ROLLED_BACK) {
                // Revert the persisted transaction file back to ROLLING_BACK to simulate crash right after disk rollback but before ROLLED_BACK log flush
                val unrecordedRollbackTx = tx.copy(
                    phase = TransactionPhase.ROLLING_BACK,
                    completedAtEpochMs = null,
                    recoveryResolution = null
                ).withComputedChecksum()
                stack1.transactionManager.persistTransactionToDisk(unrecordedRollbackTx)
                throw SimulatedProcessKillException("BEFORE_ROLLED_BACK_LOGGED")
            }
        }

        try {
            stack1.transactionManager.executeModification(targetPath, newBytes, userConfirmed = true)
            fail("Expected SimulatedProcessKillException")
        } catch (_: SimulatedProcessKillException) {
        }

        // File on disk is already rolled back to originalBytes, while transaction log still says ROLLING_BACK
        assertArrayEquals(originalBytes, executor.resolveHostFile(targetPath).readBytes())

        val stack2 = createFreshManagerStack()
        val report = stack2.recoveryManager.inspectStartupRecovery()

        assertTrue(report.hasPendingRecovery)
        val item = report.pendingItems.first()
        assertEquals(TransactionPhase.ROLLING_BACK, item.interruptedAtPhase)
        assertEquals(TargetStateDiagnosis.MATCHES_ORIGINAL_BACKUP, item.diagnosis)
        assertTrue(item.backupValid)
    }

    @Test
    fun `stage 1b - abrupt kill right at PREPARED before backup finishes diagnoses MATCHES_ORIGINAL_BACKUP via preModifyMetadata`() {
        val stack1 = createFreshManagerStack()
        stack1.transactionManager.onPhasePersistedHook = { tx ->
            if (tx.phase == TransactionPhase.PREPARED) {
                throw SimulatedProcessKillException("PREPARED_BEFORE_BACKUP")
            }
        }

        try {
            stack1.transactionManager.executeModification(targetPath, newBytes, userConfirmed = true)
            fail("Expected SimulatedProcessKillException")
        } catch (_: SimulatedProcessKillException) {
        }

        val stack2 = createFreshManagerStack()
        val report = stack2.recoveryManager.inspectStartupRecovery()

        assertTrue(report.hasPendingRecovery)
        val item = report.pendingItems.first()
        assertEquals(TransactionPhase.PREPARED, item.interruptedAtPhase)
        assertFalse("Backup had not finished yet when killed at PREPARED", item.backupValid)
        assertEquals(
            "Even without completed backup, preModifyMetadata proves target file is untouched",
            TargetStateDiagnosis.MATCHES_ORIGINAL_BACKUP,
            item.diagnosis
        )
    }

    @Test
    fun `startup recovery cleans up orphan root staging files left by crashed APPLYING transaction`() {
        val stack1 = createFreshManagerStack()
        var crashedTxId: String? = null
        stack1.transactionManager.onPhasePersistedHook = { tx ->
            if (tx.phase == TransactionPhase.APPLYING) {
                crashedTxId = tx.transactionId
                // Simulate staging file written right before process kill
                val stagingPath = "/data/misc/bluetooth/.bt_did.conf.tmp.${tx.transactionId}"
                executor.seedRegularFile(stagingPath, newBytes)
                throw SimulatedProcessKillException("APPLYING_AFTER_STAGING_WRITE")
            }
        }

        try {
            stack1.transactionManager.executeModification(targetPath, newBytes, userConfirmed = true)
            fail("Expected SimulatedProcessKillException")
        } catch (_: SimulatedProcessKillException) {
        }

        val stagingPath = "/data/misc/bluetooth/.bt_did.conf.tmp.$crashedTxId"
        assertTrue("Staging file should exist before startup recovery", executor.resolveHostFile(stagingPath).exists())

        val stack2 = createFreshManagerStack()
        val report = stack2.recoveryManager.inspectStartupRecovery()

        assertTrue(report.hasPendingRecovery)
        assertTrue("Orphan root staging file must be counted in cleaned temp files", report.orphanTempFilesCleaned >= 1)
        assertFalse("Orphan root staging file must be deleted on recovery", executor.resolveHostFile(stagingPath).exists())
    }

    @Test
    fun `concurrent inspectStartupRecovery while modification is in flight does not hijack active transaction`() {
        val stack = createFreshManagerStack()
        var concurrentReport: StartupRecoveryReport? = null

        stack.transactionManager.onPhasePersistedHook = { tx ->
            if (tx.phase == TransactionPhase.APPLYING) {
                // Invoke inspectStartupRecovery concurrently while executeModification is in flight
                concurrentReport = stack.recoveryManager.inspectStartupRecovery()
            }
        }

        val outcome = stack.transactionManager.executeModification(targetPath, newBytes, userConfirmed = true)
        assertTrue("Active modification must complete cleanly, got $outcome", outcome is ModificationOutcome.Committed)
        assertNotNull(concurrentReport)
        assertFalse("In-flight transaction must not be flagged as crashed by concurrent recovery scan", concurrentReport!!.hasPendingRecovery)
    }

    @Test
    fun `inspectStartupRecovery diagnoses UNCERTAIN_INTERMEDIATE_OR_EXTERNAL when content SHA-256 matches backup but metadata is broken`() {
        val stack1 = createFreshManagerStack()
        // Ignore metadata updates so rollback writes original bytes back with 0600 root:root instead of 0640 bluetooth:bluetooth
        executor.ignoreMetadataUpdateInFs = true

        val outcome = stack1.transactionManager.executeModification(targetPath, newBytes, userConfirmed = true)
        assertTrue("Expected RecoveryRequired when rollback metadata fails, got $outcome", outcome is ModificationOutcome.RecoveryRequired)

        // Content on disk matches originalBytes, but metadata is broken (0600 root:root instead of 0640 bluetooth:bluetooth)
        assertArrayEquals(originalBytes, executor.resolveHostFile(targetPath).readBytes())

        val stack2 = createFreshManagerStack()
        val report = stack2.recoveryManager.inspectStartupRecovery()
        assertTrue(report.hasPendingRecovery)
        val item = report.pendingItems.first()
        assertEquals(
            "Broken metadata with matching content SHA-256 must NOT falsely diagnose MATCHES_ORIGINAL_BACKUP",
            TargetStateDiagnosis.UNCERTAIN_INTERMEDIATE_OR_EXTERNAL,
            item.diagnosis
        )
    }
}
