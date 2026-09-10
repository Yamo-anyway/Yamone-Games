from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)


def replace_all(text: str, old: str, new: str, expected: int, label: str) -> str:
    count = text.count(old)
    if count != expected:
        raise SystemExit(f"{label}: expected {expected} matches, found {count}")
    return text.replace(old, new)


# -----------------------------------------------------------------------------
# Main app frame, game list icons, and development ad-flow bypass.
# -----------------------------------------------------------------------------
path = Path("app/src/main/java/com/yamone/games/YamoneGamesApp.kt")
text = path.read_text()
text = replace_once(
    text,
    "import androidx.compose.foundation.BorderStroke\n",
    "import androidx.compose.foundation.BorderStroke\nimport androidx.compose.foundation.Canvas\n",
    "main Canvas import",
)
text = replace_once(
    text,
    "import androidx.compose.ui.graphics.Color\n",
    "import androidx.compose.ui.geometry.Offset\nimport androidx.compose.ui.geometry.Size\nimport androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.StrokeCap\n",
    "main graphics imports",
)
text = replace_once(
    text,
    "private const val ONLINE_RANKING_VISIBLE = false\n",
    "private const val ONLINE_RANKING_VISIBLE = false\n\n// Development-only convenience switch. Keep ad code intact, but do not delay game testing.\nprivate const val DEV_AD_TIMER_BYPASS = true\n",
    "dev ad bypass flag",
)
text = replace_once(
    text,
    '''private data class GameListItem(\n    val title: String,\n    val subtitle: String,\n    val symbol: String,\n    val onClick: () -> Unit\n)\n''',
    '''private enum class GameIconKind {\n    SUDOKU, ICE_JUMP, FISH_MUNCH, SNOW_RUSH\n}\n\nprivate data class GameListItem(\n    val title: String,\n    val icon: GameIconKind,\n    val onClick: () -> Unit\n)\n''',
    "game list model",
)
old_request = '''    val requestGameStart: (AppScreen) -> Unit = { target ->\n        val now = System.currentTimeMillis()\n        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)\n        val online = runCatching { connectivityManager?.activeNetwork != null }.getOrDefault(false)\n        val entitlements = entitlementManager.snapshot(now)\n        when {\n            // The first actual game start is free regardless of promotion/purchase state.\n            !adAccessStore.hasUsedFirstFreeGame() -> {\n                adAccessStore.markFirstFreeGameUsed()\n                screenName = target.name\n            }\n            !entitlements.shouldShowInterstitial(now) -> screenName = target.name\n            !online -> screenName = target.name\n            else -> {\n                pendingGameName = target.name\n                showInterstitialTestAd = true\n            }\n        }\n    }\n'''
new_request = '''    val requestGameStart: (AppScreen) -> Unit = { target ->\n        if (DEV_AD_TIMER_BYPASS) {\n            screenName = target.name\n        } else {\n            val now = System.currentTimeMillis()\n            val connectivityManager = context.getSystemService(ConnectivityManager::class.java)\n            val online = runCatching { connectivityManager?.activeNetwork != null }.getOrDefault(false)\n            val entitlements = entitlementManager.snapshot(now)\n            when {\n                // The first actual game start is free regardless of promotion/purchase state.\n                !adAccessStore.hasUsedFirstFreeGame() -> {\n                    adAccessStore.markFirstFreeGameUsed()\n                    screenName = target.name\n                }\n                !entitlements.shouldShowInterstitial(now) -> screenName = target.name\n                !online -> screenName = target.name\n                else -> {\n                    pendingGameName = target.name\n                    showInterstitialTestAd = true\n                }\n            }\n        }\n    }\n'''
text = replace_once(text, old_request, new_request, "game-start ad bypass")
text = replace_once(
    text,
    "                containerColor = YamoneCream,\n",
    "                containerColor = yamonePrimarySoft(themeMode),\n",
    "main scaffold background",
)
text = replace_once(
    text,
    '''private fun MainTopBar(mascot: YamoneMascot, themeMode: YamoneThemeMode) {\n    Surface(color = Color.White, shadowElevation = 1.dp) {\n''',
    '''private fun MainTopBar(mascot: YamoneMascot, themeMode: YamoneThemeMode) {\n    Surface(\n        color = yamonePrimarySoft(themeMode),\n        shape = RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp),\n        shadowElevation = 2.dp\n    ) {\n''',
    "main top bar",
)
text = replace_once(
    text,
    "    NavigationBar(containerColor = Color.White) {\n",
    "    NavigationBar(containerColor = yamoneSecondarySoft(themeMode), tonalElevation = 0.dp) {\n",
    "main bottom bar",
)
text = replace_all(
    text,
    '''        GameListItem("스도쿠", "숫자로 채우는 똑똑한 두뇌 운동", "9×9", onSudoku),\n        GameListItem("빙하 점프", "빙하를 넘어 더 멀리 올라가요", "▲", onIceJump),\n        GameListItem("물고기 냠냠", "좌우로 움직여 물고기를 받아먹어요", "≈", onFishMunch),\n        GameListItem("눈덩이 러시", "눈덩이와 눈송이를 피해 오래 버텨요", "❄", onSnowRush),\n''',
    '''        GameListItem("스도쿠", GameIconKind.SUDOKU, onSudoku),\n        GameListItem("빙하 점프", GameIconKind.ICE_JUMP, onIceJump),\n        GameListItem("물고기 냠냠", GameIconKind.FISH_MUNCH, onFishMunch),\n        GameListItem("눈덩이 러시", GameIconKind.SNOW_RUSH, onSnowRush),\n''',
    1,
    "home game items",
)
text = replace_once(
    text,
    '''    val puzzle = listOf(GameListItem("스도쿠", "숫자로 채우는 9×9 퍼즐", "9×9", onSudoku))\n    val arcade = listOf(\n        GameListItem("빙하 점프", "자동 점프 · 드래그로 방향 이동", "▲", onIceJump),\n        GameListItem("물고기 냠냠", "일반 / 60초 타임어택", "≈", onFishMunch),\n        GameListItem("눈덩이 러시", "쏟아지는 눈을 피해 오래 생존", "❄", onSnowRush),\n    )\n''',
    '''    val puzzle = listOf(GameListItem("스도쿠", GameIconKind.SUDOKU, onSudoku))\n    val arcade = listOf(\n        GameListItem("빙하 점프", GameIconKind.ICE_JUMP, onIceJump),\n        GameListItem("물고기 냠냠", GameIconKind.FISH_MUNCH, onFishMunch),\n        GameListItem("눈덩이 러시", GameIconKind.SNOW_RUSH, onSnowRush),\n    )\n''',
    "games screen items",
)
old_icon = '''                    Surface(\n                        shape = RoundedCornerShape(14.dp),\n                        color = if (index % 2 == 0) yamonePrimarySoft(themeMode) else yamoneSecondarySoft(themeMode)\n                    ) {\n                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {\n                            Text(game.symbol, fontSize = if (game.symbol == "9×9") 12.sp else 20.sp, fontWeight = FontWeight.Black, color = YamoneInk)\n                        }\n                    }\n                    Spacer(Modifier.width(12.dp))\n'''
new_icon = '''                    GameListIcon(game.icon, themeMode)\n                    Spacer(Modifier.width(12.dp))\n'''
text = replace_once(text, old_icon, new_icon, "game list icon slot")
insert_after = '''private fun GameListCard(games: List<GameListItem>, themeMode: YamoneThemeMode) {\n    Surface(shape = RoundedCornerShape(24.dp), color = Color.White, shadowElevation = 1.dp) {\n        Column(Modifier.fillMaxWidth()) {\n            games.forEachIndexed { index, game ->\n                Row(\n                    Modifier.fillMaxWidth().clickable(onClick = game.onClick).padding(horizontal = 14.dp, vertical = 9.dp),\n                    verticalAlignment = Alignment.CenterVertically\n                ) {\n                    GameListIcon(game.icon, themeMode)\n                    Spacer(Modifier.width(12.dp))\n                    Column(Modifier.weight(1f)) {\n                        Text(game.title, fontSize = 16.sp, fontWeight = FontWeight.Black, color = YamoneInk)\n                    }\n                    Text("›", fontSize = 27.sp, color = YamoneMuted)\n                }\n                if (index != games.lastIndex) HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = Color(0xFFEAF0EF))\n            }\n        }\n    }\n}\n'''
if insert_after not in text:
    raise SystemExit("game list card insertion anchor not found")
