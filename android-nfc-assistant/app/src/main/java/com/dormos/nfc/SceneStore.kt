package com.dormos.nfc

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Scene(
    val id: String,
    val name: String,
    val action: String,
    val enabled: Boolean = true
)

/** All actions are mapped onto known ModuleGateway dispatcher IDs. */
object AllowedActions {
    val items = linkedMapOf(
        "study" to "学习模式",
        "dingtalk" to "打开钉钉",
        "ncwu-check" to "Ncwu 网络检查",
        "dorm" to "打开 DORM OS"
    )
}

class SceneStore(context: Context) {
    private val prefs = context.getSharedPreferences("dorm_scene_v2", Context.MODE_PRIVATE)
    fun all(): List<Scene> {
        val data = prefs.getString("scenes", null) ?: return defaults()
        return try {
            val a = JSONArray(data)
            val results = (0 until a.length()).mapNotNull { i ->
                val v = a.optJSONObject(i) ?: return@mapNotNull null
                val id = v.optString("id")
                val action = v.optString("action")
                if (!id.matches(Regex("[a-z0-9-]{1,48}")) || action !in AllowedActions.items) null
                else Scene(id, v.optString("name").take(40).ifBlank { "未命名场景" }, action, v.optBoolean("enabled", true))
            }
            if (results.isEmpty()) defaults() else results.distinctBy { it.id }.take(50)
        } catch (_: Exception) { defaults() }
    }
    fun save(items: List<Scene>) {
        val a = JSONArray()
        items.take(50).filter { it.action in AllowedActions.items && it.id.matches(Regex("[a-z0-9-]{1,48}")) }.forEach {
            a.put(JSONObject().put("id", it.id).put("name", it.name.take(40)).put("action", it.action).put("enabled", it.enabled))
        }
        prefs.edit().putString("scenes", a.toString()).apply()
    }
    fun fresh(name: String, action: String): Scene = Scene("custom-" + UUID.randomUUID().toString().take(12), name.take(40), action)
    private fun defaults(): List<Scene> = listOf(
        Scene("study", "学习模式", "study"),
        Scene("dingtalk", "钉钉快捷打开", "dingtalk"),
        Scene("ncwu-check", "校园网检查", "ncwu-check"),
        Scene("dorm", "DORM OS 网站", "dorm")
    )
}
