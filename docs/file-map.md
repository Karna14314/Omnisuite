# OmniSuite — Complete File Map

This document catalogs all source files within OmniSuite under `app/src/main/java/com/karnadigital/omnisuite/`, separated by layer and purpose.

**Last Updated:** 2026-09-01
**Total Files:** 140 Kotlin source files

---

## 🏗️ Core Application Shell
- **`MainActivity.kt`**: Application single-activity entry point. Registers launch intents, captures external file-sharing `ACTION_VIEW` triggers, sets dynamic edge-to-edge Compose content, and binds the navigation controller.
- **`OmniApplication.kt`**: Application class annotated with `@HiltAndroidApp`. Establishes global dependencies and overrides the Apache POI XML Input Factory system property for compatibility with Android's custom JVM environment.

---

## 🔬 Core Layer (`core/`)

### 1. File Processing Engines (`core/engine/`)

#### Root Engines
- **`DocumentSearchEngine.kt`**: Backend engine for real-time document search. Traverses text structures in PDFs (via PDFBox), Word paragraphs (via POI), and Excel cells (via POI) case-insensitively to generate snippets and page coordinate pointers.
- **`EncodingDetector.kt`**: Character encoding detection engine with BOM detection (UTF-8/16/32), UTF-8 validation, and single-byte encoding heuristics (Windows-1252, ISO-8859-1).
- **`SyntaxHighlighter.kt`**: Code syntax highlighting for 30+ programming languages including Kotlin, Java, Python, JS/TS, C/C++, C#, PHP, SQL, HTML/CSS, XML, JSON, YAML, Markdown, Gradle, Shell, Ruby, Go, Rust, Swift, Dart, Scala, R, Lua, Perl, and Log files.
- **`PdfLayoutParser.kt`**: PDF layout analysis engine for block-by-block editing. Extracts text blocks with bounding boxes, font metadata, and positional data using PDFBox.

#### Document Engines (`core/engine/document/`)
- **`OfficeConverter.kt`**: Core conversion engine that parses Word (`.docx`), Excel (`.xlsx`), and PowerPoint (`.pptx`) formats and writes them page-by-page as a standard PDF document using PDFBox.
- **`ReverseOfficeConverter.kt`**: Performs reverse file operations, extracting PDF contents into Word/Excel/PowerPoint structures, and houses AcroForm interactive form filling.

#### Image Engines (`core/engine/image/`)
- **`ImageLabExtensions.kt`**: Custom Canvas-based image manipulation engine supporting vertical image stitching, template-based ID card frames, and rotation-aware custom watermarking.
- **`ImageUtils.kt`**: Utility package for quick, low-overhead image scaling, rotation, format conversion (JPEG/PNG/WEBP), and quality compression.

#### Utility Engines (`core/engine/utility/`)
- **`QrCodeGenerator.kt`**: ZXing MultiFormatWriter configuration wrapper that generates QR codes and 1D Barcodes as Android Bitmaps offline. Supports 11+ QR formats and 7+ barcode formats.
- **`UtilityToolsRepository.kt`**: Shared utility operations repository for unit conversion, image enhancement, background removal, blur/sharpen, red-eye removal, collage maker, meme maker, and sticker operations.

### 2. Data & Persistence Layer (`core/model/` & `core/repository/`)
- **`RecentFile.kt`**: Room database entity defining schema attributes for file name, URI, size, access epoch timestamp, and operation flags.
- **`OmniDatabase.kt`**: Abstract Room Database initialization schema mapping entities and caching rules.
- **`RecentFileDao.kt`**: Data Access Object defining Room queries (reactive Flows and suspend one-shots) for caching, updates, and deletes.
- **`RecentFileRepository.kt`**: Repository coordinating database writes. Auto-resolves standard document lookups versus utility/conversion operations.
- **`ThemeRepository.kt`**: DataStore-backed repository for theme preferences.

