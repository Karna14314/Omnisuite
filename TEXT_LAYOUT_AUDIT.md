# TEXT_LAYOUT_AUDIT.md — OmniSuite PPTX Viewer Text Layout Fidelity

**Date:** 2026-08-30
**Scope:** Text layout accuracy in the Compose PPTX renderer: line spacing, autofit /
shrink-to-fit, bullet/numbering, table rendering, and padding/insets.
**Sources audited:**
- Renderer: `feature/viewer/PptxViewerScreen.kt` — `TextShapeItem` (L1372–1498)
- Parser + data model: `feature/viewer/PptxViewerViewModel.kt` — `PptxTextRun`/`PptxParagraph`/`PptxTextShape`/`PptxSlide` (L32–99), `parseAllSlides` (L594–975)
- Engine API: `docs/engines.md`

The data model is named `PptxTextShape`/`PptxSlide` in code (the brief's "ParsedShape"/"ParsedSlide"
are these types). The renderer is the single `TextShapeItem` composable.

---

## 1. What PPTX specifies per text body — and what we capture

### 1.1 Line spacing `<a:lnSpc>`
OOXML: `<a:lnSpc>` is either `<a:spcPct val="…"/>` (percentage; 100000 = 100% = single)
or `<a:spcPts val="…"/>` (exact leading in points). Per-paragraph, set via `<a:pPr><a:lnSpc>`.
Absent value → client default, conventionally single (≈1.0× font height; PowerPoint uses
~1.0–1.15× depending on font).

| Field | Captured? | Where | Notes |
|-------|-----------|-------|-------|
| `spcPct` (line spacing %) | ❌ NO | — | Not parsed anywhere |
| `spcPts` (exact pt leading) | ❌ NO | — | Not parsed anywhere |

**Renderer gap:** `TextShapeItem` hardcodes `lineHeight = (maxRunSp * 1.4f)` (L1485), regardless of
source. This is the **primary root cause of "lines touching/overlap"** — single-spaced source
(100%) is being rendered at 1.4×, but more importantly the inter-paragraph gap collapses to a
hardcoded `padding(bottom = 1.dp)` (L1443) and the parsed `spaceBeforePt`/`spaceAfterPt` are
ignored (see 1.2). When source specifies tight exact spacing (e.g. spcPts) or when paragraphs
have no space-after, consecutive paragraph lines visually collide.

### 1.2 Space before/after paragraph `<a:spcBef>`/`<a:spcAft>`
OOXML: paragraph-level spacing in points (`<a:spcBef><a:spcPts val="…"/></a:spcBef>` etc.).

| Field | Captured? | Where | Notes |
|-------|-----------|-------|-------|
| `spaceBeforePt` | ✅ YES | `PptxParagraph` L47, parsed L821 | value in points |
| `spaceAfterPt` | ✅ YES | `PptxParagraph` L48, parsed L822 | value in points |

**Renderer gap:** `spaceBeforePt`/`spaceAfterPt` are stored in the model but **never read by
`TextShapeItem`**. Paragraphs are stacked with a fixed `Row(…padding(bottom = 1.dp))` (L1443).
This is a **contributing root cause of "lines touching"** — inter-paragraph leading is ~1dp
instead of the source's pt value.

### 1.3 Autofit `<a:normAutofit>` / `<a:spAutoFit>` / none
OOXML `<a:bodyPr>`: `normAutofit` (shrink text on overflow — PowerPoint's "shrink text on
overflow"; carries optional `fontScale` and `lnSpcReduction` attrs that the source file may
already bake in at save time), `spAutoFit` (resize the shape to fit text), or neither.

| Field | Captured? | Where | Notes |
|-------|-----------|-------|-------|
| `normAutofit` presence | ❌ NO | — | not parsed |
| `normAutofit fontScale` | ❌ NO | — | not parsed |
| `normAutofit lnSpcReduction` | ❌ NO | — | not parsed |
| `spAutoFit` presence | ❌ NO | — | not parsed |

**Renderer gap:** `TextShapeItem` has **no shrink-to-fit logic**. Text is simply clipped via
`overflow = TextOverflow.Ellipsis` with `maxLines = 3` (title) / `100` (body) (L1490–1491),
inside a fixed-height box (L1391). This is the **root cause of "content overflow"** (long text
bleeds/ellipsizes instead of shrinking) and contributes to **"title truncated to 1 line"** (a
title needing 2 lines is clipped by the fixed box height / `maxLines` instead of being measured
and shrunk or having its font/box sized to the wrapped line count).

