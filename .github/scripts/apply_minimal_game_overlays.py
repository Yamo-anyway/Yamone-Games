from pathlib import Path


def read(path):
    return Path(path).read_text()


def write(path, text):
    Path(path).write_text(text)


def rep(text, old, new, label, count=1):
    if old not in text:
        raise SystemExit(f"missing anchor: {label}")
    return text.replace(old, new, count)

arrow_old = '''            Surface(onClick = ::requestExit, shape = RoundedCornerShape(15.dp), color = soft) {
                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    Text("←", fontSize = 20.sp, fontWeight = FontWeight.Black, color = primaryDark)
                }
            }
'''
arrow_new = '''            Surface(onClick = ::requestExit, color = Color.Transparent) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Text("←", fontSize = 30.sp, fontWeight = FontWeight.Black, color = primaryDark)
                }
            }
'''

# Ice Jump: thick standalone back arrow and start button only.
p = 'games/icejump/src/main/java/com/yamone/games/icejump/IceJumpScreen.kt'
s = read(p)
s = rep(s, arrow_old, arrow_new, 'ice back arrow')
old = '''            if (!state.started) {
                Surface(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White.copy(alpha = 0.98f),
                    shadowElevation = 5.dp
                ) {
                    Box(Modifier.padding(horizontal = 30.dp, vertical = 22.dp), contentAlignment = Alignment.Center) {
                        Button(
                            onClick = ::restart,
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primary)
                        ) { Text("시작하기", fontWeight = FontWeight.ExtraBold) }
                    }
                }
            }
'''
new = '''            if (!state.started) {
                Button(
                    onClick = ::restart,
                    modifier = Modifier.align(Alignment.Center),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primary)
                ) { Text("시작하기", fontWeight = FontWeight.ExtraBold) }
            }
'''
s = rep(s, old, new, 'ice start button only')
write(p, s)

# Fish Munch: thick standalone back arrow and only two mode buttons.
p = 'games/fishmunch/src/main/java/com/yamone/games/fishmunch/FishMunchScreen.kt'
s = read(p)
s = rep(s, arrow_old, arrow_new, 'fish back arrow')
s = rep(s,
'''                ModeSelectOverlay(
                    primary = primary,
                    primaryDark = primaryDark,
                    ink = ink,
                    muted = muted,
                    mascotContent = mascotContent,
                    onNormal = { start(FishMode.NORMAL) },
                    onTimeAttack = { start(FishMode.TIME_ATTACK) }
                )
''',
'''                ModeSelectOverlay(
                    primary = primary,
                    primaryDark = primaryDark,
                    onNormal = { start(FishMode.NORMAL) },
                    onTimeAttack = { start(FishMode.TIME_ATTACK) }
                )
''', 'fish mode call')
start = s.index('@Composable\nprivate fun BoxScope.ModeSelectOverlay(')
end = s.index('@Composable\nprivate fun BoxScope.ResultOverlay(', start)
mode = '''@Composable
private fun BoxScope.ModeSelectOverlay(
    primary: Color,
    primaryDark: Color,
    onNormal: () -> Unit,
    onTimeAttack: () -> Unit
) {
    Column(
        modifier = Modifier.align(Alignment.Center).width(210.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onNormal,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primary),
            shape = RoundedCornerShape(17.dp)
        ) { Text("일반 모드", fontWeight = FontWeight.ExtraBold) }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onTimeAttack,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryDark),
            shape = RoundedCornerShape(17.dp)
        ) { Text("타임어택 60초", fontWeight = FontWeight.ExtraBold) }
    }
}

'''
s = s[:start] + mode + s[end:]
write(p, s)

# Snow Rush: thick standalone back arrow and start button only.
p = 'games/snowrush/src/main/java/com/yamone/games/snowrush/SnowRushScreen.kt'
s = read(p)
s = rep(s, arrow_old, arrow_new, 'snow back arrow')
start = s.index('@Composable\nprivate fun BoxScope.StartOverlay(')
end = s.index('@Composable\nprivate fun BoxScope.ResultOverlay(', start)
start_overlay = '''@Composable
private fun BoxScope.StartOverlay(
    primary: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.align(Alignment.Center),
        colors = ButtonDefaults.buttonColors(containerColor = primary),
        shape = RoundedCornerShape(17.dp)
    ) {
        Text("시작하기", fontWeight = FontWeight.Bold)
    }
}

'''
s = s[:start] + start_overlay + s[end:]
write(p, s)

# Sudoku: same standalone thick back arrow.
p = 'games/sudoku/src/main/java/com/yamone/games/sudoku/ui/SudokuApp.kt'
s = read(p)
s = rep(s,
'''            Surface(onClick = onBack, shape = RoundedCornerShape(15.dp), color = yamonePrimarySoft(themeMode)) {
                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    Text("←", fontSize = 20.sp, fontWeight = FontWeight.Black, color = yamonePrimaryDark(themeMode))
                }
            }
''',
'''            Surface(onClick = onBack, color = Color.Transparent) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Text("←", fontSize = 30.sp, fontWeight = FontWeight.Black, color = yamonePrimaryDark(themeMode))
                }
            }
''', 'sudoku back arrow')
write(p, s)

# Validation build version.
p = 'app/build.gradle.kts'
s = read(p)
s = rep(s, 'versionCode = 51', 'versionCode = 52', 'version code')
s = rep(s, 'versionName = "1.1.0-dev45"', 'versionName = "1.1.0-dev46"', 'version name')
write(p, s)
