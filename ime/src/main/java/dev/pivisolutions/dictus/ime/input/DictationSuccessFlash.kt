package dev.pivisolutions.dictus.ime.input

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One pending green flash on the mic pill, owed after a dictation was inserted (#24).
 *
 * WHY a token and not a Boolean: the pill is composed only while the dictation is Idle, and the
 * service can go back to Idle before or after the insertion lands. A token survives both orders
 * and is consumed exactly once, so a remount never replays a flash already shown.
 */
class DictationSuccessFlash {
    private val _pending = MutableStateFlow<Int?>(null)
    val pending: StateFlow<Int?> = _pending.asStateFlow()
    private var lastToken = 0

    /** Owes a flash for the dictation just inserted, replacing one not yet shown. */
    fun arm() {
        lastToken += 1
        _pending.value = lastToken
    }

    /** Marks [token] as shown; a newer token armed meanwhile stays pending. */
    fun consume(token: Int) {
        _pending.compareAndSet(token, null)
    }

    /** Drops a flash not yet shown, e.g. when the keyboard hides. */
    fun clear() {
        _pending.value = null
    }
}
