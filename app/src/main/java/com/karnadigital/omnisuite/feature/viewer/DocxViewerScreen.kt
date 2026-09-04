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
import androidx.compose.foundation.lazy.rememberLazyListState
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
    var isExporting by remember { mutableStateOf(false) }

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val currentMatchIndex by viewModel.currentMatchIndex.collectAsState()

    var searchExpanded by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()

    var activeIndexToEdit by remember { mutableStateOf(-1) }
    var paragraphToEdit by remember { mutableStateOf<DocxParagraph?>(null) }

    // Edit menu state
    var showEditMenu by remember { mutableStateOf(false) }
    var showFormatMenu by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var pendingFormatBold by remember { mutableStateOf(false) }
    var pendingFormatItalic by remember { mutableStateOf(false) }
    var pendingFormatUnderline by remember { mutableStateOf(false) }
    var pendingFormatStrike by remember { mutableStateOf(false) }
    var pendingAlignment by remember { mutableStateOf<String?>(null) }
    var pendingListType by remember { mutableStateOf<String?>(null) }
    var pendingFontSize by remember { mutableStateOf(12f) }
    var pendingTextColor by remember { mutableStateOf<String?>(null) }
    var pendingHighlightColor by remember { mutableStateOf<String?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                coroutineScope.launch {
                    val cachedFile = uriCacheUtils.cacheUriToFile(it)
                    if (cachedFile != null) {
                        viewModel.insertImageIntoParagraph(activeIndexToEdit, cachedFile.absolutePath)
                        paragraphToEdit = null
                        activeIndexToEdit = -1
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

                                val docxText = remember(state) {
                                    (state as? DocxLoadState.Success)?.document?.elements
                                        ?.filterIsInstance<DocxBodyElement.Para>()
                                        ?.joinToString("\n") { it.paragraph.runs.joinToString("") { r -> r.text } } ?: ""
                                }

                                com.karnadigital.omnisuite.feature.utility.ReadAloudButton(text = docxText)

                                IconButton(onClick = { searchExpanded = true }) {
                                    Icon(Icons.Default.Search, contentDescription = "Search text")
                                }

                                if (isEditMode) {
                                    IconButton(onClick = { viewModel.commitChanges() }) {
                                        Icon(Icons.Default.Check, contentDescription = "Commit changes", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                IconButton(onClick = { isEditMode = !isEditMode }) {
                                    Icon(
                                        imageVector = if (isEditMode) Icons.Default.Close else Icons.Default.Edit,
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
        floatingActionButton = {
            if (isEditMode && state is DocxLoadState.Success) {
                ExtendedFloatingActionButton(
                    onClick = { showAppendDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Append paragraph") },
                    text = { Text("Append") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        },
        bottomBar = {
            if (state is DocxLoadState.Success) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Microsoft 365 Bottom Editing Ribbon Sheet (when in edit mode)
                    AnimatedVisibility(
                        visible = isEditMode,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 8.dp,
                            shadowElevation = 8.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val canUndo by viewModel.canUndo.collectAsState()
                                    val canRedo by viewModel.canRedo.collectAsState()

                                    IconButton(
                                        onClick = { viewModel.undo() },
                                        enabled = canUndo,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Undo, contentDescription = "Undo", tint = if (canUndo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                                    }

                                    IconButton(
                                        onClick = { viewModel.redo() },
                                        enabled = canRedo,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Redo, contentDescription = "Redo", tint = if (canRedo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                                    }

                                    VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))

                                    FilterChip(
                                        selected = pendingFormatBold,
                                        onClick = { pendingFormatBold = !pendingFormatBold },
                                        label = { Text("B", fontWeight = FontWeight.Bold) }
                                    )
                                    FilterChip(
                                        selected = pendingFormatItalic,
                                        onClick = { pendingFormatItalic = !pendingFormatItalic },
                                        label = { Text("I", fontStyle = FontStyle.Italic) }
                                    )
                                    FilterChip(
                                        selected = pendingFormatUnderline,
                                        onClick = { pendingFormatUnderline = !pendingFormatUnderline },
                                        label = { Text("U", textDecoration = TextDecoration.Underline) }
                                    )

                                    IconButton(onClick = { showFormatMenu = true }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.FormatSize, contentDescription = "Font Style")
                                    }
                                    IconButton(onClick = { imagePickerLauncher.launch("image/*") }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.Image, contentDescription = "Insert Image")
                                    }
                                    IconButton(onClick = { showLinkDialog = true }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.Link, contentDescription = "Insert Link")
                                    }
                                    IconButton(onClick = { showAppendDialog = true }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.Add, contentDescription = "Append Paragraph")
                                    }

                                    VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))

                                    Button(
                                        onClick = { viewModel.commitChanges() },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Save Changes", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Main Viewer Dock Bar
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
                                icon = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                                title = if (isEditMode) "Done" else "Edit"
                            ) {
                                isEditMode = !isEditMode
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
                        // Compose interactive editor stream (when in Edit Mode or fallback for legacy format)
                        SelectionContainer {
                            LazyColumn(
                                state = lazyListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surface),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp)
                            ) {
                                itemsIndexed(document.elements) { index, element ->
                                    when (element) {
                                        is DocxBodyElement.Para -> {
                                            val isHighlighted = searchResults.getOrNull(currentMatchIndex)?.pageIndex == index
                                            val clickableModifier = if (isEditMode) {
                                                Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        activeIndexToEdit = index
                                                        paragraphToEdit = element.paragraph
                                                    }
                                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                                    .padding(6.dp)
                                            } else Modifier
                                            Box(modifier = clickableModifier) {
                                                DocxParagraphItem(
                                                    paragraph = element.paragraph,
                                                    isHighlighted = isHighlighted,
                                                    searchQuery = searchQuery,
                                                    isPrintLayout = false
                                                )
                                            }
                                        }
                                        is DocxBodyElement.Table -> {
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

    if (showAppendDialog) {
        var newParagraphText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAppendDialog = false },
            title = { Text("Append New Paragraph") },
            text = {
                OutlinedTextField(
                    value = newParagraphText,
                    onValueChange = { newParagraphText = it },
                    label = { Text("Paragraph Text") },
                    placeholder = { Text("Type paragraph content here...") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newParagraphText.isNotBlank()) {
                            viewModel.appendParagraph(newParagraphText)
                        }
                        showAppendDialog = false
                    }
                ) {
                    Text("Append")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAppendDialog = false }) {
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

    if (paragraphToEdit != null && activeIndexToEdit >= 0) {
        val paragraph = paragraphToEdit!!
        var textValue by remember(paragraph) { mutableStateOf(paragraph.runs.joinToString("") { it.text }) }
        var commentValue by remember(paragraph) { mutableStateOf(paragraph.comment ?: "") }
        var isBold by remember(paragraph) { mutableStateOf(paragraph.runs.firstOrNull()?.isBold ?: false) }
        var isItalic by remember(paragraph) { mutableStateOf(paragraph.runs.firstOrNull()?.isItalic ?: false) }
        var isUnderline by remember(paragraph) { mutableStateOf(paragraph.runs.firstOrNull()?.isUnderline ?: false) }
        var textColorHex by remember(paragraph) { mutableStateOf(paragraph.runs.firstOrNull()?.color) }

        AlertDialog(
            onDismissRequest = { 
                paragraphToEdit = null
                activeIndexToEdit = -1
            },
            title = { Text("Edit Paragraph Content & Format", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Paragraph Text Field
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { textValue = it },
                        label = { Text("Paragraph Text") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 6
                    )

                    // Text Formatting Options Row
                    Text("Typography Style:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilterChip(
                            selected = isBold,
                            onClick = { isBold = !isBold },
                            label = { Text("Bold") },
                            leadingIcon = if (isBold) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null
                        )
                        FilterChip(
                            selected = isItalic,
                            onClick = { isItalic = !isItalic },
                            label = { Text("Italic") },
                            leadingIcon = if (isItalic) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null
                        )
                        FilterChip(
                            selected = isUnderline,
                            onClick = { isUnderline = !isUnderline },
                            label = { Text("Underline") },
                            leadingIcon = if (isUnderline) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null
                        )
                    }

                    // Text Color Swatches Row
                    Text("Text Color Preset:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        listOf(null to "Default", "EF4444" to "Red", "3B82F6" to "Blue", "10B981" to "Green", "F59E0B" to "Orange").forEach { (hex, name) ->
                            val isSelected = (textColorHex?.lowercase()?.replace("#", "") == hex?.lowercase())
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (hex == null) MaterialTheme.colorScheme.surfaceVariant else Color(android.graphics.Color.parseColor("#$hex")))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { textColorHex = hex }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (hex == null) {
                                    Text("A", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                } else if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // Annotation / Comment Field
                    OutlinedTextField(
                        value = commentValue,
                        onValueChange = { commentValue = it },
                        label = { Text("Add Comment Annotation Note") },
                        placeholder = { Text("Type an offline review note...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Image Insertion Launcher Button
                    Button(
                        onClick = {
                            imagePickerLauncher.launch("image/*")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Insert Picture Run")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateParagraph(
                            index = activeIndexToEdit,
                            newText = textValue,
                            isBold = isBold,
                            isItalic = isItalic,
                            isUnderline = isUnderline,
                            colorHex = textColorHex,
                            comment = commentValue
                        )
                        paragraphToEdit = null
                        activeIndexToEdit = -1
                    }
                ) {
                    Text("Apply & Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        paragraphToEdit = null
                        activeIndexToEdit = -1
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Format Menu Popup
    FormatMenuPopup(
        expanded = showFormatMenu,
        onDismiss = { showFormatMenu = false },
        isBold = pendingFormatBold,
        isItalic = pendingFormatItalic,
        isUnderline = pendingFormatUnderline,
        onBoldToggle = { pendingFormatBold = !pendingFormatBold },
        onItalicToggle = { pendingFormatItalic = !pendingFormatItalic },
        onUnderlineToggle = { pendingFormatUnderline = !pendingFormatUnderline },
        onColorChange = { pendingTextColor = it },
        onBgColorChange = { pendingHighlightColor = it },
        onFontSizeChange = { pendingFontSize = it },
        onAlignChange = { pendingAlignment = it }
    )

    // Link Dialog
    if (showLinkDialog) {
        var linkText by remember { mutableStateOf("") }
        var linkUrl by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showLinkDialog = false },
            title = { Text("Insert Link") },
            text = {
                Column {
                    OutlinedTextField(value = linkText, onValueChange = { linkText = it }, label = { Text("Link Text") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = linkUrl, onValueChange = { linkUrl = it }, label = { Text("URL") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (linkText.isNotBlank() && linkUrl.isNotBlank()) {
                        viewModel.appendParagraph("$linkText ($linkUrl)")
                        Toast.makeText(context, "Link inserted as new paragraph", Toast.LENGTH_SHORT).show()
                    }
                    showLinkDialog = false
                }) { Text("Insert") }
            },
            dismissButton = {
                TextButton(onClick = { showLinkDialog = false }) { Text("Cancel") }
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
fun DocxParagraphEditorItem(
    index: Int,
    paragraph: DocxParagraph,
    isHighlighted: Boolean = false,
    onTextChange: (String) -> Unit
) {
    var textState by remember(paragraph) { 
        mutableStateOf(paragraph.runs.joinToString("") { it.text }) 
    }

    val backgroundColor = if (isHighlighted) Color.Yellow.copy(alpha = 0.1f) else Color.Transparent

    val textStyle = if (paragraph.isHeading) {
        MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
    } else {
        MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
    ) {
        TextField(
            value = textState,
            onValueChange = {
                textState = it
                onTextChange(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            textStyle = textStyle,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            singleLine = false
        )

        // Render embedded images in editor mode too
        paragraph.runs.forEach { run ->
            if (run.imageUrl != null) {
                val bitmap = remember(run.imageUrl) {
                    try {
                        BitmapFactory.decodeFile(run.imageUrl)
                    } catch (e: Exception) {
                        null
                    }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Embedded Image",
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(vertical = 8.dp)
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                }
            }
        }
    }
}

@Composable
fun DocxTableItem(table: DocxBodyElement.Table, searchQuery: String, isPrintLayout: Boolean = false) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        border = BorderStroke(0.5.dp, if (isPrintLayout) Color.LightGray else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = if (isPrintLayout) Color(0xFFFAFAFA) else MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            table.rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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

            SelectionContainer {
                var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                Text(
                    text = annotatedString,
                    style = baseStyle,
                    onTextLayout = { layoutResult = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = paragraph.indentStartPt.dp,
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

        // Render embedded images with proper sizing
        paragraph.runs.forEach { run ->
            if (run.imageUrl != null) {
                val bitmap = remember(run.imageUrl) {
                    try { BitmapFactory.decodeFile(run.imageUrl) } catch (e: Exception) { null }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
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


