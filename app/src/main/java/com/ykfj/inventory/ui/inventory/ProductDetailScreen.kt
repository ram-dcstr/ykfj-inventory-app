package com.ykfj.inventory.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ykfj.inventory.data.local.db.enums.DiscountType
import com.ykfj.inventory.data.local.db.enums.ProductStatus
import com.ykfj.inventory.util.CurrencyFormatter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    onNavigateUp: () -> Unit,
    onNavigateToEdit: (productId: String) -> Unit,
    onNavigateToCustomers: () -> Unit,
    pickedCustomerId: String? = null,
    onPickedCustomerConsumed: () -> Unit = {},
    readOnly: Boolean = false,
    viewModel: ProductDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickedCustomer by viewModel.pickedCustomer.collectAsStateWithLifecycle()
    val navigateBack by viewModel.navigateBack.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(pickedCustomerId) {
        if (pickedCustomerId != null) {
            viewModel.setPickedCustomer(pickedCustomerId)
            onPickedCustomerConsumed()
        }
    }
    // After a Sell / Layaway / Damaged / Delete action, return to the inventory list.
    LaunchedEffect(navigateBack) {
        if (navigateBack) onNavigateUp()
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHost.showSnackbar(it) }
    }
    LaunchedEffect(state.actionError) {
        state.actionError?.let {
            snackbarHost.showSnackbar(it)
            viewModel.dismissActionError()
        }
    }

    // Dialogs
    val product = state.product
    if (product != null) {
        when (state.activeDialog) {
            StatusDialog.SELL -> SoldDialog(
                availableQty = product.quantity,
                defaultPrice = state.sellingPrice,
                capitalPerUnit = product.capitalPrice,
                canDiscount = state.isAdminOrManager,
                onConfirm = { qty, price, custId, disc, discType, paymentMethod, notes, tradeInItems ->
                    viewModel.submitSell(qty, price, custId, disc, discType, paymentMethod, notes, tradeInItems)
                },
                onDismiss = viewModel::dismissDialog,
            )
            StatusDialog.LAYAWAY -> LayawayDialog(
                availableQty = product.quantity,
                defaultPrice = state.sellingPrice,
                initialCustomer = pickedCustomer,
                onConfirm = { custId, qty, price, due, downpayment ->
                    viewModel.submitLayaway(custId, qty, price, due, downpayment)
                },
                onDismiss = viewModel::dismissDialog,
                onNavigateToCustomers = onNavigateToCustomers,
            )
            StatusDialog.DAMAGED -> DamagedDialog(
                onConfirm = { reason, notes -> viewModel.submitDamaged(reason, notes) },
                onDismiss = viewModel::dismissDialog,
            )
            StatusDialog.REVERT -> RevertDialog(
                productName = product.name,
                onConfirm = { reason -> viewModel.submitRevert(reason) },
                onDismiss = viewModel::dismissDialog,
            )
            StatusDialog.DELETE -> DeleteProductDialog(
                productName = product.name,
                quantity = product.quantity,
                onConfirm = viewModel::deleteProduct,
                onDismiss = viewModel::dismissDialog,
            )
            StatusDialog.ADJUST_STOCK -> AdjustStockDialog(
                availableQty = product.quantity,
                onConfirm = { qty, reason, notes ->
                    viewModel.submitStockAdjustment(qty, reason, notes)
                },
                onDismiss = viewModel::dismissDialog,
            )
            StatusDialog.NONE -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.product?.name ?: "Product Detail") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.canEdit) {
                        state.product?.let { product ->
                            IconButton(onClick = { onNavigateToEdit(product.id) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit item")
                            }
                        }
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

            state.product == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text("Product not found") }

            else -> ProductDetailContent(
                state = state,
                onOpenDialog = viewModel::openDialog,
                modifier = Modifier.padding(padding),
                snackbarHost = snackbarHost,
                readOnly = readOnly,
            )
        }
    }
}

