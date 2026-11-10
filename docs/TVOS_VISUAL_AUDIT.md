# RFTV Visual Shell Audit — tvOS Reference

Audit date: 2026-11-10  
Audited base: `main` at `4981dce3629592ac1edb243ba2e6c7cae2fe23fa`  
Release baseline: `v0.9.4` (stable, published and verified)  
Implementation branch: `feature/visual-shell-foundation`

## 1. Scope and sources

This audit covers visual composition, reusable UI, focus/navigation, and animation. It does not authorize new user-facing functionality and does not expand TV Manager.

Primary donor source (Apache-2.0):
- `SceneHome.java` — home composition, top controls, dock, focus, tooltip.
- `Scenes.java` — layered scenes, clock rendering, foreground cut-outs, scene transitions.
- `HomeDashboard.java` — glass card/focus treatment (visual reference only; do not import dashboard features).
- `ControlPanelOverlay.java` — panel geometry, tile composition, DPAD navigation, blur strategy.
- `SettingsActivity.java` — persistent sidebar, right pane, nested-page stack and focus restoration.
- `MainActivity.java` / `Ui.java` — `LiquidGlass`, geometry and shared drawing primitives.

RFTV source reviewed:
- `MainActivity.kt` (4,264 lines): Home, Dock, Control Center, settings, wallpaper selection, catalog, diagnostics, OTA and TV Manager orchestration.
- `RftvDesignSystem.kt` (353 lines): current tokens and shared surface/focus helpers.
- `GlassBackdrop.kt`: API 24+ downsampled snapshot blur.
- `SettingsNavigationSpec.kt`: current settings glyph mapping.
- `DockWidthPolicy.kt`, `CatalogReturnPolicy.kt`, `DockTooltipDrawable.kt`, `RftvControlIconView.kt`, `OverlaySnapshotSizing.kt`: existing reusable policies and drawing helpers.
- `DONOR_ANALYSIS.md`: prior product-owner decisions, including layered clock scenes and visual boundaries.

The donor's feature set is not a backlog. Do not add its dashboard widgets, Continue Watching, Recent Apps, button mapper, accessibility overlay, profiles, search or other functions as part of this visual phase.

## 2. Current RFTV screen inventory

| Screen / surface | Current implementation | Current state |
|---|---|---|
| Home | `MainActivity.showHome()` | Fullscreen photo, center clock/date, top-right weather, settings shortcut, bottom Dock |
| Dock | `buildDockContent()`, `buildAppRow()` | Favorites, All Apps, dynamic width, focus scale, tooltip |
| App catalog | `showAppCatalog()` | Focusable icon grid, async icon loading, launch and long-press actions |
| Control Center | `showControlCenter()` | Right-side panel, existing system actions, focus graph and return focus |
| Settings hub | `showLauncherSettings()` | Persistent left navigation and right content pane |
| Appearance | `showAppearanceSettings()`, `addWallpaperSettings()` | Day/night wallpaper collections, picker, carousel interval |
| Dock settings | `showDockSettings()` | Existing width/height settings |
| Screensaver settings | `showScreensaverSettings()` | Existing timer and system settings shortcut |
| App settings / hidden apps | `showAppSettings()`, `showHiddenAppsPage()` | Existing catalog and visibility controls |
| Weather | `showWeather()` | Forecast page |
| TV Manager | `showTvManager()` and related methods | Existing separate functional direction; out of scope for expansion |
| Diagnostics | `showDiagnosticsScreen()` | Existing diagnostics surface |
| About / OTA | `showAboutSystem()` | Version and update flow |
| Screensaver | `ScreensaverActivity`, `RftvDreamService` | Separate Activity and DreamService |

Most visual screens are assembled programmatically inside one Activity. There are no screen-specific View classes for Home, Dock, Control Center or Settings.

## 3. Concrete findings

### 3.1 Home and clock

RFTV currently builds one `ImageView` wallpaper, one dark readability gradient, a top-right weather pill/settings button, a centered text clock/date column, and the Dock. Clock text is not a glass-rendered scene element.

The donor's `Scenes.SceneView` draws a defined layer stack:
1. photographic background;
2. scene-specific shade;
3. glass clock with adaptive luminance treatment;
4. date/weather/greeting composition;
5. foreground image cut-out drawn over the clock to create depth.

The prior RFTV owner clarification in `DONOR_ANALYSIS.md` specifies 3–5 complete layered scenes and a transparent foreground cut-out partially overlapping the clock. The current RFTV Home has no foreground cut-out layer and therefore does not meet that recorded visual requirement. The 20 day/night photographs are useful wallpaper content, but flat photographs alone do not create the intended layered-scene composition.

