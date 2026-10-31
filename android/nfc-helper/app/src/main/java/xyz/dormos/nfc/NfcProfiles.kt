package xyz.dormos.nfc

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

/**
 * Profiles live on the phone; the NFC sticker contains only an opaque UUID.
 * The previous v1:ncwu payload remains supported for existing tags.
 */
data class NfcProfile(
    val id: String,
    val name: String,
    val action: String,
    val target: String = ""
)

object NfcProfiles {
    const val MIME = "application/vnd.dormos.ncwu"
    const val LEGACY_NCWU = "v1:ncwu"
    const val DIAGNOSE_NCWU = "ncwu"
    const val OPEN_HTTPS = "https"
    const val DORM_SCENE = "scene"
    const val KSU_WIFI_STATUS = "ksu-wifi-status"
    const val SITE = "https://dorm-os-dk9.pages.dev/"
    private val uuidPattern = Regex("^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$")
    val allowedScenes = setOf("study", "rest", "dev", "away")

    fun makeId(): String = UUID.randomUUID().toString()

    fun payload(id: String): String {
        require(validId(id))
        return "v2:tag:$id"
    }

    fun decodeId(payload: String?): String? {
        if (payload == null || !payload.startsWith("v2:tag:")) return null
        val id = payload.removePrefix("v2:tag:")
        return id.takeIf(::validId)
    }

    fun validId(id: String): Boolean = uuidPattern.matches(id)

    private val blockedParameters = setOf(
        "password", "passwd", "pwd", "token", "access_token", "refresh_token",
        "id_token", "api_key", "apikey", "client_secret", "secret",
        "session", "sessionid", "sid", "auth", "authorization", "cookie",
        "jwt", "signature", "code", "credential"
    )

    private fun hasCredentialParameters(value: String?): Boolean {
        if (value.isNullOrEmpty()) return false
        val parameters = value.substringAfter('?', value)
        return parameters.split('&').any { part ->
            val rawKey = part.substringBefore('=')
            if (rawKey.isBlank()) return@any false
            val key = try {
                URLDecoder.decode(rawKey, StandardCharsets.UTF_8)
                    .lowercase(Locale.ROOT).replace('-', '_').replace('.', '_')
            } catch (_: IllegalArgumentException) {
                return@any true
            }
            key in blockedParameters || key.endsWith("_token") ||
                key.endsWith("_password") || key.endsWith("_secret")
        }
    }

    fun isSafeHttpsUrl(raw: String): Boolean {
        if (raw.length !in 9..500 || raw.any { it.code < 32 }) return false
        val uri = try { URI(raw) } catch (_: Exception) { return false }
        if (uri.scheme != "https" || uri.userInfo != null || uri.host.isNullOrBlank()) return false
        if (uri.host.equals("localhost", true) || uri.port !in -1..65535 || uri.port == 0) return false
        if (uri.host?.contains('.') != true || (uri.rawFragment?.length ?: 0) > 120) return false
        if (hasCredentialParameters(uri.rawQuery) || hasCredentialParameters(uri.rawFragment)) return false
        return true
    }

    fun sceneUrl(scene: String): String? =
        scene.takeIf { it in allowedScenes }?.let { "$SITE?dormScene=$it#scenes" }

    fun validProfile(p: NfcProfile): Boolean =
        validId(p.id) &&
            p.name.isNotBlank() && p.name.length <= 40 &&
            when (p.action) {
                DIAGNOSE_NCWU -> p.target.isEmpty()
                OPEN_HTTPS -> isSafeHttpsUrl(p.target)
                DORM_SCENE -> p.target in allowedScenes
                KSU_WIFI_STATUS -> p.target.isEmpty()
                else -> false
            }
}
