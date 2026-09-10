from pathlib import Path


def patch(path, old, new, label):
    p = Path(path)
    s = p.read_text()
    if old not in s:
        raise SystemExit(f"missing anchor: {label}")
    p.write_text(s.replace(old, new, 1))

# Ice Jump start button: wide, standardized.
patch(
    'games/icejump/src/main/java/com/yamone/games/icejump/IceJumpScreen.kt',
    'modifier = Modifier.align(Alignment.Center),\n                    shape = RoundedCornerShape(18.dp),',
    'modifier = Modifier.align(Alignment.Center).width(220.dp).height(50.dp),\n                    shape = RoundedCornerShape(17.dp),',
    'ice start width'
)

# Fish Munch mode buttons: same standard width.
patch(
    'games/fishmunch/src/main/java/com/yamone/games/fishmunch/FishMunchScreen.kt',
    'modifier = Modifier.align(Alignment.Center).width(210.dp),',
    'modifier = Modifier.align(Alignment.Center).width(220.dp),',
    'fish mode width'
)

# Snow Rush start button: same standard width and height.
patch(
    'games/snowrush/src/main/java/com/yamone/games/snowrush/SnowRushScreen.kt',
    'modifier = Modifier.align(Alignment.Center),\n        colors = ButtonDefaults.buttonColors(containerColor = primary),',
    'modifier = Modifier.align(Alignment.Center).width(220.dp).height(50.dp),\n        colors = ButtonDefaults.buttonColors(containerColor = primary),',
    'snow start width'
)

# Validation version.
p = Path('app/build.gradle.kts')
s = p.read_text()
if 'versionCode = 52' not in s or 'versionName = "1.1.0-dev46"' not in s:
    raise SystemExit('version anchor missing')
s = s.replace('versionCode = 52', 'versionCode = 53', 1)
s = s.replace('versionName = "1.1.0-dev46"', 'versionName = "1.1.0-dev47"', 1)
p.write_text(s)
