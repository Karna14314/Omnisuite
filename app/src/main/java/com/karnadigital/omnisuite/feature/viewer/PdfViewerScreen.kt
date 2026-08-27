package com.karnadigital.omnisuite.feature.viewer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
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
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.karnadigital.omnisuite.di.coreEntryPoint
import com.karnadigital.omnisuite.core.util.ZoomableBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

enum class AnnotationMode {
    NONE, HIGHLIGHT, MARKER, TEXT_NOTE, ERASER
}

data class DrawingPoint(val x: Float, val y: Float)
data class DrawingPath(
    val points: List<DrawingPoint>,
    val color: Color,
    val strokeWidth: Float,
    val isHighlight: Boolean
)
data class TextNote(
    val text: String,
    val x: Float,
    val y: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    fileUri: String,
    onBack: () -> Unit,
    onToolAction: (ViewerTool) -> Unit = {},
    viewModel: PdfViewerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uriCacheUtils = coreEntryPoint(context).uriCacheUtils()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(fileUri) {
        viewModel.loadPdf(fileUri)
    }

    val state by viewModel.loadState.collectAsState()
    val lazyListState = rememberLazyListState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val currentMatchIndex by viewModel.currentMatchIndex.collectAsState()

    var searchExpanded by remember { mutableStateOf(false) }

    // Annotations Active Modes
    var isPdfEditingActive by remember { mutableStateOf(false) }
    var annotationMode by remember { mutableStateOf(AnnotationMode.NONE) }
    var selectedMarkerColor by remember { mutableStateOf(Color.Red) }
    var selectedStrokeWidth by remember { mutableStateOf(8f) }
    
    // Page level active overlays
    val pagePaths = remember { mutableStateMapOf<Int, List<DrawingPath>>() }
    val pageTextNotes = remember { mutableStateMapOf<Int, List<TextNote>>() }

    val loadedAnnotations by viewModel.loadedAnnotations.collectAsState()
    LaunchedEffect(loadedAnnotations) {
        loadedAnnotations.forEach { (pageIdx, notes) ->
            pageTextNotes[pageIdx] = notes.map { TextNote(it.text, it.x, it.y) }
        }
    }

    var showViewEditNoteDialog by remember { mutableStateOf(false) }
    var viewEditNoteText by remember { mutableStateOf("") }
    var viewEditNoteIndex by remember { mutableStateOf(-1) }
    var viewEditNotePageIndex by remember { mutableStateOf(-1) }

    // Floating Text note dialog triggers
    var showTextNoteDialog by remember { mutableStateOf(false) }
    var activeNoteText by remember { mutableStateOf("") }
    var activeNoteOffset by remember { mutableStateOf<DrawingPoint?>(null) }
    var activeNotePageIndex by remember { mutableStateOf(-1) }

    // Scroll to match
    LaunchedEffect(currentMatchIndex) {
        if (currentMatchIndex >= 0 && currentMatchIndex < searchResults.size) {
            val match = searchResults[currentMatchIndex]
            lazyListState.animateScrollToItem(match.pageIndex)
        }
    }

    val currentPageIndex by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex + 1
        }
    }

    var showTextSelectionSheet by remember { mutableStateOf(false) }
    var pageTextToSelect by remember { mutableStateOf("") }
    var isExtractingText by remember { mutableStateOf(false) }

    fun extractPageText(pageIdx: Int) {
        isExtractingText = true
        coroutineScope.launch(Dispatchers.IO) {
            var doc: com.tom_roush.pdfbox.pdmodel.PDDocument? = null
            try {
                val sourceUri = android.net.Uri.parse(fileUri)
                val pdfFile = if (sourceUri.scheme == "content") {
                    // SAF content URIs must be cached to a real file before PDFBox can read them.
                    uriCacheUtils.cacheUriToFile(sourceUri)
                } else {
                    val f = java.io.File(fileUri)
                    if (f.exists()) f else null
                }

                if (pdfFile == null) {
                    withContext(Dispatchers.Main) {
                        isExtractingText = false
                        Toast.makeText(context, "Could not resolve PDF file for text extraction.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                doc = com.tom_roush.pdfbox.pdmodel.PDDocument.load(pdfFile)
                val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                stripper.startPage = pageIdx
                stripper.endPage = pageIdx
                val pageText = stripper.getText(doc) ?: ""
                withContext(Dispatchers.Main) {
                    pageTextToSelect = pageText
                    isExtractingText = false
                    showTextSelectionSheet = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isExtractingText = false
                    Toast.makeText(context, "Could not extract text: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                try { doc?.close() } catch (e: Exception) {}
            }
        }
    }

    Scaffold(
        topBar = {
            if (searchExpanded) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            searchExpanded = false
                            viewModel.setSearchQuery("")
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close search")
                        }

                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search text in PDF...") },
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true
                        )

                        if (searchResults.isNotEmpty()) {
                            Text(
                                text = "${currentMatchIndex + 1} of ${searchResults.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(onClick = { viewModel.prevMatch() }) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Prev")
                            }
                            IconButton(onClick = { viewModel.nextMatch() }) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next")
                            }
                        }
                    }
                }
            } else {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = when (val s = state) {
                                        is PdfLoadState.Success -> s.fileName
                                        else -> "Loading PDF..."
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (state is PdfLoadState.Success) {
                                    val successState = state as PdfLoadState.Success
                                    Text(
                                        text = "Page $currentPageIndex of ${successState.pageCount}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Navigate back")
                            }
                        },
                        actions = {
                            if (state is PdfLoadState.Success) {
                                // Unified Premium Annotations Toggle
                                IconButton(
                                    onClick = {
                                        isPdfEditingActive = !isPdfEditingActive
                                        if (!isPdfEditingActive) {
                                            annotationMode = AnnotationMode.NONE
                                        } else {
                                            annotationMode = AnnotationMode.MARKER // Default to marker pen mode
                                        }
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = if (isPdfEditingActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Annotation",
                                        tint = if (isPdfEditingActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // Search
                                IconButton(onClick = { searchExpanded = true }) {
                                    Icon(Icons.Default.Search, contentDescription = "Search")
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )

                    // Sliding Premium Formatting and Customization Toolbar
                    AnimatedVisibility(
                        visible = isPdfEditingActive,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 4.dp
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 🎨 Highlight mode chip
                                        val isHighlightSelected = annotationMode == AnnotationMode.HIGHLIGHT
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isHighlightSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isHighlightSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable { annotationMode = AnnotationMode.HIGHLIGHT }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Create,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = if (isHighlightSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Highlight",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isHighlightSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }

                                        // ✒️ Draw Pen mode chip
                                        val isMarkerSelected = annotationMode == AnnotationMode.MARKER
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isMarkerSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isMarkerSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable { annotationMode = AnnotationMode.MARKER }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Gesture,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = if (isMarkerSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Draw Pen",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isMarkerSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }

                                        // 💬 Comment text note chip
                                        val isCommentSelected = annotationMode == AnnotationMode.TEXT_NOTE
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isCommentSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isCommentSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable { annotationMode = AnnotationMode.TEXT_NOTE }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.AddComment,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = if (isCommentSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Comment",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isCommentSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }

                                        // 🧹 Eraser mode chip
                                        val isEraserSelected = annotationMode == AnnotationMode.ERASER
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isEraserSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isEraserSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable { annotationMode = AnnotationMode.ERASER }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = if (isEraserSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Eraser",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isEraserSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            isPdfEditingActive = false
                                            annotationMode = AnnotationMode.NONE
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Close edit toolbar", modifier = Modifier.size(16.dp))
                                    }
                                }

                                // Secondary Customizer Panel if Draw Pen is selected
                                if (annotationMode == AnnotationMode.MARKER) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Colors Picker preset swatches
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("Pen:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            listOf(Color.Red, Color.Blue, Color.Black, Color(0xFF10B981), Color(0xFFFF9800)).forEach { color ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(22.dp)
                                                        .clip(CircleShape)
                                                        .background(color)
                                                        .clickable { selectedMarkerColor = color }
                                                        .padding(2.dp)
                                                ) {
                                                    if (selectedMarkerColor == color) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .clip(CircleShape)
                                                                .background(Color.White.copy(alpha = 0.4f))
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Stroke Width Presets
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text("Size:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            listOf(4f to "Thin", 8f to "Med", 16f to "Thick", 24f to "X-Thick").forEach { (widthValue, label) ->
                                                val isSelected = selectedStrokeWidth == widthValue
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                                        .clickable { selectedStrokeWidth = widthValue }
                                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = label,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (state is PdfLoadState.Success) {
                Column {
                    // Expanded Marker Colors row
                    AnimatedVisibility(visible = annotationMode == AnnotationMode.MARKER) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            tonalElevation = 2.dp,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Pen Color:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                listOf(Color.Red, Color.Blue, Color.Black, Color(0xFF10B981)).forEach { color ->
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .clickable { selectedMarkerColor = color }
                                            .padding(2.dp)
                                    ) {
                                        if (selectedMarkerColor == color) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(CircleShape)
                                                    .background(Color.White.copy(alpha = 0.4f))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Save Annotations bar (visible if active strokes exist)
                    AnimatedVisibility(visible = pagePaths.isNotEmpty() || pageTextNotes.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFE8F5E9),
                            tonalElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Unsaved in-app annotations", color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                
                                TextButton(
                                    onClick = {
                                        pagePaths.clear()
                                        pageTextNotes.clear()
                                    }
                                ) {
                                    Text("Discard", color = MaterialTheme.colorScheme.error)
                                }

                                Button(
                                    onClick = {
                                        val allKeys = pagePaths.keys + pageTextNotes.keys
                                        allKeys.forEach { idx ->
                                            val pathsData = (pagePaths[idx] ?: emptyList()).map { path ->
                                                DrawingPathData(
                                                    points = path.points.map { DrawingPointData(it.x, it.y) },
                                                    colorHex = String.format("#%08X", path.color.toArgb()),
                                                    strokeWidth = path.strokeWidth,
                                                    isHighlight = path.isHighlight
                                                )
                                            }
                                            val notesData = (pageTextNotes[idx] ?: emptyList()).map { note ->
                                                TextNoteData(note.text, note.x, note.y)
                                            }
                                            viewModel.savePdfAnnotations(idx, pathsData, notesData)
                                        }

                                        pagePaths.clear()
                                        pageTextNotes.clear()
                                        Toast.makeText(context, "Annotations permanently saved to PDF!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Save Changes")
                                }
                            }
                        }
                    }

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
                            ViewerActionColumnButton(icon = Icons.Default.OpenInNew, title = "Open in...") {
                                onToolAction(ViewerTool.OpenIn)
                            }

                            ViewerActionColumnButton(icon = Icons.Default.Print, title = "Print") {
                                onToolAction(ViewerTool.Print)
                            }

                            ViewerActionColumnButton(icon = Icons.Default.Share, title = "Share") {
                                onToolAction(ViewerTool.Share)
                            }

                            ViewerActionColumnButton(icon = Icons.Default.TextSnippet, title = "Select Text") {
                                extractPageText(currentPageIndex)
                            }

                            val pdfTools = pdfToolActions(fileUri)
                            ViewerQuickToolsMenu(
                                fileUri = fileUri,
                                toolActions = pdfTools,
                                onToolClick = { tool ->
                                    handleViewerToolAction(tool, fileUri, context, onNavigate = { route ->
                                        onToolAction(ViewerTool.Navigate(route))
                                    })
                                }
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (val currentState = state) {
                is PdfLoadState.PasswordRequired -> {
                    var passwordText by remember { mutableStateOf("") }
                    var passwordVisible by remember { mutableStateOf(false) }
                    
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(0.9f),
                            shape = MaterialTheme.shapes.extraLarge,
                            tonalElevation = 6.dp,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Encrypted file",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Password Required",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "The file '${currentState.fileName}' is encrypted. Enter the password to unlock it.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                
                                OutlinedTextField(
                                    value = passwordText,
                                    onValueChange = { passwordText = it },
                                    label = { Text("Password") },
                                    singleLine = true,
                                    visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                    trailingIcon = {
                                        val image = if (passwordVisible) Icons.Default.Info else Icons.Default.Lock
                                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                            Icon(imageVector = image, contentDescription = "Toggle password visibility")
                                        }
                                    },
                                    isError = currentState.incorrectAttempt,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                
                                if (currentState.incorrectAttempt) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Incorrect password. Please try again.",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.align(Alignment.Start)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    TextButton(
                                        onClick = { onBack() }
                                    ) {
                                        Text("Cancel")
                                    }
                                    
                                    Button(
                                        onClick = { viewModel.submitPassword(passwordText) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                                    ) {
                                        Text("Unlock File")
                                    }
                                }
                            }
                        }
                    }
                }
                is PdfLoadState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Color(0xFFEF4444), strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Reading document layout...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is PdfLoadState.Success -> {
                    ZoomableBox(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
                        ) {
                            items(count = currentState.pageCount, key = { it }) { pageIndex ->
                                val isHighlighted = searchResults.getOrNull(currentMatchIndex)?.pageIndex == pageIndex
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                ) {
                                    InteractivePdfPageItem(
                                        pageIndex = pageIndex,
                                        viewModel = viewModel,
                                        isCurrentMatch = searchResults.getOrNull(currentMatchIndex)?.pageIndex == pageIndex,
                                        hasAnyMatch = searchResults.any { it.pageIndex == pageIndex },
                                        annotationMode = annotationMode,
                                        selectedColor = selectedMarkerColor,
                                        selectedStrokeWidth = selectedStrokeWidth,
                                        pagePaths = pagePaths,
                                        pageTextNotes = pageTextNotes,
                                        onAddTextNoteTap = { offset ->
                                            activeNoteOffset = offset
                                            activeNotePageIndex = pageIndex
                                            showTextNoteDialog = true
                                        },
                                        onViewEditNoteTap = { index, text ->
                                            viewEditNoteIndex = index
                                            viewEditNoteText = text
                                            viewEditNotePageIndex = pageIndex
                                            showViewEditNoteDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                is PdfLoadState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(currentState.message, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Dialog for Text Note typing
            if (showTextNoteDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showTextNoteDialog = false
                        activeNoteText = ""
                    },
                    title = { Text("Add Text Annotation", fontWeight = FontWeight.Bold) },
                    text = {
                        OutlinedTextField(
                            value = activeNoteText,
                            onValueChange = { activeNoteText = it },
                            placeholder = { Text("Type custom note...") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val offset = activeNoteOffset
                                if (offset != null && activeNoteText.isNotBlank()) {
                                    val list = pageTextNotes[activeNotePageIndex] ?: emptyList()
                                    pageTextNotes[activeNotePageIndex] = list + TextNote(activeNoteText, offset.x, offset.y)
                                }
                                showTextNoteDialog = false
                                activeNoteText = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                        ) {
                            Text("Place Note")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showTextNoteDialog = false
                            activeNoteText = ""
                        }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Dialog for Viewing / Editing / Deleting comments
            if (showViewEditNoteDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showViewEditNoteDialog = false
                        viewEditNoteText = ""
                    },
                    title = { Text("Edit Comment", fontWeight = FontWeight.Bold) },
                    text = {
                        OutlinedTextField(
                            value = viewEditNoteText,
                            onValueChange = { viewEditNoteText = it },
                            placeholder = { Text("Type comment...") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val list = pageTextNotes[viewEditNotePageIndex] ?: emptyList()
                                if (viewEditNoteIndex >= 0 && viewEditNoteIndex < list.size) {
                                    val updatedList = list.toMutableList()
                                    val currentNote = updatedList[viewEditNoteIndex]
                                    updatedList[viewEditNoteIndex] = TextNote(viewEditNoteText, currentNote.x, currentNote.y)
                                    pageTextNotes[viewEditNotePageIndex] = updatedList
                                }
                                showViewEditNoteDialog = false
                                viewEditNoteText = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        Row {
                            TextButton(
                                onClick = {
                                    val list = pageTextNotes[viewEditNotePageIndex] ?: emptyList()
                                    if (viewEditNoteIndex >= 0 && viewEditNoteIndex < list.size) {
                                        val updatedList = list.toMutableList()
                                        updatedList.removeAt(viewEditNoteIndex)
                                        pageTextNotes[viewEditNotePageIndex] = updatedList
                                    }
                                    showViewEditNoteDialog = false
                                    viewEditNoteText = ""
                                }
                            ) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = {
                                showViewEditNoteDialog = false
                                viewEditNoteText = ""
                            }) {
                                Text("Cancel")
                            }
                        }
                    }
                )
            }

            // Bottom sheet for Selecting / Copying text
            if (showTextSelectionSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showTextSelectionSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.6f)
                            .padding(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Page $currentPageIndex Text Selection",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                            TextButton(
                                onClick = {
                                    if (pageTextToSelect.isNotBlank()) {
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(pageTextToSelect))
                                        Toast.makeText(context, "Copied all page text!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Text("Copy All")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            ) {
                                androidx.compose.foundation.text.selection.SelectionContainer {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        Text(
                                            text = if (pageTextToSelect.isBlank()) "No extractable text found on this page." else pageTextToSelect,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = { showTextSelectionSheet = false },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Close")
                        }
                    }
                }
            }

            // Dialog for showing text extraction loading
            if (isExtractingText) {
                AlertDialog(
                    onDismissRequest = {},
                    confirmButton = {},
                    title = { Text("Extracting Page Text", fontWeight = FontWeight.Bold) },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text("Reading PDF page content offline...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun InteractivePdfPageItem(
    pageIndex: Int,
    viewModel: PdfViewerViewModel,
    isCurrentMatch: Boolean,
    hasAnyMatch: Boolean,
    annotationMode: AnnotationMode,
    selectedColor: Color,
    selectedStrokeWidth: Float,
    pagePaths: MutableMap<Int, List<DrawingPath>>,
    pageTextNotes: MutableMap<Int, List<TextNote>>,
    onAddTextNoteTap: (DrawingPoint) -> Unit,
    onViewEditNoteTap: (Int, String) -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var renderError by remember { mutableStateOf(false) }

    LaunchedEffect(pageIndex) {
        try {
            val rendered = viewModel.renderPage(pageIndex)
            if (rendered != null) {
                bitmap = rendered
            } else {
                renderError = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            renderError = true
        }
    }

    val aspectRatio = viewModel.getPageAspectRatio(pageIndex)

    fun distance(p1: DrawingPoint, p2: DrawingPoint): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        coroutineScope.launch {
                            val pageText = viewModel.extractTextFromPage(pageIndex)
                            if (pageText.isNotBlank()) {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(pageText))
                                Toast.makeText(context, "Page text copied", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            },
        shape = RoundedCornerShape(4.dp),
        border = when {
            isCurrentMatch -> BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
            hasAnyMatch -> BorderStroke(3.dp, Color(0xFFFFF59D))
            else -> null
        },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(annotationMode) {
                    if (annotationMode == AnnotationMode.TEXT_NOTE) {
                        detectTapGestures { offset ->
                            val normX = offset.x / size.width.toFloat()
                            val normY = offset.y / size.height.toFloat()
                            onAddTextNoteTap(DrawingPoint(normX, normY))
                        }
                    } else if (annotationMode == AnnotationMode.ERASER) {
                        detectTapGestures { offset ->
                            val normX = offset.x / size.width.toFloat()
                            val normY = offset.y / size.height.toFloat()
                            val touchPoint = DrawingPoint(normX, normY)
                            
                            // Erase sticky notes if clicked close
                            val notes = pageTextNotes[pageIndex] ?: emptyList()
                            val remainingNotes = notes.filter { note ->
                                distance(DrawingPoint(note.x, note.y), touchPoint) > 0.05f
                            }
                            if (remainingNotes.size != notes.size) {
                                pageTextNotes[pageIndex] = remainingNotes
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val containerWidth = maxWidth
            val containerHeight = maxHeight

            when {
                bitmap != null -> {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "Page ${pageIndex + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    // Overlay Drawing Canvas
                    DrawingCanvasOverlay(
                        annotationMode = annotationMode,
                        selectedColor = selectedColor,
                        selectedStrokeWidth = selectedStrokeWidth,
                        savedPaths = pagePaths[pageIndex] ?: emptyList(),
                        onPathFinished = { newPath ->
                            val list = pagePaths[pageIndex] ?: emptyList()
                            pagePaths[pageIndex] = list + newPath
                        },
                        onErasePaths = { touchPoint ->
                            val paths = pagePaths[pageIndex] ?: emptyList()
                            val remainingPaths = paths.filter { path ->
                                path.points.none { pt -> distance(pt, touchPoint) < 0.03f }
                            }
                            if (remainingPaths.size != paths.size) {
                                pagePaths[pageIndex] = remainingPaths
                            }
                            
                            // Also check sticky notes in drag
                            val notes = pageTextNotes[pageIndex] ?: emptyList()
                            val remainingNotes = notes.filter { note ->
                                distance(DrawingPoint(note.x, note.y), touchPoint) > 0.04f
                            }
                            if (remainingNotes.size != notes.size) {
                                pageTextNotes[pageIndex] = remainingNotes
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Render placed Text note badges using real Box constraints
                    val notes = pageTextNotes[pageIndex] ?: emptyList()
                    notes.forEachIndexed { index, note ->
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(
                                    x = containerWidth * note.x - 12.dp,
                                    y = containerHeight * note.y - 12.dp
                                )
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFCA28))
                                .border(1.dp, Color.Black.copy(alpha = 0.5f), CircleShape)
                                .clickable {
                                    if (annotationMode == AnnotationMode.ERASER) {
                                        val list = pageTextNotes[pageIndex] ?: emptyList()
                                        pageTextNotes[pageIndex] = list.filterIndexed { i, _ -> i != index }
                                    } else {
                                        onViewEditNoteTap(index, note.text)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Comment,
                                contentDescription = "Comment",
                                tint = Color.Black,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                renderError -> {
                    Text("Error rendering page ${pageIndex + 1}", color = MaterialTheme.colorScheme.error)
                }
                else -> {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
fun DrawingCanvasOverlay(
    annotationMode: AnnotationMode,
    selectedColor: Color,
    selectedStrokeWidth: Float,
    savedPaths: List<DrawingPath>,
    onPathFinished: (DrawingPath) -> Unit,
    onErasePaths: (DrawingPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentPathPoints = remember { mutableStateListOf<DrawingPoint>() }

    Box(
        modifier = modifier
            .pointerInput(annotationMode) {
                if (annotationMode == AnnotationMode.MARKER || annotationMode == AnnotationMode.HIGHLIGHT) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val normX = offset.x / size.width.toFloat()
                            val normY = offset.y / size.height.toFloat()
                            currentPathPoints.add(DrawingPoint(normX, normY))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val offset = change.position
                            val normX = offset.x / size.width.toFloat()
                            val normY = offset.y / size.height.toFloat()
                            currentPathPoints.add(DrawingPoint(normX, normY))
                        },
                        onDragEnd = {
                            if (currentPathPoints.isNotEmpty()) {
                                val isHighlight = annotationMode == AnnotationMode.HIGHLIGHT
                                val color = if (isHighlight) Color.Yellow else selectedColor
                                val width = if (isHighlight) 24f else selectedStrokeWidth
                                val newPath = DrawingPath(
                                    points = currentPathPoints.toList(),
                                    color = color,
                                    strokeWidth = width,
                                    isHighlight = isHighlight
                                )
                                onPathFinished(newPath)
                                currentPathPoints.clear()
                            }
                        }
                    )
                } else if (annotationMode == AnnotationMode.ERASER) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val normX = offset.x / size.width.toFloat()
                            val normY = offset.y / size.height.toFloat()
                            onErasePaths(DrawingPoint(normX, normY))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val offset = change.position
                            val normX = offset.x / size.width.toFloat()
                            val normY = offset.y / size.height.toFloat()
                            onErasePaths(DrawingPoint(normX, normY))
                        }
                    )
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Render saved paths
            savedPaths.forEach { drawPath ->
                val strokeColor = drawPath.color
                val alpha = if (drawPath.isHighlight) 0.4f else 1.0f
                val path = Path()
                if (drawPath.points.size >= 2) {
                    path.moveTo(drawPath.points.first().x * width, drawPath.points.first().y * height)
                    for (i in 1 until drawPath.points.size) {
                        path.lineTo(drawPath.points[i].x * width, drawPath.points[i].y * height)
                    }
                    drawPath(
                        path = path,
                        color = strokeColor,
                        alpha = alpha,
                        style = Stroke(
                            width = drawPath.strokeWidth,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                        )
                    )
                }
            }

            // Render active drawn path live
            if (currentPathPoints.size >= 2) {
                val isHighlight = annotationMode == AnnotationMode.HIGHLIGHT
                val color = if (isHighlight) Color.Yellow else selectedColor
                val alpha = if (isHighlight) 0.4f else 1.0f
                val widthStroke = if (isHighlight) 24f else selectedStrokeWidth
                val path = Path()
                path.moveTo(currentPathPoints.first().x * width, currentPathPoints.first().y * height)
                for (i in 1 until currentPathPoints.size) {
                    path.lineTo(currentPathPoints[i].x * width, currentPathPoints[i].y * height)
                }
                drawPath(
                    path = path,
                    color = color,
                    alpha = alpha,
                    style = Stroke(
                        width = widthStroke,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )
            }
        }
    }
}

/**
 * Custom PDF print adapter that spools pages directly from cached Sandbox Storage.
 */
class PdfDocumentAdapter(private val context: Context, private val file: File) : PrintDocumentAdapter() {
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
        val info = PrintDocumentInfo.Builder(file.name)
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
            input = FileInputStream(file)
            output = FileOutputStream(destination?.fileDescriptor)
            input.copyTo(output)
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            e.printStackTrace()
            callback?.onWriteFailed(e.localizedMessage)
        } finally {
            input?.close()
            output?.close()
        }
    }
}
