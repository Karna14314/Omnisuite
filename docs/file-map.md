# OmniSuite — Complete File Map

This document catalogs all source files within OmniSuite under `app/src/main/java/com/karnadigital/omnisuite/`, separated by layer and purpose.

---

## 🏗️ Core Application Shell
- **[MainActivity.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/MainActivity.kt)**: Application single-activity entry point. Registers launch intents, captures external file-sharing `ACTION_VIEW` triggers, sets dynamic edge-to-edge Compose content, and binds the navigation controller.
- **[OmniApplication.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/OmniApplication.kt)**: Application class annotated with `@HiltAndroidApp`. Establishes global dependencies and overrides the Apache POI XML Input Factory system property for compatibility with Android's custom JVM environment.

---

## 🔬 Core Layer (`core/`)

### 1. File Processing Engines (`core/engine/`)
- **[DocumentSearchEngine.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/core/engine/DocumentSearchEngine.kt)**: Backend engine for real-time document search. Traverses text structures in PDFs (via PDFBox), Word paragraphs (via POI), and Excel cells (via POI) case-insensitively to generate snippets and page coordinate pointers.
- **[document/OfficeConverter.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/core/engine/document/OfficeConverter.kt)**: Core conversion engine that parses Word (`.docx`), Excel (`.xlsx`), and PowerPoint (`.pptx`) formats and writes them page-by-page as a standard PDF document using PDFBox.
- **[document/ReverseOfficeConverter.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/core/engine/document/ReverseOfficeConverter.kt)**: Performs reverse file operations, extracting PDF contents into Word/Excel/PowerPoint structures, and houses AcroForm interactive form filling.
- **[image/ImageLabExtensions.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/core/engine/image/ImageLabExtensions.kt)**: Custom Canvas-based image manipulation engine supporting vertical image stitching, template-based ID card frames, and rotation-aware custom watermarking.
- **[image/ImageUtils.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/core/engine/image/ImageUtils.kt)**: Utility package for quick, low-overhead image scaling, rotation, format conversion (JPEG/PNG/WEBP), and quality compression.
- **[utility/QrCodeGenerator.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/core/engine/utility/QrCodeGenerator.kt)**: ZXing MultiFormatWriter configuration wrapper that generates QR codes and 1D Barcodes as Android Bitmaps offline.

### 2. Data & Persistence Layer (`core/model/` & `core/repository/`)
- **[RecentFile.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/core/model/RecentFile.kt)**: Room database entity defining schema attributes for file name, URI, size, access epoch timestamp, and operation flags.
- **[OmniDatabase.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/core/repository/OmniDatabase.kt)**: Abstract Room Database initialization schema mapping entities and caching rules.
- **[RecentFileDao.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/core/repository/RecentFileDao.kt)**: Data Access Object defining Room queries (reactive Flows and suspend one-shots) for caching, updates, and deletes.
- **[RecentFileRepository.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/core/repository/RecentFileRepository.kt)**: Repository coordinating database writes. Auto-resolves standard document lookups versus utility/conversion operations.

### 3. Core Utilities (`core/util/`)
- **[UriCacheUtils.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/core/util/UriCacheUtils.kt)**: Critically important SAF caching utility. Resolves Android storage permission barriers by writing secure local file copies in `context.cacheDir` for engine access.
- **[FileOutputManager.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/core/util/FileOutputManager.kt)**: MediaStore gateway routing finalized output streams to `Documents/OmniSuite` on Android 10+ without requiring runtime storage permission requests.
- **[ThemePreferences.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/core/util/ThemePreferences.kt)**: Persisted configuration manager for dark mode state, accent color selections, and output DPI targets.
- **[ZoomableBox.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/core/util/ZoomableBox.kt)**: Premium multi-touch gesture modifier supporting pinch-to-zoom scaling, bounds checking, and panning.

---

## 🎨 Feature UI Layer (`feature/`)

