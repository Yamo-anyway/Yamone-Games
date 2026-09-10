from pathlib import Path
import re


def replace_once(path: str, old: str, new: str, label: str):
    p = Path(path)
    s = p.read_text()
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    p.write_text(s.replace(old, new, 1))


def regex_once(path: str, pattern: str, repl: str, label: str, flags=0):
    p = Path(path)
    s = p.read_text()
    new, count = re.subn(pattern, repl, s, count=1, flags=flags)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 regex match, found {count}")
    p.write_text(new)


APP = "app/src/main/java/com/yamone/games/YamoneGamesApp.kt"
RANK = "app/src/main/java/com/yamone/games/OnlineRankingUi.kt"
MAIN = "app/src/main/java/com/yamone/games/MainActivity.kt"
ICE = "games/icejump/src/main/java/com/yamone/games/icejump/IceJumpScreen.kt"
FISH = "games/fishmunch/src/main/java/com/yamone/games/fishmunch/FishMunchScreen.kt"
SNOW = "games/snowrush/src/main/java/com/yamone/games/snowrush/SnowRushScreen.kt"
BUILD = "app/build.gradle.kts"
WORKER = "cloudflare/ranking-worker.js"

# --- YamoneGamesApp: ranking becomes always-on app feature, sharing UI disappears. ---
replace_once(APP, "private const val ONLINE_RANKING_VISIBLE = false", "private const val ONLINE_RANKING_VISIBLE = true", "enable ranking feature")
replace_once(APP, "    var shareRequest by remember { mutableStateOf<ShareCardRequest?>(null) }\n", "", "remove share request state")
replace_once(APP, "    var onlineRankingEnabled by remember { mutableStateOf(false) }", "    var onlineRankingEnabled by remember { mutableStateOf(true) }", "ranking state on")

replace_once(
    APP,
    '''    BackHandler(enabled = screen != AppScreen.HOME && shareRequest == null && !showInterstitialTestAd && !showRewardedTestAd) {
        refreshKey++
        screenName = if (screen == AppScreen.ONLINE_RANKING) AppScreen.RECORDS.name else AppScreen.HOME.name
    }
    BackHandler(enabled = shareRequest != null) { shareRequest = null }
''',
    '''    BackHandler(enabled = screen != AppScreen.HOME && !showInterstitialTestAd && !showRewardedTestAd) {
        refreshKey++
        screenName = AppScreen.HOME.name
    }
''',
    "back handler cleanup",
)

replace_once(
    APP,
    '''    LaunchedEffect(Unit) {
    if (ONLINE_RANKING_VISIBLE) {
        if (onlineRankingEnabled) rankingRepository.setEnabled(true)
        rankingRepository.syncSharingState(nickname)
    }
}
''',
    '''    LaunchedEffect(Unit) {
        rankingRepository.setEnabled(true)
        rankingRepository.syncSharingState(nickname)
    }
''',
    "ranking startup",
)
replace_once(
    APP,
    '                if (ONLINE_RANKING_VISIBLE) scope.launch { rankingRepository.syncSharingState(nickname) }',
    '                scope.launch { rankingRepository.syncSharingState(nickname) }',
    "ranking reconnect",
)
replace_once(
    APP,
    '''            if (onlineRankingEnabled && best > previous) {
                scope.launch { rankingRepository.onLocalBestChanged(changedGame, best, nickname) }
            }
''',
    '''            if (best > previous) {
                scope.launch { rankingRepository.onLocalBestChanged(changedGame, best, nickname) }
            }
''',
    "ranking local best listener",
)
replace_once(
    APP,
    '    val arcadeRecords = remember(refreshKey, screenName, shareRequest) {',
    '    val arcadeRecords = remember(refreshKey, screenName) {',
    "records remember keys",
)

# Remove share callbacks from active games.
replace_once(APP, '                onShareRecord = { shareRequest = ShareCardRequest(ArcadeGameId.ICE_JUMP, it) },\n', '', 'ice share call')
replace_once(APP, '                onShareRecord = { game, record -> shareRequest = ShareCardRequest(game, record) },\n', '', 'fish share call')
replace_once(APP, '                onShareRecord = { shareRequest = ShareCardRequest(ArcadeGameId.SNOW_RUSH, it) },\n', '', 'snow share call')

