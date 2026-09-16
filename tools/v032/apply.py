"""Materialize reviewed v0.3.02 source once; verify every input and output hash first."""
from pathlib import Path
import hashlib, json, lzma, base64
root = Path(__file__).resolve().parents[2]
if (root / 'docs/V0.3.02.md').exists():
    print('v0.3.02 source is already materialized')
    raise SystemExit(0)
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
