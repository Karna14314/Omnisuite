# OmniSuite — AI Agent Onboarding & Codebase Navigation Guide

> **Purpose**: This document is the **primary entry point** for any AI coding agent working on OmniSuite. It maps every feature to its exact source files, explains responsibilities, and provides quick-fix lookup tables so agents can locate, understand, and surgically edit code in seconds.

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
| **Gradle** | 8.11.1 |
| **Kotlin Compiler Extension** | 1.5.8 |
| **Offline-Only** | Yes — zero network features allowed |

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
│   │   ├── document/
│   │   │   ├── OfficeConverter.kt
│   │   │   └── ReverseOfficeConverter.kt
│   │   ├── image/
│   │   │   ├── ImageLabExtensions.kt
│   │   │   └── ImageUtils.kt
│   │   ├── pdf/                 ← (empty — PDF ops live in PdfToolsViewModel)
│   │   └── utility/
│   │       └── QrCodeGenerator.kt
│   ├── model/
│   │   └── RecentFile.kt
│   ├── repository/
│   │   ├── OmniDatabase.kt
│   │   ├── RecentFileDao.kt
│   │   └── RecentFileRepository.kt
│   └── util/
│       ├── FileOutputManager.kt
│       ├── ThemePreferences.kt
│       ├── UriCacheUtils.kt
│       └── ZoomableBox.kt
├── di/
│   └── DatabaseModule.kt
├── feature/                     ← All UI screens (Jetpack Compose)
│   ├── home/
│   │   ├── HomeScreen.kt
│   │   ├── HomeScreenViewModel.kt
│   │   ├── FileBrowserScreen.kt
│   │   └── FilesScreen.kt
│   ├── viewer/
│   │   ├── ViewerDispatcherScreen.kt
│   │   ├── PdfViewerScreen.kt / PdfViewerViewModel.kt
│   │   ├── DocxViewerScreen.kt / DocxViewerViewModel.kt
│   │   ├── XlsxViewerScreen.kt / XlsxViewerViewModel.kt
│   │   ├── PptxViewerScreen.kt / PptxViewerViewModel.kt
│   │   ├── TxtViewerScreen.kt / TxtViewerViewModel.kt
│   │   ├── ImageViewerScreen.kt / ImageViewerViewModel.kt
│   │   └── ArchiveViewerScreen.kt
│   ├── tools/
│   │   ├── AllToolsScreen.kt
│   │   ├── BatchToolsScreen.kt
│   │   ├── BatchOperationsManager.kt
│   │   ├── ImageToolsScreen.kt / ImageToolsViewModel.kt
│   │   └── ZipMakerScreen.kt / ZipMakerViewModel.kt
│   ├── utility/
│   │   ├── QrGeneratorScreen.kt / QrGeneratorViewModel.kt
│   │   ├── OcrScreen.kt / OcrViewModel.kt
│   │   ├── BarcodeScannerScreen.kt / BarcodeScannerViewModel.kt
│   │   ├── DocumentScannerWrapper.kt
│   │   ├── ScannerScreen.kt
│   │   └── UtilityHubScreen.kt
│   ├── pdf_tools/
│   │   ├── PdfToolsViewModel.kt          ← Shared VM for ALL PDF operations
│   │   ├── PdfMergeScreen.kt
│   │   ├── PdfSplitScreen.kt
│   │   ├── PdfLockScreen.kt
│   │   ├── DocToPdfScreen.kt
│   │   ├── PptToPdfScreen.kt
│   │   ├── ScanToPdfScreen.kt
│   │   ├── ImagesToPdfScreen.kt
│   │   ├── PdfToImagesScreen.kt
│   │   ├── PdfToWordScreen.kt
│   │   ├── PdfToPptScreen.kt
│   │   ├── PdfToExcelScreen.kt
│   │   ├── PdfFormFillerScreen.kt
│   │   ├── WatermarkScreen.kt / WatermarkViewModel.kt
│   │   └── SignaturePadScreen.kt / SignatureViewModel.kt
│   ├── settings/
│   │   ├── SettingsScreen.kt
│   │   └── SettingsViewModel.kt
│   └── history/
│       ├── HistoryScreen.kt
│       └── HistoryViewModel.kt
└── ui/                          ← Shared UI layer (theme, components, navigation)
    ├── theme/
    │   ├── Color.kt
    │   ├── Theme.kt
    │   └── Type.kt
    ├── component/
    │   ├── OmniBottomNav.kt
    │   ├── OmniTopBar.kt
    │   ├── RecentFileChip.kt
    │   ├── SectionHeader.kt
    │   ├── SettingToggleRow.kt
    │   ├── ToolListRow.kt
    │   └── ToolkitCard.kt
    └── navigation/
        ├── Screen.kt            ← All route definitions (sealed class)
        └── OmniNavGraph.kt      ← NavHost composable graph
