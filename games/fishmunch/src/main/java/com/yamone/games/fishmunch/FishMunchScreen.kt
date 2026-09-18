package com.yamone.games.fishmunch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import com.yamone.games.arcadecore.*
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
    var fish by mutableStateOf<List<FallingFish>>(emptyList())

    private var nextBaseSpawnAt = 0.9f
    private var preparedExtraWindow = 0
    private var extraSpawnTimes = mutableListOf<Float>()
    private var nextExtraSpawnIndex = 0

    fun start() {
        playerX = 0.5f
        score = 0
        elapsed = 0f
        fish = emptyList()
        started = true
        gameOver = false
        serial++
        resetSpawnSchedule()
        spawnOneFish(initial = true)
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

        // v0.3.07: overall speed never stops increasing.
        val timeSpeed = 0.285f * (1f + elapsed / 88f)
        val swaySpeed = 2.25f + elapsed * 0.018f
        var missed = false

        val next = buildList {
            fish.forEach { item ->
                val nextPhase = item.phase + dt * swaySpeed
                val nextY = item.y + timeSpeed * sizeSpeedFactor(item.sizeTier) * item.fallFactor * dt
                val nextX = (item.baseX + sin(nextPhase.toDouble()).toFloat() * item.amplitude)
                    .coerceIn(0.055f, 0.945f)

                val caught = isCaught(
                    x = nextX,
                    y = nextY,
                    sizeTier = item.sizeTier,
                    style = item.style,
                    playerHalfWidth = playerHalfWidth,
                    playerHalfHeight = playerHalfHeight
                )

                when {
                    caught -> {
                        score++
                        GameFeedback.play("collect")
                    }
                    nextY > 1.055f -> missed = true
                    else -> add(item.copy(x = nextX, y = nextY, phase = nextPhase))
                }
            }
        }

        if (missed) {
            gameOver = true
            return
        }
        fish = next
    }

    private fun resetSpawnSchedule() {
        nextBaseSpawnAt = 0.9f
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
            val extraCount = preparedExtraWindow.coerceAtMost(9)
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
        if (fish.size >= MAX_ACTIVE_FISH) return

        serial++
        val random = Random(serial * 137 + elapsed.toInt() * 31)
        val sizeTier = chooseSizeTier(random)
        val style = random.nextInt(FISH_STYLE_COUNT)
        val margin = fishMargin(sizeTier)
        val baseX = margin + random.nextFloat() * (1f - margin * 2f)

        fish = fish + FallingFish(
            id = serial,
            baseX = baseX,
            x = baseX,
            y = if (initial) 0.025f else -0.025f - random.nextFloat() * 0.07f,
            style = style,
            sizeTier = sizeTier,
            phase = random.nextFloat() * 6.28f,
            amplitude = 0.030f + random.nextFloat() * 0.055f,
            fallFactor = 0.92f + random.nextFloat() * 0.16f
        )
    }

    private fun chooseSizeTier(random: Random): Int {
        // Large fish first, all sizes in the middle, then large sizes disappear one by one.
        val range = when {
            elapsed < 18f -> 8..10
            elapsed < 32f -> 7..10
            elapsed < 46f -> 6..10
            elapsed < 60f -> 5..10
            elapsed < 76f -> 4..10
            elapsed < 96f -> 1..10
            elapsed < 112f -> 1..9
            elapsed < 128f -> 1..8
            elapsed < 144f -> 1..7
            elapsed < 160f -> 1..6
            elapsed < 176f -> 1..5
            else -> 1..4
        }
        return random.nextInt(range.first, range.last + 1)
    }

    private fun sizeSpeedFactor(sizeTier: Int): Float {
        val tier = sizeTier.coerceIn(1, 10)
        // size 10 = slowest, size 1 = fastest
        return 1.45f - (tier - 1) * (0.73f / 9f)
    }

    private fun fishMargin(sizeTier: Int): Float =
        0.050f + sizeTier.coerceIn(1, 10) * 0.0068f

    private fun isCaught(
        x: Float,
        y: Float,
        sizeTier: Int,
        style: Int,
        playerHalfWidth: Float,
        playerHalfHeight: Float
    ): Boolean {
        val tier = sizeTier.coerceIn(1, 10)
        val slender = style == 7 || style == 9
        val tall = style == 1 || style == 5
        val fishHalfWidth = (0.017f + tier * 0.0042f) * if (slender) 1.10f else 1f
        val fishHalfHeight = (0.012f + tier * 0.0030f) * if (tall) 1.15f else 1f
        return abs(x - playerX) <= playerHalfWidth + fishHalfWidth &&
            abs(y - PLAYER_Y) <= playerHalfHeight + fishHalfHeight
    }

    companion object {
        const val PLAYER_Y = 0.80f
        private const val MAX_ACTIVE_FISH = 42
        private const val FISH_STYLE_COUNT = 10
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
    val storage = remember { ArcadeRecordStorage(context) }
    val state = remember { FishMunchState() }
    var best by remember { mutableIntStateOf(storage.topRecords(ArcadeGameId.FISH_MUNCH).firstOrNull()?.score ?: 0) }
    var previousBest by remember { mutableIntStateOf(best) }
    var saved by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var exitConfirm by remember { mutableStateOf(false) }

    fun start() {
        previousBest = best
        saved = false
        paused = false
        exitConfirm = false
        GameFeedback.setPaused(false)
        GameFeedback.play("start")
        state.start()
    }

    fun requestExit() {
        if (!state.started || state.gameOver) onBack()
        else {
            exitConfirm = true
            GameFeedback.setPaused(true)
        }
    }

    BackHandler {
        if (paused) {
            paused = false
            GameFeedback.setPaused(false)
        } else requestExit()
    }

    DisposableEffect(Unit) {
        onDispose { GameFeedback.setPaused(false) }
    }

    LaunchedEffect(Unit) {
        var previous = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (previous != 0L && !paused && !exitConfirm && GameFeedback.canAdvance) {
                    state.update((now - previous) / 1_000_000_000f, playerHalfWidth, playerHalfHeight)
                }
                previous = now
            }
        }
    }

    LaunchedEffect(state.gameOver) {
        if (state.gameOver && !saved) {
            storage.addRecord(ArcadeGameId.FISH_MUNCH, state.score, nickname = nickname)
            best = storage.topRecords(ArcadeGameId.FISH_MUNCH).firstOrNull()?.score ?: state.score
            saved = true
            GameFeedback.play(if (state.score > previousBest) "record" else "finish")
            GameFeedback.setPaused(true)
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
                onClick = {
                    GameFeedback.tap()
                    paused = true
                    GameFeedback.setPaused(true)
                },
                enabled = state.started && !state.gameOver
            ) { Text("Ⅱ", fontSize = 25.sp, color = primaryDark) }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FishStatChip(Modifier.weight(1f), "먹은 물고기", "${state.score}마리", primaryDark, ink)
            FishStatChip(Modifier.weight(1f), "최고", "${best}마리", primaryDark, ink)
        }

        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFBCEFF4), Color(0xFF79D2DE), Color(0xFF388DB3))
                    )
                )
                .pointerInput(state.started, state.gameOver, exitConfirm, paused) {
                    if (state.started && !state.gameOver && !exitConfirm && !paused && GameFeedback.canAdvance) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            if (size.width > 0) state.dragBy(dragAmount / size.width.toFloat())
                        }
                    }
                }
        ) {
            ArcadeBackdrop(ScenicWorld.OCEAN, Modifier.matchParentSize())

            state.fish.forEach { item ->
                key(item.id) {
                    val fishSize = fishSizeForTier(item.sizeTier)
                    Box(
                        Modifier.offset(
                            x = maxWidth * item.x - fishSize / 2,
                            y = maxHeight * item.y - fishSize / 2
                        )
                    ) {
                        PrettyFish(item.style, fishSize, primary)
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

            if (!state.started) {
                Surface(
                    modifier = Modifier.align(Alignment.Center).padding(26.dp),
                    shape = RoundedCornerShape(26.dp),
                    color = Color.White.copy(alpha = .97f)
                ) {
                    Column(
                        Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("예쁜 물고기를 냠냠!", fontSize = 19.sp, fontWeight = FontWeight.Black, color = ink)
                        Spacer(Modifier.height(7.dp))
                        Text(
                            "처음엔 크고 느린 물고기부터 시작해요.\n시간이 지날수록 작고 빠른 물고기가 많아져요.",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,
                            color = muted
                        )
                        Spacer(Modifier.height(15.dp))
                        Button(
                            onClick = ::start,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primary),
                            shape = RoundedCornerShape(17.dp)
                        ) { Text("시작하기", fontWeight = FontWeight.ExtraBold) }
                    }
                }
            }

            if (state.gameOver) {
                Surface(
                    modifier = Modifier.align(Alignment.Center).padding(20.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White.copy(alpha = .99f),
                    shadowElevation = 6.dp
                ) {
                    Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        mascotContent(74.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (state.score > previousBest) "새로운 최고기록!" else "즐거운 바다 한 판!",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = ink
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("${state.score}마리", fontSize = 30.sp, fontWeight = FontWeight.Black, color = primaryDark)
                        Text("최고 기록 ${best}마리", fontSize = 11.sp, color = muted)
                        Spacer(Modifier.height(15.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            FilledTonalButton(
                                onClick = ::start,
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) { Text("다시하기", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            FilledTonalButton(
                                onClick = onBack,
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) { Text("그만하기", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }

    if (paused) {
        var options by remember { mutableStateOf(GameFeedback.options) }
        AlertDialog(
            onDismissRequest = {
                paused = false
                GameFeedback.setPaused(false)
            },
            shape = RoundedCornerShape(26.dp),
            title = { Text("잠깐 쉬어가요", fontWeight = FontWeight.Black) },
            text = {
                Column {
                    Text("게임 진행은 멈춰 있어요.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("배경음악", Modifier.weight(1f))
                        Switch(options.music, {
                            options = options.copy(music = it)
                            GameFeedback.update(options)
                        })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("진동", Modifier.weight(1f))
                        Switch(options.vibration, {
                            options = options.copy(vibration = it)
                            GameFeedback.update(options)
                        })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    paused = false
                    GameFeedback.setPaused(false)
                    GameFeedback.tap()
                }) { Text("계속하기") }
            },
            dismissButton = {
                TextButton(onClick = {
                    paused = false
                    exitConfirm = true
                }) { Text("게임 종료") }
            }
        )
    }

    if (exitConfirm) {
        AlertDialog(
            onDismissRequest = {
                exitConfirm = false
                GameFeedback.setPaused(false)
            },
            shape = RoundedCornerShape(24.dp),
            title = { Text("게임을 그만둘까요?", fontWeight = FontWeight.Black, color = ink) },
            confirmButton = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            exitConfirm = false
                            GameFeedback.setPaused(false)
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("계속하기", fontWeight = FontWeight.Bold, color = primaryDark) }
                    Button(
                        onClick = onBack,
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

private fun fishSizeForTier(sizeTier: Int): Dp {
    val tier = sizeTier.coerceIn(1, 10)
    return (30 + (tier - 1) * 6).dp
}

@Composable
private fun PrettyFish(styleRaw: Int, size: Dp, primary: Color) {
    val style = ((styleRaw % 10) + 10) % 10
    val palette = when (style) {
        0 -> Triple(Color(0xFFFF8A62), Color(0xFFFFC56F), Color(0xFFE45A57))
        1 -> Triple(Color(0xFF61C9E8), Color(0xFFC8F6FF), Color(0xFF347FC6))
        2 -> Triple(Color(0xFFFFD954), Color(0xFFFFF2A5), Color(0xFFF29D38))
        3 -> Triple(Color(0xFF69D7B2), Color(0xFFD4FFF0), Color(0xFF268C7D))
        4 -> Triple(Color(0xFFF38AB3), Color(0xFFFFD5E7), Color(0xFFC95B8D))
        5 -> Triple(Color(0xFF9A86E8), Color(0xFFE4DEFF), Color(0xFF6551AF))
        6 -> Triple(Color(0xFFFFA24A), Color(0xFFFFDF8C), Color(0xFFE66B35))
        7 -> Triple(Color(0xFF398FD4), Color(0xFF89E5F7), Color(0xFF24529D))
        8 -> Triple(Color(0xFF8BCB5A), Color(0xFFE5F5A5), Color(0xFF4A8F52))
        else -> Triple(Color(0xFFB477DB), Color(0xFFF1D7FF), Color(0xFF704AA6))
    }

    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val bodyColor = palette.first
        val light = palette.second
        val dark = palette.third

        val bodyWidth = when (style) {
            1, 5 -> w * .54f
            7, 9 -> w * .72f
            6 -> w * .58f
            else -> w * .64f
        }
        val bodyHeight = when (style) {
            1 -> h * .70f
            5 -> h * .64f
            6 -> h * .66f
            7, 9 -> h * .40f
            else -> h * .52f
        }
        val bodyLeft = w * .25f
        val bodyTop = (h - bodyHeight) / 2f
        val bodyCenter = Offset(bodyLeft + bodyWidth * .52f, h * .50f)

        drawOval(
            Color.Black.copy(alpha = .13f),
            Offset(bodyLeft + w * .025f, bodyTop + h * .040f),
            Size(bodyWidth, bodyHeight)
        )

        val tail = when (style) {
            3 -> Path().apply {
                moveTo(w*.28f,h*.50f)
                lineTo(w*.01f,h*.18f)
                lineTo(w*.12f,h*.50f)
                lineTo(w*.01f,h*.82f)
                close()
            }
            6 -> Path().apply {
                moveTo(w*.30f,h*.50f)
                lineTo(w*.03f,h*.25f)
                lineTo(w*.11f,h*.50f)
                lineTo(w*.03f,h*.75f)
                close()
            }
            7,9 -> Path().apply {
                moveTo(w*.28f,h*.50f)
                lineTo(w*.04f,h*.31f)
                lineTo(w*.04f,h*.69f)
                close()
            }
            else -> Path().apply {
                moveTo(w*.29f,h*.50f)
                lineTo(w*.04f,h*.24f)
                lineTo(w*.04f,h*.76f)
                close()
            }
        }
        drawPath(tail, Brush.linearGradient(listOf(light, bodyColor, dark), Offset.Zero, Offset(w*.35f,h)))
        drawPath(tail, dark.copy(alpha=.50f), style=Stroke((w*.022f).coerceAtLeast(1f)))

        if (style == 1 || style == 5 || style == 9) {
            val dorsal = Path().apply {
                moveTo(w*.43f,bodyTop+h*.03f)
                lineTo(w*.57f,bodyTop-h*(if(style==1) .17f else .10f))
                lineTo(w*.70f,bodyTop+h*.05f)
                close()
            }
            drawPath(dorsal, Brush.linearGradient(listOf(light,bodyColor)))
        }
        if (style == 1 || style == 5) {
            val ventral = Path().apply {
                moveTo(w*.46f,bodyTop+bodyHeight-h*.03f)
                lineTo(w*.58f,bodyTop+bodyHeight+h*.14f)
                lineTo(w*.69f,bodyTop+bodyHeight-h*.04f)
                close()
            }
            drawPath(ventral, bodyColor.copy(alpha=.92f))
        }

        drawOval(
            Brush.linearGradient(
                listOf(light, bodyColor, dark),
                Offset(bodyLeft, bodyTop),
                Offset(bodyLeft + bodyWidth, bodyTop + bodyHeight)
            ),
            Offset(bodyLeft, bodyTop),
            Size(bodyWidth, bodyHeight)
        )
        drawOval(
            dark.copy(alpha=.42f),
            Offset(bodyLeft, bodyTop),
            Size(bodyWidth, bodyHeight),
            style=Stroke((w*.023f).coerceAtLeast(1f))
        )

        when(style) {
            0 -> {
                repeat(2) { i ->
                    val x=w*(.45f+i*.17f)
                    drawLine(Color.White.copy(alpha=.88f),Offset(x,bodyTop+bodyHeight*.12f),Offset(x-w*.035f,bodyTop+bodyHeight*.88f),(w*.055f).coerceAtLeast(2f),StrokeCap.Round)
                }
            }
            1 -> {
                drawOval(Color.White.copy(alpha=.35f),Offset(w*.40f,bodyTop+bodyHeight*.16f),Size(w*.24f,bodyHeight*.38f))
                drawLine(dark.copy(alpha=.55f),Offset(w*.56f,bodyTop+bodyHeight*.07f),Offset(w*.49f,bodyTop+bodyHeight*.90f),(w*.027f).coerceAtLeast(1f),StrokeCap.Round)
            }
            2 -> {
                drawLine(Color(0xFF4D4D46).copy(alpha=.70f),Offset(w*.65f,bodyTop+bodyHeight*.10f),Offset(w*.61f,bodyTop+bodyHeight*.90f),(w*.040f).coerceAtLeast(2f),StrokeCap.Round)
                drawCircle(light.copy(alpha=.65f),w*.045f,Offset(w*.49f,h*.47f))
            }
            3 -> {
                repeat(4) { i ->
                    drawCircle(light.copy(alpha=.72f),w*.027f,Offset(w*(.42f+(i%2)*.16f),bodyTop+bodyHeight*(.30f+(i/2)*.35f)))
                }
            }
            4 -> {
                repeat(3) { i ->
                    drawCircle(if(i==1) dark.copy(alpha=.50f) else light.copy(alpha=.72f),w*(.035f+i*.004f),Offset(w*(.44f+i*.10f),h*(.42f+(i%2)*.16f)))
                }
            }
            5 -> {
                drawLine(light.copy(alpha=.85f),Offset(w*.40f,h*.50f),Offset(w*.70f,h*.50f),(h*.075f).coerceAtLeast(2f),StrokeCap.Round)
                drawCircle(dark.copy(alpha=.40f),w*.045f,Offset(w*.53f,h*.40f))
            }
            6 -> {
                drawOval(light.copy(alpha=.58f),Offset(w*.41f,bodyTop+bodyHeight*.18f),Size(w*.26f,bodyHeight*.34f))
                drawCircle(Color.White.copy(alpha=.50f),w*.035f,Offset(w*.48f,h*.62f))
            }
            7 -> {
                drawLine(Color(0xFFBDFBFF).copy(alpha=.95f),Offset(w*.38f,h*.46f),Offset(w*.70f,h*.46f),(h*.07f).coerceAtLeast(2f),StrokeCap.Round)
                drawLine(Color(0xFF174E8E).copy(alpha=.65f),Offset(w*.42f,h*.62f),Offset(w*.67f,h*.62f),(h*.035f).coerceAtLeast(1f),StrokeCap.Round)
            }
            8 -> {
                repeat(3) { i ->
                    val x=w*(.43f+i*.11f)
                    drawLine(light.copy(alpha=.78f),Offset(x,bodyTop+bodyHeight*.17f),Offset(x-w*.025f,bodyTop+bodyHeight*.83f),(w*.025f).coerceAtLeast(1f),StrokeCap.Round)
                }
            }
            9 -> {
                drawOval(light.copy(alpha=.48f),Offset(w*.40f,bodyTop+bodyHeight*.15f),Size(w*.25f,bodyHeight*.28f))
                drawLine(Color.White.copy(alpha=.62f),Offset(w*.45f,h*.55f),Offset(w*.67f,h*.55f),(h*.045f).coerceAtLeast(1f),StrokeCap.Round)
            }
        }

        if(style==6) {
            // goldfish double tail sparkle
            drawCircle(light.copy(alpha=.72f),w*.025f,Offset(w*.18f,h*.32f))
            drawCircle(light.copy(alpha=.55f),w*.018f,Offset(w*.14f,h*.68f))
        }
        if(style==8) {
            // puffer-like tiny soft spikes
            listOf(-.8f,-.4f,0f,.4f,.8f).forEach { t ->
                val x=bodyCenter.x + bodyWidth*.42f*t
                val top=bodyTop + bodyHeight*(.08f+.16f*abs(t))
                drawLine(dark.copy(alpha=.45f),Offset(x,top),Offset(x,top-h*.05f),(w*.012f).coerceAtLeast(1f),StrokeCap.Round)
            }
        }

        val eyeX = bodyLeft + bodyWidth * .78f
        val eyeY = bodyTop + bodyHeight * .38f
        drawCircle(Color.White, w*.060f, Offset(eyeX,eyeY))
        drawCircle(Color(0xFF173845), w*.029f, Offset(eyeX+w*.010f,eyeY))
        drawCircle(Color.White, w*.010f, Offset(eyeX+w*.017f,eyeY-w*.012f))

        drawLine(
            dark.copy(alpha=.65f),
            Offset(bodyLeft+bodyWidth*.88f,bodyTop+bodyHeight*.62f),
            Offset(bodyLeft+bodyWidth*.96f,bodyTop+bodyHeight*.60f),
            (w*.016f).coerceAtLeast(1f),
            StrokeCap.Round
        )
        drawCircle(primary.copy(alpha=.20f),w*.026f,Offset(bodyLeft+bodyWidth*.69f,bodyTop+bodyHeight*.69f))
        drawOval(Color.White.copy(alpha=.36f),Offset(bodyLeft+bodyWidth*.20f,bodyTop+bodyHeight*.13f),Size(bodyWidth*.34f,bodyHeight*.12f))
    }
}

@Composable
private fun FishStatChip(modifier: Modifier, label: String, value: String, dark: Color, ink: Color) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 1.dp) {
        Column(Modifier.padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Black, color = dark)
            Text(label, fontSize = 10.sp, color = ink.copy(alpha = .58f))
        }
    }
}
