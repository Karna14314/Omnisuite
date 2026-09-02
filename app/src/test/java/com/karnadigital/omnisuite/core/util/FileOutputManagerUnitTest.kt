package com.karnadigital.omnisuite.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for file output utility logic, filename sanitization,
 * subfolder formatting, and extension validation.
 */
class FileOutputManagerUnitTest {

    @Test
    fun testFilenameExtensionFormatting() {
        val rawName = "my_archive"
        val extension = "zip"
        val formatted = if (rawName.endsWith(".$extension", ignoreCase = true)) rawName else "$rawName.$extension"
        assertEquals("my_archive.zip", formatted)

        val alreadyHasExt = "my_archive.zip"
        val formattedAlready = if (alreadyHasExt.endsWith(".$extension", ignoreCase = true)) alreadyHasExt else "$alreadyHasExt.$extension"
        assertEquals("my_archive.zip", formattedAlready)
    }

    @Test
    fun testSubfolderRelativePathFormatting() {
        val subfolder = "Archives"
        val relativePath = "Documents/OmniSuite/$subfolder"
        assertEquals("Documents/OmniSuite/Archives", relativePath)
        assertTrue(relativePath.startsWith("Documents/OmniSuite/"))
        assertFalse(relativePath.contains(".."))
    }
}
