package com.yamone.games.fishmunch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

private data class FallingFish(
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
    var score by mutableIntStateOf(0)
    var started by mutableStateOf(false)
    var gameOver by mutableStateOf(false)
    var serial by mutableIntStateOf(1)
    var elapsed by mutableFloatStateOf(0f)
    var normalFish by mutableStateOf<List<FallingFish>>(emptyList())
    var timeAttackFish by mutableStateOf<List<FallingFish>>(emptyList())

    private var nextBaseSpawnAt = 1f
    private var preparedExtraWindow = 0
    private var extraSpawnTimes = mutableListOf<Float>()
    private var nextExtraSpawnIndex = 0

    val remainingSeconds: Int
        get() = ceil((TIME_ATTACK_SECONDS - elapsed).coerceAtLeast(0f)).toInt()

    fun start(selectedMode: FishMode) {
        mode = selectedMode
        playerX = 0.5f
        score = 0
        elapsed = 0f
        normalFish = emptyList()
        timeAttackFish = emptyList()
        started = true
        gameOver = false
        serial++
        resetSpawnSchedule()
        spawnOneFish(initial = true)
    }

    fun restartCurrentMode() {
        start(mode)
    }

    fun returnToModeSelect() {
        playerX = 0.5f
        score = 0
        elapsed = 0f
        normalFish = emptyList()
        timeAttackFish = emptyList()
        started = false
        gameOver = false
        resetSpawnSchedule()
    }

    fun dragBy(deltaNormalized: Float) {
        if (!started || gameOver) return
        playerX = (playerX + deltaNormalized * 1.20f).coerceIn(0.045f, 0.955f)
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
        elapsed += dt
        spawnDueFish()

        val baseSpeed = (0.43f + score * 0.017f + elapsed * 0.0026f).coerceAtMost(1.34f)
        val phaseSpeed = 2.65f + score * 0.045f
        var missed = false

        val next = buildList {
            normalFish.forEach { fish ->
                val nextPhase = fish.phase + dt * phaseSpeed
                val nextY = fish.y + baseSpeed * kindSpeedFactor(fish.kind) * fish.fallFactor * dt
                val nextX = (fish.baseX + sin(nextPhase.toDouble()).toFloat() * fish.amplitude)
                    .coerceIn(0.060f, 0.940f)

                val caught = isCaught(
                    x = nextX,
                    y = nextY,
                    kind = fish.kind,
                    playerHalfWidth = playerHalfWidth,
                    playerHalfHeight = playerHalfHeight
                )

                when {
                    caught -> score++
                    nextY > 1.055f -> missed = true
                    else -> add(fish.copy(x = nextX, y = nextY, phase = nextPhase))
                }
            }
        }

        if (missed) {
            gameOver = true
            return
        }
        normalFish = next
    }

    private fun updateTimeAttack(dt: Float, playerHalfWidth: Float, playerHalfHeight: Float) {
        elapsed = (elapsed + dt).coerceAtMost(TIME_ATTACK_SECONDS)
        if (elapsed >= TIME_ATTACK_SECONDS) {
            gameOver = true
            return
        }

        spawnDueFish()

        val progress = (elapsed / TIME_ATTACK_SECONDS).coerceIn(0f, 1f)
        val afterForty = ((elapsed - 40f) / 20f).coerceIn(0f, 1f)
        val baseSpeed = 0.39f + progress * 0.18f + afterForty * 0.16f
        val phaseSpeed = 2.20f + progress * 1.60f

        val next = buildList {
            timeAttackFish.forEach { fish ->
                val nextPhase = fish.phase + dt * phaseSpeed
                val nextY = fish.y + baseSpeed * kindSpeedFactor(fish.kind) * fish.fallFactor * dt
                val nextX = (fish.baseX + sin(nextPhase.toDouble()).toFloat() * fish.amplitude)
                    .coerceIn(0.060f, 0.940f)

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

    private fun resetSpawnSchedule() {
        nextBaseSpawnAt = 1f
        preparedExtraWindow = 0
        extraSpawnTimes = mutableListOf()
        nextExtraSpawnIndex = 0
    }

    private fun spawnDueFish() {
        while (elapsed >= nextBaseSpawnAt) {
            spawnOneFish()
            nextBaseSpawnAt += 1f
        }

        prepareExtraWindows((elapsed / 5f).toInt())
        while (
            nextExtraSpawnIndex < extraSpawnTimes.size &&
            elapsed >= extraSpawnTimes[nextExtraSpawnIndex]
        ) {
            spawnOneFish()
            nextExtraSpawnIndex++
        }
    }

    private fun prepareExtraWindows(targetWindow: Int) {
        while (preparedExtraWindow < targetWindow) {
            preparedExtraWindow++
            val extraCount = preparedExtraWindow
            val windowStart = preparedExtraWindow * 5f
            val random = Random(50_003 + preparedExtraWindow * 977 + mode.ordinal * 9_973)
            val segment = 5f / extraCount
            val times = (0 until extraCount).map { index ->
                val offset = index * segment + random.nextFloat() * segment
                (windowStart + offset).coerceIn(windowStart + 0.15f, windowStart + 4.85f)
            }.sorted()
            extraSpawnTimes.addAll(times)
        }
    }

    private fun spawnOneFish(initial: Boolean = false) {
        val active = if (mode == FishMode.NORMAL) normalFish.size else timeAttackFish.size
        val maxActive = if (mode == FishMode.NORMAL) MAX_ACTIVE_NORMAL_FISH else MAX_ACTIVE_TIME_ATTACK_FISH
        if (active >= maxActive) return

        serial++
        val random = Random(serial * 137 + elapsed.toInt() * 31 + mode.ordinal * 7_919)
        val kind = random.nextInt(3)
        val margin = fishMargin(kind)
        val baseX = margin + random.nextFloat() * (1f - margin * 2f)
        val fish = FallingFish(
            id = serial,
            baseX = baseX,
            x = baseX,
            y = if (initial) 0.025f else -0.025f - random.nextFloat() * 0.07f,
            kind = kind,
            phase = random.nextFloat() * 6.28f,
            amplitude = if (mode == FishMode.NORMAL) {
                0.045f + random.nextFloat() * 0.050f
            } else {
                0.035f + random.nextFloat() * 0.055f
            },
            fallFactor = if (mode == FishMode.NORMAL) {
                0.92f + random.nextFloat() * 0.20f
            } else {
                0.90f + random.nextFloat() * 0.26f
            }
        )

        if (mode == FishMode.NORMAL) {
            normalFish = normalFish + fish
        } else {
            timeAttackFish = timeAttackFish + fish
        }
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
        0 -> 0.09f
        2 -> 0.145f
        else -> 0.115f
    }

    private fun kindSpeedFactor(kind: Int): Float = when (kind) {
        0 -> 1.08f
        2 -> 0.94f
        else -> 1.0f
    }

    companion object {
        const val PLAYER_Y = 0.80f
        private const val TIME_ATTACK_SECONDS = 60f
        private const val MAX_ACTIVE_NORMAL_FISH = 36
        private const val MAX_ACTIVE_TIME_ATTACK_FISH = 72
    }
}

@Composable
fun FishMunchScreen(
    onBack: () -> Unit,
    nickname: String,
    playerHalfWidth: Float,
    playerHalfHeight: Float,
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
    var exitConfirm by remember { mutableStateOf(false) }

    fun requestExit() {
        if (!state.started || state.gameOver) {
            onBack()
        } else {
            exitConfirm = true
        }
    }

    BackHandler { requestExit() }

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
                if (previous != 0L && !exitConfirm) {
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
            Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(onClick = ::requestExit, shape = RoundedCornerShape(15.dp), color = soft) {
                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    Text("←", fontSize = 20.sp, fontWeight = FontWeight.Black, color = primaryDark)
                }
            }
            Spacer(Modifier.width(10.dp))
            Text("물고기 냠냠", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ink)
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
                .pointerInput(state.started, state.gameOver, exitConfirm) {
                    if (state.started && !state.gameOver && !exitConfirm) {
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

            val visibleFish = if (state.mode == FishMode.NORMAL) state.normalFish else state.timeAttackFish
            visibleFish.forEach { fish ->
                key(fish.id) {
                    val fishSize = if (state.mode == FishMode.NORMAL) normalFishSize(fish.kind) else timeAttackFishSize(fish.kind)
                    Box(
                        Modifier.offset(
                            x = maxWidth * fish.x - fishSize / 2,
                            y = maxHeight * fish.y - fishSize / 2
                        )
                    ) {
                        PrettyFish(fish.kind, fishSize, primary)
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
                    onRestart = ::restart,
                    onExit = ::selectModeAgain
                )
            }
        }

    }

    if (exitConfirm) {
        AlertDialog(
            onDismissRequest = { exitConfirm = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("게임을 그만둘까요?", fontWeight = FontWeight.Black, color = ink) },
            text = { Text("게임이 일시정지됐어요. 계속 플레이하거나 현재 게임을 종료할 수 있어요.", color = muted) },
            confirmButton = {
                TextButton(onClick = { exitConfirm = false }) {
                    Text("계속하기", fontWeight = FontWeight.Bold, color = primaryDark)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    exitConfirm = false
                    selectModeAgain()
                }) { Text("게임 종료", fontWeight = FontWeight.Bold, color = Color(0xFFD85C6A)) }
            }
        )
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
private fun PrettyFish(kind: Int, size: Dp, primary: Color) {
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

        drawOval(Color.Black.copy(alpha = 0.17f), Offset(bodyLeft + w * 0.030f, bodyTop + h * 0.050f), Size(bodyWidth, bodyHeight))

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
        drawOval(Color(0xFF174E65).copy(alpha = .42f), Offset(bodyLeft, bodyTop), Size(bodyWidth, bodyHeight), style = Stroke(width = (w * .025f).coerceAtLeast(1f)))
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
        Column(Modifier.padding(horizontal = 24.dp, vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            mascotContent(76.dp)
            Spacer(Modifier.height(7.dp))
            Text("어떻게 냠냠할까요?", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ink)
            Spacer(Modifier.height(5.dp))
            Spacer(Modifier.height(16.dp))

            Button(onClick = onNormal, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = primary), shape = RoundedCornerShape(17.dp)) {
                Text("일반 모드", fontWeight = FontWeight.ExtraBold)
            }

            Spacer(Modifier.height(12.dp))

            Button(onClick = onTimeAttack, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = primaryDark), shape = RoundedCornerShape(17.dp)) {
                Text("타임어택 60초", fontWeight = FontWeight.ExtraBold)
            }
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
    onRestart: () -> Unit,
    onExit: () -> Unit
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
            Text(if (mode == FishMode.TIME_ATTACK) "60초 끝!" else "앗, 물고기를 놓쳤어요!", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ink)
            Spacer(Modifier.height(4.dp))
            Text("${score}마리", fontSize = 30.sp, fontWeight = FontWeight.Black, color = primaryDark)
            Text(if (mode == FishMode.TIME_ATTACK) "타임어택 최고 기록 ${best}마리" else "최고 기록 ${best}마리", fontSize = 11.sp, color = muted)
            Spacer(Modifier.height(15.dp))
            Button(onClick = onRestart, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp), colors = ButtonDefaults.buttonColors(containerColor = primary)) {
                Text("다시하기", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp)) {
                Text("그만하기", fontWeight = FontWeight.Bold)
            }
        }
    }
}