icon_helper = r'''

@Composable
private fun GameListIcon(kind: GameIconKind, themeMode: YamoneThemeMode) {
    val mascot = when (kind) {
        GameIconKind.SUDOKU, GameIconKind.SNOW_RUSH -> YamoneMascot.BEAR
        GameIconKind.ICE_JUMP, GameIconKind.FISH_MUNCH -> YamoneMascot.SEAL
    }
    val accent = when (kind) {
        GameIconKind.SUDOKU, GameIconKind.FISH_MUNCH -> yamonePrimary(themeMode)
        GameIconKind.ICE_JUMP, GameIconKind.SNOW_RUSH -> yamoneSecondary(themeMode)
    }
    val soft = when (kind) {
        GameIconKind.SUDOKU, GameIconKind.FISH_MUNCH -> yamonePrimarySoft(themeMode)
        GameIconKind.ICE_JUMP, GameIconKind.SNOW_RUSH -> yamoneSecondarySoft(themeMode)
    }

    Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.size(54.dp),
            shape = RoundedCornerShape(18.dp),
            color = soft,
            border = BorderStroke(1.dp, accent.copy(alpha = .22f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                YamoneMascotIcon(mascot, size = 46.dp, accent = accent)
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).size(25.dp),
            shape = RoundedCornerShape(9.dp),
            color = Color.White,
            border = BorderStroke(1.5.dp, accent),
            shadowElevation = 1.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(15.dp)) {
                    val stroke = 1.7.dp.toPx()
                    when (kind) {
                        GameIconKind.SUDOKU -> {
                            for (i in 0..3) {
                                val p = size.width * i / 3f
                                drawLine(accent, Offset(p, 0f), Offset(p, size.height), strokeWidth = stroke, cap = StrokeCap.Round)
                                drawLine(accent, Offset(0f, p), Offset(size.width, p), strokeWidth = stroke, cap = StrokeCap.Round)
                            }
                        }
                        GameIconKind.ICE_JUMP -> {
                            drawLine(accent, Offset(size.width * .08f, size.height * .78f), Offset(size.width * .43f, size.height * .22f), strokeWidth = stroke, cap = StrokeCap.Round)
                            drawLine(accent, Offset(size.width * .43f, size.height * .22f), Offset(size.width * .64f, size.height * .55f), strokeWidth = stroke, cap = StrokeCap.Round)
                            drawLine(accent, Offset(size.width * .64f, size.height * .55f), Offset(size.width * .78f, size.height * .36f), strokeWidth = stroke, cap = StrokeCap.Round)
                            drawLine(accent, Offset(size.width * .78f, size.height * .36f), Offset(size.width * .94f, size.height * .78f), strokeWidth = stroke, cap = StrokeCap.Round)
                            drawLine(accent, Offset(size.width * .06f, size.height * .80f), Offset(size.width * .94f, size.height * .80f), strokeWidth = stroke, cap = StrokeCap.Round)
                        }
                        GameIconKind.FISH_MUNCH -> {
                            drawOval(
                                color = accent,
                                topLeft = Offset(size.width * .24f, size.height * .30f),
                                size = Size(size.width * .52f, size.height * .40f)
                            )
                            drawLine(accent, Offset(size.width * .28f, size.height * .50f), Offset(size.width * .08f, size.height * .30f), strokeWidth = stroke, cap = StrokeCap.Round)
                            drawLine(accent, Offset(size.width * .28f, size.height * .50f), Offset(size.width * .08f, size.height * .70f), strokeWidth = stroke, cap = StrokeCap.Round)
                            drawCircle(Color.White, radius = size.width * .045f, center = Offset(size.width * .64f, size.height * .43f))
                        }
                        GameIconKind.SNOW_RUSH -> {
                            drawCircle(accent, radius = size.minDimension * .43f, center = center)
                            drawCircle(Color.White.copy(alpha = .92f), radius = size.minDimension * .27f, center = center - Offset(size.width * .10f, size.height * .10f))
                            drawCircle(Color.White, radius = size.minDimension * .07f, center = center - Offset(size.width * .18f, size.height * .18f))
                        }
                    }
                }
            }
        }
    }
}
'''
text = text.replace(insert_after, insert_after + icon_helper, 1)
text = replace_all(
    text,
    "        if (!adRemoved) {\n            AdFreeTimeCard",
    "        if (!adRemoved && !DEV_AD_TIMER_BYPASS) {\n            AdFreeTimeCard",
    3,
    "hide ad timer cards during dev bypass",
)
path.write_text(text)


