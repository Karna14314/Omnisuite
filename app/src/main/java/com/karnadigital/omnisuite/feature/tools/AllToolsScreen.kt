package com.karnadigital.omnisuite.feature.tools

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karnadigital.omnisuite.core.util.ToolPreferences
import com.karnadigital.omnisuite.feature.home.NavigationEvent
import com.karnadigital.omnisuite.ui.theme.OmniColors

data class ToolItem(
    val id: String,
    val icon: String,
    val name: String,
    val description: String,
    val color: Color,
    val onClick: () -> Unit,
    val category: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllToolsScreen(
    onBack: () -> Unit = {},
    isInline: Boolean = false,
    onEvent: (NavigationEvent) -> Unit,
    onSelectFileForType: (String) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategoryFilter by rememberSaveable { mutableStateOf("All") }
    var showFavoritesSheet by remember { mutableStateOf(false) }

    val categories = listOf("All", "PDF", "Word", "Excel", "Slides", "Image", "Archive", "QR & Scan", "Utilities")

    // Flattened tools list with zero duplicates and complete coverage
    val allTools = remember(onEvent, onSelectFileForType) {
        getAllToolsList(onEvent, onSelectFileForType)
    }

    val favoriteIds by ToolPreferences.favoriteToolIds
    val favoriteTools = remember(allTools, favoriteIds) {
        allTools.filter { it.id in favoriteIds }
    }

    // Filtered tools when searching
    val isSearching = searchQuery.isNotBlank()
    val searchResults = remember(searchQuery, allTools) {
        if (!isSearching) emptyList()
        else allTools.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.description.contains(searchQuery, ignoreCase = true) ||
            it.category.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            if (!isInline) {
                TopAppBar(
                    title = {
                        Text(
                            text = "All Tools Suite",
                            fontWeight = FontWeight.Bold,
                            color = OmniColors.TextPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = OmniColors.TextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = OmniColors.Bg
                    )
                )
            }
        },
        containerColor = OmniColors.Bg,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isInline) PaddingValues(0.dp) else innerPadding)
        ) {
            // 1. Search Bar
            SearchBarSection(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onClear = { searchQuery = "" }
            )

            if (isSearching) {
                // Search Results Grid
                SearchResultsView(
                    results = searchResults,
                    favoriteIds = favoriteIds,
                    onToggleFavorite = { tool ->
                        ToolPreferences.toggleFavorite(tool.id)
                        val msg = if (ToolPreferences.isFavorite(tool.id)) "Added to favorites" else "Removed from favorites"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                // Category Filter Chips
                CategoryFilterBar(
                    categories = categories,
                    selectedCategory = selectedCategoryFilter,
                    onSelectCategory = { selectedCategoryFilter = it }
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 2. Favorites Section (only shown when 'All' is selected or user hasn't filtered out)
                    if (selectedCategoryFilter == "All") {
                        item {
                            FavoritesSection(
                                favoriteTools = favoriteTools,
                                onManageFavorites = { showFavoritesSheet = true },
                                favoriteIds = favoriteIds,
                                onToggleFavorite = { tool ->
                                    ToolPreferences.toggleFavorite(tool.id)
                                    val msg = if (ToolPreferences.isFavorite(tool.id)) "Added to favorites" else "Removed from favorites"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }

                    // 3. Sequential WPS-Style Categorized Tool Sections
                    val displayedCategories = if (selectedCategoryFilter == "All") {
                        listOf("PDF", "Word", "Excel", "Slides", "Image", "Archive", "QR & Scan", "Utilities")
                    } else {
                        listOf(selectedCategoryFilter)
                    }

                    displayedCategories.forEach { category ->
                        val categoryTools = allTools.filter { it.category == category }
                        if (categoryTools.isNotEmpty()) {
                            item {
                                CategoryToolSection(
                                    category = category,
                                    tools = categoryTools,
                                    favoriteIds = favoriteIds,
                                    onToggleFavorite = { tool ->
                                        ToolPreferences.toggleFavorite(tool.id)
                                        val msg = if (ToolPreferences.isFavorite(tool.id)) "Added to favorites" else "Removed from favorites"
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 4. Favorites Management Modal Sheet
    if (showFavoritesSheet) {
        FavoritesManagementSheet(
            allTools = allTools,
            favoriteIds = favoriteIds,
            onToggleFavorite = { toolId ->
                ToolPreferences.toggleFavorite(toolId)
            },
            onDismiss = { showFavoritesSheet = false }
        )
    }
}

@Composable
private fun SearchBarSection(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = "Search tools (e.g. merge, compress, watermark, ocr)...",
                fontSize = 13.sp,
                color = OmniColors.TextMuted
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = OmniColors.TextMuted,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = OmniColors.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    )
}

@Composable
private fun CategoryFilterBar(
    categories: List<String>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { category ->
            val isSelected = selectedCategory == category
            FilterChip(
                selected = isSelected,
                onClick = { onSelectCategory(category) },
                label = {
                    Text(
                        text = category,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                ),
                shape = RoundedCornerShape(10.dp)
            )
        }
    }
}

@Composable
private fun FavoritesSection(
    favoriteTools: List<ToolItem>,
    onManageFavorites: () -> Unit,
    favoriteIds: Set<String>,
    onToggleFavorite: (ToolItem) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header Row with Title and + Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Favorite Tools",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = OmniColors.TextPrimary
                    )
                    if (favoriteTools.isNotEmpty()) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${favoriteTools.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Plus (+) Button to manage favorites
                IconButton(
                    onClick = onManageFavorites,
                    modifier = Modifier.size(32.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add or edit favorites",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (favoriteTools.isEmpty()) {
                // Minimized empty state
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "No favorites yet. Tap '+' to pin your most used tools here.",
                        fontSize = 12.sp,
                        color = OmniColors.TextMuted,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = onManageFavorites,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Add", fontSize = 12.sp)
                    }
                }
            } else {
                // 4-Column Grid for Favorites
                WpsToolsGrid(
                    tools = favoriteTools,
                    favoriteIds = favoriteIds,
                    onToggleFavorite = onToggleFavorite
                )
            }
        }
    }
}

@Composable
private fun CategoryToolSection(
    category: String,
    tools: List<ToolItem>,
    favoriteIds: Set<String>,
    onToggleFavorite: (ToolItem) -> Unit
) {
    val categoryHeader = when (category) {
        "PDF" -> "PDF Tools"
        "Word" -> "Word & Documents"
        "Excel" -> "Spreadsheets & Excel"
        "Slides" -> "Presentations & Slides"
        "Image" -> "Image & Creative"
        "Archive" -> "Archive & Security"
        "QR & Scan" -> "Scan & Barcode"
        "Utilities" -> "Utilities"
        else -> category
    }

    val categoryColor = when (category) {
        "PDF" -> OmniColors.PdfRed
        "Word" -> OmniColors.DocBlue
        "Excel" -> OmniColors.XlsGreen
        "Slides" -> Color(0xFFF59E0B)
        "Image" -> OmniColors.ImgPurple
        "Archive" -> OmniColors.ArcCyan
        "QR & Scan" -> Color(0xFF8B5CF6)
        "Utilities" -> Color(0xFF10B981)
        else -> MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // Category Header with colored indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(4.dp, 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(categoryColor)
            )
            Text(
                text = categoryHeader,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = OmniColors.TextPrimary
            )
            Text(
                text = "(${tools.size})",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = OmniColors.TextMuted
            )
        }

        // WPS-Style 4-column Grid
        WpsToolsGrid(
            tools = tools,
            favoriteIds = favoriteIds,
            onToggleFavorite = onToggleFavorite
        )
    }
}

/**
 * 4-column grid displaying tools in WPS Office mobile style.
 * Computes row count dynamically for nested embedding inside LazyColumn.
 */
@Composable
private fun WpsToolsGrid(
    tools: List<ToolItem>,
    favoriteIds: Set<String>,
    onToggleFavorite: (ToolItem) -> Unit
) {
    val columns = 4
    val rows = (tools.size + columns - 1) / columns

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        for (rowIndex in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                for (colIndex in 0 until columns) {
                    val toolIndex = rowIndex * columns + colIndex
                    if (toolIndex < tools.size) {
                        val tool = tools[toolIndex]
                        val isFav = tool.id in favoriteIds
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            WpsToolGridItem(
                                tool = tool,
                                isFavorite = isFav,
                                onToggleFavorite = { onToggleFavorite(tool) }
                            )
                        }
                    } else {
                        // Empty placeholder to preserve column widths
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * Single WPS-style tool grid cell:
 * - Rounded square card with category tint background
 * - Emoji / symbol centered
 * - Star badge if favorited
 * - Tool title centered below with max 2 lines
 * - Long-press to toggle favorite
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WpsToolGridItem(
    tool: ToolItem,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = tool.onClick,
                onLongClick = onToggleFavorite
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon Box
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(tool.color.copy(alpha = 0.12f))
                .border(1.dp, tool.color.copy(alpha = 0.22f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = tool.icon,
                fontSize = 24.sp
            )

            // Small favorite star indicator
            if (isFavorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF59E0B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Favorited",
                        tint = Color.White,
                        modifier = Modifier.size(9.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Tool Title
        Text(
            text = tool.name,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = OmniColors.TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SearchResultsView(
    results: List<ToolItem>,
    favoriteIds: Set<String>,
    onToggleFavorite: (ToolItem) -> Unit
) {
    if (results.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SearchOff,
                    contentDescription = null,
                    tint = OmniColors.TextMuted,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "No tools match your search",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = OmniColors.TextPrimary
                )
                Text(
                    text = "Try searching by keyword like 'rotate', 'zip', 'excel', or 'pdf'",
                    style = MaterialTheme.typography.bodySmall,
                    color = OmniColors.TextMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(results) { tool ->
                val isFav = tool.id in favoriteIds
                WpsToolGridItem(
                    tool = tool,
                    isFavorite = isFav,
                    onToggleFavorite = { onToggleFavorite(tool) }
                )
            }
        }
    }
}

/**
 * Bottom Sheet to manage favorite tools with quick search and checkmarks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesManagementSheet(
    allTools: List<ToolItem>,
    favoriteIds: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, allTools) {
        if (query.isBlank()) allTools
        else allTools.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.category.contains(query, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Manage Favorites",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${favoriteIds.size} tools pinned",
                        style = MaterialTheme.typography.bodySmall,
                        color = OmniColors.TextMuted
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Filter tools...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filtered) { tool ->
                    val isFav = tool.id in favoriteIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onToggleFavorite(tool.id) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(tool.color.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(tool.icon, fontSize = 18.sp)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tool.name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = OmniColors.TextPrimary
                            )
                            Text(
                                text = "${tool.category} • ${tool.description}",
                                fontSize = 11.sp,
                                color = OmniColors.TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Checkbox(
                            checked = isFav,
                            onCheckedChange = { onToggleFavorite(tool.id) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Returns the complete exhaustive catalog of all tools in OmniSuite.
 * Each tool has a unique ID, icon, category, and action callback.
 */
private fun getAllToolsList(
    onEvent: (NavigationEvent) -> Unit,
    onSelectFileForType: (String) -> Unit
): List<ToolItem> {
    return listOf(
        // ================= PDF TOOLS =================
        ToolItem("pdf_merge", "🥞", "Merge PDFs", "Combine multiple files", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfMerge) }, "PDF"),
        ToolItem("pdf_split", "✂️", "Split PDF", "Extract page ranges", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfSplit) }, "PDF"),
        ToolItem("pdf_lock", "🔒", "Encrypt PDF", "Lock with secure password", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfLock) }, "PDF"),
        ToolItem("pdf_decrypt", "🔓", "Decrypt PDF", "Remove PDF password lock", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfDecrypt) }, "PDF"),
        ToolItem("pdf_rotate", "🔄", "Rotate Pages", "Rotate individual/all pages", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfRotate) }, "PDF"),
        ToolItem("pdf_extract", "📑", "Extract Pages", "Select and extract pages", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfExtract) }, "PDF"),
        ToolItem("pdf_delete", "🗑️", "Delete Pages", "Remove pages from PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfDelete) }, "PDF"),
        ToolItem("pdf_reorder", "🔀", "Reorder Pages", "Drag to reorder PDF pages", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfReorder) }, "PDF"),
        ToolItem("pdf_insert", "📎", "Insert Pages", "Insert pages from another PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfInsertPages) }, "PDF"),
        ToolItem("pdf_replace", "🔄", "Replace Pages", "Replace pages with another PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfReplacePages) }, "PDF"),
        ToolItem("pdf_crop", "✂️", "Crop Margins", "Adjust PDF page margins", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfCropMargins) }, "PDF"),
        ToolItem("pdf_resize", "📐", "Resize Pages", "Change PDF page size (A4/Letter)", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfResize) }, "PDF"),
        ToolItem("pdf_page_number", "🔢", "Page Numbers", "Add page numbers to PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfPageNumber) }, "PDF"),
        ToolItem("pdf_header_footer", "📋", "Header & Footer", "Add header and footer", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfHeaderFooter) }, "PDF"),
        ToolItem("watermark", "💧", "Watermark", "Add security stamp overlay", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToWatermark) }, "PDF"),
        ToolItem("pdf_signature", "✍️", "Digital Sign", "Stamp digital signature", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToSignaturePad) }, "PDF"),
        ToolItem("pdf_redact", "⬛", "Redact PDF", "Blackout sensitive areas", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfRedact) }, "PDF"),
        ToolItem("pdf_compress", "🗜️", "Compress PDF", "Reduce PDF file size offline", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfCompress) }, "PDF"),
        ToolItem("pdf_flatten", "🔒", "Flatten PDF", "Flatten interactive form fields", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfFlatten) }, "PDF"),
        ToolItem("pdf_compare", "⚖️", "Compare PDF", "Split-screen diff of two PDFs", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfCompare) }, "PDF"),
        ToolItem("pdf_form_filler", "✍️", "Fill Form", "Fill PDF interactive form fields", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfFormFiller) }, "PDF"),
        ToolItem("pdf_metadata", "✏️", "Edit Metadata", "Edit title, author, keywords", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfMetadata) }, "PDF"),
        ToolItem("pdf_bookmarks", "🔖", "Bookmarks", "Manage PDF bookmarks", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfBookmarks) }, "PDF"),
        ToolItem("pdf_block_editor", "📝", "PDF Editor", "Direct text & block editing", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfBlockEditor) }, "PDF"),
        ToolItem("print_studio", "🖨️", "Print Studio", "N-Up, Booklets & Bleeds", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPrintImpositionStudio) }, "PDF"),
        ToolItem("doc_to_pdf", "📑", "Doc to PDF", "Transcode Word files to PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToDocToPdf) }, "PDF"),
        ToolItem("ppt_to_pdf", "🖼️", "Slides to PDF", "Transcode PPTX files to PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPptToPdf) }, "PDF"),
        ToolItem("xls_to_pdf", "📊", "Excel to PDF", "Transcode Excel sheets to PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToXlsToPdf) }, "PDF"),
        ToolItem("images_to_pdf", "📕", "Images to PDF", "Compile photos into PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToImagesToPdf) }, "PDF"),
        ToolItem("images_to_pdf_layout", "🖼️", "PDF Page Layout", "Custom collage & grid layout", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToImagesToPdfLayout) }, "PDF"),
        ToolItem("scan_to_pdf", "📷", "Scan to PDF", "Compile camera scans to PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToScanToPdf) }, "PDF"),
        ToolItem("txt_to_pdf", "📄", "TXT to PDF", "Convert text file to PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToTxtToPdf) }, "PDF"),
        ToolItem("csv_to_pdf", "📊", "CSV to PDF", "Convert CSV data to PDF table", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToCsvToPdf) }, "PDF"),
        ToolItem("html_to_pdf", "🌐", "HTML to PDF", "Compile custom HTML to PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToHtmlToPdf) }, "PDF"),
        ToolItem("web_to_pdf", "🌐", "Web to PDF", "Render URL layouts to PDF offline", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToWebToPdf) }, "PDF"),
        ToolItem("markdown_to_pdf", "✍️", "Markdown to PDF", "Format Markdown text to PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToMarkdownToPdf) }, "PDF"),
        ToolItem("pdf_to_images", "🖨️", "PDF to Images", "Extract PDF pages to PNGs", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfToImages) }, "PDF"),
        ToolItem("pdf_extract_images", "🖼️", "Extract Images", "Extract embedded photos from PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfExtractImages) }, "PDF"),
        ToolItem("pdf_to_word", "📝", "PDF to Word", "Convert PDF to Word DOCX", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfToWord) }, "PDF"),
        ToolItem("pdf_to_ppt", "🖼️", "PDF to PPT", "Convert PDF to Slides", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfToPpt) }, "PDF"),
        ToolItem("pdf_to_txt", "📝", "PDF to TXT", "Extract plain text from PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfToTxt) }, "PDF"),

        // ================= WORD TOOLS =================
        ToolItem("word_viewer", "📝", "Word Viewer", "Open and read DOCX files", OmniColors.DocBlue, { onSelectFileForType("word") }, "Word"),
        ToolItem("word_doc_to_pdf", "📑", "Word to PDF", "Transcode Word files to PDF", OmniColors.DocBlue, { onEvent(NavigationEvent.NavigateToDocToPdf) }, "Word"),
        ToolItem("word_pdf_to_word", "🔄", "PDF to Word", "Convert PDF to editable DOCX", OmniColors.DocBlue, { onEvent(NavigationEvent.NavigateToPdfToWord) }, "Word"),
        ToolItem("docx_to_txt", "📄", "DOCX to TXT", "Extract text blocks to TXT file", OmniColors.DocBlue, { onEvent(NavigationEvent.NavigateToDocxToTxt) }, "Word"),
        ToolItem("word_txt_to_pdf", "📄", "TXT to PDF", "Convert text file to PDF", OmniColors.DocBlue, { onEvent(NavigationEvent.NavigateToTxtToPdf) }, "Word"),
        ToolItem("text_editor", "✏️", "Text Editor", "Read and edit local TXT files", OmniColors.DocBlue, { onSelectFileForType("text") }, "Word"),
        ToolItem("word_count", "🧮", "Word Count", "Analyze document metrics", OmniColors.DocBlue, { onEvent(NavigationEvent.NavigateToAdvancedWordCount) }, "Word"),
        ToolItem("text_compare", "🔍", "Text Compare", "Compare text diffs offline", OmniColors.DocBlue, { onEvent(NavigationEvent.NavigateToTextCompare) }, "Word"),

        // ================= EXCEL TOOLS =================
        ToolItem("excel_viewer", "📊", "Excel Viewer", "View spreadsheet XLSX cells", OmniColors.XlsGreen, { onSelectFileForType("excel") }, "Excel"),
        ToolItem("excel_to_pdf", "📑", "Excel to PDF", "Transcode Excel sheets to PDF", OmniColors.XlsGreen, { onEvent(NavigationEvent.NavigateToXlsToPdf) }, "Excel"),
        ToolItem("csv_to_xlsx", "📤", "CSV to Excel", "Import CSV records to Excel", OmniColors.XlsGreen, { onEvent(NavigationEvent.NavigateToCsvToXlsx) }, "Excel"),
        ToolItem("xlsx_to_csv", "📥", "Excel to CSV", "Export sheet cells to CSV", OmniColors.XlsGreen, { onEvent(NavigationEvent.NavigateToXlsxToCsv) }, "Excel"),
        ToolItem("csv_editor", "📅", "CSV Editor", "Edit and parse CSV grids", OmniColors.XlsGreen, { onSelectFileForType("csv") }, "Excel"),
        ToolItem("excel_csv_to_pdf", "📊", "CSV to PDF", "Convert CSV data to PDF table", OmniColors.XlsGreen, { onEvent(NavigationEvent.NavigateToCsvToPdf) }, "Excel"),

        // ================= SLIDES TOOLS =================
        ToolItem("slides_viewer", "🖼️", "Slides Viewer", "Launch PPTX presentation", Color(0xFFF59E0B), { onSelectFileForType("slides") }, "Slides"),
        ToolItem("slides_ppt_to_pdf", "📑", "Slides to PDF", "Transcode PPTX files to PDF", Color(0xFFF59E0B), { onEvent(NavigationEvent.NavigateToPptToPdf) }, "Slides"),
        ToolItem("slides_pdf_to_ppt", "🔄", "PDF to Slides", "Convert PDF to Presentation", Color(0xFFF59E0B), { onEvent(NavigationEvent.NavigateToPdfToPpt) }, "Slides"),
        ToolItem("pptx_to_txt", "📄", "PPTX to TXT", "Extract presentation text to TXT", Color(0xFFF59E0B), { onEvent(NavigationEvent.NavigateToPptxToTxt) }, "Slides"),

        // ================= IMAGE TOOLS =================
        ToolItem("image_resize", "📐", "Resize Dimensions", "Exact WxH px & presets", OmniColors.ImgPurple, { onEvent(NavigationEvent.NavigateToImageToolsWithTab(0)) }, "Image"),
        ToolItem("image_compress", "🗜️", "Compress Image", "Target KB for jobs & forms", OmniColors.ImgPurple, { onEvent(NavigationEvent.NavigateToImageToolsWithTab(1)) }, "Image"),
        ToolItem("passport_photo", "✂️", "Passport Photo", "Standard 2x2, 3.5x4.5cm ID crop", OmniColors.ImgPurple, { onEvent(NavigationEvent.NavigateToImageToolsWithTab(2)) }, "Image"),
        ToolItem("image_convert", "🔄", "Format Converter", "Convert JPG, PNG, WEBP, PDF", OmniColors.ImgPurple, { onEvent(NavigationEvent.NavigateToImageToolsWithTab(1)) }, "Image"),
        ToolItem("photo_adjust", "🎨", "Photo Adjust", "Brightness, contrast, tones", OmniColors.ImgPurple, { onEvent(NavigationEvent.NavigateToImageToolsWithTab(3)) }, "Image"),
        ToolItem("text_ocr", "🔬", "Text OCR", "Extract text offline with ML Kit", OmniColors.ImgPurple, { onEvent(NavigationEvent.NavigateToOcr) }, "Image"),
        ToolItem("collage_maker", "🖼️", "Collage Maker", "Grid photo collage designer", OmniColors.ImgPurple, { onEvent(NavigationEvent.NavigateToCollageMaker) }, "Image"),
        ToolItem("meme_maker", "🎭", "Meme Maker", "Add top & bottom captions", OmniColors.ImgPurple, { onEvent(NavigationEvent.NavigateToMemeMaker) }, "Image"),
        ToolItem("color_picker", "🎨", "Color Picker", "Pick color codes from photos", OmniColors.ImgPurple, { onEvent(NavigationEvent.NavigateToColorPicker) }, "Image"),

        // ================= ARCHIVE & SECURITY TOOLS =================
        ToolItem("zip_maker", "🗜️", "ZIP Maker", "Compress multiple files to ZIP", OmniColors.ArcCyan, { onEvent(NavigationEvent.NavigateToZipMaker) }, "Archive"),
        ToolItem("zip_extractor", "🔓", "ZIP Extractor", "Extract local ZIP archives", OmniColors.ArcCyan, { onSelectFileForType("zip") }, "Archive"),
        ToolItem("password_zip", "🔐", "Password ZIP", "Create AES-256 protected ZIP", OmniColors.ArcCyan, { onEvent(NavigationEvent.NavigateToPasswordZip) }, "Archive"),
        ToolItem("extract_password_zip", "🔓", "Extract Pass ZIP", "Extract password-protected ZIP", OmniColors.ArcCyan, { onEvent(NavigationEvent.NavigateToPasswordZipExtract) }, "Archive"),
        ToolItem("file_encrypt", "🔒", "Encrypt File", "AES-256 file encryption", OmniColors.ArcCyan, { onEvent(NavigationEvent.NavigateToFileEncrypt) }, "Archive"),
        ToolItem("file_decrypt", "🔓", "Decrypt File", "Decrypt AES-256 files", OmniColors.ArcCyan, { onEvent(NavigationEvent.NavigateToFileDecrypt) }, "Archive"),
        ToolItem("tar_tools", "📦", "TAR Archiver", "Create or unpack TAR archives", OmniColors.ArcCyan, { onEvent(NavigationEvent.NavigateToTarTools) }, "Archive"),
        ToolItem("file_checksum", "🛡️", "File Checksum", "MD5, SHA-256 hash calculator", OmniColors.ArcCyan, { onEvent(NavigationEvent.NavigateToFileChecksum) }, "Archive"),

        // ================= QR & SCAN TOOLS =================
        ToolItem("qr_scanner", "📷", "QR & Barcode Scanner", "Live camera viewfinder scanner", Color(0xFF8B5CF6), { onEvent(NavigationEvent.NavigateToBarcodeScanner) }, "QR & Scan"),
        ToolItem("qr_generator", "🧬", "QR Generator", "Compile WiFi/vCard/URL QR", Color(0xFF8B5CF6), { onEvent(NavigationEvent.NavigateToQrGenerator) }, "QR & Scan"),
        ToolItem("barcode_builder", "📊", "Barcode Builder", "Generate EAN/UPC barcodes", Color(0xFF8B5CF6), { onEvent(NavigationEvent.NavigateToQrGeneratorWithTab(1)) }, "QR & Scan"),

        // ================= UTILITIES =================
        ToolItem("batch_toolkit", "⚡", "Batch Toolkit", "Batch process multiple files", Color(0xFF10B981), { onEvent(NavigationEvent.NavigateToBatchTools) }, "Utilities"),
        ToolItem("read_aloud", "🔊", "Read Aloud", "Text-to-speech for docs (offline)", Color(0xFF10B981), { onEvent(NavigationEvent.NavigateToReadAloud) }, "Utilities"),
        ToolItem("unit_converter", "📏", "Unit Converter", "Length, weight, temperature", Color(0xFF10B981), { onEvent(NavigationEvent.NavigateToUnitConverter) }, "Utilities")
    )
}
