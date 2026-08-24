# OmniSuite Comprehensive Architecture, Viewer & UX Audit

**Date:** 2026-08-24  
**Auditor:** Senior Software Architect / Android Engineer  
**Scope:** Full codebase audit — Architecture, Navigation, State Management, Storage, Viewers, Document Processing, Image Tools, File Handling, Intents, UI/UX, Accessibility, Performance, Memory, Error Handling

---

## Executive Summary

OmniSuite is an offline Android document/utility suite (~25,000 LOC) built with Jetpack Compose + Material3 + Hilt. It supports PDF, DOCX, XLSX, PPTX, images, text, and archives. The codebase demonstrates competent feature implementation but suffers from **critical architectural debt**, **god-class ViewModels**, **broken viewer implementations**, **security vulnerabilities**, and **inconsistent state management** that collectively prevent production readiness.

**Verdict:** The app needs significant refactoring across all layers. The navigation architecture is fundamentally broken (33+ callback parameters). Multiple viewers have showstopping bugs. The sole DI module provides only a database. Security vulnerabilities exist in file handling.

---

## Critical Issues

### C1. God-Class ViewModel — PdfToolsViewModel (1276+ lines)
- **File:** `feature/pdf_tools/PdfToolsViewModel.kt`
- **Impact:** Merge, split, lock, decrypt, compress, flatten, rotate, extract, delete, convert (6+ formats), watermark, sign — all in ONE class. Impossible to test, review, or maintain. High risk of merge conflicts in team development.
- **Fix vs Rewrite:** **Rewrite.** Decompose into per-operation ViewModels or a single PdfToolsRepository with UseCases.

### C2. HomeScreen Has 33+ Callback Parameters
- **File:** `feature/home/HomeScreen.kt:49-88`
- **Impact:** Every tool navigation is a separate lambda parameter. Adding a new tool requires modifying HomeScreen, OmniNavGraph, and AllToolsScreen signatures. This is an anti-pattern that scales terribly.
- **Fix vs Rewrite:** **Rewrite.** Replace with a sealed class `NavigationEvent` and a single `onEvent: (NavigationEvent) -> Unit` callback.

### C3. Security: Path Traversal in ZIP Extraction
- **File:** `feature/viewer/ArchiveViewerScreen.kt:161-165`
- **Impact:** ZIP entry names like `../../etc/passwd` could write outside extraction directory. Classic Zip Slip vulnerability.
- **Fix:** Validate that `entry.canonicalPath` starts with extraction directory canonical path.

### C4. Security: SAF URI Used as File Path in PDF Text Extraction
- **File:** `feature/viewer/PdfViewerScreen.kt:156`
- **Impact:** `PDDocument.load(File(fileUri))` crashes when `fileUri` is a `content://` SAF URI. Text extraction from SAF-sourced PDFs is completely broken.
- **Fix:** Copy SAF URI to temp file via `UriCacheUtils.cacheUriToFile()` before passing to PDFBox.

### C5. HTTP/HTTPS Download Violates Offline-Only Spec
- **File:** `core/util/UriCacheUtils.kt:52-72`
- **Impact:** Despite being marketed as "100% offline," the app downloads HTTP/HTTPS URIs. This is a spec violation and potential privacy concern.
- **Fix:** Remove HTTP/HTTPS support. Only handle `content://` and `file://` schemes.

### C6. Double URI Encoding Mismatch Breaks Tool Launch Flow
- **File:** `ui/navigation/Screen.kt:54` vs `OmniNavGraph.kt:258`
- **Impact:** URIs are double-encoded (`Uri.encode(Uri.encode(it))`) but only single-decoded. When navigating from ImageViewer to ImageTools, complex URIs may arrive malformed, breaking the "Edit in Image Lab" flow.
- **Fix:** Use single encoding or pass URIs via a shared ViewModel/state holder instead of route arguments.

---

## High Priority Issues

