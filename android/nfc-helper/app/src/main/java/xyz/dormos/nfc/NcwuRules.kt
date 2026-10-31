package xyz.dormos.nfc

/** Pure, fail-closed rules tested without device hardware or campus credentials. */
object NcwuRules {
    const val MIME = "application/vnd.dormos.ncwu"
    const val COMMAND = "v1:ncwu"

    fun isExpectedTag(mime: String?, payload: String?): Boolean =
        mime == MIME && payload == COMMAND

    fun isSingleTargetWifi(ssid: String?, wifiNetworkCount: Int): Boolean =
        wifiNetworkCount == 1 && ssid == "Ncwu"

    /** HTTPS 30x is not treated as internet success: captive portals can redirect. */
    fun isHttpsSuccess(status: Int): Boolean = status in 200..299
}
