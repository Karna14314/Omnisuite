# PPTX Rendering Fidelity — Fix Plan & Cloud-Agent Handoff

Status: **open**. Created after a visual diff of OmniSuite's PPTX viewer against a
reference render of a real 16:9 hackathon template deck.

This document is the single source of truth for the work. It is written to be
self-contained: a fresh agent with no prior context should be able to execute it
top to bottom.

---

## 1. Environment

| Item | Value |
| --- | --- |
| Repo root | `C:\Users\chait\Projects\Omnisuite` (git, branch `main`) |
| App module | `app`, namespace `com.karnadigital.omnisuite` |
| Language / UI | Kotlin, Jetpack Compose (BOM `2024.06.00`) |
| DI / persistence | Hilt, Room |
| SDK | `compileSdk 36`, `minSdk 30`, `targetSdk 36`, `jvmTarget 17` |
| Office parsing | `org.apache.poi:poi-ooxml:5.2.5`, `poi-scratchpad:5.2.5` |
| Unit tests | JUnit 4.13.2, `testOptions { unitTests.isReturnDefaultValues = true }` |
| Gradle heap | `-Xmx6144m` already set in `gradle.properties` |

### Commands

```powershell
# compile only (fastest signal)
.\gradlew.bat :app:compileDebugKotlin --console=plain

# unit tests
.\gradlew.bat :app:testDebugUnitTest --console=plain

# single test class
.\gradlew.bat :app:testDebugUnitTest --tests "*PptxTextLayoutParserUnitTest" --console=plain

# lint
.\gradlew.bat :app:lintDebug --console=plain
```

SDK-XML version warnings and the `dagger.*` kapt "options not recognized"
warning are **pre-existing and harmless**. Do not try to fix them.

> R8/minified release builds are slow and need the 6 GB heap. Do not run
> `assembleRelease` or `bundleRelease` as part of this work; `compileDebugKotlin`
> + `testDebugUnitTest` + `lintDebug` is the verify gate.

---

## 2. Baseline — FIXED, do not redo

The test source set previously **did not compile** on `main`:

```
e: app/src/test/java/com/karnadigital/omnisuite/feature/viewer/PptxRendererUnitTest.kt:231:12
   Suspend function 'updateShapeTextSync' should be called only from a coroutine
   or another suspend function
e: app/src/test/java/com/karnadigital/omnisuite/feature/viewer/PptxRendererUnitTest.kt:241:12
   (same)
```

`PptxViewerViewModel.updateShapeTextSync` is a `suspend fun`; the test called it
from a plain `@Test fun`. Already resolved by wrapping both calls in
`runBlocking { }`. The production signature was **not** changed to accommodate
the test.

Current baseline, verified:

```
.\gradlew.bat :app:testDebugUnitTest   ->  BUILD SUCCESSFUL
9 test classes, 45 tests, 0 failures, 0 errors
```

The test count must only ever go **up** from here, and must never regress below
45 green.

---

## 3. Architecture as it stands

There are **three independent PPTX code paths** and no shared engine interface.

| # | Path | File | Output | Used by |
| --- | --- | --- | --- | --- |
| 1 | Viewer parser | `feature/viewer/PptxViewerViewModel.kt` (~3650 lines) | `PptxPresentation` model, drawn with Compose primitives | CONTINUOUS + PAGER modes |
| 2 | Bitmap rasteriser | `core/engine/document/OfficeConverter.kt` (~1981 lines) | `List<Bitmap>` via `android.graphics.Canvas` | GRID + slideshow thumbnails, PPT→PDF export |
| 3 | Print | `feature/viewer/PptxViewerScreen.kt:2995-3060` `PptxPrintDocument` | text-only `PdfDocument` | print action |

Only `core/util/DrawingMlGeom.kt` (`custGeom` parsing) is genuinely shared.
`OfficeConverter` has **no table support at all**, so GRID/slideshow and
PPT→PDF export silently drop every table while the scroll view shows them.

Parsing is done with `javaClass.getMethod(...).invoke(...)` + `catch (_: Throwable)`
because POI's XMLBeans types differ per shape subtype. This is deliberate
tolerance for malformed files — keep it, but stop letting it hide real gaps.

### Key entry points

