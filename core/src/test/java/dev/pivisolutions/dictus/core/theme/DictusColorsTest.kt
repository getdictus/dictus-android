package dev.pivisolutions.dictus.core.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class DictusColorsTest {

    @Test
    fun `background color matches iOS token 0A1628`() {
        assertEquals(Color(0xFF0A1628), DictusColors.Background)
    }

    @Test
    fun `accent color matches iOS token 3D7EFF`() {
        assertEquals(Color(0xFF3D7EFF), DictusColors.Accent)
    }

    @Test
    fun `surface color matches iOS token 161C2C`() {
        assertEquals(Color(0xFF161C2C), DictusColors.Surface)
    }

    @Test
    fun `recording color matches iOS token EF4444`() {
        assertEquals(Color(0xFFEF4444), DictusColors.Recording)
    }

    @Test
    fun `success color matches iOS token 22C55E`() {
        assertEquals(Color(0xFF22C55E), DictusColors.Success)
    }

    @Test
    fun `all brand colors are defined`() {
        val colors = listOf(
            DictusColors.Background,
            DictusColors.Accent,
            DictusColors.AccentHighlight,
            DictusColors.Surface,
            DictusColors.Recording,
            DictusColors.SmartMode,
            DictusColors.Success,
            DictusColors.OnBackground,
            DictusColors.OnSurface,
            DictusColors.KeyBackground,
            DictusColors.KeyText,
        )
        assertEquals(11, colors.size)
    }
}
