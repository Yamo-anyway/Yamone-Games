from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)

app = Path("app/src/main/java/com/yamone/games/YamoneGamesApp.kt")
s = app.read_text()
s = replace_once(
    s,
    "                                    color = if (selected) dark else YamoneMuted\n",
    "                                    color = if (selected) dark else accent.copy(alpha = .82f)\n",
    "bottom nav glyph theme color",
)
s = replace_once(
    s,
    "                            color = if (selected) dark else YamoneMuted\n",
    "                            color = if (selected) dark else dark.copy(alpha = .68f)\n",
    "bottom nav label theme color",
)
app.write_text(s)

build = Path("app/build.gradle.kts")
b = build.read_text()
b = replace_once(b, "versionCode = 56", "versionCode = 57", "version code")
b = replace_once(b, 'versionName = "1.1.0-dev50"', 'versionName = "1.1.0-dev51"', "version name")
build.write_text(b)

print("dev51 theme bar patch applied")
