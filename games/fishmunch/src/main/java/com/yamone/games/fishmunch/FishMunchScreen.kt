package com.yamone.games.fishmunch

import com.yamone.games.arcadecore.*
import androidx.compose.material3.*
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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
import kotlin.math.sin
import kotlin.random.Random

private data class FallingFish(
    val id: Int,
    val baseX: Float,
    val x: Float,
    val y: Float,
    val style: Int,
    val sizeTier: Int,
    val phase: Float,
    val amplitude: Float,
    val fallFactor: Float
)

private class FishMunchState {
    var playerX by mutableFloatStateOf(0.5f)
    var score by mutableIntStateOf(0)
    var started by mutableStateOf(false)
    var gameOver by mutableStateOf(false)
    var serial by mutableIntStateOf(1)
    var elapsed by mutableFloatStateOf(0f)
    var normalFish by mutableStateOf<List<FallingFish>>(emptyList())

    private var nextBaseSpawnAt = 1f
    private var preparedExtraWindow = 0
    private var extraSpawnTimes = mutableListOf<Float>()
    private var nextExtraSpawnIndex = 0
    private var speciesBag = emptyList<Int>()
    private var speciesIndex = 0
    private var speciesCycle = 0

    fun start() {
        playerX = 0.5f
        score = 0
        elapsed = 0f
        normalFish = emptyList()
        started = true
        gameOver = false
        serial++
        resetSpawnSchedule()
        resetSpeciesBag()
        spawnOneFish(initial = true)
    }

    fun restart() = start()

    fun resetToStart() {
        playerX = 0.5f
        score = 0
        elapsed = 0f
        normalFish = emptyList()
        started = false
        gameOver = false
        resetSpawnSchedule()
        resetSpeciesBag()
    }

    fun dragBy(deltaNormalized: Float) {
        if (!started || gameOver) return
        playerX = (playerX + deltaNormalized * 1.20f).coerceIn(0.045f, 0.955f)
    }

