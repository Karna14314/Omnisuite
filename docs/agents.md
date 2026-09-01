# OmniSuite — AI Agent Onboarding & Codebase Navigation Guide

> **Purpose**: This document is the **primary entry point** for any AI coding agent working on OmniSuite. It maps every feature to its exact source files, explains responsibilities, and provides quick-fix lookup tables so agents can locate, understand, and surgically edit code in seconds.

**Last Updated:** 2026-09-01
**Total Files:** 140 Kotlin source files

---

## 📋 Quick Reference Card

| Property | Value |
|---|---|
| **Package** | `com.karnadigital.omnisuite` |
| **Language** | Kotlin |
| **UI Framework** | Jetpack Compose (Material 3) |
| **Architecture** | MVVM + Clean Architecture |
| **DI Framework** | Dagger Hilt |
| **Database** | Room (SQLite) |
| **Min SDK** | 30 (Android 11) |
| **Target SDK** | 36 (Android 16) |
| **Compile SDK** | 36 |
| **JDK** | 17 |
| **Offline-Only** | Yes — zero network features allowed |
| **Total Routes** | 76 (Screen.kt) / 87 (OmniNavGraph.kt) |
| **Total Tools** | 86 across 6 tabs |

---

## 🗂️ Source Root

All source code lives under:
```
app/src/main/java/com/karnadigital/omnisuite/
```

Abbreviated as `~/` in file paths below for readability.

---

## 🏗️ Package Architecture Overview