Current Home weather is a top-right pill. The prior owner clarification describes weather beside the clock/date composition. Treat that as an existing visual requirement, not as permission to add weather functionality.

### 3.2 Dock

RFTV already matches several donor geometry values:
- 54dp tile size;
- 10dp tile gap;
- 13dp horizontal edge inset;
- 14dp bottom inset;
- 1.13x focused scale;
- content-driven width from favorites plus All Apps, bounded by the user's width preference.

The main visual difference is treatment: RFTV draws a translucent/stroked surface behind individual tiles as well as the outer Dock capsule. The donor uses full-bleed rounded-square app icons, a clean focus ring and one prominent frosted Dock capsule. The RFTV implementation should preserve app order, favorite behavior, hidden apps, All Apps and launch semantics while reducing redundant per-icon framing.

Donor focus behavior also includes a consistent easing curve, tooltip aligned to the focused tile, and explicit restoration of the focused tile after rebuilding. RFTV has working focus scale/tooltip and the Iteration 4 catalog-return fix; do not replace those policies without regression tests.

### 3.3 Control Center

RFTV uses a fixed 293dp panel width but lays most controls out as full-width vertical rows. Theme and RAM are paired; the remaining existing controls are full-width. The donor's 293dp panel uses two 141dp capsules with an 11dp gap, compact icon-only shortcuts, and a bottom full-width weather tile where that feature exists. For RFTV, adapt the composition to its existing controls only; do not add donor tiles.

RFTV already has explicit focus-row IDs, initial focus on Wi-Fi, a right-side placement, reveal animation, and return-focus logic. Keep these mechanics and improve their consistency.

The supplied device photo shows the Dock visually ghosting through the lower Control Center region. The current `captureBackdropSnapshot()` / `addGlassBackdrop()` pipeline blurs a full-screen snapshot, so Home UI can be baked into the panel backdrop. Fix the backdrop source/crop or exclude foreground UI; do not hide this with a stronger tint. Verify that the panel and Dock have a clean visual boundary.

### 3.4 Glass rendering

RFTV `GlassBackdrop` creates a low-resolution blurred bitmap (radius 7, 3 passes) off the UI thread. Dock separately crops the wallpaper region under its bounds and blurs that crop. This is a safe Android 9-compatible frosted snapshot fallback, but it is not equivalent to the donor's `LiquidGlass`.

The donor Drawable maps a backdrop BitmapShader to the surface's screen coordinates, optionally magnifies the edge band (lensing), samples local luminance for adaptive tint, and draws a directional specular rim/glow. Its glass is not just a uniform blurred image plus dark scrim.

For API 28, use a cached low-resolution backdrop and shader-based surface rendering; never require Android 12 `RenderEffect`. Any higher-quality path must be measured on the MT9632/ARMv7 target. Keep blur generation off the UI thread and reuse bitmaps.

### 3.5 Settings

RFTV already has the correct high-level two-column shell: sidebar plus right pane. It is a useful base, not a reason to replace the whole screen.

The weakness is the content grammar. The hub and many pages use large `infoCard` / `controlTile` blocks with repeated borders and surfaces. `showPanel()` is a generic centered page shell used by many settings subpages. `SettingsNavigationSpec` currently maps only section glyphs; it does not model a nested page hierarchy.

The donor's Settings implementation uses compact 35dp sidebar rows, small squircle icons, selected/focused states, section headers, setting rows, choice rows, info rows, and a real nested-page stack. It preserves parent section and focus index through `subMenuStack` and `subMenuParentFocusIdx`. RFTV currently returns Back from a settings page to the settings hub; it does not provide the same general nested-page stack/focus restoration.

Rework the presentation and navigation model while retaining all existing RFTV setting actions and persistence. Do not turn each preference into another large card.

### 3.6 Design tokens and code organization

RFTV has a real starting design system in `RftvDesignSystem.kt`: Color, Spacing, GlassAlpha, Radius, Elevation, Size and Motion tokens plus shared surface/focus helpers.

It is not yet a complete system:
- `MainActivity.kt` is 4,264 lines.
- A source scan found approximately 306 literal `dp(number)` calls, 22 literal `textSize` assignments and 29 hex color literals in that file.
- Several view builders still define their own dimensions, text sizes, alpha and focus treatment.
- `SettingsNavigationSpec.kt` is only a glyph list, not a navigation model.

