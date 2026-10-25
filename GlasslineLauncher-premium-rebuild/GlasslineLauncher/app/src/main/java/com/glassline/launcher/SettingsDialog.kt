package com.glassline.launcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SettingsDialog(
    palette: LauncherPalette,
    darkTheme: Boolean,
    gridColumns: Int,
    onDarkThemeChange: (Boolean) -> Unit,
    onGridColumnsChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        titleContentColor = palette.text,
        textContentColor = palette.muted,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(palette.accentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Settings, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Glassline settings", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text("Make the home screen yours", fontSize = 11.sp, color = palette.muted)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(palette.elevated), contentAlignment = Alignment.Center) {
                        Icon(if (darkTheme) Icons.Outlined.DarkMode else Icons.Outlined.LightMode, contentDescription = null, tint = palette.accent)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Dark appearance", color = palette.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("A softer, lower-glare palette", color = palette.muted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = darkTheme,
                        onCheckedChange = onDarkThemeChange,
                        colors = SwitchDefaults.colors(checkedTrackColor = palette.accent, checkedThumbColor = palette.background),
                    )
                }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.GridView, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("App grid", color = palette.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Choose how many icons fit in a row", color = palette.muted, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 4, 5).forEach { count ->
                            val selected = gridColumns == count
                            Surface(
                                onClick = { onGridColumnsChange(count) },
                                modifier = Modifier.weight(1f).height(42.dp),
                                shape = RoundedCornerShape(13.dp),
                                color = if (selected) palette.accentSoft else palette.elevated,
                                border = BorderStroke(1.dp, if (selected) palette.accent.copy(alpha = 0.7f) else palette.border),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("$count columns", color = if (selected) palette.accent else palette.text, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
                Text("Tip: press and hold an app to pin it to your dock or open its Android app info.", color = palette.muted, fontSize = 11.sp, lineHeight = 16.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = palette.accent, fontWeight = FontWeight.SemiBold) }
        },
    )
}
