package com.karnadigital.omnisuite.feature.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karnadigital.omnisuite.core.engine.SearchResult
import com.karnadigital.omnisuite.core.engine.document.NormalizedBounds
import com.karnadigital.omnisuite.core.engine.document.ParsedBackground
import com.karnadigital.omnisuite.core.engine.document.ParsedParagraph
import com.karnadigital.omnisuite.core.engine.document.ParsedPresentation
import com.karnadigital.omnisuite.core.engine.document.ParsedShape
import com.karnadigital.omnisuite.core.engine.document.ParsedSlide
import com.karnadigital.omnisuite.core.engine.document.ParsedTextRun
import com.karnadigital.omnisuite.core.engine.document.PptxShapeExtractor
import com.karnadigital.omnisuite.core.engine.document.TextAlignment
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

sealed class PptxLoadState {
    object Loading : PptxLoadState()
    data class Success(
        val presentation: ParsedPresentation,
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

                    // 1. Parse slide content models using PptxShapeExtractor
                    val presentation = PptxShapeExtractor.parsePresentation(ppt)

                    // 2. Generate slide thumbnail paths using OfficeConverter walking ParsedSlide model
                    val renderPaths = officeConverter.renderParsedPresentationToSlideImages(presentation)

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

                    val updatedPres = PptxShapeExtractor.parsePresentation(ppt)
                    val updatedRenderPaths = officeConverter.renderParsedPresentationToSlideImages(updatedPres)

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

                    val updatedPres = PptxShapeExtractor.parsePresentation(ppt)
                    val updatedRenderPaths = officeConverter.renderParsedPresentationToSlideImages(updatedPres)

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
