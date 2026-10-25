package com.glassline.launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.LinearLayout
import android.graphics.drawable.GradientDrawable

class FolderIconView(
    context: Context,
    private val title: String,
    private val onOpen: (FolderIconView) -> Unit
) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    init { isClickable = true; contentDescription = title; setOnClickListener { onOpen(this) } }
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val d = density
        p.color = Color.argb(45, 120, 125, 140)
        c.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 17*d, 17*d, p)
        val colors = intArrayOf(Color.rgb(80,150,255), Color.rgb(140,100,240), Color.rgb(90,195,145), Color.rgb(245,175,75))
        val cell = 13f*d; val gap = 5f*d
        val sx = (width - (cell*2+gap))/2
        val sy = (height - (cell*2+gap))/2
        var i = 0
        for (row in 0..1) for (col in 0..1) {
            p.color = colors[i++]
            val x = sx + col*(cell+gap); val y = sy + row*(cell+gap)
            c.drawRoundRect(x,y,x+cell,y+cell,4*d,4*d,p)
        }
    }
}

class ExpandedFolderView(
    context: Context,
    private val folderTitle: String,
    private val apps: List<String>,
    wallpaperTint: Int = Color.rgb(235,235,242)
) : FrameLayout(context) {
    private val d = resources.displayMetrics.density
    private val panel = FrameLayout(context)
    private var animator: ValueAnimator? = null

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
        val panelBg = GradientDrawable().apply {
            setColor(Color.argb(225, Color.red(wallpaperTint), Color.green(wallpaperTint), Color.blue(wallpaperTint)))
            cornerRadius = 30*d
            setStroke((1*d).toInt(), Color.argb(55,255,255,255))
        }
        panel.background = panelBg
        panel.elevation = 12*d
        panel.clipToOutline = true
        panel.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND

        val heading = TextView(context).apply {
            text = folderTitle; textSize = 19f; setTextColor(Color.rgb(30,31,38))
            gravity = Gravity.CENTER_VERTICAL; setPadding((22*d).toInt(),0,0,0)
        }
        panel.addView(heading, LayoutParams(LayoutParams.MATCH_PARENT,(56*d).toInt()))
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((22*d).toInt(),(60*d).toInt(),(22*d).toInt(),(18*d).toInt())
        }
        apps.forEach { app ->
            content.addView(TextView(context).apply {
                text=app; textSize=16f; setTextColor(Color.rgb(35,36,42))
                gravity=Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,(42*d).toInt()))
        }
        panel.addView(content, LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT))
        val widthDp = (resources.displayMetrics.widthPixels / d).toInt()
        val heightDp = (resources.displayMetrics.heightPixels / d).toInt()
        addView(panel, LayoutParams((minOf(330,widthDp-32)*d).toInt(),(minOf(390,heightDp-100)*d).toInt()))
        setOnClickListener { close() }
        panel.setOnClickListener { /* Consume panel taps. */ }
    }

    override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int) {
        val child=getChildAt(0); val w=child.measuredWidth; val h=child.measuredHeight
        child.layout((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2)
    }

    fun openFrom(icon: View) {
        val rect = Rect()
        icon.getGlobalVisibleRect(rect)
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val sx = rect.exactCenterX() - loc[0] - width/2f
        val sy = rect.exactCenterY() - loc[1] - height/2f
        panel.scaleX=.22f; panel.scaleY=.22f
        panel.translationX=sx; panel.translationY=sy; alpha=0f
        animator?.cancel()
        animator=ValueAnimator.ofFloat(0f,1f).apply {
            duration=280; interpolator=PathInterpolator(.2f,0f,0f,1f)
            addUpdateListener {
                val f=it.animatedValue as Float
                panel.scaleX=.22f+.78f*f; panel.scaleY=.22f+.78f*f
                panel.translationX=sx*(1-f); panel.translationY=sy*(1-f); alpha=f
            }
            start()
        }
        requestFocus()
    }

    fun close() {
        animator?.cancel()
        animator=ValueAnimator.ofFloat(1f,0f).apply {
            duration=180
            addUpdateListener {
                val f=it.animatedValue as Float
                alpha=f; panel.scaleX=.22f+.78f*f; panel.scaleY=.22f+.78f*f
            }
            addListener(object: AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    (parent as? ViewGroup)?.removeView(this@ExpandedFolderView)
                }
            })
            start()
        }
    }
}
