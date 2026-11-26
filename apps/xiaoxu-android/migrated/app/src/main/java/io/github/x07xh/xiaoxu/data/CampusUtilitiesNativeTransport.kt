package io.github.x07xh.xiaoxu.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Only a verified, school-supported native authorization flow may supply
 * this grant. No implementation exists yet. Never read another application's
 * cookies, WebView storage or accept pasted credentials as a substitute.
 * The provider must return null on owner change, revocation or expiry.
 */
internal fun interface CampusUtilitiesNativeGrant {
    suspend fun authorizationForCurrentOwner(): String?
}

/**
 * Native read-only transport candidate. Reuses CampusUtilitiesReadOnlyClient
 * and its response decoder. Not wired to production until the official
 * native authorization flow and real response contract are verified.
 */
internal class CampusUtilitiesNativeTransport(
    private val grant: CampusUtilitiesNativeGrant,
    private val connect: (URL) -> HttpsURLConnection = {
        it.openConnection() as HttpsURLConnection
    }
) : CampusUtilitiesTransport {
    override suspend fun post(
        path: String, json: JSONObject
    ): CampusUtilitiesRawResponse = withContext(Dispatchers.IO) {
        require(path in allowedPaths) { "Unsupported utilities route" }
        val payload = json.toString().toByteArray(Charsets.UTF_8)
        require(payload.size <= MAX_REQUEST_BYTES) { "Oversized request" }
        val authorization = grant.authorizationForCurrentOwner()
        if (authorization.isNullOrBlank() || authorization.length > 8192 ||
            authorization.any { it.code < 32 || it.code == 127 }
        ) {
            throw CampusUtilitiesException(CampusUtilitiesFailure.LOGIN_REQUIRED)
        }
        var connection: HttpsURLConnection? = null
        try {
            val http = connect(URL(ORIGIN + "/server" + path))
            connection = http
            http.requestMethod = "POST"
            http.instanceFollowRedirects = false
            http.useCaches = false
            http.connectTimeout = 15_000
            http.readTimeout = 15_000
            http.doOutput = true
            http.setFixedLengthStreamingMode(payload.size)
            http.setRequestProperty("Authorization", authorization)
            http.setRequestProperty("Content-Type", "application/json")
            http.setRequestProperty("Accept", "application/json")
            http.setRequestProperty("V8-Language", "zh-CN")
            http.outputStream.use { it.write(payload) }
            val status = http.responseCode
            // Never follow redirects or consume error payloads containing
            // account details. The read-only client classifies status codes.
            if (status != 200) {
                return@withContext CampusUtilitiesRawResponse(status, "")
            }
            if (http.contentLengthLong > MAX_RESPONSE_BYTES) {
                throw CampusUtilitiesException(CampusUtilitiesFailure.UNEXPECTED_RESPONSE)
            }
            val bytes = http.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_RESPONSE_BYTES) {
                        throw CampusUtilitiesException(CampusUtilitiesFailure.UNEXPECTED_RESPONSE)
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            CampusUtilitiesRawResponse(status, bytes.toString(Charsets.UTF_8))
        } catch (exception: CampusUtilitiesException) {
            throw exception
        } catch (_: IOException) {
            throw CampusUtilitiesException(CampusUtilitiesFailure.NETWORK_FAILURE)
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val ORIGIN = "https://mobile-ykt.ncwu.edu.cn"
        const val MAX_REQUEST_BYTES = 16 * 1024
        const val MAX_RESPONSE_BYTES = 256 * 1024
        val allowedPaths = setOf(
            "/utilities/config", "/utilities/account",
            "/utilities/usageDetails", "/utilities/usageDetailsV2"
        )
    }
}
