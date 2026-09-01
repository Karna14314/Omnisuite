# Competitor Feature Gap Analysis

**Date:** 2026-09-01
**Sources:** iLovePDF, WPS Office, PDF24, Xodo, Foxit PDF, SmallPDF
**Criteria:** Tools that can run fully offline on-device without heavy processing

---

## Tools NOT in Our Suite (Available in Competitors)

### PDF Tools (from iLovePDF, PDF24, WPS, Xodo, Foxit)

| # | Tool | Competitors | Offline Feasible | Complexity | Notes |
|---|------|-------------|------------------|------------|-------|
| 1 | **PDF to PDF/A** | iLovePDF, PDF24 | Yes | Low | PDFBox supports PDF/A conversion (archival standard) |
| 2 | **Repair PDF** | iLovePDF, PDF24 | Yes | Low | Fix corrupted PDFs using PDFBox rebuild |
| 3 | **Compare PDF** | iLovePDF, PDF24, WPS | Yes | Medium | Text extraction + visual diff highlighting |
| 4 | **Redact PDF** | iLovePDF, PDF24 | Yes | Low | Permanently blackout text/areas (PDFBox overlay) |
| 5 | **PDF Form Creation** | iLovePDF, PDF24, WPS | Yes | Medium | Create fillable forms (text fields, checkboxes, radio, dropdowns, signatures) |
| 6 | **PDF Crop (Margins)** | iLovePDF, PDF24 | Yes | Low | Crop page margins (adjust mediaBox) - different from image crop |
| 7 | **PDF Bookmark Editor** | PDF24 | Yes | Low | Add/edit/remove bookmarks (PDFBox outlines) |
| 8 | **PDF Metadata Editor** | PDF24 | Yes | Low | Edit title, author, subject, keywords, creator |
| 9 | **PDF Overlay** | PDF24 | Yes | Low | Overlay one PDF on another (letterhead, stationery) |
| 10 | **PDF Underlay** | PDF24 | Yes | Low | Place PDF behind content (watermark background) |
| 11 | **PDF to Markdown** | iLovePDF | Yes | Medium | Convert PDF structure to Markdown format |
| 12 | **Optimize PDF (Web)** | PDF24 | Yes | Low | Linearize PDF for fast web viewing |
| 13 | **Digital Signature (Certificate)** | iLovePDF, Foxit | Yes | Medium | Certificate-based digital signature (PKCS12) |
| 14 | **Split by Bookmarks** | PDF24 | Yes | Low | Split PDF at bookmark boundaries |
| 15 | **Split by Size** | PDF24 | Yes | Low | Split PDF into chunks by file size |
| 16 | **Extract Text (Structured)** | iLovePDF | Yes | Low | Extract text with positioning info |
| 17 | **Insert Pages** | iLovePDF, PDF24 | Yes | Low | Insert pages from another PDF at specific position |
| 18 | **Replace Pages** | PDF24 | Yes | Low | Replace specific pages with pages from another PDF |
| 19 | **Duplicate Pages** | PDF24 | Yes | Low | Duplicate specific pages within PDF |
| 20 | **Crop Pages (Visual)** | iLovePDF, PDF24 | Yes | Medium | Visual crop with preview (different from margin crop) |

### Archive/Security Tools

| # | Tool | Competitors | Offline Feasible | Complexity | Notes |
|---|------|-------------|------------------|------------|-------|
| 21 | **Extract Password ZIP** | WPS, PDF24 | Yes | Low | Extract password-protected ZIP (zip4j) |
| 22 | **File Encryption (AES)** | WPS | Yes | Low | AES-256 encryption for arbitrary files |
| 23 | **File Shredder** | PDF24 | Yes | Low | Secure file deletion (overwrite with random data) |
| 24 | **Archive Info** | PDF24 | Yes | Low | Show compression ratio, method, file count |

### Productivity Utilities

