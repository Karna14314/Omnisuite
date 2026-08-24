package com.karnadigital.omnisuite

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

/**
 * Verification for the Phase A (low-complexity) fixes described in docs/audit.md:
 *  - C3  ZIP path traversal (ZipSecurity)
 *  - C5  Offline-only scheme enforcement (UriSchemeUtils)
 *  - M2  Non-overlapping search matches (TextSearchUtils)
 *  - H4  Type-aware spreadsheet sort (SpreadsheetUtils.compareCellValues)
 *  - M6  CSV detection heuristic (SpreadsheetUtils.isCsvFile)
 */
class AuditFixesUnitTest {

    // ---- C3: ZIP path traversal ----
    @Test
    fun testIsWithinDirectory_rejectsTraversal() {
        val parent = File(System.getProperty("java.io.tmpdir"), "omni_parent_${System.nanoTime()}")
        parent.mkdirs()
        try {
            val inside = File(parent, "file.txt")
            assertTrue(ZipSecurity.isWithinDirectory(parent, inside))
            // "../escape.txt" resolves outside the parent directory
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
            // Only entries whose *final* segment is ".." escape the directory and must be rejected
            assertNull(ZipSecurity.safeResolveEntryFile(parent, ".."))
            assertNull(ZipSecurity.safeResolveEntryFile(parent, "subdir/.."))
            // Entries with directory prefixes are flattened to their last segment, so traversal
            // sequences are neutralized and the file resolves safely *inside* the parent dir.
            val flattened1 = ZipSecurity.safeResolveEntryFile(parent, "../../etc/passwd")
            assertTrue(flattened1 != null && flattened1.absolutePath.startsWith(parent.absolutePath))
            val flattened2 = ZipSecurity.safeResolveEntryFile(parent, "../escape.txt")
            assertTrue(flattened2 != null && flattened2.absolutePath.startsWith(parent.absolutePath))
            // Safe names resolve correctly inside the parent
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
        // Non-overlapping: "aa" in "aaa" is a single match at index 0 (not 3 as the old `pos + 1` bug produced)
        assertEquals(listOf(0), TextSearchUtils.findAllMatchIndices("aaa", "aa"))
        // "aba" in "ababa" matches at index 0; the second candidate at index 2 overlaps so it is skipped
        assertEquals(listOf(0), TextSearchUtils.findAllMatchIndices("ababa", "aba"))
        assertTrue(TextSearchUtils.findAllMatchIndices("abc", "").isEmpty())
        // case-insensitive: "ABC" at index 0 and "abc" at index 3 both match
        assertEquals(listOf(0, 3), TextSearchUtils.findAllMatchIndices("ABCabc", "abc", ignoreCase = true))
        assertTrue(TextSearchUtils.findAllMatchIndices("hello", "xyz").isEmpty())
    }

    // ---- H4: type-aware spreadsheet sort ----
    @Test
    fun testCompareCellValues_numericBeforeTextAndSortedNumerically() {
        // Numbers sort numerically: "2" < "10"
        assertTrue(SpreadsheetUtils.compareCellValues("2", "10") < 0)
        assertTrue(SpreadsheetUtils.compareCellValues("10", "2") > 0)
        // Numbers come before text for stable ordering
        assertTrue(SpreadsheetUtils.compareCellValues("10", "abc") < 0)
        assertTrue(SpreadsheetUtils.compareCellValues("abc", "10") > 0)
        // Text fallback is case-insensitive
        assertTrue(SpreadsheetUtils.compareCellValues("abc", "ABD") < 0)
        // Blank values always sort last
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
            // .csv extension → CSV
            val csv = File(dir, "data.csv")
            FileWriter(csv).use { it.write("a,b,c\n1,2,3\n") }
            assertTrue(SpreadsheetUtils.isCsvFile(csv))

            // .xlsx (ZIP container) → not CSV
            val xlsx = File(dir, "book.xlsx")
            FileWriter(xlsx).use { it.write("PK\u0003\u0004fake-zip-content") }
            assertFalse(SpreadsheetUtils.isCsvFile(xlsx))

            // delimited text → CSV
            val delimited = File(dir, "notes.txt")
            FileWriter(delimited).use { it.write("name,age,city\nbob,30,nyc\n") }
            assertTrue(SpreadsheetUtils.isCsvFile(delimited))

            // non-delimited text → not CSV (prevents misclassifying unrelated files)
            val plain = File(dir, "plain.txt")
            FileWriter(plain).use { it.write("this is just some prose without delimiters\n") }
            assertFalse(SpreadsheetUtils.isCsvFile(plain))
        } finally {
            dir.deleteRecursively()
        }
    }
}
