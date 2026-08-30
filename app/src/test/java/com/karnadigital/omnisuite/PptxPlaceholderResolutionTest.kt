package com.karnadigital.omnisuite

import com.karnadigital.omnisuite.core.engine.document.PptxShapeExtractor
import org.apache.poi.sl.usermodel.Placeholder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verification for PPTX placeholder bounds resolution & OOXML inheritance chain:
 *  - PlaceholderInfo extraction (type, idx, explicit vs defaulted flags)
 *  - Multi-criteria placeholder matching (exact idx, type+idx, compatible types, master mapping)
 *  - Slide -> SlideLayout -> SlideMaster full resolution chain
 *  - Group transform integration with inherited bounds
 */
class PptxPlaceholderResolutionTest {

    // Mock shape classes for testing OOXML reflection / extraction without heavy PPTX files
    private class MockXmlPlaceholder(
        private val type: String?,
        private val idx: Long?,
        private val isSetType: Boolean = type != null,
        private val isSetIdx: Boolean = idx != null
    ) {
        fun getType(): String? = type
        fun getIdx(): Long? = idx
        fun isSetType(): Boolean = isSetType
        fun isSetIdx(): Boolean = isSetIdx
    }

    private class MockXmlPoint(private val x: Long, private val y: Long) {
        fun getX(): Long = x
        fun getY(): Long = y
    }

    private class MockXmlDimension(private val cx: Long, private val cy: Long) {
        fun getCx(): Long = cx
        fun getCy(): Long = cy
    }

    private class MockXmlXfrm(
        private val off: MockXmlPoint,
        private val ext: MockXmlDimension
    ) {
        fun getOff(): MockXmlPoint = off
        fun getExt(): MockXmlDimension = ext
    }

    private class MockXmlNvPr(private val ph: MockXmlPlaceholder?) {
        fun getPh(): MockXmlPlaceholder? = ph
    }

    private class MockXmlShape(
        private val nvPr: MockXmlNvPr?,
        private val xfrm: MockXmlXfrm? = null,
        private val xmlString: String = ""
    ) {
        fun getNvPr(): MockXmlNvPr? = nvPr
        fun getXfrm(): MockXmlXfrm? = xfrm
        override fun toString(): String = xmlString
    }

    private class MockShape(
        private val name: String,
        private val xmlObject: MockXmlShape?
    ) {
        fun getShapeName(): String = name
        fun getXmlObject(): MockXmlShape? = xmlObject
    }

    private class MockSlideLayout(
        private val name: String,
        private val shapes: List<MockShape>,
        private val slideMaster: MockSlideMaster? = null
    ) {
        fun getShapeName(): String = name
        fun getShapes(): List<MockShape> = shapes
        fun getSlideMaster(): MockSlideMaster? = slideMaster
    }

    private class MockSlideMaster(
        private val name: String,
        private val shapes: List<MockShape>
    ) {
        fun getShapeName(): String = name
        fun getShapes(): List<MockShape> = shapes
    }

    private class MockSlide(
        private val slideLayout: MockSlideLayout?
    ) {
        fun getSlideLayout(): MockSlideLayout? = slideLayout
    }

    @Test
    fun testMapTypeNameToPlaceholder() {
        assertEquals(Placeholder.TITLE, PptxShapeExtractor.mapTypeNameToPlaceholder("title"))
        assertEquals(Placeholder.TITLE, PptxShapeExtractor.mapTypeNameToPlaceholder("vertical_title"))
        assertEquals(Placeholder.CENTERED_TITLE, PptxShapeExtractor.mapTypeNameToPlaceholder("ctrTitle"))
        assertEquals(Placeholder.SUBTITLE, PptxShapeExtractor.mapTypeNameToPlaceholder("subTitle"))
        assertEquals(Placeholder.BODY, PptxShapeExtractor.mapTypeNameToPlaceholder("body"))
        assertEquals(Placeholder.DATETIME, PptxShapeExtractor.mapTypeNameToPlaceholder("dt"))
        assertEquals(Placeholder.FOOTER, PptxShapeExtractor.mapTypeNameToPlaceholder("ftr"))
        assertEquals(Placeholder.SLIDE_NUMBER, PptxShapeExtractor.mapTypeNameToPlaceholder("sldNum"))
        assertEquals(Placeholder.HEADER, PptxShapeExtractor.mapTypeNameToPlaceholder("hdr"))
        assertEquals(Placeholder.DGM, PptxShapeExtractor.mapTypeNameToPlaceholder("dgm"))
        assertEquals(Placeholder.PICTURE, PptxShapeExtractor.mapTypeNameToPlaceholder("pic"))
        assertNull(PptxShapeExtractor.mapTypeNameToPlaceholder(null))
        assertNull(PptxShapeExtractor.mapTypeNameToPlaceholder("unknown_type_xyz"))
    }