### 1.4 Text insets / padding `<a:bodyPr>` lIns/tIns/rIns/bIns
OOXML: `<a:bodyPr lIns="…" tIns="…" rIns="…" bIns="…">` in EMU. Specified defaults when absent:
lIns 91440, tIns 45720, rIns 91440, bIns 45720 EMU (≈0.1in / 0.05in / 0.1in / 0.05in).

| Field | Captured? | Where | Notes |
|-------|-----------|-------|-------|
| `lIns` / `tIns` / `rIns` / `bIns` | ❌ NO | — | not parsed |

**Renderer gap:** `TextShapeItem` applies hardcoded proportional padding
`padH = maxOf((slideW * 0.02f).dp, 4.dp)` (title) / `maxOf((slideW * 0.01f).dp, 3.dp)` (body),
`padV` similar (L1385–1386) — independent of the source's real insets. This means every shape
gets ~1–2% padding regardless of what PowerPoint defined; text either crowd the borders or
waste space. This is a **contributing cause of "text touching cell borders"** in tables and of
the title/shape sizing being off.

### 1.5 Bullet definition
OOXML: `<a:buChar char="…">`, `<a:buAutoNum type="…">` (arabicPeriod, arabicParenR,
alphaUcParenR, alphaLcParenR, romanUcPeriod, romanLcPeriod, etc.), `<a:buNone>`, with optional
`buFont` (bullet may need a different font, e.g. Wingdings) and per-level `<a:lvl1pPr>`…`<a:lvl9pPr>`
(alignment + indent per level).

| Field | Captured? | Where | Notes |
|-------|-----------|-------|-------|
| `bulletLevel` (indent level) | ✅ YES | `PptxParagraph` L43, parsed L777 | `p.indentLevel` |
| `hasBullet` | ✅ YES | L44, L804 | incl. XML buChar/buAutoNum presence |
| `bulletChar` | ✅ YES | L45, L805–810 | char or fallback "•" |
| `buAutoNum` **type** | ❌ NO | — | only presence detected (L791–798); type string not stored |
| `buFont` | ❌ NO | — | not parsed |
| per-level indent/alignment | ❌ NO | — | not parsed |

**Renderer gap:** `TextShapeItem` renders a bullet as a plain `Text` "• " or the char (L1450–1458)
and indents by `bulletLevel * 6dp` (L1448). Because the `buAutoNum` type is not captured,
**numbered lists cannot render "1. 2. 3." / "a)" / "i."** — they fall back to "•". Indent is a
fixed 6dp/level instead of the source's per-level indent. This is the **root cause of the
numbered-list rendering issue**.

### 1.6 Table structure
OOXML `<a:tbl>`: `<a:gridCol w="…"/>` (column widths, EMU), `<a:tr h="…">` (row heights, EMU),
`<a:tc>` with `<a:tcPr marL/marR/marT/marB>` (cell margins, EMU) + borders/fill.

| Field | Captured? | Where | Notes |
|-------|-----------|-------|-------|
| gridCol widths | ❌ NO | — | not parsed |
| row heights | ❌ NO | — | not parsed |
| cell margins | ❌ NO | — | not parsed |
| cell borders / fill | ❌ NO | — | not parsed |

**Current handling:** `parseAllSlides` (L884–945) flattens a table into independent
`PptxTextShape`s with id `table_cell_r_c`, each positioned by **crude equal division**
(`cellLeft = tLeft + c/numCols * tWidth`, `cellH = tHeight/numRows`, L898–901). No gridCol width,
row height, or cell margin is read. `TextShapeItem` then pads each cell with the same
proportional `padH/padV` (L1385–1386) — so **text touches cell borders** (no real cell margin)
and **columns are equal-width** regardless of source. This is the **root cause of all table
rendering issues**.

---

## 2. Current Compose rendering gaps (summary)

| Symptom | Root cause (renderer) | Root cause (parser/model) |
|---------|-----------------------|---------------------------|
| Lines touching / overlap | `lineHeight` hardcoded 1.4× (L1485); inter-paragraph gap = fixed 1dp (L1443); ignores `spaceBeforePt`/`spaceAfterPt` | `lnSpc` not parsed |
| Content overflow (no shrink) | No shrink-to-fit; fixed-height box + `Ellipsis` (L1391, L1490–1491) | `normAutofit` fontScale/lnSpcReduction not parsed |
| Title clipped to 1 line | Fixed box height clips; `maxLines=3` but height-bound; no measurement of wrapped line count | `normAutofit` not parsed; insets not parsed (box too small) |
| Numbered lists wrong | Bullet always "•"; indent fixed 6dp/level (L1448, L1450) | `buAutoNum` type not captured |
| Table cells: text touches borders; equal columns | Cells are independent shapes with proportional padding, equal division (L898–901) | gridCol widths, row heights, cell margins not parsed |

---

## 3. Measurement approach

