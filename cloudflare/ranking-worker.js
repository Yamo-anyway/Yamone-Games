const RANKING_ENABLED = false;

const ALLOWED_GAMES = {
  ice_jump: ["normal"],
  fish_munch: ["normal", "time_attack"],
  snow_rush: ["normal"],
};

const MAX_SCORE = {
  "ice_jump:normal": 50000000,
  "fish_munch:normal": 100000,
  "fish_munch:time_attack": 100000,
  "snow_rush:normal": 86400,
};

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, DELETE, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
};

export default {
  async fetch(request, env) {
    try {
      if (request.method === "OPTIONS") {
        return new Response(null, { status: 204, headers: CORS_HEADERS });
      }

      if (!env.DB) return json({ error: "DATABASE_NOT_CONFIGURED" }, 500);

      const url = new URL(request.url);
      const path = url.pathname;

      if (request.method === "GET" && path === "/health") {
        return json({ ok: true, service: "yamone-games-ranking-api" });
      }

      if (request.method === "POST" && path === "/v1/ranking/submit") {
        if (!RANKING_ENABLED) return json({ error: "FEATURE_DISABLED" }, 404);
        if (!env.RANKING_SIGNING_SECRET) return json({ error: "SECRET_NOT_CONFIGURED" }, 500);
        return submitRanking(request, env);
      }

      if (request.method === "POST" && path === "/v1/promotion/redeem") {
        return redeemPromotion(request, env);
      }

      if (request.method === "DELETE" && path === "/v1/ranking/player") {
        if (!RANKING_ENABLED) return json({ error: "FEATURE_DISABLED" }, 404);
        if (!env.RANKING_SIGNING_SECRET) return json({ error: "SECRET_NOT_CONFIGURED" }, 500);
        return deletePlayerRecords(request, env);
      }

      if (request.method === "DELETE" && path === "/v1/ranking/player/games") {
        if (!RANKING_ENABLED) return json({ error: "FEATURE_DISABLED" }, 404);
        if (!env.RANKING_SIGNING_SECRET) return json({ error: "SECRET_NOT_CONFIGURED" }, 500);
        return deleteSelectedPlayerRecords(request, env);
      }

      const match = path.match(/^\/v1\/ranking\/([a-z_]+)\/([a-z_]+)$/);
      if (request.method === "GET" && match) {
        if (!RANKING_ENABLED) return json({ error: "FEATURE_DISABLED" }, 404);
        if (!env.RANKING_SIGNING_SECRET) return json({ error: "SECRET_NOT_CONFIGURED" }, 500);
        return getRanking(env, match[1], match[2], url.searchParams.get("playerId"));
      }

      return json({ error: "NOT_FOUND" }, 404);
    } catch (error) {
      console.error(error);
      return json({ error: "INTERNAL_ERROR" }, 500);
    }
  },
};

