# PPTX Viewer Subsystem — Design Document

**Status:** Phase 0 (design — implementation follows this document)
**Scope:** Complete rewrite of the PPTX viewer: parser, data model, and renderer.
**Date:** 2026-08-30

---

## 0. Guiding Principles

1. **Decouple parsing from rendering.** A self-contained `ParsedSlide`/`ParsedShape` model (no Apache POI types) is the single contract between parser and renderer. Either side can be tested, replaced, or reasoned about independently.
2. **One rendering implementation for all views.** Continuous scroll, pager, slideshow, thumbnail strip, and grid all compose the same `ParsedSlide` composables. No duplicated shape-walking rasterizer.
3. **No guessing.** A shape whose bounds cannot be resolved through the full chain (own xfrm → layout placeholder → master placeholder) is **omitted**, never drawn at a fabricated rectangle. An image that fails to decode is omitted.
4. **White default background.** Slide content renders on white unless the source specifies otherwise. The app's Material theme surface color never bleeds into slide content.

---

## 1. Data Model

All types live in `PptxSlideParser.kt` and depend on nothing from Apache POI.

### 1.1 Coordinates

```kotlin
/** Normalized slide-space rectangle: all values in 0.0..1.0 relative to slide width/height. */
data class NormRect(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}
```

Bounds are **always normalized** (0..1) so the renderer is agnostic to the slide's physical pixel/EMU size.

### 1.2 Text

```kotlin
enum class TextAlignment { LEFT, CENTER, RIGHT, JUSTIFY }

data class ParsedTextRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    /** Null means "inherit" — renderer applies its own default (dark text). */
    val colorHex: String? = null,
    /** Font size in points; 0f means "inherit from placeholder style". */
    val sizePt: Float = 0f,
    val fontFamily: String? = null,
)

data class ParsedParagraph(
    val runs: List<ParsedTextRun>,
    val alignment: TextAlignment = TextAlignment.LEFT,
    /** Indent level 0 = body; 1+ = nested bullet. */
    val bulletLevel: Int = 0,
    /** Explicit bullet glyph, e.g. "•", "–", "→". Empty = renderer default. */
    val bulletChar: String = "",
)
```

### 1.3 Shape content (sealed)

```kotlin
sealed interface ShapeContent

/** Text box / placeholder with one or more paragraphs. */
data class TextContent(val paragraphs: List<ParsedParagraph>) : ShapeContent

/** Raster image already extracted to bytes (PNG/JPEG/etc.). */
data class ImageContent(val bytes: ByteArray, val contentType: String?) : ShapeContent

enum class VectorKind { RECTANGLE, ROUNDED_RECTANGLE, ELLIPSE, TRIANGLE, DIAMOND, LINE, OTHER }

/** Simple vector shape with optional fill + stroke. */
data class VectorContent(
    val kind: VectorKind,
    val fillHex: String? = null,
    val strokeHex: String? = null,
    val strokeWidthPt: Float = 0f,
) : ShapeContent

/** Group of child shapes. Bounds on the NormRect hold the group extent;
 *  children carry their own already-resolved slide-space NormRects. */
data class GroupContent(val children: List<ParsedShape>) : ShapeContent

data class ParsedTableCell(val text: String, val fillHex: String? = null)
data class TableContent(val rows: List<List<ParsedTableCell>>) : ShapeContent

/** Shape present in the source but with no renderable content (e.g. a pure anchor). */
data object DecorativeContent : ShapeContent
```

### 1.4 Shape

```kotlin
data class ParsedShape(
    val bounds: NormRect,   // resolved slide-space position; shape is omitted if null upstream
    val content: ShapeContent,
    /** Z-order: lower = painted first (back). Matches source XML order. */
    val zIndex: Int,
)
```

The renderer iterates shapes in list order (which is z-order) and paints back-to-front. No separate z-sorting step.

### 1.5 Slide background

```kotlin
sealed interface SlideBackground
data object BgNone : SlideBackground          // renderer uses white
data class BgSolid(val colorHex: String) : SlideBackground
data class BgGradient(val stops: List<Pair<Float, String>>) : SlideBackground  // position,colorHex
data class BgImage(val bytes: ByteArray, val contentType: String?) : SlideBackground
```

