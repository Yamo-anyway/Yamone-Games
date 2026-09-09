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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.ceil
import kotlin.math.sin
import kotlin.random.Random

private enum class FishMode {
    NORMAL,
    TIME_ATTACK
}

private val FishMode.gameId: ArcadeGameId
    get() = when (this) {
        FishMode.NORMAL -> ArcadeGameId.FISH_MUNCH
        FishMode.TIME_ATTACK -> ArcadeGameId.FISH_MUNCH_TIME_ATTACK
    }

private data class TimeAttackFish(
    val id: Int,
    val baseX: Float,
    val x: Float,
    val y: Float,
    val kind: Int,
    val phase: Float,
    val amplitude: Float,
    val fallFactor: Float
)

private class FishMunchState {
    var mode by mutableStateOf(FishMode.NORMAL)
    var playerX by mutableFloatStateOf(0.5f)
    var fishX by mutableFloatStateOf(0.5f)
    var fishY by mutableFloatStateOf(0.08f)
    var fishKind by mutableIntStateOf(1)
    var score by mutableIntStateOf(0)
    var started by mutableStateOf(false)
    var gameOver by mutableStateOf(false)
    var serial by mutableIntStateOf(1)
    var elapsed by mutableFloatStateOf(0f)
    var timeAttackFish by mutableStateOf<List<TimeAttackFish>>(emptyList())

    private var fishBaseX by mutableFloatStateOf(0.5f)
    private var zigzagPhase by mutableFloatStateOf(0f)
    private var zigzagAmplitude by mutableFloatStateOf(0.05f)
    private var spawnClock by mutableFloatStateOf(0f)

    val remainingSeconds: Int
        get() = ceil((TIME_ATTACK_SECONDS - elapsed).coerceAtLeast(0f)).toInt()

    fun start(selectedMode: FishMode) {
        mode = selectedMode
        playerX = 0.5f
        score = 0
        elapsed = 0f
        spawnClock = 0f
        timeAttackFish = emptyList()
        started = true
        gameOver = false
        serial++

        if (mode == FishMode.NORMAL) {
            respawnFish()
        } else {
            spawnTimeAttackBurst(2)
        }
    }

    fun restartCurrentMode() {
        start(mode)
    }

    fun returnToModeSelect() {
        playerX = 0.5f
        score = 0
        elapsed = 0f
        spawnClock = 0f
        timeAttackFish = emptyList()
        started = false
        gameOver = false
    }

    fun dragBy(deltaNormalized: Float) {
        if (!started || gameOver) return
        playerX = (playerX + deltaNormalized * 1.08f).coerceIn(0.07f, 0.93f)
    }

    fun update(dtRaw: Float, playerHalfWidth: Float, playerHalfHeight: Float) {
        if (!started || gameOver) return
        val dt = dtRaw.coerceIn(0f, 0.033f)
        when (mode) {
            FishMode.NORMAL -> updateNormal(dt, playerHalfWidth, playerHalfHeight)
            FishMode.TIME_ATTACK -> updateTimeAttack(dt, playerHalfWidth, playerHalfHeight)
        }
    }

    private fun updateNormal(dt: Float, playerHalfWidth: Float, playerHalfHeight: Float) {
        val kindFactor = kindSpeedFactor(fishKind)
        val speed = ((0.44f + score * 0.021f) * kindFactor).coerceAtMost(1.38f)
        fishY += speed * dt

        zigzagPhase += dt * (2.55f + score * 0.050f)
        fishX = (fishBaseX + sin(zigzagPhase.toDouble()).toFloat() * zigzagAmplitude)
            .coerceIn(0.085f, 0.915f)

        val caught = isCaught(
            x = fishX,
            y = fishY,
            kind = fishKind,
            playerHalfWidth = playerHalfWidth,
            playerHalfHeight = playerHalfHeight
        )

        if (caught) {
            score++
            serial++
            respawnFish()
        } else if (fishY > 1.05f) {
            gameOver = true
        }
    }

