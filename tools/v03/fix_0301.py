from pathlib import Path
root=Path(__file__).resolve().parents[2]
marker=root/'docs/V0.3.01.md'
if marker.exists():
    raise SystemExit(0)
p=root/'games/snowrush/src/main/java/com/yamone/games/snowrush/SnowRushScreen.kt'
s=p.read_text()
bad='''    fun restart() {
        roundBestBefore = best
        newBest = false
        paused = false
        GameFeedback.play("start")
        playerX = 0.5f'''
assert bad in s
p.write_text(s.replace(bad,'''    fun restart() {
        playerX = 0.5f''',1))
p=root/'app/build.gradle.kts';s=p.read_text();assert 'versionName = "0.3.00"' in s
p.write_text(s.replace('versionName = "0.3.00"','versionName = "0.3.01"').replace('?: 30000','?: 30001'))
p=root/'app/src/main/java/com/yamone/games/YamoneGamesApp.kt';p.write_text(p.read_text().replace('야모네 게임 0.3.00','야모네 게임 0.3.01'))
marker.write_text('''# 0.3.01

눈덩이 상태 객체와 화면의 재시작 처리를 분리해 컴파일 오류 수정.
0.3.00 개편의 디자인·소리·진동·랭킹·설정을 유지.
Android emulator 설치 및 화면 순회 검증 추가.
versionCode 30001. 기존 버전으로 데이터 보존 복귀 시 30002 이상으로 빌드.
''')
print('Applied v0.3.01 compile correction')
