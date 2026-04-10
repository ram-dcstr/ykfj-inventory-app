package com.ykfj.inventory.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Gold40,
    secondary = GoldGrey40,
    tertiary = Amber40
)

private val DarkColors = darkColorScheme(
    primary = Gold80,
    secondary = GoldGrey80,
    tertiary = Amber80
)

@Composable
fun YkfjInventoryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