### H1. Print Layout Pagination is Fundamentally Broken (DOCX)
- **File:** `feature/viewer/DocxViewerScreen.kt:478-492`
- **Root Cause:** `PAGE_LINE_BUDGET = 55` hardcoded. `estimateLines()` uses character count / 65 with no font-metrics awareness. Images cost 10 lines regardless of actual size. Tables sum cell content but ignore row heights.
- **Impact:** Page boundaries are arbitrary and incorrect. Content overflows or clips. Multi-page rendering is unreliable.
- **Fix vs Rewrite:** **Rewrite.** Use an actual text layout engine (`StaticLayout` or `Paragraph` from `androidx.compose.ui.text`) to measure real text bounds and compute page breaks based on actual measured heights.

### H2. Spreadsheet Charts Show Mock Data Only
- **File:** `feature/viewer/XlsxViewerScreen.kt:1732-1767`, `XlsxViewerViewModel.kt:454-531`
- **Root Cause:** `extractCharts()` extracts chart metadata but series data is always empty. Charts are rendered with hardcoded mock data.
- **Impact:** Charts in XLSX files are effectively non-functional.
- **Fix:** Parse chart data from POI's `XSSFChart` / `ChartDataSource` APIs and bind to chart composable.

### H3. Full Workbook Re-parse on Every Structural Edit (XLSX)
- **File:** `feature/viewer/XlsxViewerViewModel.kt:1031`
- **Root Cause:** `refreshState()` calls `parseWorkbook(wb)` for every insert/delete row/col operation.
- **Impact:** For a 10,000-row sheet, each edit triggers a full re-parse — extremely slow, causes ANR.
- **Fix:** Implement incremental cell updates. Modify only the affected cell/row/col in the in-memory model without re-reading from POI.

### H4. XLSX Sort is String-Based, Not Numeric
- **File:** `feature/viewer/XlsxViewerViewModel.kt:1007`
- **Root Cause:** `compareBy { ... "\uffff" + it.second }` sorts all values lexicographically.
- **Impact:** "10" sorts before "2". Numbers, dates, and mixed content sort incorrectly.
- **Fix:** Detect cell type (numeric, date, string) and use appropriate comparison logic.

### H5. PPTX Editing Uses Fragile Reflection
- **File:** `feature/viewer/PptxViewerViewModel.kt:91-273`
- **Root Cause:** Almost all PPTX editing uses reflection (`getXmlObjectReflection`, `getShapeAnchor`, etc.) to access POI internals.
- **Impact:** Breaks on different POI versions, Android JVM implementations, or ProGuard optimization. Unreliable.
- **Fix vs Rewrite:** **Rewrite.** Use POI's public API exclusively (`XSSFSlide`, `XSSFTextBox`, etc.) or switch to direct OpenXML manipulation.

### H6. No UseCase/Domain Layer
- **Root Cause:** Business logic is embedded directly in ViewModels. No abstraction between data and presentation.
- **Impact:** ViewModels are massive, untestable in isolation, and mix concerns.
- **Fix:** Introduce UseCase classes for each discrete operation. Inject into ViewModels.

### H7. Static Object Singletons Instead of Hilt DI
- **Files:** `UriCacheUtils.kt`, `FileOutputManager.kt`, `ThemePreferences.kt`, `OfficeConverter.kt`
- **Root Cause:** Core utilities use `object` singleton pattern instead of Hilt injection.
- **Impact:** Hidden dependencies, impossible to mock in tests, tight coupling.
- **Fix:** Convert to `@Singleton` classes with `@Inject` constructor. Provide via Hilt modules.

### H8. ThemePreferences Global Mutable State
- **File:** `core/util/ThemePreferences.kt`
- **Root Cause:** `var currentThemeState = mutableStateOf(...)` — global mutable Compose state.
- **Impact:** Not reactive across configuration changes. Violates unidirectional data flow.
- **Fix:** Move to a Hilt-managed repository backed by DataStore. Expose `Flow<ThemeState>`.

---

## Medium Priority Issues

### M1. No Large File Support in TXT Viewer
- **File:** `feature/viewer/TxtViewerViewModel.kt:65`
- **Impact:** `file.bufferedReader().use { it.readText() }` loads entire file into RAM. A 100MB text file will OOM.
- **Fix:** Implement pagination or use a `Flow<String>` that reads chunks lazily.

