package com.yamone.games

import android.Manifest
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.yamone.games.arcadecore.ArcadeGameId
import com.yamone.games.arcadecore.ArcadeRecord
import com.yamone.games.sudoku.ui.theme.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class ShareCardRequest(
    val game: ArcadeGameId,
    val record: ArcadeRecord
)

private const val STORE_LINK = ""

internal fun arcadeGameTitle(game: ArcadeGameId): String = when (game) {
    ArcadeGameId.ICE_JUMP -> "빙하 점프"
    ArcadeGameId.FISH_MUNCH -> "물고기 냠냠"
    ArcadeGameId.SNOW_RUSH -> "눈덩이 러시"
}

internal fun arcadeScoreText(game: ArcadeGameId, score: Int): String = when (game) {
    ArcadeGameId.ICE_JUMP -> "${score}m"
    ArcadeGameId.FISH_MUNCH -> "${score}마리"
    ArcadeGameId.SNOW_RUSH -> formatArcadeDuration(score)
}

internal fun arcadeEndedAtText(epochMillis: Long): String =
    SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault()).format(Date(epochMillis))

private fun formatArcadeDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}

@Composable
internal fun ShareCardScreen(
    request: ShareCardRequest,
    themeMode: YamoneThemeMode,
    mascot: YamoneMascot,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val bitmap = remember(request, themeMode, mascot) {
        renderShareCardBitmap(context, request, themeMode, mascot)
    }
    var savedUri by remember(request) { mutableStateOf<Uri?>(null) }

    fun saveNow() {
        val uri = saveShareCardToPhotos(context, bitmap, request)
        if (uri != null) {
            savedUri = uri
            Toast.makeText(context, "사진에 저장했어요 ♡", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "사진 저장에 실패했어요", Toast.LENGTH_SHORT).show()
        }
    }

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) saveNow()
        else Toast.makeText(context, "사진 저장 권한이 필요해요", Toast.LENGTH_SHORT).show()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(YamoneCream)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier.fillMaxWidth().height(54.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("기록 공유", fontSize = 21.sp, fontWeight = FontWeight.Black, color = YamoneInk)
            Spacer(Modifier.weight(1f))
            Text("Yamone Games", fontSize = 11.sp, color = YamoneMuted)
        }

        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "${arcadeGameTitle(request.game)} 공유카드",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentScale = ContentScale.Fit
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(17.dp)
            ) {
                Text("돌아가기", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Button(
                onClick = {
                    val uri = savedUri ?: writeTemporaryShareCard(context, bitmap, request)
                    if (uri != null) shareCard(context, uri, request)
                    else Toast.makeText(context, "공유 이미지를 만들지 못했어요", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.buttonColors(containerColor = yamonePrimary(themeMode))
            ) {
                Text("공유하기", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    ) {
                        saveNow()
                    } else {
                        legacyPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                },
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.buttonColors(containerColor = yamonePrimaryDark(themeMode))
            ) {
                Text("사진 저장", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

private fun renderShareCardBitmap(
    context: Context,
    request: ShareCardRequest,
    themeMode: YamoneThemeMode,
    mascot: YamoneMascot
): Bitmap {
    val width = 1080
    val height = 1350
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val primary = if (themeMode == YamoneThemeMode.MINT) AndroidColor.rgb(59, 201, 176) else AndroidColor.rgb(255, 127, 164)
    val primaryDark = if (themeMode == YamoneThemeMode.MINT) AndroidColor.rgb(21, 140, 122) else AndroidColor.rgb(217, 87, 125)
    val soft = if (themeMode == YamoneThemeMode.MINT) AndroidColor.rgb(229, 248, 244) else AndroidColor.rgb(255, 232, 239)
    val ink = AndroidColor.rgb(36, 52, 58)
    val muted = AndroidColor.rgb(115, 133, 139)

    canvas.drawColor(AndroidColor.rgb(255, 253, 249))

    paint.color = soft
    canvas.drawRoundRect(RectF(50f, 45f, 1030f, 1305f), 64f, 64f, paint)

    drawGameDecoration(canvas, request.game, primary, primaryDark)

    paint.textAlign = Paint.Align.CENTER
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    paint.color = primaryDark
    paint.textSize = 42f
    canvas.drawText("YAMONE GAMES", 540f, 125f, paint)

    val mascotName = when (mascot) {
        YamoneMascot.SEAL -> if (themeMode == YamoneThemeMode.PINK) "yamone_seal_pink" else "yamone_seal_mint"
        YamoneMascot.BEAR -> if (themeMode == YamoneThemeMode.PINK) "yamone_bear_pink" else "yamone_bear_mint"
    }
    val mascotRes = context.resources.getIdentifier(mascotName, "drawable", context.packageName)
    if (mascotRes != 0) {
        val mascotBitmap = BitmapFactory.decodeResource(context.resources, mascotRes)
        if (mascotBitmap != null) {
            val dst = RectF(350f, 165f, 730f, 545f)
            canvas.drawBitmap(mascotBitmap, null, dst, paint)
        }
    }

    paint.color = ink
    paint.textSize = 48f
    canvas.drawText("${request.record.nickname}님의", 540f, 610f, paint)

    paint.textSize = 58f
    canvas.drawText(arcadeGameTitle(request.game), 540f, 685f, paint)

    paint.color = primaryDark
    paint.textSize = 118f
    canvas.drawText(arcadeScoreText(request.game, request.record.score), 540f, 845f, paint)

    paint.color = ink
    paint.textSize = 38f
    canvas.drawText(cardCaption(request.game), 540f, 925f, paint)

    paint.color = muted
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
    paint.textSize = 31f
    canvas.drawText(arcadeEndedAtText(request.record.endedAtEpochMillis), 540f, 1000f, paint)

    paint.color = primary
    canvas.drawRoundRect(RectF(245f, 1050f, 835f, 1056f), 3f, 3f, paint)

    paint.color = ink
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    paint.textSize = 34f
    canvas.drawText("Yamone Games", 540f, 1135f, paint)

    if (STORE_LINK.isNotBlank()) {
        paint.color = muted
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        paint.textSize = 26f
        canvas.drawText(STORE_LINK, 540f, 1184f, paint)
    }

    paint.color = muted
    paint.textSize = 23f
    canvas.drawText("작고 귀여운 기록 한 장 ♡", 540f, 1245f, paint)

    return bitmap
}

private fun cardCaption(game: ArcadeGameId): String = when (game) {
    ArcadeGameId.ICE_JUMP -> "얼음판을 타고 여기까지 올라왔어요!"
    ArcadeGameId.FISH_MUNCH -> "오늘도 냠냠 성공 ♡"
    ArcadeGameId.SNOW_RUSH -> "눈덩이를 피해 살아남았어요!"
}

private fun drawGameDecoration(canvas: Canvas, game: ArcadeGameId, primary: Int, primaryDark: Int) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    when (game) {
        ArcadeGameId.ICE_JUMP -> {
            paint.color = AndroidColor.WHITE
            canvas.drawRoundRect(RectF(105f, 1080f, 300f, 1115f), 18f, 18f, paint)
            canvas.drawRoundRect(RectF(780f, 280f, 970f, 315f), 18f, 18f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 8f
            paint.color = primary
            canvas.drawRoundRect(RectF(105f, 1080f, 300f, 1115f), 18f, 18f, paint)
            canvas.drawRoundRect(RectF(780f, 280f, 970f, 315f), 18f, 18f, paint)
            paint.style = Paint.Style.FILL
            paint.color = primaryDark
            repeat(5) { index ->
                val cx = 110f + index * 205f
                val cy = if (index % 2 == 0) 240f else 1160f
                canvas.drawCircle(cx, cy, 7f, paint)
            }
        }
        ArcadeGameId.FISH_MUNCH -> {
            drawFish(canvas, 150f, 310f, 0.85f, primary)
            drawFish(canvas, 895f, 1030f, 1.15f, primaryDark)
            drawFish(canvas, 875f, 220f, 0.65f, primary)
            paint.color = AndroidColor.argb(110, 255, 255, 255)
            canvas.drawCircle(160f, 1020f, 32f, paint)
            canvas.drawCircle(220f, 960f, 18f, paint)
            canvas.drawCircle(885f, 445f, 24f, paint)
        }
        ArcadeGameId.SNOW_RUSH -> {
            drawSnowball(canvas, 160f, 310f, 72f, primary)
            drawSnowball(canvas, 900f, 1030f, 96f, primaryDark)
            drawSnowflake(canvas, 885f, 245f, 36f, primaryDark)
            drawSnowflake(canvas, 180f, 1040f, 28f, primary)
            drawSnowflake(canvas, 930f, 500f, 22f, primary)
        }
    }
}

private fun drawFish(canvas: Canvas, cx: Float, cy: Float, scale: Float, color: Int) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = AndroidColor.argb(45, 0, 0, 0)
    canvas.drawOval(RectF(cx - 64f * scale + 7f, cy - 34f * scale + 8f, cx + 64f * scale + 7f, cy + 34f * scale + 8f), paint)
    paint.color = color
    canvas.drawOval(RectF(cx - 64f * scale, cy - 34f * scale, cx + 64f * scale, cy + 34f * scale), paint)
    val tail = Path().apply {
        moveTo(cx - 58f * scale, cy)
        lineTo(cx - 104f * scale, cy - 43f * scale)
        lineTo(cx - 104f * scale, cy + 43f * scale)
        close()
    }
    canvas.drawPath(tail, paint)
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(cx + 31f * scale, cy - 8f * scale, 9f * scale, paint)
    paint.color = AndroidColor.rgb(36, 52, 58)
    canvas.drawCircle(cx + 33f * scale, cy - 8f * scale, 4f * scale, paint)
}

private fun drawSnowball(canvas: Canvas, cx: Float, cy: Float, radius: Float, accent: Int) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = AndroidColor.argb(40, 35, 80, 100)
    canvas.drawCircle(cx + 9f, cy + 12f, radius, paint)
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(cx, cy, radius, paint)
    paint.color = AndroidColor.argb(55, AndroidColor.red(accent), AndroidColor.green(accent), AndroidColor.blue(accent))
    canvas.drawCircle(cx + radius * 0.20f, cy + radius * 0.18f, radius * 0.70f, paint)
    paint.color = AndroidColor.argb(190, 255, 255, 255)
    canvas.drawCircle(cx - radius * 0.30f, cy - radius * 0.34f, radius * 0.18f, paint)
}

private fun drawSnowflake(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
    }
    repeat(3) { index ->
        val angle = Math.toRadians((index * 60).toDouble())
        val dx = kotlin.math.cos(angle).toFloat() * radius
        val dy = kotlin.math.sin(angle).toFloat() * radius
        canvas.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, paint)
    }
}

private fun writeTemporaryShareCard(context: Context, bitmap: Bitmap, request: ShareCardRequest): Uri? = runCatching {
    val dir = File(context.cacheDir, "shared_cards").apply { mkdirs() }
    val file = File(dir, "${request.game.storageKey}_${request.record.endedAtEpochMillis}.png")
    FileOutputStream(file).use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}.getOrNull()

private fun saveShareCardToPhotos(context: Context, bitmap: Bitmap, request: ShareCardRequest): Uri? = runCatching {
    val resolver = context.contentResolver
    val fileName = "Yamone_${request.game.storageKey}_${request.record.endedAtEpochMillis}.png"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Yamone Games")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching null
    resolver.openOutputStream(uri)?.use { output ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
    } ?: return@runCatching null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val completed = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
        resolver.update(uri, completed, null, null)
    }
    uri
}.getOrNull()

private fun shareCard(context: Context, uri: Uri, request: ShareCardRequest) {
    val text = buildString {
        append("${request.record.nickname}님의 ${arcadeGameTitle(request.game)} 기록 ${arcadeScoreText(request.game, request.record.score)}")
        if (STORE_LINK.isNotBlank()) append("\n$STORE_LINK")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri("Yamone Games", uri)
    }
    context.startActivity(Intent.createChooser(intent, "기록 공유하기"))
}