async function redeemPromotion(request, env) {
  let body;
  try {
    body = await request.json();
  } catch {
    return json({ error: "INVALID_JSON" }, 400);
  }

  const code = normalizePromotionCode(body?.code);
  const installId = typeof body?.installId === "string" ? body.installId.trim() : "";
  if (code.length < 4 || code.length > 40) {
    return json({ error: "INVALID_PROMOTION_CODE" }, 400);
  }
  // Random UUID-like app-install identifier. It is not a Google/Apple/device/account identifier.
  if (installId.length < 16 || installId.length > 128) {
    return json({ error: "INVALID_INSTALL_ID" }, 400);
  }

  const codeHash = await sha256Hex(code);
  const installHash = await sha256Hex(`promotion-install:${installId}`);
  const promo = await env.DB
    .prepare(`
      SELECT id, label, enabled, starts_at, expires_at, duration_minutes
      FROM promotions
      WHERE code_hash = ?
      LIMIT 1
    `)
    .bind(codeHash)
    .first();

  if (!promo || Number(promo.enabled) !== 1) {
    return json({ error: "PROMOTION_NOT_FOUND" }, 404);
  }

  const now = Date.now();
  if (promo.starts_at != null && now < Number(promo.starts_at)) {
    return json({ error: "PROMOTION_NOT_STARTED" }, 409);
  }
  if (promo.expires_at != null && now >= Number(promo.expires_at)) {
    return json({ error: "PROMOTION_EXPIRED" }, 410);
  }

  let validUntil = null;
  const durationMinutes = promo.duration_minutes == null ? null : Number(promo.duration_minutes);
  if (Number.isFinite(durationMinutes) && durationMinutes > 0) {
    validUntil = now + Math.floor(durationMinutes * 60_000);
  }
  if (promo.expires_at != null) {
    const absoluteExpiry = Number(promo.expires_at);
    validUntil = validUntil == null ? absoluteExpiry : Math.min(validUntil, absoluteExpiry);
  }

  // Atomic one-time redemption. If the response is lost, the same installation may retry and
  // receive the same entitlement. A reinstall gets a new installId and therefore cannot reuse it.
  await env.DB
    .prepare(`
      INSERT OR IGNORE INTO promotion_redemptions (
        promotion_id, install_hash, redeemed_at, entitlement_until
      ) VALUES (?, ?, ?, ?)
    `)
    .bind(promo.id, installHash, now, validUntil)
    .run();

  const redemption = await env.DB
    .prepare(`
      SELECT install_hash, redeemed_at, entitlement_until
      FROM promotion_redemptions
      WHERE promotion_id = ?
      LIMIT 1
    `)
    .bind(promo.id)
    .first();

  if (!redemption || redemption.install_hash !== installHash) {
    return json({ error: "PROMOTION_ALREADY_USED" }, 409);
  }

  if (redemption.entitlement_until != null && Number(redemption.entitlement_until) <= now) {
    return json({ error: "PROMOTION_EXPIRED" }, 410);
  }

  return json({
    ok: true,
    label: typeof promo.label === "string" && promo.label.trim() ? promo.label.trim().slice(0, 40) : "프로모션",
    validUntil: redemption.entitlement_until == null ? null : Number(redemption.entitlement_until),
    bannerAdsRemain: true,
    fullscreenAdsDisabled: true,
  });
}

function normalizePromotionCode(value) {
  if (typeof value !== "string") return "";
  return value.trim().toUpperCase().replace(/[^\p{L}\p{N}_-]/gu, "").slice(0, 40);
}

async function sha256Hex(value) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return [...new Uint8Array(digest)]
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

async function submitRanking(request, env) {
  let body;
  try {
    body = await request.json();
  } catch {
    return json({ error: "INVALID_JSON" }, 400);
  }

  const playerId = typeof body.playerId === "string" ? body.playerId.trim() : "";
  const nickname = cleanNickname(body.nickname);
  const countryCode = cleanCountryCode(body.countryCode);
  const gameId = typeof body.gameId === "string" ? body.gameId.trim() : "";
  const modeId = typeof body.modeId === "string" ? body.modeId.trim() : "";
  const score = Number(body.score);

  if (!validPlayerId(playerId)) return json({ error: "INVALID_PLAYER_ID" }, 400);
  if (!validGameMode(gameId, modeId)) return json({ error: "INVALID_GAME_MODE" }, 400);
  if (!Number.isInteger(score) || score < 0) return json({ error: "INVALID_SCORE" }, 400);

  const maxScore = MAX_SCORE[`${gameId}:${modeId}`];
  if (maxScore !== undefined && score > maxScore) {
    return json({ error: "SCORE_OUT_OF_RANGE" }, 400);
  }

  const playerKey = await playerHash(env.RANKING_SIGNING_SECRET, playerId);
  const existing = await env.DB
    .prepare(`
      SELECT best_score, achieved_at
      FROM leaderboard
      WHERE player_id = ? AND game_id = ? AND mode_id = ?
      LIMIT 1
    `)
    .bind(playerKey, gameId, modeId)
    .first();

  const now = Date.now();
  if (existing && score <= existing.best_score) {
    await env.DB
      .prepare(`
        UPDATE leaderboard
        SET nickname = ?, country_code = ?, updated_at = ?
        WHERE player_id = ? AND game_id = ? AND mode_id = ?
      `)
      .bind(nickname, countryCode, now, playerKey, gameId, modeId)
      .run();
    return json({ ok: true, updated: false, bestScore: existing.best_score });
  }

  await env.DB
    .prepare(`
      INSERT INTO leaderboard (
        player_id, nickname, country_code, game_id, mode_id, best_score, achieved_at, updated_at
      )
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(player_id, game_id, mode_id)
      DO UPDATE SET
        nickname = excluded.nickname,
        country_code = excluded.country_code,
        best_score = excluded.best_score,
        achieved_at = excluded.achieved_at,
        updated_at = excluded.updated_at
      WHERE excluded.best_score > leaderboard.best_score
    `)
    .bind(playerKey, nickname, countryCode, gameId, modeId, score, now, now)
    .run();

  return json({ ok: true, updated: true, bestScore: score });
}

