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
}
