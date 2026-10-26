package com.glassline.launcher

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.StrictMode
import android.os.SystemClock

internal object StartupMetrics {
    @Volatile var processAttachElapsedRealtime: Long = 0L
}

class GlasslineApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        StartupMetrics.processAttachElapsedRealtime = SystemClock.elapsedRealtime()
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
        }
    }
}
