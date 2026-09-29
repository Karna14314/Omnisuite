package com.karnadigital.omnisuite.core.model

import com.karnadigital.omnisuite.core.util.CustomGeomPath

data class PptxTextRun(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val textColorHex: String? = null,
    val fontSizePt: Float = 14f,
    val fontFamily: String? = null
)

/**
 * Text-box / table-cell padding derived from `<a:bodyPr>` lIns/tIns/rIns/bIns (EMU).
 * Horizontal values (left/right) are stored as a fraction of slide width;
 * vertical values (top/bottom) as a fraction of slide height.
 */
data class Insets(
    val left: Float = 0.01f,
    val top: Float = 0.009f,
    val right: Float = 0.01f,
    val bottom: Float = 0.009f
)

/** Autofit mode from `<a:bodyPr>` — shrink text, resize shape, or none. */
enum class AutoFitMode { NONE, NORM_AUTOFIT, SP_AUTO_FIT }

/** Vertical anchor alignment from `<a:bodyPr anchor=" t|ctr|b|just ">`. */
enum class VerticalAnchor { TOP, CENTER, BOTTOM, JUSTIFIED }

data class PptxParagraph(
    val runs: List<PptxTextRun>,
    val bulletLevel: Int = 0,
    val hasBullet: Boolean = false,
    val bulletChar: String = "•",
    val bulletFont: String? = null,
    val bulletColorHex: String? = null,
    val bulletSizePct: Float? = null,
    val alignment: String = "LEFT", // "LEFT", "CENTER", "RIGHT", "JUSTIFY"
    val spaceBeforePt: Float = 0f,
    val spaceAfterPt: Float = 0f,
    /** Line-spacing multiplier: 1.0 = single (100% spcPct). From `<a:lnSpc>`. */
    val lineSpacingMul: Float = 1.0f,
    /** Numbering scheme from `<a:buAutoNum type>`, e.g. "arabicPeriod". Null = bullet/none. */
    val numberingType: String? = null
) {
    val fullText: String get() = runs.joinToString("") { it.text }
    val primaryText: String get() = runs.firstOrNull()?.text ?: ""
    val isBold: Boolean get() = runs.firstOrNull()?.isBold ?: false
    val isItalic: Boolean get() = runs.firstOrNull()?.isItalic ?: false
    val isUnderline: Boolean get() = runs.firstOrNull()?.isUnderline ?: false
    val textColorHex: String? get() = runs.firstOrNull()?.textColorHex
    val fontSizePt: Float get() = runs.firstOrNull()?.fontSizePt ?: 14f
}

enum class ShapeGeometryType {
    RECTANGLE, ROUNDED_RECTANGLE, ELLIPSE, HEXAGON, TRIANGLE, DIAMOND, CHEVRON,
    STAR, PENTAGON, RIGHT_ARROW, CALLOUT, PARALLELOGRAM, LINE, NONE
}

data class ShapeBorder(
    val strokeColorHex: String? = null,
    val strokeWidthDp: Float = 1.5f
)

data class PptxTextShape(
    val id: String,
    val isTitle: Boolean = false,
    val paragraphs: List<PptxParagraph> = emptyList(),
    val shapeGeometry: ShapeGeometryType = ShapeGeometryType.RECTANGLE,
    val shapeBorder: ShapeBorder? = null,
    val shapeLeft: Float = 0.05f,
    val shapeTop: Float = 0.05f,
    val shapeWidth: Float = 0.9f,
    val shapeHeight: Float = 0.15f,
    val backgroundColorHex: String? = null,
    val zOrder: Int = 0,
    val rotationDegrees: Float = 0f,
    /** Text-box / cell padding from `<a:bodyPr>` (fraction of slide w/h). */
    val insets: Insets = Insets(),
    /** Autofit mode from `<a:bodyPr>`. */
    val autoFit: AutoFitMode = AutoFitMode.NONE,
    /** normAutofit fontScale in thousandths (e.g. 80000 = 80%). Null = use default. */
    val fontScale: Int? = null,
    /** normAutofit lnSpcReduction in thousandths. Null = no reduction. */
    val lnSpcReduction: Int? = null,
    /** True if shape is full-bleed background or master layout decorative shape. */
    val isBackgroundShape: Boolean = false,
    /** Parsed `<a:custGeom>` outline; renderer prefers it over [shapeGeometry]. */
    val customPath: CustomGeomPath? = null,
    /** Vertical alignment within text box from `<a:bodyPr anchor>`. */
    val verticalAnchor: VerticalAnchor = VerticalAnchor.TOP
) {
    val fullText: String get() = paragraphs.joinToString("\n") { it.fullText }
    val primaryText: String get() = paragraphs.firstOrNull()?.primaryText ?: ""
    val isBold: Boolean get() = paragraphs.firstOrNull()?.isBold ?: false
    val isItalic: Boolean get() = paragraphs.firstOrNull()?.isItalic ?: false
    val isUnderline: Boolean get() = paragraphs.firstOrNull()?.isUnderline ?: false
    val textColorHex: String? get() = paragraphs.firstOrNull()?.textColorHex
    val fontSizePt: Float get() = paragraphs.firstOrNull()?.fontSizePt ?: 14f
}

data class PptxImage(
    val filePath: String,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val zOrder: Int = 0,
    val id: String = "",
    /** Geometry to clip this image to when it is a picture-fill of an AutoShape. */
    val clipGeometry: ShapeGeometryType = ShapeGeometryType.RECTANGLE,
    /** True when sourced from an AutoShape spPr blipFill rather than a true p:pic. */
    val isShapeFill: Boolean = false,
    /** Parsed `<a:custGeom>` outline for clipping picture-fills of custom shapes. */
    val customClip: CustomGeomPath? = null,
    val rotationDegrees: Float = 0f,
    val flipH: Boolean = false,
    val flipV: Boolean = false
)

data class PptxSlide(
    val slideNumber: Int,
    val title: PptxTextShape,
    val textShapes: List<PptxTextShape>,
    val images: List<PptxImage> = emptyList(),
    val backgroundImage: PptxImage? = null,
    val speakerNotes: String? = null,
    val bgColorHex: String? = null,
    val aspectRatio: Float = 16f / 9f,
    val slideWidthPt: Float = 960f,
    val slideHeightPt: Float = 540f
)

data class PptxPresentation(val slides: List<PptxSlide>)
