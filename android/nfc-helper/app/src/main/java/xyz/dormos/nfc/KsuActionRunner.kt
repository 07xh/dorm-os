package xyz.dormos.nfc

/** NFC can call only the fixed KernelSU dispatcher with a validated tag UUID. */
object KsuRules {
    const val MODULE = "/data/adb/modules/dormos_nfc_actions/run.sh"

    fun validSceneId(id: String?): Boolean = NfcProfiles.validId(id)

    fun sceneCommand(id: String): String {
        require(validSceneId(id))
        return "/system/bin/sh $MODULE --nfc $id"
    }

    fun succeeded(exitCode: Int, output: String): Boolean =
        exitCode == 0 && output.lineSequence().any { it == "DORM_NFC_RESULT=success" }
}

class KsuActionRunner(
    private val bridge: RootModuleClient = RootModuleClient()
) {
    fun runScene(id: String): String {
        if (!KsuRules.validSceneId(id)) return "标签编号无效，已拒绝执行。"
        val result = bridge.triggerNfc(id)
        if (!KsuRules.succeeded(result.code, result.output)) {
            val detail = result.output.lineSequence()
                .filter { it.startsWith("DORM_NFC_ERROR=") || it.startsWith("DORM_NFC_RESULT=") }
                .take(3).joinToString("\n")
            return "KernelSU 动作未通过验证。\n" +
                (detail.ifBlank { result.output.ifBlank { "检查模块安装、NFC 开关和 Root 授权。" } })
        }
        val scriptId = result.output.lineSequence()
            .lastOrNull { it.startsWith("DORM_SCRIPT_ID=") }
            ?.substringAfter("=") ?: "unknown"
        return "KernelSU 脚本执行完成。\n脚本 ID：$scriptId"
    }
}
