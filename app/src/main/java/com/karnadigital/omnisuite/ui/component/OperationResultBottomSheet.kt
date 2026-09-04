package com.karnadigital.omnisuite.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import coil.compose.AsyncImage
import com.karnadigital.omnisuite.ui.theme.OmniColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationResultBottomSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    title: String = "Operation Completed",
    fileName: String? = null,
    fileUri: String? = null,
    fileUris: List<String> = emptyList(),
    fileSize: Long = 0L,
    mimeType: String? = null,
    textResult: String? = null,
    onOpenFile: ((String) -> Unit)? = null,
    onOpenSequentialImages: ((List<String>) -> Unit)? = null
) {
    if (!show) return

    val context = LocalContext.current
    var currentFileName by remember(fileName) { mutableStateOf(fileName ?: "Processed_File") }
    var currentFileUri by remember(fileUri) { mutableStateOf(fileUri ?: "") }
    var showRenameDialog by remember { mutableStateOf(false) }
    var resolvedSize by remember(currentFileUri, fileSize) { mutableStateOf(fileSize) }

    LaunchedEffect(fileUri, fileSize) {
        if (resolvedSize <= 0L && !fileUri.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val uri = Uri.parse(fileUri)
                    if (uri.scheme == "content") {
                        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                                if (idx != -1) {
                                    val sz = cursor.getLong(idx)
                                    if (sz > 0) resolvedSize = sz
                                }
                            }
                        }
                    }
                    if (resolvedSize <= 0L) {
                        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { fd ->
                            if (fd.length > 0) resolvedSize = fd.length
                        }
                    }
                } catch (e: Exception) {
                    // Ignore fallback
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = OmniColors.Surface,
        contentColor = OmniColors.TextPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = OmniColors.Border) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon header indicator (Standard Accent Green for success completions)
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = OmniColors.TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (!textResult.isNullOrBlank()) {
                // TEXT/DECODED PAYLOAD VIEW
                Text(
                    text = "Parsed Result Content:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = OmniColors.TextMuted,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(OmniColors.Surface2)
                        .border(1.dp, OmniColors.Border, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = textResult,
                        fontSize = 14.sp,
                        color = OmniColors.TextPrimary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons for plain text results
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("OmniSuite Scanned Content", textResult)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OmniColors.Accent)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, textResult)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Scanned Text"))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OmniColors.Surface2, contentColor = OmniColors.TextPrimary)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share", fontWeight = FontWeight.Bold)
                    }
                }

                if (textResult.startsWith("http://", ignoreCase = true) || textResult.startsWith("https://", ignoreCase = true)) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            try {
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(textResult))
                                context.startActivity(browserIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to open link: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Browse URL", fontWeight = FontWeight.Bold)
                    }
                }
            } else if (fileUris.isNotEmpty()) {
                // MULTI-IMAGE RESULTS VIEW
                Text(
                    text = "Generated Images (${fileUris.size}):",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = OmniColors.TextMuted,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    fileUris.forEachIndexed { index, uriStr ->
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, OmniColors.Border, RoundedCornerShape(12.dp))
                                .clickable {
                                    if (onOpenFile != null) {
                                        onOpenFile(uriStr)
                                        onDismiss()
                                    }
                                }
                        ) {
                            AsyncImage(
                                model = uriStr,
                                contentDescription = "Image ${index + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                                modifier = Modifier.align(Alignment.TopStart)
                            ) {
                                Text(
                                    text = "#${index + 1}",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (onOpenSequentialImages != null) {
                    Button(
                        onClick = {
                            onOpenSequentialImages(fileUris)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OmniColors.Accent)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View All ${fileUris.size} Images Sequentially", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Button(
                    onClick = {
                        try {
                            val uriList = ArrayList(fileUris.map { Uri.parse(it) })
                            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                type = "image/*"
                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share All Images"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Share error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OmniColors.Surface2, contentColor = OmniColors.TextPrimary),
                    border = BorderStroke(1.dp, OmniColors.Border)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share All Images", fontWeight = FontWeight.Bold)
                }
            } else if (!fileUri.isNullOrBlank()) {
                // FILE DETAILS CARD
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = OmniColors.Surface2),
                    border = BorderStroke(1.dp, OmniColors.Border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(OmniColors.Accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    mimeType?.contains("pdf") == true -> Icons.Default.PictureAsPdf
                                    mimeType?.startsWith("image/") == true -> Icons.Default.Image
                                    mimeType?.startsWith("text/") == true -> Icons.Default.Description
                                    mimeType?.contains("zip") == true -> Icons.Default.FolderZip
                                    else -> Icons.Default.InsertDriveFile
                                },
                                contentDescription = null,
                                tint = OmniColors.Accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentFileName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = OmniColors.TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Size: ${formatFileSize(resolvedSize)} • Format: ${mimeType?.substringAfter('/')?.uppercase() ?: "UNKNOWN"}",
                                fontSize = 12.sp,
                                color = OmniColors.TextMuted
                            )
                        }

                        IconButton(onClick = { showRenameDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Rename File",
                                tint = OmniColors.TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Standard Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (onOpenFile != null) {
                        Button(
                            onClick = {
                                onOpenFile(fileUri)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OmniColors.Accent)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View", fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            try {
                                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(fileUri), mimeType ?: "*/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(openIntent, "Open File with..."))
                            } catch (e: Exception) {
                                Toast.makeText(context, "No compatible app found to open this file.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OmniColors.Surface2, contentColor = OmniColors.TextPrimary),
                        border = BorderStroke(1.dp, OmniColors.Border)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            try {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = mimeType ?: "*/*"
                                    putExtra(Intent.EXTRA_STREAM, Uri.parse(fileUri))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share File via"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OmniColors.Surface2, contentColor = OmniColors.TextPrimary),
                        border = BorderStroke(1.dp, OmniColors.Border)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Done dismissal trigger
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Done",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = OmniColors.TextMuted
                )
            }
        }
    }

    if (showRenameDialog) {
        var newNameInput by remember { mutableStateOf(currentFileName) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename File", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newNameInput,
                    onValueChange = { newNameInput = it },
                    label = { Text("New File Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newNameInput.isNotBlank()) {
                            val oldUri = currentFileUri
                            val parsed = Uri.parse(oldUri)
                            if (parsed.scheme == "file" || parsed.scheme == null) {
                                val oldFile = java.io.File(parsed.path ?: oldUri)
                                if (oldFile.exists() && oldFile.isFile) {
                                    val oldExt = oldFile.extension
                                    val cleanName = if (newNameInput.contains(".")) newNameInput else if (oldExt.isNotBlank()) "$newNameInput.$oldExt" else newNameInput
                                    val newFile = java.io.File(oldFile.parentFile, cleanName)
                                    if (oldFile.renameTo(newFile)) {
                                        currentFileName = newFile.name
                                        currentFileUri = Uri.fromFile(newFile).toString()
                                        Toast.makeText(context, "File renamed successfully!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                currentFileName = newNameInput
                                Toast.makeText(context, "File name updated!", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.toDouble())).toInt()
    return String.format("%.2f %s", size / Math.pow(1024.toDouble(), digitGroups.toDouble()), units[digitGroups])
}