    @Test
    fun testExtractPlaceholderInfo_fromXmlWithBothTypeAndIdx() {
        val xmlShape = MockXmlShape(
            nvPr = MockXmlNvPr(MockXmlPlaceholder(type = "subTitle", idx = 1L))
        )
        val shape = MockShape("Subtitle 1", xmlShape)
        val info = PptxShapeExtractor.extractPlaceholderInfo(shape)

        assertNotNull(info)
        assertEquals(Placeholder.SUBTITLE, info?.placeholder)
        assertEquals(1L, info?.idx)
        assertTrue(info?.hasExplicitType == true)
        assertTrue(info?.hasExplicitIdx == true)
    }

    @Test
    fun testExtractPlaceholderInfo_fromXmlWithIdxOnlyDefaultsToBody() {
        // In OOXML, <p:ph idx="1"/> with no type defaults to BODY
        val xmlShape = MockXmlShape(
            nvPr = MockXmlNvPr(MockXmlPlaceholder(type = null, idx = 2L, isSetType = false, isSetIdx = true))
        )
        val shape = MockShape("Content Placeholder 2", xmlShape)
        val info = PptxShapeExtractor.extractPlaceholderInfo(shape)

        assertNotNull(info)
        assertEquals(Placeholder.BODY, info?.placeholder)
        assertEquals(2L, info?.idx)
        assertTrue(info?.hasExplicitIdx == true)
    }

    @Test
    fun testExtractPlaceholderInfo_fromXmlStringFallback() {
        val xmlShape = MockXmlShape(
            nvPr = null,
            xmlString = "<p:sp><p:nvSpPr><p:nvPr><p:ph type=\"ctrTitle\"/></p:nvPr></p:nvSpPr></p:sp>"
        )
        val shape = MockShape("Title 1", xmlShape)
        val info = PptxShapeExtractor.extractPlaceholderInfo(shape)

        assertNotNull(info)
        assertEquals(Placeholder.CENTERED_TITLE, info?.placeholder)
        assertTrue(info?.hasExplicitType == true)
    }

    @Test
    fun testExtractPlaceholderInfo_nonPlaceholderReturnsNull() {
        val xmlShape = MockXmlShape(nvPr = null, xmlString = "<p:sp><p:nvSpPr><p:nvPr/></p:nvSpPr></p:sp>")
        val shape = MockShape("Rectangle 1", xmlShape)
        val info = PptxShapeExtractor.extractPlaceholderInfo(shape)

        assertNull(info)
    }

    @Test
    fun testMatchPlaceholderScore_layoutMatching() {
        val target = PptxShapeExtractor.PlaceholderInfo(
            typeName = "subTitle",
            placeholder = Placeholder.SUBTITLE,
            idx = 1L,
            hasExplicitType = true,
            hasExplicitIdx = true
        )

        // Exact match (both type and idx)
        val exactCandidate = PptxShapeExtractor.PlaceholderInfo(
            typeName = "subTitle",
            placeholder = Placeholder.SUBTITLE,
            idx = 1L,
            hasExplicitType = true,
            hasExplicitIdx = true
        )
        assertEquals(100, PptxShapeExtractor.matchPlaceholderScore(target, exactCandidate, isMaster = false))

        // Same idx, candidate defaulted type
        val idxOnlyCandidate = PptxShapeExtractor.PlaceholderInfo(
            typeName = "body",
            placeholder = Placeholder.BODY,
            idx = 1L,
            hasExplicitType = false,
            hasExplicitIdx = true
        )
        assertTrue(PptxShapeExtractor.matchPlaceholderScore(target, idxOnlyCandidate, isMaster = false) >= 90)

        // Different idx
        val diffIdxCandidate = PptxShapeExtractor.PlaceholderInfo(
            typeName = "subTitle",
            placeholder = Placeholder.SUBTITLE,
            idx = 2L,
            hasExplicitType = true,
            hasExplicitIdx = true
        )
        assertTrue(PptxShapeExtractor.matchPlaceholderScore(target, diffIdxCandidate, isMaster = false) < 70)
    }

