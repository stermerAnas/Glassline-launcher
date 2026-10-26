package com.glassline.launcher

/** Pure workspace transformations. Callers persist the returned immutable snapshot. */
object WorkspaceOperations {
    fun moveToPage(workspace: Workspace, itemId: String, page: Int): Workspace {
        val targetPage = page.coerceIn(0, workspace.pageCount - 1)
        val target = workspace.items.firstOrNull { it.id == itemId } ?: return workspace
        val nextY = (workspace.items.filter { it.page == targetPage }.maxOfOrNull { it.y } ?: -1) + 1
        return workspace.copy(items = workspace.items.map { if (it.id == itemId) withPosition(it, targetPage, 0, nextY) else it })
    }

    fun reorderPage(workspace: Workspace, page: Int, orderedIds: List<String>, columns: Int): Workspace {
        val colCount = columns.coerceIn(3, 6)
        val order = orderedIds.withIndex().associate { it.value to it.index }
        val pageItems = workspace.items.filter { it.page == page }.sortedBy { order[it.id] ?: Int.MAX_VALUE }
        val placed = pageItems.mapIndexed { index, item -> withPosition(item, page, index % colCount, index / colCount) }
        val ids = placed.map { it.id }.toSet()
        return workspace.copy(items = workspace.items.filterNot { it.id in ids } + placed)
    }

    fun createFolder(workspace: Workspace, firstId: String, secondId: String, label: String = "Folder"): Workspace {
        if (firstId == secondId) return workspace
        val first = workspace.items.firstOrNull { it.id == firstId } as? HomeItem.AppItem ?: return workspace
        val second = workspace.items.firstOrNull { it.id == secondId } as? HomeItem.AppItem ?: return workspace
        if (first.page != second.page) return workspace
        val folder = HomeItem.FolderItem(label, listOf(first, second), first.page, second.x, second.y)
        return workspace.copy(items = workspace.items.filterNot { it.id == firstId || it.id == secondId } + folder)
    }

    fun addToFolder(workspace: Workspace, folderId: String, itemId: String): Workspace {
        val folder = workspace.items.firstOrNull { it.id == folderId } as? HomeItem.FolderItem ?: return workspace
        val item = workspace.items.firstOrNull { it.id == itemId } as? HomeItem.AppItem ?: return workspace
        if (folder.children.any { it.id == itemId }) return workspace
        return workspace.copy(items = workspace.items.filterNot { it.id == itemId || it.id == folderId } + folder.copy(children = folder.children + item))
    }

    fun renameFolder(workspace: Workspace, folderId: String, label: String): Workspace {
        val cleaned = label.trim().take(40)
        if (cleaned.isEmpty()) return workspace
        return workspace.copy(items = workspace.items.map { if (it is HomeItem.FolderItem && it.id == folderId) it.copy(label = cleaned) else it })
    }

    fun removeFolderChild(workspace: Workspace, folderId: String, childId: String): Workspace {
        val folder = workspace.items.firstOrNull { it.id == folderId } as? HomeItem.FolderItem ?: return workspace
        val child = folder.children.firstOrNull { it.id == childId } ?: return workspace
        val children = folder.children.filterNot { it.id == childId }
        val remaining = if (children.isEmpty()) emptyList() else listOf(folder.copy(children = children))
        val nextY = (workspace.items.filter { it.page == folder.page && it.id != folderId }.maxOfOrNull { it.y } ?: folder.y) + 1
        val extracted = when (child) {
            is HomeItem.AppItem -> child.copy(page = folder.page, x = folder.x, y = nextY)
            is HomeItem.FolderItem -> child.copy(page = folder.page, x = folder.x, y = nextY)
            is HomeItem.WidgetItem -> child.copy(page = folder.page, x = folder.x, y = nextY)
            is HomeItem.ShortcutItem -> child.copy(page = folder.page, x = folder.x, y = nextY)
        }
        return workspace.copy(items = workspace.items.filterNot { it.id == folderId } + remaining + extracted)
    }

    fun dissolveFolder(workspace: Workspace, folderId: String): Workspace {
        val folder = workspace.items.firstOrNull { it.id == folderId } as? HomeItem.FolderItem ?: return workspace
        val existing = workspace.items.filterNot { it.id == folderId }
        val startY = (existing.filter { it.page == folder.page }.maxOfOrNull { it.y } ?: -1) + 1
        val restoredChildren = folder.children.mapIndexed { index, child -> withPosition(child, folder.page, index % 4, startY + index / 4) }
        return workspace.copy(items = existing + restoredChildren)
    }

    fun removeFromDock(workspace: Workspace, appId: String): Workspace = workspace.copy(dock = workspace.dock.filterNot { it.id == appId })

    private fun withPosition(item: HomeItem, page: Int, x: Int, y: Int): HomeItem = when (item) {
        is HomeItem.AppItem -> item.copy(page = page, x = x, y = y)
        is HomeItem.FolderItem -> item.copy(page = page, x = x, y = y)
        is HomeItem.WidgetItem -> item.copy(page = page, x = x, y = y)
        is HomeItem.ShortcutItem -> item.copy(page = page, x = x, y = y)
    }
}