# -----------------------------------------------------------------------------
# Exact mascot silhouette cleanup: remove only edge-connected near-white pixels.
# This preserves the original line art instead of approximating it with a polygon.
# -----------------------------------------------------------------------------
path = Path("games/sudoku/src/main/java/com/yamone/games/sudoku/ui/theme/Theme.kt")
text = path.read_text()
start = text.index("private fun maskApprovedMascot")
new_mask = r'''private fun maskApprovedMascot(source: Bitmap, mascot: YamoneMascot): Bitmap {
    val width = source.width
    val height = source.height
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)

    val removed = BooleanArray(pixels.size)
    val queued = BooleanArray(pixels.size)
    val queue = java.util.ArrayDeque<Int>()
    val whiteFloor = when (mascot) {
        YamoneMascot.SEAL -> 236
        YamoneMascot.BEAR -> 236
    }

    fun isEdgeBackground(color: Int): Boolean {
        val alpha = AndroidColor.alpha(color)
        if (alpha <= 12) return true
        val r = AndroidColor.red(color)
        val g = AndroidColor.green(color)
        val b = AndroidColor.blue(color)
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        return min >= whiteFloor && max - min <= 18
    }

    fun enqueue(index: Int) {
        if (index !in pixels.indices || queued[index] || !isEdgeBackground(pixels[index])) return
        queued[index] = true
        queue.add(index)
    }

    for (x in 0 until width) {
        enqueue(x)
        enqueue((height - 1) * width + x)
    }
    for (y in 0 until height) {
        enqueue(y * width)
        enqueue(y * width + width - 1)
    }

    while (queue.isNotEmpty()) {
        val index = queue.removeFirst()
        if (removed[index]) continue
        removed[index] = true
        val x = index % width
        val y = index / width
        if (x > 0) enqueue(index - 1)
        if (x + 1 < width) enqueue(index + 1)
        if (y > 0) enqueue(index - width)
        if (y + 1 < height) enqueue(index + width)
    }

    for (i in pixels.indices) {
        if (removed[i]) pixels[i] = pixels[i] and 0x00FFFFFF
    }

    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, width, 0, 0, width, height)
    }
}
'''
text = text[:start] + new_mask
path.write_text(text)


