# RFTV TV Manager

## Priority and current implementation status

**Priority: deferred.** TV Manager is retained as an existing subsystem but feature development is paused while the primary launcher work (measured blur, layered Home scenes, Settings focus and screensaver polish) is completed. The requirements below are the safety contract for later work; they are not a request to expand TV Manager now.



Existing implementation shipped in **RFTV Launcher v0.9.0** (versionCode 12), merged into `main` and published as a stable [GitHub Release](https://github.com/gonscharovd/rftv-launcher/releases/tag/v0.9.0). The physical Shizuku/provider and package-operation flow has not yet been validated on the target Sber TV; initial acceptance is read-only.

- Device and package inventory through Android PackageManager.
- Package details: requested permissions, granted/denied state, declared services/receivers/activities, category, risk, confidence and analysis limitations.
- Sensitive permissions are metadata, not proof of recording, tracking or data transmission.
- Shizuku API/provider integration with a narrow UserService AIDL bridge.
- Manual cleanup UI with a searchable package list, per-package switches, package-specific confirmation, protected-package locks and DPAD navigation.
- Durable before-change snapshots, operation journal, read-back verification, exact-state restoration and reverse-order restore-all.
- Automatic batch orchestration with fail-fast behavior and reverse-order rollback. It remains unavailable until an exact tested profile explicitly marks optional packages, high confidence and verified dependencies.
- No package was disabled during development. Physical device testing has not yet been performed; the first target-device validation must remain read-only.

## Package analysis: evidence and limits

The report reads declared permissions and component metadata from PackageManager. A requested permission is not proof that an app used the capability. A shared system UID can make permission grants appear on unrelated packages. Internet access alone is not evidence of tracking or exfiltration.

This screen does not read AppOps history, packet captures, runtime sensor use or remote endpoints. Where sensitive permissions and network access coexist, the report requests review with low confidence; it does not assert surveillance. Declared services and receivers do not prove that a component is currently running.

Package dependencies are not fully resolvable through PackageManager on all supported TV builds. Unknown dependencies remain unknown and trigger a manual warning; they are never treated as safe.

## Reference catalogue and attribution

Reference data adapted from [Gathin23/CleanYourTV](https://github.com/Gathin23/cleanyourtv), distributed under the MIT License.

Imported reference profiles:
- [Sber Salute TV 43-inch, CVTE / Amlogic T962D4, StarOS 1.101 / Android 13](https://github.com/Gathin23/cleanyourtv/tree/main/examples/sber-salute-tv-at30a6)
- [Sber Salute TV 32-inch, CVTE / MediaTek MT9256, StarOS 1.103 / Android 11](https://github.com/Gathin23/cleanyourtv/tree/main/examples/sber-salute-tv-pb766)

Package identifiers and device notes are reference material. The upstream repository remains the source of truth for current lists, full test reports and command scripts. RFTV summaries do not claim that a package is safe on another model.

The upstream MIT notice is reproduced in THIRD_PARTY_NOTICES.md.

## Exact profile matching

Automatic cleanup requires every identity dimension below to match:

- manufacturer and retail model;
- board/hardware and device/product identifiers;
- exact Android SDK/API level;
- exact build fingerprint (firmware);
- exact supported ABI set.

All fields must be explicitly present in a reviewed profile. Empty manufacturer/model/build/ABI fields are a hard non-match. No substring or generic platform match is accepted. This intentionally favors false negatives over applying a profile to the wrong television.

A profile match alone is not permission to disable packages. Every automatic candidate must additionally be explicitly marked optional, have high-confidence analysis, have verified dependencies and pass the protected-package policy.

The primary RFTV target, Sber SDX-50U4123B on Android 9 / API 28 with MediaTek MT9632 / ARMv7, does not match either imported donor profile. Its exact build fingerprint and package inventory have not yet been validated. Automatic cleanup is therefore unavailable on this target; manual cleanup is the only supported selection mode once Shizuku is connected.

## Privileged access

An ordinary Android application runs under its own UID. Enabling Developer options or ADB debugging does not grant the app ADB Shell permissions.

The current on-device provider is Shizuku UserService:
- Shizuku must be installed and separately started.
- RFTV requests explicit Shizuku authorization.
- The UserService accepts only UID 2000 (ADB shell) or UID 0 (root).
- On Android 9, starting Shizuku without root normally requires an authorized computer/ADB host; it may need to be started again after reboot.
- A connected UID confirms the privileged channel identity, not that every OEM package-manager command will succeed. The exact operation is verified only after the owner confirms it, the snapshot is persisted and the resulting state is read back.

Device Owner/DevicePolicyManager and an authenticated external ADB companion are not implemented in this branch. Root is not assumed or requested.

## Manual cleanup safety

- Each package is shown with display label, package ID, system/user state, enabled state and protection status.
- Package details expose requested permissions and declared component counts. Unknown dependencies are explicitly identified.
- The owner must confirm each disable operation separately. The warning explains that the app or dependent TV features may stop working.
- Protected packages are locked: RFTV, HOME/launchers, System UI, Settings, installer, permission controller, package manager, input methods, active screensaver, network, Bluetooth, audio/video, remote/input and known OEM/SoC components.
- Packages already disabled before RFTV are not adopted into its restore set.
- Only primary Android user 0 is supported by the package bridge.
- No package uninstall, data clearing, generic debloat script or arbitrary shell command is exposed.
- Disabling uses the reversible DISABLED_USER state.
- Before every change, RFTV synchronously saves and reads back the original PackageManager enabled-setting integer, effective ApplicationInfo.enabled flag, provider, user and device build fingerprint.
- After the command, PackageManager state is read back. A zero exit code alone is not considered success.
- If the result cannot be verified, RFTV attempts immediate exact-state rollback. If rollback also fails, the snapshot remains available for recovery.
- Restore applies the captured original state (DEFAULT, ENABLED, DISABLED, DISABLED_USER or DISABLED_UNTIL_USED), never a generic ENABLED state.
- Restore-all processes only RFTV-owned snapshots in reverse operation order. Packages disabled by the owner or another app are not changed.
- The journal is app-private, bounded to the latest 500 events and records before/after state, provider, timestamp and result.

Restoring an exact RFTV-owned snapshot is allowed even if a package became protected after a later app/profile update; disabling that package remains blocked.

## Verification and limitations

- Unit tests cover package policy, profile matching, protected identities, exact enabled-state enum handling, unknown/manual warnings, dependencies, snapshot requirements and rollback decisions.
- CI builds Debug for diagnostics and verifies a signed Release APK using the persistent Release key.
- CI does not prove Shizuku works on a particular OEM firmware.
- No physical test has yet been performed on Sber SDX-50U4123B / StarOS 1.103.91 / Android 9 / MT9632.
- Do not disable any package on the TV until the owner has confirmed that Shizuku connects and the package inventory matches the TV. First physical validation should be read-only: connection, UID, inventory and package details.

## Adding a model profile

A new profile must be based on a traceable source and a verified inventory from the exact model/firmware. It must include:
- manufacturer, retail model, board/hardware, device/product, exact build fingerprint, Android API and ABI set;
- package IDs, human-readable purpose and evidence;
- explicit keep/protected list;
- optional packages with confidence and dependency review;
- test scope: cold boot, reboot, HOME, remote, HDMI, audio/video, networking, standby and OTA;
- known side effects and exact restoration steps;
- source URL, author attribution and applicable license.

A reference-only profile may identify a model, but it must not enable automatic cleanup without an independently tested optional-package list.


## Owner-required safety gaps to close before any future enable/disable release

The source audit as of 2026-11-10 found explicit protected package IDs/prefixes and dynamic HOME/Settings/input/screen-saver protection, but did not find explicit hard-protection checks for shared UID `android.uid.system`, `ApplicationInfo.FLAG_PERSISTENT`, or TvInputService role. The current package policy must not be described as fully implementing the owner's red-rule set until these are covered by code and tests.

Unknown packages must be yellow, and green must require verified evidence. Current heuristic analysis uses low-confidence/informational and review severities; do not equate this automatically with the owner's exact three-color policy.

There is no signed online profile database in the current implementation. The imported CleanYourTV profiles are local reference data and do not match SDX-50U4123B / Android 9 / MT9632. No green or automatic candidates are available for that target. Any future online source must be signature-verified, cached for offline use and subordinate to local red protections.

Without a verified Shizuku privileged channel, package modification must remain unavailable and analysis must remain read-only. The existing Shizuku bridge has not been validated on the physical target TV.
