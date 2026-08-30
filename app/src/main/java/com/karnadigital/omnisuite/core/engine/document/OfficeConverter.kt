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
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.poi.xslf.usermodel.XSLFShape
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
    // 1080px covers current phone displays while keeping a 13-slide deck below
    // the memory footprint that caused preview surfaces to be evicted/blank.
    suspend fun renderPptxToSlideImages(pptxFile: File, targetWidth: Int = 1080): List<String?> = withContext(Dispatchers.IO) {
        var pptxStream: FileInputStream? = null
        var ppt: XMLSlideShow? = null
        val renderedPaths = mutableListOf<String?>()
        val renderDirectory = File(context.cacheDir, "pptx_slide_renders").apply { mkdirs() }
        val renderPrefix = "${pptxFile.nameWithoutExtension}_${pptxFile.length()}_${pptxFile.lastModified()}"

        try {
            pptxStream = FileInputStream(pptxFile)
            ppt = XMLSlideShow(pptxStream)

            val (slideWidthEmu, slideHeightEmu) = PptxShapeExtractor.getSlideDimensionsEmu(ppt)

            // Calculate target height maintaining aspect ratio
            val targetHeight = if (slideWidthEmu > 0) (targetWidth * slideHeightEmu / slideWidthEmu).toInt() else (targetWidth * 9 / 16)

            for ((slideIndex, slide) in ppt.slides.withIndex()) {
                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)

                // Draw slide background
                val bgColor = PptxShapeExtractor.getSlideBgColorHex(slide)
                if (bgColor != null) {
                    try {
                        canvas.drawColor(android.graphics.Color.parseColor(bgColor))
                    } catch (_: Throwable) {
                        canvas.drawColor(android.graphics.Color.WHITE)
                    }
                }

                // Collect shapes with GroupTransform tracking
                data class SlideCollectedShape(
                    val shape: org.apache.poi.sl.usermodel.Shape<*, *>,
                    val ancestry: List<PptxShapeExtractor.GroupTransform>
                )
                val allSlideShapes = mutableListOf<SlideCollectedShape>()
                fun collectBitmapShapes(
                    shapes: List<org.apache.poi.sl.usermodel.Shape<*, *>>,
                    currentAncestry: List<PptxShapeExtractor.GroupTransform>
                ) {
                    for (s in shapes) {
                        if (s is org.apache.poi.sl.usermodel.GroupShape<*, *>) {
                            val gt = PptxShapeExtractor.extractGroupTransform(s)
                            val nextAncestry = if (gt != null) currentAncestry + gt else currentAncestry
                            val nested = try { s.shapes } catch (_: Throwable) { emptyList() }
                            collectBitmapShapes(nested, nextAncestry)
                        } else {
                            allSlideShapes.add(SlideCollectedShape(s, currentAncestry))
                        }
                    }
                }
                try { collectBitmapShapes(slide.shapes, emptyList()) } catch (_: Throwable) { }

                // Draw shapes
                for (item in allSlideShapes) {
                    val shape = item.shape
                    val ancestry = item.ancestry
                    val normBounds = PptxShapeExtractor.getShapeNormalizedBounds(shape, slide, slideWidthEmu, slideHeightEmu, ancestry)

                    // 1. Picture extraction (PictureShape, blipFill on AutoShape/Freeform, graphicFrame OLE/SmartArt)
                    val picPair = PptxShapeExtractor.extractPictureDataFromShape(shape, slide)
                    if (picPair != null && picPair.first.isNotEmpty()) {
                        try {
                            val bmp = BitmapFactory.decodeByteArray(picPair.first, 0, picPair.first.size)
                            if (bmp != null) {
                                val fb = normBounds ?: floatArrayOf(0.05f, 0.3f, 0.6f, 0.4f)
                                val destRect = android.graphics.Rect(
                                    (fb[0] * targetWidth).toInt(),
                                    (fb[1] * targetHeight).toInt(),
                                    ((fb[0] + fb[2]) * targetWidth).toInt(),
                                    ((fb[1] + fb[3]) * targetHeight).toInt()
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

                    // 2. Text Shape rendering
                    if (shape is org.apache.poi.sl.usermodel.TextShape<*, *>) {
                        val shapeText = try { shape.text ?: "" } catch (_: Throwable) { "" }
                        val isTitle = try {
                            shape.placeholder == Placeholder.TITLE || shape.placeholder == Placeholder.CENTERED_TITLE
                        } catch (_: Throwable) {
                            shape.shapeName.lowercase().contains("title")
                        }

                        val paragraphs = try { shape.textParagraphs } catch (_: Throwable) { emptyList() }
                        if (paragraphs.isNotEmpty()) {
                            canvas.save()
                            canvas.clipRect(px, py, px + pw, py + ph)
                            val defaultSize = if (isTitle) 28f else 16f
                            val textPaint = Paint().apply {
                                color = if (isTitle) android.graphics.Color.DKGRAY else android.graphics.Color.BLACK
                                isAntiAlias = true
                            }
                            var curY = py

                            for (p in paragraphs) {
                                val pRuns = try { p.textRuns } catch (_: Throwable) { emptyList() }
                                val pText = pRuns.joinToString("") { PptxShapeExtractor.getTextFromRun(it) }.trim()
                                if (pText.isNotBlank()) {
                                    val firstRun = pRuns.firstOrNull()
                                    val fontSizePt = try { firstRun?.fontSize?.toFloat() } catch (_: Throwable) { null } ?: defaultSize
                                    val runColorHex = firstRun?.let { PptxShapeExtractor.extractTextRunColorHex(it) }
                                    val runColor = runColorHex?.let {
                                        try { android.graphics.Color.parseColor(it) } catch (_: Throwable) { null }
                                    } ?: (if (isTitle) android.graphics.Color.DKGRAY else android.graphics.Color.BLACK)

                                    val isBold = try { firstRun?.isBold ?: isTitle } catch (_: Throwable) { isTitle }
                                    val isItalic = try { firstRun?.isItalic ?: false } catch (_: Throwable) { false }

                                    textPaint.apply {
                                        color = runColor
                                        textSize = (fontSizePt * targetWidth / 720f).coerceIn(10f, targetHeight * 0.22f)
                                        isFakeBoldText = isBold
                                        textSkewX = if (isItalic) -0.25f else 0f
                                    }

                                    if (curY == py) {
                                        curY += textPaint.textSize
                                    }

                                    val bulletPrefix = if (p.indentLevel > 0 || (!isTitle && paragraphs.size > 1 && shapeText.contains("\n"))) "• " else ""
                                    val fullLine = bulletPrefix + pText
                                    val indentOffset = p.indentLevel * textPaint.textSize * 1.2f
                                    val lines = wrapTextForCanvas(fullLine, textPaint, (pw - 12f - indentOffset).coerceAtLeast(50f))
                                    for (line in lines) {
                                        if (curY <= py + ph + textPaint.textSize) {
                                            canvas.drawText(line, px + 6f + indentOffset, curY, textPaint)
                                            curY += textPaint.textSize * 1.3f
                                        }
                                    }
                                    curY += 4f
                                }
                            }
                            canvas.restore()
                        } else if (shapeText.isNotBlank()) {
                            val cleanText = PptxShapeExtractor.cleanTextRunString(shapeText).trim()
                            if (cleanText.isNotBlank() &&
                                !cleanText.startsWith("org.apache.poi") &&
                                !cleanText.startsWith("org.apache.xmlbeans") &&
                                !(cleanText.startsWith("<") && cleanText.endsWith(">"))
                            ) {
                                val textPaint = Paint().apply {
                                    color = if (isTitle) android.graphics.Color.DKGRAY else android.graphics.Color.BLACK
                                    textSize = if (isTitle) (28f * (targetWidth / 720f)) else (16f * (targetWidth / 720f))
                                    isAntiAlias = true
                                    isFakeBoldText = isTitle
                                }
                                val lines = wrapTextForCanvas(cleanText, textPaint, (pw - 12f).coerceAtLeast(50f))
                                var curY = py + textPaint.textSize
                                for (line in lines) {
                                    if (curY <= py + ph + textPaint.textSize) {
                                        canvas.drawText(line, px + 6f, curY, textPaint)
                                        curY += textPaint.textSize * 1.3f
                                    }
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
                val renderFile = File(renderDirectory, "${renderPrefix}_$slideIndex.png")
                val persisted = try {
                    FileOutputStream(renderFile).use { output ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                    }
                    renderFile.absolutePath
                } catch (_: Throwable) {
                    null
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
                renderedPaths.add(persisted)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { ppt?.close() } catch (e: Exception) {}
            try { pptxStream?.close() } catch (e: Exception) {}
        }
        return@withContext renderedPaths
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

            val (slideWidthEmu, slideHeightEmu) = PptxShapeExtractor.getSlideDimensionsEmu(ppt)

            for ((slideIndex, slide) in ppt.slides.withIndex()) {
                val currentPage = PDPage(pageBounds)
                pdf.addPage(currentPage)
                contentStream = PDPageContentStream(pdf, currentPage)

                if (renderMode.lowercase() == "image") {
                    val canvasWidth = 1440
                    val canvasHeight = if (slideWidthEmu > 0) (canvasWidth * slideHeightEmu / slideWidthEmu).toInt() else 1080
                    val bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)

                    val bgColor = PptxShapeExtractor.getSlideBgColorHex(slide)
                    if (bgColor != null) {
                        try {
                            canvas.drawColor(android.graphics.Color.parseColor(bgColor))
                        } catch (_: Throwable) {
                            canvas.drawColor(android.graphics.Color.WHITE)
                        }
                    }

                    // Draw slide background picture if present
                    val bgPicPair = PptxShapeExtractor.extractSlideBackgroundPicture(slide)
                    if (bgPicPair != null && bgPicPair.first.isNotEmpty()) {
                        try {
                            val bgBmp = BitmapFactory.decodeByteArray(bgPicPair.first, 0, bgPicPair.first.size)
                            if (bgBmp != null) {
                                val destRect = android.graphics.Rect(0, 0, canvasWidth, canvasHeight)
                                canvas.drawBitmap(bgBmp, null, destRect, null)
                                bgBmp.recycle()
                            }
                        } catch (_: Throwable) { }
                    }

                    for (shape in slide.shapes) {
                        val normBounds = PptxShapeExtractor.getShapeNormalizedBounds(shape, slide, slideWidthEmu, slideHeightEmu)
                        if (normBounds != null) {
                            val px = normBounds[0] * canvasWidth.toFloat()
                            val py = normBounds[1] * canvasHeight.toFloat()
                            val pw = normBounds[2] * canvasWidth.toFloat()
                            val ph = normBounds[3] * canvasHeight.toFloat()

                            val picPair = PptxShapeExtractor.extractPictureDataFromShape(shape, slide)
                            if (picPair != null && picPair.first.isNotEmpty()) {
                                try {
                                    val bmp = BitmapFactory.decodeByteArray(picPair.first, 0, picPair.first.size)
                                    if (bmp != null) {
                                        val destRect = android.graphics.Rect(px.toInt(), py.toInt(), (px + pw).toInt(), (py + ph).toInt())
                                        canvas.drawBitmap(bmp, null, destRect, null)
                                        bmp.recycle()
                                    }
                                } catch (_: Throwable) { }
                                continue
                            }

                            if (shape is XSLFSimpleShape) {
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

                            if (shape is XSLFTextShape) {
                                val text = PptxShapeExtractor.cleanTextRunString(shape.text ?: "").trim()
                                if (text.isNotBlank()) {
                                    val isTitle = shape.isPlaceholder && (shape.textType == Placeholder.TITLE || shape.textType == Placeholder.CENTERED_TITLE)
                                    val textPaint = Paint().apply {
                                        color = android.graphics.Color.BLACK
                                        textSize = if (isTitle) 28f else 16f
                                        isAntiAlias = true
                                        isFakeBoldText = isTitle
                                    }
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
     * Extracts shape fill color as Android Color int, or null if none.
     */
    private fun getShapeFillColor(shape: XSLFSimpleShape): Int? {
        return try {
            val xmlObj = PptxShapeExtractor.getXmlObjectReflection(shape) ?: return null
            val spPr = PptxShapeExtractor.invokeMethod(xmlObj, "getSpPr") ?: return null
            val solidFill = PptxShapeExtractor.invokeMethod(spPr, "getSolidFill") ?: return null
            val srgbClr = PptxShapeExtractor.invokeMethod(solidFill, "getSrgbClr") ?: return null
            val hexBytes = PptxShapeExtractor.invokeMethod(srgbClr, "getVal") as? ByteArray ?: return null
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
            val xmlObj = PptxShapeExtractor.getXmlObjectReflection(shape) ?: return null
            val spPr = PptxShapeExtractor.invokeMethod(xmlObj, "getSpPr") ?: return null
            val ln = PptxShapeExtractor.invokeMethod(spPr, "getLn") ?: return null
            val solidFill = PptxShapeExtractor.invokeMethod(ln, "getSolidFill") ?: return null
            val srgbClr = PptxShapeExtractor.invokeMethod(solidFill, "getSrgbClr") ?: return null
            val hexBytes = PptxShapeExtractor.invokeMethod(srgbClr, "getVal") as? ByteArray ?: return null
            if (hexBytes.size >= 3) {
                android.graphics.Color.rgb(hexBytes[0].toInt() and 0xFF, hexBytes[1].toInt() and 0xFF, hexBytes[2].toInt() and 0xFF)
            } else null
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
