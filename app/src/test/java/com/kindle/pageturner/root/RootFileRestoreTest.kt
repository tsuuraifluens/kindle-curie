package com.kindle.pageturner.root

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [RootFileTransactionManager.restoreFromBackup] covering all 9 Phase 10 restoration scenarios.
 */
class RootFileRestoreTest {

    private lateinit var tempDir: File
    private lateinit var virtualFsDir: File
    private lateinit var storageDir: File
    private lateinit var executor: VirtualFsRootCommandExecutor
    private lateinit var inspector: RootFileInspector
    private lateinit var auditLogger: RootAuditLogger
    private lateinit var backupManager: RootBackupManager
    private lateinit var transactionManager: RootFileTransactionManager

    private val targetPath = ManagedTargetPath.DATA_BT_DID_CONF.canonicalPath
    private val systemTemplatePath = ManagedTargetPath.SYSTEM_BT_DID_CONF.canonicalPath

    private val originalText = """
        [DID1]
        primaryRecord = true
        vendorIdSource = 0x0002
        vendorId = 0x2717
        productId = 0xFF08
        version = 0x0100
    """.trimIndent() + "\n"

    private val modifiedText = BtDidConfValidator.buildPatchedDidContent(originalText).getOrThrow()

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("kindle_root_restore_test_").toFile()
        virtualFsDir = File(tempDir, "virtual_fs").apply { mkdirs() }
        storageDir = File(tempDir, "app_storage").apply { mkdirs() }
        executor = VirtualFsRootCommandExecutor(virtualFsDir)
        inspector = RootFileInspector(executor)
        auditLogger = RootAuditLogger(storageDir)
        backupManager = RootBackupManager(storageDir, inspector, auditLogger)
        transactionManager = RootFileTransactionManager(storageDir, executor, inspector, backupManager, auditLogger)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `normal restore and repeated restore verify sha256 and restore original metadata`() {
        val origBytes = originalText.toByteArray(Charsets.UTF_8)
        executor.seedRegularFile(
            canonicalPath = targetPath,
            content = origBytes,
            ownerUid = 1002,
            groupGid = 1002,
            permissionsOctal = "0640",
            selinuxContext = "u:object_r:bluetooth_data_file:s0"
        )

        val modOutcome = transactionManager.executeModification(
            targetPath = targetPath,
            newContentBytes = modifiedText.toByteArray(Charsets.UTF_8),
            userConfirmed = true
        ) as ModificationOutcome.Committed

        // 1. Normal restore
        val restore1 = transactionManager.restoreFromBackup(
            backupId = modOutcome.backup.backupId,
            userConfirmed = true
        )
        assertTrue("Expected VerifiedSuccess, got $restore1", restore1 is RestoreOutcome.VerifiedSuccess)
        val verified1 = restore1 as RestoreOutcome.VerifiedSuccess

        assertNotNull("Pre-restore protection snapshot should be created", verified1.preRestoreSnapshot)
        assertArrayEquals(origBytes, executor.resolveHostFile(targetPath).readBytes())
        assertEquals(Sha256Utils.sha256Hex(origBytes), verified1.afterMetadata.sha256)
        assertEquals("0640", verified1.afterMetadata.permissionsOctal)
        assertEquals(1002, verified1.afterMetadata.ownerUid)
        assertEquals("u:object_r:bluetooth_data_file:s0", verified1.afterMetadata.selinuxContext)

        // 2. Repeated restore (idempotency check)
        val restore2 = transactionManager.restoreFromBackup(
            backupId = modOutcome.backup.backupId,
            userConfirmed = true
        )
        assertTrue("Second repeated restore should also succeed: $restore2", restore2 is RestoreOutcome.VerifiedSuccess)
        assertArrayEquals(origBytes, executor.resolveHostFile(targetPath).readBytes())
    }

