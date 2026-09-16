"""One-time checked migration. Preserve release/0.2.00 and existing user data."""
from pathlib import Path
import shutil, subprocess, sys
ROOT=Path(__file__).resolve().parents[2]
MARKER=ROOT/'docs/V03_MIGRATED'
if MARKER.exists():
    print('v0.3 migration already materialized; no source rewrites')
    raise SystemExit(0)

def edit(relative, changes):
    path=ROOT/relative; text=path.read_text()
    for before,after in changes:
        if before not in text: raise RuntimeError(f'Migration precondition failed: {relative}: {before[:90]}')
        text=text.replace(before,after)
    path.write_text(text)

def replace_function(text,start,end,replacement):
    a=text.index(start); b=text.index(end,a)
    return text[:a]+replacement+'\n\n'+text[b:]

core='games/arcadecore/src/main/java/com/yamone/games/arcadecore'
app='app/src/main/java/com/yamone/games'
sudoku='games/sudoku/src/main/java/com/yamone/games/sudoku/ui/SudokuApp.kt'
shutil.copyfile(ROOT/'tools/v03/GameFeedback.kt', ROOT/core/'GameFeedback.kt')
shutil.copyfile(ROOT/'tools/v03/RedesignUi.kt', ROOT/app/'RedesignUi.kt')
edit('app/build.gradle.kts', [('versionName = "0.2.00"','versionName = "0.3.00"'),('?: 20000','?: 30000')])
edit('games/sudoku/build.gradle.kts', [('dependencies {','dependencies {\n    testImplementation("junit:junit:4.13.2")\n    implementation(project(":games:arcadecore"))')])
edit('app/src/main/AndroidManifest.xml', [('android:icon="@drawable/yamone_bear_pink"','android:icon="@mipmap/ic_launcher"'),('android:roundIcon="@drawable/yamone_bear_pink"','android:roundIcon="@mipmap/ic_launcher"')])
edit(f'{core}/ArcadeRecords.kt', [('    private fun read(game: ArcadeGameId)', '''    fun deleteSelected(games: Set<ArcadeGameId>) {
        if (games.isEmpty()) return
        prefs.edit().also { editor -> games.forEach { editor.remove(key(it)) } }.apply()
    }

    private fun read(game: ArcadeGameId)''')])
edit(f'{app}/MainActivity.kt', [
 ('import android.os.Build','import com.yamone.games.arcadecore.GameFeedback\nimport android.os.Build'),
 ('super.onCreate(savedInstanceState)','super.onCreate(savedInstanceState)\n        GameFeedback.attach(this, window.decorView)'),
 ('super.onResume()','super.onResume()\n        GameFeedback.foreground(true)'),
 ('    override fun onDestroy() {','''    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        GameFeedback.windowFocus(hasFocus)
    }
    override fun onPause() {
        GameFeedback.foreground(false)
        super.onPause()
    }
    override fun onDestroy() {
        GameFeedback.release()''')])
path=ROOT/app/'YamoneGamesApp.kt'; text=path.read_text()
text=text.replace('import android.content.Context','import com.yamone.games.arcadecore.GameFeedback\nimport android.content.Context')
text=text.replace('var onlineRankingEnabled by remember { mutableStateOf(true) }','var onlineRankingEnabled by remember { mutableStateOf(rankingRepository.enabled()) }')
text=text.replace('        rankingRepository.setEnabled(true)\n        if (nicknameConfigured) rankingRepository.syncNickname(nickname)', '''        if (nicknameConfigured) {
            rankingRepository.syncRankingState(nickname)
            if (onlineRankingEnabled) rankingRepository.syncNickname(nickname)
        }''')
text=text.replace('    val hitbox = hitboxFor(mascot)','''    LaunchedEffect(screenName, showInterstitialTestAd, showRewardedTestAd) {
        GameFeedback.setBlocked(showInterstitialTestAd || showRewardedTestAd)
        GameFeedback.setScene(when (screen) {
            AppScreen.SUDOKU -> "sudoku"
            AppScreen.ICE_JUMP -> "ice"
            AppScreen.FISH_MUNCH -> "fish"
            AppScreen.SNOW_RUSH -> "snow"
            else -> "home"
        })
    }
    val hitbox = hitboxFor(mascot)''')
