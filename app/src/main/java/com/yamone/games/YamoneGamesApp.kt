package com.yamone.games

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamone.games.arcadecore.ArcadeGameId
import com.yamone.games.arcadecore.ArcadeRecord
import com.yamone.games.arcadecore.ArcadeRecordStorage
import com.yamone.games.fishmunch.FishMunchScreen
import com.yamone.games.icejump.IceJumpScreen
import com.yamone.games.snowrush.SnowRushScreen
import com.yamone.games.sudoku.game.GameStorage
import com.yamone.games.sudoku.game.SudokuDifficulty
import com.yamone.games.sudoku.game.SudokuStats
import com.yamone.games.sudoku.ui.SudokuApp
import com.yamone.games.sudoku.ui.theme.*
import kotlinx.coroutines.launch

private enum class AppScreen {
    HOME, GAMES, RECORDS, SETTINGS, ONLINE_RANKING, SUDOKU, ICE_JUMP, FISH_MUNCH, SNOW_RUSH
}

private data class MascotHitbox(
    val halfWidth: Float,
    val halfHeight: Float,
    val landingHalfWidth: Float
)

private fun hitboxFor(mascot: YamoneMascot): MascotHitbox = when (mascot) {
    YamoneMascot.SEAL -> MascotHitbox(halfWidth = 0.055f, halfHeight = 0.038f, landingHalfWidth = 0.036f)
    YamoneMascot.BEAR -> MascotHitbox(halfWidth = 0.047f, halfHeight = 0.044f, landingHalfWidth = 0.033f)
}

