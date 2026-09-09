package com.yamone.games.sudoku.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yamone.games.sudoku.R

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
    val context = LocalContext.current
    val pinkTheme = accent == YamonePink || accent == YamonePinkDark || accent == YamonePinkSoft || accent == YamonePinkLine
    val imageRes = when (mascot) {
        YamoneMascot.SEAL -> if (pinkTheme) R.drawable.yamone_seal_pink else R.drawable.yamone_seal_mint
        YamoneMascot.BEAR -> if (pinkTheme) R.drawable.yamone_bear_pink else R.drawable.yamone_bear_mint
    }
    val image = remember(imageRes, mascot) {
        maskApprovedMascot(BitmapFactory.decodeResource(context.resources, imageRes), mascot).asImageBitmap()
    }

    Image(
        bitmap = image,
        contentDescription = mascot.label,
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit
    )
}

/**
 * 승인된 마스코트 원본은 유지하면서 외곽 배경만 제거한다.
 * 직선 다각형 대신 곡선 마스크를 사용하고 경계를 아주 조금 안쪽으로 당겨
 * 작은 크기에서도 흰 halo·잔픽셀·들쭉날쭉한 외곽선이 보이지 않게 한다.
 * 게임 판정 영역과는 별개다.
 */
private fun maskApprovedMascot(source: Bitmap, mascot: YamoneMascot): Bitmap {
    val width = source.width.toFloat()
    val height = source.height.toFloat()
    val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(output)
    val maskPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = AndroidPaint.Style.FILL
        isDither = true
    }
    val imagePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.FILTER_BITMAP_FLAG).apply {
        isDither = true
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }

    val rawPoints = when (mascot) {
        YamoneMascot.SEAL -> listOf(
            .20f to .31f, .25f to .22f, .39f to .15f, .55f to .14f,
            .68f to .18f, .73f to .27f, .73f to .43f, .70f to .49f,
            .78f to .45f, .88f to .48f, .95f to .57f, .95f to .68f,
            .89f to .75f, .82f to .76f, .78f to .83f, .66f to .88f,
            .50f to .90f, .34f to .88f, .23f to .84f, .15f to .77f,
            .13f to .68f, .16f to .61f, .23f to .55f, .24f to .48f,
            .19f to .42f, .18f to .36f
        )
        YamoneMascot.BEAR -> listOf(
            .16f to .31f, .18f to .22f, .26f to .16f, .36f to .15f,
            .43f to .12f, .52f to .14f, .59f to .09f, .69f to .11f,
            .77f to .17f, .80f to .26f, .79f to .39f, .75f to .50f,
            .73f to .56f, .79f to .63f, .82f to .72f, .80f to .80f,
            .74f to .87f, .65f to .91f, .51f to .93f, .37f to .92f,
            .26f to .88f, .20f to .82f, .19f to .73f, .22f to .65f,
            .23f to .58f, .20f to .50f, .17f to .42f
        )
    }

    val inset = 0.006f
    val points = rawPoints.map { (x, y) ->
        val nx = x + (0.5f - x) * inset
        val ny = y + (0.5f - y) * inset
        nx * width to ny * height
    }

    val path = AndroidPath()
    val last = points.last()
    val first = points.first()
    path.moveTo((last.first + first.first) / 2f, (last.second + first.second) / 2f)
    points.forEachIndexed { index, point ->
        val next = points[(index + 1) % points.size]
        val midX = (point.first + next.first) / 2f
        val midY = (point.second + next.second) / 2f
        path.quadTo(point.first, point.second, midX, midY)
    }
    path.close()

    canvas.drawPath(path, maskPaint)
    canvas.drawBitmap(source, 0f, 0f, imagePaint)
    imagePaint.xfermode = null
    return output
}