    fun update(dtRaw: Float, playerHalfWidth: Float, playerHalfHeight: Float) {
        if (!started || gameOver) return
        val dt = dtRaw.coerceIn(0f, 0.033f)
        elapsed += dt
        spawnDueFish()

        // v0.3.07: no speed ceiling. The whole school keeps getting faster over time.
        val baseSpeed = 0.36f + elapsed * 0.0031f + score * 0.0025f
        val phaseSpeed = 2.55f + elapsed * 0.014f
        var missed = false

        val next = buildList {
            normalFish.forEach { fish ->
                val nextPhase = fish.phase + dt * phaseSpeed
                val nextY = fish.y + baseSpeed * sizeSpeedFactor(fish.sizeTier) * fish.fallFactor * dt
                val nextX = (fish.baseX + sin(nextPhase.toDouble()).toFloat() * fish.amplitude)
                    .coerceIn(0.055f, 0.945f)

                val caught = isCaught(
                    x = nextX,
                    y = nextY,
                    sizeTier = fish.sizeTier,
                    style = fish.style,
                    playerHalfWidth = playerHalfWidth,
                    playerHalfHeight = playerHalfHeight
                )

                when {
                    caught -> { score++; GameFeedback.play("collect") }
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
        while (nextExtraSpawnIndex < extraSpawnTimes.size && elapsed >= extraSpawnTimes[nextExtraSpawnIndex]) {
            spawnOneFish()
            nextExtraSpawnIndex++
        }
    }

    private fun prepareExtraWindows(targetWindow: Int) {
        while (preparedExtraWindow < targetWindow) {
            preparedExtraWindow++
            val extraCount = preparedExtraWindow
            val windowStart = preparedExtraWindow * 5f
            val random = Random(50_003 + preparedExtraWindow * 977)
            val segment = 5f / extraCount
            val times = (0 until extraCount).map { index ->
                val offset = index * segment + random.nextFloat() * segment
                (windowStart + offset).coerceIn(windowStart + 0.15f, windowStart + 4.85f)
            }.sorted()
            extraSpawnTimes.addAll(times)
        }
    }

    private fun spawnOneFish(initial: Boolean = false) {
        if (normalFish.size >= MAX_ACTIVE_NORMAL_FISH) return

        serial++
        val random = Random(serial * 137 + elapsed.toInt() * 31)
        val sizeTier = chooseSizeTier(random)
        val style = nextSpecies()
        val margin = fishMargin(sizeTier)
        val baseX = margin + random.nextFloat() * (1f - margin * 2f)
        normalFish = normalFish + FallingFish(
            id = serial,
            baseX = baseX,
            x = baseX,
            y = if (initial) 0.025f else -0.025f - random.nextFloat() * 0.07f,
            style = style,
            sizeTier = sizeTier,
            phase = random.nextFloat() * 6.28f,
            amplitude = 0.035f + random.nextFloat() * 0.055f,
            fallFactor = 0.92f + random.nextFloat() * 0.18f
        )
    }

    private fun resetSpeciesBag() {
        speciesBag = emptyList()
        speciesIndex = 0
        speciesCycle = 0
    }

    private fun nextSpecies(): Int {
        if (speciesIndex >= speciesBag.size) {
            val random = Random(730_201 + speciesCycle * 104_729)
            speciesBag = (0 until FISH_STYLE_COUNT).shuffled(random)
            speciesIndex = 0
            speciesCycle++
        }
        return speciesBag[speciesIndex++]
    }

    private fun chooseSizeTier(random: Random): Int {
        // Start 10/9/8, gradually add smaller fish, mix all sizes,
        // then retire the biggest sizes until only 4/3/2/1 remain.
        val range = when {
            elapsed < 18f -> 8..10
            elapsed < 32f -> 7..10
            elapsed < 46f -> 6..10
            elapsed < 60f -> 5..10
            elapsed < 74f -> 4..10
            elapsed < 84f -> 3..10
            elapsed < 92f -> 2..10
            elapsed < 104f -> 1..10
            elapsed < 120f -> 1..9
            elapsed < 136f -> 1..8
            elapsed < 152f -> 1..7
            elapsed < 168f -> 1..6
            elapsed < 184f -> 1..5
            else -> 1..4
        }
        return random.nextInt(range.first, range.last + 1)
    }

    private fun sizeSpeedFactor(sizeTier: Int): Float {
        val tier = sizeTier.coerceIn(1, 10)
        return 1.45f - (tier - 1) * (0.73f / 9f) // 1=fastest, 10=slowest
    }

    private fun fishMargin(sizeTier: Int): Float = 0.055f + sizeTier.coerceIn(1, 10) * 0.0065f

    private fun isCaught(
        x: Float,
        y: Float,
        sizeTier: Int,
        style: Int,
        playerHalfWidth: Float,
        playerHalfHeight: Float
    ): Boolean {
        val tier = sizeTier.coerceIn(1, 10)
        val slender = style in setOf(5, 8, 14, 15, 18, 22, 23, 25, 28)
        val tall = style in setOf(1, 6, 9, 10, 11, 19, 20, 24, 26, 29)
        val fishHalfWidth = (0.017f + tier * 0.0042f) * if (slender) 1.14f else 1f
        val fishHalfHeight = (0.012f + tier * 0.0030f) * if (tall) 1.18f else 1f
        return abs(x - playerX) <= playerHalfWidth + fishHalfWidth &&
            abs(y - PLAYER_Y) <= playerHalfHeight + fishHalfHeight
    }

    companion object {
        const val PLAYER_Y = 0.80f
        private const val MAX_ACTIVE_NORMAL_FISH = 36
        private const val FISH_STYLE_COUNT = 30
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
    var lastRecord by remember { mutableStateOf<ArcadeRecord?>(null) }
    var exitConfirm by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var newBest by remember { mutableStateOf(false) }
    var roundBestBefore by remember { mutableIntStateOf(0) }

    DisposableEffect(paused, exitConfirm, state.gameOver) {
        GameFeedback.setPaused(paused || exitConfirm || state.gameOver)
        onDispose { }
    }
    DisposableEffect(Unit) { onDispose { GameFeedback.setPaused(false) } }

    fun requestExit() {
        if (!state.started || state.gameOver) onBack() else exitConfirm = true
    }

    BackHandler { if (paused) paused = false else requestExit() }

    val currentRecords = normalRecords
    val best = currentRecords.firstOrNull()?.score ?: 0

    fun start() {
        roundBestBefore = normalRecords.firstOrNull()?.score ?: 0
        newBest = false
        paused = false
        GameFeedback.play("start")
        lastRecord = null
        state.start()
    }

    fun restart() {
        roundBestBefore = best
        newBest = false
        paused = false
        GameFeedback.play("start")
        lastRecord = null
        state.restart()
    }

    fun returnToStart() {
        lastRecord = null
        state.resetToStart()
    }

    LaunchedEffect(Unit) {
        var previous = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (previous != 0L && !exitConfirm && !paused && GameFeedback.canAdvance) {
                    state.update((now - previous) / 1_000_000_000f, playerHalfWidth, playerHalfHeight)
                }
                previous = now
            }
        }
    }

    LaunchedEffect(state.gameOver) {
        if (state.gameOver && lastRecord == null) {
            newBest = state.score > roundBestBefore
            GameFeedback.play(if (newBest) "record" else "finish")
            lastRecord = recordStorage.addRecord(
                game = ArcadeGameId.FISH_MUNCH,
                score = state.score,
                nickname = nickname
            )
            normalRecords = recordStorage.topRecords(ArcadeGameId.FISH_MUNCH)
        }
    }

    Column(Modifier.fillMaxSize().background(soft)) {
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(onClick = ::requestExit, color = Color.Transparent) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(30.dp)) {
                        val stroke = 4.dp.toPx()
                        val tip = Offset(size.width * 0.16f, size.height * 0.50f)
                        val tail = Offset(size.width * 0.84f, size.height * 0.50f)
                        drawLine(primaryDark, tail, tip, strokeWidth = stroke, cap = StrokeCap.Round)
                        drawLine(primaryDark, tip, Offset(size.width * 0.43f, size.height * 0.22f), strokeWidth = stroke, cap = StrokeCap.Round)
                        drawLine(primaryDark, tip, Offset(size.width * 0.43f, size.height * 0.78f), strokeWidth = stroke, cap = StrokeCap.Round)
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Text("물고기 냠냠", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ink)
            Spacer(Modifier.weight(1f))
            IconButton(
                modifier = Modifier.semantics { contentDescription = "물고기 일시정지" },
                onClick = { GameFeedback.tap(); paused = true },
                enabled = state.started && !state.gameOver
            ) { Text("Ⅱ", fontSize = 25.sp, color = primaryDark) }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip(Modifier.weight(1f), "먹은 물고기", "${state.score}마리", primaryDark, ink)
            StatChip(Modifier.weight(1f), "최고", "${best}마리", primaryDark, ink)
        }

        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFBFEAF5), Color(0xFF8FD0E5), Color(0xFF67B9D4))))
                .pointerInput(state.started, state.gameOver, exitConfirm, paused) {
                    if (state.started && !state.gameOver && !exitConfirm && !paused && GameFeedback.canAdvance) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            if (size.width > 0) state.dragBy(dragAmount / size.width.toFloat())
                        }
                    }
                }
        ) {
            // Preserve the v0.3.05/v0.3.06 painted underwater background exactly.
            Image(
                painterResource(R.drawable.fish_backdrop_v305),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.FillBounds
            )
            Box(Modifier.matchParentSize().background(Color(0xFF082C4C).copy(alpha = .16f)))

            state.normalFish.forEach { fish ->
                key(fish.id) {
                    val fishSize = fishSize(fish.sizeTier)
                    Box(
                        Modifier.offset(
                            x = maxWidth * fish.x - fishSize / 2,
                            y = maxHeight * fish.y - fishSize / 2
                        )
                    ) { PrettyFish(fish.style, fishSize, primary) }
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
                Button(
                    onClick = ::start,
                    modifier = Modifier.align(Alignment.Center).width(220.dp).height(50.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primary)
                ) { Text("시작하기", fontWeight = FontWeight.ExtraBold) }
            }

            if (state.gameOver) {
                ResultOverlay(
                    isNewBest = newBest,
                    score = state.score,
                    best = currentRecords.firstOrNull()?.score ?: state.score,
                    primary = primary,
                    primaryDark = primaryDark,
                    ink = ink,
                    muted = muted,
                    mascotContent = mascotContent,
                    onRestart = ::restart,
                    onExit = onBack
                )
            }
        }
    }

    if (paused) {
        var soundOptions by remember { mutableStateOf(GameFeedback.options) }
        AlertDialog(
            onDismissRequest = { paused = false },
            shape = RoundedCornerShape(26.dp),
            title = { Text("잠깐 쉬어가요", fontWeight = FontWeight.Black) },
            text = {
                Column {
                    Text("진행과 기록 시간은 멈춰 있어요.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("배경음악", Modifier.weight(1f))
                        Switch(soundOptions.music, { soundOptions = soundOptions.copy(music=it); GameFeedback.update(soundOptions) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("진동", Modifier.weight(1f))
                        Switch(soundOptions.vibration, { soundOptions = soundOptions.copy(vibration=it); GameFeedback.update(soundOptions) })
                    }
                }
            },
            confirmButton = { Button(onClick = { paused = false; GameFeedback.tap() }) { Text("계속하기") } },
            dismissButton = { TextButton(onClick = { paused = false; requestExit() }) { Text("게임 종료") } }
        )
    }

    if (exitConfirm) {
        AlertDialog(
            onDismissRequest = { exitConfirm = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("게임을 그만둘까요?", fontWeight = FontWeight.Black, color = ink) },
            confirmButton = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { exitConfirm = false },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("계속하기", fontWeight = FontWeight.Bold, color = primaryDark) }
                    Button(
                        onClick = { exitConfirm = false; returnToStart() },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFE7EC), contentColor = Color(0xFFD85C6A))
                    ) { Text("게임 종료", fontWeight = FontWeight.Bold) }
                }
            }
        )
    }
}

