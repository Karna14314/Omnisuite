package com.karnadigital.omnisuite.core.engine.document

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.karnadigital.omnisuite.core.util.FileOutputManager
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.android.gms.tasks.Tasks
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.BreakType
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReverseOfficeConverter @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val fileOutputManager: FileOutputManager
) {

    suspend fun convertPdfToDocx(uri: Uri): Uri? = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                ?: return@withContext null
            val doc = PDDocument.load(inputStream)
            val docx = XWPFDocument()

            // 1. Standardize document-level A4 layout and 1-inch margins across all pages
            val sectPr = docx.document.body.sectPr ?: docx.document.body.addNewSectPr()
            val pgSz = sectPr.pgSz ?: sectPr.addNewPgSz()
            pgSz.w = java.math.BigInteger.valueOf(11906L) // Standard A4: 210mm = 11906 twips
            pgSz.h = java.math.BigInteger.valueOf(16838L) // Standard A4: 297mm = 16838 twips
            val pgMar = sectPr.pgMar ?: sectPr.addNewPgMar()
            pgMar.top = java.math.BigInteger.valueOf(1440L)    // 1 inch = 1440 twips
            pgMar.bottom = java.math.BigInteger.valueOf(1440L) // 1 inch = 1440 twips
            pgMar.left = java.math.BigInteger.valueOf(1440L)   // 1 inch = 1440 twips
            pgMar.right = java.math.BigInteger.valueOf(1440L)  // 1 inch = 1440 twips

            for (pageNum in 1..doc.numberOfPages) {
                val stripper = DocxPageTextStripper()
                stripper.startPage = pageNum
                stripper.endPage = pageNum
                stripper.getText(doc)
                var rawLines = stripper.lines

                // 1. OCR Fallback for Scanned / Image-Only Pages
                if (rawLines.sumOf { it.lineText.trim().length } < 30) {
                    try {
                        val renderer = PDFRenderer(doc)
                        val pageBmp = renderer.renderImageWithDPI(pageNum - 1, 150f)
                        if (pageBmp != null) {
                            val inputImg = InputImage.fromBitmap(pageBmp, 0)
                            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                            val task = recognizer.process(inputImg)
                            val visionText = Tasks.await(task)
                            val ocrLines = mutableListOf<DocxPageTextStripper.ExtractedLine>()
                            val allBoxes = visionText.textBlocks.flatMap { it.lines }.mapNotNull { it.boundingBox }
                            val avgH = if (allBoxes.isNotEmpty()) allBoxes.map { it.height() }.average().toFloat() else 22f

                            for (block in visionText.textBlocks) {
                                for (line in block.lines) {
                                    val box = line.boundingBox ?: continue
                                    val text = line.text.trim()
                                    if (text.isEmpty()) continue
                                    val isBold = box.height() > avgH * 1.28f || (text.length < 45 && text.uppercase() == text && text.any { it.isLetter() })
                                    val fontSize = (box.height().toFloat() * 0.72f).coerceIn(9f, 32f)
                                    val chunk = DocxPageTextStripper.StyledChunk(
                                        text = text,
                                        fontFamily = "Calibri",
                                        fontSizePt = fontSize,
                                        isBold = isBold,
                                        isItalic = false,
                                        x = box.left.toFloat() * (72f / 150f),
                                        y = box.top.toFloat() * (72f / 150f)
                                    )
                                    ocrLines.add(
                                        DocxPageTextStripper.ExtractedLine(
                                            chunks = listOf(chunk),
                                            y = box.top.toFloat() * (72f / 150f),
                                            minX = box.left.toFloat() * (72f / 150f),
                                            maxX = box.right.toFloat() * (72f / 150f),
                                            lineText = text
                                        )
                                    )
                                }
                            }
                            if (ocrLines.isNotEmpty()) {
                                rawLines = ocrLines
                            }
                            pageBmp.recycle()
                        }
                    } catch (_: Throwable) {}
                }

                val lines = orderPageLines(rawLines)

                // 2. Extract embedded images, diagrams, and formulas from PDF resources
                val pageImages = mutableListOf<ExtractedDocxImage>()
                try {
                    val page = doc.getPage(pageNum - 1)
                    val resources = page.resources
                    val hasRichText = lines.size >= 4
                    if (resources != null) {
                        for (name in resources.xObjectNames) {
                            if (resources.isImageXObject(name)) {
                                val xObj = resources.getXObject(name)
                                if (xObj is com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject) {
                                    val bmp = xObj.image
                                    if (bmp != null && bmp.width > 24 && bmp.height > 24) {
                                        // Skip full-page background images on pages with rich text to prevent blank/split layouts
                                        val isFullPageBg = hasRichText && bmp.width >= 500 && bmp.height >= 700
                                        if (!isFullPageBg) {
                                            val stream = ByteArrayOutputStream()
                                            bmp.compress(Bitmap.CompressFormat.PNG, 95, stream)
                                            val imgBytes = stream.toByteArray()
                                            if (imgBytes.isNotEmpty()) {
                                                val maxW = 460f
                                                val wPt = bmp.width.toFloat().coerceAtMost(maxW)
                                                val hPt = (wPt / bmp.width.toFloat()) * bmp.height.toFloat()
                                                val recordedY = stripper.imagePositions[name.name]
                                                val defaultY = if (lines.isNotEmpty()) lines.last().y + 20f else 50f
                                                val imgY = recordedY ?: defaultY
                                                pageImages.add(
                                                    ExtractedDocxImage(
                                                        filename = "page_${pageNum}_${name.name}.png",
                                                        bytes = imgBytes,
                                                        widthPt = wPt,
                                                        heightPt = hPt,
                                                        y = imgY
                                                    )
                                                )
                                            }
                                        }
                                        bmp.recycle()
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Throwable) {}

                // 3. Assemble all page elements (tables, text lines, and images) object-wise
                val pageElements = mutableListOf<DocxPageElement>()
                var lineIdx = 0
                while (lineIdx < lines.size) {
                    val line = lines[lineIdx]
                    val trimmed = line.lineText.trim()

                    if (trimmed.isEmpty()) {
                        lineIdx++
                        continue
                    }

                    // Check for multi-column table pattern (2+ columns separated by 2+ spaces, tabs, or chunk gaps)
                    val tableLines = mutableListOf<List<String>>()
                    var lookahead = lineIdx
                    while (lookahead < lines.size) {
                        val candidate = lines[lookahead]
                        val candidateText = candidate.lineText.trim()
                        val regexCols = candidateText.split(Regex("\\s{2,}|\t")).map { it.trim() }.filter { it.isNotEmpty() }
                        val chunkCols = if (candidate.chunks.size >= 2 && candidate.chunks.all { it.text.trim().isNotEmpty() }) {
                            candidate.chunks.map { it.text.trim() }
                        } else emptyList()

                        val cols = if (regexCols.size >= 2) regexCols else if (chunkCols.size >= 2) chunkCols else emptyList()
                        if (cols.size >= 2) {
                            tableLines.add(cols)
                            lookahead++
                        } else {
                            break
                        }
                    }

                    if (tableLines.size >= 2) {
                        val tableStartY = lines[lineIdx].y
                        pageElements.add(DocxPageElement.Table(tableLines, tableStartY))
                        lineIdx = lookahead
                        continue
                    }

                    pageElements.add(DocxPageElement.TextLine(line))
                    lineIdx++
                }

                for (img in pageImages) {
                    pageElements.add(DocxPageElement.Image(img))
                }

                val sortedElements = pageElements.sortedBy { it.y }

                val minPageX = lines.map { it.minX }.filter { it > 10f }.minOrNull() ?: 54f

                var currentParagraph: org.apache.poi.xwpf.usermodel.XWPFParagraph? = null
                var prevLineY = 0f
                var prevLineMinX = minPageX
                var prevLineHeight = 12f
                var prevLineText = ""
                var inCodeBlock = false

                for (elem in sortedElements) {
                    when (elem) {
                        is DocxPageElement.Table -> {
                            currentParagraph = null
                            inCodeBlock = false
                            prevLineText = ""
                            val maxCols = elem.rows.maxOf { it.size }
                            val table = docx.createTable()
                            for ((rIdx, rowCols) in elem.rows.withIndex()) {
                                val row = if (rIdx == 0) table.getRow(0) else table.createRow()
                                for (cIdx in 0 until maxCols) {
                                    val cell = if (cIdx < row.tableCells.size) row.getCell(cIdx) else row.createCell()
                                    val cellText = rowCols.getOrNull(cIdx) ?: ""
                                    val cellP = if (cell.paragraphs.isNotEmpty()) cell.paragraphs[0] else cell.addParagraph()
                                    cellP.spacingBefore = 60
                                    cellP.spacingAfter = 60
                                    val r = cellP.createRun()
                                    r.fontSize = 10
                                    r.fontFamily = "Calibri"
                                    if (rIdx == 0) {
                                        r.isBold = true
                                        r.color = "1F4E79"
                                        cell.setColor("D9E1F2")
                                    } else if (rIdx % 2 == 1) {
                                        cell.setColor("F9FAFB")
                                    }
                                    r.setText(cellText)
                                }
                            }
                        }
                        is DocxPageElement.Image -> {
                            currentParagraph = null
                            inCodeBlock = false
                            prevLineText = ""
                            val imgP = docx.createParagraph()
                            imgP.alignment = org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER
                            imgP.spacingBefore = 120
                            imgP.spacingAfter = 120
                            val imgR = imgP.createRun()
                            imgR.addPicture(
                                java.io.ByteArrayInputStream(elem.img.bytes),
                                org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG,
                                elem.img.filename,
                                org.apache.poi.util.Units.toEMU(elem.img.widthPt.toDouble()),
                                org.apache.poi.util.Units.toEMU(elem.img.heightPt.toDouble())
                            )
                        }
                        is DocxPageElement.TextLine -> {
                            val line = elem.line
                            val trimmed = line.lineText.trim()
                            val isHeading = (line.chunks.any { it.fontSizePt >= 14f } || isProbableHeading(trimmed)) &&
                                    (line.chunks.any { it.isBold } || line.chunks.any { it.fontSizePt >= 14f })
                            val isCode = !isHeading && (line.chunks.all { it.fontFamily == "Consolas" } || isProbableCodeLine(line.lineText, trimmed))
                            val isListItem = !isHeading && !isCode && (
                                    trimmed.startsWith("•") || trimmed.startsWith("- ") || trimmed.startsWith("* ") ||
                                            trimmed.startsWith("▪") || trimmed.startsWith("▫") ||
                                            trimmed.matches(Regex("^[0-9]+[.)]\\s+.*")) ||
                                            trimmed.matches(Regex("^[a-zA-Z][.)]\\s+.*"))
                                    )

                            val indentInPt = (line.minX - minPageX).coerceAtLeast(0f)
                            val indentTwips = if (indentInPt >= 8f) (indentInPt * 20).toInt() else 0

                            val yGap = if (prevLineY > 0f) line.y - prevLineY else 0f
                            val isBelowPrevLine = line.y > (prevLineY + 2f)
                            val isSameCol = kotlin.math.abs(line.minX - prevLineMinX) < 35f
                            val prevEndedSentence = prevLineText.endsWith(".") || prevLineText.endsWith(":") ||
                                    prevLineText.endsWith("?") || prevLineText.endsWith("!")
                            val isShortLine = prevLineText.length < 45 && prevLineText.isNotEmpty()

                            val isNewParagraphNeeded = isHeading || isListItem || (isCode != inCodeBlock) ||
                                    currentParagraph == null || !isBelowPrevLine || !isSameCol ||
                                    (yGap > (prevLineHeight * 1.55f)) ||
                                    (indentTwips > 0) || (prevEndedSentence && isShortLine)

                            if (isNewParagraphNeeded) {
                                currentParagraph = docx.createParagraph()
                                when {
                                    isHeading -> {
                                        inCodeBlock = false
                                        currentParagraph.spacingBefore = 220
                                        currentParagraph.spacingAfter = 100
                                        if (indentTwips > 0) currentParagraph.indentationLeft = indentTwips
                                    }
                                    isCode -> {
                                        inCodeBlock = true
                                        currentParagraph.spacingBefore = 80
                                        currentParagraph.spacingAfter = 40
                                        currentParagraph.indentationLeft = maxOf(280, indentTwips)
                                    }
                                    isListItem -> {
                                        inCodeBlock = false
                                        currentParagraph.spacingBefore = 40
                                        currentParagraph.spacingAfter = 60
                                        currentParagraph.indentationLeft = maxOf(720, indentTwips)
                                        currentParagraph.indentationHanging = 360
                                    }
                                    else -> {
                                        inCodeBlock = false
                                        currentParagraph.spacingBefore = 60
                                        currentParagraph.spacingAfter = 60
                                        if (indentTwips > 0) currentParagraph.indentationLeft = indentTwips
                                    }
                                }
                            } else {
                                if (inCodeBlock) {
                                    val r = currentParagraph!!.createRun()
                                    r.addBreak()
                                } else {
                                    val r = currentParagraph!!.createRun()
                                    r.setText(" ")
                                }
                            }

                            for (chunk in line.chunks) {
                                val r = currentParagraph!!.createRun()
                                r.fontFamily = if (isCode) "Consolas" else chunk.fontFamily
                                r.fontSize = Math.round(chunk.fontSizePt).toInt().coerceIn(6, 72)
                                r.isBold = chunk.isBold || isHeading
                                r.isItalic = chunk.isItalic
                                if (isHeading) {
                                    r.color = "1F4E79"
                                } else if (isCode) {
                                    r.color = "24292E"
                                }
                                r.setText(chunk.text)
                            }

                            prevLineY = line.y
                            prevLineMinX = line.minX
                            prevLineHeight = line.chunks.maxOfOrNull { it.fontSizePt } ?: 12f
                            prevLineText = trimmed
                        }
                    }
                }

                // Authentic hard page break between pages
                if (pageNum < doc.numberOfPages) {
                    val breakP = docx.createParagraph()
                    val breakR = breakP.createRun()
                    breakR.addBreak(org.apache.poi.xwpf.usermodel.BreakType.PAGE)
                }
            }
            doc.close()

            val outStream = ByteArrayOutputStream()
            docx.write(outStream)
            docx.close()

            val outputFileUri = fileOutputManager.saveToDefault(
                bytes = outStream.toByteArray(),
                filename = "converted_${System.currentTimeMillis()}.docx",
                mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                subfolder = "DOCX"
            )
            outputFileUri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private data class ExtractedDocxImage(
        val filename: String,
        val bytes: ByteArray,
        val widthPt: Float,
        val heightPt: Float,
        val y: Float
    )

    private sealed class DocxPageElement(val y: Float) {
        data class TextLine(val line: DocxPageTextStripper.ExtractedLine) : DocxPageElement(line.y)
        data class Table(val rows: List<List<String>>, val startY: Float) : DocxPageElement(startY)
        data class Image(val img: ExtractedDocxImage) : DocxPageElement(img.y)
    }

    private class DocxPageTextStripper : PDFTextStripper() {
        init {
            sortByPosition = true
        }

        val imagePositions = mutableMapOf<String, Float>()

        override fun processOperator(operator: Operator, operands: MutableList<COSBase>) {
            if (operator.name == "Do" && operands.isNotEmpty()) {
                val nameObj = operands.firstOrNull() as? COSName
                if (nameObj != null) {
                    try {
                        val ctm = graphicsState.currentTransformationMatrix
                        val pageHeight = currentPage?.cropBox?.height ?: 792f
                        val yPt = (pageHeight - ctm.translateY - kotlin.math.abs(ctm.scalingFactorY)).coerceAtLeast(0f)
                        imagePositions[nameObj.name] = yPt
                    } catch (_: Throwable) {}
                }
            }
            super.processOperator(operator, operands)
        }

        data class StyledChunk(
            val text: String,
            val fontFamily: String,
            val fontSizePt: Float,
            val isBold: Boolean,
            val isItalic: Boolean,
            val x: Float,
            val y: Float
        )

        data class ExtractedLine(
            val chunks: List<StyledChunk>,
            val y: Float,
            val minX: Float,
            val maxX: Float,
            val lineText: String
        )

        val lines = mutableListOf<ExtractedLine>()

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            if (textPositions.isEmpty() || text.isBlank()) return

            // 1. Deduplicate characters from drop-shadow, faux-bold, or dual-layer OCR text
            val deduped = mutableListOf<TextPosition>()
            for (pos in textPositions.sortedBy { it.xDirAdj }) {
                val u = pos.unicode
                if (u.isNullOrEmpty()) continue
                val prev = deduped.lastOrNull()
                if (prev != null && prev.unicode == u &&
                    kotlin.math.abs(pos.xDirAdj - prev.xDirAdj) < 1.8f &&
                    kotlin.math.abs(pos.yDirAdj - prev.yDirAdj) < 1.8f
                ) {
                    continue
                }
                deduped.add(pos)
            }
            if (deduped.isEmpty()) return

            // 2. Build styled chunks using exact coordinate displacement for word spacing
            val chunks = mutableListOf<StyledChunk>()
            var currentChunkText = StringBuilder()
            var currentFont = ""
            var currentSize = 0f
            var currentBold = false
            var currentItalic = false
            var chunkStartX = deduped.first().xDirAdj
            val lineY = deduped.first().yDirAdj
            var prevEnd = deduped.first().xDirAdj

            for (pos in deduped) {
                val rawFontName = pos.font?.name ?: "Calibri"
                val fontName = if (rawFontName.contains("+")) rawFontName.substringAfter("+") else rawFontName
                val isBold = fontName.contains("bold", ignoreCase = true) ||
                        fontName.contains("black", ignoreCase = true) ||
                        fontName.contains("heavy", ignoreCase = true) ||
                        fontName.contains("w7", ignoreCase = true) ||
                        fontName.contains("w8", ignoreCase = true) ||
                        fontName.contains("w9", ignoreCase = true)
                val isItalic = fontName.contains("italic", ignoreCase = true) ||
                        fontName.contains("oblique", ignoreCase = true)
                val fontSize = pos.fontSizeInPt

                val family = when {
                    fontName.contains("Courier", ignoreCase = true) ||
                            fontName.contains("Consolas", ignoreCase = true) ||
                            fontName.contains("Mono", ignoreCase = true) ||
                            fontName.contains("Menlo", ignoreCase = true) -> "Consolas"
                    fontName.contains("Times", ignoreCase = true) -> "Times New Roman"
                    fontName.contains("Georgia", ignoreCase = true) -> "Georgia"
                    fontName.contains("Arial", ignoreCase = true) ||
                            fontName.contains("Helvetica", ignoreCase = true) -> "Arial"
                    else -> "Calibri"
                }

                val u = pos.unicode ?: ""
                val spaceGap = pos.xDirAdj - prevEnd
                val spaceWidth = if (pos.widthOfSpace > 0) pos.widthOfSpace else (fontSize * 0.25f)
                val needsSpace = spaceGap > (spaceWidth * 0.45f).coerceAtLeast(2.0f) && currentChunkText.isNotEmpty()

                if (chunks.isEmpty() && currentChunkText.isEmpty()) {
                    currentFont = family
                    currentSize = fontSize
                    currentBold = isBold
                    currentItalic = isItalic
                    chunkStartX = pos.xDirAdj
                    currentChunkText.append(u)
                } else if (family == currentFont &&
                    kotlin.math.abs(fontSize - currentSize) < 0.5f &&
                    isBold == currentBold &&
                    isItalic == currentItalic
                ) {
                    if (needsSpace) currentChunkText.append(" ")
                    currentChunkText.append(u)
                } else {
                    if (currentChunkText.isNotEmpty()) {
                        chunks.add(
                            StyledChunk(
                                text = currentChunkText.toString(),
                                fontFamily = currentFont,
                                fontSizePt = currentSize,
                                isBold = currentBold,
                                isItalic = currentItalic,
                                x = chunkStartX,
                                y = lineY
                            )
                        )
                    }
                    currentChunkText = StringBuilder()
                    if (needsSpace) currentChunkText.append(" ")
                    currentChunkText.append(u)
                    currentFont = family
                    currentSize = fontSize
                    currentBold = isBold
                    currentItalic = isItalic
                    chunkStartX = pos.xDirAdj
                }
                prevEnd = pos.xDirAdj + pos.widthDirAdj
            }

            if (currentChunkText.isNotEmpty()) {
                chunks.add(
                    StyledChunk(
                        text = currentChunkText.toString(),
                        fontFamily = currentFont,
                        fontSizePt = currentSize,
                        isBold = currentBold,
                        isItalic = currentItalic,
                        x = chunkStartX,
                        y = lineY
                    )
                )
            }

            if (chunks.isNotEmpty()) {
                val firstX = chunks.first().x
                val lastChunk = chunks.last()
                val calculatedMaxX = lastChunk.x + (lastChunk.text.length * lastChunk.fontSizePt * 0.5f)
                val fullText = chunks.joinToString("") { it.text }
                lines.add(ExtractedLine(chunks, lineY, firstX, calculatedMaxX, fullText.trimEnd()))
            }
        }
    }

    /**
     * Orders extracted lines on a page to avoid interleaving multi-column text.
     * Detects if the page has 2 columns separated by a central gutter, and outputs:
     * Header lines -> Left column -> Right column -> Footer lines.
     */
    private fun orderPageLines(rawLines: List<DocxPageTextStripper.ExtractedLine>): List<DocxPageTextStripper.ExtractedLine> {
        val bodyLines = rawLines.filter { it.lineText.isNotBlank() }
        if (bodyLines.size < 6) {
            return rawLines.sortedWith(compareBy({ it.y }, { it.minX }))
        }

        val minX = bodyLines.minOf { it.minX }
        val maxX = bodyLines.maxOf { it.maxX }
        val midX = (minX + maxX) / 2f
        val gutterMargin = 20f

        val leftLines = bodyLines.filter { it.maxX < (midX + gutterMargin) }
        val rightLines = bodyLines.filter { it.minX > (midX - gutterMargin) }
        val spanningLines = bodyLines.filter { it.minX < (midX - gutterMargin) && it.maxX > (midX + gutterMargin) }

        val isTwoColumn = leftLines.size >= 3 && rightLines.size >= 3 &&
                spanningLines.size <= ((leftLines.size + rightLines.size) * 0.35f)

        if (!isTwoColumn) {
            return rawLines.sortedWith(compareBy({ it.y }, { it.minX }))
        }

        val colStartY = minOf(
            leftLines.minOfOrNull { it.y } ?: 0f,
            rightLines.minOfOrNull { it.y } ?: 0f
        )
        val colEndY = maxOf(
            leftLines.maxOfOrNull { it.y } ?: Float.MAX_VALUE,
            rightLines.maxOfOrNull { it.y } ?: Float.MAX_VALUE
        )

        val topSpanning = rawLines.filter { it.y < (colStartY - 5f) }.sortedWith(compareBy({ it.y }, { it.minX }))
        val leftColumn = rawLines.filter { it.y in (colStartY - 5f)..(colEndY + 5f) && it.maxX < (midX + gutterMargin) }.sortedWith(compareBy({ it.y }, { it.minX }))
        val rightColumn = rawLines.filter { it.y in (colStartY - 5f)..(colEndY + 5f) && it.minX > (midX - gutterMargin) }.sortedWith(compareBy({ it.y }, { it.minX }))
        val midSpanning = rawLines.filter { it.y in (colStartY - 5f)..(colEndY + 5f) && it.minX < (midX - gutterMargin) && it.maxX > (midX + gutterMargin) }.sortedWith(compareBy({ it.y }, { it.minX }))
        val bottomSpanning = rawLines.filter { it.y > (colEndY + 5f) }.sortedWith(compareBy({ it.y }, { it.minX }))

        val result = mutableListOf<DocxPageTextStripper.ExtractedLine>()
        result.addAll(topSpanning)
        result.addAll(leftColumn)
        result.addAll(rightColumn)
        result.addAll(midSpanning)
        result.addAll(bottomSpanning)
        return result
    }

    private fun isProbableHeading(trimmed: String): Boolean {
        if (trimmed.length > 80) return false
        if (trimmed.matches(Regex("^[0-9]+(\\.[0-9]+)*\\s+[A-Za-z].*"))) return true
        if (trimmed.matches(Regex("^(PART|Part|CHAPTER|Chapter|SECTION|Section)\\s+[0-9IVXLCDM]+.*", RegexOption.IGNORE_CASE))) return true
        if (trimmed.startsWith("###") || trimmed.startsWith("##") || trimmed.startsWith("# ")) return true
        return false
    }

    private fun isProbableCodeLine(line: String, trimmed: String): Boolean {
        if (trimmed.startsWith("#") || trimmed.startsWith("//")) return true
        if (trimmed.contains(" # ") || trimmed.contains(" // ")) return true
        val codeKeywords = listOf(
            "import ", "from ", "def ", "class ", "return ", "lambda ", "for ", "while ",
            "if ", "elif ", "else:", "try:", "except ", "print(", "len(", "math.",
            "int(", "str(", "float(", "list(", "dict(", "set(", "range(", "sorted(",
            "public ", "private ", "void ", "const ", "var ", "val ", "fun "
        )
        if (codeKeywords.any { trimmed.contains(it) }) return true
        if (trimmed.contains(";")) return true
        if (trimmed.contains(" = ") || trimmed.contains(" == ") || trimmed.contains(" += ")) return true
        if (trimmed.contains("[") && trimmed.contains("]")) return true
        if (trimmed.contains("->") || trimmed.contains("=>")) return true
        if (line.startsWith("    ") || line.startsWith("\t")) return true
        return false
    }

    suspend fun convertPdfToPptx(uri: Uri): Uri? = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                ?: return@withContext null
            val doc = PDDocument.load(inputStream)
            val numPages = doc.numberOfPages
            if (numPages == 0) {
                doc.close()
                return@withContext null
            }

            val renderer = PDFRenderer(doc)
            val firstPage = doc.getPage(0)
            val widthPt = firstPage.mediaBox.width
            val heightPt = firstPage.mediaBox.height
            val slideCxEmu = (widthPt * 12700L).toLong().coerceAtLeast(1000000L)
            val slideCyEmu = (heightPt * 12700L).toLong().coerceAtLeast(1000000L)

            val outStream = ByteArrayOutputStream()
            java.util.zip.ZipOutputStream(outStream).use { zip ->
                // 1. [Content_Types].xml
                zip.putNextEntry(java.util.zip.ZipEntry("[Content_Types].xml"))
                val contentTypes = buildString {
                    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                    append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
                    append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
                    append("""<Default Extension="xml" ContentType="application/xml"/>""")
                    append("""<Default Extension="jpg" ContentType="image/jpeg"/>""")
                    append("""<Default Extension="jpeg" ContentType="image/jpeg"/>""")
                    append("""<Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>""")
                    for (i in 1..numPages) {
                        append("""<Override PartName="/ppt/slides/slide$i.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>""")
                    }
                    append("""</Types>""")
                }
                zip.write(contentTypes.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                // 2. _rels/.rels
                zip.putNextEntry(java.util.zip.ZipEntry("_rels/.rels"))
                val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
</Relationships>"""
                zip.write(rootRels.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                // 3. ppt/_rels/presentation.xml.rels
                zip.putNextEntry(java.util.zip.ZipEntry("ppt/_rels/presentation.xml.rels"))
                val presRels = buildString {
                    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                    append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                    for (i in 1..numPages) {
                        append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide$i.xml"/>""")
                    }
                    append("""</Relationships>""")
                }
                zip.write(presRels.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                // 4. ppt/presentation.xml
                zip.putNextEntry(java.util.zip.ZipEntry("ppt/presentation.xml"))
                val presXml = buildString {
                    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                    append("""<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" """)
                    append("""xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" """)
                    append("""xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">""")
                    append("""<p:sldMasterIdLst/>""")
                    append("""<p:sldIdLst>""")
                    for (i in 1..numPages) {
                        val slideId = 255 + i
                        append("""<p:sldId id="$slideId" r:id="rId$i"/>""")
                    }
                    append("""</p:sldIdLst>""")
                    append("""<p:sldSz cx="$slideCxEmu" cy="$slideCyEmu" type="custom"/>""")
                    append("""<p:notesSz cx="6858000" cy="9144000"/>""")
                    append("""</p:presentation>""")
                }
                zip.write(presXml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                // 5. For each slide: slide XML, slide rels, and media JPEG
                for (i in 1..numPages) {
                    val pageIndex = i - 1
                    val bitmap = renderer.renderImageWithDPI(pageIndex, 150f)
                    val imgStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, imgStream)
                    bitmap.recycle()
                    val imgBytes = imgStream.toByteArray()

                    // ppt/media/image{i}.jpg
                    zip.putNextEntry(java.util.zip.ZipEntry("ppt/media/image$i.jpg"))
                    zip.write(imgBytes)
                    zip.closeEntry()

                    // ppt/slides/_rels/slide{i}.xml.rels
                    zip.putNextEntry(java.util.zip.ZipEntry("ppt/slides/_rels/slide$i.xml.rels"))
                    val slideRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image$i.jpg"/>
</Relationships>"""
                    zip.write(slideRels.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    // ppt/slides/slide{i}.xml
                    zip.putNextEntry(java.util.zip.ZipEntry("ppt/slides/slide$i.xml"))
                    val slideXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
       xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
       xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld>
    <p:spTree>
      <p:nvGrpSpPr>
        <p:cNvPr id="1" name=""/>
        <p:cNvGrpSpPr/>
        <p:nvPr/>
      </p:nvGrpSpPr>
      <p:grpSpPr>
        <a:xfrm>
          <a:off x="0" y="0"/>
          <a:ext cx="0" cy="0"/>
          <a:chOff x="0" y="0"/>
          <a:chExt cx="0" cy="0"/>
        </a:xfrm>
      </p:grpSpPr>
      <p:pic>
        <p:nvPicPr>
          <p:cNvPr id="2" name="Page $i Image"/>
          <p:cNvPicPr>
            <a:picLocks noChangeAspect="1"/>
          </p:cNvPicPr>
          <p:nvPr/>
        </p:nvPicPr>
        <p:blipFill>
          <a:blip r:embed="rId1"/>
          <a:stretch>
            <a:fillRect/>
          </a:stretch>
        </p:blipFill>
        <p:spPr>
          <a:xfrm>
            <a:off x="0" y="0"/>
            <a:ext cx="$slideCxEmu" cy="$slideCyEmu"/>
          </a:xfrm>
          <a:prstGeom prst="rect">
            <a:avLst/>
          </a:prstGeom>
        </p:spPr>
      </p:pic>
    </p:spTree>
  </p:cSld>
  <p:clrMapOvr>
    <a:masterClrMapping/>
  </p:clrMapOvr>
</p:sld>"""
                    zip.write(slideXml.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
            doc.close()

            val outputFileUri = fileOutputManager.saveToDefault(
                bytes = outStream.toByteArray(),
                filename = "converted_${System.currentTimeMillis()}.pptx",
                mimeType = "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                subfolder = "PPTX"
            )
            outputFileUri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun fillInteractiveForm(uri: Uri, formData: Map<String, String>): Uri? = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val doc = PDDocument.load(inputStream)
            val catalog = doc.documentCatalog
            val acroForm: PDAcroForm? = catalog.acroForm

            if (acroForm != null) {
                for ((key, value) in formData) {
                    val field = acroForm.getField(key)
                    field?.setValue(value)
                }
            }

            val outStream = ByteArrayOutputStream()
            doc.save(outStream)
            doc.close()

            val outputFileUri = fileOutputManager.saveToDefault(
                bytes = outStream.toByteArray(),
                filename = "filled_form_${System.currentTimeMillis()}.pdf",
                mimeType = "application/pdf",
                subfolder = "PDF"
            )
            outputFileUri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
