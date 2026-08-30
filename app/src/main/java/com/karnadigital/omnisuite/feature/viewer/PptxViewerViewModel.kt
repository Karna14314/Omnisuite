package com.karnadigital.omnisuite.feature.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karnadigital.omnisuite.core.engine.SearchResult
import com.karnadigital.omnisuite.core.engine.document.PptxShapeExtractor
import com.karnadigital.omnisuite.core.engine.document.PptxSlideRenderer
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.sl.usermodel.Placeholder
import org.apache.poi.sl.usermodel.SlideShow
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.poi.xslf.usermodel.XSLFTextRun
import org.apache.poi.xslf.usermodel.XSLFTextShape
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject

data class PptxTextRun(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val textColorHex: String? = null,
    val fontSizePt: Float = 14f
)

data class PptxParagraph(
    val runs: List<PptxTextRun> = emptyList(),
    val bulletLevel: Int = 0,
    val hasBullet: Boolean = false,
    val bulletChar: String = "",
    val alignment: String = "LEFT"
) {
    val fullText: String get() = runs.joinToString("") { it.text }
}

data class PptxTextShape(
    val id: String,
    val isTitle: Boolean = false,
    val paragraphs: List<PptxParagraph> = emptyList(),
    val shapeLeft: Float = 0.05f,
    val shapeTop: Float = 0.05f,
    val shapeWidth: Float = 0.9f,
    val shapeHeight: Float = 0.15f,
    val backgroundColorHex: String? = null
) {
    val fullText: String get() = paragraphs.joinToString("\n") { it.fullText }
    val primaryText: String get() = paragraphs.firstOrNull()?.fullText ?: ""
    val isBold: Boolean get() = paragraphs.firstOrNull()?.runs?.firstOrNull()?.isBold ?: isTitle
    val isItalic: Boolean get() = paragraphs.firstOrNull()?.runs?.firstOrNull()?.isItalic ?: false
    val isUnderline: Boolean get() = paragraphs.firstOrNull()?.runs?.firstOrNull()?.isUnderline ?: false
    val textColorHex: String? get() = paragraphs.firstOrNull()?.runs?.firstOrNull()?.textColorHex
    val fontSizePt: Float get() = paragraphs.firstOrNull()?.runs?.firstOrNull()?.fontSizePt ?: (if (isTitle) 24f else 14f)
}

data class PptxImage(
    val filePath: String,
    val left: Float = 0f,
    val top: Float = 0f,
    val width: Float = 1f,
    val height: Float = 1f
)

data class PptxSlide(
    val slideNumber: Int,
    val title: PptxTextShape = PptxTextShape(id = "title", isTitle = true),
    val textShapes: List<PptxTextShape> = emptyList(),
    val images: List<PptxImage> = emptyList(),
    val backgroundImage: PptxImage? = null,
    val speakerNotes: String? = null,
    val bgColorHex: String? = null,
    val aspectRatio: Float = 16f / 9f,
    val imagePath: String? = null
)

data class PptxPresentation(
    val slides: List<PptxSlide>,
    val slideWidthEmu: Long = 9144000L,
    val slideHeightEmu: Long = 5143500L
)

sealed class PptxLoadState {
    object Loading : PptxLoadState()
    data class Success(
        val presentation: PptxPresentation,
        val fileName: String,
        val slideRenderPaths: List<String?>
    ) : PptxLoadState()
    data class Error(val message: String) : PptxLoadState()
}