    @Test
    fun `restore when original file did not exist deletes the created file instead of leaving empty file`() {
        // Seed system template
        executor.seedRegularFile(systemTemplatePath, originalText.toByteArray(Charsets.UTF_8))
        executor.removeSeededFile(targetPath)
        assertFalse(executor.resolveHostFile(targetPath).exists())

        // By default, creating a non-existent /data/misc/bluetooth/bt_did.conf is rejected even if /system template exists
        val defaultRejected = transactionManager.executeModification(
            targetPath = targetPath,
            newContentBytes = modifiedText.toByteArray(Charsets.UTF_8),
            userConfirmed = true
        )
        assertTrue(
            "Creating a non-existent bt_did.conf must be rejected by default without explicit template override verification",
            defaultRejected is ModificationOutcome.RejectedPrecheck
        )

        val modOutcome = transactionManager.executeModification(
            targetPath = targetPath,
            newContentBytes = modifiedText.toByteArray(Charsets.UTF_8),
            userConfirmed = true,
            allowCreateFromVerifiedTemplate = true
        ) as ModificationOutcome.Committed

        assertFalse("Backup must record originalExisted = false", modOutcome.backup.originalExisted)
        assertTrue("Target file should exist after modification", executor.resolveHostFile(targetPath).exists())

        val restoreOutcome = transactionManager.restoreFromBackup(
            backupId = modOutcome.backup.backupId,
            userConfirmed = true
        )
        assertTrue("Expected VerifiedSuccess, got $restoreOutcome", restoreOutcome is RestoreOutcome.VerifiedSuccess)
        val verified = restoreOutcome as RestoreOutcome.VerifiedSuccess

        assertFalse("Restored file must NOT exist (not an empty file)", verified.afterMetadata.exists)
        assertEquals(FileType.NOT_EXIST, verified.afterMetadata.fileType)
        assertFalse("Host file must be deleted", executor.resolveHostFile(targetPath).exists())
    }

    @Test
    fun `restore is rejected when backup is corrupted and leaves target file untouched`() {
        val origBytes = originalText.toByteArray(Charsets.UTF_8)
        val modBytes = modifiedText.toByteArray(Charsets.UTF_8)
        executor.seedRegularFile(targetPath, origBytes)

        val modOutcome = transactionManager.executeModification(
            targetPath = targetPath,
            newContentBytes = modBytes,
            userConfirmed = true
        ) as ModificationOutcome.Committed

        // Corrupt the stored backup blob on disk
        val blobFile = File(backupManager.blobsDir, modOutcome.backup.contentFileName!!)
        blobFile.writeBytes("CORRUPTED_BACKUP_CONTENT".toByteArray())

        val restoreOutcome = transactionManager.restoreFromBackup(
            backupId = modOutcome.backup.backupId,
            userConfirmed = true
        )
        assertTrue(restoreOutcome is RestoreOutcome.RejectedPrecheck)
        assertTrue((restoreOutcome as RestoreOutcome.RejectedPrecheck).reason.contains("备份校验未通过"))
        assertArrayEquals(modBytes, executor.resolveHostFile(targetPath).readBytes())
    }

    @Test
    fun `exception or command failure during restore preserves both original backup and pre-restore snapshot`() {
        val origBytes = originalText.toByteArray(Charsets.UTF_8)
        val modBytes = modifiedText.toByteArray(Charsets.UTF_8)
        executor.seedRegularFile(targetPath, origBytes)

        val modOutcome = transactionManager.executeModification(
            targetPath = targetPath,
            newContentBytes = modBytes,
            userConfirmed = true
        ) as ModificationOutcome.Committed

        // Inject failure on AtomicRename during restore
        executor.commandInterceptor = { cmd ->
            if (cmd is RootShellCommand.AtomicRename) {
                CommandResult(exitCode = 1, stdout = "", stderr = "Simulated mv error during restore")
            } else {
                null
            }
        }

        val restoreOutcome = transactionManager.restoreFromBackup(
            backupId = modOutcome.backup.backupId,
            userConfirmed = true
        )
        assertTrue("Expected FailedRecoveryRequired, got $restoreOutcome", restoreOutcome is RestoreOutcome.FailedRecoveryRequired)
        val failed = restoreOutcome as RestoreOutcome.FailedRecoveryRequired

        assertEquals(TransactionPhase.RECOVERY_REQUIRED, failed.transaction?.phase)
        assertNotNull("Pre-restore protection snapshot must be preserved", failed.preRestoreSnapshot)
        assertTrue(backupManager.verifyBackup(modOutcome.backup.backupId) is BackupVerificationResult.Valid)
        assertTrue(backupManager.verifyBackup(failed.preRestoreSnapshot!!.backupId) is BackupVerificationResult.Valid)
    }

    @Test
    fun `hash mismatch after restore fails verification and preserves backups`() {
        val origBytes = originalText.toByteArray(Charsets.UTF_8)
        val modBytes = modifiedText.toByteArray(Charsets.UTF_8)
        executor.seedRegularFile(targetPath, origBytes)

        val modOutcome = transactionManager.executeModification(
            targetPath = targetPath,
            newContentBytes = modBytes,
            userConfirmed = true
        ) as ModificationOutcome.Committed

        executor.corruptTargetAfterAtomicMove = "# CORRUPTED AFTER RESTORE MOVE\n".toByteArray()

        val restoreOutcome = transactionManager.restoreFromBackup(
            backupId = modOutcome.backup.backupId,
            userConfirmed = true
        )
        assertTrue("Expected FailedRecoveryRequired, got $restoreOutcome", restoreOutcome is RestoreOutcome.FailedRecoveryRequired)
        val failed = restoreOutcome as RestoreOutcome.FailedRecoveryRequired
        assertTrue(failed.reason.contains("SHA-256 不匹配"))
        assertTrue(backupManager.verifyBackup(modOutcome.backup.backupId) is BackupVerificationResult.Valid)
        assertTrue(backupManager.verifyBackup(failed.preRestoreSnapshot!!.backupId) is BackupVerificationResult.Valid)
    }