Going forward, text will be measured with Compose layout APIs rather than estimated:

- **Wrapped line count / text height:** use `Text.onTextLayout` → `TextLayoutResult`
  (`lineCount`, `hasVisualOverflow`, `getLineBottom(i)`, `size`). This gives the ACTUAL wrapped
  line count and pixel height for the given width + font size, used to decide (a) whether the
  text overflows the shape's content box and (b) how many lines a title really occupies.
- **Shrink-to-fit (normAutofit):** measure at full font; if `hasVisualOverflow` within the
  content-box height, reduce the font scale (and optionally line-spacing reduction) and
  re-measure, down to a sane minimum (e.g. 50% font / PowerPoint's floor). Prefer the source's
  baked-in `fontScale`/`lnSpcReduction` when present.
- **No estimation:** line count is never derived from character count or a fixed line-height
  constant. The 1.4× and 1dp constants are replaced by measured values driven by parsed
  `lnSpc` / `spaceBeforePt` / `spaceAfterPt`.

---

## 4. Test plan

> **Environment limitation (see Phase 2):** this container has no Java, no Android SDK/AVD, and
> no working Gradle daemon (`java` not on PATH, `ANDROID_HOME` unset). Therefore the app cannot
> be built, run, or screen-shotted here. Phase 2 verification is performed via (a) JVM unit
> tests that exercise the POI parsing path on programmatically-built PPTX files (asserting the
> newly-parsed fields) and (b) careful renderer code review against the measurement approach.
> On-device screenshot confirmation is documented as a required follow-up.

Test PPTX decks will be generated in-test with Apache POI 5.2.5 (already a dependency) so the
parsing path is exercised on real OpenXML. Each maps to a concrete assertion:

| # | Slide content | Asserted correct rendering |
|---|--------------|----------------------------|
| T1 | Two-line title (e.g. "Quarterly\nResults" in a short title placeholder) | Title renders on **2 lines, both fully visible, no clipping** (measured line count = 2; box/font sized to fit) |
| T2 | Dense body box, single-spaced (100% lnSpc), 5 short paragraphs with 0 space-before/after | **No line overlap**; inter-line leading ≈ single; paragraphs separated by their (zero) space-after — lines never closer than the 100% leading |
| T3 | Long bullet list (8 items) in a fixed-height body placeholder with `normAutofit` | Text **shrinks to fit** within bounds (no ellipsis, no overflow); bullet markers present per level |
| T4 | Numbered list with `buAutoNum type="arabicPeriod"` | Markers render **"1." "2." "3."** sequentially (not "•") |
| T5 | Numbered list with `buAutoNum type="alphaLcParenR"` | Markers render **"a)" "b)" "c)"** |
| T6 | Table 3×4 with **unequal** gridCol widths, explicit row heights, and cell margins | Columns render at **correct relative widths**; rows at correct heights; **text inset from every cell border** by the cell margin; no text touching borders |
| T7 | Body box with `spaceAfterPt=12` between paragraphs | Visible gap ≈12pt between paragraphs (not 1dp) |
| T8 | Title placeholder with `normAutofit fontScale="75000"` (75%) baked in | Renderer honors the 75% scale (text measurably ~75% of base size) rather than recomputing |

---

## 5. Confirmed model/parser additions required (Phase 0 proof)

The following fields are **proven missing and required** by the renderer, so the data model and
parser WILL be extended (the only model/parser changes in this task):

1. `PptxParagraph.lineSpacingMul: Float = 1.0f` — from `<a:lnSpc>` spcPct (val/100000) or spcPts
   (pts / run font size); default 1.0 (single). Needed for line-height fidelity.
2. `PptxParagraph.numberingType: String? = null` — from `<a:buAutoNum type>`. Needed to render
   numbered lists (arabicPeriod → "1.", alphaLcParenR → "a)", etc.).
3. `PptxTextShape.bodyInsets: EdgeInsets` (l,t,r,b in EMU) — from `<a:bodyPr>` lIns/tIns/rIns/bIns;
   default to OOXML spec defaults (91440/45720/91440/45720). Needed for correct padding.
4. `PptxTextShape.autoFit: AutoFitMode` + `fontScale: Int?` + `lnSpcReduction: Int?` — from
   `<a:bodyPr>` normAutofit/spAutoFit. Needed for shrink-to-fit.
5. New `PptxTable` / `PptxTableCell` model with gridCol widths, row heights, cell margins — needed
   for correct table layout. `PptxSlide.tables: List<PptxTable>` added; table cells are NO LONGER
   flattened into independent text shapes.

No other model/parser changes are made. Shape-bounds resolution, image extraction, and the
`PptxSlide` fields other than the new `tables` list are untouched.