@HiltViewModel
class PptxViewerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val officeConverter: com.karnadigital.omnisuite.core.engine.document.OfficeConverter
) : ViewModel() {

    private val _loadState = MutableStateFlow<PptxLoadState>(PptxLoadState.Loading)
    val loadState: StateFlow<PptxLoadState> = _loadState.asStateFlow()

    private val _saveStatus = MutableSharedFlow<String>()
    val saveStatus = _saveStatus.asSharedFlow()

    private var activePresentation: SlideShow<*, *>? = null
    private var activeFilePath: String? = null
    private var activeWidthEmu: Long = 9144000L
    private var activeHeightEmu: Long = 5143500L

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _currentMatchIndex = MutableStateFlow(-1)
    val currentMatchIndex: StateFlow<Int> = _currentMatchIndex.asStateFlow()

    fun loadPptxFile(filePath: String) {
        viewModelScope.launch {
            _loadState.value = PptxLoadState.Loading
            withContext(Dispatchers.IO) {
                try {
                    activePresentation?.close()
                } catch (_: Throwable) { }
                activePresentation = null
                activeFilePath = null

                var fileInputStream: FileInputStream? = null
                try {
                    val file = File(filePath)
                    if (!file.exists() || !file.isFile) {
                        _loadState.value = PptxLoadState.Error("Target presentation file does not exist.")
                        return@withContext
                    }

                    fileInputStream = FileInputStream(file)
                    val ppt: SlideShow<*, *> = if (filePath.endsWith(".ppt", ignoreCase = true)) {
                        HSLFSlideShow(fileInputStream)
                    } else {
                        XMLSlideShow(fileInputStream)
                    }

                    activePresentation = ppt
                    activeFilePath = filePath

                    val (widthEmu, heightEmu) = PptxShapeExtractor.getSlideDimensionsEmu(ppt)
                    activeWidthEmu = widthEmu
                    activeHeightEmu = heightEmu

                    // 1. Parse slide content models
                    val slides = parseAllSlides(ppt, widthEmu, heightEmu)

                    // 2. High-Fidelity Canvas Rendering Pipeline for all slides
                    val renderDir = File(context.cacheDir, "pptx_slides_${file.nameWithoutExtension}_${System.currentTimeMillis()}")
                    renderDir.mkdirs()

                    val renderPaths = ppt.slides.mapIndexed { index, slide ->
                        try {
                            val outFile = File(renderDir, "slide_${index + 1}.jpg")
                            PptxSlideRenderer.renderSlideToFile(slide, outFile, widthEmu, heightEmu, targetWidth = 1920)
                            outFile.absolutePath
                        } catch (t: Throwable) {
                            PptxShapeExtractor.logWarn("PptxViewerViewModel", "Slide $index render error", t)
                            null
                        }
                    }

                    val presentation = PptxPresentation(slides, widthEmu, heightEmu)
                    _loadState.value = PptxLoadState.Success(presentation, file.name, renderPaths)

                } catch (t: Throwable) {
                    PptxShapeExtractor.logWarn("PptxViewerViewModel", "Failed to load PPTX", t)
                    _loadState.value = PptxLoadState.Error(t.message ?: "Failed to read PowerPoint presentation.")
                } finally {
                    try { fileInputStream?.close() } catch (_: Throwable) { }
                }
            }
        }
    }

    private fun parseAllSlides(ppt: SlideShow<*, *>, slideWidthEmu: Long, slideHeightEmu: Long): List<PptxSlide> {
        val slideAspectRatio = if (slideHeightEmu > 0) slideWidthEmu.toFloat() / slideHeightEmu.toFloat() else (16f / 9f)
        val slides = mutableListOf<PptxSlide>()

        for ((index, slide) in ppt.slides.withIndex()) {
            val textShapes = mutableListOf<PptxTextShape>()
            val images = mutableListOf<PptxImage>()
            var backgroundImage: PptxImage? = null
            val bgColorHex = PptxShapeExtractor.getSlideBgColorHex(slide)

            // Extract Slide Background Picture
            try {
                val bgPicPair = PptxShapeExtractor.extractSlideBackgroundPicture(slide)
                if (bgPicPair != null && bgPicPair.first.isNotEmpty()) {
                    val bgFile = savePicBytesToCache(index, bgPicPair.first, bgPicPair.second)
                    backgroundImage = PptxImage(bgFile.absolutePath, 0f, 0f, 1f, 1f)
                }
            } catch (_: Throwable) { }

            // Extract Speaker Notes safely
            val speakerNotes: String? = try {
                val notesObj = try { slide.notes } catch (_: Throwable) { null }
                if (notesObj != null) {
                    val shapes = try { notesObj.shapes } catch (_: Throwable) { emptyList() }
                    var noteText: String? = null
                    for (sh in shapes) {
                        if (sh is org.apache.poi.sl.usermodel.TextShape<*, *>) {
                            val t = try { sh.text } catch (_: Throwable) { null }
                            if (!t.isNullOrBlank()) {
                                noteText = t
                                break
                            }
                        }
                    }
                    noteText
                } else null
            } catch (_: Throwable) { null }

            var titleShape: PptxTextShape? = null
            var bodyCount = 0

            val rootShapes = try { slide.shapes } catch (_: Throwable) { emptyList() }

            for (shape in rootShapes) {
                try {
                    // 1. Picture extraction
                    val picPair = PptxShapeExtractor.extractPictureDataFromShape(shape, slide)
                    if (picPair != null && picPair.first.isNotEmpty()) {
                        val file = savePicBytesToCache(index, picPair.first, picPair.second)
                        val bounds = PptxShapeExtractor.getShapeNormalizedBounds(shape, slide, slideWidthEmu, slideHeightEmu)
                        val left = bounds?.get(0) ?: 0f
                        val top = bounds?.get(1) ?: 0f
                        val width = bounds?.get(2) ?: 1f
                        val height = bounds?.get(3) ?: 1f

                        val isFullBleed = (width * height >= 0.75f && left <= 0.08f && top <= 0.08f) || (rootShapes.size == 1 && width * height >= 0.50f)
                        if (isFullBleed && backgroundImage == null) {
                            backgroundImage = PptxImage(file.absolutePath, 0f, 0f, 1f, 1f)
                        } else {
                            images.add(PptxImage(file.absolutePath, left, top, width, height))
                        }
                    }

                    // 2. Text shape extraction
                    if (shape is org.apache.poi.sl.usermodel.TextShape<*, *>) {
                        val shapeText = try { shape.text ?: "" } catch (_: Throwable) { "" }
                        if (shapeText.isNotBlank()) {
                            val isTitle = try {
                                shape.placeholder == Placeholder.TITLE || shape.placeholder == Placeholder.CENTERED_TITLE
                            } catch (_: Throwable) {
                                shape.shapeName.lowercase().contains("title")
                            }

                            val bounds = PptxShapeExtractor.getShapeNormalizedBounds(shape, slide, slideWidthEmu, slideHeightEmu)
                            val shapeLeft = bounds?.get(0) ?: 0.05f
                            val shapeTop = bounds?.get(1) ?: (0.08f + bodyCount * 0.14f).coerceAtMost(0.85f)
                            val shapeWidthVal = bounds?.get(2) ?: 0.9f
                            val shapeHeightVal = bounds?.get(3) ?: 0.15f

                            val paragraphs = try { shape.textParagraphs } catch (_: Throwable) { emptyList() }
                            val shapeParagraphs = mutableListOf<PptxParagraph>()

                            for (p in paragraphs) {
                                val runs = try { p.textRuns } catch (_: Throwable) { emptyList() }
                                val pRuns = mutableListOf<PptxTextRun>()

                                for (r in runs) {
                                    val text = PptxShapeExtractor.getTextFromRun(r)
                                    if (text.isNotBlank()) {
                                        val isBold = try { r.isBold } catch (_: Throwable) { false }
                                        val isItalic = try { r.isItalic } catch (_: Throwable) { false }
                                        val isUnderline = try { r.isUnderlined } catch (_: Throwable) { false }
                                        val colorHex = PptxShapeExtractor.extractTextRunColorHex(r)
                                        val fSize = try { r.fontSize } catch (_: Throwable) { null }
                                        val fontSizePt = if (fSize != null && fSize > 0) fSize.toFloat() else (if (isTitle) 24f else 14f)

                                        pRuns.add(PptxTextRun(text, isBold, isItalic, isUnderline, colorHex, fontSizePt))
                                    }
                                }

                                if (pRuns.isNotEmpty()) {
                                    val alignStr = try { p.textAlign?.name ?: "LEFT" } catch (_: Throwable) { "LEFT" }
                                    val bulletLevel = try { p.indentLevel } catch (_: Throwable) { 0 }
                                    val hasBullet = try {
                                        if (p is org.apache.poi.xslf.usermodel.XSLFTextParagraph) {
                                            p.bulletCharacter != null || p.indentLevel > 0
                                        } else {
                                            p.indentLevel > 0
                                        }
                                    } catch (_: Throwable) { false }

                                    val bulletChar = try {
                                        if (p is org.apache.poi.xslf.usermodel.XSLFTextParagraph) p.bulletCharacter ?: "" else ""
                                    } catch (_: Throwable) { "" }

                                    shapeParagraphs.add(PptxParagraph(pRuns, bulletLevel, hasBullet, bulletChar, alignStr))
                                }
                            }

                            if (shapeParagraphs.isNotEmpty()) {
                                val parsedShape = PptxTextShape(
                                    id = "shape_$bodyCount",
                                    isTitle = isTitle,
                                    paragraphs = shapeParagraphs,
                                    shapeLeft = shapeLeft,
                                    shapeTop = shapeTop,
                                    shapeWidth = shapeWidthVal,
                                    shapeHeight = shapeHeightVal
                                )
                                if (isTitle && titleShape == null) {
                                    titleShape = parsedShape
                                }
                                textShapes.add(parsedShape)
                                bodyCount++
                            }
                        }
                    }
                } catch (_: Throwable) { }
            }

            val finalTitle = titleShape ?: textShapes.firstOrNull { it.isTitle } ?: PptxTextShape(
                id = "empty_title",
                isTitle = false,
                paragraphs = emptyList(),
                shapeLeft = 0f,
                shapeTop = 0f,
                shapeWidth = 0f,
                shapeHeight = 0f
            )

            slides.add(
                PptxSlide(
                    slideNumber = index + 1,
                    title = finalTitle,
                    textShapes = textShapes,
                    images = images,
                    backgroundImage = backgroundImage,
                    speakerNotes = speakerNotes,
                    bgColorHex = bgColorHex,
                    aspectRatio = slideAspectRatio
                )
            )
        }
        return slides
    }

    private fun savePicBytesToCache(slideIndex: Int, dataBytes: ByteArray, contentType: String?): File {
        val hash = dataBytes.contentHashCode().toString()
        val suggestExt = contentType?.substringAfter("/")?.substringBefore("+")?.lowercase() ?: "png"
        val tempFile = File(context.cacheDir, "pptx_pic_${slideIndex}_$hash.$suggestExt")
        if (!tempFile.exists() || tempFile.length() == 0L) {
            tempFile.outputStream().use { it.write(dataBytes) }
        }
        return tempFile
    }

    // =========================================================================
    // SEARCH ENGINE
    // =========================================================================

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        val currentState = _loadState.value
        if (currentState is PptxLoadState.Success) {
            val results = PptxSearchEngine.search(currentState.presentation, query)
            _searchResults.value = results
            _currentMatchIndex.value = if (results.isNotEmpty()) 0 else -1
        }
    }

    fun nextSearchResult(): Int? {
        val results = _searchResults.value
        if (results.isEmpty()) return null
        val next = (_currentMatchIndex.value + 1) % results.size
        _currentMatchIndex.value = next
        return results[next].pageIndex
    }

    fun previousSearchResult(): Int? {
        val results = _searchResults.value
        if (results.isEmpty()) return null
        val prev = if (_currentMatchIndex.value <= 0) results.size - 1 else _currentMatchIndex.value - 1
        _currentMatchIndex.value = prev
        return results[prev].pageIndex
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _currentMatchIndex.value = -1
    }

    // =========================================================================
    // SLIDE & TEXT FORMATTING / EDITING
    // =========================================================================

    fun updateSlideTextShape(
        slideIndex: Int,
        isTitle: Boolean,
        blockIndex: Int,
        newText: String,
        isBold: Boolean,
        isItalic: Boolean,
        isUnderline: Boolean,
        textColorHex: String?,
        comment: String?,
        fontSizePt: Float
    ) {
        val currentState = _loadState.value as? PptxLoadState.Success ?: return
        val ppt = activePresentation ?: return

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val slide = ppt.slides.getOrNull(slideIndex) ?: return@withContext
                    val shapes = slide.shapes.filterIsInstance<XSLFTextShape>()

                    val targetShape = if (isTitle) {
                        shapes.firstOrNull { it.isPlaceholder && (it.textType == Placeholder.TITLE || it.textType == Placeholder.CENTERED_TITLE) }
                            ?: shapes.firstOrNull { it.shapeName.lowercase().contains("title") }
                            ?: shapes.firstOrNull()
                    } else {
                        val bodyShapes = shapes.filter { !(it.isPlaceholder && (it.textType == Placeholder.TITLE || it.textType == Placeholder.CENTERED_TITLE)) }
                        bodyShapes.getOrNull(blockIndex) ?: shapes.getOrNull(blockIndex)
                    }

                    if (targetShape != null) {
                        targetShape.text = newText
                        for (p in targetShape.textParagraphs) {
                            for (r in p.textRuns) {
                                r.isBold = isBold
                                r.isItalic = isItalic
                                r.isUnderlined = isUnderline
                                if (fontSizePt > 0) r.fontSize = fontSizePt.toDouble()
                                if (textColorHex != null) {
                                    setRunProperties(r, textColorHex, fontSizePt)
                                }
                            }
                        }
                    }

                    // Speaker notes update
                    if (comment != null) {
                        try {
                            val notes = slide.notes
                            if (notes != null) {
                                val noteShape = notes.shapes.filterIsInstance<XSLFTextShape>().firstOrNull()
                                noteShape?.text = comment
                            }
                        } catch (_: Throwable) { }
                    }

                    // Re-render modified slide immediately
                    val renderDir = File(context.cacheDir, "pptx_edits_${System.currentTimeMillis()}")
                    renderDir.mkdirs()
                    val outFile = File(renderDir, "slide_${slideIndex + 1}.jpg")
                    PptxSlideRenderer.renderSlideToFile(slide, outFile, activeWidthEmu, activeHeightEmu, targetWidth = 1920)

                    val updatedRenderPaths = currentState.slideRenderPaths.toMutableList()
                    if (slideIndex in updatedRenderPaths.indices) {
                        updatedRenderPaths[slideIndex] = outFile.absolutePath
                    }

                    val updatedSlides = parseAllSlides(ppt, activeWidthEmu, activeHeightEmu)
                    val updatedPres = PptxPresentation(updatedSlides, activeWidthEmu, activeHeightEmu)

                    _loadState.value = currentState.copy(
                        presentation = updatedPres,
                        slideRenderPaths = updatedRenderPaths
                    )
                    _saveStatus.emit("Slide updated successfully")

                } catch (t: Throwable) {
                    PptxShapeExtractor.logWarn("PptxViewerViewModel", "Failed updating slide", t)
                    _saveStatus.emit("Error updating slide: ${t.message}")
                }
            }
        }
    }

    private fun setRunProperties(r: XSLFTextRun, textColorHex: String?, fontSizePt: Float) {
        try {
            if (fontSizePt > 0) r.fontSize = fontSizePt.toDouble()
        } catch (_: Throwable) { }

        val xmlRun = PptxShapeExtractor.getXmlObjectReflection(r) ?: return
        try {
            val rPr = PptxShapeExtractor.invokeMethod(xmlRun, "getRPr")
                ?: PptxShapeExtractor.invokeMethod(xmlRun, "addNewRPr")
            if (rPr != null && textColorHex != null) {
                try { PptxShapeExtractor.invokeMethod(rPr, "unsetSolidFill") } catch (_: Throwable) { }
                val solidFill = PptxShapeExtractor.invokeMethod(rPr, "addNewSolidFill")
                val srgbClr = PptxShapeExtractor.invokeMethod(solidFill, "addNewSrgbClr")
                val color = android.graphics.Color.parseColor(textColorHex)
                val rgbBytes = byteArrayOf(
                    ((color shr 16) and 0xFF).toByte(),
                    ((color shr 8) and 0xFF).toByte(),
                    (color and 0xFF).toByte()
                )
                if (srgbClr != null) {
                    PptxShapeExtractor.invokeMethod(srgbClr, "setVal", rgbBytes)
                }
            }
        } catch (_: Throwable) { }
    }

    fun setSlideBackground(slideIndex: Int, colorHex: String) {
        val currentState = _loadState.value as? PptxLoadState.Success ?: return
        val ppt = activePresentation ?: return

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val slide = ppt.slides.getOrNull(slideIndex) ?: return@withContext
                    if (slide is XSLFSlide) {
                        val color = android.graphics.Color.parseColor(colorHex)
                        val rgbBytes = byteArrayOf(
                            ((color shr 16) and 0xFF).toByte(),
                            ((color shr 8) and 0xFF).toByte(),
                            (color and 0xFF).toByte()
                        )
                        val ctSlide = PptxShapeExtractor.getXmlObjectReflection(slide)
                        if (ctSlide != null) {
                            val cSld = PptxShapeExtractor.invokeMethod(ctSlide, "getCSld")
                                ?: PptxShapeExtractor.invokeMethod(ctSlide, "addNewCSld")
                            if (cSld != null) {
                                val bg = PptxShapeExtractor.invokeMethod(cSld, "getBg")
                                    ?: PptxShapeExtractor.invokeMethod(cSld, "addNewBg")
                                if (bg != null) {
                                    val bgPr = PptxShapeExtractor.invokeMethod(bg, "getBgPr")
                                        ?: PptxShapeExtractor.invokeMethod(bg, "addNewBgPr")
                                    if (bgPr != null) {
                                        try { PptxShapeExtractor.invokeMethod(bgPr, "unsetSolidFill") } catch (_: Throwable) { }
                                        val solidFill = PptxShapeExtractor.invokeMethod(bgPr, "addNewSolidFill")
                                        val srgbClr = PptxShapeExtractor.invokeMethod(solidFill, "addNewSrgbClr")
                                        if (srgbClr != null) {
                                            PptxShapeExtractor.invokeMethod(srgbClr, "setVal", rgbBytes)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Re-render modified slide
                    val renderDir = File(context.cacheDir, "pptx_bg_${System.currentTimeMillis()}")
                    renderDir.mkdirs()
                    val outFile = File(renderDir, "slide_${slideIndex + 1}.jpg")
                    PptxSlideRenderer.renderSlideToFile(slide, outFile, activeWidthEmu, activeHeightEmu, targetWidth = 1920)

                    val updatedRenderPaths = currentState.slideRenderPaths.toMutableList()
                    if (slideIndex in updatedRenderPaths.indices) {
                        updatedRenderPaths[slideIndex] = outFile.absolutePath
                    }

                    val updatedSlides = parseAllSlides(ppt, activeWidthEmu, activeHeightEmu)
                    val updatedPres = PptxPresentation(updatedSlides, activeWidthEmu, activeHeightEmu)

                    _loadState.value = currentState.copy(
                        presentation = updatedPres,
                        slideRenderPaths = updatedRenderPaths
                    )
                    _saveStatus.emit("Background updated")
                } catch (t: Throwable) {
                    _saveStatus.emit("Error updating background: ${t.message}")
                }
            }
        }
    }

    fun saveActivePresentation(onComplete: (Boolean, String?) -> Unit) {
        val ppt = activePresentation
        val path = activeFilePath
        if (ppt == null || path == null) {
            onComplete(false, "No active presentation to save.")
            return
        }

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val file = File(path)
                    val tempFile = File(file.parentFile, "${file.nameWithoutExtension}_temp.pptx")
                    FileOutputStream(tempFile).use { out ->
                        ppt.write(out)
                    }
                    if (tempFile.exists() && tempFile.length() > 0) {
                        tempFile.copyTo(file, overwrite = true)
                        tempFile.delete()
                        withContext(Dispatchers.Main) {
                            onComplete(true, "Presentation saved successfully.")
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            onComplete(false, "Failed to write presentation data.")
                        }
                    }
                } catch (t: Throwable) {
                    withContext(Dispatchers.Main) {
                        onComplete(false, "Save error: ${t.message}")
                    }
                }
            }
        }
    }

    // =========================================================================
    // EXPORT UTILITIES (PDF, IMAGES, OUTLINE)
    // =========================================================================

    fun exportToPdf(outputUri: Uri, onComplete: (Boolean, String?) -> Unit) {
        val currentState = _loadState.value as? PptxLoadState.Success ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val doc = PDDocument()
                    for (path in currentState.slideRenderPaths) {
                        if (path != null && File(path).exists()) {
                            val bitmap = BitmapFactory.decodeFile(path)
                            if (bitmap != null) {
                                val page = PDPage(PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat()))
                                doc.addPage(page)
                                val pdImage = JPEGFactory.createFromImage(doc, bitmap, 0.9f)
                                PDPageContentStream(doc, page).use { cs ->
                                    cs.drawImage(pdImage, 0f, 0f)
                                }
                                bitmap.recycle()
                            }
                        }
                    }

                    context.contentResolver.openOutputStream(outputUri)?.use { out ->
                        doc.save(out)
                    }
                    doc.close()

                    withContext(Dispatchers.Main) {
                        onComplete(true, "PDF exported successfully.")
                    }
                } catch (t: Throwable) {
                    withContext(Dispatchers.Main) {
                        onComplete(false, "PDF Export failed: ${t.message}")
                    }
                }
            }
        }
    }

    fun exportToOutlineTxt(outputUri: Uri, onComplete: (Boolean, String?) -> Unit) {
        val currentState = _loadState.value as? PptxLoadState.Success ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val sb = java.lang.StringBuilder()
                    sb.appendLine("Presentation: ${currentState.fileName}")
                    sb.appendLine("=".repeat(40))
                    sb.appendLine()

                    for ((idx, slide) in currentState.presentation.slides.withIndex()) {
                        sb.appendLine("--- Slide ${idx + 1} ---")
                        if (slide.title.fullText.isNotBlank()) {
                            sb.appendLine("Title: ${slide.title.fullText.trim()}")
                        }
                        for (sh in slide.textShapes) {
                            if (!sh.isTitle && sh.fullText.isNotBlank()) {
                                sb.appendLine(sh.fullText.trim())
                            }
                        }
                        if (!slide.speakerNotes.isNullOrBlank()) {
                            sb.appendLine("Notes: ${slide.speakerNotes.trim()}")
                        }
                        sb.appendLine()
                    }

                    context.contentResolver.openOutputStream(outputUri)?.use { out ->
                        out.write(sb.toString().toByteArray(Charsets.UTF_8))
                    }

                    withContext(Dispatchers.Main) {
                        onComplete(true, "Outline extracted to text.")
                    }
                } catch (t: Throwable) {
                    withContext(Dispatchers.Main) {
                        onComplete(false, "Outline export failed: ${t.message}")
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            activePresentation?.close()
        } catch (_: Throwable) { }
    }
}