```
~/
├── MainActivity.kt              ← Single-Activity entry point, edge-to-edge, Hilt
├── OmniApplication.kt           ← @HiltAndroidApp, PDFBox initializer
├── core/                        ← Business logic, engines, data layer (NO UI)
│   ├── engine/                  ← File processing engines
│   │   ├── DocumentSearchEngine.kt
│   │   ├── EncodingDetector.kt
│   │   ├── SyntaxHighlighter.kt
│   │   ├── PdfLayoutParser.kt
│   │   ├── document/
│   │   │   ├── OfficeConverter.kt
│   │   │   └── ReverseOfficeConverter.kt
│   │   ├── image/
│   │   │   ├── ImageLabExtensions.kt
│   │   │   └── ImageUtils.kt
│   │   └── utility/
│   │       ├── QrCodeGenerator.kt
│   │       └── UtilityToolsRepository.kt
│   ├── model/
│   │   └── RecentFile.kt
│   ├── repository/
│   │   ├── OmniDatabase.kt
│   │   ├── RecentFileDao.kt
│   │   ├── RecentFileRepository.kt
│   │   └── ThemeRepository.kt
│   └── util/
│       ├── FileOutputManager.kt
│       ├── ThemePreferences.kt
│       ├── UriCacheUtils.kt
│       ├── ZoomableBox.kt
│       ├── TextSearchUtils.kt
│       ├── SpreadsheetUtils.kt
│       ├── ImageSampling.kt
│       ├── UriSchemeUtils.kt
│       └── ZipSecurity.kt
├── di/
│   ├── CoreEntryPoint.kt
│   └── DatabaseModule.kt
├── feature/                     ← All UI screens (Jetpack Compose)
│   ├── home/
│   │   ├── HomeScreen.kt
│   │   ├── HomeScreenViewModel.kt
│   │   ├── FileBrowserScreen.kt
│   │   ├── FilesScreen.kt
│   │   ├── FilesViewModel.kt
│   │   └── NavigationEvent.kt
│   ├── viewer/
│   │   ├── ViewerDispatcherScreen.kt
│   │   ├── ViewerTool.kt
│   │   ├── ViewerActionBar.kt
│   │   ├── EditMenu.kt
│   │   ├── PdfViewerScreen.kt / PdfViewerViewModel.kt
│   │   ├── DocxViewerScreen.kt / DocxViewerViewModel.kt
│   │   ├── XlsxViewerScreen.kt / XlsxViewerViewModel.kt
│   │   ├── PptxViewerScreen.kt / PptxViewerViewModel.kt
│   │   ├── PptxSearchEngine.kt
│   │   ├── TxtViewerScreen.kt / TxtViewerViewModel.kt
│   │   ├── ImageViewerScreen.kt / ImageViewerViewModel.kt
│   │   ├── ArchiveViewerScreen.kt
│   │   └── SequentialImageViewerScreen.kt
│   ├── tools/
│   │   ├── AllToolsScreen.kt
│   │   ├── BatchToolsScreen.kt
│   │   ├── BatchOperationsManager.kt
│   │   ├── ImageToolsScreen.kt / ImageToolsViewModel.kt
│   │   ├── ZipMakerScreen.kt / ZipMakerViewModel.kt
│   │   └── TarToolsScreen.kt
│   ├── utility/
│   │   ├── QrGeneratorScreen.kt / QrGeneratorViewModel.kt
│   │   ├── BarcodeScannerScreen.kt / BarcodeScannerViewModel.kt
│   │   ├── OcrScreen.kt / OcrViewModel.kt
│   │   ├── DocumentScannerWrapper.kt
│   │   ├── ScannerScreen.kt
│   │   ├── UtilityHubScreen.kt
│   │   ├── UtilityScreens.kt
│   │   ├── UtilityToolsViewModel.kt
│   │   ├── StickerScreens.kt
│   │   └── ReadAloud.kt
│   ├── pdf_tools/ (46 files)
│   │   ├── PdfToolsViewModel.kt / PdfToolsRepository.kt
│   │   ├── PdfMergeScreen.kt / PdfSplitScreen.kt / PdfLockScreen.kt
│   │   ├── PdfDecryptScreen.kt / PdfRotateScreen.kt / PdfExtractScreen.kt
│   │   ├── PdfDeleteScreen.kt / PdfCompressScreen.kt / PdfFlattenScreen.kt
│   │   ├── DocToPdfScreen.kt / PptToPdfScreen.kt / XlsToPdfScreen.kt
│   │   ├── ScanToPdfScreen.kt / WebToPdfScreen.kt / HtmlToPdfScreen.kt
│   │   ├── MarkdownToPdfScreen.kt / TxtToPdfScreen.kt / CsvToPdfScreen.kt
│   │   ├── ImagesToPdfScreen.kt / ImagesToPdfLayoutScreen.kt / SvgToPdfScreen.kt
│   │   ├── PdfToImagesScreen.kt / PdfToWordScreen.kt / PdfToPptScreen.kt
│   │   ├── PdfToExcelScreen.kt / PdfToTxtScreen.kt / PdfToMarkdownScreen.kt
│   │   ├── PdfPageNumberScreen.kt / PdfReorderScreen.kt / PdfExtractImagesScreen.kt
│   │   ├── PdfFormFillerScreen.kt / PdfHeaderFooterScreen.kt / PdfResizeScreen.kt
│   │   ├── PdfToPdfAScreen.kt / PdfMetadataScreen.kt / PdfCropMarginsScreen.kt
│   │   ├── PdfRedactScreen.kt / PdfRepairScreen.kt / PdfOverlayScreen.kt
│   │   ├── PdfCompareScreen.kt / PdfSplitBySizeScreen.kt / PdfInsertPagesScreen.kt
│   │   ├── PdfReplacePagesScreen.kt / PdfBookmarksScreen.kt / PdfSplitByBookmarksScreen.kt
│   │   ├── PdfUnderlayScreen.kt / PdfFormCreationScreen.kt
│   │   ├── PdfSelectiveImageExtractScreen.kt / PdfAllPagesToImageScreen.kt
│   │   ├── PdfBookmarkReaderScreen.kt / PdfAValidationScreen.kt
│   │   ├── PdfToWordEnhancedScreen.kt / MarkdownToPdfEnhancedScreen.kt
│   │   ├── AdvancedWordCountScreen.kt
│   │   ├── WatermarkScreen.kt / WatermarkViewModel.kt
│   │   ├── SignaturePadScreen.kt / SignatureViewModel.kt
│   │   ├── PasswordZipScreen.kt / PasswordZipExtractScreen.kt
│   │   ├── FileEncryptScreen.kt / FileDecryptScreen.kt / FileChecksumScreen.kt
│   │   ├── UnitConverterScreen.kt / ColorPickerScreen.kt
│   │   ├── CollageMakerScreen.kt / MemeMakerScreen.kt
│   │   ├── ExactResizeScreen.kt / TextCompareScreen.kt
│   │   ├── PdfBlockEditorScreen.kt / PdfBlockEditorViewModel.kt
│   │   ├── CsvToXlsxScreen.kt / XlsxToCsvScreen.kt
│   │   ├── DocxToTxtScreen.kt / PptxToTxtScreen.kt
│   │   └── MissedToolsScreens.kt / MoreMissedScreens.kt / PdfTier1Screens.kt / PdfTier2Screens.kt
│   ├── settings/
│   │   ├── SettingsScreen.kt / SettingsViewModel.kt
│   └── history/
│       ├── HistoryScreen.kt / HistoryViewModel.kt
└── ui/                          ← Shared UI layer (theme, components, navigation)
    ├── theme/
    │   ├── Color.kt / Theme.kt / Type.kt
    ├── component/
    │   ├── OmniBottomNav.kt / OmniTopBar.kt / RecentFileChip.kt
    │   ├── SectionHeader.kt / SettingToggleRow.kt / ToolListRow.kt
    │   ├── ToolkitCard.kt / CommonStates.kt / OperationResultBottomSheet.kt
    └── navigation/
        ├── Screen.kt            ← All route definitions (sealed class)
        └── OmniNavGraph.kt      ← NavHost composable graph
```

