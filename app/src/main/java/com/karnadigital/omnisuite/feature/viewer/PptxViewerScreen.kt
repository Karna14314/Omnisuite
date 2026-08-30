@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.karnadigital.omnisuite.feature.viewer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.karnadigital.omnisuite.core.engine.document.ParsedShape
import com.karnadigital.omnisuite.core.engine.document.ParsedSlide
import com.karnadigital.omnisuite.core.engine.document.TextContent
import com.karnadigital.omnisuite.core.engine.document.ParsedSlideView
import kotlinx.coroutines.launch

enum class PptxViewMode { CONTINUOUS, PAGER, GRID, SLIDESHOW }

private fun safeParseColor(colorHex: String?, fallback: Color = Color.White): Color {
    if (colorHex.isNullOrBlank()) return fallback
    val formatted = if (colorHex.trim().startsWith("#")) colorHex.trim() else "#${colorHex.trim()}"
    return try { Color(android.graphics.Color.parseColor(formatted)) } catch (_: Throwable) { fallback }
}

@Composable
fun PptxViewerScreen(
    fileUri: String,
    onBack: () -> Unit,
    onToolAction: (ViewerTool) -> Unit = {},
    viewModel: PptxViewerViewModel = hiltViewModel(),
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

    var showFormatter by remember { mutableStateOf(false) }
    var editingSlideIndex by remember { mutableIntStateOf(0) }
    var editingShapeIndex by remember { mutableIntStateOf(0) }
    var editingIsTitle by remember { mutableStateOf(false) }
    var editingShape by remember { mutableStateOf<ParsedShape?>(null) }

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val currentMatchIndex by viewModel.currentMatchIndex.collectAsState()

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

    LaunchedEffect(Unit) {
        viewModel.saveStatus.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    LaunchedEffect(filePath) {
        if (filePath.isNotBlank()) viewModel.loadPptxFile(filePath)
    }

    val activity = context as? Activity
    LaunchedEffect(viewMode) {
        if (activity != null) {
            val window = activity.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (viewMode == PptxViewMode.SLIDESHOW) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler {
        when {
            viewMode == PptxViewMode.SLIDESHOW -> viewMode = PptxViewMode.CONTINUOUS
            isSearchActive -> { isSearchActive = false; viewModel.clearSearch() }
            else -> onNavigateBack()
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
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Column {
                                val titleText = when (val s = loadState) {
                                    is PptxLoadState.Success -> s.fileName
                                    else -> "Presentation Viewer"
                                }
                                Text(text = titleText, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                if (loadState is PptxLoadState.Success) {
                                    Text(text = "${(loadState as PptxLoadState.Success).presentation.slides.size} slides • ${viewMode.name.lowercase().replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (isSearchActive) { isSearchActive = false; viewModel.clearSearch() } else onNavigateBack()
                        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    },
                    actions = {
                        if (isSearchActive) {
                            if (searchResults.isNotEmpty()) {
                                Text(text = "${currentMatchIndex + 1}/${searchResults.size}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 4.dp))
                                IconButton(onClick = { viewModel.previousSearchResult() }) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous") }
                                IconButton(onClick = { viewModel.nextSearchResult() }) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next") }
                            }
                            IconButton(onClick = { isSearchActive = false; viewModel.clearSearch() }) { Icon(Icons.Default.Close, contentDescription = "Close Search") }
                        } else {
                            IconButton(onClick = { isSearchActive = true }) { Icon(Icons.Default.Search, contentDescription = "Search") }
                            ViewModeMenu(viewMode) { viewMode = it }
                            IconButton(onClick = {
                                isEditMode = !isEditMode
                                Toast.makeText(context, if (isEditMode) "Edit Mode: Tap any text to edit" else "Viewing Mode", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = if (isEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                            MoreOptionsMenu(
                                onShowNotes = { showNotesSheet = true },
                                onExportPdf = { exportPdfLauncher.launch(File(filePath).nameWithoutExtension + ".pdf") },
                                onExportTxt = { exportTxtLauncher.launch(File(filePath).nameWithoutExtension + "_outline.txt") },
                                onSave = { viewModel.saveActivePresentation { s, m -> Toast.makeText(context, m ?: if (s) "Saved" else "Save Failed", Toast.LENGTH_SHORT).show() } },
                                onInfo = { showInfoDialog = true },
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (viewMode == PptxViewMode.SLIDESHOW) PaddingValues(0.dp) else paddingValues)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            when (val state = loadState) {
                is PptxLoadState.Loading -> {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Rendering presentation slides...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                is PptxLoadState.Error -> {
                    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Unable to open presentation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(state.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { viewModel.loadPptxFile(filePath) }) { Text("Retry") }
                    }
                }
                is PptxLoadState.Success -> {
                    val presentation = state.presentation
                    val pagerState = rememberPagerState(initialPage = 0, pageCount = { presentation.slides.size })
                    val listState = rememberLazyListState()

                    LaunchedEffect(currentMatchIndex) {
                        if (currentMatchIndex >= 0 && currentMatchIndex < searchResults.size) {
                            val target = searchResults[currentMatchIndex].pageIndex
                            if (viewMode == PptxViewMode.PAGER || viewMode == PptxViewMode.SLIDESHOW) pagerState.animateScrollToPage(target)
                            else if (viewMode == PptxViewMode.CONTINUOUS) listState.animateScrollToItem(target)
                        }
                    }

                    when (viewMode) {
                        PptxViewMode.CONTINUOUS -> ContinuousView(presentation, listState, isEditMode) { slideIdx, shape, isTitle, shapeIdx ->
                            editingSlideIndex = slideIdx; editingShape = shape; editingIsTitle = isTitle; editingShapeIndex = shapeIdx; showFormatter = true
                        }
                        PptxViewMode.PAGER -> PagerView(presentation, pagerState, isEditMode) { slideIdx, shape, isTitle, shapeIdx ->
                            editingSlideIndex = slideIdx; editingShape = shape; editingIsTitle = isTitle; editingShapeIndex = shapeIdx; showFormatter = true
                        }
                        PptxViewMode.GRID -> GridView(presentation) { index -> coroutineScope.launch { pagerState.scrollToPage(index) }; viewMode = PptxViewMode.PAGER }
                        PptxViewMode.SLIDESHOW -> SlideshowView(presentation, pagerState) { viewMode = PptxViewMode.CONTINUOUS }
                    }

                    if (showNotesSheet) {
                        val activeIndex = if (viewMode == PptxViewMode.PAGER || viewMode == PptxViewMode.SLIDESHOW) pagerState.currentPage
                        else listState.firstVisibleItemIndex.coerceIn(0, presentation.slides.size - 1)
                        val activeSlide = presentation.slides.getOrNull(activeIndex)
                        ModalBottomSheet(onDismissRequest = { showNotesSheet = false }) {
                            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                                Text("Speaker Notes • Slide ${activeIndex + 1}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(12.dp))
                                val notes = activeSlide?.speakerNotes
                                if (!notes.isNullOrBlank()) Text(notes, style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp)
                                else Text("No speaker notes for this slide.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }

                    if (showInfoDialog) {
                        AlertDialog(
                            onDismissRequest = { showInfoDialog = false },
                            title = { Text("Presentation Details", fontWeight = FontWeight.Bold) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("File: ${state.fileName}")
                                    Text("Slides: ${presentation.slides.size}")
                                    val f = File(filePath)
                                    if (f.exists()) Text("Size: ${f.length() / 1024} KB")
                                }
                            },
                            confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("Close") } },
                        )
                    }

                    if (showFormatter && editingShape != null) {
                        val slide = presentation.slides[editingSlideIndex]
                        PptxTextFormatterDialog(
                            shape = editingShape!!,
                            isTitle = editingIsTitle,
                            initialNotes = slide.speakerNotes,
                            initialBgColorHex = null,
                            onDismiss = { showFormatter = false },
                            onSave = { newText, isBold, isItalic, isUnderline, textColorHex, notes, fontSize, bgColor ->
                                viewModel.updateSlideTextShape(
                                    slideIndex = editingSlideIndex, isTitle = editingIsTitle, blockIndex = editingShapeIndex,
                                    newText = newText, isBold = isBold, isItalic = isItalic, isUnderline = isUnderline,
                                    textColorHex = textColorHex, comment = notes, fontSizePt = fontSize,
                                )
                                if (bgColor != null) viewModel.setSlideBackground(editingSlideIndex, bgColor)
                                showFormatter = false
                            },
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// VIEW MODE MENU
// =============================================================================

@Composable
private fun ViewModeMenu(current: PptxViewMode, onChange: (PptxViewMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(
            when (current) {
                PptxViewMode.CONTINUOUS -> Icons.Default.ViewAgenda
                PptxViewMode.PAGER -> Icons.Default.ViewCarousel
                PptxViewMode.GRID -> Icons.Default.GridView
                PptxViewMode.SLIDESHOW -> Icons.Default.PlayArrow
            },
            contentDescription = "View Mode",
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(text = { Text("Continuous Scroll") }, leadingIcon = { Icon(Icons.Default.ViewAgenda, null) }, onClick = { onChange(PptxViewMode.CONTINUOUS); expanded = false })
        DropdownMenuItem(text = { Text("Single Slide Pager") }, leadingIcon = { Icon(Icons.Default.ViewCarousel, null) }, onClick = { onChange(PptxViewMode.PAGER); expanded = false })
        DropdownMenuItem(text = { Text("Slide Grid Overview") }, leadingIcon = { Icon(Icons.Default.GridView, null) }, onClick = { onChange(PptxViewMode.GRID); expanded = false })
        DropdownMenuItem(text = { Text("Fullscreen Slideshow") }, leadingIcon = { Icon(Icons.Default.PlayArrow, null) }, onClick = { onChange(PptxViewMode.SLIDESHOW); expanded = false })
    }
}

@Composable
private fun MoreOptionsMenu(onShowNotes: () -> Unit, onExportPdf: () -> Unit, onExportTxt: () -> Unit, onSave: () -> Unit, onInfo: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(text = { Text("Speaker Notes") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.SpeakerNotes, null) }, onClick = { onShowNotes(); expanded = false })
        DropdownMenuItem(text = { Text("Export to PDF") }, leadingIcon = { Icon(Icons.Default.PictureAsPdf, null) }, onClick = { onExportPdf(); expanded = false })
        DropdownMenuItem(text = { Text("Extract Outline (TXT)") }, leadingIcon = { Icon(Icons.Default.Description, null) }, onClick = { onExportTxt(); expanded = false })
        DropdownMenuItem(text = { Text("Save Changes") }, leadingIcon = { Icon(Icons.Default.Save, null) }, onClick = { onSave(); expanded = false })
        DropdownMenuItem(text = { Text("Presentation Info") }, leadingIcon = { Icon(Icons.Default.Info, null) }, onClick = { onInfo(); expanded = false })
    }
}

// =============================================================================
// CONTINUOUS VIEW
// =============================================================================

@Composable
fun ContinuousView(
    presentation: com.karnadigital.omnisuite.core.engine.document.ParsedPresentation,
    listState: androidx.compose.foundation.lazy.LazyListState,
    isEditMode: Boolean,
    onEditShapeClick: (slideIdx: Int, shape: ParsedShape, isTitle: Boolean, shapeIdx: Int) -> Unit,
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        itemsIndexed(presentation.slides) { index, slide ->
            ZoomableBox(modifier = Modifier.fillMaxWidth()) {
                SlideCardItem(slide, index, presentation.aspectRatio, isEditMode) { shape, isTitle, shapeIdx -> onEditShapeClick(index, shape, isTitle, shapeIdx) }
            }
        }
    }
}

// =============================================================================
// PAGER VIEW
// =============================================================================

@Composable
fun PagerView(
    presentation: com.karnadigital.omnisuite.core.engine.document.ParsedPresentation,
    pagerState: androidx.compose.foundation.pager.PagerState,
    isEditMode: Boolean,
    onEditShapeClick: (slideIdx: Int, shape: ParsedShape, isTitle: Boolean, shapeIdx: Int) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { pageIndex ->
            val slide = presentation.slides[pageIndex]
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                ZoomableBox(modifier = Modifier.fillMaxWidth()) {
                    SlideCardItem(slide, pageIndex, presentation.aspectRatio, isEditMode) { shape, isTitle, shapeIdx -> onEditShapeClick(pageIndex, shape, isTitle, shapeIdx) }
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
            androidx.compose.foundation.lazy.LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                itemsIndexed(presentation.slides) { index, slide ->
                    val isActive = pagerState.currentPage == index
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        border = if (isActive) BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.width(84.dp).aspectRatio(presentation.aspectRatio).clickable { coroutineScope.launch { pagerState.animateScrollToPage(index) } }.graphicsLayer { alpha = if (isActive) 1f else 0.65f },
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            ParsedSlideView(slide, minTextSp = 7f)
                            Surface(shape = RoundedCornerShape(topStart = 4.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f), modifier = Modifier.align(Alignment.BottomEnd)) {
                                Text("${index + 1}", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// GRID VIEW
// =============================================================================

@Composable
fun GridView(
    presentation: com.karnadigital.omnisuite.core.engine.document.ParsedPresentation,
    onSlideClick: (Int) -> Unit,
) {
    LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(presentation.slides) { index, slide ->
            Card(shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), modifier = Modifier.fillMaxWidth().aspectRatio(presentation.aspectRatio).clickable { onSlideClick(index) }) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ParsedSlideView(slide, minTextSp = 7f)
                    Surface(shape = RoundedCornerShape(topStart = 6.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f), modifier = Modifier.align(Alignment.BottomEnd)) {
                        Text("${index + 1}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }
        }
    }
}

// =============================================================================
// SLIDESHOW VIEW
// =============================================================================

@Composable
fun SlideshowView(
    presentation: com.karnadigital.omnisuite.core.engine.document.ParsedPresentation,
    pagerState: androidx.compose.foundation.pager.PagerState,
    onExit: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { pageIndex ->
            val slide = presentation.slides[pageIndex]
            Box(
                modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (offset.x > size.width * 0.65f && pagerState.currentPage < presentation.slides.size - 1) coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        else if (offset.x < size.width * 0.35f && pagerState.currentPage > 0) coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                ParsedSlideView(slide, minTextSp = 9f, modifier = Modifier.fillMaxSize())
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopCenter), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.6f), modifier = Modifier.padding(4.dp)) {
                Text("${pagerState.currentPage + 1} / ${presentation.slides.size}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
            IconButton(onClick = onExit, modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), CircleShape)) {
                Icon(Icons.Default.Close, contentDescription = "Exit Slideshow", tint = Color.White)
            }
        }
    }
}

// =============================================================================
// SLIDE CARD — renders ParsedSlide directly via Compose
// =============================================================================

@Composable
fun SlideCardItem(
    slide: ParsedSlide,
    slideIndex: Int,
    aspectRatio: Float,
    isEditMode: Boolean,
    onEditShapeClick: (shape: ParsedShape, isTitle: Boolean, shapeIdx: Int) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio).border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val slideW = maxWidth
            val slideH = maxHeight

            // The slide itself — Compose renders ParsedSlide directly
            ParsedSlideView(slide, minTextSp = 9f, modifier = Modifier.fillMaxSize())

            // Interactive edit overlays
            if (isEditMode) {
                slide.shapes.forEachIndexed { shapeIdx, shape ->
                    if (shape.content is TextContent) {
                        val isTitle = shapeIdx == 0
                        Box(
                            modifier = Modifier
                                .offset(x = slideW * shape.bounds.left, y = slideH * shape.bounds.top)
                                .size(slideW * shape.bounds.width, slideH * shape.bounds.height)
                                .clickable { onEditShapeClick(shape, isTitle, shapeIdx) }
                                .background(if (isTitle) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
                                .border(1.dp, if (isTitle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary, RoundedCornerShape(4.dp)),
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// ZOOMABLE BOX
// =============================================================================

@Composable
fun ZoomableBox(modifier: Modifier = Modifier, maxScale: Float = 4.0f, minScale: Float = 1.0f, content: @Composable BoxScope.() -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(minScale, maxScale)
        scale = newScale
        offset = if (scale > 1f) {
            val maxOx = ((1080f * scale) - (1080f * 1f)) / 2f
            val maxOy = ((1920f * scale) - (1920f * 1f)) / 2f
            Offset((offset.x + offsetChange.x).coerceIn(-maxOx.coerceAtLeast(0f), maxOx.coerceAtLeast(0f)), (offset.y + offsetChange.y).coerceIn(-maxOy.coerceAtLeast(0f), maxOy.coerceAtLeast(0f)))
        } else Offset.Zero
    }
    Box(
        modifier = modifier.pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = if (scale > 1.2f) 1f else 2.5f; offset = Offset.Zero }) }.transformable(state = state)
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
        content = content,
    )
}

// =============================================================================
// TEXT FORMATTER DIALOG — adapted to ParsedShape
// =============================================================================

@Composable
fun PptxTextFormatterDialog(
    shape: ParsedShape,
    isTitle: Boolean,
    initialNotes: String?,
    initialBgColorHex: String?,
    onDismiss: () -> Unit,
    onSave: (newText: String, isBold: Boolean, isItalic: Boolean, isUnderline: Boolean, textColorHex: String?, notes: String?, fontSizePt: Float, bgColorHex: String?) -> Unit,
) {
    val tc = shape.content as? TextContent
    val firstPara = tc?.paragraphs?.firstOrNull()
    val firstRun = firstPara?.runs?.firstOrNull()
    val initialText = tc?.paragraphs?.joinToString("\n") { p -> p.runs.joinToString("") { it.text } } ?: ""

    var text by remember { mutableStateOf(initialText) }
    var isBold by remember { mutableStateOf(firstRun?.bold ?: isTitle) }
    var isItalic by remember { mutableStateOf(firstRun?.italic ?: false) }
    var isUnderline by remember { mutableStateOf(firstRun?.underline ?: false) }
    var textColorHex by remember { mutableStateOf(firstRun?.colorHex ?: "#000000") }
    var notes by remember { mutableStateOf(initialNotes ?: "") }
    var fontSizePt by remember { mutableFloatStateOf(firstRun?.sizePt?.takeIf { it > 0f } ?: if (isTitle) 24f else 14f) }
    var slideBgColorHex by remember { mutableStateOf(initialBgColorHex ?: "#FFFFFF") }

    val presetColors = listOf("#000000", "#FFFFFF", "#2563EB", "#16A34A", "#DC2626", "#D97706", "#7C3AED", "#0891B2")
    val presetBgColors = listOf("#FFFFFF", "#F8FAFC", "#F1F5F9", "#FEF3C7", "#DCFCE7", "#E0E7FF", "#FCE7F3", "#1E293B")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isTitle) "Edit Slide Title" else "Edit Text Shape", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Slide Text") }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = isBold, onClick = { isBold = !isBold }, label = { Text("Bold") })
                    FilterChip(selected = isItalic, onClick = { isItalic = !isItalic }, label = { Text("Italic") })
                    FilterChip(selected = isUnderline, onClick = { isUnderline = !isUnderline }, label = { Text("Underline") })
                }
                Text("Font Size: ${fontSizePt.toInt()}pt", style = MaterialTheme.typography.labelMedium)
                Slider(value = fontSizePt, onValueChange = { fontSizePt = it }, valueRange = 8f..72f)
                Text("Text Color", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presetColors.forEach { c ->
                        Box(modifier = Modifier.size(32.dp).background(parseColorOrWhite(c), CircleShape).border(2.dp, if (textColorHex == c) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape).clickable { textColorHex = c })
                    }
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Speaker Notes (optional)") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text, isBold, isItalic, isUnderline, textColorHex, notes.ifBlank { null }, fontSizePt, slideBgColorHex.takeIf { it != "#FFFFFF" }) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun parseColorOrWhite(hex: String): Color = safeParseColor(hex, Color.White)
