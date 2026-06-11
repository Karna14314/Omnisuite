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
import com.karnadigital.omnisuite.core.engine.document.ReverseOfficeConverter
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.cos.COSName
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import android.os.ParcelFileDescriptor
import android.os.CancellationSignal
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import javax.inject.Inject

@HiltViewModel
class PdfToolsViewModel @Inject constructor(
    private val recentFileRepository: RecentFileRepository,
    @ApplicationContext private val context: Context,
    private val reverseOfficeConverter: ReverseOfficeConverter
) : ViewModel() {

    init {
        // Initialize PDFBox resource loader once for dynamic assets mapping
        PDFBoxResourceLoader.init(context)
    }

    // State Variables
    var selectedMergeUris by mutableStateOf<List<Uri>>(emptyList())
        private set

    var pdfToWordInputUri by mutableStateOf<Uri?>(null)
    var pdfToPptInputUri by mutableStateOf<Uri?>(null)
    var pdfToExcelInputUri by mutableStateOf<Uri?>(null)
    var pdfFormInputUri by mutableStateOf<Uri?>(null)

    var splitInputUri by mutableStateOf<Uri?>(null)
    var splitRanges by mutableStateOf("1-3, 4-6")

    var lockInputUri by mutableStateOf<Uri?>(null)
    var lockPassword by mutableStateOf("")

    var pptInputUri by mutableStateOf<Uri?>(null)
    var pptRenderMode by mutableStateOf("image") // "image" or "text"

    var docInputUri by mutableStateOf<Uri?>(null)

    var scanInputUri by mutableStateOf<Uri?>(null)

    var pdfToImagesInputUri by mutableStateOf<Uri?>(null)

    var compressInputUri by mutableStateOf<Uri?>(null)
    var compressQuality by mutableStateOf(0.5f)
    var flattenInputUri by mutableStateOf<Uri?>(null)
    var xlsInputUri by mutableStateOf<Uri?>(null)

    var isProcessing by mutableStateOf(false)
        private set
    var successMessage by mutableStateOf<String?>(null)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    var successUri by mutableStateOf<Uri?>(null)
        private set
    var successUris by mutableStateOf<List<Uri>>(emptyList())
        private set
    var successName by mutableStateOf<String?>(null)
        private set
    var lastOutputBytes by mutableStateOf<ByteArray?>(null)
        private set

    fun registerRecentFile(recent: RecentFile) {
        viewModelScope.launch(Dispatchers.IO) {
            recentFileRepository.insertRecentFile(recent)
        }
    }

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
        successUri = null
        successName = null
        successUris = emptyList()
        lastOutputBytes = null
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
                    val savedUris = mutableListOf<Uri>()
                    var sampleBytes: ByteArray? = null
                    var sampleName: String? = null
                    var totalSize = 0L

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

                        savedUris.add(savedUri)
                        totalSize += bytes.size.toLong()

                        if (i == 0) {
                            sampleBytes = bytes
                            sampleName = imageName
                        }
                    }

                    // Register to recent files as a single batch history entry
                    val batchUriString = savedUris.joinToString("|") { it.toString() }
                    val recent = RecentFile(
                        fileUri = batchUriString,
                        fileName = "${originalName} (All Pages)",
                        mimeType = "image/png",
                        fileSize = totalSize,
                        lastOpened = System.currentTimeMillis()
                    )
                    recentFileRepository.insertRecentFile(recent)

                    pdfRenderer.close()
                    parcelFileDescriptor.close()

                    successUri = savedUris.firstOrNull()
                    successUris = savedUris
                    lastOutputBytes = sampleBytes
                    successName = "${originalName} (All Pages)"
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

    fun convertPdfToDocx() {
        val uri = pdfToWordInputUri ?: return
        isProcessing = true
        resetStatus()
        successUri = null
        successName = null
        lastOutputBytes = null

        viewModelScope.launch {
            try {
                val outputUri = reverseOfficeConverter.convertPdfToDocx(uri)
                if (outputUri != null) {
                    successUri = outputUri
                    val originalName = (getFileNameFromUri(uri) ?: "document").removeSuffix(".pdf")
                    successName = "${originalName}_converted.docx"
                    
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(outputUri)?.use { stream ->
                            lastOutputBytes = stream.readBytes()
                        }
                    }
                    successMessage = "PDF converted to DOCX successfully!"
                    pdfToWordInputUri = null
                } else {
                    errorMessage = "Failed to convert PDF to DOCX."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Error: ${e.localizedMessage}"
            } finally {
                isProcessing = false
            }
        }
    }

    fun convertPdfToPptx() {
        val uri = pdfToPptInputUri ?: return
        isProcessing = true
        resetStatus()
        successUri = null
        successName = null
        lastOutputBytes = null

        viewModelScope.launch {
            try {
                val outputUri = reverseOfficeConverter.convertPdfToPptx(uri)
                if (outputUri != null) {
                    successUri = outputUri
                    val originalName = (getFileNameFromUri(uri) ?: "document").removeSuffix(".pdf")
                    successName = "${originalName}_converted.pptx"
                    
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(outputUri)?.use { stream ->
                            lastOutputBytes = stream.readBytes()
                        }
                    }
                    successMessage = "PDF converted to PPTX successfully!"
                    pdfToPptInputUri = null
                } else {
                    errorMessage = "Failed to convert PDF to PPTX."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Error: ${e.localizedMessage}"
            } finally {
                isProcessing = false
            }
        }
    }

    fun convertPdfToXlsx() {
        val uri = pdfToExcelInputUri ?: return
        isProcessing = true
        resetStatus()
        successUri = null
        successName = null
        lastOutputBytes = null

        viewModelScope.launch {
            try {
                val outputUri = reverseOfficeConverter.convertPdfToXlsx(uri)
                if (outputUri != null) {
                    successUri = outputUri
                    val originalName = (getFileNameFromUri(uri) ?: "document").removeSuffix(".pdf")
                    successName = "${originalName}_converted.xlsx"
                    
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(outputUri)?.use { stream ->
                            lastOutputBytes = stream.readBytes()
                        }
                    }
                    successMessage = "PDF converted to XLSX successfully!"
                    pdfToExcelInputUri = null
                } else {
                    errorMessage = "Failed to convert PDF to XLSX."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Error: ${e.localizedMessage}"
            } finally {
                isProcessing = false
            }
        }
    }

    fun fillPdfForm(formData: Map<String, String>) {
        val uri = pdfFormInputUri ?: return
        isProcessing = true
        resetStatus()
        successUri = null
        successName = null
        lastOutputBytes = null

        viewModelScope.launch {
            try {
                val outputUri = reverseOfficeConverter.fillInteractiveForm(uri, formData)
                if (outputUri != null) {
                    successUri = outputUri
                    val originalName = (getFileNameFromUri(uri) ?: "document").removeSuffix(".pdf")
                    successName = "${originalName}_filled.pdf"
                    
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(outputUri)?.use { stream ->
                            lastOutputBytes = stream.readBytes()
                        }
                    }
                    successMessage = "Interactive PDF Form filled successfully!"
                    pdfFormInputUri = null
                } else {
                    errorMessage = "Failed to fill interactive PDF form."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Error: ${e.localizedMessage}"
            } finally {
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

    fun compressPdf() {
        val inputUri = compressInputUri
        if (inputUri == null) {
            errorMessage = "Please select a source PDF document first."
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
                        ?: throw Exception("Could not open source PDF file.")

                    tempOutputFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.pdf")

                    PDDocument.load(tempInputFile).use { document ->
                        for (page in document.pages) {
                            val resources = page.resources ?: continue
                            for (name in resources.xObjectNames) {
                                if (resources.isImageXObject(name)) {
                                    val xObject = resources.getXObject(name)
                                    if (xObject is com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject) {
                                        val bitmap = xObject.image ?: continue
                                        val stream = java.io.ByteArrayOutputStream()
                                        val qualityPercent = (compressQuality * 100).toInt().coerceIn(10, 100)
                                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, qualityPercent, stream)
                                        val compressedBytes = stream.toByteArray()
                                        
                                        val compressedImage = com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory.createFromStream(document, java.io.ByteArrayInputStream(compressedBytes))
                                        resources.put(name, compressedImage)
                                        bitmap.recycle()
                                    }
                                }
                            }
                        }

                        FileOutputStream(tempOutputFile).use { outStream ->
                            document.save(outStream)
                        }
                    }

                    val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".pdf")
                    val outName = "${originalName}_compressed.pdf"
                    val bytes = tempOutputFile!!.readBytes()

                    val savedUri = FileOutputManager.saveToDefault(
                        context = context,
                        bytes = bytes,
                        filename = outName,
                        mimeType = "application/pdf",
                        subfolder = "PDF"
                    ) ?: throw Exception("Failed to save compressed PDF.")

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

                successMessage = "PDF compressed successfully!"
                compressInputUri = null
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Error compressing PDF: ${e.localizedMessage}"
            } finally {
                withContext(Dispatchers.IO) {
                    tempInputFile?.let { if (it.exists()) it.delete() }
                    tempOutputFile?.let { if (it.exists()) it.delete() }
                }
                isProcessing = false
            }
        }
    }

    fun flattenPdf() {
        val inputUri = flattenInputUri
        if (inputUri == null) {
            errorMessage = "Please select a source PDF document first."
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
                        ?: throw Exception("Could not open source PDF file.")

                    tempOutputFile = File(context.cacheDir, "flattened_${System.currentTimeMillis()}.pdf")

                    PDDocument.load(tempInputFile).use { document ->
                        val acroForm = document.documentCatalog.acroForm
                        if (acroForm != null) {
                            acroForm.flatten()
                        } else {
                            throw Exception("This PDF does not contain any interactive form fields to flatten.")
                        }

                        FileOutputStream(tempOutputFile).use { outStream ->
                            document.save(outStream)
                        }
                    }

                    val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".pdf")
                    val outName = "${originalName}_flattened.pdf"
                    val bytes = tempOutputFile!!.readBytes()

                    val savedUri = FileOutputManager.saveToDefault(
                        context = context,
                        bytes = bytes,
                        filename = outName,
                        mimeType = "application/pdf",
                        subfolder = "PDF"
                    ) ?: throw Exception("Failed to save flattened PDF.")

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

                successMessage = "PDF form fields flattened successfully!"
                flattenInputUri = null
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Error flattening PDF: ${e.localizedMessage}"
            } finally {
                withContext(Dispatchers.IO) {
                    tempInputFile?.let { if (it.exists()) it.delete() }
                    tempOutputFile?.let { if (it.exists()) it.delete() }
                }
                isProcessing = false
            }
        }
    }

    fun convertXlsToPdf() {
        val inputUri = xlsInputUri
        if (inputUri == null) {
            errorMessage = "Please select an Excel sheet first."
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
                        ?: throw Exception("Could not open Excel workbook.")

                    tempOutputFile = File(context.cacheDir, "xlsx_converted_${System.currentTimeMillis()}.pdf")

                    com.karnadigital.omnisuite.core.engine.document.OfficeConverter.convertXlsxToPdf(
                        context,
                        tempInputFile!!,
                        tempOutputFile!!
                    )

                    val originalName = (getFileNameFromUri(inputUri) ?: "spreadsheet").removeSuffix(".xlsx").removeSuffix(".xls")
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

                successMessage = "Excel sheet converted to PDF successfully!"
                xlsInputUri = null
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Error during Excel to PDF conversion: ${e.localizedMessage}"
            } finally {
                withContext(Dispatchers.IO) {
                    tempInputFile?.let { if (it.exists()) it.delete() }
                    tempOutputFile?.let { if (it.exists()) it.delete() }
                }
                isProcessing = false
            }
        }
    }

    // --- NEW STATE VARIABLES ---
    var decryptInputUri by mutableStateOf<Uri?>(null)
    var decryptPassword by mutableStateOf("")

    var rotateInputUri by mutableStateOf<Uri?>(null)
    var extractInputUri by mutableStateOf<Uri?>(null)
    var deleteInputUri by mutableStateOf<Uri?>(null)

    var docxToTxtInputUri by mutableStateOf<Uri?>(null)
    var csvToXlsxInputUri by mutableStateOf<Uri?>(null)
    var xlsxToCsvInputUri by mutableStateOf<Uri?>(null)
    var pptxToTxtInputUri by mutableStateOf<Uri?>(null)

    var tarInputUris by mutableStateOf<List<Uri>>(emptyList())
    var tarExtractUri by mutableStateOf<Uri?>(null)

    // --- NEW CORE PROCESSING FUNCTIONS ---

    fun decryptPdf() {
        val inputUri = decryptInputUri
        if (inputUri == null) {
            errorMessage = "Please select a locked PDF document."
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
                        ?: throw Exception("Could not open source PDF file.")
                    tempOutputFile = File(context.cacheDir, "decrypted_${System.currentTimeMillis()}.pdf")

                    PDDocument.load(tempInputFile, decryptPassword).use { document ->
                        if (document.isEncrypted) {
                            document.setAllSecurityToBeRemoved(true)
                        }
                        FileOutputStream(tempOutputFile).use { outStream ->
                            document.save(outStream)
                        }
                    }

                    val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".pdf")
                    val outName = "${originalName}_unlocked.pdf"
                    val bytes = tempOutputFile!!.readBytes()

                    val savedUri = FileOutputManager.saveToDefault(
                        context = context,
                        bytes = bytes,
                        filename = outName,
                        mimeType = "application/pdf",
                        subfolder = "PDF"
                    ) ?: throw Exception("Failed to save decrypted PDF.")

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
                successMessage = "Password security removed successfully!"
                decryptInputUri = null
                decryptPassword = ""
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Failed to decrypt: ${e.localizedMessage}"
            } finally {
                withContext(Dispatchers.IO) {
                    tempInputFile?.let { if (it.exists()) it.delete() }
                    tempOutputFile?.let { if (it.exists()) it.delete() }
                }
                isProcessing = false
            }
        }
    }

    fun rotatePdfPages(rotations: Map<Int, Int>) {
        val inputUri = rotateInputUri
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
            var tempOutputFile: File? = null
            try {
                withContext(Dispatchers.IO) {
                    tempInputFile = UriCacheUtils.cacheUriToFile(context, inputUri)
                        ?: throw Exception("Could not open source PDF file.")
                    tempOutputFile = File(context.cacheDir, "rotated_${System.currentTimeMillis()}.pdf")

                    PDDocument.load(tempInputFile).use { document ->
                        val pages = document.pages
                        rotations.forEach { (pageIdx, angle) ->
                            if (pageIdx in 0 until pages.count) {
                                val page = pages.get(pageIdx)
                                val currentRotation = page.rotation
                                val newRotation = (currentRotation + angle) % 360
                                page.rotation = newRotation
                            }
                        }
                        FileOutputStream(tempOutputFile).use { outStream ->
                            document.save(outStream)
                        }
                    }

                    val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".pdf")
                    val outName = "${originalName}_rotated.pdf"
                    val bytes = tempOutputFile!!.readBytes()

                    val savedUri = FileOutputManager.saveToDefault(
                        context = context,
                        bytes = bytes,
                        filename = outName,
                        mimeType = "application/pdf",
                        subfolder = "PDF"
                    ) ?: throw Exception("Failed to save rotated PDF.")

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
                successMessage = "Selected PDF pages rotated successfully!"
                rotateInputUri = null
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Failed to rotate pages: ${e.localizedMessage}"
            } finally {
                withContext(Dispatchers.IO) {
                    tempInputFile?.let { if (it.exists()) it.delete() }
                    tempOutputFile?.let { if (it.exists()) it.delete() }
                }
                isProcessing = false
            }
        }
    }

    fun extractPdfPages(selectedPages: Set<Int>) {
        val inputUri = extractInputUri
        if (inputUri == null) {
            errorMessage = "Please select a PDF document first."
            return
        }
        if (selectedPages.isEmpty()) {
            errorMessage = "Please select at least one page to extract."
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
                        ?: throw Exception("Could not open source PDF file.")
                    tempOutputFile = File(context.cacheDir, "extracted_${System.currentTimeMillis()}.pdf")

                    PDDocument.load(tempInputFile).use { document ->
                        PDDocument().use { outputDoc ->
                            val pages = document.pages
                            selectedPages.sorted().forEach { pageIdx ->
                                if (pageIdx in 0 until pages.count) {
                                    outputDoc.addPage(pages.get(pageIdx))
                                }
                            }
                            FileOutputStream(tempOutputFile).use { outStream ->
                                outputDoc.save(outStream)
                            }
                        }
                    }

                    val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".pdf")
                    val outName = "${originalName}_extracted.pdf"
                    val bytes = tempOutputFile!!.readBytes()

                    val savedUri = FileOutputManager.saveToDefault(
                        context = context,
                        bytes = bytes,
                        filename = outName,
                        mimeType = "application/pdf",
                        subfolder = "PDF"
                    ) ?: throw Exception("Failed to save extracted PDF.")

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
                successMessage = "Selected pages extracted successfully!"
                extractInputUri = null
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Failed to extract pages: ${e.localizedMessage}"
            } finally {
                withContext(Dispatchers.IO) {
                    tempInputFile?.let { if (it.exists()) it.delete() }
                    tempOutputFile?.let { if (it.exists()) it.delete() }
                }
                isProcessing = false
            }
        }
    }

    fun deletePdfPages(selectedPages: Set<Int>) {
        val inputUri = deleteInputUri
        if (inputUri == null) {
            errorMessage = "Please select a PDF document first."
            return
        }
        if (selectedPages.isEmpty()) {
            errorMessage = "Please select at least one page to delete."
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
                        ?: throw Exception("Could not open source PDF file.")
                    tempOutputFile = File(context.cacheDir, "deleted_pages_${System.currentTimeMillis()}.pdf")

                    PDDocument.load(tempInputFile).use { document ->
                        val pages = document.pages
                        if (selectedPages.size >= pages.count) {
                            throw Exception("Cannot delete all pages in the document. At least one page must remain.")
                        }
                        // Delete in reverse order to keep indices correct
                        selectedPages.sortedDescending().forEach { pageIdx ->
                            if (pageIdx in 0 until pages.count) {
                                document.removePage(pageIdx)
                            }
                        }
                        FileOutputStream(tempOutputFile).use { outStream ->
                            document.save(outStream)
                        }
                    }

                    val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".pdf")
                    val outName = "${originalName}_modified.pdf"
                    val bytes = tempOutputFile!!.readBytes()

                    val savedUri = FileOutputManager.saveToDefault(
                        context = context,
                        bytes = bytes,
                        filename = outName,
                        mimeType = "application/pdf",
                        subfolder = "PDF"
                    ) ?: throw Exception("Failed to save modified PDF.")

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
                successMessage = "Selected pages deleted successfully!"
                deleteInputUri = null
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Failed to delete pages: ${e.localizedMessage}"
            } finally {
                withContext(Dispatchers.IO) {
                    tempInputFile?.let { if (it.exists()) it.delete() }
                    tempOutputFile?.let { if (it.exists()) it.delete() }
                }
                isProcessing = false
            }
        }
    }

    fun convertDocxToTxt() {
        val inputUri = docxToTxtInputUri
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
            try {
                withContext(Dispatchers.IO) {
                    tempInputFile = UriCacheUtils.cacheUriToFile(context, inputUri)
                        ?: throw Exception("Could not open source file.")

                    val textBuilder = StringBuilder()
                    java.io.FileInputStream(tempInputFile!!).use { fis ->
                        org.apache.poi.xwpf.usermodel.XWPFDocument(fis).use { document ->
                            for (para in document.paragraphs) {
                                textBuilder.append(para.text).append("\n")
                            }
                            for (table in document.tables) {
                                for (row in table.rows) {
                                    val rowText = row.tableCells.joinToString("\t") { it.text }
                                    textBuilder.append(rowText).append("\n")
                                }
                            }
                        }
                    }

                    val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".docx").removeSuffix(".doc")
                    val outName = "${originalName}_text.txt"
                    val bytes = textBuilder.toString().toByteArray(Charsets.UTF_8)

                    val savedUri = FileOutputManager.saveToDefault(
                        context = context,
                        bytes = bytes,
                        filename = outName,
                        mimeType = "text/plain",
                        subfolder = "Documents"
                    ) ?: throw Exception("Failed to save converted TXT.")

                    val recent = RecentFile(
                        fileUri = savedUri.toString(),
                        fileName = outName,
                        mimeType = "text/plain",
                        fileSize = bytes.size.toLong(),
                        lastOpened = System.currentTimeMillis()
                    )
                    recentFileRepository.insertRecentFile(recent)

                    successUri = savedUri
                    successName = outName
                    lastOutputBytes = bytes
                }
                successMessage = "Word document converted to TXT successfully!"
                docxToTxtInputUri = null
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Failed to convert to TXT: ${e.localizedMessage}"
            } finally {
                withContext(Dispatchers.IO) {
                    tempInputFile?.let { if (it.exists()) it.delete() }
                }
                isProcessing = false
            }
        }
    }

    fun convertCsvToXlsx() {
        val inputUri = csvToXlsxInputUri
        if (inputUri == null) {
            errorMessage = "Please select a CSV file first."
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
                        ?: throw Exception("Could not open source file.")
                    tempOutputFile = File(context.cacheDir, "converted_${System.currentTimeMillis()}.xlsx")

                    org.apache.poi.xssf.usermodel.XSSFWorkbook().use { workbook ->
                        val sheet = workbook.createSheet("CSV Data")
                        val lines = tempInputFile!!.readLines(Charsets.UTF_8)
                        
                        lines.forEachIndexed { r, line ->
                            val row = sheet.createRow(r)
                            val cells = parseCsvLine(line)
                            cells.forEachIndexed { c, cellVal ->
                                val cell = row.createCell(c)
                                val doubleVal = cellVal.toDoubleOrNull()
                                if (doubleVal != null) {
                                    cell.setCellValue(doubleVal)
                                } else {
                                    cell.setCellValue(cellVal)
                                }
                            }
                        }
                        
                        FileOutputStream(tempOutputFile!!).use { fos ->
                            workbook.write(fos)
                        }
                    }

                    val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".csv")
                    val outName = "${originalName}_excel.xlsx"
                    val bytes = tempOutputFile!!.readBytes()

                    val savedUri = FileOutputManager.saveToDefault(
                        context = context,
                        bytes = bytes,
                        filename = outName,
                        mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        subfolder = "Documents"
                    ) ?: throw Exception("Failed to save Excel file.")

                    val recent = RecentFile(
                        fileUri = savedUri.toString(),
                        fileName = outName,
                        mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        fileSize = tempOutputFile!!.length(),
                        lastOpened = System.currentTimeMillis()
                    )
                    recentFileRepository.insertRecentFile(recent)

                    successUri = savedUri
                    successName = outName
                    lastOutputBytes = bytes
                }
                successMessage = "CSV file converted to Excel successfully!"
                csvToXlsxInputUri = null
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Failed to convert CSV: ${e.localizedMessage}"
            } finally {
                withContext(Dispatchers.IO) {
                    tempInputFile?.let { if (it.exists()) it.delete() }
                    tempOutputFile?.let { if (it.exists()) it.delete() }
                }
                isProcessing = false
            }
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var curVal = java.lang.StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (inQuotes) {
                if (ch == '\"') {
                    if (i + 1 < line.length && line[i + 1] == '\"') {
                        curVal.append('\"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    curVal.append(ch)
                }
            } else {
                if (ch == '\"') {
                    inQuotes = true
                } else if (ch == ',') {
                    result.add(curVal.toString())
                    curVal = java.lang.StringBuilder()
                } else {
                    curVal.append(ch)
                }
            }
            i++
        }
        result.add(curVal.toString())
        return result
    }

    fun convertXlsxToCsv() {
        val inputUri = xlsxToCsvInputUri
        if (inputUri == null) {
            errorMessage = "Please select an Excel document first."
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
                        ?: throw Exception("Could not open source file.")

                    val csvBuilder = StringBuilder()
                    java.io.FileInputStream(tempInputFile!!).use { fis ->
                        org.apache.poi.xssf.usermodel.XSSFWorkbook(fis).use { workbook ->
                            val sheet = workbook.getSheetAt(0)
                            val dataFormatter = org.apache.poi.ss.usermodel.DataFormatter()
                            for (r in 0..sheet.lastRowNum) {
                                val row = sheet.getRow(r)
                                if (row == null) {
                                    csvBuilder.append("\n")
                                    continue
                                }
                                val cellList = mutableListOf<String>()
                                for (c in 0 until row.lastCellNum) {
                                    val cell = row.getCell(c)
                                    val text = dataFormatter.formatCellValue(cell)
                                    val escaped = if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
                                        "\"" + text.replace("\"", "\"\"") + "\""
                                    } else {
                                        text
                                    }
                                    cellList.add(escaped)
                                }
                                csvBuilder.append(cellList.joinToString(",")).append("\n")
                            }
                        }
                    }

                    val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".xlsx").removeSuffix(".xls")
                    val outName = "${originalName}_csv.csv"
                    val bytes = csvBuilder.toString().toByteArray(Charsets.UTF_8)

                    val savedUri = FileOutputManager.saveToDefault(
                        context = context,
                        bytes = bytes,
                        filename = outName,
                        mimeType = "text/csv",
                        subfolder = "Documents"
                    ) ?: throw Exception("Failed to save CSV.")

                    val recent = RecentFile(
                        fileUri = savedUri.toString(),
                        fileName = outName,
                        mimeType = "text/csv",
                        fileSize = bytes.size.toLong(),
                        lastOpened = System.currentTimeMillis()
                    )
                    recentFileRepository.insertRecentFile(recent)

                    successUri = savedUri
                    successName = outName
                    lastOutputBytes = bytes
                }
                successMessage = "Excel workbook converted to CSV successfully!"
                xlsxToCsvInputUri = null
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Failed to convert: ${e.localizedMessage}"
            } finally {
                withContext(Dispatchers.IO) {
                    tempInputFile?.let { if (it.exists()) it.delete() }
                }
                isProcessing = false
            }
        }
    }

    fun convertPptxToTxt() {
        val inputUri = pptxToTxtInputUri
        if (inputUri == null) {
            errorMessage = "Please select a PowerPoint presentation first."
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
                        ?: throw Exception("Could not open source file.")

                    val textBuilder = StringBuilder()
                    java.io.FileInputStream(tempInputFile!!).use { fis ->
                        org.apache.poi.xslf.usermodel.XMLSlideShow(fis).use { ppt ->
                            ppt.slides.forEachIndexed { index, slide ->
                                textBuilder.append("--- Slide ").append(index + 1).append(" ---\n")
                                slide.shapes.forEach { shape ->
                                    if (shape is org.apache.poi.xslf.usermodel.XSLFTextShape) {
                                        textBuilder.append(shape.text).append("\n")
                                    }
                                }
                                textBuilder.append("\n")
                            }
                        }
                    }

                    val originalName = (getFileNameFromUri(inputUri) ?: "presentation").removeSuffix(".pptx").removeSuffix(".ppt")
                    val outName = "${originalName}_slides.txt"
                    val bytes = textBuilder.toString().toByteArray(Charsets.UTF_8)

                    val savedUri = FileOutputManager.saveToDefault(
                        context = context,
                        bytes = bytes,
                        filename = outName,
                        mimeType = "text/plain",
                        subfolder = "Documents"
                    ) ?: throw Exception("Failed to save TXT.")

                    val recent = RecentFile(
                        fileUri = savedUri.toString(),
                        fileName = outName,
                        mimeType = "text/plain",
                        fileSize = bytes.size.toLong(),
                        lastOpened = System.currentTimeMillis()
                    )
                    recentFileRepository.insertRecentFile(recent)

                    successUri = savedUri
                    successName = outName
                    lastOutputBytes = bytes
                }
                successMessage = "PowerPoint slides converted to TXT successfully!"
                pptxToTxtInputUri = null
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Failed to convert PPTX: ${e.localizedMessage}"
            } finally {
                withContext(Dispatchers.IO) {
                    tempInputFile?.let { if (it.exists()) it.delete() }
                }
                isProcessing = false
            }
        }
    }

    fun convertMarkdownToPdf(markdownText: String, filename: String) {
        if (markdownText.isBlank()) {
            errorMessage = "Markdown text is empty."
            return
        }
        isProcessing = true
        resetStatus()
        successUri = null
        successName = null
        lastOutputBytes = null

        viewModelScope.launch {
            var tempOutputFile: File? = null
            try {
                withContext(Dispatchers.IO) {
                    tempOutputFile = File(context.cacheDir, "md_converted_${System.currentTimeMillis()}.pdf")
                    
                    PDDocument().use { doc ->
                        var page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
                        doc.addPage(page)
                        
                        var contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page)
                        val fontNormal = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA
                        val fontBold = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD
                        
                        var yOffset = 750f
                        val margin = 50f
                        val width = page.mediaBox.width - (2 * margin)
                        
                        val lines = markdownText.split("\n")
                        lines.forEach { line ->
                            val cleanLine = line.trim()
                            if (cleanLine.isEmpty()) {
                                yOffset -= 15f
                                return@forEach
                            }
                            
                            val isHeader = cleanLine.startsWith("#")
                            var headerLevel = 0
                            if (isHeader) {
                                while (headerLevel < cleanLine.length && cleanLine[headerLevel] == '#') {
                                    headerLevel++
                                }
                            }
                            
                            val text = if (isHeader) cleanLine.substring(headerLevel).trim() else cleanLine
                            val currentFont = if (isHeader) fontBold else fontNormal
                            val fontSize = if (isHeader) {
                                when (headerLevel) {
                                    1 -> 24f
                                    2 -> 18f
                                    else -> 14f
                                }
                            } else 12f
                            
                            if (yOffset < 50f) {
                                contentStream.close()
                                page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
                                doc.addPage(page)
                                contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page)
                                yOffset = 750f
                            }
                            
                            contentStream.beginText()
                            contentStream.setFont(currentFont, fontSize)
                            contentStream.newLineAtOffset(margin, yOffset)
                            
                            val words = text.split(" ")
                            val wrappedLine = StringBuilder()
                            
                            words.forEach { word ->
                                val testLine = if (wrappedLine.isEmpty()) word else "${wrappedLine} $word"
                                val size = fontSize * currentFont.getStringWidth(testLine) / 1000f
                                if (size > width) {
                                    contentStream.showText(wrappedLine.toString())
                                    contentStream.endText()
                                    yOffset -= (fontSize + 4f)
                                    
                                    if (yOffset < 50f) {
                                        contentStream.close()
                                        page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
                                        doc.addPage(page)
                                        contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page)
                                        yOffset = 750f
                                    }
                                    
                                    contentStream.beginText()
                                    contentStream.setFont(currentFont, fontSize)
                                    contentStream.newLineAtOffset(margin, yOffset)
                                    wrappedLine.setLength(0)
                                    wrappedLine.append(word)
                                } else {
                                    wrappedLine.setLength(0)
                                    wrappedLine.append(testLine)
                                }
                            }
                            
                            if (wrappedLine.isNotEmpty()) {
                                contentStream.showText(wrappedLine.toString())
                            }
                            contentStream.endText()
                            yOffset -= (fontSize + 8f)
                        }
                        
                        contentStream.close()
                        FileOutputStream(tempOutputFile!!).use { fos ->
                            doc.save(fos)
                        }
                    }

                    val outName = if (filename.endsWith(".pdf", ignoreCase = true)) filename else "$filename.pdf"
                    val bytes = tempOutputFile!!.readBytes()

                    val savedUri = FileOutputManager.saveToDefault(
                        context = context,
                        bytes = bytes,
                        filename = outName,
                        mimeType = "application/pdf",
                        subfolder = "PDF"
                    ) ?: throw Exception("Failed to save Markdown PDF.")

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
                successMessage = "Markdown compiled to PDF successfully!"
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Failed to compile Markdown: ${e.localizedMessage}"
            } finally {
                withContext(Dispatchers.IO) {
                    tempOutputFile?.let { if (it.exists()) it.delete() }
                }
                isProcessing = false
            }
        }
    }

    fun packTarArchive(outputName: String) {
        if (tarInputUris.isEmpty()) {
            errorMessage = "Please select files to pack into TAR archive."
            return
        }
        isProcessing = true
        resetStatus()
        successUri = null
        successName = null
        lastOutputBytes = null

        viewModelScope.launch {
            var tempOutputFile: File? = null
            val cachedFiles = mutableListOf<Pair<File, String>>()
            try {
                withContext(Dispatchers.IO) {
                    tarInputUris.forEach { uri ->
                        val file = UriCacheUtils.cacheUriToFile(context, uri)
                        val name = getFileNameFromUri(uri) ?: "file_${System.currentTimeMillis()}"
                        if (file != null) {
                            cachedFiles.add(Pair(file, name))
                        }
                    }

                    tempOutputFile = File(context.cacheDir, "archive_${System.currentTimeMillis()}.tar")
                    FileOutputStream(tempOutputFile!!).use { fos ->
                        cachedFiles.forEach { (file, name) ->
                            val size = file.length()
                            val header = ByteArray(512)
                            
                            val nameBytes = name.take(99).toByteArray(Charsets.UTF_8)
                            System.arraycopy(nameBytes, 0, header, 0, nameBytes.size)
                            
                            val modeBytes = "0000644\u0000".toByteArray()
                            System.arraycopy(modeBytes, 0, header, 100, modeBytes.size)
                            
                            val sizeOctal = String.format("%011o", size) + " "
                            val sizeBytes = sizeOctal.toByteArray()
                            System.arraycopy(sizeBytes, 0, header, 124, sizeBytes.size)
                            
                            val modTimeOctal = String.format("%011o", file.lastModified() / 1000L) + " "
                            val modTimeBytes = modTimeOctal.toByteArray()
                            System.arraycopy(modTimeBytes, 0, header, 136, modTimeBytes.size)
                            
                            header[156] = '0'.toByte()
                            
                            val magicBytes = "ustar\u0000".toByteArray()
                            System.arraycopy(magicBytes, 0, header, 257, magicBytes.size)
                            
                            for (j in 148 until 156) {
                                header[j] = ' '.toByte()
                            }
                            var checksum = 0
                            for (b in header) {
                                checksum += (b.toInt() and 0xFF)
                            }
                            val checksumOctal = String.format("%06o", checksum) + "\u0000 "
                            val checksumBytes = checksumOctal.toByteArray()
                            System.arraycopy(checksumBytes, 0, header, 148, checksumBytes.size)
                            
                            fos.write(header)
                            
                            java.io.FileInputStream(file).use { fis ->
                                val buf = ByteArray(1024)
                                var read: Int
                                while (fis.read(buf).also { read = it } != -1) {
                                    fos.write(buf, 0, read)
                                }
                            }
                            
                            val remainder = (size % 512).toInt()
                            if (remainder > 0) {
                                val padding = ByteArray(512 - remainder)
                                fos.write(padding)
                            }
                        }
                        fos.write(ByteArray(1024))
                    }

                    val outName = if (outputName.endsWith(".tar", ignoreCase = true)) outputName else "$outputName.tar"
                    val bytes = tempOutputFile!!.readBytes()
                    val savedUri = FileOutputManager.saveToDefault(
                        context = context,
                        bytes = bytes,
                        filename = outName,
                        mimeType = "application/x-tar",
                        subfolder = "Archive"
                    ) ?: throw Exception("Failed to save TAR archive.")

                    val recent = RecentFile(
                        fileUri = savedUri.toString(),
                        fileName = outName,
                        mimeType = "application/x-tar",
                        fileSize = tempOutputFile!!.length(),
                        lastOpened = System.currentTimeMillis()
                    )
                    recentFileRepository.insertRecentFile(recent)

                    successUri = savedUri
                    successName = outName
                    lastOutputBytes = bytes
                }
                successMessage = "Files packed to TAR archive successfully!"
                tarInputUris = emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Failed to create TAR: ${e.localizedMessage}"
            } finally {
                withContext(Dispatchers.IO) {
                    cachedFiles.forEach { if (it.first.exists()) it.first.delete() }
                    tempOutputFile?.let { if (it.exists()) it.delete() }
                }
                isProcessing = false
            }
        }
    }

    fun unpackTarArchive() {
        val inputUri = tarExtractUri
        if (inputUri == null) {
            errorMessage = "Please select a TAR file to unpack."
            return
        }
        isProcessing = true
        resetStatus()
        successUri = null
        successName = null
        lastOutputBytes = null

        viewModelScope.launch {
            var tempInputFile: File? = null
            val extractedFiles = mutableListOf<File>()
            try {
                withContext(Dispatchers.IO) {
                    tempInputFile = UriCacheUtils.cacheUriToFile(context, inputUri)
                        ?: throw Exception("Could not open source TAR file.")

                    java.io.FileInputStream(tempInputFile!!).use { fis ->
                        val header = ByteArray(512)
                        while (fis.read(header) == 512) {
                            if (header.all { it == 0.toByte() }) {
                                break
                            }
                            
                            val nameLength = header.take(100).indexOf(0.toByte()).let { if (it == -1) 100 else it }
                            if (nameLength == 0) continue
                            val name = String(header, 0, nameLength, Charsets.UTF_8).trim()
                            
                            val sizeStr = String(header, 124, 12, Charsets.UTF_8).trim()
                            val size = try {
                                sizeStr.toLong(8)
                            } catch (e: Exception) {
                                0L
                            }
                            
                            val isFile = header[156] == '0'.toByte() || header[156] == 0.toByte()
                            
                            if (isFile && size > 0) {
                                val outFile = File(context.cacheDir, "extracted_${System.currentTimeMillis()}_$name")
                                FileOutputStream(outFile).use { fos ->
                                    var remaining = size
                                    val buf = ByteArray(1024)
                                    while (remaining > 0) {
                                        val toRead = minOf(remaining, buf.size.toLong()).toInt()
                                        val read = fis.read(buf, 0, toRead)
                                        if (read == -1) break
                                        fos.write(buf, 0, read)
                                        remaining -= read
                                    }
                                }
                                extractedFiles.add(outFile)
                                
                                val remainder = (size % 512).toInt()
                                if (remainder > 0) {
                                    val paddingBytes = 512 - remainder
                                    fis.skip(paddingBytes.toLong())
                                }
                            } else {
                                val blocks = (size + 511) / 512
                                fis.skip(blocks * 512)
                            }
                        }
                    }

                    if (extractedFiles.isEmpty()) {
                        throw Exception("No files were extracted from the archive.")
                    }

                    val firstFile = extractedFiles.first()
                    val savedUri = FileOutputManager.saveToDefault(
                        context = context,
                        bytes = firstFile.readBytes(),
                        filename = firstFile.name.substringAfterLast('_'),
                        mimeType = "*/*",
                        subfolder = "Archive"
                    ) ?: throw Exception("Failed to save unpacked file.")

                    val recent = RecentFile(
                        fileUri = savedUri.toString(),
                        fileName = firstFile.name.substringAfterLast('_'),
                        mimeType = "*/*",
                        fileSize = firstFile.length(),
                        lastOpened = System.currentTimeMillis()
                    )
                    recentFileRepository.insertRecentFile(recent)

                    successUri = savedUri
                    successName = firstFile.name.substringAfterLast('_')
                    lastOutputBytes = firstFile.readBytes()
                }
                successMessage = "TAR archive unpacked successfully! Files saved to Documents/OmniSuite/Archive/"
                tarExtractUri = null
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Failed to unpack TAR: ${e.localizedMessage}"
            } finally {
                withContext(Dispatchers.IO) {
                    tempInputFile?.let { if (it.exists()) it.delete() }
                    extractedFiles.forEach { if (it.exists()) it.delete() }
                }
                isProcessing = false
            }
        }
    }

    fun printWebViewToPdf(htmlContent: String?, webUrl: String?, filename: String) {
        isProcessing = true
        resetStatus()
        successUri = null
        successName = null
        lastOutputBytes = null

        viewModelScope.launch(Dispatchers.Main) {
            try {
                val webView = android.webkit.WebView(context)
                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                
                val tempOutputFile = File(context.cacheDir, "webview_printed_${System.currentTimeMillis()}.pdf")
                
                val onPageLoaded = {
                    val printAdapter = webView.createPrintDocumentAdapter("Print")
                    val printAttributes = PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build()
                        
                    val pfd = ParcelFileDescriptor.open(tempOutputFile, ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE)
                    
                    android.print.PrintAdapterHelper.print(printAdapter, printAttributes, pfd) { success, error ->
                        try {
                            pfd.close()
                        } catch (e: Exception) {}
                        
                        if (success) {
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    val bytes = tempOutputFile.readBytes()
                                    val savedUri = FileOutputManager.saveToDefault(
                                        context = context,
                                        bytes = bytes,
                                        filename = filename,
                                        mimeType = "application/pdf",
                                        subfolder = "PDF"
                                    ) ?: throw Exception("Failed to save printed WebView PDF.")
                                    
                                    val recent = RecentFile(
                                        fileUri = savedUri.toString(),
                                        fileName = filename,
                                        mimeType = "application/pdf",
                                        fileSize = tempOutputFile.length(),
                                        lastOpened = System.currentTimeMillis()
                                    )
                                    recentFileRepository.insertRecentFile(recent)
                                    
                                    withContext(Dispatchers.Main) {
                                        successUri = savedUri
                                        successName = filename
                                        lastOutputBytes = bytes
                                        successMessage = "Web/HTML printed to PDF successfully!"
                                        isProcessing = false
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        errorMessage = "Print failed: ${e.localizedMessage}"
                                        isProcessing = false
                                    }
                                } finally {
                                    if (tempOutputFile.exists()) tempOutputFile.delete()
                                }
                            }
                        } else {
                            errorMessage = error ?: "Print execution failed"
                            isProcessing = false
                            if (tempOutputFile.exists()) tempOutputFile.delete()
                        }
                    }
                }
                
                webView.webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        webView.postDelayed({
                            onPageLoaded()
                        }, 500)
                    }
                }
                
                if (htmlContent != null) {
                    webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                } else if (webUrl != null) {
                    webView.loadUrl(webUrl)
                }
            } catch (e: Exception) {
                errorMessage = "WebView print initialization error: ${e.localizedMessage}"
                isProcessing = false
            }
        }
    }
}

