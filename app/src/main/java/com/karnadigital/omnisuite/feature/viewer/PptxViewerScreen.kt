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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Build
import java.io.File
import androidx.compose.foundation.clickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.karnadigital.omnisuite.core.util.ZoomableBox
import com.karnadigital.omnisuite.di.coreEntryPoint

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import androidx.hilt.navigation.compose.hiltViewModel

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SpeakerNotes
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewStream

enum class PptxViewMode {
    CONTINUOUS, PAGER, GRID, SLIDESHOW
}

private fun safeParseColor(colorHex: String?, fallback: Color): Color {
    if (colorHex == null) return fallback
    val trimmed = colorHex.trim()
    if (trimmed.isEmpty()) return fallback
    val formatted = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
    return try {
        Color(android.graphics.Color.parseColor(formatted))
    } catch (e: Exception) {
        fallback
    }
}

/**
 * Slide-deck Presentation Viewer (PPTX) mobile screen engine with WPS Office & Mi Docs features.
 * Supports continuous vertical flow (Mi Docs / PDF style), swipeable pager, multi-slide overview grid, and full-screen slideshow.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PptxViewerScreen(
    fileUri: String,
    onBack: () -> Unit,
    onToolAction: (ViewerTool) -> Unit = {},
    viewModel: PptxViewerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uriCacheUtils = coreEntryPoint(context).uriCacheUtils()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(fileUri) {
        viewModel.loadPptxFile(fileUri)
    }

    LaunchedEffect(Unit) {
        viewModel.saveStatus.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val state by viewModel.loadState.collectAsState()
    var isEditMode by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(PptxViewMode.CONTINUOUS) }
    var showNotesPanel by remember { mutableStateOf(false) }

    var activeIndexToEdit by remember { mutableStateOf<Int?>(null) }
    var blockToEdit by remember { mutableStateOf<PptxTextShape?>(null) }
    var isTitleEdit by remember { mutableStateOf(false) }
    var blockIndexToEdit by remember { mutableStateOf(-1) }
    var showFormatter by remember { mutableStateOf(false) }

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val currentMatchIndex by viewModel.currentMatchIndex.collectAsState()
    var searchExpanded by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(pageCount = { 
        (state as? PptxLoadState.Success)?.presentation?.slides?.size ?: 0 
    })

    LaunchedEffect(currentMatchIndex) {
        if (currentMatchIndex >= 0 && currentMatchIndex < searchResults.size) {
            val match = searchResults[currentMatchIndex]
            pagerState.animateScrollToPage(match.pageIndex)
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                coroutineScope.launch {
                    val cachedFile = uriCacheUtils.cacheUriToFile(it)
                    if (cachedFile != null) {
                        val slideIndex = activeIndexToEdit ?: 0
                        viewModel.insertImageIntoSlide(slideIndex, cachedFile.absolutePath)
                        Toast.makeText(context, "Picture inserted successfully!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    Scaffold(
        topBar = {
            if (viewMode != PptxViewMode.SLIDESHOW) {
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
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close search"
                                )
                            }

                            TextField(
                                value = searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = { Text("Search text in slides...") },
                                modifier = Modifier.weight(1f),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                singleLine = true
                            )

                            if (searchResults.isNotEmpty()) {
                                Text(
                                    text = "${currentMatchIndex + 1} of ${searchResults.size}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                IconButton(onClick = { viewModel.prevMatch() }) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Prev match"
                                    )
                                }
                                IconButton(onClick = { viewModel.nextMatch() }) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Next match"
                                    )
                                }
                            } else if (searchQuery.isNotEmpty()) {
                                Text(
                                    text = "No matches",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }
                } else {
                    TopAppBar(
                        title = {
                            Text(
                                text = when (val s = state) {
                                    is PptxLoadState.Success -> s.fileName
                                    else -> "Presentation Viewer"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Navigate back"
                                )
                            }
                        },
                        actions = {
                            if (state is PptxLoadState.Success) {
                                var showMenu by remember { mutableStateOf(false) }

                                // Toggle Continuous Flow vs Single Slide Pager
                                IconButton(onClick = {
                                    viewMode = if (viewMode == PptxViewMode.CONTINUOUS) PptxViewMode.PAGER else PptxViewMode.CONTINUOUS
                                }) {
                                    Icon(
                                        imageVector = if (viewMode == PptxViewMode.CONTINUOUS) Icons.Default.ViewCarousel else Icons.Default.ViewStream,
                                        contentDescription = "Toggle Flow / Slide Pager View",
                                        tint = if (viewMode == PptxViewMode.CONTINUOUS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // Toggle Grid Overview
                                IconButton(onClick = {
                                    viewMode = if (viewMode == PptxViewMode.GRID) PptxViewMode.CONTINUOUS else PptxViewMode.GRID
                                }) {
                                    Icon(
                                        imageVector = if (viewMode == PptxViewMode.GRID) Icons.Default.ViewAgenda else Icons.Default.GridView,
                                        contentDescription = "Toggle Grid Overview",
                                        tint = if (viewMode == PptxViewMode.GRID) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                IconButton(onClick = { searchExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search text"
                                    )
                                }
                                // Edit button temporarily hidden
                                // IconButton(onClick = {
                                //     if (isEditMode) {
                                //         viewModel.commitChanges()
                                //     }
                                //     isEditMode = !isEditMode
                                // }) {
                                //     Icon(
                                //         imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                                //         contentDescription = "Toggle Edit Mode",
                                //         tint = if (isEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                //     )
                                // }
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Play SlideShow") },
                                        onClick = {
                                            showMenu = false
                                            viewMode = PptxViewMode.SLIDESHOW
                                        },
                                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Print") },
                                        onClick = {
                                            showMenu = false
                                            onToolAction(ViewerTool.Print)
                                        },
                                        leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share") },
                                        onClick = {
                                            showMenu = false
                                            onToolAction(ViewerTool.Share)
                                        },
                                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Open in...") },
                                        onClick = {
                                            showMenu = false
                                            onToolAction(ViewerTool.OpenIn)
                                        },
                                        leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Convert to PDF") },
                                        onClick = {
                                            showMenu = false
                                            onToolAction(ViewerTool.Navigate(com.karnadigital.omnisuite.ui.navigation.Screen.PptToPdf.route))
                                        },
                                        leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Extract Text to TXT") },
                                        onClick = {
                                            showMenu = false
                                            onToolAction(ViewerTool.Navigate(com.karnadigital.omnisuite.ui.navigation.Screen.PptxToTxt.createRoute(fileUri)))
                                        },
                                        leadingIcon = { Icon(Icons.Default.TextSnippet, contentDescription = null) }
                                    )
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
            if (state is PptxLoadState.Success && viewMode != PptxViewMode.SLIDESHOW) {
                var showToolsMenu by remember { mutableStateOf(false) }
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ViewerActionColumnButton(
                            icon = if (viewMode == PptxViewMode.CONTINUOUS) Icons.Default.ViewCarousel else Icons.Default.ViewStream,
                            title = if (viewMode == PptxViewMode.CONTINUOUS) "Single" else "Flow"
                        ) {
                            viewMode = if (viewMode == PptxViewMode.CONTINUOUS) PptxViewMode.PAGER else PptxViewMode.CONTINUOUS
                        }

                        ViewerActionColumnButton(
                            icon = Icons.Default.GridView,
                            title = "Grid"
                        ) {
                            viewMode = if (viewMode == PptxViewMode.GRID) PptxViewMode.CONTINUOUS else PptxViewMode.GRID
                        }

                        ViewerActionColumnButton(
                            icon = Icons.Default.PlayArrow,
                            title = "SlideShow"
                        ) {
                            viewMode = PptxViewMode.SLIDESHOW
                        }

                        // Edit button temporarily hidden
                        // ViewerActionColumnButton(
                        //     icon = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                        //     title = if (isEditMode) "Save" else "Edit"
                        // ) {
                        //     if (isEditMode) {
                        //         viewModel.commitChanges()
                        //     }
                        //     isEditMode = !isEditMode
                        // }

                        ViewerActionColumnButton(
                            icon = Icons.Default.SpeakerNotes,
                            title = "Notes"
                        ) {
                            showNotesPanel = !showNotesPanel
                        }

                        Box {
                            ViewerActionColumnButton(
                                icon = Icons.Default.Build,
                                title = "Tools"
                            ) {
                                showToolsMenu = true
                            }
                            DropdownMenu(
                                expanded = showToolsMenu,
                                onDismissRequest = { showToolsMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Convert to PDF") },
                                    onClick = {
                                        showToolsMenu = false
                                        onToolAction(ViewerTool.Navigate(com.karnadigital.omnisuite.ui.navigation.Screen.PptToPdf.route))
                                    },
                                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Extract Text to TXT") },
                                    onClick = {
                                        showToolsMenu = false
                                        onToolAction(ViewerTool.Navigate(com.karnadigital.omnisuite.ui.navigation.Screen.PptxToTxt.createRoute(fileUri)))
                                    },
                                    leadingIcon = { Icon(Icons.Default.TextSnippet, contentDescription = null) }
                                )
                            }
                        }

                        ViewerActionColumnButton(
                            icon = Icons.Default.Share,
                            title = "Share"
                        ) {
                            onToolAction(ViewerTool.Share)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (viewMode == PptxViewMode.SLIDESHOW) PaddingValues(0.dp) else paddingValues)
                .background(if (viewMode == PptxViewMode.SLIDESHOW) Color.Black else MaterialTheme.colorScheme.background)
        ) {
            when (val currentState = state) {
                is PptxLoadState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Reflowing slides deck...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }
                }
                is PptxLoadState.Success -> {
                    val presentation = currentState.presentation
                    if (presentation.slides.isEmpty()) {
                        EmptyPresentationState()
                    } else {
                        // Native Compose rendering for all modes (no WebView)
                        if (viewMode == PptxViewMode.SLIDESHOW) {
                            // WPS Office Fullscreen Presentation Mode
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black)
                                    .pointerInput(presentation.slides.size) {
                                        detectTapGestures(
                                            onTap = { offset ->
                                                if (offset.x < size.width / 3f) {
                                                    if (pagerState.currentPage > 0) {
                                                        coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                                    }
                                                } else {
                                                    if (pagerState.currentPage < presentation.slides.size - 1) {
                                                        coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                                    }
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize()
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
                                                isEditMode = false,
                                                onTextBlockClick = { _, _, _ -> }
                                            )
                                        }
                                    }
                                }

                                // Top navigation overlay
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter)
                                        .statusBarsPadding()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { viewMode = PptxViewMode.PAGER },
                                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Exit SlideShow", tint = Color.White)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color.Black.copy(alpha = 0.6f)
                                    ) {
                                        Text(
                                            text = "${pagerState.currentPage + 1} / ${presentation.slides.size}",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        } else if (viewMode == PptxViewMode.CONTINUOUS) {
                            // Mi PPT / WPS Office Continuous Flow Mode (Vertical Scroll)
                            ContinuousSlideView(
                                presentation = presentation,
                                isEditMode = isEditMode,
                                onTextBlockClick = { textBlock, isTitle, blockIdx ->
                                    blockToEdit = textBlock
                                    isTitleEdit = isTitle
                                    blockIndexToEdit = blockIdx
                                    showFormatter = true
                                }
                            )
                        } else if (viewMode == PptxViewMode.GRID) {
                            // Mi Docs Multi-slide Overview Grid Mode
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                contentPadding = PaddingValues(vertical = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(presentation.slides) { index, slideItem ->
                                    val isCurrent = pagerState.currentPage == index
                                    Card(
                                        shape = RoundedCornerShape(10.dp),
                                        border = if (isCurrent) BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                        colors = CardDefaults.cardColors(containerColor = safeParseColor(slideItem.bgColorHex, MaterialTheme.colorScheme.surface)),
                                        elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrent) 6.dp else 2.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(slideItem.aspectRatio)
                                            .clickable {
                                                coroutineScope.launch {
                                                    pagerState.scrollToPage(index)
                                                }
                                                viewMode = PptxViewMode.PAGER
                                            }
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            if (index < currentState.slideBitmaps.size && currentState.slideBitmaps[index] != null) {
                                                Image(
                                                    bitmap = currentState.slideBitmaps[index].asImageBitmap(),
                                                    contentDescription = "Slide ${index + 1}",
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                MiniSlidePreview(slide = slideItem)
                                            }

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
                        } else {
                            // Standard Slide Pager Mode
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Swipable Slides horizontal pager
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    pageSpacing = 16.dp
                                ) { pageIndex ->
                                    val slide = presentation.slides[pageIndex]

                                    val pageOffset = (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                                    val scale = 1f - (Math.abs(pageOffset) * 0.12f).coerceIn(0f, 0.12f)
                                    val alpha = 1f - (Math.abs(pageOffset) * 0.4f).coerceIn(0f, 0.4f)

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                scaleX = scale
                                                scaleY = scale
                                                this.alpha = alpha
                                            }
                                    ) {
                                        ZoomableBox(
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            SlideCardItem(
                                                slide = slide,
                                                isEditMode = isEditMode,
                                                onTextBlockClick = { textBlock, isTitle, blockIdx ->
                                                    blockToEdit = textBlock
                                                    activeIndexToEdit = pageIndex
                                                    isTitleEdit = isTitle
                                                    blockIndexToEdit = blockIdx
                                                    showFormatter = true
                                                }
                                            )
                                        }
                                    }
                                }

                                if (isEditMode) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = { viewModel.addSlide(pagerState.currentPage) },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text("+ Add Slide", fontSize = 12.sp)
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.duplicateSlide(pagerState.currentPage) },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text("📋 Duplicate", fontSize = 12.sp)
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.deleteSlide(pagerState.currentPage) },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text("🗑️ Delete", fontSize = 12.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Slide counter badge
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                                    ) {
                                        Text(
                                            text = "Slide ${pagerState.currentPage + 1} of ${presentation.slides.size}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Slide thumbnail strip drawer
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(presentation.slides.size, key = { it }) { index ->
                                        val slideItem = presentation.slides[index]
                                        val isActive = pagerState.currentPage == index
                                        val borderStroke = if (isActive) {
                                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                        } else {
                                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        }
                                        val opacity = if (isActive) 1f else 0.6f

                                        Card(
                                            shape = RoundedCornerShape(6.dp),
                                            border = borderStroke,
                                            colors = CardDefaults.cardColors(
                                                containerColor = safeParseColor(slideItem.bgColorHex, MaterialTheme.colorScheme.surface)
                                            ),
                                            modifier = Modifier
                                                .width(80.dp)
                                                .aspectRatio(slideItem.aspectRatio)
                                                .clickable {
                                                    coroutineScope.launch {
                                                        pagerState.animateScrollToPage(index)
                                                    }
                                                }
                                                .graphicsLayer { this.alpha = opacity }
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                if (index < currentState.slideBitmaps.size && currentState.slideBitmaps[index] != null) {
                                                    Image(
                                                        bitmap = currentState.slideBitmaps[index].asImageBitmap(),
                                                        contentDescription = "Slide ${index + 1}",
                                                        contentScale = ContentScale.Fit,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    MiniSlidePreview(slide = slideItem)
                                                }

                                                Surface(
                                                    shape = RoundedCornerShape(topStart = 4.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
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

                                // Collapsible Speaker Notes Panel
                                if (showNotesPanel) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val currentSlide = presentation.slides[pagerState.currentPage]
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.SpeakerNotes,
                                                        contentDescription = "Speaker Notes",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Speaker Notes (Slide ${pagerState.currentPage + 1})",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { showNotesPanel = false },
                                                    modifier = Modifier.size(20.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "Close Notes", modifier = Modifier.size(14.dp))
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            val displayNotes = currentSlide.speakerNotes
                                            if (!displayNotes.isNullOrBlank()) {
                                                Text(
                                                    text = displayNotes,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else {
                                                Text(
                                                    text = "No slide notes recorded.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                            // Text Formatter dialog overlay
                            if (showFormatter && blockToEdit != null && activeIndexToEdit != null) {
                                val slide = presentation.slides[activeIndexToEdit!!]
                                PptxTextFormatterDialog(
                                    slideIndex = activeIndexToEdit!!,
                                    textBlock = blockToEdit!!,
                                    isTitle = isTitleEdit,
                                    blockIndex = blockIndexToEdit,
                                    initialNotes = slide.speakerNotes,
                                    initialBgColorHex = slide.bgColorHex,
                                    onDismiss = { showFormatter = false },
                                    onSave = { newText, isBold, isItalic, isUnderline, textColorHex, notes, fontSize, bgColor ->
                                        viewModel.updateSlideTextShape(
                                            slideIndex = activeIndexToEdit!!,
                                            isTitle = isTitleEdit,
                                            blockIndex = blockIndexToEdit,
                                            newText = newText,
                                            isBold = isBold,
                                            isItalic = isItalic,
                                            isUnderline = isUnderline,
                                            textColorHex = textColorHex,
                                            comment = notes,
                                            fontSizePt = fontSize
                                        )
                                        if (bgColor != null) {
                                            viewModel.setSlideBackground(activeIndexToEdit!!, bgColor)
                                        }
                                        showFormatter = false
                                    },
                                    onInsertImageClick = {
                                        imagePickerLauncher.launch("image/*")
                                        showFormatter = false
                                    }
                                )
                            }
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
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error Icon",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Slides Read Error",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = currentState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PptxTextFormatterDialog(
    slideIndex: Int,
    textBlock: PptxTextShape,
    isTitle: Boolean,
    blockIndex: Int,
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
    ) -> Unit,
    onInsertImageClick: () -> Unit
) {
    var text by remember { mutableStateOf(textBlock.primaryText.ifBlank { textBlock.fullText }) }
    var isBold by remember { mutableStateOf(textBlock.isBold) }
    var isItalic by remember { mutableStateOf(textBlock.isItalic) }
    var isUnderline by remember { mutableStateOf(textBlock.isUnderline) }
    var textColorHex by remember { mutableStateOf(textBlock.textColorHex) }
    var notes by remember { mutableStateOf(initialNotes ?: "") }
    var fontSizePt by remember { mutableStateOf(textBlock.fontSizePt) }
    var slideBgColorHex by remember { mutableStateOf(initialBgColorHex ?: "#FFFFFF") }

    val colors = listOf(
        "#000000", // Black
        "#FFFFFF", // White
        "#2196F3", // Blue
        "#4CAF50", // Green
        "#F44336", // Red
        "#FFEB3B", // Yellow
        "#9C27B0", // Purple
        "#FF9800", // Orange
        "#00BCD4"  // Cyan
    )

    val bgColors = listOf(
        "#FFFFFF", // White
        "#F5F5F5", // Off-white
        "#E0F7FA", // Light Cyan
        "#FFF3E0", // Light Orange
        "#E8F5E9", // Light Green
        "#F3E5F5", // Light Purple
        "#ECEFF1", // Slate
        "#212121"  // Dark Grey
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isTitle) "Format Slide Title" else "Format Text Shape",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Text input
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text Content") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )

                // Formatting toggles
                Text(
                    text = "Text Styling",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledIconToggleButton(
                        checked = isBold,
                        onCheckedChange = { isBold = it },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Text("B", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    FilledIconToggleButton(
                        checked = isItalic,
                        onCheckedChange = { isItalic = it },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Text("I", style = MaterialTheme.typography.bodyLarge.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), fontSize = 16.sp)
                    }

                    FilledIconToggleButton(
                        checked = isUnderline,
                        onCheckedChange = { isUnderline = it },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Text("U", style = MaterialTheme.typography.bodyLarge.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), fontSize = 16.sp)
                    }
                }

                // Font Size Slider
                Text(
                    text = "Font Size: ${fontSizePt.toInt()} pt",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Slider(
                    value = fontSizePt,
                    onValueChange = { fontSizePt = it },
                    valueRange = 8f..72f,
                    modifier = Modifier.fillMaxWidth()
                )

                // Color picker swatches
                Text(
                    text = "Text Color",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    colors.forEach { hex ->
                        val color = Color(android.graphics.Color.parseColor(hex))
                        val isSelected = textColorHex?.lowercase() == hex.lowercase()
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                                .clickable {
                                    textColorHex = if (isSelected) null else hex
                                }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                // Slide Background Color Picker
                Text(
                    text = "Slide Background Color",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    bgColors.forEach { hex ->
                        val color = Color(android.graphics.Color.parseColor(hex))
                        val isSelected = slideBgColorHex.lowercase() == hex.lowercase()
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                                .clickable {
                                    slideBgColorHex = hex
                                }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                // Speaker Notes input
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Speaker Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                // Insert Picture Button
                Button(
                    onClick = onInsertImageClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "Insert Image", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Insert Picture Run")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(text, isBold, isItalic, isUnderline, textColorHex, notes, fontSizePt, slideBgColorHex)
            }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ContinuousSlideView(
    presentation: PptxPresentation,
    isEditMode: Boolean,
    onTextBlockClick: (PptxTextShape, isTitle: Boolean, blockIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    Box(modifier = modifier.fillMaxSize()) {
        ZoomableBox(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(presentation.slides.size, key = { it }) { index ->
                    val slide = presentation.slides[index]
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SlideCardItem(
                            slide = slide,
                            isEditMode = isEditMode,
                            onTextBlockClick = onTextBlockClick
                        )
                        // Page number badge on bottom right of slide card
                        Surface(
                            shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                            modifier = Modifier.align(Alignment.BottomEnd)
                        ) {
                            Text(
                                text = "${index + 1}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        // Floating indicator showing current visible slide number
        val firstVisibleIndex by remember {
            derivedStateOf { listState.firstVisibleItemIndex }
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "Slide ${firstVisibleIndex + 1} of ${presentation.slides.size}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun SlideCardItem(
    slide: PptxSlide,
    isEditMode: Boolean = false,
    onTextBlockClick: (PptxTextShape, isTitle: Boolean, blockIndex: Int) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = safeParseColor(slide.bgColorHex, MaterialTheme.colorScheme.surface)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(slide.aspectRatio)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val slideW = maxWidth.value
            val slideH = maxHeight.value
            val title = slide.title

            // LAYER 1: Background Image (bottom layer, full slide coverage)
            slide.backgroundImage?.let { bgImg ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(bgImg.filePath))
                        .crossfade(true)
                        .build(),
                    contentDescription = "Slide Background",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }

            // Combine foreground images and text shapes into a single z-ordered list
            val imageElements = slide.images.map { img -> SlideElement.ImageElement(img) }
            val titleElement = if (title.fullText.isNotBlank() && title.id == "title") {
                listOf(SlideElement.TextElement(title, true, title.zOrder))
            } else emptyList()
            val bodyElements = slide.textShapes.mapIndexed { idx, shape ->
                SlideElement.TextElement(shape, shape.isTitle, idx)
            }
            val allElements = (imageElements + titleElement + bodyElements).sortedBy { it.zOrder }

            // Render all elements in z-order (bottom to top)
            allElements.forEach { element ->
                when (element) {
                    is SlideElement.ImageElement -> {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(File(element.image.filePath))
                                .crossfade(true)
                                .build(),
                            contentDescription = "Slide Image",
                            modifier = Modifier
                                .offset(x = (element.image.left * slideW).dp, y = (element.image.top * slideH).dp)
                                .size(width = (element.image.width * slideW).dp, height = (element.image.height * slideH).dp)
                                .clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                    is SlideElement.TextElement -> {
                        TextShapeItem(
                            shape = element.shape,
                            slideW = slideW,
                            slideH = slideH,
                            isTitle = element.isTitle,
                            isEditMode = isEditMode,
                            onClick = { onTextBlockClick(element.shape, element.isTitle, element.index) }
                        )
                    }
                }
            }
        }
    }
}

/** Represents a renderable slide element with z-order for correct layering */
private sealed class SlideElement(val zOrder: Int) {
    data class ImageElement(val image: PptxImage) : SlideElement(image.zOrder)
    data class TextElement(val shape: PptxTextShape, val isTitle: Boolean, val index: Int) : SlideElement(shape.zOrder)
}