text=text.replace('AppScreen.HOME -> HomeScreen(', 'AppScreen.HOME -> V3HomeScreen(')
text=text.replace('AppScreen.RECORDS -> RankingTabScreen(', 'AppScreen.RECORDS -> V3RankingScreen(')
text=text.replace('containerColor = yamonePrimarySoft(themeMode),','containerColor = V3Background,')
text=text.replace('Text("닉네임과 캐릭터, 색상을 골라요.", fontSize = 14.sp, color = YamoneMuted)', '''Text("내 취향에 맞게 소리와 플레이를 조절해요.", fontSize = 14.sp, color = YamoneMuted)
        V3SoundSettings()
        OnlineRankingSettingsSection(themeMode, onlineRankingEnabled, rankingRepository, onOnlineRankingEnabledChange)
        V3DataSettings()
        Text("야모네 게임 0.3.00", fontSize = 12.sp, color = YamoneMuted)''')
text=replace_function(text,'@Composable\nprivate fun MainTopBar(', 'private enum class BottomNavIconKind', '''@Composable
private fun MainTopBar(mascot: YamoneMascot, themeMode: YamoneThemeMode, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(V3Background).height(78.dp).padding(horizontal=18.dp), verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row {
                Text("야모네 ",fontSize=26.sp,fontWeight=FontWeight.Black,color=yamonePrimaryDark(themeMode))
                Text("게임",fontSize=26.sp,fontWeight=FontWeight.Black,color=Color(0xFFDE6F91))
            }
            Text("나만의 작은 놀이터",fontSize=11.sp,color=YamoneMuted)
        }
        Surface(onClick={GameFeedback.tap();onSettings()},color=Color.White,shape=RoundedCornerShape(16.dp)) {
            Box(Modifier.size(44.dp),contentAlignment=Alignment.Center) { Text("⚙",fontSize=24.sp,color=yamonePrimaryDark(themeMode)) }
        }
    }
}''')
text=replace_function(text,'@Composable\nprivate fun MainBottomBar(', '@Composable\nprivate fun BottomNavGlyph(', '''@Composable
private fun MainBottomBar(screen: AppScreen, themeMode: YamoneThemeMode, onSelect: (AppScreen) -> Unit) {
    Surface(color=Color.White,shape=RoundedCornerShape(topStart=24.dp,topEnd=24.dp)) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal=12.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(Triple(AppScreen.HOME,"⌂","홈"),Triple(AppScreen.RECORDS,"★","랭킹"),Triple(AppScreen.SETTINGS,"⚙","설정")).forEach { (target,icon,label) ->
                val selected=screen==target
                Surface(onClick={GameFeedback.tap();onSelect(target)},modifier=Modifier.weight(1f).fillMaxHeight(),shape=RoundedCornerShape(18.dp),color=if(selected) yamonePrimarySoft(themeMode) else Color.Transparent) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                        Text(icon,fontSize=19.sp,color=if(selected) yamonePrimaryDark(themeMode) else YamoneMuted)
                        Text(label,fontSize=11.sp,fontWeight=FontWeight.Bold,color=if(selected) yamonePrimaryDark(themeMode) else YamoneMuted)
                    }
                }
            }
        }
    }
}''')
path.write_text(text)
theme='games/sudoku/src/main/java/com/yamone/games/sudoku/ui/theme/Theme.kt'
edit(theme,[
 ('val YamoneMint = Color(0xFF3BC9B0)','val YamoneMint = Color(0xFF239E8B)'),
 ('val YamonePink = Color(0xFFFF7FA4)','val YamonePink = Color(0xFFD96387)'),
 ('val YamoneMuted = Color(0xFF73858B)','val YamoneMuted = Color(0xFF62787A)'),
 ('surface = Color.White,','surface = Color.White,\n        outline = Color.Transparent,\n        outlineVariant = Color(0xFFE1EEEA),'),
 ('R.drawable.yamone_seal_pink','R.drawable.yamone_seal_pink_cutout'),
 ('R.drawable.yamone_seal_mint','R.drawable.yamone_seal_mint_cutout'),
 ('R.drawable.yamone_bear_pink','R.drawable.yamone_bear_pink_cutout'),
 ('R.drawable.yamone_bear_mint','R.drawable.yamone_bear_mint_cutout'),
 ('maskApprovedMascot(BitmapFactory.decodeResource(context.resources, imageRes), mascot).asImageBitmap()','BitmapFactory.decodeResource(context.resources, imageRes).asImageBitmap()')])