    @Test
    fun `external modification before restore triggers ConflictDetected and only overwrites after confirmation with snapshot`() {
        val origBytes = originalText.toByteArray(Charsets.UTF_8)
        val modBytes = modifiedText.toByteArray(Charsets.UTF_8)
        executor.seedRegularFile(targetPath, origBytes)

        val modOutcome = transactionManager.executeModification(
            targetPath = targetPath,
            newContentBytes = modBytes,
            userConfirmed = true
        ) as ModificationOutcome.Committed

        // Simulate third-party modification to the target file
        val thirdPartyBytes = """
            # Modified by another tool
            [DID1]
            primaryRecord = true
            vendorId = 0x04E8
            productId = 0x7021
        """.trimIndent().toByteArray(Charsets.UTF_8)
        executor.seedRegularFile(targetPath, thirdPartyBytes)

        // 1. Restore without forceOverwriteConflict must detect conflict and NOT overwrite
        val conflictOutcome = transactionManager.restoreFromBackup(
            backupId = modOutcome.backup.backupId,
            userConfirmed = true,
            forceOverwriteConflict = false
        )
        assertTrue("Expected ConflictDetected, got $conflictOutcome", conflictOutcome is RestoreOutcome.ConflictDetected)
        val conflict = conflictOutcome as RestoreOutcome.ConflictDetected
        assertEquals(Sha256Utils.sha256Hex(thirdPartyBytes), conflict.currentMetadata.sha256)
        assertEquals(Sha256Utils.sha256Hex(modBytes), conflict.lastKnownExpectedSha256)
        assertArrayEquals("Third-party content must NOT be overwritten without confirmation", thirdPartyBytes, executor.resolveHostFile(targetPath).readBytes())

        // 2. Restore with forceOverwriteConflict = true saves snapshot of third-party state and restores original
        val forcedOutcome = transactionManager.restoreFromBackup(
            backupId = modOutcome.backup.backupId,
            userConfirmed = true,
            forceOverwriteConflict = true
        )
        assertTrue("Expected VerifiedSuccess after conflict confirmation, got $forcedOutcome", forcedOutcome is RestoreOutcome.VerifiedSuccess)
        val verified = forcedOutcome as RestoreOutcome.VerifiedSuccess
        assertNotNull(verified.preRestoreSnapshot)
        assertEquals(Sha256Utils.sha256Hex(thirdPartyBytes), verified.preRestoreSnapshot!!.contentSha256)
        assertArrayEquals(origBytes, executor.resolveHostFile(targetPath).readBytes())
    }

    @Test
    fun `metadata restoration failure reports PartialRestoreMetadataWarning instead of full success`() {
        val origBytes = originalText.toByteArray(Charsets.UTF_8)
        executor.seedRegularFile(
            canonicalPath = targetPath,
            content = origBytes,
            ownerUid = 1002,
            groupGid = 1002,
            permissionsOctal = "0640",
            selinuxContext = "u:object_r:bluetooth_data_file:s0"
        )

        val modOutcome = transactionManager.executeModification(
            targetPath = targetPath,
            newContentBytes = modifiedText.toByteArray(Charsets.UTF_8),
            userConfirmed = true
        ) as ModificationOutcome.Committed

        // Inject metadata failure on the target path during restore
        executor.failApplyMetadataOnPathPrefix = targetPath

        val restoreOutcome = transactionManager.restoreFromBackup(
            backupId = modOutcome.backup.backupId,
            userConfirmed = true
        )
        assertTrue(
            "Expected PartialRestoreMetadataWarning when metadata restore fails, got $restoreOutcome",
            restoreOutcome is RestoreOutcome.PartialRestoreMetadataWarning
        )
        val partial = restoreOutcome as RestoreOutcome.PartialRestoreMetadataWarning
        assertTrue(partial.metadataWarning.contains("部分恢复"))
        assertEquals(Sha256Utils.sha256Hex(origBytes), partial.afterMetadata.sha256)
        assertFalse("Transaction must not claim full verificationPassed", partial.transaction.verificationPassed)
        assertNull("Partial restore must keep recoveryResolution null until resolved", partial.transaction.recoveryResolution)
        assertEquals(
            "Partial restore must remain in getUnfinishedTransactions to prevent silent overwrite",
            1,
            transactionManager.getUnfinishedTransactions(targetPath).size
        )
    }

