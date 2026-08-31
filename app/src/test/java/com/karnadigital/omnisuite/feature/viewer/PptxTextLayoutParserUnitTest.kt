package com.karnadigital.omnisuite.feature.viewer

import org.apache.poi.sl.usermodel.TextParagraph
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method

/**
 * Exercises the PPTX parser's text-layout fields (line spacing, bodyPr insets,
 * autofit, numbering, table geometry) by building real .pptx content with POI and
 * running it through PptxViewerViewModel.parseAllSlides via reflection.
 */
class PptxTextLayoutParserUnitTest {

    private fun newPptxWithSlide(
        configure: (XMLSlideShow, org.apache.poi.xslf.usermodel.XSLFSlide) -> Unit
    ): XMLSlideShow {
        val ppt = XMLSlideShow()
        val slide = ppt.createSlide()
        configure(ppt, slide)
        return ppt
    }

    /** Reflectively invoke PptxViewerViewModel.parseAllSlides (private). */
    private fun parseSlides(ppt: XMLSlideShow): List<PptxSlide> {
        val unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null)
        val allocateInstance = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
        val vm = allocateInstance.invoke(unsafe, PptxViewerViewModel::class.java) as PptxViewerViewModel
        val method: Method = PptxViewerViewModel::class.java
            .getDeclaredMethod("parseAllSlides", org.apache.poi.sl.usermodel.SlideShow::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(vm, ppt) as List<PptxSlide>
    }

    /** Adds a text box with one paragraph per entry (text, fontSizePt). */
    private fun addText(
        slide: org.apache.poi.xslf.usermodel.XSLFSlide,
        left: Int, top: Int, w: Int, h: Int,
        entries: List<Pair<String, Float>>
    ): org.apache.poi.xslf.usermodel.XSLFTextShape {
        val box = slide.createTextBox()
        try {
            val anchorMethod = box.javaClass.methods.firstOrNull { it.name == "setAnchor" }
            val rectClass = anchorMethod?.parameterTypes?.firstOrNull()
            if (rectClass != null) {
                val constructor = rectClass.getConstructor(Double::class.javaPrimitiveType, Double::class.javaPrimitiveType, Double::class.javaPrimitiveType, Double::class.javaPrimitiveType)
                val rect = constructor.newInstance(left.toDouble(), top.toDouble(), w.toDouble(), h.toDouble())
                anchorMethod.invoke(box, rect)
            }
        } catch (_: Throwable) {}
        entries.forEachIndexed { i, (text, size) ->
            val p = if (i == 0) box.textParagraphs[0] else box.addNewTextParagraph()
            val r = if (p.textRuns.isNotEmpty()) p.textRuns[0] else p.addNewTextRun()
            r.setText(text)
            if (size > 0f) r.fontSize = size.toDouble()
        }
        return box
    }

    // ---- T2: line spacing (single = 1.0) read from <a:lnSpc> ----
    @Test
    fun testLineSpacingSingleFromLnSpc() {
        val ppt = newPptxWithSlide { _, slide ->
            val box = addText(slide, 100000, 100000, 2000000, 400000,
                listOf("Line one of the body text" to 12f, "Line two of the body text" to 12f))
            val p = box.textParagraphs[0]
            val ctP = p.getXmlObject()
            val pPr = if (ctP.isSetPPr) ctP.pPr else ctP.addNewPPr()
            val lnSpc = pPr.addNewLnSpc()
            val spcPct = lnSpc.addNewSpcPct()
            try { spcPct.javaClass.getMethod("setVal", Any::class.java).invoke(spcPct, 120000) } catch (_: Throwable) {
                try { spcPct.javaClass.getMethod("setVal", Int::class.javaPrimitiveType).invoke(spcPct, 120000) } catch (_: Throwable) {}
            }
        }
        val slides = parseSlides(ppt)
        val body = slides[0].textShapes[0]
        assertEquals("lnSpc 120% -> 1.2 multiplier", 1.2f, body.paragraphs[0].lineSpacingMul, 0.05f)
        assertEquals("default line spacing single", 1.0f, body.paragraphs[1].lineSpacingMul, 0.0001f)
    }

