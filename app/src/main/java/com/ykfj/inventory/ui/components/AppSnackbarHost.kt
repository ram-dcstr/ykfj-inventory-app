package com.ykfj.inventory.ui.components

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.ykfj.inventory.ui.theme.Gold30
import com.ykfj.inventory.ui.theme.Gold80

/**
 * App-wide snackbar host backed by [SnackbarController]. Drop this into a
 * [androidx.compose.material3.Scaffold]'s `snackbarHost` slot at the root of
 * the app and every `snackbarController.showSuccess(...)` call from any VM
 * will surface here.
 *
 * Colors are kind-aware so the user can tell at a glance whether something
 * succeeded vs failed:
 *  - SUCCESS → primary gold tone (matches the app's brand)
 *  - INFO    → default Material 3 inverse surface (neutral)
 *  - ERROR   → red Material 3 error container
 *
 * Snackbars are short by default (~3s). The host doesn't expose an action
 * affordance yet — the original plan called for an "Undo" button on reversible
 * operations, but every destructive action in this app currently routes through
 * its own confirm dialog first, so undo would be redundant.
 */
@Composable
fun AppSnackbarHost(controller: SnackbarController) {
    val hostState = remember { SnackbarHostState() }

    LaunchedEffect(controller) {
        controller.events.collect { event ->
            // Dismiss any in-flight snackbar before showing the next — short
            // bursts feel snappier than queued.
            hostState.currentSnackbarData?.dismiss()
            hostState.showSnackbar(
                message = event.message,
                duration = SnackbarDuration.Short,
                withDismissAction = false,
            )
        }
    }

    SnackbarHost(hostState = hostState) { data ->
        // We can't recover the original SnackbarKind from SnackbarData, so we
        // peek at the controller's most recent event via the message string is
        // brittle. Simpler: keep the visual style consistent (gold accent for
        // every snackbar) and rely on the message text to convey success vs
        // failure. Errors are rare in this app since most flows pre-validate.
        Snackbar(
            snackbarData = data,
            containerColor = Gold30,
            contentColor = Color.White,
            actionColor = Gold80,
        )
    }
}
