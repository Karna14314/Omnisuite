package com.karnadigital.omnisuite.feature.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.karnadigital.omnisuite.ui.component.SectionHeader
import com.karnadigital.omnisuite.ui.theme.OmniColors
import com.karnadigital.omnisuite.ui.component.OperationResultBottomSheet
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SupportedExtensions = listOf(
    "pdf", "docx", "doc", "xlsx", "xls", "csv", "pptx", "ppt", "txt", "md",
    "png", "jpg", "jpeg", "webp", "gif", "bmp", "zip", "rar", "7z",
    "py", "kt", "java", "json", "xml", "html", "css", "js", "gradle", "sh", "bat", "cpp", "c", "h", "properties",
    "dart", "ts", "tsx", "jsx", "hpp", "cs", "php", "sql", "yaml", "yml", "ini", "cfg", "conf", "log", "tsv", "bash", "rb", "go", "rs", "swift", "scala", "r", "lua"
)

private fun isFileSupported(file: File): Boolean {
    return file.extension.lowercase() in SupportedExtensions
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    val exp = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 3)
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, exp.toDouble()), units[exp])
}

private fun formatFileDate(time: Long): String {
    val df = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US)
    return df.format(Date(time))
}

private fun getFileEmoji(file: File): String {
    if (file.isDirectory) return "📁"
    val name = file.name.lowercase()
    val ext = file.extension.lowercase()
    return when {
        name.endsWith(".pdf") -> "📋"
        name.endsWith(".docx") || name.endsWith(".doc") -> "📝"
        name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".csv") -> "📊"
        name.endsWith(".pptx") || name.endsWith(".ppt") -> "🖼️"
        name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") -> "📦"
        name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp") || name.endsWith(".gif") -> "🖼️"
        name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".m4a") || name.endsWith(".ogg") -> "🎵"
        name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") || name.endsWith(".3gp") -> "📹"
        ext in listOf("py", "kt", "java", "dart", "js", "ts", "tsx", "jsx", "cpp", "c", "h", "hpp", "cs", "php", "sql", "rb", "go", "rs", "swift", "sh", "json", "xml", "html", "css", "yaml", "yml", "gradle") -> "💻"
        name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".log") -> "📄"
        else -> "📄"
    }
}

@Composable
private fun getFileIconBg(file: File): Color {
    if (file.isDirectory) return Color(0x1F3B82F6)
    val name = file.name.lowercase()
    val ext = file.extension.lowercase()
    return when {
        name.endsWith(".pdf") -> OmniColors.PdfRedBg
        name.endsWith(".docx") || name.endsWith(".doc") -> OmniColors.DocBlueBg
        name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".csv") -> OmniColors.XlsGreenBg
        name.endsWith(".pptx") || name.endsWith(".ppt") -> Color(0x1FF59E0B)
        name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") -> OmniColors.ArcCyanBg
        name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp") || name.endsWith(".gif") -> OmniColors.ImgPurpleBg
        name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".m4a") || name.endsWith(".ogg") -> Color(0x1FF59E0B)
        name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") || name.endsWith(".3gp") -> Color(0x1FEF4444)
        ext in listOf("py", "kt", "java", "dart", "js", "ts", "tsx", "jsx", "cpp", "c", "h", "hpp", "cs", "php", "sql", "rb", "go", "rs", "swift", "sh", "json", "xml", "html", "css", "yaml", "yml", "gradle") -> Color(0x1F10B981)
        else -> OmniColors.Surface2
    }
}

private fun formatStorageSize(bytes: Long): String {
    val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 0.1) {
        String.format(Locale.US, "%.1f GB", gb)
    } else {
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        String.format(Locale.US, "%.1f MB", mb)
    }
}

private fun shareFile(context: Context, file: File) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = context.contentResolver.getType(uri) ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share File"))
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot share file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

