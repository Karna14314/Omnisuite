package com.karnadigital.omnisuite.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.karnadigital.omnisuite.core.model.RecentFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Premium 2-Tab History Screen displaying recent document streams and barcode scans offline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Search Bar & Clear all action
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Search logs...", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon") },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            )

            if (uiState is HistoryUiState.Success) {
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(
                    onClick = { viewModel.clearHistory() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear All History",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (val state = uiState) {
            is HistoryUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                }
            }
            is HistoryUiState.Empty -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "🕰️",
                                fontSize = 48.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No History Logs",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Opened documents, scanned barcodes, and generated QR codes appear here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
            is HistoryUiState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(state.items) { item ->
                        HistoryItemRow(
                            item = item,
                            onDelete = { viewModel.deleteItem(item) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Highly polished list element mapping a single history entry.
 */
@Composable
fun HistoryItemRow(
    item: RecentFile,
    onDelete: () -> Unit
) {
    val (typeLabel, iconText, themeColor) = when {
        item.mimeType.contains("pdf", ignoreCase = true) -> Triple("PDF", "📄", Color(0xFFEF4444))
        item.mimeType.contains("sheet", ignoreCase = true) || item.mimeType.contains("excel", ignoreCase = true) -> Triple("Excel", "📊", Color(0xFF10B981))
        item.mimeType.contains("word", ignoreCase = true) || item.mimeType.contains("document", ignoreCase = true) -> Triple("Word", "📝", Color(0xFF3B82F6))
        item.mimeType.contains("presentation", ignoreCase = true) || item.mimeType.contains("powerpoint", ignoreCase = true) -> Triple("Slides", "🖼️", Color(0xFFF59E0B))
        item.mimeType.contains("barcode", ignoreCase = true) -> Triple("Barcode", "📷", Color(0xFF8B5CF6))
        item.mimeType.contains("qrcode", ignoreCase = true) -> Triple("QR Code", "🧬", Color(0xFF06B6D4))
        else -> Triple("Text", "📄", Color(0xFF64748B))
    }

    val formattedTime = remember(item.lastOpened) {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        sdf.format(Date(item.lastOpened))
    }

    val subtitleText = remember(item) {
        if (item.mimeType.contains("barcode") || item.mimeType.contains("qrcode")) {
            item.fileUri
        } else {
            formatFileSize(item.fileSize)
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(themeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = iconText,
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(themeColor.copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = typeLabel,
                            color = themeColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete item",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}
