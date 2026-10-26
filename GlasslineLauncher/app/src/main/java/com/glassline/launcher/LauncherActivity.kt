
package com.glassline.launcher

import android.app.Activity
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.setPadding

class LauncherActivity : Activity() {

    data class LaunchableApp(
        val packageName: String,
        val activityName: String,
        val label: String,
        val userSerial: Long = 0L,
        val resolveInfo: ResolveInfo? = null
    )

    private lateinit var root: LinearLayout
    private lateinit var appGrid: GridLayout
    private lateinit var searchField: EditText
    private lateinit var statusText: TextView

    private val apps = mutableListOf<LaunchableApp>()
    private var columns = 4
    private var showLabels = true

    private val prefs by lazy {
        getSharedPreferences("glassline", MODE_PRIVATE)
    }

    private val usageTracker by lazy {
        AppUsageTracker(this)
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
        date = TextView(this).apply {
            textSize = 14f
            letterSpacing = 0.015f
            typeface = androidx.core.content.res.ResourcesCompat.getFont(this@LauncherActivity, R.font.roboto_medium)
            setTextColor(getColor(com.glassline.launcher.R.color.glass_muted))
        }
        val settings = TextView(this).apply {
            text = "⚙"
            textSize = 20f
            setTextColor(getColor(R.color.glass_text))
            gravity = Gravity.CENTER
            background = glassChipDrawable(22)
            contentDescription = "Launcher settings"
            isClickable = true
            isFocusable = true
            setOnClickListener { startActivity(Intent(this@LauncherActivity, SettingsActivity::class.java)) }
        }
        top.addView(date, LinearLayout.LayoutParams(0, dp(44), 1f))
        top.addView(settings, LinearLayout.LayoutParams(dp(44), dp(44)))
        home.addView(top)
        clock = TextView(this).apply {
            textSize = 60f
            letterSpacing = -0.055f
            maxLines = 1
            includeFontPadding = false
            typeface = androidx.core.content.res.ResourcesCompat.getFont(this@LauncherActivity, R.font.roboto_regular)
            setTextColor(getColor(R.color.glass_text))
            setPadding(0, dp(18), 0, dp(5))
            contentDescription = "Current time"
        }
        home.addView(clock)
        val subtitle = TextView(this).apply {
            text = "A quieter space for your apps"
            textSize = 13.5f
            letterSpacing = 0.005f
            setTextColor(getColor(R.color.glass_muted))
            setPadding(0, 0, 0, dp(20))
        }
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
        dockContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = glassDockDrawable()
            setPadding(dp(8), dp(8), dp(8), dp(8))
            elevation = dp(12).toFloat()
            clipToPadding = false
            clipChildren = false
            contentDescription = "Favourite apps dock"
        }
        home.addView(dockContainer, LinearLayout.LayoutParams(-1, dp(76)))
        renderDock()
        updateClock()
    }

    private fun buildDrawerChrome() {
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply {
            text = "App Library"
            textSize = 28f
            letterSpacing = -0.025f
            typeface = androidx.core.content.res.ResourcesCompat.getFont(this@LauncherActivity, R.font.roboto_medium)
            setTextColor(getColor(R.color.glass_text))
        }
        val close = TextView(this).apply {
            text = "Done"
            textSize = 13f
            typeface = androidx.core.content.res.ResourcesCompat.getFont(this@LauncherActivity, R.font.roboto_medium)
            setTextColor(accentColor())
            gravity = Gravity.CENTER
            background = glassChipDrawable(18)
            isClickable = true
            isFocusable = true
            setOnClickListener { showHome() }
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(48), 1f)); header.addView(close, LinearLayout.LayoutParams(dp(56), dp(48))); drawer.addView(header)
        search = EditText(this).apply {
            tag = "glassline-search"
            hint = "Search apps"
            singleLine = true
            textSize = 15f
            setTextColor(getColor(R.color.glass_text))
            setHintTextColor(getColor(R.color.glass_muted))
            background = glassChipDrawable(19)
            setPadding(dp(16), 0, dp(16), 0)
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

        window.statusBarColor = Color.rgb(18, 19, 24)
        window.navigationBarColor = Color.rgb(18, 19, 24)

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = 0

        usageTracker.refreshFromDisk()
        buildInterface()
        loadApps()
    }

    private fun buildInterface() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(12))
            setBackgroundColor(Color.rgb(18, 19, 24))
        }

        val clock = TextView(this).apply {
            text = "Glassline"
            textSize = 30f
            typeface = Typeface.create(
                "sans-serif-medium",
                Typeface.NORMAL
            )
            setTextColor(Color.WHITE)
        }

        root.addView(
            clock,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val subtitle = TextView(this).apply {
            text = "Your space. Your apps."
            textSize = 14f
            setTextColor(Color.rgb(165, 168, 181))
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

        root.addView(
            subtitle,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(34)
            )
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

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val settingsButton = TextView(this).apply {
            text = "Settings"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(48, 50, 62), 18)
            setPadding(dp(16), dp(10), dp(16), dp(10))

            setOnClickListener {
                runCatching {
                    startActivity(
                        Intent(
                            this@LauncherActivity,
                            SettingsActivity::class.java
                        )
                    )
                }.onFailure {
                    Toast.makeText(
                        this@LauncherActivity,
                        "Could not open settings",
                        Toast.LENGTH_SHORT
                    ).show()
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
        }

        val refreshButton = TextView(this).apply {
            text = "Refresh"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(48, 50, 62), 18)
            setPadding(dp(16), dp(10), dp(16), dp(10))

            setOnClickListener {
                loadApps()
            }
        }

        actions.addView(
            settingsButton,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        actions.addView(
            View(this),
            LinearLayout.LayoutParams(dp(10), 1)
        )

        actions.addView(
            refreshButton,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        root.addView(
            actions,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(18)
            }
        )

        searchField = EditText(this).apply {
            hint = "Search apps"
            textSize = 16f
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(155, 158, 172))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = rounded(Color.rgb(38, 40, 49), 20)
        }

        root.addView(
            searchField,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                bottomMargin = dp(12)
            }
        )

        statusText = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(165, 168, 181))
        }

        root.addView(
            statusText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(28)
            )
        )

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        appGrid = GridLayout(this).apply {
            columnCount = columns
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }

        scroll.addView(
            appGrid,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)

        searchField.addTextChangedListener(
            SimpleTextWatcher {
                renderApps(it.toString())
            }
        )
    }

    private fun loadApps() {
        columns = prefs.getInt("columns", 4).coerceIn(3, 6)
        showLabels = prefs.getBoolean("labels", true)

        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val results = packageManager.queryIntentActivities(
            launcherIntent,
            0
        )

        apps.clear()

        val hiddenApps = prefs.getStringSet(
            "hidden_apps",
            emptySet()
        ) ?: emptySet()

        for (resolveInfo in results) {
            val info = resolveInfo.activityInfo ?: continue
            if (info.packageName in hiddenApps) continue

            val label = resolveInfo.loadLabel(packageManager)
                ?.toString()
                ?.takeIf { it.isNotBlank() }
                ?: info.packageName

            apps.add(
                LaunchableApp(
                    packageName = info.packageName,
                    activityName = info.name,
                    label = label,
                    resolveInfo = resolveInfo
                )
            )
        }

        apps.sortBy { it.label.lowercase() }

        renderApps(searchField.text?.toString().orEmpty())
    }

    private fun renderApps(rawQuery: String) {
        if (!::appGrid.isInitialized) return

        val query = rawQuery.trim().lowercase()

        val visibleApps = apps.filter { app ->
            app.label.lowercase().contains(query) ||
                app.packageName.lowercase().contains(query)
        }

        appGrid.removeAllViews()
        appGrid.columnCount = columns

        statusText.text = when {
            visibleApps.isEmpty() -> "No matching apps"
            query.isBlank() -> "${visibleApps.size} apps"
            else -> "${visibleApps.size} results"
        }

        visibleApps.forEach { app ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(8), dp(4), dp(8))
                isClickable = true
                isFocusable = true
                background = selectableBackground()
                contentDescription = "Open ${app.label}"

                setOnClickListener {
                    launchApp(app)
                }
            }

            val icon = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageDrawable(
                    runCatching {
                        app.resolveInfo?.loadIcon(packageManager)
                    }.getOrNull()
                )
                contentDescription = app.label
            }

            cell.addView(
                icon,
                LinearLayout.LayoutParams(dp(52), dp(52))
            )

            if (showLabels) {
                val label = TextView(this).apply {
                    text = app.label
                    textSize = 11f
                    gravity = Gravity.CENTER
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(Color.WHITE)
                    setPadding(dp(2), dp(5), dp(2), 0)
                }

                cell.addView(
                    label,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(38)
                    )
                )
            }

            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(
                    GridLayout.UNDEFINED,
                    1f
                )
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }

            appGrid.addView(cell, params)
        }
    }

    private fun launchApp(app: LaunchableApp) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(app.packageName, app.activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            startActivity(intent)
            usageTracker.record(app)
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Could not open ${app.label}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun rounded(
        color: Int,
        radiusDp: Int
    ) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun selectableBackground(): android.graphics.drawable.Drawable {
        val attributes = intArrayOf(
            android.R.attr.selectableItemBackground
        )
        val array = obtainStyledAttributes(attributes)
        val drawable = array.getDrawable(0)
        array.recycle()
        return drawable ?: rounded(Color.TRANSPARENT, 12)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}
