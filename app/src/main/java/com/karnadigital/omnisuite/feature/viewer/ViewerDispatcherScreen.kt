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
import com.karnadigital.omnisuite.di.coreEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.hilt.navigation.compose.hiltViewModel
import com.karnadigital.omnisuite.feature.home.HomeScreenViewModel

sealed class DispatcherState {
    object Loading : DispatcherState()
    data class Success(val cachedPath: String, val fileType: FileType) : DispatcherState()
    data class Error(val message: String) : DispatcherState()
}

enum class FileType {
    PDF, TXT, DOCX, XLSX, PPTX, PPT_LEGACY, IMAGE, CSV, ARCHIVE, DOC_LEGACY, XLS_LEGACY
}

private fun getMimeTypeFromFileType(fileType: FileType): String {
    return when (fileType) {
        FileType.PDF -> "application/pdf"
        FileType.TXT -> "text/plain"
        FileType.DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        FileType.XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        FileType.PPTX -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        FileType.IMAGE -> "image/*"
        FileType.CSV -> "text/csv"
        FileType.ARCHIVE -> "application/zip"
        FileType.DOC_LEGACY -> "application/msword"
        FileType.XLS_LEGACY -> "application/vnd.ms-excel"
        FileType.PPT_LEGACY -> "application/vnd.ms-powerpoint"
    }
}

