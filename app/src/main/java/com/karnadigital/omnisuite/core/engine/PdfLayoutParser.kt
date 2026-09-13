package com.karnadigital.omnisuite.core.engine

import android.graphics.Color
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.util.ArrayList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class PdfTextBlock(
    val id: Int,
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val fontSize: Float,
    val fontFamily: String,
    val fontWeight: String,
    val textColor: Int,
    val backgroundColor: Int?,
    val pageIndex: Int,
    val blockType: BlockType = BlockType.PARAGRAPH,
    val confidence: Float = 1.0f,
    var editedText: String? = null
) {
    val displayText: String get() = editedText ?: text
    val centerX: Float get() = x + width / 2
    val centerY: Float get() = y + height / 2
    val right: Float get() = x + width
    val bottom: Float get() = y + height
}

enum class BlockType {
    TITLE,
    HEADING,
    PARAGRAPH,
    LINE,
    TABLE_CELL,
    CAPTION,
    HEADER,
    FOOTER
}

data class PdfPageLayout(
    val pageIndex: Int,
    val pageWidth: Float,
    val pageHeight: Float,
    val blocks: List<PdfTextBlock>
)

data class FontInfo(
    val family: String,
    val weight: String,
    val size: Float,
    val color: Int,
    val isItalic: Boolean = false
)

class PdfLayoutParser {

    private var blockIdCounter = 0

    fun parseDocument(file: File): List<PdfPageLayout> {
        blockIdCounter = 0
        val layouts = mutableListOf<PdfPageLayout>()
        PDDocument.load(file).use { document ->
            for (pageIndex in 0 until document.numberOfPages) {
                val page = document.getPage(pageIndex)
                // PDFTextStripper pages are 1-based; parse from the owning document
                // so the live PDPage is never re-parented to a temp doc.
                val textExtractor = BlockTextStripper()
                textExtractor.startPage = pageIndex + 1
                textExtractor.endPage = pageIndex + 1
                textExtractor.sortByPosition = true
                textExtractor.resetBlocks()
                textExtractor.getText(document)
                val rawBlocks = textExtractor.drainBlocks()
                layouts.add(buildLayout(page, pageIndex, rawBlocks))
            }
        }
        return layouts
    }

    fun parsePage(page: PDPage, pageIndex: Int): PdfPageLayout {
        val mediaBox = page.mediaBox
        val pageWidth = mediaBox.width
        val pageHeight = mediaBox.height

        val textExtractor = BlockTextStripper()
        textExtractor.startPage = pageIndex + 1
        textExtractor.endPage = pageIndex + 1
        textExtractor.sortByPosition = true

        val rawBlocks = mutableListOf<RawTextBlock>()
        textExtractor.extractBlocks(page, rawBlocks)

        val blocks = rawBlocks.map { raw ->
            PdfTextBlock(
                id = blockIdCounter++,
                text = raw.text,
                x = raw.x,
                y = raw.y,
                width = raw.width,
                height = raw.height,
                fontSize = raw.fontSize,
                fontFamily = raw.fontFamily,
                fontWeight = raw.fontWeight,
                textColor = raw.textColor,
                backgroundColor = raw.backgroundColor,
                pageIndex = pageIndex,
                blockType = classifyBlock(raw, pageHeight)
            )
        }

        return PdfPageLayout(pageIndex, pageWidth, pageHeight, blocks)
    }

    private fun buildLayout(page: PDPage, pageIndex: Int, rawBlocks: List<RawTextBlock>): PdfPageLayout {
        val mediaBox = page.mediaBox
        val blocks = rawBlocks.map { raw ->
            PdfTextBlock(
                id = blockIdCounter++,
                text = raw.text,
                x = raw.x,
                y = raw.y,
                width = raw.width,
                height = raw.height,
                fontSize = raw.fontSize,
                fontFamily = raw.fontFamily,
                fontWeight = raw.fontWeight,
                textColor = raw.textColor,
                backgroundColor = raw.backgroundColor,
                pageIndex = pageIndex,
                blockType = classifyBlock(raw, mediaBox.height)
            )
        }
        return PdfPageLayout(pageIndex, mediaBox.width, mediaBox.height, blocks)
    }

    private fun classifyBlock(raw: RawTextBlock, pageHeight: Float): BlockType {
        return when {
            raw.fontSize > 20 -> BlockType.TITLE
            raw.fontSize > 16 -> BlockType.HEADING
            raw.y < pageHeight * 0.05 -> BlockType.HEADER
            raw.y > pageHeight * 0.95 -> BlockType.FOOTER
            raw.text.length < 50 && raw.text.endsWith(":") -> BlockType.CAPTION
            else -> BlockType.PARAGRAPH
        }
    }

    private class RawTextBlock {
        var text: String = ""
        var x: Float = 0f
        var y: Float = 0f
        var width: Float = 0f
        var height: Float = 0f
        var fontSize: Float = 12f
        var fontFamily: String = "Helvetica"
        var fontWeight: String = "normal"
        var textColor: Int = Color.BLACK
        var backgroundColor: Int? = null
        val characters: MutableList<TextPosition> = ArrayList()
    }

