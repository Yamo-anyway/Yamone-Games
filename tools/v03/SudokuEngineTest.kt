package com.yamone.games.sudoku.game

import org.junit.Assert.*
import org.junit.Test

class SudokuEngineTest {
    private val digits = (1..9).toSet()
    @Test fun everyDifficultyProducesAValidUniquePuzzle() {
        for (difficulty in SudokuDifficulty.entries) for (seed in listOf(7, 42, 2026)) {
            val result = SudokuEngine.generate(difficulty, seed)
            assertEquals(difficulty, result.difficulty)
            assertEquals(81, result.solution.size)
            assertEquals(81, result.puzzle.size)
            for (row in 0..8) assertEquals(digits, (0..8).map { result.solution[row * 9 + it] }.toSet())
            for (col in 0..8) assertEquals(digits, (0..8).map { result.solution[it * 9 + col] }.toSet())
            for (boxRow in 0..2) for (boxCol in 0..2) {
                val cells = (0..8).map { n -> result.solution[(boxRow * 3 + n / 3) * 9 + boxCol * 3 + n % 3] }
                assertEquals(digits, cells.toSet())
            }
            result.puzzle.forEachIndexed { index, value ->
                assertTrue(value in 0..9)
                if (value != 0) assertEquals(result.solution[index], value)
            }
            assertEquals("Unique solution for $difficulty, $seed", 1, SudokuEngine.countSolutions(result.puzzle))
        }
    }
    @Test fun fixedSeedIsReproducible() {
        for (difficulty in SudokuDifficulty.entries) {
            val a = SudokuEngine.generate(difficulty, 73)
            val b = SudokuEngine.generate(difficulty, 73)
            assertArrayEquals(a.puzzle, b.puzzle)
            assertArrayEquals(a.solution, b.solution)
        }
    }
    @Test fun countingSolutionsDoesNotMutateThePuzzle() {
        val puzzle = SudokuEngine.generate(SudokuDifficulty.NORMAL, 21).puzzle
        val original = puzzle.copyOf()
        SudokuEngine.countSolutions(puzzle)
        assertArrayEquals(original, puzzle)
    }
}
