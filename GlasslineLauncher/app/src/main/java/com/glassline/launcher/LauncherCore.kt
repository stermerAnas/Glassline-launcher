package com.glassline.launcher

import android.content.Context
import android.graphics.Color
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.abs

internal object Motion {
    const val PAGE_STIFFNESS = 700f
    const val PAGE_DAMPING = 0.88f
    const val REACH_STIFFNESS = 520f
    const val REACH_DAMPING = 0.90f
    const val FOLDER_STIFFNESS = 650f
    const val FOLDER_DAMPING = 0.86f

    fun spring(view: View, property: DynamicAnimation.ViewProperty,
               stiffness: Float, damping: Float, end: Float) =
        SpringAnimation(view, property).apply {
            spring = SpringForce(end).setStiffness(stiffness).setDampingRatio(damping)
        }
}

class HomeScreenLayout(context: Context) : FrameLayout(context) {
    var reachability = false
        private set
    private var downX = 0f
    private var downY = 0f
    private var tracking = false
    private var previousVelocity = 0f
    private var peakHapticArmed = false
    private val reachSpring = Motion.spring(
        this, DynamicAnimation.TRANSLATION_Y,
        Motion.REACH_STIFFNESS, Motion.REACH_DAMPING, 0f
    )

    init {
        setWillNotDraw(true)
        clipChildren = false
        clipToPadding = false
        isClickable = true
        reachSpring.addUpdateListener { _, value, velocity ->
            if (peakHapticArmed && previousVelocity > 0f && velocity <= 0f && value > 1f) {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                peakHapticArmed = false
            }
            previousVelocity = velocity
        }
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                tracking = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = e.y - downY
                if (dy > 18 * resources.displayMetrics.density &&
                    dy > abs(e.x - downX) * 1.35f && downY < height * .45f) {
                    tracking = true
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> tracking = false
        }
        return tracking
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                tracking = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return false
                translationY = ((e.y - downY) * .62f).coerceIn(0f, height * .32f)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!tracking) return false
                tracking = false
                reachability = e.y - downY > height * .10f
                val target = if (reachability) height * .24f else 0f
                reachSpring.cancel()
                reachSpring.spring = SpringForce(target)
                    .setStiffness(Motion.REACH_STIFFNESS)
                    .setDampingRatio(Motion.REACH_DAMPING)
                peakHapticArmed = reachability
                previousVelocity = 0f
                reachSpring.start()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                tracking = false
                reachability = false
                reachSpring.animateToFinalPosition(0f)
                return true
        }
        }
        return true
    }

    fun dismissReachability() {
        reachability = false
        reachSpring.animateToFinalPosition(0f)
    }
}

fun roundedBackground(color: Int, radiusPx: Float) =
    android.graphics.drawable.GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusPx
    }

fun Context.dp(value: Float): Int =
    (value * resources.displayMetrics.density + .5f).toInt()
