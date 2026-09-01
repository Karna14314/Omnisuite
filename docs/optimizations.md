# OmniSuite — Optimization & Hard Issues Reference

**Date:** 2026-09-01
**Purpose:** Documents complex architectural issues requiring manual engineering review and optimization opportunities.

---

## Fixed Issues

### ImageToolsViewModel Memory Leak (Resolved)
- **File:** `feature/tools/ImageToolsViewModel.kt`
- **Issue:** `originalPreviewBitmap` was not being recycled in `onCleared()`, causing memory leaks when editing/filtering images.
- **Fix:** Added `originalPreviewBitmap?.recycle()` and `originalPreviewBitmap = null` in `onCleared()`. Also added `previewJob?.cancel()` to prevent coroutine leaks.

---

## God-Class ViewModel Issues

### PdfToolsViewModel (1276+ lines)
- **File:** `feature/pdf_tools/PdfToolsViewModel.kt`
- **Impact:** Manages 15+ document operations (merge, split, lock, decrypt, compress, flatten, rotate, extract, delete, convert, watermark, sign, etc.) in a single class.
- **Risk:** Difficult to test, review, and maintain. High risk of merge conflicts.
- **Recommendation:** Decompose into per-operation ViewModels or extract processing logic into PdfToolsRepository with UseCases.
- **Priority:** Medium (current implementation works but scales poorly)

---

## Navigation Architecture

### HomeScreen Navigation Callback Explosion (Resolved)
- **File:** `feature/home/HomeScreen.kt`
- **Issue:** Originally had 33+ individual navigation lambda parameters.
- **Fix:** Replaced with sealed class `NavigationEvent` and single `onEvent: (NavigationEvent) -> Unit` callback with 64 event types.
- **Status:** Resolved.

---

## Document Viewer Issues

### DOCX Print Layout Pagination
- **File:** `feature/viewer/DocxViewerScreen.kt`
- **Issue:** Hardcoded character budget heuristics cause inaccurate page breaks in print layout mode.
- **Recommendation:** Integrate with Compose TextMeasurer or native StaticLayout for accurate text measurement.
- **Priority:** Medium (reflow mode works correctly)

### PPTX Internal Reflection Usage
- **File:** `feature/viewer/PptxViewerViewModel.kt`
- **Issue:** Uses reflection calls on POI internals for background color extraction and z-order parsing.
- **Risk:** Reflection may break with POI version updates.
- **Recommendation:** Migrate to public POI XMLBeans or OpenXML APIs where possible.
- **Priority:** Low (current implementation works)

### XLSX Full Workbook Re-parse & Mock Charts
- **Files:** `feature/viewer/XlsxViewerViewModel.kt`, `feature/viewer/XlsxViewerScreen.kt`
- **Issue:** Full POI workbook re-parsing on cell edit causes performance bottleneck. Chart data is mocked.
- **Recommendation:** Implement incremental state updates and dynamic XSSFChart series binding.
- **Priority:** Medium

---

## Performance Optimization Opportunities

### 1. Bitmap Memory Management
- **Scope:** All image processing screens
- **Current:** Basic bitmap recycling in some areas
- **Recommendation:** Implement a centralized BitmapPool for reuse across image operations

### 2. Large File Handling
- **Scope:** PDF, DOCX, XLSX viewers
- **Current:** Some viewers load entire files into memory
- **Recommendation:** Implement streaming/pagination for files larger than 50MB

### 3. Coroutine Scope Management
- **Scope:** All ViewModels
- **Current:** Most ViewModels use viewModelScope correctly
- **Recommendation:** Ensure all long-running operations use Dispatchers.IO explicitly

---

## UI/UX Optimization Opportunities

### 1. Tool Organization
- **Current:** 86+ tools across 6 tabs in a flat list
- **Recommendation:** Implement "Most Used" section, favorites, and search functionality
- **Priority:** High (improves discoverability)

### 2. Empty States
- **Current:** Some screens lack proper empty states
- **Recommendation:** Add EmptyStateMessage component (already in CommonStates.kt) to all applicable screens
- **Priority:** Medium

### 3. Loading Indicators
- **Current:** Inconsistent loading indicators across screens
- **Recommendation:** Standardize using LoadingIndicator component from CommonStates.kt
- **Priority:** Medium

### 4. Error Handling
- **Current:** Errors shown as Toast in some screens
- **Recommendation:** Add ErrorStateMessage component with retry actions
- **Priority:** Medium

---

## Accessibility Improvements

### 1. Content Descriptions
- **Scope:** All icon buttons
- **Recommendation:** Add content descriptions to all IconButton components
- **Priority:** Medium

### 2. Touch Target Sizes
- **Scope:** All interactive elements
- **Recommendation:** Ensure minimum 48dp touch targets
- **Priority:** Low

### 3. Text Contrast
- **Scope:** All text elements
- **Recommendation:** Verify WCAG AA contrast ratios
- **Priority:** Low

---

## Testing Recommendations

### 1. Unit Tests
- **Current:** Limited unit test coverage
- **Recommendation:** Add tests for:
  - PdfToolsRepository operations
  - EncodingDetector algorithms
  - SyntaxHighlighter rules
  - UtilityToolsRepository conversions

### 2. Integration Tests
- **Current:** No integration tests
- **Recommendation:** Add tests for:
  - Navigation flow
  - File conversion pipelines
  - Viewer rendering

### 3. Performance Tests
- **Current:** No performance tests
- **Recommendation:** Add benchmarks for:
  - Large PDF rendering
  - Image processing pipelines
  - Memory usage under load

---

## Build Optimization

### 1. Build Speed
- **Current:** Standard Gradle build
- **Recommendation:** Enable build cache, configure parallel execution

### 2. APK Size
- **Current:** ~25-30MB estimated
- **Recommendation:** Enable R8 full mode, remove unused resources

---

## Summary

| Category | Issues | Resolved | Remaining |
|----------|--------|----------|-----------|
| Memory Leaks | 1 | 1 | 0 |
| God-Class ViewModels | 1 | 0 | 1 |
| Navigation | 1 | 1 | 0 |
| Document Viewers | 3 | 0 | 3 |
| Performance | 3 | 0 | 3 |
| UI/UX | 4 | 0 | 4 |
| Accessibility | 3 | 0 | 3 |
| Testing | 3 | 0 | 3 |
| **Total** | **19** | **3** | **16** |
