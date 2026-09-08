package com.yamone.games.sudoku.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yamone.games.sudoku.game.GameStorage
import com.yamone.games.sudoku.game.GuessCheckpoint
import com.yamone.games.sudoku.game.StoredGame
import com.yamone.games.sudoku.game.SudokuDifficulty
import com.yamone.games.sudoku.game.SudokuEngine
import com.yamone.games.sudoku.ui.theme.*
import kotlinx.coroutines.delay

private class SudokuController(context: Context) {
    private val storage = GameStorage(context)

    var puzzle by mutableStateOf(IntArray(81)); private set
    var solution by mutableStateOf(IntArray(81)); private set
    var values by mutableStateOf(IntArray(81)); private set
    var notes by mutableStateOf(IntArray(81)); private set
    var difficulty by mutableStateOf(SudokuDifficulty.NORMAL); private set
    var selected by mutableIntStateOf(-1); private set
    var noteMode by mutableStateOf(false); private set
    var mistakes by mutableIntStateOf(0); private set
    var elapsedSeconds by mutableIntStateOf(0); private set
    var paused by mutableStateOf(false); private set
    var completed by mutableStateOf(false); private set
    var wrongCell by mutableIntStateOf(-1); private set
    var guessCheckpoint by mutableStateOf<GuessCheckpoint?>(null); private set

    val canUseGuess: Boolean get() = difficulty != SudokuDifficulty.EASY
    val guessing: Boolean get() = guessCheckpoint != null

    init {
        val saved = storage.loadLast()
        if (saved != null) restore(saved) else createNew(SudokuDifficulty.NORMAL)
    }

    fun select(index: Int) {
        if (!paused && !completed) selected = index
    }

    fun toggleNote() {
        if (!paused && !completed) noteMode = !noteMode
    }

    fun togglePause() {
        if (!completed) paused = !paused
    }

    fun input(number: Int) {
        val index = selected
        if (index !in 0..80 || puzzle[index] != 0 || paused || completed) return

        if (noteMode) {
            if (values[index] != 0) return
            val bit = 1 shl number
            notes = notes.copyOf().also { it[index] = it[index] xor bit }
        } else {
            values = values.copyOf().also { it[index] = number }
            notes = notes.copyOf().also { it[index] = 0 }
            if (number != solution[index]) {
                mistakes++
                wrongCell = index
            } else {
                wrongCell = -1
                removePeerNote(index, number)
            }
            checkCompletion()
        }
        if (!completed) persist()
    }

    fun erase() {
        val index = selected
        if (index !in 0..80 || puzzle[index] != 0 || paused || completed) return
        values = values.copyOf().also { it[index] = 0 }
        notes = notes.copyOf().also { it[index] = 0 }
        if (wrongCell == index) wrongCell = -1
        persist()
    }

    fun startGuess() {
        if (!canUseGuess || guessing || paused || completed) return
        guessCheckpoint = GuessCheckpoint(
            values = values.copyOf(),
            notes = notes.copyOf(),
            selected = selected,
            mistakes = mistakes,
            noteMode = noteMode
        )
        wrongCell = -1
        persist()
    }

    fun endGuess() {
        if (!guessing || paused || completed) return
        guessCheckpoint = null
        persist()
    }

    fun returnToGuess() {
        val checkpoint = guessCheckpoint ?: return
        if (paused || completed) return
        values = checkpoint.values.copyOf()
        notes = checkpoint.notes.copyOf()
        selected = checkpoint.selected.coerceIn(-1, 80)
        mistakes = checkpoint.mistakes
        noteMode = checkpoint.noteMode
        wrongCell = -1
        // 체크포인트는 유지한다. 다른 후보를 다시 시험한 뒤 또 돌아올 수 있다.
        persist()
    }

    fun tick() {
        if (!paused && !completed) {
            elapsedSeconds++
            if (elapsedSeconds % 5 == 0) persist()
        }
    }

    fun saveAndSwitch(target: SudokuDifficulty) {
        persist()
        openDifficulty(target)
    }

    fun discardAndSwitch(target: SudokuDifficulty) {
        storage.delete(difficulty)
        openDifficulty(target)
    }

    fun newGame(level: SudokuDifficulty = difficulty) {
        storage.delete(level)
        createNew(level)
    }

    fun hasSaved(level: SudokuDifficulty): Boolean = storage.hasSaved(level)

