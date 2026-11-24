package io.github.x07xh.xiaoxu.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CampusUtilitiesNativeTransportTest {
    private class FakeConnection(url: URL, private val code: Int = 200,
        private val body: ByteArray = "{}".toByteArray()) : HttpsURLConnection(url) {
        val posted = ByteArrayOutputStream()
        var closed = false
        override fun connect() {}
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getCipherSuite() = "test"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
        override fun getOutputStream() = posted
        override fun getResponseCode() = code
        override fun getInputStream() = ByteArrayInputStream(body)
        override fun getContentLengthLong() = -1L
    }

    @Test fun absentGrantDoesNotOpenNetwork() = runBlocking {
        val transport = CampusUtilitiesNativeTransport(CampusUtilitiesNativeGrant { null }) {
            throw AssertionError("No network without authorization")
        }
        try {
            transport.post("/utilities/config", JSONObject())
            fail("Expected LOGIN_REQUIRED")
        } catch (e: CampusUtilitiesException) {
            assertEquals(CampusUtilitiesFailure.LOGIN_REQUIRED, e.failure)
        }
    }

    @Test fun unknownRouteRejectedBeforeAuthorization() = runBlocking {
        val transport = CampusUtilitiesNativeTransport(CampusUtilitiesNativeGrant {
            throw AssertionError("No authorization for an unknown route")
        })
        try {
            transport.post("/utilities/unrecognized", JSONObject())
            fail("Expected route rejection")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun documentedReadUsesFixedOriginAndNoRedirect() = runBlocking {
        lateinit var conn: FakeConnection
        val transport = CampusUtilitiesNativeTransport(CampusUtilitiesNativeGrant { "test-grant" }) {
            assertEquals("https://mobile-ykt.ncwu.edu.cn/server/utilities/config", it.toString())
            FakeConnection(it).also { c -> conn = c }
        }
        val body = JSONObject().put("utilityType", "electric")
        assertEquals(200, transport.post("/utilities/config", body).statusCode)
        assertEquals("POST", conn.requestMethod)
        assertFalse(conn.instanceFollowRedirects)
        assertEquals("test-grant", conn.getRequestProperty("Authorization"))
        assertEquals(body.toString(), conn.posted.toString("UTF-8"))
        assertTrue(conn.closed)
    }

    @Test fun redirectIsReturnedWithoutAnotherRequest() = runBlocking {
        var calls = 0
        val transport = CampusUtilitiesNativeTransport(CampusUtilitiesNativeGrant { "test-grant" }) {
            calls++
            FakeConnection(it, 302)
        }
        val result = transport.post("/utilities/account", JSONObject())
        assertEquals(302, result.statusCode)
        assertEquals("", result.body)
        assertEquals(1, calls)
    }

    @Test fun oversizedStreamIsRejected() = runBlocking {
        val transport = CampusUtilitiesNativeTransport(CampusUtilitiesNativeGrant { "test-grant" }) {
            FakeConnection(it, body = ByteArray(256 * 1024 + 1))
        }
        try {
            transport.post("/utilities/config", JSONObject())
            fail("Expected bounded response rejection")
        } catch (e: CampusUtilitiesException) {
            assertEquals(CampusUtilitiesFailure.UNEXPECTED_RESPONSE, e.failure)
        }
    }
}
