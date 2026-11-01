package com.dormos.nfc

import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/** Never interpolate user script paths into a root command. Fixed dispatcher + allowlisted ID only. */
object ModuleGateway {
    private const val DISPATCH = "/data/adb/modules/dormos_nfc/bin/dispatch.sh"
    data class Result(val ok: Boolean, val message: String)

    private const val BRIDGE = "/data/adb/modules/dormos_nfc_actions/dispatcher.sh"
    data class ModuleStatus(val rootGranted: Boolean, val legacyReady: Boolean, val bridgePresent: Boolean, val message: String)
    data class ScriptEntry(val title: String, val module: String, val installed: Boolean)

    /** Two previously supplied KernelSU modules use incompatible scene protocols. */
    fun inspect(): ModuleStatus {
        val root = check()
        if (!root.ok) return ModuleStatus(false, false, false, root.message)
        val old = run("test -f " + DISPATCH, 4).ok
        val bridge = run("test -f " + BRIDGE, 4).ok
        val label = when {
            old -> "旧版场景调度器已就绪"
            bridge -> "检测到 Bridge 模块：需要 UUID 场景配置，不可直接使用旧版动作"
            else -> "未发现可用 NFC 模块，请检查 KernelSU 安装情况"
        }
        return ModuleStatus(true, old, bridge, label)
    }

    /** Read-only inventory of known scripts; no arbitrary shell paths or user input. */
    fun knownScripts(): List<ScriptEntry> {
        val known = listOf(
            Triple("场景调度器", "DORM OS NFC v0.1", DISPATCH),
            Triple("新版场景桥接", "DORM OS Bridge v0.9", BRIDGE),
            Triple("Ncwu 网络检查", "Bridge v0.9", "/data/adb/modules/dormos_nfc_actions/scripts/ncwu-check.sh"),
            Triple("Wi-Fi 状态检查", "Bridge v0.9", "/data/adb/modules/dormos_nfc_actions/scripts/wifi-status.sh")
        )
        if (!check().ok) return known.map { ScriptEntry(it.first, it.second, false) }
        return known.map { ScriptEntry(it.first, it.second, run("test -f " + it.third, 4).ok) }
    }

    fun check(): Result {
        val result = run("id -u", 5)
        return if (result.ok && result.message.trim() == "0") Result(true, "Root 已授权")
               else Result(false, "未取得 uid=0；请在 KernelSU 中检查授权")
    }
    fun execute(action: String): Result {
        if (action !in AllowedActions.items) return Result(false, "未知动作，已拦截")
        val status = inspect()
        if (!status.legacyReady) return Result(false, status.message)
        val result = run("/system/bin/sh $DISPATCH $action", 15)
        // Avoid persisting or displaying diagnostic output that may contain private credentials.
        return when {
            result.ok -> Result(true, "模块动作已执行")
            result.message.startsWith("执行超时") -> result
            else -> Result(false, "执行失败：请检查模块配置与 KernelSU 授权")
        }
    }
    private fun run(command: String, timeout: Long): Result {
        var process: Process? = null
        return try {
            val active: Process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            process = active
            val out = ByteArrayOutputStream()
            // Drain asynchronously so a filled pipe cannot block waitFor().
            val reader = Thread {
                try { active.inputStream.use { input ->
                    val buf = ByteArray(1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        if (out.size() < 8192) synchronized(out) { out.write(buf, 0, minOf(n, 8192 - out.size())) }
                    }
                } } catch (_: Exception) {}
            }
            reader.isDaemon = true
            reader.start()
            if (!active.waitFor(timeout, TimeUnit.SECONDS)) {
                active.destroyForcibly(); reader.join(500)
                Result(false, "执行超时（${timeout}s），已终止进程")
            } else {
                reader.join(500)
                val message = synchronized(out) { out.toString("UTF-8").trim().take(1000) }
                val success = active.exitValue() == 0
                Result(success, if (message.isBlank()) if (success) "执行完成" else "执行失败：退出码 ${active.exitValue()}" else message)
            }
        } catch (ex: Exception) {
            Result(false, "KernelSU/Root 不可用或模块未安装：${ex.javaClass.simpleName}")
        } finally { process?.destroy() }
    }
}
