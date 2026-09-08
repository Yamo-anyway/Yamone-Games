package com.yamone.games.snowrush

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.random.Random

private class SnowRushState {
    var playerX by mutableFloatStateOf(0.5f)
    var moveDirection by mutableFloatStateOf(0f)
    var snowX by mutableFloatStateOf(0.5f)
    var snowY by mutableFloatStateOf(0.05f)
    var score by mutableIntStateOf(0)
    var started by mutableStateOf(false)
    var gameOver by mutableStateOf(false)
    var serial by mutableIntStateOf(1)
    private var elapsed by mutableFloatStateOf(0f)

    fun restart() {
        playerX = 0.5f
        moveDirection = 0f
        score = 0
        elapsed = 0f
        started = true
        gameOver = false
        serial++
        respawnSnowball()
    }

    fun update(dtRaw: Float) {
        if (!started || gameOver) return
        val dt = dtRaw.coerceIn(0f, 0.033f)
        playerX = (playerX + moveDirection * 0.78f * dt).coerceIn(0.08f, 0.92f)
        elapsed += dt
        score = elapsed.toInt()

        val speed = (0.32f + score * 0.012f).coerceAtMost(0.82f)
        snowY += speed * dt

        if (snowY in 0.70f..0.88f && abs(snowX - playerX) < 0.115f) {
            gameOver = true
            moveDirection = 0f
        } else if (snowY > 1.02f) {
            serial++
            respawnSnowball()
        }
    }

    private fun respawnSnowball() {
        val random = Random(serial * 131 + score * 29)
        snowX = 0.10f + random.nextFloat() * 0.80f
        snowY = 0.04f
    }
}

@Composable
fun SnowRushScreen(
    onBack: () -> Unit,
    primary: Color,
    primaryDark: Color,
    soft: Color,
    ink: Color,
    muted: Color,
    mascotContent: @Composable (Dp) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("yamone_snow_rush", 0) }
    val state = remember { SnowRushState() }
    var best by remember { mutableIntStateOf(prefs.getInt("best_seconds", 0)) }

    LaunchedEffect(Unit) {
        var previous = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (previous != 0L) {
                    state.update((now - previous) / 1_000_000_000f)
                    if (state.gameOver && state.score > best) {
                        best = state.score
                        prefs.edit().putInt("best_seconds", best).apply()
                    }
                }
                previous = now
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFFFFCF9))) {
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
                Text("좌우 이동 · 눈덩이 피하기", fontSize = 10.sp, color = muted)
            }
            Spacer(Modifier.weight(1f))
            mascotContent(38.dp)
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatChip(Modifier.weight(1f), "생존", "${state.score}초", primaryDark, soft, ink)
            StatChip(Modifier.weight(1f), "최고", "${best}초", primaryDark, soft, ink)
        }

        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(28.dp)).background(Color(0xFFF3FAFF))
        ) {
            Canvas(Modifier.matchParentSize()) {
                val white = Color.White.copy(alpha = .85f)
                repeat(14) { i ->
                    val x = size.width * ((i * 37 % 100) / 100f)
                    val y = size.height * ((i * 19 % 75) / 100f)
                    drawCircle(white, radius = size.width * .008f, center = Offset(x, y))
                }
            }

            Surface(
                modifier = Modifier.offset(x = maxWidth * state.snowX - 22.dp, y = maxHeight * state.snowY - 22.dp).size(44.dp),
                shape = RoundedCornerShape(50),
                color = Color.White,
                shadowElevation = 3.dp
            ) {
                Box(contentAlignment = Alignment.Center) { Text("●", fontSize = 23.sp, color = primary.copy(alpha = .18f)) }
            }

            Box(
                Modifier.offset(x = maxWidth * state.playerX - 27.dp, y = maxHeight * .80f - 27.dp)
            ) { mascotContent(54.dp) }

            if (!state.started) {
                StartOverlay(
                    title = "눈덩이를 피해요!",
                    body = "아래 버튼으로 좌우 이동해요.\n오래 버틸수록 눈덩이가 빨라져요 ♡",
                    button = "시작하기",
                    primary = primary,
                    ink = ink,
                    muted = muted,
                    mascotContent = mascotContent,
                    onClick = state::restart
                )
            }

            if (state.gameOver) {
                StartOverlay(
                    title = "데굴! 눈덩이에 닿았어요",
                    body = "이번 기록 ${state.score}초\n최고 기록 ${best.coerceAtLeast(state.score)}초",
                    button = "다시하기",
                    primary = primary,
                    ink = ink,
                    muted = muted,
                    mascotContent = mascotContent,
                    onClick = state::restart
                )
            }
        }

        ControlRow(
            primary = primary,
            soft = soft,
            dark = primaryDark,
            enabled = state.started && !state.gameOver,
            onDirection = { state.moveDirection = it }
        )
        Spacer(Modifier.navigationBarsPadding().height(8.dp))
    }
}

@Composable
private fun StatChip(modifier: Modifier, label: String, value: String, dark: Color, soft: Color, ink: Color) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = soft) {
        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Black, color = dark)
            Text(label, fontSize = 10.sp, color = ink.copy(alpha = .58f))
        }
    }
}

@Composable
private fun StartOverlay(
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
        modifier = Modifier.align(Alignment.Center).padding(24.dp),
        shape = RoundedCornerShape(28.dp), color = Color.White.copy(alpha = .97f), shadowElevation = 4.dp
    ) {
        Column(Modifier.padding(horizontal = 28.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            mascotContent(72.dp)
            Spacer(Modifier.height(10.dp))
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.Black, color = ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(5.dp))
            Text(body, fontSize = 12.sp, color = muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = primary), shape = RoundedCornerShape(16.dp)) {
                Text(button, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ControlRow(primary: Color, soft: Color, dark: Color, enabled: Boolean, onDirection: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DirectionButton(Modifier.weight(1f), "◀", primary, soft, dark, enabled, -1f, onDirection)
        DirectionButton(Modifier.weight(1f), "▶", primary, soft, dark, enabled, 1f, onDirection)
    }
}

@Composable
private fun DirectionButton(modifier: Modifier, text: String, primary: Color, soft: Color, dark: Color, enabled: Boolean, direction: Float, onDirection: (Float) -> Unit) {
    Surface(
        modifier = modifier.height(62.dp).pointerInput(enabled) {
            if (enabled) detectTapGestures(
                onPress = {
                    onDirection(direction)
                    tryAwaitRelease()
                    onDirection(0f)
                }
            )
        },
        shape = RoundedCornerShape(22.dp),
        color = if (enabled) soft else Color(0xFFF1F3F3),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (enabled) primary.copy(alpha = .6f) else Color(0xFFE1E5E5))
    ) {
        Box(contentAlignment = Alignment.Center) { Text(text, fontSize = 26.sp, fontWeight = FontWeight.Black, color = if (enabled) dark else dark.copy(alpha = .35f)) }
    }
}
