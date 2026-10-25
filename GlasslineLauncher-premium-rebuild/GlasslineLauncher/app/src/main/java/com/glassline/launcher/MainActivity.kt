package com.glassline.launcher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.drawWithCache
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import android.text.format.DateFormat
import java.time.LocalDateTime
import java.util.Date
import java.util.Locale

private const val PREFS_NAME = "glassline_settings"
private const val PREF_DARK_THEME = "dark_theme"
private const val PREF_GRID_COLUMNS = "grid_columns"
private const val PREF_PINNED_PACKAGES = "pinned_package_order_v2"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
        setContent { GlasslineApp() }
    }
}

@Composable
private fun GlasslineApp() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val prefs = remember { appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    var darkTheme by remember { mutableStateOf(prefs.getBoolean(PREF_DARK_THEME, true)) }
    var gridColumns by remember { mutableIntStateOf(prefs.getInt(PREF_GRID_COLUMNS, 4).coerceIn(3, 5)) }
    var apps by remember { mutableStateOf<List<LauncherApp>>(emptyList()) }
    var loadingApps by remember { mutableStateOf(true) }
    var pinnedPackages by remember { mutableStateOf<List<String>>(emptyList()) }
    var appInventoryVersion by remember { mutableIntStateOf(0) }
    var showDrawer by remember { mutableStateOf(false) }
    var drawerMounted by remember { mutableStateOf(false) }
    var focusSearchOnOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var settingsOpen by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf("") }
    var dateLine by remember { mutableStateOf("") }

    val palette = remember(darkTheme) { paletteFor(darkTheme) }
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalDateTime.now()
            clock = DateFormat.getTimeFormat(context).format(Date())
            dateLine = now.format(java.time.format.DateTimeFormatter.ofPattern("EEEE  ·  d MMMM", Locale.ENGLISH))
                .uppercase(Locale.ENGLISH)
            val millisToNextMinute = 60_000L - (now.second * 1_000L + now.nano / 1_000_000L)
            delay(millisToNextMinute.coerceAtLeast(1_000L))
        }
    }

    DisposableEffect(appContext) {
        val packageReceiver = object : BroadcastReceiver() {
            override fun onReceive(receivedContext: Context?, intent: Intent?) {
                LauncherIconCache.invalidate(intent?.data?.schemeSpecificPart)
                appInventoryVersion += 1
            }
        }
        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(packageReceiver, packageFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(packageReceiver, packageFilter)
        }
        onDispose { runCatching { appContext.unregisterReceiver(packageReceiver) } }
    }

    LaunchedEffect(appInventoryVersion) {
        loadingApps = true
        val loaded = withContext(Dispatchers.IO) { loadLaunchableApps(appContext) }
        apps = loaded
        val availablePackages = loaded.map { it.packageName }.toSet()
        val storedPins = prefs.getString(PREF_PINNED_PACKAGES, null)
        val validStoredPins = storedPins
            ?.split("|")
            ?.filter { it.isNotBlank() && it in availablePackages }
            ?.distinct()
            .orEmpty()
        pinnedPackages = if (storedPins == null) defaultPinnedApps(loaded) else validStoredPins
        if (storedPins != null && validStoredPins.size != storedPins.split("|").count { it.isNotBlank() }) {
            prefs.edit().putString(PREF_PINNED_PACKAGES, validStoredPins.joinToString("|")).apply()
        }
        loadingApps = false
    }

    LaunchedEffect(drawerMounted, showDrawer, focusSearchOnOpen) {
        if (drawerMounted && showDrawer && focusSearchOnOpen) {
            delay(170L)
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
            focusSearchOnOpen = false
        }
    }

    val screenTransition = updateTransition(targetState = showDrawer, label = "home-drawer")

    LaunchedEffect(showDrawer) {
        if (showDrawer) {
            drawerMounted = true
        } else if (drawerMounted) {
            // Unmount only after the real animation finishes, rather than guessing its duration.
            snapshotFlow {
                screenTransition.currentState == screenTransition.targetState && !screenTransition.isRunning
            }.first { finished -> finished }
            drawerMounted = false
        }
    }

    val drawerProgress by screenTransition.animateFloat(
        transitionSpec = {
            // Springs preserve velocity when the user opens and closes quickly in succession.
            spring(
                dampingRatio = 0.94f,
                stiffness = if (targetState) 520f else 620f,
            )
        },
        label = "drawer-progress",
    ) { visible -> if (visible) 1f else 0f }

    val filteredApps = remember(apps, searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) apps else apps.filter {
            it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        }
    }
    val pinnedApps = remember(apps, pinnedPackages) {
        apps.filter { it.packageName in pinnedPackages }
            .distinctBy { it.packageName }
            .sortedBy { pinnedPackages.indexOf(it.packageName) }
            .take(5)
    }

    fun openAppDrawer(requestSearchFocus: Boolean) {
        searchQuery = ""
        focusSearchOnOpen = requestSearchFocus
        drawerMounted = true
        showDrawer = true
    }

    fun closeAppDrawer() {
        showDrawer = false
        focusSearchOnOpen = false
        keyboard?.hide()
    }

    fun togglePinned(app: LauncherApp) {
        val newPins = pinnedPackages.toMutableList()
        if (app.packageName in newPins) {
            newPins.remove(app.packageName)
        } else {
            if (newPins.size >= 5) {
                Toast.makeText(context, "The dock holds up to five apps", Toast.LENGTH_SHORT).show()
                return
            }
            newPins.add(app.packageName)
        }
        pinnedPackages = newPins
        prefs.edit().putString(PREF_PINNED_PACKAGES, newPins.joinToString("|")).apply()
    }

    BackHandler(enabled = showDrawer && !settingsOpen) { closeAppDrawer() }

    MaterialTheme(
        colorScheme = if (darkTheme) {
            darkColorScheme(primary = palette.accent, background = palette.background, surface = palette.surface)
        } else {
            lightColorScheme(primary = palette.accent, background = palette.background, surface = palette.surface)
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val glow = Brush.radialGradient(
                        colors = listOf(palette.glow, Color.Transparent),
                        center = Offset(size.width * 0.86f, size.height * 0.13f),
                        radius = size.maxDimension * 0.78f,
                    )
                    onDrawBehind {
                        drawRect(palette.background)
                        drawRect(glow)
                    }
                }
                .then(
                    if (!showDrawer) {
                        Modifier.pointerInput(Unit) {
                            var dragTotal = 0f
                            detectVerticalDragGestures(
                                onDragStart = { dragTotal = 0f },
                                onVerticalDrag = { _, amount -> dragTotal += amount },
                                onDragEnd = {
                                    if (dragTotal < -72.dp.toPx()) openAppDrawer(false)
                                    dragTotal = 0f
                                },
                                onDragCancel = { dragTotal = 0f },
                            )
                        }
                    } else Modifier
                )
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            HomeScreen(
                palette = palette,
                clock = clock,
                dateLine = dateLine,
                pinnedApps = pinnedApps,
                loadingApps = loadingApps,
                drawerProgress = { drawerProgress },
                onOpenDrawer = { openAppDrawer(true) },
                onOpenDrawerBySwipe = { openAppDrawer(false) },
                onOpenSettings = { settingsOpen = true },
                onLaunchApp = { app, sourceView, sourceBounds -> launchApp(context, app, sourceView, sourceBounds) },
                onTogglePinned = ::togglePinned,
                onAppInfo = { openAppInfo(context, it) },
            )

            if (drawerMounted) {
                AppDrawer(
                    palette = palette,
                    apps = filteredApps,
                    allAppsCount = apps.size,
                    isLoading = loadingApps,
                    query = searchQuery,
                    focusRequester = focusRequester,
                    drawerProgress = { drawerProgress },
                    pinnedPackages = pinnedPackages,
                    gridColumns = gridColumns,
                    onQueryChange = { searchQuery = it },
                    onClose = ::closeAppDrawer,
                    onLaunchApp = { app, sourceView, sourceBounds ->
                        closeAppDrawer()
                        launchApp(context, app, sourceView, sourceBounds)
                    },
                    onTogglePinned = ::togglePinned,
                    onAppInfo = {
                        closeAppDrawer()
                        openAppInfo(context, it)
                    },
                    onOpenSettings = { settingsOpen = true },
                )
            }
        }

        if (settingsOpen) {
            SettingsDialog(
                palette = palette,
                darkTheme = darkTheme,
                gridColumns = gridColumns,
                onDarkThemeChange = {
                    darkTheme = it
                    prefs.edit().putBoolean(PREF_DARK_THEME, it).apply()
                },
                onGridColumnsChange = {
                    gridColumns = it
                    prefs.edit().putInt(PREF_GRID_COLUMNS, it).apply()
                },
                onDismiss = { settingsOpen = false },
            )
        }
    }
}