---

## 🔍 Feature → File Lookup Table

### Document Viewers

| Feature | Screen File | ViewModel | Engine / Core | Route |
|---|---|---|---|---|
| **Viewer Dispatch** (MIME routing) | `feature/viewer/ViewerDispatcherScreen.kt` | — | `core/util/UriCacheUtils.kt` | `viewer_dispatcher` |
| **PDF Viewer** | `feature/viewer/PdfViewerScreen.kt` | `PdfViewerViewModel.kt` | Android `PdfRenderer` | via dispatcher |
| **DOCX/DOC Viewer** | `feature/viewer/DocxViewerScreen.kt` | `DocxViewerViewModel.kt` | Apache POI (`XWPFDocument`) | via dispatcher |
| **XLSX/XLS/CSV Viewer** | `feature/viewer/XlsxViewerScreen.kt` | `XlsxViewerViewModel.kt` | Apache POI (`XSSFWorkbook`) | via dispatcher |
| **PPTX/PPT Viewer** | `feature/viewer/PptxViewerScreen.kt` | `PptxViewerViewModel.kt` | Apache POI (`XMLSlideShow`) | via dispatcher |
| **TXT Editor** | `feature/viewer/TxtViewerScreen.kt` | `TxtViewerViewModel.kt` | Plain Kotlin I/O | via dispatcher |
| **Image Viewer** | `feature/viewer/ImageViewerScreen.kt` | `ImageViewerViewModel.kt` | Coil | via dispatcher |
| **ZIP/Archive Viewer** | `feature/viewer/ArchiveViewerScreen.kt` | — | `java.util.zip` | via dispatcher |

### PDF Tools (56 tools)

