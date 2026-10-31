package xyz.dormos.nfc

import org.json.JSONArray
import org.json.JSONObject

/**
 * Manual, user-directed SAF export/import. No cloud, background sync, passwords or root options.
 * Import fails closed: never silently drop invalid or duplicate profiles.
 */
object NfcProfileBackup {
    const val MAX_BYTES = 65536
    const val VERSION = 1
    const val MAX_PROFILES = 32

    fun encode(profiles: List<NfcProfile>): String {
        require(profiles.size <= MAX_PROFILES && profiles.all(NfcProfiles::validProfile))
        require(profiles.distinctBy { it.id }.size == profiles.size)
        val records = JSONArray()
        for (p in profiles) {
            records.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("action", p.action)
                put("target", p.target)
            })
        }
        return JSONObject().apply {
            put("type", "dorm-os-nfc-profiles")
            put("version", VERSION)
            put("profiles", records)
        }.toString(2)
    }

    fun decode(input: String): List<NfcProfile>? {
        if (input.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return null
        return try {
            val root = JSONObject(input)
            if (root.optString("type") != "dorm-os-nfc-profiles" ||
                root.optInt("version") != VERSION) return null
            val records = root.optJSONArray("profiles") ?: return null
            if (records.length() > MAX_PROFILES) return null
            val result = ArrayList<NfcProfile>(records.length())
            val seen = HashSet<String>()
            for (i in 0 until records.length()) {
                val record = records.optJSONObject(i) ?: return null
                val p = NfcProfile(
                    record.optString("id"), record.optString("name"),
                    record.optString("action"), record.optString("target")
                )
                if (!NfcProfiles.validProfile(p) || !seen.add(p.id)) return null
                result.add(p)
            }
            result
        } catch (_: Exception) {
            null
        }
    }
}
