package com.yamone.games.sudoku.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamone.games.sudoku.game.GameStorage
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

    init {
        val saved = storage.load()
        if (saved != null && !saved.completed) {
            puzzle = saved.puzzle
            solution = saved.solution
            values = saved.values
            notes = saved.notes
            difficulty = saved.difficulty
            elapsedSeconds = saved.elapsedSeconds
            mistakes = saved.mistakes
            selected = puzzle.indexOfFirst { it == 0 }
        } else {
            newGame(SudokuDifficulty.NORMAL)
        }
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
        persist()
    }

    fun erase() {
        val index = selected
        if (index !in 0..80 || puzzle[index] != 0 || paused || completed) return
        values = values.copyOf().also { it[index] = 0 }
        notes = notes.copyOf().also { it[index] = 0 }
        if (wrongCell == index) wrongCell = -1
        persist()
    }

    fun tick() {
        if (!paused && !completed) {
            elapsedSeconds++
            if (elapsedSeconds % 5 == 0) persist()
        }
    }

    fun newGame(newDifficulty: SudokuDifficulty = difficulty) {
        val generated = SudokuEngine.generate(newDifficulty)
        puzzle = generated.puzzle
        solution = generated.solution
        values = generated.puzzle.copyOf()
        notes = IntArray(81)
        difficulty = newDifficulty
        selected = puzzle.indexOfFirst { it == 0 }
        noteMode = false
        mistakes = 0
        elapsedSeconds = 0
        paused = false
        completed = false
        wrongCell = -1
        persist()
    }

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
        completed = values.contentEquals(solution)
        if (completed) paused = true
    }

    private fun persist() {
        storage.save(
            StoredGame(
                puzzle = puzzle,
                solution = solution,
                values = values,
                notes = notes,
                difficulty = difficulty,
                elapsedSeconds = elapsedSeconds,
                mistakes = mistakes,
                completed = completed
            )
        )
    }
}

@Composable
fun SudokuApp(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val game = remember { SudokuController(context) }

    LaunchedEffect(game.paused, game.completed) {
        while (!game.paused && !game.completed) {
            delay(1000)
            game.tick()
        }
    }

    Box(Modifier.fillMaxSize().background(YamoneCream)) {
        Scaffold(
            containerColor = YamoneCream,
            topBar = { SudokuTopBar(onBack) }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DifficultyBar(game)
                Spacer(Modifier.height(8.dp))
                SudokuBoard(game)
                Spacer(Modifier.height(10.dp))
                ToolBar(game)
                Spacer(Modifier.height(10.dp))
                NumberPad(game::input)
                Spacer(Modifier.height(10.dp))
                MascotTip(game.noteMode, game.difficulty)
            }
        }

        if (game.paused && !game.completed) PauseOverlay(game::togglePause)
        if (game.completed) ClearOverlay(game)
    }
}

@Composable
private fun SudokuTopBar(onBack: () -> Unit) {
    Surface(color = Color.White) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().height(58.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("‹", fontSize = 30.sp, color = YamoneInk) }
            Text("스도쿠", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
            Spacer(Modifier.weight(1f))
            Mascots()
        }
    }
}

@Composable
private fun DifficultyBar(game: SudokuController) {
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
                    color = if (active) YamoneMint else Color.Transparent,
                    onClick = { if (!active) game.newGame(level) }
                ) {
                    Text(
                        level.label,
                        modifier = Modifier.padding(vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (active) Color.White else YamoneMuted
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(formatTime(game.elapsedSeconds), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = YamoneMintDark)
    }
}

@Composable
private fun SudokuBoard(game: SudokuController) {
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(Color.White)
    ) {
        Column(Modifier.fillMaxSize()) {
            repeat(9) { row ->
                Row(Modifier.weight(1f)) {
                    repeat(9) { col ->
                        val index = row * 9 + col
                        SudokuCell(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            index = index,
                            game = game
                        )
                    }
                }
            }
        }

        Canvas(Modifier.matchParentSize()) {
            val cell = size.width / 9f
            for (i in 0..9) {
                val thick = i % 3 == 0
                val stroke = if (thick) 2.4.dp.toPx() else 0.7.dp.toPx()
                val color = if (thick) YamoneMintDark else Color(0xFFCFDEDB)
                drawLine(color, Offset(i * cell, 0f), Offset(i * cell, size.height), stroke)
                drawLine(color, Offset(0f, i * cell), Offset(size.width, i * cell), stroke)
            }
        }
    }
}

@Composable
private fun SudokuCell(modifier: Modifier, index: Int, game: SudokuController) {
    val selected = index == game.selected
    val same = game.isSameNumber(index)
    val peer = game.isPeer(index)
    val value = game.values[index]
    val given = game.puzzle[index] != 0
    val wrong = game.wrongCell == index && value != 0

    val background = when {
        selected -> YamoneMint.copy(alpha = 0.48f)
        same -> YamonePinkSoft
        peer -> YamoneMintSoft
        else -> Color.White
    }

    Box(
        modifier = modifier.background(background).clickable { game.select(index) },
        contentAlignment = Alignment.Center
    ) {
        if (value != 0) {
            Text(
                value.toString(),
                fontSize = 19.sp,
                fontWeight = if (given) FontWeight.ExtraBold else FontWeight.Bold,
                color = when {
                    wrong -> YamoneError
                    given -> YamoneInk
                    else -> YamoneMintDark
                }
            )
        } else if (game.notes[index] != 0) {
            NoteGrid(game.notes[index])
        }
    }
}

