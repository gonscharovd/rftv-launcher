# RFTV Launcher — единая память проекта

Последняя сверка: 2026-11-14. Источник истины — GitHub; перед следующей работой перепроверяй репозиторий, PR, CI и Release.

## Репозиторий и стек

- Repository: https://github.com/gonscharovd/rftv-launcher
- Main HEAD: `0e7ce1e5b9e8d9bcf5b06a5793249c7ad59466c7`
- Kotlin / Android Views; Gradle 8.9; AGP 8.7.3; JDK 17
- minSdk 24; targetSdk 28; compileSdk 35
- package `com.rftv.launcher`
- Test target: Sber SDX-50U4123B / StarOS 1.103.91 / Android 9 API 28 / ARMv7 / MediaTek MT9632. This is the first physical target, not a universal OEM profile.

## Latest stable Release

- [v0.9.5](https://github.com/gonscharovd/rftv-launcher/releases/tag/v0.9.5), published 2026-11-14
- versionName `0.9.5`, versionCode `17`, package `com.rftv.launcher`
- APK size: 9,837,340 bytes
- APK SHA-256: `be224eaf0d1a916b9a2b37ee09cf2c016ce807e2816d3b1f567fded1eca9304a`
- Permanent signing certificate SHA-256: `22b4f71da5f543e933780f05f8573783ad4718036b5bfc1b36fe258695e30917`
- Release: draft=false, prerelease=false; assets include APK, checksum, metadata and signature report.
- Main Android CI #43616923593 and Publisher #43617178052 succeeded; Publisher verified the artifact before stable publication.
- Physical acceptance on the target TV is not confirmed.

## Completed visual shell

- PR #43: https://github.com/gonscharovd/rftv-launcher/pull/43 — merged at `d218dd744c816e4607e45a054e2c9ba089ceb976`.
- PR #44: https://github.com/gonscharovd/rftv-launcher/pull/44 — merged at `0e7ce1e5b9e8d9bcf5b06a5793249c7ad59466c7`; scopes Publisher workflow_run events to main and requires successful Android CI.
- Five layered Home scenes, 14 offline scene assets, four transparent foreground cut-outs, glass clock shader, Auto/Photo/manual scene selection and existing 20-photo wallpaper mode.
- Shared Liquid Glass materials/tokens, two-column Control Center, Settings parent scroll/focus restoration, unified dark-glass theme for seven existing AlertDialogs, secondary panel/Settings reveal transitions.
- Existing Dock, catalog, TV Manager, screensaver and OTA behavior retained.
- Donor scene source pinned to upstream tvOS Launcher commit `463fa6c2a9b7322c4407f1d00db935ef09d20f4f`; Apache-2.0 LICENSE/NOTICE and attribution included.
- Source tests, Android lint, Debug CI build, signed Release build, package/version/SDK, checksum and permanent certificate checks passed. APK contains 20 wallpaper assets and 14 scene assets; no native libraries.

## Next work

- No pending visual-shell implementation remains in the approved scope.
- Next required step is physical acceptance after the user updates the target TV through OTA.
- Use DEVICE_TESTING.md for the v0.9.5 checklist; do not claim physical acceptance until the user confirms results.
- Fix device-discovered issues in feature branches and PRs. Do not install Debug APK on the TV.

## Boundaries

- No new user-facing sections, features or product-concept changes without separate approval.
- Preserve TV Manager, Diagnostics, screensaver, OTA, settings and user data.
- Never clear data, reset DB/settings, uninstall system packages or perform irreversible device operations.
- Never install Debug APK on the TV. Physical testing uses only a verified signed stable Release through OTA.
- Do not claim physical acceptance without user confirmation.
- Do not replace, recreate, lose or disclose the permanent Release signing key.
- Work in feature branches and PRs; do not modify main directly unless unavoidable.
- Merge only after required checks pass. The user has authorized normal PR creation, merge and verified stable Release publication; no repeat permission is needed for those steps.

## Project documents

- [README](../README.md)
- [ROADMAP](../ROADMAP.md)
- [CHANGELOG](../CHANGELOG.md)
- [DEVICE_TESTING](../DEVICE_TESTING.md)
- [Third-party notices](../THIRD_PARTY_NOTICES.md)
- [Donor analysis](../DONOR_ANALYSIS.md)
- [Iteration 4 acceptance](ITERATION_4_ACCEPTANCE.md)
