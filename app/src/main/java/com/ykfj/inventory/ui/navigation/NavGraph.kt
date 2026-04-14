package com.ykfj.inventory.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.ykfj.inventory.ui.categories.CategoriesScreen
import com.ykfj.inventory.ui.metalrates.MetalRatesScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Inventory.route,
        modifier = modifier,
    ) {
        Screen.allScreens.forEach { screen ->
            composable(screen.route) {
                when (screen) {
                    Screen.MetalRates -> MetalRatesScreen()
                    Screen.Categories -> CategoriesScreen()
                    else -> PlaceholderScreen(title = screen.label)
                }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}
