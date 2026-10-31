package dev.pivisolutions.dictus.core.vocabulary

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.core.premium.PremiumFlags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.util.UUID

/**
 * Every term the user taught Dictus, newest first. Port of iOS `VocabularyStore` (#203).
 *
 * WHY the app's DataStore and one JSON key: iOS keeps a JSON file in the App Group
 * because shared `UserDefaults` survived reinstalls and could not be cleared (#428 on
 * iOS). Android has neither problem: Dictus runs in one process, the app and the
 * keyboard share the single `dictus_prefs` DataStore, and app data goes with an
 * uninstall or a "Clear storage". A second DataStore file would need its own singleton
 * in the app's DI graph for nothing. One key written whole keeps iOS's
 * load-once/write-whole discipline, and [resetAll] removes it.
 *
 * WHY injected rather than built here: `core` has no DI of its own, and the DataStore
 * has to stay a process-wide singleton (two instances on one file corrupt it). The app
 * passes the instance Hilt already provides; the tests pass one on a temporary file.
 *
 * Write first, publish second comes for free: [entries] only emits what DataStore has
 * written, so a failed write publishes nothing and the call returns false.
 *
 * The flag gates growth, never removal, as on iOS: [add] refuses while the feature is
 * hidden, [update], [delete] and [resetAll] do not, so data the user can no longer see
 * is never imprisoned.
 */
class VocabularyStore(
    private val dataStore: DataStore<Preferences>,
    /** Read at each call. Injected so the tests can drive both directions of the gate. */
    private val isEntitled: () -> Boolean = { PremiumFlags.VOCABULARY },
) {

    /** The stored entries, newest first. Invalid entries on disk are dropped, not trusted. */
    val entries: Flow<List<VocabularyEntry>> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { decode(it[PreferenceKeys.VOCABULARY_ENTRIES]) }

    /** One read of [entries], for the replacement pass. */
    suspend fun loadEntries(): List<VocabularyEntry> = entries.first()

    /**
     * Store a term, or refuse: without the entitlement, at [MAX_ENTRIES], and when the
     * term duplicates one already stored (ignoring case). Checked inside the write
     * transaction, so two concurrent adds cannot both pass the cap.
     */
    suspend fun add(entry: VocabularyEntry): Boolean {
        if (!isEntitled()) return false
        return commit { current ->
            when {
                current.size >= MAX_ENTRIES -> null
                current.any { it.term.lowercase() == entry.term.lowercase() } -> null
                else -> listOf(entry) + current
            }
        }
    }

    /** Replace an entry in place, keeping its position. Ungated: it cannot grow the store. */
    suspend fun update(entry: VocabularyEntry): Boolean = commit { current ->
        val index = current.indexOfFirst { it.id == entry.id }
        if (index < 0) null else current.toMutableList().also { it[index] = entry }
    }

    suspend fun delete(id: UUID): Boolean = commit { current ->
        val remaining = current.filter { it.id != id }
        if (remaining.size == current.size) null else remaining
    }

    /** The one action that empties the vocabulary. */
    suspend fun resetAll(): Boolean = commit { emptyList() }

    /**
     * Every mutation goes through here. [transform] returns the new list, or null to
     * refuse. Returns whether the store now holds what was asked; an unchanged list is
     * a success with nothing written.
     */
    private suspend fun commit(transform: (List<VocabularyEntry>) -> List<VocabularyEntry>?): Boolean {
        var accepted = false
        var size = 0
        return try {
            dataStore.edit { preferences ->
                val current = decode(preferences[PreferenceKeys.VOCABULARY_ENTRIES])
                val candidate = transform(current) ?: return@edit
                accepted = true
                size = candidate.size
                if (candidate == current) return@edit
                if (candidate.isEmpty()) {
                    preferences.remove(PreferenceKeys.VOCABULARY_ENTRIES)
                } else {
                    preferences[PreferenceKeys.VOCABULARY_ENTRIES] = encode(candidate)
                }
            }
            accepted
        } catch (e: IOException) {
            // Counts only: an entry is a word the user dictates, and the log is exportable.
            Timber.w("Vocabulary write failed: entries=$size, error=${e.javaClass.simpleName}")
            false
        }
    }

    companion object {

        /**
         * Maximum number of entries. A refusal and not an eviction: a term that vanished
         * would stop correcting text with nothing on screen to say why.
         */
        const val MAX_ENTRIES = 200

        internal fun encode(entries: List<VocabularyEntry>): String {
            val array = JSONArray()
            for (entry in entries) {
                array.put(
                    JSONObject()
                        .put("id", entry.id.toString())
                        .put("term", entry.term)
                        .put("variants", JSONArray(entry.variants))
                        .put("isEnabled", entry.isEnabled)
                        .put("dateAdded", entry.dateAdded),
                )
            }
            return array.toString()
        }

        /**
         * A value that will not decode is an empty vocabulary rather than a crash, and an
         * entry that violates the limits is dropped rather than trusted: an over-long
         * needle would cost a scan of every transcript.
         */
        internal fun decode(json: String?): List<VocabularyEntry> {
            if (json.isNullOrEmpty()) return emptyList()
            return try {
                val array = JSONArray(json)
                (0 until array.length()).map { index ->
                    val item = array.getJSONObject(index)
                    val variants = item.getJSONArray("variants")
                    VocabularyEntry(
                        id = UUID.fromString(item.getString("id")),
                        term = item.getString("term"),
                        variants = (0 until variants.length()).map { variants.getString(it) },
                        isEnabled = item.getBoolean("isEnabled"),
                        dateAdded = item.getLong("dateAdded"),
                    )
                }.filter { it.isValid }
            } catch (e: JSONException) {
                emptyList()
            } catch (e: IllegalArgumentException) {
                // UUID.fromString on a malformed id.
                emptyList()
            }
        }
    }
}