# Repurpose the former Records destination as the permanent Ranking tab.
old_records_call = '''                        AppScreen.RECORDS -> RecordsScreen(
                            themeMode = themeMode,
                            mascot = mascot,
                            stats = stats,
                            arcadeRecords = arcadeRecords,
                            onlineRankingEnabled = onlineRankingEnabled,
                            onOnlineRanking = { screenName = AppScreen.ONLINE_RANKING.name },
                            onShare = { game, record -> shareRequest = ShareCardRequest(game, record) }
                        )
'''
replace_once(
    APP,
    old_records_call,
    '''                        AppScreen.RECORDS -> RankingTabScreen(
                            themeMode = themeMode,
                            repository = rankingRepository
                        )
''',
    "records to ranking tab",
)

# Remove the obsolete full-screen share-card overlay.
regex_once(
    APP,
    r'''\n\s*shareRequest\?\.let \{ request ->\n\s*ShareCardScreen\(\n\s*request = request,\n\s*themeMode = themeMode,\n\s*mascot = mascot,\n\s*onBack = \{ shareRequest = null \}\n\s*\)\n\s*\}\n''',
    '\n',
    "share card overlay",
    flags=re.MULTILINE,
)

# No ranking sharing on/off section in Settings; ranking is just part of the game now.
replace_once(
    APP,
    '''        if (ONLINE_RANKING_VISIBLE) {
            OnlineRankingSettingsSection(themeMode, onlineRankingEnabled, rankingRepository, onOnlineRankingEnabledChange)
        }

''',
    '',
    "ranking settings toggle",
)
replace_once(APP, 'supportingText = { Text("기록 공유카드에 표시돼요", fontSize = 12.sp, color = YamoneMuted) },', 'supportingText = { Text("랭킹에 표시돼요", fontSize = 12.sp, color = YamoneMuted) },', 'nickname helper text')

# Home: only the game shortcuts remain; remove Today-at-a-glance and statistics/records cards.
regex_once(
    APP,
    r'''@Composable\nprivate fun HomeScreen\([\s\S]*?\n\}\n\n@Composable\nprivate fun GamesScreen''',
    '''@Composable
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
        GameListItem("스도쿠", GameIconKind.SUDOKU, onSudoku),
        GameListItem("빙하 점프", GameIconKind.ICE_JUMP, onIceJump),
        GameListItem("물고기 냠냠", GameIconKind.FISH_MUNCH, onFishMunch),
        GameListItem("눈덩이 러시", GameIconKind.SNOW_RUSH, onSnowRush),
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!adRemoved && !DEV_AD_TIMER_BYPASS) {
            AdFreeTimeCard(themeMode, adFreeUntilMillis, onAdAccess)
        }
        SectionTitle("바로가기", "", themeMode)
        GameListCard(games = games, themeMode = themeMode)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun GamesScreen''',
    "simplify home",
    flags=re.MULTILINE,
)

# Bottom navigation: exactly Home + Ranking, full theme background, black glyph/text,
# selected area is a darker rounded patch inside the theme-colored bar.
regex_once(
    APP,
    r'''private enum class BottomNavIconKind \{[\s\S]*?\n\}\n\n@Composable\nprivate fun HomeScreen''',
    '''private enum class BottomNavIconKind { HOME, RANKING }

@Composable
private fun MainBottomBar(screen: AppScreen, themeMode: YamoneThemeMode, onSelect: (AppScreen) -> Unit) {
    val tabs = listOf(
        Triple(AppScreen.HOME, BottomNavIconKind.HOME, "홈"),
        Triple(AppScreen.RECORDS, BottomNavIconKind.RANKING, "랭킹")
    )
    val barColor = yamonePrimary(themeMode)
    val selectedColor = yamonePrimaryDark(themeMode).copy(alpha = .24f)

    Surface(
        color = barColor,
        shadowElevation = 6.dp
    ) {
        Row(
            Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { (target, kind, label) ->
                val selected = screen == target
                Surface(
                    modifier = Modifier.weight(1f).height(58.dp),
                    onClick = { onSelect(target) },
                    shape = RoundedCornerShape(19.dp),
                    color = if (selected) selectedColor else Color.Transparent
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        BottomNavGlyph(kind = kind, color = YamoneInk)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            label,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                            color = YamoneInk
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNavGlyph(kind: BottomNavIconKind, color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val stroke = 2.4.dp.toPx()
        when (kind) {
            BottomNavIconKind.HOME -> {
                drawLine(color, Offset(size.width * .12f, size.height * .48f), Offset(size.width * .50f, size.height * .16f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * .50f, size.height * .16f), Offset(size.width * .88f, size.height * .48f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * .25f, size.height * .43f),
                    size = Size(size.width * .50f, size.height * .40f),
                    cornerRadius = CornerRadius(size.width * .08f),
                    style = Stroke(width = stroke)
                )
                drawLine(color, Offset(size.width * .50f, size.height * .61f), Offset(size.width * .50f, size.height * .83f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            BottomNavIconKind.RANKING -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * .10f, size.height * .57f),
                    size = Size(size.width * .20f, size.height * .27f),
                    cornerRadius = CornerRadius(size.width * .04f),
                    style = Stroke(width = stroke)
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * .40f, size.height * .34f),
                    size = Size(size.width * .20f, size.height * .50f),
                    cornerRadius = CornerRadius(size.width * .04f),
                    style = Stroke(width = stroke)
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * .70f, size.height * .48f),
                    size = Size(size.width * .20f, size.height * .36f),
                    cornerRadius = CornerRadius(size.width * .04f),
                    style = Stroke(width = stroke)
                )
                drawCircle(
                    color = color,
                    radius = size.width * .075f,
                    center = Offset(size.width * .50f, size.height * .16f),
                    style = Stroke(width = stroke)
                )
            }
        }
    }
}

@Composable
private fun HomeScreen''',
    "bottom nav redesign",
    flags=re.MULTILINE,
)

