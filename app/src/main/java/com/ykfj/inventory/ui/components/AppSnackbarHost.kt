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
 * Every snackbar uses the same gold-on-dark styling regardless of kind. The
 * [SnackbarKind] enum exists in the controller for future expansion (e.g.
 * tinting error snackbars red when error paths migrate off the per-screen
 * pattern), but today the message text alone conveys success vs info — errors
 * are still handled by per-screen `SnackbarHost`s near the action that
 * produced them.
 *
 * Snackbars are short by default (~3s). No action affordance: every
 * destructive flow in this app already routes through its own confirm dialog,
 * so an "Undo" button on the snackbar would be redundant.
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
        Snackbar(
            snackbarData = data,
            containerColor = Gold30,
            contentColor = Color.White,
            actionColor = Gold80,
        )
    }
}
