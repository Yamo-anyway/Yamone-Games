# Yamone Games 광고 권한 구조

## 1. 권한은 서로 분리

앱의 광고 정책은 세 종류의 권한을 서로 독립적으로 판단합니다.

| 권한 | 배너 | 전면광고 |
|---|---|---|
| 보상형 광고로 받은 임시 시간 | 표시 | 만료 전 면제 |
| 프로모션 | **표시** | 기간형/무기한 면제 |
| 향후 유료 광고제거 구매 | **제거** | **제거** |

따라서 향후 Google Play Billing / Apple StoreKit 광고제거 구매를 추가해도 프로모션의 `배너는 계속 표시` 정책은 바뀌지 않습니다.

## 2. Android 프로모션

Android는 Yamone이 무료로 개별 발급한 코드를 사용합니다. 코드는 판매하거나 외부 결제의 대가로 제공하지 않습니다.

최종 동작:

1. 사용자가 개별 프로모션 코드 입력
2. Google 계정 인증으로 ID Token 획득
3. Worker가 Google ID Token의 서명/issuer/audience/expiry를 검증
4. Worker는 Google `sub` 원문을 저장하지 않고 `HMAC(PROMO_IDENTITY_SECRET, provider + sub)` 값만 생성
5. `promotion_claims.promotion_id`가 PRIMARY KEY이므로 해당 코드는 최초 사용자에게만 귀속
6. 재설치/기기변경 시 같은 Google 계정으로 인증하면 서버에서 기간형/무기한 entitlement 복원

서버에 이름, 이메일, 전화번호, Google 프로필 사진, 광고 ID, 게임 기록은 저장하지 않습니다. 다만 HMAC 계정 식별값도 지속적인 사용자 구분이 가능하므로 개인정보처리방침에서는 가명화된 계정 식별정보로 취급합니다.

## 3. iOS 프로모션

iOS는 자체 코드를 사용하지 않고 Apple 공식 App Store Offer Code / StoreKit 흐름으로 연결합니다.

앱 내부 결과는 Android와 동일하게 두 종류로만 변환합니다.

- 기간형: `validUntilMillis` 있음
- 무기한: `validUntilMillis = null`

두 경우 모두 `fullscreenAdsDisabled = true`, `bannerAdsRemain = true`입니다.

## 4. 향후 광고제거 구매

광고제거 구매는 프로모션과 다른 entitlement입니다.

- Android: Google Play Billing 비소모성 상품
- iOS: StoreKit 비소모성 상품
- 구매 성공 여부는 플랫폼 스토어가 검증한 결과만 신뢰
- 로컬에는 검증된 권한을 캐시할 수 있으나 단순 로컬 boolean만으로 구매를 생성하지 않음
- 같은 플랫폼 재설치 시 스토어 복원 기능으로 다시 확인
- Android와 iOS 구매 연동은 현재 범위에서 제외

구매 entitlement가 활성화된 경우에만 배너와 전면광고가 모두 제거됩니다.

## 5. Cloudflare D1

적용 순서:

1. `cloudflare/migrations/003_promotions.sql`
2. `cloudflare/migrations/004_promotion_claims.sql`
3. 변경된 Worker 재배포

Android 계정 귀속 프로모션을 실제 활성화할 때 Worker 환경 변수/Secret으로 다음 값이 필요합니다.

- `GOOGLE_CLIENT_ID`: Android Google 인증에서 사용하는 서버(Web) OAuth client ID
- `PROMO_IDENTITY_SECRET`: Google `sub`를 HMAC 처리하기 위한 충분히 긴 랜덤 Secret
- `PROMOTION_REQUIRE_GOOGLE_BINDING=1`: 기존 개발용 익명 `/v1/promotion/redeem`을 막고 Google 귀속 방식만 허용할 때 설정

`PROMO_IDENTITY_SECRET`은 `RANKING_SIGNING_SECRET`과 반드시 별개로 사용합니다.

## 6. 개인정보 연동 삭제 시

사용자가 프로모션 계정 연동정보 삭제를 요청하면 `promotion_claims.subject_hash`를 NULL로 만들고 `deleted_at`만 기록합니다.

이렇게 하면:

- 가명 사용자 식별값은 제거됨
- 이미 사용된 코드라는 사실은 유지됨
- 삭제된 코드를 다른 사람이 다시 등록할 수 없음
- 삭제 이후에는 해당 프로모션 복원 불가
