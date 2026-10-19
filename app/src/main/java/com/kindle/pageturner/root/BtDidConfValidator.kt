package com.kindle.pageturner.root

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * Dedicated auditor, format validator, and safe patch builder for Android Fluoride `bt_did.conf`.
 *
 * Enforces Phase 7 (bt_did.conf Special Audit & Protection Rules):
 * - Rejects unknown/non-INI or binary file formats before modification.
 * - Preserves comments and valid unrelated sections while setting `[DID1]` to Kindle Remote DID.
 * - Provides line-by-line unified diff inspection.
 */
object BtDidConfValidator {

    const val KINDLE_VENDOR_ID_SOURCE_HEX = "0x0002"
    const val KINDLE_VENDOR_ID_HEX = "0x0171"
    const val KINDLE_PRODUCT_ID_HEX = "0x0443"
    const val KINDLE_VERSION_HEX = "0x0100"
    const val MAX_CONF_BYTES = 64 * 1024

    private val SECTION_REGEX = Regex("^\\[[A-Za-z0-9_.-]+]$")
    private val KEY_VALUE_REGEX = Regex("^([A-Za-z0-9_.-]+)\\s*=\\s*([^#;\\r\\n]*?)(?:\\s*[#;].*)?$")

    private val KNOWN_DID_KEYS = setOf(
        "recordNumber",
        "primaryRecord",
        "vendorId",
        "vendorIdSource",
        "productId",
        "version",
        "clientExecutableURL",
        "serviceDescription",
        "documentationURL"
    )

    sealed class FormatValidationResult {
        data object Valid : FormatValidationResult()
        data class Invalid(val lineNumber: Int, val reason: String) : FormatValidationResult()
    }

    /**
     * Validates that raw bytes represent a valid text-based `bt_did.conf` INI file.
     * Rejects >64KB files, binary data, null bytes, invalid UTF-8 sequences, or non-DID sections.
     */
    fun validateExistingBytes(bytes: ByteArray): FormatValidationResult {
        if (bytes.size > MAX_CONF_BYTES) {
            return FormatValidationResult.Invalid(
                lineNumber = 1,
                reason = "文件大小 (${bytes.size} bytes) 超过安全上限 ($MAX_CONF_BYTES bytes)，拒绝覆盖异常大文件"
            )
        }
        for (i in bytes.indices) {
            val b = bytes[i].toInt() and 0xFF
            if (b == 0x00) {
                return FormatValidationResult.Invalid(
                    lineNumber = 1,
                    reason = "检测到二进制 NUL 字节 (偏移 $i)，拒绝覆盖未知二进制格式文件"
                )
            }
        }
        val text = try {
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (t: Throwable) {
            return FormatValidationResult.Invalid(1, "检测到无效 UTF-8 字节序列，拒绝覆盖非文本或损坏文件: ${t.message}")
        }
        return validateExistingText(text)
    }

    fun validateExistingText(content: String): FormatValidationResult {
        if (content.toByteArray(Charsets.UTF_8).size > MAX_CONF_BYTES) {
            return FormatValidationResult.Invalid(
                lineNumber = 1,
                reason = "配置文本大小超过安全上限 ($MAX_CONF_BYTES bytes)"
            )
        }
        if (content.isBlank()) {
            return FormatValidationResult.Valid
        }
        val lines = content.lines()
        var currentSection: String? = null

        for ((idx, rawLine) in lines.withIndex()) {
            val lineNum = idx + 1
            for (ch in rawLine) {
                if ((ch.code < 0x20 && ch != '\t' && ch != '\r') || ch == '\uFFFD') {
                    return FormatValidationResult.Invalid(
                        lineNumber = lineNum,
                        reason = "第 $lineNum 行包含非法控制字符或损坏字符 (0x${String.format("%04X", ch.code)})"
                    )
                }
            }
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) {
                continue
            }
            if (trimmed.startsWith("[")) {
                if (!SECTION_REGEX.matches(trimmed)) {
                    return FormatValidationResult.Invalid(
                        lineNumber = lineNum,
                        reason = "第 $lineNum 行节标题格式无效: '$trimmed'"
                    )
                }
                val sectionName = trimmed.substring(1, trimmed.length - 1)
                if (!sectionName.startsWith("DID", ignoreCase = true) &&
                    !sectionName.equals("General", ignoreCase = true)
                ) {
                    return FormatValidationResult.Invalid(
                        lineNumber = lineNum,
                        reason = "第 $lineNum 行包含非预期的配置节 [$sectionName]，拒绝覆盖未知格式配置文件"
                    )
                }
                currentSection = sectionName
                continue
            }

            val kvMatch = KEY_VALUE_REGEX.matchEntire(trimmed)
                ?: return FormatValidationResult.Invalid(
                    lineNumber = lineNum,
                    reason = "第 $lineNum 行不符合 INI 'key = value' 语法: '$trimmed'"
                )

            if (currentSection == null) {
                return FormatValidationResult.Invalid(
                    lineNumber = lineNum,
                    reason = "第 $lineNum 行键值对位于任何 [DIDx] 节之外: '$trimmed'"
                )
            }

            val key = kvMatch.groupValues[1]
            if (currentSection.startsWith("DID", ignoreCase = true) && key !in KNOWN_DID_KEYS) {
                return FormatValidationResult.Invalid(
                    lineNumber = lineNum,
                    reason = "第 $lineNum 行 [$currentSection] 节包含未知键名 '$key'，拒绝覆盖未知格式"
                )
            }
        }
        return FormatValidationResult.Valid
    }

