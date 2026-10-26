package com.glassline.launcher

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.Executors

class WidgetPinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val pending = goAsync()
        EXECUTOR.execute {
            try {
                val store = WorkspaceStore(context)
                val workspace = store.load()
                if (workspace.items.none { it is HomeItem.WidgetItem && it.appWidgetId == id }) {
                    val nextY = (workspace.items.filter { it.page == 0 }.maxOfOrNull { it.y } ?: -1) + 1
                    store.save(workspace.copy(items = workspace.items + HomeItem.WidgetItem(id, 0, 0, nextY)))
                }
                context.sendBroadcast(Intent(ACTION_WIDGET_PINNED).setPackage(context.packageName))
            } catch (_: Exception) {
                context.getSharedPreferences("glassline", Context.MODE_PRIVATE).edit().putString("widget_pin_error", "The widget could not be added to the workspace.").apply()
                context.sendBroadcast(Intent(ACTION_WIDGET_PINNED).setPackage(context.packageName))
            } finally { pending.finish() }
        }
    }

    companion object {
        const val ACTION_WIDGET_PINNED = "com.glassline.launcher.action.WIDGET_PINNED"
        private val EXECUTOR = Executors.newSingleThreadExecutor()
    }
}