| Feature | Screen File | ViewModel | Route |
|---|---|---|---|
| **PDF Merge** | `feature/pdf_tools/PdfMergeScreen.kt` | `PdfToolsViewModel.kt` | `pdf_merge` |
| **PDF Split** | `feature/pdf_tools/PdfSplitScreen.kt` | `PdfToolsViewModel.kt` | `pdf_split` |
| **PDF Lock** | `feature/pdf_tools/PdfLockScreen.kt` | `PdfToolsViewModel.kt` | `pdf_lock` |
| **PDF Decrypt** | `feature/pdf_tools/PdfDecryptScreen.kt` | `PdfToolsViewModel.kt` | `pdf_decrypt` |
| **PDF Rotate** | `feature/pdf_tools/PdfRotateScreen.kt` | `PdfToolsViewModel.kt` | `pdf_rotate` |
| **PDF Extract** | `feature/pdf_tools/PdfExtractScreen.kt` | `PdfToolsViewModel.kt` | `pdf_extract` |
| **PDF Delete** | `feature/pdf_tools/PdfDeleteScreen.kt` | `PdfToolsViewModel.kt` | `pdf_delete` |
| **PDF Compress** | `feature/pdf_tools/PdfCompressScreen.kt` | `PdfToolsViewModel.kt` | `pdf_compress` |
| **PDF Flatten** | `feature/pdf_tools/PdfFlattenScreen.kt` | `PdfToolsViewModel.kt` | `pdf_flatten` |
| **PDF Page Number** | `feature/pdf_tools/PdfPageNumberScreen.kt` | `PdfToolsViewModel.kt` | `pdf_page_number` |
| **PDF Reorder** | `feature/pdf_tools/PdfReorderScreen.kt` | `PdfToolsViewModel.kt` | `pdf_reorder` |
| **PDF Extract Images** | `feature/pdf_tools/PdfExtractImagesScreen.kt` | `PdfToolsViewModel.kt` | `pdf_extract_images` |
| **PDF Form Filler** | `feature/pdf_tools/PdfFormFillerScreen.kt` | `PdfToolsViewModel.kt` | `pdf_form_filler` |
| **PDF Header/Footer** | `feature/pdf_tools/PdfHeaderFooterScreen.kt` | `PdfToolsViewModel.kt` | `pdf_header_footer` |
| **PDF Resize** | `feature/pdf_tools/PdfResizeScreen.kt` | `PdfToolsViewModel.kt` | `pdf_resize` |
| **PDF to PDF/A** | `feature/pdf_tools/PdfToPdfAScreen.kt` | `PdfToolsViewModel.kt` | `pdf_to_pdfa` |
| **PDF Metadata** | `feature/pdf_tools/PdfMetadataScreen.kt` | `PdfToolsViewModel.kt` | `pdf_metadata` |
| **PDF Crop Margins** | `feature/pdf_tools/PdfCropMarginsScreen.kt` | `PdfToolsViewModel.kt` | `pdf_crop_margins` |
| **PDF Redact** | `feature/pdf_tools/PdfRedactScreen.kt` | `PdfToolsViewModel.kt` | `pdf_redact` |
| **PDF Repair** | `feature/pdf_tools/PdfRepairScreen.kt` | `PdfToolsViewModel.kt` | `pdf_repair` |
| **PDF Overlay** | `feature/pdf_tools/PdfOverlayScreen.kt` | `PdfToolsViewModel.kt` | `pdf_overlay` |
| **PDF Compare** | `feature/pdf_tools/PdfCompareScreen.kt` | `PdfToolsViewModel.kt` | `pdf_compare` |
| **PDF to Markdown** | `feature/pdf_tools/PdfToMarkdownScreen.kt` | `PdfToolsViewModel.kt` | `pdf_to_markdown` |
| **PDF Split by Size** | `feature/pdf_tools/PdfSplitBySizeScreen.kt` | `PdfToolsViewModel.kt` | `pdf_split_by_size` |
| **PDF Insert Pages** | `feature/pdf_tools/PdfInsertPagesScreen.kt` | `PdfToolsViewModel.kt` | `pdf_insert_pages` |
| **PDF Replace Pages** | `feature/pdf_tools/PdfReplacePagesScreen.kt` | `PdfToolsViewModel.kt` | `pdf_replace_pages` |
| **PDF Bookmarks** | `feature/pdf_tools/PdfBookmarksScreen.kt` | `PdfToolsViewModel.kt` | `pdf_bookmarks` |
| **PDF Split by Bookmarks** | `feature/pdf_tools/PdfSplitByBookmarksScreen.kt` | `PdfToolsViewModel.kt` | `pdf_split_by_bookmarks` |
| **PDF Underlay** | `feature/pdf_tools/PdfUnderlayScreen.kt` | `PdfToolsViewModel.kt` | `pdf_underlay` |
| **PDF Form Creation** | `feature/pdf_tools/PdfFormCreationScreen.kt` | `PdfToolsViewModel.kt` | `pdf_form_creation` |
| **PDF Selective Image Extract** | `feature/pdf_tools/PdfSelectiveImageExtractScreen.kt` | `PdfToolsViewModel.kt` | `pdf_selective_image_extract` |
| **PDF All Pages to Image** | `feature/pdf_tools/PdfAllPagesToImageScreen.kt` | `PdfToolsViewModel.kt` | `pdf_all_pages_to_image` |
| **PDF Bookmark Reader** | `feature/pdf_tools/PdfBookmarkReaderScreen.kt` | `PdfToolsViewModel.kt` | `pdf_bookmark_reader` |
| **PDF/A Validation** | `feature/pdf_tools/PdfAValidationScreen.kt` | `PdfToolsViewModel.kt` | `pdf_a_validation` |
| **PDF to Word (Enhanced)** | `feature/pdf_tools/PdfToWordEnhancedScreen.kt` | `PdfToolsViewModel.kt` | `pdf_to_word_enhanced` |
| **MD to PDF (Enhanced)** | `feature/pdf_tools/MarkdownToPdfEnhancedScreen.kt` | `PdfToolsViewModel.kt` | `markdown_to_pdf_enhanced` |
| **Advanced Word Count** | `feature/pdf_tools/AdvancedWordCountScreen.kt` | `PdfToolsViewModel.kt` | `advanced_word_count` |
| **PDF Block Editor** | `feature/pdf_tools/PdfBlockEditorScreen.kt` | `PdfBlockEditorViewModel.kt` | `pdf_block_editor` |
| **Watermark** | `feature/pdf_tools/WatermarkScreen.kt` | `WatermarkViewModel.kt` | `watermark` |
| **Signature Pad** | `feature/pdf_tools/SignaturePadScreen.kt` | `SignatureViewModel.kt` | `signature_pad` |
| **Password ZIP** | `feature/pdf_tools/PasswordZipScreen.kt` | `PdfToolsViewModel.kt` | `password_zip` |
| **Extract Password ZIP** | `feature/pdf_tools/PasswordZipExtractScreen.kt` | `PdfToolsViewModel.kt` | `password_zip_extract` |
| **File Encrypt** | `feature/pdf_tools/FileEncryptScreen.kt` | `PdfToolsViewModel.kt` | `file_encrypt` |
| **File Decrypt** | `feature/pdf_tools/FileDecryptScreen.kt` | `PdfToolsViewModel.kt` | `file_decrypt` |
| **File Checksum** | `feature/pdf_tools/FileChecksumScreen.kt` | `PdfToolsViewModel.kt` | `file_checksum` |
| **Unit Converter** | `feature/pdf_tools/UnitConverterScreen.kt` | `UtilityToolsViewModel.kt` | `unit_converter` |
| **Color Picker** | `feature/pdf_tools/ColorPickerScreen.kt` | `UtilityToolsViewModel.kt` | `color_picker` |
| **Collage Maker** | `feature/pdf_tools/CollageMakerScreen.kt` | `UtilityToolsViewModel.kt` | `collage_maker` |
| **Meme Maker** | `feature/pdf_tools/MemeMakerScreen.kt` | `UtilityToolsViewModel.kt` | `meme_maker` |
| **Exact Resize** | `feature/pdf_tools/ExactResizeScreen.kt` | `UtilityToolsViewModel.kt` | `exact_resize` |
| **Text Compare** | `feature/pdf_tools/TextCompareScreen.kt` | `PdfToolsViewModel.kt` | `text_compare` |

