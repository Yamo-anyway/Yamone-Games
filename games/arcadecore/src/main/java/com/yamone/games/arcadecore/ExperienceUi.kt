package com.yamone.games.arcadecore

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.sin

private val ExperienceInk = Color(0xFF213C43)
private val ExperienceMuted = Color(0xFF647E84)

@Composable
fun ExperienceSettingsPanel(modifier: Modifier = Modifier, compact: Boolean = false) {
    val controller = LocalGameExperience.current ?: return
    val feedbackView = LocalView.current
    val s = controller.settings
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = Color.White) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("소리와 진동", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = ExperienceInk)
            SettingsSwitch("효과음", "버튼 · 획득 · 점프 · 결과", s.effects) { controller.update { it.copy(effects = !s.effects) } }
            if (s.effects) {
                VolumeControl("효과음 크기", s.effectsVolume) { value -> controller.update { it.copy(effectsVolume = value) } }
                TextButton(onClick = { controller.play(GameSound.COLLECT, feedbackView = feedbackView) }) { Text("효과음 들어보기") }
            }
            SettingsSwitch("배경음악", "게임 분위기에 맞는 오프라인 음악", s.music) { controller.update { it.copy(music = !s.music) } }
            if (s.music) {
                VolumeControl("음악 크기", s.musicVolume) { value -> controller.update { it.copy(musicVolume = value) } }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MusicStyle.entries.forEach { style ->
                        FilterChip(selected = s.musicStyle == style,
                            onClick = { controller.update { it.copy(musicStyle = style) } },
                            label = { Text(style.label, fontSize = 10.sp) }, border = null)
                    }
                }
                if (!compact) TextButton(onClick = { controller.previewMusic() }) { Text("배경음악 미리 듣기") }
            }
            SettingsSwitch("진동", "휴대폰의 진동 설정을 함께 따라요", s.vibration) { controller.update { it.copy(vibration = !s.vibration) } }
            if (s.vibration) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    HapticStrength.entries.forEach { strength ->
                        FilterChip(selected = s.hapticStrength == strength,
                            onClick = { controller.update { it.copy(hapticStrength = strength) }; controller.play(GameSound.TAP, feedbackView = feedbackView) },
                            label = { Text(strength.label) }, border = null)
                    }
                    TextButton(onClick = { controller.play(GameSound.TAP, feedbackView = feedbackView) }) { Text("테스트") }
                }
            }
            SettingsSwitch("휴대폰 무음 모드 따르기", "무음·진동 모드에서는 음악과 효과음을 꺼요", s.respectSilentMode) {
                controller.update { it.copy(respectSilentMode = !s.respectSilentMode) }
            }
            Text("설정은 모든 게임에 적용되고 자동 저장돼요. 앱을 벗어나면 음악도 멈춰요.", fontSize = 11.sp, color = ExperienceMuted)
        }
    }
}

@Composable
private fun SettingsSwitch(title: String, description: String, checked: Boolean, onChange: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ExperienceInk)
            Text(description, fontSize = 11.sp, color = ExperienceMuted)
        }
        Switch(checked = checked, onCheckedChange = { onChange() })
    }
}

@Composable
private fun VolumeControl(label: String, value: Float, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 12.sp, color = ExperienceMuted)
            Text("${(value * 100).toInt()}%", fontSize = 12.sp, color = ExperienceInk)
        }
        Slider(value, onValueChange = onChange, modifier = Modifier.semantics { contentDescription = label })
    }
}

@Stable
class ArcadeSession {
    var paused by mutableStateOf(false)
    var showSoundSettings by mutableStateOf(false)
}

@Composable
fun rememberArcadeSession(scene: MusicScene, active: Boolean, ended: Boolean, exitConfirm: Boolean): ArcadeSession {
    val experience = LocalGameExperience.current
    val session = remember { ArcadeSession() }
    var observedPauseGeneration by remember { mutableIntStateOf(experience?.pauseGeneration ?: 0) }
    LaunchedEffect(experience?.pauseGeneration) {
        val generation = experience?.pauseGeneration ?: 0
        // A paused Activity may defer composition until AFTER onResume. Compare generations,
        // not only the current foreground flag, so returning never resumes gameplay silently.
        if (active && !ended && (generation != observedPauseGeneration || experience?.foreground == false || experience?.interrupted == true)) session.paused = true
        observedPauseGeneration = generation
    }
    SideEffect {
        experience?.setScene(scene, active && !ended && !exitConfirm && !session.paused && !session.showSoundSettings)
    }
    DisposableEffect(Unit) { onDispose { experience?.setScene(MusicScene.HOME, true) } }
    return session
}

@Composable
fun SessionHeader(title: String, subtitle: String, primary: Color, onBack: () -> Unit,
                  active: Boolean, session: ArcadeSession, mascot: @Composable (Dp) -> Unit) {
    val experience = LocalGameExperience.current
    Row(Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack, modifier = Modifier.size(48.dp).semantics { contentDescription = "뒤로가기" }, contentPadding = PaddingValues(0.dp)) {
            Text("‹", fontSize = 34.sp, color = primary)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = ExperienceInk)
            Text(subtitle, fontSize = 11.sp, color = ExperienceMuted)
        }
        if (active) {
            TextButton(onClick = { session.paused = true; experience?.play(GameSound.TAP) },
                modifier = Modifier.size(48.dp).semantics { contentDescription = "일시정지" }, contentPadding = PaddingValues(0.dp)) {
                Text("Ⅱ", fontSize = 24.sp, color = primary)
            }
        } else mascot(42.dp)
        TextButton(onClick = { session.showSoundSettings = true; if (active) session.paused = true },
            modifier = Modifier.size(48.dp).semantics { contentDescription = "소리와 진동 설정" }, contentPadding = PaddingValues(0.dp)) {
            Text("♫", fontSize = 23.sp, color = primary)
        }
    }
}

