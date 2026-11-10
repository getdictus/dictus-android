package dev.pivisolutions.dictus.recording

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Home's card holds the last dictation in memory, with no expiry, as iOS does (#245). */
@OptIn(ExperimentalCoroutinesApi::class)
class LastTranscriptionStoreTest {

    @Test
    fun `a new store, as in a relaunched app, is empty`() {
        LastTranscriptionStore().save("private canary")

        assertNull(LastTranscriptionStore().text.value)
    }

    @Test
    fun `a saved text stays well past the old five minutes`() = runTest {
        val store = LastTranscriptionStore()
        store.save("private canary")

        advanceTimeBy(60 * 60 * 1_000L)

        assertEquals("private canary", store.text.value)
    }

    @Test
    fun `a newer text replaces the older one, and clear empties the card`() {
        val store = LastTranscriptionStore()
        store.save("older")
        store.save("newer")
        assertEquals("newer", store.text.value)

        store.clear()

        assertNull(store.text.value)
    }
}
