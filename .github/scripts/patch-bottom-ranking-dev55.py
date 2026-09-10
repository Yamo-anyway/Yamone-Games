from pathlib import Path
import re

repo = Path('.')
app = repo / 'app/src/main/java/com/yamone/games/YamoneGamesApp.kt'
ranking = repo / 'app/src/main/java/com/yamone/games/OnlineRankingUi.kt'
activity = repo / 'app/src/main/java/com/yamone/games/MainActivity.kt'
gradle = repo / 'app/build.gradle.kts'

# Shared game-list icon so the ranking landing can use the exact same icon component as Home.
s = app.read_text()
s = s.replace('private enum class GameIconKind {', 'internal enum class GameIconKind {', 1)
s = s.replace('private fun GameListIcon(kind: GameIconKind, themeMode: YamoneThemeMode) {', 'internal fun GameListIcon(kind: GameIconKind, themeMode: YamoneThemeMode) {', 1)

old_nav = '''    Surface(\n        color = barColor,\n        shadowElevation = 2.dp\n    ) {\n        Row(\n            Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 12.dp, vertical = 6.dp),\n            horizontalArrangement = Arrangement.spacedBy(8.dp),\n            verticalAlignment = Alignment.CenterVertically\n        ) {\n            tabs.forEach { (target, kind, label) ->\n                val selected = screen == target\n                Surface(\n                    modifier = Modifier.weight(1f).height(58.dp),\n                    onClick = { onSelect(target) },\n                    shape = RoundedCornerShape(19.dp),\n                    color = if (selected) selectedColor else Color.Transparent\n                ) {\n                    Column(\n                        Modifier.fillMaxSize(),\n                        horizontalAlignment = Alignment.CenterHorizontally,\n                        verticalArrangement = Arrangement.Center\n                    ) {\n                        BottomNavGlyph(kind = kind, color = YamoneInk)\n                        Spacer(Modifier.height(3.dp))\n                        Text(\n                            label,\n                            fontSize = 11.sp,\n                            fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,\n                            color = YamoneInk\n                        )\n                    }\n                }\n            }\n        }\n    }'''
new_nav = '''    Surface(\n        color = barColor,\n        shadowElevation = 2.dp\n    ) {\n        Row(\n            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp, vertical = 6.dp),\n            horizontalArrangement = Arrangement.spacedBy(8.dp),\n            verticalAlignment = Alignment.CenterVertically\n        ) {\n            tabs.forEach { (target, _, label) ->\n                val selected = screen == target\n                Surface(\n                    modifier = Modifier.weight(1f).height(52.dp),\n                    onClick = { onSelect(target) },\n                    shape = RoundedCornerShape(19.dp),\n                    color = if (selected) selectedColor else Color.Transparent\n                ) {\n                    Box(\n                        Modifier.fillMaxSize(),\n                        contentAlignment = Alignment.Center\n                    ) {\n                        Text(\n                            label,\n                            fontSize = 16.sp,\n                            fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,\n                            color = YamoneInk\n                        )\n                    }\n                }\n            }\n        }\n    }'''
if old_nav not in s:
    raise SystemExit('MainBottomBar block not found')
s = s.replace(old_nav, new_nav, 1)
app.write_text(s)