# Serialize online writes and retain pending improvements that arrive mid-request.
ranking=ROOT/app/'OnlineRankingSupport.kt'; t=ranking.read_text()
t=t.replace('import kotlinx.coroutines.Dispatchers','import kotlinx.coroutines.sync.Mutex\nimport kotlinx.coroutines.sync.withLock\nimport kotlinx.coroutines.Dispatchers')
t=t.replace('    private val client = OnlineRankingClient()','    private val client = OnlineRankingClient()\n    private val syncMutex = Mutex()')
t=t.replace('if (normalizedScore <= current) return','if (normalizedScore < current) return')
t=t.replace('    fun clearPending(game: ArcadeGameId) {','''    fun clearPendingIfUnchanged(game: ArcadeGameId, score: Int, nickname: String) {
        if (prefs.getInt(pendingScoreKey(game), -1) == score &&
            prefs.getString(pendingNicknameKey(game), "") == nickname) clearPending(game)
    }

    fun clearPending(game: ArcadeGameId) {''')
t=t.replace('    suspend fun syncRankingState(nickname: String) {','''    suspend fun syncRankingState(nickname: String) = syncMutex.withLock { syncStateUnlocked(nickname) }

    private suspend fun syncStateUnlocked(nickname: String) {''')
t=t.replace('        flushPending()\n    }\n\n    private fun queueCurrentLocalBests','        flushPendingUnlocked()\n    }\n\n    private fun queueCurrentLocalBests')
t=t.replace('    suspend fun flushPending() {','''    suspend fun flushPending() = syncMutex.withLock { flushPendingUnlocked() }

    private suspend fun flushPendingUnlocked() {''')
t=t.replace('            store.clearPending(game)','            store.clearPendingIfUnchanged(game, pending.score, pending.nickname)')
t=t.replace('    suspend fun deleteSelectedOnlineRecords(games: Set<ArcadeGameId>): OnlineRankingDeleteResult {','''    suspend fun deleteSelectedOnlineRecords(games: Set<ArcadeGameId>): OnlineRankingDeleteResult =
        syncMutex.withLock { deleteSelectedUnlocked(games) }

    private suspend fun deleteSelectedUnlocked(games: Set<ArcadeGameId>): OnlineRankingDeleteResult {''')
