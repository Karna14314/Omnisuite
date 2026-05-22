package com.karnadigital.omnisuite.feature.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Local memory-safe PDF Viewer Screen using Android's native PdfRenderer inside a LazyColumn.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    fileUri: String,
    onBack: () -> Unit,
    viewModel: PdfViewerViewModel = hiltViewModel()
) {
    LaunchedEffect(fileUri) {
        viewModel.loadPdf(fileUri)
    }

    val state by viewModel.loadState.collectAsState()
    val lazyListState = rememberLazyListState()

    // Track active top-most visible page dynamically for toolbar tracking
    val currentPageIndex by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex + 1
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = when (val s = state) {
                                is PdfLoadState.Success -> s.fileName
                                else -> "Loading PDF..."
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (state is PdfLoadState.Success) {
                            val successState = state as PdfLoadState.Success
                            Text(
                                text = "Page $currentPageIndex of ${successState.pageCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (val currentState = state) {
                is PdfLoadState.Loading -> {
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
                            text = "Initializing native PDF engine...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }
                }
                is PdfLoadState.Success -> {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
                    ) {
                        items(currentState.pageCount) { pageIndex ->
                            PdfPageItem(
                                pageIndex = pageIndex,
                                viewModel = viewModel
                            )
                        }
                    }
                }
                is PdfLoadState.Error -> {
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
                            text = "Document Load Failed",
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

@Composable
fun PdfPageItem(
    pageIndex: Int,
    viewModel: PdfViewerViewModel
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var renderError by remember { mutableStateOf(false) }

    LaunchedEffect(pageIndex) {
        try {
            val rendered = viewModel.renderPage(pageIndex)
            if (rendered != null) {
                bitmap = rendered
            } else {
                renderError = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            renderError = true
        }
    }

    val aspectRatio = viewModel.getPageAspectRatio(pageIndex)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when {
                bitmap != null -> {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "Page ${pageIndex + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                renderError -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error rendering",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Failed to render page ${pageIndex + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

