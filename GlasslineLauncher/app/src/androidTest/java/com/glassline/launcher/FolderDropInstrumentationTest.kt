package com.glassline.launcher

import android.content.Intent
import android.graphics.Point
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderDropInstrumentationTest {
    @Test fun droppingOnePinnedAppOnAnotherCreatesPersistedFolder() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.getSharedPreferences("glassline", 0).edit().clear().commit()
        context.deleteFile("workspace.json")
        val device = UiDevice.getInstance(instrumentation)
        device.pressHome()
        context.startActivity(Intent(context, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        check(device.wait(Until.hasObject(By.desc("Open app drawer")), 8_000))
        val width = device.displayWidth
        val height = device.displayHeight
        device.swipe(width / 2, height * 3 / 4, width / 2, height / 4, 20)
        check(device.wait(Until.hasObject(By.desc("Alphabetical app picker")), 5_000))
        val pinned = mutableSetOf<String>()
        repeat(2) {
            val candidate = device.findObjects(By.descStartsWith("Launch "))
                .filter { it.isClickable }
                .firstOrNull { it.contentDescription?.toString() !in pinned }
                ?: error("Two distinct launchable app cells are required for this test")
            val description = candidate.contentDescription.toString()
            candidate.longClick()
            check(device.wait(Until.hasObject(By.text("Pin to home")), 3_000))
            device.findObject(By.text("Pin to home")).click()
            pinned += description
            device.waitForIdle()
        }
        device.pressBack()
        device.waitForIdle()
        val homeItems = device.findObjects(By.descStartsWith("Launch "))
            .filter { it.isClickable && it.visibleBounds.centerY() < height * 4 / 5 }
        assertTrue("At least two pinned app cells must be visible", homeItems.size >= 2)
        val source = homeItems[0]
        val target = homeItems[1]
        val targetBounds = target.visibleBounds
        source.drag(Point(targetBounds.centerX(), targetBounds.centerY()), 650)
        device.waitForIdle()
        var saved = WorkspaceStore(context).load()
        repeat(10) {
            if (saved.items.any { it is HomeItem.FolderItem }) return@repeat
            android.os.SystemClock.sleep(150)
            saved = WorkspaceStore(context).load()
        }
        assertTrue("Drop should persist a folder item", saved.items.any { it is HomeItem.FolderItem })
    }
}
