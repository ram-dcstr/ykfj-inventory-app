package com.ykfj.inventory

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.ykfj.inventory.data.remote.sync.SyncManager
import com.ykfj.inventory.data.remote.sync.SyncServerManager
import com.ykfj.inventory.data.repository.DeviceRoleManager
import com.ykfj.inventory.domain.sync.DeviceRole
import com.ykfj.inventory.ui.auth.LoginScreen
import com.ykfj.inventory.ui.auth.SessionManager
import com.ykfj.inventory.ui.components.AppSnackbarHost
import com.ykfj.inventory.ui.components.SnackbarController
import com.ykfj.inventory.ui.navigation.NavGraph
import com.ykfj.inventory.ui.navigation.Screen
import com.ykfj.inventory.ui.navigation.SidebarBadgeViewModel
import com.ykfj.inventory.ui.navigation.SidebarContent
import com.ykfj.inventory.ui.theme.YkfjInventoryTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var syncServerManager: SyncServerManager

    @Inject
    lateinit var deviceRoleManager: DeviceRoleManager

    @Inject
    lateinit var snackbarController: SnackbarController

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YkfjInventoryTheme {
                val windowSizeClass = calculateWindowSizeClass(this)
                val isExpandedScreen =
                    windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded

                val currentUser by sessionManager.currentUser.collectAsStateWithLifecycle()

                // Idle timeout check. Emits the flash BEFORE logout so the snackbar
                // host (mounted at the root, see below) catches it while the user
                // is still in the "logged-in" branch — by the time LoginScreen renders,
                // the snackbar is already on screen.
                LaunchedEffect(currentUser) {
                    while (currentUser != null && isActive) {
                        delay(60_000) // check every minute
                        if (sessionManager.isSessionExpired()) {
                            snackbarController.showInfo("Logged out due to inactivity")
                            sessionManager.logout()
                        }
                    }
                }

                // Root-level Scaffold so the snackbar host stays mounted across the
                // LoginScreen ↔ AppShell transition. Without this, login welcome and
                // idle-logout messages would be lost — the host would unmount before
                // they reach the screen.
                Scaffold(
                    snackbarHost = { AppSnackbarHost(snackbarController) },
                ) { padding ->
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        if (currentUser == null) {
                            LoginScreen(onLoginSuccess = { /* state auto-updates via Flow */ })
                        } else {
                            AppShell(
                                isExpandedScreen = isExpandedScreen,
                                sessionManager = sessionManager,
                                syncManager = syncManager,
                                syncServerManager = syncServerManager,
                                deviceRoleManager = deviceRoleManager,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (sessionManager.isLoggedIn) {
            sessionManager.recordActivity()
        }
    }

    // App lock: single-activity app, so the Activity's stop/start track the app
    // leaving/returning. Returning after more than the grace window requires re-login.
    override fun onStop() {
        super.onStop()
        sessionManager.onAppBackgrounded()
    }

    override fun onStart() {
        super.onStart()
        if (sessionManager.onAppForegrounded()) {
            snackbarController.showInfo("Logged out for security — please log in")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppShell(
    isExpandedScreen: Boolean,
    sessionManager: SessionManager,
    syncManager: SyncManager,
    syncServerManager: SyncServerManager,
    deviceRoleManager: DeviceRoleManager,
) {
    val navController = rememberNavController()
    val currentUser by sessionManager.currentUser.collectAsStateWithLifecycle()
    val syncStatus by syncManager.status.collectAsStateWithLifecycle()
    val deviceRole by deviceRoleManager.deviceRole.collectAsStateWithLifecycle(initialValue = DeviceRole.TABLET)
    val isServerRunning by syncServerManager.isRunning.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val syncScope = rememberCoroutineScope()
    var selectedScreen by rememberSaveable { mutableStateOf(Screen.Inventory.route) }

    val activeScreen = Screen.allScreens.firstOrNull { it.route == selectedScreen }
        ?: Screen.Inventory

    val onScreenSelected: (Screen) -> Unit = { screen ->
        selectedScreen = screen.route
        navController.navigate(screen.route) {
            // Clear the back stack on every tab switch so each tab starts fresh —
            // no half-finished work (search text, filters, scroll, or an open
            // sub-screen) is restored when returning to a tab. This applies to
            // every tab, including the Home/Inventory tab.
            //
            // Drilling into a detail screen within a tab uses navController.navigate(...)
            // directly (not this handler), so back from a detail still returns to the
            // tab's list as usual. Only switching tabs resets.
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    val onLogout: () -> Unit = { sessionManager.logout() }

    val onSyncTap: () -> Unit = {
        when (deviceRole) {
            DeviceRole.PHONE -> {
                if (!syncStatus.isSyncing) {
                    Toast.makeText(context, "Syncing now…", Toast.LENGTH_SHORT).show()
                    syncScope.launch { syncManager.sync() }
                }
            }
            DeviceRole.TABLET -> {
                val msg = if (isServerRunning) {
                    "Sync server running on port ${SyncServerManager.SERVER_PORT}"
                } else {
                    "Sync server stopped. Open Settings to restart."
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Live red-alert badge counts. The VM ticks once a minute so the time
    // window in the underlying SQL stays current.
    val badgeViewModel: SidebarBadgeViewModel = hiltViewModel()
    val layawayOverdueCount by badgeViewModel.layawayOverdueCount.collectAsStateWithLifecycle()
    val paluwaganDueCount by badgeViewModel.paluwaganDueTodayCount.collectAsStateWithLifecycle()

    if (isExpandedScreen) {
        Row(modifier = Modifier.fillMaxSize()) {
            SidebarContent(
                currentUser = currentUser,
                selectedScreen = activeScreen,
                layawayOverdueCount = layawayOverdueCount,
                paluwaganDueCount = paluwaganDueCount,
                onScreenSelected = onScreenSelected,
                onLogout = onLogout,
                syncStatus = syncStatus,
                deviceRole = deviceRole,
                isServerRunning = isServerRunning,
                onSyncTap = onSyncTap,
            )
            Scaffold { padding ->
                NavGraph(
                    navController = navController,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    } else {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    SidebarContent(
                        currentUser = currentUser,
                        selectedScreen = activeScreen,
                        layawayOverdueCount = layawayOverdueCount,
                        paluwaganDueCount = paluwaganDueCount,
                        onScreenSelected = { screen ->
                            onScreenSelected(screen)
                            scope.launch { drawerState.close() }
                        },
                        onLogout = onLogout,
                        syncStatus = syncStatus,
                        deviceRole = deviceRole,
                        isServerRunning = isServerRunning,
                        onSyncTap = onSyncTap,
                    )
                }
            },
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(activeScreen.label) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Open menu",
                                )
                            }
                        },
                    )
                },
            ) { padding ->
                NavGraph(
                    navController = navController,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}
