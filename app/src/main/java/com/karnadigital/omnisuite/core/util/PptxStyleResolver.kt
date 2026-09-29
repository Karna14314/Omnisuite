package com.karnadigital.omnisuite.core.util

import org.apache.poi.sl.usermodel.Placeholder
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.poi.xslf.usermodel.XSLFTextParagraph
import org.apache.poi.xslf.usermodel.XSLFTextRun
import org.apache.poi.xslf.usermodel.XSLFTextShape

data class ThemeFontScheme(
    val minorLatin: String? = null,
    val majorLatin: String? = null,
    val minorEastAsian: String? = null,
    val majorEastAsian: String? = null,
    val minorComplexScript: String? = null,
    val majorComplexScript: String? = null
)

data class ResolvedRunStyle(
    val fontSizePt: Float? = null,
    val typeface: String? = null,
    val isBold: Boolean? = null,
    val isItalic: Boolean? = null,
    val isUnderline: Boolean? = null,
    val colorHex: String? = null
)

object PptxStyleResolver {

    fun extractThemeFontScheme(slide: XSLFSlide?): ThemeFontScheme {
        if (slide == null) return ThemeFontScheme()
        return try {
            val theme = try { slide.theme } catch (_: Throwable) { null }
                ?: try { slide.slideLayout?.slideMaster?.theme } catch (_: Throwable) { null }
            if (theme != null) {
                val ctTheme = try {
                    theme.javaClass.getMethod("getXmlObject").invoke(theme)
                } catch (_: Throwable) { null }
                if (ctTheme != null) {
                    val themeElements = try { ctTheme.javaClass.getMethod("getThemeElements").invoke(ctTheme) } catch (_: Throwable) { null }
                    val fontScheme = try { themeElements?.javaClass?.getMethod("getFontScheme")?.invoke(themeElements) } catch (_: Throwable) { null }
                    if (fontScheme != null) {
                        val minorFont = try { fontScheme.javaClass.getMethod("getMinorFont").invoke(fontScheme) } catch (_: Throwable) { null }
                        val majorFont = try { fontScheme.javaClass.getMethod("getMajorFont").invoke(fontScheme) } catch (_: Throwable) { null }

                        val minorLatin = extractLatinTypeface(minorFont)
                        val majorLatin = extractLatinTypeface(majorFont)
                        return ThemeFontScheme(minorLatin = minorLatin, majorLatin = majorLatin)
                    }
                }
            }
            ThemeFontScheme()
        } catch (_: Throwable) {
            ThemeFontScheme()
        }
    }

    private fun extractLatinTypeface(fontObj: Any?): String? {
        if (fontObj == null) return null
        return try {
            val latin = fontObj.javaClass.getMethod("getLatin").invoke(fontObj)
            latin?.javaClass?.getMethod("getTypeface")?.invoke(latin) as? String
        } catch (_: Throwable) { null }
    }

    fun resolveTypeface(rawTypeface: String?, fontScheme: ThemeFontScheme?): String? {
        if (rawTypeface.isNullOrBlank()) return null
        val tf = rawTypeface.trim()
        return when {
            tf.equals("+mn-lt", ignoreCase = true) -> fontScheme?.minorLatin ?: "sans-serif"
            tf.equals("+mj-lt", ignoreCase = true) -> fontScheme?.majorLatin ?: "sans-serif"
            tf.equals("+mn-ea", ignoreCase = true) -> fontScheme?.minorEastAsian ?: fontScheme?.minorLatin ?: "sans-serif"
            tf.equals("+mj-ea", ignoreCase = true) -> fontScheme?.majorEastAsian ?: fontScheme?.majorLatin ?: "sans-serif"
            tf.equals("+mn-cs", ignoreCase = true) -> fontScheme?.minorComplexScript ?: fontScheme?.minorLatin ?: "sans-serif"
            tf.equals("+mj-cs", ignoreCase = true) -> fontScheme?.majorComplexScript ?: fontScheme?.majorLatin ?: "sans-serif"
            else -> tf
        }
    }

