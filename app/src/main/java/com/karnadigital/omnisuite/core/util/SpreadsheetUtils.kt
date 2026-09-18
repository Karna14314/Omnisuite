package com.karnadigital.omnisuite.core.util

import java.io.File

/**
 * Pure, testable helpers for spreadsheet handling used by the XLSX viewer.
 */
object SpreadsheetUtils {

    /**
     * Type-aware comparison of two cell display values for spreadsheet sorting.
     *
     * - Blank/null values always sort last.
     * - If both values parse as numbers they are compared numerically so that,
     *   for example, "10" sorts after "2".
     * - Otherwise (or when the types are mixed) a case-insensitive string
     *   comparison is used as a fallback.
     */
    fun compareCellValues(a: String?, b: String?): Int {
        val aBlank = a.isNullOrBlank()
        val bBlank = b.isNullOrBlank()
        if (aBlank && bBlank) return 0
        if (aBlank) return 1
        if (bBlank) return -1

        val aNum = a!!.toDoubleOrNull()
        val bNum = b!!.toDoubleOrNull()
        if (aNum != null && bNum != null) return aNum.compareTo(bNum)

        // Mixed numeric/text: numbers sort before text for a stable ordering.
        if (aNum != null) return -1
        if (bNum != null) return 1

        return a.compareTo(b, ignoreCase = true)
    }

    /**
     * Determines whether a file should be parsed as a delimited text (CSV/TSV) document.
     *
     * Rules:
     * - `.csv`, `.tsv`, `.tab` extensions are always treated as delimited text.
     * - `.xls` / `.xlsx` (ZIP/OLE2 containers) are never CSV.
     * - For any other file we perform a lightweight content sniff: it must not be a
     *   ZIP container and its first records must contain a comma or tab delimiter.
     *   This prevents unrelated files (e.g. PDFs) from being misclassified as CSV.
     */
    fun isCsvFile(file: File): Boolean {
        val name = file.name.lowercase()
        if (name.endsWith(".csv") || name.endsWith(".tsv") || name.endsWith(".tab")) return true
        if (name.endsWith(".xls") || name.endsWith(".xlsx")) return false
        if (isZipContainer(file)) return false
        return sniffDelimited(file)
    }

    private fun isZipContainer(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return try {
            file.inputStream().use { fis ->
                val magic = ByteArray(4)
                val read = fis.read(magic)
                read == 4 && magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte() // "PK"
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun sniffDelimited(file: File): Boolean {
        return try {
            file.bufferedReader().use { reader ->
                repeat(5) {
                    val line = reader.readLine() ?: return@use false
                    if (line.contains(',') || line.contains('\t')) return true
                }
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun getColumnLetter(colIndex: Int): String {
        var c = colIndex
        var result = ""
        while (c >= 0) {
            result = ('A'.code + (c % 26)).toChar() + result
            c = c / 26 - 1
        }
        return result
    }
}
