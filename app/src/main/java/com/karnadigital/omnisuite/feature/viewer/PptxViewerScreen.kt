@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.karnadigital.omnisuite.feature.viewer

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.SpeakerNotes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.karnadigital.omnisuite.core.engine.document.NormalizedBounds
import com.karnadigital.omnisuite.core.engine.document.ParsedBackground
import com.karnadigital.omnisuite.core.engine.document.ParsedParagraph
import com.karnadigital.omnisuite.core.engine.document.ParsedPresentation
import com.karnadigital.omnisuite.core.engine.document.ParsedShape
import com.karnadigital.omnisuite.core.engine.document.ParsedSlide
import com.karnadigital.omnisuite.core.engine.document.PptxShapeExtractor
import com.karnadigital.omnisuite.core.engine.document.TextAlignment
import kotlinx.coroutines.launch
import java.io.File

enum class PptxViewMode {
    CONTINUOUS, PAGER, GRID, SLIDESHOW
}

private fun safeParseColor(colorHex: String?, fallback: Color = Color.White): Color {
    if (colorHex.isNullOrBlank()) return fallback
    val formatted = if (colorHex.trim().startsWith("#")) colorHex.trim() else "#${colorHex.trim()}"
    return try {
        Color(android.graphics.Color.parseColor(formatted))
    } catch (_: Throwable) {
        fallback
    }
}

/**
 * Modern High-Fidelity Slide-Deck Presentation Viewer (PPTX).
 * Powered by a single decoupled Compose-native slide rendering engine.
 */
