# RFTV TV Manager

## What is implemented in this branch

- Reads the installed system-package inventory using Android PackageManager.
- Exports device identity (manufacturer, model, board, device, hardware, product, Android/API) and package state.
- Includes a small, offline reference catalogue derived from two CleanYourTV Sber StarOS examples.
- Matches a reference profile only when its board/device token and Android API level match.
- Shows each profile's package IDs, descriptions, risk notes and original source link.

The catalogue is deliberately reference-only. It does not execute shell commands, disable packages, uninstall packages, or modify system state. A profile match is not permission to apply a list automatically.

## CleanYourTV source and attribution

Reference data adapted from [Gathin23/CleanYourTV](https://github.com/Gathin23/cleanyourtv), distributed under the MIT License.

Imported reference profiles:
- [Sber Salute TV 43-inch, CVTE / Amlogic T962D4, StarOS 1.101 / Android 13](https://github.com/Gathin23/cleanyourtv/tree/main/examples/sber-salute-tv-at30a6)
- [Sber Salute TV 32-inch, CVTE / MediaTek MT9256, StarOS 1.103 / Android 11](https://github.com/Gathin23/cleanyourtv/tree/main/examples/sber-salute-tv-pb766)

Package identifiers and device-specific notes are presented as reference material. The original repository remains the source of truth for current lists, full test reports and command scripts. RFTV's short descriptions are adapted summaries, not a claim that every package is safe on another device.

The upstream repository's MIT notice is reproduced in THIRD_PARTY_NOTICES.md.

## Matching policy

A profile must match all of the following:
- Android SDK/API level;
- at least one exact (case-insensitive) board/hardware token; and
- at least one exact (case-insensitive) device/product token.

Partial substring matches and generic platform codenames are not accepted. This deliberately favors false negatives over applying a profile to the wrong television.

A brand name or Android version alone is never sufficient. A profile that does not match is displayed as a reference and must not be treated as a recommendation.

The primary RFTV target, Sber SDX-50U4123B on Android 9 / API 28 with MediaTek MT9632, does not match either imported profile (Android 13/Amlogic or Android 11/MT9256). Its own inventory must be collected before a device-specific cleanup profile can be prepared.

## Why privileged access is required

An ordinary Android application runs under its own UID. Android does not grant it permission to disable unrelated system packages. PackageManager can inspect packages, but cannot perform the equivalent of pm disable-user on protected apps.

Potential bridge options:
1. **ADB host bridge** — a trusted computer or companion device runs ADB and sends narrowly scoped commands after the TV owner authorizes debugging.
2. **Shizuku** — investigate compatibility with the target firmware. Shizuku must itself be started through an authorized ADB session or root; it is not a privilege bypass.
3. **Root** — not assumed and not required for the first implementation.

Wireless debugging pairing introduced in later Android releases is not assumed to exist on Android 9. Legacy ADB-over-network availability is firmware-dependent and must be tested on each device.

## Safety model required before enabling changes

- Collect the real package inventory from the exact model and firmware.
- Never apply generic Google TV / Xiaomi / Android TV lists to Sber StarOS, or vice versa.
- Classify packages using verified package identity and observed function, not package-name guesses.
- Protect Home/launcher, System UI, Settings, setup wizard, input/remote, audio, video, networking, account services, package installer and firmware-update components unless there is a model-specific, reboot-tested reason.
- Save the original enabled state before every change.
- Preview each batch and require explicit owner confirmation.
- Disable packages; do not uninstall them.
- Verify navigation, HDMI, audio, video, remote, Wi-Fi and standby after each small batch.
- Provide restore-all for only the packages changed by RFTV, returning each to its recorded original state.
- Keep an append-only action journal with timestamps, package IDs, prior state, requested state and result.
- If bridge connectivity is lost, stop immediately and do not continue a batch.

Risk labels in the UI are review prompts, not guarantees. A package described as low-risk in one firmware can be essential on another firmware.

## Adding more manufacturers and models

New profiles should be added only from a traceable source and include:
- exact retail model, board/codename and hardware platform;
- firmware/build and Android API;
- package IDs, human-readable purpose and evidence;
- packages explicitly marked to keep;
- test scope (cold boot, reboot count, Home, remote, HDMI, audio/video, networking, standby);
- known side effects and restoration steps;
- source URL, author attribution and applicable license.

Profiles without a tested disable list can still be added as device-identification/reference records, but must not display cleanup recommendations.

## Next device-testing session

1. Enable developer options / USB or network debugging if firmware exposes them.
2. Authorize a trusted ADB host.
3. Export RFTV inventory and collect the same inventory using ADB.
4. Compare results and build a device-specific allowlist/denylist.
5. Test one low-risk, nonessential package and immediately restore it.
6. Reboot and test core TV functions before widening the list.

## Current status

- Inventory and report export: implemented.
- Reference catalogue and strict profile matching: implemented for two Sber examples.
- Other manufacturers/models: catalogue structure is extensible; profiles still need to be imported and reviewed.
- Privileged bridge, disable/restore, journal and rollback: not implemented.
- No package is modified by the current TV Manager.