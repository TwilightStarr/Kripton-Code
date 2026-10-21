package com.kriptoncod.app.ui.ais

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceInputTest {
    @Test fun cleanKeepsDigitsAndOneDecimalSeparator() {
        assertEquals("12.5", AccountEditorViewModel.cleanPrice("12,5"))
        assertEquals("1.25", AccountEditorViewModel.cleanPrice("1.2.5"))
        assertEquals("30", AccountEditorViewModel.cleanPrice("\$30 abc"))
        assertEquals("", AccountEditorViewModel.cleanPrice("abc"))
    }

    @Test fun parseRejectsEmptyZeroAndGarbage() {
        assertEquals(12.5, AccountEditorViewModel.parsePrice("12.5")!!, 0.0)
        assertNull(AccountEditorViewModel.parsePrice(""))
        assertNull(AccountEditorViewModel.parsePrice("0"))
        assertNull(AccountEditorViewModel.parsePrice("."))
    }
}
