package com.yamone.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamone.games.arcadecore.*
import com.yamone.games.sudoku.game.*
import com.yamone.games.sudoku.ui.theme.*
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val V3Background = Color(0xFFF5FBF9)
private val V3Rose = Color(0xFFEB7899)

@Composable
internal fun V3BrandIcon(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val id = remember { context.resources.getIdentifier("v03_icon", "drawable", context.packageName) }
    if (id != 0) Image(painterResource(id), "야모네 게임", modifier.clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
}

@Composable
internal fun V3HomeScreen(
    themeMode: YamoneThemeMode, mascot: YamoneMascot, stats: SudokuStats,
    arcadeRecords: Map<ArcadeGameId, List<ArcadeRecord>>, adRemoved: Boolean,
    adFreeUntilMillis: Long, onAdAccess: () -> Unit, onSudoku: () -> Unit,
    onIceJump: () -> Unit, onFishMunch: () -> Unit, onSnowRush: () -> Unit, onRecords: () -> Unit
) {
    val dark = yamonePrimaryDark(themeMode)
    val entries = listOf(
        Triple(GameIconKind.SUDOKU, "스도쿠", onSudoku),
        Triple(GameIconKind.SNOW_RUSH, "눈덩이 러시", onSnowRush),
        Triple(GameIconKind.FISH_MUNCH, "물고기 냠냠", onFishMunch),
        Triple(GameIconKind.ICE_JUMP, "빙하 점프", onIceJump)
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (kind, title, action) ->
                    val caption = when(kind) {
                        GameIconKind.SUDOKU -> "차근차근 숫자 퍼즐"
                        GameIconKind.SNOW_RUSH -> "눈덩이와 파편을 피해요"
                        GameIconKind.FISH_MUNCH -> "놓치지 말고 냠냠!"
                        GameIconKind.ICE_JUMP -> "한 칸 더, 높이 점프!"
                    }
                    val record = when(kind) {
                        GameIconKind.SUDOKU -> "완성 ${stats.totalCompleted}판"
                        GameIconKind.SNOW_RUSH -> "최고 " + preciseDuration(arcadeRecords[ArcadeGameId.SNOW_RUSH]?.firstOrNull()?.score ?: 0)
                        GameIconKind.FISH_MUNCH -> "최고 ${arcadeRecords[ArcadeGameId.FISH_MUNCH]?.firstOrNull()?.score ?: 0}마리"
                        GameIconKind.ICE_JUMP -> "최고 ${formatIceHeight(arcadeRecords[ArcadeGameId.ICE_JUMP]?.firstOrNull()?.score ?: 0)}"
                    }
                    Surface(onClick = { GameFeedback.tap(); action() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(25.dp), color = Color.White, shadowElevation = 1.dp) {
                        Column {
                            V3GameArt(kind, themeMode, Modifier.fillMaxWidth().height(98.dp))
                            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Black, color = YamoneInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(caption, fontSize = 11.sp, color = YamoneMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(record, color = dark, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text("▶", fontSize = 13.sp, color = if(kind == GameIconKind.SNOW_RUSH || kind == GameIconKind.ICE_JUMP) V3Rose else dark)
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
            V3BrandIcon(Modifier.size(42.dp))
            Text("작은 한 판, 커다란 즐거움",Modifier.padding(start=10.dp),fontSize=13.sp,color=dark,fontWeight=FontWeight.Bold)
        }
        Text("오프라인에서도 즐겁게 · 기록은 이 기기에 저장돼요", Modifier.fillMaxWidth().padding(bottom = 18.dp), textAlign = TextAlign.Center, color = YamoneMuted, fontSize = 10.sp)
    }
}

@Composable
private fun V3GameArt(kind: GameIconKind, themeMode: YamoneThemeMode, modifier: Modifier) {
    val colors = when(kind) {
        GameIconKind.SUDOKU -> listOf(Color(0xFFDFF5EB), Color(0xFFCBEBE7))
        GameIconKind.SNOW_RUSH -> listOf(Color(0xFFFFE2EC), Color(0xFFE5EEFB))
        GameIconKind.FISH_MUNCH -> listOf(Color(0xFFBAF0F5), Color(0xFF76CEE2))
        GameIconKind.ICE_JUMP -> listOf(Color(0xFFDBF1FF), Color(0xFFA9DDEB))
    }
    Box(modifier.background(Brush.verticalGradient(colors)), contentAlignment = Alignment.BottomEnd) {
        Canvas(Modifier.matchParentSize()) {
            repeat(8) { i ->
                drawCircle(Color.White.copy(alpha = .6f), (3+i%3).dp.toPx(), Offset(size.width*((i*23+11)%97)/100f, size.height*((i*31+13)%83)/100f))
            }
            if(kind == GameIconKind.SUDOKU) {
                val x = size.width*.09f; val y = size.height*.12f; val w = size.width*.46f; val h = size.height*.74f
                drawRoundRect(Color.White.copy(alpha=.9f), Offset(x,y), Size(w,h), CornerRadius(9.dp.toPx()))
                for(i in 1..3) {
                    drawLine(Color(0xFF8CCDC0), Offset(x+w*i/4,y), Offset(x+w*i/4,y+h), 1.dp.toPx())
                    drawLine(Color(0xFF8CCDC0), Offset(x,y+h*i/4), Offset(x+w,y+h*i/4), 1.dp.toPx())
                }
            } else if(kind == GameIconKind.FISH_MUNCH) {
                listOf(Color(0xFFFFB477), Color(0xFFFFDF76), Color(0xFFF39FBC)).forEachIndexed { i,c ->
                    val x=size.width*(.18f+i*.25f); val y=size.height*(.25f+i%2*.2f)
                    drawOval(c, Offset(x-13.dp.toPx(),y-8.dp.toPx()), Size(26.dp.toPx(),16.dp.toPx()))
                    drawCircle(Color.White, 2.dp.toPx(), Offset(x+7.dp.toPx(),y-2.dp.toPx()))
                }
            } else if(kind == GameIconKind.SNOW_RUSH) {
                drawCircle(Color(0xFFCAE5F5), size.height*.28f, Offset(size.width*.25f,size.height*.70f))
                drawCircle(Color.White.copy(alpha=.94f), size.height*.25f, Offset(size.width*.23f,size.height*.66f))
            } else {
                repeat(3) { i ->
                    val x=size.width*(.07f+i*.28f); val y=size.height*(.76f-i*.17f)
                    drawRoundRect(Color(0xFF81C6E1), Offset(x,y), Size(size.width*.25f, 19.dp.toPx()), CornerRadius(7.dp.toPx()))
                    drawRoundRect(Color.White, Offset(x,y), Size(size.width*.25f, 7.dp.toPx()), CornerRadius(4.dp.toPx()))
                }
            }
        }
        YamoneMascotIcon(if(kind == GameIconKind.SUDOKU || kind == GameIconKind.FISH_MUNCH) YamoneMascot.SEAL else YamoneMascot.BEAR,
            Modifier.padding(end=5.dp,bottom=3.dp), size=102.dp, accent=yamonePrimary(themeMode))
    }
}

@Composable
internal fun V3SoundSettings() {
    var state by remember { mutableStateOf(GameFeedback.options) }
    fun update(next: FeedbackOptions) { state = next; GameFeedback.update(next) }
    var preview by remember { mutableStateOf("home") }
    DisposableEffect(Unit) { onDispose { GameFeedback.setScene("home") } }
    Text("소리와 진동", fontSize=19.sp, fontWeight=FontWeight.Black, color=YamoneInk)
    Surface(shape=RoundedCornerShape(24.dp),color=Color.White) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement=Arrangement.spacedBy(5.dp)) {
            V3Switch("전체 소리", "게임 음악과 효과음을 한 번에",state.sound) { update(state.copy(sound=it)) }
            V3Switch("배경음악", "홈과 게임마다 다른 오프라인 음악",state.music) { update(state.copy(music=it)) }
            V3Switch("효과음", "획득 · 오답 · 성공 · 신기록",state.effects) { update(state.copy(effects=it)) }
            V3Switch("버튼 소리", "화면을 누를 때 짧고 가볍게",state.buttons) { update(state.copy(buttons=it)) }
            V3Slider("전체 음량",state.masterVolume,state.sound) { update(state.copy(masterVolume=it)) }
            V3Slider("배경음악 음량",state.musicVolume,state.sound && state.music) { update(state.copy(musicVolume=it)) }
            V3Slider("효과음 음량",state.effectVolume,state.sound && state.effects) { update(state.copy(effectVolume=it)) }
            V3Switch("무음 모드 따르기", "기기가 무음·진동 모드이면 소리도 꺼요",state.followSilent) { update(state.copy(followSilent=it)) }
            Text("배경음악 미리 듣기",color=YamoneInk,fontSize=13.sp,fontWeight=FontWeight.Bold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("home" to "홈", "sudoku" to "스도쿠", "snow" to "눈덩이", "fish" to "물고기", "ice" to "빙하").forEach { (id,label) ->
                    Surface(onClick={ preview=id; GameFeedback.tap(); GameFeedback.setScene(id) }, enabled=state.sound && state.music,
                        color=if(preview==id) Color(0xFFDDF5EC) else V3Background,shape=RoundedCornerShape(12.dp)) {
                        Text(label,Modifier.padding(horizontal=12.dp,vertical=11.dp),fontSize=12.sp,color=YamoneInk)
                    }
                }
            }
        }
    }
    Surface(shape=RoundedCornerShape(24.dp),color=Color.White) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            V3Switch("진동", "지원 기기에서 시스템 진동 설정을 따라요",state.vibration) { update(state.copy(vibration=it)) }
            V3Switch("진동을 더 또렷하게", "끄면 가벼운 진동만 사용해요",state.strongVibration) { update(state.copy(strongVibration=it)) }
            V3Switch("움직임 줄이기", "배경 장식과 축하 효과를 줄여요",state.reduceMotion) { update(state.copy(reduceMotion=it)) }
        }
    }
}

@Composable
private fun V3Switch(title: String, subtitle: String, checked: Boolean, onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end=12.dp)) {
            Text(title,color=YamoneInk,fontWeight=FontWeight.Bold,fontSize=14.sp)
            Text(subtitle,color=YamoneMuted,fontSize=11.sp,lineHeight=16.sp)
        }
        Switch(checked=checked,onCheckedChange=onChange)
    }
}
@Composable
private fun V3Slider(title:String,value:Float,enabled:Boolean,onChange:(Float)->Unit) {
    Column {
        Row { Text(title,Modifier.weight(1f),color=YamoneMuted,fontSize=12.sp); Text("${(value*100).toInt()}%",color=YamoneInk,fontSize=12.sp) }
        Slider(value=value,onValueChange=onChange,enabled=enabled,modifier=Modifier.fillMaxWidth())
    }
}

