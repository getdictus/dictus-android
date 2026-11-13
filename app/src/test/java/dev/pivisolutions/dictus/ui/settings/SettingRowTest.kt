package dev.pivisolutions.dictus.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** How a Settings row shares its width between the label and the trailing value (#350). */
class SettingRowTest {

    @Test
    fun `both fit - the value keeps its natural width`() {
        assertEquals(80, trailingWidth(available = 300, label = 150, trailing = 80, trailingWord = 40))
    }

    @Test
    fun `a long value next to a short label leaves the label its width`() {
        // « Langue » next to « Suivre la langue du clavier ».
        assertEquals(230, trailingWidth(available = 300, label = 70, trailing = 400, trailingWord = 80))
    }

    @Test
    fun `when neither fits the value gets half the row`() {
        assertEquals(150, trailingWidth(available = 300, label = 250, trailing = 280, trailingWord = 90))
    }

    @Test
    fun `the value is never narrower than its longest word`() {
        // « Langue de l'interface » next to « Automatique ⇕ ».
        assertEquals(170, trailingWidth(available = 300, label = 250, trailing = 170, trailingWord = 170))
    }

    @Test
    fun `the value is never wider than the row`() {
        assertEquals(300, trailingWidth(available = 300, label = 250, trailing = 500, trailingWord = 400))
    }
}
