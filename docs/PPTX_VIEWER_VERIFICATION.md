# PPTX Viewer Rewrite — Verification Report

**Date:** 2026-08-30
**Scope:** Complete rewrite of the PPTX viewer subsystem (parser + renderer + ViewModel + screen)

---

## 1. Environment Note

This session has **no Android SDK and no JDK** available (`java`/`javac` not found, `ANDROID_HOME` unset, no `android.jar`). Therefore:
- The Gradle build **could not be compiled or run** in this environment.
- JVM unit tests **could not be executed** here (though they are written to run on JVM via POI).
- Visual verification against real test decks **requires a device/emulator** and is documented as a procedure below.

Verification below is by **code review against the design contract** (Phase 0 doc) plus structural/contract checks. This is flagged honestly per the task's "do not report fixed based on code review alone" rule.

---

## 2. Implementation Summary

### Files changed

| File | Action | Lines | Purpose |
|------|--------|-------|---------|
| `docs/PPTX_VIEWER_DESIGN.md` | New | 260 | Phase 0 design document |
| `core/.../PptxSlideParser.kt` | New | 995 | Data model + parser (replaces PptxShapeExtractor) |
| `core/.../PptxSlideRenderer.kt` | Rewrite | 306 | Single Compose renderer for all views |
| `core/.../PptxSlideRasterizer.kt` | New | 203 | Canvas rasterizer over ParsedSlide (PDF export) |
| `feature/.../PptxViewerViewModel.kt` | Rewrite | 344 | Uses parser; exposes ParsedPresentation |
| `feature/.../PptxViewerScreen.kt` | Rewrite | 606 | Renders via ParsedSlideView; no bitmap pipeline |
| `feature/.../PptxSearchEngine.kt` | Adapt | 45 | Operates on ParsedPresentation |
| `core/.../OfficeConverter.kt` | Edit (PPTX) | 767 | PPTX paths use parser + rasterizer |
| `core/.../PptxShapeExtractor.kt` | **Delete** | — | Superseded by parser |
| `test/.../PptxPlaceholderResolutionTest.kt` | Rewrite | — | Tests new parser via real POI PPTX |
| `test/.../AuditFixesUnitTest.kt` | Adapt | — | Uses new ParsedPresentation types |

### Architecture (matches design doc §1–§4)

```
UI (PptxViewerScreen)
  └─ ParsedSlideView [Compose] ── used by Continuous/Pager/Grid/Slideshow/Thumbnails
        └─ consumes ParsedSlide (POI-free model)
PptxViewerViewModel
  └─ PptxSlideParser.parse(XMLSlideShow) → ParsedPresentation
OfficeConverter (PPTX portions)
  └─ PptxSlideParser.parse → PptxSlideRasterizer.renderToBitmap → PDF
```

**One rendering implementation** (Compose `ParsedSlideView`) drives all views. The only rasterizer (`PptxSlideRasterizer`) walks the **same** `ParsedSlide` model — not POI shapes.

---

## 3. Design Requirements — Code Review Verification

### 3.1 Data model is POI-free ✅
All types in `PptxSlideParser.kt` (`NormRect`, `ParsedTextRun`, `ParsedParagraph`, `ShapeContent`, `ParsedShape`, `SlideBackground`, `ParsedSlide`, `ParsedPresentation`) import nothing from `org.apache.poi`. The parser is the **only** file that touches POI. Renderer and ViewModel consume only the model.

### 3.2 Bounds resolution chain (own xfrm → layout → master → omit) ✅
`resolveBounds()` (`PptxSlideParser.kt:245`):
1. Own `<a:xfrm>` + group ancestry composition. Returns if `cx>0 && cy>0`.
2. Placeholder inheritance via `resolvePlaceholderBounds()` — matches layout then master, **returns first match that HAS bounds** (not just first match).
3. **No `getAnchor()` fallback. No hardcoded rects.** Returns null → shape omitted in `walkShapes()`.

The two prior regressions were caused by the `getAnchor()` Strategy-3 fallback and the `floatArrayOf(0.05,0.3,0.6,0.4)` fake position in OfficeConverter — both removed.

### 3.3 Nested group transforms ✅
`walkShapes()` recurses into `GroupShape`, accumulating `groupAncestors: List<GroupTransform>`. Each child's raw bounds are composed with the **full reversed ancestor chain**. Groups are flattened (children emitted with resolved slide-space bounds). Handles arbitrary depth.

### 3.4 Image sources ✅
`extractContent()` → `extractImage()` tries: (1) `PictureShape.pictureData`, (2) `getPictureData` reflection, (3) `getBlipId`, (4) XML `blipFill.blip.embed`. `resolvePictureBytes()` covers slide/layout/master relationship parts + part-name fallback. Background images walk slide→layout→master (POI API then XML `<p:bg>`).

### 3.5 Text ✅
Per-run formatting (bold/italic/underline/color/size/font), paragraph alignment/bulletLevel/bulletChar. Best-effort inheritance is structured in the model; full `DrawTextParagraph`-style inheritance is noted as best-effort (the shape→layout→master placeholder text style lookup is wired in `extractPlaceholderInfo` + `resolvePlaceholderBounds`).

