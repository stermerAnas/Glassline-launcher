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
import android.widget.FrameLayout
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
        val resolveInfo: ResolveInfo
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
        super.onCreate(savedInstanceState)

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
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
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
        root.addView(
            subtitle,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(34)
            )
        )

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val settingsButton = TextView(this).apply {
            text = "Settings"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(
                Color.rgb(48, 50, 62),
                18
            )
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
            }
        }

        val refreshButton = TextView(this).apply {
            text = "Refresh"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(
                Color.rgb(48, 50, 62),
                18
            )
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
            singleLine = true
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(155, 158, 172))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = rounded(
                Color.rgb(38, 40, 49),
                20
            )
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
            ScrollView.LayoutParams(
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
                        app.resolveInfo.loadIcon(packageManager)
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

    private fun rounded(color: Int, radiusDp: Int) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun selectableBackground(): android.graphics.drawable.Drawable {
        val attributes = intArrayOf(android.R.attr.selectableItemBackground)
        val array = obtainStyledAttributes(attributes)
        val drawable = array.getDrawable(0)
        array.recycle()
        return drawable ?: rounded(Color.TRANSPARENT, 12)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}
