# Yamone Games 프로모션 운영

현재 프로모션은 **전면광고만 면제**합니다. 프로모션이 적용되어도 배너 광고는 계속 표시됩니다.

프로모션 권한은 두 종류만 사용합니다.

- **기간형**: 코드 최초 등록 시점부터 지정 기간 동안 전면광고 면제
- **무기한**: 만료 없이 전면광고 면제

## 1회용 정책

Android 프로모션 코드는 Yamone이 특정 사용자에게 무료로 개별 지급하는 **1회용 코드**입니다.

- 코드 하나는 최초 한 번만 등록 가능
- 앱 업데이트는 기존 앱 데이터가 유지되므로 프로모션도 유지
- 앱 삭제 후 재설치하면 프로모션은 복원되지 않음
- 삭제 후 같은 코드를 다시 입력해도 서버에서 이미 사용된 코드로 거절
- Google 계정 로그인/연동/복원 기능 없음
- 이름, 이메일, 전화번호, Google 계정 ID를 프로모션 서버에 보내지 않음

앱은 프로모션 등록 시 로컬에서 무작위 설치 식별값을 생성합니다. 서버에는 그 원문을 저장하지 않고 SHA-256 해시만 저장합니다. 이 값은 동일 설치에서 네트워크 응답이 끊긴 경우 안전하게 재시도하기 위한 용도이며 Android 백업 및 기기 이전 대상에서 제외됩니다.

## Cloudflare 적용

기존 Worker와 D1 `DB` 바인딩을 그대로 사용합니다.

1. 기존 D1 데이터베이스에 `cloudflare/migrations/003_promotions.sql` 적용
2. 이어서 `cloudflare/migrations/004_promotion_redemptions.sql` 적용
3. 변경된 `cloudflare/ranking-worker.js`를 기존 Worker에 재배포

`GOOGLE_CLIENT_ID`, `PROMO_IDENTITY_SECRET`, `PROMOTION_REQUIRE_GOOGLE_BINDING`은 더 이상 필요하지 않습니다.

새 D1, KV, R2 또는 별도 유료 서비스도 필요하지 않습니다. 현재 릴리스의 랭킹 API는 비활성화되어 있고 프로모션 API만 사용합니다.

## 코드 등록

DB에는 코드 원문이 아니라 정규화된 코드의 SHA-256 해시를 저장합니다.

```bash
printf 'YAMONE2026' | shasum -a 256
```

### 기간형 예시: 적용 후 7일

```sql
INSERT INTO promotions (
  code_hash, label, enabled,
  starts_at, expires_at, duration_minutes,
  created_at, updated_at
) VALUES (
  '<SHA256_HASH>', '7일 프로모션', 1,
  NULL, NULL, 10080,
  unixepoch() * 1000,
  unixepoch() * 1000
);
```

### 무기한 예시

```sql
INSERT INTO promotions (
  code_hash, label, enabled,
  starts_at, expires_at, duration_minutes,
  created_at, updated_at
) VALUES (
  '<SHA256_HASH>', '무기한 프로모션', 1,
  NULL, NULL, NULL,
  unixepoch() * 1000,
  unixepoch() * 1000
);
```

`expires_at`을 사용하면 코드 자체를 언제까지 최초 등록할 수 있는지도 제한할 수 있습니다. `duration_minutes`와 함께 사용하면 실제 entitlement 만료는 둘 중 먼저 오는 시각으로 제한됩니다.

## iOS

iOS 프로모션은 나중에 Apple 공식 App Store Offer Code / StoreKit 방식으로 연결합니다. Android의 자체 1회용 코드와 구현 방식은 다르지만 앱 내부에서는 둘 다 `기간형` 또는 `무기한` 프로모션 entitlement로 변환합니다.

두 경우 모두:

- `fullscreenAdsDisabled = true`
- `bannerAdsRemain = true`

## 향후 광고제거 구매

향후 광고제거 구매는 프로모션과 별도 entitlement로 유지합니다.

- 프로모션: 배너 표시 / 전면광고 면제
- 유료 광고제거: 배너 제거 / 전면광고 제거

Android는 Google Play Billing, iOS는 StoreKit의 스토어 검증 결과만 신뢰하도록 Provider 자리를 분리해 두었습니다. 따라서 나중에 구매 기능을 추가해도 프로모션 구조를 변경할 필요가 없습니다.
