package com.karnadigital.omnisuite.feature.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import coil.compose.AsyncImage
import com.karnadigital.omnisuite.ui.component.SectionHeader
import com.karnadigital.omnisuite.ui.theme.OmniColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    onSelectFileForType: (String) -> Unit,
    onNavigateToBarcodeScanner: () -> Unit,
    onNavigateToScanToPdf: () -> Unit,
    onOpenFile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }

    // Standard storage roots
    val downloadsDir = remember { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) }
    val documentsDir = remember { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) }
    val draftsDir = remember { context.getExternalFilesDir(null) ?: context.filesDir }

    var activeRootType by rememberSaveable { mutableStateOf("Downloads") }
    var currentDirectoryPath by rememberSaveable { mutableStateOf(downloadsDir.absolutePath) }
    val currentDirectory = remember(currentDirectoryPath) { File(currentDirectoryPath) }

    // Scanned categories states
    var activeCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAlbumName by rememberSaveable { mutableStateOf<String?>(null) }
    var scannedImagesMap by remember { mutableStateOf<Map<String, List<File>>>(emptyMap()) }
    var scannedDocuments by remember { mutableStateOf<List<File>>(emptyList()) }
    var scannedArchives by remember { mutableStateOf<List<File>>(emptyList()) }
    var isScanningCategory by remember { mutableStateOf(false) }

    // Real system storage capacity stats
    val storageStats = remember {
        try {
            val stat = android.os.StatFs(Environment.getExternalStorageDirectory().path)
            val totalBytes = stat.totalBytes
            val availableBytes = stat.availableBytes
            val usedBytes = totalBytes - availableBytes
            val totalGB = totalBytes / (1024 * 1024 * 1024)
            val usedGB = usedBytes / (1024 * 1024 * 1024)
            val percent = (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            Triple(usedGB, totalGB, percent)
        } catch (e: Exception) {
            Triple(42L, 128L, 0.32f) // mock fallback
        }
    }

    // Storage permission checks
    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            currentDirectoryPath = downloadsDir.absolutePath
            activeRootType = "Downloads"
        }
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hasPermission = Environment.isExternalStorageManager()
            if (hasPermission) {
                currentDirectoryPath = downloadsDir.absolutePath
                activeRootType = "Downloads"
            }
        }
    }

    val requestStoragePermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                manageStorageLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageStorageLauncher.launch(intent)
            }
        } else {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    // Scans standard directories for images, documents, or archives in the background
    LaunchedEffect(activeCategory, hasPermission) {
        if (hasPermission && activeCategory != null) {
            isScanningCategory = true
            scope.launch(Dispatchers.IO) {
                try {
                    val rootDirs = listOf(downloadsDir, documentsDir, draftsDir)
                    val allFiles = mutableListOf<File>()
                    
                    fun scanDir(dir: File) {
                        val files = dir.listFiles() ?: return
                        for (f in files) {
                            if (f.isDirectory) {
                                if (!f.name.startsWith(".") && f.name != "Android") {
                                    scanDir(f)
                                }
                            } else {
                                allFiles.add(f)
                            }
                        }
                    }
                    
                    rootDirs.forEach { scanDir(it) }
                    
                    when (activeCategory) {
                        "images" -> {
                            val images = allFiles.filter {
                                val ext = it.name.substringAfterLast('.').lowercase()
                                ext in listOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
                            }
                            scannedImagesMap = images.groupBy { it.parentFile?.name ?: "Other" }
                        }
                        "documents" -> {
                            scannedDocuments = allFiles.filter {
                                val ext = it.name.substringAfterLast('.').lowercase()
                                ext in listOf("pdf", "docx", "doc", "xlsx", "xls", "csv", "pptx", "ppt", "txt", "md")
                            }.sortedByDescending { it.lastModified() }
                        }
                        "archives" -> {
                            scannedArchives = allFiles.filter {
                                val ext = it.name.substringAfterLast('.').lowercase()
                                ext in listOf("zip", "rar", "7z")
                            }.sortedByDescending { it.lastModified() }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isScanningCategory = false
                }
            }
        }
    }

    // Standard active folder lists
    val currentFiles = remember(currentDirectory, hasPermission) {
        if (!hasPermission && activeRootType != "Private Drafts") {
            emptyList()
        } else {
            try {
                val list = currentDirectory.listFiles()
                if (list != null) {
                    list.toList().sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showBottomSheet = true },
                containerColor = OmniColors.Accent,
                contentColor = Color.White,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .size(48.dp)
                    .padding(bottom = 8.dp, end = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Quick Actions Menu",
                    modifier = Modifier.size(24.dp)
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

            // 1. Storage Usage Meter Card (Google Files style)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = OmniColors.Surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Storage & Memory",
                            fontWeight = FontWeight.Bold,
                            color = OmniColors.TextPrimary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${storageStats.first} GB used of ${storageStats.second} GB",
                            style = MaterialTheme.typography.bodySmall,
                            color = OmniColors.TextMuted,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { storageStats.third },
                        color = OmniColors.Accent,
                        trackColor = OmniColors.Border,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Google Files style Quick Category shortcuts
            Text(
                text = "Categories",
                fontWeight = FontWeight.Bold,
                color = OmniColors.TextPrimary,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val categories = listOf(
                    Triple("images", "🖼️ Images", OmniColors.ImgPurple),
                    Triple("documents", "📄 Documents", OmniColors.DocBlue),
                    Triple("archives", "📦 Archives", OmniColors.ArcCyan)
                )
                categories.forEach { (catName, label, color) ->
                    val isSelected = activeCategory == catName
                    val bg = if (isSelected) color.copy(alpha = 0.15f) else OmniColors.Surface
                    val borderCol = if (isSelected) color else OmniColors.Border

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(bg)
                            .border(1.dp, borderCol, RoundedCornerShape(12.dp))
                            .clickable {
                                if (hasPermission) {
                                    activeCategory = if (isSelected) null else catName
                                    selectedAlbumName = null
                                } else {
                                    requestStoragePermission()
                                }
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) color else OmniColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Check Permission layout
            if (!hasPermission && activeRootType != "Private Drafts") {
                SectionHeader(title = "Local File Explorer")
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = OmniColors.Surface),
                    border = BorderStroke(1.dp, OmniColors.Border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📁 Storage Access Needed", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = OmniColors.TextPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "To browse public folders like Downloads and open documents offline, OmniSuite requires storage permission.",
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
                            Text("Grant Storage Access")
                        }
                    }
                }
            } else {
                // 3. Dynamic Category content panels OR general file explorer list
                if (isScanningCategory) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = OmniColors.Accent)
                    }
                } else if (activeCategory == "images") {
                    // Images - Folder-based Album Grid View
                    if (selectedAlbumName == null) {
                        // Album List view
                        if (scannedImagesMap.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                Text("No albums found in storage.", color = OmniColors.TextMuted)
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 80.dp)
                            ) {
                                items(scannedImagesMap.keys.toList()) { album ->
                                    val photos = scannedImagesMap[album] ?: emptyList()
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = OmniColors.Surface),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedAlbumName = album }
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            val firstPhoto = photos.firstOrNull()
                                            if (firstPhoto != null) {
                                                AsyncImage(
                                                    model = firstPhoto,
                                                    contentDescription = album,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(100.dp)
                                                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(100.dp)
                                                        .background(OmniColors.Surface2),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("Empty")
                                                }
                                            }
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(
                                                    text = album,
                                                    fontWeight = FontWeight.Bold,
                                                    color = OmniColors.TextPrimary,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${photos.size} items",
                                                    fontSize = 10.sp,
                                                    color = OmniColors.TextMuted
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Photos Grid inside specific album view
                        val albumPhotos = scannedImagesMap[selectedAlbumName] ?: emptyList()
                        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                IconButton(onClick = { selectedAlbumName = null }) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Back",
                                        tint = OmniColors.Accent
                                    )
                                }
                                Text(
                                    text = selectedAlbumName!!,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = OmniColors.TextPrimary
                                )
                            }
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 80.dp),
                                modifier = Modifier.fillMaxWidth().weight(1f)
                            ) {
                                items(albumPhotos) { file ->
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clickable { onOpenFile(Uri.fromFile(file).toString()) }
                                    ) {
                                        AsyncImage(
                                            model = file,
                                            contentDescription = file.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (activeCategory == "documents") {
                    // Documents listing
                    if (scannedDocuments.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text("No documents found.", color = OmniColors.TextMuted)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 80.dp),
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        ) {
                            items(scannedDocuments) { file ->
                                FileExplorerRow(file = file, onClick = { onOpenFile(Uri.fromFile(file).toString()) })
                            }
                        }
                    }
                } else if (activeCategory == "archives") {
                    // ZIP archives listing
                    if (scannedArchives.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text("No archives found.", color = OmniColors.TextMuted)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 80.dp),
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        ) {
                            items(scannedArchives) { file ->
                                FileExplorerRow(file = file, onClick = { onOpenFile(Uri.fromFile(file).toString()) })
                            }
                        }
                    }
                } else {
                    // Standard directory file explorer layout
                    SectionHeader(title = "Local Storage Folders")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple("Downloads", downloadsDir, "📥"),
                            Triple("Documents", downloadsDir, "📄"),
                            Triple("Private Drafts", draftsDir, "📁")
                        ).forEach { (label, dir, icon) ->
                            val isSelected = activeRootType == label
                            val chipBg = if (isSelected) OmniColors.Accent.copy(alpha = 0.15f) else OmniColors.Surface
                            val chipText = if (isSelected) OmniColors.Accent else OmniColors.TextMuted
                            val chipBorder = if (isSelected) OmniColors.Accent.copy(alpha = 0.4f) else OmniColors.Border

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(chipBg)
                                    .border(1.dp, chipBorder, RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (hasPermission || label == "Private Drafts") {
                                            activeRootType = label
                                            currentDirectoryPath = dir.absolutePath
                                        } else {
                                            requestStoragePermission()
                                        }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(icon, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = label,
                                        color = chipText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val currentParent = currentDirectory.parentFile
                    val isAtRoot = currentDirectory == downloadsDir || currentDirectory == documentsDir || currentDirectory == draftsDir || currentParent == null

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(OmniColors.Surface)
                            .border(1.dp, OmniColors.Border, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isAtRoot) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Navigate Up",
                                tint = OmniColors.Accent,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable {
                                        val parent = currentDirectory.parentFile
                                        if (parent != null) {
                                            currentDirectoryPath = parent.absolutePath
                                        }
                                    }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Text(
                            text = "Path: ${currentDirectory.name}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = OmniColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (currentFiles.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Empty folder or directory.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OmniColors.TextMuted
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(currentFiles) { file ->
                                FileExplorerRow(
                                    file = file,
                                    onClick = {
                                        if (file.isDirectory) {
                                            currentDirectoryPath = file.absolutePath
                                        } else {
                                            onOpenFile(Uri.fromFile(file).toString())
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Sheet quick actions drawer
        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
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
                        text = "Quick Actions",
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
                                    showBottomSheet = false
                                    onSelectFileForType("any")
                                }
                            }
                        }
                    )
                    
                    Divider(color = OmniColors.Border, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
                    
                    BottomSheetItem(
                        icon = "📷",
                        title = "Use Device Camera",
                        description = "Scan document or barcode dynamically",
                        onClick = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                if (!sheetState.isVisible) {
                                    showBottomSheet = false
                                    onNavigateToScanToPdf()
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun FileExplorerRow(
    file: File,
    onClick: () -> Unit,
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
    val dateText = formatFileDate(file.lastModified())

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
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = "Open",
            tint = OmniColors.TextMuted
        )
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

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    val exp = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 3)
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, exp.toDouble()), units[exp])
}

private fun formatFileDate(time: Long): String {
    val df = SimpleDateFormat("MMM dd, yyyy", Locale.US)
    return df.format(Date(time))
}

private fun getFileEmoji(file: File): String {
    if (file.isDirectory) return "📁"
    val name = file.name.lowercase()
    return when {
        name.endsWith(".pdf") -> "📋"
        name.endsWith(".docx") || name.endsWith(".doc") -> "📝"
        name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".csv") -> "📊"
        name.endsWith(".pptx") || name.endsWith(".ppt") -> "🖼️"
        name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") -> "📦"
        name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp") || name.endsWith(".gif") -> "🖼"
        name.endsWith(".txt") || name.endsWith(".md") -> "📄"
        else -> "📄"
    }
}

@Composable
private fun getFileIconBg(file: File): Color {
    if (file.isDirectory) return Color(0x1F3B82F6)
    val name = file.name.lowercase()
    return when {
        name.endsWith(".pdf") -> OmniColors.PdfRedBg
        name.endsWith(".docx") || name.endsWith(".doc") -> OmniColors.DocBlueBg
        name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".csv") -> OmniColors.XlsGreenBg
        name.endsWith(".pptx") || name.endsWith(".ppt") -> Color(0x1FF59E0B)
        name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") -> OmniColors.ArcCyanBg
        name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp") || name.endsWith(".gif") -> OmniColors.ImgPurpleBg
        else -> OmniColors.Surface2
    }
}
