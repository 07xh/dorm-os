package io.github.x07xh.xiaoxu.data

import org.junit.Assert.*
import org.junit.Test

class ChaoxingWebNavigationTest {
    @Test fun officialLoginAndBusinessSubdomainsRemainNavigable() {
        for (url in listOf(
            "https://passport2-api.chaoxing.com/login",
            "https://sso.chaoxing.com/login",
            "https://notice.chaoxing.com/pc/notice/myNotice",
            "https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/stu?enc=example"
        )) assertEquals(url, ChaoxingWebNavigation.target(url))
    }
    @Test fun legacyOfficialLinksAreUpgradedBeforeLoading() {
        assertEquals("https://i.mooc.chaoxing.com/space/index?a=1&b=2",
            ChaoxingWebNavigation.target("http://i.mooc.chaoxing.com/space/index?a=1&b=2"))
    }
    @Test fun externalAndMalformedTargetsRemainBlocked() {
        for (url in listOf(
            "https://chaoxing.com.attacker.example/", "https://evilchaoxing.com/",
            "https://user@i.chaoxing.com/", "https://i.chaoxing.com:444/",
            "javascript:alert(1)", "intent://i.chaoxing.com/", "file:///tmp/login",
            "http://authserver.ncwu.edu.cn/authserver/login"
        )) assertNull(url, ChaoxingWebNavigation.target(url))
    }
}
