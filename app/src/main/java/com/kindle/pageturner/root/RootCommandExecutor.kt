package com.kindle.pageturner.root

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false,
    val durationMs: Long = 0L,
    val exceptionMessage: String? = null
) {
    val isSuccess: Boolean
        get() = !timedOut && exitCode == 0 && exceptionMessage == null
}

/**
 * Escapes single-quoted arguments for POSIX sh (`'...'`).
 */
object RootShellEscaper {
    fun quoteSingle(raw: String): String {
        require(!raw.contains('\u0000')) { "Shell argument cannot contain NUL character" }
        return "'" + raw.replace("'", "'\"'\"'") + "'"
    }
}

/**
 * Strongly-typed, parameter-whitelisted Root shell commands.
 * Prevents arbitrary string concatenation or command injection.
 */
sealed class RootShellCommand {

    abstract fun toShellScript(): String

    open fun stdinPayload(): ByteArray? = null

    data object CheckSuId : RootShellCommand() {
        override fun toShellScript(): String = "id"
    }

    data object GetSelinuxEnforce : RootShellCommand() {
        override fun toShellScript(): String = "getenforce 2>/dev/null || echo Unknown"
    }

    data class StatAndHashFile(
        val path: String,
        val allowStagingInDir: String? = null
    ) : RootShellCommand() {
        init {
            if (allowStagingInDir != null && path.startsWith("$allowStagingInDir/.")) {
                RootPathValidator.validateStagingPath(path, allowStagingInDir).getOrThrow()
            } else {
                RootPathValidator.validatePath(path, requireWritable = false).getOrThrow()
            }
        }

        override fun toShellScript(): String {
            val qPath = RootShellEscaper.quoteSingle(path)
            return buildString {
                append("P=").append(qPath).append("; ")
                append("if [ -L \"\$P\" ]; then echo 'TYPE=SYMLINK'; exit 0; fi; ")
                append("if [ ! -e \"\$P\" ]; then echo 'TYPE=NOT_EXIST'; exit 0; fi; ")
                append("if [ -d \"\$P\" ]; then echo 'TYPE=DIRECTORY'; exit 0; fi; ")
                append("if [ ! -f \"\$P\" ]; then echo 'TYPE=OTHER_SPECIAL'; exit 0; fi; ")
                append("echo 'TYPE=REGULAR_FILE'; ")
                append("stat -c 'META=%s|%a|%u|%U|%g|%G|%Y' \"\$P\" 2>/dev/null; ")
                append("CTX=\$(stat -c '%C' \"\$P\" 2>/dev/null); ")
                append("if [ -z \"\$CTX\" ] || [ \"\$CTX\" = '?' ] || [ \"\$CTX\" = '(null)' ]; then ")
                append("LSZ=\$(ls -Zd \"\$P\" 2>/dev/null); LSZ=\${LSZ#\"\${LSZ%%[! ]*}\"}; CTX=\${LSZ%% *}; ")
                append("fi; ")
                append("echo \"SELINUX=\$CTX\"; ")
                append("SHALINE=\$(sha256sum -b \"\$P\" 2>/dev/null || sha256sum \"\$P\" 2>/dev/null); ")
                append("SHA=\${SHALINE%% *}; ")
                append("echo \"SHA256=\$SHA\"")
            }
        }
    }

    data class ReadFileBase64(
        val path: String,
        val maxBytes: Long = MAX_READ_BYTES
    ) : RootShellCommand() {
        init {
            require(maxBytes in 1..(50 * 1024 * 1024L)) { "Invalid maxBytes: $maxBytes" }
            RootPathValidator.validatePath(path, requireWritable = false).getOrThrow()
        }

        override fun toShellScript(): String {
            val qPath = RootShellEscaper.quoteSingle(path)
            return "if [ -L $qPath ] || [ ! -f $qPath ]; then exit 2; fi; " +
                "SZ=\$(stat -c '%s' $qPath 2>/dev/null || wc -c < $qPath 2>/dev/null); " +
                "if [ -n \"\$SZ\" ] && [ \"\$SZ\" -gt $maxBytes ]; then echo 'FILE_TOO_LARGE' >&2; exit 3; fi; " +
                "base64 -w 0 $qPath 2>/dev/null || base64 $qPath"
        }

        companion object {
            const val MAX_READ_BYTES = 10 * 1024 * 1024L
        }
    }

    data class CheckDirectoryStatus(val dirPath: String) : RootShellCommand() {
        init {
            val validDirs = ManagedTargetPath.entries.map { it.parentDir }.toSet()
            require(dirPath in validDirs) { "目录不在白名单中: '$dirPath'" }
        }

        override fun toShellScript(): String {
            val qDir = RootShellEscaper.quoteSingle(dirPath)
            return "if [ ! -d $qDir ]; then echo 'DIR=MISSING'; exit 0; fi; " +
                "if [ -w $qDir ]; then echo 'DIR=WRITABLE'; else echo 'DIR=READONLY'; fi"
        }
    }

