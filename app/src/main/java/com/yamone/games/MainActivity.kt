package com.yamone.games

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
import androidx.core.view.WindowCompat
import com.yamone.games.sudoku.ui.theme.YamoneSudokuTheme
import com.yamone.games.sudoku.ui.theme.yamonePrimarySoft

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            val statusBarBackground = yamonePrimarySoft(themeMode)
            val navigationBarBackground = yamonePrimarySoft(themeMode)

            // The Android system bars are part of the Yamone frame too.
            // Their backgrounds follow the selected mint/pink theme, while icons stay dark
            // because both Yamone soft colors are intentionally light.
            SideEffect {
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
                    // On gesture-navigation phones Android draws the gesture handle over the
                    // app content. Paint that inset explicitly so the bottom system area follows
                    // the selected Yamone theme color instead of falling back to white/black.
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
                            onThemeChange = {
                                themeMode = it
                                prefs.setThemeMode(it)
                            },
                            onMascotChange = {
                                mascot = it
                                prefs.setMascot(it)
                            },
                            onNicknameChange = {
                                nickname = it.take(AppPreferences.MAX_NICKNAME_LENGTH)
                                prefs.setNickname(nickname)
                            }
                        )
                    }
                }
            }
        }
    }
}
