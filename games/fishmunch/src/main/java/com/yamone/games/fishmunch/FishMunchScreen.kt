package com.yamone.games.fishmunch

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

private class FishMunchState {
    var playerX by mutableFloatStateOf(0.5f)
    var moveDirection by mutableFloatStateOf(0f)
    var fishX by mutableFloatStateOf(0.5f)
    var fishY by mutableFloatStateOf(0.08f)
    var score by mutableIntStateOf(0)
    var started by mutableStateOf(false)
    var gameOver by mutableStateOf(false)
    var serial by mutableIntStateOf(1)

    fun restart() {
        playerX = 0.5f
        moveDirection = 0f
        score = 0
        started = true
        gameOver = false
        serial++
        respawnFish()
    }

    fun update(dtRaw: Float) {
        if (!started || gameOver) return
        val dt = dtRaw.coerceIn(0f, 0.033f)
        playerX = (playerX + moveDirection * 0.72f * dt).coerceIn(0.08f, 0.92f)
        val speed = (0.28f + score * 0.018f).coerceAtMost(0.72f)
        fishY += speed * dt

        if (fishY in 0.72f..0.88f && abs(fishX - playerX) < 0.13f) {
            score++
            serial++
            respawnFish()
        } else if (fishY > 1.02f) {
            gameOver = true
            moveDirection = 0f
        }
    }

    private fun respawnFish() {
        val random = Random(serial * 97 + score * 17)
        fishX = 0.12f + random.nextFloat() * 0.76f
        fishY = 0.05f
    }
}

@Composable
fun FishMunchScreen(
    onBack: () -> Unit,
    primary: Color,
    primaryDark: Color,
    soft: Color,
    ink: Color,
    muted: Color,
    mascotContent: @Composable (Dp) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("yamone_fish_munch", 0) }
    val state = remember { FishMunchState() }
    var best by remember { mutableIntStateOf(prefs.getInt("best_score", 0)) }

    LaunchedEffect(Unit) {
        var previous = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (previous != 0L) {
                    state.update((now - previous) / 1_000_000_000f)
                    if (state.gameOver && state.score > best) {
                        best = state.score
                        prefs.edit().putInt("best_score", best).apply()
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
                Text("물고기 냠냠", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ink)
                Text("좌우 이동 · 물고기 먹기", fontSize = 10.sp, color = muted)
            }
            Spacer(Modifier.weight(1f))
            mascotContent(38.dp)
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatChip(Modifier.weight(1f), "먹은 물고기", "${state.score}마리", primaryDark, soft, ink)
            StatChip(Modifier.weight(1f), "최고", "${best}마리", primaryDark, soft, ink)
        }

        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(28.dp)).background(Color(0xFFEAF9FF))
        ) {
            Canvas(Modifier.matchParentSize()) {
                val bubble = Color.White.copy(alpha = .65f)
                drawCircle(bubble, size.width * .035f, Offset(size.width * .18f, size.height * .18f))
                drawCircle(bubble, size.width * .02f, Offset(size.width * .78f, size.height * .32f))
                drawCircle(primary.copy(alpha = .10f), size.width * .016f, Offset(size.width * .35f, size.height * .52f))
            }

            Text(
                "🐟",
                modifier = Modifier.offset(x = maxWidth * state.fishX - 22.dp, y = maxHeight * state.fishY - 22.dp),
                fontSize = 34.sp
            )

            Box(
                Modifier.offset(x = maxWidth * state.playerX - 27.dp, y = maxHeight * .80f - 27.dp)
            ) { mascotContent(54.dp) }

            if (!state.started) {
                StartOverlay(
                    title = "물고기를 놓치지 마요!",
                    body = "아래 버튼으로 움직여서\n내려오는 물고기를 먹어요 ♡",
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
                    title = "앗, 물고기를 놓쳤어요!",
                    body = "이번 기록 ${state.score}마리\n최고 기록 ${best.coerceAtLeast(state.score)}마리",
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
