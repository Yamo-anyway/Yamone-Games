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

@Composable
private fun PrettyFish(styleRaw: Int, size: Dp, primary: Color) {
    val style = ((styleRaw % 10) + 10) % 10
    val palette = when (style) {
        0 -> Triple(Color(0xFFFF755B), Color(0xFFFFD08A), Color(0xFFD94B48)) // clownfish
        1 -> Triple(Color(0xFF63D2EA), Color(0xFFD5FBFF), Color(0xFF2D76B9)) // angelfish
        2 -> Triple(Color(0xFFFFD84E), Color(0xFFFFF3A6), Color(0xFFE99030)) // butterflyfish
        3 -> Triple(Color(0xFFF48FB7), Color(0xFFFFD9E9), Color(0xFFBE4E82)) // pink spotted
        4 -> Triple(Color(0xFF60D2A7), Color(0xFFD9FFF0), Color(0xFF258B75)) // mint reef
        5 -> Triple(Color(0xFF9B86E6), Color(0xFFE8E2FF), Color(0xFF634CA9)) // purple fin
        6 -> Triple(Color(0xFFFFA33E), Color(0xFFFFE49B), Color(0xFFDB6630)) // goldfish
        7 -> Triple(Color(0xFF348FD7), Color(0xFFA3F1FB), Color(0xFF214F99)) // blue tang
        8 -> Triple(Color(0xFF9BCB58), Color(0xFFEAF4A5), Color(0xFF4C8B4B)) // puffer
        else -> Triple(Color(0xFFB379D9), Color(0xFFF2DFFF), Color(0xFF6C49A1)) // neon slim
    }

    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val body = palette.first
        val light = palette.second
        val dark = palette.third

        val bodyW = when (style) { 1,5 -> w*.56f; 7,9 -> w*.72f; 8 -> w*.58f; else -> w*.65f }
        val bodyH = when (style) { 1 -> h*.72f; 5 -> h*.64f; 8 -> h*.66f; 7,9 -> h*.40f; else -> h*.53f }
        val bodyLeft = w*.25f
        val bodyTop = (h-bodyH)/2f
        val bodyRight = bodyLeft + bodyW
        val cy = h*.50f

        // soft drop shadow
        drawOval(Color.Black.copy(alpha=.14f), Offset(bodyLeft+w*.025f, bodyTop+h*.04f), Size(bodyW, bodyH))

        val tail = when(style) {
            6 -> Path().apply {
                moveTo(w*.30f,cy); lineTo(w*.03f,h*.20f); lineTo(w*.13f,cy); lineTo(w*.03f,h*.80f); close()
            }
            1,5 -> Path().apply {
                moveTo(w*.29f,cy); lineTo(w*.02f,h*.17f); lineTo(w*.08f,cy); lineTo(w*.02f,h*.83f); close()
            }
            7,9 -> Path().apply {
                moveTo(w*.28f,cy); lineTo(w*.04f,h*.31f); lineTo(w*.04f,h*.69f); close()
            }
            else -> Path().apply {
                moveTo(w*.29f,cy); lineTo(w*.04f,h*.24f); lineTo(w*.04f,h*.76f); close()
            }
        }
        drawPath(tail, Brush.linearGradient(listOf(light,body,dark), Offset.Zero, Offset(w*.35f,h)))
        drawPath(tail, Color.White.copy(alpha=.90f), style=Stroke((w*.025f).coerceAtLeast(1f)))

        // distinctive fins
        if (style in setOf(1,5,6,9)) {
            val fin = Path().apply {
                moveTo(w*.43f,bodyTop+h*.04f)
                lineTo(w*.56f,bodyTop-h*(if(style==1) .18f else .10f))
                lineTo(w*.68f,bodyTop+h*.05f)
                close()
            }
            drawPath(fin, Brush.linearGradient(listOf(light,body)))
            drawPath(fin, Color.White.copy(alpha=.76f), style=Stroke((w*.018f).coerceAtLeast(1f)))
        }
        if (style in setOf(1,5)) {
            val fin = Path().apply {
                moveTo(w*.47f,bodyTop+bodyH-h*.03f)
                lineTo(w*.58f,bodyTop+bodyH+h*.14f)
                lineTo(w*.69f,bodyTop+bodyH-h*.04f)
                close()
            }
            drawPath(fin, body.copy(alpha=.90f))
        }

        drawOval(
            Brush.linearGradient(listOf(light,body,dark), Offset(bodyLeft,bodyTop), Offset(bodyRight,bodyTop+bodyH)),
            Offset(bodyLeft,bodyTop), Size(bodyW,bodyH)
        )
        drawOval(Color.White.copy(alpha=.92f), Offset(bodyLeft,bodyTop), Size(bodyW,bodyH), style=Stroke((w*.025f).coerceAtLeast(1f)))

        // pattern layer: each silhouette gets a different recognizable detail.
        when(style) {
            0 -> repeat(2) { i ->
                val x=w*(.46f+i*.16f)
                drawLine(Color.White.copy(alpha=.90f),Offset(x,bodyTop+bodyH*.11f),Offset(x-w*.035f,bodyTop+bodyH*.89f),(w*.055f).coerceAtLeast(2f),StrokeCap.Round)
            }
            1 -> {
                drawOval(Color.White.copy(alpha=.34f),Offset(w*.40f,bodyTop+bodyH*.16f),Size(w*.24f,bodyH*.38f))
                drawLine(dark.copy(alpha=.50f),Offset(w*.57f,bodyTop+bodyH*.06f),Offset(w*.49f,bodyTop+bodyH*.91f),(w*.027f).coerceAtLeast(1f),StrokeCap.Round)
            }
            2 -> {
                drawLine(Color(0xFF4D4D46).copy(alpha=.68f),Offset(w*.65f,bodyTop+bodyH*.09f),Offset(w*.61f,bodyTop+bodyH*.91f),(w*.040f).coerceAtLeast(2f),StrokeCap.Round)
                drawCircle(light.copy(alpha=.72f),w*.043f,Offset(w*.49f,h*.47f))
            }
            3 -> repeat(5) { i ->
                val px=w*(.42f+(i%3)*.11f); val py=bodyTop+bodyH*(.28f+(i/3)*.38f)
                drawCircle(light.copy(alpha=.78f),w*.026f,Offset(px,py))
            }
            4 -> repeat(3) { i ->
                val x=w*(.43f+i*.11f)
                drawLine(light.copy(alpha=.82f),Offset(x,bodyTop+bodyH*.14f),Offset(x-w*.025f,bodyTop+bodyH*.86f),(w*.024f).coerceAtLeast(1f),StrokeCap.Round)
            }
            5 -> {
                drawLine(light.copy(alpha=.86f),Offset(w*.40f,cy),Offset(w*.70f,cy),(h*.074f).coerceAtLeast(2f),StrokeCap.Round)
                drawCircle(dark.copy(alpha=.40f),w*.044f,Offset(w*.53f,h*.40f))
            }
            6 -> {
                drawOval(light.copy(alpha=.60f),Offset(w*.41f,bodyTop+bodyH*.18f),Size(w*.26f,bodyH*.34f))
                drawCircle(Color.White.copy(alpha=.55f),w*.034f,Offset(w*.48f,h*.62f))
            }
            7 -> {
                drawLine(Color(0xFFC5FAFF),Offset(w*.38f,h*.45f),Offset(w*.70f,h*.45f),(h*.072f).coerceAtLeast(2f),StrokeCap.Round)
                drawLine(Color(0xFF183E83).copy(alpha=.72f),Offset(w*.42f,h*.61f),Offset(w*.67f,h*.61f),(h*.035f).coerceAtLeast(1f),StrokeCap.Round)
            }
            8 -> {
                repeat(5) { i ->
                    val a=(i-2)*.18f
                    drawCircle(dark.copy(alpha=.35f),w*.026f,Offset(w*(.54f+a),h*(if(i%2==0).40f else .60f)))
                }
                listOf(.39f,.48f,.58f,.68f).forEach { x ->
                    drawLine(dark.copy(alpha=.40f),Offset(w*x,bodyTop+h*.01f),Offset(w*x,bodyTop-h*.05f),(w*.011f).coerceAtLeast(1f),StrokeCap.Round)
                }
            }
            9 -> {
                drawLine(Color(0xFFD3FCFF).copy(alpha=.92f),Offset(w*.40f,h*.45f),Offset(w*.70f,h*.45f),(h*.045f).coerceAtLeast(1f),StrokeCap.Round)
                drawLine(Color(0xFFFF8BC3).copy(alpha=.86f),Offset(w*.42f,h*.56f),Offset(w*.68f,h*.56f),(h*.038f).coerceAtLeast(1f),StrokeCap.Round)
            }
        }

        // face + glossy highlights make the small tiers readable too.
        val eyeX = bodyLeft + bodyW*.79f
        val eyeY = bodyTop + bodyH*.38f
        drawCircle(Color.White,w*.061f,Offset(eyeX,eyeY))
        drawCircle(Color(0xFF173845),w*.029f,Offset(eyeX+w*.010f,eyeY))
        drawCircle(Color.White,w*.010f,Offset(eyeX+w*.018f,eyeY-w*.013f))
        drawLine(dark.copy(alpha=.62f),Offset(bodyLeft+bodyW*.89f,bodyTop+bodyH*.63f),Offset(bodyLeft+bodyW*.97f,bodyTop+bodyH*.60f),(w*.016f).coerceAtLeast(1f),StrokeCap.Round)
        drawCircle(primary.copy(alpha=.18f),w*.026f,Offset(bodyLeft+bodyW*.69f,bodyTop+bodyH*.70f))
        drawOval(Color.White.copy(alpha=.38f),Offset(bodyLeft+bodyW*.18f,bodyTop+bodyH*.12f),Size(bodyW*.36f,bodyH*.12f))
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
