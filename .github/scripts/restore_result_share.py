from pathlib import Path


def read(path): return Path(path).read_text()
def write(path, text): Path(path).write_text(text)
def rep(s, old, new, label):
    if old not in s:
        raise SystemExit(f'missing anchor: {label}')
    return s.replace(old, new, 1)

# App callbacks: result screens can open share card again.
p = 'app/src/main/java/com/yamone/games/YamoneGamesApp.kt'
s = read(p)
s = rep(s,
'''                landingHalfWidth = hitbox.landingHalfWidth,
                primary = yamonePrimary(themeMode),
''',
'''                landingHalfWidth = hitbox.landingHalfWidth,
                onShareRecord = { shareRequest = ShareCardRequest(ArcadeGameId.ICE_JUMP, it) },
                primary = yamonePrimary(themeMode),
''', 'app ice share')
s = rep(s,
'''                playerHalfHeight = hitbox.halfHeight,
                primary = yamonePrimary(themeMode),
''',
'''                playerHalfHeight = hitbox.halfHeight,
                onShareRecord = { game, record -> shareRequest = ShareCardRequest(game, record) },
                primary = yamonePrimary(themeMode),
''', 'app fish share')
# second playerHalfHeight occurrence is snow rush
idx = s.find('            AppScreen.SNOW_RUSH -> SnowRushScreen(')
if idx < 0: raise SystemExit('snow route missing')
tail = s[idx:]
tail = rep(tail,
'''                playerHalfHeight = hitbox.halfHeight,
                primary = yamonePrimary(themeMode),
''',
'''                playerHalfHeight = hitbox.halfHeight,
                onShareRecord = { shareRequest = ShareCardRequest(ArcadeGameId.SNOW_RUSH, it) },
                primary = yamonePrimary(themeMode),
''', 'app snow share')
s = s[:idx] + tail
write(p, s)

# Ice Jump result: one compact row, 3 equal actions.
p = 'games/icejump/src/main/java/com/yamone/games/icejump/IceJumpScreen.kt'
s = read(p)
s = rep(s, '    landingHalfWidth: Float,\n    primary: Color,', '    landingHalfWidth: Float,\n    onShareRecord: (ArcadeRecord) -> Unit,\n    primary: Color,', 'ice param')
old = '''                        Button(
                            onClick = ::restart,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(17.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primary)
                        ) { Text("다시하기", fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(17.dp)
                        ) { Text("그만하기", fontWeight = FontWeight.Bold) }
'''
new = '''                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = ::restart,
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) { Text("다시하기", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            Button(
                                onClick = { lastRecord?.let(onShareRecord) },
                                enabled = lastRecord != null,
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primary),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) { Text("공유", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            OutlinedButton(
                                onClick = onBack,
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) { Text("그만하기", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
'''
s = rep(s, old, new, 'ice result row')
write(p, s)

# Fish: result share uses current mode's correct game id; quit returns mode select.
p = 'games/fishmunch/src/main/java/com/yamone/games/fishmunch/FishMunchScreen.kt'
s = read(p)
s = rep(s, '    playerHalfHeight: Float,\n    primary: Color,', '    playerHalfHeight: Float,\n    onShareRecord: (ArcadeGameId, ArcadeRecord) -> Unit,\n    primary: Color,', 'fish param')
s = rep(s,
'''                    mascotContent = mascotContent,
                    onRestart = ::restart,
                    onExit = ::selectModeAgain
''',
'''                    mascotContent = mascotContent,
                    onRestart = ::restart,
                    onShare = { lastRecord?.let { onShareRecord(state.mode.gameId, it) } },
                    onExit = ::selectModeAgain,
                    shareEnabled = lastRecord != null
''', 'fish result call')
s = rep(s,
'''    mascotContent: @Composable (Dp) -> Unit,
    onRestart: () -> Unit,
    onExit: () -> Unit
) {
''',
'''    mascotContent: @Composable (Dp) -> Unit,
    onRestart: () -> Unit,
    onShare: () -> Unit,
    onExit: () -> Unit,
    shareEnabled: Boolean
) {
''', 'fish result signature')
old = '''            Button(onClick = onRestart, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp), colors = ButtonDefaults.buttonColors(containerColor = primary)) {
                Text("다시하기", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp)) {
                Text("그만하기", fontWeight = FontWeight.Bold)
            }
'''
new = '''            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onRestart,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) { Text("다시하기", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                Button(
                    onClick = onShare,
                    enabled = shareEnabled,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primary),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) { Text("공유", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = onExit,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) { Text("그만하기", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
'''
s = rep(s, old, new, 'fish result row')
write(p, s)

# Snow Rush result: one compact row, 3 equal actions.
p = 'games/snowrush/src/main/java/com/yamone/games/snowrush/SnowRushScreen.kt'
s = read(p)
s = rep(s, '    playerHalfHeight: Float,\n    primary: Color,', '    playerHalfHeight: Float,\n    onShareRecord: (ArcadeRecord) -> Unit,\n    primary: Color,', 'snow param')
s = rep(s,
'''                    mascotContent = mascotContent,
                    onRestart = ::restart,
                    onExit = onBack
''',
'''                    mascotContent = mascotContent,
                    onRestart = ::restart,
                    onShare = { lastRecord?.let(onShareRecord) },
                    onExit = onBack,
                    shareEnabled = lastRecord != null
''', 'snow result call')
s = rep(s,
'''    mascotContent: @Composable (Dp) -> Unit,
    onRestart: () -> Unit,
    onExit: () -> Unit
) {
''',
'''    mascotContent: @Composable (Dp) -> Unit,
    onRestart: () -> Unit,
    onShare: () -> Unit,
    onExit: () -> Unit,
    shareEnabled: Boolean
) {
''', 'snow result signature')
old = '''            Button(onClick = onRestart, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp), colors = ButtonDefaults.buttonColors(containerColor = primary)) {
                Text("다시하기", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp)) {
                Text("그만하기", fontWeight = FontWeight.Bold)
            }
'''
new = '''            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onRestart,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) { Text("다시하기", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                Button(
                    onClick = onShare,
                    enabled = shareEnabled,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primary),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) { Text("공유", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = onExit,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) { Text("그만하기", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
'''
s = rep(s, old, new, 'snow result row')
write(p, s)

# Validation version bump.
p = 'app/build.gradle.kts'
s = read(p)
s = rep(s, 'versionCode = 48', 'versionCode = 49', 'version code')
s = rep(s, 'versionName = "1.1.0-dev42"', 'versionName = "1.1.0-dev43"', 'version name')
write(p, s)
