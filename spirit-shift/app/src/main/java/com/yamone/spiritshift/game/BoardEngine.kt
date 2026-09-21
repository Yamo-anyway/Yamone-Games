package com.yamone.spiritshift.game

import kotlin.math.abs
import kotlin.random.Random

const val BOARD_SIZE = 9

enum class ElementKind { FIRE, WATER, NATURE, LIGHTNING, MOON, LIGHT, ICE }

data class Cell(val row: Int, val col: Int) {
    fun isInside(): Boolean = row in 0 until BOARD_SIZE && col in 0 until BOARD_SIZE
}

enum class Gravity(
    val dr: Int,
    val dc: Int,
    val arrow: String,
    val koreanLabel: String,
) {
    DOWN(1, 0, "↓", "아래"),
    DOWN_RIGHT(1, 1, "↘", "오른쪽 아래"),
    RIGHT(0, 1, "→", "오른쪽"),
    UP_RIGHT(-1, 1, "↗", "오른쪽 위"),
    UP(-1, 0, "↑", "위"),
    UP_LEFT(-1, -1, "↖", "왼쪽 위"),
    LEFT(0, -1, "←", "왼쪽"),
    DOWN_LEFT(1, -1, "↙", "왼쪽 아래");

    fun rotateBy(degrees: Int): Gravity {
        require(degrees % 45 == 0)
        val steps = ((degrees / 45) % 8 + 8) % 8
        return entries[(ordinal + steps) % entries.size]
    }
}

data class CorePiece(val id: Long, val kind: ElementKind)
data class PieceMove(val piece: CorePiece, val from: Cell, val to: Cell)
data class PieceSpawn(val piece: CorePiece, val target: Cell, val startRow: Float, val startCol: Float)
data class CompressionResult(val moves: List<PieceMove>, val spawns: List<PieceSpawn>)

class BoardEngine(private val random: Random = Random.Default) {
    val board: Array<Array<CorePiece?>> = Array(BOARD_SIZE) { arrayOfNulls(BOARD_SIZE) }
    private var nextId = 1L

    fun reset() {
        var attempts = 0
        do {
            attempts++
            clear()
            fillInitialWithoutMatches()
        } while ((findMatches().isNotEmpty() || !hasPossibleMove()) && attempts < 500)
        check(hasPossibleMove()) { "Unable to create an initial board with at least one move." }
    }

    fun clear() {
        for (r in 0 until BOARD_SIZE) for (c in 0 until BOARD_SIZE) board[r][c] = null
    }

    fun snapshotPositions(): Map<Long, Cell> {
        val result = LinkedHashMap<Long, Cell>()
        for (r in 0 until BOARD_SIZE) for (c in 0 until BOARD_SIZE) {
            board[r][c]?.let { result[it.id] = Cell(r, c) }
        }
        return result
    }

    fun swap(a: Cell, b: Cell): Boolean {
        if (!a.isInside() || !b.isInside()) return false
        if (abs(a.row - b.row) + abs(a.col - b.col) != 1) return false
        val temp = board[a.row][a.col]
        board[a.row][a.col] = board[b.row][b.col]
        board[b.row][b.col] = temp
        return true
    }

    fun findMatches(): Set<Cell> {
        val matches = LinkedHashSet<Cell>()
        for (r in 0 until BOARD_SIZE) {
            var start = 0
            while (start < BOARD_SIZE) {
                val kind = board[r][start]?.kind
                var end = start + 1
                while (kind != null && end < BOARD_SIZE && board[r][end]?.kind == kind) end++
                if (kind != null && end - start >= 3) for (c in start until end) matches += Cell(r, c)
                start = end
            }
        }
        for (c in 0 until BOARD_SIZE) {
            var start = 0
            while (start < BOARD_SIZE) {
                val kind = board[start][c]?.kind
                var end = start + 1
                while (kind != null && end < BOARD_SIZE && board[end][c]?.kind == kind) end++
                if (kind != null && end - start >= 3) for (r in start until end) matches += Cell(r, c)
                start = end
            }
        }
        return matches
    }

    fun remove(cells: Set<Cell>): List<Pair<Cell, CorePiece>> {
        val removed = ArrayList<Pair<Cell, CorePiece>>(cells.size)
        for (cell in cells) {
            board[cell.row][cell.col]?.let { removed += cell to it }
            board[cell.row][cell.col] = null
        }
        return removed
    }

    fun compressAndFill(gravity: Gravity): CompressionResult {
        val moves = mutableListOf<PieceMove>()
        val spawns = mutableListOf<PieceSpawn>()
        for (line in buildLines(gravity)) {
            val existing = line.mapNotNull { cell -> board[cell.row][cell.col]?.let { cell to it } }
            line.forEach { board[it.row][it.col] = null }
            val emptyCount = line.size - existing.size
            existing.forEachIndexed { index, (from, piece) ->
                val target = line[emptyCount + index]
                board[target.row][target.col] = piece
                if (from != target) moves += PieceMove(piece, from, target)
            }
            repeat(emptyCount) { index ->
                val target = line[index]
                val piece = newPiece()
                board[target.row][target.col] = piece
                val stepsOutside = emptyCount - index
                val source = line.first()
                spawns += PieceSpawn(
                    piece = piece,
                    target = target,
                    startRow = source.row - gravity.dr * stepsOutside.toFloat(),
                    startCol = source.col - gravity.dc * stepsOutside.toFloat(),
                )
            }
        }
        return CompressionResult(moves, spawns)
    }

    fun hasPossibleMove(): Boolean {
        for (r in 0 until BOARD_SIZE) for (c in 0 until BOARD_SIZE) {
            val a = Cell(r, c)
            if (c + 1 < BOARD_SIZE && swapWouldMatch(a, Cell(r, c + 1))) return true
            if (r + 1 < BOARD_SIZE && swapWouldMatch(a, Cell(r + 1, c))) return true
        }
        return false
    }

    private fun swapWouldMatch(a: Cell, b: Cell): Boolean {
        swap(a, b)
        val createsMatch = findMatches().isNotEmpty()
        swap(a, b)
        return createsMatch
    }

    private fun buildLines(gravity: Gravity): List<List<Cell>> {
        val starts = mutableListOf<Cell>()
        for (r in 0 until BOARD_SIZE) for (c in 0 until BOARD_SIZE) {
            val prev = Cell(r - gravity.dr, c - gravity.dc)
            if (!prev.isInside()) starts += Cell(r, c)
        }
        return starts.map { start ->
            buildList {
                var r = start.row
                var c = start.col
                while (Cell(r, c).isInside()) {
                    add(Cell(r, c))
                    r += gravity.dr
                    c += gravity.dc
                }
            }
        }
    }

    private fun fillInitialWithoutMatches() {
        for (r in 0 until BOARD_SIZE) for (c in 0 until BOARD_SIZE) {
            val blocked = mutableSetOf<ElementKind>()
            if (c >= 2) {
                val a = board[r][c - 1]?.kind
                val b = board[r][c - 2]?.kind
                if (a != null && a == b) blocked += a
            }
            if (r >= 2) {
                val a = board[r - 1][c]?.kind
                val b = board[r - 2][c]?.kind
                if (a != null && a == b) blocked += a
            }
            val choices = ElementKind.entries.filterNot { it in blocked }
            board[r][c] = CorePiece(nextId++, choices.random(random))
        }
    }

    private fun newPiece(): CorePiece = CorePiece(nextId++, ElementKind.entries.random(random))
}
