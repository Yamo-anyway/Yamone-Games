package com.yamone.games

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
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
            val systemDark = isSystemInDarkTheme()

            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView)
                    .isAppearanceLightStatusBars = !systemDark
            }

            YamoneSudokuTheme(themeMode) {
                Box(Modifier.fillMaxSize()) {
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

                    // Android 15+는 상태바가 투명한 edge-to-edge를 강제하므로,
                    // 게임/앱 배경 대신 사용자의 시스템 다크/라이트 상태에 맞는
                    // 독립적인 상태바 보호 영역을 맨 위에 둔다.
                    Spacer(
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .windowInsetsTopHeight(WindowInsets.statusBars)
                            .background(if (systemDark) Color.Black else Color.White)
                    )
                }
            }
        }
    }
}
