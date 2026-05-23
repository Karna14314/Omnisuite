package com.karnadigital.omnisuite.feature.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karnadigital.omnisuite.core.engine.image.ImageUtils
import com.karnadigital.omnisuite.core.engine.image.OutputFormat
import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.core.repository.RecentFileRepository
import com.karnadigital.omnisuite.core.util.FileOutputManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * State representation for the offline Image Utilities UI.
 */
data class ImageToolsUiState(
    val selectedUri: Uri? = null,
    val originalWidth: Int = 0,
    val originalHeight: Int = 0,
    val originalSize: Long = 0,
    val resizeScale: Float = 1.0f,
    val compressionQuality: Int = 80,
    val rotationDegrees: Float = 0f,
    val outputFormat: OutputFormat = OutputFormat.JPEG,
    val isProcessing: Boolean = false,
    val processingMessage: String? = null,
    val isSuccess: Boolean = false,
    val successUri: Uri? = null,
    val successName: String? = null,
    val lastOutputBytes: ByteArray? = null
)

/**
 * ViewModel coordinating offline, low-overhead native Bitmap operations.
 */
@HiltViewModel
class ImageToolsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recentFileRepository: RecentFileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImageToolsUiState())
    val uiState: StateFlow<ImageToolsUiState> = _uiState.asStateFlow()

    private var originalBitmap: Bitmap? = null

    /**
     * Loads the selected image Uri off-thread safely, downscaling if required to prevent OOM.
     */
    fun loadSelectedImage(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessing = true,
                processingMessage = "Decoding selected image..."
            )
            withContext(Dispatchers.IO) {
                try {
                    var fileSize = 0L
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                        fileSize = afd.length
                    }

                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val options = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        BitmapFactory.decodeStream(inputStream, null, options)
                        val width = options.outWidth
                        val height = options.outHeight

                        // Re-open stream for decoding high-res images safely
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            // Scale down images larger than 3000px on either side to maintain memory balance
                            val scaleOpts = BitmapFactory.Options().apply {
                                inSampleSize = calculateInSampleSize(width, height, 3000, 3000)
                            }
                            val decoded = BitmapFactory.decodeStream(stream, null, scaleOpts)
                            if (decoded != null) {
                                originalBitmap = decoded
                                _uiState.value = _uiState.value.copy(
                                    selectedUri = uri,
                                    originalWidth = decoded.width,
                                    originalHeight = decoded.height,
                                    originalSize = fileSize,
                                    isProcessing = false,
                                    processingMessage = null,
                                    isSuccess = false
                                )
                            } else {
                                _uiState.value = _uiState.value.copy(
                                    isProcessing = false,
                                    processingMessage = "Failed to parse local image stream."
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        processingMessage = "Error opening image: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    /**
     * Updates the active scaling factor slider value.
     */
    fun updateScale(scale: Float) {
        _uiState.value = _uiState.value.copy(
            resizeScale = scale,
            isSuccess = false
        )
    }

    /**
     * Updates the compression quality slider value.
     */
    fun updateQuality(quality: Int) {
        _uiState.value = _uiState.value.copy(
            compressionQuality = quality,
            isSuccess = false
        )
    }

    /**
     * Increments rotation degrees in 90-degree steps.
     */
    fun rotateImage() {
        val newRotation = (_uiState.value.rotationDegrees + 90f) % 360f
        _uiState.value = _uiState.value.copy(
            rotationDegrees = newRotation,
            isSuccess = false
        )
    }

    /**
     * Resets rotation degrees back to 0.
     */
    fun resetRotation() {
        _uiState.value = _uiState.value.copy(
            rotationDegrees = 0f,
            isSuccess = false
        )
    }

    /**
     * Changes target file extension output format.
     */
    fun updateFormat(format: OutputFormat) {
        _uiState.value = _uiState.value.copy(
            outputFormat = format,
            isSuccess = false
        )
    }

    /**
     * Clear all current modifications and revert selection.
     */
    fun clearSelection() {
        originalBitmap = null
        _uiState.value = ImageToolsUiState()
    }

    /**
     * Safe asynchronous image manipulator pipeline.
     * Executes rotate/resize/compress processes and saves raw bytes to custom SAF destination.
     */
    fun processAndSaveImage(customFilename: String? = null) {
        val bitmap = originalBitmap
        if (bitmap == null) {
            _uiState.value = _uiState.value.copy(
                processingMessage = "No active image selected to process."
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessing = true,
                processingMessage = "Processing and exporting image..."
            )
            val currentState = _uiState.value

            withContext(Dispatchers.Default) {
                try {
                    // 1. Rotate Bitmap
                    var processed = ImageUtils.rotate(bitmap, currentState.rotationDegrees)

                    // 2. Resize Bitmap
                    if (currentState.resizeScale != 1.0f) {
                        val finalScaled = ImageUtils.resize(processed, currentState.resizeScale)
                        if (finalScaled != processed && processed != bitmap) {
                            processed.recycle()
                        }
                        processed = finalScaled
                    }

                    // 3. Compress and transcode
                    val encodedBytes = ImageUtils.compressAndEncode(
                        processed,
                        currentState.outputFormat,
                        currentState.compressionQuality
                    )

                    // Free memory
                    if (processed != bitmap) {
                        processed.recycle()
                    }

                    val outName = customFilename ?: "processed_${System.currentTimeMillis()}.${currentState.outputFormat.name.lowercase()}"
                    val mimeType = getMimeType(currentState.outputFormat)

                    // 4. Save to content provider Uri on IO pool
                    withContext(Dispatchers.IO) {
                        val savedUri = FileOutputManager.saveToDefault(
                            context = context,
                            bytes = encodedBytes,
                            filename = outName,
                            mimeType = mimeType,
                            subfolder = "Images"
                        ) ?: throw Exception("Failed to save image to OmniSuite default directory.")

                        val recentFile = RecentFile(
                            fileUri = savedUri.toString(),
                            fileName = outName,
                            mimeType = mimeType,
                            fileSize = encodedBytes.size.toLong(),
                            lastOpened = System.currentTimeMillis()
                        )
                        recentFileRepository.insertRecentFile(recentFile)

                        _uiState.value = _uiState.value.copy(
                            isProcessing = false,
                            processingMessage = "Image saved successfully!",
                            isSuccess = true,
                            successUri = savedUri,
                            successName = outName,
                            lastOutputBytes = encodedBytes
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        processingMessage = "Failed to process image: ${e.localizedMessage}",
                        isSuccess = false
                    )
                }
            }
        }
    }

    fun saveToCustomLocation(targetUri: Uri) {
        val bytes = _uiState.value.lastOutputBytes ?: return
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

    private fun getMimeType(format: OutputFormat): String {
        return when (format) {
            OutputFormat.PNG -> "image/png"
            OutputFormat.JPEG -> "image/jpeg"
            OutputFormat.WEBP -> "image/webp"
        }
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        name = cursor.getString(index)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (name == null) {
            name = uri.path
            val lastSlash = name?.lastIndexOf('/') ?: -1
            if (lastSlash != -1) {
                name = name?.substring(lastSlash + 1)
            }
        }
        return name
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    override fun onCleared() {
        super.onCleared()
        // Force garbage collector memory recycling
        originalBitmap = null
    }
}