### 1.6 Slide & Presentation

```kotlin
data class ParsedSlide(
    val index: Int,                          // 0-based
    val background: SlideBackground,
    val shapes: List<ParsedShape>,           // ordered back-to-front
    val speakerNotes: String? = null,
)

data class ParsedPresentation(
    val slides: List<ParsedSlide>,
    val widthEmu: Long,                      // presentation-level sldSz cx
    val heightEmu: Long,                     // presentation-level sldSz cy
) {
    val aspectRatio: Float get() = if (heightEmu > 0) widthEmu.toFloat() / heightEmu.toFloat() else 16f / 9f
}
```

`widthEmu`/`heightEmu` are kept so the renderer can convert EMU→pixels for any EMU-native image/anchor math, and so callers can display slide-size metadata. The shapes themselves are already normalized and need no EMU conversion.

---

## 2. Parser — `PptxSlideParser`

Single entry point:

```kotlin
class PptxSlideParser @Inject constructor() {
    fun parse(slideShow: XMLSlideShow): ParsedPresentation
}
```

(Uses Hilt `@Inject` so it can replace the static-object pattern flagged in audit H7; if DI wiring is already satisfied by a provider, the constructor stays.)

### 2.1 Slide dimensions

- Read `slideShow.ctPresentation.sldSz.cx` / `.cy` via the public POI property `getCTPresentation()` (available on `XMLSlideShow`).
- `sldSz` is a `CTSlideSize` whose `cx`/`cy` are `long` (primitive) in POI 5.2.5 — when accessed through a generic反射 path they box to `java.lang.Long`, which **is** a `Number`. The prior bug arose from an intermediate holder that was NOT a Number. To be safe, the parser reads through a small helper that:
  1. Tries the typed property first (`CTSlideSize.getCx()` → `long`).
  2. Falls back to reflection with explicit `Number` check and string-parse guard.
- Fallback default only if the value is genuinely unreadable: **9144000 × 5143500 EMU** (standard 16:9). A default is acceptable here because slide *size* missing entirely is a degenerate file; this is not a shape-bounds fallback.

### 2.2 Shape bounds resolution chain

For each shape, resolve a `NormRect` in this order. **Stop at the first success.**

1. **Own `<a:xfrm>`** — read `off.x/off.y/ext.cx/ext.cy` from the shape's XML (`spPr.xfrm` or top-level `xfrm` for group/pic). Compose with any group-ancestor transforms (§2.3). If `cx>0 && cy>0` → success.
2. **Placeholder inheritance** — if the shape has a `<p:ph>` (type and/or idx), match against the slide's layout, then master:
   - Match by **type AND idx** (idx is the strong key; type disambiguates). Compatible-type rules: `TITLE≈CENTERED_TITLE`, `BODY≈SUBTITLE≈CONTENT`. Master matching uses the relaxed master rules (idx 0/1 maps to master body).
   - Take the **first ancestor in the chain (layout → master) that has a resolvable xfrm**. If the layout match has no xfrm, continue up to master — do NOT stop at the first *match*, stop at the first match **with bounds**.
3. **Unresolved → return null.** The shape is **omitted** from `ParsedSlide.shapes`.

This replaces the old 3-strategy chain that ended in a `getAnchor()` reflection fallback (the source of two regressions). There is **no** `getAnchor` fallback, ever.

### 2.3 Group shape transforms

- A group shape (`CTGroupShape` / `XSLFGroupShape`) carries `grpSpPr.xfrm` with `off/ext/chOff/chExt`.
- Transform composition for a child at `(cx,cy,cw,ch)` in group child-space:

```
slideX = offX + (childX - chOffX) * extCx / chExtCx
slideY = offY + (childY - chOffY) * extCy / chExtCy
slideW = childW * extCx / chExtCx
slideH = childH * extCy / chExtCy
```