### 1. Navigation Shell & File Browser (`feature/home/`)
- **[HomeScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/home/HomeScreen.kt)**: Main cockpit hosting bottom navigation. Contains sub-screens for Workspace, Tools Grid, Files category filters, and History logs.
- **[HomeScreenViewModel.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/home/HomeScreenViewModel.kt)**: State holder managing history listings, cache clearings, and recent additions.
- **[FileBrowserScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/home/FileBrowserScreen.kt)**: In-app file tree explorer navigating device structures, folders, and documents offline.
- **[FilesScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/home/FilesScreen.kt)**: File category filter screen that lets users view PDFs, Word files, Excel files, and images grouped in tabs.

### 2. Polymorphic Document Viewers (`feature/viewer/`)
- **[ViewerDispatcherScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/viewer/ViewerDispatcherScreen.kt)**: Main file viewer gateway. Resolves MIME types, caches the target file, and routes to the appropriate viewer composable.
- **[PdfViewerScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/viewer/PdfViewerScreen.kt)**: Lazy-loading PDF canvas page renderer with search highlights, sticky notes, freehand pen draws, and print spools.
- **[PdfViewerViewModel.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/viewer/PdfViewerViewModel.kt)**: Handles loading, background page rendering, annotation saving, and keyword searches.
- **[DocxViewerScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/viewer/DocxViewerScreen.kt)**: Reflowed text renderer for Word documents. Integrates a custom shadow-cast A4 page view and double-tap zoom triggers.
- **[DocxViewerViewModel.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/viewer/DocxViewerViewModel.kt)**: Traverses paragraph shapes, extracts run formatting, updates text, and saves changes back to DOCX.
- **[XlsxViewerScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/viewer/XlsxViewerScreen.kt)**: Excel document grid sheet. Computes grid coordinates and scrolls tabular layouts smoothly.
- **[XlsxViewerViewModel.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/viewer/XlsxViewerViewModel.kt)**: Streams Excel workbooks, handles cell editing updates, evaluates formulas, and transcodes sheets.
- **[PptxViewerScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/viewer/PptxViewerScreen.kt)**: Paged presentation slideshow viewer.
- **[PptxViewerViewModel.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/viewer/PptxViewerViewModel.kt)**: Renders slides to bitmaps for smooth swiping.
- **[TxtViewerScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/viewer/TxtViewerScreen.kt)**: Plain text viewing and editing canvas.
- **[TxtViewerViewModel.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/viewer/TxtViewerViewModel.kt)**: Simple plain text file writer.
- **[ImageViewerScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/viewer/ImageViewerScreen.kt)**: Coil-based zoomable image canvas with direct hooks to Image Lab crop and resizing screens.
- **[ArchiveViewerScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/viewer/ArchiveViewerScreen.kt)**: ZIP directory tree index inspector.

### 3. Planning & Utility Tools (`feature/tools/` & `feature/utility/`)
- **[AllToolsScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/tools/AllToolsScreen.kt)**: Grid categorizing and launching the application's 30+ planning tools.
- **[BatchToolsScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/tools/BatchToolsScreen.kt)**: Dashboard for queuing multiple image actions and PDF encryptions.
- **[BatchOperationsManager.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/tools/BatchOperationsManager.kt)**: Handles parallel batch operations on a background thread pool.
- **[ImageToolsScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/tools/ImageToolsScreen.kt)**: Interface for image cropping, scaling, watermarking, and stitching.
- **[ImageToolsViewModel.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/tools/ImageToolsViewModel.kt)**: Coordinates parameters for Image Lab modifications.
- **[ZipMakerScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/tools/ZipMakerScreen.kt)**: Panel for selecting multiple files and packaging them into a archive.
- **[ZipMakerViewModel.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/tools/ZipMakerViewModel.kt)**: Builds standard ZIP files offline using standard JVM `ZipOutputStream`.
- **[QrGeneratorScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/utility/QrGeneratorScreen.kt)**: Form-based QR code builder supporting 11 payload schemes (WiFi, vCard, Email, etc.).
- **[QrGeneratorViewModel.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/utility/QrGeneratorViewModel.kt)**: Logs generated QR metadata logs into Room database history.
- **[OcrScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/utility/OcrScreen.kt)**: Text extraction screen hosting on-demand offline ML Kit Text Recognition workflows.
- **[BarcodeScannerScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/utility/BarcodeScannerScreen.kt)**: CameraX viewfinder screen for scanning barcodes and QR codes.
- **[DocumentScannerWrapper.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/utility/DocumentScannerWrapper.kt)**: Integrates the ML Kit Document Scanner API for automated edge-detection.