@Composable
fun SessionDialogs(session: ArcadeSession, onExit: () -> Unit, mascot: @Composable (Dp) -> Unit) {
    val experience = LocalGameExperience.current
    if (session.showSoundSettings) {
        Dialog(onDismissRequest = { session.showSoundSettings = false }) {
            Surface(shape = RoundedCornerShape(28.dp), color = Color(0xFFF5FBFA)) {
                Column(Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(8.dp)) {
                    ExperienceSettingsPanel(compact = true)
                    TextButton(onClick = { session.showSoundSettings = false }, modifier = Modifier.align(Alignment.End)) { Text("완료") }
                }
            }
        }
    } else if (session.paused) {
        AlertDialog(onDismissRequest = {}, shape = RoundedCornerShape(28.dp),
            icon = { mascot(68.dp) }, title = { Text("잠깐 쉬어가요", fontWeight = FontWeight.Bold) },
            text = { Text("계속하기를 누르면 다시 시작해요.\n앱을 벗어나도 게임은 일시정지돼요.") },
            confirmButton = {
                Button(onClick = { experience?.resumeByUser(); session.paused = false; experience?.play(GameSound.START) }, shape = RoundedCornerShape(16.dp)) { Text("계속하기") }
            }, dismissButton = {
                Row {
                    TextButton(onClick = { session.showSoundSettings = true }) { Text("소리 설정") }
                    TextButton(onClick = onExit) { Text("그만하기") }
                }
            })
    }
}

@Composable
fun GameResultPanel(modifier: Modifier = Modifier, score: Int, previousBest: Int, unit: String,
                    primary: Color, title: String = "멋진 한 판이었어요!", onRetry: () -> Unit,
                    onExit: () -> Unit, exitLabel: String = "다른 게임", mascot: @Composable (Dp) -> Unit) {
    val newBest = score > previousBest
    Surface(modifier.widthIn(max = 340.dp).fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(28.dp), color = Color.White) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            mascot(78.dp)
            Text(if (newBest) "반짝! 새로운 최고기록" else title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = ExperienceInk, textAlign = TextAlign.Center)
            Text("$score$unit", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = primary)
            Text(if (newBest) "이전 최고 $previousBest$unit → $score$unit" else "최고기록까지 ${previousBest - score + 1}$unit 더!", fontSize = 12.sp, color = ExperienceMuted)
            Text("기록은 기기에 저장됐어요", fontSize = 11.sp, color = ExperienceMuted)
            Spacer(Modifier.height(4.dp))
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primary)) { Text("한 번 더!", fontWeight = FontWeight.Bold) }
            TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text(exitLabel, color = ExperienceMuted) }
        }
    }
}

@Composable
fun GameStartPanel(modifier: Modifier = Modifier, title: String, instruction: String, best: String,
                   primary: Color, onStart: () -> Unit, mascot: @Composable (Dp) -> Unit) {
    Surface(modifier.widthIn(max = 340.dp).fillMaxWidth().padding(22.dp), color = Color.White.copy(alpha = .96f), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            mascot(84.dp)
            Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = ExperienceInk)
            Text(instruction, fontSize = 13.sp, color = ExperienceMuted, textAlign = TextAlign.Center)
            Text("내 최고 $best", fontSize = 12.sp, color = primary)
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primary)) { Text("시작하기", fontWeight = FontWeight.Bold) }
        }
    }
}

/** Borderless low-contrast scenery; gameplay objects remain the high-contrast foreground. */
@Composable
fun ArcadeScenery(scene: MusicScene, modifier: Modifier = Modifier, phase: Float = 0f) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        if (scene == MusicScene.FISH) {
            repeat(8) { i ->
                val x = w * (i + .4f) / 8
                val seaweed = Path().apply {
                    moveTo(x, h); cubicTo(x - w * .05f, h * .93f, x + w * .06f, h * .85f, x, h * .79f)
                    cubicTo(x + w * .07f, h * .89f, x - w * .02f, h * .96f, x + w * .035f, h); close()
                }
                drawPath(seaweed, if (i % 2 == 0) Color(0xFF66B8B6).copy(alpha = .22f) else Color(0xFFDB9BBE).copy(alpha = .22f))
            }
            repeat(12) { i ->
                val x = w * ((i * 37 + 13) % 97) / 100f
                val y = h * (((i * .13f - phase * .013f) % 1f + 1f) % 1f)
                drawCircle(Color.White.copy(alpha = .34f), w * (.008f + (i % 3) * .006f), Offset(x, y))
            }
        } else {
            repeat(5) { i ->
                val x = w * i / 4
                val peak = h * (.17f + (i % 3) * .055f)
                val mountain = Path().apply { moveTo(x - w * .31f, h * .52f); lineTo(x, peak); lineTo(x + w * .31f, h * .52f); close() }
                drawPath(mountain, Color.White.copy(alpha = .28f))
            }
            repeat(24) { i ->
                val x = w * ((i * 37 + 11) % 101) / 100f
                val y = h * (((i * .137f + phase * .009f) % 1f + 1f) % 1f)
                drawCircle(Color.White.copy(alpha = .57f), (1 + i % 3).dp.toPx(), Offset(x, y))
            }
        }
    }
}
