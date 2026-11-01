package com.dormos.nfc

import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/** Never interpolate user script paths into a root command. Fixed dispatcher + allowlisted ID only. */
object ModuleGateway {
    private const val DISPATCH = "/data/adb/modules/dormos_nfc/bin/dispatch.sh"
    data class Result(val ok: Boolean, val message: String)

    fun check(): Result {
        val result = run("id -u", 5)
        return if (result.ok && result.message.trim() == "0") Result(true, "Root 已授权")
               else Result(false, "未取得 uid=0；请在 KernelSU 中检查授权")
    }
    fun execute(action: String): Result {
        if (action !in AllowedActions.items) return Result(false, "未知动作，已拦截")
        return run("/system/bin/sh $DISPATCH $action", 15)
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
