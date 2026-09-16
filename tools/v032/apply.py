"""Materialize reviewed v0.3.02 sources, then apply the device-review corrections."""
from pathlib import Path
import hashlib, json, lzma, base64
root = Path(__file__).resolve().parents[2]
if not (root / 'docs/V0.3.02.md').exists():
    parts = sorted(Path(__file__).parent.glob('changes.b64.*'))
    assert len(parts) == 4
    payload = base64.b64decode(''.join(p.read_text().strip() for p in parts), validate=True)
    assert hashlib.sha256(payload).hexdigest() == '8cc815d3c56ee150387e4d442061b88c21dea94c82c22394c010ddfd233050cd'
    outputs = []
    for item in json.loads(lzma.decompress(payload)):
        relative = Path(item['path'])
        assert not relative.is_absolute() and '..' not in relative.parts
        assert relative.parts[0] in ('app', 'games', 'cloudflare', 'docs', 'tools')
        p = root / relative
        before = p.read_bytes() if p.exists() else b''
        assert hashlib.sha256(before).hexdigest() == item['before'], f'Changed source: {relative}'
        lines = before.decode().splitlines(keepends=True)
        for start, end, text in reversed(item['edits']):
            lines[start:end] = [text]
        after = ''.join(lines).encode()
        assert hashlib.sha256(after).hexdigest() == item['after'], f'Output hash mismatch: {relative}'
        outputs.append((p, after))
    for p, data in outputs:
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(data)
    print(f'Materialized {len(outputs)} reviewed source files')

marker = root / 'docs/V0.3.02-device-review.md'
if marker.exists():
    print('v0.3.02 device review already materialized')
    raise SystemExit(0)

p = root / 'games/snowrush/src/main/java/com/yamone/games/snowrush/SnowRushScreen.kt'
s = p.read_text()
before = '            Text("게임과 생존 시간은 멈춰 있어요.")'
assert before in s
s = s.replace(before, '''            Text(preciseDuration(elapsed), fontSize=26.sp, fontWeight=FontWeight.Black, color=primaryDark)
            Spacer(Modifier.height(8.dp))
            Text("게임과 생존 시간은 멈춰 있어요.")''', 1)
p.write_text(s)

p = root / 'tools/v032/smoke.py'
s = p.read_text()
a = '''        home_game(title);screenshot(f'{index:02d}-scenery')
        p=center(node('Ⅱ'));b=center(node(start));tapxy(*b);time.sleep(.15);tapxy(*p);time.sleep(.4)'''
b = '''        home_game(title);p=center(node('Ⅱ'));b=center(node(start));screenshot(f'{index:02d}-scenery')
        tapxy(*b);time.sleep(.15);tapxy(*p);time.sleep(.4)'''
assert a in s;s=s.replace(a,b)
a = '''    home_game('눈덩이 러시');screenshot('06-snow-instructions')
    p=center(node('일시정지'));b=center(node('시작하기'))'''
b = '''    home_game('눈덩이 러시')
    p=center(node('일시정지'));b=center(node('시작하기'));screenshot('06-snow-instructions')'''
assert a in s;s=s.replace(a,b)
a = '''    tap('계속하기');time.sleep(4.6);screenshot('08-snow-active')
    # Wait for an ordinary collision; never publish the emulator record.
    time.sleep(10);screenshot('09-snow-result')'''
b = '''    screenshot('07b-snow-paused-clock')
    tap('계속하기');time.sleep(1.5)
    # A real swipe keeps the avatar out of the first approach and shows emitted fragments.
    w,h=map(int,re.findall(r'(\\d+)x(\\d+)',shell('wm','size'))[-1])
    shell('input','swipe',str(w//2),str(int(h*.73)),str(int(w*.04)),str(int(h*.73)),'500')
    time.sleep(3.5);screenshot('08-snow-active')
    # Wait for an ordinary collision; never publish the emulator record.
    time.sleep(10);screenshot('09-snow-result')'''
assert a in s;s=s.replace(a,b)
p.write_text(s)
marker.write_text('''# 0.3.02 device review

- The Android modal accessibility tree hides the background timer. Display the exact paused time inside the dialog so both players and the automated test can read it.
- Capture instruction/scenery screens after their controls are present, not during asynchronous navigation.
- Exercise an actual left swipe to survive beyond the initial approach and capture snowball fragments.
- APK version remains 0.3.02; this correction precedes delivery. See CI results for verified coverage.
''')
print('Applied paused-time UI and device-test improvements')