# Ranking landing: use the same list-card spacing, mascot icons, mini game badges, divider and chevron as Home.
s = ranking.read_text()
pattern = re.compile(r'@Composable\nprivate fun RankingLanding\(.*?\n}\n\n@Composable\nprivate fun RankingTabContents', re.S)
replacement = '''@Composable\nprivate fun RankingLanding(\n    themeMode: YamoneThemeMode,\n    onSelect: (ArcadeGameId) -> Unit\n) {\n    val games = listOf(\n        ArcadeGameId.ICE_JUMP,\n        ArcadeGameId.FISH_MUNCH,\n        ArcadeGameId.FISH_MUNCH_TIME_ATTACK,\n        ArcadeGameId.SNOW_RUSH\n    )\n    Column(\n        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 14.dp),\n        verticalArrangement = Arrangement.spacedBy(10.dp)\n    ) {\n        Text("순위", fontSize = 26.sp, fontWeight = FontWeight.Black, color = YamoneInk)\n        Text("게임을 선택해 순위를 확인해요", fontSize = 12.sp, color = YamoneMuted)\n        Spacer(Modifier.height(2.dp))\n        Surface(shape = RoundedCornerShape(24.dp), color = Color.White, shadowElevation = 1.dp) {\n            Column(Modifier.fillMaxWidth()) {\n                games.forEachIndexed { index, game ->\n                    val iconKind = when (game) {\n                        ArcadeGameId.ICE_JUMP -> GameIconKind.ICE_JUMP\n                        ArcadeGameId.FISH_MUNCH, ArcadeGameId.FISH_MUNCH_TIME_ATTACK -> GameIconKind.FISH_MUNCH\n                        ArcadeGameId.SNOW_RUSH -> GameIconKind.SNOW_RUSH\n                    }\n                    Row(\n                        Modifier.fillMaxWidth().clickable { onSelect(game) }.padding(horizontal = 14.dp, vertical = 9.dp),\n                        verticalAlignment = Alignment.CenterVertically\n                    ) {\n                        GameListIcon(iconKind, themeMode)\n                        Spacer(Modifier.width(12.dp))\n                        Text(\n                            arcadeGameTitle(game),\n                            modifier = Modifier.weight(1f),\n                            fontSize = 16.sp,\n                            fontWeight = FontWeight.Black,\n                            color = YamoneInk\n                        )\n                        Text("›", fontSize = 27.sp, color = YamoneMuted)\n                    }\n                    if (index != games.lastIndex) {\n                        HorizontalDivider(\n                            modifier = Modifier.padding(horizontal = 14.dp),\n                            color = Color(0xFFEAF0EF)\n                        )\n                    }\n                }\n            }\n        }\n    }\n}\n\n@Composable\nprivate fun RankingTabContents'''
s2, n = pattern.subn(replacement, s, count=1)
if n != 1:
    raise SystemExit(f'RankingLanding replacement count={n}')
ranking.write_text(s2)

# Force true edge-to-edge and explicitly theme the Android navigation bar as well as its inset backdrop.
s = activity.read_text()
if 'import android.os.Build\n' not in s:
    s = s.replace('import android.os.Bundle\n', 'import android.os.Build\nimport android.os.Bundle\n', 1)
if 'import androidx.compose.ui.graphics.toArgb\n' not in s:
    s = s.replace('import androidx.compose.ui.Modifier\n', 'import androidx.compose.ui.Modifier\nimport androidx.compose.ui.graphics.toArgb\n', 1)
s = s.replace(
    '        super.onCreate(savedInstanceState)\n\n        // UMP consent/privacy status',
    '        super.onCreate(savedInstanceState)\n        WindowCompat.setDecorFitsSystemWindows(window, false)\n\n        // UMP consent/privacy status',
    1
)
old_side = '''            SideEffect {\n                WindowCompat.getInsetsController(window, window.decorView).apply {\n                    isAppearanceLightStatusBars = true\n                    isAppearanceLightNavigationBars = true\n                }\n            }'''
new_side = '''            SideEffect {\n                // Some Android versions add their own white/black navigation-bar scrim.\n                // Set the actual system navigation bar too, so it continues seamlessly from Yamone's soft theme.\n                window.navigationBarColor = navigationBarBackground.toArgb()\n                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {\n                    window.isNavigationBarContrastEnforced = false\n                }\n                WindowCompat.getInsetsController(window, window.decorView).apply {\n                    isAppearanceLightStatusBars = true\n                    isAppearanceLightNavigationBars = true\n                }\n            }'''
if old_side not in s:
    raise SystemExit('MainActivity SideEffect block not found')
s = s.replace(old_side, new_side, 1)
activity.write_text(s)

# Version bump.
s = gradle.read_text()
s = s.replace('// Development validation build: nickname gate + 순위 UI + soft themed navigation.', '// Development validation build: text-only bottom tabs + Home-style ranking list + themed system navigation.', 1)
s = s.replace('versionCode = 60', 'versionCode = 61', 1)
s = s.replace('versionName = "1.1.0-dev54"', 'versionName = "1.1.0-dev55"', 1)
gradle.write_text(s)
