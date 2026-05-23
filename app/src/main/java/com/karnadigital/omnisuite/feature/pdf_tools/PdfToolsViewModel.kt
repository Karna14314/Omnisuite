package com.karnadigital.omnisuite.feature.pdf_tools

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.core.repository.RecentFileRepository
import com.karnadigital.omnisuite.core.util.UriCacheUtils
import com.karnadigital.omnisuite.core.util.FileOutputManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject

@HiltViewModel
class PdfToolsViewModel @Inject constructor(
    private val recentFileRepository: RecentFileRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    init {
        // Initialize PDFBox resource loader once for dynamic assets mapping
        PDFBoxResourceLoader.init(context)
    }

    // State Variables
    var selectedMergeUris by mutableStateOf<List<Uri>>(emptyList())
        private set

    var splitInputUri by mutableStateOf<Uri?>(null)
    var splitRanges by mutableStateOf("1-3, 4-6")

    var lockInputUri by mutableStateOf<Uri?>(null)
    var lockPassword by mutableStateOf("")

    var pptInputUri by mutableStateOf<Uri?>(null)
    var pptRenderMode by mutableStateOf("image") // "image" or "text"

    var docInputUri by mutableStateOf<Uri?>(null)

    var scanInputUri by mutableStateOf<Uri?>(null)

    var pdfToImagesInputUri by mutableStateOf<Uri?>(null)

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

    fun addMergeUri(uri: Uri) {
        if (!selectedMergeUris.contains(uri)) {
            selectedMergeUris = selectedMergeUris + uri
        }
    }

    fun removeMergeUri(uri: Uri) {
        selectedMergeUris = selectedMergeUris - uri
    }

    fun reorderMergeUris(fromIndex: Int, toIndex: Int) {
        if (fromIndex in selectedMergeUris.indices && toIndex in selectedMergeUris.indices) {
            val list = selectedMergeUris.toMutableList()
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            selectedMergeUris = list
        }
    }

    fun clearMergeUris() {
        selectedMergeUris = emptyList()
    }

    fun resetStatus() {
        successMessage = null
        errorMessage = null
    }

    /**
     * Combines all selected document Uris sequentially using PDFMergerUtility
     * and streams the output directly to the destination SAF Uri.
     */
    fun mergePdfs(customFilename: String? = null) {
        if (selectedMergeUris.size < 2) {
            errorMessage = "Please select at least 2 PDF documents to merge."
            return
        }
        isProcessing = true
        resetStatus()
        successUri = null
        successName = null
        lastOutputBytes = null

        viewModelScope.launch {
            val tempFiles = mutableListOf<File>()
            var tempOutputFile: File? = null

            try {
                // Cache incoming SAF content streams to sandbox files
                withContext(Dispatchers.IO) {
                    selectedMergeUris.forEach { uri ->
                        val file = UriCacheUtils.cacheUriToFile(context, uri)
                        if (file != null) {
                            tempFiles.add(file)
                        } else {
                            throw Exception("Failed to cache file: $uri")
                        }
                    }

                    // Create output sandbox representation
                    tempOutputFile = File(context.cacheDir, "merged_output_${System.currentTimeMillis()}.pdf")

                    val merger = PDFMergerUtility()
                    tempFiles.forEach { file ->
                        merger.addSource(file)
                    }

                    FileOutputStream(tempOutputFile).use { outStream ->
                        merger.destinationStream = outStream
                        merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
                    }

                    val outName = customFilename ?: "merged_${System.currentTimeMillis()}.pdf"
                    val bytes = tempOutputFile!!.readBytes()
                    val savedUri = FileOutputManager.saveToDefault(
                        context = context,
                        bytes = bytes,
                        filename = outName,
                        mimeType = "application/pdf",
                        subfolder = "PDF"
                    ) ?: throw Exception("Failed to save merged PDF to OmniSuite/PDF folder.")

                    // Register to Room RecentFiles cache
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
                }

                successMessage = "PDF documents merged successfully!"
                selectedMergeUris = emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Error during merge: ${e.localizedMessage}"
            } finally {
                // Aggressive cache cleanup to prevent memory bloating
                withContext(Dispatchers.IO) {
                    tempFiles.forEach { if (it.exists()) it.delete() }
                    tempOutputFile?.let { if (it.exists()) it.delete() }
                }
                isProcessing = false
            }
        }
    }

    /**
     * Splits an incoming PDF Uri into distinct standalone page ranges saved to an SAF Directory.
     */
    fun splitPdf() {
        val inputUri = splitInputUri
        if (inputUri == null) {
            errorMessage = "Please select a source PDF document first."
            return
        }
        if (splitRanges.isBlank()) {
            errorMessage = "Please enter valid page ranges (e.g. 1-2, 3-5)."
            return
        }
        isProcessing = true
        resetStatus()
        successUri = null
        successName = null
        lastOutputBytes = null

        viewModelScope.launch {
            var tempInputFile: File? = null
            val createdTempFiles = mutableListOf<File>()

            try {
                withContext(Dispatchers.IO) {
                    // Cache the source PDF file
                    tempInputFile = UriCacheUtils.cacheUriToFile(context, inputUri)
                        ?: throw Exception("Could not open source PDF file.")

                    // Parse ranges
                    val parsedRanges = parseRanges(splitRanges)
                    if (parsedRanges.isEmpty()) {
                        throw Exception("No valid page ranges parsed. Use format: 1-3, 5-8")
                    }

                    PDDocument.load(tempInputFile).use { mainDocument ->
                        val totalPages = mainDocument.numberOfPages

                        val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".pdf")

                        parsedRanges.forEachIndexed { index, range ->
                            val startPage = range.first
                            val endPage = range.second

                            if (startPage < 1 || endPage > totalPages || startPage > endPage) {
                                throw Exception("Range ${startPage}-${endPage} is out of bounds (1 to $totalPages).")
                            }

                            PDDocument().use { subDoc ->
                                for (p in startPage..endPage) {
                                    subDoc.addPage(mainDocument.getPage(p - 1))
                                }

                                val subFileName = "${originalName}_part_${startPage}_to_${endPage}.pdf"
                                val tempSubFile = File(context.cacheDir, "split_${System.currentTimeMillis()}_$index.pdf")
                                createdTempFiles.add(tempSubFile)

                                FileOutputStream(tempSubFile).use { out ->
                                    subDoc.save(out)
                                }

                                val bytes = tempSubFile.readBytes()
                                val savedUri = FileOutputManager.saveToDefault(
                                    context = context,
                                    bytes = bytes,
                                    filename = subFileName,
                                    mimeType = "application/pdf",
                                    subfolder = "PDF"
                                ) ?: throw Exception("Failed to save split file to OmniSuite folder.")

                                // Register to recent files
                                val recent = RecentFile(
                                    fileUri = savedUri.toString(),
                                    fileName = subFileName,
                                    mimeType = "application/pdf",
                                    fileSize = tempSubFile.length(),
                                    lastOpened = System.currentTimeMillis()
                                )
                                recentFileRepository.insertRecentFile(recent)

                                if (index == 0) {
                                    successUri = savedUri
                                    successName = subFileName
                                    lastOutputBytes = bytes
                                }
                            }
                        }
                    }
                }

                successMessage = "PDF split operation completed successfully!"
                splitInputUri = null
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Error during split: ${e.localizedMessage}"
            } finally {
                withContext(Dispatchers.IO) {
                    tempInputFile?.let { if (it.exists()) it.delete() }
                    createdTempFiles.forEach { if (it.exists()) it.delete() }
                }
                isProcessing = false
            }
        }
    }
    fun encryptPdf(customFilename: String? = null) {
        val inputUri = lockInputUri
        if (inputUri == null) {
            errorMessage = "Please select a source PDF document first."
            return
        }
        if (lockPassword.isBlank()) {
            errorMessage = "Password lock field cannot be empty."
            return
        }
        isProcessing = true
        resetStatus()
        successUri = null
        successName = null
        lastOutputBytes = null

        viewModelScope.launch {
            var tempInputFile: File? = null
            var tempOutputFile: File? = null

            try {
                withContext(Dispatchers.IO) {
                    // Cache the source PDF file
                    tempInputFile = UriCacheUtils.cacheUriToFile(context, inputUri)
                        ?: throw Exception("Could not open source PDF file.")

                    tempOutputFile = File(context.cacheDir, "secured_${System.currentTimeMillis()}.pdf")

                    PDDocument.load(tempInputFile).use { document ->
                        val ap = AccessPermission()
                        // 128-bit encryption standard protection policies
                        val spp = StandardProtectionPolicy(lockPassword, lockPassword, ap).apply {
                            encryptionKeyLength = 128
                        }
                        document.protect(spp)

                        FileOutputStream(tempOutputFile).use { outStream ->
                            document.save(outStream)
                        }
                    }

                    val outName = customFilename ?: "secured_${System.currentTimeMillis()}.pdf"
                    val bytes = tempOutputFile!!.readBytes()
                    val savedUri = FileOutputManager.saveToDefault(
                        context = context,
                        bytes = bytes,
                        filename = outName,
                        mimeType = "application/pdf",
                        subfolder = "PDF"
                    ) ?: throw Exception("Failed to save encrypted PDF to OmniSuite/PDF folder.")

                    // Register to Room RecentFiles cache
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
                }

                successMessage = "Password Lock applied successfully!"
                lockInputUri = null
                lockPassword = ""
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Error applying encryption: ${e.localizedMessage}"
            } finally {
                withContext(Dispatchers.IO) {
                    tempInputFile?.let { if (it.exists()) it.delete() }
                    tempOutputFile?.let { if (it.exists()) it.delete() }
                }
                isProcessing = false
            }
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

    /**
     * Parses ranges like "1-3, 5, 8-10" into list of (start, end) pairs
     */
    private fun parseRanges(rangeStr: String): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        try {
            val parts = rangeStr.split(",")
            parts.forEach { part ->
                val clean = part.trim()
                if (clean.contains("-")) {
                    val split = clean.split("-")
                    if (split.size == 2) {
                        val start = split[0].trim().toInt()
                        val end = split[1].trim().toInt()
                        result.add(Pair(start, end))
                    }
                } else if (clean.isNotEmpty()) {
                    val page = clean.toInt()
                    result.add(Pair(page, page))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
        return result
    }

    /**
     * Converts Word DOCX/DOC Uri to PDF
     */
    fun convertDocToPdf() {
        val inputUri = docInputUri
        if (inputUri == null) {
            errorMessage = "Please select a Word document first."
            return
        }
        isProcessing = true
        resetStatus()
        successUri = null
        successName = null
        lastOutputBytes = null

        viewModelScope.launch {
            var tempInputFile: File? = null
            var tempOutputFile: File? = null

            try {
                withContext(Dispatchers.IO) {
                    tempInputFile = UriCacheUtils.cacheUriToFile(context, inputUri)
                        ?: throw Exception("Could not open Word document.")

                    tempOutputFile = File(context.cacheDir, "docx_converted_${System.currentTimeMillis()}.pdf")

                    // Call the engine
                    com.karnadigital.omnisuite.core.engine.document.OfficeConverter.convertDocxToPdf(
                        context,
                        tempInputFile!!,
                        tempOutputFile!!
                    )

                    val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".docx").removeSuffix(".doc")
                    val outName = "${originalName}_converted.pdf"
                    val bytes = tempOutputFile!!.readBytes()

                    val savedUri = FileOutputManager.saveToDefault(
                        context = context,
                        bytes = bytes,
                        filename = outName,
                        mimeType = "application/pdf",
                        subfolder = "PDF"
                    ) ?: throw Exception("Failed to save PDF to OmniSuite folder.")

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
                }

                successMessage = "Word document converted to PDF successfully!"
                docInputUri = null
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Error during Word to PDF conversion: ${e.localizedMessage}"
            } finally {
                withContext(Dispatchers.IO) {
                    tempInputFile?.let { if (it.exists()) it.delete() }
                    tempOutputFile?.let { if (it.exists()) it.delete() }
                }
                isProcessing = false
            }
        }
    }

    /**
     * Converts PPTX Uri to PDF
     */
    fun convertPptToPdf() {
        val inputUri = pptInputUri
        if (inputUri == null) {
            errorMessage = "Please select a PowerPoint document first."
            return
        }
        isProcessing = true
        resetStatus()
        successUri = null
        successName = null
        lastOutputBytes = null

        viewModelScope.launch {
            var tempInputFile: File? = null
            var tempOutputFile: File? = null

            try {
                withContext(Dispatchers.IO) {
                    tempInputFile = UriCacheUtils.cacheUriToFile(context, inputUri)
                        ?: throw Exception("Could not open PowerPoint document.")

                    tempOutputFile = File(context.cacheDir, "pptx_converted_${System.currentTimeMillis()}.pdf")

                    // Call the engine
                    com.karnadigital.omnisuite.core.engine.document.OfficeConverter.convertPptxToPdf(
                        context,
                        tempInputFile!!,
                        tempOutputFile!!,
                        pptRenderMode
                    )

                    val originalName = (getFileNameFromUri(inputUri) ?: "presentation").removeSuffix(".pptx").removeSuffix(".ppt")
                    val outName = "${originalName}_converted.pdf"
                    val bytes = tempOutputFile!!.readBytes()

                    val savedUri = FileOutputManager.saveToDefault(
                        context = context,
                        bytes = bytes,
                        filename = outName,
                        mimeType = "application/pdf",
                        subfolder = "PDF"
                    ) ?: throw Exception("Failed to save PDF to OmniSuite folder.")

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
                }

                successMessage = "PowerPoint converted to PDF successfully!"
                pptInputUri = null
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Error during PowerPoint to PDF conversion: ${e.localizedMessage}"
            } finally {
                withContext(Dispatchers.IO) {
                    tempInputFile?.let { if (it.exists()) it.delete() }
                    tempOutputFile?.let { if (it.exists()) it.delete() }
                }
                isProcessing = false
            }
        }
    }

    /**
     * Saves a scanned PDF file generated by the camera scanner
     */
    fun saveScannedPdf(scannedFile: File) {
        isProcessing = true
        resetStatus()
        successUri = null
        successName = null
        lastOutputBytes = null

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val bytes = scannedFile.readBytes()
                    val outName = "Scan_${System.currentTimeMillis()}.pdf"

                    val savedUri = FileOutputManager.saveToDefault(
                        context = context,
                        bytes = bytes,
                        filename = outName,
                        mimeType = "application/pdf",
                        subfolder = "PDF"
                    ) ?: throw Exception("Failed to save scanned document.")

                    val recent = RecentFile(
                        fileUri = savedUri.toString(),
                        fileName = outName,
                        mimeType = "application/pdf",
                        fileSize = scannedFile.length(),
                        lastOpened = System.currentTimeMillis()
                    )
                    recentFileRepository.insertRecentFile(recent)

                    successUri = savedUri
                    successName = outName
                    lastOutputBytes = bytes
                }

                successMessage = "Scanned document saved and indexed successfully!"
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Failed to save scan: ${e.localizedMessage}"
            } finally {
                isProcessing = false
            }
        }
    }

    /**
     * Extracts PDF pages to individual PNG images
     */
    fun convertPdfToImages() {
        val inputUri = pdfToImagesInputUri
        if (inputUri == null) {
            errorMessage = "Please select a PDF document first."
            return
        }
        isProcessing = true
        resetStatus()
        successUri = null
        successName = null
        lastOutputBytes = null

        viewModelScope.launch {
            var tempInputFile: File? = null

            try {
                withContext(Dispatchers.IO) {
                    tempInputFile = UriCacheUtils.cacheUriToFile(context, inputUri)
                        ?: throw Exception("Could not open source PDF document.")

                    val parcelFileDescriptor = android.os.ParcelFileDescriptor.open(
                        tempInputFile!!,
                        android.os.ParcelFileDescriptor.MODE_READ_ONLY
                    )
                    val pdfRenderer = android.graphics.pdf.PdfRenderer(parcelFileDescriptor)
                    val pageCount = pdfRenderer.pageCount

                    if (pageCount == 0) {
                        pdfRenderer.close()
                        parcelFileDescriptor.close()
                        throw Exception("PDF has no pages to extract.")
                    }

                    val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".pdf")
                    var sampleSavedUri: Uri? = null
                    var sampleBytes: ByteArray? = null
                    var sampleName: String? = null

                    for (i in 0 until pageCount) {
                        val page = pdfRenderer.openPage(i)
                        // Create high-res bitmap
                        val bitmap = android.graphics.Bitmap.createBitmap(
                            page.width * 2,
                            page.height * 2,
                            android.graphics.Bitmap.Config.ARGB_8888
                        )
                        page.render(
                            bitmap,
                            null,
                            null,
                            android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )
                        page.close()

                        // Compress to PNG
                        val stream = java.io.ByteArrayOutputStream()
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                        val bytes = stream.toByteArray()
                        bitmap.recycle()

                        val imageName = "${originalName}_page_${i + 1}.png"

                        val savedUri = FileOutputManager.saveToDefault(
                            context = context,
                            bytes = bytes,
                            filename = imageName,
                            mimeType = "image/png",
                            subfolder = "Images"
                        ) ?: throw Exception("Failed to save page ${i + 1} image.")

                        // Register to recent files
                        val recent = RecentFile(
                            fileUri = savedUri.toString(),
                            fileName = imageName,
                            mimeType = "image/png",
                            fileSize = bytes.size.toLong(),
                            lastOpened = System.currentTimeMillis()
                        )
                        recentFileRepository.insertRecentFile(recent)

                        if (i == 0) {
                            sampleSavedUri = savedUri
                            sampleBytes = bytes
                            sampleName = imageName
                        }
                    }

                    pdfRenderer.close()
                    parcelFileDescriptor.close()

                    successUri = sampleSavedUri
                    lastOutputBytes = sampleBytes
                    successName = sampleName
                }

                successMessage = "PDF pages successfully converted to images under Documents/OmniSuite/Images/!"
                pdfToImagesInputUri = null
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Error during PDF to Image extraction: ${e.localizedMessage}"
            } finally {
                withContext(Dispatchers.IO) {
                    tempInputFile?.let { if (it.exists()) it.delete() }
                }
                isProcessing = false
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx != -1) name = cursor.getString(idx)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (name == null) {
            name = uri.path
            val lastSlash = name?.lastIndexOf('/') ?: -1
            if (lastSlash != -1) name = name?.substring(lastSlash + 1)
        }
        return name
    }
}