    @Test
    fun testMatchPlaceholderScore_masterMatching() {
        val targetTitle = PptxShapeExtractor.PlaceholderInfo(
            typeName = "ctrTitle",
            placeholder = Placeholder.CENTERED_TITLE,
            idx = null,
            hasExplicitType = true,
            hasExplicitIdx = false
        )
        val masterTitle = PptxShapeExtractor.PlaceholderInfo(
            typeName = "title",
            placeholder = Placeholder.TITLE,
            idx = null,
            hasExplicitType = true,
            hasExplicitIdx = false
        )
        // Center title maps to Master Title
        assertEquals(95, PptxShapeExtractor.matchPlaceholderScore(targetTitle, masterTitle, isMaster = true))

        val targetBody = PptxShapeExtractor.PlaceholderInfo(
            typeName = "body",
            placeholder = Placeholder.BODY,
            idx = 1L,
            hasExplicitType = true,
            hasExplicitIdx = true
        )
        val masterBody = PptxShapeExtractor.PlaceholderInfo(
            typeName = "body",
            placeholder = Placeholder.BODY,
            idx = null,
            hasExplicitType = true,
            hasExplicitIdx = false
        )
        // Body placeholder maps to Master Body
        assertTrue(PptxShapeExtractor.matchPlaceholderScore(targetBody, masterBody, isMaster = true) >= 85)
    }

    @Test
    fun testGetShapeNormalizedBounds_directXfrm() {
        val slideW = 9144000L
        val slideH = 5143500L

        val xmlShape = MockXmlShape(
            nvPr = null,
            xfrm = MockXmlXfrm(
                off = MockXmlPoint(914400L, 514350L), // x=0.1, y=0.1
                ext = MockXmlDimension(4572000L, 2571750L) // w=0.5, h=0.5
            )
        )
        val shape = MockShape("Direct Shape", xmlShape)
        val bounds = PptxShapeExtractor.getShapeNormalizedBounds(shape, null, slideW, slideH)

        assertNotNull(bounds)
        assertEquals(0.1f, bounds!![0], 0.001f)
        assertEquals(0.1f, bounds[1], 0.001f)
        assertEquals(0.5f, bounds[2], 0.001f)
        assertEquals(0.5f, bounds[3], 0.001f)
    }

    @Test
    fun testGetShapeNormalizedBounds_resolvesFromSlideLayout() {
        val slideW = 9144000L
        val slideH = 5143500L

        // Slide shape has no xfrm, only placeholder <p:ph type="title"/>
        val slideShapeXml = MockXmlShape(
            nvPr = MockXmlNvPr(MockXmlPlaceholder(type = "title", idx = null))
        )
        val slideShape = MockShape("Title 1", slideShapeXml)

        // Layout shape has placeholder <p:ph type="title"/> AND explicit xfrm
        val layoutShapeXml = MockXmlShape(
            nvPr = MockXmlNvPr(MockXmlPlaceholder(type = "title", idx = null)),
            xfrm = MockXmlXfrm(
                off = MockXmlPoint(914400L, 257175L), // left=0.1, top=0.05
                ext = MockXmlDimension(7315200L, 1028700L) // width=0.8, height=0.2
            )
        )
        val layoutShape = MockShape("Layout Title", layoutShapeXml)
        val layout = MockSlideLayout("Title Layout", listOf(layoutShape))
        val slide = MockSlide(layout)

        val bounds = PptxShapeExtractor.getShapeNormalizedBounds(slideShape, slide, slideW, slideH)

        assertNotNull(bounds)
        assertEquals(0.1f, bounds!![0], 0.001f)
        assertEquals(0.05f, bounds[1], 0.001f)
        assertEquals(0.8f, bounds[2], 0.001f)
        assertEquals(0.2f, bounds[3], 0.001f)
    }

