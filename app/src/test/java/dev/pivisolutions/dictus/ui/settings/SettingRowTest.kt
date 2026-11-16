package dev.pivisolutions.dictus.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** How a Settings row shares its width between the label and the trailing value (#350, #359). */
class SettingRowTest {

    @Test
    fun `both fit - the value keeps its natural width`() {
        assertEquals(80, trailingWidth(available = 300, label = 150, labelWord = 60, trailing = 80, trailingWord = 40))
    }

    @Test
    fun `a long value next to a short label leaves the label its width`() {
        // « Langue » next to « Suivre la langue du clavier ».
        assertEquals(230, trailingWidth(available = 300, label = 70, labelWord = 70, trailing = 400, trailingWord = 80))
    }

    @Test
    fun `when neither fits the value gets half the row`() {
        assertEquals(150, trailingWidth(available = 300, label = 250, labelWord = 100, trailing = 280, trailingWord = 90))
    }

    @Test
    fun `the value is never narrower than its longest word`() {
        // « Langue de l'interface » next to « Automatique ⇕ ».
        assertEquals(170, trailingWidth(available = 300, label = 250, labelWord = 110, trailing = 170, trailingWord = 170))
    }

    @Test
    fun `the label always keeps its longest word`() {
        // Both longest words cannot sit side by side: the value breaks, not the label.
        assertEquals(180, trailingWidth(available = 300, label = 250, labelWord = 120, trailing = 500, trailingWord = 400))
    }

    @Test
    fun `a label word wider than the row leaves the value no width`() {
        assertEquals(0, trailingWidth(available = 300, label = 350, labelWord = 320, trailing = 80, trailingWord = 80))
    }

    @Test
    fun `the label stays beside the value while both longest words fit`() {
        // « Langue » next to « Suivre la langue du clavier » at font scale 1.3: the value wraps beside it.
        assertFalse(stacksLabel(available = 300, labelWord = 70, trailingWord = 80))
        assertFalse(stacksLabel(available = 300, labelWord = 150, trailingWord = 150))
    }

    @Test
    fun `the label goes above the value when both longest words do not fit side by side`() {
        // « Langue de l'interface » next to « Automatique ⇕ » at font scale 2.0.
        assertTrue(stacksLabel(available = 300, labelWord = 140, trailingWord = 170))
        // A label word wider than the row, « haptique » at font scale 5.0.
        assertTrue(stacksLabel(available = 300, labelWord = 320, trailingWord = 51))
    }

    @Test
    fun `a row without a value never stacks`() {
        assertFalse(stacksLabel(available = 300, labelWord = 320, trailingWord = 0))
    }
}
