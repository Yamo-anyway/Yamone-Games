package com.yamone.games.winterride

import android.content.Context
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

enum class WinterRideMode(val title: String, val shortRule: String) {
    SKI("스키", "빨강은 왼쪽 · 파랑은 오른쪽 바깥"),
    SNOWBOARD("스노보드", "낮은 쪽 바깥으로 통과"),
    TREE_RUN("트리런", "갈라지는 나무 사이 길을 선택")
}

private enum class GateSide { RED, BLUE }

private data class RideGate(
    val id: Int,
    val side: GateSide,
    val x: Float,
    val y: Float,
    val halfWidth: Float
)

private data class TreeRow(
    val id: Int,
    val y: Float,
    val treeXs: List<Float>,
    val gapXs: List<Float>
)

private class WinterRideState {
    var mode by mutableStateOf(WinterRideMode.SKI)
    var playerX by mutableFloatStateOf(0.5f)
    var elapsed by mutableFloatStateOf(0f)
    var speedKmh by mutableFloatStateOf(38f)
    var distanceMeters by mutableFloatStateOf(0f)
    var passed by mutableIntStateOf(0)
    var combo by mutableIntStateOf(0)
    var started by mutableStateOf(false)
    var paused by mutableStateOf(false)
    var gameOver by mutableStateOf(false)
    var sceneryTravel by mutableFloatStateOf(0f)
    var gates by mutableStateOf<List<RideGate>>(emptyList())
    var treeRows by mutableStateOf<List<TreeRow>>(emptyList())

    private var nextSpawnIn = 0f
    private var serial = 0
    private var lastGateSide = GateSide.BLUE

    fun start(newMode: WinterRideMode) {
        mode = newMode
        playerX = 0.5f
        elapsed = 0f
        speedKmh = if (newMode == WinterRideMode.TREE_RUN) 34f else 38f
        distanceMeters = 0f
        passed = 0
        combo = 0
        started = true
        paused = false
        gameOver = false
        sceneryTravel = 0f
        gates = emptyList()
        treeRows = emptyList()
        nextSpawnIn = 0f
        serial = 0
        lastGateSide = GateSide.BLUE

        if (newMode == WinterRideMode.TREE_RUN) {
            repeat(6) { index -> spawnTreeRow(initialY = -0.04f + index * 0.17f) }
        } else {
            repeat(4) { index -> spawnGate(initialY = -0.05f + index * 0.22f) }
        }
    }

    fun dragBy(deltaNormalized: Float) {
        if (!started || paused || gameOver) return
        playerX = (playerX + deltaNormalized * 1.10f).coerceIn(0.055f, 0.945f)
    }

    fun togglePause() {
        if (started && !gameOver) paused = !paused
    }

    fun update(dtRaw: Float) {
        if (!started || paused || gameOver) return
        val dt = dtRaw.coerceIn(0f, 0.033f)
        elapsed += dt
        val startSpeed = if (mode == WinterRideMode.TREE_RUN) 34f else 38f
        val cap = if (mode == WinterRideMode.TREE_RUN) 92f else 112f
        speedKmh = (startSpeed + elapsed * 1.18f + passed * 0.42f).coerceAtMost(cap)
        distanceMeters += (speedKmh / 3.6f) * dt
        sceneryTravel += dt * (0.42f + speedKmh / 115f)

        if (mode == WinterRideMode.TREE_RUN) updateTreeRows(dt) else updateGates(dt)
    }

