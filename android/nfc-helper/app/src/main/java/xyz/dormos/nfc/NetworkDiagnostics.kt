package xyz.dormos.nfc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.HttpURLConnection
import java.net.URL

/** Uses the actual Wi-Fi Network for every probe; never falls back to mobile data. */
class NetworkDiagnostics(private val context: Context) {
    fun run(): String {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.allNetworks.firstOrNull { candidate ->
            manager.getNetworkCapabilities(candidate)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: return "未找到已连接的 Wi-Fi。\n请在系统设置连接 Ncwu 后再碰卡。"

        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return "发现 Wi-Fi，但尚未授权读取 SSID。\n请在助手内授权，然后确认连接的网络为 Ncwu。"
        }
        val ssid = try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifi.connectionInfo?.ssid?.trim('"') ?: ""
        } catch (e: SecurityException) {
            ""
        }
        if (ssid != "Ncwu") {
            return if (ssid.isBlank() || ssid == WifiManager.UNKNOWN_SSID) {
                "无法识别当前 Wi-Fi 名称。请打开系统定位开关并确认连接 Ncwu。\n为安全起见停止。"
            } else {
                "当前 Wi-Fi 为 " + ssid + "，不是 Ncwu；未发送认证请求。"
            }
        }

        return try {
            val conn = network.openConnection(URL("https://www.baidu.com/")) as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "GET"
            try {
                val code = conn.responseCode
                if (code in 200..399) {
                    "Ncwu Wi-Fi 可访问测试网站（HTTP " + code + "）。\n尚未验证 Dr.COM 状态；未发送账号密码。"
                } else {
                    "Ncwu 公网探测异常（HTTP " + code + "）。\n认证请求尚未核实，本次安全停止。"
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            "Ncwu 公网探测未通过（" + e.javaClass.simpleName + "）。\n可能需要网页认证；不会猜测接口或提交密码。"
        }
    }
}
