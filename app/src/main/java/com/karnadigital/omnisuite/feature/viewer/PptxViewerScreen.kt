package com.karnadigital.omnisuite.feature.viewer

import android.content.Context
import android.content.Intent
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
import com.karnadigital.omnisuite.core.util.ZoomableBox

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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import coil.compose.AsyncImage

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
 * Slide-deck Presentation Viewer (PPTX) mobile screen engine.
 * Renders slides in a distraction-free swipeable HorizontalPager.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PptxViewerScreen(
    fileUri: String,
    onBack: () -> Unit,
    viewModel: PptxViewerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
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
                    val cachedFile = com.karnadigital.omnisuite.core.util.UriCacheUtils.cacheUriToFile(context, it)
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
            if (state is PptxLoadState.Success) {
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
                        val dynamicMimeType = if (fileUri.endsWith(".ppt", ignoreCase = true)) {
                            "application/vnd.ms-powerpoint"
                        } else {
                            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                        }

                        PptxActionColumnButton(icon = Icons.Default.OpenInNew, title = "Open in...") {
                            try {
                                val file = File(fileUri)
                                val fileUriProvider = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(fileUriProvider, dynamicMimeType)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(openIntent, "Open PowerPoint In"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }

                        PptxActionColumnButton(icon = Icons.Default.Print, title = "Print") {
                            coroutineScope.launch {
                                val tempPdfFile = File(context.cacheDir, "temp_print_${System.currentTimeMillis()}.pdf")
                                try {
                                    withContext(Dispatchers.IO) {
                                        com.karnadigital.omnisuite.core.engine.document.OfficeConverter.convertPptxToPdf(context, File(fileUri), tempPdfFile, "image")
                                    }
                                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
                                    val jobName = "OmniSuite Presentation Print"
                                    printManager.print(
                                        jobName,
                                        PptxPrintDocumentAdapter(context, tempPdfFile),
                                        null
                                    )
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Print failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }

                        PptxActionColumnButton(icon = Icons.Default.Share, title = "Share") {
                            try {
                                val file = File(fileUri)
                                val fileUriProvider = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = dynamicMimeType
                                    putExtra(Intent.EXTRA_STREAM, fileUriProvider)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Presentation"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }

                        var showQuickToolsMenu by remember { mutableStateOf(false) }
                        Box {
                            PptxActionColumnButton(icon = Icons.Default.Build, title = "Quick Tools") {
                                showQuickToolsMenu = true
                            }
                            DropdownMenu(
                                expanded = showQuickToolsMenu,
                                onDismissRequest = { showQuickToolsMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("📕 Convert to PDF (Image Mode)") },
                                    onClick = {
                                        showQuickToolsMenu = false
                                        coroutineScope.launch {
                                            val tempPdfFile = File(context.cacheDir, "temp_conv_${System.currentTimeMillis()}.pdf")
                                            try {
                                                withContext(Dispatchers.IO) {
                                                    com.karnadigital.omnisuite.core.engine.document.OfficeConverter.convertPptxToPdf(context, File(fileUri), tempPdfFile, "image")
                                                }
                                                // Save to public Documents/OmniSuite/
                                                val savedUri = com.karnadigital.omnisuite.core.util.FileOutputManager.saveToDefault(
                                                    context = context,
                                                    bytes = tempPdfFile.readBytes(),
                                                    filename = File(fileUri).name.substringBeforeLast(".") + "_converted.pdf",
                                                    mimeType = "application/pdf",
                                                    subfolder = ""
                                                )
                                                if (savedUri != null) {
                                                    Toast.makeText(context, "Presentation saved under Documents/OmniSuite!", Toast.LENGTH_LONG).show()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            } finally {
                                                if (tempPdfFile.exists()) tempPdfFile.delete()
                                            }
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("📄 Convert to PDF (Text Reflow)") },
                                    onClick = {
                                        showQuickToolsMenu = false
                                        coroutineScope.launch {
                                            val tempPdfFile = File(context.cacheDir, "temp_conv_${System.currentTimeMillis()}.pdf")
                                            try {
                                                withContext(Dispatchers.IO) {
                                                    com.karnadigital.omnisuite.core.engine.document.OfficeConverter.convertPptxToPdf(context, File(fileUri), tempPdfFile, "text")
                                                }
                                                // Save to public Documents/OmniSuite/
                                                val savedUri = com.karnadigital.omnisuite.core.util.FileOutputManager.saveToDefault(
                                                    context = context,
                                                    bytes = tempPdfFile.readBytes(),
                                                    filename = File(fileUri).name.substringBeforeLast(".") + "_reflowed.pdf",
                                                    mimeType = "application/pdf",
                                                    subfolder = ""
                                                )
                                                if (savedUri != null) {
                                                    Toast.makeText(context, "Presentation saved under Documents/OmniSuite!", Toast.LENGTH_LONG).show()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            } finally {
                                                if (tempPdfFile.exists()) tempPdfFile.delete()
                                            }
                                        }
                                    }
                                )
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
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
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
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Swipable Slides horizontal pager with premium scale/fade transitions
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 24.dp),
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

                            Spacer(modifier = Modifier.height(12.dp))

                            // Slide counter
                            Text(
                                text = "Slide ${pagerState.currentPage + 1} of ${presentation.slides.size}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Scrollable horizontal slide thumbnail strip drawer
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
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

                            Spacer(modifier = Modifier.height(12.dp))

                            // Render slide comment note at the bottom
                            val currentSlide = presentation.slides[pagerState.currentPage]
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Speaker Notes",
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Speaker Notes",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val displayNotes = currentSlide.speakerNotes
                                    if (!displayNotes.isNullOrBlank()) {
                                        Text(
                                            text = displayNotes,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Text(
                                            text = "No slide notes recorded.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
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
            .aspectRatio(16f / 9f)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val scaleFactor = (maxWidth.value / 720f).coerceIn(0.1f, 2f)

            // 1. Title Block
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
                        .fillMaxWidth(title.shapeWidth.coerceIn(0.1f, 1f))
                        .fillMaxHeight(title.shapeHeight.coerceIn(0.05f, 1f))
                        .offset(
                            x = (title.shapeLeft * maxWidth.value).dp,
                            y = (title.shapeTop * maxHeight.value).dp
                        )
                        .clickable(enabled = isEditMode) {
                            onTextBlockClick(title, true, -1)
                        }
                        .background(if (isEditMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                        .border(
                            width = if (isEditMode) 1.dp else 0.dp,
                            color = if (isEditMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(if (isEditMode) 4.dp else 0.dp)
                ) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            text = title.text,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = if (title.isBold) FontWeight.Bold else FontWeight.Normal,
                                fontStyle = if (title.isItalic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                                textDecoration = if (title.isUnderline) androidx.compose.ui.text.style.TextDecoration.Underline else androidx.compose.ui.text.style.TextDecoration.None,
                                fontSize = (title.fontSizePt * scaleFactor).sp,
                                lineHeight = (title.fontSizePt * scaleFactor * 1.25f).sp,
                                textAlign = titleAlign
                            ),
                            color = titleColor,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 2. Body Text Blocks
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
                        .fillMaxWidth(block.shapeWidth.coerceIn(0.1f, 1f))
                        .fillMaxHeight(block.shapeHeight.coerceIn(0.05f, 1f))
                        .offset(
                            x = (block.shapeLeft * maxWidth.value).dp,
                            y = (block.shapeTop * maxHeight.value).dp
                        )
                        .clickable(enabled = isEditMode) {
                            onTextBlockClick(block, false, idx)
                        }
                        .background(if (isEditMode) MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f) else Color.Transparent)
                        .border(
                            width = if (isEditMode) 1.dp else 0.dp,
                            color = if (isEditMode) MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f) else Color.Transparent,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(if (isEditMode) 4.dp else 0.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (block.bulletLevel > 0) {
                                Spacer(modifier = Modifier.width((block.bulletLevel * 8 * scaleFactor).dp))
                                Text(
                                    text = "• ",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = (block.fontSizePt * scaleFactor).sp
                                    ),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Text(
                                text = block.text,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (block.isBold) FontWeight.Bold else FontWeight.Normal,
                                    fontStyle = if (block.isItalic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                                    textDecoration = if (block.isUnderline) androidx.compose.ui.text.style.TextDecoration.Underline else androidx.compose.ui.text.style.TextDecoration.None,
                                    fontSize = (block.fontSizePt * scaleFactor).sp,
                                    lineHeight = (block.fontSizePt * scaleFactor * 1.25f).sp,
                                    textAlign = textAlign
                                ),
                                color = blockColor,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                    }
                }
            }

            // 3. Images
            slide.images.forEach { img ->
                AsyncImage(
                    model = File(img.filePath),
                    contentDescription = "Slide Image",
                    modifier = Modifier
                        .fillMaxWidth(img.width.coerceIn(0.05f, 1f))
                        .fillMaxHeight(img.height.coerceIn(0.05f, 1f))
                        .offset(
                            x = (img.left * maxWidth.value).dp,
                            y = (img.top * maxHeight.value).dp
                        )
                        .clip(RoundedCornerShape(4.dp))
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
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

private @Composable
fun PptxActionColumnButton(
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
        var input: java.io.InputStream? = null
        var output: java.io.OutputStream? = null
        try {
            input = java.io.FileInputStream(file)
            output = java.io.FileOutputStream(destination?.fileDescriptor)
            val buffer = ByteArray(1024)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } >= 0) {
                output.write(buffer, 0, bytesRead)
            }
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback?.onWriteFailed(e.localizedMessage)
        } finally {
            try { input?.close() } catch(e: Exception) {}
            try { output?.close() } catch(e: Exception) {}
        }
    }
}
