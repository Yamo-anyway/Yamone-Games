from pathlib import Path

path = Path("app/src/main/java/com/yamone/games/OnlineRankingUi.kt")
text = path.read_text()
text = text.replace("순위을", "순위를")
text = text.replace("등록된 순위이 없어요", "등록된 순위가 없어요")
path.write_text(text)
print("dev54 ranking Korean copy fixed")
