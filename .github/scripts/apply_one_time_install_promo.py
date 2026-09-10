from pathlib import Path
import re

# --- Cloudflare Worker: remove Google account binding and make promo one-time per installation.
p = Path('cloudflare/ranking-worker.js')
s = p.read_text()
s = s.replace('let googleJwksCache = null;\n\n', '', 1)

routes = re.compile(
    r'''      if \(request\.method === "POST" && path === "/v1/promotion/redeem"\) \{.*?      if \(request\.method === "DELETE" && path === "/v1/ranking/player"\) \{''',
    re.S,
)
replacement = '''      if (request.method === "POST" && path === "/v1/promotion/redeem") {
        return redeemPromotion(request, env);
      }

      if (request.method === "DELETE" && path === "/v1/ranking/player") {'''
s, count = routes.subn(replacement, s, count=1)
if count != 1:
    raise SystemExit(f'promotion routes replacement failed: {count}')

redeem = re.compile(
    r'async function redeemPromotion\(request, env\) \{.*?(?=async function claimGooglePromotion\(request, env\))',
    re.S,
)
new_redeem = r'''async function redeemPromotion(request, env) {
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

'''
s, count = redeem.subn(new_redeem, s, count=1)
if count != 1:
    raise SystemExit(f'redeem replacement failed: {count}')

google_block = re.compile(
    r'async function claimGooglePromotion\(request, env\) \{.*?(?=function normalizePromotionCode\(value\))',
    re.S,
)
s, count = google_block.subn('', s, count=1)
if count != 1:
    raise SystemExit(f'google promotion block removal failed: {count}')
p.write_text(s)

# --- UI: show a clear one-time-code message.
p = Path('app/src/main/java/com/yamone/games/YamoneGamesApp.kt')
s = p.read_text()
needle = '                    PromotionRedeemResult.InvalidCode -> adInfoMessage = "사용할 수 없는 프로모션 코드예요."\n'
insert = needle + '                    PromotionRedeemResult.AlreadyUsed -> adInfoMessage = "이미 사용된 프로모션 코드예요. 프로모션 코드는 최초 등록한 설치에서만 사용할 수 있어요."\n'
if 'PromotionRedeemResult.AlreadyUsed ->' not in s:
    if needle not in s:
        raise SystemExit('promo result UI anchor not found')
    s = s.replace(needle, insert, 1)
p.write_text(s)
