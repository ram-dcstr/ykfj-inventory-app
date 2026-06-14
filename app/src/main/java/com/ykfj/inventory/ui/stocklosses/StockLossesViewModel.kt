package com.ykfj.inventory.ui.stocklosses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.ui.auth.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Drives the Stock Losses container — only Admin/Manager see the Write-offs tab. */
@HiltViewModel
class StockLossesViewModel @Inject constructor(
    sessionManager: SessionManager,
) : ViewModel() {
    val isAdminOrManager: StateFlow<Boolean> = sessionManager.currentUser
        .map { it?.role == UserRole.ADMIN || it?.role == UserRole.MANAGER }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}
