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
import com.karnadigital.omnisuite.core.engine.image.ImageLabExtensions

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
    val lastOutputBytes: ByteArray? = null,
    
    // Adjustment & Visual Crop States
    val brightness: Float = 0f,
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    val filterType: String = "Normal",
    val previewBitmap: Bitmap? = null,
    
    // Compression Dual Mode
    val compressMode: String = "QUALITY", // "QUALITY" vs "TARGET_SIZE"
    val targetSizeKbText: String = "200",

    // Exact Dimension Resizing
    val targetWidthPx: Int = 0,
    val targetHeightPx: Int = 0,
    val keepAspectRatio: Boolean = true,
    
    // Premium Image Lab Extensions States
    val selectedStitchUris: List<Uri> = emptyList(),
    val extractedMediaUris: List<Uri> = emptyList(),
    val idFrontUri: Uri? = null,
    val idBackUri: Uri? = null,
    val watermarkText: String = "CONFIDENTIAL",
    val watermarkSize: Float = 60f,
    val watermarkAlpha: Int = 128,
    val watermarkRotation: Float = 45f
)

/**
 * ViewModel coordinating offline, low-overhead native Bitmap operations.
 */
@HiltViewModel
class ImageToolsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recentFileRepository: RecentFileRepository,
    private val imageLabExtensions: ImageLabExtensions,
    private val fileOutputManager: FileOutputManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImageToolsUiState())
    val uiState: StateFlow<ImageToolsUiState> = _uiState.asStateFlow()

    private var originalBitmap: Bitmap? = null
    private var originalPreviewBitmap: Bitmap? = null
    private var previewJob: kotlinx.coroutines.Job? = null

    private fun scaleBitmapToMax(bitmap: Bitmap, maxDim: Int): Bitmap {
        if (bitmap.width <= maxDim && bitmap.height <= maxDim) {
            return bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        }
        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val newWidth = if (ratio > 1) maxDim else (maxDim * ratio).toInt()
        val newHeight = if (ratio > 1) (maxDim / ratio).toInt() else maxDim
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun updatePreview() {
        val base = originalPreviewBitmap ?: return
        val state = _uiState.value
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            kotlinx.coroutines.delay(50)
            val adjusted = withContext(Dispatchers.Default) {
                ImageUtils.applyFilterAndAdjustments(
                    base,
                    state.brightness,
                    state.contrast,
                    state.saturation,
                    state.filterType
                )
            }
            _uiState.value = _uiState.value.copy(previewBitmap = adjusted)
        }
    }

    fun updateAdjustments(brightness: Float, contrast: Float, saturation: Float, filterType: String) {
        _uiState.value = _uiState.value.copy(
            brightness = brightness,
            contrast = contrast,
            saturation = saturation,
            filterType = filterType
        )
        updatePreview()
    }

    fun applyCroppedBitmap(cropped: Bitmap) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessing = true,
                processingMessage = "Applying visual crop..."
            )
            withContext(Dispatchers.Default) {
                originalBitmap = cropped
                originalPreviewBitmap = scaleBitmapToMax(cropped, 1000)
                _uiState.value = _uiState.value.copy(
                    originalWidth = cropped.width,
                    originalHeight = cropped.height,
                    isProcessing = false,
                    processingMessage = "Image cropped successfully!",
                    isSuccess = false
                )
                updatePreview()
            }
        }
    }

    fun applyCropRatios(left: Float, top: Float, right: Float, bottom: Float) {
        val bitmap = originalBitmap ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessing = true,
                processingMessage = "Applying visual crop..."
            )
            withContext(Dispatchers.Default) {
                try {
                    val x = (left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                    val y = (top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                    val w = ((right - left) * bitmap.width).toInt().coerceIn(1, bitmap.width - x)
                    val h = ((bottom - top) * bitmap.height).toInt().coerceIn(1, bitmap.height - y)
                    val cropped = Bitmap.createBitmap(bitmap, x, y, w, h)
                    if (cropped != bitmap) {
                        originalBitmap = cropped
                        originalPreviewBitmap = scaleBitmapToMax(cropped, 1000)
                        _uiState.value = _uiState.value.copy(
                            originalWidth = cropped.width,
                            originalHeight = cropped.height,
                            isProcessing = false,
                            processingMessage = "Image cropped successfully!",
                            isSuccess = false
                        )
                        updatePreview()
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isProcessing = false,
                            processingMessage = "Crop completed."
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        processingMessage = "Crop failed: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

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
                                originalPreviewBitmap = scaleBitmapToMax(decoded, 1000)
                                _uiState.value = _uiState.value.copy(
                                    selectedUri = uri,
                                    originalWidth = decoded.width,
                                    originalHeight = decoded.height,
                                    targetWidthPx = decoded.width,
                                    targetHeightPx = decoded.height,
                                    originalSize = fileSize,
                                    brightness = 0f,
                                    contrast = 1.0f,
                                    saturation = 1.0f,
                                    filterType = "Normal",
                                    isProcessing = false,
                                    processingMessage = null,
                                    isSuccess = false
                                )
                                updatePreview()
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

    fun updateCompressMode(mode: String) {
        _uiState.value = _uiState.value.copy(
            compressMode = mode,
            isSuccess = false
        )
    }

    fun updateTargetSizeKbText(text: String) {
        _uiState.value = _uiState.value.copy(
            targetSizeKbText = text,
            isSuccess = false
        )
    }

    fun updateTargetDimensions(width: Int, height: Int) {
        _uiState.value = _uiState.value.copy(
            targetWidthPx = width.coerceAtLeast(1),
            targetHeightPx = height.coerceAtLeast(1),
            isSuccess = false
        )
    }

    fun updateKeepAspectRatio(keep: Boolean) {
        _uiState.value = _uiState.value.copy(keepAspectRatio = keep)
    }

    fun applyDimensionPreset(targetW: Int, targetH: Int) {
        _uiState.value = _uiState.value.copy(
            targetWidthPx = targetW.coerceAtLeast(1),
            targetHeightPx = targetH.coerceAtLeast(1),
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
     * Center-crops the active image bitmap to a 1:1 perfect square offline.
     */
    fun cropToSquare() {
        val bitmap = originalBitmap ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessing = true,
                processingMessage = "Center-cropping image to square..."
            )
            withContext(Dispatchers.Default) {
                try {
                    val size = Math.min(bitmap.width, bitmap.height)
                    val x = (bitmap.width - size) / 2
                    val y = (bitmap.height - size) / 2
                    val cropped = Bitmap.createBitmap(bitmap, x, y, size, size)
                    if (cropped != bitmap) {
                        originalBitmap = cropped
                        originalPreviewBitmap = scaleBitmapToMax(cropped, 1000)
                        _uiState.value = _uiState.value.copy(
                            originalWidth = cropped.width,
                            originalHeight = cropped.height,
                            isProcessing = false,
                            processingMessage = "Image center-cropped to square successfully!",
                            isSuccess = false
                        )
                        updatePreview()
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isProcessing = false,
                            processingMessage = "Image is already a perfect square."
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        processingMessage = "Crop operation failed: ${e.localizedMessage}"
                    )
                }
            }
        }
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
        originalPreviewBitmap = null
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
                    val tw = currentState.targetWidthPx
                    val th = currentState.targetHeightPx
                    if (tw > 0 && th > 0 && (tw != processed.width || th != processed.height)) {
                        val finalScaled = Bitmap.createScaledBitmap(processed, tw, th, true)
                        if (finalScaled != processed && processed != bitmap) {
                            processed.recycle()
                        }
                        processed = finalScaled
                    } else if (currentState.resizeScale != 1.0f) {
                        val finalScaled = ImageUtils.resize(processed, currentState.resizeScale)
                        if (finalScaled != processed && processed != bitmap) {
                            processed.recycle()
                        }
                        processed = finalScaled
                    }

                    // 3. Apply Adjustments & Filters
                    val finalProcessed = ImageUtils.applyFilterAndAdjustments(
                        processed,
                        currentState.brightness,
                        currentState.contrast,
                        currentState.saturation,
                        currentState.filterType
                    )
                    if (finalProcessed != processed && processed != bitmap) {
                        processed.recycle()
                    }
                    processed = finalProcessed

                    // 4. Compress and transcode (with Target Size support)
                    var encodedBytes: ByteArray
                    if (currentState.compressMode == "TARGET_SIZE" && (currentState.targetSizeKbText.toIntOrNull() ?: 0) > 0) {
                        val targetBytes = ((currentState.targetSizeKbText.toIntOrNull() ?: 200) * 1024L).coerceAtLeast(10240L)
                        var testQuality = 85
                        var currentScale = 1.0f
                        var workingBitmap = processed
                        var bestEncoded = ImageUtils.compressAndEncode(workingBitmap, currentState.outputFormat, testQuality)
                        
                        while (bestEncoded.size > targetBytes && testQuality > 20) {
                            testQuality -= 12
                            bestEncoded = ImageUtils.compressAndEncode(workingBitmap, currentState.outputFormat, testQuality)
                        }
                        
                        while (bestEncoded.size > targetBytes && currentScale > 0.35f) {
                            currentScale -= 0.15f
                            val resized = ImageUtils.resize(processed, currentScale)
                            bestEncoded = ImageUtils.compressAndEncode(resized, currentState.outputFormat, testQuality.coerceAtMost(50))
                            if (resized != processed && resized != bitmap) {
                                resized.recycle()
                            }
                        }
                        encodedBytes = bestEncoded
                    } else {
                        encodedBytes = ImageUtils.compressAndEncode(
                            processed,
                            currentState.outputFormat,
                            currentState.compressionQuality
                        )
                    }

                    // Free memory
                    if (processed != bitmap) {
                        processed.recycle()
                    }

                    val outName = customFilename ?: "processed_${System.currentTimeMillis()}.${currentState.outputFormat.name.lowercase()}"
                    val mimeType = getMimeType(currentState.outputFormat)

                    // 5. Save to content provider Uri on IO pool
                    withContext(Dispatchers.IO) {
                        val savedUri = fileOutputManager.saveToDefault(
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

    fun selectStitchImages(uris: List<Uri>) {
        _uiState.value = _uiState.value.copy(selectedStitchUris = uris, isSuccess = false)
    }

    fun selectIdFrontImage(uri: Uri?) {
        _uiState.value = _uiState.value.copy(idFrontUri = uri, isSuccess = false)
    }

    fun selectIdBackImage(uri: Uri?) {
        _uiState.value = _uiState.value.copy(idBackUri = uri, isSuccess = false)
    }

    fun updateWatermarkText(text: String) {
        _uiState.value = _uiState.value.copy(watermarkText = text, isSuccess = false)
    }

    fun updateWatermarkSize(size: Float) {
        _uiState.value = _uiState.value.copy(watermarkSize = size, isSuccess = false)
    }

    fun updateWatermarkAlpha(alpha: Int) {
        _uiState.value = _uiState.value.copy(watermarkAlpha = alpha, isSuccess = false)
    }

    fun updateWatermarkRotation(rotation: Float) {
        _uiState.value = _uiState.value.copy(watermarkRotation = rotation, isSuccess = false)
    }

    fun stitchImages() {
        val uris = _uiState.value.selectedStitchUris
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessing = true,
                processingMessage = "Stitching images vertically..."
            )
            val resultUri = imageLabExtensions.stitchImagesVertically(uris)
            if (resultUri != null) {
                val outName = "stitched_${System.currentTimeMillis()}.jpg"
                val recentFile = RecentFile(
                    fileUri = resultUri.toString(),
                    fileName = outName,
                    mimeType = "image/jpeg",
                    fileSize = 0L,
                    lastOpened = System.currentTimeMillis()
                )
                recentFileRepository.insertRecentFile(recentFile)
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    processingMessage = "Images stitched successfully!",
                    isSuccess = true,
                    successUri = resultUri,
                    successName = outName,
                    lastOutputBytes = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    processingMessage = "Stitching failed."
                )
            }
        }
    }

    fun extractMedia(docUri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessing = true,
                processingMessage = "Extracting document media..."
            )
            val resultUris = imageLabExtensions.extractMediaFromDocument(docUri)
            if (resultUris.isNotEmpty()) {
                resultUris.forEachIndexed { idx, uri ->
                    val outName = "extracted_${System.currentTimeMillis()}_$idx.jpg"
                    recentFileRepository.insertRecentFile(
                        RecentFile(
                            fileUri = uri.toString(),
                            fileName = outName,
                            mimeType = "image/jpeg",
                            fileSize = 0L,
                            lastOpened = System.currentTimeMillis()
                        )
                    )
                }
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    processingMessage = "Extracted ${resultUris.size} media files successfully!",
                    isSuccess = true,
                    extractedMediaUris = resultUris,
                    successUri = resultUris.firstOrNull(),
                    successName = "Extracted Media files",
                    lastOutputBytes = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    processingMessage = "No media files extracted."
                )
            }
        }
    }

    fun makeIdCard() {
        val front = _uiState.value.idFrontUri ?: return
        val back = _uiState.value.idBackUri ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessing = true,
                processingMessage = "Creating ID card print template..."
            )
            val resultUri = imageLabExtensions.createIdCardPrintTemplate(front, back)
            if (resultUri != null) {
                val outName = "id_template_${System.currentTimeMillis()}.jpg"
                val recentFile = RecentFile(
                    fileUri = resultUri.toString(),
                    fileName = outName,
                    mimeType = "image/jpeg",
                    fileSize = 0L,
                    lastOpened = System.currentTimeMillis()
                )
                recentFileRepository.insertRecentFile(recentFile)
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    processingMessage = "ID Card Print Template saved!",
                    isSuccess = true,
                    successUri = resultUri,
                    successName = outName,
                    lastOutputBytes = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    processingMessage = "Failed to generate print template."
                )
            }
        }
    }

    fun applyCustomWatermark() {
        val uri = _uiState.value.selectedUri ?: return
        val text = _uiState.value.watermarkText
        val size = _uiState.value.watermarkSize
        val alpha = _uiState.value.watermarkAlpha
        val rotation = _uiState.value.watermarkRotation
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessing = true,
                processingMessage = "Applying watermark..."
            )
            val resultUri = imageLabExtensions.addCustomWatermark(uri, text, size, alpha, rotation)
            if (resultUri != null) {
                val outName = "watermarked_${System.currentTimeMillis()}.jpg"
                val recentFile = RecentFile(
                    fileUri = resultUri.toString(),
                    fileName = outName,
                    mimeType = "image/jpeg",
                    fileSize = 0L,
                    lastOpened = System.currentTimeMillis()
                )
                recentFileRepository.insertRecentFile(recentFile)
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    processingMessage = "Watermark applied successfully!",
                    isSuccess = true,
                    successUri = resultUri,
                    successName = outName,
                    lastOutputBytes = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    processingMessage = "Watermark operation failed."
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Release all bitmap resources to prevent memory leaks
        originalBitmap?.recycle()
        originalBitmap = null
        originalPreviewBitmap?.recycle()
        originalPreviewBitmap = null
        previewJob?.cancel()
        previewJob = null
    }
}
