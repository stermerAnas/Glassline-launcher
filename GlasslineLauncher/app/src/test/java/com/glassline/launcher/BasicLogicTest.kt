package com.glassline.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class BasicLogicTest {
    @Test fun queryNormalizationTrimsAndIgnoresCase() {
        assertEquals("calendar", LauncherLogic.normalize("  Calendar  "))
    }
    @Test fun queryNormalizationUsesUnicodeNormalization() {
        assertEquals(LauncherLogic.normalize("café"), LauncherLogic.normalize("cafe\u0301"))
        assertEquals(LauncherLogic.normalize("café"), LauncherLogic.normalize("cafe"))
    }
    @Test fun filterMatchingFindsLabelAndPackage() {
        assertTrue(LauncherLogic.matchesQuery("Samsung Calendar", "com.samsung.android.calendar", "cal"))
        assertTrue(LauncherLogic.matchesQuery("Calendar", "com.samsung.android.calendar", "samsung.android"))
        assertFalse(LauncherLogic.matchesQuery("Calendar", "com.samsung.android.calendar", "camera"))
    }


    @Test fun predictiveQueryRanksPrefixAboveWordBoundaryAboveContains() {
        val tracker = AppUsageTracker(RuntimeEnvironment.getApplication())
        val prefix = LauncherActivity.LaunchableApp("Calendar", "com.example.calendar", "com.example.calendar.Main")
        val boundary = LauncherActivity.LaunchableApp("Samsung Calendar", "com.samsung.calendar", "com.samsung.calendar.Main")
        val contains = LauncherActivity.LaunchableApp("Schedule", "com.example.calendarwidget", "com.example.calendarwidget.Main")
        assertTrue(tracker.queryMatchStrength(prefix, "cal") > tracker.queryMatchStrength(boundary, "cal"))
        assertTrue(tracker.queryMatchStrength(boundary, "cal") > tracker.queryMatchStrength(contains, "cal"))
    }

    @Test fun appUsageScoreIncreasesAfterLaunchRecord() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("glassline_usage", 0).edit().clear().commit()
        val tracker = AppUsageTracker(context)
        val app = LauncherActivity.LaunchableApp("Maps", "com.example.maps", "com.example.maps.Main")
        val before = tracker.score(app, 1_000_000L)
        tracker.record(app, 1_000_000L)
        val after = tracker.score(app, 1_000_000L)
        assertTrue(after > before)
        context.getSharedPreferences("glassline_usage", 0).edit().clear().commit()
    }

    @Test fun workspaceSerializationRoundTripsAllItemTypes() {
        val app = HomeItem.AppItem("com.example.app", "com.example.app.MainActivity", 0, 1, 2)
        val workspace = Workspace(
            pageCount = 4,
            items = listOf(
                app,
                HomeItem.FolderItem("Tools", listOf(app.copy(x = 0, y = 0)), 1, 0, 0, "folder-1"),
                HomeItem.WidgetItem(42, 2, 0, 0, 2, 3),
                HomeItem.ShortcutItem(ShortcutKey("com.example.app", "shortcut-id", 7L), 3, 1, 1)
            ),
            dock = listOf(app.copy(page = 0, x = 0, y = 0))
        )
        assertEquals(workspace, WorkspaceJson.decode(WorkspaceJson.encode(workspace)))
    }

    @Test fun workspaceRejectsUnknownSchemaVersion() {
        val error = org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            WorkspaceJson.decode("""{"schemaVersion":99,"pageCount":1,"items":[],"dock":[]}""")
        }
        assertTrue(error.message!!.contains("Unsupported workspace schema version"))
    }


    @Test fun folderCreationRenameAndRemovalAreImmutableWorkspaceOperations() {
        val one = HomeItem.AppItem("one.pkg", "one.pkg.Main", 0, 0, 0)
        val two = HomeItem.AppItem("two.pkg", "two.pkg.Main", 0, 1, 0)
        val base = Workspace(items = listOf(one, two))
        val foldered = WorkspaceOperations.createFolder(base, one.id, two.id)
        assertEquals(1, foldered.items.size)
        val folder = foldered.items.single() as HomeItem.FolderItem
        assertEquals(2, folder.children.size)
        val renamed = WorkspaceOperations.renameFolder(foldered, folder.id, "Utilities")
        assertEquals("Utilities", (renamed.items.single() as HomeItem.FolderItem).label)
        val extracted = WorkspaceOperations.removeFolderChild(renamed, folder.id, one.id)
        assertEquals(1, (extracted.items.first { it is HomeItem.FolderItem } as HomeItem.FolderItem).children.size)
        assertTrue(extracted.items.any { it.id == one.id })
        val dissolved = WorkspaceOperations.dissolveFolder(extracted, folder.id)
        assertEquals(2, dissolved.items.count { it is HomeItem.AppItem })
    }

    @Test fun movingWorkspaceItemChangesItsPageAndDockRemovalIsScoped() {
        val app = HomeItem.AppItem("app.pkg", "app.pkg.Main", 0, 0, 0)
        val other = HomeItem.AppItem("other.pkg", "other.pkg.Main", 0, 1, 0)
        val base = Workspace(pageCount = 3, items = listOf(app, other), dock = listOf(app, other))
        val moved = WorkspaceOperations.moveToPage(base, app.id, 2)
        assertEquals(2, (moved.items.first { it.id == app.id }).page)
        assertEquals(1, WorkspaceOperations.removeFromDock(base, app.id).dock.size)
    }

    @Test fun hiddenAppPersistenceRoundTripsThroughSharedPreferences() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("hidden-test", 0)
        LauncherLogic.saveHiddenApps(prefs, setOf("com.example.hidden", "com.example.other"))
        assertEquals(setOf("com.example.hidden", "com.example.other"), LauncherLogic.readHiddenApps(prefs))
        prefs.edit().clear().commit()
    }
    @Test fun columnCountParsingClampsUnsupportedValues() {
        assertEquals(3, LauncherLogic.parseColumns(2))
        assertEquals(4, LauncherLogic.parseColumns(4))
        assertEquals(6, LauncherLogic.parseColumns(9))
    }
}
