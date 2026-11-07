package io.github.x07xh.xiaoxu.data

import org.json.JSONObject
import java.util.Base64

/**
 * Endpoint inventory extracted only from the owner's November 2026 HAR audit.
 * This registry is evidence, NOT a working session client. Do not use it to
 * replay captured credentials, upload results, or bypass platform protections.
 */
enum class CampusRunObservedEndpoint(
    val host: String,
    val method: String,
    val path: String
) {
    SEMESTER_SUMMARY("run.gxapp.iydsj.com", "POST",
        "/api/v55/runnings/recordssummary/semester"),
    RUN_RECORDS("run.gxapp.iydsj.com", "POST",
        "/api/v70230/runnings/records"),
    HOME_USER("run.gxapp.iydsj.com", "GET",
        "/api/v76/user/homePageBaseUserInfo"),
    PERSONAL_SEMESTER("run.gxapp.iydsj.com", "POST",
        "/api/v41/running/getPersonalSemesterInfo"),
    RUN_DATA_INDEX("run.gxapp.iydsj.com", "GET",
        "/api/v70100/run/data/index"),
    HISTORY_CONFIG("run.gxapp.iydsj.com", "GET",
        "/api/v70100/run/getHistoryConfig"),
    INDOOR_RECORDS("run.gxapp.iydsj.com", "POST",
        "/api/v74/runnings/indoor/findForPage"),
    RUN_CALENDAR("discovery.gxapp.iydsj.com", "POST",
        "/api/v70100/run/calendar/list")
}

data class CampusRunOpaqueEnvelopeShape(
    val responseVersion: Number,
    val rCharacters: Int,
    val sCharacters: Int,
    val rDecodedBytes: Int,
    val sDecodedBytes: Int,
    val rBlockAligned16: Boolean
)

/**
 * The HAR establishes an r/s/v top-level envelope, with r/s strings and v
 * numeric. Its meanings and encoded content are NOT established. Inspect
 * structure without printing, storing, decoding, or interpreting credentials
 * and encrypted payloads. This is never a successful business-data read.
 */
fun inspectCampusRunOpaqueEnvelope(body: String): CampusRunOpaqueEnvelopeShape {
    require(body.length in 2..2_000_000) { "invalid_response_size" }
    val envelope = try {
        JSONObject(body)
    } catch (_: Exception) {
        throw IllegalArgumentException("not_json_object")
    }
    val names = envelope.keys().asSequence().toSet()
    require(names == setOf("r", "s", "v")) { "unknown_envelope_shape" }
    val r = envelope.opt("r")
    val s = envelope.opt("s")
    val v = envelope.opt("v")
    require(r is String && s is String && v is Number) { "invalid_envelope_fields" }
    require(r.length <= 1_900_000 && s.length <= 1_900_000) {
        "oversized_envelope_fields"
    }
    val rBytes = try { Base64.getDecoder().decode(r) }
    catch (_: IllegalArgumentException) { throw IllegalArgumentException("invalid_r_base64") }
    val sBytes = try { Base64.getDecoder().decode(s) }
    catch (_: IllegalArgumentException) { throw IllegalArgumentException("invalid_s_base64") }
    require(rBytes.isNotEmpty() && sBytes.isNotEmpty()) { "empty_encoded_field" }
    return CampusRunOpaqueEnvelopeShape(
        responseVersion = v, rCharacters = r.length, sCharacters = s.length,
        rDecodedBytes = rBytes.size, sDecodedBytes = sBytes.size,
        rBlockAligned16 = rBytes.size % 16 == 0
    )
}
