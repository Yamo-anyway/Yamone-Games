package com.yamone.games.sudoku.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
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

private const val GOOGLE_FONTS_DEV_CERT = "MIIEqDCCA5CgAwIBAgIJANWFuGx90071MA0GCSqGSIb3DQEBBAUAMIGUMQswCQYDVQQGEwJVUzETMBEGA1UECBMKQ2FsaWZvcm5pYTEWMBQGA1UEBxMNTW91bnRhaW4gVmlldzEQMA4GA1UEChMHQW5kcm9pZDEQMA4GA1UECxMHQW5kcm9pZDEQMA4GA1UEAxMHQW5kcm9pZDEiMCAGCSqGSIb3DQEJARYTYW5kcm9pZEBhbmRyb2lkLmNvbTAeFw0wODA0MTUyMzM2NTZaFw0zNTA5MDEyMzM2NTZaMIGUMQswCQYDVQQGEwJVUzETMBEGA1UECBMKQ2FsaWZvcm5pYTEWMBQGA1UEBxMNTW91bnRhaW4gVmlldzEQMA4GA1UEChMHQW5kcm9pZDEQMA4GA1UECxMHQW5kcm9pZDEQMA4GA1UEAxMHQW5kcm9pZDEiMCAGCSqGSIb3DQEJARYTYW5kcm9pZEBhbmRyb2lkLmNvbTCCASAwDQYJKoZIhvcNAQEBBQADggENADCCAQgCggEBANbOLggKv+IxTdGNs8/TGFy0PTP6DHThvbbR24kT9ixcOd9W+EaBPWW+wPPKQmsHxajtWjmQwWfna8mZuSeJS48LIgAZlKkpFeVyxW0qMBujb8X8ETrWy550NaFtI6t9+u7hZeTfHwqNvacKhp1RbE6dBRGWynwMVX8XW8N1+UjFaq6GCJukT4qmpN2afb8sCjUigq0GuMwYXrFVee74bQgLHWGJwPmvmLHC69EH6kWr22ijx4OKXlSIx2xT1AsSHee70w5iDBiK4aph27yH3TxkXy9V89TDdexAcKk/cVHYNnDBapcavl7y0RiQ4biu8ymM8Ga/nmzhRKya6G0cGw8CAQOjgfwwgfkwHQYDVR0OBBYEFI0cxb6VTEM8YYY6FbBMvAPyT+CyMIHJBgNVHSMEgcEwgb6AFI0cxb6VTEM8YYY6FbBMvAPyT+CyoYGapIGXMIGUMQswCQYDVQQGEwJVUzETMBEGA1UECBMKQ2FsaWZvcm5pYTEWMBQGA1UEBxMNTW91bnRhaW4gVmlldzEQMA4GA1UEChMHQW5kcm9pZDEQMA4GA1UECxMHQW5kcm9pZDEQMA4GA1UEAxMHQW5kcm9pZDEiMCAGCSqGSIb3DQEJARYTYW5kcm9pZEBhbmRyb2lkLmNvbYIJANWFuGx90071MAwGA1UdEwQFMAMBAf8wDQYJKoZIhvcNAQEEBQADggEBABnTDPEF+3iSP0wNfdIjIz1AlnrPzgAIHVvXxunW7SBrDhEglQZBbKJEk5kT0mtKoOD1JMrSu1xuTKEBahWRbqHsXclaXjoBADb0kkjVEJu/Lh5hgYZnOjvlba8Ld7HCKePCVePoTJBdI4fvugnL8TsgK05aIskyY0hKI9L8KfqfGTl1lzOv2KoWD0KWwtAWPoGChZxmQ+nBli+gwYMzM1vAkP+aayLe0a1EQimlOalO762r0GXO0ks+UeXde2Z4e+8S/pf7pITEI/tP+MxJTALw9QUWEv9lKTk+jkbqxbsh8nfBUapfKqYn0eidpwq2AzVp3juYl7//fKnaPhJD9gs="
private const val GOOGLE_FONTS_PROD_CERT = "MIIEQzCCAyugAwIBAgIJAMLgh0ZkSjCNMA0GCSqGSIb3DQEBBAUAMHQxCzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpDYWxpZm9ybmlhMRYwFAYDVQQHEw1Nb3VudGFpbiBWaWV3MRQwEgYDVQQKEwtHb29nbGUgSW5jLjEQMA4GA1UECxMHQW5kcm9pZDEQMA4GA1UEAxMHQW5kcm9pZDAeFw0wODA4MjEyMzEzMzRaFw0zNjAxMDcyMzEzMzRaMHQxCzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpDYWxpZm9ybmlhMRYwFAYDVQQHEw1Nb3VudGFpbiBWaWV3MRQwEgYDVQQKEwtHb29nbGUgSW5jLjEQMA4GA1UECxMHQW5kcm9pZDEQMA4GA1UEAxMHQW5kcm9pZDCCASAwDQYJKoZIhvcNAQEBBQADggENADCCAQgCggEBAKtWLgDYO6IIrgqWbxJOKdoR8qtW0I9Y4sypEwPpt1TTcvZApxsdyxMJZ2JORland2qSGT2y5b+3JKkedxiLDmpHpDsz2WCbdxgxRczfey5YZnTJ4VZbH0xqWVW/8lGmPav5xVwnIiJS6HXk+BVKZF+JcWjAsb/GEuq/eFdpuzSqeYTcfi6idkyugwfYwXFU1+5fZKUaRKYCwkkFQVfcAs1fXA5V+++FGfvjJ/CxURaSxaBvGdGDhfXE28LWuT9ozCl5xw4Yq5OGazvV24mZVSoOO0yZ31j7kYvtwYK6NeADwbSxDdJEqO4k//0zOHKrUiGYXtqw/A0LFFtqoZKFjnkCAQOjgdkwgdYwHQYDVR0OBBYEFMd9jMIhF1Ylmn/Tgt9r45jk14alMIGmBgNVHSMEgZ4wgZuAFMd9jMIhF1Ylmn/Tgt9r45jk14aloXikdjB0MQswCQYDVQQGEwJVUzETMBEGA1UECBMKQ2FsaWZvcm5pYTEWMBQGA1UEBxMNTW91bnRhaW4gVmlldzEUMBIGA1UEChMLR29vZ2xlIEluYy4xEDAOBgNVBAsTB0FuZHJvaWQxEDAOBgNVBAMTB0FuZHJvaWSCCQDC4IdGZEowjTAMBgNVHRMEBTADAQH/MA0GCSqGSIb3DQEBBAUAA4IBAQBt0lLO74UwLDYKqs6Tm8/yzKkEu116FmH4rkaymUIE0P9KaMftGlMexFlaYjzmB2OxZyl6euNXEsQH8gjwyxCUKRJNexBiGcCEyj6z+a1fuHHvkiaai+KL8W1EyNmgjmyy8AW7P+LLlkR+ho5zEHatRbM/YAnqGcFh5iZBqpknHf1SKMXFh4dd239FJ1jWYfbMDMy3NS5CTMQ2XFI1MvcyUTdZPErjQfTbQe3aDQsQcafEQPD+nqActifKZ0Np0IS9L9kR/wbNvyz6ENwPiTrjV2KRkEjH78ZMcUQXg0L3BYHJ3lc69Vs5Ddf9uUGGMYldX3WfMBEmh/9iFBDAaTCK"

