package com.glassline.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor

class AppGridView(context:Context, private val repository:AppRepository):View(context) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val apps=mutableListOf<LauncherApp>()
    private val icons=HashMap<String,Bitmap>()
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private val d=resources.displayMetrics.density
    private val iconSize=(48*d).toInt()
    private val cellW=84f*d; private val cellH=92f*d
    private var scrollYpx=0f; private var downY=0f; private var lastY=0f; private var moved=false

    init { isClickable=true }
    fun refresh() {
        scope.launch {
            val result=repository.loadApps()
            apps.clear(); apps.addAll(result); icons.clear(); invalidate(); loadVisibleIcons()
        }
    }
    private fun loadVisibleIcons() {
        if(width<=0) return
        val columns=maxOf(1,(width/cellW).toInt())
        val firstRow=(scrollYpx/cellH).toInt()
        val lastRow=((scrollYpx+height)/cellH).toInt()+1
        val first=(firstRow*columns).coerceAtLeast(0)
        val end=minOf(apps.size,(lastRow+1)*columns)
        scope.launch {
            for(i in first until end) {
                val app=apps[i]; val key=app.packageName+"/"+app.activityName
                if(!icons.containsKey(key)) repository.icon(app,iconSize)?.let { icons[key]=it }
            }
            invalidate()
        }
    }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val columns=maxOf(1,(width/cellW).toInt())
        val startRow=(scrollYpx/cellH).toInt()
        val endRow=((scrollYpx+height)/cellH).toInt()+1
        p.color=Color.WHITE; p.textSize=11*d; p.textAlign=Paint.Align.CENTER
        for(i in apps.indices) {
            val row=i/columns
            if(row<startRow || row>endRow) continue
            val col=i%columns; val cx=col*cellW+cellW/2; val y=row*cellH-scrollYpx
            icons[apps[i].packageName+"/"+apps[i].activityName]?.let {
                canvas.drawBitmap(it,null,RectF(cx-iconSize/2,y+5*d,cx+iconSize/2,y+5*d+iconSize),p)
            }
            val label=apps[i].label
            canvas.drawText(if(label.length>15) label.take(14)+"…" else label,cx,y+82*d,p)
        }
        p.textAlign=Paint.Align.LEFT
    }
    override fun onTouchEvent(e:MotionEvent):Boolean {
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downY=e.y; lastY=e.y; moved=false; return true }
            MotionEvent.ACTION_MOVE -> {
                val dy=lastY-e.y
                if(abs(e.y-downY)>6*d) moved=true
                if(moved) {
                    val cols=maxOf(1,(width/cellW).toInt())
                    val rows=(apps.size+cols-1)/cols
                    scrollYpx=(scrollYpx+dy).coerceIn(0f,maxOf(0f,rows*cellH-height))
                    loadVisibleIcons(); invalidate()
                }
                lastY=e.y; return true
            }
            MotionEvent.ACTION_UP -> {
                if(!moved) {
                    val cols=maxOf(1,(width/cellW).toInt())
                    val col=floor(e.x/cellW).toInt()
                    val row=floor((e.y+scrollYpx)/cellH).toInt()
                    val index=row*cols+col
                    if(index in apps.indices) {
                        val a=apps[index]
                        val intent=Intent(Intent.ACTION_MAIN).setComponent(ComponentName(a.packageName,a.activityName))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try { context.startActivity(intent) }
                        catch (_:android.content.ActivityNotFoundException) { refresh() }
                        catch (_:SecurityException) { refresh() }
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }
    fun dispose() { scope.cancel(); icons.clear() }
}
