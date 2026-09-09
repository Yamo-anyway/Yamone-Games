package com.yamone.games

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.yamone.games.arcadecore.ArcadeGameId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

internal data class OnlineRankingRow(
    val rank: Int,
    val nickname: String,
    val score: Int,
    val isMe: Boolean = false
)

internal data class OnlineRankingMe(
    val rank: Int,
    val nickname: String,
    val score: Int
)

internal data class OnlineRankingData(
    val game: ArcadeGameId,
    val totalPlayers: Int,
    val top: List<OnlineRankingRow>,
    val me: OnlineRankingMe?,
    val nearby: List<OnlineRankingRow>
)

internal sealed interface OnlineRankingLoadResult {
    data class Success(val data: OnlineRankingData) : OnlineRankingLoadResult
    data object Disabled : OnlineRankingLoadResult
    data object Offline : OnlineRankingLoadResult
    data object ServerUnavailable : OnlineRankingLoadResult
}

internal sealed interface OnlineRankingDeleteResult {
    data object Success : OnlineRankingDeleteResult
    data object Offline : OnlineRankingDeleteResult
    data object ServerUnavailable : OnlineRankingDeleteResult
}

internal data class PendingOnlineRanking(
    val game: ArcadeGameId,
    val score: Int,
    val nickname: String
)

internal class OnlineRankingStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val playerIdFile = File(context.noBackupFilesDir, PLAYER_ID_FILE)

    fun enabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (!enabled) clearAllPending()
    }

    fun playerId(): String {
        runCatching { playerIdFile.readText().trim() }
            .getOrNull()
            ?.takeIf { it.length >= 16 }
            ?.let { return it }

        val generated = UUID.randomUUID().toString()
        runCatching {
            playerIdFile.parentFile?.mkdirs()
            playerIdFile.writeText(generated)
        }.getOrElse {
            throw IOException("Unable to persist ranking player id", it)
        }
        return generated
    }

    fun queueBest(game: ArcadeGameId, score: Int, nickname: String) {
        val normalizedScore = score.coerceAtLeast(0)
        val current = prefs.getInt(pendingScoreKey(game), -1)
        if (normalizedScore <= current) return

        prefs.edit()
            .putInt(pendingScoreKey(game), normalizedScore)
            .putString(pendingNicknameKey(game), nickname.trim().ifBlank { DEFAULT_NICKNAME })
            .apply()
    }

    fun pending(): List<PendingOnlineRanking> = ArcadeGameId.entries.mapNotNull { game ->
        val score = prefs.getInt(pendingScoreKey(game), -1)
        if (score < 0) return@mapNotNull null
        PendingOnlineRanking(
            game = game,
            score = score,
            nickname = prefs.getString(pendingNicknameKey(game), DEFAULT_NICKNAME)
                .orEmpty()
                .trim()
                .ifBlank { DEFAULT_NICKNAME }
        )
    }

    fun clearPending(game: ArcadeGameId) {
        prefs.edit()
            .remove(pendingScoreKey(game))
            .remove(pendingNicknameKey(game))
            .apply()
    }

    fun clearAllPending() {
        val editor = prefs.edit()
        ArcadeGameId.entries.forEach { game ->
            editor.remove(pendingScoreKey(game))
            editor.remove(pendingNicknameKey(game))
        }
        editor.apply()
    }

    private fun pendingScoreKey(game: ArcadeGameId): String = "pending_score_${game.storageKey}"
    private fun pendingNicknameKey(game: ArcadeGameId): String = "pending_nickname_${game.storageKey}"

    private companion object {
        const val PREFS_NAME = "yamone_online_ranking"
        const val KEY_ENABLED = "enabled"
        const val PLAYER_ID_FILE = "online_ranking_player_id"
        const val DEFAULT_NICKNAME = "야모네 플레이어"
    }
}

internal class OnlineRankingRepository(context: Context) {
    private val appContext = context.applicationContext
    private val store = OnlineRankingStore(appContext)
    private val client = OnlineRankingClient()

    fun enabled(): Boolean = store.enabled()

    fun setEnabled(enabled: Boolean) {
        store.setEnabled(enabled)
    }

    suspend fun submitNewBest(game: ArcadeGameId, score: Int, nickname: String) {
        if (!store.enabled()) return
        store.queueBest(game, score, nickname)
        flushPending(game)
    }

    suspend fun flushPending() {
        if (!store.enabled() || !hasUsableNetwork(appContext)) return
        store.pending().forEach { pending -> flushPending(pending.game) }
    }

    private suspend fun flushPending(game: ArcadeGameId) {
        if (!store.enabled() || !hasUsableNetwork(appContext)) return
        val pending = store.pending().firstOrNull { it.game == game } ?: return
        runCatching {
            client.submit(
                playerId = store.playerId(),
                nickname = pending.nickname,
                game = pending.game,
                score = pending.score
            )
        }.onSuccess {
            store.clearPending(game)
        }
    }