- **Nested groups:** the parser walks groups recursively, accumulating the ancestor transform list. Each child's raw bounds are composed with the full ancestor chain before normalization. This handles groups-at-any-depth (not just one level like the prior code).
- The resolved `NormRect` on each `ParsedShape` is already in slide-space, so the renderer knows nothing about groups — it only sees flat shapes with correct bounds. Group `ParsedShape`s use `GroupContent` (children already resolved) so the renderer can also recurse if it wishes, but the flat normalized bounds are authoritative.

### 2.4 Image extraction

For each shape, try sources in order; first non-empty bytes win:

1. `XSLFPictureShape.pictureData` (POI public API).
2. `shape.spPr.blipFill.blip.embed` → resolve via slide/layout/master relationshippart (covers blipFill on AutoShape, Freeform, TextBox — the paperIQ.pptx pattern).
3. Graphic frame (`<p:graphicFrame>`) picture fill / blip.
4. Shape-type `PictureShape` via reflection fallback.

Slide/background images: walk `slide.background` → `slideLayout.background` → `slideMaster.background`, first with picture data wins (full-bleed bg pattern in pushkaralu.pptx).

Resolution of `r:embed` uses `XSLFSlide.getRelationById` / `getRelationPartById` and falls back to matching `pictureData` by part name. All four strategies from the prior `resolvePictureBytesFromBlipId` are retained (they are correct); only the *fallback-to-fake-position* behavior is removed.

### 2.5 Text extraction

- Walk `XSLFTextShape.textParagraphs` → `paragraph.textRuns` via POI public API.
- Per run: raw text (POI `TextRun.rawText`), bold/italic/underline, font color (from `rPr.solidFill.srgbClr.val`), fontSize, fontFamily.
- Per paragraph: alignment (`textAlign`), indent level (`indentLevel`), bullet character.

**Inheritance of unset properties** (references POI `org.apache.poi.sl.draw.DrawTextParagraph`):
- A run property that is "unset" (size ≤ 0, color null, bold unresolved) is resolved by walking: run's own `rPr` → paragraph's default run properties (`pPr.defRPr`) → the matched layout placeholder's text style → master placeholder's text style → presentation defaults.
- This mirrors what POI's desktop renderer does (see `DrawTextParagraph` / `DrawTextRun` in `poi-*.jar`). On Android we replicate the lookup using the same XML paths (`... Styles.xml` is not used; we use the live layout/master placeholder shapes).
- Inheritance is best-effort: if no ancestor supplies a value, the renderer's own default (dark color, readable size) applies.

### 2.6 Parser output contract

- Every `ParsedShape.bounds` is a **resolved, validated** `NormRect` in 0..1. No nulls, no zeros-area shapes.
- `shapes` are ordered back-to-front as in source XML.
- A source shape that yields no content AND no bounds is simply absent — it never enters the model.

---

## 3. Renderer — Compose-native

Single implementation in `PptxSlideRenderer.kt` (rewritten as Compose). One composable drives **all** views.

### 3.1 Core composable

```kotlin
@Composable
fun ParsedSlideView(
    slide: ParsedSlide,
    modifier: Modifier = Modifier,
    /** Controls text legibility floor; smaller views (grid/thumb) may pass a smaller min. */
    minTextSp: Float = 9f,
)
```

Layout:
- `BoxWithConstraints` so normalized bounds → on-screen pixels via `maxWidth`/`maxHeight`.
- Aspect ratio comes from `ParsedPresentation.aspectRatio`; the *outer* container (per view mode) applies `aspectRatio()`, so `ParsedSlideView` just fills it.

Layers (painted in order):
1. **Background** — `Canvas` or `Box` with `BgSolid` color / `BgGradient` brush / `BgImage` decoded bitmap. `BgNone` → `Color.White`.
2. **Shapes** — for each `ParsedShape` in list order, a positioned child at `offset(x = bounds.left * w, y = bounds.top * h)` with `size(width * w, width * h)` (using width as the normalized base; height derived from aspect ratio). Each content type dispatches to a small composable:
   - `TextContent` → `TextShapeLayout` (see §3.2).
   - `ImageContent` → decode once (remembered), `Image` with `ContentScale.Fit`.
   - `VectorContent` → `Canvas` drawing the vector kind.
   - `GroupContent` → recurse (children already have slide-space bounds).
   - `TableContent` → grid of cells.
   - `DecorativeContent` → nothing.

