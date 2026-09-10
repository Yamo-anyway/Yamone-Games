package com.yamone.games

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
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
 * Development-only AdMob integration.
 * All IDs below are Google's official demo/test IDs and must be replaced before release.
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
        scope.launch {
            runCatching {
                MobileAds.initialize(
                    appContext,
                    InitializationConfig.Builder(SAMPLE_APP_ID).build()
                ) {
                    if (!ready.isCompleted) ready.complete(Unit)
                }
            }.onFailure {
                Log.e("YamoneAdMob", "Mobile Ads initialization failed", it)
                if (!ready.isCompleted) ready.complete(Unit)
            }
        }
    }

    suspend fun awaitReady(context: Context) {
        initialize(context)
        ready.await()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
internal fun AdMobTestBanner() {
    val context = LocalContext.current
    val activity = context.findActivity() ?: return
    val adView = remember(activity) { AdView(activity) }

    LaunchedEffect(adView) {
        YamoneAdMob.awaitReady(activity)
        withContext(Dispatchers.Main) {
            val request = BannerAdRequest.Builder(YamoneAdMob.BANNER_ID, AdSize.BANNER).build()
            adView.loadAd(
                request,
                object : AdLoadCallback<BannerAd> {
                    override fun onAdLoaded(ad: BannerAd) {
                        Log.d("YamoneAdMob", "Banner test ad loaded")
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        Log.w("YamoneAdMob", "Banner test ad failed: ${adError.message}")
                    }
                }
            )
        }
    }

    DisposableEffect(adView) {
        onDispose { adView.destroy() }
    }

    AndroidView(
        factory = {
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            adView
        },
        modifier = Modifier.fillMaxWidth().height(50.dp)
    )
}

@Composable
internal fun AdMobTestInterstitial(
    onDismissed: () -> Unit,
    onUnavailable: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val latestDismissed = remember(onDismissed) { onDismissed }
    val latestUnavailable = remember(onUnavailable) { onUnavailable }

    LaunchedEffect(Unit) {
        if (activity == null) {
            latestUnavailable()
            return@LaunchedEffect
        }
        YamoneAdMob.awaitReady(activity)
        withContext(Dispatchers.Main) {
            InterstitialAd.load(
                AdRequest.Builder(YamoneAdMob.INTERSTITIAL_ID).build(),
                object : AdLoadCallback<InterstitialAd> {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        ad.adEventCallback = object : InterstitialAdEventCallback {
                            override fun onAdDismissedFullScreenContent() {
                                latestDismissed()
                            }

                            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                                Log.w("YamoneAdMob", "Interstitial show failed: ${error.message}")
                                latestUnavailable()
                            }
                        }
                        ad.show(activity)
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        Log.w("YamoneAdMob", "Interstitial test ad failed: ${adError.message}")
                        latestUnavailable()
                    }
                }
            )
        }
    }
}

@Composable
internal fun AdMobTestRewarded(
    onRewardEarned: () -> Unit,
    onUnavailableOrClosed: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val latestEarned = remember(onRewardEarned) { onRewardEarned }
    val latestClosed = remember(onUnavailableOrClosed) { onUnavailableOrClosed }

    LaunchedEffect(Unit) {
        if (activity == null) {
            latestClosed()
            return@LaunchedEffect
        }
        YamoneAdMob.awaitReady(activity)
        withContext(Dispatchers.Main) {
            RewardedAd.load(
                AdRequest.Builder(YamoneAdMob.REWARDED_ID).build(),
                object : AdLoadCallback<RewardedAd> {
                    override fun onAdLoaded(ad: RewardedAd) {
                        var earned = false
                        ad.adEventCallback = object : RewardedAdEventCallback {
                            override fun onAdDismissedFullScreenContent() {
                                if (earned) latestEarned() else latestClosed()
                            }

                            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                                Log.w("YamoneAdMob", "Rewarded show failed: ${error.message}")
                                latestClosed()
                            }
                        }
                        ad.show(activity) {
                            earned = true
                        }
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        Log.w("YamoneAdMob", "Rewarded test ad failed: ${adError.message}")
                        latestClosed()
                    }
                }
            )
        }
    }
}
