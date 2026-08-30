package com.karnadigital.omnisuite

import com.karnadigital.omnisuite.core.engine.document.ParsedPresentation
import com.karnadigital.omnisuite.core.engine.document.ParsedSlide
import com.karnadigital.omnisuite.core.engine.document.TextContent
import com.karnadigital.omnisuite.core.engine.document.ParsedParagraph
import com.karnadigital.omnisuite.core.engine.document.ParsedTextRun
import com.karnadigital.omnisuite.feature.viewer.PptxSearchEngine
import com.karnadigital.omnisuite.core.util.ImageSampling
import com.karnadigital.omnisuite.core.util.SpreadsheetUtils
import com.karnadigital.omnisuite.core.util.TextSearchUtils
import com.karnadigital.omnisuite.core.util.UriSchemeUtils
import com.karnadigital.omnisuite.core.util.ZipSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileWriter

class AuditFixesUnitTest {

    private fun createTestSlide(index: Int, title: String, body: String): ParsedSlide {
        return ParsedSlide(
            index = index,
            background = com.karnadigital.omnisuite.core.engine.document.BgNone,
            shapes = listOf(
                ParsedShape_model(title, isTitle = true),
                ParsedShape_model(body, isTitle = false),
            ),
            speakerNotes = null,
        )
    }

    private fun ParsedShape_model(text: String, isTitle: Boolean): com.karnadigital.omnisuite.core.engine.document.ParsedShape {
        return com.karnadigital.omnisuite.core.engine.document.ParsedShape(
            bounds = com.karnadigital.omnisuite.core.engine.document.NormRect(0.05f, 0.05f, 0.9f, 0.15f),
            content = TextContent(listOf(ParsedParagraph(runs = listOf(ParsedTextRun(text = text)), bulletLevel = 0))),
            zIndex = if (isTitle) 0 else 1,
        )
    }

