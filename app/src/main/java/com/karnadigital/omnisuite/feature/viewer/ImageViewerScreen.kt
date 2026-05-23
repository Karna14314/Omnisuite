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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Build
import java.io.File
import androidx.compose.foundation.clickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage

/**
 * Aesthetic, high-performance offline Image Viewer screen.
 * Supports pinch-to-zoom, panning, double-tap reset, image sharing, and viewing file metadata.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    fileUri: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val imageFile = remember(fileUri) { File(fileUri) }
    val fileName = remember(fileUri) { imageFile.name }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset += offsetChange * scale
    }

    var showInfoDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = fileName,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        try {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/*"
                                putExtra(Intent.EXTRA_STREAM, Uri.fromFile(imageFile))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Image"))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Image",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Image Details",
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
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ImageActionColumnButton(icon = Icons.Default.OpenInNew, title = "Open in...") {
                        try {
                            val file = File(fileUri)
                            val fileUriProvider = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(fileUriProvider, "image/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(openIntent, "Open Image In"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }

                    ImageActionColumnButton(icon = Icons.Default.Print, title = "Print") {
                        coroutineScope.launch {
                            val tempPdfFile = File(context.cacheDir, "temp_print_${System.currentTimeMillis()}.pdf")
                            try {
                                withContext(Dispatchers.IO) {
                                    val pdf = com.tom_roush.pdfbox.pdmodel.PDDocument()
                                    val page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
                                    pdf.addPage(page)
                                    val contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(pdf, page)
                                    val bitmap = android.graphics.BitmapFactory.decodeFile(fileUri)
                                    val pdImage = com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(pdf, bitmap)
                                    
                                    val pageWidth = page.mediaBox.width
                                    val pageHeight = page.mediaBox.height
                                    val imageWidth = bitmap.width.toFloat()
                                    val imageHeight = bitmap.height.toFloat()
                                    val ratio = Math.min((pageWidth - 80f) / imageWidth, (pageHeight - 80f) / imageHeight)
                                    val drawWidth = imageWidth * ratio
                                    val drawHeight = imageHeight * ratio
                                    val x = (pageWidth - drawWidth) / 2f
                                    val y = (pageHeight - drawHeight) / 2f
                                    
                                    contentStream.drawImage(pdImage, x, y, drawWidth, drawHeight)
                                    contentStream.close()
                                    pdf.save(tempPdfFile)
                                    pdf.close()
                                    bitmap.recycle()
                                }
                                val printManager = context.getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
                                val jobName = "OmniSuite Image Print"
                                printManager.print(
                                    jobName,
                                    ImagePrintDocumentAdapter(context, tempPdfFile),
                                    null
                                )
                            } catch (e: Exception) {
                                Toast.makeText(context, "Print failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    ImageActionColumnButton(icon = Icons.Default.Share, title = "Share") {
                        try {
                            val file = File(fileUri)
                            val fileUriProvider = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/*"
                                putExtra(Intent.EXTRA_STREAM, fileUriProvider)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Image"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }

                    var showQuickToolsMenu by remember { mutableStateOf(false) }
                    Box {
                        ImageActionColumnButton(icon = Icons.Default.Build, title = "Quick Tools") {
                            showQuickToolsMenu = true
                        }
                        DropdownMenu(
                            expanded = showQuickToolsMenu,
                            onDismissRequest = { showQuickToolsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("📕 Convert to PDF format") },
                                onClick = {
                                    showQuickToolsMenu = false
                                    coroutineScope.launch {
                                        val tempPdfFile = File(context.cacheDir, "temp_conv_${System.currentTimeMillis()}.pdf")
                                        try {
                                            withContext(Dispatchers.IO) {
                                                val pdf = com.tom_roush.pdfbox.pdmodel.PDDocument()
                                                val page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
                                                pdf.addPage(page)
                                                val contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(pdf, page)
                                                val bitmap = android.graphics.BitmapFactory.decodeFile(fileUri)
                                                val pdImage = com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(pdf, bitmap)
                                                
                                                val pageWidth = page.mediaBox.width
                                                val pageHeight = page.mediaBox.height
                                                val imageWidth = bitmap.width.toFloat()
                                                val imageHeight = bitmap.height.toFloat()
                                                val ratio = Math.min((pageWidth - 80f) / imageWidth, (pageHeight - 80f) / imageHeight)
                                                val drawWidth = imageWidth * ratio
                                                val drawHeight = imageHeight * ratio
                                                val x = (pageWidth - drawWidth) / 2f
                                                val y = (pageHeight - drawHeight) / 2f
                                                
                                                contentStream.drawImage(pdImage, x, y, drawWidth, drawHeight)
                                                contentStream.close()
                                                pdf.save(tempPdfFile)
                                                pdf.close()
                                                bitmap.recycle()
                                            }
                                            // Save to public Documents/OmniSuite/
                                            val savedUri = com.karnadigital.omnisuite.core.util.FileOutputManager.saveToDefault(
                                                context = context,
                                                bytes = tempPdfFile.readBytes(),
                                                filename = File(fileUri).name.substringBeforeLast(".") + "_image.pdf",
                                                mimeType = "application/pdf",
                                                subfolder = ""
                                            )
                                            if (savedUri != null) {
                                                Toast.makeText(context, "Image successfully saved as PDF under Documents/OmniSuite!", Toast.LENGTH_LONG).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            if (tempPdfFile.exists()) tempPdfFile.delete()
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("ℹ️ View Image details") },
                                onClick = {
                                    showQuickToolsMenu = false
                                    showInfoDialog = true
                                }
                            )
                        }
                    }
                }
            }
        },
        containerColor = Color.Black // Cinematic dark viewport backdrop
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = if (scale > 1f) 1f else 2.5f
                            offset = Offset.Zero
                        }
                    )
                }
                .transformable(state = transformState),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageFile,
                contentDescription = "Loaded image view",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )
        }
    }

    if (showInfoDialog) {
        Dialog(onDismissRequest = { showInfoDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Image Metadata",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    MetadataRow(label = "Filename", value = fileName)
                    MetadataRow(label = "Absolute Path", value = imageFile.absolutePath)
                    MetadataRow(
                        label = "File Size",
                        value = formatFileSize(if (imageFile.exists()) imageFile.length() else 0L)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { showInfoDialog = false },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}

private @Composable
fun ImageActionColumnButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = title, tint = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(4.dp))
        Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

private class ImagePrintDocumentAdapter(private val context: Context, private val file: File) : PrintDocumentAdapter() {
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
        val info = PrintDocumentInfo.Builder("print_output.pdf")
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
        var input: java.io.InputStream? = null
        var output: java.io.OutputStream? = null
        try {
            input = java.io.FileInputStream(file)
            output = java.io.FileOutputStream(destination?.fileDescriptor)
            val buffer = ByteArray(1024)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } >= 0) {
                output.write(buffer, 0, bytesRead)
            }
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback?.onWriteFailed(e.localizedMessage)
        } finally {
            try { input?.close() } catch(e: Exception) {}
        }
    }
}

