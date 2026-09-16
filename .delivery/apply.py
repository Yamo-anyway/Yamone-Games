from pathlib import Path
import hashlib, io, lzma, subprocess, zipfile
root = Path.cwd()
def payload(prefix, expected):
    parts = sorted((root/'.delivery').glob(prefix+'.[0-9][0-9]'))
    if not parts: raise RuntimeError('Missing payload '+prefix)
    data = b''.join(p.read_bytes() for p in parts)
    if hashlib.sha256(data).hexdigest() != expected: raise RuntimeError('Payload integrity mismatch '+prefix)
    return data
patch = lzma.decompress(payload('source', '2fde40e4ebf54570f6ae9efccf5ba9ca359a938b33fccb5268f64a359caa42cc'))
subprocess.run(['git','apply','--check','-'], input=patch, check=True)
subprocess.run(['git','apply','-'], input=patch, check=True)
art = payload('art','ba6891f1a0f2f4a699debdfa941bfb87310ab231f5b4a48f4b7f9c95b1532158')
with zipfile.ZipFile(io.BytesIO(art)) as z:
    for info in z.infolist():
        p = Path(info.filename)
        if p.is_absolute() or '..' in p.parts or not info.filename.startswith('app/src/main/res/drawable-nodpi/') or p.suffix != '.webp':
            raise RuntimeError('Unexpected art path')
        target = root/p; target.parent.mkdir(parents=True,exist_ok=True); target.write_bytes(z.read(info))
# Final review fixes: feedback belongs to the current window; BGM pause must not cut finish chimes.
p = root/'games/arcadecore/src/main/java/com/yamone/games/arcadecore/GameExperience.kt'
s=p.read_text()
def replace_once(old,new):
    global s
    assert s.count(old)==1, old
    s=s.replace(old,new)
replace_once('fun play(sound: GameSound, haptic: Boolean = true)', 'fun play(sound: GameSound, haptic: Boolean = true, feedbackView: View? = null)')
replace_once('view.get()?.takeIf { it.hasWindowFocus() }', '(feedbackView ?: view.get())?.takeIf { it.hasWindowFocus() }')
replace_once('            pauseAudio()\n            if (!foreground || blocked || !settings.music || !maySound())','            pauseAudio(stopEffects = !foreground || blocked || interrupted || !maySound())\n            if (!foreground || blocked || !settings.music || !maySound())')
replace_once('    private fun pauseAudio() {\n        runCatching { if (prepared && player?.isPlaying == true) player?.pause() }\n        soundPool.autoPause()\n    }','    private fun pauseAudio(stopEffects: Boolean = true) {\n        runCatching { if (prepared && player?.isPlaying == true) player?.pause() }\n        if (stopEffects) soundPool.autoPause()\n    }')
p.write_text(s)
p=root/'games/arcadecore/src/main/java/com/yamone/games/arcadecore/ExperienceUi.kt'
s=p.read_text().replace('import androidx.compose.ui.Modifier','import androidx.compose.ui.Modifier\nimport androidx.compose.ui.platform.LocalView')
s=s.replace('    val s = controller.settings','    val feedbackView = LocalView.current\n    val s = controller.settings')
s=s.replace('controller.play(GameSound.COLLECT)','controller.play(GameSound.COLLECT, feedbackView = feedbackView)').replace('controller.play(GameSound.TAP)','controller.play(GameSound.TAP, feedbackView = feedbackView)')
p.write_text(s)
print('Applied verified 0.3.00 source, approved artwork, and review fixes.')
