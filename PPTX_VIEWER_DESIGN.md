# PPTX Viewer Subsystem Design Specification (`PPTX_VIEWER_DESIGN.md`)

## 1. DATA MODEL
The PPTX Viewer subsystem decouples presentation document parsing from rendering by using a pure Kotlin data model that is completely independent of Apache POI and Java AWT types (`org.apache.poi.*`, `java.awt.*`).

```kotlin
data class ParsedPresentation(
    val slides: List<ParsedSlide>,
    val slideWidthEmu: Long = 9144000L,
    val slideHeightEmu: Long = 5143500L
) {
    val aspectRatio: Float
        get() = if (slideHeightEmu > 0) slideWidthEmu.toFloat() / slideHeightEmu.toFloat() else (16f / 9f)
}

data class ParsedSlide(
    val slideIndex: Int,
    val background: ParsedBackground,
    val shapes: List<ParsedShape>,
    val speakerNotes: String? = null,
    val aspectRatio: Float = 16f / 9f
)

sealed class ParsedBackground {
    object DefaultWhite : ParsedBackground()
    data class SolidColor(val colorHex: String) : ParsedBackground()
    data class ImageFill(val imageBytes: ByteArray, val contentType: String? = null) : ParsedBackground()
}

sealed class ParsedShape {
    abstract val id: String
    abstract val bounds: NormalizedBounds // Normalized 0..1 slide coordinate space
    abstract val zIndex: Int

    data class TextShape(
        override val id: String,
        override val bounds: NormalizedBounds,
        override val zIndex: Int,
        val isTitle: Boolean = false,
        val paragraphs: List<ParsedParagraph>,
        val backgroundColorHex: String? = null
    ) : ParsedShape()

    data class ImageShape(
        override val id: String,
        override val bounds: NormalizedBounds,
        override val zIndex: Int,
        val imageBytes: ByteArray,
        val contentType: String? = null
    ) : ParsedShape()

    data class VectorShape(
        override val id: String,
        override val bounds: NormalizedBounds,
        override val zIndex: Int,
        val shapeType: String, // rect, roundRect, ellipse, triangle, line, etc.
        val fillColorHex: String? = null,
        val strokeColorHex: String? = null,
        val strokeWidthDp: Float = 1f
    ) : ParsedShape()

    data class TableShape(
        override val id: String,
        override val bounds: NormalizedBounds,
        override val zIndex: Int,
        val rows: Int,
        val cols: Int,
        val cells: List<List<ParsedTableCell>>
    ) : ParsedShape()

    data class GroupShape(
        override val id: String,
        override val bounds: NormalizedBounds,
        override val zIndex: Int,
        val children: List<ParsedShape>
    ) : ParsedShape()
}

data class ParsedTableCell(
    val textShape: ParsedShape.TextShape?,
    val backgroundColorHex: String? = null
)

data class ParsedParagraph(
    val runs: List<ParsedTextRun>,
    val bulletLevel: Int = 0,
    val hasBullet: Boolean = false,
    val bulletChar: String = "",
    val alignment: TextAlignment = TextAlignment.LEFT
) {
    val fullText: String get() = runs.joinToString("") { it.text }
}

enum class TextAlignment { LEFT, CENTER, RIGHT, JUSTIFY }

data class ParsedTextRun(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val textColorHex: String? = null,
    val fontSizePt: Float = 14f,
    val fontFamily: String? = null
)

data class NormalizedBounds(
    val left: Float,   // 0.0f..1.0f
    val top: Float,    // 0.0f..1.0f
    val width: Float,  // 0.001f..1.0f
    val height: Float  // 0.001f..1.0f
)
```

---

## 2. PARSING RESPONSIBILITIES

### Slide Dimensions (`sldSz`)
- Presentation slide dimensions are extracted safely from `ctPresentation.getSldSz()`.
- Apache POI / XMLBeans holder types for `getCx()` and `getCy()` do not implement `java.lang.Number`. Value extraction uses `extractLongValue` which inspects reflection methods (e.g., `getLongValue()`) or parses string values safely without unsafe type casting.
- Default fallback dimensions: 9144000 x 5143500 EMU (16:9 ratio, 10 x 5.625 inches).

