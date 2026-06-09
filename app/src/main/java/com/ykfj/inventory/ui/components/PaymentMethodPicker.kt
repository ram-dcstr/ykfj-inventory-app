package com.ykfj.inventory.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ykfj.inventory.data.local.db.enums.PaymentMethod

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentMethodPicker(
    selected: PaymentMethod,
    onSelected: (PaymentMethod) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        PaymentMethod.entries.forEachIndexed { index, method ->
            SegmentedButton(
                selected = selected == method,
                onClick = { onSelected(method) },
                shape = SegmentedButtonDefaults.itemShape(index, PaymentMethod.entries.size),
                label = { Text(method.label) },
            )
        }
    }
}
