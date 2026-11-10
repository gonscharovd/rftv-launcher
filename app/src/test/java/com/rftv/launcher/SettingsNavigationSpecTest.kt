package com.rftv.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsNavigationSpecTest {
    @Test
    fun everySettingsSectionHasAnIcon() {
        assertEquals(8, SettingsNavigationSpec.SECTION_GLYPHS.size)
    }

    @Test
    fun missingFutureGlyphUsesSafeFallbackInsteadOfThrowing() {
        assertEquals("•", SettingsNavigationSpec.glyphFor(8))
    }

    @Test
    fun settingsGlyphsAreNotBlank() {
        assertFalse(SettingsNavigationSpec.SECTION_GLYPHS.any { it.isBlank() })
    }
}
