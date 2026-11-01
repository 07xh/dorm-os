package xyz.dormos.nfc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/** Local, non-root actions. Old KernelSU scene bindings remain completely separate. */
internal enum class NativeKind(val title: String, val hint: String) {
    COPY("复制文字", "保存到剪贴板"),
    APP("打开应用", "输入应用包名"),
    URL("打开链接", "仅支持 HTTPS"),
    CHAT_DRAFT("指定聊天草稿", "选择聊天应用、群名和消息，手动发送"),
    SH("执行 SH 脚本", "只调用已保存的脚本，需明确确认"),
    MANUAL("人工确认步骤", "提示用户完成最后一步")
}

internal data class NativeStep(val kind: NativeKind, val value: String)
internal data class NativeScene(val id: String, val name: String, val actions: List<NativeStep>)

internal object NativeSceneRules {
    private val idPattern = Regex("^[a-z0-9][a-z0-9-]{0,63}$")
    private val pkgPattern = Regex("^[a-zA-Z_][a-zA-Z_0-9]*(\\.[a-zA-Z_][a-zA-Z_0-9]*)+$")
    fun sceneId(uri: String?): String? {
        val parsed = try { URI(uri ?: return null) } catch (_: Exception) { return null }
        if (parsed.scheme != "tapflow" || parsed.rawAuthority != "scene" ||
            parsed.rawQuery != null || parsed.rawFragment != null || parsed.rawUserInfo != null ||
            parsed.port != -1) return null
        val path = parsed.rawPath ?: return null
        val id = path.removePrefix("/")
        return id.takeIf { path == "/$id" && idPattern.matches(it) }
    }
    fun uri(id: String): String = "tapflow://scene/$id"
    fun valid(scene: NativeScene): Boolean {
        if (!idPattern.matches(scene.id) || scene.name.isBlank() ||
            scene.name.length > 50 || scene.actions.isEmpty() || scene.actions.size > 12) return false
        var handedOff = false
        for (action in scene.actions) {
            val v = action.value.trim()
            if (v.isBlank() || v.length > 2000) return false
            if (handedOff && action.kind != NativeKind.MANUAL) return false
            when (action.kind) {
                NativeKind.COPY, NativeKind.MANUAL -> {}
                NativeKind.CHAT_DRAFT -> if (ChatDraftCodec.decode(action.value) == null) return false
                NativeKind.APP -> if (!pkgPattern.matches(v)) return false
                NativeKind.URL -> {
                    val u = try { URI(v) } catch (_: Exception) { return false }
                    if (u.scheme != "https" || u.host.isNullOrBlank() || u.rawUserInfo != null) return false
                }
                NativeKind.SH -> if (!NfcProfiles.validId(v)) return false
            }
            if (action.kind == NativeKind.APP || action.kind == NativeKind.URL || action.kind == NativeKind.CHAT_DRAFT) handedOff = true
        }
        return true
    }
}

/**
 * Reusable chat draft parameters. The first two newline-delimited fields are
 * app package and exact chat name; remaining text is the user's draft.
 * An app adapter may later locate a verified chat, but this fallback never
 * guesses which chat is open and never presses the Send button.
 */
internal data class ChatDraft(val appPackage: String, val chatName: String, val message: String)

internal object ChatDraftCodec {
    private val packagePattern = Regex("^[a-zA-Z_][a-zA-Z_0-9]*(\\.[a-zA-Z_][a-zA-Z_0-9]*)+$")
    fun encode(draft: ChatDraft): String =
        draft.appPackage + "\n" + draft.chatName + "\n" + draft.message

    fun decode(value: String): ChatDraft? {
        val first = value.indexOf('\n')
        if (first < 1) return null
        val second = value.indexOf('\n', first + 1)
        if (second <= first + 1) return null
        val draft = ChatDraft(
            value.substring(0, first),
            value.substring(first + 1, second),
            value.substring(second + 1)
        )
        if (!packagePattern.matches(draft.appPackage) || draft.appPackage.length > 130 ||
            draft.chatName.isBlank() || draft.chatName.length > 80 ||
            draft.chatName.any { it == '\n' || it == '\r' || it == '\u0000' } ||
            draft.message.isBlank() || draft.message.length > 1700 ||
            draft.message.contains('\u0000')
        ) return null
        return draft
    }
}

