package dev.pivisolutions.dictus.service

import dev.pivisolutions.dictus.service.DictationSessionOwnership.Owner.COMPANION
import dev.pivisolutions.dictus.service.DictationSessionOwnership.Owner.LEGACY
import org.junit.Assert.*
import org.junit.Test

class DictationSessionOwnershipTest {
    private val gate = DictationSessionOwnership()

    @Test fun `companion lease blocks legacy even between recordings`() {
        assertTrue(gate.acquireCompanion())
        assertFalse(gate.permits(LEGACY))
        assertNull(gate.requestStart(LEGACY))
        val token = gate.requestStart(COMPANION)!!
        assertTrue(gate.acceptStart(token))
        assertTrue(gate.finish(token))
        assertNull(gate.requestStart(LEGACY))
        assertNotNull(gate.requestStart(COMPANION))
    }

    @Test fun `pending legacy start blocks acquisition and duplicate starts`() {
        val token = gate.requestStart(LEGACY)!!
        assertFalse(gate.acquireCompanion())
        assertNull(gate.requestStart(LEGACY))
        assertTrue(gate.acceptStart(token))
        assertFalse(gate.acceptStart(token))
        assertNull(gate.requestStart(LEGACY))
    }

    @Test fun `cancelled intent cannot start or finish a newer session`() {
        val stale = gate.requestStart(LEGACY)!!
        gate.finish(stale)
        val current = gate.requestStart(LEGACY)!!
        assertFalse(gate.acceptStart(stale))
        assertFalse(gate.finish(stale))
        assertEquals(current, gate.token)
        assertTrue(gate.acceptStart(current))
    }

    @Test fun `transcription is exclusive and confirm cannot repeat`() {
        val token = gate.requestStart(LEGACY)!!
        assertNull(gate.beginTranscription(LEGACY))
        gate.acceptStart(token)
        assertEquals(token, gate.beginTranscription(LEGACY))
        assertNull(gate.beginTranscription(LEGACY))
        assertNull(gate.requestStart(LEGACY))
        assertFalse(gate.acquireCompanion())
    }

    @Test fun `release waits for transcription cleanup before legacy can start`() {
        gate.acquireCompanion()
        val token = gate.requestStart(COMPANION)!!
        gate.acceptStart(token)
        gate.beginTranscription(COMPANION)
        gate.releaseCompanion()
        assertTrue(gate.companionOwned)
        assertFalse(gate.acquireCompanion())
        assertNull(gate.requestStart(LEGACY))
        assertNull(gate.requestStart(COMPANION))
        gate.finish(token)
        assertFalse(gate.companionOwned)
        assertNotNull(gate.requestStart(LEGACY))
    }

    @Test fun `idle release is immediate and does not disturb legacy work`() {
        gate.acquireCompanion()
        gate.releaseCompanion()
        val token = gate.requestStart(LEGACY)!!
        gate.releaseCompanion()
        assertTrue(gate.acceptStart(token))
    }

    @Test fun `failed companion start permits retry without giving up lease`() {
        gate.acquireCompanion()
        val token = gate.requestStart(COMPANION)!!
        gate.acceptStart(token)
        gate.finish(token)
        assertTrue(gate.companionOwned)
        assertNull(gate.requestStart(LEGACY))
        assertNotNull(gate.requestStart(COMPANION))
    }

    @Test fun `destroy invalidates all delayed work and future commands`() {
        gate.acquireCompanion()
        val token = gate.requestStart(COMPANION)!!
        gate.destroy()
        assertFalse(gate.acceptStart(token))
        assertFalse(gate.finish(token))
        assertFalse(gate.acquireCompanion())
        assertNull(gate.requestStart(LEGACY))
        assertNull(gate.requestStart(COMPANION))
    }
}
