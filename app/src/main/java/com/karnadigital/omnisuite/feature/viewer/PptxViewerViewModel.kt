package com.karnadigital.omnisuite.feature.viewer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karnadigital.omnisuite.core.engine.SearchResult
import com.karnadigital.omnisuite.core.engine.document.ParsedPresentation
import com.karnadigital.omnisuite.core.engine.document.PptxSlideParser
import com.karnadigital.omnisuite.core.engine.document.PptxSlideRasterizer
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.poi.xslf.usermodel.XSLFTextRun
import org.apache.poi.xslf.usermodel.XSLFTextShape
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject

sealed class PptxLoadState {
    data object Loading : PptxLoadState()
    data class Success(val presentation: ParsedPresentation, val fileName: String) : PptxLoadState()
    data class Error(val message: String) : PptxLoadState()
}

@HiltViewModel
class PptxViewerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parser: PptxSlideParser,
) : ViewModel() {

    private val _loadState = MutableStateFlow<PptxLoadState>(PptxLoadState.Loading)
    val loadState: StateFlow<PptxLoadState> = _loadState.asStateFlow()

    private val _saveStatus = MutableSharedFlow<String>()
    val saveStatus = _saveStatus.asSharedFlow()

    private var activePresentation: XMLSlideShow? = null
    private var activeFilePath: String? = null

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
                try { activePresentation?.close() } catch (_: Throwable) { }
                activePresentation = null
                activeFilePath = null

                var stream: FileInputStream? = null
                try {
                    val file = File(filePath)
                    if (!file.exists() || !file.isFile) {
                        _loadState.value = PptxLoadState.Error("Target presentation file does not exist.")
                        return@withContext
                    }
                    stream = FileInputStream(file)
                    val ppt = XMLSlideShow(stream)
                    activePresentation = ppt
                    activeFilePath = filePath

                    val presentation = parser.parse(ppt)
                    _loadState.value = PptxLoadState.Success(presentation, file.name)
                } catch (t: Throwable) {
                    _loadState.value = PptxLoadState.Error(t.message ?: "Failed to read PowerPoint presentation.")
                } finally {
                    try { stream?.close() } catch (_: Throwable) { }
                }
            }
        }
    }

    // =========================================================================
    // SEARCH
    // =========================================================================

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        val state = _loadState.value
        if (state is PptxLoadState.Success) {
            val results = PptxSearchEngine.search(state.presentation, query)
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
    // EDITING
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
        fontSizePt: Float,
    ) {
        val state = _loadState.value as? PptxLoadState.Success ?: return
        val ppt = activePresentation ?: return

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val slide = ppt.slides.getOrNull(slideIndex) ?: return@withContext
                    val shapes = slide.shapes.filterIsInstance<XSLFTextShape>()
                    val targetShape = if (isTitle) {
                        shapes.firstOrNull { it.isPlaceholder && (it.textType == org.apache.poi.sl.usermodel.Placeholder.TITLE || it.textType == org.apache.poi.sl.usermodel.Placeholder.CENTERED_TITLE) }
                            ?: shapes.firstOrNull { it.shapeName.lowercase().contains("title") }
                            ?: shapes.firstOrNull()
                    } else {
                        val bodyShapes = shapes.filter { !(it.isPlaceholder && (it.textType == org.apache.poi.sl.usermodel.Placeholder.TITLE || it.textType == org.apache.poi.sl.usermodel.Placeholder.CENTERED_TITLE)) }
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
                                if (textColorHex != null) setRunColor(r, textColorHex)
                            }
                        }
                    }

                    if (comment != null) {
                        try {
                            val notes = slide.notes
                            if (notes != null) {
                                val noteShape = notes.shapes.filterIsInstance<XSLFTextShape>().firstOrNull()
                                noteShape?.text = comment
                            }
                        } catch (_: Throwable) { }
                    }

                    val updated = parser.parse(ppt)
                    _loadState.value = state.copy(presentation = updated)
                    _saveStatus.emit("Slide updated successfully")
                } catch (t: Throwable) {
                    _saveStatus.emit("Error updating slide: ${t.message}")
                }
            }
        }
    }

    private fun setRunColor(run: XSLFTextRun, colorHex: String) {
        try {
            val xmlRun = run.getXmlObject()
            val rPr = xmlRun.javaClass.getMethod("getRPr").invoke(xmlRun)
                ?: xmlRun.javaClass.getMethod("addNewRPr").invoke(xmlRun)
            if (rPr != null) {
                try { rPr.javaClass.getMethod("unsetSolidFill").invoke(rPr) } catch (_: Throwable) { }
                val solidFill = rPr.javaClass.getMethod("addNewSolidFill").invoke(rPr)
                val srgbClr = solidFill.javaClass.getMethod("addNewSrgbClr").invoke(solidFill)
                val color = android.graphics.Color.parseColor(colorHex)
                val rgb = byteArrayOf(((color shr 16) and 0xFF).toByte(), ((color shr 8) and 0xFF).toByte(), (color and 0xFF).toByte())
                srgbClr.javaClass.getMethod("setVal", ByteArray::class.java).invoke(srgbClr, rgb)
            }
        } catch (_: Throwable) { }
    }

    fun setSlideBackground(slideIndex: Int, colorHex: String) {
        val state = _loadState.value as? PptxLoadState.Success ?: return
        val ppt = activePresentation ?: return

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val slide = ppt.slides.getOrNull(slideIndex) ?: return@withContext
                    if (slide is XSLFSlide) {
                        val color = android.graphics.Color.parseColor(colorHex)
                        val rgb = byteArrayOf(((color shr 16) and 0xFF).toByte(), ((color shr 8) and 0xFF).toByte(), (color and 0xFF).toByte())
                        val ctSlide = slide.getXmlObject()
                        val cSld = ctSlide.javaClass.getMethod("getCSld").invoke(ctSlide)
                            ?: ctSlide.javaClass.getMethod("addNewCSld").invoke(ctSlide)
                        if (cSld != null) {
                            val bg = cSld.javaClass.getMethod("getBg").invoke(cSld)
                                ?: cSld.javaClass.getMethod("addNewBg").invoke(cSld)
                            if (bg != null) {
                                val bgPr = bg.javaClass.getMethod("getBgPr").invoke(bg)
                                    ?: bg.javaClass.getMethod("addNewBgPr").invoke(bg)
                                if (bgPr != null) {
                                    try { bgPr.javaClass.getMethod("unsetSolidFill").invoke(bgPr) } catch (_: Throwable) { }
                                    val solidFill = bgPr.javaClass.getMethod("addNewSolidFill").invoke(bgPr)
                                    val srgbClr = solidFill.javaClass.getMethod("addNewSrgbClr").invoke(solidFill)
                                    srgbClr.javaClass.getMethod("setVal", ByteArray::class.java).invoke(srgbClr, rgb)
                                }
                            }
                        }
                    }
                    val updated = parser.parse(ppt)
                    _loadState.value = state.copy(presentation = updated)
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
        if (ppt == null || path == null) { onComplete(false, "No active presentation to save."); return }

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val file = File(path)
                    val tempFile = File(file.parentFile, "${file.nameWithoutExtension}_temp.pptx")
                    FileOutputStream(tempFile).use { ppt.write(it) }
                    if (tempFile.exists() && tempFile.length() > 0) {
                        tempFile.copyTo(file, overwrite = true)
                        tempFile.delete()
                        withContext(Dispatchers.Main) { onComplete(true, "Presentation saved successfully.") }
                    } else {
                        withContext(Dispatchers.Main) { onComplete(false, "Failed to write presentation data.") }
                    }
                } catch (t: Throwable) {
                    withContext(Dispatchers.Main) { onComplete(false, "Save error: ${t.message}") }
                }
            }
        }
    }

    // =========================================================================
    // EXPORT — PDF uses the SAME ParsedSlide model via the rasterizer
    // =========================================================================

    fun exportToPdf(outputUri: Uri, onComplete: (Boolean, String?) -> Unit) {
        val state = _loadState.value as? PptxLoadState.Success ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val doc = PDDocument()
                    for (slide in state.presentation.slides) {
                        val aspect = state.presentation.aspectRatio
                        val imgW = 1440
                        val imgH = (imgW / aspect).toInt().coerceAtLeast(800)
                        val bitmap = PptxSlideRasterizer.renderToBitmap(slide, imgW, imgH)
                        val page = PDPage(PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat()))
                        doc.addPage(page)
                        val pdImage = JPEGFactory.createFromImage(doc, bitmap, 0.9f)
                        PDPageContentStream(doc, page).use { cs -> cs.drawImage(pdImage, 0f, 0f) }
                        bitmap.recycle()
                    }
                    context.contentResolver.openOutputStream(outputUri)?.use { doc.save(it) }
                    doc.close()
                    withContext(Dispatchers.Main) { onComplete(true, "PDF exported successfully.") }
                } catch (t: Throwable) {
                    withContext(Dispatchers.Main) { onComplete(false, "PDF Export failed: ${t.message}") }
                }
            }
        }
    }

    fun exportToOutlineTxt(outputUri: Uri, onComplete: (Boolean, String?) -> Unit) {
        val state = _loadState.value as? PptxLoadState.Success ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val sb = StringBuilder()
                    sb.appendLine("Presentation: ${state.fileName}")
                    sb.appendLine("=".repeat(40))
                    sb.appendLine()
                    for ((idx, slide) in state.presentation.slides.withIndex()) {
                        sb.appendLine("--- Slide ${idx + 1} ---")
                        val title = slide.shapes.firstOrNull { it.content is com.karnadigital.omnisuite.core.engine.document.TextContent }
                        // best-effort title extraction
                        for (sh in slide.shapes) {
                            if (sh.content is com.karnadigital.omnisuite.core.engine.document.TextContent) {
                                val tc = sh.content as com.karnadigital.omnisuite.core.engine.document.TextContent
                                sb.appendLine(tc.paragraphs.joinToString(" ") { p -> p.runs.joinToString("") { it.text } }.trim())
                            }
                        }
                        if (!slide.speakerNotes.isNullOrBlank()) sb.appendLine("Notes: ${slide.speakerNotes.trim()}")
                        sb.appendLine()
                    }
                    context.contentResolver.openOutputStream(outputUri)?.use { it.write(sb.toString().toByteArray(Charsets.UTF_8)) }
                    withContext(Dispatchers.Main) { onComplete(true, "Outline extracted to text.") }
                } catch (t: Throwable) {
                    withContext(Dispatchers.Main) { onComplete(false, "Outline export failed: ${t.message}") }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try { activePresentation?.close() } catch (_: Throwable) { }
    }
}
