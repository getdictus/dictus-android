package dev.pivisolutions.dictus.ime.suggestion

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Microbenchmark for DictionaryEngine.getSuggestions() throughput.
 *
 * Verifies that 1000 sequential prefix lookups complete in under 100ms total,
 * which corresponds to ~100µs per keystroke — well within the 16ms frame budget.
 *
 * WHY this matters: getSuggestions() is called on the main thread from
 * onUpdateSelection() on every keystroke. It must be fast enough to not cause
 * IME frame drops or visible lag.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DictionaryBenchmarkTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var testScope: TestScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var context: Context

    @Before
    fun setUp() {
        testScope = TestScope(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tempFolder.newFile("bench_prefs.preferences_pb") },
        )
    }

    @Test
    fun `1000 sequential getSuggestions calls complete in under 100ms`() = runTest(testDispatcher) {
        val engine = DictionaryEngine(
            context = context,
            dataStore = dataStore,
            coroutineScope = testScope,
            assetName = "dict_test.txt",
            ioDispatcher = testDispatcher,
        )
        advanceUntilIdle()

        // Warm up: one call to ensure JIT compilation is done
        engine.getSuggestions("bon", 3)

        val startMs = System.currentTimeMillis()
        repeat(1000) {
            engine.getSuggestions("bon", 3)
        }
        val elapsedMs = System.currentTimeMillis() - startMs

        assertTrue(
            "1000 getSuggestions calls should complete in under 100ms, took: ${elapsedMs}ms",
            elapsedMs < 100,
        )
    }
}
