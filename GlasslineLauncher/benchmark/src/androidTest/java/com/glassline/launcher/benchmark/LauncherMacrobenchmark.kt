package com.glassline.launcher.benchmark

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Point
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkRule
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherMacrobenchmark {
    @get:Rule val benchmarkRule = MacrobenchmarkRule()
    private val targetPackage = "com.glassline.launcher"
    private val launcherComponent = "$targetPackage/.LauncherActivity"

    @Test fun coldStartupCompilationNone() = benchmarkRule.measureRepeated(
        packageName = targetPackage,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.COLD,
        iterations = 5
    ) { launchLauncher() }

    @Test fun coldStartupCompilationFull() = benchmarkRule.measureRepeated(
        packageName = targetPackage,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Full(),
        startupMode = StartupMode.COLD,
        iterations = 5
    ) { launchLauncher() }

    @Test fun drawerScrollFrameTimingWith200Apps() = benchmarkRule.measureRepeated(
        packageName = targetPackage,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launchables = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            PackageManager.MATCH_ALL
        )
        assumeTrue("Install at least 200 launchable apps for the 200-app drawer benchmark; found ${launchables.size}.", launchables.size >= 200)
        launchLauncher()
        openDrawer()
        val width = device.displayWidth
        val height = device.displayHeight
        repeat(4) { device.swipe(width / 2, height * 3 / 4, width / 2, height / 4, 18) }
    }

    @Test fun drawerOpenAnimationFrameTiming() = benchmarkRule.measureRepeated(
        packageName = targetPackage,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5
    ) {
        launchLauncher()
        device.waitForIdle()
        openDrawer()
        device.waitForIdle()
    }

    @Test fun pageSwipeFrameTiming() = benchmarkRule.measureRepeated(
        packageName = targetPackage,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5
    ) {
        launchLauncher()
        device.waitForIdle()
        val width = device.displayWidth
        val height = device.displayHeight
        repeat(3) { device.swipe(width * 4 / 5, height / 2, width / 5, height / 2, 18) }
    }

    @Test fun iconDragFrameTiming() = benchmarkRule.measureRepeated(
        packageName = targetPackage,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5
    ) {
        launchLauncher()
        device.waitForIdle()
        val source = device.findObject(By.descStartsWith("Launch "))
        if (source != null) {
            val bounds = source.visibleBounds
            source.drag(Point(bounds.centerX() + 12, bounds.centerY() + 24), 700)
            device.waitForIdle()
        }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.launchLauncher() {
        device.pressHome()
        startActivityAndWait(
            Intent().setClassName(targetPackage, "$targetPackage.LauncherActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        device.wait(Until.hasObject(By.desc("Open app drawer")), 5_000)
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.openDrawer() {
        device.findObject(By.desc("Open app drawer")).click()
        device.waitForIdle()
    }
}
