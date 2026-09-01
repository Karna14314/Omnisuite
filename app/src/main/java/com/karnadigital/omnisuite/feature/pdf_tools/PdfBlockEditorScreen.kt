package com.karnadigital.omnisuite.feature.pdf_tools

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.karnadigital.omnisuite.core.engine.PdfLayoutParser
import com.karnadigital.omnisuite.core.engine.PdfTextBlock
import com.karnadigital.omnisuite.core.engine.PdfPageLayout
import com.karnadigital.omnisuite.core.engine.BlockType
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.karnadigital.omnisuite.core.engine.PdfLayoutParser
import com.karnadigital.omnisuite.core.engine.PdfTextBlock
import com.karnadigital.omnisuite.core.engine.PdfPageLayout
import com.karnadigital.omnisuite.core.engine.BlockType
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfBlockEditorScreen(
    onBack: () -> Unit,
    viewModel: PdfBlockEditorViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val loadState by viewModel.loadState.collectAsState()
    var selectedBlock by remember { mutableStateOf<PdfTextBlock?>(null) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var showBlockEditor by remember { mutableStateOf(false) }
    var blockEditText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val cachedFile = viewModel.cacheUriToFile(context, it)
                if (cachedFile != null) {
                    viewModel.loadPdf(cachedFile)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (loadState) {
                            is PdfBlockLoadState.Success -> "Block Editor (${((loadState as PdfBlockLoadState.Success).pages.getOrNull(currentPageIndex)?.blocks?.size ?: 0)} blocks)"
                            else -> "PDF Block Editor"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (loadState is PdfBlockLoadState.Success) {
                        IconButton(onClick = { filePicker.launch(arrayOf("application/pdf")) }) {
                            Icon(Icons.Default.FileOpen, contentDescription = "Open PDF")
                        }
                        IconButton(onClick = {
                            scope.launch {
                                isProcessing = true
                                viewModel.saveEdits(context)
                                isProcessing = false
                                snackbarHostState.showSnackbar("PDF saved successfully!")
                            }
                        }, enabled = !isProcessing) {
                            if (isProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Save, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (loadState is PdfBlockLoadState.Success) {
                val state = loadState as PdfBlockLoadState.Success
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                            enabled = currentPageIndex > 0
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous page")
                        }
                        Text("Page ${currentPageIndex + 1} of ${state.pages.size}")
                        IconButton(
                            onClick = { if (currentPageIndex < state.pages.size - 1) currentPageIndex++ },
                            enabled = currentPageIndex < state.pages.size - 1
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next page")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (val state = loadState) {
                is PdfBlockLoadState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Parsing PDF layout...")
                    }
                }
                is PdfBlockLoadState.Success -> {
                    val page = state.pages.getOrNull(currentPageIndex)
                    if (page != null) {
                        PdfBlockEditorCanvas(
                            page = page,
                            pageBitmap = state.pageBitmaps.getOrNull(currentPageIndex),
                            selectedBlock = selectedBlock,
                            onBlockSelected = { block ->
                                selectedBlock = block
                                blockEditText = block.displayText
                                showBlockEditor = true
                            }
                        )
                    }
                }
                is PdfBlockLoadState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Error loading PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(state.message, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { filePicker.launch(arrayOf("application/pdf")) }) {
                            Text("Open PDF")
                        }
                    }
                }
                is PdfBlockLoadState.Idle -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("PDF Block Editor", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Edit PDF text block-by-block with layout parsing, font matching, and container-isolated reflow.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { filePicker.launch(arrayOf("application/pdf")) }) {
                            Icon(Icons.Default.FileOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open PDF")
                        }
                    }
                }
            }
        }
    }

    // Block Editor Bottom Sheet
    if (showBlockEditor && selectedBlock != null) {
        ModalBottomSheet(
            onDismissRequest = { showBlockEditor = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Edit Block", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Type: ${selectedBlock!!.blockType} | Font: ${selectedBlock!!.fontFamily} ${selectedBlock!!.fontSize}pt",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = blockEditText,
                    onValueChange = { blockEditText = it },
                    label = { Text("Block Text") },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    maxLines = 10
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { showBlockEditor = false }, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            selectedBlock?.let { block ->
                                viewModel.updateBlockText(block, blockEditText)
                            }
                            showBlockEditor = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

@Composable
fun PdfBlockEditorCanvas(
    page: PdfPageLayout,
    pageBitmap: Bitmap?,
    selectedBlock: PdfTextBlock?,
    onBlockSelected: (PdfTextBlock) -> Unit
) {
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    val scale = if (canvasSize.width > 0) canvasSize.width / page.pageWidth else 1f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .onGloballyPositioned { canvasSize = Size(it.size.width.toFloat(), it.size.height.toFloat()) }
    ) {
        // PDF Page Background
        pageBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "PDF Page",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(page.pageWidth / page.pageHeight),
                contentScale = ContentScale.Fit
            )
        }

        // Text Block Overlays
        page.blocks.forEach { block ->
            val isSelected = selectedBlock?.id == block.id
            val blockX = block.x * scale
            val blockY = block.y * scale
            val blockW = block.width * scale
            val blockH = block.height * scale

            Box(
                modifier = Modifier
                    .offset(x = blockX.dp, y = blockY.dp)
                    .size(width = blockW.dp, height = blockH.dp)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(2.dp)
                    )
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
                    )
                    .clickable { onBlockSelected(block) }
            ) {
                Text(
                    text = block.displayText,
                    fontSize = (block.fontSize * scale * 0.5f).sp,
                    color = androidx.compose.ui.graphics.Color(block.textColor),
                    fontWeight = if (block.fontWeight == "bold") FontWeight.Bold else FontWeight.Normal,
                    maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(2.dp)
                )
            }
        }
    }
}
