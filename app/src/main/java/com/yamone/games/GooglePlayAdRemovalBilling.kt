package com.yamone.games

import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Google Play is the source of truth for permanent ad removal on Android.
 *
 * The current Yamone release does not expose a paid purchase button. Users can receive the
 * [GOOGLE_PLAY_REMOVE_ADS_PRODUCT_ID] one-time product through an official Google Play promo code.
 * Later, the same product can also be sold normally without changing the entitlement model.
 */
internal class GooglePlayAdRemovalBilling(
    context: Context,
    private val onEntitlementChanged: () -> Unit
) {
    private val appContext = context.applicationContext
    private val entitlementStore = PurchaseEntitlementStore(appContext)

    @Volatile
    private var connecting = false

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.orEmpty().forEach(::processPurchase)
                // A Play promo can be redeemed outside the app. Always re-query the complete
                // ownership snapshot instead of trusting only the update callback payload.
                queryOwnedProducts()
            }
        }
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun refresh() {
        if (billingClient.isReady) {
            queryOwnedProducts()
            return
        }
        connect()
    }

    fun close() {
        connecting = false
        runCatching { billingClient.endConnection() }
    }

    private fun connect() {
        synchronized(this) {
            if (connecting || billingClient.isReady) return
            connecting = true
        }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                connecting = false
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryOwnedProducts()
                }
            }

            override fun onBillingServiceDisconnected() {
                connecting = false
            }
        })
    }

    private fun queryOwnedProducts() {
        if (!billingClient.isReady) {
            connect()
            return
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                // Keep the last verified cache while Play is temporarily unavailable/offline.
                return@queryPurchasesAsync
            }

            val owned = purchases.firstOrNull { purchase ->
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    purchase.products.contains(GOOGLE_PLAY_REMOVE_ADS_PRODUCT_ID)
            }

            if (owned != null) {
                grantGooglePlayEntitlement()
                processPurchase(owned)
            } else {
                clearGooglePlayEntitlement()
            }
        }
    }

    private fun processPurchase(purchase: Purchase) {
        if (!purchase.products.contains(GOOGLE_PLAY_REMOVE_ADS_PRODUCT_ID)) return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        grantGooglePlayEntitlement()

        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) {
                // Ownership has already been confirmed by Play. A later refresh retries the
                // acknowledgement if it did not complete this time.
            }
        }
    }

    private fun grantGooglePlayEntitlement() {
        val before = entitlementStore.current()
        val after = PermanentAdFreeEntitlement(
            active = true,
            source = PermanentAdFreeSource.GOOGLE_PLAY,
            verifiedAtMillis = System.currentTimeMillis()
        )
        entitlementStore.cacheVerified(after)

        if (!before.active || before.source != PermanentAdFreeSource.GOOGLE_PLAY) {
            onEntitlementChanged()
        }
    }

    private fun clearGooglePlayEntitlement() {
        val before = entitlementStore.current()
        if (!before.active || before.source != PermanentAdFreeSource.GOOGLE_PLAY) return
        entitlementStore.clear()
        onEntitlementChanged()
    }
}
