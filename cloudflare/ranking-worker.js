const RANKING_ENABLED = false;

let googleJwksCache = null;

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
        // Development/transition endpoint. Set PROMOTION_REQUIRE_GOOGLE_BINDING=1 before
        // account-bound Android promotion is enabled in production.
        if (env.PROMOTION_REQUIRE_GOOGLE_BINDING === "1") {
          return json({ error: "ACCOUNT_BINDING_REQUIRED" }, 409);
        }
        return redeemPromotion(request, env);
      }

      if (request.method === "POST" && path === "/v1/promotion/google/claim") {
        return claimGooglePromotion(request, env);
      }

      if (request.method === "POST" && path === "/v1/promotion/google/restore") {
        return restoreGooglePromotion(request, env);
      }

      if (request.method === "DELETE" && path === "/v1/promotion/google/link") {
        return unlinkGooglePromotions(request, env);
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
  if (code.length < 4 || code.length > 40) {
    return json({ error: "INVALID_PROMOTION_CODE" }, 400);
  }

  const codeHash = await sha256Hex(code);
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

  return json({
    ok: true,
    label: typeof promo.label === "string" && promo.label.trim() ? promo.label.trim().slice(0, 40) : "프로모션",
    validUntil,
    bannerAdsRemain: true,
    fullscreenAdsDisabled: true,
  });
}

async function claimGooglePromotion(request, env) {
  const body = await readJsonBody(request);
  if (!body) return json({ error: "INVALID_JSON" }, 400);

  const identity = await resolveGoogleIdentity(body.idToken, env);
  if (identity.error) return json({ error: identity.error }, identity.status);

  const code = normalizePromotionCode(body.code);
  if (code.length < 4 || code.length > 40) {
    return json({ error: "INVALID_PROMOTION_CODE" }, 400);
  }

  const codeHash = await sha256Hex(code);
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

  let claim = await env.DB
    .prepare(`
      SELECT provider, subject_hash, claimed_at, entitlement_until, deleted_at
      FROM promotion_claims
      WHERE promotion_id = ?
      LIMIT 1
    `)
    .bind(promo.id)
    .first();

  if (!claim) {
    const entitlementUntil = computePromotionEntitlementUntil(promo, now);
    await env.DB
      .prepare(`
        INSERT OR IGNORE INTO promotion_claims (
          promotion_id, provider, subject_hash, claimed_at, entitlement_until, deleted_at
        ) VALUES (?, 'google', ?, ?, ?, NULL)
      `)
      .bind(promo.id, identity.subjectHash, now, entitlementUntil)
      .run();

    claim = await env.DB
      .prepare(`
        SELECT provider, subject_hash, claimed_at, entitlement_until, deleted_at
        FROM promotion_claims
        WHERE promotion_id = ?
        LIMIT 1
      `)
      .bind(promo.id)
      .first();
  }

  if (
    !claim ||
    claim.deleted_at != null ||
    claim.provider !== "google" ||
    claim.subject_hash !== identity.subjectHash
  ) {
    return json({ error: "PROMOTION_ALREADY_CLAIMED" }, 409);
  }

  if (claim.entitlement_until != null && Number(claim.entitlement_until) <= now) {
    return json({ error: "PROMOTION_EXPIRED" }, 410);
  }

  return promotionEntitlementJson(promo.label, claim.entitlement_until, true);
}

async function restoreGooglePromotion(request, env) {
  const body = await readJsonBody(request);
  if (!body) return json({ error: "INVALID_JSON" }, 400);

  const identity = await resolveGoogleIdentity(body.idToken, env);
  if (identity.error) return json({ error: identity.error }, identity.status);

  const result = await env.DB
    .prepare(`
      SELECT p.label, p.enabled, c.entitlement_until, c.claimed_at
      FROM promotion_claims c
      JOIN promotions p ON p.id = c.promotion_id
      WHERE c.provider = 'google'
        AND c.subject_hash = ?
        AND c.deleted_at IS NULL
        AND p.enabled = 1
      ORDER BY c.claimed_at DESC
    `)
    .bind(identity.subjectHash)
    .all();

  const now = Date.now();
  let best = null;
  for (const row of result?.results || []) {
    if (row.entitlement_until == null) {
      best = { label: row.label, validUntil: null };
      break;
    }
    const validUntil = Number(row.entitlement_until);
    if (!Number.isFinite(validUntil) || validUntil <= now) continue;
    if (!best || best.validUntil == null || validUntil > best.validUntil) {
      best = { label: row.label, validUntil };
    }
  }

  if (!best) {
    return json({
      ok: true,
      active: false,
      bannerAdsRemain: true,
      fullscreenAdsDisabled: false,
    });
  }

  return promotionEntitlementJson(best.label, best.validUntil, true);
}

async function unlinkGooglePromotions(request, env) {
  const body = await readJsonBody(request);
  if (!body) return json({ error: "INVALID_JSON" }, 400);

  const identity = await resolveGoogleIdentity(body.idToken, env);
  if (identity.error) return json({ error: identity.error }, identity.status);

  const now = Date.now();
  const result = await env.DB
    .prepare(`
      UPDATE promotion_claims
      SET subject_hash = NULL,
          deleted_at = ?
      WHERE provider = 'google'
        AND subject_hash = ?
        AND deleted_at IS NULL
    `)
    .bind(now, identity.subjectHash)
    .run();

  return json({
    ok: true,
    unlinked: true,
    affected: Number(result?.meta?.changes || 0),
  });
}

