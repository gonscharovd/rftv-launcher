# Third-party notices

## CleanYourTV reference data

Portions of the RFTV TV Manager reference catalogue (package identifiers and adapted device-specific descriptions) are derived from:

- Project: [Gathin23/CleanYourTV](https://github.com/Gathin23/cleanyourtv)
- Copyright: Copyright (c) 2026 Gathin
- License: MIT

The upstream MIT License text:

MIT License

Copyright (c) 2026 Gathin

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED AS IS, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

RFTV's catalogue is a reference adaptation, not an endorsement or guarantee that a package can safely be disabled on any device. Consult the upstream device report for the full test conditions and current instructions.

## Shizuku API

RFTV uses the Shizuku API and provider libraries:

- Project: [RikkaApps/Shizuku-API](https://github.com/RikkaApps/Shizuku-API)
- Maven artifacts: `dev.rikka.shizuku:api:13.1.5` and `dev.rikka.shizuku:provider:13.1.5`
- Copyright: Copyright (c) 2021 RikkaW
- License: MIT

The Shizuku manager application/server is not bundled with RFTV. Users install and authorize it separately. RFTV does not use Shizuku branding or manager application assets.

The upstream MIT License text:

MIT License

Copyright (c) 2021 RikkaW

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.


## tvOS Launcher weather fallback reference

RFTV's multi-provider weather fallback and WMO condition-code mapping are adapted from the weather implementation in:

- Project: [techgenius1/tvOS-Launcher](https://github.com/techgenius1/tvOS-Launcher)
- Copyright: Copyright 2026 techgenius
- License: Apache License 2.0
- Upstream files: `src/com/techgenius/tvoslauncher/Weather.java`
- RFTV adaptation: `app/src/main/java/com/rftv/launcher/RftvWeatherRepository.kt` (adapted and modified for RFTV's fixed Chelyabinsk location, Home/forecast/screensaver cache, and current app architecture).

The upstream license and NOTICE are preserved in `third_party/tvos-launcher/`.

### UI and performance reference

Iteration 2 also studies three narrowly scoped design/performance patterns from the same Apache-2.0 project:
- `MainActivity.java`: scaled-canvas quarter-resolution overlay capture and the cached LiquidGlass Drawable/shader approach.
- `ControlPanelOverlay.java`: compact capsule geometry, concise single-line labels and focused tile treatment.
- `SceneHome.java`: a separate floating Dock name tooltip above the glass Dock container.

RFTV reimplements these ideas in its own Kotlin/Android Views architecture; no donor Java source, fonts, icons, or unrelated launcher features are copied or bundled. The upstream project remains the design/performance reference, not a source of new RFTV product features.


### Dock, Control Center and Settings UI reference (Iteration 3)

The RFTV UI in Iteration 3 reimplements measured layout and focus styling from the Apache-2.0 tvOS Launcher project:

- Project: [techgenius1/tvOS-Launcher](https://github.com/techgenius1/tvOS-Launcher)
- License: Apache License 2.0; upstream LICENSE and NOTICE are preserved in `third_party/tvos-launcher/`.
- `SceneHome.java`: `buildBottomDock()` — 23dp glass radius, 13dp/10dp horizontal/vertical padding, 14dp bottom inset; `rebuildDock()` — 54dp app tiles, 10dp gaps, 13dp squircle radius, 26dp glyphs, 1.13 focus scale and 190ms focus animation; `updateDockTooltip()` — separate 11.5sp name tooltip, 12dp/5dp padding, 340dp width, 26dp + 5dp pointer height and 5dp Dock gap.
- `ControlPanelOverlay.java`: 141dp columns, 11dp row/column gap, 56dp capsule height, 25dp icon, 12.5sp title, 10.5sp status, 14dp/12dp horizontal padding, 10dp icon-to-label gap, 18dp weather capsule radius; panel placement 36dp from top and 28dp from right. RFTV adapts these values to its existing actions only.
- `MainActivity.java`: shared LiquidGlass and focus treatment reference. Settings retains RFTV sections and DPAD behavior while adopting compact 56dp navigation rhythm, subtle 0x38FFFFFF borders and bright focused surfaces.

All values are ported into Kotlin/Android Views. No donor Java implementation, fonts, icons, or donor-only product features are copied.


### Settings Activity visual reference (Iteration 3)

The Settings navigation and content layout also reference the Apache-2.0 donor `SettingsActivity.java`:
- `buildUi()`: 44dp/28dp/48dp/22dp outer content insets, 288dp left column, 26dp sidebar-to-content gap, 24sp sidebar title, 20sp right-pane heading, 18dp right list horizontal inset and 8dp top inset.
- `buildSidebarRows()`: 27dp icon containers, 15dp glyphs, 14.5sp labels, 11dp icon-to-label gap, 35dp minimum row height, 7dp/4dp/14dp row padding, 12dp pill radius, 2dp row spacing.
- `updateSidebarStyles()`: focused white pill, selected 0x22FFFFFF pill, resting 0x1AFFFFFF icon well; focus scale 1.02 and 160ms.
- Bottom hint: 10.5sp text, 12dp radius, 0xD916161F fill, 0x26FFFFFF stroke and 14dp/7dp padding.

These are measured donor source values adapted to RFTV's existing eight-section settings screen; no donor SettingsActivity implementation or navigation logic is copied.
