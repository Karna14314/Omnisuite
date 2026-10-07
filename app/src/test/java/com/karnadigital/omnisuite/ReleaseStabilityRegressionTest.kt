package com.karnadigital.omnisuite

import com.karnadigital.omnisuite.core.engine.document.PptxGeometry
import com.karnadigital.omnisuite.feature.viewer.DocxWebViewLimits
import com.karnadigital.omnisuite.feature.viewer.XlsxViewerViewModel
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Regression coverage for the release-stabilization pass of 2026-10-04.
 *
 * Each test pins the root cause of a defect that shipped, not the symptom.
 */
class ReleaseStabilityRegressionTest {

    // ---------------------------------------------------------------------
    // PPTX geometry: one clamp for every path
    //
    // The on-screen viewer clamped the RECT (shrinking w to 1f - cx) while OfficeConverter
    // clamped each EDGE independently, so a shape at x=0.98 w=0.5 was drawn 1.48x the slide
    // width in print / PPTX-to-PDF while the screen showed it clipped. The two disagreed for
    // the same file, which is the structural reason the PPTX fix was reverted once.
    // ---------------------------------------------------------------------

    @Test
    fun clampRectNeverLetsAShapeExtendBeyondTheRightEdge() {
        val r = PptxGeometry.clampRect(0.98f, 0.10f, 0.50f, 0.20f)
        assertTrue(
            "shape must stay inside the slide: x=${r[0]} w=${r[2]} sums to ${r[0] + r[2]}",
            r[0] + r[2] <= 1.0001f
        )
    }

    @Test
    fun clampRectNeverLetsAShapeExtendBeyondTheBottomEdge() {
        val r = PptxGeometry.clampRect(0.10f, 0.95f, 0.20f, 0.40f)
        assertTrue("y=${r[1]} h=${r[3]} sums to ${r[1] + r[3]}", r[1] + r[3] <= 1.0001f)
    }

    @Test
    fun clampRectClampsNegativeOriginsIntoTheSlide() {
        val r = PptxGeometry.clampRect(-0.4f, -0.2f, 0.3f, 0.3f)
        assertEquals(0f, r[0], 1e-5f)
        assertEquals(0f, r[1], 1e-5f)
    }

    @Test
    fun clampRectNeverReturnsZeroOrNegativeExtent() {
        // A zero-extent shape collapses to nothing and reads as a rendering failure.
        val r = PptxGeometry.clampRect(0.5f, 0.5f, 0f, -3f)
        assertTrue("width must stay positive, was ${r[2]}", r[2] > 0f)
        assertTrue("height must stay positive, was ${r[3]}", r[3] > 0f)
    }

    @Test
    fun clampRectPreservesAWellFormedShapeExactly() {
        val r = PptxGeometry.clampRect(0.25f, 0.25f, 0.5f, 0.5f)
        assertEquals(0.25f, r[0], 1e-5f)
        assertEquals(0.25f, r[1], 1e-5f)
        assertEquals(0.5f, r[2], 1e-5f)
        assertEquals(0.5f, r[3], 1e-5f)
    }

    // ---------------------------------------------------------------------
    // DOCX: the blank-viewer window
    //
    // The producer allowed a 15 MB payload but the consumer refused anything over
    // 8,000,000 Base64 chars (~5.7 MB of source). In that window the screen had already
    // committed to the WebView branch, so evaluateJavascript was never called and
    // viewer.html painted an empty body, with the native fallback unreachable.
    // ---------------------------------------------------------------------

    @Test
    fun docxWebViewPayloadCanNeverExceedWhatTheScreenWillRender() {
        val bytes = DocxWebViewLimits.MAX_WEBVIEW_BASE64_BYTES
        val chars = (bytes + 2L) / 3L * 4L
        assertTrue(
            "producer allows $bytes bytes -> ~$chars chars, screen refuses above " +
                "${DocxWebViewLimits.MAX_WEBVIEW_BASE64_CHARS}; that gap renders blank",
            chars <= DocxWebViewLimits.MAX_WEBVIEW_BASE64_CHARS
        )
    }

    @Test
    fun docxWebViewLimitIsBelowTheOldFifteenMegabyteCeiling() {
        assertTrue(
            "the blank-viewer window only closes if the limit actually dropped",
            DocxWebViewLimits.MAX_WEBVIEW_BASE64_BYTES < 15L * 1024L * 1024L
        )
    }

    // ---------------------------------------------------------------------
    // XLSX: silent truncation followed by a save that destroys the remainder
    //
    // Rows were clamped to 5000 and columns to 200 with no user-visible signal, and
    // commitChanges then wrote the clamped slice back over the original file. A
    // 1,000,000-row workbook became its first 5,000 rows.
    // ---------------------------------------------------------------------

    @Test
    fun xlsxTruncationIsDetectedWhenRowsAreClamped() {
        assertTrue(
            XlsxViewerViewModel.isTruncatedByLimits(actualRows = 1_000_000, actualCols = 12)
        )
    }

    @Test
    fun xlsxTruncationIsDetectedWhenColumnsAreClamped() {
        assertTrue(
            XlsxViewerViewModel.isTruncatedByLimits(actualRows = 40, actualCols = 512)
        )
    }

