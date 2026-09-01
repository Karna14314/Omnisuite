package com.karnadigital.omnisuite.feature.pdf_tools

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karnadigital.omnisuite.core.engine.PdfLayoutParser
import com.karnadigital.omnisuite.core.engine.PdfTextBlock
import com.karnadigital.omnisuite.core.engine.PdfPageLayout
import com.karnadigital.omnisuite.core.util.UriCacheUtils
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

sealed class PdfBlockLoadState {
    object Idle : PdfBlockLoadState()
    object Loading : PdfBlockLoadState()
    data class Success(
        val pages: List<PdfPageLayout>,
        val pageBitmaps: List<Bitmap?>,
        val document: PDDocument?,
        val originalFile: File
    ) : PdfBlockLoadState()
    data class Error(val message: String) : PdfBlockLoadState()
}

@HiltViewModel
class PdfBlockEditorViewModel @Inject constructor(
    private val uriCacheUtils: UriCacheUtils
) : ViewModel() {

    private val _loadState = MutableStateFlow<PdfBlockLoadState>(PdfBlockLoadState.Idle)
    val loadState: StateFlow<PdfBlockLoadState> = _loadState.asStateFlow()

    private val layoutParser = PdfLayoutParser()
    private var originalDocument: PDDocument? = null
    private var originalFile: File? = null

    fun cacheUriToFile(context: Context, uri: Uri): File? {
        return uriCacheUtils.cacheUriToFile(uri)
    }

    fun loadPdf(file: File) {
        viewModelScope.launch {
            _loadState.value = PdfBlockLoadState.Loading
            withContext(Dispatchers.IO) {
                try {
                    val document = PDDocument.load(file)
                    originalDocument = document
                    originalFile = file

                    val parser = PdfLayoutParser()
                    val layouts = parser.parseDocument(file)

                    val renderer = PDFRenderer(document)
                    val bitmaps = mutableListOf<Bitmap?>()
                    for (i in 0 until document.numberOfPages) {
                        try {
                            val bitmap = renderer.renderImage(i, 2f)
                            bitmaps.add(bitmap)
                        } catch (e: Exception) {
                            bitmaps.add(null)
                        }
                    }

                    _loadState.value = PdfBlockLoadState.Success(
                        pages = layouts,
                        pageBitmaps = bitmaps,
                        document = document,
                        originalFile = file
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    _loadState.value = PdfBlockLoadState.Error("Failed to parse PDF: ${e.localizedMessage}")
                }
            }
        }
    }

    fun updateBlockText(block: PdfTextBlock, newText: String) {
        val currentState = _loadState.value
        if (currentState is PdfBlockLoadState.Success) {
            val updatedPages = currentState.pages.map { page ->
                val updatedBlocks = page.blocks.map { b ->
                    if (b.id == block.id) b.copy(editedText = newText) else b
                }
                page.copy(blocks = updatedBlocks)
            }
            _loadState.value = currentState.copy(pages = updatedPages)
        }
    }

    fun saveEdits(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val currentState = _loadState.value
                    if (currentState is PdfBlockLoadState.Success) {
                        val file = currentState.originalFile
                        val document = currentState.document

                        if (file != null && document != null) {
                            for (pageLayout in currentState.pages) {
                                val pageIndex = pageLayout.pageIndex
                                if (pageIndex < document.numberOfPages) {
                                    val page = document.getPage(pageIndex)
                                    for (block in pageLayout.blocks) {
                                        if (block.editedText != null && block.editedText != block.text) {
                                            // Apply text changes to the PDF
                                            applyTextToPage(page, block)
                                        }
                                    }
                                }
                            }

                            val outputDir = File(context.getExternalFilesDir(null), "PDF/Edited")
                            if (!outputDir.exists()) outputDir.mkdirs()
                            val outputFile = File(outputDir, "edited_${file.name}")
                            document.save(outputFile)
                            document.close()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun applyTextToPage(page: com.tom_roush.pdfbox.pdmodel.PDPage, block: PdfTextBlock) {
        try {
            val contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
                page.document(), page,
                com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND,
                true
            )

            contentStream.beginText()
            contentStream.setFont(
                com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA,
                block.fontSize
            )
            contentStream.newLineAtOffset(block.x, block.y)
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
