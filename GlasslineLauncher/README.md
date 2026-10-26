# Glassline Launcher

Glassline is a privacy-first Android home launcher written in Kotlin. The continuously recycled app drawer and workspace use native Android Views, `RecyclerView`, `GridLayoutManager`, and `ViewPager2`; Material Components provides theme tokens and the draggable bottom sheet. Compose is intentionally not used on the high-frequency icon-grid path. No network permission or analytics SDK is included.

## Build requirements

- Android Studio with JDK 17
- Android SDK Platform 36 and matching build tools
- Gradle 8.11.1 or compatible with Android Gradle Plugin 8.10.1
- A connected Android 13+ device or emulator for instrumentation and macrobenchmark runs

The supplied project does not contain `gradlew`, `gradlew.bat`, or `gradle-wrapper.jar`. Use an installed Gradle executable from the project root, or generate a wrapper in an environment with Gradle installed. This workspace had no Gradle executable, Android SDK, or network access to regenerate the wrapper.

## Build, test, and profiling commands

Run these from the project root in an Android development environment:

```bash
# Unit tests
 gradle :app:testDebugUnitTest

# Debug APK
 gradle :app:assembleDebug

# Instrumentation tests on a connected device/emulator
 gradle :app:connectedDebugAndroidTest

# Release APK with minification and scoped ProGuard rules
 gradle :app:assembleRelease

# Generate a Baseline Profile from a connected Android 9+ device
 gradle :app:generateBaselineProfile

# Run Macrobenchmark instrumentation tests on a connected device
 gradle :benchmark:connectedAndroidTest
```

Install the debug APK and select Glassline in **Settings → Apps → Default apps → Home app**. `LauncherActivity` declares `MAIN`, `HOME`, and `DEFAULT`; Android owns the default-home selection/role flow.

## Feature inventory

### Workspace and home

- Three swipeable pages with an animated, accessible page indicator.
- Versioned JSON workspace model in app-private storage (`Workspace.kt`); app items, folders, widgets, shortcuts, page coordinates, and dock entries are persisted.
- Installed apps appear in the drawer until pinned. The default dock attempts to resolve Phone, Messages, Browser, and Camera package variants and always includes the app-drawer pill.
- `ItemTouchHelper` reordering, adjacent-page drag targeting, folder creation by dropping one app onto another, folder rename, child extraction, and folder dissolution.
- Folder cells display a compact 2×2 icon preview; empty home cells open workspace actions.
- App-widget picker, `AppWidgetHostView`, provider configuration, system pin flow, resize handles, persisted IDs, and ID cleanup on removal.
- Long-press on the home surface offers settings, wallpaper, and widget actions.
- Backup and restore use the Storage Access Framework. JSON schema versions are checked before a restore replaces the saved workspace. No cloud service is used.

### App drawer and app actions

- `LauncherApps.getActivityList` discovery across profiles visible to the launcher; package callbacks refresh the app list after add/remove/change events.
- App discovery and icon resolution happen on `Dispatchers.IO`; the UI receives immutable app-list snapshots. The icon cache is size-bounded.
- Alphabetical sections, an A–Z rail, 200 ms debounced search, Unicode/diacritic normalization, prefix/word-boundary/contains ranking, and local recency/frequency scoring.
- Search text is preserved across refreshes. Hidden package names persist in SharedPreferences; Settings exposes an Unhide all action.
- Long-press actions include app info, Android's uninstall confirmation, pin to home, add to dock, move to page, hide/unhide, per-app icon override, and available launcher shortcuts.
- App launch uses `LauncherApps.startMainActivity` with a scale-up animation when motion is enabled. Shortcut launch uses `LauncherApps.startShortcut` and updates local usage ranking.

### Personalization and platform integration

- Live grid columns, icon size, labels, six accent choices, day/night palettes, bundled Roboto fonts, edge-to-edge insets, and a wallpaper toggle.
- ADW/GO/Nova-style icon-pack discovery, `appfilter.xml` mappings, per-app overrides, bounded drawable cache, and system-icon fallback.
- Static wallpaper chooser and live wallpaper chooser. Cached wallpaper is invalidated on `ACTION_WALLPAPER_CHANGED`; no blur is used.
- Gestures: swipe up opens the drawer; swipe down closes the drawer; home swipe down can open search or request the notification shade. Horizontal motion is excluded from the vertical drawer gesture.
- Double-tap is configurable: do nothing by default, open focused search, or lock the device. Locking uses `DevicePolicyManager.lockNow()` only after the user explicitly enables Device Admin.
- Notification-shade gesture uses an opt-in accessibility service and `GLOBAL_ACTION_NOTIFICATIONS`; the service declares `canRetrieveWindowContent=false` and does not read screen content.
- Reduced-motion checks use `Settings.Global.ANIMATOR_DURATION_SCALE`; custom swipe gestures are suppressed while touch exploration is active.
- RTL is enabled in the manifest; interactive launch targets and page indicators have content descriptions.

## Architecture and source map

