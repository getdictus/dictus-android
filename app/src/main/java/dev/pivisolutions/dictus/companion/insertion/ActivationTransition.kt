package dev.pivisolutions.dictus.companion.insertion

internal enum class ActivationRejection {
    UNRELATED_APP, ACTIVATION_NOT_CONFIRMED, WINDOW_OR_FOCUS_CHANGED, ACTIVE_ROOT_UNAVAILABLE,
}

/** Metadata-only activation exception. An unrelated focus permanently revokes the capture. */
internal class ActivationTransition(private val activationClassName: String) {
    var target: EditorIdentity? = null
        private set
    private var allowing = false
    private var finishing = false
    private var activationWindow: Int? = null
    private var provisionalWindow: Int? = null
    var lastRejection: ActivationRejection? = null
        private set
    val ready: Boolean get() = target != null && !allowing

    fun arm(identity: EditorIdentity) { clear(); target = identity }
    fun clear() {
        target = null
        allowing = false
        finishing = false
        activationWindow = null
        provisionalWindow = null
        lastRejection = null
    }
    fun begin() { if (target != null && !allowing) allowing = true }
    fun end() { if (allowing) finishing = true }

    /** False means invalidation. A provisional own window cannot authorize insertion. */
    fun accept(
        ownPackage: String,
        eventPackage: String?,
        eventClass: String?,
        eventWindow: Int,
        windowStateEvent: Boolean,
        focusedEvent: Boolean,
        activePackage: String?,
        activeWindow: Int,
        focused: EditorIdentity?,
    ): Boolean {
        val original = target ?: return false
        // Window-list notifications often belong to an IME or overlay. Their package is not
        // input focus: a freshly resolved exact original editor is the authoritative check.
        val originalFocused = focused == original && activePackage == original.packageName && activeWindow == original.windowId
        if (!windowStateEvent && !focusedEvent && originalFocused) {
            if (allowing && (activationWindow != null || provisionalWindow != null)) {
                if (!finishing || activationWindow == null) return revoke(ActivationRejection.ACTIVATION_NOT_CONFIRMED)
                allowing = false
            }
            return true
        }
        if (eventPackage != null && eventPackage != original.packageName && eventPackage != ownPackage)
            return revoke(ActivationRejection.UNRELATED_APP)
        // Android can briefly expose no active root while removing the known activation
        // window. This metadata-only list event cannot authorize insertion. Keep waiting
        // within the confirmed bracket; unknown windows and focus/state events still revoke.
        if (allowing && activationWindow != null && !windowStateEvent && !focusedEvent &&
            eventPackage == null && activePackage == null && activeWindow == -1 && focused == null &&
            (eventWindow == activationWindow || eventWindow == original.windowId)) return true
        val exactActivation = windowStateEvent && eventPackage == ownPackage &&
            eventClass == activationClassName &&
            eventWindow == activeWindow && activePackage == ownPackage && focused == null
        if (exactActivation && allowing) {
            if (activationWindow != null && activationWindow != activeWindow) return revoke()
            if (provisionalWindow != null && provisionalWindow != activeWindow) return revoke()
            activationWindow = activeWindow
            return true
        }
        if (originalFocused) {
            if (focusedEvent && (eventPackage != original.packageName || eventWindow != original.windowId)) return revoke()
            if (windowStateEvent && eventPackage != original.packageName) return revoke()
            if (allowing && (activationWindow != null || provisionalWindow != null)) {
                if (!finishing || activationWindow == null) return revoke(ActivationRejection.ACTIVATION_NOT_CONFIRMED)
                allowing = false
            }
            return true
        }
        // On an immediate recording handoff Android can activate the original window before
        // restoring its editor's input focus. Keep waiting only for that exact known window,
        // after the named activation has explicitly finished. This does not authorize a write:
        // ready stays false until the exact original editor identity is resolved above.
        if (allowing && finishing && activationWindow != null && !focusedEvent && focused == null &&
            activePackage == original.packageName && activeWindow == original.windowId &&
            (eventPackage == null || eventPackage == original.packageName)) {
            return true
        }
        // Window-list events can precede the named activity event. Permit only a single own
        // window without an editor, and require the exact activity confirmation before return.
        if (allowing && !windowStateEvent && !focusedEvent && activePackage == ownPackage && focused == null) {
            if (activationWindow != null) return if (activationWindow == activeWindow) true else revoke()
            if (provisionalWindow != null && provisionalWindow != activeWindow) return revoke()
            provisionalWindow = activeWindow
            return true
        }
        return revoke(if (activePackage == null && activeWindow == -1)
            ActivationRejection.ACTIVE_ROOT_UNAVAILABLE else ActivationRejection.WINDOW_OR_FOCUS_CHANGED)
    }

    private fun revoke(reason: ActivationRejection = ActivationRejection.WINDOW_OR_FOCUS_CHANGED): Boolean {
        clear()
        lastRejection = reason
        return false
    }
}
