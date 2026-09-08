package com.yamone.games.icejump

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
import kotlin.math.max
import kotlin.random.Random

private data class IcePlatform(
    val id: Int,
    val x: Float,
    val y: Float,
    val width: Float
)

private class IceJumpState {
    var playerX by mutableFloatStateOf(0.5f)
    var playerY by mutableFloatStateOf(0.74f)
    var velocityY by mutableFloatStateOf(-1.05f)
    var moveDirection by mutableFloatStateOf(0f)
    var heightScore by mutableIntStateOf(0)
    var gameOver by mutableStateOf(false)
    var started by mutableStateOf(false)
    var platformSerial by mutableIntStateOf(0)
    var platforms by mutableStateOf(initialPlatforms())

    fun restart() {
        playerX = 0.5f
        playerY = 0.74f
        velocityY = -1.05f
        moveDirection = 0f
        heightScore = 0
        gameOver = false
        started = true
        platformSerial = 20
        platforms = initialPlatforms()
    }

    fun update(dtRaw: Float) {
        if (!started || gameOver) return
        val dt = dtRaw.coerceIn(0f, 0.033f)
        val previousY = playerY
        val previousBottom = previousY + PLAYER_HALF_HEIGHT

        playerX = (playerX + moveDirection * HORIZONTAL_SPEED * dt).coerceIn(0.055f, 0.945f)
        velocityY += GRAVITY * dt
        playerY += velocityY * dt

        if (velocityY > 0f) {
            val newBottom = playerY + PLAYER_HALF_HEIGHT
            val landing = platforms
                .filter { platform ->
                    previousBottom <= platform.y + 0.012f &&
                        newBottom >= platform.y - 0.012f &&
                        playerX >= platform.x - platform.width / 2f - 0.025f &&
                        playerX <= platform.x + platform.width / 2f + 0.025f
                }
                .minByOrNull { it.y }

            if (landing != null) {
                playerY = landing.y - PLAYER_HALF_HEIGHT
                velocityY = JUMP_VELOCITY
            }
        }

        if (playerY < CAMERA_LINE) {
            val scroll = CAMERA_LINE - playerY
            playerY = CAMERA_LINE
            platforms = platforms.map { it.copy(y = it.y + scroll) }
            heightScore += max(1, (scroll * 1000f).toInt())
            recyclePlatforms()
        }

        if (playerY > 1.08f) {
            gameOver = true
            moveDirection = 0f
        }
    }

    private fun recyclePlatforms() {
        var next = platforms.filter { it.y < 1.12f }
        var highestY = next.minOfOrNull { it.y } ?: 0.9f
        val random = Random(heightScore + platformSerial * 31)

        while (highestY > -0.12f) {
            val gap = 0.125f + random.nextFloat() * 0.045f
            highestY -= gap
            val width = (0.23f - (heightScore / 12000f)).coerceIn(0.14f, 0.23f)
            val x = 0.14f + random.nextFloat() * 0.72f
            platformSerial++
            next = next + IcePlatform(platformSerial, x, highestY, width)
        }
        platforms = next
    }

    companion object {
        private const val PLAYER_HALF_HEIGHT = 0.045f
        private const val HORIZONTAL_SPEED = 0.62f
        private const val GRAVITY = 2.6f
        private const val JUMP_VELOCITY = -1.08f
        private const val CAMERA_LINE = 0.34f

        private fun initialPlatforms(): List<IcePlatform> = listOf(
            IcePlatform(1, 0.50f, 0.82f, 0.34f),
            IcePlatform(2, 0.27f, 0.67f, 0.24f),
            IcePlatform(3, 0.68f, 0.52f, 0.23f),
            IcePlatform(4, 0.39f, 0.37f, 0.22f),
            IcePlatform(5, 0.73f, 0.22f, 0.21f),
            IcePlatform(6, 0.30f, 0.07f, 0.20f),
            IcePlatform(7, 0.59f, -0.08f, 0.19f)
        )
    }
}

