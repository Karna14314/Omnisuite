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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.core.content.ContextCompat
import com.karnadigital.omnisuite.ui.component.SectionHeader
import com.karnadigital.omnisuite.ui.theme.OmniColors
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Premium Files local directory explorer manager tab (Google Files style).
 * Supports permission warnings, dynamic file listings, public storage navigators,
 * dynamic breadcrumb chains, and document viewer intents.
 */
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

    // Standard public storage root paths
    val downloadsDir = remember { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) }
    val documentsDir = remember { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) }
    val draftsDir = remember { context.getExternalFilesDir(null) ?: context.filesDir }

    var activeRootType by rememberSaveable { mutableStateOf("Downloads") }
    var currentDirectoryPath by rememberSaveable { mutableStateOf(downloadsDir.absolutePath) }
    val currentDirectory = remember(currentDirectoryPath) { File(currentDirectoryPath) }

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

    // Refresh list of files dynamically when folder changes or permission changes
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
            Spacer(modifier = Modifier.height(10.dp))

            // 1. Google Files Navigation Shortcuts Row
            SectionHeader(title = "Storage Folders")
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple("Downloads", downloadsDir, "📥"),
                    Triple("Documents", downloadsDir, "📄"), // Let fallback folder be Downloads as documentsDir may be sandboxed
                    Triple("Private Drafts", draftsDir, "📁")
                ).forEach { (label, dir, icon) ->
                    val isSelected = activeRootType == label
                    val chipBg = if (isSelected) OmniColors.Accent.copy(alpha = 0.15f) else OmniColors.Surface2
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

            // Check Permission layout
            if (!hasPermission && activeRootType != "Private Drafts") {
                SectionHeader(title = "Local File Explorer")
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = OmniColors.Surface2),
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
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                // 2. Active Directory Navigation Breadcrumbs
                val currentParent = currentDirectory.parentFile
                val isAtRoot = currentDirectory == downloadsDir || currentDirectory == documentsDir || currentDirectory == draftsDir || currentParent == null

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(OmniColors.Surface2)
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

                // 3. Dynamic Local Files List
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

        // Bottom Sheet quick actions drawer (SAF picker + scanner)
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
    val iconColor = getFileIconColor(file)
    
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
            .background(OmniColors.Surface2)
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
    if (file.isDirectory) return Color(0x1F3B82F6) // Light blue
    val name = file.name.lowercase()
    return when {
        name.endsWith(".pdf") -> OmniColors.PdfRedBg
        name.endsWith(".docx") || name.endsWith(".doc") -> OmniColors.DocBlueBg
        name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".csv") -> OmniColors.XlsGreenBg
        name.endsWith(".pptx") || name.endsWith(".ppt") -> Color(0x1FF59E0B) // Light orange
        name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") -> OmniColors.ArcCyanBg
        name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp") || name.endsWith(".gif") -> OmniColors.ImgPurpleBg
        else -> OmniColors.Surface2
    }
}

@Composable
private fun getFileIconColor(file: File): Color {
    if (file.isDirectory) return Color(0xFF3B82F6) // Blue
    val name = file.name.lowercase()
    return when {
        name.endsWith(".pdf") -> OmniColors.PdfRed
        name.endsWith(".docx") || name.endsWith(".doc") -> OmniColors.DocBlue
        name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".csv") -> OmniColors.XlsGreen
        name.endsWith(".pptx") || name.endsWith(".ppt") -> Color(0xFFF59E0B)
        name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") -> OmniColors.ArcCyan
        name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp") || name.endsWith(".gif") -> OmniColors.ImgPurple
        else -> OmniColors.TextMuted
    }
}
