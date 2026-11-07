package io.github.x07xh.xiaoxu.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Narrow JSON seam for the existing Python OSS read-only bridge (#372).
 *
 * The caller supplies its independently authenticated, trusted transport.
 * This class does not select an upstream server, obtain login credentials,
 * perform HTTP, or authenticate a payload by itself. Never use untrusted
 * imported JSON as official school data.
 *
 * These methods reuse #368's CampusRunAuthorizedDecodedReader data contract
 * and pass its verification flags through to the source gate.
 */
class CampusRunBridgeJsonReader(
    private val fetchSubject: suspend () -> String,
    private val fetchSemester: suspend () -> String,
    private val fetchHistoryPage: suspend (String?) -> String
) : CampusRunAuthorizedDecodedReader {
    override suspend fun currentSubjectFingerprint(): String =
        json(fetchSubject(), "subject").subject()

    override suspend fun semester(): CampusRunAuthorizedDecodedSemester {
        val envelope = json(fetchSemester(), "semester")
        val fields = envelope.opt("fields") as? JSONObject
            ?: throw IllegalArgumentException("bridge_semester_missing_fields")
        return CampusRunAuthorizedDecodedSemester(
            subjectFingerprint = envelope.subject(),
            fields = primitiveFields(fields),
            verifiedFieldSemantics = envelope.requiredBoolean("verifiedFieldSemantics")
        )
    }

    override suspend fun historyPage(cursor: String?): CampusRunAuthorizedDecodedPage {
        val envelope = json(fetchHistoryPage(cursor), "history")
        val records = envelope.opt("recordsPayload") as? JSONArray
            ?: throw IllegalArgumentException("bridge_history_missing_records")
        require(records.length() <= 100) { "bridge_history_page_too_large" }
        val cleaned = ArrayList<Map<String, Any?>>(records.length())
        for (i in 0 until records.length()) {
            val row = records.opt(i) as? JSONObject
                ?: throw IllegalArgumentException("bridge_history_invalid_record")
            cleaned.add(primitiveFields(row))
        }
        val paginationVerified = envelope.requiredBoolean("paginationVerified")
        val final = envelope.opt("isLastPage")
        // Python legitimately returns null when the pagination contract is
        // not known; the Android trusted query must fail closed, never guess.
        require(final is Boolean) { "bridge_unverified_page_termination" }
        val next = envelope.opt("nextCursor")
        val cursorText = when (next) {
            null, JSONObject.NULL -> null
            is String -> next
            else -> throw IllegalArgumentException("bridge_invalid_cursor")
        }
        require(final == (cursorText == null)) {
            "bridge_inconsistent_page_termination"
        }
        return CampusRunAuthorizedDecodedPage(
            subjectFingerprint = envelope.subject(),
            recordsPayload = cleaned,
            nextCursor = cursorText,
            isLastPage = final,
            verifiedFieldSemantics = envelope.requiredBoolean("verifiedFieldSemantics"),
            paginationVerified = paginationVerified
        )
    }

    private fun json(raw: String, kind: String): JSONObject {
        require(raw.length in 2..120_000) { "bridge_invalid_body_length" }
        val value = try { JSONObject(raw) }
        catch (_: Exception) { throw IllegalArgumentException("bridge_not_json_object") }
        require(value.opt("bridgeSchemaVersion") == 1 &&
            value.opt("bridgeKind") == kind) { "bridge_version_or_kind_mismatch" }
        return value
    }

    private fun JSONObject.subject(): String {
        val value = opt("subjectFingerprint") as? String
            ?: throw IllegalArgumentException("bridge_missing_subject")
        require(SUBJECT.matches(value)) { "bridge_invalid_subject_fingerprint" }
        return value
    }

    private fun JSONObject.requiredBoolean(name: String): Boolean =
        (opt(name) as? Boolean)
            ?: throw IllegalArgumentException("bridge_invalid_boolean_$name")

    private fun primitiveFields(json: JSONObject): Map<String, Any?> {
        require(json.length() in 1..45) { "bridge_invalid_field_count" }
        val output = linkedMapOf<String, Any?>()
        val names = json.keys()
        while (names.hasNext()) {
            val key = names.next()
            require(KEY.matches(key)) { "bridge_invalid_field_key" }
            val value = json.opt(key)
            require(value == null || value === JSONObject.NULL ||
                value is String || value is Boolean || value is Number) {
                "bridge_non_primitive_field"
            }
            if (value is String) {
                require(value.length <= 180 && value.none { it.isISOControl() }) {
                    "bridge_oversized_field_value"
                }
            }
            output[key] = if (value === JSONObject.NULL) null else value
        }
        return output
    }

    private companion object {
        val SUBJECT = Regex("^[a-f0-9]{64}$")
        val KEY = Regex("^[A-Za-z][A-Za-z0-9.]{0,79}$")
    }
}
