package com.yamone.games.sudoku.game

/** Visual guidance only: never changes entries, notes, score, difficulty or candidate rules. */
enum class SudokuCellHint { NONE, SELECTED, SAME, BLOCKED, AVAILABLE, OCCUPIED, PEER }

object SudokuGuidance {
    fun shareUnit(a: Int, b: Int): Boolean {
        if (a !in 0..80 || b !in 0..80) return false
        val ar = a / 9; val ac = a % 9; val br = b / 9; val bc = b % 9
        return ar == br || ac == bc || (ar / 3 == br / 3 && ac / 3 == bc / 3)
    }

    fun hints(values: IntArray, selected: Int, number: Int): List<SudokuCellHint> {
        require(values.size == 81) { "A Sudoku board must have 81 cells" }
        val anchors = if (number in 1..9) values.indices.filter { values[it] == number } else emptyList()
        return values.indices.map { index ->
            when {
                index == selected -> SudokuCellHint.SELECTED
                number !in 1..9 -> if (shareUnit(index, selected)) SudokuCellHint.PEER else SudokuCellHint.NONE
                values[index] == number -> SudokuCellHint.SAME
                values[index] != 0 -> SudokuCellHint.OCCUPIED
                anchors.any { shareUnit(index, it) } -> SudokuCellHint.BLOCKED
                else -> SudokuCellHint.AVAILABLE
            }
        }
    }
}