| What | Where |
| --- | --- |
| Load file | `PptxViewerViewModel.kt:2082` `loadPptxFile` |
| Parse core | `PptxViewerViewModel.kt:1596` `parseAllSlides` |
| Shape dispatch | `PptxViewerViewModel.kt:1700-2051` (pictures / text / tables) |
| Slide size | `PptxViewerViewModel.kt:1575` `getSlideDimensionsEmu` |
| Normalized bounds | `PptxViewerViewModel.kt:254` `getShapeNormalizedBounds`, `:322` `getXmlShapeBoundsNormalized` |
| `bodyPr` | `PptxViewerViewModel.kt:438` `extractBodyPr` |
| Runs | `PptxViewerViewModel.kt:1340` `extractParagraphRunsWithLineBreaks` |
| Typeface | `PptxViewerViewModel.kt:1108` `extractRunTypeface` |
| Table geometry | `PptxViewerViewModel.kt:550` `extractTableGeometry` |
| Image rels | `PptxViewerViewModel.kt:602` `extractBlipEmbedId`, `:653` `resolvePictureBytesFromBlipId`, `:714` `extractPictureDataFromShape` |
| Slide renderer | `PptxViewerScreen.kt:2202` `SlideCardItem` |
| Text renderer | `PptxViewerScreen.kt:2630` `TextShapeItem` |
| Font mapping | `PptxViewerScreen.kt:2581` `resolveFontFamily` |

### Model classes (all at top of `PptxViewerViewModel.kt`)

`PptxTextRun:37`, `Insets:55`, `AutoFitMode:63`, `PptxParagraph:65`,
`ShapeGeometryType:87`, `ShapeBorder:95`, `PptxTextShape:100`, `PptxImage:135`,
`PptxSlide:151`, `PptxPresentation:164`, `PptxLoadState:166`.

Model and parser currently live in the same file as the ViewModel — Phase 4
separates them.

---

## 4. POI 5.2.5 API facts (verified via `javap` — do not re-derive)

These were confirmed against the actual jars. Trust them; they explain most of
the bugs.

| Fact | Consequence |
| --- | --- |
| `XSLFShape.getXmlObject()` is `public final`, returns field `_shape`. | Most reflection paths work for `XSLFSimpleShape`, `XSLFPictureShape`, `XSLFConnectorShape`, `XSLFGroupShape`. |
| `XSLFGraphicFrame` passes its `CTGraphicalObjectFrame` as `_shape`, so `getXmlObject()` returns the frame. | `CTGraphicalObjectFrame` has `getXfrm()` and `getGraphic()` but **no `getTbl()`** → `extractTableGeometry` always returns `null`. See Phase 2. |
| `XSLFTable extends XSLFGraphicFrame implements TableShape`; has **`public CTTable getCTTable()`**, `getNumberOfRows()`, `getNumberOfColumns()`, `getCell(r,c)`, `getRows()`, `getColumnWidth(int)` / `getRowHeight(int)` (**points**, not EMU). | Use `getCTTable()` for EMU-accurate `gridCol`/`tr@h`. |
| `XSLFTableCell extends XSLFTextShape`; has `getGridSpan()`, `isMerged()`, `getVerticalAlignment()`, `getAnchor()`, `getBorderColor(TableCell.BorderEdge)`, `getBorderWidth(...)`. | Enough to fix merges, vertical alignment and real borders. |
| `XSLFTextRun.getFontSize()` returns `java.lang.Double` and **returns `null` when the run has no explicit `<a:rPr sz>`**. | Inherited placeholder text hits the hardcoded 18/24/14 pt fallbacks. This is the single biggest visual bug. |
| `XSLFTextRun.getFontFamily()` returns `null` when there is no explicit `<a:latin>`; theme refs look like `+mj-lt` / `+mn-lt`. | Theme fonts are lost; everything falls back to Roboto. |
| `XSLFGraphicFrame.getAnchor()` returns `java.awt.geom.Rectangle2D` in **points**, and returns **`null` when `xfrm` is absent**. | Table/chart/SmartArt bounds can be null → the parser invents fake bounds. |
| `XMLSlideShow.getCTPresentation()` is **`public`**. | `getSlideDimensionsEmu` works correctly. **Do not "fix" it.** It is not a bug. |
| `XSLFSlide.getXmlObject()` → `CTSlide`; `XSLFTextParagraph.getXmlObject()` → `CTTextParagraph`. | Both public. |
| There is **no `fetchXmlObject` anywhere in POI.** | The second branch of `getXmlObjectReflection` (`PptxViewerViewModel.kt:221`) is dead code. |
| `org.apache.poi.sl.usermodel.TableShape` lives in the **poi** jar, not `poi-ooxml`. | Relevant if an agent adds `javap` classpaths. |

