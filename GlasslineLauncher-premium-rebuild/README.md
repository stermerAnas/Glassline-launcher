# Glassline Launcher — premium rebuild

This is a clean replacement project for the earlier Glassline prototype. It keeps the launcher lightweight and prioritizes fast first paint, responsive app discovery, and restrained animation instead of adding expensive effects.

## Improvements

- **Fast initial app inventory:** app labels and activity metadata are queried off the UI thread. Icon bitmaps are not created up front.
- **Visible-icon loading:** tiles request their icons only when composed. Icon work runs on `Dispatchers.IO` and uses a bounded 12 MiB LRU cache, so the first app-library open does not have to wait for every installed app's icon.
- **Launcher-style launch motion:** opening an app uses an Android scale-up animation anchored to the tapped icon's bounds when possible. A plain launch is used as a fallback if the animation option fails.
- **Interruptible drawer motion:** spring-based motion replaces fixed-duration fade transitions. Alpha, scale and translation are applied using `graphicsLayer`; animated state is read in the layer lambda so it avoids triggering a full layout pass for every animation frame.
- **Correct animation lifecycle:** the drawer remains mounted until its transition actually finishes rather than being removed after a guessed delay.
- **A cleaner visual hierarchy:** an expressive clock, quiet metadata, consistent rounded geometry, a subtle background glow, a compact favorites dock and a separate app-library surface.
- **Real app-library behavior:** stable lazy-grid item keys/content types, searchable labels and package IDs, useful empty-search state, configurable 3/4/5-column grid, long-press pinning and Android app info.
- **Less keyboard friction:** the app library applies IME insets, and its search field can be focused directly from the home search entry.
- **Persistent settings and dock order:** dark/light appearance, grid density and pinned apps survive restarts.
- **Package updates:** newly installed, removed or updated apps refresh the inventory and invalidate the matching icon cache entries.

## Build

Requirements: JDK 17, Android SDK platform 35/build tools 35.0.0, and Gradle 8.11.1.

```bash
gradle -p GlasslineLauncher :app:assembleDebug
```

The debug APK is written to:

`GlasslineLauncher/app/build/outputs/apk/debug/app-debug.apk`

The included GitHub Actions workflow builds that same folder and uploads an artifact named `GlasslineLauncher-debug-apk` for 14 days. It no longer extracts a separate ZIP or applies fragile search-and-replace patches.

## Install and try it

1. Build the debug APK through Android Studio or the included GitHub Actions workflow.
2. Install it on your phone and open **Glassline**.
3. Select it as your default Home app when Android prompts you, or choose it from Android's default Home-app settings.
4. Swipe up to open the app library. Tap an app to launch it; long-press to pin it to the dock or open Android app info. Use the settings button to change theme and grid density.

## Validation status

The XML resources were parsed locally and the Kotlin sources were reviewed for syntax, but **a full Android build and on-device animation test have not been completed here** because this environment has no Android SDK/Gradle installation and cannot download dependencies. Use the included CI workflow as the first full compile check, then evaluate gesture feel and frame pacing on the actual phone; no source-only review can honestly guarantee Nova Launcher-level animation quality.

This is a sideloadable development build, not a Play Store release. `QUERY_ALL_PACKAGES` is included because a launcher needs visibility into launchable apps; Play distribution has separate policy requirements for that permission.
