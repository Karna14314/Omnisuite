package com.karnadigital.omnisuite.feature.home

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.karnadigital.omnisuite.feature.settings.SettingsScreen
import com.karnadigital.omnisuite.feature.tools.AllToolsScreen
import com.karnadigital.omnisuite.ui.component.*
import com.karnadigital.omnisuite.ui.theme.OmniColors
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeScreenViewModel = hiltViewModel(),
    onEvent: (NavigationEvent) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.Home) }
    var searchQuery by remember { mutableStateOf("") }
    var showHistorySheet by rememberSaveable { mutableStateOf(false) }
    var lastRequestedType by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                try {
                    val fileName = getFileName(context, it) ?: "file"
                    val fileExtension = fileName.substringAfterLast('.').lowercase(Locale.ROOT)

                    val type = lastRequestedType
                    if (type != null) {
                        val isValid = when (type) {
                            "pdf" -> fileExtension == "pdf"
                            "word" -> fileExtension in listOf("docx", "doc", "odt")
                            "excel" -> fileExtension in listOf("xlsx", "xls", "ods", "csv")
                            "slides" -> fileExtension in listOf("pptx", "ppt", "odp")
                            "image" -> fileExtension in listOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
                            "text" -> fileExtension in listOf("txt", "py", "kt", "java", "json", "xml", "html", "css", "js", "gradle", "sh", "bat", "cpp", "c", "md", "properties")
                            "csv" -> fileExtension == "csv"
                            "zip" -> fileExtension == "zip"
                            else -> true
                        }

                        if (!isValid) {
                            val formatMessage = when (type) {
                                "pdf" -> "PDF (.pdf)"
                                "word" -> "Word Document (.docx, .doc)"
                                "excel" -> "Excel Spreadsheet (.xlsx, .xls, .csv)"
                                "slides" -> "PowerPoint Slides (.pptx, .ppt)"
                                "image" -> "Image (.png, .jpg, .webp)"
                                "text" -> "Text File (.txt, .json, .xml)"
                                "csv" -> "CSV Sheet (.csv)"
                                "zip" -> "ZIP Archive (.zip)"
                                else -> "valid file"
                            }
                            Toast.makeText(context, "Invalid format! Please select a $formatMessage.", Toast.LENGTH_LONG).show()
                            lastRequestedType = null
                            return@launch
                        }
                    }

                    lastRequestedType = null

                    val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    try {
                        context.contentResolver.takePersistableUriPermission(it, takeFlags)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    val fileSize = getFileSize(context, it)
                    val mimeType = context.contentResolver.getType(it) ?: when {
                        fileName.endsWith(".pdf") -> "application/pdf"
                        fileName.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        fileName.endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        fileName.endsWith(".pptx") -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                        fileName.endsWith(".txt") -> "text/plain"
                        fileName.endsWith(".csv") -> "text/csv"
                        fileName.endsWith(".zip") -> "application/zip"
                        else -> "*/*"
                    }

                    viewModel.addRecentFile(
                        fileUri = it.toString(),
                        fileName = fileName,
                        mimeType = mimeType,
                        fileSize = fileSize
                    )
                    onEvent(NavigationEvent.OpenFile(it.toString()))
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Import error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val onSelectFileForType: (String) -> Unit = { type ->
        lastRequestedType = if (type == "any") null else type
        when (type) {
            "pdf" -> documentLauncher.launch(arrayOf("application/pdf"))
            "word" -> documentLauncher.launch(arrayOf(
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.sun.xml.writer",
                "application/vnd.oasis.opendocument.text"
            ))
            "excel" -> documentLauncher.launch(arrayOf(
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "text/comma-separated-values",
                "text/csv"
            ))
            "slides" -> documentLauncher.launch(arrayOf(
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.oasis.opendocument.presentation"
            ))
            "image" -> documentLauncher.launch(arrayOf("image/*"))
            "text" -> documentLauncher.launch(arrayOf("text/plain"))
            "csv" -> documentLauncher.launch(arrayOf("text/csv", "text/comma-separated-values"))
            "zip" -> documentLauncher.launch(arrayOf(
                "application/zip",
                "application/x-zip-compressed",
                "application/x-zip"
            ))
            else -> documentLauncher.launch(arrayOf(
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "image/*",
                "text/plain",
                "text/csv",
                "application/zip"
            ))
        }
    }

    Scaffold(
        bottomBar = {
            OmniBottomNav(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        },
        containerColor = OmniColors.Bg,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                HomeTab.Home -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                    ) {
                        OmniTopBar(
                            showActions = false,
                            onNotificationsClick = {},
                            onSettingsClick = { selectedTab = HomeTab.Settings }
                        )

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search files, tools...", color = OmniColors.TextMuted) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon", tint = OmniColors.TextMuted) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OmniColors.Accent,
                                unfocusedBorderColor = OmniColors.Border,
                                focusedContainerColor = OmniColors.Surface2,
                                unfocusedContainerColor = OmniColors.Surface2,
                                focusedTextColor = OmniColors.TextPrimary,
                                unfocusedTextColor = OmniColors.TextPrimary
                            ),
                            singleLine = true
                        )

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectFileForType("any") },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = OmniColors.Accent.copy(alpha = 0.1f)),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(OmniColors.Accent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Open Document",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = OmniColors.TextPrimary
                                    )
                                    Text(
                                        text = "PDF, Word, Excel, Slides, Images, ZIP",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OmniColors.TextMuted
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = OmniColors.TextMuted
                                )
                            }
                        }

                        SectionHeader(title = "Quick Tools")

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HomeGridToolCard(
                                title = "📋 PDF",
                                bgColor = OmniColors.PdfRedBg,
                                borderColor = OmniColors.PdfRed.copy(alpha = 0.4f),
                                textColor = OmniColors.PdfRed,
                                modifier = Modifier.weight(1f),
                                onClick = { onSelectFileForType("pdf") }
                            )
                            HomeGridToolCard(
                                title = "📝 Word",
                                bgColor = OmniColors.DocBlueBg,
                                borderColor = OmniColors.DocBlue.copy(alpha = 0.4f),
                                textColor = OmniColors.DocBlue,
                                modifier = Modifier.weight(1f),
                                onClick = { onSelectFileForType("word") }
                            )
                            HomeGridToolCard(
                                title = "📊 Excel",
                                bgColor = OmniColors.XlsGreenBg,
                                borderColor = OmniColors.XlsGreen.copy(alpha = 0.4f),
                                textColor = OmniColors.XlsGreen,
                                modifier = Modifier.weight(1f),
                                onClick = { onSelectFileForType("excel") }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HomeGridToolCard(
                                title = "🖼️ Slides",
                                bgColor = Color(0x1FF59E0B),
                                borderColor = Color(0xFFF59E0B).copy(alpha = 0.4f),
                                textColor = Color(0xFFF59E0B),
                                modifier = Modifier.weight(1f),
                                onClick = { onSelectFileForType("slides") }
                            )
                            HomeGridToolCard(
                                title = "📸 Images",
                                bgColor = OmniColors.ImgPurpleBg,
                                borderColor = OmniColors.ImgPurple.copy(alpha = 0.4f),
                                textColor = OmniColors.ImgPurple,
                                modifier = Modifier.weight(1f),
                                onClick = { onSelectFileForType("image") }
                            )
                            HomeGridToolCard(
                                title = "📦 ZIP",
                                bgColor = OmniColors.ArcCyanBg,
                                borderColor = OmniColors.ArcCyan.copy(alpha = 0.4f),
                                textColor = OmniColors.ArcCyan,
                                modifier = Modifier.weight(1f),
                                onClick = { onSelectFileForType("zip") }
                            )
                        }

                        // Recent Files Header with Universal History button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "RECENT FILES",
                                style = MaterialTheme.typography.labelMedium,
                                color = OmniColors.TextMuted,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { onEvent(NavigationEvent.NavigateToHistory) }) {
                                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp), tint = OmniColors.Accent)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("All History", color = OmniColors.Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                        
                        // History Category Filter Chips
                        val currentFilter = (uiState as? RecentFilesUiState.Success)?.currentFilter ?: HistoryFilter.ALL
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HistoryFilter.entries.forEach { filter ->
                                val isSelected = currentFilter == filter
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setFilter(filter) },
                                    label = { Text(filter.displayName, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = OmniColors.Accent,
                                        selectedLabelColor = Color.White,
                                        containerColor = OmniColors.Surface2,
                                        labelColor = OmniColors.TextMuted
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = if (isSelected) OmniColors.Accent else OmniColors.Border
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val recentFiles = when (val state = uiState) {
                            is RecentFilesUiState.Success -> state.filteredFiles
                            else -> emptyList()
                        }
                        if (recentFiles.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = OmniColors.Surface2)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(text = "🕐", fontSize = 32.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (currentFilter == HistoryFilter.ALL) "No recent files" else "No ${currentFilter.displayName.lowercase()} found",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = OmniColors.TextMuted
                                    )
                                }
                            }
                        } else {
                            recentFiles.take(8).forEach { file ->
                                val (typeColor, typeIcon) = getFileTypeInfo(file.mimeType)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            if (file.fileUri.contains("|||")) {
                                                onEvent(NavigationEvent.OpenSequentialImages(file.fileUri.split("|||"), file.fileName))
                                            } else {
                                                onEvent(NavigationEvent.OpenFile(file.fileUri))
                                            }
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = OmniColors.Surface2)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // File type icon with color
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = typeColor.copy(alpha = 0.15f),
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = typeIcon,
                                                    contentDescription = null,
                                                    tint = typeColor,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = file.fileName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = OmniColors.TextPrimary
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                // Type badge
                                                Text(
                                                    text = file.mimeType.substringAfterLast('/').take(4).uppercase(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = typeColor,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                )
                                                if (file.fileSize > 0) {
                                                    Text(
                                                        text = " • ${formatFileSize(file.fileSize)}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = OmniColors.TextMuted
                                                    )
                                                }
                                                Text(
                                                    text = " • ${formatRelativeTime(file.lastOpened)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = OmniColors.TextMuted
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.Rounded.ChevronRight,
                                            contentDescription = null,
                                            tint = OmniColors.TextMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                HomeTab.Tools -> {
                    AllToolsScreen(
                        isInline = true,
                        onBack = { selectedTab = HomeTab.Home },
                        onEvent = onEvent,
                        onSelectFileForType = onSelectFileForType
                    )
                }
                HomeTab.Settings -> {
                    SettingsScreen(
                        onBack = { selectedTab = HomeTab.Home }
                    )
                }
            }
        }
    }

    if (showHistorySheet) {
        UniversalHistoryBottomSheet(
            uiState = uiState,
            currentFilter = (uiState as? RecentFilesUiState.Success)?.currentFilter ?: HistoryFilter.ALL,
            onFilterSelected = { viewModel.setFilter(it) },
            onSearchQueryChanged = { viewModel.setSearchQuery(it) },
            onDeleteFile = { viewModel.deleteRecentFile(it) },
            onClearAll = { viewModel.clearRecents() },
            onOpenFile = { file ->
                showHistorySheet = false
                if (file.fileUri.contains("|||")) {
                    onEvent(NavigationEvent.OpenSequentialImages(file.fileUri.split("|||"), file.fileName))
                } else {
                    onEvent(NavigationEvent.OpenFile(file.fileUri))
                }
            },
            onDismiss = { showHistorySheet = false }
        )
    }
}

