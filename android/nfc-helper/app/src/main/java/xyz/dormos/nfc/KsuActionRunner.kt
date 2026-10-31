package xyz.dormos.nfc

import android.content.Context
import java.util.concurrent.TimeUnit

/**
 * Explicitly opted-in, fixed-command bridge.
 * Never consume NFC tag bytes, web URLs or editable text as shell code.
 */
object KsuRules {
    const val ACTION = "wifi-status"
    const val MODULE = "/data/adb/modules/dormos_nfc_actions/run.sh"
    // Root command is an immutable literal; tag contents cannot change it.
    const val COMMAND = "/system/bin/sh /data/adb/modules/dormos_nfc_actions/run.sh wifi-status"
    fun approved(action: String, target: String) = action == ACTION && target.isEmpty()
    fun succeeded(code: Int, output: String): Boolean =
        code == 0 && output.lines().any { it == "DORM_KSU=ok" } &&
            output.lines().any { it == "ACTION=wifi-status" }
}

class KsuActionRunner(private val context: Context) {
    private val prefs = context.getSharedPreferences("root_actions", Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean("enabled", false)
    fun setEnabled(enabled: Boolean): Boolean = prefs.edit().putBoolean("enabled", enabled).commit()

    fun run(): String {
        if (!isEnabled()) return "尚未启用 KernelSU 脚本执行。请在助手设置中明确授权。"
        val process = try {
            ProcessBuilder("su", "-c", KsuRules.COMMAND)
                .redirectErrorStream(false)
                .start()
        } catch (_: Exception) {
            return "无法启动 su：请确认安装 KernelSU，并单独为 DORM OS 助手授予 Root 权限。"
        }
        return try {
            if (!process.waitFor(12, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return "脚本超时已停止（12 秒）；未进行网页登录。"
            }
            // Bounded and strictly recognized output: no IP, MAC, tokens or arbitrary shell stdout.
            val output = process.inputStream.bufferedReader().use { it.readText().take(2048) }
            if (!KsuRules.succeeded(process.exitValue(), output)) {
                "KernelSU 脚本未通过验证（退出码 " + process.exitValue() + "）。检查模块安装与 Root 授权。"
            } else {
                val wlan = when {
                    output.lines().contains("WLAN_INTERFACE=present") -> "存在"
                    output.lines().contains("WLAN_INTERFACE=not_up") -> "未启动"
                    else -> "未知"
                }
                "KernelSU 脚本执行成功。\nWi-Fi 网卡：$wlan\n未提交校园网认证。"
            }
        } catch (_: Exception) {
            process.destroyForcibly()
            "执行失败，已请求结束脚本。"
        } finally {
            process.destroy()
        }
    }
}
