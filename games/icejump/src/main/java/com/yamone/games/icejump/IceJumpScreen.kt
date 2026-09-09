package com.yamone.games.icejump

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
import kotlin.math.max
import kotlin.random.Random

private data class IcePlatform(
    val id: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val activated: Boolean = false,
    val waitRemaining: Float = 0f
)

private class IceJumpState {
    var playerX by mutableFloatStateOf(0.5f)
    var playerY by mutableFloatStateOf(0.74f)
    var velocityY by mutableFloatStateOf(-1.05f)
    var heightScore by mutableIntStateOf(0)
    var gameOver by mutableStateOf(false)
    var started by mutableStateOf(false)
    var platformSerial by mutableIntStateOf(0)
    var platforms by mutableStateOf(initialPlatforms())

    fun restart() {
        playerX = 0.5f
        playerY = 0.74f
        velocityY = -1.05f
        heightScore = 0
        gameOver = false
        started = true
        platformSerial = 20
        platforms = initialPlatforms().map { platform ->
            if (platform.id == 1) {
                platform.copy(activated = true, waitRemaining = platformWaitTime())
            } else {
                platform
            }
        }
    }

    fun dragBy(deltaNormalized: Float) {
        if (!started || gameOver) return
        playerX = (playerX + deltaNormalized * 1.12f).coerceIn(0.055f, 0.945f)
    }

    fun update(dtRaw: Float, landingHalfWidth: Float) {
        if (!started || gameOver) return
        val dt = dtRaw.coerceIn(0f, 0.033f)

        val fallSpeed = platformFallSpeed()
        platforms = platforms.map { platform ->
            when {
                !platform.activated -> platform
                platform.waitRemaining > 0f -> platform.copy(
                    waitRemaining = (platform.waitRemaining - dt).coerceAtLeast(0f)
                )
                else -> platform.copy(y = platform.y + fallSpeed * dt)
            }
        }
        recyclePlatforms()

        val previousY = playerY
        val previousBottom = previousY + PLAYER_HALF_HEIGHT

        velocityY += GRAVITY * dt
        playerY += velocityY * dt

        if (velocityY > 0f) {
            val newBottom = playerY + PLAYER_HALF_HEIGHT
            val landing = platforms
                .filter { platform ->
                    val platformLeft = platform.x - platform.width / 2f
                    val platformRight = platform.x + platform.width / 2f
                    val playerLeft = playerX - landingHalfWidth
                    val playerRight = playerX + landingHalfWidth
                    previousBottom <= platform.y + 0.012f &&
                        newBottom >= platform.y - 0.012f &&
                        playerRight >= platformLeft + 0.015f &&
                        playerLeft <= platformRight - 0.015f
                }
                .minByOrNull { it.y }

            if (landing != null) {
                playerY = landing.y - PLAYER_HALF_HEIGHT
                velocityY = JUMP_VELOCITY
                if (!landing.activated) {
                    val wait = platformWaitTime()
                    platforms = platforms.map { platform ->
                        if (platform.id == landing.id) {
                            platform.copy(activated = true, waitRemaining = wait)
                        } else {
                            platform
                        }
                    }
                }
            }
        }

        if (playerY < CAMERA_LINE) {
            val scroll = CAMERA_LINE - playerY
            playerY = CAMERA_LINE
            platforms = platforms.map { it.copy(y = it.y + scroll) }
            heightScore += max(1, (scroll * 1000f).toInt())
            recyclePlatforms()
        }

        if (playerY > 1.08f) gameOver = true
    }

    private fun platformWaitTime(): Float {
        val difficulty = (heightScore / 15000f).coerceIn(0f, 1f)
        return 2.8f - 2.25f * difficulty
    }

    private fun platformFallSpeed(): Float {
        val difficulty = (heightScore / 15000f).coerceIn(0f, 1f)
        return 0.035f + 0.145f * difficulty
    }

    private fun recyclePlatforms() {
        var next = platforms.filter { it.y < 1.12f }
        var highestY = next.minOfOrNull { it.y } ?: 0.9f
        val random = Random(heightScore + platformSerial * 31)

        while (highestY > -0.12f) {
            val difficulty = (heightScore / 20000f).coerceIn(0f, 1f)
            val gap = 0.125f + random.nextFloat() * (0.040f + difficulty * 0.020f)
            highestY -= gap
            val width = (0.235f - heightScore / 180000f).coerceIn(0.145f, 0.235f)
            val x = 0.14f + random.nextFloat() * 0.72f
            platformSerial++
            next = next + IcePlatform(platformSerial, x, highestY, width)
        }
        platforms = next
    }

