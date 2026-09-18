package com.yamone.games

import com.yamone.games.arcadecore.ArcadeGameId
import org.junit.Assert.*
import org.junit.Test

class RankingBoardTest {
    @Test fun onlyBestHigherArcadeScoreWins() {
        for(b in listOf(RankingBoard.ICE,RankingBoard.FISH,RankingBoard.SNOW)) {
            assertTrue(b.better(31,30));assertFalse(b.better(29,30));assertFalse(b.better(30,30))
        }
    }
    @Test fun sudokuUsesMinimumInEveryDifficulty() {
        val boards=RankingBoard.entries.filter {it.minimumWins};assertEquals(4,boards.size)
        for(b in boards) {assertTrue(b.better(300,321));assertFalse(b.better(322,321));assertFalse(b.better(321,321))}
    }
    @Test fun firstRecordAcceptedInBothDirections() {
        for(b in RankingBoard.entries) assertTrue(b.better(100,-1))
    }
    @Test fun retiredTimeAttackIsNotUploaded() {
        assertNull(RankingBoard.forGame(ArcadeGameId.FISH_MUNCH_TIME_ATTACK))
        assertEquals(7,RankingBoard.entries.size)
    }
    @Test fun scoreProtocolsNeverMixUnits() {
        assertEquals("height_cm",RankingBoard.ICE.modeId);assertEquals("centimeters",RankingBoard.ICE.unit)
        assertEquals("shards_ms",RankingBoard.SNOW.modeId);assertEquals("milliseconds",RankingBoard.SNOW.unit)
        assertEquals("12.34m",RankingBoard.ICE.format(1234));assertEquals("5:21",RankingBoard.SUDOKU_NORMAL.format(321))
    }
}
