# OmniSuite — Release Readiness Audit (P0 Blockers)

**Date:** 2026-09-13
**Scope:** Full functional sweep. PPT shape-fill + PDF→DOCX clutter fixes already landed + `compileDebugKotlin` green.
**Policy:** Only `PDF to Word` carries a Beta tag. Everything else must ship as stable → P0s below must be fixed without regressing working flows (surgical, behavior-preserving defaults).

## Beta tagging
- `PDF to Word` (routes `pdf_to_word`, tool ids `pdf_to_word` + `word_pdf_to_word`) → labeled `PDF to Word • Beta`.
- All other tools keep existing names.

## P0-1 — PdfLayoutParser (block editor path)
- `core/engine/PdfLayoutParser.kt:89-90` — `startPage/endPage = pageIndex` (0-based). PDFBox `PDFTextStripper` is 1-based → parses wrong/all pages. Fix: `pageIndex + 1`. No behavior change for callers using `parseDocument` loop.
- `core/engine/PdfLayoutParser.kt:151-154` — `PDDocument().use { tempDoc.addPage(page) }` re-parents live `PDPage`, corrupts COS tree / crash. Fix: parse from owning `PDDocument` passed in, or stripper over loaded doc, never `addPage` a live page.

## P0-2 — PDF viewer (stability + privacy)
- `feature/viewer/PdfViewerViewModel.kt:299-315` — fixed `1.5x ARGB_8888` + count-based `LruCache(5)`, catches `Exception` only. A0/300dpi page ≈ 30MB+ ×5 = OOM crash. Fix: catch `OutOfMemoryError`, size-based cache, `RGB_565`/downsample, recycle on evict. Keep current fast path for normal pages.
- `feature/viewer/PdfViewerViewModel.kt:262-270,512-516` — decrypted `cacheDir/unlocked_*` deleted only on clean `closeRenderer()`. Crash/kill leaks sensitive PDF. Fix: startup sweeper + wipe in `onCleared()`, keep same filenames.
- `feature/viewer/PdfViewerViewModel.kt:543-555,585-636` — `PDDocument.load()` + manual `close()` not in `finally/.use{}` → fd/handle leak on timeout. Fix: `.use{}` blocks, same logic inside.

## P0-3 — Output + cache layer
- `core/util/FileOutputManager.kt:38-52,76-87` — `IS_PENDING=1` row never deleted when `openOutputStream` null/throws; empty URI returned as success (0B ghost). Fix: `resolver.delete(uri)` + return null on failure; clear `IS_PENDING` only after `length>0`. Same MediaStore folder/behavior otherwise.
- `core/util/UriCacheUtils.kt:112` — `File(cacheDir, DISPLAY_NAME)` raw → `../` traversal + collision overwrite (`117:delete()`). Fix: `hash_prefix + sanitized(name)`, same lookup order.
- `core/util/UriCacheUtils.kt:63-73,105-111` — `recent_files/hash_name` never pruned, stale `length()>0` returned. Fix: LRU cap + eviction, validate size/lastModified. No API change.

## P0-4 — PdfToolsRepository (render / redact / archive)
- `feature/pdf_tools/PdfToolsRepository.kt:335` — `createBitmap(w*2,h*2,ARGB_8888)` ≈58MB/page, no cap, renderer/page not in `finally`. Fix: cap max dim ~2048px, `RGB_565`, `try-finally` close. Same output for normal pages.
- `feature/pdf_tools/PdfToolsRepository.kt:1648` — redact box hardcodes `595/842` divisor → misplaced on non-A4. Fix: divide by actual `mediaBox`. Same API.
- `feature/pdf_tools/PdfToolsRepository.kt:1600-1627` — redact paints black rect only; text still extractable (privacy). Fix applied: honest "visual cover" labeling in UI messages + code comment; placement fixed to actual mediaBox. Full content-strip secure redact deferred (would risk content-stream corruption).
- `feature/pdf_tools/PdfToolsRepository.kt:955-965` — tar `unpackTarArchive` path traversal via `$name` with `../`. Fix: sanitize to normalized subpath + `isWithinDirectory`, reject `..`/absolute. Preserves nested dirs (unlike basename-flatten).

## P0-5 — DOCX/XLSX viewers (OOM + data loss)
- `DocxViewerViewModel.kt:250-252,1324` + `XlsxViewerViewModel.kt:145-146` + `PdfToolsViewModel.kt:273-275,365` — whole-file `readBytes()+Base64` into `evaluateJavascript("...('$base64')")` → Binder/JS limit, quoting break, OOM. Fix: size-guard (>15MB reject with message), stream via temp file / chunked interface. Keep current path for small files.
- `DocxViewerViewModel.kt:1308` + `XlsxViewerViewModel.kt:1482,1528` — in-place `FileOutputStream(f)` overwrite + unchecked SAF `openOutputStream` null → data loss / 0-byte success. Fix: write `.tmp` + atomic rename; throw/verify when stream null. Same filenames on success.
- `XlsxViewerViewModel.kt:966` — `wb.createCellStyle()` per `updateCell` hits POI 64k style limit → crash. Fix: style cache reuse/clone. Same visuals.
- `XlsxViewerViewModel.kt:249-306` — `EXTRA_ROWS=50/EXTRA_COLS=10` materializes full padded grid → 10k-row OOM. Fix: paginate/virtualize, drop padding. Same data, windowed render.

## P0-6 — OfficeConverter + WebView lifecycle
- `core/engine/document/OfficeConverter.kt:352` — `decodeByteArray` full-res + `LosslessFactory` OOM on large DOCX images. Fix: bounds + `inSampleSize` to printable width. Same placement.
- `core/engine/document/OfficeConverter.kt:1054,1233` — `renderPptxToBitmaps(1920)` accumulates `List<Bitmap>` (~8MB/slide) then duplicates into PDF. Fix: render/stream one slide at a time, recycle immediately. Same PDF out.
- `feature/pdf_tools/PdfToolsViewModel.kt:836` — `WebView(applicationContext)` + never `destroy()` → leak/crash. Fix: activity-context WebView, `destroy()` + 30s timeout. Same HTML→PDF result.
- `feature/pdf_tools/PdfToolsViewModel.kt:162ff` (all ~30 ops) — no `try/finally` on `isProcessing` → frozen UI on throw. Fix: `try/finally{isProcessing=false}` + single-flight guard. No flow change.

## P1 (deferred, not blocking this pass)
XLSX landscape/print-setup ignore, `DataFormatter` for ERROR/BLANK cells, cell truncation `...`, `splitPdf !!` + dropped ranges, `runBlocking` ANR, rotation/cropBox coords, hardcoded A4/300dpi/12-10pt fonts, bookmark-Y `700`, thresholds/columns/colors in layout parser, renderer openPage race, silent catches, stale-cache delete races, blocking-IO ANR, ZipSecurity basename flatten, CSV `readLines()`.

## Verification
- `compileDebugKotlin` green after PPT + DOCX fixes (this audit taken from green tree).
- After each P0 fix: re-run `compileDebugKotlin`; behavior-preserving defaults so existing working files render identically.
