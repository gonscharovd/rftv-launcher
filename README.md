# RFTV Launcher

**Current stable release: [0.9.3](https://github.com/gonscharovd/rftv-launcher/releases/tag/v0.9.3) · versionCode 15.** Signed APK SHA-256: `ce7bfcd6639ab9df57b1982518a867089ecd99bd0fac339dda3cb5840f11240e`. Release signing certificate SHA-256: `22b4f71da5f543e933780f05f8573783ad4718036b5bfc1b36fe258695e30917`. Physical acceptance on the target Sber TV is still pending; see [DEVICE_TESTING.md](DEVICE_TESTING.md). Portable context: [docs/PROJECT_MEMORY.md](docs/PROJECT_MEMORY.md).

A lightweight Android TV launcher with a tvOS-inspired Liquid Glass visual direction.

## Owner-approved product target — 2026-11-10

The primary product is a lightweight, attractive launcher for a broad range of Android TV devices. The Sber SDX-50U4123B (Android 9/API 28, MT9632, ARMv7, approximately 1.4 GB RAM) remains the main physical test target, not the only supported device.

The intended Home composition is a photographic scene with large iOS-style clock typography **between two image layers**: a background photograph plus a separately cut-out transparent foreground element that overlaps part of the clock. Start with 3–5 complete layered scenes; the 20-photo flat wallpaper catalogue in v0.9.0 is existing shipped content, not the final depth-effect design.

Weather belongs beside the clock. The city and provider configuration must be changeable; neither is to remain permanently hard-coded. The Dock contains favourites and an All Apps entry. All Apps opens an app grid over a full-screen blurred backdrop. The gear opens Control Center over a full-screen blurred backdrop. Settings use tvOS-style left navigation and a right content pane, preserving focus when entering and leaving nested screens.

The screensaver is available from Control Center and, where supported by firmware, as Android DreamService. It shows clock/date, weather, CBR currency rates and Russia/world news. Clock position must drift slowly to reduce burn-in. Any remote key must dismiss the manual and system screensaver paths where the platform delivers key events.

TV Manager is retained as an existing, lower-priority subsystem. Do not expand it while the primary launcher work is in progress. Its future safety requirements are documented in [TV_MANAGER.md](TV_MANAGER.md).

## Implemented features (v0.9.1 shipped state)

- Native Kotlin Android Views (no Compose and no third-party UI dependencies).
- Home screen with a large centered clock/date over a full-screen photograph, a compact weather status pill, a five-day forecast overlay, and the Control Center entry point. The shipped clock is not yet interleaved between a separate background and transparent foreground layer.
- Single-row content-sized glass Dock capsule with favorite apps, dedicated All Apps entry, centered responsive width and focused-app label; DPAD Down does not expand the dock.
- Dock and catalog refresh automatically after app installation, removal or replacement, without restarting the launcher; focus and favorite ordering are preserved.
- 10 real daytime and 10 real nighttime landscape photographs from Unsplash, bundled in the APK for reliable first-run and offline display. These are flat photos, not the planned 3–5 layered depth scenes.
- Separate day/night wallpaper selection and carousel modes, with a configurable 1–60 minute interval.
- Wallpaper photographs are validated and packaged at build time, then restored into app-private cache; TV runtime access to Unsplash is not required.
- Floating right-side dark-glass Control Center with vector icons, grouped Wi-Fi/Bluetooth, theme, RAM, screensaver and settings actions; it stays clear of the Dock.
- Dock width and height controls saved locally.
- TV Cleaner lists user-installed apps and delegates uninstall to Android's confirmation screen.
- Existing RFTV TV Manager code provides package inventory/analysis and Shizuku-backed reversible package operations. Further TV Manager development is deferred behind the launcher priorities; physical Shizuku validation has not been performed.
- Remote-control focus states and large TV-friendly controls.\n- All Apps uses a responsive icon grid over a full-screen blurred Home snapshot; Control Center also blurs the full-screen snapshot while remaining a right-side panel.\n- Settings include a local Diagnostics screen with the last 10 All Apps/Control Center openings, capture/blur/open timings, bitmap sizes, Java/native heap deltas and device/build details. Data is held in a process-memory ring buffer only; it is not written to disk and requires no new permissions.
- Manual screensaver dashboard uses the active Home wallpaper with a blurred backdrop, clock, CBR currency rates, current weather, and separate Russia/World RSS columns. Android DreamService also uses the active Home wallpaper. Burn-in prevention by slow clock movement is not implemented.

## Target device

Primary test target:
- Sber SDX-50U4123B / Salute TV (StarOS)
- Android 9 (API 28), firmware 1.103.91-b32273
- MediaTek MT9632, ARMv7 (armeabi-v7a)
- Approximately 1.36 GB RAM

Build configuration:
- Minimum Android: API 24
- Target Android: API 28
- Compile SDK: 35
- JDK: 17
- Android Gradle Plugin: 8.7.3
- Kotlin: 2.0.21

## Build

Open the project in Android Studio with JDK 17 and Android SDK platform 35, or use Gradle 8.9:

```bash
gradle --no-daemon assembleDebug
```

Debug APK: app/build/outputs/apk/debug/app-debug.apk

Stable Release APKs are published through [GitHub Releases](https://github.com/gonscharovd/rftv-launcher/releases). Stable v0.9.3 uses versionCode 15. Iteration 4A candidate v0.9.4 / versionCode 16 is being verified; it is not a published Release. Use the in-app OTA updater for upgrades; it verifies SHA-256, package identity, increasing versionCode, ARMv7 compatibility when native libraries are present, and the installed signing certificate before opening Android's installer.

GitHub Actions builds pushes to main and feature/** branches and pull requests targeting main.

## Important prototype limitations

- The manual screensaver and Android DreamService use the shared Open-Meteo → MET Norway → wttr.in weather fallback for Chelyabinsk, official Bank of Russia USD/EUR rates, and RSS feeds from RIA Novosti and Google News (Russian-language world feed). The city and feeds are currently hard-coded in the shipped build; configurable location/provider selection remains future work. Both manual ScreensaverActivity and the system DreamService now use the same three-card header and two-column Russia/World news layout.
- All 20 wallpaper photos are bundled in the APK and restored to private storage. First-run display and carousel selection do not require Unsplash to be reachable from the TV.
- Liquid Glass surfaces use layered translucent fills, highlights, strokes and a blurred backdrop. Android 9 uses an asynchronously generated, downsampled snapshot blur; newer Android versions may use native blur APIs with a compatible fallback. The effect is evaluated on the target TV rather than disabled by assumption.
- Brightness, Wi-Fi and Bluetooth entries open Android settings. Direct toggles depend on StarOS permissions and firmware APIs and are not claimed to work universally.
- RAM is informational. Android controls background process lifecycle; this app does not force-stop arbitrary apps or claim to free RAM by killing protected processes.
- Theme selection, dock dimensions, favourites, per-app ordering and hidden-app state are persisted. Per-app icon sizing and a full appearance editor are not implemented.
- The screensaver must be selected by the user in Android/StarOS Settings > Display > Screen saver if the firmware exposes DreamService. Some TV firmware builds remove this system feature; this must be verified on the physical Sber TV.
- Device installation, launcher replacement behaviour, remote navigation and performance still require testing on the physical Sber TV.

## Cleaner safety

The general cleaner does not silently remove packages or clear another app's private cache. It lists non-system apps and opens Android's native uninstall confirmation.

TV Manager has a separate reversible package-management flow through Shizuku. The owner must install/start Shizuku, authorize RFTV, and confirm each manual package change. Every change stores and verifies the original PackageManager enabled state before execution; the result is read back and rollback is attempted on failure. Only RFTV-owned snapshots can be restored. Protected HOME/System UI/Settings/installer/network/media/remote/OEM/SoC packages remain locked. No package uninstall, data clearing or arbitrary shell console is provided.

Automatic cleanup requires a complete exact profile (manufacturer, model, board/hardware, device/product, Android API, build fingerprint and ABI) plus explicitly verified optional packages, high confidence and checked dependencies. Imported CleanYourTV profiles are reference-only and do not match the primary Sber SDX-50U4123B / Android 9 / MT9632 target. Automatic cleanup is therefore unavailable on that device until its own firmware and package inventory are validated.

The Shizuku bridge and cleanup flow have passed automated CI checks, but have not yet been tested on the physical Sber TV. First device validation should be read-only: Shizuku connection/UID, package inventory and package details. See [TV_MANAGER.md](TV_MANAGER.md), [TV_MANAGER_BRIDGE.md](TV_MANAGER_BRIDGE.md) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Wallpaper credits

All 20 photographs are linked to their original Unsplash photo pages and used under the Unsplash License. The app downloads optimized 1920×1080 JPEG derivatives from Unsplash's image CDN. See [WALLPAPER_CREDITS.md](WALLPAPER_CREDITS.md) for photographer attribution and original photo links.


## Next implementation order

1. Measure the current v0.9.0 baseline on the target Sber TV: memory, frame pacing, cold launch, overlay opening and blur cost. No physical measurements are claimed yet.
2. Deliver and measure full-screen blur for All Apps and Control Center.
3. Implement 3–5 layered photographic scenes and the clock-between-layers composition.
4. Complete tvOS-style Settings focus restoration.
5. Finish screensaver polish, including slow clock drift and correct remote dismissal on DreamService-capable firmware.

Do not treat the v0.9.0 shipped flat wallpapers, fixed Chelyabinsk weather location, or current screensaver clock as completion of the new product target.
