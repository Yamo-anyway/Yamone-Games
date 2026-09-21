package com.yamone.spiritshift.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SpiritColors = darkColorScheme(
    primary = Color(0xFF8CB8FF),
    secondary = Color(0xFFC8A6FF),
    tertiary = Color(0xFFFFC55B),
    background = Color(0xFF08111F),
    surface = Color(0xFF0E1A2D),
    surfaceVariant = Color(0xFF15243B),
    onPrimary = Color(0xFF071326),
    onBackground = Color(0xFFF1F5FF),
    onSurface = Color(0xFFF1F5FF),
)

@Composable
fun SpiritShiftTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SpiritColors, content = content)
}
