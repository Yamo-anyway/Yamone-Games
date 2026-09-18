package com.yamone.games

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
internal fun AutomaticRankingSettings(
    theme: YamoneThemeMode,
    repository: OnlineRankingRepository
) {
    var status by remember { mutableStateOf(repository.status()) }

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
                "게임별 최고기록 하나만 온라인 순위에 유지해요. 오프라인에서 만든 기록도 인터넷이 연결되면 자동으로 전송해요.",
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
                "기기에서 기록 데이터를 삭제해도 서버의 온라인 순위 기록은 유지돼요.",
                fontSize = 10.sp,
                lineHeight = 15.sp,
                color = YamoneMuted
            )
        }
    }
}
