package com.ykfj.inventory.ui.paluwagan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykfj.inventory.data.local.db.enums.PaluwaganPaymentStatus
import com.ykfj.inventory.data.local.db.enums.PaymentMethod
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.model.PaluwaganGroup
import com.ykfj.inventory.domain.model.PaluwaganPayment
import com.ykfj.inventory.domain.repository.CustomerRepository
import com.ykfj.inventory.domain.repository.PaluwaganRepository
import com.ykfj.inventory.domain.usecase.paluwagan.AddPaluwaganSlotUseCase
import com.ykfj.inventory.domain.usecase.paluwagan.AdvancePaluwaganRoundUseCase
import com.ykfj.inventory.domain.usecase.paluwagan.CompletePaluwaganGroupUseCase
import com.ykfj.inventory.domain.usecase.paluwagan.DeletePaluwaganGroupUseCase
import com.ykfj.inventory.domain.usecase.paluwagan.RecordPaluwaganPaymentUseCase
import com.ykfj.inventory.domain.usecase.paluwagan.ReorderPaluwaganSlotsUseCase
import com.ykfj.inventory.ui.auth.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SlotRow(
    val slotId: String,
    val customerId: String,
    val customerName: String,
    /** Non-null when this slot has been pasalo'd — name of the original holder. */
    val originalCustomerName: String? = null,
    val position: Int,
    /** roundNumber → payment (null = no row seeded yet for that round) */
    val payments: Map<Int, PaluwaganPayment?>,
    /** Actual date the collector physically received the pot. Null until recorded. */
    val potCollectedAt: Long? = null,
)