private fun fishSize(sizeTier: Int): Dp = (30 + (sizeTier.coerceIn(1, 10) - 1) * 5).dp

private data class FishVisualSpec(
    val name: String,
    val body: Color,
    val light: Color,
    val dark: Color,
    val shape: Int,
    val pattern: Int,
    val tail: Int = 0
)

private val fishVisuals = listOf(
    FishVisualSpec("흰동가리", Color(0xFFFF743A), Color(0xFFFFD39A), Color(0xFF2F2A28), 0, 0, 0),
    FishVisualSpec("해수엔젤피시", Color(0xFFF4D84B), Color(0xFFF9F7ED), Color(0xFF35384A), 1, 1, 2),
    FishVisualSpec("블루탱", Color(0xFF2C65D8), Color(0xFF4C8BFF), Color(0xFF172B63), 0, 2, 1),
    FishVisualSpec("코이", Color(0xFFF6F3E9), Color(0xFFFFA06B), Color(0xFF56535A), 4, 3, 1),
    FishVisualSpec("금붕어", Color(0xFFFF8C38), Color(0xFFFFD7A1), Color(0xFFD85B2E), 3, 4, 2),
    FishVisualSpec("네온테트라", Color(0xFF2798E6), Color(0xFF7DE7FF), Color(0xFFCF3147), 2, 5, 1),
    FishVisualSpec("베타", Color(0xFF3458C8), Color(0xFFE94B69), Color(0xFF65256E), 10, 6, 2),
    FishVisualSpec("복어", Color(0xFFE2BD55), Color(0xFFFFF0BA), Color(0xFF725B3A), 3, 7, 0),
    FishVisualSpec("고등어", Color(0xFF4C92B7), Color(0xFFC7E8F0), Color(0xFF244A63), 2, 8, 1),
    FishVisualSpec("나비고기", Color(0xFFF3D64A), Color(0xFFFFF6D2), Color(0xFF313239), 1, 9, 0),
    FishVisualSpec("쏠배감펭", Color(0xFFD6573F), Color(0xFFF8D4C0), Color(0xFF6E302B), 7, 10, 2),
    FishVisualSpec("디스커스", Color(0xFF3AA7CE), Color(0xFFFF765D), Color(0xFF255B8B), 1, 11, 0),
    FishVisualSpec("구피", Color(0xFF4B9AB5), Color(0xFFFFC84E), Color(0xFFEA6B42), 2, 12, 2),
    FishVisualSpec("만다린피쉬", Color(0xFF1767B8), Color(0xFFFF8A39), Color(0xFF0B4E78), 0, 13, 2),
    FishVisualSpec("연어", Color(0xFFBFC8CC), Color(0xFFF08394), Color(0xFF5B6973), 2, 14, 1),
    FishVisualSpec("메기", Color(0xFF6D6A62), Color(0xFFA59A85), Color(0xFF383A36), 9, 15, 1),
    FishVisualSpec("쥐치", Color(0xFF839F9C), Color(0xFFE3D86E), Color(0xFF52646A), 0, 16, 0),
    FishVisualSpec("블루탱서전피쉬", Color(0xFF265FD4), Color(0xFF3F94FF), Color(0xFF182C66), 0, 17, 1),
    FishVisualSpec("날치", Color(0xFF4B83B4), Color(0xFFCFE9F7), Color(0xFF274A6A), 8, 18, 1),
    FishVisualSpec("엔젤피쉬", Color(0xFFF2E6D4), Color(0xFFFFB34C), Color(0xFF33343D), 1, 19, 2),
    FishVisualSpec("해마", Color(0xFFF2A43B), Color(0xFFFFD77C), Color(0xFF9C5B28), 6, 20, 4),
    FishVisualSpec("흰점복어", Color(0xFF2F323A), Color(0xFFFFD247), Color(0xFFF3F2E9), 0, 21, 0),
    FishVisualSpec("아처피시", Color(0xFFB8C4C8), Color(0xFFF4E8CE), Color(0xFF40434A), 2, 22, 1),
    FishVisualSpec("소드테일", Color(0xFFEE6135), Color(0xFFFFA34E), Color(0xFFC7422C), 2, 23, 3),
    FishVisualSpec("개복치", Color(0xFF8DA8BC), Color(0xFFD8E6EF), Color(0xFF5C7387), 3, 24, 0),
    FishVisualSpec("곰치", Color(0xFFC38B37), Color(0xFFF0BE55), Color(0xFF6A4A2D), 5, 25, 4),
    FishVisualSpec("나비고기-긴주둥이", Color(0xFFF2C440), Color(0xFFF8F1DC), Color(0xFF34353B), 1, 26, 0),
    FishVisualSpec("펄구라미", Color(0xFF6CA1B4), Color(0xFFDAEEF3), Color(0xFFE16C45), 0, 27, 0),
    FishVisualSpec("제브라다니오", Color(0xFF7AA4BE), Color(0xFFE4EEF1), Color(0xFF244E80), 2, 28, 1),
    FishVisualSpec("파로트시클리드", Color(0xFFF05738), Color(0xFFFF9F69), Color(0xFFB43B31), 3, 29, 0)
)

