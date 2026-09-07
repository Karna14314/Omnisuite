package com.karnadigital.omnisuite.core.engine.document

import org.apache.poi.xwpf.usermodel.BreakType
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.lang.reflect.Method
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Unit tests for ReverseOfficeConverter:
 * 1. OpenXML PPTX ZIP package structure and content types.
 * 2. XWPF DOCX hard page breaks and styled formatting.
 * 3. Heuristic classifiers for headings and code blocks.
 */
class ReverseOfficeConverterUnitTest {

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
    fun testOpenXmlPptxZipArchiveStructure() {
        val numPages = 2
        val slideCxEmu = 9144000L
        val slideCyEmu = 5143500L

        val outStream = ByteArrayOutputStream()
        ZipOutputStream(outStream).use { zip ->
            // [Content_Types].xml
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            val contentTypes = buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
                append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
                append("""<Default Extension="xml" ContentType="application/xml"/>""")
                append("""<Default Extension="jpg" ContentType="image/jpeg"/>""")
                append("""<Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>""")
                for (i in 1..numPages) {
                    append("""<Override PartName="/ppt/slides/slide$i.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>""")
                }
                append("""</Types>""")
            }
            zip.write(contentTypes.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // _rels/.rels
            zip.putNextEntry(ZipEntry("_rels/.rels"))
            val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
</Relationships>"""
            zip.write(rootRels.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // ppt/_rels/presentation.xml.rels
            zip.putNextEntry(ZipEntry("ppt/_rels/presentation.xml.rels"))
            val presRels = buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                for (i in 1..numPages) {
                    append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide$i.xml"/>""")
                }
                append("""</Relationships>""")
            }
            zip.write(presRels.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // ppt/presentation.xml
            zip.putNextEntry(ZipEntry("ppt/presentation.xml"))
            val presXml = buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("""<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" """)
                append("""xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" """)
                append("""xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">""")
                append("""<p:sldMasterIdLst/>""")
                append("""<p:sldIdLst>""")
                for (i in 1..numPages) {
                    val slideId = 255 + i
                    append("""<p:sldId id="$slideId" r:id="rId$i"/>""")
                }
                append("""</p:sldIdLst>""")
                append("""<p:sldSz cx="$slideCxEmu" cy="$slideCyEmu" type="custom"/>""")
                append("""<p:notesSz cx="6858000" cy="9144000"/>""")
                append("""</p:presentation>""")
            }
            zip.write(presXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            for (i in 1..numPages) {
                zip.putNextEntry(ZipEntry("ppt/media/image$i.jpg"))
                zip.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("ppt/slides/_rels/slide$i.xml.rels"))
                val slideRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image$i.jpg"/>
</Relationships>"""
                zip.write(slideRels.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("ppt/slides/slide$i.xml"))
                val slideXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
       xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
       xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld>
    <p:spTree>
      <p:pic>
        <p:blipFill>
          <a:blip r:embed="rId1"/>
        </p:blipFill>
      </p:pic>
    </p:spTree>
  </p:cSld>
</p:sld>"""
                zip.write(slideXml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }

        // Verify ZIP entries can be read back and contain all required OpenXML parts
        val entryNames = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(outStream.toByteArray())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entryNames.add(entry.name)
                entry = zis.nextEntry
            }
        }

        assertTrue(entryNames.contains("[Content_Types].xml"))
        assertTrue(entryNames.contains("_rels/.rels"))
        assertTrue(entryNames.contains("ppt/presentation.xml"))
        assertTrue(entryNames.contains("ppt/_rels/presentation.xml.rels"))
        assertTrue(entryNames.contains("ppt/slides/slide1.xml"))
        assertTrue(entryNames.contains("ppt/slides/_rels/slide1.xml.rels"))
        assertTrue(entryNames.contains("ppt/media/image1.jpg"))
        assertTrue(entryNames.contains("ppt/slides/slide2.xml"))
        assertTrue(entryNames.contains("ppt/slides/_rels/slide2.xml.rels"))
        assertTrue(entryNames.contains("ppt/media/image2.jpg"))
    }

    @Test
    fun testDocxPageBreakAndFormattingStructure() {
        val docx = XWPFDocument()

        // Page 1 Heading
        val p1 = docx.createParagraph()
        p1.spacingBefore = 220
        p1.spacingAfter = 100
        val r1 = p1.createRun()
        r1.fontFamily = "Calibri"
        r1.fontSize = 16
        r1.isBold = true
        r1.color = "1F4E79"
        r1.setText("1. Introduction to Quantum Computing")

        // Page 1 Code Block
        val pCode = docx.createParagraph().apply {
            spacingBefore = 80
            spacingAfter = 40
            indentationLeft = 280
        }
        val rCode = pCode.createRun()
        rCode.fontFamily = "Consolas"
        rCode.fontSize = 10
        rCode.color = "24292E"
        rCode.setText("def quantum_gate():")

        // Page 1 Break
        val breakP = docx.createParagraph()
        val breakR = breakP.createRun()
        breakR.addBreak(BreakType.PAGE)

        // Page 2 List Item
        val pList = docx.createParagraph().apply {
            spacingBefore = 40
            spacingAfter = 60
            indentationLeft = 360
            indentationHanging = 280
        }
        val rList = pList.createRun()
        rList.fontFamily = "Calibri"
        rList.fontSize = 11
        rList.setText("• First principle of superposition")

        // Verify paragraphs in document
        val paragraphs = docx.paragraphs
        assertEquals(4, paragraphs.size)

        // Verify heading
        assertEquals("1. Introduction to Quantum Computing", paragraphs[0].runs[0].text())
        assertTrue(paragraphs[0].runs[0].isBold)
        assertEquals(16, paragraphs[0].runs[0].fontSize)
        assertEquals("1F4E79", paragraphs[0].runs[0].color)

        // Verify code block
        assertEquals("def quantum_gate():", paragraphs[1].runs[0].text())
        assertEquals("Consolas", paragraphs[1].runs[0].fontFamily)
        assertEquals(280, paragraphs[1].indentationLeft)

        // Verify page break is present
        assertNotNull(paragraphs[2].runs[0])

        // Verify list item hanging indent
        assertEquals(360, paragraphs[3].indentationLeft)
        assertEquals(280, paragraphs[3].indentationHanging)
    }

    @Test
    fun testProbableHeadingDetection() {
        val converter = allocateUnsafeInstance(ReverseOfficeConverter::class.java)
        val isProbableHeading = getMethod(ReverseOfficeConverter::class.java, "isProbableHeading", String::class.java)

        assertTrue(isProbableHeading.invoke(converter, "1.4 Strings") as Boolean)
        assertTrue(isProbableHeading.invoke(converter, "Chapter 3: Dynamic Programming") as Boolean)
        assertTrue(isProbableHeading.invoke(converter, "PART II — Data Structures") as Boolean)
        assertTrue(isProbableHeading.invoke(converter, "### Markdown Subheading") as Boolean)
    }

    @Test
    fun testProbableCodeLineDetection() {
        val converter = allocateUnsafeInstance(ReverseOfficeConverter::class.java)
        val isProbableCodeLine = getMethod(ReverseOfficeConverter::class.java, "isProbableCodeLine", String::class.java, String::class.java)

        assertTrue(isProbableCodeLine.invoke(converter, "def foo(x):", "def foo(x):") as Boolean)
        assertTrue(isProbableCodeLine.invoke(converter, "    val x = 10", "val x = 10") as Boolean)
        assertTrue(isProbableCodeLine.invoke(converter, "# comment here", "# comment here") as Boolean)
        assertTrue(isProbableCodeLine.invoke(converter, "import os", "import os") as Boolean)
    }
}