@Composable
fun ViewerDispatcherScreen(
    fileUri: String?,
    onOpenFile: (String) -> Unit = {},
    onNavigate: (String) -> Unit = {},
    onNavigateImageTool: (String, Int) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    viewModel: HomeScreenViewModel = hiltViewModel()
) {
    if (fileUri.isNullOrEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            ErrorCard(
                title = "Invalid Document",
                message = "No document URI or path was provided.",
                onBack = onBack
            )
        }
        return
    }

    val context = LocalContext.current
    val uriCacheUtils = coreEntryPoint(context).uriCacheUtils()
    val officeConverter = coreEntryPoint(context).officeConverter()
    var state by remember { mutableStateOf<DispatcherState>(DispatcherState.Loading) }

    LaunchedEffect(fileUri) {
        try {
            if (fileUri != null && fileUri.contains("|")) {
                state = DispatcherState.Success(fileUri, FileType.IMAGE)
                return@LaunchedEffect
            }
            val parsedUri = Uri.parse(fileUri)
            uriCacheUtils.takePersistablePermission(parsedUri)

            val cachedFile = uriCacheUtils.cacheUriToFile(parsedUri)
            if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
                val fileType = determineFileType(context, fileUri, cachedFile)
                if (fileType != null) {
                    state = DispatcherState.Success(cachedFile.absolutePath, fileType)
                    val fileName = getFileNameFromUri(context, parsedUri) ?: cachedFile.name
                    val fileSize = cachedFile.length()
                    val mimeType = getMimeTypeFromFileType(fileType)
                    viewModel.addRecentFile(fileUri, fileName, mimeType, fileSize)
                } else {
                    state = DispatcherState.Error("Unsupported File Format: OmniSuite does not support this file type.")
                }
            } else {
                val pathToCheck = parsedUri.path ?: fileUri
                val directFile = File(pathToCheck)
                if (directFile.exists() && directFile.isFile && directFile.length() > 0) {
                    val fileType = determineFileType(context, fileUri, directFile)
                    if (fileType != null) {
                        state = DispatcherState.Success(directFile.absolutePath, fileType)
                        val fileName = directFile.name
                        val fileSize = directFile.length()
                        val mimeType = getMimeTypeFromFileType(fileType)
                        viewModel.addRecentFile(fileUri, fileName, mimeType, fileSize)
                    } else {
                        state = DispatcherState.Error("Unsupported File Format: OmniSuite does not support this file type.")
                    }
                } else {
                    if (fileUri.startsWith("content://")) {
                        state = DispatcherState.Error("File Moved or Permission Expired: The original document stream could not be accessed. The file may have been moved, deleted, or its access permission was revoked.")
                    } else {
                        state = DispatcherState.Error("File Moved or Deleted: The file could not be found at $pathToCheck.")
                    }
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            state = DispatcherState.Error("Permission Revoked or Expired: Storage access permission has expired. Please re-open this file from the file picker to grant fresh permissions.")
        } catch (e: Exception) {
            e.printStackTrace()
            state = DispatcherState.Error("Document Read Error: Unable to access document (${e.localizedMessage}).")
        }
    }

    val onToolAction: (ViewerTool) -> Unit = { tool ->
        val currentPath = (state as? DispatcherState.Success)?.cachedPath ?: ""
        when (tool) {
            is ViewerTool.Navigate -> onNavigate(tool.route)
            is ViewerTool.NavigateImageTool -> onNavigateImageTool(tool.fileUri, tool.tab)
            is ViewerTool.ExportPdf -> { /* handled by caller via SAF launcher */ }
            else -> handleViewerToolAction(tool, currentPath, context, onNavigate, onNavigateImageTool, officeConverter)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val currentState = state) {
            is DispatcherState.Loading -> LoadingIndicator()
            is DispatcherState.Success -> {
                when (currentState.fileType) {
                    FileType.PDF -> PdfViewerScreen(
                        fileUri = currentState.cachedPath,
                        onBack = onBack,
                        onToolAction = onToolAction
                    )
                    FileType.TXT -> TxtViewerScreen(
                        fileUri = currentState.cachedPath,
                        onBack = onBack,
                        onToolAction = onToolAction
                    )
                    FileType.DOCX, FileType.DOC_LEGACY -> DocxViewerScreen(
                        fileUri = currentState.cachedPath,
                        onBack = onBack,
                        onToolAction = onToolAction
                    )
                    FileType.XLSX, FileType.CSV, FileType.XLS_LEGACY -> XlsxViewerScreen(
                        fileUri = currentState.cachedPath,
                        onBack = onBack,
                        onToolAction = onToolAction
                    )
                    FileType.PPTX, FileType.PPT_LEGACY -> PptxViewerScreen(
                        fileUri = currentState.cachedPath,
                        onBack = onBack,
                        onToolAction = onToolAction
                    )
                    FileType.IMAGE -> ImageViewerScreen(
                        fileUri = currentState.cachedPath,
                        onBack = onBack,
                        onToolAction = onToolAction
                    )
                    FileType.ARCHIVE -> ArchiveViewerScreen(
                        fileUri = currentState.cachedPath,
                        onOpenFile = onOpenFile,
                        onBack = onBack
                    )
                }
            }
            is DispatcherState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    ErrorCard(
                        title = "Document Load Error",
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
        if (cachedFile.exists() && cachedFile.length() > 0) {
            val bytes = ByteArray(8)
            java.io.FileInputStream(cachedFile).use { fis -> fis.read(bytes) }
            val hex = bytes.joinToString("") { String.format("%02X", it) }

            if (hex.startsWith("25504446")) return FileType.PDF
            if (hex.startsWith("89504E47")) return FileType.IMAGE
            if (hex.startsWith("FFD8FF")) return FileType.IMAGE
            if (hex.startsWith("47494638")) return FileType.IMAGE

            if (hex.startsWith("52494646")) {
                val fullBytes = ByteArray(12)
                java.io.FileInputStream(cachedFile).use { fis -> fis.read(fullBytes) }
                val fullHex = fullBytes.joinToString("") { String.format("%02X", it) }
                if (fullHex.endsWith("57454250")) return FileType.IMAGE
            }

            if (hex.startsWith("504B")) {
                val originalName = getFileNameFromUri(context, Uri.parse(originalUriString))?.lowercase() ?: cachedFile.name.lowercase()
                val mimeType = try {
                    context.contentResolver.getType(Uri.parse(originalUriString))?.lowercase()
                } catch (e: Exception) { null }

                if (originalName.endsWith(".zip") ||
                    mimeType == "application/zip" ||
                    mimeType == "application/x-zip-compressed" ||
                    mimeType == "application/x-zip") {
                    return FileType.ARCHIVE
                }

                try {
                    java.util.zip.ZipFile(cachedFile).use { zip ->
                        val entries = zip.entries()
                        var isDocx = false
                        var isPptx = false
                        var isXlsx = false
                        var count = 0
                        while (entries.hasMoreElements() && count < 20) {
                            val entry = entries.nextElement()
                            val name = entry.name
                            if (name.startsWith("word/")) isDocx = true
                            if (name.startsWith("ppt/")) isPptx = true
                            if (name.startsWith("xl/")) isXlsx = true
                            if (name == "[Content_Types].xml" && originalName.endsWith(".pptx")) isPptx = true
                            if (name == "[Content_Types].xml" && originalName.endsWith(".docx")) isDocx = true
                            if (name == "[Content_Types].xml" && originalName.endsWith(".xlsx")) isXlsx = true
                            count++
                        }
                        return when {
                            isDocx -> FileType.DOCX
                            isPptx -> FileType.PPTX
                            isXlsx -> FileType.XLSX
                            else -> FileType.ARCHIVE
                        }
                    }
                } catch (e: Exception) {
                    return FileType.ARCHIVE
                }
            }

            if (hex.startsWith("D0CF11E0A1B11AE1")) {
                val originalName = getFileNameFromUri(context, Uri.parse(originalUriString))?.lowercase() ?: ""
                return when {
                    originalName.endsWith(".doc") -> FileType.DOC_LEGACY
                    originalName.endsWith(".xls") -> FileType.XLS_LEGACY
                    originalName.endsWith(".ppt") -> FileType.PPT_LEGACY
                    else -> FileType.DOC_LEGACY
                }
            }
        }
    } catch (e: Exception) { e.printStackTrace() }

    try {
        val parsedUri = Uri.parse(originalUriString)
        val mimeType = context.contentResolver.getType(parsedUri)?.lowercase()
        val originalName = getFileNameFromUri(context, parsedUri)?.lowercase() ?: ""

        if (mimeType != null) {
            when {
                mimeType == "application/pdf" -> return FileType.PDF
                mimeType == "text/plain" -> return FileType.TXT
                mimeType.contains("word") || mimeType == "application/msword" || mimeType.contains("wordprocessingml") -> {
                    return if (originalName.endsWith(".doc") || mimeType == "application/msword") FileType.DOC_LEGACY else FileType.DOCX
                }
                mimeType.contains("excel") || mimeType == "application/vnd.ms-excel" || mimeType.contains("spreadsheetml") -> {
                    return if (originalName.endsWith(".xls") || mimeType == "application/vnd.ms-excel") FileType.XLS_LEGACY else FileType.XLSX
                }
                mimeType.contains("powerpoint") || mimeType.contains("presentation") || mimeType.contains("presentationml") -> {
                    return if (originalName.endsWith(".ppt") || mimeType == "application/vnd.ms-powerpoint") FileType.PPT_LEGACY else FileType.PPTX
                }
                mimeType.startsWith("image/") -> return FileType.IMAGE
                mimeType == "text/csv" || mimeType == "text/comma-separated-values" -> return FileType.CSV
                mimeType == "application/zip" || mimeType == "application/x-zip-compressed" || mimeType == "application/x-zip" -> return FileType.ARCHIVE
            }
        }
    } catch (e: Exception) { e.printStackTrace() }

    val nameToCheck = cachedFile.name.lowercase()
    return when {
        nameToCheck.endsWith(".pdf") -> FileType.PDF
        nameToCheck.endsWith(".txt") -> FileType.TXT
        nameToCheck.endsWith(".docx") -> FileType.DOCX
        nameToCheck.endsWith(".doc") -> FileType.DOC_LEGACY
        nameToCheck.endsWith(".xlsx") -> FileType.XLSX
        nameToCheck.endsWith(".xls") -> FileType.XLS_LEGACY
        nameToCheck.endsWith(".pptx") -> FileType.PPTX
        nameToCheck.endsWith(".ppt") -> FileType.PPT_LEGACY
        nameToCheck.endsWith(".png") || nameToCheck.endsWith(".jpg") || nameToCheck.endsWith(".jpeg") ||
                nameToCheck.endsWith(".webp") || nameToCheck.endsWith(".gif") || nameToCheck.endsWith(".bmp") -> FileType.IMAGE
        nameToCheck.endsWith(".csv") -> FileType.CSV
        nameToCheck.endsWith(".zip") -> FileType.ARCHIVE
        nameToCheck.endsWith(".py") || nameToCheck.endsWith(".kt") || nameToCheck.endsWith(".java") ||
                nameToCheck.endsWith(".json") || nameToCheck.endsWith(".xml") || nameToCheck.endsWith(".html") ||
                nameToCheck.endsWith(".css") || nameToCheck.endsWith(".js") || nameToCheck.endsWith(".gradle") ||
                nameToCheck.endsWith(".sh") || nameToCheck.endsWith(".bat") || nameToCheck.endsWith(".cpp") ||
                nameToCheck.endsWith(".c") || nameToCheck.endsWith(".h") || nameToCheck.endsWith(".md") ||
                nameToCheck.endsWith(".properties") -> FileType.TXT
        else -> {
            try {
                if (cachedFile.exists() && cachedFile.length() > 0) {
                    val length = Math.min(cachedFile.length(), 4096L).toInt()
                    val buffer = ByteArray(length)
                    java.io.FileInputStream(cachedFile).use { fis ->
                        var bytesRead = 0
                        while (bytesRead < length) {
                            val read = fis.read(buffer, bytesRead, length - bytesRead)
                            if (read == -1) break
                            bytesRead += read
                        }
                    }
                    val hasNullBytes = (0 until length).any { buffer[it] == 0.toByte() }
                    if (!hasNullBytes) return FileType.TXT
                }
            } catch (e: Exception) { e.printStackTrace() }
            null
        }
    }
}

@Composable
fun LoadingIndicator() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
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
    title: String = "Format Unrecognized",
    message: String,
    onBack: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
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
                text = title,
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
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back icon", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Return to Workspace")
            }
        }
    }
}

private fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var name: String? = null
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) name = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
    if (name == null) {
        name = uri.path
        val lastSlash = name?.lastIndexOf('/') ?: -1
        if (lastSlash != -1) name = name?.substring(lastSlash + 1)
    }
    return name
}