/**
 * Formats a numbered-list marker for a given OOXML auto-numbering scheme and 1-based index.
 * Covers the common PowerPoint schemes; unknown schemes fall back to "index.".
 */
private fun formatNumberedMarker(type: String, index: Int): String {
    if (index <= 0) return "$index."
    return when (type) {
        "arabicPeriod" -> "$index."
        "arabicParenR" -> "$index)"
        "arabicParen" -> "($index)"
        "arabicUcPeriod" -> "$index."
        "alphaUcPeriod" -> "${(64 + index).toChar()}."
        "alphaLcPeriod" -> "${(96 + index).toChar()}."
        "alphaUcParenR" -> "${(64 + index).toChar()})"
        "alphaLcParenR" -> "${(96 + index).toChar()})"
        "romanUcPeriod" -> "${romanNumeral(index)}."
        "romanLcPeriod" -> "${romanNumeral(index).lowercase()}."
        "romanUcParenR" -> "${romanNumeral(index)})"
        "romanLcParenR" -> "${romanNumeral(index).lowercase()})"
        else -> "$index."
    }
}

/** Minimal Roman-numeral conversion for the small indices typical of slide bullets. */
private fun romanNumeral(value: Int): String {
    val nums = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
    val romans = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
    var n = value
    val sb = StringBuilder()
    for (i in nums.indices) {
        while (n >= nums[i]) {
            sb.append(romans[i])
            n -= nums[i]
        }
    }
    return sb.toString()
}

