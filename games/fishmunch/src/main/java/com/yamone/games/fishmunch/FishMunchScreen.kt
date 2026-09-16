package com.yamone.games.fishmunch

import com.yamone.games.arcadecore.*
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
import androidx.compose.ui.graphics.StrokeCap
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
    val experience = LocalGameExperience.current
    val session = rememberArcadeSession(MusicScene.FISH, state.started, state.gameOver, exitConfirm)
    var previousBest by remember { mutableIntStateOf(normalRecords.firstOrNull()?.score ?: 0) }

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
        previousBest = recordStorage.topRecords(mode.gameId).firstOrNull()?.score ?: 0
        session.paused = false
        experience?.resumeByUser()
        experience?.play(GameSound.START)
        state.start(mode)
    }

    fun restart() {
        lastRecord = null
        previousBest = recordStorage.topRecords(state.mode.gameId).firstOrNull()?.score ?: 0
        session.paused = false
        experience?.resumeByUser()
        experience?.play(GameSound.START)
        state.restartCurrentMode()
    }

    fun selectModeAgain() {
        lastRecord = null
        session.paused = false
        state.returnToModeSelect()
    }

    LaunchedEffect(Unit) {
        var previous = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (previous != 0L && !exitConfirm && !session.paused && experience?.foreground != false) {
                    state.update((now - previous) / 1_000_000_000f, playerHalfWidth, playerHalfHeight)
                }
                previous = now
            }
        }
    }

    LaunchedEffect(state.score) {
        if (state.score > 0 && state.started && !state.gameOver && !session.paused) experience?.play(GameSound.COLLECT, haptic = true)
    }

    LaunchedEffect(state.gameOver) {
        if (state.gameOver && lastRecord == null) {
            experience?.play(if (state.score > previousBest) GameSound.RECORD else GameSound.FINISH)
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

    Column(Modifier.fillMaxSize().background(soft)) {
        SessionHeader("물고기 냠냠", "좌우로 움직여 물고기를 받아요", primaryDark, ::requestExit,
            state.started && !state.gameOver, session, mascotContent)

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
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFD2F3F7), Color(0xFF9ADCE7), Color(0xFF64BBCF))
                    )
                )
                .pointerInput(state.started, state.gameOver, exitConfirm, session.paused, experience?.foreground) {
                    if (state.started && !state.gameOver && !exitConfirm && !session.paused && experience?.foreground != false) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            if (size.width > 0) state.dragBy(dragAmount / size.width.toFloat())
                        }
                    }
                }
        ) {
            ArcadeScenery(MusicScene.FISH, Modifier.matchParentSize(), state.elapsed)

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
                    mascotContent = mascotContent,
                    onNormal = { start(FishMode.NORMAL) },
                    onTimeAttack = { start(FishMode.TIME_ATTACK) }
                )
            }

            if (state.gameOver) {
                GameResultPanel(Modifier.align(Alignment.Center), state.score, previousBest, "마리", primaryDark,
                    onRetry = ::restart, onExit = ::selectModeAgain, exitLabel = "모드 선택", mascot = mascotContent)
            }
        }

    }

    if (!exitConfirm && (!state.gameOver || session.showSoundSettings)) SessionDialogs(session, ::requestExit, mascotContent)

    if (exitConfirm) {
        AlertDialog(
            onDismissRequest = { exitConfirm = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("게임을 그만둘까요?", fontWeight = FontWeight.Black, color = ink) },
            confirmButton = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { exitConfirm = false },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("계속하기", fontWeight = FontWeight.Bold, color = primaryDark) }
                    Button(
                        onClick = {
                            exitConfirm = false
                            selectModeAgain()
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFE7EC),
                            contentColor = Color(0xFFD85C6A)
                        )
                    ) { Text("게임 종료", fontWeight = FontWeight.Bold) }
                }
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
        drawOval(bodyColor, Offset(bodyLeft, bodyTop), Size(bodyWidth, bodyHeight))
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
private fun BoxScope.ModeSelectOverlay(primary: Color, primaryDark: Color, mascotContent: @Composable (Dp) -> Unit,
                                      onNormal: () -> Unit, onTimeAttack: () -> Unit) {
    Surface(Modifier.align(Alignment.Center).widthIn(max = 340.dp).fillMaxWidth().padding(22.dp), shape = RoundedCornerShape(28.dp), color = Color.White.copy(alpha = .97f)) {
        Column(Modifier.padding(23.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            mascotContent(80.dp)
            Text("물고기 냠냠", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = primaryDark)
            Text("좌우로 움직여 물고기를 받아요", fontSize = 13.sp, color = primaryDark)
            Button(onClick = onNormal, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primary)) { Text("일반 · 놓치면 끝!") }
            Button(onClick = onTimeAttack, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryDark)) { Text("60초 타임어택 · 최대한 많이!") }
            Text("두 모드의 기록과 순위는 따로 저장돼요", fontSize = 11.sp, color = primaryDark.copy(alpha = .7f))
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
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onRestart,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) { Text("다시하기", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = onExit,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) { Text("그만하기", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
