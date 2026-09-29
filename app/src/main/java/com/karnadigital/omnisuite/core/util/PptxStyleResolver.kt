package com.karnadigital.omnisuite.core.util

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFShape
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.poi.xslf.usermodel.XSLFSlideLayout
import org.apache.poi.xslf.usermodel.XSLFSlideMaster
import org.apache.poi.xslf.usermodel.XSLFTextParagraph
import org.apache.poi.xslf.usermodel.XSLFTextRun
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.openxmlformats.schemas.drawingml.x2006.main.CTRegularTextRun
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextCharacterProperties
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextListStyle
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextParagraphProperties
import org.openxmlformats.schemas.presentationml.x2006.main.CTShape
import org.openxmlformats.schemas.presentationml.x2006.main.CTSlideMasterTextStyles

/**
 * Theme font scheme from `<a:fontScheme>`. OOXML references the major/minor faces
 * indirectly as `+mj-lt` / `+mn-lt`; the real typeface names live here.
 */
data class ThemeFontScheme(
    val majorLatin: String? = null,
    val minorLatin: String? = null
) {
    fun resolve(rawTypeface: String?): String? {
        val tf = rawTypeface?.trim().orEmpty()
        if (tf.isEmpty()) return null
        return when {
            tf.equals("+mj-lt", true) -> majorLatin
            tf.equals("+mn-lt", true) -> minorLatin
            tf.startsWith("+mj-") || tf.startsWith("+mn-") -> (majorLatin ?: minorLatin)
            else -> tf
        }
    }
}

/** Text properties for a single run after the OOXML inheritance chain is walked. */
data class ResolvedRunStyle(
    val fontSizePt: Float? = null,
    /** Resolved typeface name — `+mj-lt`/`+mn-lt` already substituted. Null = inherit. */
    val typeface: String? = null,
    val isBold: Boolean? = null,
    val isItalic: Boolean? = null,
    val isUnderline: Boolean? = null,
    val colorHex: String? = null
)

/**
 * Resolves effective text properties through the DrawingML inheritance chain that
 * PowerPoint applies, which Apache POI does not:
 *
 *  1. run `<a:rPr>`
 *  2. paragraph `<a:pPr><a:defRPr>`
 *  3. shape `<a:lstStyle><a:lvlNpPr><a:defRPr>`
 *  4. matching layout placeholder `<a:lstStyle>`
 *  5. slide master `<p:txStyles>` (`titleStyle` / `bodyStyle` / `otherStyle`)
 *  6. `<p:defaultTextStyle>` in `presentation.xml`
 *
 * This matters because `XSLFTextRun.getFontSize()` returns null whenever a run has
 * no explicit `<a:rPr sz>` — the normal case for text inherited from a template
 * layout. Without this walk the parser falls back to a hardcoded point size and
 * every slide renders with grossly oversized text.
 */
object PptxStyleResolver {

    /**
     * Reads `<a:fontScheme>` major/minor latin typefaces from the slide's theme.
     * Uses typed XMLBeans access — reflective `getMethod` on the schema classes
     * trips a `NoClassDefFoundError` (CTFlatText) under the Android classloader.
     */
    fun extractThemeFontScheme(ppt: XMLSlideShow?, slide: XSLFSlide?): ThemeFontScheme {
        val theme = try {
            slide?.theme ?: ppt?.slideMasters?.firstOrNull()?.theme
        } catch (_: Throwable) { null } ?: return ThemeFontScheme()
        return try {
            val fontScheme = theme.xmlObject.themeElements?.fontScheme
            ThemeFontScheme(
                majorLatin = fontScheme?.majorFont?.latin?.typeface?.takeIf { it.isNotBlank() },
                minorLatin = fontScheme?.minorFont?.latin?.typeface?.takeIf { it.isNotBlank() }
            )
        } catch (_: Throwable) { ThemeFontScheme() }
    }