@Composable
private fun PrettyFish(styleRaw: Int, size: Dp, primary: Color) {
    val style = ((styleRaw % fishVisuals.size) + fishVisuals.size) % fishVisuals.size
    val spec = fishVisuals[style]

    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val body = spec.body
        val light = spec.light
        val dark = spec.dark
        val cy = h * .50f

        if (spec.shape == 6) {
            // 21. Seahorse — upright silhouette with curled tail.
            val torso = Path().apply {
                moveTo(w*.58f,h*.18f)
                cubicTo(w*.42f,h*.22f,w*.42f,h*.40f,w*.52f,h*.49f)
                cubicTo(w*.61f,h*.58f,w*.55f,h*.72f,w*.44f,h*.75f)
                cubicTo(w*.36f,h*.77f,w*.38f,h*.68f,w*.44f,h*.66f)
                cubicTo(w*.49f,h*.64f,w*.45f,h*.58f,w*.40f,h*.58f)
                cubicTo(w*.28f,h*.58f,w*.25f,h*.47f,w*.34f,h*.40f)
                cubicTo(w*.39f,h*.35f,w*.37f,h*.26f,w*.46f,h*.21f)
                close()
            }
            drawPath(torso, Brush.verticalGradient(listOf(light, body, dark)))
            drawPath(torso, dark.copy(alpha=.55f), style=Stroke((w*.022f).coerceAtLeast(1f)))
            val snout=Path().apply{
                moveTo(w*.48f,h*.23f);lineTo(w*.20f,h*.26f);lineTo(w*.45f,h*.32f);close()
            }
            drawPath(snout,body)
            drawCircle(Color.White,w*.042f,Offset(w*.50f,h*.25f))
            drawCircle(Color(0xFF233642),w*.020f,Offset(w*.51f,h*.25f))
            repeat(5){i->
                drawLine(light.copy(alpha=.8f),Offset(w*(.43f+i*.025f),h*(.34f+i*.07f)),Offset(w*(.57f+i*.010f),h*(.35f+i*.07f)),(w*.016f).coerceAtLeast(1f),StrokeCap.Round)
            }
            return@Canvas
        }

        if (spec.shape == 5) {
            // 26. Moray eel — long serpentine body.
            val eel = Path().apply {
                moveTo(w*.10f,h*.46f)
                cubicTo(w*.25f,h*.28f,w*.46f,h*.28f,w*.58f,h*.42f)
                cubicTo(w*.70f,h*.57f,w*.83f,h*.63f,w*.93f,h*.48f)
                lineTo(w*.91f,h*.62f)
                cubicTo(w*.79f,h*.76f,w*.65f,h*.70f,w*.52f,h*.55f)
                cubicTo(w*.40f,h*.42f,w*.24f,h*.42f,w*.10f,h*.58f)
                close()
            }
            drawPath(eel,Brush.linearGradient(listOf(light,body,dark),Offset.Zero,Offset(w,h)))
            drawPath(eel,dark.copy(alpha=.6f),style=Stroke((w*.020f).coerceAtLeast(1f)))
            repeat(10){i->
                val px=w*(.20f+i*.065f); val py=h*(if(i%2==0).45f else .55f)
                drawCircle(dark.copy(alpha=.55f),w*.023f,Offset(px,py))
            }
            drawCircle(Color.White,w*.035f,Offset(w*.17f,h*.48f))
            drawCircle(Color(0xFF29333A),w*.016f,Offset(w*.175f,h*.48f))
            drawLine(dark,Offset(w*.08f,h*.55f),Offset(w*.18f,h*.56f),(w*.015f).coerceAtLeast(1f),StrokeCap.Round)
            return@Canvas
        }

        val bodyW = when(spec.shape) {
            1 -> w*.55f
            2 -> w*.74f
            3 -> w*.58f
            4 -> w*.76f
            7 -> w*.60f
            8 -> w*.74f
            9 -> w*.72f
            10 -> w*.52f
            else -> w*.65f
        }
        val bodyH = when(spec.shape) {
            1 -> h*.72f
            2 -> h*.36f
            3 -> h*.64f
            4 -> h*.44f
            7 -> h*.54f
            8 -> h*.34f
            9 -> h*.40f
            10 -> h*.42f
            else -> h*.52f
        }
        val bodyLeft = if(spec.shape in setOf(2,4,8,9)) w*.19f else w*.25f
        val bodyTop = (h-bodyH)/2f
        val bodyRight = bodyLeft+bodyW

        drawOval(Color.Black.copy(alpha=.13f),Offset(bodyLeft+w*.025f,bodyTop+h*.035f),Size(bodyW,bodyH))

        // Tail silhouette.
        val tail = Path().apply {
            when(spec.tail) {
                1 -> { // forked
                    moveTo(bodyLeft+w*.05f,cy)
                    lineTo(w*.01f,h*.28f); lineTo(w*.10f,cy)
                    lineTo(w*.01f,h*.72f); close()
                }
                2 -> { // flowing fan
                    moveTo(bodyLeft+w*.06f,cy)
                    cubicTo(w*.04f,h*.12f,w*.01f,h*.16f,w*.02f,h*.46f)
                    cubicTo(w*.01f,h*.82f,w*.06f,h*.88f,bodyLeft+w*.06f,cy)
                    close()
                }
                3 -> { // swordtail
                    moveTo(bodyLeft+w*.04f,cy)
                    lineTo(w*.02f,h*.30f); lineTo(w*.09f,cy)
                    lineTo(w*.02f,h*.70f); lineTo(w*.42f,h*.72f); close()
                }
                else -> {
                    moveTo(bodyLeft+w*.05f,cy)
                    lineTo(w*.03f,h*.26f); lineTo(w*.03f,h*.74f); close()
                }
            }
        }
        drawPath(tail,Brush.linearGradient(listOf(light,body,dark),Offset.Zero,Offset(w*.35f,h)))
        drawPath(tail,dark.copy(alpha=.45f),style=Stroke((w*.020f).coerceAtLeast(1f)))

        // Species-specific fins before the body.
        if(spec.shape==1 || style in setOf(6,10,18,19,26)) {
            val upper=Path().apply{
                moveTo(w*.42f,bodyTop+h*.03f)
                lineTo(w*.54f,bodyTop-h*(if(spec.shape==1).17f else .10f))
                lineTo(w*.69f,bodyTop+h*.05f);close()
            }
            drawPath(upper,light.copy(alpha=.88f))
            val lower=Path().apply{
                moveTo(w*.44f,bodyTop+bodyH-h*.02f)
                lineTo(w*.58f,bodyTop+bodyH+h*(if(spec.shape==1).17f else .10f))
                lineTo(w*.69f,bodyTop+bodyH-h*.03f);close()
            }
            drawPath(lower,body.copy(alpha=.88f))
        }

        if(spec.shape==8) {
            // Flying fish: huge pectoral wings.
            val wing=Path().apply{
                moveTo(w*.42f,cy);lineTo(w*.48f,h*.08f);lineTo(w*.75f,h*.18f);lineTo(w*.62f,cy);close()
            }
            drawPath(wing,light.copy(alpha=.80f))
            drawPath(wing,dark.copy(alpha=.35f),style=Stroke((w*.015f).coerceAtLeast(1f)))
            val wing2=Path().apply{
                moveTo(w*.44f,cy);lineTo(w*.50f,h*.90f);lineTo(w*.72f,h*.80f);lineTo(w*.61f,cy);close()
            }
            drawPath(wing2,light.copy(alpha=.58f))
        }

        if(spec.shape==7) {
            // Lionfish: long defensive spines.
            repeat(7){i->
                val x=w*(.36f+i*.055f)
                drawLine(dark.copy(alpha=.8f),Offset(x,bodyTop+h*.05f),Offset(x-w*.10f,h*(.05f+i*.015f)),(w*.012f).coerceAtLeast(1f),StrokeCap.Round)
            }
        }

        drawOval(
            Brush.linearGradient(listOf(light,body,dark),Offset(bodyLeft,bodyTop),Offset(bodyRight,bodyTop+bodyH)),
            Offset(bodyLeft,bodyTop),Size(bodyW,bodyH)
        )
        drawOval(dark.copy(alpha=.38f),Offset(bodyLeft,bodyTop),Size(bodyW,bodyH),style=Stroke((w*.020f).coerceAtLeast(1f)))

        // Pattern layer based on the 30 concept-sheet species.
        when(spec.pattern) {
            0 -> repeat(3){i->
                val x=w*(.40f+i*.15f)
                drawLine(Color.White.copy(alpha=.92f),Offset(x,bodyTop+bodyH*.08f),Offset(x-w*.025f,bodyTop+bodyH*.92f),(w*.052f).coerceAtLeast(2f),StrokeCap.Round)
                drawLine(Color(0xFF292A2D).copy(alpha=.8f),Offset(x-w*.04f,bodyTop+bodyH*.08f),Offset(x-w*.065f,bodyTop+bodyH*.92f),(w*.020f).coerceAtLeast(1f),StrokeCap.Round)
            }
            1,19 -> repeat(3){i->
                val x=w*(.43f+i*.12f)
                drawLine(dark.copy(alpha=.82f),Offset(x,bodyTop+bodyH*.05f),Offset(x-w*.015f,bodyTop+bodyH*.95f),(w*.034f).coerceAtLeast(1f),StrokeCap.Round)
            }
            2,17 -> {
                val patch=Path().apply{
                    moveTo(w*.40f,bodyTop+bodyH*.18f);lineTo(w*.66f,bodyTop+bodyH*.12f)
                    lineTo(w*.72f,bodyTop+bodyH*.72f);lineTo(w*.48f,bodyTop+bodyH*.80f);close()
                }
                drawPath(patch,dark.copy(alpha=.78f))
            }
            3 -> repeat(7){i->
                val px=w*(.40f+(i%4)*.10f);val py=bodyTop+bodyH*(.25f+(i/4)*.42f)
                drawCircle(if(i%3==0)Color(0xFF39383C) else Color(0xFFE9503C),w*.032f,Offset(px,py))
            }
            4 -> {
                drawOval(light.copy(alpha=.55f),Offset(w*.42f,bodyTop+bodyH*.18f),Size(w*.24f,bodyH*.35f))
                drawCircle(Color.White.copy(alpha=.5f),w*.025f,Offset(w*.51f,bodyTop+bodyH*.68f))
            }
            5 -> {
                drawLine(Color(0xFF30E7FF),Offset(w*.31f,cy-h*.035f),Offset(w*.78f,cy-h*.035f),(h*.055f).coerceAtLeast(2f),StrokeCap.Round)
                drawLine(Color(0xFFE74354),Offset(w*.48f,cy+h*.055f),Offset(w*.78f,cy+h*.055f),(h*.050f).coerceAtLeast(2f),StrokeCap.Round)
            }
            6 -> {
                drawLine(Color(0xFFE54A67),Offset(w*.40f,bodyTop+bodyH*.20f),Offset(w*.68f,bodyTop+bodyH*.65f),(w*.030f).coerceAtLeast(1f),StrokeCap.Round)
                drawLine(Color(0xFF526EF0),Offset(w*.39f,bodyTop+bodyH*.70f),Offset(w*.68f,bodyTop+bodyH*.24f),(w*.025f).coerceAtLeast(1f),StrokeCap.Round)
            }
            7,24 -> repeat(10){i->
                val px=w*(.38f+(i%5)*.075f);val py=bodyTop+bodyH*(.27f+(i/5)*.42f)
                drawCircle(dark.copy(alpha=.30f),w*.018f,Offset(px,py))
            }
            8 -> repeat(6){i->
                val x=w*(.35f+i*.065f)
                drawLine(dark.copy(alpha=.75f),Offset(x,bodyTop+bodyH*.12f),Offset(x+w*.030f,bodyTop+bodyH*.45f),(w*.017f).coerceAtLeast(1f),StrokeCap.Round)
            }
            9,26 -> {
                repeat(3){i->
                    val x=w*(.42f+i*.13f)
                    drawLine(if(spec.pattern==26)Color(0xFFE4942E) else dark,Offset(x,bodyTop+bodyH*.08f),Offset(x,bodyTop+bodyH*.92f),(w*.038f).coerceAtLeast(1f),StrokeCap.Round)
                }
                drawCircle(dark,w*.038f,Offset(w*.68f,bodyTop+bodyH*.48f))
            }
            10 -> repeat(5){i->
                val x=w*(.38f+i*.075f)
                drawLine(Color(0xFFF6E5D9).copy(alpha=.90f),Offset(x,bodyTop+bodyH*.08f),Offset(x+w*.035f,bodyTop+bodyH*.92f),(w*.025f).coerceAtLeast(1f),StrokeCap.Round)
            }
            11 -> repeat(6){i->
                val yy=bodyTop+bodyH*(.15f+i*.13f)
                drawLine(if(i%2==0)light else Color(0xFFFF6C54),Offset(w*.38f,yy),Offset(w*.69f,yy+h*.015f),(w*.018f).coerceAtLeast(1f),StrokeCap.Round)
            }
            12 -> repeat(7){i->
                val px=w*(.40f+(i%4)*.08f);val py=bodyTop+bodyH*(.28f+(i/4)*.40f)
                drawCircle(if(i%2==0)Color(0xFF243D7B) else Color(0xFFFF743D),w*.020f,Offset(px,py))
            }
            13 -> {
                repeat(3){i->
                    val inset=w*(.020f+i*.018f)
                    drawOval(Color(0xFFFF8A39).copy(alpha=.72f-i*.15f),Offset(bodyLeft+inset,bodyTop+inset),Size(bodyW-inset*2,bodyH-inset*2),style=Stroke((w*.018f).coerceAtLeast(1f)))
                }
            }
            14 -> {
                drawLine(Color(0xFFE86F82).copy(alpha=.85f),Offset(w*.28f,cy),Offset(w*.82f,cy),(h*.060f).coerceAtLeast(2f),StrokeCap.Round)
                repeat(5){i->drawCircle(dark.copy(alpha=.6f),w*.010f,Offset(w*(.36f+i*.09f),bodyTop+bodyH*.18f))}
            }
            15 -> repeat(8){i->
                val px=w*(.34f+(i%4)*.10f); val py=bodyTop+bodyH*(.30f+(i/4)*.35f)
                drawCircle(if(i%2==0)light.copy(alpha=.30f) else dark.copy(alpha=.25f),w*.022f,Offset(px,py))
            }
            16 -> {
                drawOval(light.copy(alpha=.28f),Offset(w*.39f,bodyTop+bodyH*.20f),Size(w*.25f,bodyH*.35f))
                drawLine(dark.copy(alpha=.55f),Offset(w*.58f,bodyTop+bodyH*.08f),Offset(w*.60f,bodyTop+bodyH*.92f),(w*.025f).coerceAtLeast(1f),StrokeCap.Round)
            }
            18 -> drawLine(Color.White.copy(alpha=.68f),Offset(w*.30f,cy),Offset(w*.80f,cy),(h*.040f).coerceAtLeast(1f),StrokeCap.Round)
            20 -> repeat(5){i->drawLine(light.copy(alpha=.75f),Offset(w*(.40f+i*.04f),bodyTop+bodyH*.12f),Offset(w*(.43f+i*.035f),bodyTop+bodyH*.88f),(w*.012f).coerceAtLeast(1f),StrokeCap.Round)}
            21 -> {
                repeat(6){i->
                    val px=w*(.38f+(i%3)*.12f);val py=bodyTop+bodyH*(.28f+(i/3)*.38f)
                    drawCircle(Color.White.copy(alpha=.92f),w*.031f,Offset(px,py))
                }
                drawOval(Color(0xFFE9C83D),Offset(w*.39f,bodyTop+bodyH*.04f),Size(w*.25f,bodyH*.28f))
            }
            22 -> repeat(4){i->
                val x=w*(.40f+i*.095f)
                drawLine(dark.copy(alpha=.75f),Offset(x,bodyTop+bodyH*.10f),Offset(x+w*.020f,bodyTop+bodyH*.90f),(w*.027f).coerceAtLeast(1f),StrokeCap.Round)
            }
            23 -> drawLine(light.copy(alpha=.45f),Offset(w*.32f,bodyTop+bodyH*.30f),Offset(w*.80f,bodyTop+bodyH*.30f),(h*.025f).coerceAtLeast(1f),StrokeCap.Round)
            27 -> repeat(12){i->
                val px=w*(.36f+(i%6)*.065f);val py=bodyTop+bodyH*(.30f+(i/6)*.38f)
                drawCircle(Color.White.copy(alpha=.75f),w*.011f,Offset(px,py))
            }
            28 -> repeat(4){i->
                val yy=bodyTop+bodyH*(.24f+i*.16f)
                drawLine(dark.copy(alpha=.85f),Offset(w*.30f,yy),Offset(w*.81f,yy),(h*.025f).coerceAtLeast(1f),StrokeCap.Round)
            }
            29 -> {
                repeat(4){i->
                    val x=w*(.38f+i*.09f)
                    drawLine(light.copy(alpha=.35f),Offset(x,bodyTop+bodyH*.15f),Offset(x+w*.04f,bodyTop+bodyH*.78f),(w*.014f).coerceAtLeast(1f),StrokeCap.Round)
                }
            }
        }

        // Puffer / sunfish extra silhouette details.
        if(style==7) {
            listOf(.38f,.46f,.55f,.64f,.72f).forEach { x ->
                drawLine(dark.copy(alpha=.55f),Offset(w*x,bodyTop+h*.02f),Offset(w*x,bodyTop-h*.045f),(w*.010f).coerceAtLeast(1f),StrokeCap.Round)
                drawLine(dark.copy(alpha=.55f),Offset(w*x,bodyTop+bodyH-h*.02f),Offset(w*x,bodyTop+bodyH+h*.045f),(w*.010f).coerceAtLeast(1f),StrokeCap.Round)
            }
        }

        if(style==15) {
            // Catfish barbels.
            drawLine(dark.copy(alpha=.75f),Offset(bodyRight-w*.04f,cy),Offset(w*.98f,h*.35f),(w*.010f).coerceAtLeast(1f),StrokeCap.Round)
            drawLine(dark.copy(alpha=.75f),Offset(bodyRight-w*.04f,cy+h*.02f),Offset(w*.98f,h*.68f),(w*.010f).coerceAtLeast(1f),StrokeCap.Round)
        }

        if(style==12) {
            // Guppy fan tail overlay.
            val fan=Path().apply{
                moveTo(bodyLeft+w*.05f,cy)
                lineTo(w*.01f,h*.16f);lineTo(w*.01f,h*.84f);close()
            }
            drawPath(fan,Brush.linearGradient(listOf(Color(0xFFFFD34F),Color(0xFFFF7146),Color(0xFF274DA1))))
            repeat(5){i->drawCircle(Color(0xFF273D75).copy(alpha=.65f),w*.015f,Offset(w*.06f,h*(.30f+i*.10f)))}
        }

        if(style==6) {
            // Betta: extra layered flowing tail and ventral fins.
            val veil=Path().apply{
                moveTo(bodyLeft+w*.05f,cy);cubicTo(w*.04f,h*.08f,w*.01f,h*.14f,w*.02f,h*.48f)
                cubicTo(w*.01f,h*.87f,w*.10f,h*.91f,bodyLeft+w*.05f,cy);close()
            }
            drawPath(veil,Brush.linearGradient(listOf(Color(0xFFE54869),Color(0xFF354FC7),Color(0xFF7A2F8B))))
            drawLine(Color(0xFFE54869),Offset(w*.55f,bodyTop+bodyH*.80f),Offset(w*.48f,h*.91f),(w*.012f).coerceAtLeast(1f),StrokeCap.Round)
        }

        val eyeX = bodyRight-bodyW*.15f
        val eyeY = bodyTop+bodyH*.38f
        drawCircle(Color.White,w*.052f,Offset(eyeX,eyeY))
        drawCircle(Color(0xFF182F3D),w*.025f,Offset(eyeX+w*.009f,eyeY))
        drawCircle(Color.White,w*.008f,Offset(eyeX+w*.016f,eyeY-w*.010f))

        val mouthY=bodyTop+bodyH*.60f
        drawLine(dark.copy(alpha=.70f),Offset(bodyRight-bodyW*.07f,mouthY),Offset(bodyRight+w*.012f,mouthY-h*.006f),(w*.014f).coerceAtLeast(1f),StrokeCap.Round)
        drawOval(Color.White.copy(alpha=.35f),Offset(bodyLeft+bodyW*.18f,bodyTop+bodyH*.10f),Size(bodyW*.34f,bodyH*.10f))
        drawCircle(primary.copy(alpha=.14f),w*.022f,Offset(bodyLeft+bodyW*.70f,bodyTop+bodyH*.70f))
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
private fun BoxScope.ResultOverlay(
    isNewBest: Boolean,
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
            Text(if (isNewBest) "새로운 최고기록!" else "즐거운 바다 한 판!", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ink)
            Spacer(Modifier.height(4.dp))
            Text("${score}마리", fontSize = 30.sp, fontWeight = FontWeight.Black, color = primaryDark)
            Text("최고 기록 ${best}마리", fontSize = 11.sp, color = muted)
            Spacer(Modifier.height(15.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(onClick=onRestart,modifier=Modifier.weight(1f).height(46.dp),shape=RoundedCornerShape(16.dp),contentPadding=PaddingValues(horizontal=4.dp)) {
                    Text("다시하기",fontSize=12.sp,fontWeight=FontWeight.Bold)
                }
                FilledTonalButton(onClick=onExit,modifier=Modifier.weight(1f).height(46.dp),shape=RoundedCornerShape(16.dp),contentPadding=PaddingValues(horizontal=4.dp)) {
                    Text("그만하기",fontSize=12.sp,fontWeight=FontWeight.Bold)
                }
            }
        }
    }
}
