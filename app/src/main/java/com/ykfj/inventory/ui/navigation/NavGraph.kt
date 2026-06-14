package com.ykfj.inventory.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ykfj.inventory.ui.analytics.AnalyticsScreen
import com.ykfj.inventory.ui.goldpurchase.AddGoldPurchaseModal
import com.ykfj.inventory.ui.goldpurchase.GoldPurchaseDetailScreen
import com.ykfj.inventory.ui.goldpurchase.GoldPurchasesScreen
import com.ykfj.inventory.ui.paluwagan.PaluwaganDetailScreen
import com.ykfj.inventory.ui.paluwagan.PaluwaganScreen
import com.ykfj.inventory.ui.customers.CustomerDetailScreen
import com.ykfj.inventory.ui.customers.CustomersScreen
import com.ykfj.inventory.ui.dailycash.DailyCashScreen
import com.ykfj.inventory.ui.inventory.AddItemModal
import com.ykfj.inventory.ui.inventory.InventoryScreen
import com.ykfj.inventory.ui.inventory.ProductDetailScreen
import com.ykfj.inventory.ui.layaway.CustomerLayawayDetailScreen
import com.ykfj.inventory.ui.layaway.LayawayDetailScreen
import com.ykfj.inventory.ui.layaway.LayawayScreen
import com.ykfj.inventory.ui.metalrates.MetalRatesScreen
import com.ykfj.inventory.ui.settings.SettingsScreen
import com.ykfj.inventory.ui.settings.activity.ActivityLogScreen
import com.ykfj.inventory.ui.settings.archive.ArchiveManagerScreen
import com.ykfj.inventory.ui.settings.backup.BackupScreen
import com.ykfj.inventory.ui.settings.users.UserManagementScreen
import com.ykfj.inventory.ui.setup.SetupScreen
import com.ykfj.inventory.ui.sold.SoldArchiveScreen
import com.ykfj.inventory.ui.stocklosses.StockLossesScreen

