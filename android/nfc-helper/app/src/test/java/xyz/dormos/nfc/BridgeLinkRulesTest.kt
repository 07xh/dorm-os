package xyz.dormos.nfc

import org.junit.Assert.*
import org.junit.Test

class BridgeLinkRulesTest {
    @Test fun acceptsOnlyKnownSitesAndScenes() {
        assertEquals("study", BridgeLinkRules.sceneFromLink("https://dorm.0701000.xyz/assistant/?scene=study"))
        assertEquals("dev", BridgeLinkRules.sceneFromLink("https://dorm-os-dk9.pages.dev/assistant/?scene=dev"))
        assertEquals("away", BridgeLinkRules.sceneFromLink("dormos://assistant/open?scene=away"))
        assertEquals("none", BridgeLinkRules.sceneFromLink("dormos://assistant/open?scene=none"))
    }

    @Test fun rejectsUntrustedAndExecutableInput() {
        for (link in listOf(
            "http://dorm.0701000.xyz/assistant/?scene=study",
            "https://dorm.0701000.xyz.evil.example/assistant/?scene=study",
            "https://dorm.0701000.xyz@evil.example/assistant/?scene=study",
            "https://dorm.0701000.xyz/assistant/?scene=study&script=123",
            "https://dorm.0701000.xyz/assistant/?scene=study&scene=away",
            "https://dorm.0701000.xyz/assistant/?scene=study#fragment",
            "https://dorm.0701000.xyz/assistant/?scene=study;reboot",
            "https://dorm.0701000.xyz/assistant/?scene=unknown",
            "https://dorm.0701000.xyz:444/assistant/?scene=study",
            "dormos://assistant/open?scene=study&command=su",
            "dormos://other/open?scene=study",
            "https://dorm.0701000.xyz/other/?scene=study",
            "file:///assistant/?scene=study"
        )) assertNull(link, BridgeLinkRules.sceneFromLink(link))
        assertNull(BridgeLinkRules.sceneFromLink(null))
    }
}
