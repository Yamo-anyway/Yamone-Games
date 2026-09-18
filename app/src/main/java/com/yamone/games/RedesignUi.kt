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
                        GameIconKind.ICE_JUMP -> "최고 " + arcadeScoreText(ArcadeGameId.ICE_JUMP, arcadeRecords[ArcadeGameId.ICE_JUMP]?.firstOrNull()?.score ?: 0)
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

private data class V3RankingGame(val label:String,val game:ArcadeGameId)
private val onlineRankingGames = listOf(
    V3RankingGame("눈덩이 러시",ArcadeGameId.SNOW_RUSH),
    V3RankingGame("물고기 냠냠",ArcadeGameId.FISH_MUNCH),
    V3RankingGame("빙하 점프",ArcadeGameId.ICE_JUMP)
)

@Composable
internal fun V3RankingScreen(themeMode:YamoneThemeMode,repository:OnlineRankingRepository) {
    val context=LocalContext.current
    var online by rememberSaveable { mutableStateOf(false) }
    var selectedOnline by rememberSaveable { mutableIntStateOf(0) }
    var revision by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<OnlineRankingLoadResult?>(null) }
    val item=onlineRankingGames[selectedOnline.coerceIn(0,onlineRankingGames.lastIndex)]

    LaunchedEffect(online,selectedOnline,revision) {
        result=null
        if(online) {
            repository.flushPending()
            result=repository.load(item.game)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)
    ) {
        Text("우리의 기록",fontSize=25.sp,fontWeight=FontWeight.Black,color=YamoneInk)

        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(false to "내 기록",true to "온라인 순위").forEach { (value,label) ->
                Surface(
                    onClick={ GameFeedback.tap(); online=value },
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

        if(!online) {
            Text("스도쿠",fontSize=17.sp,fontWeight=FontWeight.Black,color=YamoneInk)
            val stats=remember(revision) { GameStorage(context).stats() }
            stats.difficultyStats.forEach { stat ->
                V3RecordRow(
                    stat.difficulty.label,
                    "완성 ${stat.completed}판",
                    stat.bestSeconds?.let(::v3Time) ?: "아직 기록 없음",
                    true
                )
            }
        } else {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(6.dp)
            ) {
                onlineRankingGames.forEachIndexed { i,g ->
                    Surface(
                        onClick={GameFeedback.tap();selectedOnline=i},
                        shape=RoundedCornerShape(14.dp),
                        color=if(i==selectedOnline) yamonePrimarySoft(themeMode) else Color.White
                    ) {
                        Text(
                            g.label,
                            Modifier.padding(horizontal=13.dp,vertical=12.dp),
                            fontSize=12.sp,
                            color=if(i==selectedOnline) yamonePrimaryDark(themeMode) else YamoneMuted,
                            fontWeight=FontWeight.Bold
                        )
                    }
                }
            }

            when(val loaded=result) {
                null -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    V3Message("순위를 불러오는 중이에요…")
                }
                OnlineRankingLoadResult.Disabled -> V3Message("온라인 순위를 준비하고 있어요.")
                OnlineRankingLoadResult.Offline -> V3Message("지금은 오프라인이에요. 연결되면 최고기록을 자동으로 전송해요.")
                OnlineRankingLoadResult.ServerUpdateRequired -> V3Message("순위 서버 업데이트가 필요해요.")
                OnlineRankingLoadResult.ServerUnavailable -> V3Message("순위 서버에 연결하지 못했어요. 기록은 기기에 안전하게 남아 있어요.")
                is OnlineRankingLoadResult.Success -> {
                    val data=loaded.data
                    data.me?.let { me ->
                        V3RecordRow(
                            "내 순위 ${me.rank}위",
                            "전체 ${data.totalPlayers}명",
                            arcadeScoreText(item.game,me.score),
                            true
                        )
                    }
                    if(data.top.isEmpty()) {
                        V3Message("아직 등록된 순위가 없어요.")
                    } else {
                        Text("TOP ${data.top.size}",fontSize=16.sp,fontWeight=FontWeight.Black,color=YamoneInk)
                        data.top.forEach { row ->
                            V3RecordRow(
                                "${row.rank}위  ${row.nickname}",
                                if(row.isMe) "내 기록" else if(row.rank<=3) "★ TOP 3" else "",
                                arcadeScoreText(item.game,row.score),
                                row.isMe || row.rank<=3
                            )
                        }
                        if(data.me!=null && data.top.none { it.rank==data.me.rank }) {
                            Text("내 주변 순위",fontWeight=FontWeight.Bold,color=YamoneInk)
                            data.nearby.forEach { row ->
                                V3RecordRow(
                                    "${row.rank}위  ${row.nickname}",
                                    if(row.isMe) "내 기록" else "",
                                    arcadeScoreText(item.game,row.score),
                                    row.isMe
                                )
                            }
                        }
                    }
                }
            }

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
internal fun V3DataSettings() {
    val context=LocalContext.current
    var show by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<ArcadeGameId>>(emptySet()) }
    var sudoku by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    Text("기기 기록 관리",fontSize=19.sp,fontWeight=FontWeight.Black,color=YamoneInk)
    Surface(color=Color.White,shape=RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("체크한 게임의 기록만 삭제해요.\n진행 중인 스도쿠와 닉네임·광고 권한은 유지돼요.",fontSize=12.sp,color=YamoneMuted)
            TextButton(onClick={selected=emptySet();sudoku=false;show=true}) { Text("기기 기록 선택 삭제",color=YamoneError) }
            message?.let { Text(it,fontSize=12.sp,color=YamoneMuted) }
        }
    }
    if(show) AlertDialog(onDismissRequest={show=false},title={Text("삭제할 게임을 체크해요")},text={
        Column {
            Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(sudoku,{sudoku=it});Text("스도쿠 완성 기록",fontSize=13.sp) }
            ArcadeGameId.entries.filter { it != ArcadeGameId.FISH_MUNCH_TIME_ATTACK }.forEach { game ->
                Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(game in selected,{checked->selected=if(checked) selected+game else selected-game});Text(arcadeGameTitle(game),fontSize=13.sp) }
            }
            Text("기기 기록만 삭제해요. 온라인 기록은 ‘온라인 기록 선택 삭제’에서 별도로 관리해요.",fontSize=11.sp,color=YamoneMuted)
        }
    },confirmButton={TextButton(enabled=sudoku || selected.isNotEmpty(),onClick={
        ArcadeRecordStorage(context).deleteSelected(selected)
        if(sudoku) {
            val prefs=context.getSharedPreferences("yamone_sudoku_game",android.content.Context.MODE_PRIVATE)
            val keys=prefs.all.keys.filter { it.startsWith("best_") || it.startsWith("completed_") || it.startsWith("recent_") || it in setOf("total_completed","total_mistakes","current_streak","last_completed_day") }
            prefs.edit().also { edit -> keys.forEach { edit.remove(it) } }.apply()
        }
        message="선택한 기기 기록을 삭제했어요.";show=false
    }) {Text("선택 기록 삭제",color=YamoneError)}},dismissButton={TextButton(onClick={show=false}) {Text("취소")}})
}

internal fun v3Time(seconds:Int):String {
    val value=seconds.coerceAtLeast(0)
    return if(value>=3600) "%d:%02d:%02d".format(value/3600,value/60%60,value%60) else "%d:%02d".format(value/60,value%60)
}
