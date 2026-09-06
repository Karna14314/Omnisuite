package com.karnadigital.omnisuite.feature.pdf_tools

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.karnadigital.omnisuite.ui.component.OperationResultBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatermarkScreen(
    onBack: () -> Unit,
    onOpenFile: (String) -> Unit,
    initialPdfUri: String? = null,
    viewModel: WatermarkViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(initialPdfUri) {
        if (!initialPdfUri.isNullOrEmpty()) {
            try {
                viewModel.selectPdf(android.net.Uri.parse(initialPdfUri))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val pickPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let { viewModel.selectPdf(it) }
        }
    )

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let { viewModel.selectImage(it) }
        }
    )

    val thumbnails = rememberPdfThumbnails(context, viewModel.selectedPdfUri)
    val pageOneBitmap = thumbnails.firstOrNull()

    val scrollState = rememberScrollState()
    var showBottomSheet by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.successUri) {
        if (viewModel.successUri != null) {
            showBottomSheet = true
        }
    }

    LaunchedEffect(viewModel.errorMessage) {
        viewModel.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.resetStatus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (viewModel.watermarkMode == WatermarkMode.REMOVE) "Watermark Remover" else "Watermark Studio",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Mode Selector Tabs (Text / Image / Remove)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        Triple(WatermarkMode.TEXT, "Text", Icons.Default.TextFields),
                        Triple(WatermarkMode.IMAGE, "Image", Icons.Default.Image),
                        Triple(WatermarkMode.REMOVE, "Remover", Icons.Default.DeleteSweep)
                    ).forEach { (mode, label, icon) ->
                        val isSelected = viewModel.watermarkMode == mode
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.watermarkMode = mode }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 1. PDF File Selection Card
                if (viewModel.selectedPdfUri == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                            .clickable { pickPdfLauncher.launch("application/pdf") },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = "Upload PDF",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Select PDF Document",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Tap here to load document",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = viewModel.selectedPdfName ?: "Document Loaded",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Ready for processing offline",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            IconButton(onClick = { pickPdfLauncher.launch("application/pdf") }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Change PDF",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // 2. Real Live PDF Page 1 Preview
                if (viewModel.selectedPdfUri != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Live Page 1 Preview",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )

                        Box(
                            modifier = Modifier
                                .width(190.dp)
                                .aspectRatio(1f / 1.414f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                .shadow(2.dp, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            // Actual PDF Page 1 bitmap
                            if (pageOneBitmap != null) {
                                Image(
                                    bitmap = pageOneBitmap.asImageBitmap(),
                                    contentDescription = "Page 1",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.FillBounds
                                )
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    for (i in 0..7) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(if (i % 2 == 0) 0.85f else 0.95f)
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(Color.LightGray.copy(alpha = 0.35f))
                                        )
                                    }
                                }
                            }

                            // Watermark Overlay on Preview
                            when (viewModel.watermarkMode) {
                                WatermarkMode.TEXT -> {
                                    if (viewModel.watermarkPosition == WatermarkPosition.DIAGONAL_REPEAT) {
                                        // 3x3 repeat
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.SpaceAround,
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            for (r in 0..2) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceAround
                                                ) {
                                                    for (c in 0..2) {
                                                        Text(
                                                            text = viewModel.watermarkText.ifBlank { "SAMPLE" },
                                                            color = Color.Gray.copy(alpha = viewModel.opacityAlpha.coerceIn(0f, 1f)),
                                                            fontSize = 7.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.rotate(-viewModel.rotationAngle)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        val alignment = when (viewModel.watermarkPosition) {
                                            WatermarkPosition.CENTER -> Alignment.Center
                                            WatermarkPosition.TOP_LEFT -> Alignment.TopStart
                                            WatermarkPosition.TOP_RIGHT -> Alignment.TopEnd
                                            WatermarkPosition.BOTTOM_LEFT -> Alignment.BottomStart
                                            WatermarkPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
                                            WatermarkPosition.DIAGONAL_REPEAT -> Alignment.Center
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(10.dp),
                                            contentAlignment = alignment
                                        ) {
                                            Text(
                                                text = viewModel.watermarkText.ifBlank { "SAMPLE" },
                                                color = Color.Gray.copy(alpha = viewModel.opacityAlpha.coerceIn(0f, 1f)),
                                                fontSize = (viewModel.fontSize / 3.5f).sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.rotate(-viewModel.rotationAngle)
                                            )
                                        }
                                    }
                                }
                                WatermarkMode.IMAGE -> {
                                    if (viewModel.selectedImageUri != null) {
                                        val alignment = when (viewModel.watermarkPosition) {
                                            WatermarkPosition.CENTER -> Alignment.Center
                                            WatermarkPosition.TOP_LEFT -> Alignment.TopStart
                                            WatermarkPosition.TOP_RIGHT -> Alignment.TopEnd
                                            WatermarkPosition.BOTTOM_LEFT -> Alignment.BottomStart
                                            WatermarkPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
                                            WatermarkPosition.DIAGONAL_REPEAT -> Alignment.Center
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(8.dp),
                                            contentAlignment = alignment
                                        ) {
                                            AsyncImage(
                                                model = viewModel.selectedImageUri,
                                                contentDescription = "Watermark Logo",
                                                alpha = viewModel.opacityAlpha.coerceIn(0f, 1f),
                                                modifier = Modifier.size((viewModel.imageScalePercent * 0.8f).dp)
                                            )
                                        }
                                    }
                                }
                                WatermarkMode.REMOVE -> {
                                    // Visual badge
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                        modifier = Modifier.padding(4.dp)
                                    ) {
                                        Text(
                                            text = "Cleaner Active",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Mode-Specific Settings Deck
                when (viewModel.watermarkMode) {
                    WatermarkMode.TEXT -> {
                        // Watermark Text Field
                        OutlinedTextField(
                            value = viewModel.watermarkText,
                            onValueChange = { viewModel.watermarkText = it },
                            label = { Text("Watermark Text") },
                            placeholder = { Text("e.g. CONFIDENTIAL, DRAFT, COPY") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Position Presets
                        PositionSelector(
                            currentPosition = viewModel.watermarkPosition,
                            onSelectPosition = { viewModel.watermarkPosition = it }
                        )

                        // Angle Slider
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Rotation Angle", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("${viewModel.rotationAngle.toInt()}°", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = viewModel.rotationAngle,
                                onValueChange = { viewModel.rotationAngle = it },
                                valueRange = 0f..90f,
                                steps = 17
                            )
                        }

                        // Opacity Slider
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Opacity", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("${(viewModel.opacityAlpha * 100).toInt()}%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = viewModel.opacityAlpha,
                                onValueChange = { viewModel.opacityAlpha = it },
                                valueRange = 0.05f..1.0f
                            )
                        }

                        // Font Size Slider
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Font Size", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("${viewModel.fontSize.toInt()} pt", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = viewModel.fontSize,
                                onValueChange = { viewModel.fontSize = it },
                                valueRange = 20f..100f,
                                steps = 15
                            )
                        }
                    }

                    WatermarkMode.IMAGE -> {
                        // Image Picker Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pickImageLauncher.launch("image/*") },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = viewModel.selectedImageName ?: "Select Watermark Logo/Image",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text("PNG, JPG logo or stamp", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Button(
                                    onClick = { pickImageLauncher.launch("image/*") },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("Choose")
                                }
                            }
                        }

                        // Position Presets
                        PositionSelector(
                            currentPosition = viewModel.watermarkPosition,
                            onSelectPosition = { viewModel.watermarkPosition = it }
                        )

                        // Image Scale Slider
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Logo Scale", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("${viewModel.imageScalePercent.toInt()}%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = viewModel.imageScalePercent,
                                onValueChange = { viewModel.imageScalePercent = it },
                                valueRange = 15f..90f
                            )
                        }

                        // Opacity Slider
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Opacity", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("${(viewModel.opacityAlpha * 100).toInt()}%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = viewModel.opacityAlpha,
                                onValueChange = { viewModel.opacityAlpha = it },
                                valueRange = 0.05f..1.0f
                            )
                        }
                    }

                    WatermarkMode.REMOVE -> {
                        // Watermark Remover Options
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Cleaning Methods", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Strip Annotations & Stamps", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("Removes digital watermark overlays & security stamp annotations", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = viewModel.removeAnnotationWatermarks,
                                        onCheckedChange = { viewModel.removeAnnotationWatermarks = it }
                                    )
                                }

                                HorizontalDivider()

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Center Whiteout Mask", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("Applies clean whiteout cover across center diagonal watermark zone", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = viewModel.removeWhiteoutMask,
                                        onCheckedChange = { viewModel.removeWhiteoutMask = it }
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Primary Execution Action Button
                Button(
                    onClick = { viewModel.applyWatermark() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !viewModel.isProcessing && viewModel.selectedPdfUri != null
                ) {
                    if (viewModel.isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        val actionIcon = when (viewModel.watermarkMode) {
                            WatermarkMode.REMOVE -> Icons.Default.DeleteSweep
                            WatermarkMode.IMAGE -> Icons.Default.Image
                            WatermarkMode.TEXT -> Icons.Default.WaterDrop
                        }
                        val actionLabel = when (viewModel.watermarkMode) {
                            WatermarkMode.REMOVE -> "Clean & Save PDF"
                            WatermarkMode.IMAGE -> "Apply Image Watermark"
                            WatermarkMode.TEXT -> "Apply Watermark"
                        }
                        Icon(imageVector = actionIcon, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(actionLabel, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Success Output Bottom Sheet
    if (showBottomSheet && viewModel.successUri != null) {
        OperationResultBottomSheet(
            show = showBottomSheet,
            onDismiss = {
                showBottomSheet = false
                viewModel.resetStatus()
            },
            title = if (viewModel.watermarkMode == WatermarkMode.REMOVE) "Document Cleaned!" else "Watermark Applied!",
            fileUri = viewModel.successUri.toString(),
            fileName = viewModel.successName ?: "output.pdf",
            mimeType = "application/pdf",
            onOpenFile = onOpenFile
        )
    }
}

@Composable
private fun PositionSelector(
    currentPosition: WatermarkPosition,
    onSelectPosition: (WatermarkPosition) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Position", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(WatermarkPosition.values()) { pos ->
                val isSelected = currentPosition == pos
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectPosition(pos) },
                    label = { Text(pos.label, fontSize = 12.sp) },
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }
    }
}
