from pathlib import Path
import re


def read(path):
    return Path(path).read_text()


def write(path, text):
    Path(path).write_text(text)


def must_replace(text, old, new, label, count=1):
    if old not in text:
        raise SystemExit(f"missing anchor: {label}")
    return text.replace(old, new, count)

# -----------------------------------------------------------------------------
# App shell: remove Winter Ride from shipped app, simplify lists, compact records.
# -----------------------------------------------------------------------------
path = 'app/src/main/java/com/yamone/games/YamoneGamesApp.kt'
s = read(path)
s = must_replace(s, 'import com.yamone.games.winterride.WinterRideScreen\n', '', 'winter ride import')
s = must_replace(
    s,
    '    HOME, GAMES, RECORDS, SETTINGS, ONLINE_RANKING, SUDOKU, ICE_JUMP, FISH_MUNCH, SNOW_RUSH, WINTER_RIDE\n',
    '    HOME, GAMES, RECORDS, SETTINGS, ONLINE_RANKING, SUDOKU, ICE_JUMP, FISH_MUNCH, SNOW_RUSH\n',
    'app screen winter ride'
)

winter_block = '''            AppScreen.WINTER_RIDE -> WinterRideScreen(\n                onBack = goHome,\n                primary = yamonePrimary(themeMode),\n                primaryDark = yamonePrimaryDark(themeMode),\n                soft = yamonePrimarySoft(themeMode),\n                ink = YamoneInk,\n                muted = YamoneMuted,\n                mascotContent = { size -> YamoneMascotIcon(mascot, size = size, accent = yamonePrimary(themeMode)) }\n            )\n'''
s = must_replace(s, winter_block, '', 'winter ride route')

# Remove now-unused per-game share callbacks; sharing lives on Records only.
s = must_replace(s, '                onShareRecord = { shareRequest = ShareCardRequest(ArcadeGameId.ICE_JUMP, it) },\n', '', 'ice share callback')
s = must_replace(s, '                onShareRecord = { shareRequest = ShareCardRequest(ArcadeGameId.FISH_MUNCH, it) },\n', '', 'fish share callback')
s = must_replace(s, '                onShareRecord = { shareRequest = ShareCardRequest(ArcadeGameId.SNOW_RUSH, it) },\n', '', 'snow share callback')

# Remove Winter Ride navigation plumbing.
s = s.replace('                            onWinterRide = { requestGameStart(AppScreen.WINTER_RIDE) },\n', '')
s = s.replace('                            onWinterRide = { requestGameStart(AppScreen.WINTER_RIDE) }\n', '')
s = s.replace('    onWinterRide: () -> Unit,\n', '')
s = s.replace('    onWinterRide: () -> Unit\n', '')
s = s.replace('        GameListItem("스키 · 보드", "스키·스노보드·트리런으로 설원을 달려요", "⛷", onWinterRide),\n', '')
s = s.replace('        GameListItem("스키 · 보드", "스키 / 스노보드 / 트리런", "⛷", onWinterRide)\n', '')
s = s.replace('        GameListItem("스키 · 보드", "스키 / 스노보드 / 트리런", "⛷", onWinterRide),\n', '')

# Main app header: title only, no small explanatory subtitle.
old = '''            Column {\n                Text("야모네 게임", fontSize = 27.sp, fontWeight = FontWeight.Black, color = yamonePrimaryDark(themeMode))\n                Text("작고 귀여운 게임들", fontSize = 12.sp, color = YamoneMuted)\n            }\n'''
new = '''            Text("야모네 게임", fontSize = 27.sp, fontWeight = FontWeight.Black, color = yamonePrimaryDark(themeMode))\n'''
s = must_replace(s, old, new, 'main top subtitle')

