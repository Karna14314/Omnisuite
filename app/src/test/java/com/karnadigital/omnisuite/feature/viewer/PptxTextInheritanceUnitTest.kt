package com.karnadigital.omnisuite.feature.viewer

import com.karnadigital.omnisuite.core.util.PptxStyleResolver
import com.karnadigital.omnisuite.core.util.ThemeFontScheme
import org.apache.poi.sl.usermodel.TextParagraph
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextParagraph

/**
 * Regression tests for the PPTX text-metrics defects found by diffing the viewer
 * against a reference render: inherited font size/family were dropped and replaced
 * with hardcoded point sizes, theme font references were discarded, and bullet /
 * alignment fidelity was lost.
 */
class PptxTextInheritanceUnitTest {

    private fun parseSlides(ppt: XMLSlideShow): List<PptxSlide> {
        val unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null)
        val allocateInstance = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
        val vm = allocateInstance.invoke(unsafe, PptxViewerViewModel::class.java) as PptxViewerViewModel
        val method = PptxViewerViewModel::class.java
            .getDeclaredMethod("parseAllSlides", org.apache.poi.sl.usermodel.SlideShow::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(vm, ppt) as List<PptxSlide>
    }

    private fun textBox(slide: XSLFSlide, left: Int = 100000, top: Int = 100000, w: Int = 4000000, h: Int = 2000000) =
        slide.createTextBox().also { box ->
            try {
                val m = box.javaClass.methods.first { it.name == "setAnchor" }
                val ctor = m.parameterTypes[0].getConstructor(
                    Double::class.javaPrimitiveType, Double::class.javaPrimitiveType,
                    Double::class.javaPrimitiveType, Double::class.javaPrimitiveType
                )
                m.invoke(box, ctor.newInstance(left.toDouble(), top.toDouble(), w.toDouble(), h.toDouble()))
            } catch (_: Throwable) {}
        }

    /** POI types getXmlObject() as the base XmlObject; cast to reach CTShape accessors. */
    private fun org.apache.poi.xslf.usermodel.XSLFTextShape.ctShape() =
        xmlObject as org.openxmlformats.schemas.presentationml.x2006.main.CTShape

    private fun lstStyleOf(box: org.apache.poi.xslf.usermodel.XSLFTextShape) =
        box.ctShape().txBody.let { if (it.isSetLstStyle) it.lstStyle else it.addNewLstStyle() }

    private fun setLvl1Size(listStyle: org.openxmlformats.schemas.drawingml.x2006.main.CTTextListStyle, sizeHundredths: Int) {
        val lvl1 = if (listStyle.isSetLvl1PPr) listStyle.lvl1PPr else listStyle.addNewLvl1PPr()
        if (lvl1.isSetDefRPr) lvl1.defRPr.sz = sizeHundredths else lvl1.addNewDefRPr().sz = sizeHundredths
    }

    private fun CTTextParagraph.lvl1DefRPr() =
        (if (isSetPPr) pPr else addNewPPr()).let { if (it.isSetDefRPr) it.defRPr else it.addNewDefRPr() }

    // ---- 1. font size inherited from the shape's own <a:lstStyle> ----
    @Test
    fun testFontSizeInheritedFromShapeListStyle() {
        val ppt = XMLSlideShow()
        val slide = ppt.createSlide()
        val box = textBox(slide)
        val p = box.textParagraphs[0]
        p.textRuns[0].setText("Inherited body text")

        // The run deliberately has NO explicit <a:rPr sz>.
        setLvl1Size(lstStyleOf(box), 1100)

        val style = PptxStyleResolver.resolveRunStyle(
            run = box.textParagraphs[0].textRuns[0] as? org.apache.poi.xslf.usermodel.XSLFTextRun,
            shape = box, slide = slide, ppt = ppt,
            themeFonts = ThemeFontScheme(), isTitle = false, level = 0
        )
        assertEquals("size inherited from shape lstStyle", 11f, style.fontSizePt!!, 0.01f)

        val parsed = parseSlides(ppt)[0].textShapes.first()
        assertEquals("parser uses inherited size, not 14/18/24", 11f, parsed.paragraphs[0].runs[0].fontSizePt, 0.01f)
    }

