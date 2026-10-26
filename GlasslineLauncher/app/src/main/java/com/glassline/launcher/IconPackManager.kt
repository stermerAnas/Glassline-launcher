package com.glassline.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.util.LruCache
import java.util.concurrent.ConcurrentHashMap
import org.xmlpull.v1.XmlPullParser

/** ADW/GO/Nova-compatible appfilter.xml icon-pack reader with adaptive-icon fallback. */
class IconPackManager(private val context: Context) {
    private val packageManager = context.packageManager
    private val drawableCache = LruCache<String, Drawable>(128)
    private val filterCache = ConcurrentHashMap<String, Map<String, String>>()

    data class IconPack(val packageName: String, val label: String)
    data class PackIcon(val drawableName: String, val label: String)

    fun discover(): List<IconPack> {
        val actions = listOf("com.glassline.launcher.action.ICON_PACK", "org.adw.launcher.THEMES", "com.gradle.theme.ICONS", "com.novalauncher.THEME", "com.gau.go.launcherex.theme")
        val found = LinkedHashMap<String, IconPack>()
        actions.forEach { action ->
            val intent = Intent(action)
            queryActivities(intent).forEach resultLoop@ { result ->
                val info = result.activityInfo ?: return@resultLoop
                found[info.packageName] = IconPack(info.packageName, runCatching { result.loadLabel(packageManager).toString() }.getOrDefault(info.packageName))
            }
        }
        return found.values.sortedBy { it.label.lowercase(java.util.Locale.ROOT) }
    }

    fun resolveIcon(packPackage: String?, packageName: String, activityName: String, fallback: Drawable?): Drawable? {
        val component = ComponentName(packageName, activityName)
        val override = context.getSharedPreferences("glassline", Context.MODE_PRIVATE).getString("icon_override:${component.flattenToString()}", null)
        if (!override.isNullOrBlank()) {
            val pieces = override.split('|', limit = 2)
            if (pieces.size == 2) drawableCache.get("${pieces[0]}|${pieces[1]}")?.let { return it }
        }
        if (packPackage.isNullOrBlank()) return fallback
        val key = component.flattenToString()
        val mapping = filterCache[packPackage] ?: return fallback
        val drawableName = mapping["ComponentInfo{$key}"] ?: mapping[key]
        return drawableName?.let { drawableCache.get("$packPackage|$it") } ?: fallback
    }

    fun preload(packPackage: String, components: List<ComponentName> = emptyList()) {
        val mapping = appFilter(packPackage)
        components.forEach { component ->
            val key = component.flattenToString()
            (mapping["ComponentInfo{$key}"] ?: mapping[key])?.let { loadDrawable(packPackage, it) }
        }
    }

    fun preloadOverrides(components: List<ComponentName>) {
        val preferences = context.getSharedPreferences("glassline", Context.MODE_PRIVATE)
        components.forEach { component ->
            val override = preferences.getString("icon_override:${component.flattenToString()}", null) ?: return@forEach
            val pieces = override.split('|', limit = 2)
            if (pieces.size == 2) loadDrawable(pieces[0], pieces[1])
        }
    }

    fun icons(packPackage: String): List<PackIcon> = appFilter(packPackage).values.distinct().sorted().map { PackIcon(it, it.replace('_', ' ')) }

    fun setOverride(packageName: String, activityName: String, packPackage: String, drawableName: String) {
        val key = ComponentName(packageName, activityName).flattenToString()
        context.getSharedPreferences("glassline", Context.MODE_PRIVATE).edit().putString("icon_override:$key", "$packPackage|$drawableName").apply()
    }

    fun clearCache() { drawableCache.evictAll(); filterCache.clear() }

    private fun loadDrawable(packPackage: String, name: String): Drawable? {
        val key = "$packPackage|$name"
        drawableCache.get(key)?.let { return it }
        val drawable = runCatching {
            val resources = packageManager.getResourcesForApplication(packPackage)
            val id = resources.getIdentifier(name, "drawable", packPackage)
            if (id == 0) null else resources.getDrawable(id, context.theme)
        }.getOrNull()
        if (drawable != null) drawableCache.put(key, drawable)
        return drawable
    }

    private fun appFilter(packPackage: String): Map<String, String> = filterCache.getOrPut(packPackage) {
        runCatching {
            val resources = packageManager.getResourcesForApplication(packPackage)
            val id = resources.getIdentifier("appfilter", "xml", packPackage)
            if (id == 0) return@runCatching emptyMap()
            val parser = resources.getXml(id)
            val mapping = LinkedHashMap<String, String>()
            try {
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name == "item") {
                        val component = parser.getAttributeValue(null, "component")
                        val drawable = parser.getAttributeValue(null, "drawable")
                        if (!component.isNullOrBlank() && !drawable.isNullOrBlank()) mapping[component] = drawable
                    }
                    event = parser.next()
                }
            } finally { parser.close() }
            mapping
        }.getOrDefault(emptyMap())
    }

    private fun queryActivities(intent: Intent): List<ResolveInfo> = if (android.os.Build.VERSION.SDK_INT >= 33) {
        packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
    } else {
        queryActivitiesLegacy(intent)
    }

    @Suppress("DEPRECATION")
    private fun queryActivitiesLegacy(intent: Intent): List<ResolveInfo> = packageManager.queryIntentActivities(intent, 0)
}