function computePromotionEntitlementUntil(promo, now) {
  let validUntil = null;
  const durationMinutes = promo.duration_minutes == null ? null : Number(promo.duration_minutes);
  if (Number.isFinite(durationMinutes) && durationMinutes > 0) {
    validUntil = now + Math.floor(durationMinutes * 60_000);
  }
  if (promo.expires_at != null) {
    const absoluteExpiry = Number(promo.expires_at);
    if (Number.isFinite(absoluteExpiry)) {
      validUntil = validUntil == null ? absoluteExpiry : Math.min(validUntil, absoluteExpiry);
    }
  }
  return validUntil;
}

function promotionEntitlementJson(label, validUntil, active) {
  return json({
    ok: true,
    active,
    label: typeof label === "string" && label.trim() ? label.trim().slice(0, 40) : "프로모션",
    validUntil: validUntil == null ? null : Number(validUntil),
    bannerAdsRemain: true,
    fullscreenAdsDisabled: active,
  });
}

async function readJsonBody(request) {
  try {
    return await request.json();
  } catch {
    return null;
  }
}

async function resolveGoogleIdentity(idToken, env) {
  if (!env.GOOGLE_CLIENT_ID || !env.PROMO_IDENTITY_SECRET) {
    return { error: "PROMOTION_IDENTITY_NOT_CONFIGURED", status: 503 };
  }
  if (typeof idToken !== "string" || idToken.length < 32 || idToken.length > 8192) {
    return { error: "INVALID_GOOGLE_ID_TOKEN", status: 401 };
  }

  try {
    const subject = await verifyGoogleIdToken(idToken, env.GOOGLE_CLIENT_ID);
    const subjectHash = await hmacSha256Hex(
      env.PROMO_IDENTITY_SECRET,
      `promotion:google:${subject}`
    );
    return { subjectHash };
  } catch (error) {
    console.warn("Google promotion identity verification failed", error?.message || "invalid token");
    return { error: "INVALID_GOOGLE_ID_TOKEN", status: 401 };
  }
}

async function verifyGoogleIdToken(idToken, expectedAudience) {
  const parts = idToken.split(".");
  if (parts.length !== 3) throw new Error("JWT_PARTS");

  const header = decodeJwtJson(parts[0]);
  const payload = decodeJwtJson(parts[1]);
  if (header.alg !== "RS256" || typeof header.kid !== "string") {
    throw new Error("JWT_HEADER");
  }

  const jwk = await getGoogleJwk(header.kid);
  const key = await crypto.subtle.importKey(
    "jwk",
    jwk,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["verify"]
  );
  const signingInput = new TextEncoder().encode(`${parts[0]}.${parts[1]}`);
  const signature = base64UrlToBytes(parts[2]);
  const verified = await crypto.subtle.verify(
    { name: "RSASSA-PKCS1-v1_5" },
    key,
    signature,
    signingInput
  );
  if (!verified) throw new Error("JWT_SIGNATURE");

  const nowSeconds = Math.floor(Date.now() / 1000);
  const validIssuer = payload.iss === "accounts.google.com" || payload.iss === "https://accounts.google.com";
  const audiences = Array.isArray(payload.aud) ? payload.aud : [payload.aud];
  if (!validIssuer) throw new Error("JWT_ISSUER");
  if (!audiences.includes(expectedAudience)) throw new Error("JWT_AUDIENCE");
  if (!Number.isFinite(Number(payload.exp)) || Number(payload.exp) <= nowSeconds) throw new Error("JWT_EXPIRED");
  if (payload.nbf != null && Number(payload.nbf) > nowSeconds + 60) throw new Error("JWT_NOT_YET_VALID");
  if (typeof payload.sub !== "string" || payload.sub.length < 1 || payload.sub.length > 255) {
    throw new Error("JWT_SUBJECT");
  }
  return payload.sub;
}

async function getGoogleJwk(kid) {
  const now = Date.now();
  if (googleJwksCache && googleJwksCache.expiresAt > now && googleJwksCache.keys[kid]) {
    return googleJwksCache.keys[kid];
  }

  const response = await fetch("https://www.googleapis.com/oauth2/v3/certs", {
    headers: { Accept: "application/json" },
  });
  if (!response.ok) throw new Error("GOOGLE_JWKS_FETCH");
  const body = await response.json();
  const keys = {};
  for (const key of body?.keys || []) {
    if (typeof key?.kid === "string") keys[key.kid] = key;
  }
  const cacheControl = response.headers.get("cache-control") || "";
  const maxAgeMatch = cacheControl.match(/max-age=(\d+)/i);
  const maxAgeSeconds = maxAgeMatch ? Number(maxAgeMatch[1]) : 3600;
  googleJwksCache = {
    keys,
    expiresAt: now + Math.max(300, Math.min(maxAgeSeconds, 86400)) * 1000,
  };

  if (!keys[kid]) throw new Error("GOOGLE_JWK_NOT_FOUND");
  return keys[kid];
}

function decodeJwtJson(segment) {
  const bytes = base64UrlToBytes(segment);
  return JSON.parse(new TextDecoder().decode(bytes));
}

function base64UrlToBytes(value) {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - (normalized.length % 4)) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

async function hmacSha256Hex(secret, value) {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const signature = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(value));
  return [...new Uint8Array(signature)]
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
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
