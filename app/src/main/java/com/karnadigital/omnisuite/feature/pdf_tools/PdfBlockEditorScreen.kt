package com.karnadigital.omnisuite.feature.pdf_tools

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.karnadigital.omnisuite.core.engine.PdfTextBlock
import com.karnadigital.omnisuite.core.util.ZoomableBox
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfBlockEditorScreen(
    onBack: () -> Unit,
    viewModel: PdfBlockEditorViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val loadState by viewModel.loadState.collectAsState()
    var selectedBlock by remember { mutableStateOf<PdfTextBlock?>(null) }
    var blockEditText by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val cachedFile = viewModel.cacheUriToFile(context, it)
                cachedFile?.let { file ->
                    viewModel.loadPdf(file)
                }
            }
        }
    }

    LaunchedEffect((loadState as? PdfBlockLoadState.Success)?.saveMessage) {
        val msg = (loadState as? PdfBlockLoadState.Success)?.saveMessage
        if (!msg.isNullOrBlank()) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF Block Editor", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (loadState is PdfBlockLoadState.Success) {
                        IconButton(onClick = { viewModel.saveEdits(context) }) {
                            Icon(Icons.Default.Save, contentDescription = "Save PDF", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { filePicker.launch(arrayOf("application/pdf")) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.EditNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Select PDF to Edit Blocks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        val fileName = (loadState as? PdfBlockLoadState.Success)?.originalFile?.name
                        if (fileName != null) {
                            Text(fileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        } else {
                            Text("Tap to pick document", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            when (val state = loadState) {
                is PdfBlockLoadState.Loading -> {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is PdfBlockLoadState.Success -> {
                    val pageLayout = state.pages.getOrNull(state.activePageIndex)
                    val bitmap = state.activePageBitmap

                    if (pageLayout != null && bitmap != null) {
                        // Page navigation header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.selectPage(state.activePageIndex - 1) },
                                enabled = state.activePageIndex > 0
                            ) {
                                Icon(Icons.Default.NavigateBefore, contentDescription = "Prev Page")
                            }

                            Text(
                                "Page ${state.activePageIndex + 1} of ${state.pages.size}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            IconButton(
                                onClick = { viewModel.selectPage(state.activePageIndex + 1) },
                                enabled = state.activePageIndex < state.pages.size - 1
                            ) {
                                Icon(Icons.Default.NavigateNext, contentDescription = "Next Page")
                            }
                        }

                        // Interactive viewport with Zoom/Pan and block overlays
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.DarkGray),
                            contentAlignment = Alignment.Center
                        ) {
                            ZoomableBox(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BoxWithConstraints(
                                        modifier = Modifier.wrapContentSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "Rendered Page View",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )

                                        // Bounding Box overlays
                                        Canvas(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .pointerInput(pageLayout) {
                                                    detectTapGestures { tapOffset ->
                                                        val canvasW = size.width.toFloat()
                                                        val canvasH = size.height.toFloat()
                                                        if (canvasW <= 0f || canvasH <= 0f) return@detectTapGestures

                                                        val scaleX = canvasW / pageLayout.pageWidth
                                                        val scaleY = canvasH / pageLayout.pageHeight

                                                        val clickedBlock = pageLayout.blocks.firstOrNull { block ->
                                                            val bx = block.x * scaleX
                                                            val by = block.y * scaleY
                                                            val bw = block.width * scaleX
                                                            val bh = block.height * scaleY
                                                            tapOffset.x >= bx && tapOffset.x <= bx + bw &&
                                                                    tapOffset.y >= by && tapOffset.y <= by + bh
                                                        }
                                                        if (clickedBlock != null) {
                                                            selectedBlock = clickedBlock
                                                            blockEditText = clickedBlock.displayText
                                                        }
                                                    }
                                                }
                                        ) {
                                            val canvasW = size.width
                                            val canvasH = size.height
                                            val scaleX = canvasW / pageLayout.pageWidth
                                            val scaleY = canvasH / pageLayout.pageHeight

                                            for (block in pageLayout.blocks) {
                                                val bx = block.x * scaleX
                                                val by = block.y * scaleY
                                                val bw = block.width * scaleX
                                                val bh = block.height * scaleY

                                                val isEdited = block.editedText != null
                                                val strokeColor = if (isEdited) Color(0xFF4CAF50) else Color(0xFF2196F3)

                                                drawRect(
                                                    color = strokeColor.copy(alpha = 0.2f),
                                                    topLeft = Offset(bx, by),
                                                    size = Size(bw, bh)
                                                )
                                                drawRect(
                                                    color = strokeColor,
                                                    topLeft = Offset(bx, by),
                                                    size = Size(bw, bh),
                                                    style = Stroke(width = 2.dp.toPx())
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { viewModel.saveEdits(context) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Edited PDF", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                is PdfBlockLoadState.Error -> {
                    Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                }
                else -> {}
            }
        }
    }

    // Edit Text Block Dialog
    if (selectedBlock != null) {
        val block = selectedBlock!!
        AlertDialog(
            onDismissRequest = { selectedBlock = null },
            title = {
                Text("Edit Text Block #${block.id}", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Original: ${block.text}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = blockEditText,
                        onValueChange = { blockEditText = it },
                        label = { Text("Replacement Text") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 5
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateBlockText(block, blockEditText)
                        selectedBlock = null
                    }
                ) {
                    Text("Apply Change")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedBlock = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
