package com.karnadigital.omnisuite.feature.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
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

data class ToolItem(
    val icon: String,
    val name: String,
    val description: String,
    val color: Color,
    val onClick: () -> Unit,
    val category: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllToolsScreen(
    onBack: () -> Unit = {},
    isInline: Boolean = false,
    onEvent: (NavigationEvent) -> Unit,
    onSelectFileForType: (String) -> Unit
) {
    var selectedTabState by rememberSaveable { mutableStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }

    val tabs = listOf("📋 PDF", "📝 Word", "📊 Excel", "🖼️ Slides", "📸 Image", "📦 Archive", "🧬 QR & Scan", "⚡ Utilities")

    val activeIndicatorColor = when (selectedTabState) {
        0 -> OmniColors.PdfRed
        1 -> OmniColors.DocBlue
        2 -> OmniColors.XlsGreen
        3 -> Color(0xFFF59E0B)
        4 -> OmniColors.ImgPurple
        5 -> OmniColors.ArcCyan
        6 -> Color(0xFF8B5CF6)
        7 -> Color(0xFF10B981)
        else -> OmniColors.Accent
    }

    // All tools flattened for search
    val allTools = remember { getAllTools(onEvent, onSelectFileForType) }

    val filteredTools = remember(searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else allTools.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.description.contains(searchQuery, ignoreCase = true) ||
            it.category.contains(searchQuery, ignoreCase = true)
        }
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
                    actions = {
                        if (isSearchActive) {
                            IconButton(onClick = {
                                isSearchActive = false
                                searchQuery = ""
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Close search")
                            }
                        } else {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search tools")
                            }
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
            // Search bar
            if (isSearchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search tools...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true
                )
            }

            if (filteredTools.isNotEmpty()) {
                // Search results
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            "Search Results (${filteredTools.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    items(filteredTools) { tool ->
                        ToolListRow(tool.icon, tool.name, tool.description, tool.color, tool.onClick)
                    }
                }
            } else {
                // Normal tabbed view
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
                        5 -> ArchiveToolsList(onEvent, onSelectFileForType)
                        6 -> QrScanToolsList(onEvent)
                        7 -> UtilityToolsList(onEvent)
                    }
                }
            }
        }
    }
}

/**
 * Returns a flat list of all tools for search functionality.
 */
