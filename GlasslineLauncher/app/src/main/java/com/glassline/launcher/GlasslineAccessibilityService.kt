package com.glassline.launcher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference

class GlasslineAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { super.onServiceConnected(); activeService = WeakReference(this) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onDestroy() {
        if (activeService?.get() === this) activeService = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var activeService: WeakReference<GlasslineAccessibilityService>? = null
        fun requestNotificationShade(): Boolean = activeService?.get()?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) == true

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
            if (!enabled) return false
            val expected = ComponentName(context, GlasslineAccessibilityService::class.java)
            val services = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(services)
            return splitter.any { ComponentName.unflattenFromString(it) == expected }
        }
    }
}
