package com.karnadigital.omnisuite.feature.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karnadigital.omnisuite.core.engine.image.ImageUtils
import com.karnadigital.omnisuite.core.engine.image.OutputFormat
import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.core.repository.RecentFileRepository
import com.karnadigital.omnisuite.core.util.UriCacheUtils
import com.tomroush.pdfbox.pdmodel.PDDocument
import com.tomroush.pdfbox.pdmodel.encryption.AccessPermission
import com.tomroush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tomroush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

data class BatchUiState(
    val totalCount: Int = 0,
    val processedCount: Int = 0,
    val isProcessing: Boolean = false,
    val statusMessage: String = "",
    val isSuccess: Boolean = false,
    val isError: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class BatchOperationsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recentFileRepository: RecentFileRepository
) : ViewModel() {

    init {
        try {
            PDFBoxResourceLoader.init(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val _uiState = MutableStateFlow(BatchUiState())
    val uiState: StateFlow<BatchUiState> = _uiState.asStateFlow()

    private val _selectedUris = MutableStateFlow<List<Uri>>(emptyList())
    val selectedUris: StateFlow<List<Uri>> = _selectedUris.asStateFlow()

    private val _targetFolderUri = MutableStateFlow<Uri?>(null)
    val targetFolderUri: StateFlow<Uri?> = _targetFolderUri.asStateFlow()

    private val _targetFolderName = MutableStateFlow<String?>(null)
    val targetFolderName: StateFlow<String?> = _targetFolderName.asStateFlow()

    // Configuration states
    val resizeScale = MutableStateFlow(1.0f)
    val compressionQuality = MutableStateFlow(80)
    val outputFormat = MutableStateFlow(OutputFormat.JPEG)
    val lockPassword = MutableStateFlow("")

    fun selectFiles(uris: List<Uri>) {
        _selectedUris.value = uris
        _uiState.value = BatchUiState()
    }

    fun selectTargetFolder(uri: Uri, folderName: String) {
        _targetFolderUri.value = uri
        _targetFolderName.value = folderName
    }

    fun clearSelections() {
        _selectedUris.value = emptyList()
        _targetFolderUri.value = null
        _targetFolderName.value = null
        _uiState.value = BatchUiState()
    }

    /**
     * Executes concurrent image scaling, compression, and WebP transcode off the main thread.
     */
    fun runBatchImageLab() {
        val uris = _selectedUris.value
        val folderUri = _targetFolderUri.value
        if (uris.isEmpty()) {
            _uiState.value = BatchUiState(isError = true, errorMessage = "Please select one or more images.")
            return
        }
        if (folderUri == null) {
            _uiState.value = BatchUiState(isError = true, errorMessage = "Please select a target output folder.")
            return
        }

        val total = uris.size
        _uiState.value = BatchUiState(
            totalCount = total,
            processedCount = 0,
            isProcessing = true,
            statusMessage = "Starting concurrent image batch processing..."
        )

        val scale = resizeScale.value
        val quality = compressionQuality.value
        val format = outputFormat.value

        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                val processedCount = AtomicInteger(0)
                val targetDir = DocumentFile.fromTreeUri(context, folderUri)

                if (targetDir == null || !targetDir.exists()) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = BatchUiState(isError = true, errorMessage = "Target folder is inaccessible.")
                    }
                    return@withContext
                }

                // Process images concurrently using async
                val deferredJobs = uris.map { uri ->
                    async(Dispatchers.Default) {
                        try {
                            val originalName = getFileName(context, uri) ?: "image_${System.currentTimeMillis()}"
                            val baseName = originalName.substringBeforeLast(".")
                            val extension = format.name.lowercase()
                            val outputName = "${baseName}_processed.$extension"
                            val mimeType = getMimeType(format)

                            // Decode bitmap
                            var bitmap: Bitmap? = null
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                bitmap = BitmapFactory.decodeStream(stream)
                            }

                            if (bitmap == null) return@async false

                            // 1. Resize if required
                            var processed = bitmap!!
                            if (scale != 1.0f) {
                                val finalScaled = ImageUtils.resize(processed, scale)
                                if (finalScaled != processed) {
                                    processed.recycle()
                                }
                                processed = finalScaled
                            }

                            // 2. Compress and transcode
                            val bytes = ImageUtils.compressAndEncode(processed, format, quality)

                            // Clean up bitmap
                            processed.recycle()

                            // 3. Save to tree folder via DocumentFile
                            withContext(Dispatchers.IO) {
                                val newFile = targetDir.createFile(mimeType, outputName)
                                    ?: throw Exception("Could not create output file inside directory.")
                                
                                context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
                                    out.write(bytes)
                                }

                                // Register to room db
                                val recent = RecentFile(
                                    fileUri = newFile.uri.toString(),
                                    fileName = outputName,
                                    mimeType = mimeType,
                                    fileSize = bytes.size.toLong(),
                                    lastOpened = System.currentTimeMillis()
                                )
                                recentFileRepository.insertRecentFile(recent)
                            }

                            val currentProcessed = processedCount.incrementAndGet()
                            withContext(Dispatchers.Main) {
                                _uiState.value = _uiState.value.copy(
                                    processedCount = currentProcessed,
                                    statusMessage = "Processed $currentProcessed of $total images..."
                                )
                            }
                            true
                        } catch (e: Exception) {
                            e.printStackTrace()
                            false
                        }
                    }
                }

                val results = deferredJobs.awaitAll()
                val successCount = results.count { it }

                withContext(Dispatchers.Main) {
                    if (successCount == total) {
                        _uiState.value = BatchUiState(
                            totalCount = total,
                            processedCount = total,
                            isProcessing = false,
                            statusMessage = "Successfully processed and saved all $total images offline!",
                            isSuccess = true
                        )
                    } else {
                        _uiState.value = BatchUiState(
                            totalCount = total,
                            processedCount = successCount,
                            isProcessing = false,
                            statusMessage = "Batch completed with errors. Successfully processed $successCount of $total images.",
                            isSuccess = true
                        )
                    }
                }
            }
        }
    }

    /**
     * Executes concurrent PDF password locking off the main thread.
     */
    fun runBatchPdfLocker() {
        val uris = _selectedUris.value
        val folderUri = _targetFolderUri.value
        val password = lockPassword.value

        if (uris.isEmpty()) {
            _uiState.value = BatchUiState(isError = true, errorMessage = "Please select one or more PDF documents.")
            return
        }
        if (folderUri == null) {
            _uiState.value = BatchUiState(isError = true, errorMessage = "Please select a target output folder.")
            return
        }
        if (password.isBlank()) {
            _uiState.value = BatchUiState(isError = true, errorMessage = "Please enter a non-empty password passkey.")
            return
        }

        val total = uris.size
        _uiState.value = BatchUiState(
            totalCount = total,
            processedCount = 0,
            isProcessing = true,
            statusMessage = "Encrypting documents concurrently..."
        )

        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                val processedCount = AtomicInteger(0)
                val targetDir = DocumentFile.fromTreeUri(context, folderUri)

                if (targetDir == null || !targetDir.exists()) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = BatchUiState(isError = true, errorMessage = "Target folder is inaccessible.")
                    }
                    return@withContext
                }

                val deferredJobs = uris.map { uri ->
                    async(Dispatchers.IO) {
                        var tempInputFile: File? = null
                        var tempOutputFile: File? = null
                        try {
                            val originalName = getFileName(context, uri) ?: "document_${System.currentTimeMillis()}.pdf"
                            val baseName = originalName.substringBeforeLast(".")
                            val outputName = "${baseName}_protected.pdf"

                            tempInputFile = UriCacheUtils.cacheUriToFile(context, uri)
                                ?: throw Exception("Could not open source PDF file.")

                            tempOutputFile = File(context.cacheDir, "secured_batch_${System.currentTimeMillis()}.pdf")

                            PDDocument.load(tempInputFile).use { document ->
                                val ap = AccessPermission()
                                val spp = StandardProtectionPolicy(password, password, ap).apply {
                                    encryptionKeyLength = 128
                                }
                                document.protect(spp)

                                FileOutputStream(tempOutputFile).use { outStream ->
                                    document.save(outStream)
                                }
                            }

                            // Write to DocumentFile in Tree Directory
                            val newFile = targetDir.createFile("application/pdf", outputName)
                                ?: throw Exception("Could not create output file inside directory.")

                            context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
                                tempOutputFile.inputStream().use { inp ->
                                    inp.copyTo(out)
                                }
                            }

                            // Register to room db
                            val recent = RecentFile(
                                fileUri = newFile.uri.toString(),
                                fileName = outputName,
                                mimeType = "application/pdf",
                                fileSize = tempOutputFile.length(),
                                lastOpened = System.currentTimeMillis()
                            )
                            recentFileRepository.insertRecentFile(recent)

                            val currentProcessed = processedCount.incrementAndGet()
                            withContext(Dispatchers.Main) {
                                _uiState.value = _uiState.value.copy(
                                    processedCount = currentProcessed,
                                    statusMessage = "Protected $currentProcessed of $total PDFs..."
                                )
                            }
                            true
                        } catch (e: Exception) {
                            e.printStackTrace()
                            false
                        } finally {
                            try {
                                if (tempInputFile?.exists() == true) tempInputFile.delete()
                                if (tempOutputFile?.exists() == true) tempOutputFile.delete()
                            } catch (ex: Exception) {
                                ex.printStackTrace()
                            }
                        }
                    }
                }

                val results = deferredJobs.awaitAll()
                val successCount = results.count { it }

                withContext(Dispatchers.Main) {
                    if (successCount == total) {
                        _uiState.value = BatchUiState(
                            totalCount = total,
                            processedCount = total,
                            isProcessing = false,
                            statusMessage = "All $total PDF documents protected with password successfully!",
                            isSuccess = true
                        )
                    } else {
                        _uiState.value = BatchUiState(
                            totalCount = total,
                            processedCount = successCount,
                            isProcessing = false,
                            statusMessage = "Batch lock completed with errors. Locked $successCount of $total PDFs.",
                            isSuccess = true
                        )
                    }
                }
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

    private fun getFileName(context: Context, uri: Uri): String? {
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
}
