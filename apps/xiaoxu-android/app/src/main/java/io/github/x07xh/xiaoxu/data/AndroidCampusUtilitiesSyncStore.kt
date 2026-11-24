package io.github.x07xh.xiaoxu.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.time.YearMonth
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** Latest monthly summary only, not a historical billing database.
 * Scope must be derived from a verified owner, school subject and room binding.
 * No grant, account number or room coordinates are persisted by this store.
 * All callers must use a single application process.
 */
internal class AndroidCampusUtilitiesSyncStore(context: Context) : CampusUtilitiesSyncStore {
    private val prefs = context.applicationContext.getSharedPreferences(
        "xiaoxu_campus_utilities_sync_v1", Context.MODE_PRIVATE)

    private fun secret(create: Boolean): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        check(create) { "utility_cache_key_missing" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true).build())
            }.generateKey()
    }

    private fun validateScope(scope: String) {
        require(Regex("^[a-f0-9]{64}$").matches(scope))
    }

    private fun aad(scope: String): ByteArray =
        ("xiaoxu-campus-utilities-sync-v1|" + scope).toByteArray(Charsets.UTF_8)

    private fun readExisting(scope: String): CampusUtilityMonthResult? {
        if (!prefs.contains("body_$scope") && !prefs.contains("iv_$scope")) return null
        val ivText = checkNotNull(prefs.getString("iv_$scope", null))
        val bodyText = checkNotNull(prefs.getString("body_$scope", null))
        require(ivText.length <= 24 && bodyText.length <= 4096)
        val iv = Base64.getDecoder().decode(ivText)
        val bytes = Base64.getDecoder().decode(bodyText)
        require(iv.size == 12 && bytes.size in 17..2064)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secret(false), GCMParameterSpec(128, iv))
        cipher.updateAAD(aad(scope))
        return CampusUtilitiesSnapshotCodec.decode(cipher.doFinal(bytes))
    }

    override fun load(scope: String, month: YearMonth): CampusUtilityMonthResult? =
        synchronized(LOCK) {
            validateScope(scope)
            // A temporary Keystore error is not a reason to erase ciphertext.
            readExisting(scope)?.takeIf { it.month == month.toString() }
        }

    override fun reserveIfDue(scope: String, now: Long, interval: Long): Boolean =
        synchronized(LOCK) {
            validateScope(scope)
            require(now > 0 && interval >= CampusUtilitiesSyncCoordinator.DEFAULT_INTERVAL_MILLIS)
            val key = "attempt_$scope"
            val last = prefs.getLong(key, 0L)
            if (last < 0 || last >= now || (last > 0 && now - last < interval)) {
                return@synchronized false
            }
            prefs.edit().putLong(key, now).commit()
        }

    override fun save(scope: String, result: CampusUtilityMonthResult) {
        synchronized(LOCK) {
            validateScope(scope)
            // Do not replace an existing unreadable record or an older live read.
            val existing = readExisting(scope)
            check(existing == null || result.fetchedAtMillis >= existing.fetchedAtMillis)
            val plain = CampusUtilitiesSnapshotCodec.encode(result)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secret(true))
            cipher.updateAAD(aad(scope))
            val encrypted = cipher.doFinal(plain)
            check(prefs.edit()
                .putString("iv_$scope", Base64.getEncoder().encodeToString(cipher.iv))
                .putString("body_$scope", Base64.getEncoder().encodeToString(encrypted))
                .commit()) { "utility_cache_write_failed" }
        }
    }

    override fun clear(scope: String) {
        synchronized(LOCK) {
            validateScope(scope)
            // Keep the attempt budget: signing out is not a rate-limit reset.
            check(prefs.edit().remove("iv_$scope").remove("body_$scope").commit()) {
                "utility_cache_clear_failed"
            }
        }
    }

    private companion object {
        const val KEY_ALIAS = "xiaoxu_campus_utilities_sync_aes_v1"
        val LOCK = Any()
    }
}

internal object CampusUtilitiesSnapshotCodec {
    private const val SCHEMA = "xiaoxu/campus-utilities-month-v1"

    private fun validate(result: CampusUtilityMonthResult) {
        require(result.utility == CampusUtilityKind.ELECTRIC && result.accountNum == null)
        require(YearMonth.parse(result.month).toString() == result.month)
        require(result.detailVersion in setOf("v1", "v2") && result.fetchedAtMillis > 0)
        require(result.recordCount == null || result.recordCount >= 0)
        result.totalUsed?.let { value ->
            require(value.length in 1..64 && Regex("^[0-9]+(\\.[0-9]+)?$").matches(value))
        }
        require(result.totalUsed != null || result.recordCount != null)
    }

    fun encode(result: CampusUtilityMonthResult): ByteArray {
        validate(result)
        return JSONObject().put("schema", SCHEMA)
            .put("month", result.month)
            .put("totalUsed", result.totalUsed ?: JSONObject.NULL)
            .put("recordCount", result.recordCount ?: JSONObject.NULL)
            .put("version", result.detailVersion)
            .put("fetchedAt", result.fetchedAtMillis)
            .toString().toByteArray(Charsets.UTF_8).also { require(it.size <= 2048) }
    }

    fun decode(bytes: ByteArray): CampusUtilityMonthResult {
        require(bytes.size in 2..2048)
        val json = JSONObject(String(bytes, Charsets.UTF_8))
        require(json.getString("schema") == SCHEMA)
        val used = json.get("totalUsed").let {
            if (it === JSONObject.NULL) null else it as String
        }
        val count = json.get("recordCount").let {
            if (it === JSONObject.NULL) null else it as Int
        }
        val fetched = json.get("fetchedAt")
        require(fetched is Int || fetched is Long)
        return CampusUtilityMonthResult(CampusUtilityKind.ELECTRIC,
            json.getString("month"), null, used, count, json.getString("version"),
            (fetched as Number).toLong()).also(::validate)
    }
}
