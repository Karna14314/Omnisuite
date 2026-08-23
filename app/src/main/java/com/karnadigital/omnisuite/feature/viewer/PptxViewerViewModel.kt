package com.karnadigital.omnisuite.feature.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karnadigital.omnisuite.core.engine.DocumentSearchEngine
import com.karnadigital.omnisuite.core.engine.SearchResult
import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.core.repository.RecentFileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.sl.usermodel.Placeholder
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.poi.xslf.usermodel.XSLFTextParagraph
import org.apache.poi.xslf.usermodel.XSLFTextRun
import org.apache.poi.xslf.usermodel.XSLFTextShape
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject

data class PptxTextBlock(
    val id: String,
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val textColorHex: String? = null,
    val fontSizePt: Float = 18f,
    val bulletLevel: Int = 0,
    val alignment: String = "LEFT", // "LEFT", "CENTER", "RIGHT", "JUSTIFY"
    val shapeLeft: Float = 0f,
    val shapeTop: Float = 0f,
    val shapeWidth: Float = 1f,
    val shapeHeight: Float = 0.1f
)

data class PptxImage(
    val filePath: String,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

data class PptxSlide(
    val slideNumber: Int,
    val title: PptxTextBlock,
    val textBlocks: List<PptxTextBlock>,
    val images: List<PptxImage> = emptyList(),
    val speakerNotes: String? = null,
    val bgColorHex: String? = null
)

data class PptxPresentation(val slides: List<PptxSlide>)

sealed class PptxLoadState {
    object Loading : PptxLoadState()
    data class Success(val presentation: PptxPresentation, val fileName: String) : PptxLoadState()
    data class Error(val message: String) : PptxLoadState()
}

@HiltViewModel
class PptxViewerViewModel @Inject constructor(
    private val recentFileRepository: RecentFileRepository
) : ViewModel() {

    private val _loadState = MutableStateFlow<PptxLoadState>(PptxLoadState.Loading)
    val loadState: StateFlow<PptxLoadState> = _loadState.asStateFlow()

    private val _saveStatus = MutableSharedFlow<String>()
    val saveStatus = _saveStatus.asSharedFlow()

    private var activePresentation: org.apache.poi.sl.usermodel.SlideShow<*, *>? = null
    private var activeFilePath: String? = null

    private val tempImageCache = mutableMapOf<String, File>()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _currentMatchIndex = MutableStateFlow(-1)
    val currentMatchIndex: StateFlow<Int> = _currentMatchIndex.asStateFlow()

    private fun getXmlObjectReflection(obj: Any): Any? {
        return try {
            obj.javaClass.getMethod("getXmlObject").invoke(obj)
        } catch (t: Throwable) {
            try {
                val method = obj.javaClass.getDeclaredMethod("fetchXmlObject")
                method.isAccessible = true
                method.invoke(obj)
            } catch (t2: Throwable) {
                null
            }
        }
    }

    /**
     * Safely reads shape bounding box (x, y, cx, cy) in EMUs from XMLBeans
     * without touching java.awt.geom.Rectangle2D which is absent on Android runtime.
     */
    private fun getXmlShapeBoundsNormalized(
        shape: Any,
        slideWidthEmu: Long,
        slideHeightEmu: Long
    ): FloatArray? {
        try {
            val xml = getXmlObjectReflection(shape) ?: return null
            val spPr = try {
                xml.javaClass.getMethod("getSpPr").invoke(xml)
            } catch (t: Throwable) {
                null
            } ?: return null

            val xfrm = try {
                spPr.javaClass.getMethod("getXfrm").invoke(spPr)
            } catch (t: Throwable) {
                null
            } ?: return null

            val off = try { xfrm.javaClass.getMethod("getOff").invoke(xfrm) } catch (t: Throwable) { null }
            val ext = try { xfrm.javaClass.getMethod("getExt").invoke(xfrm) } catch (t: Throwable) { null }

            val x = (try { off?.javaClass?.getMethod("getX")?.invoke(off) as? Number } catch (t: Throwable) { null })?.toLong()
            val y = (try { off?.javaClass?.getMethod("getY")?.invoke(off) as? Number } catch (t: Throwable) { null })?.toLong()
            val cx = (try { ext?.javaClass?.getMethod("getCx")?.invoke(ext) as? Number } catch (t: Throwable) { null })?.toLong()
            val cy = (try { ext?.javaClass?.getMethod("getCy")?.invoke(ext) as? Number } catch (t: Throwable) { null })?.toLong()

            if (x != null && y != null && cx != null && cy != null && slideWidthEmu > 0 && slideHeightEmu > 0) {
                val left = (x.toFloat() / slideWidthEmu.toFloat()).coerceIn(0f, 1f)
                val top = (y.toFloat() / slideHeightEmu.toFloat()).coerceIn(0f, 1f)
                val width = (cx.toFloat() / slideWidthEmu.toFloat()).coerceIn(0.05f, 1f)
                val height = (cy.toFloat() / slideHeightEmu.toFloat()).coerceIn(0.02f, 1f)
                return floatArrayOf(left, top, width, height)
            }
        } catch (t: Throwable) {
            // Ignore any XML navigation exception
        }
        return null
    }

    /**
     * Safely reads slide background color in hex from XMLBeans without touching java.awt.Color.
     */
    private fun getSlideBgColorHex(slide: org.apache.poi.sl.usermodel.Slide<*, *>): String? {
        if (slide !is XSLFSlide) return null
        try {
            val ctSlide = getXmlObjectReflection(slide) ?: return null
            val cSld = try { ctSlide.javaClass.getMethod("getCSld").invoke(ctSlide) } catch (t: Throwable) { null } ?: return null
            val bg = try { cSld.javaClass.getMethod("getBg").invoke(cSld) } catch (t: Throwable) { null } ?: return null
            val bgPr = try { bg.javaClass.getMethod("getBgPr").invoke(bg) } catch (t: Throwable) { null } ?: return null
            val solidFill = try { bgPr.javaClass.getMethod("getSolidFill").invoke(bgPr) } catch (t: Throwable) { null } ?: return null
            val srgbClr = try { solidFill.javaClass.getMethod("getSrgbClr").invoke(solidFill) } catch (t: Throwable) { null }
            if (srgbClr != null) {
                val hexBytes = try { srgbClr.javaClass.getMethod("getVal").invoke(srgbClr) as? ByteArray } catch (t: Throwable) { null }
                val hex = hexBytes?.joinToString("") { String.format("%02X", it) }
                if (!hex.isNullOrBlank()) return "#$hex"
            }
        } catch (t: Throwable) {
            // Safe fallback
        }
        return null
    }

    /**
     * Safely extracts text run color without touching java.awt.Color.
     */
    private fun extractTextRunColorHex(run: org.apache.poi.sl.usermodel.TextRun): String? {
        if (run is XSLFTextRun) {
            try {
                val xmlRun = getXmlObjectReflection(run) ?: return null
                val rPr = try { xmlRun.javaClass.getMethod("getRPr").invoke(xmlRun) } catch (t: Throwable) { null } ?: return null
                val solidFill = try { rPr.javaClass.getMethod("getSolidFill").invoke(rPr) } catch (t: Throwable) { null } ?: return null
                val srgb = try { solidFill.javaClass.getMethod("getSrgbClr").invoke(solidFill) } catch (t: Throwable) { null }
                if (srgb != null) {
                    val hexBytes = try { srgb.javaClass.getMethod("getVal").invoke(srgb) as? ByteArray } catch (t: Throwable) { null }
                    val hex = hexBytes?.joinToString("") { String.format("%02X", it) }
                    if (!hex.isNullOrBlank()) return "#$hex"
                }
            } catch (t: Throwable) {
                // Ignore
            }
        }
        return null
    }

    private fun setRunProperties(r: XSLFTextRun, textColorHex: String?, fontSizePt: Float) {
        try {
            r.fontSize = fontSizePt.toDouble()
        } catch (t: Throwable) {}

        val xmlRun = getXmlObjectReflection(r) ?: return
        try {
            val rPr = try {
                xmlRun.javaClass.getMethod("getRPr").invoke(xmlRun)
                    ?: xmlRun.javaClass.getMethod("addNewRPr").invoke(xmlRun)
            } catch (t: Throwable) {
                try { xmlRun.javaClass.getMethod("addNewRPr").invoke(xmlRun) } catch (t2: Throwable) { null }
            }

            if (rPr != null) {
                if (textColorHex != null) {
                    try { rPr.javaClass.getMethod("unsetSolidFill").invoke(rPr) } catch (t: Throwable) {}
                    val solidFill = rPr.javaClass.getMethod("addNewSolidFill").invoke(rPr)
                    val srgbClr = solidFill.javaClass.getMethod("addNewSrgbClr").invoke(solidFill)
                    val color = android.graphics.Color.parseColor(textColorHex)
                    val rgbBytes = byteArrayOf(
                        ((color shr 16) and 0xFF).toByte(),
                        ((color shr 8) and 0xFF).toByte(),
                        (color and 0xFF).toByte()
                    )
                    srgbClr.javaClass.getMethod("setVal", ByteArray::class.java).invoke(srgbClr, rgbBytes)
                } else {
                    try { rPr.javaClass.getMethod("unsetSolidFill").invoke(rPr) } catch (t: Throwable) {}
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private fun setSlideBgColorHex(slide: org.apache.poi.sl.usermodel.Slide<*, *>, colorHex: String) {
        if (slide is XSLFSlide) {
            try {
                val color = android.graphics.Color.parseColor(colorHex)
                val rgbBytes = byteArrayOf(
                    ((color shr 16) and 0xFF).toByte(),
                    ((color shr 8) and 0xFF).toByte(),
                    (color and 0xFF).toByte()
                )
                val ctSlide = getXmlObjectReflection(slide)
                if (ctSlide != null) {
                    val cSld = try {
                        ctSlide.javaClass.getMethod("getCSld").invoke(ctSlide)
                            ?: ctSlide.javaClass.getMethod("addNewCSld").invoke(ctSlide)
                    } catch (t: Throwable) {
                        try { ctSlide.javaClass.getMethod("addNewCSld").invoke(ctSlide) } catch (t2: Throwable) { null }
                    }
                    if (cSld != null) {
                        val bg = try {
                            cSld.javaClass.getMethod("getBg").invoke(cSld)
                                ?: cSld.javaClass.getMethod("addNewBg").invoke(cSld)
                        } catch (t: Throwable) {
                            try { cSld.javaClass.getMethod("addNewBg").invoke(cSld) } catch (t2: Throwable) { null }
                        }
                        if (bg != null) {
                            try {
                                val isSetBgPr = bg.javaClass.getMethod("isSetBgPr").invoke(bg) as Boolean
                                if (isSetBgPr) {
                                    val bgPr = bg.javaClass.getMethod("getBgPr").invoke(bg)
                                    if (bgPr != null) {
                                        val unsetMethods = listOf("unsetSolidFill", "unsetGradFill", "unsetBlipFill", "unsetPattFill", "unsetGrpFill")
                                        unsetMethods.forEach { m ->
                                            try { bgPr.javaClass.getMethod(m).invoke(bgPr) } catch (t: Throwable) {}
                                        }
                                    }
                                }
                            } catch (t: Throwable) {}
                            val bgPr = try {
                                bg.javaClass.getMethod("getBgPr").invoke(bg)
                                    ?: bg.javaClass.getMethod("addNewBgPr").invoke(bg)
                            } catch (t: Throwable) {
                                try { bg.javaClass.getMethod("addNewBgPr").invoke(bg) } catch (t2: Throwable) { null }
                            }
                            if (bgPr != null) {
                                val solidFill = bgPr.javaClass.getMethod("addNewSolidFill").invoke(bgPr)
                                val srgbClr = solidFill.javaClass.getMethod("addNewSrgbClr").invoke(solidFill)
                                srgbClr.javaClass.getMethod("setVal", ByteArray::class.java).invoke(srgbClr, rgbBytes)
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    /**
     * Extracts slide dimensions in EMUs without calling ppt.getPageSize() (which returns java.awt.Dimension).
     */
    private fun getSlideDimensionsEmu(ppt: org.apache.poi.sl.usermodel.SlideShow<*, *>): Pair<Long, Long> {
        if (ppt is XMLSlideShow) {
            try {
                val ctPresentation = getXmlObjectReflection(ppt)
                    ?: try { ppt.javaClass.getMethod("getCTPresentation").invoke(ppt) } catch (t: Throwable) { null }
                if (ctPresentation != null) {
                    val sldSz = ctPresentation.javaClass.getMethod("getSldSz").invoke(ctPresentation)
                    if (sldSz != null) {
                        val cx = (sldSz.javaClass.getMethod("getCx").invoke(sldSz) as? Number)?.toLong() ?: 9144000L
                        val cy = (sldSz.javaClass.getMethod("getCy").invoke(sldSz) as? Number)?.toLong() ?: 5143500L
                        return Pair(cx, cy)
                    }
                }
            } catch (t: Throwable) {
                // Fall back to standard 16:9 widescreen in EMUs (10 inches x 5.625 inches)
            }
        }
        // Default: 9144000 x 5143500 (10 x 5.625 in = 16:9 standard PPTX)
        return Pair(9144000L, 5143500L)
    }

    private fun parseAllSlides(ppt: org.apache.poi.sl.usermodel.SlideShow<*, *>): List<PptxSlide> {
        val (slideWidthEmu, slideHeightEmu) = getSlideDimensionsEmu(ppt)
        val slides = mutableListOf<PptxSlide>()

        for ((index, slide) in ppt.slides.withIndex()) {
            val textBlocks = mutableListOf<PptxTextBlock>()
            val images = mutableListOf<PptxImage>()
            val bgColorHex = getSlideBgColorHex(slide)

            // Extract Speaker Notes safely
            val speakerNotes: String? = try {
                val notesObj = try { slide.notes } catch (t: Throwable) { null }
                if (notesObj != null) {
                    val shapes = try { notesObj.shapes } catch (t: Throwable) { emptyList() }
                    var noteText: String? = null
                    for (sh in shapes) {
                        if (sh is org.apache.poi.sl.usermodel.TextShape<*, *>) {
                            val t = try { sh.text } catch (t2: Throwable) { null }
                            if (!t.isNullOrBlank()) {
                                noteText = t
                                break
                            }
                        }
                    }
                    noteText
                } else null
            } catch (t: Throwable) {
                null
            }

            var titleBlock = PptxTextBlock(
                id = "title",
                text = "Slide ${index + 1}",
                fontSizePt = 24f,
                shapeLeft = 0.05f,
                shapeTop = 0.05f,
                shapeWidth = 0.9f,
                shapeHeight = 0.15f
            )
            var bodyCount = 0

            // Helper function to recursively flatten group shapes and collect all shapes
            val allShapes = mutableListOf<org.apache.poi.sl.usermodel.Shape<*, *>>()
            fun collectShapes(shapeList: List<org.apache.poi.sl.usermodel.Shape<*, *>>) {
                for (sh in shapeList) {
                    if (sh is org.apache.poi.sl.usermodel.GroupShape<*, *>) {
                        val nested = try { sh.shapes } catch (t: Throwable) { emptyList() }
                        collectShapes(nested)
                    } else {
                        allShapes.add(sh)
                    }
                }
            }
            val rootShapes = try { slide.shapes } catch (t: Throwable) { emptyList() }
            collectShapes(rootShapes)

            for (shape in allShapes) {
                try {
                    if (shape is org.apache.poi.sl.usermodel.TextShape<*, *>) {
                        val shapeText = try { shape.text ?: "" } catch (t: Throwable) { "" }
                        if (shapeText.isNotBlank()) {
                            val isTitle = try {
                                shape.placeholder == Placeholder.TITLE || shape.placeholder == Placeholder.CENTERED_TITLE
                            } catch (t: Throwable) {
                                shape.shapeName.lowercase().contains("title")
                            }

                            val bounds = getXmlShapeBoundsNormalized(shape, slideWidthEmu, slideHeightEmu)

                            if (isTitle) {
                                val firstParagraph = try { shape.textParagraphs.firstOrNull() } catch (t: Throwable) { null }
                                val firstRun = try { firstParagraph?.textRuns?.firstOrNull() } catch (t: Throwable) { null }
                                val isBold = try { firstRun?.isBold ?: false } catch (t: Throwable) { true }
                                val isItalic = try { firstRun?.isItalic ?: false } catch (t: Throwable) { false }
                                val isUnderline = try { firstRun?.isUnderlined ?: false } catch (t: Throwable) { false }
                                val colorHex = firstRun?.let { extractTextRunColorHex(it) }
                                val fSize = try { firstRun?.fontSize } catch (t: Throwable) { null }
                                val fontSizePt = if (fSize != null && fSize > 0) fSize.toFloat() else 24f

                                val alignment = try {
                                    when (firstParagraph?.textAlign) {
                                        org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER -> "CENTER"
                                        org.apache.poi.sl.usermodel.TextParagraph.TextAlign.RIGHT -> "RIGHT"
                                        org.apache.poi.sl.usermodel.TextParagraph.TextAlign.JUSTIFY -> "JUSTIFY"
                                        else -> "LEFT"
                                    }
                                } catch (t: Throwable) { "LEFT" }

                                titleBlock = PptxTextBlock(
                                    id = "title",
                                    text = shapeText.trim(),
                                    isBold = isBold,
                                    isItalic = isItalic,
                                    isUnderline = isUnderline,
                                    textColorHex = colorHex,
                                    fontSizePt = fontSizePt,
                                    bulletLevel = 0,
                                    alignment = alignment,
                                    shapeLeft = bounds?.get(0) ?: 0.05f,
                                    shapeTop = bounds?.get(1) ?: 0.05f,
                                    shapeWidth = bounds?.get(2) ?: 0.9f,
                                    shapeHeight = bounds?.get(3) ?: 0.15f
                                )
                            } else {
                                // Extract each paragraph in the text box so bullet levels and formatting are preserved
                                val paragraphs = try { shape.textParagraphs } catch (t: Throwable) { emptyList() }
                                if (paragraphs.isNotEmpty()) {
                                    for (p in paragraphs) {
                                        val pRuns = try { p.textRuns } catch (t: Throwable) { emptyList() }
                                        val pText = pRuns.joinToString("") { run ->
                                            try { run.rawText ?: "" } catch (t: Throwable) { "" }
                                        }.ifBlank {
                                            try { p.toString().trim() } catch (t: Throwable) { "" }
                                        }

                                        if (pText.isNotBlank()) {
                                            val firstRun = pRuns.firstOrNull()
                                            val isBold = try { firstRun?.isBold ?: false } catch (t: Throwable) { false }
                                            val isItalic = try { firstRun?.isItalic ?: false } catch (t: Throwable) { false }
                                            val isUnderline = try { firstRun?.isUnderlined ?: false } catch (t: Throwable) { false }
                                            val colorHex = firstRun?.let { extractTextRunColorHex(it) }

                                            val fSize = try { firstRun?.fontSize } catch (t: Throwable) { null }
                                            val fontSizePt = if (fSize != null && fSize > 0) fSize.toFloat() else 15f
                                            val bulletLevel = try { p.indentLevel } catch (t: Throwable) { 0 }

                                            val alignment = try {
                                                when (p.textAlign) {
                                                    org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER -> "CENTER"
                                                    org.apache.poi.sl.usermodel.TextParagraph.TextAlign.RIGHT -> "RIGHT"
                                                    org.apache.poi.sl.usermodel.TextParagraph.TextAlign.JUSTIFY -> "JUSTIFY"
                                                    else -> "LEFT"
                                                }
                                            } catch (t: Throwable) { "LEFT" }

                                            val shapeLeft = bounds?.get(0) ?: 0.05f
                                            val shapeTop = bounds?.get(1) ?: (0.24f + bodyCount * 0.08f).coerceAtMost(0.85f)
                                            val shapeWidthVal = bounds?.get(2) ?: 0.9f
                                            val shapeHeightVal = bounds?.get(3) ?: 0.1f

                                            textBlocks.add(
                                                PptxTextBlock(
                                                    id = "body_$bodyCount",
                                                    text = pText.trim(),
                                                    isBold = isBold,
                                                    isItalic = isItalic,
                                                    isUnderline = isUnderline,
                                                    textColorHex = colorHex,
                                                    fontSizePt = fontSizePt,
                                                    bulletLevel = bulletLevel,
                                                    alignment = alignment,
                                                    shapeLeft = shapeLeft,
                                                    shapeTop = shapeTop,
                                                    shapeWidth = shapeWidthVal,
                                                    shapeHeight = shapeHeightVal
                                                )
                                            )
                                            bodyCount++
                                        }
                                    }
                                } else {
                                    textBlocks.add(
                                        PptxTextBlock(
                                            id = "body_$bodyCount",
                                            text = shapeText.trim(),
                                            fontSizePt = 15f,
                                            shapeLeft = bounds?.get(0) ?: 0.05f,
                                            shapeTop = bounds?.get(1) ?: (0.24f + bodyCount * 0.08f),
                                            shapeWidth = bounds?.get(2) ?: 0.9f,
                                            shapeHeight = bounds?.get(3) ?: 0.1f
                                        )
                                    )
                                    bodyCount++
                                }
                            }
                        }
                    } else if (shape is org.apache.poi.sl.usermodel.TableShape<*, *>) {
                        val numRows = try { shape.numberOfRows } catch (t: Throwable) { 0 }
                        val numCols = try { shape.numberOfColumns } catch (t: Throwable) { 0 }
                        for (r in 0 until numRows) {
                            for (c in 0 until numCols) {
                                val cell = try { shape.getCell(r, c) } catch (t: Throwable) { null } ?: continue
                                val text = try { cell.text ?: "" } catch (t: Throwable) { "" }
                                if (text.isNotBlank()) {
                                    val firstParagraph = try { cell.textParagraphs.firstOrNull() } catch (t: Throwable) { null }
                                    val firstRun = try { firstParagraph?.textRuns?.firstOrNull() } catch (t: Throwable) { null }

                                    val isBold = try { firstRun?.isBold ?: false } catch (t: Throwable) { false }
                                    val isItalic = try { firstRun?.isItalic ?: false } catch (t: Throwable) { false }
                                    val isUnderline = try { firstRun?.isUnderlined ?: false } catch (t: Throwable) { false }
                                    val colorHex = firstRun?.let { extractTextRunColorHex(it) }

                                    val fSize = try { firstRun?.fontSize } catch (t: Throwable) { null }
                                    val fontSizePt = if (fSize != null && fSize > 0) fSize.toFloat() else 14f
                                    val bulletLevel = try { firstParagraph?.indentLevel ?: 0 } catch (t: Throwable) { 0 }

                                    val cellLeft = (c.toFloat() / numCols.toFloat() * 0.9f + 0.05f).coerceIn(0f, 1f)
                                    val cellTop = (0.35f + r.toFloat() / numRows.toFloat() * 0.5f).coerceIn(0f, 1f)
                                    val cellW = (0.9f / numCols.toFloat()).coerceIn(0.05f, 1f)
                                    val cellH = (0.5f / numRows.toFloat()).coerceIn(0.05f, 1f)

                                    textBlocks.add(
                                        PptxTextBlock(
                                            id = "table_cell_${r}_${c}",
                                            text = text.trim(),
                                            isBold = isBold,
                                            isItalic = isItalic,
                                            isUnderline = isUnderline,
                                            textColorHex = colorHex,
                                            fontSizePt = fontSizePt,
                                            bulletLevel = bulletLevel,
                                            alignment = "LEFT",
                                            shapeLeft = cellLeft,
                                            shapeTop = cellTop,
                                            shapeWidth = cellW,
                                            shapeHeight = cellH
                                        )
                                    )
                                    bodyCount++
                                }
                            }
                        }
                    } else if (shape is org.apache.poi.sl.usermodel.PictureShape<*, *>) {
                        try {
                            val picData = shape.pictureData
                            val dataBytes = picData.data
                            if (dataBytes != null && dataBytes.isNotEmpty()) {
                                val hash = dataBytes.contentHashCode().toString()
                                val cacheKey = "${index}_$hash"
                                val cachedFile = tempImageCache[cacheKey]
                                val file = if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
                                    cachedFile
                                } else {
                                    val suggestExt = picData.contentType?.substringAfter("/")?.substringBefore("+")?.lowercase() ?: "png"
                                    val tempFile = File.createTempFile("pptx_img_", ".$suggestExt")
                                    tempFile.outputStream().use { it.write(dataBytes) }
                                    tempImageCache[cacheKey] = tempFile
                                    tempFile
                                }

                                val bounds = getXmlShapeBoundsNormalized(shape, slideWidthEmu, slideHeightEmu)
                                val left = bounds?.get(0) ?: 0.1f
                                val top = bounds?.get(1) ?: 0.3f
                                val width = bounds?.get(2) ?: 0.5f
                                val height = bounds?.get(3) ?: 0.4f

                                images.add(PptxImage(file.absolutePath, left, top, width, height))
                            }
                        } catch (t: Throwable) {
                            t.printStackTrace()
                        }
                    }
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }

            // Also check slide package relationship parts for any embedded pictures
            if (slide is XSLFSlide) {
                try {
                    val relParts = slide.relationParts
                    for (rp in relParts) {
                        val rel = try { rp.relationship } catch (t: Throwable) { null } ?: continue
                        if (rel.relationshipType.contains("image", ignoreCase = true)) {
                            val part = try {
                                val m = rp.javaClass.getMethod("getDocumentPart")
                                m.invoke(rp) as? org.apache.poi.ooxml.POIXMLDocumentPart
                            } catch (t: Throwable) {
                                try {
                                    val f = rp.javaClass.getDeclaredField("documentPart")
                                    f.isAccessible = true
                                    f.get(rp) as? org.apache.poi.ooxml.POIXMLDocumentPart
                                } catch (t2: Throwable) { null }
                            }
                            val bytes = try { part?.packagePart?.inputStream?.use { it.readBytes() } } catch (t: Throwable) { null }
                            if (bytes != null && bytes.isNotEmpty()) {
                                val hash = bytes.contentHashCode().toString()
                                val cacheKey = "${index}_rel_$hash"
                                if (!images.any { it.filePath.contains(hash) } && !tempImageCache.containsKey(cacheKey)) {
                                    val tempFile = File.createTempFile("pptx_img_rel_", ".png")
                                    tempFile.outputStream().use { it.write(bytes) }
                                    tempImageCache[cacheKey] = tempFile
                                    images.add(PptxImage(tempFile.absolutePath, 0.1f, 0.3f, 0.8f, 0.4f))
                                }
                            }
                        }
                    }
                } catch (t: Throwable) {}
            }

            slides.add(
                PptxSlide(
                    slideNumber = index + 1,
                    title = titleBlock,
                    textBlocks = textBlocks,
                    images = images,
                    speakerNotes = speakerNotes,
                    bgColorHex = bgColorHex
                )
            )
        }
        return slides
    }

    fun loadPptxFile(filePath: String) {
        viewModelScope.launch {
            _loadState.value = PptxLoadState.Loading
            withContext(Dispatchers.IO) {
                try {
                    activePresentation?.close()
                } catch (t: Throwable) {}
                activePresentation = null
                activeFilePath = null

                var fileInputStream: FileInputStream? = null
                var ppt: org.apache.poi.sl.usermodel.SlideShow<*, *>? = null
                try {
                    val file = File(filePath)
                    if (!file.exists() || !file.isFile) {
                        _loadState.value = PptxLoadState.Error("Target presentation does not exist or is corrupted.")
                        return@withContext
                    }

                    fileInputStream = FileInputStream(file)
                    ppt = if (filePath.endsWith(".ppt", ignoreCase = true)) {
                        org.apache.poi.hslf.usermodel.HSLFSlideShow(fileInputStream)
                    } else {
                        XMLSlideShow(fileInputStream)
                    }

                    val slides = parseAllSlides(ppt)

                    activePresentation = ppt
                    activeFilePath = filePath

                    _loadState.value = PptxLoadState.Success(
                        presentation = PptxPresentation(slides),
                        fileName = file.name
                    )

                } catch (e: Throwable) {
                    e.printStackTrace()
                    _loadState.value = PptxLoadState.Error("PowerPoint presentation parser failure: ${e.localizedMessage}")
                } finally {
                    try {
                        fileInputStream?.close()
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }
            }
        }
    }

    fun updateSlideTextShape(
        slideIndex: Int,
        isTitle: Boolean,
        blockIndex: Int,
        newText: String,
        isBold: Boolean = false,
        isItalic: Boolean = false,
        isUnderline: Boolean = false,
        textColorHex: String? = null,
        comment: String? = null,
        fontSizePt: Float = 18f
    ) {
        if (activePresentation is org.apache.poi.hslf.usermodel.HSLFSlideShow) {
            viewModelScope.launch {
                _saveStatus.emit("Editing is not supported for legacy PowerPoint (.ppt) documents. Please save as .pptx format to edit.")
            }
            return
        }
        val ppt = activePresentation as? XMLSlideShow ?: return
        val slides = ppt.slides
        if (slideIndex in slides.indices) {
            val slide = slides[slideIndex]
            var blockIdx = 0

            for (shape in slide.shapes) {
                if (shape is XSLFTextShape) {
                    val isShapeTitle = shape.isPlaceholder && (shape.textType == Placeholder.TITLE || shape.textType == Placeholder.CENTERED_TITLE)
                    val targetMatch = if (isTitle) isShapeTitle else (!isShapeTitle && blockIdx == blockIndex)

                    if (targetMatch) {
                        val paras = shape.textParagraphs
                        if (paras.isNotEmpty()) {
                            val p = paras[0]
                            while (p.textRuns.size > 1) {
                                try {
                                    val rXml = getXmlObjectReflection(p.textRuns[1])
                                    val pXml = getXmlObjectReflection(p)
                                    val rListObj = pXml?.javaClass?.getMethod("getRList")?.invoke(pXml) as? MutableList<*>
                                    rListObj?.remove(rXml)
                                } catch (t: Throwable) {}
                            }

                            val r = p.textRuns.firstOrNull() ?: p.addNewTextRun()
                            r.setText(newText)
                            r.isBold = isBold
                            r.isItalic = isItalic
                            r.isUnderlined = isUnderline
                            setRunProperties(r, textColorHex, fontSizePt)

                            while (shape.textParagraphs.size > 1) {
                                try {
                                    val paraXml = getXmlObjectReflection(shape.textParagraphs[1])
                                    val shapeXml = getXmlObjectReflection(shape)
                                    val txBody = shapeXml?.javaClass?.getMethod("getTxBody")?.invoke(shapeXml)
                                    val pListObj = txBody?.javaClass?.getMethod("getPList")?.invoke(txBody) as? MutableList<*>
                                    pListObj?.remove(paraXml)
                                } catch (t: Throwable) {}
                            }
                        } else {
                            val p = shape.addNewTextParagraph()
                            val r = p.addNewTextRun()
                            r.setText(newText)
                            r.isBold = isBold
                            r.isItalic = isItalic
                            r.isUnderlined = isUnderline
                            setRunProperties(r, textColorHex, fontSizePt)
                        }
                        break
                    }
                    if (!isShapeTitle) {
                        blockIdx++
                    }
                }
            }

            if (comment != null) {
                try {
                    val notesObj = try {
                        slide.javaClass.getMethod("getNotes").invoke(slide)
                            ?: slide.javaClass.getMethod("createNotes").invoke(slide)
                    } catch (t: Throwable) {
                        try { slide.javaClass.getMethod("createNotes").invoke(slide) } catch (t2: Throwable) { null }
                    }

                    if (notesObj != null) {
                        val shapesList = notesObj.javaClass.getMethod("getShapes").invoke(notesObj) as? List<*>
                        var notesShape = shapesList?.filterIsInstance<XSLFTextShape>()?.firstOrNull {
                            it.placeholder == Placeholder.BODY
                        } ?: shapesList?.filterIsInstance<XSLFTextShape>()?.firstOrNull()

                        if (notesShape == null) {
                            notesShape = notesObj.javaClass.getMethod("createTextBox").invoke(notesObj) as? XSLFTextShape
                            try {
                                notesShape?.javaClass?.getMethod("setPlaceholder", Placeholder::class.java)?.invoke(notesShape, Placeholder.BODY)
                            } catch (t: Throwable) {}
                        }
                        notesShape?.clearText()
                        notesShape?.setText(comment)
                    }
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }

            viewModelScope.launch {
                _loadState.value = PptxLoadState.Success(
                    presentation = PptxPresentation(parseAllSlides(ppt)),
                    fileName = File(activeFilePath!!).name
                )
            }
        }
    }

    fun setSlideBackground(slideIndex: Int, colorHex: String) {
        val ppt = activePresentation as? XMLSlideShow ?: return
        val slides = ppt.slides
        if (slideIndex in slides.indices) {
            val slide = slides[slideIndex]
            setSlideBgColorHex(slide, colorHex)
            viewModelScope.launch {
                _loadState.value = PptxLoadState.Success(
                    presentation = PptxPresentation(parseAllSlides(ppt)),
                    fileName = File(activeFilePath!!).name
                )
            }
        }
    }

    fun insertImageIntoSlide(slideIndex: Int, imagePath: String) {
        if (activePresentation is org.apache.poi.hslf.usermodel.HSLFSlideShow || activeFilePath?.endsWith(".ppt", ignoreCase = true) == true) {
            viewModelScope.launch {
                _saveStatus.emit("Editing is not supported for legacy PowerPoint (.ppt) documents. Please save as .pptx format to edit.")
            }
            return
        }
        val ppt = activePresentation as? XMLSlideShow ?: return
        val slides = ppt.slides
        if (slideIndex in slides.indices) {
            val slide = slides[slideIndex]
            try {
                val imageBytes = File(imagePath).readBytes()
                val pictureData = ppt.addPicture(imageBytes, org.apache.poi.sl.usermodel.PictureData.PictureType.PNG)
                val pictureShape = slide.createPicture(pictureData)

                val sp = getXmlObjectReflection(pictureShape)
                if (sp != null) {
                    val spPr = try {
                        sp.javaClass.getMethod("getSpPr").invoke(sp)
                            ?: sp.javaClass.getMethod("addNewSpPr").invoke(sp)
                    } catch (t: Throwable) {
                        try { sp.javaClass.getMethod("addNewSpPr").invoke(sp) } catch (t2: Throwable) { null }
                    }
                    if (spPr != null) {
                        val xfrm = try {
                            spPr.javaClass.getMethod("getXfrm").invoke(spPr)
                                ?: spPr.javaClass.getMethod("addNewXfrm").invoke(spPr)
                        } catch (t: Throwable) {
                            try { spPr.javaClass.getMethod("addNewXfrm").invoke(spPr) } catch (t2: Throwable) { null }
                        }
                        if (xfrm != null) {
                            val off = try {
                                xfrm.javaClass.getMethod("getOff").invoke(xfrm)
                                    ?: xfrm.javaClass.getMethod("addNewOff").invoke(xfrm)
                            } catch (t: Throwable) {
                                try { xfrm.javaClass.getMethod("addNewOff").invoke(xfrm) } catch (t2: Throwable) { null }
                            }
                            if (off != null) {
                                off.javaClass.getMethod("setX", Long::class.javaPrimitiveType).invoke(off, 914400L * 1)
                                off.javaClass.getMethod("setY", Long::class.javaPrimitiveType).invoke(off, 914400L * 1)
                            }
                            val ext = try {
                                xfrm.javaClass.getMethod("getExt").invoke(xfrm)
                                    ?: xfrm.javaClass.getMethod("addNewExt").invoke(xfrm)
                            } catch (t: Throwable) {
                                try { xfrm.javaClass.getMethod("addNewExt").invoke(xfrm) } catch (t2: Throwable) { null }
                            }
                            if (ext != null) {
                                ext.javaClass.getMethod("setCx", Long::class.javaPrimitiveType).invoke(ext, 914400L * 5)
                                ext.javaClass.getMethod("setCy", Long::class.javaPrimitiveType).invoke(ext, 914400L * 4)
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }

            viewModelScope.launch {
                _loadState.value = PptxLoadState.Success(
                    presentation = PptxPresentation(parseAllSlides(ppt)),
                    fileName = File(activeFilePath!!).name
                )
            }
        }
    }

    fun addSlide(afterIndex: Int) {
        if (activePresentation is org.apache.poi.hslf.usermodel.HSLFSlideShow) {
            viewModelScope.launch {
                _saveStatus.emit("Editing is not supported for legacy PowerPoint (.ppt) documents. Please save as .pptx format to edit.")
            }
            return
        }
        val ppt = activePresentation as? XMLSlideShow ?: return
        val layoutsList = try {
            ppt.javaClass.getMethod("getSlideLayouts").invoke(ppt) as? List<*>
        } catch (t: Throwable) {
            null
        }
        val layout = layoutsList?.firstOrNull() ?: return
        try {
            val newSlide = ppt.javaClass.getMethod("createSlide", layout.javaClass).invoke(ppt, layout)
            ppt.setSlideOrder(newSlide as? XSLFSlide, (afterIndex + 1).coerceIn(0, ppt.slides.size))
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        viewModelScope.launch {
            _loadState.value = PptxLoadState.Success(
                presentation = PptxPresentation(parseAllSlides(ppt)),
                fileName = File(activeFilePath!!).name
            )
        }
    }

    fun deleteSlide(index: Int) {
        if (activePresentation is org.apache.poi.hslf.usermodel.HSLFSlideShow) {
            viewModelScope.launch {
                _saveStatus.emit("Editing is not supported for legacy PowerPoint (.ppt) documents. Please save as .pptx format to edit.")
            }
            return
        }
        val ppt = activePresentation as? XMLSlideShow ?: return
        val slides = ppt.slides
        if (index in slides.indices) {
            try {
                ppt.removeSlide(index)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
            viewModelScope.launch {
                _loadState.value = PptxLoadState.Success(
                    presentation = PptxPresentation(parseAllSlides(ppt)),
                    fileName = File(activeFilePath!!).name
                )
            }
        }
    }

    fun duplicateSlide(index: Int) {
        if (activePresentation is org.apache.poi.hslf.usermodel.HSLFSlideShow) {
            viewModelScope.launch {
                _saveStatus.emit("Editing is not supported for legacy PowerPoint (.ppt) documents. Please save as .pptx format to edit.")
            }
            return
        }
        val ppt = activePresentation as? XMLSlideShow ?: return
        val slides = ppt.slides
        if (index in slides.indices) {
            try {
                val originalSlide = slides[index]
                val layout = originalSlide.slideLayout
                val newSlide = ppt.javaClass.getMethod("createSlide", layout.javaClass).invoke(ppt, layout) as XSLFSlide

                val originalBgColor = getSlideBgColorHex(originalSlide)
                if (originalBgColor != null) {
                    setSlideBgColorHex(newSlide, originalBgColor)
                }

                originalSlide.shapes.forEach { shape ->
                    try {
                        if (shape is XSLFTextShape) {
                            val newShape = newSlide.createTextBox()
                            newShape.clearText()
                            newShape.setText(shape.text)
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }

            viewModelScope.launch {
                _loadState.value = PptxLoadState.Success(
                    presentation = PptxPresentation(parseAllSlides(ppt)),
                    fileName = File(activeFilePath!!).name
                )
            }
        }
    }

    fun commitChanges() {
        viewModelScope.launch {
            if (activePresentation is org.apache.poi.hslf.usermodel.HSLFSlideShow) {
                _saveStatus.emit("Editing is not supported for legacy PowerPoint (.ppt) documents. Please save as .pptx format to edit.")
                return@launch
            }
            val ppt = activePresentation
            val filePath = activeFilePath
            if (ppt == null || filePath == null) {
                _saveStatus.emit("No active presentation loaded.")
                return@launch
            }

            withContext(Dispatchers.IO) {
                var fileOutputStream: FileOutputStream? = null
                try {
                    fileOutputStream = FileOutputStream(File(filePath))
                    ppt.write(fileOutputStream)
                    _saveStatus.emit("Presentation changes committed successfully!")
                } catch (t: Throwable) {
                    t.printStackTrace()
                    _saveStatus.emit("Failed to save changes: ${t.localizedMessage}")
                } finally {
                    try {
                        fileOutputStream?.close()
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _currentMatchIndex.value = -1
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val path = activeFilePath ?: return@withContext
                val results = DocumentSearchEngine.searchPptx(path, query)
                _searchResults.value = results
                if (results.isNotEmpty()) {
                    _currentMatchIndex.value = 0
                } else {
                    _currentMatchIndex.value = -1
                }
            }
        }
    }

    fun nextMatch() {
        val results = _searchResults.value
        if (results.isEmpty()) return
        val nextIndex = (_currentMatchIndex.value + 1) % results.size
        _currentMatchIndex.value = nextIndex
    }

    fun prevMatch() {
        val results = _searchResults.value
        if (results.isEmpty()) return
        val prevIndex = (_currentMatchIndex.value - 1 + results.size) % results.size
        _currentMatchIndex.value = prevIndex
    }

    override fun onCleared() {
        super.onCleared()
        try {
            activePresentation?.close()
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        // Clean up temporary image files
        tempImageCache.values.forEach { file ->
            try { if (file.exists()) file.delete() } catch (t: Throwable) {}
        }
        tempImageCache.clear()
    }
}