    fun isPeer(index: Int): Boolean {
        if (selected !in 0..80) return false
        val sr = selected / 9
        val sc = selected % 9
        val r = index / 9
        val c = index % 9
        return r == sr || c == sc || (r / 3 == sr / 3 && c / 3 == sc / 3)
    }

    fun isSameNumber(index: Int): Boolean {
        if (selected !in 0..80 || index == selected) return false
        val selectedValue = values[selected]
        return selectedValue != 0 && values[index] == selectedValue
    }

    private fun openDifficulty(level: SudokuDifficulty) {
        val saved = storage.load(level)?.takeUnless { it.completed }
        if (saved != null) restore(saved) else createNew(level)
    }

    private fun restore(saved: StoredGame) {
        puzzle = saved.puzzle
        solution = saved.solution
        values = saved.values
        notes = saved.notes
        difficulty = saved.difficulty
        elapsedSeconds = saved.elapsedSeconds
        mistakes = saved.mistakes
        selected = puzzle.indices.firstOrNull { puzzle[it] == 0 && values[it] == 0 } ?: puzzle.indexOfFirst { it == 0 }
        noteMode = false
        paused = false
        completed = false
        wrongCell = -1
        guessCheckpoint = saved.guessCheckpoint?.let {
            GuessCheckpoint(
                values = it.values.copyOf(),
                notes = it.notes.copyOf(),
                selected = it.selected,
                mistakes = it.mistakes,
                noteMode = it.noteMode
            )
        }
    }

    private fun createNew(level: SudokuDifficulty) {
        val generated = SudokuEngine.generate(level)
        puzzle = generated.puzzle
        solution = generated.solution
        values = generated.puzzle.copyOf()
        notes = IntArray(81)
        difficulty = level
        selected = puzzle.indexOfFirst { it == 0 }
        noteMode = false
        mistakes = 0
        elapsedSeconds = 0
        paused = false
        completed = false
        wrongCell = -1
        guessCheckpoint = null
        persist()
    }

    private fun removePeerNote(index: Int, number: Int) {
        val bit = 1 shl number
        val row = index / 9
        val col = index % 9
        notes = notes.copyOf().also { arr ->
            for (i in 0..8) {
                arr[row * 9 + i] = arr[row * 9 + i] and bit.inv()
                arr[i * 9 + col] = arr[i * 9 + col] and bit.inv()
            }
            val br = row / 3 * 3
            val bc = col / 3 * 3
            for (r in br until br + 3) for (c in bc until bc + 3) {
                val p = r * 9 + c
                arr[p] = arr[p] and bit.inv()
            }
        }
    }

    private fun checkCompletion() {
        if (!completed && values.contentEquals(solution)) {
            completed = true
            paused = true
            val result = snapshot(completed = true)
            storage.recordCompletion(result)
            storage.delete(difficulty)
        }
    }

    private fun snapshot(completed: Boolean = this.completed) = StoredGame(
        puzzle = puzzle,
        solution = solution,
        values = values,
        notes = notes,
        difficulty = difficulty,
        elapsedSeconds = elapsedSeconds,
        mistakes = mistakes,
        completed = completed,
        guessCheckpoint = guessCheckpoint?.let {
            GuessCheckpoint(
                values = it.values.copyOf(),
                notes = it.notes.copyOf(),
                selected = it.selected,
                mistakes = it.mistakes,
                noteMode = it.noteMode
            )
        }
    )

    private fun persist() = storage.save(snapshot(completed = false))
}

