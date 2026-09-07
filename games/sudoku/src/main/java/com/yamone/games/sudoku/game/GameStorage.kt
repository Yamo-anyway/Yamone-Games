package com.yamone.games.sudoku.game

import android.content.Context

data class StoredGame(
    val puzzle: IntArray,
    val solution: IntArray,
    val values: IntArray,
    val notes: IntArray,
    val difficulty: SudokuDifficulty,
    val elapsedSeconds: Int,
    val mistakes: Int,
    val completed: Boolean
)

class GameStorage(context: Context) {
    private val prefs = context.getSharedPreferences("yamone_sudoku_game", Context.MODE_PRIVATE)

    fun save(game: StoredGame) {
        prefs.edit()
            .putString("puzzle", game.puzzle.joinToString(","))
            .putString("solution", game.solution.joinToString(","))
            .putString("values", game.values.joinToString(","))
            .putString("notes", game.notes.joinToString(","))
            .putString("difficulty", game.difficulty.name)
            .putInt("elapsed", game.elapsedSeconds)
            .putInt("mistakes", game.mistakes)
            .putBoolean("completed", game.completed)
            .apply()
    }

    fun load(): StoredGame? {
        val puzzle = prefs.getString("puzzle", null)?.ints() ?: return null
        val solution = prefs.getString("solution", null)?.ints() ?: return null
        val values = prefs.getString("values", null)?.ints() ?: return null
        val notes = prefs.getString("notes", null)?.ints() ?: return null
        if (listOf(puzzle, solution, values, notes).any { it.size != 81 }) return null

        val difficulty = runCatching {
            SudokuDifficulty.valueOf(prefs.getString("difficulty", SudokuDifficulty.NORMAL.name)!!)
        }.getOrDefault(SudokuDifficulty.NORMAL)

        return StoredGame(
            puzzle = puzzle,
            solution = solution,
            values = values,
            notes = notes,
            difficulty = difficulty,
            elapsedSeconds = prefs.getInt("elapsed", 0).coerceAtLeast(0),
            mistakes = prefs.getInt("mistakes", 0).coerceAtLeast(0),
            completed = prefs.getBoolean("completed", false)
        )
    }

    private fun String.ints(): IntArray? = runCatching {
        split(',').map(String::toInt).toIntArray()
    }.getOrNull()
}
