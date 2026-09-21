package com.yamone.spiritshift.data

import com.yamone.spiritshift.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class RankingEntry(
    val rank: Int,
    val nickname: String,
    val score: Int,
    val playTimeMs: Long,
    val playerId: String = "",
)

class RankingRepository {
    val isConfigured: Boolean get() = AppConfig.rankingBaseUrl.isNotBlank()

    suspend fun reserveNickname(playerId: String, nickname: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext Result.success(Unit)
        runCatching {
            requestJson(
                path = "/api/profile",
                method = "POST",
                body = JSONObject().put("playerId", playerId).put("nickname", nickname),
            )
        }.map { Unit }
    }

    suspend fun submitBest(playerId: String, nickname: String, score: Int, playTimeMs: Long): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext Result.success(Unit)
        runCatching {
            requestJson(
                path = "/api/score",
                method = "POST",
                body = JSONObject()
                    .put("playerId", playerId)
                    .put("nickname", nickname)
                    .put("score", score)
                    .put("playTimeMs", playTimeMs),
            )
        }.map { Unit }
    }

    suspend fun leaderboard(limit: Int = 100): Result<List<RankingEntry>> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext Result.success(emptyList())
        runCatching {
            val json = requestJson("/api/leaderboard?limit=$limit", "GET", null)
            val array = json.optJSONArray("items") ?: JSONArray()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        RankingEntry(
                            rank = i + 1,
                            nickname = item.getString("nickname"),
                            score = item.getInt("score"),
                            playTimeMs = item.optLong("playTimeMs", 0L),
                            playerId = item.optString("playerId", ""),
                        )
                    )
                }
            }
        }
    }

    private fun requestJson(path: String, method: String, body: JSONObject?): JSONObject {
        val connection = (URL(AppConfig.rankingBaseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 7_000
            readTimeout = 7_000
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
        connection.disconnect()
        val json = if (text.isBlank()) JSONObject() else JSONObject(text)
        if (code !in 200..299) {
            throw IllegalStateException(json.optString("error", "서버 오류 ($code)"))
        }
        return json
    }
}
