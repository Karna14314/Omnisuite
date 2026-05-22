# OmniSuite — Full Design & Implementation Document
**Version:** 1.1 (Scaffolding Ready)
**Author:** Karna Digital
**Status:** Approved Production Reference
**Monetization:** 100% Free / Fully Offline (No AdMob, No Billing Client)

---

## 0. Project Overview

A lightweight, fully offline-capable Android document suite targeting users who want comprehensive office utility functionality without subscription models, trackers, corporate bloat, or mandatory cloud dependencies. Runs as a sister utility to PDF Toolkit—where PDF Toolkit goes deep on PDF manipulation, OmniSuite goes wide across document processing, spreadsheet parsing, slides rendering, image toolsets, and fast local utility operations (QR/Barcode systems).

- **Package name:** `com.karnadigital.omnisuite`
- **Min SDK:** 30 (Android 11.0)
- **Target SDK:** 36 (Android 16.0)
- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Architecture:** MVVM + Clean Architecture (UseCase layer)

---

### 📊 The Android Market Reality in India (2026)

According to regional distribution numbers for India, dropping support for old devices and drawing the line at **Android 11** results in losing almost nothing of significance but gaining immense development speed:

| Android Version | API Level | Market Share in India (April 2026) |
| --- | --- | --- |
| **Android 16.0** | API 36 | 15.70% |
| **Android 15.0** | API 35 | 26.85% |
| **Android 14.0** | API 34 | 13.21% |
| **Android 13.0** | API 33 | 14.16% |
| **Android 12.0** | API 32/31 | 10.28% |
| **Android 11.0** | API 30 | 9.89% |
| **Total Reach (API 30+)** |  | **~90.1% of the entire country** |

---

### 🛠️ The Optimal Strategy for OmniSuite

#### 1. Set `minSdk = 30` (Android 11)

Setting the minimum threshold to API 30 is the single best decision for an offline document processing application:

* **Clean Scoped Storage:** Prior to Android 11, storage access was a chaotic mix of legacy permissions and custom flags (`requestLegacyExternalStorage`). From API 30 onward, **Scoped Storage is strictly enforced**. By setting this as the minimum, the file picker architecture (`UriCacheUtils`) becomes perfectly uniform without needing code forks for older devices.
* **Modern Immersive Views:** For full-screen presentation viewing (PPTX) or distraction-free document editing, Android 11 introduced `WindowInsetsController` to replace a messy web of deprecated UI visibility flags.
* **Compose Performance:** Modern Jetpack Compose rendering runtime routines are significantly more stable and optimized on the Android 11+ ART compiler pipeline.

#### 2. Set `targetSdk = 36` (Android 16)

Targeting the current modern standard ensures alignment with Play Store requirements and system integrity:

* **Google Play Compliance:** Google Play mandates that all new applications and updates target API 36 starting **August 2026**. Starting here ensures OmniSuite doesn't face sudden visibility drops or immediate technical refactoring down the line.
* **System Integrity:** It ensures the app runs perfectly with Android 16's enhanced multi-window adjustments (vital for cross-referencing sheets/documents) and respects the platform's strict edge-to-edge UI requirements out of the box.

---

## 1. Core Philosophy

- **Offline-first, always:** No runtime feature requires network permissions. All compute, rendering, processing, and parsing occur strictly on-device.
- **Viewing is the foundation:** Ensure high-stability rendering and text layout extraction across all structural definitions (DOCX, XLSX, PPTX, PDF) before layered editing utilities are expanded.
- **Size discipline:** Core binary target remains under 30MB. Unused references are aggressively pruned.
- **One unified engine per family:** Apache POI serves all core Microsoft Office specifications to limit binary size redundancies.

---

## 2. Core Feature & Engine Registry (Phase 1 Focused)

