package com.glassline.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Versioned, offline-only launcher workspace persisted in app-private storage. */
data class Workspace(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val pageCount: Int = 3,
    val items: List<HomeItem> = emptyList(),
    val dock: List<HomeItem.AppItem> = emptyList()
) {
    companion object { const val CURRENT_SCHEMA_VERSION = 1 }
}

sealed interface HomeItem {
    val id: String
    val page: Int
    val x: Int
    val y: Int

    data class AppItem(
        val packageName: String,
        val activityName: String,
        override val page: Int,
        override val x: Int,
        override val y: Int,
        val userSerial: Long = 0L,
        override val id: String = "$packageName/$activityName@$userSerial"
    ) : HomeItem

    data class FolderItem(
        val label: String,
        val children: List<HomeItem>,
        override val page: Int,
        override val x: Int,
        override val y: Int,
        override val id: String = UUID.randomUUID().toString()
    ) : HomeItem

    data class WidgetItem(
        val appWidgetId: Int,
        override val page: Int,
        override val x: Int,
        override val y: Int,
        val spanX: Int = 2,
        val spanY: Int = 2,
        override val id: String = "widget:$appWidgetId"
    ) : HomeItem

    data class ShortcutItem(
        val shortcutKey: ShortcutKey,
        override val page: Int,
        override val x: Int,
        override val y: Int,
        override val id: String = "shortcut:${shortcutKey.packageName}:${shortcutKey.shortcutId}"
    ) : HomeItem
}

data class ShortcutKey(val packageName: String, val shortcutId: String, val userSerial: Long = 0L)

object WorkspaceJson {
    fun encode(workspace: Workspace): String = JSONObject().apply {
        put("schemaVersion", workspace.schemaVersion)
        put("pageCount", workspace.pageCount.coerceAtLeast(1))
        put("items", JSONArray().apply { workspace.items.forEach { put(encodeItem(it)) } })
        put("dock", JSONArray().apply { workspace.dock.forEach { put(encodeItem(it)) } })
    }.toString(2)

    fun decode(json: String): Workspace {
        val root = JSONObject(json)
        val version = root.optInt("schemaVersion", -1)
        require(version == Workspace.CURRENT_SCHEMA_VERSION) {
            "Unsupported workspace schema version $version; expected ${Workspace.CURRENT_SCHEMA_VERSION}."
        }
        val items = root.getJSONArray("items").toItemList()
        val dockItems = root.getJSONArray("dock").toItemList().map {
            require(it is HomeItem.AppItem) { "Dock entries must be app items." }; it
        }
        require(dockItems.size <= 4) { "Workspace dock cannot contain more than four apps." }
        return Workspace(version, root.optInt("pageCount", 3).coerceIn(1, 20), items, dockItems)
    }

    private fun encodeItem(item: HomeItem): JSONObject = JSONObject().apply {
        put("id", item.id); put("page", item.page); put("x", item.x); put("y", item.y)
        when (item) {
            is HomeItem.AppItem -> { put("type", "app"); put("packageName", item.packageName); put("activityName", item.activityName); put("userSerial", item.userSerial) }
            is HomeItem.FolderItem -> { put("type", "folder"); put("label", item.label); put("children", JSONArray().apply { item.children.forEach { put(encodeItem(it)) } }) }
            is HomeItem.WidgetItem -> { put("type", "widget"); put("appWidgetId", item.appWidgetId); put("spanX", item.spanX); put("spanY", item.spanY) }
            is HomeItem.ShortcutItem -> { put("type", "shortcut"); put("shortcutPackage", item.shortcutKey.packageName); put("shortcutId", item.shortcutKey.shortcutId); put("userSerial", item.shortcutKey.userSerial) }
        }
    }

    private fun JSONArray.toItemList(): List<HomeItem> = (0 until length()).map { index ->
        val item = getJSONObject(index)
        val id = item.getString("id"); val page = item.optInt("page", 0).coerceAtLeast(0)
        val x = item.optInt("x", 0).coerceAtLeast(0); val y = item.optInt("y", 0).coerceAtLeast(0)
        when (item.getString("type")) {
            "app" -> HomeItem.AppItem(item.getString("packageName"), item.getString("activityName"), page, x, y, item.optLong("userSerial", 0L), id)
            "folder" -> HomeItem.FolderItem(item.getString("label"), item.getJSONArray("children").toItemList(), page, x, y, id)
            "widget" -> HomeItem.WidgetItem(item.getInt("appWidgetId"), page, x, y, item.optInt("spanX", 2).coerceIn(1, 6), item.optInt("spanY", 2).coerceIn(1, 8), id)
            "shortcut" -> HomeItem.ShortcutItem(ShortcutKey(item.getString("shortcutPackage"), item.getString("shortcutId"), item.optLong("userSerial", 0L)), page, x, y, id)
            else -> throw IllegalArgumentException("Unknown workspace item type: ${item.getString("type")}")
        }
    }
}

class WorkspaceStore(context: Context) {
    private val file: File = File(context.filesDir, "workspace.json")
    fun exists(): Boolean = file.exists()
    fun load(): Workspace = if (!file.exists()) Workspace() else WorkspaceJson.decode(file.readText(Charsets.UTF_8))
    fun save(workspace: Workspace) {
        require(workspace.schemaVersion == Workspace.CURRENT_SCHEMA_VERSION) { "Cannot save an unsupported workspace schema." }
        val temp = File(file.parentFile, "workspace.json.tmp")
        temp.writeText(WorkspaceJson.encode(workspace), Charsets.UTF_8)
        if (!temp.renameTo(file)) {
            file.writeText(WorkspaceJson.encode(workspace), Charsets.UTF_8)
            temp.delete()
        }
    }
    fun replaceFromBackup(json: String) { save(WorkspaceJson.decode(json)) }
    fun readBackupFile(): String = file.readText(Charsets.UTF_8)
}
