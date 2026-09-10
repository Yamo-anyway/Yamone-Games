from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)


app = Path("app/src/main/java/com/yamone/games/YamoneGamesApp.kt")
s = app.read_text()

s = replace_once(
    s,
    "import androidx.compose.ui.geometry.Offset\nimport androidx.compose.ui.geometry.Size\n",
    "import androidx.compose.ui.geometry.CornerRadius\nimport androidx.compose.ui.geometry.Offset\nimport androidx.compose.ui.geometry.Size\n",
    "CornerRadius import",
)
s = replace_once(
    s,
    "import androidx.compose.ui.graphics.StrokeCap\n",
    "import androidx.compose.ui.graphics.StrokeCap\nimport androidx.compose.ui.graphics.drawscope.Stroke\n",
    "Stroke import",
)

s = replace_once(
    s,
    "                topBar = { MainTopBar(mascot, themeMode) },\n",
    '''                topBar = {
                    MainTopBar(
                        mascot = mascot,
                        themeMode = themeMode,
                        onSettings = {
                            screenName = AppScreen.SETTINGS.name
                            refreshKey++
                        }
                    )
                },
''',
    "top bar call",
)

old = '''@Composable
private fun MainTopBar(mascot: YamoneMascot, themeMode: YamoneThemeMode) {
    Surface(
        color = yamonePrimarySoft(themeMode),
        shape = RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp),
        shadowElevation = 2.dp
    ) {
        Row(
            Modifier.fillMaxWidth().height(78.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("야모네 게임", fontSize = 27.sp, fontWeight = FontWeight.Black, color = yamonePrimaryDark(themeMode))
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
    NavigationBar(containerColor = yamoneSecondarySoft(themeMode), tonalElevation = 0.dp) {
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
'''

new = r'''@Composable
private fun MainTopBar(
    mascot: YamoneMascot,
    themeMode: YamoneThemeMode,
    onSettings: () -> Unit
) {
    Surface(
        color = yamonePrimarySoft(themeMode),
        shape = RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp),
        shadowElevation = 2.dp
    ) {
        Row(
            Modifier.fillMaxWidth().height(78.dp).padding(start = 18.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "야모네 게임",
                fontSize = 27.sp,
                fontWeight = FontWeight.Black,
                color = yamonePrimaryDark(themeMode)
            )
            Spacer(Modifier.weight(1f))
            YamoneMascotIcon(mascot, size = 50.dp, accent = yamonePrimary(themeMode))
            Spacer(Modifier.width(6.dp))
            Surface(
                onClick = onSettings,
                modifier = Modifier.size(44.dp),
                color = Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(28.dp)) {
                        val stroke = 3.dp.toPx()
                        val left = size.width * .15f
                        val right = size.width * .85f
                        listOf(.27f, .50f, .73f).forEach { y ->
                            drawLine(
                                color = yamonePrimaryDark(themeMode),
                                start = Offset(left, size.height * y),
                                end = Offset(right, size.height * y),
                                strokeWidth = stroke,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class BottomNavIconKind { HOME, GAMES, RECORDS }

@Composable
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
                                    color = if (selected) dark else YamoneMuted
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            label,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                            color = if (selected) dark else YamoneMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNavGlyph(kind: BottomNavIconKind, color: Color) {
    Canvas(Modifier.size(23.dp)) {
        val stroke = 2.3.dp.toPx()
        when (kind) {
            BottomNavIconKind.HOME -> {
                drawLine(
                    color,
                    Offset(size.width * .12f, size.height * .48f),
                    Offset(size.width * .50f, size.height * .16f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color,
                    Offset(size.width * .50f, size.height * .16f),
                    Offset(size.width * .88f, size.height * .48f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * .25f, size.height * .43f),
                    size = Size(size.width * .50f, size.height * .40f),
                    cornerRadius = CornerRadius(size.width * .08f),
                    style = Stroke(width = stroke)
                )
                drawLine(
                    color,
                    Offset(size.width * .50f, size.height * .61f),
                    Offset(size.width * .50f, size.height * .83f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }

            BottomNavIconKind.GAMES -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * .10f, size.height * .28f),
                    size = Size(size.width * .80f, size.height * .50f),
                    cornerRadius = CornerRadius(size.width * .18f),
                    style = Stroke(width = stroke)
                )
                drawLine(
                    color,
                    Offset(size.width * .30f, size.height * .53f),
                    Offset(size.width * .46f, size.height * .53f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color,
                    Offset(size.width * .38f, size.height * .45f),
                    Offset(size.width * .38f, size.height * .61f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawCircle(color, radius = size.width * .045f, center = Offset(size.width * .67f, size.height * .48f))
                drawCircle(color, radius = size.width * .045f, center = Offset(size.width * .76f, size.height * .59f))
            }

            BottomNavIconKind.RECORDS -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * .15f, size.height * .58f),
                    size = Size(size.width * .18f, size.height * .25f),
                    cornerRadius = CornerRadius(size.width * .04f),
                    style = Stroke(width = stroke)
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * .41f, size.height * .38f),
                    size = Size(size.width * .18f, size.height * .45f),
                    cornerRadius = CornerRadius(size.width * .04f),
                    style = Stroke(width = stroke)
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * .67f, size.height * .49f),
                    size = Size(size.width * .18f, size.height * .34f),
                    cornerRadius = CornerRadius(size.width * .04f),
                    style = Stroke(width = stroke)
                )
                drawCircle(
                    color = color,
                    radius = size.width * .08f,
                    center = Offset(size.width * .50f, size.height * .19f),
                    style = Stroke(width = stroke)
                )
            }
        }
    }
}
'''

s = replace_once(s, old, new, "top/bottom navigation")
app.write_text(s)

build = Path("app/build.gradle.kts")
b = build.read_text()
b = replace_once(b, "versionCode = 55", "versionCode = 56", "version code")
b = replace_once(b, 'versionName = "1.1.0-dev49"', 'versionName = "1.1.0-dev50"', "version name")
build.write_text(b)

print("dev50 bottom navigation patch applied")
