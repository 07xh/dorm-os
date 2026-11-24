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
}
