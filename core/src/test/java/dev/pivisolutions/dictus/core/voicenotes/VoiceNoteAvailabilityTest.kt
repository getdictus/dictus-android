package dev.pivisolutions.dictus.core.voicenotes

import dev.pivisolutions.dictus.core.voicenotes.VoiceNoteAvailability.ShareDecision
import org.junit.Assert.assertEquals
import org.junit.Test

/** iOS `VoiceNoteAvailability.shareDecision` (#204). */
class VoiceNoteAvailabilityTest {

    @Test
    fun `accepted with Pro and the switch on, refused otherwise with the right sentence`() {
        assertEquals(ShareDecision.ACCEPT, VoiceNoteAvailability.shareDecision(isEntitled = true, hasPro = true, paywallVisible = false))
        assertEquals(ShareDecision.REFUSE_SWITCHED_OFF, VoiceNoteAvailability.shareDecision(isEntitled = false, hasPro = true, paywallVisible = false))
        assertEquals(ShareDecision.REFUSE_UNAVAILABLE, VoiceNoteAvailability.shareDecision(isEntitled = false, hasPro = false, paywallVisible = false))
        assertEquals(ShareDecision.REFUSE_NEEDS_PRO, VoiceNoteAvailability.shareDecision(isEntitled = false, hasPro = false, paywallVisible = true))
    }
}
