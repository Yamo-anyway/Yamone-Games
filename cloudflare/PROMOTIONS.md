# Yamone Games 프로모션 운영

현재 프로모션은 **전면광고만 면제**합니다. 프로모션이 적용되어도 배너 광고는 계속 표시됩니다.

프로모션 검증 요청에는 **프로모션 코드만** 전송합니다. 계정 ID, 기기 ID, 광고 ID, 닉네임, 게임 기록은 프로모션 API로 전송하지 않습니다.

## 1. Cloudflare에 필요한 변경

기존 `yamone-games-ranking-api` Worker와 기존 D1 `DB` 바인딩을 그대로 사용합니다.

추가로 필요한 것은 두 가지입니다.

1. 기존 D1 데이터베이스에 `cloudflare/migrations/003_promotions.sql` 적용
2. 변경된 `cloudflare/ranking-worker.js`를 기존 Worker에 재배포

새 D1 데이터베이스, 새 KV, 새 R2, 새 유료 서비스는 필요하지 않습니다.

현재 릴리스에서는 `RANKING_ENABLED = false`이므로 랭킹 API는 404 `FEATURE_DISABLED`를 반환합니다. 프로모션 API `/v1/promotion/redeem`은 랭킹 비밀키와 무관하게 동작하며 `DB` 바인딩만 필요합니다.

## 2. Dashboard에서 적용하는 가장 간단한 방법

### D1 마이그레이션

Cloudflare Dashboard → Storage & Databases → D1 → 기존 Yamone Games 데이터베이스 → Console에서 `cloudflare/migrations/003_promotions.sql` 내용을 실행합니다.

### Worker 배포

Cloudflare Dashboard → Workers & Pages → 기존 `yamone-games-ranking-api` → Edit code에서 `cloudflare/ranking-worker.js` 내용으로 갱신 후 Deploy 합니다.

`ranking-worker.js`는 별도 빌드가 필요한 파일이 아니라 Cloudflare Worker에서 바로 실행되는 JavaScript입니다.

## 3. 프로모션 코드 등록

DB에는 코드 원문을 저장하지 않고 **정규화된 코드의 SHA-256 해시**를 저장합니다.

예: 코드가 `YAMONE2026`인 경우 macOS Terminal에서:

```bash
printf 'YAMONE2026' | shasum -a 256
```

출력된 64자리 해시를 `code_hash`에 넣습니다.

### 기간 제한 없는 코드 / 1회만 사용

```sql
INSERT INTO promotions (
  code_hash, label, enabled,
  starts_at, expires_at, duration_minutes,
  max_redemptions, redeemed_count,
  created_at, updated_at
) VALUES (
  '<SHA256_HASH>', '오픈 기념', 1,
  NULL, NULL, NULL,
  1, 0,
  unixepoch() * 1000,
  unixepoch() * 1000
);
```

### 등록 후 7일 동안 전면광고 면제 / 최대 100회 사용

`10080분 = 7일`

```sql
INSERT INTO promotions (
  code_hash, label, enabled,
  starts_at, expires_at, duration_minutes,
  max_redemptions, redeemed_count,
  created_at, updated_at
) VALUES (
  '<SHA256_HASH>', '7일 체험', 1,
  NULL, NULL, 10080,
  100, 0,
  unixepoch() * 1000,
  unixepoch() * 1000
);
```

## 4. 개인정보 최소화와 코드 공유 방지

계정/기기 식별자를 서버에 보내지 않기 때문에 동일 사용자가 같은 공용 코드를 여러 기기에서 사용하는 것을 사용자 단위로 판별하지 않습니다.

개인에게 한 번만 제공할 프로모션은 **각 사용자마다 서로 다른 1회용 코드**를 만들고 `max_redemptions = 1`로 등록하는 방식을 권장합니다. 이 방식이면 사용자 식별정보를 서버에 저장하지 않고도 코드 재사용을 막을 수 있습니다.

공개 이벤트 코드는 `max_redemptions`를 원하는 전체 사용 횟수로 설정할 수 있습니다.

## 5. 현재 광고 권한 분리

- 일반 사용자: 배너 표시 / 전면광고 정책 적용 / 보상형 +30분 가능
- 프로모션 사용자: **배너 표시 / 전면광고 면제**
- 향후 유료 광고제거: 현재 UI 비활성화. 추후 별도 entitlement로 구현하여 배너 + 전면광고 모두 제거하도록 분리

향후 유료 광고제거 기능을 실제 출시할 때는 프로모션이 유료 권한을 우회하는 구조로 해석되지 않도록 Apple/Google의 당시 최신 결제·프로모션 정책을 다시 확인합니다.
