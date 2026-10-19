package com.kindle.pageturner.root

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [RootFileTransactionManager.executeModification] covering all 11 Phase 10 modification scenarios.
 */
class RootFileTransactionModifyTest {

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

    private val sampleOriginalText = """
        # OEM Default Bluetooth DID
        [DID1]
        primaryRecord = true
        vendorIdSource = 0x0002
        vendorId = 0x18D1
        productId = 0x4EE1
        version = 0x0100
    """.trimIndent() + "\n"

    private val sampleNewText = BtDidConfValidator.buildPatchedDidContent(sampleOriginalText).getOrThrow()

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("kindle_root_modify_test_").toFile()
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
    fun `normal modification creates verified backup, transitions through all phases, and commits`() {
        val originalBytes = sampleOriginalText.toByteArray(Charsets.UTF_8)
        executor.seedRegularFile(
            canonicalPath = targetPath,
            content = originalBytes,
            ownerUid = 1002,
            groupGid = 1002,
            permissionsOctal = "0640",
            selinuxContext = "u:object_r:bluetooth_data_file:s0"
        )

        val observedPhases = mutableListOf<TransactionPhase>()
        transactionManager.onPhasePersistedHook = { tx ->
            observedPhases.add(tx.phase)
        }

        val newBytes = sampleNewText.toByteArray(Charsets.UTF_8)
        val outcome = transactionManager.executeModification(
            targetPath = targetPath,
            newContentBytes = newBytes,
            userConfirmed = true,
            expectedBeforeSha256 = Sha256Utils.sha256Hex(originalBytes),
            expectedBeforeExists = true
        )

        assertTrue("Expected Committed outcome, got $outcome", outcome is ModificationOutcome.Committed)
        val committed = outcome as ModificationOutcome.Committed

        assertEquals(
            listOf(
                TransactionPhase.PREPARED,
                TransactionPhase.BACKUP_VERIFIED,
                TransactionPhase.APPLYING,
                TransactionPhase.APPLIED,
                TransactionPhase.VERIFYING,
                TransactionPhase.COMMITTED
            ),
            observedPhases
        )

        assertEquals(Sha256Utils.sha256Hex(newBytes), committed.afterMetadata.sha256)
        assertEquals("0640", committed.afterMetadata.permissionsOctal)
        assertEquals(1002, committed.afterMetadata.ownerUid)
        assertEquals("u:object_r:bluetooth_data_file:s0", committed.afterMetadata.selinuxContext)
        assertArrayEquals(newBytes, executor.resolveHostFile(targetPath).readBytes())

        // Verify original backup is intact
        val backupVer = backupManager.verifyBackup(committed.backup.backupId)
        assertTrue(backupVer is BackupVerificationResult.Valid)
        assertArrayEquals(originalBytes, (backupVer as BackupVerificationResult.Valid).contentBytes)
    }

    @Test
    fun `modification when Root authorization fails is rejected before touching file`() {
        val originalBytes = sampleOriginalText.toByteArray(Charsets.UTF_8)
        executor.seedRegularFile(targetPath, originalBytes)
        executor.rootAuthorized = false

        val outcome = transactionManager.executeModification(
            targetPath = targetPath,
            newContentBytes = sampleNewText.toByteArray(Charsets.UTF_8),
            userConfirmed = true
        )

        assertTrue(outcome is ModificationOutcome.RejectedPrecheck)
        assertTrue((outcome as ModificationOutcome.RejectedPrecheck).reason.contains("Root"))
        assertArrayEquals(originalBytes, executor.resolveHostFile(targetPath).readBytes())
        assertTrue(transactionManager.listTransactions(targetPath).isEmpty())
    }

    @Test
    fun `modification when directory is read-only or path is read-only system partition is rejected`() {
        val originalBytes = sampleOriginalText.toByteArray(Charsets.UTF_8)
        executor.seedRegularFile(targetPath, originalBytes)
        executor.setDirectoryWriteStatus("/data/misc/bluetooth", DirectoryWriteStatus.READ_ONLY)

        val outcome1 = transactionManager.executeModification(
            targetPath = targetPath,
            newContentBytes = sampleNewText.toByteArray(Charsets.UTF_8),
            userConfirmed = true
        )
        assertTrue(outcome1 is ModificationOutcome.RejectedPrecheck)
        assertTrue((outcome1 as ModificationOutcome.RejectedPrecheck).reason.contains("不可写"))

        // Also test attempting to modify /system/etc/bluetooth/bt_did.conf directly
        executor.setDirectoryWriteStatus("/data/misc/bluetooth", DirectoryWriteStatus.WRITABLE)
        val outcome2 = transactionManager.executeModification(
            targetPath = systemTemplatePath,
            newContentBytes = sampleNewText.toByteArray(Charsets.UTF_8),
            userConfirmed = true
        )
        assertTrue(outcome2 is ModificationOutcome.RejectedPrecheck)
        assertTrue((outcome2 as ModificationOutcome.RejectedPrecheck).reason.contains("只读系统分区"))
    }