    private fun updateGates(dt: Float) {
        val worldSpeed = (0.20f + speedKmh / 190f).coerceAtMost(0.82f)
        nextSpawnIn -= dt
        if (nextSpawnIn <= 0f) {
            spawnGate()
            nextSpawnIn = (1.18f - elapsed * 0.010f).coerceAtLeast(0.56f)
        }

        val next = mutableListOf<RideGate>()
        for (gate in gates) {
            val moved = gate.copy(y = gate.y + worldSpeed * dt)
            if (gate.y < PLAYER_Y && moved.y >= PLAYER_Y) {
                val margin = if (mode == WinterRideMode.SNOWBOARD) 0.020f else 0.015f
                val correct = when (moved.side) {
                    GateSide.RED -> playerX < moved.x - moved.halfWidth - margin
                    GateSide.BLUE -> playerX > moved.x + moved.halfWidth + margin
                }
                if (!correct) {
                    gameOver = true
                    combo = 0
                    return
                }
                passed++
                combo++
            }
            if (moved.y <= 1.12f) next += moved
        }
        gates = next
    }

    private fun spawnGate(initialY: Float? = null) {
        serial++
        val random = Random(17_071 + serial * 379)
        val side = if (serial <= 5 || elapsed < 24f || random.nextFloat() > 0.24f) {
            if (lastGateSide == GateSide.BLUE) GateSide.RED else GateSide.BLUE
        } else {
            lastGateSide
        }
        lastGateSide = side

        val widen = min(0.17f, elapsed * 0.0032f)
        val baseOffset = 0.105f + widen
        val jitter = (random.nextFloat() - 0.5f) * 0.055f
        val x = when (side) {
            GateSide.RED -> (0.50f + baseOffset + jitter).coerceIn(0.55f, 0.80f)
            GateSide.BLUE -> (0.50f - baseOffset + jitter).coerceIn(0.20f, 0.45f)
        }
        val halfWidth = if (mode == WinterRideMode.SNOWBOARD) 0.072f else 0.062f
        gates = gates + RideGate(
            id = serial,
            side = side,
            x = x,
            y = initialY ?: -0.09f,
            halfWidth = halfWidth
        )
    }

    private fun updateTreeRows(dt: Float) {
        val worldSpeed = (0.19f + speedKmh / 205f).coerceAtMost(0.72f)
        nextSpawnIn -= dt
        if (nextSpawnIn <= 0f) {
            spawnTreeRow()
            nextSpawnIn = (0.86f - elapsed * 0.0065f).coerceAtLeast(0.44f)
        }

        val next = mutableListOf<TreeRow>()
        for (row in treeRows) {
            val moved = row.copy(y = row.y + worldSpeed * dt)
            if (moved.y in (PLAYER_Y - 0.055f)..(PLAYER_Y + 0.055f)) {
                val hit = moved.treeXs.any { treeX -> abs(treeX - playerX) < 0.060f }
                if (hit) {
                    gameOver = true
                    combo = 0
                    return
                }
            }
            if (row.y < PLAYER_Y && moved.y >= PLAYER_Y) {
                passed++
                combo++
            }
            if (moved.y <= 1.12f) next += moved
        }
        treeRows = next
    }

    private fun spawnTreeRow(initialY: Float? = null) {
        serial++
        val random = Random(51_337 + serial * 911)
        val block = (serial / 5) % 4
        val branchSeed = (serial / 5) % 2
        val gaps = when (block) {
            0 -> listOf(0.50f)
            1 -> listOf(0.31f, 0.69f)
            2 -> if (branchSeed == 0) listOf(0.31f) else listOf(0.69f)
            else -> listOf(0.50f)
        }

        val candidates = (0..10).map { 0.07f + it * 0.086f }
        val trees = candidates.mapNotNull { baseX ->
            val jittered = (baseX + (random.nextFloat() - 0.5f) * 0.024f).coerceIn(0.04f, 0.96f)
            if (gaps.any { abs(it - jittered) < 0.125f }) null else jittered
        }
        treeRows = treeRows + TreeRow(
            id = serial,
            y = initialY ?: -0.08f,
            treeXs = trees,
            gapXs = gaps
        )
    }