private val yamoneGoogleFontsProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = listOf(
        listOf(Base64.decode(GOOGLE_FONTS_DEV_CERT, Base64.DEFAULT)),
        listOf(Base64.decode(GOOGLE_FONTS_PROD_CERT, Base64.DEFAULT))
    )
)

private val yamoneJua = GoogleFont("Jua")
private val yamoneGowunDodum = GoogleFont("Gowun Dodum")

val YamoneDisplayFontFamily = FontFamily(
    Font(
        googleFont = yamoneJua,
        fontProvider = yamoneGoogleFontsProvider,
        weight = FontWeight.Normal
    )
)

val YamoneBodyFontFamily = FontFamily(
    Font(
        googleFont = yamoneGowunDodum,
        fontProvider = yamoneGoogleFontsProvider,
        weight = FontWeight.Normal
    )
)

private val DefaultTypography = Typography()

val YamoneTypography = Typography(
    displayLarge = DefaultTypography.displayLarge.copy(fontFamily = YamoneDisplayFontFamily),
    displayMedium = DefaultTypography.displayMedium.copy(fontFamily = YamoneDisplayFontFamily),
    displaySmall = DefaultTypography.displaySmall.copy(fontFamily = YamoneDisplayFontFamily),
    headlineLarge = DefaultTypography.headlineLarge.copy(fontFamily = YamoneDisplayFontFamily),
    headlineMedium = DefaultTypography.headlineMedium.copy(fontFamily = YamoneDisplayFontFamily),
    headlineSmall = DefaultTypography.headlineSmall.copy(fontFamily = YamoneDisplayFontFamily),
    titleLarge = DefaultTypography.titleLarge.copy(fontFamily = YamoneDisplayFontFamily),
    titleMedium = DefaultTypography.titleMedium.copy(fontFamily = YamoneDisplayFontFamily),
    titleSmall = DefaultTypography.titleSmall.copy(fontFamily = YamoneDisplayFontFamily),
    labelLarge = DefaultTypography.labelLarge.copy(fontFamily = YamoneDisplayFontFamily),
    labelMedium = DefaultTypography.labelMedium.copy(fontFamily = YamoneDisplayFontFamily),
    labelSmall = DefaultTypography.labelSmall.copy(fontFamily = YamoneDisplayFontFamily),
    bodyLarge = DefaultTypography.bodyLarge.copy(fontFamily = YamoneBodyFontFamily),
    bodyMedium = DefaultTypography.bodyMedium.copy(fontFamily = YamoneBodyFontFamily),
    bodySmall = DefaultTypography.bodySmall.copy(fontFamily = YamoneBodyFontFamily)
)

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
    MaterialTheme(colorScheme = colors, typography = YamoneTypography) {
        ProvideTextStyle(value = YamoneTypography.bodyMedium, content = content)
    }
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
        YamoneMascot.SEAL -> if (pinkTheme) com.yamone.games.sudoku.R.drawable.yamone_seal_pink else com.yamone.games.sudoku.R.drawable.yamone_seal_mint
        YamoneMascot.BEAR -> if (pinkTheme) com.yamone.games.sudoku.R.drawable.yamone_bear_pink else com.yamone.games.sudoku.R.drawable.yamone_bear_mint
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
