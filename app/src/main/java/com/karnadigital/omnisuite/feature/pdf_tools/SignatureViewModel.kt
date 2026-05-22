package com.karnadigital.omnisuite.feature.pdf_tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.core.repository.RecentFileRepository
import com.karnadigital.omnisuite.core.util.UriCacheUtils
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class SignatureViewModel @Inject constructor(
    private val recentFileRepository: RecentFileRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    init {
        PDFBoxResourceLoader.init(context)
    }

    var selectedPdfUri by mutableStateOf<Uri?>(null)
        private set

    var cachedPdfFile by mutableStateOf<File?>(null)
        private set

    var pageCount by mutableStateOf(0)
        private set

    var currentPageIndex by mutableStateOf(0)
        private set

    var renderedPageBitmap by mutableStateOf<Bitmap?>(null)
        private set

    var cachedSignatureFile by mutableStateOf<File?>(null)
        private set

    var isProcessing by mutableStateOf(false)
        private set

    var successMessage by mutableStateOf<String?>(null)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var activeDocument: PDDocument? = null
        private set

    // Track the dimensions of the currently rendered PDF page in points
    var currentPdfPageWidth by mutableStateOf(595f) // Default A4 width
    var currentPdfPageHeight by mutableStateOf(842f) // Default A4 height

    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null

    fun selectPdf(uri: Uri) {
        selectedPdfUri = uri
        currentPageIndex = 0
        renderedPageBitmap = null
        resetStatus()
        
        viewModelScope.launch {
            isProcessing = true
            withContext(Dispatchers.IO) {
                try {
                    closeRenderer()
                    
                    val cachedFile = UriCacheUtils.cacheUriToFile(context, uri)
                    if (cachedFile == null || !cachedFile.exists()) {
                        throw Exception("Failed to cache source PDF file.")
                    }
                    cachedPdfFile = cachedFile

                    fileDescriptor = ParcelFileDescriptor.open(cachedFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    fileDescriptor?.let { fd ->
                        val renderer = PdfRenderer(fd)
                        pdfRenderer = renderer
                        pageCount = renderer.pageCount
                        renderCurrentPage()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorMessage = "Failed to load PDF: ${e.localizedMessage}"
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    fun setSignatureFile(file: File) {
        cachedSignatureFile = file
    }

    fun nextPage() {
        if (currentPageIndex < pageCount - 1) {
            currentPageIndex++
            viewModelScope.launch {
                isProcessing = true
                withContext(Dispatchers.IO) {
                    renderCurrentPage()
                }
                isProcessing = false
            }
        }
    }

    fun prevPage() {
        if (currentPageIndex > 0) {
            currentPageIndex--
            viewModelScope.launch {
                isProcessing = true
                withContext(Dispatchers.IO) {
                    renderCurrentPage()
                }
                isProcessing = false
            }
        }
    }

    private fun renderCurrentPage() {
        val renderer = pdfRenderer ?: return
        if (currentPageIndex !in 0 until pageCount) return

        try {
            val page = renderer.openPage(currentPageIndex)
            currentPdfPageWidth = page.width.toFloat()
            currentPdfPageHeight = page.height.toFloat()

            // Calculate responsive visual scale
            val displayWidth = 1080
            val aspectRatio = page.height.toFloat() / page.width.toFloat()
            val displayHeight = (displayWidth * aspectRatio).toInt()

            val bitmap = Bitmap.createBitmap(displayWidth, displayHeight, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            renderedPageBitmap = bitmap
            page.close()
        } catch (e: Exception) {
            e.printStackTrace()
            errorMessage = "Failed to render PDF page: ${e.localizedMessage}"
        }
    }

    /**
     * Stamped the digital signature PNG at the calculated PDF postscript points.
     */
    fun stampSignature(
        tapX: Float,
        tapY: Float,
        viewWidth: Float,
        viewHeight: Float,
        targetWidthPoints: Float = 120f,
        targetHeightPoints: Float = 60f,
        outputUri: Uri
    ) {
        val pdfFile = cachedPdfFile
        val sigFile = cachedSignatureFile
        if (pdfFile == null || sigFile == null) {
            errorMessage = "Please select a PDF and draw a signature first."
            return
        }

        isProcessing = true
        resetStatus()

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                var doc: PDDocument? = null
                var tempOutputFile: File? = null
                try {
                    // Map screen coordinate relative to image size to bottom-left PDF postscript coordinates
                    val ratioX = currentPdfPageWidth / viewWidth
                    val ratioY = currentPdfPageHeight / viewHeight

                    // Center the signature on the tap point
                    val targetPdfX = (tapX * ratioX) - (targetWidthPoints / 2f)
                    val targetPdfY = (currentPdfPageHeight - (tapY * ratioY)) - (targetHeightPoints / 2f)

                    doc = PDDocument.load(pdfFile)
                    activeDocument = doc
                    val page = doc.getPage(currentPageIndex)

                    val signatureImage = PDImageXObject.createFromFileByExtension(sigFile, doc)

                    // Draw image into PDF page
                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { contentStream ->
                        contentStream.drawImage(
                            signatureImage,
                            targetPdfX,
                            targetPdfY,
                            targetWidthPoints,
                            targetHeightPoints
                        )
                    }

                    // Save output to temporary file
                    tempOutputFile = File(context.cacheDir, "signed_${System.currentTimeMillis()}.pdf")
                    doc.save(tempOutputFile)
                    doc.close()
                    doc = null

                    // Copy to selected output destination Uri
                    context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                        tempOutputFile!!.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    } ?: throw Exception("Failed to open output stream.")

                    // Register to recent files database
                    val outName = getFileNameFromUri(outputUri) ?: "Signed_Document.pdf"
                    val recent = RecentFile(
                        fileUri = outputUri.toString(),
                        fileName = outName,
                        mimeType = "application/pdf",
                        fileSize = tempOutputFile!!.length(),
                        lastOpened = System.currentTimeMillis()
                    )
                    recentFileRepository.insertRecentFile(recent)

                    // Reload the cache representation with new signed document to allow progressive stamps
                    closeRenderer()
                    
                    val newCachedFile = UriCacheUtils.cacheUriToFile(context, outputUri)
                    if (newCachedFile != null) {
                        cachedPdfFile = newCachedFile
                        fileDescriptor = ParcelFileDescriptor.open(newCachedFile, ParcelFileDescriptor.MODE_READ_ONLY)
                        fileDescriptor?.let { fd ->
                            val renderer = PdfRenderer(fd)
                            pdfRenderer = renderer
                            renderCurrentPage()
                        }
                    }

                    successMessage = "Signature stamped successfully!"
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorMessage = "Failed to stamp signature: ${e.localizedMessage}"
                } finally {
                    withContext(NonCancellable) {
                        try {
                            doc?.close()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        tempOutputFile?.let { if (it.exists()) it.delete() }
                        isProcessing = false
                    }
                }
            }
        }
    }

    fun resetStatus() {
        successMessage = null
        errorMessage = null
    }

    private fun closeRenderer() {
        try {
            pdfRenderer?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        pdfRenderer = null
        try {
            fileDescriptor?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        fileDescriptor = null
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        it.getString(nameIndex)
                    } else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            activeDocument?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        closeRenderer()
        // Clean cached temporary files
        viewModelScope.launch(Dispatchers.IO) {
            try {
                cachedPdfFile?.let { if (it.exists()) it.delete() }
                cachedSignatureFile?.let { if (it.exists()) it.delete() }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
