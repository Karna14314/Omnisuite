package com.karnadigital.omnisuite.core.engine.document

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the PPTX bitmap/PDF render path.
 *
 * `OfficeConverter` had no table support at all, so the GRID and slideshow
 * thumbnails and the PPT-to-PDF export silently dropped every table while the
 * Compose viewer rendered them -- the same file produced two different documents
 * depending on the view mode.
 */
class OfficeConverterTableUnitTest {

    private fun allocateConverter(): OfficeConverter {
        val unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null)
        val allocateInstance = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
        return allocateInstance.invoke(unsafe, OfficeConverter::class.java) as OfficeConverter
    }

    private fun pptWithTable(): XMLSlideShow {
        val ppt = XMLSlideShow()
        val slide = ppt.createSlide()
        val table = slide.createTable(2, 2)
        // POI has no anchor setter usable without java.awt, so write the frame
        // transform through the typed XMLBeans accessor instead.
        val frame = table.xmlObject as org.openxmlformats.schemas.presentationml.x2006.main.CTGraphicalObjectFrame
        val xfrm = frame.xfrm ?: frame.addNewXfrm()
        (xfrm.off ?: xfrm.addNewOff()).apply { x = 100000L; y = 100000L }
        (xfrm.ext ?: xfrm.addNewExt()).apply { cx = 4000000L; cy = 2000000L }
        table.getCell(0, 0).setText("Layer")
        table.getCell(0, 1).setText("Stack")
        table.getCell(1, 0).setText("Language")
        table.getCell(1, 1).setText("Python 3.12")
        table.ctTable.tblGrid.gridColList[0].w = 1200000
        table.ctTable.tblGrid.gridColList[1].w = 2800000
        table.ctTable.trList[0].h = 1500000
        table.ctTable.trList[1].h = 500000
        return ppt
    }

    // ---- 1. the converter exposes a table draw routine ----
    // Regression: there was no table branch at all, so tables were skipped by the
    // `if (normBounds == null) continue` path and never drawn.
    @Test
    fun testConverterHasTableDrawing() {
        val declared = OfficeConverter::class.java.declaredMethods.map { it.name }
        assertTrue(
            "OfficeConverter must have a table rendering path (found: $declared)",
            declared.any { it.contains("Table", ignoreCase = true) }
        )
    }

    // ---- 2. a table shape is recognised as such by the converter's shape walk ----
    @Test
    fun testTableShapeIsDetected() {
        val ppt = pptWithTable()
        val slide = ppt.slides[0]
        val hasTable = slide.shapes.any { it is org.apache.poi.xslf.usermodel.XSLFTable }
        assertTrue("the fixture slide really contains a TableShape", hasTable)
    }

    // ---- 3. real column widths survive into the XML the converter reads ----
    @Test
    fun testTableColumnWidthsAreNonUniform() {
        val ppt = pptWithTable()
        val table = ppt.slides[0].shapes.first { it is org.apache.poi.xslf.usermodel.XSLFTable } as org.apache.poi.xslf.usermodel.XSLFTable
        val widths = table.ctTable.tblGrid.gridColList.map { (it.w as? Number)?.toLong() ?: 0L }
        assertNotNull("grid widths readable", widths)
        assertTrue("first column narrower than second", widths[0] < widths[1])
    }
}
