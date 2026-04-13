package com.ykfj.inventory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.ykfj.inventory.ui.auth.LoginScreen
import com.ykfj.inventory.ui.auth.SessionManager
import com.ykfj.inventory.ui.navigation.NavGraph
import com.ykfj.inventory.ui.navigation.Screen
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

                // Idle timeout check
                LaunchedEffect(currentUser) {
                    while (currentUser != null && isActive) {
                        delay(60_000) // check every minute
                        if (sessionManager.isSessionExpired()) {
                            sessionManager.logout()
                        }
                    }
                }

                if (currentUser == null) {
                    LoginScreen(onLoginSuccess = { /* state auto-updates via Flow */ })
                } else {
                    AppShell(
                        isExpandedScreen = isExpandedScreen,
                        sessionManager = sessionManager,
                    )
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppShell(
    isExpandedScreen: Boolean,
    sessionManager: SessionManager,
) {
    val navController = rememberNavController()
    val currentUser by sessionManager.currentUser.collectAsStateWithLifecycle()
    var selectedScreen by rememberSaveable { mutableStateOf(Screen.Inventory.route) }

    val activeScreen = Screen.allScreens.firstOrNull { it.route == selectedScreen }
        ?: Screen.Inventory

    val onScreenSelected: (Screen) -> Unit = { screen ->
        selectedScreen = screen.route
        navController.navigate(screen.route) {
            popUpTo(Screen.Inventory.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val onLogout: () -> Unit = { sessionManager.logout() }

    // TODO: Wire real badge counts from DAOs in Phase 2+
    val layawayOverdueCount = 0
    val paluwaganDueCount = 0

    if (isExpandedScreen) {
        Row(modifier = Modifier.fillMaxSize()) {
            SidebarContent(
                currentUser = currentUser,
                selectedScreen = activeScreen,
                layawayOverdueCount = layawayOverdueCount,
                paluwaganDueCount = paluwaganDueCount,
                onScreenSelected = onScreenSelected,
                onLogout = onLogout,
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
