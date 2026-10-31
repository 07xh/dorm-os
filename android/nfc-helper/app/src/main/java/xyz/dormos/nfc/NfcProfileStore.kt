package xyz.dormos.nfc

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Device-only preferences: no cloud sync and no credentials on the tag. */
class NfcProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("nfc_profiles_v2", Context.MODE_PRIVATE)

    fun all(): List<NfcProfile> {
        val saved = prefs.getString("records", "[]") ?: "[]"
        val array = try { JSONArray(saved) } catch (_: Exception) { return emptyList() }
        val result = mutableListOf<NfcProfile>()
        for (i in 0 until minOf(array.length(), 32)) {
            val item = array.optJSONObject(i) ?: continue
            val profile = NfcProfile(
                item.optString("id"),
                item.optString("name"),
                item.optString("action"),
                item.optString("target")
            )
            if (NfcProfiles.validProfile(profile) && result.none { it.id == profile.id }) {
                result.add(profile)
            }
        }
        return result
    }

    fun byId(id: String): NfcProfile? = all().firstOrNull { it.id == id }

    fun save(profile: NfcProfile): Boolean {
        if (!NfcProfiles.validProfile(profile)) return false
        val previous = all()
        val exists = previous.any { it.id == profile.id }
        if (!exists && previous.size >= 32) return false
        return persist(previous.filterNot { it.id == profile.id } + profile)
    }

    fun remove(id: String): Boolean {
        val previous = all()
        if (previous.none { it.id == id }) return false
        return persist(previous.filterNot { it.id == id })
    }

    private fun persist(items: List<NfcProfile>): Boolean {
        val data = JSONArray()
        for (profile in items) {
            data.put(JSONObject().apply {
                put("id", profile.id)
                put("name", profile.name)
                put("action", profile.action)
                put("target", profile.target)
            })
        }
        return prefs.edit().putString("records", data.toString()).commit()
    }
}
