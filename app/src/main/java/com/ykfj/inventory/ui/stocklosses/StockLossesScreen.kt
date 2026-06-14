package com.ykfj.inventory.ui.stocklosses

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykfj.inventory.ui.damaged.DamagedScreen
import com.ykfj.inventory.ui.stockadjustments.StockAdjustmentsScreen

/**
 * Container for everything "stock left without a sale": the existing Damaged screen
 * (with its own Active/Melted filter) and the Stock Write-offs list. Reuses both
 * screens as-is. The Write-offs tab is Admin/Manager only — Staff see just Damaged.
 */
@Composable
fun StockLossesScreen(
    onNavigateToProduct: (productId: String) -> Unit = {},
    viewModel: StockLossesViewModel = hiltViewModel(),
) {
    val isAdminOrManager by viewModel.isAdminOrManager.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    val tabs = if (isAdminOrManager) listOf("Damaged", "Write-offs") else listOf("Damaged")
    val tab = selectedTab.coerceIn(0, tabs.lastIndex)

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = tab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) },
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> DamagedScreen(onNavigateToProduct = onNavigateToProduct)
                else -> StockAdjustmentsScreen()
            }
        }
    }
}
