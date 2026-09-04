package com.karnadigital.omnisuite.feature.imposition

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.karnadigital.omnisuite.core.engine.imposition.*
import com.karnadigital.omnisuite.ui.component.OperationResultBottomSheet
import com.karnadigital.omnisuite.ui.theme.OmniColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintImpositionStudioScreen(
    onBack: () -> Unit = {},
    onOpenFile: ((String) -> Unit)? = null,
    viewModel: PrintImpositionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setSelectedFile(uri, uri.lastPathSegment ?: "document.pdf")
        }
    }

    var selectedTabIdx by remember { mutableIntStateOf(0) }
    val modes = listOf(
        ImpositionToolMode.BOOKLET,
        ImpositionToolMode.N_UP,
        ImpositionToolMode.CARDS,
        ImpositionToolMode.CROP_RESIZE,
        ImpositionToolMode.BLEED_GENERATOR,
        ImpositionToolMode.REGISTRATION_MARKS,
        ImpositionToolMode.ZINE
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Print & Imposition Studio",
                            fontWeight = FontWeight.Bold,
                            color = OmniColors.TextPrimary,
                            fontSize = 18.sp
                        )
                        if (uiState.fileName.isNotEmpty()) {
                            Text(
                                text = "${uiState.fileName} (${uiState.pageCount} pages)",
                                color = OmniColors.TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = OmniColors.TextPrimary)
                    }
                },
                actions = {
                    Button(
                        onClick = { pdfPickerLauncher.launch("application/pdf") },
                        colors = ButtonDefaults.buttonColors(containerColor = OmniColors.Surface2),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, tint = OmniColors.TextPrimary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open PDF", color = OmniColors.TextPrimary, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.exportImposedPdf() },
                        enabled = uiState.fileUri != null && !uiState.isExporting,
                        colors = ButtonDefaults.buttonColors(containerColor = OmniColors.PdfRed),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export PDF", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = OmniColors.Bg)
            )
        },
        containerColor = OmniColors.Bg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Horizontal Tool Tabs Bar
            ScrollableTabRow(
                selectedTabIndex = selectedTabIdx,
                containerColor = OmniColors.Surface,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIdx]),
                        color = OmniColors.PdfRed
                    )
                }
            ) {
                modes.forEachIndexed { idx, toolMode ->
                    Tab(
                        selected = selectedTabIdx == idx,
                        onClick = {
                            selectedTabIdx = idx
                            viewModel.updateConfig { it.copy(mode = toolMode) }
                        },
                        text = {
                            Text(
                                text = toolMode.displayName,
                                fontWeight = if (selectedTabIdx == idx) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                color = if (selectedTabIdx == idx) OmniColors.PdfRed else OmniColors.TextMuted
                            )
                        }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left Panel: Interactive Control Panel
                Card(
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = OmniColors.Surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "${modes[selectedTabIdx].displayName} Controls",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = OmniColors.TextPrimary
                        )

                        // Paper Size Selector
                        Text("Paper Size", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = OmniColors.TextMuted)
                        val currentPreset = uiState.config.targetPaperSize.preset
                        var presetExpanded by remember { mutableStateOf(false) }

                        Box {
                            OutlinedButton(
                                onClick = { presetExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(currentPreset.displayName, fontSize = 12.sp)
                            }
                            DropdownMenu(
                                expanded = presetExpanded,
                                onDismissRequest = { presetExpanded = false }
                            ) {
                                PaperPreset.entries.forEach { preset ->
                                    DropdownMenuItem(
                                        text = { Text(preset.displayName, fontSize = 12.sp) },
                                        onClick = {
                                            presetExpanded = false
                                            viewModel.updateConfig { cfg ->
                                                cfg.copy(targetPaperSize = cfg.targetPaperSize.copy(preset = preset))
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // Sheet Orientation Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Landscape Orientation", fontSize = 12.sp, color = OmniColors.TextPrimary)
                            Switch(
                                checked = uiState.config.isLandscape,
                                onCheckedChange = { isLand ->
                                    viewModel.updateConfig { it.copy(isLandscape = isLand) }
                                }
                            )
                        }

                        Divider(color = OmniColors.Border)

                        // Mode-Specific Controls Panel
                        when (modes[selectedTabIdx]) {
                            ImpositionToolMode.N_UP, ImpositionToolMode.CARDS -> {
                                Text("Grid Layout (Cols x Rows)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = uiState.config.gridCols.toString(),
                                        onValueChange = { val c = it.toIntOrNull() ?: 1; viewModel.updateConfig { cfg -> cfg.copy(gridCols = c) } },
                                        label = { Text("Cols", fontSize = 10.sp) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = uiState.config.gridRows.toString(),
                                        onValueChange = { val r = it.toIntOrNull() ?: 1; viewModel.updateConfig { cfg -> cfg.copy(gridRows = r) } },
                                        label = { Text("Rows", fontSize = 10.sp) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                            }

                            ImpositionToolMode.BOOKLET -> {
                                Text("Binding Direction", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                BindingDirection.entries.forEach { dir ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        RadioButton(
                                            selected = uiState.config.bindingDirection == dir,
                                            onClick = { viewModel.updateConfig { it.copy(bindingDirection = dir) } }
                                        )
                                        Column {
                                            Text(dir.displayName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            Text(dir.description, fontSize = 10.sp, color = OmniColors.TextMuted)
                                        }
                                    }
                                }
                            }

                            ImpositionToolMode.CROP_RESIZE -> {
                                Text("Fit Mode", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                FitMode.entries.forEach { fit ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(
                                            selected = uiState.config.fitMode == fit,
                                            onClick = { viewModel.updateConfig { it.copy(fitMode = fit) } }
                                        )
                                        Text(fit.displayName, fontSize = 12.sp)
                                    }
                                }
                            }

                            ImpositionToolMode.BLEED_GENERATOR, ImpositionToolMode.REGISTRATION_MARKS -> {
                                Text("Bleed & Print Marks", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                OutlinedTextField(
                                    value = uiState.config.bleedMm.toString(),
                                    onValueChange = { val b = it.toFloatOrNull() ?: 3f; viewModel.updateConfig { cfg -> cfg.copy(bleedMm = b) } },
                                    label = { Text("Bleed (mm)", fontSize = 10.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = uiState.config.showCropMarks,
                                        onCheckedChange = { show -> viewModel.updateConfig { it.copy(showCropMarks = show) } }
                                    )
                                    Text("Show Crop Marks", fontSize = 12.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = uiState.config.showRegistrationTargets,
                                        onCheckedChange = { show -> viewModel.updateConfig { it.copy(showRegistrationTargets = show) } }
                                    )
                                    Text("Show Registration Crosshairs", fontSize = 12.sp)
                                }
                            }

                            ImpositionToolMode.ZINE -> {
                                Text("Zine Type", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                ZineType.entries.forEach { zine ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(
                                            selected = uiState.config.zineType == zine,
                                            onClick = { viewModel.updateConfig { it.copy(zineType = zine) } }
                                        )
                                        Text(zine.displayName, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Right Panel: Interactive Live Preview Viewport
                Box(
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxHeight()
                ) {
                    InteractivePreviewEngine(
                        fileUri = uiState.fileUri,
                        sheetLayouts = uiState.calculatedSheets,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (uiState.isExporting) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f)),
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = OmniColors.PdfRed)
                                Spacer(Modifier.height(12.dp))
                                Text(uiState.exportProgressMessage, color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Error Snackbar / Alert Banner
    if (uiState.errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearExportResult() },
            title = { Text("Error", fontWeight = FontWeight.Bold) },
            text = { Text(uiState.errorMessage ?: "An unexpected error occurred.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearExportResult() }) {
                    Text("OK")
                }
            }
        )
    }

    // Export Success BottomSheet
    if (uiState.exportedFile != null) {
        val targetUri = uiState.exportedUri?.toString() ?: Uri.fromFile(uiState.exportedFile).toString()
        OperationResultBottomSheet(
            show = true,
            onDismiss = { viewModel.clearExportResult() },
            title = "PDF Imposition Export Complete!",
            fileName = "Imposed_${uiState.fileName.ifBlank { "document.pdf" }}",
            fileUri = targetUri,
            fileSize = uiState.exportedFile?.length() ?: 0L,
            mimeType = "application/pdf",
            onOpenFile = onOpenFile
        )
    }
}
