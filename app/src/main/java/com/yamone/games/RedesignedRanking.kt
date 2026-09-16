package com.yamone.games

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamone.games.arcadecore.*
import com.yamone.games.sudoku.game.GameStorage
import com.yamone.games.sudoku.ui.theme.*

/** Scores from unlike games are never added together; each game/mode has its own board. */
@Composable
internal fun RedesignedRankingScreen(themeMode: YamoneThemeMode, repository: OnlineRankingRepository) {
    val context = LocalContext.current
    val experience = LocalGameExperience.current
    var family by rememberSaveable { mutableStateOf("ice") }
    var timeAttack by rememberSaveable { mutableStateOf(false) }
    var localOnly by rememberSaveable { mutableStateOf(!repository.enabled()) }
    var refresh by remember { mutableIntStateOf(0) }
    var visibleCount by remember { mutableIntStateOf(20) }
    val game = when (family) {
        "fish" -> if (timeAttack) ArcadeGameId.FISH_MUNCH_TIME_ATTACK else ArcadeGameId.FISH_MUNCH
        "snow" -> ArcadeGameId.SNOW_RUSH
        else -> ArcadeGameId.ICE_JUMP
    }
    val puzzle = family == "sudoku"
    var result by remember { mutableStateOf<OnlineRankingLoadResult?>(null) }
    val records = remember(game, refresh) { ArcadeRecordStorage(context).topRecords(game) }
    val stats = remember(refresh) { GameStorage(context).stats() }
    LaunchedEffect(game, family, localOnly, refresh) {
        visibleCount = 20
        result = null
        if (!puzzle && !localOnly) {
            repository.flushPending()
            result = repository.load(game)
        }
    }
    val successful = result as? OnlineRankingLoadResult.Success
    val data = successful?.data
    val accent = yamonePrimaryDark(themeMode)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 15.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("우리들의 기록", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = YamoneInk)
                    Text("게임마다 다른 도전, 나만의 최고기록", fontSize = 11.sp, color = YamoneMuted)
                }
                TextButton(onClick = { refresh++; experience?.play(GameSound.TAP) }) { Text("새로고침", fontSize = 11.sp) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("sudoku" to "스도쿠", "ice" to "빙하", "fish" to "물고기", "snow" to "눈덩이").forEach { (id, label) ->
                    Surface(onClick = { family = id; experience?.play(GameSound.TAP) }, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(17.dp), color = if (family == id) yamonePrimary(themeMode) else Color.White) {
                        Text(label, modifier = Modifier.padding(vertical = 13.dp), textAlign = TextAlign.Center, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, color = if (family == id) Color.White else YamoneMuted)
                    }
                }
            }
        }
        if (family == "fish") item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !timeAttack, onClick = { timeAttack = false }, label = { Text("일반") }, border = null)
                FilterChip(selected = timeAttack, onClick = { timeAttack = true }, label = { Text("60초 타임어택") }, border = null)
            }
        }
        if (!puzzle) item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(false to "온라인 순위", true to "내 기록").forEach { (isLocal, title) ->
                    Surface(onClick = { localOnly = isLocal }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp),
                        color = if (localOnly == isLocal) yamonePrimarySoft(themeMode) else Color.White) {
                        Text(title, modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold, color = if (localOnly == isLocal) accent else YamoneMuted)
                    }
                }
            }
        }
        if (puzzle) {
            item { RankingNote("스도쿠는 난이도별 기기 기록으로 보여줘요. 서로 다른 퍼즐의 시간을 온라인 점수처럼 비교하지 않아요.") }
            item { RankingSummary("완료한 퍼즐", "${stats.totalCompleted}판", "연속 ${stats.currentStreak}일", themeMode) }
            stats.difficultyStats.forEach { stat -> item {
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stat.difficulty.label, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("완료 ${stat.completed}판", fontSize = 12.sp, color = YamoneMuted)
                        }
                        Text(stat.bestSeconds?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "첫 기록을 기다려요", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = accent)
                    }
                }
            } }
        } else if (localOnly) {
            item { RankingSummary(arcadeGameTitle(game), records.firstOrNull()?.let { arcadeScoreText(game, it.score) } ?: "아직 기록 없음", "기기에 저장된 최고 5개 기록", themeMode) }
            if (records.isEmpty()) item { RankingNote("첫 번째 기록을 만들어 볼까요? 인터넷이 없어도 게임과 기록 저장은 가능해요.") }
            itemsIndexed(records) { index, record ->
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}", modifier = Modifier.width(28.dp), fontWeight = FontWeight.Bold, color = accent)
                        Column(Modifier.weight(1f)) {
                            Text(record.nickname, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(arcadeEndedAtText(record.endedAtEpochMillis), fontSize = 10.sp, color = YamoneMuted)
                        }
                        Text(arcadeScoreText(game, record.score), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = accent)
                    }
                }
            }
            item { RankingNote(if (repository.enabled()) "순위 공유 ON · 최고기록만 온라인에 공유돼요." else "순위 공유 OFF · 기록은 기기에만 보관돼요. 공유 여부는 설정에서 바꿀 수 있어요.") }
        } else when (val current = result) {
            null -> item {
                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.height(12.dp)); Text("순위를 불러오고 있어요", fontSize = 12.sp, color = YamoneMuted)
                }
            }
            OnlineRankingLoadResult.Disabled -> item { RankingNote("순위 공유가 꺼져 있어요. 설정의 ‘게임 순위 공유’를 켜면 저장된 최고기록을 공유해요. 내 기록은 그대로 볼 수 있어요.") }
            OnlineRankingLoadResult.Offline -> item { RankingNote("인터넷에 연결되어 있지 않아요. ‘내 기록’은 계속 볼 수 있고, 연결 후 새로고침하면 온라인 순위를 불러와요.") }
            OnlineRankingLoadResult.ServerUnavailable -> item { RankingNote("순위 서버에 연결할 수 없어요. 잠시 후 새로고침해 주세요. 기기의 게임 기록은 그대로예요.") }
            is OnlineRankingLoadResult.Success -> {
                val ranking = current.data
                item {
                    RankingSummary(ranking.me?.let { "내 전체 순위 · ${it.rank}위" } ?: "아직 공유한 기록이 없어요",
                        ranking.me?.let { arcadeScoreText(game, it.score) } ?: "첫 도전을 시작해요",
                        "${arcadeGameTitle(game)} · 참여 ${ranking.totalPlayers}명", themeMode)
                }
                if (ranking.top.isEmpty()) item { RankingNote("아직 등록된 순위가 없어요. 첫 번째 주인공이 되어 주세요!") }
                if (ranking.top.isNotEmpty()) item { RankingPodium(ranking, themeMode) }
                item { Text("최고기록 순위", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = YamoneInk) }
                itemsIndexed(ranking.top.take(visibleCount)) { _, row -> RankingListRow(game, row, themeMode) }
                if (ranking.top.size > visibleCount) item {
                    TextButton(onClick = { visibleCount += 20 }, modifier = Modifier.fillMaxWidth()) { Text("순위 더 보기") }
                }
                if (ranking.me != null && ranking.top.take(visibleCount).none { it.isMe }) {
                    item { Text("내 주변 순위", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = YamoneInk) }
                    itemsIndexed(ranking.nearby) { _, row -> RankingListRow(game, row, themeMode) }
                }
            }
        }
    }
}

