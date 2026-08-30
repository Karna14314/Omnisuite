package com.karnadigital.omnisuite.feature.viewer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.karnadigital.omnisuite.di.coreEntryPoint
import com.karnadigital.omnisuite.core.util.ZoomableBox
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToInt

enum class AnnotationMode {
    NONE, HIGHLIGHT, MARKER, UNDERLINE, TEXT_NOTE, ERASER
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

    // Search highlight state (with text position data)
    val searchHighlightState by viewModel.searchHighlightState.collectAsState()

    // Text selection state
    var selectPageIndex by remember { mutableIntStateOf(-1) }
    var selectStartCharIndex by remember { mutableIntStateOf(-1) }
    var selectEndCharIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(currentMatchIndex, searchResults) {
        val match = searchResults.getOrNull(currentMatchIndex)
        if (match != null) {
            lazyListState.animateScrollToItem(match.pageIndex)
        }
    }

    // Auto-scroll to search highlight match
    LaunchedEffect(searchHighlightState.currentMatchIndex, searchHighlightState.matches) {
        if (searchHighlightState.matches.isNotEmpty()) {
            val match = searchHighlightState.matches.getOrNull(searchHighlightState.currentMatchIndex)
            if (match != null) {
                lazyListState.animateScrollToItem(match.pageIndex)
            }
        }
    }

    var searchExpanded by remember { mutableStateOf(false) }

    // Annotations Active Modes
    var isPdfEditingActive by remember { mutableStateOf(false) }
    var annotationMode by remember { mutableStateOf(AnnotationMode.NONE) }
    var selectedMarkerColor by remember { mutableStateOf(Color.Yellow) }
    var selectedStrokeWidth by remember { mutableStateOf(8f) }
    var showPageJumpDialog by remember { mutableStateOf(false) }
    var showPdfToolsSheet by remember { mutableStateOf(false) }
    var showBrushSizeSlider by remember { mutableStateOf(false) }
    var showColorPickerDialog by remember { mutableStateOf(false) }

    // UI visibility state (auto-hide top bar and page indicator)
    var showControls by remember { mutableStateOf(true) }
    var showPageIndicator by remember { mutableStateOf(false) }

    // Zoom scale tracking for dynamic padding
    var currentScale by remember { mutableStateOf(1f) }
    var viewportHeight by remember { mutableStateOf(0f) }

    // Ensure controls are visible when search state changes
    LaunchedEffect(searchExpanded) {
        showControls = true
    }
    
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

