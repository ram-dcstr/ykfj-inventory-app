package com.ykfj.inventory.ui.customers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykfj.inventory.domain.model.Customer

/**
 * A self-contained customer selector. Shows an [OutlinedTextField] where the
 * user types a name; a dropdown lists matching customers.
 *
 * @param selectedCustomer currently selected customer (null if none)
 * @param onCustomerSelected called when the user picks a suggestion
 * @param modifier optional modifier on the text field
 */
@Composable
fun CustomerAutoSuggest(
    selectedCustomer: Customer?,
    onCustomerSelected: (Customer) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CustomerAutoSuggestViewModel = hiltViewModel(),
) {
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf(selectedCustomer?.name.orEmpty()) }
    var expanded by remember { mutableStateOf(false) }

    // Sync query text when a customer is injected from outside or cleared after selection
    LaunchedEffect(selectedCustomer) {
        query = selectedCustomer?.name.orEmpty()
        if (selectedCustomer == null) viewModel.onQueryChange("")
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { text ->
                query = text
                viewModel.onQueryChange(text)
                expanded = text.isNotBlank()
            },
            label = { Text("Customer") },
            placeholder = { Text("Search by name…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            suggestions.forEach { customer ->
                DropdownMenuItem(
                    text = {
                        Column(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(customer.name)
                            customer.mobile?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    text = it,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    onClick = {
                        query = customer.name
                        expanded = false
                        onCustomerSelected(customer)
                    },
                )
            }
        }
    }
}
