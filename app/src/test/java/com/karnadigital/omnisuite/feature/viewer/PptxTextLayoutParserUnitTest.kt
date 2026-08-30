package com.karnadigital.omnisuite.feature.viewer

import org.apache.poi.sl.usermodel.TextAlign
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method

/**
 * Exercises the PPTX parser's new text-layout fields (line spacing, bodyPr insets,
 * autofit, numbering, table geometry) by building real .pptx content with POI and
 * running it through PptxViewerViewModel.parseAllSlides via reflection.
 *
 * NOTE: requires a JVM with Apache POI on the classpath (runs under `./gradlew test`).
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
        // parseAllSlides does not touch the repository/converter deps, so we allocate the
        // instance WITHOUT calling its constructor (which has non-null Hilt deps and would
        // otherwise fail Kotlin's parameter null-checks). sun.misc.Unsafe.allocateInstance
        // bypasses the constructor entirely.
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
        box.setAnchor(java.awt.Rectangle(left, top, w, h))
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
            // Set lnSpc to 120% (spcPct) on the first paragraph via CT pPr.
            val p = box.textParagraphs[0]
            val ctP = p.getXmlObject()
            val pPr = if (ctP.isSetPPr) ctP.pPr else ctP.addNewPPr()
            val lnSpc = pPr.addNewLnSpc()
            lnSpc.spcPct = 120000
        }
        val slides = parseSlides(ppt)
        val body = slides[0].textShapes[0]
        assertEquals("lnSpc 120% -> 1.2 multiplier", 1.2f, body.paragraphs[0].lineSpacingMul, 0.05f)
        // Default (no lnSpc) paragraph should be single (1.0).
        assertEquals("default line spacing single", 1.0f, body.paragraphs[1].lineSpacingMul, 0.0001f)
    }

    @Test
    fun testLineSpacingExactPtsFromLnSpc() {
        val ppt = newPptxWithSlide { _, slide ->
            val box = addText(slide, 100000, 100000, 2000000, 400000,
                listOf("Exact leading paragraph" to 12f))
            val p = box.textParagraphs[0]
            val ctP = p.getXmlObject()
            val pPr = if (ctP.isSetPPr) ctP.pPr else ctP.addNewPPr()
            val lnSpc = pPr.addNewLnSpc()
            // spcPts in hundredths of a point: 2400 = 24.00 pt leading. 24pt / 12pt font = 2.0x.
            lnSpc.spcPts = 2400
        }
        val slides = parseSlides(ppt)
        val body = slides[0].textShapes[0]
        assertEquals("spcPts 24pt / 12pt font -> 2.0x", 2.0f, body.paragraphs[0].lineSpacingMul, 0.05f)
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

    // ---- bodyPr insets + normAutofit ----
    @Test
    fun testBodyPrInsetsReadFromSource() {
        val ppt = newPptxWithSlide { _, slide ->
            val box = addText(slide, 100000, 100000, 2000000, 400000,
                listOf("Inset paragraph" to 12f))
            val bodyPr = box.textBody.bodyPr ?: box.textBody.addNewBodyPr()
            bodyPr.lIns = 182880L // 0.2 in
            bodyPr.tIns = 91440L  // 0.1 in
            bodyPr.rIns = 182880L
            bodyPr.bIns = 91440L
        }
        val slides = parseSlides(ppt)
        val body = slides[0].textShapes[0]
        // Slide width default 9144000 EMU -> 0.2 in = 182880 EMU -> fraction ~0.02
        assertEquals("left inset fraction", 182880f / 9144000f, body.insets.left, 1e-4f)
        assertEquals("top inset fraction", 91440f / 5143500f, body.insets.top, 1e-4f)
    }

    @Test
    fun testNormAutofitFontScaleAndLnSpcReduction() {
        val ppt = newPptxWithSlide { _, slide ->
            val box = addText(slide, 100000, 100000, 2000000, 400000,
                listOf("Autofit paragraph" to 12f))
            val bodyPr = box.textBody.bodyPr ?: box.textBody.addNewBodyPr()
            val norm = bodyPr.addNewNormAutofit()
            norm.fontScale = 75000  // 75%
            norm.lnSpcReduction = 20000 // reduce line spacing by 20%
        }
        val slides = parseSlides(ppt)
        val body = slides[0].textShapes[0]
        assertEquals(AutoFitMode.NORM_AUTOFIT, body.autoFit)
        assertEquals(75000, body.fontScale)
        assertEquals(20000, body.lnSpcReduction)
    }

    @Test
    fun testSpAutoFitDetected() {
        val ppt = newPptxWithSlide { _, slide ->
            val box = addText(slide, 100000, 100000, 2000000, 400000,
                listOf("Shape fit paragraph" to 12f))
            val bodyPr = box.textBody.bodyPr ?: box.textBody.addNewBodyPr()
            bodyPr.addNewSpAutoFit()
        }
        val slides = parseSlides(ppt)
        assertEquals(AutoFitMode.SP_AUTO_FIT, slides[0].textShapes[0].autoFit)
    }

    // ---- T6: table geometry — unequal gridCol widths + cell margins ----
    @Test
    fun testTableUnequalColumnsAndCellMargins() {
        val ppt = newPptxWithSlide { _, slide ->
            val table = slide.createTable()
            table.setAnchor(java.awt.Rectangle(100000, 100000, 400000, 200000))
            for (r in 0 until 3) {
                val row = table.addRow()
                row.getCtTr().setH(50000L)
                for (c in 0 until 2) {
                    val cell = row.addCell()
                    cell.text = "R${r}C$c"
                    val tcPr = cell.ctTc.addNewTcPr()
                    tcPr.marL = 9144L
                    tcPr.marT = 4572L
                    tcPr.marR = 9144L
                    tcPr.marB = 4572L
                }
            }
            // Force unequal column widths: col0 = 200000 EMU, col1 = 400000 EMU.
            val gridCols = table.ctTable.gridColList
            if (gridCols.size >= 2) {
                gridCols[0].w = 200000L
                gridCols[1].w = 400000L
            }
        }
        val slides = parseSlides(ppt)
        val cells = slides[0].textShapes.filter { it.id.startsWith("table_cell_") }
        assertTrue("expected table cells", cells.size >= 6)
        // Column 1 (width 400000) should be twice as wide as column 0 (width 200000).
        val c00 = cells.first { it.id == "table_cell_0_0" }
        val c01 = cells.first { it.id == "table_cell_0_1" }
        assertTrue("col1 wider than col0", c01.shapeWidth > c00.shapeWidth * 1.5f)
        // Cell margins should be present (non-default inset > 0).
        assertTrue("cell left inset > 0", c00.insets.left > 0f)
        assertTrue("cell top inset > 0", c00.insets.top > 0f)
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
            box.textParagraphs[0].alignment = TextAlign.CENTER
        }
        val slides = parseSlides(ppt)
        assertEquals("CENTER", slides[0].textShapes[0].paragraphs[0].alignment)
    }
}
