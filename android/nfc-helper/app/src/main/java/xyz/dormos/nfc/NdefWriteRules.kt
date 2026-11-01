package xyz.dormos.nfc

import java.net.URI

/**
 * Pure validation and overwrite policy, independent of Android and KernelSU.
 * Each write replaces the entire NDEF message, so existing content requires consent.
 */
internal enum class NdefWriteKind { TEXT, WEB_URL }

internal data class PreparedNdefWrite(
    val kind: NdefWriteKind,
    val content: String,
    val utf8Bytes: Int
)

internal data class NdefPreparation(
    val draft: PreparedNdefWrite? = null,
    val error: String? = null
)

internal object NdefWriteRules {
    const val MAX_CONTENT_BYTES = 2048

    fun prepare(kind: NdefWriteKind, raw: String): NdefPreparation {
        val input = raw.trim()
        if (input.isBlank()) return NdefPreparation(error = "请先输入要写入的内容")
        if (input.any { it == '\u0000' }) {
            return NdefPreparation(error = "不支持 NUL 控制字符")
        }

        val content = when (kind) {
            NdefWriteKind.TEXT -> input
            NdefWriteKind.WEB_URL -> {
                if (input.any { it.isWhitespace() }) {
                    return NdefPreparation(error = "网址不能包含空格")
                }
                val address = if (input.contains("://")) input else "https://$input"
                val uri = try { URI(address) } catch (_: Exception) { null }
                if (uri == null ||
                    uri.scheme?.lowercase() !in setOf("http", "https") ||
                    uri.host.isNullOrBlank() ||
                    uri.rawUserInfo != null ||
                    uri.port > 65535
                ) {
                    return NdefPreparation(error = "请输入有效的 HTTP 或 HTTPS 网址，且不能包含账号信息")
                }
                address
            }
        }
        val size = content.toByteArray(Charsets.UTF_8).size
        if (size > MAX_CONTENT_BYTES) {
            return NdefPreparation(error = "内容不能超过 $MAX_CONTENT_BYTES 字节")
        }
        return NdefPreparation(PreparedNdefWrite(kind, content, size))
    }

    /**
     * Never replace DORM automation bindings through the general-purpose writer.
     * The dedicated binding flow must verify a new UUID before changing bindings.
     */
    fun replacementError(
        existingRecordCount: Int,
        existingMimeTypes: List<String>,
        overwriteConfirmed: Boolean
    ): String? {
        if (existingMimeTypes.any { it.startsWith("application/vnd.dormos.", ignoreCase = true) }) {
            return "这是 DORM 自动化标签，请使用原有的脚本绑定功能，不允许普通写卡覆盖"
        }
        if (existingRecordCount > 0 && !overwriteConfirmed) {
            return "标签已有内容，未同意覆盖；本次没有写入"
        }
        return null
    }
}

/** Never offer immediate rewriting after an unknown physical write outcome. */
internal object NdefWriteRecoveryPolicy {
    fun needsReadOnlyReview(phase: NdefWritePhase, outcome: NdefWriteOutcome?): Boolean =
        phase == NdefWritePhase.INTERRUPTED || outcome?.requiresReadback == true
}