    // ---- 2. font size inherited from the slide master <p:bodyStyle> ----
    @Test
    fun testFontSizeInheritedFromMasterBodyStyle() {
        val ppt = XMLSlideShow()
        val slide = ppt.createSlide()
        val master = ppt.slideMasters[0]
        val bodyStyle = master.xmlObject.txStyles
        val lvl1 = if (bodyStyle.isSetBodyStyle && bodyStyle.bodyStyle.isSetLvl1PPr)
            bodyStyle.bodyStyle.lvl1PPr else bodyStyle.addNewBodyStyle().addNewLvl1PPr()
        if (!lvl1.isSetDefRPr) lvl1.addNewDefRPr().sz = 2000 else lvl1.defRPr.sz = 2000

        val box = textBox(slide)
        box.textParagraphs[0].textRuns[0].setText("Master styled text")

        val style = PptxStyleResolver.resolveRunStyle(
            run = box.textParagraphs[0].textRuns[0] as? org.apache.poi.xslf.usermodel.XSLFTextRun,
            shape = box, slide = slide, ppt = ppt,
            themeFonts = ThemeFontScheme(), isTitle = false, level = 0
        )
        assertEquals("size inherited from master bodyStyle", 20f, style.fontSizePt!!, 0.01f)

        // POI 5.2.5 does NOT walk the master's <p:txStyles>; it returns its own
        // 18.0 pt default instead. That silent wrong value is exactly what made
        // inherited template text render oversized.
        assertEquals(
            "POI substitutes its own 18pt default for master-inherited text",
            18.0, box.textParagraphs[0].textRuns[0].fontSize!!, 0.01
        )
    }

    // ---- 3. theme font references +mj-lt / +mn-lt resolve to real typefaces ----
    @Test
    fun testThemeFontReferenceResolves() {
        val scheme = ThemeFontScheme(majorLatin = "Georgia", minorLatin = "Verdana")
        assertEquals("Georgia", scheme.resolve("+mj-lt"))
        assertEquals("Verdana", scheme.resolve("+mn-lt"))
        assertEquals("literal passes through", "Times New Roman", scheme.resolve("Times New Roman"))
        assertNull("blank stays null", scheme.resolve("  "))
    }

    @Test
    fun testThemeFontSchemeExtractedFromTheme() {
        val ppt = XMLSlideShow()
        val theme = ppt.slideMasters[0].theme
        val fontScheme = theme.xmlObject.themeElements.fontScheme
        fontScheme.majorFont.latin.typeface = "Georgia"
        fontScheme.minorFont.latin.typeface = "Verdana"

        val scheme = PptxStyleResolver.extractThemeFontScheme(ppt, ppt.createSlide())
        assertEquals("Georgia", scheme.majorLatin)
        assertEquals("Verdana", scheme.minorLatin)
    }

    // A run whose <a:latin> is "+mn-lt" must surface the theme's minor face,
    // not be discarded (which previously forced the system sans-serif).
    @Test
    fun testThemeFontSurvivesIntoParsedRun() {
        val ppt = XMLSlideShow()
        val slide = ppt.createSlide()
        val fontScheme = ppt.slideMasters[0].theme.xmlObject.themeElements.fontScheme
        fontScheme.majorFont.latin.typeface = "Georgia"
        fontScheme.minorFont.latin.typeface = "Verdana"

        val box = textBox(slide)
        val run = box.textParagraphs[0].textRuns[0]
        run.setText("Themed text")
        run.fontSize = 12.0
        val rPr = (run.xmlObject as org.openxmlformats.schemas.drawingml.x2006.main.CTRegularTextRun)
            .let { if (it.isSetRPr) it.rPr else it.addNewRPr() }
        if (!rPr.isSetLatin) rPr.addNewLatin().typeface = "+mn-lt" else rPr.latin.typeface = "+mn-lt"

        val parsed = parseSlides(ppt)[0].textShapes.first()
        assertEquals(
            "+mn-lt resolved via theme fontScheme", "Verdana",
            parsed.paragraphs[0].runs[0].fontFamily
        )
    }

    // ---- 4. <a:buNone/> suppresses the bullet even at indentLevel > 0 ----
    @Test
    fun testBuNoneSuppressesBullet() {
        val ppt = XMLSlideShow()
        val slide = ppt.createSlide()
        val box = textBox(slide)
        val p = box.textParagraphs[0]
        p.textRuns[0].setText("No bullet here")
        p.indentLevel = 1

        val ctP = p.getXmlObject()
        val pPr = if (ctP.isSetPPr) ctP.pPr else ctP.addNewPPr()
        if (!pPr.isSetBuNone) pPr.addNewBuNone()

        val parsed = parseSlides(ppt)[0].textShapes.first().paragraphs[0]
        assertFalse("buNone must win over indentLevel", parsed.hasBullet)
    }

    // ---- 5. indented paragraph without buNone still gets a bullet ----
    @Test
    fun testIndentedParagraphStillBulleted() {
        val ppt = XMLSlideShow()
        val slide = ppt.createSlide()
        val box = textBox(slide)
        val p = box.textParagraphs[0]
        p.textRuns[0].setText("Indented item")
        p.indentLevel = 1

        val parsed = parseSlides(ppt)[0].textShapes.first().paragraphs[0]
        assertTrue("indentLevel without buNone is bulleted", parsed.hasBullet)
    }