| Format / Utility Family | Engine | Version | Approx Size Added | Notes |
|---|---|---|---|---|
| **PDF Viewer** | Android Native PdfRenderer | Built-in | 0MB | View only (API 21+), paginated view using lazy-loaded canvas configurations. |
| **PDF Operations** | Apache PDFBox Android Port | 2.0.27.0 | ~8MB | Merge, Split, and Rotate. |
| **DOCX / XLSX / PPTX** | Apache POI Engine | 5.2.5 | ~14MB | Document parsing, sheet text streaming to lazy grids, slide canvas bitmap compilation. |
| **QR & Barcode Utilities** | ZXing Core Engine | 3.5.3 | ~500KB | Crisp offline bitmap generation. |
| **QR/Barcode Scanning** | Google ML Kit Barcode Scanning | 17.2.0 | 0MB | Thin-client rapid CameraX viewfinder loops. |
| **AI Vision Ops** | ML Kit Vision Engine Bundle | 16.0.0 | ~5MB | On-demand offline OCR and Document scanning workflows. |
| **Camera / Viewfinder** | CameraX Stack | 1.3.4 | ~2MB | Viewfinder loops and camera hardware abstraction. |
| **Image operations** | Android Bitmap API + Canvas | Built-in | 0MB | Scaling, format conversions, canvas drawing. |

**Engines explicitly rejected:**
- LibreOffice headless — ~80MB, not viable.
- OpenCV — ~10MB, only needed for Remove Moiré/shadow (not worth it).
- Mammoth.js — JS-only, limited, redundant with POI.
- documents4j — server-side only, not Android-compatible.
- Telerik/Aspose — proprietary, expensive.

---

## 3. Feature Registry

### Status Legend
- ✅ **IN SCOPE** — implement in phased order
- ⏳ **PHASE 2** — after core is stable
- ❌ **SKIPPED** — explicitly excluded with reason

---

### 3.1 PDF Tools

| Feature | Status | Engine | Implementation Notes |
|---|---|---|---|
| PDF Viewer | ✅ Phase 1 | PdfRenderer | Zoomable, paginated, thumbnail sidebar, lazy canvas |
| Merge PDFs | ✅ Phase 1 | PDFBox | Select multiple files, reorder, merge |
| Split PDF | ✅ Phase 1 | PDFBox | By page range or every N pages |
| Rotate Pages | ✅ Phase 1 | PDFBox | Per-page or all pages |
| PDF Password | ✅ Phase 2 | PDFBox | Encrypt with owner/user password, decrypt |
| PDF Watermark | ✅ Phase 2 | PDFBox | Text or image watermark, opacity, position |
| PDF Compress | ✅ Phase 2 | PDFBox | Downsample embedded images, remove metadata |
| Fill Form | ✅ Phase 2 | PDFBox | AcroForm field detection and filling |
| PDF Signature | ✅ Phase 2 | PDFBox + Canvas | Draw signature on canvas, stamp as image onto PDF page |
| Annotate PDF | ✅ Phase 2 | PDFBox | Highlight, underline, freehand draw, sticky note |
| Manage Pages | ✅ Phase 2 | PDFBox | Reorder, delete, duplicate pages with drag UI |
| Edit PDF (text) | ✅ Phase 3 | PDFBox | Limited: add text boxes only, not edit existing text |
| PDF to Word | ❌ SKIPPED | — | Requires layout analysis engine (>20MB). Suggest online tool |
| PDF to PPT | ❌ SKIPPED | — | Same reason |
| PDF to Excel | ❌ SKIPPED | — | Same reason. OCR-based table extraction is Phase 2+ |
| Output Pure Data Sheet | ❌ SKIPPED | — | Unclear feature, skip |

---

### 3.2 Document Tools (DOCX / DOC)

