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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Premium, rich-aesthetic local offline image compression and editing dashboard.
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
    val selectedUri = uiState.selectedUri

    var activeTab by remember { mutableStateOf(initialTab) }
    var showCropDialog by remember { mutableStateOf(false) }
    val toolTabs = listOf("Editor", "Long Stitcher", "Extractor", "ID Card Maker", "Watermarker")

    // Launchers
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.loadSelectedImage(uri)
        }
    }

    val multiImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.selectStitchImages(uris)
        }
    }

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.extractMedia(it) }
    }

    val frontLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.selectIdFrontImage(uri)
    }

    val backLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.selectIdBackImage(uri)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/jpeg"),
        onResult = { uri ->
            uri?.let { viewModel.saveToCustomLocation(uri) }
        }
    )

    // React to processing messages
    LaunchedEffect(uiState.processingMessage) {
        uiState.processingMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Image Lab Extensions",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Workspace"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearSelection() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Form"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // sticky top tabs selector
            ScrollableTabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                toolTabs.forEachIndexed { index, title ->
                    Tab(
                        selected = activeTab == index,
                        onClick = {
                            activeTab = index
                            viewModel.clearSelection()
                        },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (activeTab == index) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (activeTab) {
                        0 -> { // --- STANDARD EDITOR ---
                            if (selectedUri == null) {
                                Spacer(modifier = Modifier.height(20.dp))
                                DropZoneBox(
                                    title = "Select Image to Edit",
                                    desc = "Compress, rotate, resize, and convert formats offline.",
                                    emoji = "📸",
                                    onClick = { imagePickerLauncher.launch("image/*") }
                                )
                            } else {
                                AsyncImageCard(bitmap = uiState.previewBitmap, uri = selectedUri, rotation = uiState.rotationDegrees)
                                Spacer(modifier = Modifier.height(16.dp))
                                MetadataBanner(uiState = uiState)
                                Spacer(modifier = Modifier.height(20.dp))
                                
                                // Format selector
                                EditingControlCard(title = "Target Format") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutputFormat.values().forEach { format ->
                                            val isSelected = uiState.outputFormat == format
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(44.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                                    .border(1.dp, if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                                    .clickable { viewModel.updateFormat(format) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = format.name,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Resize slider
                                EditingControlCard(
                                    title = "Resize Resolution",
                                    valueText = "${(uiState.resizeScale * 100).toInt()}%"
                                ) {
                                    Slider(
                                        value = uiState.resizeScale,
                                        onValueChange = { viewModel.updateScale(it) },
                                        valueRange = 0.1f..2.0f,
                                        steps = 18
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("10% (Mini)", style = MaterialTheme.typography.labelSmall)
                                        Text("100% (Original)", style = MaterialTheme.typography.labelSmall)
                                        Text("200% (Double)", style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Quality slider
                                if (uiState.outputFormat != OutputFormat.PNG) {
                                    EditingControlCard(
                                        title = "Compression Quality",
                                        valueText = "${uiState.compressionQuality}%"
                                    ) {
                                        Slider(
                                            value = uiState.compressionQuality.toFloat(),
                                            onValueChange = { viewModel.updateQuality(it.toInt()) },
                                            valueRange = 10f..100f,
                                            steps = 9
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                // Crop & Rotation Tools
                                EditingControlCard(title = "Crop & Rotation Tools") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(
                                            onClick = { showCropDialog = true },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                        ) {
                                            Text("Crop Image ✂️")
                                        }
                                        Button(
                                            onClick = { viewModel.rotateImage() },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Rotate 90° 🔄")
                                        }
                                    }
                                    if (uiState.rotationDegrees != 0f) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        OutlinedButton(
                                            onClick = { viewModel.resetRotation() },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Reset Rotation")
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Filter Effects
                                EditingControlCard(title = "Filter Effects") {
                                    val filters = listOf("Normal", "Grayscale", "Sepia", "Inverted", "Vintage", "Cool")
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        filters.forEach { filter ->
                                            val isSelected = uiState.filterType == filter
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                                    .border(1.dp, if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                                    .clickable { viewModel.updateAdjustments(uiState.brightness, uiState.contrast, uiState.saturation, filter) }
                                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = filter,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // General Image Enhancements
                                EditingControlCard(title = "Image Enhancements") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Brightness", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text("${uiState.brightness.toInt()}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    }
                                    Slider(
                                        value = uiState.brightness,
                                        onValueChange = { viewModel.updateAdjustments(it, uiState.contrast, uiState.saturation, uiState.filterType) },
                                        valueRange = -100f..100f
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Contrast", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text("${((uiState.contrast * 100).toInt() / 100f)}x", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    }
                                    Slider(
                                        value = uiState.contrast,
                                        onValueChange = { viewModel.updateAdjustments(uiState.brightness, it, uiState.saturation, uiState.filterType) },
                                        valueRange = 0.5f..2.0f
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Saturation", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text("${((uiState.saturation * 100).toInt() / 100f)}x", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    }
                                    Slider(
                                        value = uiState.saturation,
                                        onValueChange = { viewModel.updateAdjustments(uiState.brightness, uiState.contrast, it, uiState.filterType) },
                                        valueRange = 0.0f..2.0f
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))
                                if (uiState.isSuccess) {
                                    SuccessOutputCard(
                                        fileName = uiState.successName ?: "",
                                        onExport = { exportLauncher.launch(uiState.successName ?: "processed.jpg") }
                                    )
                                } else {
                                    Button(
                                        onClick = { viewModel.processAndSaveImage() },
                                        modifier = Modifier.fillMaxWidth().height(56.dp),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Text("Process & Save Image", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        1 -> { // --- VERTICAL STITCHER ---
                            if (uiState.selectedStitchUris.isEmpty()) {
                                Spacer(modifier = Modifier.height(20.dp))
                                DropZoneBox(
                                    title = "Stitch Images Vertically",
                                    desc = "Select multiple images to compile into one long vertical image layout.",
                                    emoji = "🥞",
                                    onClick = { multiImagePickerLauncher.launch("image/*") }
                                )
                            } else {
                                Text(
                                    text = "Selected Images Layout (Top to Bottom)",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                // Render stitched thumbnails
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        uiState.selectedStitchUris.forEachIndexed { index, uri ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primary),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("${index + 1}", color = Color.White, fontWeight = FontWeight.Bold)
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = uri.lastPathSegment ?: "Image ${index + 1}",
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                AsyncImage(
                                                    model = uri,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(6.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))
                                if (uiState.isSuccess) {
                                    SuccessOutputCard(
                                        fileName = uiState.successName ?: "",
                                        onExport = { exportLauncher.launch(uiState.successName ?: "stitched.jpg") }
                                    )
                                } else {
                                    Button(
                                        onClick = { viewModel.stitchImages() },
                                        modifier = Modifier.fillMaxWidth().height(56.dp),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Text("Stitch Images Vertically", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        2 -> { // --- MEDIA EXTRACTOR ---
                            if (uiState.extractedMediaUris.isEmpty() && !uiState.isSuccess) {
                                Spacer(modifier = Modifier.height(20.dp))
                                DropZoneBox(
                                    title = "Extract Document Media",
                                    desc = "Upload Word (.docx) or PowerPoint (.pptx) archives to extract high-resolution internal photos.",
                                    emoji = "📦",
                                    onClick = { docPickerLauncher.launch("*/*") }
                                )
                            } else {
                                Text(
                                    text = "Extracted Photos (${uiState.extractedMediaUris.size})",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                // Grid rows representation for extracted media
                                uiState.extractedMediaUris.chunked(2).forEach { pair ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        pair.forEach { uri ->
                                            Card(
                                                modifier = Modifier.weight(1f).height(160.dp),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Box(modifier = Modifier.fillMaxSize()) {
                                                    AsyncImage(
                                                        model = uri,
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                            }
                                        }
                                        if (pair.size < 2) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))
                                SuccessOutputCard(
                                    fileName = "Extracted Media images saved in Default folder",
                                    onExport = {
                                        uiState.extractedMediaUris.firstOrNull()?.let {
                                            exportLauncher.launch("extracted_media.jpg")
                                        }
                                    }
                                )
                            }
                        }

                        3 -> { // --- ID CARD MAKER ---
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                        .clickable { frontLauncher.launch("image/*") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (uiState.idFrontUri == null) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("📸", fontSize = 28.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Upload Front", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                    } else {
                                        AsyncImage(
                                            model = uiState.idFrontUri,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                        .clickable { backLauncher.launch("image/*") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (uiState.idBackUri == null) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("📸", fontSize = 28.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Upload Back", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                    } else {
                                        AsyncImage(
                                            model = uiState.idBackUri,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            if (uiState.isSuccess) {
                                SuccessOutputCard(
                                    fileName = uiState.successName ?: "",
                                    onExport = { exportLauncher.launch(uiState.successName ?: "id_template.jpg") }
                                )
                            } else {
                                Button(
                                    onClick = { viewModel.makeIdCard() },
                                    enabled = uiState.idFrontUri != null && uiState.idBackUri != null,
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text("Compile A4 ID Card Template", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        4 -> { // --- WATERMARKER ---
                            if (selectedUri == null) {
                                Spacer(modifier = Modifier.height(20.dp))
                                DropZoneBox(
                                    title = "Select Base Image",
                                    desc = "Upload the photo you'd like to apply a text watermark to.",
                                    emoji = "🎨",
                                    onClick = { imagePickerLauncher.launch("image/*") }
                                )
                            } else {
                                AsyncImageCard(bitmap = null, uri = selectedUri, rotation = 0f)
                                Spacer(modifier = Modifier.height(20.dp))

                                EditingControlCard(title = "Watermark Custom Text") {
                                    OutlinedTextField(
                                        value = uiState.watermarkText,
                                        onValueChange = { viewModel.updateWatermarkText(it) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                EditingControlCard(
                                    title = "Text Size",
                                    valueText = "${uiState.watermarkSize.toInt()} px"
                                ) {
                                    Slider(
                                        value = uiState.watermarkSize,
                                        onValueChange = { viewModel.updateWatermarkSize(it) },
                                        valueRange = 20f..200f
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                EditingControlCard(
                                    title = "Opacity (Alpha)",
                                    valueText = "${((uiState.watermarkAlpha / 255f) * 100).toInt()}%"
                                ) {
                                    Slider(
                                        value = uiState.watermarkAlpha.toFloat(),
                                        onValueChange = { viewModel.updateWatermarkAlpha(it.toInt()) },
                                        valueRange = 10f..255f
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                EditingControlCard(
                                    title = "Rotation Angle",
                                    valueText = "${uiState.watermarkRotation.toInt()}°"
                                ) {
                                    Slider(
                                        value = uiState.watermarkRotation,
                                        onValueChange = { viewModel.updateWatermarkRotation(it) },
                                        valueRange = -90f..90f
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))
                                if (uiState.isSuccess) {
                                    SuccessOutputCard(
                                        fileName = uiState.successName ?: "",
                                        onExport = { exportLauncher.launch(uiState.successName ?: "watermarked.jpg") }
                                    )
                                } else {
                                    Button(
                                        onClick = { viewModel.applyCustomWatermark() },
                                        modifier = Modifier.fillMaxWidth().height(56.dp),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Text("Apply & Save Watermarked Copy", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }

        // --- BLOCKING LOADER OVERLAY ---
        if (uiState.isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.processingMessage ?: "Executing core processing...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(initialUri) {
        if (!initialUri.isNullOrBlank()) {
            viewModel.loadSelectedImage(Uri.parse(initialUri))
        }
    }

    val showBottomSheet = uiState.isSuccess && uiState.successUri != null
    var successFileSize by remember { mutableStateOf(0L) }

    LaunchedEffect(uiState.isSuccess, uiState.successUri) {
        if (uiState.isSuccess && uiState.successUri != null) {
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openAssetFileDescriptor(uiState.successUri!!, "r")?.use { fd ->
                        successFileSize = fd.length
                    }
                } catch (e: Exception) {
                    successFileSize = 0L
                }
            }
        }
    }

    OperationResultBottomSheet(
        show = showBottomSheet,
        onDismiss = {
            viewModel.clearSelection()
        },
        title = "Image Processed Successfully",
        fileName = uiState.successName,
        fileUri = uiState.successUri?.toString(),
        fileSize = uiState.lastOutputBytes?.size?.toLong() ?: successFileSize,
        mimeType = uiState.successUri?.let { uri ->
            context.contentResolver.getType(uri)
        } ?: "image/jpeg",
        onOpenFile = onOpenFile
    )

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

