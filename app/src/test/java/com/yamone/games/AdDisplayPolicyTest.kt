package com.yamone.games

import org.junit.Assert.*
import org.junit.Test

class AdDisplayPolicyTest {
    @Test fun remainingRewardHidesBanner() {
        assertFalse(AdDisplayPolicy.showBanner(false, 2000L, 1000L))
    }
    @Test fun expiryAtExactMillisecondShowsBanner() {
        assertTrue(AdDisplayPolicy.showBanner(false, 2000L, 2000L))
        assertTrue(AdDisplayPolicy.showBanner(false, 0L, 2000L))
    }
    @Test fun permanentOwnershipOverridesExpiredTimer() {
        for (expiry in listOf(0L, 1000L, Long.MAX_VALUE)) {
            assertFalse(AdDisplayPolicy.showBanner(true, expiry, 5000L))
        }
    }
    @Test fun gamesNeverRequireInterstitial() { assertFalse(AdDisplayPolicy.requireInterstitial()) }
    @Test fun clockIsCeilingRoundedWithoutEarlyZero() {
        assertEquals("00:00:01", AdDisplayPolicy.clock(1001L, 1000L))
        assertEquals("00:00:01", AdDisplayPolicy.clock(2000L, 1000L))
        assertEquals("00:00:02", AdDisplayPolicy.clock(2001L, 1000L))
        assertEquals("00:00:00", AdDisplayPolicy.clock(1000L, 1000L))
        assertEquals("00:00:00", AdDisplayPolicy.clock(500L, 1000L))
    }
    @Test fun accruedTimeDoesNotWrapAfterOneDay() {
        assertEquals("25:01:01", AdDisplayPolicy.clock(90_061_000L, 0L))
        assertEquals("100:00:00", AdDisplayPolicy.clock(360_000_000L, 0L))
    }
    @Test fun extremelyLargeExpiryDoesNotOverflow() {
        assertTrue(AdDisplayPolicy.remainingSeconds(Long.MAX_VALUE, 0L) > 0L)
        assertTrue(AdDisplayPolicy.clock(Long.MAX_VALUE, 0L).endsWith(":12:56"))
    }
    @Test fun snapshotUsesSamePolicyAsHeaderAndAllScreens() {
        val normal = AdEntitlementSnapshot(2000L, PromotionEntitlement(false, null, ""), PermanentAdFreeEntitlement())
        assertFalse(normal.shouldShowBanner(1000L))
        assertTrue(normal.shouldShowBanner(2000L))
        assertFalse(normal.shouldShowInterstitial(9000L))
        assertTrue(normal.shouldOfferRewarded(1000L))
        val owned = normal.copy(permanentAdFree = PermanentAdFreeEntitlement(true, PermanentAdFreeSource.GOOGLE_PLAY))
        assertFalse(owned.shouldShowBanner(9000L))
        assertFalse(owned.shouldOfferRewarded(9000L))
    }
}