This is measurable token leakage, not a reason for a wholesale rewrite. Extract rendering components incrementally while MainActivity remains the coordinator for lifecycle, data and actions.

## 4. Reuse / refactor / keep out of scope

### Reuse
- `DockWidthPolicy`, `CatalogReturnPolicy` and their tests.
- `GlassBackdrop` as the API 24+ fallback blur engine.
- `OverlaySnapshotSizing` and existing overlay performance diagnostics.
- `RftvControlIconView`, `DockTooltipDrawable`.
- Wallpaper loading/cache/fallback, weather repository and existing preference persistence.
- Current app discovery, favorites/hidden package state, launch and OTA logic.

### Refactor visually
- Home scene composition and clock rendering.
- Dock item/surface composition and shared focus treatment.
- Control Center tile layout, surface rendering and backdrop composition.
- Settings shell content grammar, page model and focus restoration.
- Common dialog, popup, focus and transition components.
- Design tokens to cover typography, dimensions, glass materials, states and motion consistently.

### Keep unchanged in this phase
- TV Manager package analysis, privileged bridge and package operations.
- OTA download/install/security pipeline and persistent Release signing key.
- Screensaver data/service functionality.
- App discovery and app launch behavior.
- Existing weather and wallpaper capabilities (visual composition may change; do not add providers/services/features).

## 5. Proposed incremental UI architecture

Use Kotlin + Android Views; do not introduce Compose or raise minSdk.

1. **Design tokens:** complete `RftvDesignSystem` with geometry, typography, materials, focus states and motion curves. Remove duplicated literals as components are extracted.
2. **Glass material:** one shared Drawable/View renderer with cached backdrop mapping, adaptive tint, subtle lensing and directional rim. API 28 fallback remains snapshot/shader based.
3. **Focus and motion:** common focus animator and explicit focus-boundary/return helpers; each screen supplies its navigation graph and remembered focus target.
4. **Home scene:** dedicated renderer for background, foreground cut-out, clock/date/weather composition and transitions. MainActivity owns data and lifecycle.
5. **Dock:** dedicated View group for the existing app set, preserving favorites/order/All Apps and launch behavior.
6. **Control Center:** dedicated panel view using the existing actions and focus graph.
7. **Settings:** shell + section/page model + reusable setting rows/choices; nested stack remembers parent page and focus.
8. **Dialogs/popups:** shared surface, focus and enter/exit transitions.

Avoid introducing a new Activity for every settings page. Prefer a stable shell and page content replacement so focus and scroll state can be restored deterministically.

## 6. Implementation order

| Block | Scope | Acceptance |
|---|---|---|
| 0 | Tokens, Glass Surface, Focus/Motion primitives | No visual regressions in existing screens; API 28-safe; blur remains off UI thread |
| 1 | Home composition | Layer order, clock geometry, date/weather balance and scene transition match the donor pattern |
| 2 | Dock | Single capsule, correct icon geometry, 1.13x focus, tooltip alignment, no clipped scaling, unchanged app semantics |
| 3 | Control Center | Donor-like compact composition using only current controls; no Dock ghosting/overlap; initial/return focus correct |
| 4 | Wallpapers, clock and themes | Existing day/night sets, preview/selection/carousel and persistence; 3–5 layered scenes; no image reload per clock tick |
| 5 | Settings shell and nested pages | Compact iOS-style rows/choices, Back returns to parent, parent focus and scroll restored |
| 6 | Dialogs, popups and global transitions | Shared material/motion/focus; no remote navigation traps |

Each block must pass unit tests, lint, build, CI, API/ABI review and a dedicated DPAD acceptance checklist before moving to the next block. Physical TV acceptance remains separate and must be confirmed by the owner.

## 7. Repository and release state

- `main` is at merge commit `4981dce`.
- PR #42 is merged.
- Stable `v0.9.4` is published. APK SHA-256: `398ec55a1ce62cccde33afccc7827356016c145c1e097e745b4551457a1174c7`.
- Release package/version: `com.rftv.launcher`, `0.9.4`, versionCode `16`; ARMv7 ABI check passed; signing certificate SHA-256 matches the established permanent certificate `22b4f71da5f543e933780f05f8573783ad4718036b5bfc1b36fe258695e30917`.
- Main CI and publisher succeeded. This is automated verification only; physical TV test is not confirmed.
- One unrelated old documentation PR (#15) remains open. Several older visual branches are heavily behind main (roughly 110–137 commits); their small token changes are historical references, not safe merge candidates.

No TV Manager, OTA or Release signing changes are part of the visual-shell branch.
