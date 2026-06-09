package com.ykfj.inventory.ui.inventory

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykfj.inventory.data.local.db.enums.PricingType
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemModal(
    onNavigateUp: () -> Unit,
    onSaved: (productId: String) -> Unit,
    viewModel: AddItemViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(state.savedProductId) {
        state.savedProductId?.let { onSaved(it) }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditMode) "Edit Item" else "Add Item") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            else -> AddItemForm(
                state = state,
                modifier = Modifier.padding(padding),
                onFormDataChanged = { name, catId, rateId, weight ->
                    viewModel.onFormDataChanged(name, catId, rateId, weight)
                },
                onSubmit = { params ->
                    viewModel.submit(
                        name = params.name,
                        categoryId = params.categoryId,
                        categoryName = params.categoryName,
                        metalRateId = params.metalRateId,
                        metalRateName = params.metalRateName,
                        supplierId = params.supplierId,
                        dateAcquired = params.dateAcquired,
                        pricingType = params.pricingType,
                        capitalPrice = params.capitalPrice,
                        sellingPrice = params.sellingPrice,
                        weightGrams = params.weightGrams,
                        size = params.size,
                        quantity = params.quantity,
                        notes = params.notes,
                        imageFile = params.imageFile,
                    )
                },
                isSaving = state.isSaving,
            )
        }
    }
}

data class ItemFormParams(
    val name: String,
    val categoryId: String,
    val categoryName: String,
    val metalRateId: String?,
    val metalRateName: String?,
    val supplierId: String?,
    val dateAcquired: Long,
    val pricingType: PricingType,
    val capitalPrice: Double,
    val sellingPrice: Double?,
    val weightGrams: Double?,
    val size: String?,
    val quantity: Int,
    val notes: String?,
    val imageFile: File?,
)