    private fun updateTimeAttack(dt: Float, playerHalfWidth: Float, playerHalfHeight: Float) {
        elapsed = (elapsed + dt).coerceAtMost(TIME_ATTACK_SECONDS)
        if (elapsed >= TIME_ATTACK_SECONDS) {
            gameOver = true
            return
        }

        spawnClock += dt
        var spawnSafety = 0
        while (spawnSafety < 4) {
            val interval = currentSpawnInterval()
            if (spawnClock < interval) break
            spawnClock -= interval
            spawnTimeAttackBurst(currentBurstCount())
            spawnSafety++
        }

        val progress = (elapsed / TIME_ATTACK_SECONDS).coerceIn(0f, 1f)
        val afterForty = ((elapsed - 40f) / 20f).coerceIn(0f, 1f)
        val baseSpeed = 0.39f + progress * 0.18f + afterForty * 0.16f
        val phaseSpeed = 2.20f + progress * 1.60f

        val next = buildList {
            timeAttackFish.forEach { fish ->
                val nextPhase = fish.phase + dt * phaseSpeed
                val nextY = fish.y + baseSpeed * kindSpeedFactor(fish.kind) * fish.fallFactor * dt
                val nextX = (fish.baseX + sin(nextPhase.toDouble()).toFloat() * fish.amplitude)
                    .coerceIn(0.075f, 0.925f)

                val caught = isCaught(
                    x = nextX,
                    y = nextY,
                    kind = fish.kind,
                    playerHalfWidth = playerHalfWidth,
                    playerHalfHeight = playerHalfHeight
                )

                if (caught) {
                    score++
                } else if (nextY <= 1.08f) {
                    add(fish.copy(x = nextX, y = nextY, phase = nextPhase))
                }
            }
        }
        timeAttackFish = next
    }

    private fun currentSpawnInterval(): Float {
        val second = elapsed.toInt().coerceIn(0, 59)
        return when {
            second < 20 -> 0.76f - second * 0.016f
            second < 40 -> 0.44f - (second - 20) * 0.010f
            else -> (0.24f - (second - 40) * 0.006f).coerceAtLeast(0.13f)
        }
    }

    private fun currentBurstCount(): Int {
        val second = elapsed.toInt().coerceIn(0, 59)
        return when {
            second < 16 -> 1
            second < 28 -> if ((serial + second) % 3 == 0) 2 else 1
            second < 38 -> 2
            second < 48 -> if ((serial + second) % 2 == 0) 3 else 2
            else -> if ((serial + second) % 3 == 0) 4 else 3
        }
    }

    private fun spawnTimeAttackBurst(requestedCount: Int) {
        val available = (MAX_ACTIVE_TIME_ATTACK_FISH - timeAttackFish.size).coerceAtLeast(0)
        val count = requestedCount.coerceAtMost(available)
        if (count <= 0) return

        val additions = buildList {
            repeat(count) { burstIndex ->
                serial++
                val random = Random(serial * 137 + elapsed.toInt() * 31 + burstIndex * 19)
                val kind = random.nextInt(3)
                val margin = fishMargin(kind)
                val baseX = margin + random.nextFloat() * (1f - margin * 2f)
                add(
                    TimeAttackFish(
                        id = serial,
                        baseX = baseX,
                        x = baseX,
                        y = -0.035f - random.nextFloat() * 0.10f,
                        kind = kind,
                        phase = random.nextFloat() * 6.28f,
                        amplitude = 0.030f + random.nextFloat() * 0.040f,
                        fallFactor = 0.90f + random.nextFloat() * 0.26f
                    )
                )
            }
        }
        timeAttackFish = timeAttackFish + additions
    }

    private fun respawnFish() {
        val random = Random(serial * 97 + score * 17)
        fishKind = random.nextInt(3)
        val margin = fishMargin(fishKind)
        fishBaseX = margin + random.nextFloat() * (1f - margin * 2f)
        fishX = fishBaseX
        fishY = 0.04f
        zigzagPhase = random.nextFloat() * 6.28f
        zigzagAmplitude = 0.040f + random.nextFloat() * 0.040f
    }

