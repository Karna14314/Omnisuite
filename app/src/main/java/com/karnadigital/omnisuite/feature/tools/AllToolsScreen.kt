package com.karnadigital.omnisuite.feature.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karnadigital.omnisuite.feature.home.NavigationEvent
import com.karnadigital.omnisuite.ui.component.ToolListRow
import com.karnadigital.omnisuite.ui.theme.OmniColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllToolsScreen(
    onBack: () -> Unit = {},
    isInline: Boolean = false,
    onEvent: (NavigationEvent) -> Unit,
    onSelectFileForType: (String) -> Unit
) {
    var selectedTabState by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf("📋 PDF", "📝 Word", "📊 Excel", "🖼️ Slides", "🖼 Image", "📦 Archive")

    val activeIndicatorColor = when (selectedTabState) {
        0 -> OmniColors.PdfRed
        1 -> OmniColors.DocBlue
        2 -> OmniColors.XlsGreen
        3 -> Color(0xFFF59E0B)
        4 -> OmniColors.ImgPurple
        5 -> OmniColors.ArcCyan
        else -> OmniColors.Accent
    }

    Scaffold(
        topBar = {
            if (!isInline) {
                TopAppBar(
                    title = {
                        Text(
                            text = "All Tools Suite",
                            fontWeight = FontWeight.Bold,
                            color = OmniColors.TextPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Navigate back",
                                tint = OmniColors.TextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = OmniColors.Bg
                    )
                )
            }
        },
        containerColor = OmniColors.Bg,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isInline) PaddingValues(0.dp) else innerPadding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabState,
                containerColor = OmniColors.Surface,
                contentColor = OmniColors.TextPrimary,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabState]),
                        color = activeIndicatorColor
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
                                fontSize = 14.sp,
                                color = if (selectedTabState == index) activeIndicatorColor else OmniColors.TextMuted
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
                    0 -> PdfToolsList(onEvent)
                    1 -> WordToolsList(onSelectFileForType, onEvent)
                    2 -> ExcelToolsList(onSelectFileForType, onEvent)
                    3 -> SlidesToolsList(onSelectFileForType, onEvent)
                    4 -> ImageToolsList(onEvent)
                    5 -> ArchiveQrToolsList(onEvent, onSelectFileForType)
                }
            }
        }
    }
}

@Composable
fun PdfToolsList(onEvent: (NavigationEvent) -> Unit) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { ToolListRow("🥞", "Merge PDFs", "Combine multiple files", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfMerge) }) }
        item { ToolListRow("✂️", "Split PDF", "Extract page ranges", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfSplit) }) }
        item { ToolListRow("🔒", "Encrypt PDF", "Lock with secure password", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfLock) }) }
        item { ToolListRow("🔓", "Decrypt PDF", "Remove PDF password lock offline", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfDecrypt) }) }
        item { ToolListRow("🔄", "Rotate PDF Pages", "Rotate visual page layout preview", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfRotate) }) }
        item { ToolListRow("✂️", "Extract PDF Pages", "Select and extract pages to new PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfExtract) }) }
        item { ToolListRow("🗑️", "Delete PDF Pages", "Select and remove pages from PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfDelete) }) }
        item { ToolListRow("✍️", "Digital Sign", "Stamp digital signature", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToSignaturePad) }) }
        item { ToolListRow("💧", "Watermark", "Add security stamp overlay", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToWatermark) }) }
        item { ToolListRow("📕", "Images to PDF", "Compile multiple photos into PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToImagesToPdf) }) }
        item { ToolListRow("📑", "Doc to PDF", "Transcode Word files to PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToDocToPdf) }) }
        item { ToolListRow("🖼️", "Slides to PDF", "Transcode PPTX files to PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPptToPdf) }) }
        item { ToolListRow("📷", "Scan to PDF", "Compile camera scans to PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToScanToPdf) }) }
        item { ToolListRow("🖨️", "PDF to Images", "Extract PDF pages to PNGs", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfToImages) }) }
        item { ToolListRow("📝", "PDF to Word", "Convert PDF to Word offline", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfToWord) }) }
        item { ToolListRow("🖼️", "PDF to PPT", "Convert PDF to Slides offline", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfToPpt) }) }
        item { ToolListRow("📊", "PDF to Excel", "Convert PDF to Sheets offline", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfToExcel) }) }
        item { ToolListRow("✍️", "Fill Form", "Fill PDF interactive form fields", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfFormFiller) }) }
        item { ToolListRow("🗜️", "Compress PDF", "Reduce PDF file size offline", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfCompress) }) }
        item { ToolListRow("🔒", "Flatten PDF", "Flatten interactive form fields", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfFlatten) }) }
        item { ToolListRow("📊", "Excel to PDF", "Transcode Excel sheets to PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToXlsToPdf) }) }
        item { ToolListRow("🌐", "Web to PDF", "Render URL layouts to PDF offline", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToWebToPdf) }) }
        item { ToolListRow("<html>", "HTML to PDF", "Compile custom HTML text to PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToHtmlToPdf) }) }
    }
}

