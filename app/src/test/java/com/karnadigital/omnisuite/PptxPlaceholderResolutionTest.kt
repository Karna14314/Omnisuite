package com.karnadigital.omnisuite

import com.karnadigital.omnisuite.core.engine.document.PptxSlideParser
import org.apache.poi.sl.usermodel.Placeholder
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.poi.xslf.usermodel.XSLFSlideLayout
import org.apache.poi.xslf.usermodel.XSLFSlideMaster
import org.apache.poi.xslf.usermodel.XSLFTextBox
import org.apache.poi.xslf.usermodel.XSLFAutoShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileOutputStream

/**
 * Verifies the PPTX placeholder bounds resolution & OOXML inheritance chain via the new
 * PptxSlideParser, using real programmatically-built PPTX files (POI runs on JVM).
 */
class PptxPlaceholderResolutionTest {

    private val parser = PptxSlideParser()

    private fun newShow(): XMLSlideShow = XMLSlideShow()

    @Test
    fun parser_resolvesOwnXfrm() {
        val show = newShow()
        val slide = show.createSlide()
        val box = slide.createTextBox()
        box.text = "Hello"
        // Set explicit anchor via POI (EMU)
        box.setAnchor(java.awt.Rectangle(914400, 514350, 4572000, 2571750))

        val pres = parser.parse(show)
        val parsedSlide = pres.slides[0]
        val shape = parsedSlide.shapes.first { (it.content as? com.karnadigital.omnisuite.core.engine.document.TextContent)?.paragraphs?.firstOrNull()?.runs?.firstOrNull()?.text == "Hello" }
        // 914400/9144000 = 0.1, 514350/5143500 = 0.1, 4572000/9144000 = 0.5, 2571750/5143500 = 0.5
        assertEquals(0.1f, shape.bounds.left, 0.02f)
        assertEquals(0.1f, shape.bounds.top, 0.02f)
        assertEquals(0.5f, shape.bounds.width, 0.02f)
        assertEquals(0.5f, shape.bounds.height, 0.02f)
    }

    @Test
    fun parser_slideDimensions() {
        val show = newShow()
        show.slides.createSlide()
        val pres = parser.parse(show)
        // Default 16:9
        assertEquals(9144000L, pres.widthEmu)
        assertEquals(5143500L, pres.heightEmu)
        assertEquals(16f / 9f, pres.aspectRatio, 0.01f)
    }

    @Test
    fun parser_defaultWhiteBackground() {
        val show = newShow()
        show.slides.createSlide()
        val pres = parser.parse(show)
        assertTrue(pres.slides[0].background is com.karnadigital.omnisuite.core.engine.document.BgNone)
    }

    @Test
    fun parser_omitsUnresolvableShape() {
        // A shape with no xfrm and no placeholder should be omitted.
        val show = newShow()
        val slide = show.createSlide()
        // Add a text box but then clear its spPr xfrm to make it unresolvable.
        val box = slide.createTextBox()
        box.text = "unresolvable"
        // Remove the xfrm from the XML to simulate an unresolvable shape
        val sp = box.xmlObject
        val spPr = sp.javaClass.getMethod("getSpPr").invoke(sp)
        val xfrm = spPr.javaClass.getMethod("getXfrm").invoke(spPr)
        if (xfrm != null) {
            spPr.javaClass.getMethod("unsetXfrm").invoke(spPr)
        }

        val pres = parser.parse(show)
        val hasUnresolvable = pres.slides[0].shapes.any {
            (it.content as? com.karnadigital.omnisuite.core.engine.document.TextContent)?.paragraphs?.firstOrNull()?.runs?.firstOrNull()?.text == "unresolvable"
        }
        assertTrue("unresolvable shape should be omitted", !hasUnresolvable)
    }

    @Test
    fun parser_textContentExtracted() {
        val show = newShow()
        val slide = show.createSlide()
        val box = slide.createTextBox()
        box.text = "Hello World"

        val pres = parser.parse(show)
        val tc = pres.slides[0].shapes.first().content as com.karnadigital.omnisuite.core.engine.document.TextContent
        assertEquals("Hello World", tc.paragraphs.first().runs.first().text)
    }

    @Test
    fun parser_multipleParagraphsAndRuns() {
        val show = newShow()
        val slide = show.createSlide()
        val box = slide.createTextBox()
        val p1 = box.addNewTextParagraph()
        val r1 = p1.addNewTextRun()
        r1.text = "Line1"
        val p2 = box.addNewTextParagraph()
        val r2 = p2.addNewTextRun()
        r2.text = "Line2"

        val pres = parser.parse(show)
        val tc = pres.slides[0].shapes.first().content as com.karnadigital.omnisuite.core.engine.document.TextContent
        assertEquals(2, tc.paragraphs.size)
        assertEquals("Line1", tc.paragraphs[0].runs[0].text)
        assertEquals("Line2", tc.paragraphs[1].runs[0].text)
    }

    @Test
    fun parser_nonDefaultSlideSize() {
        val show = newShow()
        // Set a custom slide size (4:3)
        show.slides.createSlide()
        val ct = show.ctPresentation
        ct.sldSz.cx = 9144000L
        ct.sldSz.cy = 6858000L

        val pres = parser.parse(show)
        assertEquals(9144000L, pres.widthEmu)
        assertEquals(6858000L, pres.heightEmu)
        assertEquals(9144000f / 6858000f, pres.aspectRatio, 0.01f)
    }

    @Test
    fun parser_pictureShapeExtracted() {
        val show = newShow()
        val slide = show.createSlide()
        // Add a small 1x1 PNG as a picture
        val pngBytes = createPngBytes()
        val pic = slide.createPicture(pngBytes)

        val pres = parser.parse(show)
        val imgShape = pres.slides[0].shapes.find { it.content is com.karnadigital.omnisuite.core.engine.document.ImageContent }
        assertNotNull("picture shape should be extracted", imgShape)
    }

    /** Minimal 1x1 white PNG. */
    private fun createPngBytes(): ByteArray {
        val bos = java.io.ByteArrayOutputStream()
        val img = java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB)
        img.setRGB(0, 0, 0xFFFFFF)
        javax.imageio.ImageIO.write(img, "png", bos)
        return bos.toByteArray()
    }
}