# Remove obsolete date imports now that home summary cards are gone.
for imp in ["import java.time.Instant\n", "import java.time.LocalDate\n", "import java.time.ZoneId\n"]:
    p = Path(APP)
    s = p.read_text()
    p.write_text(s.replace(imp, ""))

# --- New Ranking tab UI appended before the existing country helper. ---
p = Path(RANK)
s = p.read_text()
if "import androidx.activity.compose.BackHandler\n" not in s:
    s = s.replace("package com.yamone.games\n\n", "package com.yamone.games\n\nimport androidx.activity.compose.BackHandler\n", 1)
marker = "private fun countryFlag(countryCode: String): String {"
if marker not in s:
    raise SystemExit("ranking country helper marker missing")
new_ui = r'''
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
            Surface(
                onClick = { selectedName = null },
                shape = RoundedCornerShape(14.dp),
                color = yamonePrimaryDark(themeMode).copy(alpha = .13f)
            ) {
                Text(
                    "‹",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 1.dp),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    color = YamoneInk
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Ranking", fontSize = 24.sp, fontWeight = FontWeight.Black, color = YamoneInk)
                Text(arcadeGameTitle(selected), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = yamonePrimaryDark(themeMode))
            }
        }

        when (val result = loadResult) {
            null -> RankingMessageCard(themeMode, "랭킹을 불러오는 중이에요…", showProgress = true)
            OnlineRankingLoadResult.Disabled -> RankingMessageCard(themeMode, "랭킹을 준비하고 있어요.", onRetry = { reloadKey++ })
            OnlineRankingLoadResult.Offline -> RankingMessageCard(themeMode, "인터넷에 연결되면 랭킹을 볼 수 있어요.", onRetry = { reloadKey++ })
            OnlineRankingLoadResult.ServerUnavailable -> RankingMessageCard(themeMode, "랭킹 서버에 연결할 수 없어요.", onRetry = { reloadKey++ })
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
        Text("Ranking", fontSize = 26.sp, fontWeight = FontWeight.Black, color = YamoneInk)
        Text("게임을 선택해 랭킹을 확인해요", fontSize = 12.sp, color = YamoneMuted)
        Spacer(Modifier.height(2.dp))
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White, shadowElevation = 1.dp) {
            Column(Modifier.fillMaxWidth()) {
                games.forEachIndexed { index, game ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(game) }.padding(horizontal = 16.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(9.dp).background(yamonePrimary(themeMode), RoundedCornerShape(50))
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            arcadeGameTitle(game),
                            modifier = Modifier.weight(1f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = YamoneInk
                        )
                        Text("›", fontSize = 27.sp, color = YamoneInk.copy(alpha = .55f))
                    }
                    if (index != games.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = yamonePrimaryDark(themeMode).copy(alpha = .10f)
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
        RankingMessageCard(themeMode, "아직 등록된 랭킹이 없어요.")
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

'''
s = s.replace(marker, new_ui + marker, 1)
p.write_text(s)

