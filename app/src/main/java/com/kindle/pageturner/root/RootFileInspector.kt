package com.kindle.pageturner.root

import java.util.Base64

enum class DirectoryWriteStatus {
    WRITABLE,
    READ_ONLY,
    MISSING,
    ERROR
}

/**
 * Inspects target system files, reads raw bytes safely, and checks directory status
 * using [RootCommandExecutor].
 */
class RootFileInspector(
    private val executor: RootCommandExecutor
) {

    fun inspectFile(
        path: String,
        allowStagingInDir: String? = null
    ): Result<RootFileMetadata> {
        val cmd = try {
            RootShellCommand.StatAndHashFile(path, allowStagingInDir)
        } catch (t: Throwable) {
            return Result.failure(t)
        }

        val res = executor.execute(cmd)
        if (!res.isSuccess) {
            val reason = res.exceptionMessage
                ?: if (res.timedOut) "探测文件超时" else "探测命令返回错误码 ${res.exitCode}: ${res.stderr}"
            return Result.failure(IllegalStateException("无法读取文件状态 '$path': $reason"))
        }

        val kv = mutableMapOf<String, String>()
        for (line in res.stdout.lines()) {
            val eqIdx = line.indexOf('=')
            if (eqIdx > 0) {
                val key = line.substring(0, eqIdx).trim()
                val value = line.substring(eqIdx + 1).trim()
                kv[key] = value
            }
        }

        val typeStr = kv["TYPE"]
            ?: return Result.failure(IllegalStateException("无法解析文件类型输出: '${res.stdout}'"))

        return when (typeStr) {
            "NOT_EXIST" -> Result.success(RootFileMetadata.notExist(path))
            "SYMLINK" -> Result.success(
                RootFileMetadata(
                    path = path,
                    exists = true,
                    fileType = FileType.SYMLINK
                )
            )
            "DIRECTORY" -> Result.success(
                RootFileMetadata(
                    path = path,
                    exists = true,
                    fileType = FileType.DIRECTORY
                )
            )
            "OTHER_SPECIAL" -> Result.success(
                RootFileMetadata(
                    path = path,
                    exists = true,
                    fileType = FileType.OTHER_SPECIAL
                )
            )
            "REGULAR_FILE" -> {
                val metaRaw = kv["META"]
                    ?: return Result.failure(IllegalStateException("缺失文件元数据字段 META ($path)"))
                val parts = metaRaw.split("|")
                if (parts.size < 7) {
                    return Result.failure(IllegalStateException("文件元数据字段不完整 ($metaRaw)"))
                }
                val sizeBytes = parts[0].toLongOrNull()
                    ?: return Result.failure(IllegalStateException("文件大小解析失败: '${parts[0]}'"))
                val rawOctal = parts[1].trim()
                val permissionsOctal = if (rawOctal.length == 3) "0$rawOctal" else rawOctal
                val ownerUid = parts[2].toIntOrNull()
                    ?: return Result.failure(IllegalStateException("文件 UID 解析失败: '${parts[2]}'"))
                val ownerUser = parts[3].takeIf { it.isNotBlank() }
                val groupGid = parts[4].toIntOrNull()
                    ?: return Result.failure(IllegalStateException("文件 GID 解析失败: '${parts[4]}'"))
                val groupName = parts[5].takeIf { it.isNotBlank() }
                val mtime = parts[6].toLongOrNull()
                val selinux = kv["SELINUX"]?.takeIf { it.isNotBlank() && it != "?" }
                val sha256 = kv["SHA256"]?.lowercase()?.takeIf { it.matches(Regex("^[0-9a-f]{64}$")) }
                    ?: return Result.failure(IllegalStateException("文件 SHA-256 校验值缺失或无效: '${kv["SHA256"]}'"))

                Result.success(
                    RootFileMetadata(
                        path = path,
                        exists = true,
                        fileType = FileType.REGULAR_FILE,
                        sizeBytes = sizeBytes,
                        sha256 = sha256,
                        ownerUid = ownerUid,
                        ownerUser = ownerUser,
                        groupGid = groupGid,
                        groupName = groupName,
                        permissionsOctal = permissionsOctal,
                        selinuxContext = selinux,
                        lastModifiedEpochSec = mtime
                    )
                )
            }
            else -> Result.failure(IllegalStateException("未知文件类型标识: '$typeStr'"))
        }
    }

    fun readFileBytes(
        path: String,
        maxBytes: Long = RootShellCommand.ReadFileBase64.MAX_READ_BYTES
    ): Result<ByteArray> {
        val cmd = try {
            RootShellCommand.ReadFileBase64(path, maxBytes = maxBytes)
        } catch (t: Throwable) {
            return Result.failure(t)
        }

        val res = executor.execute(cmd)
        if (!res.isSuccess) {
            val reason = res.exceptionMessage
                ?: if (res.timedOut) "读取文件内容超时" else "读取文件失败 (exit=${res.exitCode}): ${res.stderr}"
            return Result.failure(IllegalStateException("无法读取文件内容 '$path': $reason"))
        }

        return try {
            val cleaned = res.stdout.trim()
            if (cleaned.isEmpty()) {
                Result.success(ByteArray(0))
            } else {
                val decoded = Base64.getMimeDecoder().decode(cleaned)
                if (decoded.size > maxBytes) {
                    return Result.failure(
                        IllegalStateException("文件大小 (${decoded.size} bytes) 超过安全上限 ($maxBytes bytes)")
                    )
                }
                Result.success(decoded)
            }
        } catch (t: Throwable) {
            Result.failure(IllegalStateException("解码文件 Base64 内容失败: ${t.message}", t))
        }
    }

    fun checkDirectoryWritable(dirPath: String): DirectoryWriteStatus {
        val cmd = try {
            RootShellCommand.CheckDirectoryStatus(dirPath)
        } catch (_: Throwable) {
            return DirectoryWriteStatus.ERROR
        }
        val res = executor.execute(cmd)
        if (!res.isSuccess) return DirectoryWriteStatus.ERROR
        return when (res.stdout.trim()) {
            "DIR=WRITABLE" -> DirectoryWriteStatus.WRITABLE
            "DIR=READONLY" -> DirectoryWriteStatus.READ_ONLY
            "DIR=MISSING" -> DirectoryWriteStatus.MISSING
            else -> DirectoryWriteStatus.ERROR
        }
    }
}