### M2. Search Overlapping Matches (Multiple Viewers)
- **Files:** `feature/viewer/TxtViewerScreen.kt:579`, `core/engine/DocumentSearchEngine.kt:44`
- **Root Cause:** `text.indexOf(query, pos + 1)` increments by 1 instead of query length.
- **Impact:** Searching "aa" in "aaa" finds 2 matches instead of 1.
- **Fix:** Change to `pos + query.length`.

### M3. Annotation Colors Hardcoded to 4 Values (PDF)
- **File:** `feature/viewer/PdfViewerScreen.kt:597-601`
- **Impact:** Custom colors from the picker are lost on save. Only red, blue, green, yellow persist.
- **Fix:** Store full ARGB color value, not an enum.

### M4. No EXIF Orientation in Image Viewer
- **File:** `feature/viewer/ImageViewerScreen.kt:782-851`
- **Impact:** Photos taken in portrait mode display incorrectly rotated.
- **Fix:** Read EXIF orientation via `ExifInterface` and apply rotation to displayed bitmap.

### M5. Image Edit Operations Decode Full-Resolution Bitmap
- **File:** `feature/viewer/ImageViewerScreen.kt:782-788`
- **Impact:** `BitmapFactory.decodeStream(stream)` without `inSampleSize`. A 4000x3000 image uses ~48MB RAM.
- **Fix:** Use `calculateInSampleSize` (as `ImageToolsViewModel` does) targeting a max dimension.

### M6. XLSX CSV Detection Heuristic Misclassifies Files
- **File:** `feature/viewer/XlsxViewerViewModel.kt:127`
- **Root Cause:** `isCsv = file.name.endsWith(".csv") || (!file.name.endsWith(".xls") && !isZipFile(file))` — any file without `.xls` extension that isn't a ZIP is classified as CSV.
- **Fix:** Check MIME type or use content sniffing.

### M7. PPTX Search Re-parses Entire Presentation
- **File:** `feature/viewer/PptxViewerViewModel.kt:251-291`
- **Impact:** `searchPptx` loads the entire presentation again for search — duplicates parsing already done for display.
- **Fix:** Search the already-parsed `PptxPresentation` in-memory model.

### M8. No PDF Page Recycling in LazyColumn
- **File:** `feature/viewer/PdfViewerScreen.kt`
- **Root Cause:** `items(currentPage.pageCount)` without keys. All page composables stay in memory.
- **Fix:** Use `items(count = pageCount, key = { it })`.

### M9. ImageToolsCallback Flow Broken (Double Encoding)
- **File:** `ui/navigation/Screen.kt:54`
- **Root Cause:** `Uri.encode(Uri.encode(it))` double-encoding breaks for complex content URIs.
- **Fix:** Single encode or use shared state.

### M10. ArchiveViewer Loads Entire File into Memory
- **File:** `feature/viewer/ArchiveViewerScreen.kt:158`
- **Root Cause:** `outStream.toByteArray()` loads entire extracted file into memory.
- **Fix:** Stream directly to output file without intermediate byte array.

---

## Tool Organization & Product Structure Audit

### Current Categories
```
📋 PDF (21 items) | 📝 Word (5 items) | 📊 Excel (4 items) | 🖼️ Slides (2 items) | 🖼 Image (6 items) | 📦 Archive (7 items)
```

### Problems
1. **PDF category is overloaded** (21 items) — mixes PDF-to-PDF operations, conversions TO PDF, conversions FROM PDF, and utilities
2. **Image Lab conflates 5 distinct tools** under one entry (compress, stitch, extract, ID card, watermark)
3. **Related tools are separated** — "Doc to PDF" is under PDF tab, but "Word Viewer" is under Word tab
4. **Discoverability is poor** — no search within tools, no favorites, no recent tools

### Recommended Restructuring