| # | Tool | Competitors | Offline Feasible | Complexity | Notes |
|---|------|-------------|------------------|------------|-------|
| 25 | **File Hash/Checksum** | PDF24 | Yes | Low | Calculate MD5, SHA-1, SHA-256 hashes |
| 26 | **QR/Barcode History** | WPS | Yes | Low | Save scan/generate history with timestamps |
| 27 | **Signature Library** | Foxit, WPS | Yes | Medium | Save, manage, and reuse multiple signatures |
| 28 | **Unit Converter** | WPS | Yes | Low | Common unit conversions (length, weight, temperature) |
| 29 | **Color Picker/Extractor** | WPS | Yes | Low | Pick colors from images or screen |
| 30 | **Document Templates** | WPS | Yes | Medium | Templates for resumes, invoices, letters |
| 31 | **Mind Map** | WPS | Yes | Medium | Create visual mind maps |
| 32 | **Batch Rename** | WPS | Yes | Low | Rename multiple files with patterns |
| 33 | **File Splitter/Joiner** | WPS | Yes | Low | Split large files into parts and rejoin |

### Image Tools (Additional)

| # | Tool | Competitors | Offline Feasible | Complexity | Notes |
|---|------|-------------|------------------|------------|-------|
| 34 | **Image to PDF (Multiple Layouts)** | iLovePDF, WPS | Yes | Low | 1/2/4/6/9 images per page with margins |
| 35 | **SVG to PDF** | PDF24 | Yes | Medium | Convert SVG vector graphics to PDF |
| 36 | **Batch Image Processing** | WPS | Yes | Low | Apply operations to multiple images at once |
| 37 | **Image Metadata (EXIF) Viewer** | WPS | Yes | Low | View and edit EXIF data |
| 38 | **Collage Maker** | WPS | Yes | Medium | Create photo collages with templates |
| 39 | **Panorama Stitch** | WPS | Yes | Medium | Stitch overlapping images into panorama |

### Text/Document Tools

| # | Tool | Competitors | Offline Feasible | Complexity | Notes |
|---|------|-------------|------------------|------------|-------|
| 40 | **Text-to-Speech** | WPS | Yes | Low | Read documents aloud (Android TTS) |
| 41 | **Speech-to-Text** | WPS | Yes | Low | Voice dictation (Android SpeechRecognizer) |
| 42 | **Document Translation** | WPS | No | High | Requires AI/cloud (skip per requirements) |
| 43 | **Word Count (Advanced)** | WPS | Yes | Low | Reading time, character count, line count |
| 44 | **Text Compare/Diff** | WPS | Yes | Medium | Compare two text files and highlight differences |

---

## Priority Implementation Order

### Tier 1: Quick Wins (Low Complexity, High Value)
1. PDF to PDF/A conversion
2. PDF metadata editor
3. PDF bookmark editor
4. PDF crop (margin adjustment)
5. PDF redaction
6. Repair PDF
7. File hash/Checksum
8. Extract password ZIP
9. QR/Barcode history
10. Batch rename

### Tier 2: Medium Complexity (Medium Value)
11. PDF form creation
12. Compare PDF
13. PDF overlay/underlay
14. PDF to Markdown
15. Split by bookmarks/size
16. Insert/replace pages
17. Signature library
18. File encryption (AES)
19. Image metadata viewer
20. Text compare/Diff

### Tier 3: Higher Complexity (Specialized Value)
21. Digital signature (certificate)
22. Optimize PDF (web linearization)
23. Document templates
24. Mind map
25. Collage maker
26. Panorama stitch

---

## Summary

**Total competitor tools not in our suite:** 44
**Feasible to implement offline:** 44 (all can be done locally)
**Quick wins (Tier 1):** 10 tools
**Medium complexity (Tier 2):** 10 tools
**Higher complexity (Tier 3):** 6 tools

All listed tools can be implemented without network connectivity and without requiring server maintenance, aligning with the offline-first requirement.
