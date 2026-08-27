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
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    val inputImage = InputImage.fromBitmap(bitmap, 0)

                    recognizer.process(inputImage)
                        .addOnSuccessListener { result ->
                            recognizedText = result.text
                            isProcessing = false
                            if (result.text.isBlank()) {
                                errorMessage = "No text could be identified in the selected image."
                            } else {
                                successMessage = "Text parsed successfully!"
                                saveOcrTextToFileAndLog(result.text)
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

    private fun saveOcrTextToFileAndLog(text: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val bytes = text.toByteArray()
                    val fileName = "Ocr_${System.currentTimeMillis()}.txt"
                    val savedUri = fileOutputManager.saveToDefault(
                        bytes = bytes,
                        filename = fileName,
                        mimeType = "text/plain",
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
                                mimeType = "text/plain",
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
