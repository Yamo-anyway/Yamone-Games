from pathlib import Path
r=Path(__file__).resolve().parents[2]
a=r/'app/src/main/java/com/yamone/games'
t=(a/'YamoneGamesApp.kt').read_text()
assert 'showInterstitialTestAd' not in t
assert 'AdMobTestInterstitial(' not in t
start=t[t.index('val startGameAfterNickname:'):t.index('val requestGameStart:')]
assert 'screenName = target.name' in start
assert 'adAccessStore' not in start and 'ConnectivityManager' not in start
assert t.count('GlobalTopBanner(')==1
home=(a/'RedesignUi.kt').read_text().split('internal fun V3HomeScreen',1)[1].split('private fun V3GameArt',1)[0]
for old in ['우리의 놀이터','내 기록 ›','AdFreeTimeCard(']: assert old not in home
assert 'MainTopBar(' not in t
assert '나만의 작은 놀이터' not in t
header=(a/'AdFreeHeader.kt').read_text()
assert 'if (!permanentAdFree)' in header and 'RewardPlayGlyph' in header
assert 'onAdDetails()' in header
board=(r/'games/sudoku/src/main/java/com/yamone/games/sudoku/ui/SudokuApp.kt').read_text()
assert '.border(2.5.dp, dark, RoundedCornerShape(7.dp))' in board
assert 'for (i in 1..8)' in board
assert 'SudokuGuidance.hints' in board
policy=(a/'AdDisplayPolicy.kt').read_text()
assert 'requireInterstitial(): Boolean = false' in policy
print('PASS: home cleanup, single global banner, no game gate, permanent ownership and board frame')
