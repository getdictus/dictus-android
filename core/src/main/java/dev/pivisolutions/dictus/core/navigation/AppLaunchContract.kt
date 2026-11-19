package dev.pivisolutions.dictus.core.navigation

import java.util.UUID

/** Cross-module contract used by the IME to open the app directly on Settings or Models. */
object AppLaunchContract {
    const val ACTION_OPEN_SETTINGS = "dev.pivisolutions.dictus.action.OPEN_SETTINGS"

    /**
     * Opens the Models tab, from a mic tap with no model on disk (#247). It needs no
     * authorization: it bypasses nothing, and onboarding still runs first when incomplete.
     */
    const val ACTION_OPEN_MODELS = "dev.pivisolutions.dictus.action.OPEN_MODELS"

    /**
     * Opens Dictus and asks for the microphone, from the keyboard's refused-microphone notice
     * (#307): an IME has no activity to ask from. It needs no authorization either, as it only
     * shows Android's own prompt.
     */
    const val ACTION_REQUEST_MICROPHONE = "dev.pivisolutions.dictus.action.REQUEST_MICROPHONE"
    const val EXTRA_AUTHORIZATION = "dev.pivisolutions.dictus.extra.SETTINGS_AUTHORIZATION"
    const val MAIN_ACTIVITY_CLASS = "dev.pivisolutions.dictus.MainActivity"
}

/**
 * One-shot, process-local authorization for the exported launcher activity.
 *
 * MainActivity must remain exported for the launcher, so an action string alone cannot authorize
 * bypassing the onboarding gate. The IME and activity share this process-local capability; other
 * apps cannot issue a valid token through an explicit intent.
 */
object SettingsLaunchAuthorization {
    private val pendingTokens = mutableSetOf<String>()

    @Synchronized
    fun issue(): String = UUID.randomUUID().toString().also(pendingTokens::add)

    @Synchronized
    fun consume(token: String?): Boolean = token != null && pendingTokens.remove(token)

    @Synchronized
    fun revoke(token: String) {
        pendingTokens.remove(token)
    }
}
