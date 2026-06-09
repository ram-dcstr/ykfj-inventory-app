package com.ykfj.inventory.ui.dailycash

import com.ykfj.inventory.domain.model.CashMovement

/**
 * State for the Daily Cash screen. All monetary values are signed totals; the
 * screen displays gold purchases as a negative (cash going out) so the
 * [cashBalance] arithmetic in [DailyCashViewModel.buildState] reads naturally.
 *
 * `expenses` and `adjustments` are the raw rows so the screen can list and edit
 * them individually; their sums are already baked into [cashBalance].
 */
data class DailyCashUiState(
    val selectedDay: Long = 0L,
    val changeFloat: Double = 0.0,
    val purchaseFloat: Double = 0.0,
    val cashSales: Double = 0.0,
    val gcashSales: Double = 0.0,
    val onlineBankingSales: Double = 0.0,
    val otherSales: Double = 0.0,
    val cashLayawayPayments: Double = 0.0,
    val gcashLayawayPayments: Double = 0.0,
    val onlineBankingLayawayPayments: Double = 0.0,
    val otherLayawayPayments: Double = 0.0,
    val goldPurchasesTotal: Double = 0.0,
    val expenses: List<CashMovement> = emptyList(),
    val adjustments: List<CashMovement> = emptyList(),
    val cashBalance: Double = 0.0,
    val gcashBalance: Double = 0.0,
    val onlineBankingBalance: Double = 0.0,
    val otherBalance: Double = 0.0,
    val totalCollected: Double = 0.0,
    val isAdmin: Boolean = false,
    val isAdminOrManager: Boolean = false,
    val isLoading: Boolean = true,
)