    suspend fun load(game: ArcadeGameId): OnlineRankingLoadResult {
        if (!store.enabled()) return OnlineRankingLoadResult.Disabled
        if (!hasUsableNetwork(appContext)) return OnlineRankingLoadResult.Offline

        return try {
            OnlineRankingLoadResult.Success(
                client.load(
                    playerId = store.playerId(),
                    game = game
                )
            )
        } catch (_: Exception) {
            OnlineRankingLoadResult.ServerUnavailable
        }
    }

    suspend fun deleteOnlineRecord(game: ArcadeGameId): OnlineRankingDeleteResult {
        if (!hasUsableNetwork(appContext)) return OnlineRankingDeleteResult.Offline
        return try {
            client.deletePlayerGame(
                playerId = store.playerId(),
                game = game
            )
            store.clearPending(game)
            OnlineRankingDeleteResult.Success
        } catch (_: Exception) {
            OnlineRankingDeleteResult.ServerUnavailable
        }
    }
}

private class OnlineRankingClient {
    suspend fun submit(
        playerId: String,
        nickname: String,
        game: ArcadeGameId,
        score: Int
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("playerId", playerId)
            .put("nickname", nickname)
            .put("gameId", game.serverGameId())
            .put("modeId", game.serverModeId())
            .put("score", score)

        request(
            method = "POST",
            path = "/v1/ranking/submit",
            body = body
        )
    }

    suspend fun load(playerId: String, game: ArcadeGameId): OnlineRankingData = withContext(Dispatchers.IO) {
        val encodedPlayer = URLEncoder.encode(playerId, Charsets.UTF_8.name())
        val response = request(
            method = "GET",
            path = "/v1/ranking/${game.serverGameId()}/${game.serverModeId()}?playerId=$encodedPlayer"
        )

        val meObject = response.optJSONObject("me")
        val me = meObject?.let {
            OnlineRankingMe(
                rank = it.optInt("rank"),
                nickname = it.optString("nickname"),
                score = it.optInt("score")
            )
        }

        val topArray = response.optJSONArray("top")
        val top = buildList {
            if (topArray != null) {
                for (index in 0 until topArray.length()) {
                    val row = topArray.optJSONObject(index) ?: continue
                    val rank = row.optInt("rank")
                    add(
                        OnlineRankingRow(
                            rank = rank,
                            nickname = row.optString("nickname"),
                            score = row.optInt("score"),
                            isMe = me?.rank == rank
                        )
                    )
                }
            }
        }

        val nearbyArray = response.optJSONArray("nearby")
        val nearby = buildList {
            if (nearbyArray != null) {
                for (index in 0 until nearbyArray.length()) {
                    val row = nearbyArray.optJSONObject(index) ?: continue
                    add(
                        OnlineRankingRow(
                            rank = row.optInt("rank"),
                            nickname = row.optString("nickname"),
                            score = row.optInt("score"),
                            isMe = row.optBoolean("isMe", false)
                        )
                    )
                }
            }
        }

        OnlineRankingData(
            game = game,
            totalPlayers = response.optInt("totalPlayers", 0),
            top = top,
            me = me,
            nearby = nearby
        )
    }

    suspend fun deletePlayerGame(playerId: String, game: ArcadeGameId) = withContext(Dispatchers.IO) {
        request(
            method = "DELETE",
            path = "/v1/ranking/player",
            body = JSONObject()
                .put("playerId", playerId)
                .put("gameId", game.serverGameId())
                .put("modeId", game.serverModeId())
        )
    }

    private fun request(
        method: String,
        path: String,
        body: JSONObject? = null
    ): JSONObject {
        val connection = (URL(API_BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 5_000
            readTimeout = 5_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (body != null) doOutput = true
        }

        try {
            if (body != null) {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(body.toString())
                }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (status !in 200..299) {
                throw IOException("Ranking API HTTP $status: $text")
            }

            return if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val API_BASE = "https://yamone-games-ranking-api.yamone0479.workers.dev"
    }
}

private fun ArcadeGameId.serverGameId(): String = when (this) {
    ArcadeGameId.ICE_JUMP -> "ice_jump"
    ArcadeGameId.FISH_MUNCH,
    ArcadeGameId.FISH_MUNCH_TIME_ATTACK -> "fish_munch"
    ArcadeGameId.SNOW_RUSH -> "snow_rush"
}

private fun ArcadeGameId.serverModeId(): String = when (this) {
    ArcadeGameId.FISH_MUNCH_TIME_ATTACK -> "time_attack"
    else -> "normal"
}

internal fun hasUsableNetwork(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
