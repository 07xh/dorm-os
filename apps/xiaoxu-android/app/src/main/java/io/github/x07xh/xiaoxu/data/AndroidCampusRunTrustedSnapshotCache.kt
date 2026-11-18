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
import org.json.JSONArray
import org.json.JSONObject

/**
 * Private, authenticated cache for an already VERIFIED own-account read.
 * This is NOT a source of authorization or a substitute for the official
 * upstream identity check. No credentials, tokens, tracks, or device IDs.
 *
 * Keystore AES-GCM AAD binds both the validated 校序 owner scope and
 * upstream subject fingerprint. A different owner/account cannot read the
 * previous snapshot, even if the ciphertext is copied between preferences.
 */
interface CampusRunTrustedSnapshotCache {
    fun load(subjectFingerprint: String, nowEpochMillis: Long): CampusRunSnapshot?
    /** false means the live read was valid but the local cache could not persist. */
    fun save(subjectFingerprint: String, snapshot: CampusRunSnapshot, nowEpochMillis: Long): Boolean
    fun clear(): Boolean
}

class AndroidCampusRunTrustedSnapshotCache(context: Context, private val ownerScope: String) :
    CampusRunTrustedSnapshotCache {
    init {
        require(Regex("^owner_[a-f0-9]{64}$").matches(ownerScope)) {
            "trusted_cache_requires_verified_owner"
        }
    }

    private val prefs = context.applicationContext.getSharedPreferences(
        "xiaoxu_campus_run_trusted_v1", Context.MODE_PRIVATE
    )
    private val ivKey = "iv_$ownerScope"
    private val bodyKey = "body_$ownerScope"

    private fun secret(): SecretKey {
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keystore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
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

    private fun aad(subjectFingerprint: String): ByteArray {
        require(SUBJECT.matches(subjectFingerprint)) { "invalid_cache_subject" }
        return ("xiaoxu-campus-run-trusted-v1|" + ownerScope + "|" + subjectFingerprint)
            .toByteArray(Charsets.UTF_8)
    }

    override fun save(
        subjectFingerprint: String,
        snapshot: CampusRunSnapshot,
        nowEpochMillis: Long
    ): Boolean {
        require(snapshot.provenance == CampusRunProvenance.TRUSTED_READONLY &&
            snapshot.ownerMatched) { "unverified_trusted_cache_write" }
        validateCampusRunSnapshot(snapshot, nowEpochMillis)
        val json = JSONObject()
            .put("schema", SCHEMA)
            .put("subject", subjectFingerprint)
            .put("capturedAt", snapshot.capturedAtEpochMillis)
            .put("term", snapshot.termLabel ?: JSONObject.NULL)
            .put("target", snapshot.targetMeters ?: JSONObject.NULL)
            .put("credited", snapshot.effectiveMeters ?: JSONObject.NULL)
            .put("total", snapshot.rawMeters ?: JSONObject.NULL)
            .put("dashboard", snapshot.dashboardAccumulatedMeters ?: JSONObject.NULL)
            .put("weekly", snapshot.weeklyMeters ?: JSONObject.NULL)
            .put("totalCount", snapshot.totalRunCount ?: JSONObject.NULL)
            .put("creditedCount", snapshot.effectiveRunCount ?: JSONObject.NULL)
            .put("historyComplete", snapshot.historyComplete)

        val rows = JSONArray()
        snapshot.records.forEach { record ->
            rows.put(JSONObject()
                .put("key", record.recordKey)
                .put("startedAt", record.startedAtEpochMillis ?: JSONObject.NULL)
                .put("raw", record.rawMeters ?: JSONObject.NULL)
                .put("credited", record.effectiveMeters ?: JSONObject.NULL)
                .put("seconds", record.durationSeconds ?: JSONObject.NULL)
                .put("status", record.status.name)
                .put("kind", record.kind.name)
                .put("anomaly", record.hasAnomaly ?: JSONObject.NULL))
        }
        json.put("records", rows)
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size in 2..MAX_PLAINTEXT_BYTES) { "trusted_cache_too_large" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret())
        cipher.updateAAD(aad(subjectFingerprint))
        val encrypted = cipher.doFinal(bytes)
        return prefs.edit()
            .putString(ivKey, Base64.getEncoder().encodeToString(cipher.iv))
            .putString(bodyKey, Base64.getEncoder().encodeToString(encrypted))
            .commit()
    }

    override fun load(subjectFingerprint: String, nowEpochMillis: Long): CampusRunSnapshot? {
        if (!prefs.contains(bodyKey)) return null
        return try {
            val iv = Base64.getDecoder().decode(prefs.getString(ivKey, null)
                ?: error("missing_cache_iv"))
            require(iv.size == 12) { "invalid_cache_iv" }
            val encrypted = Base64.getDecoder().decode(prefs.getString(bodyKey, null)
                ?: error("missing_cache_body"))
            require(encrypted.size in 17..MAX_PLAINTEXT_BYTES + 16) {
                "invalid_cache_cipher_length"
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, iv))
            cipher.updateAAD(aad(subjectFingerprint))
            val bytes = cipher.doFinal(encrypted)
            require(bytes.size in 2..MAX_PLAINTEXT_BYTES) { "invalid_cache_body_length" }
            val json = JSONObject(String(bytes, Charsets.UTF_8))
            require(json.getString("schema") == SCHEMA &&
                json.getString("subject") == subjectFingerprint) {
                "trusted_cache_identity_mismatch"
            }
            val array = json.getJSONArray("records")
            require(array.length() <= 1_000) { "too_many_cached_records" }
            val records = (0 until array.length()).map { index ->
                val row = array.getJSONObject(index)
                val anomaly = row.opt("anomaly")
                require(anomaly == null || anomaly === JSONObject.NULL || anomaly is Boolean)
                CampusRunRecord(
                    recordKey = row.getString("key"),
                    startedAtEpochMillis = row.nullableLong("startedAt"),
                    rawMeters = row.nullableLong("raw"),
                    effectiveMeters = row.nullableLong("credited"),
                    durationSeconds = row.nullableLong("seconds"),
                    status = CampusRunRecordStatus.valueOf(row.getString("status")),
                    kind = CampusRunRecordKind.valueOf(row.getString("kind")),
                    hasAnomaly = anomaly as? Boolean
                )
            }
            require(json.opt("historyComplete") is Boolean) { "invalid_history_complete" }
            val result = CampusRunSnapshot(
                provenance = CampusRunProvenance.TRUSTED_READONLY,
                ownerMatched = true,
                capturedAtEpochMillis = json.getLong("capturedAt"),
                termLabel = when (val term = json.opt("term")) {
                    null, JSONObject.NULL -> null
                    is String -> term
                    else -> error("invalid_cached_term")
                },
                targetMeters = json.nullableLong("target"),
                effectiveMeters = json.nullableLong("credited"),
                rawMeters = json.nullableLong("total"),
                dashboardAccumulatedMeters = json.nullableLong("dashboard"),
                weeklyMeters = json.nullableLong("weekly"),
                totalRunCount = json.nullableInt("totalCount"),
                effectiveRunCount = json.nullableInt("creditedCount"),
                historyComplete = json.getBoolean("historyComplete"),
                records = records
            )
            validateCampusRunSnapshot(result, nowEpochMillis)
        } catch (_: Exception) {
            // Invalid AEAD, moved credentials, damaged schema or account change:
            // do not expose a previous owner's personal snapshot.
            clear()
            null
        }
    }

    override fun clear(): Boolean =
        prefs.edit().remove(ivKey).remove(bodyKey).commit()

    private fun JSONObject.nullableLong(key: String): Long? = when (val value = opt(key)) {
        null, JSONObject.NULL -> null
        is Int -> value.toLong()
        is Long -> value
        else -> error("invalid_cached_integer")
    }

    private fun JSONObject.nullableInt(key: String): Int? = nullableLong(key)?.let {
        require(it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
        it.toInt()
    }

    private companion object {
        const val KEY_ALIAS = "xiaoxu_campus_run_trusted_aes_v1"
        const val SCHEMA = "xiaoxu/campus-run-trusted-v1"
        const val MAX_PLAINTEXT_BYTES = 512_000
        val SUBJECT = Regex("^[a-f0-9]{64}$")
    }
}
