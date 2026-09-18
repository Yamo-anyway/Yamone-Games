package com.yamone.games.arcadecore

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class ArcadeGameId(val storageKey: String) {
    ICE_JUMP("ice_jump_cm"),
    FISH_MUNCH("fish_munch"),
    FISH_MUNCH_TIME_ATTACK("fish_munch_time_attack"),
    SNOW_RUSH("snow_rush_shards_ms")
}

data class ArcadeRecord(
    val score: Int,
    val endedAtEpochMillis: Long,
    val nickname: String
)

class ArcadeRecordStorage(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        synchronized(LOCK) {
            if (!prefs.getBoolean("best_only_v305", false)) {
                val editor = prefs.edit()
                val oldIce = readKey("records_ice_jump").maxByOrNull { it.score }
                if (!prefs.contains("records_ice_jump_cm") && oldIce != null) {
                    editor.putString("records_ice_jump_cm", encode(listOf(oldIce.copy(score = legacyIceToCentimeters(oldIce.score)))))
                }
                prefs.all.keys.filter { it.startsWith("records_") }.forEach { key ->
                    val best = readKey(key).sortedWith(compareByDescending<ArcadeRecord> { it.score }.thenBy { it.endedAtEpochMillis }).take(1)
                    editor.putString(key, encode(best))
                }
                check(editor.putBoolean("best_only_v305", true).commit()) { "Unable to persist record migration" }
            }
        }
    }

    fun topRecords(game: ArcadeGameId): List<ArcadeRecord> = read(game)
        .sortedWith(compareByDescending<ArcadeRecord> { it.score }.thenBy { it.endedAtEpochMillis })
        .take(MAX_RECORDS)

    fun addRecord(
        game: ArcadeGameId,
        score: Int,
        endedAtEpochMillis: Long = System.currentTimeMillis(),
        nickname: String
    ): ArcadeRecord {
        val normalizedNickname = nickname.trim().ifBlank { DEFAULT_NICKNAME }
        val record = ArcadeRecord(
            score = score.coerceAtLeast(0),
            endedAtEpochMillis = endedAtEpochMillis,
            nickname = normalizedNickname
        )

        synchronized(LOCK) {
            val best = (read(game) + record).sortedWith(compareByDescending<ArcadeRecord> { it.score }.thenBy { it.endedAtEpochMillis }).take(1)
            if (best != read(game)) write(game, best)
        }
        return record
    }

    fun isTopRecord(game: ArcadeGameId, record: ArcadeRecord): Boolean =
        topRecords(game).any {
            it.score == record.score &&
                it.endedAtEpochMillis == record.endedAtEpochMillis &&
                it.nickname == record.nickname
        }

    fun deleteSelected(games: Set<ArcadeGameId>): Boolean {
        if (games.isEmpty()) return true
        return synchronized(LOCK) {
            val editor = prefs.edit()
            games.forEach { game ->
                when (game) {
                    ArcadeGameId.ICE_JUMP -> {
                        editor.remove("records_ice_jump_cm")
                        editor.remove("records_ice_jump")
                    }
                    ArcadeGameId.FISH_MUNCH,
                    ArcadeGameId.FISH_MUNCH_TIME_ATTACK -> {
                        editor.remove("records_fish_munch")
                        editor.remove("records_fish_munch_time_attack")
                    }
                    ArcadeGameId.SNOW_RUSH -> {
                        editor.remove("records_snow_rush_shards_ms")
                        editor.remove("records_snow_rush")
                    }
                }
            }
            editor.commit()
        }
    }

    // Old whole-second records are displayed separately, never reinterpreted as milliseconds.
    fun legacySnowRecords(): List<ArcadeRecord> = readKey("records_snow_rush")
        .sortedWith(compareByDescending<ArcadeRecord> { it.score }.thenBy { it.endedAtEpochMillis })
        .take(MAX_RECORDS)

    private fun read(game: ArcadeGameId): List<ArcadeRecord> = readKey(key(game))

    private fun readKey(storageKey: String): List<ArcadeRecord> {
        val raw = prefs.getString(storageKey, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        ArcadeRecord(
                            score = item.optInt(FIELD_SCORE, 0),
                            endedAtEpochMillis = item.optLong(FIELD_ENDED_AT, 0L),
                            nickname = item.optString(FIELD_NICKNAME, DEFAULT_NICKNAME)
                                .trim()
                                .ifBlank { DEFAULT_NICKNAME }
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encode(records: List<ArcadeRecord>): String {
        val array = JSONArray()
        records.take(MAX_RECORDS).forEach { record ->
            array.put(
                JSONObject()
                    .put(FIELD_SCORE, record.score)
                    .put(FIELD_ENDED_AT, record.endedAtEpochMillis)
                    .put(FIELD_NICKNAME, record.nickname)
            )
        }
        return array.toString()
    }
    private fun write(game: ArcadeGameId, records: List<ArcadeRecord>) {
        check(prefs.edit().putString(key(game), encode(records)).commit()) { "Unable to save personal best" }
    }

    private fun key(game: ArcadeGameId): String = "records_${game.storageKey}"

    companion object {
        private val LOCK = Any()
        val ACTIVE_GAMES = listOf(ArcadeGameId.ICE_JUMP, ArcadeGameId.FISH_MUNCH, ArcadeGameId.SNOW_RUSH)
        const val MAX_RECORDS = 1
        const val DEFAULT_NICKNAME = "야모네 플레이어"

        private const val PREFS_NAME = "yamone_arcade_records"
        private const val FIELD_SCORE = "score"
        private const val FIELD_ENDED_AT = "ended_at"
        private const val FIELD_NICKNAME = "nickname"
    }
}
