package com.yamone.spiritshift.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yamone.spiritshift.ads.AdaptiveBannerAd
import com.yamone.spiritshift.ads.RewardedAdController
import com.yamone.spiritshift.data.AppUiState
import com.yamone.spiritshift.data.AppViewModel
import com.yamone.spiritshift.data.RankingEntry
import com.yamone.spiritshift.game.ElementKind
import com.yamone.spiritshift.game.GamePhase
import com.yamone.spiritshift.game.GameViewModel
import kotlinx.coroutines.launch

private enum class AppScreen { HOME, RANKING, SETTINGS, GAME }

@Composable
fun SpiritShiftApp(
    appViewModel: AppViewModel = viewModel(),
    gameViewModel: GameViewModel = viewModel(),
) {
    val app by appViewModel.ui.collectAsStateWithLifecycle()
    val game by gameViewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()
    val rewarded = remember { RewardedAdController() }
    var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    var nicknameEditor by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        appViewModel.refreshDaily()
        appViewModel.refreshLeaderboard()
        rewarded.load(context)
    }

    LaunchedEffect(game.gameId, game.phase) {
        if (game.gameId > 0L && game.phase == GamePhase.GAME_OVER) {
            appViewModel.recordGame(game.score, game.elapsedMs)
        }
    }

    fun beginGame() {
        if (app.nickname.isBlank()) {
            nicknameEditor = true
            return
        }
        if (appViewModel.consumePlay()) {
            gameViewModel.startNewGame()
            screen = AppScreen.GAME
        } else if (activity != null) {
            rewarded.show(
                activity = activity,
                onEarned = {
                    appViewModel.grantRewardedPlays(5)
                    if (appViewModel.consumePlay()) {
                        gameViewModel.startNewGame()
                        screen = AppScreen.GAME
                    }
                },
                onUnavailable = {
                    Toast.makeText(context, "광고를 불러오는 중이에요. 잠시 후 다시 눌러 주세요.", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        AdaptiveBannerAd()
        if (screen == AppScreen.GAME) {
            GameScreen(
                state = game,
                bestScore = app.bestScore,
                nickname = app.nickname,
                onTapPiece = gameViewModel::onPieceTap,
                onSwipePiece = gameViewModel::onPieceSwipe,
                onPause = { gameViewModel.setPaused(true) },
                onResume = { gameViewModel.setPaused(false) },
                onHome = {
                    gameViewModel.setPaused(true)
                    screen = AppScreen.HOME
                },
                onReplay = { beginGame() },
                onBackground = { gameViewModel.setPaused(true) },
                modifier = Modifier.weight(1f)
            )
        } else {
            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    MainBottomBar(screen) { screen = it }
                }
            ) { padding ->
                when (screen) {
                    AppScreen.HOME -> HomeScreen(app, ::beginGame, Modifier.padding(padding))
                    AppScreen.RANKING -> RankingScreen(app, appViewModel::refreshLeaderboard, Modifier.padding(padding))
                    AppScreen.SETTINGS -> SettingsScreen(app, { nicknameEditor = true }, Modifier.padding(padding))
                    AppScreen.GAME -> Unit
                }
            }
        }
    }

    if (app.nickname.isBlank() || nicknameEditor) {
        NicknameDialog(
            current = app.nickname,
            canDismiss = app.nickname.isNotBlank(),
            serverConfigured = app.serverConfigured,
            onDismiss = { nicknameEditor = false },
            onSubmit = { appViewModel.setNickname(it) }
        )
    }
}

@Composable
private fun MainBottomBar(screen: AppScreen, onSelect: (AppScreen) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = screen == AppScreen.HOME,
            onClick = { onSelect(AppScreen.HOME) },
            icon = { Icon(Icons.Default.Home, "홈") },
            label = { Text("홈") }
        )
        NavigationBarItem(
            selected = screen == AppScreen.RANKING,
            onClick = { onSelect(AppScreen.RANKING) },
            icon = { Icon(Icons.Default.Leaderboard, "랭킹") },
            label = { Text("랭킹") }
        )
        NavigationBarItem(
            selected = screen == AppScreen.SETTINGS,
            onClick = { onSelect(AppScreen.SETTINGS) },
            icon = { Icon(Icons.Default.Settings, "설정") },
            label = { Text("설정") }
        )
    }
}

