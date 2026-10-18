package com.kriptoncod.app.data.power

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerSettingsTest {
    @Test fun detectsCommonOems() {
        assertEquals(Oem.XIAOMI, PowerSettings.detectOem("Xiaomi", "Redmi"))
        assertEquals(Oem.XIAOMI, PowerSettings.detectOem("Xiaomi", "POCO"))
        assertEquals(Oem.HUAWEI, PowerSettings.detectOem("HONOR", null))
        assertEquals(Oem.OPPO, PowerSettings.detectOem("realme", null))
        assertEquals(Oem.SAMSUNG, PowerSettings.detectOem("samsung", "samsung"))
        assertEquals(Oem.OTHER, PowerSettings.detectOem("Google", "google"))
        assertEquals(Oem.OTHER, PowerSettings.detectOem(null, null))
    }

    @Test fun oemsWithKnownScreensHaveComponents() {
        assertTrue(PowerSettings.oemComponents(Oem.XIAOMI).isNotEmpty())
        assertTrue(PowerSettings.oemComponents(Oem.OTHER).isEmpty())
    }
}