| Feature | Status | Engine | Implementation Notes |
|---|---|---|---|
| DOCX Viewer | ✅ Phase 1 | POI → Bitmap render | Render each page to bitmap via POI layout engine |
| DOC Viewer | ✅ Phase 1 | POI HWPF | Auto-convert DOC→DOCX in memory, then render |
| DOCX Basic Edit | ✅ Phase 2 | POI | Bold, italic, font size, color, paragraph align |
| DOCX→PDF Export | ✅ Phase 2 | POI + PDFBox | POI parses content, PDFBox writes PDF layout |
| Word Count / Stats | ✅ Phase 2 | POI | Word, char, paragraph count from XWPFDocument |
| TXT Viewer + Editor | ✅ Phase 1 | None | Plain EditText with file read/write |
| RTF Viewer | ⏳ Phase 3 | POI RTF | Basic support |
| Track Changes view | ⏳ Phase 3 | POI | Read tracked change metadata and display |
| Comments view | ⏳ Phase 3 | POI | Read comment annotations |
| Templates | ⏳ Phase 3 | POI | Create from pre-bundled .docx templates |

---

### 3.3 Spreadsheet Tools (XLSX / XLS / CSV)

| Feature | Status | Engine | Implementation Notes |
|---|---|---|---|
| XLSX Viewer | ✅ Phase 1 | POI | Render as scrollable table. Sheet tabs at bottom |
| XLS Viewer | ✅ Phase 1 | POI HSSF | Same flow, POI handles both |
| CSV Viewer | ✅ Phase 1 | None | Parse with kotlin stdlib, display as table |
| Basic Cell Edit | ✅ Phase 2 | POI | Edit cell string/number values, save back |
| Formula eval | ✅ Phase 2 | POI | POI has built-in formula evaluator |
| XLSX→PDF Export | ✅ Phase 2 | POI + PDFBox | Paginated table render to PDF |
| CSV→XLSX | ✅ Phase 2 | POI | Read CSV, write XLSX via SXSSFWorkbook |
| Charts view | ⏳ Phase 3 | POI + MPAndroidChart | Read chart data from XLSX, render with MPChart |

---

### 3.4 Presentation Tools (PPTX / PPT)

| Feature | Status | Engine | Implementation Notes |
|---|---|---|---|
| PPTX Viewer | ✅ Phase 1 | POI → Bitmap | Render each slide to Bitmap. Show as swipeable pages |
| PPT Viewer | ✅ Phase 1 | POI HSLF | Auto-convert in memory |
| Slide thumbnail grid | ✅ Phase 1 | POI | Grid of rendered slide bitmaps |
| PPTX→PDF Export | ✅ Phase 2 | POI + PDFBox | Each slide bitmap → PDF page |
| Slideshow mode | ✅ Phase 2 | Compose | Full-screen swipe with slide transitions |
| Basic text edit | ⏳ Phase 3 | POI | Edit text in existing text boxes only |
| Add slide/delete | ⏳ Phase 3 | POI | Clone slide template, or delete slide |
| Animations | ❌ SKIPPED | — | Not implementable offline without massive engine |

---

### 3.5 Image Tools