    @Test
    fun `modification when target file changed before execution is rejected`() {
        val initialBytes = sampleOriginalText.toByteArray(Charsets.UTF_8)
        val initialSha = Sha256Utils.sha256Hex(initialBytes)

        // Simulate external change right before executeModification runs
        val externallyModifiedBytes = sampleOriginalText.replace("0x18D1", "0x04E8").toByteArray(Charsets.UTF_8)
        executor.seedRegularFile(targetPath, externallyModifiedBytes)

        val outcome = transactionManager.executeModification(
            targetPath = targetPath,
            newContentBytes = sampleNewText.toByteArray(Charsets.UTF_8),
            userConfirmed = true,
            expectedBeforeSha256 = initialSha,
            expectedBeforeExists = true
        )

        assertTrue(outcome is ModificationOutcome.RejectedPrecheck)
        assertTrue((outcome as ModificationOutcome.RejectedPrecheck).reason.contains("操作前已被外部修改"))
        assertArrayEquals(externallyModifiedBytes, executor.resolveHostFile(targetPath).readBytes())
    }

    @Test
    fun `modification command failure triggers automatic rollback and preserves original file`() {
        val originalBytes = sampleOriginalText.toByteArray(Charsets.UTF_8)
        executor.seedRegularFile(targetPath, originalBytes)

        // Fail AtomicRename on the main modification, but allow rollback commands to succeed
        var failedOnce = false
        executor.commandInterceptor = { cmd ->
            if (cmd is RootShellCommand.AtomicRename && !failedOnce) {
                failedOnce = true
                CommandResult(exitCode = 1, stdout = "", stderr = "mv: simulated I/O error")
            } else {
                null
            }
        }

        val outcome = transactionManager.executeModification(
            targetPath = targetPath,
            newContentBytes = sampleNewText.toByteArray(Charsets.UTF_8),
            userConfirmed = true
        )

        assertTrue("Expected RolledBack, got $outcome", outcome is ModificationOutcome.RolledBack)
        val rolledBack = outcome as ModificationOutcome.RolledBack
        assertEquals(TransactionPhase.ROLLED_BACK, rolledBack.transaction.phase)
        assertArrayEquals(originalBytes, executor.resolveHostFile(targetPath).readBytes())
    }

    @Test
    fun `command timeout during write triggers automatic rollback`() {
        val originalBytes = sampleOriginalText.toByteArray(Charsets.UTF_8)
        executor.seedRegularFile(targetPath, originalBytes)

        var timedOutOnce = false
        executor.commandInterceptor = { cmd ->
            if (cmd is RootShellCommand.WriteStagingFile && !timedOutOnce) {
                timedOutOnce = true
                CommandResult(
                    exitCode = -1,
                    stdout = "",
                    stderr = "",
                    timedOut = true,
                    durationMs = 8000L,
                    exceptionMessage = "Root 命令执行超时 (8000ms)"
                )
            } else {
                null
            }
        }

        val outcome = transactionManager.executeModification(
            targetPath = targetPath,
            newContentBytes = sampleNewText.toByteArray(Charsets.UTF_8),
            userConfirmed = true
        )

        assertTrue(outcome is ModificationOutcome.RolledBack)
        assertTrue((outcome as ModificationOutcome.RolledBack).reason.contains("超时"))
        assertArrayEquals(originalBytes, executor.resolveHostFile(targetPath).readBytes())
    }

    @Test
    fun `partial write to staging file is caught by hash check before atomic move and rolled back`() {
        val originalBytes = sampleOriginalText.toByteArray(Charsets.UTF_8)
        executor.seedRegularFile(targetPath, originalBytes)

        // Truncate staging write to 12 bytes on the first WriteStagingFile call
        var injectedPartial = false
        executor.commandInterceptor = { cmd ->
            if (cmd is RootShellCommand.WriteStagingFile && !injectedPartial) {
                injectedPartial = true
                val hostFile = executor.resolveHostFile(cmd.stagingPath)
                hostFile.parentFile?.mkdirs()
                hostFile.writeBytes(cmd.content.copyOf(12))
                CommandResult(exitCode = 0, stdout = "", stderr = "")
            } else {
                null
            }
        }

        val outcome = transactionManager.executeModification(
            targetPath = targetPath,
            newContentBytes = sampleNewText.toByteArray(Charsets.UTF_8),
            userConfirmed = true
        )

        assertTrue("Partial staging write must roll back: $outcome", outcome is ModificationOutcome.RolledBack)
        assertTrue((outcome as ModificationOutcome.RolledBack).reason.contains("临时文件写后校验失败"))
        assertArrayEquals(originalBytes, executor.resolveHostFile(targetPath).readBytes())
    }

