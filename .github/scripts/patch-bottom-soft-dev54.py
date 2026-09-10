from pathlib import Path
import re


def read(path):
    return Path(path).read_text()


def write(path, text):
    Path(path).write_text(text)


def replace_once(path: str, old: str, new: str, label: str):
    s = read(path)
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    write(path, s.replace(old, new, 1))


def regex_once(path: str, pattern: str, repl: str, label: str, flags=0):
    s = read(path)
    new, count = re.subn(pattern, repl, s, count=1, flags=flags)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 regex match, found {count}")
    write(path, new)


APP = "app/src/main/java/com/yamone/games/YamoneGamesApp.kt"
PREFS = "app/src/main/java/com/yamone/games/AppPreferences.kt"
MAIN = "app/src/main/java/com/yamone/games/MainActivity.kt"
RANK = "app/src/main/java/com/yamone/games/OnlineRankingUi.kt"
SUPPORT = "app/src/main/java/com/yamone/games/OnlineRankingSupport.kt"
BUILD = "app/build.gradle.kts"

# ---------------------------------------------------------------------------
# 1) Soft theme frame: bottom app nav + Android gesture/navigation inset.
# ---------------------------------------------------------------------------
replace_once(
    APP,
    "    val barColor = yamonePrimary(themeMode)\n    val selectedColor = yamonePrimaryDark(themeMode).copy(alpha = .24f)",
    "    // Match the bottom bar itself to the same soft mint/pink page background.\n    val barColor = yamonePrimarySoft(themeMode)\n    // Only the selected tab gets a slightly stronger rounded patch.\n    val selectedColor = yamonePrimary(themeMode).copy(alpha = .16f)",
    "soft bottom navigation",
)
replace_once(
    APP,
    "        shadowElevation = 6.dp\n    ) {\n        Row(\n            Modifier.fillMaxWidth().height(72.dp)",
    "        shadowElevation = 2.dp\n    ) {\n        Row(\n            Modifier.fillMaxWidth().height(72.dp)",
    "lighter bottom shadow",
)
replace_once(
    MAIN,
    "            val navigationBarBackground = yamonePrimary(themeMode)",
    "            val navigationBarBackground = yamonePrimarySoft(themeMode)",
    "soft Android navigation bar",
)

# ---------------------------------------------------------------------------
# 2) Nickname: explicit configured state survives updates and gates first game.
# ---------------------------------------------------------------------------
replace_once(
    PREFS,
    '''    fun nickname(): String = prefs
        .getString("nickname", ArcadeRecordStorage.DEFAULT_NICKNAME)
        .orEmpty()
        .trim()
        .ifBlank { ArcadeRecordStorage.DEFAULT_NICKNAME }
''',
    '''    fun nickname(): String = prefs
        .getString("nickname", ArcadeRecordStorage.DEFAULT_NICKNAME)
        .orEmpty()
        .trim()
        .ifBlank { ArcadeRecordStorage.DEFAULT_NICKNAME }

    fun hasNickname(): Boolean = prefs.contains("nickname") &&
        prefs.getString("nickname", "").orEmpty().trim().isNotBlank()
''',
    "nickname configured getter",
)
replace_once(
    PREFS,
    '''    fun setNickname(nickname: String) {
        prefs.edit().putString("nickname", nickname.take(MAX_NICKNAME_LENGTH)).apply()
    }
''',
    '''    fun setNickname(nickname: String) {
        val value = nickname.trim().take(MAX_NICKNAME_LENGTH)
        if (value.isNotBlank()) prefs.edit().putString("nickname", value).apply()
    }
''',
    "nickname setter trim",
)

