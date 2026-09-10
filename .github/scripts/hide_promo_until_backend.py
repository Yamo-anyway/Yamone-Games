from pathlib import Path
import re

p = Path('app/src/main/java/com/yamone/games/AdExperienceUi.kt')
s = p.read_text()

pattern = re.compile(
    r'''\n                HorizontalDivider\(color = yamonePrimaryLine\(themeMode\)\)\n                Text\("프로모션 코드".*?\n                \}\n            \}\n        \},\n        confirmButton''',
    re.S,
)
match = pattern.search(s)
if not match:
    raise SystemExit('promotion UI block not found')
block = match.group(0)
body, suffix = block.rsplit('\n            }\n        },\n        confirmButton', 1)
wrapped = '\n                if (PROMOTION_REDEMPTION_ENABLED) {' + body.replace('\n                ', '\n                    ', 1) + '\n                }\n            }\n        },\n        confirmButton' + suffix
s = s[:match.start()] + wrapped + s[match.end():]
p.write_text(s)