| Category | Tools |
|----------|-------|
| **📄 Viewers** | PDF, Word, Excel, Slides, Images, Text, Archives |
| **🔄 Convert** | PDF↔Word, PDF↔Excel, PDF↔Slides, PDF↔Images, HTML→PDF, Markdown→PDF, CSV↔Excel |
| **🔧 PDF Tools** | Merge, Split, Rotate, Extract, Delete, Compress, Lock, Decrypt, Flatten, Watermark, Sign |
| **🖼 Image Lab** | Compress, Resize, Crop, Rotate, Filters, Stitch, Watermark, ID Card |
| **📦 Archives** | ZIP Create, ZIP Extract, TAR Tools, Batch Operations |
| **🔍 Utilities** | QR Generator, QR Scanner, OCR, Document Scanner |

**Key Change:** Add a dedicated **Viewers** category as the primary entry point — this is what most users need first.

---

## UI/UX Review

### Strengths
- Consistent Material3 design language
- Good use of semantic colors per file type
- Bottom navigation with 5 clear tabs
- Recent files carousel on home

### Issues

| Issue | Location | Impact |
|-------|----------|--------|
| Search bar on Home doesn't filter anything | `HomeScreen.kt:244-262` | Users expect search to filter files/tools — it's non-functional |
| No empty states on any screen | All feature screens | Users see blank screens when no data |
| No loading indicators for file operations | Most tools | Users can't tell if app is working or stuck |
| No error state UIs | All feature screens | Errors only show as Toast — easily missed |
| No confirmation dialogs for destructive ops | Delete pages, remove files | Accidental data loss |
| Emoji in tool labels is inconsistent | `AllToolsScreen.kt:69` | Mix of emoji and text reduces professional feel |
| No tooltips on icon buttons | All screens | Unclear what actions do |
| Touch targets below 48dp on many controls | Various | Accessibility violation |
| No accessibility content descriptions on many icons | Various | Screen reader users can't navigate |
| Hardcoded strings throughout | All files | No localization readiness |
| No haptic feedback | All screens | Missed tactile UX opportunity |

---

## Performance & Stability Audit

| Risk | Details | File |
|------|---------|------|
| **ANR Risk** | Full workbook re-parse on every XLSX edit | `XlsxViewerViewModel.kt:1031` |
| **OOM Risk** | Full-res bitmap decode without sampling in image editor | `ImageViewerScreen.kt:782` |
| **OOM Risk** | Entire text file loaded into memory | `TxtViewerViewModel.kt:65` |
| **OOM Risk** | All PDF page composables in memory | `PdfViewerScreen.kt` |
| **OOM Risk** | ZIP entries loaded into `ByteArrayOutputStream` | `ArchiveViewerScreen.kt:158` |
| **Memory Leak** | `originalPreviewBitmap` not nulled in `onCleared()` | `ImageToolsViewModel.kt:691-695` |
| **Memory Leak** | `ZoomableBox.kt` is unused but never removed | `core/util/ZoomableBox.kt` |
| **Render Bottleneck** | PDF aspect ratios computed by opening every page | `PdfViewerViewModel.kt:118-127` |
| **Thread Blocking** | `e.printStackTrace()` on main thread in some paths | Multiple files |

---

## Architecture Review

### Current State
```
UI (Compose) → ViewModel (God classes) → Static Objects / Direct Engine Calls
```

### Recommended State
```
UI (Compose) → ViewModel (thin) → UseCase → Repository → Data Source
                                     ↕
                              Domain Models
```

### Key Changes Needed

1. **Introduce Domain Layer**
   - Create domain models separate from data models
   - Define repository interfaces in domain layer

2. **Extract UseCases**
   - `MergePdfsUseCase`, `SplitPdfUseCase`, `CompressImageUseCase`, etc.
   - Each UseClass is single-responsibility, testable, injectable

3. **Convert Static Objects to Hilt-Managed Dependencies**
   - `UriCacheUtils` → `@Singleton class UriCache`
   - `FileOutputManager` → `@Singleton class FileOutputRepository`
   - `ThemePreferences` → `@Singleton class ThemeRepository` (backed by DataStore)
   - `OfficeConverter` → `@Singleton class OfficeConverter`

4. **Break Down PdfToolsViewModel**
   - Option A: One ViewModel per operation (15+ small ViewModels)
   - Option B: Single PdfToolsViewModel backed by a PdfToolsRepository with injected UseCases
   - **Recommendation:** Option B for maintainability