@Composable
fun SudokuApp(
    onBack: () -> Unit,
    themeMode: YamoneThemeMode = YamoneThemeMode.MINT,
    mascot: YamoneMascot = YamoneMascot.SEAL
) {
    val context = LocalContext.current.applicationContext
    val game = remember { SudokuController(context) }
    var pendingDifficulty by remember { mutableStateOf<SudokuDifficulty?>(null) }

    LaunchedEffect(game.paused, game.completed) {
        while (!game.paused && !game.completed) {
            delay(1000)
            game.tick()
        }
    }

    Box(Modifier.fillMaxSize().background(YamoneCream)) {
        Scaffold(
            containerColor = YamoneCream,
            topBar = { SudokuTopBar(onBack, mascot, themeMode) }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DifficultyBar(game, themeMode) { pendingDifficulty = it }
                Spacer(Modifier.height(8.dp))
                SudokuBoard(game, themeMode)
                Spacer(Modifier.height(10.dp))
                ToolBar(game, themeMode)
                Spacer(Modifier.height(10.dp))
                NumberPad(game::input, themeMode)
                Spacer(Modifier.height(10.dp))
                MascotTip(game, mascot, themeMode)
            }
        }

        if (game.paused && !game.completed) PauseOverlay(game::togglePause, mascot, themeMode)
        if (game.completed) ClearOverlay(game, mascot, themeMode)
    }

    pendingDifficulty?.let { target ->
        DifficultyChangeDialog(
            current = game.difficulty,
            target = target,
            targetHasSave = game.hasSaved(target),
            themeMode = themeMode,
            onDiscard = {
                game.discardAndSwitch(target)
                pendingDifficulty = null
            },
            onSave = {
                game.saveAndSwitch(target)
                pendingDifficulty = null
            },
            onCancel = { pendingDifficulty = null }
        )
    }
}

@Composable
private fun SudokuTopBar(onBack: () -> Unit, mascot: YamoneMascot, themeMode: YamoneThemeMode) {
    Surface(color = Color.White) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().height(58.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("‹", fontSize = 30.sp, color = YamoneInk) }
            Text("스도쿠", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
            Spacer(Modifier.weight(1f))
            YamoneMascotIcon(mascot, size = 36.dp, accent = yamonePrimary(themeMode))
        }
    }
}

@Composable
private fun DifficultyBar(game: SudokuController, themeMode: YamoneThemeMode, onChange: (SudokuDifficulty) -> Unit) {
    val accent = yamonePrimary(themeMode)
    val dark = yamonePrimaryDark(themeMode)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color(0xFFF0F5F4)).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            SudokuDifficulty.entries.forEach { level ->
                val active = level == game.difficulty
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(13.dp),
                    color = if (active) accent else Color.Transparent,
                    onClick = { if (!active) onChange(level) }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            level.label,
                            modifier = Modifier.padding(top = 7.dp, bottom = if (game.hasSaved(level) && !active) 1.dp else 7.dp),
                            textAlign = TextAlign.Center,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (active) Color.White else YamoneMuted
                        )
                        if (game.hasSaved(level) && !active) {
                            Text("저장", fontSize = 8.sp, color = dark, modifier = Modifier.padding(bottom = 3.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(formatTime(game.elapsedSeconds), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = dark)
    }
}

@Composable
private fun SudokuBoard(game: SudokuController, themeMode: YamoneThemeMode) {
    val dark = yamonePrimaryDark(themeMode)
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(Color.White)
    ) {
        Column(Modifier.fillMaxSize()) {
            repeat(9) { row ->
                Row(Modifier.weight(1f)) {
                    repeat(9) { col ->
                        val index = row * 9 + col
                        SudokuCell(Modifier.weight(1f).fillMaxHeight(), index, game, themeMode)
                    }
                }
            }
        }

        Canvas(Modifier.matchParentSize()) {
            val cell = size.width / 9f
            for (i in 0..9) {
                val thick = i % 3 == 0
                val stroke = if (thick) 2.4.dp.toPx() else 0.7.dp.toPx()
                val color = if (thick) dark else Color(0xFFCFDEDB)
                drawLine(color, Offset(i * cell, 0f), Offset(i * cell, size.height), stroke)
                drawLine(color, Offset(0f, i * cell), Offset(size.width, i * cell), stroke)
            }
        }
    }
}

@Composable
private fun SudokuCell(modifier: Modifier, index: Int, game: SudokuController, themeMode: YamoneThemeMode) {
    val selected = index == game.selected
    val same = game.isSameNumber(index)
    val peer = game.isPeer(index)
    val value = game.values[index]
    val given = game.puzzle[index] != 0
    val wrong = game.wrongCell == index && value != 0
    val accent = yamonePrimary(themeMode)
    val dark = yamonePrimaryDark(themeMode)

    val background = when {
        selected -> accent.copy(alpha = 0.42f)
        same -> yamoneSecondarySoft(themeMode)
        peer -> yamonePrimarySoft(themeMode)
        else -> Color.White
    }

    Box(modifier = modifier.background(background).clickable { game.select(index) }, contentAlignment = Alignment.Center) {
        if (value != 0) {
            Text(
                value.toString(),
                fontSize = 19.sp,
                fontWeight = if (given) FontWeight.ExtraBold else FontWeight.Bold,
                color = when {
                    wrong -> YamoneError
                    given -> YamoneInk
                    else -> dark
                }
            )
        } else if (game.notes[index] != 0) {
            NoteGrid(game.notes[index], dark)
        }
    }
}

