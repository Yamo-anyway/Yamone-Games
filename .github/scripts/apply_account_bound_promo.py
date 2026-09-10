from pathlib import Path
import re

# ---------------- Android ad entitlement gate ----------------
p = Path('app/src/main/java/com/yamone/games/YamoneGamesApp.kt')
s = p.read_text()

if 'val entitlementManager = remember { AdEntitlementManager(context, promotionRepository) }' not in s:
    s = s.replace(
        '    val promotionRepository = remember { PromotionRepository(context) }\n    val scope = rememberCoroutineScope()\n',
        '    val promotionRepository = remember { PromotionRepository(context) }\n'
        '    val entitlementManager = remember { AdEntitlementManager(context, promotionRepository) }\n'
        '    val scope = rememberCoroutineScope()\n',
        1
    )

s = s.replace(
    '    val adRemoved = remember(adRevision) { adAccessStore.adRemoved() }\n'
    '    val adFreeUntilMillis = remember(adRevision) { adAccessStore.adFreeUntilMillis() }\n',
    '    val entitlementSnapshot = remember(adRevision) { entitlementManager.snapshot() }\n'
    '    val adRemoved = entitlementSnapshot.permanentAdFree.active\n'
    '    val adFreeUntilMillis = entitlementSnapshot.temporaryFullscreenFreeUntilMillis\n',
    1
)

request_pattern = re.compile(
    r'    val requestGameStart: \(AppScreen\) -> Unit = \{ target ->.*?\n\}\n\n    LaunchedEffect\(Unit\)',
    re.S
)
request_replacement = '''    val requestGameStart: (AppScreen) -> Unit = { target ->
        val now = System.currentTimeMillis()
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val online = runCatching { connectivityManager?.activeNetwork != null }.getOrDefault(false)
        val entitlements = entitlementManager.snapshot(now)
        when {
            // The first actual game start is free regardless of promotion/purchase state.
            !adAccessStore.hasUsedFirstFreeGame() -> {
                adAccessStore.markFirstFreeGameUsed()
                screenName = target.name
            }
            !entitlements.shouldShowInterstitial(now) -> screenName = target.name
            !online -> screenName = target.name
            else -> {
                pendingGameName = target.name
                showInterstitialTestAd = true
            }
        }
    }

    LaunchedEffect(Unit)'''
s, n = request_pattern.subn(request_replacement, s, count=1)
if n != 1:
    raise SystemExit(f'Could not replace requestGameStart block: {n}')

p.write_text(s)

# ---------------- Cloudflare account-bound Android promotion API ----------------
p = Path('cloudflare/ranking-worker.js')
s = p.read_text()

if 'let googleJwksCache = null;' not in s:
    s = s.replace(
        'const RANKING_ENABLED = false;\n',
        'const RANKING_ENABLED = false;\n\nlet googleJwksCache = null;\n',
        1
    )

old_route = '''      if (request.method === "POST" && path === "/v1/promotion/redeem") {
        return redeemPromotion(request, env);
      }
'''
new_route = '''      if (request.method === "POST" && path === "/v1/promotion/redeem") {
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
'''
if old_route in s:
    s = s.replace(old_route, new_route, 1)
elif '/v1/promotion/google/claim' not in s:
    raise SystemExit('Promotion route anchor not found')

if 'async function claimGooglePromotion' not in s:
    insert_at = s.index('function normalizePromotionCode(value)')
    functions = r'''async function claimGooglePromotion(request, env) {
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

'''
    s = s[:insert_at] + functions + s[insert_at:]

p.write_text(s)

# Force validation APK build.
p = Path('app/build.gradle.kts')
s = p.read_text()
s = re.sub(r'versionCode = \d+', 'versionCode = 29', s, count=1)
s = re.sub(r'versionName = "[^"]+"', 'versionName = "1.1.0-dev23"', s, count=1)
p.write_text(s)
