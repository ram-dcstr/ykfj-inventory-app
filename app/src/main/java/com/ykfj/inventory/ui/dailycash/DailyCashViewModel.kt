package com.ykfj.inventory.ui.dailycash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykfj.inventory.data.local.AppSettingKeys
import com.ykfj.inventory.data.local.db.YkfjDatabase
import com.ykfj.inventory.data.local.db.enums.CashMovementType
import com.ykfj.inventory.data.local.db.enums.PaymentMethod
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.model.CashMovement
import com.ykfj.inventory.domain.repository.CashMovementRepository
import com.ykfj.inventory.ui.auth.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

/**
 * Phase 11 "Daily Cash" — owner's end-of-day reconciliation view.
 *
 * Combines four flows for the selected calendar day:
 *  - [CashMovementRepository.observeForDay] → change float, purchase float, expenses, adjustments
 *  - Sold records summed by payment method (one query per method)
 *  - Layaway transactions summed by payment method (one query per method)
 *  - Gold purchase records summed by date (cash going out)
 *
 * Cash balance = changeFloat + purchaseFloat + cashSales + cashLayawayPayments
 *              − goldPurchasesTotal − expenses + adjustments.
 *
 * Total collected = cash balance + non-cash sales + non-cash layaway payments.
 *
 * The change-float row is auto-created from [AppSettingKeys.DEFAULT_CHANGE_FLOAT]
 * the first time a new day is opened so the owner doesn't have to set it daily.
 * Editing it later updates the same row by id (one CHANGE_FLOAT per day).
 */
