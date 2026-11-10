# RFTV Launcher — Product Roadmap

## Owner-approved direction — 2026-11-10

The primary product is a lightweight, premium-feeling Android TV launcher for a range of Android TV devices. The Sber SDX-50U4123B / StarOS 1.103.91 / Android 9 / API 28 / MT9632 / ARMv7 / approximately 1.4 GB RAM is the main physical acceptance target, not the only supported device.

### Product requirements

- Home: large iOS-style clock embedded between a full-screen photo background and a separate transparent foreground cut-out that overlaps the clock. Start with 3–5 layered scenes, not a 20-photo flat library.
- Weather beside the clock. City and provider sources must be configurable.
- Dock: favourites plus All Apps. All Apps opens a grid over a full-screen blurred background.
- Gear opens Control Center over a full-screen blurred background.
- Settings: tvOS-style sidebar and right content area, with nested focus position restored on return.
- Screensaver: clock/date, weather, CBR currency rates, Russia/world news; slow clock drift to reduce burn-in; launch manually from Control Center and through Android DreamService where firmware supports it; remote key dismisses it where key events are delivered.
- TV Manager remains in the repository but is lower priority and frozen for feature development while launcher work proceeds.

### Agreed implementation order

1. Measure current behavior on the target TV: memory, frame pacing, cold launch, overlay open time and blur cost. Distinguish measured values from estimates. No physical measurements are currently recorded.
2. Full-screen blur for All Apps and Control Center.
3. Layered photographic scenes and clock-between-layers composition.
4. Settings focus restoration.
5. Screensaver polish, including burn-in drift and DreamService remote behavior.

Stability, remote navigation and performance remain constraints throughout. No new TV Manager feature work until the primary launcher scope is complete.

## Current release and verification baseline

- Stable Release: [v0.9.0 / versionCode 12](https://github.com/gonscharovd/rftv-launcher/releases/tag/v0.9.0), published 2026-11-10.
- Android CI run 42810753407 and publisher run 42811138590 are recorded as successful in the release audit.
- APK SHA-256: `d4a2941add01cfb6c7c56845005f02939eceef3ed9c05c2e04b8c0839539e31c`.
- Release certificate SHA-256: `22b4f71da5f543e933780f05f8573783ad4718036b5bfc1b36fe258695e30917`.
- Physical acceptance of v0.9.0 on Sber SDX-50U4123B has not been confirmed. The owner reported v0.8.0 visual, wallpaper and weather failures; v0.9.0 contains automated remediation, but no claim is made that those issues are resolved on the TV.
- Detailed artifact evidence: [docs/V0.9.0_RELEASE_AUDIT.md](docs/V0.9.0_RELEASE_AUDIT.md). Physical checklist: [DEVICE_TESTING.md](DEVICE_TESTING.md).

## Shipped v0.9.0 versus target design

v0.9.0 contains 20 flat bundled photographs, a centered clock over one photograph, a fixed Chelyabinsk weather location with three-provider fallback/cache, a snapshot blur implementation, a floating Control Center, a Settings sidebar, and a manual/system screensaver dashboard. These are existing implementation facts, not proof that the new owner-approved depth composition, configurable weather, measured blur performance, full nested focus restoration, or burn-in drift are complete.

The prior roadmap's statement that development should stop after v0.9.0 acceptance and only fix regressions is superseded by the owner-approved implementation order above. The v0.8.0 and earlier release audit material below is retained as historical evidence only.

## Safety and release rules

- Keep CI verification separate from physical TV acceptance.
- Never claim a physical test without owner confirmation.
- Measure memory, frame pacing and blur cost on target hardware; do not infer them from source code or CI.
- Keep minSdk 24 and native Kotlin Android Views unless a concrete compatibility reason requires change.
- Release only with increased versionCode, the persistent signing certificate, and verified package/version/SDK/ABI/size/SHA-256/certificate/artifact identity.
- Never replace the persistent signing key, delete user data, reset the TV or disable packages without the required safety process.
- TV Manager's existing code is retained but is not the current development priority.

