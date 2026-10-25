package com.glassline.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class LauncherApp(val label: String, val packageName: String, val activityName: String)

class AppRepository(context: Context) {
    private val appContext = context.applicationContext
    private val pm = appContext.packageManager
    private val iconCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory()/1024L/8L).coerceAtMost(16*1024L).toInt()
    ) {
        override fun sizeOf(key:String, value:Bitmap) = value.byteCount/1024
    }

    suspend fun loadApps(): List<LauncherApp> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val results = pm.queryIntentActivities(intent, 0)
        results.map {
            LauncherApp(it.loadLabel(pm).toString(),it.activityInfo.packageName,it.activityInfo.name)
        }.distinctBy { it.packageName to it.activityName }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
    }

    suspend fun icon(app:LauncherApp,sizePx:Int):Bitmap? = withContext(Dispatchers.IO) {
        val key="${app.packageName}/${app.activityName}@$sizePx"
        synchronized(iconCache) { iconCache.get(key) }?.let { return@withContext it }
        val drawable = try {
            pm.getActivityInfo(ComponentName(app.packageName,app.activityName),0).loadIcon(pm)
        } catch (_: PackageManager.NameNotFoundException) { return@withContext null }
          catch (_: SecurityException) { return@withContext null }
        val bitmap = drawableToBitmap(drawable,sizePx)
        synchronized(iconCache) { iconCache.put(key,bitmap) }
        bitmap
    }

    private fun drawableToBitmap(drawable:Drawable,size:Int):Bitmap {
        if(drawable is BitmapDrawable) {
            val source=drawable.bitmap
            if(source.width==size && source.height==size && source.config==Bitmap.Config.ARGB_8888) return source
        }
        val bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        val old=android.graphics.Rect(drawable.bounds)
        drawable.setBounds(0,0,size,size)
        drawable.draw(canvas)
        drawable.bounds=old
        return bitmap
    }
    fun clear() = iconCache.evictAll()
}
