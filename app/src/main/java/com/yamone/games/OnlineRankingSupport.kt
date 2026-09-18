package com.yamone.games

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.yamone.games.arcadecore.ArcadeGameId
import com.yamone.games.arcadecore.ArcadeRecordStorage
import com.yamone.games.arcadecore.legacyIceToCentimeters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID

internal data class OnlineRankingRow(val rank: Int, val nickname: String, val countryCode: String, val score: Int, val isMe: Boolean = false)
internal data class OnlineRankingMe(val rank: Int, val nickname: String, val countryCode: String, val score: Int)
internal data class OnlineRankingData(val board: RankingBoard, val totalPlayers: Int, val top: List<OnlineRankingRow>, val me: OnlineRankingMe?, val nearby: List<OnlineRankingRow>) {
    val game: ArcadeGameId get() = requireNotNull(board.arcade) { "Legacy arcade-only renderer cannot render Sudoku" }
}
internal sealed interface OnlineRankingLoadResult {
    data class Success(val data: OnlineRankingData) : OnlineRankingLoadResult
    data object Disabled : OnlineRankingLoadResult // kept for binary/source compatibility of old screens only
    data object Offline : OnlineRankingLoadResult
    data object ServerUnavailable : OnlineRankingLoadResult
    data object ServerUpdateRequired : OnlineRankingLoadResult
}
internal sealed interface OnlineRankingDeleteResult {
    data object Success : OnlineRankingDeleteResult
    data object Offline : OnlineRankingDeleteResult
    data object ServerUnavailable : OnlineRankingDeleteResult
}
internal data class PendingOnlineRanking(val board: RankingBoard, val score: Int, val nickname: String, val revision: Long)

/** Durable, per-board coalescing outbox. Never cleared by a failed or cancelled request. */
internal class OnlineRankingStore(context: Context) {
    private val prefs = context.getSharedPreferences("yamone_online_ranking", Context.MODE_PRIVATE)
    private val playerIdFile = File(context.noBackupFilesDir, "online_ranking_player_id")
    init {
        synchronized(LOCK) {
            if (!prefs.getBoolean("automatic_v305", false)) {
                // Retire the old OFF/delete-on-OFF policy without discarding queued bests.
                val editor = prefs.edit().putBoolean("enabled", true)
                    .putBoolean("delete_all_pending", false).putBoolean("publish_all_pending", false)
                check(editor.commit())
                listOf(RankingBoard.FISH to "fish_munch", RankingBoard.SNOW to "snow_rush_shards_ms", RankingBoard.ICE to "ice_jump").forEach { (board, key) ->
                    val score = prefs.getInt("pending_score_$key", -1)
                    if (score >= 0) queueBest(board, if (board == RankingBoard.ICE) legacyIceToCentimeters(score) else score,
                        prefs.getString("pending_nickname_$key", ArcadeRecordStorage.DEFAULT_NICKNAME).orEmpty())
                }
                val cleanup = prefs.edit()
                prefs.all.keys.filter { it.startsWith("pending_score_") || it.startsWith("pending_nickname_") }.forEach { cleanup.remove(it) }
                check(cleanup.putBoolean("automatic_v305", true).commit())
            }
        }
    }
    fun playerId(): String = synchronized(LOCK) {
        if (playerIdFile.exists()) {
            val existing = playerIdFile.readText().trim()
            if (existing.length in 16..128) return@synchronized existing
            throw IOException("Existing ranking identity is invalid; refusing to create a second player")
        }
        val id = UUID.randomUUID().toString()
        playerIdFile.parentFile?.mkdirs()
        val temporary = File(playerIdFile.parentFile, "ranking-id.tmp")
        temporary.writeText(id)
        if (!temporary.renameTo(playerIdFile)) throw IOException("Unable to save ranking identity")
        id
    }
    fun queueBest(board: RankingBoard, score: Int, nickname: String): Boolean = synchronized(LOCK) {
        if (score < 0 || (board.minimumWins && score == 0)) return@synchronized false
        val name = nickname.trim().ifBlank { ArcadeRecordStorage.DEFAULT_NICKNAME }.take(20)
        val previous = pendingUnlocked(board)
        val candidate = if (previous != null && board.better(previous.score, score)) previous.score else score
        if (previous != null && previous.score == candidate && previous.nickname == name) return@synchronized false
        val ackScore = prefs.getInt("ack_score_${board.key}", -1)
        val ackName = prefs.getString("ack_name_${board.key}", "")
        if (previous == null && ackScore == candidate && ackName == name) return@synchronized false
        val revision = prefs.getLong("serial", 0) + 1
        val json = JSONObject().put("score", candidate).put("nickname", name).put("revision", revision)
        check(prefs.edit().putLong("serial", revision).putString("outbox_${board.key}", json.toString()).commit())
        true
    }
    private fun pendingUnlocked(board: RankingBoard): PendingOnlineRanking? {
        val raw = prefs.getString("outbox_${board.key}", null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            PendingOnlineRanking(board, o.getInt("score"), o.getString("nickname"), o.getLong("revision"))
        }.getOrNull()
    }
    fun pending(): List<PendingOnlineRanking> = synchronized(LOCK) { RankingBoard.entries.mapNotNull(::pendingUnlocked) }
    fun acknowledge(sent: PendingOnlineRanking) = synchronized(LOCK) {
        val edit = prefs.edit().putInt("ack_score_${sent.board.key}", sent.score).putString("ack_name_${sent.board.key}", sent.nickname)
            .putLong("last_success", System.currentTimeMillis()).remove("last_error")
        // A newer score/nickname queued during HTTP must survive this acknowledgement.
        if (pendingUnlocked(sent.board)?.revision == sent.revision) edit.remove("outbox_${sent.board.key}")
        check(edit.commit())
    }
    fun error(message: String) { prefs.edit().putString("last_error", message.take(120)).apply() }
    fun status(): String {
        val count = pending().size
        return if (count > 0) "전송 대기 ${count}개 · 연결되면 자동 재시도" else if (prefs.contains("last_success")) "최고기록 동기화 완료" else "새 최고기록부터 자동 전송해요"
    }
    fun forget(boards: Set<RankingBoard>) = synchronized(LOCK) {
        val editor = prefs.edit()
        boards.forEach { b -> editor.remove("outbox_${b.key}").remove("ack_score_${b.key}").remove("ack_name_${b.key}") }
        check(editor.commit())
    }
    companion object { private val LOCK = Any() }
}