    // ---- T4/T5: numbering scheme captured from <a:buAutoNum type> ----
    @Test
    fun testNumberingTypeArabicPeriod() {
        val ppt = newPptxWithSlide { _, slide ->
            val box = addText(slide, 100000, 100000, 2000000, 400000,
                listOf("First item" to 12f, "Second item" to 12f, "Third item" to 12f))
            box.textParagraphs.forEach { p ->
                val ctP = p.getXmlObject()
                val pPr = if (ctP.isSetPPr) ctP.pPr else ctP.addNewPPr()
                val buAutoNum = pPr.addNewBuAutoNum()
                buAutoNum.setType(org.openxmlformats.schemas.drawingml.x2006.main.STTextAutonumberScheme.Enum.forString("arabicPeriod"))
            }
        }
        val slides = parseSlides(ppt)
        val body = slides[0].textShapes[0]
        assertEquals("arabicPeriod", body.paragraphs[0].numberingType)
        assertEquals("arabicPeriod", body.paragraphs[1].numberingType)
        assertEquals("arabicPeriod", body.paragraphs[2].numberingType)
    }

    @Test
    fun testNumberingTypeAlphaLcParenR() {
        val ppt = newPptxWithSlide { _, slide ->
            val box = addText(slide, 100000, 100000, 2000000, 400000,
                listOf("Alpha item one" to 12f))
            val p = box.textParagraphs[0]
            val ctP = p.getXmlObject()
            val pPr = if (ctP.isSetPPr) ctP.pPr else ctP.addNewPPr()
            val buAutoNum = pPr.addNewBuAutoNum()
            buAutoNum.setType(org.openxmlformats.schemas.drawingml.x2006.main.STTextAutonumberScheme.Enum.forString("alphaLcParenR"))
        }
        val slides = parseSlides(ppt)
        assertEquals("alphaLcParenR", slides[0].textShapes[0].paragraphs[0].numberingType)
    }

    // ---- spaceBefore / spaceAfter already parsed; sanity check ----
    @Test
    fun testSpaceBeforeAfterParsed() {
        val ppt = newPptxWithSlide { _, slide ->
            val box = addText(slide, 100000, 100000, 2000000, 400000,
                listOf("Spaced paragraph" to 12f))
            box.textParagraphs[0].spaceAfter = 12.0 // 12 pt
        }
        val slides = parseSlides(ppt)
        assertEquals(12f, slides[0].textShapes[0].paragraphs[0].spaceAfterPt, 0.001f)
    }

    // ---- bullet char still works ----
    @Test
    fun testBulletCharParsed() {
        val ppt = newPptxWithSlide { _, slide ->
            val box = addText(slide, 100000, 100000, 2000000, 400000,
                listOf("Bullet item" to 12f))
            box.textParagraphs[0].isBullet = true
            box.textParagraphs[0].bulletCharacter = "•"
        }
        val slides = parseSlides(ppt)
        val p = slides[0].textShapes[0].paragraphs[0]
        assertTrue("has bullet", p.hasBullet)
        assertEquals("•", p.bulletChar)
        assertNull("numbered type null for bullet", p.numberingType)
    }

    // ---- alignment passthrough ----
    @Test
    fun testAlignmentCenterParsed() {
        val ppt = newPptxWithSlide { _, slide ->
            val box = addText(slide, 100000, 100000, 2000000, 400000,
                listOf("Centered text" to 12f))
            box.textParagraphs[0].textAlign = TextParagraph.TextAlign.CENTER
        }
        val slides = parseSlides(ppt)
        assertEquals("CENTER", slides[0].textShapes[0].paragraphs[0].alignment)
    }
}
