package dev.pivisolutions.dictus.ime.suggestion

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests for PersonalDictionary.
 *
 * WHY TestScope + PreferenceDataStoreFactory: We use an in-process DataStore backed
 * by a temp file so tests are isolated and don't touch production preferences.
 * advanceUntilIdle() ensures all DataStore writes and collect callbacks complete
 * before assertions run.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonalDictionaryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var testScope: TestScope
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        testScope = TestScope(testDispatcher)
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tempFolder.newFile("personal_dict_test.preferences_pb") },
        )
    }

    @Test
    fun `recordWordTyped once does not learn the word`() = runTest(testDispatcher) {
        val dict = PersonalDictionary(dataStore, testScope)
        advanceUntilIdle()

        dict.recordWordTyped("hello")
        advanceUntilIdle()

        assertFalse(
            "Word typed only once should NOT be in learnedWords",
            dict.learnedWords.contains("hello"),
        )
    }

    @Test
    fun `recordWordTyped twice learns the word`() = runTest(testDispatcher) {
        val dict = PersonalDictionary(dataStore, testScope)
        advanceUntilIdle()

        dict.recordWordTyped("hello")
        dict.recordWordTyped("hello")
        advanceUntilIdle()

        assertTrue(
            "Word typed twice should be in learnedWords",
            dict.learnedWords.contains("hello"),
        )
    }

    @Test
    fun `recordWordTyped with blank word is a no-op`() = runTest(testDispatcher) {
        val dict = PersonalDictionary(dataStore, testScope)
        advanceUntilIdle()

        dict.recordWordTyped("")
        dict.recordWordTyped("   ")
        advanceUntilIdle()

        assertTrue(
            "learnedWords should remain empty after blank inputs",
            dict.learnedWords.isEmpty(),
        )
    }

    @Test
    fun `learnedWords are persisted to DataStore`() = runTest(testDispatcher) {
        val dict = PersonalDictionary(dataStore, testScope)
        advanceUntilIdle()

        dict.recordWordTyped("merci")
        dict.recordWordTyped("merci")
        advanceUntilIdle()

        // Verify the DataStore actually contains the learned word
        val storedSet = dataStore.data.first()[PreferenceKeys.PERSONAL_DICTIONARY]
        assertTrue(
            "DataStore should contain 'merci' after learning, got: $storedSet",
            storedSet?.contains("merci") == true,
        )
    }

    @Test
    fun `learnedWords loaded from DataStore on init`() = runTest(testDispatcher) {
        // First dictionary instance learns a word
        val dict1 = PersonalDictionary(dataStore, testScope)
        advanceUntilIdle()
        dict1.recordWordTyped("bonjour")
        dict1.recordWordTyped("bonjour")
        advanceUntilIdle()

        // Second dictionary instance should load it from DataStore
        val dict2 = PersonalDictionary(dataStore, testScope)
        advanceUntilIdle()

        assertTrue(
            "New PersonalDictionary instance should load 'bonjour' from DataStore",
            dict2.learnedWords.contains("bonjour"),
        )
    }
}