| Feature | Status | Engine | Notes |
|---|---|---|---|
| Image Viewer | ✅ Phase 1 | Coil | Zoomable, supports JPG/PNG/WEBP/GIF/BMP |
| Compress Image | ✅ Phase 1 | Bitmap API | Quality slider → JPEG/WEBP compress |
| Resize Image | ✅ Phase 1 | Bitmap API | Width/height or percentage input |
| Crop Image | ✅ Phase 1 | UCrop library | Free crop, aspect ratio presets |
| Rotate / Flip | ✅ Phase 1 | Bitmap API | 90°/180°/270° rotate, H/V flip |
| Convert Format | ✅ Phase 1 | Bitmap API | JPG↔PNG↔WEBP↔BMP |
| Add Watermark | ✅ Phase 2 | Bitmap + Canvas | Text or image watermark, opacity, position |
| Image to PDF | ✅ Phase 2 | PDFBox | Single or batch images → PDF |
| Long Image (stitch) | ✅ Phase 2 | Canvas API | Vertical stitch of multiple images |
| Extract Text (OCR) | ✅ Phase 2 | ML Kit Text Recognition | On-demand model, works offline after download |
| Scanner | ✅ Phase 2 | CameraX + ML Kit Document Scanner | Auto edge detect, perspective correct |
| Remove Background | ✅ Phase 2 | ML Kit Selfie Segmentation | Good for portraits; general objects are approximate |
| Enhance Image | ✅ Phase 2 | Bitmap API | Brightness, contrast, saturation, sharpness sliders |
| AI Filters | ⏳ Phase 3 | TFLite | Bundle small style-transfer model (~3MB) |
| Batch Image ops | ⏳ Phase 3 | Bitmap API | Apply compress/resize/convert to folder |
| Lossless Resize | ✅ Phase 2 | Bitmap API | WEBP lossless mode |
| Restore Image | ⏳ Phase 3 | TFLite | Needs denoising model — evaluate size |
| Remove Shadow | ❌ SKIPPED | — | Needs OpenCV. Not worth 10MB for this one feature |
| Remove Moiré | ❌ SKIPPED | — | Needs OpenCV. Skip |
| Remove Handwriting | ❌ SKIPPED | — | No good lightweight solution exists |
| Remove Watermark | ❌ SKIPPED | — | Legally grey, technically hard |
| Magic Eraser | ⏳ Phase 3 | TFLite inpainting model | Evaluate model size; ~5-8MB |
| Change Image Format | ✅ Phase 1 | Bitmap API | Same as Convert Format above |
| Images by Page | ✅ Phase 2 | PDFBox | Extract images embedded in PDF pages |
| Output Card Image | ⏳ Phase 3 | Canvas API | Business card style layout generator |
| AI Cropping | ⏳ Phase 3 | ML Kit subject detection | Smart crop to subject |
| Batch Image Extraction | ⏳ Phase 3 | PDFBox | Extract all images from PDF |

---

### 3.6 QR / Barcode & General Utilities

| Feature | Status | Engine | Notes |
|---|---|---|---|
| QR Code generator | ✅ Phase 1 | ZXing | Crisp offline generation based on parameters |
| QR Code scanner | ✅ Phase 1 | ML Kit Barcode | Fast CameraX local scanning viewfinder loops |
| Barcode generator | ✅ Phase 1 | ZXing | Offline barcode generator (CODE_128, etc.) |
| Barcode scanner | ✅ Phase 1 | ML Kit Barcode | Included in scan viewfinder loop |
| Recent files list | ✅ Phase 1 | Room DB | Room DB storing file URI + metadata |
| File browser | ✅ Phase 1 | SAF | SAF-based picker + optional built-in tree browser |
| Share file | ✅ Phase 1 | Android Native | FileProvider + Android share sheet |
| Print | ✅ Phase 2 | PrintManager | Android PrintManager API |
| Dark mode | ✅ Phase 1 | MaterialTheme | Material3 typography and colors, local dynamic styling |
| Multi-window | ✅ Phase 2 | Android Manifest | System support for split screen and multi-window |
| Search in doc | ✅ Phase 2 | POI / PdfRenderer | POI string extraction + PdfRenderer rendering layers |
| Bookmarks | ⏳ Phase 3 | Room DB | Star/favorite documents dynamically |

---

## 4. App Architecture Clean Map

```
com.karnadigital.omnisuite
├── core/
│   ├── engine/
│   │   ├── pdf/          — PdfMerger, PdfSplitter, PdfRenderer wrappers
│   │   ├── document/     — XWPFDocument and XSSFSheet custom parsers
│   │   ├── image/        — Scaling, format conversions, canvas drawing
│   │   └── utility/      — QrGenerator and BarcodeAnalyzer layers
│   ├── model/            — Local Room state models (RecentFile, ScannedData)
│   ├── repository/       — Isolated local storage and IO operations
│   └── util/             — UriCacheUtils and structural helpers
├── feature/
│   ├── home/             — Unified landing view, tools picker grid, recents listing
│   ├── viewer/           — Dynamic polymorphic view hub coordinating file types
│   ├── tools/            — Feature-specific input parameters and preview screens
│   └── utility/          — Dedicated UI routes for real-time QR generation and scanning
├── ui/
│   ├── theme/            — Strict Material3 typography and theme configurations
│   └── component/        — Generic, reusable buttons, progress sheets, cards
└── di/                   — Clean Dependency Injection configuration via Hilt modules
```

