# LVNG -X — Android source development

Package: `com.lvngx.app`  
Version: `0.5.0`  
Min SDK: 26 · Target SDK: 35 · Compile SDK: 35 · JDK 17 · Gradle 8.9

This project continues the supplied LVNG -X source tree. The existing package, launcher branding, Shizuku provider/API, and GitHub Actions workflow are retained. This is an in-progress development source, not a tested final release.

## Implemented in this development pass

- Retained official LVNG -X launcher and floating-overlay logo assets.
- Retained local game library, installed-app picker, launch/remove actions, and Shizuku status checks.
- Crimson/dark floating control panel with three-column quick actions, RAM usage from Android `ActivityManager`, and honest `N/A` values for system-wide FPS/CPU metrics that are not available through the current implementation.
- Draggable floating logo bubble, edge docking, long-press conversion to a slim edge handle, outside-tap/close behavior, and normalized position persistence.
- Separate floating circular `MACRO` trigger; hold dispatches repeated tap gestures and release stops the loop. Trigger position is persisted in normalized coordinates and can be unlocked temporarily from the Macro page.
- AccessibilityService declaration and configuration with `canPerformGestures=true`; it does not inspect screen content or collect personal data.
- Macro target X/Y normalized coordinate settings, 120–1200 ms interval configuration, trigger size/opacity settings, named local profiles, and emergency stop.
- Macro loop stops on finger release, screen-off, accessibility interruption/unbind, overlay service destruction, and explicit Emergency Stop. An in-flight Android gesture may complete because Android does not expose a universal cancellation API for a dispatched gesture.
- Touch test inside LVNG -X with pointer count, coordinates, tap count, elapsed session time, and average CPS based only on events received by that test surface.
- Backup now includes game list and macro preferences/profile list with SHA-256 integrity validation.

## Still incomplete / not claimed as implemented

- Full visual parity with the supplied reference panel across every screen size and OEM overlay implementation.
- Drag-preview target marker and a separate trigger/target visual editor canvas.
- Full keymap editor and universal game key remapping (Android does not provide universal remapping of arbitrary third-party games).
- Controller test dashboard and detailed axis/button event explorer.
- Touch calibration that affects system touchscreen drivers (LVNG -X cannot change OEM touchscreen firmware calibration).
- System-wide CPS, crosshair injection, true game FPS telemetry, CPU usage telemetry, CPU/GPU tuning, RAM boosting, resolution forcing, or thermal controls.
- Full profile management UX (duplicate/rename conflict workflow), migration framework, complete diagnostics matrix, broad OEM/device testing, and production hardening.

## Permissions

- `SYSTEM_ALERT_WINDOW`: required only for floating bubble, macro trigger, and overlay panel.
- Accessibility Service (`BIND_ACCESSIBILITY_SERVICE`): user must enable `LVNG -X Macro Engine` manually in Android Accessibility settings. Used only to dispatch the user-configured tap gesture while the trigger is held.
- `QUERY_ALL_PACKAGES`: used by the installed-app picker/game library. Distribution through app stores may require policy justification.
- Shizuku provider/API: retained from the original project; Shizuku server and its permission remain separate from overlay and Accessibility permissions.

No credit, paid-token, premium billing, subscription, or paywall implementation was found in the supplied source tree. No billing SDK was added.

## Build with GitHub Actions

1. Push the contents of this project folder to a GitHub repository.
2. Run **Android Build** from GitHub Actions or push to `main`/`master`.
3. Download the `LVNG-X-debug-apk` artifact from the workflow run.

The local environment used for this development pass does not contain Android SDK/Gradle, so an APK build, device test, orientation test, and real-game gesture compatibility test have **not** been performed. XML resources and manifest are parsed locally; Kotlin syntax was checked for parser errors, but that is not a substitute for Android compilation.

## Important compatibility notes

- Macro injection depends on Android Accessibility gesture support and the target app/game. Some games may reject or ignore synthetic gestures.
- Gesture injection is not a guarantee of game compatibility and should be tested manually. The service is opt-in and requires explicit user activation.
- Overlay permission is independent from Accessibility and Shizuku permission.
- Shizuku server startup is a user action; LVNG -X does not silently start it or request root.
- System-wide FPS/CPU telemetry is not fabricated; unavailable values are shown as `N/A`.
