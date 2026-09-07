package com.yamone.games.sudoku.game

import kotlin.random.Random

enum class SudokuDifficulty(val label: String, val targetClues: Int) {
    EASY("쉬움", 42),
    NORMAL("보통", 36),
    HARD("어려움", 30),
    CHALLENGE("도전", 25)
}

data class SudokuPuzzle(
    val puzzle: IntArray,
    val solution: IntArray,
    val difficulty: SudokuDifficulty
)

object SudokuEngine {
    fun generate(difficulty: SudokuDifficulty, seed: Int = Random.nextInt()): SudokuPuzzle {
        val random = Random(seed)
        val solution = solvedBoard(random)
        val puzzle = solution.copyOf()
        val order = (0 until 81).shuffled(random)
        var clues = 81

        for (index in order) {
            if (clues <= difficulty.targetClues) break
            val old = puzzle[index]
            puzzle[index] = 0
            if (countSolutions(puzzle, 2) != 1) puzzle[index] = old else clues--
        }
        return SudokuPuzzle(puzzle, solution, difficulty)
    }

    fun countSolutions(board: IntArray, limit: Int = 2): Int = count(board.copyOf(), limit)

    private fun solvedBoard(random: Random): IntArray {
        val base = IntArray(81) { i ->
            val r = i / 9
            val c = i % 9
            ((r * 3 + r / 3 + c) % 9) + 1
        }
        val digits = (1..9).shuffled(random)
        for (i in base.indices) base[i] = digits[base[i] - 1]

        fun order(): List<Int> = (0..2).shuffled(random).flatMap { g ->
            (0..2).shuffled(random).map { g * 3 + it }
        }
        val rows = order()
        val cols = order()
        return IntArray(81) { i -> base[rows[i / 9] * 9 + cols[i % 9]] }
    }

    private fun count(board: IntArray, limit: Int): Int {
        var best = -1
        var choices: IntArray? = null
        for (i in board.indices) {
            if (board[i] != 0) continue
            val c = candidates(board, i)
            if (c.isEmpty()) return 0
            if (choices == null || c.size < choices.size) {
                best = i
                choices = c
                if (c.size == 1) break
            }
        }
        if (best == -1) return 1

        var total = 0
        for (v in choices!!) {
            board[best] = v
            total += count(board, limit - total)
            board[best] = 0
            if (total >= limit) return total
        }
        return total
    }

    private fun candidates(board: IntArray, index: Int): IntArray {
        val used = BooleanArray(10)
        val row = index / 9
        val col = index % 9
        for (i in 0..8) {
            used[board[row * 9 + i]] = true
            used[board[i * 9 + col]] = true
        }
        val br = row / 3 * 3
        val bc = col / 3 * 3
        for (r in br until br + 3) for (c in bc until bc + 3) used[board[r * 9 + c]] = true
        return (1..9).filter { !used[it] }.toIntArray()
    }
}
