from pathlib import Path


def replace_once(path: str, old: str, new: str, label: str):
    p = Path(path)
    s = p.read_text()
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    p.write_text(s.replace(old, new, 1))

APP = "app/src/main/java/com/yamone/games/YamoneGamesApp.kt"
MAIN = "app/src/main/java/com/yamone/games/MainActivity.kt"
BUILD = "app/build.gradle.kts"

replace_once(
    APP,
    "    val barColor = yamonePrimary(themeMode)\n    val selectedColor = yamonePrimaryDark(themeMode).copy(alpha = .24f)",
    "    // Keep the bottom navigation on the same soft mint/pink surface as the rest of the app.\n    val barColor = yamonePrimarySoft(themeMode)\n    val selectedColor = yamonePrimary(themeMode).copy(alpha = .20f)",
    "soft bottom navigation",
)
replace_once(APP, "        shadowElevation = 6.dp\n    ) {\n        Row(\n            Modifier.fillMaxWidth().height(72.dp)", "        shadowElevation = 3.dp\n    ) {\n        Row(\n            Modifier.fillMaxWidth().height(72.dp)", "lighter bottom shadow")
replace_once(
    MAIN,
    "            val navigationBarBackground = yamonePrimary(themeMode)",
    "            val navigationBarBackground = yamonePrimarySoft(themeMode)",
    "soft Android navigation bar",
)
replace_once(BUILD, "        versionCode = 59\n        versionName = \"1.1.0-dev53\"", "        versionCode = 60\n        versionName = \"1.1.0-dev54\"", "version dev54")
