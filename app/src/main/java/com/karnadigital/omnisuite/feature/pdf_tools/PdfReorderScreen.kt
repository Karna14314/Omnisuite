package com.karnadigital.omnisuite.feature.pdf_tools

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.karnadigital.omnisuite.core.util.UriCacheUtils
import com.karnadigital.omnisuite.di.coreEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReorderScreen(
    onBack: () -> Unit,
    viewModel: PdfToolsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uriCacheUtils = coreEntryPoint(context).uriCacheUtils()

    var pageCount by remember { mutableIntStateOf(0) }
    var pageThumbnails by remember { mutableStateOf<List<Bitmap?>>(emptyList()) }
    var pageOrder by remember { mutableStateOf<List<Int>>(emptyList()) }
    var isLoadingPages by remember { mutableStateOf(false) }
    var dragItemIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.reorderInputUri = it
            context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    LaunchedEffect(viewModel.reorderInputUri) {
        val uri = viewModel.reorderInputUri ?: return@LaunchedEffect
        isLoadingPages = true
        withContext(Dispatchers.IO) {
            try {
                val cachedFile = uriCacheUtils.cacheUriToFile(uri) ?: return@withContext
                val pfd = ParcelFileDescriptor.open(cachedFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val count = renderer.pageCount
                pageCount = count
                val thumbs = mutableListOf<Bitmap?>()
                for (i in 0 until count) {
                    val page = renderer.openPage(i)
                    val scale = 300f / page.width.coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(
                        (page.width * scale).toInt().coerceAtLeast(1),
                        (page.height * scale).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    thumbs.add(bitmap)
                }
                pageThumbnails = thumbs
                pageOrder = (0 until count).toList()
                renderer.close()
                pfd.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        isLoadingPages = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reorder Pages", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (viewModel.reorderInputUri == null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { filePickerLauncher.launch(arrayOf("application/pdf")) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(48.dp))
                        Text("Tap to select PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                if (isLoadingPages) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Loading pages...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    Text(
                        text = "Long press and drag to reorder pages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val gridState = rememberLazyGridState()
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(pageOrder) { visualIndex, originalPageIdx ->
                            val isDragging = dragItemIndex == visualIndex
                            Card(
                                modifier = Modifier
                                    .aspectRatio(0.7f)
                                    .zIndex(if (isDragging) 10f else 0f)
                                    .then(
                                        if (isDragging) Modifier.offset {
                                            androidx.compose.ui.unit.IntOffset(
                                                dragOffset.x.toInt(),
                                                dragOffset.y.toInt()
                                            )
                                        } else Modifier
                                    )
                                    .pointerInput(visualIndex) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { dragItemIndex = visualIndex },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffset += dragAmount
                                            },
                                            onDragEnd = {
                                                val targetIndex = visualIndex + (dragOffset.y / 100f).toInt().coerceIn(-visualIndex, pageOrder.size - 1 - visualIndex)
                                                if (targetIndex != 0) {
                                                    val newOrder = pageOrder.toMutableList()
                                                    val item = newOrder.removeAt(visualIndex)
                                                    newOrder.add((visualIndex + targetIndex).coerceIn(0, newOrder.size), item)
                                                    pageOrder = newOrder
                                                }
                                                dragItemIndex = -1
                                                dragOffset = Offset.Zero
                                            },
                                            onDragCancel = {
                                                dragItemIndex = -1
                                                dragOffset = Offset.Zero
                                            }
                                        )
                                    },
                                elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 2.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDragging) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    pageThumbnails.getOrNull(originalPageIdx)?.let { bitmap ->
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "Page ${originalPageIdx + 1}",
                                            modifier = Modifier.fillMaxSize().padding(4.dp).clip(RoundedCornerShape(4.dp)),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                    Surface(
                                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                                    ) {
                                        Text(
                                            text = "${originalPageIdx + 1}",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Surface(
                                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f)
                                    ) {
                                        Text(
                                            text = "${visualIndex + 1}",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            color = MaterialTheme.colorScheme.onSecondary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { pageOrder = (0 until pageCount).toList() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset")
                        }
                        Button(
                            onClick = {
                                viewModel.reorderPageOrder = pageOrder
                                viewModel.reorderPdfPages()
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !viewModel.isProcessing
                        ) {
                            if (viewModel.isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Apply")
                            }
                        }
                    }
                }
            }

            if (viewModel.successMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(viewModel.successMessage!!, color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (viewModel.errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