    /**
     * Checks if the provided `bt_did.conf` text already configures Amazon Kindle Remote (0x0171:0x0443)
     * as a primary DID record.
     */
    fun containsKindleRemoteDid(content: String?): Boolean {
        if (content.isNullOrBlank()) return false
        if (validateExistingText(content) !is FormatValidationResult.Valid) return false

        val sections = parseSections(content)
        for ((sectionName, kvMap) in sections) {
            if (!sectionName.startsWith("DID", ignoreCase = true)) continue
            val vid = parseHexOrDec(kvMap["vendorId"])
            val pid = parseHexOrDec(kvMap["productId"])
            val vidSource = parseHexOrDec(kvMap["vendorIdSource"])
            val primary = kvMap["primaryRecord"]?.trim()?.equals("true", ignoreCase = true) ?: false
            if (vid == 0x0171 && pid == 0x0443 && (vidSource == null || vidSource == 0x0002) && primary) {
                return true
            }
        }
        return false
    }

    /**
     * Builds the target `bt_did.conf` content safely.
     * Fails if the existing content is not in a recognized valid `bt_did.conf` format.
     */
    fun buildPatchedDidContent(existingContent: String?): Result<String> {
        if (!existingContent.isNullOrEmpty()) {
            when (val validation = validateExistingText(existingContent)) {
                is FormatValidationResult.Invalid -> {
                    return Result.failure(
                        IllegalStateException("现有文件格式校验失败 (行 ${validation.lineNumber}): ${validation.reason}")
                    )
                }
                FormatValidationResult.Valid -> {}
            }
        }

        val managedHeader1 = "# Managed by Kindle Page Turner Root Transaction Manager"
        val managedHeader2 = "# Target Identity: Amazon Kindle Remote (Lab126 VID=0x0171, PID=0x0443)"
        val preservedComments = if (!existingContent.isNullOrBlank()) {
            existingContent.lines()
                .map { it.trim() }
                .filter { (it.startsWith("#") || it.startsWith(";")) && it != managedHeader1 && it != managedHeader2 }
                .distinct()
        } else {
            emptyList()
        }

        val sb = StringBuilder()
        sb.append(managedHeader1).append("\n")
        sb.append(managedHeader2).append("\n")
        for (comment in preservedComments) {
            sb.append(comment).append("\n")
        }
        sb.append("[DID1]\n")
        sb.append("primaryRecord = true\n")
        sb.append("vendorIdSource = ").append(KINDLE_VENDOR_ID_SOURCE_HEX).append("\n")
        sb.append("vendorId = ").append(KINDLE_VENDOR_ID_HEX).append("\n")
        sb.append("productId = ").append(KINDLE_PRODUCT_ID_HEX).append("\n")
        sb.append("version = ").append(KINDLE_VERSION_HEX).append("\n")

        if (!existingContent.isNullOrBlank()) {
            // Preserve any non-DID1 sections (such as [DID2]) with primaryRecord demoted to false
            val sections = parseSections(existingContent)
            val extraSections = sections.filterKeys { !it.equals("DID1", ignoreCase = true) }
            for ((secName, kvMap) in extraSections) {
                sb.append("\n[").append(secName).append("]\n")
                for ((k, v) in kvMap) {
                    val safeVal = if (k == "primaryRecord") "false" else v
                    sb.append(k).append(" = ").append(safeVal).append("\n")
                }
            }
        }

        val finalContent = sb.toString()
        return when (val finalCheck = validateExistingText(finalContent)) {
            is FormatValidationResult.Valid -> Result.success(finalContent)
            is FormatValidationResult.Invalid -> Result.failure(
                IllegalStateException("生成的配置未通过自检: ${finalCheck.reason}")
            )
        }
    }

    /**
     * Generates a human-readable unified diff between the current state and proposed state.
     */
    fun generateUnifiedDiff(
        originalExists: Boolean,
        originalContent: String?,
        newContent: String,
        targetPath: String = ManagedTargetPath.DATA_BT_DID_CONF.canonicalPath
    ): String {
        val sb = StringBuilder()
        sb.append("--- ").append(if (originalExists) "a$targetPath (当前原始状态)" else "/dev/null (原始文件不存在)").append("\n")
        sb.append("+++ b").append(targetPath).append(" (目标修改状态)\n")

        val oldLines = if (originalExists && !originalContent.isNullOrEmpty()) {
            originalContent.trimEnd('\n', '\r').lines()
        } else {
            emptyList()
        }
        val newLines = newContent.trimEnd('\n', '\r').lines()

        sb.append("@@ -").append(if (oldLines.isEmpty()) "0,0" else "1,${oldLines.size}")
            .append(" +1,").append(newLines.size).append(" @@\n")

        for (line in oldLines) {
            sb.append("- ").append(line).append("\n")
        }
        for (line in newLines) {
            sb.append("+ ").append(line).append("\n")
        }
        return sb.toString()
    }

    private fun parseSections(content: String): LinkedHashMap<String, LinkedHashMap<String, String>> {
        val map = LinkedHashMap<String, LinkedHashMap<String, String>>()
        var currentSection: String? = null
        for (rawLine in content.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) continue
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                currentSection = trimmed.substring(1, trimmed.length - 1)
                map.putIfAbsent(currentSection, LinkedHashMap())
            } else if (currentSection != null) {
                val match = KEY_VALUE_REGEX.matchEntire(trimmed)
                if (match != null) {
                    map[currentSection]?.put(match.groupValues[1], match.groupValues[2].trim())
                }
            }
        }
        return map
    }

    private fun parseHexOrDec(raw: String?): Int? {
        val trimmed = raw?.trim() ?: return null
        return if (trimmed.startsWith("0x", ignoreCase = true)) {
            trimmed.substring(2).toIntOrNull(16)
        } else {
            trimmed.toIntOrNull(10)
        }
    }
}