// Fast fade-through for every screen/tab change. Pure alpha (no slide/scale/relayout),
// so it's GPU-cheap and ~3× snappier than Compose Navigation's 700ms default fade.
private const val NAV_FADE_IN_MS = 250
private const val NAV_FADE_OUT_MS = 200

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Inventory.route,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = tween(NAV_FADE_IN_MS)) },
        exitTransition = { fadeOut(animationSpec = tween(NAV_FADE_OUT_MS)) },
        popEnterTransition = { fadeIn(animationSpec = tween(NAV_FADE_IN_MS)) },
        popExitTransition = { fadeOut(animationSpec = tween(NAV_FADE_OUT_MS)) },
    ) {
        Screen.allScreens.forEach { screen ->
            composable(screen.route) {
                when (screen) {
                    Screen.Inventory -> InventoryScreen(
                        onNavigateToAddItem = { navController.navigate("add_item") },
                        onNavigateToDetail = { productId -> navController.navigate("product_detail/$productId") },
                    )
                    Screen.MetalRates -> MetalRatesScreen()
                    Screen.Setup -> SetupScreen()
                    Screen.Customers -> CustomersScreen(
                        onNavigateToDetail = { id ->
                            navController.navigate("customer_detail/$id")
                        },
                    )
                    Screen.GoldPurchases -> GoldPurchasesScreen(
                        onNavigateToAdd = { navController.navigate("add_gold_purchase") },
                        onNavigateToDetail = { id -> navController.navigate("gold_purchase_detail/$id") },
                    )
                    Screen.SoldArchive -> SoldArchiveScreen()
                    Screen.StockLosses -> StockLossesScreen(
                        onNavigateToProduct = { productId ->
                            navController.navigate("product_detail_readonly/$productId")
                        },
                    )
                    Screen.Layaway -> LayawayScreen(
                        onNavigateToCustomerLayaway = { customerId ->
                            navController.navigate("customer_layaway/$customerId")
                        },
                    )
                    Screen.Paluwagan -> PaluwaganScreen(
                        onNavigateToDetail = { groupId ->
                            navController.navigate("paluwagan_detail/$groupId")
                        },
                    )
                    Screen.DailyCash -> DailyCashScreen()
                    Screen.Analytics -> AnalyticsScreen()
                    Screen.Settings -> SettingsScreen(
                        onNavigateToUserManagement = { navController.navigate("user_management") },
                        onNavigateToArchiveManager = { navController.navigate("archive_manager") },
                        onNavigateToBackup = { navController.navigate("backup") },
                        onNavigateToActivityLog = { navController.navigate("activity_log") },
                    )
                    else -> PlaceholderScreen(title = screen.label)
                }
            }
        }

        composable(
            route = "customer_detail/{customerId}",
            arguments = listOf(navArgument("customerId") { type = NavType.StringType }),
        ) {
            CustomerDetailScreen(onNavigateUp = { navController.navigateUp() })
        }

        composable(route = "add_item") {
            AddItemModal(
                onNavigateUp = { navController.navigateUp() },
                onSaved = { navController.navigateUp() },
            )
        }

        composable(
            route = "edit_item/{productId}",
            arguments = listOf(navArgument("productId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val originalProductId = backStackEntry.arguments?.getString("productId").orEmpty()
            AddItemModal(
                onNavigateUp = { navController.navigateUp() },
                onSaved = { savedProductId ->
                    navController.navigate("product_detail/$savedProductId") {
                        popUpTo("product_detail/$originalProductId") { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = "product_detail/{productId}",
            arguments = listOf(navArgument("productId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val pickedCustomerId by backStackEntry.savedStateHandle
                .getStateFlow<String?>("picked_customer_id", null)
                .collectAsStateWithLifecycle()
            ProductDetailScreen(
                onNavigateUp = { navController.navigateUp() },
                onNavigateToEdit = { productId ->
                    navController.navigate("edit_item/$productId")
                },
                onNavigateToCustomers = {
                    navController.navigate("customers_with_back")
                },
                pickedCustomerId = pickedCustomerId,
                onPickedCustomerConsumed = {
                    backStackEntry.savedStateHandle.remove<String>("picked_customer_id")
                },
            )
        }

        composable(
            route = "product_detail_readonly/{productId}",
            arguments = listOf(navArgument("productId") { type = NavType.StringType }),
        ) {
            ProductDetailScreen(
                onNavigateUp = { navController.navigateUp() },
                onNavigateToEdit = {},
                onNavigateToCustomers = {},
                readOnly = true,
            )
        }

        composable(
            route = "layaway_detail/{layawayId}",
            arguments = listOf(navArgument("layawayId") { type = NavType.StringType }),
        ) {
            LayawayDetailScreen(onNavigateUp = { navController.navigateUp() })
        }

        composable(
            route = "customer_layaway/{customerId}",
            arguments = listOf(navArgument("customerId") { type = NavType.StringType }),
        ) {
            CustomerLayawayDetailScreen(
                onNavigateUp = { navController.navigateUp() },
                onNavigateToProduct = { productId -> navController.navigate("product_detail_readonly/$productId") },
            )
        }

        composable(
            route = "paluwagan_detail/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) {
            PaluwaganDetailScreen(onNavigateUp = { navController.navigateUp() })
        }

        composable(route = "user_management") {
            UserManagementScreen(onNavigateUp = { navController.navigateUp() })
        }

        composable(route = "archive_manager") {
            ArchiveManagerScreen(onNavigateUp = { navController.navigateUp() })
        }

        composable(route = "backup") {
            BackupScreen(onNavigateUp = { navController.navigateUp() })
        }

        composable(route = "activity_log") {
            ActivityLogScreen(onNavigateUp = { navController.navigateUp() })
        }

        composable(route = "customers_with_back") {
            CustomersScreen(
                onNavigateToDetail = { id -> navController.navigate("customer_detail/$id") },
                onNavigateUp = { navController.navigateUp() },
                onCustomerPicked = { customer ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("picked_customer_id", customer.id)
                    navController.navigateUp()
                },
            )
        }

        composable(route = "add_gold_purchase") { backStackEntry ->
            val pickedCustomerId by backStackEntry.savedStateHandle
                .getStateFlow<String?>("picked_customer_id", null)
                .collectAsStateWithLifecycle()
            AddGoldPurchaseModal(
                onNavigateUp = { navController.navigateUp() },
                onSaved = { navController.navigate("gold_purchase_detail/$it") {
                    popUpTo("add_gold_purchase") { inclusive = true }
                } },
                onNavigateToCustomers = { navController.navigate("customers_with_back") },
                pickedCustomerId = pickedCustomerId,
                onPickedCustomerConsumed = {
                    backStackEntry.savedStateHandle.remove<String>("picked_customer_id")
                },
            )
        }

        composable(
            route = "gold_purchase_detail/{purchaseId}",
            arguments = listOf(navArgument("purchaseId") { type = NavType.StringType }),
        ) {
            GoldPurchaseDetailScreen(onNavigateUp = { navController.navigateUp() })
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
