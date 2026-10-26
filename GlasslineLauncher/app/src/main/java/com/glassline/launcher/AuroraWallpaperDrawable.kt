package com.glassline.launcher

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.max

/**
 * Low-cost, offline wallpaper for Glassline's default appearance.
 * Shaders are rebuilt only when the drawable bounds change: there is no
 * animation loop, image download, or per-draw shader allocation.
 */
class AuroraWallpaperDrawable(context: Context) : Drawable() {
    private val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var drawableAlpha = 255
    private var colorFilter: ColorFilter? = null
    private var baseShader: Shader? = null
    private var glowShaders: List<Shader> = emptyList()

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val left = bounds.left.toFloat()
        val top = bounds.top.toFloat()
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) {
            baseShader = null
            glowShaders = emptyList()
            return
        }

        baseShader = if (dark) {
            LinearGradient(
                left, top, left + w, top + h,
                intArrayOf(Color.rgb(16, 20, 32), Color.rgb(27, 28, 48), Color.rgb(17, 27, 43)),
                floatArrayOf(0f, 0.54f, 1f), Shader.TileMode.CLAMP
            )
        } else {
            LinearGradient(
                left, top, left + w, top + h,
                intArrayOf(Color.rgb(249, 249, 255), Color.rgb(237, 241, 255), Color.rgb(229, 239, 250)),
                floatArrayOf(0f, 0.56f, 1f), Shader.TileMode.CLAMP
            )
        }

        val radius = max(w, h)
        val glows = if (dark) {
            listOf(
                Glow(left + w * 0.84f, top + h * 0.12f, radius * 0.58f, 0x553C58C8, 0x003C58C8),
                Glow(left + w * 0.08f, top + h * 0.52f, radius * 0.64f, 0x554F2D82, 0x004F2D82),
                Glow(left + w * 0.78f, top + h * 0.91f, radius * 0.55f, 0x44316E83, 0x00316E83)
            )
        } else {
            listOf(
                Glow(left + w * 0.88f, top + h * 0.12f, radius * 0.58f, 0x99C8D2FF.toInt(), 0x00C8D2FF),
                Glow(left + w * 0.06f, top + h * 0.47f, radius * 0.66f, 0x77E6D5F5, 0x00E6D5F5),
                Glow(left + w * 0.79f, top + h * 0.88f, radius * 0.57f, 0x77BDE8F2, 0x00BDE8F2),
                Glow(left + w * 0.45f, top + h * 0.98f, radius * 0.42f, 0x55D5DFFF, 0x00D5DFFF)
            )
        }
        glowShaders = glows.map { glow ->
            RadialGradient(
                glow.x, glow.y, glow.radius,
                intArrayOf(glow.centerColor, glow.edgeColor),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        basePaint.alpha = drawableAlpha
        basePaint.colorFilter = colorFilter
        basePaint.shader = baseShader
        canvas.drawRect(b, basePaint)
        glowPaint.alpha = drawableAlpha
        glowPaint.colorFilter = colorFilter
        glowShaders.forEach { shader ->
            glowPaint.shader = shader
            canvas.drawRect(b, glowPaint)
        }
        basePaint.shader = null
        glowPaint.shader = null
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(filter: ColorFilter?) {
        colorFilter = filter
        invalidateSelf()
    }

    @Deprecated("Deprecated by Android in favour of PixelFormat constants for opaque drawables")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private data class Glow(
        val x: Float,
        val y: Float,
        val radius: Float,
        val centerColor: Int,
        val edgeColor: Int
    )
}