@Composable
fun YamoneGamesApp(
    themeMode: YamoneThemeMode,
    mascot: YamoneMascot,
    nickname: String,
    onThemeChange: (YamoneThemeMode) -> Unit,
    onMascotChange: (YamoneMascot) -> Unit,
    onNicknameChange: (String) -> Unit
) {
    val context = LocalContext.current.applicationContext
    val sudokuStorage = remember { GameStorage(context) }
    val arcadeStorage = remember { ArcadeRecordStorage(context) }
    val rankingRepository = remember { OnlineRankingRepository(context) }
    val scope = rememberCoroutineScope()

    var screenName by rememberSaveable { mutableStateOf(AppScreen.HOME.name) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var shareRequest by remember { mutableStateOf<ShareCardRequest?>(null) }
    var onlineRankingEnabled by remember { mutableStateOf(rankingRepository.enabled()) }
    var observedLocalBests by remember {
        mutableStateOf(
            ArcadeGameId.entries.associateWith { game ->
                arcadeStorage.topRecords(game).firstOrNull()?.score ?: -1
            }
        )
    }

    val screen = runCatching { AppScreen.valueOf(screenName) }.getOrDefault(AppScreen.HOME)
    val hitbox = hitboxFor(mascot)

    BackHandler(enabled = screen != AppScreen.HOME && shareRequest == null) {
        refreshKey++
        screenName = if (screen == AppScreen.ONLINE_RANKING) AppScreen.RECORDS.name else AppScreen.HOME.name
    }
    BackHandler(enabled = shareRequest != null) {
        shareRequest = null
    }

    val goHome = {
        refreshKey++
        screenName = AppScreen.HOME.name
    }

    val openArcade: (AppScreen) -> Unit = { target ->
        screenName = target.name
    }

    LaunchedEffect(Unit) {
        if (onlineRankingEnabled) {
            // 이전 버전에서 이미 ON이었던 경우에도 현재 로컬 최고기록을 한 번 다시 동기화한다.
            rankingRepository.setEnabled(true)
        }
        rankingRepository.syncSharingState(nickname)
    }

    DisposableEffect(nickname) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch { rankingRepository.syncSharingState(nickname) }
            }
        }
        runCatching { manager?.registerDefaultNetworkCallback(callback) }
        onDispose {
            runCatching { manager?.unregisterNetworkCallback(callback) }
        }
    }

    DisposableEffect(nickname, onlineRankingEnabled) {
        val recordPrefs = context.getSharedPreferences("yamone_arcade_records", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            val changedGame = ArcadeGameId.entries.firstOrNull { game ->
                key == "records_${game.storageKey}"
            } ?: return@OnSharedPreferenceChangeListener

            val best = arcadeStorage.topRecords(changedGame).firstOrNull()?.score ?: -1
            val previous = observedLocalBests[changedGame] ?: -1
            observedLocalBests = observedLocalBests + (changedGame to best)

            if (onlineRankingEnabled && best > previous) {
                scope.launch {
                    rankingRepository.onLocalBestChanged(changedGame, best, nickname)
                }
            }
        }
        recordPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            recordPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val stats = remember(refreshKey, screenName) { sudokuStorage.stats() }
    val savedLevels = remember(refreshKey, screenName) {
        SudokuDifficulty.entries.filter { sudokuStorage.hasSaved(it) }
    }
    val arcadeRecords = remember(refreshKey, screenName, shareRequest) {
        ArcadeGameId.entries.associateWith { arcadeStorage.topRecords(it) }
    }

    Box(Modifier.fillMaxSize()) {
        when (screen) {
            AppScreen.SUDOKU -> {
                SudokuApp(onBack = goHome, themeMode = themeMode, mascot = mascot)
            }
            AppScreen.ICE_JUMP -> {
                IceJumpScreen(
                    onBack = goHome,
                    nickname = nickname,
                    landingHalfWidth = hitbox.landingHalfWidth,
                    onShareRecord = { record ->
                        shareRequest = ShareCardRequest(ArcadeGameId.ICE_JUMP, record)
                    },
                    primary = yamonePrimary(themeMode),
                    primaryDark = yamonePrimaryDark(themeMode),
                    soft = yamonePrimarySoft(themeMode),
                    ink = YamoneInk,
                    muted = YamoneMuted,
                    mascotContent = { size -> YamoneMascotIcon(mascot, size = size, accent = yamonePrimary(themeMode)) }
                )
            }
            AppScreen.FISH_MUNCH -> {
                FishMunchScreen(
                    onBack = goHome,
                    nickname = nickname,
                    playerHalfWidth = hitbox.halfWidth,
                    playerHalfHeight = hitbox.halfHeight,
                    onShareRecord = { record ->
                        shareRequest = ShareCardRequest(ArcadeGameId.FISH_MUNCH, record)
                    },
                    primary = yamonePrimary(themeMode),
                    primaryDark = yamonePrimaryDark(themeMode),
                    soft = yamonePrimarySoft(themeMode),
                    ink = YamoneInk,
                    muted = YamoneMuted,
                    mascotContent = { size -> YamoneMascotIcon(mascot, size = size, accent = yamonePrimary(themeMode)) }
                )
            }
            AppScreen.SNOW_RUSH -> {
                SnowRushScreen(
                    onBack = goHome,
                    nickname = nickname,
                    playerHalfWidth = hitbox.halfWidth,
                    playerHalfHeight = hitbox.halfHeight,
                    onShareRecord = { record ->
                        shareRequest = ShareCardRequest(ArcadeGameId.SNOW_RUSH, record)
                    },
                    primary = yamonePrimary(themeMode),
                    primaryDark = yamonePrimaryDark(themeMode),
                    soft = yamonePrimarySoft(themeMode),
                    ink = YamoneInk,
                    muted = YamoneMuted,
                    mascotContent = { size -> YamoneMascotIcon(mascot, size = size, accent = yamonePrimary(themeMode)) }
                )
            }
            AppScreen.ONLINE_RANKING -> {
                OnlineRankingScreen(
                    themeMode = themeMode,
                    mascot = mascot,
                    repository = rankingRepository,
                    onBack = {
                        refreshKey++
                        screenName = AppScreen.RECORDS.name
                    }
                )
            }
            else -> {
                Scaffold(
                    containerColor = YamoneCream,
                    topBar = { MainTopBar(mascot, themeMode) },
                    bottomBar = {
                        MainBottomBar(screen, themeMode) { selected ->
                            screenName = selected.name
                            refreshKey++
                        }
                    }
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        when (screen) {
                            AppScreen.HOME -> HomeScreen(
                                themeMode = themeMode,
                                mascot = mascot,
                                stats = stats,
                                savedLevels = savedLevels,
                                onSudoku = { screenName = AppScreen.SUDOKU.name },
                                onIceJump = { openArcade(AppScreen.ICE_JUMP) },
                                onFishMunch = { openArcade(AppScreen.FISH_MUNCH) },
                                onSnowRush = { openArcade(AppScreen.SNOW_RUSH) }
                            )
                            AppScreen.GAMES -> GamesScreen(
                                themeMode = themeMode,
                                onSudoku = { screenName = AppScreen.SUDOKU.name },
                                onIceJump = { openArcade(AppScreen.ICE_JUMP) },
                                onFishMunch = { openArcade(AppScreen.FISH_MUNCH) },
                                onSnowRush = { openArcade(AppScreen.SNOW_RUSH) }
                            )
                            AppScreen.RECORDS -> RecordsScreen(
                                themeMode = themeMode,
                                mascot = mascot,
                                stats = stats,
                                arcadeRecords = arcadeRecords,
                                onlineRankingEnabled = onlineRankingEnabled,
                                onOnlineRanking = { screenName = AppScreen.ONLINE_RANKING.name },
                                onShare = { game, record -> shareRequest = ShareCardRequest(game, record) }
                            )
                            AppScreen.SETTINGS -> SettingsScreen(
                                themeMode = themeMode,
                                mascot = mascot,
                                nickname = nickname,
                                onlineRankingEnabled = onlineRankingEnabled,
                                rankingRepository = rankingRepository,
                                onOnlineRankingEnabledChange = { enabled ->
                                    rankingRepository.setEnabled(enabled)
                                    onlineRankingEnabled = enabled
                                    scope.launch { rankingRepository.syncSharingState(nickname) }
                                },
                                onThemeChange = onThemeChange,
                                onMascotChange = onMascotChange,
                                onNicknameChange = onNicknameChange
                            )
                            else -> Unit
                        }
                    }
                }
            }
        }

        shareRequest?.let { request ->
            ShareCardScreen(
                request = request,
                themeMode = themeMode,
                mascot = mascot,
                onBack = { shareRequest = null }
            )
        }
    }
}

