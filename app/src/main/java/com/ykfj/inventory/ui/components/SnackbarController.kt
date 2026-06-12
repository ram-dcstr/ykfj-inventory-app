package com.ykfj.inventory.ui.components

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Global, app-wide snackbar bus. Any ViewModel (or other layer) injects this
 * singleton and calls [showSuccess] / [showError] / [showInfo] to surface a
 * transient message — the snackbar host wired into [com.ykfj.inventory.MainActivity]
 * collects the [events] flow and renders the snackbar above whatever screen is
 * currently visible.
 *
 * Why a global controller instead of per-screen `SnackbarHostState` plumbing:
 *
 * - Most success messages fire from ViewModels after the dialog/form that
 *   triggered them has already dismissed. The host that originated the action
 *   may be gone by the time the message arrives. A single root-level host
 *   survives navigation.
 * - Eliminates the boilerplate of every screen owning a `SnackbarHostState`,
 *   collecting `viewModel.actionError`/`successEvent`, and forwarding them.
 *   The VM emits; the root host shows.
 *
 * Buffer policy: `extraBufferCapacity = 8` so a quick burst of writes (e.g. a
 * batch operation that emits multiple messages) doesn't drop any. Messages are
 * never replayed — they're transient by nature, so a new collector shouldn't
 * see old ones.
 */
@Singleton
class SnackbarController @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _events = MutableSharedFlow<SnackbarEvent>(
        replay = 0,
        extraBufferCapacity = 8,
    )
    val events: SharedFlow<SnackbarEvent> = _events.asSharedFlow()

    /** Fire-and-forget — safe to call from any thread/dispatcher. */
    fun showSuccess(message: String) = emit(SnackbarEvent(message, SnackbarKind.SUCCESS))
    fun showInfo(message: String) = emit(SnackbarEvent(message, SnackbarKind.INFO))
    fun showError(message: String) = emit(SnackbarEvent(message, SnackbarKind.ERROR))

    private fun emit(event: SnackbarEvent) {
        scope.launch { _events.emit(event) }
    }
}

data class SnackbarEvent(
    val message: String,
    val kind: SnackbarKind,
)

enum class SnackbarKind { SUCCESS, INFO, ERROR }
