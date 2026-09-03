package com.karnadigital.omnisuite.feature.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

data class EditMenuItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val isToggle: Boolean = false,
    val toggledOn: Boolean = false
)

@Composable
fun EditMenuPopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    items: List<EditMenuItem>,
    modifier: Modifier = Modifier
) {
    if (!expanded) return
    Popup(
        alignment = Alignment.TopEnd,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Card(
            modifier = modifier.width(220.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                items.forEach { item ->
                    val bgColor = if (item.isToggle && item.toggledOn) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgColor)
                            .clickable(enabled = item.enabled) { item.onClick(); onDismiss() }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (item.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (item.isToggle && item.toggledOn) FontWeight.Bold else FontWeight.Normal,
                            color = if (item.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EditMenuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun FormatMenuPopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    isBold: Boolean,
    isItalic: Boolean,
    isUnderline: Boolean,
    onBoldToggle: () -> Unit,
    onItalicToggle: () -> Unit,
    onUnderlineToggle: () -> Unit,
    onColorChange: (String) -> Unit,
    onBgColorChange: (String) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onAlignChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!expanded) return
    Popup(
        alignment = Alignment.TopEnd,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Card(
            modifier = modifier.width(260.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Format", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormatToggleButton("B", isBold, onBoldToggle)
                    FormatToggleButton("I", isItalic, onItalicToggle)
                    FormatToggleButton("U", isUnderline, onUnderlineToggle)
                }
                Text("Text Color", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("#000000" to Color.Black, "#FFFFFF" to Color.White, "#2196F3" to Color(0xFF2196F3), "#4CAF50" to Color(0xFF4CAF50), "#F44336" to Color(0xFFF44336), "#FF9800" to Color(0xFFFF9800)).forEach { (hex, color) ->
                        Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(color).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape).clickable { onColorChange(hex) })
                    }
                }
                Text("Background", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("#FFFFFF" to Color.White, "#FFF9C4" to Color(0xFFFFF9C4), "#E1F5FE" to Color(0xFFE1F5FE), "#E8F5E9" to Color(0xFFE8F5E9), "#FFEBEE" to Color(0xFFFFEBEE), "#F3E5F5" to Color(0xFFF3E5F5)).forEach { (hex, color) ->
                        Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(color).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape).clickable { onBgColorChange(hex) })
                    }
                }
                Text("Alignment", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("LEFT" to Icons.Default.FormatAlignLeft, "CENTER" to Icons.Default.FormatAlignCenter, "RIGHT" to Icons.Default.FormatAlignRight).forEach { (align, icon) ->
                        IconButton(onClick = { onAlignChange(align) }, modifier = Modifier.size(32.dp)) { Icon(icon, contentDescription = align, modifier = Modifier.size(20.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatToggleButton(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)).background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant).border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(6.dp)).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Microsoft 365 Inspired Context-Aware Editing Floating Toolbar.
 * Displays smart quick-action buttons based on selection context (Text, Image, Table).
 */
enum class SelectionType { NONE, TEXT, IMAGE, TABLE }

@Composable
fun ContextAwareFloatingToolbar(
    selectionType: SelectionType,
    onBoldToggle: () -> Unit = {},
    onItalicToggle: () -> Unit = {},
    onUnderlineToggle: () -> Unit = {},
    onCopy: () -> Unit = {},
    onCut: () -> Unit = {},
    onComment: () -> Unit = {},
    onInsertRow: () -> Unit = {},
    onInsertCol: () -> Unit = {},
    onDeleteTable: () -> Unit = {},
    onReplaceImage: () -> Unit = {},
    onRotateImage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (selectionType == SelectionType.NONE) return

    Surface(
        modifier = modifier.padding(8.dp),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 10.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            when (selectionType) {
                SelectionType.TEXT -> {
                    IconButton(onClick = onBoldToggle, modifier = Modifier.size(36.dp)) {
                        Text("B", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }
                    IconButton(onClick = onItalicToggle, modifier = Modifier.size(36.dp)) {
                        Text("I", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    IconButton(onClick = onUnderlineToggle, modifier = Modifier.size(36.dp)) {
                        Text("U", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp))
                    IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onCut, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.ContentCut, contentDescription = "Cut", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onComment, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.AddComment, contentDescription = "Comment", modifier = Modifier.size(18.dp))
                    }
                }
                SelectionType.IMAGE -> {
                    IconButton(onClick = onReplaceImage, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Image, contentDescription = "Replace Image", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onRotateImage, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.RotateRight, contentDescription = "Rotate Image", modifier = Modifier.size(18.dp))
                    }
                }
                SelectionType.TABLE -> {
                    IconButton(onClick = onInsertRow, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.TableRows, contentDescription = "Insert Row", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onInsertCol, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.ViewColumn, contentDescription = "Insert Column", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDeleteTable, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Table", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
                else -> {}
            }
        }
    }
}
