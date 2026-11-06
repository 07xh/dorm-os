package io.github.x07xh.xiaoxu.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/**
 * An explicitly entered personal semester summary, encrypted on this device.
 * No campus credentials, HAR, Cookies, GPS tracks, or remote API requests.
 * The schema is deliberately separate from a future trusted school connector.
 */
class LocalCampusRunStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "xiaoxu_campus_run_manual_v1", Context.MODE_PRIVATE
    )

    private fun secret(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build())
            }.generateKey()
    }

    fun save(input: CampusRunManualInput, nowEpochMillis: Long): CampusRunSnapshot {
        val snapshot = input.toSnapshot(nowEpochMillis)
        require(snapshot.provenance == CampusRunProvenance.USER_IMPORT)
        val json = JSONObject()
            .put("schema", "xiaoxu/campus-run-manual-v1")
            .put("capturedAt", snapshot.capturedAtEpochMillis)
            .put("term", snapshot.termLabel ?: JSONObject.NULL)
            .put("target", snapshot.targetMeters ?: JSONObject.NULL)
            .put("credited", snapshot.effectiveMeters ?: JSONObject.NULL)
            .put("semesterTotal", snapshot.rawMeters ?: JSONObject.NULL)
            .put("dashboardTotal", snapshot.dashboardAccumulatedMeters ?: JSONObject.NULL)
            .put("weekly", snapshot.weeklyMeters ?: JSONObject.NULL)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret())
        cipher.updateAAD(AAD)
        val encrypted = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        require(preferences.edit()
            .putString("iv", Base64.getEncoder().encodeToString(cipher.iv))
            .putString("cipher", Base64.getEncoder().encodeToString(encrypted))
            .commit()) { "campus_run_local_write_failed" }
        return snapshot
    }

    fun load(nowEpochMillis: Long): CampusRunSnapshot? {
        if (!preferences.contains("cipher")) return null
        return try {
            val iv = Base64.getDecoder().decode(
                preferences.getString("iv", null) ?: error("missing_iv")
            )
            require(iv.size == 12) { "invalid_iv" }
            val encrypted = Base64.getDecoder().decode(
                preferences.getString("cipher", null) ?: error("missing_cipher")
            )
            require(encrypted.size in 17..4_096) { "invalid_cipher" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, iv))
            cipher.updateAAD(AAD)
            val plaintext = String(cipher.doFinal(encrypted), Charsets.UTF_8)
            require(plaintext.length <= 2_000) { "oversized_snapshot" }
            val json = JSONObject(plaintext)
            require(json.getString("schema") == "xiaoxu/campus-run-manual-v1")
            fun nullableMeters(name: String): Long? =
                if (json.isNull(name)) null else {
                    val value = json.get(name)
                    require(value is Number && value.toLong().toDouble() ==
                        value.toDouble()) { "invalid_meter_field" }
                    value.toLong()
                }
            val snapshot = CampusRunSnapshot(
                provenance = CampusRunProvenance.USER_IMPORT,
                ownerMatched = false,
                capturedAtEpochMillis = json.getLong("capturedAt"),
                termLabel = if (json.isNull("term")) null else json.getString("term"),
                targetMeters = nullableMeters("target"),
                effectiveMeters = nullableMeters("credited"),
                rawMeters = nullableMeters("semesterTotal"),
                historyComplete = false,
                records = emptyList(),
                dashboardAccumulatedMeters = nullableMeters("dashboardTotal"),
                weeklyMeters = nullableMeters("weekly")
            )
            validateCampusRunSnapshot(snapshot, nowEpochMillis)
        } catch (_: Exception) {
            // A corrupted or incompatible secret must not surface misleading
            // cached personal data; never silently turn absent fields into 0.
            clear()
            null
        }
    }

    fun clear() {
        require(preferences.edit().clear().commit()) { "campus_run_local_clear_failed" }
    }

    private companion object {
        const val KEY_ALIAS = "xiaoxu_campus_run_manual_aes_v1"
        val AAD = "xiaoxu-campus-run-manual-v1".toByteArray(Charsets.UTF_8)
    }
}
