# Yamone Games 프로모션 운영

현재 프로모션은 **전면광고만 면제**합니다. 프로모션이 적용되어도 배너 광고는 계속 표시됩니다.

프로모션 권한은 두 종류만 사용합니다.

- **기간형**: 코드 적용 시점부터 지정 기간 동안 전면광고 면제
- **무기한**: 만료 없이 전면광고 면제

앱/Cloudflare에서는 프로모션 사용 횟수나 소진 횟수를 관리하지 않습니다. 향후 Apple/Google 공식 프로모션으로 전환하면 코드 발급/교환 한도는 각 스토어가 관리하고, 앱은 스토어가 확인한 결과를 기간형 또는 무기한 entitlement로만 변환합니다.

프로모션 검증 요청에는 **프로모션 코드만** 전송합니다. 계정 ID, 기기 ID, 광고 ID, 닉네임, 게임 기록은 프로모션 API로 전송하지 않습니다.

## Cloudflare 적용

기존 Worker와 D1 `DB` 바인딩을 그대로 사용합니다.

1. 기존 D1 데이터베이스에 `cloudflare/migrations/003_promotions.sql` 적용
2. 변경된 `cloudflare/ranking-worker.js`를 기존 Worker에 재배포

새 D1, KV, R2 또는 별도 유료 서비스는 필요하지 않습니다. 현재 릴리스의 랭킹 API는 비활성화되어 있고 프로모션 API만 사용합니다.

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

`expires_at`을 사용하면 코드 자체를 언제까지 교환할 수 있는지도 제한할 수 있습니다. `duration_minutes`와 함께 사용하면 실제 entitlement 만료는 둘 중 먼저 오는 시각으로 제한됩니다.

## 향후 공식 스토어 프로모션 매핑

앱 내부 모델은 그대로 유지합니다.

- Store 결과가 기간형 상품/혜택이면 `validUntilMillis`를 저장
- Store 결과가 영구 비소모성 상품이면 `validUntilMillis = null`로 저장
- 두 경우 모두 `fullscreenAdsDisabled = true`, `bannerAdsRemain = true`

따라서 나중에 광고제거 구매 기능을 추가해도 프로모션 entitlement와 영구 유료 광고제거 entitlement는 서로 독립적으로 유지할 수 있습니다.
