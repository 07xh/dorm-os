package xyz.dormos.nfc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.net.wifi.WifiInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.HttpURLConnection
import java.net.URL

/** Uses the actual Wi-Fi Network for every probe; never falls back to mobile data. */
class NetworkDiagnostics(private val context: Context) {
    fun run(): String {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        // Probe only the system's active default network. If a VPN or mobile network
        // is active, do not open a secondary Wi-Fi Network behind the user's routing policy.
        val network = manager.activeNetwork
            ?: return "系统当前没有默认网络。请连接 Ncwu 后再检测。"
        val capabilities = manager.getNetworkCapabilities(network)
            ?: return "默认网络状态已变化，请重新检测。"
        val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (!NcwuRules.mayProbeActiveNetwork(isWifi, hasInternet)) {
            return if (!isWifi) {
                "系统当前默认网络不是 Wi-Fi（可能启用了 VPN 或移动网络）。\\n为避免绕过系统路由，本次停止。"
            } else {
                "当前 Wi-Fi 未声明互联网能力，停止公网检测；不会切换移动数据。"
            }
        }

        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return "发现 Wi-Fi，但尚未授权读取 SSID。\n请在助手内授权，然后确认连接的网络为 Ncwu。"
        }
        val ssid = try {
            // Prefer SSID associated with the exact Network used for the HTTPS probe.
            val networkSsid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (capabilities.transportInfo as? WifiInfo)?.ssid
            } else null
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val legacySsid = wifi.connectionInfo?.ssid
            NcwuRules.selectSsid(networkSsid, legacySsid) ?: ""
        } catch (_: SecurityException) {
            ""
        }
        if (!NcwuRules.isSingleTargetWifi(ssid, 1)) {
            return if (ssid.isBlank() || ssid == WifiManager.UNKNOWN_SSID) {
                "无法识别当前 Wi-Fi 名称。请打开系统定位开关并确认连接 Ncwu。\n为安全起见停止。"
            } else {
                "当前 Wi-Fi 为 " + ssid + "，不是 Ncwu；未发送认证请求。"
            }
        }

        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)) {
            return "Android 已将 Ncwu 标记为需要门户认证。请使用系统 Wi-Fi 登录通知或浏览器手动完成认证；助手未提交账号密码。"
        }

        return try {
            val conn = network.openConnection(URL("https://www.baidu.com/")) as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "GET"
            try {
                val code = conn.responseCode
                if (NcwuRules.isHttpsSuccess(code)) {
                    "Ncwu Wi-Fi HTTPS 测试成功（HTTP " + code + "）。\n不代表 Dr.COM 认证已经确认；未发送账号密码。"
                } else {
                    "Ncwu 公网探测未通过（HTTP " + code + "，重定向也不当作成功）。\n认证状态未知，本次安全停止。"
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            "Ncwu 公网探测未通过（" + e.javaClass.simpleName + "）。\n可能需要网页认证；不会猜测接口或提交密码。"
        }
    }
}
