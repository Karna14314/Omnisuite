package com.karnadigital.omnisuite.core.engine

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import com.karnadigital.omnisuite.core.util.TextSearchUtils
import java.io.File
import java.io.FileInputStream

data class SearchResult(
    val pageIndex: Int,           // 0-indexed page index (or sheet index, or paragraph index)
    val textSnippet: String,      // Context snippet around match
    val extraData: String? = null // Coordinates or metadata like "SheetName,row,col"
)

object DocumentSearchEngine {

    /**
     * Searches a PDF file page-by-page case-insensitively using PDFBox.
     */
    fun searchPdf(filePath: String, query: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        if (query.isBlank()) return results
        var doc: PDDocument? = null
        try {
            doc = PDDocument.load(File(filePath))
            val pageCount = doc.numberOfPages
            val stripper = PDFTextStripper()
            for (i in 0 until pageCount) {
                stripper.startPage = i + 1
                stripper.endPage = i + 1
                val text = stripper.getText(doc) ?: ""
                for (pos in TextSearchUtils.findAllMatchIndices(text, query)) {
                    val start = maxOf(0, pos - 25)
                    val end = minOf(text.length, pos + query.length + 25)
                    val snippet = (if (start > 0) "..." else "") + 
                                  text.substring(start, end).replace('\n', ' ').trim() + 
                                  (if (end < text.length) "..." else "")
                    results.add(SearchResult(pageIndex = i, textSnippet = snippet))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                doc?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return results
    }

    /**
     * Searches a DOCX file paragraph-by-paragraph using Apache POI.
     */
    fun searchDocx(filePath: String, query: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        if (query.isBlank()) return results
        var fis: FileInputStream? = null
        var doc: XWPFDocument? = null
        try {
            fis = FileInputStream(File(filePath))
            doc = XWPFDocument(fis)
            val paragraphs = doc.paragraphs
            paragraphs.forEachIndexed { i, paragraph ->
                val text = paragraph.text ?: ""
                for (pos in TextSearchUtils.findAllMatchIndices(text, query)) {
                    val start = maxOf(0, pos - 25)
                    val end = minOf(text.length, pos + query.length + 25)
                    val snippet = (if (start > 0) "..." else "") + 
                                  text.substring(start, end).replace('\n', ' ').trim() + 
                                  (if (end < text.length) "..." else "")
                    results.add(
                        SearchResult(
                            pageIndex = i,
                            textSnippet = snippet,
                            extraData = "Paragraph ${i + 1}"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                doc?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                fis?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return results
    }

    /**
     * Searches a DOC file paragraph-by-paragraph using Apache POI.
     */
    fun searchDoc(filePath: String, query: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        if (query.isBlank()) return results
        var fis: java.io.FileInputStream? = null
        var doc: org.apache.poi.hwpf.HWPFDocument? = null
        try {
            fis = java.io.FileInputStream(java.io.File(filePath))
            doc = org.apache.poi.hwpf.HWPFDocument(fis)
            val range = doc.range
            val numParagraphs = range.numParagraphs()
            for (i in 0 until numParagraphs) {
                val paragraph = range.getParagraph(i)
                val text = paragraph.text() ?: ""
                for (pos in TextSearchUtils.findAllMatchIndices(text, query)) {
                    val start = maxOf(0, pos - 25)
                    val end = minOf(text.length, pos + query.length + 25)
                    val snippet = (if (start > 0) "..." else "") + 
                                  text.substring(start, end).replace('\n', ' ').trim() + 
                                  (if (end < text.length) "..." else "")
                    results.add(
                        SearchResult(
                            pageIndex = i,
                            textSnippet = snippet,
                            extraData = "Paragraph ${i + 1}"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { doc?.close() } catch (e: Exception) {}
            try { fis?.close() } catch (e: Exception) {}
        }
        return results
    }

    /**
     * Searches a XLSX workbook sheet-by-sheet, row-by-row, cell-by-cell using Apache POI.
     */
    fun searchXlsx(filePath: String, query: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        if (query.isBlank()) return results
        var fis: FileInputStream? = null
        var wb: XSSFWorkbook? = null
        try {
            fis = FileInputStream(File(filePath))
            wb = XSSFWorkbook(fis)
            val sheetCount = wb.numberOfSheets
            for (s in 0 until sheetCount) {
                val sheet = wb.getSheetAt(s)
                val sheetName = sheet.sheetName ?: "Sheet ${s + 1}"
                val lastRowNum = sheet.lastRowNum
                for (r in 0..lastRowNum) {
                    val row = sheet.getRow(r) ?: continue
                    val lastCellNum = row.lastCellNum.toInt()
                    for (c in 0 until lastCellNum) {
                        val cell = row.getCell(c) ?: continue
                        val cellValue = getFormattedCellValue(cell)
                        if (cellValue.contains(query, ignoreCase = true)) {
                            val colLetter = getColumnLetter(c)
                            val coord = "$colLetter${r + 1}"
                            results.add(
                                SearchResult(
                                    pageIndex = s,
                                    textSnippet = "[$sheetName] Cell $coord: $cellValue",
                                    extraData = "$r,$c"
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                wb?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                fis?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return results
    }

    private fun getColumnLetter(colIndex: Int): String {
        var temp = colIndex
        val letter = StringBuilder()
        while (temp >= 0) {
            letter.insert(0, ('A'.toInt() + (temp % 26)).toChar())
            temp = (temp / 26) - 1
        }
        return letter.toString()
    }

    private fun getFormattedCellValue(cell: Cell): String {
        return try {
            when (cell.cellType) {
                CellType.STRING -> cell.stringCellValue ?: ""
                CellType.NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        cell.dateCellValue?.toString() ?: ""
                    } else {
                        val numeric = cell.numericCellValue
                        if (numeric == numeric.toLong().toDouble()) {
                            numeric.toLong().toString()
                        } else {
                            numeric.toString()
                        }
                    }
                }
                CellType.BOOLEAN -> cell.booleanCellValue.toString()
                CellType.FORMULA -> {
                    try {
                        cell.stringCellValue ?: ""
                    } catch (e: Exception) {
                        try {
                            cell.numericCellValue.toString()
                        } catch (e2: Exception) {
                            cell.cellFormula ?: ""
                        }
                    }
                }
                CellType.BLANK -> ""
                else -> cell.toString()
            }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Searches a PPTX/PPT presentation file slide-by-slide case-insensitively.
     */
    fun searchPptx(filePath: String, query: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        if (query.isBlank()) return results
        var fis: java.io.FileInputStream? = null
        var slideshow: org.apache.poi.sl.usermodel.SlideShow<*, *>? = null
        try {
            fis = java.io.FileInputStream(java.io.File(filePath))
            slideshow = if (filePath.endsWith(".ppt", ignoreCase = true)) {
                org.apache.poi.hslf.usermodel.HSLFSlideShow(fis)
            } else {
                org.apache.poi.xslf.usermodel.XMLSlideShow(fis)
            }
            val slides = slideshow.slides
            for (i in slides.indices) {
                val slide = slides[i]
                val text = StringBuilder()
                for (shape in slide.shapes) {
                    if (shape is org.apache.poi.sl.usermodel.TextShape<*, *>) {
                        text.append(shape.text ?: "").append("\n")
                    }
                }
                val slideText = text.toString()
                for (pos in TextSearchUtils.findAllMatchIndices(slideText, query)) {
                    val start = maxOf(0, pos - 25)
                    val end = minOf(slideText.length, pos + query.length + 25)
                    val snippet = (if (start > 0) "..." else "") + 
                                  slideText.substring(start, end).replace('\n', ' ').trim() + 
                                  (if (end < slideText.length) "..." else "")
                    results.add(SearchResult(pageIndex = i, textSnippet = "Slide ${i + 1}: $snippet"))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { slideshow?.close() } catch (e: Exception) {}
            try { fis?.close() } catch (e: Exception) {}
        }
        return results
    }

    /**
     * Searches a TXT text file line-by-line case-insensitively.
     */
    fun searchTxt(filePath: String, query: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        if (query.isBlank()) return results
        try {
            val file = java.io.File(filePath)
            if (file.exists() && file.isFile) {
                val lines = file.readLines()
                lines.forEachIndexed { i, line ->
                    for (pos in TextSearchUtils.findAllMatchIndices(line, query)) {
                        val start = maxOf(0, pos - 25)
                        val end = minOf(line.length, pos + query.length + 25)
                        val snippet = (if (start > 0) "..." else "") + 
                                      line.substring(start, end).trim() + 
                                      (if (end < line.length) "..." else "")
                        results.add(SearchResult(pageIndex = i, textSnippet = "Line ${i + 1}: $snippet"))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }
}