    // Scroll-driven toolbar visibility and page indicator
    val nestedScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): Offset {
                if (!isPdfEditingActive) {
                    if (available.y < -10f) {
                        showControls = false
                    } else if (available.y > 10f) {
                        showControls = true
                    }
                }
                return Offset.Zero
            }
        }
    }

    // Show floating page indicator when scrolling, auto-hide after delay
    LaunchedEffect(lazyListState.isScrollInProgress, currentPageIndex) {
        if (lazyListState.isScrollInProgress) {
            showPageIndicator = true
        } else if (showPageIndicator) {
            kotlinx.coroutines.delay(1500)
            showPageIndicator = false
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(nestedScrollConnection),
        topBar = {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
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
                                viewModel.clearHighlightSearch()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Close search")
                            }

                            TextField(
                                value = searchQuery,
                                onValueChange = {
                                    viewModel.setSearchQuery(it)
                                    viewModel.searchWithHighlights(it)
                                },
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

                            if (searchHighlightState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                IconButton(onClick = { viewModel.stopHighlightSearch() }) {
                                    Icon(Icons.Default.Stop, contentDescription = "Stop search", tint = MaterialTheme.colorScheme.error)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            if (searchHighlightState.matches.isNotEmpty()) {
                                Text(
                                    text = "${searchHighlightState.currentMatchIndex + 1}/${searchHighlightState.totalMatches}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                                IconButton(onClick = { viewModel.prevHighlightMatch() }) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous")
                                }
                                IconButton(onClick = { viewModel.nextHighlightMatch() }) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next")
                                }
                            }

                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.searchWithHighlights("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
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
                                // Search button
                                IconButton(onClick = { searchExpanded = true }) {
                                    Icon(Icons.Default.Search, contentDescription = "Search")
                                }

                                // Save annotations button (only in edit mode with annotations)
                                if (isPdfEditingActive && (pagePaths.isNotEmpty() || pageTextNotes.isNotEmpty())) {
                                    IconButton(
                                        onClick = {
                                            val pathsDataMap = pagePaths.mapValues { (_, paths) ->
                                                paths.map { path ->
                                                    DrawingPathData(
                                                        points = path.points.map { DrawingPointData(it.x, it.y) },
                                                        colorHex = String.format("#%08X", path.color.toArgb()),
                                                        strokeWidth = path.strokeWidth,
                                                        isHighlight = path.isHighlight
                                                    )
                                                }
                                            }
                                            val notesDataMap = pageTextNotes.mapValues { (_, notes) ->
                                                notes.map { TextNoteData(it.text, it.x, it.y) }
                                            }
                                            viewModel.saveAllPdfAnnotations(pathsDataMap, notesDataMap) { success ->
                                                if (success) {
                                                    pagePaths.clear()
                                                    pageTextNotes.clear()
                                                    Toast.makeText(context, "Annotations saved!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Failed to save", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Save,
                                            contentDescription = "Save annotations",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                // Edit/Annotate toggle
                                IconButton(
                                    onClick = {
                                        isPdfEditingActive = !isPdfEditingActive
                                        if (!isPdfEditingActive) {
                                            annotationMode = AnnotationMode.NONE
                                        } else {
                                            annotationMode = AnnotationMode.MARKER
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isPdfEditingActive) Icons.Default.Check else Icons.Default.Edit,
                                        contentDescription = if (isPdfEditingActive) "Done" else "Edit",
                                        tint = if (isPdfEditingActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // More options menu
                                var showMoreMenu by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { showMoreMenu = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                                    }
                                    DropdownMenu(
                                        expanded = showMoreMenu,
                                        onDismissRequest = { showMoreMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Print") },
                                            onClick = {
                                                showMoreMenu = false
                                                onToolAction(ViewerTool.Print)
                                            },
                                            leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Share") },
                                            onClick = {
                                                showMoreMenu = false
                                                onToolAction(ViewerTool.Share)
                                            },
                                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Open in...") },
                                            onClick = {
                                                showMoreMenu = false
                                                onToolAction(ViewerTool.OpenIn)
                                            },
                                            leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null) }
                                        )
                                        HorizontalDivider()
                                        if (state is PdfLoadState.Success && (state as PdfLoadState.Success).pageCount > 1) {
                                            DropdownMenuItem(
                                                text = { Text("Go to page") },
                                                onClick = {
                                                    showMoreMenu = false
                                                    showPageJumpDialog = true
                                                },
                                                leadingIcon = { Icon(Icons.Default.ViewList, contentDescription = null) }
                                            )
                                        }
                                        HorizontalDivider()
                                        pdfToolActions(fileUri).forEach { (tool, label) ->
                                            DropdownMenuItem(
                                                text = { Text(label) },
                                                onClick = {
                                                    showMoreMenu = false
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
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        },
        bottomBar = {
            if (state is PdfLoadState.Success) {
                val isEditMode = isPdfEditingActive
                Column {
                    // Brush size slider
                    AnimatedVisibility(
                        visible = isEditMode && showBrushSizeSlider && annotationMode != AnnotationMode.NONE && annotationMode != AnnotationMode.TEXT_NOTE
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            tonalElevation = 4.dp,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text(
                                    text = "Brush Size: ${selectedStrokeWidth.toInt()}px",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Slider(
                                    value = selectedStrokeWidth,
                                    onValueChange = { selectedStrokeWidth = it },
                                    valueRange = 2f..30f,
                                    steps = 13
                                )
                            }
                        }
                    }

                    // Annotation toolbar (only visible in edit mode)
                    AnimatedVisibility(
                        visible = isEditMode,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            tonalElevation = 8.dp,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Pan/Select tool
                                AnnotationToolButton(
                                    icon = Icons.Default.PanTool,
                                    label = "Select",
                                    isSelected = annotationMode == AnnotationMode.NONE,
                                    onClick = { annotationMode = AnnotationMode.NONE }
                                )
                                // Highlighter
                                AnnotationToolButton(
                                    icon = Icons.Default.Highlight,
                                    label = "Highlight",
                                    isSelected = annotationMode == AnnotationMode.HIGHLIGHT,
                                    onClick = { annotationMode = AnnotationMode.HIGHLIGHT }
                                )
                                // Marker
                                AnnotationToolButton(
                                    icon = Icons.Default.Gesture,
                                    label = "Marker",
                                    isSelected = annotationMode == AnnotationMode.MARKER,
                                    onClick = { annotationMode = AnnotationMode.MARKER }
                                )
                                // Comment/Text Note
                                AnnotationToolButton(
                                    icon = Icons.Default.AddComment,
                                    label = "Comment",
                                    isSelected = annotationMode == AnnotationMode.TEXT_NOTE,
                                    onClick = { annotationMode = AnnotationMode.TEXT_NOTE }
                                )
                                // Eraser
                                AnnotationToolButton(
                                    icon = Icons.Default.AutoFixHigh,
                                    label = "Eraser",
                                    isSelected = annotationMode == AnnotationMode.ERASER,
                                    onClick = { annotationMode = AnnotationMode.ERASER }
                                )
                                // Color picker
                                IconButton(onClick = { showColorPickerDialog = true }) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(selectedMarkerColor)
                                            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                    )
                                }
                                // Brush size toggle
                                IconButton(
                                    onClick = { showBrushSizeSlider = !showBrushSizeSlider },
                                    enabled = annotationMode != AnnotationMode.NONE && annotationMode != AnnotationMode.TEXT_NOTE
                                ) {
                                    Icon(
                                        Icons.Default.Tune,
                                        contentDescription = "Brush size",
                                        tint = if (annotationMode != AnnotationMode.NONE && annotationMode != AnnotationMode.TEXT_NOTE)
                                            MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    )
                                }
                                // Undo
                                IconButton(
                                    onClick = {
                                        // Undo last stroke
                                        if (pagePaths.isNotEmpty()) {
                                            val lastPage = pagePaths.keys.maxOrNull() ?: -1
                                            if (lastPage >= 0) {
                                                val paths = pagePaths[lastPage] ?: emptyList()
                                                if (paths.isNotEmpty()) {
                                                    pagePaths[lastPage] = paths.dropLast(1)
                                                }
                                            }
                                        }
                                    },
                                    enabled = pagePaths.isNotEmpty()
                                ) {
                                    Icon(
                                        Icons.Default.Undo,
                                        contentDescription = "Undo",
                                        tint = if (pagePaths.isNotEmpty())
                                            MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
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
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { viewportHeight = it.height.toFloat() },
                        lazyListState = lazyListState,
                        onScaleChanged = { currentScale = it },
                        onTap = { showControls = !showControls }
                    ) {
                        val density = LocalDensity.current
                        val extraBottomPadding = if (currentScale > 1f && viewportHeight > 0f) {
                            with(density) {
                                (viewportHeight * ((currentScale - 1f) / currentScale)).toDp()
                            }
                        } else {
                            0.dp
                        }
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp + extraBottomPadding)
                        ) {
                            items(count = currentState.pageCount, key = { it }) { pageIndex ->
                                val isHighlighted = searchResults.getOrNull(currentMatchIndex)?.pageIndex == pageIndex
                                val pageHighlightMatches = remember(searchHighlightState.matches, pageIndex) {
                                    searchHighlightState.matches.filter { it.pageIndex == pageIndex }
                                }
                                val currentMatchIndexOnPage = remember(searchHighlightState.currentMatchIndex, searchHighlightState.matches, pageHighlightMatches, pageIndex) {
                                    val currentGlobalResult = searchHighlightState.matches.getOrNull(searchHighlightState.currentMatchIndex)
                                    if (currentGlobalResult != null && currentGlobalResult.pageIndex == pageIndex) {
                                        pageHighlightMatches.indexOf(currentGlobalResult)
                                    } else -1
                                }

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
                                        },
                                        // Text selection params
                                        selectPageIndex = selectPageIndex,
                                        selectStartCharIndex = selectStartCharIndex,
                                        selectEndCharIndex = selectEndCharIndex,
                                        onSelectionChange = { pIdx, start, end ->
                                            selectPageIndex = pIdx
                                            selectStartCharIndex = start
                                            selectEndCharIndex = end
                                        },
                                        // Search highlight params
                                        pageHighlightMatches = pageHighlightMatches,
                                        currentMatchIndexOnPage = currentMatchIndexOnPage
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

            // Floating Page Indicator
            AnimatedVisibility(
                visible = showPageIndicator && state is PdfLoadState.Success,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .navigationBarsPadding()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = "$currentPageIndex of ${state.let { if (it is PdfLoadState.Success) it.pageCount else 0 }}",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
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

            // Page Jump & Navigation Dialog
            if (showPageJumpDialog && state is PdfLoadState.Success) {
                val success = state as PdfLoadState.Success
                var targetPageText by remember { mutableStateOf("$currentPageIndex") }
                AlertDialog(
                    onDismissRequest = { showPageJumpDialog = false },
                    title = { Text("Jump to Page (1 - ${success.pageCount})") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = targetPageText,
                                onValueChange = { targetPageText = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Page Number") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            val pageNum = targetPageText.toIntOrNull()
                            if (pageNum != null && pageNum in 1..success.pageCount) {
                                coroutineScope.launch {
                                    lazyListState.animateScrollToItem(pageNum - 1)
                                }
                            }
                            showPageJumpDialog = false
                        }) {
                            Text("Go")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPageJumpDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Color Picker Dialog
            if (showColorPickerDialog) {
                ColorPickerDialog(
                    currentColor = selectedMarkerColor,
                    onColorSelected = {
                        selectedMarkerColor = it
                        showColorPickerDialog = false
                    },
                    onDismiss = { showColorPickerDialog = false }
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
    onViewEditNoteTap: (Int, String) -> Unit,
    // Text selection params
    selectPageIndex: Int,
    selectStartCharIndex: Int,
    selectEndCharIndex: Int,
    onSelectionChange: (Int, Int, Int) -> Unit,
    // Search highlight params
    pageHighlightMatches: List<SearchMatchRect>,
    currentMatchIndexOnPage: Int
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var renderError by remember { mutableStateOf(false) }
    var pageText by remember(pageIndex) { mutableStateOf("") }
    var pageTextData by remember { mutableStateOf<PageTextData?>(null) }
    var pageSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(pageIndex) {
        try {
            val rendered = viewModel.renderPage(pageIndex)
            if (rendered != null) {
                bitmap = rendered
            } else {
                renderError = true
            }
            pageText = viewModel.extractTextFromPage(pageIndex)
        } catch (e: Exception) {
            e.printStackTrace()
            renderError = true
        }
    }

    LaunchedEffect(selectPageIndex) {
        if (selectPageIndex != pageIndex) {
            pageTextData = null
        }
    }

    val aspectRatio = viewModel.getPageAspectRatio(pageIndex)

    fun distance(p1: DrawingPoint, p2: DrawingPoint): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio),
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
                .onSizeChanged { pageSize = it }
                .pointerInput(annotationMode) {
                    if (annotationMode == AnnotationMode.TEXT_NOTE) {
                        detectTapGestures(
                            onTap = { offset ->
                                val normX = offset.x / size.width.toFloat()
                                val normY = offset.y / size.height.toFloat()
                                onAddTextNoteTap(DrawingPoint(normX, normY))
                            }
                        )
                    } else if (annotationMode == AnnotationMode.ERASER) {
                        detectTapGestures(
                            onTap = { offset ->
                                val normX = offset.x / size.width.toFloat()
                                val normY = offset.y / size.height.toFloat()
                                val touchPoint = DrawingPoint(normX, normY)

                                val notes = pageTextNotes[pageIndex] ?: emptyList()
                                val remainingNotes = notes.filter { note ->
                                    distance(DrawingPoint(note.x, note.y), touchPoint) > 0.05f
                                }
                                if (remainingNotes.size != notes.size) {
                                    pageTextNotes[pageIndex] = remainingNotes
                                }
                            }
                        )
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

                    // Search Highlights Overlay
                    if (pageHighlightMatches.isNotEmpty()) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            pageHighlightMatches.forEachIndexed { index, match ->
                                val color = if (index == currentMatchIndexOnPage) {
                                    Color(0xFFFF8C00).copy(alpha = 0.5f)
                                } else {
                                    Color.Yellow.copy(alpha = 0.4f)
                                }
                                val scaleX = pageSize.width.toFloat() / bitmap!!.width.toFloat()
                                val scaleY = pageSize.height.toFloat() / bitmap!!.height.toFloat()
                                match.rects.forEach { rect ->
                                    drawRect(
                                        color = color,
                                        topLeft = Offset(rect.left * scaleX, rect.top * scaleY),
                                        size = Size(rect.width() * scaleX, rect.height() * scaleY)
                                    )
                                }
                            }
                        }
                    }

                    // Text Selection Layer with long press
                    if (annotationMode == AnnotationMode.NONE) {
                        // Long press to select text
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(pageIndex, bitmap, pageSize) {
                                    detectTapGestures(
                                        onTap = {
                                            // Clear selection when tapping anywhere on the page
                                            onSelectionChange(-1, -1, -1)
                                        },
                                        onLongPress = { touchOffset ->
                                            coroutineScope.launch {
                                                val textData = viewModel.getPageText(pageIndex)
                                                if (textData != null && textData.positions.isNotEmpty()) {
                                                    pageTextData = textData
                                                    val scaleX = pageSize.width.toFloat() / bitmap!!.width.toFloat()
                                                    val scaleY = pageSize.height.toFloat() / bitmap!!.height.toFloat()
                                                    val closest = findClosestCharIndex(
                                                        touchOffset.x, touchOffset.y,
                                                        textData.positions, scaleX, scaleY
                                                    )
                                                    if (closest != -1) {
                                                        val bounds = findWordBounds(
                                                            closest, textData.text, textData.positions
                                                        )
                                                        onSelectionChange(pageIndex, bounds.first, bounds.second)
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                        )
                    }

                    // Text Selection Overlay with handles and context menu
                    val currentPositions = pageTextData?.positions
                    if (selectPageIndex == pageIndex && selectStartCharIndex >= 0 && currentPositions != null &&
                        selectStartCharIndex < currentPositions.size && selectEndCharIndex > selectStartCharIndex &&
                        selectEndCharIndex <= currentPositions.size) {

                        val scaleX = pageSize.width.toFloat() / bitmap!!.width.toFloat()
                        val scaleY = pageSize.height.toFloat() / bitmap!!.height.toFloat()

                        val selectedPositions = currentPositions.subList(selectStartCharIndex, selectEndCharIndex)
                        val lines = mutableListOf<MutableList<TextPosition>>()
                        selectedPositions.forEach { tp ->
                            val matchingLine = lines.find { kotlin.math.abs(it.first().yDirAdj - tp.yDirAdj) < 4f }
                            if (matchingLine != null) {
                                matchingLine.add(tp)
                            } else {
                                lines.add(mutableListOf(tp))
                            }
                        }

                        val rects = lines.map { line ->
                            val minLeft = line.minOf { it.xDirAdj }
                            val maxRight = line.maxOf { it.xDirAdj + it.widthDirAdj }
                            val minTop = line.minOf { it.yDirAdj - it.heightDir }
                            val maxBottom = line.maxOf { it.yDirAdj + it.heightDir * 0.2f }
                            RectF(
                                minLeft * 1.5f * scaleX,
                                minTop * 1.5f * scaleY,
                                maxRight * 1.5f * scaleX,
                                maxBottom * 1.5f * scaleY
                            )
                        }

                        val firstChar = currentPositions[selectStartCharIndex]
                        val lastChar = currentPositions[selectEndCharIndex - 1]
                        // Tip positions - these align exactly with the text
                        val tipStartX = firstChar.xDirAdj * 1.5f * scaleX
                        val tipStartY = firstChar.yDirAdj * 1.5f * scaleY
                        val tipEndX = (lastChar.xDirAdj + lastChar.widthDirAdj) * 1.5f * scaleX
                        val tipEndY = lastChar.yDirAdj * 1.5f * scaleY
                        // Handle circle centers - offset so tip aligns with text
                        val handleOffset = with(density) { 12.dp.toPx() }
                        // Start handle points down: circle is above the tip
                        val handleStartX = tipStartX
                        val handleStartY = tipStartY - handleOffset
                        // End handle points up: circle is below the tip
                        val handleEndX = tipEndX
                        val handleEndY = tipEndY + handleOffset

                        var draggingHandle by remember { mutableStateOf<String?>(null) }
                        val currentStart by rememberUpdatedState(selectStartCharIndex)
                        val currentEnd by rememberUpdatedState(selectEndCharIndex)
                        val currentScaleX by rememberUpdatedState(scaleX)
                        val currentScaleY by rememberUpdatedState(scaleY)
                        val currentPositionsState by rememberUpdatedState(currentPositions)

                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .pointerInput(pageIndex, handleStartX, handleStartY, handleEndX, handleEndY) {
                                    // Custom gesture handler that consumes events IMMEDIATELY on handle touch
                                    // This prevents ZoomableBox from intercepting for pan-zoom
                                    awaitPointerEventScope {
                                        while (true) {
                                            val down = awaitPointerEvent(PointerEventPass.Initial)
                                            val downChange = down.changes.firstOrNull() ?: continue
                                            val downPos = downChange.position
                                            val startDist = (downPos - Offset(handleStartX, handleStartY)).getDistance()
                                            val endDist = (downPos - Offset(handleEndX, handleEndY)).getDistance()
                                            val threshold = 48.dp.toPx()
                                            val onStartHandle = startDist < threshold && startDist < endDist
                                            val onEndHandle = endDist < threshold && !onStartHandle

                                            if (onStartHandle || onEndHandle) {
                                                // Consume immediately to block ZoomableBox panning
                                                downChange.consume()
                                                val handle = if (onStartHandle) "start" else "end"
                                                draggingHandle = handle

                                                // Track drag
                                                do {
                                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                                    val change = event.changes.firstOrNull { it.id == downChange.id }
                                                    if (change != null && change.pressed) {
                                                        change.consume()
                                                        val positions = currentPositionsState
                                                        val closestIndex = findClosestCharIndexForDrag(
                                                            change.position.x, change.position.y,
                                                            positions, currentScaleX, currentScaleY
                                                        )
                                                        if (closestIndex != -1) {
                                                            if (handle == "start") {
                                                                if (closestIndex < currentEnd) {
                                                                    onSelectionChange(pageIndex, closestIndex, currentEnd)
                                                                }
                                                            } else {
                                                                if (closestIndex > currentStart) {
                                                                    onSelectionChange(pageIndex, currentStart, closestIndex + 1)
                                                                }
                                                            }
                                                        }
                                                    }
                                                } while (event.changes.any { it.id == downChange.id && it.pressed })
                                                draggingHandle = null
                                            }
                                        }
                                    }
                                }
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .matchParentSize()
                                    .pointerInput(handleStartX, handleStartY, handleEndX, handleEndY) {
                                        // Handle simple taps outside handles to clear selection
                                        detectTapGestures(
                                            onTap = { offset ->
                                                val startDist = (offset - Offset(handleStartX, handleStartY)).getDistance()
                                                val endDist = (offset - Offset(handleEndX, handleEndY)).getDistance()
                                                val threshold = 48.dp.toPx()
                                                if (startDist >= threshold && endDist >= threshold) {
                                                    onSelectionChange(-1, -1, -1)
                                                }
                                            }
                                        )
                                    }
                            ) {
                                // Draw selection highlight
                                rects.forEach { rect ->
                                    drawRoundRect(
                                        color = Color(0xFF2196F3).copy(alpha = 0.3f),
                                        topLeft = Offset(rect.left, rect.top),
                                        size = Size(rect.width(), rect.height()),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                                    )
                                }
                                // Draw start handle (teardrop pointing down at selection)
                                drawSelectionHandle(
                                    centerX = handleStartX,
                                    centerY = handleStartY,
                                    pointUp = false
                                )
                                // Draw end handle (teardrop pointing up at selection)
                                drawSelectionHandle(
                                    centerX = handleEndX,
                                    centerY = handleEndY,
                                    pointUp = true
                                )
                            }

                            // Floating context menu - minimalist design
                            val menuWidth = 180.dp
                            val menuHeight = 36.dp
                            val menuLeft = with(density) {
                                (handleStartX + handleEndX) / 2f - menuWidth.toPx() / 2f
                            }
                            val menuTop = with(density) {
                                (rects.minOfOrNull { it.top } ?: 0f) - 52.dp.toPx()
                            }

                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            x = menuLeft.roundToInt().coerceIn(
                                                8.dp.toPx().toInt(),
                                                pageSize.width - menuWidth.toPx().toInt() - 8.dp.toPx().toInt()
                                            ),
                                            y = menuTop.roundToInt().coerceAtLeast(8.dp.toPx().toInt())
                                        )
                                    }
                                    .width(menuWidth)
                                    .height(menuHeight)
                                    .shadow(4.dp, RoundedCornerShape(16.dp))
                                    .background(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable(enabled = false) {},
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val clipboardManager = LocalClipboardManager.current

                                    // Copy button - icon only for minimalist look
                                    TextButton(
                                        onClick = {
                                            val selectedText = currentPositions.subList(
                                                selectStartCharIndex, selectEndCharIndex
                                            ).joinToString("") { it.unicode }
                                            clipboardManager.setText(AnnotatedString(selectedText))
                                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                            onSelectionChange(-1, -1, -1)
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(20.dp))
                                    }

                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(18.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    )

                                    // Select All button - icon only
                                    TextButton(
                                        onClick = {
                                            onSelectionChange(pageIndex, 0, currentPositions.size)
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.SelectAll, contentDescription = "Select All", modifier = Modifier.size(20.dp))
                                    }

                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(18.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    )

                                    // Search button - icon only, opens web search
                                    TextButton(
                                        onClick = {
                                            val selectedText = currentPositions.subList(
                                                selectStartCharIndex, selectEndCharIndex
                                            ).joinToString("") { it.unicode }
                                            onSelectionChange(-1, -1, -1)
                                            try {
                                                val encodedQuery = java.net.URLEncoder.encode(selectedText, "UTF-8")
                                                val searchUri = Uri.parse("https://www.google.com/search?q=$encodedQuery")
                                                val browserIntent = Intent(Intent.ACTION_VIEW, searchUri)
                                                context.startActivity(browserIntent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }

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

/**
 * Draws a simple, clean text selection handle.
 * Circle body with a sharp triangular pointer extending toward the text.
 * The tip of the pointer indicates the exact character position.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSelectionHandle(
    centerX: Float,
    centerY: Float,
    pointUp: Boolean
) {
    val radius = 9.dp.toPx()
    val pointerLength = 12.dp.toPx()
    val pointerWidth = 5.dp.toPx()

    // Pointer tip position (this is the exact point that touches the text)
    val tipY = if (pointUp) centerY - pointerLength else centerY + pointerLength

    // Draw pointer triangle (from circle edge to tip)
    val pointerPath = Path().apply {
        // Base of triangle at circle edge
        if (pointUp) {
            moveTo(centerX - pointerWidth, centerY - radius * 0.5f)
            lineTo(centerX + pointerWidth, centerY - radius * 0.5f)
            lineTo(centerX, tipY)
        } else {
            moveTo(centerX - pointerWidth, centerY + radius * 0.5f)
            lineTo(centerX + pointerWidth, centerY + radius * 0.5f)
            lineTo(centerX, tipY)
        }
        close()
    }
    drawPath(path = pointerPath, color = Color(0xFF1565C0))

    // Draw circle body
    drawCircle(
        color = Color(0xFF1565C0),
        radius = radius,
        center = Offset(centerX, centerY)
    )

    // Small white dot in center for grip indication
    drawCircle(
        color = Color.White.copy(alpha = 0.6f),
        radius = radius * 0.3f,
        center = Offset(centerX, centerY)
    )
}

/**
 * Finds the closest character index to the touch point.
 */
private fun findClosestCharIndex(
    touchX: Float,
    touchY: Float,
    positions: List<TextPosition>,
    scaleX: Float,
    scaleY: Float
): Int {
    if (positions.isEmpty()) return -1

    var closestIndex = -1
    var minDistance = Float.MAX_VALUE

    for (i in positions.indices) {
        val tp = positions[i]
        val charCenterX = (tp.xDirAdj + tp.widthDirAdj / 2f) * 1.5f * scaleX
        val charCenterY = (tp.yDirAdj + tp.heightDir / 2f) * 1.5f * scaleY

        val dx = touchX - charCenterX
        val dy = (touchY - charCenterY) * 2f

        val distance = dx * dx + dy * dy
        if (distance < minDistance) {
            minDistance = distance
            closestIndex = i
        }
    }

    val maxAllowedDistancePx = (32f * scaleX * 1.5f).coerceAtLeast(48f)
    val maxAllowedDistSq = maxAllowedDistancePx * maxAllowedDistancePx
    if (minDistance > maxAllowedDistSq) {
        return -1
    }

    return closestIndex
}

/**
 * Finds the closest character index for handle dragging.
 * Unlike findClosestCharIndex, this has no max distance threshold so the
 * selection can be extended across multiple words by dragging the handle.
 */
private fun findClosestCharIndexForDrag(
    touchX: Float,
    touchY: Float,
    positions: List<TextPosition>,
    scaleX: Float,
    scaleY: Float
): Int {
    if (positions.isEmpty()) return -1

    var closestIndex = -1
    var minDistance = Float.MAX_VALUE

    for (i in positions.indices) {
        val tp = positions[i]
        val charCenterX = (tp.xDirAdj + tp.widthDirAdj / 2f) * 1.5f * scaleX
        val charCenterY = (tp.yDirAdj + tp.heightDir / 2f) * 1.5f * scaleY

        val dx = touchX - charCenterX
        val dy = (touchY - charCenterY) * 2f

        val distance = dx * dx + dy * dy
        if (distance < minDistance) {
            minDistance = distance
            closestIndex = i
        }
    }

    return closestIndex
}

/**
 * Expands a character index to word boundaries.
 */
private fun findWordBounds(
    charIndex: Int,
    text: String,
    positions: List<TextPosition>
): Pair<Int, Int> {
    if (positions.isEmpty() || charIndex < 0 || charIndex >= positions.size) {
        return Pair(0, 0)
    }

    fun isWordChar(charStr: String): Boolean {
        if (charStr.isEmpty()) return false
        val c = charStr[0]
        return c.isLetterOrDigit() || c == '\'' || c == '_'
    }

    fun isSameWord(idx1: Int, idx2: Int): Boolean {
        if (idx2 < 0 || idx2 >= positions.size) return false
        if (!isWordChar(positions[idx1].unicode) || !isWordChar(positions[idx2].unicode)) return false
        val right1 = positions[idx1].xDirAdj + positions[idx1].widthDirAdj
        val left2 = positions[idx2].xDirAdj
        val gap = left2 - right1
        val avgWidth = (positions[idx1].widthDirAdj + positions[idx2].widthDirAdj) / 2f
        return gap < avgWidth * 0.35f
    }

    var start = charIndex
    while (start > 0 && isSameWord(start - 1, start)) {
        start--
    }

    var end = charIndex + 1
    while (end < positions.size && isSameWord(end - 1, end)) {
        end++
    }

    return Pair(start, end)
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
            .then(
                if (annotationMode == AnnotationMode.MARKER || annotationMode == AnnotationMode.HIGHLIGHT) {
                    Modifier.pointerInput(annotationMode) {
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
                    }
                } else if (annotationMode == AnnotationMode.ERASER) {
                    Modifier.pointerInput(annotationMode) {
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
                } else {
                    Modifier
                }
            )
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
 * Custom PDF print adapter that spools pages directly from URI.
 */
class PdfDocumentAdapter(private val context: Context, private val uri: Uri, private val documentName: String) : PrintDocumentAdapter() {
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
            e.printStackTrace()
            callback?.onWriteFailed(e.localizedMessage)
        } finally {
            input?.close()
            output?.close()
        }
    }
}

@Composable
private fun AnnotationToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun ColorPickerDialog(
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = listOf(
        Color.Yellow to "Yellow",
        Color.Green to "Green",
        Color.Cyan to "Cyan",
        Color.Magenta to "Pink",
        Color.Red to "Red",
        Color.Blue to "Blue",
        Color(0xFF614700) to "Brown",
        Color.Black to "Black"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    colors.take(4).forEach { (color, name) ->
                        ColorOption(color, name, currentColor == color, { onColorSelected(color) })
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    colors.drop(4).forEach { (color, name) ->
                        ColorOption(color, name, currentColor == color, { onColorSelected(color) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ColorOption(
    color: Color,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                )
                .clickable(onClick = onClick)
        )
        Text(
            text = name,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