@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DailyCashViewModel @Inject constructor(
    private val db: YkfjDatabase,
    private val cashMovementRepository: CashMovementRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _selectedDay = MutableStateFlow(startOfDay(System.currentTimeMillis()))
    val selectedDay: StateFlow<Long> = _selectedDay

    val uiState: StateFlow<DailyCashUiState> = _selectedDay
        .flatMapLatest { dayStart ->
            val dayEnd = dayStart + ONE_DAY_MS - 1

            combine(
                cashMovementRepository.observeForDay(dayStart),
                db.soldRecordDao().observeSumByPaymentMethodForDay(PaymentMethod.CASH.name, dayStart, dayEnd),
                db.soldRecordDao().observeSumByPaymentMethodForDay(PaymentMethod.GCASH.name, dayStart, dayEnd),
                db.soldRecordDao().observeSumByPaymentMethodForDay(PaymentMethod.ONLINE_BANKING.name, dayStart, dayEnd),
                db.soldRecordDao().observeSumByPaymentMethodForDay(PaymentMethod.OTHER.name, dayStart, dayEnd),
                db.layawayTransactionDao().observeSumByPaymentMethodForDay(PaymentMethod.CASH.name, dayStart, dayEnd),
                db.layawayTransactionDao().observeSumByPaymentMethodForDay(PaymentMethod.GCASH.name, dayStart, dayEnd),
                db.layawayTransactionDao().observeSumByPaymentMethodForDay(PaymentMethod.ONLINE_BANKING.name, dayStart, dayEnd),
                db.layawayTransactionDao().observeSumByPaymentMethodForDay(PaymentMethod.OTHER.name, dayStart, dayEnd),
                db.goldPurchaseRecordDao().observeSumForDay(dayStart, dayEnd),
                sessionManager.currentUser,
            ) { args: Array<Any?> ->
                @Suppress("UNCHECKED_CAST")
                val movements = args[0] as List<CashMovement>
                val cashSales = args[1] as Double
                val gcashSales = args[2] as Double
                val onlineBankingSales = args[3] as Double
                val otherSales = args[4] as Double
                val cashLayaway = args[5] as Double
                val gcashLayaway = args[6] as Double
                val onlineBankingLayaway = args[7] as Double
                val otherLayaway = args[8] as Double
                val goldPurchasesTotal = args[9] as Double
                val user = args[10] as com.ykfj.inventory.domain.model.User?

                buildState(
                    dayStart = dayStart,
                    movements = movements,
                    cashSales = cashSales,
                    gcashSales = gcashSales,
                    onlineBankingSales = onlineBankingSales,
                    otherSales = otherSales,
                    cashLayaway = cashLayaway,
                    gcashLayaway = gcashLayaway,
                    onlineBankingLayaway = onlineBankingLayaway,
                    otherLayaway = otherLayaway,
                    goldPurchasesTotal = goldPurchasesTotal,
                    role = user?.role,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DailyCashUiState(),
        )

    init {
        // Auto-seed CHANGE_FLOAT for today (idempotent — only fires when no row exists yet).
        viewModelScope.launch { seedChangeFloatIfNeeded(_selectedDay.value) }
    }

    fun selectDay(dayMillis: Long) {
        val normalized = startOfDay(dayMillis)
        if (normalized == _selectedDay.value) return
        _selectedDay.value = normalized
        viewModelScope.launch { seedChangeFloatIfNeeded(normalized) }
    }

    fun nextDay() = selectDay(_selectedDay.value + ONE_DAY_MS)
    fun previousDay() = selectDay(_selectedDay.value - ONE_DAY_MS)
    fun today() = selectDay(System.currentTimeMillis())

    /** Admin/Manager: edits the CHANGE_FLOAT row for the current day. Creates one if absent. */
    fun editChangeFloat(amount: Double) = upsertOnePerDay(CashMovementType.CHANGE_FLOAT, amount)

    /** Admin/Manager: edits the PURCHASE_FLOAT row for the current day. Creates one if absent. */
    fun setPurchaseFloat(amount: Double) = upsertOnePerDay(CashMovementType.PURCHASE_FLOAT, amount)

    /** Admin/Manager: appends an expense. Notes required by UI gate. */
    fun addExpense(amount: Double, notes: String) {
        if (amount <= 0 || notes.isBlank()) return
        appendMovement(CashMovementType.EXPENSE, -amount, notes)
    }

    /** Admin only: appends a positive or negative adjustment. Notes required by UI gate. */
    fun addAdjustment(amount: Double, notes: String) {
        if (amount == 0.0 || notes.isBlank()) return
        appendMovement(CashMovementType.ADJUSTMENT, amount, notes)
    }

    /** Admin: removes a recorded expense or adjustment. */
    fun deleteMovement(id: String) {
        viewModelScope.launch { cashMovementRepository.softDelete(id) }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun upsertOnePerDay(type: CashMovementType, amount: Double) {
        if (amount < 0) return
        val day = _selectedDay.value
        viewModelScope.launch {
            val userId = sessionManager.currentUser.value?.id ?: return@launch
            val now = System.currentTimeMillis()
            val existing = cashMovementRepository.getForTypeAndDay(type, day)
            val movement = existing?.copy(amount = amount, recordedBy = userId, recordedAt = now, updatedAt = now)
                ?: CashMovement(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    amount = amount,
                    date = day,
                    notes = null,
                    recordedBy = userId,
                    recordedAt = now,
                    createdAt = now,
                    updatedAt = now,
                )
            cashMovementRepository.upsert(movement)
        }
    }

    private fun appendMovement(type: CashMovementType, signedAmount: Double, notes: String) {
        val day = _selectedDay.value
        viewModelScope.launch {
            val userId = sessionManager.currentUser.value?.id ?: return@launch
            val now = System.currentTimeMillis()
            cashMovementRepository.upsert(
                CashMovement(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    amount = signedAmount,
                    date = day,
                    notes = notes,
                    recordedBy = userId,
                    recordedAt = now,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    /**
     * Only seeds CHANGE_FLOAT for the current "today" — we don't retroactively
     * inject opening cash for past days the owner is just browsing.
     */
    private suspend fun seedChangeFloatIfNeeded(day: Long) {
        if (day != startOfDay(System.currentTimeMillis())) return
        val existing = cashMovementRepository.getForTypeAndDay(CashMovementType.CHANGE_FLOAT, day)
        if (existing != null) return
        val defaultFloat = db.appSettingsDao().getValue(AppSettingKeys.DEFAULT_CHANGE_FLOAT)
            ?.toDoubleOrNull() ?: return
        if (defaultFloat <= 0) return
        val userId = sessionManager.currentUser.value?.id ?: return
        val now = System.currentTimeMillis()
        cashMovementRepository.upsert(
            CashMovement(
                id = UUID.randomUUID().toString(),
                type = CashMovementType.CHANGE_FLOAT,
                amount = defaultFloat,
                date = day,
                notes = null,
                recordedBy = userId,
                recordedAt = now,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    @Suppress("LongParameterList")
    private fun buildState(
        dayStart: Long,
        movements: List<CashMovement>,
        cashSales: Double,
        gcashSales: Double,
        onlineBankingSales: Double,
        otherSales: Double,
        cashLayaway: Double,
        gcashLayaway: Double,
        onlineBankingLayaway: Double,
        otherLayaway: Double,
        goldPurchasesTotal: Double,
        role: UserRole?,
    ): DailyCashUiState {
        val changeFloat = movements.firstOrNull { it.type == CashMovementType.CHANGE_FLOAT }?.amount ?: 0.0
        val purchaseFloat = movements.firstOrNull { it.type == CashMovementType.PURCHASE_FLOAT }?.amount ?: 0.0
        val expenses = movements.filter { it.type == CashMovementType.EXPENSE }
        val adjustments = movements.filter { it.type == CashMovementType.ADJUSTMENT }
        val expensesSum = expenses.sumOf { it.amount }       // negative
        val adjustmentsSum = adjustments.sumOf { it.amount } // signed

        val cashBalance = changeFloat + purchaseFloat + cashSales + cashLayaway -
            goldPurchasesTotal + expensesSum + adjustmentsSum

        val gcashBalance = gcashSales + gcashLayaway
        val onlineBankingBalance = onlineBankingSales + onlineBankingLayaway
        val otherBalance = otherSales + otherLayaway

        return DailyCashUiState(
            selectedDay = dayStart,
            changeFloat = changeFloat,
            purchaseFloat = purchaseFloat,
            cashSales = cashSales,
            gcashSales = gcashSales,
            onlineBankingSales = onlineBankingSales,
            otherSales = otherSales,
            cashLayawayPayments = cashLayaway,
            gcashLayawayPayments = gcashLayaway,
            onlineBankingLayawayPayments = onlineBankingLayaway,
            otherLayawayPayments = otherLayaway,
            goldPurchasesTotal = goldPurchasesTotal,
            expenses = expenses,
            adjustments = adjustments,
            cashBalance = cashBalance,
            gcashBalance = gcashBalance,
            onlineBankingBalance = onlineBankingBalance,
            otherBalance = otherBalance,
            totalCollected = cashBalance + gcashBalance + onlineBankingBalance + otherBalance,
            isAdmin = role == UserRole.ADMIN,
            isAdminOrManager = role == UserRole.ADMIN || role == UserRole.MANAGER,
            isLoading = false,
        )
    }

    private companion object {
        const val ONE_DAY_MS = 24L * 60 * 60 * 1000
    }
}

private fun startOfDay(epochMillis: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = epochMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}
