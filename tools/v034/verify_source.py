from pathlib import Path
import hashlib
r=Path(__file__).resolve().parents[2]
s=(r/'games/snowrush/src/main/java/com/yamone/games/snowrush/SnowRushScreen.kt').read_text()
a=(r/'games/snowrush/src/main/java/com/yamone/games/snowrush/SnowRushArt.kt').read_text()
e=(r/'games/snowrush/src/main/java/com/yamone/games/snowrush/SnowRushEngine.kt').read_text()
checks=[
 'SnowPaintedBackdrop(Modifier.matchParentSize())' in s,
 'ArcadeBackdrop(' not in s,
 'paintSnow304Hazard(' in s,
 'paintDodgeBounds()' in s,
 'configureViewport(maxWidth.value, maxHeight.value, playerSize.value)' in s,
 'R.drawable.snow_aurora_304' in a,
 'Image(painterResource(' in a,
 'h.vy = (h.vy +' not in e,
 'h.vx *= h.lateralDrag' in e,
 'const val PROTECTION_MS = 3_000' in e,
 'versionName = "0.3.04"' in (r/'app/build.gradle.kts').read_text(),
 hashlib.sha256((r/'games/snowrush/src/main/res/drawable-nodpi/snow_aurora_304.webp').read_bytes()).hexdigest()=='23dd47a480667ef4632f163ba59d821280bf65e41d4e9e613eb36953a519a669'
]
assert all(checks),checks
print(f'Passed {len(checks)} stage-two source/artifact checks')
