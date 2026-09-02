package com.karnadigital.omnisuite.feature.pdf_tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
    var currentPageIndex by remember { mutableIntStateOf(0) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF Block Editor", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.EditNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text("Select PDF to Edit Blocks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }

            when (val state = loadState) {
                is PdfBlockLoadState.Loading -> {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is PdfBlockLoadState.Success -> {
                    val pageLayout = state.pages.getOrNull(currentPageIndex)
                    if (pageLayout != null) {
                        Text("Page ${currentPageIndex + 1} of ${state.pages.size}", fontWeight = FontWeight.Bold)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .background(Color.LightGray)
                        ) {
                            Text("Rendered Page View", modifier = Modifier.align(Alignment.Center))
                        }

                        Button(
                            onClick = {
                                viewModel.saveEdits(context)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Edited PDF")
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
}
