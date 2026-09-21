package com.yamone.spiritshift.ads

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.yamone.spiritshift.AppConfig

@Composable
fun AdaptiveBannerAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp.coerceAtLeast(320)
    val adView = remember(widthDp) {
        AdView(context).apply {
            adUnitId = AppConfig.BANNER_AD_UNIT_ID
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(adView) {
        onDispose { adView.destroy() }
    }
    AndroidView(
        factory = { adView },
        modifier = modifier.fillMaxWidth().height(60.dp),
    )
}

class RewardedAdController {
    private var rewardedAd: RewardedAd? = null
    private var loading = false

    fun load(context: Context) {
        if (loading || rewardedAd != null) return
        loading = true
        RewardedAd.load(
            context,
            AppConfig.REWARDED_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    loading = false
                    rewardedAd = ad
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    rewardedAd = null
                }
            },
        )
    }

    fun show(activity: Activity, onEarned: () -> Unit, onUnavailable: () -> Unit) {
        val ad = rewardedAd ?: run {
            load(activity)
            onUnavailable()
            return
        }
        rewardedAd = null
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                load(activity)
            }
        }
        ad.show(activity) {
            if (!earned) {
                earned = true
                onEarned()
            }
        }
    }
}
