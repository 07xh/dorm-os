package io.github.x07xh.xiaoxu.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL

class SecondClassCookieSessionTest {
    @Test fun onlyFixedHttpsReadEndpointsAreAllowed() {
        assertTrue(isAllowedSecondClassHttpRequest(
            URL("https://dekt.ncwu.edu.cn/sys/stu/score/list"), "POST"))
        assertTrue(isAllowedSecondClassHttpRequest(
            URL("https://dekt.ncwu.edu.cn/sys/stu/center/signupactivity/list"), "POST"))
        assertTrue(isAllowedSecondClassHttpRequest(
            URL("https://dekt.ncwu.edu.cn/sys/stu/activity"), "POST"))
        assertFalse(isAllowedSecondClassHttpRequest(
            URL("http://dekt.ncwu.edu.cn/sys/stu/score/list"), "POST"))
        assertFalse(isAllowedSecondClassHttpRequest(
            URL("https://example.com/sys/stu/score/list"), "POST"))
        assertFalse(isAllowedSecondClassHttpRequest(
            URL("https://dekt.ncwu.edu.cn/sys/stu/score/list/other"), "POST"))
        assertFalse(isAllowedSecondClassHttpRequest(
            URL("https://dekt.ncwu.edu.cn/sys/stu/score/list"), "GET"))
        assertFalse(isAllowedSecondClassHttpRequest(
            URL("https://dekt.ncwu.edu.cn:8443/sys/stu/score/list"), "POST"))
    }
}