@Composable
private fun NoteGrid(mask: Int, noteColor: Color) {
    Column(Modifier.fillMaxSize().padding(vertical = 1.dp)) {
        repeat(3) { row ->
            Row(Modifier.weight(1f)) {
                repeat(3) { col ->
                    val n = row * 3 + col + 1
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (mask and (1 shl n) != 0) {
                            Text(n.toString(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = noteColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolBar(game: SudokuController, themeMode: YamoneThemeMode) {
    val accent = yamonePrimary(themeMode)
    val line = yamonePrimaryLine(themeMode)
    val dark = yamonePrimaryDark(themeMode)
    val spacing = if (game.guessing) 5.dp else 8.dp

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
        ToolButton(
            Modifier.weight(1f),
            if (game.noteMode) "메모 ON" else "메모 OFF",
            "✎",
            game.noteMode,
            accent,
            line,
            dark,
            onClick = game::toggleNote
        )
        ToolButton(Modifier.weight(1f), "지우기", "⌫", false, accent, line, dark, onClick = game::erase)
        ToolButton(
            Modifier.weight(1f),
            if (game.paused) "계속" else "일시정지",
            if (game.paused) "▶" else "Ⅱ",
            false,
            accent,
            line,
            dark,
            onClick = game::togglePause
        )

        if (!game.guessing) {
            GuessStartButton(
                modifier = Modifier.weight(1f),
                enabled = game.canUseGuess,
                accent = accent,
                line = line,
                dark = dark,
                onClick = game::startGuess
            )
        } else {
            GuessStateButton(
                modifier = Modifier.weight(1f),
                title = "추측 종료",
                symbol = "■",
                color = YamoneError,
                line = line,
                onClick = game::endGuess
            )
            GuessStateButton(
                modifier = Modifier.weight(1f),
                title = "돌아가기",
                symbol = "↩",
                color = dark,
                line = line,
                onClick = game::returnToGuess
            )
        }
    }
}

@Composable
private fun ToolButton(
    modifier: Modifier,
    title: String,
    symbol: String,
    active: Boolean,
    accent: Color,
    line: Color,
    dark: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(15.dp),
        color = when {
            !enabled -> Color(0xFFF3F5F5)
            active -> accent
            else -> Color.White
        },
        border = BorderStroke(1.dp, if (active && enabled) accent else line),
        enabled = enabled,
        onClick = onClick
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(symbol, fontSize = 16.sp, color = if (active && enabled) Color.White else if (enabled) dark else YamoneMuted.copy(alpha = 0.55f))
            Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (active && enabled) Color.White else if (enabled) YamoneInk else YamoneMuted.copy(alpha = 0.55f))
        }
    }
}

@Composable
private fun GuessStartButton(
    modifier: Modifier,
    enabled: Boolean,
    accent: Color,
    line: Color,
    dark: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(15.dp),
        color = if (enabled) yamonePrimarySoft(if (accent == YamoneMint) YamoneThemeMode.MINT else YamoneThemeMode.PINK) else Color(0xFFF3F5F5),
        border = BorderStroke(1.dp, if (enabled) accent else line),
        enabled = enabled,
        onClick = onClick
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("▶", fontSize = 15.sp, color = if (enabled) dark else YamoneMuted.copy(alpha = 0.5f))
            Text("추측 시작", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = if (enabled) YamoneInk else YamoneMuted.copy(alpha = 0.55f))
            if (!enabled) Text("보통부터", fontSize = 7.sp, color = YamoneMuted.copy(alpha = 0.65f))
        }
    }
}

@Composable
private fun GuessStateButton(
    modifier: Modifier,
    title: String,
    symbol: String,
    color: Color,
    line: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(15.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f).takeIf { color != Color.Unspecified } ?: line),
        onClick = onClick
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(symbol, fontSize = 15.sp, fontWeight = FontWeight.Black, color = color)
            Text(title, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = color)
        }
    }
}

@Composable
private fun NumberPad(onNumber: (Int) -> Unit, themeMode: YamoneThemeMode) {
    val dark = yamonePrimaryDark(themeMode)
    val line = yamonePrimaryLine(themeMode)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        (1..9).forEach { number ->
            Surface(
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, line),
                onClick = { onNumber(number) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(number.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = dark)
                }
            }
        }
    }
}

