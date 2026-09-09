package com.yamone.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamone.games.arcadecore.ArcadeGameId
import com.yamone.games.sudoku.ui.theme.*
import kotlinx.coroutines.launch

@Composable
internal fun OnlineRankingEntryCard(
    themeMode: YamoneThemeMode,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = yamonePrimarySoft(themeMode),
        border = BorderStroke(1.dp, yamonePrimaryLine(themeMode))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(15.dp), color = Color.White) {
                Text(
                    "★",
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = yamonePrimaryDark(themeMode)
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text("온라인 랭킹", fontSize = 17.sp, fontWeight = FontWeight.Black, color = YamoneInk)
                Text("TOP 100과 내 전체 순위를 확인해요", fontSize = 10.sp, color = YamoneMuted)
            }
            Text("›", fontSize = 26.sp, color = yamonePrimaryDark(themeMode))
        }
    }
}

@Composable
internal fun OnlineRankingSettingsSection(
    themeMode: YamoneThemeMode,
    enabled: Boolean,
    repository: OnlineRankingRepository,
    onEnabledChange: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }

    Text("온라인 랭킹", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
    Surface(shape = RoundedCornerShape(22.dp), color = Color.White) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("게임 순위 공유", fontSize = 15.sp, fontWeight = FontWeight.Black, color = YamoneInk)
                    Text(
                        if (enabled) "새 최고기록을 온라인 랭킹에 자동 등록해요"
                        else "켜기 전에는 기록을 보내거나 랭킹을 보여주지 않아요",
                        fontSize = 10.sp,
                        color = YamoneMuted
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        statusText = null
                        onEnabledChange(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = yamonePrimary(themeMode)
                    )
                )
            }

            if (enabled) {
                Spacer(Modifier.height(10.dp))
                Surface(shape = RoundedCornerShape(14.dp), color = yamonePrimarySoft(themeMode)) {
                    Text(
                        "네트워크가 없으면 새 최고기록은 기기에 대기했다가 연결되면 전송돼요 ♡",
                        modifier = Modifier.fillMaxWidth().padding(11.dp),
                        fontSize = 9.sp,
                        color = YamoneMuted
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showDeleteDialog = true }) {
                    Text("온라인 랭킹 기록 삭제", fontSize = 10.sp, color = yamonePrimaryDark(themeMode))
                }
            }

            statusText?.let {
                Text(it, fontSize = 10.sp, color = YamoneMuted)
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("온라인 기록을 삭제할까요?", fontWeight = FontWeight.Black) },
            text = { Text("현재 기기의 익명 사용자 ID로 등록된 아케이드 온라인 기록을 모두 삭제해요.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        scope.launch {
                            statusText = when (repository.deleteAllOnlineRecords()) {
                                OnlineRankingDeleteResult.Success -> "온라인 랭킹 기록을 삭제했어요."
                                OnlineRankingDeleteResult.Offline -> "네트워크에 연결되어 있지 않아요."
                                OnlineRankingDeleteResult.ServerUnavailable -> "온라인 랭킹을 잠시 이용할 수 없어요."
                            }
                        }
                    }
                ) {
                    Text("삭제", color = yamonePrimaryDark(themeMode), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("취소", color = YamoneMuted)
                }
            }
        )
    }
}

@Composable
internal fun OnlineRankingScreen(
    themeMode: YamoneThemeMode,
    mascot: YamoneMascot,
    repository: OnlineRankingRepository,
    onBack: () -> Unit
) {
    var selectedName by rememberSaveable { mutableStateOf(ArcadeGameId.ICE_JUMP.name) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var loadResult by remember { mutableStateOf<OnlineRankingLoadResult?>(null) }
    val selected = runCatching { ArcadeGameId.valueOf(selectedName) }.getOrDefault(ArcadeGameId.ICE_JUMP)

    LaunchedEffect(selected, reloadKey) {
        loadResult = null
        repository.flushPending()
        loadResult = repository.load(selected)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(YamoneCream)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(onClick = onBack, shape = RoundedCornerShape(16.dp), color = Color.White) {
                Text("‹", modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp), fontSize = 30.sp, color = YamoneInk)
            }
            Spacer(Modifier.width(9.dp))
            Column {
                Text("온라인 랭킹", fontSize = 20.sp, fontWeight = FontWeight.Black, color = YamoneInk)
                Text("공유에 참여한 플레이어의 최고기록", fontSize = 10.sp, color = YamoneMuted)
            }
            Spacer(Modifier.weight(1f))
            YamoneMascotIcon(mascot, size = 42.dp, accent = yamonePrimary(themeMode))
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RankingGameSelector(themeMode, selected) { selectedName = it.name }

            when (val result = loadResult) {
                null -> RankingMessageCard(themeMode, "랭킹을 불러오는 중이에요…", showProgress = true)
                OnlineRankingLoadResult.Disabled -> RankingMessageCard(themeMode, "게임 순위 공유가 꺼져 있어요.")
                OnlineRankingLoadResult.Offline -> RankingMessageCard(
                    themeMode,
                    "네트워크에 연결되어 있지 않아요.\n게임은 그대로 즐길 수 있어요 ♡",
                    onRetry = { reloadKey++ }
                )
                OnlineRankingLoadResult.ServerUnavailable -> RankingMessageCard(
                    themeMode,
                    "온라인 랭킹을 잠시 불러올 수 없어요.",
                    onRetry = { reloadKey++ }
                )
                is OnlineRankingLoadResult.Success -> RankingContents(themeMode, result.data)
            }
        }
    }
}

