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

Android는 Yamone이 무료로 개별 발급한 **1회용 코드**를 사용합니다. 코드는 판매하거나 외부 결제의 대가로 제공하지 않습니다.

최종 동작:

1. 사용자가 개별 프로모션 코드 입력
2. 앱이 해당 설치에만 쓰이는 무작위 `installId` 생성
3. Worker는 코드와 설치 식별값을 검증하고, 설치 식별값 원문은 저장하지 않고 SHA-256 해시만 사용
4. `promotion_redemptions.promotion_id`가 PRIMARY KEY이므로 해당 코드는 최초 등록 1회만 가능
5. 같은 설치에서 통신 응답이 끊겨 재시도한 경우 같은 설치 해시라면 기존 entitlement를 다시 반환
6. 앱 업데이트는 로컬 앱 데이터가 유지되므로 프로모션 유지
7. 앱 삭제 후 재설치하면 프로모션/설치 식별값이 복원되지 않으며, 같은 코드는 이미 사용된 상태라 다시 등록 불가

Android 백업 및 기기 이전에서도 `yamone_promotion`, `yamone_promotion_install`을 제외하여 삭제 후 재설치/이전 과정에서 프로모션이 자동 복원되지 않도록 합니다.

Google 로그인, Google 계정 ID, 이메일, 이름, 전화번호, 광고 ID, 게임 기록은 프로모션 등록에 사용하거나 서버에 저장하지 않습니다.

## 3. iOS 프로모션

iOS는 자체 Android 코드 구조를 그대로 복제하지 않고 Apple 공식 App Store Offer Code / StoreKit 흐름으로 연결합니다.

앱 내부 결과는 두 종류로만 변환합니다.

- 기간형: `validUntilMillis` 있음
- 무기한: `validUntilMillis = null`

두 경우 모두 `fullscreenAdsDisabled = true`, `bannerAdsRemain = true`입니다.

## 4. 향후 광고제거 구매

광고제거 구매는 프로모션과 다른 entitlement입니다.

- Android: Google Play Billing 비소모성 상품
- iOS: StoreKit 비소모성 상품
- 구매 성공 여부는 플랫폼 스토어가 검증한 결과만 신뢰
- 로컬에는 검증된 권한을 캐시할 수 있으나 단순 로컬 boolean만으로 구매를 생성하지 않음
- 같은 플랫폼 재설치 시에는 스토어 구매 복원 기능으로 다시 확인
- Android와 iOS 구매 연동은 현재 범위에서 제외

구매 entitlement가 활성화된 경우에만 배너와 전면광고가 모두 제거됩니다.

또한 `yamone_purchase_entitlement` 로컬 캐시는 Android 백업/기기이전에서 제외합니다. 향후 재설치 시 백업된 boolean을 신뢰하지 않고 반드시 스토어에서 구매 상태를 다시 확인하기 위함입니다.

## 5. Cloudflare D1

적용 순서:

1. `cloudflare/migrations/003_promotions.sql`
2. `cloudflare/migrations/004_promotion_redemptions.sql`
3. 변경된 `cloudflare/ranking-worker.js` 재배포

Google 계정 귀속을 사용하지 않으므로 다음 설정은 필요하지 않습니다.

- `GOOGLE_CLIENT_ID`
- `PROMO_IDENTITY_SECRET`
- `PROMOTION_REQUIRE_GOOGLE_BINDING`

현재 필요한 Cloudflare 바인딩은 기존 D1 `DB`뿐입니다.