### Conversions (12 tools)

| Feature | Screen File | Route |
|---|---|---|
| **DOCX → PDF** | `feature/pdf_tools/DocToPdfScreen.kt` | `doc_to_pdf` |
| **PPTX → PDF** | `feature/pdf_tools/PptToPdfScreen.kt` | `ppt_to_pdf` |
| **XLSX → PDF** | `feature/pdf_tools/XlsToPdfScreen.kt` | `xls_to_pdf` |
| **Scan → PDF** | `feature/pdf_tools/ScanToPdfScreen.kt` | `scan_to_pdf` |
| **Web → PDF** | `feature/pdf_tools/WebToPdfScreen.kt` | `web_to_pdf` |
| **HTML → PDF** | `feature/pdf_tools/HtmlToPdfScreen.kt` | `html_to_pdf` |
| **Markdown → PDF** | `feature/pdf_tools/MarkdownToPdfScreen.kt` | `markdown_to_pdf` |
| **TXT → PDF** | `feature/pdf_tools/TxtToPdfScreen.kt` | `txt_to_pdf` |
| **CSV → PDF** | `feature/pdf_tools/CsvToPdfScreen.kt` | `csv_to_pdf` |
| **Images → PDF** | `feature/pdf_tools/ImagesToPdfScreen.kt` | `images_to_pdf` |
| **Images → PDF+** | `feature/pdf_tools/ImagesToPdfLayoutScreen.kt` | `images_to_pdf_layout` |
| **SVG → PDF** | `feature/pdf_tools/SvgToPdfScreen.kt` | `svg_to_pdf` |

