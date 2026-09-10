from pathlib import Path

files = [
    'games/icejump/src/main/java/com/yamone/games/icejump/IceJumpScreen.kt',
    'games/fishmunch/src/main/java/com/yamone/games/fishmunch/FishMunchScreen.kt',
    'games/snowrush/src/main/java/com/yamone/games/snowrush/SnowRushScreen.kt',
    'games/sudoku/src/main/java/com/yamone/games/sudoku/ui/SudokuApp.kt',
]

for path in files:
    p = Path(path)
    s = p.read_text()
    if 'import androidx.compose.ui.graphics.StrokeCap' not in s:
        s = s.replace(
            'import androidx.compose.ui.graphics.Color\n',
            'import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.StrokeCap\n',
            1,
        )

    if 'SudokuApp.kt' in path:
        old = '''            Surface(onClick = onBack, color = Color.Transparent) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Text("←", fontSize = 30.sp, fontWeight = FontWeight.Black, color = yamonePrimaryDark(themeMode))
                }
            }
'''
        color = 'yamonePrimaryDark(themeMode)'
        click = 'onBack'
    else:
        old = '''            Surface(onClick = ::requestExit, color = Color.Transparent) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Text("←", fontSize = 30.sp, fontWeight = FontWeight.Black, color = primaryDark)
                }
            }
'''
        color = 'primaryDark'
        click = '::requestExit'

    new = f'''            Surface(onClick = {click}, color = Color.Transparent) {{
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {{
                    Canvas(Modifier.size(30.dp)) {{
                        val stroke = 4.dp.toPx()
                        val tip = Offset(size.width * 0.16f, size.height * 0.50f)
                        val tail = Offset(size.width * 0.84f, size.height * 0.50f)
                        drawLine({color}, tail, tip, strokeWidth = stroke, cap = StrokeCap.Round)
                        drawLine({color}, tip, Offset(size.width * 0.43f, size.height * 0.22f), strokeWidth = stroke, cap = StrokeCap.Round)
                        drawLine({color}, tip, Offset(size.width * 0.43f, size.height * 0.78f), strokeWidth = stroke, cap = StrokeCap.Round)
                    }}
                }}
            }}
'''

    if old not in s:
        raise SystemExit(f'missing arrow anchor: {path}')
    p.write_text(s.replace(old, new, 1))