---

## 5. Navigation Structure

```
NavGraph
├── HomeScreen (bottom nav: Home / Tools / Recents)
├── FileBrowserScreen
├── ViewerScreen (single destination, dispatches by MIME type)
│   ├── → PdfViewerScreen
│   ├── → DocViewerScreen
│   ├── → XlsViewerScreen
│   ├── → PptViewerScreen
│   ├── → ImageViewerScreen
│   └── → TxtViewerScreen
├── ToolCategoryScreen (PDF / Document / Image / Utility)
├── ToolScreen (individual tool — receives file URI as arg)
└── UtilityScreen (QR/Barcode Generator & Camera Scanner viewfinder UI)
```

**Viewer dispatch logic:** A single `openFile(uri)` function reads MIME type, then navigates to the correct viewer. This is the single entry point from the file browser, recent files list, and share intent filters.

---

## 6. Implementation Phases

### Phase 1 — Viewers + Core + QR Foundations (Current Stage)
**Goal:** App is fully usable as a universal document viewer and local barcode assistant.

Deliverables:
- Home screen layout with recent files and tool category grid.
- Android Storage Access Framework (SAF) system picker.
- PDF Viewer (PdfRenderer using lazy-loaded canvas and zoomable views).
- DOCX Viewer (POI parsing → reflowed text inside Compose UI, basic fidelity).
- XLSX Viewer (POI streaming → scrollable Compose table grid with sheet tabs).
- PPTX Viewer (POI slide shape rendering → slide bitmaps, swipeable grid).
- TXT Viewer + Editor (Direct file reading/writing on EditText with zero layers).
- Image Viewer (Coil-compose with dynamic zoom).
- QR and Barcode Generator (ZXing core MultiFormatWriter engine working 100% offline).
- Barcode & QR Camera Scanner (CameraX lifecycle controller + ML Kit Barcode thin-client).
- Room DB database setup caching RecentFile references.

**POI Rendering Strategy for DOCX/PPTX (critical detail):**
POI does not have a built-in renderer. The approach is:
1. Parse with POI into XWPFDocument / XMLSlideShow.
2. Use `XWPFWordExtractor` or custom traversal to build a Compose-native layout (paragraphs, bold, tables) for DOCX viewing.
3. For PPTX, render each `XSLFSlide` to a `Bitmap` using Android Canvas manually per shape type or via custom graphics bindings.
4. This means PPTX gets slide bitmaps; DOCX gets compose-rendered (reflowed) text — acceptable tradeoff.

---

### Phase 2 — Advanced PDF Operations & Image Ops
**Goal:** High-performance local file manipulations.

Deliverables:
- Merge, Split, Rotate, Compress PDF (via PDFBox Android).
- PDF Password protection configuration (encrypt/decrypt).
- PDF Watermark overlays.
- PDF Signature stamps.
- CameraX scanner document perspective adjustments.
- Format exports: DOCX→PDF, PPTX→PDF, XLSX→PDF.
- Print support via standard Android PrintManager.

---

### Phase 3 — Premium Features (100% Free)
**Goal:** Local smart operations and deep document editing.

Deliverables:
- Local offline OCR extraction using ML Kit.
- XLSX formula execution support using POI's evaluator.
- Interactive slide transitions in full-screen slideshow mode.
- In-document keyword search indexes.
- Starred file configurations.

---

## 7. Dependency List (build.gradle.kts)