internal class OnlineRankingRepository(context: Context) {
    private val appContext = context.applicationContext
    private val store = OnlineRankingStore(appContext)
    private val localRecords = ArcadeRecordStorage(appContext)
    private val client = OnlineRankingClient(endpoint(appContext))
    fun enabled(): Boolean = true
    fun setEnabled(enabled: Boolean) { /* automatic publishing has no switch in v0.3.05 */ }
    fun ensurePlayerId(): String = store.playerId()
    fun status(): String = store.status()
    fun hasPending(): Boolean = store.pending().isNotEmpty()
    private fun stageLocalBests(nickname: String) {
        RankingBoard.entries.forEach { board ->
            val score = if (board.arcade != null) localRecords.topRecords(board.arcade).firstOrNull()?.score else
                appContext.getSharedPreferences("yamone_sudoku_game", Context.MODE_PRIVATE).getInt("best_${board.modeId}", 0).takeIf { it > 0 }
            if (score != null) store.queueBest(board, score, nickname)
        }
    }
    suspend fun onLocalBestChanged(game: ArcadeGameId, score: Int, nickname: String) {
        RankingBoard.forGame(game)?.let { store.queueBest(it, score, nickname) }
        RankingSyncScheduler.schedule(appContext)
        syncRankingState(nickname)
    }
    suspend fun syncNickname(nickname: String) = syncRankingState(nickname)
    suspend fun syncRankingState(nickname: String) = SYNC_MUTEX.withLock {
        stageLocalBests(nickname)
        flushUnlocked()
    }
    suspend fun flushPending() = SYNC_MUTEX.withLock { flushUnlocked() }
    private suspend fun flushUnlocked() {
        if (!hasUsableNetwork(appContext)) return
        for (pending in store.pending()) {
            try {
                client.submit(store.playerId(), pending.nickname, deviceCountryCode(), pending.board, pending.score)
                store.acknowledge(pending)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                store.error(if (e is RankingApiException && e.code == "INVALID_GAME_MODE") "순위 서버 업데이트 대기" else "네트워크 또는 서버 응답 대기")
            }
        }
    }
    suspend fun load(game: ArcadeGameId): OnlineRankingLoadResult = RankingBoard.forGame(game)?.let { load(it) } ?: OnlineRankingLoadResult.Disabled
    suspend fun load(board: RankingBoard): OnlineRankingLoadResult {
        if (!hasUsableNetwork(appContext)) return OnlineRankingLoadResult.Offline
        return try { OnlineRankingLoadResult.Success(client.load(store.playerId(), board)) }
        catch (e: CancellationException) { throw e }
        catch (e: RankingApiException) { if (e.code == "INVALID_GAME_MODE") OnlineRankingLoadResult.ServerUpdateRequired else OnlineRankingLoadResult.ServerUnavailable }
        catch (_: Exception) { OnlineRankingLoadResult.ServerUnavailable }
    }
    suspend fun deleteSelectedOnlineRecords(games: Set<ArcadeGameId>): OnlineRankingDeleteResult = deleteBoards(games.mapNotNull(RankingBoard::forGame).toSet())
    suspend fun deleteBoards(boards: Set<RankingBoard>): OnlineRankingDeleteResult = SYNC_MUTEX.withLock {
        if (boards.isEmpty()) return@withLock OnlineRankingDeleteResult.Success
        if (!hasUsableNetwork(appContext)) return@withLock OnlineRankingDeleteResult.Offline
        try {
            client.deleteBoards(store.playerId(), boards)
            // Clear the matching local best too, otherwise automatic sync would republish it.
            localRecords.deleteSelected(boards.mapNotNull { it.arcade }.toSet())
            val edit = appContext.getSharedPreferences("yamone_sudoku_game", Context.MODE_PRIVATE).edit()
            boards.filter { it.minimumWins }.forEach { edit.remove("best_${it.modeId}") }
            edit.commit()
            store.forget(boards)
            OnlineRankingDeleteResult.Success
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { OnlineRankingDeleteResult.ServerUnavailable }
    }
    private fun deviceCountryCode(): String = appContext.resources.configuration.locales.get(0).country
        .uppercase(Locale.US).takeIf { it.matches(Regex("^[A-Z]{2}$")) }.orEmpty()
    companion object {
        private val SYNC_MUTEX = Mutex()
        const val API_BASE = "https://yamone-games-ranking-api.yamone0479.workers.dev"
        private fun endpoint(context: Context): String {
            // Instrumented offline integration tests only. Production always uses HTTPS API_BASE.
            if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                val test = context.getSharedPreferences("yamone_qa", Context.MODE_PRIVATE).getString("ranking_endpoint", "").orEmpty()
                val parsed = runCatching { URL(test) }.getOrNull()
                if (parsed?.host in setOf("10.0.2.2", "127.0.0.1", "localhost") && parsed?.protocol == "http") return test.trimEnd('/')
            }
            return API_BASE
        }
    }
}