    fun guideTargets(): List<Pair<Float, Float>> {
        return if (mode == WinterRideMode.TREE_RUN) {
            treeRows
                .filter { it.y < PLAYER_Y - 0.04f }
                .sortedByDescending { it.y }
                .take(3)
                .flatMap { row -> row.gapXs.map { gap -> gap to row.y } }
        } else {
            gates
                .filter { it.y < PLAYER_Y - 0.03f }
                .sortedByDescending { it.y }
                .take(3)
                .map { gate ->
                    val target = when (gate.side) {
                        GateSide.RED -> gate.x - gate.halfWidth - 0.065f
                        GateSide.BLUE -> gate.x + gate.halfWidth + 0.065f
                    }.coerceIn(0.07f, 0.93f)
                    target to gate.y
                }
        }
    }

    companion object {
        const val PLAYER_Y = 0.79f
    }
}

private class WinterRideRecordStorage(context: Context) {
    private val prefs = context.getSharedPreferences("yamone_winter_ride", Context.MODE_PRIVATE)

    fun best(mode: WinterRideMode): Int = prefs.getInt("best_${mode.name}", 0)

    fun saveBest(mode: WinterRideMode, meters: Int): Int {
        val old = best(mode)
        if (meters > old) prefs.edit().putInt("best_${mode.name}", meters).apply()
        return maxOf(old, meters)
    }
}

@Composable
fun WinterRideScreen(
    onBack: () -> Unit,
    primary: Color,
    primaryDark: Color,
    soft: Color,
    ink: Color,
    muted: Color,
    mascotContent: @Composable (Dp) -> Unit
) {
    val context = LocalContext.current.applicationContext
    val storage = remember { WinterRideRecordStorage(context) }
    val state = remember { WinterRideState() }
    var selectedMode by remember { mutableStateOf<WinterRideMode?>(null) }
    var bests by remember {
        mutableStateOf(WinterRideMode.entries.associateWith { storage.best(it) })
    }
    var savedThisRun by remember { mutableStateOf(false) }

    LaunchedEffect(selectedMode) {
        var previous = 0L
        while (isActive && selectedMode != null) {
            withFrameNanos { now ->
                if (previous != 0L) {
                    state.update((now - previous) / 1_000_000_000f)
                }
                previous = now
            }
        }
    }

    LaunchedEffect(state.gameOver) {
        if (state.gameOver && !savedThisRun) {
            val meters = state.distanceMeters.toInt()
            val newBest = storage.saveBest(state.mode, meters)
            bests = bests + (state.mode to newBest)
            savedThisRun = true
        }
    }

    if (selectedMode == null) {
        WinterRideModeSelect(
            primary = primary,
            primaryDark = primaryDark,
            soft = soft,
            ink = ink,
            muted = muted,
            bests = bests,
            mascotContent = mascotContent,
            onBack = onBack,
            onSelect = { mode ->
                savedThisRun = false
                selectedMode = mode
                state.start(mode)
            }
        )
        return
    }

    WinterRideGame(
        state = state,
        primary = primary,
        primaryDark = primaryDark,
        soft = soft,
        ink = ink,
        muted = muted,
        mascotContent = mascotContent,
        onBack = {
            selectedMode = null
            state.started = false
        },
        onRetry = {
            savedThisRun = false
            state.start(state.mode)
        }
    )
}