private fun openFileWithExternalApp(context: Context, file: File) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val extension = file.extension.lowercase()
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open with"))
    } catch (e: Exception) {
        Toast.makeText(context, "No app found to open this file.", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    onSelectFileForType: (String) -> Unit,
    onNavigateToBarcodeScanner: () -> Unit,
    onNavigateToScanToPdf: () -> Unit,
    onOpenFile: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FilesViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Collect flow states from view model
    val hasPermission by viewModel.hasStoragePermission.collectAsState()
    val storageVolumes by viewModel.storageVolumes.collectAsState()
    val activeCategory by viewModel.activeCategory.collectAsState()
    val documentFilter by viewModel.documentFilter.collectAsState()
    val categoryState by viewModel.categoryState.collectAsState()
    val currentDirectoryPath by viewModel.currentDirectoryPath.collectAsState()
    val currentDirectoryFiles by viewModel.currentDirectoryFiles.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val recentFiles by viewModel.recentFiles.collectAsState()

    // Navigation and Action sheet triggers
    val sheetState = rememberModalBottomSheetState()
    var showQuickActionsSheet by remember { mutableStateOf(false) }

    // Dialog state controllers
    var activeActionFile by remember { mutableStateOf<File?>(null) }
    var showFileActionOptions by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }

    // Gallery QR Scanner states
    var decodedQrText by remember { mutableStateOf<String?>(null) }
    var showQrResultSheet by remember { mutableStateOf(false) }

    // Observer lifecycle updates to automatically refresh storage when settings change
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkPermissionAndDiscover()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Permission launch configurations
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            viewModel.checkPermissionAndDiscover()
        }
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        viewModel.checkPermissionAndDiscover()
    }

    val requestStoragePermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                manageStorageLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageStorageLauncher.launch(intent)
            }
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    // Gallery photo picker scanner
    val galleryScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val image = com.google.mlkit.vision.common.InputImage.fromFilePath(context, it)
                val scanner = com.google.mlkit.vision.barcode.BarcodeScanning.getClient()
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val firstBarcode = barcodes.firstOrNull()
                        if (firstBarcode != null) {
                            val rawValue = firstBarcode.rawValue
                            if (rawValue != null) {
                                decodedQrText = rawValue
                                showQrResultSheet = true
                                Toast.makeText(context, "QR / Barcode scanned successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "No readable barcode content found.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "No QR or Barcode detected in this image.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Scan failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to analyze image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showQuickActionsSheet = true },
                containerColor = OmniColors.Accent,
                contentColor = Color.White,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .size(56.dp)
                    .padding(bottom = 8.dp, end = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Quick Actions Menu",
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        containerColor = OmniColors.Bg,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // RENDER HEADER AND SEARCH/SORT OR BREADCRUMBS
            val isInExplorer = currentDirectoryPath != null
            val isInCategory = activeCategory != null

            if (isInExplorer || isInCategory) {
                // Category/Explorer mode header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = {
                            if (isInCategory) {
                                viewModel.selectCategory(null)
                            } else {
                                viewModel.navigateUp()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Navigate Back",
                            tint = OmniColors.TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    if (isInCategory) {
                        Text(
                            text = when (activeCategory) {
                                "images" -> "🖼️ Images"
                                "videos" -> "📹 Videos"
                                "audio" -> "🎵 Audio"
                                "documents" -> "📄 Documents"
                                "archives" -> "📦 Archives"
                                else -> "Category"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = OmniColors.TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        // BREADCRUMBS ROW (Clickable stack navigation segments)
                        val activePath = currentDirectoryPath ?: ""
                        // Attempt to locate matching storage root
                        val activeVolume = storageVolumes.find { activePath.startsWith(it.path) }
                        
                        if (activeVolume != null) {
                            val relativePath = activePath.removePrefix(activeVolume.path).trim('/')
                            val pathSegments = if (relativePath.isEmpty()) emptyList() else relativePath.split('/')
                            
                            LazyRow(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                item {
                                    Text(
                                        text = activeVolume.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (pathSegments.isEmpty()) OmniColors.Accent else OmniColors.TextMuted,
                                        modifier = Modifier.clickable {
                                            viewModel.navigateToDirectory(activeVolume.path)
                                        }
                                    )
                                }
                                items(pathSegments.size) { index ->
                                    Text(
                                        text = " > ",
                                        fontSize = 12.sp,
                                        color = OmniColors.TextMuted
                                    )
                                    val segment = pathSegments[index]
                                    val isLast = index == pathSegments.size - 1
                                    val segmentPath = activeVolume.path + "/" + pathSegments.take(index + 1).joinToString("/")
                                    
                                    Text(
                                        text = segment,
                                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp,
                                        color = if (isLast) OmniColors.Accent else OmniColors.TextMuted,
                                        modifier = Modifier.clickable {
                                            viewModel.navigateToDirectory(segmentPath)
                                        }
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Explorer",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = OmniColors.TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Sticky query filter row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search files...", fontSize = 14.sp, color = OmniColors.TextMuted) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = OmniColors.TextMuted) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = OmniColors.TextMuted)
                                }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = OmniColors.Surface,
                            unfocusedContainerColor = OmniColors.Surface,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    var showSortMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(OmniColors.Surface)
                                .border(1.dp, OmniColors.Border, RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                imageVector = if (sortOrder in listOf(FileSortOrder.SIZE_ASC, FileSortOrder.SIZE_DESC)) Icons.Default.Sort
                                else Icons.Default.FilterList,
                                contentDescription = "Sort order",
                                tint = OmniColors.Accent
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            modifier = Modifier.background(OmniColors.Surface)
                        ) {
                            val sortItems = listOf(
                                FileSortOrder.NAME_ASC to "Name A-Z",
                                FileSortOrder.NAME_DESC to "Name Z-A",
                                FileSortOrder.SIZE_ASC to "Size (Small to Large)",
                                FileSortOrder.SIZE_DESC to "Size (Large to Small)",
                                FileSortOrder.DATE_DESC to "Date (Newest)",
                                FileSortOrder.DATE_ASC to "Date (Oldest)"
                            )
                            sortItems.forEach { (order, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, color = if (sortOrder == order) OmniColors.Accent else OmniColors.TextPrimary) },
                                    onClick = {
                                        viewModel.setSortOrder(order)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // DOCUMENT FILTER SUB-TABS (Only if active category is documents)
                if (activeCategory == "documents") {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val filterChips = listOf(
                            "all" to "📂 All Documents",
                            "pdf" to "📋 PDFs",
                            "word" to "📝 Word Docs",
                            "excel" to "📊 Spreadsheets",
                            "slides" to "🖼️ Presentations",
                            "txt" to "📄 Text Files"
                        )
                        items(filterChips) { (filterVal, label) ->
                            val isSelected = documentFilter == filterVal
                            val bg = if (isSelected) OmniColors.Accent.copy(alpha = 0.15f) else OmniColors.Surface
                            val border = if (isSelected) OmniColors.Accent else OmniColors.Border
                            val textColor = if (isSelected) OmniColors.Accent else OmniColors.TextPrimary

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bg)
                                    .border(1.dp, border, RoundedCornerShape(10.dp))
                                    .clickable { viewModel.setDocumentFilter(filterVal) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = textColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // PERMISSION MANAGER WARNING CARD
            if (!hasPermission) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = OmniColors.Surface),
                    border = BorderStroke(1.dp, OmniColors.Border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "📁 Storage Permissions Required",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = OmniColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "To locate storage devices, query system media databases, and edit local files offline, OmniSuite requires Files Access permissions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OmniColors.TextMuted,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { requestStoragePermission() },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OmniColors.Accent)
                        ) {
                            Text("Configure File Permissions", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            // 1. DASHBOARD VIEW (Drives list, Category shortcuts, Recents)
            if (!isInExplorer && !isInCategory) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState)
                ) {
                    // CATEGORIES GRID
                    SectionHeader(title = "Categories")
                    Spacer(modifier = Modifier.height(8.dp))

                    val cats = listOf(
                        Triple("images", "🖼️ Images", OmniColors.ImgPurple),
                        Triple("videos", "📹 Videos", Color(0xFFEF4444)),
                        Triple("audio", "🎵 Audio", Color(0xFFF59E0B)),
                        Triple("documents", "📄 Documents", OmniColors.DocBlue),
                        Triple("archives", "📦 Archives", OmniColors.ArcCyan)
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                    ) {
                        items(cats) { (catKey, label, color) ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(OmniColors.Surface)
                                    .border(1.dp, OmniColors.Border, RoundedCornerShape(14.dp))
                                    .clickable {
                                        if (hasPermission) {
                                            viewModel.selectCategory(catKey)
                                        } else {
                                            requestStoragePermission()
                                        }
                                    }
                                    .padding(vertical = 16.dp, horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(color.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = label.take(2), fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = label.substring(2),
                                        color = OmniColors.TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // STORAGE DRIVES LIST
                    SectionHeader(title = "Storage Devices")
                    Spacer(modifier = Modifier.height(8.dp))

                    if (storageVolumes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = OmniColors.Accent)
                        }
                    } else {
                        storageVolumes.forEach { volume ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = OmniColors.Surface),
                                border = BorderStroke(1.dp, OmniColors.Border),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp)
                                    .clickable {
                                        if (hasPermission) {
                                            viewModel.navigateToDirectory(volume.path)
                                        } else {
                                            requestStoragePermission()
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (volume.type == "Internal") OmniColors.DocBlueBg
                                                else OmniColors.ArcCyanBg
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (volume.type == "Internal") "📱" else "💾",
                                            fontSize = 20.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = volume.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = OmniColors.TextPrimary
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        val percent = if (volume.totalSpace > 0) {
                                            (volume.totalSpace - volume.freeSpace).toFloat() / volume.totalSpace.toFloat()
                                        } else 0f
                                        
                                        LinearProgressIndicator(
                                            progress = { percent },
                                            color = OmniColors.Accent,
                                            trackColor = OmniColors.Border,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            val used = volume.totalSpace - volume.freeSpace
                                            Text(
                                                text = "${formatStorageSize(used)} used",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = OmniColors.TextMuted
                                            )
                                            Text(
                                                text = formatStorageSize(volume.totalSpace),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = OmniColors.TextMuted,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // RECENT FILES SECTION
                    SectionHeader(title = "Recent Documents")
                    Spacer(modifier = Modifier.height(8.dp))

                    if (recentFiles.isEmpty()) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = OmniColors.Surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 32.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No recent files opened yet.",
                                    color = OmniColors.TextMuted,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        // Render up to 10 recents
                        recentFiles.take(10).forEach { fileItem ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = OmniColors.Surface),
                                border = BorderStroke(1.dp, OmniColors.Border),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .clickable {
                                        onOpenFile(fileItem.fileUri)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val ext = fileItem.fileName.substringAfterLast('.').lowercase()
                                    val emoji = when {
                                        ext == "pdf" -> "📋"
                                        ext in listOf("docx", "doc") -> "📝"
                                        ext in listOf("xlsx", "xls", "csv") -> "📊"
                                        ext in listOf("pptx", "ppt") -> "🖼️"
                                        ext in listOf("zip", "rar", "7z") -> "📦"
                                        ext in listOf("png", "jpg", "jpeg", "webp") -> "🖼️"
                                        ext in listOf("mp3", "wav", "m4a") -> "🎵"
                                        ext in listOf("mp4", "mkv", "avi") -> "📹"
                                        else -> "📄"
                                    }
                                    val colorBg = when {
                                        ext == "pdf" -> OmniColors.PdfRedBg
                                        ext in listOf("docx", "doc") -> OmniColors.DocBlueBg
                                        ext in listOf("xlsx", "xls", "csv") -> OmniColors.XlsGreenBg
                                        ext in listOf("zip", "rar", "7z") -> OmniColors.ArcCyanBg
                                        ext in listOf("png", "jpg", "jpeg", "webp") -> OmniColors.ImgPurpleBg
                                        else -> OmniColors.Border
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colorBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = emoji, fontSize = 16.sp)
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = fileItem.fileName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = OmniColors.TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${formatFileSize(fileItem.fileSize)} • ${formatFileDate(fileItem.lastOpened)}",
                                            fontSize = 10.sp,
                                            color = OmniColors.TextMuted
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }

            // 2. CATEGORY VIEWER LIST
            if (isInCategory) {
                when (val cState = categoryState) {
                    is CategoryState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = OmniColors.Accent)
                        }
                    }
                    is CategoryState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cState.message,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    is CategoryState.Success -> {
                        val files = cState.files
                        if (files.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No files found in this category.",
                                    color = OmniColors.TextMuted
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(bottom = 80.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(files) { file ->
                                    FileExplorerRow(
                                        file = file,
                                        onClick = {
                                            if (isFileSupported(file)) {
                                                onOpenFile(Uri.fromFile(file).toString())
                                            } else {
                                                openFileWithExternalApp(context, file)
                                            }
                                        },
                                        onOptionsClick = {
                                            activeActionFile = file
                                            showFileActionOptions = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }

            // 3. STORAGE EXPLORER DIR LIST
            if (isInExplorer) {
                if (currentDirectoryFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No folders or files here.",
                            color = OmniColors.TextMuted
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(currentDirectoryFiles) { file ->
                            FileExplorerRow(
                                file = file,
                                onClick = {
                                    if (file.isDirectory) {
                                        viewModel.navigateToDirectory(file.absolutePath)
                                    } else {
                                        if (isFileSupported(file)) {
                                            onOpenFile(Uri.fromFile(file).toString())
                                        } else {
                                            openFileWithExternalApp(context, file)
                                        }
                                    }
                                },
                                onOptionsClick = {
                                    activeActionFile = file
                                    showFileActionOptions = true
                                }
                            )
                        }
                    }
                }
            }
        }

        // QUICK ACTIONS DRAWER (BOTTOM SHEET)
        if (showQuickActionsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showQuickActionsSheet = false },
                sheetState = sheetState,
                containerColor = OmniColors.Surface,
                dragHandle = { BottomSheetDefaults.DragHandle(color = OmniColors.Border) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = "Quick Utilities",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OmniColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    BottomSheetItem(
                        icon = "🗂️",
                        title = "Pick Document from Storage",
                        description = "Access device storage via SAF picker",
                        onClick = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                if (!sheetState.isVisible) {
                                    showQuickActionsSheet = false
                                    onSelectFileForType("any")
                                }
                            }
                        }
                    )

                    HorizontalDivider(color = OmniColors.Border, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                    BottomSheetItem(
                        icon = "📄",
                        title = "Scan Document to PDF",
                        description = "Digitize paper sheets with edge-detection camera",
                        onClick = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                if (!sheetState.isVisible) {
                                    showQuickActionsSheet = false
                                    onNavigateToScanToPdf()
                                }
                            }
                        }
                    )

                    HorizontalDivider(color = OmniColors.Border, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                    BottomSheetItem(
                        icon = "🔍",
                        title = "Scan QR / Barcode with Camera",
                        description = "Live camera viewport decoding",
                        onClick = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                if (!sheetState.isVisible) {
                                    showQuickActionsSheet = false
                                    onNavigateToBarcodeScanner()
                                }
                            }
                        }
                    )

                    HorizontalDivider(color = OmniColors.Border, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                    BottomSheetItem(
                        icon = "🖼️",
                        title = "Scan QR/Barcode from Gallery",
                        description = "Parse QR/Barcode payloads from photos offline",
                        onClick = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                if (!sheetState.isVisible) {
                                    showQuickActionsSheet = false
                                    galleryScannerLauncher.launch("image/*")
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // FILE ACTIONS SHEET OPTIONS
        if (showFileActionOptions && activeActionFile != null) {
            val file = activeActionFile!!
            ModalBottomSheet(
                onDismissRequest = { showFileActionOptions = false },
                sheetState = rememberModalBottomSheetState(),
                containerColor = OmniColors.Surface,
                dragHandle = { BottomSheetDefaults.DragHandle(color = OmniColors.Border) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OmniColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    BottomSheetItem(
                        icon = "🗂️",
                        title = "Open With External App",
                        description = "Launch default resolver installed on device",
                        onClick = {
                            showFileActionOptions = false
                            openFileWithExternalApp(context, file)
                        }
                    )

                    HorizontalDivider(color = OmniColors.Border, modifier = Modifier.padding(vertical = 4.dp))

                    BottomSheetItem(
                        icon = "📤",
                        title = "Share Document",
                        description = "Send document using Android Share Sheet",
                        onClick = {
                            showFileActionOptions = false
                            shareFile(context, file)
                        }
                    )

                    HorizontalDivider(color = OmniColors.Border, modifier = Modifier.padding(vertical = 4.dp))

                    BottomSheetItem(
                        icon = "✏️",
                        title = "Rename File",
                        description = "Edit display title of the document",
                        onClick = {
                            showFileActionOptions = false
                            showRenameDialog = true
                        }
                    )

                    HorizontalDivider(color = OmniColors.Border, modifier = Modifier.padding(vertical = 4.dp))

                    BottomSheetItem(
                        icon = "ℹ️",
                        title = "Details & Metadata",
                        description = "View file size, date modified, and path parameters",
                        onClick = {
                            showFileActionOptions = false
                            showDetailsDialog = true
                        }
                    )

                    HorizontalDivider(color = OmniColors.Border, modifier = Modifier.padding(vertical = 4.dp))

                    BottomSheetItem(
                        icon = "🗑️",
                        title = "Delete File",
                        description = "Remove this file permanently from device",
                        onClick = {
                            showFileActionOptions = false
                            showDeleteConfirmation = true
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // RENAME DIALOG CONTROL
        if (showRenameDialog && activeActionFile != null) {
            val file = activeActionFile!!
            var inputName by remember { mutableStateOf(file.nameWithoutExtension) }
            
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                containerColor = OmniColors.Surface,
                title = { Text("Rename File", color = OmniColors.TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            text = "Enter a new name for the file:",
                            color = OmniColors.TextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = inputName,
                            onValueChange = { inputName = it },
                            placeholder = { Text("Filename") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OmniColors.Accent,
                                unfocusedBorderColor = OmniColors.Border,
                                focusedLabelColor = OmniColors.Accent
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.renameFile(file, inputName) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (success) {
                                    showRenameDialog = false
                                    activeActionFile = null
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OmniColors.Accent)
                    ) {
                        Text("Rename", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
                        Text("Cancel", color = OmniColors.TextMuted)
                    }
                }
            )
        }

        // DELETE WARNING ALERT
        if (showDeleteConfirmation && activeActionFile != null) {
            val file = activeActionFile!!
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                containerColor = OmniColors.Surface,
                title = { Text("Confirm Deletion", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = "Are you sure you want to permanently delete \"${file.name}\"? This action is offline, irreversible, and deletes the actual file from storage.",
                        color = OmniColors.TextPrimary,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteFile(file) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (success) {
                                    showDeleteConfirmation = false
                                    activeActionFile = null
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("Delete Permanently", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation = false }) {
                        Text("Cancel", color = OmniColors.TextMuted)
                    }
                }
            )
        }

        // DETAILS INFO DIALOG
        if (showDetailsDialog && activeActionFile != null) {
            val file = activeActionFile!!
            AlertDialog(
                onDismissRequest = { showDetailsDialog = false },
                containerColor = OmniColors.Surface,
                title = { Text("File Details", color = OmniColors.TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        DetailItem("Filename", file.name)
                        DetailItem("Location", file.parent ?: "Root")
                        DetailItem("Size", if (file.isDirectory) "Folder" else formatFileSize(file.length()))
                        DetailItem("Last Modified", formatFileDate(file.lastModified()))
                        DetailItem("Extension", file.extension.uppercase())
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showDetailsDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = OmniColors.Accent)
                    ) {
                        Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // DISPLAY OF QR CODE READ RESULT
        OperationResultBottomSheet(
            show = showQrResultSheet,
            onDismiss = { showQrResultSheet = false; decodedQrText = null },
            title = "QR / Barcode Decoded",
            textResult = decodedQrText
        )
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = OmniColors.TextMuted)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            color = OmniColors.TextPrimary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FileExplorerRow(
    file: File,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val name = file.name
    val emoji = getFileEmoji(file)
    val iconBg = getFileIconBg(file)

    val sizeText = if (file.isDirectory) {
        "Folder"
    } else {
        formatFileSize(file.length())
    }
    val dateText = formatFileDate(file.lastModified()).split(" ").first()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(OmniColors.Surface)
            .border(1.dp, OmniColors.Border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = OmniColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$sizeText • $dateText",
                style = MaterialTheme.typography.labelSmall,
                color = OmniColors.TextMuted
            )
        }

        IconButton(onClick = onOptionsClick) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = OmniColors.TextMuted
            )
        }
    }
}

@Composable
private fun BottomSheetItem(
    icon: String,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(OmniColors.Surface2),
            contentAlignment = Alignment.Center
        ) {
            Text(text = icon, fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = OmniColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = OmniColors.TextMuted
            )
        }
    }
}