```

---

## 🔍 Feature → File Lookup Table

Use this table to instantly find which files to edit for any given feature or bug.

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

### PDF Tools

| Feature | Screen File | ViewModel | Route |
|---|---|---|---|
| **PDF Merge** | `feature/pdf_tools/PdfMergeScreen.kt` | `PdfToolsViewModel.kt` | `pdf_merge` |
| **PDF Split** | `feature/pdf_tools/PdfSplitScreen.kt` | `PdfToolsViewModel.kt` | `pdf_split` |
| **PDF Lock** | `feature/pdf_tools/PdfLockScreen.kt` | `PdfToolsViewModel.kt` | `pdf_lock` |
| **DOCX → PDF** | `feature/pdf_tools/DocToPdfScreen.kt` | `PdfToolsViewModel.kt` | `doc_to_pdf` |
| **PPTX → PDF** | `feature/pdf_tools/PptToPdfScreen.kt` | `PdfToolsViewModel.kt` | `ppt_to_pdf` |
| **Scan → PDF** | `feature/pdf_tools/ScanToPdfScreen.kt` | `PdfToolsViewModel.kt` | `scan_to_pdf` |
| **Images → PDF** | `feature/pdf_tools/ImagesToPdfScreen.kt` | `PdfToolsViewModel.kt` | `images_to_pdf` |
| **PDF → Images** | `feature/pdf_tools/PdfToImagesScreen.kt` | `PdfToolsViewModel.kt` | `pdf_to_images` |
| **PDF → Word** | `feature/pdf_tools/PdfToWordScreen.kt` | `PdfToolsViewModel.kt` | `pdf_to_word` |
| **PDF → PPT** | `feature/pdf_tools/PdfToPptScreen.kt` | `PdfToolsViewModel.kt` | `pdf_to_ppt` |
| **PDF → Excel** | `feature/pdf_tools/PdfToExcelScreen.kt` | `PdfToolsViewModel.kt` | `pdf_to_excel` |
| **Form Filler** | `feature/pdf_tools/PdfFormFillerScreen.kt` | `PdfToolsViewModel.kt` | `pdf_form_filler` |
| **Watermark** | `feature/pdf_tools/WatermarkScreen.kt` | `WatermarkViewModel.kt` | `watermark` |
| **Signature Pad** | `feature/pdf_tools/SignaturePadScreen.kt` | `SignatureViewModel.kt` | `signature_pad` |

### Image & Batch Tools

| Feature | Screen File | ViewModel | Route |
|---|---|---|---|
| **Image Tools** (compress/resize/crop/rotate/convert) | `feature/tools/ImageToolsScreen.kt` | `ImageToolsViewModel.kt` | `image_tools` |
| **Batch Operations** | `feature/tools/BatchToolsScreen.kt` | — (uses `BatchOperationsManager.kt`) | `batch_tools` |
| **ZIP Maker** | `feature/tools/ZipMakerScreen.kt` | `ZipMakerViewModel.kt` | `zip_maker` |

### Utility Tools

| Feature | Screen File | ViewModel | Engine | Route |
|---|---|---|---|---|
| **QR Generator** (11 payload types) | `feature/utility/QrGeneratorScreen.kt` | `QrGeneratorViewModel.kt` | `core/engine/utility/QrCodeGenerator.kt` | `qr_generator` |
| **Barcode/QR Scanner** | `feature/utility/BarcodeScannerScreen.kt` | `BarcodeScannerViewModel.kt` | ML Kit + CameraX | `barcode_scanner` |
| **OCR** | `feature/utility/OcrScreen.kt` | `OcrViewModel.kt` | ML Kit Text Recognition | `ocr` |
| **Document Scanner** | `feature/utility/DocumentScannerWrapper.kt` | — | ML Kit Document Scanner | (wrapped) |

### Navigation & Shell

| Feature | Files | Purpose |
|---|---|---|
| **Home Dashboard** | `feature/home/HomeScreen.kt`, `HomeScreenViewModel.kt` | 4-tab bottom nav shell (Workspace, Tools, Files, History) |
| **File Browser** (SAF) | `feature/home/FileBrowserScreen.kt` | Built-in tree browser for device storage |
| **Files Tab** | `feature/home/FilesScreen.kt` | File picker with SAF integration |
| **All Tools Grid** | `feature/tools/AllToolsScreen.kt` | Categorized tool launcher (30+ tools) |
| **History** | `feature/history/HistoryScreen.kt`, `HistoryViewModel.kt` | Recent files log from Room DB |
| **Settings** | `feature/settings/SettingsScreen.kt`, `SettingsViewModel.kt` | Theme toggle, app info |
| **Navigation Graph** | `ui/navigation/OmniNavGraph.kt` | All route registrations |
| **Route Definitions** | `ui/navigation/Screen.kt` | Sealed class with all route strings |

### Core Engines (Backend / Non-UI)

| Engine | File | Responsibility |
|---|---|---|
| **Office → PDF Converter** | `core/engine/document/OfficeConverter.kt` | Converts DOCX/XLSX/PPTX → PDF using POI + PDFBox |
| **PDF → Office Converter** | `core/engine/document/ReverseOfficeConverter.kt` | Converts PDF → DOCX/XLSX/PPTX (reverse flow) |
| **Document Search** | `core/engine/DocumentSearchEngine.kt` | Real-time in-document text search across all formats |
| **Image Lab** | `core/engine/image/ImageLabExtensions.kt` | Compress, resize, rotate, flip, format convert |
| **Image Utilities** | `core/engine/image/ImageUtils.kt` | Bitmap helpers and format detection |
| **QR/Barcode Generation** | `core/engine/utility/QrCodeGenerator.kt` | ZXing-based offline QR bitmap generation |
| **URI Caching** | `core/util/UriCacheUtils.kt` | Copies SAF URIs to temp files for engine consumption |
| **File Output** | `core/util/FileOutputManager.kt` | Saves processed files to user storage |
| **Theme Preferences** | `core/util/ThemePreferences.kt` | DataStore-based theme persistence |
| **Zoomable Box** | `core/util/ZoomableBox.kt` | Reusable pinch-to-zoom + double-tap Compose modifier |

### Data Layer

| Component | File | Purpose |
|---|---|---|
| **Room Database** | `core/repository/OmniDatabase.kt` | Abstract Room DB class |
| **DAO** | `core/repository/RecentFileDao.kt` | CRUD operations for recent files |
| **Repository** | `core/repository/RecentFileRepository.kt` | Business-level data access |
| **Entity** | `core/model/RecentFile.kt` | Room entity for file history records |
| **Hilt Module** | `di/DatabaseModule.kt` | Provides DB singleton + DAO + Repository |

### Shared UI Components

| Component | File | Used By |
|---|---|---|
| **Bottom Navigation** | `ui/component/OmniBottomNav.kt` | `HomeScreen.kt` |
| **Top App Bar** | `ui/component/OmniTopBar.kt` | Multiple screens |
| **Recent File Chip** | `ui/component/RecentFileChip.kt` | Home workspace |
| **Section Header** | `ui/component/SectionHeader.kt` | Home, Tools screens |
| **Settings Toggle** | `ui/component/SettingToggleRow.kt` | `SettingsScreen.kt` |
| **Tool Row** | `ui/component/ToolListRow.kt` | `AllToolsScreen.kt` |
| **Toolkit Card** | `ui/component/ToolkitCard.kt` | Home, Tools screens |
| **Color Tokens** | `ui/theme/Color.kt` | All screens |
| **Theme Config** | `ui/theme/Theme.kt` | `MainActivity.kt` |
| **Typography** | `ui/theme/Type.kt` | All screens |

---

## 🛠️ Build & Deployment

### One-Command Deploy
```powershell
# Windows
.\build_and_install.bat