@Composable
private fun WinterRideModeSelect(
    primary: Color,
    primaryDark: Color,
    soft: Color,
    ink: Color,
    muted: Color,
    bests: Map<WinterRideMode, Int>,
    mascotContent: @Composable (Dp) -> Unit,
    onBack: () -> Unit,
    onSelect: (WinterRideMode) -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFFDDF3FF), Color(0xFFF9FDFF), Color.White))
        ).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 13.dp)) {
                Text("‹", fontSize = 26.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("스키 · 스노보드 · 트리런", fontSize = 24.sp, fontWeight = FontWeight.Black, color = primaryDark)
                Text("화면을 좌우로 밀면서 설원을 달려요", fontSize = 12.sp, color = muted)
            }
            HelmetMascot(mode = WinterRideMode.SKI, primary = primary, mascotContent = mascotContent, size = 58.dp)
        }

        Surface(shape = RoundedCornerShape(24.dp), color = Color.White.copy(alpha = .92f), shadowElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ModeCard(
                    title = "스키 모드",
                    subtitle = "빨간 기문은 왼쪽 바깥 · 파란 기문은 오른쪽 바깥",
                    best = bests[WinterRideMode.SKI] ?: 0,
                    color = Color(0xFF65B7FF),
                    symbol = "⛷",
                    onClick = { onSelect(WinterRideMode.SKI) }
                )
                ModeCard(
                    title = "스노보드 모드",
                    subtitle = "삼각 기문의 낮은 쪽 바깥으로 통과",
                    best = bests[WinterRideMode.SNOWBOARD] ?: 0,
                    color = Color(0xFFFF8DB2),
                    symbol = "◆",
                    onClick = { onSelect(WinterRideMode.SNOWBOARD) }
                )
                ModeCard(
                    title = "트리런 모드",
                    subtitle = "길이 갈라지고 만나고, 선택한 길이 막히기도 해요",
                    best = bests[WinterRideMode.TREE_RUN] ?: 0,
                    color = Color(0xFF58D3B4),
                    symbol = "▲",
                    onClick = { onSelect(WinterRideMode.TREE_RUN) }
                )
            }
        }

        Surface(shape = RoundedCornerShape(22.dp), color = soft.copy(alpha = .75f)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("플레이 방법", fontSize = 18.sp, fontWeight = FontWeight.Black, color = ink)
                Text("• 이동 버튼은 없어요. 화면을 좌우로 드래그하면 캐릭터가 부드럽게 따라가요.", fontSize = 12.sp, color = muted)
                Text("• 속도는 계속 올라가고 기문 좌우 폭은 커지며 앞뒤 간격은 점점 좁아져요.", fontSize = 12.sp, color = muted)
                Text("• 점선은 다음 통과 위치를 미리 보여주는 가상 라인이에요.", fontSize = 12.sp, color = muted)
                Text("• 모든 캐릭터는 헬멧을 착용해요.", fontSize = 12.sp, color = muted)
            }
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    best: Int,
    color: Color,
    symbol: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 82.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = .22f), contentColor = Color(0xFF24343A)),
        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 12.dp)
    ) {
        Surface(shape = RoundedCornerShape(15.dp), color = Color.White.copy(alpha = .85f)) {
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                Text(symbol, fontSize = 23.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text(subtitle, fontSize = 11.sp, textAlign = TextAlign.Start)
            Text("최고 ${best}m", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text("›", fontSize = 30.sp)
    }
}

@Composable
private fun WinterRideGame(
    state: WinterRideState,
    primary: Color,
    primaryDark: Color,
    soft: Color,
    ink: Color,
    muted: Color,
    mascotContent: @Composable (Dp) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit
) {
    val modeColor = when (state.mode) {
        WinterRideMode.SKI -> Color(0xFF58AFFF)
        WinterRideMode.SNOWBOARD -> Color(0xFFFF79A6)
        WinterRideMode.TREE_RUN -> Color(0xFF39C6A0)
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color(0xFFE9F7FF))
    ) {
        val gameHeight = maxHeight
        val gameWidth = maxWidth

        Box(
            Modifier.fillMaxSize().pointerInput(state.started, state.paused, state.gameOver) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    if (size.width > 0) state.dragBy(dragAmount / size.width.toFloat())
                }
            }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawWinterBackground(state.sceneryTravel, state.speedKmh)
                drawGuideLine(state, modeColor)
                if (state.mode == WinterRideMode.TREE_RUN) {
                    state.treeRows.sortedBy { it.y }.forEach { drawTreeRow(it) }
                } else {
                    state.gates.sortedBy { it.y }.forEach { drawRideGate(it, state.mode) }
                }
                drawSnowSpeedLines(state.sceneryTravel, state.speedKmh)
            }

            PlayerRider(
                modifier = Modifier.offset(
                    x = gameWidth * state.playerX - 39.dp,
                    y = gameHeight * WinterRideState.PLAYER_Y - 49.dp
                ),
                mode = state.mode,
                primary = modeColor,
                mascotContent = mascotContent
            )

            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = .92f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "‹",
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 5.dp),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = primaryDark
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(19.dp), color = modeColor.copy(alpha = .93f)) {
                        Text(
                            "${state.mode.title} 모드",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { state.togglePause() },
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = .92f), contentColor = primaryDark),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(if (state.paused) "▶" else "Ⅱ", fontWeight = FontWeight.Black)
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    HudCard(Modifier.weight(1f), "속도", "${state.speedKmh.toInt()} km/h")
                    HudCard(Modifier.weight(1f), if (state.mode == WinterRideMode.TREE_RUN) "통과" else "기문", "${state.passed}")
                    HudCard(Modifier.weight(1f), "거리", "${state.distanceMeters.toInt()} m")
                }
            }

            Box(
                Modifier.align(Alignment.TopStart).padding(top = 10.dp, start = 10.dp).size(48.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { _, _ -> }
                    }
            )

            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 13.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = .88f)
            ) {
                Text(
                    when (state.mode) {
                        WinterRideMode.SKI -> "화면을 밀어 빨강은 왼쪽 · 파랑은 오른쪽 바깥으로!"
                        WinterRideMode.SNOWBOARD -> "화면을 밀어 삼각 기문의 낮은 쪽 바깥으로!"
                        WinterRideMode.TREE_RUN -> "화면을 밀어 갈라지는 나무 사이 길을 선택하세요!"
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ink,
                    textAlign = TextAlign.Center
                )
            }

            if (state.paused && !state.gameOver) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = RoundedCornerShape(26.dp),
                    color = Color.White.copy(alpha = .95f),
                    shadowElevation = 5.dp
                ) {
                    Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("잠시 쉬는 중", fontSize = 22.sp, fontWeight = FontWeight.Black, color = ink)
                        Spacer(Modifier.height(8.dp))
                        Text("위의 ▶ 버튼을 누르면 다시 달려요.", fontSize = 12.sp, color = muted)
                    }
                }
            }

            if (state.gameOver) {
                Surface(
                    modifier = Modifier.align(Alignment.Center).padding(25.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White.copy(alpha = .97f),
                    shadowElevation = 7.dp
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        HelmetMascot(mode = state.mode, primary = modeColor, mascotContent = mascotContent, size = 72.dp)
                        Text(
                            if (state.mode == WinterRideMode.TREE_RUN) "앗, 나무를 만났어요!" else "앗, 기문을 놓쳤어요!",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = ink
                        )
                        Text("${state.distanceMeters.toInt()}m · 통과 ${state.passed}", fontSize = 15.sp, color = primaryDark, fontWeight = FontWeight.Bold)
                        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                            Text("다시 달리기", fontWeight = FontWeight.Black)
                        }
                        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                            Text("모드 선택으로", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Box(
                Modifier.align(Alignment.TopStart).padding(10.dp).height(46.dp).width(50.dp)
                    .pointerInput(onBack) {
                        detectHorizontalDragGestures(
                            onDragStart = { onBack() },
                            onHorizontalDrag = { _, _ -> }
                        )
                    }
            )
        }
    }
}