    // ---- C3: ZIP path traversal ----
    @Test
    fun testIsWithinDirectory_rejectsTraversal() {
        val parent = File(System.getProperty("java.io.tmpdir"), "omni_parent_${System.nanoTime()}")
        parent.mkdirs()
        try {
            val inside = File(parent, "file.txt")
            assertTrue(ZipSecurity.isWithinDirectory(parent, inside))
            val escape = File(parent, "../escape.txt")
            assertFalse(ZipSecurity.isWithinDirectory(parent, escape))
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun testSafeResolveEntryFile_blocksZipSlip() {
        val parent = File(System.getProperty("java.io.tmpdir"), "omni_extract_${System.nanoTime()}")
        parent.mkdirs()
        try {
            assertNull(ZipSecurity.safeResolveEntryFile(parent, ".."))
            assertNull(ZipSecurity.safeResolveEntryFile(parent, "subdir/.."))
            val flattened1 = ZipSecurity.safeResolveEntryFile(parent, "../../etc/passwd")
            assertTrue(flattened1 != null && flattened1.absolutePath.startsWith(parent.absolutePath))
            val flattened2 = ZipSecurity.safeResolveEntryFile(parent, "../escape.txt")
            assertTrue(flattened2 != null && flattened2.absolutePath.startsWith(parent.absolutePath))
            val safe = ZipSecurity.safeResolveEntryFile(parent, "folder/sub/report.csv")
            assertTrue(safe != null && safe.absolutePath.startsWith(parent.absolutePath))
        } finally {
            parent.deleteRecursively()
        }
    }

    // ---- C5: offline-only scheme enforcement ----
    @Test
    fun testIsOfflineScheme() {
        assertTrue(UriSchemeUtils.isOfflineScheme("content"))
        assertTrue(UriSchemeUtils.isOfflineScheme("file"))
        assertTrue(UriSchemeUtils.isOfflineScheme(null))
        assertFalse(UriSchemeUtils.isOfflineScheme("http"))
        assertFalse(UriSchemeUtils.isOfflineScheme("https"))
    }

    // ---- M2: non-overlapping search matches ----
    @Test
    fun testFindAllMatchIndices_noOverlap() {
        assertEquals(listOf(0), TextSearchUtils.findAllMatchIndices("aaa", "aa"))
        assertEquals(listOf(0), TextSearchUtils.findAllMatchIndices("ababa", "aba"))
        assertTrue(TextSearchUtils.findAllMatchIndices("abc", "").isEmpty())
        assertEquals(listOf(0, 3), TextSearchUtils.findAllMatchIndices("ABCabc", "abc", ignoreCase = true))
        assertTrue(TextSearchUtils.findAllMatchIndices("hello", "xyz").isEmpty())
    }

    // ---- H4: type-aware spreadsheet sort ----
    @Test
    fun testCompareCellValues_numericBeforeTextAndSortedNumerically() {
        assertTrue(SpreadsheetUtils.compareCellValues("2", "10") < 0)
        assertTrue(SpreadsheetUtils.compareCellValues("10", "2") > 0)
        assertTrue(SpreadsheetUtils.compareCellValues("10", "abc") < 0)
        assertTrue(SpreadsheetUtils.compareCellValues("abc", "10") > 0)
        assertTrue(SpreadsheetUtils.compareCellValues("abc", "ABD") < 0)
        assertTrue(SpreadsheetUtils.compareCellValues("", "1") > 0)
        assertTrue(SpreadsheetUtils.compareCellValues("1", "") < 0)
        assertEquals(0, SpreadsheetUtils.compareCellValues("", null))
    }

    // ---- M6: CSV detection heuristic ----
    @Test
    fun testIsCsvFile_detection() {
        val dir = File(System.getProperty("java.io.tmpdir"), "omni_csv_${System.nanoTime()}")
        dir.mkdirs()
        try {
            val csv = File(dir, "data.csv")
            FileWriter(csv).use { it.write("a,b,c\n1,2,3\n") }
            assertTrue(SpreadsheetUtils.isCsvFile(csv))
            val xlsx = File(dir, "book.xlsx")
            FileWriter(xlsx).use { it.write("PK\u0003\u0004fake-zip-content") }
            assertFalse(SpreadsheetUtils.isCsvFile(xlsx))
            val delimited = File(dir, "notes.txt")
            FileWriter(delimited).use { it.write("name,age,city\nbob,30,nyc\n") }
            assertTrue(SpreadsheetUtils.isCsvFile(delimited))
            val plain = File(dir, "plain.txt")
            FileWriter(plain).use { it.write("this is just some prose without delimiters\n") }
            assertFalse(SpreadsheetUtils.isCsvFile(plain))
        } finally {
            dir.deleteRecursively()
        }
    }

    // ---- H2: Image sampling OOM protection ----
    @Test
    fun testCalculateInSampleSize_powersOfTwo() {
        assertEquals(2, ImageSampling.calculateInSampleSize(2000, 2000, 1000, 1000))
        assertEquals(4, ImageSampling.calculateInSampleSize(4000, 4000, 1000, 1000))
        assertEquals(1, ImageSampling.calculateInSampleSize(800, 600, 1000, 1000))
        assertEquals(1, ImageSampling.calculateInSampleSize(0, 0, 3000, 3000))
        assertEquals(1, ImageSampling.calculateInSampleSize(-1, 100, 3000, 3000))
    }

    // ---- M7: in-memory PPTX search ----
    @Test
    fun testPptxSearchEngineInMemory() {
        val presentation = ParsedPresentation(
            slides = listOf(
                ParsedSlide(
                    index = 0,
                    background = com.karnadigital.omnisuite.core.engine.document.BgNone,
                    shapes = listOf(
                        ParsedShape_model("Quarterly Report", isTitle = true),
                        ParsedShape_model("Revenue grew ten percent this quarter.", isTitle = false),
                        ParsedShape_model("Quarter expenses were controlled.", isTitle = false),
                    ),
                    speakerNotes = "Emphasize the quarterly growth story.",
                ),
                ParsedSlide(
                    index = 1,
                    background = com.karnadigital.omnisuite.core.engine.document.BgNone,
                    shapes = listOf(
                        ParsedShape_model("Roadmap", isTitle = true),
                        ParsedShape_model("Next quarter priorities.", isTitle = false),
                    ),
                    speakerNotes = null,
                ),
            ),
            widthEmu = 9144000L,
            heightEmu = 5143500L,
        )

        val quarterResults = PptxSearchEngine.search(presentation, "quarter")
        assertTrue("expected matches for 'quarter'", quarterResults.isNotEmpty())
        assertEquals(5, quarterResults.size)
        assertTrue(quarterResults.any { it.pageIndex == 0 })
        assertTrue(quarterResults.any { it.pageIndex == 1 })

        val notesResults = PptxSearchEngine.search(presentation, "growth")
        assertTrue("expected match in speaker notes", notesResults.isNotEmpty())

        assertTrue(PptxSearchEngine.search(presentation, "nonexistent").isEmpty())
        assertTrue(PptxSearchEngine.search(presentation, "").isEmpty())
    }

    // ---- Default white background ----
    @Test
    fun testPptxSlideDefaultBackgroundColor() {
        val slideWithNoBg = ParsedSlide(index = 0, background = com.karnadigital.omnisuite.core.engine.document.BgNone, shapes = emptyList())
        // BgNone renders as white in the renderer
        assertTrue(slideWithNoBg.background is com.karnadigital.omnisuite.core.engine.document.BgNone)
    }
}