5. **Fix Navigation Architecture**
   - Replace 33+ lambda params with sealed class events
   - Use `SavedStateHandle` for complex argument passing
   - Consider a single-activity, single-ViewModel-per-screen approach

6. **Standardize Error Handling**
   - Create sealed `AppError` type hierarchy
   - Map exceptions to user-friendly messages consistently
   - Use `Result<T>` type instead of try/catch with Toast

---

## Implementation Roadmap

### Phase A: Critical Fixes (Week 1-2)
| Task | Complexity |
|------|-----------|
| C3: Fix ZIP path traversal vulnerability | Low |
| C4: Fix SAF URI handling in PDF text extraction | Low |
| C5: Remove HTTP/HTTPS download support | Low |
| C6: Fix double URI encoding mismatch | Low |
| H4: Fix XLSX sort to be type-aware | Low |
| M2: Fix search overlapping matches | Low |
| M3: Fix PDF annotation color persistence | Low |
| M6: Fix CSV detection heuristic | Low |

### Phase B: Viewer Stabilization (Week 3-5)
| Task | Complexity |
|------|-----------|
| H1: Rewrite DOCX print layout pagination | High |
| H2: Implement real XLSX chart data binding | High |
| H3: Incremental XLSX cell updates (no full re-parse) | Medium |
| H5: Rewrite PPTX editing without reflection | High |
| M1: Large file support in TXT viewer (streaming) | Medium |
| M4: Add EXIF orientation to image viewer | Low |
| M5: Downsampling for image edit operations | Medium |
| M7: PPTX search uses in-memory model | Low |
| M8: PDF page recycling with keys | Low |
| M10: ZIP extraction streaming | Low |

### Phase C: UX Improvements (Week 6-7)
| Task | Complexity |
|------|-----------|
| Implement functional search on Home screen | Medium |
| Add empty/loading/error states to all screens | Medium |
| Add confirmation dialogs for destructive ops | Low |
| Standardize touch targets to 48dp minimum | Medium |
| Add content descriptions for accessibility | Medium |
| Extract all hardcoded strings to strings.xml | Medium |
| Redesign tool categories (Viewers-first) | Medium |

### Phase D: Architecture Cleanup (Week 8-10)
| Task | Complexity |
|------|-----------|
| C1: Decompose PdfToolsViewModel | High |
| C2: Refactor HomeScreen navigation (sealed events) | Medium |
| H6: Introduce UseCase layer | High |
| H7: Convert static objects to Hilt singletons | Medium |
| H8: Replace ThemePreferences with DataStore | Medium |
| Introduce domain models and repository interfaces | High |
| Standardize error handling (sealed AppError) | Medium |
| Add unit tests for UseCases | Medium |

### Phase E: Performance Optimization (Week 11-12)
| Task | Complexity |
|------|-----------|
| PDF aspect ratio optimization (no full page open) | Low |
| Memory leak fixes (ImageToolsViewModel, etc.) | Low |
| Large file handling across all viewers | Medium |
| Background processing optimization | Medium |
| Startup performance (lazy initialization) | Medium |

---

## Final Verdict

### 1. What components should be **fixed** (not rewritten)?
- PDF annotation color persistence (M3)
- Search overlapping matches (M2)
- ZIP path traversal (C3)
- SAF URI handling (C4)
- HTTP/HTTPS removal (C5)
- Double URI encoding (C6)
- XLSX sort logic (H4)
- CSV detection (M6)
- EXIF orientation (M4)
- Image edit downsampling (M5)
- PDF page recycling (M8)
- ZIP streaming extraction (M10)
- Memory leak fixes

### 2. What components should be **rewritten**?
- **DOCX Print Layout** — the pagination algorithm is fundamentally broken and cannot be patched
- **PPTX Editing** — reflection-based approach is too fragile for production
- **XLSX Chart Rendering** — mock data approach needs complete chart data binding rewrite
- **PdfToolsViewModel** — god class must be decomposed
- **HomeScreen Navigation** — 33+ callback params is unmaintainable
- **Navigation Architecture** — route-based URI passing is fragile

