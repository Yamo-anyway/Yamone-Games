package com.yamone.games

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamone.games.sudoku.ui.theme.YamoneInk
import com.yamone.games.sudoku.ui.theme.YamoneMascotIcon
import com.yamone.games.sudoku.ui.theme.YamoneMuted
import com.yamone.games.sudoku.ui.theme.YamoneSudokuTheme
import com.yamone.games.sudoku.ui.theme.yamonePrimary
import com.yamone.games.sudoku.ui.theme.yamonePrimaryDark
import com.yamone.games.sudoku.ui.theme.yamonePrimarySoft
import com.yamone.games.winterride.WinterRideScreen

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
            var winterRideOpen by remember { mutableStateOf(false) }
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
                    if (winterRideOpen) {
                        BackHandler { winterRideOpen = false }
                        WinterRideScreen(
                            onBack = { winterRideOpen = false },
                            primary = yamonePrimary(themeMode),
                            primaryDark = yamonePrimaryDark(themeMode),
                            soft = yamonePrimarySoft(themeMode),
                            ink = YamoneInk,
                            muted = YamoneMuted,
                            mascotContent = { size ->
                                YamoneMascotIcon(
                                    mascot = mascot,
                                    size = size,
                                    accent = yamonePrimary(themeMode)
                                )
                            }
                        )
                    } else {
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

                        Button(
                            onClick = { winterRideOpen = true },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 14.dp, bottom = 88.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = yamonePrimary(themeMode),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 13.dp, vertical = 9.dp)
                        ) {
                            Text(
                                "⛷  스키·보드",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
}
