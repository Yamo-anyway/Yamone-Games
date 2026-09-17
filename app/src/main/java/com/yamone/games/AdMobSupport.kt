package com.yamone.games

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Development AdMob integration.
 * Google's official demo/test IDs are used until production AdMob IDs are configured.
 */
internal object YamoneAdMob {
    const val SAMPLE_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    const val BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
    const val INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"

    private val started = AtomicBoolean(false)
    private val ready = CompletableDeferred<Unit>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun initialize(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext

        // Yamone Games is currently treated as child-directed for all ad requests.
        // This disables interest-based/remarketing treatment and limits ad content to G.
        val requestConfiguration = RequestConfiguration.Builder()
            .setTagForChildDirectedTreatment(
                RequestConfiguration.TagForChildDirectedTreatment.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE
            )
            .setMaxAdContentRating(
                RequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_G
            )
            .build()

        scope.launch {
            runCatching {
                MobileAds.initialize(
                    appContext,
                    InitializationConfig.Builder(SAMPLE_APP_ID)
                        .setRequestConfiguration(requestConfiguration)
                        .build()
                ) {
                    if (!ready.isCompleted) ready.complete(Unit)
                }
            }.onFailure {
                Log.e("YamoneAdMob", "Mobile Ads initialization failed", it)
                if (!ready.isCompleted) ready.complete(Unit)
            }
        }
    }

    /** Returns false when UMP says ads must not be requested in the current session. */
    suspend fun awaitReady(activity: Activity): Boolean {
        if (!YamonePrivacy.awaitCanRequestAds(activity)) return false
        initialize(activity.applicationContext)
        ready.await()
        return true
    }
}

@Composable
internal fun GlobalTopBanner(visible: Boolean) {
    // Keep this composable in the tree; only its ad subtree changes when the timer expires.
    if (!visible) return
    BoxWithConstraints(
        Modifier.fillMaxWidth().background(Color(0xFFF5FBF9)).padding(top = 4.dp, bottom = 10.dp)
    ) {
        if (maxWidth >= 320.dp) AdMobTestBanner()
    }
}

@Composable
internal fun AdMobTestBanner() {
    val context = LocalContext.current
    val activity = context.findActivity() ?: return
    val adView = remember(activity) { AdView(activity) }
    val alive = remember(adView) { AtomicBoolean(true) }

    LaunchedEffect(adView) {
        if (!YamoneAdMob.awaitReady(activity) || !alive.get()) return@LaunchedEffect
        withContext(Dispatchers.Main) {
            if (!alive.get()) return@withContext
            val request = BannerAdRequest.Builder(YamoneAdMob.BANNER_ID, AdSize.BANNER).build()
            adView.loadAd(request, object : AdLoadCallback<BannerAd> {
                override fun onAdLoaded(ad: BannerAd) {
                    if (!alive.get()) { ad.destroy(); return }
                    Log.d("YamoneAdMob", "Banner test ad loaded")
                }
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.w("YamoneAdMob", "Banner test ad unavailable: ${adError.message}")
                }
            })
        }
    }
    DisposableEffect(adView) {
        onDispose { alive.set(false); adView.destroy() }
    }
    Box(
        Modifier.fillMaxWidth().height(50.dp).semantics { contentDescription = "상단 배너 광고 영역" },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(factory = { adView }, modifier = Modifier.width(320.dp).height(50.dp))
    }
}

@Composable
internal fun AdMobTestRewarded(
    onRewardEarned: () -> Unit,
    onUnavailableOrClosed: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val latestEarned = rememberUpdatedState(onRewardEarned)
    val latestClosed = rememberUpdatedState(onUnavailableOrClosed)

    val alive = remember { AtomicBoolean(true) }
    val completed = remember { AtomicBoolean(false) }
    DisposableEffect(Unit) { onDispose { alive.set(false) } }
    fun finish(reward: Boolean) {
        if (alive.get() && completed.compareAndSet(false, true)) {
            if (reward) latestEarned.value() else latestClosed.value()
        }
    }
    LaunchedEffect(Unit) {
        if (activity == null || !YamoneAdMob.awaitReady(activity)) {
            finish(false)
            return@LaunchedEffect
        }
        withContext(Dispatchers.Main) {
            RewardedAd.load(
                AdRequest.Builder(YamoneAdMob.REWARDED_ID).build(),
                object : AdLoadCallback<RewardedAd> {
                    override fun onAdLoaded(ad: RewardedAd) {
                        if (!alive.get()) return
                        var earned = false
                        ad.adEventCallback = object : RewardedAdEventCallback {
                            override fun onAdDismissedFullScreenContent() {
                                finish(earned)
                            }

                            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                                Log.w("YamoneAdMob", "Rewarded show failed: ${error.message}")
                                finish(false)
                            }
                        }
                        ad.show(activity) {
                            earned = true
                        }
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        Log.w("YamoneAdMob", "Rewarded test ad failed: ${adError.message}")
                        finish(false)
                    }
                }
            )
        }
    }
}
