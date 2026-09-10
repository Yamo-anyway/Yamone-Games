CREATE TABLE IF NOT EXISTS promotion_redemptions (
    promotion_id INTEGER PRIMARY KEY,
    install_hash TEXT NOT NULL,
    redeemed_at INTEGER NOT NULL,
    entitlement_until INTEGER,
    FOREIGN KEY (promotion_id) REFERENCES promotions(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_promotion_redemptions_install
ON promotion_redemptions(install_hash);

-- One row per promotion_id means each promotion code can be redeemed only once.
-- install_hash is SHA-256 of a random app-installation identifier generated locally.
-- The raw installation identifier is never stored in D1.
-- entitlement_until NULL means indefinite fullscreen-ad exemption.
-- Android backup/device-transfer excludes the local promotion/install preferences,
-- so uninstall + reinstall creates a new installation and cannot restore/reuse the code.
