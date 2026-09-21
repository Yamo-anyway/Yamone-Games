package com.yamone.spiritshift

object AppConfig {
    const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"
    const val REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

    val rankingBaseUrl: String
        get() = BuildConfig.RANKING_BASE_URL.trim().trimEnd('/')
}
