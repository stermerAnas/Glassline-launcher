package com.glassline.launcher

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.BatteryManager
import android.view.MotionEvent
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CleanWidgetStack(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private var battery = 100
    private var current = suggestedWidget()
    private val names = arrayOf("Clock", "Battery", "Calendar", "Music")

    init {
        background = roundedBackground(Color.rgb(245, 245, 248), 28f * density)
        clipToOutline = true
        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        isClickable = true
    }

    private fun suggestedWidget(): Int {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> 2
            in 17..23 -> 3
            else -> 0
        }
    }

    fun refreshTimeAwareSelection() {
        current = suggestedWidget()
        invalidate()
    }

    fun refreshBattery() {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        if (level >= 0 && scale > 0) battery = level * 100 / scale
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = 24f * density
        p.color = Color.rgb(31, 32, 38)
        p.style = Paint.Style.FILL
        p.textSize = 14f * density
        p.typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
        canvas.drawText(names[current], pad, pad + 4 * density, p)
        when (current) {
            0 -> {
                p.textSize = 42f * density
                p.typeface = android.graphics.Typeface.create("sans-serif-light", 0)
                canvas.drawText(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), pad, h * .57f, p)
                p.textSize = 14f * density
                canvas.drawText(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()), pad, h * .75f, p)
                postInvalidateDelayed(15_000)
            }
            1 -> {
                val radius = minOf(w, h) * .20f
                val cx = w * .5f
                val cy = h * .56f
                p.style = Paint.Style.STROKE
                p.strokeWidth = 8f * density
                p.strokeCap = Paint.Cap.ROUND
                p.color = Color.rgb(218, 220, 225)
                canvas.drawCircle(cx, cy, radius, p)
                p.color = Color.rgb(50, 130, 240)
                canvas.drawArc(cx-radius, cy-radius, cx+radius, cy+radius, -90f, battery*3.6f, false, p)
                p.style = Paint.Style.FILL
                p.textSize = 26f * density
                p.textAlign = Paint.Align.CENTER
                canvas.drawText("$battery%", cx, cy + 9f * density, p)
                p.textAlign = Paint.Align.LEFT
            }
            2 -> {
                p.textSize = 19f * density
                canvas.drawText(SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date()), pad, h*.34f, p)
                p.textSize = 15f * density
                canvas.drawText("Today · ${java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)}", pad, h*.54f, p)
                p.textSize = 12f * density
                canvas.drawText("No events displayed", pad, h*.69f, p)
            }
            3 -> {
                p.textSize = 24f * density
                canvas.drawText("Music", pad, h*.48f, p)
                p.textSize = 14f * density
                p.color = Color.rgb(100, 101, 110)
                canvas.drawText("Open your music app", pad, h*.63f, p)
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            if (current == 3) openMusicApp()
            else if (e.y < height * .8f) {
                current = (current + 1) % 4
                invalidate()
            }
            return true
        }
        return true
    }

    private fun openMusicApp() {
        val intent = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
            ?: context.packageManager.getLaunchIntentForPackage("com.sec.android.app.music")
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent != null) context.startActivity(intent)
    }
}
