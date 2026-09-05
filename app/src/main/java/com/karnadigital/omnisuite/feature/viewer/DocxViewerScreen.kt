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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import java.io.File
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.luminance
import androidx.compose.material.icons.filled.Info
import com.karnadigital.omnisuite.core.util.ZoomableBox
import com.karnadigital.omnisuite.di.coreEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import android.graphics.BitmapFactory
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import coil.compose.AsyncImage
import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Reflowable Word Document (DOCX) mobile viewer and interactive editor engine.
 * Renders paragraphs as rich e-book typography layouts or editable fields.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocxViewerScreen(
    fileUri: String,
    onBack: () -> Unit,
    onToolAction: (ViewerTool) -> Unit = {},
    viewModel: DocxViewerViewModel = hiltViewModel()
) {
    LaunchedEffect(fileUri) {
        viewModel.loadWordFile(fileUri)
    }

    val state by viewModel.loadState.collectAsState()
    var isEditMode by remember { mutableStateOf(false) }
    var showAppendDialog by remember { mutableStateOf(false) }
    var isPrintLayout by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }
    
    val context = LocalContext.current
    val uriCacheUtils = coreEntryPoint(context).uriCacheUtils()
    val officeConverter = coreEntryPoint(context).officeConverter()
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    var isExporting by remember { mutableStateOf(false) }

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val currentMatchIndex by viewModel.currentMatchIndex.collectAsState()

    var searchExpanded by remember { mutableStateOf(false) }
    var activeElementIndex by remember { mutableIntStateOf(0) }
    var showInsertTableDialog by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var showFontSizeMenu by remember { mutableStateOf(false) }
    var showColorMenu by remember { mutableStateOf(false) }
    var showStyleMenu by remember { mutableStateOf(false) }
    var showInsertMenu by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                coroutineScope.launch {
                    val cachedFile = uriCacheUtils.cacheUriToFile(it)
                    if (cachedFile != null) {
                        viewModel.insertImage(activeElementIndex, cachedFile.absolutePath)
                        Toast.makeText(context, "Image inserted successfully!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    // Scroll to active search match paragraph index
    LaunchedEffect(currentMatchIndex) {
        if (currentMatchIndex >= 0 && currentMatchIndex < searchResults.size) {
            val match = searchResults[currentMatchIndex]
            lazyListState.animateScrollToItem(match.pageIndex)
        }
    }

    val exportPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
        onResult = { uri ->
            uri?.let {
                isExporting = true
                viewModel.exportToPdf(
                    outputUri = it,
                    onSuccess = {
                        isExporting = false
                        Toast.makeText(context, "Document exported to PDF successfully!", Toast.LENGTH_LONG).show()
                    },
                    onFailure = { error ->
                        isExporting = false
                        Toast.makeText(context, "Export failed: $error", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    )

    LaunchedEffect(Unit) {
        viewModel.saveStatus.collect { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                            placeholder = { Text("Search text in Word...") },
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
                    if (fileUri.endsWith(".doc", ignoreCase = true)) {
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
                                    text = "View Mode (Read-Only .doc format). Convert to .docx to enable editing.",
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
                                    is DocxLoadState.Success -> s.fileName
                                    else -> "Document Viewer"
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
                            if (state is DocxLoadState.Success) {
                                var showMenu by remember { mutableStateOf(false) }

                                IconButton(onClick = { searchExpanded = true }) {
                                    Icon(Icons.Default.Search, contentDescription = "Search text")
                                }

                                if (isEditMode) {
                                    val canUndo by viewModel.canUndo.collectAsState()
                                    val canRedo by viewModel.canRedo.collectAsState()

                                    IconButton(
                                        onClick = { viewModel.undo() },
                                        enabled = canUndo
                                    ) {
                                        Icon(
                                            Icons.Default.Undo,
                                            contentDescription = "Undo",
                                            tint = if (canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.redo() },
                                        enabled = canRedo
                                    ) {
                                        Icon(
                                            Icons.Default.Redo,
                                            contentDescription = "Redo",
                                            tint = if (canRedo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        )
                                    }
                                    IconButton(onClick = {
                                        viewModel.commitChanges()
                                        isEditMode = false
                                    }) {
                                        Icon(Icons.Default.Check, contentDescription = "Save changes", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                IconButton(onClick = { isEditMode = !isEditMode }) {
                                    Icon(
                                        imageVector = if (isEditMode) Icons.Default.Close else Icons.Default.Edit,
                                        contentDescription = if (isEditMode) "Exit Edit Mode" else "Enter Edit Mode",
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
                                        text = { Text("Export to PDF") },
                                        onClick = {
                                            showMenu = false
                                            val currentSuccess = state as DocxLoadState.Success
                                            val defaultName = currentSuccess.fileName.substringBeforeLast(".") + ".pdf"
                                            exportPdfLauncher.launch(defaultName)
                                        },
                                        leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Convert to TXT") },
                                        onClick = {
                                            showMenu = false
                                            onToolAction(ViewerTool.Navigate(com.karnadigital.omnisuite.ui.navigation.Screen.DocxToTxt.createRoute(fileUri)))
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
        floatingActionButton = {},
        bottomBar = {
            if (state is DocxLoadState.Success) {
                val currentDoc = (state as DocxLoadState.Success).document
                if (isEditMode) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                    ) {
                        val canUndo by viewModel.canUndo.collectAsState()
                        val canRedo by viewModel.canRedo.collectAsState()

                        val activeElement = currentDoc.elements.getOrNull(activeElementIndex)
                        val activePara = (activeElement as? DocxBodyElement.Para)?.paragraph
                        val firstRun = activePara?.runs?.firstOrNull()

                        val currentIsBold = activePara?.isHeading == true || (firstRun?.isBold == true)
                        val currentIsItalic = firstRun?.isItalic == true
                        val currentIsUnderline = firstRun?.isUnderline == true
                        val currentIsStrike = firstRun?.isStrike == true
                        val currentAlignment = activePara?.alignment ?: "LEFT"
                        val currentBulletType = activePara?.bulletType
                        val currentHeadingLevel = activePara?.headingLevel ?: 0
                        val currentFontSize = firstRun?.fontSizePt ?: (if (activePara?.isHeading == true) 16f else 12f)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Undo & Redo
                            IconButton(
                                onClick = { viewModel.undo() },
                                enabled = canUndo,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Undo,
                                    contentDescription = "Undo",
                                    tint = if (canUndo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.redo() },
                                enabled = canRedo,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Redo,
                                    contentDescription = "Redo",
                                    tint = if (canRedo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }

                            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 2.dp))

                            // B, I, U, S styling toggles
                            FormatRibbonToggleButton(
                                label = "B",
                                isSelected = currentIsBold,
                                fontWeight = FontWeight.Bold,
                                onClick = {
                                    viewModel.applyParagraphFormatting(activeElementIndex, isBold = !currentIsBold)
                                }
                            )
                            FormatRibbonToggleButton(
                                label = "I",
                                isSelected = currentIsItalic,
                                fontStyle = FontStyle.Italic,
                                onClick = {
                                    viewModel.applyParagraphFormatting(activeElementIndex, isItalic = !currentIsItalic)
                                }
                            )
                            FormatRibbonToggleButton(
                                label = "U",
                                isSelected = currentIsUnderline,
                                textDecoration = TextDecoration.Underline,
                                onClick = {
                                    viewModel.applyParagraphFormatting(activeElementIndex, isUnderline = !currentIsUnderline)
                                }
                            )
                            FormatRibbonToggleButton(
                                label = "S",
                                isSelected = currentIsStrike,
                                textDecoration = TextDecoration.LineThrough,
                                onClick = {
                                    viewModel.applyParagraphFormatting(activeElementIndex, isStrike = !currentIsStrike)
                                }
                            )

                            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 2.dp))

                            // Font Size Stepper
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        val newSize = (currentFontSize - 1f).coerceAtLeast(8f)
                                        viewModel.applyParagraphFormatting(activeElementIndex, fontSizePt = newSize)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease Font Size", modifier = Modifier.size(16.dp))
                                }
                                Box {
                                    Text(
                                        text = "${currentFontSize.toInt()} pt",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable { showFontSizeMenu = true }
                                            .padding(horizontal = 6.dp)
                                    )
                                    DropdownMenu(
                                        expanded = showFontSizeMenu,
                                        onDismissRequest = { showFontSizeMenu = false }
                                    ) {
                                        listOf(9f, 10f, 11f, 12f, 14f, 16f, 18f, 20f, 24f, 28f, 32f).forEach { size ->
                                            DropdownMenuItem(
                                                text = { Text("${size.toInt()} pt", fontWeight = if (size == currentFontSize) FontWeight.Bold else FontWeight.Normal) },
                                                onClick = {
                                                    viewModel.applyParagraphFormatting(activeElementIndex, fontSizePt = size)
                                                    showFontSizeMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        val newSize = (currentFontSize + 1f).coerceAtMost(72f)
                                        viewModel.applyParagraphFormatting(activeElementIndex, fontSizePt = newSize)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase Font Size", modifier = Modifier.size(16.dp))
                                }
                            }

                            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 2.dp))

                            // Alignment Controls
                            IconButton(
                                onClick = { viewModel.applyParagraphFormatting(activeElementIndex, alignment = "LEFT") },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.FormatAlignLeft,
                                    contentDescription = "Align Left",
                                    tint = if (currentAlignment == "LEFT") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = { viewModel.applyParagraphFormatting(activeElementIndex, alignment = "CENTER") },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.FormatAlignCenter,
                                    contentDescription = "Align Center",
                                    tint = if (currentAlignment == "CENTER") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = { viewModel.applyParagraphFormatting(activeElementIndex, alignment = "RIGHT") },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.FormatAlignRight,
                                    contentDescription = "Align Right",
                                    tint = if (currentAlignment == "RIGHT") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = { viewModel.applyParagraphFormatting(activeElementIndex, alignment = "JUSTIFY") },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.FormatAlignJustify,
                                    contentDescription = "Justify",
                                    tint = if (currentAlignment == "JUSTIFY") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 2.dp))

                            // Bullet & Number Lists
                            IconButton(
                                onClick = {
                                    val next = if (currentBulletType == "bullet") "NONE" else "bullet"
                                    viewModel.applyParagraphFormatting(activeElementIndex, bulletType = next)
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.FormatListBulleted,
                                    contentDescription = "Bullet List",
                                    tint = if (currentBulletType == "bullet") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = {
                                    val next = if (currentBulletType == "number") "NONE" else "number"
                                    viewModel.applyParagraphFormatting(activeElementIndex, bulletType = next)
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.FormatListNumbered,
                                    contentDescription = "Numbered List",
                                    tint = if (currentBulletType == "number") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 2.dp))

                            // Style / Heading Menu
                            Box {
                                OutlinedButton(
                                    onClick = { showStyleMenu = true },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = when (currentHeadingLevel) {
                                            1 -> "Heading 1"
                                            2 -> "Heading 2"
                                            3 -> "Heading 3"
                                            else -> "Normal"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                DropdownMenu(
                                    expanded = showStyleMenu,
                                    onDismissRequest = { showStyleMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Normal Text") },
                                        onClick = {
                                            viewModel.applyParagraphFormatting(activeElementIndex, headingLevel = 0, fontSizePt = 12f)
                                            showStyleMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Heading 1", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                                        onClick = {
                                            viewModel.applyParagraphFormatting(activeElementIndex, headingLevel = 1, fontSizePt = 20f, isBold = true)
                                            showStyleMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Heading 2", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                                        onClick = {
                                            viewModel.applyParagraphFormatting(activeElementIndex, headingLevel = 2, fontSizePt = 16f, isBold = true)
                                            showStyleMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Heading 3", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                                        onClick = {
                                            viewModel.applyParagraphFormatting(activeElementIndex, headingLevel = 3, fontSizePt = 14f, isBold = true)
                                            showStyleMenu = false
                                        }
                                    )
                                }
                            }

                            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 2.dp))

                            // Text Color Swatches
                            Box {
                                IconButton(onClick = { showColorMenu = true }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.FormatColorText, contentDescription = "Text Color")
                                }
                                DropdownMenu(
                                    expanded = showColorMenu,
                                    onDismissRequest = { showColorMenu = false }
                                ) {
                                    listOf(
                                        "Default" to "CLEAR",
                                        "Black" to "#000000",
                                        "Red" to "#EF4444",
                                        "Blue" to "#3B82F6",
                                        "Green" to "#10B981",
                                        "Orange" to "#F59E0B",
                                        "Purple" to "#8B5CF6"
                                    ).forEach { (name, hex) ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(16.dp)
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(if (hex == "CLEAR") Color.Gray else Color(android.graphics.Color.parseColor(hex)))
                                                    )
                                                    Text(name)
                                                }
                                            },
                                            onClick = {
                                                viewModel.applyParagraphFormatting(activeElementIndex, colorHex = hex)
                                                showColorMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 2.dp))

                            // Insert Menu (+)
                            Box {
                                IconButton(onClick = { showInsertMenu = true }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.AddCircleOutline, contentDescription = "Insert Elements", tint = MaterialTheme.colorScheme.primary)
                                }
                                DropdownMenu(
                                    expanded = showInsertMenu,
                                    onDismissRequest = { showInsertMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Paragraph Below") },
                                        leadingIcon = { Icon(Icons.Default.FormatAlignLeft, contentDescription = null) },
                                        onClick = {
                                            val newIdx = viewModel.insertParagraph(activeElementIndex, "", after = true)
                                            if (newIdx != -1) activeElementIndex = newIdx
                                            showInsertMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Paragraph Above") },
                                        leadingIcon = { Icon(Icons.Default.FormatAlignLeft, contentDescription = null) },
                                        onClick = {
                                            val newIdx = viewModel.insertParagraph(activeElementIndex, "", after = false)
                                            if (newIdx != -1) activeElementIndex = newIdx
                                            showInsertMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Insert Image") },
                                        leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                                        onClick = {
                                            imagePickerLauncher.launch("image/*")
                                            showInsertMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Insert Table") },
                                        leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null) },
                                        onClick = {
                                            showInsertTableDialog = true
                                            showInsertMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Insert Link") },
                                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                                        onClick = {
                                            showLinkDialog = true
                                            showInsertMenu = false
                                        }
                                    )
                                }
                            }

                            // Delete Paragraph
                            IconButton(
                                onClick = {
                                    val next = viewModel.deleteParagraph(activeElementIndex)
                                    activeElementIndex = next
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Paragraph", tint = MaterialTheme.colorScheme.error)
                            }

                            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 2.dp))

                            // Save Changes Button
                            Button(
                                onClick = {
                                    viewModel.commitChanges()
                                    isEditMode = false
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // Normal Viewer Dock Bar
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
                                icon = if (isPrintLayout) Icons.Default.TextSnippet else Icons.Default.PictureAsPdf,
                                title = if (isPrintLayout) "Reflow" else "Print Layout"
                            ) {
                                isPrintLayout = !isPrintLayout
                            }

                            ViewerActionColumnButton(
                                icon = Icons.Default.Edit,
                                title = "Edit"
                            ) {
                                isEditMode = true
                            }

                            ViewerActionColumnButton(
                                icon = Icons.Default.Search,
                                title = "Search"
                            ) {
                                searchExpanded = true
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
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (val currentState = state) {
                is DocxLoadState.Loading -> {
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
                            text = "Reflowing document pages...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }
                }
                is DocxLoadState.Success -> {
                    val document = currentState.document
                    if (document.elements.isEmpty() && currentState.docxBase64 == null) {
                        EmptyDocumentState()
                    } else if (!isEditMode && currentState.docxBase64 != null) {
                        // High-fidelity WebView DOCX renderer using docx-preview.js (Print Layout / Reflow)
                        DocxWebView(
                            docxBase64 = currentState.docxBase64,
                            isPrintLayout = isPrintLayout,
                            searchQuery = searchQuery,
                            currentMatchIndex = currentMatchIndex,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Document Sheet Container (when in Edit Mode or fallback for legacy format)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = if (isEditMode) 8.dp else 0.dp, vertical = if (isEditMode) 8.dp else 0.dp),
                                shape = if (isEditMode) RoundedCornerShape(8.dp) else RoundedCornerShape(0.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = if (isEditMode) 2.dp else 0.dp)
                            ) {
                                LazyColumn(
                                    state = lazyListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp)
                                ) {
                                    itemsIndexed(
                                        items = document.elements,
                                        key = { _, element -> element.elementId }
                                    ) { index, element ->
                                        when (element) {
                                            is DocxBodyElement.Para -> {
                                                if (isEditMode) {
                                                    DocxInPlaceParagraphEditor(
                                                        index = index,
                                                        paragraph = element.paragraph,
                                                        isActive = activeElementIndex == index,
                                                        onFocus = { activeElementIndex = index },
                                                        onTextChange = { newText ->
                                                            viewModel.updateParagraphText(index, newText)
                                                        },
                                                        onEnterPressed = { remainingText ->
                                                            val newIdx = viewModel.insertParagraph(index, remainingText, after = true)
                                                            if (newIdx != -1) activeElementIndex = newIdx
                                                        },
                                                        onDeleteImage = {
                                                            val filtered = element.paragraph.runs.filter { it.imageUrl == null }
                                                            viewModel.updateParagraphText(index, filtered.joinToString("") { it.text })
                                                        }
                                                    )
                                                } else {
                                                    val isHighlighted = searchResults.getOrNull(currentMatchIndex)?.pageIndex == index
                                                    DocxParagraphItem(
                                                        paragraph = element.paragraph,
                                                        isHighlighted = isHighlighted,
                                                        searchQuery = searchQuery,
                                                        isPrintLayout = false
                                                    )
                                                }
                                            }
                                            is DocxBodyElement.Table -> {
                                                if (isEditMode) {
                                                    DocxEditableTableItem(
                                                        tableIndex = index,
                                                        table = element,
                                                        isEditMode = true,
                                                        onCellTextChange = { rIdx, cIdx, text ->
                                                            viewModel.updateTableCellText(index, rIdx, cIdx, text)
                                                        },
                                                        onAddRow = {
                                                            viewModel.insertTableRow(index, element.rows.size - 1)
                                                        },
                                                        onDeleteRow = {
                                                            viewModel.deleteTableRow(index, element.rows.size - 1)
                                                        },
                                                        onDeleteTable = {
                                                            viewModel.deleteBodyElement(index)
                                                        }
                                                    )
                                                } else {
                                                    DocxTableItem(
                                                        table = element,
                                                        searchQuery = searchQuery,
                                                        isPrintLayout = false
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
                is DocxLoadState.Error -> {
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
                            text = "Word Read Error",
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

    if (showInsertTableDialog) {
        var rowsText by remember { mutableStateOf("2") }
        var colsText by remember { mutableStateOf("2") }
        AlertDialog(
            onDismissRequest = { showInsertTableDialog = false },
            title = { Text("Insert Table", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = rowsText,
                        onValueChange = { rowsText = it.filter { c -> c.isDigit() } },
                        label = { Text("Number of Rows") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = colsText,
                        onValueChange = { colsText = it.filter { c -> c.isDigit() } },
                        label = { Text("Number of Columns") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val r = rowsText.toIntOrNull()?.coerceIn(1, 20) ?: 2
                    val c = colsText.toIntOrNull()?.coerceIn(1, 10) ?: 2
                    viewModel.insertTable(activeElementIndex, r, c)
                    showInsertTableDialog = false
                }) {
                    Text("Insert")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInsertTableDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showLinkDialog) {
        var linkText by remember { mutableStateOf("") }
        var linkUrl by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showLinkDialog = false },
            title = { Text("Insert Link", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = linkText,
                        onValueChange = { linkText = it },
                        label = { Text("Link Text") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = linkUrl,
                        onValueChange = { linkUrl = it },
                        label = { Text("URL (e.g. https://...)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (linkText.isNotBlank()) {
                        val formattedLink = if (linkUrl.isNotBlank()) "$linkText ($linkUrl)" else linkText
                        viewModel.insertParagraph(activeElementIndex, formattedLink, after = true)
                    }
                    showLinkDialog = false
                }) {
                    Text("Insert")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLinkDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (isExporting) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Exporting PDF", fontWeight = FontWeight.Bold) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text("Converting document offline...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        )
    }
}

private fun parseHexColor(hex: String?): Color? {
    if (hex == null) return null
    val cleanHex = hex.trim().replace("#", "")
    return try {
        if (cleanHex.length == 6) {
            Color(android.graphics.Color.parseColor("#$cleanHex"))
        } else if (cleanHex.length == 8) {
            Color(android.graphics.Color.parseColor("#$cleanHex"))
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

private fun mapFontFamily(name: String?): FontFamily {
    if (name == null) return FontFamily.Default
    val lower = name.lowercase().trim()
    return when {
        lower.contains("times") || lower.contains("georgia") || lower.contains("serif") || lower.contains("cambria") -> FontFamily.Serif
        lower.contains("courier") || lower.contains("consolas") || lower.contains("monospace") || lower.contains("code") -> FontFamily.Monospace
        lower.contains("cursive") || lower.contains("comic") -> FontFamily.Cursive
        else -> FontFamily.SansSerif
    }
}

@Composable
fun FormatRibbonToggleButton(
    label: String,
    isSelected: Boolean,
    fontWeight: FontWeight = FontWeight.Normal,
    fontStyle: FontStyle = FontStyle.Normal,
    textDecoration: TextDecoration = TextDecoration.None,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                RoundedCornerShape(6.dp)
            )
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            textDecoration = textDecoration,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun DocxInPlaceParagraphEditor(
    index: Int,
    paragraph: DocxParagraph,
    isActive: Boolean,
    onFocus: () -> Unit,
    onTextChange: (String) -> Unit,
    onEnterPressed: (String) -> Unit,
    onDeleteImage: () -> Unit
) {
    val currentParagraphText = paragraph.runs.joinToString("") { it.text }
    var rawText by remember(paragraph.id, currentParagraphText) {
        mutableStateOf(currentParagraphText)
    }

    LaunchedEffect(currentParagraphText) {
        if (rawText != currentParagraphText) {
            rawText = currentParagraphText
        }
    }

    // Determine typography from paragraph properties or first run
    val firstRun = paragraph.runs.firstOrNull()
    val isBold = firstRun?.isBold == true || paragraph.isHeading
    val isItalic = firstRun?.isItalic == true
    val isUnderline = firstRun?.isUnderline == true
    val isStrike = firstRun?.isStrike == true
    val fontSize = (firstRun?.fontSizePt ?: if (paragraph.isHeading) 18f else 14f).sp
    val fontFamily = mapFontFamily(firstRun?.fontFamily)
    val parsedColor = parseHexColor(firstRun?.color) ?: MaterialTheme.colorScheme.onSurface

    val textAlign = when (paragraph.alignment) {
        "CENTER" -> TextAlign.Center
        "RIGHT" -> TextAlign.Right
        "JUSTIFY" -> TextAlign.Justify
        else -> TextAlign.Left
    }

    val textDecoration = when {
        isUnderline && isStrike -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
        isUnderline -> TextDecoration.Underline
        isStrike -> TextDecoration.LineThrough
        else -> TextDecoration.None
    }

    val bulletPrefix = when (paragraph.bulletType) {
        "bullet" -> "• "
        "number" -> "${index + 1}. "
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.04f) else Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .border(
                width = if (isActive) 1.dp else 0.dp,
                color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            if (bulletPrefix != null) {
                Text(
                    text = bulletPrefix,
                    fontSize = fontSize,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 4.dp, top = 2.dp)
                )
            }

            BasicTextField(
                value = rawText,
                onValueChange = { newText ->
                    if (newText.contains("\n")) {
                        val parts = newText.split("\n", limit = 2)
                        val beforeEnter = parts[0]
                        val afterEnter = if (parts.size > 1) parts[1] else ""
                        rawText = beforeEnter
                        onTextChange(beforeEnter)
                        onEnterPressed(afterEnter)
                    } else {
                        rawText = newText
                        onTextChange(newText)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            onFocus()
                        }
                    },
                textStyle = TextStyle(
                    fontFamily = fontFamily,
                    fontSize = fontSize,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = textDecoration,
                    textAlign = textAlign,
                    color = parsedColor
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    keyboardType = KeyboardType.Text
                ),
                singleLine = false
            )
        }

        // Display any embedded image with a quick-action delete button in edit mode
        paragraph.runs.forEach { run ->
            if (run.imageUrl != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 8.dp)
                ) {
                    AsyncImage(
                        model = run.imageUrl,
                        contentDescription = "Embedded Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                    IconButton(
                        onClick = onDeleteImage,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(28.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove Image",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DocxEditableTableItem(
    tableIndex: Int,
    table: DocxBodyElement.Table,
    isEditMode: Boolean,
    onCellTextChange: (rowIndex: Int, colIndex: Int, text: String) -> Unit,
    onAddRow: () -> Unit,
    onDeleteRow: () -> Unit,
    onDeleteTable: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (isEditMode) {
                // Table header actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Table (${table.rows.size} rows)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onAddRow, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "Add Row", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        if (table.rows.size > 1) {
                            IconButton(onClick = onDeleteRow, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Remove, contentDescription = "Delete Last Row", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        IconButton(onClick = onDeleteTable, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Table", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Render table grid
            table.rows.forEachIndexed { rIdx, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawLine(
                                color = Color.LightGray.copy(alpha = 0.5f),
                                start = Offset(0f, size.height),
                                end = Offset(size.width, size.height),
                                strokeWidth = 0.5.dp.toPx()
                            )
                        },
                    verticalAlignment = Alignment.Top
                ) {
                    row.cells.forEachIndexed { cIdx, cell ->
                        val currentText = cell.paragraphs.joinToString("\n") { it.runs.joinToString("") { r -> r.text } }
                        var cellText by remember(cell.id, currentText) {
                            mutableStateOf(currentText)
                        }

                        LaunchedEffect(currentText) {
                            if (cellText != currentText) {
                                cellText = currentText
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (cIdx < row.cells.size - 1)
                                        Modifier.drawBehind {
                                            drawLine(
                                                color = Color.LightGray.copy(alpha = 0.5f),
                                                start = Offset(size.width, 0f),
                                                end = Offset(size.width, size.height),
                                                strokeWidth = 0.5.dp.toPx()
                                            )
                                        }
                                    else Modifier
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            BasicTextField(
                                value = cellText,
                                onValueChange = { newText ->
                                    cellText = newText
                                    onCellTextChange(rIdx, cIdx, newText)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = TextStyle(
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (rIdx == 0) FontWeight.Bold else FontWeight.Normal
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                singleLine = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DocxTableItem(table: DocxBodyElement.Table, searchQuery: String, isPrintLayout: Boolean = false) {
    val tableScrollState = rememberScrollState()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        border = BorderStroke(0.5.dp, if (isPrintLayout) Color.LightGray else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = if (isPrintLayout) Color(0xFFFAFAFA) else MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(tableScrollState)
        ) {
            val maxCols = table.rows.maxOfOrNull { it.cells.size } ?: 1
            table.rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .then(if (maxCols > 3) Modifier.widthIn(min = (maxCols * 100).dp) else Modifier.fillMaxWidth())
                        .drawBehind {
                            drawLine(
                                color = Color.LightGray.copy(alpha = 0.4f),
                                start = Offset(0f, size.height),
                                end = Offset(size.width, size.height),
                                strokeWidth = 0.5.dp.toPx()
                            )
                        },
                    verticalAlignment = Alignment.Top
                ) {
                    row.cells.forEachIndexed { cellIdx, cell ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (cellIdx < row.cells.size - 1)
                                        Modifier.drawBehind {
                                            drawLine(
                                                color = Color.LightGray.copy(alpha = 0.4f),
                                                start = Offset(size.width, 0f),
                                                end = Offset(size.width, size.height),
                                                strokeWidth = 0.5.dp.toPx()
                                            )
                                        }
                                    else Modifier
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            cell.paragraphs.forEach { para ->
                                DocxParagraphItem(
                                    paragraph = para,
                                    isHighlighted = false,
                                    searchQuery = searchQuery,
                                    isPrintLayout = isPrintLayout
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildAnnotatedStringForRuns(
    runs: List<DocxRun>,
    searchQuery: String,
    isPrintLayout: Boolean
): AnnotatedString {
    return buildAnnotatedString {
        runs.forEach { run ->
            if (run.text.isBlank() && run.imageUrl == null) return@forEach
            val cleanText = run.text.replace("\t", "")
            if (cleanText.isEmpty()) return@forEach
            val start = length
            append(cleanText)
            val end = length

            val isLink = run.hyperlinkUrl != null
            val runColor: Color = when {
                isLink -> Color(0xFF1A73E8)
                run.color != null && run.color != "000000" && run.color != "auto" -> {
                    try { Color(android.graphics.Color.parseColor("#${run.color}")) }
                    catch (e: Exception) { if (isPrintLayout) Color(0xFF1F1F1F) else Color.Unspecified }
                }
                isPrintLayout -> Color(0xFF1F1F1F)
                else -> Color.Unspecified
            }

            val spanStyle = SpanStyle(
                fontWeight = if (run.isBold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (run.isItalic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = when {
                    isLink -> TextDecoration.Underline
                    run.isUnderline && run.isStrike -> TextDecoration.Underline + TextDecoration.LineThrough
                    run.isUnderline -> TextDecoration.Underline
                    run.isStrike -> TextDecoration.LineThrough
                    else -> TextDecoration.None
                },
                color = runColor,
                fontSize = if (run.fontSizePt != null && run.fontSizePt > 0) run.fontSizePt.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
                fontFamily = mapFontFamily(run.fontFamily)
            )
            addStyle(spanStyle, start, end)

            if (run.hyperlinkUrl != null) {
                addStringAnnotation("URL", run.hyperlinkUrl, start, end)
            }
        }

        if (searchQuery.isNotEmpty()) {
            val fullText = toString()
            var idx = fullText.indexOf(searchQuery, ignoreCase = true)
            while (idx != -1) {
                addStyle(SpanStyle(background = Color.Yellow, color = Color.Black), idx, idx + searchQuery.length)
                idx = fullText.indexOf(searchQuery, idx + searchQuery.length, ignoreCase = true)
            }
        }
    }
}

@Composable
fun DocxParagraphItem(
    paragraph: DocxParagraph,
    isHighlighted: Boolean = false,
    searchQuery: String = "",
    isPrintLayout: Boolean = false
) {
    val context = LocalContext.current

    val textAlign = when (paragraph.alignment) {
        "CENTER" -> TextAlign.Center
        "RIGHT" -> TextAlign.Right
        "JUSTIFY" -> TextAlign.Justify
        else -> TextAlign.Left
    }

    val inkTextColor = if (isPrintLayout) Color(0xFF111827) else MaterialTheme.colorScheme.onSurface

    // Fidelity-first typography scale matching Word / reference viewers
    val baseFontSize = if (isPrintLayout) {
        when (paragraph.headingLevel) {
            1 -> 15.sp
            2 -> 12.5.sp
            3 -> 11.5.sp
            4 -> 10.5.sp
            else -> 10.sp
        }
    } else {
        when (paragraph.headingLevel) {
            1 -> 19.sp
            2 -> 15.5.sp
            3 -> 13.5.sp
            4 -> 12.5.sp
            else -> 11.5.sp
        }
    }

    val resolvedLineHeight = paragraph.exactLineHeightPt?.sp ?: (baseFontSize * paragraph.lineHeightMultiplier)

    val baseStyle = TextStyle(
        fontSize = baseFontSize,
        lineHeight = resolvedLineHeight,
        fontWeight = if (paragraph.isHeading) FontWeight.Bold else FontWeight.Normal,
        color = inkTextColor,
        textAlign = textAlign
    )

    // Accurate paragraph vertical spacing directly from document spacing rules
    val verticalPadding = paragraph.spacingAfterPt.coerceAtMost(6f).dp
    val spacingTop = paragraph.spacingBeforePt.coerceAtMost(6f).dp

    val backgroundColor = if (isHighlighted) Color.Yellow.copy(alpha = 0.3f) else Color.Transparent

    // Check if paragraph contains tab stops / tab-separated runs (e.g. Title on left, Date on right)
    val hasTabs = paragraph.tabStops.isNotEmpty() || paragraph.runs.any { it.isTab || it.text.contains("\t") }
    val tabRunIndex = paragraph.runs.indexOfFirst { it.isTab || it.text.contains("\t") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(top = spacingTop)
    ) {
        if (hasTabs && tabRunIndex != -1) {
            // Split into left and right tab portions
            val leftRuns = mutableListOf<DocxRun>()
            val rightRuns = mutableListOf<DocxRun>()

            paragraph.runs.forEachIndexed { idx, run ->
                if (idx < tabRunIndex) {
                    leftRuns.add(run)
                } else if (idx == tabRunIndex) {
                    val parts = run.text.split("\t", limit = 2)
                    if (parts[0].isNotEmpty()) leftRuns.add(run.copy(text = parts[0]))
                    if (parts.size > 1 && parts[1].isNotEmpty()) rightRuns.add(run.copy(text = parts[1]))
                } else {
                    rightRuns.add(run)
                }
            }

            val leftAnnotated = remember(leftRuns, searchQuery, isPrintLayout) {
                buildAnnotatedStringForRuns(leftRuns, searchQuery, isPrintLayout)
            }
            val rightAnnotated = remember(rightRuns, searchQuery, isPrintLayout) {
                buildAnnotatedStringForRuns(rightRuns, searchQuery, isPrintLayout)
            }

            SelectionContainer {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = paragraph.indentStartPt.dp, bottom = verticalPadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = leftAnnotated,
                        style = baseStyle.copy(textAlign = TextAlign.Left),
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = rightAnnotated,
                        style = baseStyle.copy(textAlign = TextAlign.Right),
                        modifier = Modifier.wrapContentWidth()
                    )
                }
            }
        } else {
            val annotatedString = remember(paragraph, searchQuery, isPrintLayout) {
                buildAnnotatedStringForRuns(paragraph.runs, searchQuery, isPrintLayout)
            }

            val hasBullet = paragraph.bulletType != null
            val bulletSymbol = when (paragraph.bulletType) {
                "bullet" -> "•"
                "number" -> "•" // Or numbering indicator
                else -> null
            }

            // If firstLineIndentPt is negative, it's a hanging indent; if positive, it's a first line indent
            val baseIndent = paragraph.indentStartPt.coerceAtLeast(0f).dp
            val firstLineIndent = paragraph.firstLineIndentPt.dp

            SelectionContainer {
                var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }

                if (hasBullet && bulletSymbol != null) {
                    // Bullet list item with hanging indentation (two-column row so wrapped lines do not fall below bullet)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = baseIndent,
                                end = 0.dp,
                                bottom = verticalPadding
                            )
                    ) {
                        Text(
                            text = bulletSymbol,
                            style = baseStyle.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = annotatedString,
                            style = baseStyle,
                            onTextLayout = { layoutResult = it },
                            modifier = Modifier
                                .weight(1f)
                                .pointerInput(annotatedString) {
                                    detectTapGestures { offset ->
                                        layoutResult?.let { layout ->
                                            val position = layout.getOffsetForPosition(offset)
                                            annotatedString.getStringAnnotations("URL", position, position)
                                                .firstOrNull()?.let { annotation ->
                                                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))) }
                                                    catch (e: Exception) { }
                                                }
                                        }
                                    }
                                }
                        )
                    }
                } else {
                    val startPadding = (paragraph.indentStartPt + paragraph.firstLineIndentPt.coerceAtLeast(0f)).coerceAtLeast(0f).dp
                    Text(
                        text = annotatedString,
                        style = baseStyle,
                        onTextLayout = { layoutResult = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = startPadding,
                                end = 0.dp,
                                bottom = verticalPadding
                            )
                            .pointerInput(annotatedString) {
                                detectTapGestures { offset ->
                                    layoutResult?.let { layout ->
                                        val position = layout.getOffsetForPosition(offset)
                                        annotatedString.getStringAnnotations("URL", position, position)
                                            .firstOrNull()?.let { annotation ->
                                                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))) }
                                                catch (e: Exception) { }
                                            }
                                    }
                                }
                            }
                    )
                }
            }
        }

        // Render embedded images with proper sizing
        paragraph.runs.forEach { run ->
            if (run.imageUrl != null) {
                AsyncImage(
                    model = run.imageUrl,
                    contentDescription = "Embedded Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
                )
            }
        }

        // Comment annotation
        if (!paragraph.comment.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(paragraph.comment, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun EmptyDocumentState() {
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
            text = "Document Contains No Text",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

private class DocxPrintDocumentAdapter(private val context: Context, private val file: File) : PrintDocumentAdapter() {
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DocxWebView(
    docxBase64: String,
    isPrintLayout: Boolean,
    searchQuery: String,
    currentMatchIndex: Int,
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isPageLoaded by remember { mutableStateOf(false) }

    // When layout mode changes (Print Layout vs Reflow)
    LaunchedEffect(isPrintLayout, isPageLoaded) {
        if (isPageLoaded && webViewInstance != null) {
            webViewInstance?.evaluateJavascript("renderDocxBase64('$docxBase64', $isPrintLayout)", null)
        }
    }

    // When search query changes
    LaunchedEffect(searchQuery, isPageLoaded) {
        if (isPageLoaded && webViewInstance != null) {
            if (searchQuery.isBlank()) {
                webViewInstance?.evaluateJavascript("clearHighlights()", null)
            } else {
                val escaped = searchQuery
                    .replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\n", " ")
                    .replace("\r", "")
                webViewInstance?.evaluateJavascript("searchText('$escaped')", null)
            }
        }
    }

    // When match index changes (next / prev match)
    var lastMatchIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(currentMatchIndex, isPageLoaded) {
        if (isPageLoaded && webViewInstance != null && currentMatchIndex != lastMatchIndex) {
            if (currentMatchIndex > lastMatchIndex) {
                webViewInstance?.evaluateJavascript("nextMatch()", null)
            } else if (currentMatchIndex < lastMatchIndex && currentMatchIndex >= 0) {
                webViewInstance?.evaluateJavascript("prevMatch()", null)
            }
            lastMatchIndex = currentMatchIndex
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
                isVerticalScrollBarEnabled = true
                isHorizontalScrollBarEnabled = true
                setInitialScale(0)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        isPageLoaded = true
                        webViewInstance = this@apply
                        evaluateJavascript("renderDocxBase64('$docxBase64', $isPrintLayout)", null)
                    }
                }

                loadUrl("file:///android_asset/docx_viewer/viewer.html")
                webViewInstance = this
            }
        },
        update = { wv ->
            webViewInstance = wv
        },
        modifier = modifier
    )
}


