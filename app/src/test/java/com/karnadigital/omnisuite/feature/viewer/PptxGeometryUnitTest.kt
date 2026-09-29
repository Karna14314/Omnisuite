package com.karnadigital.omnisuite.feature.viewer

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.poi.xslf.usermodel.XSLFTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for PPTX geometry defects found by diffing the viewer against a
 * reference render: fabricated fallback positions, a width clamp that collapsed
 * shapes to slivers, and table column/row geometry that always fell back to an
 * equal split because `CTGraphicalObjectFrame` has no `getTbl()`.
 *
 * Anchors are set reflectively: java.awt is excluded from the Android classpath,
 * so Rectangle2D cannot be referenced directly.
 */
class PptxGeometryUnitTest {

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

    /**
     * Writes an `<a:xfrm>` straight into the shape's XML via the typed CTShape
     * accessor. POI's setAnchor takes a java.awt.geom.Rectangle2D, which is not on
     * the Android classpath, and reflective access to spPr pulls in schema classes
     * absent from the unit-test classpath.
     */
    private fun setXfrm(shape: org.apache.poi.xslf.usermodel.XSLFTextShape, x: Long, y: Long, cx: Long, cy: Long) {
        val sp = shape.xmlObject as org.openxmlformats.schemas.presentationml.x2006.main.CTShape
        val spPr = sp.spPr ?: sp.addNewSpPr()
        val xfrm = if (spPr.isSetXfrm) spPr.xfrm else spPr.addNewXfrm()
        if (xfrm.isSetOff) {
            xfrm.off.x = x
            xfrm.off.y = y
        } else {
            val off = xfrm.addNewOff()
            off.x = x
            off.y = y
        }
        if (xfrm.isSetExt) {
            xfrm.ext.cx = cx
            xfrm.ext.cy = cy
        } else {
            val ext = xfrm.addNewExt()
            ext.cx = cx
            ext.cy = cy
        }
    }

    /** Graphic frames (tables) hold their xfrm on the frame, not on an spPr. */
    private fun setFrameXfrm(table: XSLFTable, x: Long, y: Long, cx: Long, cy: Long) {
        val frame = table.xmlObject as org.openxmlformats.schemas.presentationml.x2006.main.CTGraphicalObjectFrame
        val xfrm = frame.xfrm ?: frame.addNewXfrm()
        if (xfrm.isSetOff) {
            xfrm.off.x = x
            xfrm.off.y = y
        } else {
            val off = xfrm.addNewOff()
            off.x = x
            off.y = y
        }
        if (xfrm.isSetExt) {
            xfrm.ext.cx = cx
            xfrm.ext.cy = cy
        } else {
            val ext = xfrm.addNewExt()
            ext.cx = cx
            ext.cy = cy
        }
    }

    private fun textBox(slide: XSLFSlide, left: Long, top: Long, w: Long, h: Long) =
        slide.createTextBox().also {
            setXfrm(it, left, top, w, h)
            it.textParagraphs[0].textRuns[0].setText("Content")
        }

    private fun table(slide: XSLFSlide, rows: Int, cols: Int) =
        slide.createTable(rows, cols).also { setFrameXfrm(it, 100000L, 100000L, 4000000L, 2000000L) }

    /** setCellText lives on the cell, not the table. */
    private fun XSLFTable.set(r: Int, c: Int, text: String) {
        getCell(r, c).setText(text)
    }

    private fun cells(slide: PptxSlide) = slide.textShapes.filter { it.id.startsWith("table_cell_") }

    // ---- 1. a shape near the right edge keeps its width ----
    // Regression: a shape at x=8,000,000 on a 12,192,000 EMU slide clamped to
    // x=1.0, then the width clamp squeezed it into a 5%-wide sliver.
    @Test
    fun testShapeNearRightEdgeKeepsItsWidth() {
        val ppt = XMLSlideShow()
        val slide = ppt.createSlide()
        val x = 8000000L
        val w = 1000000L
        textBox(slide, x, 100000L, w, 500000L)

        val parsed = parseSlides(ppt)[0]
        val shape = parsed.textShapes.first()
        // The page size differs by template, so derive the expected fraction from
        // the slide the parser actually measured.
        val slideWEmu = parsed.slideWidthPt * 12700f
        assertEquals("left from the real xfrm", x / slideWEmu, shape.shapeLeft, 0.01f)
        assertTrue(
            "width must not collapse to a 5% sliver (was ${shape.shapeWidth})",
            shape.shapeWidth > 0.05f
        )
        assertTrue(
            "left + width stays on the slide", shape.shapeLeft + shape.shapeWidth <= 1.001f
        )
    }

