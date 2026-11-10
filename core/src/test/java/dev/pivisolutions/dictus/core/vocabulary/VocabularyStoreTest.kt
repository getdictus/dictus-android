package dev.pivisolutions.dictus.core.vocabulary

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The store's contract, after iOS `VocabularyStore` (#203), on a real DataStore file.
 * Robolectric only for `org.json`, which the plain JVM android.jar stubs out.
 */
@RunWith(RobolectricTestRunner::class)
class VocabularyStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    private fun dataStore(file: File = File(folder.root, "prefs.preferences_pb")): DataStore<Preferences> {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        return PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    private fun entry(term: String, vararg variants: String) =
        requireNotNull(VocabularyEntry.create(term, variants.toList()))

    @Test
    fun `add stores newest first and survives a new store instance`() = runTest {
        val file = File(folder.root, "persist.preferences_pb")
        val store = VocabularyStore(dataStore(file), isEntitled = { true })
        val first = entry("Kubernetes", "cubernetes")
        val second = entry("Claude Code", "clode code")
        assertTrue(store.add(first))
        assertTrue(store.add(second))
        assertEquals(listOf(second, first), store.loadEntries())

        scopes.forEach { it.cancel() }
        val reopened = VocabularyStore(dataStore(file), isEntitled = { true })
        assertEquals(listOf(second, first), reopened.loadEntries())
    }

    @Test
    fun `add is refused while the feature is hidden, removal is not`() = runTest {
        val dataStore = dataStore()
        assertTrue(VocabularyStore(dataStore, isEntitled = { true }).add(entry("Kubernetes", "cubernetes")))

        val hidden = VocabularyStore(dataStore, isEntitled = { false })
        assertFalse(hidden.add(entry("Claude", "clode")))
        val stored = hidden.loadEntries().single()
        assertTrue(hidden.update(stored.withEnabled(false)))
        assertFalse(hidden.loadEntries().single().isEnabled)
        assertTrue(hidden.delete(stored.id))
        assertEquals(emptyList<VocabularyEntry>(), hidden.loadEntries())
    }

    @Test
    fun `a duplicate term is refused ignoring case`() = runTest {
        val store = VocabularyStore(dataStore(), isEntitled = { true })
        assertTrue(store.add(entry("Kubernetes", "cubernetes")))
        assertFalse(store.add(entry("kubernetes", "kube")))
        assertEquals(1, store.loadEntries().size)
    }

    @Test
    fun `the entry past the cap is refused`() = runTest {
        val store = VocabularyStore(dataStore(), isEntitled = { true })
        repeat(VocabularyStore.MAX_ENTRIES) { assertTrue(store.add(entry("Term$it", "variant$it"))) }
        assertFalse(store.add(entry("One too many", "oops")))
        assertEquals(VocabularyStore.MAX_ENTRIES, store.loadEntries().size)
    }

    @Test
    fun `update keeps the position, an unknown id is refused`() = runTest {
        val store = VocabularyStore(dataStore(), isEntitled = { true })
        val a = entry("Alpha", "alfa")
        val b = entry("Beta", "betta")
        store.add(a)
        store.add(b)
        assertTrue(store.update(a.withEnabled(false)))
        assertEquals(listOf(b, a.withEnabled(false)), store.loadEntries())
        assertFalse(store.update(entry("Gamma", "gama")))
        assertFalse(store.delete(entry("Gamma").id))
    }

    @Test
    fun `resetAll empties the vocabulary and removes the key`() = runTest {
        val dataStore = dataStore()
        val store = VocabularyStore(dataStore, isEntitled = { true })
        store.add(entry("Kubernetes", "cubernetes"))
        assertTrue(store.resetAll())
        assertEquals(emptyList<VocabularyEntry>(), store.loadEntries())
        val raw = dataStore.edit { }[PreferenceKeys.VOCABULARY_ENTRIES]
        assertEquals(null, raw)
        assertTrue("resetting an empty vocabulary is a success", store.resetAll())
    }

    @Test
    fun `an unreadable value is an empty vocabulary`() = runTest {
        val dataStore = dataStore()
        dataStore.edit { it[PreferenceKeys.VOCABULARY_ENTRIES] = "{not json" }
        assertEquals(emptyList<VocabularyEntry>(), VocabularyStore(dataStore).loadEntries())
    }

    @Test
    fun `an entry that violates the limits is dropped at load, the rest survive`() = runTest {
        val dataStore = dataStore()
        val good = entry("Kubernetes", "cubernetes")
        val overLong = good.copy(id = java.util.UUID.randomUUID(), term = "x".repeat(101))
        dataStore.edit { it[PreferenceKeys.VOCABULARY_ENTRIES] = VocabularyStore.encode(listOf(overLong, good)) }
        assertEquals(listOf(good), VocabularyStore(dataStore).loadEntries())
    }
}