replace_once(
    MAIN,
    "            var nickname by remember { mutableStateOf(prefs.nickname()) }\n",
    "            var nickname by remember { mutableStateOf(prefs.nickname()) }\n            var nicknameConfigured by remember { mutableStateOf(prefs.hasNickname()) }\n",
    "main nickname configured state",
)
replace_once(
    MAIN,
    '''                            nickname = nickname,
                            onThemeChange = {
''',
    '''                            nickname = nickname,
                            nicknameConfigured = nicknameConfigured,
                            onThemeChange = {
''',
    "pass nickname configured",
)
replace_once(
    MAIN,
    '''                            onNicknameChange = {
                                nickname = it.take(AppPreferences.MAX_NICKNAME_LENGTH)
                                prefs.setNickname(nickname)
                            }
''',
    '''                            onNicknameChange = {
                                val saved = it.trim().take(AppPreferences.MAX_NICKNAME_LENGTH)
                                if (saved.isNotBlank()) {
                                    nickname = saved
                                    prefs.setNickname(saved)
                                    nicknameConfigured = true
                                }
                            }
''',
    "main nickname save",
)

replace_once(
    APP,
    '''    mascot: YamoneMascot,
    nickname: String,
    onThemeChange: (YamoneThemeMode) -> Unit,
''',
    '''    mascot: YamoneMascot,
    nickname: String,
    nicknameConfigured: Boolean,
    onThemeChange: (YamoneThemeMode) -> Unit,
''',
    "app nickname configured param",
)
replace_once(
    APP,
    '''    var pendingGameName by rememberSaveable { mutableStateOf<String?>(null) }
    var showRankingNickname by remember { mutableStateOf(false) }
''',
    '''    var pendingGameName by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingNicknameGameName by rememberSaveable { mutableStateOf<String?>(null) }
    var showRequiredNickname by rememberSaveable { mutableStateOf(false) }
    var showRankingNickname by remember { mutableStateOf(false) }
''',
    "nickname gate state",
)

# Split actual game start from nickname gate so DEV ad bypass cannot skip nickname setup.
regex_once(
    APP,
    r'''    val requestGameStart: \(AppScreen\) -> Unit = \{ target ->\n([\s\S]*?)\n    \}\n\n    LaunchedEffect\(Unit\) \{''',
    '''    val startGameAfterNickname: (AppScreen) -> Unit = { target ->
\1
    }

    val requestGameStart: (AppScreen) -> Unit = { target ->
        if (!nicknameConfigured) {
            pendingNicknameGameName = target.name
            showRequiredNickname = true
        } else {
            startGameAfterNickname(target)
        }
    }

    LaunchedEffect(nickname, nicknameConfigured) {''',
    "nickname gate game start",
    flags=re.MULTILINE,
)

replace_once(
    APP,
    '''    LaunchedEffect(nickname, nicknameConfigured) {
        rankingRepository.setEnabled(true)
        rankingRepository.syncSharingState(nickname)
    }
''',
    '''    LaunchedEffect(nickname, nicknameConfigured) {
        // Create the anonymous install/game ID on first launch; app updates keep the same file.
        runCatching { rankingRepository.ensurePlayerId() }
        rankingRepository.setEnabled(true)
        if (nicknameConfigured) rankingRepository.syncNickname(nickname)
    }
''',
    "ranking startup nickname aware",
)
replace_once(
    APP,
    '''            override fun onAvailable(network: Network) {
                scope.launch { rankingRepository.syncSharingState(nickname) }
            }
''',
    '''            override fun onAvailable(network: Network) {
                if (nicknameConfigured) scope.launch { rankingRepository.syncRankingState(nickname) }
            }
''',
    "network ranking sync nickname aware",
)
replace_once(
    APP,
    "    DisposableEffect(nickname) {",
    "    DisposableEffect(nickname, nicknameConfigured) {",
    "network effect key",
)
replace_once(
    APP,
    '''            if (best > previous) {
                scope.launch { rankingRepository.onLocalBestChanged(changedGame, best, nickname) }
            }
''',
    '''            if (nicknameConfigured && best > previous) {
                scope.launch { rankingRepository.onLocalBestChanged(changedGame, best, nickname) }
            }
''',
    "record submit nickname gate",
)
replace_once(
    APP,
    "    DisposableEffect(nickname, onlineRankingEnabled) {",
    "    DisposableEffect(nickname, nicknameConfigured, onlineRankingEnabled) {",
    "record effect key",
)

