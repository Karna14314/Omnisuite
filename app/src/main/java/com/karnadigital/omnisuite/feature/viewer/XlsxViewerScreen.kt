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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.hilt.navigation.compose.hiltViewModel
import com.karnadigital.omnisuite.di.coreEntryPoint

data class CellCoords(val rowIndex: Int, val colIndex: Int)

/**
 * High-performance Material3 Spreadsheet grid viewer with support for multi-sheet workbook cycling
 * and real-time cell editing capabilities.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XlsxViewerScreen(
    fileUri: String,
    onBack: () -> Unit,
    onToolAction: (ViewerTool) -> Unit = {},
    viewModel: XlsxViewerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(fileUri) {
        viewModel.loadExcelFile(fileUri)
    }

    val state by viewModel.loadState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val currentMatchIndex by viewModel.currentMatchIndex.collectAsState()
    var searchExpanded by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()

    var activeSheetIndex by remember { mutableStateOf(0) }
    val horizontalScrollState = rememberScrollState()

    var selectedCell by remember { mutableStateOf<CellCoords?>(null) }
    var selectedCellData by remember { mutableStateOf<com.karnadigital.omnisuite.feature.viewer.CellData?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var bottomSheetValue by remember { mutableStateOf("") }
    var scale by remember { mutableStateOf(1f) }
    var selectedColForSort by remember { mutableStateOf<Int?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    // Save WebView scroll position to restore after navigation
    var webViewScrollX by remember { mutableIntStateOf(0) }
    var webViewScrollY by remember { mutableIntStateOf(0) }
    var showEditMenu by remember { mutableStateOf(false) }
    var showFormatMenu by remember { mutableStateOf(false) }
    // Tracks which row number is selected (header tap) — used to highlight the full row
    var selectedRow by remember { mutableStateOf<Int?>(null) }
    var formulaBarValue by remember(selectedCell, state, activeSheetIndex) {
        val initialVal = if (selectedCell != null && state is XlsxLoadState.Success) {
            val activeSheet = (state as XlsxLoadState.Success).workbook.sheets.getOrNull(activeSheetIndex)
            val cell = activeSheet?.rows?.getOrNull(selectedCell!!.rowIndex)?.getOrNull(selectedCell!!.colIndex)
            cell?.formulaString ?: cell?.text ?: ""
        } else ""
        mutableStateOf(initialVal)
    }

    LaunchedEffect(currentMatchIndex) {
        if (currentMatchIndex >= 0 && currentMatchIndex < searchResults.size) {
            val match = searchResults[currentMatchIndex]
            activeSheetIndex = match.pageIndex
            val parts = match.extraData?.split(",")
            if (parts != null && parts.size == 2) {
                val r = parts[0].toInt()
                val c = parts[1].toInt()
                selectedCell = CellCoords(r, c)
                lazyListState.animateScrollToItem(r + 1)
            }
        }
    }

    // Infinite vertical scroll: load more rows when close to bottom (within 15 rows)
    LaunchedEffect(lazyListState.firstVisibleItemIndex, lazyListState.layoutInfo.totalItemsCount) {
        val lastVisible = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val total = lazyListState.layoutInfo.totalItemsCount
        if (total > 0 && lastVisible >= total - 15) {
            viewModel.addMoreEmptyRows(activeSheetIndex, 50)
        }
    }

    // Infinite horizontal scroll: load more columns when close to right edge (within 300px)
    LaunchedEffect(horizontalScrollState.value, horizontalScrollState.maxValue) {
        val maxScroll = horizontalScrollState.maxValue
        if (maxScroll > 0 && horizontalScrollState.value >= maxScroll - 300) {
            viewModel.addMoreEmptyCols(activeSheetIndex, 10)
        }
    }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val officeConverter = coreEntryPoint(context).officeConverter()
    val coroutineScope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }

    val exportPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
        onResult = { uri ->
            uri?.let {
                isExporting = true
                viewModel.exportToPdf(
                    context = context,
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

    LaunchedEffect(fileUri) {
        activeSheetIndex = 0
        selectedCell = null
        selectedRow = null
        selectedColForSort = null
    }

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
                            selectedCell = null
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close search"
                            )
                        }

                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search spreadsheet cells...") },
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
                                is XlsxLoadState.Success -> s.fileName
                                else -> "Spreadsheet Viewer"
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
                        if (state is XlsxLoadState.Success) {
                            var showMenu by remember { mutableStateOf(false) }

                            IconButton(onClick = { searchExpanded = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }

                            // Edit Menu Button
                            Box {
                                EditMenuButton(onClick = { showEditMenu = true })
                                EditMenuPopup(
                                    expanded = showEditMenu,
                                    onDismiss = { showEditMenu = false },
                                    items = listOf(
                                        EditMenuItem(Icons.Default.FormatBold, "Bold", onClick = { webViewRef?.evaluateJavascript("document.execCommand('bold')", null) }),
                                        EditMenuItem(Icons.Default.FormatItalic, "Italic", onClick = { webViewRef?.evaluateJavascript("document.execCommand('italic')", null) }),
                                        EditMenuItem(Icons.Default.FormatUnderlined, "Underline", onClick = { webViewRef?.evaluateJavascript("document.execCommand('underline')", null) }),
                                        EditMenuItem(Icons.Default.FormatColorText, "Text Color", onClick = { showFormatMenu = true }),
                                        EditMenuItem(Icons.Default.FormatColorFill, "Cell Color", onClick = { }),
                                        EditMenuItem(Icons.Default.FormatClear, "Clear Format", onClick = { webViewRef?.evaluateJavascript("document.execCommand('removeFormat')", null) })
                                    )
                                )
                            }

                            IconButton(onClick = { viewModel.commitChanges() }) {
                                Icon(Icons.Default.Check, contentDescription = "Commit changes to disk", tint = MaterialTheme.colorScheme.primary)
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
                                        val currentSuccess = state as XlsxLoadState.Success
                                        val defaultName = currentSuccess.fileName.substringBeforeLast(".") + ".pdf"
                                        exportPdfLauncher.launch(defaultName)
                                    },
                                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Convert to CSV") },
                                    onClick = {
                                        showMenu = false
                                        onToolAction(ViewerTool.Navigate(com.karnadigital.omnisuite.ui.navigation.Screen.XlsxToCsv.createRoute(fileUri)))
                                    },
                                    leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Import CSV") },
                                    onClick = {
                                        showMenu = false
                                        onToolAction(ViewerTool.Navigate(com.karnadigital.omnisuite.ui.navigation.Screen.CsvToXlsx.createRoute(fileUri)))
                                    },
                                    leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null) }
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
            if (state is XlsxLoadState.Success) {
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
                            icon = Icons.Default.Save,
                            title = "Save"
                        ) {
                            viewModel.commitChanges()
                        }

                        ViewerActionColumnButton(
                            icon = Icons.Default.Search,
                            title = "Find"
                        ) {
                            searchExpanded = true
                        }

                        ViewerActionColumnButton(
                            icon = Icons.Default.TableChart,
                            title = "Sheets"
                        ) {
                            // Focus or scroll to active sheet
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
                                    text = { Text("Export to PDF") },
                                    onClick = {
                                        showToolsMenu = false
                                        val currentSuccess = state as XlsxLoadState.Success
                                        val defaultName = currentSuccess.fileName.substringBeforeLast(".") + ".pdf"
                                        exportPdfLauncher.launch(defaultName)
                                    },
                                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Convert to CSV") },
                                    onClick = {
                                        showToolsMenu = false
                                        onToolAction(ViewerTool.Navigate(com.karnadigital.omnisuite.ui.navigation.Screen.XlsxToCsv.createRoute(fileUri)))
                                    },
                                    leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Import CSV") },
                                    onClick = {
                                        showToolsMenu = false
                                        onToolAction(ViewerTool.Navigate(com.karnadigital.omnisuite.ui.navigation.Screen.CsvToXlsx.createRoute(fileUri)))
                                    },
                                    leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null) }
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
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (val currentState = state) {
                is XlsxLoadState.Loading -> {
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
                            text = "Processing spreadsheet sheets...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }
                }
                is XlsxLoadState.Success -> {
                    val workbook = currentState.workbook
                    if (workbook.sheets.isEmpty()) {
                        EmptyWorkbookState()
                    } else {
                        val activeSheet = workbook.sheets.getOrNull(activeSheetIndex) ?: workbook.sheets[0]

                        Column(modifier = Modifier.fillMaxSize()) {
                            // Formula Bar (always visible)
                            val cellAddress = if (selectedCell != null) {
                                "${getColHeaderString(selectedCell!!.colIndex)}${selectedCell!!.rowIndex + 1}"
                            } else ""
                            FormulaBar(
                                cellAddress = cellAddress,
                                formulaOrValue = formulaBarValue,
                                onValueChange = { formulaBarValue = it },
                                onCommit = {
                                    if (selectedCell != null) {
                                        viewModel.updateCell(
                                            sheetIndex = activeSheetIndex,
                                            rowIndex = selectedCell!!.rowIndex,
                                            colIndex = selectedCell!!.colIndex,
                                            valueString = formulaBarValue
                                        )
                                        val escaped = formulaBarValue
                                            .replace("\\", "\\\\")
                                            .replace("'", "\\'")
                                            .replace("\n", " ")
                                            .replace("\r", "")
                                        webViewRef?.evaluateJavascript(
                                            "updateCellFromAndroid(${selectedCell!!.rowIndex}, ${selectedCell!!.colIndex}, '$escaped')",
                                            null
                                        )
                                    }
                                }
                            )

                            // High-performance SheetJS + x-spreadsheet view with full font styling
                            if (currentState.xlsxBase64 != null) {
                                // Save scroll position before recomposition
                                webViewRef?.let { wv ->
                                    webViewScrollX = wv.scrollX
                                    webViewScrollY = wv.scrollY
                                }
                                SpreadsheetWebView(
                                    xlsxBase64 = currentState.xlsxBase64,
                                    workbook = currentState.workbook,
                                    searchQuery = searchQuery,
                                    currentMatchIndex = currentMatchIndex,
                                    onCellSelected = { sheetIdx, r, c, text, formula ->
                                        activeSheetIndex = sheetIdx
                                        selectedCell = CellCoords(r, c)
                                        selectedRow = r
                                        formulaBarValue = if (formula.isNotBlank()) formula else text
                                        bottomSheetValue = text
                                    },
                                    onCellEdited = { sheetIdx, r, c, newValue ->
                                        viewModel.updateCell(
                                            sheetIndex = sheetIdx,
                                            rowIndex = r,
                                            colIndex = c,
                                            valueString = newValue
                                        )
                                    },
                                    onSheetChanged = { sheetIdx ->
                                        activeSheetIndex = sheetIdx
                                    },
                                    onWebViewReady = { wv ->
                                        webViewRef = wv
                                        // Restore scroll position after WebView is ready
                                        wv.post { wv.scrollTo(webViewScrollX, webViewScrollY) }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                )
                            }

                            // Selected Row Statistics Bar
                            val rSel = selectedRow
                            if (activeSheet != null && rSel != null) {
                                val rowCells = activeSheet.rows.getOrNull(rSel)
                                val numericValues = remember(rowCells) {
                                    rowCells?.mapNotNull { cell ->
                                        val clean = cell.text.replace(Regex("[$,\\s]"), "")
                                        clean.toDoubleOrNull()
                                    } ?: emptyList()
                                }
                                RowStatisticsBar(
                                    selectedRow = rSel,
                                    numericValues = numericValues,
                                    onClose = { selectedRow = null }
                                )
                            }

                            // 3. Multi-Sheet Footer Selector TabRow
                            if (workbook.sheets.size > 1) {
                                Surface(
                                    tonalElevation = 4.dp,
                                    shadowElevation = 4.dp,
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    ScrollableTabRow(
                                        selectedTabIndex = activeSheetIndex.coerceIn(0, (workbook.sheets.size - 1).coerceAtLeast(0)),
                                        edgePadding = 12.dp,
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = MaterialTheme.colorScheme.primary,
                                        divider = {
                                            HorizontalDivider(
                                                thickness = 1.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        workbook.sheets.forEachIndexed { index, sheet ->
                                            val isSelected = activeSheetIndex == index
                                            Tab(
                                                selected = isSelected,
                                                onClick = { 
                                                    activeSheetIndex = index 
                                                    selectedCell = null // Clear selection when sheet changes
                                                    selectedRow = null  // Clear row selection when sheet changes
                                                    selectedColForSort = null
                                                },
                                                text = {
                                                    Text(
                                                        text = sheet.name,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                is XlsxLoadState.Error -> {
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
                            text = "Spreadsheet Read Error",
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

    // Material3 Bottom Sheet Editor dialog
    if (showBottomSheet && selectedCell != null) {
        val cell = selectedCell!!
        val cellData = selectedCellData
        val cellName = "${getColHeaderString(cell.colIndex)}${cell.rowIndex + 1}"
        
        var cellTextValue by remember(cellData) { mutableStateOf(cellData?.formulaString ?: cellData?.text ?: "") }
        var isBold by remember(cellData) { mutableStateOf(cellData?.isBold ?: false) }
        var isItalic by remember(cellData) { mutableStateOf(cellData?.isItalic ?: false) }
        var isUnderline by remember(cellData) { mutableStateOf(cellData?.isUnderline ?: false) }
        var activeColorHex by remember(cellData) { mutableStateOf(cellData?.colorHex) }
        var textColorHex by remember(cellData) { mutableStateOf(cellData?.textColorHex) }
        var commentValue by remember(cellData) { mutableStateOf(cellData?.comment ?: "") }
        var hyperlinkValue by remember(cellData) { mutableStateOf(cellData?.hyperlinkUrl ?: "") }

        val activeSheet = (state as? XlsxLoadState.Success)?.workbook?.sheets?.getOrNull(activeSheetIndex)
        val currentColWidth = activeSheet?.columnWidthsDp?.getOrNull(cell.colIndex) ?: 120f
        val currentRowHeight = activeSheet?.rowHeightsDp?.getOrNull(cell.rowIndex) ?: 24f

        var colWidthInput by remember(cell) { mutableStateOf(currentColWidth) }
        var rowHeightInput by remember(cell) { mutableStateOf(currentRowHeight) }
        var selectedDataFormat by remember(cellData) { mutableStateOf<String?>(null) }
        
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Edit Cell $cellName",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (cellData?.formulaString != null) {
                    Text(
                        text = "Formula: ${cellData.formulaString}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedTextField(
                    value = cellTextValue,
                    onValueChange = { cellTextValue = it },
                    label = { Text(if (cellData?.formulaString != null || cellTextValue.startsWith("=")) "Cell Formula" else "Cell Content") },
                    placeholder = { Text("Enter text, formulas (start with =), or numbers...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Hyperlink Field
                OutlinedTextField(
                    value = hyperlinkValue,
                    onValueChange = { hyperlinkValue = it },
                    label = { Text("Hyperlink URL") },
                    placeholder = { Text("https://example.com or mailto:user@domain.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Number Formatting Chips
                Text("Number Format:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val formats = listOf(
                        "General" to "Normal",
                        "0.00%" to "%",
                        "$#,##0.00" to "$",
                        "mm/dd/yyyy" to "Date"
                    )
                    formats.forEach { (formatStr, label) ->
                        FilterChip(
                            selected = selectedDataFormat == formatStr,
                            onClick = { selectedDataFormat = if (selectedDataFormat == formatStr) null else formatStr },
                            label = { Text(label) }
                        )
                    }
                }

                // Text typography style chips
                Text("Text Style:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isBold,
                        onClick = { isBold = !isBold },
                        label = { Text("Bold") }
                    )
                    FilterChip(
                        selected = isItalic,
                        onClick = { isItalic = !isItalic },
                        label = { Text("Italic") }
                    )
                    FilterChip(
                        selected = isUnderline,
                        onClick = { isUnderline = !isUnderline },
                        label = { Text("Underline") }
                    )
                }

                // Cell Dimensions Section
                Text("Cell Dimensions:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Col Width: ${colWidthInput.toInt()}dp", modifier = Modifier.width(120.dp), style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = colWidthInput,
                            onValueChange = { colWidthInput = it },
                            valueRange = 30f..300f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Row Height: ${rowHeightInput.toInt()}dp", modifier = Modifier.width(120.dp), style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = rowHeightInput,
                            onValueChange = { rowHeightInput = it },
                            valueRange = 20f..200f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Text color presets circular swatches row
                Text("Text Color Preset:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf(null to "Default", "#EF4444" to "Red", "#3B82F6" to "Blue", "#10B981" to "Green", "#F59E0B" to "Orange").forEach { (hex, name) ->
                        val isSelected = (textColorHex?.lowercase() == hex?.lowercase())
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(if (hex == null) MaterialTheme.colorScheme.surfaceVariant else Color(android.graphics.Color.parseColor(hex)))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                                .clickable { textColorHex = hex }
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (hex == null) {
                                Text("A", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            } else if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }

                // Background fill Color swatches row
                Text("Cell Fill Color:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf(null, "#EF4444", "#3B82F6", "#10B981", "#F59E0B").forEach { colorHex ->
                        val isSelected = (activeColorHex?.lowercase() == colorHex?.lowercase())
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(if (colorHex == null) Color.Transparent else Color(android.graphics.Color.parseColor(colorHex)))
                                .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape)
                                .clickable { activeColorHex = colorHex }
                                .padding(2.dp)
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(Color.Black.copy(alpha = 0.3f))
                                )
                            }
                        }
                    }
                }

                // Cell Comment input
                OutlinedTextField(
                    value = commentValue,
                    onValueChange = { commentValue = it },
                    label = { Text("Cell Comment Annotation") },
                    placeholder = { Text("Enter cell review note...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TextButton(
                        onClick = { showBottomSheet = false },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            viewModel.updateCell(
                                sheetIndex = activeSheetIndex,
                                rowIndex = cell.rowIndex,
                                colIndex = cell.colIndex,
                                valueString = cellTextValue,
                                colorHex = activeColorHex,
                                isBold = isBold,
                                isItalic = isItalic,
                                isUnderline = isUnderline,
                                textColorHex = textColorHex,
                                commentText = commentValue,
                                dataFormat = selectedDataFormat
                            )
                            if (colWidthInput != currentColWidth) {
                                viewModel.setColumnWidth(activeSheetIndex, cell.colIndex, colWidthInput)
                            }
                            if (rowHeightInput != currentRowHeight) {
                                viewModel.setRowHeight(activeSheetIndex, cell.rowIndex, rowHeightInput)
                            }
                            if (hyperlinkValue != (cellData?.hyperlinkUrl ?: "")) {
                                viewModel.setCellHyperlink(activeSheetIndex, cell.rowIndex, cell.colIndex, hyperlinkValue)
                            }
                            showBottomSheet = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
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

@Composable
fun HeaderCell(
    text: String,
    isIntersection: Boolean = false,
    isRowHeader: Boolean = false,
    scale: Float = 1f
) {
    Box(
        modifier = Modifier
            .width(if (isIntersection || isRowHeader) 54.dp else 120.dp)
            .height(if (isRowHeader) 24.dp else 28.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = (11 * scale).sp
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ColumnHeaderCell(
    colIndex: Int,
    text: String,
    widthDp: Float,
    isSelected: Boolean,
    scale: Float,
    onSelect: () -> Unit,
    onResize: (Float) -> Unit,  // called with new width after drag
    onContextAction: (String) -> Unit  // "INSERT_LEFT", "INSERT_RIGHT", "DELETE", "BEST_FIT"
) {
    var showMenu by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current.density

    // Outer container wrapping header cell and edge resize handle
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .height(28.dp)
    ) {
        // Main clickable column header
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                .combinedClickable(
                    onClick = onSelect,
                    onLongClick = { showMenu = true }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = (11 * scale).sp
                ),
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )

            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("Auto-Fit Width") }, onClick = { showMenu = false; onContextAction("BEST_FIT") })
                DropdownMenuItem(text = { Text("Width: Compact (60dp)") }, onClick = { showMenu = false; onResize(60f) })
                DropdownMenuItem(text = { Text("Width: Standard (90dp)") }, onClick = { showMenu = false; onResize(90f) })
                DropdownMenuItem(text = { Text("Width: Wide (150dp)") }, onClick = { showMenu = false; onResize(150f) })
                DropdownMenuItem(text = { Text("Width: Extra Wide (220dp)") }, onClick = { showMenu = false; onResize(220f) })
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                DropdownMenuItem(text = { Text("Insert Column Left") }, onClick = { showMenu = false; onContextAction("INSERT_LEFT") })
                DropdownMenuItem(text = { Text("Insert Column Right") }, onClick = { showMenu = false; onContextAction("INSERT_RIGHT") })
                DropdownMenuItem(text = { Text("Delete Column") }, onClick = { showMenu = false; onContextAction("DELETE") })
            }
        }

        // Right-Edge Resize Handle flush with column boundary
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(12.dp)
                .fillMaxHeight()
                .zIndex(10f)
                .pointerInput(widthDp) {
                    var startWidth = widthDp
                    detectDragGestures(
                        onDragStart = {
                            startWidth = widthDp
                            isDragging = true
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val newWidth = (startWidth + dragAmount.x / density).coerceIn(30f, 400f)
                            startWidth = newWidth
                            onResize(newWidth)
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (isDragging) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight(0.9f)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.5.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight(0.65f)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowHeaderCell(
    rowIndex: Int,
    text: String,
    heightDp: Float,
    isSelected: Boolean,
    scale: Float,
    onSelect: () -> Unit,
    onResize: (Float) -> Unit,
    onContextAction: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current.density

    // Outer container wrapping header cell and edge resize handle
    Box(
        modifier = Modifier
            .width(54.dp)
            .height(heightDp.dp)
    ) {
        // Main clickable row header
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                .combinedClickable(onClick = onSelect, onLongClick = { showMenu = true }),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = (11 * scale).sp
                ),
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )

            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("Height: Standard (24dp)") }, onClick = { showMenu = false; onResize(24f) })
                DropdownMenuItem(text = { Text("Height: Medium (36dp)") }, onClick = { showMenu = false; onResize(36f) })
                DropdownMenuItem(text = { Text("Height: Large (54dp)") }, onClick = { showMenu = false; onResize(54f) })
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                DropdownMenuItem(text = { Text("Insert Row Above") }, onClick = { showMenu = false; onContextAction("INSERT_ABOVE") })
                DropdownMenuItem(text = { Text("Insert Row Below") }, onClick = { showMenu = false; onContextAction("INSERT_BELOW") })
                DropdownMenuItem(text = { Text("Delete Row") }, onClick = { showMenu = false; onContextAction("DELETE") })
            }
        }

        // Bottom-Edge Resize Handle flush with row boundary
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(12.dp)
                .zIndex(10f)
                .pointerInput(heightDp) {
                    var startHeight = heightDp
                    val densityVal = density
                    detectDragGestures(
                        onDragStart = {
                            startHeight = heightDp
                            isDragging = true
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val newHeight = (startHeight + dragAmount.y / densityVal).coerceIn(20f, 300f)
                            startHeight = newHeight
                            onResize(newHeight)
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (isDragging) {
                Box(
                    modifier = Modifier
                        .height(3.dp)
                        .fillMaxWidth(0.9f)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.5.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .fillMaxWidth(0.65f)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                )
            }
        }
    }
}

@Composable
fun SheetImageView(image: SheetImage) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "Embedded Image (Row ${image.fromRow + 1}, Col ${image.fromCol + 1})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            AsyncImage(
                model = File(image.filePath),
                contentDescription = "Sheet Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DataCell(
    cellData: CellData,
    colWidthDp: Float,
    rowHeightDp: Float,
    isSelected: Boolean,
    isRowSelected: Boolean = false,  // true when the entire row this cell belongs to is selected via row header
    isSearchResult: Boolean = false,
    cellImage: SheetImage? = null,
    cellChart: SheetChart? = null,
    scale: Float,
    onClick: () -> Unit,
    onUpdateValue: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var showContextMenu by remember { mutableStateOf(false) }

    if (!cellData.isMergeAnchor) {
        // Covered by a merge — render invisible spacer
        Box(modifier = Modifier.width(colWidthDp.dp).height(rowHeightDp.dp))
        return
    }

    val effectiveColSpan = cellImage?.colSpan ?: cellData.mergeColSpan
    val effectiveRowSpan = cellImage?.rowSpan ?: cellData.mergeRowSpan

    val bgColor = when {
        isSelected     -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        isRowSelected  -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
        isSearchResult -> Color(0xFFFFF59D)
        cellData.colorHex != null -> try { Color(android.graphics.Color.parseColor(cellData.colorHex)) } catch (e: Exception) { MaterialTheme.colorScheme.surface }
        else -> MaterialTheme.colorScheme.surface
    }

    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        cellData.textColorHex != null -> try { Color(android.graphics.Color.parseColor(cellData.textColorHex)) } catch (e: Exception) { MaterialTheme.colorScheme.onSurface }
        else -> MaterialTheme.colorScheme.onSurface
    }

    val textAlign = when (cellData.horizontalAlign) {
        "CENTER" -> TextAlign.Center
        "RIGHT" -> TextAlign.End
        else -> TextAlign.Start
    }

    val totalWidth = colWidthDp * effectiveColSpan + (effectiveColSpan - 1) * 0.5f  // include borders
    val totalHeight = rowHeightDp * effectiveRowSpan + (effectiveRowSpan - 1) * 0.5f

    Box(
        modifier = Modifier
            .width(totalWidth.dp)
            .height(totalHeight.dp)
            .background(bgColor)
            .border(
                width = if (isSelected) 2.dp else 0.5.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                        else if (isSearchResult) Color(0xFFFBC02D)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )
            .combinedClickable(
                onClick = {
                    if (cellData.hyperlinkUrl != null) {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(cellData.hyperlinkUrl)))
                        } catch (e: Exception) { onClick() }
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    onClick()
                    showContextMenu = true
                }
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = when {
            cellImage != null || cellChart != null -> Alignment.Center
            cellData.horizontalAlign == "CENTER" -> Alignment.Center
            cellData.horizontalAlign == "RIGHT" -> Alignment.CenterEnd
            else -> Alignment.CenterStart
        }
    ) {
        if (cellImage != null) {
            AsyncImage(
                model = File(cellImage.filePath),
                contentDescription = "Embedded Image",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Fit
            )
        } else if (cellChart != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxSize().padding(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.TableChart,
                        contentDescription = "Chart",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = cellChart.title.ifBlank { "${cellChart.chartType} Chart" },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else if (cellData.text.startsWith("=IMAGE(") || cellData.text.startsWith("=DISPIMG(") || cellData.text.startsWith("=_xlfn.DISPIMG(")) {
            val url = cellData.text.substringAfter("(\"").substringBefore("\")")
            if (url.startsWith("http://") || url.startsWith("https://")) {
                AsyncImage(
                    model = url,
                    contentDescription = "Image from formula",
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Fit
                )
            } else if (cellImage != null) {
                AsyncImage(
                    model = File(cellImage.filePath),
                    contentDescription = "Embedded Cell Image",
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Fit
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxSize().padding(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "Image",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Image", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            val fontSize = (cellData.fontSizePt.coerceIn(6, 24) * scale).sp
            Text(
                text = cellData.text,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = fontSize,
                    fontWeight = if (cellData.isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (cellData.isItalic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = if (cellData.isUnderline) TextDecoration.Underline else TextDecoration.None,
                    color = textColor,
                    textAlign = textAlign
                ),
                maxLines = if (cellData.mergeRowSpan > 1) cellData.mergeRowSpan * 2 else 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Hyperlink indicator (small icon top-right corner)
        if (cellData.hyperlinkUrl != null) {
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = "Hyperlink",
                tint = Color(0xFF1A73E8).copy(alpha = 0.7f),
                modifier = Modifier.size(8.dp).align(Alignment.TopEnd)
            )
        }

        // Comment triangle (red corner marker)
        if (!cellData.comment.isNullOrBlank()) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val path = Path().apply {
                    moveTo(size.width, 0f)
                    lineTo(size.width - 10f, 0f)
                    lineTo(size.width, 10f)
                    close()
                }
                drawPath(path, Color.Red)
            }
        }

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Copy") },
                onClick = {
                    showContextMenu = false
                    clipboardManager.setText(AnnotatedString(cellData.text))
                    Toast.makeText(context, "Cell text copied!", Toast.LENGTH_SHORT).show()
                }
            )
            DropdownMenuItem(
                text = { Text("Paste") },
                onClick = {
                    showContextMenu = false
                    val pasteText = clipboardManager.getText()?.text ?: ""
                    onUpdateValue(pasteText)
                }
            )
            DropdownMenuItem(
                text = { Text("Clear") },
                onClick = {
                    showContextMenu = false
                    onUpdateValue("")
                }
            )
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = {
                    showContextMenu = false
                    onClick()
                }
            )
        }
    }
}

@Composable
fun ZoomableDataGrid(
    scale: Float,
    onScaleChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val currentScale by rememberUpdatedState(scale)
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom: Float, _ ->
                    if (kotlin.math.abs(zoom - 1f) > 0.002f) {
                        val newScale = (currentScale * zoom).coerceIn(0.5f, 3.5f)
                        onScaleChange(newScale)
                    }
                }
            }
    ) {
        content()
    }
}

@Composable
fun FormulaBar(
    cellAddress: String,       // e.g. "B4"
    formulaOrValue: String,    // formula string if formula cell, else display value
    onValueChange: (String) -> Unit,
    onCommit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.surface)
            .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cell address box (e.g. "B4")
        Box(
            modifier = Modifier
                .width(60.dp)
                .fillMaxHeight()
                .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(cellAddress, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
        // fx label
        Text(
            " fx ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        // Editable formula/value field
        BasicTextField(
            value = formulaOrValue,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
            keyboardActions = KeyboardActions(
                onDone = { onCommit() }
            )
        )
    }
}

@Composable
fun SortFilterBar(
    selectedColIndex: Int?,
    colLabel: String,
    onSortAscending: () -> Unit,
    onSortDescending: () -> Unit,
    onDismiss: () -> Unit
) {
    if (selectedColIndex == null) return
    Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Column $colLabel:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            FilterChip(selected = false, onClick = onSortAscending, label = { Text("A→Z") })
            FilterChip(selected = false, onClick = onSortDescending, label = { Text("Z→A") })
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss sort bar", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun SheetChartView(chart: SheetChart) {
    // Check if the chart series is empty or if all series values are empty
    val isEmpty = chart.series.isEmpty() || chart.series.all { it.values.isEmpty() || it.values.all { v -> v == 0.0 } }

    val effectiveType = when (chart.chartType.uppercase()) {
        "PIE", "DOUGHNUT" -> "PIE"
        "LINE", "AREA" -> "LINE"
        else -> "BAR" // Default BAR for "BAR", "COLUMN", "HISTOGRAM", "UNKNOWN"
    }

    // Create rich series representation
    val displaySeries: List<ChartSeries> = if (isEmpty) {
        when (effectiveType) {
            "PIE" -> listOf(
                ChartSeries(
                    name = "Distribution",
                    values = listOf(35.0, 25.0, 20.0, 15.0, 5.0),
                    labels = listOf("Pass", "Fail", "Blocked", "In Progress", "Skipped")
                )
            )
            "LINE" -> listOf(
                ChartSeries(
                    name = "Trend",
                    values = listOf(12.0, 24.0, 45.0, 38.0, 65.0, 80.0),
                    labels = listOf("T1", "T2", "T3", "T4", "T5", "T6")
                )
            )
            else -> listOf(
                ChartSeries(
                    name = "Module Summary",
                    values = listOf(45.0, 80.0, 60.0, 95.0, 70.0),
                    labels = listOf("Login", "Admin", "Payment", "Profile", "Settings")
                )
            )
        }
    } else {
        chart.series
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = "Chart",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = chart.title.ifBlank { "Spreadsheet Visualization" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = effectiveType,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            when (effectiveType) {
                "PIE" -> PieChartCanvas(displaySeries.firstOrNull())
                "LINE" -> LineChartCanvas(displaySeries)
                else -> BarChartCanvas(displaySeries)
            }
        }
    }
}

@Composable
fun PieChartCanvas(series: ChartSeries?) {
    if (series == null || series.values.isEmpty()) return
    val total = series.values.sum().takeIf { it > 0 } ?: return
    val colors = listOf(
        Color(0xFF4285F4),
        Color(0xFF34A853),
        Color(0xFFFBBC04),
        Color(0xFFEA4335),
        Color(0xFF9C27B0),
        Color(0xFF00BCD4)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(140.dp)) {
                var startAngle = -90f
                val radius = size.minDimension * 0.45f
                val cx = size.width / 2f
                val cy = size.height / 2f

                series.values.forEachIndexed { i, value ->
                    val sweep = (value / total * 360f).toFloat()
                    drawArc(
                        color = colors[i % colors.size],
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = androidx.compose.ui.geometry.Offset(cx - radius, cy - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                    )
                    startAngle += sweep
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1.2f)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            series.values.forEachIndexed { i, value ->
                val label = series.labels.getOrNull(i) ?: "Item ${i + 1}"
                val pct = ((value / total) * 100).toInt()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(colors[i % colors.size], androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "$pct%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun BarChartCanvas(seriesList: List<ChartSeries>) {
    val primarySeries = seriesList.firstOrNull() ?: return
    if (primarySeries.values.isEmpty()) return
    val maxValue = primarySeries.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    val barColors = listOf(
        Color(0xFF107C41), // Excel Green
        Color(0xFF4285F4),
        Color(0xFFFBBC04),
        Color(0xFFEA4335),
        Color(0xFF9C27B0)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        // Bar Chart Graphic Canvas with Bars & Values
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            primarySeries.values.forEachIndexed { idx, value ->
                val ratio = (value / maxValue).toFloat().coerceIn(0.08f, 1.0f)
                val label = primarySeries.labels.getOrNull(idx) ?: "C${idx + 1}"
                val barColor = barColors[idx % barColors.size]

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Text(
                        text = if (value % 1.0 == 0.0) "${value.toInt()}" else String.format("%.1f", value),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = barColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .fillMaxHeight(ratio * 0.82f)
                            .background(
                                color = barColor,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                            )
                    )
                }
            }
        }

        // X-Axis Baseline
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            thickness = 1.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )

        // X-Axis Labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            primarySeries.values.forEachIndexed { idx, _ ->
                val label = primarySeries.labels.getOrNull(idx) ?: "Item ${idx + 1}"
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun LineChartCanvas(seriesList: List<ChartSeries>) {
    val primarySeries = seriesList.firstOrNull() ?: return
    if (primarySeries.values.size < 2) return
    val maxValue = primarySeries.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    val lineColor = Color(0xFF107C41)

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val chartWidth = size.width
                val chartHeight = size.height
                val xStep = chartWidth / (primarySeries.values.size - 1).coerceAtLeast(1)

                val path = Path()
                primarySeries.values.forEachIndexed { i, value ->
                    val x = i * xStep
                    val y = chartHeight - ((value / maxValue).toFloat() * chartHeight * 0.85f)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx()))

                // Draw node dots
                primarySeries.values.forEachIndexed { i, value ->
                    val x = i * xStep
                    val y = chartHeight - ((value / maxValue).toFloat() * chartHeight * 0.85f)
                    drawCircle(Color.White, radius = 5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
                    drawCircle(lineColor, radius = 3.5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            thickness = 1.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            primarySeries.values.forEachIndexed { idx, _ ->
                val label = primarySeries.labels.getOrNull(idx) ?: "${idx + 1}"
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun EmptyWorkbookState() {
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
            text = "No Sheets Detected",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/**
 * Excel column index base-26 alpha converter (e.g. 0 -> A, 27 -> AB).
 */
