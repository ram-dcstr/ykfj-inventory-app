package com.ykfj.inventory.ui.analytics

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDayPicker by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }

    // Each time the screen is opened, snap the day/month filters back to the
    // current period — a previously-browsed date shouldn't linger on return.
    LaunchedEffect(Unit) { viewModel.resetToToday() }

    // Launch share intent when export URI arrives
    LaunchedEffect(state.exportedUri) {
        state.exportedUri?.let { uri ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Open CSV with…"))
            viewModel.clearExport()
        }
    }

    LaunchedEffect(state.exportError) {
        state.exportError?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearExport()
        }
    }

    SnackbarHost(snackbarHost)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Analytics", style = MaterialTheme.typography.headlineSmall)

        // ── Daily Sales ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { showDayPicker = true }) {
                Text(SimpleDateFormat("MMMM d, yyyy", Locale.US).format(Date(state.selectedDayMillis)))
            }
            ExportButton(
                loading = state.isExporting,
                onClick = viewModel::exportDayCsv,
                label = "Day CSV",
            )
        }
        SalesSummaryCard(title = "Daily Sales", summary = state.dailySummary)
        GoldTradingCard(title = "Daily Gold Trading", summary = state.dailyGoldTrading)

        // ── Monthly Sales ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonthNavigator(
                year = state.selectedYear,
                month = state.selectedMonth,
                onPrev = {
                    val cal = Calendar.getInstance().apply {
                        set(state.selectedYear, state.selectedMonth, 1)
                        add(Calendar.MONTH, -1)
                    }
                    viewModel.selectMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
                },
                onNext = {
                    val cal = Calendar.getInstance().apply {
                        set(state.selectedYear, state.selectedMonth, 1)
                        add(Calendar.MONTH, 1)
                    }
                    viewModel.selectMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
                },
            )
            ExportButton(
                loading = state.isExporting,
                onClick = viewModel::exportMonthCsv,
                label = "Month CSV",
            )
        }
        SalesSummaryCard(title = "Monthly Sales", summary = state.monthlySummary)
        GoldTradingCard(title = "Monthly Gold Trading", summary = state.monthlyGoldTrading)
        TopCategoriesCard(summary = state.monthlySummary)

        // ── Inventory / Layaway / Paluwagan ───────────────────────────────────
        InventorySummaryCard(summary = state.inventorySummary)
        LayawayOutstandingCard(summary = state.inventorySummary)
        PaluwaganSummaryCard(summary = state.inventorySummary)
    }

    if (showDayPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = state.selectedDayMillis)
        DatePickerDialog(
            onDismissRequest = { showDayPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { viewModel.selectDay(it) }
                    showDayPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDayPicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun ExportButton(loading: Boolean, onClick: () -> Unit, label: String) {
    TextButton(onClick = onClick, enabled = !loading) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Default.FileDownload, contentDescription = null,
                modifier = Modifier.size(16.dp))
        }
        Text(" $label", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MonthNavigator(year: Int, month: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    val label = SimpleDateFormat("MMMM yyyy", Locale.US).format(
        Calendar.getInstance().apply { set(year, month, 1) }.time,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.Default.ChevronLeft, "Previous month") }
        Text(label, style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = onNext) { Icon(Icons.Default.ChevronRight, "Next month") }
    }
}