    private fun isCaught(
        x: Float,
        y: Float,
        kind: Int,
        playerHalfWidth: Float,
        playerHalfHeight: Float
    ): Boolean {
        val fishHalfWidth = when (kind) {
            0 -> 0.035f
            2 -> 0.065f
            else -> 0.050f
        }
        val fishHalfHeight = when (kind) {
            0 -> 0.025f
            2 -> 0.045f
            else -> 0.035f
        }
        return abs(x - playerX) <= playerHalfWidth + fishHalfWidth &&
            abs(y - PLAYER_Y) <= playerHalfHeight + fishHalfHeight
    }

    private fun fishMargin(kind: Int): Float = when (kind) {
        0 -> 0.10f
        2 -> 0.16f
        else -> 0.13f
    }

    private fun kindSpeedFactor(kind: Int): Float = when (kind) {
        0 -> 1.08f
        2 -> 0.94f
        else -> 1.0f
    }

    companion object {
        const val PLAYER_Y = 0.80f
        private const val TIME_ATTACK_SECONDS = 60f
        private const val MAX_ACTIVE_TIME_ATTACK_FISH = 60
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
    var normalRecords by remember { mutableStateOf(recordStorage.topRecords(ArcadeGameId.FISH_MUNCH)) }
    var timeAttackRecords by remember { mutableStateOf(recordStorage.topRecords(ArcadeGameId.FISH_MUNCH_TIME_ATTACK)) }
    var lastRecord by remember { mutableStateOf<ArcadeRecord?>(null) }

    val currentRecords = if (state.mode == FishMode.NORMAL) normalRecords else timeAttackRecords
    val best = currentRecords.firstOrNull()?.score ?: 0

    fun start(mode: FishMode) {
        lastRecord = null
        state.start(mode)
    }

    fun restart() {
        lastRecord = null
        state.restartCurrentMode()
    }

    fun selectModeAgain() {
        lastRecord = null
        state.returnToModeSelect()
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
            val game = state.mode.gameId
            lastRecord = recordStorage.addRecord(
                game = game,
                score = state.score,
                nickname = nickname
            )
            if (state.mode == FishMode.NORMAL) {
                normalRecords = recordStorage.topRecords(ArcadeGameId.FISH_MUNCH)
            } else {
                timeAttackRecords = recordStorage.topRecords(ArcadeGameId.FISH_MUNCH_TIME_ATTACK)
            }
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
                Text(
                    if (state.mode == FishMode.TIME_ATTACK) "타임어택 60초 · 놓쳐도 계속" else "일반 모드 · 놓치면 종료",
                    fontSize = 10.sp,
                    color = muted
                )
            }
            Spacer(Modifier.weight(1f))
            mascotContent(40.dp)
        }