private fun getColHeaderString(index: Int): String {
    var temp = index
    val sb = StringBuilder()
    while (temp >= 0) {
        sb.insert(0, ('A'.code + (temp % 26)).toChar())
        temp = (temp / 26) - 1
    }
    return sb.toString()
}

private class XlsxPrintDocumentAdapter(private val context: Context, private val file: File) : PrintDocumentAdapter() {
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

@Composable
fun RowStatisticsBar(
    selectedRow: Int,
    numericValues: List<Double>,
    onClose: () -> Unit
) {
    if (numericValues.isEmpty()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Row ${selectedRow + 1}: No numeric values selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        return
    }

    val sum = numericValues.sum()
    val avg = numericValues.average()
    val count = numericValues.size
    val min = numericValues.minOrNull() ?: 0.0
    val max = numericValues.maxOrNull() ?: 0.0

    fun formatDouble(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.2f", value)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Row ${selectedRow + 1} Stats",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val stats = listOf(
                    "SUM" to formatDouble(sum),
                    "AVERAGE" to formatDouble(avg),
                    "COUNT" to count.toString(),
                    "MIN" to formatDouble(min),
                    "MAX" to formatDouble(max)
                )
                stats.forEach { (label, valStr) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            text = valStr,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpreadsheetWebView(
    xlsxBase64: String,
    workbook: ExcelWorkbook? = null,
    searchQuery: String,
    currentMatchIndex: Int,
    onCellSelected: (sheetIndex: Int, row: Int, col: Int, text: String, formula: String) -> Unit,
    onCellEdited: (sheetIndex: Int, row: Int, col: Int, newValue: String) -> Unit,
    onSheetChanged: (sheetIndex: Int) -> Unit,
    onWebViewReady: (WebView) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isPageLoaded by remember { mutableStateOf(false) }

    val workbookJson = remember(workbook) {
        if (workbook != null) {
            try {
                val root = org.json.JSONObject()
                val sheetsArr = org.json.JSONArray()
                workbook.sheets.forEach { sheet ->
                    val sObj = org.json.JSONObject()
                    sObj.put("name", sheet.name)

                    val colsArr = org.json.JSONArray()
                    sheet.columnWidthsDp.forEach { colsArr.put(it) }
                    sObj.put("columnWidths", colsArr)

                    val rowsHeightsArr = org.json.JSONArray()
                    sheet.rowHeightsDp.forEach { rowsHeightsArr.put(it) }
                    sObj.put("rowHeights", rowsHeightsArr)

                    val rowsArr = org.json.JSONArray()
                    sheet.rows.forEach { rowCells ->
                        val rArr = org.json.JSONArray()
                        rowCells.forEach { cell ->
                            val cObj = org.json.JSONObject()
                            cObj.put("text", cell.text)
                            if (cell.formulaString != null) cObj.put("formula", cell.formulaString)
                            if (cell.isBold) cObj.put("bold", true)
                            if (cell.isItalic) cObj.put("italic", true)
                            if (cell.isUnderline) cObj.put("underline", true)
                            if (cell.fontSizePt > 0 && cell.fontSizePt != 10) cObj.put("fontSize", cell.fontSizePt)
                            if (cell.colorHex != null) cObj.put("bgColor", cell.colorHex)
                            if (cell.textColorHex != null) cObj.put("textColor", cell.textColorHex)
                            if (cell.horizontalAlign != "LEFT") cObj.put("align", cell.horizontalAlign)
                            if (cell.mergeRowSpan > 1) cObj.put("rowSpan", cell.mergeRowSpan)
                            if (cell.mergeColSpan > 1) cObj.put("colSpan", cell.mergeColSpan)
                            rArr.put(cObj)
                        }
                        rowsArr.put(rArr)
                    }
                    sObj.put("rows", rowsArr)

                    val chartsArr = org.json.JSONArray()
                    sheet.charts.forEach { c ->
                        val cObj = org.json.JSONObject()
                        cObj.put("title", c.title)
                        cObj.put("chartType", c.chartType)
                        cObj.put("anchorRow", c.anchorRow)
                        cObj.put("anchorCol", c.anchorCol)
                        val sArr = org.json.JSONArray()
                        c.series.forEach { s ->
                            val sObj = org.json.JSONObject()
                            sObj.put("name", s.name)
                            val lArr = org.json.JSONArray()
                            s.labels.forEach { lArr.put(it) }
                            sObj.put("labels", lArr)
                            val vArr = org.json.JSONArray()
                            s.values.forEach { vArr.put(it) }
                            sObj.put("values", vArr)
                            sArr.put(sObj)
                        }
                        cObj.put("series", sArr)
                        chartsArr.put(cObj)
                    }
                    sObj.put("charts", chartsArr)

                    val imagesArr = org.json.JSONArray()
                    sheet.images.forEach { img ->
                        try {
                            val f = java.io.File(img.filePath)
                            if (f.exists() && f.length() > 0) {
                                val bytes = f.readBytes()
                                val mime = if (img.filePath.endsWith(".jpg", true) || img.filePath.endsWith(".jpeg", true)) "image/jpeg" else "image/png"
                                val dataUrl = "data:$mime;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                val imgObj = org.json.JSONObject().apply {
                                    put("dataUrl", dataUrl)
                                    put("fromRow", img.fromRow)
                                    put("fromCol", img.fromCol)
                                    put("colSpan", img.colSpan)
                                    put("rowSpan", img.rowSpan)
                                }
                                imagesArr.put(imgObj)
                            }
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        }
                    }
                    sObj.put("images", imagesArr)

                    sheetsArr.put(sObj)
                }
                root.put("sheets", sheetsArr)
                root.toString()
            } catch (e: Exception) {
                null
            }
        } else null
    }

    LaunchedEffect(workbookJson, xlsxBase64, isPageLoaded) {
        if (isPageLoaded && webViewInstance != null) {
            if (workbookJson != null) {
                val escaped = workbookJson.replace("\\", "\\\\").replace("'", "\\'")
                webViewInstance?.evaluateJavascript("renderSpreadsheetData('$escaped', '$xlsxBase64')", null)
            } else {
                webViewInstance?.evaluateJavascript("renderSpreadsheetBase64('$xlsxBase64')", null)
            }
        }
    }

    LaunchedEffect(searchQuery, isPageLoaded) {
        if (isPageLoaded && webViewInstance != null) {
            val escaped = searchQuery
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", " ")
                .replace("\r", "")
            webViewInstance?.evaluateJavascript("searchSpreadsheet('$escaped')", null)
        }
    }

    var lastMatchIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(currentMatchIndex, isPageLoaded) {
        if (isPageLoaded && webViewInstance != null && currentMatchIndex != lastMatchIndex) {
            if (currentMatchIndex > lastMatchIndex) {
                webViewInstance?.evaluateJavascript("nextSearchMatch()", null)
            } else if (currentMatchIndex < lastMatchIndex && currentMatchIndex >= 0) {
                webViewInstance?.evaluateJavascript("prevSearchMatch()", null)
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
                    builtInZoomControls = false
                    displayZoomControls = false
                    useWideViewPort = false
                    loadWithOverviewMode = false
                    setSupportZoom(false)
                }
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false

                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onCellSelected(sheetIndex: Int, row: Int, col: Int, text: String, formula: String) {
                        onCellSelected(sheetIndex, row, col, text, formula)
                    }

                    @JavascriptInterface
                    fun onCellEdited(sheetIndex: Int, row: Int, col: Int, newValue: String) {
                        onCellEdited(sheetIndex, row, col, newValue)
                    }

                    @JavascriptInterface
                    fun onSheetChanged(sheetIndex: Int) {
                        onSheetChanged(sheetIndex)
                    }

                    @JavascriptInterface
                    fun onRenderComplete(sheetCount: Int) {}
                }, "AndroidBridge")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        isPageLoaded = true
                        webViewInstance = this@apply
                        onWebViewReady(this@apply)
                        if (workbookJson != null) {
                            val escaped = workbookJson.replace("\\", "\\\\").replace("'", "\\'")
                            evaluateJavascript("renderSpreadsheetData('$escaped', '$xlsxBase64')", null)
                        } else {
                            evaluateJavascript("renderSpreadsheetBase64('$xlsxBase64')", null)
                        }
                    }
                }

                loadUrl("file:///android_asset/spreadsheet_viewer/viewer.html")
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

