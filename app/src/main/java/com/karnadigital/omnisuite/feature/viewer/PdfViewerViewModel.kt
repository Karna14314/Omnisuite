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
import dagger.hilt.android.lifecycle.HiltViewModel
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
    data class Error(val message: String) : PdfLoadState()
}

@HiltViewModel
class PdfViewerViewModel @Inject constructor(
    private val recentFileRepository: RecentFileRepository
) : ViewModel() {

    private val _loadState = MutableStateFlow<PdfLoadState>(PdfLoadState.Loading)
    val loadState: StateFlow<PdfLoadState> = _loadState.asStateFlow()

    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private val pageRatios = mutableMapOf<Int, Float>()

    private var activeFilePath: String? = null

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
    fun loadPdf(filePath: String) {
        viewModelScope.launch {
            _loadState.value = PdfLoadState.Loading
            withContext(Dispatchers.IO) {
                try {
                    val file = File(filePath)
                    if (!file.exists() || !file.isFile) {
                        _loadState.value = PdfLoadState.Error("Cached file not found or is invalid.")
                        return@withContext
                    }

                    activeFilePath = filePath
                    // Open file descriptor
                    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
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

                    // Log this file opening to our Room RecentFiles history
                    try {
                        val recentFile = RecentFile(
                            fileUri = file.absolutePath,
                            fileName = file.name,
                            mimeType = "application/pdf",
                            fileSize = file.length(),
                            lastOpened = System.currentTimeMillis()
                        )
                        recentFileRepository.insertRecentFile(recentFile)
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
                
                val contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
                    doc,
                    page,
                    com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                )

                val pageWidth = page.mediaBox.width
                val pageHeight = page.mediaBox.height

                // 1. Draw Paths / Highlights
                paths.forEach { drawPath ->
                    if (drawPath.points.size >= 2) {
                        val color = android.graphics.Color.parseColor(drawPath.colorHex)
                        val r = android.graphics.Color.red(color)
                        val g = android.graphics.Color.green(color)
                        val b = android.graphics.Color.blue(color)

                        if (drawPath.isHighlight) {
                            // Highlight: translucent yellow stroke
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

                // 2. Draw Text Notes
                notes.forEach { note ->
                    if (note.text.isNotBlank()) {
                        val pdfX = note.x * pageWidth
                        val pdfY = (1f - note.y) * pageHeight

                        contentStream.beginText()
                        contentStream.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD, 12f)
                        contentStream.setNonStrokingColor(0, 0, 0)
                        contentStream.newLineAtOffset(pdfX, pdfY)
                        contentStream.showText(note.text)
                        contentStream.endText()
                    }
                }

                contentStream.close()
                doc.save(file)

                // Evict this page from cache to force renderer reload the newly written strokes
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
