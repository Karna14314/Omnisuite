package com.karnadigital.omnisuite.feature.viewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

@Composable
fun ViewerActionColumnButton(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            title,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
fun ViewerQuickToolsMenu(
    fileUri: String,
    toolActions: List<Pair<ViewerTool, String>>,
    onToolClick: (ViewerTool) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ViewerActionColumnButton(
            icon = Icons.Default.Build,
            title = "Tools"
        ) { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            toolActions.forEach { (tool, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onToolClick(tool)
                    }
                )
            }
        }
    }
}

fun handleViewerToolAction(
    tool: ViewerTool,
    fileUri: String,
    context: Context,
    onNavigate: (String) -> Unit = {},
    onNavigateImageTool: (String, Int) -> Unit = { _, _ -> },
    officeConverter: com.karnadigital.omnisuite.core.engine.document.OfficeConverter? = null
) {
    when (tool) {
        is ViewerTool.OpenIn -> {
            try {
                val file = File(fileUri)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val mime = getMimeTypeForFile(fileUri)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Open In"))
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to open: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
        is ViewerTool.Print -> {
            try {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val file = File(fileUri)
                val uri = if (file.exists()) {
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                } else {
                    Uri.parse(fileUri)
                }
                val docName = file.name
                val mimeType = getMimeTypeForFile(fileUri)
                val isOfficeDoc = mimeType.contains("word") || mimeType.contains("excel") ||
                        mimeType.contains("powerpoint") || mimeType.contains("spreadsheet") ||
                        mimeType.contains("presentation") || mimeType.contains("officedocument")
                val adapter = if (isOfficeDoc && officeConverter != null) {
                    OfficeDocumentPrintAdapter(context, file, docName, officeConverter)
                } else {
                    GenericDocumentAdapter(context, uri, docName, mimeType)
                }
                printManager.print("OmniSuite Print", adapter, null)
            } catch (e: Exception) {
                Toast.makeText(context, "Print failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
        is ViewerTool.Share -> {
            try {
                val file = File(fileUri)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = getMimeTypeForFile(fileUri)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Document"))
            } catch (e: Exception) {
                Toast.makeText(context, "Share failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
        is ViewerTool.Navigate -> onNavigate(tool.route)
        is ViewerTool.NavigateImageTool -> onNavigateImageTool(tool.fileUri, tool.tab)
        is ViewerTool.ExportPdf -> { /* handled by caller via SAF launcher */ }
    }
}

private fun getMimeTypeForFile(fileUri: String): String {
    val lower = fileUri.lowercase()
    return when {
        lower.endsWith(".pdf") -> "application/pdf"
        lower.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        lower.endsWith(".doc") -> "application/msword"
        lower.endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        lower.endsWith(".xls") -> "application/vnd.ms-excel"
        lower.endsWith(".pptx") -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        lower.endsWith(".ppt") -> "application/vnd.ms-powerpoint"
        lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".json") || lower.endsWith(".xml") -> "text/plain"
        lower.endsWith(".csv") -> "text/csv"
        lower.endsWith(".png") -> "image/png"
        lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
        lower.endsWith(".webp") -> "image/webp"
        lower.endsWith(".gif") -> "image/gif"
        else -> "*/*"
    }
}

class GenericDocumentAdapter(
    private val context: Context,
    private val uri: Uri,
    private val documentName: String,
    private val mimeType: String
) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(documentName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .build()
        callback?.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        var input: InputStream? = null
        var output: OutputStream? = null
        try {
            input = context.contentResolver.openInputStream(uri)
            output = FileOutputStream(destination?.fileDescriptor)
            input?.copyTo(output)
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback?.onWriteFailed(e.localizedMessage)
        } finally {
            try { input?.close() } catch (_: Exception) {}
            try { output?.close() } catch (_: Exception) {}
        }
    }
}

class OfficeDocumentPrintAdapter(
    private val context: Context,
    private val file: File,
    private val documentName: String,
    private val officeConverter: com.karnadigital.omnisuite.core.engine.document.OfficeConverter
) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(documentName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .build()
        callback?.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        var input: InputStream? = null
        var output: OutputStream? = null
        var pdfFile: File? = null
        try {
            pdfFile = File(context.cacheDir, "print_temp_${System.currentTimeMillis()}.pdf")
            val lower = file.name.lowercase()
            when {
                lower.endsWith(".docx") -> kotlinx.coroutines.runBlocking { officeConverter.convertDocxToPdf(file, pdfFile) }
                lower.endsWith(".xlsx") -> kotlinx.coroutines.runBlocking { officeConverter.convertXlsxToPdf(file, pdfFile) }
                lower.endsWith(".pptx") -> kotlinx.coroutines.runBlocking { officeConverter.convertPptxToPdf(file, pdfFile, "text") }
                else -> kotlinx.coroutines.runBlocking { officeConverter.convertDocxToPdf(file, pdfFile) }
            }
            input = FileInputStream(pdfFile)
            output = FileOutputStream(destination?.fileDescriptor)
            input.copyTo(output)
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback?.onWriteFailed(e.localizedMessage)
        } finally {
            try { input?.close() } catch (_: Exception) {}
            try { output?.close() } catch (_: Exception) {}
            try { pdfFile?.delete() } catch (_: Exception) {}
        }
    }
}
