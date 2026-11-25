package dev.pivisolutions.dictus.voicenotes

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Shows or hides Dictus in Android's share sheet for audio (#204).
 *
 * WHY: iOS cannot hide a share extension, so it lists Dictus for everyone and refuses inside it.
 * Android can enable or disable a manifest component at run time, so the share target exists only
 * for a Pro user, which is what the feature promises. The alias is declared disabled, so a fresh
 * install shows nothing until the entitlement says otherwise.
 */
object VoiceNoteShareTarget {

    /** The `<activity-alias>` that carries the intent filters. */
    const val ALIAS = "dev.pivisolutions.dictus.voicenotes.VoiceNoteShareTarget"

    fun setVisible(context: Context, visible: Boolean) {
        val state = if (visible) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        val component = ComponentName(context.packageName, ALIAS)
        if (context.packageManager.getComponentEnabledSetting(component) == state) return
        context.packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
    }
}
