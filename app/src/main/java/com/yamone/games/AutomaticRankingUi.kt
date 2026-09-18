package com.yamone.games

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamone.games.sudoku.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun AutomaticRankingSettings(
    theme: YamoneThemeMode,
    repository: OnlineRankingRepository
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(repository.status()) }
    var showDelete by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<RankingBoard>>(emptySet()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val boards = remember {
        listOf(
            RankingBoard.SNOW,
            RankingBoard.FISH,
            RankingBoard.ICE
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            status = repository.status()
            delay(1000)
        }
    }

    Text(
        "온라인 순위",
        fontSize = 18.sp,
        fontWeight = FontWeight.ExtraBold,
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
                    color = yamonePrimarySoft(theme)
                ) {
                    Text(
                        "★",
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = yamonePrimaryDark(theme)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "최고기록 자동 전송",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = YamoneInk
                    )
                    Text(
                        "눈덩이 러시 · 물고기 냠냠 · 빙하 점프",
                        fontSize = 11.sp,
                        color = YamoneMuted
                    )
                }
            }

            Text(
                "게임별 최고기록 하나만 서버에 유지해요. 오프라인에서 만든 기록도 인터넷이 연결되면 자동으로 전송해요.",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = YamoneMuted
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = yamonePrimarySoft(theme)
            ) {
                Text(
                    status,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = yamonePrimaryDark(theme)
                )
            }

            Text(
                "랭킹 식별값은 같은 기기·같은 앱 서명에서 재설치해도 동일하게 유지되도록 앱 안에서 생성하며, 위치 정보는 사용하지 않아요.",
                fontSize = 10.sp,
                lineHeight = 15.sp,
                color = YamoneMuted
            )

            OutlinedButton(
                onClick = {
                    selected = emptySet()
                    message = null
                    showDelete = true
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    yamonePrimaryLine(theme)
                )
            ) {
                Text(
                    "온라인 최고기록 삭제",
                    fontWeight = FontWeight.Bold,
                    color = yamonePrimaryDark(theme)
                )
            }

            message?.let {
                Text(it, fontSize = 11.sp, color = YamoneMuted)
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { if (!busy) showDelete = false },
            shape = RoundedCornerShape(26.dp),
            title = {
                Text(
                    "온라인 기록 삭제",
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

                    boards.forEach { board ->
                        val checked = board in selected
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !busy) {
                                    selected = if (checked) selected - board else selected + board
                                },
                            shape = RoundedCornerShape(15.dp),
                            color = if (checked) yamonePrimarySoft(theme) else Color(0xFFF7FAF9)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    enabled = !busy,
                                    onCheckedChange = { value ->
                                        selected = if (value) selected + board else selected - board
                                    }
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    when (board) {
                                        RankingBoard.SNOW -> "눈덩이 러시"
                                        RankingBoard.FISH -> "물고기 냠냠"
                                        RankingBoard.ICE -> "빙하 점프"
                                        else -> board.label
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = YamoneInk
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFFFF3F4)
                    ) {
                        Text(
                            "온라인 기록을 삭제하면 같은 게임의 기기 최고기록도 함께 삭제해요. 그래야 지운 기록이 자동 전송으로 다시 올라가지 않아요.",
                            modifier = Modifier.padding(11.dp),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = Color(0xFF9A5360)
                        )
                    }

                    message?.let {
                        Text(it, color = YamoneError, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selected.isNotEmpty() && !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            when (repository.deleteBoards(selected)) {
                                OnlineRankingDeleteResult.Success -> {
                                    message = "선택한 최고기록을 삭제했어요."
                                    showDelete = false
                                }
                                OnlineRankingDeleteResult.Offline ->
                                    message = "인터넷 연결 후 다시 시도해 주세요. 아직 기록은 삭제하지 않았어요."
                                OnlineRankingDeleteResult.ServerUnavailable ->
                                    message = "서버 삭제를 확인하지 못했어요. 기기 기록은 유지돼요."
                            }
                            busy = false
                        }
                    }
                ) {
                    Text(
                        if (busy) "처리 중…" else "선택 삭제",
                        fontWeight = FontWeight.Bold,
                        color = YamoneError
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { showDelete = false }
                ) {
                    Text("취소", color = YamoneMuted)
                }
            }
        )
    }
}
