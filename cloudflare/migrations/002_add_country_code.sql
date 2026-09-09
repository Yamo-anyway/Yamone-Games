ALTER TABLE leaderboard
ADD COLUMN country_code TEXT NOT NULL DEFAULT '';

DROP INDEX IF EXISTS idx_leaderboard_ranking;

CREATE INDEX IF NOT EXISTS idx_leaderboard_ranking
ON leaderboard (
    game_id,
    mode_id,
    best_score DESC,
    achieved_at ASC,
    player_id ASC
);
