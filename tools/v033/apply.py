"""Apply the reviewed v0.3.03 patch exactly once, with SHA-256 verification."""
from pathlib import Path
import base64
import gzip
import hashlib
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
MARKER = ROOT / 'docs/V033_MATERIALIZED'
if MARKER.exists():
    print('v0.3.03 sources already materialized')
    raise SystemExit(0)

# Restore two known transport transcription errors; full patch digest remains mandatory.
last = ROOT / 'tools/v033/patch.part2'
text = last.read_text().replace('J7NLZfzutFzutFzbUp', 'J7NLZfzutFzbUp').replace('1cX6swuwPdq', '1cX6wuwPdq')
last.write_text(text)
encoded = ''.join((ROOT / f'tools/v033/patch.part{i}').read_text().strip() for i in range(3))
patch = gzip.decompress(base64.b64decode(encoded, validate=True))
expected = 'abe35b6ae382a97bd759079b5453e60b8fe060287ee002c5bbde9a61d1142dc5'
if hashlib.sha256(patch).hexdigest() != expected:
    raise RuntimeError('Reviewed source patch checksum mismatch; refusing to change source')
with tempfile.NamedTemporaryFile(suffix='.patch') as handle:
    handle.write(patch)
    handle.flush()
    subprocess.run(['git', 'apply', '--check', handle.name], cwd=ROOT, check=True)
    subprocess.run(['git', 'apply', handle.name], cwd=ROOT, check=True)
MARKER.parent.mkdir(parents=True, exist_ok=True)
MARKER.write_text('v0.3.03 source patch SHA256: ' + expected + '\n')
print('Applied verified v0.3.03 source patch')
