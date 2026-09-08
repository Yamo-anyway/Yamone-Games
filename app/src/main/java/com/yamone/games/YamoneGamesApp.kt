package com.yamone.games

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
import com.yamone.games.fishmunch.FishMunchScreen
import com.yamone.games.icejump.IceJumpScreen
import com.yamone.games.snowrush.SnowRushScreen
import com.yamone.games.sudoku.game.GameStorage
import com.yamone.games.sudoku.game.SudokuDifficulty
import com.yamone.games.sudoku.game.SudokuStats
import com.yamone.games.sudoku.ui.SudokuApp
import com.yamone.games.sudoku.ui.theme.*

private enum class AppScreen {
    HOME, GAMES, RECORDS, SETTINGS, SUDOKU, ICE_JUMP, FISH_MUNCH, SNOW_RUSH
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
    val storage = remember { GameStorage(context) }
    var screenName by rememberSaveable { mutableStateOf(AppScreen.HOME.name) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val screen = runCatching { AppScreen.valueOf(screenName) }.getOrDefault(AppScreen.HOME)

    BackHandler(enabled = screen != AppScreen.HOME) {
        refreshKey++
        screenName = AppScreen.HOME.name
    }

    val goHome = {
        refreshKey++
        screenName = AppScreen.HOME.name
    }

    when (screen) {
        AppScreen.SUDOKU -> {
            SudokuApp(onBack = goHome, themeMode = themeMode, mascot = mascot)
            return
        }
        AppScreen.ICE_JUMP -> {
            IceJumpScreen(
                onBack = goHome,
                primary = yamonePrimary(themeMode),
                primaryDark = yamonePrimaryDark(themeMode),
                soft = yamonePrimarySoft(themeMode),
                ink = YamoneInk,
                muted = YamoneMuted,
                mascotContent = { size -> YamoneMascotIcon(mascot, size = size, accent = yamonePrimary(themeMode)) }
            )
            return
        }
        AppScreen.FISH_MUNCH -> {
            FishMunchScreen(
                onBack = goHome,
                primary = yamonePrimary(themeMode),
                primaryDark = yamonePrimaryDark(themeMode),
                soft = yamonePrimarySoft(themeMode),
                ink = YamoneInk,
                muted = YamoneMuted,
                mascotContent = { size -> YamoneMascotIcon(mascot, size = size, accent = yamonePrimary(themeMode)) }
            )
            return
        }
        AppScreen.SNOW_RUSH -> {
            SnowRushScreen(
                onBack = goHome,
                primary = yamonePrimary(themeMode),
                primaryDark = yamonePrimaryDark(themeMode),
                soft = yamonePrimarySoft(themeMode),
                ink = YamoneInk,
                muted = YamoneMuted,
                mascotContent = { size -> YamoneMascotIcon(mascot, size = size, accent = yamonePrimary(themeMode)) }
            )
            return
        }
        else -> Unit
    }

    val stats = remember(refreshKey, screenName) { storage.stats() }
    val savedLevels = remember(refreshKey, screenName) {
        SudokuDifficulty.entries.filter { storage.hasSaved(it) }
    }

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
                    onIceJump = { screenName = AppScreen.ICE_JUMP.name },
                    onFishMunch = { screenName = AppScreen.FISH_MUNCH.name },
                    onSnowRush = { screenName = AppScreen.SNOW_RUSH.name }
                )
                AppScreen.GAMES -> GamesScreen(
                    themeMode = themeMode,
                    onSudoku = { screenName = AppScreen.SUDOKU.name },
                    onIceJump = { screenName = AppScreen.ICE_JUMP.name },
                    onFishMunch = { screenName = AppScreen.FISH_MUNCH.name },
                    onSnowRush = { screenName = AppScreen.SNOW_RUSH.name }
                )
                AppScreen.RECORDS -> RecordsScreen(themeMode, mascot, stats)
                AppScreen.SETTINGS -> SettingsScreen(
                    themeMode = themeMode,
                    mascot = mascot,
                    nickname = nickname,
                    onThemeChange = onThemeChange,
                    onMascotChange = onMascotChange,
                    onNicknameChange = onNicknameChange
                )
                else -> Unit
            }
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
            YamoneMascotIcon(mascot, size = 42.dp, accent = yamonePrimary(themeMode))
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
    val dark = yamonePrimaryDark(themeMode)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(shape = RoundedCornerShape(28.dp), color = yamonePrimarySoft(themeMode)) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("오늘은 뭐 하고 놀까?", fontSize = 22.sp, fontWeight = FontWeight.Black, color = YamoneInk)
                    Spacer(Modifier.height(6.dp))
                    Text("스도쿠는 완성! 아케이드 3개를 기본 제작 중이에요 ♡", fontSize = 12.sp, color = YamoneMuted)
                }
                YamoneMascotIcon(mascot, size = 74.dp, accent = accent)
            }
        }

        Text("플레이", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onSudoku),
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(18.dp), color = yamonePrimarySoft(themeMode)) {
                    Text("9×9", modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp), fontSize = 18.sp, fontWeight = FontWeight.Black, color = dark)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("스도쿠", fontSize = 19.sp, fontWeight = FontWeight.Black, color = YamoneInk)
                    Text(
                        if (savedLevels.isEmpty()) "새 게임 시작하기"
                        else "${savedLevels.joinToString(" · ") { it.label }} 임시저장됨",
                        fontSize = 12.sp,
                        color = YamoneMuted
                    )
                }
                Text("›", fontSize = 28.sp, color = accent)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat(Modifier.weight(1f), "완료", "${stats.totalCompleted}판", themeMode)
            MiniStat(Modifier.weight(1f), "연속", "${stats.currentStreak}일", themeMode)
            MiniStat(Modifier.weight(1f), "임시저장", "${savedLevels.size}개", themeMode)
        }

        Text("개발중", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
        DevelopmentGameCard(Modifier.fillMaxWidth(), "빙하 점프", "▲", "자동 점프 · 좌우 이동 · 높이 기록", themeMode, onIceJump)
        DevelopmentGameCard(Modifier.fillMaxWidth(), "물고기 냠냠", "🐟", "좌우 이동 · 물고기 먹기 · 마리 수 기록", themeMode, onFishMunch)
        DevelopmentGameCard(Modifier.fillMaxWidth(), "눈덩이 러시", "●", "좌우 이동 · 눈덩이 피하기 · 생존 기록", themeMode, onSnowRush)
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
        Text("완성된 게임과 지금 만들고 있는 게임만 보여드려요.", fontSize = 12.sp, color = YamoneMuted)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActiveGameCard(Modifier.weight(1f), "스도쿠", "9×9", themeMode, onSudoku)
            DevelopmentGameCard(Modifier.weight(1f), "빙하 점프", "▲", "높이 기록", themeMode, onIceJump)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DevelopmentGameCard(Modifier.weight(1f), "물고기 냠냠", "🐟", "마리 수 기록", themeMode, onFishMunch)
            DevelopmentGameCard(Modifier.weight(1f), "눈덩이 러시", "●", "생존 기록", themeMode, onSnowRush)
        }
    }
}

