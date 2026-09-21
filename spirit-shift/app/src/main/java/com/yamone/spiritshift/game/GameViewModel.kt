package com.yamone.spiritshift.game

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class GamePhase { IDLE, PLAYING, GAME_OVER }

data class UiPiece(
    val id: Long,
    val kind: ElementKind,
    val row: Int,
    val col: Int,
    val startRow: Float = row.toFloat(),
    val startCol: Float = col.toFloat(),
    val removing: Boolean = false,
    val moveDurationMs: Int = 180,
)

data class GameUiState(
    val gameId: Long = 0L,
    val pieces: List<UiPiece> = emptyList(),
    val selectedId: Long? = null,
    val score: Int = 0,
    val elapsedMs: Long = 0L,
    val gravity: Gravity = Gravity.DOWN,
    val lastRotationDegrees: Int? = null,
    val rotationEvent: Int = 0,
    val busy: Boolean = false,
    val paused: Boolean = false,
    val phase: GamePhase = GamePhase.IDLE,
    val noMove: Boolean = false,
) {
    val difficultyLevel: Int get() = ((elapsedMs / 60_000L).toInt() + 1).coerceIn(1, 10)
    val isMaxDifficulty: Boolean get() = elapsedMs >= 10 * 60_000L
}

class GameViewModel : ViewModel() {
    private val random = Random.Default
    private val engine = BoardEngine(random)
    private val display = LinkedHashMap<Long, UiPiece>()
    private val _ui = MutableStateFlow(GameUiState())
    val ui: StateFlow<GameUiState> = _ui.asStateFlow()

    private var timerJob: Job? = null
    private var actionJob: Job? = null
    private var successfulMovesSinceRotation = 0
    private var nextRotationAt = Int.MAX_VALUE

    fun startNewGame() {
        actionJob?.cancel()
        engine.reset()
        display.clear()
        engine.snapshotPositions().forEach { (id, cell) ->
            val core = engine.board[cell.row][cell.col]!!
            display[id] = UiPiece(id, core.kind, cell.row, cell.col)
        }
        successfulMovesSinceRotation = 0
        nextRotationAt = Int.MAX_VALUE
        _ui.value = GameUiState(
            gameId = _ui.value.gameId + 1L,
            pieces = display.values.toList(),
            gravity = Gravity.DOWN,
            phase = GamePhase.PLAYING,
        )
        startTimer()
    }

    fun setPaused(paused: Boolean) {
        val state = _ui.value
        if (state.phase != GamePhase.PLAYING || state.paused == paused) return
        _ui.value = state.copy(paused = paused)
    }

    fun onPieceTap(pieceId: Long) {
        val state = _ui.value
        if (state.phase != GamePhase.PLAYING || state.paused || state.busy) return
        val piece = display[pieceId] ?: return
        val selected = state.selectedId
        if (selected == null) {
            _ui.value = state.copy(selectedId = pieceId)
            return
        }
        if (selected == pieceId) {
            _ui.value = state.copy(selectedId = null)
            return
        }
        val other = display[selected]
        if (other != null && kotlin.math.abs(other.row - piece.row) + kotlin.math.abs(other.col - piece.col) == 1) {
            attemptSwap(Cell(other.row, other.col), Cell(piece.row, piece.col))
        } else {
            _ui.value = state.copy(selectedId = pieceId)
        }
    }

    fun onPieceSwipe(pieceId: Long, dRow: Int, dCol: Int) {
        val state = _ui.value
        if (state.phase != GamePhase.PLAYING || state.paused || state.busy) return
        val piece = display[pieceId] ?: return
        val target = Cell(piece.row + dRow, piece.col + dCol)
        if (!target.isInside()) return
        attemptSwap(Cell(piece.row, piece.col), target)
    }

    private fun attemptSwap(a: Cell, b: Cell) {
        if (_ui.value.busy) return
        val pieceA = engine.board[a.row][a.col] ?: return
        val pieceB = engine.board[b.row][b.col] ?: return
        actionJob = viewModelScope.launch {
            updateState(busy = true, selectedId = null)
            engine.swap(a, b)
            moveExisting(pieceA.id, b, 160)
            moveExisting(pieceB.id, a, 160)
            publishPieces()
            delay(165)
            awaitIfPaused()

            var matches = engine.findMatches()
            if (matches.isEmpty()) {
                engine.swap(a, b)
                moveExisting(pieceA.id, a, 160)
                moveExisting(pieceB.id, b, 160)
                publishPieces()
                delay(165)
                awaitIfPaused()
                updateState(busy = false)
                return@launch
            }

            successfulMovesSinceRotation++
            resolveCascades(matches)
            if (_ui.value.phase == GamePhase.PLAYING) updateState(busy = false)
        }
    }

