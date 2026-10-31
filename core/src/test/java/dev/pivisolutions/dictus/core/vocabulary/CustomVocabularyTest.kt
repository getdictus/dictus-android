package dev.pivisolutions.dictus.core.vocabulary

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.pivisolutions.dictus.core.premium.PremiumFlags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import timber.log.Timber
import java.io.File

/** The one gate (#203): off or empty means the text comes back as it went in, and the log holds counts only. */
@RunWith(RobolectricTestRunner::class)
class CustomVocabularyTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val logs = mutableListOf<String>()
    private val tree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            logs += message
        }
    }

    private lateinit var store: VocabularyStore

    @Before
    fun setUp() {
        Timber.plant(tree)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "prefs.preferences_pb") }
        store = VocabularyStore(dataStore, isEntitled = { true })
    }

    @After
    fun tearDown() {
        Timber.uproot(tree)
        scope.cancel()
    }

    private val raw = "on passe sur cubernetes demain"

    private suspend fun storeKubernetes() {
        store.add(requireNotNull(VocabularyEntry.create("Kubernetes", listOf("cubernetes"))))
    }

    @Test
    fun `the vocabulary is hidden by default`() {
        assertFalse(PremiumFlags.VOCABULARY)
    }

    @Test
    fun `with the flag off the text is unchanged even with entries stored`() = runTest {
        storeKubernetes()
        assertEquals(raw, CustomVocabulary(store).corrected(raw))
        assertEquals(listOf("vocabularyApplied: enabled=no entries=0 replacements=0 chars=${raw.length}"), logs)
    }

    @Test
    fun `with the flag on and nothing stored the text is unchanged`() = runTest {
        assertEquals(raw, CustomVocabulary(store, isEnabled = { true }).corrected(raw))
        assertEquals(listOf("vocabularyApplied: enabled=yes entries=0 replacements=0 chars=${raw.length}"), logs)
    }

    @Test
    fun `with the flag on the stored vocabulary is applied and only counts are logged`() = runTest {
        storeKubernetes()
        assertEquals("on passe sur Kubernetes demain", CustomVocabulary(store, isEnabled = { true }).corrected(raw))
        assertEquals(listOf("vocabularyApplied: enabled=yes entries=1 replacements=1 chars=${raw.length}"), logs)
        for (word in listOf("cubernetes", "Kubernetes", "passe", "demain")) {
            assertFalse("the log must not carry '$word'", logs.single().contains(word, ignoreCase = true))
        }
    }

    @Test
    fun `an entry switched off does not count and does not replace`() = runTest {
        storeKubernetes()
        store.update(store.loadEntries().single().withEnabled(false))
        assertEquals(raw, CustomVocabulary(store, isEnabled = { true }).corrected(raw))
        assertEquals(listOf("vocabularyApplied: enabled=yes entries=0 replacements=0 chars=${raw.length}"), logs)
    }

    @Test
    fun `the supplied-entries overload applies without logging`() {
        val entries = listOf(requireNotNull(VocabularyEntry.create("Kubernetes", listOf("cubernetes"))))
        assertEquals("on passe sur Kubernetes demain", CustomVocabulary.corrected(raw, entries))
        assertEquals(emptyList<String>(), logs)
    }
}
