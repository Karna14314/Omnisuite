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
import com.karnadigital.omnisuite.ui.component.ToolListRow
import com.karnadigital.omnisuite.ui.theme.OmniColors

/**
 * Redesigned Categorized All Tools cockpit dashboard.
 * Compliant with tab row emojis, active semantic indicator colors,
 * and high-scannability full-width ToolListRow listings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllToolsScreen(
    onBack: () -> Unit = {},
    isInline: Boolean = false, // If true, hides the top app bar for seamless inline tab presentation
    onNavigateToPdfMerge: () -> Unit,
    onNavigateToPdfSplit: () -> Unit,
    onNavigateToPdfLock: () -> Unit,
    onNavigateToDocToPdf: () -> Unit,
    onNavigateToPptToPdf: () -> Unit,
    onNavigateToScanToPdf: () -> Unit,
    onNavigateToPdfToImages: () -> Unit,
    onNavigateToSignaturePad: () -> Unit,
    onNavigateToWatermark: () -> Unit,
    onNavigateToPdfToWord: () -> Unit,
    onNavigateToPdfToPpt: () -> Unit,
    onNavigateToPdfToExcel: () -> Unit,
    onNavigateToPdfFormFiller: () -> Unit,
    onNavigateToImagesToPdf: () -> Unit,
    onNavigateToPdfCompress: () -> Unit,
    onNavigateToPdfFlatten: () -> Unit,
    onNavigateToXlsToPdf: () -> Unit,
    onNavigateToImageTools: () -> Unit,
    onNavigateToQrGenerator: () -> Unit,
    onNavigateToBarcodeScanner: () -> Unit,
    onNavigateToOcr: () -> Unit,
    onNavigateToBatchTools: () -> Unit,
    onNavigateToZipMaker: () -> Unit,
    onSelectFileForType: (String) -> Unit, // Resolves picking files for viewers
    // New parameters for upgraded tools
    onNavigateToPdfDecrypt: () -> Unit,
    onNavigateToPdfRotate: () -> Unit,
    onNavigateToPdfExtract: () -> Unit,
    onNavigateToPdfDelete: () -> Unit,
    onNavigateToWebToPdf: () -> Unit,
    onNavigateToHtmlToPdf: () -> Unit,
    onNavigateToMarkdownToPdf: () -> Unit,
    onNavigateToDocxToTxt: () -> Unit,
    onNavigateToCsvToXlsx: () -> Unit,
    onNavigateToXlsxToCsv: () -> Unit,
    onNavigateToPptxToTxt: () -> Unit,
    onNavigateToTarTools: () -> Unit
) {
    var selectedTabState by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf("📋 PDF", "📝 Word", "📊 Excel", "🖼️ Slides", "🖼 Image", "📦 Archive")
    
    // Resolve dynamic active indicator color based on current tab selection
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
            // Horizontal scrolling tab selectors with dynamic indicator coloring
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

            // Sub-grids replaced with linear list layouts for enhanced reading flow
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                when (selectedTabState) {
                    0 -> PdfToolsList(
                        onNavigateToPdfMerge = onNavigateToPdfMerge,
                        onNavigateToPdfSplit = onNavigateToPdfSplit,
                        onNavigateToPdfLock = onNavigateToPdfLock,
                        onNavigateToDocToPdf = onNavigateToDocToPdf,
                        onNavigateToPptToPdf = onNavigateToPptToPdf,
                        onNavigateToScanToPdf = onNavigateToScanToPdf,
                        onNavigateToPdfToImages = onNavigateToPdfToImages,
                        onNavigateToSignaturePad = onNavigateToSignaturePad,
                        onNavigateToWatermark = onNavigateToWatermark,
                        onNavigateToPdfToWord = onNavigateToPdfToWord,
                        onNavigateToPdfToPpt = onNavigateToPdfToPpt,
                        onNavigateToPdfToExcel = onNavigateToPdfToExcel,
                        onNavigateToPdfFormFiller = onNavigateToPdfFormFiller,
                        onNavigateToImagesToPdf = onNavigateToImagesToPdf,
                        onNavigateToPdfCompress = onNavigateToPdfCompress,
                        onNavigateToPdfFlatten = onNavigateToPdfFlatten,
                        onNavigateToXlsToPdf = onNavigateToXlsToPdf,
                        onNavigateToPdfDecrypt = onNavigateToPdfDecrypt,
                        onNavigateToPdfRotate = onNavigateToPdfRotate,
                        onNavigateToPdfExtract = onNavigateToPdfExtract,
                        onNavigateToPdfDelete = onNavigateToPdfDelete,
                        onNavigateToWebToPdf = onNavigateToWebToPdf,
                        onNavigateToHtmlToPdf = onNavigateToHtmlToPdf
                    )
                    1 -> WordToolsList(
                        onSelectFileForType = onSelectFileForType,
                        onNavigateToDocxToTxt = onNavigateToDocxToTxt,
                        onNavigateToMarkdownToPdf = onNavigateToMarkdownToPdf
                    )
                    2 -> ExcelToolsList(
                        onSelectFileForType = onSelectFileForType,
                        onNavigateToCsvToXlsx = onNavigateToCsvToXlsx,
                        onNavigateToXlsxToCsv = onNavigateToXlsxToCsv
                    )
                    3 -> SlidesToolsList(
                        onSelectFileForType = onSelectFileForType,
                        onNavigateToPptxToTxt = onNavigateToPptxToTxt
                    )
                    4 -> ImageToolsList(
                        onNavigateToImageTools = onNavigateToImageTools,
                        onNavigateToOcr = onNavigateToOcr,
                        onNavigateToBarcodeScanner = onNavigateToBarcodeScanner
                    )
                    5 -> ArchiveQrToolsList(
                        onNavigateToQrGenerator = onNavigateToQrGenerator,
                        onNavigateToBarcodeScanner = onNavigateToBarcodeScanner,
                        onNavigateToBatchTools = onNavigateToBatchTools,
                        onNavigateToZipMaker = onNavigateToZipMaker,
                        onSelectFileForType = onSelectFileForType,
                        onNavigateToTarTools = onNavigateToTarTools
                    )
                }
            }
        }
    }
}

@Composable
fun PdfToolsList(
    onNavigateToPdfMerge: () -> Unit,
    onNavigateToPdfSplit: () -> Unit,
    onNavigateToPdfLock: () -> Unit,
    onNavigateToDocToPdf: () -> Unit,
    onNavigateToPptToPdf: () -> Unit,
    onNavigateToScanToPdf: () -> Unit,
    onNavigateToPdfToImages: () -> Unit,
    onNavigateToSignaturePad: () -> Unit,
    onNavigateToWatermark: () -> Unit,
    onNavigateToPdfToWord: () -> Unit,
    onNavigateToPdfToPpt: () -> Unit,
    onNavigateToPdfToExcel: () -> Unit,
    onNavigateToPdfFormFiller: () -> Unit,
    onNavigateToImagesToPdf: () -> Unit,
    onNavigateToPdfCompress: () -> Unit,
    onNavigateToPdfFlatten: () -> Unit,
    onNavigateToXlsToPdf: () -> Unit,
    onNavigateToPdfDecrypt: () -> Unit,
    onNavigateToPdfRotate: () -> Unit,
    onNavigateToPdfExtract: () -> Unit,
    onNavigateToPdfDelete: () -> Unit,
    onNavigateToWebToPdf: () -> Unit,
    onNavigateToHtmlToPdf: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { ToolListRow("🥞", "Merge PDFs", "Combine multiple files", OmniColors.PdfRed, onNavigateToPdfMerge) }
        item { ToolListRow("✂️", "Split PDF", "Extract page ranges", OmniColors.PdfRed, onNavigateToPdfSplit) }
        item { ToolListRow("🔒", "Encrypt PDF", "Lock with secure password", OmniColors.PdfRed, onNavigateToPdfLock) }
        item { ToolListRow("🔓", "Decrypt PDF", "Remove PDF password lock offline", OmniColors.PdfRed, onNavigateToPdfDecrypt) }
        item { ToolListRow("🔄", "Rotate PDF Pages", "Rotate visual page layout preview", OmniColors.PdfRed, onNavigateToPdfRotate) }
        item { ToolListRow("✂️", "Extract PDF Pages", "Select and extract pages to new PDF", OmniColors.PdfRed, onNavigateToPdfExtract) }
        item { ToolListRow("🗑️", "Delete PDF Pages", "Select and remove pages from PDF", OmniColors.PdfRed, onNavigateToPdfDelete) }
        item { ToolListRow("✍️", "Digital Sign", "Stamp digital signature", OmniColors.PdfRed, onNavigateToSignaturePad) }
        item { ToolListRow("💧", "Watermark", "Add security stamp overlay", OmniColors.PdfRed, onNavigateToWatermark) }
        item { ToolListRow("📕", "Images to PDF", "Compile multiple photos into PDF", OmniColors.PdfRed, onNavigateToImagesToPdf) }
        item { ToolListRow("📑", "Doc to PDF", "Transcode Word files to PDF", OmniColors.PdfRed, onNavigateToDocToPdf) }
        item { ToolListRow("🖼️", "Slides to PDF", "Transcode PPTX files to PDF", OmniColors.PdfRed, onNavigateToPptToPdf) }
        item { ToolListRow("📷", "Scan to PDF", "Compile camera scans to PDF", OmniColors.PdfRed, onNavigateToScanToPdf) }
        item { ToolListRow("🖨️", "PDF to Images", "Extract PDF pages to PNGs", OmniColors.PdfRed, onNavigateToPdfToImages) }
        item { ToolListRow("📝", "PDF to Word", "Convert PDF to Word offline", OmniColors.PdfRed, onNavigateToPdfToWord) }
        item { ToolListRow("🖼️", "PDF to PPT", "Convert PDF to Slides offline", OmniColors.PdfRed, onNavigateToPdfToPpt) }
        item { ToolListRow("📊", "PDF to Excel", "Convert PDF to Sheets offline", OmniColors.PdfRed, onNavigateToPdfToExcel) }
        item { ToolListRow("✍️", "Fill Form", "Fill PDF interactive form fields", OmniColors.PdfRed, onNavigateToPdfFormFiller) }
        item { ToolListRow("🗜️", "Compress PDF", "Reduce PDF file size offline", OmniColors.PdfRed, onNavigateToPdfCompress) }
        item { ToolListRow("🔒", "Flatten PDF", "Flatten interactive form fields", OmniColors.PdfRed, onNavigateToPdfFlatten) }
        item { ToolListRow("📊", "Excel to PDF", "Transcode Excel sheets to PDF", OmniColors.PdfRed, onNavigateToXlsToPdf) }
        item { ToolListRow("🌐", "Web to PDF", "Render URL layouts to PDF offline", OmniColors.PdfRed, onNavigateToWebToPdf) }
        item { ToolListRow("<html>", "HTML to PDF", "Compile custom HTML text to PDF", OmniColors.PdfRed, onNavigateToHtmlToPdf) }
    }
}

@Composable
fun WordToolsList(
    onSelectFileForType: (String) -> Unit,
    onNavigateToDocxToTxt: () -> Unit,
    onNavigateToMarkdownToPdf: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { ToolListRow("📝", "Word Viewer", "Open and read DOCX files", OmniColors.DocBlue, { onSelectFileForType("word") }) }
        item { ToolListRow("📄", "Text Editor", "Read and edit local TXT files", OmniColors.TextMuted, { onSelectFileForType("text") }) }
        item { ToolListRow("🧮", "Word Count", "Analyze document metrics", OmniColors.DocBlue, { onSelectFileForType("word") }) }
        item { ToolListRow("📄", "DOCX to TXT", "Extract text blocks to TXT file", OmniColors.DocBlue, onNavigateToDocxToTxt) }
        item { ToolListRow("✍️", "Markdown to PDF", "Format markdown text to PDF", OmniColors.DocBlue, onNavigateToMarkdownToPdf) }
    }
}

@Composable
fun ExcelToolsList(
    onSelectFileForType: (String) -> Unit,
    onNavigateToCsvToXlsx: () -> Unit,
    onNavigateToXlsxToCsv: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { ToolListRow("📊", "Excel Viewer", "View spreadsheet XLSX cells", OmniColors.XlsGreen, { onSelectFileForType("excel") }) }
        item { ToolListRow("📅", "CSV Editor", "Edit and parse CSV grids", OmniColors.XlsGreen, { onSelectFileForType("csv") }) }
        item { ToolListRow("📤", "CSV to Excel", "Import CSV records to Excel workbook", OmniColors.XlsGreen, onNavigateToCsvToXlsx) }
        item { ToolListRow("📥", "Excel to CSV", "Export workbook sheet cells to CSV", OmniColors.XlsGreen, onNavigateToXlsxToCsv) }
    }
}

@Composable
fun SlidesToolsList(
    onSelectFileForType: (String) -> Unit,
    onNavigateToPptxToTxt: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { ToolListRow("🖼️", "Slides Viewer", "Launch PPTX presentation", Color(0xFFF59E0B), { onSelectFileForType("slides") }) }
        item { ToolListRow("📄", "PPTX to TXT", "Extract presentation slides text to TXT", Color(0xFFF59E0B), onNavigateToPptxToTxt) }
    }
}

@Composable
fun ImageToolsList(
    onNavigateToImageTools: () -> Unit,
    onNavigateToOcr: () -> Unit,
    onNavigateToBarcodeScanner: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { ToolListRow("🖼️", "Image Lab", "Compress & format convert", OmniColors.ImgPurple, onNavigateToImageTools) }
        item { ToolListRow("🔬", "Text OCR", "Extract text offline with ML Kit", OmniColors.ImgPurple, onNavigateToOcr) }
        item { ToolListRow("📷", "Smart Scan", "Auto edge-detect page camera", OmniColors.ImgPurple, onNavigateToBarcodeScanner) }
        item { ToolListRow("✂️", "Crop Image", "Adjust custom proportions", OmniColors.ImgPurple, onNavigateToImageTools) }
        item { ToolListRow("🔄", "Format Transcoder", "PNG, JPEG, WEBP conversions", OmniColors.ImgPurple, onNavigateToImageTools) }
        item { ToolListRow("📐", "Lossless Resize", "Fine-grain dimension control", OmniColors.ImgPurple, onNavigateToImageTools) }
    }
}

@Composable
fun ArchiveQrToolsList(
    onNavigateToQrGenerator: () -> Unit,
    onNavigateToBarcodeScanner: () -> Unit,
    onNavigateToBatchTools: () -> Unit,
    onNavigateToZipMaker: () -> Unit,
    onSelectFileForType: (String) -> Unit,
    onNavigateToTarTools: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { ToolListRow("🗜️", "ZIP Maker", "Compress multiple files to ZIP", OmniColors.ArcCyan, onNavigateToZipMaker) }
        item { ToolListRow("🔓", "ZIP Extractor", "Extract local ZIP archives", OmniColors.ArcCyan, { onSelectFileForType("zip") }) }
        item { ToolListRow("📦", "TAR Archiver", "Create or unpack offline TAR archives", OmniColors.ArcCyan, onNavigateToTarTools) }
        item { ToolListRow("🧬", "QR Generator", "Compile WiFi/vCard QR codes", OmniColors.ArcCyan, onNavigateToQrGenerator) }
        item { ToolListRow("📷", "QR Scanner", "Live viewfinder decoding", OmniColors.ArcCyan, onNavigateToBarcodeScanner) }
        item { ToolListRow("📊", "Barcode Builder", "Generate EAN/UPC barcodes", OmniColors.ArcCyan, onNavigateToQrGenerator) }
        item { ToolListRow("⚡", "Batch Toolkit", "Optimize multiple actions", OmniColors.ArcCyan, onNavigateToBatchTools) }
    }
}
