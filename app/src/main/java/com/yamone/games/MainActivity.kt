package com.yamone.games

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.yamone.games.sudoku.ui.theme.YamoneSudokuTheme

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
            val systemBarBackground = if (isSystemInDarkTheme()) Color.Black else Color.White

            YamoneSudokuTheme(themeMode) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(systemBarBackground)
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
