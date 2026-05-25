package com.karnadigital.omnisuite.feature.viewer

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.core.repository.RecentFileRepository
import com.karnadigital.omnisuite.core.util.FileOutputManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ArchiveViewerViewModel @Inject constructor(
    private val recentFileRepository: RecentFileRepository
) : ViewModel() {
    fun registerRecentFile(recent: RecentFile) {
        viewModelScope.launch(Dispatchers.IO) {
            recentFileRepository.insertRecentFile(recent)
        }
    }
}

data class ZipEntryInfo(
    val name: String,
    val size: Long,
    val compressedSize: Long,
    val isDirectory: Boolean,
    var isSelected: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveViewerScreen(
    fileUri: String, // Absolute path of cached zip file
    onOpenFile: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val viewModel: ArchiveViewerViewModel = hiltViewModel()

    var entries by remember { mutableStateOf<List<ZipEntryInfo>>(emptyList()) }
    var isLoadingEntries by remember { mutableStateOf(true) }
    var isExtracting by remember { mutableStateOf(false) }
    var extractProgress by remember { mutableStateOf("") }
    
    val zipFile = remember { File(fileUri) }
    val archiveName = remember { zipFile.name.removeSuffix(".zip") }


    // Ephemeral Purge Routine
    DisposableEffect(Unit) {
        onDispose {
            try {
                val extractionDir = File(context.cacheDir, "omnisuite_extracted_${archiveName}")
                if (extractionDir.exists()) {
                    extractionDir.deleteRecursively()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Load zip contents on start

    LaunchedEffect(fileUri) {
        withContext(Dispatchers.IO) {
            val list = mutableListOf<ZipEntryInfo>()
            try {
                java.util.zip.ZipFile(zipFile).use { zip ->
                    val elements = zip.entries()
                    while (elements.hasMoreElements()) {
                        val entry = elements.nextElement()
                        // Ignore empty macOS metadata directories / __MACOSX files
                        if (!entry.name.contains("__MACOSX") && !entry.name.startsWith(".")) {
                            list.add(
                                ZipEntryInfo(
                                    name = entry.name,
                                    size = entry.size,
                                    compressedSize = entry.compressedSize,
                                    isDirectory = entry.isDirectory
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            entries = list
            isLoadingEntries = false
        }
    }

    // Extraction operation
    val extractAction: (List<ZipEntryInfo>) -> Unit = { targets ->
        if (targets.isEmpty()) {
            Toast.makeText(context, "No files selected for extraction", Toast.LENGTH_SHORT).show()
        } else {
            isExtracting = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val zipInputStream = ZipInputStream(FileInputStream(zipFile))
                    var entry: ZipEntry? = zipInputStream.nextEntry
                    
                    val targetNames = targets.map { it.name }.toSet()

                    while (entry != null) {
                        val name = entry.name
                        if (targetNames.contains(name) && !entry.isDirectory) {
                            withContext(Dispatchers.Main) {
                                extractProgress = "Extracting ${name.substringAfterLast('/')}"
                            }

                            // Extract the bytes off-stream
                            val buffer = ByteArray(4096)
                            val outStream = java.io.ByteArrayOutputStream()
                            var len = zipInputStream.read(buffer)
                            while (len > 0) {
                                outStream.write(buffer, 0, len)
                                len = zipInputStream.read(buffer)
                            }
                            val fileBytes = outStream.toByteArray()

                            // Extract strictly to Ephemeral Cache
                            val cleanFileName = name.substringAfterLast('/')
                            val extractionDir = File(context.cacheDir, "omnisuite_extracted_${archiveName}")
                            if (!extractionDir.exists()) extractionDir.mkdirs()
                            
                            val tempFile = File(extractionDir, cleanFileName)
                            FileOutputStream(tempFile).use { fos ->
                                fos.write(fileBytes)
                            }
                        }
                        zipInputStream.closeEntry()
                        entry = zipInputStream.nextEntry
                    }
                    zipInputStream.close()

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Archive items successfully extracted to secure temporary viewer.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isExtracting = false
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = zipFile.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${entries.filter { !it.isDirectory }.size} files",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (entries.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                // Extract only selected files
                                val selected = entries.filter { it.isSelected && !it.isDirectory }
                                extractAction(selected)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Extract Selected")
                        }

                        Button(
                            onClick = {
                                // Extract all files
                                val allNonDirs = entries.filter { !it.isDirectory }
                                extractAction(allNonDirs)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Extract All")
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLoadingEntries) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFEF4444))
                }
            } else if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🗜️", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Empty ZIP Archive",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "No compatible entries were parsed in this ZIP folder.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    items(entries) { entry ->
                        val extension = entry.name.substringAfterLast('.', "").lowercase()
                        val iconChar = getExtensionIcon(extension, entry.isDirectory)
                        val iconBg = getExtensionColor(extension, entry.isDirectory)

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (!entry.isDirectory) {
                                            // Extract single file to cache and view once in-app directly!
                                            coroutineScope.launch(Dispatchers.IO) {
                                                try {
                                                    val zipInputStream = ZipInputStream(FileInputStream(zipFile))
                                                    var currentEntry: ZipEntry? = zipInputStream.nextEntry
                                                    while (currentEntry != null) {
                                                        if (currentEntry.name == entry.name) {
                                                            val cleanFileName = entry.name.substringAfterLast('/')
                                                            // Save in cache dir with unique prefix
                                                            val tempFile = File(context.cacheDir, "extracted_${System.currentTimeMillis()}_$cleanFileName")
                                                            
                                                            val buffer = ByteArray(4096)
                                                            FileOutputStream(tempFile).use { fos ->
                                                                var len = zipInputStream.read(buffer)
                                                                while (len > 0) {
                                                                    fos.write(buffer, 0, len)
                                                                    len = zipInputStream.read(buffer)
                                                                }
                                                            }
                                                            
                                                            withContext(Dispatchers.Main) {
                                                                onOpenFile(Uri.fromFile(tempFile).toString())
                                                            }
                                                            break
                                                        }
                                                        zipInputStream.closeEntry()
                                                        currentEntry = zipInputStream.nextEntry
                                                    }
                                                    zipInputStream.close()
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, "Error opening file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!entry.isDirectory) {
                                    Checkbox(
                                        checked = entry.isSelected,
                                        onCheckedChange = { selected ->
                                            entry.isSelected = selected
                                            entries = entries.toList()
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFFEF4444))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(iconBg.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(iconChar, fontSize = 14.sp)
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!entry.isDirectory) {
                                        Text(
                                            text = "${formatSize(entry.size)} (${calculateRatio(entry.size, entry.compressedSize)})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }

            // High premium unzipping progress dialog
            if (isExtracting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFFEF4444),
                                strokeWidth = 4.dp,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Extracting ZIP Archive...",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = extractProgress,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun resolveMimeType(fileName: String): String {
    return when (fileName.substringAfterLast('.').lowercase()) {
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "txt" -> "text/plain"
        "csv" -> "text/csv"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        else -> "*/*"
    }
}

private fun getExtensionIcon(ext: String, isDir: Boolean): String {
    if (isDir) return "📁"
    return when (ext) {
        "pdf" -> "📕"
        "docx", "doc" -> "📝"
        "xlsx", "xls" -> "📊"
        "pptx", "ppt" -> "🖼️"
        "txt" -> "📄"
        "csv" -> "📅"
        "png", "jpg", "jpeg", "webp" -> "📷"
        else -> "🗃️"
    }
}

private fun getExtensionColor(ext: String, isDir: Boolean): Color {
    if (isDir) return Color(0xFFE2E8F0)
    return when (ext) {
        "pdf" -> Color(0xFFEF4444)
        "docx", "doc" -> Color(0xFF3B82F6)
        "xlsx", "xls" -> Color(0xFF10B981)
        "pptx", "ppt" -> Color(0xFFF59E0B)
        "png", "jpg", "jpeg", "webp" -> Color(0xFF8B5CF6)
        else -> Color(0xFF64748B)
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}

private fun calculateRatio(size: Long, compressedSize: Long): String {
    if (size <= 0) return "0% ratio"
    val ratio = 100 - (compressedSize * 100 / size)
    return "$ratio% ratio"
}
