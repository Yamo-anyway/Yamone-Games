package com.yamone.games.snowrush

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamone.games.arcadecore.ArcadeGameId
import com.yamone.games.arcadecore.ArcadeRecord
import com.yamone.games.arcadecore.ArcadeRecordStorage
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private enum class SnowPattern {
    STEADY,
    GROW,
    GROW_THEN_SHRINK,
    SHRINK_THEN_GROW,
    SHRINK
}

private class SnowRushState {
    var playerX by mutableFloatStateOf(0.5f)
    var snowX by mutableFloatStateOf(0.5f)
    var snowY by mutableFloatStateOf(0.05f)
    var baseRadius by mutableFloatStateOf(0.043f)
    var pattern by mutableStateOf(SnowPattern.STEADY)
    var score by mutableIntStateOf(0)
    var started by mutableStateOf(false)
    var gameOver by mutableStateOf(false)
    var serial by mutableIntStateOf(1)
    var ambientPhase by mutableFloatStateOf(0f)
    private var elapsed by mutableFloatStateOf(0f)

    fun restart() {
        playerX = 0.5f
        score = 0
        elapsed = 0f
        started = true
        gameOver = false
        serial++
        respawnSnowball()
    }

    fun dragBy(deltaNormalized: Float) {
        if (!started || gameOver) return
        playerX = (playerX + deltaNormalized * 1.22f).coerceIn(0.075f, 0.925f)
    }

    fun updateAmbient(dtRaw: Float) {
        val dt = dtRaw.coerceIn(0f, 0.033f)
        ambientPhase = (ambientPhase + dt * 0.070f) % 1f
    }

    fun update(dtRaw: Float, playerHalfWidth: Float, playerHalfHeight: Float) {
        if (!started || gameOver) return
        val dt = dtRaw.coerceIn(0f, 0.033f)
        elapsed += dt
        score = elapsed.toInt()

        val speed = (0.34f + score * 0.010f).coerceAtMost(0.78f)
        snowY += speed * dt

        val radius = currentRadius()
        val hitRadius = radius * 0.82f
        val hit = abs(snowX - playerX) <= playerHalfWidth + hitRadius &&
            abs(snowY - PLAYER_Y) <= playerHalfHeight + hitRadius

        if (hit) {
            gameOver = true
        } else if (snowY > 1.06f) {
            serial++
            respawnSnowball()
        }
    }

    fun currentRadius(): Float {
        val progress = ((snowY - 0.04f) / 0.98f).coerceIn(0f, 1f)
        val wave = sin(PI.toFloat() * progress)
        val scale = when (pattern) {
            SnowPattern.STEADY -> 1.00f
            SnowPattern.GROW -> 0.68f + 0.72f * progress
            SnowPattern.GROW_THEN_SHRINK -> 0.70f + 0.78f * wave
            SnowPattern.SHRINK_THEN_GROW -> 1.38f - 0.68f * wave
            SnowPattern.SHRINK -> 1.38f - 0.66f * progress
        }
        return (baseRadius * scale).coerceIn(0.027f, 0.067f)
    }

    private fun respawnSnowball() {
        val random = Random(serial * 131 + score * 29)
        snowX = 0.11f + random.nextFloat() * 0.78f
        snowY = 0.04f
        baseRadius = 0.038f + random.nextFloat() * 0.011f
        pattern = SnowPattern.entries[random.nextInt(SnowPattern.entries.size)]
    }

    companion object {
        const val PLAYER_Y = 0.80f
    }
}