    data class WriteStagingFile(
        val stagingPath: String,
        val expectedParentDir: String,
        val content: ByteArray
    ) : RootShellCommand() {
        init {
            val validWritableDirs = ManagedTargetPath.entries.filter { it.writable }.map { it.parentDir }.toSet()
            require(expectedParentDir in validWritableDirs) { "目标目录不允许写入: '$expectedParentDir'" }
            RootPathValidator.validateStagingPath(stagingPath, expectedParentDir).getOrThrow()
        }

        override fun toShellScript(): String {
            val qStaging = RootShellEscaper.quoteSingle(stagingPath)
            return "umask 077 && cat > $qStaging && sync"
        }

        override fun stdinPayload(): ByteArray = content
    }

    data class ApplyMetadata(
        val path: String,
        val expectedParentDir: String,
        val ownerUid: Int,
        val groupGid: Int,
        val modeOctal: String,
        val selinuxContext: String?
    ) : RootShellCommand() {
        init {
            require(ownerUid >= 0 && groupGid >= 0) { "非法 UID/GID: $ownerUid:$groupGid" }
            require(modeOctal.matches(Regex("^[0-7]{3,4}$"))) { "非法八进制权限模式: '$modeOctal'" }
            if (selinuxContext != null) {
                require(selinuxContext.matches(Regex("^[a-zA-Z0-9_.:-]+$"))) {
                    "非法 SELinux 上下文格式: '$selinuxContext'"
                }
            }
            if (path.startsWith("$expectedParentDir/.")) {
                RootPathValidator.validateStagingPath(path, expectedParentDir).getOrThrow()
            } else {
                RootPathValidator.validatePath(path, requireWritable = true).getOrThrow()
            }
        }

        override fun toShellScript(): String {
            val qPath = RootShellEscaper.quoteSingle(path)
            val sb = StringBuilder()
            sb.append("chown $ownerUid:$groupGid $qPath && ")
            sb.append("chmod $modeOctal $qPath")
            if (!selinuxContext.isNullOrBlank()) {
                val qCtx = RootShellEscaper.quoteSingle(selinuxContext)
                sb.append(" && chcon $qCtx $qPath")
            }
            sb.append(" && sync")
            return sb.toString()
        }
    }

    data class AtomicRename(
        val stagingPath: String,
        val targetPath: String
    ) : RootShellCommand() {
        init {
            val managed = RootPathValidator.validatePath(targetPath, requireWritable = true).getOrThrow()
            RootPathValidator.validateStagingPath(stagingPath, managed.parentDir).getOrThrow()
        }

        override fun toShellScript(): String {
            val qStaging = RootShellEscaper.quoteSingle(stagingPath)
            val qTarget = RootShellEscaper.quoteSingle(targetPath)
            return "if [ -L $qTarget ] || [ -d $qTarget ]; then exit 3; fi; mv -f $qStaging $qTarget && sync"
        }
    }

    data class DeleteFile(
        val path: String,
        val isStaging: Boolean = false,
        val expectedParentDir: String = ManagedTargetPath.DATA_BT_DID_CONF.parentDir
    ) : RootShellCommand() {
        init {
            if (isStaging) {
                RootPathValidator.validateStagingPath(path, expectedParentDir).getOrThrow()
            } else {
                RootPathValidator.validatePath(path, requireWritable = true).getOrThrow()
            }
        }

        override fun toShellScript(): String {
            val qPath = RootShellEscaper.quoteSingle(path)
            return "if [ -d $qPath ] || [ -L $qPath ]; then exit 3; fi; rm -f $qPath && sync"
        }
    }
}

/**
 * Abstraction for controlled Root command execution.
 * Decouples business and transaction logic from real `su` processes so fault injection and unit tests run safely.
 */
interface RootCommandExecutor {
    fun isSuBinaryPresent(): Boolean
    fun execute(command: RootShellCommand, timeoutMs: Long = DEFAULT_TIMEOUT_MS): CommandResult

    companion object {
        const val DEFAULT_TIMEOUT_MS = 8_000L
    }
}

/**
 * Production implementation of [RootCommandExecutor] using `ProcessBuilder("su", "-c", ...)`
 * with strict timeout, concurrent stream draining, and guaranteed process/stream cleanup.
 */