# macOS/Linux
./build_and_install.sh
```

### Individual Gradle Tasks
```bash
# Syntax check only
./gradlew compileDebugKotlin --no-daemon

# Run unit tests
./gradlew testDebugUnitTest --no-daemon

# Build debug APK
./gradlew assembleDebug --no-daemon

# Install on device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch app
adb shell am start -n com.karnadigital.omnisuite/.MainActivity
```

### CI Pipeline
GitHub Actions (`.github/workflows/ci.yml`) runs on every push/PR to `main`/`master`:
1. `compileDebugKotlin` — syntax verification
2. `testDebugUnitTest` — JVM unit tests
3. `assembleDebug` — APK assembly

---

## ⚠️ Critical Rules for Agents

### 1. 100% Offline — No Exceptions
No network calls, no cloud APIs, no analytics, no ads. Every engine runs on-device.

### 2. Always Use `Dispatchers.IO` for File Operations
All POI, PDFBox, and bitmap operations MUST run on background coroutines. Never block the main thread.

### 3. Always Cache SAF URIs Before Processing
```kotlin
// POI and PDFBox need File paths, not content:// URIs
val cachedFile = UriCacheUtils.cacheUri(context, uri)
```

### 4. Persist SAF Permissions for Recents
```kotlin
context.contentResolver.takePersistableUriPermission(uri, takeFlags)
```

### 5. PDFBox Import Path
Use `com.tom_roush.pdfbox` (NOT `org.apache.pdfbox`). This is the Android port.

### 6. ProGuard/R8 Rules
When adding new reflection-dependent libraries, update `app/proguard-rules.pro`. POI and PDFBox require explicit keep rules.

### 7. Bitmap Memory Management
Always `bitmap.recycle()` after use. Use `WeakReference` for page caches. Target max 5 bitmaps in memory for large documents.

### 8. Surgical Changes Only
- Match existing code style
- Don't refactor adjacent working code
- Only remove imports/variables YOUR changes made unused

---

## 🔗 Cross-Reference: "I need to fix X" Quick Guide

| If you need to fix... | Start with these files |
|---|---|
| App crashes on launch | `MainActivity.kt`, `OmniApplication.kt`, `AndroidManifest.xml` |
| Navigation broken | `ui/navigation/OmniNavGraph.kt`, `ui/navigation/Screen.kt` |
| File won't open | `feature/viewer/ViewerDispatcherScreen.kt`, `core/util/UriCacheUtils.kt` |
| PDF viewer issues | `feature/viewer/PdfViewerScreen.kt`, `PdfViewerViewModel.kt` |
| DOCX rendering wrong | `feature/viewer/DocxViewerScreen.kt`, `DocxViewerViewModel.kt` |
| Excel grid broken | `feature/viewer/XlsxViewerScreen.kt`, `XlsxViewerViewModel.kt` |
| Slides not rendering | `feature/viewer/PptxViewerScreen.kt`, `PptxViewerViewModel.kt` |
| PDF merge/split fails | `feature/pdf_tools/PdfToolsViewModel.kt` |
| Office → PDF fails | `core/engine/document/OfficeConverter.kt` |
| PDF → Office fails | `core/engine/document/ReverseOfficeConverter.kt` |
| QR code wrong | `feature/utility/QrGeneratorScreen.kt`, `core/engine/utility/QrCodeGenerator.kt` |
| Camera scanner fails | `feature/utility/BarcodeScannerScreen.kt` |
| OCR not working | `feature/utility/OcrScreen.kt`, `OcrViewModel.kt` |
| Image tools broken | `feature/tools/ImageToolsScreen.kt`, `ImageToolsViewModel.kt`, `core/engine/image/` |
| Zoom/pinch broken | `core/util/ZoomableBox.kt` |
| Theme/colors wrong | `ui/theme/Color.kt`, `ui/theme/Theme.kt`, `core/util/ThemePreferences.kt` |
| Recent files missing | `core/repository/RecentFileRepository.kt`, `RecentFileDao.kt` |
| Build fails (Hilt) | `di/DatabaseModule.kt`, check `@HiltAndroidApp` on `OmniApplication.kt` |
| ProGuard crashes | `app/proguard-rules.pro` |
| Bottom nav broken | `ui/component/OmniBottomNav.kt`, `feature/home/HomeScreen.kt` |
| Batch operations | `feature/tools/BatchOperationsManager.kt`, `BatchToolsScreen.kt` |
| ZIP creation | `feature/tools/ZipMakerScreen.kt`, `ZipMakerViewModel.kt` |
| Search in documents | `core/engine/DocumentSearchEngine.kt` |
| Watermark issues | `feature/pdf_tools/WatermarkScreen.kt`, `WatermarkViewModel.kt` |
| Signature pad issues | `feature/pdf_tools/SignaturePadScreen.kt`, `SignatureViewModel.kt` |
| Settings not saving | `feature/settings/SettingsScreen.kt`, `core/util/ThemePreferences.kt` |

---

## 📚 Related Documentation

| Document | Path | Purpose |
|---|---|---|
| **Architecture Deep-Dive** | [architecture.md](file:///c:/Users/chait/Projects/Omnisuite/docs/architecture.md) | Layered architecture, data flow, dependency graph |
| **File Map (Complete)** | [file-map.md](file:///c:/Users/chait/Projects/Omnisuite/docs/file-map.md) | Every file with line counts and descriptions |
| **Navigation Reference** | [navigation.md](file:///c:/Users/chait/Projects/Omnisuite/docs/navigation.md) | All routes, arguments, and screen graph |
| **Dependencies Reference** | [dependencies.md](file:///c:/Users/chait/Projects/Omnisuite/docs/dependencies.md) | All third-party libraries with versions and purposes |
| **Engine Reference** | [engines.md](file:///c:/Users/chait/Projects/Omnisuite/docs/engines.md) | Core processing engine APIs and usage patterns |
| **Known Issues** | [ISSUES_AND_PROGRESS.md](file:///c:/Users/chait/Projects/Omnisuite/ISSUES_AND_PROGRESS.md) | Active bugs and fixes |
| **Design Document** | [KarnaOffice_Design_Doc.md](file:///c:/Users/chait/Projects/Omnisuite/KarnaOffice_Design_Doc.md) | Full product spec and feature registry |