### Reverse Conversions (6 tools)

| Feature | Screen File | Route |
|---|---|---|
| **PDF → Images** | `feature/pdf_tools/PdfToImagesScreen.kt` | `pdf_to_images` |
| **PDF → Word** | `feature/pdf_tools/PdfToWordScreen.kt` | `pdf_to_word` |
| **PDF → PPT** | `feature/pdf_tools/PdfToPptScreen.kt` | `pdf_to_ppt` |
| **PDF → Excel** | `feature/pdf_tools/PdfToExcelScreen.kt` | `pdf_to_excel` |
| **PDF → Text** | `feature/pdf_tools/PdfToTxtScreen.kt` | `pdf_to_txt` |

### Image & Batch Tools

| Feature | Screen File | ViewModel | Route |
|---|---|---|---|
| **Image Tools** | `feature/tools/ImageToolsScreen.kt` | `ImageToolsViewModel.kt` | `image_tools` |
| **Batch Operations** | `feature/tools/BatchToolsScreen.kt` | `BatchOperationsManager.kt` | `batch_tools` |
| **ZIP Maker** | `feature/tools/ZipMakerScreen.kt` | `ZipMakerViewModel.kt` | `zip_maker` |
| **TAR Tools** | `feature/tools/TarToolsScreen.kt` | — | `tar_tools` |

### Utility Tools

| Feature | Screen File | ViewModel | Engine | Route |
|---|---|---|---|---|
| **QR Generator** | `feature/utility/QrGeneratorScreen.kt` | `QrGeneratorViewModel.kt` | `QrCodeGenerator.kt` | `qr_generator` |
| **Barcode Scanner** | `feature/utility/BarcodeScannerScreen.kt` | `BarcodeScannerViewModel.kt` | ML Kit + CameraX | `barcode_scanner` |
| **OCR** | `feature/utility/OcrScreen.kt` | `OcrViewModel.kt` | ML Kit | `ocr` |
| **Document Scanner** | `feature/utility/DocumentScannerWrapper.kt` | — | ML Kit | (wrapped) |
| **Sticker Maker** | `feature/utility/StickerScreens.kt` | `UtilityToolsViewModel.kt` | `UtilityToolsRepository.kt` | `sticker_maker` |
| **Sticker Import** | `feature/utility/StickerScreens.kt` | `UtilityToolsViewModel.kt` | `UtilityToolsRepository.kt` | `sticker_import` |
| **Read Aloud** | `feature/utility/ReadAloud.kt` | — | Android TTS | `read_aloud` |