@Composable
private fun ProductDetailContent(
    state: ProductDetailUiState,
    onOpenDialog: (StatusDialog) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHost: SnackbarHostState,
    readOnly: Boolean = false,
) {
    val product = state.product ?: return
    val context = LocalContext.current
    val fullImageFile = state.image?.let { File(context.filesDir, "images/full/${it.fileName}") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Full-size product image
        if (fullImageFile != null) {
            AsyncImage(
                model = fullImageFile,
                contentDescription = "Product photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(240.dp),
            )
        }

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Product code + status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = product.id,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusBadge(product.status)
            }

            HorizontalDivider()

            // Core info
            InfoRow("Category", state.categoryName)
            state.metalRateName?.let { InfoRow("Metal Rate", it) }
            state.supplierName?.let { InfoRow("Supplier", it) }
            InfoRow(
                "Date Acquired",
                SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(product.dateAcquired)),
            )
            InfoRow("Pricing Type", product.pricingType.name.lowercase().replaceFirstChar { it.uppercase() })
            product.weightGrams?.let { InfoRow("Weight", "$it g") }
            product.size?.let { InfoRow("Size", it) }
            InfoRow("Quantity", product.quantity.toString())

            HorizontalDivider()

            // Pricing
            InfoRow("Capital Price", CurrencyFormatter.format(product.capitalPrice))
            state.sellingPrice?.let { InfoRow("Selling Price", CurrencyFormatter.format(it)) }

            // Profit — Admin only
            if (state.isAdmin) {
                state.profitAmount?.let { profit ->
                    val pct = state.profitMarginPct
                    val label = if (pct != null) "${"%.1f".format(pct)}% margin" else ""
                    InfoRow(
                        label = "Profit",
                        value = "${CurrencyFormatter.format(profit)}  $label",
                        valueColor = if (profit >= 0) Color(0xFF1B5E20) else MaterialTheme.colorScheme.error,
                    )
                }
            }

            product.notes?.let {
                HorizontalDivider()
                InfoRow("Notes", it)
            }

            // Status change actions — hidden in read-only mode (e.g. viewed from Layaway)
            if (!readOnly) {
                if (product.status == ProductStatus.AVAILABLE) {
                    HorizontalDivider()
                    Text("Actions", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { onOpenDialog(StatusDialog.SELL) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Sell") }
                        OutlinedButton(
                            onClick = { onOpenDialog(StatusDialog.LAYAWAY) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Layaway") }
                        OutlinedButton(
                            onClick = { onOpenDialog(StatusDialog.DAMAGED) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            modifier = Modifier.weight(1f),
                        ) { Text("Damaged") }
                    }
                    // Admin-only: write off lost/stolen/miscounted units with a reason,
                    // instead of deleting the whole product to fix a count.
                    if (state.isAdmin) {
                        OutlinedButton(
                            onClick = { onOpenDialog(StatusDialog.ADJUST_STOCK) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Adjust Stock") }
                    }
                }

                // Revert — Admin / Manager only, visible when SOLD or DAMAGED
                if (state.canRevert) {
                    HorizontalDivider()
                    OutlinedButton(
                        onClick = { onOpenDialog(StatusDialog.REVERT) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Revert Status") }
                }

                // Delete — Admin only. The use case rejects the delete if any
                // sold/layaway/damaged records still reference the product.
                if (state.canDelete) {
                    HorizontalDivider()
                    OutlinedButton(
                        onClick = { onOpenDialog(StatusDialog.DELETE) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.height(18.dp),
                        )
                        Text("  Delete Product")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteProductDialog(
    productName: String,
    quantity: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete product?") },
        text = {
            Text(
                buildString {
                    if (quantity > 1) {
                        append("This removes ALL $quantity units of \"$productName\" from inventory — not just one. ")
                        append("To remove only some units, use Adjust Stock instead. ")
                    } else {
                        append("This permanently removes \"$productName\" from inventory. ")
                    }
                    append("Products with sold, layaway, or damaged records can't be deleted — revert those first.")
                },
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text("Delete") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            modifier = Modifier.weight(0.6f),
        )
    }
}

@Composable
private fun StatusBadge(status: ProductStatus) {
    val (text, color) = when (status) {
        ProductStatus.AVAILABLE -> "Available" to Color(0xFF1B5E20)
        ProductStatus.SOLD -> "Sold" to Color(0xFF616161)
        ProductStatus.LAYAWAY -> "Layaway" to Color(0xFFE65100)
        ProductStatus.DAMAGED -> "Damaged" to Color(0xFFB71C1C)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.SemiBold,
    )
}