@Composable
fun SnowRushScreen(
    onBack: () -> Unit,
    nickname: String,
    playerHalfWidth: Float,
    playerHalfHeight: Float,
    onShareRecord: (ArcadeRecord) -> Unit,
    primary: Color,
    primaryDark: Color,
    soft: Color,
    ink: Color,
    muted: Color,
    mascotContent: @Composable (Dp) -> Unit
) {
    val context = LocalContext.current.applicationContext
    val recordStorage = remember { ArcadeRecordStorage(context) }
    val state = remember { SnowRushState() }
    var topRecords by remember { mutableStateOf(recordStorage.topRecords(ArcadeGameId.SNOW_RUSH)) }
    var lastRecord by remember { mutableStateOf<ArcadeRecord?>(null) }
    val best = topRecords.firstOrNull()?.score ?: 0

    fun restart() {
        lastRecord = null
        state.restart()
    }

    LaunchedEffect(Unit) {
        var previous = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (previous != 0L) {
                    val dt = (now - previous) / 1_000_000_000f
                    state.updateAmbient(dt)
                    state.update(dt, playerHalfWidth, playerHalfHeight)
                }
                previous = now
            }
        }
    }

    LaunchedEffect(state.gameOver) {
        if (state.gameOver && lastRecord == null) {
            lastRecord = recordStorage.addRecord(
                game = ArcadeGameId.SNOW_RUSH,
                score = state.score,
                nickname = nickname
            )
            topRecords = recordStorage.topRecords(ArcadeGameId.SNOW_RUSH)
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFFFFDF9))) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(60.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(onClick = onBack, shape = RoundedCornerShape(16.dp), color = Color.White) {
                Text("‹", modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp), fontSize = 30.sp, color = ink)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text("눈덩이 러시", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ink)
                Text("화면 드래그 · 눈덩이 피하기", fontSize = 10.sp, color = muted)
            }
            Spacer(Modifier.weight(1f))
            mascotContent(40.dp)
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatChip(Modifier.weight(1f), "생존", "${formatDuration(state.score)}", primaryDark, ink)
            StatChip(Modifier.weight(1f), "최고", formatDuration(best), primaryDark, ink)
        }

        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFFF4F9FC))
                .pointerInput(state.started, state.gameOver) {
                    if (state.started && !state.gameOver) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            if (size.width > 0) state.dragBy(dragAmount / size.width.toFloat())
                        }
                    }
                }
        ) {
            Canvas(Modifier.matchParentSize()) {
                repeat(18) { index ->
                    val xSeed = ((index * 37 + 11) % 97) / 100f
                    val ySeed = ((index * 19 + 7) % 103) / 100f
                    val fall = (ySeed + state.ambientPhase * (0.72f + (index % 4) * 0.06f)) % 1.08f
                    val x = size.width * (0.04f + xSeed * 0.92f)
                    val y = size.height * (fall - 0.04f)
                    val radius = size.width * (0.008f + (index % 4) * 0.0025f)
                    drawPrettySnowflake(
                        center = Offset(x, y),
                        radius = radius,
                        color = Color.White.copy(alpha = 0.62f)
                    )
                }
                repeat(6) { index ->
                    val x = size.width * ((index * 29 + 17) % 93) / 100f
                    val y = size.height * ((index * 41 + 11) % 77) / 100f
                    drawCircle(primary.copy(alpha = 0.055f), radius = size.width * 0.010f, center = Offset(x, y))
                }
            }

            val radius = state.currentRadius()
            val snowSize = maxWidth * (radius * 2f)
            Box(
                Modifier.offset(
                    x = maxWidth * state.snowX - snowSize / 2,
                    y = maxHeight * state.snowY - snowSize / 2
                )
            ) {
                PrettySnowball(snowSize, primary, primaryDark)
            }

            val playerSize = 58.dp
            Box(
                Modifier.offset(
                    x = maxWidth * state.playerX - playerSize / 2,
                    y = maxHeight * SnowRushState.PLAYER_Y - playerSize / 2
                )
            ) { mascotContent(playerSize) }

            if (!state.started) {
                StartOverlay(
                    title = "눈덩이를 피해요!",
                    body = "눈덩이는 커지기도, 작아지기도 해요.\n천천히 내리는 눈꽃송이는 배경이니 안심하세요 ♡",
                    button = "시작하기",
                    primary = primary,
                    ink = ink,
                    muted = muted,
                    mascotContent = mascotContent,
                    onClick = ::restart
                )
            }

            if (state.gameOver) {
                ResultOverlay(
                    score = state.score,
                    best = topRecords.firstOrNull()?.score ?: state.score,
                    primary = primary,
                    primaryDark = primaryDark,
                    ink = ink,
                    muted = muted,
                    mascotContent = mascotContent,
                    onRestart = ::restart,
                    onShare = { lastRecord?.let(onShareRecord) },
                    shareEnabled = lastRecord != null
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp),
            shape = RoundedCornerShape(18.dp),
            color = soft
        ) {
            Text(
                "화면을 누른 채 좌우로 움직여 눈덩이를 피해요",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                textAlign = TextAlign.Center,
                fontSize = 10.sp,
                color = ink.copy(alpha = .65f)
            )
        }
    }
}

