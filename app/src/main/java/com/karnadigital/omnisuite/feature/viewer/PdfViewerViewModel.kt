package com.karnadigital.omnisuite.feature.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karnadigital.omnisuite.core.engine.DocumentSearchEngine
import com.karnadigital.omnisuite.core.engine.SearchResult
import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.core.repository.RecentFileRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

sealed class PdfLoadState {
    object Loading : PdfLoadState()
    data class Success(val pageCount: Int, val fileName: String) : PdfLoadState()
    data class PasswordRequired(
        val fileName: String,
        val incorrectAttempt: Boolean = false
    ) : PdfLoadState()
    data class Error(val message: String) : PdfLoadState()
}

@HiltViewModel
class PdfViewerViewModel @Inject constructor(
    private val recentFileRepository: RecentFileRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _loadState = MutableStateFlow<PdfLoadState>(PdfLoadState.Loading)
    val loadState: StateFlow<PdfLoadState> = _loadState.asStateFlow()

    private val _loadedAnnotations = MutableStateFlow<Map<Int, List<TextNoteData>>>(emptyMap())
    val loadedAnnotations: StateFlow<Map<Int, List<TextNoteData>>> = _loadedAnnotations.asStateFlow()

    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private val pageRatios = mutableMapOf<Int, Float>()

    private var sourceFilePath: String? = null
    private var activeFilePath: String? = null
    private var decryptedRenderFile: File? = null

    init {
        PDFBoxResourceLoader.init(context)
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _currentMatchIndex = MutableStateFlow(-1)
    val currentMatchIndex: StateFlow<Int> = _currentMatchIndex.asStateFlow()

    // 5-item LRU Bitmap cache to prevent OutOfMemory crashes
    private val bitmapCache = object : android.util.LruCache<Int, Bitmap>(5) {
        override fun entryRemoved(evicted: Boolean, key: Int?, oldValue: Bitmap?, newValue: Bitmap?) {
            // Remove reference, standard GC cleans it up
        }
    }

    /**
     * Safely opens the local cached PDF file and reads structural metadata.
     */
    fun loadPdf(filePath: String, password: String? = null) {
        viewModelScope.launch {
            _loadState.value = PdfLoadState.Loading
            withContext(Dispatchers.IO) {
                try {
                    closeRenderer()
                    pageRatios.clear()

                    val file = File(filePath)
                    if (!file.exists() || !file.isFile) {
                        _loadState.value = PdfLoadState.Error("Cached file not found or is invalid.")
                        return@withContext
                    }

                    sourceFilePath = filePath
                    val renderFile = prepareRenderablePdf(file, password)
                        ?: return@withContext

                    activeFilePath = renderFile.absolutePath
                    // Open file descriptor
                    val pfd = ParcelFileDescriptor.open(renderFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    parcelFileDescriptor = pfd
                    
                    val renderer = PdfRenderer(pfd)
                    pdfRenderer = renderer

                    val pageCount = renderer.pageCount
                    if (pageCount == 0) {
                        _loadState.value = PdfLoadState.Error("This PDF document contains no pages.")
                        return@withContext
                    }

                    // Query aspect ratios of all pages quickly to set sizing dimensions upfront
                    for (i in 0 until pageCount) {
                        try {
                            val page = renderer.openPage(i)
                            pageRatios[i] = page.width.toFloat() / page.height.toFloat()
                            page.close()
                        } catch (e: Exception) {
                            // Default fallback if a single page metadata fails
                            pageRatios[i] = 0.707f // A4 ratio
                        }
                    }

                    // Aspect ratios collected, ready to render
                    // Load existing text annotations/comments using PDFBox
                    try {
                        PDDocument.load(renderFile).use { doc ->
                            val annotationsMap = mutableMapOf<Int, List<TextNoteData>>()
                            for (i in 0 until doc.numberOfPages) {
                                val pg = doc.getPage(i)
                                val pageWidth = pg.mediaBox.width
                                val pageHeight = pg.mediaBox.height
                                val noteList = pg.annotations
                                    ?.filterIsInstance<PDAnnotationText>()
                                    ?.map { ann ->
                                        val rect = ann.rectangle
                                        val normX = rect.lowerLeftX / pageWidth
                                        val normY = 1f - (rect.upperRightY / pageHeight)
                                        TextNoteData(
                                            text = ann.contents ?: "",
                                            x = normX,
                                            y = normY
                                        )
                                    } ?: emptyList()
                                if (noteList.isNotEmpty()) {
                                    annotationsMap[i] = noteList
                                }
                            }
                            _loadedAnnotations.value = annotationsMap
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    _loadState.value = PdfLoadState.Success(
                        pageCount = pageCount,
                        fileName = file.name
                    )

                } catch (e: Exception) {
                    e.printStackTrace()
                    _loadState.value = PdfLoadState.Error("Failed to render PDF: ${e.localizedMessage}")
                }
            }
        }
    }

    fun submitPassword(password: String) {
        val path = sourceFilePath ?: return
        loadPdf(path, password)
    }

    private fun prepareRenderablePdf(file: File, password: String?): File? {
        if (password == null) {
            return try {
                PDDocument.load(file).use { document ->
                    if (document.isEncrypted) {
                        _loadState.value = PdfLoadState.PasswordRequired(file.name)
                        null
                    } else {
                        file
                    }
                }
            } catch (e: InvalidPasswordException) {
                _loadState.value = PdfLoadState.PasswordRequired(file.name)
                null
            } catch (e: Exception) {
                if (e.message?.contains("encrypted", ignoreCase = true) == true ||
                    e.message?.contains("password", ignoreCase = true) == true ||
                    e.message?.contains("decrypt", ignoreCase = true) == true) {
                    _loadState.value = PdfLoadState.PasswordRequired(file.name)
                } else {
                    _loadState.value = PdfLoadState.Error("Failed to render PDF: ${e.localizedMessage}")
                }
                null
            }
        }

        return try {
            PDDocument.load(file, password).use { document ->
                if (document.isEncrypted) {
                    createUnlockedCopy(file, document)
                } else {
                    file
                }
            }
        } catch (e: InvalidPasswordException) {
            _loadState.value = PdfLoadState.PasswordRequired(file.name, incorrectAttempt = true)
            null
        } catch (e: Exception) {
            _loadState.value = PdfLoadState.PasswordRequired(file.name, incorrectAttempt = true)
            null
        }
    }

    private fun createUnlockedCopy(source: File, document: PDDocument): File {
        decryptedRenderFile?.let { if (it.exists()) it.delete() }
        val output = File(context.cacheDir, "unlocked_${System.currentTimeMillis()}_${source.name}")
        if (document.isEncrypted) {
            document.isAllSecurityToBeRemoved = true
        }
        document.save(output)
        decryptedRenderFile = output
        return output
    }

    /**
     * Pre-calculated aspect ratio of a given page index to allow Compose list sizing before rendering completes.
     */
    fun getPageAspectRatio(pageIndex: Int): Float {
        return pageRatios[pageIndex] ?: 0.707f
    }

    /**
     * Asynchronously renders a PDF page to a Bitmap at 1.5x resolution scale on Dispatchers.IO.
     * Caches the output in a 5-item LRU memory cache.
     */
    suspend fun renderPage(pageIndex: Int): Bitmap? = withContext(Dispatchers.IO) {
        val renderer = pdfRenderer ?: return@withContext null

        // Check LRU cache first
        val cached = bitmapCache.get(pageIndex)
        if (cached != null) {
            return@withContext cached
        }

        try {
            // PDFRenderer requires thread synchronization since only one page can be open at a time
            synchronized(renderer) {
                if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null
                val page = renderer.openPage(pageIndex)
                
                // Render at 1.5x scale for optimal sharpness vs memory consumption
                val width = (page.width * 1.5f).toInt()
                val height = (page.height * 1.5f).toInt()
                
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE) // Fill background
                
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                bitmapCache.put(pageIndex, bitmap)
                bitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
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
                val results = DocumentSearchEngine.searchPdf(path, query)
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

    /**
     * Saves drawing strokes, highlights, and text notes overlay directly into the PDF document streams using Apache PDFBox.
     */
    fun savePdfAnnotations(
        pageIndex: Int,
        paths: List<DrawingPathData>,
        notes: List<TextNoteData>
    ) {
        val path = activeFilePath ?: return
        viewModelScope.launch(Dispatchers.IO) {
            var doc: com.tom_roush.pdfbox.pdmodel.PDDocument? = null
            try {
                val file = java.io.File(path)
                doc = com.tom_roush.pdfbox.pdmodel.PDDocument.load(file)
                val page = doc.getPage(pageIndex)
                
                val pageWidth = page.mediaBox.width
                val pageHeight = page.mediaBox.height

                // 1. Draw Paths / Highlights
                if (paths.isNotEmpty()) {
                    val contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
                        doc,
                        page,
                        com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND,
                        true,
                        true
                    )

                    paths.forEach { drawPath ->
                        if (drawPath.points.size >= 2) {
                            val color = android.graphics.Color.parseColor(drawPath.colorHex)
                            val r = android.graphics.Color.red(color)
                            val g = android.graphics.Color.green(color)
                            val b = android.graphics.Color.blue(color)

                            if (drawPath.isHighlight) {
                                contentStream.setStrokingColor(255, 255, 0)
                                contentStream.setLineWidth(drawPath.strokeWidth)
                            } else {
                                contentStream.setStrokingColor(r, g, b)
                                contentStream.setLineWidth(drawPath.strokeWidth)
                            }

                            val firstPoint = drawPath.points.first()
                            val prevPdfX = firstPoint.x * pageWidth
                            val prevPdfY = (1f - firstPoint.y) * pageHeight

                            contentStream.moveTo(prevPdfX, prevPdfY)

                            for (i in 1 until drawPath.points.size) {
                                val pt = drawPath.points[i]
                                val pdfX = pt.x * pageWidth
                                val pdfY = (1f - pt.y) * pageHeight
                                contentStream.lineTo(pdfX, pdfY)
                            }
                            contentStream.stroke()
                        }
                    }
                    contentStream.close()
                }

                // 2. Save Native PDF Text Notes (PDAnnotationText comments)
                val existingAnnots = page.annotations ?: mutableListOf()
                // Filter out previous PDAnnotationText to avoid duplication during update
                val toKeep = existingAnnots.filter { it !is PDAnnotationText }
                val newAnnots = mutableListOf<PDAnnotation>()
                newAnnots.addAll(toKeep)

                notes.forEach { note ->
                    if (note.text.isNotBlank()) {
                        val textAnnotation = PDAnnotationText()
                        textAnnotation.contents = note.text
                        textAnnotation.setName(PDAnnotationText.NAME_COMMENT)
                        textAnnotation.color = PDColor(
                            floatArrayOf(1f, 1f, 0f),
                            PDDeviceRGB.INSTANCE
                        )
                        
                        val rect = PDRectangle()
                        val sizeVal = 20f
                        val pdfX = note.x * pageWidth
                        val pdfY = (1f - note.y) * pageHeight
                        rect.setLowerLeftX(pdfX)
                        rect.setLowerLeftY(pdfY - sizeVal)
                        rect.setUpperRightX(pdfX + sizeVal)
                        rect.setUpperRightY(pdfY)
                        textAnnotation.rectangle = rect
                        
                        newAnnots.add(textAnnotation)
                    }
                }
                page.annotations = newAnnots

                doc.save(file)

                // Evict this page from cache to force renderer reload the newly written strokes/annotations
                synchronized(bitmapCache) {
                    bitmapCache.remove(pageIndex)
                }

                // Re-trigger load to refresh renderer state
                withContext(Dispatchers.Main) {
                    loadPdf(path)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    doc?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun closeRenderer() {
        try {
            pdfRenderer?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            parcelFileDescriptor?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        pdfRenderer = null
        parcelFileDescriptor = null
        bitmapCache.evictAll()
        val sourcePath = sourceFilePath
        decryptedRenderFile?.let { file ->
            if (file.absolutePath != sourcePath && file.exists()) {
                file.delete()
            }
        }
        decryptedRenderFile = null
    }

    override fun onCleared() {
        super.onCleared()
        closeRenderer()
    }
}

data class DrawingPointData(val x: Float, val y: Float)
data class DrawingPathData(
    val points: List<DrawingPointData>,
    val colorHex: String,
    val strokeWidth: Float,
    val isHighlight: Boolean
)
data class TextNoteData(
    val text: String,
    val x: Float,
    val y: Float
)