/** Per-paragraph resolved layout data (sizes at scale = 1.0). */
private data class ParagraphLayout(
    val paragraph: PptxParagraph,
    val textAlign: TextAlign,
    val maxFontSizeSp: Float,
    val lineHeightSp: Float,
    val spaceBeforeSp: Float,
    val spaceAfterSp: Float,
    val markerText: String,
    val indentSp: Float
)

@Composable
fun TextShapeItem(
    shape: PptxTextShape,
    slideW: Float,
    slideH: Float,
    isTitle: Boolean,
    isEditMode: Boolean,
    onClick: () -> Unit
) {
    val shapeGeom = shape.shapeGeometry
    val shapeBorder = shape.shapeBorder
    val shapeBgColor = shape.backgroundColorHex?.let { safeParseColor(it, Color.Transparent) } ?: Color.Transparent

    val shapeShape = when (shapeGeom) {
        ShapeGeometryType.ELLIPSE -> androidx.compose.foundation.shape.CircleShape
        ShapeGeometryType.ROUNDED_RECTANGLE -> RoundedCornerShape(8.dp)
        else -> RoundedCornerShape(if (shape.id.startsWith("table_cell_")) 0.dp else 2.dp)
    }

    val borderWidth = when {
        shapeBorder != null && shapeBorder.strokeColorHex != null -> shapeBorder.strokeWidthDp.dp
        shape.id.startsWith("table_cell_") -> 1.dp
        isEditMode -> 1.dp
        else -> 0.dp
    }

    val borderColor = when {
        shapeBorder != null && shapeBorder.strokeColorHex != null -> safeParseColor(shapeBorder.strokeColorHex, MaterialTheme.colorScheme.primary)
        shape.id.startsWith("table_cell_") -> MaterialTheme.colorScheme.outlineVariant
        isEditMode && isTitle -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        isEditMode -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
        else -> Color.Transparent
    }

    // Proportional font scale: reference 720dp slide width
    val baseFontScale = (slideW / 720f).coerceIn(0.35f, 1.2f) * 0.88f
    val isTableCell = shape.id.startsWith("table_cell_")
    val defaultFontSize = if (isTitle) 24f else 13f
    val maxSpCap = if (isTitle) 32f else 22f
    val defaultColor = if (isTitle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()

    // Inner content box (shape size minus real <a:bodyPr> insets) in px.
    val shapeWidthPx = with(density) { (shape.shapeWidth * slideW).dp.toPx() }
    val shapeHeightPx = with(density) { (shape.shapeHeight * slideH).dp.toPx() }
    val insetLeftPx = with(density) { (shape.insets.left * slideW).dp.toPx() }
    val insetTopPx = with(density) { (shape.insets.top * slideH).dp.toPx() }
    val insetRightPx = with(density) { (shape.insets.right * slideW).dp.toPx() }
    val insetBottomPx = with(density) { (shape.insets.bottom * slideH).dp.toPx() }
    val contentWidthPx = (shapeWidthPx - insetLeftPx - insetRightPx).coerceAtLeast(1f)

    // Line-spacing floor from normAutofit lnSpcReduction (default 1.0 = single, no compression).
    val minLineMul = shape.lnSpcReduction?.let { (1f - it / 100000f).coerceIn(0.5f, 1f) } ?: 1.0f

    // Assign sequential indices to numbered paragraphs sharing the same scheme.
    val numberingIndex = remember(shape) {
        val map = HashMap<Int, Int>()
        var counter = 0
        var prevType: String? = null
        shape.paragraphs.forEachIndexed { i, p ->
            if (p.numberingType != null) {
                counter = if (p.numberingType == prevType) counter + 1 else 1
                prevType = p.numberingType
                map[i] = counter
            }
        }
        map
    }

    // Resolve per-paragraph layout at scale 1.0.
    val paraLayouts = remember(shape, baseFontScale) {
        shape.paragraphs.mapIndexed { i, paragraph ->
            val textAlign = when (paragraph.alignment) {
                "CENTER" -> TextAlign.Center
                "RIGHT" -> TextAlign.Right
                "JUSTIFY" -> TextAlign.Justify
                else -> if (shapeGeom == ShapeGeometryType.ELLIPSE) TextAlign.Center else TextAlign.Start
            }
            val maxFontSizeSp = paragraph.runs.maxOfOrNull { run ->
                val basePt = if (run.fontSizePt > 0) run.fontSizePt else defaultFontSize
                (basePt * baseFontScale).coerceIn(6f, maxSpCap)
            } ?: (defaultFontSize * baseFontScale).coerceIn(6f, maxSpCap)
            val effLineMul = max(paragraph.lineSpacingMul, minLineMul)
            val lineHeightSp = maxFontSizeSp * effLineMul
            val markerText = when {
                paragraph.numberingType != null -> formatNumberedMarker(
                    paragraph.numberingType,
                    numberingIndex[i] ?: 1
                )
                paragraph.hasBullet || paragraph.bulletLevel > 0 -> "${paragraph.bulletChar} "
                else -> ""
            }
            val indentSp = paragraph.bulletLevel * 12f
            ParagraphLayout(
                paragraph = paragraph,
                textAlign = textAlign,
                maxFontSizeSp = maxFontSizeSp,
                lineHeightSp = lineHeightSp,
                spaceBeforeSp = paragraph.spaceBeforePt,
                spaceAfterSp = paragraph.spaceAfterPt,
                markerText = markerText,
                indentSp = indentSp
            )
        }
    }

    // Build the annotated string for a paragraph at a given scale.
    fun buildAnnotated(pl: ParagraphLayout, scale: Float): AnnotatedString = buildAnnotatedString {
        pl.paragraph.runs.forEach { run ->
            val basePt = if (run.fontSizePt > 0) run.fontSizePt else defaultFontSize
            val sizeSp = (basePt * baseFontScale * scale).coerceIn(6f, maxSpCap)
            val runColor = run.textColorHex?.let {
                try { Color(android.graphics.Color.parseColor(it)) } catch (e: Exception) { null }
            } ?: defaultColor
            withStyle(
                SpanStyle(
                    color = runColor,
                    fontSize = sizeSp.sp,
                    fontWeight = if (run.isBold) FontWeight.Bold else (if (isTitle) FontWeight.SemiBold else FontWeight.Normal),
                    fontStyle = if (run.isItalic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = if (run.isUnderline) TextDecoration.Underline else TextDecoration.None
                )
            ) {
                append(run.text)
            }
        }
    }

    // Measure total content height (all paragraphs + spacing) at a given scale.
    fun measureTotalHeight(scale: Float): Float {
        var total = 0f
        paraLayouts.forEach { pl ->
            val markerW = if (pl.markerText.isNotBlank()) with(density) { (pl.indentSp * scale + 20f).dp.toPx() } else 0f
            val availableTextW = (contentWidthPx - markerW).coerceAtLeast(10f)
            val fontSize = pl.maxFontSizeSp * scale
            val lineHeight = pl.lineHeightSp * scale
            val style = TextStyle(fontSize = fontSize.sp, lineHeight = lineHeight.sp)
            val annotatedText: AnnotatedString = buildAnnotated(pl, scale)
            val result = measurer.measure(
                text = annotatedText,
                style = style,
                constraints = Constraints(maxWidth = availableTextW.toInt()),
                layoutDirection = LayoutDirection.Ltr,
                density = density
            )
            total += result.size.height
            total += with(density) { (pl.spaceBeforeSp * scale).sp.toPx() + (pl.spaceAfterSp * scale).sp.toPx() }
        }
        return total
    }

    // Determine shrink scale: binary search to guarantee fit within available height.
    val shrinkScale = remember(shape, contentWidthPx, contentHeightPx(shapeHeightPx, insetTopPx, insetBottomPx, isTitle)) {
        val availableHeightPx = contentHeightPx(shapeHeightPx, insetTopPx, insetBottomPx, isTitle)
        val baked = shape.fontScale?.let { (it / 100000f).coerceIn(0.5f, 1f) } ?: 1f
        if (availableHeightPx <= 0f) {
            baked
        } else {
            val minScale = 0.55f
            if (measureTotalHeight(baked) <= availableHeightPx) {
                baked
            } else {
                // Binary search the largest scale whose measured height fits the box.
                var lo = minScale
                var hi = baked
                repeat(6) {
                    val mid = (lo + hi) / 2f
                    if (measureTotalHeight(mid) > availableHeightPx) hi = mid else lo = mid
                }
                lo
            }
        }
    }

    // For titles without autofit, allow the box to grow to the measured wrapped content height
    // so a title that needs 2 lines is not clipped to 1.
    val finalHeightDp = with(density) {
        val baseHeightPx = (shape.shapeHeight * slideH).dp.toPx()
        val extraPx = if (isTitle && shape.autoFit != AutoFitMode.NORM_AUTOFIT) {
            val scale = shape.fontScale?.let { (it / 100000f).coerceIn(0.5f, 1f) } ?: 1f
            val needed = measureTotalHeight(scale)
            val contentH = contentHeightPx(baseHeightPx, insetTopPx, insetBottomPx, isTitle)
            (needed - contentH).coerceAtLeast(0f)
        } else 0f
        (baseHeightPx + extraPx).toDp()
    }

    Box(
        modifier = Modifier
            .offset(x = (shape.shapeLeft * slideW).dp, y = (shape.shapeTop * slideH).dp)
            .size(width = (shape.shapeWidth * slideW).dp, height = finalHeightDp)
            .clip(shapeShape)
            .clickable(enabled = isEditMode, onClick = onClick)
            .background(
                if (isEditMode) {
                    if (isTitle) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)
                } else shapeBgColor
            )
            .border(
                width = borderWidth,
                color = borderColor,
                shape = shapeShape
            )
            .padding(
                start = (shape.insets.left * slideW).coerceAtLeast(if (isTitle) 8f else (if (shapeGeom == ShapeGeometryType.ELLIPSE) 6f else 2f)).dp,
                top = (shape.insets.top * slideH).coerceAtLeast(if (shapeGeom == ShapeGeometryType.ELLIPSE) 4f else 1f).dp,
                end = (shape.insets.right * slideW).coerceAtLeast(if (isTitle) 8f else (if (shapeGeom == ShapeGeometryType.ELLIPSE) 6f else 2f)).dp,
                bottom = (shape.insets.bottom * slideH).coerceAtLeast(if (shapeGeom == ShapeGeometryType.ELLIPSE) 4f else 1f).dp
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = if (shapeGeom == ShapeGeometryType.ELLIPSE) Arrangement.Center else Arrangement.Top,
            horizontalAlignment = if (shapeGeom == ShapeGeometryType.ELLIPSE) Alignment.CenterHorizontally else Alignment.Start
        ) {
            paraLayouts.forEach { pl ->
                val textAlign = pl.textAlign
                val bulletLevel = pl.paragraph.bulletLevel
                val showMarker = pl.markerText.isNotBlank()
                val bulletSp = (pl.maxFontSizeSp * shrinkScale).coerceIn(5f, 18f).sp

                val annotatedText = buildAnnotated(pl, shrinkScale)

                // Line height driven by the parsed line-spacing multiplier (single = 1.0).
                val leadSp = (pl.lineHeightSp * shrinkScale).coerceIn(7f, 32f).sp

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = (pl.spaceAfterSp * shrinkScale).coerceAtLeast(0f).dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = if (shapeGeom == ShapeGeometryType.ELLIPSE) Arrangement.Center else Arrangement.Start
                ) {
                    if (showMarker) {
                        if (bulletLevel > 0) {
                            Spacer(modifier = Modifier.width((pl.indentSp * shrinkScale).coerceAtLeast(2f).dp))
                        }
                        Text(
                            text = pl.markerText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = bulletSp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = annotatedText,
                        textAlign = textAlign,
                        lineHeight = leadSp,
                        maxLines = if (isTitle) 100 else 1000,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = (pl.spaceBeforeSp * shrinkScale).coerceAtLeast(0f).dp)
                            .weight(1f, fill = false)
                    )
                }
            }
        }
    }
}

