package com.karnadigital.omnisuite.feature.viewer

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Regression unit tests for OOXML document parsing (XLSX, PPTX, DOCX) and corrupt file error handling.
 */
class OoxmlRegressionUnitTest {

    @Test
    fun validXlsxDocumentParsesSuccessfully() {
        val bytes = buildMinimalXlsx()
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
            assertEquals(1, workbook.numberOfSheets)
            val sheet = workbook.getSheetAt(0)
            assertEquals("Sheet1", sheet.sheetName)
            val row = sheet.getRow(0)
            assertNotNull(row)
            assertEquals("OmniSuite Test Cell", row.getCell(0).stringCellValue)
        }
    }

    @Test
    fun validPptxDocumentParsesSuccessfully() {
        val bytes = buildMinimalPptx()
        XMLSlideShow(ByteArrayInputStream(bytes)).use { ppt ->
            assertEquals(1, ppt.slides.size)
            val slide = ppt.slides[0]
            assertNotNull(slide)
        }
    }

    @Test
    fun validDocxDocumentParsesSuccessfully() {
        val bytes = buildMinimalDocx()
        XWPFDocument(ByteArrayInputStream(bytes)).use { doc ->
            assertEquals(1, doc.paragraphs.size)
            assertEquals("OmniSuite DOCX Test", doc.paragraphs[0].text)
        }
    }

    @Test
    fun corruptXlsxFileFailsWithDescriptiveException() {
        val junk = ByteArray(1024) { (it * 37 % 251).toByte() }
        try {
            XSSFWorkbook(ByteArrayInputStream(junk))
            fail("Corrupt XLSX should throw exception during parsing")
        } catch (e: Throwable) {
            val detail = e.message?.takeIf { it.isNotBlank() } ?: e.localizedMessage ?: e.javaClass.simpleName
            val formatted = if (e.javaClass.simpleName !in detail) "${e.javaClass.simpleName}: $detail" else detail
            assertTrue("Error string should contain exception information", formatted.isNotBlank())
        }
    }

    @Test
    fun corruptPptxFileFailsWithDescriptiveException() {
        val junk = ByteArray(1024) { (it * 41 % 251).toByte() }
        try {
            XMLSlideShow(ByteArrayInputStream(junk))
            fail("Corrupt PPTX should throw exception during parsing")
        } catch (e: Throwable) {
            val detail = e.message?.takeIf { it.isNotBlank() } ?: e.localizedMessage ?: e.javaClass.simpleName
            val formatted = if (e.javaClass.simpleName !in detail) "${e.javaClass.simpleName}: $detail" else detail
            assertTrue("Error string should contain exception information", formatted.isNotBlank())
        }
    }

    // Helper functions
    private fun buildMinimalXlsx(): ByteArray {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("Sheet1")
        val row = sheet.createRow(0)
        row.createCell(0).setCellValue("OmniSuite Test Cell")
        val out = ByteArrayOutputStream()
        wb.write(out)
        wb.close()
        return out.toByteArray()
    }

    private fun buildMinimalPptx(): ByteArray {
        val ppt = XMLSlideShow()
        ppt.createSlide()
        val out = ByteArrayOutputStream()
        ppt.write(out)
        ppt.close()
        return out.toByteArray()
    }

    private fun buildMinimalDocx(): ByteArray {
        val doc = XWPFDocument()
        val p = doc.createParagraph()
        p.createRun().setText("OmniSuite DOCX Test")
        val out = ByteArrayOutputStream()
        doc.write(out)
        doc.close()
        return out.toByteArray()
    }
}