        if (state.mode == FishMode.TIME_ATTACK) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                StatChip(Modifier.weight(1f), "남은 시간", "${state.remainingSeconds}초", primaryDark, ink)
                StatChip(Modifier.weight(1f), "먹은 물고기", "${state.score}마리", primaryDark, ink)
                StatChip(Modifier.weight(1f), "최고", "${best}마리", primaryDark, ink)
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatChip(Modifier.weight(1f), "먹은 물고기", "${state.score}마리", primaryDark, ink)
                StatChip(Modifier.weight(1f), "최고", "${best}마리", primaryDark, ink)
            }
        }

        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFBFEAF5), Color(0xFF8FD0E5), Color(0xFF67B9D4))
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
            Canvas(Modifier.matchParentSize()) {
                val bubble = Color.White.copy(alpha = .48f)
                drawCircle(bubble, size.width * .030f, Offset(size.width * .18f, size.height * .18f))
                drawCircle(bubble, size.width * .018f, Offset(size.width * .78f, size.height * .32f))
                drawCircle(bubble, size.width * .012f, Offset(size.width * .72f, size.height * .67f))
                drawCircle(Color(0xFF287EA0).copy(alpha = .14f), size.width * .016f, Offset(size.width * .32f, size.height * .58f))
                drawCircle(Color(0xFF287EA0).copy(alpha = .12f), size.width * .011f, Offset(size.width * .84f, size.height * .72f))
                repeat(5) { index ->
                    val y = size.height * (0.12f + index * 0.16f)
                    drawOval(
                        color = Color.White.copy(alpha = 0.08f),
                        topLeft = Offset(size.width * 0.03f, y),
                        size = Size(size.width * 0.94f, size.height * 0.018f)
                    )
                }
            }

            if (state.mode == FishMode.NORMAL) {
                val fishSize = normalFishSize(state.fishKind)
                Box(
                    Modifier.offset(
                        x = maxWidth * state.fishX - fishSize / 2,
                        y = maxHeight * state.fishY - fishSize / 2
                    )
                ) {
                    PrettyFish(state.fishKind, fishSize, primary, primaryDark)
                }
            } else {
                state.timeAttackFish.forEach { fish ->
                    key(fish.id) {
                        val fishSize = timeAttackFishSize(fish.kind)
                        Box(
                            Modifier.offset(
                                x = maxWidth * fish.x - fishSize / 2,
                                y = maxHeight * fish.y - fishSize / 2
                            )
                        ) {
                            PrettyFish(fish.kind, fishSize, primary, primaryDark)
                        }
                    }
                }
            }

            val playerSize = 58.dp
            Box(
                Modifier.offset(
                    x = maxWidth * state.playerX - playerSize / 2,
                    y = maxHeight * FishMunchState.PLAYER_Y - playerSize / 2
                )
            ) { mascotContent(playerSize) }

            if (!state.started && !state.gameOver) {
                ModeSelectOverlay(
                    primary = primary,
                    primaryDark = primaryDark,
                    ink = ink,
                    muted = muted,
                    mascotContent = mascotContent,
                    onNormal = { start(FishMode.NORMAL) },
                    onTimeAttack = { start(FishMode.TIME_ATTACK) }
                )
            }

            if (state.gameOver) {
                ResultOverlay(
                    mode = state.mode,
                    score = state.score,
                    best = currentRecords.firstOrNull()?.score ?: state.score,
                    primary = primary,
                    primaryDark = primaryDark,
                    ink = ink,
                    muted = muted,
                    mascotContent = mascotContent,
                    onModeSelect = ::selectModeAgain,
                    onRestart = ::restart,
                    onShare = { lastRecord?.let(onShareRecord) },
                    showShare = state.mode == FishMode.NORMAL && lastRecord != null
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp),
            shape = RoundedCornerShape(18.dp),
            color = soft
        ) {
            Text(
                if (state.mode == FishMode.TIME_ATTACK)
                    "1분 동안 놓치는 물고기는 신경 쓰지 말고 최대한 많이 받아먹어요"
                else
                    "화면을 누른 채 좌우로 움직여 물고기를 받아먹어요",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                textAlign = TextAlign.Center,
                fontSize = 10.sp,
                color = ink.copy(alpha = .65f)
            )
        }
    }
}

private fun normalFishSize(kind: Int): Dp = when (kind) {
    0 -> 40.dp
    2 -> 70.dp
    else -> 54.dp
}

private fun timeAttackFishSize(kind: Int): Dp = when (kind) {
    0 -> 34.dp
    2 -> 60.dp
    else -> 46.dp
}