@Composable
private fun MainTopBar(mascot: YamoneMascot, themeMode: YamoneThemeMode) {
    Surface(color = Color.White) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(62.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("야모네", fontSize = 22.sp, fontWeight = FontWeight.Black, color = YamoneInk)
                Text("작고 귀여운 게임들", fontSize = 10.sp, color = YamoneMuted)
            }
            Spacer(Modifier.weight(1f))
            YamoneMascotIcon(mascot, size = 44.dp, accent = yamonePrimary(themeMode))
        }
    }
}

@Composable
private fun MainBottomBar(screen: AppScreen, themeMode: YamoneThemeMode, onSelect: (AppScreen) -> Unit) {
    val tabs = listOf(
        Triple(AppScreen.HOME, "⌂", "홈"),
        Triple(AppScreen.GAMES, "▦", "게임"),
        Triple(AppScreen.RECORDS, "★", "기록"),
        Triple(AppScreen.SETTINGS, "⚙", "설정")
    )
    NavigationBar(containerColor = Color.White) {
        tabs.forEach { (target, symbol, label) ->
            NavigationBarItem(
                selected = screen == target,
                onClick = { onSelect(target) },
                icon = { Text(symbol, fontSize = 18.sp) },
                label = { Text(label, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = yamonePrimaryDark(themeMode),
                    selectedTextColor = yamonePrimaryDark(themeMode),
                    indicatorColor = yamonePrimarySoft(themeMode)
                )
            )
        }
    }
}

@Composable
private fun HomeScreen(
    themeMode: YamoneThemeMode,
    mascot: YamoneMascot,
    stats: SudokuStats,
    savedLevels: List<SudokuDifficulty>,
    onSudoku: () -> Unit,
    onIceJump: () -> Unit,
    onFishMunch: () -> Unit,
    onSnowRush: () -> Unit
) {
    val accent = yamonePrimary(themeMode)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(shape = RoundedCornerShape(28.dp), color = yamonePrimarySoft(themeMode)) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("오늘은 뭐 하고 놀까?", fontSize = 22.sp, fontWeight = FontWeight.Black, color = YamoneInk)
                    Spacer(Modifier.height(6.dp))
                    Text("스도쿠와 아케이드 3개를 즐겨요 ♡", fontSize = 12.sp, color = YamoneMuted)
                }
                YamoneMascotIcon(mascot, size = 78.dp, accent = accent)
            }
        }

        Text("플레이", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActiveGameCard(Modifier.weight(1f), "스도쿠", "9×9", themeMode, onSudoku)
            ActiveGameCard(Modifier.weight(1f), "빙하 점프", "▲", themeMode, onIceJump)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActiveGameCard(Modifier.weight(1f), "물고기 냠냠", "🐟", themeMode, onFishMunch)
            ActiveGameCard(Modifier.weight(1f), "눈덩이 러시", "❄", themeMode, onSnowRush)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat(Modifier.weight(1f), "완료", "${stats.totalCompleted}판", themeMode)
            MiniStat(Modifier.weight(1f), "연속", "${stats.currentStreak}일", themeMode)
            MiniStat(Modifier.weight(1f), "임시저장", "${savedLevels.size}개", themeMode)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GamesScreen(
    themeMode: YamoneThemeMode,
    onSudoku: () -> Unit,
    onIceJump: () -> Unit,
    onFishMunch: () -> Unit,
    onSnowRush: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("게임", fontSize = 24.sp, fontWeight = FontWeight.Black, color = YamoneInk)
        Text("지금 플레이할 수 있는 게임만 보여드려요.", fontSize = 12.sp, color = YamoneMuted)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActiveGameCard(Modifier.weight(1f), "스도쿠", "9×9", themeMode, onSudoku)
            ActiveGameCard(Modifier.weight(1f), "빙하 점프", "▲", themeMode, onIceJump)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActiveGameCard(Modifier.weight(1f), "물고기 냠냠", "🐟", themeMode, onFishMunch)
            ActiveGameCard(Modifier.weight(1f), "눈덩이 러시", "❄", themeMode, onSnowRush)
        }
    }
}

