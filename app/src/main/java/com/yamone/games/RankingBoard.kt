package com.yamone.games

import com.yamone.games.arcadecore.ArcadeGameId
import com.yamone.games.arcadecore.formatIceHeight
import com.yamone.games.arcadecore.preciseDuration
import java.util.Locale

/** Distinct units and modes prevent old whole metres from being read as centimetres. */
internal enum class RankingBoard(
    val gameId: String, val modeId: String, val unit: String, val label: String,
    val minimumWins: Boolean = false, val arcade: ArcadeGameId? = null
) {
    ICE("ice_jump", "height_cm", "centimeters", "빙하", arcade = ArcadeGameId.ICE_JUMP),
    FISH("fish_munch", "normal", "points", "물고기", arcade = ArcadeGameId.FISH_MUNCH),
    SNOW("snow_rush", "shards_ms", "milliseconds", "눈덩이", arcade = ArcadeGameId.SNOW_RUSH),
    SUDOKU_EASY("sudoku", "easy", "seconds", "쉬움", true),
    SUDOKU_NORMAL("sudoku", "normal", "seconds", "보통", true),
    SUDOKU_HARD("sudoku", "hard", "seconds", "어려움", true),
    SUDOKU_CHALLENGE("sudoku", "challenge", "seconds", "도전", true);

    val key: String get() = "${gameId}_${modeId}"
    fun better(candidate: Int, old: Int): Boolean = old < 0 || if (minimumWins) candidate < old else candidate > old
    fun format(score: Int): String = when (this) {
        ICE -> formatIceHeight(score)
        FISH -> "${score}마리"
        SNOW -> preciseDuration(score)
        else -> String.format(Locale.US, "%d:%02d", score.coerceAtLeast(0) / 60, score.coerceAtLeast(0) % 60)
    }
    companion object {
        fun forGame(game: ArcadeGameId): RankingBoard? = entries.firstOrNull { it.arcade == game }
    }
}
