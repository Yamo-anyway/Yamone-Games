package com.yamone.games

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamone.games.arcadecore.ArcadeGameId
import com.yamone.games.arcadecore.ArcadeRecord
import com.yamone.games.arcadecore.ArcadeRecordStorage
import com.yamone.games.fishmunch.FishMunchScreen
import com.yamone.games.icejump.IceJumpScreen
import com.yamone.games.snowrush.SnowRushScreen
import com.yamone.games.sudoku.game.GameStorage
import com.yamone.games.sudoku.game.SudokuStats
import com.yamone.games.sudoku.ui.SudokuApp
import com.yamone.games.sudoku.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

private enum class AppScreen {
    HOME, GAMES, RECORDS, SETTINGS, ONLINE_RANKING, SUDOKU, ICE_JUMP, FISH_MUNCH, SNOW_RUSH
}

private data class MascotHitbox(
    val halfWidth: Float,
    val halfHeight: Float,
    val landingHalfWidth: Float
)

private data class GameListItem(
    val title: String,
    val subtitle: String,
    val symbol: String,
    val onClick: () -> Unit
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
    val adAccessStore = remember { AdAccessStore(context) }
    val scope = rememberCoroutineScope()

    var screenName by rememberSaveable { mutableStateOf(AppScreen.HOME.name) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var shareRequest by remember { mutableStateOf<ShareCardRequest?>(null) }
    var onlineRankingEnabled by remember { mutableStateOf(rankingRepository.enabled()) }
    var adRevision by remember { mutableIntStateOf(0) }
    var showAdDetails by remember { mutableStateOf(false) }
    var showRewardedTestAd by remember { mutableStateOf(false) }
    var showInterstitialTestAd by remember { mutableStateOf(false) }
    var pendingGameName by rememberSaveable { mutableStateOf<String?>(null) }
    var showRankingNickname by remember { mutableStateOf(false) }
    var adInfoMessage by remember { mutableStateOf<String?>(null) }
    var observedLocalBests by remember {
        mutableStateOf(
            ArcadeGameId.entries.associateWith { game ->
                arcadeStorage.topRecords(game).firstOrNull()?.score ?: -1
            }
        )
    }

    val screen = runCatching { AppScreen.valueOf(screenName) }.getOrDefault(AppScreen.HOME)
    val hitbox = hitboxFor(mascot)
    val adRemoved = remember(adRevision) { adAccessStore.adRemoved() }
    val adFreeUntilMillis = remember(adRevision) { adAccessStore.adFreeUntilMillis() }

    BackHandler(enabled = screen != AppScreen.HOME && shareRequest == null && !showInterstitialTestAd && !showRewardedTestAd) {
        refreshKey++
        screenName = if (screen == AppScreen.ONLINE_RANKING) AppScreen.RECORDS.name else AppScreen.HOME.name
    }
    BackHandler(enabled = shareRequest != null) { shareRequest = null }

    val goHome = {
        refreshKey++
        screenName = AppScreen.HOME.name
    }

    val requestGameStart: (AppScreen) -> Unit = { target ->
        val now = System.currentTimeMillis()
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val online = runCatching { connectivityManager?.activeNetwork != null }.getOrDefault(false)
        when {
            adAccessStore.adRemoved() -> screenName = target.name
            !adAccessStore.hasUsedFirstFreeGame() -> {
                adAccessStore.markFirstFreeGameUsed()
                screenName = target.name
            }
            adAccessStore.adFreeUntilMillis() > now -> screenName = target.name
            !online -> screenName = target.name
            else -> {
                pendingGameName = target.name
                showInterstitialTestAd = true
            }
        }
    }

    LaunchedEffect(Unit) {
        if (onlineRankingEnabled) rankingRepository.setEnabled(true)
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
        onDispose { runCatching { manager?.unregisterNetworkCallback(callback) } }
    }

    DisposableEffect(nickname, onlineRankingEnabled) {
        val recordPrefs = context.getSharedPreferences("yamone_arcade_records", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            val changedGame = ArcadeGameId.entries.firstOrNull { game -> key == "records_${game.storageKey}" }
                ?: return@OnSharedPreferenceChangeListener
            val best = arcadeStorage.topRecords(changedGame).firstOrNull()?.score ?: -1
            val previous = observedLocalBests[changedGame] ?: -1
            observedLocalBests = observedLocalBests + (changedGame to best)
            refreshKey++
            if (onlineRankingEnabled && best > previous) {
                scope.launch { rankingRepository.onLocalBestChanged(changedGame, best, nickname) }
            }
        }
        recordPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { recordPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val stats = remember(refreshKey, screenName) { sudokuStorage.stats() }
    val arcadeRecords = remember(refreshKey, screenName, shareRequest) {
        ArcadeGameId.entries.associateWith { arcadeStorage.topRecords(it) }
    }

    Box(Modifier.fillMaxSize()) {
        when (screen) {
            AppScreen.SUDOKU -> SudokuApp(onBack = goHome, themeMode = themeMode, mascot = mascot)
            AppScreen.ICE_JUMP -> IceJumpScreen(
                onBack = goHome,
                nickname = nickname,
                landingHalfWidth = hitbox.landingHalfWidth,
                onShareRecord = { shareRequest = ShareCardRequest(ArcadeGameId.ICE_JUMP, it) },
                primary = yamonePrimary(themeMode),
                primaryDark = yamonePrimaryDark(themeMode),
                soft = yamonePrimarySoft(themeMode),
                ink = YamoneInk,
                muted = YamoneMuted,
                mascotContent = { size -> YamoneMascotIcon(mascot, size = size, accent = yamonePrimary(themeMode)) }
            )
            AppScreen.FISH_MUNCH -> FishMunchScreen(
                onBack = goHome,
                nickname = nickname,
                playerHalfWidth = hitbox.halfWidth,
                playerHalfHeight = hitbox.halfHeight,
                onShareRecord = { shareRequest = ShareCardRequest(ArcadeGameId.FISH_MUNCH, it) },
                primary = yamonePrimary(themeMode),
                primaryDark = yamonePrimaryDark(themeMode),
                soft = yamonePrimarySoft(themeMode),
                ink = YamoneInk,
                muted = YamoneMuted,
                mascotContent = { size -> YamoneMascotIcon(mascot, size = size, accent = yamonePrimary(themeMode)) }
            )
            AppScreen.SNOW_RUSH -> SnowRushScreen(
                onBack = goHome,
                nickname = nickname,
                playerHalfWidth = hitbox.halfWidth,
                playerHalfHeight = hitbox.halfHeight,
                onShareRecord = { shareRequest = ShareCardRequest(ArcadeGameId.SNOW_RUSH, it) },
                primary = yamonePrimary(themeMode),
                primaryDark = yamonePrimaryDark(themeMode),
                soft = yamonePrimarySoft(themeMode),
                ink = YamoneInk,
                muted = YamoneMuted,
                mascotContent = { size -> YamoneMascotIcon(mascot, size = size, accent = yamonePrimary(themeMode)) }
            )
            AppScreen.ONLINE_RANKING -> OnlineRankingScreen(
                themeMode = themeMode,
                mascot = mascot,
                repository = rankingRepository,
                onBack = {
                    refreshKey++
                    screenName = AppScreen.RECORDS.name
                }
            )
            else -> Scaffold(
                containerColor = YamoneCream,
                topBar = { MainTopBar(mascot, themeMode) },
                bottomBar = {
                    Column {
                        if (!adRemoved && screen == AppScreen.HOME) {
                            DevelopmentBannerAd(themeMode)
                        }
                        MainBottomBar(screen, themeMode) { selected ->
                            screenName = selected.name
                            refreshKey++
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (screen) {
                        AppScreen.HOME -> HomeScreen(
                            themeMode = themeMode,
                            mascot = mascot,
                            stats = stats,
                            arcadeRecords = arcadeRecords,
                            adRemoved = adRemoved,
                            adFreeUntilMillis = adFreeUntilMillis,
                            onAdAccess = { showAdDetails = true },
                            onSudoku = { requestGameStart(AppScreen.SUDOKU) },
                            onIceJump = { requestGameStart(AppScreen.ICE_JUMP) },
                            onFishMunch = { requestGameStart(AppScreen.FISH_MUNCH) },
                            onSnowRush = { requestGameStart(AppScreen.SNOW_RUSH) },
                            onRecords = { screenName = AppScreen.RECORDS.name }
                        )
                        AppScreen.GAMES -> GamesScreen(
                            themeMode = themeMode,
                            adRemoved = adRemoved,
                            adFreeUntilMillis = adFreeUntilMillis,
                            onAdAccess = { showAdDetails = true },
                            onSudoku = { requestGameStart(AppScreen.SUDOKU) },
                            onIceJump = { requestGameStart(AppScreen.ICE_JUMP) },
                            onFishMunch = { requestGameStart(AppScreen.FISH_MUNCH) },
                            onSnowRush = { requestGameStart(AppScreen.SNOW_RUSH) }
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
                            adRemoved = adRemoved,
                            adFreeUntilMillis = adFreeUntilMillis,
                            onAdAccess = { showAdDetails = true },
                            onOnlineRankingEnabledChange = { enabled ->
                                if (enabled && nickname == ArcadeRecordStorage.DEFAULT_NICKNAME) {
                                    showRankingNickname = true
                                } else {
                                    rankingRepository.setEnabled(enabled)
                                    onlineRankingEnabled = enabled
                                    scope.launch { rankingRepository.syncSharingState(nickname) }
                                }
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

        shareRequest?.let { request ->
            ShareCardScreen(
                request = request,
                themeMode = themeMode,
                mascot = mascot,
                onBack = { shareRequest = null }
            )
        }

        if (showAdDetails && !adRemoved) {
            AdAccessDetailsDialog(
                themeMode = themeMode,
                adFreeUntilMillis = adFreeUntilMillis,
                onDismiss = { showAdDetails = false },
                onRewardedAd = {
                    showAdDetails = false
                    showRewardedTestAd = true
                },
                onPurchaseAdRemoval = {
                    adInfoMessage = "Google Play / App Store 광고 제거 구매 연결 영역이에요. 현재 개발 버전에서는 실제 결제를 실행하지 않아요."
                },
                onRedeemPromo = {
                    adInfoMessage = "프로모션 코드는 Google Play / App Store 정책에 맞춘 검증 연결 후 활성화돼요."
                }
            )
        }

        if (showInterstitialTestAd) {
            DevelopmentInterstitialAdDialog(
                themeMode = themeMode,
                onComplete = {
                    adAccessStore.addMinutes(30)
                    adRevision++
                    showInterstitialTestAd = false
                    val target = pendingGameName?.let { runCatching { AppScreen.valueOf(it) }.getOrNull() }
                    pendingGameName = null
                    target?.let { screenName = it.name }
                }
            )
        }

        if (showRewardedTestAd) {
            DevelopmentRewardedAdDialog(
                themeMode = themeMode,
                onComplete = {
                    adAccessStore.addMinutes(30)
                    adRevision++
                    showRewardedTestAd = false
                    showAdDetails = true
                },
                onCancel = {
                    showRewardedTestAd = false
                    showAdDetails = true
                }
            )
        }

        if (showRankingNickname) {
            RankingNicknameDialog(
                themeMode = themeMode,
                initialNickname = nickname,
                onDismiss = { showRankingNickname = false },
                onConfirm = { newNickname ->
                    onNicknameChange(newNickname)
                    rankingRepository.setEnabled(true)
                    onlineRankingEnabled = true
                    showRankingNickname = false
                    scope.launch { rankingRepository.syncSharingState(newNickname) }
                }
            )
        }

        adInfoMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { adInfoMessage = null },
                shape = RoundedCornerShape(24.dp),
                title = { Text("광고 설정", fontWeight = FontWeight.Black, color = YamoneInk) },
                text = { Text(message, fontSize = 12.sp, color = YamoneMuted) },
                confirmButton = {
                    TextButton(onClick = { adInfoMessage = null }) {
                        Text("확인", color = yamonePrimaryDark(themeMode), fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

@Composable
private fun MainTopBar(mascot: YamoneMascot, themeMode: YamoneThemeMode) {
    Surface(color = Color.White, shadowElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().height(78.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("야모네 게임", fontSize = 27.sp, fontWeight = FontWeight.Black, color = yamonePrimaryDark(themeMode))
                Text("작고 귀여운 게임들", fontSize = 12.sp, color = YamoneMuted)
            }
            Spacer(Modifier.weight(1f))
            YamoneMascotIcon(mascot, size = 58.dp, accent = yamonePrimary(themeMode))
        }
    }
}

@Composable
private fun MainBottomBar(screen: AppScreen, themeMode: YamoneThemeMode, onSelect: (AppScreen) -> Unit) {
    val tabs = listOf(
        Triple(AppScreen.HOME, "⌂", "홈"),
        Triple(AppScreen.GAMES, "▦", "게임"),
        Triple(AppScreen.RECORDS, "▥", "기록"),
        Triple(AppScreen.SETTINGS, "⚙", "설정")
    )
    NavigationBar(containerColor = Color.White) {
        tabs.forEach { (target, symbol, label) ->
            NavigationBarItem(
                selected = screen == target,
                onClick = { onSelect(target) },
                icon = { Text(symbol, fontSize = 18.sp) },
                label = { Text(label, fontSize = 11.sp) },
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
    arcadeRecords: Map<ArcadeGameId, List<ArcadeRecord>>,
    adRemoved: Boolean,
    adFreeUntilMillis: Long,
    onAdAccess: () -> Unit,
    onSudoku: () -> Unit,
    onIceJump: () -> Unit,
    onFishMunch: () -> Unit,
    onSnowRush: () -> Unit,
    onRecords: () -> Unit
) {
    val games = listOf(
        GameListItem("스도쿠", "숫자로 채우는 똑똑한 두뇌 운동", "9×9", onSudoku),
        GameListItem("빙하 점프", "빙하를 넘어 더 멀리 올라가요", "▲", onIceJump),
        GameListItem("물고기 냠냠", "좌우로 움직여 물고기를 받아먹어요", "≈", onFishMunch),
        GameListItem("눈덩이 러시", "눈덩이와 눈송이를 피해 오래 버텨요", "❄", onSnowRush)
    )
    val pairedRecords = arcadeRecords.flatMap { (game, records) -> records.map { game to it } }
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val todayRecords = pairedRecords.count { (_, record) ->
        Instant.ofEpochMilli(record.endedAtEpochMillis).atZone(zone).toLocalDate() == today
    }
    val latest = pairedRecords.maxByOrNull { it.second.endedAtEpochMillis }
    val latestName = latest?.first?.let(::arcadeGameTitle) ?: "아직 없음"
    val playedGames = arcadeRecords.count { it.value.isNotEmpty() } + if (stats.totalCompleted > 0) 1 else 0
    val bestKinds = arcadeRecords.count { it.value.isNotEmpty() } + stats.difficultyStats.count { it.bestSeconds != null }
    val storedRecords = pairedRecords.size + stats.totalCompleted

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White, shadowElevation = 1.dp) {
            Column(Modifier.fillMaxWidth().padding(17.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("오늘 한눈에", fontSize = 19.sp, fontWeight = FontWeight.Black, color = YamoneInk)
                    Spacer(Modifier.weight(1f))
                    Text("오늘도 즐겁게 ♡", fontSize = 12.sp, color = yamonePrimaryDark(themeMode))
                }
                Spacer(Modifier.height(13.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniSummary(Modifier.weight(1f), "오늘 기록", "${todayRecords}개", themeMode)
                    MiniSummary(Modifier.weight(1f), "최고기록", "${bestKinds}개", themeMode)
                    MiniSummary(Modifier.weight(1f), "최근 게임", latestName, themeMode, compact = true)
                }
            }
        }

        if (!adRemoved) {
            AdFreeTimeCard(themeMode, adFreeUntilMillis, onAdAccess)
        }

        SectionTitle("바로가기", "지금, 한 판 어때요?", themeMode)
        GameListCard(games = games, themeMode = themeMode)

        Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onRecords),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 1.dp
        ) {
            Column(Modifier.fillMaxWidth().padding(17.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("통계 / 기록", fontSize = 19.sp, fontWeight = FontWeight.Black, color = YamoneInk)
                    Spacer(Modifier.weight(1f))
                    Text("더 보기  ›", fontSize = 12.sp, color = YamoneMuted)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniSummary(Modifier.weight(1f), "스도쿠 완료", "${stats.totalCompleted}판", themeMode)
                    MiniSummary(Modifier.weight(1f), "플레이한 게임", "${playedGames}개", themeMode)
                    MiniSummary(Modifier.weight(1f), "보관 기록", "${storedRecords}개", themeMode)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun GamesScreen(
    themeMode: YamoneThemeMode,
    adRemoved: Boolean,
    adFreeUntilMillis: Long,
    onAdAccess: () -> Unit,
    onSudoku: () -> Unit,
    onIceJump: () -> Unit,
    onFishMunch: () -> Unit,
    onSnowRush: () -> Unit
) {
    val puzzle = listOf(GameListItem("스도쿠", "숫자로 채우는 9×9 퍼즐", "9×9", onSudoku))
    val arcade = listOf(
        GameListItem("빙하 점프", "자동 점프 · 드래그로 방향 이동", "▲", onIceJump),
        GameListItem("물고기 냠냠", "일반 / 60초 타임어택", "≈", onFishMunch),
        GameListItem("눈덩이 러시", "쏟아지는 눈을 피해 오래 생존", "❄", onSnowRush)
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Surface(shape = RoundedCornerShape(23.dp), color = yamonePrimarySoft(themeMode)) {
            Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = Color.White) {
                    Text("▶", modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp), color = yamonePrimaryDark(themeMode), fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("게임을 누르면 바로 시작돼요", fontSize = 17.sp, fontWeight = FontWeight.Black, color = YamoneInk)
                    Text("첫 게임은 바로 시작하고, 이후에는 남은 전면광고 없는 시간을 확인해요 ♡", fontSize = 12.sp, color = YamoneMuted)
                }
            }
        }

        if (!adRemoved) {
            AdFreeTimeCard(themeMode, adFreeUntilMillis, onAdAccess)
        }

        SectionTitle("퍼즐", "두뇌를 깨우는 즐거움", themeMode)
        GameListCard(puzzle, themeMode)

        SectionTitle("아케이드", "짧고 신나게 한 판!", themeMode)
        GameListCard(arcade, themeMode)

        Surface(shape = RoundedCornerShape(23.dp), color = Color.White) {
            Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(13.dp), color = Color(0xFFF1F4F4)) {
                    Text("+", modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp), fontSize = 22.sp, fontWeight = FontWeight.Black, color = YamoneMuted)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("준비 중인 게임", fontSize = 17.sp, fontWeight = FontWeight.Black, color = YamoneInk)
                    Text("새로운 퍼즐과 아케이드를 하나씩 추가할게요.", fontSize = 12.sp, color = YamoneMuted)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun SectionTitle(title: String, trailing: String, themeMode: YamoneThemeMode) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(yamonePrimary(themeMode), RoundedCornerShape(50)))
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Black, color = YamoneInk)
        Spacer(Modifier.weight(1f))
        Text(trailing, fontSize = 12.sp, color = YamoneMuted)
    }
}

@Composable
private fun GameListCard(games: List<GameListItem>, themeMode: YamoneThemeMode) {
    Surface(shape = RoundedCornerShape(24.dp), color = Color.White, shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth()) {
            games.forEachIndexed { index, game ->
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = game.onClick).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (index % 2 == 0) yamonePrimarySoft(themeMode) else yamoneSecondarySoft(themeMode)
                    ) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            Text(game.symbol, fontSize = if (game.symbol == "9×9") 12.sp else 20.sp, fontWeight = FontWeight.Black, color = YamoneInk)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(game.title, fontSize = 16.sp, fontWeight = FontWeight.Black, color = YamoneInk)
                        Text(game.subtitle, fontSize = 12.sp, color = YamoneMuted)
                    }
                    Text("›", fontSize = 27.sp, color = YamoneMuted)
                }
                if (index != games.lastIndex) HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = Color(0xFFEAF0EF))
            }
        }
    }
}

@Composable
private fun MiniSummary(
    modifier: Modifier,
    label: String,
    value: String,
    themeMode: YamoneThemeMode,
    compact: Boolean = false
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = yamonePrimarySoft(themeMode).copy(alpha = .55f)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 10.sp, color = YamoneMuted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(3.dp))
            Text(
                value,
                fontSize = if (compact) 12.sp else 16.sp,
                fontWeight = FontWeight.Black,
                color = YamoneInk,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
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
                    Text("아케이드 좋은 기록은 게임별 5개까지만 보관해요 ♡", fontSize = 13.sp, color = YamoneMuted)
                }
            }
        }

        if (onlineRankingEnabled) OnlineRankingEntryCard(themeMode = themeMode, onClick = onOnlineRanking)

        Text("스도쿠", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
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
                    Text("완료 ${item.completed}판", fontSize = 12.sp, color = YamoneMuted)
                    Spacer(Modifier.weight(1f))
                    Text(item.bestSeconds?.let(::formatDuration) ?: "—", fontWeight = FontWeight.ExtraBold, color = YamoneInk)
                }
            }
        }

        ArcadeRecordSection(ArcadeGameId.ICE_JUMP, arcadeRecords[ArcadeGameId.ICE_JUMP].orEmpty(), themeMode, onShare)
        ArcadeRecordSection(ArcadeGameId.FISH_MUNCH, arcadeRecords[ArcadeGameId.FISH_MUNCH].orEmpty(), themeMode, onShare)
        ArcadeRecordSection(ArcadeGameId.FISH_MUNCH_TIME_ATTACK, arcadeRecords[ArcadeGameId.FISH_MUNCH_TIME_ATTACK].orEmpty(), themeMode, onShare)
        ArcadeRecordSection(ArcadeGameId.SNOW_RUSH, arcadeRecords[ArcadeGameId.SNOW_RUSH].orEmpty(), themeMode, onShare)
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
    Text(arcadeGameTitle(game), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
    if (records.isEmpty()) {
        Surface(shape = RoundedCornerShape(18.dp), color = Color.White) {
            Text("아직 기록이 없어요", modifier = Modifier.fillMaxWidth().padding(16.dp), fontSize = 12.sp, color = YamoneMuted)
        }
    } else {
        records.forEachIndexed { index, record ->
            Surface(shape = RoundedCornerShape(18.dp), color = Color.White) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(11.dp), color = yamonePrimarySoft(themeMode)) {
                        Text("${index + 1}", modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), fontWeight = FontWeight.Black, color = yamonePrimaryDark(themeMode))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(arcadeScoreText(game, record.score), fontSize = 15.sp, fontWeight = FontWeight.Black, color = YamoneInk)
                        Text(arcadeEndedAtText(record.endedAtEpochMillis), fontSize = 11.sp, color = YamoneMuted)
                        Text(record.nickname, fontSize = 11.sp, color = YamoneMuted.copy(alpha = .8f))
                    }
                    TextButton(onClick = { onShare(game, record) }) {
                        Text("공유", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = yamonePrimaryDark(themeMode))
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
    adRemoved: Boolean,
    adFreeUntilMillis: Long,
    onAdAccess: () -> Unit,
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
        Text("닉네임과 캐릭터, 색상을 골라요.", fontSize = 14.sp, color = YamoneMuted)

        if (!adRemoved) {
            Text("광고", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
            AdFreeTimeCard(themeMode, adFreeUntilMillis, onAdAccess)
        }

        Text("닉네임", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
        OutlinedTextField(
            value = nickname,
            onValueChange = { onNicknameChange(it.take(AppPreferences.MAX_NICKNAME_LENGTH)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            placeholder = { Text("야모네 플레이어") },
            supportingText = { Text("공유카드에 표시되고, 랭킹 ON일 때 온라인에도 표시돼요", fontSize = 12.sp, color = YamoneMuted) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = yamonePrimary(themeMode),
                unfocusedBorderColor = yamonePrimaryLine(themeMode),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                cursorColor = yamonePrimaryDark(themeMode)
            )
        )

        OnlineRankingSettingsSection(themeMode, onlineRankingEnabled, rankingRepository, onOnlineRankingEnabledChange)

        Surface(shape = RoundedCornerShape(26.dp), color = yamonePrimarySoft(themeMode)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                YamoneMascotIcon(mascot, size = 92.dp, accent = yamonePrimary(themeMode))
                Spacer(Modifier.height(8.dp))
                Text("선택한 캐릭터가 홈과 게임 화면에도 바로 적용돼요 ♡", fontSize = 12.sp, color = YamoneMuted)
            }
        }

        Text("캐릭터", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            YamoneMascot.entries.forEach { option ->
                SelectorCard(Modifier.weight(1f), mascot == option, themeMode, { onMascotChange(option) }) {
                    YamoneMascotIcon(option, size = 76.dp, accent = yamonePrimary(themeMode))
                }
            }
        }

        Text("색상", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
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
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) yamonePrimary(themeMode) else Color(0xFFE4ECEA))
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

@Composable
private fun BigStatCard(modifier: Modifier, label: String, value: String, themeMode: YamoneThemeMode) {
    Surface(modifier = modifier, shape = RoundedCornerShape(20.dp), color = Color.White) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 23.sp, fontWeight = FontWeight.Black, color = yamonePrimaryDark(themeMode))
            Text(label, fontSize = 12.sp, color = YamoneMuted)
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
