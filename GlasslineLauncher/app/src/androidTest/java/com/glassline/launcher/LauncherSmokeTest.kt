package com.glassline.launcher

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.appcompat.app.AppCompatDelegate
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.startsWith
import org.hamcrest.CoreMatchers.allOf
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.action.ViewActions.swipeDown
import androidx.test.espresso.action.ViewActions.swipeRight
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.CoreMatchers.`is`
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherSmokeTest {
    @Before fun clearLauncherPreferences() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("glassline", 0).edit().clear().commit()
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("glassline_usage", 0).edit().clear().commit()
    }
    @Test fun activityLaunches() { ActivityScenario.launch(LauncherActivity::class.java).use { scenario -> scenario.onActivity { activity -> val grid = activity.window.decorView.findViewWithTag<RecyclerView>("glassline-grid"); assertEquals(4, (grid.layoutManager as GridLayoutManager).spanCount) } } }

    @Test fun swipeUpFromGridOpensDrawer() {
        ActivityScenario.launch(LauncherActivity::class.java).use {
            onView(withTagValue(`is`("glassline-home-pager"))).perform(swipeUp())
            onView(withHint("Search apps")).check(matches(isDisplayed()))
        }
    }


    @Test fun swipeDownFromDrawerReturnsHome() {
        ActivityScenario.launch(LauncherActivity::class.java).use {
            onView(withTagValue(`is`("glassline-home-pager"))).perform(swipeUp())
            onView(withHint("Search apps")).check(matches(isDisplayed()))
            onView(withTagValue(`is`("glassline-grid"))).perform(swipeDown())
            onView(withTagValue(`is`("glassline-home-pager"))).check(matches(isDisplayed()))
        }
    }

    @Test fun horizontalSwipeDoesNotOpenDrawer() {
        ActivityScenario.launch(LauncherActivity::class.java).use {
            onView(withTagValue(`is`("glassline-home-pager"))).perform(swipeRight())
            onView(withHint("Search apps")).check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
    }

    @Test fun alphabetRailAppearsForUnfilteredDrawer() {
        ActivityScenario.launch(LauncherActivity::class.java).use {
            onView(withTagValue(`is`("glassline-home-pager"))).perform(swipeUp())
            onView(withContentDescription("Alphabetical app picker")).check(matches(isDisplayed()))
        }
    }

    @Test fun drawerSearchAcceptsQueryAndShowsEmptyStateByFiltering() {
        ActivityScenario.launch(LauncherActivity::class.java).use {
            onView(withTagValue(`is`("glassline-home-pager"))).perform(swipeUp())
            onView(withHint("Search apps")).perform(replaceText("glassline-no-match-8c31"))
            android.os.SystemClock.sleep(300)
            onView(withTagValue(`is`("glassline-grid"))).check(matches(isDisplayed()))
            onView(withText(containsString("No apps found"))).check(matches(isDisplayed()))
            it.onActivity { activity -> assertEquals(1, activity.window.decorView.findViewWithTag<RecyclerView>("glassline-grid").adapter?.itemCount) }
        }
    }


    @Test fun emptyHomePageOffersWorkspaceActions() {
        ActivityScenario.launch(LauncherActivity::class.java).use {
            onView(withContentDescription("Empty home page. Pin apps from the app drawer.")).perform(androidx.test.espresso.action.ViewActions.click())
            onView(withText("Open app drawer")).check(matches(isDisplayed()))
            onView(withText("Add widget")).check(matches(isDisplayed()))
            onView(withText("Create folder")).check(matches(isDisplayed()))
        }
    }

    @Test fun homeUsesWorkspacePagerWithThreeInitialPages() {
        ActivityScenario.launch(LauncherActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val pager = activity.window.decorView.findViewWithTag<androidx.viewpager2.widget.ViewPager2>("glassline-home-pager")
                assertEquals(3, pager.adapter?.itemCount)
                pager.setCurrentItem(2, false)
                assertEquals(2, pager.currentItem)
            }
        }
    }

    @Test fun hiddenAppsPersistAcrossActivityRecreation() {
        ActivityScenario.launch(LauncherActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.getSharedPreferences("glassline", 0).edit().putStringSet("hidden_apps", setOf("com.example.persisted-hidden")).commit()
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals(setOf("com.example.persisted-hidden"), activity.getSharedPreferences("glassline", 0).getStringSet("hidden_apps", emptySet()))
                activity.getSharedPreferences("glassline", 0).edit().remove("hidden_apps").commit()
            }
        }
    }

    @Test fun columnPreferenceAppliesToExistingGridImmediately() {
        ActivityScenario.launch(LauncherActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.getSharedPreferences("glassline", 0).edit().putInt("columns", 5).commit()
                val grid = activity.window.decorView.findViewWithTag<RecyclerView>("glassline-grid")
                assertEquals(5, (grid.layoutManager as GridLayoutManager).spanCount)
                activity.getSharedPreferences("glassline", 0).edit().putInt("columns", 4).commit()
            }
        }
    }
    @Test fun longPressDrawerAppShowsExpectedActions() {
        ActivityScenario.launch(LauncherActivity::class.java).use {
            onView(withTagValue(`is`("glassline-home-pager"))).perform(swipeUp())
            onView(allOf(withContentDescription(startsWith("Launch ")), isClickable(), isDescendantOfA(withTagValue(`is`("glassline-grid")))))
                .perform(longClick())
            onView(withText("App info")).check(matches(isDisplayed()))
            onView(withText("Uninstall")).check(matches(isDisplayed()))
            onView(withText("Pin to home")).check(matches(isDisplayed()))
        }
    }

    @Test fun lightAndDarkThemeResourcesRebindAfterNightModeChange() {
        val originalMode = AppCompatDelegate.getDefaultNightMode()
        ActivityScenario.launch(LauncherActivity::class.java).use { scenario ->
            try {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                scenario.recreate()
                scenario.onActivity { activity ->
                    assertEquals(Color.parseColor("#10131A"), activity.getColor(R.color.glass_bg))
                }
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                scenario.recreate()
                scenario.onActivity { activity ->
                    assertEquals(Color.parseColor("#F5F6FA"), activity.getColor(R.color.glass_bg))
                }
            } finally {
                AppCompatDelegate.setDefaultNightMode(originalMode)
            }
        }
    }

}
