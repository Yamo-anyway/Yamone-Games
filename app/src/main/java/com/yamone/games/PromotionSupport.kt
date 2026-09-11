package com.yamone.games

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

// Current release: the normal paid purchase button stays hidden.
// Later this can be turned on and wired to the very same one-time product.
internal const val PURCHASE_UI_ENABLED = false

// Current release: promo-code entry is available and redemption is delegated to Google Play.
internal const val PROMOTION_REDEMPTION_ENABLED = true

// Create this exact one-time product ID in Play Console before generating promo codes.
internal const val GOOGLE_PLAY_REMOVE_ADS_PRODUCT_ID = "remove_ads"

/**
 * Legacy compatibility model retained so older call sites do not need a broad refactor in dev56.
 * Yamone-issued promotion entitlements are no longer granted. Google Play ownership is now the
 * only Android source of permanent ad-removal entitlement.
 */
internal data class PromotionEntitlement(
    val active: Boolean,
    val validUntilMillis: Long?,
    val label: String
) {
    fun isActive(nowMillis: Long = System.currentTimeMillis()): Boolean =
        active && (validUntilMillis == null || validUntilMillis > nowMillis)
}

internal sealed interface PromotionRedeemResult {
    data class Success(val entitlement: PromotionEntitlement) : PromotionRedeemResult
    data object InvalidCode : PromotionRedeemResult
    data object AlreadyUsed : PromotionRedeemResult
    data object NotStarted : PromotionRedeemResult
    data object Expired : PromotionRedeemResult
    data object Offline : PromotionRedeemResult
    data object ServerUnavailable : PromotionRedeemResult
}

/**
 * The old local promotion store is intentionally neutralized. Official Google Play promo codes
 * grant the same non-consumable `remove_ads` ownership as a future normal purchase, so there is no
 * separate Yamone promotion entitlement to persist.
 */
internal class PromotionStore(context: Context) {
    @Suppress("UNUSED_PARAMETER")
    fun current(nowMillis: Long = System.currentTimeMillis()): PromotionEntitlement =
        PromotionEntitlement(active = false, validUntilMillis = null, label = "Google Play 프로모션")

    @Suppress("UNUSED_PARAMETER")
    fun isFullscreenAdFree(nowMillis: Long = System.currentTimeMillis()): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun apply(grant: PromotionEntitlement) = Unit

    fun clear() = Unit
}

/**
 * Kept only for source compatibility with the current YamoneGamesApp call site. The UI no longer
 * invokes this method; it opens the official Google Play redemption page instead.
 */
internal class PromotionRepository(context: Context) {
    private val store = PromotionStore(context.applicationContext)

    fun current(): PromotionEntitlement = store.current()

    @Suppress("UNUSED_PARAMETER")
    suspend fun redeem(rawCode: String): PromotionRedeemResult = PromotionRedeemResult.ServerUnavailable
}

/**
 * Opens Google Play's official promo-code redemption screen with the entered code pre-filled.
 * Google Play validates and redeems the code; Yamone never validates, stores, or transmits it.
 */
internal fun openGooglePlayPromoCode(context: Context, rawCode: String): Boolean {
    val code = normalizePromotionCode(rawCode)
    if (code.isBlank()) return false

    val uri = Uri.parse("https://play.google.com/redeem?code=${Uri.encode(code)}")
    val playIntent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.android.vending")
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    return try {
        context.startActivity(playIntent)
        true
    } catch (_: ActivityNotFoundException) {
        val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(fallback)
            true
        } catch (_: Exception) {
            Toast.makeText(context, "Google Play 코드 사용 화면을 열 수 없어요.", Toast.LENGTH_SHORT).show()
            false
        }
    } catch (_: Exception) {
        Toast.makeText(context, "Google Play 코드 사용 화면을 열 수 없어요.", Toast.LENGTH_SHORT).show()
        false
    }
}

internal fun normalizePromotionCode(value: String): String = value.trim()
