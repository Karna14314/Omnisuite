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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
    viewModel: XlsxViewerViewModel = hiltViewModel()
) {
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
    val context = LocalContext.current
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
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search"
                                )
                            }

                            IconButton(onClick = { viewModel.commitChanges() }) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Commit changes to disk",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More Options"
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Export to PDF") },
                                    onClick = {
                                        showMenu = false
                                        val currentSuccess = state as XlsxLoadState.Success
                                        val defaultName = currentSuccess.fileName.substringBeforeLast(".") + ".pdf"
                                        exportPdfLauncher.launch(defaultName)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.PictureAsPdf,
                                            contentDescription = "PDF Export"
                                        )
                                    }
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
                        XlsxActionColumnButton(icon = Icons.Default.OpenInNew, title = "Open in...") {
                            try {
                                val file = File(fileUri)
                                val fileUriProvider = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(fileUriProvider, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(openIntent, "Open Spreadsheet In"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }

                        XlsxActionColumnButton(icon = Icons.Default.Print, title = "Print") {
                            coroutineScope.launch {
                                val tempPdfFile = File(context.cacheDir, "temp_print_${System.currentTimeMillis()}.pdf")
                                try {
                                    withContext(Dispatchers.IO) {
                                        officeConverter.convertXlsxToPdf(File(fileUri), tempPdfFile)
                                    }
                                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
                                    val jobName = "OmniSuite Spreadsheet Print"
                                    printManager.print(
                                        jobName,
                                        XlsxPrintDocumentAdapter(context, tempPdfFile),
                                        null
                                    )
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Print failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }

                        XlsxActionColumnButton(icon = Icons.Default.Share, title = "Share") {
                            try {
                                val file = File(fileUri)
                                val fileUriProvider = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                    putExtra(Intent.EXTRA_STREAM, fileUriProvider)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Spreadsheet"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }

                        var showQuickToolsMenu by remember { mutableStateOf(false) }
                        Box {
                            XlsxActionColumnButton(icon = Icons.Default.Build, title = "Quick Tools") {
                                showQuickToolsMenu = true
                            }
                            DropdownMenu(
                                expanded = showQuickToolsMenu,
                                onDismissRequest = { showQuickToolsMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("📕 Convert to PDF format") },
                                    onClick = {
                                        showQuickToolsMenu = false
                                        val currentSuccess = state as XlsxLoadState.Success
                                        val defaultName = currentSuccess.fileName.substringBeforeLast(".") + ".pdf"
                                        exportPdfLauncher.launch(defaultName)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("💾 Save Changes to Disk") },
                                    onClick = {
                                        showQuickToolsMenu = false
                                        viewModel.commitChanges()
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
                                    }
                                }
                            )

                            // Sort Filter Bar
                            if (selectedColForSort != null) {
                                val colLabel = getColHeaderString(selectedColForSort!!)
                                SortFilterBar(
                                    selectedColIndex = selectedColForSort,
                                    colLabel = colLabel,
                                    onSortAscending = {
                                        viewModel.sortByColumn(activeSheetIndex, selectedColForSort!!, true)
                                        selectedColForSort = null
                                    },
                                    onSortDescending = {
                                        viewModel.sortByColumn(activeSheetIndex, selectedColForSort!!, false)
                                        selectedColForSort = null
                                    },
                                    onDismiss = { selectedColForSort = null }
                                )
                            }

                            // Aligned Scrollable Grid Container
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                val actualFrozenRows = activeSheet.frozenRowCount.coerceAtMost(activeSheet.rows.size)
                                val actualFrozenCols = activeSheet.frozenColCount.coerceAtMost(if (activeSheet.rows.isNotEmpty()) activeSheet.rows[0].size else 0)

                                ZoomableDataGrid(
                                    scale = scale,
                                    onScaleChange = { scale = it },
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        // 1. Column headers index (A, B, C...) - pinned vertically, scrolls horizontally
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            HeaderCell("", isIntersection = true, scale = scale)
                                            
                                            // Frozen column headers
                                            if (actualFrozenCols > 0) {
                                                Row {
                                                    for (c in 0 until actualFrozenCols) {
                                                        ColumnHeaderCell(
                                                            colIndex = c,
                                                            text = getColHeaderString(c),
                                                            widthDp = activeSheet.columnWidthsDp.getOrElse(c) { 80f } * scale,
                                                            isSelected = selectedColForSort == c,
                                                            scale = scale,
                                                            onSelect = {
                                                                selectedColForSort = c
                                                                selectedRow = null  // clear row selection on col tap
                                                            },
                                                            onResize = { newWidth -> viewModel.setColumnWidth(activeSheetIndex, c, newWidth / scale) },
                                                            onContextAction = { action ->
                                                                when (action) {
                                                                    "INSERT_LEFT" -> viewModel.insertColumn(activeSheetIndex, c, left = true)
                                                                    "INSERT_RIGHT" -> viewModel.insertColumn(activeSheetIndex, c, left = false)
                                                                    "DELETE" -> viewModel.deleteColumn(activeSheetIndex, c)
                                                                    "BEST_FIT" -> viewModel.setColumnBestFit(activeSheetIndex, c)
                                                                }
                                                            }
                                                        )
                                                    }
                                                }
                                            }

                                            // Scrollable column headers
                                            Box(
                                                modifier = Modifier.horizontalScroll(horizontalScrollState)
                                            ) {
                                                Row {
                                                    val colCount = if (activeSheet.rows.isNotEmpty()) activeSheet.rows[0].size else 0
                                                     for (c in actualFrozenCols until colCount) {
                                                         ColumnHeaderCell(
                                                             colIndex = c,
                                                             text = getColHeaderString(c),
                                                             widthDp = activeSheet.columnWidthsDp.getOrElse(c) { 80f } * scale,
                                                             isSelected = selectedColForSort == c,
                                                             scale = scale,
                                                             onSelect = {
                                                                 selectedColForSort = c
                                                                 selectedRow = null  // clear row selection on col tap
                                                             },
                                                             onResize = { newWidth -> viewModel.setColumnWidth(activeSheetIndex, c, newWidth / scale) },
                                                             onContextAction = { action ->
                                                                 when (action) {
                                                                     "INSERT_LEFT" -> viewModel.insertColumn(activeSheetIndex, c, left = true)
                                                                     "INSERT_RIGHT" -> viewModel.insertColumn(activeSheetIndex, c, left = false)
                                                                     "DELETE" -> viewModel.deleteColumn(activeSheetIndex, c)
                                                                     "BEST_FIT" -> viewModel.setColumnBestFit(activeSheetIndex, c)
                                                                 }
                                                             }
                                                         )
                                                     }
                                                }
                                            }
                                        }

                                        // 2. Frozen Rows Block (does not scroll vertically, scrolls horizontally)
                                        if (actualFrozenRows > 0) {
                                            Column {
                                                for (r in 0 until actualFrozenRows) {
                                                    val rowCells = activeSheet.rows.getOrNull(r) ?: emptyList()
                                                    val rowHeight = activeSheet.rowHeightsDp.getOrNull(r) ?: 24f
                                                    Row(modifier = Modifier.fillMaxWidth()) {
                                                        RowHeaderCell(
                                                            rowIndex = r,
                                                            text = (r + 1).toString(),
                                                            heightDp = rowHeight * scale,
                                                            isSelected = selectedRow == r,
                                                            scale = scale,
                                                            onSelect = {
                                                                selectedRow = r
                                                                selectedCell = null  // clear cell selection when row selected
                                                            },
                                                            onResize = { newHeight -> viewModel.setRowHeight(activeSheetIndex, r, newHeight / scale) },
                                                            onContextAction = { action ->
                                                                when (action) {
                                                                    "INSERT_ABOVE" -> viewModel.insertRow(activeSheetIndex, r, above = true)
                                                                    "INSERT_BELOW" -> viewModel.insertRow(activeSheetIndex, r, above = false)
                                                                    "DELETE" -> viewModel.deleteRow(activeSheetIndex, r)
                                                                }
                                                            }
                                                        )
                                                        if (actualFrozenCols > 0) {
                                                            Row {
                                                                for (c in 0 until actualFrozenCols) {
                                                                    val cellData = rowCells.getOrNull(c) ?: CellData("")
                                                                    val isSelected = selectedCell?.rowIndex == r && selectedCell?.colIndex == c
                                                                    val isRowSelected = selectedRow == r
                                                                    val isSearchResult = searchResults.getOrNull(currentMatchIndex)?.let { match ->
                                                                        match.pageIndex == activeSheetIndex &&
                                                                        match.extraData?.split(",")?.let { parts ->
                                                                            parts.size == 2 && parts[0].toInt() == r && parts[1].toInt() == c
                                                                        } ?: false
                                                                    } ?: false
                                                                    DataCell(
                                                                        cellData = cellData,
                                                                        colWidthDp = activeSheet.columnWidthsDp.getOrElse(c) { 80f } * scale,
                                                                        rowHeightDp = rowHeight * scale,
                                                                        isSelected = isSelected,
                                                                        isRowSelected = isRowSelected,
                                                                        isSearchResult = isSearchResult,
                                                                        scale = scale,
                                                                        onClick = {
                                                                            selectedCell = CellCoords(r, c)
                                                                            selectedCellData = cellData
                                                                            bottomSheetValue = cellData.text
                                                                            showBottomSheet = true
                                                                        },
                                                                        onUpdateValue = { newValue ->
                                                                            viewModel.updateCell(activeSheetIndex, r, c, newValue)
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        Box(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                                                            Row {
                                                                for (c in actualFrozenCols until rowCells.size) {
                                                                    val cellData = rowCells.getOrNull(c) ?: CellData("")
                                                                    val isSelected = selectedCell?.rowIndex == r && selectedCell?.colIndex == c
                                                                    val isRowSelected = selectedRow == r
                                                                    val isSearchResult = searchResults.getOrNull(currentMatchIndex)?.let { match ->
                                                                        match.pageIndex == activeSheetIndex &&
                                                                        match.extraData?.split(",")?.let { parts ->
                                                                            parts.size == 2 && parts[0].toInt() == r && parts[1].toInt() == c
                                                                        } ?: false
                                                                    } ?: false
                                                                    DataCell(
                                                                        cellData = cellData,
                                                                        colWidthDp = activeSheet.columnWidthsDp.getOrElse(c) { 80f } * scale,
                                                                        rowHeightDp = rowHeight * scale,
                                                                        isSelected = isSelected,
                                                                        isRowSelected = isRowSelected,
                                                                        isSearchResult = isSearchResult,
                                                                        scale = scale,
                                                                        onClick = {
                                                                            selectedCell = CellCoords(r, c)
                                                                            selectedCellData = cellData
                                                                            bottomSheetValue = cellData.text
                                                                            showBottomSheet = true
                                                                        },
                                                                        onUpdateValue = { newValue ->
                                                                            viewModel.updateCell(activeSheetIndex, r, c, newValue)
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // 3. Scrollable Rows Block (LazyColumn)
                                        LazyColumn(
                                            state = lazyListState,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                            contentPadding = PaddingValues(bottom = 16.dp)
                                        ) {
                                            items(activeSheet.rows.size - actualFrozenRows) { index ->
                                                val rowIndex = index + actualFrozenRows
                                                val rowCells = activeSheet.rows[rowIndex]
                                                val rowHeight = activeSheet.rowHeightsDp.getOrElse(rowIndex) { 24f }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    // Row Number Header Index - pinned horizontally
                                                    RowHeaderCell(
                                                        rowIndex = rowIndex,
                                                        text = (rowIndex + 1).toString(),
                                                        heightDp = rowHeight * scale,
                                                        isSelected = selectedRow == rowIndex,
                                                        scale = scale,
                                                        onSelect = {
                                                            selectedRow = rowIndex
                                                            selectedCell = null  // clear cell selection when row selected
                                                        },
                                                        onResize = { newHeight -> viewModel.setRowHeight(activeSheetIndex, rowIndex, newHeight / scale) },
                                                        onContextAction = { action ->
                                                            when (action) {
                                                                "INSERT_ABOVE" -> viewModel.insertRow(activeSheetIndex, rowIndex, above = true)
                                                                "INSERT_BELOW" -> viewModel.insertRow(activeSheetIndex, rowIndex, above = false)
                                                                "DELETE" -> viewModel.deleteRow(activeSheetIndex, rowIndex)
                                                            }
                                                        }
                                                    )
                                                    
                                                    // Frozen column data cells in this row
                                                    if (actualFrozenCols > 0) {
                                                        Row {
                                                            for (c in 0 until actualFrozenCols) {
                                                                val cellData = rowCells.getOrNull(c) ?: CellData("")
                                                                val isSelected = selectedCell?.rowIndex == rowIndex && selectedCell?.colIndex == c
                                                                val isRowSelected = selectedRow == rowIndex
                                                                val isSearchResult = searchResults.getOrNull(currentMatchIndex)?.let { match ->
                                                                    match.pageIndex == activeSheetIndex &&
                                                                    match.extraData?.split(",")?.let { parts ->
                                                                        parts.size == 2 && parts[0].toInt() == rowIndex && parts[1].toInt() == c
                                                                    } ?: false
                                                                } ?: false
                                                                DataCell(
                                                                    cellData = cellData,
                                                                    colWidthDp = activeSheet.columnWidthsDp.getOrElse(c) { 80f } * scale,
                                                                    rowHeightDp = rowHeight * scale,
                                                                    isSelected = isSelected,
                                                                    isRowSelected = isRowSelected,
                                                                    isSearchResult = isSearchResult,
                                                                    scale = scale,
                                                                    onClick = {
                                                                        selectedCell = CellCoords(rowIndex, c)
                                                                        selectedCellData = cellData
                                                                        bottomSheetValue = cellData.text
                                                                        showBottomSheet = true
                                                                    },
                                                                    onUpdateValue = { newValue ->
                                                                        viewModel.updateCell(activeSheetIndex, rowIndex, c, newValue)
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }

                                                    // Data Row Cells - scrolls horizontally in sync
                                                    Box(
                                                        modifier = Modifier.horizontalScroll(horizontalScrollState)
                                                    ) {
                                                        Row {
                                                            for (colIndex in actualFrozenCols until rowCells.size) {
                                                                val cellData = rowCells[colIndex]
                                                                val isSelected = selectedCell?.rowIndex == rowIndex && selectedCell?.colIndex == colIndex
                                                                val isRowSelected = selectedRow == rowIndex
                                                                val isSearchResult = searchResults.getOrNull(currentMatchIndex)?.let { match ->
                                                                    match.pageIndex == activeSheetIndex &&
                                                                    match.extraData?.split(",")?.let { parts ->
                                                                        parts.size == 2 && parts[0].toInt() == rowIndex && parts[1].toInt() == colIndex
                                                                    } ?: false
                                                                } ?: false
                                                                DataCell(
                                                                    cellData = cellData,
                                                                    colWidthDp = activeSheet.columnWidthsDp.getOrElse(colIndex) { 80f } * scale,
                                                                    rowHeightDp = rowHeight * scale,
                                                                    isSelected = isSelected,
                                                                    isRowSelected = isRowSelected,
                                                                    isSearchResult = isSearchResult,
                                                                    scale = scale,
                                                                    onClick = {
                                                                        selectedCell = CellCoords(rowIndex, colIndex)
                                                                        selectedCellData = cellData
                                                                        bottomSheetValue = cellData.text
                                                                        showBottomSheet = true
                                                                    },
                                                                    onUpdateValue = { newValue ->
                                                                        viewModel.updateCell(activeSheetIndex, rowIndex, colIndex, newValue)
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // Draw sheet charts and embedded images below the grid
                                            if (activeSheet.charts.isNotEmpty() || activeSheet.images.isNotEmpty()) {
                                                item {
                                                    Spacer(modifier = Modifier.height(24.dp))
                                                    Text(
                                                        text = "Visualizations & Images (${activeSheet.charts.size + activeSheet.images.size})",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                                    )
                                                }
                                                items(activeSheet.charts.size) { chartIndex ->
                                                    val chart = activeSheet.charts[chartIndex]
                                                    SheetChartView(chart)
                                                }
                                                items(activeSheet.images.size) { imageIndex ->
                                                    val img = activeSheet.images[imageIndex]
                                                    SheetImageView(img)
                                                }
                                            }
                                        }
                                    }
                                }
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

        // Sleek Right-Edge Resize Handle with generous 24dp touch target
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 12.dp)
                .width(24.dp)
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
                        .width(1.5.dp)
                        .fillMaxHeight(0.65f)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
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

        // Sleek Bottom-Edge Resize Handle with generous 24dp touch target
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 12.dp)
                .fillMaxWidth()
                .height(24.dp)
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
                        .height(1.5.dp)
                        .fillMaxWidth(0.65f)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
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

    val totalWidth = colWidthDp * cellData.mergeColSpan + (cellData.mergeColSpan - 1) * 0.5f  // include borders
    val totalHeight = rowHeightDp * cellData.mergeRowSpan + (cellData.mergeRowSpan - 1) * 0.5f

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
        contentAlignment = when (cellData.horizontalAlign) {
            "CENTER" -> Alignment.Center
            "RIGHT" -> Alignment.CenterEnd
            else -> Alignment.CenterStart
        }
    ) {
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
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                // Only intercept 2-finger pinch — let 1-finger scroll pass through
                awaitEachGesture {
                    var event = awaitPointerEvent(pass = PointerEventPass.Initial)
                    while (event.changes.any { it.pressed }) {
                        if (event.changes.size >= 2) {
                            val zoom = event.calculateZoom()
                            onScaleChange((scale * zoom).coerceIn(0.5f, 4f))
                            event.changes.forEach { it.consume() }
                        }
                        event = awaitPointerEvent(pass = PointerEventPass.Initial)
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
    val isEmpty = chart.series.isEmpty() || chart.series.all { it.values.isEmpty() }
    
    // Create local series representation
    val displaySeries = if (isEmpty) {
        // Generate mock data for beautiful preview
        when (chart.chartType) {
            "PIE" -> listOf(
                ChartSeries(
                    name = "Mock Series",
                    values = listOf(35.0, 25.0, 20.0, 15.0, 5.0),
                    labels = listOf("Q1 Sales", "Q2 Sales", "Q3 Sales", "Q4 Sales", "Other")
                )
            )
            "BAR", "LINE" -> listOf(
                ChartSeries(
                    name = "Mock Target",
                    values = listOf(40.0, 55.0, 70.0, 65.0, 85.0, 90.0),
                    labels = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun")
                ),
                ChartSeries(
                    name = "Mock Actual",
                    values = listOf(30.0, 60.0, 65.0, 75.0, 80.0, 95.0),
                    labels = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun")
                )
            )
            else -> listOf(
                ChartSeries(
                    name = "Mock Series",
                    values = listOf(10.0, 20.0, 30.0, 40.0),
                    labels = listOf("A", "B", "C", "D")
                )
            )
        }
    } else {
        chart.series
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Text(
                    text = chart.title.ifBlank { "Chart Preview" } + (if (isEmpty) " (Mock Preview)" else ""),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isEmpty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when (chart.chartType) {
                "PIE" -> PieChartCanvas(displaySeries.firstOrNull())
                "BAR" -> BarChartCanvas(displaySeries)
                "LINE" -> LineChartCanvas(displaySeries)
                else -> {
                    Text(
                        "Chart: ${chart.chartType} (${displaySeries.size} series)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PieChartCanvas(series: ChartSeries?) {
    if (series == null || series.values.isEmpty()) return
    val total = series.values.sum().takeIf { it > 0 } ?: return
    val colors = listOf(Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFFFBBC04), Color(0xFF34A853), Color(0xFFFF6D00), Color(0xFF46BDC6))

    Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            var startAngle = -90f
            val radius = size.minDimension * 0.4f
            val cx = size.width * 0.38f
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
        // Legend
        Column(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
            series.labels.take(6).forEachIndexed { i, label ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).background(colors[i % colors.size], androidx.compose.foundation.shape.RoundedCornerShape(2.dp)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(label.take(12), style = MaterialTheme.typography.bodySmall, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
fun BarChartCanvas(seriesList: List<ChartSeries>) {
    if (seriesList.isEmpty() || seriesList.all { it.values.isEmpty() }) return
    val maxValue = seriesList.flatMap { it.values }.maxOrNull() ?: return
    val barColors = listOf(Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFFFBBC04), Color(0xFF34A853))
    val labelCount = seriesList.firstOrNull()?.labels?.size ?: seriesList.firstOrNull()?.values?.size ?: 0

    Canvas(modifier = Modifier.fillMaxWidth().height(160.dp).padding(horizontal = 8.dp)) {
        val chartWidth = size.width
        val chartHeight = size.height - 20f
        val groupWidth = chartWidth / labelCount.coerceAtLeast(1)
        val barWidth = (groupWidth / (seriesList.size + 1)).coerceAtLeast(4f)

        seriesList.forEachIndexed { serIdx, series ->
            series.values.forEachIndexed { valIdx, value ->
                val barHeight = ((value / maxValue) * chartHeight).toFloat()
                val x = valIdx * groupWidth + serIdx * barWidth + barWidth * 0.5f
                val y = chartHeight - barHeight
                drawRect(
                    color = barColors[serIdx % barColors.size],
                    topLeft = androidx.compose.ui.geometry.Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(barWidth * 0.8f, barHeight)
                )
            }
        }
    }
}

@Composable
fun LineChartCanvas(seriesList: List<ChartSeries>) {
    if (seriesList.isEmpty() || seriesList.all { it.values.isEmpty() }) return
    val maxValue = seriesList.flatMap { it.values }.maxOrNull()?.coerceAtLeast(0.001) ?: return
    val lineColors = listOf(Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFFFBBC04), Color(0xFF34A853))

    Canvas(modifier = Modifier.fillMaxWidth().height(140.dp).padding(8.dp)) {
        val chartHeight = size.height
        val chartWidth = size.width

        seriesList.forEachIndexed { serIdx, series ->
            if (series.values.size < 2) return@forEachIndexed
            val path = Path()
            val xStep = chartWidth / (series.values.size - 1).coerceAtLeast(1)
            series.values.forEachIndexed { i, value ->
                val x = i * xStep
                val y = chartHeight - ((value / maxValue) * chartHeight).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, lineColors[serIdx % lineColors.size], style = Stroke(width = 2.dp.toPx()))
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

private @Composable
fun XlsxActionColumnButton(
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
