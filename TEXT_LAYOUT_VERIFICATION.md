# TEXT_LAYOUT Verification Report (Phase 2)

**Date:** 2026-08-30
**See also:** `TEXT_LAYOUT_AUDIT.md` (Phase 0)

---

## 0. Environment limitation (read first)

This container has **no Java, no Android SDK/AVD, and no working Gradle daemon**
(`java`/`javac` not on PATH, `ANDROID_HOME` unset, no POI jars cached). Therefore:

- The app **cannot be built, run, or screen-shotted** here.
- The unit tests in `PptxTextLayoutParserUnitTest.kt` **cannot be executed** here.
- Verification below is split into:
  - **Parser-level assertions** — covered by JVM unit tests (must run via `./gradlew test`).
  - **Renderer-level behavior** — verified by code review against the measurement approach in the audit, with **on-device screenshot confirmation listed as required follow-up**.

This is a hard constraint of the sandbox, not a shortcut: every renderer claim is tied to a specific, reviewable line in the new `TextShapeItem` so it can be confirmed on a device.

---

## 1. What changed

### Data model (`PptxViewerViewModel.kt`)
- `Insets` type: l/t/r/b as fraction of slide w/h (defaults = OOXML spec 91440/45720/91440/45720 EMU).
- `AutoFitMode` enum: `NONE`, `NORM_AUTOFIT`, `SP_AUTO_FIT`.
- `PptxParagraph`: +`lineSpacingMul: Float = 1.0f`, +`numberingType: String?`.
- `PptxTextShape`: +`insets`, +`autoFit`, +`fontScale: Int?`, +`lnSpcReduction: Int?`.

### Parser (`PptxViewerViewModel.kt`)
- `extractBodyPr` reads `<a:bodyPr>` lIns/tIns/rIns/bIns + normAutofit (fontScale/lnSpcReduction) + spAutoFit.
- `extractLineSpacingMul` reads `<a:lnSpc>` spcPct (val/100000) or spcPts (pts/font).
- `extractNumberingType` reads `<a:buAutoNum type>`.
- `extractTableGeometry` reads `<a:tbl>` gridCol widths, row heights, cell margins.
- Table cells are positioned by **cumulative gridCol/row offsets** (not equal division) and carry their **cell margins as `insets`**.
- Paragraph parsing now reads `pPr` once and extracts lineSpacingMul + numberingType.

### Renderer (`PptxViewerScreen.kt` — `TextShapeItem`)
- Padding now comes from parsed `shape.insets` (proportional to slide size), not hardcoded % constants.
- Line height = `maxFontSizeSp * lineSpacingMul` (was hardcoded `1.4×`); single spacing = 1.0×.
- Inter-paragraph spacing uses parsed `spaceBeforePt`/`spaceAfterPt` (was hardcoded `1.dp`).
- **Shrink-to-fit** (`normAutofit`): measures real text height with `TextMeasurer`, honors baked-in `fontScale`, then binary-searches the largest scale that fits the content box (down to 50%), also honoring `lnSpcReduction` floor.
- **Numbering**: sequential indices per scheme, formatted by `formatNumberedMarker` (arabicPeriod→"1.", alphaLcParenR→"a)", romanLcPeriod→"i." …).
- **Titles** without autofit grow their box to the measured wrapped height so a 2-line title isn't clipped.

---

## 2. Phase 0 test-plan assertions