/** Avoids storing account credentials on tags; only the local action ID is written. */
internal class NativeSceneStore(context: Context) {
    private val prefs = context.getSharedPreferences("tapflow_native_scenes", Context.MODE_PRIVATE)
    init {
        if (!prefs.getBoolean("initialized", false)) {
            if (!prefs.contains("scenes")) {
                val welcome = NativeScene("wechat-group-01", "26电气日常查寝", listOf(
                    NativeStep(NativeKind.CHAT_DRAFT,
                        ChatDraftCodec.encode(ChatDraft("com.tencent.mm", "26电气日常查寝", "8624均在寝"))),
                    NativeStep(NativeKind.MANUAL, "选择正确的群聊并手动确认发送")
                ))
                prefs.edit().putString("scenes", JSONArray().put(encode(welcome)).toString())
                    .putBoolean("initialized", true).apply()
            } else prefs.edit().putBoolean("initialized", true).apply()
        }
    }
    private fun encode(scene: NativeScene): JSONObject = JSONObject()
        .put("id", scene.id).put("name", scene.name)
        .put("actions", JSONArray().apply {
            scene.actions.forEach { put(JSONObject().put("type", it.kind.name).put("value", it.value)) }
        })
    fun list(): List<NativeScene> {
        val array = try { JSONArray(prefs.getString("scenes", "[]")) } catch (_: Exception) { JSONArray() }
        val result = mutableListOf<NativeScene>()
        for (i in 0 until array.length()) try {
            val obj = array.getJSONObject(i)
            val steps = obj.getJSONArray("actions")
            val actions = (0 until steps.length()).map { j ->
                val step = steps.getJSONObject(j)
                NativeStep(NativeKind.valueOf(step.getString("type")), step.getString("value"))
            }
            val scene = NativeScene(obj.getString("id"), obj.getString("name"), actions)
            if (NativeSceneRules.valid(scene)) result.add(scene)
        } catch (_: Exception) { }
        return result
    }
    fun find(id: String): NativeScene? = list().firstOrNull { it.id == id }
    fun save(scene: NativeScene): Boolean {
        if (!NativeSceneRules.valid(scene)) return false
        val newList = list().filterNot { it.id == scene.id } + scene
        val encoded = JSONArray()
        newList.forEach { encoded.put(encode(it)) }
        return prefs.edit().putString("scenes", encoded.toString()).commit()
    }
    fun delete(id: String): Boolean {
        val encoded = JSONArray()
        list().filterNot { it.id == id }.forEach { encoded.put(encode(it)) }
        return prefs.edit().putString("scenes", encoded.toString()).commit()
    }
}

/** Only executes locally reviewed actions. NFC never supplies a shell command. */
internal object NativeSceneRunner {
    fun run(context: Context, scene: NativeScene, rootApproved: Boolean, done: (String) -> Unit) {
        if (!NativeSceneRules.valid(scene)) { done("场景不合法，已拒绝执行"); return }
        if (scene.actions.any { it.kind == NativeKind.SH } && !rootApproved) {
            done("此场景包含 SH，请在 TapFlow 界面手动确认；碰卡不会直接运行 Root 脚本")
            return
        }
        fun execute(index: Int) {
            if (index >= scene.actions.size) { done("场景已执行；消息仍需手动发送"); return }
            val a = scene.actions[index]
            when (a.kind) {
                NativeKind.COPY -> {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("TapFlow", a.value))
                    execute(index + 1)
                }
                NativeKind.MANUAL -> {
                    Toast.makeText(context, a.value.take(150), Toast.LENGTH_LONG).show()
                    execute(index + 1)
                }
                NativeKind.CHAT_DRAFT -> {
                    val draft = ChatDraftCodec.decode(a.value)
                    if (draft == null) { done("聊天草稿配置无效"); return }
                    // Public ACTION_SEND: the chat recipient must still be chosen and verified
                    // by the person using the phone. No private Activity or auto-send.
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("TapFlow 聊天草稿", draft.message))
                    Toast.makeText(context, "目标：" + draft.chatName.take(32) +
                        "。请核对聊天对象并手动发送", Toast.LENGTH_LONG).show()
                    val share = Intent(Intent.ACTION_SEND).apply {
                        setPackage(draft.appPackage)
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, draft.message)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(share)
                        done("已尝试打开分享界面；请选择「" + draft.chatName.take(32) +
                            "」并核对内容后手动发送")
                    } catch (_: Exception) {
                        // Some chat clients cannot accept plain-text shares. Retain clipboard.
                        val fallback = context.packageManager.getLaunchIntentForPackage(draft.appPackage)
                        if (fallback == null) {
                            done("应用不支持分享或不可启动；草稿已复制")
                        } else try {
                            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(fallback)
                            done("分享入口不可用，已复制草稿并打开应用；请手动找到正确聊天")
                        } catch (_: Exception) { done("聊天应用启动失败，草稿已复制") }
                    }
                }
                NativeKind.APP, NativeKind.URL -> {
                    // Show instructions before handing foreground control to an external app.
                    scene.actions.drop(index + 1).filter { it.kind == NativeKind.MANUAL }
                        .forEach { Toast.makeText(context, it.value.take(150), Toast.LENGTH_LONG).show() }
                    val intent = if (a.kind == NativeKind.APP) {
                        context.packageManager.getLaunchIntentForPackage(a.value)
                    } else Intent(Intent.ACTION_VIEW, Uri.parse(a.value))
                    if (intent == null) { done("无法启动目标应用"); return }
                    try {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        done("已启动目标应用；请自行确认后续步骤")
                    } catch (_: Exception) { done("目标应用启动失败") }
                }
                NativeKind.SH -> Thread {
                    val result = RootModuleClient().runScript(a.value)
                    Handler(Looper.getMainLooper()).post {
                        if (!result.success) done("SH 执行失败：" + result.output.take(120))
                        else execute(index + 1)
                    }
                }.start()
            }
        }
        try { execute(0) } catch (_: Exception) { done("原生动作执行失败") }
    }
}
