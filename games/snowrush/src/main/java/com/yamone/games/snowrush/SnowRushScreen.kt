package com.yamone.games.snowrush

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamone.games.arcadecore.*
import kotlinx.coroutines.isActive

@Composable
fun SnowRushScreen(
    onBack: () -> Unit, nickname: String, playerHalfWidth: Float, playerHalfHeight: Float,
    primary: Color, primaryDark: Color, soft: Color, ink: Color, muted: Color,
    mascotContent: @Composable (Dp) -> Unit
) {
    val context = LocalContext.current.applicationContext
    val storage = remember { ArcadeRecordStorage(context) }
    val engine = remember { SnowRushEngine() }
    var revision by remember { mutableLongStateOf(0L) }
    var started by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var exitConfirm by remember { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(storage.topRecords(ArcadeGameId.SNOW_RUSH).firstOrNull()?.score ?: 0) }
    var previousBest by remember { mutableIntStateOf(best) }
    var saved by remember { mutableStateOf(false) }
    val objects = remember(revision) { engine.visuals() }
    val gameOver = engine.gameOver
    val elapsed = engine.elapsedMillis

    fun pause() { paused = true; GameFeedback.setPaused(true) }
    fun resume() { GameFeedback.setPaused(false); paused = false; GameFeedback.tap() }
    fun exit() { if (!started || gameOver) onBack() else { exitConfirm = true; GameFeedback.setPaused(true) } }
    fun restart() {
        engine.restart(); saved = false; previousBest = best; started = true
        paused = false; exitConfirm = false; GameFeedback.setPaused(false)
        GameFeedback.play("start"); revision++
    }
    BackHandler { if (paused) resume() else exit() }
    DisposableEffect(Unit) { onDispose { GameFeedback.setPaused(false) } }
    LaunchedEffect(Unit) {
        var previous = 0L
        var wasActive = false
        var previousDodged = 0
        while (isActive) withFrameNanos { now ->
            val active = started && !paused && !exitConfirm && !engine.gameOver && GameFeedback.canAdvance
            if (active && wasActive && previous != 0L) {
                val delta = now - previous
                if (delta > 250_000_000L) {
                    // A stalled/background frame must not instantly move an obstacle through the player.
                    pause()
                } else {
                    engine.advance(delta)
                    if (engine.dodged > previousDodged) GameFeedback.play("collect")
                    previousDodged = engine.dodged
                    revision++
                }
            } else if (wasActive && started && !engine.gameOver && !paused && !exitConfirm && !GameFeedback.canAdvance) {
                pause()
            }
            wasActive = active && !paused
            previous = now
        }
    }
    LaunchedEffect(gameOver) {
        if (gameOver && !saved) {
            storage.addRecord(ArcadeGameId.SNOW_RUSH, elapsed, nickname = nickname)
            best = storage.topRecords(ArcadeGameId.SNOW_RUSH).firstOrNull()?.score ?: elapsed
            saved = true
            GameFeedback.play(if (elapsed > previousBest) "record" else "finish")
            GameFeedback.setPaused(true)
        }
    }
    Column(Modifier.fillMaxSize().background(soft)) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal=12.dp), verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=::exit,modifier=Modifier.semantics { contentDescription="뒤로가기" }) {
                Canvas(Modifier.size(26.dp)) {
                    val p=Offset(size.width*.2f,size.height*.5f)
                    drawLine(primaryDark,Offset(size.width*.82f,p.y),p,3.dp.toPx(),StrokeCap.Round)
                    drawLine(primaryDark,p,Offset(size.width*.49f,size.height*.21f),3.dp.toPx(),StrokeCap.Round)
                    drawLine(primaryDark,p,Offset(size.width*.49f,size.height*.79f),3.dp.toPx(),StrokeCap.Round)
                }
            }
            Text("눈덩이 러시",color=ink,fontWeight=FontWeight.Black,fontSize=20.sp,modifier=Modifier.weight(1f))
            IconButton(onClick={GameFeedback.tap();pause()},enabled=started && !gameOver,
                modifier=Modifier.semantics { contentDescription="일시정지" }) { Text("Ⅱ",fontSize=26.sp,color=primaryDark) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=5.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TimeChip("생존 시간",preciseDuration(elapsed),Modifier.weight(1.2f),primaryDark)
            TimeChip("최고 기록",preciseDuration(best),Modifier.weight(1.2f),primaryDark)
            TimeChip("단계",engine.difficulty.toString(),Modifier.weight(.6f),primaryDark)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal=8.dp,vertical=6.dp)
            .clip(RoundedCornerShape(24.dp)).semantics {
                contentDescription="눈덩이 경기장"
                stateDescription="${preciseDuration(elapsed)}, 눈덩이 ${objects.count { !it.fragment }}개, 파편 ${objects.count { it.fragment }}개"
            }
            .pointerInput(started,paused,exitConfirm,gameOver) {
                detectDragGestures { change, amount ->
                    if (started && !paused && !exitConfirm && !engine.gameOver && GameFeedback.canAdvance && size.width>0) {
                        change.consume(); engine.moveBy(amount.x/size.width); revision++
                    }
                }
            }) {
            val playerSize=58.dp
            SideEffect { engine.configureViewport(maxWidth.value, maxHeight.value, playerSize.value) }
            SnowPaintedBackdrop(Modifier.matchParentSize())
            Canvas(Modifier.matchParentSize().semantics { contentDescription="좌우 이동 경계" }) {
                paintDodgeBounds()
            }
            Canvas(Modifier.matchParentSize()) {
                objects.forEach { h ->
                    paintSnow304Hazard(Offset(size.width*h.x,size.height*h.y),size.width*h.radius,h.rotation,h.id,h.fragment)
                }
                if(started && engine.protected) {
                    drawCircle(Color(0xFFAEFFF0).copy(alpha=.22f),36.dp.toPx(),Offset(size.width*engine.playerX.toFloat(),size.height*SnowRushEngine.PLAYER_Y.toFloat()))
                    drawCircle(Color(0xFFC2FFF1).copy(alpha=.92f),34.dp.toPx(),Offset(size.width*engine.playerX.toFloat(),size.height*SnowRushEngine.PLAYER_Y.toFloat()),style=Stroke(2.dp.toPx()))
                }
            }
            Box(Modifier.offset(x=maxWidth*engine.playerX.toFloat()-playerSize/2,y=maxHeight*SnowRushEngine.PLAYER_Y.toFloat()-playerSize/2).semantics { contentDescription="눈덩이 플레이어" }) { mascotContent(playerSize) }
            if(started && !gameOver) {
                Surface(Modifier.align(Alignment.TopCenter).padding(10.dp),color=Color(0xFF163E67).copy(alpha=.70f),shape=RoundedCornerShape(12.dp)) {
                    Text(if(engine.protected) "시작 보호 · 좌우로 움직여봐요" else "작은 파편은 더 천천히 내려와요",Modifier.padding(horizontal=12.dp,vertical=7.dp),fontSize=11.sp,color=Color.White)
                }
            }
            if(!started) {
                Surface(Modifier.align(Alignment.Center).padding(26.dp),shape=RoundedCornerShape(25.dp),color=Color.White.copy(alpha=.97f)) {
                    Column(Modifier.padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text("눈덩이와 파편을 피해요!",fontSize=18.sp,fontWeight=FontWeight.Black,color=ink)
                        Row(horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.CenterVertically) {
                            HazardLegend(false, "눈덩이", ink)
                            HazardLegend(true, "파편", ink)
                        }
                        Text("양쪽 경계 안에서 좌우로 움직여요.\n시작 후 3초는 보호받아요.\n파편마다 느린 속도와 퍼지는 거리가 달라요.",fontSize=12.sp,lineHeight=20.sp,color=muted,textAlign=TextAlign.Center)
                        Button(onClick=::restart,modifier=Modifier.fillMaxWidth().height(48.dp),shape=RoundedCornerShape(16.dp),colors=ButtonDefaults.buttonColors(containerColor=primary)) {Text("시작하기",fontWeight=FontWeight.Bold)}
                    }
                }
            }
            if(gameOver) {
                Surface(Modifier.align(Alignment.Center).padding(24.dp),shape=RoundedCornerShape(27.dp),color=Color.White) {
                    Column(Modifier.padding(23.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(9.dp)) {
                        mascotContent(70.dp)
                        Text(if(elapsed>previousBest) "새로운 최고기록!" else "이번에도 멋진 도전!",fontWeight=FontWeight.Black,fontSize=21.sp,color=ink)
                        Text(preciseDuration(elapsed),fontWeight=FontWeight.Black,fontSize=30.sp,color=primaryDark)
                        Text("최고 ${preciseDuration(best)} · ${engine.difficulty}단계",fontSize=12.sp,color=muted)
                        Text(if(engine.deathCause=="완주") "최대 생존시간에 도달했어요!" else "${engine.deathCause}에 닿았어요",fontSize=12.sp,color=muted)
                        if(elapsed<previousBest) Text("최고까지 ${preciseDuration(previousBest-elapsed)}",fontSize=12.sp,color=primaryDark)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Button(onClick=::restart,modifier=Modifier.weight(1f),shape=RoundedCornerShape(15.dp)) {Text("다시하기",fontSize=12.sp)}
                            FilledTonalButton(onClick=onBack,modifier=Modifier.weight(1f),shape=RoundedCornerShape(15.dp)) {Text("그만하기",fontSize=12.sp)}
                        }
                    }
                }
            }
        }
    }
    if(paused) AlertDialog(onDismissRequest=::resume,shape=RoundedCornerShape(26.dp),title={Text("잠깐 쉬어가요",fontWeight=FontWeight.Black)},text={
        Column {
            Text(preciseDuration(elapsed), fontSize=26.sp, fontWeight=FontWeight.Black, color=primaryDark)
            Spacer(Modifier.height(8.dp))
            Text("게임과 생존 시간은 멈춰 있어요.")
            var opts by remember { mutableStateOf(GameFeedback.options) }
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("배경음악",Modifier.weight(1f));Switch(opts.music,{opts=opts.copy(music=it);GameFeedback.update(opts)})
            }
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("진동",Modifier.weight(1f));Switch(opts.vibration,{opts=opts.copy(vibration=it);GameFeedback.update(opts)})
            }
        }
    },confirmButton={Button(onClick=::resume){Text("계속하기")}},dismissButton={TextButton(onClick={paused=false;exitConfirm=true}){Text("게임 종료")}})
    if(exitConfirm) AlertDialog(onDismissRequest={exitConfirm=false;GameFeedback.setPaused(false)},title={Text("게임을 그만둘까요?")},text={Text("진행 중인 기록은 순위에 등록하지 않아요.")},
        confirmButton={TextButton(onClick=onBack){Text("게임 종료")}},dismissButton={TextButton(onClick={exitConfirm=false;GameFeedback.setPaused(false)}){Text("계속하기")}})
}

@Composable
private fun TimeChip(label:String,value:String,modifier:Modifier,dark:Color) {
    Surface(modifier,shape=RoundedCornerShape(17.dp),color=Color.White) {
        Column(Modifier.padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text(value,fontSize=14.sp,fontWeight=FontWeight.Black,color=dark,maxLines=1)
            Text(label,fontSize=10.sp,color=dark.copy(alpha=.65f))
        }
    }
}

@Composable
private fun HazardLegend(fragment: Boolean, text: String, ink: Color) {
    Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        Canvas(Modifier.size(28.dp)) {
            paintSnow304Hazard(Offset(size.width*.5f,size.height*.46f),size.width*(if(fragment) .32f else .38f),0f,1,fragment)
        }
        Text(text,fontSize=11.sp,color=ink)
    }
}