# Games page explanatory cards are removed; only the actual lists remain.
start_marker = '''        Surface(shape = RoundedCornerShape(23.dp), color = yamonePrimarySoft(themeMode)) {\n            Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {\n                Surface(shape = RoundedCornerShape(14.dp), color = Color.White) {\n                    Text("▶", modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp), color = yamonePrimaryDark(themeMode), fontWeight = FontWeight.Black)\n                }\n                Spacer(Modifier.width(12.dp))\n                Column {\n                    Text("게임을 누르면 바로 시작돼요", fontSize = 17.sp, fontWeight = FontWeight.Black, color = YamoneInk)\n                    Text("첫 게임은 바로 시작하고, 이후에는 남은 전면광고 없는 시간을 확인해요 ♡", fontSize = 12.sp, color = YamoneMuted)\n                }\n            }\n        }\n\n'''
s = must_replace(s, start_marker, '', 'games intro card')

coming_soon = '''        Surface(shape = RoundedCornerShape(23.dp), color = Color.White) {\n            Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {\n                Surface(shape = RoundedCornerShape(13.dp), color = Color(0xFFF1F4F4)) {\n                    Text("+", modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp), fontSize = 22.sp, fontWeight = FontWeight.Black, color = YamoneMuted)\n                }\n                Spacer(Modifier.width(12.dp))\n                Column {\n                    Text("준비 중인 게임", fontSize = 17.sp, fontWeight = FontWeight.Black, color = YamoneInk)\n                    Text("새로운 퍼즐과 아케이드를 하나씩 추가할게요.", fontSize = 12.sp, color = YamoneMuted)\n                }\n            }\n        }\n'''
s = must_replace(s, coming_soon, '', 'coming soon card')

# Hide section trailing descriptions.
old = '''        Spacer(Modifier.weight(1f))\n        Text(trailing, fontSize = 12.sp, color = YamoneMuted)\n'''
s = must_replace(s, old, '', 'section trailing explanation')

# Hide game-list subtitles and tighten rows.
s = must_replace(s, '.padding(horizontal = 14.dp, vertical = 12.dp)', '.padding(horizontal = 14.dp, vertical = 9.dp)', 'game row spacing')
s = must_replace(s, '                        Text(game.subtitle, fontSize = 12.sp, color = YamoneMuted)\n', '', 'game subtitle')

# Records header: no descriptive small print, and overall spacing tighter.
s = must_replace(
    s,
    '''                Column {\n                    Text("나의 기록", fontSize = 21.sp, fontWeight = FontWeight.Black, color = YamoneInk)\n                    Text("아케이드 좋은 기록은 게임별 5개까지만 보관해요 ♡", fontSize = 13.sp, color = YamoneMuted)\n                }\n''',
    '''                Text("나의 기록", fontSize = 21.sp, fontWeight = FontWeight.Black, color = YamoneInk)\n''',
    'records header subtitle'
)
# Only first occurrence here is RecordsScreen's column spacing because the exact padding is unique enough.
s = must_replace(
    s,
    '        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),\n        verticalArrangement = Arrangement.spacedBy(12.dp)\n',
    '        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),\n        verticalArrangement = Arrangement.spacedBy(8.dp)\n',
    'records compact outer spacing'
)

