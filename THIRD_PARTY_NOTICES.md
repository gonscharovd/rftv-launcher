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