    @Test
    fun `restore applies original metadata to staging file before atomic rename`() {
        val origBytes = originalText.toByteArray(Charsets.UTF_8)
        executor.seedRegularFile(targetPath, origBytes)

        val modOutcome = transactionManager.executeModification(
            targetPath = targetPath,
            newContentBytes = modifiedText.toByteArray(Charsets.UTF_8),
            userConfirmed = true
        ) as ModificationOutcome.Committed

        executor.executedCommands.clear()
        val restoreOutcome = transactionManager.restoreFromBackup(
            backupId = modOutcome.backup.backupId,
            userConfirmed = true
        )
        assertTrue(restoreOutcome is RestoreOutcome.VerifiedSuccess)

        val applyStagingIdx = executor.executedCommands.indexOfFirst {
            it is RootShellCommand.ApplyMetadata && it.path.contains(".bt_did.conf.restore.")
        }
        val atomicRenameIdx = executor.executedCommands.indexOfFirst {
            it is RootShellCommand.AtomicRename
        }
        assertTrue("ApplyMetadata must be called on staging file", applyStagingIdx >= 0)
        assertTrue("AtomicRename must be called", atomicRenameIdx > applyStagingIdx)
    }

    @Test
    fun `restore succeeds on file but surfaces RestoredWithLogWriteFailure when final log write fails`() {
        val origBytes = originalText.toByteArray(Charsets.UTF_8)
        executor.seedRegularFile(targetPath, origBytes)

        val modOutcome = transactionManager.executeModification(
            targetPath = targetPath,
            newContentBytes = modifiedText.toByteArray(Charsets.UTF_8),
            userConfirmed = true
        ) as ModificationOutcome.Committed

        transactionManager.simulateFinalLogWriteFailure = true

        val restoreOutcome = transactionManager.restoreFromBackup(
            backupId = modOutcome.backup.backupId,
            userConfirmed = true
        )
        assertTrue(
            "Expected RestoredWithLogWriteFailure, got $restoreOutcome",
            restoreOutcome is RestoreOutcome.RestoredWithLogWriteFailure
        )
        val withLogWarning = restoreOutcome as RestoreOutcome.RestoredWithLogWriteFailure
        assertTrue(withLogWarning.logWriteError.contains("日志写入失败"))
        assertArrayEquals("Target file itself must be restored accurately", origBytes, executor.resolveHostFile(targetPath).readBytes())
    }

    @Test
    fun `pre-restore snapshot of external modification does not suppress ConflictDetected when restore fails before replacing target`() {
        val origBytes = originalText.toByteArray(Charsets.UTF_8)
        executor.seedRegularFile(targetPath, origBytes)

        val modOutcome = transactionManager.executeModification(
            targetPath = targetPath,
            newContentBytes = modifiedText.toByteArray(Charsets.UTF_8),
            userConfirmed = true
        ) as ModificationOutcome.Committed

        // External tool modifies target file
        val thirdPartyBytes = "# External third-party edit\n[DID1]\nprimaryRecord = true\nvendorId = 0x054C\nproductId = 0x0268\n".toByteArray(Charsets.UTF_8)
        executor.seedRegularFile(targetPath, thirdPartyBytes)

        // Forced restore creates a pre-restore snapshot (with thirdPartyBytes hash), then fails on WriteStagingFile
        var failedStagingOnce = false
        executor.commandInterceptor = { cmd ->
            if (cmd is RootShellCommand.WriteStagingFile && !failedStagingOnce) {
                failedStagingOnce = true
                CommandResult(exitCode = 1, stdout = "", stderr = "simulated staging write failure")
            } else {
                null
            }
        }

        val failedForcedRestore = transactionManager.restoreFromBackup(
            backupId = modOutcome.backup.backupId,
            userConfirmed = true,
            forceOverwriteConflict = true
        )
        assertTrue(failedForcedRestore is RestoreOutcome.FailedRecoveryRequired)
        assertArrayEquals("Target file still has third-party content", thirdPartyBytes, executor.resolveHostFile(targetPath).readBytes())

        // Subsequent non-forced restore must STILL detect the conflict with thirdPartyBytes instead of treating the preRestoreSnapshot as a managed state
        val retryWithoutForce = transactionManager.restoreFromBackup(
            backupId = modOutcome.backup.backupId,
            userConfirmed = true,
            forceOverwriteConflict = false
        )
        assertTrue(
            "Pre-restore snapshot must not pollute knownManagedHashes; expected ConflictDetected, got $retryWithoutForce",
            retryWithoutForce is RestoreOutcome.ConflictDetected
        )
    }
}
