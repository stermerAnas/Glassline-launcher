package com.glassline.launcher

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun sampleWallpaperTint(context:Context):Int = withContext(Dispatchers.IO) {
    val drawable=WallpaperManager.getInstance(context).drawable
        ?: return@withContext Color.rgb(235,235,242)
    val bitmap=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888)
    val canvas=Canvas(bitmap)
    drawable.setBounds(0,0,1,1)
    drawable.draw(canvas)
    val color=bitmap.getPixel(0,0)
    bitmap.recycle()
    color
}
