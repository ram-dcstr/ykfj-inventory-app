package com.ykfj.inventory.ui.customers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykfj.inventory.data.local.db.enums.LayawayStatus
import com.ykfj.inventory.data.local.db.enums.PaluwaganGroupStatus
import com.ykfj.inventory.data.local.db.enums.PaluwaganPaymentStatus
import com.ykfj.inventory.domain.model.Customer
import com.ykfj.inventory.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    onNavigateUp: () -> Unit,
    viewModel: CustomerDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.customer?.name ?: "Customer") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.customer == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text("Customer not found.") }

            else -> CustomerDetailContent(
                customer = state.customer!!,
                layaways = state.layaways,
                paluwaganEntries = state.paluwaganEntries,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun CustomerDetailContent(
    customer: Customer,
    layaways: List<CustomerLayawaySummary>,
    paluwaganEntries: List<CustomerPaluwaganEntry>,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Info", "Sales", "Layaway", "Paluwagan")

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) },
                )
            }
        }
        when (selectedTab) {
            0 -> CustomerInfoTab(customer)
            1 -> HistoryPlaceholder("Sales history available after Phase 3 ships.")
            2 -> CustomerLayawayTab(layaways)
            3 -> CustomerPaluwaganTab(paluwaganEntries)
        }
    }
}

@Composable
private fun CustomerInfoTab(customer: Customer) {
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        InfoRow(label = "Credit Score") {
            CreditScoreBadge(score = customer.creditScore)
            Text(
                text = " (${customer.creditScore})",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (!customer.mobile.isNullOrBlank()) InfoField("Mobile", customer.mobile)
        if (!customer.phone.isNullOrBlank()) InfoField("Phone", customer.phone)
        customer.birthday?.let { InfoField("Birthday", dateFormat.format(Date(it))) }
        if (!customer.address.isNullOrBlank()) InfoField("Address", customer.address)
        if (!customer.notes.isNullOrBlank()) InfoField("Notes", customer.notes)
    }
}

@Composable
private fun InfoField(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun InfoRow(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

@Composable
private fun CustomerLayawayTab(layaways: List<CustomerLayawaySummary>) {
    if (layaways.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No layaway records.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val dateSdf = remember { SimpleDateFormat("MMM d, yyyy", Locale.US) }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(layaways) { summary ->
            val statusColor = when (summary.status) {
                LayawayStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                LayawayStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
                LayawayStatus.CANCELLED -> MaterialTheme.colorScheme.error
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            summary.productName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            summary.status.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total: ${CurrencyFormatter.format(summary.total)}", style = MaterialTheme.typography.bodySmall)
                        Text("Paid: ${CurrencyFormatter.format(summary.totalPaid)}", style = MaterialTheme.typography.bodySmall)
                    }
                    when (summary.status) {
                        LayawayStatus.ACTIVE -> {
                            Text(
                                "Remaining: ${CurrencyFormatter.format(summary.remaining)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            summary.dueDate?.let {
                                val overdue = System.currentTimeMillis() > it
                                Text(
                                    "Due: ${dateSdf.format(Date(it))}${if (overdue) " — OVERDUE" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        LayawayStatus.COMPLETED -> summary.completionDate?.let {
                            Text(
                                "Completed: ${dateSdf.format(Date(it))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        LayawayStatus.CANCELLED -> {
                            summary.forfeitedAmount?.let {
                                Text(
                                    "Forfeited: ${CurrencyFormatter.format(it)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            summary.completionDate?.let {
                                Text(
                                    "Cancelled: ${dateSdf.format(Date(it))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (summary.transactions.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                        summary.transactions.forEach { tx ->
                            val isLate = summary.dueDate != null && tx.paymentDate > summary.dueDate
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${dateSdf.format(Date(tx.paymentDate))}  ${CurrencyFormatter.format(tx.amountPaid)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    if (isLate) "Late" else "On-time",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isLate) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Text(
                            "${summary.onTimeCount} on-time · ${summary.lateCount} late",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryPlaceholder(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CustomerPaluwaganTab(entries: List<CustomerPaluwaganEntry>) {
    if (entries.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No paluwagan memberships.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(entries, key = { it.slotId }) { entry ->
            CustomerPaluwaganCard(entry)
        }
    }
}

@Composable
private fun CustomerPaluwaganCard(entry: CustomerPaluwaganEntry) {
    val statusColor = when (entry.groupStatus) {
        PaluwaganGroupStatus.ACTIVE -> MaterialTheme.colorScheme.primary
        PaluwaganGroupStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
    }
    androidx.compose.material3.Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    entry.groupName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    entry.groupStatus.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                "Position #${entry.position} · ${CurrencyFormatter.format(entry.contributionAmount)} / round",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Round ${entry.currentRound} / ${entry.totalRounds}",
                style = MaterialTheme.typography.bodySmall,
            )
            if (entry.payments.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(entry.payments, key = { it.id }) { payment ->
                        RoundChip(round = payment.roundNumber, status = payment.status)
                    }
                }
                Text(
                    "${entry.paidCount} on-time · ${entry.lateCount} late",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RoundChip(round: Int, status: PaluwaganPaymentStatus) {
    val bgColor: Color
    val textColor: Color
    when (status) {
        PaluwaganPaymentStatus.PAID, PaluwaganPaymentStatus.PREPAID -> {
            bgColor = MaterialTheme.colorScheme.tertiary
            textColor = MaterialTheme.colorScheme.onTertiary
        }
        PaluwaganPaymentStatus.LATE -> {
            bgColor = MaterialTheme.colorScheme.error
            textColor = MaterialTheme.colorScheme.onError
        }
        PaluwaganPaymentStatus.UNPAID -> {
            bgColor = MaterialTheme.colorScheme.surfaceVariant
            textColor = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    Box(
        modifier = Modifier
            .size(width = 28.dp, height = 24.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$round",
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
        )
    }
}