```kotlin
// Core Android & Lifecycle
implementation("androidx.core:core-ktx:1.13.1")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
implementation("androidx.activity:activity-compose:1.9.0")

// Jetpack Compose
implementation(platform("androidx.compose:compose-bom:2024.06.00"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.ui:ui-graphics")
implementation("androidx.compose.ui:ui-tooling-preview")
implementation("androidx.compose.material3:material3")
implementation("androidx.navigation:navigation-compose:2.7.7")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")

// Dependency Injection (Hilt)
implementation("com.google.dagger:hilt-android:2.51.1")
kapt("com.google.dagger:hilt-compiler:2.51.1")

// Room Database
val roomVersion = "2.6.1"
implementation("androidx.room:room-runtime:$roomVersion")
implementation("androidx.room:room-ktx:$roomVersion")
kapt("androidx.room:room-compiler:$roomVersion")

// Document engines
implementation("org.apache.poi:poi-ooxml:5.2.5")
implementation("com.tom-roush:pdfbox-android:2.0.27.0")

// QR & Barcode Utilities
implementation("com.google.zxing:core:3.5.3")
implementation("com.google.mlkit:barcode-scanning:17.2.0")

// CameraX Hardware Stack
val cameraXVersion = "1.3.4"
implementation("androidx.camera:camera-core:$cameraXVersion")
implementation("androidx.camera:camera-camera2:$cameraXVersion")
implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
implementation("androidx.camera:camera-view:$cameraXVersion")
```

**ProGuard Rules for POI (keeps binary target under 30MB):**
```proguard
-keep class org.apache.poi.** { *; }
-keep class org.openxmlformats.** { *; }
-dontwarn org.apache.xmlbeans.**
-dontwarn com.microsoft.schemas.**
```

---

## 8. Key Implementation Details

### 8.1 POI on Android — Critical Notes

POI was designed for JVM, not Android. Known issues:
- **javax.xml dependency:** POI needs `javax.xml` which Android has partially. Use `poi-ooxml` (not `poi-ooxml-schemas`) and add:
  ```kotlin
  // In Application.onCreate()
  System.setProperty("org.apache.poi.javax.xml.stream.XMLInputFactory",
      "com.sun.xml.internal.stream.XMLInputFactoryImpl")
  ```
- **Threading:** All POI operations MUST run on a background coroutine (`Dispatchers.IO`). Never on the main thread.
- **Memory:** Large files (>50 page DOCX, >10MB XLSX) can cause OOM. Stream large XLS with `SXSSFWorkbook` for writing; for reading, consider page-by-page rendering with page cache of max 5 bitmaps.

### 8.2 PDF Viewer Implementation

```kotlin
// Recommended viewer approach
class PdfViewerViewModel : ViewModel() {
    private val renderer: PdfRenderer  // open lazily
    val pageCount: Int
    
    fun renderPage(index: Int, width: Int): Bitmap {
        val page = renderer.openPage(index)
        val bitmap = Bitmap.createBitmap(width, (width * page.height / page.width), Bitmap.Config.ARGB_8888)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        return bitmap
    }
}
```

### 8.3 File Opening Strategy (SAF + FileProvider)

```kotlin
// Always copy URI content to cache before passing to engines
// POI and PDFBox work best with File paths, not ContentResolver streams
suspend fun uriToTempFile(uri: Uri, context: Context): File = withContext(Dispatchers.IO) {
    val name = context.getFileName(uri) ?: "temp_doc"
    val temp = File(context.cacheDir, name)
    context.contentResolver.openInputStream(uri)?.use { input ->
        temp.outputStream().use { output -> input.copyTo(output) }
    }
    temp
}
```

---

## 9. UI/UX Decisions

- **Home screen:** Two sections — "Recent Files" (horizontal scroll) + "Tools" (category cards: PDF / Documents / Images / Utilities)
- **Viewer toolbar:** Minimal. File name, page indicator, share button, overflow menu (more tools for this file)
- **Tool screens:** Single-purpose. File picker → options → process → result with share/save actions
- **Progress:** Bottom sheet with animated progress for long operations (merge, convert, compress)
- **Error handling:** Plain language errors. "This file may be corrupted" not stack traces. Always offer to try again or share the file
- **No monetization blockades:** Since the app is 100% free and fully offline, there are no lock screens, premium indicators, billing sheets, or ad boxes.

