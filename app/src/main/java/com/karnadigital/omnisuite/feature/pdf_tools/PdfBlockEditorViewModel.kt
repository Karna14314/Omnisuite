package com.karnadigital.omnisuite.feature.pdf_tools

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karnadigital.omnisuite.core.engine.PdfLayoutParser
import com.karnadigital.omnisuite.core.engine.PdfPageLayout
import com.karnadigital.omnisuite.core.engine.PdfTextBlock
import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.core.repository.RecentFileRepository
import com.karnadigital.omnisuite.core.util.FileOutputManager
import com.karnadigital.omnisuite.core.util.UriCacheUtils
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.rendering.PDFRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

sealed class PdfBlockLoadState {
    object Idle : PdfBlockLoadState()
    object Loading : PdfBlockLoadState()
    data class Success(
        val pages: List<PdfPageLayout>,
        val activePageIndex: Int = 0,
        val activePageBitmap: Bitmap? = null,
        val originalFile: File,
        val saveMessage: String? = null
    ) : PdfBlockLoadState()
    data class Error(val message: String) : PdfBlockLoadState()
}

@HiltViewModel
class PdfBlockEditorViewModel @Inject constructor(
    private val uriCacheUtils: UriCacheUtils,
    private val fileOutputManager: FileOutputManager,
    private val recentFileRepository: RecentFileRepository
) : ViewModel() {

    private val _loadState = MutableStateFlow<PdfBlockLoadState>(PdfBlockLoadState.Idle)
    val loadState: StateFlow<PdfBlockLoadState> = _loadState.asStateFlow()

    private val layoutParser = PdfLayoutParser()
    private var originalDocument: PDDocument? = null
    private var pdfRenderer: PDFRenderer? = null
    private var originalFile: File? = null

    // Memory-bounded LRU cache for rendered page bitmaps (max 5 pages)
    private val bitmapCache = LruCache<Int, Bitmap>(5)

    suspend fun cacheUriToFile(context: Context, uri: Uri): File? {
        return uriCacheUtils.cacheUriToFile(uri)
    }

    fun loadPdf(file: File) {
        viewModelScope.launch {
            _loadState.value = PdfBlockLoadState.Loading
            withContext(Dispatchers.IO) {
                try {
                    // Clean up previous document if one was open
                    try { originalDocument?.close() } catch (e: Exception) {}
                    bitmapCache.evictAll()

                    val document = PDDocument.load(file)
                    originalDocument = document
                    pdfRenderer = PDFRenderer(document)
                    originalFile = file

                    val parser = PdfLayoutParser()
                    val layouts = parser.parseDocument(file)

                    val initialBitmap = renderPageBitmapInternal(0)

                    _loadState.value = PdfBlockLoadState.Success(
                        pages = layouts,
                        activePageIndex = 0,
                        activePageBitmap = initialBitmap,
                        originalFile = file
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    _loadState.value = PdfBlockLoadState.Error("Failed to parse PDF: ${e.localizedMessage}")
                }
            }
        }
    }

    fun selectPage(pageIndex: Int) {
        val currentState = _loadState.value as? PdfBlockLoadState.Success ?: return
        if (pageIndex < 0 || pageIndex >= currentState.pages.size) return

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val bitmap = renderPageBitmapInternal(pageIndex)
                _loadState.value = currentState.copy(
                    activePageIndex = pageIndex,
                    activePageBitmap = bitmap
                )
            }
        }
    }

    private fun renderPageBitmapInternal(pageIndex: Int, scale: Float = 1.5f): Bitmap? {
        val cached = bitmapCache.get(pageIndex)
        if (cached != null && !cached.isRecycled) {
            return cached
        }
        val renderer = pdfRenderer ?: return null
        return try {
            val bitmap = renderer.renderImage(pageIndex, scale)
            bitmapCache.put(pageIndex, bitmap)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun updateBlockText(block: PdfTextBlock, newText: String) {
        val currentState = _loadState.value as? PdfBlockLoadState.Success ?: return
        val updatedPages = currentState.pages.map { page ->
            val updatedBlocks = page.blocks.map { b ->
                if (b.id == block.id) b.copy(editedText = newText) else b
            }
            page.copy(blocks = updatedBlocks)
        }
        _loadState.value = currentState.copy(pages = updatedPages)
    }

    fun saveEdits(context: Context) {
        val currentState = _loadState.value as? PdfBlockLoadState.Success ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val doc = originalDocument ?: return@withContext
                    val file = currentState.originalFile

                    for (pageLayout in currentState.pages) {
                        val pageIndex = pageLayout.pageIndex
                        if (pageIndex < doc.numberOfPages) {
                            val page = doc.getPage(pageIndex)
                            for (block in pageLayout.blocks) {
                                if (block.editedText != null && block.editedText != block.text) {
                                    applyTextToPage(doc, page, pageLayout.pageHeight, block)
                                }
                            }
                        }
                    }

                    val tempFile = File(context.cacheDir, "edited_${System.currentTimeMillis()}_${file.name}")
                    FileOutputStream(tempFile).use { doc.save(it) }

                    val bytes = tempFile.readBytes()
                    val outName = "Edited_${file.name}"
                    val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/pdf", "PDF")
                    if (savedUri != null) {
                        recentFileRepository.insertRecentFile(
                            RecentFile(
                                fileUri = savedUri.toString(),
                                fileName = outName,
                                mimeType = "application/pdf",
                                fileSize = bytes.size.toLong(),
                                lastOpened = System.currentTimeMillis(),
                                isOperation = true
                            )
                        )
                    }
                    if (tempFile.exists()) tempFile.delete()

                    bitmapCache.evictAll()
                    val newBitmap = renderPageBitmapInternal(currentState.activePageIndex)
                    _loadState.value = currentState.copy(
                        activePageBitmap = newBitmap,
                        saveMessage = "Edits saved successfully!"
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun applyTextToPage(doc: PDDocument, page: PDPage, pageHeight: Float, block: PdfTextBlock) {
        try {
            val contentStream = PDPageContentStream(
                doc, page,
                PDPageContentStream.AppendMode.APPEND,
                true
            )

            // 1. Mask original glyphs by drawing a white rectangle over original block bounds
            contentStream.setNonStrokingColor(1f, 1f, 1f)
            val pdfY = pageHeight - block.y - block.height
            contentStream.addRect(block.x, pdfY, block.width, block.height)
            contentStream.fill()

            // 2. Draw replacement text in black
            contentStream.setNonStrokingColor(0f, 0f, 0f)
            contentStream.beginText()
            contentStream.setFont(PDType1Font.HELVETICA, block.fontSize)
            contentStream.newLineAtOffset(block.x, pageHeight - block.y)
            contentStream.showText(block.editedText ?: block.text)
            contentStream.endText()

            contentStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            originalDocument?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
