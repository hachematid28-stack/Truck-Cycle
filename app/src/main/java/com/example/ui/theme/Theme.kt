package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val IndustrialColorScheme = darkColorScheme(
    primary = IndustrialYellow,
    onPrimary = IndustrialBlack,
    secondary = IndustrialCharcoal,
    onSecondary = IndustrialWhite,
    tertiary = IndustrialAmber,
    onTertiary = IndustrialBlack,
    background = IndustrialBlack,
    onBackground = IndustrialWhite,
    surface = IndustrialSurface,
    onSurface = IndustrialWhite,
    error = SafetyRed,
    onError = IndustrialWhite
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = IndustrialColorScheme,
        typography = Typography,
        content = content
    )
}