private fun getAllTools(
    onEvent: (NavigationEvent) -> Unit,
    onSelectFileForType: (String) -> Unit
): List<ToolItem> {
    return listOf(
        // PDF Tools
        ToolItem("🥞", "Merge PDFs", "Combine multiple files", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfMerge) }, "PDF"),
        ToolItem("✂️", "Split PDF", "Extract page ranges", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfSplit) }, "PDF"),
        ToolItem("🔒", "Encrypt PDF", "Lock with secure password", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfLock) }, "PDF"),
        ToolItem("🔓", "Decrypt PDF", "Remove PDF password lock", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfDecrypt) }, "PDF"),
        ToolItem("🔄", "Rotate PDF Pages", "Rotate visual page layout", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfRotate) }, "PDF"),
        ToolItem("✂️", "Extract PDF Pages", "Select and extract pages", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfExtract) }, "PDF"),
        ToolItem("🗑️", "Delete PDF Pages", "Remove pages from PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfDelete) }, "PDF"),
        ToolItem("✍️", "Digital Sign", "Stamp digital signature", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToSignaturePad) }, "PDF"),
        ToolItem("💧", "Watermark", "Add security stamp overlay", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToWatermark) }, "PDF"),
        ToolItem("📕", "Images to PDF", "Compile multiple photos into PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToImagesToPdf) }, "PDF"),
        ToolItem("📑", "Doc to PDF", "Transcode Word files to PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToDocToPdf) }, "PDF"),
        ToolItem("🖼️", "Slides to PDF", "Transcode PPTX files to PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPptToPdf) }, "PDF"),
        ToolItem("📷", "Scan to PDF", "Compile camera scans to PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToScanToPdf) }, "PDF"),
        ToolItem("🖨️", "PDF to Images", "Extract PDF pages to PNGs", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfToImages) }, "PDF"),
        ToolItem("📝", "PDF to Word", "Convert PDF to Word offline", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfToWord) }, "PDF"),
        ToolItem("🖼️", "PDF to PPT", "Convert PDF to Slides offline", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfToPpt) }, "PDF"),
        ToolItem("📊", "PDF to Excel", "Convert PDF to Sheets offline", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfToExcel) }, "PDF"),
        ToolItem("🖨️", "Print & Imposition Studio", "N-Up, Booklets, Bleeds & Registration", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPrintImpositionStudio) }, "PDF"),
        ToolItem("✍️", "Fill Form", "Fill PDF interactive form fields", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfFormFiller) }, "PDF"),
        ToolItem("🗜️", "Compress PDF", "Reduce PDF file size offline", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfCompress) }, "PDF"),
        ToolItem("📄", "TXT to PDF", "Convert text file to PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToTxtToPdf) }, "PDF"),
        ToolItem("🔒", "Flatten PDF", "Flatten interactive form fields", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfFlatten) }, "PDF"),
        ToolItem("📊", "Excel to PDF", "Transcode Excel sheets to PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToXlsToPdf) }, "PDF"),
        ToolItem("🌐", "Web to PDF", "Render URL layouts to PDF offline", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToWebToPdf) }, "PDF"),
        ToolItem("<html>", "HTML to PDF", "Compile custom HTML text to PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToHtmlToPdf) }, "PDF"),
        ToolItem("🔢", "Page Numbers", "Add page numbers to PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfPageNumber) }, "PDF"),
        ToolItem("🔀", "Reorder Pages", "Drag and drop to reorder PDF pages", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfReorder) }, "PDF"),
        ToolItem("🖼️", "Extract Images", "Extract embedded images from PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfExtractImages) }, "PDF"),
        ToolItem("📊", "CSV to PDF", "Convert CSV data to PDF table", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToCsvToPdf) }, "PDF"),
        ToolItem("📝", "PDF to TXT", "Extract text from PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfToTxt) }, "PDF"),
        ToolItem("📝", "Header & Footer", "Add header and footer to PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfHeaderFooter) }, "PDF"),
        ToolItem("📐", "Resize Pages", "Change PDF page size", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfResize) }, "PDF"),
        ToolItem("✏️", "Edit Metadata", "Edit title, author, subject, keywords", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfMetadata) }, "PDF"),
        ToolItem("✂️", "Crop Margins", "Adjust PDF page margins", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfCropMargins) }, "PDF"),
        ToolItem("⬛", "Redact PDF", "Permanently blackout sensitive areas", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfRedact) }, "PDF"),
        ToolItem("⚖️", "Compare PDF", "Compare text of two PDFs", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfCompare) }, "PDF"),
        ToolItem("📎", "Insert Pages", "Insert pages from another PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfInsertPages) }, "PDF"),
        ToolItem("🔄", "Replace Pages", "Replace pages with another PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfReplacePages) }, "PDF"),
        ToolItem("🔖", "Bookmarks", "Manage PDF bookmarks", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfBookmarks) }, "PDF"),

        // Word Tools
        ToolItem("📝", "Word Viewer", "Open and read DOCX files", OmniColors.DocBlue, { onSelectFileForType("word") }, "Word"),
        ToolItem("📄", "Text Editor", "Read and edit local TXT files", Color(0xFF6B7280), { onSelectFileForType("text") }, "Word"),
        ToolItem("🧮", "Word Count", "Analyze document metrics", OmniColors.DocBlue, { onSelectFileForType("word") }, "Word"),
        ToolItem("📄", "DOCX to TXT", "Extract text blocks to TXT file", OmniColors.DocBlue, { onEvent(NavigationEvent.NavigateToDocxToTxt) }, "Word"),
        ToolItem("✍️", "Markdown to PDF", "Format markdown text to PDF", OmniColors.DocBlue, { onEvent(NavigationEvent.NavigateToMarkdownToPdf) }, "Word"),

        // Excel Tools
        ToolItem("📊", "Excel Viewer", "View spreadsheet XLSX cells", OmniColors.XlsGreen, { onSelectFileForType("excel") }, "Excel"),
        ToolItem("📅", "CSV Editor", "Edit and parse CSV grids", OmniColors.XlsGreen, { onSelectFileForType("csv") }, "Excel"),
        ToolItem("📤", "CSV to Excel", "Import CSV records to Excel workbook", OmniColors.XlsGreen, { onEvent(NavigationEvent.NavigateToCsvToXlsx) }, "Excel"),
        ToolItem("📥", "Excel to CSV", "Export workbook sheet cells to CSV", OmniColors.XlsGreen, { onEvent(NavigationEvent.NavigateToXlsxToCsv) }, "Excel"),

        // Slides Tools
        ToolItem("🖼️", "Slides Viewer", "Launch PPTX presentation", Color(0xFFF59E0B), { onSelectFileForType("slides") }, "Slides"),
        ToolItem("📄", "PPTX to TXT", "Extract presentation slides text to TXT", Color(0xFFF59E0B), { onEvent(NavigationEvent.NavigateToPptxToTxt) }, "Slides"),

        // Image Tools
        ToolItem("📐", "Resize Dimensions", "Exact WxH in px, aspect lock, presets", OmniColors.ImgPurple, { onEvent(NavigationEvent.NavigateToImageToolsWithTab(0)) }, "Image"),
        ToolItem("🗜️", "Compress Image", "Target KB for jobs & govt forms", OmniColors.ImgPurple, { onEvent(NavigationEvent.NavigateToImageToolsWithTab(1)) }, "Image"),
        ToolItem("✂️", "Passport Photo Maker", "Standard 2x2, 3.5x4.5cm ID crop", OmniColors.ImgPurple, { onEvent(NavigationEvent.NavigateToImageToolsWithTab(2)) }, "Image"),
        ToolItem("🔄", "Format Converter", "Convert JPG, PNG, WEBP, PDF", OmniColors.ImgPurple, { onEvent(NavigationEvent.NavigateToImageToolsWithTab(1)) }, "Image"),
        ToolItem("🎨", "Photo Adjust & Filters", "Brightness, contrast, saturation", OmniColors.ImgPurple, { onEvent(NavigationEvent.NavigateToImageToolsWithTab(3)) }, "Image"),
        ToolItem("🔬", "Text OCR", "Extract text offline with ML Kit", OmniColors.ImgPurple, { onEvent(NavigationEvent.NavigateToOcr) }, "Image"),
        ToolItem("📷", "Smart Scan", "Auto edge-detect page camera", OmniColors.ImgPurple, { onEvent(NavigationEvent.NavigateToBarcodeScanner) }, "Image"),

        // Archive & Security Tools
        ToolItem("🗜️", "ZIP Maker", "Compress multiple files to ZIP", OmniColors.ArcCyan, { onEvent(NavigationEvent.NavigateToZipMaker) }, "Archive"),
        ToolItem("🔓", "ZIP Extractor", "Extract local ZIP archives", OmniColors.ArcCyan, { onSelectFileForType("zip") }, "Archive"),
        ToolItem("📦", "TAR Archiver", "Create or unpack offline TAR archives", OmniColors.ArcCyan, { onEvent(NavigationEvent.NavigateToTarTools) }, "Archive"),
        ToolItem("🔐", "Password ZIP", "Create password-protected ZIP", OmniColors.ArcCyan, { onEvent(NavigationEvent.NavigateToPasswordZip) }, "Archive"),
        ToolItem("🔓", "Extract Password ZIP", "Extract password-protected ZIP", OmniColors.ArcCyan, { onEvent(NavigationEvent.NavigateToPasswordZipExtract) }, "Archive"),
        ToolItem("🔒", "Encrypt File", "AES-256 file encryption", OmniColors.ArcCyan, { onEvent(NavigationEvent.NavigateToFileEncrypt) }, "Archive"),
        ToolItem("🔓", "Decrypt File", "Decrypt AES-256 files", OmniColors.ArcCyan, { onEvent(NavigationEvent.NavigateToFileDecrypt) }, "Archive"),

        // QR & Barcode Tools
        ToolItem("📷", "QR & Barcode Scanner", "Live camera viewfinder scanner", Color(0xFF8B5CF6), { onEvent(NavigationEvent.NavigateToBarcodeScanner) }, "QR & Scan"),
        ToolItem("🧬", "QR Generator", "Compile WiFi/vCard QR codes", Color(0xFF8B5CF6), { onEvent(NavigationEvent.NavigateToQrGenerator) }, "QR & Scan"),
        ToolItem("📊", "Barcode Builder", "Generate EAN/UPC barcodes", Color(0xFF8B5CF6), { onEvent(NavigationEvent.NavigateToQrGenerator) }, "QR & Scan"),

        // Document Utilities & Tools (in PDF & Docs)
        ToolItem("⚡", "Batch Toolkit", "Optimize multiple document actions", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToBatchTools) }, "PDF"),
        ToolItem("🔊", "Read Aloud", "Text-to-speech for documents (offline)", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToReadAloud) }, "PDF"),

        // Utilities
        ToolItem("📏", "Unit Converter", "Length, weight, temperature", Color(0xFF10B981), { onEvent(NavigationEvent.NavigateToUnitConverter) }, "Utilities")
    )
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
        item { ToolListRow("🖨️", "Print & Imposition Studio", "N-Up, Booklets, Bleeds & Registration", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPrintImpositionStudio) }) }
        item { ToolListRow("📊", "PDF to Excel", "Convert PDF to Sheets offline", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfToExcel) }) }
        item { ToolListRow("✍️", "Fill Form", "Fill PDF interactive form fields", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfFormFiller) }) }
        item { ToolListRow("🗜️", "Compress PDF", "Reduce PDF file size offline", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfCompress) }) }
        item { ToolListRow("🔒", "Flatten PDF", "Flatten interactive form fields", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfFlatten) }) }
        item { ToolListRow("📊", "Excel to PDF", "Transcode Excel sheets to PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToXlsToPdf) }) }
        item { ToolListRow("🌐", "Web to PDF", "Render URL layouts to PDF offline", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToWebToPdf) }) }
        item { ToolListRow("<html>", "HTML to PDF", "Compile custom HTML text to PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToHtmlToPdf) }) }
        item { ToolListRow("🔢", "Page Numbers", "Add page numbers to PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfPageNumber) }) }
        item { ToolListRow("🔀", "Reorder Pages", "Drag and drop to reorder PDF pages", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfReorder) }) }
        item { ToolListRow("🖼️", "Extract Images", "Extract embedded images from PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfExtractImages) }) }
        item { ToolListRow("📄", "TXT to PDF", "Convert text file to PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToTxtToPdf) }) }
        item { ToolListRow("📊", "CSV to PDF", "Convert CSV data to PDF table", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToCsvToPdf) }) }
        item { ToolListRow("📝", "PDF to TXT", "Extract text from PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfToTxt) }) }
        item { ToolListRow("📝", "Header & Footer", "Add header and footer to PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfHeaderFooter) }) }
        item { ToolListRow("📐", "Resize Pages", "Change PDF page size (A3/A4/A5/Letter)", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfResize) }) }
        item { ToolListRow("✏️", "Edit Metadata", "Edit title, author, subject, keywords", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfMetadata) }) }
        item { ToolListRow("✂️", "Crop Margins", "Adjust PDF page margins", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfCropMargins) }) }
        item { ToolListRow("⬛", "Redact PDF", "Permanently blackout sensitive areas", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfRedact) }) }
        item { ToolListRow("⚖️", "Compare PDF", "Compare text of two PDFs", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfCompare) }) }
        item { ToolListRow("📎", "Insert Pages", "Insert pages from another PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfInsertPages) }) }
        item { ToolListRow("🔄", "Replace Pages", "Replace pages with another PDF", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfReplacePages) }) }
        item { ToolListRow("🔖", "Bookmarks", "Add and view PDF bookmarks", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToPdfBookmarks) }) }
        item { ToolListRow("⚡", "Batch Toolkit", "Optimize & process multiple files in batch", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToBatchTools) }) }
        item { ToolListRow("🔊", "Read Aloud", "Text-to-speech for documents (offline)", OmniColors.PdfRed, { onEvent(NavigationEvent.NavigateToReadAloud) }) }
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
        item { ToolListRow("📐", "Resize Dimensions", "Exact WxH in px, aspect lock, presets", OmniColors.ImgPurple, { onEvent(NavigationEvent.NavigateToImageToolsWithTab(0)) }) }
        item { ToolListRow("🗜️", "Compress Image", "Target KB for job & govt applications", OmniColors.ImgPurple, { onEvent(NavigationEvent.NavigateToImageToolsWithTab(1)) }) }
        item { ToolListRow("✂️", "Passport Photo Maker", "Standard 2x2, 3.5x4.5cm ID crop", OmniColors.ImgPurple, { onEvent(NavigationEvent.NavigateToImageToolsWithTab(2)) }) }
        item { ToolListRow("🔄", "Format Converter", "Convert JPG, PNG, WEBP, PDF", OmniColors.ImgPurple, { onEvent(NavigationEvent.NavigateToImageToolsWithTab(1)) }) }
        item { ToolListRow("🎨", "Photo Adjust & Filters", "Brightness, contrast, saturation, tones", OmniColors.ImgPurple, { onEvent(NavigationEvent.NavigateToImageToolsWithTab(3)) }) }
        item { ToolListRow("🔬", "Text OCR", "Extract text offline with ML Kit", OmniColors.ImgPurple, { onEvent(NavigationEvent.NavigateToOcr) }) }
        item { ToolListRow("📷", "Smart Scan", "Auto edge-detect page camera", OmniColors.ImgPurple, { onEvent(NavigationEvent.NavigateToBarcodeScanner) }) }
    }
}

