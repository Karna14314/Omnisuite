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
                // Char-level regrouping (own line formation, not PDFBox's).
                var rawLines = stripper.finishGrouping()

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
                                                // Content width for 1in margins on A4 ≈ 451pt; cap at
                                                // 440pt and never upscale tiny icons (keeps formal print clean).
                                                val maxW = 440f
                                                val wPt = bmp.width.toFloat().coerceAtMost(maxW)
                                                val hPt = ((wPt / bmp.width.toFloat()) * bmp.height.toFloat()).coerceAtMost(600f)
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

                    // Code lines are never tables: aligned trailing comments look
                    // tabular but must stay monospace paragraphs (TCS case).
                    val lineIsMono = line.chunks.isNotEmpty() &&
                            line.chunks.all { it.fontFamily == "Consolas" }

                    // Table detection (strict): require 3+ consecutive rows with a
                    // consistent column count, short cell text, and real column gaps.
                    // The old >=2-row whitespace rule turned justified prose/TOC/code
                    // into blue-header tables — the main "clutter" complaint.
                    val tableLines = mutableListOf<List<String>>()
                    var lookahead = lineIdx
                    while (lookahead < lines.size && !lineIsMono) {
                        val candidate = lines[lookahead]
                        val candidateText = candidate.lineText.trim()
                        if (candidateText.isEmpty()) break
                        // Monospace rows belong to code blocks, never to tables.
                        if (candidate.chunks.isNotEmpty() &&
                            candidate.chunks.all { it.fontFamily == "Consolas" }
                        ) break
                        val regexCols = candidateText.split(Regex("\\s{2,}|\t")).map { it.trim() }.filter { it.isNotEmpty() }
                        // Chunk-gap columns only count when chunks are physically separated
                        // (>=30pt gap), not merely styled differently on one line.
                        val chunkCols = if (candidate.chunks.size >= 2) {
                            val sorted = candidate.chunks.sortedBy { it.x }
                            var separated = true
                            for (i in 1 until sorted.size) {
                                val prev = sorted[i - 1]
                                val prevEnd = prev.x + (prev.text.length * prev.fontSizePt * 0.55f)
                                if ((sorted[i].x - prevEnd) < 30f) { separated = false; break }
                            }
                            if (separated) sorted.map { it.text.trim() }.filter { it.isNotEmpty() } else emptyList()
                        } else emptyList()

                        val cols = if (regexCols.size >= 2) regexCols else if (chunkCols.size >= 2) chunkCols else emptyList()
                        // Reject prose-like rows: any cell >60 chars is almost never a table cell.
                        if (cols.size >= 2 && cols.all { it.length <= 60 }) {
                            tableLines.add(cols)
                            lookahead++
                        } else {
                            break
                        }
                    }

                    val isConsistentTable = tableLines.size >= 3 &&
                            tableLines.map { it.size }.toSet().size <= 2 &&
                            tableLines.all { it.size >= 2 }
                    if (isConsistentTable) {
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
                var prevIndentTwips = 0
                var prevDomSize = 0f
                var prevDomBold = false
                var inCodeBlock = false

                for (elem in sortedElements) {
                    when (elem) {
                        is DocxPageElement.Table -> {
                            currentParagraph = null
                            inCodeBlock = false
                            prevIndentTwips = 0
                            prevDomSize = 0f
                            prevDomBold = false
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
                            prevIndentTwips = 0
                            prevDomSize = 0f
                            prevDomBold = false
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
                            // Strict heading: needs BOTH size bump AND bold (or numbered/chapter
                            // pattern with size bump). Old OR-logic bolded every large OR bold line.
                            // 13pt covers section heads (body is ~9-11pt); cover titles are 18pt+.
                            val hasSizeBump = line.chunks.any { it.fontSizePt >= 13f }
                            val hasBold = line.chunks.any { it.isBold }
                            val isHeading = (hasSizeBump && hasBold) ||
                                    (hasSizeBump && isProbableHeading(trimmed))
                            // Strict code: mono font wins; keyword guess only with indent/leading
                            // whitespace to avoid flagging prose containing "if / for / =".
                            val isMono = line.chunks.isNotEmpty() && line.chunks.all { it.fontFamily == "Consolas" }
                            val isCode = !isHeading && (isMono ||
                                    ((line.lineText.startsWith("    ") || line.lineText.startsWith("\t")) &&
                                            isProbableCodeLine(line.lineText, trimmed)))
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
                            // Indent alone must not split a wrapped paragraph — only a real
                            // indent JUMP (>36pt ≈ 0.5in) starts a new block (block-quote etc).
                            val indentJump = kotlin.math.abs(indentTwips - prevIndentTwips) > 720
                            // Dominant style per line (majority chars): a style change
                            // (heading bar -> body, body -> section head) always splits.
                            // This fixes "What's inside" merging into the Part 1 paragraph.
                            val domSize = line.chunks.maxByOrNull { it.text.length }?.fontSizePt ?: 12f
                            val domBold = line.chunks.maxByOrNull { it.text.length }?.isBold ?: false
                            val styleChanged = currentParagraph != null &&
                                    (kotlin.math.abs(domSize - prevDomSize) > 1.5f ||
                                            (domBold != prevDomBold && yGap > prevLineHeight * 1.3f))

                            val isNewParagraphNeeded = isHeading || isListItem || (isCode != inCodeBlock) ||
                                    currentParagraph == null || !isBelowPrevLine || !isSameCol ||
                                    (yGap > (prevLineHeight * 1.9f)) || indentJump || styleChanged

                            if (isNewParagraphNeeded) {
                                currentParagraph = docx.createParagraph()
                                when {
                                    isHeading -> {
                                        inCodeBlock = false
                                        currentParagraph.spacingBefore = 220
                                        currentParagraph.spacingAfter = 100
                                        if (indentTwips > 0) currentParagraph.indentationLeft = indentTwips
                                        // Title-grade heads (cover title, PART banners) stay
                                        // centered like the source; section heads stay left.
                                        val maxSize = line.chunks.maxOfOrNull { it.fontSizePt } ?: 12f
                                        if (maxSize >= 18f) {
                                            currentParagraph.alignment = org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER
                                        }
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

                            // Merge consecutive same-style chunks into one run to avoid
                            // run explosion (a major source of cluttered/formal-print noise).
                            // No invented colors: headings keep bold+size+spacing, not forced blue.
                            var pendingText = StringBuilder()
                            var pendingFamily: String? = null
                            var pendingSize = 0
                            var pendingBold = false
                            var pendingItalic = false
                            fun flushPending() {
                                if (pendingText.isNotEmpty()) {
                                    val r = currentParagraph!!.createRun()
                                    r.fontFamily = pendingFamily ?: "Calibri"
                                    r.fontSize = pendingSize.coerceIn(6, 72)
                                    r.isBold = pendingBold
                                    r.isItalic = pendingItalic
                                    r.setText(pendingText.toString())
                                    pendingText = StringBuilder()
                                }
                            }
                            for (chunk in line.chunks) {
                                val fam = if (isCode) "Consolas" else chunk.fontFamily
                                val sz = Math.round(chunk.fontSizePt).toInt().coerceIn(6, 72)
                                val b = chunk.isBold || isHeading
                                val it = chunk.isItalic
                                if (pendingText.isEmpty()) {
                                    pendingFamily = fam; pendingSize = sz; pendingBold = b; pendingItalic = it
                                    pendingText.append(chunk.text)
                                } else if (fam == pendingFamily && sz == pendingSize && b == pendingBold && it == pendingItalic) {
                                    pendingText.append(chunk.text)
                                } else {
                                    flushPending()
                                    pendingFamily = fam; pendingSize = sz; pendingBold = b; pendingItalic = it
                                    pendingText.append(chunk.text)
                                }
                            }
                            flushPending()

                            prevLineY = line.y
                            prevLineMinX = line.minX
                            prevLineHeight = line.chunks.maxOfOrNull { it.fontSizePt } ?: 12f
                            prevIndentTwips = indentTwips
                            prevDomSize = domSize
                            prevDomBold = domBold
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

        private data class RawChar(
            val ch: String,
            val x: Float,
            val yBase: Float,
            val size: Float,
            val fontName: String,
            val width: Float,
            val height: Float
        )

        private val rawChars = mutableListOf<RawChar>()

        override fun processTextPosition(pos: TextPosition) {
            super.processTextPosition(pos)
            val u = pos.unicode ?: return
            if (u.isEmpty()) return
            rawChars.add(
                RawChar(
                    ch = u,
                    x = pos.xDirAdj,
                    yBase = pos.yDirAdj,
                    size = pos.fontSizeInPt,
                    fontName = pos.font?.name ?: "",
                    width = pos.widthDirAdj,
                    height = pos.fontSizeInPt * 0.7f
                )
            )
        }

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            // Intentional no-op: visual lines are formed in finishGrouping() from
            // raw chars. PDFBox's own line formation merges rows whose y-ranges
            // overlap (e.g. a 24pt title over an 11pt subtitle), interleaving both
            // rows' characters into one garbled line.
        }

        /**
         * Char-level line formation (validated against pdfminer-style layout):
         * baseline rows -> superscript merge-back -> font-size split for merged
         * rows -> styled chunks -> same-visual-line merge -> duplicate drop.
         * Conventions match the old code: ascending y = top to bottom.
         */
        fun finishGrouping(): List<ExtractedLine> {
            lines.clear()
            if (rawChars.isEmpty()) return lines

            // 1. Rows by baseline proximity.
            val sorted = rawChars.sortedWith(compareBy({ it.yBase }, { it.x }))
            val rows = mutableListOf<MutableList<RawChar>>()
            for (c in sorted) {
                val last = rows.lastOrNull()
                if (last != null && kotlin.math.abs(c.yBase - last.last().yBase) <= 2.5f) last.add(c)
                else rows.add(mutableListOf(c))
            }

            // 2. Merge superscript/subscript slivers back into the neighbor row.
            val mergedRows = mutableListOf<MutableList<RawChar>>()
            for (row in rows) {
                val prev = mergedRows.lastOrNull()
                if (prev != null && row.size <= 3 && prev.size > 3) {
                    val rowMed = row.map { it.size }.sorted().let { it[it.size / 2] }
                    val prevMed = prev.map { it.size }.sorted().let { it[it.size / 2] }
                    val prevH = prev.maxOf { it.height }.coerceAtLeast(1f)
                    if (rowMed < prevMed * 0.7f &&
                        kotlin.math.abs(row.first().yBase - prev.last().yBase) < prevH * 1.2f
                    ) {
                        prev.addAll(row)
                        continue
                    }
                }
                mergedRows.add(row)
            }

            // 3. Build lines, splitting rows that mix distinct font sizes
            // (the title-over-subtitle case) into separate lines, top first.
            val built = mutableListOf<ExtractedLine>()
            for (row in mergedRows) {
                val bands = mutableMapOf<Float, MutableList<RawChar>>()
                for (c in row) {
                    val key = bands.keys.firstOrNull { kotlin.math.abs(it - c.size) <= 1.2f }
                    if (key != null) bands[key]!!.add(c) else bands[c.size] = mutableListOf(c)
                }
                val fragments: List<List<RawChar>> = if (bands.size > 1) {
                    val counts = bands.values.map { it.size }
                    val sizes = bands.keys.sorted()
                    if (counts.min() / counts.sum().toFloat() > 0.12f &&
                        sizes.last() / sizes.first().coerceAtLeast(0.1f) > 1.5f
                    ) {
                        bands.values.sortedBy { frag -> frag.minOf { it.yBase } }
                    } else listOf(row.sortedBy { it.x })
                } else listOf(row.sortedBy { it.x })
                for (frag in fragments) {
                    val line = buildLine(frag.sortedBy { it.x })
                    if (line != null) built.add(line)
                }
            }

            // 4. Merge fragments of one visual line that PDF text ops split apart
            // (wide interior gaps, e.g. code plus trailing comment).
            val merged = mutableListOf<ExtractedLine>()
            for (ln in built) {
                val prev = merged.lastOrNull()
                if (prev != null && canMergeVisual(prev, ln)) {
                    merged[merged.lastIndex] = joinVisual(prev, ln)
                } else merged.add(ln)
            }

            // 5. Drop exact-duplicate lines (shadow / double-drawn text).
            var lastText = ""
            var lastY = Float.MIN_VALUE
            for (ln in merged) {
                val norm = ln.lineText.replace(Regex("\\s+"), " ")
                if (norm.isNotBlank() &&
                    !(norm == lastText && kotlin.math.abs(ln.y - lastY) < 4f)
                ) {
                    lines.add(ln)
                    lastText = norm
                    lastY = ln.y
                }
            }
            return lines
        }

        private fun lineHeightOf(ln: ExtractedLine): Float =
            ln.chunks.maxOfOrNull { it.fontSizePt } ?: 12f

        private fun canMergeVisual(prev: ExtractedLine, next: ExtractedLine): Boolean {
            if (next.lineText.isBlank() || prev.lineText.isBlank()) return false
            val dy = kotlin.math.abs(next.y - prev.y)
            if (dy > 0.5f * kotlin.math.min(lineHeightOf(prev), lineHeightOf(next))) return false
            // Next starts where previous ends (x-disjoint, to the right).
            return next.minX >= prev.maxX - 2f
        }

        private fun joinVisual(prev: ExtractedLine, next: ExtractedLine): ExtractedLine {
            val gap = next.minX - prev.maxX
            val mono = (prev.chunks + next.chunks).all { it.fontFamily == "Consolas" }
            val spaceW = 4f
            val sep = if (gap > spaceW) {
                if (mono) " ".repeat(1 + (gap / spaceW).toInt().coerceIn(0, 8)) else " "
            } else ""
            val first = next.chunks.first()
            val adjustedFirst = first.copy(text = sep + first.text)
            val chunks = prev.chunks + listOf(adjustedFirst) + next.chunks.drop(1)
            val fullText = chunks.joinToString("") { it.text }
            return ExtractedLine(chunks, prev.y, prev.minX, next.maxX, fullText.trimEnd())
        }

        private fun mapFamily(rawFontName: String): Triple<String, Boolean, Boolean> {
            val fontName = if (rawFontName.contains("+")) rawFontName.substringAfter("+") else rawFontName
            val isBold = fontName.contains("bold", ignoreCase = true) ||
                    fontName.contains("black", ignoreCase = true) ||
                    fontName.contains("heavy", ignoreCase = true) ||
                    fontName.contains("w7", ignoreCase = true) ||
                    fontName.contains("w8", ignoreCase = true) ||
                    fontName.contains("w9", ignoreCase = true)
            val isItalic = fontName.contains("italic", ignoreCase = true) ||
                    fontName.contains("oblique", ignoreCase = true)
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
            return Triple(family, isBold, isItalic)
        }

        private fun buildLine(cells: List<RawChar>): ExtractedLine? {
            if (cells.isEmpty()) return null
            // Deduplicate characters from drop-shadow, faux-bold, or dual-layer text.
            val deduped = mutableListOf<RawChar>()
            for (c in cells) {
                val prev = deduped.lastOrNull()
                if (prev != null && prev.ch == c.ch &&
                    kotlin.math.abs(c.x - prev.x) < 1.8f &&
                    kotlin.math.abs(c.yBase - prev.yBase) < 1.8f
                ) continue
                deduped.add(c)
            }
            if (deduped.isEmpty()) return null

            val chunks = mutableListOf<StyledChunk>()
            var currentChunkText = StringBuilder()
            var currentFont = ""
            var currentSize = 0f
            var currentBold = false
            var currentItalic = false
            var chunkStartX = deduped.first().x
            val lineY = deduped.first().yBase
            var prevEnd = deduped.first().x

            fun pushChunk() {
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
                    currentChunkText = StringBuilder()
                }
            }

            for (c in deduped) {
                val (family, isBold, isItalic) = mapFamily(c.fontName)
                val fontSize = c.size
                val spaceGap = c.x - prevEnd
                val spaceWidth = (fontSize * 0.28f).coerceAtLeast(1.5f)
                val needsSpace = spaceGap > (spaceWidth * 0.45f).coerceAtLeast(2.0f) && currentChunkText.isNotEmpty()
                // Monospace column gaps keep extra spaces so code columns survive.
                val gapSpaces = if (needsSpace && family == "Consolas" && currentFont == "Consolas") {
                    " ".repeat(1 + (spaceGap / spaceWidth).toInt().coerceIn(0, 8))
                } else if (needsSpace) " " else ""

                if (chunks.isEmpty() && currentChunkText.isEmpty()) {
                    currentFont = family
                    currentSize = fontSize
                    currentBold = isBold
                    currentItalic = isItalic
                    chunkStartX = c.x
                    currentChunkText.append(c.ch)
                } else if (family == currentFont &&
                    kotlin.math.abs(fontSize - currentSize) < 0.5f &&
                    isBold == currentBold &&
                    isItalic == currentItalic
                ) {
                    currentChunkText.append(gapSpaces)
                    currentChunkText.append(c.ch)
                } else {
                    pushChunk()
                    currentChunkText.append(gapSpaces)
                    currentChunkText.append(c.ch)
                    currentFont = family
                    currentSize = fontSize
                    currentBold = isBold
                    currentItalic = isItalic
                    chunkStartX = c.x
                }
                prevEnd = c.x + c.width
            }
            pushChunk()

            if (chunks.isEmpty()) return null
            val firstX = chunks.first().x
            val lastChunk = chunks.last()
            val calculatedMaxX = lastChunk.x + (lastChunk.text.length * lastChunk.fontSizePt * 0.5f)
            val fullText = chunks.joinToString("") { it.text }
            if (fullText.isBlank()) return null
            return ExtractedLine(chunks, lineY, firstX, calculatedMaxX, fullText.trimEnd())
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
