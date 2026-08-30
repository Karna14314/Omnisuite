package com.karnadigital.omnisuite.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.ChevronRight
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
import com.karnadigital.omnisuite.ui.theme.OmniColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.text.style.TextAlign

/**
 * Premium redesigned History Screen viewport.
 * Compliant with Room/SQLite queries, search, chevrons, chevrons, and illustrations for empty states.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
    onOpenFile: (String) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    val subFilter by viewModel.subFilter.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Top bar with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = OmniColors.TextPrimary
                )
            }
            Text(
                text = "History",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = OmniColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 1. Search Bar & Clear all action matching main cockpit search
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Search history...", style = MaterialTheme.typography.bodyMedium, color = OmniColors.TextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon", tint = OmniColors.TextMuted) },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OmniColors.Accent,
                    unfocusedBorderColor = OmniColors.Border,
                    focusedContainerColor = OmniColors.Surface2,
                    unfocusedContainerColor = OmniColors.Surface2,
                    focusedTextColor = OmniColors.TextPrimary,
                    unfocusedTextColor = OmniColors.TextPrimary
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
                        .background(OmniColors.PdfRedBg)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear All History",
                        tint = OmniColors.PdfRed
                    )
                }
            }
        }

        // Dual-Tab system
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = Color.Transparent,
            contentColor = OmniColors.Accent,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { viewModel.setActiveTab(0) },
                text = { Text("Opened Documents", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { viewModel.setActiveTab(1) },
                text = { Text("Operations Done", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            )
        }

        // Sub-filter chips row (only visible on Tab 0: Opened Documents)
        if (activeTab == 0) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("all" to "All Files", "documents" to "Documents", "images" to "Images").forEach { (filterVal, label) ->
                    val isSelected = subFilter == filterVal
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setSubFilter(filterVal) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OmniColors.Accent.copy(alpha = 0.15f),
                            selectedLabelColor = OmniColors.Accent
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = OmniColors.Border,
                            selectedBorderColor = OmniColors.Accent
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. Stateful history log renderer
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
                // Redesigned empty illustration layout
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Text(
                            text = "🕰️",
                            fontSize = 64.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No logs found here",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = OmniColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (activeTab == 0) "Opened documents and loaded gallery images will appear in this log." else "Conversions, QR codes generated, and scanning operations will be cached here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OmniColors.TextMuted,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            textAlign = TextAlign.Center
                        )
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
                        val isFile = !(item.mimeType.contains("barcode", ignoreCase = true) || item.mimeType.contains("qrcode", ignoreCase = true))
                        HistoryItemRow(
                            item = item,
                            onClick = {
                                if (isFile) {
                                    onOpenFile(item.fileUri)
                                }
                            },
                            onDelete = { viewModel.deleteItem(item) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Highly polished full-width list element mapping a single history entry.
 */
@Composable
fun HistoryItemRow(
    item: RecentFile,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val (typeLabel, iconText, themeColor, categoryBg) = when {
        item.mimeType.contains("pdf", ignoreCase = true) -> Quad("PDF", "📄", OmniColors.PdfRed, OmniColors.PdfRedBg)
        item.mimeType.contains("sheet", ignoreCase = true) || item.mimeType.contains("excel", ignoreCase = true) || item.mimeType.contains("csv", ignoreCase = true) -> Quad("Excel", "📊", OmniColors.XlsGreen, OmniColors.XlsGreenBg)
        item.mimeType.contains("word", ignoreCase = true) || item.mimeType.contains("document", ignoreCase = true) -> Quad("Word", "📝", OmniColors.DocBlue, OmniColors.DocBlueBg)
        item.mimeType.contains("presentation", ignoreCase = true) || item.mimeType.contains("powerpoint", ignoreCase = true) -> Quad("Slides", "🖼️", Color(0xFFF59E0B), Color(0x1FF59E0B))
        item.mimeType.startsWith("image/", ignoreCase = true) || item.mimeType.contains("image", ignoreCase = true) -> Quad("Image", "🖼️", OmniColors.ImgPurple, OmniColors.ImgPurpleBg)
        item.mimeType.contains("barcode", ignoreCase = true) -> Quad("Barcode", "📷", OmniColors.ImgPurple, OmniColors.ImgPurpleBg)
        item.mimeType.contains("qrcode", ignoreCase = true) -> Quad("QR Code", "🧬", OmniColors.ArcCyan, OmniColors.ArcCyanBg)
        else -> Quad("Text", "📄", OmniColors.TextMuted, Color(0x1F7A8299))
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
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = OmniColors.Surface2
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OmniColors.Border, RoundedCornerShape(14.dp))
            .clickable(
                enabled = !(item.mimeType.contains("barcode", ignoreCase = true) || item.mimeType.contains("qrcode", ignoreCase = true)),
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(categoryBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = iconText,
                    fontSize = 18.sp
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
                            .background(categoryBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = typeLabel,
                            color = themeColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formattedTime,
                        fontSize = 9.sp,
                        color = OmniColors.TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = OmniColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.labelSmall,
                    color = OmniColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete item",
                    tint = OmniColors.TextMuted
                )
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}