# --- Game result overlays: remove share button and share callback entirely. ---
replace_once(ICE, "    onShareRecord: (ArcadeRecord) -> Unit,\n", "", "ice share parameter")
regex_once(
    ICE,
    r'''\n\s*Button\(\n\s*onClick = \{ lastRecord\?\.let\(onShareRecord\) \},[\s\S]*?\n\s*\) \{ Text\("공유", fontSize = 12\.sp, fontWeight = FontWeight\.Bold\) \}\n''',
    '\n',
    "ice share button",
)

replace_once(FISH, "    onShareRecord: (ArcadeGameId, ArcadeRecord) -> Unit,\n", "", "fish share parameter")
replace_once(FISH, "                    onShare = { lastRecord?.let { onShareRecord(state.mode.gameId, it) } },\n", "", "fish share call")
replace_once(FISH, "                    shareEnabled = lastRecord != null\n", "", "fish share enabled call")
replace_once(FISH, "    onShare: () -> Unit,\n", "", "fish result share parameter")
replace_once(FISH, "    shareEnabled: Boolean\n", "", "fish result share enabled parameter")
regex_once(
    FISH,
    r'''\n\s*Button\(\n\s*onClick = onShare,\n\s*enabled = shareEnabled,[\s\S]*?\n\s*\) \{ Text\("공유", fontSize = 12\.sp, fontWeight = FontWeight\.Bold\) \}\n''',
    '\n',
    "fish share button",
)
# Remove dangling comma before close after signature cleanup.
p = Path(FISH)
s = p.read_text().replace("    onExit: () -> Unit,\n) {", "    onExit: () -> Unit\n) {")
s = s.replace("                    onExit = ::selectModeAgain,\n                )", "                    onExit = ::selectModeAgain\n                )")
p.write_text(s)

replace_once(SNOW, "    onShareRecord: (ArcadeRecord) -> Unit,\n", "", "snow share parameter")
replace_once(SNOW, "                    onShare = { lastRecord?.let(onShareRecord) },\n", "", "snow share call")
replace_once(SNOW, "                    shareEnabled = lastRecord != null\n", "", "snow share enabled call")
replace_once(SNOW, "    onShare: () -> Unit,\n", "", "snow result share parameter")
replace_once(SNOW, "    shareEnabled: Boolean\n", "", "snow result share enabled parameter")
regex_once(
    SNOW,
    r'''\n\s*Button\(\n\s*onClick = onShare,\n\s*enabled = shareEnabled,[\s\S]*?\n\s*\) \{ Text\("공유", fontSize = 12\.sp, fontWeight = FontWeight\.Bold\) \}\n''',
    '\n',
    "snow share button",
)
p = Path(SNOW)
s = p.read_text().replace("    onExit: () -> Unit,\n) {", "    onExit: () -> Unit\n) {")
s = s.replace("                    onExit = onBack,\n                )", "                    onExit = onBack\n                )")
p.write_text(s)

# --- Android system navigation/gesture area matches the solid selected theme color. ---
p = Path(MAIN)
s = p.read_text()
if "import com.yamone.games.sudoku.ui.theme.yamonePrimary\n" not in s:
    s = s.replace(
        "import com.yamone.games.sudoku.ui.theme.YamoneSudokuTheme\n",
        "import com.yamone.games.sudoku.ui.theme.YamoneSudokuTheme\nimport com.yamone.games.sudoku.ui.theme.yamonePrimary\n",
        1,
    )
s = s.replace("val navigationBarBackground = yamonePrimarySoft(themeMode)", "val navigationBarBackground = yamonePrimary(themeMode)", 1)
p.write_text(s)

# --- Worker source: ranking is now enabled, and nearby response matches +/-2 requirement. ---
replace_once(WORKER, "const RANKING_ENABLED = false;", "const RANKING_ENABLED = true;", "worker ranking enabled")
replace_once(WORKER, "  const fromRank = Math.max(1, myRank - 3);", "  const fromRank = Math.max(1, myRank - 2);", "worker nearby upper")
replace_once(WORKER, "  const toRank = Math.min(totalPlayers, myRank + 3);", "  const toRank = Math.min(totalPlayers, myRank + 2);", "worker nearby lower")

# --- Version ---
replace_once(BUILD, "versionCode = 58", "versionCode = 59", "version code")
replace_once(BUILD, 'versionName = "1.1.0-dev52"', 'versionName = "1.1.0-dev53"', "version name")

print("dev53 ranking + theme patch applied")
