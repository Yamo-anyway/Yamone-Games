package com.yamone.games

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.yamone.games.sudoku.ui.theme.YamoneSudokuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs = remember { AppPreferences(applicationContext) }
            var themeMode by remember { mutableStateOf(prefs.themeMode()) }
            var mascot by remember { mutableStateOf(prefs.mascot()) }
            var nickname by remember { mutableStateOf(prefs.nickname()) }

            YamoneSudokuTheme(themeMode) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(
                            WindowInsets.statusBars.only(WindowInsetsSides.Top)
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
