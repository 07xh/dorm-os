package xyz.dormos.nfc

import android.util.Base64
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Personal script registry shared by the native app and NFC handler. KernelSU only. */
data class PersonalScript(val id: String, val name: String, val bytes: Int)
data class RootResult(val code: Int, val output: String) {
    val success: Boolean get() = code == 0
}

object ScriptRules {
    private val uuid = Regex("^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$")
    private val b64 = Regex("^[A-Za-z0-9+/]+={0,2}$")
    fun idValid(value: String) = uuid.matches(value)
    fun safeArguments(args: List<String>): Boolean {
        val head = args.firstOrNull() ?: return false
        return when {
            args.size == 1 && head in setOf("--status", "--script-list", "--script-bindings", "--script-logs") -> true
            args.size == 2 && head == "--enable" -> args[1] in setOf("0", "1")
            args.size == 2 && head in setOf("--script-read", "--script-run", "--script-delete", "--script-unbind") -> idValid(args[1])
            args.size == 3 && head == "--script-bind" -> idValid(args[1]) && idValid(args[2])
            args.size == 4 && head == "--script-save" ->
                idValid(args[1]) && args[2].length <= 360 && b64.matches(args[2]) &&
                    args[3].length <= 24000 && b64.matches(args[3])
            else -> false
        }
    }
}

class RootModuleClient {
    private val module = "/data/adb/modules/dormos_nfc_actions/run.sh"

    private fun encode(input: String) =
        Base64.encodeToString(input.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private fun decode(input: String) =
        String(Base64.decode(input, Base64.DEFAULT), Charsets.UTF_8)

    fun invoke(vararg args: String): RootResult {
        val list = args.toList()
        if (!ScriptRules.safeArguments(list)) return RootResult(64, "拒绝未登记或不合法的操作")
        val cmd = "/system/bin/sh $module " + list.joinToString(" ")
        val process = try {
            ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
        } catch (_: Exception) {
            return RootResult(69, "无法启动 KernelSU Root，请确认已授权本应用")
        }
        val readerPool = Executors.newSingleThreadExecutor()
        val outputJob = readerPool.submit<String> {
            process.inputStream.bufferedReader().use { reader ->
                val buf = CharArray(2048)
                val result = StringBuilder()
                while (true) {
                    val count = reader.read(buf)
                    if (count < 0) break
                    if (result.length < 42000) {
                        result.append(buf, 0, minOf(count, 42000 - result.length))
                    }
                }
                result.toString()
            }
        }
        return try {
            if (!process.waitFor(26, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                RootResult(124, "脚本执行超时（26 秒），已请求结束")
            } else {
                RootResult(process.exitValue(), outputJob.get(2, TimeUnit.SECONDS).trim())
            }
        } catch (_: Exception) {
            process.destroyForcibly()
            RootResult(70, "Root 执行异常")
        } finally {
            outputJob.cancel(true)
            readerPool.shutdownNow()
            process.destroy()
        }
    }

    fun listScripts(): Pair<List<PersonalScript>, RootResult> {
        val result = invoke("--script-list")
        if (!result.success) return emptyList<PersonalScript>() to result
        val entries = result.output.lineSequence().mapNotNull { line ->
            val fields = line.split("|")
            if (fields.size != 3 || !ScriptRules.idValid(fields[0])) return@mapNotNull null
            try {
                PersonalScript(fields[0], decode(fields[1]), fields[2].toInt())
            } catch (_: Exception) { null }
        }.toList()
        return entries to result
    }

    fun readScript(id: String): Pair<String?, RootResult> {
        val result = invoke("--script-read", id)
        if (!result.success) return null to result
        val body = result.output.lineSequence()
            .firstOrNull { it.startsWith("SCRIPT_BODY64=") }?.substringAfter("=")
        return try { if (body == null) null to result else decode(body) to result }
        catch (_: Exception) { null to RootResult(65, "脚本内容解码失败") }
    }

    fun saveScript(id: String, name: String, body: String): RootResult {
        if (!ScriptRules.idValid(id) || name.isBlank() || name.toByteArray().size > 120 ||
            body.isBlank() || body.toByteArray().size > 16384)
            return RootResult(64, "名称或脚本内容无效（最大 16 KiB）")
        return invoke("--script-save", id, encode(name), encode(body.replace("\r\n", "\n")))
    }

    fun deleteScript(id: String) = invoke("--script-delete", id)
    fun runScript(id: String) = invoke("--script-run", id)
    fun bind(tagId: String, scriptId: String) = invoke("--script-bind", tagId, scriptId)
    fun unbind(tagId: String) = invoke("--script-unbind", tagId)
    fun listBindings() = invoke("--script-bindings")
    fun status() = invoke("--status")
    fun enableNfc(value: Boolean) = invoke("--enable", if (value) "1" else "0")
    fun logs() = invoke("--script-logs")
    fun newId(): String = UUID.randomUUID().toString()
}