    @Test
    fun `post-write target hash mismatch triggers rollback and restores original file`() {
        val originalBytes = sampleOriginalText.toByteArray(Charsets.UTF_8)
        executor.seedRegularFile(targetPath, originalBytes)

        // Corrupt target file only on the first AtomicRename (the modification step), then clear so rollback succeeds
        var corruptedOnce = false
        executor.commandInterceptor = { cmd ->
            if (cmd is RootShellCommand.AtomicRename && !corruptedOnce) {
                corruptedOnce = true
                executor.corruptTargetAfterAtomicMove = "# CORRUPTED TARGET\n".toByteArray()
                val res = executor.execute(cmd) // will recurse unless we clear interceptor first
                res
            } else {
                null
            }
        }
        // Better: use corruptTargetAfterAtomicMove directly and clear it inside onPhasePersistedHook when ROLLING_BACK starts!
        executor.commandInterceptor = null
        executor.corruptTargetAfterAtomicMove = "# CORRUPTED BY FAULT INJECTION\n[DID1]\nvendorId = 0x9999\n".toByteArray()
        transactionManager.onPhasePersistedHook = { tx ->
            if (tx.phase == TransactionPhase.ROLLBACK_REQUIRED) {
                executor.corruptTargetAfterAtomicMove = null
            }
        }

        val outcome = transactionManager.executeModification(
            targetPath = targetPath,
            newContentBytes = sampleNewText.toByteArray(Charsets.UTF_8),
            userConfirmed = true
        )

        assertTrue("Expected RolledBack on post-write hash mismatch, got $outcome", outcome is ModificationOutcome.RolledBack)
        assertTrue((outcome as ModificationOutcome.RolledBack).reason.contains("SHA-256 校验不匹配"))
        assertArrayEquals(originalBytes, executor.resolveHostFile(targetPath).readBytes())
    }

    @Test
    fun `metadata or selinux context failure triggers rollback and restores original file`() {
        val originalBytes = sampleOriginalText.toByteArray(Charsets.UTF_8)
        executor.seedRegularFile(
            canonicalPath = targetPath,
            content = originalBytes,
            permissionsOctal = "0640",
            selinuxContext = "u:object_r:bluetooth_data_file:s0"
        )

        // Simulate metadata update silently failing in the filesystem during APPLYING, then fixed for rollback
        executor.ignoreMetadataUpdateInFs = true
        transactionManager.onPhasePersistedHook = { tx ->
            if (tx.phase == TransactionPhase.ROLLBACK_REQUIRED) {
                executor.ignoreMetadataUpdateInFs = false
            }
        }

        val outcome = transactionManager.executeModification(
            targetPath = targetPath,
            newContentBytes = sampleNewText.toByteArray(Charsets.UTF_8),
            userConfirmed = true
        )

        assertTrue("Expected RolledBack on metadata verification mismatch, got $outcome", outcome is ModificationOutcome.RolledBack)
        assertTrue((outcome as ModificationOutcome.RolledBack).reason.contains("元数据不符合预期"))
        assertArrayEquals(originalBytes, executor.resolveHostFile(targetPath).readBytes())
    }