@Composable
private fun MascotTip(game: SudokuController, mascot: YamoneMascot, themeMode: YamoneThemeMode) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = yamonePrimarySoft(themeMode)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            YamoneMascotIcon(mascot, size = 38.dp, accent = yamonePrimary(themeMode))
            Spacer(Modifier.width(9.dp))
            Text(
                when {
                    game.guessing -> "추측 중이에요. ■ 종료하거나 ↩ 저장한 곳으로 돌아갈 수 있어요 ♡"
                    game.difficulty == SudokuDifficulty.EASY -> "추측 시작은 보통부터 사용할 수 있어요 ♡"
                    game.noteMode -> "가능한 숫자를 메모해 두고 하나씩 지워가요 ♡"
                    game.difficulty == SudokuDifficulty.HARD || game.difficulty == SudokuDifficulty.CHALLENGE -> "헷갈리기 시작할 때 ▶ 추측 시작을 눌러두면 편해요 ♡"
                    else -> "막힐 것 같으면 ▶ 추측 시작으로 지금 상태를 남겨둘 수 있어요 ♡"
                },
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                color = YamoneInk
            )
        }
    }
}

@Composable
private fun DifficultyChangeDialog(
    current: SudokuDifficulty,
    target: SudokuDifficulty,
    targetHasSave: Boolean,
    themeMode: YamoneThemeMode,
    onDiscard: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Dialog(onDismissRequest = onCancel) {
        Surface(shape = RoundedCornerShape(26.dp), color = Color.White, shadowElevation = 8.dp) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${current.label} 게임을 멈출까요?", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (targetHasSave) "${target.label}에는 임시저장된 게임이 있어요. 이동하면 이어서 시작해요."
                    else "${target.label}으로 이동하기 전에 지금 게임을 어떻게 할지 선택해 주세요.",
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    color = YamoneMuted
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = yamonePrimary(themeMode)),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("임시저장하고 이동", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDiscard,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("그만두고 이동", color = YamoneError, fontWeight = FontWeight.Bold) }
                TextButton(onClick = onCancel) { Text("취소", color = YamoneMuted, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun PauseOverlay(onResume: () -> Unit, mascot: YamoneMascot, themeMode: YamoneThemeMode) {
    Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.96f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            YamoneMascotIcon(mascot, size = 58.dp, accent = yamonePrimary(themeMode))
            Spacer(Modifier.height(14.dp))
            Text("잠깐 쉬어갈까요?", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
            Text("게임 시간은 멈춰 있어요 ♡", fontSize = 13.sp, color = YamoneMuted)
            Spacer(Modifier.height(18.dp))
            Button(onClick = onResume, colors = ButtonDefaults.buttonColors(containerColor = yamonePrimary(themeMode))) {
                Text("계속하기", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ClearOverlay(game: SudokuController, mascot: YamoneMascot, themeMode: YamoneThemeMode) {
    Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.97f)), contentAlignment = Alignment.Center) {
        Surface(modifier = Modifier.fillMaxWidth().padding(24.dp), shape = RoundedCornerShape(28.dp), shadowElevation = 5.dp, color = Color.White) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                YamoneMascotIcon(mascot, size = 64.dp, accent = yamonePrimary(themeMode))
                Spacer(Modifier.height(8.dp))
                Text("클리어!", fontSize = 36.sp, fontWeight = FontWeight.Black, color = yamonePrimary(themeMode))
                Text("한 판 완성! 기록에 저장했어요 ♡", fontSize = 13.sp, color = YamoneMuted)
                Spacer(Modifier.height(18.dp))
                ResultRow("난이도", game.difficulty.label)
                ResultRow("플레이 시간", formatTime(game.elapsedSeconds))
                ResultRow("실수", "${game.mistakes}회")
                Spacer(Modifier.height(18.dp))
                Button(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    onClick = { game.newGame(game.difficulty) },
                    colors = ButtonDefaults.buttonColors(containerColor = yamonePrimary(themeMode)),
                    shape = RoundedCornerShape(17.dp)
                ) { Text("다음 게임", fontWeight = FontWeight.ExtraBold) }
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = YamoneMuted)
        Spacer(Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Bold, color = YamoneInk)
    }
}

private fun formatTime(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
