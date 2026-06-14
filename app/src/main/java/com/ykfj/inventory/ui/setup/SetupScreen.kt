package com.ykfj.inventory.ui.setup

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
import com.ykfj.inventory.ui.categories.CategoriesScreen
import com.ykfj.inventory.ui.suppliers.SuppliersScreen

/**
 * Container for low-frequency reference data: Categories and Suppliers. Reuses both
 * existing screens as-is under a tab switcher. Admin/Manager only (gated by the
 * sidebar entry's allowedRoles).
 */
@Composable
fun SetupScreen() {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf("Categories", "Suppliers")

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) },
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                0 -> CategoriesScreen()
                else -> SuppliersScreen()
            }
        }
    }
}
