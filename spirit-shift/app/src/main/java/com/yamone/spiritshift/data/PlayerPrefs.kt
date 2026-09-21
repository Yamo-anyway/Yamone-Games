package com.yamone.spiritshift.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class PlayerPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("spirit_shift", Context.MODE_PRIVATE)

    val playerId: String
        get() {
            val saved = prefs.getString(KEY_PLAYER_ID, null)
            if (!saved.isNullOrBlank()) return saved
            val id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_PLAYER_ID, id).apply()
            return id
        }

    var nickname: String
        get() = prefs.getString(KEY_NICKNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_NICKNAME, value).apply()

    var bestScore: Int
        get() = prefs.getInt(KEY_BEST_SCORE, 0)
        set(value) = prefs.edit().putInt(KEY_BEST_SCORE, value).apply()

    var bestTimeMs: Long
        get() = prefs.getLong(KEY_BEST_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_BEST_TIME, value).apply()

    var freeUsed: Int
        get() = prefs.getInt(KEY_FREE_USED, 0)
        set(value) = prefs.edit().putInt(KEY_FREE_USED, value).apply()

    var bonusPlays: Int
        get() = prefs.getInt(KEY_BONUS_PLAYS, 0)
        set(value) = prefs.edit().putInt(KEY_BONUS_PLAYS, value).apply()

    fun refreshDailyIfNeeded() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val last = prefs.getString(KEY_DAILY_DATE, "")
        if (last != today) {
            prefs.edit().putString(KEY_DAILY_DATE, today).putInt(KEY_FREE_USED, 0).apply()
        }
    }

    companion object {
        private const val KEY_PLAYER_ID = "player_id"
        private const val KEY_NICKNAME = "nickname"
        private const val KEY_BEST_SCORE = "best_score"
        private const val KEY_BEST_TIME = "best_time_ms"
        private const val KEY_DAILY_DATE = "daily_date"
        private const val KEY_FREE_USED = "free_used"
        private const val KEY_BONUS_PLAYS = "bonus_plays"
    }
}