@Composable
private fun RankingGameSelector(
    themeMode: YamoneThemeMode,
    selected: ArcadeGameId,
    onSelect: (ArcadeGameId) -> Unit
) {
    Text("게임 선택", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
    val games = listOf(
        ArcadeGameId.ICE_JUMP,
        ArcadeGameId.FISH_MUNCH,
        ArcadeGameId.FISH_MUNCH_TIME_ATTACK,
        ArcadeGameId.SNOW_RUSH
    )
    games.chunked(2).forEach { rowGames ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowGames.forEach { game ->
                Surface(
                    modifier = Modifier.weight(1f).clickable { onSelect(game) },
                    shape = RoundedCornerShape(17.dp),
                    color = if (game == selected) yamonePrimarySoft(themeMode) else Color.White,
                    border = BorderStroke(
                        if (game == selected) 2.dp else 1.dp,
                        if (game == selected) yamonePrimary(themeMode) else yamonePrimaryLine(themeMode)
                    )
                ) {
                    Text(
                        arcadeGameTitle(game),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (game == selected) yamonePrimaryDark(themeMode) else YamoneInk
                    )
                }
            }
            if (rowGames.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun RankingContents(themeMode: YamoneThemeMode, data: OnlineRankingData) {
    Surface(shape = RoundedCornerShape(22.dp), color = yamonePrimarySoft(themeMode)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("내 순위", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
            Spacer(Modifier.height(5.dp))
            if (data.me == null) {
                Text("아직 온라인 기록이 없어요", fontSize = 18.sp, fontWeight = FontWeight.Black, color = yamonePrimaryDark(themeMode))
                Text("최고기록을 새로 갱신하면 자동으로 등록돼요", fontSize = 10.sp, color = YamoneMuted)
            } else {
                Text(
                    "${data.me.rank}위 / 전체 ${data.totalPlayers}명",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = yamonePrimaryDark(themeMode)
                )
                Text(
                    "${data.me.nickname} · ${arcadeScoreText(data.game, data.me.score)}",
                    fontSize = 11.sp,
                    color = YamoneMuted
                )
            }
        }
    }

    if (data.nearby.isNotEmpty()) {
        Text("내 주변 순위", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
        data.nearby.forEach { RankingRowCard(themeMode, data.game, it) }
    }

    Text("TOP 100", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
    if (data.top.isEmpty()) {
        RankingMessageCard(themeMode, "아직 등록된 기록이 없어요.")
    } else {
        data.top.forEach { RankingRowCard(themeMode, data.game, it) }
    }
}

@Composable
private fun RankingRowCard(
    themeMode: YamoneThemeMode,
    game: ArcadeGameId,
    row: OnlineRankingRow
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (row.isMe) yamonePrimarySoft(themeMode) else Color.White,
        border = if (row.isMe) BorderStroke(2.dp, yamonePrimary(themeMode)) else null
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${row.rank}",
                modifier = Modifier.width(42.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = if (row.isMe) yamonePrimaryDark(themeMode) else YamoneMuted
            )
            Text(
                row.nickname,
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                fontWeight = if (row.isMe) FontWeight.Black else FontWeight.Medium,
                color = YamoneInk
            )
            if (row.isMe) {
                Surface(shape = RoundedCornerShape(9.dp), color = Color.White) {
                    Text(
                        "나",
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = yamonePrimaryDark(themeMode)
                    )
                }
                Spacer(Modifier.width(7.dp))
            }
            Text(
                arcadeScoreText(game, row.score),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = yamonePrimaryDark(themeMode)
            )
        }
    }
}

@Composable
private fun RankingMessageCard(
    themeMode: YamoneThemeMode,
    message: String,
    showProgress: Boolean = false,
    onRetry: (() -> Unit)? = null
) {
    Surface(shape = RoundedCornerShape(22.dp), color = Color.White) {
        Column(
            Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = yamonePrimary(themeMode),
                    strokeWidth = 3.dp
                )
                Spacer(Modifier.height(10.dp))
            }
            Text(message, fontSize = 12.sp, color = YamoneMuted)
            if (onRetry != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onRetry) {
                    Text("다시 시도", fontWeight = FontWeight.Bold, color = yamonePrimaryDark(themeMode))
                }
            }
        }
    }
}
