# OmniSuite Architectural & Complex Issues Optimization Guide

**Date:** 2026-08-24
**Scope:** Hard / Architectural Issues & Technical Recommendations for Manual Review

This document details high-complexity, architectural issues identified during the OmniSuite end-to-end repository audit. These issues require architectural refactoring or core engine redesign before long-term production deployment.

---

## 1. God-Class ViewModels (PdfToolsViewModel)

### Location
- `app/src/main/java/com/karnadigital/omnisuite/feature/pdf_tools/PdfToolsViewModel.kt` (lines 1–1276+)

### Root Cause
`PdfToolsViewModel` handles 15+ distinct document transformations in a single class: PDF merge, split, lock, decrypt, compress, flatten, rotate, page extract, delete, transcode (DOC/XLS/PPT to PDF and vice versa), watermark stamping, and digital signature attachment.

### App Impact
- Extreme difficulty writing targeted unit tests.
- High risk of unintended side-effects and merge conflicts in multi-developer environments.
- High memory pressure during simultaneous operation state transitions.

### Proposed Architecture Solution
- **Decompose into UseCases & Targeted ViewModels**:
  Extract standalone UseCase classes (`MergePdfUseCase`, `SplitPdfUseCase`, `WatermarkPdfUseCase`) implementing a common `CoroutinesUseCase<Params, Result>` pattern.
- Refactor UI components to bind to specialized ViewModels (e.g. `PdfMergeViewModel`, `PdfSecurityViewModel`).

---

## 2. HomeScreen Callback Explosion (33+ Navigation Lambdas)

### Location
- `app/src/main/java/com/karnadigital/omnisuite/feature/home/HomeScreen.kt` (lines 49–88)
- `app/src/main/java/com/karnadigital/omnisuite/ui/navigation/OmniNavGraph.kt`

### Root Cause
Every tool action on the Home dashboard is passed as an explicit function callback parameter in `HomeScreen(...)`. Adding or modifying any tool route requires updating signatures across `HomeScreen`, `AllToolsScreen`, and `OmniNavGraph`.

### App Impact
- Violates Open-Closed Principle and Compose parameter best practices.
- Causes excessive recomposition surface area.
- Makes navigation flow brittle and verbose.

### Proposed Architecture Solution
- Unify dashboard navigation through a sealed event class `NavigationEvent` (e.g. `NavigationEvent.OpenTool(val tool: ViewerTool)`, `NavigationEvent.OpenViewer(val uri: Uri)`).
- Reduce `HomeScreen` parameters to a single callback: `onNavigationEvent: (NavigationEvent) -> Unit`.

---

## 3. DOCX Print Layout Pagination Engine Limitations

### Location
- `app/src/main/java/com/karnadigital/omnisuite/feature/viewer/DocxViewerScreen.kt` (lines 478–492)

### Root Cause
Page layout boundaries for Word document print previews are estimated using a fixed character budget (`PAGE_LINE_BUDGET = 55`) and character length heuristics rather than true font metrics or text measurement APIs.

### App Impact
- Page breaks in multi-page document viewports do not align with actual rendered line heights.
- Embedded tables and high-DPI images cause vertical clipping or premature page breaks.

### Proposed Architecture Solution
- Integrate native `StaticLayout` or Compose `TextMeasurer` / `Paragraph` layout engines to calculate exact vertical height bounds for styled text spans before rendering page breaks.
- Implement cached layout calculations based on viewport width and target DPI.

---

## 4. PPTX Editing Engine Dependence on POI Internal Reflection

### Location
- `app/src/main/java/com/karnadigital/omnisuite/feature/viewer/PptxViewerViewModel.kt` (lines 91–273)

### Root Cause
Slide shape manipulation and text editing in PPTX documents rely on reflection calls (`getXmlObjectReflection`, `getShapeAnchor`) to bypass package-private Apache POI constraints on Android.

### App Impact
- Fragile across runtime Java version updates, ProGuard/R8 optimizations, or POI library version upgrades.
- Incurred reflection overhead during batch slide updates.

### Proposed Architecture Solution
- Migrate slide shape modifications to POI's public XMLBeans/OpenXML APIs or lightweight native XML DOM stream builders for Android.

---

## 5. XLSX Full Workbook Re-parse Bottleneck & Chart Binding

### Location
- `app/src/main/java/com/karnadigital/omnisuite/feature/viewer/XlsxViewerViewModel.kt` (line 1031)
- `app/src/main/java/com/karnadigital/omnisuite/feature/viewer/XlsxViewerScreen.kt` (lines 1732–1767)

### Root Cause
1. **Workbook Re-parse**: Any structural change (inserting/deleting rows or updating cell values) triggers `refreshState()`, which re-parses the entire POI `Workbook` instance from scratch.
2. **Chart Data**: Spreadsheet charts pull metadata but bind series data from hardcoded mock structures instead of active XSSF chart data sources.

### App Impact
- Editing cell values on large spreadsheets (1,000+ rows) produces frame drops or Application Not Responding (ANR) warnings.
- Real chart visualization in complex Excel workbooks is inaccurate.

### Proposed Architecture Solution
- **Incremental Cell Updates**: Update in-memory grid state models incrementally upon user edit, deferring full POI Workbook serialization until save/export events.
- **Dynamic XSSFChart Binding**: Parse POI `XSSFChart` series data bounds directly into Compose charts.