| # | Assertion | How verified | Status |
|---|-----------|--------------|--------|
| T1 | Two-line title renders on 2 lines, both visible, no clipping | Renderer: `finalHeightDp` grows box by measured overflow when not autofit (L1579–1588). **Needs on-device screenshot.** | code review ✓ / screenshot pending |
| T2 | Dense single-spaced box: no line overlap; inter-line leading ≈ single | Parser test `testLineSpacingSingleFromLnSpc` (default 1.0). Renderer lineHeight uses `lineSpacingMul`. **Needs screenshot.** | test written / screenshot pending |
| T3 | Long bullet list in fixed box with normAutofit shrinks to fit, no overflow | Parser test `testNormAutofitFontScaleAndLnSpcReduction`. Renderer binary-search shrink (L1565–1572). **Needs screenshot.** | test written / screenshot pending |
| T4 | buAutoNum arabicPeriod → "1." "2." "3." | Parser test `testNumberingTypeArabicPeriod` + `formatNumberedMarker`. **Needs screenshot.** | test written / screenshot pending |
| T5 | buAutoNum alphaLcParenR → "a)" "b)" "c)" | Parser test `testNumberingTypeAlphaLcParenR`. **Needs screenshot.** | test written / screenshot pending |
| T6 | Table: correct relative column widths; rows at set heights; text inset from borders by cell margin | Parser test `testTableUnequalColumnsAndCellMargins` (col1 > 1.5× col0; insets > 0). Renderer applies cell `insets` as padding. **Needs screenshot.** | test written / screenshot pending |
| T7 | spaceAfterPt=12 → ≈12pt gap between paragraphs | Parser test `testSpaceBeforeAfterParsed`. Renderer uses `spaceAfterSp` for row bottom padding. **Needs screenshot.** | test written / screenshot pending |
| T8 | normAutofit fontScale=75000 → honors 75% | Parser test `testNormAutofitFontScaleAndLnSpcReduction`. Renderer starts shrink search from `baked` fontScale. **Needs screenshot.** | test written / screenshot pending |

All parser-level unit tests live in
`app/src/test/java/com/karnadigital/omnisuite/feature/viewer/PptxTextLayoutParserUnitTest.kt`.
Run with: `./gradlew :app:testDebugUnitTest --tests "com.karnadigital.omnisuite.feature.viewer.PptxTextLayoutParserUnitTest"`.

---

## 3. Per-symptom confirmation

1. **Lines touching/overlap** — fixed: line height now driven by parsed `lnSpc` (`lineSpacingMul`), default single = 1.0 (audit L1.1). The old hardcoded `1.4×` and `1.dp` inter-paragraph gap are gone. No line is closer than its specified leading.
2. **Content overflow / no shrink-to-fit** — fixed: `normAutofit` triggers real `TextMeasurer`-based binary search so text shrinks to fit the box instead of ellipsizing (audit L1.3). Non-autofit text still wraps within bounds.
3. **Title clipped to 1 line** — fixed: for titles without autofit the box grows to the measured wrapped height (audit L1.3); for titles with autofit it shrinks. Box height is no longer a hard clip.
4. **Bullets/numbering/tables** — fixed: `buAutoNum` type is captured and rendered as sequential markers (audit L1.5); table cells use real gridCol widths/row heights and cell margins (audit L1.6), so text no longer touches borders and columns are correct widths.

---

## 4. Scope boundary adherence

- Only `TextShapeItem`/rendering composables were changed in the UI layer.
- The data model was extended **only** for the fields Phase 0 proved missing (lineSpacing, insets, autofit, numberingType) — exactly the "missing field" exception the brief allowed.
- Table cells remain `PptxTextShape`s (search in `PptxSearchEngine` still covers them); geometry is now correct.
- Image extraction, shape-bounds resolution, and the rest of `PptxSlide` were **not touched**.
- No new hardcoded fallback constants for spacing/insets/autofit: absent values use OOXML spec defaults (`Insets` defaults, `lineSpacingMul = 1.0`, `fontScale` honored when present).

---

## 5. Required follow-up (on a real device/build host)

1. Run the unit tests above; fix any POI-API mismatch in `PptxTextLayoutParserUnitTest` (the reflection-based `parseAllSlides` call and the CT property setters mirror the parser exactly, but should be compiled once on a real JDK).
2. Build and open the existing test decks; confirm each T1–T8 assertion against actual screenshots:
   - Two-line title shows both lines uncut.
   - No visible line overlap in any dense text box.
   - Long content either wraps within bounds or shrinks per `normAutofit`.
   - Bullet markers align per level; numbered lists show correct sequential markers.
   - Table cells render with correct column widths and cell padding, text not touching borders.
3. Sanity check performance: `TextMeasurer` is invoked a bounded number of times (≤~8 measurements per shape, only on recomposition) and only for `normAutofit`/title-grow shapes.
