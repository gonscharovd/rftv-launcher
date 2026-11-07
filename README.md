# RFTV Launcher

A lightweight Android TV launcher with a tvOS-inspired Liquid Glass visual direction.

## Current prototype

- Native Kotlin Android Views (no Compose and no third-party UI dependencies).
- Home screen with a large clock, date, weather entry point and glass-style settings button.
- Translucent app dock with focus scaling and a second row revealed with DPAD Down / hidden again with DPAD Up.
- Separate light and dark wallpaper palettes with a time-based automatic theme mode.
- Control panel with Wi-Fi, Bluetooth, display settings, RAM information, launcher settings and Android settings shortcuts.
- Dock width and height controls saved locally.
- TV Cleaner lists user-installed apps and delegates uninstall to Android's confirmation screen.
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

- The weather tile currently contains demonstration data. A real weather provider and location/source settings are not connected yet.
- Wallpaper choices are lightweight gradient placeholders. Real photographic wallpaper packs and per-theme carousels are planned.
- Glass is approximated with translucent fills and strokes. Real-time background blur is intentionally avoided for the low-memory target device.
- Brightness, Wi-Fi and Bluetooth entries open Android settings. Direct toggles depend on StarOS permissions and firmware APIs and are not claimed to work universally.
- RAM is informational. Android controls background process lifecycle; this app does not force-stop arbitrary apps or claim to free RAM by killing protected processes.
- Theme selection and dock dimensions are persisted. Favourites, per-app ordering, icon sizing and full appearance editor are not implemented yet.
- Device installation, launcher replacement behaviour, remote navigation and performance still require testing on the physical Sber TV.

## Cleaner safety

The cleaner does not silently remove packages or clear another app's private cache. It lists non-system apps and opens Android's native uninstall confirmation. ADB / Shizuku support is not implemented yet.
