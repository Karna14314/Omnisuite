package com.karnadigital.omnisuite.core.engine.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.apache.poi.xslf.usermodel.XSLFSimpleShape
import org.apache.poi.sl.usermodel.Placeholder
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
                        contentStream?.close()
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
                        contentStream?.close()
                        currentPage = PDPage(pageBounds)
                        pdf.addPage(currentPage)
                        contentStream = PDPageContentStream(pdf, currentPage)
                        yPosition = pageBounds.height - margin
                    }

                    // Paint line onto canvas stream
                    contentStream?.beginText()
                    contentStream?.setFont(font, fontSize)
                    contentStream?.newLineAtOffset(margin, yPosition)
                    contentStream?.showText(sanitizedLine)
                    contentStream?.endText()

                    yPosition -= leading
                }

                // Paragraph separation spacing
                yPosition -= 6f
            }

            contentStream?.close()
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
                contentStream?.beginText()
                contentStream?.setFont(fontBold, 12f)
                contentStream?.newLineAtOffset(margin, pageBounds.height - margin + 12f)
                contentStream?.showText("Spreadsheet Export: ${sheet.sheetName}")
                contentStream?.endText()

                var yPosition = pageBounds.height - margin - 20f

                for (rowIndex in 0..sheet.lastRowNum) {
                    val row = sheet.getRow(rowIndex) ?: continue

                    // Check bounds for page breaks
                    if (yPosition - rowHeight < margin) {
                        contentStream?.close()
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
                        contentStream?.setStrokingColor(200, 200, 200)
                        contentStream?.setLineWidth(0.5f)
                        contentStream?.moveTo(cellX, yPosition)
                        contentStream?.lineTo(cellX + colWidth, yPosition)
                        contentStream?.lineTo(cellX + colWidth, yPosition - rowHeight)
                        contentStream?.lineTo(cellX, yPosition - rowHeight)
                        contentStream?.lineTo(cellX, yPosition)
                        contentStream?.stroke()

                        // 2. Draw cell string values (with custom truncation if needed)
                        if (cellValue.isNotBlank()) {
                            val sanitizedValue = sanitizeText(cellValue)
                            val displayValue = truncateToWidth(sanitizedValue, fontNormal, fontSizeCell, colWidth - 8f)

                            contentStream?.beginText()
                            contentStream?.setFont(fontNormal, fontSizeCell)
                            contentStream?.newLineAtOffset(cellX + 4f, yPosition - rowHeight + 6f)
                            contentStream?.showText(displayValue)
                            contentStream?.endText()
                        }
                    }

                    yPosition -= rowHeight
                }

                contentStream?.close()
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
     * Converts a PPTX presentation file slide-by-slide to A4 PDF.
     * Supports "image" mode (accurate slide shapes and text locations rendered to bitmaps)
     * and "text" mode (clean reflowed text and title elements).
     */
    suspend fun convertPptxToPdf(context: Context, pptxFile: File, pdfFile: File, renderMode: String) = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context)

        var pptxStream: FileInputStream? = null
        var ppt: XMLSlideShow? = null
        var pdf: PDDocument? = null
        var contentStream: PDPageContentStream? = null

        try {
            pptxStream = FileInputStream(pptxFile)
            ppt = XMLSlideShow(pptxStream)
            pdf = PDDocument()

            // Landscape A4 bounds
            val pageBounds = PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width)
            val margin = 50f
            val printableWidth = pageBounds.width - (2 * margin)

            val fontNormal = PDType1Font.HELVETICA
            val fontBold = PDType1Font.HELVETICA_BOLD
            val fontSizeTitle = 18f
            val fontSizeBody = 12f
            val leadingTitle = fontSizeTitle * 1.3f
            val leadingBody = fontSizeBody * 1.3f

            for ((slideIndex, slide) in ppt.slides.withIndex()) {
                val currentPage = PDPage(pageBounds)
                pdf.addPage(currentPage)
                contentStream = PDPageContentStream(pdf, currentPage)

                if (renderMode.lowercase() == "image") {
                    // 1. Accurate slide shape rendering to bitmap using reflection to bypass AWT classpath blocks
                    val pageSizeObj = try { ppt.javaClass.getMethod("getPageSize").invoke(ppt) } catch(e: Exception) { null }
                    val slideWidth = if (pageSizeObj != null) {
                        try { (pageSizeObj.javaClass.getMethod("getWidth").invoke(pageSizeObj) as Number).toInt() } catch(e: Exception) { 720 }
                    } else 720
                    val slideHeight = if (pageSizeObj != null) {
                        try { (pageSizeObj.javaClass.getMethod("getHeight").invoke(pageSizeObj) as Number).toInt() } catch(e: Exception) { 540 }
                    } else 540
                    
                    // Create high-res bitmap (double size for crisp rendering in PDF)
                    val scaleFactor = 2f
                    val bitmap = Bitmap.createBitmap((slideWidth * scaleFactor).toInt(), (slideHeight * scaleFactor).toInt(), Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.scale(scaleFactor, scaleFactor)
                    canvas.drawColor(android.graphics.Color.WHITE) // Background fill

                    // Draw all shapes
                    for (shape in slide.shapes) {
                        val anchorObj = try { shape.javaClass.getMethod("getAnchor").invoke(shape) } catch(e: Exception) { null } ?: continue
                        val x = try { (anchorObj.javaClass.getMethod("getX").invoke(anchorObj) as Number).toFloat() } catch(e: Exception) { 0f }
                        val y = try { (anchorObj.javaClass.getMethod("getY").invoke(anchorObj) as Number).toFloat() } catch(e: Exception) { 0f }
                        val w = try { (anchorObj.javaClass.getMethod("getWidth").invoke(anchorObj) as Number).toFloat() } catch(e: Exception) { 0f }
                        val h = try { (anchorObj.javaClass.getMethod("getHeight").invoke(anchorObj) as Number).toFloat() } catch(e: Exception) { 0f }

                        if (shape is XSLFSimpleShape) {
                            val fillObj = try { shape.javaClass.getMethod("getFillColor").invoke(shape) } catch (e: Exception) { null }
                            if (fillObj != null) {
                                val fillPaint = Paint().apply {
                                    val r = try { fillObj.javaClass.getMethod("getRed").invoke(fillObj) as Int } catch (e: Exception) { 255 }
                                    val g = try { fillObj.javaClass.getMethod("getGreen").invoke(fillObj) as Int } catch (e: Exception) { 255 }
                                    val b = try { fillObj.javaClass.getMethod("getBlue").invoke(fillObj) as Int } catch (e: Exception) { 255 }
                                    val a = try { fillObj.javaClass.getMethod("getAlpha").invoke(fillObj) as Int } catch (e: Exception) { 255 }
                                    color = android.graphics.Color.argb(a, r, g, b)
                                    style = Paint.Style.FILL
                                }
                                canvas.drawRect(x, y, x + w, y + h, fillPaint)
                            }
                            
                            val lineObj = try { shape.javaClass.getMethod("getLineColor").invoke(shape) } catch (e: Exception) { null }
                            if (lineObj != null) {
                                val strokePaint = Paint().apply {
                                    val r = try { lineObj.javaClass.getMethod("getRed").invoke(lineObj) as Int } catch (e: Exception) { 0 }
                                    val g = try { lineObj.javaClass.getMethod("getGreen").invoke(lineObj) as Int } catch (e: Exception) { 0 }
                                    val b = try { lineObj.javaClass.getMethod("getBlue").invoke(lineObj) as Int } catch (e: Exception) { 0 }
                                    val a = try { lineObj.javaClass.getMethod("getAlpha").invoke(lineObj) as Int } catch (e: Exception) { 255 }
                                    color = android.graphics.Color.argb(a, r, g, b)
                                    style = Paint.Style.STROKE
                                    strokeWidth = 1f
                                }
                                canvas.drawRect(x, y, x + w, y + h, strokePaint)
                            }
                        }

                        if (shape is XSLFTextShape) {
                            val text = shape.text ?: ""
                            if (text.isNotBlank()) {
                                val isTitle = shape.isPlaceholder && (shape.textType == Placeholder.TITLE || shape.textType == Placeholder.CENTERED_TITLE)
                                val textPaint = Paint().apply {
                                    color = android.graphics.Color.BLACK
                                    textSize = if (isTitle) 22f else 14f
                                    isAntiAlias = true
                                    isFakeBoldText = isTitle
                                }
                                
                                val lines = text.split("\n")
                                var curY = y + textPaint.textSize + 4f
                                for (line in lines) {
                                    canvas.drawText(line, x + 8f, curY, textPaint)
                                    curY += textPaint.textSize * 1.3f
                                }
                            }
                        }
                    }

                    // Convert Bitmap directly to PDImageXObject using LosslessFactory
                    val pdImage = LosslessFactory.createFromImage(pdf, bitmap)
                    contentStream?.drawImage(pdImage, 0f, 0f, pageBounds.width, pageBounds.height)
                    bitmap.recycle()
                } else {
                    // 2. Clean Text Reflow Mode
                    var slideTitle = ""
                    val bodyBlocks = mutableListOf<String>()

                    for (shape in slide.shapes) {
                        if (shape is XSLFTextShape) {
                            val text = shape.text ?: ""
                            if (text.isNotBlank()) {
                                if (shape.isPlaceholder && (shape.textType == Placeholder.TITLE || shape.textType == Placeholder.CENTERED_TITLE)) {
                                    slideTitle = text
                                } else {
                                    bodyBlocks.add(text)
                                }
                            }
                        }
                    }

                    if (slideTitle.isBlank()) {
                        slideTitle = "Slide ${slideIndex + 1}"
                    }

                    var yPosition = pageBounds.height - margin

                    // Draw Title
                    val sanitizedTitle = sanitizeText(slideTitle)
                    contentStream?.beginText()
                    contentStream?.setFont(fontBold, fontSizeTitle)
                    contentStream?.newLineAtOffset(margin, yPosition)
                    contentStream?.showText(sanitizedTitle)
                    contentStream?.endText()
                    
                    yPosition -= leadingTitle

                    // Draw a visual separator line
                    contentStream?.setStrokingColor(180, 180, 180)
                    contentStream?.setLineWidth(1f)
                    contentStream?.moveTo(margin, yPosition + 6f)
                    contentStream?.lineTo(pageBounds.width - margin, yPosition + 6f)
                    contentStream?.stroke()
                    
                    yPosition -= 12f

                    // Draw body text blocks
                    for (block in bodyBlocks) {
                        val lines = wrapText(block, fontNormal, fontSizeBody, printableWidth)
                        for (line in lines) {
                            val sanitizedLine = sanitizeText(line)

                            if (yPosition - leadingBody < margin) {
                                // Add a sub-page if slide text overflows
                                contentStream?.close()
                                val nextSubPage = PDPage(pageBounds)
                                pdf.addPage(nextSubPage)
                                contentStream = PDPageContentStream(pdf, nextSubPage)
                                yPosition = pageBounds.height - margin
                            }

                            contentStream?.beginText()
                            contentStream?.setFont(fontNormal, fontSizeBody)
                            contentStream?.newLineAtOffset(margin, yPosition)
                            contentStream?.showText("• $sanitizedLine")
                            contentStream?.endText()

                            yPosition -= leadingBody
                        }
                        yPosition -= 8f // Spacing between different text shapes
                    }
                }

                contentStream?.close()
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
                ppt?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                pptxStream?.close()
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
                val width = (font.getStringWidth(cleanTestLine) / 1000f * fontSize)
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
            var width = (font.getStringWidth(text) / 1000f * fontSize)
            if (width <= maxWidth) return text

            var truncated = text
            while (truncated.isNotEmpty() && width > maxWidth) {
                truncated = truncated.dropLast(1)
                width = (font.getStringWidth("$truncated...") / 1000f * fontSize)
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
