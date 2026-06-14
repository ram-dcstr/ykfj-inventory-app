package com.ykfj.inventory.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ykfj.inventory.data.local.db.enums.PaymentMethod

/**
 * Single-select payment-method picker.
 *
 * Uses wrapping [FilterChip]s rather than a fixed [androidx.compose.material3.SegmentedButton]
 * row: a long label like "Online Banking" stays fully readable and wraps to a second
 * line on narrow phone screens instead of overflowing the segment, while still fitting
 * on one row on the tablet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaymentMethodPicker(
    selected: PaymentMethod,
    onSelected: (PaymentMethod) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PaymentMethod.entries.forEach { method ->
            val isSelected = selected == method
            FilterChip(
                selected = isSelected,
                onClick = { onSelected(method) },
                label = { Text(method.label) },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}
