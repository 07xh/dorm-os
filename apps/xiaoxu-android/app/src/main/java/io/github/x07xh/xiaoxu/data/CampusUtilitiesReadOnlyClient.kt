package io.github.x07xh.xiaoxu.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
    suspend fun post(path: String, accessToken: String, json: JSONObject): CampusUtilitiesRawResponse
}

class CampusUtilitiesOfficialTransport : CampusUtilitiesTransport {
    override suspend fun post(path: String, accessToken: String, json: JSONObject) =
        withContext(Dispatchers.IO) {
            if (path !in ALLOWED) throw CampusUtilitiesException(CampusUtilitiesFailure.ACCESS_DENIED)
            if (accessToken.isBlank() || '\r' in accessToken || '\n' in accessToken) {
                throw CampusUtilitiesException(CampusUtilitiesFailure.LOGIN_REQUIRED)
            }
            try {
                val connection = URL("https://mobile-ykt.ncwu.edu.cn/server$path")
                    .openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 15_000
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("Accept", "application/json")
                    connection.setRequestProperty("Authorization", accessToken)
                    connection.setRequestProperty("V8-Language", "zh-CN")
                    connection.outputStream.use {
                        it.write(json.toString().toByteArray(Charsets.UTF_8))
                    }
                    val status = connection.responseCode
                    val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                    val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                    CampusUtilitiesRawResponse(status, body)
                } finally {
                    connection.disconnect()
                }
            } catch (exception: CampusUtilitiesException) {
                throw exception
            } catch (_: IOException) {
                throw CampusUtilitiesException(CampusUtilitiesFailure.NETWORK_FAILURE)
            }
        }

    companion object {
        private val ALLOWED = setOf(
            "/utilities/config", "/utilities/account",
            "/utilities/usageDetails", "/utilities/usageDetailsV2"
        )
    }
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
 * The access token is passed per call and never persisted here. The school's
 * server is still responsible for authorizing the student's room. The result
 * indicates successful upstream reading, NOT proof of account/room ownership.
 */
class CampusUtilitiesReadOnlyClient(
    private val transport: CampusUtilitiesTransport = CampusUtilitiesOfficialTransport(),
    private val pace: suspend () -> Unit = { delay(2_000) },
    private val now: () -> Long = System::currentTimeMillis
) {
    suspend fun readMonth(
        accessToken: String,
        utility: CampusUtilityKind,
        room: CampusUtilityRoom,
        month: YearMonth
    ): CampusUtilityMonthResult {
        if (accessToken.isBlank()) throw CampusUtilitiesException(CampusUtilitiesFailure.LOGIN_REQUIRED)
        val type = utility.apiType
        val config = request(
            "/utilities/config", accessToken,
            JSONObject().put("utilityType", type).put("appId", type)
        )
        val details = config.optJSONObject("detailConfig")
        if (details != null && !details.optBoolean("enabled", true)) {
            throw CampusUtilitiesException(CampusUtilitiesFailure.SCHOOL_UNAVAILABLE)
        }
        val v2 = details?.optString("version") == "v2"
        pace()
        val account = request(
            "/utilities/account", accessToken,
            room.putInto(JSONObject().put("utilityType", type).put("appId", type))
        )
        pace()
        val usage = request(
            if (v2) "/utilities/usageDetailsV2" else "/utilities/usageDetails",
            accessToken,
            room.putInto(
                JSONObject().put("utilityType", type)
                    .put("fromDate", month.atDay(1).toString())
                    .put("toDate", month.atEndOfMonth().toString())
                    .put("pageSize", 1_000).put("pageNo", 0).put("appId", type)
            )
        )
        val rows = if (v2) usage.optJSONObject("data") else usage.optJSONObject("details")
        val count = rows?.optInt("totalCount", -1)?.takeIf { it >= 0 }
        val total = usage.opt("totalUsed")?.takeUnless { it == JSONObject.NULL }?.toString()
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

    private suspend fun request(path: String, token: String, body: JSONObject): JSONObject {
        val result = transport.post(path, token, body)
        when (result.statusCode) {
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