async function getRanking(env, gameId, modeId, rawPlayerId) {
  if (!validGameMode(gameId, modeId)) {
    return json({ error: "INVALID_GAME_MODE" }, 400);
  }

  const topResult = await env.DB
    .prepare(`
      SELECT nickname, country_code, best_score, achieved_at
      FROM leaderboard
      WHERE game_id = ? AND mode_id = ?
      ORDER BY best_score DESC, achieved_at ASC, player_id ASC
      LIMIT 100
    `)
    .bind(gameId, modeId)
    .all();

  const top = (topResult.results || []).map((row, index) => ({
    rank: index + 1,
    nickname: row.nickname,
    countryCode: row.country_code || "",
    score: row.best_score,
  }));

  const totalRow = await env.DB
    .prepare(`
      SELECT COUNT(*) AS total
      FROM leaderboard
      WHERE game_id = ? AND mode_id = ?
    `)
    .bind(gameId, modeId)
    .first();

  const totalPlayers = Number(totalRow?.total || 0);

  if (!rawPlayerId || !validPlayerId(rawPlayerId)) {
    return json({ ok: true, gameId, modeId, totalPlayers, top, me: null, nearby: [] });
  }

  const playerKey = await playerHash(env.RANKING_SIGNING_SECRET, rawPlayerId);
  const me = await env.DB
    .prepare(`
      SELECT nickname, country_code, best_score, achieved_at
      FROM leaderboard
      WHERE player_id = ? AND game_id = ? AND mode_id = ?
      LIMIT 1
    `)
    .bind(playerKey, gameId, modeId)
    .first();

  if (!me) {
    return json({ ok: true, gameId, modeId, totalPlayers, top, me: null, nearby: [] });
  }

  const betterRow = await env.DB
    .prepare(`
      SELECT COUNT(*) AS better
      FROM leaderboard
      WHERE game_id = ? AND mode_id = ?
        AND (
          best_score > ?
          OR (best_score = ? AND achieved_at < ?)
          OR (best_score = ? AND achieved_at = ? AND player_id < ?)
        )
    `)
    .bind(
      gameId,
      modeId,
      me.best_score,
      me.best_score,
      me.achieved_at,
      me.best_score,
      me.achieved_at,
      playerKey
    )
    .first();

  const myRank = Number(betterRow?.better || 0) + 1;
  const fromRank = Math.max(1, myRank - 3);
  const toRank = Math.min(totalPlayers, myRank + 3);

  const nearbyResult = await env.DB
    .prepare(`
      WITH ranked AS (
        SELECT
          player_id,
          nickname,
          country_code,
          best_score,
          ROW_NUMBER() OVER (
            ORDER BY best_score DESC, achieved_at ASC, player_id ASC
          ) AS ranking
        FROM leaderboard
        WHERE game_id = ? AND mode_id = ?
      )
      SELECT player_id, nickname, country_code, best_score, ranking
      FROM ranked
      WHERE ranking BETWEEN ? AND ?
      ORDER BY ranking ASC
    `)
    .bind(gameId, modeId, fromRank, toRank)
    .all();

  const nearby = (nearbyResult.results || []).map((row) => ({
    rank: Number(row.ranking),
    nickname: row.nickname,
    countryCode: row.country_code || "",
    score: row.best_score,
    isMe: row.player_id === playerKey,
  }));

  return json({
    ok: true,
    gameId,
    modeId,
    totalPlayers,
    top,
    me: {
      rank: myRank,
      nickname: me.nickname,
      countryCode: me.country_code || "",
      score: me.best_score,
    },
    nearby,
  });
}

