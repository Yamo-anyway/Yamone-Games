from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    n = text.count(old)
    if n != 1:
        raise SystemExit(f"{label}: expected 1 match, found {n}")
    return text.replace(old, new, 1)

app = Path('app/src/main/java/com/yamone/games/YamoneGamesApp.kt')
s = app.read_text()
old = '''@Composable
private fun MainBottomBar(screen: AppScreen, themeMode: YamoneThemeMode, onSelect: (AppScreen) -> Unit) {
    val tabs = listOf(
        Triple(AppScreen.HOME, BottomNavIconKind.HOME, "홈"),
        Triple(AppScreen.GAMES, BottomNavIconKind.GAMES, "게임"),
        Triple(AppScreen.RECORDS, BottomNavIconKind.RECORDS, "기록")
    )

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = 6.dp
    ) {
        Row(
            Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, (target, kind, label) ->
                val selected = screen == target
                val accent = if (index % 2 == 0) yamonePrimary(themeMode) else yamoneSecondary(themeMode)
                val dark = if (index % 2 == 0) yamonePrimaryDark(themeMode) else yamoneSecondary(themeMode)
                val soft = if (index % 2 == 0) yamonePrimarySoft(themeMode) else yamoneSecondarySoft(themeMode)

                Surface(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = { onSelect(target) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) soft else Color.Transparent
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = RoundedCornerShape(13.dp),
                            color = soft.copy(alpha = if (selected) 1f else .68f),
                            border = if (selected) BorderStroke(1.5.dp, accent.copy(alpha = .55f)) else null
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                BottomNavGlyph(
                                    kind = kind,
                                    color = if (selected) dark else accent.copy(alpha = .82f)
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            label,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                            color = if (selected) dark else dark.copy(alpha = .68f)
                        )
                    }
                }
            }
        }
    }
}
'''
new = '''@Composable
private fun MainBottomBar(screen: AppScreen, themeMode: YamoneThemeMode, onSelect: (AppScreen) -> Unit) {
    val tabs = listOf(
        Triple(AppScreen.HOME, BottomNavIconKind.HOME, "홈"),
        Triple(AppScreen.GAMES, BottomNavIconKind.GAMES, "게임"),
        Triple(AppScreen.RECORDS, BottomNavIconKind.RECORDS, "기록")
    )
    val accent = yamonePrimary(themeMode)
    val dark = yamonePrimaryDark(themeMode)
    val soft = yamonePrimarySoft(themeMode)

    Surface(
        color = Color.White,
        shadowElevation = 6.dp
    ) {
        Row(
            Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { (target, kind, label) ->
                val selected = screen == target
                Surface(
                    modifier = Modifier.weight(1f).height(60.dp),
                    onClick = { onSelect(target) },
                    shape = RoundedCornerShape(19.dp),
                    color = if (selected) soft else Color.Transparent
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        BottomNavGlyph(
                            kind = kind,
                            color = if (selected) dark else accent.copy(alpha = .58f)
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            label,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                            color = if (selected) dark else dark.copy(alpha = .54f)
                        )
                    }
                }
            }
        }
    }
}
'''
s = replace_once(s, old, new, 'bottom nav block')
app.write_text(s)

main = Path('app/src/main/java/com/yamone/games/MainActivity.kt')
m = main.read_text()
m = replace_once(m, 'import com.yamone.games.sudoku.ui.theme.yamoneSecondarySoft\n', '', 'secondary soft import')
m = replace_once(m, '            val navigationBarBackground = yamoneSecondarySoft(themeMode)\n', '            val navigationBarBackground = yamonePrimarySoft(themeMode)\n', 'navigation bar color')
m = replace_once(m, '                    // the complementary Yamone theme color instead of falling back to white/black.\n', '                    // the selected Yamone theme color instead of falling back to white/black.\n', 'nav comment')
main.write_text(m)

build = Path('app/build.gradle.kts')
b = build.read_text()
b = replace_once(b, 'versionCode = 57', 'versionCode = 58', 'version code')
b = replace_once(b, 'versionName = "1.1.0-dev51"', 'versionName = "1.1.0-dev52"', 'version name')
build.write_text(b)

print('dev52 activity-style navigation patch applied')