    private suspend fun resolveCascades(initial: Set<Cell>) {
        var matches = initial
        var chain = 1
        var firstPass = true
        while (matches.isNotEmpty()) {
            val ids = matches.mapNotNull { engine.board[it.row][it.col]?.id }.toSet()
            ids.forEach { id -> display[id]?.let { display[id] = it.copy(removing = true) } }
            publishPieces()
            delay(185)
            awaitIfPaused()

            engine.remove(matches)
            ids.forEach(display::remove)
            val gained = matches.size * 10 * chain
            _ui.value = _ui.value.copy(score = _ui.value.score + gained, pieces = display.values.toList())

            if (firstPass) maybeRotateGravity()

            val compression = engine.compressAndFill(_ui.value.gravity)
            compression.moves.forEach { move -> moveExisting(move.piece.id, move.to, 270) }
            publishPieces()
            if (compression.moves.isNotEmpty()) {
                delay(275)
                awaitIfPaused()
            }

            compression.spawns.forEach { spawn ->
                display[spawn.piece.id] = UiPiece(
                    id = spawn.piece.id,
                    kind = spawn.piece.kind,
                    row = spawn.target.row,
                    col = spawn.target.col,
                    startRow = spawn.startRow,
                    startCol = spawn.startCol,
                    moveDurationMs = 285,
                )
            }
            publishPieces()
            if (compression.spawns.isNotEmpty()) {
                delay(290)
                awaitIfPaused()
            }

            matches = engine.findMatches()
            chain++
            firstPass = false
            if (matches.isNotEmpty()) {
                delay(70)
                awaitIfPaused()
            }
        }

        if (!engine.hasPossibleMove()) {
            _ui.value = _ui.value.copy(
                pieces = display.values.toList(),
                selectedId = null,
                busy = false,
                paused = false,
                phase = GamePhase.GAME_OVER,
                noMove = true,
            )
        }
    }

    private suspend fun maybeRotateGravity() {
        val angles = unlockedRotationAngles(_ui.value.elapsedMs)
        if (angles.isEmpty()) return
        if (nextRotationAt == Int.MAX_VALUE) nextRotationAt = nextRotationThreshold(_ui.value.elapsedMs)
        if (successfulMovesSinceRotation < nextRotationAt) return

        val angle = angles.random(random)
        val newGravity = _ui.value.gravity.rotateBy(angle)
        successfulMovesSinceRotation = 0
        nextRotationAt = nextRotationThreshold(_ui.value.elapsedMs)
        _ui.value = _ui.value.copy(
            gravity = newGravity,
            lastRotationDegrees = angle,
            rotationEvent = _ui.value.rotationEvent + 1,
        )
        delay(230)
        awaitIfPaused()
    }

    private suspend fun awaitIfPaused() {
        while (_ui.value.phase == GamePhase.PLAYING && _ui.value.paused) delay(50)
    }

    private fun unlockedRotationAngles(elapsedMs: Long): List<Int> = when {
        elapsedMs < 2 * 60_000L -> emptyList()
        elapsedMs < 3 * 60_000L -> listOf(90)
        elapsedMs < 4 * 60_000L -> listOf(90, 180)
        elapsedMs < 5 * 60_000L -> listOf(90, 180, 270)
        elapsedMs < 6 * 60_000L -> listOf(90, 180, 270, 45)
        elapsedMs < 7 * 60_000L -> listOf(90, 180, 270, 45, 135)
        elapsedMs < 8 * 60_000L -> listOf(90, 180, 270, 45, 135, 225)
        else -> listOf(90, 180, 270, 45, 135, 225, 315)
    }

    private fun nextRotationThreshold(elapsedMs: Long): Int {
        val range = when {
            elapsedMs < 2 * 60_000L -> return Int.MAX_VALUE
            elapsedMs < 3 * 60_000L -> 7..10
            elapsedMs < 4 * 60_000L -> 6..9
            elapsedMs < 5 * 60_000L -> 5..8
            elapsedMs < 6 * 60_000L -> 4..7
            elapsedMs < 7 * 60_000L -> 4..6
            elapsedMs < 8 * 60_000L -> 3..6
            elapsedMs < 9 * 60_000L -> 3..5
            elapsedMs < 10 * 60_000L -> 2..5
            else -> 2..4
        }
        return random.nextInt(range.first, range.last + 1)
    }

    private fun moveExisting(id: Long, target: Cell, durationMs: Int) {
        display[id]?.let { piece ->
            display[id] = piece.copy(row = target.row, col = target.col, removing = false, moveDurationMs = durationMs)
        }
    }

    private fun publishPieces() {
        _ui.value = _ui.value.copy(pieces = display.values.toList())
    }

    private fun updateState(busy: Boolean = _ui.value.busy, selectedId: Long? = _ui.value.selectedId) {
        _ui.value = _ui.value.copy(busy = busy, selectedId = selectedId, pieces = display.values.toList())
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var previous = SystemClock.elapsedRealtime()
            while (true) {
                delay(100)
                val now = SystemClock.elapsedRealtime()
                val delta = now - previous
                previous = now
                val state = _ui.value
                if (state.phase == GamePhase.PLAYING && !state.paused) {
                    _ui.value = state.copy(elapsedMs = state.elapsedMs + delta)
                }
            }
        }
    }
}
