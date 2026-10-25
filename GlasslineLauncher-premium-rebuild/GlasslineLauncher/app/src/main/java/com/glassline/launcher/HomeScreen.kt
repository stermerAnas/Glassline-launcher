package com.glassline.launcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
internal fun HomeScreen(
    palette: LauncherPalette,
    clock: String,
    dateLine: String,
    pinnedApps: List<LauncherApp>,
    loadingApps: Boolean,
    drawerProgress: () -> Float,
    onOpenDrawer: () -> Unit,
    onOpenDrawerBySwipe: () -> Unit,
    onOpenSettings: () -> Unit,
    onLaunchApp: (LauncherApp, View, Rect?) -> Unit,
    onTogglePinned: (LauncherApp) -> Unit,
    onAppInfo: (LauncherApp) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val progress = drawerProgress()
                alpha = 1f - 0.18f * progress
                scaleX = 1f - 0.018f * progress
                scaleY = 1f - 0.018f * progress
                translationY = 10.dp.toPx() * progress
            }
            .padding(horizontal = 24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(27.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(palette.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text("g", color = palette.accent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "GLASSLINE",
                color = palette.text,
                fontSize = 12.sp,
                letterSpacing = 2.2.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(42.dp)) {
                Icon(Icons.Outlined.Settings, contentDescription = "Launcher settings", tint = palette.muted, modifier = Modifier.size(21.dp))
            }
        }

        Spacer(Modifier.height(56.dp))
        Text(
            dateLine.ifEmpty { "YOUR DAY, AT A GLANCE" },
            color = palette.accent,
            fontSize = 10.sp,
            letterSpacing = 1.8.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = clock.ifEmpty { "--:--" },
            color = palette.text,
            fontSize = 76.sp,
            lineHeight = 82.sp,
            letterSpacing = (-4).sp,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Light,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "A little more space\nfor what matters.",
            color = palette.muted,
            fontSize = 19.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Normal,
        )

        Spacer(Modifier.height(28.dp))
        HomeSearchField(palette = palette, onClick = onOpenDrawer)
        Spacer(Modifier.weight(1f, fill = true))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("YOUR DOCK", color = palette.text, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        loadingApps -> "Preparing your apps"
                        pinnedApps.isEmpty() -> "Pin apps from the library"
                        else -> "Press and hold to edit"
                    },
                    color = palette.muted,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            if (pinnedApps.isNotEmpty()) {
                Text("${pinnedApps.size} / 5", color = palette.muted, fontSize = 11.sp, letterSpacing = 0.6.sp)
            }
        }
        Spacer(Modifier.height(14.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = palette.surface.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, palette.border.copy(alpha = 0.8f)),
        ) {
            if (pinnedApps.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(90.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (loadingApps) "Loading app icons…" else "Open the app library to pin favourites",
                        color = palette.muted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    pinnedApps.forEach { app ->
                        Box(Modifier.weight(1f)) {
                            LauncherAppTile(
                                app = app,
                                palette = palette,
                                pinned = true,
                                showLabel = false,
                                onClick = { sourceView, sourceBounds -> onLaunchApp(app, sourceView, sourceBounds) },
                                onTogglePinned = { onTogglePinned(app) },
                                onAppInfo = { onAppInfo(app) },
                            )
                        }
                    }
                    if (pinnedApps.size < 4) {
                        Box(Modifier.weight((4 - pinnedApps.size).toFloat()))
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onOpenDrawerBySwipe, onLongClick = onOpenSettings)
                .padding(bottom = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .width(34.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(palette.border),
            )
            Spacer(Modifier.height(11.dp))
            Text("SWIPE UP FOR ALL APPS", color = palette.muted, fontSize = 9.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun HomeSearchField(palette: LauncherPalette, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        color = palette.surface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, palette.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = palette.accent, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(12.dp))
            Text("Search apps", color = palette.muted, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = palette.muted, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LauncherAppTile(
    app: LauncherApp,
    palette: LauncherPalette,
    pinned: Boolean,
    showLabel: Boolean,
    onClick: (View, Rect?) -> Unit,
    onTogglePinned: () -> Unit,
    onAppInfo: () -> Unit,
) {
    var menuExpanded by remember(app.key) { mutableStateOf(false) }
    var iconBounds by remember(app.key) { mutableStateOf<Rect?>(null) }
    val sourceView = LocalView.current
    val appContext = LocalContext.current.applicationContext
    val iconBitmap by produceState<Bitmap?>(initialValue = null, key1 = app.key) {
        value = withContext(Dispatchers.IO) { LauncherIconCache.load(appContext, app) }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (showLabel) 94.dp else 70.dp)
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = { onClick(sourceView, iconBounds) },
                onLongClick = { menuExpanded = true },
            )
            .padding(horizontal = 2.dp, vertical = if (showLabel) 4.dp else 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.onGloballyPositioned { coordinates ->
                val position = coordinates.positionInRoot()
                val newBounds = Rect(
                    position.x.roundToInt(),
                    position.y.roundToInt(),
                    (position.x + coordinates.size.width).roundToInt(),
                    (position.y + coordinates.size.height).roundToInt(),
                )
                if (iconBounds != newBounds) iconBounds = newBounds
            },
        ) {
            Surface(
                modifier = Modifier.size(if (showLabel) 54.dp else 56.dp),
                shape = RoundedCornerShape(if (showLabel) 17.dp else 18.dp),
                color = palette.iconSurface,
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = remember(iconBitmap) { iconBitmap!!.asImageBitmap() },
                        contentDescription = app.label,
                        modifier = Modifier.fillMaxSize().padding(5.dp),
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            app.label.firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
                            color = palette.accent,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            if (pinned && showLabel) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 1.dp, bottom = 1.dp)
                        .size(17.dp)
                        .clip(CircleShape)
                        .background(palette.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = "Pinned", tint = palette.background, modifier = Modifier.size(11.dp))
                }
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(7.dp))
            Text(
                text = app.label,
                color = palette.text,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            )
        }
    }

    DropdownMenu(
        expanded = menuExpanded,
        onDismissRequest = { menuExpanded = false },
        properties = PopupProperties(focusable = true),
    ) {
        DropdownMenuItem(
            text = { Text(if (pinned) "Remove from dock" else "Pin to dock") },
            leadingIcon = { Icon(if (pinned) Icons.Outlined.StarBorder else Icons.Outlined.PushPin, contentDescription = null) },
            onClick = { menuExpanded = false; onTogglePinned() },
        )
        DropdownMenuItem(
            text = { Text("App info") },
            leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
            onClick = { menuExpanded = false; onAppInfo() },
        )
    }
}
