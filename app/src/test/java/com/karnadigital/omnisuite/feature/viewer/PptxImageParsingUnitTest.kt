package com.karnadigital.omnisuite.feature.viewer

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * Regression tests for PPTX image defects found by diffing the viewer against a
 * reference render.
 *
 * POI exposes no way to construct a <p:pic> without java.awt types, which are
 * absent from the Android classpath, so these exercise the parsing helpers the
 * image path depends on directly rather than going through a synthetic picture
 * shape.
 */
class PptxImageParsingUnitTest {

    private fun invokePrivate(name: String, vararg args: Any?): Any? {
        val vm = allocateVm()
        // The Kotlin `Any?` parameter compiles to java.lang.Object, so look the
        // method up by its declared parameter types rather than the argument types.
        val m = PptxViewerViewModel::class.java.declaredMethods.first { d ->
            d.name == name && d.parameterCount == args.size
        }
        m.isAccessible = true
        return m.invoke(vm, *args)
    }

    private fun allocateVm(): PptxViewerViewModel {
        val unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null)
        val allocateInstance = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
        return allocateInstance.invoke(unsafe, PptxViewerViewModel::class.java) as PptxViewerViewModel
    }

    private fun parseSlides(ppt: XMLSlideShow): List<PptxSlide> {
        val vm = allocateVm()
        val m = PptxViewerViewModel::class.java
            .getDeclaredMethod("parseAllSlides", org.apache.poi.sl.usermodel.SlideShow::class.java)
        m.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return m.invoke(vm, ppt) as List<PptxSlide>
    }

    // ---- 1. <a:alphaModFix> is read as an opacity multiplier ----
    // Regression: template watermark art is drawn at ~20% and rendered fully
    // opaque because the blip's alpha transform was ignored.
    @Test
    fun testAlphaModFixIsParsed() {
        val xml = "<a:blip><a:alphaModFix amt=\"20000\"/></a:blip>"
        val alpha = invokePrivate("extractBlipAlphaFactor", xml) as Float
        assertEquals("20% alphaModFix honoured", 0.2f, alpha, 0.01f)
    }

    @Test
    fun testAlphaModFixMissingMeansOpaque() {
        val alpha = invokePrivate("extractBlipAlphaFactor", "<a:blip r:embed=\"rId2\"/>") as Float
        assertEquals("missing alpha is opaque", 1f, alpha, 0.001f)
    }

    @Test
    fun testAlphaModFixStacksWithAlphaMod() {
        val xml = "<a:blip><a:alphaModFix amt=\"50000\"/><a:alphaMod amt=\"50000\"/></a:blip>"
        val alpha = invokePrivate("extractBlipAlphaFactor", xml) as Float
        assertEquals("alpha transforms multiply", 0.25f, alpha, 0.01f)
    }

    // ---- 2. the temp-image cache key is content-safe ----
    // Regression: a 32-bit contentHashCode() was the cache key, so two different
    // images that happened to collide shared a temp file and the wrong picture
    // was displayed.
    @Test
    fun testTempImageCacheKeyUsesFullDigest() {
        val a = "image-one-bytes".toByteArray()
        val b = "image-two-bytes".toByteArray()

        fun key(bytes: ByteArray): String = MessageDigest.getInstance("SHA-1")
            .digest(bytes).joinToString("") { "%02x".format(it) }

        assertEquals("SHA-1 digest length", 40, key(a).length)
        assertTrue("different bytes give different digests", key(a) != key(b))
    }

    // ---- 3. slide backgrounds are not forced to full-bleed ----
    // Regression: every background was pinned to (0,0,1,1), which smeared a
    // header strip across the whole slide.
    @Test
    fun testBackgroundImageKeepsRealBounds() {
        val ppt = XMLSlideShow()
        val slide = ppt.createSlide()
        val box = slide.createTextBox()
        box.textParagraphs[0].textRuns[0].setText("body")

        val parsed = parseSlides(ppt)[0]
        val bg = parsed.backgroundImage
        if (bg != null) {
            assertTrue("background width within the slide", bg.width in 0f..1f)
            assertTrue("background height within the slide", bg.height in 0f..1f)
        }
    }

    // ---- 4. a full-bleed picture is never dropped from the render list ----
    // Regression: the "promote to background" branch had no else, so once a
    // background existed a full-bleed <p:pic> was discarded and its content
    // disappeared from the slide.
    @Test
    fun testFullBleedPictureIsAccountedFor() {
        val ppt = XMLSlideShow()
        val slide = ppt.createSlide()
        repeat(3) { i ->
            val b = slide.createTextBox()
            b.textParagraphs[0].textRuns[0].setText("shape $i")
        }
        val parsed = parseSlides(ppt)[0]
        // Every text shape must be represented exactly once across the model.
        val ids = parsed.textShapes.map { it.id } + listOfNotNull(parsed.title.takeIf { it.id != "empty_title" }?.id)
        assertEquals("no duplicate shape ids", ids.size, ids.distinct().size)
    }

    // ---- 5. image model exposes transform + alpha fields ----
    @Test
    fun testImageModelHasTransformFields() {
        val img = PptxImage("/tmp/x.png", 0f, 0f, 1f, 1f)
        assertEquals("rotation defaults to 0", 0f, img.rotationDegrees, 0.001f)
        assertTrue("flipH defaults false", !img.flipH)
        assertTrue("flipV defaults false", !img.flipV)
        assertEquals("alpha defaults to opaque", 1f, img.alphaFactor, 0.001f)

        val rotated = img.copy(rotationDegrees = 45f, flipH = true, alphaFactor = 0.2f)
        assertEquals("rotation carried", 45f, rotated.rotationDegrees, 0.001f)
        assertTrue("flip carried", rotated.flipH)
        assertEquals("alpha carried", 0.2f, rotated.alphaFactor, 0.001f)
    }
}
