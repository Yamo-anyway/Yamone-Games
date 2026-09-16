# Version history

## 0.2.00 — preserved baseline (2026-09-16)
- Original source commit: 6770008244349cb6a1e3c43e43ac0a70113a38cd.
- Untouched original: archive/pre-0.3-original.
- Baseline branch: release/0.2.00.
- Game behavior, records, ad and ranking policies are unchanged.
- Only version metadata and a versioned build/archive workflow were added.
- Future redesign starts at 0.3.00; subsequent releases use 0.3.01, 0.3.02, etc.

## Rollback
Rebuild release/0.2.00 with the SAME signing key and a versionCode greater than the installed build. Run the Versioned Yamone Games workflow on release/0.2.00 with version_code, or `gradle :app:assembleDebug -PyamoneVersionCode=40000`. Do not uninstall to preserve local records. Back up data before rollback; source rollback is not a guarantee of runtime data compatibility.

## 0.3.00 — design / audio / ranking redesign
- Version code 30000; next presentation versions 0.3.01, 0.3.02, etc.
- Four existing games redesigned; fish keeps separate normal/time-attack records.
- Shared offline music/effects/haptics and independent saved controls.
- Background interruption pause, game-specific rankings and local Sudoku statistics.
- Existing record schema and application ID retained; rollback instructions above apply.
- Full scope and manual QA checklist: docs/0.3.00-RELEASE.md.
