from pathlib import Path

p = Path('app/src/main/java/com/yamone/games/YamoneGamesApp.kt')
s = p.read_text()

old = '    val context = LocalContext.current.applicationContext\n'
new = '''    val hostContext = LocalContext.current\n    val context = hostContext.applicationContext\n    val privacyOptionsRequired by YamonePrivacy.privacyOptionsRequired.collectAsState()\n'''
if old not in s:
    raise SystemExit('context anchor not found')
s = s.replace(old, new, 1)

old = '''                            adRemoved = adRemoved,\n                            adFreeUntilMillis = adFreeUntilMillis,\n                            onAdAccess = { showAdDetails = true },\n                            onOnlineRankingEnabledChange = { enabled ->\n'''
new = '''                            adRemoved = adRemoved,\n                            adFreeUntilMillis = adFreeUntilMillis,\n                            onAdAccess = { showAdDetails = true },\n                            privacyOptionsRequired = privacyOptionsRequired,\n                            onPrivacyOptions = {\n                                hostContext.findActivity()?.let { activity ->\n                                    YamonePrivacy.showPrivacyOptions(activity) { adRevision++ }\n                                }\n                            },\n                            onOnlineRankingEnabledChange = { enabled ->\n'''
if old not in s:
    raise SystemExit('SettingsScreen call anchor not found')
s = s.replace(old, new, 1)

old = '''    adRemoved: Boolean,\n    adFreeUntilMillis: Long,\n    onAdAccess: () -> Unit,\n    onOnlineRankingEnabledChange: (Boolean) -> Unit,\n'''
new = '''    adRemoved: Boolean,\n    adFreeUntilMillis: Long,\n    onAdAccess: () -> Unit,\n    privacyOptionsRequired: Boolean,\n    onPrivacyOptions: () -> Unit,\n    onOnlineRankingEnabledChange: (Boolean) -> Unit,\n'''
if old not in s:
    raise SystemExit('SettingsScreen signature anchor not found')
s = s.replace(old, new, 1)

old = '''        if (!adRemoved) {\n            Text("광고", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)\n            AdFreeTimeCard(themeMode, adFreeUntilMillis, onAdAccess)\n        }\n\n        Text("닉네임", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)\n'''
new = '''        if (!adRemoved) {\n            Text("광고", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)\n            AdFreeTimeCard(themeMode, adFreeUntilMillis, onAdAccess)\n        }\n\n        if (privacyOptionsRequired) {\n            OutlinedButton(\n                onClick = onPrivacyOptions,\n                modifier = Modifier.fillMaxWidth(),\n                shape = RoundedCornerShape(18.dp),\n                border = BorderStroke(1.dp, yamonePrimaryLine(themeMode))\n            ) {\n                Text(\n                    "광고 개인정보 설정",\n                    fontWeight = FontWeight.Bold,\n                    color = yamonePrimaryDark(themeMode)\n                )\n            }\n        }\n\n        Text("닉네임", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = YamoneInk)\n'''
if old not in s:
    raise SystemExit('SettingsScreen ad UI anchor not found')
s = s.replace(old, new, 1)

p.write_text(s)