async function deletePlayerRecords(request, env) {
  let body;
  try {
    body = await request.json();
  } catch {
    return json({ error: "INVALID_JSON" }, 400);
  }

  const playerId = typeof body.playerId === "string" ? body.playerId.trim() : "";
  if (!validPlayerId(playerId)) return json({ error: "INVALID_PLAYER_ID" }, 400);

  const playerKey = await playerHash(env.RANKING_SIGNING_SECRET, playerId);
  await env.DB
    .prepare(`DELETE FROM leaderboard WHERE player_id = ?`)
    .bind(playerKey)
    .run();

  return json({ ok: true });
}

async function deleteSelectedPlayerRecords(request, env) {
  let body;
  try {
    body = await request.json();
  } catch {
    return json({ error: "INVALID_JSON" }, 400);
  }

  const playerId = typeof body.playerId === "string" ? body.playerId.trim() : "";
  const records = Array.isArray(body.records) ? body.records : [];

  if (!validPlayerId(playerId)) return json({ error: "INVALID_PLAYER_ID" }, 400);
  if (records.length < 1 || records.length > 4) {
    return json({ error: "INVALID_RECORD_SELECTION" }, 400);
  }

  const normalized = [];
  const seen = new Set();
  for (const item of records) {
    const gameId = typeof item?.gameId === "string" ? item.gameId.trim() : "";
    const modeId = typeof item?.modeId === "string" ? item.modeId.trim() : "";
    if (!validGameMode(gameId, modeId)) {
      return json({ error: "INVALID_GAME_MODE" }, 400);
    }
    const key = `${gameId}:${modeId}`;
    if (!seen.has(key)) {
      seen.add(key);
      normalized.push({ gameId, modeId });
    }
  }

  const playerKey = await playerHash(env.RANKING_SIGNING_SECRET, playerId);
  const statements = normalized.map(({ gameId, modeId }) =>
    env.DB
      .prepare(`
        DELETE FROM leaderboard
        WHERE player_id = ? AND game_id = ? AND mode_id = ?
      `)
      .bind(playerKey, gameId, modeId)
  );

  await env.DB.batch(statements);
  return json({ ok: true, deleted: normalized.length });
}

function validGameMode(gameId, modeId) {
  return Object.prototype.hasOwnProperty.call(ALLOWED_GAMES, gameId) &&
    ALLOWED_GAMES[gameId].includes(modeId);
}

function validPlayerId(value) {
  return typeof value === "string" && value.length >= 16 && value.length <= 128;
}

function cleanNickname(value) {
  if (typeof value !== "string") return "야모네 플레이어";
  const cleaned = value.replace(/[\u0000-\u001F\u007F]/g, "").trim().slice(0, 20);
  return cleaned || "야모네 플레이어";
}

function cleanCountryCode(value) {
  if (typeof value !== "string") return "";
  const code = value.trim().toUpperCase();
  return /^[A-Z]{2}$/.test(code) ? code : "";
}

async function playerHash(secret, playerId) {
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const signature = await crypto.subtle.sign("HMAC", key, encoder.encode(playerId));
  return [...new Uint8Array(signature)]
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      ...CORS_HEADERS,
    },
  });
}
