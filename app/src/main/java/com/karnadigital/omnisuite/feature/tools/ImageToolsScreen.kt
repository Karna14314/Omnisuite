package com.karnadigital.omnisuite.feature.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.karnadigital.omnisuite.core.engine.image.OutputFormat

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.horizontalScroll
import com.karnadigital.omnisuite.ui.component.OperationResultBottomSheet
import kotlinx.coroutines.withContext

/**
 * Premium, rich-aesthetic local offline image compression and editing dashboard.
 */
enum class PhotoEditorCategory(val title: String) {
    TRANSFORM("Transform"),
    ADJUST("Adjust"),
    FILTERS("Filters"),
    COMPRESS("Compress"),
    TOOLS("Tools")
}

enum class AdjustmentParam(val title: String) {
    BRIGHTNESS("Brightness"),
    CONTRAST("Contrast"),
    SATURATION("Saturation")
}

/**
 * Modern Gallery Photo Editor & Image Lab Extensions.
 * Clean, tactile interface modelled after Google Photos & Samsung Gallery.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageToolsScreen(
    initialUri: String? = null,
    initialTab: Int = 0,
    onOpenFile: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ImageToolsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var activeCategory by remember(initialTab) {
        mutableStateOf(PhotoEditorCategory.entries.getOrElse(initialTab) { PhotoEditorCategory.TRANSFORM })
    }
    var activeAdjustParam by remember { mutableStateOf(AdjustmentParam.BRIGHTNESS) }
    var isHoldingCompare by remember { mutableStateOf(false) }
    var showLabSheet by remember { mutableStateOf(false) }
    var activeLabTool by remember { mutableStateOf<String?>(null) }
    var showCropDialog by remember { mutableStateOf(false) }

    // Activity Result Launchers
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadSelectedImage(it) }
    }

    val stitchPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.selectStitchImages(uris)
            activeLabTool = "stitch"
        }
    }

    val docExtractLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.extractMedia(it)
            activeLabTool = "extract"
        }
    }

    val idFrontLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.selectIdFrontImage(uri)
    }

    val idBackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.selectIdBackImage(uri)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/jpeg"),
        onResult = { uri ->
            uri?.let { viewModel.saveToCustomLocation(it) }
        }
    )

    LaunchedEffect(initialUri) {
        if (!initialUri.isNullOrBlank() && uiState.selectedUri == null) {
            try {
                viewModel.loadSelectedImage(Uri.parse(initialUri))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(uiState.processingMessage) {
        uiState.processingMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.selectedUri != null) {
                        Column {
                            Text(
                                text = "Photo Editor",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${uiState.originalWidth} × ${uiState.originalHeight} px",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = "Image Lab",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (uiState.selectedUri != null) {
                        // Compare with Original (Toggle)
                        IconButton(
                            onClick = { isHoldingCompare = !isHoldingCompare }
                        ) {
                            Icon(
                                imageVector = if (isHoldingCompare) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Compare original",
                                tint = if (isHoldingCompare) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Revert / Reset Adjustments
                        IconButton(
                            onClick = {
                                viewModel.updateAdjustments(0f, 1f, 1f, "Normal")
                                viewModel.resetRotation()
                                viewModel.updateScale(1f)
                                viewModel.updateQuality(80)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset adjustments"
                            )
                        }

                        // Save / Export
                        Button(
                            onClick = { viewModel.processAndSaveImage() },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    } else {
                        IconButton(onClick = { showLabSheet = true }) {
                            Icon(Icons.Default.Build, contentDescription = "Lab Utilities")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (uiState.selectedUri != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        // Contextual Controls Shelf
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            when (activeCategory) {
                                PhotoEditorCategory.TRANSFORM -> {
                                    TransformShelf(
                                        rotation = uiState.rotationDegrees,
                                        onRotate = { viewModel.rotateImage() },
                                        onResetRotation = { viewModel.resetRotation() },
                                        onCropSquare = { viewModel.cropToSquare() },
                                        onOpenVisualCrop = { showCropDialog = true }
                                    )
                                }
                                PhotoEditorCategory.ADJUST -> {
                                    AdjustShelf(
                                        activeParam = activeAdjustParam,
                                        onSelectParam = { activeAdjustParam = it },
                                        brightness = uiState.brightness,
                                        contrast = uiState.contrast,
                                        saturation = uiState.saturation,
                                        onAdjust = { b, c, s ->
                                            viewModel.updateAdjustments(b, c, s, uiState.filterType)
                                        }
                                    )
                                }
                                PhotoEditorCategory.FILTERS -> {
                                    FiltersShelf(
                                        activeFilter = uiState.filterType,
                                        onSelectFilter = { filter ->
                                            viewModel.updateAdjustments(
                                                uiState.brightness,
                                                uiState.contrast,
                                                uiState.saturation,
                                                filter
                                            )
                                        }
                                    )
                                }
                                PhotoEditorCategory.COMPRESS -> {
                                    CompressResizeShelf(
                                        quality = uiState.compressionQuality,
                                        scale = uiState.resizeScale,
                                        format = uiState.outputFormat,
                                        originalSize = uiState.originalSize,
                                        compressMode = uiState.compressMode,
                                        targetSizeKbText = uiState.targetSizeKbText,
                                        onQualityChange = { viewModel.updateQuality(it) },
                                        onScaleChange = { viewModel.updateScale(it) },
                                        onFormatChange = { viewModel.updateFormat(it) },
                                        onCompressModeChange = { viewModel.updateCompressMode(it) },
                                        onTargetSizeKbTextChange = { viewModel.updateTargetSizeKbText(it) }
                                    )
                                }
                                PhotoEditorCategory.TOOLS -> {
                                    ToolsShelf(
                                        onOpenStitcher = { stitchPickerLauncher.launch("image/*") },
                                        onOpenIdCard = { activeLabTool = "id_card" },
                                        onOpenWatermark = { activeLabTool = "watermark" },
                                        onOpenExtractor = { docExtractLauncher.launch("*/*") }
                                    )
                                }
                            }
                        }

                        // Bottom Navigation Categories
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EditorCategoryButton(
                                title = "Crop & Rotate",
                                icon = Icons.Default.Crop,
                                isSelected = activeCategory == PhotoEditorCategory.TRANSFORM,
                                onClick = { activeCategory = PhotoEditorCategory.TRANSFORM }
                            )

                            EditorCategoryButton(
                                title = "Adjust",
                                icon = Icons.Default.Tune,
                                isSelected = activeCategory == PhotoEditorCategory.ADJUST,
                                onClick = { activeCategory = PhotoEditorCategory.ADJUST }
                            )

                            EditorCategoryButton(
                                title = "Filters",
                                icon = Icons.Default.AutoFixHigh,
                                isSelected = activeCategory == PhotoEditorCategory.FILTERS,
                                onClick = { activeCategory = PhotoEditorCategory.FILTERS }
                            )

                            EditorCategoryButton(
                                title = "Compress",
                                icon = Icons.Default.Compress,
                                isSelected = activeCategory == PhotoEditorCategory.COMPRESS,
                                onClick = { activeCategory = PhotoEditorCategory.COMPRESS }
                            )

                            EditorCategoryButton(
                                title = "Lab Tools",
                                icon = Icons.Default.MoreHoriz,
                                isSelected = activeCategory == PhotoEditorCategory.TOOLS,
                                onClick = { activeCategory = PhotoEditorCategory.TOOLS }
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.selectedUri == null) {
                // Empty / Welcome State
                ImageLabWelcomeContent(
                    onPickImage = { imagePickerLauncher.launch("image/*") },
                    onOpenStitcher = { stitchPickerLauncher.launch("image/*") },
                    onOpenIdCard = { activeLabTool = "id_card" },
                    onOpenWatermark = { imagePickerLauncher.launch("image/*") },
                    onOpenExtractor = { docExtractLauncher.launch("*/*") }
                )
            } else {
                // Interactive Center Photo Canvas
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF141414)),
                    contentAlignment = Alignment.Center
                ) {
                    val displayBitmap = if (isHoldingCompare) null else uiState.previewBitmap
                    val displayUri = uiState.selectedUri

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (displayBitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = displayBitmap.asImageBitmap(),
                                contentDescription = "Edited Image",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(rotationZ = uiState.rotationDegrees),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            AsyncImage(
                                model = displayUri,
                                contentDescription = "Original Image",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(rotationZ = uiState.rotationDegrees),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    // Comparison overlay badge
                    if (isHoldingCompare) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                        ) {
                            Text(
                                text = "Viewing Original Image",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Processing overlay
            if (uiState.isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = uiState.processingMessage ?: "Processing image...",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    // Visual Crop Dialog
    if (showCropDialog && uiState.previewBitmap != null) {
        VisualCropDialog(
            bitmap = uiState.previewBitmap!!,
            onDismiss = { showCropDialog = false },
            onCropApplied = { left, top, right, bottom ->
                viewModel.applyCropRatios(left, top, right, bottom)
                showCropDialog = false
            }
        )
    }

    // Modal Sub-Tool Sheets
    when (activeLabTool) {
        "id_card" -> {
            ModalBottomSheet(
                onDismissRequest = { activeLabTool = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                IdCardSheetContent(
                    frontUri = uiState.idFrontUri,
                    backUri = uiState.idBackUri,
                    onPickFront = { idFrontLauncher.launch("image/*") },
                    onPickBack = { idBackLauncher.launch("image/*") },
                    onGenerate = { viewModel.makeIdCard() },
                    isSuccess = uiState.isSuccess,
                    successName = uiState.successName,
                    onExport = { exportLauncher.launch(uiState.successName ?: "id_card_template.jpg") }
                )
            }
        }
        "stitch" -> {
            ModalBottomSheet(
                onDismissRequest = { activeLabTool = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                StitchSheetContent(
                    selectedUris = uiState.selectedStitchUris,
                    onPickMore = { stitchPickerLauncher.launch("image/*") },
                    onStitch = { viewModel.stitchImages() },
                    isSuccess = uiState.isSuccess,
                    successName = uiState.successName,
                    onExport = { exportLauncher.launch(uiState.successName ?: "stitched_image.jpg") }
                )
            }
        }
        "watermark" -> {
            ModalBottomSheet(
                onDismissRequest = { activeLabTool = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                WatermarkSheetContent(
                    watermarkText = uiState.watermarkText,
                    watermarkSize = uiState.watermarkSize,
                    watermarkAlpha = uiState.watermarkAlpha,
                    watermarkRotation = uiState.watermarkRotation,
                    onTextChange = { viewModel.updateWatermarkText(it) },
                    onSizeChange = { viewModel.updateWatermarkSize(it) },
                    onAlphaChange = { viewModel.updateWatermarkAlpha(it) },
                    onRotationChange = { viewModel.updateWatermarkRotation(it) },
                    onApply = { viewModel.applyCustomWatermark() },
                    isSuccess = uiState.isSuccess,
                    successName = uiState.successName,
                    onExport = { exportLauncher.launch(uiState.successName ?: "watermarked.jpg") }
                )
            }
        }
        "extract" -> {
            ModalBottomSheet(
                onDismissRequest = { activeLabTool = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                ExtractSheetContent(
                    extractedUris = uiState.extractedMediaUris,
                    onExport = {
                        uiState.extractedMediaUris.firstOrNull()?.let {
                            exportLauncher.launch("extracted_media.jpg")
                        }
                    }
                )
            }
        }
    }

    // Success result sheet
    OperationResultBottomSheet(
        show = uiState.isSuccess && activeLabTool == null,
        onDismiss = { viewModel.clearSelection() },
        title = "Image Saved Successfully",
        fileName = uiState.successName,
        fileUri = uiState.successUri?.toString(),
        mimeType = when (uiState.outputFormat) {
            OutputFormat.PNG -> "image/png"
            OutputFormat.WEBP -> "image/webp"
            OutputFormat.JPEG -> "image/jpeg"
        },
        onOpenFile = onOpenFile
    )
}

// -------------------------------------------------------------
// CONTEXTUAL TOOL SHELVES
// -------------------------------------------------------------

@Composable
fun TransformShelf(
    rotation: Float,
    onRotate: () -> Unit,
    onResetRotation: () -> Unit,
    onCropSquare: () -> Unit,
    onOpenVisualCrop: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistChip(
            onClick = onOpenVisualCrop,
            label = { Text("Crop ✂️") },
            leadingIcon = { Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(16.dp)) }
        )

        AssistChip(
            onClick = onRotate,
            label = { Text("Rotate 90° (${rotation.toInt()}°)") },
            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp)) }
        )

        AssistChip(
            onClick = onCropSquare,
            label = { Text("Square 1:1") }
        )

        if (rotation != 0f) {
            AssistChip(
                onClick = onResetRotation,
                label = { Text("Reset Angle") }
            )
        }
    }
}

@Composable
fun AdjustShelf(
    activeParam: AdjustmentParam,
    onSelectParam: (AdjustmentParam) -> Unit,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    onAdjust: (Float, Float, Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AdjustmentParam.values().forEach { param ->
                val isSelected = activeParam == param
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectParam(param) },
                    label = { Text(param.title, fontSize = 12.sp) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (activeParam) {
            AdjustmentParam.BRIGHTNESS -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Low", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = brightness,
                        onValueChange = { onAdjust(it, contrast, saturation) },
                        valueRange = -100f..100f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text("High (${brightness.toInt()})", style = MaterialTheme.typography.labelSmall)
                }
            }
            AdjustmentParam.CONTRAST -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("0.5x", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = contrast,
                        onValueChange = { onAdjust(brightness, it, saturation) },
                        valueRange = 0.5f..2.0f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text("2.0x (${String.format("%.1f", contrast)})", style = MaterialTheme.typography.labelSmall)
                }
            }
            AdjustmentParam.SATURATION -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("B&W", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = saturation,
                        onValueChange = { onAdjust(brightness, contrast, it) },
                        valueRange = 0.0f..2.0f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text("Vivid (${String.format("%.1f", saturation)})", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun FiltersShelf(
    activeFilter: String,
    onSelectFilter: (String) -> Unit
) {
    val filters = listOf("Normal", "Vintage", "Cool", "Grayscale", "Sepia", "Inverted")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filters.forEach { filter ->
            val isSelected = activeFilter == filter
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelectFilter(filter) }
            ) {
                Text(
                    text = filter,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun CompressResizeShelf(
    quality: Int,
    scale: Float,
    format: OutputFormat,
    originalSize: Long,
    compressMode: String = "QUALITY",
    targetSizeKbText: String = "200",
    onQualityChange: (Int) -> Unit,
    onScaleChange: (Float) -> Unit,
    onFormatChange: (OutputFormat) -> Unit,
    onCompressModeChange: (String) -> Unit = {},
    onTargetSizeKbTextChange: (String) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Mode Selector Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = compressMode == "QUALITY",
                onClick = { onCompressModeChange("QUALITY") },
                label = { Text("Quality Slider", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = compressMode == "TARGET_SIZE",
                onClick = { onCompressModeChange("TARGET_SIZE") },
                label = { Text("Target Size (KB)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (compressMode == "TARGET_SIZE") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = targetSizeKbText,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 6) {
                            onTargetSizeKbTextChange(input)
                        }
                    },
                    label = { Text("Target Max Size (KB)", fontSize = 12.sp) },
                    trailingIcon = { Text("KB", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("20", "50", "100", "200", "500").forEach { preset ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (targetSizeKbText == preset) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { onTargetSizeKbTextChange(preset) }
                        ) {
                            Text(
                                text = "${preset}K",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (targetSizeKbText == preset) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Format & Quality", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutputFormat.values().forEach { fmt ->
                        val isSel = format == fmt
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.clickable { onFormatChange(fmt) }
                        ) {
                            Text(
                                text = fmt.name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (format != OutputFormat.PNG) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Quality: $quality%", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = quality.toFloat(),
                        onValueChange = { onQualityChange(it.toInt()) },
                        valueRange = 10f..100f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(0.25f to "25%", 0.5f to "50%", 0.75f to "75%", 1.0f to "100%").forEach { (sc, label) ->
                    val isSel = kotlin.math.abs(scale - sc) < 0.05f
                    FilterChip(
                        selected = isSel,
                        onClick = { onScaleChange(sc) },
                        label = { Text(label, fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun ToolsShelf(
    onOpenStitcher: () -> Unit,
    onOpenIdCard: () -> Unit,
    onOpenWatermark: () -> Unit,
    onOpenExtractor: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssistChip(
            onClick = onOpenStitcher,
            label = { Text("Long Stitch") },
            leadingIcon = { Icon(Icons.Default.BurstMode, contentDescription = null, modifier = Modifier.size(16.dp)) }
        )
        AssistChip(
            onClick = onOpenIdCard,
            label = { Text("ID Card A4") },
            leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(16.dp)) }
        )
        AssistChip(
            onClick = onOpenWatermark,
            label = { Text("Watermark") },
            leadingIcon = { Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(16.dp)) }
        )
        AssistChip(
            onClick = onOpenExtractor,
            label = { Text("Extract Media") },
            leadingIcon = { Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp)) }
        )
    }
}

@Composable
fun EditorCategoryButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// -------------------------------------------------------------
// WELCOME & MODAL SHEETS CONTENT
// -------------------------------------------------------------

@Composable
fun ImageLabWelcomeContent(
    onPickImage: () -> Unit,
    onOpenStitcher: () -> Unit,
    onOpenIdCard: () -> Unit,
    onOpenWatermark: () -> Unit,
    onOpenExtractor: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPickImage)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Select Photo to Edit",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Crop, adjust colors, apply filters, compress, and convert offline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Specialized Image Tools",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LabToolGridCard(
                title = "Long Stitcher",
                desc = "Combine vertical screenshots",
                emoji = "📜",
                modifier = Modifier.weight(1f),
                onClick = onOpenStitcher
            )
            LabToolGridCard(
                title = "ID Card Maker",
                desc = "Front & back on A4",
                emoji = "🪪",
                modifier = Modifier.weight(1f),
                onClick = onOpenIdCard
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LabToolGridCard(
                title = "Watermarker",
                desc = "Custom text overlay",
                emoji = "💧",
                modifier = Modifier.weight(1f),
                onClick = onOpenWatermark
            )
            LabToolGridCard(
                title = "Media Extractor",
                desc = "Extract images from docs",
                emoji = "📦",
                modifier = Modifier.weight(1f),
                onClick = onOpenExtractor
            )
        }
    }
}

@Composable
fun LabToolGridCard(
    title: String,
    desc: String,
    emoji: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = emoji, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun IdCardSheetContent(
    frontUri: Uri?,
    backUri: Uri?,
    onPickFront: () -> Unit,
    onPickBack: () -> Unit,
    onGenerate: () -> Unit,
    isSuccess: Boolean,
    successName: String?,
    onExport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text("ID Card Print Template (A4)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .clickable(onClick = onPickFront),
                contentAlignment = Alignment.Center
            ) {
                if (frontUri != null) {
                    AsyncImage(model = frontUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                } else {
                    Text("Upload Front", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .clickable(onClick = onPickBack),
                contentAlignment = Alignment.Center
            ) {
                if (backUri != null) {
                    AsyncImage(model = backUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                } else {
                    Text("Upload Back", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (isSuccess) {
            Button(
                onClick = onExport,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Export A4 Document: $successName")
            }
        } else {
            Button(
                onClick = onGenerate,
                enabled = frontUri != null && backUri != null,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Generate A4 Layout")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun StitchSheetContent(
    selectedUris: List<Uri>,
    onPickMore: () -> Unit,
    onStitch: () -> Unit,
    isSuccess: Boolean,
    successName: String?,
    onExport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text("Long Photo Stitcher", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Text("${selectedUris.size} images selected for vertical stitching", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)

        Spacer(modifier = Modifier.height(16.dp))
        if (isSuccess) {
            Button(onClick = onExport, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)) {
                Text("Export Stitched Photo: $successName")
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onPickMore, modifier = Modifier.weight(1f)) {
                    Text("Select Images")
                }
                Button(onClick = onStitch, enabled = selectedUris.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text("Stitch Vertically")
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun WatermarkSheetContent(
    watermarkText: String,
    watermarkSize: Float,
    watermarkAlpha: Int,
    watermarkRotation: Float,
    onTextChange: (String) -> Unit,
    onSizeChange: (Float) -> Unit,
    onAlphaChange: (Int) -> Unit,
    onRotationChange: (Float) -> Unit,
    onApply: () -> Unit,
    isSuccess: Boolean,
    successName: String?,
    onExport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text("Apply Text Watermark", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = watermarkText,
            onValueChange = onTextChange,
            label = { Text("Watermark Text") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        Text("Size: ${watermarkSize.toInt()}px", fontSize = 12.sp)
        Slider(value = watermarkSize, onValueChange = onSizeChange, valueRange = 20f..200f)

        Text("Opacity: ${((watermarkAlpha / 255f) * 100).toInt()}%", fontSize = 12.sp)
        Slider(value = watermarkAlpha.toFloat(), onValueChange = { onAlphaChange(it.toInt()) }, valueRange = 20f..255f)

        Spacer(modifier = Modifier.height(16.dp))
        if (isSuccess) {
            Button(onClick = onExport, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)) {
                Text("Export Watermarked Image")
            }
        } else {
            Button(onClick = onApply, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)) {
                Text("Apply & Save Watermark")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ExtractSheetContent(
    extractedUris: List<Uri>,
    onExport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text("Extracted Document Images", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Found ${extractedUris.size} embedded image files in document.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)

        Spacer(modifier = Modifier.height(16.dp))
        if (extractedUris.isNotEmpty()) {
            Button(onClick = onExport, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)) {
                Text("Export Extracted Images")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun DropZoneBox(
    title: String,
    desc: String,
    emoji: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(
                width = 2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 40.sp)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Select File")
            }
        }
    }
}

@Composable
private fun AsyncImageCard(bitmap: Bitmap?, uri: Uri, rotation: Float) {
    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(20.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Image preview",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = rotation
                        },
                    contentScale = ContentScale.Fit
                )
            } else {
                AsyncImage(
                    model = uri,
                    contentDescription = "Image preview",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = rotation
                        },
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
private fun MetadataBanner(uiState: ImageToolsUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Original Res", style = MaterialTheme.typography.labelSmall)
            Text("${uiState.originalWidth} × ${uiState.originalHeight}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Divider(modifier = Modifier.height(36.dp).width(1.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Original Size", style = MaterialTheme.typography.labelSmall)
            Text(formatSize(uiState.originalSize), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Divider(modifier = Modifier.height(36.dp).width(1.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Target Res", style = MaterialTheme.typography.labelSmall)
            val targetW = (uiState.originalWidth * uiState.resizeScale).toInt()
            val targetH = (uiState.originalHeight * uiState.resizeScale).toInt()
            Text("$targetW × $targetH", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun EditingControlCard(
    title: String,
    valueText: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                if (valueText != null) {
                    Text(
                        text = valueText,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SuccessOutputCard(
    fileName: String,
    onExport: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = if (fileName.isNotEmpty()) fileName else "Operation Completed Successfully!",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Saved in Documents/OmniSuite default folder",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onExport,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save copy to custom location...", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}

@Composable
private fun VisualCropDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit,
    onCropApplied: (Float, Float, Float, Float) -> Unit
) {
    var cropLeft by remember { mutableStateOf(0.1f) }
    var cropTop by remember { mutableStateOf(0.1f) }
    var cropRight by remember { mutableStateOf(0.9f) }
    var cropBottom by remember { mutableStateOf(0.9f) }

    var activeHandle by remember { mutableStateOf<String?>(null) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                    Text(
                        text = "Crop Image",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(
                        onClick = {
                            onCropApplied(cropLeft, cropTop, cropRight, cropBottom)
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = "Apply", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                // Crop area
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val containerWidth = constraints.maxWidth.toFloat()
                    val containerHeight = constraints.maxHeight.toFloat()

                    val bmpWidth = bitmap.width.toFloat()
                    val bmpHeight = bitmap.height.toFloat()

                    val bmpRatio = bmpWidth / bmpHeight
                    val containerRatio = containerWidth / containerHeight

                    val drawWidth: Float
                    val drawHeight: Float
                    val offsetX: Float
                    val offsetY: Float

                    if (bmpRatio > containerRatio) {
                        drawWidth = containerWidth
                        drawHeight = containerWidth / bmpRatio
                        offsetX = 0f
                        offsetY = (containerHeight - drawHeight) / 2f
                    } else {
                        drawWidth = containerHeight * bmpRatio
                        drawHeight = containerHeight
                        offsetX = (containerWidth - drawWidth) / 2f
                        offsetY = 0f
                    }

                    val pxLeft = offsetX + cropLeft * drawWidth
                    val pxTop = offsetY + cropTop * drawHeight
                    val pxRight = offsetX + cropRight * drawWidth
                    val pxBottom = offsetY + cropBottom * drawHeight

                    // Render Image inside the same bounding box
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    // Touch interaction canvas overlay
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(bitmap) {
                                val touchRadius = 30.dp.toPx()
                                detectDragGestures(
                                    onDragStart = { startOffset ->
                                        val x = startOffset.x
                                        val y = startOffset.y

                                        fun distSq(x1: Float, y1: Float, x2: Float, y2: Float) = (x1-x2)*(x1-x2) + (y1-y2)*(y1-y2)
                                        val tlD = distSq(x, y, pxLeft, pxTop)
                                        val trD = distSq(x, y, pxRight, pxTop)
                                        val blD = distSq(x, y, pxLeft, pxBottom)
                                        val brD = distSq(x, y, pxRight, pxBottom)

                                        val rSq = touchRadius * touchRadius
                                        activeHandle = when {
                                            tlD < rSq -> "TL"
                                            trD < rSq -> "TR"
                                            blD < rSq -> "BL"
                                            brD < rSq -> "BR"
                                            x >= pxLeft && x <= pxRight && y >= pxTop && y <= pxBottom -> "BODY"
                                            else -> null
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        val handle = activeHandle ?: return@detectDragGestures
                                        val pos = change.position

                                        val relX = ((pos.x - offsetX) / drawWidth).coerceIn(0f, 1f)
                                        val relY = ((pos.y - offsetY) / drawHeight).coerceIn(0f, 1f)

                                        val minGap = 0.05f

                                        when (handle) {
                                            "TL" -> {
                                                cropLeft = relX.coerceAtMost(cropRight - minGap)
                                                cropTop = relY.coerceAtMost(cropBottom - minGap)
                                            }
                                            "TR" -> {
                                                cropRight = relX.coerceAtLeast(cropLeft + minGap)
                                                cropTop = relY.coerceAtMost(cropBottom - minGap)
                                            }
                                            "BL" -> {
                                                cropLeft = relX.coerceAtMost(cropRight - minGap)
                                                cropBottom = relY.coerceAtLeast(cropTop + minGap)
                                            }
                                            "BR" -> {
                                                cropRight = relX.coerceAtLeast(cropLeft + minGap)
                                                cropBottom = relY.coerceAtLeast(cropTop + minGap)
                                            }
                                            "BODY" -> {
                                                val deltaX = dragAmount.x / drawWidth
                                                val deltaY = dragAmount.y / drawHeight
                                                val w = cropRight - cropLeft
                                                val h = cropBottom - cropTop
                                                val newLeft = (cropLeft + deltaX).coerceIn(0f, 1f - w)
                                                val newTop = (cropTop + deltaY).coerceIn(0f, 1f - h)
                                                cropLeft = newLeft
                                                cropRight = newLeft + w
                                                cropTop = newTop
                                                cropBottom = newTop + h
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        activeHandle = null
                                    }
                                )
                            }
                    ) {
                        val dimColor = Color(0x99000000)

                        // Top dim
                        drawRect(
                            color = dimColor,
                            topLeft = androidx.compose.ui.geometry.Offset(offsetX, offsetY),
                            size = androidx.compose.ui.geometry.Size(drawWidth, pxTop - offsetY)
                        )
                        // Bottom dim
                        drawRect(
                            color = dimColor,
                            topLeft = androidx.compose.ui.geometry.Offset(offsetX, pxBottom),
                            size = androidx.compose.ui.geometry.Size(drawWidth, offsetY + drawHeight - pxBottom)
                        )
                        // Left dim
                        drawRect(
                            color = dimColor,
                            topLeft = androidx.compose.ui.geometry.Offset(offsetX, pxTop),
                            size = androidx.compose.ui.geometry.Size(pxLeft - offsetX, pxBottom - pxTop)
                        )
                        // Right dim
                        drawRect(
                            color = dimColor,
                            topLeft = androidx.compose.ui.geometry.Offset(pxRight, pxTop),
                            size = androidx.compose.ui.geometry.Size(offsetX + drawWidth - pxRight, pxBottom - pxTop)
                        )

                        // Draw crop rect stroke
                        drawRect(
                            color = Color.White,
                            topLeft = androidx.compose.ui.geometry.Offset(pxLeft, pxTop),
                            size = androidx.compose.ui.geometry.Size(pxRight - pxLeft, pxBottom - pxTop),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                        )

                        // Draw corner handle circles
                        val handleRadius = 8.dp.toPx()
                        val handleColor = Color.White
                        drawCircle(color = handleColor, radius = handleRadius, center = androidx.compose.ui.geometry.Offset(pxLeft, pxTop))
                        drawCircle(color = handleColor, radius = handleRadius, center = androidx.compose.ui.geometry.Offset(pxRight, pxTop))
                        drawCircle(color = handleColor, radius = handleRadius, center = androidx.compose.ui.geometry.Offset(pxLeft, pxBottom))
                        drawCircle(color = handleColor, radius = handleRadius, center = androidx.compose.ui.geometry.Offset(pxRight, pxBottom))
                    }
                }

                // Bottom instructions
                Text(
                    text = "Drag corners to resize, drag center to move crop area.",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                )
            }
        }
    }
}

