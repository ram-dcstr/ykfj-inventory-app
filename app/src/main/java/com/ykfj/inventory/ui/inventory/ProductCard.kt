package com.ykfj.inventory.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ykfj.inventory.data.local.db.enums.ProductStatus
import com.ykfj.inventory.domain.model.Product
import com.ykfj.inventory.util.CurrencyFormatter
import java.io.File

/**
 * A single row card for the inventory list.
 *
 * @param thumbFileName optional filename in `filesDir/images/thumb/` — shown as thumbnail if present.
 * @param sellingPrice  computed selling price (WEIGHTED: weight × rate, FIXED: stored value).
 */
@Composable
fun ProductCard(
    product: Product,
    thumbFileName: String?,
    sellingPrice: Double?,
    categoryName: String,
    metalRateName: String?,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val thumbFile = thumbFileName?.let { File(context.filesDir, "images/thumb/$it") }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.heightIn(min = 96.dp)) {
            // Thumbnail or placeholder
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.width(96.dp).fillMaxHeight(),
            ) {
                if (thumbFile != null) {
                    AsyncImage(
                        model = thumbFile,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxHeight(),
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Diamond,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
            }

            // Info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    CardStatusBadge(product.status)
                }

                Text(
                    text = product.id,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Category + metal rate + weight hint
                    val gramsStr = product.weightGrams?.let { "${it}g" }
                    val subtitle = buildString {
                        append(categoryName)
                        if (metalRateName != null) append(" · $metalRateName")
                        if (gramsStr != null) append(" · $gramsStr")
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // Selling price + quantity
                    Column(horizontalAlignment = Alignment.End) {
                        sellingPrice?.let {
                            Text(
                                text = CurrencyFormatter.format(it),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            text = "Qty: ${product.quantity}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Notes — only shown when present
                if (!product.notes.isNullOrBlank()) {
                    Text(
                        text = product.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CardStatusBadge(status: ProductStatus) {
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