@Composable
private fun HudCard(modifier: Modifier, label: String, value: String) {
    Surface(modifier = modifier, shape = RoundedCornerShape(15.dp), color = Color(0xCC164A78)) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = .85f))
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
    }
}

@Composable
private fun PlayerRider(
    modifier: Modifier,
    mode: WinterRideMode,
    primary: Color,
    mascotContent: @Composable (Dp) -> Unit
) {
    Box(modifier.size(78.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val boardColor = if (mode == WinterRideMode.SNOWBOARD) Color(0xFFFF6F9F) else Color(0xFF43A4F4)
            if (mode == WinterRideMode.SNOWBOARD) {
                rotate(-13f, pivot = Offset(size.width * .5f, size.height * .76f)) {
                    drawRoundRect(
                        boardColor,
                        topLeft = Offset(size.width * .12f, size.height * .72f),
                        size = Size(size.width * .76f, size.height * .13f),
                        cornerRadius = CornerRadius(size.height * .07f)
                    )
                    drawLine(Color.White, Offset(size.width * .28f, size.height * .785f), Offset(size.width * .72f, size.height * .785f), strokeWidth = 2.5f)
                }
            } else {
                drawRoundRect(
                    boardColor,
                    topLeft = Offset(size.width * .29f, size.height * .66f),
                    size = Size(size.width * .12f, size.height * .29f),
                    cornerRadius = CornerRadius(size.width * .05f)
                )
                drawRoundRect(
                    boardColor,
                    topLeft = Offset(size.width * .59f, size.height * .66f),
                    size = Size(size.width * .12f, size.height * .29f),
                    cornerRadius = CornerRadius(size.width * .05f)
                )
            }
        }
        mascotContent(64.dp)
        Canvas(Modifier.fillMaxSize()) {
            drawArc(
                color = primary,
                startAngle = 195f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(size.width * .24f, size.height * .08f),
                size = Size(size.width * .52f, size.height * .42f),
                style = Stroke(width = 5f, cap = StrokeCap.Round)
            )
            drawCircle(primary, radius = size.width * .055f, center = Offset(size.width * .23f, size.height * .30f))
            drawCircle(primary, radius = size.width * .055f, center = Offset(size.width * .77f, size.height * .30f))
        }
    }
}

