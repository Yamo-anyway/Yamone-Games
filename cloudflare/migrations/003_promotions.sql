CREATE TABLE IF NOT EXISTS promotions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code_hash TEXT NOT NULL UNIQUE,
    label TEXT NOT NULL DEFAULT '프로모션',
    enabled INTEGER NOT NULL DEFAULT 1,
    starts_at INTEGER,
    expires_at INTEGER,
    duration_minutes INTEGER,
    max_redemptions INTEGER,
    redeemed_count INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_promotions_active
ON promotions(enabled, starts_at, expires_at);

-- code_hash에는 원문 코드가 아니라 SHA-256(정규화된 코드)의 16진수 값을 저장합니다.
-- duration_minutes: 사용 시점부터 N분 동안 전면광고 면제
-- expires_at: 프로모션 자체의 절대 만료 시각(epoch milliseconds)
-- 둘 다 NULL이면 기간 제한 없는 전면광고 면제 프로모션입니다.
-- max_redemptions: 전체 사용 가능 횟수. NULL이면 횟수 제한 없음.