# Replace arcade record section completely: title + best-share, then one compact row per rank.
start = s.index('@Composable\nprivate fun ArcadeRecordSection(')
end = s.index('@Composable\nprivate fun SettingsScreen(', start)
record_section = '''@Composable\nprivate fun ArcadeRecordSection(\n    game: ArcadeGameId,\n    records: List<ArcadeRecord>,\n    themeMode: YamoneThemeMode,\n    onShare: (ArcadeGameId, ArcadeRecord) -> Unit\n) {\n    Spacer(Modifier.height(1.dp))\n    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {\n        Text(arcadeGameTitle(game), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)\n        if (records.isNotEmpty()) {\n            Spacer(Modifier.width(6.dp))\n            TextButton(\n                onClick = { onShare(game, records.first()) },\n                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)\n            ) {\n                Text("공유", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = yamonePrimaryDark(themeMode))\n            }\n        }\n    }\n    if (records.isEmpty()) {\n        Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {\n            Text("아직 기록이 없어요", modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp), fontSize = 12.sp, color = YamoneMuted)\n        }\n    } else {\n        records.forEachIndexed { index, record ->\n            Surface(shape = RoundedCornerShape(15.dp), color = Color.White) {\n                Row(\n                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),\n                    verticalAlignment = Alignment.CenterVertically\n                ) {\n                    Text(\n                        "${index + 1}위",\n                        modifier = Modifier.width(42.dp),\n                        fontSize = 12.sp,\n                        fontWeight = FontWeight.Black,\n                        color = yamonePrimaryDark(themeMode)\n                    )\n                    Text(\n                        arcadeScoreText(game, record.score),\n                        modifier = Modifier.weight(1f),\n                        fontSize = 14.sp,\n                        fontWeight = FontWeight.Black,\n                        color = YamoneInk\n                    )\n                    Text(\n                        record.nickname,\n                        fontSize = 12.sp,\n                        fontWeight = FontWeight.SemiBold,\n                        color = YamoneMuted,\n                        maxLines = 1\n                    )\n                }\n            }\n        }\n    }\n}\n\n'''
s = s[:start] + record_section + s[end:]
write(path, s)

# -----------------------------------------------------------------------------
# Ice Jump: clean top/start/result, pretty back arrow, share only from Records.
# -----------------------------------------------------------------------------
path = 'games/icejump/src/main/java/com/yamone/games/icejump/IceJumpScreen.kt'
s = read(path)
s = must_replace(s, '    onShareRecord: (ArcadeRecord) -> Unit,\n', '', 'ice share param')
old = '''            Surface(onClick = ::requestExit, shape = RoundedCornerShape(16.dp), color = Color.White) {\n                Text("‹", modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp), fontSize = 30.sp, color = ink)\n            }\n            Spacer(Modifier.width(8.dp))\n            Column {\n                Text("빙하 점프", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ink)\n                Text("자동 점프 · 화면 드래그 이동", fontSize = 10.sp, color = muted)\n            }\n'''
new = '''            Surface(onClick = ::requestExit, shape = RoundedCornerShape(15.dp), color = soft) {\n                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {\n                    Text("←", fontSize = 20.sp, fontWeight = FontWeight.Black, color = primaryDark)\n                }\n            }\n            Spacer(Modifier.width(10.dp))\n            Text("빙하 점프", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ink)\n'''
s = must_replace(s, old, new, 'ice top bar')
# Start overlay content -> Start button only.
old = '''                    Column(\n                        Modifier.padding(horizontal = 28.dp, vertical = 24.dp),\n                        horizontalAlignment = Alignment.CenterHorizontally\n                    ) {\n                        mascotContent(82.dp)\n                        Spacer(Modifier.height(10.dp))\n                        Text("얼음판을 타고 올라가요!", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ink)\n                        Spacer(Modifier.height(5.dp))\n                        Text("밟은 빙하는 잠시 뒤 내려가요.\\n높이 올라갈수록 발판은 조금 더 좁아지고 더 빨리 내려가요 ♡", textAlign = TextAlign.Center, fontSize = 12.sp, color = muted)\n                        Spacer(Modifier.height(16.dp))\n                        Button(\n                            onClick = ::restart,\n                            shape = RoundedCornerShape(18.dp),\n                            colors = ButtonDefaults.buttonColors(containerColor = primary)\n                        ) { Text("시작하기", fontWeight = FontWeight.ExtraBold) }\n                    }\n'''
new = '''                    Box(Modifier.padding(horizontal = 30.dp, vertical = 22.dp), contentAlignment = Alignment.Center) {\n                        Button(\n                            onClick = ::restart,\n                            shape = RoundedCornerShape(18.dp),\n                            colors = ButtonDefaults.buttonColors(containerColor = primary)\n                        ) { Text("시작하기", fontWeight = FontWeight.ExtraBold) }\n                    }\n'''
s = must_replace(s, old, new, 'ice start overlay')
# Result: no share, just retry + quit.
old = '''                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                            OutlinedButton(onClick = ::restart, shape = RoundedCornerShape(17.dp)) {\n                                Text("다시하기", fontWeight = FontWeight.Bold)\n                            }\n                            Button(\n                                onClick = { lastRecord?.let(onShareRecord) },\n                                enabled = lastRecord != null,\n                                shape = RoundedCornerShape(17.dp),\n                                colors = ButtonDefaults.buttonColors(containerColor = primary)\n                            ) { Text("공유카드", fontWeight = FontWeight.Bold) }\n                        }\n                        Spacer(Modifier.height(8.dp))\n                        OutlinedButton(\n                            onClick = onBack,\n                            modifier = Modifier.fillMaxWidth(),\n                            shape = RoundedCornerShape(17.dp)\n                        ) { Text("그만하기", fontWeight = FontWeight.Bold) }\n'''
new = '''                        Button(\n                            onClick = ::restart,\n                            modifier = Modifier.fillMaxWidth(),\n                            shape = RoundedCornerShape(17.dp),\n                            colors = ButtonDefaults.buttonColors(containerColor = primary)\n                        ) { Text("다시하기", fontWeight = FontWeight.Bold) }\n                        Spacer(Modifier.height(8.dp))\n                        OutlinedButton(\n                            onClick = onBack,\n                            modifier = Modifier.fillMaxWidth(),\n                            shape = RoundedCornerShape(17.dp)\n                        ) { Text("그만하기", fontWeight = FontWeight.Bold) }\n'''
s = must_replace(s, old, new, 'ice result actions')
# Remove lower instruction card.
start = s.index('        Surface(\n            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),', s.index('if (state.gameOver)'))
end = s.index('    }\n\n\n    if (exitConfirm)', start)
s = s[:start] + s[end:]
write(path, s)

