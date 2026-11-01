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
    fun uid(tag: Tag): String = tag.id.joinToString("") { "%02x".format(it.toInt() and 0xff) }

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
                if (SceneWritePolicy.needsOverwrite(
                        recordsPresent = existing?.records?.isNotEmpty() == true,
                        alreadyMatches = matched,
                        allowed = allowOverwrite
                    )) {
                    return SceneWriteOutcome(
                        SceneWriteState.NEED_OVERWRITE,
                        "检测到标签已有 NDEF 数据。确认覆盖后，再将同一标签贴近。",
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