### Navigation & Shell

| Feature | Files | Purpose |
|---|---|---|
| **Home Dashboard** | `HomeScreen.kt`, `HomeScreenViewModel.kt` | 4-tab bottom nav shell |
| **File Browser** | `FileBrowserScreen.kt` | Built-in tree browser |
| **Files Tab** | `FilesScreen.kt`, `FilesViewModel.kt` | File picker with SAF |
| **All Tools Grid** | `AllToolsScreen.kt` | Categorized tool launcher (86+ tools) |
| **History** | `HistoryScreen.kt`, `HistoryViewModel.kt` | Recent files log |
| **Settings** | `SettingsScreen.kt`, `SettingsViewModel.kt` | Theme toggle, app info |
| **Navigation Graph** | `OmniNavGraph.kt` | All route registrations |
| **Route Definitions** | `Screen.kt` | Sealed class with 76 route strings |

### Core Engines (Backend / Non-UI)

| Engine | File | Responsibility |
|---|---|---|
| **Office → PDF** | `core/engine/document/OfficeConverter.kt` | Converts DOCX/XLSX/PPTX → PDF |
| **PDF → Office** | `core/engine/document/ReverseOfficeConverter.kt` | Converts PDF → DOCX/XLSX/PPTX |
| **Document Search** | `core/engine/DocumentSearchEngine.kt` | Real-time text search |
| **Syntax Highlight** | `core/engine/SyntaxHighlighter.kt` | 30+ language syntax coloring |
| **Encoding Detect** | `core/engine/EncodingDetector.kt` | Character encoding detection |
| **PDF Layout Parse** | `core/engine/PdfLayoutParser.kt` | Block-by-block PDF analysis |
| **Image Lab** | `core/engine/image/ImageLabExtensions.kt` | Image manipulation |
| **Image Utils** | `core/engine/image/ImageUtils.kt` | Bitmap helpers |
| **QR/Barcode Gen** | `core/engine/utility/QrCodeGenerator.kt` | ZXing-based generation |
| **Utility Tools** | `core/engine/utility/UtilityToolsRepository.kt` | Unit conversion, enhancement |

### Data Layer

| Component | File | Purpose |
|---|---|---|
| **Room Database** | `core/repository/OmniDatabase.kt` | Abstract Room DB |
| **DAO** | `core/repository/RecentFileDao.kt` | CRUD operations |
| **Repository** | `core/repository/RecentFileRepository.kt` | Data access |
| **Theme Repository** | `core/repository/ThemeRepository.kt` | Theme persistence |
| **Entity** | `core/model/RecentFile.kt` | Room entity |
| **Hilt Module** | `di/DatabaseModule.kt` | DB singleton provider |
| **Core Entry** | `di/CoreEntryPoint.kt` | Non-Hilt access |

---

## 🛠️ Build & Deployment

### Individual Gradle Tasks
```bash
# Syntax check only
./gradlew compileDebugKotlin --no-daemon

# Build debug APK
./gradlew assembleDebug --no-daemon

# Install on device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch app
adb shell am start -n com.karnadigital.omnisuite/.MainActivity
```

---

## ⚠️ Critical Rules for Agents

### 1. 100% Offline — No Exceptions
No network calls, no cloud APIs, no analytics, no ads. Every engine runs on-device.