private class RankingApiException(val code: String, status: Int) : IOException("Ranking API HTTP $status: $code")

private class OnlineRankingClient(private val baseUrl: String) {
    suspend fun submit(
        playerId: String,
        nickname: String,
        countryCode: String,
        board: RankingBoard,
        score: Int
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("playerId", playerId)
            .put("nickname", nickname)
            .put("countryCode", countryCode)
            .put("gameId", board.gameId)
            .put("modeId", board.modeId)
            .put("score", score)
            .put("scoreUnit", board.unit)

        request(
            method = "POST",
            path = "/v1/ranking/submit",
            body = body
        )
    }

    suspend fun load(playerId: String, board: RankingBoard): OnlineRankingData = withContext(Dispatchers.IO) {
        val encodedPlayer = URLEncoder.encode(playerId, Charsets.UTF_8.name())
        val response = request(
            method = "GET",
            path = "/v1/ranking/${board.gameId}/${board.modeId}?playerId=$encodedPlayer"
        )

        if ((board == RankingBoard.ICE || board.minimumWins) && response.optString("scoreUnit") != board.unit) {
            throw RankingApiException("INVALID_GAME_MODE", 409)
        }
        val meObject = response.optJSONObject("me")
        val me = meObject?.let {
            OnlineRankingMe(
                rank = it.optInt("rank"),
                nickname = it.optString("nickname"),
                countryCode = it.optString("countryCode"),
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
                            countryCode = row.optString("countryCode"),
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
                            countryCode = row.optString("countryCode"),
                            score = row.optInt("score"),
                            isMe = row.optBoolean("isMe", false)
                        )
                    )
                }
            }
        }

        OnlineRankingData(
            board = board,
            totalPlayers = response.optInt("totalPlayers", 0),
            top = top,
            me = me,
            nearby = nearby
        )
    }

    suspend fun deletePlayer(playerId: String) = withContext(Dispatchers.IO) {
        request(
            method = "DELETE",
            path = "/v1/ranking/player",
            body = JSONObject().put("playerId", playerId)
        )
    }

    suspend fun deleteBoards(playerId: String, boards: Set<RankingBoard>) = withContext(Dispatchers.IO) {
        val records = JSONArray()
        boards.forEach { board -> records.put(JSONObject().put("gameId", board.gameId).put("modeId", board.modeId)) }
        request("DELETE", "/v1/ranking/player/games", JSONObject().put("playerId", playerId).put("records", records))
    }

    private fun request(
        method: String,
        path: String,
        body: JSONObject? = null
    ): JSONObject {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
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
                throw RankingApiException(runCatching { JSONObject(text).optString("error", "HTTP_ERROR") }.getOrDefault("HTTP_ERROR"), status)
            }

            val parsed = JSONObject(text)
            if (!parsed.optBoolean("ok", false)) throw IOException("Ranking server did not acknowledge success")
            return parsed
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val API_BASE = "https://yamone-games-ranking-api.yamone0479.workers.dev"
    }
}

internal fun hasUsableNetwork(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
