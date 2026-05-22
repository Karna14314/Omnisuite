package com.karnadigital.omnisuite.feature.pdf_tools

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.core.repository.RecentFileRepository
import com.karnadigital.omnisuite.core.util.UriCacheUtils
import com.tomroush.pdfbox.android.PDFBoxResourceLoader
import com.tomroush.pdfbox.pdmodel.PDDocument
import com.tomroush.pdfbox.pdmodel.PDPageContentStream
import com.tomroush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tomroush.pdfbox.pdmodel.font.PDType1Font
import com.tomroush.pdfbox.util.Matrix
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class WatermarkViewModel @Inject constructor(
    private val recentFileRepository: RecentFileRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    init {
        PDFBoxResourceLoader.init(context)
    }

    var selectedPdfUri by mutableStateOf<Uri?>(null)
        private set

    var selectedPdfName by mutableStateOf<String?>(null)
        private set

    var watermarkText by mutableStateOf("CONFIDENTIAL")
    var rotationAngle by mutableStateOf(45f)
    var opacityAlpha by mutableStateOf(0.3f)
    var fontSize by mutableStateOf(60f)

    var isProcessing by mutableStateOf(false)
        private set

    var successMessage by mutableStateOf<String?>(null)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun selectPdf(uri: Uri) {
        selectedPdfUri = uri
        selectedPdfName = getFileNameFromUri(uri)
        resetStatus()
    }

    fun applyWatermark(outputUri: Uri) {
        val pdfUri = selectedPdfUri
        if (pdfUri == null) {
            errorMessage = "Please select a source PDF document first."
            return
        }
        if (watermarkText.isBlank()) {
            errorMessage = "Please enter valid watermark text."
            return
        }

        isProcessing = true
        resetStatus()

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                var doc: PDDocument? = null
                var tempInputFile: File? = null
                var tempOutputFile: File? = null

                try {
                    // Cache the source PDF file locally
                    tempInputFile = UriCacheUtils.cacheUriToFile(context, pdfUri)
                        ?: throw Exception("Could not cache source PDF file.")

                    doc = PDDocument.load(tempInputFile)
                    val font = PDType1Font.HELVETICA_BOLD
                    val numPages = doc.numberOfPages

                    // Sanitize text to ensure HELVETICA safe character mappings
                    val cleanText = sanitizeWatermarkText(watermarkText)

                    for (i in 0 until numPages) {
                        val page = doc.getPage(i)
                        val mediaBox = page.mediaBox
                        val width = mediaBox.width
                        val height = mediaBox.height

                        // Append content stream
                        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { contentStream ->
                            // Extended Graphics State for Opacity Alpha Transparency
                            val graphicsState = PDExtendedGraphicsState().apply {
                                nonStrokingAlphaConstant = opacityAlpha
                                strokingAlphaConstant = opacityAlpha
                            }
                            contentStream.setGraphicsStateParameters(graphicsState)

                            contentStream.setFont(font, fontSize)
                            contentStream.setNonStrokingColor(128, 128, 128) // Classic cool grey watermark

                            contentStream.beginText()

                            // Center calculation
                            val stringWidth = font.getStringWidth(cleanText) / 1000f * fontSize
                            val fontHeight = fontSize * 0.7f // Close approximation of baseline height

                            val centerX = width / 2f
                            val centerY = height / 2f

                            val rad = Math.toRadians(rotationAngle.toDouble()).toFloat()
                            val cos = Math.cos(rad.toDouble()).toFloat()
                            val sin = Math.sin(rad.toDouble()).toFloat()

                            // Rotated centering matrix
                            val tx = centerX - (cos * (stringWidth / 2f) - sin * (fontHeight / 2f))
                            val ty = centerY - (sin * (stringWidth / 2f) + cos * (fontHeight / 2f))

                            val matrix = Matrix(cos, sin, -sin, cos, tx, ty)
                            contentStream.setTextMatrix(matrix)
                            contentStream.showText(cleanText)
                            contentStream.endText()
                        }
                    }

                    tempOutputFile = File(context.cacheDir, "watermarked_${System.currentTimeMillis()}.pdf")
                    doc.save(tempOutputFile)
                    doc.close()
                    doc = null

                    // Copy to SAF Uri destination
                    context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                        tempOutputFile!!.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    } ?: throw Exception("Failed to open output stream for saved document.")

                    // Register in RecentFiles DB log
                    val outName = getFileNameFromUri(outputUri) ?: "Watermarked_Document.pdf"
                    val recent = RecentFile(
                        fileUri = outputUri.toString(),
                        fileName = outName,
                        mimeType = "application/pdf",
                        fileSize = tempOutputFile!!.length(),
                        lastOpened = System.currentTimeMillis()
                    )
                    recentFileRepository.insertRecentFile(recent)

                    successMessage = "Watermark applied successfully!"
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorMessage = "Failed to apply watermark: ${e.localizedMessage}"
                } finally {
                    try {
                        doc?.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    tempInputFile?.let { if (it.exists()) it.delete() }
                    tempOutputFile?.let { if (it.exists()) it.delete() }
                    isProcessing = false
                }
            }
        }
    }

    fun resetStatus() {
        successMessage = null
        errorMessage = null
    }

    private fun sanitizeWatermarkText(text: String): String {
        return text.map { char ->
            if (char.code in 32..126) char else '?'
        }.joinToString("")
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
}
