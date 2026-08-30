package com.karnadigital.omnisuite.core.engine.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first office document format (Word, Excel) to PDF conversion engine.
 * Synthesizes formatted text blocks and landscape cell tables into standard paginated PDFs.
 * Runs fully on Dispatchers.IO background contexts with aggressive resource management.
 */
@Singleton
class OfficeConverter @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {

    /**
     * Converts a DOCX Word file paragraph-by-paragraph to an A4 PDF with dynamic line-wrapping and pagination.
     */
    suspend fun convertDocxToPdf(docxFile: File, pdfFile: File) = withContext(Dispatchers.IO) {
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

            for (bodyElement in docx.bodyElements) {
                if (bodyElement is org.apache.poi.xwpf.usermodel.XWPFTable) {
                    for (row in bodyElement.rows) {
                        val cells = row.tableCells
                        val cellCount = cells.size
                        if (cellCount == 0) continue
                        val colWidth = printableWidth / cellCount.toFloat()

                        // Store lines of text for each cell
                        class CellLine(val text: String, val font: PDType1Font, val fontSize: Float, val leading: Float, val colorHex: String?)
                        val cellLinesList = mutableListOf<List<CellLine>>()
                        var maxCellHeight = 0f

                        for (cell in cells) {
                            val lines = mutableListOf<CellLine>()
                            for (para in cell.paragraphs) {
                                val isHeading = para.styleID?.lowercase()?.contains("heading") == true ||
                                        para.runs.firstOrNull()?.fontSize ?: 0 > 14

                                for (run in para.runs) {
                                    val font = when {
                                        run.isBold && run.isItalic -> PDType1Font.HELVETICA_BOLD_OBLIQUE
                                        run.isBold || isHeading -> PDType1Font.HELVETICA_BOLD
                                        run.isItalic -> PDType1Font.HELVETICA_OBLIQUE
                                        else -> PDType1Font.HELVETICA
                                    }
                                    val fontSizeHalfPoints = run.fontSize
                                    val fontSize = if (fontSizeHalfPoints > 0) (fontSizeHalfPoints.toFloat()) else (if (isHeading) fontSizeHeading else fontSizeNormal)
                                    val leading = fontSize * 1.4f
                                    val runText = run.getText(0) ?: ""
                                    if (runText.isNotEmpty()) {
                                        val wrapped = wrapText(runText, font, fontSize, colWidth - 10f)
                                        for (line in wrapped) {
                                            lines.add(CellLine(line, font, fontSize, leading, run.color))
                                        }
                                    }
                                }
                            }
                            val cellHeight = lines.sumOf { it.leading.toDouble() }.toFloat()
                            if (cellHeight > maxCellHeight) {
                                maxCellHeight = cellHeight
                            }
                            cellLinesList.add(lines)
                        }

                        // Check page bound break for the entire row height
                        if (yPosition - maxCellHeight < margin) {
                            contentStream?.close()
                            currentPage = PDPage(pageBounds)
                            pdf.addPage(currentPage)
                            contentStream = PDPageContentStream(pdf, currentPage)
                            yPosition = pageBounds.height - margin
                        }

                        val startY = yPosition
                        var rowBottomY = startY - maxCellHeight
                        if (maxCellHeight == 0f) {
                            rowBottomY = startY - 15f
                        }

                        for (cellIdx in 0 until cellCount) {
                            val cellX = margin + (cellIdx.toFloat() * colWidth)
                            val lines = cellLinesList[cellIdx]
                            var currentCellY = startY

                            for (line in lines) {
                                currentCellY -= line.leading
                                val sanitizedLine = sanitizeText(line.text)
                                contentStream?.beginText()
                                val colorHex = line.colorHex
                                if (colorHex != null && colorHex.length == 6) {
                                    try {
                                        val r = colorHex.substring(0, 2).toInt(16)
                                        val g = colorHex.substring(2, 4).toInt(16)
                                        val b = colorHex.substring(4, 6).toInt(16)
                                        contentStream?.setNonStrokingColor(r, g, b)
                                    } catch (e: Exception) {
                                        contentStream?.setNonStrokingColor(0, 0, 0)
                                    }
                                } else {
                                    contentStream?.setNonStrokingColor(0, 0, 0)
                                }
                                contentStream?.setFont(line.font, line.fontSize)
                                contentStream?.newLineAtOffset(cellX + 5f, currentCellY)
                                contentStream?.showText(sanitizedLine)
                                contentStream?.endText()
                            }

                            // Draw borders for this cell
                            contentStream?.setStrokingColor(200, 200, 200)
                            contentStream?.setLineWidth(0.5f)
                            contentStream?.moveTo(cellX, startY)
                            contentStream?.lineTo(cellX + colWidth, startY)
                            contentStream?.lineTo(cellX + colWidth, rowBottomY)
                            contentStream?.lineTo(cellX, rowBottomY)
                            contentStream?.lineTo(cellX, startY)
                            contentStream?.stroke()
                        }

                        yPosition = rowBottomY
                    }
                }

                if (bodyElement is org.apache.poi.xwpf.usermodel.XWPFParagraph) {
                    val paragraph = bodyElement
                    val isHeading = paragraph.styleID?.lowercase()?.contains("heading") == true ||
                            paragraph.runs.firstOrNull()?.fontSize ?: 0 > 14
                    
                    var xCursor = margin

                    // Check if paragraph needs a page break before starting if yPosition is too low
                    if (yPosition - 15f < margin) {
                        contentStream?.close()
                        currentPage = PDPage(pageBounds)
                        pdf.addPage(currentPage)
                        contentStream = PDPageContentStream(pdf, currentPage)
                        yPosition = pageBounds.height - margin
                    }

                    // Check if paragraph has text or images
                    val hasTextOrImage = paragraph.runs.any { (it.getText(0) ?: "").isNotEmpty() || it.embeddedPictures.isNotEmpty() }
                    if (!hasTextOrImage) {
                        continue
                    }

                    for (run in paragraph.runs) {
                        // Check for images
                        val pictures = run.embeddedPictures
                        if (pictures.isNotEmpty()) {
                            for (pic in pictures) {
                                try {
                                    val picData = pic.pictureData.data
                                    val bitmap = BitmapFactory.decodeByteArray(picData, 0, picData.size)
                                    if (bitmap != null) {
                                        val originalWidth = bitmap.width.toFloat()
                                        val originalHeight = bitmap.height.toFloat()
                                        val targetWidth = Math.min(printableWidth, originalWidth)
                                        val targetHeight = (targetWidth / originalWidth) * originalHeight

                                        if (xCursor > margin) {
                                            yPosition -= (if (isHeading) fontSizeHeading else fontSizeNormal) * 1.4f
                                            xCursor = margin
                                        }

                                        if (yPosition - targetHeight < margin) {
                                            contentStream?.close()
                                            currentPage = PDPage(pageBounds)
                                            pdf.addPage(currentPage)
                                            contentStream = PDPageContentStream(pdf, currentPage)
                                            yPosition = pageBounds.height - margin
                                        }

                                        val pdImage = LosslessFactory.createFromImage(pdf, bitmap)
                                        contentStream?.drawImage(pdImage, margin, yPosition - targetHeight, targetWidth, targetHeight)
                                        yPosition -= targetHeight
                                        bitmap.recycle()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }

                        // Check for text
                        val runText = run.getText(0) ?: ""
                        if (runText.isNotEmpty()) {
                            val fontSizeHalfPoints = run.fontSize
                            val actualFontSize = if (fontSizeHalfPoints > 0) (fontSizeHalfPoints.toFloat()) else (if (isHeading) fontSizeHeading else fontSizeNormal)
                            val leading = actualFontSize * 1.4f
                            val font = when {
                                run.isBold && run.isItalic -> PDType1Font.HELVETICA_BOLD_OBLIQUE
                                run.isBold -> PDType1Font.HELVETICA_BOLD
                                run.isItalic -> PDType1Font.HELVETICA_OBLIQUE
                                else -> PDType1Font.HELVETICA
                            }

                            // Split runText into words
                            val words = runText.split(Regex("(?<=\\s)|(?=\\s)"))
                            for (word in words) {
                                if (word.isEmpty()) continue
                                val sanitizedWord = sanitizeText(word)
                                val wordWidth = try {
                                    font.getStringWidth(sanitizedWord) / 1000f * actualFontSize
                                } catch (e: Exception) {
                                    0f
                                }

                                if (xCursor + wordWidth > pageBounds.width - margin) {
                                    yPosition -= leading
                                    xCursor = margin

                                    if (yPosition < margin) {
                                        contentStream?.close()
                                        currentPage = PDPage(pageBounds)
                                        pdf.addPage(currentPage)
                                        contentStream = PDPageContentStream(pdf, currentPage)
                                        yPosition = pageBounds.height - margin
                                    }
                                }

                                if (sanitizedWord.trim().isNotEmpty()) {
                                    contentStream?.beginText()
                                    val colorHex = run.color
                                    if (colorHex != null && colorHex.length == 6) {
                                        try {
                                            val r = colorHex.substring(0, 2).toInt(16)
                                            val g = colorHex.substring(2, 4).toInt(16)
                                            val b = colorHex.substring(4, 6).toInt(16)
                                            contentStream?.setNonStrokingColor(r, g, b)
                                        } catch (e: Exception) {
                                            contentStream?.setNonStrokingColor(0, 0, 0)
                                        }
                                    } else {
                                        contentStream?.setNonStrokingColor(0, 0, 0)
                                    }
                                    contentStream?.setFont(font, actualFontSize)
                                    contentStream?.newLineAtOffset(xCursor, yPosition)
                                    contentStream?.showText(sanitizedWord)
                                    contentStream?.endText()
                                }
                                xCursor += wordWidth
                            }
                        }
                    }

                    val lastLeading = (if (isHeading) fontSizeHeading else fontSizeNormal) * 1.4f
                    yPosition -= (lastLeading + 6f)
                }
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
    suspend fun convertXlsxToPdf(xlsxFile: File, pdfFile: File) = withContext(Dispatchers.IO) {
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
     * Renders a PowerPoint PPTX file to high-resolution bitmaps for mobile preview without WebView.
     * Returns a list of Bitmaps, one per slide.
     */
    suspend fun renderPptxToBitmaps(pptxFile: File, targetWidth: Int = 1440): List<Bitmap> = withContext(Dispatchers.IO) {
        var pptxStream: FileInputStream? = null
        var ppt: XMLSlideShow? = null
        val bitmaps = mutableListOf<Bitmap>()

        try {
            pptxStream = FileInputStream(pptxFile)
            ppt = XMLSlideShow(pptxStream)

            val slideDimEmu = getSlideDimensionsEmu(ppt)
            val slideWidthEmu = slideDimEmu.first
            val slideHeightEmu = slideDimEmu.second

            // Calculate target height maintaining aspect ratio
            val targetHeight = if (slideWidthEmu > 0) (targetWidth * slideHeightEmu / slideWidthEmu).toInt() else (targetWidth * 9 / 16)

            for (slide in ppt.slides) {
                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)

                // Draw slide background
                val bgColor = getSlideBgColor(slide)
                if (bgColor != null) {
                    try {
                        canvas.drawColor(android.graphics.Color.parseColor(bgColor))
                    } catch (e: Exception) {
                        canvas.drawColor(android.graphics.Color.WHITE)
                    }
                }

                // Flatten all shapes (including group children)
                val allSlideShapes = mutableListOf<org.apache.poi.xslf.usermodel.XSLFShape>()
                fun collectBitmapShapes(shapes: List<org.apache.poi.xslf.usermodel.XSLFShape>) {
                    for (s in shapes) {
                        if (s is org.apache.poi.xslf.usermodel.XSLFGroupShape) {
                            try { collectBitmapShapes(s.shapes) } catch (_: Throwable) { }
                        } else {
                            allSlideShapes.add(s)
                        }
                    }
                }
                try { collectBitmapShapes(slide.shapes) } catch (_: Throwable) { }

                // Draw all shapes — check PictureShape / blipFill FIRST
                for (shape in allSlideShapes) {
                    val normBounds = getShapeNormalizedBounds(shape, slide, slideWidthEmu, slideHeightEmu)

                    // Check if this shape is a picture (XSLFPictureShape or shape with blipFill)
                    var dataBytes: ByteArray? = null
                    if (shape is org.apache.poi.xslf.usermodel.XSLFPictureShape) {
                        try { dataBytes = shape.pictureData?.data } catch (_: Throwable) { }
                        if (dataBytes == null) {
                            try {
                                val pd = shape.javaClass.getMethod("getPictureData").invoke(shape)
                                if (pd != null) dataBytes = pd.javaClass.getMethod("getData").invoke(pd) as? ByteArray
                            } catch (_: Throwable) { }
                        }
                    }
                    if (dataBytes == null) {
                        try {
                            val xml = try { shape.javaClass.getMethod("getXmlObject").invoke(shape) } catch (_: Throwable) { null }
                            if (xml != null) {
                                val xmlStr = xml.toString()
                                val match = Regex("""(?:embed|link)=["'](rId\d+)["']""").find(xmlStr)
                                if (match != null) {
                                    val rId = match.groupValues[1]
                                    val rel = slide.packagePart?.getRelationship(rId)
                                    if (rel != null) {
                                        val part = slide.packagePart?.getRelatedPart(rel) ?: slide.packagePart?.getPackage()?.getPart(rel)
                                        dataBytes = part?.inputStream?.use { stream -> stream.readBytes() }
                                    }
                                }
                            }
                        } catch (_: Throwable) { }
                    }

                    if (dataBytes != null && dataBytes.isNotEmpty()) {
                        try {
                            val bmp = BitmapFactory.decodeByteArray(dataBytes, 0, dataBytes.size)
                            if (bmp != null) {
                                val fb = normBounds ?: floatArrayOf(0.05f, 0.3f, 0.6f, 0.4f)
                                val destRect = android.graphics.Rect(
                                    (fb[0] * targetWidth).toInt(), (fb[1] * targetHeight).toInt(),
                                    ((fb[0] + fb[2]) * targetWidth).toInt(), ((fb[1] + fb[3]) * targetHeight).toInt()
                                )
                                canvas.drawBitmap(bmp, null, destRect, null)
                                bmp.recycle()
                            }
                        } catch (_: Throwable) { }
                        continue
                    }

                    if (normBounds == null) continue
                    val px = normBounds[0] * targetWidth.toFloat()
                    val py = normBounds[1] * targetHeight.toFloat()
                    val pw = normBounds[2] * targetWidth.toFloat()
                    val ph = normBounds[3] * targetHeight.toFloat()

                    if (shape is XSLFTextShape) {
                        val paragraphs = try { shape.textParagraphs } catch (t: Throwable) { emptyList() }
                        val text = try { shape.text ?: "" } catch (t: Throwable) { "" }
                        if (paragraphs.isNotEmpty()) {
                            val isTitle = try {
                                shape.isPlaceholder && (shape.textType == Placeholder.TITLE || shape.textType == Placeholder.CENTERED_TITLE)
                            } catch (_: Throwable) { false }
                            val textColor = getTextColor(shape)
                            val textPaint = Paint().apply {
                                color = textColor
                                textSize = if (isTitle) (28f * (targetWidth / 960f)) else (16f * (targetWidth / 960f))
                                isAntiAlias = true
                                isFakeBoldText = isTitle
                            }

                            var curY = py + textPaint.textSize + 4f
                            for (p in paragraphs) {
                                val pText = try {
                                    p.textRuns.joinToString("") { it.rawText ?: "" }
                                } catch (t: Throwable) { "" }
                                if (pText.isNotBlank()) {
                                    val bulletPrefix = if (p.indentLevel > 0 || (!isTitle && paragraphs.size > 1)) "• " else ""
                                    val fullLine = bulletPrefix + pText.trim()
                                    val indentOffset = (p.indentLevel * 14f * (targetWidth / 960f))
                                    val lines = wrapTextForCanvas(fullLine, textPaint, (pw - 12f - indentOffset).coerceAtLeast(50f))
                                    for (line in lines) {
                                        if (curY < py + ph - 4f) {
                                            canvas.drawText(line, px + 6f + indentOffset, curY, textPaint)
                                            curY += textPaint.textSize * 1.3f
                                        }
                                    }
                                    curY += 3f
                                }
                            }
                        } else if (text.isNotBlank()) {
                            val isTitle = try {
                                shape.isPlaceholder && (shape.textType == Placeholder.TITLE || shape.textType == Placeholder.CENTERED_TITLE)
                            } catch (_: Throwable) { false }
                            val textPaint = Paint().apply {
                                color = getTextColor(shape)
                                textSize = if (isTitle) (28f * (targetWidth / 960f)) else (16f * (targetWidth / 960f))
                                isAntiAlias = true
                                isFakeBoldText = isTitle
                            }
                            val lines = wrapTextForCanvas(text.trim(), textPaint, pw - 12f)
                            var curY = py + textPaint.textSize + 4f
                            for (line in lines) {
                                if (curY < py + ph - 4f) {
                                    canvas.drawText(line, px + 6f, curY, textPaint)
                                    curY += textPaint.textSize * 1.3f
                                }
                            }
                        }
                    } else if (shape is XSLFSimpleShape) {
                        val fillColor = getShapeFillColor(shape)
                        if (fillColor != null) {
                            val fillPaint = Paint().apply { color = fillColor; style = Paint.Style.FILL }
                            canvas.drawRect(px, py, px + pw, py + ph, fillPaint)
                        }
                        val lineColor = getShapeLineColor(shape)
                        if (lineColor != null) {
                            val strokePaint = Paint().apply { color = lineColor; style = Paint.Style.STROKE; strokeWidth = 2f }
                            canvas.drawRect(px, py, px + pw, py + ph, strokePaint)
                        }
                    }
                }
                bitmaps.add(bitmap)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { ppt?.close() } catch (e: Exception) {}
            try { pptxStream?.close() } catch (e: Exception) {}
        }
        return@withContext bitmaps
    }

    /**
     * Extracts text color from a text shape.
     * Returns Android Color int.
     */
    private fun getTextColor(shape: XSLFTextShape): Int {
        return try {
            val paragraphs = shape.textParagraphs
            if (paragraphs.isNotEmpty()) {
                val runs = paragraphs[0].textRuns
                if (runs.isNotEmpty()) {
                    val run = runs[0]
                    if (run is org.apache.poi.xslf.usermodel.XSLFTextRun) {
                        // Try getting color via reflection on the XML run
                        val xmlRun = try {
                            run.javaClass.getMethod("getXmlObject").invoke(run)
                        } catch (t: Throwable) {
                            try {
                                val m = run.javaClass.getDeclaredMethod("fetchXmlObject")
                                m.isAccessible = true
                                m.invoke(run)
                            } catch (t2: Throwable) { null }
                        }
                        if (xmlRun != null) {
                            val rPr = try { xmlRun.javaClass.getMethod("getRPr").invoke(xmlRun) } catch (t: Throwable) { null }
                            if (rPr != null) {
                                val solidFill = try { rPr.javaClass.getMethod("getSolidFill").invoke(rPr) } catch (t: Throwable) { null }
                                if (solidFill != null) {
                                    val srgb = try { solidFill.javaClass.getMethod("getSrgbClr").invoke(solidFill) } catch (t: Throwable) { null }
                                    if (srgb != null) {
                                        val hexBytes = try { srgb.javaClass.getMethod("getVal").invoke(srgb) as? ByteArray } catch (t: Throwable) { null }
                                        if (hexBytes != null && hexBytes.size >= 3) {
                                            return android.graphics.Color.rgb(
                                                hexBytes[0].toInt() and 0xFF,
                                                hexBytes[1].toInt() and 0xFF,
                                                hexBytes[2].toInt() and 0xFF
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            android.graphics.Color.BLACK
        } catch (e: Exception) {
            android.graphics.Color.BLACK
        }
    }

    /**
     * Converts a PPTX presentation file slide-by-slide to A4 PDF.
     * Supports "image" mode (accurate slide shapes and text locations rendered to bitmaps)
     * and "text" mode (clean reflowed text and title elements).
     */
    suspend fun convertPptxToPdf(pptxFile: File, pdfFile: File, renderMode: String) = withContext(Dispatchers.IO) {
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

            // Get slide dimensions in EMUs for coordinate conversion
            val slideDimEmu = getSlideDimensionsEmu(ppt)
            val slideWidthEmu = slideDimEmu.first.toFloat()
            val slideHeightEmu = slideDimEmu.second.toFloat()

            for ((slideIndex, slide) in ppt.slides.withIndex()) {
                val currentPage = PDPage(pageBounds)
                pdf.addPage(currentPage)
                contentStream = PDPageContentStream(pdf, currentPage)

                if (renderMode.lowercase() == "image") {
                    // Render slide to bitmap with proper EMU-to-pixel coordinate conversion
                    val canvasWidth = 1440
                    val canvasHeight = 1080
                    val bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)

                    // Calculate scale factors: EMU -> pixels
                    val scaleX = canvasWidth.toFloat() / slideWidthEmu
                    val scaleY = canvasHeight.toFloat() / slideHeightEmu

                    // Extract slide content
                    val bodyBlocks = mutableListOf<String>()

                    // Draw slide background if present
                    val bgColor = getSlideBgColor(slide)
                    if (bgColor != null) {
                        canvas.drawColor(android.graphics.Color.parseColor(bgColor))
                    }

                    // Collect and draw all shapes
                    for (shape in slide.shapes) {
                        val bounds = getShapeBoundsEmu(shape)
                        if (bounds != null) {
                            // Convert EMU coordinates to canvas pixels
                            val px = bounds[0] * scaleX
                            val py = bounds[1] * scaleY
                            val pw = bounds[2] * scaleX
                            val ph = bounds[3] * scaleY

                            if (shape is XSLFSimpleShape) {
                                // Draw shape fill
                                val fillColor = getShapeFillColor(shape)
                                if (fillColor != null) {
                                    val fillPaint = Paint().apply {
                                        color = fillColor
                                        style = Paint.Style.FILL
                                    }
                                    canvas.drawRect(px, py, px + pw, py + ph, fillPaint)
                                }

                                // Draw shape border
                                val lineColor = getShapeLineColor(shape)
                                if (lineColor != null) {
                                    val strokePaint = Paint().apply {
                                        color = lineColor
                                        style = Paint.Style.STROKE
                                        strokeWidth = 2f
                                    }
                                    canvas.drawRect(px, py, px + pw, py + ph, strokePaint)
                                }
                            }

                            if (shape is XSLFTextShape) {
                                val text = shape.text ?: ""
                                if (text.isNotBlank()) {
                                    val isTitle = shape.isPlaceholder && (shape.textType == Placeholder.TITLE || shape.textType == Placeholder.CENTERED_TITLE)
                                    if (!isTitle) {
                                        bodyBlocks.add(text)
                                    }

                                    val textPaint = Paint().apply {
                                        color = android.graphics.Color.BLACK
                                        textSize = if (isTitle) 28f else 16f
                                        isAntiAlias = true
                                        isFakeBoldText = isTitle
                                    }

                                    // Draw text within shape bounds
                                    val lines = text.split("\n")
                                    var curY = py + textPaint.textSize + 8f
                                    for (line in lines) {
                                        if (curY < py + ph - 4f) {
                                            canvas.drawText(line, px + 8f, curY, textPaint)
                                            curY += textPaint.textSize * 1.4f
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Convert Bitmap to PDImageXObject and draw on PDF page
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
                        yPosition -= 8f
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
     * Gets slide dimensions in EMUs without using java.awt.
     */
    private fun getSlideDimensionsEmu(ppt: XMLSlideShow): Pair<Long, Long> {
        return try {
            val ctPresentation = try {
                ppt.javaClass.getMethod("getCTPresentation").invoke(ppt)
            } catch (t: Throwable) { null }
            if (ctPresentation != null) {
                val sldSz = ctPresentation.javaClass.getMethod("getSldSz").invoke(ctPresentation)
                if (sldSz != null) {
                    val cx = (sldSz.javaClass.getMethod("getCx").invoke(sldSz) as? Number)?.toLong() ?: 9144000L
                    val cy = (sldSz.javaClass.getMethod("getCy").invoke(sldSz) as? Number)?.toLong() ?: 5143500L
                    return Pair(cx, cy)
                }
            }
            Pair(9144000L, 5143500L)
        } catch (t: Throwable) {
            Pair(9144000L, 5143500L)
        }
    }

    /**
     * Extracts shape bounds in EMU units directly from XML, avoiding java.awt dependencies.
     */
    private fun getShapeBoundsEmu(shape: Any): FloatArray? {
        return try {
            val xmlObj = try {
                shape.javaClass.getMethod("getXmlObject").invoke(shape)
            } catch (t: Throwable) {
                try {
                    val method = shape.javaClass.getDeclaredMethod("fetchXmlObject")
                    method.isAccessible = true
                    method.invoke(shape)
                } catch (t2: Throwable) { null }
            } ?: return null

            val spPr = try {
                xmlObj.javaClass.getMethod("getSpPr").invoke(xmlObj)
            } catch (t: Throwable) { null } ?: return null

            val xfrm = try {
                spPr.javaClass.getMethod("getXfrm").invoke(spPr)
            } catch (t: Throwable) { null } ?: return null

            val off = try { xfrm.javaClass.getMethod("getOff").invoke(xfrm) } catch (t: Throwable) { null }
            val ext = try { xfrm.javaClass.getMethod("getExt").invoke(xfrm) } catch (t: Throwable) { null }

            val x = (try { off?.javaClass?.getMethod("getX")?.invoke(off) as? Number } catch (t: Throwable) { null })?.toFloat()
            val y = (try { off?.javaClass?.getMethod("getY")?.invoke(off) as? Number } catch (t: Throwable) { null })?.toFloat()
            val cx = (try { ext?.javaClass?.getMethod("getCx")?.invoke(ext) as? Number } catch (t: Throwable) { null })?.toFloat()
            val cy = (try { ext?.javaClass?.getMethod("getCy")?.invoke(ext) as? Number } catch (t: Throwable) { null })?.toFloat()

            if (x != null && y != null && cx != null && cy != null) {
                floatArrayOf(x, y, cx, cy)
            } else null
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Extracts normalized shape bounds (left, top, width, height: 0.0f..1.0f)
     * using getAnchor() reflection with fallback to XMLBeans without java.awt compile dependencies.
     */
    private fun extractLongValueOC(obj: Any?): Long? {
        if (obj == null) return null
        if (obj is Number) return obj.toLong()
        try {
            val longValMethod = obj.javaClass.getMethod("getLongValue")
            val v = longValMethod.invoke(obj)
            if (v is Number) return v.toLong()
        } catch (_: Throwable) { }
        try {
            val str = obj.toString().trim()
            val num = str.toLongOrNull()
            if (num != null) return num
            val doubleNum = str.toDoubleOrNull()
            if (doubleNum != null) return doubleNum.toLong()
        } catch (_: Throwable) { }
        return null
    }

    private fun getShapeNormalizedBounds(
        shape: Any,
        slide: Any?,
        slideWidthEmu: Long,
        slideHeightEmu: Long
    ): FloatArray? {
        // Strategy 1: XMLBeans direct EMU extraction
        val directXml = getXmlShapeBoundsNormalized(shape, slideWidthEmu, slideHeightEmu)
        if (directXml != null) return directXml

        // Strategy 2: If placeholder, resolve from Slide Layout / Master
        if (slide is XSLFSlide && shape is XSLFShape) {
            try {
                val phDetails = try { shape.placeholderDetails } catch (_: Throwable) { null }
                val phType = phDetails?.placeholder
                if (phType != null) {
                    val layoutShapes = try { slide.slideLayout?.shapes } catch (_: Throwable) { emptyList() }
                    for (lShape in layoutShapes) {
                        if (lShape.placeholderDetails?.placeholder == phType) {
                            val lBounds = getXmlShapeBoundsNormalized(lShape, slideWidthEmu, slideHeightEmu)
                            if (lBounds != null) return lBounds
                        }
                    }
                    val masterShapes = try { slide.slideLayout?.slideMaster?.shapes } catch (_: Throwable) { emptyList() }
                    for (mShape in masterShapes) {
                        if (mShape.placeholderDetails?.placeholder == phType) {
                            val mBounds = getXmlShapeBoundsNormalized(mShape, slideWidthEmu, slideHeightEmu)
                            if (mBounds != null) return mBounds
                        }
                    }
                }
            } catch (_: Throwable) { }
        }

        // Strategy 3: Try getAnchor() via reflection
        try {
            val anchor = shape.javaClass.getMethod("getAnchor").invoke(shape)
            if (anchor != null) {
                val x = (anchor.javaClass.getMethod("getX").invoke(anchor) as? Number)?.toDouble()
                val y = (anchor.javaClass.getMethod("getY").invoke(anchor) as? Number)?.toDouble()
                val w = (anchor.javaClass.getMethod("getWidth").invoke(anchor) as? Number)?.toDouble()
                val h = (anchor.javaClass.getMethod("getHeight").invoke(anchor) as? Number)?.toDouble()

                val slideWPt = if (slideWidthEmu > 0) slideWidthEmu / 12700.0 else 720.0
                val slideHPt = if (slideHeightEmu > 0) slideHeightEmu / 12700.0 else 540.0

                if (x != null && y != null && w != null && h != null && w > 0 && h > 0) {
                    return floatArrayOf(
                        (x / slideWPt).toFloat().coerceIn(0f, 1f),
                        (y / slideHPt).toFloat().coerceIn(0f, 1f),
                        (w / slideWPt).toFloat().coerceIn(0.01f, 1f),
                        (h / slideHPt).toFloat().coerceIn(0.01f, 1f)
                    )
                }
            }
        } catch (_: Throwable) { }

        return null
    }

    private fun getXmlShapeBoundsNormalized(
        shape: Any,
        slideWidthEmu: Long,
        slideHeightEmu: Long
    ): FloatArray? {
        if (slideWidthEmu <= 0 || slideHeightEmu <= 0) return null
        try {
            val xmlObj = try {
                shape.javaClass.getMethod("getXmlObject").invoke(shape)
            } catch (t: Throwable) {
                try {
                    val method = shape.javaClass.getDeclaredMethod("fetchXmlObject")
                    method.isAccessible = true
                    method.invoke(shape)
                } catch (t2: Throwable) { null }
            } ?: return null

            var xfrm: Any? = null
            xfrm = tryGetXfrmOC(xmlObj, "getSpPr")
            if (xfrm == null) xfrm = tryGetXfrmOC(xmlObj, "getGrpSpPr")
            if (xfrm == null) {
                for (methodName in listOf("getCxnSpPr", "getNvSpPr", "getNvPicPr", "getNvCxnSpPr")) {
                    xfrm = tryGetXfrmOC(xmlObj, methodName)
                    if (xfrm != null) break
                }
            }
            if (xfrm == null) {
                xfrm = try { xmlObj.javaClass.getMethod("getXfrm").invoke(xmlObj) } catch (_: Throwable) { null }
            }

            if (xfrm == null) return null

            val off = try { xfrm.javaClass.getMethod("getOff").invoke(xfrm) } catch (_: Throwable) { null }
            val ext = try { xfrm.javaClass.getMethod("getExt").invoke(xfrm) } catch (_: Throwable) { null }

            val rawX = try { off?.javaClass?.getMethod("getX")?.invoke(off) } catch (_: Throwable) { null }
            val rawY = try { off?.javaClass?.getMethod("getY")?.invoke(off) } catch (_: Throwable) { null }
            val rawCx = try { ext?.javaClass?.getMethod("getCx")?.invoke(ext) } catch (_: Throwable) { null }
            val rawCy = try { ext?.javaClass?.getMethod("getCy")?.invoke(ext) } catch (_: Throwable) { null }

            val x = extractLongValueOC(rawX)
            val y = extractLongValueOC(rawY)
            val cx = extractLongValueOC(rawCx)
            val cy = extractLongValueOC(rawCy)

            if (x != null && y != null && cx != null && cy != null && cx > 0 && cy > 0) {
                return floatArrayOf(
                    (x.toFloat() / slideWidthEmu.toFloat()).coerceIn(0f, 1f),
                    (y.toFloat() / slideHeightEmu.toFloat()).coerceIn(0f, 1f),
                    (cx.toFloat() / slideWidthEmu.toFloat()).coerceIn(0.01f, 1f),
                    (cy.toFloat() / slideHeightEmu.toFloat()).coerceIn(0.01f, 1f)
                )
            }
        } catch (_: Throwable) { }
        return null
    }

    /** Helper: tries parentObj.getMethodName().getXfrm() via reflection */
    private fun tryGetXfrmOC(parentObj: Any, prMethodName: String): Any? {
        return try {
            val pr = parentObj.javaClass.getMethod(prMethodName).invoke(parentObj) ?: return null
            pr.javaClass.getMethod("getXfrm").invoke(pr)
        } catch (_: Throwable) { null }
    }

    /**
     * Extracts shape fill color as Android Color int, or null if none.
     */
    private fun getShapeFillColor(shape: XSLFSimpleShape): Int? {
        return try {
            val xmlObj = try {
                shape.javaClass.getMethod("getXmlObject").invoke(shape)
            } catch (t: Throwable) { null } ?: return null

            val spPr = try {
                xmlObj.javaClass.getMethod("getSpPr").invoke(xmlObj)
            } catch (t: Throwable) { null } ?: return null

            val solidFill = try {
                spPr.javaClass.getMethod("getSolidFill").invoke(spPr)
            } catch (t: Throwable) { null } ?: return null

            val srgbClr = try {
                solidFill.javaClass.getMethod("getSrgbClr").invoke(solidFill)
            } catch (t: Throwable) { null } ?: return null

            val hexBytes = try {
                srgbClr.javaClass.getMethod("getVal").invoke(srgbClr) as? ByteArray
            } catch (t: Throwable) { null } ?: return null

            if (hexBytes.size >= 3) {
                android.graphics.Color.rgb(hexBytes[0].toInt() and 0xFF, hexBytes[1].toInt() and 0xFF, hexBytes[2].toInt() and 0xFF)
            } else null
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Extracts shape line/border color as Android Color int, or null if none.
     */
    private fun getShapeLineColor(shape: XSLFSimpleShape): Int? {
        return try {
            val xmlObj = try {
                shape.javaClass.getMethod("getXmlObject").invoke(shape)
            } catch (t: Throwable) { null } ?: return null

            val spPr = try {
                xmlObj.javaClass.getMethod("getSpPr").invoke(xmlObj)
            } catch (t: Throwable) { null } ?: return null

            val ln = try {
                spPr.javaClass.getMethod("getLn").invoke(spPr)
            } catch (t: Throwable) { null } ?: return null

            val solidFill = try {
                ln.javaClass.getMethod("getSolidFill").invoke(ln)
            } catch (t: Throwable) { null } ?: return null

            val srgbClr = try {
                solidFill.javaClass.getMethod("getSrgbClr").invoke(solidFill)
            } catch (t: Throwable) { null } ?: return null

            val hexBytes = try {
                srgbClr.javaClass.getMethod("getVal").invoke(srgbClr) as? ByteArray
            } catch (t: Throwable) { null } ?: return null

            if (hexBytes.size >= 3) {
                android.graphics.Color.rgb(hexBytes[0].toInt() and 0xFF, hexBytes[1].toInt() and 0xFF, hexBytes[2].toInt() and 0xFF)
            } else null
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Extracts slide background color as hex string, or null if none.
     */
    private fun getSlideBgColor(slide: org.apache.poi.sl.usermodel.Slide<*, *>): String? {
        if (slide !is org.apache.poi.xslf.usermodel.XSLFSlide) return null
        return try {
            val ctSlide = try {
                slide.javaClass.getMethod("getXmlObject").invoke(slide)
            } catch (t: Throwable) { null } ?: return null

            val cSld = try {
                ctSlide.javaClass.getMethod("getCSld").invoke(ctSlide)
            } catch (t: Throwable) { null } ?: return null

            val bg = try {
                cSld.javaClass.getMethod("getBg").invoke(cSld)
            } catch (t: Throwable) { null } ?: return null

            val bgPr = try {
                bg.javaClass.getMethod("getBgPr").invoke(bg)
            } catch (t: Throwable) { null } ?: return null

            val solidFill = try {
                bgPr.javaClass.getMethod("getSolidFill").invoke(bgPr)
            } catch (t: Throwable) { null } ?: return null

            val srgbClr = try {
                solidFill.javaClass.getMethod("getSrgbClr").invoke(solidFill)
            } catch (t: Throwable) { null } ?: return null

            val hexBytes = try {
                srgbClr.javaClass.getMethod("getVal").invoke(srgbClr) as? ByteArray
            } catch (t: Throwable) { null } ?: return null

            val hex = hexBytes.joinToString("") { String.format("%02X", it) }
            if (hex.isNotBlank()) "#$hex" else null
        } catch (t: Throwable) {
            null
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

    private fun wrapTextForCanvas(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            if (word.isEmpty()) continue
            val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
            val width = paint.measureText(testLine)
            if (width <= maxWidth) {
                currentLine.append(if (currentLine.isEmpty()) word else " $word")
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                }
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines
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