    @Test
    fun `concurrent modifications on the same file reject the second operation safely`() {
        val originalBytes = sampleOriginalText.toByteArray(Charsets.UTF_8)
        executor.seedRegularFile(targetPath, originalBytes)

        val enteredApplyingLatch = CountDownLatch(1)
        val allowFinishLatch = CountDownLatch(1)

        transactionManager.onPhasePersistedHook = { tx ->
            if (tx.phase == TransactionPhase.APPLYING) {
                enteredApplyingLatch.countDown()
                allowFinishLatch.await(3, TimeUnit.SECONDS)
            }
        }

        val firstOutcomeRef = AtomicReference<ModificationOutcome>()
        val firstThread = Thread {
            val res = transactionManager.executeModification(
                targetPath = targetPath,
                newContentBytes = sampleNewText.toByteArray(Charsets.UTF_8),
                userConfirmed = true
            )
            firstOutcomeRef.set(res)
        }.apply { start() }

        assertTrue(enteredApplyingLatch.await(3, TimeUnit.SECONDS))

        // While first modification is in APPLYING, launch a second concurrent modification
        val secondOutcome = transactionManager.executeModification(
            targetPath = targetPath,
            newContentBytes = sampleNewText.toByteArray(Charsets.UTF_8),
            userConfirmed = true
        )

        allowFinishLatch.countDown()
        firstThread.join(3000)

        assertTrue("Second concurrent operation must be rejected", secondOutcome is ModificationOutcome.RejectedPrecheck)
        assertTrue((secondOutcome as ModificationOutcome.RejectedPrecheck).reason.contains("并发"))
        assertTrue("First operation must complete cleanly", firstOutcomeRef.get() is ModificationOutcome.Committed)
    }

    @Test
    fun `symlink directory special file or unknown format is rejected without modifying target`() {
        // 1. Symlink
        executor.seedSpecialFile(targetPath, FileType.SYMLINK)
        val symOutcome = transactionManager.executeModification(
            targetPath = targetPath,
            newContentBytes = sampleNewText.toByteArray(Charsets.UTF_8),
            userConfirmed = true
        )
        assertTrue(symOutcome is ModificationOutcome.RejectedPrecheck)
        assertTrue((symOutcome as ModificationOutcome.RejectedPrecheck).reason.contains("类型不受支持"))

        // 2. Directory
        executor.seedSpecialFile(targetPath, FileType.DIRECTORY)
        val dirOutcome = transactionManager.executeModification(
            targetPath = targetPath,
            newContentBytes = sampleNewText.toByteArray(Charsets.UTF_8),
            userConfirmed = true
        )
        assertTrue(dirOutcome is ModificationOutcome.RejectedPrecheck)

        // 3. Unknown binary / non-INI format in existing file
        val unknownFormatBytes = "NOT_AN_INI_FILE_RANDOM_SYNTAX_12345\n{{{bad}}}".toByteArray(Charsets.UTF_8)
        executor.seedRegularFile(targetPath, unknownFormatBytes)
        val fmtOutcome = transactionManager.executeModification(
            targetPath = targetPath,
            newContentBytes = sampleNewText.toByteArray(Charsets.UTF_8),
            userConfirmed = true
        )
        assertTrue(fmtOutcome is ModificationOutcome.RejectedPrecheck)
        assertTrue((fmtOutcome as ModificationOutcome.RejectedPrecheck).reason.contains("格式未知或损坏"))
        assertArrayEquals(unknownFormatBytes, executor.resolveHostFile(targetPath).readBytes())
    }

    @Test
    fun `rollback metadata failure transitions transaction to RecoveryRequired instead of falsely claiming RolledBack`() {
        val originalBytes = sampleOriginalText.toByteArray(Charsets.UTF_8)
        executor.seedRegularFile(
            canonicalPath = targetPath,
            content = originalBytes,
            permissionsOctal = "0640",
            selinuxContext = "u:object_r:bluetooth_data_file:s0"
        )

        // Fail metadata updates permanently (both during initial apply and during rollback)
        executor.ignoreMetadataUpdateInFs = true

        val outcome = transactionManager.executeModification(
            targetPath = targetPath,
            newContentBytes = sampleNewText.toByteArray(Charsets.UTF_8),
            userConfirmed = true
        )

        assertTrue(
            "When rollback cannot restore original metadata, outcome must be RecoveryRequired, got $outcome",
            outcome is ModificationOutcome.RecoveryRequired
        )
        val recoveryReq = outcome as ModificationOutcome.RecoveryRequired
        assertEquals(TransactionPhase.RECOVERY_REQUIRED, recoveryReq.transaction.phase)
        assertTrue(transactionManager.getUnfinishedTransactions(targetPath).isNotEmpty())
    }

