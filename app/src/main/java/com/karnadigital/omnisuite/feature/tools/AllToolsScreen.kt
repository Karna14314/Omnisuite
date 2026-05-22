package com.karnadigital.omnisuite.feature.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllToolsScreen(
    onBack: () -> Unit = {},
    isInline: Boolean = false, // If true, hides the top app bar for seamless inline tab presentation
    onNavigateToPdfTools: () -> Unit,
    onNavigateToSignaturePad: () -> Unit,
    onNavigateToWatermark: () -> Unit,
    onNavigateToImageTools: () -> Unit,
    onNavigateToQrGenerator: () -> Unit,
    onNavigateToBarcodeScanner: () -> Unit,
    onNavigateToOcr: () -> Unit,
    onNavigateToBatchTools: () -> Unit,
    onSelectFileForType: (String) -> Unit // Resolves picking files for viewers
) {
    var selectedTabState by remember { mutableStateOf(0) }
    val tabs = listOf("PDF", "Documents", "Image", "Utilities")

    Scaffold(
        topBar = {
            if (!isInline) {
                TopAppBar(
                    title = {
                        Text(
                            text = "All Tools Suite",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
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
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isInline) PaddingValues(0.dp) else innerPadding)
        ) {
            // Horizontal scrolling category headers
            ScrollableTabRow(
                selectedTabIndex = selectedTabState,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabState]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabState == index,
                        onClick = { selectedTabState = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTabState == index) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 15.sp
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                when (selectedTabState) {
                    0 -> PdfToolsGrid(
                        onNavigateToPdfTools = onNavigateToPdfTools,
                        onNavigateToSignaturePad = onNavigateToSignaturePad,
                        onNavigateToWatermark = onNavigateToWatermark
                    )
                    1 -> DocumentsToolsGrid(
                        onSelectFileForType = onSelectFileForType
                    )
                    2 -> ImageToolsGrid(
                        onNavigateToImageTools = onNavigateToImageTools,
                        onNavigateToOcr = onNavigateToOcr,
                        onNavigateToBarcodeScanner = onNavigateToBarcodeScanner
                    )
                    3 -> UtilitiesToolsGrid(
                        onNavigateToQrGenerator = onNavigateToQrGenerator,
                        onNavigateToBarcodeScanner = onNavigateToBarcodeScanner,
                        onNavigateToBatchTools = onNavigateToBatchTools
                    )
                }
            }
        }
    }
}

@Composable
fun PdfToolsGrid(
    onNavigateToPdfTools: () -> Unit,
    onNavigateToSignaturePad: () -> Unit,
    onNavigateToWatermark: () -> Unit
) {
    val items = listOf(
        ToolItem("Merge PDFs", "Combine multiple files", "🥞", Color(0xFFEF4444), onNavigateToPdfTools),
        ToolItem("Split PDF", "Extract custom page ranges", "✂️", Color(0xFFEF4444), onNavigateToPdfTools),
        ToolItem("Encrypt PDF", "Lock with secure password", "🔒", Color(0xFFEF4444), onNavigateToPdfTools),
        ToolItem("Digital Sign", "Stamp digital signature", "✍️", Color(0xFFEF4444), onNavigateToSignaturePad),
        ToolItem("Watermark", "Add security stamp overlay", "🎨", Color(0xFFEF4444), onNavigateToWatermark),
        ToolItem("Annotate PDF", "Mark, highlight & add text", "🖋️", Color(0xFFEF4444), onNavigateToPdfTools),
        ToolItem("Image to PDF", "Convert images directly to PDF", "📸", Color(0xFFEF4444), onNavigateToPdfTools),
        ToolItem("Doc to PDF", "Transcode Office files to PDF", "💾", Color(0xFFEF4444), onNavigateToPdfTools)
    )

    ToolsLazyGrid(items)
}

@Composable
fun DocumentsToolsGrid(
    onSelectFileForType: (String) -> Unit
) {
    val items = listOf(
        ToolItem("Word Viewer", "Open and read DOCX files", "📝", Color(0xFF3B82F6)) { onSelectFileForType("word") },
        ToolItem("Excel Viewer", "View spreadsheet XLSX cells", "📊", Color(0xFF10B981)) { onSelectFileForType("excel") },
        ToolItem("Slides Viewer", "Launch PPTX presentation", "🖼️", Color(0xFFF59E0B)) { onSelectFileForType("slides") },
        ToolItem("Text Editor", "Read and edit local TXT files", "📄", Color(0xFF64748B)) { onSelectFileForType("text") },
        ToolItem("Word Count", "Analyze document metrics", "🧮", Color(0xFF3B82F6)) { onSelectFileForType("word") },
        ToolItem("CSV Editor", "Edit and parse CSV grids", "📅", Color(0xFF10B981)) { onSelectFileForType("csv") }
    )

    ToolsLazyGrid(items)
}

@Composable
fun ImageToolsGrid(
    onNavigateToImageTools: () -> Unit,
    onNavigateToOcr: () -> Unit,
    onNavigateToBarcodeScanner: () -> Unit
) {
    val items = listOf(
        ToolItem("Image Lab", "Compress & format convert", "🖼️", Color(0xFF8B5CF6), onNavigateToImageTools),
        ToolItem("Text OCR", "Extract text offline with ML Kit", "🔬", Color(0xFF8B5CF6), onNavigateToOcr),
        ToolItem("Smart Scan", "Auto edge-detect page camera", "📷", Color(0xFF8B5CF6), onNavigateToBarcodeScanner),
        ToolItem("Crop Image", "Adjust custom proportions", "✂️", Color(0xFF8B5CF6), onNavigateToImageTools),
        ToolItem("Format Transcoder", "PNG, JPEG, WEBP conversions", "🔄", Color(0xFF8B5CF6), onNavigateToImageTools),
        ToolItem("Lossless Resize", "Fine-grain dimension control", "📐", Color(0xFF8B5CF6), onNavigateToImageTools)
    )

    ToolsLazyGrid(items)
}

@Composable
fun UtilitiesToolsGrid(
    onNavigateToQrGenerator: () -> Unit,
    onNavigateToBarcodeScanner: () -> Unit,
    onNavigateToBatchTools: () -> Unit
) {
    val items = listOf(
        ToolItem("QR Generator", "Compile WiFi/vCard QR codes", "🧬", Color(0xFF06B6D4), onNavigateToQrGenerator),
        ToolItem("QR Scanner", "Live viewfinder decoding", "📷", Color(0xFF06B6D4), onNavigateToBarcodeScanner),
        ToolItem("Barcode Builder", "Generate EAN/UPC barcodes", "📊", Color(0xFF06B6D4), onNavigateToQrGenerator),
        ToolItem("Batch Toolkit", "Optimize multiple actions", "⚡", Color(0xFF06B6D4), onNavigateToBatchTools)
    )

    ToolsLazyGrid(items)
}

@Composable
fun ToolsLazyGrid(items: List<ToolItem>) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items.size) { index ->
            val item = items[index]
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { item.onClick() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(item.color.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.iconText,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

data class ToolItem(
    val title: String,
    val description: String,
    val iconText: String,
    val color: Color,
    val onClick: () -> Unit
)
