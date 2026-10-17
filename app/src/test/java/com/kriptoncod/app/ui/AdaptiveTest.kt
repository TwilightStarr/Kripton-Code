package com.kriptoncod.app.ui

import com.kriptoncod.app.ui.adaptive.WindowClass
import com.kriptoncod.app.ui.adaptive.windowClassFor
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveTest {
    @Test fun windowClassBoundaries() {
        assertEquals(WindowClass.Compact, windowClassFor(360))
        assertEquals(WindowClass.Compact, windowClassFor(599))
        assertEquals(WindowClass.Medium, windowClassFor(600))
        assertEquals(WindowClass.Medium, windowClassFor(839))
        assertEquals(WindowClass.Expanded, windowClassFor(840))
        assertEquals(WindowClass.Expanded, windowClassFor(1280))
    }
}
