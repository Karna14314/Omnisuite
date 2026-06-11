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
import java.io.File
import androidx.compose.foundation.clickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.karnadigital.omnisuite.core.util.FileOutputManager
import com.karnadigital.omnisuite.ui.component.OperationResultBottomSheet
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Save

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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState

/**
 * Aesthetic, high-performance offline Image Viewer screen.
 * Supports pinch-to-zoom, panning, double-tap reset, image sharing, and viewing file metadata.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ImageViewerScreen(
    fileUri: String,
    onBack: () -> Unit,
    onEditInImageLab: (String) -> Unit,
    viewModel: ImageViewerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeUriList by remember(fileUri) {
        mutableStateOf(
            if (fileUri.contains("|")) {
                fileUri.split("|").filter { it.isNotBlank() }
            } else {
                listOf(fileUri)
            }
        )
    }

    val pagerState = if (activeUriList.size > 1) {
        rememberPagerState(pageCount = { activeUriList.size })
    } else {
        null
    }

    val activeUriString = pagerState?.let { activeUriList.getOrNull(it.currentPage) } ?: activeUriList.firstOrNull() ?: fileUri
    val activeUri = remember(activeUriString) { Uri.parse(activeUriString) }
    val isContentUri = activeUriString.startsWith("content://") || activeUriString.startsWith("file://")
    val activeFileName = remember(activeUriString) {
        if (isContentUri) {
            activeUri.path?.substringAfterLast('/') ?: "extracted_image.png"
        } else {
            File(activeUriString).name
        }
    }
    val activeFile = remember(activeUriString) {
        if (isContentUri) null else File(activeUriString)
    }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset += offsetChange * scale
    }

    var showInfoDialog by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    var showResultSheet by remember { mutableStateOf(false) }
    var resultFileName by remember { mutableStateOf<String?>(null) }
    var resultFileUri by remember { mutableStateOf<String?>(null) }
    var resultMimeType by remember { mutableStateOf<String?>(null) }
    var resultFileSize by remember { mutableStateOf(0L) }

    // Offline editing states
    var editRotation by remember { mutableStateOf(0f) }
    var editSquareCrop by remember { mutableStateOf(false) }
    var editCompressQuality by remember { mutableStateOf(80f) }
    var editOutputFormat by remember { mutableStateOf("JPEG") }

    var imageWidth by remember { mutableStateOf(0) }
    var imageHeight by remember { mutableStateOf(0) }
    var customWidth by remember { mutableStateOf("") }
    var customHeight by remember { mutableStateOf("") }

    LaunchedEffect(showEditSheet, activeUriString) {
        if (showEditSheet) {
            withContext(Dispatchers.IO) {
                try {
                    val options = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    if (isContentUri) {
                        context.contentResolver.openInputStream(activeUri).use { stream ->
                            android.graphics.BitmapFactory.decodeStream(stream, null, options)
                        }
                    } else {
                        android.graphics.BitmapFactory.decodeFile(activeUriString, options)
                    }
                    imageWidth = options.outWidth
                    imageHeight = options.outHeight
                    customWidth = options.outWidth.toString()
                    customHeight = options.outHeight.toString()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (activeUriList.size > 1 && pagerState != null) {
                            "Image ${pagerState.currentPage + 1} of ${activeUriList.size}"
                        } else {
                            activeFileName
                        },
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
                                if (isContentUri) {
                                    putExtra(Intent.EXTRA_STREAM, activeUri)
                                } else {
                                    val providerUri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", activeFile!!)
                                    putExtra(Intent.EXTRA_STREAM, providerUri)
                                }
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
                            val fileUriProvider = if (isContentUri) {
                                activeUri
                            } else {
                                androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", activeFile!!)
                            }
                            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(fileUriProvider, "image/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(openIntent, "Open Image In"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }

                    ImageActionColumnButton(icon = Icons.Default.Edit, title = "Edit Image") {
                        editRotation = 0f
                        editSquareCrop = false
                        editCompressQuality = 80f
                        editOutputFormat = "JPEG"
                        showEditSheet = true
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
                                    
                                    val bitmap = if (isContentUri) {
                                        context.contentResolver.openInputStream(activeUri).use { stream ->
                                            android.graphics.BitmapFactory.decodeStream(stream)
                                        }
                                    } else {
                                        android.graphics.BitmapFactory.decodeFile(activeUriString)
                                    }
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
                            val fileUriProvider = if (isContentUri) {
                                activeUri
                            } else {
                                androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", activeFile!!)
                            }
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
                                                
                                                val bitmap = if (isContentUri) {
                                                    context.contentResolver.openInputStream(activeUri).use { stream ->
                                                        android.graphics.BitmapFactory.decodeStream(stream)
                                                    }
                                                } else {
                                                    android.graphics.BitmapFactory.decodeFile(activeUriString)
                                                }
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
                                                filename = activeFileName.substringBeforeLast(".") + "_image.pdf",
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
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            if (activeUriList.size > 1 && pagerState != null) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    val pageUri = activeUriList[pageIndex]
                    val pageModel = remember(pageUri) {
                        if (pageUri.startsWith("content://") || pageUri.startsWith("file://")) {
                            Uri.parse(pageUri)
                        } else {
                            File(pageUri)
                        }
                    }
                    var pageScale by remember { mutableStateOf(1f) }
                    var pageOffset by remember { mutableStateOf(Offset.Zero) }
                    val pageTransformState = rememberTransformableState { zoomChange, offsetChange, _ ->
                        pageScale = (pageScale * zoomChange).coerceIn(1f, 5f)
                        pageOffset += offsetChange * pageScale
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        pageScale = if (pageScale > 1f) 1f else 2.5f
                                        pageOffset = Offset.Zero
                                    }
                                )
                            }
                            .transformable(state = pageTransformState),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = pageModel,
                            contentDescription = "Loaded image view page ${pageIndex + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = pageScale,
                                    scaleY = pageScale,
                                    translationX = pageOffset.x,
                                    translationY = pageOffset.y
                                )
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
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
                        model = if (isContentUri) activeUri else File(fileUri),
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
                    MetadataRow(label = "Filename", value = activeFileName)
                    MetadataRow(label = "Path / Uri", value = activeUriString)
                    if (activeFile != null) {
                        MetadataRow(
                            label = "File Size",
                            value = formatFileSize(if (activeFile.exists()) activeFile.length() else 0L)
                        )
                    }
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

    if (showEditSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showEditSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Tools",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Image Lab Editor",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Advanced editing card redirect to Image Lab
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showEditSheet = false
                            onEditInImageLab(activeUriString)
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Open in Image Lab (Advanced)",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Use advanced batch tools, filters and resizing options",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(16.dp))

                // Section 1: Basic Editing Tools
                Text(
                    text = "Basic Corrections",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { editRotation = (editRotation + 90f) % 360f },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Rotate")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Rotate 90° (${editRotation.toInt()}°)")
                    }

                    FilterChip(
                        selected = editSquareCrop,
                        onClick = { editSquareCrop = !editSquareCrop },
                        label = { Text("1:1 Center Crop") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Crop,
                                contentDescription = "Crop 1:1",
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Section 2: Dimension Control
                Text(
                    text = "Pixel Resizer (Resolution)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Original: ${imageWidth}x${imageHeight} px",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = customWidth,
                        onValueChange = { customWidth = it.filter { char -> char.isDigit() } },
                        label = { Text("Width (px)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = customHeight,
                        onValueChange = { customHeight = it.filter { char -> char.isDigit() } },
                        label = { Text("Height (px)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        customWidth = imageWidth.toString()
                        customHeight = imageHeight.toString()
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Reset to Original")
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Section 3: Compressor Settings
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Compressor Quality",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${editCompressQuality.toInt()}%",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Slider(
                    value = editCompressQuality,
                    onValueChange = { editCompressQuality = it },
                    valueRange = 10f..100f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Section 4: Format conversion
                Text(
                    text = "Save/Transcode Format",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val formats = listOf("JPEG", "PNG", "WEBP")
                    formats.forEach { fmt ->
                        FilterChip(
                            selected = editOutputFormat == fmt,
                            onClick = { editOutputFormat = fmt },
                            label = { Text(fmt) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Apply Transformations Button
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isSaving = true
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    val originalBitmap = if (isContentUri) {
                                        context.contentResolver.openInputStream(activeUri).use { stream ->
                                            android.graphics.BitmapFactory.decodeStream(stream)
                                        }
                                    } else {
                                        android.graphics.BitmapFactory.decodeFile(activeUriString)
                                    } ?: throw Exception("Failed to decode bitmap")

                                    var bitmap = originalBitmap

                                    // Rotate
                                    if (editRotation != 0f) {
                                        val matrix = android.graphics.Matrix().apply { postRotate(editRotation) }
                                        val rotated = android.graphics.Bitmap.createBitmap(
                                            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                                        )
                                        if (rotated != bitmap) {
                                            bitmap.recycle()
                                            bitmap = rotated
                                        }
                                    }

                                    // Center Crop
                                    if (editSquareCrop) {
                                        val size = Math.min(bitmap.width, bitmap.height)
                                        val x = (bitmap.width - size) / 2
                                        val y = (bitmap.height - size) / 2
                                        val cropped = android.graphics.Bitmap.createBitmap(
                                            bitmap, x, y, size, size
                                        )
                                        if (cropped != bitmap) {
                                            bitmap.recycle()
                                            bitmap = cropped
                                        }
                                    }

                                    // Scale Resize
                                    val targetWidth = customWidth.toIntOrNull() ?: bitmap.width
                                    val targetHeight = customHeight.toIntOrNull() ?: bitmap.height
                                    if (targetWidth != bitmap.width || targetHeight != bitmap.height) {
                                        val scaled = android.graphics.Bitmap.createScaledBitmap(
                                            bitmap, targetWidth, targetHeight, true
                                        )
                                        if (scaled != bitmap) {
                                            bitmap.recycle()
                                            bitmap = scaled
                                        }
                                    }

                                    // Format Transcoding
                                    val format = when (editOutputFormat) {
                                        "PNG" -> android.graphics.Bitmap.CompressFormat.PNG
                                        "WEBP" -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                            android.graphics.Bitmap.CompressFormat.WEBP_LOSSY
                                        } else {
                                            android.graphics.Bitmap.CompressFormat.WEBP
                                        }
                                        else -> android.graphics.Bitmap.CompressFormat.JPEG
                                    }
                                    val mimeType = when (editOutputFormat) {
                                        "PNG" -> "image/png"
                                        "WEBP" -> "image/webp"
                                        else -> "image/jpeg"
                                    }
                                    val ext = editOutputFormat.lowercase(java.util.Locale.ROOT)

                                    val stream = java.io.ByteArrayOutputStream()
                                    bitmap.compress(format, editCompressQuality.toInt(), stream)
                                    val bytes = stream.toByteArray()
                                    bitmap.recycle()

                                    val newName = activeFileName.substringBeforeLast(".") + "_edited." + ext
                                    val savedUri = FileOutputManager.saveToDefault(
                                        context = context,
                                        bytes = bytes,
                                        filename = newName,
                                        mimeType = mimeType,
                                        subfolder = "Images"
                                    )
                                    Pair(savedUri, bytes.size.toLong())
                                }

                                val resultUri = result.first
                                val fileSize = result.second

                                if (resultUri != null) {
                                    val filename = resultUri.path?.substringAfterLast('/') ?: "edited_image.${editOutputFormat.lowercase()}"
                                    val mime = when (editOutputFormat) {
                                        "PNG" -> "image/png"
                                        "WEBP" -> "image/webp"
                                        else -> "image/jpeg"
                                    }
                                    viewModel.registerRecentFile(
                                        com.karnadigital.omnisuite.core.model.RecentFile(
                                            fileUri = resultUri.toString(),
                                            fileName = filename,
                                            mimeType = mime,
                                            fileSize = fileSize,
                                            lastOpened = System.currentTimeMillis(),
                                            isOperation = true
                                        )
                                    )

                                    resultFileName = filename
                                    resultFileUri = resultUri.toString()
                                    resultMimeType = mime
                                    resultFileSize = fileSize
                                    showResultSheet = true
                                    showEditSheet = false
                                    
                                    val newList = activeUriList.toMutableList()
                                    newList.add(resultUri.toString())
                                    activeUriList = newList
                                    pagerState?.scrollToPage(newList.size - 1)
                                } else {
                                    Toast.makeText(context, "Failed to save edited image", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(imageVector = Icons.Default.Save, contentDescription = "Save")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Apply Transformations",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    OperationResultBottomSheet(
        show = showResultSheet,
        onDismiss = { showResultSheet = false },
        title = "Image Saved Successfully",
        fileName = resultFileName,
        fileUri = resultFileUri,
        fileSize = resultFileSize,
        mimeType = resultMimeType
    )
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

