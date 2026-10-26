# Glassline Launcher verification report

Date: 2026-10-26. This report separates source inspection from execution. The current environment has no `gradle`, `adb`, Android SDK `android.jar`, or connected Android device. Source-level assertions and XML parsing ran successfully. A `kotlinc` 1.9.0 parser pass was attempted, but full compilation cannot resolve Android/AndroidX dependencies without the SDK and Gradle classpath.

## Phase 1 — bug fixes

| Requirement | Source location | Verification evidence |
|---|---|---|
| Lifecycle-aware clock, no redundant updates | `LauncherActivity.kt`: `onStart`, `onStop`, `updateClock` | Source contains time tick, time/timezone/locale change filters and compares formatted text before assigning. Runtime receiver delivery is NOT RUN. |
| Swipe direction and gesture interception | `LauncherActivity.kt`: `dispatchTouchEvent` | Source assertion confirms swipe-up opens drawer and swipe-down closes it; horizontal-dominant motion disqualifies gesture. Instrumentation test: `swipeUpFromGridOpensDrawer`. NOT RUN. |
| Live settings | `LauncherActivity.kt`: `onSharedPreferenceChanged`; `LauncherSmokeTest.kt`: `columnPreferenceAppliesToExistingGridImmediately` | Source assertion confirms preference listener and immediate span update. Test NOT RUN. |
| Off-main-thread app discovery and bounded icons | `LauncherActivity.kt`: `reloadApps`, `refreshPackage`, `appIconCache` | Source assertion confirms `Dispatchers.IO`, package-specific refresh and `LruCache<String, Drawable>(96)`. NOT RUN under StrictMode. |
| Search state and debounce | `LauncherActivity.kt`: `scheduleFilter`, `AppAdapter.filter` | Source has one `query` field, cancellable job, 200 ms debounce, and re-filtering after submit. Search instrumentation test NOT RUN. |
| Reused RecyclerViews/adapters and fixed-size removal | `LauncherActivity.kt`: `onCreate`, `showHome`, `showDrawer` | Source inspection confirms persistent grid/adapter objects and no `setHasFixedSize(true)`. Runtime allocation profile NOT RUN. |
| Uninstall/package changes and hidden apps | `LauncherActivity.kt`: `launcherAppsCallback`, `refreshPackage`; `LauncherLogic.readHiddenApps/saveHiddenApps` | Unit test: `hiddenAppPersistenceRoundTripsThroughSharedPreferences`; package callbacks are source-inspected. NOT RUN. |
| API 33 package query flags | `IconPackManager.kt`: `queryActivities`, `queryActivitiesLegacy` | Source uses `ResolveInfoFlags.of(0L)` on API 33+ and confines deprecation suppression to the legacy helper. |
| Day/night colors | `res/values/colors.xml`, `res/values-night/colors.xml`, `LauncherSmokeTest.kt` | XML parses; test `lightAndDarkThemeResourcesRebindAfterNightModeChange` exists. NOT RUN. |
| Scoped shrinking rules | `app/build.gradle.kts`, `app/proguard-rules.pro` | Release build enables minification and rules keep only manifest components plus source line attributes. Release build NOT RUN. |
| Real unit/instrumentation tests | `BasicLogicTest.kt`, `LauncherSmokeTest.kt` | Test sources inspected; no tautological fixed-list equality test remains. Test suites NOT RUN. |

## Phases 2–5 — feature source map

| Feature | Source location | Verification |
|---|---|---|
| Sealed workspace model and versioned JSON | `Workspace.kt`: `HomeItem`, `WorkspaceJson`, `WorkspaceStore` | Source assertions passed; round-trip and unsupported-schema tests exist but NOT RUN. |
| Workspace-backed pages and dock | `LauncherActivity.kt`: `reloadWorkspace`, `maybeInitializeWorkspace`, `WorkspacePagerAdapter`, `renderDock` | Source inspection and static assertions passed. UI behavior NOT RUN. |
| SAF backup/restore | `SettingsActivity.kt`: export/import activity-result launchers; `WorkspaceStore.replaceFromBackup` | Source inspection confirms document intents and schema validation. NOT RUN. |
| Drag, folders, and dock | `LauncherActivity.kt`: `workspaceDragCallback`, `WorkspaceOperations`; `WorkspaceOperations.kt` | Static source assertions passed; folder unit and UI drag tests exist but NOT RUN. |
| Shortcut launch and pin | `LauncherActivity.kt`: `launchShortcut`, `pinShortcut`, app menu | Source inspection confirms LauncherApps shortcut query/start paths. Third-party shortcut compatibility NOT RUN. |
| Widgets | `LauncherActivity.kt`: `showWidgetPicker`, `configureOrPlaceWidget`, `removeWidget`, `resizeWidget`; `WidgetPinReceiver.kt` | Source inspection confirms host lifecycle, binding/configuration paths, mutable pin callback on API 31+, persistence and cleanup. Real providers NOT RUN. |
| Icon packs | `IconPackManager.kt`; `<queries>` in `AndroidManifest.xml` | Source inspection confirms ADW/GO/Nova-compatible actions, `appfilter.xml`, bounded cache and fallback. Third-party packs NOT RUN. |
| Wallpaper, accessibility, device admin | `SettingsActivity.kt`, `GlasslineAccessibilityService.kt`, `GlasslineDeviceAdminReceiver.kt` | Source inspection confirms chooser/consent paths. OEM behavior NOT RUN. |
| Ranking and alphabet rail | `AppUsageTracker.kt`; `LauncherActivity.kt`: `AppAdapter`, `scrollToLetter` | Source inspection confirms in-memory usage snapshot, time decay, normalized match strength and A–Z rail. Ranking tests NOT RUN. |