# -----------------------------------------------------------------------------
# Fish Munch: mode buttons only; quitting returns to mode selection; no result share.
# -----------------------------------------------------------------------------
path = 'games/fishmunch/src/main/java/com/yamone/games/fishmunch/FishMunchScreen.kt'
s = read(path)
s = must_replace(s, '    onShareRecord: (ArcadeRecord) -> Unit,\n', '', 'fish share param')
old = '''            Surface(onClick = ::requestExit, shape = RoundedCornerShape(16.dp), color = Color.White) {\n                Text("‹", modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp), fontSize = 30.sp, color = ink)\n            }\n            Spacer(Modifier.width(8.dp))\n            Column {\n                Text("물고기 냠냠", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ink)\n                Text(\n                    if (state.mode == FishMode.TIME_ATTACK) "타임어택 60초 · 놓쳐도 계속" else "일반 모드 · 하나라도 놓치면 종료",\n                    fontSize = 10.sp,\n                    color = muted\n                )\n            }\n'''
new = '''            Surface(onClick = ::requestExit, shape = RoundedCornerShape(15.dp), color = soft) {\n                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {\n                    Text("←", fontSize = 20.sp, fontWeight = FontWeight.Black, color = primaryDark)\n                }\n            }\n            Spacer(Modifier.width(10.dp))\n            Text("물고기 냠냠", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ink)\n'''
s = must_replace(s, old, new, 'fish top bar')
old = '''                    onModeSelect = ::selectModeAgain,\n                    onRestart = ::restart,\n                    onShare = { lastRecord?.let(onShareRecord) },\n                    onExit = onBack,\n                    showShare = state.mode == FishMode.NORMAL && lastRecord != null\n'''
new = '''                    onRestart = ::restart,\n                    onExit = ::selectModeAgain\n'''
s = must_replace(s, old, new, 'fish result call')
# Remove lower instruction card.
start = s.index('        Surface(\n            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),', s.index('if (state.gameOver)'))
end = s.index('    }\n\n    if (exitConfirm)', start)
s = s[:start] + s[end:]
# In-progress exit returns to mode select rather than app home.
old = '''                TextButton(onClick = {\n                    state.started = false\n                    exitConfirm = false\n                    onBack()\n                }) { Text("게임 종료", fontWeight = FontWeight.Bold, color = Color(0xFFD85C6A)) }\n'''
new = '''                TextButton(onClick = {\n                    exitConfirm = false\n                    selectModeAgain()\n                }) { Text("게임 종료", fontWeight = FontWeight.Bold, color = Color(0xFFD85C6A)) }\n'''
s = must_replace(s, old, new, 'fish exit to mode')
# Mode overlay: keep cute title + the two mode buttons, remove all small explanations.
s = must_replace(s, '            Text("두 모드의 기록은 따로 저장돼요 ♡", fontSize = 11.sp, color = muted, textAlign = TextAlign.Center)\n', '', 'fish mode intro text')
s = must_replace(s, '            Text("놓치면 종료 · 기본 1초 1마리 · 5초마다 추가 등장 증가", fontSize = 10.sp, color = muted, textAlign = TextAlign.Center)\n', '', 'fish normal mode text')
s = must_replace(s, '            Text("놓쳐도 계속 · 추가 물고기는 랜덤 시점에 한 마리씩 등장", fontSize = 10.sp, color = muted, textAlign = TextAlign.Center)\n', '', 'fish time mode text')
# Rewrite result overlay to retry + quit only.
start = s.index('@Composable\nprivate fun BoxScope.ResultOverlay(')
result = '''@Composable\nprivate fun BoxScope.ResultOverlay(\n    mode: FishMode,\n    score: Int,\n    best: Int,\n    primary: Color,\n    primaryDark: Color,\n    ink: Color,\n    muted: Color,\n    mascotContent: @Composable (Dp) -> Unit,\n    onRestart: () -> Unit,\n    onExit: () -> Unit\n) {\n    Surface(\n        modifier = Modifier.align(Alignment.Center).padding(20.dp),\n        shape = RoundedCornerShape(28.dp),\n        color = Color.White.copy(alpha = .99f),\n        shadowElevation = 6.dp\n    ) {\n        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {\n            mascotContent(74.dp)\n            Spacer(Modifier.height(6.dp))\n            Text(if (mode == FishMode.TIME_ATTACK) "60초 끝!" else "앗, 물고기를 놓쳤어요!", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ink)\n            Spacer(Modifier.height(4.dp))\n            Text("${score}마리", fontSize = 30.sp, fontWeight = FontWeight.Black, color = primaryDark)\n            Text(if (mode == FishMode.TIME_ATTACK) "타임어택 최고 기록 ${best}마리" else "최고 기록 ${best}마리", fontSize = 11.sp, color = muted)\n            Spacer(Modifier.height(15.dp))\n            Button(onClick = onRestart, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp), colors = ButtonDefaults.buttonColors(containerColor = primary)) {\n                Text("다시하기", fontWeight = FontWeight.Bold)\n            }\n            Spacer(Modifier.height(8.dp))\n            OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp)) {\n                Text("그만하기", fontWeight = FontWeight.Bold)\n            }\n        }\n    }\n}\n'''
s = s[:start] + result
write(path, s)