# Required nickname dialog: cannot be dismissed; save continues the originally selected game.
marker = '''        if (showAdDetails && !adRemoved) {
'''
insert = '''        if (showRequiredNickname) {
            NicknameEditDialog(
                themeMode = themeMode,
                initialNickname = "",
                title = "닉네임을 정해주세요",
                dismissible = false,
                onDismiss = {},
                onSave = { savedNickname ->
                    onNicknameChange(savedNickname)
                    showRequiredNickname = false
                    val target = pendingNicknameGameName
                        ?.let { runCatching { AppScreen.valueOf(it) }.getOrNull() }
                    pendingNicknameGameName = null
                    target?.let(startGameAfterNickname)
                }
            )
        }

'''
replace_once(APP, marker, insert + marker, "required nickname dialog")

# Settings nickname becomes display row + explicit Modify popup, not always-editing field.
replace_once(
    APP,
    ''') {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
''',
    ''') {
    var showNicknameEditor by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
''',
    "settings nickname editor state",
)
regex_once(
    APP,
    r'''        Text\("닉네임", fontSize = 18\.sp, fontWeight = FontWeight\.ExtraBold, color = YamoneInk\)\n        OutlinedTextField\([\s\S]*?\n        \)\n\n''',
    '''        Text("닉네임", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    nickname,
                    modifier = Modifier.weight(1f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = YamoneInk,
                    maxLines = 1
                )
                TextButton(onClick = { showNicknameEditor = true }) {
                    Text("수정", fontWeight = FontWeight.Black, color = yamonePrimaryDark(themeMode))
                }
            }
        }

        if (showNicknameEditor) {
            NicknameEditDialog(
                themeMode = themeMode,
                initialNickname = nickname,
                title = "닉네임 수정",
                dismissible = true,
                onDismiss = { showNicknameEditor = false },
                onSave = { savedNickname ->
                    onNicknameChange(savedNickname)
                    showNicknameEditor = false
                }
            )
        }

''',
    "settings nickname field to row",
    flags=re.MULTILINE,
)

# Shared nickname dialog used by first-game gate and Settings editor.
settings_end = '''@Composable
private fun SelectorCard(
'''
dialog = '''@Composable
private fun NicknameEditDialog(
    themeMode: YamoneThemeMode,
    initialNickname: String,
    title: String,
    dismissible: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var draft by remember(initialNickname) { mutableStateOf(initialNickname) }
    val value = draft.trim()
    AlertDialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, fontWeight = FontWeight.Black, color = YamoneInk) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(AppPreferences.MAX_NICKNAME_LENGTH) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("닉네임 입력") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = yamonePrimary(themeMode),
                    unfocusedBorderColor = yamonePrimaryLine(themeMode),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    cursorColor = yamonePrimaryDark(themeMode)
                )
            )
        },
        confirmButton = {
            Button(
                enabled = value.isNotBlank(),
                onClick = { onSave(value) },
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = yamonePrimary(themeMode))
            ) { Text("저장", fontWeight = FontWeight.Black) }
        },
        dismissButton = if (dismissible) {
            @Composable {
                TextButton(onClick = onDismiss) { Text("취소", color = YamoneMuted) }
            }
        } else null
    )
}

'''
replace_once(APP, settings_end, dialog + settings_end, "insert nickname edit dialog")

# Rename visible bottom destination from ranking to 순위.
s = read(APP).replace('BottomNavIconKind.RANKING, "랭킹"', 'BottomNavIconKind.RANKING, "순위"')
s = s.replace('Text("랭킹에 표시돼요"', 'Text("순위에 표시돼요"')
write(APP, s)