# -----------------------------------------------------------------------------
# Game exit dialogs: no explanatory copy, two equal half-width actions.
# Also use themed soft backgrounds throughout game screens.
# -----------------------------------------------------------------------------
exit_specs = [
    (
        Path("games/icejump/src/main/java/com/yamone/games/icejump/IceJumpScreen.kt"),
        '''    if (exitConfirm) {\n        AlertDialog(\n            onDismissRequest = { exitConfirm = false },\n            shape = RoundedCornerShape(24.dp),\n            title = { Text("게임을 그만둘까요?", fontWeight = FontWeight.Black, color = ink) },\n            text = { Text("게임이 일시정지됐어요. 계속 플레이하거나 현재 게임을 종료할 수 있어요.", color = muted) },\n            confirmButton = {\n                TextButton(onClick = { exitConfirm = false }) {\n                    Text("계속하기", fontWeight = FontWeight.Bold, color = primaryDark)\n                }\n            },\n            dismissButton = {\n                TextButton(onClick = {\n                    state.started = false\n                    exitConfirm = false\n                    onBack()\n                }) { Text("게임 종료", fontWeight = FontWeight.Bold, color = Color(0xFFD85C6A)) }\n            }\n        )\n    }\n''',
        '''    if (exitConfirm) {\n        AlertDialog(\n            onDismissRequest = { exitConfirm = false },\n            shape = RoundedCornerShape(24.dp),\n            title = { Text("게임을 그만둘까요?", fontWeight = FontWeight.Black, color = ink) },\n            confirmButton = {\n                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                    OutlinedButton(\n                        onClick = { exitConfirm = false },\n                        modifier = Modifier.weight(1f).height(48.dp),\n                        shape = RoundedCornerShape(16.dp)\n                    ) { Text("계속하기", fontWeight = FontWeight.Bold, color = primaryDark) }\n                    Button(\n                        onClick = {\n                            state.started = false\n                            exitConfirm = false\n                            onBack()\n                        },\n                        modifier = Modifier.weight(1f).height(48.dp),\n                        shape = RoundedCornerShape(16.dp),\n                        colors = ButtonDefaults.buttonColors(\n                            containerColor = Color(0xFFFFE7EC),\n                            contentColor = Color(0xFFD85C6A)\n                        )\n                    ) { Text("게임 종료", fontWeight = FontWeight.Bold) }\n                }\n            }\n        )\n    }\n''',
    ),
    (
        Path("games/fishmunch/src/main/java/com/yamone/games/fishmunch/FishMunchScreen.kt"),
        '''    if (exitConfirm) {\n        AlertDialog(\n            onDismissRequest = { exitConfirm = false },\n            shape = RoundedCornerShape(24.dp),\n            title = { Text("게임을 그만둘까요?", fontWeight = FontWeight.Black, color = ink) },\n            text = { Text("게임이 일시정지됐어요. 계속 플레이하거나 현재 게임을 종료할 수 있어요.", color = muted) },\n            confirmButton = {\n                TextButton(onClick = { exitConfirm = false }) {\n                    Text("계속하기", fontWeight = FontWeight.Bold, color = primaryDark)\n                }\n            },\n            dismissButton = {\n                TextButton(onClick = {\n                    exitConfirm = false\n                    selectModeAgain()\n                }) { Text("게임 종료", fontWeight = FontWeight.Bold, color = Color(0xFFD85C6A)) }\n            }\n        )\n    }\n''',
        '''    if (exitConfirm) {\n        AlertDialog(\n            onDismissRequest = { exitConfirm = false },\n            shape = RoundedCornerShape(24.dp),\n            title = { Text("게임을 그만둘까요?", fontWeight = FontWeight.Black, color = ink) },\n            confirmButton = {\n                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                    OutlinedButton(\n                        onClick = { exitConfirm = false },\n                        modifier = Modifier.weight(1f).height(48.dp),\n                        shape = RoundedCornerShape(16.dp)\n                    ) { Text("계속하기", fontWeight = FontWeight.Bold, color = primaryDark) }\n                    Button(\n                        onClick = {\n                            exitConfirm = false\n                            selectModeAgain()\n                        },\n                        modifier = Modifier.weight(1f).height(48.dp),\n                        shape = RoundedCornerShape(16.dp),\n                        colors = ButtonDefaults.buttonColors(\n                            containerColor = Color(0xFFFFE7EC),\n                            contentColor = Color(0xFFD85C6A)\n                        )\n                    ) { Text("게임 종료", fontWeight = FontWeight.Bold) }\n                }\n            }\n        )\n    }\n''',
    ),
    (
        Path("games/snowrush/src/main/java/com/yamone/games/snowrush/SnowRushScreen.kt"),
        '''    if (exitConfirm) {\n        AlertDialog(\n            onDismissRequest = { exitConfirm = false },\n            shape = RoundedCornerShape(24.dp),\n            title = { Text("게임을 그만둘까요?", fontWeight = FontWeight.Black, color = ink) },\n            text = { Text("게임이 일시정지됐어요. 계속 플레이하거나 현재 게임을 종료할 수 있어요.", color = muted) },\n            confirmButton = {\n                TextButton(onClick = { exitConfirm = false }) { Text("계속하기", fontWeight = FontWeight.Bold, color = primaryDark) }\n            },\n            dismissButton = {\n                TextButton(onClick = {\n                    state.started = false\n                    exitConfirm = false\n                    onBack()\n                }) { Text("게임 종료", fontWeight = FontWeight.Bold, color = Color(0xFFD85C6A)) }\n            }\n        )\n    }\n''',
        '''    if (exitConfirm) {\n        AlertDialog(\n            onDismissRequest = { exitConfirm = false },\n            shape = RoundedCornerShape(24.dp),\n            title = { Text("게임을 그만둘까요?", fontWeight = FontWeight.Black, color = ink) },\n            confirmButton = {\n                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                    OutlinedButton(\n                        onClick = { exitConfirm = false },\n                        modifier = Modifier.weight(1f).height(48.dp),\n                        shape = RoundedCornerShape(16.dp)\n                    ) { Text("계속하기", fontWeight = FontWeight.Bold, color = primaryDark) }\n                    Button(\n                        onClick = {\n                            state.started = false\n                            exitConfirm = false\n                            onBack()\n                        },\n                        modifier = Modifier.weight(1f).height(48.dp),\n                        shape = RoundedCornerShape(16.dp),\n                        colors = ButtonDefaults.buttonColors(\n                            containerColor = Color(0xFFFFE7EC),\n                            contentColor = Color(0xFFD85C6A)\n                        )\n                    ) { Text("게임 종료", fontWeight = FontWeight.Bold) }\n                }\n            }\n        )\n    }\n''',
    ),
]