# -----------------------------------------------------------------------------
# Snow Rush: clean top/start/result, pretty back arrow, share only from Records.
# -----------------------------------------------------------------------------
path = 'games/snowrush/src/main/java/com/yamone/games/snowrush/SnowRushScreen.kt'
s = read(path)
s = must_replace(s, '    onShareRecord: (ArcadeRecord) -> Unit,\n', '', 'snow share param')
old = '''            Surface(onClick = ::requestExit, shape = RoundedCornerShape(16.dp), color = Color.White) {\n                Text("‹", modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp), fontSize = 30.sp, color = ink)\n            }\n            Spacer(Modifier.width(8.dp))\n            Column {\n                Text("눈덩이 러시", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ink)\n                Text("기본 1초 1개 · 5초마다 추가 등장 증가", fontSize = 10.sp, color = muted)\n            }\n'''
new = '''            Surface(onClick = ::requestExit, shape = RoundedCornerShape(15.dp), color = soft) {\n                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {\n                    Text("←", fontSize = 20.sp, fontWeight = FontWeight.Black, color = primaryDark)\n                }\n            }\n            Spacer(Modifier.width(10.dp))\n            Text("눈덩이 러시", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ink)\n'''
s = must_replace(s, old, new, 'snow top bar')
# Call simplified start overlay.
old = '''                StartOverlay(\n                    title = "눈덩이와 눈송이를 피해요!",\n                    body = "눈덩이는 기본 1초마다 1개씩 내려와요.\\n5초마다 그 구간의 랜덤 시점에 추가 눈덩이가 1개씩 더 늘어나요.\\n눈덩이는 내려오며 커지고, 눈송이는 빠르게 커졌다 작아져요 ♡",\n                    button = "시작하기",\n                    primary = primary,\n                    ink = ink,\n                    muted = muted,\n                    mascotContent = mascotContent,\n                    onClick = ::restart\n                )\n'''
new = '''                StartOverlay(primary = primary, onClick = ::restart)\n'''
s = must_replace(s, old, new, 'snow start call')
old = '''                    onRestart = ::restart,\n                    onShare = { lastRecord?.let(onShareRecord) },\n                    onExit = onBack,\n                    shareEnabled = lastRecord != null\n'''
new = '''                    onRestart = ::restart,\n                    onExit = onBack\n'''
s = must_replace(s, old, new, 'snow result call')
# Remove lower instruction card.
start = s.index('        Surface(\n            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),', s.index('if (state.gameOver)'))
end = s.index('    }\n\n\n    if (exitConfirm)', start)
s = s[:start] + s[end:]
# Rewrite StartOverlay and ResultOverlay tail.
start = s.index('@Composable\nprivate fun BoxScope.StartOverlay(')
end = s.index('private fun formatDuration(seconds: Int): String', start)
new_tail = '''@Composable\nprivate fun BoxScope.StartOverlay(\n    primary: Color,\n    onClick: () -> Unit\n) {\n    Surface(\n        modifier = Modifier.align(Alignment.Center).padding(22.dp),\n        shape = RoundedCornerShape(28.dp),\n        color = Color.White.copy(alpha = .99f),\n        shadowElevation = 5.dp\n    ) {\n        Box(Modifier.padding(horizontal = 30.dp, vertical = 22.dp), contentAlignment = Alignment.Center) {\n            Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = primary), shape = RoundedCornerShape(17.dp)) {\n                Text("시작하기", fontWeight = FontWeight.Bold)\n            }\n        }\n    }\n}\n\n@Composable\nprivate fun BoxScope.ResultOverlay(\n    score: Int,\n    best: Int,\n    primary: Color,\n    primaryDark: Color,\n    ink: Color,\n    muted: Color,\n    mascotContent: @Composable (Dp) -> Unit,\n    onRestart: () -> Unit,\n    onExit: () -> Unit\n) {\n    Surface(\n        modifier = Modifier.align(Alignment.Center).padding(20.dp),\n        shape = RoundedCornerShape(28.dp), color = Color.White.copy(alpha = .99f), shadowElevation = 6.dp\n    ) {\n        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {\n            mascotContent(74.dp)\n            Spacer(Modifier.height(6.dp))\n            Text("앗! 눈에 닿았어요", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ink)\n            Spacer(Modifier.height(4.dp))\n            Text(formatDuration(score), fontSize = 30.sp, fontWeight = FontWeight.Black, color = primaryDark)\n            Text("최고 기록 ${formatDuration(best)}", fontSize = 11.sp, color = muted)\n            Spacer(Modifier.height(15.dp))\n            Button(onClick = onRestart, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp), colors = ButtonDefaults.buttonColors(containerColor = primary)) {\n                Text("다시하기", fontWeight = FontWeight.Bold)\n            }\n            Spacer(Modifier.height(8.dp))\n            OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp)) {\n                Text("그만하기", fontWeight = FontWeight.Bold)\n            }\n        }\n    }\n}\n\n'''
s = s[:start] + new_tail + s[end:]
write(path, s)

