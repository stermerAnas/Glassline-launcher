package com.glassline.launcher.baselineprofile

import android.content.Intent
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val baselineProfileRule = BaselineProfileRule()

    @Test fun generateStartupDrawerAndSearchProfile() = baselineProfileRule.collect(
        packageName = "com.glassline.launcher"
    ) {
        device.pressHome()
        startActivityAndWait(
            Intent().setClassName("com.glassline.launcher", "com.glassline.launcher.LauncherActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        device.wait(Until.hasObject(By.desc("Open app drawer")), 5_000)
        device.findObject(By.desc("Open app drawer")).click()
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
    }
}