    // ---- 2. no emitted shape may extend beyond the slide ----
    @Test
    fun testNoShapeExtendsBeyondSlideBounds() {
        val ppt = XMLSlideShow()
        val slide = ppt.createSlide()
        val w = 12192000L
        val h = 6858000L
        textBox(slide, 0L, 0L, w, 100000L)
        textBox(slide, w - 20000, 0L, w, 200000L)
        textBox(slide, 0L, h - 20000, 200000L, h)

        for (shape in parseSlides(ppt)[0].textShapes) {
            assertTrue("left >= 0 for ${shape.id}", shape.shapeLeft >= 0f)
            assertTrue("top >= 0 for ${shape.id}", shape.shapeTop >= 0f)
            assertTrue(
                "right <= 1 for ${shape.id} (${shape.shapeLeft + shape.shapeWidth})",
                shape.shapeLeft + shape.shapeWidth <= 1.001f
            )
            assertTrue(
                "bottom <= 1 for ${shape.id} (${shape.shapeTop + shape.shapeHeight})",
                shape.shapeTop + shape.shapeHeight <= 1.001f
            )
            assertTrue("width positive for ${shape.id}", shape.shapeWidth > 0f)
        }
    }

    // ---- 3. table column widths follow <a:gridCol>, not an equal split ----
    // Regression: getTbl() on a CTGraphicalObjectFrame always threw, so every
    // table rendered 50/50 regardless of the file.
    @Test
    fun testTableColumnWidthsFollowGridCol() {
        val ppt = XMLSlideShow()
        val slide = ppt.createSlide()
        val t = table(slide, 2, 2)
        t.set(0, 0, "Layer")
        t.set(0, 1, "Stack")
        t.set(1, 0, "Language")
        t.set(1, 1, "Python")
        t.ctTable.tblGrid.gridColList[0].w = 1200000
        t.ctTable.tblGrid.gridColList[1].w = 2800000

        val row0 = cells(parseSlides(ppt)[0]).filter { it.id.startsWith("table_cell_0_") }
        assertTrue("row 0 cells emitted", row0.size == 2)
        val c0 = row0.first { it.id == "table_cell_0_0" }
        val c1 = row0.first { it.id == "table_cell_0_1" }
        assertEquals(
            "30/70 column split honoured", 1200000f / 2800000f, c0.shapeWidth / c1.shapeWidth, 0.05f
        )
    }

    // ---- 4. row heights follow <a:tr h> ----
    @Test
    fun testTableRowHeightsFollowTrHeight() {
        val ppt = XMLSlideShow()
        val slide = ppt.createSlide()
        val t = table(slide, 2, 1)
        t.set(0, 0, "Tall")
        t.set(1, 0, "Short")
        t.ctTable.trList[0].h = 1800000
        t.ctTable.trList[1].h = 200000

        val all = cells(parseSlides(ppt)[0])
        val r0 = all.first { it.id == "table_cell_0_0" }
        val r1 = all.first { it.id == "table_cell_1_0" }
        assertTrue(
            "row 0 much taller than row 1 (${r0.shapeHeight} vs ${r1.shapeHeight})",
            r0.shapeHeight > r1.shapeHeight * 2f
        )
    }

    // ---- 5. no cell carries the hardcoded grey border ----
    @Test
    fun testTableCellBorderIsNotHardcoded() {
        val ppt = XMLSlideShow()
        val slide = ppt.createSlide()
        val t = table(slide, 1, 2)
        t.set(0, 0, "A")
        t.set(0, 1, "B")

        val all = cells(parseSlides(ppt)[0])
        assertTrue("table cells emitted", all.isNotEmpty())
        assertFalse(
            "no cell may carry the hardcoded #CBD5E1 border",
            all.any { it.shapeBorder?.strokeColorHex == "#CBD5E1" }
        )
    }

    // ---- 6. a merged header emits one cell spanning both columns ----
    @Test
    fun testMergedHeaderEmitsSingleCell() {
        val ppt = XMLSlideShow()
        val slide = ppt.createSlide()
        val t = table(slide, 2, 2)
        t.set(0, 0, "Header")
        t.set(0, 1, "")
        t.set(1, 0, "A")
        t.set(1, 1, "B")
        t.mergeCells(0, 0, 0, 1)

        val all = cells(parseSlides(ppt)[0])
        val row0 = all.filter { it.id.startsWith("table_cell_0_") }
        assertEquals("merged header emits exactly one cell", 1, row0.size)
        val origin = row0.first()
        val below = all.first { it.id == "table_cell_1_0" }
        assertTrue(
            "merged cell spans both columns (${origin.shapeWidth} vs ${below.shapeWidth})",
            origin.shapeWidth > below.shapeWidth
        )
    }

    // ---- 7. table cells carry a real font size, not a hardcoded constant ----
    @Test
    fun testTableCellHasResolvedFontSize() {
        val ppt = XMLSlideShow()
        val slide = ppt.createSlide()
        val t = table(slide, 1, 1)
        t.set(0, 0, "Cell")

        val cell = cells(parseSlides(ppt)[0]).first { it.id == "table_cell_0_0" }
        val run = cell.paragraphs.firstOrNull()?.runs?.firstOrNull()
        assertTrue("cell has a run", run != null)
        assertTrue("cell font size positive (got ${run!!.fontSizePt})", run.fontSizePt > 0f)
    }
}
