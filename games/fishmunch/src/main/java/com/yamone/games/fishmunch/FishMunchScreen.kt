package com.yamone.games.fishmunch

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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import kotlin.math.abs
import kotlin.random.Random

private class FishMunchState {
    var playerX by mutableFloatStateOf(0.5f)
    var fishX by mutableFloatStateOf(0.5f)
    var fishY by mutableFloatStateOf(0.08f)
    var fishKind by mutableIntStateOf(1)
    var score by mutableIntStateOf(0)
    var started by mutableStateOf(false)
    var gameOver by mutableStateOf(false)
    var serial by mutableIntStateOf(1)

    fun restart() {
        playerX = 0.5f
        score = 0
        started = true
        gameOver = false
        serial++
        respawnFish()
    }

    fun dragBy(deltaNormalized: Float) {
        if (!started || gameOver) return
        playerX = (playerX + deltaNormalized * 1.28f).coerceIn(0.07f, 0.93f)
    }

    fun update(dtRaw: Float, playerHalfWidth: Float, playerHalfHeight: Float) {
        if (!started || gameOver) return
        val dt = dtRaw.coerceIn(0f, 0.033f)
        val speedFactor = when (fishKind) {
            0 -> 1.10f
            2 -> 0.92f
            else -> 1.0f
        }
        val speed = (0.38f + score * 0.022f).coerceAtMost(0.88f) * speedFactor
        fishY += speed * dt

        val fishHalfWidth = when (fishKind) {
            0 -> 0.035f
            2 -> 0.065f
            else -> 0.050f
        }
        val fishHalfHeight = when (fishKind) {
            0 -> 0.025f
            2 -> 0.045f
            else -> 0.035f
        }

        val caught = abs(fishX - playerX) <= playerHalfWidth + fishHalfWidth &&
            abs(fishY - PLAYER_Y) <= playerHalfHeight + fishHalfHeight

        if (caught) {
            score++
            serial++
            respawnFish()
        } else if (fishY > 1.05f) {
            gameOver = true
        }
    }

    private fun respawnFish() {
        val random = Random(serial * 97 + score * 17)
        fishKind = random.nextInt(3)
        val margin = when (fishKind) {
            0 -> 0.10f
            2 -> 0.16f
            else -> 0.13f
        }
        fishX = margin + random.nextFloat() * (1f - margin * 2f)
        fishY = 0.04f
    }

    companion object {
        const val PLAYER_Y = 0.80f
    }
}