### 3.2 Text rendering & legibility

- Font size: `run.sizePt` converted to sp via the slide's render scale, **floored at `minTextSp`** (default 9sp). This guarantees legibility regardless of how small the slide is scaled (grid/thumb views pass a smaller floor but never below ~7sp).
- Color: `run.colorHex` if set; otherwise a dark default (`Color(0xFF1E293B)`) so text is legible on both light and dark app themes. **Never** inherit the app's `MaterialTheme.colorScheme.onSurface`.
- Bold/italic/underline via `SpanStyle`. Bullets prefixed per paragraph. Alignment via `TextAlign`.
- Multi-paragraph text uses a `Column` of `Text` composables; word wrap is native Compose.

### 3.3 View-mode reuse

All four view modes render the **same** `ParsedSlideView`:

| View mode | Container | `minTextPerShape` |
|-----------|-----------|-------------------|
| Continuous | `LazyColumn` → `ZoomableBox` → `ParsedSlideView` | 9sp |
| Pager | `HorizontalPager` → `ZoomableBox` → `ParsedSlideView` | 9sp |
| Grid | `LazyVerticalGrid` → `ParsedSlideView` (smaller) | 7sp |
| Slideshow | `HorizontalPager` (fullscreen, black bg) → `ParsedSlideView` | 9sp |
| Thumbnail strip | `LazyRow` → `ParsedSlideView` (tiny) | 7sp |

No separate bitmap pipeline. The ViewModel no longer pre-renders slides to JPEG.

### 3.4 Background default

`BgNone` → `Color.White`. The slide's outer `Card`/`Box` in the view also defaults to white (`safeParseColor(slideBg, Color.White)`), so even if the renderer is bypassed the content area stays white. The app's `surfaceContainerLow` appears only in the margin *around* the slide card, never inside it.

---

## 4. PDF Export — Canvas rasterizer over ParsedSlide

PDF export may rasterize, but it must walk the **same `ParsedSlide` model**, not POI shapes.

```kotlin
object PptxSlideRasterizer {
    fun renderToBitmap(slide: ParsedSlide, width: Int, height: Int, aspectRatio: Float): Bitmap
}
```

- Uses Android `Canvas`/`Paint`/`StaticLayout` (the proven drawing primitives from the old `PptxSlideRenderer`).
- Walks `slide.background` then `slide.shapes` exactly like the Compose renderer, so the PDF visually matches the on-screen view.
- `OfficeConverter.convertPptxToPdf` is rewritten to: parse via `PptxSlideParser` → rasterize each `ParsedSlide` via `PptxSlideRasterizer` → embed in PDF pages.
- `OfficeConverter.renderPptxToSlideImages` (the duplicate raster path) is **deleted**; callers that need slide images use the parser + `PptxSlideRasterizer` or the Compose view directly.

This satisfies "a renderer that walks the identical ParsedSlide data — not a second independent shape-walking implementation."

---

## 5. Failure Mode Policy (invariant, non-negotiable)

| Condition | Action |
|-----------|--------|
| Shape bounds cannot be resolved through §2.2 chain | **Omit** the shape. Never draw at a fake/hardcoded rect. |
| Shape has zero/negative area after resolution | **Omit**. |
| Image bytes fail to decode (`BitmapFactory` returns null) | **Omit** the image. No placeholder, no fake position. |
| Text run is empty after cleaning | Skip the run (not a whole-shape failure). |
| Background image fails to decode | Fall back to `BgNone` (white). |
| Slide dimensions unreadable | Use 16:9 EMU default (degenerate file; size only affects metadata, not shape bounds which are normalized). |

These rules are encoded as `require`/`check` or early-returns in the parser and asserted in unit tests so they cannot silently regress.

---

## 6. Test Plan

### 6.1 JVM unit tests (run on JVM, no Android needed)

Created programmatically with POI `XMLSlideShow` (POI runs on JVM):