---

## 10. What We Skipped and Why

| Feature | Reason Skipped |
|---|---|
| PDF→Word/PPT/Excel | Requires complex layout analysis engine (20MB+). Recommend user to online tool |
| Remove Moiré | Needs OpenCV (~10MB) for only this one use case |
| Remove Shadow | Same — OpenCV dependency not justified |
| Remove Handwriting | No good lightweight TFLite model; results would be poor |
| Remove Watermark | Legally grey area (copyright concerns), technically hard |
| PPTX animations | No viable offline engine |
| Full DOCX complex layout | POI rendering limitation — communicate to reflow to user |
| Cloud sync / backup | Keep app offline-first; users use their own cloud providers |
| Collaboration / comments | Requires backend — out of scope |

---

## 11. Future Features (Low Tradeoff)

These can be added later without architectural changes:
- Unit converter (pure math, zero size overhead).
- eBook viewer (EPUB) via lightweight parsing.
- Password manager / notes (encrypted via AES).
- TTF font loading custom layouts.

---

## 12. Development Sequence

```
Week 1-2:   Project setup, clean architecture package layout, build.gradle.kts dependencies, Room DB
Week 3:     PDF Viewer (PdfRenderer zoom, scroll, canvas)
Week 4:     TXT editor, Image viewer + crop/compress utilities
Week 5:     XLSX Viewer (POI grid stream)
Week 6:     DOCX Viewer (POI reflow rendering)
Week 7:     PPTX Viewer (POI slide canvas drawings)
            → MILESTONE 1: Universal viewer core ready
Week 8-9:   Offline QR Code & Barcode utilities (ZXing engine + ML Kit camera loops)
Week 10-12: PDF advanced operations (Merge, Split, Rotate, Password)
Week 13-14: Image scanning adjustments & exports
Week 15:    Verification & final clean compilation
            → MILESTONE 2: Production offline suite ready
```

---

## 13. ProGuard / R8 Configuration

```proguard
# POI
-keep class org.apache.poi.** { *; }
-keep class org.openxmlformats.schemas.** { *; }
-dontwarn org.apache.xmlbeans.**
-dontwarn com.microsoft.schemas.**
-dontwarn org.etsi.**
-dontwarn org.w3.x2000.**

# PDFBox
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# ML Kit
-keep class com.google.mlkit.** { *; }

# UCrop
-keep class com.yalantis.ucrop.** { *; }
```

---

## 14. Testing Strategy

- **Unit tests:** Core engines (QrCodeGenerator, UriCacheUtils, PdfMerger) tested with sample assets in `src/test/assets/`.
- **UI tests:** Compose testing layouts verifying viewing render matrices, canvas scrolling, and zoom interactions.
- **OOM tests:** Dynamic memory profiling under massive file stresses (large PDFs/XLSX streams) targeting low-RAM profiles.

---

## 15. Notes for AI Coding Agent

When implementing any feature in this project:
1. **Always run file operations on `Dispatchers.IO`** — never on the main thread.
2. **Always copy URI to temp file before passing to POI or PDFBox** — see `UriCacheUtils`.
3. **POI is verbose** — expect 50-100 lines for even simple operations; that's normal.
4. **Bitmap memory:** Always call `bitmap.recycle()` when done with rendered pages; use `WeakReference` for page cache.
5. **PDFBox Android port** is `com.tom-roush:pdfbox-android`, NOT the standard `org.apache.pdfbox` — import paths differ.
6. **ML Kit models:** Always check model availability and download with user authorization prompts.
7. **SAF URIs expire** after process restart if not persisted — use `takePersistableUriPermission()` for recent listings.
8. **Error mapping:** Wrap raw POI/PDFBox errors into plain, human-friendly localized alerts.

---

*End of document — update version and date when decisions change*