### 4. Settings & History (`feature/settings/` & `feature/history/`)
- **[SettingsScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/settings/SettingsScreen.kt)**: User settings panel for theme toggles, DPI targets, and cache cleanup.
- **[HistoryScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/history/HistoryScreen.kt)**: History log displaying recent files and utility actions stored in the database.

### 5. Advanced PDF Toolkit (`feature/pdf_tools/`)
- **[PdfToolsViewModel.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/pdf_tools/PdfToolsViewModel.kt)**: Central Hilt ViewModel coordinating all backend operations for PDF merging, page splitting, password lock configurations, and office transcoding.
- **[PdfMergeScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/pdf_tools/PdfMergeScreen.kt)**: PDF merger screen.
- **[PdfSplitScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/pdf_tools/PdfSplitScreen.kt)**: PDF split screen.
- **[PdfLockScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/pdf_tools/PdfLockScreen.kt)**: Password locker.
- **[ImagesToPdfScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/pdf_tools/ImagesToPdfScreen.kt)**: Image compilation screen.
- **[DocToPdfScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/pdf_tools/DocToPdfScreen.kt)**: Word-to-PDF screen.
- **[PptToPdfScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/pdf_tools/PptToPdfScreen.kt)**: Slides-to-PDF screen.
- **[ScanToPdfScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/pdf_tools/ScanToPdfScreen.kt)**: Camera-to-PDF screen.
- **[WatermarkScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/pdf_tools/WatermarkScreen.kt)** & **[WatermarkViewModel.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/pdf_tools/WatermarkViewModel.kt)**: Form layouts for applying watermarks.
- **[SignaturePadScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/pdf_tools/SignaturePadScreen.kt)** & **[SignatureViewModel.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/pdf_tools/SignatureViewModel.kt)**: Signature pad screen.
- **[PdfToImagesScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/pdf_tools/PdfToImagesScreen.kt)**, **[PdfToWordScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/pdf_tools/PdfToWordScreen.kt)**, **[PdfToPptScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/pdf_tools/PdfToPptScreen.kt)**, **[PdfToExcelScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/pdf_tools/PdfToExcelScreen.kt)**, **[PdfFormFillerScreen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/feature/pdf_tools/PdfFormFillerScreen.kt)**.

---

## 🎨 Shared UI components (`ui/`)
- **[ui/component/OmniBottomNav.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/ui/component/OmniBottomNav.kt)**: Custom bottom navigation bar.
- **[ui/component/OmniTopBar.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/ui/component/OmniTopBar.kt)**: Standardized Top App Bar.
- **[ui/component/RecentFileChip.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/ui/component/RecentFileChip.kt)**: Document card for Workspace dashboard listings.
- **[ui/component/SectionHeader.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/ui/component/SectionHeader.kt)**: Typography label with action anchors.
- **[ui/component/SettingToggleRow.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/ui/component/SettingToggleRow.kt)**: Reusable card hosting settings switches.
- **[ui/component/ToolListRow.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/ui/component/ToolListRow.kt)**: Full-width row used in tool grids.
- **[ui/component/ToolkitCard.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/ui/component/ToolkitCard.kt)**: Interactive grid card mapping dashboard highlights.
- **[ui/navigation/OmniNavGraph.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/ui/navigation/OmniNavGraph.kt)**: Navigation graph registering all routes.
- **[ui/navigation/Screen.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/ui/navigation/Screen.kt)**: Sealed class mapping routes.
- **[ui/theme/Color.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/ui/theme/Color.kt)**: HSL semantic palettes and Light/Dark values.
- **[ui/theme/Theme.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/ui/theme/Theme.kt)**: Configures Material3 ColorSchemes and applies status bar settings.
- **[ui/theme/Type.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/ui/theme/Type.kt)**: Binds Syne and DM Sans font families for brand alignment.