/** Available inner content height in px. For non-expanding shapes this is the box minus insets. */
private fun contentHeightPx(shapeHeightPx: Float, insetTopPx: Float, insetBottomPx: Float, isTitle: Boolean): Float =
    (shapeHeightPx - insetTopPx - insetBottomPx).coerceAtLeast(0f)

@Composable
fun MiniSlidePreview(slide: PptxSlide) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(safeParseColor(slide.bgColorHex, MaterialTheme.colorScheme.surface))
            .padding(4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = slide.title.primaryText.ifBlank { "Slide ${slide.slideNumber}" },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontSize = 8.sp,
                lineHeight = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = safeParseColor(slide.title.textColorHex, MaterialTheme.colorScheme.onSurface)
            )
            if (slide.textShapes.isNotEmpty()) {
                Text(
                    text = slide.textShapes.firstOrNull()?.primaryText ?: "",
                    fontSize = 6.sp,
                    lineHeight = 7.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun EmptyPresentationState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Empty",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Presentation Contains No Slides",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

private class PptxPrintDocumentAdapter(private val context: Context, private val file: File) : PrintDocumentAdapter() {
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
        var ppt: org.apache.poi.xslf.usermodel.XMLSlideShow? = null
        try {
            ppt = org.apache.poi.xslf.usermodel.XMLSlideShow(java.io.FileInputStream(file))
            val pdfDoc = android.graphics.pdf.PdfDocument()
            val slideW = 960
            val slideH = 540

            var pageNum = 0
            for (slide in ppt.slides) {
                if (cancellationSignal?.isCanceled == true) break
                pageNum++
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(slideW, slideH, pageNum).create()
                val page = pdfDoc.startPage(pageInfo)
                val canvas = page.canvas
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 36f
                    isAntiAlias = true
                }
                val titlePaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 48f
                    isAntiAlias = true
                    isFakeBoldText = true
                }

                var yOffset = 60f
                for (shape in slide.shapes) {
                    if (shape is org.apache.poi.xslf.usermodel.XSLFTextShape) {
                        val text = shape.text ?: continue
                        val isTitle = shape.isPlaceholder && (shape.textType == org.apache.poi.sl.usermodel.Placeholder.TITLE || shape.textType == org.apache.poi.sl.usermodel.Placeholder.CENTERED_TITLE)
                        val currentPaint = if (isTitle) titlePaint else paint
                        canvas.drawText(text, 40f, yOffset, currentPaint)
                        yOffset += if (isTitle) 70f else 50f
                    }
                }

                pdfDoc.finishPage(page)
            }

            val outputStream = java.io.FileOutputStream(destination?.fileDescriptor)
            pdfDoc.writeTo(outputStream)
            outputStream.close()
            pdfDoc.close()
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback?.onWriteFailed(e.localizedMessage)
        } finally {
            try { ppt?.close() } catch (e: Exception) {}
        }
    }
}

