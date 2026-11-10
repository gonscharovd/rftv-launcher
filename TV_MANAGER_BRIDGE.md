# TV Manager privileged package-management architecture

## Status

**Development priority: deferred.** This bridge is retained as existing code. Do not expand package-management features while the launcher-first roadmap is active. The first eventual device check remains read-only.

This document defines the implementation boundary for reversible package management. Existing implementation shipped in **RFTV Launcher v0.9.0** (versionCode 12), merged into `main` and published as a stable [GitHub Release](https://github.com/gonscharovd/rftv-launcher/releases/tag/v0.8.0). The release contains a Shizuku UserService bridge, durable snapshots/journal, a transactional per-package controller, manual cleanup UI and automatic batch orchestration. Automatic cleanup remains intentionally unavailable until a target-specific profile has explicitly validated the full device fingerprint, optional packages and dependencies.

The bridge and package-operation flow have passed automated CI, but have not yet been physically tested on Sber SDX-50U4123B / StarOS 1.103.91 / Android 9 / MT9632. The first device test must be read-only.

## Access providers

TV Manager must discover capabilities at runtime. A setting such as Android's global ADB-enabled flag is diagnostic information only; it is never proof that RFTV can execute shell commands.

1. **Ordinary Android application** — read-only PackageManager inventory and public device facts. It cannot disable unrelated system packages.
2. **Device Owner / Profile Owner** — when RFTV is already provisioned and Android confirms the owner role, use only the DevicePolicyManager package-hiding capability. Hiding is not the same operation as changing enabled state. Never silently provision Device Owner or request a factory reset.
3. **Shizuku with ADB identity** — preferred on-device privileged provider where the installed Shizuku service is available, its binder is alive, the user has explicitly authorized RFTV, and the service UID/permissions are checked for the exact operation. On Android versions before 11, starting Shizuku without root requires an authorized computer/ADB session; the service generally must be started again after reboot.
4. **Shizuku with root / Sui** — optional provider for devices that already have a supported root setup. Root is never assumed, requested, or installed by RFTV.
5. **External ADB host** — a separately installed trusted companion on a computer can execute ADB commands after the TV owner authorizes that host. Any future bridge protocol must authenticate the host, require explicit pairing and per-session consent, restrict commands to a fixed package-operation API, and avoid exposing an unauthenticated TCP shell on the TV.

A provider is not considered available merely because a helper app is installed, USB/Wi-Fi debugging is enabled, an IP address is known, or a port is displayed. Each capability is probed independently and non-destructively.

## User-facing flow

TV Manager presents two primary actions:

### Automatic cleanup

1. The owner enables Developer options and ADB debugging in the TV settings.
2. RFTV detects the exact TV model, manufacturer, board, hardware/SoC, Android API, build/firmware fingerprint and ABI.
3. RFTV finds a matching, reviewed device profile. A profile is valid only for the identifiers and firmware range it explicitly covers.
4. RFTV checks the Shizuku binder, explicit permission and UserService UID. This proves the provider identity, not that the OEM firmware permits every package operation.
5. RFTV previews only profile-listed packages explicitly marked optional with high confidence and verified dependencies.
6. Immediately before each owner-approved change, it persists and reads back the original state snapshot, then requests the fixed package-manager operation and verifies the effective state.
7. On the first failure or unexpected state, it stops and attempts to restore the exact original state.

Developer options and the global ADB debugging toggle are not themselves a grant of shell privileges to an ordinary application. RFTV must show the exact missing step/provider. For Android 9, Shizuku commonly needs to be started from an already authorized computer/ADB host unless the TV is already rooted. Do not claim that enabling a toggle on the TV is enough.

If no exact profile exists, the firmware is outside the tested range, or the privileged channel cannot perform the operation, automatic cleanup is unavailable. The user can still open Manual cleanup.

### Manual cleanup

Manual cleanup displays the installed package inventory with a per-package enable/disable switch and a clear status indicator. Each row explains the package name, display label, category, system/user status, declared permissions, exported services/components where available, known dependencies, analysis confidence and risk. The UI must distinguish facts from heuristics.

- Protected packages (RFTV, HOME/launcher, System UI, Settings, installer, permission controller, package manager, network, Bluetooth, audio/video, remote/input, update and essential OEM/SoC services) are always locked and cannot be toggled.
- Packages already disabled before RFTV are shown as already disabled and are not adopted into RFTV's restore set.
- A package that the user manually selects may lack an exact device profile or have low-confidence analysis. Show a strong, package-specific warning and require a separate confirmation before applying it; never silently apply such a selection.
- If dependencies are unknown, explain that dependent features may stop working. Never present an unknown package as safe.
- The switch is actionable only when the Shizuku UserService is connected with UID 2000 (shell) or 0 (root). This confirms a privileged channel, not the success of a particular OEM package-manager command; the first owner-confirmed operation is verified after execution. Otherwise show the reason and the exact access path needed.
- Turning a package off first commits and verifies its exact original state snapshot. Only then may the provider execute the operation. Read the effective state back and verify it.
- Turning it back on restores the captured original enabled setting, not a generic ENABLED state. This includes restoring DEFAULT, DISABLED_USER and DISABLED_UNTIL_USED exactly.
- If a manual operation fails verification, revert the switch and report the error. Do not display a successful state based on the command exit code alone.

Automatic and manual operations share the same snapshot store, operation journal, provider verification and rollback mechanisms. Manual mode changes who selects a candidate; it does not bypass protected-package rules or access checks.

## Android 9 / target-device constraints

- Android 9 (API 28) does not have Android 11's on-device Wireless debugging pairing workflow. ADB-over-network availability and port are firmware-dependent.
- Shizuku documents API-specific limitations, including hidden-API restrictions introduced in Android 9 and differences in permissions granted to the ADB identity. Do not assume that shell identity can perform every package operation.
- Device Owner package hiding is available from API 21, but only for an actual device/profile owner (or an appropriately delegated package-access role). It hides a package; it does not provide a general package-manager shell.
- RFTV must display the exact access mode and tested capabilities. Unsupported operations remain disabled in the UI.

## Package eligibility

A package may enter a proposed disable batch only when all conditions hold:

- the device fingerprint exactly matches a profile tested for the current board/hardware, device/product, Android API and relevant firmware/build;
- the package is explicitly listed as optional in that profile, with traceable evidence and a test record;
- analysis confidence is high; package identity, role and required dependencies are reviewed;
- no unresolved dependency exists;
- the package is not RFTV itself or any protected Android, TV, OEM, SoC, input, remote, network, installer, audio/video, update, account, setup or launcher component;
- the active provider identity/UID is verified; the exact package-manager command is verified by read-back only after the owner-approved operation;
- the original state snapshot has been persisted and verified before the first change.

A profile match alone is never permission to apply a profile. Unknown, low-confidence, untested, newly installed or firmware-mismatched packages are never eligible.

## State snapshot and restoration

Before a change, persist an immutable record containing at least:

- package name and user ID;
- original PackageManager enabled-setting integer (including DEFAULT, ENABLED, DISABLED, DISABLED_USER and DISABLED_UNTIL_USED);
- the observed `ApplicationInfo.enabled` flag (effective enabled state at capture time);
- Device Owner hidden state when that provider is used;
- provider and provider identity/UID;
- device fingerprint, Android build fingerprint, timestamp and operation ID.

Do not collapse DEFAULT into ENABLED: the exact original setting must be restored. Do not treat packages that were already disabled before RFTV as RFTV-managed. Restore only packages with a verified RFTV operation record, and restore each package to its captured original state rather than enabling everything.

If a snapshot cannot be saved and read back successfully, no change is allowed.

## Transaction and rollback

- Preview the complete batch: package purpose, evidence, confidence, dependencies, expected side effects, access provider and exact requested state.
- Require explicit owner confirmation immediately before execution.
- Apply one package at a time; after each operation, query and verify the resulting state.
- Stop at the first error, bridge disconnect, unexpected state or failed verification.
- Roll back already-applied changes in reverse order using their exact snapshots. Report both the original failure and every rollback result.
- Keep an append-only journal with before/after state, provider, result, timestamp and error details. Never report success based only on a zero exit code.
- Provide per-package restore and restore-all for RFTV-managed changes only.
- Re-scan after firmware or package updates. A changed fingerprint invalidates the old profile for new changes; previously saved snapshots remain available for restoration.

## Command and transport safety

- Never expose a general shell, command editor or arbitrary command execution surface in TV Manager.
- Use typed provider methods and fixed operation enums. Pass package names as validated arguments; never concatenate untrusted package names into shell command strings.
- Never uninstall packages, clear app data, disable all apps in a category, or run a generic "debloat" script.
- Do not change ADB debugging, network settings, developer options, bootloader, recovery or root configuration automatically.
- Do not probe remote IP ranges or attempt to connect to an assumed port. External ADB requires an explicitly paired host and a verified transport.

## Current implementation in this branch

- Added Shizuku API/provider dependencies and a narrow UserService AIDL contract. The service accepts only validated package names, enabled-state enum values and user 0; no arbitrary shell command surface exists.
- The UserService verifies its process UID (shell/root), rejects RFTV and hard-protected package identities, uses fixed `cmd package` / `pm` argument arrays, and applies a bounded process timeout compatible with API 24+.
- Added durable, synchronous before-change snapshots and an operation journal in app-private SharedPreferences.
- Added a package controller that rechecks eligibility, commits the snapshot before mutation, disables with `DISABLED_USER`, reads the effective state back, and attempts exact-state rollback on failure. Restore applies the captured integer state, including DEFAULT; restore-all processes RFTV-owned snapshots in reverse operation order.
- Added automatic batch orchestration with fail-fast behavior and reverse-order rollback. It currently has no eligible candidates because donor profiles lack exact manufacturer/model/build-fingerprint/ABI identity and verified optional/dependency metadata.
- Added a DPAD-operable manual cleanup list with per-package switches, package-specific confirmation, protected-package locks, a details/return-focus path, per-package restoration and restore-all for RFTV-owned snapshots. Previously disabled packages are not adopted.
- Added automatic-cleanup eligibility flags to profile entries. Donor entries default to unverified, so neither existing Sber donor profile is automatically executable. The current Sber SDX-50U4123B / Android 9 / MT9632 target has no matching profile.
- Added/extended tests for protected package identities and donor-profile trust defaults.
- The UI and provider have not yet been validated on physical hardware. OEM-specific Shizuku permissions and command behavior still require a device test; no package was disabled during development.


## Owner clarification and audit gaps — 2026-11-10

The hard-protection list must be absolute and must not be overridden by imported or online data. Required red rules include HOME, System UI, Settings, installer/package manager, network/Wi-Fi/Bluetooth, remote/input, TvInputService, shared UID `android.uid.system`, and persistent system components.

The current source audit found package-ID/prefix protections and dynamic HOME/Settings/input/screen-saver protection, but did not find explicit checks for shared UID `android.uid.system`, `ApplicationInfo.FLAG_PERSISTENT`, or TvInputService role. Treat these as open safety defects before any future package-operation release.

Unknown packages are yellow, not safe. Green requires verified evidence. Current local donor profile records are reference-only and no exact profile matches SDX-50U4123B / Android 9 / MT9632. Automatic cleanup must remain unavailable on that target.

An eventual online profile service must distribute signed records, be generated automatically without manual owner moderation, verify signatures in the app and retain a verified cache for offline use. No such signed online database is implemented in v0.9.0.

TV Manager development is paused until the primary launcher scope is delivered. Do not perform package changes during current physical acceptance.
