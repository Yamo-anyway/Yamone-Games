from pathlib import Path
import runpy

patch = Path('.github/scripts/apply-polish-refresh.py')
script = patch.read_text()
old = '    3,\n    "hide ad timer cards during dev bypass",\n)'
new = '    2,\n    "hide ad timer cards during dev bypass",\n)'
if old not in script:
    raise SystemExit('ad-card expectation anchor not found')
patch.write_text(script.replace(old, new, 1))

runpy.run_path(str(patch), run_name='__main__')

app = Path('app/src/main/java/com/yamone/games/YamoneGamesApp.kt')
text = app.read_text()
old_settings = '''        if (!adRemoved) {
            Text("광고", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
            AdFreeTimeCard(themeMode, adFreeUntilMillis, onAdAccess)
        }
'''
new_settings = '''        if (!adRemoved && !DEV_AD_TIMER_BYPASS) {
            Text("광고", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)
            AdFreeTimeCard(themeMode, adFreeUntilMillis, onAdAccess)
        }
'''
if old_settings not in text:
    raise SystemExit('settings ad timer block not found')
app.write_text(text.replace(old_settings, new_settings, 1))

print('UI polish wrapper completed')