    private inner class BlockTextStripper : PDFTextStripper() {
        private val rawBlocks = mutableListOf<RawTextBlock>()
        private var currentBlock: RawTextBlock? = null
        private val lineThreshold = 5f
        private val paragraphThreshold = 15f

        fun resetBlocks() {
            rawBlocks.clear()
            currentBlock = null
            // Flush any paragraph still held from a previous getText() call.
            endPageBlocks()
        }

        fun drainBlocks(): List<RawTextBlock> {
            endPageBlocks()
            return rawBlocks.toList()
        }

        private fun endPageBlocks() {
            val pending = currentBlock
            if (pending != null && pending.text.isNotBlank()) {
                rawBlocks.add(pending)
            }
            currentBlock = null
        }

        fun extractBlocks(page: PDPage, output: MutableList<RawTextBlock>) {
            rawBlocks.clear()
            currentBlock = null
            // Standalone path (no owning doc): import (clone) instead of addPage,
            // which would steal the live page from its document and corrupt it.
            PDDocument().use { tempDoc ->
                try {
                    val imported = tempDoc.importPage(page)
                    startPage = 1
                    endPage = 1
                    getText(tempDoc)
                    // importPage leaves `imported` owned by tempDoc; closed by use{}.
                } catch (_: Throwable) {
                    // Fallback for ports without importPage: parse via COS copy is
                    // unavailable, return what we have rather than crashing.
                }
            }
            endPageBlocks()
            output.addAll(rawBlocks)
        }

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            if (textPositions.isEmpty()) return

            val firstChar = textPositions.first()
            val fontSize = firstChar.fontSizeInPt
            val fontFamily = firstChar.font.name ?: "Helvetica"
            val textColor = extractTextColor(firstChar)

            val x = firstChar.xDirAdj
            val y = firstChar.yDirAdj
            val width = textPositions.sumOf { it.widthDirAdj.toDouble() }.toFloat()
            val height = fontSize * 1.2f

            val block = currentBlock
            if (block == null || !shouldMerge(block, x, y, fontSize, fontFamily)) {
                if (block != null && block.text.isNotBlank()) {
                    rawBlocks.add(block)
                }
                currentBlock = RawTextBlock().apply {
                    this.x = x
                    this.y = y
                    this.width = width
                    this.height = height
                    this.fontSize = fontSize
                    this.fontFamily = fontFamily
                    this.fontWeight = if (fontFamily.contains("Bold") || fontFamily.contains("bold")) "bold" else "normal"
                    this.textColor = textColor
                    this.text = text
                }
            } else {
                block.width = (x + width) - block.x
                block.height = max(block.height, height)
                block.text = block.text + " " + text
            }
        }

        private fun shouldMerge(block: RawTextBlock, x: Float, y: Float, fontSize: Float, fontFamily: String): Boolean {
            val sameLine = abs(y - block.y) < lineThreshold
            val sameFont = abs(fontSize - block.fontSize) < 1f && fontFamily == block.fontFamily
            val closeEnough = (x - (block.x + block.width)) < paragraphThreshold
            return sameLine && sameFont && closeEnough
        }

        private fun extractTextColor(textPosition: TextPosition): Int {
            return Color.BLACK
        }
    }

    companion object {
        fun mergeNearbyBlocks(blocks: List<PdfTextBlock>, threshold: Float = 10f): List<PdfTextBlock> {
            if (blocks.isEmpty()) return blocks
            val merged = mutableListOf<PdfTextBlock>()
            val sorted = blocks.sortedWith(compareBy({ it.y }, { it.x }))
            var current = sorted.first()

            for (i in 1 until sorted.size) {
                val next = sorted[i]
                val sameRow = abs(next.y - current.y) < threshold
                val closeEnough = (next.x - (current.x + current.width)) < threshold * 2
                val sameFont = abs(next.fontSize - current.fontSize) < 1f

                if (sameRow && closeEnough && sameFont) {
                    current = current.copy(
                        text = current.text + " " + next.text,
                        width = (next.x + next.width) - current.x,
                        height = max(current.height, next.height)
                    )
                } else {
                    merged.add(current)
                    current = next
                }
            }
            merged.add(current)
            return merged
        }

        fun detectTables(blocks: List<PdfTextBlock>): List<PdfTextBlock> {
            val columns = blocks.groupBy { it.x.toInt() / 50 * 50 }
            val tableColumns = columns.filter { it.value.size >= 3 }
            if (tableColumns.size >= 2) {
                return blocks.map { block ->
                    val col = tableColumns.keys.minByOrNull { abs(it - (block.x.toInt() / 50 * 50)) }
                    if (col != null && abs(col - (block.x.toInt() / 50 * 50)) < 30) {
                        block.copy(blockType = BlockType.TABLE_CELL)
                    } else block
                }
            }
            return blocks
        }
    }
}
