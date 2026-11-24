package io.github.x07xh.xiaoxu.data

import java.io.IOException
import java.time.YearMonth
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * Only the documented /utilities/config, /utilities/account and usageDetails(V2)
 * read routes are permitted. No payments, room mutations, endpoint probing,
 * shared credentials, auto-retry or background polling.
 */
data class CampusUtilityRoom(
    val bigArea: String,
    val area: String,
    val building: String,
    val unit: String,
    val level: String,
    val room: String
) {
    init {
        for (part in listOf(bigArea, area, building, unit, level, room)) {
            require(part.isNotBlank() && part.length <= 32 &&
                part.all { it.isLetterOrDigit() || it == '_' }) { "Invalid room coordinate" }
        }
    }

    fun putInto(json: JSONObject): JSONObject = json
        .put("bigArea", bigArea).put("area", area).put("building", building)
        .put("unit", unit).put("level", level).put("room", room)

    companion object {
        fun parse(raw: String): CampusUtilityRoom? {
            val parts = raw.trim().split("-")
            if (parts.size != 6) return null
            return runCatching {
                CampusUtilityRoom(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5])
            }.getOrNull()
        }
    }
}

enum class CampusUtilitiesFailure {
    LOGIN_REQUIRED, ACCESS_DENIED, SCHOOL_UNAVAILABLE, UNEXPECTED_RESPONSE, NETWORK_FAILURE
}

class CampusUtilitiesException(val failure: CampusUtilitiesFailure) :
    IOException(when (failure) {
        CampusUtilitiesFailure.LOGIN_REQUIRED -> "校园服务会话无效，需要本人重新授权"
        CampusUtilitiesFailure.ACCESS_DENIED -> "学校未授权查询该房间"
        CampusUtilitiesFailure.SCHOOL_UNAVAILABLE -> "校园服务暂时无法完成查询"
        CampusUtilitiesFailure.UNEXPECTED_RESPONSE -> "校方返回的数据格式尚未适配"
        CampusUtilitiesFailure.NETWORK_FAILURE -> "连接校园服务失败"
    })

data class CampusUtilitiesRawResponse(val statusCode: Int, val body: String)

fun interface CampusUtilitiesTransport {
    suspend fun post(path: String, json: JSONObject): CampusUtilitiesRawResponse
}

data class CampusUtilityMonthResult(
    val utility: CampusUtilityKind,
    val month: String,
    val accountNum: String?,
    val totalUsed: String?,
    val recordCount: Int?,
    val detailVersion: String,
    val fetchedAtMillis: Long
)

/**
 * Transport owns the authorized same-origin school session. Kotlin receives
 * only read responses, never an accessToken. The school remains responsible
 * for enforcing room permissions; a response alone is not ownership proof.
 */
