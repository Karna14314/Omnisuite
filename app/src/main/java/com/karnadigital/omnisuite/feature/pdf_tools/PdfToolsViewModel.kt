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
import com.tomroush.pdfbox.android.PDFBoxResourceLoader
import com.tomroush.pdfbox.io.MemoryUsageSetting
import com.tomroush.pdfbox.multipdf.PDFMergerUtility
import com.tomroush.pdfbox.pdmodel.PDDocument
import com.tomroush.pdfbox.pdmodel.encryption.AccessPermission
import com.tomroush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
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

    var isProcessing by mutableStateOf(false)
        private set
    var successMessage by mutableStateOf<String?>(null)
        private set
    var errorMessage by mutableStateOf<String?>(null)
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
    fun mergePdfs(targetUri: Uri) {
        if (selectedMergeUris.size < 2) {
            errorMessage = "Please select at least 2 PDF documents to merge."
            return
        }
        isProcessing = true
        resetStatus()

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

                    // Write output sandbox bytes back to SAF target URI stream
                    context.contentResolver.openOutputStream(targetUri)?.use { outStream ->
                        tempOutputFile!!.inputStream().use { inpStream ->
                            inpStream.copyTo(outStream)
                        }
                    } ?: throw Exception("Failed to write to selected output stream.")

                    // Register to Room RecentFiles cache
                    val outName = getFileNameFromUri(targetUri) ?: "Merged_Document.pdf"
                    val recent = RecentFile(
                        fileUri = targetUri.toString(),
                        fileName = outName,
                        mimeType = "application/pdf",
                        fileSize = tempOutputFile!!.length(),
                        lastOpened = System.currentTimeMillis()
                    )
                    recentFileRepository.insertRecentFile(recent)
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
    fun splitPdf(targetDirectoryUri: Uri) {
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

                        val dirFile = DocumentFile.fromTreeUri(context, targetDirectoryUri)
                            ?: throw Exception("Failed to access selected destination directory.")

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

                                // Create SAF file in target directory
                                val newFile = dirFile.createFile("application/pdf", subFileName)
                                    ?: throw Exception("Failed to create file '$subFileName' in target directory.")

                                context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
                                    tempSubFile.inputStream().use { inp ->
                                        inp.copyTo(out)
                                    }
                                } ?: throw Exception("Failed to write to file: $subFileName")

                                // Register to recent files
                                val recent = RecentFile(
                                    fileUri = newFile.uri.toString(),
                                    fileName = subFileName,
                                    mimeType = "application/pdf",
                                    fileSize = tempSubFile.length(),
                                    lastOpened = System.currentTimeMillis()
                                )
                                recentFileRepository.insertRecentFile(recent)
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

    /**
     * Applies StandardProtectionPolicy password security locks on an open PDF file.
     */
    fun encryptPdf(targetUri: Uri) {
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

                    // Save output bytes to target SAF URI
                    context.contentResolver.openOutputStream(targetUri)?.use { out ->
                        tempOutputFile!!.inputStream().use { inp ->
                            inp.copyTo(out)
                        }
                    } ?: throw Exception("Failed to write encrypted file to storage.")

                    // Register to Room RecentFiles cache
                    val outName = getFileNameFromUri(targetUri) ?: "Secured_Document.pdf"
                    val recent = RecentFile(
                        fileUri = targetUri.toString(),
                        fileName = outName,
                        mimeType = "application/pdf",
                        fileSize = tempOutputFile!!.length(),
                        lastOpened = System.currentTimeMillis()
                    )
                    recentFileRepository.insertRecentFile(recent)
                }

                successMessage = "Password Lock applied and saved successfully!"
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
