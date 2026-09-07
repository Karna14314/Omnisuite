package com.karnadigital.omnisuite.feature.pdf_tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfLockScreen(
    fileUri: String? = null,
    onBack: () -> Unit,
    viewModel: PdfToolsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.lockInputUri = it; try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {} }
    }

    LaunchedEffect(fileUri) { fileUri?.let { viewModel.lockInputUri = Uri.parse(it) } }

    Scaffold(topBar = { TopAppBar(title = { Text("Protect PDF (Encrypt)", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth().clickable { filePicker.launch(arrayOf("application/pdf")) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = if (viewModel.lockInputUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Text(if (viewModel.lockInputUri != null) "PDF Selected" else "Tap to select PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            if (viewModel.lockInputUri != null) {
                OutlinedTextField(value = viewModel.lockPassword, onValueChange = { viewModel.lockPassword = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
                Button(onClick = { viewModel.lockPdf() }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = !viewModel.isProcessing && viewModel.lockPassword.isNotBlank()) {
                    if (viewModel.isProcessing) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    else { Icon(Icons.Default.Lock, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Encrypt PDF") }
                }
            }
            if (viewModel.successMessage != null) Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f))) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50)); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.successMessage!!, color = Color(0xFF2E7D32)) } }
            if (viewModel.errorMessage != null) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfDecryptScreen(
    fileUri: String? = null,
    onBack: () -> Unit,
    viewModel: PdfToolsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.decryptInputUri = it; try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {} }
    }

    LaunchedEffect(fileUri) { fileUri?.let { viewModel.decryptInputUri = Uri.parse(it) } }

    Scaffold(topBar = { TopAppBar(title = { Text("Unlock PDF (Decrypt)", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth().clickable { filePicker.launch(arrayOf("application/pdf")) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.LockOpen, contentDescription = null, tint = if (viewModel.decryptInputUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Text(if (viewModel.decryptInputUri != null) "PDF Selected" else "Tap to select encrypted PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            if (viewModel.decryptInputUri != null) {
                OutlinedTextField(value = viewModel.decryptPassword, onValueChange = { viewModel.decryptPassword = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
                Button(onClick = { viewModel.decryptPdf() }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = !viewModel.isProcessing && viewModel.decryptPassword.isNotBlank()) {
                    if (viewModel.isProcessing) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    else { Icon(Icons.Default.LockOpen, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Remove Password") }
                }
            }
            if (viewModel.successMessage != null) Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f))) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50)); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.successMessage!!, color = Color(0xFF2E7D32)) } }
            if (viewModel.errorMessage != null) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfRedactScreen(onBack: () -> Unit, viewModel: PdfToolsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            viewModel.redactInputUri = it
            try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        }
    }

    val thumbnails = rememberPdfThumbnails(context, viewModel.redactInputUri)
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var redactionBoxes by remember { mutableStateOf<List<RedactionBox>>(emptyList()) }

    var dragStart by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }

    LaunchedEffect(viewModel.redactInputUri) {
        redactionBoxes = emptyList()
        currentPageIndex = 0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Visual Redact PDF", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (viewModel.redactInputUri != null) {
                        IconButton(onClick = { filePicker.launch(arrayOf("application/pdf")) }) {
                            Icon(Icons.Default.UploadFile, contentDescription = "Change PDF")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (viewModel.redactInputUri == null || thumbnails.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { filePicker.launch(arrayOf("application/pdf")) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(54.dp))
                        Text("Select PDF to Redact", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Touch and drag to blackout confidential text, numbers, or photos visually", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            } else {
                // Page Pager & Controls Header
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                                enabled = currentPageIndex > 0
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Prev")
                            }
                            Text(
                                text = "Page ${currentPageIndex + 1} / ${thumbnails.size}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { if (currentPageIndex < thumbnails.size - 1) currentPageIndex++ },
                                enabled = currentPageIndex < thumbnails.size - 1
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Next")
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    if (redactionBoxes.isNotEmpty()) {
                                        redactionBoxes = redactionBoxes.dropLast(1)
                                    }
                                },
                                enabled = redactionBoxes.isNotEmpty()
                            ) {
                                Text("Undo")
                            }
                            TextButton(
                                onClick = {
                                    redactionBoxes = redactionBoxes.filter { it.pageIndex != currentPageIndex }
                                },
                                enabled = redactionBoxes.any { it.pageIndex == currentPageIndex }
                            ) {
                                Text("Clear Page")
                            }
                        }
                    }
                }

                Text(
                    text = "👆 Drag your finger across confidential text or images to blackout:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )

                // Visual Page Canvas
                val currentBmp = thumbnails.getOrNull(currentPageIndex)
                if (currentBmp != null) {
                    val aspect = currentBmp.width.toFloat() / currentBmp.height.toFloat()
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspect)
                            .clip(RoundedCornerShape(8.dp)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(currentPageIndex) {
                                    val canvasW = size.width.toFloat()
                                    val canvasH = size.height.toFloat()
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            dragStart = offset
                                            dragCurrent = offset
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            dragCurrent = change.position
                                        },
                                        onDragEnd = {
                                            val start = dragStart
                                            val curr = dragCurrent
                                            if (start != null && curr != null && canvasW > 0f && canvasH > 0f) {
                                                val left = minOf(start.x, curr.x)
                                                val top = minOf(start.y, curr.y)
                                                val w = kotlin.math.abs(curr.x - start.x)
                                                val h = kotlin.math.abs(curr.y - start.y)

                                                if (w > 12f && h > 12f) {
                                                    val normX = (left / canvasW).coerceIn(0f, 1f)
                                                    val normY = (top / canvasH).coerceIn(0f, 1f)
                                                    val maxW = (1f - normX).coerceAtLeast(0f)
                                                    val maxH = (1f - normY).coerceAtLeast(0f)
                                                    val normW = (w / canvasW).coerceIn(0f, maxW)
                                                    val normH = (h / canvasH).coerceIn(0f, maxH)
                                                    redactionBoxes = redactionBoxes + RedactionBox(
                                                        pageIndex = currentPageIndex,
                                                        normX = normX,
                                                        normY = normY,
                                                        normWidth = normW,
                                                        normHeight = normH
                                                    )
                                                }
                                            }
                                            dragStart = null
                                            dragCurrent = null
                                        },
                                        onDragCancel = {
                                            dragStart = null
                                            dragCurrent = null
                                        }
                                    )
                                }
                        ) {
                            // 1. Draw Page Bitmap
                            drawImage(
                                image = currentBmp.asImageBitmap(),
                                dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
                            )

                            // 2. Draw Committed Redactions for Current Page
                            val pageBoxes = redactionBoxes.filter { it.pageIndex == currentPageIndex }
                            for (box in pageBoxes) {
                                val boxLeft = box.normX * size.width
                                val boxTop = box.normY * size.height
                                val boxW = box.normWidth * size.width
                                val boxH = box.normHeight * size.height
                                drawRect(
                                    color = Color.Black,
                                    topLeft = androidx.compose.ui.geometry.Offset(boxLeft, boxTop),
                                    size = androidx.compose.ui.geometry.Size(boxW, boxH)
                                )
                                drawRect(
                                    color = Color(0xFFEF4444),
                                    topLeft = androidx.compose.ui.geometry.Offset(boxLeft, boxTop),
                                    size = androidx.compose.ui.geometry.Size(boxW, boxH),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                                )
                            }

                            // 3. Draw Active Drag Rectangle
                            val start = dragStart
                            val curr = dragCurrent
                            if (start != null && curr != null) {
                                val left = minOf(start.x, curr.x)
                                val top = minOf(start.y, curr.y)
                                val w = kotlin.math.abs(curr.x - start.x)
                                val h = kotlin.math.abs(curr.y - start.y)
                                drawRect(
                                    color = Color.Black.copy(alpha = 0.7f),
                                    topLeft = androidx.compose.ui.geometry.Offset(left, top),
                                    size = androidx.compose.ui.geometry.Size(w, h)
                                )
                                drawRect(
                                    color = Color(0xFF2563EB),
                                    topLeft = androidx.compose.ui.geometry.Offset(left, top),
                                    size = androidx.compose.ui.geometry.Size(w, h),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                                )
                            }
                        }
                    }
                }

                // Status badge & Apply button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val pageBoxCount = redactionBoxes.count { it.pageIndex == currentPageIndex }
                    Text(
                        text = "$pageBoxCount on Page ${currentPageIndex + 1} (${redactionBoxes.size} total)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (redactionBoxes.isNotEmpty()) {
                        TextButton(onClick = { redactionBoxes = emptyList() }) {
                            Text("Clear All", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Button(
                    onClick = { viewModel.redactPdfWithBoxes(redactionBoxes) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !viewModel.isProcessing && redactionBoxes.isNotEmpty()
                ) {
                    if (viewModel.isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Block, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Apply ${redactionBoxes.size} Redaction${if (redactionBoxes.size != 1) "s" else ""}")
                    }
                }
            }

            if (viewModel.successMessage != null) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f))) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(viewModel.successMessage!!, color = Color(0xFF2E7D32))
                    }
                }
            }
            if (viewModel.errorMessage != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    }

    com.karnadigital.omnisuite.ui.component.OperationResultBottomSheet(
        show = viewModel.successUri != null,
        onDismiss = { viewModel.resetStatus() },
        title = "PDF Redaction Applied",
        fileName = viewModel.successName,
        fileUri = viewModel.successUri?.toString(),
        mimeType = "application/pdf"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfFlattenScreen(onBack: () -> Unit, viewModel: PdfToolsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.flattenInputUri = it; try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {} }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Flatten PDF", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth().clickable { filePicker.launch(arrayOf("application/pdf")) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.LayersClear, contentDescription = null, tint = if (viewModel.flattenInputUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Text(if (viewModel.flattenInputUri != null) "PDF Selected" else "Tap to select PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            if (viewModel.flattenInputUri != null) {
                Button(onClick = { viewModel.flattenPdf() }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = !viewModel.isProcessing) {
                    if (viewModel.isProcessing) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    else { Icon(Icons.Default.LayersClear, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Flatten PDF Forms & Annotations") }
                }
            }
            if (viewModel.successMessage != null) Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f))) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50)); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.successMessage!!, color = Color(0xFF2E7D32)) } }
            if (viewModel.errorMessage != null) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer) } }
        }
    }
}
