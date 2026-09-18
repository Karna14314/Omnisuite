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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import java.io.File
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.karnadigital.omnisuite.core.util.CustomGeomPath
import com.karnadigital.omnisuite.core.util.GeomCmd
import com.karnadigital.omnisuite.core.util.ZoomableBox
import com.karnadigital.omnisuite.di.coreEntryPoint

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontFamily
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.ImageLoader
import coil.decode.SvgDecoder
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
    originalUri: String? = null,
    onBack: () -> Unit,
    onToolAction: (ViewerTool) -> Unit = {},
    viewModel: PptxViewerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uriCacheUtils = coreEntryPoint(context).uriCacheUtils()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(fileUri, originalUri) {
        viewModel.loadPptxFile(fileUri, originalUri)
    }

    LaunchedEffect(Unit) {
        viewModel.saveStatus.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val state by viewModel.loadState.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    var isEditMode by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(PptxViewMode.CONTINUOUS) }
    var showNotesPanel by remember { mutableStateOf(false) }
    var showShapePicker by remember { mutableStateOf(false) }

    var selectedShapeId by remember { mutableStateOf<String?>(null) }
    var selectedImageId by remember { mutableStateOf<String?>(null) }
    var quickEditText by remember { mutableStateOf("") }
    var ribbonTab by remember { mutableStateOf("HOME") }
    var currentFontSizePt by remember { mutableFloatStateOf(18f) }

    var activeIndexToEdit by remember { mutableStateOf<Int?>(null) }
    var blockToEdit by remember { mutableStateOf<PptxTextShape?>(null) }
    var isTitleEdit by remember { mutableStateOf(false) }
    var blockIndexToEdit by remember { mutableStateOf(-1) }
    var showFormatter by remember { mutableStateOf(false) }

    LaunchedEffect(isEditMode) {
        if (!isEditMode) {
            selectedShapeId = null
            selectedImageId = null
            quickEditText = ""
        }
    }

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val currentMatchIndex by viewModel.currentMatchIndex.collectAsState()
    var searchExpanded by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(pageCount = { 
        (state as? PptxLoadState.Success)?.presentation?.slides?.size ?: 0 
    })

    val currentSlideIndex = if (viewMode == PptxViewMode.PAGER) pagerState.currentPage else (activeIndexToEdit ?: pagerState.currentPage)

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
                        val slideIndex = currentSlideIndex
                        val targetId = selectedShapeId
                        viewModel.insertImageIntoSlide(
                            slideIndex = slideIndex,
                            imagePath = cachedFile.absolutePath,
                            targetShapeId = targetId,
                            onSuccess = {
                                Toast.makeText(
                                    context,
                                    if (targetId != null) "Picture inserted into selected box!" else "Picture inserted on slide ${slideIndex + 1}!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onError = { err ->
                                Toast.makeText(context, "Failed to insert picture: $err", Toast.LENGTH_LONG).show()
                            }
                        )
                    } else {
                        Toast.makeText(context, "Unable to access selected image", Toast.LENGTH_SHORT).show()
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
                Column {
                    if (fileUri.endsWith(".ppt", ignoreCase = true)) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "View Mode (Read-Only .ppt format). Convert to .pptx to enable editing.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
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
                                    Icon(Icons.Default.Search, contentDescription = "Search text")
                                }

                                if (isEditMode) {
                                    IconButton(
                                        onClick = { viewModel.undo() },
                                        enabled = canUndo
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Undo,
                                            contentDescription = "Undo",
                                            tint = if (canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.redo() },
                                        enabled = canRedo
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Redo,
                                            contentDescription = "Redo",
                                            tint = if (canRedo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        )
                                    }

                                     IconButton(
                                          onClick = {
                                              selectedShapeId?.let { id ->
                                                  viewModel.updateShapeText(currentSlideIndex, id, quickEditText)
                                              }
                                              viewModel.commitChanges {
                                                  isEditMode = false
                                                  selectedShapeId = null
                                                  Toast.makeText(context, "Saved presentation changes!", Toast.LENGTH_SHORT).show()
                                              }
                                          },
                                          enabled = !isSaving
                                      ) {
                                         if (isSaving) {
                                             CircularProgressIndicator(
                                                 modifier = Modifier.size(20.dp),
                                                 strokeWidth = 2.dp,
                                                 color = MaterialTheme.colorScheme.primary
                                             )
                                         } else {
                                             Icon(
                                                 imageVector = Icons.Default.Check,
                                                 contentDescription = "Save & Exit Edit Mode",
                                                 tint = MaterialTheme.colorScheme.primary
                                             )
                                         }
                                     }
                                }

                                IconButton(onClick = { isEditMode = !isEditMode }) {
                                    Icon(
                                        imageVector = if (isEditMode) Icons.Default.Close else Icons.Default.Edit,
                                        contentDescription = if (isEditMode) "Cancel Edit Mode" else "Edit Presentation",
                                        tint = if (isEditMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
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
            }
        },
        bottomBar = {
            if (state is PptxLoadState.Success && viewMode != PptxViewMode.SLIDESHOW) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // M365 Presentation Editing Ribbon (when edit mode active)
                    AnimatedVisibility(
                        visible = isEditMode,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Quick In-Place Text Editor Strip (appears when a shape is selected)
                            if (selectedShapeId != null) {
                                LaunchedEffect(quickEditText) {
                                    delay(500)
                                    selectedShapeId?.let { id ->
                                        viewModel.updateShapeText(currentSlideIndex, id, quickEditText)
                                    }
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    tonalElevation = 6.dp,
                                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            OutlinedTextField(
                                                value = quickEditText,
                                                onValueChange = {
                                                    quickEditText = it
                                                },
                                                placeholder = { Text("Type shape text...", fontSize = 13.sp) },
                                                singleLine = false,
                                                maxLines = 4,
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                                keyboardActions = KeyboardActions(
                                                    onDone = {
                                                        selectedShapeId?.let { id ->
                                                            viewModel.updateShapeText(currentSlideIndex, id, quickEditText)
                                                        }
                                                        selectedShapeId = null
                                                    }
                                                ),
                                                modifier = Modifier.weight(1f),
                                                textStyle = TextStyle(fontSize = 13.sp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            IconButton(
                                                onClick = {
                                                    selectedShapeId?.let { id ->
                                                        viewModel.updateShapeText(currentSlideIndex, id, quickEditText)
                                                    }
                                                    selectedShapeId = null
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = "Apply Text", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(
                                                onClick = { showFormatter = true },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(Icons.Default.Tune, contentDescription = "Advanced Formatter", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(
                                                onClick = {
                                                    selectedShapeId?.let { id ->
                                                        viewModel.deleteShape(currentSlideIndex, id)
                                                        selectedShapeId = null
                                                        quickEditText = ""
                                                    }
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete Shape", tint = MaterialTheme.colorScheme.error)
                                            }
                                            IconButton(
                                                onClick = {
                                                    selectedShapeId?.let { id ->
                                                        if (quickEditText.isNotBlank()) {
                                                            viewModel.updateShapeText(currentSlideIndex, id, quickEditText)
                                                        }
                                                    }
                                                    selectedShapeId = null
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Done Editing Shape")
                                            }
                                        }

                                        // Shape Move & Resize Control Bar
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Move", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                IconButton(
                                                    onClick = { selectedShapeId?.let { id -> viewModel.moveShape(currentSlideIndex, id, -0.02f, 0f) } },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.ArrowBack, contentDescription = "Move Left", modifier = Modifier.size(16.dp))
                                                }
                                                IconButton(
                                                    onClick = { selectedShapeId?.let { id -> viewModel.moveShape(currentSlideIndex, id, 0f, -0.02f) } },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(16.dp))
                                                }
                                                IconButton(
                                                    onClick = { selectedShapeId?.let { id -> viewModel.moveShape(currentSlideIndex, id, 0f, 0.02f) } },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(16.dp))
                                                }
                                                IconButton(
                                                    onClick = { selectedShapeId?.let { id -> viewModel.moveShape(currentSlideIndex, id, 0.02f, 0f) } },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.ArrowForward, contentDescription = "Move Right", modifier = Modifier.size(16.dp))
                                                }
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Scale", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                IconButton(
                                                    onClick = { selectedShapeId?.let { id -> viewModel.resizeShape(currentSlideIndex, id, 0.9f) } },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.ZoomOut, contentDescription = "Scale Down", modifier = Modifier.size(16.dp))
                                                }
                                                IconButton(
                                                    onClick = { selectedShapeId?.let { id -> viewModel.resizeShape(currentSlideIndex, id, 1.1f) } },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.ZoomIn, contentDescription = "Scale Up", modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Quick Image Editor Strip (appears when an image is selected)
                            if (selectedImageId != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    tonalElevation = 6.dp,
                                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Image,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Image Selected",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            IconButton(
                                                onClick = { selectedImageId?.let { id -> viewModel.moveImage(currentSlideIndex, id, -0.02f, 0f) } },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.ArrowBack, contentDescription = "Move Left", modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(
                                                onClick = { selectedImageId?.let { id -> viewModel.moveImage(currentSlideIndex, id, 0f, -0.02f) } },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(
                                                onClick = { selectedImageId?.let { id -> viewModel.moveImage(currentSlideIndex, id, 0f, 0.02f) } },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(
                                                onClick = { selectedImageId?.let { id -> viewModel.moveImage(currentSlideIndex, id, 0.02f, 0f) } },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.ArrowForward, contentDescription = "Move Right", modifier = Modifier.size(16.dp))
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            IconButton(
                                                onClick = {
                                                    selectedImageId?.let { id ->
                                                        viewModel.scaleImage(currentSlideIndex, id, 0.9f)
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.ZoomOut, contentDescription = "Shrink -10%", modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(
                                                onClick = {
                                                    selectedImageId?.let { id ->
                                                        viewModel.scaleImage(currentSlideIndex, id, 1.1f)
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.ZoomIn, contentDescription = "Enlarge +10%", modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(
                                                onClick = {
                                                    selectedImageId?.let { id ->
                                                        viewModel.deleteImage(currentSlideIndex, id)
                                                        selectedImageId = null
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete Image", tint = MaterialTheme.colorScheme.error)
                                            }
                                            IconButton(
                                                onClick = { selectedImageId = null },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Deselect Image")
                                            }
                                        }
                                    }
                                }
                            }

                            // Microsoft 365 Ribbon Toolbar
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                tonalElevation = 8.dp,
                                shadowElevation = 8.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // Ribbon Tabs Header: HOME | INSERT | SLIDE | MANAGE
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        listOf("HOME", "INSERT", "SLIDE", "MANAGE").forEach { tab ->
                                            val isTabSelected = ribbonTab == tab
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = if (isTabSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                modifier = Modifier.clickable { ribbonTab = tab }
                                            ) {
                                                Text(
                                                    text = tab,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isTabSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                    // Ribbon Tab Body
                                    when (ribbonTab) {
                                        "HOME" -> {
                                            val currentSlideForFormatting = (state as? PptxLoadState.Success)?.presentation?.slides?.getOrNull(currentSlideIndex)
                                            val selectedTextShape = currentSlideForFormatting?.let { s ->
                                                if (s.title.id == selectedShapeId) s.title
                                                else s.textShapes.firstOrNull { it.id == selectedShapeId }
                                            }
                                            val currentBold = selectedTextShape?.isBold ?: false
                                            val currentItalic = selectedTextShape?.isItalic ?: false
                                            val currentUnderline = selectedTextShape?.isUnderline ?: false

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState())
                                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(
                                                    onClick = { viewModel.undo() },
                                                    enabled = canUndo,
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Undo,
                                                        contentDescription = "Undo",
                                                        tint = if (canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { viewModel.redo() },
                                                    enabled = canRedo,
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Redo,
                                                        contentDescription = "Redo",
                                                        tint = if (canRedo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                                    )
                                                }
                                                VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 2.dp))

                                                FormatRibbonToggleButton(
                                                    label = "B",
                                                    isSelected = currentBold,
                                                    fontWeight = FontWeight.Bold,
                                                    onClick = {
                                                        selectedShapeId?.let { id ->
                                                            viewModel.applyShapeFormatting(currentSlideIndex, id, isBold = !currentBold)
                                                        } ?: Toast.makeText(context, "Select a text box first", Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                                FormatRibbonToggleButton(
                                                    label = "I",
                                                    isSelected = currentItalic,
                                                    fontStyle = FontStyle.Italic,
                                                    onClick = {
                                                        selectedShapeId?.let { id ->
                                                            viewModel.applyShapeFormatting(currentSlideIndex, id, isItalic = !currentItalic)
                                                        } ?: Toast.makeText(context, "Select a text box first", Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                                FormatRibbonToggleButton(
                                                    label = "U",
                                                    isSelected = currentUnderline,
                                                    textDecoration = TextDecoration.Underline,
                                                    onClick = {
                                                        selectedShapeId?.let { id ->
                                                            viewModel.applyShapeFormatting(currentSlideIndex, id, isUnderline = !currentUnderline)
                                                        } ?: Toast.makeText(context, "Select a text box first", Toast.LENGTH_SHORT).show()
                                                    }
                                                )

                                                VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 2.dp))

                                                // Font Size Stepper
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    IconButton(
                                                        onClick = {
                                                            if (currentFontSizePt > 8f) {
                                                                currentFontSizePt -= 2f
                                                                selectedShapeId?.let { id ->
                                                                    viewModel.applyShapeFormatting(currentSlideIndex, id, fontSizePt = currentFontSizePt)
                                                                }
                                                            }
                                                        },
                                                        modifier = Modifier.size(30.dp)
                                                    ) {
                                                        Text("-", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    Text(
                                                        text = "${currentFontSizePt.toInt()}pt",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.padding(horizontal = 4.dp)
                                                    )
                                                    IconButton(
                                                        onClick = {
                                                            if (currentFontSizePt < 72f) {
                                                                currentFontSizePt += 2f
                                                                selectedShapeId?.let { id ->
                                                                    viewModel.applyShapeFormatting(currentSlideIndex, id, fontSizePt = currentFontSizePt)
                                                                }
                                                            }
                                                        },
                                                        modifier = Modifier.size(30.dp)
                                                    ) {
                                                        Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }

                                                VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 2.dp))

                                                // Text Color Swatches
                                                val textColors = listOf("#000000", "#D32F2F", "#1976D2", "#388E3C", "#7B1FA2", "#F57C00")
                                                textColors.forEach { hex ->
                                                    Box(
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .clip(CircleShape)
                                                            .background(safeParseColor(hex, Color.Black))
                                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                                            .clickable {
                                                                selectedShapeId?.let { id ->
                                                                    viewModel.applyShapeFormatting(currentSlideIndex, id, textColorHex = hex)
                                                                } ?: Toast.makeText(context, "Select a text box first", Toast.LENGTH_SHORT).show()
                                                            }
                                                    )
                                                }

                                                VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 2.dp))

                                                IconButton(
                                                    onClick = {
                                                        selectedShapeId?.let { id ->
                                                            viewModel.applyShapeFormatting(currentSlideIndex, id, alignment = "LEFT")
                                                        }
                                                    },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(Icons.Default.FormatAlignLeft, contentDescription = "Align Left")
                                                }
                                                IconButton(
                                                    onClick = {
                                                        selectedShapeId?.let { id ->
                                                            viewModel.applyShapeFormatting(currentSlideIndex, id, alignment = "CENTER")
                                                        }
                                                    },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(Icons.Default.FormatAlignCenter, contentDescription = "Align Center")
                                                }
                                                IconButton(
                                                    onClick = {
                                                        selectedShapeId?.let { id ->
                                                            viewModel.applyShapeFormatting(currentSlideIndex, id, alignment = "RIGHT")
                                                        }
                                                    },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(Icons.Default.FormatAlignRight, contentDescription = "Align Right")
                                                }
                                            }
                                        }

                                        "INSERT" -> {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState())
                                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RibbonActionCard(
                                                    icon = Icons.Default.TextFields,
                                                    title = "Text Box",
                                                    subtitle = "Add text",
                                                    isPrimary = true,
                                                    onClick = {
                                                        viewModel.insertTextBox(currentSlideIndex)
                                                        Toast.makeText(context, "Text box added to slide!", Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                                RibbonActionCard(
                                                    icon = Icons.Default.Category,
                                                    title = "Shape",
                                                    subtitle = "Insert geometry",
                                                    onClick = { showShapePicker = true }
                                                )
                                                RibbonActionCard(
                                                    icon = Icons.Default.Image,
                                                    title = "Picture",
                                                    subtitle = "Insert image",
                                                    onClick = { imagePickerLauncher.launch("image/*") }
                                                )
                                                RibbonActionCard(
                                                    icon = Icons.Default.Add,
                                                    title = "New Slide",
                                                    subtitle = "Add blank",
                                                    onClick = {
                                                        viewModel.addSlide(currentSlideIndex) { newIdx ->
                                                            coroutineScope.launch {
                                                                pagerState.animateScrollToPage(newIdx)
                                                            }
                                                        }
                                                        Toast.makeText(context, "New slide added!", Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                                RibbonActionCard(
                                                    icon = Icons.Default.ContentCopy,
                                                    title = "Duplicate",
                                                    subtitle = "Copy slide",
                                                    onClick = {
                                                        viewModel.duplicateSlide(currentSlideIndex) { newIdx ->
                                                            coroutineScope.launch {
                                                                pagerState.animateScrollToPage(newIdx)
                                                            }
                                                        }
                                                        Toast.makeText(context, "Slide duplicated!", Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            }
                                        }

                                        "SLIDE" -> {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState())
                                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Background:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                val bgColors = listOf("#FFFFFF", "#F5F5F5", "#E3F2FD", "#FFF8E1", "#E8F5E9", "#212121")
                                                bgColors.forEach { hex ->
                                                    Box(
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(safeParseColor(hex, Color.White))
                                                            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                                                            .clickable {
                                                                viewModel.setSlideBackground(currentSlideIndex, hex)
                                                            }
                                                    )
                                                }

                                                VerticalDivider(modifier = Modifier.height(28.dp).padding(horizontal = 2.dp))

                                                val currentSlideIdx = currentSlideIndex
                                                val totalSlidesCount = (state as? PptxLoadState.Success)?.presentation?.slides?.size ?: 0

                                                RibbonActionCard(
                                                    icon = Icons.Default.ArrowBack,
                                                    title = "Move Left",
                                                    subtitle = "Slide order",
                                                    enabled = currentSlideIdx > 0,
                                                    onClick = {
                                                        if (currentSlideIdx > 0) {
                                                            viewModel.moveSlide(currentSlideIdx, currentSlideIdx - 1)
                                                            coroutineScope.launch {
                                                                pagerState.animateScrollToPage(currentSlideIdx - 1)
                                                            }
                                                        }
                                                    }
                                                )
                                                RibbonActionCard(
                                                    icon = Icons.Default.ArrowForward,
                                                    title = "Move Right",
                                                    subtitle = "Slide order",
                                                    enabled = currentSlideIdx < totalSlidesCount - 1,
                                                    onClick = {
                                                        if (currentSlideIdx < totalSlidesCount - 1) {
                                                            viewModel.moveSlide(currentSlideIdx, currentSlideIdx + 1)
                                                            coroutineScope.launch {
                                                                pagerState.animateScrollToPage(currentSlideIdx + 1)
                                                            }
                                                        }
                                                    }
                                                )
                                                RibbonActionCard(
                                                    icon = Icons.Default.Delete,
                                                    title = "Delete Slide",
                                                    subtitle = "Remove slide",
                                                    isDestructive = true,
                                                    onClick = { viewModel.deleteSlide(currentSlideIndex) }
                                                )
                                            }
                                        }

                                        "MANAGE" -> {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState())
                                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RibbonActionCard(
                                                    icon = Icons.Default.Save,
                                                    title = if (isSaving) "Saving..." else "Save PPTX",
                                                    subtitle = "Commit changes",
                                                    isPrimary = true,
                                                    enabled = !isSaving,
                                                    onClick = {
                                                        viewModel.commitChanges {
                                                            isEditMode = false
                                                            Toast.makeText(context, "Saved changes to presentation!", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                )
                                                RibbonActionCard(
                                                    icon = Icons.Default.Delete,
                                                    title = "Delete Box",
                                                    subtitle = "Remove shape",
                                                    isDestructive = true,
                                                    enabled = selectedShapeId != null,
                                                    onClick = {
                                                        selectedShapeId?.let { id ->
                                                            viewModel.deleteShape(currentSlideIndex, id)
                                                            selectedShapeId = null
                                                            quickEditText = ""
                                                        }
                                                    }
                                                )
                                                RibbonActionCard(
                                                    icon = Icons.Default.Undo,
                                                    title = "Undo",
                                                    subtitle = "Revert edit",
                                                    enabled = canUndo,
                                                    onClick = { viewModel.undo() }
                                                )
                                                RibbonActionCard(
                                                    icon = Icons.Default.Redo,
                                                    title = "Redo",
                                                    subtitle = "Repeat edit",
                                                    enabled = canRedo,
                                                    onClick = { viewModel.redo() }
                                                )
                                                RibbonActionCard(
                                                    icon = Icons.Default.Close,
                                                    title = "Exit Editor",
                                                    subtitle = "Back to viewer",
                                                    onClick = { isEditMode = false }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Main Dock Action Bar
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
                                icon = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                                title = if (isEditMode) "Done" else "Edit"
                            ) {
                                isEditMode = !isEditMode
                                if (isEditMode && viewMode != PptxViewMode.PAGER) {
                                    viewMode = PptxViewMode.PAGER
                                }
                            }

                            ViewerActionColumnButton(
                                icon = Icons.Default.PlayArrow,
                                title = "SlideShow"
                            ) {
                                viewMode = PptxViewMode.SLIDESHOW
                            }

                            ViewerActionColumnButton(
                                icon = Icons.Default.SpeakerNotes,
                                title = "Notes"
                            ) {
                                showNotesPanel = !showNotesPanel
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
                                selectedShapeId = selectedShapeId,
                                selectedImageId = selectedImageId,
                                onVisibleSlideChange = { idx ->
                                    if (selectedShapeId == null && selectedImageId == null) {
                                        activeIndexToEdit = idx
                                    }
                                },
                                onTextBlockClick = { slideIdx, textBlock, isTitle, blockIdx ->
                                    activeIndexToEdit = slideIdx
                                    blockToEdit = textBlock
                                    isTitleEdit = isTitle
                                    blockIndexToEdit = blockIdx
                                    selectedShapeId = textBlock.id
                                    selectedImageId = null
                                    quickEditText = textBlock.fullText
                                    currentFontSizePt = textBlock.fontSizePt
                                },
                                onImageClick = { slideIdx, img ->
                                    activeIndexToEdit = slideIdx
                                    selectedImageId = img.id
                                    selectedShapeId = null
                                },
                                onShapeMove = { slideIdx, shapeId, dx, dy ->
                                    viewModel.moveShape(slideIdx, shapeId, dx, dy)
                                },
                                onImageMove = { slideIdx, imgId, dx, dy ->
                                    viewModel.moveImage(slideIdx, imgId, dx, dy)
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
                                                selectedShapeId = selectedShapeId,
                                                selectedImageId = selectedImageId,
                                                onTextBlockClick = { textBlock, isTitle, blockIdx ->
                                                    blockToEdit = textBlock
                                                    activeIndexToEdit = pageIndex
                                                    isTitleEdit = isTitle
                                                    blockIndexToEdit = blockIdx
                                                    selectedShapeId = textBlock.id
                                                    selectedImageId = null
                                                    quickEditText = textBlock.fullText
                                                    currentFontSizePt = textBlock.fontSizePt
                                                },
                                                onImageClick = { img ->
                                                    selectedImageId = img.id
                                                    selectedShapeId = null
                                                    activeIndexToEdit = pageIndex
                                                },
                                                onShapeMove = { shapeId, dx, dy ->
                                                    viewModel.moveShape(pageIndex, shapeId, dx, dy)
                                                },
                                                onImageMove = { imgId, dx, dy ->
                                                    viewModel.moveImage(pageIndex, imgId, dx, dy)
                                                }
                                            )
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

                            if (showShapePicker) {
                                var selectedGeometry by remember { mutableStateOf(ShapeGeometryType.RECTANGLE) }
                                var selectedFillColor by remember { mutableStateOf("#1976D2") }
                                var selectedBorderColor by remember { mutableStateOf("#0D47A1") }

                                val shapeOptions = listOf(
                                    Triple(ShapeGeometryType.RECTANGLE, "Rectangle", Icons.Default.CropSquare),
                                    Triple(ShapeGeometryType.ROUNDED_RECTANGLE, "Rounded", Icons.Default.CropSquare),
                                    Triple(ShapeGeometryType.ELLIPSE, "Circle", Icons.Default.Circle),
                                    Triple(ShapeGeometryType.TRIANGLE, "Triangle", Icons.Default.ChangeHistory),
                                    Triple(ShapeGeometryType.RIGHT_ARROW, "Arrow", Icons.Default.ArrowForward),
                                    Triple(ShapeGeometryType.STAR, "Star", Icons.Default.Star),
                                    Triple(ShapeGeometryType.LINE, "Line", Icons.Default.HorizontalRule)
                                )
                                val colorOptions = listOf(
                                    "#1976D2" to "#0D47A1",
                                    "#D32F2F" to "#B71C1C",
                                    "#388E3C" to "#1B5E20",
                                    "#F57C00" to "#E65100",
                                    "#7B1FA2" to "#4A148C",
                                    "#424242" to "#212121",
                                    "#00897B" to "#004D40",
                                    "#C2185B" to "#880E4F"
                                )

                                AlertDialog(
                                    onDismissRequest = { showShapePicker = false },
                                    title = {
                                        Text("Insert Shape", fontWeight = FontWeight.Bold)
                                    },
                                    text = {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                "Shape Geometry",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                items(shapeOptions.size) { idx ->
                                                    val (geom, label, icon) = shapeOptions[idx]
                                                    val isSelected = selectedGeometry == geom
                                                    Surface(
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                                        modifier = Modifier.clickable { selectedGeometry = geom }
                                                    ) {
                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = icon,
                                                                contentDescription = label,
                                                                modifier = Modifier.size(24.dp),
                                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Text(
                                                                text = label,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                "Fill & Border Color",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                items(colorOptions.size) { idx ->
                                                    val (fill, border) = colorOptions[idx]
                                                    val isColorSelected = selectedFillColor == fill
                                                    Box(
                                                        modifier = Modifier
                                                            .size(34.dp)
                                                            .clip(CircleShape)
                                                            .background(safeParseColor(fill, Color.Blue))
                                                            .border(
                                                                width = if (isColorSelected) 3.dp else 1.dp,
                                                                color = if (isColorSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                                                shape = CircleShape
                                                            )
                                                            .clickable {
                                                                selectedFillColor = fill
                                                                selectedBorderColor = border
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (isColorSelected) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = "Selected",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                viewModel.insertShape(
                                                    slideIndex = currentSlideIndex,
                                                    geometry = selectedGeometry,
                                                    fillColorHex = selectedFillColor,
                                                    borderColorHex = selectedBorderColor
                                                )
                                                showShapePicker = false
                                                Toast.makeText(context, "Shape inserted into slide!", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Text("Insert")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showShapePicker = false }) {
                                            Text("Cancel")
                                        }
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
    var text by remember { mutableStateOf(textBlock.fullText) }
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
                    Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = "Insert Image", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Insert Picture")
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
    selectedShapeId: String? = null,
    selectedImageId: String? = null,
    onVisibleSlideChange: (Int) -> Unit = {},
    onTextBlockClick: (slideIndex: Int, PptxTextShape, isTitle: Boolean, blockIndex: Int) -> Unit,
    onImageClick: ((slideIndex: Int, PptxImage) -> Unit)? = null,
    onShapeMove: ((slideIndex: Int, shapeId: String, deltaXFrac: Float, deltaYFrac: Float) -> Unit)? = null,
    onImageMove: ((slideIndex: Int, imageId: String, deltaXFrac: Float, deltaYFrac: Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val firstVisible = remember { derivedStateOf { listState.firstVisibleItemIndex } }
    LaunchedEffect(firstVisible.value) {
        if (firstVisible.value in presentation.slides.indices) {
            onVisibleSlideChange(firstVisible.value)
        }
    }

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
                            selectedShapeId = selectedShapeId,
                            selectedImageId = selectedImageId,
                            onTextBlockClick = { textBlock, isTitle, blockIdx ->
                                onTextBlockClick(index, textBlock, isTitle, blockIdx)
                            },
                            onImageClick = { img ->
                                onImageClick?.invoke(index, img)
                            },
                            onShapeMove = { shapeId, dx, dy ->
                                onShapeMove?.invoke(index, shapeId, dx, dy)
                            },
                            onImageMove = { imgId, dx, dy ->
                                onImageMove?.invoke(index, imgId, dx, dy)
                            }
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
    selectedShapeId: String? = null,
    selectedImageId: String? = null,
    onTextBlockClick: (PptxTextShape, isTitle: Boolean, blockIndex: Int) -> Unit,
    onImageClick: ((PptxImage) -> Unit)? = null,
    onShapeMove: ((shapeId: String, deltaXFrac: Float, deltaYFrac: Float) -> Unit)? = null,
    onImageMove: ((imageId: String, deltaXFrac: Float, deltaYFrac: Float) -> Unit)? = null
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
            val density = LocalDensity.current
            val slideW = maxWidth.value
            val slideH = maxHeight.value
            val title = slide.title
            val slideWidthPt = if (slide.slideWidthPt > 0f) slide.slideWidthPt else 960f
            val fontScale = slideW / slideWidthPt

            val context = LocalContext.current
            val imageLoader = remember(context) {
                ImageLoader.Builder(context)
                    .components {
                        add(SvgDecoder.Factory())
                    }
                    .build()
            }

            // LAYER 1: Background Image (bottom layer, full slide coverage)
            slide.backgroundImage?.let { bgImg ->
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(File(bgImg.filePath))
                        .crossfade(true)
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = "Slide Background",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }

            // Combine foreground images and text shapes into a single z-ordered list
            val imageElements = slide.images.map { img -> SlideElement.ImageElement(img) }
            val titleElement = if (title.fullText.isNotBlank() && title.id == "title") {
                listOf(SlideElement.TextElement(title, true, 0))
            } else emptyList()
            val bodyElements = slide.textShapes.mapIndexed { idx, shape ->
                SlideElement.TextElement(shape, shape.isTitle, idx)
            }
            val allElements = (imageElements + titleElement + bodyElements).sortedBy { it.zOrder }

            // Render all elements in z-order (bottom to top)
            allElements.forEach { element ->
                when (element) {
                    is SlideElement.ImageElement -> {
                        val isImgSelected = isEditMode && (element.image.id.isNotEmpty() && element.image.id == selectedImageId)
                        var dragOffsetX by remember(element.image.id, isImgSelected) { mutableFloatStateOf(0f) }
                        var dragOffsetY by remember(element.image.id, isImgSelected) { mutableFloatStateOf(0f) }
                        // Picture-fill of an AutoShape must keep that shape's outline;
                        // a plain rect clip is what produced the "crude cube" look.
                        val imgClip = element.image.customClip?.let { customGeomToShape(it) }
                            ?: if (element.image.isShapeFill) geometryToShape(element.image.clipGeometry) else RoundedCornerShape(4.dp)

                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(File(element.image.filePath))
                                .crossfade(true)
                                .build(),
                            imageLoader = imageLoader,
                            contentDescription = "Slide Image",
                            modifier = Modifier
                                .offset(x = (element.image.left * slideW).dp, y = (element.image.top * slideH).dp)
                                .offset { IntOffset(dragOffsetX.roundToInt(), dragOffsetY.roundToInt()) }
                                .size(width = (element.image.width * slideW).dp, height = (element.image.height * slideH).dp)
                                .clip(imgClip)
                                .then(
                                    if (isImgSelected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, imgClip)
                                    else if (isEditMode) Modifier.border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f), imgClip)
                                    else Modifier
                                )
                                .then(
                                    if (isImgSelected && isEditMode) {
                                        Modifier.pointerInput(element.image.id) {
                                            detectDragGestures(
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragOffsetX += dragAmount.x
                                                    dragOffsetY += dragAmount.y
                                                },
                                                onDragEnd = {
                                                    val densityDpi = density.density
                                                    val deltaXFrac = dragOffsetX / (slideW * densityDpi)
                                                    val deltaYFrac = dragOffsetY / (slideH * densityDpi)
                                                    dragOffsetX = 0f
                                                    dragOffsetY = 0f
                                                    if (kotlin.math.abs(deltaXFrac) > 0.005f || kotlin.math.abs(deltaYFrac) > 0.005f) {
                                                        onImageMove?.invoke(element.image.id, deltaXFrac, deltaYFrac)
                                                    }
                                                },
                                                onDragCancel = {
                                                    dragOffsetX = 0f
                                                    dragOffsetY = 0f
                                                }
                                            )
                                        }
                                    } else if (isEditMode) {
                                        Modifier.clickable { onImageClick?.invoke(element.image) }
                                    } else Modifier
                                ),
                            contentScale = if (element.image.isShapeFill) ContentScale.Crop else ContentScale.Fit
                        )
                    }
                    is SlideElement.TextElement -> {
                        val isSelected = isEditMode && (element.shape.id == selectedShapeId)
                        var dragOffsetX by remember(element.shape.id, isSelected) { mutableFloatStateOf(0f) }
                        var dragOffsetY by remember(element.shape.id, isSelected) { mutableFloatStateOf(0f) }

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(dragOffsetX.roundToInt(), dragOffsetY.roundToInt()) }
                                .then(
                                    if (isSelected && isEditMode) {
                                        Modifier.pointerInput(element.shape.id) {
                                            detectDragGestures(
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragOffsetX += dragAmount.x
                                                    dragOffsetY += dragAmount.y
                                                },
                                                onDragEnd = {
                                                    val densityDpi = density.density
                                                    val deltaXFrac = dragOffsetX / (slideW * densityDpi)
                                                    val deltaYFrac = dragOffsetY / (slideH * densityDpi)
                                                    dragOffsetX = 0f
                                                    dragOffsetY = 0f
                                                    if (kotlin.math.abs(deltaXFrac) > 0.005f || kotlin.math.abs(deltaYFrac) > 0.005f) {
                                                        onShapeMove?.invoke(element.shape.id, deltaXFrac, deltaYFrac)
                                                    }
                                                },
                                                onDragCancel = {
                                                    dragOffsetX = 0f
                                                    dragOffsetY = 0f
                                                }
                                            )
                                        }
                                    } else Modifier
                                )
                        ) {
                            TextShapeItem(
                                shape = element.shape,
                                slideW = slideW,
                                slideH = slideH,
                                fontScale = fontScale,
                                isTitle = element.isTitle,
                                isEditMode = isEditMode,
                                isSelected = isSelected,
                                onClick = { onTextBlockClick(element.shape, element.isTitle, element.index) }
                            )
                        }
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

/** True geometric ellipse shape matching PowerPoint's oval geometry */
private val EllipseShape = GenericShape { size, _ ->
    addOval(Rect(0f, 0f, size.width, size.height))
}

/** True geometric hexagon shape matching PowerPoint's hexagon geometry */
private val HexagonShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(w * 0.25f, 0f)
    lineTo(w * 0.75f, 0f)
    lineTo(w, h * 0.5f)
    lineTo(w * 0.75f, h)
    lineTo(w * 0.25f, h)
    lineTo(0f, h * 0.5f)
    close()
}

/** True geometric triangle shape */
private val TriangleShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(w * 0.5f, 0f)
    lineTo(w, h)
    lineTo(0f, h)
    close()
}

/** True geometric diamond shape */
private val DiamondShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(w * 0.5f, 0f)
    lineTo(w, h * 0.5f)
    lineTo(w * 0.5f, h)
    lineTo(0f, h * 0.5f)
    close()
}

/** True geometric chevron shape */
private val ChevronShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(0f, 0f)
    lineTo(w * 0.75f, 0f)
    lineTo(w, h * 0.5f)
    lineTo(w * 0.75f, h)
    lineTo(0f, h)
    lineTo(w * 0.25f, h * 0.5f)
    close()
}

/** True geometric 5-point star shape */
private val StarShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val outerR = min(cx, cy)
    val innerR = outerR * 0.42f
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outerR else innerR
        val angle = Math.toRadians((i * 36 - 90).toDouble())
        val x = cx + (r * kotlin.math.cos(angle)).toFloat()
        val y = cy + (r * kotlin.math.sin(angle)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** True geometric regular pentagon shape */
private val PentagonShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val r = min(cx, cy)
    for (i in 0 until 5) {
        val angle = Math.toRadians((i * 72 - 90).toDouble())
        val x = cx + (r * kotlin.math.cos(angle)).toFloat()
        val y = cy + (r * kotlin.math.sin(angle)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** True geometric right arrow shape */
private val RightArrowShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(0f, h * 0.25f)
    lineTo(w * 0.6f, h * 0.25f)
    lineTo(w * 0.6f, 0f)
    lineTo(w, h * 0.5f)
    lineTo(w * 0.6f, h)
    lineTo(w * 0.6f, h * 0.75f)
    lineTo(0f, h * 0.75f)
    close()
}

/** True geometric rectangular callout with bottom tail */
private val CalloutShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    val bodyH = h * 0.8f
    moveTo(0f, 0f)
    lineTo(w, 0f)
    lineTo(w, bodyH)
    lineTo(w * 0.45f, bodyH)
    lineTo(w * 0.25f, h)
    lineTo(w * 0.3f, bodyH)
    lineTo(0f, bodyH)
    close()
}

/** True geometric parallelogram shape */
private val ParallelogramShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    val shift = w * 0.2f
    moveTo(shift, 0f)
    lineTo(w, 0f)
    lineTo(w - shift, h)
    lineTo(0f, h)
    close()
}

/** True geometric line/divider shape */
private val LineShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    val midY = h / 2f
    val thickness = (h * 0.2f).coerceIn(2f, 8f)
    moveTo(0f, midY - thickness / 2f)
    lineTo(w, midY - thickness / 2f)
    lineTo(w, midY + thickness / 2f)
    lineTo(0f, midY + thickness / 2f)
    close()
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

private fun resolveFontFamily(family: String?): FontFamily {
    if (family == null) return FontFamily.Default
    val f = family.lowercase()
    return when {
        f.contains("times") || f.contains("georgia") || f.contains("cambria") || f.contains("garamond") || f.contains("palatino") || f.contains("serif") -> FontFamily.Serif
        f.contains("courier") || f.contains("consolas") || f.contains("mono") -> FontFamily.Monospace
        f.contains("comic") || f.contains("cursive") || f.contains("script") -> FontFamily.Cursive
        else -> FontFamily.SansSerif
    }
}

/** Builds a clip/fill Shape from a parsed DrawingML custom-geometry path. */
private fun customGeomToShape(geom: CustomGeomPath): androidx.compose.ui.graphics.Shape {
    return GenericShape { size, _ ->
        val sx = if (geom.viewW > 0f) size.width / geom.viewW else 1f
        val sy = if (geom.viewH > 0f) size.height / geom.viewH else 1f
        for (cmd in geom.commands) {
            when (cmd) {
                is GeomCmd.MoveTo -> moveTo(cmd.x * sx, cmd.y * sy)
                is GeomCmd.LineTo -> lineTo(cmd.x * sx, cmd.y * sy)
                is GeomCmd.CubicTo -> cubicTo(
                    cmd.x1 * sx, cmd.y1 * sy, cmd.x2 * sx, cmd.y2 * sy, cmd.x3 * sx, cmd.y3 * sy
                )
                is GeomCmd.QuadTo -> quadraticBezierTo(cmd.x1 * sx, cmd.y1 * sy, cmd.x2 * sx, cmd.y2 * sy)
                is GeomCmd.Close -> close()
            }
        }
    }
}

private fun geometryToShape(geom: ShapeGeometryType, isTableCell: Boolean = false): androidx.compose.ui.graphics.Shape {
    return when (geom) {
        ShapeGeometryType.ELLIPSE -> EllipseShape
        ShapeGeometryType.HEXAGON -> HexagonShape
        ShapeGeometryType.TRIANGLE -> TriangleShape
        ShapeGeometryType.DIAMOND -> DiamondShape
        ShapeGeometryType.CHEVRON -> ChevronShape
        ShapeGeometryType.STAR -> StarShape
        ShapeGeometryType.PENTAGON -> PentagonShape
        ShapeGeometryType.RIGHT_ARROW -> RightArrowShape
        ShapeGeometryType.CALLOUT -> CalloutShape
        ShapeGeometryType.PARALLELOGRAM -> ParallelogramShape
        ShapeGeometryType.LINE -> LineShape
        ShapeGeometryType.ROUNDED_RECTANGLE -> RoundedCornerShape(8.dp)
        else -> RoundedCornerShape(if (isTableCell) 0.dp else 2.dp)
    }
}

@Composable
fun TextShapeItem(
    shape: PptxTextShape,
    slideW: Float,
    slideH: Float,
    fontScale: Float,
    isTitle: Boolean,
    isEditMode: Boolean,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    val shapeGeom = shape.shapeGeometry
    val shapeBorder = shape.shapeBorder
    // Custom DrawingML outline wins over the preset-geometry fallback (hexagon/blob
    // backdrops otherwise render as crude rectangles).
    val shapeBgColor = shape.backgroundColorHex?.let { safeParseColor(it, Color.Transparent) }
        ?: Color.Transparent
    val isEllipseBadge = shapeGeom == ShapeGeometryType.ELLIPSE
    val isTableCell = shape.id.startsWith("table_cell_")

    // Custom DrawingML outline wins over the preset-geometry fallback (hexagon/blob
    // backdrops otherwise render as crude rectangles).
    val shapeShape = shape.customPath?.let { customGeomToShape(it) } ?: geometryToShape(shapeGeom, isTableCell)

    val borderWidth = when {
        shape.isBackgroundShape -> 0.dp
        isSelected -> 2.dp
        shapeBorder != null && shapeBorder.strokeColorHex != null -> shapeBorder.strokeWidthDp.dp
        isTableCell -> 1.dp
        isEditMode -> 1.dp
        else -> 0.dp
    }

    val borderColor = when {
        shape.isBackgroundShape -> Color.Transparent
        isSelected -> MaterialTheme.colorScheme.primary
        shapeBorder != null && shapeBorder.strokeColorHex != null -> safeParseColor(shapeBorder.strokeColorHex, MaterialTheme.colorScheme.primary)
        isTableCell -> MaterialTheme.colorScheme.outlineVariant
        isEditMode && isTitle -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        isEditMode -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
        else -> Color.Transparent
    }

    val defaultFontSizePt = if (isTitle) 24f else (if (isEllipseBadge) 12f else 18f)
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
    val paraLayouts = remember(shape, fontScale) {
        shape.paragraphs.mapIndexed { i, paragraph ->
            val textAlign = when {
                isEllipseBadge || isTitle -> TextAlign.Center
                paragraph.alignment == "CENTER" -> TextAlign.Center
                paragraph.alignment == "RIGHT" -> TextAlign.Right
                paragraph.alignment == "JUSTIFY" -> TextAlign.Justify
                else -> TextAlign.Start
            }
            val maxFontSizeSp = paragraph.runs.maxOfOrNull { run ->
                val pt = if (run.fontSizePt > 0) run.fontSizePt else defaultFontSizePt
                (pt * fontScale).coerceAtLeast(4f)
            } ?: (defaultFontSizePt * fontScale).coerceAtLeast(4f)

            val effLineMul = max(paragraph.lineSpacingMul, minLineMul)
            val lineHeightSp = maxFontSizeSp * effLineMul
            val markerText = when {
                isEllipseBadge || isTitle -> ""
                paragraph.numberingType != null -> formatNumberedMarker(
                    paragraph.numberingType,
                    numberingIndex[i] ?: 1
                )
                paragraph.hasBullet || paragraph.bulletLevel > 0 -> "${paragraph.bulletChar} "
                else -> ""
            }
            val indentSp = if (isEllipseBadge) 0f else (paragraph.bulletLevel * 10f * (fontScale / 0.375f).coerceIn(0.6f, 1.2f))
            ParagraphLayout(
                paragraph = paragraph,
                textAlign = textAlign,
                maxFontSizeSp = maxFontSizeSp,
                lineHeightSp = lineHeightSp,
                spaceBeforeSp = if (isEllipseBadge) 0f else (paragraph.spaceBeforePt * fontScale),
                spaceAfterSp = if (isEllipseBadge) 0f else (paragraph.spaceAfterPt * fontScale),
                markerText = markerText,
                indentSp = indentSp
            )
        }
    }

    // Build the annotated string for a paragraph at a given scale.
    fun buildAnnotated(pl: ParagraphLayout, scale: Float): AnnotatedString = buildAnnotatedString {
        pl.paragraph.runs.forEachIndexed { rIdx, run ->
            var text = run.text
            if (rIdx == 0 && pl.markerText.isNotBlank()) {
                text = text.replace(Regex("""^[*•▪\-–—]\s*"""), "")
            }
            val pt = if (run.fontSizePt > 0) run.fontSizePt else defaultFontSizePt
            val sizeSp = (pt * fontScale * scale).coerceAtLeast(4f)
            val runColor = run.textColorHex?.let {
                try { Color(android.graphics.Color.parseColor(it)) } catch (e: Exception) { null }
            } ?: defaultColor
            val runFontFamily = resolveFontFamily(run.fontFamily)
            withStyle(
                SpanStyle(
                    color = runColor,
                    fontSize = sizeSp.sp,
                    fontFamily = runFontFamily,
                    fontWeight = if (run.isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (run.isItalic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = if (run.isUnderline) TextDecoration.Underline else TextDecoration.None
                )
            ) {
                append(text)
            }
        }
    }

    // Measure total content height (all paragraphs + spacing) at a given scale.
    fun measureTotalHeight(scale: Float): Float {
        var total = 0f
        paraLayouts.forEach { pl ->
            val markerW = if (pl.markerText.isNotBlank()) with(density) { (pl.indentSp * scale + 14f).dp.toPx() } else 0f
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
            total += with(density) { (pl.spaceBeforeSp * scale).dp.toPx() + (pl.spaceAfterSp * scale).dp.toPx() }
        }
        return total
    }

    // Determine shrink scale: binary search to guarantee fit within available height ONLY if normAutofit is set.
    val shrinkScale = remember(shape, contentWidthPx, contentHeightPx(shapeHeightPx, insetTopPx, insetBottomPx, isTitle)) {
        if (shape.autoFit != AutoFitMode.NORM_AUTOFIT) {
            1.0f
        } else {
            val availableHeightPx = contentHeightPx(shapeHeightPx, insetTopPx, insetBottomPx, isTitle)
            val baked = shape.fontScale?.let { (it / 100000f).coerceIn(0.6f, 1f) } ?: 1f
            if (availableHeightPx <= 0f) {
                baked
            } else {
                val minScale = 0.65f
                if (measureTotalHeight(baked) <= availableHeightPx) {
                    baked
                } else {
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
    }

    // For non-autofit shapes, allow the box to grow to the measured wrapped content height
    val finalHeightDp = with(density) {
        val baseHeightPx = (shape.shapeHeight * slideH).dp.toPx()
        val extraPx = if (shape.autoFit != AutoFitMode.NORM_AUTOFIT) {
            val scale = shape.fontScale?.let { (it / 100000f).coerceIn(0.6f, 1f) } ?: 1f
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
            .then(if (shape.rotationDegrees != 0f) Modifier.rotate(shape.rotationDegrees) else Modifier)
            .clip(shapeShape)
            .clickable(enabled = isEditMode && !shape.isBackgroundShape, onClick = onClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else if (shapeBgColor != Color.Transparent) shapeBgColor
                else if (isEditMode && !shape.isBackgroundShape) {
                    if (isTitle) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    else MaterialTheme.colorScheme.secondary.copy(alpha = 0.06f)
                } else Color.Transparent
            )
            .border(
                width = borderWidth,
                color = borderColor,
                shape = shapeShape
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = if (isEllipseBadge) 1.dp else (shape.insets.left * slideW).coerceAtLeast(if (isTitle) 2f else 1f).dp,
                    top = if (isEllipseBadge) 1.dp else (shape.insets.top * slideH).coerceAtLeast(1f).dp,
                    end = if (isEllipseBadge) 1.dp else (shape.insets.right * slideW).coerceAtLeast(if (isTitle) 2f else 1f).dp,
                    bottom = if (isEllipseBadge) 1.dp else (shape.insets.bottom * slideH).coerceAtLeast(1f).dp
                ),
            verticalArrangement = if (isEllipseBadge) Arrangement.Center else Arrangement.Top,
            horizontalAlignment = if (isEllipseBadge || isTitle) Alignment.CenterHorizontally else Alignment.Start
        ) {
            paraLayouts.forEach { pl ->
                val textAlign = pl.textAlign
                val bulletLevel = pl.paragraph.bulletLevel
                val showMarker = pl.markerText.isNotBlank()
                val bulletSp = (pl.maxFontSizeSp * shrinkScale).coerceAtLeast(4f).sp

                val annotatedText = buildAnnotated(pl, shrinkScale)
                val leadSp = (pl.lineHeightSp * shrinkScale).coerceAtLeast(pl.maxFontSizeSp * shrinkScale * 1.05f).sp

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = (pl.spaceAfterSp * shrinkScale).coerceAtLeast(0f).dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = if (isEllipseBadge || isTitle) Arrangement.Center else Arrangement.Start
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
                        Spacer(modifier = Modifier.width(3.dp))
                    }

                    Text(
                        text = annotatedText,
                        textAlign = textAlign,
                        lineHeight = leadSp,
                        softWrap = true,
                        maxLines = if (isTitle) 100 else 1000,
                        overflow = if (isEllipseBadge || isTableCell) TextOverflow.Clip else TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = (pl.spaceBeforeSp * shrinkScale).coerceAtLeast(0f).dp)
                            .then(if (isTitle || isEllipseBadge) Modifier.fillMaxWidth() else Modifier.weight(1f, fill = false))
                    )
                }
            }
        }

        if (isSelected) {
            Box(modifier = Modifier.size(7.dp).align(Alignment.TopStart).background(MaterialTheme.colorScheme.primary, CircleShape))
            Box(modifier = Modifier.size(7.dp).align(Alignment.TopEnd).background(MaterialTheme.colorScheme.primary, CircleShape))
            Box(modifier = Modifier.size(7.dp).align(Alignment.BottomStart).background(MaterialTheme.colorScheme.primary, CircleShape))
            Box(modifier = Modifier.size(7.dp).align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.primary, CircleShape))
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

@Composable
private fun RibbonActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    isPrimary: Boolean = false,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                isPrimary -> MaterialTheme.colorScheme.primaryContainer
                isDestructive -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                isPrimary -> MaterialTheme.colorScheme.onPrimaryContainer
                isDestructive -> MaterialTheme.colorScheme.onErrorContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        ),
        modifier = Modifier
            .widthIn(min = 84.dp)
            .height(58.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(18.dp),
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    isDestructive -> MaterialTheme.colorScheme.error
                    isPrimary -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}