---

## 5. Symptom → cause map (from the screenshots)

Reference render (Image 1) vs OmniSuite (Image 2), same `.pptx`.

| # | Reference | OmniSuite | Root cause |
| --- | --- | --- | --- |
| 1 | Serif title, ~12 pt body | Roboto, body ~1.8× too large, overflowing boxes | Inherited font size + family dropped → hardcoded 18/24/14 pt (P0) |
| 2 | `Problem Statement ID –SIH26080` tight | `Problem   Statement   ID` spread full-width, wrong wrap point | `TextAlign.Justify` passed straight to Compose |
| 3 | `○` bullets, small, inline | Large blue `•` on every line, wrong baseline | `buChar`/`buNone`/`buClr`/`buSzPct` ignored; marker drawn at paragraph max size |
| 4 | Text vertically centred in cards | Text hard-clipped mid-word | `bodyPr/@anchor` ignored + autofit floor 0.65 + `.clip()` |
| 5 | Faint hexagon watermark | Opaque, mis-scaled, smeared band | `alphaModFix` ignored; bg forced `0,0,1,1` + `FillBounds` |
| 6 | `TECHNICAL APPROACH` fits | `…ICAL APPROACH` cut off left | Forced centre alignment + oversized text |
| 7 | Layer/Stack table rendered | Table region empty | `extractTableGeometry` always `null` → equal splits; plus layout-table bounds |
| 8 | Footer legible | Micro-text in a white chip | Font + anchor again |

---

## 6. Phase 1 — Text metrics (highest visual impact, do first)

### 1.1 Style inheritance resolver — NEW FILE

Create `core/util/PptxStyleResolver.kt` (or `core/engine/document/` if you prefer
that package; keep it near `DrawingMlGeom.kt`).

Implement the OOXML text-property inheritance chain. For a given run, resolve in
order, first hit wins:

1. `<a:rPr>` on the run itself (`sz`, `b`, `i`, `u`, `latin/@typeface`, `solidFill`)
2. `<a:pPr><a:defRPr>` on the paragraph
3. `<a:lstStyle><a:lvl{N}pPr><a:defRPr>` on the **shape** (`p:txBody/a:lstStyle`),
   where `N` = `bulletLevel + 1`, clamped to 1..9
4. Matching placeholder's `<a:lstStyle>` on the **slide layout**, if the shape is a
   placeholder
5. `<p:txStyles>` on the **slide master**: `p:titleStyle` for titles,
   `p:bodyStyle` for bodies, `p:otherStyle` otherwise — again by `lvl{N}pPr`
6. `<p:defaultTextStyle>` in `presentation.xml` (`lvl{N}pPr`)
7. Built-in defaults: **18 pt body, 24 pt title** — but only as the last resort,
   after 1-6 have been tried

Expose a small data holder, e.g.

```kotlin
data class ResolvedRunStyle(
    val fontSizePt: Float?,
    val typeface: String?,       // raw, may be "+mj-lt"
    val isBold: Boolean?,        // null = inherit
    val isItalic: Boolean?,
    val isUnderline: Boolean?,
    val colorHex: String?
)
```

Walk the chain with XMLBeans, not string regex. `XSLFTextRun.getXmlObject()` is
public and returns the run's `CTRegularTextRun`; the shape's
`XSLFSimpleShape.getXmlObject()` returns `CTShape` (`getTxBody()`), the layout's
`XSLFSlideLayout.getXmlObject()` returns `CTSlideLayout`, the master's
`XSLFSlideMaster.getXmlObject()` returns `CTSlideMaster`, and
`XMLSlideShow.getCTPresentation().getDefaultTextStyle()` gives step 6.

### 1.2 Theme font scheme — NEW FILE or same file