@Composable
private fun HomeScreen(state: AppUiState, onStart: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("SPIRIT SHIFT", fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text(
                "9×9 정령 매치 · 회전하는 중력 · No Move 종료",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
            )
        }
        item { ElementPreviewRow() }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("최고 기록", fontWeight = FontWeight.Bold)
                    Text(formatNumber(state.bestScore) + "점", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                    if (state.bestTimeMs > 0) Text("플레이 " + formatTime(state.bestTimeMs))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Text("플레이어  " + state.nickname.ifBlank { "닉네임 설정 필요" })
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(22.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("오늘 무료", fontWeight = FontWeight.Bold)
                        Text(state.freeRemaining.toString() + " / 3회", fontSize = 22.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("광고 보상", fontWeight = FontWeight.Bold)
                        Text(state.bonusPlays.toString() + "회", fontSize = 22.sp)
                    }
                }
            }
        }
        item {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(62.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.freeRemaining > 0 || state.bonusPlays > 0) "게임 시작" else "광고 보고 +5회",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("게임 규칙", fontWeight = FontWeight.Bold)
                    Text("• 가로·세로 같은 정령 3개 이상 맞추기")
                    Text("• 자동 셔플 없음 · No Move 즉시 종료")
                    Text("• 2분부터 회전 시작 · 매분 회전 각도 추가")
                    Text("• 8분부터 45° 단위 모든 회전 사용")
                    Text("• 10분부터 최고 난이도 유지")
                }
            }
        }
    }
}

@Composable
private fun ElementPreviewRow() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ElementKind.entries.forEach { kind ->
            val visual = kind.visual()
            Box(
                Modifier.size(42.dp).clip(CircleShape)
                    .background(visual.accent.copy(alpha = 0.22f))
                    .border(1.dp, visual.accent.copy(alpha = 0.85f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painterResource(visual.drawable),
                    visual.label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(3.dp)
                )
            }
        }
    }
}

@Composable
private fun RankingScreen(state: AppUiState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EmojiEvents, null)
                Spacer(Modifier.width(8.dp))
                Text("랭킹", fontSize = 26.sp, fontWeight = FontWeight.Black)
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "새로고침") }
        }

        if (!state.serverConfigured) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text(
                    "이 APK는 랭킹 서버 주소가 비어 있는 테스트 빌드예요. 현재는 내 최고 기록을 표시합니다.",
                    modifier = Modifier.padding(14.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        if (state.rankingLoading) {
            Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.leaderboard.isEmpty()) {
            if (state.bestScore <= 0) {
                Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
                    Text("아직 기록이 없어요.")
                }
            } else {
                RankingRow(RankingEntry(1, state.nickname.ifBlank { "PLAYER" }, state.bestScore, state.bestTimeMs, state.playerId), true)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(state.leaderboard) { entry ->
                    RankingRow(entry, entry.playerId.isNotBlank() && entry.playerId == state.playerId)
                }
            }
        }
    }
}

@Composable
private fun RankingRow(entry: RankingEntry, isMine: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isMine) MaterialTheme.colorScheme.primary.copy(alpha = 0.17f) else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(15.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(entry.rank.toString(), modifier = Modifier.width(34.dp), fontWeight = FontWeight.Bold)
            Text(entry.nickname, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text(formatNumber(entry.score), fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun SettingsScreen(state: AppUiState, onEditNickname: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text("설정", fontSize = 28.sp, fontWeight = FontWeight.Black) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("닉네임", fontWeight = FontWeight.Bold)
                        Text(state.nickname.ifBlank { "미설정" }, fontSize = 22.sp)
                        Text("한글 포함 최대 6자 · 영문/숫자 최대 12자", fontSize = 12.sp)
                    }
                    IconButton(onClick = onEditNickname) { Icon(Icons.Default.Edit, "닉네임 변경") }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("현재 버전", fontWeight = FontWeight.Bold)
                    Text("9×9 · 정령 7종 · 특수 블록 없음")
                    Text("No Move 즉시 종료 · 자동 셔플 없음")
                    Text("음악/효과음은 추후 추가")
                }
            }
        }
        item { Text("Spirit Shift v0.1.0 · 임시 게임명", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f)) }
    }
}

@Composable
private fun NicknameDialog(
    current: String,
    canDismiss: Boolean,
    serverConfigured: Boolean,
    onDismiss: () -> Unit,
    onSubmit: suspend (String) -> Result<Unit>
) {
    var value by remember(current) { mutableStateOf(current) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (canDismiss && !saving) onDismiss() },
        title = { Text(if (current.isBlank()) "닉네임을 정해 주세요" else "닉네임 변경") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; error = null },
                    label = { Text("닉네임") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { if (error != null) Text(error.orEmpty()) }
                )
                Text("한글이 포함되면 최대 6자, 영문+숫자는 최대 12자예요.", fontSize = 12.sp)
                if (!serverConfigured) Text("테스트 빌드에서는 서버 중복 검사가 꺼져 있어요.", fontSize = 11.sp, color = MaterialTheme.colorScheme.tertiary)
            }
        },
        confirmButton = {
            Button(
                enabled = !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        val result = onSubmit(value)
                        saving = false
                        result.onSuccess { onDismiss() }.onFailure { error = it.message ?: "사용할 수 없는 닉네임이에요." }
                    }
                }
            ) { Text(if (saving) "확인 중..." else "사용하기") }
        },
        dismissButton = {
            if (canDismiss) TextButton(enabled = !saving, onClick = onDismiss) { Text("취소") }
        }
    )
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
