package xyz.dormos.nfc

import java.util.UUID

/** Shared NDEF contract. The tag contains only an opaque script-binding UUID, never commands. */
object NfcProfiles {
    const val MIME = "application/vnd.dormos.nfc-script"
    const val LEGACY_MIME = "application/vnd.dormos.ncwu"
    fun isSupportedMime(value: String) = value == MIME || value == LEGACY_MIME

    fun bindingMayCommit(expectedTag: String?, scriptId: String?, returnedTag: String?, verified: Boolean) =
        verified && validId(expectedTag) && validId(scriptId) &&
            expectedTag == returnedTag

    fun previousBindingToRemove(previousTagId: String?, newTagId: String?): String? =
        previousTagId.takeIf { validId(it) && validId(newTagId) && it != newTagId }
    private val uuidPattern = Regex("^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$")

    fun makeId(): String = UUID.randomUUID().toString()

    fun payload(id: String): String {
        require(validId(id))
        return "v2:tag:$id"
    }

    fun decodeRecord(type: String, payload: ByteArray?): String? {
        if (!isSupportedMime(type) || payload == null || payload.size !in 1..80) return null
        return decodeId(String(payload, Charsets.UTF_8))
    }

    fun decodeId(payload: String?): String? {
        if (payload == null) return null
        val id = when {
            payload.startsWith("v2:tag:") -> payload.removePrefix("v2:tag:")
            payload.startsWith("v3:scene:") -> payload.removePrefix("v3:scene:")
            else -> return null
        }
        return id.takeIf(::validId)
    }

    fun validId(id: String?): Boolean =
        id != null && uuidPattern.matches(id)
}