ranking.write_text(t)
for module,filename,score,scene in [
 ('icejump','IceJumpScreen.kt','heightScore','ice'),
 ('fishmunch','FishMunchScreen.kt','score','fish'),
 ('snowrush','SnowRushScreen.kt','score','snow')]:
    p=ROOT/f'games/{module}/src/main/java/com/yamone/games/{module}/{filename}'
    t=p.read_text().replace('import androidx.activity.compose.BackHandler','import com.yamone.games.arcadecore.GameFeedback\nimport androidx.activity.compose.BackHandler')
    t=t.replace('    var exitConfirm by remember { mutableStateOf(false) }','''    var exitConfirm by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var newBest by remember { mutableStateOf(false) }
    var roundBestBefore by remember { mutableIntStateOf(0) }
    DisposableEffect(paused, exitConfirm, state.gameOver) {
        GameFeedback.setPaused(paused || exitConfirm || state.gameOver)
        onDispose { }
    }
    DisposableEffect(Unit) { onDispose { GameFeedback.setPaused(false) } }''')
    t=t.replace('BackHandler { requestExit() }','BackHandler { if (paused) paused = false else requestExit() }')
    t=t.replace('previous != 0L && !exitConfirm','previous != 0L && !exitConfirm && !paused && GameFeedback.canAdvance')
    t=t.replace('.pointerInput(state.started, state.gameOver, exitConfirm)', '.pointerInput(state.started, state.gameOver, exitConfirm, paused)')
    t=t.replace('if (state.started && !state.gameOver && !exitConfirm)', 'if (state.started && !state.gameOver && !exitConfirm && !paused && GameFeedback.canAdvance)')
    t=t.replace('            mascotContent(40.dp)','''            IconButton(onClick = { GameFeedback.tap(); paused = true }, enabled = state.started && !state.gameOver) {
                Text("Ⅱ", fontSize = 25.sp, color = primaryDark)
            }''')
    if module=='icejump':
        t=t.replace('    fun restart() {\n        lastRecord = null','    fun restart() {\n        roundBestBefore = bestHeight\n        newBest = false\n        paused = false\n        GameFeedback.play("start")\n        lastRecord = null')
        t=t.replace('            if (landing != null) {','            if (landing != null) {\n                GameFeedback.play("jump")')
        t=t.replace('                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF3D8FB2).copy(alpha = 0.58f))','                    border = null')
        t=t.replace('Text("앗, 미끄러졌어요!",','Text(if (newBest) "새로운 최고기록!" else "한 칸 더 높이 도전!",')
        t=t.replace('                        Text("최고 기록 ${topRecords.firstOrNull()?.score ?: state.heightScore}m",','                        Text(if (newBest) "★ 멋진 도전이었어요!" else "최고 기록 ${topRecords.firstOrNull()?.score ?: state.heightScore}m",')
    else:
        if module=='fishmunch':
            t=t.replace('    fun start(mode: FishMode) {','''    fun start(mode: FishMode) {
        roundBestBefore = (if (mode == FishMode.NORMAL) normalRecords else timeAttackRecords).firstOrNull()?.score ?: 0
        newBest = false
        paused = false
        GameFeedback.play("start")''')
            t=t.replace('    fun restart() {','    fun restart() {\n        roundBestBefore = best\n        newBest = false\n        paused = false\n        GameFeedback.play("start")')
            t=t.replace('caught -> score++','caught -> { score++; GameFeedback.play("collect") }')
            t=t.replace('onExit = ::selectModeAgain','onExit = onBack')
            t=t.replace('Text(if (mode == FishMode.TIME_ATTACK) "60초 끝!" else "앗, 물고기를 놓쳤어요!",','Text(if (isNewBest) "새로운 최고기록!" else if (mode == FishMode.TIME_ATTACK) "60초 도전 완료!" else "즐거운 바다 한 판!",')
        else:
            t=t.replace('    fun restart() {','    fun restart() {\n        roundBestBefore = best\n        newBest = false\n        paused = false\n        GameFeedback.play("start")')
            t=t.replace('if (escaped > 0) dodged += escaped','if (escaped > 0) { dodged += escaped; GameFeedback.play("collect") }')
            t=t.replace('Text("앗! 눈에 닿았어요",','Text(if (isNewBest) "새로운 최고기록!" else "눈밭에서 멋진 도전!",')
            t=t.replace('Color(0xFFC2D9E7), Color(0xFF9EBFD2), Color(0xFF7FA5BC)','Color(0xFFEAF1FA), Color(0xFFD2E8F2), Color(0xFFA9D5E4)')
        t=t.replace('                ResultOverlay(\n','                ResultOverlay(\n                    isNewBest = newBest,\n')
        t=t.replace('private fun BoxScope.ResultOverlay(\n','private fun BoxScope.ResultOverlay(\n    isNewBest: Boolean,\n')
    t=t.replace('        if (state.gameOver && lastRecord == null) {',f'''        if (state.gameOver && lastRecord == null) {{
            newBest = state.{score} > roundBestBefore
            GameFeedback.play(if (newBest) "record" else "finish")''')
    t=t.replace('.padding(horizontal = 14.dp, vertical = 8.dp)','.padding(horizontal = 8.dp, vertical = 5.dp)')
    t=t.replace('.clip(RoundedCornerShape(28.dp))','.clip(RoundedCornerShape(22.dp))')
    t=t.replace('OutlinedButton(', 'FilledTonalButton(')
    t=t.replace('import androidx.activity.compose.BackHandler', 'import androidx.compose.material3.*\nimport androidx.activity.compose.BackHandler')
    if module=='icejump': t=t.replace('repeat(7) { index ->','repeat(if (GameFeedback.options.reduceMotion) 0 else 7) { index ->')
    if module=='fishmunch': t=t.replace('repeat(5) { index ->','repeat(if (GameFeedback.options.reduceMotion) 0 else 5) { index ->')
    needle='    if (exitConfirm) {\n        AlertDialog('
    assert needle in t, filename
    t=t.replace(needle,'''    if (paused) {
        var soundOptions by remember { mutableStateOf(GameFeedback.options) }
        AlertDialog(
            onDismissRequest = { paused = false },
            shape = RoundedCornerShape(26.dp),
            title = { Text("잠깐 쉬어가요", fontWeight = FontWeight.Black) },
            text = {
                Column {
                    Text("진행과 기록 시간은 멈춰 있어요.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("배경음악", Modifier.weight(1f))
                        Switch(soundOptions.music, { soundOptions = soundOptions.copy(music=it); GameFeedback.update(soundOptions) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("진동", Modifier.weight(1f))
                        Switch(soundOptions.vibration, { soundOptions = soundOptions.copy(vibration=it); GameFeedback.update(soundOptions) })
                    }
                }
            },
            confirmButton = { Button(onClick = { paused = false; GameFeedback.tap() }) { Text("계속하기") } },
            dismissButton = { TextButton(onClick = { paused = false; requestExit() }) { Text("게임 종료") } }
        )
    }

    if (exitConfirm) {
        AlertDialog(''')
    p.write_text(t)
