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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

enum class AnnotationMode {
    NONE, HIGHLIGHT, MARKER, TEXT_NOTE
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
    viewModel: PdfViewerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
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
    var annotationMode by remember { mutableStateOf(AnnotationMode.NONE) }
    var selectedMarkerColor by remember { mutableStateOf(Color.Red) }
    
    // Page level active overlays
    val pagePaths = remember { mutableStateMapOf<Int, List<DrawingPath>>() }
    val pageTextNotes = remember { mutableStateMapOf<Int, List<TextNote>>() }

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
                            // Highlights toggle
                            IconButton(
                                onClick = {
                                    annotationMode = if (annotationMode == AnnotationMode.HIGHLIGHT) AnnotationMode.NONE else AnnotationMode.HIGHLIGHT
                                },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (annotationMode == AnnotationMode.HIGHLIGHT) Color.Yellow.copy(alpha = 0.3f) else Color.Transparent
                                )
                            ) {
                                Icon(Icons.Default.Create, contentDescription = "Highlight", tint = if (annotationMode == AnnotationMode.HIGHLIGHT) Color.Yellow else MaterialTheme.colorScheme.onSurface)
                            }

                            // Marker toggle
                            IconButton(
                                onClick = {
                                    annotationMode = if (annotationMode == AnnotationMode.MARKER) AnnotationMode.NONE else AnnotationMode.MARKER
                                },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (annotationMode == AnnotationMode.MARKER) selectedMarkerColor.copy(alpha = 0.15f) else Color.Transparent
                                )
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Marker Pen", tint = if (annotationMode == AnnotationMode.MARKER) selectedMarkerColor else MaterialTheme.colorScheme.onSurface)
                            }

                            // Text Note toggle
                            IconButton(
                                onClick = {
                                    annotationMode = if (annotationMode == AnnotationMode.TEXT_NOTE) AnnotationMode.NONE else AnnotationMode.TEXT_NOTE
                                },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (annotationMode == AnnotationMode.TEXT_NOTE) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
                                )
                            ) {
                                Icon(Icons.Default.AddComment, contentDescription = "Text Note", tint = if (annotationMode == AnnotationMode.TEXT_NOTE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
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
                                        // Save all page annotations sequentially via PDFBox in ViewModel
                                        val successCount = pagePaths.size + pageTextNotes.size
                                        
                                        // Package data models
                                        pagePaths.forEach { (idx, list) ->
                                            val pathsData = list.map { path ->
                                                DrawingPathData(
                                                    points = path.points.map { DrawingPointData(it.x, it.y) },
                                                    colorHex = when(path.color) {
                                                        Color.Yellow -> "#FFFF00"
                                                        Color.Blue -> "#0000FF"
                                                        Color.Black -> "#000000"
                                                        Color(0xFF10B981) -> "#10B981"
                                                        else -> "#FF0000"
                                                    },
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

                    // Consistent actions bar
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
                            ActionColumnButton(icon = Icons.Default.OpenInNew, title = "Open in...") {
                                try {
                                    val file = File(fileUri)
                                    val fileUriProvider = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(fileUriProvider, "application/pdf")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(openIntent, "Open PDF In"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed to resolve open intent: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            }

                            ActionColumnButton(icon = Icons.Default.Print, title = "Print") {
                                try {
                                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                                    val jobName = "OmniSuite Document Print"
                                    printManager.print(
                                        jobName,
                                        PdfDocumentAdapter(context, File(fileUri)),
                                        null
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }

                            ActionColumnButton(icon = Icons.Default.Share, title = "Share") {
                                try {
                                    val file = File(fileUri)
                                    val fileUriProvider = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, fileUriProvider)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Document"))
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }

                            // Quick Tools Dropdown trigger
                            var showQuickToolsMenu by remember { mutableStateOf(false) }
                            Box {
                                ActionColumnButton(icon = Icons.Default.Build, title = "Quick Tools") {
                                    showQuickToolsMenu = true
                                }
                                DropdownMenu(
                                    expanded = showQuickToolsMenu,
                                    onDismissRequest = { showQuickToolsMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("✍️ Add Digital Signature") },
                                        onClick = {
                                            showQuickToolsMenu = false
                                            Toast.makeText(context, "Launch Signature tool from Tools Hub", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("🎨 Add Watermark overlay") },
                                        onClick = {
                                            showQuickToolsMenu = false
                                            Toast.makeText(context, "Launch Watermark tool from Tools Hub", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("🔒 Encrypt / Lock PDF") },
                                        onClick = {
                                            showQuickToolsMenu = false
                                            Toast.makeText(context, "Launch Encrypt tool from Tools Hub", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
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
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
                    ) {
                        items(currentState.pageCount) { pageIndex ->
                            val isHighlighted = searchResults.getOrNull(currentMatchIndex)?.pageIndex == pageIndex
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                InteractivePdfPageItem(
                                    pageIndex = pageIndex,
                                    viewModel = viewModel,
                                    isHighlighted = isHighlighted,
                                    annotationMode = annotationMode,
                                    selectedColor = selectedMarkerColor,
                                    pagePaths = pagePaths,
                                    pageTextNotes = pageTextNotes,
                                    onAddTextNoteTap = { offset ->
                                        activeNoteOffset = offset
                                        activeNotePageIndex = pageIndex
                                        showTextNoteDialog = true
                                    }
                                )
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
        }
    }
}

@Composable
fun InteractivePdfPageItem(
    pageIndex: Int,
    viewModel: PdfViewerViewModel,
    isHighlighted: Boolean,
    annotationMode: AnnotationMode,
    selectedColor: Color,
    pagePaths: MutableMap<Int, List<DrawingPath>>,
    pageTextNotes: MutableMap<Int, List<TextNote>>,
    onAddTextNoteTap: (DrawingPoint) -> Unit
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
    val currentPathPoints = remember { mutableStateListOf<DrawingPoint>() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio),
        shape = RoundedCornerShape(4.dp),
        border = if (isHighlighted) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                                    val width = if (isHighlight) 24f else 8f
                                    val newPath = DrawingPath(
                                        points = currentPathPoints.toList(),
                                        color = color,
                                        strokeWidth = width,
                                        isHighlight = isHighlight
                                    )
                                    val list = pagePaths[pageIndex] ?: emptyList()
                                    pagePaths[pageIndex] = list + newPath
                                    currentPathPoints.clear()
                                }
                            }
                        )
                    } else if (annotationMode == AnnotationMode.TEXT_NOTE) {
                        detectTapGestures { offset ->
                            val normX = offset.x / size.width.toFloat()
                            val normY = offset.y / size.height.toFloat()
                            onAddTextNoteTap(DrawingPoint(normX, normY))
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                bitmap != null -> {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "Page ${pageIndex + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    // Draw freehand drawing layers
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height

                        // Render saved paths
                        val saved = pagePaths[pageIndex] ?: emptyList()
                        saved.forEach { drawPath ->
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
                            val widthStroke = if (isHighlight) 24f else 8f
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

                    // Render placed Text note badges
                    val notes = pageTextNotes[pageIndex] ?: emptyList()
                    Box(modifier = Modifier.fillMaxSize()) {
                        notes.forEach { note ->
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(
                                        start = (note.x * bitmap!!.width / 1.5f).dp,
                                        top = (note.y * bitmap!!.height / 1.5f).dp
                                    )
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Yellow)
                                    .border(1.dp, Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(note.text, fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            }
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
fun ActionColumnButton(
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