@Composable
private fun RecordsScreen(themeMode: YamoneThemeMode, mascot: YamoneMascot, stats: SudokuStats) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(shape = RoundedCornerShape(26.dp), color = yamonePrimarySoft(themeMode)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                YamoneMascotIcon(mascot, size = 62.dp, accent = yamonePrimary(themeMode))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("나의 스도쿠 기록", fontSize = 21.sp, fontWeight = FontWeight.Black, color = YamoneInk)
                    Text("조금씩 쌓이는 기록도 게임의 재미 ♡", fontSize = 12.sp, color = YamoneMuted)
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigStatCard(Modifier.weight(1f), "완료한 게임", "${stats.totalCompleted}판", themeMode)
            BigStatCard(Modifier.weight(1f), "연속 플레이", "${stats.currentStreak}일", themeMode)
        }

        Text("난이도별 최고 기록", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
        stats.difficultyStats.forEach { item ->
            Surface(shape = RoundedCornerShape(18.dp), color = Color.White) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp), color = yamonePrimarySoft(themeMode)) {
                        Text(item.difficulty.label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontWeight = FontWeight.Bold, color = yamonePrimaryDark(themeMode))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("완료 ${item.completed}판", fontSize = 12.sp, color = YamoneMuted)
                    Spacer(Modifier.weight(1f))
                    Text(item.bestSeconds?.let(::formatDuration) ?: "—", fontWeight = FontWeight.ExtraBold, color = YamoneInk)
                }
            }
        }

        stats.recentDifficulty?.let { recentDifficulty ->
            Text("최근 완료", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
            Surface(shape = RoundedCornerShape(18.dp), color = yamoneSecondarySoft(themeMode)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("${recentDifficulty.label} · ${formatDuration(stats.recentElapsedSeconds)}", fontWeight = FontWeight.ExtraBold, color = YamoneInk)
                    Text("실수 ${stats.recentMistakes}회", fontSize = 12.sp, color = YamoneMuted)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsScreen(
    themeMode: YamoneThemeMode,
    mascot: YamoneMascot,
    nickname: String,
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
                Text("공유카드에 표시돼요 · 기기에만 저장돼요", fontSize = 10.sp, color = YamoneMuted)
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = yamonePrimary(themeMode),
                unfocusedBorderColor = yamonePrimaryLine(themeMode),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                cursorColor = yamonePrimaryDark(themeMode)
            )
        )

        Surface(shape = RoundedCornerShape(26.dp), color = yamonePrimarySoft(themeMode)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                YamoneMascotIcon(mascot, size = 88.dp, accent = yamonePrimary(themeMode))
                Spacer(Modifier.height(8.dp))
                Text("게임 화면에도 바로 적용돼요 ♡", fontSize = 11.sp, color = YamoneMuted)
            }
        }

        Text("캐릭터", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            YamoneMascot.entries.forEach { option ->
                SelectorCard(Modifier.weight(1f), mascot == option, themeMode, { onMascotChange(option) }) {
                    YamoneMascotIcon(option, size = 72.dp, accent = yamonePrimary(themeMode))
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.Black, color = YamoneInk)
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(10.dp), color = yamonePrimarySoft(themeMode)) {
                        Text(
                            "개발중",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = yamonePrimaryDark(themeMode)
                        )
                    }
                }
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
