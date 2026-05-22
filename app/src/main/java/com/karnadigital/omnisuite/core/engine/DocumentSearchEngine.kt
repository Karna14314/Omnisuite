package com.karnadigital.omnisuite.core.engine

import com.tomroush.pdfbox.pdmodel.PDDocument
import com.tomroush.pdfbox.text.PDFTextStripper
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
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
                var pos = text.indexOf(query, ignoreCase = true)
                while (pos >= 0) {
                    val start = maxOf(0, pos - 25)
                    val end = minOf(text.length, pos + query.length + 25)
                    val snippet = (if (start > 0) "..." else "") + 
                                  text.substring(start, end).replace('\n', ' ').trim() + 
                                  (if (end < text.length) "..." else "")
                    results.add(SearchResult(pageIndex = i, textSnippet = snippet))
                    pos = text.indexOf(query, pos + 1, ignoreCase = true)
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
                var pos = text.indexOf(query, ignoreCase = true)
                while (pos >= 0) {
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
                    pos = text.indexOf(query, pos + 1, ignoreCase = true)
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
}
