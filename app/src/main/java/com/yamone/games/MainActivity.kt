package com.yamone.games

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import com.yamone.games.sudoku.ui.theme.YamoneSudokuTheme
import com.yamone.games.sudoku.ui.theme.yamonePrimarySoft

class MainActivity : ComponentActivity() {
    private lateinit var adRemovalBilling: GooglePlayAdRemovalBilling

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        adRemovalBilling = GooglePlayAdRemovalBilling(applicationContext) {
            // A promo code may be redeemed while the app is in the background. When Play reports
            // that remove_ads ownership changed, rebuild the Compose tree so every ad surface
            // immediately follows the new entitlement.
            runOnUiThread {
                if (!isFinishing && !isDestroyed) recreate()
            }
        }

        // UMP consent/privacy status is refreshed before the Mobile Ads SDK is initialized.
        YamonePrivacy.start(this) { canRequestAds ->
            if (canRequestAds) {
                YamoneAdMob.initialize(applicationContext)
            }
        }

        setContent {
            val prefs = remember { AppPreferences(applicationContext) }
            var themeMode by remember { mutableStateOf(prefs.themeMode()) }
            var mascot by remember { mutableStateOf(prefs.mascot()) }
            var nickname by remember { mutableStateOf(prefs.nickname()) }
            var nicknameConfigured by remember { mutableStateOf(prefs.hasNickname()) }
            val statusBarBackground = yamonePrimarySoft(themeMode)
            val navigationBarBackground = yamonePrimarySoft(themeMode)

            SideEffect {
                window.navigationBarColor = navigationBarBackground.toArgb()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = true
                    isAppearanceLightNavigationBars = true
                }
            }

            YamoneSudokuTheme(themeMode) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(statusBarBackground)
                ) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .windowInsetsBottomHeight(WindowInsets.navigationBars)
                            .background(navigationBarBackground)
                    )

                    Box(
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)
                            )
                    ) {
                        YamoneGamesApp(
                            themeMode = themeMode,
                            mascot = mascot,
                            nickname = nickname,
                            nicknameConfigured = nicknameConfigured,
                            onThemeChange = {
                                themeMode = it
                                prefs.setThemeMode(it)
                            },
                            onMascotChange = {
                                mascot = it
                                prefs.setMascot(it)
                            },
                            onNicknameChange = {
                                val saved = it.trim().take(AppPreferences.MAX_NICKNAME_LENGTH)
                                if (saved.isNotBlank()) {
                                    nickname = saved
                                    prefs.setNickname(saved)
                                    nicknameConfigured = true
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::adRemovalBilling.isInitialized) {
            adRemovalBilling.refresh()
        }
    }

    override fun onDestroy() {
        if (::adRemovalBilling.isInitialized) {
            adRemovalBilling.close()
        }
        super.onDestroy()
    }
}
