# RFTV Launcher

A lightweight Android TV launcher with a tvOS-inspired Liquid Glass visual direction.

## Current prototype

- Native Kotlin Android Views (no Compose and no third-party UI dependencies).
- Home screen with a large centered clock, date, weather entry point and glass-style settings button.
- Translucent app dock with focus scaling and a second row revealed with DPAD Down / hidden again with DPAD Up.
- 10 photographic daytime and 10 photographic nighttime landscape wallpapers from Unsplash.
- Separate day/night wallpaper selection and carousel modes, with a configurable 1–60 minute interval.
- Wallpaper photographs are downloaded over HTTPS on first use and cached in app-private storage. Once cached, they work offline.
- Control panel with Wi-Fi, Bluetooth, display settings, RAM information, launcher settings and Android settings shortcuts.
- Dock width and height controls saved locally.
- TV Cleaner lists user-installed apps and delegates uninstall to Android's confirmation screen.
- TV Manager provides a read-only inventory of system packages, enabled state, and text report export for device-specific analysis.
- Remote-control focus states and large TV-friendly controls.

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

GitHub Actions builds pushes to main and feature/** branches and pull requests targeting main.

## Important prototype limitations

- RFTV now includes an Android DreamService screensaver prototype with hourly random landscape downloads, cached images, Open-Meteo weather for Chelyabinsk, official Bank of Russia USD/EUR rates, and RSS headlines from RIA Novosti and Google News (Russian-language world feed). The initial city and feeds are hard-coded; configuration UI is a follow-up.
- Wallpaper photos require an internet connection for their first download. The launcher stores each downloaded image privately; previously downloaded wallpapers remain available offline.
- Glass is approximated with translucent fills and strokes. Real-time background blur is intentionally avoided for the low-memory target device.
- Brightness, Wi-Fi and Bluetooth entries open Android settings. Direct toggles depend on StarOS permissions and firmware APIs and are not claimed to work universally.
- RAM is informational. Android controls background process lifecycle; this app does not force-stop arbitrary apps or claim to free RAM by killing protected processes.
- Theme selection and dock dimensions are persisted. Favourites, per-app ordering, icon sizing and full appearance editor are not implemented yet.
- The screensaver must be selected by the user in Android/StarOS Settings > Display > Screen saver if the firmware exposes DreamService. Some TV firmware builds remove this system feature; this must be verified on the physical Sber TV.
- Random screensaver photos are served by LoremFlickr and cached locally. The service retains only a small cache to protect internal storage.
- Device installation, launcher replacement behaviour, remote navigation and performance still require testing on the physical Sber TV.

## Cleaner safety

The cleaner does not silently remove packages or clear another app's private cache. It lists non-system apps and opens Android's native uninstall confirmation. TV Manager inventory and report export are implemented; privileged ADB / Shizuku bridge, disable / restore actions, and rollback are not implemented yet. See [TV_MANAGER.md](TV_MANAGER.md) for the device-specific safety plan.

## Wallpaper credits

All 20 photographs are linked to their original Unsplash photo pages and used under the Unsplash License. The app downloads optimized 1920×1080 JPEG derivatives from Unsplash's image CDN. See [WALLPAPER_CREDITS.md](WALLPAPER_CREDITS.md) for photographer attribution and original photo links.
