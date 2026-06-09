package com.ykfj.inventory.ui.goldpurchase

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ykfj.inventory.ui.customers.CustomerAutoSuggest
import com.ykfj.inventory.util.CurrencyFormatter
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGoldPurchaseModal(
    onNavigateUp: () -> Unit,
    onSaved: (id: String) -> Unit,
    onNavigateToCustomers: () -> Unit,
    pickedCustomerId: String? = null,
    onPickedCustomerConsumed: () -> Unit = {},
    viewModel: AddGoldPurchaseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    var cameraTargetIndex by rememberSaveable { mutableIntStateOf(-1) }
    val cameraDir = remember(context) { File(context.filesDir, "images/camera").also { it.mkdirs() } }
    var cameraFilePath by rememberSaveable { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(TakePicture()) { success ->
        if (success && cameraTargetIndex >= 0) viewModel.setItemPhoto(cameraTargetIndex, cameraFilePath)
    }
    val cameraPermLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        if (granted && cameraTargetIndex >= 0) {
            val f = File(cameraDir, "gp_${System.currentTimeMillis()}.jpg").also { it.createNewFile() }
            cameraFilePath = f.absolutePath
            cameraLauncher.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f))
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        if (cameraTargetIndex >= 0) {
            val dest = File(cameraDir, "gp_g_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { dest.outputStream().use(it::copyTo) }
            viewModel.setItemPhoto(cameraTargetIndex, dest.absolutePath)
        }
    }

    LaunchedEffect(pickedCustomerId) {
        if (pickedCustomerId != null) {
            viewModel.setPickedCustomer(pickedCustomerId)
            onPickedCustomerConsumed()
        }
    }
    LaunchedEffect(state.savedId) { state.savedId?.let { onSaved(it) } }
    LaunchedEffect(state.error) { state.error?.let { snackbarHost.showSnackbar(it); viewModel.clearError() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Gold Purchase") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Customer selector (optional — Walk-in if blank)
            Row(verticalAlignment = Alignment.CenterVertically) {
                CustomerAutoSuggest(
                    selectedCustomer = state.customer,
                    onCustomerSelected = viewModel::setCustomer,
                    modifier = Modifier.weight(1f),
                )
                if (state.customer != null) {
                    IconButton(onClick = { viewModel.setCustomer(null) }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear customer")
                    }
                }
            }
            TextButton(
                onClick = onNavigateToCustomers,
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    text = "Customer not in directory? Add in Customers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                )
            }

            // Dynamic item rows
            state.items.forEachIndexed { index, draft ->
                ItemDraftCard(
                    index = index,
                    totalItems = state.items.size,
                    draft = draft,
                    onDraftChanged = { viewModel.updateItem(index, it) },
                    onRemove = { viewModel.removeItem(index) },
                    onCamera = {
                        cameraTargetIndex = index
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            val f = File(cameraDir, "gp_${System.currentTimeMillis()}.jpg").also { it.createNewFile() }
                            cameraFilePath = f.absolutePath
                            cameraLauncher.launch(
                                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f),
                            )
                        } else {
                            cameraPermLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onGallery = {
                        cameraTargetIndex = index
                        galleryLauncher.launch("image/*")
                    },
                )
            }

            // Add item button
            OutlinedButton(onClick = viewModel::addItem, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add Item")
            }

            // Notes
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )

            // Total to pay
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Total to pay:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        CurrencyFormatter.format(state.totalPaid),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Confirm button
            Button(
                onClick = viewModel::submit,
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Confirm Purchase")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemDraftCard(
    index: Int,
    totalItems: Int,
    draft: GoldPurchaseItemDraft,
    onDraftChanged: (GoldPurchaseItemDraft) -> Unit,
    onRemove: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
) {
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Item ${index + 1}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                IconButton(onClick = onRemove, enabled = totalItems > 1) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove item",
                        tint = if (totalItems > 1) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = draft.description,
                onValueChange = { onDraftChanged(draft.copy(description = it)) },
                label = { Text("Description *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            PurityDropdown(
                selected = draft.purity,
                onSelected = { onDraftChanged(draft.copy(purity = it)) },
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.weightGrams,
                    onValueChange = { onDraftChanged(draft.copy(weightGrams = it.filter { c -> c.isDigit() || c == '.' })) },
                    label = { Text("Weight (g) *") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = draft.buyRatePerGram,
                    onValueChange = { onDraftChanged(draft.copy(buyRatePerGram = it.filter { c -> c.isDigit() || c == '.' })) },
                    label = { Text("Buy rate/g *") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }

            draft.computedValue?.let {
                Text(
                    "Value: ${CurrencyFormatter.format(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Override price?", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = draft.overrideEnabled,
                    onCheckedChange = { onDraftChanged(draft.copy(overrideEnabled = it)) },
                )
            }
            if (draft.overrideEnabled) {
                OutlinedTextField(
                    value = draft.overrideValue,
                    onValueChange = { onDraftChanged(draft.copy(overrideValue = it.filter { c -> c.isDigit() || c == '.' })) },
                    label = { Text("Override price (₱)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }

            draft.finalValue?.let {
                Text(
                    "You pay: ${CurrencyFormatter.format(it)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (draft.photoUri != null) {
                    AsyncImage(
                        model = draft.photoUri,
                        contentDescription = "Item photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { onDraftChanged(draft.copy(photoUri = null)) }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove photo")
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCamera) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Text(" Camera")
                }
                TextButton(onClick = onGallery) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Text(" Gallery")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurityDropdown(
    selected: String?,
    onSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val displayValue = selected ?: "None (optional)"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            label = { Text("Purity (optional)") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("None") }, onClick = { onSelected(null); expanded = false })
            GoldPurityOptions.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p) },
                    onClick = { onSelected(p); expanded = false },
                )
            }
        }
    }
}
