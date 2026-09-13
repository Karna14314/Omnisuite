package com.karnadigital.omnisuite.feature.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
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
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

data class PageTextData(val text: String, val positions: List<TextPosition>)

data class SearchMatchRect(val pageIndex: Int, val rects: List<RectF>)

data class SearchHighlightState(
    val query: String = "",
    val matches: List<SearchMatchRect> = emptyList(),
    val currentMatchIndex: Int = 0,
    val isLoading: Boolean = false
) {
    val totalMatches: Int
        get() = matches.size
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

    // Search highlight state (with text position data for highlighting)
    private val _searchHighlightState = MutableStateFlow(SearchHighlightState())
    val searchHighlightState: StateFlow<SearchHighlightState> = _searchHighlightState.asStateFlow()

    // Text extraction cache for text selection
    private val extractedTextCache = object : LinkedHashMap<Int, PageTextData>(20, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<Int, PageTextData>) = size > 20
    }

    // Search Job Control
    private var searchJob: Job? = null

    // 5-item LRU Bitmap cache to prevent OutOfMemory crashes
    private val bitmapCache = object : android.util.LruCache<Int, Bitmap>(5) {
        override fun entryRemoved(evicted: Boolean, key: Int?, oldValue: Bitmap?, newValue: Bitmap?) {
            if (evicted) {
                try { oldValue?.recycle() } catch (_: Throwable) {}
            }
        }
    }

    init {
        // Best-effort sweep of stale decrypted copies leaked by a prior crash/kill.
        try {
            context.cacheDir.listFiles { f -> f.name.startsWith("unlocked_") }?.forEach {
                try { it.delete() } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
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

                    // Graceful progressive loading: inspect page 0 immediately so page 1 displays instantly
                    val firstPageRatio = try {
                        val page0 = renderer.openPage(0)
                        val ratio = page0.width.toFloat() / page0.height.toFloat()
                        page0.close()
                        ratio
                    } catch (e: Exception) {
                        0.707f // A4 standard ratio
                    }
                    for (i in 0 until pageCount) {
                        pageRatios[i] = firstPageRatio
                    }

                    // Emit Success immediately so viewer appears without blocking
                    _loadState.value = PdfLoadState.Success(
                        pageCount = pageCount,
                        fileName = file.name
                    )

                    // Progressively read exact subsequent page ratios and annotations in background
                    viewModelScope.launch(Dispatchers.IO) {
                        for (i in 1 until pageCount) {
                            try {
                                val page = renderer.openPage(i)
                                pageRatios[i] = page.width.toFloat() / page.height.toFloat()
                                page.close()
                            } catch (_: Exception) {}
                        }

                        // Background load existing annotations using PDFBox
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
                        } catch (_: Throwable) {}
                    }

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
        decryptedRenderFile?.let { if (it.exists()) try { it.delete() } catch (_: Throwable) {} }
        val safeName = source.name.replace(Regex("[^a-zA-Z0-9._-]"), "_").takeLast(40)
        val output = File(context.cacheDir, "unlocked_${System.currentTimeMillis()}_$safeName")
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
                try {
                    // Render at 1.5x scale, capped so A0/300dpi pages can't OOM.
                    var width = (page.width * 1.5f).toInt()
                    var height = (page.height * 1.5f).toInt()
                    val maxDim = 2048
                    if (width > maxDim || height > maxDim) {
                        val scale = maxDim.toFloat() / maxOf(width, height).toFloat()
                        width = (width * scale).toInt().coerceAtLeast(1)
                        height = (height * scale).toInt().coerceAtLeast(1)
                    }

                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE) // Fill background

                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    bitmapCache.put(pageIndex, bitmap)
                    bitmap
                } finally {
                    try { page.close() } catch (_: Throwable) {}
                }
            }
        } catch (e: OutOfMemoryError) {
            try { bitmapCache.evictAll() } catch (_: Throwable) {}
            e.printStackTrace()
            null
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
    /**
     * Saves all drawing strokes, highlights, and text notes across multiple pages in a single atomic pass
     * without causing renderer descriptor collisions or app crashes.
     */
    fun saveAllPdfAnnotations(
        pagePaths: Map<Int, List<DrawingPathData>>,
        pageNotes: Map<Int, List<TextNoteData>>,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val path = activeFilePath ?: run {
            onComplete(false)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            var doc: com.tom_roush.pdfbox.pdmodel.PDDocument? = null
            val file = java.io.File(path)
            val tempFile = java.io.File(file.parentFile ?: file.absoluteFile.parentFile, "annot_${System.currentTimeMillis()}.pdf")
            var isSuccess = false
            try {
                doc = com.tom_roush.pdfbox.pdmodel.PDDocument.load(file)
                val allPageIndices = (pagePaths.keys + pageNotes.keys).distinct()

                for (pageIndex in allPageIndices) {
                    if (pageIndex < 0 || pageIndex >= doc.numberOfPages) continue
                    val page = doc.getPage(pageIndex)
                    val pageWidth = page.mediaBox.width
                    val pageHeight = page.mediaBox.height
                    val paths = pagePaths[pageIndex] ?: emptyList()
                    val notes = pageNotes[pageIndex] ?: emptyList()

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
                                val color = try { android.graphics.Color.parseColor(drawPath.colorHex) } catch (e: Exception) { android.graphics.Color.BLACK }
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

                    // 2. Save Text Notes
                    if (notes.isNotEmpty()) {
                        val existingAnnots = page.annotations ?: mutableListOf()
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
                    }
                }

                doc.save(tempFile)
                isSuccess = true
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    doc?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (isSuccess && tempFile.exists() && tempFile.length() > 0) {
                withContext(Dispatchers.Main) {
                    closeRenderer()
                    try {
                        tempFile.copyTo(file, overwrite = true)
                        tempFile.delete()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    bitmapCache.evictAll()
                    loadPdf(path)
                    onComplete(true)
                }
            } else {
                withContext(Dispatchers.Main) {
                    onComplete(false)
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
        try { bitmapCache.evictAll() } catch (_: Throwable) {}
        extractedTextCache.clear()
        val sourcePath = sourceFilePath
        decryptedRenderFile?.let { file ->
            if (file.absolutePath != sourcePath && file.exists()) {
                try { file.delete() } catch (_: Throwable) {}
            }
        }
        decryptedRenderFile = null
        // Sweep any other stale unlocked_* copies (previous crash may have orphaned them).
        try {
            context.cacheDir.listFiles { f -> f.name.startsWith("unlocked_") }?.forEach {
                if (it.absolutePath != sourcePath) try { it.delete() } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }

    suspend fun extractTextFromPage(pageIndex: Int): String = withContext(Dispatchers.IO) {
        try {
            val file = activeFilePath?.let { File(it) } ?: return@withContext ""
            com.tom_roush.pdfbox.pdmodel.PDDocument.load(file).use { doc ->
                val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                stripper.startPage = pageIndex + 1
                stripper.endPage = pageIndex + 1
                stripper.getText(doc) ?: ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Extracts text with position data for text selection.
     */
    suspend fun getPageText(pageIndex: Int): PageTextData? = withContext(Dispatchers.IO) {
        try {
            val file = activeFilePath?.let { File(it) } ?: return@withContext null
            val cached = extractedTextCache[pageIndex]
            if (cached != null) return@withContext cached

            val textPositions = mutableListOf<TextPosition>()
            val stripper = object : PDFTextStripper() {
                override fun processTextPosition(text: TextPosition) {
                    super.processTextPosition(text)
                    textPositions.add(text)
                }
            }
            stripper.sortByPosition = true
            stripper.startPage = pageIndex + 1
            stripper.endPage = pageIndex + 1
            val pageText = com.tom_roush.pdfbox.pdmodel.PDDocument.load(file).use { doc ->
                withTimeoutOrNull(5000) { stripper.getText(doc) } ?: ""
            }

            val cleanedText = pageText.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString("\n")

            val data = PageTextData(cleanedText, textPositions)
            extractedTextCache[pageIndex] = data
            data
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Searches PDF and returns matches with text position rectangles for highlighting.
     * Each individual occurrence is a separate match for proper navigation.
     */
    fun searchWithHighlights(query: String) {
        searchJob?.cancel()
        if (query.length < 2) {
            _searchHighlightState.value = SearchHighlightState(query = query)
            return
        }
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            _searchHighlightState.value = _searchHighlightState.value.copy(query = query, isLoading = true)
            val matches = mutableListOf<SearchMatchRect>()
            try {
                val path = activeFilePath ?: return@launch
                PDDocument.load(File(path)).use { doc ->
                val totalPages = doc.numberOfPages
                for (pageIndex in 0 until totalPages) {
                    try {
                        val lowerQuery = query.lowercase()
                        val textPositions = mutableListOf<TextPosition>()
                        val stripper = object : PDFTextStripper() {
                            override fun processTextPosition(text: TextPosition) {
                                super.processTextPosition(text)
                                textPositions.add(text)
                            }
                        }
                        stripper.sortByPosition = true
                        stripper.startPage = pageIndex + 1
                        stripper.endPage = pageIndex + 1
                        val pageText = withTimeoutOrNull(5000) { stripper.getText(doc) } ?: ""
                        if (!pageText.lowercase().contains(lowerQuery)) continue

                        val sb = StringBuilder()
                        val positionMap = mutableListOf<Int>()
                        textPositions.forEachIndexed { index, tp ->
                            sb.append(tp.unicode)
                            repeat(tp.unicode.length) { positionMap.add(index) }
                        }
                        val rawText = sb.toString().lowercase()
                        var pos = 0
                        while (true) {
                            val found = rawText.indexOf(lowerQuery, pos)
                            if (found == -1) break
                            // Create individual match for each occurrence
                            val matchRects = mutableListOf<RectF>()
                            for (i in found until (found + lowerQuery.length)) {
                                if (i < positionMap.size) {
                                    val tpIndex = positionMap[i]
                                    val tp = textPositions[tpIndex]
                                    val x = tp.xDirAdj * 1.5f
                                    val y = tp.yDirAdj * 1.5f
                                    val w = tp.widthDirAdj * 1.5f
                                    val h = tp.heightDir * 1.5f
                                    matchRects.add(RectF(x, y - h, x + w, y + h * 0.2f))
                                }
                            }
                            if (matchRects.isNotEmpty()) {
                                matches.add(SearchMatchRect(pageIndex, matchRects))
                            }
                            pos = found + 1
                        }
                    } catch (e: Exception) {
                        // skip page
                    }
                }
                } // use{} closes doc even on timeout/exception
            } catch (e: Exception) {
                // search failed
            }
            _searchHighlightState.value = SearchHighlightState(
                query = query,
                matches = matches,
                isLoading = false
            )
        }
    }

    fun stopHighlightSearch() {
        searchJob?.cancel()
        searchJob = null
        val current = _searchHighlightState.value
        if (current.isLoading) {
            _searchHighlightState.value = current.copy(isLoading = false)
        }
    }

    fun nextHighlightMatch() {
        val current = _searchHighlightState.value
        if (current.matches.isNotEmpty()) {
            val next = (current.currentMatchIndex + 1) % current.matches.size
            _searchHighlightState.value = current.copy(currentMatchIndex = next)
        }
    }

    fun prevHighlightMatch() {
        val current = _searchHighlightState.value
        if (current.matches.isNotEmpty()) {
            val prev = if (current.currentMatchIndex > 0) current.currentMatchIndex - 1 else current.matches.size - 1
            _searchHighlightState.value = current.copy(currentMatchIndex = prev)
        }
    }

    fun clearHighlightSearch() {
        searchJob?.cancel()
        searchJob = null
        _searchHighlightState.value = SearchHighlightState()
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
