package com.karnadigital.omnisuite.feature.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.feature.history.HistoryScreen
import com.karnadigital.omnisuite.feature.tools.AllToolsScreen
import com.karnadigital.omnisuite.feature.utility.rememberDocumentScannerLauncher

/**
 * Bottom Navigation Primary Root Tabs.
 */
enum class HomeTab {
    Home,
    Tools,
    Files,
    History
}

data class OpenFileItem(
    val title: String,
    val iconText: String,
    val color: Color,
    val onClick: () -> Unit
)

/**
 * Premium 4-Tab root Scaffold viewport shell for OmniSuite.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeScreenViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit,
    onNavigateToQrGenerator: () -> Unit,
    onNavigateToBarcodeScanner: () -> Unit,
    onNavigateToImageTools: () -> Unit,
    onNavigateToPdfTools: () -> Unit,
    onNavigateToOcr: () -> Unit,
    onNavigateToSignaturePad: () -> Unit,
    onNavigateToWatermark: () -> Unit,
    onNavigateToBatchTools: () -> Unit,
    onOpenFile: (String) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(HomeTab.Home) }
    var searchQuery by remember { mutableStateOf("") }

    // Reusable Storage Access Framework picker launcher
    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                // Grant persistable URI read/write permissions
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val fileName = getFileName(context, it)
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
            onOpenFile(it.toString())
        }
    }

    // Dynamic camera edge-crop smart scanner launcher
    val scannerLauncher = rememberDocumentScannerLauncher(
        onScanSuccess = { tempUri, savedFile ->
            Toast.makeText(context, "Document scanned successfully!", Toast.LENGTH_SHORT).show()
            viewModel.addRecentFile(
                fileUri = Uri.fromFile(savedFile).toString(),
                fileName = savedFile.name,
                mimeType = "application/pdf",
                fileSize = savedFile.length()
            )
            onOpenFile(Uri.fromFile(savedFile).toString())
        },
        onScanFailure = { exception ->
            Toast.makeText(context, "Scan error: ${exception.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "O",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "OmniSuite",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Open Settings Screen",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home Workspace Dashboard") },
                    label = { Text("Home") },
                    selected = selectedTab == HomeTab.Home,
                    onClick = { selectedTab = HomeTab.Home }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Build, contentDescription = "Dedicated Tools Hub") },
                    label = { Text("Tools") },
                    selected = selectedTab == HomeTab.Tools,
                    onClick = { selectedTab = HomeTab.Tools }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Storage Browser") },
                    label = { Text("Files") },
                    selected = selectedTab == HomeTab.Files,
                    onClick = { selectedTab = HomeTab.Files }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = "Recent Files History") },
                    label = { Text("History") },
                    selected = selectedTab == HomeTab.History,
                    onClick = { selectedTab = HomeTab.History }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
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
                        // Search bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search files or tools...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            ),
                            singleLine = true
                        )

                        // Recent Files horizontal scroll
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent Files",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Clear",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { viewModel.clearRecents() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        when (val state = uiState) {
                            is RecentFilesUiState.Loading -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(strokeWidth = 3.dp)
                                }
                            }
                            is RecentFilesUiState.Empty -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No recent documents opened offline.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            is RecentFilesUiState.Success -> {
                                val filteredFiles = if (searchQuery.isBlank()) {
                                    state.files
                                } else {
                                    state.files.filter { it.fileName.contains(searchQuery, ignoreCase = true) }
                                }

                                if (filteredFiles.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(130.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(12.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No matching files found.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(filteredFiles) { file ->
                                            RecentFileCard(
                                                file = file,
                                                onClick = { onOpenFile(file.fileUri) } // Fixed: Pass fileUri instead of fileName!
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // OPEN FILE grid cards (2x4 list)
                        CategoryHeader("Open File", "Select and open any document offline")
                        Spacer(modifier = Modifier.height(12.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val row1 = listOf(
                                OpenFileItem("PDF", "📕", Color(0xFFEF4444)) { documentLauncher.launch(arrayOf("application/pdf")) },
                                OpenFileItem("Word", "📝", Color(0xFF3B82F6)) { documentLauncher.launch(arrayOf("application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) }
                            )
                            val row2 = listOf(
                                OpenFileItem("Excel", "📊", Color(0xFF10B981)) { documentLauncher.launch(arrayOf("application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/csv")) },
                                OpenFileItem("Slides", "🖼️", Color(0xFFF59E0B)) { documentLauncher.launch(arrayOf("application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation")) }
                            )
                            val row3 = listOf(
                                OpenFileItem("Image", "📷", Color(0xFF8B5CF6)) { documentLauncher.launch(arrayOf("image/*")) },
                                OpenFileItem("Text", "📄", Color(0xFF64748B)) { documentLauncher.launch(arrayOf("text/plain")) }
                            )
                            val row4 = listOf(
                                OpenFileItem("Archive", "🗜️", Color(0xFF06B6D4)) { documentLauncher.launch(arrayOf("application/zip", "application/x-tar", "application/x-rar-compressed", "application/x-7z-compressed")) },
                                OpenFileItem("eBook", "📚", Color(0xFF14B8A6)) { documentLauncher.launch(arrayOf("application/epub+zip", "application/x-mobipocket-ebook")) }
                            )

                            listOf(row1, row2, row3, row4).forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowItems.forEach { item ->
                                        OpenFileCard(
                                            title = item.title,
                                            iconText = item.iconText,
                                            color = item.color,
                                            onClick = item.onClick,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        // Quick toolkits
                        Spacer(modifier = Modifier.height(24.dp))
                        CategoryHeader("Quick Toolkits", "Direct access to local offline operations")
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ToolItemCard(
                                title = "PDF Factory",
                                description = "Merge, split & lock",
                                iconText = "🥞",
                                color = Color(0xFFEF4444),
                                modifier = Modifier.weight(1f),
                                onClick = onNavigateToPdfTools
                            )
                            ToolItemCard(
                                title = "Image Lab",
                                description = "Compress & convert",
                                iconText = "🖼️",
                                color = Color(0xFF8B5CF6),
                                modifier = Modifier.weight(1f),
                                onClick = onNavigateToImageTools
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ToolItemCard(
                                title = "QR Generator",
                                description = "vCard, WiFi forms",
                                iconText = "🧬",
                                color = Color(0xFF06B6D4),
                                modifier = Modifier.weight(1f),
                                onClick = onNavigateToQrGenerator
                            )
                            ToolItemCard(
                                title = "Text OCR",
                                description = "Camera page extraction",
                                iconText = "🔬",
                                color = Color(0xFF10B981),
                                modifier = Modifier.weight(1f),
                                onClick = onNavigateToOcr
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
                HomeTab.Tools -> {
                    AllToolsScreen(
                        isInline = true,
                        onBack = { selectedTab = HomeTab.Home },
                        onNavigateToPdfTools = onNavigateToPdfTools,
                        onNavigateToSignaturePad = onNavigateToSignaturePad,
                        onNavigateToWatermark = onNavigateToWatermark,
                        onNavigateToImageTools = onNavigateToImageTools,
                        onNavigateToQrGenerator = onNavigateToQrGenerator,
                        onNavigateToBarcodeScanner = onNavigateToBarcodeScanner,
                        onNavigateToOcr = onNavigateToOcr,
                        onNavigateToBatchTools = onNavigateToBatchTools,
                        onSelectFileForType = { type ->
                            when (type) {
                                "pdf" -> documentLauncher.launch(arrayOf("application/pdf"))
                                "word" -> documentLauncher.launch(arrayOf("application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                                "excel" -> documentLauncher.launch(arrayOf("application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                                "slides" -> documentLauncher.launch(arrayOf("application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation"))
                                "image" -> documentLauncher.launch(arrayOf("image/*"))
                                "text" -> documentLauncher.launch(arrayOf("text/plain"))
                                "csv" -> documentLauncher.launch(arrayOf("text/csv"))
                            }
                        }
                    )
                }
                HomeTab.Files -> {
                    FileBrowserScreen(
                        onOpenFile = onOpenFile
                    )
                }
                HomeTab.History -> {
                    HistoryScreen()
                }
            }
        }
    }
}

@Composable
fun OpenFileCard(
    title: String,
    iconText: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = iconText,
                    fontSize = 24.sp
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 4-column document selector hubs.
 */
