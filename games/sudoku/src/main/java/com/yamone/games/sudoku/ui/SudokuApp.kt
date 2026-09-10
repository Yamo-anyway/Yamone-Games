package com.yamone.games.sudoku.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
    var fixedInput by mutableStateOf(false); private set
    var fixedNumber by mutableIntStateOf(0); private set
    var mistakes by mutableIntStateOf(0); private set
    var elapsedSeconds by mutableIntStateOf(0); private set
    var paused by mutableStateOf(false); private set
    var completed by mutableStateOf(false); private set
    var wrongCell by mutableIntStateOf(-1); private set
    var guessCheckpoint by mutableStateOf<GuessCheckpoint?>(null); private set

    val canUseGuess: Boolean get() = difficulty != SudokuDifficulty.EASY
    val guessing: Boolean get() = guessCheckpoint != null
    val guideNumber: Int
        get() = when {
            fixedInput && fixedNumber in 1..9 -> fixedNumber
            selected in 0..80 -> values[selected]
            else -> 0
        }

    init {
        val saved = storage.loadLast()
        if (saved != null) restore(saved) else createNew(SudokuDifficulty.NORMAL)
    }

    fun select(index: Int) {
        if (paused || completed || index !in 0..80) return
        selected = index
        if (
            fixedInput && fixedNumber in 1..9 &&
            !isNumberComplete(fixedNumber) &&
            puzzle[index] == 0 && values[index] == 0
        ) {
            inputAt(index, fixedNumber)
        }
    }

    fun toggleNote() {
        if (!paused && !completed) noteMode = !noteMode
    }

    fun switchInputMode(enabled: Boolean) {
        if (paused || completed || fixedInput == enabled) return
        fixedInput = enabled
        fixedNumber = 0
        if (enabled) selected = -1
    }

    fun pressNumber(number: Int) {
        if (paused || completed || number !in 1..9) return
        if (fixedInput) {
            fixedNumber = if (fixedNumber == number) 0 else number
            return
        }
        input(number)
    }

    fun togglePause() {
        if (!completed) paused = !paused
    }

    fun input(number: Int) {
        val index = selected
        if (index !in 0..80) return
        inputAt(index, number)
    }

    private fun inputAt(index: Int, number: Int) {
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
            noteMode = noteMode,
            fixedInput = fixedInput,
            fixedNumber = fixedNumber
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
        fixedInput = checkpoint.fixedInput
        fixedNumber = checkpoint.fixedNumber.coerceIn(0, 9)
        guessCheckpoint = null
        wrongCell = -1
        persist()
    }

    fun tick() {
        if (!paused && !completed) {
            elapsedSeconds++
            if (elapsedSeconds % 5 == 0) persist()
        }
    }

    fun saveNow() {
        if (!completed) persist()
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

    fun isWrong(index: Int): Boolean =
        index in 0..80 && puzzle[index] == 0 && values[index] != 0 && values[index] != solution[index]

    fun isCorrectNumberAt(index: Int, number: Int): Boolean =
        index in 0..80 && number in 1..9 && values[index] == number && solution[index] == number

    fun isSameNumber(index: Int): Boolean {
        if (index !in 0..80 || index == selected) return false
        return guideNumber != 0 && isCorrectNumberAt(index, guideNumber)
    }

    fun isNumberComplete(number: Int): Boolean {
        if (number !in 1..9) return false
        return solution.indices.all { solution[it] != number || values[it] == number }
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
        fixedInput = false
        fixedNumber = 0
        paused = false
        completed = false
        wrongCell = -1
        guessCheckpoint = saved.guessCheckpoint?.let {
            GuessCheckpoint(
                values = it.values.copyOf(),
                notes = it.notes.copyOf(),
                selected = it.selected,
                mistakes = it.mistakes,
                noteMode = it.noteMode,
                fixedInput = it.fixedInput,
                fixedNumber = it.fixedNumber
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
        fixedInput = false
        fixedNumber = 0
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
                noteMode = it.noteMode,
                fixedInput = it.fixedInput,
                fixedNumber = it.fixedNumber
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
    val view = LocalView.current
    val game = remember { SudokuController(context) }
    var pendingDifficulty by remember { mutableStateOf<SudokuDifficulty?>(null) }
    var exitConfirm by remember { mutableStateOf(false) }
    var appActive by remember(view) { mutableStateOf(view.hasWindowFocus()) }
    val scrollState = rememberScrollState()

    fun requestExit() {
        if (game.completed) {
            onBack()
            return
        }
        if (!game.paused) game.togglePause()
        exitConfirm = true
    }

    BackHandler { requestExit() }

    DisposableEffect(view, game) {
        val focusListener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            appActive = hasFocus
            if (!hasFocus) game.saveNow()
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
        appActive = view.hasWindowFocus()

        onDispose {
            if (view.viewTreeObserver.isAlive) {
                view.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
            }
            game.saveNow()
        }
    }

    LaunchedEffect(game.paused, game.completed, appActive) {
        while (appActive && !game.paused && !game.completed) {
            delay(1000)
            game.tick()
        }
    }

    Box(Modifier.fillMaxSize().background(YamoneCream)) {
        Scaffold(
            containerColor = YamoneCream,
            topBar = { SudokuTopBar(::requestExit, mascot, themeMode) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DifficultyBar(game, themeMode) { pendingDifficulty = it }
                Spacer(Modifier.height(8.dp))
                SudokuBoard(game, themeMode)
                Spacer(Modifier.height(10.dp))
                ToolBar(game, themeMode)
                Spacer(Modifier.height(8.dp))
                InputModeToggle(game, themeMode)
                Spacer(Modifier.height(8.dp))
                NumberPad(game, themeMode)
                Spacer(Modifier.height(8.dp))
                MascotTip(game, mascot, themeMode)
                Spacer(Modifier.height(8.dp))
            }
        }

        if (game.paused && !game.completed && !exitConfirm) PauseOverlay(game::togglePause, mascot, themeMode)
        if (game.completed) ClearOverlay(game, mascot, themeMode, onBack)
    }

    if (exitConfirm) {
        AlertDialog(
            onDismissRequest = {
                exitConfirm = false
                if (game.paused && !game.completed) game.togglePause()
            },
            shape = RoundedCornerShape(24.dp),
            title = { Text("게임을 그만둘까요?", fontWeight = FontWeight.Black, color = YamoneInk) },
            text = { Text("게임이 일시정지됐어요. 종료하면 현재 스도쿠 판은 임시 저장돼요.", color = YamoneMuted) },
            confirmButton = {
                TextButton(onClick = {
                    exitConfirm = false
                    if (game.paused && !game.completed) game.togglePause()
                }) { Text("계속하기", fontWeight = FontWeight.Bold, color = yamonePrimaryDark(themeMode)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    game.saveNow()
                    exitConfirm = false
                    onBack()
                }) { Text("게임 종료", fontWeight = FontWeight.Bold, color = YamoneError) }
            }
        )
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
            modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(onClick = onBack, color = Color.Transparent) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Text("←", fontSize = 30.sp, fontWeight = FontWeight.Black, color = yamonePrimaryDark(themeMode))
                }
            }
            Spacer(Modifier.width(10.dp))
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
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Color(0xFFF0F5F4)).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            SudokuDifficulty.entries.forEach { level ->
                val active = level == game.difficulty
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(15.dp),
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
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(Color.White)
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
    val value = game.values[index]
    val given = game.puzzle[index] != 0
    val wrong = game.isWrong(index)
    val accent = yamonePrimary(themeMode)
    val dark = yamonePrimaryDark(themeMode)

    val selectedIndex = game.selected
    val hasSelection = selectedIndex in 0..80
    val selectedRow = if (hasSelection) selectedIndex / 9 else -1
    val selectedCol = if (hasSelection) selectedIndex % 9 else -1
    val selectedValue = game.guideNumber
    val row = index / 9
    val col = index % 9

    val primaryCross = hasSelection && (row == selectedRow || col == selectedCol)
    val primaryBox = hasSelection && row / 3 == selectedRow / 3 && col / 3 == selectedCol / 3
    val secondaryCross = selectedValue != 0 && !primaryCross && game.values.indices.any { anchor ->
        anchor != selectedIndex && game.isCorrectNumberAt(anchor, selectedValue) &&
            (row == anchor / 9 || col == anchor % 9)
    }

    val background = when {
        selected -> accent.copy(alpha = 0.42f)
        same -> yamoneSecondarySoft(themeMode)
        primaryCross -> accent.copy(alpha = 0.18f)
        primaryBox -> accent.copy(alpha = 0.09f)
        secondaryCross -> accent.copy(alpha = 0.055f)
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
    Column(Modifier.fillMaxSize()) {
        repeat(3) { row ->
            Row(Modifier.weight(1f).fillMaxWidth()) {
                repeat(3) { col ->
                    val n = row * 3 + col + 1
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (mask and (1 shl n) != 0) {
                            Text(
                                text = n.toString(),
                                fontSize = 10.sp,
                                lineHeight = 10.sp,
                                maxLines = 1,
                                softWrap = false,
                                fontWeight = FontWeight.SemiBold,
                                color = noteColor,
                                textAlign = TextAlign.Center
                            )
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
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(18.dp),
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
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(18.dp),
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
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(18.dp),
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
private fun InputModeToggle(game: SudokuController, themeMode: YamoneThemeMode) {
    val accent = yamonePrimary(themeMode)
    val dark = yamonePrimaryDark(themeMode)
    val background = Color(0xFFF0F5F4)

    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(20.dp)).background(background).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        InputModeButton(
            modifier = Modifier.weight(1f),
            title = "일반 입력",
            active = !game.fixedInput,
            accent = accent,
            dark = dark,
            onClick = { game.switchInputMode(false) }
        )
        InputModeButton(
            modifier = Modifier.weight(1f),
            title = if (game.fixedInput && game.fixedNumber > 0) "숫자 고정 · ${game.fixedNumber}" else "숫자 고정",
            active = game.fixedInput,
            accent = accent,
            dark = dark,
            onClick = { game.switchInputMode(true) }
        )
    }
}

@Composable
private fun InputModeButton(
    modifier: Modifier,
    title: String,
    active: Boolean,
    accent: Color,
    dark: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(17.dp),
        color = if (active) accent else Color.Transparent,
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                title,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (active) Color.White else dark
            )
        }
    }
}

@Composable
private fun NumberPad(game: SudokuController, themeMode: YamoneThemeMode) {
    val accent = yamonePrimary(themeMode)
    val dark = yamonePrimaryDark(themeMode)
    val line = yamonePrimaryLine(themeMode)
    val completedBackground = yamonePrimarySoft(themeMode)

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        (1..9).forEach { number ->
            val complete = game.isNumberComplete(number)
            val fixedSelected = game.fixedInput && game.fixedNumber == number
            Surface(
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(16.dp),
                color = when {
                    fixedSelected -> accent
                    complete -> completedBackground
                    else -> Color.White
                },
                border = BorderStroke(
                    1.dp,
                    when {
                        fixedSelected -> accent
                        complete -> accent.copy(alpha = 0.55f)
                        else -> line
                    }
                ),
                onClick = { game.pressNumber(number) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        number.toString(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            fixedSelected -> Color.White
                            complete -> dark.copy(alpha = 0.68f)
                            else -> dark
                        }
                    )
                    if (complete) {
                        Text(
                            "✓",
                            modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 4.dp),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = if (fixedSelected) Color.White else accent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MascotTip(game: SudokuController, mascot: YamoneMascot, themeMode: YamoneThemeMode) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = yamonePrimarySoft(themeMode)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            YamoneMascotIcon(mascot, size = 36.dp, accent = yamonePrimary(themeMode))
            Spacer(Modifier.width(9.dp))
            Text(
                when {
                    game.fixedInput && game.fixedNumber > 0 && game.isNumberComplete(game.fixedNumber) -> "${game.fixedNumber}은(는) 모두 완성됐어요. 선택해서 줄만 살펴볼 수 있어요 ♡"
                    game.fixedInput && game.fixedNumber == 0 -> "숫자 고정에서는 숫자를 먼저 골라주세요. 그다음 빈칸을 톡톡 ♡"
                    game.fixedInput && game.noteMode -> "메모 ${game.fixedNumber} 고정 중! 빈칸을 누르면 메모가 추가·해제돼요 ♡"
                    game.fixedInput -> "숫자 ${game.fixedNumber} 고정 중! 빈칸을 연속으로 누르면 바로 입력돼요 ♡"
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
private fun ClearOverlay(game: SudokuController, mascot: YamoneMascot, themeMode: YamoneThemeMode, onExit: () -> Unit) {
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
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f).height(48.dp),
                        onClick = { game.newGame(game.difficulty) },
                        colors = ButtonDefaults.buttonColors(containerColor = yamonePrimary(themeMode)),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("다시하기", fontWeight = FontWeight.ExtraBold) }
                    OutlinedButton(
                        modifier = Modifier.weight(1f).height(48.dp),
                        onClick = onExit,
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("그만하기", fontWeight = FontWeight.Bold) }
                }
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