@Composable
private fun RankingSummary(title: String, value: String, caption: String, mode: YamoneThemeMode) {
    Surface(shape = RoundedCornerShape(24.dp), color = yamonePrimarySoft(mode)) {
        Column(Modifier.fillMaxWidth().padding(19.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = yamonePrimaryDark(mode))
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = YamoneInk)
            Text(caption, fontSize = 11.sp, color = YamoneMuted)
        }
    }
}
@Composable
private fun RankingNote(text: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
        Text(text, modifier = Modifier.fillMaxWidth().padding(18.dp), fontSize = 13.sp, lineHeight = 21.sp, color = YamoneMuted)
    }
}
@Composable
private fun RankingPodium(data: OnlineRankingData, mode: YamoneThemeMode) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        listOf(1, 0, 2).forEach { index ->
            val row = data.top.getOrNull(index)
            if (row != null) {
                Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp), color = if (index == 0) Color(0xFFFFF0D8) else Color.White) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = if (index == 0) 20.dp else 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(listOf("🥇", "🥈", "🥉")[index], fontSize = 28.sp)
                        Text("${row.rank}위", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = YamoneMuted)
                        Text(row.nickname, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(5.dp))
                        Text(arcadeScoreText(data.game, row.score), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = yamonePrimaryDark(mode))
                    }
                }
            } else Spacer(Modifier.weight(1f))
        }
    }
}
@Composable
private fun RankingListRow(game: ArcadeGameId, row: OnlineRankingRow, mode: YamoneThemeMode) {
    Surface(shape = RoundedCornerShape(18.dp), color = if (row.isMe) yamonePrimarySoft(mode) else Color.White) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${row.rank}", modifier = Modifier.width(32.dp), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = yamonePrimaryDark(mode))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.nickname, modifier = Modifier.weight(1f, fill = false), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (row.isMe) Text("  나", fontSize = 11.sp, color = yamonePrimaryDark(mode))
                }
                Text(row.countryCode.ifBlank { "--" }, fontSize = 10.sp, color = YamoneMuted)
            }
            Spacer(Modifier.width(9.dp))
            Text(arcadeScoreText(game, row.score), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = YamoneInk)
        }
    }
}
