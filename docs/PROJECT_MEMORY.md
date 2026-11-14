# RFTV Launcher — единая память проекта

Последняя сверка: 2026-11-14 21:26 +05:00. Источник истины — GitHub; перед каждой новой работой перепроверяй репозиторий, PR, CI и Release.

## Репозиторий и стек

- Repository: https://github.com/gonscharovd/rftv-launcher
- Main HEAD: `4981dce3629592ac1edb243ba2e6c7cae2fe23fa`
- Kotlin / Android Views; Gradle 8.9; AGP 8.7.3; JDK 17
- minSdk 24; targetSdk 28; compileSdk 35
- package `com.rftv.launcher`
- Test target: Sber SDX-50U4123B / StarOS 1.103.91 / Android 9 API 28 / ARMv7 / MediaTek MT9632. This is the first physical target, not a universal OEM profile.

## Latest stable Release

- [v0.9.4](https://github.com/gonscharovd/rftv-launcher/releases/tag/v0.9.4), published 2026-11-10
- versionName `0.9.4`, versionCode `16`, package `com.rftv.launcher`
- APK size: 7,993,855 bytes
- APK SHA-256: `398ec55a1ce62cccde33afccc7827356016c145c1e097e745b4551457a1174c7`
- Permanent signing certificate SHA-256: `22b4f71da5f543e933780f05f8573783ad4718036b5bfc1b36fe258695e30917`
- Release: draft=false, prerelease=false; assets include app-release.apk, checksum, metadata and signature report.
- Physical acceptance on the target TV is not confirmed.

## Current approved work — visual shell

- Draft PR: https://github.com/gonscharovd/rftv-launcher/pull/43
- Branch: `feature/visual-shell-execution`
- Original PR head: `7f65d8a0c4acda26e416084ffdc065f23fb39402`; follow-up commit: `59545758caca9afbaec256ab7e221c5a32b43b73`
- PR scope includes centralized glass tokens/materials, two-column Control Center, Home layered scene renderer, five scenes and 14 offline scene assets, glass clock shader, Auto/Photo/manual scene selection, Settings parent scroll/focus restoration, and regression tests.
- Upstream scene imagery comes from tvOS Launcher; Apache-2.0 LICENSE/NOTICE and attribution are included. Existing 20 Unsplash wallpapers remain.
- Current feature CI run #42884493320 passed source tests, wallpaper/scene packaging, lint and Debug build. Release verification was skipped because this is a feature branch; no Release is certified by this run.
- A follow-up teardown fix detaches scene ImageViews and shader references before recycling scene bitmaps.

## Candidate version

- PR branch is prepared for `0.9.5` / versionCode `17`, which is required to upgrade stable `0.9.4` / code `16`.
- Do not publish until PR checks, signed APK artifact, package/version metadata, ABI, file SHA-256 and permanent signing certificate all verify.
- If the permanent signing key is unavailable or the certificate differs, stop publication. Never create or substitute a key.

## Remaining approved visual-shell work

1. Unify the existing AlertDialogs and transient popups with the shared glass style without changing their actions.
2. Finish open/close transitions across Home overlays, panels and Settings.
3. Refine Dock and Settings visual details against tvOS while preserving all RFTV controls and features.
4. Complete DPAD/OK/Back, initial focus, focus restoration and scroll regression review.
5. Update acceptance documentation, run current-head CI, merge PR #43 when all checks pass, then verify and publish v0.9.5.

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
