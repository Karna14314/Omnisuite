# OmniSuite — Core Engine API Specifications

This document outlines the programming interfaces, signatures, and execution characteristics of OmniSuite's core backend processing engines. All engines operate fully offline on background threads.

**Last Updated:** 2026-09-01
**Total Engines:** 10 files across 4 sub-packages

---

## 1. Office to PDF Transcoder (`OfficeConverter.kt`)

- **Coordinates**: `com.karnadigital.omnisuite.core.engine.document.OfficeConverter`
- **Purpose**: Converts Microsoft Office documents (`.docx`, `.xlsx`, `.pptx`) into paginated PDF documents offline.
- **Threading Model**: Strictly runs on `Dispatchers.IO`.

### Key APIs & Signatures
- **`suspend fun convertDocxToPdf(context: Context, docxFile: File, pdfFile: File)`**:
  - Traverses `XWPFParagraph` and `XWPFRun` blocks inside Word document structures.
  - Applies a custom text wrapping engine to compute page breaks.
  - **Repairs Font Metrics Spill**: Integrates a `0.92f` character width scaling multiplier when measuring glyph boundaries against the page margins to prevent overflows.
- **`suspend fun convertXlsxToPdf(context: Context, xlsxFile: File, pdfFile: File)`**:
  - Traverses `XSSFWorkbook` sheets.
  - Generates landscape A4 pages and draws tabular structures with cell outlines.
- **`suspend fun convertPptxToPdf(context: Context, pptxFile: File, pdfFile: File)`**:
  - Iterates slides using `XMLSlideShow`.
  - Renders each slide layout into a drawing Canvas bitmap, then compiles pages.

---

## 2. PDF to Office Extractor (`ReverseOfficeConverter.kt`)

- **Coordinates**: `com.karnadigital.omnisuite.core.engine.document.ReverseOfficeConverter`
- **Purpose**: Handles reverse conversions and interactive form filling using Room logging hooks.
- **Threading Model**: Injected via Hilt (`@Inject constructor`), runs on `Dispatchers.IO`.

### Key APIs & Signatures
- **`suspend fun convertPdfToDocx(uri: Uri): Uri?`**: Extracts text strings using PDFBox `PDFTextStripper` and writes them into a new `XWPFDocument` output wrapper.
- **`suspend fun convertPdfToXlsx(uri: Uri): Uri?`**: Streams tabular PDF contents into sheet cells.
- **`suspend fun fillInteractiveForm(uri: Uri, formData: Map<String, String>): Uri?`**:
  - Captures `PDAcroForm` interactive fields from the PDF catalog.
  - Loops through form mappings and stamps the requested strings into fields.

---

## 3. Real-Time Document Search Engine (`DocumentSearchEngine.kt`)

- **Coordinates**: `com.karnadigital.omnisuite.core.engine.DocumentSearchEngine`
- **Purpose**: Scans files for keyword matches case-insensitively and returns structured snippets.

### Data Models
```kotlin
data class SearchResult(
    val pageIndex: Int,           // 0-indexed page location
    val textSnippet: String,      // Context match snippet
    val extraData: String? = null // metadata like "SheetName,row,col"
)
```

### Key APIs & Signatures
- **`fun searchPdf(filePath: String, query: String): List<SearchResult>`**: Strips text from PDFBox pages page-by-page.
- **`fun searchDocx(filePath: String, query: String): List<SearchResult>`**: Scans paragraphs and tables sequentially.
- **`fun searchXlsx(filePath: String, query: String): List<SearchResult>`**: Traverses sheets and cells. Appends coordinate pointers like `"Sheet1,Row 5,Col B"` to `extraData`.

---

## 4. Barcode & QR Code Compiler (`QrCodeGenerator.kt`)

- **Coordinates**: `com.karnadigital.omnisuite.core.engine.utility.QrCodeGenerator`
- **Purpose**: Generates high-fidelity barcodes and QR codes as standard Bitmaps.
- **Underlying Engine**: ZXing (Zebra Crossing).

### Key APIs & Signatures
- **`fun generateQrCode(content, width, height, qrColor, backgroundColor, errorCorrection, margin): Bitmap?`**
- **`fun generate1DBarcode(content, format, width, height, barcodeColor, backgroundColor): Bitmap?`**
- **`fun getSupportedBarcodeFormats(): List<Pair<String, BarcodeFormat>>`**: Returns CODE_128, CODE_39, EAN_13, EAN_8, UPC_A, UPC_E, ITF, CODABAR.

---

## 5. Advanced Image Lab (`ImageLabExtensions.kt`)

- **Coordinates**: `com.karnadigital.omnisuite.core.engine.image.ImageLabExtensions`
- **Purpose**: Renders overlays, stitches canvases, and applies ID templates.
- **Threading Model**: Injected via Hilt, runs on `Dispatchers.IO`.

