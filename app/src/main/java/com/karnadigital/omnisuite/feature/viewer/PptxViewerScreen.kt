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
import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.clickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import coil.compose.AsyncImage

import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SpeakerNotes
import androidx.compose.material.icons.filled.ViewAgenda

enum class PptxViewMode {
    PAGER, GRID, SLIDESHOW
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
 * Supports swipeable pager, multi-slide overview grid, and full-screen slideshow presentation.
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
    val fileOutputManager = coreEntryPoint(context).fileOutputManager()
    val uriCacheUtils = coreEntryPoint(context).uriCacheUtils()
    val officeConverter = coreEntryPoint(context).officeConverter()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(fileUri) {
        viewModel.loadPptxFile(fileUri)
        try {
            val uri = Uri.parse(fileUri)
            val name = uri.lastPathSegment ?: "presentation.pptx"
            val coreRepo = coreEntryPoint(context).recentFileRepository()
            coreRepo.insertRecentFile(
                com.karnadigital.omnisuite.core.model.RecentFile(
                    fileUri = fileUri,
                    fileName = name,
                    mimeType = "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    fileSize = 0L,
                    lastOpened = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {}
    }

    LaunchedEffect(Unit) {
        viewModel.saveStatus.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val state by viewModel.loadState.collectAsState()
    var isEditMode by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(PptxViewMode.PAGER) }
    var showNotesPanel by remember { mutableStateOf(false) }

    var activeIndexToEdit by remember { mutableStateOf<Int?>(null) }
    var blockToEdit by remember { mutableStateOf<PptxTextBlock?>(null) }
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

                                IconButton(onClick = {
                                    viewMode = if (viewMode == PptxViewMode.GRID) PptxViewMode.PAGER else PptxViewMode.GRID
                                }) {
                                    Icon(
                                        imageVector = if (viewMode == PptxViewMode.GRID) Icons.Default.ViewAgenda else Icons.Default.GridView,
                                        contentDescription = "Toggle Grid View",
                                        tint = if (viewMode == PptxViewMode.GRID) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                IconButton(onClick = { searchExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search text"
                                    )
                                }
                                IconButton(onClick = {
                                    if (isEditMode) {
                                        viewModel.commitChanges()
                                    }
                                    isEditMode = !isEditMode
                                }) {
                                    Icon(
                                        imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                                        contentDescription = "Toggle Edit Mode",
                                        tint = if (isEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
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
                            icon = if (viewMode == PptxViewMode.GRID) Icons.Default.ViewAgenda else Icons.Default.GridView,
                            title = if (viewMode == PptxViewMode.GRID) "Slides" else "Grid"
                        ) {
                            viewMode = if (viewMode == PptxViewMode.GRID) PptxViewMode.PAGER else PptxViewMode.GRID
                        }

                        ViewerActionColumnButton(
                            icon = Icons.Default.PlayArrow,
                            title = "SlideShow"
                        ) {
                            viewMode = PptxViewMode.SLIDESHOW
                        }

                        ViewerActionColumnButton(
                            icon = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                            title = if (isEditMode) "Save" else "Edit"
                        ) {
                            if (isEditMode) {
                                viewModel.commitChanges()
                            }
                            isEditMode = !isEditMode
                        }

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
                    } else if (!isEditMode && currentState.pptxBase64 != null) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            PptxWebView(
                                pptxBase64 = currentState.pptxBase64,
                                viewMode = viewMode,
                                currentPage = pagerState.currentPage,
                                searchQuery = searchQuery,
                                currentMatchIndex = currentMatchIndex,
                                onSlideChanged = { idx, _ ->
                                    if (pagerState.currentPage != idx && idx < pagerState.pageCount) {
                                        coroutineScope.launch { pagerState.scrollToPage(idx) }
                                    }
                                },
                                onSlideClicked = { idx ->
                                    if (viewMode == PptxViewMode.GRID) {
                                        viewMode = PptxViewMode.PAGER
                                        coroutineScope.launch { pagerState.scrollToPage(idx) }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )

                            // Speaker notes bottom panel
                            if (showNotesPanel && viewMode != PptxViewMode.SLIDESHOW) {
                                val currentSlide = presentation.slides.getOrNull(pagerState.currentPage)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
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
                                        val displayNotes = currentSlide?.speakerNotes
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
                    } else {
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
                                            .defaultMinSize(minHeight = 110.dp)
                                            .clickable {
                                                coroutineScope.launch {
                                                    pagerState.scrollToPage(index)
                                                }
                                                viewMode = PptxViewMode.PAGER
                                            }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(10.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = slideItem.title.text.ifBlank { "Slide ${index + 1}" },
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    lineHeight = 14.sp,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = safeParseColor(slideItem.title.textColorHex, MaterialTheme.colorScheme.onSurface)
                                                )
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (slideItem.images.isNotEmpty()) {
                                                        Text("🖼️ ${slideItem.images.size}", fontSize = 9.sp)
                                                    } else {
                                                        Spacer(modifier = Modifier.width(1.dp))
                                                    }
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = MaterialTheme.colorScheme.primaryContainer
                                                    ) {
                                                        Text(
                                                            text = "${index + 1}",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
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
                                                .height(45.dp)
                                                .clickable {
                                                    coroutineScope.launch {
                                                        pagerState.animateScrollToPage(index)
                                                    }
                                                }
                                                .graphicsLayer { this.alpha = opacity }
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.fillMaxSize().padding(4.dp)
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.SpaceBetween,
                                                    modifier = Modifier.fillMaxSize()
                                                ) {
                                                    Text(
                                                        text = slideItem.title.text,
                                                        fontSize = 5.sp,
                                                        lineHeight = 6.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        color = safeParseColor(slideItem.title.textColorHex, MaterialTheme.colorScheme.onSurface),
                                                        textAlign = TextAlign.Center
                                                    )
                                                    Text(
                                                        text = "${index + 1}",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
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
    textBlock: PptxTextBlock,
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
    var text by remember { mutableStateOf(textBlock.text) }
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
fun SlideCardItem(
    slide: PptxSlide,
    isEditMode: Boolean = false,
    onTextBlockClick: (PptxTextBlock, isTitle: Boolean, blockIndex: Int) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = safeParseColor(slide.bgColorHex, MaterialTheme.colorScheme.surface)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .defaultMinSize(minHeight = 260.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            val slideW = constraints.maxWidth.toFloat()
            val slideH = (constraints.maxWidth * 0.5625f).coerceAtLeast(260f)

            fun absX(normalized: Float): Dp = (normalized * slideW).dp
            fun absY(normalized: Float): Dp = (normalized * slideH).dp
            fun absW(normalized: Float): Dp = (normalized * slideW).dp
            fun absH(normalized: Float): Dp = (normalized * slideH).dp

            fun textBlockStyle(block: PptxTextBlock, textAlign: TextAlign) = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (block.isBold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (block.isItalic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                textDecoration = if (block.isUnderline) androidx.compose.ui.text.style.TextDecoration.Underline else androidx.compose.ui.text.style.TextDecoration.None,
                fontSize = (block.fontSizePt * 0.55f).sp,
                lineHeight = (block.fontSizePt * 0.7f).sp,
                textAlign = textAlign
            )

            // 1. Title Block — positioned using normalized bounds
            val title = slide.title
            if (title.text.isNotBlank()) {
                val titleColor = title.textColorHex?.let {
                    try { Color(android.graphics.Color.parseColor(it)) } catch (e: Exception) { MaterialTheme.colorScheme.primary }
                } ?: MaterialTheme.colorScheme.primary

                val titleAlign = when (title.alignment) {
                    "CENTER" -> TextAlign.Center
                    "RIGHT" -> TextAlign.Right
                    "JUSTIFY" -> TextAlign.Justify
                    else -> TextAlign.Start
                }

                Box(
                    modifier = Modifier
                        .offset(x = absX(title.shapeLeft), y = absY(title.shapeTop))
                        .size(width = absW(title.shapeWidth), height = absH(title.shapeHeight))
                        .clickable(enabled = isEditMode) {
                            onTextBlockClick(title, true, -1)
                        }
                        .background(if (isEditMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                        .border(
                            width = if (isEditMode) 1.dp else 0.dp,
                            color = if (isEditMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(4.dp)
                ) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            text = title.text,
                            style = textBlockStyle(title, titleAlign).copy(
                                fontWeight = if (title.isBold) FontWeight.Bold else FontWeight.SemiBold
                            ),
                            color = titleColor,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 2. Body Content & Text Blocks — absolutely positioned
            slide.textBlocks.forEachIndexed { idx, block ->
                val blockColor = block.textColorHex?.let {
                    try { Color(android.graphics.Color.parseColor(it)) } catch (e: Exception) { MaterialTheme.colorScheme.onSurface }
                } ?: MaterialTheme.colorScheme.onSurface

                val textAlign = when (block.alignment) {
                    "CENTER" -> TextAlign.Center
                    "RIGHT" -> TextAlign.Right
                    "JUSTIFY" -> TextAlign.Justify
                    else -> TextAlign.Start
                }

                Box(
                    modifier = Modifier
                        .offset(x = absX(block.shapeLeft), y = absY(block.shapeTop))
                        .size(width = absW(block.shapeWidth), height = absH(block.shapeHeight))
                        .clickable(enabled = isEditMode) {
                            onTextBlockClick(block, false, idx)
                        }
                        .background(if (isEditMode) MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f) else Color.Transparent)
                        .border(
                            width = if (isEditMode) 1.dp else 0.dp,
                            color = if (isEditMode) MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f) else Color.Transparent,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(2.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (block.bulletLevel > 0) {
                                Spacer(modifier = Modifier.width((block.bulletLevel * 10).dp))
                                Text(
                                    text = "• ",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = (block.fontSizePt * 0.55f).sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = block.text,
                                style = textBlockStyle(block, textAlign),
                                color = blockColor,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                    }
                }
            }

            // 3. Embedded Images — absolutely positioned using normalized bounds
            if (slide.images.isNotEmpty()) {
                slide.images.forEach { img ->
                    AsyncImage(
                        model = File(img.filePath),
                        contentDescription = "Slide Image",
                        modifier = Modifier
                            .offset(x = absX(img.left), y = absY(img.top))
                            .size(width = absW(img.width), height = absH(img.height))
                            .clip(RoundedCornerShape(6.dp))
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                }
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
            val slideW = ppt.pageSize.width.toInt().coerceAtLeast(960)
            val slideH = ppt.pageSize.height.toInt().coerceAtLeast(540)

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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PptxWebView(
    pptxBase64: String,
    viewMode: PptxViewMode,
    currentPage: Int,
    searchQuery: String,
    currentMatchIndex: Int,
    onSlideChanged: (currentIndex: Int, totalSlides: Int) -> Unit,
    onSlideClicked: (index: Int) -> Unit,
    onWebViewReady: (WebView) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isPageLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(pptxBase64, isPageLoaded) {
        if (isPageLoaded && webViewInstance != null) {
            webViewInstance?.evaluateJavascript("renderPptxBase64('$pptxBase64')", null)
        }
    }

    LaunchedEffect(viewMode, isPageLoaded) {
        if (isPageLoaded && webViewInstance != null) {
            webViewInstance?.evaluateJavascript("setViewMode('${viewMode.name}')", null)
        }
    }

    LaunchedEffect(currentPage, isPageLoaded) {
        if (isPageLoaded && webViewInstance != null) {
            webViewInstance?.evaluateJavascript("goToSlide($currentPage)", null)
        }
    }

    LaunchedEffect(searchQuery, isPageLoaded) {
        if (isPageLoaded && webViewInstance != null) {
            val escaped = searchQuery
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", " ")
                .replace("\r", "")
            webViewInstance?.evaluateJavascript("searchPresentation('$escaped')", null)
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(true)
                }
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false

                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onSlideChanged(currentIndex: Int, totalSlides: Int) {
                        onSlideChanged(currentIndex, totalSlides)
                    }

                    @JavascriptInterface
                    fun onSlideClicked(index: Int) {
                        onSlideClicked(index)
                    }

                    @JavascriptInterface
                    fun onRenderComplete(totalSlides: Int) {
                        onSlideChanged(0, totalSlides)
                    }
                }, "AndroidBridge")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        isPageLoaded = true
                        webViewInstance = this@apply
                        onWebViewReady(this@apply)
                        evaluateJavascript("renderPptxBase64('$pptxBase64')", null)
                        evaluateJavascript("setViewMode('${viewMode.name}')", null)
                        evaluateJavascript("goToSlide($currentPage)", null)
                    }
                }

                loadUrl("file:///android_asset/pptx_viewer/viewer.html")
                webViewInstance = this
                onWebViewReady(this)
            }
        },
        update = { wv ->
            webViewInstance = wv
            onWebViewReady(wv)
        },
        modifier = modifier
    )
}

