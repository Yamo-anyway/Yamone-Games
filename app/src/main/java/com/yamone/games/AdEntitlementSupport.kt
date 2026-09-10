package com.yamone.games

import android.content.Context

/**
 * Paid ad removal is intentionally disabled in the current release.
 *
 * The important design rule is that a promotion and a paid purchase are different entitlements:
 * - promotion: fullscreen ads only are disabled, banner ads remain
 * - paid purchase: banner + fullscreen ads are disabled
 */
internal enum class PermanentAdFreeSource {
    NONE,
    GOOGLE_PLAY,
    APP_STORE
}

internal data class PermanentAdFreeEntitlement(
    val active: Boolean = false,
    val source: PermanentAdFreeSource = PermanentAdFreeSource.NONE,
    val verifiedAtMillis: Long = 0L
)

/**
 * Local cache for a store-verified permanent ad-free entitlement.
 *
 * Nothing writes an active entitlement in the current release. A future Play Billing / StoreKit
 * provider may cache a successfully verified entitlement here after checking the store.
 */
internal class PurchaseEntitlementStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun current(): PermanentAdFreeEntitlement {
        val active = prefs.getBoolean(KEY_ACTIVE, false)
        val source = runCatching {
            PermanentAdFreeSource.valueOf(prefs.getString(KEY_SOURCE, null).orEmpty())
        }.getOrDefault(PermanentAdFreeSource.NONE)
        return PermanentAdFreeEntitlement(
            active = active && source != PermanentAdFreeSource.NONE,
            source = if (active) source else PermanentAdFreeSource.NONE,
            verifiedAtMillis = prefs.getLong(KEY_VERIFIED_AT, 0L)
        )
    }

    fun cacheVerified(entitlement: PermanentAdFreeEntitlement) {
        if (!entitlement.active || entitlement.source == PermanentAdFreeSource.NONE) {
            clear()
            return
        }
        prefs.edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_SOURCE, entitlement.source.name)
            .putLong(KEY_VERIFIED_AT, entitlement.verifiedAtMillis)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "yamone_purchase_entitlement"
        const val KEY_ACTIVE = "permanent_ad_free_active"
        const val KEY_SOURCE = "permanent_ad_free_source"
        const val KEY_VERIFIED_AT = "permanent_ad_free_verified_at"
    }
}

/** Contract that future Play Billing / StoreKit implementations plug into. */
internal interface PermanentAdFreeProvider {
    fun cached(): PermanentAdFreeEntitlement
    suspend fun restore(): PermanentAdFreeEntitlement
}

/** Current release implementation: purchase is structurally reserved but not offered. */
internal class DisabledPermanentAdFreeProvider(
    private val store: PurchaseEntitlementStore
) : PermanentAdFreeProvider {
    override fun cached(): PermanentAdFreeEntitlement = store.current()
    override suspend fun restore(): PermanentAdFreeEntitlement = store.current()
}

internal data class AdEntitlementSnapshot(
    val temporaryFullscreenFreeUntilMillis: Long,
    val promotion: PromotionEntitlement,
    val permanentAdFree: PermanentAdFreeEntitlement
) {
    fun shouldShowBanner(): Boolean = !permanentAdFree.active

    fun shouldShowInterstitial(nowMillis: Long = System.currentTimeMillis()): Boolean =
        !permanentAdFree.active &&
            !promotion.isActive(nowMillis) &&
            temporaryFullscreenFreeUntilMillis <= nowMillis

    fun shouldOfferRewarded(nowMillis: Long = System.currentTimeMillis()): Boolean =
        !permanentAdFree.active && !promotion.isActive(nowMillis)
}

internal class AdEntitlementManager(
    context: Context,
    private val promotionRepository: PromotionRepository,
    private val permanentProvider: PermanentAdFreeProvider = DisabledPermanentAdFreeProvider(
        PurchaseEntitlementStore(context.applicationContext)
    )
) {
    private val adAccessStore = AdAccessStore(context.applicationContext)

    fun snapshot(nowMillis: Long = System.currentTimeMillis()): AdEntitlementSnapshot =
        AdEntitlementSnapshot(
            temporaryFullscreenFreeUntilMillis = adAccessStore.adFreeUntilMillis(),
            promotion = promotionRepository.current(),
            permanentAdFree = permanentProvider.cached()
        )
}

/**
 * Cross-platform contract kept here so later implementation does not change ad policy.
 * Android promotion codes are Yamone-issued one-time codes bound only to the current installation.
 * iOS promotion codes can later use Apple's official StoreKit Offer Code flow.
 */
internal enum class PromotionChannel {
    ANDROID_ONE_TIME_INSTALL_CODE,
    IOS_APP_STORE_OFFER_CODE
}