@Composable
private fun HelmetMascot(
    mode: WinterRideMode,
    primary: Color,
    mascotContent: @Composable (Dp) -> Unit,
    size: Dp
) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        mascotContent(size * .84f)
        Canvas(Modifier.fillMaxSize()) {
            drawArc(
                primary,
                startAngle = 195f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(this.size.width * .22f, this.size.height * .05f),
                size = Size(this.size.width * .56f, this.size.height * .46f),
                style = Stroke(width = 5f, cap = StrokeCap.Round)
            )
        }
    }
}

private fun DrawScope.drawWinterBackground(travel: Float, speed: Float) {
    drawRect(
        Brush.verticalGradient(
            colors = listOf(Color(0xFF89D0FF), Color(0xFFE8F7FF), Color(0xFFF8FCFF)),
            startY = 0f,
            endY = size.height
        )
    )

    val mountain = Path().apply {
        moveTo(0f, size.height * .30f)
        lineTo(size.width * .18f, size.height * .14f)
        lineTo(size.width * .31f, size.height * .28f)
        lineTo(size.width * .48f, size.height * .10f)
        lineTo(size.width * .65f, size.height * .27f)
        lineTo(size.width * .80f, size.height * .15f)
        lineTo(size.width, size.height * .30f)
        lineTo(size.width, size.height * .42f)
        lineTo(0f, size.height * .42f)
        close()
    }
    drawPath(mountain, Color(0xFFD5EEFA))

    repeat(22) { index ->
        val base = (index * 0.071f + travel * (0.045f + speed / 3800f)) % 1.18f
        val y = (base - 0.10f) * size.height
        val left = index % 2 == 0
        val edge = if (left) 0.025f + (index % 4) * .028f else 0.975f - (index % 4) * .028f
        val perspective = (0.40f + (y / size.height).coerceIn(0f, 1f) * 1.05f)
        drawPine(edge * size.width, y, 19f * perspective)
    }
}