class CampusUtilitiesReadOnlyClient(
    private val transport: CampusUtilitiesTransport,
    private val pace: suspend () -> Unit = { delay(2_000) },
    private val now: () -> Long = System::currentTimeMillis
) {
    suspend fun readMonth(
        utility: CampusUtilityKind,
        room: CampusUtilityRoom,
        month: YearMonth,
        bindingStillValid: suspend () -> Boolean = { true }
    ): CampusUtilityMonthResult {
        // The native coordinator supplies its current verified binding check.
        // Check before and after each read, including after inter-request delay.
        // The transport must independently keep credentials tied to that owner.
        suspend fun boundRequest(path: String, body: JSONObject): JSONObject {
            if (!bindingStillValid()) {
                throw CampusUtilitiesException(CampusUtilitiesFailure.LOGIN_REQUIRED)
            }
            val result = request(path, body)
            if (!bindingStillValid()) {
                throw CampusUtilitiesException(CampusUtilitiesFailure.LOGIN_REQUIRED)
            }
            return result
        }
        val type = utility.apiType
        val config = boundRequest(
            "/utilities/config",
            JSONObject().put("utilityType", type).put("appId", type)
        )
        val details = config.optJSONObject("detailConfig")
        if (details != null && !details.optBoolean("enabled", true)) {
            throw CampusUtilitiesException(CampusUtilitiesFailure.SCHOOL_UNAVAILABLE)
        }
        val v2 = details?.optString("version") == "v2"
        pace()
        val account = boundRequest(
            "/utilities/account",
            room.putInto(JSONObject().put("utilityType", type).put("appId", type))
        )
        pace()
        val usage = boundRequest(
            if (v2) "/utilities/usageDetailsV2" else "/utilities/usageDetails",
            room.putInto(
                JSONObject().put("utilityType", type)
                    .put("fromDate", month.atDay(1).toString())
                    .put("toDate", month.atEndOfMonth().toString())
                    .put("pageSize", 1_000).put("pageNo", 0).put("appId", type)
            )
        )
        val rows = if (v2) usage.optJSONObject("data") else usage.optJSONObject("details")
        // Unknown fields remain unknown. JSONObject.optInt can truncate a
        // fractional count or overflow, and toString() on an object can turn
        // a malformed payload into a seemingly valid displayed quantity.
        val count = rows?.opt("totalCount")?.let { value ->
            when (value) {
                JSONObject.NULL -> null
                is Int -> value.takeIf { it >= 0 }
                is Long -> value.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()
                is String -> value.takeIf { it.matches(Regex("^[0-9]{1,10}$")) }
                    ?.toIntOrNull()
                else -> null
            }
        }
        val total = usage.opt("totalUsed")?.let { value ->
            when (value) {
                is String, is Number -> value.toString().takeIf {
                    it.length in 1..64 && it.matches(Regex("^[0-9]+(\\.[0-9]+)?$"))
                }
                else -> null
            }
        }
        // The V2 dynamic sumList must not be guessed to mean electric balance
        // or a particular usage unit without a verified real response.
        if (count == null && total == null) {
            throw CampusUtilitiesException(CampusUtilitiesFailure.UNEXPECTED_RESPONSE)
        }
        return CampusUtilityMonthResult(
            utility = utility,
            month = month.toString(),
            accountNum = account.optString("accountNum").takeIf { it.isNotBlank() },
            totalUsed = total,
            recordCount = count,
            detailVersion = if (v2) "v2" else "v1",
            fetchedAtMillis = now()
        )
    }

    private suspend fun request(path: String, body: JSONObject): JSONObject {
        val result = transport.post(path, body)
        when (result.statusCode) {
            0 -> throw CampusUtilitiesException(CampusUtilitiesFailure.NETWORK_FAILURE)
            401 -> throw CampusUtilitiesException(CampusUtilitiesFailure.LOGIN_REQUIRED)
            403 -> throw CampusUtilitiesException(CampusUtilitiesFailure.ACCESS_DENIED)
            200 -> Unit
            else -> throw CampusUtilitiesException(CampusUtilitiesFailure.SCHOOL_UNAVAILABLE)
        }
        try {
            val envelope = JSONObject(result.body)
            val payload = if (!envelope.optBoolean("success", false) &&
                envelope.opt("data") is String
            ) {
                JSONObject(CampusUtilitiesSm4.decryptBase64(envelope.getString("data")))
            } else envelope
            if (payload.has("success") && !payload.optBoolean("success")) {
                throw CampusUtilitiesException(CampusUtilitiesFailure.SCHOOL_UNAVAILABLE)
            }
            return payload.optJSONObject("resultData")
                ?: if (payload.has("resultData")) {
                    throw CampusUtilitiesException(CampusUtilitiesFailure.UNEXPECTED_RESPONSE)
                } else payload
        } catch (exception: CampusUtilitiesException) {
            throw exception
        } catch (_: Exception) {
            throw CampusUtilitiesException(CampusUtilitiesFailure.UNEXPECTED_RESPONSE)
        }
    }
}
