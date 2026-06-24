package com.ykfj.inventory.ui.stockadjustments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykfj.inventory.domain.model.StockAdjustment
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.repository.StockAdjustmentRepository
import com.ykfj.inventory.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class StockAdjustmentRow(
    val id: String,
    val productName: String,
    val quantity: Int,
    val reasonLabel: String,
    val notes: String?,
    val recordedByName: String,
    val dateRecorded: Long,
)

data class StockAdjustmentsUiState(
    val rows: List<StockAdjustmentRow> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * Read-only history of stock write-offs (lost / stolen / miscount / returned to
 * supplier). Unlike the Activity Log, these records are never auto-pruned, so this
 * is the permanent place to review what left stock and why.
 */
@HiltViewModel
class StockAdjustmentsViewModel @Inject constructor(
    stockAdjustmentRepository: StockAdjustmentRepository,
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    val uiState: StateFlow<StockAdjustmentsUiState> =
        stockAdjustmentRepository.observeAll()
            .map { records -> StockAdjustmentsUiState(rows = enrich(records), isLoading = false) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = StockAdjustmentsUiState(),
            )

    private suspend fun enrich(records: List<StockAdjustment>): List<StockAdjustmentRow> {
        if (records.isEmpty()) return emptyList()
        // A write-off can zero out a product, which soft-deletes it — use the
        // deletion-agnostic lookup so the name still resolves for history.
        val productNames = productRepository.getByIdsAnyState(records.map { it.productId }.distinct())
            .associate { it.id to it.name }
        val userNames = userRepository.getByIds(records.map { it.recordedBy }.distinct())
            .associate { it.id to it.name }
        return records.map { r ->
            StockAdjustmentRow(
                id = r.id,
                productName = productNames[r.productId] ?: r.productId,
                quantity = r.quantity,
                reasonLabel = r.reason.label,
                notes = r.notes,
                recordedByName = userNames[r.recordedBy] ?: "Unknown",
                dateRecorded = r.dateRecorded,
            )
        }
    }
}
