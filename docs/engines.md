# OmniSuite — Core Engine API Specifications

This document outlines the programming interfaces, signatures, and execution characteristics of OmniSuite's core backend processing engines. All engines operate fully offline on background threads.

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
  - Loops through form mappings and stamps the requested strings into fields:
    ```kotlin
    val acroForm = doc.documentCatalog.acroForm
    val field = acroForm?.getField(key)
    field?.setValue(value)
    ```

---

## 3. Real-Time Document Search Engine (`DocumentSearchEngine.kt`)

- **Coordinates**: `com.karnadigital.omnisuite.core.engine.DocumentSearchEngine`
- **Purpose**: Scans files for keyword matches case-insensitively and returns structured snippets.

### Data Models
```kotlin
data class SearchResult(
    val pageIndex: Int,           // 0-indexed page location
    val textSnippet: String,      // Context match snippet (e.g., "...found matching query here...")
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
- **`fun generateQrCode(...)`**:
  ```kotlin
  fun generateQrCode(
      content: String,
      width: Int = 512,
      height: Int = 512,
      qrColor: Int = Color.BLACK,
      backgroundColor: Int = Color.WHITE,
      errorCorrection: ErrorCorrectionLevel = ErrorCorrectionLevel.M,
      margin: Int = 1
  ): Bitmap?
  ```
- **`fun generate1DBarcode(...)`**:
  ```kotlin
  fun generate1DBarcode(
      content: String,
      format: BarcodeFormat = BarcodeFormat.CODE_128,
      width: Int = 600,
      height: Int = 200,
      barcodeColor: Int = Color.BLACK,
      backgroundColor: Int = Color.WHITE
  ): Bitmap?
  ```

---

## 5. Advanced Image Lab (`ImageLabExtensions.kt`)

- **Coordinates**: `com.karnadigital.omnisuite.core.engine.image.ImageLabExtensions`
- **Purpose**: Renders overlays, stitches canvases, and applies ID templates.
- **Threading Model**: Injected via Hilt, runs on `Dispatchers.IO`.

### Key APIs & Signatures
- **`suspend fun stitchImagesVertically(uris: List<Uri>): Uri?`**: Measures and stacks multiple bitmap canvases vertically, returning a stitched file.
- **`suspend fun applyIDCardTemplate(uri: Uri): Uri?`**: Stamps an input photo onto a template canvas.
- **`suspend fun addCustomWatermark(uri: Uri, text: String, size: Float, alpha: Int, rotation: Float): Uri?`**:
  - Renders a rotation-aware watermark text overlay using the Android Canvas API.
  - Uses `Paint` alpha configurations to adjust transparency.