@Composable
fun PptxViewerScreen(
    fileUri: String,
    onBack: () -> Unit,
    onToolAction: (ViewerTool) -> Unit = {},
    viewModel: PptxViewerViewModel = hiltViewModel()
) {
    val filePath = fileUri
    val onNavigateBack = onBack
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val loadState by viewModel.loadState.collectAsState()

    var viewMode by remember { mutableStateOf(PptxViewMode.CONTINUOUS) }
    var isSearchActive by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }
    var showNotesSheet by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    // Text formatting dialog state
    var showFormatter by remember { mutableStateOf(false) }
    var editingSlideIndex by remember { mutableIntStateOf(0) }
    var editingShapeIndex by remember { mutableIntStateOf(0) }
    var editingIsTitle by remember { mutableStateOf(false) }
    var blockToEdit by remember { mutableStateOf<ParsedShape.TextShape?>(null) }

    // Search query & results
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val currentMatchIndex by viewModel.currentMatchIndex.collectAsState()

    // Export launchers
    val exportPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            viewModel.exportToPdf(uri) { success, msg ->
                Toast.makeText(context, msg ?: if (success) "PDF Exported" else "Export Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val exportTxtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            viewModel.exportToOutlineTxt(uri) { success, msg ->
                Toast.makeText(context, msg ?: if (success) "Outline Exported" else "Export Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Save status notifications
    LaunchedEffect(Unit) {
        viewModel.saveStatus.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(filePath) {
        if (filePath.isNotBlank()) {
            viewModel.loadPptxFile(filePath)
        }
    }

    // Fullscreen slideshow system bar handling
    val activity = context as? Activity
    LaunchedEffect(viewMode) {
        if (activity != null) {
            val window = activity.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (viewMode == PptxViewMode.SLIDESHOW) {
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler {
        if (viewMode == PptxViewMode.SLIDESHOW) {
            viewMode = PptxViewMode.CONTINUOUS
        } else if (isSearchActive) {
            isSearchActive = false
            viewModel.clearSearch()
        } else {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            if (viewMode != PptxViewMode.SLIDESHOW) {
                TopAppBar(
                    title = {
                        if (isSearchActive) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = { Text("Search presentation...", fontSize = 14.sp) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Column {
                                val titleText = when (val state = loadState) {
                                    is PptxLoadState.Success -> state.fileName
                                    else -> "Presentation Viewer"
                                }
                                Text(
                                    text = titleText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (loadState is PptxLoadState.Success) {
                                    val slideCount = (loadState as PptxLoadState.Success).presentation.slides.size
                                    Text(
                                        text = "$slideCount slides • ${viewMode.name.lowercase().replaceFirstChar { it.uppercase() }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (isSearchActive) {
                                isSearchActive = false
                                viewModel.clearSearch()
                            } else {
                                onNavigateBack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (isSearchActive) {
                            if (searchResults.isNotEmpty()) {
                                Text(
                                    text = "${currentMatchIndex + 1}/${searchResults.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                                IconButton(onClick = { viewModel.previousSearchResult() }) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous")
                                }
                                IconButton(onClick = { viewModel.nextSearchResult() }) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next")
                                }
                            }
                            IconButton(onClick = {
                                isSearchActive = false
                                viewModel.clearSearch()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Close Search")
                            }
                        } else {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }

                            // View Mode Menu
                            var showModeMenu by remember { mutableStateOf(false) }
                            IconButton(onClick = { showModeMenu = true }) {
                                Icon(
                                    imageVector = when (viewMode) {
                                        PptxViewMode.CONTINUOUS -> Icons.Default.ViewAgenda
                                        PptxViewMode.PAGER -> Icons.Default.ViewCarousel
                                        PptxViewMode.GRID -> Icons.Default.GridView
                                        PptxViewMode.SLIDESHOW -> Icons.Default.PlayArrow
                                    },
                                    contentDescription = "View Mode"
                                )
                            }
                            DropdownMenu(
                                expanded = showModeMenu,
                                onDismissRequest = { showModeMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Continuous Scroll") },
                                    leadingIcon = { Icon(Icons.Default.ViewAgenda, null) },
                                    onClick = { viewMode = PptxViewMode.CONTINUOUS; showModeMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Single Slide Pager") },
                                    leadingIcon = { Icon(Icons.Default.ViewCarousel, null) },
                                    onClick = { viewMode = PptxViewMode.PAGER; showModeMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Slide Grid Overview") },
                                    leadingIcon = { Icon(Icons.Default.GridView, null) },
                                    onClick = { viewMode = PptxViewMode.GRID; showModeMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Fullscreen Slideshow") },
                                    leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                                    onClick = { viewMode = PptxViewMode.SLIDESHOW; showModeMenu = false }
                                )
                            }

                            // Edit Mode Toggle
                            IconButton(onClick = {
                                isEditMode = !isEditMode
                                Toast.makeText(context, if (isEditMode) "Edit Mode: Tap any text to edit" else "Viewing Mode", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit",
                                    tint = if (isEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // More Options Menu
                            var showMoreMenu by remember { mutableStateOf(false) }
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Speaker Notes") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.SpeakerNotes, null) },
                                    onClick = { showNotesSheet = true; showMoreMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export to PDF") },
                                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, null) },
                                    onClick = {
                                        showMoreMenu = false
                                        val defaultName = File(filePath).nameWithoutExtension + ".pdf"
                                        exportPdfLauncher.launch(defaultName)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Extract Outline (TXT)") },
                                    leadingIcon = { Icon(Icons.Default.Description, null) },
                                    onClick = {
                                        showMoreMenu = false
                                        val defaultName = File(filePath).nameWithoutExtension + "_outline.txt"
                                        exportTxtLauncher.launch(defaultName)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Save Changes") },
                                    leadingIcon = { Icon(Icons.Default.Save, null) },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.saveActivePresentation { success, msg ->
                                            Toast.makeText(context, msg ?: if (success) "Saved" else "Save Failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Presentation Info") },
                                    leadingIcon = { Icon(Icons.Default.Info, null) },
                                    onClick = { showInfoDialog = true; showMoreMenu = false }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (viewMode == PptxViewMode.SLIDESHOW) PaddingValues(0.dp) else paddingValues)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            when (val state = loadState) {
                is PptxLoadState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Parsing presentation slides...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is PptxLoadState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Unable to open presentation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { viewModel.loadPptxFile(filePath) }) {
                            Text("Retry")
                        }
                    }
                }

                is PptxLoadState.Success -> {
                    val presentation = state.presentation
                    val renderPaths = state.slideRenderPaths
                    val pagerState = rememberPagerState(initialPage = 0, pageCount = { presentation.slides.size })
                    val listState = rememberLazyListState()

                    // Scroll to search match if active
                    LaunchedEffect(currentMatchIndex) {
                        if (currentMatchIndex >= 0 && currentMatchIndex < searchResults.size) {
                            val targetPage = searchResults[currentMatchIndex].pageIndex
                            if (viewMode == PptxViewMode.PAGER || viewMode == PptxViewMode.SLIDESHOW) {
                                pagerState.animateScrollToPage(targetPage)
                            } else if (viewMode == PptxViewMode.CONTINUOUS) {
                                listState.animateScrollToItem(targetPage)
                            }
                        }
                    }

                    when (viewMode) {
                        PptxViewMode.CONTINUOUS -> {
                            ContinuousView(
                                presentation = presentation,
                                renderPaths = renderPaths,
                                listState = listState,
                                isEditMode = isEditMode,
                                onEditShapeClick = { slideIdx, shape, isTitle, shapeIdx ->
                                    editingSlideIndex = slideIdx
                                    blockToEdit = shape
                                    editingIsTitle = isTitle
                                    editingShapeIndex = shapeIdx
                                    showFormatter = true
                                }
                            )
                        }

                        PptxViewMode.PAGER -> {
                            PagerView(
                                presentation = presentation,
                                renderPaths = renderPaths,
                                pagerState = pagerState,
                                isEditMode = isEditMode,
                                onEditShapeClick = { slideIdx, shape, isTitle, shapeIdx ->
                                    editingSlideIndex = slideIdx
                                    blockToEdit = shape
                                    editingIsTitle = isTitle
                                    editingShapeIndex = shapeIdx
                                    showFormatter = true
                                }
                            )
                        }

                        PptxViewMode.GRID -> {
                            GridView(
                                presentation = presentation,
                                renderPaths = renderPaths,
                                onSlideClick = { index ->
                                    coroutineScope.launch {
                                        pagerState.scrollToPage(index)
                                    }
                                    viewMode = PptxViewMode.PAGER
                                }
                            )
                        }

                        PptxViewMode.SLIDESHOW -> {
                            SlideshowView(
                                presentation = presentation,
                                renderPaths = renderPaths,
                                pagerState = pagerState,
                                onExit = { viewMode = PptxViewMode.CONTINUOUS }
                            )
                        }
                    }

                    // Speaker Notes Bottom Sheet
                    if (showNotesSheet) {
                        val activeIndex = if (viewMode == PptxViewMode.PAGER || viewMode == PptxViewMode.SLIDESHOW) {
                            pagerState.currentPage
                        } else {
                            listState.firstVisibleItemIndex.coerceIn(0, presentation.slides.size - 1)
                        }
                        val activeSlide = presentation.slides.getOrNull(activeIndex)

                        ModalBottomSheet(
                            onDismissRequest = { showNotesSheet = false }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp)
                            ) {
                                Text(
                                    text = "Speaker Notes • Slide ${activeIndex + 1}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                val notesText = activeSlide?.speakerNotes
                                if (!notesText.isNullOrBlank()) {
                                    Text(
                                        text = notesText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        lineHeight = 22.sp
                                    )
                                } else {
                                    Text(
                                        text = "No speaker notes for this slide.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }

                    // Presentation Info Dialog
                    if (showInfoDialog) {
                        AlertDialog(
                            onDismissRequest = { showInfoDialog = false },
                            title = { Text("Presentation Details", fontWeight = FontWeight.Bold) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("File: ${state.fileName}")
                                    Text("Slides: ${presentation.slides.size}")
                                    val f = File(filePath)
                                    if (f.exists()) {
                                        Text("Size: ${f.length() / 1024} KB")
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showInfoDialog = false }) {
                                    Text("Close")
                                }
                            }
                        )
                    }

                    // Text & Formatting Dialog
                    if (showFormatter && blockToEdit != null) {
                        val slide = presentation.slides[editingSlideIndex]
                        val bgHex = (slide.background as? ParsedBackground.SolidColor)?.colorHex
                        PptxTextFormatterDialog(
                            textBlock = blockToEdit!!,
                            isTitle = editingIsTitle,
                            initialNotes = slide.speakerNotes,
                            initialBgColorHex = bgHex,
                            onDismiss = { showFormatter = false },
                            onSave = { newText, isBold, isItalic, isUnderline, textColorHex, notes, fontSize, bgColor ->
                                viewModel.updateSlideTextShape(
                                    slideIndex = editingSlideIndex,
                                    isTitle = editingIsTitle,
                                    blockIndex = editingShapeIndex,
                                    newText = newText,
                                    isBold = isBold,
                                    isItalic = isItalic,
                                    isUnderline = isUnderline,
                                    textColorHex = textColorHex,
                                    comment = notes,
                                    fontSizePt = fontSize
                                )
                                if (bgColor != null) {
                                    viewModel.setSlideBackground(editingSlideIndex, bgColor)
                                }
                                showFormatter = false
                            }
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// CONTINUOUS VIEW (VERTICAL STREAM)
// =============================================================================

@Composable
fun ContinuousView(
    presentation: ParsedPresentation,
    renderPaths: List<String?>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    isEditMode: Boolean,
    onEditShapeClick: (slideIdx: Int, shape: ParsedShape.TextShape, isTitle: Boolean, shapeIdx: Int) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        itemsIndexed(presentation.slides) { index, slide ->
            ZoomableBox(modifier = Modifier.fillMaxWidth()) {
                SlideCardItem(
                    slide = slide,
                    slideRenderPath = renderPaths.getOrNull(index),
                    slideIndex = index,
                    isEditMode = isEditMode,
                    onEditShapeClick = { shape, isTitle, shapeIdx ->
                        onEditShapeClick(index, shape, isTitle, shapeIdx)
                    }
                )
            }
        }
    }
}

// =============================================================================
// PAGER VIEW (HORIZONTAL SLIDE DECK WITH THUMBNAIL STRIP)
// =============================================================================

@Composable
fun PagerView(
    presentation: ParsedPresentation,
    renderPaths: List<String?>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    isEditMode: Boolean,
    onEditShapeClick: (slideIdx: Int, shape: ParsedShape.TextShape, isTitle: Boolean, shapeIdx: Int) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // Main slide deck pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { pageIndex ->
            val slide = presentation.slides[pageIndex]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                ZoomableBox(modifier = Modifier.fillMaxWidth()) {
                    SlideCardItem(
                        slide = slide,
                        slideRenderPath = renderPaths.getOrNull(pageIndex),
                        slideIndex = pageIndex,
                        isEditMode = isEditMode,
                        onEditShapeClick = { shape, isTitle, shapeIdx ->
                            onEditShapeClick(pageIndex, shape, isTitle, shapeIdx)
                        }
                    )
                }
            }
        }

        // Bottom thumbnail navigation strip
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(presentation.slides) { index, slide ->
                    val isActive = pagerState.currentPage == index

                    Card(
                        shape = RoundedCornerShape(8.dp),
                        border = if (isActive) BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier
                            .width(84.dp)
                            .aspectRatio(slide.aspectRatio)
                            .clickable {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                            .graphicsLayer { this.alpha = if (isActive) 1f else 0.65f }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            ParsedSlideView(
                                slide = slide,
                                modifier = Modifier.fillMaxSize()
                            )

                            Surface(
                                shape = RoundedCornerShape(topStart = 4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                modifier = Modifier.align(Alignment.BottomEnd)
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// GRID VIEW (2-COLUMN SLIDE OVERVIEW)
// =============================================================================

@Composable
fun GridView(
    presentation: ParsedPresentation,
    renderPaths: List<String?>,
    onSlideClick: (Int) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(presentation.slides) { index, slide ->
            Card(
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(slide.aspectRatio)
                    .clickable { onSlideClick(index) }
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ParsedSlideView(
                        slide = slide,
                        modifier = Modifier.fillMaxSize()
                    )

                    Surface(
                        shape = RoundedCornerShape(topStart = 6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                        modifier = Modifier.align(Alignment.BottomEnd)
                    ) {
                        Text(
                            text = "${index + 1}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// FULLSCREEN SLIDESHOW VIEW
// =============================================================================

@Composable
fun SlideshowView(
    presentation: ParsedPresentation,
    renderPaths: List<String?>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    onExit: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val slide = presentation.slides[pageIndex]

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val width = size.width
                            if (offset.x > width * 0.65f) {
                                if (pagerState.currentPage < presentation.slides.size - 1) {
                                    coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                }
                            } else if (offset.x < width * 0.35f) {
                                if (pagerState.currentPage > 0) {
                                    coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                ParsedSlideView(
                    slide = slide,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Overlay controls (Slide indicator and Exit button)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier.padding(4.dp)
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${presentation.slides.size}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            IconButton(
                onClick = onExit,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Exit Slideshow", tint = Color.White)
            }
        }
    }
}

// =============================================================================
// SLIDE CARD COMPONENT (HIGH-FIDELITY BITMAP + INTERACTIVE OVERLAY)
// =============================================================================

@Composable
fun SlideCardItem(
    slide: ParsedSlide,
    slideRenderPath: String?,
    slideIndex: Int,
    isEditMode: Boolean,
    onEditShapeClick: (shape: ParsedShape.TextShape, isTitle: Boolean, shapeIdx: Int) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(slide.aspectRatio)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val slideW = maxWidth.value
            val slideH = maxHeight.value

            // 1. Compose-native Slide Renderer consuming pure ParsedSlide
            ParsedSlideView(
                slide = slide,
                modifier = Modifier.fillMaxSize()
            )

            // 2. Interactive Editing Overlays (Active when user enters Edit Mode)
            if (isEditMode) {
                slide.textShapes.forEachIndexed { shapeIdx, shape ->
                    Box(
                        modifier = Modifier
                            .offset(x = (shape.bounds.left * slideW).dp, y = (shape.bounds.top * slideH).dp)
                            .width((shape.bounds.width * slideW).dp)
                            .heightIn(min = (shape.bounds.height * slideH).dp)
                            .clickable { onEditShapeClick(shape, shape.isTitle, shapeIdx) }
                            .background(
                                if (shape.isTitle) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (shape.isTitle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                }
            }
        }
    }
}

// =============================================================================
// UNIFIED COMPOSE-NATIVE SLIDE RENDERER
// =============================================================================

@Composable
fun ParsedSlideView(
    slide: ParsedSlide,
    modifier: Modifier = Modifier
) {
    // Default background is fixed WHITE (independent of dark/light theme surface color)
    val bgColor = when (val bg = slide.background) {
        is ParsedBackground.SolidColor -> safeParseColor(bg.colorHex, Color.White)
        else -> Color.White
    }

    BoxWithConstraints(
        modifier = modifier
            .background(bgColor)
            .clipToBounds()
    ) {
        val slideWidthPx = maxWidth.value
        val slideHeightPx = maxHeight.value

        // 1. Background image fill if present
        if (slide.background is ParsedBackground.ImageFill) {
            val bgImg = slide.background as ParsedBackground.ImageFill
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(bgImg.imageBytes)
                    .crossfade(true)
                    .build(),
                contentDescription = "Background Image",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. Render shapes sorted by z-index
        slide.shapes.sortedBy { it.zIndex }.forEach { shape ->
            ParsedShapeItem(
                shape = shape,
                slideWidthPx = slideWidthPx,
                slideHeightPx = slideHeightPx
            )
        }
    }
}

@Composable
fun ParsedShapeItem(
    shape: ParsedShape,
    slideWidthPx: Float,
    slideHeightPx: Float
) {
    val xDp = (shape.bounds.left * slideWidthPx).dp
    val yDp = (shape.bounds.top * slideHeightPx).dp
    val wDp = (shape.bounds.width * slideWidthPx).dp
    val hDp = (shape.bounds.height * slideHeightPx).dp

    val shapeModifier = Modifier
        .offset(x = xDp, y = yDp)
        .size(width = wDp, height = hDp)

    when (shape) {
        is ParsedShape.TextShape -> {
            Box(
                modifier = shapeModifier
                    .background(safeParseColor(shape.backgroundColorHex, Color.Transparent))
                    .padding(horizontal = (wDp.value * 0.02f).coerceIn(2f, 12f).dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    shape.paragraphs.forEach { p ->
                        ParsedParagraphItem(paragraph = p, isTitle = shape.isTitle, slideWidthPx = slideWidthPx)
                    }
                }
            }
        }

        is ParsedShape.ImageShape -> {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(shape.imageBytes)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = shapeModifier
            )
        }

        is ParsedShape.VectorShape -> {
            val fill = safeParseColor(shape.fillColorHex, Color.Transparent)
            val stroke = safeParseColor(shape.strokeColorHex, Color.Transparent)

            Canvas(modifier = shapeModifier) {
                val size = this.size
                if (shape.fillColorHex != null) {
                    drawRect(color = fill, size = size)
                }
                if (shape.strokeColorHex != null) {
                    drawRect(
                        color = stroke,
                        size = size,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = shape.strokeWidthDp.dp.toPx())
                    )
                }
            }
        }

        is ParsedShape.TableShape -> {
            Column(
                modifier = shapeModifier
                    .border(1.dp, Color.LightGray)
            ) {
                shape.cells.forEach { row ->
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        row.forEach { cell ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .border(0.5.dp, Color.LightGray)
                                    .background(safeParseColor(cell.backgroundColorHex, Color.Transparent))
                                    .padding(2.dp)
                            ) {
                                cell.textShape?.paragraphs?.forEach { p ->
                                    ParsedParagraphItem(paragraph = p, isTitle = false, slideWidthPx = slideWidthPx)
                                }
                            }
                        }
                    }
                }
            }
        }

        is ParsedShape.GroupShape -> {
            Box(modifier = Modifier.fillMaxSize()) {
                shape.children.sortedBy { it.zIndex }.forEach { child ->
                    ParsedShapeItem(
                        shape = child,
                        slideWidthPx = slideWidthPx,
                        slideHeightPx = slideHeightPx
                    )
                }
            }
        }
    }
}

@Composable
fun ParsedParagraphItem(
    paragraph: ParsedParagraph,
    isTitle: Boolean,
    slideWidthPx: Float
) {
    val textAlign = when (paragraph.alignment) {
        TextAlignment.CENTER -> TextAlign.Center
        TextAlignment.RIGHT -> TextAlign.Right
        TextAlignment.JUSTIFY -> TextAlign.Justify
        else -> TextAlign.Left
    }

    // Dynamic scaling based on slide width px budget (ensuring legible minimum readable size)
    val scaleFactor = (slideWidthPx / 400f).coerceIn(0.6f, 2.5f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (paragraph.hasBullet) {
            val bullet = if (paragraph.bulletChar.isNotBlank()) paragraph.bulletChar else "•"
            Text(
                text = "$bullet ",
                fontSize = (14f * scaleFactor).sp,
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray
            )
        }

        val annotatedString = androidx.compose.ui.text.buildAnnotatedString {
            paragraph.runs.forEach { run ->
                val runColor = safeParseColor(run.textColorHex, if (isTitle) Color.Black else Color.DarkGray)
                val fontSize = (run.fontSizePt * scaleFactor).coerceAtLeast(10f).sp

                pushStyle(
                    androidx.compose.ui.text.SpanStyle(
                        color = runColor,
                        fontSize = fontSize,
                        fontWeight = if (run.isBold || isTitle) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (run.isItalic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                        textDecoration = if (run.isUnderline) androidx.compose.ui.text.style.TextDecoration.Underline else androidx.compose.ui.text.style.TextDecoration.None
                    )
                )
                append(run.text)
                pop()
            }
        }

        Text(
            text = annotatedString,
            textAlign = textAlign,
            modifier = Modifier.weight(1f)
        )
    }
}

// =============================================================================
// ZOOMABLE BOX (PINCH-TO-ZOOM AND PAN)
// =============================================================================

@Composable
fun ZoomableBox(
    modifier: Modifier = Modifier,
    maxScale: Float = 4.0f,
    minScale: Float = 1.0f,
    content: @Composable BoxScope.() -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(minScale, maxScale)
        val maxOffsetX = (sizeWidth(scale) - sizeWidth(1f)) / 2f
        val maxOffsetY = (sizeHeight(scale) - sizeHeight(1f)) / 2f

        scale = newScale
        offset = if (scale > 1f) {
            Offset(
                x = (offset.x + offsetChange.x).coerceIn(-maxOffsetX.coerceAtLeast(0f), maxOffsetX.coerceAtLeast(0f)),
                y = (offset.y + offsetChange.y).coerceIn(-maxOffsetY.coerceAtLeast(0f), maxOffsetY.coerceAtLeast(0f))
            )
        } else {
            Offset.Zero
        }
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1.2f) 1f else 2.5f
                        offset = Offset.Zero
                    }
                )
            }
            .transformable(state = state)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        content = content
    )
}

private fun sizeWidth(scale: Float): Float = 1080f * scale
private fun sizeHeight(scale: Float): Float = 1920f * scale

// =============================================================================
// TEXT & BACKGROUND FORMATTER DIALOG
// =============================================================================

@Composable
fun PptxTextFormatterDialog(
    textBlock: ParsedShape.TextShape,
    isTitle: Boolean,
    initialNotes: String?,
    initialBgColorHex: String?,
    onDismiss: () -> Unit,
    onSave: (
        newText: String,
        isBold: Boolean,
        isItalic: Boolean,
        isUnderline: Boolean,
        textColorHex: String?,
        notes: String?,
        fontSizePt: Float,
        bgColorHex: String?
    ) -> Unit
) {
    var text by remember { mutableStateOf(textBlock.primaryText.ifBlank { textBlock.fullText }) }
    var isBold by remember { mutableStateOf(textBlock.isBold) }
    var isItalic by remember { mutableStateOf(textBlock.isItalic) }
    var isUnderline by remember { mutableStateOf(textBlock.isUnderline) }
    var textColorHex by remember { mutableStateOf(textBlock.textColorHex ?: "#000000") }
    var notes by remember { mutableStateOf(initialNotes ?: "") }
    var fontSizePt by remember { mutableFloatStateOf(textBlock.fontSizePt) }
    var slideBgColorHex by remember { mutableStateOf(initialBgColorHex ?: "#FFFFFF") }

    val presetColors = listOf("#000000", "#FFFFFF", "#2563EB", "#16A34A", "#DC2626", "#D97706", "#7C3AED", "#0891B2")
    val presetBgColors = listOf("#FFFFFF", "#F8FAFC", "#F1F5F9", "#FEF3C7", "#DCFCE7", "#E0E7FF", "#FCE7F3", "#1E293B")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isTitle) "Edit Slide Title" else "Edit Text Shape",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Slide Text") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )

                // Style Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isBold,
                        onClick = { isBold = !isBold },
                        label = { Text("B", fontWeight = FontWeight.Bold) }
                    )
                    FilterChip(
                        selected = isItalic,
                        onClick = { isItalic = !isItalic },
                        label = { Text("I", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) }
                    )
                    FilterChip(
                        selected = isUnderline,
                        onClick = { isUnderline = !isUnderline },
                        label = { Text("U") }
                    )
                }

                // Font Size Slider
                Column {
                    Text(
                        text = "Font Size: ${fontSizePt.toInt()} pt",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = fontSizePt,
                        onValueChange = { fontSizePt = it },
                        valueRange = 10f..48f,
                        steps = 19
                    )
                }

                // Text Color Palette
                Column {
                    Text("Text Color", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(presetColors.size) { idx ->
                            val hex = presetColors[idx]
                            val isSelected = textColorHex.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(safeParseColor(hex, Color.Black))
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                                    .clickable { textColorHex = hex }
                            )
                        }
                    }
                }

                // Slide Background Color Palette
                Column {
                    Text("Slide Background", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(presetBgColors.size) { idx ->
                            val hex = presetBgColors[idx]
                            val isSelected = slideBgColorHex.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(safeParseColor(hex, Color.White))
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                                    .clickable { slideBgColorHex = hex }
                            )
                        }
                    }
                }

                // Speaker Notes Field
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Speaker Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        text,
                        isBold,
                        isItalic,
                        isUnderline,
                        textColorHex,
                        notes,
                        fontSizePt,
                        slideBgColorHex
                    )
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
