package com.karnadigital.omnisuite.feature.viewer

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karnadigital.omnisuite.core.util.UriCacheUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.karnadigital.omnisuite.feature.viewer.ImageViewerScreen
import java.io.File

sealed class DispatcherState {
    object Loading : DispatcherState()
    data class Success(val cachedPath: String, val fileType: FileType) : DispatcherState()
    data class Error(val message: String) : DispatcherState()
}

enum class FileType {
    PDF, TXT, DOCX, XLSX, PPTX, IMAGE, CSV, ARCHIVE
}

/**
 * Main dispatcher viewer screen that parses an incoming SAF document content Uri,
 * copies it safely to a localized cache file on a background IO worker thread,
 * automatically evaluates its type, and directs to the matching sub-viewer.
 */
@Composable
fun ViewerDispatcherScreen(
    fileUri: String?,
    onBack: () -> Unit
) {
    if (fileUri.isNullOrEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            ErrorCard(
                message = "No document URI or path was provided.",
                onBack = onBack
            )
        }
        return
    }

    val context = LocalContext.current
    var state by remember { mutableStateOf<DispatcherState>(DispatcherState.Loading) }

    LaunchedEffect(fileUri) {
        try {
            val parsedUri = Uri.parse(fileUri)
            val cachedFile = UriCacheUtils.cacheUriToFile(context, parsedUri)
            if (cachedFile != null && cachedFile.exists()) {
                val fileType = determineFileType(context, fileUri, cachedFile)
                if (fileType != null) {
                    state = DispatcherState.Success(cachedFile.absolutePath, fileType)
                } else {
                    state = DispatcherState.Error("This file format is not supported by OmniSuite.")
                }
            } else {
                // If it's a direct absolute path to an existing local file, we can fall back to checking it directly
                val directFile = File(fileUri)
                if (directFile.exists() && directFile.isFile) {
                    val fileType = determineFileType(context, fileUri, directFile)
                    if (fileType != null) {
                        state = DispatcherState.Success(directFile.absolutePath, fileType)
                    } else {
                        state = DispatcherState.Error("This file format is not supported by OmniSuite.")
                    }
                } else {
                    state = DispatcherState.Error("Unable to load document. The file stream could not be isolated.")
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            state = DispatcherState.Error("File permission has expired or was denied. Please re-open this file from the storage browser to grant fresh system permissions.")
        } catch (e: Exception) {
            e.printStackTrace()
            state = DispatcherState.Error("System error during document ingestion: ${e.localizedMessage}")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val currentState = state) {
            is DispatcherState.Loading -> {
                LoadingIndicator()
            }
            is DispatcherState.Success -> {
                when (currentState.fileType) {
                    FileType.PDF -> PdfViewerScreen(fileUri = currentState.cachedPath, onBack = onBack)
                    FileType.TXT -> TxtViewerScreen(fileUri = currentState.cachedPath, onBack = onBack)
                    FileType.DOCX -> DocxViewerScreen(fileUri = currentState.cachedPath, onBack = onBack)
                    FileType.XLSX -> XlsxViewerScreen(fileUri = currentState.cachedPath, onBack = onBack)
                    FileType.PPTX -> PptxViewerScreen(fileUri = currentState.cachedPath, onBack = onBack)
                    FileType.IMAGE -> ImageViewerScreen(fileUri = currentState.cachedPath, onBack = onBack)
                    FileType.CSV -> XlsxViewerScreen(fileUri = currentState.cachedPath, onBack = onBack)
                    FileType.ARCHIVE -> ArchiveViewerScreen(fileUri = currentState.cachedPath, onBack = onBack)
                }
            }
            is DispatcherState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    ErrorCard(
                        message = currentState.message,
                        onBack = onBack
                    )
                }
            }
        }
    }
}

private fun determineFileType(context: Context, originalUriString: String, cachedFile: File): FileType? {
    try {
        val parsedUri = Uri.parse(originalUriString)
        val mimeType = context.contentResolver.getType(parsedUri)?.lowercase()

        if (mimeType != null) {
            when {
                mimeType == "application/pdf" -> return FileType.PDF
                mimeType == "text/plain" -> return FileType.TXT
                mimeType.contains("word") || mimeType == "application/msword" || mimeType.contains("wordprocessingml") -> return FileType.DOCX
                mimeType.contains("excel") || mimeType == "application/vnd.ms-excel" || mimeType.contains("spreadsheetml") -> return FileType.XLSX
                mimeType.contains("powerpoint") || mimeType.contains("presentation") || mimeType.contains("presentationml") -> return FileType.PPTX
                mimeType.startsWith("image/") -> return FileType.IMAGE
                mimeType == "text/csv" || mimeType == "text/comma-separated-values" -> return FileType.CSV
                mimeType == "application/zip" || mimeType == "application/x-zip-compressed" -> return FileType.ARCHIVE
            }
        }
    } catch (e: Exception) {
        // Fallback to name-based extension matching if ContentResolver throws
        e.printStackTrace()
    }

    // Name-based extension check (handles content queries with display name as well as direct paths)
    val nameToCheck = cachedFile.name.lowercase()
    return when {
        nameToCheck.endsWith(".pdf") -> FileType.PDF
        nameToCheck.endsWith(".txt") -> FileType.TXT
        nameToCheck.endsWith(".docx") || nameToCheck.endsWith(".doc") -> FileType.DOCX
        nameToCheck.endsWith(".xlsx") || nameToCheck.endsWith(".xls") -> FileType.XLSX
        nameToCheck.endsWith(".pptx") || nameToCheck.endsWith(".ppt") -> FileType.PPTX
        nameToCheck.endsWith(".png") || nameToCheck.endsWith(".jpg") || nameToCheck.endsWith(".jpeg") || nameToCheck.endsWith(".webp") || nameToCheck.endsWith(".gif") || nameToCheck.endsWith(".bmp") -> FileType.IMAGE
        nameToCheck.endsWith(".csv") -> FileType.CSV
        nameToCheck.endsWith(".zip") -> FileType.ARCHIVE
        else -> null
    }
}

@Composable
fun LoadingIndicator() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 4.dp,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Buffering Offline Stream...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Safely caching document to local sandbox storage.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ErrorCard(
    message: String,
    onBack: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning Icon",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Format Unrecognized",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "We currently support PDF, TXT, DOCX, XLSX, PPTX, CSV and standard images (JPEG, PNG, WEBP, GIF, BMP).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back icon",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Return to Workspace")
            }
        }
    }
}

