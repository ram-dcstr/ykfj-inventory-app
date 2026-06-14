package com.ykfj.inventory.ui.paluwagan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykfj.inventory.data.local.db.enums.PaluwaganGroupStatus
import com.ykfj.inventory.data.local.db.enums.PaluwaganPaymentStatus
import com.ykfj.inventory.data.local.db.enums.PaymentMethod
import com.ykfj.inventory.domain.model.Customer
import com.ykfj.inventory.domain.model.PaluwaganGroup
import com.ykfj.inventory.domain.model.PaluwaganPayment
import com.ykfj.inventory.ui.customers.CustomerAutoSuggestViewModel
import com.ykfj.inventory.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaluwaganDetailScreen(
    onNavigateUp: () -> Unit,
    viewModel: PaluwaganDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigateBack by viewModel.navigateBack.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAdvanceConfirm by remember { mutableStateOf(false) }
    var paymentTarget by remember { mutableStateOf<Pair<SlotRow, Int>?>(null) }
    var editSlotTarget by remember { mutableStateOf<SlotRow?>(null) }
    var historySlot by remember { mutableStateOf<SlotRow?>(null) }
    var editPaymentTarget by remember { mutableStateOf<PaluwaganPayment?>(null) }
    var collectPotSlot by remember { mutableStateOf<SlotRow?>(null) }

    LaunchedEffect(navigateBack) {
        if (navigateBack) onNavigateUp()
    }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHost.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.group?.name ?: "Paluwagan Detail") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isAdminOrManager &&
                        state.group?.status == PaluwaganGroupStatus.ACTIVE &&
                        state.slotRows.size < (state.group?.totalSlots ?: 0)
                    ) {
                        IconButton(onClick = { showAddMemberDialog = true }) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Add member")
                        }
                    }
                    if (state.isAdmin) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete group",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.group == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text("Group not found") }

            else -> PaluwaganDetailContent(
                state = state,
                group = state.group!!,
                onRecordPayment = { slot, round -> paymentTarget = Pair(slot, round) },
                onAdvanceRound = { showAdvanceConfirm = true },
                onCompleteGroup = { viewModel.completeGroup() },
                onNavigateUp = onNavigateUp,
                onReorderMembers = { from, to -> viewModel.reorderMembers(from, to) },
                onEditSlot = { slot -> editSlotTarget = slot },
                onViewHistory = { slot -> historySlot = slot },
                onCollectPot = { slot -> collectPotSlot = slot },
                modifier = Modifier.padding(padding),
            )
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────

    paymentTarget?.let { (slot, round) ->
        RecordPaymentDialog(
            slotRow = slot,
            roundNumber = round,
            defaultAmount = state.group?.contributionAmount ?: 0.0,
            totalSlots = state.group?.totalSlots ?: 0,
            onConfirm = { amount, paymentDate, paymentMethod, notes ->
                viewModel.recordPayment(
                    slotId = slot.slotId,
                    customerId = slot.customerId,
                    roundNumber = round,
                    amountPaid = amount,
                    paymentDate = paymentDate,
                    paymentMethod = paymentMethod,
                    notes = notes,
                )
                paymentTarget = null
            },
            onDismiss = { paymentTarget = null },
        )
    }

    historySlot?.let { slot ->
        val customerSlots = state.slotRows
            .filter { it.customerId == slot.customerId }
            .sortedBy { it.position }
        val slotBadge = if (customerSlots.size > 1)
            "S${customerSlots.indexOfFirst { it.slotId == slot.slotId } + 1}"
        else null
        MemberPaymentHistoryDialog(
            slotRow = slot,
            slotBadge = slotBadge,
            currentRound = state.group?.currentRound ?: 0,
            isAdmin = state.isAdmin,
            isAdminOrManager = state.isAdminOrManager,
            groupStatus = state.group?.status,
            onEditPayment = { payment -> editPaymentTarget = payment },
            onRecordPayment = { roundNumber ->
                historySlot = null
                paymentTarget = Pair(slot, roundNumber)
            },
            onDismiss = { historySlot = null },
        )
    }

    collectPotSlot?.let { slot ->
        CollectPotDialog(
            slotRow = slot,
            onConfirm = { date, payoutChannel ->
                viewModel.recordPotCollection(slot.slotId, date, payoutChannel)
                collectPotSlot = null
            },
            onDismiss = { collectPotSlot = null },
        )
    }

    editPaymentTarget?.let { payment ->
        EditPaymentDialog(
            payment = payment,
            contributionAmount = state.group?.contributionAmount ?: 0.0,
            onConfirm = { amount, date, method, notes ->
                viewModel.editPayment(payment.id, payment.roundNumber, amount, date, method, notes)
                editPaymentTarget = null
            },
            onDismiss = { editPaymentTarget = null },
        )
    }

    editSlotTarget?.let { slot ->
        EditSlotCustomerDialog(
            slotRow = slot,
            onConfirm = { newCustomerId ->
                viewModel.replaceSlotCustomer(slot.slotId, newCustomerId)
                editSlotTarget = null
            },
            onDismiss = { editSlotTarget = null },
        )
    }

    if (showAddMemberDialog) {
        AddMemberDialog(
            currentMemberCount = state.slotRows.size,
            totalSlots = state.group?.totalSlots ?: 0,
            onConfirm = { customerIds ->
                viewModel.addMembers(customerIds)
                showAddMemberDialog = false
            },
            onDismiss = { showAddMemberDialog = false },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Group") },
            text = {
                Text(
                    "Delete \"${state.group?.name}\"? This cannot be undone. " +
                        "All members and payment records will be removed.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteGroup(); showDeleteConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    if (showAdvanceConfirm) {
        val group = state.group
        val label = when {
            group == null -> "Advance Round"
            group.currentRound == 0 -> "Start Round 1"
            group.currentRound + 1 == group.totalSlots -> "Start Final Round"
            else -> "Advance Round"
        }
        AlertDialog(
            onDismissRequest = { showAdvanceConfirm = false },
            title = { Text(label) },
            text = {
                val next = (group?.currentRound ?: 0) + 1
                Text(
                    "Move to round $next of ${group?.totalSlots}? " +
                        "Payment rows will be seeded for all ${state.slotRows.size} members.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.advanceRound(); showAdvanceConfirm = false }) {
                    Text(label)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdvanceConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PaluwaganDetailContent(
    state: PaluwaganDetailUiState,
    group: PaluwaganGroup,
    onRecordPayment: (SlotRow, Int) -> Unit,
    onAdvanceRound: () -> Unit,
    onCompleteGroup: () -> Unit,
    onNavigateUp: () -> Unit,
    onReorderMembers: (fromIndex: Int, toIndex: Int) -> Unit,
    onEditSlot: (SlotRow) -> Unit,
    onViewHistory: (SlotRow) -> Unit,
    onCollectPot: (SlotRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canReorder = state.isAdminOrManager && group.status == PaluwaganGroupStatus.ACTIVE
    var completing by remember { mutableStateOf(false) }

    // Show flash briefly then navigate back
    LaunchedEffect(completing) {
        if (completing) {
            delay(650)
            onNavigateUp()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Group info ────────────────────────────────────────────────────────
        Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val groupDateFmt = remember {
                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                }
                InfoRow("Start Date", groupDateFmt.format(Date(group.startDate)))
                InfoRow("Contribution", CurrencyFormatter.format(group.contributionAmount))
                InfoRow("Interval", "Every ${group.frequencyDays} days")
                InfoRow("Total Slots", group.totalSlots.toString())
                InfoRow("Pot Money", CurrencyFormatter.format(group.contributionAmount * group.totalSlots))
                InfoRow("Status", group.status.name.lowercase().replaceFirstChar { it.uppercase() })
                group.notes?.let { InfoRow("Notes", it) }
            }
        }

        // ── Round progress bar ────────────────────────────────────────────────
        RoundProgressBar(
            currentRound = group.currentRound,
            totalSlots = group.totalSlots,
        )

        // ── Current round callout ─────────────────────────────────────────────
        if (group.currentRound > 0 && group.status == PaluwaganGroupStatus.ACTIVE) {
            CurrentRoundCallout(
                group = group,
                slotRows = state.slotRows,
            )
        }

        // ── Merged member + payment list ──────────────────────────────────────
        if (state.slotRows.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Members", style = MaterialTheme.typography.titleSmall)
                if (canReorder && state.slotRows.size >= 2) {
                    Text(
                        "Long-press to drag",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                MergedMemberList(
                    slots = state.slotRows,
                    currentRound = group.currentRound,
                    totalSlots = group.totalSlots,
                    groupStartDate = group.startDate,
                    frequencyDays = group.frequencyDays,
                    canReorder = canReorder,
                    isAdmin = state.isAdmin,
                    isAdminOrManager = state.isAdminOrManager,
                    groupStatus = group.status,
                    onReorder = onReorderMembers,
                    onEditSlot = onEditSlot,
                    onRecordPayment = onRecordPayment,
                    onViewHistory = onViewHistory,
                    onCollectPot = onCollectPot,
                )
            }
        } else {
            Text(
                "No members yet. Tap + to add.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── Payment history (past rounds) ─────────────────────────────────────
        if (group.currentRound > 1) {
            PaymentHistorySection(
                slotRows = state.slotRows,
                currentRound = group.currentRound,
                contributionAmount = group.contributionAmount,
            )
        }

        // ── Completion flash ──────────────────────────────────────────────────
        AnimatedVisibility(
            visible = completing,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(250)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF1B5E20),
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Group completed!",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF1B5E20),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // ── Actions ───────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.isAdminOrManager &&
                group.status == PaluwaganGroupStatus.ACTIVE &&
                !completing,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(200)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider()
                if (state.slotRows.isNotEmpty()) {
                    val slotsFilledUp = state.slotRows.size == group.totalSlots
                    val isLastRound = group.currentRound == group.totalSlots
                    if (!isLastRound) {
                        val advanceLabel = when {
                            group.currentRound == 0 -> "Start Round 1"
                            group.currentRound + 1 == group.totalSlots -> "Start Final Round"
                            else -> "Advance to Next Round"
                        }
                        Button(
                            onClick = onAdvanceRound,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = slotsFilledUp,
                        ) {
                            Text(advanceLabel)
                        }
                        if (!slotsFilledUp) {
                            Text(
                                "Fill all ${group.totalSlots} member slots before starting.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
                val allRoundsDone = group.currentRound == group.totalSlots
                val hasUnpaidPayments = state.slotRows.any { slot ->
                    slot.payments.values.any { it?.status == PaluwaganPaymentStatus.UNPAID }
                }
                val unclaimedCount = state.slotRows.count { it.potCollectedAt == null }
                val hasUnclaimedPots = unclaimedCount > 0
                val canComplete = allRoundsDone && !hasUnpaidPayments && !hasUnclaimedPots
                OutlinedButton(
                    onClick = { completing = true; onCompleteGroup() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canComplete,
                ) {
                    Text("Mark as Completed")
                }
                when {
                    !allRoundsDone -> Text(
                        "Complete all ${group.totalSlots} rounds to mark as completed.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    hasUnpaidPayments -> Text(
                        "Clear all unpaid payments before marking as completed.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    hasUnclaimedPots -> Text(
                        "$unclaimedCount member${if (unclaimedCount > 1) "s have" else " has"} not claimed their pot yet.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ── Merged member + payment list ─────────────────────────────────────────────

@Composable
private fun MergedMemberList(
    slots: List<SlotRow>,
    currentRound: Int,
    totalSlots: Int,
    groupStartDate: Long,
    frequencyDays: Int,
    canReorder: Boolean,
    isAdmin: Boolean,
    isAdminOrManager: Boolean,
    groupStatus: PaluwaganGroupStatus,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onEditSlot: (SlotRow) -> Unit,
    onRecordPayment: (SlotRow, Int) -> Unit,
    onViewHistory: (SlotRow) -> Unit,
    onCollectPot: (SlotRow) -> Unit,
) {
    val density = LocalDensity.current
    val itemHeightDp = 68.dp
    val itemHeightPx = with(density) { itemHeightDp.toPx() }
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    // collected = members who have had their actual pot collection date recorded
    // upcoming  = everyone else (including past collectors who haven't recorded yet)
    val collected = remember(slots) {
        slots.filter { it.potCollectedAt != null }.sortedBy { it.position }
    }
    val upcoming = remember(slots) {
        slots.filter { it.potCollectedAt == null }.sortedBy { it.position }
    }

    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val customerSlotIds = remember(slots) {
        slots.sortedBy { it.position }
            .groupBy { it.customerId }
            .mapValues { (_, s) -> s.map { it.slotId } }
    }

    // Drag is only active for upcoming rows; indices here are within `upcoming`.
    // We map back to the original `slots` indices before calling onReorder.
    val showDragHandles = canReorder && upcoming.size >= 2

    Column(modifier = Modifier.fillMaxWidth()) {

        // ── Upcoming / current collector (draggable) ──────────────────────────
        upcoming.forEachIndexed { idx, slot ->
            val isDragged = idx == draggedIndex
            val targetIdx = if (draggedIndex >= 0)
                (draggedIndex + (dragOffsetY / itemHeightPx).roundToInt())
                    .coerceIn(0, upcoming.lastIndex)
            else -1

            val translationY = when {
                isDragged -> dragOffsetY
                draggedIndex < 0 -> 0f
                draggedIndex < idx && idx <= targetIdx -> -itemHeightPx
                draggedIndex > idx && idx >= targetIdx -> itemHeightPx
                else -> 0f
            }

            val dragModifier = if (showDragHandles) {
                Modifier.pointerInput(idx) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { _ -> draggedIndex = idx; dragOffsetY = 0f },
                        onDrag = { _, d -> dragOffsetY += d.y },
                        onDragEnd = {
                            val to = (draggedIndex + (dragOffsetY / itemHeightPx).roundToInt())
                                .coerceIn(0, upcoming.lastIndex)
                            if (to != draggedIndex) {
                                // Map display indices → original slots indices
                                val originalFrom = slots.indexOf(upcoming[draggedIndex])
                                val originalTo = slots.indexOf(upcoming[to])
                                onReorder(originalFrom, originalTo)
                            }
                            draggedIndex = -1; dragOffsetY = 0f
                        },
                        onDragCancel = { draggedIndex = -1; dragOffsetY = 0f },
                    )
                }
            } else Modifier

            val collectDateMs = groupStartDate +
                (slot.position.toLong() * frequencyDays - 1) * 86_400_000L
            val collectDateLabel = remember(collectDateMs) { dateFmt.format(Date(collectDateMs)) }
            val isCollector = currentRound > 0 && slot.position == currentRound
            val payment = if (currentRound > 0) slot.payments[currentRound] else null
            // Next round this member can still pay. Spans the whole group (not just up
            // to the current round) so a member who has prepaid the current round can
            // keep paying forward. A round is payable if it has no row yet (null) or
            // its row is still UNPAID. The dialog caps the amount, so no overpay.
            val nextPayableRound = if (currentRound > 0) {
                (1..totalSlots).firstOrNull { r ->
                    val p = slot.payments[r]
                    p == null || p.status == PaluwaganPaymentStatus.UNPAID
                }
            } else null
            // Recording paluwagan payments is open to all roles per business rules.
            val canRecord = groupStatus == PaluwaganGroupStatus.ACTIVE &&
                nextPayableRound != null
            // Current round already covered but a *later* round is still open → offer
            // a "pay ahead" button next to the status badge.
            val currentCovered = payment?.status != null &&
                payment.status != PaluwaganPaymentStatus.UNPAID
            val payAhead = currentCovered &&
                nextPayableRound != null && nextPayableRound > currentRound

            val customerSlots = customerSlotIds[slot.customerId] ?: emptyList()
            val slotIndex = customerSlots.indexOf(slot.slotId)

            val overdueRounds = remember(slot.payments, currentRound) {
                (1 until currentRound).filter { r ->
                    slot.payments[r]?.status == PaluwaganPaymentStatus.UNPAID
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = itemHeightDp)
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer { this.translationY = translationY }
                    .then(dragModifier),
                color = when {
                    isDragged -> MaterialTheme.colorScheme.surfaceVariant
                    isCollector -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    else -> MaterialTheme.colorScheme.surface
                },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showDragHandles) {
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        "#${slot.position}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(26.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(
                                slot.customerName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCollector) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            if (customerSlots.size > 1) {
                                SlotBadge("S${slotIndex + 1}")
                            }
                        }
                        Text(
                            "Collects: $collectDateLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCollector) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (slot.originalCustomerName != null) {
                            PasaloIndicator(slot.originalCustomerName)
                        }
                        if (overdueRounds.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(top = 3.dp),
                            ) {
                                overdueRounds.forEach { round ->
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(Color(0xFFB71C1C), shape = CircleShape)
                                            .then(
                                                if (isAdminOrManager && groupStatus == PaluwaganGroupStatus.ACTIVE)
                                                    Modifier.clickable { onRecordPayment(slot, round) }
                                                else Modifier
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "R$round",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // The pot for a member's collection round can only be taken once
                    // EVERY slot has paid that round's contribution (PAID / LATE / PREPAID).
                    // Until the pot is actually full, the collect button stays hidden — this
                    // stops anyone from collecting right after a round starts, before any
                    // money is in.
                    val potRound = slot.position
                    val potRoundFullyPaid = slots.all { s ->
                        val st = s.payments[potRound]?.status
                        st != null && st != PaluwaganPaymentStatus.UNPAID
                    }
                    // Gift button: shown from the collector's OWN round onward (<=, not <)
                    // so the final member can be marked collected during the last round —
                    // otherwise their pot can never be recorded and the group can't be
                    // completed. Gated on the round (which the admin advances manually) AND
                    // on the pot being fully funded — NOT the scheduled calendar date, so a
                    // group can run ahead of schedule.
                    val canCollect = isAdminOrManager &&
                        currentRound > 0 &&
                        slot.position <= currentRound &&
                        potRoundFullyPaid
                    if (canCollect) {
                        IconButton(
                            onClick = { onCollectPot(slot) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.Redeem,
                                contentDescription = "Record pot collection",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    if (currentRound > 0) {
                        PaymentBadgeOrButton(
                            status = payment?.status,
                            canRecord = canRecord,
                            payAheadAvailable = payAhead,
                            onRecord = { onRecordPayment(slot, nextPayableRound ?: currentRound) },
                        )
                    }
                    IconButton(onClick = { onViewHistory(slot) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Info, contentDescription = "View payment history", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                    }
                    if (isAdmin) {
                        IconButton(onClick = { onEditSlot(slot) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Pasalo — replace member", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }

            if (idx < upcoming.lastIndex || collected.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }

        // ── Already collected pot ─────────────────────────────────────────────
        if (collected.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Already collected pot",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            collected.forEachIndexed { idx, slot ->
                val collectDateMs = groupStartDate +
                    (slot.position.toLong() * frequencyDays - 1) * 86_400_000L
                val collectDateLabel = remember(collectDateMs) { dateFmt.format(Date(collectDateMs)) }
                val payment = slot.payments[currentRound]
                val nextPayableRound = (1..totalSlots).firstOrNull { r ->
                    val p = slot.payments[r]
                    p == null || p.status == PaluwaganPaymentStatus.UNPAID
                }
                val canRecord = isAdminOrManager &&
                    groupStatus == PaluwaganGroupStatus.ACTIVE &&
                    nextPayableRound != null
                val currentCovered = payment?.status != null &&
                    payment.status != PaluwaganPaymentStatus.UNPAID
                val payAhead = currentCovered &&
                    nextPayableRound != null && nextPayableRound > currentRound

                val customerSlots = customerSlotIds[slot.customerId] ?: emptyList()
                val slotIndex = customerSlots.indexOf(slot.slotId)

                val overdueRounds = remember(slot.payments, currentRound) {
                    (1 until currentRound).filter { r ->
                        slot.payments[r]?.status == PaluwaganPaymentStatus.UNPAID
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(min = itemHeightDp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (showDragHandles) Spacer(Modifier.width(22.dp))

                        Text(
                            "#${slot.position}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(26.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Text(
                                    slot.customerName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (customerSlots.size > 1) {
                                    SlotBadge("S${slotIndex + 1}")
                                }
                                CollectedBadge()
                            }
                            val displayDate = if (slot.potCollectedAt != null)
                                dateFmt.format(Date(slot.potCollectedAt))
                            else collectDateLabel
                            Text(
                                "Collected: $displayDate" + if (slot.potCollectedAt != null) " ✓" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (slot.potCollectedAt != null) Color(0xFF1B5E20)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (slot.potCollectedAt != null) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            if (slot.originalCustomerName != null) {
                                PasaloIndicator(slot.originalCustomerName)
                            }
                            if (overdueRounds.isNotEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(top = 3.dp),
                                ) {
                                    overdueRounds.forEach { round ->
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .background(Color(0xFFB71C1C), shape = CircleShape)
                                                .then(
                                                    if (isAdminOrManager && groupStatus == PaluwaganGroupStatus.ACTIVE)
                                                        Modifier.clickable { onRecordPayment(slot, round) }
                                                    else Modifier
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                "R$round",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        PaymentBadgeOrButton(
                            status = payment?.status,
                            canRecord = canRecord,
                            payAheadAvailable = payAhead,
                            onRecord = { onRecordPayment(slot, nextPayableRound ?: currentRound) },
                        )
                        IconButton(onClick = { onViewHistory(slot) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Info, contentDescription = "View payment history", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                        }
                        if (isAdmin) {
                            IconButton(onClick = { onEditSlot(slot) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Pasalo — replace member", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }

                if (idx < collected.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun RoundProgressBar(
    currentRound: Int,
    totalSlots: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                if (currentRound == 0) "Not started" else "Round $currentRound of $totalSlots",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (currentRound == 0)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "$currentRound / $totalSlots",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (round in 1..totalSlots) {
                val isCompleted = round < currentRound
                val isCurrent = round == currentRound
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (isCurrent) 12.dp else 8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            when {
                                isCompleted -> MaterialTheme.colorScheme.primary
                                isCurrent -> Color(0xFFE65100)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun CurrentRoundCallout(
    group: PaluwaganGroup,
    slotRows: List<SlotRow>,
    modifier: Modifier = Modifier,
) {
    val collector = slotRows.find { it.position == group.currentRound }
    val collectorSlotBadge = if (collector != null) {
        val sameCustomerSlots = slotRows
            .filter { it.customerId == collector.customerId }
            .sortedBy { it.position }
        if (sameCustomerSlots.size > 1)
            "S${sameCustomerSlots.indexOfFirst { it.slotId == collector.slotId } + 1}"
        else null
    } else null
    val collectDateMs = group.startDate +
        (group.currentRound.toLong() * group.frequencyDays - 1) * 86_400_000L
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val collectDateLabel = remember(collectDateMs) { dateFmt.format(Date(collectDateMs)) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    "Round ${group.currentRound} of ${group.totalSlots}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    fontWeight = FontWeight.Medium,
                )
                if (collector != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "${collector.customerName} collects",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        if (collectorSlotBadge != null) {
                            SlotBadge(collectorSlotBadge)
                        }
                    }
                } else {
                    Text(
                        "No collector assigned yet",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    "Collect by",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
                Text(
                    collectDateLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

// ── Payment history (past rounds, collapsed by default) ───────────────────────

@Composable
private fun PaymentHistorySection(
    slotRows: List<SlotRow>,
    currentRound: Int,
    contributionAmount: Double,
) {
    var historyExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { historyExpanded = !historyExpanded }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Payment History", style = MaterialTheme.typography.titleSmall)
        Icon(
            imageVector = if (historyExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (historyExpanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (historyExpanded) {
        val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
        var expandedDetailRounds by remember { mutableStateOf(emptySet<Int>()) }

        Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                (currentRound - 1 downTo 1).forEach { round ->
                    val roundCollector = slotRows.find { it.position == round }
                    val roundPaid = slotRows.count {
                        val p = it.payments[round]
                        p?.status == PaluwaganPaymentStatus.PAID ||
                            p?.status == PaluwaganPaymentStatus.LATE ||
                            p?.status == PaluwaganPaymentStatus.PREPAID
                    }
                    val roundTotal = slotRows.sumOf {
                        val p = it.payments[round]
                        if (p != null && (p.status == PaluwaganPaymentStatus.PAID ||
                                p.status == PaluwaganPaymentStatus.LATE ||
                                p.status == PaluwaganPaymentStatus.PREPAID)) {
                            // Cap at contributionAmount so advance payments don't inflate the round total
                            minOf(p.amountPaid, contributionAmount)
                        } else 0.0
                    }
                    val hasMissing = slotRows.any {
                        it.payments[round]?.status == PaluwaganPaymentStatus.UNPAID
                    }
                    val detailExpanded = round in expandedDetailRounds

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedDetailRounds = if (detailExpanded)
                                    expandedDetailRounds - round else expandedDetailRounds + round
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text("Round $round", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                if (hasMissing) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                Color(0xFFB71C1C).copy(alpha = 0.1f),
                                                shape = MaterialTheme.shapes.extraSmall,
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            "Missing",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFB71C1C),
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                            roundCollector?.let {
                                Text(it.customerName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "$roundPaid / ${slotRows.size} paid",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (hasMissing) Color(0xFFB71C1C)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (roundTotal > 0) {
                                Text(
                                    CurrencyFormatter.format(roundTotal),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF1B5E20),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = if (detailExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (detailExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            slotRows.forEach { slot ->
                                val payment = slot.payments[round]
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(slot.customerName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                        if (payment?.paymentDate != null) {
                                            Text(
                                                buildString {
                                                    append(dateFormatter.format(Date(payment.paymentDate)))
                                                    payment.paymentMethod?.let { append("  •  ${it.label}") }
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    PaymentBadgeOrButton(
                                        status = payment?.status,
                                        canRecord = false,
                                        onRecord = {},
                                    )
                                }
                            }
                        }
                    }

                    if (round > 1) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}
