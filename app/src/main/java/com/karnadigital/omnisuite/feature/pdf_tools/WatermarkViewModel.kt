package com.karnadigital.omnisuite.feature.pdf_tools

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.core.repository.RecentFileRepository
import com.karnadigital.omnisuite.core.util.FileOutputManager
import com.karnadigital.omnisuite.core.util.UriCacheUtils
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

enum class WatermarkMode {
    TEXT,
    IMAGE,
    REMOVE
}

enum class WatermarkPosition(val label: String) {
    CENTER("Center"),
    TOP_LEFT("Top Left"),
    TOP_RIGHT("Top Right"),
    BOTTOM_LEFT("Bottom Left"),
    BOTTOM_RIGHT("Bottom Right"),
    DIAGONAL_REPEAT("Tile 3x3")
}

@HiltViewModel
class WatermarkViewModel @Inject constructor(
    private val recentFileRepository: RecentFileRepository,
    @ApplicationContext private val context: Context,
    private val fileOutputManager: FileOutputManager,
    private val uriCacheUtils: UriCacheUtils
) : ViewModel() {

    init {
        PDFBoxResourceLoader.init(context)
    }

    var selectedPdfUri by mutableStateOf<Uri?>(null)
        private set

    var selectedPdfName by mutableStateOf<String?>(null)
        private set

    var watermarkMode by mutableStateOf(WatermarkMode.TEXT)
    var watermarkText by mutableStateOf("CONFIDENTIAL")
    var rotationAngle by mutableFloatStateOf(45f)
    var opacityAlpha by mutableFloatStateOf(0.35f)
    var fontSize by mutableFloatStateOf(52f)
    var watermarkPosition by mutableStateOf(WatermarkPosition.CENTER)

    // Image watermark options
    var selectedImageUri by mutableStateOf<Uri?>(null)
        private set
    var selectedImageName by mutableStateOf<String?>(null)
        private set
    var imageScalePercent by mutableFloatStateOf(45f)

    // Remover options
    var removeAnnotationWatermarks by mutableStateOf(true)
    var removeWhiteoutMask by mutableStateOf(false)

    var isProcessing by mutableStateOf(false)
        private set

    var successMessage by mutableStateOf<String?>(null)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var successUri by mutableStateOf<Uri?>(null)
        private set
    var successName by mutableStateOf<String?>(null)
        private set
    var lastOutputBytes by mutableStateOf<ByteArray?>(null)
        private set

    var activeDocument: PDDocument? = null
        private set

    fun selectPdf(uri: Uri) {
        selectedPdfUri = uri
        selectedPdfName = getFileNameFromUri(uri)
        resetStatus()
    }

    fun selectImage(uri: Uri) {
        selectedImageUri = uri
        selectedImageName = getFileNameFromUri(uri)
    }

    fun applyWatermark(customFilename: String? = null) {
        val pdfUri = selectedPdfUri
        if (pdfUri == null) {
            errorMessage = "Please select a source PDF document first."
            return
        }
        if (watermarkMode == WatermarkMode.TEXT && watermarkText.isBlank()) {
            errorMessage = "Please enter valid watermark text."
            return
        }
        if (watermarkMode == WatermarkMode.IMAGE && selectedImageUri == null) {
            errorMessage = "Please select a watermark image or logo."
            return
        }

        isProcessing = true
        resetStatus()
        successUri = null
        successName = null
        lastOutputBytes = null

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                var doc: PDDocument? = null
                var tempInputFile: File? = null
                var tempOutputFile: File? = null

                try {
                    tempInputFile = uriCacheUtils.cacheUriToFile(pdfUri)
                        ?: throw Exception("Could not cache source PDF file.")

                    doc = PDDocument.load(tempInputFile)
                    activeDocument = doc
                    val numPages = doc.numberOfPages

                    when (watermarkMode) {
                        WatermarkMode.REMOVE -> {
                            // Watermark remover: strip annotation stamps and/or draw whiteout masks
                            for (i in 0 until numPages) {
                                val page = doc.getPage(i)
                                if (removeAnnotationWatermarks) {
                                    try {
                                        val annotations = page.annotations
                                        val toRemove = annotations.filter { annot ->
                                            val sub = annot.subtype ?: ""
                                            sub.equals("Watermark", ignoreCase = true) ||
                                            sub.equals("Stamp", ignoreCase = true) ||
                                            (annot.contents?.contains("watermark", ignoreCase = true) == true)
                                        }
                                        toRemove.forEach { annotations.remove(it) }
                                        page.annotations = annotations
                                    } catch (_: Throwable) {}
                                }
                                if (removeWhiteoutMask) {
                                    val mediaBox = page.mediaBox
                                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                                        cs.setNonStrokingColor(255, 255, 255)
                                        val maskW = mediaBox.width * 0.7f
                                        val maskH = 60f
                                        val mx = (mediaBox.width - maskW) / 2f
                                        val my = (mediaBox.height - maskH) / 2f
                                        cs.addRect(mx, my, maskW, maskH)
                                        cs.fill()
                                    }
                                }
                            }
                        }
                        WatermarkMode.IMAGE -> {
                            val imgUri = selectedImageUri ?: throw Exception("Watermark image not selected")
                            val inputStream = context.contentResolver.openInputStream(imgUri)
                                ?: throw Exception("Could not read watermark image")
                            val imageBitmap = BitmapFactory.decodeStream(inputStream)
                                ?: throw Exception("Invalid image file format")
                            val pdImage = LosslessFactory.createFromImage(doc, imageBitmap)

                            for (i in 0 until numPages) {
                                val page = doc.getPage(i)
                                val mediaBox = page.mediaBox
                                val width = mediaBox.width
                                val height = mediaBox.height

                                val targetWidth = (width * (imageScalePercent / 100f)).coerceIn(30f, width * 0.95f)
                                val aspectRatio = imageBitmap.height.toFloat() / imageBitmap.width.toFloat().coerceAtLeast(1f)
                                val targetHeight = targetWidth * aspectRatio

                                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                                    val graphicsState = PDExtendedGraphicsState().apply {
                                        nonStrokingAlphaConstant = opacityAlpha
                                        strokingAlphaConstant = opacityAlpha
                                    }
                                    cs.setGraphicsStateParameters(graphicsState)

                                    if (watermarkPosition == WatermarkPosition.DIAGONAL_REPEAT) {
                                        val cols = 3
                                        val rows = 3
                                        val cellW = width / cols
                                        val cellH = height / rows
                                        val stampW = targetWidth * 0.6f
                                        val stampH = targetHeight * 0.6f
                                        for (r in 0 until rows) {
                                            for (c in 0 until cols) {
                                                val x = c * cellW + (cellW - stampW) / 2f
                                                val y = r * cellH + (cellH - stampH) / 2f
                                                cs.drawImage(pdImage, x, y, stampW, stampH)
                                            }
                                        }
                                    } else {
                                        val (x, y) = getPositionCoordinates(watermarkPosition, width, height, targetWidth, targetHeight)
                                        cs.drawImage(pdImage, x, y, targetWidth, targetHeight)
                                    }
                                }
                            }
                        }
                        WatermarkMode.TEXT -> {
                            val font = PDType1Font.HELVETICA_BOLD
                            val cleanText = sanitizeWatermarkText(watermarkText)

                            for (i in 0 until numPages) {
                                val page = doc.getPage(i)
                                val mediaBox = page.mediaBox
                                val width = mediaBox.width
                                val height = mediaBox.height

                                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                                    val graphicsState = PDExtendedGraphicsState().apply {
                                        nonStrokingAlphaConstant = opacityAlpha
                                        strokingAlphaConstant = opacityAlpha
                                    }
                                    cs.setGraphicsStateParameters(graphicsState)
                                    cs.setFont(font, fontSize)
                                    cs.setNonStrokingColor(128, 128, 128)

                                    val stringWidth = font.getStringWidth(cleanText) / 1000f * fontSize
                                    val fontHeight = fontSize * 0.7f

                                    if (watermarkPosition == WatermarkPosition.DIAGONAL_REPEAT) {
                                        val rad = Math.toRadians(rotationAngle.toDouble()).toFloat()
                                        val cos = Math.cos(rad.toDouble()).toFloat()
                                        val sin = Math.sin(rad.toDouble()).toFloat()
                                        val cols = 3
                                        val rows = 3
                                        val cellW = width / cols
                                        val cellH = height / rows

                                        for (r in 0 until rows) {
                                            for (c in 0 until cols) {
                                                val cx = c * cellW + cellW / 2f
                                                val cy = r * cellH + cellH / 2f
                                                val tx = cx - (cos * (stringWidth / 2f) - sin * (fontHeight / 2f))
                                                val ty = cy - (sin * (stringWidth / 2f) + cos * (fontHeight / 2f))

                                                cs.beginText()
                                                cs.setTextMatrix(Matrix(cos, sin, -sin, cos, tx, ty))
                                                cs.showText(cleanText)
                                                cs.endText()
                                            }
                                        }
                                    } else {
                                        val (cx, cy) = getCenterCoordinates(watermarkPosition, width, height, stringWidth, fontHeight)
                                        val rad = Math.toRadians(rotationAngle.toDouble()).toFloat()
                                        val cos = Math.cos(rad.toDouble()).toFloat()
                                        val sin = Math.sin(rad.toDouble()).toFloat()

                                        val tx = cx - (cos * (stringWidth / 2f) - sin * (fontHeight / 2f))
                                        val ty = cy - (sin * (stringWidth / 2f) + cos * (fontHeight / 2f))

                                        cs.beginText()
                                        cs.setTextMatrix(Matrix(cos, sin, -sin, cos, tx, ty))
                                        cs.showText(cleanText)
                                        cs.endText()
                                    }
                                }
                            }
                        }
                    }

                    val prefix = if (watermarkMode == WatermarkMode.REMOVE) "cleaned_" else "watermarked_"
                    tempOutputFile = File(context.cacheDir, "${prefix}${System.currentTimeMillis()}.pdf")
                    doc.save(tempOutputFile)
                    doc.close()
                    doc = null

                    val outName = customFilename ?: "${prefix}${System.currentTimeMillis()}.pdf"
                    val bytes = tempOutputFile!!.readBytes()
                    val savedUri = fileOutputManager.saveToDefault(
                        bytes = bytes,
                        filename = outName,
                        mimeType = "application/pdf",
                        subfolder = "Watermarked"
                    ) ?: throw Exception("Failed to save output PDF.")

                    val recent = RecentFile(
                        fileUri = savedUri.toString(),
                        fileName = outName,
                        mimeType = "application/pdf",
                        fileSize = tempOutputFile!!.length(),
                        lastOpened = System.currentTimeMillis()
                    )
                    recentFileRepository.insertRecentFile(recent)

                    successUri = savedUri
                    successName = outName
                    lastOutputBytes = bytes
                    successMessage = if (watermarkMode == WatermarkMode.REMOVE) "Watermarks cleaned successfully!" else "Watermark applied successfully!"
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorMessage = "Failed: ${e.localizedMessage}"
                } finally {
                    withContext(NonCancellable) {
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
    }

    private fun getPositionCoordinates(
        pos: WatermarkPosition,
        pageWidth: Float,
        pageHeight: Float,
        itemWidth: Float,
        itemHeight: Float
    ): Pair<Float, Float> {
        val margin = 36f
        return when (pos) {
            WatermarkPosition.CENTER, WatermarkPosition.DIAGONAL_REPEAT ->
                Pair((pageWidth - itemWidth) / 2f, (pageHeight - itemHeight) / 2f)
            WatermarkPosition.TOP_LEFT ->
                Pair(margin, pageHeight - itemHeight - margin)
            WatermarkPosition.TOP_RIGHT ->
                Pair(pageWidth - itemWidth - margin, pageHeight - itemHeight - margin)
            WatermarkPosition.BOTTOM_LEFT ->
                Pair(margin, margin)
            WatermarkPosition.BOTTOM_RIGHT ->
                Pair(pageWidth - itemWidth - margin, margin)
        }
    }

    private fun getCenterCoordinates(
        pos: WatermarkPosition,
        pageWidth: Float,
        pageHeight: Float,
        itemWidth: Float,
        itemHeight: Float
    ): Pair<Float, Float> {
        val margin = 48f
        return when (pos) {
            WatermarkPosition.CENTER, WatermarkPosition.DIAGONAL_REPEAT ->
                Pair(pageWidth / 2f, pageHeight / 2f)
            WatermarkPosition.TOP_LEFT ->
                Pair(margin + itemWidth / 2f, pageHeight - margin - itemHeight / 2f)
            WatermarkPosition.TOP_RIGHT ->
                Pair(pageWidth - margin - itemWidth / 2f, pageHeight - margin - itemHeight / 2f)
            WatermarkPosition.BOTTOM_LEFT ->
                Pair(margin + itemWidth / 2f, margin + itemHeight / 2f)
            WatermarkPosition.BOTTOM_RIGHT ->
                Pair(pageWidth - margin - itemWidth / 2f, margin + itemHeight / 2f)
        }
    }

    fun saveToCustomLocation(targetUri: Uri) {
        val bytes = lastOutputBytes ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(targetUri)?.use { out ->
                    out.write(bytes)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun resetStatus() {
        successMessage = null
        errorMessage = null
        successUri = null
        successName = null
        lastOutputBytes = null
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

    override fun onCleared() {
        super.onCleared()
        try {
            activeDocument?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}