package dev.pivisolutions.dictus.ime.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DictationSuccessFlashTest {

    @Test fun `nothing is pending before a dictation`() {
        assertNull(DictationSuccessFlash().pending.value)
    }

    @Test fun `an armed flash is consumed once`() {
        val flash = DictationSuccessFlash()
        flash.arm()
        val token = flash.pending.value!!

        flash.consume(token)

        assertNull(flash.pending.value)
    }

    @Test fun `consuming an old token keeps the newer flash pending`() {
        val flash = DictationSuccessFlash()
        flash.arm()
        val first = flash.pending.value!!
        flash.arm()
        val second = flash.pending.value!!

        flash.consume(first)

        assertNotEquals(first, second)
        assertEquals(second, flash.pending.value)
    }

    @Test fun `clear drops a flash not yet shown`() {
        val flash = DictationSuccessFlash()
        flash.arm()

        flash.clear()

        assertNull(flash.pending.value)
    }
}
