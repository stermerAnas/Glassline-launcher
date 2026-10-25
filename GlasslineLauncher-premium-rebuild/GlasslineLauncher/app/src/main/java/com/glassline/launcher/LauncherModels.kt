package com.glassline.launcher

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import android.view.View
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import java.text.Collator
import java.util.Locale
import android.util.LruCache

internal data class LauncherApp(
    val label: String,
    val packageName: String,
    val activityName: String,
) {
    val key: String get() = "$packageName:$activityName"
}

internal data class LauncherPalette(
    val background: Color,
    val surface: Color,
    val elevated: Color,
    val iconSurface: Color,
    val accent: Color,
    val accentSoft: Color,
    val text: Color,
    val muted: Color,
    val border: Color,
    val glow: Color,
)

internal fun paletteFor(dark: Boolean) = if (dark) {
    LauncherPalette(
        background = Color(0xFF080B10),
        surface = Color(0xFF111721),
        elevated = Color(0xFF18212D),
        iconSurface = Color(0xFF202A38),
        accent = Color(0xFFBAC8FF),
        accentSoft = Color(0xFF293650),
        text = Color(0xFFF0F3FA),
        muted = Color(0xFF9DA8BA),
        border = Color(0xFF273241),
        glow = Color(0x334C72D7),
    )
} else {
    LauncherPalette(
        background = Color(0xFFF4F6FA),
        surface = Color(0xFFFFFFFF),
        elevated = Color(0xFFE9EDF5),
        iconSurface = Color(0xFFE3E8F2),
        accent = Color(0xFF344F93),
        accentSoft = Color(0xFFDDE5FF),
        text = Color(0xFF171B24),
        muted = Color(0xFF626D7E),
        border = Color(0xFFD8DFEA),
        glow = Color(0x335C7DC8),
    )
}

internal fun loadLaunchableApps(context: Context): List<LauncherApp> {
    val packageManager = context.packageManager
    val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolved = try {
        packageManager.queryIntentActivities(launchIntent, 0)
    } catch (_: Exception) {
        emptyList<ResolveInfo>()
    }
    val seen = HashSet<String>()
    val result = ArrayList<LauncherApp>(resolved.size)
    for (info in resolved) {
        val activityInfo = info.activityInfo ?: continue
        val pkg = activityInfo.packageName ?: continue
        val activityName = activityInfo.name ?: continue
        val key = "$pkg:$activityName"
        if (!seen.add(key)) continue
        val label = runCatching { info.loadLabel(packageManager)?.toString()?.trim().orEmpty() }
            .getOrDefault("")
            .ifBlank { activityInfo.applicationInfo?.loadLabel(packageManager)?.toString().orEmpty() }
            .ifBlank { pkg.substringAfterLast('.') }
        // Keep discovery cheap: resolve labels now, and fetch icons only for visible tiles.
        result += LauncherApp(label = label, packageName = pkg, activityName = activityName)
    }
    val collator = Collator.getInstance(Locale.getDefault())
    return result.sortedWith { first, second ->
        val nameComparison = collator.compare(first.label, second.label)
        if (nameComparison != 0) nameComparison else first.packageName.compareTo(second.packageName)
    }
}

internal fun defaultPinnedApps(apps: List<LauncherApp>): List<String> {
    if (apps.isEmpty()) return emptyList()
    val selected = LinkedHashSet<String>()
    val preferences = listOf(
        listOf("phone", "dialer", "calls"),
        listOf("messages", "messaging", "whatsapp", "telegram"),
        listOf("camera"),
        listOf("chrome", "browser", "internet", "firefox", "edge"),
    )
    for (keywords in preferences) {
        val match = apps.firstOrNull { app ->
            app.packageName !in selected && keywords.any { keyword ->
                app.label.contains(keyword, ignoreCase = true) || app.packageName.contains(keyword, ignoreCase = true)
            }
        }
        if (match != null) selected.add(match.packageName)
    }
    for (app in apps) {
        if (selected.size >= 4) break
        selected.add(app.packageName)
    }
    return selected.toList()
}

/** Small process-local icon cache. Work is performed by callers on Dispatchers.IO. */
internal object LauncherIconCache {
    private const val ICON_SIZE = 144
    private val cache = object : LruCache<String, Bitmap>(12 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    fun load(context: Context, app: LauncherApp): Bitmap {
        synchronized(cache) { cache.get(app.key)?.let { return it } }
        val packageManager = context.packageManager
        val icon = runCatching {
            packageManager.getActivityIcon(ComponentName(app.packageName, app.activityName))
                .toBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        }.getOrElse {
            runCatching {
                packageManager.getApplicationIcon(app.packageName)
                    .toBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
            }.getOrElse {
                runCatching {
                    packageManager.defaultActivityIcon.toBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
                }.getOrElse {
                    Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
                }
            }
        }
        synchronized(cache) {
            cache.get(app.key)?.let { return it }
            cache.put(app.key, icon)
        }
        return icon
    }

    fun invalidate(packageName: String?) {
        synchronized(cache) {
            if (packageName.isNullOrBlank()) {
                cache.evictAll()
            } else {
                cache.snapshot().keys.filter { it.startsWith("$packageName:") }.forEach { cache.remove(it) }
            }
        }
    }
}

internal fun launchApp(
    context: Context,
    app: LauncherApp,
    sourceView: View? = null,
    sourceBounds: Rect? = null,
) {
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setClassName(app.packageName, app.activityName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val bounds = sourceBounds
    val options = if (sourceView != null && bounds != null && bounds.width() > 0 && bounds.height() > 0) {
        runCatching {
            ActivityOptions.makeScaleUpAnimation(
                sourceView,
                bounds.left,
                bounds.top,
                bounds.width(),
                bounds.height(),
            ).toBundle()
        }.getOrNull()
    } else null

    val launched = runCatching {
        if (options != null) context.startActivity(intent, options) else context.startActivity(intent)
    }.isSuccess
    if (!launched) {
        // A broken/unsupported launch animation must never prevent the actual app from opening.
        runCatching { context.startActivity(intent) }
            .onFailure { Toast.makeText(context, "Could not open ${app.label}", Toast.LENGTH_SHORT).show() }
    }
}

internal fun openAppInfo(context: Context, app: LauncherApp) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", app.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "App info is unavailable", Toast.LENGTH_SHORT).show() }
}