@Composable
fun IceJumpScreen(
    onBack: () -> Unit,
    primary: Color,
    primaryDark: Color,
    soft: Color,
    ink: Color,
    muted: Color,
    mascotContent: @Composable (Dp) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("yamone_ice_jump", 0) }
    val state = remember { IceJumpState() }
    var bestHeight by remember { mutableIntStateOf(prefs.getInt("best_height", 0)) }

    LaunchedEffect(Unit) {
        var previous = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (previous != 0L) {
                    state.update((now - previous) / 1_000_000_000f)
                    if (state.gameOver && state.heightScore > bestHeight) {
                        bestHeight = state.heightScore
                        prefs.edit().putInt("best_height", bestHeight).apply()
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
                Text("빙하 점프", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ink)
                Text("자동 점프 · 좌우 이동", fontSize = 10.sp, color = muted)
            }
            Spacer(Modifier.weight(1f))
            mascotContent(38.dp)
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ScoreChip(Modifier.weight(1f), "높이", "${state.heightScore}m", primaryDark, soft, ink)
            ScoreChip(Modifier.weight(1f), "최고", "${bestHeight}m", primaryDark, soft, ink)
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFFEFFBFA))
        ) {
            val playerSize = 52.dp

            Canvas(Modifier.matchParentSize()) {
                val cloud = Color.White.copy(alpha = 0.75f)
                drawCircle(cloud, radius = size.width * 0.10f, center = Offset(size.width * 0.14f, size.height * 0.18f))
                drawCircle(cloud, radius = size.width * 0.07f, center = Offset(size.width * 0.25f, size.height * 0.16f))
                drawCircle(cloud, radius = size.width * 0.08f, center = Offset(size.width * 0.83f, size.height * 0.28f))
                drawCircle(primary.copy(alpha = 0.10f), radius = size.width * 0.018f, center = Offset(size.width * 0.72f, size.height * 0.12f))
                drawCircle(primary.copy(alpha = 0.12f), radius = size.width * 0.012f, center = Offset(size.width * 0.18f, size.height * 0.42f))
            }

            state.platforms.forEach { platform ->
                val platformWidth = maxWidth * platform.width
                Box(
                    Modifier
                        .offset(
                            x = maxWidth * platform.x - platformWidth / 2,
                            y = maxHeight * platform.y
                        )
                        .width(platformWidth)
                        .height(15.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .align(Alignment.BottomCenter)
                            .background(primary.copy(alpha = 0.22f))
                    )
                }
            }

            Box(
                Modifier.offset(
                    x = maxWidth * state.playerX - playerSize / 2,
                    y = maxHeight * state.playerY - playerSize / 2
                )
            ) {
                mascotContent(playerSize)
            }

            if (!state.started) {
                Surface(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White.copy(alpha = 0.96f),
                    shadowElevation = 4.dp
                ) {
                    Column(
                        Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        mascotContent(76.dp)
                        Spacer(Modifier.height(10.dp))
                        Text("얼음판을 타고 올라가요!", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ink)
                        Spacer(Modifier.height(5.dp))
                        Text("점프는 자동이에요.\n아래 버튼으로 좌우만 움직여주세요 ♡", textAlign = TextAlign.Center, fontSize = 12.sp, color = muted)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = state::restart,
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primary)
                        ) { Text("시작하기", fontWeight = FontWeight.ExtraBold) }
                    }
                }
            }

            if (state.gameOver) {
                Surface(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White.copy(alpha = 0.97f),
                    shadowElevation = 5.dp
                ) {
                    Column(Modifier.padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        mascotContent(70.dp)
                        Spacer(Modifier.height(8.dp))
                        Text("앗, 미끄러졌어요!", fontSize = 22.sp, fontWeight = FontWeight.Black, color = ink)
                        Text("이번 기록 ${state.heightScore}m", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = primaryDark)
                        Text("최고 기록 ${bestHeight}m", fontSize = 11.sp, color = muted)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = state::restart,
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primary)
                        ) { Text("다시하기", fontWeight = FontWeight.ExtraBold) }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MoveButton(
                modifier = Modifier.weight(1f),
                text = "‹  왼쪽",
                primary = primary,
                soft = soft,
                ink = ink,
                onPressed = { state.moveDirection = -1f },
                onReleased = { if (state.moveDirection < 0f) state.moveDirection = 0f }
            )
            MoveButton(
                modifier = Modifier.weight(1f),
                text = "오른쪽  ›",
                primary = primary,
                soft = soft,
                ink = ink,
                onPressed = { state.moveDirection = 1f },
                onReleased = { if (state.moveDirection > 0f) state.moveDirection = 0f }
            )
        }
    }
}

@Composable
private fun ScoreChip(modifier: Modifier, label: String, value: String, dark: Color, soft: Color, ink: Color) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = Color.White) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 10.sp, color = ink.copy(alpha = 0.55f))
            Spacer(Modifier.weight(1f))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Black, color = dark)
        }
    }
}

@Composable
private fun MoveButton(
    modifier: Modifier,
    text: String,
    primary: Color,
    soft: Color,
    ink: Color,
    onPressed: () -> Unit,
    onReleased: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(58.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPressed()
                        tryAwaitRelease()
                        onReleased()
                    }
                )
            },
        shape = RoundedCornerShape(20.dp),
        color = soft,
        border = androidx.compose.foundation.BorderStroke(1.dp, primary.copy(alpha = 0.42f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = ink)
        }
    }
}
