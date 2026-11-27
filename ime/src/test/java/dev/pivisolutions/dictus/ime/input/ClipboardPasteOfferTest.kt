package dev.pivisolutions.dictus.ime.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the toolbar offers Paste (#420), ported from iOS `ClipboardPasteOfferTests` (#634). */
class ClipboardPasteOfferTest {

    private val store = object : ClipboardPasteOffer.Store {
        val values = mutableMapOf<String, Long>()
        override fun get(key: String): Long? = values[key]
        override fun put(key: String, value: Long) {
            values[key] = value
        }
    }
    private val start = 1_000_000_000L
    private val freshness = ClipboardPasteOffer.FRESHNESS_MS

    private fun text(marker: Long) = ClipboardSnapshot(marker, hasText = true)

    private fun offered(
        snapshot: ClipboardSnapshot?,
        enabled: Boolean = true,
        at: Long = start,
        offer: ClipboardPasteOffer = ClipboardPasteOffer(store),
    ): Boolean = offer.isOffered(enabled, { snapshot }, at)

    @Test
    fun `a new copy is offered`() {
        assertTrue(offered(text(7)))
    }

    /** A marker of 0 must not read as "already seen". */
    @Test
    fun `a marker of zero is still a new copy`() {
        assertTrue(offered(text(0)))
    }

    /** iOS: without Full Access the pasteboard is never read. Here, the setting off. */
    @Test
    fun `with the setting off the clipboard is never read`() {
        val shown = ClipboardPasteOffer(store).isOffered(
            enabled = false,
            clipboard = { throw AssertionError("clipboard read with the setting off") },
            now = start,
        )
        assertFalse(shown)
        assertTrue(store.values.isEmpty())
    }

    @Test
    fun `an empty clipboard is not offered`() {
        assertFalse(offered(null))
    }

    @Test
    fun `an image only clipboard is not offered but is recorded as seen`() {
        val offer = ClipboardPasteOffer(store)
        assertFalse(offered(ClipboardSnapshot(7, hasText = false), offer = offer))
        assertEquals(start + freshness, offer.expiry())
    }

    @Test
    fun `the offer holds inside the freshness window`() {
        assertTrue(offered(text(7)))
        assertTrue(offered(text(7), at = start + freshness - 1))
    }

    @Test
    fun `the offer expires after the freshness window`() {
        assertTrue(offered(text(7)))
        assertFalse(offered(text(7), at = start + freshness))
    }

    /** The window starts when the keyboard first notices the copy, so a newer copy restarts it. */
    @Test
    fun `a newer copy restarts the window`() {
        assertTrue(offered(text(7)))
        val later = start + freshness + 60_000
        assertFalse(offered(text(7), at = later))
        assertTrue(offered(text(8), at = later))
    }

    @Test
    fun `a pasted copy is not offered again`() {
        val offer = ClipboardPasteOffer(store)
        assertTrue(offered(text(7), offer = offer))
        offer.notePasted(7)
        assertFalse(offered(text(7), offer = offer))
        assertTrue(offered(text(8), offer = offer))
    }

    @Test
    fun `expiry is the first sighting plus the window`() {
        val offer = ClipboardPasteOffer(store)
        assertNull(offer.expiry())
        assertTrue(offered(text(7), offer = offer))
        assertEquals(start + freshness, offer.expiry())
        assertTrue(offered(text(7), at = start + 60_000, offer = offer))
        assertEquals("a second look does not move the window", start + freshness, offer.expiry())
    }

    /** The IME process can restart between apps; a fresh instance must remember both facts. */
    @Test
    fun `state survives a new instance`() {
        assertTrue(offered(text(7)))
        assertFalse(offered(text(7), at = start + freshness + 1, offer = ClipboardPasteOffer(store)))

        ClipboardPasteOffer(store).notePasted(9)
        assertFalse(offered(text(9), offer = ClipboardPasteOffer(store)))
    }

    /** Only markers and a time are ever stored, never anything drawn from the clip's text. */
    @Test
    fun `only two markers and a timestamp are stored`() {
        val offer = ClipboardPasteOffer(store)
        offered(text(7), offer = offer)
        offer.notePasted(7)
        assertEquals(setOf("seen_marker", "seen_at", "pasted_marker"), store.values.keys)
    }
}
