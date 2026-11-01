package xyz.dormos.nfc

import org.junit.Assert.*
import org.junit.Test

class NativeSceneRulesTest {
    @Test fun acceptsExactSceneUri() {
        assertEquals("wechat-group-01",
            NativeSceneRules.sceneId("tapflow://scene/wechat-group-01"))
        assertEquals("native-a1", NativeSceneRules.sceneId("tapflow://scene/native-a1"))
    }
    @Test fun rejectsAmbiguousOrForgedUri() {
        listOf(
            "https://scene/wechat-group-01",
            "tapflow://scene/wechat-group-01?x=1",
            "tapflow://scene/wechat-group-01#anything",
            "tapflow://scene/wechat-group-01/other",
            "tapflow://evil/wechat-group-01",
            "tapflow://scene/%77echat-group-01",
            "tapflow://scene/../wechat-group-01",
            "tapflow://scene/",
            "tapflow://scene/a b"
        ).forEach { assertNull("Must reject: $it", NativeSceneRules.sceneId(it)) }
    }
    @Test fun allowsClipboardOpenAppThenManualConfirm() {
        val scene = NativeScene("wechat-group-01", "查寝", listOf(
            NativeStep(NativeKind.COPY, "8624均在寝"),
            NativeStep(NativeKind.APP, "com.tencent.mm"),
            NativeStep(NativeKind.MANUAL, "在 26电气日常查寝中手动确认发送")
        ))
        assertTrue(NativeSceneRules.valid(scene))
    }
    @Test fun preventsUnsafeSequencesAndShellPayloads() {
        val base = NativeScene("safe-one", "测试", listOf(NativeStep(NativeKind.COPY, "hello")))
        assertTrue(NativeSceneRules.valid(base))
        assertFalse(NativeSceneRules.valid(base.copy(actions=listOf(
            NativeStep(NativeKind.APP, "com.tencent.mm"),
            NativeStep(NativeKind.SH, "00000000-0000-0000-0000-000000000000")
        ))))
        assertFalse(NativeSceneRules.valid(base.copy(actions=listOf(
            NativeStep(NativeKind.SH, "rm -rf /")
        ))))
        assertFalse(NativeSceneRules.valid(base.copy(actions=listOf(
            NativeStep(NativeKind.URL, "javascript:alert(1)")
        ))))
    }
}
