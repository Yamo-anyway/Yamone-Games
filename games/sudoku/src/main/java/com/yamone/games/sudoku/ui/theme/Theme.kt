package com.yamone.games.sudoku.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val YamoneMint = Color(0xFF3BC9B0)
val YamoneMintDark = Color(0xFF158C7A)
val YamoneMintSoft = Color(0xFFE5F8F4)
val YamoneMintLine = Color(0xFFB9E9DF)
val YamonePink = Color(0xFFFF7FA4)
val YamonePinkSoft = Color(0xFFFFE8EF)
val YamoneCream = Color(0xFFFFFDF9)
val YamoneInk = Color(0xFF24343A)
val YamoneMuted = Color(0xFF73858B)
val YamoneError = Color(0xFFD95963)

private val YamoneColors = lightColorScheme(
    primary = YamoneMint,
    onPrimary = Color.White,
    secondary = YamonePink,
    onSecondary = Color.White,
    background = YamoneCream,
    onBackground = YamoneInk,
    surface = Color.White,
    onSurface = YamoneInk,
    error = YamoneError
)

@Composable
fun YamoneSudokuTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = YamoneColors, content = content)
}
