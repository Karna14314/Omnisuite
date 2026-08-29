package com.karnadigital.omnisuite.feature.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxViewerUnitTest {

    @Test
    fun testPageGeometryCalculations() {
        val a4Geometry = DocxPageGeometry(
            widthTwips = 11906L,
            heightTwips = 16838L,
            marginTopTwips = 720L, // 0.5 inch = 36 pt
            marginBottomTwips = 720L,
            marginLeftTwips = 720L,
            marginRightTwips = 720L
        )

        assertEquals(595.3f, a4Geometry.widthPt, 0.1f)
        assertEquals(841.9f, a4Geometry.heightPt, 0.1f)
        assertEquals(36f, a4Geometry.marginTopPt, 0.1f)
        assertEquals(523.3f, a4Geometry.printableWidthPt, 0.1f)
        assertEquals(769.9f, a4Geometry.printableHeightPt, 0.1f)
    }

    @Test
    fun testLetterPageGeometryCalculations() {
        val letterGeometry = DocxPageGeometry(
            widthTwips = 12240L,  // 8.5 inch = 612 pt
            heightTwips = 15840L, // 11 inch = 792 pt
            marginTopTwips = 1440L, // 1.0 inch = 72 pt
            marginBottomTwips = 1440L,
            marginLeftTwips = 1440L,
            marginRightTwips = 1440L
        )

        assertEquals(612f, letterGeometry.widthPt, 0.1f)
        assertEquals(792f, letterGeometry.heightPt, 0.1f)
        assertEquals(72f, letterGeometry.marginTopPt, 0.1f)
        assertEquals(468f, letterGeometry.printableWidthPt, 0.1f)
        assertEquals(648f, letterGeometry.printableHeightPt, 0.1f)
        assertFalse(letterGeometry.isLandscape)
    }

    @Test
    fun testTabStopCalculations() {
        val tabStop = DocxTabStop(
            positionTwips = 7200L, // 360 pt
            alignment = TabStopAlignment.RIGHT
        )

        assertEquals(360f, tabStop.positionPt, 0.01f)
        assertEquals(TabStopAlignment.RIGHT, tabStop.alignment)
    }

    @Test
    fun testDocxDocumentStructure() {
        val runs = listOf(
            DocxRun(text = "Hello ", isBold = true, isItalic = false, isUnderline = false, isStrike = false, fontSizePt = 12f),
            DocxRun(text = "World", isBold = false, isItalic = true, isUnderline = false, isStrike = false, fontSizePt = 12f)
        )
        val para = DocxParagraph(
            runs = runs,
            alignment = "LEFT",
            headingLevel = 0,
            isHeading = false
        )
        val element = DocxBodyElement.Para(para)
        val document = DocxDocument(elements = listOf(element))

        assertEquals(1, document.elements.size)
        assertTrue(document.elements[0] is DocxBodyElement.Para)
        val retrievedPara = (document.elements[0] as DocxBodyElement.Para).paragraph
        assertEquals(2, retrievedPara.runs.size)
        assertEquals("Hello ", retrievedPara.runs[0].text)
        assertTrue(retrievedPara.runs[0].isBold)
        assertEquals("World", retrievedPara.runs[1].text)
        assertTrue(retrievedPara.runs[1].isItalic)
    }

    @Test
    fun testDocxLoadStateSuccessWithBase64() {
        val doc = DocxDocument(elements = emptyList())
        val state = DocxLoadState.Success(
            document = doc,
            fileName = "resume.docx",
            docxBase64 = "UEsDBBQAAAAIA..."
        )

        assertEquals("resume.docx", state.fileName)
        assertNotNull(state.docxBase64)
        assertEquals("UEsDBBQAAAAIA...", state.docxBase64)
    }
}
