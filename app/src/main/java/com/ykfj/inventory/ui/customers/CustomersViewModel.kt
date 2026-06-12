package com.ykfj.inventory.ui.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.model.Customer
import com.ykfj.inventory.domain.usecase.customer.AddCustomerUseCase
import com.ykfj.inventory.domain.usecase.customer.GetCustomersUseCase
import com.ykfj.inventory.domain.usecase.customer.SearchCustomersUseCase
import com.ykfj.inventory.domain.usecase.customer.UpdateCustomerUseCase
import com.ykfj.inventory.ui.auth.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomerFormInput(
    val name: String,
    val mobile: String?,
    val phone: String?,
    val birthday: Long?,
    val address: String?,
    val notes: String?,
)

data class CustomersUiState(
    val customers: List<Customer> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val editing: Customer? = null,
    val isFormOpen: Boolean = false,
    val canEdit: Boolean = false,
    val canViewHistory: Boolean = false,
    val error: String? = null,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class CustomersViewModel @Inject constructor(
    getCustomers: GetCustomersUseCase,
    private val searchCustomers: SearchCustomersUseCase,
    private val addCustomer: AddCustomerUseCase,
    private val updateCustomer: UpdateCustomerUseCase,
    private val sessionManager: SessionManager,
    private val snackbarController: com.ykfj.inventory.ui.components.SnackbarController,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _formState = MutableStateFlow(FormState())
    private val _error = MutableStateFlow<String?>(null)

    private val customers = _searchQuery
        .debounce(300)
        .flatMapLatest { q ->
            if (q.isBlank()) getCustomers() else searchCustomers(q)
        }

    val uiState: StateFlow<CustomersUiState> =
        combine(
            customers,
            _searchQuery,
            _formState,
            _error,
        ) { list, query, form, err ->
            val role = sessionManager.currentUser.value?.role
            val adminOrManager = role == UserRole.ADMIN || role == UserRole.MANAGER
            CustomersUiState(
                customers = list,
                isLoading = false,
                searchQuery = query,
                editing = form.editing,
                isFormOpen = form.open,
                canEdit = adminOrManager,
                canViewHistory = adminOrManager,
                error = err,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CustomersUiState(),
        )

    private data class FormState(val open: Boolean = false, val editing: Customer? = null)

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun openAddForm() {
        _formState.value = FormState(open = true, editing = null)
    }

    fun openEditForm(customer: Customer) {
        _formState.value = FormState(open = true, editing = customer)
    }

    fun closeForm() {
        _formState.value = FormState(open = false, editing = null)
    }

    fun clearError() {
        _error.value = null
    }

    fun submit(input: CustomerFormInput) {
        val userId = sessionManager.currentUser.value?.id ?: return
        val editing = _formState.value.editing

        if (input.name.isBlank()) {
            _error.value = "Name is required"
            return
        }

        viewModelScope.launch {
            if (editing == null) {
                addCustomer(
                    name = input.name,
                    mobile = input.mobile,
                    phone = input.phone,
                    birthday = input.birthday,
                    address = input.address,
                    notes = input.notes,
                    actorUserId = userId,
                )
                snackbarController.showSuccess("Customer \"${input.name}\" added")
            } else {
                updateCustomer(
                    id = editing.id,
                    name = input.name,
                    mobile = input.mobile,
                    phone = input.phone,
                    birthday = input.birthday,
                    address = input.address,
                    notes = input.notes,
                    actorUserId = userId,
                )
                snackbarController.showSuccess("Customer \"${input.name}\" updated")
            }
            closeForm()
        }
    }
}