`extractThemeColorScheme` already exists at `PptxViewerViewModel.kt:880`. Add the
parallel **font** scheme: read `<a:fontScheme><a:majorFont><a:latin typeface>` and
`<a:minorFont><a:latin typeface>` from the theme part
(`ppt.slideMaster.theme` → `XSLFTheme.getXmlObject()`, or
`XSLFShape.getTheme()`).

Then in `extractRunTypeface` (`PptxViewerViewModel.kt:1108`):
- **Remove the `family.startsWith("+m")` rejection at line 1110 and 1121.**
- Instead, keep the `+mj-lt` / `+mn-lt` token and resolve it through the font
  scheme (`+mj-lt` → major latin, `+mn-lt` → minor latin, `+mj-ea` / `+mn-ea` →
  east-asian, `+mj-cs` / `+mn-cs` → complex-script).

### 1.3 Stop fabricating font sizes

Replace the hardcoded fallbacks:
- `PptxViewerViewModel.kt:1366`, `:1389`, `:1419`, `:1434` — the
  `(if (isTitle) 24f else 14f)` fallbacks inside
  `extractParagraphRunsWithLineBreaks`
- `PptxViewerViewModel.kt:1791` `defaultFontSize = if (isTitle) 26f else 14f`
- `PptxViewerViewModel.kt:1870` `fontSizePt = if (isTitle) 24f else 14f`
- `PptxViewerViewModel.kt:1969`, `:1995` — table cell `?: 14f`
- `PptxViewerScreen.kt:2672` `defaultFontSizePt = if (isTitle) 24f else 18f`

All of these must consult the resolver. Keep the final constant only as the
step-7 default. Note the current spread is 14/18/24/26 pt for what should be one
value — that inconsistency is itself part of the bug.

### 1.4 `bodyPr/@anchor` (vertical alignment)

Add to `extractBodyPr` (`PptxViewerViewModel.kt:438`): read
`CTTextBodyProperties/@anchor` (`"t"` / `"ctr"` / `"b"` / `"just"`), default `"t"`.
Add a `VerticalAnchor` enum to the model and a `verticalAnchor` field on
`PptxTextShape`.

In `TextShapeItem` (`PptxViewerScreen.kt:2864`) replace
`verticalArrangement = if (isEllipseBadge) Arrangement.Center else Arrangement.Top`
with a lookup driven by `shape.verticalAnchor` (ellipse badge keeps `Center` as
its own case only if you cannot determine an anchor).

Also honour `<a:bodyPr rot="vert">` (vertical text) and `anchorCtr` if cheap;
otherwise record them and note as known-unimplemented.

### 1.5 Bullets

In the paragraph loop at `PptxViewerViewModel.kt:1803-1825`:
- **`<a:buNone/>` must win.** Currently `hasBullet = bulletLevel > 0 || ...`
  (line 1817) fires for any paragraph at `indentLevel > 0` even when the file
  explicitly says `buNone`. Add a `buNone` check.
- Keep `bulletChar` as the raw `buChar` (do not collapse unknown chars to `•`;
  the current `else -> "•"` at line 1824 destroys Wingdings/Symbol glyphs).
- Parse `<a:buClr>` (colour), `<a:buSzPct>` (size % of text), `<a:buSzPts>`,
  `<a:buFont>` (font for the glyph) and carry them on `PptxParagraph`.

In `PptxViewerScreen.kt:2883-2896` the marker `Text` currently uses
`MaterialTheme.typography.bodyMedium` at `pl.maxFontSizeSp` and is always
`FontWeight.Bold` + theme primary. Change it to use the parsed `buSzPct`/`buClr`/
`buFont` and align it to the text baseline (`Row(verticalAlignment =
Alignment.Top)` + `Modifier.alignByBaseline()` where possible).

### 1.6 Justification

`PptxViewerScreen.kt:2712` maps `"JUSTIFY"` → `TextAlign.Justify`. Compose
justifies every non-final wrapped line by distributing extra space **between
words**, which is what produced symptom #2. Map `JUSTIFY` (and `DISTRIBUTE`, if
you ever surface it) to `TextAlign.Start` **unless** the shape's effective width
is large enough that justify is visually correct. Simplest correct behaviour:
treat justify as start, and log it. Do not silently mis-render.