private fun getFileEmoji(mimeType: String): String {
    return when {
        mimeType.contains("pdf") -> "📋"
        mimeType.contains("word") || mimeType.contains("document") -> "📝"
        mimeType.contains("sheet") || mimeType.contains("excel") -> "📊"
        mimeType.contains("presentation") || mimeType.contains("powerpoint") -> "🖼️"
        mimeType.startsWith("image/") -> "📸"
        mimeType.contains("zip") || mimeType.contains("archive") -> "📦"
        mimeType.startsWith("text/") -> "📄"
        else -> "📁"
    }
}

/**
 * Returns a color and icon for visual file type identification.
 */
private fun getFileTypeInfo(mimeType: String): Pair<Color, androidx.compose.ui.graphics.vector.ImageVector> {
    return when {
        mimeType.contains("pdf") -> Pair(Color(0xFFE53935), Icons.Default.PictureAsPdf)
        mimeType.contains("word") || mimeType.contains("document") -> Pair(Color(0xFF1565C0), Icons.Default.Description)
        mimeType.contains("sheet") || mimeType.contains("excel") -> Pair(Color(0xFF2E7D32), Icons.Default.TableChart)
        mimeType.contains("presentation") || mimeType.contains("powerpoint") -> Pair(Color(0xFFE65100), Icons.Default.Slideshow)
        mimeType.startsWith("image/") -> Pair(Color(0xFF7B1FA2), Icons.Default.Image)
        mimeType.contains("zip") || mimeType.contains("archive") -> Pair(Color(0xFFF9A825), Icons.Default.Archive)
        mimeType.startsWith("text/") -> Pair(Color(0xFF455A64), Icons.Default.Article)
        else -> Pair(Color(0xFF607D8B), Icons.Default.InsertDriveFile)
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val minutes = diff / (1000 * 60)
    val hours = diff / (1000 * 60 * 60)
    val days = diff / (1000 * 60 * 60 * 24)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours hr ago"
        days < 7 -> "$days days ago"
        else -> "${days / 7}w ago"
    }
}

private fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "Unknown_File"
}

private fun getFileSize(context: Context, uri: Uri): Long {
    var result: Long = 0L
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        result = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    } else if (uri.scheme == "file") {
        try {
            val file = java.io.File(uri.path ?: "")
            if (file.exists()) {
                result = file.length()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UniversalHistoryBottomSheet(
    uiState: RecentFilesUiState,
    currentFilter: HistoryFilter,
    onFilterSelected: (HistoryFilter) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onDeleteFile: (com.karnadigital.omnisuite.core.model.RecentFile) -> Unit,
    onClearAll: () -> Unit,
    onOpenFile: (com.karnadigital.omnisuite.core.model.RecentFile) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = OmniColors.Surface,
        contentColor = OmniColors.TextPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = OmniColors.Border) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            // Header with Title and Clear All
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = OmniColors.Accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "History & Recent Activity",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = OmniColors.TextPrimary
                    )
                }

                TextButton(
                    onClick = onClearAll,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Text("Clear All", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    onSearchQueryChanged(it)
                },
                placeholder = { Text("Search history...", color = OmniColors.TextMuted, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = OmniColors.TextMuted, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            query = ""
                            onSearchQueryChanged("")
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = OmniColors.TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OmniColors.Accent,
                    unfocusedBorderColor = OmniColors.Border,
                    focusedContainerColor = OmniColors.Surface2,
                    unfocusedContainerColor = OmniColors.Surface2,
                    focusedTextColor = OmniColors.TextPrimary,
                    unfocusedTextColor = OmniColors.TextPrimary
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Tabs Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HistoryFilter.entries.forEach { filter ->
                    val isSelected = currentFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { onFilterSelected(filter) },
                        label = { Text(filter.displayName, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OmniColors.Accent,
                            selectedLabelColor = Color.White,
                            containerColor = OmniColors.Surface2,
                            labelColor = OmniColors.TextMuted
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) OmniColors.Accent else OmniColors.Border
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val files = when (uiState) {
                is RecentFilesUiState.Success -> uiState.filteredFiles
                else -> emptyList()
            }

            if (files.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "🔍", fontSize = 36.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (query.isNotBlank()) "No matching records found" else "No history in ${currentFilter.displayName}",
                            color = OmniColors.TextMuted,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(files, key = { it.id }) { file ->
                        val (typeColor, typeIcon) = getFileTypeInfo(file.mimeType)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenFile(file) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = OmniColors.Surface2),
                            border = BorderStroke(1.dp, OmniColors.Border)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = typeColor.copy(alpha = 0.15f),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = typeIcon,
                                            contentDescription = null,
                                            tint = typeColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.fileName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = OmniColors.TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = file.mimeType.substringAfterLast('/').take(4).uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = typeColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                        if (file.fileSize > 0) {
                                            Text(
                                                text = " • ${formatFileSize(file.fileSize)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = OmniColors.TextMuted
                                            )
                                        }
                                        Text(
                                            text = " • ${formatRelativeTime(file.lastOpened)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = OmniColors.TextMuted
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onDeleteFile(file) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete",
                                        tint = OmniColors.TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.toDouble())).toInt()
    return String.format("%.2f %s", size / Math.pow(1024.toDouble(), digitGroups.toDouble()), units[digitGroups])
}

@Composable
private fun HomeGridToolCard(
    title: String,
    bgColor: Color,
    borderColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.height(44.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1
            )
        }
    }
}
