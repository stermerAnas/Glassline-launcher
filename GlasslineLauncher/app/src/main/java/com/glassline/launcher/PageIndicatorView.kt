package com.glassline.launcher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import kotlin.math.max

/** Lightweight pill indicator. Only one short ValueAnimator runs after page selection. */
class PageIndicatorView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var count = 3
    private var selected = 0f
    private var animator: ValueAnimator? = null
    private val inactive = RectF()

    fun setPageCount(value: Int) {
        val next = value.coerceIn(1, 20)
        if (next == count) return
        count = next
        selected = selected.coerceIn(0f, (count - 1).toFloat())
        requestLayout(); invalidate()
    }

    fun setCurrentPage(page: Int, animate: Boolean) {
        val target = page.coerceIn(0, count - 1).toFloat()
        animator?.cancel()
        if (!animate || !motionEnabled()) { selected = target; invalidate(); return }
        animator = ValueAnimator.ofFloat(selected, target).apply {
            duration = 190L
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener { selected = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = dp(18 + max(0, count - 1) * 14 + 18)
        val desiredHeight = dp(24)
        setMeasuredDimension(resolveSize(desiredWidth, widthMeasureSpec), resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (count <= 0) return
        val spacing = dp(14).toFloat()
        val centerX = width / 2f
        val centerY = height / 2f
        val activeIndex = if (layoutDirection == LAYOUT_DIRECTION_RTL) count - 1 - selected else selected
        val firstCenter = centerX - ((count - 1) * spacing / 2f)
        val primary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary)
        val onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
        paint.color = ColorUtils.setAlphaComponent(onSurface, 96)
        for (index in 0 until count) {
            val x = firstCenter + index * spacing
            canvas.drawCircle(x, centerY, dp(3).toFloat(), paint)
        }
        val activeCenter = firstCenter + activeIndex * spacing
        paint.color = primary
        val width = dp(18).toFloat(); val height = dp(6).toFloat()
        inactive.set(activeCenter - width / 2f, centerY - height / 2f, activeCenter + width / 2f, centerY + height / 2f)
        canvas.drawRoundRect(inactive, height / 2f, height / 2f, paint)
    }

    private fun motionEnabled(): Boolean = runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }.getOrDefault(true)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    override fun onDetachedFromWindow() { animator?.cancel(); animator = null; super.onDetachedFromWindow() }
}
