package com.yamone.games

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
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
                Text("온라인 순위", fontSize = 18.sp, fontWeight = FontWeight.Black, color = YamoneInk)
                Text("TOP 100과 내 전체 순위를 확인해요", fontSize = 12.sp, color = YamoneMuted)
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
    var showDeletePicker by remember { mutableStateOf(false) }
    var selectedForDelete by remember { mutableStateOf<Set<ArcadeGameId>>(emptySet()) }
    var statusText by remember { mutableStateOf<String?>(null) }
    val games = remember {
        listOf(
            ArcadeGameId.ICE_JUMP,
            ArcadeGameId.FISH_MUNCH,
            ArcadeGameId.FISH_MUNCH_TIME_ATTACK,
            ArcadeGameId.SNOW_RUSH
        )
    }

    Text("온라인 순위", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
    Surface(shape = RoundedCornerShape(22.dp), color = Color.White) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("게임 순위 공유", fontSize = 16.sp, fontWeight = FontWeight.Black, color = YamoneInk)
                    Text(
                        if (enabled) "저장된 아케이드 최고기록을 온라인 순위에 공유해요"
                        else "기기 기록만 유지하고 온라인 순위 기록은 삭제해요",
                        fontSize = 12.sp,
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

            Spacer(Modifier.height(10.dp))
            Surface(shape = RoundedCornerShape(14.dp), color = yamonePrimarySoft(themeMode)) {
                Column(Modifier.fillMaxWidth().padding(11.dp)) {
                    Text(
                        if (enabled) {
                            "ON으로 켜면 현재 저장된 각 게임 최고기록 1개씩 전송되고, 이후 최고기록도 자동 갱신돼요 ♡"
                        } else {
                            "OFF로 바꾸면 온라인 순위의 내 기록을 모두 삭제해요. 네트워크가 없으면 연결된 뒤 삭제돼요."
                        },
                        fontSize = 11.sp,
                        color = YamoneMuted
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "국가는 GPS가 아닌 기기 지역 설정의 국가코드만 사용해요.",
                        fontSize = 11.sp,
                        color = YamoneMuted
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    selectedForDelete = emptySet()
                    showDeletePicker = true
                }
            ) {
                Text("온라인 기록 선택 삭제", fontSize = 12.sp, color = yamonePrimaryDark(themeMode))
            }

            statusText?.let {
                Text(it, fontSize = 11.sp, color = YamoneMuted)
            }
        }
    }

    if (showDeletePicker) {
        AlertDialog(
            onDismissRequest = { showDeletePicker = false },
            title = { Text("삭제할 기록을 체크해요", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val allSelected = selectedForDelete.size == games.size
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedForDelete = if (allSelected) emptySet() else games.toSet()
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = allSelected,
                            onCheckedChange = { checked ->
                                selectedForDelete = if (checked) games.toSet() else emptySet()
                            },
                            colors = CheckboxDefaults.colors(checkedColor = yamonePrimary(themeMode))
                        )
                        Text("전체 선택", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = YamoneInk)
                    }
                    HorizontalDivider(color = yamonePrimaryLine(themeMode))
                    games.forEach { game ->
                        val checked = game in selectedForDelete
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedForDelete = if (checked) selectedForDelete - game else selectedForDelete + game
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { selected ->
                                    selectedForDelete = if (selected) selectedForDelete + game else selectedForDelete - game
                                },
                                colors = CheckboxDefaults.colors(checkedColor = yamonePrimary(themeMode))
                            )
                            Text(arcadeGameTitle(game), fontSize = 13.sp, color = YamoneInk)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("체크한 온라인 기록만 삭제되고 기기 안의 기록은 그대로 남아요.", fontSize = 11.sp, color = YamoneMuted)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selectedForDelete.isNotEmpty(),
                    onClick = {
                        val targets = selectedForDelete
                        showDeletePicker = false
                        scope.launch {
                            statusText = when (repository.deleteSelectedOnlineRecords(targets)) {
                                OnlineRankingDeleteResult.Success -> "선택한 온라인 기록을 삭제했어요."
                                OnlineRankingDeleteResult.Offline -> "네트워크에 연결되어 있지 않아요."
                                OnlineRankingDeleteResult.ServerUnavailable -> "온라인 순위를 잠시 이용할 수 없어요."
                            }
                        }
                    }
                ) {
                    Text("선택 삭제", color = yamonePrimaryDark(themeMode), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePicker = false }) {
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
            Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            YamoneActivityBackButton(themeMode, onBack)
            Spacer(Modifier.width(9.dp))
            Column {
                Text("온라인 순위", fontSize = 21.sp, fontWeight = FontWeight.Black, color = YamoneInk)
                Text("공유에 참여한 플레이어의 최고기록", fontSize = 12.sp, color = YamoneMuted)
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
                null -> RankingMessageCard(themeMode, "순위를 불러오는 중이에요…", showProgress = true)
                OnlineRankingLoadResult.Disabled -> RankingMessageCard(themeMode, "게임 순위 공유가 꺼져 있어요.")
                OnlineRankingLoadResult.Offline -> RankingMessageCard(
                    themeMode,
                    "네트워크에 연결되어 있지 않아요.\n게임은 그대로 즐길 수 있어요 ♡",
                    onRetry = { reloadKey++ }
                )
                OnlineRankingLoadResult.ServerUnavailable -> RankingMessageCard(
                    themeMode,
                    "온라인 순위를 잠시 불러올 수 없어요.",
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
    Text("게임 선택", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
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
                        fontSize = 12.sp,
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
            Text("내 순위", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
            Spacer(Modifier.height(5.dp))
            if (data.me == null) {
                Text("아직 온라인 기록이 없어요", fontSize = 18.sp, fontWeight = FontWeight.Black, color = yamonePrimaryDark(themeMode))
                Text("저장된 최고기록은 순위 공유를 켜면 자동으로 등록돼요", fontSize = 12.sp, color = YamoneMuted)
            } else {
                Text(
                    "${data.me.rank}위 / 전체 ${data.totalPlayers}명",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = yamonePrimaryDark(themeMode)
                )
                Text(
                    "${countryFlag(data.me.countryCode)} ${data.me.nickname} · ${arcadeScoreText(data.game, data.me.score)}",
                    fontSize = 12.sp,
                    color = YamoneMuted
                )
            }
        }
    }

    if (data.nearby.isNotEmpty()) {
        Text("내 주변 순위", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
        data.nearby.forEach { RankingRowCard(themeMode, data.game, it) }
    }

    Text("TOP 100", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
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
                "${countryFlag(row.countryCode)} ${row.nickname}",
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                fontWeight = if (row.isMe) FontWeight.Black else FontWeight.Medium,
                color = YamoneInk
            )
            if (row.isMe) {
                Surface(shape = RoundedCornerShape(9.dp), color = Color.White) {
                    Text(
                        "나",
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        fontSize = 10.sp,
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
            Text(message, fontSize = 13.sp, color = YamoneMuted)
            if (onRetry != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onRetry) {
                    Text("다시 시도", fontWeight = FontWeight.Bold, color = yamonePrimaryDark(themeMode))
                }
            }
        }
    }
}


@Composable
private fun YamoneActivityBackButton(
    themeMode: YamoneThemeMode,
    onClick: () -> Unit
) {
    val accent = if (themeMode == YamoneThemeMode.PINK) Color(0xFFE94778) else Color(0xFF159A7A)
    Surface(
        onClick = onClick,
        modifier = Modifier.size(42.dp),
        color = Color.Transparent,
        shape = RoundedCornerShape(14.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(28.dp)) {
                val sx = size.width / 24f
                val sy = size.height / 24f
                val stroke = 4.2f * sx
                drawLine(accent, Offset(20f * sx, 12f * sy), Offset(6.5f * sx, 12f * sy), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(accent, Offset(12.5f * sx, 5.5f * sy), Offset(6f * sx, 12f * sy), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(accent, Offset(6f * sx, 12f * sy), Offset(12.5f * sx, 18.5f * sy), strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
internal fun RankingTabScreen(
    themeMode: YamoneThemeMode,
    repository: OnlineRankingRepository
) {
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = selectedName?.let { name -> runCatching { ArcadeGameId.valueOf(name) }.getOrNull() }

    if (selected == null) {
        RankingLanding(themeMode) { selectedName = it.name }
        return
    }

    BackHandler { selectedName = null }
    var reloadKey by remember { mutableIntStateOf(0) }
    var loadResult by remember { mutableStateOf<OnlineRankingLoadResult?>(null) }

    LaunchedEffect(selected, reloadKey) {
        loadResult = null
        repository.flushPending()
        loadResult = repository.load(selected)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            YamoneActivityBackButton(themeMode) { selectedName = null }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("순위", fontSize = 24.sp, fontWeight = FontWeight.Black, color = YamoneInk)
                Text(arcadeGameTitle(selected), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = yamonePrimaryDark(themeMode))
            }
        }

        when (val result = loadResult) {
            null -> RankingMessageCard(themeMode, "순위를 불러오는 중이에요…", showProgress = true)
            OnlineRankingLoadResult.Disabled -> RankingMessageCard(themeMode, "순위를 준비하고 있어요.", onRetry = { reloadKey++ })
            OnlineRankingLoadResult.Offline -> RankingMessageCard(themeMode, "인터넷에 연결되면 순위를 볼 수 있어요.", onRetry = { reloadKey++ })
            OnlineRankingLoadResult.ServerUnavailable -> RankingMessageCard(themeMode, "순위 서버에 연결할 수 없어요.", onRetry = { reloadKey++ })
            is OnlineRankingLoadResult.Success -> RankingTabContents(themeMode, result.data)
        }
        Spacer(Modifier.height(5.dp))
    }
}

@Composable
private fun RankingLanding(
    themeMode: YamoneThemeMode,
    onSelect: (ArcadeGameId) -> Unit
) {
    val games = listOf(
        ArcadeGameId.ICE_JUMP,
        ArcadeGameId.FISH_MUNCH,
        ArcadeGameId.FISH_MUNCH_TIME_ATTACK,
        ArcadeGameId.SNOW_RUSH
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("순위", fontSize = 26.sp, fontWeight = FontWeight.Black, color = YamoneInk)
        Text("게임을 선택해 순위를 확인해요", fontSize = 12.sp, color = YamoneMuted)
        Spacer(Modifier.height(2.dp))
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White, shadowElevation = 1.dp) {
            Column(Modifier.fillMaxWidth()) {
                games.forEachIndexed { index, game ->
                    val iconKind = when (game) {
                        ArcadeGameId.ICE_JUMP -> GameIconKind.ICE_JUMP
                        ArcadeGameId.FISH_MUNCH, ArcadeGameId.FISH_MUNCH_TIME_ATTACK -> GameIconKind.FISH_MUNCH
                        ArcadeGameId.SNOW_RUSH -> GameIconKind.SNOW_RUSH
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(game) }.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GameListIcon(iconKind, themeMode)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            arcadeGameTitle(game),
                            modifier = Modifier.weight(1f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = YamoneInk
                        )
                        Text("›", fontSize = 27.sp, color = YamoneMuted)
                    }
                    if (index != games.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            color = Color(0xFFEAF0EF)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RankingTabContents(themeMode: YamoneThemeMode, data: OnlineRankingData) {
    val myRank = data.me?.rank
    val topLimit = if (myRank != null && myRank > 20) 15 else 20
    val topRows = data.top.take(topLimit)

    if (topRows.isEmpty()) {
        RankingMessageCard(themeMode, "아직 등록된 순위가 없어요.")
        return
    }

    topRows.forEach { row ->
        RankingTabRow(themeMode, data.game, row.copy(isMe = row.isMe || row.rank == myRank))
    }

    if (myRank != null && myRank > 20) {
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 12.dp),
            color = yamonePrimaryDark(themeMode).copy(alpha = .18f)
        )
        Spacer(Modifier.height(8.dp))

        val around = buildList {
            addAll(data.nearby.filter { it.rank in (myRank - 2)..(myRank + 2) })
            if (none { it.rank == myRank }) {
                data.me?.let { me ->
                    add(
                        OnlineRankingRow(
                            rank = me.rank,
                            nickname = me.nickname,
                            countryCode = me.countryCode,
                            score = me.score,
                            isMe = true
                        )
                    )
                }
            }
        }.distinctBy { it.rank }.sortedBy { it.rank }

        around.forEach { row ->
            RankingTabRow(themeMode, data.game, row.copy(isMe = row.rank == myRank))
        }
    }
}

@Composable
private fun RankingTabRow(
    themeMode: YamoneThemeMode,
    game: ArcadeGameId,
    row: OnlineRankingRow
) {
    Surface(
        shape = RoundedCornerShape(15.dp),
        color = if (row.isMe) yamonePrimaryDark(themeMode).copy(alpha = .16f) else Color.White,
        border = if (row.isMe) BorderStroke(1.5.dp, yamonePrimaryDark(themeMode).copy(alpha = .42f)) else null
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${row.rank}위",
                modifier = Modifier.width(48.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = YamoneInk
            )
            Text(
                rankingCountryLabel(row.countryCode),
                modifier = Modifier.width(68.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = YamoneInk.copy(alpha = .72f)
            )
            Text(
                row.nickname,
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                fontWeight = if (row.isMe) FontWeight.Black else FontWeight.Medium,
                color = YamoneInk,
                maxLines = 1
            )
            if (row.isMe) {
                Surface(shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = .82f)) {
                    Text(
                        "나",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = YamoneInk
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
            Text(
                arcadeScoreText(game, row.score),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = YamoneInk
            )
        }
    }
}

private fun rankingCountryLabel(countryCode: String): String {
    val code = countryCode.trim().uppercase()
    return if (code.length == 2 && code.all { it in 'A'..'Z' }) {
        "${countryFlag(code)} $code"
    } else {
        "🌐 --"
    }
}

private fun countryFlag(countryCode: String): String {
    val code = countryCode.trim().uppercase()
    if (code.length != 2 || code.any { it !in 'A'..'Z' }) return "🌐"
    val first = Character.toChars(0x1F1E6 + (code[0] - 'A'))
    val second = Character.toChars(0x1F1E6 + (code[1] - 'A'))
    return String(first) + String(second)
}