## Phases 6–7 — design and performance source map

- Native Views + RecyclerView + ViewPager2: `LauncherActivity.kt`; Material theme dependency in `app/build.gradle.kts`.
- Day/night palette: `app/src/main/res/values/` and `values-night/`; XML parsing passed.
- Bundled font and license: `res/font/glassline.xml`, `roboto_*.ttf`, `assets/licenses/ROBOTO-APACHE-2.0.txt`.
- Reduced motion and TalkBack: `LauncherActivity.kt`: `animationsEnabled`, `touchExplorationEnabled`.
- StrictMode and startup log: `GlasslineApplication.kt`, `LauncherActivity.kt` (`GlasslineStartup`). Runtime audit NOT RUN.
- LeakCanary: `debugImplementation` in `app/build.gradle.kts`. Leak scenarios NOT RUN.
- Baseline Profile: plugin and `baselineprofile` module configured. Generated profile collection NOT RUN; no generated profile is claimed.
- Macrobenchmark: `benchmark/src/androidTest/java/com/glassline/launcher/benchmark/LauncherMacrobenchmark.kt` includes cold startup (`None`, `Full`), 200-app drawer scrolling, drawer opening, page swiping, and icon dragging. Module compilation/execution NOT RUN.

## Phase 8 execution contract

| Check | Status | Reproduction command / manual step |
|---|---|---|
| 8.1 Phase 1 fixes | Source-inspected; tests NOT RUN | `gradle :app:testDebugUnitTest` and `gradle :app:connectedDebugAndroidTest` |
| 8.2 Features in source | Source-inspected; map above | Review listed file/function locations. |
| 8.3 Unit test coverage | Tests present; NOT RUN | `gradle :app:testDebugUnitTest` |
| 8.4 Instrumentation coverage | Tests present; NOT RUN | `gradle :app:connectedDebugAndroidTest` |
| 8.5 Macrobenchmark | NOT RUN: no Gradle/SDK/adb/device in this environment | `gradle :benchmark:connectedAndroidTest`; install 200+ launchable apps for the 200-app scrolling test. |
| 8.6 LeakCanary | Dependency configured; no observation available | Install debug build, repeatedly open/close drawer, rotate, open Settings and return; inspect LeakCanary. |
| 8.7 StrictMode | Policies configured; zero-violation status NOT RUN | `adb logcat -s StrictMode GlasslineStartup` while exercising launch/search/widgets/settings. |
| 8.8 Light/dark, RTL, TalkBack, reduce motion, 200% font | Automated test source exists for light/dark; manual checks NOT RUN | On A34: toggle system theme; set Arabic; enable TalkBack; set Animator duration scale to 0; set font size to 200%. |
| 8.9 Release APK and install | NOT RUN: no Gradle/SDK/adb in this environment | `gradle :app:assembleRelease`; install resulting `app-release.apk` on the Galaxy A34 and select it as Home app. |

## Static checks actually executed

- Parsed every XML file under `app/src/main` with Python's XML parser: PASS.
- Ran source assertions for all eight phases' key declarations and integrations: PASS.
- Checked for unfinished task markers and user-facing filler text in Kotlin, Gradle, XML, Markdown, ProGuard, and properties files: PASS.
- Confirmed `gradle`, `adb`, and Android SDK `android.jar` are unavailable; `kotlinc` 1.9.0 is present but cannot compile this Android project without its dependency classpath. The parser emitted no `expecting`/unexpected-token diagnostics; this is not a successful compilation.

This is not a claim that Kotlin compiles, that the APK installs, or that device performance is within target. Those results require the reproducible Android build and device steps above.