    companion object {
        private const val PLAYER_HALF_HEIGHT = 0.045f
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
    nickname: String,
    landingHalfWidth: Float,
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
    val state = remember { IceJumpState() }
    var topRecords by remember { mutableStateOf(recordStorage.topRecords(ArcadeGameId.ICE_JUMP)) }
    var lastRecord by remember { mutableStateOf<ArcadeRecord?>(null) }
    val bestHeight = topRecords.firstOrNull()?.score ?: 0

    fun restart() {
        lastRecord = null
        state.restart()
    }

    LaunchedEffect(Unit) {
        var previous = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (previous != 0L) state.update((now - previous) / 1_000_000_000f, landingHalfWidth)
                previous = now
            }
        }
    }

    LaunchedEffect(state.gameOver) {
        if (state.gameOver && lastRecord == null) {
            lastRecord = recordStorage.addRecord(
                game = ArcadeGameId.ICE_JUMP,
                score = state.heightScore,
                nickname = nickname
            )
            topRecords = recordStorage.topRecords(ArcadeGameId.ICE_JUMP)
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
                Text("빙하 점프", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ink)
                Text("자동 점프 · 화면 드래그 이동", fontSize = 10.sp, color = muted)
            }
            Spacer(Modifier.weight(1f))
            mascotContent(40.dp)
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ScoreChip(Modifier.weight(1f), "높이", "${state.heightScore}m", primaryDark, ink)
            ScoreChip(Modifier.weight(1f), "최고", "${bestHeight}m", primaryDark, ink)
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFD8EFF8), Color(0xFFB9DCE9), Color(0xFFA9D0E1))
                    )
                )
                .pointerInput(state.started, state.gameOver) {
                    if (state.started && !state.gameOver) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            if (size.width > 0) state.dragBy(dragAmount / size.width.toFloat())
                        }
                    }
                }
        ) {
            val playerSize = 56.dp

            Canvas(Modifier.matchParentSize()) {
                val cloud = Color.White.copy(alpha = 0.38f)
                drawCircle(cloud, radius = size.width * 0.09f, center = Offset(size.width * 0.13f, size.height * 0.17f))
                drawCircle(cloud, radius = size.width * 0.06f, center = Offset(size.width * 0.23f, size.height * 0.15f))
                drawCircle(cloud, radius = size.width * 0.07f, center = Offset(size.width * 0.84f, size.height * 0.27f))
                repeat(7) { index ->
                    val x = size.width * ((index * 23 + 13) % 91) / 100f
                    val y = size.height * ((index * 31 + 9) % 73) / 100f
                    drawCircle(Color(0xFF4B91AD).copy(alpha = 0.10f), radius = size.width * 0.008f, center = Offset(x, y))
                }
            }

            state.platforms.forEach { platform ->
                val platformWidth = maxWidth * platform.width
                Surface(
                    modifier = Modifier
                        .offset(x = maxWidth * platform.x - platformWidth / 2, y = maxHeight * platform.y)
                        .width(platformWidth)
                        .height(19.dp),
                    shape = RoundedCornerShape(50),
                    color = Color(0xFFFBFEFF),
                    shadowElevation = 6.dp,
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF3D8FB2).copy(alpha = 0.58f))
                ) {
                    Box {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .align(Alignment.BottomCenter)
                                .background(Color(0xFF67B8D5).copy(alpha = 0.52f))
                        )
                    }
                }
            }

            Box(
                Modifier.offset(
                    x = maxWidth * state.playerX - playerSize / 2,
                    y = maxHeight * state.playerY - playerSize / 2
                )
            ) { mascotContent(playerSize) }

            if (!state.started) {
                Surface(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White.copy(alpha = 0.98f),
                    shadowElevation = 5.dp
                ) {
                    Column(
                        Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        mascotContent(82.dp)
                        Spacer(Modifier.height(10.dp))
                        Text("얼음판을 타고 올라가요!", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ink)
                        Spacer(Modifier.height(5.dp))
                        Text("밟은 빙하는 잠시 뒤 천천히 내려가요.\n높이 올라갈수록 대기시간은 짧아지고 하강은 빨라져요 ♡", textAlign = TextAlign.Center, fontSize = 12.sp, color = muted)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = ::restart,
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primary)
                        ) { Text("시작하기", fontWeight = FontWeight.ExtraBold) }
                    }
                }
            }

            if (state.gameOver) {
                Surface(
                    modifier = Modifier.align(Alignment.Center).padding(20.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White.copy(alpha = 0.99f),
                    shadowElevation = 6.dp
                ) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        mascotContent(76.dp)
                        Spacer(Modifier.height(6.dp))
                        Text("앗, 미끄러졌어요!", fontSize = 21.sp, fontWeight = FontWeight.Black, color = ink)
                        Spacer(Modifier.height(4.dp))
                        Text("${state.heightScore}m", fontSize = 30.sp, fontWeight = FontWeight.Black, color = primaryDark)
                        Text("최고 기록 ${topRecords.firstOrNull()?.score ?: state.heightScore}m", fontSize = 11.sp, color = muted)
                        Spacer(Modifier.height(15.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = ::restart, shape = RoundedCornerShape(17.dp)) {
                                Text("다시하기", fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { lastRecord?.let(onShareRecord) },
                                enabled = lastRecord != null,
                                shape = RoundedCornerShape(17.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primary)
                            ) { Text("공유카드", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp),
            shape = RoundedCornerShape(18.dp),
            color = soft
        ) {
            Text(
                "화면을 누른 채 좌우로 움직이면 공중에서 방향을 바꿀 수 있어요",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                textAlign = TextAlign.Center,
                fontSize = 10.sp,
                color = ink.copy(alpha = 0.65f)
            )
        }
    }
}

@Composable
private fun ScoreChip(modifier: Modifier, label: String, value: String, dark: Color, ink: Color) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 1.dp) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 10.sp, color = ink.copy(alpha = 0.55f))
            Spacer(Modifier.weight(1f))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Black, color = dark)
        }
    }
}