Also stop the parser rewriting alignment at `PptxViewerViewModel.kt:1831-1832`:
`TextAlign.LEFT -> if (isTitle && shapeWidthVal >= 0.4f) "CENTER" else "LEFT"`.
The parser must not invent alignment. `PptxViewerScreen.kt:2709` also force-
centres every `isTitle` paragraph — remove that and trust the file.

### 1.7 Autofit floor

`PptxViewerScreen.kt:2806` `val minScale = 0.65f` and the height-only binary
search at `:2810-2815`. Lower the floor to `0.5f`, increase iterations to 10, and
also constrain **width** (a single unbreakable token currently overflows
horizontally with no wrap). Keep the hard `.clip()` at `:2839` as the last resort
but stop relying on it.

### Tests for Phase 1 — new file `PptxTextInheritanceUnitTest.kt`

Build real `.pptx` with POI and assert on `parseAllSlides` output (mirror the
`Unsafe.allocateInstance` helper from `PptxTextLayoutParserUnitTest.kt:29-40` —
factor it into a shared test util so both classes use it).

1. A run with **no** explicit `sz` inside a shape whose `a:lstStyle` sets
   `lvl1pPr/defRPr sz="1100"` → resolved `fontSizePt == 11f`, not 14/18/24.
2. Theme font: set the theme's `minorFont/latin typeface` to `"Georgia"`, put a
   run with `<a:latin typeface="+mn-lt"/>`, assert `run.fontFamily == "Georgia"`.
3. `<a:buNone/>` on a paragraph with `indentLevel = 1` → `hasBullet == false`.
4. `<a:buChar char="o"/>` with `<a:buFont typeface="Wingdings"/>` → `bulletChar`
   preserved as `"o"` (not collapsed to `•`).
5. `<a:bodyPr anchor="ctr">` → `verticalAnchor == VerticalAnchor.CENTER`.
6. A `LEFT`-aligned wide title → `paragraph.alignment == "LEFT"` (not rewritten
   to `CENTER`).
7. Master `p:bodyStyle/lvl1pPr/defRPr sz="2000"` inherited by a placeholder with
   no `lstStyle` → `fontSizePt == 20f`.

Verify: `.\gradlew.bat :app:testDebugUnitTest --tests "*Pptx*"` green, then full
`:app:testDebugUnitTest`, then `:app:compileDebugKotlin`.

---

## 7. Phase 2 — Geometry

### 2.1 Kill the fabricated bounds

`PptxViewerViewModel.kt:1717-1720` (images) and `:1772-1775` (text shapes) invent
positions when `getShapeNormalizedBounds` returns `null`:

```
left 0.05, top 0.3, width 0.6, height 0.4
left 0.05, top 0.22 + bodyCount*0.12, width 0.9, height 0.35
```

This is what dumps unresolvable shapes into a fake column. **Replace with: skip
the shape and log it** (`android.util.Log.d("PptxParser", "unresolved bounds …")`).
Silently inventing layout is worse than omitting it.

### 2.2 Fix the sliver collapse

`PptxViewerViewModel.kt:400-405` clamps `x`/`y` into `[0,1]` but leaves
`w`/`h` unclamped. Then `:1885`:

```kotlin
val clampedWidth = shapeWidthVal.coerceAtMost((1f - shapeLeft).coerceAtLeast(0.05f))
```

A shape whose `x` clamps to `1.0` becomes `coerceAtLeast(0.05f)` → a **5 %-wide
sliver**. Clamp the rect once, coherently: compute
`right = min(x + w, 1f)`, then `w = right - x`; and clamp `x` into `[0, 1 - minW]`
before dividing. Delete the second clamp in the ViewModel.

### 2.3 Table geometry — the big one

`extractTableGeometry` (`PptxViewerViewModel.kt:550-592`) calls
`xml.javaClass.getMethod("getTbl")`. For a table, `getXmlObject()` returns
`CTGraphicalObjectFrame`, which has **no `getTbl()`** → the reflective call throws
`NoSuchMethodException` → caught → returns `null` → `:1930` and `:1938` fall back
to `List(numCols){ slideWidthEmu/numCols }` and
`List(numRows){ slideHeightEmu/numRows }`, i.e. **equal column widths and equal
row heights, ignoring the file**.

