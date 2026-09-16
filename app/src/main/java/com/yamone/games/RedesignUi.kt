package com.yamone.games

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamone.games.arcadecore.GameSound
import com.yamone.games.arcadecore.LocalGameExperience
import com.yamone.games.sudoku.ui.theme.*

internal fun gameArtwork(kind: GameIconKind) = when (kind) {
    GameIconKind.SUDOKU -> R.drawable.game_art_sudoku
    GameIconKind.ICE_JUMP -> R.drawable.game_art_ice
    GameIconKind.FISH_MUNCH -> R.drawable.game_art_fish
    GameIconKind.SNOW_RUSH -> R.drawable.game_art_snow
}

@Composable
internal fun HomeFriendsHero(themeMode: YamoneThemeMode) {
    Box(Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(26.dp))
        .background(Brush.horizontalGradient(listOf(Color(0xFFD8F4EE), Color(0xFFE3F4F8), Color(0xFFFCE2EC))))) {
        Column(Modifier.align(Alignment.CenterStart).padding(start = 19.dp, end = 135.dp)) {
            Text("오늘도, 즐거운 한 판", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = yamonePrimaryDark(themeMode))
            Spacer(Modifier.height(7.dp))
            Text("작은 도전이 쌓이는\n우리들의 놀이터", fontSize = 12.sp, lineHeight = 18.sp, color = YamoneMuted)
        }
        Image(painterResource(R.drawable.launcher_art), contentDescription = "함께 웃는 아기물범과 아기곰",
            modifier = Modifier.align(Alignment.CenterEnd).size(124.dp).padding(8.dp).clip(RoundedCornerShape(24.dp)), contentScale = ContentScale.Crop)
    }
}

@Composable
internal fun RedesignedGameGrid(games: List<GameListItem>, themeMode: YamoneThemeMode) {
    val experience = LocalGameExperience.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        games.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { game ->
                    val accent = if (game.icon == GameIconKind.SNOW_RUSH || game.icon == GameIconKind.ICE_JUMP) YamonePinkDark else yamonePrimaryDark(themeMode)
                    Surface(onClick = { experience?.play(GameSound.TAP); game.onClick() }, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp), color = Color.White, shadowElevation = 1.dp) {
                        Column {
                            Image(painterResource(gameArtwork(game.icon)), contentDescription = null,
                                modifier = Modifier.fillMaxWidth().aspectRatio(1.5f).padding(7.dp).clip(RoundedCornerShape(19.dp)), contentScale = ContentScale.Crop)
                            Column(Modifier.padding(start = 13.dp, end = 13.dp, bottom = 14.dp)) {
                                Text(game.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = YamoneInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                Text(when (game.icon) {
                                    GameIconKind.SUDOKU -> "천천히, 똑똑한 한 칸"
                                    GameIconKind.ICE_JUMP -> "빙하를 딛고 더 높이!"
                                    GameIconKind.FISH_MUNCH -> "냠냠! 놓치지 마세요"
                                    GameIconKind.SNOW_RUSH -> "커지는 눈덩이를 피해요"
                                }, fontSize = 11.sp, color = YamoneMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(9.dp))
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(game.best.ifBlank { "가볍게 시작하기" }, modifier = Modifier.weight(1f), fontSize = 10.sp, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Surface(shape = RoundedCornerShape(50), color = accent.copy(alpha = .10f)) {
                                        Text("›", modifier = Modifier.padding(horizontal = 10.dp, vertical = 1.dp), fontSize = 21.sp, color = accent)
                                    }
                                }
                            }
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