### 3. Core Utilities (`core/util/`)
- **`UriCacheUtils.kt`**: Critically important SAF caching utility. Resolves Android storage permission barriers by writing secure local file copies in `context.cacheDir` for engine access.
- **`FileOutputManager.kt`**: MediaStore gateway routing finalized output streams to `Documents/OmniSuite` on Android 10+ without requiring runtime storage permission requests.
- **`ThemePreferences.kt`**: Persisted configuration manager for dark mode state, accent color selections, and output DPI targets.
- **`ZoomableBox.kt`**: Premium multi-touch gesture modifier supporting pinch-to-zoom scaling, bounds checking, and panning.
- **`TextSearchUtils.kt`**: Text search utilities with non-overlapping match detection.
- **`SpreadsheetUtils.kt`**: Spreadsheet utilities with type-aware comparison and proper CSV detection.
- **`ImageSampling.kt`**: Image downsampling utilities for memory-efficient image processing.
- **`UriSchemeUtils.kt`**: URI scheme validation utilities.
- **`ZipSecurity.kt`**: ZIP path traversal security validation.

---

## 🎨 Feature UI Layer (`feature/`)

### 1. Navigation Shell & File Browser (`feature/home/`)
- **`HomeScreen.kt`**: Main cockpit hosting bottom navigation. Contains sub-screens for Workspace, Tools Grid, Files category filters, and History logs.
- **`HomeScreenViewModel.kt`**: State holder managing history listings, cache clearings, and recent additions.
- **`FileBrowserScreen.kt`**: In-app file tree explorer navigating device structures, folders, and documents offline.
- **`FilesScreen.kt`**: File category filter screen that lets users view PDFs, Word files, Excel files, and images grouped in tabs.
- **`FilesViewModel.kt`**: State holder for file browsing and category filtering.
- **`NavigationEvent.kt`**: Sealed class with 64 navigation event types for type-safe navigation.

### 2. Polymorphic Document Viewers (`feature/viewer/`)
- **`ViewerDispatcherScreen.kt`**: Main file viewer gateway. Resolves MIME types, caches the target file, and routes to the appropriate viewer composable.
- **`ViewerTool.kt`**: Sealed class defining viewer tool actions (Print, Share, OpenIn).
- **`ViewerActionBar.kt`**: Reusable viewer action bar component.
- **`EditMenu.kt`**: Reusable edit menu components (EditMenuPopup, FormatMenuPopup, EditMenuButton) for Microsoft 365-style editing.

#### PDF Viewer
- **`PdfViewerScreen.kt`**: Lazy-loading PDF canvas page renderer with search highlights, sticky notes, freehand pen draws, print spools, and read-aloud TTS.
- **`PdfViewerViewModel.kt`**: Handles loading, background page rendering, annotation saving, and keyword searches.

#### Word Viewer
- **`DocxViewerScreen.kt`**: Reflowed text renderer for Word documents with custom shadow-cast A4 page view, double-tap zoom, and comprehensive edit menu (Microsoft 365 style).
- **`DocxViewerViewModel.kt`**: Traverses paragraph shapes, extracts run formatting, updates text, and saves changes back to DOCX.

#### Excel Viewer
- **`XlsxViewerScreen.kt`**: Excel document grid sheet with WebView rendering, cell editing, formula evaluation, and chart support.
- **`XlsxViewerViewModel.kt`**: Streams Excel workbooks, handles cell editing updates, evaluates formulas, and transcodes sheets.

#### PowerPoint Viewer
- **`PptxViewerScreen.kt`**: Paged presentation slideshow viewer with continuous flow, grid overview, and slideshow modes.
- **`PptxViewerViewModel.kt`**: Renders slides to bitmaps, handles text shape editing, and z-order parsing.
- **`PptxSearchEngine.kt`**: PowerPoint-specific search engine.

#### Other Viewers
- **`TxtViewerScreen.kt`**: Plain text viewing and editing canvas with syntax highlighting for 30+ languages.
- **`TxtViewerViewModel.kt`**: Plain text file writer with encoding detection.
- **`ImageViewerScreen.kt`**: Coil-based zoomable image canvas with direct hooks to Image Lab crop and resizing screens.
- **`ImageViewerViewModel.kt`**: Image viewer state management.
- **`ArchiveViewerScreen.kt`**: ZIP directory tree index inspector with path traversal protection.
- **`SequentialImageViewerScreen.kt`**: Multi-image sequential viewer.

### 3. PDF Tools (`feature/pdf_tools/`) - 46 files