    // ---- 6. <a:buChar> glyph is preserved, not flattened to a bullet ----
    @Test
    fun testBuCharGlyphPreserved() {
        val ppt = XMLSlideShow()
        val slide = ppt.createSlide()
        val box = textBox(slide)
        val p = box.textParagraphs[0]
        p.textRuns[0].setText("Wingdings item")

        val ctP = p.getXmlObject()
        val pPr = if (ctP.isSetPPr) ctP.pPr else ctP.addNewPPr()
        if (!pPr.isSetBuChar) pPr.addNewBuChar().setChar("o") else pPr.buChar.setChar("o")
        if (!pPr.isSetBuFont) pPr.addNewBuFont().typeface = "Wingdings" else pPr.buFont.typeface = "Wingdings"

        val parsed = parseSlides(ppt)[0].textShapes.first().paragraphs[0]
        assertTrue("paragraph is bulleted", parsed.hasBullet)
        assertEquals("buChar glyph preserved verbatim", "o", parsed.bulletChar)
        assertEquals("buFont typeface captured", "Wingdings", parsed.bulletFont)
    }

    // ---- 7. alignment is reported as written; the parser must not invent CENTER ----
    @Test
    fun testLeftAlignedWideTitleStaysLeft() {
        val ppt = XMLSlideShow()
        val slide = ppt.createSlide()
        val box = textBox(slide, w = 9000000)
        val p = box.textParagraphs[0]
        p.textRuns[0].setText("Left aligned wide title")
        p.textAlign = TextParagraph.TextAlign.LEFT

        val parsed = parseSlides(ppt)[0].textShapes.first().paragraphs[0]
        assertEquals(
            "parser must not rewrite LEFT to CENTER for wide shapes", "LEFT", parsed.alignment
        )
    }

    @Test
    fun testCenterAlignmentStillParsed() {
        val ppt = XMLSlideShow()
        val slide = ppt.createSlide()
        val box = textBox(slide)
        box.textParagraphs[0].textRuns[0].setText("Centered")
        box.textParagraphs[0].textAlign = TextParagraph.TextAlign.CENTER

        assertEquals("CENTER", parseSlides(ppt)[0].textShapes.first().paragraphs[0].alignment)
    }

    // ---- 8. <a:bodyPr anchor> drives VerticalAnchor ----
    @Test
    fun testBodyPrAnchorCenter() {
        val ppt = XMLSlideShow()
        val slide = ppt.createSlide()
        val box = textBox(slide)
        box.textParagraphs[0].textRuns[0].setText("Vertically centred")

        val bodyPr = box.ctShape().txBody.bodyPr
        bodyPr.anchor = org.openxmlformats.schemas.drawingml.x2006.main.STTextAnchoringType.CTR

        val parsed = parseSlides(ppt)[0].textShapes.first()
        assertEquals(VerticalAnchor.CENTER, parsed.verticalAnchor)
    }

    @Test
    fun testBodyPrAnchorDefaultsToTop() {
        val ppt = XMLSlideShow()
        val slide = ppt.createSlide()
        val box = textBox(slide)
        box.textParagraphs[0].textRuns[0].setText("Top aligned")

        val parsed = parseSlides(ppt)[0].textShapes.first()
        assertEquals(VerticalAnchor.TOP, parsed.verticalAnchor)
    }

    // ---- 9. an explicit run size must still win over inheritance ----
    @Test
    fun testExplicitRunSizeWinsOverInherited() {
        val ppt = XMLSlideShow()
        val slide = ppt.createSlide()
        val box = textBox(slide)
        val p = box.textParagraphs[0]
        p.textRuns[0].setText("Explicit")
        p.textRuns[0].fontSize = 33.0

        val lstStyle = lstStyleOf(box)
        setLvl1Size(lstStyle, 1100)

        val parsed = parseSlides(ppt)[0].textShapes.first()
        assertEquals(33f, parsed.paragraphs[0].runs[0].fontSizePt, 0.01f)
    }

    // ---- 10. resolver never returns a negative or zero size ----
    @Test
    fun testResolvedSizeIsSane() {
        val ppt = XMLSlideShow()
        val slide = ppt.createSlide()
        val box = textBox(slide)
        box.textParagraphs[0].textRuns[0].setText("Sanity")

        val style = PptxStyleResolver.resolveRunStyle(
            run = box.textParagraphs[0].textRuns[0] as? org.apache.poi.xslf.usermodel.XSLFTextRun,
            shape = box, slide = slide, ppt = ppt,
            themeFonts = ThemeFontScheme(), isTitle = false, level = 0
        )
        assertNotNull("resolver always yields a size", style.fontSizePt)
        assertTrue("size must be positive", style.fontSizePt!! > 0f)
    }
}