### Shape Bounds Resolution Inheritance Chain
Shape bounds are resolved strictly in order:
1. **Direct Shape `<a:xfrm>`**: Extract `off` (x, y) and `ext` (cx, cy) from shape XML.
2. **Slide Layout Placeholder**: If the shape is a placeholder without explicit `<a:xfrm>`, query `slideLayout` for candidate shapes matching by **type AND idx**.
3. **Slide Master Placeholder**: If `slideLayout` candidate shape also lacks explicit `<a:xfrm>`, query `slideMaster` for matching placeholder shape.
4. **Unresolved Bounds Handling**: If bounds cannot be resolved at any step in the inheritance chain, **omit the shape entirely** (do not generate fallback default bounding boxes).

### Group Shape Matrix Transformations
- Handle arbitrary nested group structures (`GroupShape` within `GroupShape`).
- Group matrix affine composition:
  $$\text{ScaleX} = \frac{\text{extCx}}{\text{chExtCx}}, \quad \text{ScaleY} = \frac{\text{extCy}}{\text{chExtCy}}$$
  $$\text{TransX} = \text{offX} + (\text{childX} - \text{chOffX}) \times \text{ScaleX}$$
  $$\text{TransY} = \text{offY} + (\text{childY} - \text{chOffY}) \times \text{ScaleY}$$
- Accumulate transforms recursively down the shape tree so child shapes in deep groups resolve to accurate normalized slide-space coordinates.

### Image Source Extraction
Support image extraction across all OOXML picture structures:
- `XSLFPictureShape` instances.
- `<a:blipFill>` on `XSLFAutoShape`, `XSLFFreeform`, and `XSLFTextBox` (picture-fill shapes).
- Graphic frames (OLE / charts / SmartArt image representations).
- Slide, `slideLayout`, and `slideMaster` background picture fills.

### Text Run & Paragraph Styling Inheritance
- Extract text runs with formatting (bold, italic, underline, color hex, font size pt, font family).
- Extract paragraph-level alignment and bullet formatting.
- Inherit unset styling properties from layout and master placeholders following POI's `DrawTextParagraph` reference resolution algorithm.

---

## 3. RENDERING RESPONSIBILITIES

### Compose-Native Unified Renderer
- A single Compose composable (`ParsedSlideView`) consumes the `ParsedSlide` model.
- `ParsedSlideView` is reused across all viewing interfaces:
  - Continuous vertical scroll list (`ContinuousView`)
  - Single-slide horizontal pager (`PagerView`)
  - Fullscreen slideshow presenter (`SlideshowView`)
  - Grid overview (`GridView`)
  - Bottom thumbnail navigation strip
- Eliminates duplicate rendering logic and direct AWT/Canvas shape-walking duplication.

### PDF Export & Thumbnail Rasterization
- PDF export and thumbnail image generation walk the identical `ParsedSlide` data model.
- Composables or model-driven canvas drawers read `ParsedSlide` shapes to render slide bitmaps cleanly.

### Text Sizing & Legibility
- Text sizes in composables adapt to container width while ensuring minimum readable font size.
- Text scale-down math never clamps text to unreadably tiny values.

### Background Default Policy
- Default slide background color is **fixed White (`#FFFFFF`)**.
- Slide content background never inherits the app UI's dark/light theme surface colors.

---

## 4. FAILURE MODE POLICY
- **Unresolved Bounds**: Any shape whose bounds cannot be resolved through the full inheritance chain (Slide -> Layout -> Master) is **omitted**. Fake position fallbacks (e.g. hardcoded `0.05, 0.05, 0.9, 0.15` rectangles) are strictly forbidden.
- **Image Decoding Failure**: Any image bytes that fail to decode or are corrupt are **omitted**. Fake placeholder boxes at fake positions are strictly forbidden.

---

## 5. TEST PLAN

The implementation will be verified against standard unit test suites covering the 5 key presentation deck scenarios:
1. **Picture-fill AutoShapes/Freeforms (paperIQ pattern)**: AutoShapes with `<a:blipFill>` picture fills (not just `XSLFPictureShape`) correctly extract image bytes and bounds.
2. **Placeholder Bounds Inheritance (sih / cisco pattern)**: Text shapes with no direct `<a:xfrm>` resolve bounds through `slideLayout` and `slideMaster` matched by type and `idx`.
3. **Full-Bleed Background Images (pushkaralu pattern)**: Slide background picture fills (or full-slide bleed images) resolve as slide background image.
4. **Nested Group Shapes**: Shapes inside nested group hierarchies compose transforms correctly.
5. **Non-16:9 / Custom Slide Dimensions**: Presentations with non-16:9 slide aspect ratios (e.g. 4:3 or custom EMU sizes) calculate normalized bounds correctly.
