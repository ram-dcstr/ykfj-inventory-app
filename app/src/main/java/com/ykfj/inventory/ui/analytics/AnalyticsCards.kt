package com.ykfj.inventory.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ykfj.inventory.domain.model.GoldTradingSummary
import com.ykfj.inventory.domain.model.InventorySummary
import com.ykfj.inventory.domain.model.SalesSummary
import com.ykfj.inventory.util.CurrencyFormatter

@Composable
fun SalesSummaryCard(title: String, summary: SalesSummary, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            MetricRow("Items Sold", summary.itemCount.toString())
            MetricRow("Revenue", CurrencyFormatter.format(summary.revenue))
            MetricRow("Capital Cost", CurrencyFormatter.format(summary.capital))
            MetricRow("Profit", CurrencyFormatter.format(summary.profit), highlight = true)
        }
    }
}

@Composable
fun GoldTradingCard(title: String, summary: GoldTradingSummary, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            MetricRow("Items Sold to Supplier", summary.itemsSold.toString())
            MetricRow("Supplier Revenue", CurrencyFormatter.format(summary.revenue))
            MetricRow("Gold Profit", CurrencyFormatter.format(summary.profit), highlight = true)
        }
    }
}

@Composable
fun TopCategoriesCard(summary: SalesSummary, modifier: Modifier = Modifier) {
    if (summary.topCategories.isEmpty()) return
    Card(modifier = modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Top Categories (this month)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            summary.topCategories.forEachIndexed { i, entry ->
                if (i > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                MetricRow(entry.categoryName, "${entry.count} sold")
            }
        }
    }
}

@Composable
fun InventorySummaryCard(summary: InventorySummary, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Inventory Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            MetricRow("Active Items", summary.totalItems.toString())
            MetricRow("Total Capital Value", CurrencyFormatter.format(summary.totalCapitalValue))
        }
    }
}

@Composable
fun LayawayOutstandingCard(summary: InventorySummary, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Layaway Outstanding",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                CurrencyFormatter.format(summary.layawayOutstanding),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Total balance owed across all active layaways",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun PaluwaganSummaryCard(summary: InventorySummary, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Paluwagan Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            MetricRow("Active Groups", summary.activePaluwaganGroups.toString())
            MetricRow("Total Collected", CurrencyFormatter.format(summary.paluwaganTotalCollected))
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (highlight) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
        )
    }
}