@Composable
fun DocumentHubItem(
    title: String,
    iconText: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = iconText,
                    fontSize = 20.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Generic header representing categorised tool sections.
 */
@Composable
fun CategoryHeader(title: String, subtitle: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Highly polished, responsive card representing a specific workspace tool action.
 */
@Composable
fun ToolItemCard(
    title: String,
    description: String,
    iconText: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = iconText,
                    fontSize = 18.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Visual card representing a cached recent file item.
 */
@Composable
fun RecentFileCard(
    file: RecentFile,
    onClick: () -> Unit
) {
    val (typeChar, themeColor) = when {
        file.mimeType.contains("pdf", ignoreCase = true) -> "PDF" to Color(0xFFEF4444)
        file.mimeType.contains("sheet", ignoreCase = true) || file.mimeType.contains("excel", ignoreCase = true) -> "XLS" to Color(0xFF10B981)
        file.mimeType.contains("word", ignoreCase = true) || file.mimeType.contains("document", ignoreCase = true) -> "DOC" to Color(0xFF3B82F6)
        file.mimeType.contains("presentation", ignoreCase = true) || file.mimeType.contains("powerpoint", ignoreCase = true) -> "PPT" to Color(0xFFF59E0B)
        else -> "TXT" to Color(0xFF64748B)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .width(150.dp)
            .height(130.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(themeColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = typeChar,
                        color = themeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            Column {
                Text(
                    text = file.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatFileSize(file.fileSize),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
