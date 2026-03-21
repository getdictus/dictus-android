package dev.pivisolutions.dictus.ime.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentMapTest {

    @Test
    fun `e has French accented variants`() {
        val accents = AccentMap.accentsFor('e')
        assertNotNull(accents)
        assertTrue(accents!!.contains("\u00E8")) // e grave
        assertTrue(accents.contains("\u00E9"))   // e acute
        assertTrue(accents.contains("\u00EA"))   // e circumflex
    }

    @Test
    fun `c has cedilla`() {
        val accents = AccentMap.accentsFor('c')
        assertNotNull(accents)
        assertTrue(accents!!.contains("\u00E7")) // c cedilla
    }

    @Test
    fun `uppercase E has accented variants`() {
        val accents = AccentMap.accentsFor('E')
        assertNotNull(accents)
        assertTrue(accents!!.contains("\u00C9")) // E acute
        assertTrue(accents.contains("\u00C8"))   // E grave
    }

    @Test
    fun `hasAccents returns true for e`() {
        assertTrue(AccentMap.hasAccents('e'))
    }

    @Test
    fun `hasAccents returns false for z`() {
        assertFalse(AccentMap.hasAccents('z'))
    }

    @Test
    fun `accentsFor returns null for non-accent character`() {
        assertNull(AccentMap.accentsFor('z'))
    }

    @Test
    fun `a has French accented variants`() {
        val accents = AccentMap.accentsFor('a')
        assertNotNull(accents)
        assertTrue(accents!!.contains("\u00E0")) // a grave
        assertTrue(accents.contains("\u00E2"))   // a circumflex
    }

    @Test
    fun `uppercase C has cedilla`() {
        val accents = AccentMap.accentsFor('C')
        assertNotNull(accents)
        assertTrue(accents!!.contains("\u00C7")) // C cedilla
    }
}
