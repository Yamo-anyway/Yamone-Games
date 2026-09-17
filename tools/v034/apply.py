"""Materialize the tested stage-two changes once; fail closed on corrupt transport."""
from pathlib import Path
import base64, gzip, hashlib, subprocess, tempfile
ROOT=Path(__file__).resolve().parents[2]
marker=ROOT/'docs/V034_MATERIALIZED'
if marker.exists():
    print('v0.3.04 is already materialized');raise SystemExit(0)
patch=gzip.decompress(base64.b64decode(''.join((ROOT/f'tools/v034/patch.part{i}').read_text().strip() for i in range(4)),validate=True))
assert hashlib.sha256(patch).hexdigest()=='f02670a387615cf8520adc8741a1ec353b01e515313053aac5d644772af241f3','Source patch checksum mismatch'
art=base64.b64decode(''.join((ROOT/f'tools/v034/background.part{i}').read_text().strip() for i in range(6)),validate=True)
assert hashlib.sha256(art).hexdigest()=='23dd47a480667ef4632f163ba59d821280bf65e41d4e9e613eb36953a519a669','Artwork checksum mismatch'
assert art[:4]==b'RIFF' and art[8:12]==b'WEBP'
with tempfile.NamedTemporaryFile(suffix='.patch') as f:
    f.write(patch);f.flush()
    subprocess.run(['git','apply','--check',f.name],cwd=ROOT,check=True)
    subprocess.run(['git','apply',f.name],cwd=ROOT,check=True)
path=ROOT/'games/snowrush/src/main/res/drawable-nodpi/snow_aurora_304.webp'
path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(art)
marker.write_text('0.3.04: verified source and static background image\n')
print('v0.3.04 source and artwork materialized successfully')
