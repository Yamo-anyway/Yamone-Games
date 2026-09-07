package com.yamone.games.sudoku.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class YamoneThemeMode(val label: String) {
    MINT("민트"),
    PINK("핑크")
}

enum class YamoneMascot(val label: String) {
    SEAL("아기물범"),
    BEAR("아기곰")
}

val YamoneMint = Color(0xFF3BC9B0)
val YamoneMintDark = Color(0xFF158C7A)
val YamoneMintSoft = Color(0xFFE5F8F4)
val YamoneMintLine = Color(0xFFB9E9DF)
val YamonePink = Color(0xFFFF7FA4)
val YamonePinkDark = Color(0xFFD9577D)
val YamonePinkSoft = Color(0xFFFFE8EF)
val YamonePinkLine = Color(0xFFFFC4D5)
val YamoneCream = Color(0xFFFFFDF9)
val YamoneInk = Color(0xFF24343A)
val YamoneMuted = Color(0xFF73858B)
val YamoneError = Color(0xFFD95963)

fun yamonePrimary(mode: YamoneThemeMode) = if (mode == YamoneThemeMode.MINT) YamoneMint else YamonePink
fun yamonePrimaryDark(mode: YamoneThemeMode) = if (mode == YamoneThemeMode.MINT) YamoneMintDark else YamonePinkDark
fun yamonePrimarySoft(mode: YamoneThemeMode) = if (mode == YamoneThemeMode.MINT) YamoneMintSoft else YamonePinkSoft
fun yamonePrimaryLine(mode: YamoneThemeMode) = if (mode == YamoneThemeMode.MINT) YamoneMintLine else YamonePinkLine
fun yamoneSecondary(mode: YamoneThemeMode) = if (mode == YamoneThemeMode.MINT) YamonePink else YamoneMint
fun yamoneSecondarySoft(mode: YamoneThemeMode) = if (mode == YamoneThemeMode.MINT) YamonePinkSoft else YamoneMintSoft

@Composable
fun YamoneSudokuTheme(mode: YamoneThemeMode = YamoneThemeMode.MINT, content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = yamonePrimary(mode),
        onPrimary = Color.White,
        secondary = yamoneSecondary(mode),
        onSecondary = Color.White,
        background = YamoneCream,
        onBackground = YamoneInk,
        surface = Color.White,
        onSurface = YamoneInk,
        error = YamoneError
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun YamoneMascotIcon(
    mascot: YamoneMascot,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    when (mascot) {
        YamoneMascot.SEAL -> SealIcon(modifier.size(size), accent)
        YamoneMascot.BEAR -> BearIcon(modifier.size(size), accent)
    }
}

@Composable
private fun SealIcon(modifier: Modifier, accent: Color) {
    Canvas(modifier) {
        drawCircle(Color.White, radius = size.minDimension * 0.43f)
        drawCircle(Color(0xFFEAF3F3), radius = size.minDimension * 0.35f, center = Offset(size.width * .50f, size.height * .54f))
        drawCircle(YamoneInk, radius = size.minDimension * 0.035f, center = Offset(size.width * .40f, size.height * .44f))
        drawCircle(YamoneInk, radius = size.minDimension * 0.035f, center = Offset(size.width * .60f, size.height * .44f))
        drawCircle(YamonePink, radius = size.minDimension * 0.03f, center = Offset(size.width * .50f, size.height * .54f))
        drawArc(accent, 190f, 160f, false, style = Stroke(size.width * .065f))
    }
}

@Composable
private fun BearIcon(modifier: Modifier, accent: Color) {
    Canvas(modifier) {
        val brown = Color(0xFFD49B7B)
        val face = Color(0xFFE8B493)
        drawCircle(brown, radius = size.minDimension * .145f, center = Offset(size.width * .27f, size.height * .25f))
        drawCircle(brown, radius = size.minDimension * .145f, center = Offset(size.width * .73f, size.height * .25f))
        drawCircle(face, radius = size.minDimension * .39f)
        drawCircle(YamoneInk, radius = size.minDimension * .035f, center = Offset(size.width * .41f, size.height * .45f))
        drawCircle(YamoneInk, radius = size.minDimension * .035f, center = Offset(size.width * .59f, size.height * .45f))
        drawCircle(YamonePink, radius = size.minDimension * .05f, center = Offset(size.width * .50f, size.height * .56f))
        drawArc(accent, 200f, 140f, false, style = Stroke(size.width * .045f), topLeft = Offset(size.width * .36f, size.height * .51f), size = androidx.compose.ui.geometry.Size(size.width * .28f, size.height * .22f))
    }
}
