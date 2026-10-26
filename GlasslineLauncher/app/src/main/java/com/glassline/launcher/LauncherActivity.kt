package com.glassline.launcher

import androidx.activity.ComponentActivity
import android.graphics.Color
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

    private sealed interface DrawerEntry {
        data class Header(val letter: String) : DrawerEntry
        data class App(val value: LaunchableApp) : DrawerEntry
        data class Empty(val query: String) : DrawerEntry
    }

    inner class AppAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var all: List<LaunchableApp> = emptyList()
        private var shown: List<DrawerEntry> = emptyList()
        private val hidden = LauncherLogic.readHiddenApps(prefs)
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

internal object LauncherLogic {
    fun normalize(value: String): String = java.text.Normalizer.normalize(java.text.Normalizer.normalize(value.trim(), java.text.Normalizer.Form.NFC), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    fun parseColumns(value: Int): Int = value.coerceIn(3, 6)
    fun readHiddenApps(prefs: android.content.SharedPreferences): MutableSet<String> = prefs.getStringSet("hidden_apps", emptySet())?.toMutableSet() ?: mutableSetOf()
    fun saveHiddenApps(prefs: android.content.SharedPreferences, hidden: Set<String>) { prefs.edit().putStringSet("hidden_apps", hidden.toSet()).apply() }
    fun matchesQuery(label: String, packageName: String, normalizedQuery: String): Boolean = normalizedQuery.isEmpty() || normalize(label).contains(normalizedQuery) || normalize(packageName).contains(normalizedQuery)
}