@Composable
private fun RecordsScreen(
    themeMode: YamoneThemeMode,
    mascot: YamoneMascot,
    stats: SudokuStats,
    arcadeRecords: Map<ArcadeGameId, List<ArcadeRecord>>,
    onlineRankingEnabled: Boolean,
    onOnlineRanking: () -> Unit,
    onShare: (ArcadeGameId, ArcadeRecord) -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(shape = RoundedCornerShape(26.dp), color = yamonePrimarySoft(themeMode)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                YamoneMascotIcon(mascot, size = 66.dp, accent = yamonePrimary(themeMode))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("나의 기록", fontSize = 21.sp, fontWeight = FontWeight.Black, color = YamoneInk)
                    Text("아케이드 좋은 기록은 게임별 5개까지만 보관해요 ♡", fontSize = 11.sp, color = YamoneMuted)
                }
            }
        }

        if (onlineRankingEnabled) {
            OnlineRankingEntryCard(themeMode = themeMode, onClick = onOnlineRanking)
        }

        Text("스도쿠", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigStatCard(Modifier.weight(1f), "완료한 게임", "${stats.totalCompleted}판", themeMode)
            BigStatCard(Modifier.weight(1f), "연속 플레이", "${stats.currentStreak}일", themeMode)
        }

        stats.difficultyStats.forEach { item ->
            Surface(shape = RoundedCornerShape(18.dp), color = Color.White) {
                Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp), color = yamonePrimarySoft(themeMode)) {
                        Text(item.difficulty.label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontWeight = FontWeight.Bold, color = yamonePrimaryDark(themeMode))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("완료 ${item.completed}판", fontSize = 11.sp, color = YamoneMuted)
                    Spacer(Modifier.weight(1f))
                    Text(item.bestSeconds?.let(::formatDuration) ?: "—", fontWeight = FontWeight.ExtraBold, color = YamoneInk)
                }
            }
        }

        ArcadeRecordSection(
            game = ArcadeGameId.ICE_JUMP,
            records = arcadeRecords[ArcadeGameId.ICE_JUMP].orEmpty(),
            themeMode = themeMode,
            onShare = onShare
        )
        ArcadeRecordSection(
            game = ArcadeGameId.FISH_MUNCH,
            records = arcadeRecords[ArcadeGameId.FISH_MUNCH].orEmpty(),
            themeMode = themeMode,
            onShare = onShare
        )
        ArcadeRecordSection(
            game = ArcadeGameId.FISH_MUNCH_TIME_ATTACK,
            records = arcadeRecords[ArcadeGameId.FISH_MUNCH_TIME_ATTACK].orEmpty(),
            themeMode = themeMode,
            onShare = onShare
        )
        ArcadeRecordSection(
            game = ArcadeGameId.SNOW_RUSH,
            records = arcadeRecords[ArcadeGameId.SNOW_RUSH].orEmpty(),
            themeMode = themeMode,
            onShare = onShare
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ArcadeRecordSection(
    game: ArcadeGameId,
    records: List<ArcadeRecord>,
    themeMode: YamoneThemeMode,
    onShare: (ArcadeGameId, ArcadeRecord) -> Unit
) {
    Spacer(Modifier.height(2.dp))
    Text(arcadeGameTitle(game), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
    if (records.isEmpty()) {
        Surface(shape = RoundedCornerShape(18.dp), color = Color.White) {
            Text(
                "아직 기록이 없어요",
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                fontSize = 11.sp,
                color = YamoneMuted
            )
        }
    } else {
        records.forEachIndexed { index, record ->
            Surface(shape = RoundedCornerShape(18.dp), color = Color.White) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = RoundedCornerShape(11.dp), color = yamonePrimarySoft(themeMode)) {
                        Text(
                            "${index + 1}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            fontWeight = FontWeight.Black,
                            color = yamonePrimaryDark(themeMode)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(arcadeScoreText(game, record.score), fontSize = 15.sp, fontWeight = FontWeight.Black, color = YamoneInk)
                        Text(arcadeEndedAtText(record.endedAtEpochMillis), fontSize = 9.sp, color = YamoneMuted)
                        Text(record.nickname, fontSize = 9.sp, color = YamoneMuted.copy(alpha = .8f))
                    }
                    TextButton(onClick = { onShare(game, record) }) {
                        Text("공유", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = yamonePrimaryDark(themeMode))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    themeMode: YamoneThemeMode,
    mascot: YamoneMascot,
    nickname: String,
    onlineRankingEnabled: Boolean,
    rankingRepository: OnlineRankingRepository,
    onOnlineRankingEnabledChange: (Boolean) -> Unit,
    onThemeChange: (YamoneThemeMode) -> Unit,
    onMascotChange: (YamoneMascot) -> Unit,
    onNicknameChange: (String) -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("설정", fontSize = 24.sp, fontWeight = FontWeight.Black, color = YamoneInk)
        Text("닉네임과 캐릭터, 색상을 골라요.", fontSize = 12.sp, color = YamoneMuted)

        Text("닉네임", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
        OutlinedTextField(
            value = nickname,
            onValueChange = { onNicknameChange(it.take(AppPreferences.MAX_NICKNAME_LENGTH)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            placeholder = { Text("야모네 플레이어") },
            supportingText = {
                Text("공유카드에 표시되고, 랭킹 ON일 때 온라인에도 표시돼요", fontSize = 10.sp, color = YamoneMuted)
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = yamonePrimary(themeMode),
                unfocusedBorderColor = yamonePrimaryLine(themeMode),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                cursorColor = yamonePrimaryDark(themeMode)
            )
        )

        OnlineRankingSettingsSection(
            themeMode = themeMode,
            enabled = onlineRankingEnabled,
            repository = rankingRepository,
            onEnabledChange = onOnlineRankingEnabledChange
        )

        Surface(shape = RoundedCornerShape(26.dp), color = yamonePrimarySoft(themeMode)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                YamoneMascotIcon(mascot, size = 92.dp, accent = yamonePrimary(themeMode))
                Spacer(Modifier.height(8.dp))
                Text("원형 틀 없이 게임 화면과 공유카드에 적용돼요 ♡", fontSize = 10.sp, color = YamoneMuted)
            }
        }

        Text("캐릭터", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            YamoneMascot.entries.forEach { option ->
                SelectorCard(Modifier.weight(1f), mascot == option, themeMode, { onMascotChange(option) }) {
                    YamoneMascotIcon(option, size = 76.dp, accent = yamonePrimary(themeMode))
                }
            }
        }

        Text("색상", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            YamoneThemeMode.entries.forEach { option ->
                SelectorCard(Modifier.weight(1f), themeMode == option, option, { onThemeChange(option) }) {
                    Box(Modifier.size(54.dp).background(yamonePrimary(option), RoundedCornerShape(18.dp)))
                }
            }
        }
    }
}

@Composable
private fun SelectorCard(
    modifier: Modifier,
    selected: Boolean,
    themeMode: YamoneThemeMode,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) yamonePrimarySoft(themeMode) else Color.White,
        border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) yamonePrimary(themeMode) else Color(0xFFE4ECEA))
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

@Composable
private fun MiniStat(modifier: Modifier, label: String, value: String, themeMode: YamoneThemeMode) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = Color.White) {
        Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Black, color = yamonePrimaryDark(themeMode))
            Text(label, fontSize = 10.sp, color = YamoneMuted)
        }
    }
}

