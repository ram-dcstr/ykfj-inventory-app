package com.ykfj.inventory.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.ui.graphics.vector.ImageVector
import com.ykfj.inventory.data.local.db.enums.UserRole

/** Sidebar zone: the flat top list of daily tools vs. the Admin/Manager "Manage" section. */
enum class SidebarGroup { MAIN, MANAGE }

sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector,
    /** Which sidebar zone this screen lives in. */
    val group: SidebarGroup = SidebarGroup.MAIN,
    /** Roles that can see this screen in the sidebar. Empty = all roles. */
    val allowedRoles: Set<UserRole> = emptySet(),
) {
    // ── Main: everyday operational screens (all roles unless noted) ──────────────
    data object Inventory : Screen("inventory", "Inventory", Icons.Default.Inventory2)
    data object GoldPurchases : Screen("gold_purchases", "Gold Purchases", Icons.Default.ShoppingCart)
    data object Layaway : Screen("layaway", "Layaway", Icons.Default.Handshake)
    data object Paluwagan : Screen("paluwagan", "Paluwagan", Icons.Default.Groups)
    data object DailyCash : Screen("daily_cash", "Daily Cash", Icons.Default.AccountBalanceWallet)
    data object Customers : Screen("customers", "Customers", Icons.Default.People)
    data object SoldArchive : Screen("sold_archive", "Sold Archive", Icons.Default.Storefront)

    /** Damaged / Melted / Write-offs combined — Write-offs tab is Admin/Manager only. */
    data object StockLosses : Screen("stock_losses", "Stock Losses", Icons.Default.ReportProblem)

    // ── Manage: low-frequency config & reports (Admin / Manager) ─────────────────
    data object MetalRates : Screen(
        "metal_rates", "Metal Rates", Icons.Default.CurrencyExchange,
        group = SidebarGroup.MANAGE,
        allowedRoles = setOf(UserRole.ADMIN, UserRole.MANAGER),
    )
    data object Analytics : Screen(
        "analytics", "Analytics", Icons.Default.Analytics,
        group = SidebarGroup.MANAGE,
        allowedRoles = setOf(UserRole.ADMIN, UserRole.MANAGER),
    )

    /** Categories + Suppliers combined. */
    data object Setup : Screen(
        "setup", "Setup", Icons.Default.Tune,
        group = SidebarGroup.MANAGE,
        allowedRoles = setOf(UserRole.ADMIN, UserRole.MANAGER),
    )
    data object Settings : Screen(
        "settings", "Settings", Icons.Default.Settings,
        group = SidebarGroup.MANAGE,
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
                // Main (order = sidebar order)
                Inventory,
                GoldPurchases,
                Layaway,
                Paluwagan,
                DailyCash,
                Customers,
                SoldArchive,
                StockLosses,
                // Manage
                MetalRates,
                Analytics,
                Setup,
                Settings,
            )
        }

        fun sidebarItemsFor(role: UserRole): List<Screen> =
            allScreens.filter { it.isVisibleTo(role) }
    }
}
