package com.glassline.launcher

import android.graphics.Rect
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties

@Composable
internal fun AppDrawer(
    palette: LauncherPalette,
    apps: List<LauncherApp>,
    allAppsCount: Int,
    isLoading: Boolean,
    query: String,
    focusRequester: FocusRequester,
    drawerProgress: () -> Float,
    pinnedPackages: List<String>,
    gridColumns: Int,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onLaunchApp: (LauncherApp, View, Rect?) -> Unit,
    onTogglePinned: (LauncherApp) -> Unit,
    onAppInfo: (LauncherApp) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val progress = drawerProgress()
                alpha = progress.coerceIn(0f, 1f)
                translationY = (1f - progress) * 30.dp.toPx()
                scaleX = 0.99f + 0.01f * progress
                scaleY = 0.99f + 0.01f * progress
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(palette.surface)
            .padding(horizontal = 20.dp)
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .pointerInput(Unit) {
                    var dragTotal = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dragTotal = 0f },
                        onVerticalDrag = { _, amount -> dragTotal += amount },
                        onDragEnd = { if (dragTotal > 62.dp.toPx()) onClose() },
                        onDragCancel = { dragTotal = 0f },
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(42.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Close app library", tint = palette.text)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text("App library", color = palette.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp)
                Text("Your apps, without the clutter", color = palette.muted, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("${apps.size} / $allAppsCount", color = palette.muted, fontSize = 10.sp, letterSpacing = 0.5.sp)
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(42.dp)) {
                Icon(Icons.Outlined.MoreHoriz, contentDescription = "More options", tint = palette.muted)
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            placeholder = { Text("Find an app", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(19.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = palette.text,
                unfocusedTextColor = palette.text,
                focusedContainerColor = palette.surface,
                unfocusedContainerColor = palette.surface,
                focusedBorderColor = palette.accent,
                unfocusedBorderColor = palette.border,
                cursorColor = palette.accent,
                focusedLeadingIconColor = palette.accent,
                unfocusedLeadingIconColor = palette.muted,
                focusedTrailingIconColor = palette.muted,
                unfocusedTrailingIconColor = palette.muted,
                focusedPlaceholderColor = palette.muted,
                unfocusedPlaceholderColor = palette.muted,
            ),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 22.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (query.isBlank()) "ALL APPLICATIONS" else "SEARCH RESULTS",
                color = palette.muted,
                fontSize = 10.sp,
                letterSpacing = 1.6.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            if (query.isBlank()) {
                Icon(Icons.Outlined.GridView, contentDescription = null, tint = palette.muted, modifier = Modifier.size(15.dp))
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Loading your apps…", color = palette.muted, fontSize = 13.sp)
            }
        } else if (apps.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Apps, contentDescription = null, tint = palette.muted, modifier = Modifier.size(38.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (query.isBlank()) "No launchable apps found" else "No apps match “$query”",
                        color = palette.text,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (query.isBlank()) "Try reopening Glassline after setup." else "Try a different name or clear your search.",
                        color = palette.muted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                    if (query.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.TextButton(onClick = { onQueryChange("") }) {
                            Text("Clear search", color = palette.accent)
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 30.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(items = apps, key = { it.key }, contentType = { "app-icon" }) { app ->
                    LauncherAppTile(
                        app = app,
                        palette = palette,
                        pinned = app.packageName in pinnedPackages,
                        showLabel = true,
                        onClick = { sourceView, sourceBounds -> onLaunchApp(app, sourceView, sourceBounds) },
                        onTogglePinned = { onTogglePinned(app) },
                        onAppInfo = { onAppInfo(app) },
                    )
                }
            }
        }
    }
}
