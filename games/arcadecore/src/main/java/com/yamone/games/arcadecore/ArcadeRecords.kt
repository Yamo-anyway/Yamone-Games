package com.yamone.games.arcadecore

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class ArcadeGameId(val storageKey: String) {
    ICE_JUMP("ice_jump"),
    FISH_MUNCH("fish_munch"),
    SNOW_RUSH("snow_rush")
}

data class ArcadeRecord(
    val score: Int,
    val endedAtEpochMillis: Long,
    val nickname: String
)

class ArcadeRecordStorage(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun topRecords(game: ArcadeGameId): List<ArcadeRecord> = read(game)
        .sortedWith(compareByDescending<ArcadeRecord> { it.score }.thenByDescending { it.endedAtEpochMillis })
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

        val updated = (read(game) + record)
            .sortedWith(compareByDescending<ArcadeRecord> { it.score }.thenByDescending { it.endedAtEpochMillis })
            .take(MAX_RECORDS)

        write(game, updated)
        return record
    }

    fun isTopRecord(game: ArcadeGameId, record: ArcadeRecord): Boolean =
        topRecords(game).any {
            it.score == record.score &&
                it.endedAtEpochMillis == record.endedAtEpochMillis &&
                it.nickname == record.nickname
        }

    private fun read(game: ArcadeGameId): List<ArcadeRecord> {
        val raw = prefs.getString(key(game), null) ?: return emptyList()
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

    private fun write(game: ArcadeGameId, records: List<ArcadeRecord>) {
        val array = JSONArray()
        records.take(MAX_RECORDS).forEach { record ->
            array.put(
                JSONObject()
                    .put(FIELD_SCORE, record.score)
                    .put(FIELD_ENDED_AT, record.endedAtEpochMillis)
                    .put(FIELD_NICKNAME, record.nickname)
            )
        }
        prefs.edit().putString(key(game), array.toString()).apply()
    }

    private fun key(game: ArcadeGameId): String = "records_${game.storageKey}"

    companion object {
        const val MAX_RECORDS = 5
        const val DEFAULT_NICKNAME = "야모네 플레이어"

        private const val PREFS_NAME = "yamone_arcade_records"
        private const val FIELD_SCORE = "score"
        private const val FIELD_ENDED_AT = "ended_at"
        private const val FIELD_NICKNAME = "nickname"
    }
}
