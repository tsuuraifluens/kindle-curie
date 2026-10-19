package com.kindle.pageturner.root

import java.io.File
import java.util.Base64

/**
 * In-memory + Temp-Directory backed implementation of [RootCommandExecutor] for unit and fault-injection tests.
 *
 * Maps canonical Android paths (e.g. `/data/misc/bluetooth/bt_did.conf`) to a local temporary directory
 * so unit tests never touch host system files while exercising 100% of the production
 * `RootFileInspector`, `RootBackupManager`, `RootFileTransactionManager`, and `RootRecoveryManager` code.
 */
class VirtualFsRootCommandExecutor(
    val virtualRootDir: File
) : RootCommandExecutor {

    data class SimulatedMeta(
        var fileType: FileType = FileType.REGULAR_FILE,
        var ownerUid: Int = 1002,
        var ownerUser: String = "bluetooth",
        var groupGid: Int = 1002,
        var groupName: String = "bluetooth",
        var permissionsOctal: String = "0660",
        var selinuxContext: String = "u:object_r:bluetooth_data_file:s0"
    )

    var suBinaryExists: Boolean = true
    var rootAuthorized: Boolean = true
    var selinuxEnforceMode: String = "Enforcing"

    private val dirWriteStates = mutableMapOf(
        "/data/misc/bluetooth" to DirectoryWriteStatus.WRITABLE,
        "/system/etc/bluetooth" to DirectoryWriteStatus.READ_ONLY
    )

    private val metaMap = mutableMapOf<String, SimulatedMeta>()

    // Fault injection hooks
    var commandInterceptor: ((RootShellCommand) -> CommandResult?)? = null
    var partialWriteBytesLimit: Int? = null
    var corruptTargetAfterAtomicMove: ByteArray? = null
    var failApplyMetadataOnPathPrefix: String? = null
    var ignoreMetadataUpdateInFs: Boolean = false

    val executedCommands = mutableListOf<RootShellCommand>()

    init {
        File(virtualRootDir, "data/misc/bluetooth").mkdirs()
        File(virtualRootDir, "system/etc/bluetooth").mkdirs()
    }

    fun resolveHostFile(canonicalPath: String): File {
        val rel = canonicalPath.removePrefix("/")
        return File(virtualRootDir, rel)
    }

    fun seedRegularFile(
        canonicalPath: String,
        content: ByteArray,
        ownerUid: Int = 1002,
        ownerUser: String = "bluetooth",
        groupGid: Int = 1002,
        groupName: String = "bluetooth",
        permissionsOctal: String = "0660",
        selinuxContext: String = "u:object_r:bluetooth_data_file:s0"
    ) {
        val f = resolveHostFile(canonicalPath)
        f.parentFile?.mkdirs()
        f.writeBytes(content)
        metaMap[canonicalPath] = SimulatedMeta(
            fileType = FileType.REGULAR_FILE,
            ownerUid = ownerUid,
            ownerUser = ownerUser,
            groupGid = groupGid,
            groupName = groupName,
            permissionsOctal = permissionsOctal,
            selinuxContext = selinuxContext
        )
    }

    fun seedSpecialFile(canonicalPath: String, type: FileType) {
        val f = resolveHostFile(canonicalPath)
        f.parentFile?.mkdirs()
        if (type == FileType.DIRECTORY) {
            f.mkdirs()
        } else {
            f.writeBytes(ByteArray(0))
        }
        metaMap[canonicalPath] = SimulatedMeta(fileType = type)
    }

    fun removeSeededFile(canonicalPath: String) {
        val f = resolveHostFile(canonicalPath)
        if (f.exists()) f.deleteRecursively()
        metaMap.remove(canonicalPath)
    }

    fun setDirectoryWriteStatus(dirPath: String, status: DirectoryWriteStatus) {
        dirWriteStates[dirPath] = status
    }

    override fun isSuBinaryPresent(): Boolean = suBinaryExists

    @Synchronized
    override fun execute(command: RootShellCommand, timeoutMs: Long): CommandResult {
        executedCommands.add(command)

        // Allow fault-injection interceptor to override any command
        commandInterceptor?.invoke(command)?.let { return it }

        if (!suBinaryExists) {
            return CommandResult(exitCode = 127, stdout = "", stderr = "su: not found")
        }
        if (!rootAuthorized) {
            return CommandResult(exitCode = 1, stdout = "", stderr = "Permission denied")
        }

        return when (command) {
            is RootShellCommand.CheckSuId -> {
                CommandResult(
                    exitCode = 0,
                    stdout = "uid=0(root) gid=0(root) groups=0(root) context=u:r:magisk:s0",
                    stderr = ""
                )
            }
            is RootShellCommand.GetSelinuxEnforce -> {
                CommandResult(exitCode = 0, stdout = selinuxEnforceMode, stderr = "")
            }
            is RootShellCommand.CheckDirectoryStatus -> {
                val status = dirWriteStates[command.dirPath] ?: DirectoryWriteStatus.MISSING
                val out = when (status) {
                    DirectoryWriteStatus.WRITABLE -> "DIR=WRITABLE"
                    DirectoryWriteStatus.READ_ONLY -> "DIR=READONLY"
                    DirectoryWriteStatus.MISSING -> "DIR=MISSING"
                    DirectoryWriteStatus.ERROR -> "DIR=ERROR"
                }
                CommandResult(exitCode = 0, stdout = out, stderr = "")
            }
            is RootShellCommand.StatAndHashFile -> {
                val hostFile = resolveHostFile(command.path)
                val meta = metaMap[command.path]
                if (meta != null && meta.fileType != FileType.REGULAR_FILE) {
                    val typeName = when (meta.fileType) {
                        FileType.SYMLINK -> "SYMLINK"
                        FileType.DIRECTORY -> "DIRECTORY"
                        FileType.OTHER_SPECIAL -> "OTHER_SPECIAL"
                        FileType.NOT_EXIST -> "NOT_EXIST"
                        else -> "OTHER_SPECIAL"
                    }
                    return CommandResult(exitCode = 0, stdout = "TYPE=$typeName", stderr = "")
                }
                if (!hostFile.exists()) {
                    return CommandResult(exitCode = 0, stdout = "TYPE=NOT_EXIST", stderr = "")
                }
                if (hostFile.isDirectory) {
                    return CommandResult(exitCode = 0, stdout = "TYPE=DIRECTORY", stderr = "")
                }
                val bytes = hostFile.readBytes()
                val sha = Sha256Utils.sha256Hex(bytes)
                val effectiveMeta = meta ?: SimulatedMeta()
                val mtimeSec = hostFile.lastModified() / 1000L
                val stdout = buildString {
                    append("TYPE=REGULAR_FILE\n")
                    append("META=${bytes.size}|${effectiveMeta.permissionsOctal}|${effectiveMeta.ownerUid}|${effectiveMeta.ownerUser}|${effectiveMeta.groupGid}|${effectiveMeta.groupName}|$mtimeSec\n")
                    append("SELINUX=${effectiveMeta.selinuxContext}\n")
                    append("SHA256=$sha")
                }
                CommandResult(exitCode = 0, stdout = stdout, stderr = "")
            }
            is RootShellCommand.ReadFileBase64 -> {
                val hostFile = resolveHostFile(command.path)
                val meta = metaMap[command.path]
                if (!hostFile.exists() || (meta != null && meta.fileType != FileType.REGULAR_FILE)) {
                    return CommandResult(exitCode = 2, stdout = "", stderr = "Not a regular file")
                }
                val bytes = hostFile.readBytes()
                if (bytes.size > command.maxBytes) {
                    return CommandResult(exitCode = 3, stdout = "", stderr = "FILE_TOO_LARGE")
                }
                val b64 = Base64.getEncoder().encodeToString(bytes)
                CommandResult(exitCode = 0, stdout = b64, stderr = "")
            }
            is RootShellCommand.WriteStagingFile -> {
                val dirStatus = dirWriteStates[command.expectedParentDir]
                if (dirStatus != DirectoryWriteStatus.WRITABLE) {
                    return CommandResult(exitCode = 1, stdout = "", stderr = "Read-only file system")
                }
                val hostFile = resolveHostFile(command.stagingPath)
                hostFile.parentFile?.mkdirs()
                val payload = command.content
                val actualBytes = partialWriteBytesLimit?.let { limit ->
                    payload.copyOf(limit.coerceIn(0, payload.size))
                } ?: payload
                hostFile.writeBytes(actualBytes)
                metaMap[command.stagingPath] = SimulatedMeta(
                    fileType = FileType.REGULAR_FILE,
                    ownerUid = 0,
                    ownerUser = "root",
                    groupGid = 0,
                    groupName = "root",
                    permissionsOctal = "0600",
                    selinuxContext = "u:object_r:bluetooth_data_file:s0"
                )
                CommandResult(exitCode = 0, stdout = "", stderr = "")
            }
            is RootShellCommand.ApplyMetadata -> {
                val failPrefix = failApplyMetadataOnPathPrefix
                if (failPrefix != null && command.path.startsWith(failPrefix)) {
                    return CommandResult(exitCode = 1, stdout = "", stderr = "chcon/chmod simulated failure on ${command.path}")
                }
                val hostFile = resolveHostFile(command.path)
                if (!hostFile.exists()) {
                    return CommandResult(exitCode = 1, stdout = "", stderr = "No such file: ${command.path}")
                }
                if (!ignoreMetadataUpdateInFs) {
                    val existing = metaMap.getOrPut(command.path) { SimulatedMeta() }
                    existing.ownerUid = command.ownerUid
                    existing.ownerUser = if (command.ownerUid == 1002) "bluetooth" else "root"
                    existing.groupGid = command.groupGid
                    existing.groupName = if (command.groupGid == 1002) "bluetooth" else "root"
                    existing.permissionsOctal = if (command.modeOctal.length == 3) "0${command.modeOctal}" else command.modeOctal
                    val ctx = command.selinuxContext
                    if (!ctx.isNullOrBlank()) {
                        existing.selinuxContext = ctx
                    }
                }
                CommandResult(exitCode = 0, stdout = "", stderr = "")
            }
            is RootShellCommand.AtomicRename -> {
                val srcFile = resolveHostFile(command.stagingPath)
                val dstFile = resolveHostFile(command.targetPath)
                if (!srcFile.exists()) {
                    return CommandResult(exitCode = 1, stdout = "", stderr = "Staging file missing")
                }
                val dstMeta = metaMap[command.targetPath]
                if (dstMeta != null && (dstMeta.fileType == FileType.SYMLINK || dstMeta.fileType == FileType.DIRECTORY)) {
                    return CommandResult(exitCode = 3, stdout = "", stderr = "Target is symlink or directory")
                }
                dstFile.parentFile?.mkdirs()
                if (dstFile.exists()) {
                    dstFile.delete()
                }
                val bytesToWrite = corruptTargetAfterAtomicMove ?: srcFile.readBytes()
                dstFile.writeBytes(bytesToWrite)
                srcFile.delete()
                val stageMeta = metaMap.remove(command.stagingPath) ?: SimulatedMeta()
                metaMap[command.targetPath] = stageMeta.copy()
                CommandResult(exitCode = 0, stdout = "", stderr = "")
            }
            is RootShellCommand.DeleteFile -> {
                val targetFile = resolveHostFile(command.path)
                val meta = metaMap[command.path]
                if (meta != null && (meta.fileType == FileType.SYMLINK || meta.fileType == FileType.DIRECTORY)) {
                    return CommandResult(exitCode = 3, stdout = "", stderr = "Refusing to delete symlink or directory")
                }
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                metaMap.remove(command.path)
                CommandResult(exitCode = 0, stdout = "", stderr = "")
            }
        }
    }
}
