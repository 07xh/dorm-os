package xyz.dormos.nfc

import java.net.URI

/** Incoming web links only navigate; they never grant permission to run Root scripts. */
object BridgeLinkRules {
    private val scenes = setOf("none", "study", "rest", "dev", "away")
    private val hosts = setOf("dorm-os-dk9.pages.dev", "dorm.0701000.xyz")

    fun sceneFromLink(raw: String?): String? {
        if (raw.isNullOrBlank() || raw.length > 512) return null
        val uri = try { URI(raw) } catch (_: Exception) { return null }
        if (uri.rawFragment != null || uri.rawUserInfo != null || uri.port != -1) return null
        val validWeb = uri.scheme == "https" && uri.host in hosts && uri.rawPath == "/assistant/"
        val validLocal = uri.scheme == "dormos" && uri.host == "assistant" && uri.rawPath == "/open"
        if (!validWeb && !validLocal) return null
        val parts = uri.rawQuery?.split('&') ?: return null
        if (parts.size != 1 || !parts[0].startsWith("scene=")) return null
        val scene = parts[0].removePrefix("scene=")
        return scene.takeIf { it in scenes }
    }

    fun label(scene: String): String = when (scene) {
        "study" -> "学习"
        "rest" -> "休息"
        "dev" -> "开发"
        "away" -> "出门"
        else -> "管理"
    }
}
