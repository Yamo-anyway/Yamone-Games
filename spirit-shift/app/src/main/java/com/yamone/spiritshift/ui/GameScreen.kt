package com.yamone.spiritshift.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yamone.spiritshift.game.BOARD_SIZE
import com.yamone.spiritshift.game.GamePhase
import com.yamone.spiritshift.game.GameUiState
import com.yamone.spiritshift.game.UiPiece
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun GameScreen(
    state: GameUiState,
    bestScore: Int,
    nickname: String,
    onTapPiece: (Long) -> Unit,
    onSwipePiece: (Long, Int, Int) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onHome: () -> Unit,
    onReplay: () -> Unit,
    onBackground: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) onBackground()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = state.phase == GamePhase.PLAYING) {
        if (state.paused) onHome() else onPause()
    }

    var rotationVisible by remember { mutableStateOf(false) }
    LaunchedEffect(state.rotationEvent) {
        if (state.rotationEvent > 0) {
            rotationVisible = true
            delay(480)
            rotationVisible = false
        }
    }

    Column(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HudCard("점수", formatNumber(state.score), Modifier.weight(1f))
            HudCard("시간", formatTime(state.elapsedMs), Modifier.weight(1f))
            IconButton(
                onClick = onPause,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(Icons.Default.Pause, "일시정지")
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.gravity.arrow, fontSize = 27.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("현재 중력", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                    Text(state.gravity.koreanLabel, fontWeight = FontWeight.Bold)
                }
                Text(
                    if (state.isMaxDifficulty) "MAX" else "Lv." + state.difficultyLevel,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PuzzleBoard(
                state = state,
                onTapPiece = onTapPiece,
                onSwipePiece = onSwipePiece,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            )
            if (rotationVisible) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xEA101A2B)),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(
                        Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("회전 " + (state.lastRotationDegrees ?: 0) + "°", fontWeight = FontWeight.Bold)
                        Text(state.gravity.arrow + " " + state.gravity.koreanLabel, fontSize = 27.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("최고 점수", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                Text(formatNumber(bestScore), fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("플레이어", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                Text(nickname.ifBlank { "PLAYER" }, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (state.paused && state.phase == GamePhase.PLAYING) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("일시정지") },
            text = { Text("게임 시간과 입력이 멈춰 있어요.") },
            confirmButton = {
                Button(onClick = onResume) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("계속하기")
                }
            },
            dismissButton = {
                TextButton(onClick = onHome) {
                    Icon(Icons.Default.Home, null)
                    Spacer(Modifier.width(4.dp))
                    Text("홈")
                }
            }
        )
    }

    if (state.phase == GamePhase.GAME_OVER) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("NO MOVE") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("움직일 수 있는 조합이 없어 게임이 끝났어요.")
                    Text(formatNumber(state.score), fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text("플레이 " + formatTime(state.elapsedMs))
                }
            },
            confirmButton = {
                Button(onClick = onReplay) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(6.dp))
                    Text("다시 하기")
                }
            },
            dismissButton = { TextButton(onClick = onHome) { Text("홈") } }
        )
    }
}

@Composable
private fun HudCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 8.dp)) {
            Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
            Text(value, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun PuzzleBoard(
    state: GameUiState,
    onTapPiece: (Long) -> Unit,
    onSwipePiece: (Long, Int, Int) -> Unit,
    modifier: Modifier
) {
    BoxWithConstraints(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF07101E))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp)).clipToBounds()
    ) {
        val cell = maxWidth / BOARD_SIZE
        Canvas(Modifier.fillMaxSize()) {
            val px = size.width / BOARD_SIZE
            for (r in 0 until BOARD_SIZE) for (c in 0 until BOARD_SIZE) {
                val shade = if ((r + c) % 2 == 0) Color(0xFF0D1B2D) else Color(0xFF101F33)
                drawRect(shade, androidx.compose.ui.geometry.Offset(c * px, r * px), androidx.compose.ui.geometry.Size(px, px))
                drawRect(
                    Color.White.copy(alpha = 0.05f),
                    androidx.compose.ui.geometry.Offset(c * px, r * px),
                    androidx.compose.ui.geometry.Size(px, px),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
                )
            }
        }
        state.pieces.forEach { piece ->
            AnimatedPiece(
                piece = piece,
                cell = cell,
                selected = state.selectedId == piece.id,
                enabled = state.phase == GamePhase.PLAYING && !state.paused && !state.busy,
                onTap = { onTapPiece(piece.id) },
                onSwipe = { dr, dc -> onSwipePiece(piece.id, dr, dc) }
            )
        }
    }
}

@Composable
private fun AnimatedPiece(
    piece: UiPiece,
    cell: Dp,
    selected: Boolean,
    enabled: Boolean,
    onTap: () -> Unit,
    onSwipe: (Int, Int) -> Unit
) {
    val row = remember(piece.id) { Animatable(piece.startRow) }
    val col = remember(piece.id) { Animatable(piece.startCol) }
    val scale = remember(piece.id) { Animatable(1f) }
    val alpha = remember(piece.id) { Animatable(1f) }
    val visual = piece.kind.visual()

    LaunchedEffect(piece.row, piece.col, piece.moveDurationMs) {
        coroutineScope {
            launch { row.animateTo(piece.row.toFloat(), tween(piece.moveDurationMs, easing = FastOutSlowInEasing)) }
            launch { col.animateTo(piece.col.toFloat(), tween(piece.moveDurationMs, easing = FastOutSlowInEasing)) }
        }
    }

    LaunchedEffect(piece.removing) {
        if (piece.removing) coroutineScope {
            launch {
                scale.animateTo(1.12f, tween(65))
                scale.animateTo(0.05f, tween(115, easing = FastOutSlowInEasing))
            }
            launch {
                delay(45)
                alpha.animateTo(0f, tween(135))
            }
        }
    }

    Box(
        Modifier
            .offset(x = (col.value * cell.value).dp, y = (row.value * cell.value).dp)
            .size(cell).padding(2.dp)
            .graphicsLayer {
                scaleX = scale.value * if (selected) 1.06f else 1f
                scaleY = scale.value * if (selected) 1.06f else 1f
            }
            .alpha(alpha.value)
            .clip(RoundedCornerShape(11.dp))
            .background(visual.accent.copy(alpha = 0.18f))
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) Color.White else visual.accent.copy(alpha = 0.72f),
                RoundedCornerShape(11.dp)
            )
            .pieceInput(enabled, onTap, onSwipe),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(visual.drawable),
            contentDescription = visual.label,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(3.dp)
        )
    }
}

private fun Modifier.pieceInput(
    enabled: Boolean,
    onTap: () -> Unit,
    onSwipe: (Int, Int) -> Unit
): Modifier {
    if (!enabled) return this
    return pointerInput(enabled) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val start = down.position
            var last = start
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                last = change.position
                change.consume()
                if (!change.pressed) break
            }
            val dx = last.x - start.x
            val dy = last.y - start.y
            val threshold = 18.dp.toPx()
            if (abs(dx) < threshold && abs(dy) < threshold) onTap()
            else if (abs(dx) >= abs(dy)) onSwipe(0, if (dx > 0) 1 else -1)
            else onSwipe(if (dy > 0) 1 else -1, 0)
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000L
    return "%02d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

fun formatNumber(value: Int): String = "%,d".format(value)
