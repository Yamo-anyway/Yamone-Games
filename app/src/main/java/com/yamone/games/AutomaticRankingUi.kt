package com.yamone.games

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamone.games.sudoku.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
internal fun AutomaticRankingSettings(theme: YamoneThemeMode, repository: OnlineRankingRepository) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(repository.status()) }
    var showDelete by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<RankingBoard>>(emptySet()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { while (true) { status=repository.status(); delay(1000) } }
    Text("온라인 순위", fontSize=18.sp, fontWeight=FontWeight.ExtraBold, color=YamoneInk)
    Surface(color=Color.White, shape=RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Text("최고기록 자동 전송", fontSize=16.sp, fontWeight=FontWeight.Black, color=YamoneInk)
            Text("기기에는 게임별 최고기록 하나만 남겨요. 스도쿠는 난이도별 최단시간을 저장해요. 오프라인 기록은 온라인이 되면 자동으로 다시 전송해요.", fontSize=12.sp, color=YamoneMuted)
            Text("닉네임·게임·최고기록·기기 지역의 국가코드·설치별 무작위 식별값을 사용해요. 위치 정보는 사용하지 않아요. 실명 대신 별명을 써 주세요.", fontSize=11.sp, color=YamoneMuted)
            Text(status, fontSize=12.sp, color=yamonePrimaryDark(theme))
            TextButton(onClick={selected=emptySet();message=null;showDelete=true}) { Text("최고기록 선택 삭제") }
            message?.let { Text(it, fontSize=12.sp, color=YamoneMuted) }
        }
    }
    if (showDelete) AlertDialog(onDismissRequest={if(!busy)showDelete=false}, title={Text("삭제할 최고기록 선택")}, text={
        Column(Modifier.verticalScroll(rememberScrollState())) {
            RankingBoard.entries.forEach { board ->
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(board in selected, enabled=!busy, onCheckedChange={checked->selected=if(checked)selected+board else selected-board})
                    Text(if(board.minimumWins) "스도쿠 · ${board.label}" else board.label, fontSize=13.sp)
                }
            }
            Text("선택한 서버 기록과 기기 최고기록을 함께 삭제해요. 진행 중인 스도쿠는 유지해요. 다음 플레이에서 새 최고기록이 생기면 다시 전송돼요.", fontSize=11.sp)
            message?.let { Text(it, color=YamoneError, fontSize=11.sp) }
        }
    }, confirmButton={TextButton(enabled=selected.isNotEmpty() && !busy, onClick={
        busy=true
        scope.launch {
            when(repository.deleteBoards(selected)) {
                OnlineRankingDeleteResult.Success -> {message="선택한 최고기록을 삭제했어요.";showDelete=false}
                OnlineRankingDeleteResult.Offline -> message="온라인에서 삭제할 수 있어요. 아직 아무 기록도 삭제하지 않았어요."
                OnlineRankingDeleteResult.ServerUnavailable -> message="서버 삭제를 확인하지 못했어요. 기기 기록은 유지돼요."
            }
            busy=false
        }
    }) {Text(if(busy)"처리 중" else "선택 삭제")}}, dismissButton={TextButton(enabled=!busy,onClick={showDelete=false}) {Text("취소")}})
}
