package xyz.dormos.nfc

/** Pure, fail-closed rules tested without device hardware or campus credentials. */
object NcwuRules {
    const val MIME = "application/vnd.dormos.ncwu"
    const val COMMAND = "v1:ncwu"

    fun isExpectedTag(mime: String?, payload: String?): Boolean =
        mime == MIME && payload == COMMAND

    fun normalizeSsid(ssid: String?): String? {
        val value = ssid?.trim()?.removeSurrounding("\"") ?: return null
        if (value.isBlank() || value.equals("<unknown ssid>", ignoreCase = true)) return null
        return value
    }

    /** Prefer SSID from the selected Network; global Wi-Fi info is only a fallback. */
    fun selectSsid(networkSsid: String?, legacySsid: String?): String? =
        normalizeSsid(networkSsid) ?: normalizeSsid(legacySsid)

    fun isSingleTargetWifi(ssid: String?, wifiNetworkCount: Int): Boolean =
        wifiNetworkCount == 1 && normalizeSsid(ssid) == "Ncwu"

    /** Removes non-target Wi-Fi network names before a user explicitly copies a report. */
    fun redactDiagnostic(text: String): String =
        text.replace(Regex("当前 Wi-Fi 为 [^\\n]*?， ?不是 Ncwu"), "当前 Wi-Fi 不是 Ncwu").take(800)

    enum class ProbeDecision { STOP_NOT_WIFI, STOP_NO_INTERNET, MANUAL_PORTAL, PROBE_HTTPS }

    /** Central fail-closed decision for the active default route and Android captive-portal signal. */
    fun probeDecision(
        isWifiTransport: Boolean,
        hasInternetCapability: Boolean,
        hasCaptivePortalCapability: Boolean
    ): ProbeDecision = when {
        !isWifiTransport -> ProbeDecision.STOP_NOT_WIFI
        !hasInternetCapability -> ProbeDecision.STOP_NO_INTERNET
        hasCaptivePortalCapability -> ProbeDecision.MANUAL_PORTAL
        else -> ProbeDecision.PROBE_HTTPS
    }

    /** HTTPS 30x is not treated as internet success: captive portals can redirect. */
    fun isHttpsSuccess(status: Int): Boolean = status in 200..299
}
