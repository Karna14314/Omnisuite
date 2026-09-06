package com.karnadigital.omnisuite.feature.viewer

import com.karnadigital.omnisuite.core.engine.document.OfficeConverter
import org.apache.poi.sl.usermodel.ShapeType
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method

/**
 * Regression and unit tests for OmniSuite PPTX rendering pipeline fixes:
 * 1. Theme scheme color resolution & alpha transparency extraction.
 * 2. Hexagon & AutoShape geometry type resolution.
 * 3. Group shape transform scale and offset mapping.
 * 4. Slide Master & Layout background shape collection.
 */
class PptxRendererUnitTest {

    private fun getMethod(clazz: Class<*>, name: String, vararg paramTypes: Class<*>): Method {
        val method = clazz.getDeclaredMethod(name, *paramTypes)
        method.isAccessible = true
        return method
    }

    private fun allocateUnsafeInstance(clazz: Class<*>): Any {
        val unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null)
        val allocateInstance = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
        return allocateInstance.invoke(unsafe, clazz)
    }

    @Test
    fun testAlphaTransparencyConversion() {
        val converter = allocateUnsafeInstance(OfficeConverter::class.java) as OfficeConverter
        val extractAlpha = getMethod(OfficeConverter::class.java, "extractAlphaFromColorObj", Any::class.java)

        // Mock XML object with 50% alpha (50000)
        val xml50Alpha = "<a:srgbClr val=\"FF0000\"><a:alpha val=\"50000\"/></a:srgbClr>"
        val alphaVal = extractAlpha.invoke(converter, xml50Alpha) as? Long

        assertNotNull("Alpha value should be extracted from XML string", alphaVal)
        assertEquals("50000 EMU alpha (50%)", 50000L, alphaVal)

        // Test alpha conversion to ARGB alpha (0..255)
        val alphaFloat = (alphaVal!! / 100000f).coerceIn(0f, 1f)
        val alphaInt = (alphaFloat * 255f).toInt().coerceIn(0, 255)
        assertEquals("50% alpha mapped to 127 ARGB alpha", 127, alphaInt)
    }

    @Test
    fun testSchemeColorResolution() {
        val converter = allocateUnsafeInstance(OfficeConverter::class.java) as OfficeConverter
        val resolveScheme = getMethod(OfficeConverter::class.java, "resolveSchemeColorName", String::class.java, Map::class.java)

        val themeMap = mapOf("accent1" to "#1E40AF", "accent2" to "#EA580C")
        val resolved = resolveScheme.invoke(converter, "accent1", themeMap) as? String

        assertEquals("#1E40AF", resolved)
    }

    @Test
    fun testHexagonGeometryTypeResolution() {
        val ppt = XMLSlideShow()
        val slide = ppt.createSlide()
        val shape = slide.createAutoShape()
        shape.shapeType = ShapeType.HEXAGON

        val converter = allocateUnsafeInstance(OfficeConverter::class.java) as OfficeConverter
        val getGeomType = getMethod(OfficeConverter::class.java, "getShapeGeometryType", Any::class.java)

        val geomEnum = getGeomType.invoke(converter, shape)
        assertNotNull("Geometry enum should be returned", geomEnum)
        assertEquals("HEXAGON enum name", "HEXAGON", geomEnum.toString())
    }

    @Test
    fun testSlideMasterShapesCollection() {
        val ppt = XMLSlideShow()
        val slide = ppt.createSlide()

        // Create a decorative non-placeholder shape on the slide layout
        val layout = slide.slideLayout
        val layoutShape = layout.createAutoShape()
        layoutShape.shapeType = ShapeType.HEXAGON

        val vm = allocateUnsafeInstance(PptxViewerViewModel::class.java) as PptxViewerViewModel
        val parseMethod = getMethod(PptxViewerViewModel::class.java, "parseAllSlides", org.apache.poi.sl.usermodel.SlideShow::class.java)

        @Suppress("UNCHECKED_CAST")
        val slides = parseMethod.invoke(vm, ppt) as List<PptxSlide>

        assertNotNull(slides)
        assertEquals(1, slides.size)
        // Check that Master/Layout decorative shape was collected
        assertTrue("Slide layout master shapes collected", slides[0].textShapes.isNotEmpty() || slides[0].images.isNotEmpty() || slides[0].title != null)
    }

    @Test
    fun testPictureShapeExtractionAndXmlCursor() {
        val ppt = XMLSlideShow()
        val slide = ppt.createSlide()
        val pngBytes = byteArrayOf(
            -119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82,
            0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0, 31, 21, -60,
            -119, 0, 0, 0, 10, 73, 68, 65, 84, 120, -100, 99, 0, 1, 0, 0,
            5, 0, 1, 13, 10, 45, -76, 0, 0, 0, 0, 73, 69, 78, 68,
            -82, 66, 96, -126
        )
        val picData = ppt.addPicture(pngBytes, org.apache.poi.sl.usermodel.PictureData.PictureType.PNG)
        val picShape = slide.createPicture(picData)

        val vm = allocateUnsafeInstance(PptxViewerViewModel::class.java) as PptxViewerViewModel
        val extractPicMethod = getMethod(PptxViewerViewModel::class.java, "extractPictureDataFromShape", Any::class.java, Any::class.java)
        val picTriple = extractPicMethod.invoke(vm, picShape, slide)
        assertNotNull("picTriple should not be null", picTriple)

        val setBoundsMethod = getMethod(PptxViewerViewModel::class.java, "getShapeNormalizedBounds", Any::class.java, Any::class.java, Long::class.javaPrimitiveType!!, Long::class.javaPrimitiveType!!)

        // Apply XmlCursor bounds setting:
        val xmlObj = picShape.xmlObject
        val cursor = xmlObj.newCursor()
        val dmlNs = "http://schemas.openxmlformats.org/drawingml/2006/main"
        val pmlNs = "http://schemas.openxmlformats.org/presentationml/2006/main"
        if (cursor.toChild(javax.xml.namespace.QName(pmlNs, "spPr"))) {
            if (cursor.toChild(javax.xml.namespace.QName(dmlNs, "xfrm"))) {
                if (cursor.toChild(javax.xml.namespace.QName(dmlNs, "off"))) {
                    cursor.setAttributeText(javax.xml.namespace.QName("x"), "914400")
                    cursor.setAttributeText(javax.xml.namespace.QName("y"), "914400")
                    cursor.toParent()
                }
                if (cursor.toChild(javax.xml.namespace.QName(dmlNs, "ext"))) {
                    cursor.setAttributeText(javax.xml.namespace.QName("cx"), "4572000")
                    cursor.setAttributeText(javax.xml.namespace.QName("cy"), "3657600")
                    cursor.toParent()
                }
            }
        }
        cursor.dispose()

        val cacheField = PptxViewerViewModel::class.java.getDeclaredField("tempImageCache")
        cacheField.isAccessible = true
        cacheField.set(vm, mutableMapOf<String, java.io.File>())

        val boundsAfter = setBoundsMethod.invoke(vm, picShape, slide, 9144000L, 5143500L) as? FloatArray
        assertNotNull("boundsAfter should not be null", boundsAfter)
        assertEquals(0.1f, boundsAfter!![0], 0.01f)
        assertEquals(0.5f, boundsAfter[2], 0.01f)

        // Test parseAllSlides collects the image:
        val parseMethod = getMethod(PptxViewerViewModel::class.java, "parseAllSlides", org.apache.poi.sl.usermodel.SlideShow::class.java)
        @Suppress("UNCHECKED_CAST")
        val slides = parseMethod.invoke(vm, ppt) as List<PptxSlide>
        assertEquals(1, slides[0].images.size)
        assertEquals(0.1f, slides[0].images[0].left, 0.01f)
        assertEquals(0.5f, slides[0].images[0].width, 0.01f)
    }

    @Test
    fun testMultiLineTextShapeParsing() {
        val ppt = XMLSlideShow()
        val slide = ppt.createSlide()
        val tb = slide.createTextBox()
        val p1 = tb.addNewTextParagraph()
        p1.addNewTextRun().setText("First line")
        val p2 = tb.addNewTextParagraph()
        p2.addNewTextRun().setText("Second line")
        val p3 = tb.addNewTextParagraph()
        p3.addNewTextRun().setText("Third line")

        val vm = allocateUnsafeInstance(PptxViewerViewModel::class.java) as PptxViewerViewModel
        val parseMethod = getMethod(PptxViewerViewModel::class.java, "parseAllSlides", org.apache.poi.sl.usermodel.SlideShow::class.java)
        @Suppress("UNCHECKED_CAST")
        val slides = parseMethod.invoke(vm, ppt) as List<PptxSlide>
        val shape = slides[0].textShapes[0]

        // fullText should have all 3 lines
        assertEquals("First line\nSecond line\nThird line", shape.fullText)
        assertEquals(3, shape.paragraphs.size)
    }

    @Test
    fun testUpdateShapeTextMultiLine() {
        val ppt = XMLSlideShow()
        val slide = ppt.createSlide()
        val tb = slide.createTextBox()
        val p1 = tb.addNewTextParagraph()
        p1.addNewTextRun().setText("Line A")
        val p2 = tb.addNewTextParagraph()
        p2.addNewTextRun().setText("Line B")

        val vm = allocateUnsafeInstance(PptxViewerViewModel::class.java) as PptxViewerViewModel
        val activePresField = PptxViewerViewModel::class.java.getDeclaredField("activePresentation")
        activePresField.isAccessible = true
        activePresField.set(vm, ppt)

        val undoStackField = PptxViewerViewModel::class.java.getDeclaredField("undoStack")
        undoStackField.isAccessible = true
        undoStackField.set(vm, java.util.ArrayDeque<ByteArray>())

        val redoStackField = PptxViewerViewModel::class.java.getDeclaredField("redoStack")
        redoStackField.isAccessible = true
        redoStackField.set(vm, java.util.ArrayDeque<ByteArray>())

        val canUndoField = PptxViewerViewModel::class.java.getDeclaredField("_canUndo")
        canUndoField.isAccessible = true
        canUndoField.set(vm, kotlinx.coroutines.flow.MutableStateFlow(false))

        val canRedoField = PptxViewerViewModel::class.java.getDeclaredField("_canRedo")
        canRedoField.isAccessible = true
        canRedoField.set(vm, kotlinx.coroutines.flow.MutableStateFlow(false))

        val loadStateField = PptxViewerViewModel::class.java.getDeclaredField("_loadState")
        loadStateField.isAccessible = true
        loadStateField.set(vm, kotlinx.coroutines.flow.MutableStateFlow(PptxLoadState.Loading))

        val cacheField = PptxViewerViewModel::class.java.getDeclaredField("tempImageCache")
        cacheField.isAccessible = true
        cacheField.set(vm, mutableMapOf<String, java.io.File>())

        val parseMethod = getMethod(PptxViewerViewModel::class.java, "parseAllSlides", org.apache.poi.sl.usermodel.SlideShow::class.java)

        // Update the shape with 3 lines using its exact shape ID
        @Suppress("UNCHECKED_CAST")
        val initialSlides = parseMethod.invoke(vm, ppt) as List<PptxSlide>
        val targetId = initialSlides[0].title?.id ?: initialSlides[0].textShapes[0].id
        vm.updateShapeText(0, targetId, "Line 1\nLine 2\nLine 3")
        Thread.sleep(200)

        @Suppress("UNCHECKED_CAST")
        val slides = parseMethod.invoke(vm, ppt) as List<PptxSlide>
        val shape = slides[0].title ?: slides[0].textShapes[0]

        assertEquals(3, shape.paragraphs.size)
        assertEquals("Line 1\nLine 2\nLine 3", shape.fullText)

        // Update down to 1 line (test paragraph and run trimming):
        vm.updateShapeText(0, targetId, "Only Line")
        Thread.sleep(200)
        @Suppress("UNCHECKED_CAST")
        val slidesAfterTrim = parseMethod.invoke(vm, ppt) as List<PptxSlide>
        val shapeAfterTrim = slidesAfterTrim[0].title ?: slidesAfterTrim[0].textShapes[0]
        assertEquals(1, shapeAfterTrim.paragraphs.size)
        assertEquals("Only Line", shapeAfterTrim.fullText)
    }

    @Test
    fun testInspectPoiMethods() {
        val ppt = XMLSlideShow()
        val slide = ppt.createSlide()
        val tb = slide.createTextBox()
        val p1 = tb.textParagraphs[0]
        val r1 = p1.addNewTextRun()
        r1.setText("hello ")
        val r2 = p1.addNewTextRun()
        r2.setText("world")

        val p2 = tb.addNewTextParagraph()
        p2.addNewTextRun().setText("second paragraph")

        assertEquals(2, tb.textParagraphs.size)
        assertEquals(3, p1.textRuns.size)

        // Remove run using POI API:
        p1.removeTextRun(p1.textRuns[1])
        assertEquals(2, p1.textRuns.size)

        // Remove paragraph using POI API:
        tb.removeTextParagraph(tb.textParagraphs[1])
        assertEquals(1, tb.textParagraphs.size)
    }
}






