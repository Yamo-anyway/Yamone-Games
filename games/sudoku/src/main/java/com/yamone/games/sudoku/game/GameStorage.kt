package com.yamone.games.sudoku.game

import android.content.Context
import java.time.LocalDate

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

data class DifficultyStat(
    val difficulty: SudokuDifficulty,
    val completed: Int,
    val bestSeconds: Int?
)

data class SudokuStats(
    val totalCompleted: Int,
    val currentStreak: Int,
    val totalMistakes: Int,
    val recentDifficulty: SudokuDifficulty?,
    val recentElapsedSeconds: Int,
    val recentMistakes: Int,
    val difficultyStats: List<DifficultyStat>
)

class GameStorage(context: Context) {
    private val prefs = context.getSharedPreferences("yamone_sudoku_game", Context.MODE_PRIVATE)

    fun save(game: StoredGame) {
        val prefix = prefix(game.difficulty)
        prefs.edit()
            .putString("${prefix}_puzzle", game.puzzle.joinToString(","))
            .putString("${prefix}_solution", game.solution.joinToString(","))
            .putString("${prefix}_values", game.values.joinToString(","))
            .putString("${prefix}_notes", game.notes.joinToString(","))
            .putInt("${prefix}_elapsed", game.elapsedSeconds)
            .putInt("${prefix}_mistakes", game.mistakes)
            .putBoolean("${prefix}_completed", game.completed)
            .putString("last_difficulty", game.difficulty.name)
            .apply()
    }

    fun load(difficulty: SudokuDifficulty): StoredGame? {
        val prefix = prefix(difficulty)
        val puzzle = prefs.getString("${prefix}_puzzle", null)?.ints() ?: return loadLegacy(difficulty)
        val solution = prefs.getString("${prefix}_solution", null)?.ints() ?: return null
        val values = prefs.getString("${prefix}_values", null)?.ints() ?: return null
        val notes = prefs.getString("${prefix}_notes", null)?.ints() ?: return null
        if (listOf(puzzle, solution, values, notes).any { it.size != 81 }) return null

        return StoredGame(
            puzzle = puzzle,
            solution = solution,
            values = values,
            notes = notes,
            difficulty = difficulty,
            elapsedSeconds = prefs.getInt("${prefix}_elapsed", 0).coerceAtLeast(0),
            mistakes = prefs.getInt("${prefix}_mistakes", 0).coerceAtLeast(0),
            completed = prefs.getBoolean("${prefix}_completed", false)
        )
    }

    fun loadLast(): StoredGame? {
        val savedName = prefs.getString("last_difficulty", null)
            ?: prefs.getString("difficulty", null)
            ?: SudokuDifficulty.NORMAL.name
        val difficulty = runCatching { SudokuDifficulty.valueOf(savedName) }
            .getOrDefault(SudokuDifficulty.NORMAL)
        return load(difficulty)?.takeUnless { it.completed }
    }

    fun hasSaved(difficulty: SudokuDifficulty): Boolean = load(difficulty)?.completed == false

    fun delete(difficulty: SudokuDifficulty) {
        val prefix = prefix(difficulty)
        prefs.edit()
            .remove("${prefix}_puzzle")
            .remove("${prefix}_solution")
            .remove("${prefix}_values")
            .remove("${prefix}_notes")
            .remove("${prefix}_elapsed")
            .remove("${prefix}_mistakes")
            .remove("${prefix}_completed")
            .apply()
    }

    fun recordCompletion(game: StoredGame) {
        val today = LocalDate.now().toEpochDay()
        val previousDay = prefs.getLong("last_completed_day", Long.MIN_VALUE)
        val oldStreak = prefs.getInt("current_streak", 0)
        val newStreak = when {
            previousDay == today -> oldStreak.coerceAtLeast(1)
            previousDay == today - 1 -> oldStreak + 1
            else -> 1
        }

        val level = game.difficulty.name.lowercase()
        val oldBest = prefs.getInt("best_$level", 0)
        val newBest = if (oldBest == 0 || game.elapsedSeconds < oldBest) game.elapsedSeconds else oldBest

        prefs.edit()
            .putInt("total_completed", prefs.getInt("total_completed", 0) + 1)
            .putInt("total_mistakes", prefs.getInt("total_mistakes", 0) + game.mistakes)
            .putInt("completed_$level", prefs.getInt("completed_$level", 0) + 1)
            .putInt("best_$level", newBest)
            .putLong("last_completed_day", today)
            .putInt("current_streak", newStreak)
            .putString("recent_difficulty", game.difficulty.name)
            .putInt("recent_elapsed", game.elapsedSeconds)
            .putInt("recent_mistakes", game.mistakes)
            .apply()
    }

    fun stats(): SudokuStats {
        val recentDifficulty = prefs.getString("recent_difficulty", null)?.let {
            runCatching { SudokuDifficulty.valueOf(it) }.getOrNull()
        }
        return SudokuStats(
            totalCompleted = prefs.getInt("total_completed", 0),
            currentStreak = prefs.getInt("current_streak", 0),
            totalMistakes = prefs.getInt("total_mistakes", 0),
            recentDifficulty = recentDifficulty,
            recentElapsedSeconds = prefs.getInt("recent_elapsed", 0),
            recentMistakes = prefs.getInt("recent_mistakes", 0),
            difficultyStats = SudokuDifficulty.entries.map { difficulty ->
                val level = difficulty.name.lowercase()
                DifficultyStat(
                    difficulty = difficulty,
                    completed = prefs.getInt("completed_$level", 0),
                    bestSeconds = prefs.getInt("best_$level", 0).takeIf { it > 0 }
                )
            }
        )
    }

    private fun loadLegacy(difficulty: SudokuDifficulty): StoredGame? {
        val legacyDifficulty = runCatching {
            SudokuDifficulty.valueOf(prefs.getString("difficulty", SudokuDifficulty.NORMAL.name)!!)
        }.getOrDefault(SudokuDifficulty.NORMAL)
        if (legacyDifficulty != difficulty) return null

        val puzzle = prefs.getString("puzzle", null)?.ints() ?: return null
        val solution = prefs.getString("solution", null)?.ints() ?: return null
        val values = prefs.getString("values", null)?.ints() ?: return null
        val notes = prefs.getString("notes", null)?.ints() ?: return null
        if (listOf(puzzle, solution, values, notes).any { it.size != 81 }) return null

        val game = StoredGame(
            puzzle = puzzle,
            solution = solution,
            values = values,
            notes = notes,
            difficulty = difficulty,
            elapsedSeconds = prefs.getInt("elapsed", 0).coerceAtLeast(0),
            mistakes = prefs.getInt("mistakes", 0).coerceAtLeast(0),
            completed = prefs.getBoolean("completed", false)
        )
        if (!game.completed) save(game)
        prefs.edit()
            .remove("puzzle").remove("solution").remove("values").remove("notes")
            .remove("difficulty").remove("elapsed").remove("mistakes").remove("completed")
            .apply()
        return game.takeUnless { it.completed }
    }

    private fun prefix(difficulty: SudokuDifficulty) = "slot_${difficulty.name.lowercase()}"

    private fun String.ints(): IntArray? = runCatching {
        split(',').map(String::toInt).toIntArray()
    }.getOrNull()
}