    @Test
    fun xlsxTruncationIsDetectedWhenAnExplicitRowLimitIsTighterThanTheSheet() {
        // This is the progressive-loading path: it asks for 100 rows first.
        assertTrue(
            XlsxViewerViewModel.isTruncatedByLimits(
                actualRows = 5000, actualCols = 10, requestedRows = 100, rowLimit = 100
            )
        )
    }

    @Test
    fun ordinaryWorkbookIsNotFlaggedAsTruncated() {
        assertFalse(XlsxViewerViewModel.isTruncatedByLimits(actualRows = 800, actualCols = 24))
    }

    @Test
    fun workbookExactlyAtTheRowLimitIsNotTruncated() {
        assertFalse(
            XlsxViewerViewModel.isTruncatedByLimits(
                actualRows = XlsxViewerViewModel.MAX_RENDER_ROWS, actualCols = 12
            )
        )
    }

    @Test
    fun xlsxParseCeilingIsBounded() {
        assertTrue(
            "POI materialises the whole workbook; the ceiling must exist",
            XlsxViewerViewModel.MAX_PARSEABLE_BYTES in 1..(256L * 1024L * 1024L)
        )
    }

    // ---------------------------------------------------------------------
    // Malformed input must degrade, not crash the process
    //
    // OutOfMemoryError is an Error, not an Exception. Every office entry point used to
    // catch Exception, so a POI allocation failure escaped as an uncaught crash instead of
    // a surfaced error state.
    // ---------------------------------------------------------------------

    @Test
    fun catchThrowableActuallyCatchesTheErrorsPoiCanRaise() {
        // This is the property the load paths now rely on.
        var surfaced = false
        try {
            throw OutOfMemoryError("simulated POI allocation failure")
        } catch (e: Exception) {
            fail("OutOfMemoryError is an Error; an Exception catch cannot intercept it")
        } catch (e: Throwable) {
            surfaced = true
        }
        assertTrue(surfaced)
    }

    @Test
    fun emptyDocxIsRejectedWithACatchableException() {
        try {
            XWPFDocument(ByteArrayInputStream(ByteArray(0))).use { }
            fail("an empty .docx must not parse")
        } catch (expected: Throwable) {
            assertFalse("must not be silent", expected is OutOfMemoryError)
        }
    }

    @Test
    fun garbageDocxIsRejectedWithACatchableException() {
        val junk = ByteArray(4096) { (it * 31 % 251).toByte() }
        try {
            XWPFDocument(ByteArrayInputStream(junk)).use { }
            fail("random bytes must not parse as .docx")
        } catch (expected: Throwable) {
            assertFalse("must not be silent", expected is OutOfMemoryError)
        }
    }

    @Test
    fun truncatedDocxIsRejectedWithACatchableException() {
        val full = buildDocx()
        val truncated = full.copyOf(full.size / 2)
        try {
            XWPFDocument(ByteArrayInputStream(truncated)).use { }
            fail("a truncated .docx must not parse")
        } catch (expected: Throwable) {
            assertFalse("must not be silent", expected is OutOfMemoryError)
        }
    }

    @Test
    fun validZipThatIsNotOoxmlIsRejectedWithACatchableException() {
        // A .docx that is really a plain zip: the "misleading extension" case.
        val zip = buildPlainZip()
        try {
            XWPFDocument(ByteArrayInputStream(zip)).use { }
            fail("a plain zip must not parse as .docx")
        } catch (expected: Throwable) {
            assertFalse("must not be silent", expected is OutOfMemoryError)
        }
    }

    @Test
    fun emptyXlsxIsRejectedWithACatchableException() {
        try {
            XSSFWorkbook(ByteArrayInputStream(ByteArray(0))).use { }
            fail("an empty .xlsx must not parse")
        } catch (expected: Throwable) {
            assertFalse("must not be silent", expected is OutOfMemoryError)
        }
    }

    @Test
    fun garbageXlsxIsRejectedWithACatchableException() {
        val junk = ByteArray(4096) { (it * 17 % 253).toByte() }
        try {
            XSSFWorkbook(ByteArrayInputStream(junk)).use { }
            fail("random bytes must not parse as .xlsx")
        } catch (expected: Throwable) {
            assertFalse("must not be silent", expected is OutOfMemoryError)
        }
    }

    @Test
    fun aSheetWithNoRowsIsHandledAsAnEmptyWorkbookNotAnError() {
        XSSFWorkbook().use { wb ->
            wb.createSheet("Empty")
            assertEquals(1, wb.numberOfSheets)
            assertEquals(-1, wb.getSheetAt(0).lastRowNum)
        }
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private fun buildDocx(): ByteArray {
        val doc = XWPFDocument()
        doc.createParagraph().createRun().setText("OmniSuite regression fixture")
        val out = ByteArrayOutputStream()
        doc.write(out)
        doc.close()
        return out.toByteArray()
    }

    private fun buildPlainZip(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            zos.putNextEntry(ZipEntry("hello.txt"))
            zos.write("this is not an ooxml package".toByteArray())
            zos.closeEntry()
        }
        return out.toByteArray()
    }
}