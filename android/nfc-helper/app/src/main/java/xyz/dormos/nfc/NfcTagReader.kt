package xyz.dormos.nfc

import android.nfc.Tag
import android.nfc.NdefRecord
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import java.nio.charset.StandardCharsets

/** One read-only NFC parser shared by legacy readers and the inline workbench sheet. */
internal object NfcTagReader {
    fun inspect(tag: Tag): ReadReport {
        val technologies = tag.techList.map { it.substringAfterLast('.') }
            .take(12).joinToString(" / ")
        val ndef = Ndef.get(tag) ?: return ReadReport(
            technologies, "无 NDEF", null, null, emptyList(),
            NfcFailureFeedback.noNdef(NdefFormatable.get(tag) != null)
        )
        ndef.connect()
        return try {
            val message = ndef.ndefMessage
            val usedBytes = message?.toByteArray()?.size
            val allRecords = message?.records ?: emptyArray()
            val items = allRecords.take(24).mapIndexed { index, record ->
                val uri = try { record.toUri()?.toString() } catch (_: Exception) { null }
                val isText = record.tnf == NdefRecord.TNF_WELL_KNOWN &&
                    record.type.contentEquals(NdefRecord.RTD_TEXT)
                val preview = when {
                    isText -> NdefTextPayload.decode(record.payload)
                    uri != null -> uri
                    else -> null
                }?.take(400)
                val type = when {
                    isText -> "文本"
                    uri != null -> "链接 / URI"
                    record.tnf == NdefRecord.TNF_MIME_MEDIA -> {
                        val mime = String(record.type, StandardCharsets.US_ASCII)
                        if (NfcProfiles.isSupportedMime(mime)) "DORM 自动化标签"
                        else mime.take(90)
                    }
                    else -> "记录类型 " + record.tnf.toString()
                }
                ReadRecord(index + 1, type, preview, record.payload.size)
            }
            ReadReport(
                technologies, ndef.type ?: "NDEF", ndef.isWritable,
                ndef.maxSize, items, null, allRecords.size, usedBytes
            )
        } finally {
            try { ndef.close() } catch (_: Exception) {}
        }
    }
}
