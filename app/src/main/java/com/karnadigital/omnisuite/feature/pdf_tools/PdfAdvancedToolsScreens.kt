package com.karnadigital.omnisuite.feature.pdf_tools

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.karnadigital.omnisuite.core.util.ZoomableBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfCompareScreen(onBack: () -> Unit, viewModel: PdfToolsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var isHorizontalSplit by remember { mutableStateOf(true) }
    var syncPages by remember { mutableStateOf(true) }
    var showDiffSheet by remember { mutableStateOf(false) }

    var page1 by remember { mutableIntStateOf(0) }
    var page2 by remember { mutableIntStateOf(0) }
    var pageCount1 by remember { mutableIntStateOf(0) }
    var pageCount2 by remember { mutableIntStateOf(0) }

    val filePicker1 = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            viewModel.compareUri1 = it
            page1 = 0
            try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
        }
    }
    val filePicker2 = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            viewModel.compareUri2 = it
            page2 = 0
            try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
        }
    }

    val onPage1Change: (Int) -> Unit = { newPage ->
        val maxPage = if (pageCount1 > 0) pageCount1 - 1 else 0
        val clamped = newPage.coerceIn(0, maxPage)
        page1 = clamped
        if (syncPages) {
            val maxPage2 = if (pageCount2 > 0) pageCount2 - 1 else 0
            page2 = clamped.coerceIn(0, maxPage2)
        }
    }

    val onPage2Change: (Int) -> Unit = { newPage ->
        val maxPage = if (pageCount2 > 0) pageCount2 - 1 else 0
        val clamped = newPage.coerceIn(0, maxPage)
        page2 = clamped
        if (syncPages) {
            val maxPage1 = if (pageCount1 > 0) pageCount1 - 1 else 0
            page1 = clamped.coerceIn(0, maxPage1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compare PDF Documents", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (viewModel.compareUri1 != null && viewModel.compareUri2 != null) {
                        IconButton(onClick = { isHorizontalSplit = !isHorizontalSplit }) {
                            Icon(
                                if (isHorizontalSplit) Icons.Default.ViewAgenda else Icons.Default.ViewColumn,
                                contentDescription = "Toggle Split Orientation"
                            )
                        }
                        IconButton(onClick = {
                            viewModel.comparePdf()
                            showDiffSheet = true
                        }) {
                            Icon(Icons.Default.Difference, contentDescription = "Show Text Diff Analysis")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (viewModel.compareUri1 == null || viewModel.compareUri2 == null) {
                // Document selection screen when either document is missing
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { filePicker1.launch(arrayOf("application/pdf")) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = if (viewModel.compareUri1 != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                if (viewModel.compareUri1 != null) "PDF 1 Selected: ${getDocName(context, viewModel.compareUri1!!)}" else "Tap to Select Document 1 (Base)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { filePicker2.launch(arrayOf("application/pdf")) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = if (viewModel.compareUri2 != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                if (viewModel.compareUri2 != null) "PDF 2 Selected: ${getDocName(context, viewModel.compareUri2!!)}" else "Tap to Select Document 2 (Modified)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Text(
                        text = "Once both documents are chosen, OmniSuite splits your display so you can view both files simultaneously side-by-side with synchronized page scrolling and text diff metrics.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            } else {
                // Split Screen Comparison View (Mid of Screen Horizontal or Vertical)
                Column(modifier = Modifier.fillMaxSize()) {
                    // Divider control strip in middle
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = syncPages,
                                    onCheckedChange = { syncPages = it },
                                    modifier = Modifier.size(32.dp).padding(end = 6.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (syncPages) "Sync Pages On" else "Sync Pages Off",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = {
                                        viewModel.comparePdf()
                                        showDiffSheet = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Difference, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Text Diff", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    if (isHorizontalSplit) {
                        // Horizontal Split: Top half sees 1st PDF, bottom half sees 2nd PDF
                        Column(modifier = Modifier.fillMaxSize()) {
                            PdfComparePane(
                                title = "Doc 1: ${getDocName(context, viewModel.compareUri1!!)}",
                                uri = viewModel.compareUri1,
                                currentPage = page1,
                                onPageCountDetermined = { pageCount1 = it },
                                onPrevPage = { onPage1Change(page1 - 1) },
                                onNextPage = { onPage1Change(page1 + 1) },
                                onPickFile = { filePicker1.launch(arrayOf("application/pdf")) },
                                pageCount = pageCount1,
                                modifier = Modifier.weight(1f)
                            )

                            HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)

                            PdfComparePane(
                                title = "Doc 2: ${getDocName(context, viewModel.compareUri2!!)}",
                                uri = viewModel.compareUri2,
                                currentPage = page2,
                                onPageCountDetermined = { pageCount2 = it },
                                onPrevPage = { onPage2Change(page2 - 1) },
                                onNextPage = { onPage2Change(page2 + 1) },
                                onPickFile = { filePicker2.launch(arrayOf("application/pdf")) },
                                pageCount = pageCount2,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        // Vertical Split: Left half sees 1st PDF, right half sees 2nd PDF
                        Row(modifier = Modifier.fillMaxSize()) {
                            PdfComparePane(
                                title = getDocName(context, viewModel.compareUri1!!),
                                uri = viewModel.compareUri1,
                                currentPage = page1,
                                onPageCountDetermined = { pageCount1 = it },
                                onPrevPage = { onPage1Change(page1 - 1) },
                                onNextPage = { onPage1Change(page1 + 1) },
                                onPickFile = { filePicker1.launch(arrayOf("application/pdf")) },
                                pageCount = pageCount1,
                                modifier = Modifier.weight(1f)
                            )

                            VerticalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)

                            PdfComparePane(
                                title = getDocName(context, viewModel.compareUri2!!),
                                uri = viewModel.compareUri2,
                                currentPage = page2,
                                onPageCountDetermined = { pageCount2 = it },
                                onPrevPage = { onPage2Change(page2 - 1) },
                                onNextPage = { onPage2Change(page2 + 1) },
                                onPickFile = { filePicker2.launch(arrayOf("application/pdf")) },
                                pageCount = pageCount2,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            if (showDiffSheet) {
                ModalBottomSheet(onDismissRequest = { showDiffSheet = false }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text("Text Difference Analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (viewModel.isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
                        } else if (viewModel.compareResult != null) {
                            Text(viewModel.compareResult!!, style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text("No difference analysis loaded yet.", style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { showDiffSheet = false }, modifier = Modifier.fillMaxWidth()) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfComparePane(
    title: String,
    uri: Uri?,
    currentPage: Int,
    pageCount: Int,
    onPageCountDetermined: (Int) -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onPickFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(uri, currentPage) {
        if (uri == null) {
            pageBitmap = null
            return@LaunchedEffect
        }
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                pfd?.use { fd ->
                    val renderer = PdfRenderer(fd)
                    val total = renderer.pageCount
                    onPageCountDetermined(total)
                    if (total > 0 && currentPage in 0 until total) {
                        val page = renderer.openPage(currentPage)
                        val density = context.resources.displayMetrics.density
                        val width = (page.width * 1.5f).toInt().coerceAtLeast(300)
                        val height = (page.height * 1.5f).toInt().coerceAtLeast(300)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bitmap)
                        canvas.drawColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        pageBitmap = bitmap
                    }
                    renderer.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Pane Mini Header Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevPage, enabled = currentPage > 0, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Page")
                    }
                    Text(
                        text = "${currentPage + 1}/${pageCount.coerceAtLeast(1)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    IconButton(onClick = onNextPage, enabled = currentPage < pageCount - 1, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next Page")
                    }
                    IconButton(onClick = onPickFile, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Replace File", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .background(Color(0xFFE0E0E0)),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            } else if (pageBitmap != null) {
                ZoomableBox(modifier = Modifier.fillMaxSize()) {
                    Image(
                        bitmap = pageBitmap!!.asImageBitmap(),
                        contentDescription = "PDF Page View",
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            } else {
                Text("Unable to render page", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

private fun getDocName(context: android.content.Context, uri: Uri): String {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)
            } else null
        } ?: uri.lastPathSegment ?: "document.pdf"
    } catch (e: Exception) {
        uri.lastPathSegment ?: "document.pdf"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfMetadataScreen(onBack: () -> Unit, viewModel: PdfToolsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.metadataInputUri = it; try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {} }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Edit PDF Metadata", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth().clickable { filePicker.launch(arrayOf("application/pdf")) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = if (viewModel.metadataInputUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Text(if (viewModel.metadataInputUri != null) "PDF Selected" else "Tap to select PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            if (viewModel.metadataInputUri != null) {
                OutlinedTextField(value = viewModel.metadataTitle, onValueChange = { viewModel.metadataTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = viewModel.metadataAuthor, onValueChange = { viewModel.metadataAuthor = it }, label = { Text("Author") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = viewModel.metadataSubject, onValueChange = { viewModel.metadataSubject = it }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = viewModel.metadataKeywords, onValueChange = { viewModel.metadataKeywords = it }, label = { Text("Keywords") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(onClick = { viewModel.editMetadata() }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = !viewModel.isProcessing) {
                    if (viewModel.isProcessing) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    else { Icon(Icons.Default.Save, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Save Metadata") }
                }
            }
            if (viewModel.successMessage != null) Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f))) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50)); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.successMessage!!, color = Color(0xFF2E7D32)) } }
            if (viewModel.errorMessage != null) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfBookmarkScreen(onBack: () -> Unit, viewModel: PdfToolsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.bookmarkInputUri = it; try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {} }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("PDF Outline & Bookmarks", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth().clickable { filePicker.launch(arrayOf("application/pdf")) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = if (viewModel.bookmarkInputUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Text(if (viewModel.bookmarkInputUri != null) "PDF Selected" else "Tap to select PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            if (viewModel.bookmarkInputUri != null) {
                OutlinedTextField(value = viewModel.bookmarkTitles, onValueChange = { viewModel.bookmarkTitles = it }, label = { Text("Bookmark Titles (one per line)") }, modifier = Modifier.fillMaxWidth().height(100.dp), maxLines = 10)
                OutlinedTextField(value = viewModel.bookmarkPages, onValueChange = { viewModel.bookmarkPages = it }, label = { Text("Target Page Numbers (one per line, 1-based)") }, modifier = Modifier.fillMaxWidth().height(100.dp), maxLines = 10)
                Button(onClick = { viewModel.editBookmarks() }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = !viewModel.isProcessing) {
                    if (viewModel.isProcessing) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    else { Icon(Icons.Default.Bookmark, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Apply Bookmarks") }
                }
            }
            if (viewModel.successMessage != null) Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f))) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50)); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.successMessage!!, color = Color(0xFF2E7D32)) } }
            if (viewModel.errorMessage != null) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error); Spacer(modifier = Modifier.width(12.dp)); Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer) } }
        }
    }
}
