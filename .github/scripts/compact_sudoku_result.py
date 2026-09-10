from pathlib import Path

p = Path('games/sudoku/src/main/java/com/yamone/games/sudoku/ui/SudokuApp.kt')
s = p.read_text()
old = '''                Button(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    onClick = { game.newGame(game.difficulty) },
                    colors = ButtonDefaults.buttonColors(containerColor = yamonePrimary(themeMode)),
                    shape = RoundedCornerShape(17.dp)
                ) { Text("다시하기", fontWeight = FontWeight.ExtraBold) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    onClick = onExit,
                    shape = RoundedCornerShape(17.dp)
                ) { Text("그만하기", fontWeight = FontWeight.Bold) }
'''
new = '''                Row(
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
'''
if old not in s:
    raise SystemExit('Sudoku result action anchor not found')
p.write_text(s.replace(old, new, 1))
