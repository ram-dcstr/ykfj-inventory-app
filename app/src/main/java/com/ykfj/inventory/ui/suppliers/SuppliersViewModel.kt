package com.ykfj.inventory.ui.suppliers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykfj.inventory.domain.model.Supplier
import com.ykfj.inventory.domain.usecase.supplier.AddSupplierUseCase
import com.ykfj.inventory.domain.usecase.supplier.DeleteSupplierUseCase
import com.ykfj.inventory.domain.usecase.supplier.GetSuppliersUseCase
import com.ykfj.inventory.domain.usecase.supplier.UpdateSupplierUseCase
import com.ykfj.inventory.ui.auth.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SupplierFormInput(
    val name: String,
    val representativeName: String?,
    val mobile: String?,
    val address: String?,
    val notes: String?,
)

data class SuppliersUiState(
    val suppliers: List<Supplier> = emptyList(),
    val isLoading: Boolean = true,
    val editing: Supplier? = null,
    val isFormOpen: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SuppliersViewModel @Inject constructor(
    getSuppliers: GetSuppliersUseCase,
    private val addSupplier: AddSupplierUseCase,
    private val updateSupplier: UpdateSupplierUseCase,
    private val deleteSupplier: DeleteSupplierUseCase,
    private val sessionManager: SessionManager,
    private val snackbarController: com.ykfj.inventory.ui.components.SnackbarController,
) : ViewModel() {

    private val _formState = MutableStateFlow(FormState())
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SuppliersUiState> =
        combine(
            getSuppliers(),
            _formState,
            _error,
        ) { suppliers, form, err ->
            SuppliersUiState(
                suppliers = suppliers,
                isLoading = false,
                editing = form.editing,
                isFormOpen = form.open,
                error = err,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SuppliersUiState(),
        )

    private data class FormState(val open: Boolean = false, val editing: Supplier? = null)

    fun openAddForm() {
        _formState.value = FormState(open = true, editing = null)
    }

    fun openEditForm(supplier: Supplier) {
        _formState.value = FormState(open = true, editing = supplier)
    }

    fun closeForm() {
        _formState.value = FormState(open = false, editing = null)
    }

    fun clearError() {
        _error.value = null
    }

    fun submit(input: SupplierFormInput) {
        val userId = sessionManager.currentUser.value?.id ?: return
        val editing = _formState.value.editing

        if (input.name.isBlank()) {
            _error.value = "Name is required"
            return
        }

        viewModelScope.launch {
            if (editing == null) {
                addSupplier(
                    name = input.name,
                    representativeName = input.representativeName,
                    mobile = input.mobile,
                    address = input.address,
                    notes = input.notes,
                    actorUserId = userId,
                )
                snackbarController.showSuccess("Supplier \"${input.name}\" added")
            } else {
                updateSupplier(
                    id = editing.id,
                    name = input.name,
                    representativeName = input.representativeName,
                    mobile = input.mobile,
                    address = input.address,
                    notes = input.notes,
                    actorUserId = userId,
                )
                snackbarController.showSuccess("Supplier \"${input.name}\" updated")
            }
            closeForm()
        }
    }

    fun delete(supplier: Supplier) {
        val userId = sessionManager.currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val r = deleteSupplier(id = supplier.id, actorUserId = userId)) {
                is DeleteSupplierUseCase.Result.Blocked ->
                    _error.value = "Cannot delete '${supplier.name}' — ${r.activeProductCount} active " +
                        if (r.activeProductCount == 1) "product uses it" else "products use it"
                DeleteSupplierUseCase.Result.NotFound ->
                    _error.value = "Supplier no longer exists"
                DeleteSupplierUseCase.Result.Success ->
                    snackbarController.showSuccess("Supplier \"${supplier.name}\" deleted")
            }
        }
    }
}