edit(sudoku, [
 ('import android.content.Context','import com.yamone.games.arcadecore.GameFeedback\nimport android.content.Context'),
 ('                mistakes++','                GameFeedback.play("error")\n                mistakes++'),
 ('                wrongCell = -1\n                removePeerNote','                GameFeedback.play("collect")\n                wrongCell = -1\n                removePeerNote'),
 ('            notes = notes.copyOf().also { it[index] = it[index] xor bit }','            notes = notes.copyOf().also { it[index] = it[index] xor bit }\n            GameFeedback.tap()'),
 ('            completed = true','            GameFeedback.play("success")\n            completed = true'),
 ('    fun tick() {\n        if (!paused && !completed)','    fun tick() {\n        if (!paused && !completed && GameFeedback.canAdvance)'),
 ('    val scrollState = rememberScrollState()','''    val scrollState = rememberScrollState()
    DisposableEffect(game.paused, game.completed, pendingDifficulty) {
        GameFeedback.setPaused(game.paused || game.completed || pendingDifficulty != null)
        onDispose { }
    }
    DisposableEffect(Unit) { onDispose { GameFeedback.setPaused(false) } }'''),
 ('if (thick) 2.4.dp.toPx() else 0.7.dp.toPx()','if (thick) 1.6.dp.toPx() else 0.6.dp.toPx()'),
 ('val color = if (thick) dark else Color(0xFFCFDEDB)','val color = if (thick) dark.copy(alpha=.56f) else Color(0xFFDFEBE6)')])
testdir=ROOT/'games/sudoku/src/test/java/com/yamone/games/sudoku/game'
testdir.mkdir(parents=True,exist_ok=True)
shutil.copyfile(ROOT/'tools/v03/SudokuEngineTest.kt',testdir/'SudokuEngineTest.kt')
docs=ROOT/'docs';docs.mkdir(exist_ok=True)
(docs/'V0.3.00.md').write_text('''# 야모네 게임 0.3.00

- 원본: archive/pre-0.3-original / release/0.2.00. main은 변경하지 않음.
- 설치 package com.yamone.games / 기존 debug signing identity 유지.
- versionName 0.3.00, versionCode 30000. 이후 0.3.01 / 30001 순서.
- 2열 게임 홈, 배경을 제거한 플레이 캐릭터, 새 설치 아이콘, 3개 하단 메뉴.
- 홈/스도쿠/눈덩이/물고기/빙하 오리지널 오프라인 BGM 5종과 효과음 8종.
- 전체/배경/효과/버튼 소리, 3개 음량, 진동, 무음 모드, 효과 줄이기.
- 아케이드 일시정지, 백그라운드/포커스 상실/광고 중 소리 및 진행 정지.
- 실제 기록 단위 유지: 빙하 높이(m), 물고기 수(일반/시간도전 분리), 눈덩이 생존시간.
- 모든 게임의 기기 기록 및 아케이드 온라인 랭킹. 스도쿠 온라인은 미지원.
- 공유 OFF를 재실행 뒤에도 유지. OFF의 온라인 삭제는 네트워크 복구 후 재시도.
- 체크한 기기 기록만 삭제. 온라인 선택 삭제는 기존 별도 기능 유지.
- 스도쿠 메모/고정입력/추측저장/복귀 및 기존 저장 포맷 유지.

## 되돌리기
소스는 release/0.2.00에 보존됨. Android는 낮은 versionCode의 일반 업데이트를
허용하지 않으므로 데이터 보존 복귀는 같은 서명으로 더 높은 코드를 사용한다.
예: `gradle :app:assembleDebug -PyamoneVersionCode=30001` (release/0.2.00 체크아웃 후).
0.3.01 이상을 이미 설치했다면 그보다 큰 값을 사용한다. 앱을 먼저 삭제하지 않는다.

## 검증 범위
CI의 컴파일/단위테스트/서명 검증 결과를 빌드별로 확인한다.
실제 기기의 음질, 무음 모드, 진동 강도, 광고, 네트워크 OFF→ON은 기기 확인이 필요하다.
''')
subprocess.run([sys.executable,str(ROOT/'tools/v03/generate_audio.py')],check=True)
subprocess.run([sys.executable,str(ROOT/'tools/v03/prepare_art.py')],check=True)
MARKER.write_text('0.3.00: source migration materialized\n')
print('v0.3.00 source and resources materialized successfully')