# ---------------------------------------------------------------------------
# 3) Anonymous game/install ID is created on first launch and reused on updates.
#    Nickname changes republish existing bests without changing the ID.
# ---------------------------------------------------------------------------
replace_once(
    SUPPORT,
    '''    fun enabled(): Boolean = store.enabled()

    fun setEnabled(enabled: Boolean) {
''',
    '''    fun enabled(): Boolean = store.enabled()

    fun ensurePlayerId(): String = store.playerId()

    fun setEnabled(enabled: Boolean) {
''',
    "expose anonymous player id",
)
replace_once(
    SUPPORT,
    '''    suspend fun onLocalBestChanged(game: ArcadeGameId, score: Int, nickname: String) {
        if (!store.enabled()) return
        store.queueBest(game, score, nickname)
        syncSharingState(nickname)
    }

    suspend fun syncSharingState(nickname: String) {
''',
    '''    suspend fun onLocalBestChanged(game: ArcadeGameId, score: Int, nickname: String) {
        if (!store.enabled()) return
        store.queueBest(game, score, nickname)
        syncRankingState(nickname)
    }

    suspend fun syncNickname(nickname: String) {
        if (!store.enabled()) return
        queueCurrentLocalBests(nickname)
        flushPending()
    }

    suspend fun syncRankingState(nickname: String) {
''',
    "rename ranking sync and nickname republish",
)
s = read(SUPPORT).replace("syncSharingState(nickname)", "syncRankingState(nickname)")
write(SUPPORT, s)

# Any leftover app call uses the renamed method.
s = read(APP).replace("rankingRepository.syncSharingState(", "rankingRepository.syncRankingState(")
write(APP, s)

# ---------------------------------------------------------------------------
# 4) Ranking terminology -> 순위, and exact Activity-app back-arrow geometry.
# ---------------------------------------------------------------------------
# Compose imports for the Activity-app vector-equivalent arrow.
replace_once(RANK, "import androidx.compose.foundation.BorderStroke\n", "import androidx.compose.foundation.BorderStroke\nimport androidx.compose.foundation.Canvas\n", "ranking Canvas import")
replace_once(RANK, "import androidx.compose.ui.Modifier\n", "import androidx.compose.ui.Modifier\nimport androidx.compose.ui.geometry.Offset\nimport androidx.compose.ui.graphics.StrokeCap\n", "ranking geometry imports")

# Visible wording only; internal Ranking class/function names remain stable.
s = read(RANK)
s = s.replace("랭킹", "순위").replace('Text("Ranking"', 'Text("순위"')
write(RANK, s)

# Old OnlineRankingScreen back control.
regex_once(
    RANK,
    r'''            Surface\(onClick = onBack, shape = RoundedCornerShape\(16\.dp\), color = Color\.White\) \{\n                Text\("‹", modifier = Modifier\.padding\(horizontal = 16\.dp, vertical = 5\.dp\), fontSize = 30\.sp, color = YamoneInk\)\n            \}''',
    '''            YamoneActivityBackButton(themeMode, onBack)''',
    "old ranking activity back button",
    flags=re.MULTILINE,
)
# Current RankingTab detail back control.
regex_once(
    RANK,
    r'''            Surface\(\n                onClick = \{ selectedName = null \},\n                shape = RoundedCornerShape\(14\.dp\),\n                color = yamonePrimaryDark\(themeMode\)\.copy\(alpha = \.13f\)\n            \) \{\n                Text\(\n                    "‹",\n                    modifier = Modifier\.padding\(horizontal = 14\.dp, vertical = 1\.dp\),\n                    fontSize = 30\.sp,\n                    fontWeight = FontWeight\.Black,\n                    color = YamoneInk\n                \)\n            \}''',
    '''            YamoneActivityBackButton(themeMode) { selectedName = null }''',
    "ranking detail activity back button",
    flags=re.MULTILINE,
)

# Exact geometry copied from Yamone-App ic_yamone_arrow_back_bold.xml:
# viewport 24: M20,12 L6.5,12 M12.5,5.5 L6,12 L12.5,18.5, stroke 4.2,
# rendered as 28dp inside the same 42dp touch target used by YamoneBackHeader.
insert_before = '''@Composable
internal fun RankingTabScreen(
'''
back_button = '''@Composable
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

'''
replace_once(RANK, insert_before, back_button + insert_before, "insert exact activity back arrow")

# ---------------------------------------------------------------------------
# 5) Version.
# ---------------------------------------------------------------------------
replace_once(
    BUILD,
    '        versionCode = 59\n        versionName = "1.1.0-dev53"',
    '        versionCode = 60\n        versionName = "1.1.0-dev54"',
    "version dev54",
)

print("dev54 nickname/ranking/theme patch applied")