@Composable
private fun PrettySnowball(size: Dp, primary: Color, primaryDark: Color) {
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(Color(0xFF5E7F91).copy(alpha = 0.18f), r * 0.94f, center + Offset(r * 0.10f, r * 0.13f))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color(0xFFF1FAFE), primary.copy(alpha = 0.22f)),
                center = center - Offset(r * 0.25f, r * 0.28f),
                radius = r * 1.25f
            ),
            radius = r * 0.92f,
            center = center
        )
        drawCircle(primaryDark.copy(alpha = .18f), r * 0.92f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = (r * .08f).coerceAtLeast(1f)))
        drawCircle(Color.White.copy(alpha = .95f), r * .18f, center - Offset(r * .28f, r * .31f))
        drawCircle(primary.copy(alpha = .18f), r * .13f, center + Offset(r * .25f, r * .18f))
    }
}

private fun DrawScope.drawPrettySnowflake(center: Offset, radius: Float, color: Color) {
    repeat(3) { index ->
        val angle = (index * 60f) * PI.toFloat() / 180f
        val dx = cos(angle) * radius
        val dy = sin(angle) * radius
        val start = Offset(center.x - dx, center.y - dy)
        val end = Offset(center.x + dx, center.y + dy)
        drawLine(color, start, end, strokeWidth = (radius * .16f).coerceAtLeast(1f))
    }
}

@Composable
private fun StatChip(modifier: Modifier, label: String, value: String, dark: Color, ink: Color) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 1.dp) {
        Column(Modifier.padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Black, color = dark)
            Text(label, fontSize = 10.sp, color = ink.copy(alpha = .58f))
        }
    }
}

@Composable
private fun BoxScope.StartOverlay(
    title: String,
    body: String,
    button: String,
    primary: Color,
    ink: Color,
    muted: Color,
    mascotContent: @Composable (Dp) -> Unit,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.align(Alignment.Center).padding(22.dp),
        shape = RoundedCornerShape(28.dp), color = Color.White.copy(alpha = .99f), shadowElevation = 5.dp
    ) {
        Column(Modifier.padding(horizontal = 26.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            mascotContent(78.dp)
            Spacer(Modifier.height(8.dp))
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Black, color = ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(5.dp))
            Text(body, fontSize = 12.sp, color = muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = primary), shape = RoundedCornerShape(17.dp)) {
                Text(button, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BoxScope.ResultOverlay(
    score: Int,
    best: Int,
    primary: Color,
    primaryDark: Color,
    ink: Color,
    muted: Color,
    mascotContent: @Composable (Dp) -> Unit,
    onRestart: () -> Unit,
    onShare: () -> Unit,
    shareEnabled: Boolean
) {
    Surface(
        modifier = Modifier.align(Alignment.Center).padding(20.dp),
        shape = RoundedCornerShape(28.dp), color = Color.White.copy(alpha = .99f), shadowElevation = 6.dp
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            mascotContent(74.dp)
            Spacer(Modifier.height(6.dp))
            Text("데굴! 눈덩이에 닿았어요", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ink)
            Spacer(Modifier.height(4.dp))
            Text(formatDuration(score), fontSize = 30.sp, fontWeight = FontWeight.Black, color = primaryDark)
            Text("최고 기록 ${formatDuration(best)}", fontSize = 11.sp, color = muted)
            Spacer(Modifier.height(15.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRestart, shape = RoundedCornerShape(17.dp)) {
                    Text("다시하기", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onShare,
                    enabled = shareEnabled,
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primary)
                ) {
                    Text("공유카드", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
