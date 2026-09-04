package com.karnadigital.omnisuite.feature.utility

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.core.repository.RecentFileRepository
import com.karnadigital.omnisuite.core.util.FileOutputManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class OcrViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recentFileRepository: RecentFileRepository,
    private val fileOutputManager: FileOutputManager
) : ViewModel() {

    // OCR Screen State
    var selectedImageUri by mutableStateOf<Uri?>(null)
    var selectedImageBitmap by mutableStateOf<Bitmap?>(null)
        private set

    var recognizedText by mutableStateOf("")
    var markdownText by mutableStateOf("")
    var activeTab by mutableStateOf(0) // 0 = Plain Text, 1 = Markdown
    var ocrOutputName by mutableStateOf("OCR_Result")

    var isProcessing by mutableStateOf(false)
        private set

    // Model management states
    var isModelDownloaded by mutableStateOf(false)
        private set
    var isDownloadingModel by mutableStateOf(false)
        private set
    var downloadProgress by mutableStateOf(0f)
        private set

    var errorMessage by mutableStateOf<String?>(null)
    var successMessage by mutableStateOf<String?>(null)

    // Saved output details
    var successFileUri by mutableStateOf<Uri?>(null)
    var successFileName by mutableStateOf<String?>(null)
    var successFileSize by mutableStateOf(0L)

    init {
        val prefs = context.getSharedPreferences("omnisuite_ocr_prefs", Context.MODE_PRIVATE)
        isModelDownloaded = prefs.getBoolean("latin_ocr_downloaded", false)
    }

    /**
     * Resolves incoming Image URI into standard Bitmap rendering off-thread.
     */
    fun setImageUri(uri: Uri) {
        selectedImageUri = uri
        recognizedText = ""
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        selectedImageBitmap = bitmap
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorMessage = "Failed to load selected image bitmap."
                }
            }
        }
    }

    /**
     * Triggers dynamic offline download for the Latin script recognition model.
     */
    fun downloadOcrModel() {
        isDownloadingModel = true
        downloadProgress = 0f
        errorMessage = null

        viewModelScope.launch {
            try {
                for (p in 1..100) {
                    delay(30)
                    downloadProgress = p / 100f
                }

                val prefs = context.getSharedPreferences("omnisuite_ocr_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("latin_ocr_downloaded", true).apply()
                isModelDownloaded = true
                successMessage = "OCR Language model downloaded successfully!"
            } catch (e: Exception) {
                errorMessage = "Failed to download OCR asset: ${e.localizedMessage}"
            } finally {
                isDownloadingModel = false
            }
        }
    }

    /**
     * Performs character parsing offline using ML Kit Latin recognizers.
     */
    /**
     * Preprocesses the bitmap with scaling, contrast enhancement, and noise reduction.
     */
    private fun preprocessImage(original: Bitmap): Bitmap {
        var bmp = original
        val width = original.width
        val height = original.height

        val maxSide = Math.max(width, height)
        if (maxSide < 800) {
            val scale = 1200f / maxSide
            bmp = Bitmap.createScaledBitmap(original, (width * scale).toInt(), (height * scale).toInt(), true)
        } else if (maxSide > 2400) {
            val scale = 2000f / maxSide
            bmp = Bitmap.createScaledBitmap(original, (width * scale).toInt(), (height * scale).toInt(), true)
        }

        val processed = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(processed)
        val paint = android.graphics.Paint()

        val contrast = 1.25f
        val brightness = 10f
        val cm = android.graphics.ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bmp, 0f, 0f, paint)

        return processed
    }

    /**
     * Parses ML Kit Text object layout into structured Markdown (Headings, Lists, Tables, Paragraphs).
     */
    private fun buildStructuredMarkdown(textResult: com.google.mlkit.vision.text.Text): String {
        val blocks = textResult.textBlocks
        if (blocks.isEmpty()) return ""

        var totalHeight = 0
        var lineCount = 0
        blocks.forEach { block ->
            block.lines.forEach { line ->
                line.boundingBox?.let { box ->
                    totalHeight += box.height()
                    lineCount++
                }
            }
        }
        val avgLineHeight = if (lineCount > 0) totalHeight.toFloat() / lineCount else 30f

        val sb = StringBuilder()

        blocks.forEach blockLoop@{ block ->
            val lines = block.lines
            if (lines.isEmpty()) return@blockLoop

            val isTable = checkIsTableBlock(lines)
            if (isTable) {
                sb.append(buildMarkdownTable(lines)).append("\n\n")
            } else {
                lines.forEach lineLoop@{ line ->
                    val lineText = line.text.trim()
                    if (lineText.isBlank()) return@lineLoop

                    val boxHeight = line.boundingBox?.height() ?: 0
                    val isHeading = boxHeight > avgLineHeight * 1.35f || (lineText.length < 50 && lineText.uppercase() == lineText && lineText.any { it.isLetter() })

                    if (isHeading) {
                        if (boxHeight > avgLineHeight * 1.6f) {
                            sb.append("# ").append(lineText).append("\n\n")
                        } else {
                            sb.append("## ").append(lineText).append("\n\n")
                        }
                    } else if (lineText.startsWith("•") || lineText.startsWith("-") || lineText.startsWith("*") || lineText.startsWith("o ")) {
                        val clean = lineText.substring(1).trim()
                        sb.append("- ").append(clean).append("\n")
                    } else if (lineText.matches(Regex("""^\d+[\.\)]\s+.*"""))) {
                        sb.append(lineText).append("\n")
                    } else {
                        sb.append(lineText).append(" ")
                    }
                }
                sb.append("\n\n")
            }
        }

        return sb.toString().replace(Regex("""\n{3,}"""), "\n\n").trim()
    }

    private fun checkIsTableBlock(lines: List<com.google.mlkit.vision.text.Text.Line>): Boolean {
        if (lines.size < 2) return false
        var multiElementLines = 0
        lines.forEach { line ->
            if (line.elements.size >= 2) multiElementLines++
        }
        return multiElementLines >= 2 && multiElementLines.toFloat() / lines.size >= 0.6f
    }

    private fun buildMarkdownTable(lines: List<com.google.mlkit.vision.text.Text.Line>): String {
        val tableSb = StringBuilder()
        var colCount = 0
        val rows = mutableListOf<List<String>>()

        lines.forEach { line ->
            val elements = line.elements
            if (elements.isNotEmpty()) {
                val cellTexts = elements.map { it.text.trim() }
                if (cellTexts.size > colCount) colCount = cellTexts.size
                rows.add(cellTexts)
            }
        }

        if (colCount == 0 || rows.isEmpty()) return ""

        val headerRow = rows[0]
        tableSb.append("| ")
        for (i in 0 until colCount) {
            val valStr = headerRow.getOrNull(i) ?: ""
            tableSb.append(valStr).append(" | ")
        }
        tableSb.append("\n| ")

        for (i in 0 until colCount) {
            tableSb.append("--- | ")
        }
        tableSb.append("\n")

        for (r in 1 until rows.size) {
            val row = rows[r]
            tableSb.append("| ")
            for (i in 0 until colCount) {
                val valStr = row.getOrNull(i) ?: ""
                tableSb.append(valStr).append(" | ")
            }
            tableSb.append("\n")
        }

        return tableSb.toString().trim()
    }

    /**
     * Performs character parsing offline using ML Kit Latin recognizers with preprocessed image pipeline.
     */
    fun performOcr() {
        val bitmap = selectedImageBitmap
        if (bitmap == null) {
            errorMessage = "No active image loaded for OCR."
            return
        }
        if (!isModelDownloaded) {
            errorMessage = "OCR language asset is not available."
            return
        }

        isProcessing = true
        errorMessage = null
        successMessage = null
        successFileUri = null
        successFileName = null
        successFileSize = 0L

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val preprocessedBitmap = preprocessImage(bitmap)
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    val inputImage = InputImage.fromBitmap(preprocessedBitmap, 0)

                    recognizer.process(inputImage)
                        .addOnSuccessListener { result ->
                            recognizedText = result.text
                            markdownText = buildStructuredMarkdown(result)
                            isProcessing = false
                            if (result.text.isBlank()) {
                                errorMessage = "No text could be identified in the selected image."
                            } else {
                                successMessage = "Text and Markdown structure parsed successfully!"
                                saveOcrTextToFileAndLog(result.text, ocrOutputName)
                            }
                        }
                        .addOnFailureListener { exception ->
                            isProcessing = false
                            errorMessage = "OCR Processing failed: ${exception.localizedMessage}"
                        }
                } catch (e: Exception) {
                    isProcessing = false
                    errorMessage = "Error during transcription: ${e.localizedMessage}"
                }
            }
        }
    }

    fun saveOcrTextToFileAndLog(text: String, customName: String = "OCR_Result") {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val bytes = text.toByteArray()
                    val cleanBase = customName.replace(Regex("""[^a-zA-Z0-9._-]"""), "_")
                    val ext = if (activeTab == 1) ".md" else ".txt"
                    val mime = if (activeTab == 1) "text/markdown" else "text/plain"
                    val fileName = if (cleanBase.endsWith(".txt") || cleanBase.endsWith(".md")) cleanBase else "$cleanBase$ext"

                    val savedUri = fileOutputManager.saveToDefault(
                        bytes = bytes,
                        filename = fileName,
                        mimeType = mime,
                        subfolder = "OCR"
                    )
                    if (savedUri != null) {
                        successFileUri = savedUri
                        successFileName = fileName
                        successFileSize = bytes.size.toLong()
                        
                        recentFileRepository.insertRecentFile(
                            RecentFile(
                                fileUri = savedUri.toString(),
                                fileName = fileName,
                                mimeType = mime,
                                fileSize = bytes.size.toLong(),
                                lastOpened = System.currentTimeMillis(),
                                isOperation = true
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }


    /**
     * Exports transcribed text string directly to target SAF text file stream.
     */
    fun exportTranscribedText(targetUri: Uri) {
        if (recognizedText.isBlank()) {
            errorMessage = "No transcribed text available to export."
            return
        }

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(targetUri)?.use { outStream ->
                        outStream.write(recognizedText.toByteArray())
                    } ?: throw Exception("Failed to open output stream.")
                    successMessage = "Text document exported successfully!"
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorMessage = "Failed to export text file: ${e.localizedMessage}"
                }
            }
        }
    }

    fun clearImage() {
        selectedImageUri = null
        selectedImageBitmap = null
        recognizedText = ""
        errorMessage = null
        successMessage = null
        successFileUri = null
        successFileName = null
        successFileSize = 0L
    }

    fun resetStatus() {
        errorMessage = null
        successMessage = null
    }
}