#### Core PDF Operations
- **`PdfToolsViewModel.kt`**: Central Hilt ViewModel coordinating all backend operations for PDF merging, page splitting, password lock configurations, and office transcoding.
- **`PdfToolsRepository.kt`**: Repository with 30+ PDF operations including merge, split, compress, rotate, reorder, watermark, and more.
- **`PdfMergeScreen.kt`**: PDF merger screen.
- **`PdfSplitScreen.kt`**: PDF split screen.
- **`PdfLockScreen.kt`**: Password locker.
- **`PdfDecryptScreen.kt`**: Password remover.
- **`PdfRotateScreen.kt`**: Page rotator.
- **`PdfExtractScreen.kt`**: Page extractor.
- **`PdfDeleteScreen.kt`**: Page deleter.
- **`PdfCompressScreen.kt`**: PDF compressor.
- **`PdfFlattenPDF.kt`**: Form flattener.

#### PDF Conversions
- **`DocToPdfScreen.kt`**: Word-to-PDF converter.
- **`PptToPdfScreen.kt`**: Slides-to-PDF converter.
- **`XlsToPdfScreen.kt`**: Excel-to-PDF converter.
- **`ImagesToPdfScreen.kt`**: Image compilation screen.
- **`ImagesToPdfLayoutScreen.kt`**: Images to PDF with layout options (1/2/4/6 per page).
- **`ScanToPdfScreen.kt`**: Camera-to-PDF screen.
- **`WebToPdfScreen.kt`**: Web-to-PDF renderer.
- **`HtmlToPdfScreen.kt`**: HTML-to-PDF compiler.
- **`MarkdownToPdfScreen.kt`**: Markdown-to-PDF converter.
- **`TxtToPdfScreen.kt`**: Text-to-PDF converter.
- **`CsvToPdfScreen.kt`**: CSV-to-PDF table converter.
- **`SvgToPdfScreen.kt`**: SVG-to-PDF converter.

#### Reverse Conversions
- **`PdfToImagesScreen.kt`**: PDF-to-images extractor.
- **`PdfToWordScreen.kt`**: PDF-to-Word converter.
- **`PdfToPptScreen.kt`**: PDF-to-PowerPoint converter.
- **`PdfToExcelScreen.kt`**: PDF-to-Excel converter.
- **`PdfToTxtScreen.kt`**: PDF-to-text extractor.
- **`PdfToMarkdownScreen.kt`**: PDF-to-Markdown converter.

#### Advanced PDF Tools
- **`PdfPageNumberScreen.kt`**: Page numbering tool.
- **`PdfReorderScreen.kt`**: Drag-and-drop page reorder.
- **`PdfExtractImagesScreen.kt`**: Image extractor.
- **`PdfFormFillerScreen.kt`**: Form filler.
- **`WatermarkScreen.kt`** & **`WatermarkViewModel.kt`**: Watermark applicator.
- **`SignaturePadScreen.kt`** & **`SignatureViewModel.kt`**: Digital signature pad.
- **`PdfHeaderFooterScreen.kt`**: Header/footer adder.
- **`PdfResizeScreen.kt`**: Page resize (A3/A4/A5/Letter/Legal).
- **`PdfToPdfAScreen.kt`**: PDF/A archival converter.
- **`PdfMetadataScreen.kt`**: Metadata editor.
- **`PdfCropMarginsScreen.kt`**: Margin cropper.
- **`PdfRedactScreen.kt`**: Content redactor.
- **`PdfRepairScreen.kt`**: PDF repair tool.
- **`PdfOverlayScreen.kt`**: PDF overlay tool.
- **`PdfCompareScreen.kt`**: PDF text comparator.
- **`PdfSplitBySizeScreen.kt`**: Size-based splitter.
- **`PdfInsertPagesScreen.kt`**: Page inserter.
- **`PdfReplacePagesScreen.kt`**: Page replacer.
- **`PdfBookmarksScreen.kt`**: Bookmark editor.
- **`PdfSplitByBookmarksScreen.kt`**: Bookmark-based splitter.
- **`PdfUnderlayScreen.kt`**: PDF underlay tool.
- **`PdfFormCreationScreen.kt`**: Form creation tool.
- **`PdfSelectiveImageExtractScreen.kt`**: Selective image extractor.
- **`PdfAllPagesToImageScreen.kt`**: All pages to images renderer.
- **`PdfBookmarkReaderScreen.kt`**: Bookmark reader.
- **`PdfAValidationScreen.kt`**: PDF/A validator.
- **`PdfToWordEnhancedScreen.kt`**: Enhanced PDF-to-Word converter.
- **`MarkdownToPdfEnhancedScreen.kt`**: Enhanced Markdown-to-PDF converter.
- **`AdvancedWordCountScreen.kt`**: Advanced word count analyzer.