    fun resolveRunStyle(
        run: XSLFTextRun?,
        paragraph: XSLFTextParagraph?,
        shape: XSLFTextShape?,
        slide: XSLFSlide?
    ): ResolvedRunStyle {
        val fontScheme = extractThemeFontScheme(slide)
        var fontSizePt: Float? = null
        var rawTypeface: String? = null
        var isBold: Boolean? = null
        var isItalic: Boolean? = null
        var isUnderline: Boolean? = null
        var colorHex: String? = null

        if (run != null) {
            try {
                if (run.fontSize != null && run.fontSize!! > 0) {
                    fontSizePt = run.fontSize!!.toFloat()
                }
                if (fontSizePt == null) {
                    val xmlRun = run.javaClass.getMethod("getXmlObject").invoke(run)
                    val rPr = try { xmlRun?.javaClass?.getMethod("getRPr")?.invoke(xmlRun) } catch (_: Throwable) { null }
                    if (rPr != null) {
                        val sz = try { rPr.javaClass.getMethod("getSz").invoke(rPr) as? Number } catch (_: Throwable) { null }
                        if (sz != null && sz.toInt() > 0) {
                            fontSizePt = sz.toFloat() / 100f
                        }
                    }
                }
                val fam = run.fontFamily
                if (!fam.isNullOrBlank() && !fam.startsWith("org.apache")) {
                    rawTypeface = fam
                }
                try { isBold = run.isBold } catch (_: Throwable) {}
                try { isItalic = run.isItalic } catch (_: Throwable) {}
                try { isUnderline = run.isUnderlined } catch (_: Throwable) {}
            } catch (_: Throwable) {}
        }

        if (paragraph != null) {
            try {
                val xmlP = paragraph.javaClass.getMethod("getXmlObject").invoke(paragraph)
                val pPr = try { xmlP?.javaClass?.getMethod("getPPr")?.invoke(xmlP) } catch (_: Throwable) { null }
                val defRPr = try { pPr?.javaClass?.getMethod("getDefRPr")?.invoke(pPr) } catch (_: Throwable) { null }
                if (defRPr != null) {
                    if (fontSizePt == null) {
                        val sz = try { defRPr.javaClass.getMethod("getSz").invoke(defRPr) as? Number } catch (_: Throwable) { null }
                        if (sz != null && sz.toInt() > 0) {
                            fontSizePt = sz.toFloat() / 100f
                        }
                    }
                    if (rawTypeface == null) {
                        val latin = try { defRPr.javaClass.getMethod("getLatin").invoke(defRPr) } catch (_: Throwable) { null }
                        val tf = try { latin?.javaClass?.getMethod("getTypeface")?.invoke(latin) as? String } catch (_: Throwable) { null }
                        if (!tf.isNullOrBlank()) rawTypeface = tf
                    }
                    if (isBold == null) {
                        isBold = try { defRPr.javaClass.getMethod("getB").invoke(defRPr) as? Boolean } catch (_: Throwable) { null }
                    }
                    if (isItalic == null) {
                        isItalic = try { defRPr.javaClass.getMethod("getI").invoke(defRPr) as? Boolean } catch (_: Throwable) { null }
                    }
                }
            } catch (_: Throwable) {}
        }

        val resolvedTypeface = resolveTypeface(rawTypeface, fontScheme)
        val isTitle = try {
            shape?.placeholder == Placeholder.TITLE || shape?.placeholder == Placeholder.CENTERED_TITLE
        } catch (_: Throwable) { false }

        val fallbackSize = if (fontSizePt == null) {
            if (isTitle) 24f else 18f
        } else null

        return ResolvedRunStyle(
            fontSizePt = fontSizePt ?: fallbackSize,
            typeface = resolvedTypeface,
            isBold = isBold,
            isItalic = isItalic,
            isUnderline = isUnderline,
            colorHex = colorHex
        )
    }
}