@Composable
internal fun V3RankingScreen(themeMode: YamoneThemeMode, repository: OnlineRankingRepository) {
    val context = LocalContext.current
    var online by rememberSaveable { mutableStateOf(false) }
    var difficulty by rememberSaveable { mutableIntStateOf(1) }
    var onlineGame by rememberSaveable { mutableIntStateOf(0) }
    var revision by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<OnlineRankingLoadResult?>(null) }
    var syncText by remember { mutableStateOf(repository.status()) }

    val sudokuBoards = remember { RankingBoard.entries.filter { it.minimumWins } }
    val sudokuBoard = sudokuBoards[difficulty.coerceIn(0, sudokuBoards.lastIndex)]
    val onlineBoards = remember { listOf(RankingBoard.SNOW, RankingBoard.FISH, RankingBoard.ICE) }
    val onlineLabels = remember { listOf("눈덩이 러시", "물고기 냠냠", "빙하 점프") }
    val board = onlineBoards[onlineGame.coerceIn(0, onlineBoards.lastIndex)]

    LaunchedEffect(online, board, revision) {
        result = null
        RankingSyncScheduler.schedule(context)
        if (online) result = repository.load(board)
    }
    LaunchedEffect(Unit) {
        while (true) {
            syncText = repository.status()
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("우리의 기록", fontSize=25.sp, fontWeight=FontWeight.Black, color=YamoneInk)

        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(false to "내 기록", true to "온라인 순위").forEach { (value,label) ->
                Surface(
                    onClick={GameFeedback.tap(); online=value},
                    modifier=Modifier.weight(1f),
                    shape=RoundedCornerShape(16.dp),
                    color=if(online==value) yamonePrimaryDark(themeMode) else Color.White
                ) {
                    Text(
                        label,
                        Modifier.padding(13.dp),
                        textAlign=TextAlign.Center,
                        color=if(online==value) Color.White else YamoneInk,
                        fontWeight=FontWeight.Bold
                    )
                }
            }
        }

        if (!online) {
            Text("스도쿠", fontSize=17.sp, fontWeight=FontWeight.Black, color=YamoneInk)
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                sudokuBoards.forEachIndexed { i,entry ->
                    FilterChip(
                        selected=difficulty==i,
                        onClick={ GameFeedback.tap(); difficulty=i },
                        label={Text(entry.label, fontSize=11.sp)}
                    )
                }
            }
            val score = context.getSharedPreferences("yamone_sudoku_game", android.content.Context.MODE_PRIVATE)
                .getInt("best_${sudokuBoard.modeId}", 0).takeIf { it>0 }
            if (score == null) {
                V3Message("첫 기록을 기다리고 있어요.\n즐겁게 한 판 도전해봐요!")
            } else {
                V3RecordRow(
                    AppPreferences(context).nickname(),
                    "${sudokuBoard.label} 최고기록",
                    sudokuBoard.format(score),
                    true
                )
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                onlineLabels.forEachIndexed { i,label ->
                    Surface(
                        onClick={ GameFeedback.tap(); onlineGame=i },
                        modifier=Modifier.weight(1f),
                        shape=RoundedCornerShape(14.dp),
                        color=if(i==onlineGame) yamonePrimarySoft(themeMode) else Color.White
                    ) {
                        Text(
                            label,
                            Modifier.padding(horizontal=4.dp, vertical=12.dp),
                            textAlign=TextAlign.Center,
                            fontSize=11.sp,
                            color=if(i==onlineGame) yamonePrimaryDark(themeMode) else YamoneInk,
                            fontWeight=FontWeight.Bold,
                            maxLines=1
                        )
                    }
                }
            }

            when(val loaded=result) {
                null -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    V3Message("순위를 불러오는 중이에요…")
                }
                OnlineRankingLoadResult.Disabled -> V3Message("이 순위는 현재 제공하지 않아요.")
                OnlineRankingLoadResult.Offline -> V3Message("오프라인이에요. 기록은 기기에 보관하고 온라인이 되면 자동 전송해요.")
                OnlineRankingLoadResult.ServerUpdateRequired -> V3Message("새 기록 단위를 지원하는 서버 업데이트가 필요해요. 기록은 지우지 않고 전송 대기 중이에요.")
                OnlineRankingLoadResult.ServerUnavailable -> V3Message("서버에 연결하지 못했어요. 저장된 최고기록은 자동으로 다시 전송해요.")
                is OnlineRankingLoadResult.Success -> {
                    val data=loaded.data
                    data.me?.let { me ->
                        V3RecordRow("내 순위 ${me.rank}위", "전체 ${data.totalPlayers}명", board.format(me.score), true)
                    }
                    if(data.top.isEmpty()) V3Message("아직 등록된 최고기록이 없어요.")
                    data.top.forEach { row ->
                        V3RecordRow(
                            "${row.rank}위  ${row.nickname}",
                            if(row.isMe) "내 기록" else if(row.rank<=3) "★ TOP 3" else "",
                            board.format(row.score),
                            row.isMe || row.rank<=3
                        )
                    }
                    if(data.me != null && data.top.none { it.rank == data.me.rank }) {
                        Text("내 주변 순위", fontWeight=FontWeight.Bold, color=YamoneInk)
                        data.nearby.forEach { row ->
                            V3RecordRow(
                                "${row.rank}위  ${row.nickname}",
                                if(row.isMe) "내 기록" else "",
                                board.format(row.score),
                                row.isMe
                            )
                        }
                    }
                }
            }
            Text(syncText, color=YamoneMuted, fontSize=11.sp)
            TextButton(onClick={revision++}) { Text("새로고침") }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun V3Message(text:String) {
    Surface(color=Color.White,shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth()) {
        Text(text,Modifier.padding(22.dp),fontSize=13.sp,lineHeight=21.sp,color=YamoneMuted)
    }
}
@Composable
private fun V3RecordRow(title:String,subtitle:String,value:String,highlight:Boolean) {
    Surface(color=if(highlight) Color(0xFFE0F4ED) else Color.White,shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title,fontSize=14.sp,fontWeight=FontWeight.Bold,color=YamoneInk,maxLines=1,overflow=TextOverflow.Ellipsis)
                if(subtitle.isNotBlank()) Text(subtitle,fontSize=11.sp,color=YamoneMuted)
            }
            Text(value,fontSize=14.sp,fontWeight=FontWeight.Black,color=Color(0xFF126F61))
        }
    }
}