@Composable
fun WordToolsList(
    onSelectFileForType: (String) -> Unit,
    onEvent: (NavigationEvent) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { ToolListRow("📝", "Word Viewer", "Open and read DOCX files", OmniColors.DocBlue, { onSelectFileForType("word") }) }
        item { ToolListRow("📄", "Text Editor", "Read and edit local TXT files", OmniColors.TextMuted, { onSelectFileForType("text") }) }
        item { ToolListRow("🧮", "Word Count", "Analyze document metrics", OmniColors.DocBlue, { onSelectFileForType("word") }) }
        item { ToolListRow("📄", "DOCX to TXT", "Extract text blocks to TXT file", OmniColors.DocBlue, { onEvent(NavigationEvent.NavigateToDocxToTxt) }) }
        item { ToolListRow("✍️", "Markdown to PDF", "Format markdown text to PDF", OmniColors.DocBlue, { onEvent(NavigationEvent.NavigateToMarkdownToPdf) }) }
    }
}

@Composable
fun ExcelToolsList(
    onSelectFileForType: (String) -> Unit,
    onEvent: (NavigationEvent) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { ToolListRow("📊", "Excel Viewer", "View spreadsheet XLSX cells", OmniColors.XlsGreen, { onSelectFileForType("excel") }) }
        item { ToolListRow("📅", "CSV Editor", "Edit and parse CSV grids", OmniColors.XlsGreen, { onSelectFileForType("csv") }) }
        item { ToolListRow("📤", "CSV to Excel", "Import CSV records to Excel workbook", OmniColors.XlsGreen, { onEvent(NavigationEvent.NavigateToCsvToXlsx) }) }
        item { ToolListRow("📥", "Excel to CSV", "Export workbook sheet cells to CSV", OmniColors.XlsGreen, { onEvent(NavigationEvent.NavigateToXlsxToCsv) }) }
    }
}

@Composable
fun SlidesToolsList(
    onSelectFileForType: (String) -> Unit,
    onEvent: (NavigationEvent) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { ToolListRow("🖼️", "Slides Viewer", "Launch PPTX presentation", Color(0xFFF59E0B), { onSelectFileForType("slides") }) }
        item { ToolListRow("📄", "PPTX to TXT", "Extract presentation slides text to TXT", Color(0xFFF59E0B), { onEvent(NavigationEvent.NavigateToPptxToTxt) }) }
    }
}

@Composable
fun ImageToolsList(onEvent: (NavigationEvent) -> Unit) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { ToolListRow("🖼️", "Image Lab", "Compress & format convert", OmniColors.ImgPurple, { onEvent(NavigationEvent.NavigateToImageTools) }) }
        item { ToolListRow("🔬", "Text OCR", "Extract text offline with ML Kit", OmniColors.ImgPurple, { onEvent(NavigationEvent.NavigateToOcr) }) }
        item { ToolListRow("📷", "Smart Scan", "Auto edge-detect page camera", OmniColors.ImgPurple, { onEvent(NavigationEvent.NavigateToBarcodeScanner) }) }
        item { ToolListRow("✂️", "Crop Image", "Adjust custom proportions", OmniColors.ImgPurple, { onEvent(NavigationEvent.NavigateToImageTools) }) }
        item { ToolListRow("🔄", "Format Transcoder", "PNG, JPEG, WEBP conversions", OmniColors.ImgPurple, { onEvent(NavigationEvent.NavigateToImageTools) }) }
        item { ToolListRow("📐", "Lossless Resize", "Fine-grain dimension control", OmniColors.ImgPurple, { onEvent(NavigationEvent.NavigateToImageTools) }) }
    }
}

@Composable
fun ArchiveQrToolsList(
    onEvent: (NavigationEvent) -> Unit,
    onSelectFileForType: (String) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { ToolListRow("🗜️", "ZIP Maker", "Compress multiple files to ZIP", OmniColors.ArcCyan, { onEvent(NavigationEvent.NavigateToZipMaker) }) }
        item { ToolListRow("🔓", "ZIP Extractor", "Extract local ZIP archives", OmniColors.ArcCyan, { onSelectFileForType("zip") }) }
        item { ToolListRow("📦", "TAR Archiver", "Create or unpack offline TAR archives", OmniColors.ArcCyan, { onEvent(NavigationEvent.NavigateToTarTools) }) }
        item { ToolListRow("🧬", "QR Generator", "Compile WiFi/vCard QR codes", OmniColors.ArcCyan, { onEvent(NavigationEvent.NavigateToQrGenerator) }) }
        item { ToolListRow("📷", "QR Scanner", "Live viewfinder decoding", OmniColors.ArcCyan, { onEvent(NavigationEvent.NavigateToBarcodeScanner) }) }
        item { ToolListRow("📊", "Barcode Builder", "Generate EAN/UPC barcodes", OmniColors.ArcCyan, { onEvent(NavigationEvent.NavigateToQrGenerator) }) }
        item { ToolListRow("⚡", "Batch Toolkit", "Optimize multiple actions", OmniColors.ArcCyan, { onEvent(NavigationEvent.NavigateToBatchTools) }) }
    }
}
