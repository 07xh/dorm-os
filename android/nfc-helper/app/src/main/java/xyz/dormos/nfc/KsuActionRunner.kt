package xyz.dormos.nfc

import java.util.concurrent.TimeUnit

/** Calls one fixed module entry point. Only a validated UUID can cross the Shell boundary. */
object KsuRules {
    const val MODULE = "/data/adb/modules/dormos_nfc_actions/run.sh"

    fun validSceneId(id: String?): Boolean = NfcProfiles.validId(id)

    fun sceneCommand(id: String): String {
        require(validSceneId(id))
        return "/system/bin/sh $MODULE --nfc $id"
    }

    const val LEGACY_NCWU_COMMAND = "/system/bin/sh /data/adb/modules/dormos_nfc_actions/run.sh --legacy-ncwu"

    fun succeeded(exitCode: Int, output: String): Boolean =
        exitCode == 0 && output.length <= 4096 &&
            output.lineSequence().any { it == "DORM_NFC_RESULT=success" }
}

class KsuActionRunner {
    fun runScene(id: String): String {
        if (!KsuRules.validSceneId(id)) return "场景编号无效，已拒绝执行。"
        return execute(KsuRules.sceneCommand(id))
    }

    fun runLegacyNcwu(): String = execute(KsuRules.LEGACY_NCWU_COMMAND)

    private fun execute(command: String): String {
        val process = try {
            ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        } catch (_: Exception) {
            return "无法启动 KernelSU Root 接口。请确认模块已安装，并在 KernelSU 中授权 NFC Receiver。"
        }

        return try {
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return "执行超时，已请求结束本次任务。"
            }
            val output = process.inputStream.bufferedReader().use { reader ->
                buildString {
                    val chunk = CharArray(512)
                    while (length < 4096) {
                        val count = reader.read(chunk, 0, minOf(chunk.size, 4096 - length))
                        if (count < 0) break
                        append(chunk, 0, count)
                    }
                }
            }
            if (!KsuRules.succeeded(process.exitValue(), output)) {
                val detail = output.lineSequence()
                    .filter { it.startsWith("DORM_NFC_ERROR=") || it.startsWith("DORM_NFC_RESULT=") }
                    .take(3).joinToString("\n")
                "KernelSU 动作未通过验证。\n" + (detail.ifBlank { "检查模块安装、NFC 开关和 Root 授权。" })
            } else {
                val action = output.lineSequence().firstOrNull { it.startsWith("DORM_ACTION=") }
                    ?.substringAfter("=") ?: "unknown"
                val summary = output.lineSequence()
                    .filter { it.startsWith("NCWU_") || it.startsWith("WLAN_") || it.startsWith("BROWSER=") || it.startsWith("DORM_SCENE=") || it == "LOGIN=not_attempted" }
                    .take(5).joinToString("\n")
                "KernelSU 动作执行完成。\n动作：$action" + if (summary.isNotBlank()) "\n$summary" else ""
            }
        } catch (_: Exception) {
            process.destroyForcibly()
            "KernelSU 执行异常，已请求停止。"
        } finally {
            process.destroy()
        }
    }
}
