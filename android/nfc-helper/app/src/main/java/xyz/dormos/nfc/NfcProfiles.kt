package xyz.dormos.nfc

import java.util.UUID

/** Shared NDEF contract. The tag contains only an opaque script-binding UUID, never commands. */
object NfcProfiles {
    const val MIME = "application/vnd.dormos.ncwu"
    private val uuidPattern = Regex("^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$")

    fun makeId(): String = UUID.randomUUID().toString()

    fun payload(id: String): String {
        require(validId(id))
        return "v2:tag:$id"
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