    /**
     * Walks the inheritance chain for [run] and returns the first non-null value for
     * each property. `level` is the paragraph indent level (0-based); `isTitle`
     * selects the master's `titleStyle` over `bodyStyle`/`otherStyle`.
     */
    fun resolveRunStyle(
        run: XSLFTextRun?,
        shape: XSLFTextShape?,
        slide: XSLFSlide?,
        ppt: XMLSlideShow?,
        themeFonts: ThemeFontScheme,
        isTitle: Boolean,
        level: Int
    ): ResolvedRunStyle {
        var size: Float? = null
        var typeface: String? = null
        var bold: Boolean? = null
        var italic: Boolean? = null
        var underline: Boolean? = null
        var color: String? = null

        fun apply(rPr: CTTextCharacterProperties?) {
            if (rPr == null) return
            if (size == null) rPr.sz.takeIf { it > 0 }?.let { size = it / 100f }
            if (typeface == null) rPr.latin?.typeface?.takeIf { it.isNotBlank() }?.let { typeface = it }
            if (bold == null) rPr.b?.let { bold = it }
            if (italic == null) rPr.i?.let { italic = it }
            if (underline == null && rPr.isSetU) underline = rPr.u.toString() != "none"
        }

        // 1. run <a:rPr>
        // POI types getXmlObject() as the base XmlObject, so cast to the concrete
        // CT type to reach the generated accessors.
        try {
            apply((run?.getXmlObject() as? CTRegularTextRun)?.rPr)
        } catch (_: Throwable) {}

        // 2. paragraph <a:pPr><a:defRPr>  (getParagraph is overloaded; cast to the XSLF type)
        try {
            apply((run?.paragraph as? XSLFTextParagraph)?.xmlObject?.pPr?.defRPr)
        } catch (_: Throwable) {}

        // 3. shape <a:lstStyle>
        apply(lvlDefRPr(shapeLstStyle(shape), level))

        // 4. matching layout placeholder <a:lstStyle>
        apply(lvlDefRPr(layoutLstStyle(shape, slide), level))

        // 5. slide master <p:txStyles>
        apply(lvlDefRPr(masterStyle(slide, isTitle), level))

        // 6. presentation <p:defaultTextStyle>
        try { apply(lvlDefRPr(ppt?.ctPresentation?.defaultTextStyle, level)) } catch (_: Throwable) {}

        return ResolvedRunStyle(
            fontSizePt = size,
            typeface = themeFonts.resolve(typeface),
            isBold = bold,
            isItalic = italic,
            isUnderline = underline,
            colorHex = color
        )
    }

    /** `<a:lstStyle>` from the shape's own `<p:txBody>`. */
    private fun shapeLstStyle(shape: XSLFTextShape?): CTTextListStyle? = try {
        (shape?.xmlObject as? CTShape)?.txBody?.lstStyle
    } catch (_: Throwable) { null }

    /**
     * `<a:lstStyle>` from the layout placeholder that [shape] inherits from. POI
     * exposes no direct link, so the layout is scanned for a shape carrying the
     * same placeholder type and index.
     */
    private fun layoutLstStyle(shape: XSLFTextShape?, slide: XSLFSlide?): CTTextListStyle? {
        val layout = try { slide?.slideLayout } catch (_: Throwable) { null } ?: return null
        val idx = try { shape?.placeholder } catch (_: Throwable) { null } ?: return null
        val candidate: XSLFShape? = try {
            layout.shapes.firstOrNull { s ->
                (s as? XSLFTextShape)?.placeholder == idx
            }
        } catch (_: Throwable) { null }
        return try { (candidate as? XSLFTextShape)?.let { (it.xmlObject as? CTShape)?.txBody?.lstStyle } } catch (_: Throwable) { null }
    }

    /** Master `titleStyle` / `bodyStyle` / `otherStyle` depending on placeholder role. */
    private fun masterStyle(slide: XSLFSlide?, isTitle: Boolean): CTTextListStyle? = try {
        val master: XSLFSlideMaster? = slide?.slideLayout?.slideMaster
        val txStyles: CTSlideMasterTextStyles? = master?.xmlObject?.txStyles
        when {
            txStyles == null -> null
            isTitle -> txStyles.titleStyle
            txStyles.bodyStyle != null -> txStyles.bodyStyle
            else -> txStyles.otherStyle
        }
    } catch (_: Throwable) { null }

    /** `defRPr` of `<a:lvl{n}PPr>` for a 0-based [level]. */
    private fun lvlDefRPr(listStyle: CTTextListStyle?, level: Int): CTTextCharacterProperties? {
        if (listStyle == null) return null
        val n = (level + 1).coerceIn(1, 9)
        return try {
            val pPr: CTTextParagraphProperties? =
                listStyle.javaClass.getMethod("getLvl${n}PPr").invoke(listStyle) as? CTTextParagraphProperties
            pPr?.defRPr
        } catch (_: Throwable) { null }
    }
}
