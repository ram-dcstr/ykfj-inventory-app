package com.ykfj.inventory.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykfj.inventory.domain.model.Category
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.usecase.category.AddCategoryUseCase
import com.ykfj.inventory.domain.usecase.category.DeleteCategoryUseCase
import com.ykfj.inventory.domain.usecase.category.GetCategoriesUseCase
import com.ykfj.inventory.domain.usecase.category.UpdateCategoryUseCase
import com.ykfj.inventory.ui.auth.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoriesUiState(
    val categories: List<Category> = emptyList(),
    val categoryCounts: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = true,
    val editing: Category? = null,
    val isFormOpen: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    getCategories: GetCategoriesUseCase,
    private val addCategory: AddCategoryUseCase,
    private val updateCategory: UpdateCategoryUseCase,
    private val deleteCategory: DeleteCategoryUseCase,
    private val sessionManager: SessionManager,
    private val snackbarController: com.ykfj.inventory.ui.components.SnackbarController,
    productRepository: ProductRepository,
) : ViewModel() {

    private val _formState = MutableStateFlow(FormState())
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CategoriesUiState> =
        combine(
            getCategories(),
            productRepository.observeCountsPerCategory(),
            _formState,
            _error,
        ) { categories, counts, form, err ->
            CategoriesUiState(
                categories = categories,
                categoryCounts = counts,
                isLoading = false,
                editing = form.editing,
                isFormOpen = form.open,
                error = err,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CategoriesUiState(),
        )

    private data class FormState(val open: Boolean = false, val editing: Category? = null)

    fun openAddForm() {
        _formState.value = FormState(open = true, editing = null)
    }

    fun openEditForm(category: Category) {
        _formState.value = FormState(open = true, editing = category)
    }

    fun closeForm() {
        _formState.value = FormState(open = false, editing = null)
    }

    fun clearError() {
        _error.value = null
    }

    fun submit(name: String) {
        val userId = sessionManager.currentUser.value?.id ?: return
        val editing = _formState.value.editing

        if (name.isBlank()) {
            _error.value = "Name is required"
            return
        }

        viewModelScope.launch {
            if (editing == null) {
                addCategory(name = name, actorUserId = userId)
                snackbarController.showSuccess("Category \"$name\" added")
            } else {
                updateCategory(id = editing.id, name = name, actorUserId = userId)
                snackbarController.showSuccess("Category \"$name\" updated")
            }
            closeForm()
        }
    }

    fun delete(category: Category) {
        val userId = sessionManager.currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val r = deleteCategory(id = category.id, actorUserId = userId)) {
                is DeleteCategoryUseCase.Result.Blocked ->
                    _error.value = "Cannot delete '${category.name}' — ${r.activeProductCount} active " +
                        if (r.activeProductCount == 1) "product uses it" else "products use it"
                DeleteCategoryUseCase.Result.NotFound ->
                    _error.value = "Category no longer exists"
                DeleteCategoryUseCase.Result.Success ->
                    snackbarController.showSuccess("Category \"${category.name}\" deleted")
            }
        }
    }
}