private fun DrawScope.drawPine(x: Float, y: Float, scale: Float) {
    drawRect(
        Color(0xFF795A45),
        topLeft = Offset(x - scale * .10f, y),
        size = Size(scale * .20f, scale * .72f)
    )
    val green = Color(0xFF2E8F75)
    repeat(3) { level ->
        val topY = y - scale * (0.88f - level * .25f)
        val half = scale * (0.62f - level * .11f)
        val p = Path().apply {
            moveTo(x, topY)
            lineTo(x - half, y - scale * (0.20f + level * .06f))
            lineTo(x + half, y - scale * (0.20f + level * .06f))
            close()
        }
        drawPath(p, green)
    }
    drawCircle(Color.White.copy(alpha = .85f), radius = scale * .18f, center = Offset(x, y - scale * .60f))
}

private fun DrawScope.drawSnowSpeedLines(travel: Float, speed: Float) {
    val alpha = ((speed - 35f) / 90f).coerceIn(.10f, .48f)
    repeat(14) { index ->
        val x = ((index * 79 + 31) % 97) / 100f * size.width
        val base = ((index * .113f + travel * .31f) % 1.10f) * size.height
        val len = size.height * (.018f + speed / 4200f)
        drawLine(
            Color.White.copy(alpha = alpha),
            Offset(x, base),
            Offset(x + (x - size.width / 2f) * .018f, base + len),
            strokeWidth = 2.2f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawGuideLine(state: WinterRideState, color: Color) {
    val targets = state.guideTargets()
    if (targets.isEmpty()) return
    val path = Path().apply {
        moveTo(state.playerX * size.width, WinterRideState.PLAYER_Y * size.height)
        targets.forEach { (x, y) -> lineTo(x * size.width, y * size.height) }
    }
    drawPath(
        path,
        color.copy(alpha = .78f),
        style = Stroke(
            width = 5f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f))
        )
    )
}

private fun DrawScope.drawRideGate(gate: RideGate, mode: WinterRideMode) {
    val scale = (0.48f + gate.y.coerceIn(0f, 1f) * 0.86f)
    val cx = gate.x * size.width
    val cy = gate.y * size.height
    val half = gate.halfWidth * size.width * scale
    val color = if (gate.side == GateSide.RED) Color(0xFFE94B57) else Color(0xFF2678D7)
    val pole = Color(0xFF2F4050)

    if (mode == WinterRideMode.SKI) {
        val h = 70f * scale
        drawLine(pole, Offset(cx - half, cy), Offset(cx - half, cy - h), strokeWidth = 6f * scale, cap = StrokeCap.Round)
        drawLine(pole, Offset(cx + half, cy), Offset(cx + half, cy - h), strokeWidth = 6f * scale, cap = StrokeCap.Round)
        drawRect(
            color,
            topLeft = Offset(cx - half, cy - h * .88f),
            size = Size(half * 2f, h * .46f)
        )
        drawCircle(Color.White.copy(alpha = .9f), 5f * scale, Offset(cx, cy - h * .65f))
    } else {
        val highH = 78f * scale
        val lowH = 25f * scale
        val lowX: Float
        val highX: Float
        if (gate.side == GateSide.RED) {
            lowX = cx - half
            highX = cx + half
        } else {
            highX = cx - half
            lowX = cx + half
        }
        drawLine(pole, Offset(highX, cy), Offset(highX, cy - highH), strokeWidth = 6f * scale, cap = StrokeCap.Round)
        drawLine(pole, Offset(lowX, cy), Offset(lowX, cy - lowH), strokeWidth = 5f * scale, cap = StrokeCap.Round)
        val triangle = Path().apply {
            moveTo(highX, cy - highH * .92f)
            lineTo(highX, cy - highH * .35f)
            lineTo(lowX, cy - lowH)
            close()
        }
        drawPath(triangle, color)
        drawCircle(Color.White.copy(alpha = .9f), 5f * scale, Offset(highX, cy - highH * .60f))
    }
}

private fun DrawScope.drawTreeRow(row: TreeRow) {
    val scale = 0.52f + row.y.coerceIn(0f, 1f) * .95f
    val cy = row.y * size.height
    row.treeXs.forEach { x -> drawPine(x * size.width, cy, 21f * scale) }
}
