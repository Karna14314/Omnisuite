# OmniSuite Living Roadmap

**Last Updated:** 2026-08-31
**Status:** Audit Complete — Implementation Pending
**Goal:** Transform OmniSuite into a comprehensive offline-first document productivity suite

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Full Audit Findings](#full-audit-findings)
3. [Competitive Analysis](#competitive-analysis)
4. [Missing Features & Opportunities](#missing-features--opportunities)
5. [Implementation Roadmap](#implementation-roadmap)
6. [Progress Tracking](#progress-tracking)

---

## Executive Summary

OmniSuite is an offline Android document/utility suite (~25,000 LOC) built with Jetpack Compose + Material3 + Hilt. It supports PDF, DOCX, XLSX, PPTX, images, text, and archives. The app is stable and in closed testing with a solid foundation but needs significant expansion and modernization to compete with established players.

### Current State
- **Strengths:** Clean package structure, correct library choices, working core features, comprehensive existing documentation
- **Weaknesses:** God-class ViewModels, no domain layer, security vulnerabilities, no large file support, inconsistent error handling
- **Opportunity:** Massive gap between current capabilities and what competitors offer — especially in editing UX, text/code viewing, and PDF tool depth

### Strategic Direction
- **Offline-first:** No AI, cloud, subscriptions, backend, or server maintenance
- **Aggressive expansion:** Add missing features that competitors have
- **Modern UX:** Microsoft 365 mobile-inspired editing interface
- **Maintainability:** Refactor as we go, prefer improving existing tools

---

## Full Audit Findings

### Existing Viewers

| Viewer | File Types | Status | Issues |
|--------|-----------|--------|--------|
| PDF Viewer | `.pdf` | ✅ Functional | SAF URI bug, no page recycling, annotation color persistence |
| Word Viewer | `.docx`, `.doc` | ⚠️ Partial | Broken print layout pagination, hardcoded line budget |
| Excel Viewer | `.xlsx`, `.xls`, `.csv` | ⚠️ Partial | Mock chart data, full re-parse on edit, string-based sort |
| PowerPoint Viewer | `.pptx`, `.ppt` | ⚠️ Partial | Fragile reflection-based editing |
| Text Viewer/Editor | `.txt`, `.log`, `.json`, `.xml`, `.html`, `.py`, `.kt`, `.java`, `.css`, `.js`, `.md` | ✅ Functional | No syntax highlighting, no line numbers, no large file support |
| Image Viewer | `.png`, `.jpg`, `.jpeg`, `.webp`, `.bmp`, `.gif` | ✅ Functional | No EXIF orientation, no downsampling |
| Archive Viewer | `.zip` | ⚠️ Partial | Path traversal vulnerability, loads entire file to memory |
| Sequential Image Viewer | Multiple images | ✅ Functional | Basic implementation |

### Existing Editors

| Editor | Capabilities | Status | Issues |
|--------|-------------|--------|--------|
| TXT Editor | Full text editing, save, word count, search | ✅ Functional | No syntax highlighting, no replace, no go-to-line |
| DOCX Editor | Paragraph editing, run formatting, table editing | ⚠️ Partial | Broken pagination, limited formatting options |
| XLSX Editor | Cell editing, row/col insert/delete, sort, formula eval | ⚠️ Partial | No charts, slow re-parse, type-unaware sort |
| PPTX Editor | Slide text editing, shape manipulation | ⚠️ Partial | Reflection-based, fragile |
| PDF Annotator | Highlight, marker, underline, text notes, freehand draw, eraser | ✅ Functional | Limited annotation types |
| Image Editor | Crop, resize, compress, rotate, filters, watermark, stitch, ID card | ✅ Functional | No EXIF handling |
| Signature Pad | Digital signature capture, stamp to PDF | ✅ Functional | Basic implementation |

### PDF Tools (21+ Operations)

| Tool | Status | Quality |
|------|--------|---------|
| Merge PDFs | ✅ Implemented | Good |
| Split PDF | ✅ Implemented | Good |
| Encrypt/Lock PDF | ✅ Implemented | 128-bit, functional |
| Decrypt PDF | ✅ Implemented | Functional |
| Rotate Pages | ✅ Implemented | Functional |
| Extract Pages | ✅ Implemented | Functional |
| Delete Pages | ✅ Implemented | Functional |
| Compress PDF | ✅ Implemented | JPEG re-compression |
| Flatten PDF | ✅ Implemented | Functional |
| PDF → Word | ✅ Implemented | Text-only, no formatting |
| PDF → PPT | ✅ Implemented | Image-based |
| PDF → Excel | ✅ Implemented | Text-only |
| PDF → Images | ✅ Implemented | Functional |
| Fill Form | ✅ Implemented | Functional |
| Watermark | ✅ Implemented | Text overlay |
| Digital Sign | ✅ Implemented | Signature pad → stamp |
| Images → PDF | ✅ Implemented | Functional |
| Doc → PDF | ✅ Implemented | Good |
| PPT → PDF | ✅ Implemented | Good |
| XLS → PDF | ✅ Implemented | Good |
| Scan → PDF | ✅ Implemented | Camera capture |
| Web → PDF | ✅ Implemented | WebView rendering |
| HTML → PDF | ✅ Implemented | Text-only |
| Markdown → PDF | ✅ Implemented | Basic parser |

### Conversion Tools

| Conversion | Status | Quality |
|-----------|--------|---------|
| DOCX → PDF | ✅ Implemented | Good |
| XLSX → PDF | ✅ Implemented | Good |
| PPTX → PDF | ✅ Implemented | Good |
| PDF → DOCX | ✅ Implemented | Text-only |
| PDF → PPTX | ✅ Implemented | Image-based |
| PDF → XLSX | ✅ Implemented | Text-only |
| PDF → Images | ✅ Implemented | Good |
| Images → PDF | ✅ Implemented | Good |
| DOCX → TXT | ✅ Implemented | Good |
| PPTX → TXT | ✅ Implemented | Good |
| CSV → XLSX | ✅ Implemented | Good |
| XLSX → CSV | ✅ Implemented | Good |
| Markdown → PDF | ✅ Implemented | Basic |
| HTML → PDF | ✅ Implemented | Text-only |
| Web → PDF | ✅ Implemented | WebView |

### Image Tools

| Tool | Status |
|------|--------|
| Compress | ✅ Implemented |
| Resize | ✅ Implemented |
| Crop | ✅ Implemented |
| Rotate | ✅ Implemented |
| Format Convert | ✅ Implemented |
| Filters | ✅ Implemented |
| Stitch | ✅ Implemented |
| Watermark | ✅ Implemented |
| ID Card | ✅ Implemented |
| Extract Media | ✅ Implemented |

### Archive Tools

| Tool | Status |
|------|--------|
| ZIP Create | ✅ Implemented |
| ZIP Extract | ✅ Implemented |
| TAR Create | ✅ Implemented |
| TAR Extract | ✅ Implemented |
| Batch Operations | ✅ Implemented |

### Barcode/QR Tools

| Tool | Status |
|------|--------|
| QR Generator (11 types) | ✅ Implemented |
| Barcode Scanner | ✅ Implemented |
| Barcode Builder (EAN/UPC) | ✅ Implemented |

### Productivity Utilities

| Utility | Status |
|---------|--------|
| OCR | ✅ Implemented |
| Document Scanner | ✅ Implemented |
| File Browser | ✅ Implemented |
| History | ✅ Implemented |
| Settings | ✅ Implemented |
| Recent Files | ✅ Implemented |

---

## Competitive Analysis

### Feature Comparison Matrix

| Feature | OmniSuite | MS 365 | WPS | iLovePDF | SmallPDF | PDF24 | Xodo | Foxit |
|---------|-----------|--------|-----|----------|----------|-------|------|-------|
| **Editing UX** | | | | | | | | |
| Bottom toolbar | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Tabbed editing (Home/Insert/Format) | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Rich text formatting | ⚠️ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **PDF Tools** | | | | | | | | |
| Merge | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Split | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Compress | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Rotate | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Page numbering | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Watermark | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Password protect | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Remove password | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Fill forms | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ✅ | ✅ |
| Flatten forms | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ✅ | ✅ |
| Digital signature | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Signature library | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ |
| Export pages as images | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Reorder pages (drag) | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Extract images from PDF | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ |
| PDF page size adjustment | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Conversions** | | | | | | | | |
| Office → PDF | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| PDF → Office | ⚠️ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Image → PDF | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| PDF → Image | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| HTML → PDF | ⚠️ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ | ✅ |
| Markdown → PDF | ⚠️ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Text/Code Editing** | | | | | | | | |
| Syntax highlighting | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Line numbers | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Search & Replace | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Go to line | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Multiple encodings | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Large file handling | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Archive Tools** | | | | | | | | |
| ZIP create/extract | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| 7Z support | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| RAR support | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Password ZIP | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Preview before extract | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Selective extraction | ⚠️ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Barcode/QR** | | | | | | | | |
| QR generation | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Barcode scanning | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Multiple QR formats | ⚠️ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |

### Key Competitive Insights

1. **Microsoft 365 Mobile:** Gold standard for editing UX with bottom toolbar, tabbed interface (Home, Insert, Layout, Review, View). Rich formatting, undo/redo, collaborative features (skip — offline only).

2. **WPS Office:** Strong all-rounder with excellent PDF tools, supports 7Z/RAR, good editing UX, page numbering, and comprehensive format support.

3. **iLovePDF / SmallPDF:** Focused PDF tools with excellent merge, split, compress, rotate, page numbering, and watermark features. Simple, intuitive UX.

4. **PDF24:** Comprehensive PDF toolkit with page manipulation, compression, and creation tools. Strong offline capabilities.

5. **Xodo:** Excellent PDF reader with annotation, form filling, and digital signatures. Good page management.

6. **Foxit:** Enterprise-grade PDF with advanced security, form creation, and comprehensive editing tools.

---

## Missing Features & Opportunities

### Critical Gaps (Must-Have)

1. **Editor Modernization** — No bottom toolbar, no tabbed interface, poor discoverability
2. **PDF Page Numbering** — Standard feature in all PDF tools, completely missing
3. **PDF Reorder Pages (Drag & Drop)** — Expected in any PDF tool, missing
4. **Syntax Highlighting** — Text viewer supports code files but no highlighting
5. **Line Numbers** — Essential for code editing, missing
6. **Search & Replace** — Basic text operation, missing from text editor
7. **Go to Line** — Essential for code editing, missing
8. **Large File Handling** — Multiple viewers load entire files into memory
9. **PDF → Office Quality** — Current conversions are text-only, need formatting preservation

### High-Impact Additions

10. **Signature Library** — Save and reuse signatures
11. **PDF Extract Images** — Extract embedded images from PDF
12. **PDF Page Size Adjustment** — Change page dimensions (A4, Letter, etc.)
13. **Encoding Detection & Selection** — Support UTF-8, UTF-16, ASCII, etc.
14. **Word Wrap Toggle** — Essential for code viewing
15. **Password-protected ZIP** — Create and extract password-protected ZIPs
16. **7Z / TAR / GZIP Support** — Broader archive format support
17. **File Preview Before Extraction** — Preview archive contents
18. **Selective Extraction** — Extract specific files from archives
19. **Undo/Redo in Editors** — Standard editing feature, missing
20. **Auto-Save** — Settings toggle exists but not implemented

### Medium-Impact Improvements

21. **PDF Form Creation** — Create fillable forms (not just fill existing)
22. **PDF Header/Footer** — Add headers and footers to PDFs
23. **PDF Bates Numbering** — Legal document numbering
24. **PDF Redaction** — Permanently remove sensitive content
25. **PDF Compare** — Compare two PDFs and highlight differences
26. **PDF OCR** — Make scanned PDFs searchable
27. **Image → PDF with Layout Options** — Multiple images per page, margins
28. **CSV Viewer** — Dedicated CSV viewer with column sorting
29. **Markdown Preview** — Live preview while editing
30. **File Encryption** — AES encryption for arbitrary files

### UX/Quality Improvements

31. **Empty States** — Blank screens when no data
32. **Loading Indicators** — Users can't tell if operations are running
33. **Error State UIs** — Errors only shown as Toast
34. **Confirmation Dialogs** — Destructive operations lack confirmation
35. **Accessibility** — Missing content descriptions, touch targets below 48dp
36. **Tool Search** — Can't search within tools list
37. **Favorites/Bookmarks** — No way to bookmark files or tools
38. **Tooltips** — Unclear what icon actions do
39. **Haptic Feedback** — Missed tactile UX opportunity

---

## Implementation Roadmap

### Phase 1: Foundation & Critical Fixes
**Priority:** CRITICAL
**Timeline:** Immediate

#### 1.1 Security & Stability Fixes
- [ ] Fix ZIP path traversal vulnerability (ZipSecurity validation)
- [ ] Fix SAF URI handling in PDF text extraction
- [ ] Fix double URI encoding mismatch
- [ ] Fix XLSX sort to be type-aware
- [ ] Fix search overlapping matches (increment by query length)
- [ ] Fix CSV detection heuristic
- [ ] Fix PDF annotation color persistence
- [ ] Fix PDF page recycling in LazyColumn

#### 1.2 Architecture Improvements
- [ ] Decompose PdfToolsViewModel into per-operation ViewModels
- [ ] Convert static objects to Hilt-managed singletons
- [ ] Introduce domain layer with UseCases
- [ ] Standardize error handling (sealed AppError)
- [ ] Replace ThemePreferences with DataStore-backed repository

#### 1.3 Performance & Memory
- [ ] Implement large file support in TXT viewer (streaming/pagination)
- [ ] Add EXIF orientation to image viewer
- [ ] Add downsampling for image edit operations
- [ ] Stream ZIP extraction (no intermediate byte array)
- [ ] Fix memory leaks (ImageToolsViewModel bitmap cleanup)

---

### Phase 2: Editor Modernization
**Priority:** HIGH
**Timeline:** After Phase 1

#### 2.1 Bottom Toolbar System
- [ ] Design bottom editing toolbar with tab system
- [ ] Implement Home tab (clipboard, font, paragraph, styles)
- [ ] Implement Insert tab (images, tables, links, comments)
- [ ] Implement Format tab (bold, italic, underline, strikethrough, subscript, superscript)
- [ ] Implement Layout tab (margins, orientation, size, columns)
- [ ] Implement Review tab (spell check, word count, comments)
- [ ] Implement View tab (zoom, page width, gridlines, ruler)
- [ ] Implement Tools tab (export, protect, encrypt)

#### 2.2 DOCX Editor Enhancement
- [ ] Rewrite print layout pagination (use StaticLayout)
- [ ] Add rich text formatting toolbar
- [ ] Add paragraph alignment, indentation, spacing
- [ ] Add bullet and numbered lists
- [ ] Add table insertion and editing
- [ ] Add image insertion
- [ ] Add undo/redo
- [ ] Add find and replace

#### 2.3 XLSX Editor Enhancement
- [ ] Implement incremental cell updates (no full re-parse)
- [ ] Add real chart data binding
- [ ] Add type-aware sorting
- [ ] Add cell formatting (number, date, currency, percentage)
- [ ] Add conditional formatting
- [ ] Add formula bar
- [ ] Add sheet management (add, delete, rename, reorder)

#### 2.4 PPTX Editor Enhancement
- [ ] Rewrite editing without reflection (use POI public API)
- [ ] Add slide layout templates
- [ ] Add text formatting toolbar
- [ ] Add shape insertion and formatting
- [ ] Add image insertion
- [ ] Add slide transitions
- [ ] Add notes panel

---

### Phase 3: Text & Code Viewer Expansion
**Priority:** HIGH
**Timeline:** After Phase 2

#### 3.1 Universal Text/Code Editor
- [ ] Extend TXT viewer to handle all supported formats
- [ ] Add syntax highlighting for:
  - [ ] JSON (key-value highlighting)
  - [ ] XML/HTML (tag highlighting)
  - [ ] CSS (property-value highlighting)
  - [ ] JavaScript/TypeScript
  - [ ] Java/Kotlin
  - [ ] Python
  - [ ] C/C++/C#
  - [ ] PHP
  - [ ] SQL
  - [ ] YAML
  - [ ] Markdown
  - [ ] Gradle files
  - [ ] Config files (properties, ini, toml)
  - [ ] Log files (level-based coloring)

#### 3.2 Editor Features
- [ ] Line numbers with toggle
- [ ] Search with regex support
- [ ] Replace (single and all)
- [ ] Go to line
- [ ] Word wrap toggle
- [ ] Encoding detection and selection
- [ ] Large file handling (virtual scrolling)
- [ ] Auto-indent
- [ ] Bracket matching
- [ ] Code folding (basic)

#### 3.3 File Type Support
- [ ] Auto-detect file type by extension and content
- [ ] Associate syntax highlighter with file type
- [ ] Add file type indicator in UI
- [ ] Support for 50+ file extensions

---

### Phase 4: Conversion Expansion
**Priority:** HIGH
**Timeline:** After Phase 3

#### 4.1 Quality Improvements
- [ ] Improve DOCX → PDF (preserve formatting, images, tables)
- [ ] Improve XLSX → PDF (preserve charts, formatting)
- [ ] Improve PPTX → PDF (preserve animations, transitions)
- [ ] Improve PDF → DOCX (preserve layout, images)
- [ ] Improve PDF → PPTX (preserve slides, formatting)
- [ ] Improve HTML → PDF (render with WebView, preserve CSS)
- [ ] Improve Markdown → PDF (full GFM support)

#### 4.2 New Conversions
- [ ] TXT → PDF (with formatting options)
- [ ] CSV → PDF (table layout)
- [ ] PDF → TXT (structured text extraction)
- [ ] PDF → CSV (table extraction)
- [ ] Image → PDF (layout options: 1 per page, 2 per page, 4 per page)
- [ ] Multiple Images → PDF (batch with layout)

#### 4.3 Conversion Options
- [ ] Page range selection for all conversions
- [ ] Quality/compression settings
- [ ] Metadata preservation options
- [ ] Batch conversion support

---

### Phase 5: PDF Expansion
**Priority:** HIGH
**Timeline:** After Phase 4

#### 5.1 Page Management
- [ ] Reorder pages (drag & drop interface)
- [ ] Rotate pages (visual page selector)
- [ ] Extract pages (visual page selector)
- [ ] Delete pages (visual page selector)
- [ ] Insert pages (from another PDF)
- [ ] Duplicate pages
- [ ] Crop pages (margin adjustment)

#### 5.2 Content Tools
- [ ] Page numbering (position, format, font, start number)
- [ ] Header and footer (text, page numbers, date)
- [ ] Watermark (text and image, position, opacity, rotation)
- [ ] Bates numbering (legal documents)
- [ ] Background (color, image)
- [ ] Stamp (custom stamps)

#### 5.3 Security Tools
- [ ] Password protection (128-bit and 256-bit AES)
- [ ] Remove password
- [ ] Permission restrictions (print, copy, edit)
- [ ] Digital signature (certificate-based)
- [ ] Signature library (save, manage, reuse signatures)
- [ ] Draw signature (improved canvas)
- [ ] Image signature (import from file)

#### 5.4 Form Tools
- [ ] Fill existing forms
- [ ] Flatten forms
- [ ] Create fillable forms (text fields, checkboxes, radio buttons, dropdowns, signatures)
- [ ] Extract form data
- [ ] Import/export form data (FDF/XFDF)

#### 5.5 Export Tools
- [ ] Export pages as images (PNG, JPEG, TIFF)
- [ ] Extract embedded images
- [ ] Extract embedded fonts
- [ ] Extract text (structured)
- [ ] Export to Word (improved quality)
- [ ] Export to PowerPoint (improved quality)
- [ ] Export to Excel (table detection)

#### 5.6 Advanced Tools
- [ ] PDF/A conversion (archival standard)
- [ ] PDF comparison (visual diff)
- [ ] PDF redaction (permanent content removal)
- [ ] PDF optimization (linearization for web)
- [ ] PDF splitting by bookmarks
- [ ] PDF splitting by size

---

### Phase 6: ZIP & Archive Enhancement
**Priority:** MEDIUM
**Timeline:** After Phase 5

#### 6.1 ZIP Improvements
- [ ] Password-protected ZIP creation
- [ ] Password-protected ZIP extraction
- [ ] Compression level selection
- [ ] File preview before extraction
- [ ] Selective extraction (choose specific files)
- [ ] Add files to existing ZIP
- [ ] Delete files from ZIP
- [ ] Rename files in ZIP
- [ ] ZIP comment

#### 6.2 Additional Archive Formats
- [ ] 7Z creation and extraction
- [ ] GZIP creation and extraction
- [ ] TGZ (TAR + GZIP) creation and extraction
- [ ] RAR extraction (read-only)

#### 6.3 Archive Tools
- [ ] Archive integrity check
- [ ] Archive information (compression ratio, method)
- [ ] Split archive into volumes
- [ ] Batch compression (multiple files to multiple archives)

---

### Phase 7: Barcode & QR Enhancement
**Priority:** MEDIUM
**Timeline:** After Phase 6

#### 7.1 QR Generation
- [ ] Additional QR formats (Event, Crypto, MeCard)
- [ ] Custom QR colors
- [ ] QR logo/image overlay
- [ ] QR error correction level selection
- [ ] Batch QR generation
- [ ] QR history

#### 7.2 Barcode Generation
- [ ] Additional formats (Code 39, Code 93, ITF, Data Matrix, PDF417, Aztec)
- [ ] Barcode customization (size, color, text)
- [ ] Batch barcode generation

#### 7.3 Scanning
- [ ] Scan history
- [ ] Batch scanning
- [ ] Scan from image file
- [ ] Generate from scan history
- [ ] Export scan results

---

### Phase 8: Existing Tool Enhancement
**Priority:** MEDIUM
**Timeline:** After Phase 7

#### 8.1 Image Tools Enhancement
- [ ] Add EXIF data viewer/editor
- [ ] Add batch processing for all image operations
- [ ] Add more filters (blur, sharpen, vignette, HDR)
- [ ] Add drawing/annotation on images
- [ ] Add text overlay on images
- [ ] Add collage maker
- [ ] Add panorama stitching
- [ ] Add RAW file support

#### 8.2 OCR Enhancement
- [ ] Multi-language support
- [ ] Batch OCR
- [ ] OCR to searchable PDF
- [ ] OCR text editing before export
- [ ] Table detection in OCR

#### 8.3 Document Scanner Enhancement
- [ ] Batch scanning
- [ ] Auto-crop improvement
- [ ] Filter modes (B&W, grayscale, color)
- [ ] Scan to PDF directly
- [ ] Scan to OCR directly

#### 8.4 General UX Improvements
- [ ] Empty states for all screens
- [ ] Loading indicators for all operations
- [ ] Error state UIs
- [ ] Confirmation dialogs for destructive operations
- [ ] Tool search functionality
- [ ] Favorites/bookmarks system
- [ ] Tooltips on icon buttons
- [ ] Accessibility improvements (48dp touch targets, content descriptions)
- [ ] Haptic feedback
- [ ] Undo/redo in all editors
- [ ] Auto-save implementation

---

## Progress Tracking

### Completed
- [x] Full codebase audit
- [x] Competitive analysis
- [x] Roadmap creation
- [x] Verified existing fixes (ZipSecurity, TextSearchUtils, SpreadsheetUtils already correct)
- [x] Phase 3: Syntax highlighting engine (SyntaxHighlighter.kt — 30+ language support)
- [x] Phase 3: Encoding detection utility (EncodingDetector.kt — BOM detection, UTF-8/16/32, heuristics)
- [x] Phase 3: Universal code editor (TxtViewerScreen.kt — line numbers, search/replace, go-to-line, encoding picker, word wrap, themes)
- [x] Phase 5: PDF page numbering (PdfPageNumberScreen.kt + repository function)
- [x] Phase 5: PDF page reorder with drag-and-drop (PdfReorderScreen.kt + repository function)
- [x] Phase 5: PDF image extraction (PdfExtractImagesScreen.kt + repository function)
- [x] Navigation routes for all new screens

### In Progress
- [ ] Phase 2: Editor Modernization (bottom toolbar system)
- [ ] Phase 4: Conversion Expansion (quality improvements)

### Upcoming
- [ ] Phase 4: Conversion Expansion
- [ ] Phase 5: PDF Expansion
- [ ] Phase 6: ZIP & Archive Enhancement
- [ ] Phase 7: Barcode & QR Enhancement
- [ ] Phase 8: Existing Tool Enhancement

### Already Fixed (Verified)
- [x] ZipSecurity properly validates paths in ArchiveViewerScreen
- [x] TextSearchUtils uses correct non-overlapping search (pos + query.length)
- [x] SpreadsheetUtils has type-aware comparison and proper CSV detection
- [x] UriCacheUtils properly rejects network schemes
- [x] URI encoding is single (not double) in Screen.kt

---

## Decision Log

| Date | Decision | Rationale |
|------|----------|-----------|
| 2026-08-31 | Created living roadmap | Centralize all planning and track progress |
| 2026-08-31 | Prioritize offline-only features | User requirement: no AI, cloud, subscriptions, backend |
| 2026-08-31 | MS 365 mobile as UX inspiration | Industry standard for mobile editing UX |
| 2026-08-31 | Refactor as we go | Balance feature delivery with code quality |
| 2026-08-31 | Syntax highlighting via regex patterns | No external dependencies, fully offline |
| 2026-08-31 | Encoding detection via BOM + heuristics | Reliable detection without external libraries |
| 2026-08-31 | PDF page reorder via drag-and-drop grid | Intuitive UX, no external dependencies |

---

## Implementation Summary

### New Files Created

| File | Purpose | Lines |
|------|---------|-------|
| `core/engine/SyntaxHighlighter.kt` | Syntax highlighting for 30+ languages | ~900 |
| `core/engine/EncodingDetector.kt` | Encoding detection (BOM, UTF-8/16/32, heuristics) | ~200 |
| `feature/viewer/TxtViewerScreen.kt` | Universal code/text editor | ~730 |
| `feature/viewer/TxtViewerViewModel.kt` | Updated view model | ~150 |
| `feature/pdf_tools/PdfPageNumberScreen.kt` | PDF page numbering UI | ~200 |
| `feature/pdf_tools/PdfReorderScreen.kt` | PDF page reorder with drag-and-drop | ~320 |
| `feature/pdf_tools/PdfExtractImagesScreen.kt` | PDF image extraction UI | ~150 |

### Modified Files

| File | Changes |
|------|---------|
| `feature/pdf_tools/PdfToolsRepository.kt` | Added `addPageNumbers`, `reorderPdfPages`, `extractImagesFromPdf` |
| `feature/pdf_tools/PdfToolsViewModel.kt` | Added state vars and functions for new tools |
| `ui/navigation/Screen.kt` | Added routes for new screens |
| `ui/navigation/OmniNavGraph.kt` | Added composable destinations and event handlers |
| `feature/home/NavigationEvent.kt` | Added navigation events |
| `feature/tools/AllToolsScreen.kt` | Added new tools to PDF list |

### Features Implemented

1. **Syntax Highlighting** — 30+ languages including Kotlin, Java, Python, JS/TS, C/C++, C#, PHP, SQL, HTML/CSS, XML, JSON, YAML, Markdown, Gradle, Shell, Ruby, Go, Rust, Swift, Dart, Scala, R, Lua, Perl, and Log files

2. **Encoding Detection** — BOM detection (UTF-8/16/32), UTF-8 validation, single-byte encoding heuristics (Windows-1252, ISO-8859-1, etc.)

3. **Universal Code Editor** — Line numbers, search & replace, go-to-line, word wrap toggle, 4 color themes, font size control, encoding selection, file type detection

4. **PDF Page Numbering** — Configurable start number, 6 positions (top/bottom × left/center/right), adjustable font size

5. **PDF Page Reorder** — Visual grid with page thumbnails, drag-and-drop reordering, reset option

6. **PDF Image Extraction** — Extract all embedded images as PNG files

---

## Notes

- This document is living and will be updated as implementation progresses
- Each phase should be committed as logical batches
- Security fixes take priority over new features
- Existing tool improvements take priority over new tools
- All features must be fully offline — no network dependencies
- Maintain backward compatibility with existing file formats
