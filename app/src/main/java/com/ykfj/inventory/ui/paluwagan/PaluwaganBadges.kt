package com.ykfj.inventory.ui.paluwagan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ykfj.inventory.data.local.db.enums.PaluwaganPaymentStatus

// ── Small shared badges / status chips used across the Paluwagan detail UI ─────

@Composable
internal fun PaymentStatusBadge(label: String, background: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .background(background, shape = MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun PasaloIndicator(originalName: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.padding(top = 2.dp),
    ) {
        Icon(
            Icons.Default.SwapHoriz,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(11.dp),
        )
        Text(
            "was $originalName",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontStyle = FontStyle.Italic,
        )
    }
}

@Composable
internal fun SlotBadge(label: String) {
    Box(
        modifier = Modifier
            .background(Color(0xFF1565C0), shape = MaterialTheme.shapes.small)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun CollectedBadge() {
    Box(
        modifier = Modifier
            .background(Color(0xFF1B5E20).copy(alpha = 0.12f), shape = MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            "✓ Collected",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF1B5E20),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/**
 * Renders a member's payment indicator for the current round.
 *
 * - UNPAID → a record-payment button (when [canRecord]) or a "DUE" badge.
 * - PAID / LATE / PRE-PAID → the status badge, plus a "pay next contribution"
 *   button when [payAheadAvailable] is set — so a member who has already covered
 *   the current round can keep paying forward (the dialog caps the amount, so
 *   they can't overpay past the last round).
 */
@Composable
internal fun PaymentBadgeOrButton(
    status: PaluwaganPaymentStatus?,
    canRecord: Boolean,
    onRecord: () -> Unit,
    payAheadAvailable: Boolean = false,
) {
    when (status) {
        PaluwaganPaymentStatus.UNPAID ->
            if (canRecord) {
                IconButton(onClick = onRecord, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Payments,
                        contentDescription = "Record payment",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                PaymentStatusBadge("DUE", Color(0xFFE65100).copy(alpha = 0.12f), Color(0xFFE65100))
            }
        null -> Text("—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when (status) {
                    PaluwaganPaymentStatus.PAID ->
                        PaymentStatusBadge("PAID", Color(0xFF1B5E20).copy(alpha = 0.12f), Color(0xFF1B5E20))
                    PaluwaganPaymentStatus.LATE ->
                        PaymentStatusBadge("LATE", Color(0xFFB71C1C).copy(alpha = 0.12f), Color(0xFFB71C1C))
                    PaluwaganPaymentStatus.PREPAID ->
                        PaymentStatusBadge("PRE-PAID", Color(0xFF1565C0).copy(alpha = 0.12f), Color(0xFF1565C0))
                    else -> Unit
                }
                if (payAheadAvailable && canRecord) {
                    IconButton(onClick = onRecord, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Payments,
                            contentDescription = "Pay next contribution",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
