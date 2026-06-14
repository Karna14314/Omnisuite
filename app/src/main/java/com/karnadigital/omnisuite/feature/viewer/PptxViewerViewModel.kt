package com.karnadigital.omnisuite.feature.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.core.repository.RecentFileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.apache.poi.xslf.usermodel.XSLFTextRun
import org.apache.poi.sl.usermodel.Placeholder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import com.karnadigital.omnisuite.core.engine.SearchResult
import com.karnadigital.omnisuite.core.engine.DocumentSearchEngine

data class PptxTextBlock(
    val id: String,
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val textColorHex: String? = null,
    val fontSizePt: Float = 18f,
    val bulletLevel: Int = 0,
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

    private val _saveStatus = kotlinx.coroutines.flow.MutableSharedFlow<String>()
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
        } catch (e: Exception) {
            try {
                val method = obj.javaClass.getDeclaredMethod("fetchXmlObject")
                method.isAccessible = true
                method.invoke(obj)
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun getShapeAnchor(shape: Any): android.graphics.RectF? {
        return try {
            val anchorObj = shape.javaClass.getMethod("getAnchor").invoke(shape) ?: return null
            val x = (anchorObj.javaClass.getMethod("getX").invoke(anchorObj) as Number).toFloat()
            val y = (anchorObj.javaClass.getMethod("getY").invoke(anchorObj) as Number).toFloat()
            val w = (anchorObj.javaClass.getMethod("getWidth").invoke(anchorObj) as Number).toFloat()
            val h = (anchorObj.javaClass.getMethod("getHeight").invoke(anchorObj) as Number).toFloat()
            android.graphics.RectF(x, y, x + w, y + h)
        } catch (e: Exception) {
            null
        }
    }

    private fun getSlideBgColorHex(slide: org.apache.poi.sl.usermodel.Slide<*, *>): String? {
        // HSLF (.ppt legacy) paths call ColorStyle.getColor() which returns java.awt.Color,
        // a Java SE class absent on Android → NoClassDefFoundError. Skip color for HSLF slides.
        if (slide is org.apache.poi.hslf.usermodel.HSLFSlide) return null
        try {
            val bg = slide.background ?: return null
            val fillStyle = bg.fillStyle ?: return null
            val paint = fillStyle.paint
            if (paint is org.apache.poi.sl.usermodel.PaintStyle.SolidPaint) {
                val colorStyle = paint.solidColor
                val color = colorStyle.javaClass.getMethod("getColor").invoke(colorStyle) ?: return null
                val red = color.javaClass.getMethod("getRed").invoke(color) as Int
                val green = color.javaClass.getMethod("getGreen").invoke(color) as Int
                val blue = color.javaClass.getMethod("getBlue").invoke(color) as Int
                return String.format("#%02X%02X%02X", red, green, blue)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun XSLFTextRun.extractColorHex(): String? {
        return try {
            val xmlRun = this.xmlObject ?: return null
            val rPr = try { xmlRun.javaClass.getMethod("getRPr").invoke(xmlRun) } catch (e: Exception) { null } ?: return null
            val solidFill = try { rPr.javaClass.getMethod("getSolidFill").invoke(rPr) } catch (e: Exception) { null } ?: return null
            val srgb = try { solidFill.javaClass.getMethod("getSrgbClr").invoke(solidFill) } catch(e: Exception) { null }
            if (srgb != null) {
                val hexBytes = try { srgb.javaClass.getMethod("getVal").invoke(srgb) as? ByteArray } catch (e: Exception) { null }
                val hex = hexBytes?.let {
                    it.map { b -> String.format("%02X", b) }.joinToString("")
                }
                hex?.let { "#$it" }
            } else {
                null
            }
        } catch (e: Exception) { null }
    }

    private fun extractTextRunColorHex(run: org.apache.poi.sl.usermodel.TextRun): String? {
        // HSLF runs resolve to java.awt.Color on getColor() — not available on Android.
        // Use the safe XSLF XML path only; return null for all HSLF text runs.
        if (run is org.apache.poi.hslf.usermodel.HSLFTextRun) return null
        if (run is XSLFTextRun) {
            return run.extractColorHex()
        }
        // Generic non-HSLF SolidPaint fallback via reflection
        try {
            val paint = run.fontColor
            if (paint is org.apache.poi.sl.usermodel.PaintStyle.SolidPaint) {
                val colorStyle = paint.solidColor
                val color = colorStyle.javaClass.getMethod("getColor").invoke(colorStyle) ?: return null
                val red = color.javaClass.getMethod("getRed").invoke(color) as Int
                val green = color.javaClass.getMethod("getGreen").invoke(color) as Int
                val blue = color.javaClass.getMethod("getBlue").invoke(color) as Int
                return String.format("#%02X%02X%02X", red, green, blue)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun setRunProperties(r: XSLFTextRun, textColorHex: String?, fontSizePt: Float) {
        try {
            r.fontSize = fontSizePt.toDouble()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        val xmlRun = getXmlObjectReflection(r) ?: return
        try {
            val rPr = try {
                xmlRun.javaClass.getMethod("getRPr").invoke(xmlRun)
                    ?: xmlRun.javaClass.getMethod("addNewRPr").invoke(xmlRun)
            } catch (e: Exception) {
                try { xmlRun.javaClass.getMethod("addNewRPr").invoke(xmlRun) } catch (e2: Exception) { null }
            }
            
            if (rPr != null) {
                if (textColorHex != null) {
                    try { rPr.javaClass.getMethod("unsetSolidFill").invoke(rPr) } catch (e: Exception) {}
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
                    try { rPr.javaClass.getMethod("unsetSolidFill").invoke(rPr) } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setSlideBgColorHex(slide: org.apache.poi.sl.usermodel.Slide<*, *>, colorHex: String) {
        if (slide is org.apache.poi.xslf.usermodel.XSLFSlide) {
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
                    } catch (e: Exception) {
                        try { ctSlide.javaClass.getMethod("addNewCSld").invoke(ctSlide) } catch (e2: Exception) { null }
                    }
                    if (cSld != null) {
                        val bg = try {
                            cSld.javaClass.getMethod("getBg").invoke(cSld)
                                ?: cSld.javaClass.getMethod("addNewBg").invoke(cSld)
                        } catch (e: Exception) {
                            try { cSld.javaClass.getMethod("addNewBg").invoke(cSld) } catch (e2: Exception) { null }
                        }
                        if (bg != null) {
                            try {
                                val isSetBgPr = bg.javaClass.getMethod("isSetBgPr").invoke(bg) as Boolean
                                if (isSetBgPr) {
                                    val bgPr = bg.javaClass.getMethod("getBgPr").invoke(bg)
                                    if (bgPr != null) {
                                        val unsetMethods = listOf("unsetSolidFill", "unsetGradFill", "unsetBlipFill", "unsetPattFill", "unsetGrpFill")
                                        unsetMethods.forEach { m ->
                                            try { bgPr.javaClass.getMethod(m).invoke(bgPr) } catch (e: Exception) {}
                                        }
                                    }
                                }
                            } catch (e: Exception) {}
                            val bgPr = try {
                                bg.javaClass.getMethod("getBgPr").invoke(bg)
                                    ?: bg.javaClass.getMethod("addNewBgPr").invoke(bg)
                            } catch (e: Exception) {
                                try { bg.javaClass.getMethod("addNewBgPr").invoke(bg) } catch (e2: Exception) { null }
                            }
                            if (bgPr != null) {
                                val solidFill = bgPr.javaClass.getMethod("addNewSolidFill").invoke(bgPr)
                                val srgbClr = solidFill.javaClass.getMethod("addNewSrgbClr").invoke(solidFill)
                                srgbClr.javaClass.getMethod("setVal", ByteArray::class.java).invoke(srgbClr, rgbBytes)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun parseAllSlides(ppt: org.apache.poi.sl.usermodel.SlideShow<*, *>, filePath: String): List<PptxSlide> {
        val pageSizeObj = try { ppt.javaClass.getMethod("getPageSize").invoke(ppt) } catch(e: Exception) { null }
        val slideWidth = if (pageSizeObj != null) {
            try { (pageSizeObj.javaClass.getMethod("getWidth").invoke(pageSizeObj) as Number).toDouble() } catch(e: Exception) { 720.0 }
        } else 720.0
        val slideHeight = if (pageSizeObj != null) {
            try { (pageSizeObj.javaClass.getMethod("getHeight").invoke(pageSizeObj) as Number).toDouble() } catch(e: Exception) { 540.0 }
        } else 540.0

        val slideWidthF = slideWidth.toFloat()
        val slideHeightF = slideHeight.toFloat()

        val slides = mutableListOf<PptxSlide>()
        for ((index, slide) in ppt.slides.withIndex()) {
            val textBlocks = mutableListOf<PptxTextBlock>()
            val images = mutableListOf<PptxImage>()
            val bgColorHex = getSlideBgColorHex(slide)
            
            val speakerNotes = try {
                val notesObj = slide.notes
                if (notesObj != null) {
                    val shapes = notesObj.shapes
                    shapes.filterIsInstance<org.apache.poi.sl.usermodel.TextShape<*, *>>().firstOrNull { 
                        it.placeholder == Placeholder.BODY 
                    }?.text ?: shapes.filterIsInstance<org.apache.poi.sl.usermodel.TextShape<*, *>>().firstOrNull()?.text ?: ""
                } else null
            } catch (e: Exception) {
                null
            }

            var titleBlock = PptxTextBlock("title", "Slide ${index + 1}", fontSizePt = 32f)
            var bodyCount = 0

            for (shape in slide.shapes) {
                if (shape is org.apache.poi.sl.usermodel.TextShape<*, *>) {
                    val text = shape.text ?: ""
                    if (text.isNotBlank()) {
                        val isTitle = try {
                            shape.placeholder == Placeholder.TITLE || shape.placeholder == Placeholder.CENTERED_TITLE
                        } catch (e: Throwable) {
                            shape.shapeName.lowercase().contains("title")
                        }
                        
                        val firstParagraph = shape.textParagraphs.firstOrNull()
                        val firstRun = firstParagraph?.textRuns?.firstOrNull()
                        
                        val isBold = firstRun?.isBold ?: false
                        val isItalic = firstRun?.isItalic ?: false
                        val isUnderline = firstRun?.isUnderlined ?: false
                        val colorHex = firstRun?.let { extractTextRunColorHex(it) }
                        
                        val fSize = firstRun?.fontSize
                        val fontSizePt = if (fSize != null && fSize > 0) fSize.toFloat() else (if (isTitle) 32f else 18f)
                        val bulletLevel = firstParagraph?.indentLevel ?: 0
                        
                        val anchor = getShapeAnchor(shape)
                        val shapeLeft = if (anchor != null && slideWidthF > 0f) anchor.left / slideWidthF else 0f
                        val shapeTop = if (anchor != null && slideHeightF > 0f) anchor.top / slideHeightF else 0f
                        val shapeWidthVal = if (anchor != null && slideWidthF > 0f) anchor.width() / slideWidthF else 1f
                        val shapeHeightVal = if (anchor != null && slideHeightF > 0f) anchor.height() / slideHeightF else 0.1f

                        if (isTitle) {
                            titleBlock = PptxTextBlock(
                                id = "title",
                                text = text,
                                isBold = isBold,
                                isItalic = isItalic,
                                isUnderline = isUnderline,
                                textColorHex = colorHex,
                                fontSizePt = fontSizePt,
                                bulletLevel = bulletLevel,
                                shapeLeft = shapeLeft,
                                shapeTop = shapeTop,
                                shapeWidth = shapeWidthVal,
                                shapeHeight = shapeHeightVal
                            )
                        } else {
                            textBlocks.add(
                                PptxTextBlock(
                                    id = "body_$bodyCount",
                                    text = text,
                                    isBold = isBold,
                                    isItalic = isItalic,
                                    isUnderline = isUnderline,
                                    textColorHex = colorHex,
                                    fontSizePt = fontSizePt,
                                    bulletLevel = bulletLevel,
                                    shapeLeft = shapeLeft,
                                    shapeTop = shapeTop,
                                    shapeWidth = shapeWidthVal,
                                    shapeHeight = shapeHeightVal
                                )
                            )
                            bodyCount++
                        }
                    }
                } else if (shape is org.apache.poi.sl.usermodel.TableShape<*, *>) {
                    val numRows = shape.numberOfRows
                    val numCols = shape.numberOfColumns
                    for (r in 0 until numRows) {
                        for (c in 0 until numCols) {
                            val cell = shape.getCell(r, c) ?: continue
                            val text = cell.text ?: ""
                            if (text.isNotBlank()) {
                                val firstParagraph = cell.textParagraphs.firstOrNull()
                                val firstRun = firstParagraph?.textRuns?.firstOrNull()

                                val isBold = firstRun?.isBold ?: false
                                val isItalic = firstRun?.isItalic ?: false
                                val isUnderline = firstRun?.isUnderlined ?: false
                                val colorHex = firstRun?.let { extractTextRunColorHex(it) }

                                val fSize = firstRun?.fontSize
                                val fontSizePt = if (fSize != null && fSize > 0) fSize.toFloat() else 18f
                                val bulletLevel = firstParagraph?.indentLevel ?: 0

                                val anchor = getShapeAnchor(cell)
                                val shapeLeft = if (anchor != null && slideWidthF > 0f) anchor.left / slideWidthF else 0f
                                val shapeTop = if (anchor != null && slideHeightF > 0f) anchor.top / slideHeightF else 0f
                                val shapeWidthVal = if (anchor != null && slideWidthF > 0f) anchor.width() / slideWidthF else 1f
                                val shapeHeightVal = if (anchor != null && slideHeightF > 0f) anchor.height() / slideHeightF else 0.1f

                                textBlocks.add(
                                    PptxTextBlock(
                                        id = "body_$bodyCount",
                                        text = text,
                                        isBold = isBold,
                                        isItalic = isItalic,
                                        isUnderline = isUnderline,
                                        textColorHex = colorHex,
                                        fontSizePt = fontSizePt,
                                        bulletLevel = bulletLevel,
                                        shapeLeft = shapeLeft,
                                        shapeTop = shapeTop,
                                        shapeWidth = shapeWidthVal,
                                        shapeHeight = shapeHeightVal
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
                        val hash = dataBytes.contentHashCode().toString()
                        val cacheKey = "${index}_$hash"
                        val cachedFile = tempImageCache[cacheKey]
                        val file = if (cachedFile != null && cachedFile.exists()) {
                            cachedFile
                        } else {
                            val suggestExt = picData.contentType?.substringAfter("/")?.substringBefore("+")?.lowercase() ?: "png"
                            val tempFile = File.createTempFile("pptx_img_", ".$suggestExt")
                            tempFile.writeBytes(dataBytes)
                            tempImageCache[cacheKey] = tempFile
                            tempFile
                        }
                        
                        val anchor = getShapeAnchor(shape)
                        val left = if (anchor != null && slideWidthF > 0f) anchor.left / slideWidthF else 0f
                        val top = if (anchor != null && slideHeightF > 0f) anchor.top / slideHeightF else 0f
                        val width = if (anchor != null && slideWidthF > 0f) anchor.width() / slideWidthF else 0.5f
                        val height = if (anchor != null && slideHeightF > 0f) anchor.height() / slideHeightF else 0.5f
                        
                        images.add(PptxImage(file.absolutePath, left, top, width, height))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
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
                } catch (e: Exception) {}
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

                    val slides = parseAllSlides(ppt, filePath)

                    activePresentation = ppt
                    activeFilePath = filePath

                    _loadState.value = PptxLoadState.Success(
                        presentation = PptxPresentation(slides),
                        fileName = file.name
                    )

                } catch (e: Throwable) {
                    e.printStackTrace()
                    _loadState.value = PptxLoadState.Error("Apache POI PowerPoint parser failure: ${e.localizedMessage}")
                } finally {
                    try {
                        fileInputStream?.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
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
                                } catch (e: Exception) {}
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
                                } catch (e: Exception) {}
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
                    } catch (e: Exception) {
                        try { slide.javaClass.getMethod("createNotes").invoke(slide) } catch (e2: Exception) { null }
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
                            } catch (e: Exception) {}
                        }
                        notesShape?.clearText()
                        notesShape?.setText(comment)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            viewModelScope.launch {
                _loadState.value = PptxLoadState.Success(
                    presentation = PptxPresentation(parseAllSlides(ppt, activeFilePath!!)),
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
                    } catch (e: Exception) {
                        try { sp.javaClass.getMethod("addNewSpPr").invoke(sp) } catch (e2: Exception) { null }
                    }
                    if (spPr != null) {
                        val xfrm = try {
                            spPr.javaClass.getMethod("getXfrm").invoke(spPr)
                                ?: spPr.javaClass.getMethod("addNewXfrm").invoke(spPr)
                        } catch (e: Exception) {
                            try { spPr.javaClass.getMethod("addNewXfrm").invoke(spPr) } catch (e2: Exception) { null }
                        }
                        if (xfrm != null) {
                            val off = try {
                                xfrm.javaClass.getMethod("getOff").invoke(xfrm)
                                    ?: xfrm.javaClass.getMethod("addNewOff").invoke(xfrm)
                            } catch (e: Exception) {
                                try { xfrm.javaClass.getMethod("addNewOff").invoke(xfrm) } catch (e2: Exception) { null }
                            }
                            if (off != null) {
                                off.javaClass.getMethod("setX", Long::class.javaPrimitiveType).invoke(off, 914400L * 1)
                                off.javaClass.getMethod("setY", Long::class.javaPrimitiveType).invoke(off, 914400L * 1)
                            }
                            val ext = try {
                                xfrm.javaClass.getMethod("getExt").invoke(xfrm)
                                    ?: xfrm.javaClass.getMethod("addNewExt").invoke(xfrm)
                            } catch (e: Exception) {
                                try { xfrm.javaClass.getMethod("addNewExt").invoke(xfrm) } catch (e2: Exception) { null }
                            }
                            if (ext != null) {
                                ext.javaClass.getMethod("setCx", Long::class.javaPrimitiveType).invoke(ext, 914400L * 5)
                                ext.javaClass.getMethod("setCy", Long::class.javaPrimitiveType).invoke(ext, 914400L * 4)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            viewModelScope.launch {
                _loadState.value = PptxLoadState.Success(
                    presentation = PptxPresentation(parseAllSlides(ppt, activeFilePath!!)),
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
        } catch (e: Exception) {
            null
        }
        val layout = layoutsList?.firstOrNull() ?: return
        try {
            val newSlide = ppt.javaClass.getMethod("createSlide", layout.javaClass).invoke(ppt, layout)
            ppt.setSlideOrder(newSlide as? org.apache.poi.xslf.usermodel.XSLFSlide, (afterIndex + 1).coerceIn(0, ppt.slides.size))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        viewModelScope.launch {
            _loadState.value = PptxLoadState.Success(
                presentation = PptxPresentation(parseAllSlides(ppt, activeFilePath!!)),
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
            viewModelScope.launch {
                _loadState.value = PptxLoadState.Success(
                    presentation = PptxPresentation(parseAllSlides(ppt, activeFilePath!!)),
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
                val newSlide = ppt.javaClass.getMethod("createSlide", layout.javaClass).invoke(ppt, layout) as org.apache.poi.xslf.usermodel.XSLFSlide
                
                val originalBgColor = getSlideBgColorHex(originalSlide)
                if (originalBgColor != null) {
                    setSlideBgColorHex(newSlide, originalBgColor)
                }

                try {
                    val origNotesObj = originalSlide.javaClass.getMethod("getNotes").invoke(originalSlide)
                    if (origNotesObj != null) {
                        val bodyShape = (origNotesObj.javaClass.getMethod("getShapes").invoke(origNotesObj) as? List<*>)
                            ?.filterIsInstance<XSLFTextShape>()?.firstOrNull { 
                                it.placeholder == Placeholder.BODY 
                            } ?: (origNotesObj.javaClass.getMethod("getShapes").invoke(origNotesObj) as? List<*>)
                            ?.filterIsInstance<XSLFTextShape>()?.firstOrNull()
                        val origText = bodyShape?.text
                        if (!origText.isNullOrBlank()) {
                            val newNotesObj = newSlide.javaClass.getMethod("createNotes").invoke(newSlide)
                            if (newNotesObj != null) {
                                val shapesList = newNotesObj.javaClass.getMethod("getShapes").invoke(newNotesObj) as? List<*>
                                var notesShape = shapesList?.filterIsInstance<XSLFTextShape>()?.firstOrNull { 
                                    it.placeholder == Placeholder.BODY 
                                } ?: shapesList?.filterIsInstance<XSLFTextShape>()?.firstOrNull()
                                if (notesShape == null) {
                                    notesShape = newNotesObj.javaClass.getMethod("createTextBox").invoke(newNotesObj) as? XSLFTextShape
                                }
                                notesShape?.clearText()
                                notesShape?.setText(origText)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                originalSlide.shapes.forEach { shape ->
                    try {
                        if (shape is XSLFTextShape) {
                            val newShape = newSlide.createTextBox()
                            newShape.clearText()
                            
                            val origAnchor = getShapeAnchor(shape)
                            if (origAnchor != null) {
                                val sp = getXmlObjectReflection(newShape)
                                if (sp != null) {
                                    val spPr = try {
                                        sp.javaClass.getMethod("getSpPr").invoke(sp)
                                            ?: sp.javaClass.getMethod("addNewSpPr").invoke(sp)
                                    } catch (e: Exception) {
                                        try { sp.javaClass.getMethod("addNewSpPr").invoke(sp) } catch (e2: Exception) { null }
                                    }
                                    if (spPr != null) {
                                        val xfrm = try {
                                            spPr.javaClass.getMethod("getXfrm").invoke(spPr)
                                                ?: spPr.javaClass.getMethod("addNewXfrm").invoke(spPr)
                                        } catch (e: Exception) {
                                            try { spPr.javaClass.getMethod("addNewXfrm").invoke(spPr) } catch (e2: Exception) { null }
                                        }
                                        if (xfrm != null) {
                                            val off = try {
                                                xfrm.javaClass.getMethod("getOff").invoke(xfrm)
                                                    ?: xfrm.javaClass.getMethod("addNewOff").invoke(xfrm)
                                            } catch (e: Exception) {
                                                try { xfrm.javaClass.getMethod("addNewOff").invoke(xfrm) } catch (e2: Exception) { null }
                                            }
                                            if (off != null) {
                                                off.javaClass.getMethod("setX", Long::class.javaPrimitiveType).invoke(off, (origAnchor.left * 914400L / 72).toLong())
                                                off.javaClass.getMethod("setY", Long::class.javaPrimitiveType).invoke(off, (origAnchor.top * 914400L / 72).toLong())
                                            }
                                            val ext = try {
                                                xfrm.javaClass.getMethod("getExt").invoke(xfrm)
                                                    ?: xfrm.javaClass.getMethod("addNewExt").invoke(xfrm)
                                            } catch (e: Exception) {
                                                try { xfrm.javaClass.getMethod("addNewExt").invoke(xfrm) } catch (e2: Exception) { null }
                                            }
                                            if (ext != null) {
                                                ext.javaClass.getMethod("setCx", Long::class.javaPrimitiveType).invoke(ext, (origAnchor.width() * 914400L / 72).toLong())
                                                ext.javaClass.getMethod("setCy", Long::class.javaPrimitiveType).invoke(ext, (origAnchor.height() * 914400L / 72).toLong())
                                            }
                                        }
                                    }
                                }
                            }
                            
                            shape.textParagraphs.forEach { origPara ->
                                val newPara = newShape.addNewTextParagraph()
                                newPara.indentLevel = origPara.indentLevel
                                origPara.textRuns.forEach { origRun ->
                                    val newRun = newPara.addNewTextRun()
                                    newRun.setText(origRun.rawText)
                                    newRun.isBold = origRun.isBold
                                    newRun.isItalic = origRun.isItalic
                                    newRun.isUnderlined = origRun.isUnderlined
                                    setRunProperties(newRun, origRun.extractColorHex(), origRun.fontSize.toFloat())
                                }
                            }
                        } else if (shape.javaClass.simpleName.contains("Picture")) {
                            val picDataObj = shape.javaClass.getMethod("getPictureData").invoke(shape)
                            if (picDataObj != null) {
                                val dataBytes = picDataObj.javaClass.getMethod("getData").invoke(picDataObj) as ByteArray
                                val suggestExt = picDataObj.javaClass.getMethod("suggestFileExtension").invoke(picDataObj) as? String ?: "png"
                                val typeEnum = when (suggestExt.lowercase()) {
                                    "jpg", "jpeg" -> org.apache.poi.sl.usermodel.PictureData.PictureType.JPEG
                                    else -> org.apache.poi.sl.usermodel.PictureData.PictureType.PNG
                                }
                                val pictureData = ppt.addPicture(dataBytes, typeEnum)
                                val pictureShape = newSlide.createPicture(pictureData)
                                
                                val origAnchor = getShapeAnchor(shape)
                                if (origAnchor != null) {
                                    val sp = getXmlObjectReflection(pictureShape)
                                    if (sp != null) {
                                        val spPr = try {
                                            sp.javaClass.getMethod("getSpPr").invoke(sp)
                                                ?: sp.javaClass.getMethod("addNewSpPr").invoke(sp)
                                        } catch (e: Exception) {
                                            try { sp.javaClass.getMethod("addNewSpPr").invoke(sp) } catch (e2: Exception) { null }
                                        }
                                        if (spPr != null) {
                                            val xfrm = try {
                                                spPr.javaClass.getMethod("getXfrm").invoke(spPr)
                                                    ?: spPr.javaClass.getMethod("addNewXfrm").invoke(spPr)
                                            } catch (e: Exception) {
                                                try { spPr.javaClass.getMethod("addNewXfrm").invoke(spPr) } catch (e2: Exception) { null }
                                            }
                                            if (xfrm != null) {
                                                val off = try {
                                                    xfrm.javaClass.getMethod("getOff").invoke(xfrm)
                                                        ?: xfrm.javaClass.getMethod("addNewOff").invoke(xfrm)
                                                } catch (e: Exception) {
                                                    try { xfrm.javaClass.getMethod("addNewOff").invoke(xfrm) } catch (e2: Exception) { null }
                                                }
                                                if (off != null) {
                                                    off.javaClass.getMethod("setX", Long::class.javaPrimitiveType).invoke(off, (origAnchor.left * 914400L / 72).toLong())
                                                    off.javaClass.getMethod("setY", Long::class.javaPrimitiveType).invoke(off, (origAnchor.top * 914400L / 72).toLong())
                                                }
                                                val ext = try {
                                                    xfrm.javaClass.getMethod("getExt").invoke(xfrm)
                                                        ?: xfrm.javaClass.getMethod("addNewExt").invoke(xfrm)
                                                } catch (e: Exception) {
                                                    try { xfrm.javaClass.getMethod("addNewExt").invoke(xfrm) } catch (e2: Exception) { null }
                                                }
                                                if (ext != null) {
                                                    ext.javaClass.getMethod("setCx", Long::class.javaPrimitiveType).invoke(ext, (origAnchor.width() * 914400L / 72).toLong())
                                                    ext.javaClass.getMethod("setCy", Long::class.javaPrimitiveType).invoke(ext, (origAnchor.height() * 914400L / 72).toLong())
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                ppt.setSlideOrder(newSlide, index + 1)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            viewModelScope.launch {
                _loadState.value = PptxLoadState.Success(
                    presentation = PptxPresentation(parseAllSlides(ppt, activeFilePath!!)),
                    fileName = File(activeFilePath!!).name
                )
            }
        }
    }

    fun setSlideBackground(slideIndex: Int, colorHex: String) {
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
            setSlideBgColorHex(slide, colorHex)
            viewModelScope.launch {
                _loadState.value = PptxLoadState.Success(
                    presentation = PptxPresentation(parseAllSlides(ppt, activeFilePath!!)),
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
                    _saveStatus.emit("PowerPoint changes committed successfully!")
                } catch (e: Exception) {
                    e.printStackTrace()
                    _saveStatus.emit("Failed to save changes: ${e.localizedMessage}")
                } finally {
                    try {
                        fileOutputStream?.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
        tempImageCache.values.forEach { file ->
            try {
                if (file.exists()) file.delete()
            } catch (e: Exception) {}
        }
        tempImageCache.clear()
    }
}