@Composable
private fun NoteGrid(mask: Int) {
    Column(Modifier.fillMaxSize().padding(2.dp)) {
        repeat(3) { row ->
            Row(Modifier.weight(1f)) {
                repeat(3) { col ->
                    val n = row * 3 + col + 1
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (mask and (1 shl n) != 0) {
                            Text(n.toString(), fontSize = 8.sp, color = YamoneMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolBar(game: SudokuController) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ToolButton(
            modifier = Modifier.weight(1f),
            title = if (game.noteMode) "메모 ON" else "메모 OFF",
            symbol = "✎",
            active = game.noteMode,
            onClick = game::toggleNote
        )
        ToolButton(Modifier.weight(1f), "지우기", "⌫", onClick = game::erase)
        ToolButton(Modifier.weight(1f), if (game.paused) "계속" else "일시정지", if (game.paused) "▶" else "Ⅱ", onClick = game::togglePause)
        Surface(
            modifier = Modifier.weight(1f).height(54.dp),
            shape = RoundedCornerShape(14.dp),
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, YamoneMintLine)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("실수", fontSize = 10.sp, color = YamoneMuted)
                Text("${game.mistakes}회", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (game.mistakes > 0) YamoneError else YamoneInk)
            }
        }
    }
}

@Composable
private fun ToolButton(
    modifier: Modifier,
    title: String,
    symbol: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (active) YamoneMint else Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (active) YamoneMint else YamoneMintLine),
        onClick = onClick
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(symbol, fontSize = 16.sp, color = if (active) Color.White else YamoneMintDark)
            Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (active) Color.White else YamoneInk)
        }
    }
}

@Composable
private fun NumberPad(onNumber: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        (1..9).forEach { number ->
            Surface(
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDCE9E6)),
                onClick = { onNumber(number) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(number.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = YamoneInk)
                }
            }
        }
    }
}

@Composable
private fun MascotTip(noteMode: Boolean, difficulty: SudokuDifficulty) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (difficulty == SudokuDifficulty.CHALLENGE) YamonePinkSoft else YamoneMintSoft
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SealFace()
            Spacer(Modifier.width(9.dp))
            Text(
                when {
                    noteMode -> "가능한 숫자를 메모해 두면 훨씬 편해요 ♡"
                    difficulty == SudokuDifficulty.CHALLENGE -> "도전에서도 메모와 지우기는 그대로 사용할 수 있어요!"
                    else -> "선택한 칸의 가로·세로·3×3 영역을 같이 살펴봐요 ♡"
                },
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                color = YamoneInk
            )
        }
    }
}

@Composable
private fun Mascots() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SealFace()
        Spacer(Modifier.width(3.dp))
        BearFace()
    }
}

@Composable
private fun SealFace() {
    Canvas(Modifier.size(34.dp)) {
        drawCircle(Color.White, radius = size.minDimension * 0.42f)
        drawCircle(YamoneInk, radius = size.minDimension * 0.035f, center = Offset(size.width * .40f, size.height * .44f))
        drawCircle(YamoneInk, radius = size.minDimension * 0.035f, center = Offset(size.width * .60f, size.height * .44f))
        drawCircle(YamonePink, radius = size.minDimension * 0.028f, center = Offset(size.width * .50f, size.height * .54f))
        drawArc(YamoneMint, 180f, 180f, false, style = Stroke(size.width * .07f))
    }
}

@Composable
private fun BearFace() {
    Canvas(Modifier.size(34.dp)) {
        val brown = Color(0xFFD49B7B)
        drawCircle(brown, radius = size.minDimension * .14f, center = Offset(size.width * .27f, size.height * .25f))
        drawCircle(brown, radius = size.minDimension * .14f, center = Offset(size.width * .73f, size.height * .25f))
        drawCircle(Color(0xFFE1AC8D), radius = size.minDimension * .38f)
        drawCircle(YamoneInk, radius = size.minDimension * .035f, center = Offset(size.width * .41f, size.height * .45f))
        drawCircle(YamoneInk, radius = size.minDimension * .035f, center = Offset(size.width * .59f, size.height * .45f))
        drawCircle(YamonePink, radius = size.minDimension * .055f, center = Offset(size.width * .50f, size.height * .56f))
    }
}

@Composable
private fun PauseOverlay(onResume: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.96f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Mascots()
            Spacer(Modifier.height(14.dp))
            Text("잠깐 쉬어갈까요?", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
            Text("게임 시간은 멈춰 있어요 ♡", fontSize = 13.sp, color = YamoneMuted)
            Spacer(Modifier.height(18.dp))
            Button(onClick = onResume, colors = ButtonDefaults.buttonColors(containerColor = YamoneMint)) {
                Text("계속하기", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ClearOverlay(game: SudokuController) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.97f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 5.dp,
            color = Color.White
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Mascots()
                Spacer(Modifier.height(8.dp))
                Text("클리어!", fontSize = 36.sp, fontWeight = FontWeight.Black, color = YamoneMint)
                Text("내가 만든 스도쿠, 한 판 완성 ♡", fontSize = 13.sp, color = YamoneMuted)
                Spacer(Modifier.height(18.dp))
                ResultRow("난이도", game.difficulty.label)
                ResultRow("플레이 시간", formatTime(game.elapsedSeconds))
                ResultRow("실수", "${game.mistakes}회")
                Spacer(Modifier.height(18.dp))
                Button(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    onClick = { game.newGame(game.difficulty) },
                    colors = ButtonDefaults.buttonColors(containerColor = YamoneMint),
                    shape = RoundedCornerShape(17.dp)
                ) {
                    Text("다음 게임", fontWeight = FontWeight.ExtraBold)
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
