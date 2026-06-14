package com.ykfj.inventory.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ykfj.inventory.data.local.db.enums.DiscountType
import com.ykfj.inventory.data.local.db.enums.PaymentMethod
import com.ykfj.inventory.domain.model.Customer
import com.ykfj.inventory.ui.components.PaymentMethodPicker
import com.ykfj.inventory.ui.customers.CustomerAutoSuggest
import com.ykfj.inventory.ui.goldpurchase.GoldPurchaseItemDraft
import com.ykfj.inventory.ui.goldpurchase.GoldPurchaseItemDraftListSaver
import com.ykfj.inventory.util.CurrencyFormatter

/**
 * Dialog to mark a product as sold — with an optional trade-in mode.
 *
 * When the user toggles "Pay with trade-in?" on, the dialog grows an inline list of
 * scrap items they're handing over (description / weight / buy rate / optional override).
 * The net math line shows whether the customer still owes cash or the shop pays out the
 * difference. Confirming routes through [com.ykfj.inventory.domain.usecase.goldpurchase.SellWithTradeInUseCase]
 * on the ViewModel so both sides land atomically.
 *
 * @param availableQty   current available units
 * @param defaultPrice   pre-filled selling price per unit
 * @param capitalPerUnit capital per unit (used to compute max discount)
 * @param canDiscount    true for Admin / Manager
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoldDialog(
    availableQty: Int,
    defaultPrice: Double?,
    capitalPerUnit: Double,
    canDiscount: Boolean,
    onConfirm: (
        qty: Int,
        soldPrice: Double,
        customerId: String?,
        discountAmount: Double,
        discountType: DiscountType,
        paymentMethod: PaymentMethod,
        notes: String?,
        tradeInItems: List<GoldPurchaseItemDraft>,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    var qtyText by rememberSaveable { mutableStateOf("1") }
    var priceText by rememberSaveable { mutableStateOf(defaultPrice?.let { "%.2f".format(it) }.orEmpty()) }
    var discountText by rememberSaveable { mutableStateOf("") }
    var selectedCustomer by rememberSaveable { mutableStateOf<Customer?>(null) }
    var selectedPaymentMethod by rememberSaveable { mutableStateOf(PaymentMethod.CASH) }
    var notes by rememberSaveable { mutableStateOf("") }
    var tradeInEnabled by rememberSaveable { mutableStateOf(false) }
    // rememberSaveable so trade-in items survive rotation / process death while
    // the dialog is open. Without this the user loses every row they added.
    var tradeInItems by rememberSaveable(stateSaver = GoldPurchaseItemDraftListSaver) {
        mutableStateOf(listOf(GoldPurchaseItemDraft()))
    }

    val qty = qtyText.toIntOrNull() ?: 0
    val qtyError = qty < 1 || qty > availableQty
    val pricePerUnit = priceText.toDoubleOrNull() ?: 0.0
    val discountPerUnit = discountText.toDoubleOrNull() ?: 0.0
    val maxDiscount = ((pricePerUnit - capitalPerUnit) * 0.20).coerceAtLeast(0.0)
    val finalPrice = (pricePerUnit - discountPerUnit).coerceAtLeast(0.0)
    val saleTotal = finalPrice * qty
    val tradeInTotal = if (tradeInEnabled) tradeInItems.sumOf { it.finalValue ?: 0.0 } else 0.0
    val net = saleTotal - tradeInTotal

    // Trade-in items are only required when the toggle is on, and each must have a
    // description plus a finalValue > 0. We don't surface per-item errors here — the
    // confirm button just stays disabled until everything is valid.
    val tradeInItemsValid = !tradeInEnabled || tradeInItems.all { it.isValid }
    val isValid = !qtyError && pricePerUnit > 0 && discountPerUnit <= maxDiscount && tradeInItemsValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tradeInEnabled) "Sell with Trade-in" else "Mark as Sold") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Customer (optional; required for trade-in? — no, walk-ins can trade in too)
                CustomerAutoSuggest(
                    selectedCustomer = selectedCustomer,
                    onCustomerSelected = { selectedCustomer = it },
                )

                // Quantity + Price
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it.filter { c -> c.isDigit() } },
                        label = { Text("Qty (max $availableQty)") },
                        isError = qtyText.isNotBlank() && qtyError,
                        supportingText = if (qtyText.isNotBlank() && qtyError) {
                            { Text("1–$availableQty") }
                        } else null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Price / unit (₱)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }

                // Discount (Admin/Manager only)
                if (canDiscount) {
                    OutlinedTextField(
                        value = discountText,
                        onValueChange = { discountText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Discount / unit (₱)") },
                        supportingText = { Text("Max: ${CurrencyFormatter.format(maxDiscount)}") },
                        isError = discountPerUnit > maxDiscount,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Payment Method
                Text("Payment Method", style = MaterialTheme.typography.labelMedium)
                PaymentMethodPicker(
                    selected = selectedPaymentMethod,
                    onSelected = { selectedPaymentMethod = it },
                    modifier = Modifier.fillMaxWidth(),
                )

                // Notes (optional)
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                HorizontalDivider()

                // ── Trade-in section ──────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Pay with trade-in?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Switch(
                        checked = tradeInEnabled,
                        onCheckedChange = { tradeInEnabled = it },
                    )
                }
                if (tradeInEnabled) {
                    Text(
                        text = "Scrap or 2nd-hand jewelry the customer hands over as part of payment.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    tradeInItems.forEachIndexed { index, draft ->
                        TradeInItemRow(
                            index = index,
                            draft = draft,
                            canRemove = tradeInItems.size > 1,
                            onChange = { updated ->
                                tradeInItems = tradeInItems.toMutableList().also { it[index] = updated }
                            },
                            onRemove = {
                                tradeInItems = tradeInItems.toMutableList().also { it.removeAt(index) }
                            },
                        )
                    }
                    OutlinedButton(
                        onClick = { tradeInItems = tradeInItems + GoldPurchaseItemDraft() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(" Add scrap item")
                    }
                }

                // ── Summary line ──────────────────────────────────────────────
                if (pricePerUnit > 0) {
                    HorizontalDivider()
                    if (!tradeInEnabled) {
                        Text(
                            text = "Total: ${CurrencyFormatter.format(saleTotal)}  (${CurrencyFormatter.format(finalPrice)}/unit)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "Sale: ${CurrencyFormatter.format(saleTotal)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "− Trade-in: ${CurrencyFormatter.format(tradeInTotal)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = when {
                                    net > 0 -> "Customer pays: ${CurrencyFormatter.format(net)}"
                                    net < 0 -> "Shop pays customer: ${CurrencyFormatter.format(-net)}"
                                    else -> "Even swap"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (net < 0) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isValid) {
                        onConfirm(
                            qty, finalPrice, selectedCustomer?.id,
                            discountPerUnit,
                            if (discountPerUnit > 0) DiscountType.FIXED else DiscountType.NONE,
                            selectedPaymentMethod,
                            notes.ifBlank { null },
                            if (tradeInEnabled) tradeInItems else emptyList(),
                        )
                    }
                },
                enabled = isValid,
            ) { Text(if (tradeInEnabled) "Confirm Trade-in Sale" else "Confirm Sale") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Compact one-card editor for a single trade-in item inside [SoldDialog]. Deliberately
 * lighter than [com.ykfj.inventory.ui.goldpurchase.AddGoldPurchaseModal]'s ItemDraftCard —
 * no photo or purity here, since the sell flow prioritises checkout speed. Customers who
 * want a fully documented purchase (with photos, multi-line notes) should use the Gold
 * Purchases screen for that and pay cash for the sale separately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TradeInItemRow(
    index: Int,
    draft: GoldPurchaseItemDraft,
    canRemove: Boolean,
    onChange: (GoldPurchaseItemDraft) -> Unit,
    onRemove: () -> Unit,
) {
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Item ${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove item",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            OutlinedTextField(
                value = draft.description,
                onValueChange = { onChange(draft.copy(description = it)) },
                label = { Text("Description *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.weightGrams,
                    onValueChange = { onChange(draft.copy(weightGrams = it.filter { c -> c.isDigit() || c == '.' })) },
                    label = { Text("Weight (g) *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = draft.buyRatePerGram,
                    onValueChange = { onChange(draft.copy(buyRatePerGram = it.filter { c -> c.isDigit() || c == '.' })) },
                    label = { Text("Buy rate/g *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Override price?", style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = draft.overrideEnabled,
                    onCheckedChange = { onChange(draft.copy(overrideEnabled = it)) },
                )
            }
            if (draft.overrideEnabled) {
                OutlinedTextField(
                    value = draft.overrideValue,
                    onValueChange = { onChange(draft.copy(overrideValue = it.filter { c -> c.isDigit() || c == '.' })) },
                    label = { Text("Override (₱)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            draft.finalValue?.let {
                Text(
                    "Worth: ${CurrencyFormatter.format(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