class ProcessRootCommandExecutor(
    private val suBinaryPathCandidates: List<String> = DEFAULT_SU_PATHS,
    private val commandBuilder: (String) -> List<String> = { script -> listOf("su", "-c", script) }
) : RootCommandExecutor {

    override fun isSuBinaryPresent(): Boolean {
        for (path in suBinaryPathCandidates) {
            try {
                if (File(path).exists()) return true
            } catch (_: Throwable) {
            }
        }
        // Fallback: check via `which su` in case su is on PATH or hidden in /debug_ramdisk
        var process: Process? = null
        return try {
            process = ProcessBuilder("sh", "-c", "command -v su || which su").start()
            val finished = process.waitFor(2_000L, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                false
            } else {
                val out = process.inputStream.bufferedReader().use { it.readText().trim() }
                process.exitValue() == 0 && out.isNotEmpty()
            }
        } catch (_: Throwable) {
            false
        } finally {
            safeCleanupProcess(process)
        }
    }

    override fun execute(command: RootShellCommand, timeoutMs: Long): CommandResult {
        val startMs = System.currentTimeMillis()
        var process: Process? = null
        try {
            val script = command.toShellScript()
            process = ProcessBuilder(commandBuilder(script)).start()

            val stdoutBuffer = ByteArrayOutputStream()
            val stderrBuffer = ByteArrayOutputStream()

            val stdoutReader = Thread {
                drainStreamBounded(process.inputStream, stdoutBuffer, MAX_STREAM_CAPTURE_BYTES)
            }.apply {
                isDaemon = true
                start()
            }

            val stderrReader = Thread {
                drainStreamBounded(process.errorStream, stderrBuffer, MAX_STREAM_CAPTURE_BYTES)
            }.apply {
                isDaemon = true
                start()
            }

            // Write stdin payload concurrently on a daemon thread so a hung/unresponsive process
            // or full pipe buffer never blocks waitFor(timeoutMs) on the caller thread.
            val stdinBytes = command.stdinPayload()
            val stdinWriter = Thread {
                try {
                    process.outputStream.use { out ->
                        if (stdinBytes != null && stdinBytes.isNotEmpty()) {
                            out.write(stdinBytes)
                            out.flush()
                        }
                    }
                } catch (_: Throwable) {
                    // Process may have exited early or been destroyed on timeout; stderr/exitCode will capture the reason
                }
            }.apply {
                isDaemon = true
                start()
            }

            val completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!completed) {
                destroyProcessTree(process)
                stdinWriter.interrupt()
                stdoutReader.interrupt()
                stderrReader.interrupt()
                stdinWriter.join(200L)
                stdoutReader.join(200L)
                stderrReader.join(200L)
                val elapsed = System.currentTimeMillis() - startMs
                return CommandResult(
                    exitCode = -1,
                    stdout = stdoutBuffer.toString(Charsets.UTF_8.name()).trim(),
                    stderr = stderrBuffer.toString(Charsets.UTF_8.name()).trim(),
                    timedOut = true,
                    durationMs = elapsed,
                    exceptionMessage = "Root 命令执行超时 (${timeoutMs}ms)"
                )
            }

            stdinWriter.join(500L)
            stdoutReader.join(1_000L)
            stderrReader.join(1_000L)
            val elapsed = System.currentTimeMillis() - startMs
            return CommandResult(
                exitCode = process.exitValue(),
                stdout = stdoutBuffer.toString(Charsets.UTF_8.name()).trim(),
                stderr = stderrBuffer.toString(Charsets.UTF_8.name()).trim(),
                timedOut = false,
                durationMs = elapsed
            )
        } catch (t: Throwable) {
            val elapsed = System.currentTimeMillis() - startMs
            return CommandResult(
                exitCode = -1,
                stdout = "",
                stderr = "",
                timedOut = false,
                durationMs = elapsed,
                exceptionMessage = "${t.javaClass.simpleName}: ${t.message}"
            )
        } finally {
            safeCleanupProcess(process)
        }
    }

    private fun drainStreamBounded(input: InputStream, output: ByteArrayOutputStream, maxBytes: Int) {
        try {
            input.use { stream ->
                val buf = ByteArray(4096)
                var total = 0
                while (true) {
                    val read = stream.read(buf)
                    if (read <= 0) break
                    val remaining = maxBytes - total
                    if (remaining > 0) {
                        val toWrite = minOf(read, remaining)
                        output.write(buf, 0, toWrite)
                        total += toWrite
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun destroyProcessTree(process: Process?) {
        if (process == null) return
        try {
            process.destroyForcibly()
        } catch (_: Throwable) {
            try {
                process.destroy()
            } catch (_: Throwable) {}
        }
    }

    private fun safeCleanupProcess(process: Process?) {
        if (process == null) return
        // Always destroy the OS process FIRST so any thread blocked in pipe read/write
        // wakes up and releases stream monitor locks before close() is called.
        destroyProcessTree(process)
        try {
            process.inputStream.close()
        } catch (_: Throwable) {}
        try {
            process.errorStream.close()
        } catch (_: Throwable) {}
        try {
            process.outputStream.close()
        } catch (_: Throwable) {}
    }

    companion object {
        private const val MAX_STREAM_CAPTURE_BYTES = 16 * 1024 * 1024 // 16 MB max capture
        val DEFAULT_SU_PATHS = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/debug_ramdisk/su",
            "/system/su",
            "/system/bin/.ext/.su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
    }
}
