package dev.pivisolutions.dictus.core.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for ThemeMode enum and theme-related color tokens.
 *
 * These tests verify the ThemeMode enum values exist and that the light/dark
 * color palette tokens have the correct hex values matching iOS DictusColors.swift.
 *
 * NOTE: DictusTheme composable itself (which uses isSystemInDarkTheme) cannot
 * be tested in a pure JVM unit test without a Compose test rule. These tests
 * cover the data layer: enum values and color constants.
 */
class DictusThemeTest {

    @Test
    fun `ThemeMode DARK enum value exists`() {
        assertEquals(ThemeMode.DARK, ThemeMode.valueOf("DARK"))
    }

    @Test
    fun `ThemeMode LIGHT enum value exists`() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.valueOf("LIGHT"))
    }

    @Test
    fun `ThemeMode AUTO enum value exists`() {
        assertEquals(ThemeMode.AUTO, ThemeMode.valueOf("AUTO"))
    }

    @Test
    fun `ThemeMode has exactly 3 values`() {
        assertEquals(3, ThemeMode.entries.size)
    }

    @Test
    fun `dark background color matches iOS token 0A1628`() {
        assertEquals(Color(0xFF0A1628), DictusColors.Background)
    }

    @Test
    fun `light background color matches iOS token F2F2F7`() {
        assertEquals(Color(0xFFF2F2F7), DictusColors.LightBackground)
    }

    @Test
    fun `light surface color matches iOS token FFFFFF`() {
        assertEquals(Color(0xFFFFFFFF), DictusColors.LightSurface)
    }

    @Test
    fun `light and dark backgrounds are different`() {
        assert(DictusColors.Background != DictusColors.LightBackground) {
            "Dark and light backgrounds should have different colors"
        }
    }
}
