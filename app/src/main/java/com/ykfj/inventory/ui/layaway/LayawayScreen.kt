package com.ykfj.inventory.ui.layaway

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykfj.inventory.util.CurrencyFormatter

@Composable
fun LayawayScreen(
    onNavigateToCustomerLayaway: (customerId: String) -> Unit,
    viewModel: LayawayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHost.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::search,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search by customer or product…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = state.filter == LayawayFilter.Active,
                    onClick = { viewModel.setFilter(LayawayFilter.Active) },
                    label = { Text("Active", style = MaterialTheme.typography.labelMedium) },
                )
                FilterChip(
                    selected = state.filter == LayawayFilter.Completed,
                    onClick = { viewModel.setFilter(LayawayFilter.Completed) },
                    label = { Text("Completed", style = MaterialTheme.typography.labelMedium) },
                )
            }
            HorizontalDivider()

            when {
                state.isLoading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.filteredGroups.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when {
                            state.searchQuery.isNotBlank() -> "No results for \"${state.searchQuery}\"."
                            state.filter == LayawayFilter.Completed -> "No completed layaways."
                            else -> "No active layaways."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = state.filteredGroups, key = { it.customerId }) { group ->
                        CustomerLayawayGroupCard(
                            group = group,
                            onClick = { onNavigateToCustomerLayaway(group.customerId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLabel(text: String, color: androidx.compose.ui.graphics.Color, showWarningIcon: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showWarningIcon) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = color)
        }
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CustomerLayawayGroupCard(group: CustomerLayawayGroup, onClick: () -> Unit) {
    val overdueColor = MaterialTheme.colorScheme.errorContainer
    val normalColor = MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (group.isOverdue) overdueColor else normalColor,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Customer name + status badge (overdue / late / completed)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = group.customerName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                when {
                    group.isOverdue -> StatusLabel(
                        text = "OVERDUE",
                        color = MaterialTheme.colorScheme.error,
                        showWarningIcon = true,
                    )
                    group.latePaymentCount > 0 -> StatusLabel(
                        text = if (group.latePaymentCount == 1) "PAID LATE"
                        else "${group.latePaymentCount} PAID LATE",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // Product list (bullet per product)
            group.layaways.forEach { row ->
                Text(
                    text = "• ${row.productName}  ·  Qty ${row.quantity}  ·  ${CurrencyFormatter.format(row.remaining)} left",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Combined summary
            if (group.layaways.size > 1) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Total: ${CurrencyFormatter.format(group.totalAmount)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Remaining: ${CurrencyFormatter.format(group.totalRemaining)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
