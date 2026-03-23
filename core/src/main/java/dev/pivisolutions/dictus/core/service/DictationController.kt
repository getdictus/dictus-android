package dev.pivisolutions.dictus.core.service

import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for controlling dictation recording from the IME.
 *
 * This interface lives in the core module so the ime module can reference it
 * without depending on the app module. DictationService in the app module
 * implements this interface and exposes it through its LocalBinder.
 *
 * WHY an interface: The ime module cannot depend on the app module (that would
 * create a circular dependency). By defining the contract in core, both modules
 * can share the type without coupling.
 */
interface DictationController {

    /** Observable state for the IME to collect via collectAsState(). */
    val state: StateFlow<DictationState>

    /** Start recording via foreground service. */
    fun startRecording()

    /** Stop recording and return captured audio buffer. */
    fun stopRecording(): FloatArray

    /** Cancel recording and discard all audio data. */
    fun cancelRecording()
}
