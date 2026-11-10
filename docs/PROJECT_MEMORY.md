# RFTV Launcher — единая память проекта

Последняя сверка: 2026-11-10 22:44 +05:00. GitHub `main` HEAD: `2038bd822961772773affae63aa7119f4c1fa889`.

## Источник истины и порядок чтения

GitHub repository — источник истины для исходников, PR, workflows, Release и актуальных документов. Перед работой сверяй GitHub заново; этот файл — переносимый handoff, не замена коду.

- Repository: https://github.com/gonscharovd/rftv-launcher
- Main: `main`
- README: [../README.md](../README.md)
- Roadmap: [../ROADMAP.md](../ROADMAP.md)
- Changelog: [../CHANGELOG.md](../CHANGELOG.md)
- Device acceptance: [../DEVICE_TESTING.md](../DEVICE_TESTING.md)
- Third-party notices: [../THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)
- Donor analysis: [../DONOR_ANALYSIS.md](../DONOR_ANALYSIS.md)
- Iteration 4 checklist: [ITERATION_4_ACCEPTANCE.md](ITERATION_4_ACCEPTANCE.md)

## Product and stack

RFTV is a native Android TV launcher using Kotlin and Android Views. It is not a firmware replacement or a universal OEM device manager.

- Package: `com.rftv.launcher`
- Kotlin / Android Views; no Compose
- Gradle 8.9, AGP 8.7.3, JDK 17
- minSdk 24, targetSdk 28, compileSdk 35
- Sber SDX-50U4123B / StarOS 1.103.91 / Android 9 API 28 / ARMv7 / MediaTek MT9632 is the first physical test device, not a universal compatibility guarantee.

## Stable Release

- Latest stable: [v0.9.3](https://github.com/gonscharovd/rftv-launcher/releases/tag/v0.9.3)
- versionCode 15, versionName 0.9.3, package `com.rftv.launcher`
- APK size 7,990,843 bytes
- APK SHA-256: `ce7bfcd6639ab9df57b1982518a867089ecd99bd0fac339dda3cb5840f11240e`
- Permanent signing certificate SHA-256: `22b4f71da5f543e933780f05f8573783ad4718036b5bfc1b36fe258695e30917`
- Main CI #42865839755: success
- Publisher #42866314971: success
- Stable Release, draft=false, prerelease=false; assets: `app-release.apk`, `app-release.apk.sha256`, `apk-metadata.txt`, `signature.txt`.
- Physical acceptance on the Sber TV is not confirmed.

## Current work — Iteration 4

Feature branch: `feature/iteration-4-catalog-focus-glass`.

### 4A — code fix committed; CI pending

Root cause in `MainActivity.kt`:
1. `showHome()` attempts to find the All Apps Dock tile by tag `rftv-all-apps-trigger` when `returnFocusToCatalogButton` is set.
2. The Dock tile builder did not assign that tag. The lookup returned null and the code skipped the normal first-app focus fallback.
3. When an app was launched from the catalog, `launchApp()` did not record the origin and `onResume()` left the catalog as the active panel rather than rebuilding Home.

Fix committed:
- Stable tag assigned to the All Apps Dock tile.
- Back and Close paths request focus on that tile with fallback to first app.
- Catalog-origin app launches are tracked; on return, Home is rebuilt and focus is restored.
- Added pure `CatalogReturnPolicy` and unit regression tests.
- Candidate version: 0.9.4 / versionCode 16. Not released.

Current CI for branch head `f009ba879141b5d5a85cd71c9bafd601cc1fe8b3`: run [#42868608153](https://github.com/gonscharovd/rftv-launcher/actions/runs/42868608153), last observed in progress. Check actual status before relying on this snapshot.

### 4B — not implemented

Responsive Dock width based on favorite count plus All Apps, centered and content-sized, with min width around three tiles and max width below full screen. Keep existing squircle metrics and favorites behavior.

### 4C — not implemented

Audit and unify real frosted-glass material on Dock, Control Center, Settings and catalog. Current code audit:
- Dock: flat translucent gradient from `RftvDesignSystem.dockSurface()`; no blurred image layer.
- Control Center: real low-resolution screenshot snapshot, blurred with `GlassBackdrop` radius 7 / 3 passes, then dark scrim; glass panel itself is a gradient.
- Settings: cached blur of the selected wallpaper via `addWallpaperGlassBackdrop()`, not a fresh screenshot of the current Home tree; shell is a gradient.
- App catalog: real low-resolution screenshot snapshot via `captureBackdropSnapshot()`, blurred with `GlassBackdrop` radius 7 / 3 passes; dark scrim and panel gradient.
- Need common material parameters and a clipped blurred layer on each actual surface. Keep CPU cost bounded and do not add user-facing transparency controls.

## Important document issues already corrected on the Iteration 4 branch

- README stable baseline updated from v0.9.1 to v0.9.3.
- ROADMAP current status refreshed; old sections remain historical.
- CHANGELOG now records verified v0.9.3 and 0.9.4 candidate.
- DEVICE_TESTING updated to v0.9.3 and obsolete instruction to install Debug APK on TV replaced with the rule that Debug is CI/diagnostics only.
- Added the 4A physical acceptance checklist.
- The old open PR #15 `docs/project-memory-system` is stale, based on an old main SHA and currently not mergeable. Do not merge it as-is; this new memory file supersedes its outdated snapshot until its useful content is reconciled.

## Working rules

- Work in feature branches and PRs; do not push directly to main.
- Fixes, optimizations, compatibility, tests and documentation inside the approved scope can be done autonomously.
- New user-facing features, sections, scenarios or concept changes require owner approval.
- Do not merge this iteration's PR without explicit approval for this iteration.
- Do not publish a candidate unless CI, signed APK identity, persistent certificate, SHA-256, size and uploaded Release asset all verify.
- Never replace or expose the persistent signing key.
- Never install Debug APK on the TV. Physical acceptance uses only signed stable Release via OTA.
- Do not claim physical testing without owner confirmation.
- Do not touch TV Manager, Diagnostics, screensaver, photo scenes or OTA pipeline unless the owner explicitly scopes that work.
