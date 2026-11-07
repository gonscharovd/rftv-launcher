# RFTV Launcher

A lightweight Android TV launcher focused on fast navigation, a clean interface, and safe device maintenance.

## Current MVP

- Native Kotlin Android application (no Compose or third-party UI dependencies).
- Android TV / Leanback launcher entry points.
- App grid with installed launchable apps.
- Remote-control friendly focus navigation.
- Cleaner screen listing user-installed apps and opening Android's native uninstall confirmation.
- GitHub Actions debug APK build.

## Compatibility

- Minimum Android version: Android 7.0 (API 24).
- Compile SDK: 35.
- The app is designed for Android TV boxes; actual compatibility must be verified on vendor firmware.

## Build

Open the project in Android Studio with Android Gradle Plugin 8.7.3 and JDK 17, or use Gradle 8.9:

```bash
gradle assembleDebug
```

Debug APK output:

```
app/build/outputs/apk/debug/app-debug.apk
```

## Cleaner safety model

The first MVP does not silently remove packages or claim to clear another app's private cache. It lists non-system apps and delegates uninstall to Android's system confirmation screen.

ADB / Shizuku support is planned as a separate, explicit opt-in module. System package removal must require a deliberate selection, clear package identity, and a confirmation step. Critical Android components must be protected.

## Project status

Early MVP / development build. Not yet validated across device vendors.
