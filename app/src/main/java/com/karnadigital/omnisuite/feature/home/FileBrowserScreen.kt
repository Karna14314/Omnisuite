package com.karnadigital.omnisuite.feature.home

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.core.util.UriCacheUtils
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class ExplorerNode {
    data class Directory(val name: String, val path: String, val type: DirectoryType) : ExplorerNode()
    data class LocalFile(val file: File) : ExplorerNode()
    data class MediaStoreFile(val id: Long, val name: String, val uriString: String, val size: Long, val dateModified: Long, val mimeType: String) : ExplorerNode()
}

enum class DirectoryType {
    ROOT, SANDBOX, PUBLIC_DOCS, PUBLIC_SUBFOLDER
}

/**
 * Premium Zero-Dependency In-App File Explorer and Document Picker Manager.
 * Unifies sandboxed private workspace browsing and public Documents/OmniSuite MediaStore query logs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    viewModel: HomeScreenViewModel = hiltViewModel(),
    onOpenFile: (String) -> Unit
) {
    val context = LocalContext.current
    var currentDirType by remember { mutableStateOf(DirectoryType.ROOT) }
    var currentSubfolderName by remember { mutableStateOf("") } // Used when in PUBLIC_SUBFOLDER
    var currentPath by remember { mutableStateOf(listOf("Root")) }
    
    var localFilesList by remember { mutableStateOf<List<File>>(emptyList()) }
    var mediaStoreFilesMap by remember { mutableStateOf<Map<String, List<ExplorerNode.MediaStoreFile>>>(emptyMap()) }
    var isLoadingFiles by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Reusable Storage Access Framework picker launcher
    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                try {
                    // Cache the file immediately to local sandbox!
                    val cachedFile = UriCacheUtils.cacheUriToFile(context, it)
                    if (cachedFile != null && cachedFile.exists()) {
                        val fileName = getFileName(context, it) ?: cachedFile.name
                        val fileSize = getFileSize(context, it)
                        val mimeType = context.contentResolver.getType(it) ?: getMimeFromName(fileName)
                        
                        viewModel.addRecentFile(
                            fileUri = Uri.fromFile(cachedFile).toString(), // Save local cache file URI!
                            fileName = fileName,
                            mimeType = mimeType,
                            fileSize = fileSize
                        )
                        onOpenFile(Uri.fromFile(cachedFile).toString())
                    } else {
                        Toast.makeText(context, "Failed to import file.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Import error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Refresh function loading local sandbox files and MediaStore public files
    val refreshFiles = {
        isLoadingFiles = true
        // 1. Sandbox files walk
        val sandboxDir = context.filesDir
        localFilesList = sandboxDir.listFiles()?.filter { !it.isDirectory }?.toList() ?: emptyList()

        // 2. Public Documents walk via MediaStore
        val tempMap = mutableMapOf<String, MutableList<ExplorerNode.MediaStoreFile>>()
        try {
            val resolver = context.contentResolver
            val uri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.RELATIVE_PATH
            )
            val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("Documents/OmniSuite%")
            
            resolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol)
                    val mime = cursor.getString(mimeCol) ?: getMimeFromName(name)
                    val size = cursor.getLong(sizeCol)
                    val date = cursor.getLong(dateCol) * 1000L // convert sec to ms
                    val relPath = cursor.getString(pathCol)
                    
                    // Extract subfolder category (e.g. Documents/OmniSuite/PDF -> PDF)
                    val cleanPath = relPath.trimEnd('/')
                    val category = cleanPath.substringAfterLast("OmniSuite/", "Exports").trim()
                    
                    val fileUri = ContentUris.withAppendedId(uri, id).toString()
                    val mediaFile = ExplorerNode.MediaStoreFile(id, name, fileUri, size, date, mime)
                    
                    if (!tempMap.containsKey(category)) {
                        tempMap[category] = mutableListOf()
                    }
                    tempMap[category]?.add(mediaFile)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaStoreFilesMap = tempMap
        isLoadingFiles = false
    }

    LaunchedEffect(currentDirType) {
        refreshFiles()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. Breadcrumbs Path Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            currentPath.forEachIndexed { index, name ->
                if (index > 0) {
                    Text(" > ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
                Text(
                    text = name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (index == currentPath.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable {
                        // Backwards navigation
                        if (index == 0) {
                            currentDirType = DirectoryType.ROOT
                            currentPath = listOf("Root")
                        } else if (index == 1 && currentDirType == DirectoryType.PUBLIC_SUBFOLDER) {
                            currentDirType = DirectoryType.PUBLIC_DOCS
                            currentPath = listOf("Root", "Public Documents")
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { refreshFiles() }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh Files", modifier = Modifier.size(16.dp))
            }
        }

        // Main Explorer Body
        Box(modifier = Modifier.weight(1f)) {
            if (isLoadingFiles) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                }
            } else {
                when (currentDirType) {
                    DirectoryType.ROOT -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // SAF Quick Pickers grid at the top
                            item {
                                Text("Quick Pickers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    QuickOpenCard("PDF", "📕", Color(0xFFEF4444), modifier = Modifier.weight(1f)) { pickLauncher.launch(arrayOf("application/pdf")) }
                                    QuickOpenCard("Word", "📝", Color(0xFF3B82F6), modifier = Modifier.weight(1f)) { pickLauncher.launch(arrayOf("application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) }
                                    QuickOpenCard("Excel", "📊", Color(0xFF10B981), modifier = Modifier.weight(1f)) { pickLauncher.launch(arrayOf("application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/csv")) }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    QuickOpenCard("Slides", "🖼️", Color(0xFFF59E0B), modifier = Modifier.weight(1f)) { pickLauncher.launch(arrayOf("application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation")) }
                                    QuickOpenCard("Image", "📷", Color(0xFF8B5CF6), modifier = Modifier.weight(1f)) { pickLauncher.launch(arrayOf("image/*")) }
                                    QuickOpenCard("Text", "📄", Color(0xFF64748B), modifier = Modifier.weight(1f)) { pickLauncher.launch(arrayOf("text/plain")) }
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Storage Trees", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }

                            // Folders
                            item {
                                FolderRowCard(title = "Sandbox Drafts", subtitle = "Secure offline app-private workspace", icon = "📁") {
                                    currentDirType = DirectoryType.SANDBOX
                                    currentPath = listOf("Root", "Sandbox Drafts")
                                }
                            }
                            item {
                                FolderRowCard(title = "Public Documents", subtitle = "Shared exports inside Documents/OmniSuite", icon = "📂") {
                                    currentDirType = DirectoryType.PUBLIC_DOCS
                                    currentPath = listOf("Root", "Public Documents")
                                }
                            }
                        }
                    }
                    DirectoryType.SANDBOX -> {
                        if (localFilesList.isEmpty()) {
                            EmptyDirectoryPlaceholder("No sandbox drafts found.", "Create or save documents inside the app sandbox to view them here.") {
                                currentDirType = DirectoryType.ROOT
                                currentPath = listOf("Root")
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(localFilesList) { file ->
                                    FileItemRow(
                                        name = file.name,
                                        size = file.length(),
                                        date = file.lastModified(),
                                        mimeType = getMimeFromName(file.name),
                                        fileUri = Uri.fromFile(file).toString(),
                                        onOpenFile = onOpenFile,
                                        onDelete = {
                                            file.delete()
                                            refreshFiles()
                                            Toast.makeText(context, "Draft deleted", Toast.LENGTH_SHORT).show()
                                        },
                                        onShare = {
                                            shareLocalFile(context, file)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    DirectoryType.PUBLIC_DOCS -> {
                        val activeCategories = mediaStoreFilesMap.keys.toList()
                        if (activeCategories.isEmpty()) {
                            EmptyDirectoryPlaceholder("No exports found.", "Processed files inside PDF tools, Image lab, and QR generator will appear here.") {
                                currentDirType = DirectoryType.ROOT
                                currentPath = listOf("Root")
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(activeCategories) { category ->
                                    val count = mediaStoreFilesMap[category]?.size ?: 0
                                    FolderRowCard(title = "$category Exports", subtitle = "$count files exported offline", icon = "📁") {
                                        currentSubfolderName = category
                                        currentDirType = DirectoryType.PUBLIC_SUBFOLDER
                                        currentPath = listOf("Root", "Public Documents", "$category Exports")
                                    }
                                }
                            }
                        }
                    }
                    DirectoryType.PUBLIC_SUBFOLDER -> {
                        val categoryFiles = mediaStoreFilesMap[currentSubfolderName] ?: emptyList()
                        if (categoryFiles.isEmpty()) {
                            EmptyDirectoryPlaceholder("No files in this category.", "Exports inside this section appear empty.") {
                                currentDirType = DirectoryType.PUBLIC_DOCS
                                currentPath = listOf("Root", "Public Documents")
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(categoryFiles) { node ->
                                    FileItemRow(
                                        name = node.name,
                                        size = node.size,
                                        date = node.dateModified,
                                        mimeType = node.mimeType,
                                        fileUri = node.uriString,
                                        onOpenFile = onOpenFile,
                                        onDelete = {
                                            // Request content resolver to delete
                                            try {
                                                context.contentResolver.delete(Uri.parse(node.uriString), null, null)
                                                refreshFiles()
                                                Toast.makeText(context, "Export deleted", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Delete action failed", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onShare = {
                                            shareContentUri(context, Uri.parse(node.uriString), node.mimeType)
                                        }
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

@Composable
fun QuickOpenCard(
    title: String,
    iconText: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(iconText, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun FolderRowCard(
    title: String,
    subtitle: String,
    icon: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun FileItemRow(
    name: String,
    size: Long,
    date: Long,
    mimeType: String,
    fileUri: String,
    onOpenFile: (String) -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    val extension = name.substringAfterLast('.', "").lowercase()
    val isSupported = isFormatSupported(extension, mimeType)
    
    val (typeLabel, iconText, themeColor) = when {
        mimeType.contains("pdf", ignoreCase = true) -> Triple("PDF", "📄", Color(0xFFEF4444))
        mimeType.contains("sheet", ignoreCase = true) || mimeType.contains("excel", ignoreCase = true) -> Triple("Excel", "📊", Color(0xFF10B981))
        mimeType.contains("word", ignoreCase = true) || mimeType.contains("document", ignoreCase = true) -> Triple("Word", "📝", Color(0xFF3B82F6))
        mimeType.contains("presentation", ignoreCase = true) || mimeType.contains("powerpoint", ignoreCase = true) -> Triple("Slides", "🖼️", Color(0xFFF59E0B))
        mimeType.startsWith("image/") -> Triple("Image", "📷", Color(0xFF8B5CF6))
        mimeType == "application/zip" || mimeType == "application/x-zip-compressed" -> Triple("ZIP", "🗜️", Color(0xFF06B6D4))
        else -> Triple("Text", "📄", Color(0xFF64748B))
    }

    val formattedTime = remember(date) {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        sdf.format(Date(date))
    }

    var showMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isSupported) 1.0f else 0.5f)
            .clickable {
                if (isSupported) {
                    onOpenFile(fileUri)
                } else {
                    // Show helpful unsupported dialogue
                    showUnsupportedDialog(context, name, fileUri, mimeType)
                }
            }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(themeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(iconText, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(formatSize(size), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("•", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Text(formattedTime, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = {
                            showMenu = false
                            onShare()
                        },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyDirectoryPlaceholder(
    title: String,
    description: String,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("🏜️", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(20.dp))
                TextButton(onClick = onBack) {
                    Text("Return to Storage Root")
                }
            }
        }
    }
}

private fun isFormatSupported(ext: String, mime: String): Boolean {
    val e = ext.lowercase()
    if (e == "pdf" || e == "docx" || e == "doc" || e == "xlsx" || e == "xls" || 
        e == "pptx" || e == "ppt" || e == "zip" || e == "txt" || e == "csv" || 
        e == "png" || e == "jpg" || e == "jpeg" || e == "webp" || e == "gif" || e == "bmp" ||
        e == "py" || e == "kt" || e == "java" || e == "json" || e == "xml" || e == "html" ||
        e == "css" || e == "js" || e == "gradle" || e == "sh" || e == "bat" || e == "cpp" ||
        e == "c" || e == "h" || e == "md" || e == "properties") return true
    
    val m = mime.lowercase()
    if (m.contains("pdf") || m.contains("word") || m.contains("msword") || 
        m.contains("excel") || m.contains("spreadsheet") || m.contains("powerpoint") || 
        m.contains("presentation") || m.contains("zip") || m.contains("text/plain") || 
        m.startsWith("image/")) return true
        
    return false
}

private fun showUnsupportedDialog(context: Context, name: String, fileUri: String, mimeType: String) {
    try {
        val uri = Uri.parse(fileUri)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(viewIntent, "Open Unknown Format"))
    } catch (e: Exception) {
        android.app.AlertDialog.Builder(context)
            .setTitle("Unsupported Document")
            .setMessage("OmniSuite's offline viewers do not natively support this file format ($name). Would you like to share it to another app on your device?")
            .setPositiveButton("Share Externally") { _, _ ->
                shareContentUri(context, Uri.parse(fileUri), mimeType)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

private fun shareLocalFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        shareContentUri(context, uri, getMimeFromName(file.name))
    } catch (e: Exception) {
        Toast.makeText(context, "Sharing failed", Toast.LENGTH_SHORT).show()
    }
}

private fun shareContentUri(context: Context, uri: Uri, mimeType: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Document"))
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to share: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

private fun getMimeFromName(name: String): String {
    return when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "txt" -> "text/plain"
        "csv" -> "text/csv"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "zip" -> "application/zip"
        else -> "*/*"
    }
}

private fun formatSize(bytes: Long): String {
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