data class PaluwaganDetailUiState(
    val group: PaluwaganGroup? = null,
    val slotRows: List<SlotRow> = emptyList(),
    val isLoading: Boolean = true,
    val isAdmin: Boolean = false,
    val isAdminOrManager: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class PaluwaganDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val paluwaganRepository: PaluwaganRepository,
    private val customerRepository: CustomerRepository,
    private val addSlot: AddPaluwaganSlotUseCase,
    private val reorderSlots: ReorderPaluwaganSlotsUseCase,
    private val recordPayment: RecordPaluwaganPaymentUseCase,
    private val advanceRound: AdvancePaluwaganRoundUseCase,
    private val completeGroup: CompletePaluwaganGroupUseCase,
    private val deleteGroup: DeletePaluwaganGroupUseCase,
    private val sessionManager: SessionManager,
    private val snackbarController: com.ykfj.inventory.ui.components.SnackbarController,
) : ViewModel() {

    private val groupId: String = checkNotNull(savedStateHandle["groupId"])

    private val _error = MutableStateFlow<String?>(null)
    private val _navigateBack = MutableStateFlow(false)
    val navigateBack: StateFlow<Boolean> = _navigateBack.asStateFlow()

    val uiState: StateFlow<PaluwaganDetailUiState> = combine(
        paluwaganRepository.observeGroup(groupId),
        paluwaganRepository.observeSlots(groupId),
        paluwaganRepository.observePayments(groupId),
        sessionManager.currentUser,
    ) { group, slots, payments, user ->
        val allCustomerIds = (slots.map { it.customerId } +
            slots.mapNotNull { it.originalCustomerId }).distinct()
        val customerMap = allCustomerIds.associateWith { id ->
            customerRepository.getById(id)?.name ?: id
        }

        val paymentsBySlot: Map<String, List<PaluwaganPayment>> =
            payments.groupBy { it.slotId }

        val slotRows = slots.map { slot ->
            val slotPayments = paymentsBySlot[slot.id] ?: emptyList()
            val paymentsByRound = slotPayments.associateBy { it.roundNumber }
            SlotRow(
                slotId = slot.id,
                customerId = slot.customerId,
                customerName = customerMap[slot.customerId] ?: slot.customerId,
                originalCustomerName = slot.originalCustomerId?.let { customerMap[it] },
                position = slot.position,
                payments = paymentsByRound,
                potCollectedAt = slot.potCollectedAt,
            )
        }

        PaluwaganDetailUiState(
            group = group,
            slotRows = slotRows,
            isLoading = false,
            isAdmin = user?.role == UserRole.ADMIN,
            isAdminOrManager = user?.role in listOf(UserRole.ADMIN, UserRole.MANAGER),
            error = _error.value,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PaluwaganDetailUiState(),
    )

    /** Auto-assigns the next sequential position. Use for single additions. */
    fun addMember(customerId: String) {
        val userId = sessionManager.currentUser.value?.id ?: return
        val nextPosition = uiState.value.slotRows.size + 1
        viewModelScope.launch {
            runCatching {
                addSlot(AddPaluwaganSlotUseCase.Params(groupId, customerId, nextPosition, userId))
            }.onSuccess {
                snackbarController.showSuccess("Member added · position $nextPosition")
            }.onFailure { _error.value = it.message ?: "Failed to add member" }
        }
    }

    /**
     * Adds multiple members at once, assigning sequential positions starting
     * from the current slot count. Reads slotRows.size once so all positions
     * are correct even when the list hasn't updated between calls.
     */
    fun addMembers(customerIds: List<String>) {
        val userId = sessionManager.currentUser.value?.id ?: return
        val startPosition = uiState.value.slotRows.size + 1
        viewModelScope.launch {
            var added = 0
            customerIds.forEachIndexed { index, customerId ->
                runCatching {
                    addSlot(AddPaluwaganSlotUseCase.Params(groupId, customerId, startPosition + index, userId))
                }.onSuccess { added++ }
                    .onFailure { _error.value = it.message ?: "Failed to add member" }
            }
            if (added > 0) snackbarController.showSuccess("$added member${if (added == 1) "" else "s"} added")
        }
    }

    fun reorderMembers(fromIndex: Int, toIndex: Int) {
        val userId = sessionManager.currentUser.value?.id ?: return
        val current = uiState.value.slotRows.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val moved = current.removeAt(fromIndex)
        current.add(toIndex, moved)
        val orderedSlotIds = current.map { it.slotId }
        viewModelScope.launch {
            runCatching {
                reorderSlots(ReorderPaluwaganSlotsUseCase.Params(groupId, orderedSlotIds, userId))
            }.onSuccess {
                snackbarController.showSuccess("Member order saved")
            }.onFailure { _error.value = it.message ?: "Failed to reorder members" }
        }
    }

    fun recordPayment(
        slotId: String,
        customerId: String,
        roundNumber: Int,
        amountPaid: Double,
        paymentDate: Long,
        paymentMethod: PaymentMethod?,
        notes: String?,
    ) {
        val userId = sessionManager.currentUser.value?.id ?: return
        val group = uiState.value.group ?: return
        // Round N deadline = startDate + (N × frequencyDays − 1) days
        // e.g. start Apr 1, interval 15 → round 1 deadline = Apr 15, round 2 = Apr 30
        val roundCollectionDate = group.startDate +
            (roundNumber.toLong() * group.frequencyDays - 1) * 86_400_000L
        viewModelScope.launch {
            when (
                recordPayment(
                    RecordPaluwaganPaymentUseCase.Params(
                        groupId = groupId,
                        slotId = slotId,
                        customerId = customerId,
                        roundNumber = roundNumber,
                        amountPaid = amountPaid,
                        paymentDate = paymentDate,
                        paymentMethod = paymentMethod,
                        notes = notes,
                        actorUserId = userId,
                        roundCollectionDate = roundCollectionDate,
                        contributionAmount = group.contributionAmount,
                        totalSlots = group.totalSlots,
                        currentGroupRound = group.currentRound,
                        groupStartDate = group.startDate,
                        frequencyDays = group.frequencyDays,
                    ),
                )
            ) {
                RecordPaluwaganPaymentUseCase.Result.AlreadyPaid ->
                    _error.value = "Payment already recorded for this round"
                RecordPaluwaganPaymentUseCase.Result.Success ->
                    snackbarController.showSuccess(
                        "Payment ${com.ykfj.inventory.util.CurrencyFormatter.format(amountPaid)} recorded for round $roundNumber",
                    )
            }
        }
    }

    fun advanceRound() {
        val userId = sessionManager.currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val r = advanceRound(AdvancePaluwaganRoundUseCase.Params(groupId, userId))) {
                is AdvancePaluwaganRoundUseCase.Result.Advanced ->
                    snackbarController.showSuccess("Advanced to round ${r.newRound}")
                AdvancePaluwaganRoundUseCase.Result.Completed ->
                    snackbarController.showSuccess("Group completed · all rounds collected")
                AdvancePaluwaganRoundUseCase.Result.GroupNotFound ->
                    _error.value = "Group not found"
            }
        }
    }

    fun completeGroup() {
        val userId = sessionManager.currentUser.value?.id ?: return
        viewModelScope.launch {
            completeGroup(CompletePaluwaganGroupUseCase.Params(groupId, userId))
            snackbarController.showSuccess("Paluwagan group completed")
        }
    }

    fun deleteGroup() {
        val userId = sessionManager.currentUser.value?.id ?: return
        viewModelScope.launch {
            runCatching {
                deleteGroup(DeletePaluwaganGroupUseCase.Params(groupId, userId))
            }.onSuccess {
                snackbarController.showSuccess("Paluwagan group deleted")
                _navigateBack.value = true
            }.onFailure {
                _error.value = it.message ?: "Failed to delete group"
            }
        }
    }

    /**
     * Admin edit of a recorded payment. Recomputes PAID/LATE from the new date
     * against the round's scheduled collection date.
     */
    fun editPayment(
        paymentId: String,
        roundNumber: Int,
        amountPaid: Double,
        paymentDate: Long,
        paymentMethod: PaymentMethod?,
        notes: String?,
    ) {
        val group = uiState.value.group ?: return
        val roundCollectionDate = group.startDate +
            (roundNumber.toLong() * group.frequencyDays - 1) * 86_400_000L
        val status = if (paymentDate < roundCollectionDate)
            PaluwaganPaymentStatus.PAID else PaluwaganPaymentStatus.LATE
        viewModelScope.launch {
            runCatching {
                paluwaganRepository.updatePaymentFull(paymentId, status, paymentDate, amountPaid, paymentMethod, notes)
            }.onSuccess {
                snackbarController.showSuccess("Payment updated")
            }.onFailure { _error.value = it.message ?: "Failed to update payment" }
        }
    }

    /** Records the actual date and channel through which the collector received the pot money. */
    fun recordPotCollection(slotId: String, date: Long, payoutChannel: PaymentMethod?) {
        viewModelScope.launch {
            runCatching {
                paluwaganRepository.recordPotCollection(slotId, date, payoutChannel)
            }.onSuccess {
                snackbarController.showSuccess("Pot collection recorded")
            }.onFailure { _error.value = it.message ?: "Failed to record pot collection" }
        }
    }

    /** Pasalo: swap the customer assigned to a slot. */
    fun replaceSlotCustomer(slotId: String, newCustomerId: String) {
        viewModelScope.launch {
            runCatching {
                paluwaganRepository.updateSlotCustomer(slotId, newCustomerId)
            }.onSuccess {
                snackbarController.showSuccess("Slot member swapped (pasalo)")
            }.onFailure { _error.value = it.message ?: "Failed to update member" }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
