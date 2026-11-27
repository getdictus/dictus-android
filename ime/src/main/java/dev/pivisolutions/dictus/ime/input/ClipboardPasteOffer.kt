package dev.pivisolutions.dictus.ime.input

import android.content.ClipDescription
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * What the keyboard may know about the clipboard before the user taps anything (#420), iOS
 * `PasteboardSnapshot`: a marker of the copy and whether it holds text, never the text.
 *
 * [marker] is `ClipDescription.getTimestamp()`, the time the system set the clip. It stands for
 * iOS `UIPasteboard.changeCount`: it names one copy for good, it is read from the description
 * alone (no clip item, so no "pasted from clipboard" notice), and it survives an IME restart,
 * which a counter fed by `OnPrimaryClipChangedListener` would not: a copy made while the IME
 * process was dead would never be counted.
 */
data class ClipboardSnapshot(val marker: Long, val hasText: Boolean) {
    companion object {
        /** Null when the clipboard is empty. Reads the description only. */
        fun of(description: ClipDescription?): ClipboardSnapshot? = description?.let {
            ClipboardSnapshot(it.timestamp, it.hasMimeType("text/*"))
        }
    }
}

/**
 * The Paste offer's policy, port of iOS `ClipboardPasteOffer` (develop, #634): the toolbar shows
 * Paste for a copy that is recent and that the keyboard has not pasted yet.
 *
 * WHY a freshness window: a copy from last week is not what the user is about to paste, and an
 * offer that never leaves would sit in the bar for good. It starts when the keyboard first
 * notices the copy, as on iOS.
 *
 * Only two markers and a timestamp are stored, in [store], never clipboard contents. Stored
 * rather than kept in memory for the reason iOS gives: the IME process can be torn down between
 * two apps, and a fresh one would re-offer a copy already pasted.
 */
class ClipboardPasteOffer(private val store: Store) {

    /** Three numbers, so the tests run on a map and the service on SharedPreferences. */
    interface Store {
        fun get(key: String): Long?
        fun put(key: String, value: Long)
    }

    /**
     * Whether the toolbar should offer Paste now. Records the first sighting of a new copy, which
     * starts its freshness window.
     *
     * [clipboard] is only called when [enabled]: with the setting off, the clipboard is never
     * touched, as iOS never touches it without Full Access.
     */
    fun isOffered(enabled: Boolean, clipboard: () -> ClipboardSnapshot?, now: Long): Boolean {
        if (!enabled) return false
        val snapshot = clipboard() ?: return false
        // Recorded even for a clip that cannot be offered: the marker names one copy for good.
        if (store.get(SEEN_MARKER) != snapshot.marker) {
            store.put(SEEN_MARKER, snapshot.marker)
            store.put(SEEN_AT, now)
        }
        if (!snapshot.hasText || store.get(PASTED_MARKER) == snapshot.marker) return false
        return now - (store.get(SEEN_AT) ?: now) < FRESHNESS_MS
    }

    /** When the offer for the copy last seen runs out, null before any copy was seen. */
    fun expiry(): Long? = store.get(SEEN_AT)?.plus(FRESHNESS_MS)

    /** Records that this copy was pasted, so it is not offered again. */
    fun notePasted(marker: Long) {
        store.put(PASTED_MARKER, marker)
    }

    companion object {
        /** How long after the keyboard first notices a copy it keeps offering it (iOS `freshness`). */
        const val FRESHNESS_MS = 3 * 60 * 1000L

        private const val SEEN_MARKER = "seen_marker"
        private const val SEEN_AT = "seen_at"
        private const val PASTED_MARKER = "pasted_marker"
        private const val PREFS_NAME = "dictus_clipboard_offer"

        fun create(context: Context): ClipboardPasteOffer =
            ClipboardPasteOffer(SharedPreferencesStore(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)))
    }

    private class SharedPreferencesStore(private val prefs: SharedPreferences) : Store {
        override fun get(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, 0) else null
        override fun put(key: String, value: Long) = prefs.edit { putLong(key, value) }
    }
}
