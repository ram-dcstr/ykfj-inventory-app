package com.ykfj.inventory.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.ui.graphics.vector.ImageVector
import com.ykfj.inventory.data.local.db.enums.UserRole

sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector,
    /** Roles that can see this screen in the sidebar. Empty = all roles. */
    val allowedRoles: Set<UserRole> = emptySet(),
) {
    data object Inventory : Screen("inventory", "Inventory", Icons.Default.Inventory2)
    data object SoldArchive : Screen("sold_archive", "Sold Archive", Icons.Default.Storefront)
    data object Layaway : Screen("layaway", "Layaway", Icons.Default.Handshake)
    data object Paluwagan : Screen("paluwagan", "Paluwagan", Icons.Default.Groups)
    data object Damaged : Screen("damaged", "Damaged / Scraps", Icons.Default.ReportProblem)
    data object MetalRates : Screen(
        "metal_rates", "Metal Rates", Icons.Default.CurrencyExchange,
        allowedRoles = setOf(UserRole.ADMIN, UserRole.MANAGER),
    )
    data object Categories : Screen(
        "categories", "Categories", Icons.Default.Category,
        allowedRoles = setOf(UserRole.ADMIN, UserRole.MANAGER),
    )
    data object Customers : Screen("customers", "Customers", Icons.Default.People)
    data object GoldPurchases : Screen("gold_purchases", "Gold Purchases", Icons.Default.ShoppingCart)
    data object DailyCash : Screen("daily_cash", "Daily Cash", Icons.Default.AccountBalanceWallet)
    data object Suppliers : Screen(
        "suppliers", "Suppliers", Icons.Default.LocalShipping,
        allowedRoles = setOf(UserRole.ADMIN, UserRole.MANAGER),
    )
    data object Analytics : Screen(
        "analytics", "Analytics", Icons.Default.Analytics,
        allowedRoles = setOf(UserRole.ADMIN, UserRole.MANAGER),
    )
    data object Settings : Screen(
        "settings", "Settings", Icons.Default.Settings,
        allowedRoles = setOf(UserRole.ADMIN),
    )

    fun isVisibleTo(role: UserRole): Boolean =
        allowedRoles.isEmpty() || role in allowedRoles

    companion object {
        // `by lazy` is required: the companion initializes before nested
        // `data object` members, so an eager `listOf(...)` would capture
        // nulls and crash at first access with NPE on Screen.getRoute().
        val allScreens: List<Screen> by lazy {
            listOf(
                Inventory,
                SoldArchive,
                Layaway,
                Paluwagan,
                Damaged,
                MetalRates,
                Categories,
                Customers,
                GoldPurchases,
                Suppliers,
                DailyCash,
                Analytics,
                Settings,
            )
        }

        fun sidebarItemsFor(role: UserRole): List<Screen> =
            allScreens.filter { it.isVisibleTo(role) }
    }
}
