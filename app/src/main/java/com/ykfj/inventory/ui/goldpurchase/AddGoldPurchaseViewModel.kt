package com.ykfj.inventory.ui.goldpurchase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykfj.inventory.domain.model.Customer
import com.ykfj.inventory.domain.repository.CustomerRepository
import com.ykfj.inventory.domain.usecase.goldpurchase.AddGoldPurchaseUseCase
import com.ykfj.inventory.ui.auth.SessionManager
import com.ykfj.inventory.ui.components.SnackbarController
import com.ykfj.inventory.util.CurrencyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Standard purity options shown in the modal — labels are stored verbatim. */
val GoldPurityOptions = listOf("10K", "14K", "18K", "21K", "22K", "24K")

data class GoldPurchaseItemDraft(
    val localId: String = UUID.randomUUID().toString(),
    val description: String = "",
    val purity: String? = null,
    val weightGrams: String = "",
    val buyRatePerGram: String = "",
    val overrideEnabled: Boolean = false,
    val overrideValue: String = "",
    val photoUri: String? = null,
) {
    val weightValue: Double? get() = weightGrams.toDoubleOrNull()?.takeIf { it > 0 }
    val rateValue: Double? get() = buyRatePerGram.toDoubleOrNull()?.takeIf { it > 0 }
    val computedValue: Double? get() = weightValue?.let { w -> rateValue?.let { r -> w * r } }
    val finalValue: Double?
        get() = if (overrideEnabled) overrideValue.toDoubleOrNull()?.takeIf { it > 0 }
        else computedValue
    val isValid: Boolean get() = description.isNotBlank() && (finalValue ?: 0.0) > 0
}

/**
 * Saver for `List<GoldPurchaseItemDraft>` — lets dialogs hold trade-in item state
 * across configuration changes (rotation, dark-mode flip) via `rememberSaveable`.
 *
 * Each draft is flattened to its 8 constructor fields (all saveable primitives or
 * nullable Strings); the list of N drafts becomes a flat list of 8 × N elements.
 */
private const val DRAFT_FIELD_COUNT = 8

val GoldPurchaseItemDraftListSaver: androidx.compose.runtime.saveable.Saver<List<GoldPurchaseItemDraft>, Any> =
    androidx.compose.runtime.saveable.listSaver(
        save = { items ->
            items.flatMap { d ->
                listOf<Any?>(
                    d.localId,
                    d.description,
                    d.purity,
                    d.weightGrams,
                    d.buyRatePerGram,
                    d.overrideEnabled,
                    d.overrideValue,
                    d.photoUri,
                )
            }
        },
        restore = { flat ->
            flat.chunked(DRAFT_FIELD_COUNT).map { fields ->
                GoldPurchaseItemDraft(
                    localId = fields[0] as String,
                    description = fields[1] as String,
                    purity = fields[2] as String?,
                    weightGrams = fields[3] as String,
                    buyRatePerGram = fields[4] as String,
                    overrideEnabled = fields[5] as Boolean,
                    overrideValue = fields[6] as String,
                    photoUri = fields[7] as String?,
                )
            }
        },
    )

data class AddGoldPurchaseUiState(
    val customer: Customer? = null,
    val items: List<GoldPurchaseItemDraft> = listOf(GoldPurchaseItemDraft()),
    val notes: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedId: String? = null,
) {
    val totalPaid: Double get() = items.sumOf { it.finalValue ?: 0.0 }
    val canSubmit: Boolean get() = !isSaving && items.isNotEmpty() && items.all { it.isValid }
}

@HiltViewModel
class AddGoldPurchaseViewModel @Inject constructor(
    private val addGoldPurchaseUseCase: AddGoldPurchaseUseCase,
    private val customerRepository: CustomerRepository,
    private val sessionManager: SessionManager,
    private val snackbarController: SnackbarController,
) : ViewModel() {

    private val _state = MutableStateFlow(AddGoldPurchaseUiState())
    val uiState: StateFlow<AddGoldPurchaseUiState> = _state.asStateFlow()

    fun setCustomer(customer: Customer?) { _state.value = _state.value.copy(customer = customer) }
    fun setNotes(notes: String) { _state.value = _state.value.copy(notes = notes) }

    /** Loads a customer by id (set from the customer-picker round trip). */
    fun setPickedCustomer(customerId: String) {
        viewModelScope.launch {
            customerRepository.getById(customerId)?.let { setCustomer(it) }
        }
    }

    fun addItem() {
        _state.value = _state.value.copy(items = _state.value.items + GoldPurchaseItemDraft())
    }

    fun updateItem(index: Int, draft: GoldPurchaseItemDraft) {
        _state.value = _state.value.copy(
            items = _state.value.items.toMutableList().also {
                if (index in it.indices) it[index] = draft
            },
        )
    }

    fun removeItem(index: Int) {
        if (_state.value.items.size <= 1) return
        _state.value = _state.value.copy(
            items = _state.value.items.toMutableList().also {
                if (index in it.indices) it.removeAt(index)
            },
        )
    }

    fun setItemPhoto(index: Int, uri: String?) {
        val items = _state.value.items.toMutableList()
        if (index in items.indices) {
            items[index] = items[index].copy(photoUri = uri)
            _state.value = _state.value.copy(items = items)
        }
    }

    fun submit() {
        val s = _state.value
        val userId = sessionManager.currentUser.value?.id ?: return
        if (!s.canSubmit) return
        _state.value = s.copy(isSaving = true)
        viewModelScope.launch {
            val drafts = s.items.map { d ->
                AddGoldPurchaseUseCase.ItemDraft(
                    description = d.description,
                    weightGrams = d.weightValue!!,
                    purity = d.purity,
                    buyRatePerGram = d.rateValue!!,
                    overrideValue = if (d.overrideEnabled) d.overrideValue.toDoubleOrNull() else null,
                    photoFilename = null,
                )
            }
            when (val r = addGoldPurchaseUseCase(
                AddGoldPurchaseUseCase.Params(
                    customerId = s.customer?.id,
                    items = drafts,
                    notes = s.notes.ifBlank { null },
                    recordedBy = userId,
                ),
            )) {
                is AddGoldPurchaseUseCase.Result.Success -> {
                    snackbarController.showSuccess(
                        "Gold purchase recorded · ${s.items.size} item${if (s.items.size == 1) "" else "s"} · ${CurrencyFormatter.format(s.totalPaid)}",
                    )
                    _state.value = _state.value.copy(isSaving = false, savedId = r.recordId)
                }
                AddGoldPurchaseUseCase.Result.NoItems ->
                    _state.value = _state.value.copy(isSaving = false, error = "At least one item required")
                AddGoldPurchaseUseCase.Result.InvalidItem ->
                    _state.value = _state.value.copy(isSaving = false, error = "Each item needs weight > 0 and rate > 0")
            }
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
}