@Composable
private fun BigStatCard(modifier: Modifier, label: String, value: String, themeMode: YamoneThemeMode) {
    Surface(modifier = modifier, shape = RoundedCornerShape(20.dp), color = Color.White) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 23.sp, fontWeight = FontWeight.Black, color = yamonePrimaryDark(themeMode))
            Text(label, fontSize = 11.sp, color = YamoneMuted)
        }
    }
}

@Composable
private fun ActiveGameCard(modifier: Modifier, title: String, symbol: String, themeMode: YamoneThemeMode, onClick: () -> Unit) {
    Surface(modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(22.dp), color = yamonePrimarySoft(themeMode)) {
        Column(Modifier.padding(18.dp)) {
            Text(symbol, fontSize = 24.sp, fontWeight = FontWeight.Black, color = yamonePrimaryDark(themeMode))
            Spacer(Modifier.height(18.dp))
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Black, color = YamoneInk)
            Text("플레이하기 ›", fontSize = 11.sp, color = yamonePrimaryDark(themeMode))
        }
    }
}

@Composable
private fun DevelopmentGameCard(
    modifier: Modifier,
    title: String,
    symbol: String,
    description: String,
    themeMode: YamoneThemeMode,
    onClick: (() -> Unit)? = null
) {
    val cardModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Surface(
        modifier = cardModifier,
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, yamonePrimaryLine(themeMode))
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(16.dp), color = yamonePrimarySoft(themeMode)) {
                Text(
                    symbol,
                    modifier = Modifier.padding(horizontal = 17.dp, vertical = 15.dp),
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Black,
                    color = yamonePrimaryDark(themeMode)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Black, color = YamoneInk)
                Spacer(Modifier.height(3.dp))
                Text(description, fontSize = 11.sp, color = YamoneMuted)
            }
            if (onClick != null) Text("›", fontSize = 24.sp, color = yamonePrimary(themeMode))
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
