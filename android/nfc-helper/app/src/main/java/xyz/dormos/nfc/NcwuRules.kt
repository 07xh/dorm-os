package xyz.dormos.nfc

/** Pure, fail-closed rules tested without device hardware or campus credentials. */
object NcwuRules {
    const val MIME = "application/vnd.dormos.ncwu"
    const val COMMAND = "v1:ncwu"

    fun isExpectedTag(mime: String?, payload: String?): Boolean =
        mime == MIME && payload == COMMAND

    fun isSingleTargetWifi(ssid: String?, wifiNetworkCount: Int): Boolean =
        wifiNetworkCount == 1 && ssid == "Ncwu"

    /** Removes non-target Wi-Fi network names before a user explicitly copies a report. */
    fun redactDiagnostic(text: String): String =
        text.replace(Regex("当前 Wi-Fi 为 [^\\n]*?， ?不是 Ncwu"), "当前 Wi-Fi 不是 Ncwu").take(800)

    /** HTTPS 30x is not treated as internet success: captive portals can redirect. */
    fun isHttpsSuccess(status: Int): Boolean = status in 200..299
}