### 3. What architectural changes are recommended?
- Introduce Domain layer with UseCases
- Convert all static objects to Hilt-managed `@Singleton` classes
- Replace `ThemePreferences` global state with DataStore-backed repository
- Create repository interfaces in domain layer
- Standardize error handling with sealed types
- Add proper dependency injection throughout

### 4. What UI restructuring is recommended?
- Add **Viewers** as a primary tool category
- Implement functional search
- Add empty/loading/error states everywhere
- Standardize accessibility (48dp touch targets, content descriptions)
- Extract all strings for localization
- Add confirmation dialogs for destructive operations

### 5. What blockers prevent production readiness?
1. **God-class ViewModel** (1276 lines, untestable)
2. **Broken DOCX print layout** (core feature doesn't work)
3. **Security vulnerability** (ZIP path traversal)
4. **Navigation architecture** (33+ callbacks, doesn't scale)
5. **No error handling strategy** (errors silently swallowed or printed to stack trace)
6. **Incomplete XLSX viewer** (no charts, broken sort, full re-parse on edit)
7. **No large file support** (OOM risks across multiple viewers)

### 6. What is required to reach production quality?
- **3-4 weeks** of focused refactoring for Phases A + B
- **2-3 weeks** for architecture cleanup (Phase D)
- **1-2 weeks** for UX improvements (Phase C)
- **1 week** for performance optimization (Phase E)
- **Total estimate: 7-10 weeks** of dedicated engineering effort

The codebase has a solid foundation (good package structure, correct library choices, working core features) but needs disciplined architectural refactoring before it can be considered production-ready. The priority should be **security fixes → viewer stabilization → architecture cleanup → UX polish**.

---

## Appendix: File Reference Index

| File | Lines | Role | Status |
|------|-------|------|--------|
| `core/OmniApplication.kt` | 17 | Application entry | ✅ Clean |
| `core/MainActivity.kt` | 87 | Single Activity | ✅ Clean |
| `core/engine/DocumentSearchEngine.kt` | 321 | Full-text search | ⚠️ Bug: overlapping matches |
| `core/engine/image/ImageUtils.kt` | 168 | Image utilities | ✅ Clean |
| `core/engine/image/ImageLabExtensions.kt` | 166 | Advanced image ops | ✅ Clean |
| `core/engine/document/OfficeConverter.kt` | 889 | Office→PDF | ⚠️ Static object |
| `core/engine/document/ReverseOfficeConverter.kt` | 172 | PDF→Office | ✅ Hilt-managed |
| `core/engine/utility/QrCodeGenerator.kt` | - | QR generation | ✅ Clean |
| `core/model/RecentFile.kt` | 27 | Room entity | ✅ Clean |
| `core/repository/OmniDatabase.kt` | 22 | Room DB | ✅ Clean |
| `core/repository/RecentFileDao.kt` | 53 | DAO | ✅ Clean |
| `core/repository/RecentFileRepository.kt` | 78 | Repository | ✅ Clean |
| `core/util/FileOutputManager.kt` | 40 | File output | ⚠️ Static object |
| `core/util/ThemePreferences.kt` | 142 | Theme prefs | ⚠️ Global mutable state |
| `core/util/UriCacheUtils.kt` | 166 | URI caching | ⚠️ Static + HTTP support |
| `core/util/ZoomableBox.kt` | 97 | Zoom component | ❌ Unused dead code |
| `di/DatabaseModule.kt` | 40 | Hilt module | ⚠️ Too minimal |
| `ui/navigation/Screen.kt` | 280 | Route definitions | ⚠️ Double encoding |
| `ui/navigation/OmniNavGraph.kt` | 746 | Nav graph | ⚠️ Verbose |
| `ui/theme/Color.kt` | - | Design tokens | ✅ Clean |
| `ui/theme/Theme.kt` | - | Theme composable | ✅ Clean |
| `ui/theme/Type.kt` | - | Typography | ✅ Clean |
| `ui/component/OmniBottomNav.kt` | - | Bottom nav | ✅ Clean |
| `ui/component/OmniTopBar.kt` | - | Top bar | ✅ Clean |
| `ui/component/RecentFileChip.kt` | - | Recent files | ✅ Clean |
| `ui/component/ToolListRow.kt` | - | Tool row | ✅ Clean |
| `ui/component/ToolkitCard.kt` | - | Tool card | ✅ Clean |
| `ui/component/OperationResultBottomSheet.kt` | - | Result sheet | ✅ Clean |
| `ui/component/SettingToggleRow.kt` | - | Settings row | ✅ Clean |
| `ui/component/SectionHeader.kt` | - | Section header | ✅ Clean |
| `feature/home/HomeScreen.kt` | 603 | Home shell | ❌ 33+ callbacks |
| `feature/home/HomeScreenViewModel.kt` | 74 | Home state | ✅ Clean |
| `feature/home/FilesScreen.kt` | - | File explorer | ✅ Clean |
| `feature/home/FilesViewModel.kt` | - | File VM | ✅ Clean |
| `feature/home/FileBrowserScreen.kt` | - | File browser | ✅ Clean |
| `feature/settings/SettingsScreen.kt` | - | Settings | ✅ Clean |
| `feature/settings/SettingsViewModel.kt` | 31 | Settings VM | ✅ Clean |
| `feature/history/HistoryScreen.kt` | - | History | ✅ Clean |
| `feature/history/HistoryViewModel.kt` | 129 | History VM | ✅ Clean |
| `feature/pdf_tools/PdfToolsViewModel.kt` | 1276+ | All PDF ops | ❌ God class |
| `feature/pdf_tools/PdfMergeScreen.kt` | - | Merge | ✅ Clean |
| `feature/pdf_tools/PdfSplitScreen.kt` | - | Split | ✅ Clean |
| `feature/pdf_tools/PdfLockScreen.kt` | - | Lock | ✅ Clean |
| `feature/pdf_tools/PdfDecryptScreen.kt` | - | Decrypt | ✅ Clean |
| `feature/pdf_tools/PdfRotateScreen.kt` | - | Rotate | ✅ Clean |
| `feature/pdf_tools/PdfExtractScreen.kt` | - | Extract | ✅ Clean |
| `feature/pdf_tools/PdfDeleteScreen.kt` | - | Delete | ✅ Clean |
| `feature/pdf_tools/PdfCompressScreen.kt` | - | Compress | ✅ Clean |
| `feature/pdf_tools/PdfFlattenScreen.kt` | - | Flatten | ✅ Clean |
| `feature/pdf_tools/PdfToWordScreen.kt` | - | PDF→Word | ✅ Clean |
| `feature/pdf_tools/PdfToPptScreen.kt` | - | PDF→PPT | ✅ Clean |
| `feature/pdf_tools/PdfToExcelScreen.kt` | - | PDF→Excel | ✅ Clean |
| `feature/pdf_tools/PdfToImagesScreen.kt` | - | PDF→Images | ✅ Clean |
| `feature/pdf_tools/PdfFormFillerScreen.kt` | - | Form fill | ✅ Clean |
| `feature/pdf_tools/DocToPdfScreen.kt` | - | Doc→PDF | ✅ Clean |
| `feature/pdf_tools/PptToPdfScreen.kt` | - | Ppt→PDF | ✅ Clean |
| `feature/pdf_tools/XlsToPdfScreen.kt` | - | Xls→PDF | ✅ Clean |
| `feature/pdf_tools/ScanToPdfScreen.kt` | - | Scan→PDF | ✅ Clean |
| `feature/pdf_tools/ImagesToPdfScreen.kt` | - | Images→PDF | ✅ Clean |
| `feature/pdf_tools/WebToPdfScreen.kt` | - | Web→PDF | ✅ Clean |
| `feature/pdf_tools/HtmlToPdfScreen.kt` | - | HTML→PDF | ✅ Clean |
| `feature/pdf_tools/MarkdownToPdfScreen.kt` | - | MD→PDF | ✅ Clean |
| `feature/pdf_tools/CsvToXlsxScreen.kt` | - | CSV→XLSX | ✅ Clean |
| `feature/pdf_tools/XlsxToCsvScreen.kt` | - | XLSX→CSV | ✅ Clean |
| `feature/pdf_tools/DocxToTxtScreen.kt` | - | DOCX→TXT | ✅ Clean |
| `feature/pdf_tools/PptxToTxtScreen.kt` | - | PPTX→TXT | ✅ Clean |
| `feature/pdf_tools/SignaturePadScreen.kt` | - | Signature | ✅ Clean |
| `feature/pdf_tools/SignatureViewModel.kt` | - | Signature VM | ✅ Clean |
| `feature/pdf_tools/WatermarkScreen.kt` | - | Watermark | ✅ Clean |
| `feature/pdf_tools/WatermarkViewModel.kt` | - | Watermark VM | ✅ Clean |
| `feature/tools/AllToolsScreen.kt` | 358 | Tools dashboard | ⚠️ Needs restructure |
| `feature/tools/ImageToolsScreen.kt` | 1025+ | Image editor | ✅ Clean |
| `feature/tools/ImageToolsViewModel.kt` | 696 | Image VM | ⚠️ Memory leak |
| `feature/tools/BatchToolsScreen.kt` | - | Batch ops | ✅ Clean |
| `feature/tools/BatchOperationsManager.kt` | 395 | Batch VM | ✅ Clean |
| `feature/tools/ZipMakerScreen.kt` | - | ZIP maker | ✅ Clean |
| `feature/tools/ZipMakerViewModel.kt` | 182 | ZIP VM | ✅ Clean |
| `feature/tools/TarToolsScreen.kt` | - | TAR tools | ✅ Clean |
| `feature/utility/QrGeneratorScreen.kt` | - | QR gen | ✅ Clean |
| `feature/utility/QrGeneratorViewModel.kt` | 37 | QR VM | ✅ Clean |
| `feature/utility/BarcodeScannerScreen.kt` | - | Barcode scan | ✅ Clean |
| `feature/utility/BarcodeScannerViewModel.kt` | 84 | Barcode VM | ✅ Clean |
| `feature/utility/OcrScreen.kt` | - | OCR | ✅ Clean |
| `feature/utility/OcrViewModel.kt` | 236 | OCR VM | ✅ Clean |
| `feature/utility/DocumentScannerWrapper.kt` | - | Doc scanner | ✅ Clean |
| `feature/utility/ScannerScreen.kt` | - | Scanner | ✅ Clean |
| `feature/utility/UtilityHubScreen.kt` | - | Utility hub | ✅ Clean |
| `feature/viewer/ViewerDispatcherScreen.kt` | 512 | MIME router | ⚠️ Minor bugs |
| `feature/viewer/PdfViewerScreen.kt` | 1455 | PDF viewer | ⚠️ SAF bug |
| `feature/viewer/PdfViewerViewModel.kt` | 484 | PDF VM | ⚠️ Inefficient |
| `feature/viewer/DocxViewerScreen.kt` | 1261 | DOCX viewer | ❌ Broken pagination |
| `feature/viewer/DocxViewerViewModel.kt` | 622 | DOCX VM | ⚠️ Half-point bug |
| `feature/viewer/XlsxViewerScreen.kt` | 2105 | XLSX viewer | ❌ Charts mock |
| `feature/viewer/XlsxViewerViewModel.kt` | 1247 | XLSX VM | ❌ Full re-parse |
| `feature/viewer/PptxViewerScreen.kt` | 1190 | PPTX viewer | ⚠️ Reflection |
| `feature/viewer/PptxViewerViewModel.kt` | 1033 | PPTX VM | ❌ Reflection |
| `feature/viewer/ImageViewerScreen.kt` | 1026 | Image viewer | ⚠️ No EXIF |
| `feature/viewer/ImageViewerViewModel.kt` | 22 | Image VM | ⚠️ Near-empty |
| `feature/viewer/TxtViewerScreen.kt` | 593 | Text viewer | ⚠️ Search bug |
| `feature/viewer/TxtViewerViewModel.kt` | 135 | Text VM | ⚠️ No large file |
| `feature/viewer/ArchiveViewerScreen.kt` | 515 | Archive viewer | ❌ Path traversal |
