# RFTV Launcher

A lightweight Android TV launcher with a tvOS-inspired Liquid Glass visual direction.

## Implemented features

- Native Kotlin Android Views (no Compose and no third-party UI dependencies).
- Home screen with large glass-style clock/date, current Chelyabinsk weather beside the clock, a five-day forecast overlay, and the Control Center entry point.
- Single-row adaptive translucent app dock with focus scaling and a dedicated All Apps entry; DPAD Down does not expand the dock.
- Dock and catalog refresh automatically after app installation, removal or replacement, without restarting the launcher; focus and favorite ordering are preserved.
- 10 photographic daytime and 10 photographic nighttime landscape wallpapers from Unsplash.
- Separate day/night wallpaper selection and carousel modes, with a configurable 1–60 minute interval.
- Wallpaper photographs are downloaded over HTTPS on first use and cached in app-private storage. Once cached, they work offline.
- Full-height right-side glass Control Center with vector icons, grouped Wi-Fi/Bluetooth, theme, RAM, screensaver and settings actions.
- Dock width and height controls saved locally.
- TV Cleaner lists user-installed apps and delegates uninstall to Android's confirmation screen.
- RFTV TV Manager provides system-package inventory and analysis, strict full-device profile matching, a Shizuku UserService bridge, manual per-package cleanup, durable original-state snapshots, operation journal, verified restore and reverse-order rollback.
- Remote-control focus states and large TV-friendly controls.
- Manual screensaver dashboard uses the active Home wallpaper with a blurred backdrop, clock, CBR currency rates, current weather, and separate Russia/World RSS columns. Android DreamService also uses the active Home wallpaper.

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

Stable Release APKs are published through [GitHub Releases](https://github.com/gonscharovd/rftv-launcher/releases). Use the in-app OTA updater for upgrades; it verifies SHA-256, package identity, versionCode, ABI and signing certificate before opening Android's installer.

GitHub Actions builds pushes to main and feature/** branches and pull requests targeting main.

## Important prototype limitations

- The manual screensaver and Android DreamService use Open-Meteo weather for Chelyabinsk, official Bank of Russia USD/EUR rates, and RSS feeds from RIA Novosti and Google News (Russian-language world feed). The initial city and feeds are hard-coded. The manual dashboard has the full two-column layout; the system DreamService still needs visual parity work.
- Wallpaper photos require an internet connection for their first download. The launcher stores each downloaded image privately; previously downloaded wallpapers remain available offline.
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
