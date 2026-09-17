package com.yamone.games.sudoku.game

import org.junit.Assert.*
import org.junit.Test

class SudokuGuidanceTest {
    private fun board() = IntArray(81).also { it[0] = 5; it[40] = 5; it[71] = 5; it[20] = 7 }
    @Test fun normalSelectionShowsAllSameNumbers() {
        val h = SudokuGuidance.hints(board(), 0, 5)
        assertEquals(SudokuCellHint.SELECTED, h[0])
        assertEquals(SudokuCellHint.SAME, h[40])
        assertEquals(SudokuCellHint.SAME, h[71])
    }
    @Test fun rowAndColumnOfEveryOccurrenceAreBlocked() {
        val h = SudokuGuidance.hints(board(), 0, 5)
        for (cell in listOf(1, 9, 36, 4, 63, 8)) assertEquals("Cell $cell", SudokuCellHint.BLOCKED, h[cell])
    }
    @Test fun otherOccurrencesThreeByThreeBoxesAreAlsoBlocked() {
        val h = SudokuGuidance.hints(board(), 0, 5)
        assertEquals(SudokuCellHint.BLOCKED, h[30])
        assertEquals(SudokuCellHint.BLOCKED, h[60])
        assertEquals(SudokuCellHint.BLOCKED, h[10])
    }
    @Test fun legalBlanksStayBrightAndOccupiedCellsAreNotCandidates() {
        val h = SudokuGuidance.hints(board(), 0, 5)
        assertEquals(SudokuCellHint.AVAILABLE, h[24])
        assertEquals(SudokuCellHint.OCCUPIED, h[20])
    }
    @Test fun fixedInputWorksWithoutSelectedCell() {
        val h = SudokuGuidance.hints(board(), -1, 5)
        assertEquals(SudokuCellHint.SAME, h[0])
        assertEquals(SudokuCellHint.BLOCKED, h[30])
        assertEquals(SudokuCellHint.AVAILABLE, h[24])
    }
    @Test fun emptySelectionShowsOnlyItsUnits() {
        val h = SudokuGuidance.hints(IntArray(81), 0, 0)
        assertEquals(SudokuCellHint.SELECTED, h[0])
        assertEquals(SudokuCellHint.PEER, h[10])
        assertEquals(SudokuCellHint.NONE, h[40])
    }
    @Test fun guideUsesOnlyVisibleEntriesAndDoesNotModifyThem() {
        val values = board(); val copy = values.copyOf()
        SudokuGuidance.hints(values, 40, 5)
        assertArrayEquals(copy, values)
        assertEquals(SudokuCellHint.SAME, SudokuGuidance.hints(values, 0, 5)[40])
    }
    @Test fun clearingAnAnchorRemovesItsRestrictions() {
        val values = IntArray(81).also { it[40] = 5 }
        assertEquals(SudokuCellHint.BLOCKED, SudokuGuidance.hints(values, -1, 5)[30])
        values[40] = 0
        assertEquals(SudokuCellHint.AVAILABLE, SudokuGuidance.hints(values, -1, 5)[30])
    }
    @Test(expected = IllegalArgumentException::class) fun malformedBoardIsRejected() {
        SudokuGuidance.hints(IntArray(80), 0, 1)
    }
}