#### Block Editor (Experimental)
- **`PdfBlockEditorScreen.kt`**: Block-by-block PDF editor (LightPDF-style).
- **`PdfBlockEditorViewModel.kt`**: Block editor state management.

### 4. Planning & Utility Tools (`feature/tools/` & `feature/utility/`)

#### Tools
- **`AllToolsScreen.kt`**: Grid categorizing and launching the application's 86+ tools across 6 tabs.
- **`BatchToolsScreen.kt`**: Dashboard for queuing multiple image actions and PDF encryptions.
- **`BatchOperationsManager.kt`**: Handles parallel batch operations on a background thread pool.
- **`ImageToolsScreen.kt`**: Interface for image cropping, scaling, watermarking, and stitching.
- **`ImageToolsViewModel.kt`**: Coordinates parameters for Image Lab modifications.
- **`ZipMakerScreen.kt`**: Panel for selecting multiple files and packaging them into an archive.
- **`ZipMakerViewModel.kt`**: Builds standard ZIP files offline using standard JVM `ZipOutputStream`.
- **`TarToolsScreen.kt`**: TAR archive creator and extractor.

#### Utility
- **`QrGeneratorScreen.kt`**: Form-based QR code builder supporting 11+ payload schemes (WiFi, vCard, Email, etc.).
- **`QrGeneratorViewModel.kt`**: Logs generated QR metadata into Room database history.
- **`BarcodeScannerScreen.kt`**: CameraX viewfinder screen for scanning barcodes and QR codes.
- **`BarcodeScannerViewModel.kt`**: Barcode scanning state management.
- **`OcrScreen.kt`**: Text extraction screen hosting on-demand offline ML Kit Text Recognition workflows.
- **`OcrViewModel.kt`**: OCR state management.
- **`ScannerScreen.kt`**: Document scanner with camera capture.
- **`DocumentScannerWrapper.kt`**: Integrates the ML Kit Document Scanner API for automated edge-detection.
- **`UtilityHubScreen.kt`**: Utility tools dashboard.
- **`UtilityScreens.kt`**: Unit converter, color picker, collage maker, meme maker screens.
- **`UtilityToolsViewModel.kt`**: Utility tools state management.
- **`StickerScreens.kt`**: Sticker maker, sticker import, exact resize screens.
- **`ReadAloud.kt`**: Lightweight offline text-to-speech using Android TTS engine.

### 5. Settings & History (`feature/settings/` & `feature/history/`)
- **`SettingsScreen.kt`**: User settings panel for theme toggles, DPI targets, and cache cleanup.
- **`SettingsViewModel.kt`**: Settings state management.
- **`HistoryScreen.kt`**: History log displaying recent files and utility actions stored in the database.
- **`HistoryViewModel.kt`**: History state management.

---

## 🎨 Shared UI Components (`ui/`)

### Components (`ui/component/`)
- **`OmniBottomNav.kt`**: Custom bottom navigation bar.
- **`OmniTopBar.kt`**: Standardized Top App Bar.
- **`RecentFileChip.kt`**: Document card for Workspace dashboard listings.
- **`SectionHeader.kt`**: Typography label with action anchors.
- **`SettingToggleRow.kt`**: Reusable card hosting settings switches.
- **`ToolListRow.kt`**: Full-width row used in tool grids.
- **`ToolkitCard.kt`**: Interactive grid card mapping dashboard highlights.
- **`CommonStates.kt`**: Shared UI state components (EmptyState, LoadingIndicator, ErrorState, ConfirmationDialog).
- **`OperationResultBottomSheet.kt`**: Operation result display bottom sheet.

### Navigation (`ui/navigation/`)
- **`OmniNavGraph.kt`**: Navigation graph registering all 87 composable destinations.
- **`Screen.kt`**: Sealed class mapping 76 routes.

### Theme (`ui/theme/`)
- **`Color.kt`**: HSL semantic palettes and Light/Dark values.
- **`Theme.kt`**: Configures Material3 ColorSchemes and applies status bar settings.
- **`Type.kt`**: Binds Syne and DM Sans font families for brand alignment.
