package com.karnadigital.omnisuite.core.engine.document

import android.content.Context
import com.tomroush.pdfbox.pdmodel.PDDocument
import com.tomroush.pdfbox.pdmodel.PDPage
import com.tomroush.pdfbox.pdmodel.PDPageContentStream
import com.tomroush.pdfbox.pdmodel.common.PDRectangle
import com.tomroush.pdfbox.pdmodel.font.PDType1Font
import com.tomroush.pdfbox.util.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Offline-first office document format (Word, Excel) to PDF conversion engine.
 * Synthesizes formatted text blocks and landscape cell tables into standard paginated PDFs.
 * Runs fully on Dispatchers.IO background contexts with aggressive resource management.
 */
object OfficeConverter {

    /**
     * Converts a DOCX Word file paragraph-by-paragraph to an A4 PDF with dynamic line-wrapping and pagination.
     */
    suspend fun convertDocxToPdf(context: Context, docxFile: File, pdfFile: File) = withContext(Dispatchers.IO) {
        // Enforce PDFBox resource loading setup
        PDFBoxResourceLoader.init(context)

        var docxStream: FileInputStream? = null
        var docx: XWPFDocument? = null
        var pdf: PDDocument? = null
        var contentStream: PDPageContentStream? = null

        try {
            docxStream = FileInputStream(docxFile)
            docx = XWPFDocument(docxStream)
            pdf = PDDocument()

            val fontNormal = PDType1Font.HELVETICA
            val fontBold = PDType1Font.HELVETICA_BOLD
            val fontSizeNormal = 11f
            val fontSizeHeading = 15f
            val leadingNormal = fontSizeNormal * 1.25f
            val leadingHeading = fontSizeHeading * 1.25f

            // A4 Bounds: 595 x 842 points
            val pageBounds = PDRectangle.A4
            val margin = 50f
            val printableWidth = pageBounds.width - (2 * margin)

            var currentPage = PDPage(pageBounds)
            pdf.addPage(currentPage)
            contentStream = PDPageContentStream(pdf, currentPage)

            var yPosition = pageBounds.height - margin

            for (paragraph in docx.paragraphs) {
                val isHeading = paragraph.styleID?.lowercase()?.contains("heading") == true ||
                        paragraph.runs.firstOrNull()?.fontSize ?: 0 > 14
                
                val font = if (isHeading) fontBold else fontNormal
                val fontSize = if (isHeading) fontSizeHeading else fontSizeNormal
                val leading = if (isHeading) leadingHeading else leadingNormal

                // Combine paragraph runs text
                val fullText = paragraph.runs.joinToString("") { it.getText(0) ?: "" }
                if (fullText.isEmpty()) {
                    // Empty paragraph serves as spacer
                    yPosition -= leading
                    if (yPosition < margin) {
                        contentStream.close()
                        currentPage = PDPage(pageBounds)
                        pdf.addPage(currentPage)
                        contentStream = PDPageContentStream(pdf, currentPage)
                        yPosition = pageBounds.height - margin
                    }
                    continue
                }

                // Wrap text
                val lines = wrapText(fullText, font, fontSize, printableWidth)

                for (line in lines) {
                    val sanitizedLine = sanitizeText(line)

                    // Check page bound break
                    if (yPosition - leading < margin) {
                        contentStream.close()
                        currentPage = PDPage(pageBounds)
                        pdf.addPage(currentPage)
                        contentStream = PDPageContentStream(pdf, currentPage)
                        yPosition = pageBounds.height - margin
                    }

                    // Paint line onto canvas stream
                    contentStream.beginText()
                    contentStream.setFont(font, fontSize)
                    contentStream.newLineAtOffset(margin, yPosition)
                    contentStream.showText(sanitizedLine)
                    contentStream.endText()

                    yPosition -= leading
                }

                // Paragraph separation spacing
                yPosition -= 6f
            }

            contentStream.close()
            contentStream = null

            FileOutputStream(pdfFile).use { out ->
                pdf.save(out)
            }

        } finally {
            try {
                contentStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                pdf?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                docx?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                docxStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Converts a XLSX spreadsheet sheet-by-sheet to landscape A4 PDFs drawing clean cell gridlines.
     */
    suspend fun convertXlsxToPdf(context: Context, xlsxFile: File, pdfFile: File) = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context)

        var xlsxStream: FileInputStream? = null
        var workbook: XSSFWorkbook? = null
        var pdf: PDDocument? = null
        var contentStream: PDPageContentStream? = null

        try {
            xlsxStream = FileInputStream(xlsxFile)
            workbook = XSSFWorkbook(xlsxStream)
            pdf = PDDocument()

            // Landscape A4 bounds: width 842f, height 595f
            val pageBounds = PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width)
            val margin = 40f
            val printableWidth = pageBounds.width - (2 * margin)

            val fontNormal = PDType1Font.HELVETICA
            val fontBold = PDType1Font.HELVETICA_BOLD
            val fontSizeCell = 9f
            val rowHeight = 22f

            for (sheetIndex in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheetAt(sheetIndex)
                
                // Identify active column bounds
                var maxCol = 0
                for (rowIndex in 0..sheet.lastRowNum) {
                    val row = sheet.getRow(rowIndex) ?: continue
                    if (row.lastCellNum > maxCol) {
                        maxCol = row.lastCellNum.toInt()
                    }
                }

                if (maxCol == 0) continue // Empty sheet

                // Dynamically fit columns in printable width
                val colWidth = Math.min(150f, printableWidth / maxCol)

                var currentPage = PDPage(pageBounds)
                pdf.addPage(currentPage)
                contentStream = PDPageContentStream(pdf, currentPage)

                // Page headers and title
                contentStream.beginText()
                contentStream.setFont(fontBold, 12f)
                contentStream.newLineAtOffset(margin, pageBounds.height - margin + 12f)
                contentStream.showText("Spreadsheet Export: ${sheet.sheetName}")
                contentStream.endText()

                var yPosition = pageBounds.height - margin - 20f

                for (rowIndex in 0..sheet.lastRowNum) {
                    val row = sheet.getRow(rowIndex) ?: continue

                    // Check bounds for page breaks
                    if (yPosition - rowHeight < margin) {
                        contentStream.close()
                        currentPage = PDPage(pageBounds)
                        pdf.addPage(currentPage)
                        contentStream = PDPageContentStream(pdf, currentPage)
                        yPosition = pageBounds.height - margin - 20f
                    }

                    // Draw cell grid lines and values
                    for (colIndex in 0 until maxCol) {
                        val cell = row.getCell(colIndex)
                        val cellValue = when {
                            cell == null -> ""
                            cell.cellType == CellType.NUMERIC -> {
                                val num = cell.numericCellValue
                                if (num == num.toLong().toDouble()) num.toLong().toString() else num.toString()
                            }
                            cell.cellType == CellType.BOOLEAN -> cell.booleanCellValue.toString()
                            cell.cellType == CellType.FORMULA -> {
                                try {
                                    cell.stringCellValue
                                } catch (e: Exception) {
                                    try {
                                        cell.numericCellValue.toString()
                                    } catch (ex: Exception) {
                                        ""
                                    }
                                }
                            }
                            else -> cell.stringCellValue ?: ""
                        }

                        val cellX = margin + (colIndex * colWidth)

                        // 1. Draw Cell border lines
                        contentStream.setStrokingColor(200, 200, 200)
                        contentStream.setLineWidth(0.5f)
                        contentStream.moveTo(cellX, yPosition)
                        contentStream.lineTo(cellX + colWidth, yPosition)
                        contentStream.lineTo(cellX + colWidth, yPosition - rowHeight)
                        contentStream.lineTo(cellX, yPosition - rowHeight)
                        contentStream.lineTo(cellX, yPosition)
                        contentStream.stroke()

                        // 2. Draw cell string values (with custom truncation if needed)
                        if (cellValue.isNotBlank()) {
                            val sanitizedValue = sanitizeText(cellValue)
                            val displayValue = truncateToWidth(sanitizedValue, fontNormal, fontSizeCell, colWidth - 8f)

                            contentStream.beginText()
                            contentStream.setFont(fontNormal, fontSizeCell)
                            contentStream.newLineAtOffset(cellX + 4f, yPosition - rowHeight + 6f)
                            contentStream.showText(displayValue)
                            contentStream.endText()
                        }
                    }

                    yPosition -= rowHeight
                }

                contentStream.close()
                contentStream = null
            }

            FileOutputStream(pdfFile).use { out ->
                pdf.save(out)
            }

        } finally {
            try {
                contentStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                pdf?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                workbook?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                xlsxStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Greedy word wrapping routine checking exact horizontal width budget constraint.
     */
    private fun wrapText(text: String, font: PDType1Font, fontSize: Float, maxWidth: Float): List<String> {
        val words = text.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            if (word.isEmpty()) continue
            val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
            val cleanTestLine = sanitizeText(testLine)
            try {
                val width = font.getStringWidth(cleanTestLine) / 1000f * fontSize
                if (width <= maxWidth) {
                    currentLine.append(if (currentLine.isEmpty()) word else " $word")
                } else {
                    if (currentLine.isNotEmpty()) {
                        lines.add(currentLine.toString())
                    }
                    currentLine = StringBuilder(word)
                }
            } catch (e: Exception) {
                currentLine.append(if (currentLine.isEmpty()) word else " $word")
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines
    }

    /**
     * Truncates text greedily with an ellipsis fallback to guarantee matrix visual alignment.
     */
    private fun truncateToWidth(text: String, font: PDType1Font, fontSize: Float, maxWidth: Float): String {
        try {
            var width = font.getStringWidth(text) / 1000f * fontSize
            if (width <= maxWidth) return text

            var truncated = text
            while (truncated.isNotEmpty() && width > maxWidth) {
                truncated = truncated.dropLast(1)
                width = font.getStringWidth("$truncated...") / 1000f * fontSize
            }
            return if (truncated.isEmpty()) "" else "$truncated..."
        } catch (e: Exception) {
            return text
        }
    }

    /**
     * Filters high-unicode glyph ranges above WinAnsiEncoding bounds to avoid PDFBox rendering exception loops.
     */
    private fun sanitizeText(text: String): String {
        val sb = StringBuilder()
        for (char in text) {
            val code = char.code
            if (code in 32..126 || code in 160..255) {
                sb.append(char)
            } else if (char == '\n' || char == '\r' || char == '\t') {
                sb.append(' ')
            } else {
                when (char) {
                    '‘', '’' -> sb.append('\'')
                    '“', '”' -> sb.append('"')
                    '–', '—' -> sb.append('-')
                    else -> sb.append(' ')
                }
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ")
    }
}
