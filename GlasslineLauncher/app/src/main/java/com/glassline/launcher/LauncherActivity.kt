package com.glassline.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.app.admin.DevicePolicyManager
import android.app.ActivityOptions
import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.LauncherActivityInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.DragEvent
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.getSystemService
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Native Views keep the hot scrolling path simple and predictable. */
class LauncherActivity : AppCompatActivity(), android.content.SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var root: FrameLayout
    private lateinit var home: LinearLayout
    private lateinit var drawer: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var search: EditText
    private lateinit var appGrid: RecyclerView
    private lateinit var drawerGridContainer: FrameLayout
    private lateinit var alphabetRail: LinearLayout
    private lateinit var adapter: AppAdapter
    private lateinit var homePager: ViewPager2
    private lateinit var workspacePagerAdapter: WorkspacePagerAdapter
    private lateinit var pageIndicator: PageIndicatorView
    private lateinit var dockContainer: LinearLayout
    private lateinit var dragActionBar: LinearLayout
    private lateinit var dragInfoAction: TextView
    private lateinit var dragUninstallAction: TextView
    private var dragActionTarget: String? = null
    private var drawerTranslationSpring: SpringAnimation? = null
    private var drawerAlphaSpring: SpringAnimation? = null
    private lateinit var workspaceStore: WorkspaceStore
    private var workspace = Workspace()
    private var appsSnapshot: List<LaunchableApp> = emptyList()
    private val appIconCache = android.util.LruCache<String, Drawable>(96)
    private var workspaceLoaded = false
    private var appListLoaded = false
    private var hasSavedWorkspace = false
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private var drawerOpen = false
    private var downX = 0f
    private var downY = 0f
    private var gestureEligible = false
    private var query = ""
    private var filterJob: Job? = null
    private var reloadJob: Job? = null
    private var workspaceJob: Job? = null
    private var workspaceSaveJob: Job? = null
    private var lastTap = 0L
    private var lastClock = ""
    private var appliedAccentIndex = 0
    private var receiverRegistered = false
    private var wallpaperCache: Drawable? = null
    private var wallpaperLoadJob: Job? = null
    private val widgetViews = mutableMapOf<Int, AppWidgetHostView>()
    private var pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pendingWidgetInfo: AppWidgetProviderInfo? = null
    private val widgetBindResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = pendingWidgetId; val info = pendingWidgetInfo
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID && info != null && result.resultCode == RESULT_OK) configureOrPlaceWidget(id, info) else discardPendingWidget()
    }
    private val widgetConfigureResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = pendingWidgetId; val info = pendingWidgetInfo
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID && info != null && result.resultCode == RESULT_OK) placeWidget(id, info) else discardPendingWidget()
    }
    private val prefs by lazy { getSharedPreferences("glassline", MODE_PRIVATE) }
    private val iconPackManager by lazy { IconPackManager(this) }
    private val usageTracker by lazy { AppUsageTracker(this) }
    private val launcherAppsService by lazy { getSystemService(LauncherApps::class.java) }
    private val widgetHost by lazy { AppWidgetHost(this, 0x474C) }

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { updateClock() }
    }
    private val launcherAppsCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: android.os.UserHandle) { refreshPackage(packageName, user, removed = true) }
        override fun onPackageAdded(packageName: String, user: android.os.UserHandle) { refreshPackage(packageName, user, removed = false) }
        override fun onPackageChanged(packageName: String, user: android.os.UserHandle) { refreshPackage(packageName, user, removed = false) }
        override fun onPackagesAvailable(packageNames: Array<out String>, user: android.os.UserHandle, replacing: Boolean) { reloadApps() }
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: android.os.UserHandle, replacing: Boolean) { reloadApps() }
        override fun onPackagesSuspended(packageNames: Array<out String>, user: android.os.UserHandle) { reloadApps() }
        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: android.os.UserHandle) { reloadApps() }
        override fun onShortcutsChanged(packageName: String, shortcuts: List<android.content.pm.ShortcutInfo>, user: android.os.UserHandle) { }
    }
    private val wallpaperReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { wallpaperCache = null; applyWallpaper() }
    }
    private val widgetPinReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { reloadWorkspace(); prefs.getString("widget_pin_error", null)?.let { message -> Toast.makeText(this@LauncherActivity, message, Toast.LENGTH_LONG).show(); prefs.edit().remove("widget_pin_error").apply() } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        appliedAccentIndex = getSharedPreferences("glassline", MODE_PRIVATE).getInt("accent_index", 0).coerceIn(0, 5)
        setTheme(AccentThemes.styleFor(appliedAccentIndex))
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            val startupMs = (android.os.SystemClock.elapsedRealtime() - StartupMetrics.processAttachElapsedRealtime).coerceAtLeast(0L)
            android.util.Log.d("GlasslineStartup", "launcher_onCreate_elapsed_ms=$startupMs")
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        root = FrameLayout(this)
        workspaceStore = WorkspaceStore(this)
        home = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(12)) }
        drawer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(12)); visibility = View.GONE }
        root.addView(home, FrameLayout.LayoutParams(-1, -1))
        root.addView(drawer, FrameLayout.LayoutParams(-1, -1))
        appGrid = RecyclerView(this).apply {
            tag = "glassline-grid"
            layoutManager = GridLayoutManager(this@LauncherActivity, parsedColumns()).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int = if (this@LauncherActivity.isAppAdapterReady() && this@LauncherActivity.adapter.isFullSpan(position)) spanCount else 1
                }
            }
            itemAnimator = null
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        adapter = AppAdapter()
        appGrid.adapter = adapter
        buildHomeChrome()
        buildDrawerChrome()
        buildDragActionBar()
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            home.setPadding(dp(20), bars.top + dp(20), dp(20), bars.bottom + dp(12))
            drawer.setPadding(dp(20), bars.top + dp(20), dp(20), bars.bottom + dp(12))
            if (::dragActionBar.isInitialized) (dragActionBar.layoutParams as? FrameLayout.LayoutParams)?.let { it.topMargin = bars.top + dp(16); dragActionBar.layoutParams = it }
            insets
        }
        ViewCompat.requestApplyInsets(root)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
        prefs.registerOnSharedPreferenceChangeListener(this)
        reloadWorkspace()
        reloadApps()
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (drawerOpen) showHome() else if (::homePager.isInitialized && homePager.currentItem != 0) homePager.setCurrentItem(0, animationsEnabled()) else moveTaskToBack(true) }
        })
    }

    private fun buildHomeChrome() {
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        date = TextView(this).apply { textSize = 14f; setTextColor(getColor(com.glassline.launcher.R.color.glass_muted)) }
        val settings = TextView(this).apply { text = "⚙"; textSize = 22f; setTextColor(getColor(R.color.glass_text)); gravity = Gravity.CENTER; contentDescription = "Launcher settings"; setOnClickListener { startActivity(Intent(this@LauncherActivity, SettingsActivity::class.java)) } }
        top.addView(date, LinearLayout.LayoutParams(0, dp(44), 1f)); top.addView(settings, LinearLayout.LayoutParams(dp(44), dp(44)))
        home.addView(top)
        clock = TextView(this).apply { textSize = 56f; letterSpacing = -0.045f; maxLines = 1; typeface = androidx.core.content.res.ResourcesCompat.getFont(this@LauncherActivity, R.font.roboto_regular); setTextColor(getColor(R.color.glass_text)); setPadding(0, dp(18), 0, dp(4)) }
        home.addView(clock)
        val subtitle = TextView(this).apply { text = "A quieter space for your apps"; textSize = 14f; setTextColor(getColor(R.color.glass_muted)); setPadding(0, 0, 0, dp(18)) }
        home.addView(subtitle)
        homePager = ViewPager2(this).apply { tag = "glassline-home-pager"; offscreenPageLimit = 1; isSaveEnabled = true }
        workspacePagerAdapter = WorkspacePagerAdapter()
        homePager.adapter = workspacePagerAdapter
        home.addView(homePager, LinearLayout.LayoutParams(-1, 0, 1f))
        pageIndicator = PageIndicatorView(this).apply { isFocusable = true; contentDescription = "Page 1 of 3" }
        home.addView(pageIndicator, LinearLayout.LayoutParams(-1, dp(32)))
        homePager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) { updatePageIndicator(position) }
        })
        dockContainer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; background = rounded(getColor(R.color.glass_surface), 30); setPadding(dp(8), dp(6), dp(8), dp(6)); elevation = dp(8).toFloat() }
        home.addView(dockContainer, LinearLayout.LayoutParams(-1, dp(64)))
        renderDock()
        updateClock()
    }

    private fun buildDrawerChrome() {
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply { text = "Apps"; textSize = 28f; setTextColor(getColor(R.color.glass_text)) }
        val close = TextView(this).apply { text = "Done"; textSize = 14f; setTextColor(accentColor()); gravity = Gravity.CENTER; setOnClickListener { showHome() } }
        header.addView(title, LinearLayout.LayoutParams(0, dp(48), 1f)); header.addView(close, LinearLayout.LayoutParams(dp(56), dp(48))); drawer.addView(header)
        search = EditText(this).apply {
            tag = "glassline-search"
            hint = "Search apps"; singleLine = true; textSize = 16f; setTextColor(getColor(R.color.glass_text)); setHintTextColor(getColor(R.color.glass_muted)); background = rounded(getColor(R.color.glass_surface), 18); setPadding(dp(16), 0, dp(16), 0)
            addTextChangedListener(SimpleTextWatcher { value -> query = value; scheduleFilter(value) })
        }
        drawer.addView(search, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(8); bottomMargin = dp(10) })
        drawerGridContainer = FrameLayout(this)
        appGrid.setPaddingRelative(0, 0, dp(24), 0)
        drawerGridContainer.addView(appGrid, FrameLayout.LayoutParams(-1, -1))
        alphabetRail = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(2), dp(4), dp(2), dp(4)); contentDescription = "Alphabetical app picker" }
        val letters = (('A'..'Z').map { it.toString() } + "#")
        letters.forEach { letter -> alphabetRail.addView(TextView(this).apply { text = letter; textSize = 8f; gravity = Gravity.CENTER; setTextColor(accentColor()); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(-1, 0, 1f)) }
        alphabetRail.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE || event.actionMasked == MotionEvent.ACTION_UP) {
                val index = ((event.y / alphabetRail.height.coerceAtLeast(1)) * letters.size).toInt().coerceIn(0, letters.lastIndex)
                adapter.scrollToLetter(letters[index]); true
            } else true
        }
        drawerGridContainer.addView(alphabetRail, FrameLayout.LayoutParams(dp(22), -1, Gravity.END))
        val footer = TextView(this).apply { text = "Alphabetical · Long-press an app for options"; gravity = Gravity.CENTER; textSize = 12f; setTextColor(getColor(R.color.glass_muted)); setPadding(0, dp(12), 0, dp(8)) }
        drawer.addView(drawerGridContainer, LinearLayout.LayoutParams(-1, 0, 1f))
        drawer.addView(footer)
    }

    private fun buildDragActionBar() {
        dragActionBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; background = rounded(getColor(R.color.glass_surface), 24); setPadding(dp(12), dp(8), dp(12), dp(8)); visibility = View.GONE; elevation = dp(12).toFloat() }
        dragInfoAction = TextView(this).apply { text = "App info"; gravity = Gravity.CENTER; setTextColor(getColor(R.color.glass_text)) }
        dragUninstallAction = TextView(this).apply { text = "Uninstall"; gravity = Gravity.CENTER; setTextColor(getColor(R.color.glass_text)) }
        dragActionBar.addView(dragInfoAction, LinearLayout.LayoutParams(0, dp(44), 1f))
        dragActionBar.addView(dragUninstallAction, LinearLayout.LayoutParams(0, dp(44), 1f))
        root.addView(dragActionBar, FrameLayout.LayoutParams(-1, dp(64), Gravity.TOP).apply { leftMargin = dp(24); rightMargin = dp(24); topMargin = dp(52) })
    }

    private fun showDrawer(focusSearch: Boolean = false) {
        if (drawerOpen) {
            adapter.filter(query)
            if (focusSearch) { search.requestFocus(); search.post { getSystemService<InputMethodManager>()?.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT) } }
            return
        }
        cancelDrawerMotion()
        drawerOpen = true
        home.visibility = View.GONE
        drawer.visibility = View.VISIBLE
        adapter.filter(query)
        if (::alphabetRail.isInitialized) alphabetRail.visibility = if (normalize(query).isEmpty()) View.VISIBLE else View.GONE
        if (animationsEnabled()) {
            drawer.translationY = dp(32).toFloat(); drawer.alpha = 0f
            drawerTranslationSpring = SpringAnimation(drawer, DynamicAnimation.TRANSLATION_Y, 0f).apply { spring = SpringForce(0f).setStiffness(720f).setDampingRatio(0.86f); start() }
            drawerAlphaSpring = SpringAnimation(drawer, DynamicAnimation.ALPHA, 1f).apply { spring = SpringForce(1f).setStiffness(720f).setDampingRatio(0.9f); start() }
        } else { drawer.translationY = 0f; drawer.alpha = 1f }
        if (focusSearch) { search.requestFocus(); search.post { getSystemService<InputMethodManager>()?.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT) } }
    }

    private fun showHome() {
        if (!drawerOpen) return
        drawerOpen = false
        (getSystemService<InputMethodManager>())?.hideSoftInputFromWindow(root.windowToken, 0)
        home.visibility = View.VISIBLE
        cancelDrawerMotion()
        if (animationsEnabled()) {
            drawer.visibility = View.VISIBLE; drawer.translationY = 0f; drawer.alpha = 1f
            drawerTranslationSpring = SpringAnimation(drawer, DynamicAnimation.TRANSLATION_Y, dp(26).toFloat()).apply { spring = SpringForce(dp(26).toFloat()).setStiffness(720f).setDampingRatio(0.9f); start() }
            drawerAlphaSpring = SpringAnimation(drawer, DynamicAnimation.ALPHA, 0f).apply {
                spring = SpringForce(0f).setStiffness(720f).setDampingRatio(0.9f)
                addEndListener { _, canceled, _, _ -> if (!canceled && !drawerOpen) { drawer.visibility = View.GONE; drawer.translationY = 0f; drawer.alpha = 1f } }
                start()
            }
        } else { drawer.visibility = View.GONE; drawer.translationY = 0f; drawer.alpha = 1f }
    }

    private fun cancelDrawerMotion() {
        drawerTranslationSpring?.cancel(); drawerAlphaSpring?.cancel()
        drawerTranslationSpring = null; drawerAlphaSpring = null
        drawer.animate().cancel()
    }

    private fun scheduleFilter(value: String) {
        filterJob?.cancel()
        filterJob = lifecycleScope.launch { delay(200); adapter.filter(value); if (::alphabetRail.isInitialized) alphabetRail.visibility = if (normalize(value).isEmpty()) View.VISIBLE else View.GONE }
    }

    private fun reloadApps() {
        reloadJob?.cancel()
        reloadJob = lifecycleScope.launch {
            val found = withContext(Dispatchers.IO) {
                usageTracker.refreshFromDisk()
                val launcher = launcherAppsService
                val userManager = getSystemService(android.os.UserManager::class.java)
                val users = (listOf(android.os.Process.myUserHandle()) + launcher.profiles).distinct()
                val apps = users.flatMap { user ->
                    runCatching { launcher.getActivityList(null, user) }.getOrDefault(emptyList()).mapNotNull { activity ->
                        val component = activity.componentName
                        val label = runCatching { activity.label?.toString().orEmpty() }.getOrDefault(component.className.substringAfterLast('.'))
                        val serial = runCatching { userManager.getSerialNumberForUser(user) }.getOrDefault(0L)
                        val cacheKey = iconCacheKey(component.packageName, component.className, serial)
                        runCatching { activity.getIcon(resources.displayMetrics.densityDpi) }.getOrNull()?.let { appIconCache.put(cacheKey, it) }
                        LaunchableApp(label, component.packageName, component.className, user, serial)
                    }
                }.distinctBy { "${it.packageName}|${it.activityName}|${it.userSerial}" }
                val components = apps.map { android.content.ComponentName(it.packageName, it.activityName) }
                prefs.getString("icon_pack_package", null)?.let { pack -> runCatching { iconPackManager.preload(pack, components) } }
                runCatching { iconPackManager.preloadOverrides(components) }
                apps.sortedBy { normalize(it.label) }
            }
            if (!isFinishing && !isDestroyed) { appsSnapshot = found.toList(); appListLoaded = true; adapter.submit(found); renderWorkspace(); maybeInitializeWorkspace() }
        }
    }

    private fun refreshPackage(packageName: String, user: android.os.UserHandle, removed: Boolean) {
        reloadJob?.cancel()
        reloadJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val userManager = getSystemService(android.os.UserManager::class.java)
                val serial = runCatching { userManager.getSerialNumberForUser(user) }.getOrDefault(0L)
                appIconCache.snapshot().keys.filter { it.startsWith("$packageName|") }.forEach { appIconCache.remove(it) }
                val fresh = if (removed) emptyList() else runCatching {
                    launcherAppsService.getActivityList(packageName, user).map { activity ->
                        val component = activity.componentName
                        val label = runCatching { activity.label?.toString().orEmpty() }.getOrDefault(component.className.substringAfterLast('.'))
                        val cacheKey = iconCacheKey(component.packageName, component.className, serial)
                        runCatching { activity.getIcon(resources.displayMetrics.densityDpi) }.getOrNull()?.let { appIconCache.put(cacheKey, it) }
                        LaunchableApp(label, component.packageName, component.className, user, serial)
                    }
                }.getOrDefault(emptyList())
                val components = fresh.map { android.content.ComponentName(it.packageName, it.activityName) }
                prefs.getString("icon_pack_package", null)?.let { runCatching { iconPackManager.preload(it, components) } }
                runCatching { iconPackManager.preloadOverrides(components) }
                serial to fresh
            }
            val serial = result.first
            val fresh = result.second
            val updated = (appsSnapshot.filterNot { it.packageName == packageName && it.userSerial == serial } + fresh)
                .distinctBy { "${it.packageName}|${it.activityName}|${it.userSerial}" }
                .sortedBy { normalize(it.label) }
            appsSnapshot = updated
            adapter.submit(updated)
            renderWorkspace()
            maybeInitializeWorkspace()
        }
    }

    private fun launch(app: LaunchableApp, sourceView: View? = null) {
        try {
            val options = if (sourceView != null && animationsEnabled()) ActivityOptions.makeScaleUpAnimation(sourceView, 0, 0, sourceView.width.coerceAtLeast(1), sourceView.height.coerceAtLeast(1)).toBundle() else null
            launcherAppsService.startMainActivity(android.content.ComponentName(app.packageName, app.activityName), app.userHandle, null, options)
            usageTracker.record(app)
        } catch (_: Exception) { Toast.makeText(this, "Couldn't open ${app.label}", Toast.LENGTH_SHORT).show() }
    }
    private fun showAppMenu(app: LaunchableApp, anchor: View) {
        val dialog = BottomSheetDialog(this)
        val sheet = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(24)); background = rounded(getColor(R.color.glass_surface), 28) }
        val handle = View(this).apply { background = rounded(getColor(R.color.glass_muted), 8) }
        sheet.addView(handle, LinearLayout.LayoutParams(dp(36), dp(4)).apply { gravity = Gravity.CENTER; bottomMargin = dp(16) })
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(12)) }
        val icon = ImageView(this).apply { setImageDrawable(iconPackManager.resolveIcon(prefs.getString("icon_pack_package", null), app.packageName, app.activityName, fallbackIcon(app))); contentDescription = "${app.label} icon"; scaleType = ImageView.ScaleType.FIT_CENTER }
        header.addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(TextView(this).apply { text = app.label; textSize = 20f; typeface = androidx.core.content.res.ResourcesCompat.getFont(this@LauncherActivity, R.font.roboto_medium); setTextColor(getColor(R.color.glass_text)); setPadding(dp(14), 0, 0, 0); contentDescription = app.label }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        sheet.addView(header)
        addSheetAction(sheet, "App info", "Open Android app details", dialog) { runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${app.packageName}"))) } }
        addSheetAction(sheet, "Uninstall", "Ask Android to uninstall this app", dialog) { runCatching { startActivity(Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:${app.packageName}"))) } }
        addSheetAction(sheet, "Pin to home", "Add this app to a workspace page", dialog) { pinToHome(app) }
        addSheetAction(sheet, "Add to dock", "Add this app to the dock", dialog) { addToDock(app) }
        addSheetAction(sheet, "Move to page…", "Choose a workspace page", dialog) { moveAppToPage(app) }
        addSheetAction(sheet, "Choose icon override…", "Choose a symbol from the selected icon pack", dialog) { chooseIconOverride(app) }
        addSheetAction(sheet, if (adapter.isHidden(app.packageName)) "Unhide app" else "Hide app", "Change app-drawer visibility", dialog) { adapter.toggleHidden(app) }
        val shortcutsHeading = TextView(this).apply { text = "Shortcuts"; textSize = 13f; typeface = androidx.core.content.res.ResourcesCompat.getFont(this@LauncherActivity, R.font.roboto_medium); setTextColor(accentColor()); setPadding(0, dp(12), 0, dp(4)) }
        sheet.addView(shortcutsHeading)
        val shortcutRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sheet.addView(shortcutRows)
        dialog.setContentView(sheet)
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setDimAmount(0.32f)
        dialog.behavior.isDraggable = true
        lifecycleScope.launch {
            val shortcuts = withContext(Dispatchers.IO) {
                runCatching {
                    val query = LauncherApps.ShortcutQuery().setPackage(app.packageName)
                        .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
                    launcherAppsService.getShortcuts(query, app.userHandle).orEmpty()
                }.getOrDefault(emptyList())
            }
            if (isFinishing || isDestroyed || !dialog.isShowing) return@launch
            shortcutRows.removeAllViews()
            if (shortcuts.isEmpty()) addSheetAction(shortcutRows, "No shortcuts available", "This app has no launcher shortcuts", dialog) { }
            shortcuts.take(6).forEach { shortcut ->
                val label = shortcut.shortLabel?.toString() ?: shortcut.id
                addSheetAction(shortcutRows, label, "Open ${app.label}: $label", dialog) { launchShortcut(shortcut, app.userHandle) }
                addSheetAction(shortcutRows, "Pin $label to home", "Place this shortcut on the current page", dialog) { pinShortcut(shortcut, app.userHandle) }
            }
        }
    }

    private fun addSheetAction(container: LinearLayout, label: String, description: String, dialog: BottomSheetDialog? = null, action: () -> Unit) {
        val row = TextView(this).apply {
            text = label; textSize = 15f; setTextColor(getColor(R.color.glass_text)); gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10)); minHeight = dp(48); contentDescription = description.ifBlank { label }
            val selectable = android.util.TypedValue(); theme.resolveAttribute(android.R.attr.selectableItemBackground, selectable, true)
            if (selectable.resourceId != 0) setBackgroundResource(selectable.resourceId)
            isClickable = true; isFocusable = true
            setOnClickListener { dialog?.dismiss(); action() }
        }
        container.addView(row, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun addToDock(app: LaunchableApp) {
        if (workspace.dock.any { it.packageName == app.packageName && it.activityName == app.activityName && it.userSerial == app.userSerial }) return
        if (workspace.dock.size >= 4) { Toast.makeText(this, "Dock is full. Remove an app first.", Toast.LENGTH_SHORT).show(); return }
        val index = workspace.dock.size
        workspace = workspace.copy(dock = workspace.dock + HomeItem.AppItem(app.packageName, app.activityName, 0, index, 0, app.userSerial))
        persistWorkspace(); renderDock()
    }

    private fun launchShortcut(shortcut: android.content.pm.ShortcutInfo, userHandle: android.os.UserHandle) {
        runCatching {
            launcherAppsService.startShortcut(shortcut.`package`, shortcut.id, null, null, userHandle)
            val serial = runCatching { getSystemService(android.os.UserManager::class.java).getSerialNumberForUser(userHandle) }.getOrDefault(0L)
            appsSnapshot.firstOrNull { it.packageName == shortcut.`package` && it.userSerial == serial }?.let { usageTracker.record(it) }
        }.onFailure {
            Toast.makeText(this, "This shortcut is no longer available.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchShortcut(key: ShortcutKey) {
        lifecycleScope.launch {
            val user = appsSnapshot.firstOrNull { it.packageName == key.packageName && it.userSerial == key.userSerial }?.userHandle
                ?: android.os.Process.myUserHandle()
            val shortcut = withContext(Dispatchers.IO) {
                runCatching {
                    val query = LauncherApps.ShortcutQuery().setPackage(key.packageName)
                        .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
                    launcherAppsService.getShortcuts(query, user).orEmpty().firstOrNull { it.id == key.shortcutId }
                }.getOrNull()
            }
            if (shortcut != null) launchShortcut(shortcut, user)
            else Toast.makeText(this@LauncherActivity, "This shortcut is no longer available.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pinShortcut(shortcut: android.content.pm.ShortcutInfo, userHandle: android.os.UserHandle) {
        val userSerial = runCatching { getSystemService(android.os.UserManager::class.java).getSerialNumberForUser(userHandle) }.getOrDefault(0L)
        val page = homePager.currentItem.coerceIn(0, workspace.pageCount - 1)
        val next = (workspace.items.filter { it.page == page }.maxOfOrNull { it.y * parsedColumns() + it.x } ?: -1) + 1
        val item = HomeItem.ShortcutItem(ShortcutKey(shortcut.`package`, shortcut.id, userSerial), page, next % parsedColumns(), next / parsedColumns())
        workspace = workspace.copy(items = workspace.items + item)
        persistWorkspace(); renderWorkspace()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) { downX = ev.rawX; downY = ev.rawY; gestureEligible = true }
        else if (ev.actionMasked == MotionEvent.ACTION_MOVE && gestureEligible) {
            val dx = ev.rawX - downX; val dy = ev.rawY - downY
            if (abs(dx) > dp(24) && abs(dx) > abs(dy)) gestureEligible = false
        } else if (ev.actionMasked == MotionEvent.ACTION_UP && gestureEligible) {
            val dx = ev.rawX - downX; val dy = ev.rawY - downY
            if (!touchExplorationEnabled()) {
                if (abs(dy) > dp(90) && abs(dy) > abs(dx)) {
                    if (dy < 0 && !drawerOpen) showDrawer()
                    else if (dy > 0 && drawerOpen) showHome()
                    else if (dy > 0 && !drawerOpen) handleHomeSwipeDown()
                } else if (abs(dx) < dp(16) && abs(dy) < dp(16)) {
                    val now = SystemClock.uptimeMillis()
                    if (isLockGestureSurface(ev.rawX.toInt(), ev.rawY.toInt())) {
                        if (now - lastTap < 300) {
                            when (prefs.getString("double_tap_action", if (prefs.getBoolean("double_tap_lock", false)) "lock" else "none")) {
                                "lock" -> lockDeviceIfAuthorized()
                                "search" -> showDrawer(focusSearch = true)
                            }
                        }
                        lastTap = now
                    } else lastTap = 0L
                }
            }
            gestureEligible = false
        } else if (ev.actionMasked == MotionEvent.ACTION_CANCEL) gestureEligible = false
        return super.dispatchTouchEvent(ev)
    }

    private fun isLockGestureSurface(screenX: Int, screenY: Int): Boolean {
        if (drawerOpen) return false
        var current = findDeepestViewAt(root, screenX, screenY)
        while (current != null && current !== root) {
            val description = current.contentDescription?.toString().orEmpty()
            if (description == "Empty home slot" || description.startsWith("Empty home page")) return true
            if (description.startsWith("Launch ") || description.startsWith("Folder ") || description.startsWith("Shortcut ") || description == "Widget" || description == "Open app drawer" || description == "Launcher settings") return false
            current = current.parent as? View
        }
        return true
    }

    private fun findDeepestViewAt(view: View, screenX: Int, screenY: Int): View? {
        val location = IntArray(2); view.getLocationOnScreen(location)
        if (screenX < location[0] || screenX > location[0] + view.width || screenY < location[1] || screenY > location[1] + view.height || view.visibility != View.VISIBLE) return null
        if (view is ViewGroup) for (index in view.childCount - 1 downTo 0) {
            findDeepestViewAt(view.getChildAt(index), screenX, screenY)?.let { return it }
        }
        return view
    }

    private fun touchExplorationEnabled(): Boolean = getSystemService(android.view.accessibility.AccessibilityManager::class.java)?.isTouchExplorationEnabled == true

    private fun animationsEnabled(): Boolean = runCatching { android.provider.Settings.Global.getFloat(contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }.getOrDefault(true)

    private fun handleHomeSwipeDown() {
        if (prefs.getString("home_swipe_down", "search") != "notifications") { showDrawer(focusSearch = true); return }
        if (GlasslineAccessibilityService.isEnabled(this) && GlasslineAccessibilityService.requestNotificationShade()) return
        Toast.makeText(this, "Enable Glassline's notification gesture in Settings → Accessibility, or change swipe-down to Search.", Toast.LENGTH_LONG).show()
        showDrawer(focusSearch = true)
    }

    private fun lockDeviceIfAuthorized() {
        val manager = getSystemService(DevicePolicyManager::class.java)
        val component = android.content.ComponentName(this, GlasslineDeviceAdminReceiver::class.java)
        if (prefs.getBoolean("double_tap_lock", false) && manager.isAdminActive(component)) {
            runCatching { manager.lockNow() }.onFailure { Toast.makeText(this, "Device lock was denied by Android.", Toast.LENGTH_LONG).show() }
        } else {
            // Device locking only occurs after the user explicitly activates Device Admin.
            showDrawer(focusSearch = true)
        }
    }

    private fun reloadWorkspace() {
        workspaceJob?.cancel()
        workspaceJob = lifecycleScope.launch {
            workspaceSaveJob?.join()
            val (exists, result) = withContext(Dispatchers.IO) {
                workspaceStore.exists() to runCatching { workspaceStore.load() }
            }
            hasSavedWorkspace = exists
            result.onSuccess { loaded ->
                workspace = loaded
                workspaceLoaded = true
                renderWorkspace()
                maybeInitializeWorkspace()
            }.onFailure { error ->
                Toast.makeText(this@LauncherActivity, "Workspace couldn't be loaded: ${error.message ?: "invalid workspace file"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun maybeInitializeWorkspace() {
        if (!workspaceLoaded || !appListLoaded || hasSavedWorkspace) return
        val candidates = listOf(
            listOf("com.samsung.android.dialer", "com.google.android.dialer", "com.android.dialer"),
            listOf("com.samsung.android.messaging", "com.google.android.apps.messaging", "com.android.messaging"),
            listOf("com.sec.android.app.sbrowser", "com.android.chrome", "org.mozilla.firefox"),
            listOf("com.sec.android.app.camera", "com.android.camera2", "com.google.android.GoogleCamera")
        )
        val dockItems = candidates.mapIndexedNotNull { index, packages ->
            val app = appsSnapshot.firstOrNull { it.packageName in packages } ?: return@mapIndexedNotNull null
            HomeItem.AppItem(app.packageName, app.activityName, 0, index, 0, app.userSerial)
        }
        workspace = workspace.copy(dock = dockItems)
        hasSavedWorkspace = true
        persistWorkspace()
        renderWorkspace()
    }

    private fun persistWorkspace() {
        hasSavedWorkspace = true
        workspaceSaveJob?.cancel()
        val snapshot = workspace.copy(items = workspace.items.toList(), dock = workspace.dock.toList())
        workspaceSaveJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { workspaceStore.save(snapshot) } }
            result.onFailure { Toast.makeText(this@LauncherActivity, "Workspace save failed: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private fun renderWorkspace() {
        if (::workspacePagerAdapter.isInitialized) workspacePagerAdapter.refresh()
        if (::dockContainer.isInitialized) renderDock()
        if (::pageIndicator.isInitialized) updatePageIndicator(homePager.currentItem)
    }

    private fun pinToHome(app: LaunchableApp) {
        if (workspace.items.any { it is HomeItem.AppItem && it.packageName == app.packageName && it.activityName == app.activityName && it.userSerial == app.userSerial }) {
            Toast.makeText(this, "App is already on the home screen", Toast.LENGTH_SHORT).show()
            return
        }
        val page = homePager.currentItem.coerceIn(0, workspace.pageCount - 1)
        val pageItems = workspace.items.filter { it.page == page }
        val next = (pageItems.maxOfOrNull { it.y * parsedColumns() + it.x } ?: -1) + 1
        val item = HomeItem.AppItem(app.packageName, app.activityName, page, next % parsedColumns(), next / parsedColumns(), app.userSerial)
        workspace = workspace.copy(items = workspace.items + item)
        persistWorkspace()
        renderWorkspace()
        Toast.makeText(this, "Pinned to home", Toast.LENGTH_SHORT).show()
    }

    private fun renderDock() {
        if (!::dockContainer.isInitialized) return
        dockContainer.removeAllViews()
        workspace.dock.take(4).forEach { item ->
            val app = appsSnapshot.firstOrNull { it.packageName == item.packageName && it.activityName == item.activityName && it.userSerial == item.userSerial }
            val cell = LinearLayout(this).apply { tag = item.id; orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; isClickable = true; isFocusable = true; contentDescription = app?.let { "Launch ${it.label}" } ?: "Unavailable dock app" }
            val icon = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setImageDrawable(app?.let { iconPackManager.resolveIcon(prefs.getString("icon_pack_package", null), it.packageName, it.activityName, fallbackIcon(it)) }); contentDescription = app?.let { "Launch ${it.label}" } ?: "Unavailable app" }
            cell.addView(icon, LinearLayout.LayoutParams(dp(28), dp(28)))
            val label = TextView(this).apply { text = app?.label?.take(8) ?: "App"; textSize = 9f; gravity = Gravity.CENTER; maxLines = 1; setTextColor(getColor(R.color.glass_text)) }
            cell.addView(label, LinearLayout.LayoutParams(-1, dp(16)))
            if (app != null) {
                cell.setOnClickListener { launch(app) }
                cell.setOnLongClickListener {
                    PopupMenu(this, cell).apply {
                        menu.add("Start drag to reorder").setOnMenuItemClickListener { startDockDrag(cell, item); true }
                        menu.add("Remove from dock").setOnMenuItemClickListener { workspace = WorkspaceOperations.removeFromDock(workspace, item.id); persistWorkspace(); renderDock(); true }
                        menu.add("Move to home").setOnMenuItemClickListener { pinToHome(app); true }
                        show()
                    }
                    true
                }
            }
            cell.setOnDragListener { view, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> event.clipDescription?.hasMimeType("text/plain") == true
                    DragEvent.ACTION_DRAG_ENTERED -> { view.scaleX = 1.08f; view.scaleY = 1.08f; true }
                    DragEvent.ACTION_DRAG_EXITED -> { view.scaleX = 1f; view.scaleY = 1f; true }
                    DragEvent.ACTION_DROP -> { val from = event.clipData?.getItemAt(0)?.text?.toString(); val to = view.tag as? String; if (from != null && to != null) reorderDock(from, to); true }
                    DragEvent.ACTION_DRAG_ENDED -> { view.scaleX = 1f; view.scaleY = 1f; true }
                    else -> true
                }
            }
            dockContainer.addView(cell, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        repeat((4 - workspace.dock.take(4).size).coerceAtLeast(0)) {
            val empty = TextView(this).apply { text = "+"; textSize = 22f; gravity = Gravity.CENTER; setTextColor(accentColor()); contentDescription = "Empty dock slot. Open the app drawer to add an app."; isFocusable = true; setOnClickListener { showDrawer() } }
            dockContainer.addView(empty, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        val appsPill = TextView(this).apply { text = "Apps"; textSize = 11f; gravity = Gravity.CENTER; setTextColor(getColor(R.color.glass_text)); contentDescription = "Open app drawer"; setOnClickListener { showDrawer() } }
        dockContainer.addView(appsPill, LinearLayout.LayoutParams(0, dp(48), 0.8f))
    }

    private fun isPointInside(view: View, x: Int, y: Int): Boolean {
        if (view.visibility != View.VISIBLE) return false
        val location = IntArray(2); view.getLocationOnScreen(location)
        return x >= location[0] && x <= location[0] + view.width && y >= location[1] && y <= location[1] + view.height
    }

    private fun startDockDrag(view: View, item: HomeItem.AppItem) {
        val clip = ClipData.newPlainText("glassline-dock-item", item.id)
        view.startDragAndDrop(clip, View.DragShadowBuilder(view), item.id, 0)
    }

    private fun reorderDock(fromId: String, toId: String) {
        val current = workspace.dock.toMutableList()
        val from = current.indexOfFirst { it.id == fromId }
        val to = current.indexOfFirst { it.id == toId }
        if (from < 0 || to < 0 || from == to) return
        val item = current.removeAt(from)
        current.add(to.coerceIn(0, current.size), item)
        workspace = workspace.copy(dock = current.take(4))
        persistWorkspace(); renderDock()
    }

    private fun updatePageIndicator(position: Int) {
        if (!::pageIndicator.isInitialized) return
        val count = workspace.pageCount.coerceAtLeast(1)
        pageIndicator.setPageCount(count)
        pageIndicator.setCurrentPage(position, animationsEnabled())
        pageIndicator.contentDescription = "Page ${position + 1} of $count"
    }

    private fun workspaceDragCallback(holder: WorkspacePagerAdapter.PageHolder): ItemTouchHelper.Callback = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0) {
        private var draggedId: String? = null
        private var folderTargetId: String? = null
        private var targetPage: Int? = null
        private var dragMotionEnabled = true
        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                draggedId = (holder.grid.adapter as? WorkspaceItemAdapter)?.itemAt(viewHolder.bindingAdapterPosition)?.id
                dragMotionEnabled = animationsEnabled()
                homePager.isUserInputEnabled = false
                dragActionTarget = null
                dragActionBar.visibility = View.VISIBLE
                viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            }
        }
        override fun onMove(recyclerView: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            val itemAdapter = recyclerView.adapter as? WorkspaceItemAdapter ?: return false
            val moving = itemAdapter.itemAt(source.bindingAdapterPosition) ?: return false
            val over = itemAdapter.itemAt(target.bindingAdapterPosition)
            if (moving is HomeItem.AppItem && over is HomeItem.AppItem && moving.id != over.id) {
                folderTargetId = over.id
                return true
            }
            if (moving is HomeItem.AppItem && over is HomeItem.FolderItem) { folderTargetId = over.id; return true }
            folderTargetId = null
            itemAdapter.moveLocal(source.bindingAdapterPosition, target.bindingAdapterPosition)
            source.itemView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            return true
        }
        override fun onChildDraw(c: android.graphics.Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                viewHolder.itemView.scaleX = if (isCurrentlyActive && dragMotionEnabled) 1.06f else 1f
                viewHolder.itemView.scaleY = if (isCurrentlyActive && dragMotionEnabled) 1.06f else 1f
                val edge = recyclerView.width * 0.42f
                targetPage = when { dX > edge && holder.pageIndex < workspace.pageCount - 1 -> holder.pageIndex + 1; dX < -edge && holder.pageIndex > 0 -> holder.pageIndex - 1; else -> null }
                val location = IntArray(2); viewHolder.itemView.getLocationOnScreen(location)
                val centerX = (location[0] + dX + viewHolder.itemView.width / 2).toInt()
                val centerY = (location[1] + dY + viewHolder.itemView.height / 2).toInt()
                dragActionTarget = when { isPointInside(dragInfoAction, centerX, centerY) -> "info"; isPointInside(dragUninstallAction, centerX, centerY) -> "uninstall"; else -> null }
                dragInfoAction.alpha = if (dragActionTarget == "info") 1f else 0.72f
                dragUninstallAction.alpha = if (dragActionTarget == "uninstall") 1f else 0.72f
            }
        }
        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            viewHolder.itemView.scaleX = 1f; viewHolder.itemView.scaleY = 1f
            val sourceId = draggedId
            val folderId = folderTargetId
            val pageTarget = targetPage
            val actionTarget = dragActionTarget
            val draggedItem = workspace.items.firstOrNull { it.id == sourceId }
            if (actionTarget != null && draggedItem is HomeItem.AppItem) {
                val actionApp = appsSnapshot.firstOrNull { it.packageName == draggedItem.packageName && it.activityName == draggedItem.activityName }
                if (actionApp != null && actionTarget == "info") runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${actionApp.packageName}"))) }
                else if (actionApp != null && actionTarget == "uninstall") runCatching { startActivity(Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:${actionApp.packageName}"))) }
                renderWorkspace()
            } else if (sourceId != null && folderId != null) {
                workspace = if (workspace.items.firstOrNull { it.id == folderId } is HomeItem.FolderItem) WorkspaceOperations.addToFolder(workspace, folderId, sourceId) else WorkspaceOperations.createFolder(workspace, sourceId, folderId)
                persistWorkspace(); renderWorkspace()
            } else if (sourceId != null && pageTarget != null) {
                workspace = WorkspaceOperations.moveToPage(workspace, sourceId, pageTarget)
                persistWorkspace(); renderWorkspace(); homePager.setCurrentItem(pageTarget, animationsEnabled())
            } else {
                val ids = (recyclerView.adapter as? WorkspaceItemAdapter)?.currentIds().orEmpty()
                workspace = WorkspaceOperations.reorderPage(workspace, holder.pageIndex, ids, parsedColumns())
                persistWorkspace(); renderWorkspace()
            }
            dragActionBar.visibility = View.GONE
            viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            draggedId = null; folderTargetId = null; targetPage = null; dragActionTarget = null
            homePager.isUserInputEnabled = true
        }
    }

    private fun showWidgetPicker() {
        val manager = AppWidgetManager.getInstance(this)
        val providers = manager.installedProviders.filter { it.provider != null }
        if (providers.isEmpty()) { Toast.makeText(this, "No home-screen widgets are available", Toast.LENGTH_SHORT).show(); return }
        val labels = providers.map { "${it.loadLabel(packageManager)} · ${it.provider.packageName}" }.toTypedArray()
        android.app.AlertDialog.Builder(this).setTitle("Choose widget").setItems(labels) { _, index ->
            val info = providers[index]
            if (Build.VERSION.SDK_INT >= 26 && manager.isRequestPinAppWidgetSupported) {
                val callbackIntent = Intent(this, WidgetPinReceiver::class.java).setAction("com.glassline.launcher.action.WIDGET_PIN_CALLBACK")
                val mutableFlag = if (Build.VERSION.SDK_INT >= 31) android.app.PendingIntent.FLAG_MUTABLE else 0
                val callbackFlags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
                val callback = android.app.PendingIntent.getBroadcast(this, (SystemClock.uptimeMillis() and 0x7FFFFFFF).toInt(), callbackIntent, callbackFlags)
                val requested = runCatching { manager.requestPinAppWidget(info.provider, null, callback) }.getOrDefault(false)
                if (requested) return@setItems
            }
            pendingWidgetInfo = info
            pendingWidgetId = widgetHost.allocateAppWidgetId()
            if (manager.bindAppWidgetIdIfAllowed(pendingWidgetId, info.provider)) configureOrPlaceWidget(pendingWidgetId, info)
            else {
                val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId).putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                if (Build.VERSION.SDK_INT >= 21) bindIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, android.os.Process.myUserHandle())
                widgetBindResult.launch(bindIntent)
            }
        }.show()
    }

    private fun configureOrPlaceWidget(id: Int, info: AppWidgetProviderInfo) {
        val configure = info.configure
        if (configure == null) { placeWidget(id, info); return }
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(configure).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        runCatching { widgetConfigureResult.launch(intent) }.onFailure { discardPendingWidget() }
    }

    private fun placeWidget(id: Int, info: AppWidgetProviderInfo) {
        val page = homePager.currentItem.coerceIn(0, workspace.pageCount - 1)
        val nextY = (workspace.items.filter { it.page == page }.maxOfOrNull { it.y } ?: -1) + 1
        val cellWidthDp = (resources.displayMetrics.widthPixels / resources.displayMetrics.density / parsedColumns()).toInt().coerceAtLeast(1)
        val spanX = (info.minWidth / cellWidthDp).coerceAtLeast(1).coerceAtMost(parsedColumns())
        val spanY = (info.minHeight / 96).coerceAtLeast(1).coerceAtMost(4)
        workspace = workspace.copy(items = workspace.items + HomeItem.WidgetItem(id, page, 0, nextY, spanX, spanY))
        widgetViews[id] = widgetHost.createView(this, id, info).apply { setAppWidget(id, info) }
        persistWorkspace(); renderWorkspace(); pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID; pendingWidgetInfo = null
    }

    private fun discardPendingWidget() {
        if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) runCatching { widgetHost.deleteAppWidgetId(pendingWidgetId) }
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID; pendingWidgetInfo = null
    }

    private fun removeWidget(item: HomeItem.WidgetItem) {
        workspace = workspace.copy(items = workspace.items.filterNot { it.id == item.id })
        widgetViews.remove(item.appWidgetId)?.let { (it.parent as? ViewGroup)?.removeView(it) }
        runCatching { widgetHost.deleteAppWidgetId(item.appWidgetId) }
        persistWorkspace(); renderWorkspace()
    }

    private fun resizeWidget(item: HomeItem.WidgetItem, spanX: Int, spanY: Int) {
        workspace = workspace.copy(items = workspace.items.map { current -> if (current.id == item.id) item.copy(spanX = spanX.coerceIn(1, parsedColumns()), spanY = spanY.coerceIn(1, 6)) else current })
        persistWorkspace(); renderWorkspace()
    }

    private fun showEmptyPageMenu(page: Int) {
        val options = arrayOf("Open app drawer", "Add widget", "Create folder")
        android.app.AlertDialog.Builder(this).setTitle("Empty home page").setItems(options) { _, which ->
            when (which) {
                0 -> showDrawer()
                1 -> showWidgetPicker()
                2 -> { val next = (workspace.items.filter { it.page == page }.maxOfOrNull { it.y * parsedColumns() + it.x } ?: -1) + 1; workspace = workspace.copy(items = workspace.items + HomeItem.FolderItem("Folder", emptyList(), page, next % parsedColumns(), next / parsedColumns())); persistWorkspace(); renderWorkspace() }
            }
        }.show()
    }

    private fun showHomeContextMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("Launcher settings").setOnMenuItemClickListener { startActivity(Intent(this@LauncherActivity, SettingsActivity::class.java)); true }
            menu.add("Change wallpaper").setOnMenuItemClickListener { runCatching { startActivity(Intent(Intent.ACTION_SET_WALLPAPER)) }; true }
            menu.add("Add widget").setOnMenuItemClickListener { showWidgetPicker(); true }
            show()
        }
    }

    private fun moveAppToPage(app: LaunchableApp) {
        val item = workspace.items.firstOrNull { it is HomeItem.AppItem && it.packageName == app.packageName && it.activityName == app.activityName && it.userSerial == app.userSerial } as? HomeItem.AppItem
        if (item == null) { pinToHome(app); return }
        val labels = (0 until workspace.pageCount).map { "Page ${it + 1}" }.toTypedArray()
        android.app.AlertDialog.Builder(this).setTitle("Move ${app.label} to").setItems(labels) { _, which ->
            workspace = WorkspaceOperations.moveToPage(workspace, item.id, which)
            persistWorkspace(); renderWorkspace(); homePager.setCurrentItem(which, animationsEnabled())
        }.show()
    }

    private fun showFolder(folder: HomeItem.FolderItem) {
        val grid = android.widget.GridLayout(this).apply { columnCount = 3; setPadding(dp(12), dp(12), dp(12), dp(12)) }
        val dialog = android.app.AlertDialog.Builder(this).setTitle(folder.label).setView(grid).setNegativeButton("Close", null).create()
        if (folder.children.isEmpty()) {
            grid.columnCount = 1
            grid.addView(TextView(this).apply { text = "Drop an app onto this folder to add it"; gravity = Gravity.CENTER; setTextColor(getColor(R.color.glass_muted)); setPadding(dp(20), dp(28), dp(20), dp(28)); contentDescription = "Empty folder. Drop an app here to add it." }, android.widget.GridLayout.LayoutParams().apply { width = -1; height = dp(100) })
        }
        folder.children.forEach { child ->
            val app = when (child) {
                is HomeItem.AppItem -> appsSnapshot.firstOrNull { it.packageName == child.packageName && it.activityName == child.activityName && it.userSerial == child.userSerial }
                is HomeItem.ShortcutItem -> appsSnapshot.firstOrNull { it.packageName == child.shortcutKey.packageName && it.userSerial == child.shortcutKey.userSerial }
                else -> null
            }
            val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; isClickable = true; isFocusable = true; setPadding(dp(8), dp(10), dp(8), dp(10)) }
            val icon = ImageView(this).apply { setImageDrawable(app?.let { iconPackManager.resolveIcon(prefs.getString("icon_pack_package", null), it.packageName, it.activityName, fallbackIcon(it)) } ?: getDrawable(android.R.drawable.ic_menu_agenda)); scaleType = ImageView.ScaleType.FIT_CENTER }
            cell.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))
            cell.addView(TextView(this).apply { text = app?.label ?: child.id; textSize = 11f; gravity = Gravity.CENTER; maxLines = 2; setTextColor(getColor(R.color.glass_text)) }, LinearLayout.LayoutParams(-1, dp(34)))
            cell.contentDescription = app?.let { "Launch ${it.label} from ${folder.label}" } ?: "${folder.label} item"
            cell.setOnClickListener {
                when (child) {
                    is HomeItem.AppItem -> app?.let { launch(it, icon) }
                    is HomeItem.ShortcutItem -> launchShortcut(child.shortcutKey)
                    else -> Unit
                }
                dialog.dismiss()
            }
            cell.setOnLongClickListener {
                dialog.dismiss()
                android.app.AlertDialog.Builder(this).setTitle("Move item out of folder?").setMessage("${app?.label ?: child.id} will be placed on the home screen.").setNegativeButton("Cancel", null).setPositiveButton("Move out") { _, _ -> workspace = WorkspaceOperations.removeFolderChild(workspace, folder.id, child.id); persistWorkspace(); renderWorkspace() }.show()
                true
            }
            grid.addView(cell, android.widget.GridLayout.LayoutParams().apply { width = 0; height = dp(82); columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f); setMargins(dp(2), dp(2), dp(2), dp(2)) })
        }
        dialog.show()
    }

    private fun showFolderMenu(folder: HomeItem.FolderItem, anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("Rename folder").setOnMenuItemClickListener {
                val input = EditText(this@LauncherActivity).apply { setText(folder.label); setSelection(text.length) }
                android.app.AlertDialog.Builder(this@LauncherActivity).setTitle("Folder name").setView(input).setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ -> workspace = WorkspaceOperations.renameFolder(workspace, folder.id, input.text.toString()); persistWorkspace(); renderWorkspace() }.show(); true
            }
            menu.add("Remove a folder item").setOnMenuItemClickListener {
                val children = folder.children
                android.app.AlertDialog.Builder(this@LauncherActivity).setTitle("Remove item").setItems(children.map { child -> when(child) { is HomeItem.AppItem -> appsSnapshot.firstOrNull { it.packageName == child.packageName && it.activityName == child.activityName && it.userSerial == child.userSerial }?.label ?: child.packageName; else -> child.id } }.toTypedArray()) { _, which ->
                    val child = children.getOrNull(which) ?: return@setItems
                    workspace = WorkspaceOperations.removeFolderChild(workspace, folder.id, child.id); persistWorkspace(); renderWorkspace()
                }.show(); true
            }
            menu.add("Dissolve folder").setOnMenuItemClickListener { workspace = WorkspaceOperations.dissolveFolder(workspace, folder.id); persistWorkspace(); renderWorkspace(); true }
            show()
        }
    }

    private fun chooseIconOverride(app: LaunchableApp) {
        val pack = prefs.getString("icon_pack_package", null)
        if (pack.isNullOrBlank()) { Toast.makeText(this, "Choose an icon pack in Settings first", Toast.LENGTH_SHORT).show(); return }
        val loading = android.app.AlertDialog.Builder(this).setMessage("Loading icon pack…").setCancelable(false).create()
        loading.show()
        lifecycleScope.launch {
            val icons = withContext(Dispatchers.IO) { runCatching { iconPackManager.icons(pack) }.getOrDefault(emptyList()) }
            loading.dismiss()
            if (icons.isEmpty()) { Toast.makeText(this@LauncherActivity, "This icon pack has no readable appfilter.xml", Toast.LENGTH_LONG).show(); return@launch }
            android.app.AlertDialog.Builder(this@LauncherActivity).setTitle("Choose icon for ${app.label}").setItems(icons.map { it.label }.toTypedArray()) { _, which ->
                iconPackManager.setOverride(app.packageName, app.activityName, pack, icons[which].drawableName)
                adapter.notifyItemRangeChanged(0, adapter.itemCount); workspacePagerAdapter.refresh(); renderDock()
            }.setNegativeButton("Cancel", null).show()
        }
    }


    private fun updateClock() {
        if (!::clock.isInitialized || !::date.isInitialized) return
        val now = Date()
        val clockText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        val dateText = SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(now)
        if (clockText != lastClock) { clock.text = clockText; lastClock = clockText }
        if (date.text.toString() != dateText) date.text = dateText
    }
    private fun applyWallpaper() {
        if (!prefs.getBoolean("show_wallpaper", false)) {
            root.setBackgroundColor(getColor(R.color.glass_bg))
            return
        }
        wallpaperCache?.let { root.background = it; return }
        if (wallpaperLoadJob?.isActive == true) return
        wallpaperLoadJob = lifecycleScope.launch {
            val drawable = withContext(Dispatchers.IO) { runCatching { getSystemService<android.app.WallpaperManager>()?.drawable }.getOrNull() }
            if (!isFinishing && !isDestroyed && prefs.getBoolean("show_wallpaper", false)) {
                wallpaperCache = drawable
                if (drawable != null) root.background = drawable else root.setBackgroundColor(getColor(R.color.glass_bg))
            }
        }
    }
    private fun accentColor(): Int {
        val value = android.util.TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, value, true)
        return if (value.resourceId != 0) getColor(value.resourceId) else value.data
    }
    private fun rounded(color: Int, radius: Int) = android.graphics.drawable.GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun parsedColumns() = LauncherLogic.parseColumns(prefs.getInt("columns", 4))
    private fun isAppAdapterReady() = this::adapter.isInitialized

    override fun onStart() {
        super.onStart()
        val timeFilter = IntentFilter().apply { addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_LOCALE_CHANGED) }
        // System-originated protected broadcasts (especially ACTION_TIME_TICK) must be received
        // from the Android system process; this receiver performs no external action.
        ContextCompat.registerReceiver(this, timeReceiver, timeFilter, ContextCompat.RECEIVER_EXPORTED)
        launcherAppsService.registerCallback(launcherAppsCallback)
        ContextCompat.registerReceiver(this, wallpaperReceiver, IntentFilter(Intent.ACTION_WALLPAPER_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, widgetPinReceiver, IntentFilter(WidgetPinReceiver.ACTION_WIDGET_PINNED), ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
        runCatching { widgetHost.startListening() }
        updateClock()
    }
    override fun onStop() {
        if (receiverRegistered) { runCatching { unregisterReceiver(timeReceiver) }; runCatching { unregisterReceiver(wallpaperReceiver) }; runCatching { unregisterReceiver(widgetPinReceiver) }; receiverRegistered = false }
        runCatching { launcherAppsService.unregisterCallback(launcherAppsCallback) }
        wallpaperLoadJob?.cancel()
        runCatching { widgetHost.stopListening() }
        super.onStop()
    }
    override fun onResume() {
        super.onResume()
        if (prefs.getInt("accent_index", 0).coerceIn(0, 5) != appliedAccentIndex) { recreate(); return }
        if (::adapter.isInitialized) reloadApps(); if (::workspaceStore.isInitialized) reloadWorkspace(); applyWallpaper()
    }
    override fun onSharedPreferenceChanged(sharedPreferences: android.content.SharedPreferences?, key: String?) {
        runOnUiThread {
            when (key) {
                "columns" -> { (appGrid.layoutManager as? GridLayoutManager)?.spanCount = parsedColumns(); if (::alphabetRail.isInitialized) appGrid.setPaddingRelative(dp(0), dp(0), dp(24), dp(0)); if (::workspacePagerAdapter.isInitialized) workspacePagerAdapter.refresh() }
                "icon_size", "labels" -> { adapter.notifyItemRangeChanged(0, adapter.itemCount); if (::workspacePagerAdapter.isInitialized) workspacePagerAdapter.refresh() }
                "show_wallpaper" -> applyWallpaper()
                "hidden_apps" -> adapter.reloadHidden()
                "icon_pack_package" -> {
                    iconPackManager.clearCache()
                    val pack = prefs.getString("icon_pack_package", null)
                    lifecycleScope.launch { withContext(Dispatchers.IO) { val components = appsSnapshot.map { android.content.ComponentName(it.packageName, it.activityName) }; if (pack != null) iconPackManager.preload(pack, components); iconPackManager.preloadOverrides(components) }; adapter.notifyItemRangeChanged(0, adapter.itemCount); if (::workspacePagerAdapter.isInitialized) workspacePagerAdapter.refresh(); renderDock() }
                }
                else -> if (key?.startsWith("icon_override:") == true) {
                    iconPackManager.clearCache()
                    lifecycleScope.launch { withContext(Dispatchers.IO) { val components = appsSnapshot.map { android.content.ComponentName(it.packageName, it.activityName) }; prefs.getString("icon_pack_package", null)?.let { iconPackManager.preload(it, components) }; iconPackManager.preloadOverrides(components) }; adapter.notifyItemRangeChanged(0, adapter.itemCount); if (::workspacePagerAdapter.isInitialized) workspacePagerAdapter.refresh(); renderDock() }
                }
            }
        }
    }
    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        reloadJob?.cancel(); filterJob?.cancel(); workspaceJob?.cancel(); workspaceSaveJob?.cancel()
        super.onDestroy()
    }

    private sealed interface DrawerEntry {
        data class Header(val letter: String) : DrawerEntry
        data class App(val value: LaunchableApp) : DrawerEntry
        data class Empty(val query: String) : DrawerEntry
    }

    inner class AppAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var all: List<LaunchableApp> = emptyList()
        private var shown: List<DrawerEntry> = emptyList()
        private val hidden = LauncherLogic.readHiddenApps(prefs).toMutableSet()
        private val headerType = 1
        private val appType = 2
        inner class HeaderHolder(val title: TextView) : RecyclerView.ViewHolder(title)
        inner class AppHolder(val box: LinearLayout, val icon: ImageView, val label: TextView) : RecyclerView.ViewHolder(box)

        override fun getItemViewType(position: Int): Int = when (shown[position]) { is DrawerEntry.Header -> headerType; is DrawerEntry.Empty -> emptyType; is DrawerEntry.App -> appType }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == emptyType) {
                val message = TextView(parent.context).apply { textSize = 15f; gravity = Gravity.CENTER; setTextColor(getColor(R.color.glass_muted)); setPadding(dp(24), dp(24), dp(24), dp(24)); minHeight = dp(180); isClickable = true; isFocusable = true }
                return EmptyHolder(message)
            }
            if (viewType == headerType) {
                val title = TextView(parent.context).apply { textSize = 12f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(accentColor()); setPadding(dp(4), dp(12), dp(4), dp(6)); contentDescription = "Alphabetical section" }
                return HeaderHolder(title)
            }
            val box = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(4), dp(8), dp(4), dp(8)); isFocusable = true; isClickable = true }
            val icon = ImageView(parent.context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
            val label = TextView(parent.context).apply { textSize = 11f; gravity = Gravity.CENTER; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; setTextColor(getColor(R.color.glass_text)) }
            box.addView(icon, LinearLayout.LayoutParams(dp(prefs.getInt("icon_size", 52).coerceIn(36, 68)), dp(prefs.getInt("icon_size", 52).coerceIn(36, 68))))
            box.addView(label, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { minHeight = dp(34); topMargin = dp(5) })
            return AppHolder(box, icon, label)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val entry = shown[position]) {
                is DrawerEntry.Header -> { (holder as HeaderHolder).title.text = entry.letter; holder.title.contentDescription = "Section ${entry.letter}" }
                is DrawerEntry.Empty -> { val empty = holder as EmptyHolder; empty.message.text = "No apps found for ‘${entry.query}’\nTap to clear search"; empty.message.contentDescription = "No apps found. Clear search."; empty.message.setOnClickListener { search.setText("") } }
                is DrawerEntry.App -> {
                    val appHolder = holder as AppHolder
                    val app = entry.value
                    appHolder.label.text = if (prefs.getBoolean("labels", true)) app.label else ""
                    val size = dp(prefs.getInt("icon_size", 52).coerceIn(36, 68))
                    appHolder.icon.layoutParams = appHolder.icon.layoutParams.apply { width = size; height = size }
                    appHolder.icon.setImageDrawable(iconPackManager.resolveIcon(prefs.getString("icon_pack_package", null), app.packageName, app.activityName, fallbackIcon(app)))
                    appHolder.box.contentDescription = "Launch ${app.label}"
                    appHolder.icon.contentDescription = "Launch ${app.label}"
                    appHolder.box.setOnClickListener { launch(app, appHolder.icon) }
                    appHolder.box.setOnLongClickListener { showAppMenu(app, appHolder.box); true }
                }
            }
        }

        override fun getItemCount() = shown.size
        fun isHeader(position: Int): Boolean = shown.getOrNull(position) is DrawerEntry.Header
        fun isFullSpan(position: Int): Boolean = shown.getOrNull(position) !is DrawerEntry.App
        fun scrollToLetter(letter: String) {
            val position = shown.indexOfFirst { it is DrawerEntry.Header && it.letter == letter }
            if (position >= 0) appGrid.scrollToPosition(position)
        }
        fun submit(items: List<LaunchableApp>) { all = items.toList(); filter(query) }
        fun filter(raw: String) {
            val q = normalize(raw)
            val visible = all.filter { it.packageName !in hidden && LauncherLogic.matchesQuery(it.label, it.packageName, q) }
            val next: List<DrawerEntry> = if (q.isNotEmpty()) {
                val ranked = visible.sortedWith(compareByDescending<LaunchableApp> { usageTracker.queryMatchStrength(it, q) }.thenByDescending { usageTracker.score(it) }.thenBy { normalize(it.label) })
                if (ranked.isEmpty()) listOf(DrawerEntry.Empty(raw.trim())) else ranked.map { DrawerEntry.App(it) }
            } else {
                visible.sortedBy { normalize(it.label) }.groupBy { firstLetter(it.label) }.toSortedMap(compareBy<String> { if (it == "#") 1 else 0 }.thenBy { it }).flatMap { (letter, group) ->
                    listOf(DrawerEntry.Header(letter)) + group.map { DrawerEntry.App(it) }
                }
            }
            val previous = shown
            val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = previous.size
                override fun getNewListSize() = next.size
                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    val old = previous[oldItemPosition]; val new = next[newItemPosition]
                    return when {
                        old is DrawerEntry.Header && new is DrawerEntry.Header -> old.letter == new.letter
                        old is DrawerEntry.App && new is DrawerEntry.App -> old.value.packageName == new.value.packageName && old.value.activityName == new.value.activityName && old.value.userSerial == new.value.userSerial
                        else -> false
                    }
                }
                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) = previous[oldItemPosition] == next[newItemPosition]
            })
            shown = next
            diff.dispatchUpdatesTo(this)
        }
        fun isHidden(packageName: String) = packageName in hidden
        fun reloadHidden() { hidden.clear(); hidden.addAll(prefs.getStringSet("hidden_apps", emptySet()) ?: emptySet()); filter(query) }
        fun toggleHidden(app: LaunchableApp) {
            if (!hidden.add(app.packageName)) hidden.remove(app.packageName)
            LauncherLogic.saveHiddenApps(prefs, hidden)
            filter(query)
        }
        private fun firstLetter(label: String): String = normalize(label).firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' }?.toString() ?: "#"
    }

    inner class WorkspacePagerAdapter : RecyclerView.Adapter<WorkspacePagerAdapter.PageHolder>() {
        inner class PageHolder(val grid: RecyclerView) : RecyclerView.ViewHolder(grid) { var pageIndex: Int = 0 }
        init { setHasStableIds(true) }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val grid = RecyclerView(parent.context)
            val gridLayout = GridLayoutManager(parent.context, parsedColumns())
            gridLayout.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int = ((grid.adapter as? WorkspaceItemAdapter)?.itemAt(position) as? HomeItem.WidgetItem)?.spanX?.coerceIn(1, spanCount) ?: 1
            }
            grid.layoutManager = gridLayout
            grid.itemAnimator = null
            grid.overScrollMode = View.OVER_SCROLL_NEVER
            grid.isLongClickable = true
            grid.adapter = WorkspaceItemAdapter()
            grid.setOnLongClickListener { showHomeContextMenu(grid); true }
            val holder = PageHolder(grid)
            ItemTouchHelper(workspaceDragCallback(holder)).attachToRecyclerView(grid)
            return holder
        }
        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            holder.pageIndex = position
            (holder.grid.adapter as WorkspaceItemAdapter).submit(workspace.items.filter { it.page == position }.sortedWith(compareBy<HomeItem> { it.y }.thenBy { it.x }))
        }
        override fun getItemCount(): Int = workspace.pageCount.coerceIn(1, 20)
        override fun getItemId(position: Int): Long = position.toLong()
        fun refresh() {
            val pagerRecycler = homePager.getChildAt(0) as? RecyclerView
            if (pagerRecycler != null) for (index in 0 until pagerRecycler.childCount) {
                val pageGrid = pagerRecycler.getChildAt(index) as? RecyclerView ?: continue
                (pageGrid.layoutManager as? GridLayoutManager)?.spanCount = parsedColumns()
                pageGrid.adapter?.notifyDataSetChanged()
            }
            notifyDataSetChanged()
            updatePageIndicator(homePager.currentItem.coerceAtMost(itemCount - 1))
        }
    }

    inner class WorkspaceItemAdapter : RecyclerView.Adapter<WorkspaceItemAdapter.ItemHolder>() {
        private var items: List<HomeItem> = emptyList()
        inner class ItemHolder(val box: LinearLayout, val icon: ImageView, val label: TextView) : RecyclerView.ViewHolder(box)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder {
            val box = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(4), dp(8), dp(4), dp(8)); isClickable = true; isFocusable = true }
            val icon = ImageView(parent.context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
            val label = TextView(parent.context).apply { textSize = 11f; gravity = Gravity.CENTER; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; setTextColor(getColor(R.color.glass_text)) }
            box.addView(icon, LinearLayout.LayoutParams(dp(prefs.getInt("icon_size", 52).coerceIn(36, 68)), dp(prefs.getInt("icon_size", 52).coerceIn(36, 68))))
            box.addView(label, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { minHeight = dp(34); topMargin = dp(5) })
            return ItemHolder(box, icon, label)
        }
        override fun onBindViewHolder(holder: ItemHolder, position: Int) {
            holder.box.findViewWithTag<View>("widget-resize-handle")?.let { holder.box.removeView(it) }
            holder.box.findViewWithTag<View>("folder-preview")?.let { holder.box.removeView(it) }
            widgetViews.values.filter { it.parent === holder.box }.forEach { holder.box.removeView(it) }
            holder.icon.visibility = View.VISIBLE; holder.label.visibility = View.VISIBLE
            holder.box.layoutParams = holder.box.layoutParams.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
            if (items.isNotEmpty() && position >= items.size) {
                holder.icon.setImageDrawable(null); holder.label.text = ""; holder.box.contentDescription = "Empty home slot"
                // Empty cells are real workspace targets, not inert decoration: users can
                // open the page actions there and can reach them with TalkBack.
                holder.box.setOnClickListener { showEmptyPageMenu(homePager.currentItem) }
                holder.box.setOnLongClickListener { showHomeContextMenu(holder.box); true }
                return
            }
            if (items.isEmpty()) {
                holder.icon.setImageDrawable(null)
                holder.label.text = "Pin apps from the drawer"
                holder.box.contentDescription = "Empty home page. Pin apps from the app drawer."
                holder.box.setOnClickListener { showEmptyPageMenu(items.firstOrNull()?.page ?: homePager.currentItem) }
                holder.box.setOnLongClickListener { showHomeContextMenu(holder.box); true }
                return
            }
            val item = items[position]
            val app = when (item) {
                is HomeItem.AppItem -> appsSnapshot.firstOrNull { it.packageName == item.packageName && it.activityName == item.activityName && it.userSerial == item.userSerial }
                is HomeItem.ShortcutItem -> appsSnapshot.firstOrNull { it.packageName == item.shortcutKey.packageName && it.userSerial == item.shortcutKey.userSerial }
                else -> null
            }
            val widget = item as? HomeItem.WidgetItem
            if (widget != null) {
                holder.icon.visibility = View.GONE; holder.label.visibility = View.GONE
                val info = AppWidgetManager.getInstance(this@LauncherActivity).getAppWidgetInfo(widget.appWidgetId)
                if (info != null) {
                    val hostView = widgetViews.getOrPut(widget.appWidgetId) { widgetHost.createView(this@LauncherActivity, widget.appWidgetId, info).apply { setAppWidget(widget.appWidgetId, info) } }
                    (hostView.parent as? ViewGroup)?.removeView(hostView)
                    holder.box.addView(hostView, LinearLayout.LayoutParams(-1, dp(96 * widget.spanY)))
                    holder.box.layoutParams = holder.box.layoutParams.apply { height = dp(96 * widget.spanY) }
                    var resizeDownX = 0f; var resizeDownY = 0f
                    val resizeHandle = TextView(this@LauncherActivity).apply {
                        tag = "widget-resize-handle"; text = "↘ Resize"; gravity = Gravity.END or Gravity.CENTER_VERTICAL; textSize = 11f; setTextColor(accentColor()); contentDescription = "Resize widget"
                        setOnTouchListener { _, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> { resizeDownX = event.rawX; resizeDownY = event.rawY; true }
                                MotionEvent.ACTION_UP -> { val deltaX = ((event.rawX - resizeDownX) / dp(72)).toInt(); val deltaY = ((event.rawY - resizeDownY) / dp(72)).toInt(); if (deltaX != 0 || deltaY != 0) resizeWidget(widget, widget.spanX + deltaX, widget.spanY + deltaY); true }
                                else -> true
                            }
                        }
                    }
                    holder.box.addView(resizeHandle, LinearLayout.LayoutParams(-1, dp(24)))
                } else {
                    holder.icon.visibility = View.VISIBLE; holder.label.visibility = View.VISIBLE; holder.icon.setImageDrawable(getDrawable(android.R.drawable.ic_menu_view)); holder.label.text = "Widget unavailable"
                }
                holder.box.contentDescription = "Widget"
                holder.box.setOnClickListener { }
                holder.box.setOnLongClickListener { PopupMenu(this@LauncherActivity, holder.box).apply { menu.add("Widen widget").setOnMenuItemClickListener { resizeWidget(widget, widget.spanX + 1, widget.spanY); true }; menu.add("Make widget taller").setOnMenuItemClickListener { resizeWidget(widget, widget.spanX, widget.spanY + 1); true }; menu.add("Remove widget").setOnMenuItemClickListener { removeWidget(widget); true }; show() }; true }
                return
            } else {
                holder.icon.visibility = View.VISIBLE; holder.label.visibility = View.VISIBLE
                holder.box.layoutParams = holder.box.layoutParams.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
            }
            holder.icon.setImageDrawable(app?.let { iconPackManager.resolveIcon(prefs.getString("icon_pack_package", null), it.packageName, it.activityName, fallbackIcon(it)) } ?: when (item) { is HomeItem.FolderItem -> getDrawable(android.R.drawable.ic_menu_agenda); else -> null })
            holder.label.text = when (item) { is HomeItem.AppItem -> app?.label ?: "Unavailable app"; is HomeItem.FolderItem -> item.label; is HomeItem.WidgetItem -> "Widget"; is HomeItem.ShortcutItem -> app?.label ?: item.shortcutKey.shortcutId }
            if (item is HomeItem.FolderItem) {
                holder.icon.visibility = View.GONE
                val preview = android.widget.GridLayout(this@LauncherActivity).apply {
                    tag = "folder-preview"; columnCount = 2; rowCount = 2; useDefaultMargins = false
                    setPadding(dp(2), dp(2), dp(2), dp(2))
                }
                item.children.take(4).forEach { child ->
                    val childApp = when (child) {
                        is HomeItem.AppItem -> appsSnapshot.firstOrNull { it.packageName == child.packageName && it.activityName == child.activityName && it.userSerial == child.userSerial }
                        is HomeItem.ShortcutItem -> appsSnapshot.firstOrNull { it.packageName == child.shortcutKey.packageName && it.userSerial == child.shortcutKey.userSerial }
                        else -> null
                    }
                    val miniIcon = ImageView(this@LauncherActivity).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setImageDrawable(childApp?.let { iconPackManager.resolveIcon(prefs.getString("icon_pack_package", null), it.packageName, it.activityName, fallbackIcon(it)) }
                            ?: getDrawable(android.R.drawable.ic_menu_agenda))
                        contentDescription = childApp?.let { "${it.label} in ${item.label}" } ?: "Folder item"
                    }
                    preview.addView(miniIcon, android.widget.GridLayout.LayoutParams().apply {
                        width = dp(19); height = dp(19)
                        rowSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED)
                        columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED)
                        setMargins(dp(1), dp(1), dp(1), dp(1))
                    })
                }
                repeat((4 - item.children.take(4).size).coerceAtLeast(0)) {
                    preview.addView(View(this@LauncherActivity).apply { background = rounded(getColor(R.color.glass_surface), 5) },
                        android.widget.GridLayout.LayoutParams().apply {
                            width = dp(19); height = dp(19)
                            rowSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED)
                            columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED)
                            setMargins(dp(1), dp(1), dp(1), dp(1))
                        })
                }
                holder.box.addView(preview, 1, LinearLayout.LayoutParams(dp(44), dp(44)))
            }
            if (!prefs.getBoolean("labels", true)) holder.label.text = ""
            holder.box.contentDescription = when (item) { is HomeItem.AppItem -> "Launch ${app?.label ?: item.packageName}"; is HomeItem.FolderItem -> "Folder ${item.label}, ${item.children.size} items"; is HomeItem.WidgetItem -> "Widget"; is HomeItem.ShortcutItem -> "Shortcut ${holder.label.text}" }
            holder.box.setOnClickListener {
                when (item) {
                    is HomeItem.AppItem -> if (app != null) launch(app, holder.icon)
                    is HomeItem.ShortcutItem -> {
                        launchShortcut(item.shortcutKey)
                    }
                    is HomeItem.FolderItem -> showFolder(item)
                    is HomeItem.WidgetItem -> Toast.makeText(this@LauncherActivity, "Use the widget's own controls.", Toast.LENGTH_SHORT).show()
                }
            }
            holder.box.setOnLongClickListener { when (item) { is HomeItem.AppItem -> { if (app != null) showAppMenu(app, holder.box); app != null }; is HomeItem.FolderItem -> { showFolderMenu(item, holder.box); true }; is HomeItem.ShortcutItem -> { PopupMenu(this@LauncherActivity, holder.box).apply { menu.add("Remove shortcut").setOnMenuItemClickListener { workspace = workspace.copy(items = workspace.items.filterNot { it.id == item.id }); persistWorkspace(); renderWorkspace(); true }; show() }; true }; is HomeItem.WidgetItem -> { PopupMenu(this@LauncherActivity, holder.box).apply { menu.add("Widen widget").setOnMenuItemClickListener { resizeWidget(item, item.spanX + 1, item.spanY); true }; menu.add("Make widget taller").setOnMenuItemClickListener { resizeWidget(item, item.spanX, item.spanY + 1); true }; menu.add("Remove widget").setOnMenuItemClickListener { removeWidget(item); true }; show() }; true } } }
        }
        override fun getItemCount(): Int = if (items.isEmpty()) 1 else maxOf(items.size, 12)
        override fun getItemId(position: Int): Long = items.getOrNull(position)?.id?.hashCode()?.toLong() ?: (Long.MIN_VALUE + position)
        init { setHasStableIds(true) }
        fun itemAt(position: Int): HomeItem? = items.getOrNull(position)
        fun moveLocal(from: Int, to: Int) {
            if (from !in items.indices) return
            val mutable = items.toMutableList()
            val moving = mutable.removeAt(from)
            val targetIndex = to.coerceIn(0, mutable.size)
            mutable.add(targetIndex, moving)
            items = mutable
            notifyItemMoved(from, targetIndex)
        }
        fun currentIds(): List<String> = items.map { it.id }
        fun submit(newItems: List<HomeItem>) {
            val previous = items
            val next = newItems.toList()
            val oldSize = if (previous.isEmpty()) 1 else maxOf(previous.size, 12)
            val newSize = if (next.isEmpty()) 1 else maxOf(next.size, 12)
            val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = oldSize
                override fun getNewListSize() = newSize
                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    val oldItem = previous.getOrNull(oldItemPosition); val newItem = next.getOrNull(newItemPosition)
                    return if (oldItem == null || newItem == null) oldItem == null && newItem == null && oldItemPosition == newItemPosition else oldItem.id == newItem.id
                }
                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean = previous.getOrNull(oldItemPosition) == next.getOrNull(newItemPosition)
            })
            items = next
            diff.dispatchUpdatesTo(this)
        }
    }

    private fun iconCacheKey(packageName: String, activityName: String, userSerial: Long) = "$packageName|$activityName|$userSerial"
    private fun fallbackIcon(app: LaunchableApp): Drawable? = appIconCache.get(iconCacheKey(app.packageName, app.activityName, app.userSerial))
    data class LaunchableApp(val label: String, val packageName: String, val activityName: String, val userHandle: android.os.UserHandle = android.os.Process.myUserHandle(), val userSerial: Long = 0L)
    companion object { internal fun normalize(value: String): String = LauncherLogic.normalize(value) }
}