Fix: when `shape is XSLFTable`, use the public `shape.ctTable` to read
`<a:tbl><a:tblGrid><a:gridCol w>` and `<a:tr h>`. Keep the existing
`XmlObject`-reflection path as a fallback for anything else.

While in the table branch (`:1918-2048`) also fix:
- **Cell borders** are hardcoded `ShapeBorder("#CBD5E1", 1f)` at `:2030`. Read the
  real `<a:tcPr><a:lnL/lnR/lnT/lnB>` (or `XSLFTableCell.getBorderColor` /
  `getBorderWidth`). Apply the table style / `firstRow` banding if cheap.
- **Cell alignment** is hardcoded `"LEFT"` at `:1980` and `:2011`. Use
  `XSLFTableCell.getVerticalAlignment()` and the paragraph's real `algn`.
- **Cell font family** is never set — `PptxTextRun(crText, isB, isI, isU, cHex, fS)`
  at `:1970` omits the `fontFamily` argument. Pass `extractRunTypeface(cr)`.
- **Cell font size** is hardcoded `14f` at `:1969`/`:1995` — route through the
  Phase 1 resolver.
- **Merges**: honour `gridSpan`, `rowSpan`, `hMerge`, `vMerge`
  (`XSLFTableCell.getGridSpan()` / `isMerged()`). Skip cells that are continuation
  targets and widen the origin cell.
- Run `extractTableGeometry` for tables that come from the **layout** as well —
  `getAnchor()` returns `null` when `xfrm` is absent, and the table is then placed
  at the fabricated fallback from 2.1.

### 2.4 `getXmlObjectReflection` dead branch

`PptxViewerViewModel.kt:216-228`: `getDeclaredMethod("fetchXmlObject")` does not
exist in POI. Remove the second `try` so real failures surface, and keep
`getXmlObject()`.

Also: `getShapeNormalizedBounds` Strategy 3 (`:291-311`) clamps x/y/w/h
independently, which can produce `x + w > 1`. Apply the same coherent clamp as 2.2.

### Tests for Phase 2 — extend `PptxTextLayoutParserUnitTest.kt`

1. Build a 2-column table via `XSLFTable` with `gridCol` widths of 3000000 and
   7000000 EMU. Assert the emitted `table_cell_r_c` shapes have
   `shapeWidth` fractions ≈ `0.30` and `0.70` (**not** 0.5/0.5).
2. Assert the cell's `shapeBorder.strokeColorHex` is the file's colour, not
   `#CBD5E1`.
3. A `gridSpan=2` first cell → exactly one cell shape emitted for that origin.
4. A shape with a real `xfrm` at x = 8 000 000 on a 12 192 000 EMU slide →
   `shapeLeft` ≈ 0.656, and `shapeWidth` unchanged (**regression for the 5 %
   sliver**).
5. A placeholder whose layout has no `xfrm` → assert the shape is **absent** from
   `textShapes` (regression for the fabricated-grid fallback), i.e.
   `textShapes.none { it.shapeLeft == 0.05f && it.shapeTop == 0.22f }`.

Verify: `:app:testDebugUnitTest --tests "*Pptx*"` then full suite.

---

## 8. Phase 3 — Images & background

### 3.1 Relationship resolution against the owning part

`extractPictureDataFromShape(shape, slide)` is called at
`PptxViewerViewModel.kt:1707` and **always** passes the slide. Pictures inherited
from the **layout** or **master** have `rId`s registered against *those* parts, so
`resolvePictureBytesFromBlipId` (`:653`) misses and the artwork silently vanishes.
`:1627` already tries the right part for backgrounds — do the same for shapes.

Resolve against, in order: the shape's own sheet (`shape.getSheet()`), the
`XSLFPictureData` relation, the package part relationship, and finally the
presentation's `pictureData` list. Also delete the dead
`pd.packagePart?.partName?.name?.contains(blipId)` fallback at `:677` — part names
are `/ppt/media/imageN.png`, they never contain `rIdN`.

### 3.2 Blip effects

