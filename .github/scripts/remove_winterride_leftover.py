from pathlib import Path

p = Path('app/src/main/java/com/yamone/games/YamoneGamesApp.kt')
s = p.read_text()
line = '        GameListItem("스키 · 보드", "스키·스노보드·트리런으로 설원을 달려요", "⛷", onWinterRide)\n'
if line not in s:
    raise SystemExit('Winter Ride home list leftover not found')
p.write_text(s.replace(line, '', 1))