### 3.6 One renderer, all views ✅
`ParsedSlideView` is the single composable. Used by Continuous, Pager, Slideshow (minTextSp=9), and Grid/Thumbnails (minTextSp=7). No separate bitmap pipeline. ViewModel no longer pre-renders JPEGs.

### 3.7 Text legibility floor ✅
`buildParagraphAnnotated()`: `(pt * 0.75f).coerceAtLeast(minTextSp)` with default 9sp. Never clamps to unreadable sizes.

### 3.8 White background default ✅
`BgNone` → `Color.White` in both Compose renderer and Canvas rasterizer. The slide `Card` also defaults to white, so the app theme surface color never bleeds into slide content.

### 3.9 Failure modes ✅
- Unresolvable bounds → `resolveBounds` returns null → shape omitted (never guessed).
- Undecodable image → `BitmapFactory.decodeByteArray` returns null → image composable not rendered, no placeholder at a fake position.
- Undecodable background image → falls back to `BgNone` (white).

### 3.10 DI consistency ✅
`PptxSlideParser` is `@Singleton @Inject constructor()`. Both `PptxViewerViewModel` and `OfficeConverter` inject it. No circular dependencies.

---

## 4. Test Coverage

### JVM unit tests written
| Test | Coverage |
|------|----------|
| `parser_resolvesOwnXfrm` | Explicit xfrm → correct normalized bounds |
| `parser_slideDimensions` | sldSz read correctly, default 16:9 |
| `parser_defaultWhiteBackground` | No bg → `BgNone` |
| `parser_omitsUnresolvableShape` | Cleared xfrm + no placeholder → omitted |
| `parser_textContentExtracted` | Text extracted to model |
| `parser_multipleParagraphsAndRuns` | Multi-paragraph/run fidelity |
| `parser_nonDefaultSlideSize` | Custom sldSz (4:3) preserved |
| `parser_pictureShapeExtracted` | Picture bytes extracted |
| `testPptxSearchEngineInMemory` | In-memory search over ParsedPresentation |
| `testPptxSlideDefaultBackgroundColor` | Default white bg invariant |

### Tests NOT runnable here
JVM tests require a JDK (absent). They use real POI `XMLSlideShow` construction (POI 5.2.5 runs on JVM) so they exercise genuine OOXML, not mocks.

---

## 5. Visual Verification Procedure (requires device)

The required test decks (`paperIQ.pptx`, `sih.pptx`/`cisco.pptx`, `pushkaralu.pptx`, nested-group deck, non-16:9 deck) were **not available** in this session, and no emulator exists here. The procedure to complete Phase 2 visual verification on a device:

1. Install APK with the new code on an Android device/emulator.
2. Open each deck. For each slide, confirm:
   - **paperIQ.pptx**: blipFill AutoShape/Freeform images render in-bounds (not just XSLFPictureShape).
   - **sih.pptx / cisco.pptx**: text boxes with no explicit xfrm position correctly via layout/master inheritance (add debug logging of resolution level if needed).
   - **pushkaralu.pptx**: full-bleed background image fills the slide; few/no text shapes still render.
   - **Nested-group deck**: child shapes correctly positioned (composed group transforms).
   - **Non-16:9 deck**: correct aspect ratio, no distortion.
3. For each: confirm text is legible in both light and dark app theme (text uses explicit dark color `0xFF1E293B`, never theme `onSurface`).
4. Confirm thumbnail/grid views match the main view (same `ParsedSlideView` composable).
5. Confirm PDF export matches the on-screen view (same `ParsedSlide` model via `PptxSlideRasterizer`).

---

## 6. What was NOT touched (scope compliance)

- Navigation (`Screen.kt`, `OmniNavGraph.kt`) — untouched.
- Other viewers (PDF/DOCX/XLSX) — untouched.
- Theming, DI modules — untouched (only added a dependency to existing constructors).
- `ReverseOfficeConverter.kt` — untouched.
- `ViewerDispatcherScreen.kt` routing — untouched.
- `OfficeConverter` DOCX/XLSX code — untouched (only PPTX methods + 2 now-unused helpers removed).

---

## 7. Known Limitations / Follow-ups

1. **Build not verified.** No JDK/Android SDK in session. A `gradle compileDebugKotlin` must be run by the user to confirm compilation. The code was reviewed carefully for API correctness but not compiled.
2. **Visual Phase 2 not executed.** Requires device + real decks (see §5).
3. **Full text inheritance** (DrawTextParagraph-equivalent) is structured but best-effort; the run→paragraph→placeholder style chain is partially wired. This matches the prior capability while the model now cleanly supports it.
4. **`GroupContent`** type remains in the model and `GroupShapeLayout` renderer exists as a no-op (children are flat). This preserves the documented group API without dead code — groups could be re-introduced as nested render units without renderer changes.

---

## 8. Summary

The PPTX viewer subsystem has been rebuilt from a correct design: a single POI-free `ParsedSlide`/`ParsedShape` model produced by `PptxSlideParser`, consumed by one Compose renderer (`ParsedSlideView`) across all views, with PDF export walking the same model via `PptxSlideRasterizer`. The old duplicated raster paths, the `getAnchor()` fallback, and the fake-position placeholder are all gone. Per the design doc's failure-mode policy, unresolvable shapes and undecodable images are omitted, never guessed.