    @Test
    fun `TOCTOU external modification between precheck and backup creation aborts safely without leaving stale lock`() {
        val originalBytes = sampleOriginalText.toByteArray(Charsets.UTF_8)
        executor.seedRegularFile(targetPath, originalBytes)

        // Mutate the file right when PREPARED is persisted (just before createAndVerifyBackup reads it)
        transactionManager.onPhasePersistedHook = { tx ->
            if (tx.phase == TransactionPhase.PREPARED) {
                executor.seedRegularFile(
                    targetPath,
                    "[DID1]\nprimaryRecord = true\nvendorId = 0x9999\nproductId = 0x8888\n".toByteArray()
                )
            }
        }

        val outcome = transactionManager.executeModification(
            targetPath = targetPath,
            newContentBytes = sampleNewText.toByteArray(Charsets.UTF_8),
            userConfirmed = true
        )

        assertTrue("Expected RejectedPrecheck on TOCTOU conflict, got $outcome", outcome is ModificationOutcome.RejectedPrecheck)
        assertTrue((outcome as ModificationOutcome.RejectedPrecheck).reason.contains("TOCTOU"))
        assertTrue("Aborted pre-write transaction must not leave unfinished lock", transactionManager.getUnfinishedTransactions(targetPath).isEmpty())
    }

    @Test
    fun `tampered or corrupted transaction log json is detected via checksum and surfaced as RECOVERY_REQUIRED`() {
        val originalBytes = sampleOriginalText.toByteArray(Charsets.UTF_8)
        executor.seedRegularFile(targetPath, originalBytes)

        val committed = transactionManager.executeModification(
            targetPath = targetPath,
            newContentBytes = sampleNewText.toByteArray(Charsets.UTF_8),
            userConfirmed = true
        ) as ModificationOutcome.Committed

        // Tamper with the persisted transaction file without updating recordChecksumSha256
        val txFile = File(transactionManager.transactionsDir, "${committed.transaction.transactionId}.json")
        val tamperedJson = txFile.readText(Charsets.UTF_8).replace("\"verificationPassed\": \"true\"", "\"verificationPassed\": \"false\"")
        txFile.writeText(tamperedJson, Charsets.UTF_8)

        val unfinished = transactionManager.getUnfinishedTransactions(targetPath)
        assertEquals("Tampered transaction record must be surfaced as unfinished RECOVERY_REQUIRED", 1, unfinished.size)
        assertEquals(TransactionPhase.RECOVERY_REQUIRED, unfinished.first().phase)
        assertTrue(unfinished.first().errorMessage!!.contains("损坏或校验和不匹配"))

        // Subsequent modifications are blocked until user resolves the corrupted transaction record
        val blocked = transactionManager.executeModification(
            targetPath = targetPath,
            newContentBytes = sampleNewText.toByteArray(Charsets.UTF_8),
            userConfirmed = true
        )
        assertTrue(blocked is ModificationOutcome.RejectedPrecheck)

        // User can explicitly resolve the corrupted record
        val resolvedRes = transactionManager.markTransactionResolvedByUser(
            committed.transaction.transactionId,
            "RESOLVED_CORRUPTED_LOG"
        )
        assertTrue(resolvedRes.isSuccess)
        assertTrue(transactionManager.getUnfinishedTransactions(targetPath).isEmpty())
    }

    @Test
    fun `listTransactions preserves exact chronological sequence across mixed tx_mod and tx_restore in the same millisecond`() {
        val originalBytes = sampleOriginalText.toByteArray(Charsets.UTF_8)
        executor.seedRegularFile(targetPath, originalBytes)

        val mod1 = transactionManager.executeModification(
            targetPath = targetPath,
            newContentBytes = sampleNewText.toByteArray(Charsets.UTF_8),
            userConfirmed = true
        ) as ModificationOutcome.Committed

        val restore1 = transactionManager.restoreFromBackup(
            backupId = mod1.backup.backupId,
            userConfirmed = true
        ) as RestoreOutcome.VerifiedSuccess

        val mod2 = transactionManager.executeModification(
            targetPath = targetPath,
            newContentBytes = sampleNewText.toByteArray(Charsets.UTF_8),
            userConfirmed = true
        ) as ModificationOutcome.Committed

        // Force all three transactions to share the exact same startedAtEpochMs
        val fixedEpochMs = 1760000000000L
        val orderedTxIds = listOf(
            mod1.transaction.transactionId,
            restore1.transaction.transactionId,
            mod2.transaction.transactionId
        )
        for (txId in orderedTxIds) {
            val f = File(transactionManager.transactionsDir, "$txId.json")
            val tx = RootTransactionRecord.fromJsonString(f.readText(Charsets.UTF_8))!!
            val updated = tx.copy(startedAtEpochMs = fixedEpochMs).withComputedChecksum()
            f.writeText(updated.toJsonString(), Charsets.UTF_8)
        }

        val listedIds = transactionManager.listTransactions(targetPath).map { it.transactionId }
        assertEquals(
            "listTransactions must sort mod2 (seq 3) before restore1 (seq 2) before mod1 (seq 1) even when timestamps match",
            orderedTxIds.reversed(),
            listedIds
        )
    }
}
