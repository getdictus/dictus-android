package dev.pivisolutions.dictus.permission

import android.os.Build

/**
 * Whether to ask for POST_NOTIFICATIONS now (#308).
 *
 * Android 13 (API 33) made it a runtime permission. Without it the foreground services still
 * run, the model install (`dataSync`) and the dictation (`microphone`), but their
 * notifications stay hidden from the shade. Below 33 they show without asking.
 *
 * WHY ONCE, EVEN AFTER A REFUSAL: Android allows a second prompt after a first « Don't
 * allow », and answers every later one itself. Asking again at a later download would be
 * nagging; the user can still turn notifications on in Android settings.
 *
 * @param sdkInt the device's API level, `Build.VERSION.SDK_INT`.
 * @param granted whether POST_NOTIFICATIONS is granted already.
 * @param alreadyAsked whether Dictus has shown the prompt before, whatever the answer.
 */
fun shouldRequestNotificationPermission(sdkInt: Int, granted: Boolean, alreadyAsked: Boolean): Boolean =
    sdkInt >= Build.VERSION_CODES.TIRAMISU && !granted && !alreadyAsked
