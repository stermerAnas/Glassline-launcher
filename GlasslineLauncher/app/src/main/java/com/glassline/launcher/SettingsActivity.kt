package com.glassline.launcher

import android.app.role.RoleManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("glassline", MODE_PRIVATE) }
    private val workspaceStore by lazy { WorkspaceStore(this) }
    private val exportWorkspace = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == RESULT_OK && uri != null) exportTo(uri)
    }
    private val importWorkspace = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == RESULT_OK && uri != null) importFrom(uri)
    }
    private val enableAdmin = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val manager = getSystemService(DevicePolicyManager::class.java)
        val component = ComponentName(this, GlasslineDeviceAdminReceiver::class.java)
        val active = manager.isAdminActive(component)
        prefs.edit().putBoolean("double_tap_lock", active).apply()
        Toast.makeText(this, if (active) "Double-tap lock enabled" else "Device-admin permission was not granted", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(AccentThemes.styleFor(getSharedPreferences("glassline", MODE_PRIVATE).getInt("accent_index", 0)))
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24.dp, 32.dp, 24.dp, 24.dp); setBackgroundColor(getColor(R.color.glass_bg)) }
        fun heading(text: String) { root.addView(TextView(this).apply { this.text = text; textSize = 25f; setTextColor(getColor(R.color.glass_text)); setPadding(0, 0, 0, 12.dp) }) }
        heading("Glassline settings")
        val makeSwitch: (String, String, Boolean) -> Unit = { label, key, default -> root.addView(Switch(this).apply { text = label; textSize = 15f; setTextColor(getColor(R.color.glass_text)); isChecked = prefs.getBoolean(key, default); setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean(key, checked).apply() }; setPadding(0, 8.dp, 0, 8.dp) }) }
        makeSwitch("Show app labels", "labels", true)
        makeSwitch("Use system wallpaper", "show_wallpaper", false)
        heading("Accent color")
        val accents = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        AccentThemes.names.forEachIndexed { index, name -> accents.addView(RadioButton(this).apply { id = 100 + index; text = name; setTextColor(accentColor()); isChecked = prefs.getInt("accent_index", 0) == index }) }
        accents.setOnCheckedChangeListener { _, checkedId -> val index = checkedId - 100; if (index in 0..5 && index != prefs.getInt("accent_index", 0)) { prefs.edit().putInt("accent_index", index).apply(); recreate() } }
        root.addView(accents)
        heading("Gestures")
        val swipeMode = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val searchMode = RadioButton(this).apply { text = "Swipe down opens search"; setTextColor(getColor(R.color.glass_text)); id = 1 }
        val notificationMode = RadioButton(this).apply { text = "Swipe down opens notification shade (accessibility required)"; setTextColor(getColor(R.color.glass_text)); id = 2 }
        swipeMode.addView(searchMode); swipeMode.addView(notificationMode)
        swipeMode.check(if (prefs.getString("home_swipe_down", "search") == "notifications") 2 else 1)
        swipeMode.setOnCheckedChangeListener { _, checkedId -> prefs.edit().putString("home_swipe_down", if (checkedId == 2) "notifications" else "search").apply() }
        root.addView(swipeMode)
        root.addView(Button(this).apply { text = "Enable notification-shade gesture"; setOnClickListener { android.app.AlertDialog.Builder(this@SettingsActivity).setTitle("Allow notification access gesture?").setMessage("Glassline will use an explicitly enabled accessibility service only to request Android's notification-shade global action. It does not read screen content. You can disable the service at any time in Android Settings.").setNegativeButton("Cancel", null).setPositiveButton("Open accessibility settings") { _, _ -> runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }.show() } })
        root.addView(Button(this).apply { text = "Enable double-tap device lock"; setOnClickListener { val component = ComponentName(this@SettingsActivity, GlasslineDeviceAdminReceiver::class.java); val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component).putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Allows Glassline to lock this device when you double-tap the home screen. This is optional and can be disabled in Android Settings."); enableAdmin.launch(intent) } })
        root.addView(Button(this).apply { text = "Disable double-tap device lock"; setOnClickListener { android.app.AlertDialog.Builder(this@SettingsActivity).setTitle("Disable device lock?").setMessage("Glassline will remove its device-admin privilege and double-tap lock will stop working.").setNegativeButton("Cancel", null).setPositiveButton("Disable") { _, _ -> val manager = getSystemService(DevicePolicyManager::class.java); manager.removeActiveAdmin(ComponentName(this@SettingsActivity, GlasslineDeviceAdminReceiver::class.java)); prefs.edit().putBoolean("double_tap_lock", false).putString("double_tap_action", "none").apply() }.show() } })
        root.addView(Button(this).apply { text = "Double-tap action"; setOnClickListener { showDoubleTapActionDialog() } })
        root.addView(Button(this).apply { text = "Choose icon pack"; setOnClickListener { chooseIconPack() } })
        root.addView(Button(this).apply { text = "Choose static wallpaper"; setOnClickListener { runCatching { startActivity(Intent(android.app.WallpaperManager.ACTION_SET_WALLPAPER)) } } })
        root.addView(Button(this).apply { text = "Choose live wallpaper"; setOnClickListener { chooseLiveWallpaper() } })
        heading("Home grid columns")
        val values = listOf(3, 4, 5, 6)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        values.forEach { count -> row.addView(Button(this).apply { text = count.toString(); isSelected = prefs.getInt("columns", 4) == count; setOnClickListener { prefs.edit().putInt("columns", count).apply() } }, LinearLayout.LayoutParams(0, 48.dp, 1f)) }
        root.addView(row)
        heading("Icon size")
        val seek = SeekBar(this).apply { max = 32; progress = prefs.getInt("icon_size", 52).coerceIn(36, 68) - 36; setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) { if (user) prefs.edit().putInt("icon_size", 36 + p).apply() }; override fun onStartTrackingTouch(s: SeekBar?) = Unit; override fun onStopTrackingTouch(s: SeekBar?) = Unit }) }
        root.addView(seek)
        root.addView(Button(this).apply { text = "Unhide all apps"; setOnClickListener { prefs.edit().putStringSet("hidden_apps", emptySet()).apply(); Toast.makeText(this@SettingsActivity, "All apps are visible", Toast.LENGTH_SHORT).show() } })
        root.addView(Button(this).apply { text = "Export workspace backup"; setOnClickListener { val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"; putExtra(Intent.EXTRA_TITLE, "glassline-workspace.json") }; exportWorkspace.launch(intent) } })
        root.addView(Button(this).apply { text = "Restore workspace backup"; setOnClickListener { val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/json" }; importWorkspace.launch(intent) } })
        root.addView(Button(this).apply { text = "Choose default home app"; setOnClickListener { if (Build.VERSION.SDK_INT >= 29) { val rm = getSystemService(RoleManager::class.java); if (rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) startActivity(rm.createRequestRoleIntent(RoleManager.ROLE_HOME)) else startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } else startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } })
        setContentView(root)
    }

    private fun showDoubleTapActionDialog() {
        val actions = arrayOf("Do nothing", "Open app search", "Lock device")
        val current = prefs.getString("double_tap_action", if (prefs.getBoolean("double_tap_lock", false)) "lock" else "none")
        val selected = when (current) { "search" -> 1; "lock" -> 2; else -> 0 }
        android.app.AlertDialog.Builder(this)
            .setTitle("Double-tap action")
            .setSingleChoiceItems(actions, selected) { dialog, which ->
                when (which) {
                    0 -> prefs.edit().putString("double_tap_action", "none").putBoolean("double_tap_lock", false).apply()
                    1 -> prefs.edit().putString("double_tap_action", "search").putBoolean("double_tap_lock", false).apply()
                    2 -> {
                        val manager = getSystemService(DevicePolicyManager::class.java)
                        val component = ComponentName(this, GlasslineDeviceAdminReceiver::class.java)
                        if (manager.isAdminActive(component)) {
                            prefs.edit().putString("double_tap_action", "lock").putBoolean("double_tap_lock", true).apply()
                        } else {
                            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Android requires your explicit approval before Glassline can lock this device.")
                            enableAdmin.launch(intent)
                        }
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun chooseLiveWallpaper() {
        val chooser = Intent(android.app.WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
        runCatching { startActivity(chooser) }.onFailure {
            val current = getSystemService(android.app.WallpaperManager::class.java).wallpaperInfo
            val settingsIntent = current?.let {
                Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    .putExtra(android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, it.component)
            }
            val opened = settingsIntent?.let { intent -> runCatching { startActivity(intent); true }.getOrDefault(false) } ?: false
            if (!opened) Toast.makeText(this, "Live wallpaper selection is unavailable on this device.", Toast.LENGTH_LONG).show()
        }
    }

    private fun chooseIconPack() {
        val packs = IconPackManager(this).discover()
        val labels = listOf("System app icons") + packs.map { it.label }
        android.app.AlertDialog.Builder(this).setTitle("Icon pack").setSingleChoiceItems(labels.toTypedArray(), packs.indexOfFirst { it.packageName == prefs.getString("icon_pack_package", null) } + 1) { dialog, which ->
            if (which == 0) prefs.edit().remove("icon_pack_package").apply()
            else prefs.edit().putString("icon_pack_package", packs[which - 1].packageName).apply()
            dialog.dismiss()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun accentColor(): Int {
        val value = android.util.TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, value, true)
        return if (value.resourceId != 0) getColor(value.resourceId) else value.data
    }

    private fun exportTo(uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { val json = WorkspaceJson.encode(workspaceStore.load()); contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(json) } ?: error("Could not open destination") } }
            result.onSuccess { Toast.makeText(this@SettingsActivity, "Workspace backup exported", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(this@SettingsActivity, "Backup export failed: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private fun importFrom(uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { val json = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: error("Could not open backup"); workspaceStore.replaceFromBackup(json) } }
            result.onSuccess { Toast.makeText(this@SettingsActivity, "Workspace restored. Return to Home to apply it.", Toast.LENGTH_LONG).show() }
                .onFailure { Toast.makeText(this@SettingsActivity, "Workspace restore failed: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()
}
