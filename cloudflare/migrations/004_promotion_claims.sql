CREATE TABLE IF NOT EXISTS promotion_claims (
    promotion_id INTEGER PRIMARY KEY,
    provider TEXT NOT NULL CHECK(provider IN ('google')),
    subject_hash TEXT,
    claimed_at INTEGER NOT NULL,
    entitlement_until INTEGER,
    deleted_at INTEGER,
    FOREIGN KEY (promotion_id) REFERENCES promotions(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_promotion_claims_subject
ON promotion_claims(provider, subject_hash)
WHERE subject_hash IS NOT NULL AND deleted_at IS NULL;

-- promotion_id PRIMARY KEY means one promotion code can be claimed only once.
-- subject_hash stores only HMAC(provider subject), never the raw Google sub/email/name.
-- entitlement_until NULL means permanent fullscreen-ad exemption.
-- When a user requests unlink/deletion, subject_hash can be cleared while the claim row remains,
-- so the already-used promotion code can never be claimed by another user.