    @Test
    fun testGetShapeNormalizedBounds_resolvesFromSlideMasterChain() {
        val slideW = 9144000L
        val slideH = 5143500L

        // 1. Slide shape: placeholder <p:ph idx="1"/> (no xfrm)
        val slideShapeXml = MockXmlShape(
            nvPr = MockXmlNvPr(MockXmlPlaceholder(type = null, idx = 1L))
        )
        val slideShape = MockShape("Body 1", slideShapeXml)

        // 2. Layout shape: placeholder <p:ph idx="1"/> (ALSO NO xfrm! Inherits from master)
        val layoutShapeXml = MockXmlShape(
            nvPr = MockXmlNvPr(MockXmlPlaceholder(type = "body", idx = 1L)),
            xfrm = null
        )
        val layoutShape = MockShape("Layout Body", layoutShapeXml)

        // 3. Master shape: placeholder <p:ph type="body"/> (HAS xfrm)
        val masterShapeXml = MockXmlShape(
            nvPr = MockXmlNvPr(MockXmlPlaceholder(type = "body", idx = null)),
            xfrm = MockXmlXfrm(
                off = MockXmlPoint(914400L, 1543050L), // left=0.1, top=0.3
                ext = MockXmlDimension(7315200L, 3086100L) // width=0.8, height=0.6
            )
        )
        val masterShape = MockShape("Master Body", masterShapeXml)

        val master = MockSlideMaster("Office Theme", listOf(masterShape))
        val layout = MockSlideLayout("Blank/Custom Layout", listOf(layoutShape), master)
        val slide = MockSlide(layout)

        // Must follow Slide -> Layout -> Master chain and return Master's bounds!
        val bounds = PptxShapeExtractor.getShapeNormalizedBounds(slideShape, slide, slideW, slideH)

        assertNotNull(bounds)
        assertEquals(0.1f, bounds!![0], 0.001f)
        assertEquals(0.3f, bounds[1], 0.001f)
        assertEquals(0.8f, bounds[2], 0.001f)
        assertEquals(0.6f, bounds[3], 0.001f)
    }

    @Test
    fun testGetShapeNormalizedBounds_withGroupTransform() {
        val slideW = 9144000L
        val slideH = 5143500L

        // Group shape transforms from (0,0) 1000x1000 to offset (914400, 514350)
        val groupTransform = PptxShapeExtractor.GroupTransform(
            offX = 914400L,
            offY = 514350L,
            extCx = 4572000L,
            extCy = 2571750L,
            chOffX = 0L,
            chOffY = 0L,
            chExtCx = 1000L,
            chExtCy = 1000L
        )

        // Child shape at (100, 100, 500, 500) inside group
        val childShapeXml = MockXmlShape(
            nvPr = null,
            xfrm = MockXmlXfrm(
                off = MockXmlPoint(100L, 100L),
                ext = MockXmlDimension(500L, 500L)
            )
        )
        val childShape = MockShape("Group Child", childShapeXml)

        val bounds = PptxShapeExtractor.getShapeNormalizedBounds(
            childShape,
            null,
            slideW,
            slideH,
            listOf(groupTransform)
        )

        assertNotNull(bounds)
        // Group child x = 914400 + (100 * 4572000 / 1000) = 914400 + 457200 = 1371600 -> 1371600/9144000 = 0.15
        assertEquals(0.15f, bounds!![0], 0.001f)
        // Group child y = 514350 + (100 * 2571750 / 1000) = 514350 + 257175 = 771525 -> 771525/5143500 = 0.15
        assertEquals(0.15f, bounds[1], 0.001f)
        // Group child w = 500 * 4572000 / 1000 = 2286000 -> 2286000/9144000 = 0.25
        assertEquals(0.25f, bounds[2], 0.001f)
        // Group child h = 500 * 2571750 / 1000 = 1285875 -> 1285875/5143500 = 0.25
        assertEquals(0.25f, bounds[3], 0.001f)
    }

    private class MockXmlBlip(private val embed: String?) {
        fun getEmbed(): String? = embed
    }

    private class MockXmlBlipFill(private val blip: MockXmlBlip?) {
        fun getBlip(): MockXmlBlip? = blip
    }

    private class MockXmlBgPr(private val blipFill: MockXmlBlipFill?) {
        fun getBlipFill(): MockXmlBlipFill? = blipFill
    }

    private class MockXmlBg(private val bgPr: MockXmlBgPr?) {
        fun getBgPr(): MockXmlBgPr? = bgPr
    }

    @Test
    fun testExtractBlipEmbedId_fromBackgroundProperties() {
        val bgXml = MockXmlBg(
            bgPr = MockXmlBgPr(
                blipFill = MockXmlBlipFill(
                    blip = MockXmlBlip("rId5")
                )
            )
        )
        val blipId = PptxShapeExtractor.extractBlipEmbedId(bgXml)
        assertEquals("rId5", blipId)
    }

    @Test
    fun testExtractBlipEmbedId_fromXmlStringFallback() {
        val xmlWithEmbed = "<p:bg><p:bgPr><a:blipFill><a:blip r:embed=\"rId99\"/></a:blipFill></p:bgPr></p:bg>"
        class DummyXml(val s: String) { override fun toString(): String = s }
        val blipId = PptxShapeExtractor.extractBlipEmbedId(DummyXml(xmlWithEmbed))
        assertEquals("rId99", blipId)
    }
}

