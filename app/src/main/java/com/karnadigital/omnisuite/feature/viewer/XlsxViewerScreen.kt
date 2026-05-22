package com.karnadigital.omnisuite.feature.viewer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.hilt.navigation.compose.hiltViewModel

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
    var showBottomSheet by remember { mutableStateOf(false) }
    var bottomSheetValue by remember { mutableStateOf("") }

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
    
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
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

    LaunchedEffect(state) {
        if (state is XlsxLoadState.Success) {
            activeSheetIndex = 0
            selectedCell = null
        }
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
                            // Aligned Scrollable Grid Container
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                             ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .horizontalScroll(horizontalScrollState)
                                ) {
                                    LazyColumn(
                                        state = lazyListState,
                                        modifier = Modifier.fillMaxHeight(),
                                        contentPadding = PaddingValues(bottom = 16.dp)
                                    ) {
                                        // 1. Column headers index (A, B, C...)
                                        item {
                                            Row(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                                                HeaderCell("", isIntersection = true)
                                                val colCount = if (activeSheet.rows.isNotEmpty()) activeSheet.rows[0].size else 0
                                                for (c in 0 until colCount) {
                                                    HeaderCell(getColHeaderString(c))
                                                }
                                            }
                                        }

                                        // 2. Row cells
                                        items(activeSheet.rows.size) { rowIndex ->
                                            val rowCells = activeSheet.rows[rowIndex]
                                            Row {
                                                // Row Number Header Index
                                                HeaderCell((rowIndex + 1).toString(), isRowHeader = true)
                                                
                                                // Data Row Values
                                                rowCells.forEachIndexed { colIndex, cellText ->
                                                    val isSelected = selectedCell?.rowIndex == rowIndex && selectedCell?.colIndex == colIndex
                                                    val isSearchResult = searchResults.getOrNull(currentMatchIndex)?.let { match ->
                                                        match.pageIndex == activeSheetIndex &&
                                                        match.extraData?.split(",")?.let { parts ->
                                                            parts.size == 2 && parts[0].toInt() == rowIndex && parts[1].toInt() == colIndex
                                                        } ?: false
                                                    } ?: false
                                                    DataCell(
                                                        text = cellText,
                                                        isSelected = isSelected,
                                                        isSearchResult = isSearchResult,
                                                        onClick = {
                                                            selectedCell = CellCoords(rowIndex, colIndex)
                                                            bottomSheetValue = cellText
                                                            showBottomSheet = true
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. Multi-Sheet Footer Selector TabRow
                            if (workbook.sheets.size > 1) {
                                Surface(
                                    tonalElevation = 3.dp,
                                    shadowElevation = 3.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    ScrollableTabRow(
                                        selectedTabIndex = activeSheetIndex,
                                        edgePadding = 16.dp,
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        workbook.sheets.forEachIndexed { index, sheet ->
                                            Tab(
                                                selected = activeSheetIndex == index,
                                                onClick = { 
                                                    activeSheetIndex = index 
                                                    selectedCell = null // Clear selection when sheet changes
                                                },
                                                text = {
                                                    Text(
                                                        text = sheet.name,
                                                        fontWeight = if (activeSheetIndex == index) FontWeight.Bold else FontWeight.Normal,
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
        val cellName = "${getColHeaderString(cell.colIndex)}${cell.rowIndex + 1}"
        
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Edit Cell $cellName",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = bottomSheetValue,
                    onValueChange = { bottomSheetValue = it },
                    label = { Text("Cell Content") },
                    placeholder = { Text("Enter text, formulas, or numbers...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                                valueString = bottomSheetValue
                            )
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
    isRowHeader: Boolean = false
) {
    Box(
        modifier = Modifier
            .width(if (isIntersection || isRowHeader) 54.dp else 120.dp)
            .height(32.dp)
            .background(
                if (isIntersection) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun DataCell(
    text: String,
    isSelected: Boolean,
    isSearchResult: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(120.dp)
            .height(32.dp)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else if (isSearchResult) Color(0xFFFFF59D)
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = if (isSelected || isSearchResult) 1.5.dp else 0.5.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else if (isSearchResult) Color(0xFFFBC02D)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else if (isSearchResult) Color(0xFF212121)
            else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected || isSearchResult) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
