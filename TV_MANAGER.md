# RFTV TV Manager — implementation plan

## Current implementation

The launcher can enumerate system applications through Android PackageManager and export a plain-text inventory. The report includes:

- Manufacturer and model
- Android release and API level
- Package ID and display label
- Enabled / disabled state
- System app versus updated system app

This is deliberately read-only. It does not execute shell commands, uninstall packages, or change application state.

## Why privileged access is required

An ordinary Android application runs under its own UID. Android does not grant it permission to disable unrelated system packages. The PackageManager APIs available to a regular app can inspect packages but cannot perform the equivalent of `pm disable-user` on protected apps.

Potential bridge options for the target Sber / StarOS device:

1. **ADB host bridge** — a trusted computer or companion device runs ADB and sends narrowly scoped commands after the TV owner authorizes debugging.
2. **Shizuku** — investigate compatibility with this Android 9 firmware. Shizuku itself needs to be started through an authorized ADB session or root; it is not a privilege bypass.
3. **Root** — not assumed and not required for the first implementation.

Wireless debugging pairing introduced in later Android releases is not assumed to exist on Android 9. Legacy ADB-over-network availability is firmware-dependent and must be tested on the TV.

## Required safety model before enabling changes

- Collect the real package inventory from this exact model and firmware.
- Never apply generic Google TV / Xiaomi / Android TV package lists to Sber StarOS.
- Classify packages using verified package identity and observed function, not package-name guesses.
- Protect Home / launcher, System UI, Settings, setup wizard, input / remote, audio, video, networking, account services, package installer, and firmware update components.
- Save the original enabled state before every change.
- Preview a batch and require explicit confirmation.
- Disable packages; do not uninstall them.
- Verify the TV remains navigable after each small batch.
- Provide a restore-all action that restores only packages changed by RFTV, to their recorded original states.
- Keep an append-only action journal with timestamps, package IDs, prior state, requested state, and result.
- If bridge connectivity is lost, stop immediately and do not continue a batch.

## Next device-testing session

1. Enable developer options / USB or network debugging if the firmware exposes them.
2. Authorize a trusted ADB host.
3. Export the inventory from RFTV and collect the same inventory using ADB.
4. Compare results and build a device-specific allowlist / denylist.
5. Test one low-risk, nonessential package and immediately restore it.
6. Only then expose disable / restore controls in the TV Manager UI.

## Current status

Package inventory and export UI: implemented in the feature branch.

Privileged bridge, package profiles, disable / restore, and rollback: not implemented yet. No package is modified by the current TV Manager.