@Composable
fun FishMunchScreen(
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
    val state = remember { FishMunchState() }
    var topRecords by remember { mutableStateOf(recordStorage.topRecords(ArcadeGameId.FISH_MUNCH)) }
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
                    state.update((now - previous) / 1_000_000_000f, playerHalfWidth, playerHalfHeight)
                }
                previous = now
            }
        }
    }

    LaunchedEffect(state.gameOver) {
        if (state.gameOver && lastRecord == null) {
            lastRecord = recordStorage.addRecord(
                game = ArcadeGameId.FISH_MUNCH,
                score = state.score,
                nickname = nickname
            )
            topRecords = recordStorage.topRecords(ArcadeGameId.FISH_MUNCH)
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
                Text("물고기 냠냠", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ink)
                Text("화면 드래그 · 물고기 먹기", fontSize = 10.sp, color = muted)
            }
            Spacer(Modifier.weight(1f))
            mascotContent(40.dp)
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatChip(Modifier.weight(1f), "먹은 물고기", "${state.score}마리", primaryDark, ink)
            StatChip(Modifier.weight(1f), "최고", "${best}마리", primaryDark, ink)
        }

        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFFF3FBFD))
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
                val bubble = Color.White.copy(alpha = .58f)
                drawCircle(bubble, size.width * .030f, Offset(size.width * .18f, size.height * .18f))
                drawCircle(bubble, size.width * .018f, Offset(size.width * .78f, size.height * .32f))
                drawCircle(bubble, size.width * .012f, Offset(size.width * .72f, size.height * .67f))
                repeat(5) { index ->
                    val y = size.height * (0.12f + index * 0.16f)
                    drawOval(
                        color = primary.copy(alpha = 0.045f),
                        topLeft = Offset(size.width * 0.03f, y),
                        size = Size(size.width * 0.94f, size.height * 0.018f)
                    )
                }
            }

            val fishSize = when (state.fishKind) {
                0 -> 40.dp
                2 -> 70.dp
                else -> 54.dp
            }
            Box(
                Modifier.offset(
                    x = maxWidth * state.fishX - fishSize / 2,
                    y = maxHeight * state.fishY - fishSize / 2
                )
            ) {
                PrettyFish(state.fishKind, fishSize, primary, primaryDark)
            }

            val playerSize = 58.dp
            Box(
                Modifier.offset(
                    x = maxWidth * state.playerX - playerSize / 2,
                    y = maxHeight * FishMunchState.PLAYER_Y - playerSize / 2
                )
            ) { mascotContent(playerSize) }

            if (!state.started) {
                StartOverlay(
                    title = "물고기를 냠냠!",
                    body = "작은 물고기부터 큰 물고기까지 내려와요.\n화면을 누른 채 좌우로 움직여 먹어주세요 ♡",
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
                "화면을 누른 채 좌우로 움직여 물고기를 받아먹어요",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                textAlign = TextAlign.Center,
                fontSize = 10.sp,
                color = ink.copy(alpha = .65f)
            )
        }
    }
}

@Composable
private fun PrettyFish(kind: Int, size: Dp, primary: Color, primaryDark: Color) {
    val bodyColor = when (kind) {
        0 -> primaryDark
        2 -> Color(0xFF5A9FD4)
        else -> Color(0xFFFF9B6A)
    }
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val bodyLeft = w * 0.22f
        val bodyTop = h * 0.24f
        val bodyWidth = w * 0.66f
        val bodyHeight = h * 0.52f

        drawOval(
            color = Color.Black.copy(alpha = 0.10f),
            topLeft = Offset(bodyLeft + w * 0.025f, bodyTop + h * 0.045f),
            size = Size(bodyWidth, bodyHeight)
        )

        val tailShadow = Path().apply {
            moveTo(w * 0.28f, h * 0.50f + h * 0.035f)
            lineTo(w * 0.04f, h * 0.24f + h * 0.035f)
            lineTo(w * 0.04f, h * 0.76f + h * 0.035f)
            close()
        }
        drawPath(tailShadow, Color.Black.copy(alpha = 0.09f))

        val tail = Path().apply {
            moveTo(w * 0.28f, h * 0.50f)
            lineTo(w * 0.04f, h * 0.24f)
            lineTo(w * 0.04f, h * 0.76f)
            close()
        }
        drawPath(tail, bodyColor.copy(alpha = .92f))
        drawOval(bodyColor, Offset(bodyLeft, bodyTop), Size(bodyWidth, bodyHeight))
        drawOval(Color.White.copy(alpha = .40f), Offset(w * .39f, h * .29f), Size(w * .22f, h * .09f))
        drawCircle(Color.White, radius = w * .055f, center = Offset(w * .72f, h * .42f))
        drawCircle(Color(0xFF26373D), radius = w * .025f, center = Offset(w * .735f, h * .42f))
        drawCircle(primary.copy(alpha = .38f), radius = w * .022f, center = Offset(w * .60f, h * .61f))
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
            Text("앗, 물고기를 놓쳤어요!", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ink)
            Spacer(Modifier.height(4.dp))
            Text("${score}마리", fontSize = 30.sp, fontWeight = FontWeight.Black, color = primaryDark)
            Text("최고 기록 ${best}마리", fontSize = 11.sp, color = muted)
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
