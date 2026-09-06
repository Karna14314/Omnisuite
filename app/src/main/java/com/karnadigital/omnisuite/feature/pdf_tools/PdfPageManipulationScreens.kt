package com.karnadigital.omnisuite.feature.pdf_tools

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberPdfThumbnails(context: Context, uri: Uri?): List<Bitmap> {
    var thumbnails by remember(uri) { mutableStateOf<List<Bitmap>>(emptyList()) }
    LaunchedEffect(uri) {
        if (uri == null) {
            thumbnails = emptyList()
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                pfd?.use { fd ->
                    val renderer = android.graphics.pdf.PdfRenderer(fd)
                    val list = mutableListOf<Bitmap>()
                    val count = renderer.pageCount
                    for (i in 0 until count) {
                        val page = renderer.openPage(i)
                        val w = (page.width / 3).coerceAtLeast(100)
                        val h = (page.height / 3).coerceAtLeast(100)
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bitmap)
                        canvas.drawColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        list.add(bitmap)
                    }
                    renderer.close()
                    thumbnails = list
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    return thumbnails
}

private fun parseRangeToSet(range: String): Set<Int> {
    val set = mutableSetOf<Int>()
    val parts = range.split(",")
    for (part in parts) {
        val trimmed = part.trim()
        if (trimmed.contains("-")) {
            val bounds = trimmed.split("-")
            val start = bounds.getOrNull(0)?.toIntOrNull()
            val end = bounds.getOrNull(1)?.toIntOrNull()
            if (start != null && end != null) {
                for (i in minOf(start, end)..maxOf(start, end)) {
                    if (i > 0) set.add(i - 1)
                }
            }
        } else {
            val num = trimmed.toIntOrNull()
            if (num != null && num > 0) set.add(num - 1)
        }
    }
    return set
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfRotateScreen(
    fileUri: String? = null,
    onBack: () -> Unit,
    viewModel: PdfToolsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            viewModel.rotateInputUri = it
            try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
        }
    }

    LaunchedEffect(fileUri) { fileUri?.let { viewModel.rotateInputUri = Uri.parse(it) } }

    val thumbnails = rememberPdfThumbnails(context, viewModel.rotateInputUri)
    // Map of page index to applied rotation angle in degrees (0, 90, 180, 270)
    var pageRotations by remember(thumbnails) { mutableStateOf<Map<Int, Int>>(emptyMap()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rotate PDF Pages", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (thumbnails.isNotEmpty()) {
                        IconButton(onClick = {
                            // Cycle all pages +90 degrees
                            pageRotations = (0 until thumbnails.size).associateWith { idx ->
                                ((pageRotations[idx] ?: 0) + 90) % 360
                            }
                        }) {
                            Icon(Icons.Default.RotateRight, contentDescription = "Rotate all +90°")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { filePicker.launch(arrayOf("application/pdf")) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.RotateRight,
                        contentDescription = null,
                        tint = if (viewModel.rotateInputUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = if (viewModel.rotateInputUri != null) "PDF Selected (${thumbnails.size} pages)" else "Tap to select PDF",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (thumbnails.isNotEmpty()) {
                Text("Quick Rotation Controls", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            pageRotations = (0 until thumbnails.size).associateWith { idx ->
                                ((pageRotations[idx] ?: 0) + 90) % 360
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("All +90°", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            pageRotations = (0 until thumbnails.size).associateWith { idx ->
                                ((pageRotations[idx] ?: 0) + 270) % 360
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.RotateLeft, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("All -90°", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            pageRotations = (0 until thumbnails.size).associateWith { idx ->
                                ((pageRotations[idx] ?: 0) + 180) % 360
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text("All 180°", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { pageRotations = emptyMap() },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text("Reset", fontSize = 12.sp)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = false,
                        onClick = {
                            val newMap = pageRotations.toMutableMap()
                            (0 until thumbnails.size).filter { it % 2 == 0 }.forEach { idx ->
                                newMap[idx] = ((newMap[idx] ?: 0) + 90) % 360
                            }
                            pageRotations = newMap
                        },
                        label = { Text("Odd Pages +90°", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = false,
                        onClick = {
                            val newMap = pageRotations.toMutableMap()
                            (0 until thumbnails.size).filter { it % 2 == 1 }.forEach { idx ->
                                newMap[idx] = ((newMap[idx] ?: 0) + 90) % 360
                            }
                            pageRotations = newMap
                        },
                        label = { Text("Even Pages +90°", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Interactive Page Rotation", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Tap page to cycle angle", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(thumbnails) { index, bitmap ->
                        val currentAngle = pageRotations[index] ?: 0
                        val isRotated = currentAngle > 0
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.75f)
                                .clickable {
                                    // Cycle angle: 0 -> 90 -> 180 -> 270 -> 0
                                    val nextAngle = (currentAngle + 90) % 360
                                    val newMap = pageRotations.toMutableMap()
                                    if (nextAngle == 0) {
                                        newMap.remove(index)
                                    } else {
                                        newMap[index] = nextAngle
                                    }
                                    pageRotations = newMap
                                },
                            border = BorderStroke(
                                if (isRotated) 2.5.dp else 1.dp,
                                if (isRotated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isRotated) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Page ${index + 1}",
                                    modifier = Modifier
                                        .fillMaxSize(0.9f)
                                        .rotate(currentAngle.toFloat()),
                                    contentScale = ContentScale.Fit
                                )

                                Surface(
                                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
                                    shape = CircleShape,
                                    color = if (isRotated) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.55f)
                                ) {
                                    Text(
                                        text = "${index + 1}${if (isRotated) " ($currentAngle°)" else ""}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                if (isRotated) {
                                    Surface(
                                        modifier = Modifier.align(Alignment.BottomStart).padding(2.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Icon(
                                            Icons.Default.RotateRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp).padding(2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                val rotatedCount = pageRotations.filter { it.value > 0 }.size
                Button(
                    onClick = {
                        val activeRotations = pageRotations.filter { it.value > 0 }
                        viewModel.rotatePdfPages(rotations = activeRotations)
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !viewModel.isProcessing && rotatedCount > 0
                ) {
                    if (viewModel.isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.RotateRight, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (rotatedCount > 0) "Save Rotated PDF ($rotatedCount pages changed)" else "Tap pages to rotate")
                    }
                }
            }

            if (viewModel.successMessage != null) Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f))) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50)); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.successMessage!!, color = Color(0xFF2E7D32)) } }
            if (viewModel.errorMessage != null) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReorderScreen(
    onBack: () -> Unit,
    viewModel: PdfToolsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.reorderInputUri = it; try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {} }
    }

    val thumbnails = rememberPdfThumbnails(context, viewModel.reorderInputUri)
    var pageOrder by remember(thumbnails) { mutableStateOf<List<Int>>(emptyList()) }

    LaunchedEffect(thumbnails) {
        if (thumbnails.isNotEmpty()) {
            pageOrder = (0 until thumbnails.size).toList()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Reorder Pages", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth().clickable { filePicker.launch(arrayOf("application/pdf")) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.SwapVert, contentDescription = null, tint = if (viewModel.reorderInputUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Text(if (viewModel.reorderInputUri != null) "PDF Selected" else "Tap to select PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }

            if (thumbnails.isNotEmpty() && pageOrder.size == thumbnails.size) {
                Text("Reorder pages using controls:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(pageOrder) { orderIndex, originalPageIdx ->
                        val bitmap = thumbnails.getOrNull(originalPageIdx)
                        Card(
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.7f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "Page ${originalPageIdx + 1}",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                    Surface(
                                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.6f)
                                    ) {
                                        Text(
                                            text = "P.${originalPageIdx + 1}",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (orderIndex > 0) {
                                                val list = pageOrder.toMutableList()
                                                val temp = list[orderIndex]
                                                list[orderIndex] = list[orderIndex - 1]
                                                list[orderIndex - 1] = temp
                                                pageOrder = list
                                            }
                                        },
                                        enabled = orderIndex > 0,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Move Left", modifier = Modifier.size(16.dp))
                                    }
                                    Text("${orderIndex + 1}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    IconButton(
                                        onClick = {
                                            if (orderIndex < pageOrder.size - 1) {
                                                val list = pageOrder.toMutableList()
                                                val temp = list[orderIndex]
                                                list[orderIndex] = list[orderIndex + 1]
                                                list[orderIndex + 1] = temp
                                                pageOrder = list
                                            }
                                        },
                                        enabled = orderIndex < pageOrder.size - 1,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Move Right", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        viewModel.reorderPageOrder = pageOrder.map { it + 1 }
                        viewModel.reorderPdfPages()
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !viewModel.isProcessing && pageOrder.isNotEmpty()
                ) {
                    if (viewModel.isProcessing) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    else { Icon(Icons.Default.SwapVert, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Apply Page Order") }
                }
            }

            if (viewModel.successMessage != null) Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f))) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50)); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.successMessage!!, color = Color(0xFF2E7D32)) } }
            if (viewModel.errorMessage != null) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfExtractScreen(
    fileUri: String? = null,
    onBack: () -> Unit,
    viewModel: PdfToolsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.extractInputUri = it; try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {} }
    }

    LaunchedEffect(fileUri) { fileUri?.let { viewModel.extractInputUri = Uri.parse(it) } }

    val thumbnails = rememberPdfThumbnails(context, viewModel.extractInputUri)
    var selectedPages by remember { mutableStateOf<Set<Int>>(emptySet()) }

    Scaffold(topBar = { TopAppBar(title = { Text("Extract Pages", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth().clickable { filePicker.launch(arrayOf("application/pdf")) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = if (viewModel.extractInputUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Text(if (viewModel.extractInputUri != null) "PDF Selected" else "Tap to select PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }

            if (thumbnails.isNotEmpty()) {
                Text("Tap thumbnails to select pages for extraction:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(thumbnails) { index, bitmap ->
                        val isSelected = selectedPages.contains(index)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.75f)
                                .clickable {
                                    selectedPages = if (isSelected) selectedPages - index else selectedPages + index
                                },
                            border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                            colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Page ${index + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                                Surface(
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                                    shape = CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = { viewModel.extractPdfPages(selectedPages) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !viewModel.isProcessing && selectedPages.isNotEmpty()
                ) {
                    if (viewModel.isProcessing) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    else { Icon(Icons.Default.ContentCopy, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Extract ${selectedPages.size} Selected Pages") }
                }
            }

            if (viewModel.successMessage != null) Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f))) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50)); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.successMessage!!, color = Color(0xFF2E7D32)) } }
            if (viewModel.errorMessage != null) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfDeleteScreen(
    fileUri: String? = null,
    onBack: () -> Unit,
    viewModel: PdfToolsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.deleteInputUri = it; try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {} }
    }

    LaunchedEffect(fileUri) { fileUri?.let { viewModel.deleteInputUri = Uri.parse(it) } }

    val thumbnails = rememberPdfThumbnails(context, viewModel.deleteInputUri)
    var selectedPages by remember { mutableStateOf<Set<Int>>(emptySet()) }

    Scaffold(topBar = { TopAppBar(title = { Text("Delete Pages", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth().clickable { filePicker.launch(arrayOf("application/pdf")) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = if (viewModel.deleteInputUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Text(if (viewModel.deleteInputUri != null) "PDF Selected" else "Tap to select PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }

            if (thumbnails.isNotEmpty()) {
                Text("Tap thumbnails to select pages for deletion:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(thumbnails) { index, bitmap ->
                        val isSelected = selectedPages.contains(index)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.75f)
                                .clickable {
                                    selectedPages = if (isSelected) selectedPages - index else selectedPages + index
                                },
                            border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant),
                            colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Page ${index + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                                Surface(
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                                    shape = CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.error else Color.Black.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = { viewModel.deletePdfPages(selectedPages) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !viewModel.isProcessing && selectedPages.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    if (viewModel.isProcessing) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onError, strokeWidth = 2.dp)
                    else { Icon(Icons.Default.Delete, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Delete ${selectedPages.size} Selected Pages") }
                }
            }

            if (viewModel.successMessage != null) Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f))) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50)); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.successMessage!!, color = Color(0xFF2E7D32)) } }
            if (viewModel.errorMessage != null) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfInsertPagesScreen(onBack: () -> Unit, viewModel: PdfToolsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val filePickerMain = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> uri?.let { viewModel.insertMainUri = it; try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {} } }
    val filePickerInsert = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> uri?.let { viewModel.insertInsertUri = it; try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {} } }

    val mainThumbnails = rememberPdfThumbnails(context, viewModel.insertMainUri)
    var selectedInsertIndex by remember(mainThumbnails) { mutableIntStateOf(0) }

    Scaffold(topBar = { TopAppBar(title = { Text("Insert Pages", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth().clickable { filePickerMain.launch(arrayOf("application/pdf")) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = if (viewModel.insertMainUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (viewModel.insertMainUri != null) "Base PDF Selected" else "Select Base PDF", style = MaterialTheme.typography.titleMedium)
                }
            }
            Card(modifier = Modifier.fillMaxWidth().clickable { filePickerInsert.launch(arrayOf("application/pdf")) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.NoteAdd, contentDescription = null, tint = if (viewModel.insertInsertUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (viewModel.insertInsertUri != null) "PDF to Insert Selected" else "Select PDF to Insert", style = MaterialTheme.typography.titleMedium)
                }
            }

            if (mainThumbnails.isNotEmpty() && viewModel.insertInsertUri != null) {
                Text("Tap thumbnail to select where to insert pages:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(mainThumbnails) { index, bitmap ->
                        val isSelected = selectedInsertIndex == index
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.75f)
                                .clickable {
                                    selectedInsertIndex = index
                                    viewModel.insertAtPage = index
                                },
                            border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                            colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Page ${index + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                                Surface(
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                                    shape = CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        viewModel.insertAtPage = selectedInsertIndex
                        viewModel.insertPages()
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !viewModel.isProcessing
                ) {
                    if (viewModel.isProcessing) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    else { Icon(Icons.Default.NoteAdd, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Insert Before Page ${selectedInsertIndex + 1}") }
                }
            }
            if (viewModel.successMessage != null) Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f))) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50)); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.successMessage!!, color = Color(0xFF2E7D32)) } }
            if (viewModel.errorMessage != null) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReplacePagesScreen(onBack: () -> Unit, viewModel: PdfToolsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val filePickerMain = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> uri?.let { viewModel.replaceMainUri = it; try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {} } }
    val filePickerReplace = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> uri?.let { viewModel.replaceReplaceUri = it; try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {} } }

    val mainThumbnails = rememberPdfThumbnails(context, viewModel.replaceMainUri)
    val replacementThumbnails = rememberPdfThumbnails(context, viewModel.replaceReplaceUri)
    var selectedReplaceIndex by remember(mainThumbnails) { mutableIntStateOf(0) }

    Scaffold(topBar = { TopAppBar(title = { Text("Replace Pages", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth().clickable { filePickerMain.launch(arrayOf("application/pdf")) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = if (viewModel.replaceMainUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (viewModel.replaceMainUri != null) "Original PDF Selected" else "Select Original PDF", style = MaterialTheme.typography.titleMedium)
                }
            }
            Card(modifier = Modifier.fillMaxWidth().clickable { filePickerReplace.launch(arrayOf("application/pdf")) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FindReplace, contentDescription = null, tint = if (viewModel.replaceReplaceUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (viewModel.replaceReplaceUri != null) "Replacement PDF Selected (${replacementThumbnails.size} pages)" else "Select Replacement PDF", style = MaterialTheme.typography.titleMedium)
                }
            }

            if (mainThumbnails.isNotEmpty() && viewModel.replaceReplaceUri != null) {
                Text("Tap thumbnail in Original PDF to select start page to replace:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(mainThumbnails) { index, bitmap ->
                        val isSelected = selectedReplaceIndex == index
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.75f)
                                .clickable {
                                    selectedReplaceIndex = index
                                    viewModel.replaceStartPage = index
                                },
                            border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                            colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Page ${index + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                                Surface(
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                                    shape = CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        viewModel.replaceStartPage = selectedReplaceIndex
                        viewModel.replacePages()
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !viewModel.isProcessing
                ) {
                    if (viewModel.isProcessing) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    else { Icon(Icons.Default.FindReplace, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Replace Starting at Page ${selectedReplaceIndex + 1}") }
                }
            }
            if (viewModel.successMessage != null) Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f))) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50)); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.successMessage!!, color = Color(0xFF2E7D32)) } }
            if (viewModel.errorMessage != null) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfCropMarginsScreen(onBack: () -> Unit, viewModel: PdfToolsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.cropInputUri = it; try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {} }
    }

    val thumbnails = rememberPdfThumbnails(context, viewModel.cropInputUri)
    val pageOneBitmap = thumbnails.firstOrNull()

    Scaffold(topBar = { TopAppBar(title = { Text("Crop Margins", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth().clickable { filePicker.launch(arrayOf("application/pdf")) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Crop, contentDescription = null, tint = if (viewModel.cropInputUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Text(if (viewModel.cropInputUri != null) "PDF Selected" else "Tap to select PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (viewModel.cropInputUri != null) {
                        Text("Live preview of Page 1 shown below with cut margins shaded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (viewModel.cropInputUri != null) {
                // Live Page 1 Preview Card with Canvas Crop Mask Overlay
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Page 1 Margin Preview", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    val cropPrimaryColor = MaterialTheme.colorScheme.primary
                    Box(
                        modifier = Modifier
                            .width(200.dp)
                            .aspectRatio(1f / 1.414f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (pageOneBitmap != null) {
                            Image(
                                bitmap = pageOneBitmap.asImageBitmap(),
                                contentDescription = "Page 1 Preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.FillBounds
                            )
                        } else {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (i in 0..6) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(if (i % 2 == 0) 0.85f else 0.95f)
                                            .height(8.dp)
                                            .background(Color.LightGray.copy(alpha = 0.4f))
                                    )
                                }
                            }
                        }

                        // Canvas drawing shaded cut margins and dashed active box
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            // Standard A4 reference: 595 x 842 points
                            val normLeft = (viewModel.cropLeft / 595f).coerceIn(0f, 0.45f)
                            val normRight = (viewModel.cropRight / 595f).coerceIn(0f, 0.45f)
                            val normTop = (viewModel.cropTop / 842f).coerceIn(0f, 0.45f)
                            val normBottom = (viewModel.cropBottom / 842f).coerceIn(0f, 0.45f)

                            val maskColor = Color.Black.copy(alpha = 0.45f)
                            // Top strip
                            if (normTop > 0f) {
                                drawRect(color = maskColor, topLeft = Offset(0f, 0f), size = Size(w, h * normTop))
                            }
                            // Bottom strip
                            if (normBottom > 0f) {
                                drawRect(color = maskColor, topLeft = Offset(0f, h * (1f - normBottom)), size = Size(w, h * normBottom))
                            }
                            // Left strip
                            if (normLeft > 0f) {
                                drawRect(color = maskColor, topLeft = Offset(0f, h * normTop), size = Size(w * normLeft, h * (1f - normTop - normBottom)))
                            }
                            // Right strip
                            if (normRight > 0f) {
                                drawRect(color = maskColor, topLeft = Offset(w * (1f - normRight), h * normTop), size = Size(w * normRight, h * (1f - normTop - normBottom)))
                            }

                            // Remaining content boundary
                            drawRect(
                                color = cropPrimaryColor,
                                topLeft = Offset(w * normLeft, h * normTop),
                                size = Size(w * (1f - normLeft - normRight), h * (1f - normTop - normBottom)),
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                                )
                            )
                        }
                    }
                }

                // Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.cropTop = 0f; viewModel.cropBottom = 0f; viewModel.cropLeft = 0f; viewModel.cropRight = 0f },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("Reset (0pt)", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { viewModel.cropTop = 18f; viewModel.cropBottom = 18f; viewModel.cropLeft = 18f; viewModel.cropRight = 18f },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("Narrow (18)", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { viewModel.cropTop = 36f; viewModel.cropBottom = 36f; viewModel.cropLeft = 36f; viewModel.cropRight = 36f },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("Normal (36)", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { viewModel.cropTop = 72f; viewModel.cropBottom = 72f; viewModel.cropLeft = 72f; viewModel.cropRight = 72f },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("Wide (72)", fontSize = 11.sp)
                    }
                }

                listOf(
                    Triple("Top Margin", { viewModel.cropTop }, { v: Float -> viewModel.cropTop = v }),
                    Triple("Bottom Margin", { viewModel.cropBottom }, { v: Float -> viewModel.cropBottom = v }),
                    Triple("Left Margin", { viewModel.cropLeft }, { v: Float -> viewModel.cropLeft = v }),
                    Triple("Right Margin", { viewModel.cropRight }, { v: Float -> viewModel.cropRight = v })
                ).forEach { (label, getter, setter) ->
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold); Text("${getter().toInt()} pt", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                        Slider(value = getter(), onValueChange = { setter(it) }, valueRange = 0f..100f, steps = 19)
                    }
                }
                Button(onClick = { viewModel.cropPdfMargins() }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = !viewModel.isProcessing) {
                    if (viewModel.isProcessing) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    else { Icon(Icons.Default.Crop, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Apply Margin Crop") }
                }
            }
            if (viewModel.successMessage != null) Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f))) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50)); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.successMessage!!, color = Color(0xFF2E7D32)) } }
            if (viewModel.errorMessage != null) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPageNumberScreen(onBack: () -> Unit, viewModel: PdfToolsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.pageNumberInputUri = it; try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {} }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Add Page Numbers", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth().clickable { filePicker.launch(arrayOf("application/pdf")) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.FormatListNumbered, contentDescription = null, tint = if (viewModel.pageNumberInputUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Text(if (viewModel.pageNumberInputUri != null) "PDF Selected" else "Tap to select PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            if (viewModel.pageNumberInputUri != null) {
                OutlinedTextField(value = viewModel.pageNumberStart, onValueChange = { viewModel.pageNumberStart = it }, label = { Text("Start Page Number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(onClick = { viewModel.addPageNumbers() }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = !viewModel.isProcessing) {
                    if (viewModel.isProcessing) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    else { Icon(Icons.Default.FormatListNumbered, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Add Page Numbers") }
                }
            }
            if (viewModel.successMessage != null) Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f))) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50)); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.successMessage!!, color = Color(0xFF2E7D32)) } }
            if (viewModel.errorMessage != null) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfResizeScreen(onBack: () -> Unit, viewModel: PdfToolsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.resizeInputUri = it; try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {} }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Resize PDF Pages", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth().clickable { filePicker.launch(arrayOf("application/pdf")) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AspectRatio, contentDescription = null, tint = if (viewModel.resizeInputUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Text(if (viewModel.resizeInputUri != null) "PDF Selected" else "Tap to select PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            if (viewModel.resizeInputUri != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = viewModel.resizeTargetSize == "A4", onClick = { viewModel.resizeTargetSize = "A4" }, label = { Text("A4") }, modifier = Modifier.weight(1f))
                    FilterChip(selected = viewModel.resizeTargetSize == "LETTER", onClick = { viewModel.resizeTargetSize = "LETTER" }, label = { Text("Letter") }, modifier = Modifier.weight(1f))
                    FilterChip(selected = viewModel.resizeTargetSize == "LEGAL", onClick = { viewModel.resizeTargetSize = "LEGAL" }, label = { Text("Legal") }, modifier = Modifier.weight(1f))
                }
                Button(onClick = { viewModel.resizePdfPages() }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = !viewModel.isProcessing) {
                    if (viewModel.isProcessing) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    else { Icon(Icons.Default.AspectRatio, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Resize Pages") }
                }
            }
            if (viewModel.successMessage != null) Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f))) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50)); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.successMessage!!, color = Color(0xFF2E7D32)) } }
            if (viewModel.errorMessage != null) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer) } }
        }
    }
}
