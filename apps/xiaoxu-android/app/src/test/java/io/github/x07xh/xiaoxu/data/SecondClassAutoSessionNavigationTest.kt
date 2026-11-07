package io.github.x07xh.xiaoxu.data

import org.junit.Assert.*
import org.junit.Test

class SecondClassAutoSessionNavigationTest {
    @Test fun onlyTheObservedOfficialGetRoutesMayReuseTheLocalSession() {
        val cases = mapOf(
            "https://dekt.ncwu.edu.cn/sys/stu/score" to SecondClassAutoSessionNavigation.Page.SCORE,
            "https://dekt.ncwu.edu.cn:443/sys/stu/score" to SecondClassAutoSessionNavigation.Page.SCORE,
            "https://dekt.ncwu.edu.cn/rump_frontend/login/?next=%2Fsys%2Fstu%2Fscore" to SecondClassAutoSessionNavigation.Page.GATEWAY,
            "https://authserver.ncwu.edu.cn/authserver/login?service=https%3A%2F%2Fsec.ncwu.edu.cn%2Frump_frontend%2FloginFromCas%2F" to SecondClassAutoSessionNavigation.Page.CAS,
            "https://sec.ncwu.edu.cn/rump_frontend/loginFromCas/?ticket=SYNTHETIC" to SecondClassAutoSessionNavigation.Page.CALLBACK,
            "https://dekt.ncwu.edu.cn/" to SecondClassAutoSessionNavigation.Page.LANDING
        )
        for ((url, expected) in cases) assertEquals(url, expected, SecondClassAutoSessionNavigation.page(url))
    }
    @Test fun foreignServiceNextPortCredentialsFragmentsAndPostsAreBlocked() {
        val cas = "https://authserver.ncwu.edu.cn/authserver/login?service="
        for (url in listOf(
            "http://dekt.ncwu.edu.cn/sys/stu/score",
            "https://dekt.ncwu.edu.cn:8443/sys/stu/score",
            "https://owner@dekt.ncwu.edu.cn/sys/stu/score",
            "https://dekt.ncwu.edu.cn.evil.invalid/sys/stu/score",
            "https://dekt.ncwu.edu.cn/sys/stu/score#fragment",
            "https://dekt.ncwu.edu.cn/sys/stu/score?next=/",
            "https://dekt.ncwu.edu.cn/rump_frontend/login/?next=https%3A%2F%2Fevil.invalid",
            "https://dekt.ncwu.edu.cn/rump_frontend/login/?next=%2F%2Fevil.invalid",
            "https://dekt.ncwu.edu.cn/rump_frontend/login/?next=%2F&next=%2F",
            cas + "https%3A%2F%2Fevil.invalid%2F",
            cas + "https%3A%2F%2Fsec.ncwu.edu.cn%2Frump_frontend%2FloginFromCas%2F&service=https%3A%2F%2Fevil.invalid%2F",
            "https://sec.ncwu.edu.cn/rump_frontend/loginFromCas/?ticket=%0A",
            "https://sec.ncwu.edu.cn/sys/stu/score",
            "https://dekt.ncwu.edu.cn/sys/stu/score/../other"
        )) assertNull(url, SecondClassAutoSessionNavigation.page(url))
        assertNull(SecondClassAutoSessionNavigation.page(SecondClassAutoSessionNavigation.CAS, "POST"))
        assertNull(SecondClassAutoSessionNavigation.page(null))
    }
}
