package com.yamone.spiritshift.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.Normalizer

data class AppUiState(
    val playerId: String = "",
    val nickname: String = "",
    val bestScore: Int = 0,
    val bestTimeMs: Long = 0L,
    val freeRemaining: Int = 3,
    val bonusPlays: Int = 0,
    val leaderboard: List<RankingEntry> = emptyList(),
    val rankingLoading: Boolean = false,
    val rankingError: String? = null,
    val serverConfigured: Boolean = false,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PlayerPrefs(application)
    private val ranking = RankingRepository()
    private val _ui = MutableStateFlow(AppUiState())
    val ui: StateFlow<AppUiState> = _ui.asStateFlow()

    init {
        prefs.refreshDailyIfNeeded()
        publish()
    }

    fun refreshDaily() {
        prefs.refreshDailyIfNeeded()
        publish()
    }

    @Synchronized
    fun consumePlay(): Boolean {
        prefs.refreshDailyIfNeeded()
        return when {
            prefs.freeUsed < 3 -> {
                prefs.freeUsed += 1
                publish()
                true
            }
            prefs.bonusPlays > 0 -> {
                prefs.bonusPlays -= 1
                publish()
                true
            }
            else -> false
        }
    }

    fun grantRewardedPlays(count: Int = 5) {
        prefs.bonusPlays += count
        publish()
    }

    suspend fun setNickname(raw: String): Result<Unit> {
        val nickname = normalizeNickname(raw).getOrElse { return Result.failure(it) }
        val reserve = ranking.reserveNickname(prefs.playerId, nickname)
        if (reserve.isFailure) return reserve
        prefs.nickname = nickname
        publish()
        return Result.success(Unit)
    }

    fun recordGame(score: Int, playTimeMs: Long) {
        val nickname = prefs.nickname
        val isNewBest = score > prefs.bestScore
        if (isNewBest) {
            prefs.bestScore = score
            prefs.bestTimeMs = playTimeMs
            publish()
        }
        if (nickname.isNotBlank() && (isNewBest || ranking.isConfigured)) {
            viewModelScope.launch {
                ranking.submitBest(prefs.playerId, nickname, score, playTimeMs)
                refreshLeaderboard()
            }
        }
    }

    fun refreshLeaderboard() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(rankingLoading = true, rankingError = null)
            val result = ranking.leaderboard()
            _ui.value = _ui.value.copy(
                rankingLoading = false,
                leaderboard = result.getOrElse { emptyList() },
                rankingError = result.exceptionOrNull()?.message,
            )
        }
    }

    private fun publish() {
        _ui.value = _ui.value.copy(
            playerId = prefs.playerId,
            nickname = prefs.nickname,
            bestScore = prefs.bestScore,
            bestTimeMs = prefs.bestTimeMs,
            freeRemaining = (3 - prefs.freeUsed).coerceAtLeast(0),
            bonusPlays = prefs.bonusPlays,
            serverConfigured = ranking.isConfigured,
        )
    }

    companion object {
        fun normalizeNickname(raw: String): Result<String> = runCatching {
            val value = Normalizer.normalize(raw.trim(), Normalizer.Form.NFC)
            require(value.isNotBlank()) { "닉네임을 입력해 주세요." }
            var containsHangul = false
            for (ch in value) {
                val cp = ch.code
                val isHangul = cp in 0xAC00..0xD7A3 || cp in 0x3131..0x318E
                val isEnglish = ch in 'A'..'Z' || ch in 'a'..'z'
                val isDigit = ch in '0'..'9'
                require(isHangul || isEnglish || isDigit) { "한글, 영문, 숫자만 사용할 수 있어요." }
                if (isHangul) containsHangul = true
            }
            val max = if (containsHangul) 6 else 12
            require(value.length <= max) {
                if (containsHangul) "한글이 포함된 닉네임은 최대 6자예요." else "영문·숫자 닉네임은 최대 12자예요."
            }
            value
        }
    }
}