`<a:blip>` children are ignored entirely. Parse and carry:
- `<a:alphaModFix amt>` → alpha multiplier (this is why the template's hexagon
  watermark renders opaque instead of faint — symptom #5)
- `<a:grayscl/>`, `<a:biLevel val>`

### 3.3 Non-destructive `srcRect`

`savePicBytesToTempFile` (`PptxViewerViewModel.kt:788-822`) **bakes** the crop
into the bitmap by re-encoding to PNG. That is lossy (always PNG, quality 100,
no alpha-preserving format choice) and makes the crop impossible to re-derive.
Carry `PicCrop` on `PptxImage` and apply it at draw time.

While there: the cache key at `:790` is `dataBytes.contentHashCode().toString()` —
a **32-bit** hash. Collision across a large deck shows the wrong image. Use a
SHA-1 hex digest.

### 3.4 `ContentScale` for true pictures

`PptxViewerScreen.kt:2328` uses `ContentScale.Fit` for `<p:pic>` and
`ContentScale.Crop` for shape-fills. PowerPoint **stretches** a `<p:pic>` to its
`a:xfrm` extents; it does not letterbox. Once `srcRect` is applied at draw time
(3.3), use `FillBounds` for true pictures.

### 3.5 Background picture

- `PptxViewerViewModel.kt:1630`, `:1731`, `:1737`, `:1744` all force
  `PptxImage(path, 0f, 0f, 1f, 1f)`, discarding real bounds.
- `PptxViewerScreen.kt:2256` then draws it `Modifier.fillMaxSize()` with
  `ContentScale.FillBounds`, which stretches and ignores aspect ratio, and ignores
  `<a:tile>` / `<a:stretch><a:fillRect>`.
- Worse, a full-bleed `<p:pic>` is **silently discarded** when a background
  already exists — the `else` branch at `:1730`/`:1736`/`:1743` is never taken.
  Content disappears.

Fix: keep every picture in the z-ordered list at its real bounds. If a dedicated
`backgroundImage` slot is kept for `<p:bg><p:blipFill>`, honour the stretch/tile
mode and do **not** let it swallow `<p:pic>` content.

### 3.6 Rotation and flip

`PptxImage` has no `rotationDegrees` / `flipH` / `flipV`. Add them from
`<a:xfrm rot= flipH= flipV>` and apply in the renderer (the text path already does
`Modifier.rotate` at `PptxViewerScreen.kt:2838`). This is why the template's
rotated green swoosh renders as a hard unrotated rectangle.

### 3.7 Performance: stop serialising XML 4× per shape

`xml.toString()` is called in `extractBlipEmbedId:603`, `extractBlipCrop:690` and
`extractCustomGeomPath:772`. Serialise once per shape and pass the string down.
`extractBlipEmbedId` Priority 3 (`:641`) regex-matches any `r:embed`/`r:link` in the
serialised XML, which will happily match a hyperlink `r:link` and turn a
hyperlinked text shape into a "picture". Tighten it to the `a:blip` element.

### Tests for Phase 3 — new file `PptxImageParsingUnitTest.kt`

1. A picture on the **layout** (not the slide) with a layout-scoped `rId` →
   assert it appears in `slide.images` with a resolvable `filePath`.
2. `<a:blip><a:alphaModFix amt="20000"/></a:blip>` → assert the parsed
   `alphaModFactor == 0.2f`.
3. `<a:srcRect l="10000" t="0" r="0" b="0"/>` → assert `PptxImage.crop.l == 0.1f`
   **and** that the cached temp file bytes are byte-identical to the input (i.e.
   no destructive re-encode).
4. A full-bleed `<p:pic>` on a slide that also has a `<p:bg>` picture → assert
   **both** survive (currently one is dropped).
5. `<a:xfrm rot="2700000"/>` (45°) on a picture → assert `image.rotationDegrees == 45f`.
6. Cache-key test: two different images with equal `contentHashCode()` must not
   collide (assert distinct temp files after switching to SHA-1).

Verify: `:app:testDebugUnitTest --tests "*Pptx*"` then full suite.

---

## 9. Phase 4 — Architecture

### 4.1 Extract the parser out of the ViewModel

`parseAllSlides` is `private` inside a 3650-line `@HiltViewModel`. Both existing
test classes have to use `sun.misc.Unsafe.allocateInstance` to reach it — a clear
signal of over-coupling.

Create `core/engine/document/PptxParser.kt` as a plain class:

```kotlin
class PptxParser @Inject constructor() {
    fun parse(ppt: SlideShow<*, *>): List<PptxSlide>
}
```

Move the model classes (`PptxTextRun` … `PptxLoadState`) into
`core/engine/document/PptxModel.kt`. `PptxViewerViewModel` delegates. Then rewrite
the three test helpers to just do `PptxParser().parse(ppt)` — delete all
`Unsafe` reflection.

**Do this first in Phase 4** so the rest of the phase is testable.

### 4.2 Deduplicate geometry

`getShapeNormalizedBounds` / `getXmlShapeBoundsNormalized` / `tryGetXfrm` /
`extractLongAttr` exist **twice** — `PptxViewerViewModel.kt:254/322/412/423` and
`OfficeConverter.kt:1397/1457/1555`. Promote to a shared
`core/util/PptxGeometry.kt` (alongside `DrawingMlGeom.kt`) and have both call it.

### 4.3 Unify the render paths

`OfficeConverter.renderPptxToBitmaps` has no table support, so GRID/slideshow
render a different document than the scroll view. Make `OfficeConverter` consume
the same parsed `PptxPresentation` from Phase 4.1 rather than re-walking POI. Then
`PptxPrintDocument` (`PptxViewerScreen.kt:2995-3060`) can go too, or delegate.

While there: `renderPptxToBitmaps` is kicked off on **every** open
(`PptxViewerViewModel.kt:2131-2143`) and its output is only consumed by GRID and
slideshow. Make it lazy — only render when one of those modes is actually entered.

### 4.4 Charts and SmartArt

`p:graphicFrame` with `c:chart` and SmartArt (`dgm:`) are **silently dropped** by
both paths. `XSLFChart` exists in POI; SmartArt needs a fallback to the
pre-rendered `mc:AlternateContent` / drawing group if present, otherwise render a
placeholder frame. Also unimplemented today: `<a:effectLst>` (shadows — visible
in the reference as soft card shadows), `numCol` multi-column text,
`wrap="none"`, `vert` text, connector arrowheads (`a:headEnd`/`a:tailEnd`),
gradient fill in the Compose path, `a:tile` fills, soft edges, and EMF/WMF blips
(Coil cannot decode them at all).

Scope 4.4 to charts + a SmartArt placeholder. Log the rest as known gaps.

### Tests for Phase 4

- Rewrite `PptxTextLayoutParserUnitTest`, `PptxRendererUnitTest`,
  `PptxImageParsingUnitTest` to use `PptxParser()` directly; **delete all
  `Unsafe` usage and the reflective field pokes** in
  `PptxRendererUnitTest.kt:194-227`.
- Add a test that `OfficeConverter` output for a table-bearing slide is non-blank
  (regression: tables were absent from the bitmap path).
- Add a test that a chart graphicFrame produces a non-empty render list entry.

---

## 10. Definition of done

- `.\gradlew.bat :app:compileDebugKotlin` — clean
- `.\gradlew.bat :app:testDebugUnitTest` — all green (baseline had 0 green; the
  suite did not even compile)
- `.\gradlew.bat :app:lintDebug` — no new issues
- Every bullet in §6/§7/§8/§9 has a corresponding test
- Manual check: open the SIH template deck and confirm, per slide, that
  (a) body text size matches the reference, (b) the title is not clipped,
  (c) the hexagon watermark is faint and correctly scaled,
  (d) the Layer/Stack table renders with its real 2-column proportions and header
  styling, (e) footer text is legible.
- Update `docs/agents.md` (PowerPoint viewer row + quick-fix index) and
  `docs/engines.md` (`OfficeConverter` contract) to reflect the new
  `PptxParser` and the unified render path.
- Tick items off in this document as each phase lands.

## 11. Style constraints

- Minimal diffs. No speculative features beyond §9.4.
- Match the surrounding style: 4-space indent, `private fun` helpers, KDoc on
  non-obvious functions (that is the existing convention in these files).
- Keep the `try { … } catch (_: Throwable) { }` tolerance for malformed files, but
  do not let it silently swallow reachable data — add a `Log.d` where a shape is
  dropped.
- Do not commit `*.jks`, `keystore.properties`, `play-store-key.json`.
- Do not bump versions, touch CI, or change signing.
