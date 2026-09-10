from pathlib import Path

p = Path('app/src/main/java/com/yamone/games/YamoneGamesApp.kt')
s = p.read_text()

s = s.replace('DevelopmentBannerAd(themeMode)', 'AdMobTestBanner()')

old_interstitial = '''        if (showInterstitialTestAd) {
            DevelopmentInterstitialAdDialog(
                themeMode = themeMode,
                onComplete = {
                    adAccessStore.addMinutes(30)
                    adRevision++
                    showInterstitialTestAd = false
                    val target = pendingGameName?.let { runCatching { AppScreen.valueOf(it) }.getOrNull() }
                    pendingGameName = null
                    target?.let { screenName = it.name }
                }
            )
        }
'''
new_interstitial = '''        if (showInterstitialTestAd) {
            AdMobTestInterstitial(
                onDismissed = {
                    adAccessStore.addMinutes(30)
                    adRevision++
                    showInterstitialTestAd = false
                    val target = pendingGameName?.let { runCatching { AppScreen.valueOf(it) }.getOrNull() }
                    pendingGameName = null
                    target?.let { screenName = it.name }
                },
                onUnavailable = {
                    adAccessStore.grantLoadFailureMinutes()
                    adRevision++
                    showInterstitialTestAd = false
                    val target = pendingGameName?.let { runCatching { AppScreen.valueOf(it) }.getOrNull() }
                    pendingGameName = null
                    target?.let { screenName = it.name }
                }
            )
        }
'''
if old_interstitial not in s:
    raise SystemExit('Interstitial block not found')
s = s.replace(old_interstitial, new_interstitial, 1)

old_rewarded = '''        if (showRewardedTestAd) {
            DevelopmentRewardedAdDialog(
                themeMode = themeMode,
                onComplete = {
                    adAccessStore.addMinutes(30)
                    adRevision++
                    showRewardedTestAd = false
                    showAdDetails = true
                },
                onCancel = {
                    showRewardedTestAd = false
                    showAdDetails = true
                }
            )
        }
'''
new_rewarded = '''        if (showRewardedTestAd) {
            AdMobTestRewarded(
                onRewardEarned = {
                    adAccessStore.addMinutes(30)
                    adRevision++
                    showRewardedTestAd = false
                    showAdDetails = true
                },
                onUnavailableOrClosed = {
                    showRewardedTestAd = false
                    showAdDetails = true
                }
            )
        }
'''
if old_rewarded not in s:
    raise SystemExit('Rewarded block not found')
s = s.replace(old_rewarded, new_rewarded, 1)

p.write_text(s)
