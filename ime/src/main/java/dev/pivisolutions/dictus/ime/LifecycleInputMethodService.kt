package dev.pivisolutions.dictus.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Abstract base class that makes InputMethodService lifecycle-aware for Compose.
 *
 * InputMethodService does NOT extend ComponentActivity, so it lacks the
 * lifecycle/viewmodel/savedstate wiring that Compose expects. This class
 * manually implements the three "owner" interfaces and attaches them to the
 * window's decorView so that ComposeView can find them via ViewTree lookups.
 *
 * Why this matters: Without these owners, Compose will crash at runtime with
 * "ViewTreeLifecycleOwner not found" when trying to render inside the IME window.
 */
abstract class LifecycleInputMethodService : InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        // Restore saved state (null bundle = fresh start, which is normal for IME)
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onCreateInputView(): View {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        val view = ComposeView(this).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycle)
            )
            setContent { KeyboardContent() }
        }

        // Attach lifecycle owners to the decorView so Compose can find them
        // via ViewTreeXxxOwner lookups. This is the key trick for Compose-in-IME.
        window?.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this@LifecycleInputMethodService)
            decorView.setViewTreeViewModelStoreOwner(this@LifecycleInputMethodService)
            decorView.setViewTreeSavedStateRegistryOwner(this@LifecycleInputMethodService)
        }

        return view
    }

    /**
     * Subclasses override this to provide the keyboard Compose UI.
     */
    @Composable
    abstract fun KeyboardContent()

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}
