package com.ykfj.inventory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ykfj.inventory.ui.theme.YkfjInventoryTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity Compose host. Navigation and the sidebar shell are added in Phase 1.5.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YkfjInventoryTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Placeholder(modifier = Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
private fun Placeholder(modifier: Modifier = Modifier) {
    Text(text = "YKFJ Inventory — Phase 1.1 scaffold", modifier = modifier)
}
