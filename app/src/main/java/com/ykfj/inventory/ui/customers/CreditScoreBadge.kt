package com.ykfj.inventory.ui.customers

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class CreditTier(val label: String) {
    EXCELLENT("Excellent"),
    GOOD("Good"),
    FAIR("Fair"),
    POOR("Poor"),
}

fun creditTier(score: Int): CreditTier = when {
    score >= 90 -> CreditTier.EXCELLENT
    score >= 70 -> CreditTier.GOOD
    score >= 50 -> CreditTier.FAIR
    else -> CreditTier.POOR
}

@Composable
fun CreditScoreBadge(score: Int, modifier: Modifier = Modifier) {
    val tier = creditTier(score)
    val containerColor = when (tier) {
        CreditTier.EXCELLENT -> Color(0xFF1B5E20)
        CreditTier.GOOD -> Color(0xFF2E7D32)
        CreditTier.FAIR -> Color(0xFFF57F17)
        CreditTier.POOR -> Color(0xFFB71C1C)
    }
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier,
    ) {
        Text(
            text = tier.label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
