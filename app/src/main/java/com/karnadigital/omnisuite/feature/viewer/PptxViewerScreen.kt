package com.karnadigital.omnisuite.feature.viewer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Build
import java.io.File
import androidx.compose.foundation.clickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import com.karnadigital.omnisuite.core.util.ZoomableBox

/**
 * Slide-deck Presentation Viewer (PPTX) mobile screen engine.
 * Renders slides in a distraction-free swipeable HorizontalPager.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PptxViewerScreen(
    fileUri: String,
    onBack: () -> Unit,
    viewModel: PptxViewerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(fileUri) {
        viewModel.loadPptxFile(fileUri)
    }

    LaunchedEffect(Unit) {
        viewModel.saveStatus.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val state by viewModel.loadState.collectAsState()
    var isEditMode by remember { mutableStateOf(false) }

    var activeIndexToEdit by remember { mutableStateOf<Int?>(null) }
    var blockToEdit by remember { mutableStateOf<PptxTextBlock?>(null) }
    var isTitleEdit by remember { mutableStateOf(false) }
    var blockIndexToEdit by remember { mutableStateOf(-1) }
    var showFormatter by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                coroutineScope.launch {
                    val cachedFile = com.karnadigital.omnisuite.core.util.UriCacheUtils.cacheUriToFile(context, it)
                    if (cachedFile != null) {
                        val slideIndex = activeIndexToEdit ?: 0
                        viewModel.insertImageIntoSlide(slideIndex, cachedFile.absolutePath)
                        Toast.makeText(context, "Picture inserted successfully!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (val s = state) {
                            is PptxLoadState.Success -> s.fileName
                            else -> "Presentation Viewer"
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
                    if (state is PptxLoadState.Success) {
                        IconButton(onClick = {
                            if (isEditMode) {
                                viewModel.commitChanges()
                            }
                            isEditMode = !isEditMode
                        }) {
                            Icon(
                                imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = "Toggle Edit Mode",
                                tint = if (isEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            if (state is PptxLoadState.Success) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PptxActionColumnButton(icon = Icons.Default.OpenInNew, title = "Open in...") {
                            try {
                                val file = File(fileUri)
                                val fileUriProvider = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(fileUriProvider, "application/vnd.openxmlformats-officedocument.presentationml.presentation")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(openIntent, "Open PPTX In"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }

                        PptxActionColumnButton(icon = Icons.Default.Print, title = "Print") {
                            coroutineScope.launch {
                                val tempPdfFile = File(context.cacheDir, "temp_print_${System.currentTimeMillis()}.pdf")
                                try {
                                    withContext(Dispatchers.IO) {
                                        com.karnadigital.omnisuite.core.engine.document.OfficeConverter.convertPptxToPdf(context, File(fileUri), tempPdfFile, "image")
                                    }
                                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
                                    val jobName = "OmniSuite Presentation Print"
                                    printManager.print(
                                        jobName,
                                        PptxPrintDocumentAdapter(context, tempPdfFile),
                                        null
                                    )
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Print failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }

                        PptxActionColumnButton(icon = Icons.Default.Share, title = "Share") {
                            try {
                                val file = File(fileUri)
                                val fileUriProvider = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                                    putExtra(Intent.EXTRA_STREAM, fileUriProvider)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Presentation"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }

                        var showQuickToolsMenu by remember { mutableStateOf(false) }
                        Box {
                            PptxActionColumnButton(icon = Icons.Default.Build, title = "Quick Tools") {
                                showQuickToolsMenu = true
                            }
                            DropdownMenu(
                                expanded = showQuickToolsMenu,
                                onDismissRequest = { showQuickToolsMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("📕 Convert to PDF (Image Mode)") },
                                    onClick = {
                                        showQuickToolsMenu = false
                                        coroutineScope.launch {
                                            val tempPdfFile = File(context.cacheDir, "temp_conv_${System.currentTimeMillis()}.pdf")
                                            try {
                                                withContext(Dispatchers.IO) {
                                                    com.karnadigital.omnisuite.core.engine.document.OfficeConverter.convertPptxToPdf(context, File(fileUri), tempPdfFile, "image")
                                                }
                                                // Save to public Documents/OmniSuite/
                                                val savedUri = com.karnadigital.omnisuite.core.util.FileOutputManager.saveToDefault(
                                                    context = context,
                                                    bytes = tempPdfFile.readBytes(),
                                                    filename = File(fileUri).name.substringBeforeLast(".") + "_converted.pdf",
                                                    mimeType = "application/pdf",
                                                    subfolder = ""
                                                )
                                                if (savedUri != null) {
                                                    Toast.makeText(context, "Presentation saved under Documents/OmniSuite!", Toast.LENGTH_LONG).show()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            } finally {
                                                if (tempPdfFile.exists()) tempPdfFile.delete()
                                            }
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("📄 Convert to PDF (Text Reflow)") },
                                    onClick = {
                                        showQuickToolsMenu = false
                                        coroutineScope.launch {
                                            val tempPdfFile = File(context.cacheDir, "temp_conv_${System.currentTimeMillis()}.pdf")
                                            try {
                                                withContext(Dispatchers.IO) {
                                                    com.karnadigital.omnisuite.core.engine.document.OfficeConverter.convertPptxToPdf(context, File(fileUri), tempPdfFile, "text")
                                                }
                                                // Save to public Documents/OmniSuite/
                                                val savedUri = com.karnadigital.omnisuite.core.util.FileOutputManager.saveToDefault(
                                                    context = context,
                                                    bytes = tempPdfFile.readBytes(),
                                                    filename = File(fileUri).name.substringBeforeLast(".") + "_reflowed.pdf",
                                                    mimeType = "application/pdf",
                                                    subfolder = ""
                                                )
                                                if (savedUri != null) {
                                                    Toast.makeText(context, "Presentation saved under Documents/OmniSuite!", Toast.LENGTH_LONG).show()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            } finally {
                                                if (tempPdfFile.exists()) tempPdfFile.delete()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
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
                is PptxLoadState.Loading -> {
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
                            text = "Reflowing slides deck...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }
                }
                is PptxLoadState.Success -> {
                    val presentation = currentState.presentation
                    if (presentation.slides.isEmpty()) {
                        EmptyPresentationState()
                    } else {
                        val pagerState = rememberPagerState(pageCount = { presentation.slides.size })
                        
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Swipable Slides horizontal pager with premium scale/fade transitions
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 24.dp),
                                pageSpacing = 16.dp
                            ) { pageIndex ->
                                val slide = presentation.slides[pageIndex]

                                // Smooth scale and opacity transformation during page swipes
                                val pageOffset = (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                                val scale = 1f - (Math.abs(pageOffset) * 0.12f).coerceIn(0f, 0.12f)
                                val alpha = 1f - (Math.abs(pageOffset) * 0.4f).coerceIn(0f, 0.4f)

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                            this.alpha = alpha
                                        }
                                ) {
                                    ZoomableBox(modifier = Modifier.fillMaxSize()) {
                                        SlideCardItem(
                                            slide = slide,
                                            isEditMode = isEditMode,
                                            onTextBlockClick = { textBlock, isTitle, blockIdx ->
                                                blockToEdit = textBlock
                                                activeIndexToEdit = pageIndex
                                                isTitleEdit = isTitle
                                                blockIndexToEdit = blockIdx
                                                showFormatter = true
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Slide counter
                            Text(
                                text = "Slide ${pagerState.currentPage + 1} of ${presentation.slides.size}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Scrollable horizontal slide thumbnail strip drawer
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(presentation.slides.size) { index ->
                                    val isActive = pagerState.currentPage == index
                                    val borderStroke = if (isActive) {
                                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                    } else {
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    }
                                    val opacity = if (isActive) 1f else 0.6f

                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        border = borderStroke,
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        modifier = Modifier
                                            .width(72.dp)
                                            .height(48.dp)
                                            .clickable {
                                                coroutineScope.launch {
                                                    pagerState.animateScrollToPage(index)
                                                }
                                            }
                                            .graphicsLayer { this.alpha = opacity }
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Text(
                                                text = "${index + 1}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Render slide comment note at the bottom
                            val currentSlide = presentation.slides[pagerState.currentPage]
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Comment Notes",
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Slide Notes & Comments",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val displayComment = currentSlide.comment
                                    if (!displayComment.isNullOrBlank()) {
                                        Text(
                                            text = displayComment,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Text(
                                            text = "No slide notes recorded.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }

                            // Text Formatter dialog overlay
                            if (showFormatter && blockToEdit != null && activeIndexToEdit != null) {
                                val slide = presentation.slides[activeIndexToEdit!!]
                                PptxTextFormatterDialog(
                                    slideIndex = activeIndexToEdit!!,
                                    textBlock = blockToEdit!!,
                                    isTitle = isTitleEdit,
                                    blockIndex = blockIndexToEdit,
                                    initialComment = slide.comment,
                                    onDismiss = { showFormatter = false },
                                    onSave = { newText, isBold, isItalic, isUnderline, textColorHex, comment ->
                                        viewModel.updateSlideTextShape(
                                            slideIndex = activeIndexToEdit!!,
                                            isTitle = isTitleEdit,
                                            blockIndex = blockIndexToEdit,
                                            newText = newText,
                                            isBold = isBold,
                                            isItalic = isItalic,
                                            isUnderline = isUnderline,
                                            textColorHex = textColorHex,
                                            comment = comment
                                        )
                                        showFormatter = false
                                    },
                                    onInsertImageClick = {
                                        imagePickerLauncher.launch("image/*")
                                        showFormatter = false
                                    }
                                )
                            }
                        }
                    }
                }
                is PptxLoadState.Error -> {
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
                            text = "Slides Read Error",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PptxTextFormatterDialog(
    slideIndex: Int,
    textBlock: PptxTextBlock,
    isTitle: Boolean,
    blockIndex: Int,
    initialComment: String?,
    onDismiss: () -> Unit,
    onSave: (
        newText: String,
        isBold: Boolean,
        isItalic: Boolean,
        isUnderline: Boolean,
        textColorHex: String?,
        comment: String?
    ) -> Unit,
    onInsertImageClick: () -> Unit
) {
    var text by remember { mutableStateOf(textBlock.text) }
    var isBold by remember { mutableStateOf(textBlock.isBold) }
    var isItalic by remember { mutableStateOf(textBlock.isItalic) }
    var isUnderline by remember { mutableStateOf(textBlock.isUnderline) }
    var textColorHex by remember { mutableStateOf(textBlock.textColorHex) }
    var comment by remember { mutableStateOf(initialComment ?: "") }

    val colors = listOf(
        "#000000", // Black
        "#2196F3", // Blue
        "#4CAF50", // Green
        "#F44336", // Red
        "#FFEB3B", // Yellow
        "#9C27B0", // Purple
        "#FF9800", // Orange
        "#00BCD4"  // Cyan
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isTitle) "Format Slide Title" else "Format Text Bullet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Text input
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text Content") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )

                // Formatting toggles
                Text(
                    text = "Text Styling",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledIconToggleButton(
                        checked = isBold,
                        onCheckedChange = { isBold = it },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Text("B", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    FilledIconToggleButton(
                        checked = isItalic,
                        onCheckedChange = { isItalic = it },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Text("I", style = MaterialTheme.typography.bodyLarge.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), fontSize = 16.sp)
                    }

                    FilledIconToggleButton(
                        checked = isUnderline,
                        onCheckedChange = { isUnderline = it },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Text("U", style = MaterialTheme.typography.bodyLarge.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), fontSize = 16.sp)
                    }
                }

                // Color picker swatches
                Text(
                    text = "Text Color",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    colors.forEach { hex ->
                        val color = Color(android.graphics.Color.parseColor(hex))
                        val isSelected = textColorHex?.lowercase() == hex.lowercase()
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                                .clickable {
                                    textColorHex = if (isSelected) null else hex
                                }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                // Comment input
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Slide Notes / Comment") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                // Insert Picture Button
                Button(
                    onClick = onInsertImageClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "Insert Image", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Insert Picture Run")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(text, isBold, isItalic, isUnderline, textColorHex, comment)
            }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SlideCardItem(
    slide: PptxSlide,
    isEditMode: Boolean = false,
    onTextBlockClick: (PptxTextBlock, isTitle: Boolean, blockIndex: Int) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .fillMaxSize()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            // Slide Title
            val titleColor = slide.title.textColorHex?.let {
                try { Color(android.graphics.Color.parseColor(it)) } catch (e: Exception) { MaterialTheme.colorScheme.primary }
            } ?: MaterialTheme.colorScheme.primary

            Text(
                text = slide.title.text,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = if (slide.title.isBold) FontWeight.ExtraBold else FontWeight.Bold,
                    fontStyle = if (slide.title.isItalic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                    textDecoration = if (slide.title.isUnderline) androidx.compose.ui.text.style.TextDecoration.Underline else androidx.compose.ui.text.style.TextDecoration.None,
                    lineHeight = 32.sp
                ),
                color = titleColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = isEditMode) {
                        onTextBlockClick(slide.title, true, -1)
                    }
                    .background(if (isEditMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                    .border(
                        width = if (isEditMode) 1.dp else 0.dp,
                        color = if (isEditMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(if (isEditMode) 8.dp else 0.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(20.dp))

            // Slide text points / bullet points
            if (slide.textBlocks.isEmpty() && slide.imageUrls.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "[Blank Slide]",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    slide.textBlocks.forEachIndexed { idx, block ->
                        val blockColor = block.textColorHex?.let {
                            try { Color(android.graphics.Color.parseColor(it)) } catch (e: Exception) { MaterialTheme.colorScheme.onSurface }
                        } ?: MaterialTheme.colorScheme.onSurface

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = isEditMode) {
                                    onTextBlockClick(block, false, idx)
                                }
                                .background(if (isEditMode) MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f) else Color.Transparent)
                                .border(
                                    width = if (isEditMode) 1.dp else 0.dp,
                                    color = if (isEditMode) MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f) else Color.Transparent,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(if (isEditMode) 8.dp else 0.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "• ",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 24.sp
                                ),
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = block.text,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (block.isBold) FontWeight.Bold else FontWeight.Normal,
                                    fontStyle = if (block.isItalic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                                    textDecoration = if (block.isUnderline) androidx.compose.ui.text.style.TextDecoration.Underline else androidx.compose.ui.text.style.TextDecoration.None,
                                    lineHeight = 24.sp
                                ),
                                color = blockColor
                            )
                        }
                    }
                }
            }

            // Slide images
            if (slide.imageUrls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Slide Images",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    slide.imageUrls.forEach { imgPath ->
                        AsyncImage(
                            model = File(imgPath),
                            contentDescription = "Slide Image",
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyPresentationState() {
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
            text = "Presentation Contains No Slides",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

private @Composable
fun PptxActionColumnButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = title, tint = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(4.dp))
        Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

private class PptxPrintDocumentAdapter(private val context: Context, private val file: File) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder("print_output.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .build()
        callback?.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        var input: java.io.InputStream? = null
        var output: java.io.OutputStream? = null
        try {
            input = java.io.FileInputStream(file)
            output = java.io.FileOutputStream(destination?.fileDescriptor)
            val buffer = ByteArray(1024)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } >= 0) {
                output.write(buffer, 0, bytesRead)
            }
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback?.onWriteFailed(e.localizedMessage)
        } finally {
            try { input?.close() } catch(e: Exception) {}
            try { output?.close() } catch(e: Exception) {}
        }
    }
}

