package dev.pivisolutions.dictus.core.stats

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Words and audio seconds per local day (#201), as iOS DictationTally counts them. Robolectric
 * for android.icu, the word boundaries the phone uses.
 */
@RunWith(RobolectricTestRunner::class)
class DictationTallyTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val paris = ZoneId.of("Europe/Paris")
    private var now: ZonedDateTime = ZonedDateTime.of(2026, 10, 31, 12, 0, 0, 0, paris)
    private val dataStore by lazy {
        PreferenceDataStoreFactory.create(produceFile = { folder.newFile("tally.preferences_pb") })
    }
    private val tally by lazy { DictationTally(dataStore, now = { now }) }

    private val oct31 = LocalDate.of(2026, 10, 31)
    private val nov1 = LocalDate.of(2026, 11, 1)

    // --- Cumul ---

    @Test
    fun `dictations of the same day add up`() = runTest {
        assertEquals(DictationTally.Totals(words = 3, audioSeconds = 2), tally.record("Bonjour à tous.", 2))
        assertEquals(DictationTally.Totals(words = 5, audioSeconds = 5), tally.record("Deux mots", 3))

        assertEquals(DictationTally.Totals(words = 5, audioSeconds = 5), tally.totals(oct31, oct31))
    }

    @Test
    fun `the tally survives a new instance on the same store`() = runTest {
        tally.record("Bonjour à tous", 2)

        assertEquals(3, DictationTally(dataStore, now = { now }).totals(oct31, oct31).words)
    }

    @Test
    fun `a text without words adds nothing, audio included`() = runTest {
        assertNull(tally.record(" ... ", 4))

        assertEquals(DictationTally.Totals(0, 0), tally.totals(oct31, oct31))
    }

    @Test
    fun `negative audio adds no seconds`() = runTest {
        tally.record("Bonjour", -3)

        assertEquals(DictationTally.Totals(words = 1, audioSeconds = 0), tally.totals(oct31, oct31))
    }

    // --- Day boundaries ---

    @Test
    fun `local midnight starts a new day even when the UTC day has not changed`() = runTest {
        // 23:59:59 and 00:00:01 in Paris (UTC+1 in winter) are both 31 October in UTC.
        now = Instant.parse("2026-10-31T22:59:59Z").atZone(paris)
        tally.record("Avant minuit", 1)
        now = Instant.parse("2026-10-31T23:00:01Z").atZone(paris)
        val afterMidnight = tally.record("Après minuit pile", 2)

        assertEquals(DictationTally.Totals(words = 3, audioSeconds = 2), afterMidnight)
        assertEquals(DictationTally.Totals(words = 2, audioSeconds = 1), tally.totals(oct31, oct31))
        assertEquals(DictationTally.Totals(words = 3, audioSeconds = 2), tally.totals(nov1, nov1))
    }

    @Test
    fun `totals sum the window's days, both edges included`() = runTest {
        for ((day, text) in listOf(30 to "un", 31 to "un deux", 32 to "un deux trois", 33 to "un deux trois quatre")) {
            now = ZonedDateTime.of(2026, 10, 1, 9, 0, 0, 0, paris).plusDays(day - 1L)
            tally.record(text, day)
        }

        // 31 Oct, 1 Nov, 2 Nov: 2 + 3 + 4 words; 30 Oct is outside.
        assertEquals(DictationTally.Totals(words = 9, audioSeconds = 31 + 32 + 33), tally.totals(oct31, LocalDate.of(2026, 11, 2)))
    }

    @Test
    fun `totals derive the iOS time saved`() = runTest {
        tally.record(List(100) { "mot" }.joinToString(" "), 30)

        assertEquals(84, tally.totals(oct31, oct31).timeSavedSeconds)
    }

    // --- Word count ---

    @Test
    fun `words are counted by word boundaries, punctuation skipped`() {
        assertEquals(0, DictationTally.wordCount(""))
        assertEquals(0, DictationTally.wordCount(" … !? "))
        assertEquals(4, DictationTally.wordCount("Bonjour, comment ça va ?"))
        assertEquals(3, DictationTally.wordCount("Il y a"))
        assertEquals(4, DictationTally.wordCount("Rendez-vous à 10h."))
    }

    @Test
    fun `an elided word stays one word, as ICU word boundaries keep it`() {
        assertEquals(2, DictationTally.wordCount("aujourd'hui l'homme"))
    }

    @Test
    fun `Mandarin, written without spaces, is split into words`() {
        assertEquals(3, DictationTally.wordCount("我爱北京。"))
    }
}
