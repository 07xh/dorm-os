package io.github.x07xh.xiaoxu.ui.v2

import org.junit.Assert.assertEquals
import org.junit.Test

class UiV2WindowTest {
    @Test fun widthBoundaries() {
        assertEquals(UiV2Window.Compact, uiV2Window(599f, 800f))
        assertEquals(UiV2Window.Medium, uiV2Window(600f, 800f))
        assertEquals(UiV2Window.Medium, uiV2Window(839f, 800f))
        assertEquals(UiV2Window.Expanded, uiV2Window(840f, 800f))
    }

    @Test fun shortLandscapeKeepsOneColumn() {
        assertEquals(UiV2Window.Compact, uiV2Window(915f, 412f))
        assertEquals(UiV2Window.Compact, uiV2Window(1200f, 479f))
        assertEquals(UiV2Window.Expanded, uiV2Window(1200f, 480f))
    }

    @Test fun detailFoldsBeforePrimaryContentBecomesTooNarrow() {
        fun detail(w: Float, h: Float) = uiV2DetailWidth(w, h, 400f, 320f, 420f, 12f, 78f)
        assertEquals(null, detail(839f, 800f))
        assertEquals(null, detail(1200f, 412f))
        assertEquals(320f, detail(840f, 800f))
        assertEquals(408f, detail(1200f, 800f))
        assertEquals(420f, detail(1800f, 800f))
        assertEquals(null, uiV2DetailWidth(840f, 800f, 500f, 320f, 420f, 12f, 78f))
    }
}
