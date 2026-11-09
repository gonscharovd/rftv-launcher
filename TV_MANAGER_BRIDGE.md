# TV Manager privileged package-management architecture

## Status

This document defines the implementation boundary for reversible package management. The current release is still read-only: package inventory and analysis work, but no privileged bridge or package-state mutation is shipped.

## Access providers

TV Manager must discover capabilities at runtime. A setting such as Android's global ADB-enabled flag is diagnostic information only; it is never proof that RFTV can execute shell commands.

1. **Ordinary Android application** — read-only PackageManager inventory and public device facts. It cannot disable unrelated system packages.
2. **Device Owner / Profile Owner** — when RFTV is already provisioned and Android confirms the owner role, use only the DevicePolicyManager package-hiding capability. Hiding is not the same operation as changing enabled state. Never silently provision Device Owner or request a factory reset.
3. **Shizuku with ADB identity** — preferred on-device privileged provider where the installed Shizuku service is available, its binder is alive, the user has explicitly authorized RFTV, and the service UID/permissions are checked for the exact operation. On Android versions before 11, starting Shizuku without root requires an authorized computer/ADB session; the service generally must be started again after reboot.
4. **Shizuku with root / Sui** — optional provider for devices that already have a supported root setup. Root is never assumed, requested, or installed by RFTV.
5. **External ADB host** — a separately installed trusted companion on a computer can execute ADB commands after the TV owner authorizes that host. Any future bridge protocol must authenticate the host, require explicit pairing and per-session consent, restrict commands to a fixed package-operation API, and avoid exposing an unauthenticated TCP shell on the TV.

A provider is not considered available merely because a helper app is installed, USB/Wi-Fi debugging is enabled, an IP address is known, or a port is displayed. Each capability is probed independently and non-destructively.

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
- the active provider has independently confirmed the exact required operation;
- the original state snapshot has been persisted and verified before the first change.

A profile match alone is never permission to apply a profile. Unknown, low-confidence, untested, newly installed or firmware-mismatched packages are never eligible.

## State snapshot and restoration

Before a change, persist an immutable record containing at least:

- package name and user ID;
- original PackageManager enabled-setting integer (including DEFAULT, ENABLED, DISABLED, DISABLED_USER and DISABLED_UNTIL_USED);
- manifest enabled flag and observed effective state;
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

- Added a pure policy gate for package disable/restore eligibility and rollback decisions.
- Added unit tests for protected/unknown packages, confidence, exact profile matching, dependencies, missing snapshots, RFTV ownership and rollback conditions.
- No privileged commands are executed by the policy.
- Shizuku, Device Owner and external ADB providers, durable snapshots, operation journal, UI preview/confirmation and actual state changes remain to be implemented and tested separately.