@Composable
fun ArchiveToolsList(
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
        item { ToolListRow("🔐", "Password ZIP", "Create password-protected ZIP", OmniColors.ArcCyan, { onEvent(NavigationEvent.NavigateToPasswordZip) }) }
        item { ToolListRow("🔓", "Extract Password ZIP", "Extract password-protected ZIP", OmniColors.ArcCyan, { onEvent(NavigationEvent.NavigateToPasswordZipExtract) }) }
        item { ToolListRow("🔒", "Encrypt File", "AES-256 file encryption", OmniColors.ArcCyan, { onEvent(NavigationEvent.NavigateToFileEncrypt) }) }
        item { ToolListRow("🔓", "Decrypt File", "Decrypt AES-256 files", OmniColors.ArcCyan, { onEvent(NavigationEvent.NavigateToFileDecrypt) }) }
    }
}

@Composable
fun QrScanToolsList(onEvent: (NavigationEvent) -> Unit) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { ToolListRow("📷", "QR & Barcode Scanner", "Live offline viewfinder decoding", Color(0xFF8B5CF6), { onEvent(NavigationEvent.NavigateToBarcodeScanner) }) }
        item { ToolListRow("🧬", "QR Generator", "Compile WiFi/vCard/URL QR codes", Color(0xFF8B5CF6), { onEvent(NavigationEvent.NavigateToQrGenerator) }) }
        item { ToolListRow("📊", "Barcode Builder", "Generate EAN/UPC barcodes", Color(0xFF8B5CF6), { onEvent(NavigationEvent.NavigateToQrGenerator) }) }
    }
}

@Composable
fun UtilityToolsList(onEvent: (NavigationEvent) -> Unit) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { ToolListRow("⚡", "Batch Toolkit", "Optimize multiple actions", Color(0xFF10B981), { onEvent(NavigationEvent.NavigateToBatchTools) }) }
        item { ToolListRow("📏", "Unit Converter", "Length, weight, temperature", Color(0xFF10B981), { onEvent(NavigationEvent.NavigateToUnitConverter) }) }
        item { ToolListRow("🔊", "Read Aloud", "Text-to-speech (offline)", Color(0xFF10B981), { onEvent(NavigationEvent.NavigateToReadAloud) }) }
    }
}
