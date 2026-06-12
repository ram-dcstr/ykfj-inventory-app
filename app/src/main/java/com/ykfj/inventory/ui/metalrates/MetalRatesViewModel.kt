package com.ykfj.inventory.ui.metalrates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykfj.inventory.domain.model.MetalRate
import com.ykfj.inventory.domain.usecase.metalrate.AddMetalRateUseCase
import com.ykfj.inventory.domain.usecase.metalrate.DeleteMetalRateUseCase
import com.ykfj.inventory.domain.usecase.metalrate.GetMetalRatesUseCase
import com.ykfj.inventory.domain.usecase.metalrate.UpdateMetalRateUseCase
import com.ykfj.inventory.ui.auth.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MetalRatesUiState(
    val rates: List<MetalRate> = emptyList(),
    val isLoading: Boolean = true,
    val editing: MetalRate? = null,
    val isFormOpen: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class MetalRatesViewModel @Inject constructor(
    getMetalRates: GetMetalRatesUseCase,
    private val addMetalRate: AddMetalRateUseCase,
    private val updateMetalRate: UpdateMetalRateUseCase,
    private val deleteMetalRate: DeleteMetalRateUseCase,
    private val sessionManager: SessionManager,
    private val snackbarController: com.ykfj.inventory.ui.components.SnackbarController,
) : ViewModel() {

    private val _formState = MutableStateFlow(FormState())
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<MetalRatesUiState> =
        combine(
            getMetalRates(),
            _formState,
            _error,
        ) { rates, form, err ->
            MetalRatesUiState(
                rates = rates,
                isLoading = false,
                editing = form.editing,
                isFormOpen = form.open,
                error = err,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MetalRatesUiState(),
        )

    private data class FormState(val open: Boolean = false, val editing: MetalRate? = null)

    fun openAddForm() {
        _formState.value = FormState(open = true, editing = null)
    }

    fun openEditForm(rate: MetalRate) {
        _formState.value = FormState(open = true, editing = rate)
    }

    fun closeForm() {
        _formState.value = FormState(open = false, editing = null)
    }

    fun clearError() {
        _error.value = null
    }

    fun submit(name: String, pricePerGram: Double) {
        val userId = sessionManager.currentUser.value?.id ?: return
        val editing = _formState.value.editing

        if (name.isBlank()) {
            _error.value = "Name is required"
            return
        }
        if (pricePerGram <= 0.0) {
            _error.value = "Price must be greater than zero"
            return
        }

        viewModelScope.launch {
            if (editing == null) {
                addMetalRate(name = name, pricePerGram = pricePerGram, actorUserId = userId)
                snackbarController.showSuccess("Metal rate \"$name\" added")
            } else {
                updateMetalRate(
                    id = editing.id,
                    name = name,
                    pricePerGram = pricePerGram,
                    actorUserId = userId,
                )
                snackbarController.showSuccess("Metal rate \"$name\" updated · all weighted items repriced")
            }
            closeForm()
        }
    }

    fun delete(rate: MetalRate) {
        val userId = sessionManager.currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val r = deleteMetalRate(id = rate.id, actorUserId = userId)) {
                is DeleteMetalRateUseCase.Result.Blocked ->
                    _error.value = "Cannot delete '${rate.name}' — ${r.activeProductCount} active " +
                        if (r.activeProductCount == 1) "product uses it" else "products use it"
                DeleteMetalRateUseCase.Result.NotFound ->
                    _error.value = "Metal rate no longer exists"
                DeleteMetalRateUseCase.Result.Success ->
                    snackbarController.showSuccess("Metal rate \"${rate.name}\" deleted")
            }
        }
    }
}