### Key APIs & Signatures
- **`suspend fun stitchImagesVertically(uris: List<Uri>): Uri?`**: Measures and stacks multiple bitmap canvases vertically.
- **`suspend fun applyIDCardTemplate(uri: Uri): Uri?`**: Stamps an input photo onto a template canvas.
- **`suspend fun addCustomWatermark(uri: Uri, text: String, size: Float, alpha: Int, rotation: Float): Uri?`**: Renders rotation-aware watermark text overlay.

---

## 6. Image Utilities (`ImageUtils.kt`)

- **Coordinates**: `com.karnadigital.omnisuite.core.engine.image.ImageUtils`
- **Purpose**: Quick, low-overhead image scaling, rotation, format conversion, and quality compression.

---

## 7. Syntax Highlighter (`SyntaxHighlighter.kt`)

- **Coordinates**: `com.karnadigital.omnisuite.core.engine.SyntaxHighlighter`
- **Purpose**: Code syntax highlighting for 30+ programming languages.
- **Languages**: Kotlin, Java, Python, JS/TS, C/C++, C#, PHP, SQL, HTML/CSS, XML, JSON, YAML, Markdown, Gradle, Shell, Ruby, Go, Rust, Swift, Dart, Scala, R, Lua, Perl, Log files.

### Key APIs
- **`fun highlight(text: String, extension: String): AnnotatedString`**: Returns annotated string with syntax colors.
- **`fun getRulesForExtension(extension: String): List<SyntaxRule>`**: Returns regex-based rules for a language.

---

## 8. Encoding Detector (`EncodingDetector.kt`)

- **Coordinates**: `com.karnadigital.omnisuite.core.engine.EncodingDetector`
- **Purpose**: Character encoding detection with BOM detection and heuristics.

### Key APIs
- **`fun detectEncoding(file: File): EncodingResult`**: Detects encoding via BOM, UTF-8/16/32 validation, and single-byte heuristics.
- **`fun readTextWithEncoding(file: File, charset: Charset): String`**: Reads file with specified encoding.
- **`SUPPORTED_ENCODINGS`**: List of 16 supported encodings.

---

## 9. PDF Layout Parser (`PdfLayoutParser.kt`)

- **Coordinates**: `com.karnadigital.omnisuite.core.engine.PdfLayoutParser`
- **Purpose**: PDF layout analysis for block-by-block editing (LightPDF-style).

### Data Models
```kotlin
data class PdfTextBlock(
    val id: Int,
    val text: String,
    val x: Float, val y: Float,
    val width: Float, val height: Float,
    val fontSize: Float,
    val fontFamily: String,
    val fontWeight: String,
    val textColor: Int,
    val pageIndex: Int,
    val blockType: BlockType
)

enum class BlockType { TITLE, HEADING, PARAGRAPH, LINE, TABLE_CELL, CAPTION, HEADER, FOOTER }
```

### Key APIs
- **`fun parseDocument(file: File): List<PdfPageLayout>`**: Parses entire PDF document.
- **`fun parsePage(page: PDPage, pageIndex: Int): PdfPageLayout`**: Parses single page.
- **`fun mergeNearbyBlocks(blocks, threshold): List<PdfTextBlock>`**: Merges nearby blocks.
- **`fun detectTables(blocks): List<PdfTextBlock>`**: Detects table structures.

---

## 10. Utility Tools Repository (`UtilityToolsRepository.kt`)

- **Coordinates**: `com.karnadigital.omnisuite.core.engine.utility.UtilityToolsRepository`
- **Purpose**: Shared utility operations for unit conversion, image enhancement, and more.

### Key APIs
- **`fun convertUnit(value, fromUnit, toUnit, category): Double`**: Converts between 8 unit categories.
- **`fun autoEnhance(bitmap): Bitmap`**: Auto-levels contrast.
- **`fun removeBackground(bitmap, threshold): Bitmap`**: Removes solid-color backgrounds.
- **`fun applyBlur(bitmap, radius): Bitmap`**: Box blur with adjustable radius.
- **`fun applySharpen(bitmap): Bitmap`**: Unsharp mask.
- **`fun removeRedEye(bitmap): Bitmap`**: Detects and fixes red-eye.
- **`fun createCollage(bitmaps, layout): Bitmap`**: Photo collage with grid/2x2/3x3 layouts.
- **`fun createMeme(bitmap, topText, bottomText): Bitmap`**: Meme text overlay.
- **`fun extractSticker(bitmap, left, top, right, bottom, removeBg): Bitmap`**: Extracts objects as stickers.
- **`fun resizeExact(bitmap, width, height, keepAspectRatio): Bitmap`**: Exact dimension resize.
- **`fun placeSticker(background, sticker, x, y, width, height): Bitmap`**: Places sticker on image.
- **`fun pickColorFromImage(bitmap, x, y): Int`**: Picks color from image pixel.
- **`fun saveSticker(context, bitmap, name): File`**: Saves sticker to local storage.
- **`fun loadStickers(context): List<File>`**: Loads saved stickers.
