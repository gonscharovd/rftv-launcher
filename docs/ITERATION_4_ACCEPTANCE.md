# Iteration 4 — acceptance and implementation status

Last reconciled: 2026-11-10. Stable baseline: v0.9.3 / versionCode 15.

## 4A — Catalog exit and remote focus

Root cause found in `MainActivity.kt`:
- Home rebuild checks for tag `rftv-all-apps-trigger` when `returnFocusToCatalogButton` is true.
- Dock tile construction did not assign that tag to the All Apps tile.
- The lookup therefore returned null; because the code skipped the fallback branch in this case, the rebuilt Home did not explicitly restore focus.
- The app-launch path also left `page == "panel"` and the catalog as the active panel. `onResume()` did not reconcile this pending catalog launch with the Home screen.

Fix on the Iteration 4 branch:
- Assign a stable tag to the All Apps Dock tile.
- Request that tile explicitly after Back/Close; fall back to the first app if the tile is not available.
- Track app launches originating in the catalog and rebuild Home on return in `onResume()`.
- Add a pure policy and unit tests for the lifecycle return condition and stable tag.

## Required physical checks
1. Catalog → Back.
2. Catalog → Close button.
3. Catalog → launch app → return via Home/Back.
4. For each: verify DPAD four directions, OK, Back; repeat five times.
5. Confirm no app crash and no lost focus on the target Sber TV.

## 4B and 4C — not yet implemented
- 4B: responsive content-sized Dock width with existing squircle tile metrics and centered layout.
- 4C: audit and unify real frosted-glass backing for Dock, Control Center, Settings and catalog using a shared low-resolution blur path; preserve performance and avoid adding user-facing opacity controls.

Do not mark the iteration complete until 4A–4C, automated checks, PR review and physical acceptance status are reported separately.