| File or module | Responsibility |
|---|---|
| `app/src/main/java/com/glassline/launcher/LauncherActivity.kt` | Home/drawer UI, gestures, package discovery, filtering, workspace rendering, widgets, clock, wallpaper, app actions. |
| `SettingsActivity.kt` | Grid/icon preferences, accent, hidden-app reset, wallpaper, accessibility/device-admin consent, backup/restore. |
| `Workspace.kt` | Sealed workspace item model, versioned JSON codec, internal persistence. |
| `WorkspaceOperations.kt` | Immutable page movement, reorder, folder, and dock transformations. |
| `AppUsageTracker.kt` | Local recency/frequency ranking and query match strength. |
| `IconPackManager.kt` | ADW/GO/Nova-style icon-pack discovery, appfilter parsing, overrides, bounded drawable cache. |
| `PageIndicatorView.kt` | Lightweight animated page indicator. |
| `GlasslineAccessibilityService.kt` | Opt-in notification-shade global action. |
| `GlasslineDeviceAdminReceiver.kt` | Device Admin receiver for user-authorized device locking. |
| `WidgetPinReceiver.kt` | Handles the widget-pin callback and persists the widget item. |
| `GlasslineApplication.kt` | Debug StrictMode and startup timing diagnostic. |
| `app/src/main/res/values/` and `values-night/` | Day/night color resources and Material theme roles. |
| `app/src/main/res/font/` | Bundled Roboto font family; license at `app/src/main/assets/licenses/ROBOTO-APACHE-2.0.txt`. |
| `baselineprofile/` | Connected-device Baseline Profile generator. |
| `benchmark/` | Macrobenchmark startup and frame-timing scenarios. |

## Tests

### Unit tests

`app/src/test/java/com/glassline/launcher/BasicLogicTest.kt` covers:

- Query trimming, case folding, Unicode normalization, and label/package matching.
- Prefix/word-boundary/contains ranking and usage-score changes.
- Workspace serialization round-trip and unsupported schema rejection.
- Folder creation, rename, child extraction, and dissolution.
- Page movement, dock removal, hidden-app persistence, and column-count clamping.

### Instrumentation tests

- `LauncherSmokeTest`: startup, swipe-up drawer opening, swipe-down closing, horizontal-swipe isolation, alphabetical rail, search empty state, empty-page actions, paging, hidden-app persistence, live grid columns, app-action menu items, and day/night resource behavior.
- `FolderDropInstrumentationTest`: pins two installed apps, drags one onto the other, and checks persisted folder creation.

### Macrobenchmarks

`benchmark/src/androidTest/java/com/glassline/launcher/benchmark/LauncherMacrobenchmark.kt` defines cold startup with `CompilationMode.None()` and `CompilationMode.Full()`, drawer scroll with a 200-app precondition, drawer opening, page swiping, and icon drag scenarios using `StartupTimingMetric` and `FrameTimingMetric`.

## Performance and verification status

**Runtime verification status: NOT RUN in this environment.** There is no Android SDK, `adb`, installed Gradle executable, or attached Galaxy A34. No Kotlin compilation, JVM test, instrumentation test, release APK, screenshot, frame-time, cold-start, battery, leak, or runtime StrictMode result is claimed.

The 120 Hz budget is 8.33 ms per frame. The sub-second cold-start goal and consistent 120 fps behavior remain targets until measured on the actual device. The debug startup log (`GlasslineStartup`) reports an app-process elapsed-time diagnostic; it is not a substitute for Macrobenchmark's cold-start metric.

For repeatable measurement, record the device model, Android build, 60/120 Hz refresh setting, battery/thermal state, and app version. Run the benchmark commands above, retain the Macrobenchmark output, inspect `adb logcat` for StrictMode messages, observe LeakCanary after repeated drawer open/close, rotation, and settings return, and compare `adb shell dumpsys batterystats` around a fixed idle/interaction sequence.

## Galaxy A34 QA checklist

- [ ] Install debug APK; select Glassline as Home app; verify after reboot.
- [ ] Swipe up from grid, dock, and empty home space; swipe down from drawer; verify horizontal swipes do not open the drawer.
- [ ] Change columns, icon size, labels, accent, and hidden-app state while running.
- [ ] Search, leave/return from an app, and confirm the query is preserved after package refresh.
- [ ] Install/uninstall an app and verify drawer refresh.
- [ ] Pin apps, reorder them, move between pages, create/rename/dissolve folders, and extract folder children.
- [ ] Add widgets from multiple providers; test provider configuration, resizing, removal, rotation, and orphan-ID cleanup.
- [ ] Test an icon pack with `appfilter.xml`, missing-icon fallback, and per-app override.
- [ ] Change static/live wallpaper and verify cached wallpaper refresh.
- [ ] Explicitly enable and disable accessibility and Device Admin flows; verify notification shade and lock behavior.
- [ ] Toggle light/dark mode; set Arabic RTL; test TalkBack, 200% font scale, and animator duration scale 0.
- [ ] Run unit tests, instrumentation tests, Macrobenchmark, and release build.
- [ ] Inspect StrictMode, LeakCanary, startup metrics, frame timing at 60 Hz and 120 Hz, and battery statistics.

## Platform constraints and consent

- A third-party launcher cannot silently lock a device. Glassline uses Device Admin only after the user confirms Android's activation screen; users can revoke it from Glassline Settings or Android Settings.
- Notification-shade access requires the user to enable Glassline's accessibility service in Android Settings. OEM behavior may reject the global action; Glassline then explains the requirement and opens focused search as a fallback.
- Android package visibility and work-profile access remain subject to the OS and device policy. Glassline does not attempt to bypass those rules.
- Android provides no public launcher-side `ReportLongcutSelection` API for another app's shortcuts. Glassline uses `LauncherApps.getShortcuts`/`startShortcut` and its own local usage tracker; publisher-side shortcut reporting APIs cannot report usage for arbitrary other packages.
- Google Play's current mobile submission rule, effective 31 August 2026, requires new apps and updates to target Android 16 / API 36 or higher. Glassline's build configuration targets API 36. See the [official Google Play target API requirement](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-EN).