@Composable
private fun PrettyFish(kind: Int, size: Dp, primary: Color, primaryDark: Color) {
    val bodyColor = when (kind) {
        0 -> Color(0xFF126F9A)
        2 -> Color(0xFFFFC94D)
        else -> Color(0xFFFF7F5E)
    }
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val bodyLeft = w * 0.22f
        val bodyTop = h * 0.24f
        val bodyWidth = w * 0.66f
        val bodyHeight = h * 0.52f

        drawOval(
            color = Color.Black.copy(alpha = 0.17f),
            topLeft = Offset(bodyLeft + w * 0.030f, bodyTop + h * 0.050f),
            size = Size(bodyWidth, bodyHeight)
        )

        val tailShadow = Path().apply {
            moveTo(w * 0.28f, h * 0.50f + h * 0.035f)
            lineTo(w * 0.04f, h * 0.24f + h * 0.035f)
            lineTo(w * 0.04f, h * 0.76f + h * 0.035f)
            close()
        }
        drawPath(tailShadow, Color.Black.copy(alpha = 0.14f))

        val tail = Path().apply {
            moveTo(w * 0.28f, h * 0.50f)
            lineTo(w * 0.04f, h * 0.24f)
            lineTo(w * 0.04f, h * 0.76f)
            close()
        }
        drawPath(tail, bodyColor.copy(alpha = .96f))
        drawPath(tail, Color(0xFF174E65).copy(alpha = .45f), style = Stroke(width = (w * .025f).coerceAtLeast(1f)))
        drawOval(bodyColor, Offset(bodyLeft, bodyTop), Size(bodyWidth, bodyHeight))
        drawOval(
            Color(0xFF174E65).copy(alpha = .42f),
            Offset(bodyLeft, bodyTop),
            Size(bodyWidth, bodyHeight),
            style = Stroke(width = (w * .025f).coerceAtLeast(1f))
        )
        drawOval(Color.White.copy(alpha = .55f), Offset(w * .39f, h * .29f), Size(w * .22f, h * .09f))
        drawCircle(Color.White, radius = w * .058f, center = Offset(w * .72f, h * .42f))
        drawCircle(Color(0xFF173845), radius = w * .027f, center = Offset(w * .735f, h * .42f))
        drawCircle(primary.copy(alpha = .42f), radius = w * .022f, center = Offset(w * .60f, h * .61f))
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
private fun BoxScope.ModeSelectOverlay(
    primary: Color,
    primaryDark: Color,
    ink: Color,
    muted: Color,
    mascotContent: @Composable (Dp) -> Unit,
    onNormal: () -> Unit,
    onTimeAttack: () -> Unit
) {
    Surface(
        modifier = Modifier.align(Alignment.Center).padding(20.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = .99f),
        shadowElevation = 5.dp
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            mascotContent(76.dp)
            Spacer(Modifier.height(7.dp))
            Text("어떻게 냠냠할까요?", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ink)
            Spacer(Modifier.height(5.dp))
            Text("두 모드의 기록은 따로 저장돼요 ♡", fontSize = 11.sp, color = muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onNormal,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = primary),
                shape = RoundedCornerShape(17.dp)
            ) {
                Text("일반 모드", fontWeight = FontWeight.ExtraBold)
            }
            Text("한 마리씩 · 놓치면 종료 · 먹을수록 빨라져요", fontSize = 10.sp, color = muted, textAlign = TextAlign.Center)

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onTimeAttack,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = primaryDark),
                shape = RoundedCornerShape(17.dp)
            ) {
                Text("타임어택 60초", fontWeight = FontWeight.ExtraBold)
            }
            Text("놓쳐도 계속 · 시간이 갈수록 더 많이 쏟아져요", fontSize = 10.sp, color = muted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun BoxScope.ResultOverlay(
    mode: FishMode,
    score: Int,
    best: Int,
    primary: Color,
    primaryDark: Color,
    ink: Color,
    muted: Color,
    mascotContent: @Composable (Dp) -> Unit,
    onModeSelect: () -> Unit,
    onRestart: () -> Unit,
    onShare: () -> Unit,
    showShare: Boolean
) {
    Surface(
        modifier = Modifier.align(Alignment.Center).padding(20.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = .99f),
        shadowElevation = 6.dp
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            mascotContent(74.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                if (mode == FishMode.TIME_ATTACK) "60초 끝!" else "앗, 물고기를 놓쳤어요!",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = ink
            )
            Spacer(Modifier.height(4.dp))
            Text("${score}마리", fontSize = 30.sp, fontWeight = FontWeight.Black, color = primaryDark)
            Text(
                if (mode == FishMode.TIME_ATTACK) "타임어택 최고 기록 ${best}마리" else "최고 기록 ${best}마리",
                fontSize = 11.sp,
                color = muted
            )
            Spacer(Modifier.height(15.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onModeSelect, shape = RoundedCornerShape(17.dp)) {
                    Text("모드 선택", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onRestart,
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primary)
                ) {
                    Text("다시하기", fontWeight = FontWeight.Bold)
                }
            }
            if (showShare) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onShare,
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryDark)
                ) {
                    Text("공유카드", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
