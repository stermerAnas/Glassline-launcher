package com.glassline.launcher

import androidx.activity.ComponentActivity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class LauncherActivity : ComponentActivity() {
    private lateinit var home: HomeScreenLayout
    private lateinit var repository: AppRepository
    private lateinit var drawer: AppGridView

    private var wallpaperTint = Color.rgb(235, 235, 242)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        repository = AppRepository(this)

        home = HomeScreenLayout(this)
        home.setBackgroundColor(Color.rgb(19, 20, 25))
        setContentView(home)

        val widgets = CleanWidgetStack(this)
        widgets.refreshBattery()

        home.addView(
            widgets,
            FrameLayout.LayoutParams(
                dp(300f),
                dp(210f),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply {
                topMargin = dp(90f)
            }
        )

        val folderIcon = FolderIconView(this, "Utilities") { icon ->
            val folder = ExpandedFolderView(
                this,
                "Utilities",
                listOf("Settings", "Clock", "Calculator", "Files"),
                wallpaperTint
            )

            home.addView(
                folder,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )

            folder.openFrom(icon)
        }

        home.addView(
            folderIcon,
            FrameLayout.LayoutParams(
                dp(68f),
                dp(68f),
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = dp(130f)
            }
        )

        drawer = AppGridView(this, repository).apply {
            visibility = View.GONE
            setBackgroundColor(Color.rgb(19, 20, 25))
        }

        home.addView(
            drawer,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val drawerButton = TextView(this).apply {
            text = "All apps  ↑"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)

            background = roundedBackground(
                Color.argb(45, 255, 255, 255),
                dp(24f).toFloat()
            )

            setOnClickListener {
                if (drawer.visibility == View.VISIBLE) {
                    drawer.visibility = View.GONE
                } else {
                    drawer.visibility = View.VISIBLE
                    drawer.bringToFront()
                    drawerButtonBringToFront(this)
                    drawer.refresh()
                }
            }
        }

        home.addView(
            drawerButton,
            FrameLayout.LayoutParams(
                dp(160f),
                dp(48f),
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = dp(35f)
            }
        )

        drawerButton.bringToFront()

        lifecycleScope.launch {
            wallpaperTint = sampleWallpaperTint(this@LauncherActivity)
        }
    }

    private fun drawerButtonBringToFront(view: View) {
        view.bringToFront()
    }

    private fun dp(value: Float): Int {
        return (value * resources.displayMetrics.density + 0.5f).toInt()
    }

    override fun onResume() {
        super.onResume()

        if (::drawer.isInitialized) {
            drawer.refresh()
        }
    }

    override fun onDestroy() {
        if (::drawer.isInitialized) {
            drawer.dispose()
        }

        if (::repository.isInitialized) {
            repository.clear()
        }

        super.onDestroy()
    }
}