# -----------------------------------------------------------------------------
# Sudoku: pretty back button, avoid duplicate system insets, result has quit.
# -----------------------------------------------------------------------------
path = 'games/sudoku/src/main/java/com/yamone/games/sudoku/ui/SudokuApp.kt'
s = read(path)
s = s.replace('                    .navigationBarsPadding()\n', '')
s = s.replace('.statusBarsPadding()', '')
s = must_replace(s, '        if (game.completed) ClearOverlay(game, mascot, themeMode)\n', '        if (game.completed) ClearOverlay(game, mascot, themeMode, onBack)\n', 'sudoku clear call')
old = '''            TextButton(onClick = onBack) { Text("‹", fontSize = 30.sp, color = YamoneInk) }\n            Text("스도쿠", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)\n'''
new = '''            Surface(onClick = onBack, shape = RoundedCornerShape(15.dp), color = yamonePrimarySoft(themeMode)) {\n                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {\n                    Text("←", fontSize = 20.sp, fontWeight = FontWeight.Black, color = yamonePrimaryDark(themeMode))\n                }\n            }\n            Spacer(Modifier.width(10.dp))\n            Text("스도쿠", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)\n'''
s = must_replace(s, old, new, 'sudoku back')
old = 'private fun ClearOverlay(game: SudokuController, mascot: YamoneMascot, themeMode: YamoneThemeMode) {'
new = 'private fun ClearOverlay(game: SudokuController, mascot: YamoneMascot, themeMode: YamoneThemeMode, onExit: () -> Unit) {'
s = must_replace(s, old, new, 'sudoku clear signature')
old = '''                Button(\n                    modifier = Modifier.fillMaxWidth().height(52.dp),\n                    onClick = { game.newGame(game.difficulty) },\n                    colors = ButtonDefaults.buttonColors(containerColor = yamonePrimary(themeMode)),\n                    shape = RoundedCornerShape(17.dp)\n                ) { Text("다음 게임", fontWeight = FontWeight.ExtraBold) }\n'''
new = '''                Button(\n                    modifier = Modifier.fillMaxWidth().height(52.dp),\n                    onClick = { game.newGame(game.difficulty) },\n                    colors = ButtonDefaults.buttonColors(containerColor = yamonePrimary(themeMode)),\n                    shape = RoundedCornerShape(17.dp)\n                ) { Text("다시하기", fontWeight = FontWeight.ExtraBold) }\n                Spacer(Modifier.height(8.dp))\n                OutlinedButton(\n                    modifier = Modifier.fillMaxWidth().height(52.dp),\n                    onClick = onExit,\n                    shape = RoundedCornerShape(17.dp)\n                ) { Text("그만하기", fontWeight = FontWeight.Bold) }\n'''
s = must_replace(s, old, new, 'sudoku clear actions')
write(path, s)

# -----------------------------------------------------------------------------
# Exclude Winter Ride from the app APK but keep its source/module for later work.
# Bump validation version.
# -----------------------------------------------------------------------------
path = 'app/build.gradle.kts'
s = read(path)
s = must_replace(s, '    implementation(project(":games:winterride"))\n', '', 'winter ride dependency')
s = must_replace(s, 'versionCode = 47', 'versionCode = 48', 'version code')
s = must_replace(s, 'versionName = "1.1.0-dev41"', 'versionName = "1.1.0-dev42"', 'version name')
write(path, s)