@Composable
internal fun V3DataSettings(
    themeMode: YamoneThemeMode,
    repository: OnlineRankingRepository
) {
    val context = LocalContext.current
    var show by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<ArcadeGameId>>(emptySet()) }
    var sudoku by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Text(
        "기기 기록 관리",
        fontSize = 19.sp,
        fontWeight = FontWeight.Black,
        color = YamoneInk
    )

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 1.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = Color(0xFFFFEEF2)
                ) {
                    Text(
                        "⌫",
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black,
                        color = YamoneError
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "내 기록 정리",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = YamoneInk
                    )
                    Text(
                        "원하는 게임 기록만 골라서 삭제해요",
                        fontSize = 11.sp,
                        color = YamoneMuted
                    )
                }
            }

            Text(
                "현재 진행 중인 스도쿠와 닉네임·광고 권한은 그대로 유지돼요. 삭제한 게임의 전송 대기 기록도 함께 정리해 다시 올라가는 일을 막아요.",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = YamoneMuted
            )

            OutlinedButton(
                onClick = {
                    selected = emptySet()
                    sudoku = false
                    message = null
                    show = true
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    yamonePrimaryLine(themeMode)
                )
            ) {
                Text(
                    "기기 기록 선택 삭제",
                    fontWeight = FontWeight.Bold,
                    color = YamoneError
                )
            }

            message?.let {
                Text(
                    it,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = yamonePrimaryDark(themeMode)
                )
            }
        }
    }

    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            shape = RoundedCornerShape(26.dp),
            title = {
                Text(
                    "기기 기록 삭제",
                    fontWeight = FontWeight.Black,
                    color = YamoneInk
                )
            },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        "삭제할 게임을 선택해요.",
                        fontSize = 12.sp,
                        color = YamoneMuted
                    )

                    @Composable
                    fun ChoiceRow(
                        title: String,
                        checked: Boolean,
                        onChecked: (Boolean) -> Unit
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onChecked(!checked) },
                            shape = RoundedCornerShape(15.dp),
                            color = if (checked) yamonePrimarySoft(themeMode) else Color(0xFFF7FAF9)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = onChecked
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = YamoneInk
                                )
                            }
                        }
                    }

                    ChoiceRow("스도쿠", sudoku) { sudoku = it }

                    ArcadeRecordStorage.ACTIVE_GAMES.forEach { game ->
                        ChoiceRow(
                            arcadeGameTitle(game),
                            game in selected
                        ) { checked ->
                            selected = if (checked) selected + game else selected - game
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFFFF7F8)
                    ) {
                        Text(
                            "이 버튼은 기기 기록만 삭제해요. 서버 기록까지 삭제하려면 위의 ‘온라인 최고기록 삭제’를 사용해 주세요.",
                            modifier = Modifier.padding(11.dp),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = Color(0xFF8D6670)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = sudoku || selected.isNotEmpty(),
                    onClick = {
                        val storage = ArcadeRecordStorage(context)
                        val deleted = storage.deleteSelected(selected)

                        val boards = selected.mapNotNull(RankingBoard::forGame).toSet()
                        repository.discardLocalBoards(boards)

                        var sudokuDeleted = true
                        if (sudoku) {
                            val prefs = context.getSharedPreferences(
                                "yamone_sudoku_game",
                                android.content.Context.MODE_PRIVATE
                            )
                            val keys = prefs.all.keys.filter {
                                it.startsWith("best_") ||
                                    it.startsWith("completed_") ||
                                    it.startsWith("recent_") ||
                                    it in setOf(
                                        "total_completed",
                                        "total_mistakes",
                                        "current_streak",
                                        "last_completed_day"
                                    )
                            }
                            val editor = prefs.edit()
                            keys.forEach { editor.remove(it) }
                            sudokuDeleted = editor.commit()
                        }

                        message = if (deleted && sudokuDeleted) {
                            "선택한 기기 기록을 삭제했어요."
                        } else {
                            "일부 기록을 지우지 못했어요. 다시 시도해 주세요."
                        }
                        show = false
                    }
                ) {
                    Text(
                        "선택 삭제",
                        fontWeight = FontWeight.Bold,
                        color = YamoneError
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { show = false }) {
                    Text("취소", color = YamoneMuted)
                }
            }
        )
    }
}

internal fun v3Time(seconds:Int):String {
    val value=seconds.coerceAtLeast(0)
    return if(value>=3600) "%d:%02d:%02d".format(value/3600,value/60%60,value%60) else "%d:%02d".format(value/60,value%60)
}