| Test | What it exercises |
|------|-------------------|
| `parser_resolvesOwnXfrm` | Shape with explicit `<a:xfrm>` → bounds match. |
| `parser_resolvesFromLayout` | No own xfrm, layout placeholder match → layout bounds. |
| `parser_resolvesFromMasterChain` | No own xfrm, layout match has no xfrm → master bounds. |
| `parser_omitsUnresolvable` | No xfrm + no placeholder → shape absent from output. |
| `parser_nestedGroupTransform` | Group-in-group child bounds correctly composed. |
| `parser_blipFillImage` | blipFill on AutoShape → image bytes extracted. |
| `parser_backgroundImage` | Slide bg picture → `BgImage`. |
| `parser_textInheritance` | Unset run size/color resolved from layout placeholder style. |
| `parser_slideDimensions` | `sldSz` read correctly; default only on degenerate input. |
| `parser_defaultWhiteBackground` | No bg in source → `BgNone`. |
| `parser_nonDefaultSlideSize` | e.g. 16:10 or 4:3 deck → aspect ratio preserved. |

### 6.2 Visual verification (requires device/emulator + real decks)

Decks (from the task's required set):

| Deck | Pattern | Key assertions |
|------|---------|----------------|
| `paperIQ.pptx` | blipFill AutoShape/Freeform images | All images render in-bounds; no XSLFPictureShape-only path needed. |
| `sih.pptx` / `cisco.pptx` | layout/master placeholder inheritance, no explicit xfrm on text boxes | Text boxes positioned via layout/master chain; bounds resolution level logged per slide. |
| `pushkaralu.pptx` | full-bleed bg images, few text shapes | Background image fills slide; text legible. |
| Nested-group deck | groups within groups | Child shapes correctly positioned. |
| Non-16:9 deck | custom slide size | Aspect ratio correct; no distortion. |

Per-slide report: bounds resolution level (own/layout/master), images in-bounds, text legible in light + dark theme, thumbnail/grid matches main view.

> **Environment note:** this session has no Android SDK / emulator available, so JVM unit tests are runnable here; visual verification against real decks is documented as the required procedure and executed where a device is available. The design makes the parser fully unit-testable on JVM (the highest-risk logic: bounds resolution, group transforms, inheritance).

---

## 7. File Map (what changes)

| File | Action | Role |
|------|--------|------|
| `core/engine/document/PptxSlideParser.kt` | **New** | Data model (§1) + parser (§2). Replaces `PptxShapeExtractor`. |
| `core/engine/document/PptxSlideRenderer.kt` | **Rewrite** | Compose renderer (§3). Replaces old Canvas renderer. |
| `core/engine/document/PptxSlideRasterizer.kt` | **New** | Canvas rasterizer over `ParsedSlide` for PDF (§4). |
| `feature/viewer/PptxViewerViewModel.kt` | **Rewrite** | Uses parser; exposes `ParsedPresentation`; Compose renders directly. |
| `feature/viewer/PptxViewerScreen.kt` | **Rewrite** | All view modes use `ParsedSlideView`; no pre-rendered bitmaps. |
| `feature/viewer/PptxSearchEngine.kt` | **Adapt** | Operates on `ParsedPresentation`. |
| `core/engine/document/OfficeConverter.kt` | **Edit (PPTX only)** | `convertPptxToPdf` uses parser + rasterizer; `renderPptxToSlideImages` deleted. |
| `core/engine/document/PptxShapeExtractor.kt` | **Delete** | Superseded by parser. |

Out of scope (not touched): navigation, other viewers, theming, DI modules, `ReverseOfficeConverter` (non-PPTX parts), `ViewerDispatcherScreen` routing.

---

## 8. Consistency checks (Phase 0 self-verification)

- §3 (renderer) consumes exactly the model defined in §1. ✓ (no POI types cross the boundary)
- §4 (PDF) walks `ParsedSlide`, not POI — consistent with §1/§3. ✓
- §5 (failure modes) matches §2.2 "unresolved → omit". ✓
- §3.4 (white default) matches §1.5 `BgNone` and §5. ✓
- §3.2 (text legibility floor) satisfies the "no unreadably small text" requirement. ✓
- One rendering implementation (§3) serves all views; PDF (§4) is the only raster path and reuses the model. ✓
