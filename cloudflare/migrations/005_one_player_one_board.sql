-- v0.3.08
-- Keep exactly one leaderboard row for each player/game/mode, then enforce it.
-- Existing duplicates are reduced to the best score before the unique index is created.

DELETE FROM leaderboard
WHERE rowid IN (
  SELECT rowid
  FROM (
    SELECT
      rowid,
      ROW_NUMBER() OVER (
        PARTITION BY player_id, game_id, mode_id
        ORDER BY
          CASE WHEN game_id = 'sudoku' THEN best_score END ASC,
          CASE WHEN game_id <> 'sudoku' THEN best_score END DESC,
          achieved_at ASC,
          rowid ASC
      ) AS rn
    FROM leaderboard
  )
  WHERE rn > 1
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_leaderboard_one_player_board
ON leaderboard(player_id, game_id, mode_id);
