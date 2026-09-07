package com.yamone.games

import android.content.Context
import com.yamone.games.sudoku.ui.theme.YamoneMascot
import com.yamone.games.sudoku.ui.theme.YamoneThemeMode

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("yamone_games_settings", Context.MODE_PRIVATE)

    fun themeMode(): YamoneThemeMode = runCatching {
        YamoneThemeMode.valueOf(prefs.getString("theme_mode", YamoneThemeMode.MINT.name)!!)
    }.getOrDefault(YamoneThemeMode.MINT)

    fun mascot(): YamoneMascot = runCatching {
        YamoneMascot.valueOf(prefs.getString("mascot", YamoneMascot.SEAL.name)!!)
    }.getOrDefault(YamoneMascot.SEAL)

    fun setThemeMode(mode: YamoneThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun setMascot(mascot: YamoneMascot) {
        prefs.edit().putString("mascot", mascot.name).apply()
    }
}
