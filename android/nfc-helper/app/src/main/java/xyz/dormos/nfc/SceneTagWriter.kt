package xyz.dormos.nfc

import android.nfc.Tag
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import java.nio.charset.StandardCharsets

/** Shared implementation; the in-place editor and legacy bridge use identical write/readback checks. */
internal enum class SceneWriteState { VERIFIED, NEED_OVERWRITE, NEED_FORMAT, FORMATTED, FAILED }

internal data class SceneWriteOutcome(
    val state: SceneWriteState,
    val message: String,
    val previousTagId: String? = null,
    val tagUid: String? = null
) {
    val verified: Boolean get() = state == SceneWriteState.VERIFIED
}

internal object SceneWritePolicy {
    // Existing records must not be silently destroyed. The original scene UUID must
    // be returned so its old binding can be removed only after a successful write.
    fun mayCommit(state: SceneWriteState) = state == SceneWriteState.VERIFIED
    fun needsOverwrite(recordsPresent: Boolean, alreadyMatches: Boolean, allowed: Boolean) =
        recordsPresent && !alreadyMatches && !allowed
}

internal object SceneTagWriter {
    private fun fail(message: String) = SceneWriteOutcome(SceneWriteState.FAILED, message)
    fun uid(tag: Tag): String = try {
        (tag.id ?: byteArrayOf()).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    } catch (_: Exception) { "" }

    fun write(
        tag: Tag,
        id: String,
        allowOverwrite: Boolean = false,
        allowFormat: Boolean = false
    ): SceneWriteOutcome {
        if (!NfcProfiles.validId(id)) return fail("场景编号无效，未写入标签")
        val payload = NfcProfiles.payload(id)
        val record = NdefRecord.createMime(
            NfcProfiles.MIME, payload.toByteArray(StandardCharsets.UTF_8)
        )
        val intended = NdefMessage(arrayOf(record))
        val signature = uid(tag)
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.connect()
            try {
                if (!ndef.isWritable) return fail("标签只读，未写入")
                if (ndef.maxSize < intended.toByteArray().size) return fail("标签容量不足，未写入")
                val existing = ndef.ndefMessage
                val oldSceneId = existing?.records?.asSequence()
                    ?.filter { it.tnf == NdefRecord.TNF_MIME_MEDIA }
                    ?.mapNotNull { old ->
                        NfcProfiles.decodeRecord(
                            String(old.type, StandardCharsets.US_ASCII), old.payload
                        )
                    }?.firstOrNull()
                val matched = existing?.toByteArray()?.contentEquals(intended.toByteArray()) == true
                // An unreadable or unknown existing NDEF message must not be treated as
                // an empty tag. TNF_EMPTY is the one benign uninitialized record.
                val mayContainData = existing == null || existing.records.any { record ->
                    record.tnf != NdefRecord.TNF_EMPTY || record.payload.isNotEmpty()
                }
                if (SceneWritePolicy.needsOverwrite(
                        recordsPresent = mayContainData,
                        alreadyMatches = matched,
                        allowed = allowOverwrite
                    )) {
                    if (signature.isBlank()) return fail("标签没有可确认的编号，无法安全覆盖已有数据")
                    return SceneWriteOutcome(
                        SceneWriteState.NEED_OVERWRITE,
                        "检测到已有或无法确认的 NDEF 数据。请确认覆盖，再重新贴近同一标签。",
                        oldSceneId, signature
                    )
                }
                if (!matched) ndef.writeNdefMessage(intended)
                // Never infer write success from the NFC API call alone.
                val actual = ndef.ndefMessage
                if (actual == null || !actual.toByteArray().contentEquals(intended.toByteArray())) {
                    return fail("写卡后回读不一致；没有创建脚本绑定")
                }
                return SceneWriteOutcome(
                    SceneWriteState.VERIFIED,
                    "写入与完整 NDEF 回读一致",
                    oldSceneId, signature
                )
            } finally {
                try { ndef.close() } catch (_: Exception) {}
            }
        }
        val formattable = NdefFormatable.get(tag)
            ?: return fail("标签不支持 NDEF 写入，未修改")
        if (!allowFormat) {
            if (signature.isBlank()) return fail("此标签无法可靠识别，不能安全格式化")
            return SceneWriteOutcome(
                SceneWriteState.NEED_FORMAT,
                "标签尚未格式化。确认后重新贴卡格式化，随后还必须重新读取验证。",
                tagUid = signature
            )
        }
        formattable.connect()
        try {
            formattable.format(intended)
            return SceneWriteOutcome(
                SceneWriteState.FORMATTED,
                "已请求格式化写入；尚未重新读取验证，不能创建脚本绑定",
                tagUid = signature
            )
        } finally {
            try { formattable.close() } catch (_: Exception) {}
        }
    }
}