for game_path, old_dialog, new_dialog in exit_specs:
    game_text = game_path.read_text()
    game_text = replace_once(game_text, old_dialog, new_dialog, f"{game_path.name} exit dialog")
    game_text = replace_once(
        game_text,
        "Column(Modifier.fillMaxSize().background(Color(0xFFFFFDF9)))",
        "Column(Modifier.fillMaxSize().background(soft))",
        f"{game_path.name} soft background",
    )
    game_path.write_text(game_text)

# Sudoku exit dialog and themed frame.
path = Path("games/sudoku/src/main/java/com/yamone/games/sudoku/ui/SudokuApp.kt")
text = path.read_text()
old_sudoku_dialog = '''    if (exitConfirm) {\n        AlertDialog(\n            onDismissRequest = {\n                exitConfirm = false\n                if (game.paused && !game.completed) game.togglePause()\n            },\n            shape = RoundedCornerShape(24.dp),\n            title = { Text("게임을 그만둘까요?", fontWeight = FontWeight.Black, color = YamoneInk) },\n            text = { Text("게임이 일시정지됐어요. 종료하면 현재 스도쿠 판은 임시 저장돼요.", color = YamoneMuted) },\n            confirmButton = {\n                TextButton(onClick = {\n                    exitConfirm = false\n                    if (game.paused && !game.completed) game.togglePause()\n                }) { Text("계속하기", fontWeight = FontWeight.Bold, color = yamonePrimaryDark(themeMode)) }\n            },\n            dismissButton = {\n                TextButton(onClick = {\n                    game.saveNow()\n                    exitConfirm = false\n                    onBack()\n                }) { Text("게임 종료", fontWeight = FontWeight.Bold, color = YamoneError) }\n            }\n        )\n    }\n'''
new_sudoku_dialog = '''    if (exitConfirm) {\n        AlertDialog(\n            onDismissRequest = {\n                exitConfirm = false\n                if (game.paused && !game.completed) game.togglePause()\n            },\n            shape = RoundedCornerShape(24.dp),\n            title = { Text("게임을 그만둘까요?", fontWeight = FontWeight.Black, color = YamoneInk) },\n            confirmButton = {\n                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                    OutlinedButton(\n                        onClick = {\n                            exitConfirm = false\n                            if (game.paused && !game.completed) game.togglePause()\n                        },\n                        modifier = Modifier.weight(1f).height(48.dp),\n                        shape = RoundedCornerShape(16.dp)\n                    ) { Text("계속하기", fontWeight = FontWeight.Bold, color = yamonePrimaryDark(themeMode)) }\n                    Button(\n                        onClick = {\n                            game.saveNow()\n                            exitConfirm = false\n                            onBack()\n                        },\n                        modifier = Modifier.weight(1f).height(48.dp),\n                        shape = RoundedCornerShape(16.dp),\n                        colors = ButtonDefaults.buttonColors(\n                            containerColor = YamonePinkSoft,\n                            contentColor = YamoneError\n                        )\n                    ) { Text("게임 종료", fontWeight = FontWeight.Bold) }\n                }\n            }\n        )\n    }\n'''
text = replace_once(text, old_sudoku_dialog, new_sudoku_dialog, "Sudoku exit dialog")
text = replace_once(text, "Box(Modifier.fillMaxSize().background(YamoneCream))", "Box(Modifier.fillMaxSize().background(yamonePrimarySoft(themeMode)))", "Sudoku root background")
text = replace_once(text, "            containerColor = YamoneCream,", "            containerColor = yamonePrimarySoft(themeMode),", "Sudoku scaffold background")
text = replace_once(text, "    Surface(color = Color.White) {", "    Surface(color = yamonePrimarySoft(themeMode)) {", "Sudoku top bar background")
path.write_text(text)


# Version bump for this validation build.
path = Path("app/build.gradle.kts")
text = path.read_text()
text = replace_once(text, "versionCode = 54", "versionCode = 55", "versionCode")
text = replace_once(text, 'versionName = "1.1.0-dev48"', 'versionName = "1.1.0-dev49"', "versionName")
path.write_text(text)

print("UI polish refresh applied")