### 2. Always Use `Dispatchers.IO` for File Operations
All POI, PDFBox, and bitmap operations MUST run on background coroutines.

### 3. Always Cache SAF URIs Before Processing
```kotlin
val cachedFile = UriCacheUtils.cacheUri(context, uri)
```

### 4. Persist SAF Permissions for Recents
```kotlin
context.contentResolver.takePersistableUriPermission(uri, takeFlags)
```

### 5. PDFBox Import Path
Use `com.tom_roush.pdfbox` (NOT `org.apache.pdfbox`). This is the Android port.

### 6. ProGuard/R8 Rules
When adding new reflection-dependent libraries, update `app/proguard-rules.pro`.

### 7. Bitmap Memory Management
Always `bitmap.recycle()` after use. Use `WeakReference` for page caches.

### 8. Surgical Changes Only
- Match existing code style
- Don't refactor adjacent working code
- Only remove imports/variables YOUR changes made unused

---

## 🔗 Cross-Reference: "I need to fix X" Quick Guide

| If you need to fix... | Start with these files |
|---|---|
| App crashes on launch | `MainActivity.kt`, `OmniApplication.kt`, `AndroidManifest.xml` |
| Navigation broken | `OmniNavGraph.kt`, `Screen.kt` |
| File won't open | `ViewerDispatcherScreen.kt`, `UriCacheUtils.kt` |
| PDF viewer issues | `PdfViewerScreen.kt`, `PdfViewerViewModel.kt` |
| DOCX rendering wrong | `DocxViewerScreen.kt`, `DocxViewerViewModel.kt` |
| Excel grid broken | `XlsxViewerScreen.kt`, `XlsxViewerViewModel.kt` |
| Slides not rendering | `PptxViewerScreen.kt`, `PptxViewerViewModel.kt` |
| PDF merge/split fails | `PdfToolsViewModel.kt`, `PdfToolsRepository.kt` |
| Office → PDF fails | `core/engine/document/OfficeConverter.kt` |
| PDF → Office fails | `core/engine/document/ReverseOfficeConverter.kt` |
| QR code wrong | `QrGeneratorScreen.kt`, `QrCodeGenerator.kt` |
| Camera scanner fails | `BarcodeScannerScreen.kt` |
| OCR not working | `OcrScreen.kt`, `OcrViewModel.kt` |
| Image tools broken | `ImageToolsScreen.kt`, `ImageToolsViewModel.kt` |
| Zoom/pinch broken | `ZoomableBox.kt` |
| Theme/colors wrong | `ui/theme/Color.kt`, `Theme.kt`, `ThemePreferences.kt` |
| Recent files missing | `RecentFileRepository.kt`, `RecentFileDao.kt` |
| Build fails (Hilt) | `di/DatabaseModule.kt`, `di/CoreEntryPoint.kt` |
| PDF block editor | `PdfBlockEditorScreen.kt`, `PdfLayoutParser.kt` |
| Edit menu issues | `EditMenu.kt`, `DocxViewerScreen.kt` |
| Read aloud TTS | `ReadAloud.kt` |
| Sticker tools | `StickerScreens.kt`, `UtilityToolsRepository.kt` |

---

## 📚 Related Documentation

| Document | Path | Purpose |
|---|---|---|
| **Architecture Deep-Dive** | `docs/architecture.md` | Layered architecture, data flow |
| **File Map (Complete)** | `docs/file-map.md` | Every file with descriptions |
| **Navigation Reference** | `docs/navigation.md` | All routes and screen graph |
| **Dependencies Reference** | `docs/dependencies.md` | All third-party libraries |
| **Engine Reference** | `docs/engines.md` | Core engine APIs |
| **Roadmap** | `docs/ROADMAP.md` | Implementation progress |
| **Gap Analysis** | `docs/GAP_ANALYSIS.md` | Competitor comparison |
| **Full Audit** | `docs/FULL_AUDIT.md` | Complete tool inventory |
