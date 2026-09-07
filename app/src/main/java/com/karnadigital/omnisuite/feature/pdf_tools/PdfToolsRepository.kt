package com.karnadigital.omnisuite.feature.pdf_tools

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import com.karnadigital.omnisuite.core.engine.document.OfficeConverter
import com.karnadigital.omnisuite.core.engine.document.ReverseOfficeConverter
import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.core.repository.RecentFileRepository
import com.karnadigital.omnisuite.core.util.FileOutputManager
import com.karnadigital.omnisuite.core.util.UriCacheUtils
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class RedactionBox(
    val pageIndex: Int,
    val normX: Float,      // normalized 0f..1f relative to page width
    val normY: Float,      // normalized 0f..1f relative to page height (from top)
    val normWidth: Float,  // normalized 0f..1f
    val normHeight: Float  // normalized 0f..1f
)

@Singleton
class PdfToolsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recentFileRepository: RecentFileRepository,
    private val fileOutputManager: FileOutputManager,
    private val uriCacheUtils: UriCacheUtils,
    private val officeConverter: OfficeConverter,
    private val reverseOfficeConverter: ReverseOfficeConverter
) {

    private suspend fun getFileNameFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
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
        name
    }

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

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var curVal = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        curVal.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    curVal.append(ch)
                }
            } else {
                if (ch == '"') {
                    inQuotes = true
                } else if (ch == ',') {
                    result.add(curVal.toString())
                    curVal = StringBuilder()
                } else {
                    curVal.append(ch)
                }
            }
            i++
        }
        result.add(curVal.toString())
        return result
    }

    suspend fun saveBytesAndRegister(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        subfolder: String,
        isOperation: Boolean = true
    ): Uri = withContext(Dispatchers.IO) {
        val savedUri = fileOutputManager.saveToDefault(bytes, fileName, mimeType, subfolder)
            ?: throw Exception("Failed to save file.")
        registerRecentFile(savedUri, fileName, mimeType, bytes.size.toLong(), isOperation = isOperation)
        savedUri
    }

    suspend fun registerRecentFile(
        savedUri: Uri,
        fileName: String,
        mimeType: String,
        fileSize: Long,
        isOperation: Boolean = false
    ) {
        recentFileRepository.insertRecentFile(
            RecentFile(
                fileUri = savedUri.toString(),
                fileName = fileName,
                mimeType = mimeType,
                fileSize = fileSize,
                lastOpened = System.currentTimeMillis(),
                isOperation = isOperation
            )
        )
    }

    suspend fun mergePdfs(uris: List<Uri>, customFilename: String?): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempFiles = mutableListOf<File>()
            uris.forEach { uri ->
                val file = uriCacheUtils.cacheUriToFile(uri)
                    ?: throw Exception("Failed to cache file: $uri")
                tempFiles.add(file)
            }
            val tempOutputFile = File(context.cacheDir, "merged_output_${System.currentTimeMillis()}.pdf")
            val merger = PDFMergerUtility()
            tempFiles.forEach { merger.addSource(it) }
            FileOutputStream(tempOutputFile).use { outStream ->
                merger.destinationStream = outStream
                merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
            }
            val outName = customFilename ?: "merged_${System.currentTimeMillis()}.pdf"
            val bytes = tempOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/pdf", "PDF")
                ?: throw Exception("Failed to save merged PDF to OmniSuite/PDF folder.")
            registerRecentFile(savedUri, outName, "application/pdf", tempOutputFile.length())
            tempFiles.forEach { if (it.exists()) it.delete() }
            if (tempOutputFile.exists()) tempOutputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun splitPdf(inputUri: Uri, ranges: String): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open source PDF file.")
            val parsedRanges = parseRanges(ranges)
            if (parsedRanges.isEmpty()) throw Exception("No valid page ranges parsed. Use format: 1-3, 5-8")
            val createdTempFiles = mutableListOf<File>()
            var firstSavedUri: Uri? = null
            var firstName: String? = null
            var firstBytes: ByteArray? = null

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
                        for (p in startPage..endPage) subDoc.addPage(mainDocument.getPage(p - 1))
                        val subFileName = "${originalName}_part_${startPage}_to_${endPage}.pdf"
                        val tempSubFile = File(context.cacheDir, "split_${System.currentTimeMillis()}_$index.pdf")
                        createdTempFiles.add(tempSubFile)
                        FileOutputStream(tempSubFile).use { subDoc.save(it) }
                        val bytes = tempSubFile.readBytes()
                        val savedUri = fileOutputManager.saveToDefault(bytes, subFileName, "application/pdf", "PDF")
                            ?: throw Exception("Failed to save split file to OmniSuite folder.")
                        registerRecentFile(savedUri, subFileName, "application/pdf", tempSubFile.length())
                        if (index == 0) {
                            firstSavedUri = savedUri
                            firstName = subFileName
                            firstBytes = bytes
                        }
                    }
                }
            }
            if (tempInputFile.exists()) tempInputFile.delete()
            createdTempFiles.forEach { if (it.exists()) it.delete() }
            Result.success(firstSavedUri!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun encryptPdf(inputUri: Uri, password: String, customFilename: String?): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open source PDF file.")
            val tempOutputFile = File(context.cacheDir, "secured_${System.currentTimeMillis()}.pdf")
            PDDocument.load(tempInputFile).use { document ->
                val ap = AccessPermission()
                val spp = StandardProtectionPolicy(password, password, ap).apply { encryptionKeyLength = 128 }
                document.protect(spp)
                FileOutputStream(tempOutputFile).use { document.save(it) }
            }
            val outName = customFilename ?: "secured_${System.currentTimeMillis()}.pdf"
            val bytes = tempOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/pdf", "PDF")
                ?: throw Exception("Failed to save encrypted PDF to OmniSuite/PDF folder.")
            registerRecentFile(savedUri, outName, "application/pdf", tempOutputFile.length())
            if (tempInputFile.exists()) tempInputFile.delete()
            if (tempOutputFile.exists()) tempOutputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun convertDocToPdf(inputUri: Uri): Result<Uri> = withContext(Dispatchers.IO) {
        var tempInputFile: File? = null
        var tempOutputFile: File? = null
        try {
            tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open Word document.")
            tempOutputFile = File(context.cacheDir, "docx_converted_${System.currentTimeMillis()}.pdf")
            officeConverter.convertDocxToPdf(tempInputFile, tempOutputFile)

            val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".docx").removeSuffix(".doc")
            val outName = "${originalName}_converted.pdf"
            val bytes = tempOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/pdf", "PDF")
                ?: throw Exception("Failed to save PDF to OmniSuite folder.")
            registerRecentFile(savedUri, outName, "application/pdf", tempOutputFile.length(), isOperation = true)
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            if (tempInputFile?.exists() == true) tempInputFile?.delete()
            if (tempOutputFile?.exists() == true) tempOutputFile?.delete()
        }
    }

    suspend fun convertPptToPdf(inputUri: Uri, renderMode: String): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open PowerPoint document.")
            val tempOutputFile = File(context.cacheDir, "pptx_converted_${System.currentTimeMillis()}.pdf")
            officeConverter.convertPptxToPdf(tempInputFile, tempOutputFile, renderMode)
            val originalName = (getFileNameFromUri(inputUri) ?: "presentation").removeSuffix(".pptx").removeSuffix(".ppt")
            val outName = "${originalName}_converted.pdf"
            val bytes = tempOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/pdf", "PDF")
                ?: throw Exception("Failed to save PDF to OmniSuite folder.")
            registerRecentFile(savedUri, outName, "application/pdf", tempOutputFile.length())
            if (tempInputFile.exists()) tempInputFile.delete()
            if (tempOutputFile.exists()) tempOutputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveScannedPdf(scannedFile: File): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val bytes = scannedFile.readBytes()
            val outName = "Scan_${System.currentTimeMillis()}.pdf"
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/pdf", "PDF")
                ?: throw Exception("Failed to save scanned document.")
            registerRecentFile(savedUri, outName, "application/pdf", scannedFile.length())
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun convertPdfToImages(inputUri: Uri): Result<List<Uri>> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open source PDF document.")
            val parcelFileDescriptor = ParcelFileDescriptor.open(tempInputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = android.graphics.pdf.PdfRenderer(parcelFileDescriptor)
            val pageCount = pdfRenderer.pageCount
            if (pageCount == 0) {
                pdfRenderer.close()
                parcelFileDescriptor.close()
                throw Exception("PDF has no pages to extract.")
            }
            val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".pdf")
            val savedUris = mutableListOf<Uri>()
            var totalSize = 0L
            for (i in 0 until pageCount) {
                val page = pdfRenderer.openPage(i)
                val bitmap = android.graphics.Bitmap.createBitmap(page.width * 2, page.height * 2, android.graphics.Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                val stream = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                val bytes = stream.toByteArray()
                bitmap.recycle()
                val imageName = "${originalName}_page_${i + 1}.png"
                val savedUri = fileOutputManager.saveToDefault(bytes, imageName, "image/png", "Images")
                    ?: throw Exception("Failed to save page ${i + 1} image.")
                savedUris.add(savedUri)
                totalSize += bytes.size.toLong()
            }
            val batchUriString = savedUris.joinToString("|||") { it.toString() }
            registerRecentFile(Uri.parse(batchUriString), "${originalName} (All Pages)", "image/png", totalSize)
            pdfRenderer.close()
            parcelFileDescriptor.close()
            if (tempInputFile.exists()) tempInputFile.delete()
            Result.success(savedUris)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun convertPdfToDocx(uri: Uri): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val outputUri = reverseOfficeConverter.convertPdfToDocx(uri)
                ?: throw Exception("Failed to convert PDF to DOCX.")
            Result.success(outputUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun convertPdfToPptx(uri: Uri): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val outputUri = reverseOfficeConverter.convertPdfToPptx(uri)
                ?: throw Exception("Failed to convert PDF to PPTX.")
            Result.success(outputUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fillPdfForm(uri: Uri, formData: Map<String, String>): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val outputUri = reverseOfficeConverter.fillInteractiveForm(uri, formData)
                ?: throw Exception("Failed to fill interactive PDF form.")
            Result.success(outputUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun compressPdf(inputUri: Uri, quality: Float, targetSizeBytes: Long? = null): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open source PDF file.")
            val tempOutputFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.pdf")
            
            val originalSize = tempInputFile.length()
            val effectiveQuality: Float = if (targetSizeBytes != null && originalSize > 0) {
                val ratio = (targetSizeBytes.toFloat() / originalSize.toFloat()).coerceIn(0.15f, 0.95f)
                (ratio * 0.85f).coerceIn(0.15f, 0.90f)
            } else {
                quality.coerceIn(0.1f, 1.0f)
            }

            PDDocument.load(tempInputFile).use { document ->
                for (page in document.pages) {
                    val resources = page.resources ?: continue
                    for (name in resources.xObjectNames) {
                        if (resources.isImageXObject(name)) {
                            val xObject = resources.getXObject(name)
                            if (xObject is PDImageXObject) {
                                val bitmap = xObject.image ?: continue
                                val stream = java.io.ByteArrayOutputStream()
                                val qualityPercent = (effectiveQuality * 100).toInt().coerceIn(10, 100)
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, qualityPercent, stream)
                                val compressedBytes = stream.toByteArray()
                                val compressedImage = JPEGFactory.createFromStream(document, java.io.ByteArrayInputStream(compressedBytes))
                                resources.put(name, compressedImage)
                                bitmap.recycle()
                            }
                        }
                    }
                }
                FileOutputStream(tempOutputFile).use { document.save(it) }
            }
            val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".pdf")
            val outName = "${originalName}_compressed.pdf"
            val bytes = tempOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/pdf", "PDF")
                ?: throw Exception("Failed to save compressed PDF.")
            registerRecentFile(savedUri, outName, "application/pdf", tempOutputFile.length())
            if (tempInputFile.exists()) tempInputFile.delete()
            if (tempOutputFile.exists()) tempOutputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun flattenPdf(inputUri: Uri): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open source PDF file.")
            val tempOutputFile = File(context.cacheDir, "flattened_${System.currentTimeMillis()}.pdf")
            PDDocument.load(tempInputFile).use { document ->
                val acroForm = document.documentCatalog.acroForm
                if (acroForm != null) {
                    acroForm.flatten()
                } else {
                    throw Exception("This PDF does not contain any interactive form fields to flatten.")
                }
                FileOutputStream(tempOutputFile).use { document.save(it) }
            }
            val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".pdf")
            val outName = "${originalName}_flattened.pdf"
            val bytes = tempOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/pdf", "PDF")
                ?: throw Exception("Failed to save flattened PDF.")
            registerRecentFile(savedUri, outName, "application/pdf", tempOutputFile.length())
            if (tempInputFile.exists()) tempInputFile.delete()
            if (tempOutputFile.exists()) tempOutputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun convertXlsToPdf(inputUri: Uri): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open Excel workbook.")
            val tempOutputFile = File(context.cacheDir, "xlsx_converted_${System.currentTimeMillis()}.pdf")
            officeConverter.convertXlsxToPdf(tempInputFile, tempOutputFile)
            val originalName = (getFileNameFromUri(inputUri) ?: "spreadsheet").removeSuffix(".xlsx").removeSuffix(".xls")
            val outName = "${originalName}_converted.pdf"
            val bytes = tempOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/pdf", "PDF")
                ?: throw Exception("Failed to save PDF to OmniSuite folder.")
            registerRecentFile(savedUri, outName, "application/pdf", tempOutputFile.length())
            if (tempInputFile.exists()) tempInputFile.delete()
            if (tempOutputFile.exists()) tempOutputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun decryptPdf(inputUri: Uri, password: String): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open source PDF file.")
            val tempOutputFile = File(context.cacheDir, "decrypted_${System.currentTimeMillis()}.pdf")
            PDDocument.load(tempInputFile, password).use { document ->
                if (document.isEncrypted) document.setAllSecurityToBeRemoved(true)
                FileOutputStream(tempOutputFile).use { document.save(it) }
            }
            val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".pdf")
            val outName = "${originalName}_unlocked.pdf"
            val bytes = tempOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/pdf", "PDF")
                ?: throw Exception("Failed to save decrypted PDF.")
            registerRecentFile(savedUri, outName, "application/pdf", tempOutputFile.length())
            if (tempInputFile.exists()) tempInputFile.delete()
            if (tempOutputFile.exists()) tempOutputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rotatePdfPages(
        inputUri: Uri,
        rotations: Map<Int, Int> = emptyMap(),
        defaultDegrees: Int = 90,
        targetMode: String = "ALL",
        customRange: String = ""
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open source PDF file.")
            val tempOutputFile = File(context.cacheDir, "rotated_${System.currentTimeMillis()}.pdf")
            PDDocument.load(tempInputFile).use { document ->
                val pages = document.pages
                val totalPages = pages.count

                val mapToApply: Map<Int, Int> = if (rotations.isNotEmpty()) {
                    rotations
                } else {
                    val indices = when (targetMode) {
                        "ODD" -> (0 until totalPages).filter { it % 2 == 0 }
                        "EVEN" -> (0 until totalPages).filter { it % 2 == 1 }
                        "CUSTOM" -> {
                            val set = mutableSetOf<Int>()
                            customRange.split(",").forEach { part ->
                                val trimmed = part.trim()
                                if (trimmed.contains("-")) {
                                    val bounds = trimmed.split("-")
                                    val start = bounds.getOrNull(0)?.toIntOrNull()
                                    val end = bounds.getOrNull(1)?.toIntOrNull()
                                    if (start != null && end != null) {
                                        for (i in minOf(start, end)..maxOf(start, end)) {
                                            if (i in 1..totalPages) set.add(i - 1)
                                        }
                                    }
                                } else {
                                    val num = trimmed.toIntOrNull()
                                    if (num != null && num in 1..totalPages) set.add(num - 1)
                                }
                            }
                            set
                        }
                        else -> (0 until totalPages)
                    }
                    indices.associateWith { defaultDegrees }
                }

                mapToApply.forEach { (pageIdx, angle) ->
                    if (pageIdx in 0 until totalPages) {
                        val page = pages.get(pageIdx)
                        page.rotation = (page.rotation + angle) % 360
                    }
                }
                FileOutputStream(tempOutputFile).use { document.save(it) }
            }
            val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".pdf")
            val outName = "${originalName}_rotated.pdf"
            val bytes = tempOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/pdf", "PDF")
                ?: throw Exception("Failed to save rotated PDF.")
            registerRecentFile(savedUri, outName, "application/pdf", tempOutputFile.length())
            if (tempInputFile.exists()) tempInputFile.delete()
            if (tempOutputFile.exists()) tempOutputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun extractPdfPages(inputUri: Uri, selectedPages: Set<Int>): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open source PDF file.")
            val tempOutputFile = File(context.cacheDir, "extracted_${System.currentTimeMillis()}.pdf")
            PDDocument.load(tempInputFile).use { document ->
                PDDocument().use { outputDoc ->
                    val pages = document.pages
                    selectedPages.sorted().forEach { pageIdx ->
                        if (pageIdx in 0 until pages.count) outputDoc.addPage(pages.get(pageIdx))
                    }
                    FileOutputStream(tempOutputFile).use { outputDoc.save(it) }
                }
            }
            val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".pdf")
            val outName = "${originalName}_extracted.pdf"
            val bytes = tempOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/pdf", "PDF")
                ?: throw Exception("Failed to save extracted PDF.")
            registerRecentFile(savedUri, outName, "application/pdf", tempOutputFile.length())
            if (tempInputFile.exists()) tempInputFile.delete()
            if (tempOutputFile.exists()) tempOutputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePdfPages(inputUri: Uri, selectedPages: Set<Int>): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open source PDF file.")
            val tempOutputFile = File(context.cacheDir, "deleted_pages_${System.currentTimeMillis()}.pdf")
            PDDocument.load(tempInputFile).use { document ->
                val pages = document.pages
                if (selectedPages.size >= pages.count) throw Exception("Cannot delete all pages in the document.")
                selectedPages.sortedDescending().forEach { pageIdx ->
                    if (pageIdx in 0 until pages.count) document.removePage(pageIdx)
                }
                FileOutputStream(tempOutputFile).use { document.save(it) }
            }
            val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".pdf")
            val outName = "${originalName}_modified.pdf"
            val bytes = tempOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/pdf", "PDF")
                ?: throw Exception("Failed to save modified PDF.")
            registerRecentFile(savedUri, outName, "application/pdf", tempOutputFile.length())
            if (tempInputFile.exists()) tempInputFile.delete()
            if (tempOutputFile.exists()) tempOutputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun convertDocxToTxt(inputUri: Uri): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open source file.")
            val textBuilder = StringBuilder()
            val filename = (getFileNameFromUri(inputUri) ?: tempInputFile.name).lowercase()
            val isLegacyDoc = filename.endsWith(".doc") && !filename.endsWith(".docx")

            if (isLegacyDoc) {
                try {
                    java.io.FileInputStream(tempInputFile).use { fis ->
                        val doc = org.apache.poi.hwpf.HWPFDocument(fis)
                        val extractor = org.apache.poi.hwpf.extractor.WordExtractor(doc)
                        for (p in extractor.paragraphText) {
                            val trimmed = p.trimEnd()
                            if (trimmed.isNotEmpty()) textBuilder.append(trimmed).append("\n\n")
                        }
                        extractor.close()
                        doc.close()
                    }
                } catch (_: Throwable) {
                    extractFromXwpf(tempInputFile, textBuilder)
                }
            } else {
                try {
                    extractFromXwpf(tempInputFile, textBuilder)
                } catch (_: Throwable) {
                    try {
                        java.io.FileInputStream(tempInputFile).use { fis ->
                            val doc = org.apache.poi.hwpf.HWPFDocument(fis)
                            val extractor = org.apache.poi.hwpf.extractor.WordExtractor(doc)
                            for (p in extractor.paragraphText) {
                                val trimmed = p.trimEnd()
                                if (trimmed.isNotEmpty()) textBuilder.append(trimmed).append("\n\n")
                            }
                            extractor.close()
                            doc.close()
                        }
                    } catch (inner: Exception) {
                        throw Exception("Unable to parse Word document: ${inner.localizedMessage}")
                    }
                }
            }

            val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".docx").removeSuffix(".doc")
            val outName = "${originalName}_text.txt"
            val bytes = textBuilder.toString().toByteArray(Charsets.UTF_8)
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "text/plain", "Documents")
                ?: throw Exception("Failed to save converted TXT.")
            registerRecentFile(savedUri, outName, "text/plain", bytes.size.toLong())
            if (tempInputFile.exists()) tempInputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractFromXwpf(file: File, textBuilder: StringBuilder) {
        java.io.FileInputStream(file).use { fis ->
            org.apache.poi.xwpf.usermodel.XWPFDocument(fis).use { document ->
                for (element in document.bodyElements) {
                    if (element is org.apache.poi.xwpf.usermodel.XWPFParagraph) {
                        val text = element.text.trimEnd()
                        if (text.isNotEmpty()) {
                            val isHeading = element.style?.contains("Heading", ignoreCase = true) == true
                            if (isHeading) {
                                textBuilder.append("\n## ").append(text).append("\n\n")
                            } else {
                                textBuilder.append(text).append("\n\n")
                            }
                        }
                    } else if (element is org.apache.poi.xwpf.usermodel.XWPFTable) {
                        textBuilder.append("\n")
                        for (row in element.rows) {
                            val rowCells = row.tableCells.joinToString(" \t| ") { it.text.trim() }
                            textBuilder.append(rowCells).append("\n")
                        }
                        textBuilder.append("\n")
                    }
                }
            }
        }
    }

    suspend fun convertCsvToXlsx(inputUri: Uri): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open source file.")
            val tempOutputFile = File(context.cacheDir, "converted_${System.currentTimeMillis()}.xlsx")
            org.apache.poi.xssf.usermodel.XSSFWorkbook().use { workbook ->
                val sheet = workbook.createSheet("CSV Data")
                val lines = tempInputFile.readLines(Charsets.UTF_8)
                lines.forEachIndexed { r, line ->
                    val row = sheet.createRow(r)
                    val cells = parseCsvLine(line)
                    cells.forEachIndexed { c, cellVal ->
                        val cell = row.createCell(c)
                        val doubleVal = cellVal.toDoubleOrNull()
                        if (doubleVal != null) cell.setCellValue(doubleVal) else cell.setCellValue(cellVal)
                    }
                }
                FileOutputStream(tempOutputFile).use { workbook.write(it) }
            }
            val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".csv")
            val outName = "${originalName}_excel.xlsx"
            val bytes = tempOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Documents")
                ?: throw Exception("Failed to save Excel file.")
            registerRecentFile(savedUri, outName, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", tempOutputFile.length())
            if (tempInputFile.exists()) tempInputFile.delete()
            if (tempOutputFile.exists()) tempOutputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun convertXlsxToCsv(inputUri: Uri): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open source file.")
            val csvBuilder = StringBuilder()
            java.io.FileInputStream(tempInputFile).use { fis ->
                org.apache.poi.xssf.usermodel.XSSFWorkbook(fis).use { workbook ->
                    val sheet = workbook.getSheetAt(0)
                    val dataFormatter = org.apache.poi.ss.usermodel.DataFormatter()
                    for (r in 0..sheet.lastRowNum) {
                        val row = sheet.getRow(r)
                        if (row == null) { csvBuilder.append("\n"); continue }
                        val cellList = mutableListOf<String>()
                        for (c in 0 until row.lastCellNum) {
                            val cell = row.getCell(c)
                            val text = dataFormatter.formatCellValue(cell)
                            val escaped = if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
                                "\"" + text.replace("\"", "\"\"") + "\""
                            } else text
                            cellList.add(escaped)
                        }
                        csvBuilder.append(cellList.joinToString(",")).append("\n")
                    }
                }
            }
            val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".xlsx").removeSuffix(".xls")
            val outName = "${originalName}_csv.csv"
            val bytes = csvBuilder.toString().toByteArray(Charsets.UTF_8)
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "text/csv", "Documents")
                ?: throw Exception("Failed to save CSV.")
            registerRecentFile(savedUri, outName, "text/csv", bytes.size.toLong())
            if (tempInputFile.exists()) tempInputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun convertPptxToTxt(inputUri: Uri): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open source file.")
            val textBuilder = StringBuilder()
            java.io.FileInputStream(tempInputFile).use { fis ->
                org.apache.poi.xslf.usermodel.XMLSlideShow(fis).use { ppt ->
                    ppt.slides.forEachIndexed { index, slide ->
                        textBuilder.append("--- Slide ").append(index + 1).append(" ---\n")
                        slide.shapes.forEach { shape ->
                            if (shape is org.apache.poi.xslf.usermodel.XSLFTextShape) textBuilder.append(shape.text).append("\n")
                        }
                        textBuilder.append("\n")
                    }
                }
            }
            val originalName = (getFileNameFromUri(inputUri) ?: "presentation").removeSuffix(".pptx").removeSuffix(".ppt")
            val outName = "${originalName}_slides.txt"
            val bytes = textBuilder.toString().toByteArray(Charsets.UTF_8)
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "text/plain", "Documents")
                ?: throw Exception("Failed to save TXT.")
            registerRecentFile(savedUri, outName, "text/plain", bytes.size.toLong())
            if (tempInputFile.exists()) tempInputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun convertMarkdownToPdf(markdownText: String, filename: String): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempOutputFile = File(context.cacheDir, "md_converted_${System.currentTimeMillis()}.pdf")
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
                    if (cleanLine.isEmpty()) { yOffset -= 15f; return@forEach }
                    val isHeader = cleanLine.startsWith("#")
                    var headerLevel = 0
                    if (isHeader) {
                        while (headerLevel < cleanLine.length && cleanLine[headerLevel] == '#') headerLevel++
                    }
                    val text = if (isHeader) cleanLine.substring(headerLevel).trim() else cleanLine
                    val currentFont = if (isHeader) fontBold else fontNormal
                    val fontSize = if (isHeader) {
                        when (headerLevel) { 1 -> 24f; 2 -> 18f; else -> 14f }
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
                    if (wrappedLine.isNotEmpty()) contentStream.showText(wrappedLine.toString())
                    contentStream.endText()
                    yOffset -= (fontSize + 8f)
                }
                contentStream.close()
                FileOutputStream(tempOutputFile).use { doc.save(it) }
            }
            val outName = if (filename.endsWith(".pdf", ignoreCase = true)) filename else "$filename.pdf"
            val bytes = tempOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/pdf", "PDF")
                ?: throw Exception("Failed to save Markdown PDF.")
            registerRecentFile(savedUri, outName, "application/pdf", tempOutputFile.length())
            if (tempOutputFile.exists()) tempOutputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun packTarArchive(uris: List<Uri>, outputName: String): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val cachedFiles = mutableListOf<Pair<File, String>>()
            uris.forEach { uri ->
                val file = uriCacheUtils.cacheUriToFile(uri)
                val name = getFileNameFromUri(uri) ?: "file_${System.currentTimeMillis()}"
                if (file != null) cachedFiles.add(Pair(file, name))
            }
            val tempOutputFile = File(context.cacheDir, "archive_${System.currentTimeMillis()}.tar")
            FileOutputStream(tempOutputFile).use { fos ->
                cachedFiles.forEach { (file, name) ->
                    val size = file.length()
                    val header = ByteArray(512)
                    val nameBytes = name.take(99).toByteArray(Charsets.UTF_8)
                    System.arraycopy(nameBytes, 0, header, 0, nameBytes.size)
                    val modeBytes = "0000644\u0000".toByteArray()
                    System.arraycopy(modeBytes, 0, header, 100, modeBytes.size)
                    val sizeOctal = String.format("%011o", size) + " "
                    System.arraycopy(sizeOctal.toByteArray(), 0, header, 124, sizeOctal.toByteArray().size)
                    val modTimeOctal = String.format("%011o", file.lastModified() / 1000L) + " "
                    System.arraycopy(modTimeOctal.toByteArray(), 0, header, 136, modTimeOctal.toByteArray().size)
                    header[156] = '0'.toByte()
                    System.arraycopy("ustar\u0000".toByteArray(), 0, header, 257, 6)
                    for (j in 148 until 156) header[j] = ' '.toByte()
                    var checksum = 0
                    for (b in header) checksum += (b.toInt() and 0xFF)
                    val checksumOctal = String.format("%06o", checksum) + "\u0000 "
                    System.arraycopy(checksumOctal.toByteArray(), 0, header, 148, checksumOctal.toByteArray().size)
                    fos.write(header)
                    java.io.FileInputStream(file).use { fis ->
                        val buf = ByteArray(1024)
                        var read: Int
                        while (fis.read(buf).also { read = it } != -1) fos.write(buf, 0, read)
                    }
                    val remainder = (size % 512).toInt()
                    if (remainder > 0) fos.write(ByteArray(512 - remainder))
                }
                fos.write(ByteArray(1024))
            }
            val outName = if (outputName.endsWith(".tar", ignoreCase = true)) outputName else "$outputName.tar"
            val bytes = tempOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/x-tar", "Archive")
                ?: throw Exception("Failed to save TAR archive.")
            registerRecentFile(savedUri, outName, "application/x-tar", tempOutputFile.length())
            cachedFiles.forEach { if (it.first.exists()) it.first.delete() }
            if (tempOutputFile.exists()) tempOutputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unpackTarArchive(inputUri: Uri): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open source TAR file.")
            val extractedFiles = mutableListOf<File>()
            java.io.FileInputStream(tempInputFile).use { fis ->
                val header = ByteArray(512)
                while (fis.read(header) == 512) {
                    if (header.all { it == 0.toByte() }) break
                    val nameLength = header.take(100).indexOf(0.toByte()).let { if (it == -1) 100 else it }
                    if (nameLength == 0) continue
                    val name = String(header, 0, nameLength, Charsets.UTF_8).trim()
                    val sizeStr = String(header, 124, 12, Charsets.UTF_8).trim()
                    val size = try { sizeStr.toLong(8) } catch (e: Exception) { 0L }
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
                        if (remainder > 0) fis.skip((512 - remainder).toLong())
                    } else {
                        val blocks = (size + 511) / 512
                        fis.skip(blocks * 512)
                    }
                }
            }
            if (extractedFiles.isEmpty()) throw Exception("No files were extracted from the archive.")
            val firstFile = extractedFiles.first()
            val savedUri = fileOutputManager.saveToDefault(firstFile.readBytes(), firstFile.name.substringAfterLast('_'), "*/*", "Archive")
                ?: throw Exception("Failed to save unpacked file.")
            registerRecentFile(savedUri, firstFile.name.substringAfterLast('_'), "*/*", firstFile.length())
            if (tempInputFile.exists()) tempInputFile.delete()
            extractedFiles.forEach { if (it.exists()) it.delete() }
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addPageNumbers(inputUri: Uri, startNumber: Int, position: String, fontSize: Int, customFilename: String?): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open source PDF file.")
            val tempOutputFile = File(context.cacheDir, "pagenums_${System.currentTimeMillis()}.pdf")
            PDDocument.load(tempInputFile).use { document ->
                val font = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA
                val pageCount = document.numberOfPages
                for (i in 0 until pageCount) {
                    val page = document.getPage(i)
                    val contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(document, page, com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND, true)
                    val pageNum = (startNumber + i).toString()
                    val textWidth = font.getStringWidth(pageNum) * fontSize / 1000f
                    val mediaBox = page.mediaBox
                    val pageWidth = mediaBox.width
                    val pageHeight = mediaBox.height
                    val x = when (position) {
                        "top-left", "bottom-left" -> 30f
                        "top-center", "bottom-center" -> (pageWidth - textWidth) / 2f
                        "top-right", "bottom-right" -> pageWidth - textWidth - 30f
                        else -> (pageWidth - textWidth) / 2f
                    }
                    val y = when (position) {
                        "top-left", "top-center", "top-right" -> pageHeight - 30f
                        "bottom-left", "bottom-center", "bottom-right" -> 20f
                        else -> 20f
                    }
                    contentStream.beginText()
                    contentStream.setFont(font, fontSize.toFloat())
                    contentStream.newLineAtOffset(x, y)
                    contentStream.showText(pageNum)
                    contentStream.endText()
                    contentStream.close()
                }
                FileOutputStream(tempOutputFile).use { document.save(it) }
            }
            val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".pdf")
            val outName = customFilename ?: "${originalName}_numbered.pdf"
            val bytes = tempOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/pdf", "PDF")
                ?: throw Exception("Failed to save numbered PDF.")
            registerRecentFile(savedUri, outName, "application/pdf", tempOutputFile.length())
            if (tempInputFile.exists()) tempInputFile.delete()
            if (tempOutputFile.exists()) tempOutputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reorderPdfPages(inputUri: Uri, newOrder: List<Int>): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open source PDF file.")
            val tempOutputFile = File(context.cacheDir, "reordered_${System.currentTimeMillis()}.pdf")
            PDDocument.load(tempInputFile).use { document ->
                val pageCount = document.numberOfPages
                if (newOrder.size != pageCount) throw Exception("New order must contain exactly $pageCount pages.")
                val seen = mutableSetOf<Int>()
                for (idx in newOrder) {
                    if (idx < 0 || idx >= pageCount) throw Exception("Invalid page index: $idx")
                    if (!seen.add(idx)) throw Exception("Duplicate page index: $idx")
                }
                val pages = (0 until pageCount).map { document.getPage(it) }
                val newDoc = PDDocument()
                for (idx in newOrder) {
                    newDoc.addPage(pages[idx])
                }
                FileOutputStream(tempOutputFile).use { newDoc.save(it) }
                newDoc.close()
            }
            val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".pdf")
            val outName = "${originalName}_reordered.pdf"
            val bytes = tempOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/pdf", "PDF")
                ?: throw Exception("Failed to save reordered PDF.")
            registerRecentFile(savedUri, outName, "application/pdf", tempOutputFile.length())
            if (tempInputFile.exists()) tempInputFile.delete()
            if (tempOutputFile.exists()) tempOutputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun extractImagesFromPdf(inputUri: Uri): Result<List<Uri>> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open source PDF file.")
            val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".pdf")
            val savedUris = mutableListOf<Uri>()
            var totalSize = 0L
            PDDocument.load(tempInputFile).use { document ->
                var imageIndex = 0
                for (pageNum in 0 until document.numberOfPages) {
                    val page = document.getPage(pageNum)
                    val resources = page.resources ?: continue
                    for (name in resources.xObjectNames) {
                        if (resources.isImageXObject(name)) {
                            val xObject = resources.getXObject(name)
                            if (xObject is PDImageXObject) {
                                val image = xObject.image ?: continue
                                val stream = java.io.ByteArrayOutputStream()
                                val success = android.graphics.Bitmap.CompressFormat.PNG.let { format ->
                                    image.compress(format, 100, stream)
                                }
                                if (success) {
                                    val bytes = stream.toByteArray()
                                    val imageName = "${originalName}_page${pageNum + 1}_img${imageIndex + 1}.png"
                                    val savedUri = fileOutputManager.saveToDefault(bytes, imageName, "image/png", "Images")
                                        ?: continue
                                    savedUris.add(savedUri)
                                    totalSize += bytes.size.toLong()
                                    imageIndex++
                                }
                                image.recycle()
                            }
                        }
                    }
                }
            }
            if (savedUris.isEmpty()) throw Exception("No images found in the PDF.")
            val batchUriString = savedUris.joinToString("|||") { it.toString() }
            registerRecentFile(Uri.parse(batchUriString), "${originalName} (Extracted Images)", "image/png", totalSize)
            if (tempInputFile.exists()) tempInputFile.delete()
            Result.success(savedUris)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun convertTxtToPdf(inputUri: Uri, fontSize: Int = 12, customFilename: String? = null): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open source text file.")
            val tempOutputFile = File(context.cacheDir, "txt_converted_${System.currentTimeMillis()}.pdf")
            val content = tempInputFile.readText(Charsets.UTF_8)
            PDDocument().use { doc ->
                val font = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA
                val margin = 50f
                val lineHeight = fontSize + 4f
                val lines = content.split("\n")
                var page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
                doc.addPage(page)
                var contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page)
                var yOffset = page.mediaBox.height - margin
                for (line in lines) {
                    if (yOffset < margin + lineHeight) {
                        contentStream.close()
                        page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
                        doc.addPage(page)
                        contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page)
                        yOffset = page.mediaBox.height - margin
                    }
                    val wrappedLines = wrapText(line, font, fontSize.toFloat(), page.mediaBox.width - 2 * margin)
                    for (wrappedLine in wrappedLines) {
                        if (yOffset < margin + lineHeight) {
                            contentStream.close()
                            page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
                            doc.addPage(page)
                            contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page)
                            yOffset = page.mediaBox.height - margin
                        }
                        contentStream.beginText()
                        contentStream.setFont(font, fontSize.toFloat())
                        contentStream.newLineAtOffset(margin, yOffset)
                        contentStream.showText(wrappedLine)
                        contentStream.endText()
                        yOffset -= lineHeight
                    }
                }
                contentStream.close()
                FileOutputStream(tempOutputFile).use { doc.save(it) }
            }
            val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".txt")
            val outName = customFilename ?: "${originalName}_converted.pdf"
            val bytes = tempOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/pdf", "PDF")
                ?: throw Exception("Failed to save PDF.")
            registerRecentFile(savedUri, outName, "application/pdf", tempOutputFile.length())
            if (tempInputFile.exists()) tempInputFile.delete()
            if (tempOutputFile.exists()) tempOutputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun convertCsvToPdf(inputUri: Uri, fontSize: Int = 10, customFilename: String? = null): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open source CSV file.")
            val tempOutputFile = File(context.cacheDir, "csv_converted_${System.currentTimeMillis()}.pdf")
            val lines = tempInputFile.readLines(Charsets.UTF_8)
            if (lines.isEmpty()) throw Exception("CSV file is empty.")
            val tableData = lines.map { parseCsvLine(it) }
            val maxCols = tableData.maxOfOrNull { it.size } ?: 1
            PDDocument().use { doc ->
                val font = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA
                val fontBold = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD
                val margin = 40f
                val lineHeight = fontSize + 4f
                val pageWidth = com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4.width
                val colWidth = (pageWidth - 2 * margin) / maxCols
                var page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
                doc.addPage(page)
                var contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page)
                var yOffset = page.mediaBox.height - margin
                for ((rowIdx, row) in tableData.withIndex()) {
                    if (yOffset < margin + lineHeight) {
                        contentStream.close()
                        page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
                        doc.addPage(page)
                        contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page)
                        yOffset = page.mediaBox.height - margin
                    }
                    var xOffset = margin
                    val currentFont = if (rowIdx == 0) fontBold else font
                    for (cell in row) {
                        val cellText = if (cell.length > 20) cell.take(17) + "..." else cell
                        contentStream.beginText()
                        contentStream.setFont(currentFont, fontSize.toFloat())
                        contentStream.newLineAtOffset(xOffset, yOffset)
                        contentStream.showText(cellText)
                        contentStream.endText()
                        xOffset += colWidth
                    }
                    yOffset -= lineHeight
                }
                contentStream.close()
                FileOutputStream(tempOutputFile).use { doc.save(it) }
            }
            val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".csv")
            val outName = customFilename ?: "${originalName}_converted.pdf"
            val bytes = tempOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/pdf", "PDF")
                ?: throw Exception("Failed to save PDF.")
            registerRecentFile(savedUri, outName, "application/pdf", tempOutputFile.length())
            if (tempInputFile.exists()) tempInputFile.delete()
            if (tempOutputFile.exists()) tempOutputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun convertPdfToTxt(inputUri: Uri, customFilename: String? = null): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open source PDF file.")
            val tempOutputFile = File(context.cacheDir, "pdf_to_txt_${System.currentTimeMillis()}.txt")
            val textBuilder = StringBuilder()
            PDDocument.load(tempInputFile).use { document ->
                val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                textBuilder.append(stripper.getText(document))
            }
            tempOutputFile.writeText(textBuilder.toString(), Charsets.UTF_8)
            val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".pdf")
            val outName = customFilename ?: "${originalName}_text.txt"
            val bytes = tempOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "text/plain", "Documents")
                ?: throw Exception("Failed to save TXT file.")
            registerRecentFile(savedUri, outName, "text/plain", tempOutputFile.length())
            if (tempInputFile.exists()) tempInputFile.delete()
            if (tempOutputFile.exists()) tempOutputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun convertImagesToPdfWithLayout(inputUris: List<Uri>, imagesPerPage: Int = 1, customFilename: String? = null): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempFiles = mutableListOf<File>()
            inputUris.forEach { uri ->
                val file = uriCacheUtils.cacheUriToFile(uri)
                if (file != null) tempFiles.add(file)
            }
            if (tempFiles.isEmpty()) throw Exception("No valid images found.")
            val tempOutputFile = File(context.cacheDir, "images_layout_${System.currentTimeMillis()}.pdf")
            PDDocument().use { doc ->
                val pageWidth = com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4.width
                val pageHeight = com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4.height
                val margin = 20f
                val cols = when (imagesPerPage) {
                    1 -> 1; 2 -> 1; 4 -> 2; 6 -> 2; 9 -> 3; else -> 1
                }
                val rows = when (imagesPerPage) {
                    1 -> 1; 2 -> 2; 4 -> 2; 6 -> 3; 9 -> 3; else -> 1
                }
                val cellWidth = (pageWidth - 2 * margin) / cols
                val cellHeight = (pageHeight - 2 * margin) / rows
                var page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
                doc.addPage(page)
                var imgIdx = 0
                for (file in tempFiles) {
                    if (imgIdx > 0 && imgIdx % imagesPerPage == 0) {
                        page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
                        doc.addPage(page)
                    }
                    val slotInPage = imgIdx % imagesPerPage
                    val col = slotInPage % cols
                    val row = slotInPage / cols
                    val imgBytes = file.readBytes()
                    val pdImage = com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject.createFromByteArray(doc, imgBytes, "img_$imgIdx")
                    val contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page)
                    val maxImgWidth = cellWidth - 10f
                    val maxImgHeight = cellHeight - 10f
                    val imgWidth = pdImage.width.toFloat()
                    val imgHeight = pdImage.height.toFloat()
                    val scale = minOf(maxImgWidth / imgWidth, maxImgHeight / imgHeight, 1f)
                    val drawWidth = imgWidth * scale
                    val drawHeight = imgHeight * scale
                    val x = margin + col * cellWidth + (cellWidth - drawWidth) / 2f
                    val y = pageHeight - margin - (row + 1) * cellHeight + (cellHeight - drawHeight) / 2f
                    contentStream.drawImage(pdImage, x, y, drawWidth, drawHeight)
                    contentStream.close()
                    imgIdx++
                }
                FileOutputStream(tempOutputFile).use { doc.save(it) }
            }
            val outName = customFilename ?: "images_compiled.pdf"
            val bytes = tempOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/pdf", "PDF")
                ?: throw Exception("Failed to save PDF.")
            registerRecentFile(savedUri, outName, "application/pdf", tempOutputFile.length())
            tempFiles.forEach { if (it.exists()) it.delete() }
            if (tempOutputFile.exists()) tempOutputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun wrapText(text: String, font: com.tom_roush.pdfbox.pdmodel.font.PDFont, fontSize: Float, maxWidth: Float): List<String> {
        if (text.isBlank()) return listOf("")
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        val currentLine = StringBuilder()
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
            val width = fontSize * font.getStringWidth(testLine) / 1000f
            if (width > maxWidth && currentLine.isNotEmpty()) {
                lines.add(currentLine.toString())
                currentLine.setLength(0)
                currentLine.append(word)
            } else {
                currentLine.setLength(0)
                currentLine.append(testLine)
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
        return lines
    }

    suspend fun addHeaderFooter(inputUri: Uri, headerText: String, footerText: String, fontSize: Int, customFilename: String? = null): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open source PDF file.")
            val tempOutputFile = File(context.cacheDir, "headerfooter_${System.currentTimeMillis()}.pdf")
            PDDocument.load(tempInputFile).use { document ->
                val font = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA
                for (i in 0 until document.numberOfPages) {
                    val page = document.getPage(i)
                    val contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(document, page, com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND, true)
                    val mediaBox = page.mediaBox
                    val pageWidth = mediaBox.width
                    if (headerText.isNotBlank()) {
                        val headerWidth = fontSize.toFloat() * font.getStringWidth(headerText) / 1000f
                        contentStream.beginText()
                        contentStream.setFont(font, fontSize.toFloat())
                        contentStream.newLineAtOffset((pageWidth - headerWidth) / 2f, mediaBox.height - 30f)
                        contentStream.showText(headerText)
                        contentStream.endText()
                    }
                    if (footerText.isNotBlank()) {
                        val footerWidth = fontSize.toFloat() * font.getStringWidth(footerText) / 1000f
                        contentStream.beginText()
                        contentStream.setFont(font, fontSize.toFloat())
                        contentStream.newLineAtOffset((pageWidth - footerWidth) / 2f, 20f)
                        contentStream.showText(footerText)
                        contentStream.endText()
                    }
                    contentStream.close()
                }
                FileOutputStream(tempOutputFile).use { document.save(it) }
            }
            val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".pdf")
            val outName = customFilename ?: "${originalName}_headerfooter.pdf"
            val bytes = tempOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/pdf", "PDF")
                ?: throw Exception("Failed to save PDF.")
            registerRecentFile(savedUri, outName, "application/pdf", tempOutputFile.length())
            if (tempInputFile.exists()) tempInputFile.delete()
            if (tempOutputFile.exists()) tempOutputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resizePdfPages(inputUri: Uri, targetSize: String, customFilename: String? = null): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open source PDF file.")
            val tempOutputFile = File(context.cacheDir, "resized_${System.currentTimeMillis()}.pdf")
            val targetRect = when (targetSize.uppercase()) {
                "A3" -> com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A3
                "A4" -> com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4
                "A5" -> com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A5
                "LETTER" -> com.tom_roush.pdfbox.pdmodel.common.PDRectangle.LETTER
                "LEGAL" -> com.tom_roush.pdfbox.pdmodel.common.PDRectangle.LEGAL
                else -> com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4
            }
            PDDocument.load(tempInputFile).use { document ->
                for (i in 0 until document.numberOfPages) {
                    val page = document.getPage(i)
                    val currentRect = page.mediaBox
                    val targetWidth = targetRect.width
                    val targetHeight = targetRect.height
                    val currentWidth = currentRect.width
                    val currentHeight = currentRect.height
                    val scaleX = targetWidth / currentWidth
                    val scaleY = targetHeight / currentHeight
                    val scale = minOf(scaleX, scaleY)
                    page.mediaBox = targetRect
                    val contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(document, page, com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.PREPEND, true)
                    contentStream.saveGraphicsState()
                    contentStream.transform(com.tom_roush.pdfbox.util.Matrix(scale, 0f, 0f, scale, (targetWidth - currentWidth * scale) / 2f, (targetHeight - currentHeight * scale) / 2f))
                    contentStream.restoreGraphicsState()
                    contentStream.close()
                }
                FileOutputStream(tempOutputFile).use { document.save(it) }
            }
            val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".pdf")
            val outName = customFilename ?: "${originalName}_${targetSize.lowercase()}.pdf"
            val bytes = tempOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/pdf", "PDF")
                ?: throw Exception("Failed to save PDF.")
            registerRecentFile(savedUri, outName, "application/pdf", tempOutputFile.length())
            if (tempInputFile.exists()) tempInputFile.delete()
            if (tempOutputFile.exists()) tempOutputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createPasswordZip(uris: List<Uri>, outputName: String, password: String): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val cachedFiles = mutableListOf<Pair<File, String>>()
            uris.forEach { uri ->
                val file = uriCacheUtils.cacheUriToFile(uri)
                val name = getFileNameFromUri(uri) ?: "file_${System.currentTimeMillis()}"
                if (file != null) cachedFiles.add(Pair(file, name))
            }
            if (cachedFiles.isEmpty()) throw Exception("No valid files to compress.")
            val tempOutputFile = File(context.cacheDir, "archive_${System.currentTimeMillis()}.zip")
            val zipOutputStream = net.lingala.zip4j.io.outputstream.ZipOutputStream(FileOutputStream(tempOutputFile), password.toCharArray())
            cachedFiles.forEach { (file, name) ->
                val parameters = net.lingala.zip4j.model.ZipParameters().apply {
                    fileNameInZip = name
                    compressionMethod = net.lingala.zip4j.model.enums.CompressionMethod.DEFLATE
                    compressionLevel = net.lingala.zip4j.model.enums.CompressionLevel.NORMAL
                    isEncryptFiles = true
                    encryptionMethod = net.lingala.zip4j.model.enums.EncryptionMethod.AES
                    aesKeyStrength = net.lingala.zip4j.model.enums.AesKeyStrength.KEY_STRENGTH_256
                }
                zipOutputStream.putNextEntry(parameters)
                file.inputStream().use { it.copyTo(zipOutputStream) }
                zipOutputStream.closeEntry()
            }
            zipOutputStream.close()
            val outName = if (outputName.endsWith(".zip", ignoreCase = true)) outputName else "$outputName.zip"
            val bytes = tempOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/zip", "Archive")
                ?: throw Exception("Failed to save ZIP archive.")
            registerRecentFile(savedUri, outName, "application/zip", tempOutputFile.length())
            cachedFiles.forEach { if (it.first.exists()) it.first.delete() }
            if (tempOutputFile.exists()) tempOutputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createGzipArchive(uris: List<Uri>, outputName: String): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            if (uris.size != 1) throw Exception("GZIP supports single file compression only. Use TGZ for multiple files.")
            val cachedFile = uriCacheUtils.cacheUriToFile(uris.first())
                ?: throw Exception("Could not open source file.")
            val fileName = getFileNameFromUri(uris.first()) ?: "file"
            val tempOutputFile = File(context.cacheDir, "archive_${System.currentTimeMillis()}.tar")
            FileOutputStream(tempOutputFile).use { fos ->
                val size = cachedFile.length()
                val header = ByteArray(512)
                val nameBytes = fileName.take(99).toByteArray(Charsets.UTF_8)
                System.arraycopy(nameBytes, 0, header, 0, nameBytes.size)
                val modeBytes = "0000644\u0000".toByteArray()
                System.arraycopy(modeBytes, 0, header, 100, modeBytes.size)
                val sizeOctal = String.format("%011o", size) + " "
                System.arraycopy(sizeOctal.toByteArray(), 0, header, 124, sizeOctal.toByteArray().size)
                val modTimeOctal = String.format("%011o", cachedFile.lastModified() / 1000L) + " "
                System.arraycopy(modTimeOctal.toByteArray(), 0, header, 136, modTimeOctal.toByteArray().size)
                header[156] = '0'.toByte()
                System.arraycopy("ustar\u0000".toByteArray(), 0, header, 257, 6)
                for (j in 148 until 156) header[j] = ' '.toByte()
                var checksum = 0
                for (b in header) checksum += (b.toInt() and 0xFF)
                val checksumOctal = String.format("%06o", checksum) + "\u0000 "
                System.arraycopy(checksumOctal.toByteArray(), 0, header, 148, checksumOctal.toByteArray().size)
                fos.write(header)
                cachedFile.inputStream().use { fis ->
                    fis.copyTo(fos)
                }
                val remainder = (size % 512).toInt()
                if (remainder > 0) fos.write(ByteArray(512 - remainder))
                fos.write(ByteArray(1024))
            }
            val gzOutputFile = File(context.cacheDir, "archive_${System.currentTimeMillis()}.tgz")
            FileInputStream(tempOutputFile).use { fis ->
                java.util.zip.GZIPOutputStream(FileOutputStream(gzOutputFile)).use { gzos ->
                    fis.copyTo(gzos)
                }
            }
            val outName = if (outputName.endsWith(".tgz", ignoreCase = true) || outputName.endsWith(".tar.gz", ignoreCase = true)) outputName else "$outputName.tgz"
            val bytes = gzOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/gzip", "Archive")
                ?: throw Exception("Failed to save TGZ archive.")
            registerRecentFile(savedUri, outName, "application/gzip", gzOutputFile.length())
            if (tempOutputFile.exists()) tempOutputFile.delete()
            if (gzOutputFile.exists()) gzOutputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }



    suspend fun editPdfMetadata(inputUri: Uri, title: String, author: String, subject: String, keywords: String, customFilename: String? = null): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open source PDF file.")
            val tempOutputFile = File(context.cacheDir, "metadata_${System.currentTimeMillis()}.pdf")
            PDDocument.load(tempInputFile).use { document ->
                val info = document.documentInformation
                if (title.isNotBlank()) info.title = title
                if (author.isNotBlank()) info.author = author
                if (subject.isNotBlank()) info.subject = subject
                if (keywords.isNotBlank()) info.keywords = keywords
                FileOutputStream(tempOutputFile).use { document.save(it) }
            }
            val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".pdf")
            val outName = customFilename ?: "${originalName}_metadata.pdf"
            val bytes = tempOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/pdf", "PDF")
                ?: throw Exception("Failed to save PDF.")
            registerRecentFile(savedUri, outName, "application/pdf", tempOutputFile.length())
            if (tempInputFile.exists()) tempInputFile.delete()
            if (tempOutputFile.exists()) tempOutputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cropPdfMargins(inputUri: Uri, topMargin: Float, bottomMargin: Float, leftMargin: Float, rightMargin: Float, customFilename: String? = null): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open source PDF file.")
            val tempOutputFile = File(context.cacheDir, "cropped_${System.currentTimeMillis()}.pdf")
            PDDocument.load(tempInputFile).use { document ->
                for (page in document.pages) {
                    val mediaBox = page.mediaBox
                    val newLowerLeftX = mediaBox.lowerLeftX + leftMargin
                    val newLowerLeftY = mediaBox.lowerLeftY + bottomMargin
                    val newUpperRightX = mediaBox.upperRightX - rightMargin
                    val newUpperRightY = mediaBox.upperRightY - topMargin
                    if (newUpperRightX > newLowerLeftX && newUpperRightY > newLowerLeftY) {
                        page.mediaBox = com.tom_roush.pdfbox.pdmodel.common.PDRectangle(
                            newLowerLeftX, newLowerLeftY, newUpperRightX - newLowerLeftX, newUpperRightY - newLowerLeftY
                        )
                    }
                }
                FileOutputStream(tempOutputFile).use { document.save(it) }
            }
            val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".pdf")
            val outName = customFilename ?: "${originalName}_cropped.pdf"
            val bytes = tempOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/pdf", "PDF")
                ?: throw Exception("Failed to save PDF.")
            registerRecentFile(savedUri, outName, "application/pdf", tempOutputFile.length())
            if (tempInputFile.exists()) tempInputFile.delete()
            if (tempOutputFile.exists()) tempOutputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun redactPdfBoxes(inputUri: Uri, redactionBoxes: List<RedactionBox>, customFilename: String? = null): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open source PDF file.")
            val tempOutputFile = File(context.cacheDir, "redacted_${System.currentTimeMillis()}.pdf")
            PDDocument.load(tempInputFile).use { document ->
                val boxesByPage = redactionBoxes.groupBy { it.pageIndex }
                for ((pageIdx, boxes) in boxesByPage) {
                    if (pageIdx in 0 until document.numberOfPages) {
                        val page = document.getPage(pageIdx)
                        val pageWidth = page.mediaBox.width
                        val pageHeight = page.mediaBox.height
                        val contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
                            document,
                            page,
                            com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND,
                            true
                        )
                        contentStream.setNonStrokingColor(0f, 0f, 0f)
                        for (box in boxes) {
                            val pdfX = box.normX * pageWidth
                            val pdfW = box.normWidth * pageWidth
                            val pdfH = box.normHeight * pageHeight
                            // PDF coordinates have (0,0) at bottom-left: flip Y from screen (top-left) to PDF (bottom-left)
                            val pdfY = pageHeight - (box.normY * pageHeight + pdfH)
                            contentStream.addRect(pdfX, pdfY, pdfW, pdfH)
                        }
                        contentStream.fill()
                        contentStream.close()
                    }
                }
                FileOutputStream(tempOutputFile).use { document.save(it) }
            }
            val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".pdf")
            val outName = customFilename ?: "${originalName}_redacted.pdf"
            val bytes = tempOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/pdf", "PDF")
                ?: throw Exception("Failed to save PDF.")
            registerRecentFile(savedUri, outName, "application/pdf", tempOutputFile.length())
            if (tempInputFile.exists()) tempInputFile.delete()
            if (tempOutputFile.exists()) tempOutputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun redactPdf(inputUri: Uri, pageNumber: Int, x: Float, y: Float, width: Float, height: Float, customFilename: String? = null): Result<Uri> = withContext(Dispatchers.IO) {
        val boxes = listOf(RedactionBox(pageNumber, x / 595f, y / 842f, width / 595f, height / 842f))
        redactPdfBoxes(inputUri, boxes, customFilename)
    }





    suspend fun comparePdfText(uri1: Uri, uri2: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val tempFile1 = uriCacheUtils.cacheUriToFile(uri1)
                ?: throw Exception("Could not open first PDF file.")
            val tempFile2 = uriCacheUtils.cacheUriToFile(uri2)
                ?: throw Exception("Could not open second PDF file.")
            val text1 = StringBuilder()
            val text2 = StringBuilder()
            PDDocument.load(tempFile1).use { doc1 ->
                val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                text1.append(stripper.getText(doc1))
            }
            PDDocument.load(tempFile2).use { doc2 ->
                val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                text2.append(stripper.getText(doc2))
            }
            val lines1 = text1.toString().lines()
            val lines2 = text2.toString().lines()
            val diff = StringBuilder()
            diff.appendLine("=== PDF Text Comparison ===")
            diff.appendLine("Document 1: ${lines1.size} lines")
            diff.appendLine("Document 2: ${lines2.size} lines")
            diff.appendLine()
            val maxLines = maxOf(lines1.size, lines2.size)
            var differences = 0
            for (i in 0 until maxLines) {
                val line1 = lines1.getOrElse(i) { "" }
                val line2 = lines2.getOrElse(i) { "" }
                if (line1 != line2) {
                    differences++
                    diff.appendLine("Line ${i + 1}:")
                    diff.appendLine("  Doc1: $line1")
                    diff.appendLine("  Doc2: $line2")
                    diff.appendLine()
                }
            }
            diff.appendLine("Total differences: $differences lines")
            if (tempFile1.exists()) tempFile1.delete()
            if (tempFile2.exists()) tempFile2.delete()
            Result.success(diff.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }





    suspend fun insertPages(inputUri: Uri, insertUri: Uri, insertAtPage: Int, customFilename: String? = null): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open source PDF file.")
            val tempInsertFile = uriCacheUtils.cacheUriToFile(insertUri)
                ?: throw Exception("Could not open insert PDF file.")
            val tempOutputFile = File(context.cacheDir, "inserted_${System.currentTimeMillis()}.pdf")
            PDDocument.load(tempInputFile as File).use { mainDoc ->
                val pagesToInsert = PDDocument.load(tempInsertFile as File).use { insertDoc ->
                    (0 until insertDoc.numberOfPages).map { insertDoc.getPage(it) }
                }
                val insertIndex = insertAtPage.coerceIn(0, mainDoc.numberOfPages)
                for ((offset, page) in pagesToInsert.withIndex()) {
                    mainDoc.importPage(page)
                    val importedPage = mainDoc.getPage(mainDoc.numberOfPages - 1)
                    mainDoc.removePage(mainDoc.numberOfPages - 1)
                    val targetIndex = insertIndex + offset
                    if (targetIndex >= mainDoc.numberOfPages) {
                        mainDoc.addPage(importedPage)
                    } else {
                        val targetPage = mainDoc.getPage(targetIndex)
                        mainDoc.pages.insertBefore(importedPage, targetPage)
                    }
                }
                FileOutputStream(tempOutputFile).use { mainDoc.save(it) }
            }
            val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".pdf")
            val outName = customFilename ?: "${originalName}_inserted.pdf"
            val bytes = tempOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/pdf", "PDF")
                ?: throw Exception("Failed to save PDF.")
            registerRecentFile(savedUri, outName, "application/pdf", tempOutputFile.length())
            if (tempInputFile.exists()) tempInputFile.delete()
            if (tempInsertFile.exists()) tempInsertFile.delete()
            if (tempOutputFile.exists()) tempOutputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun replacePages(inputUri: Uri, replaceUri: Uri, startPage: Int, customFilename: String? = null): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open source PDF file.")
            val tempReplaceFile = uriCacheUtils.cacheUriToFile(replaceUri)
                ?: throw Exception("Could not open replacement PDF file.")
            val tempOutputFile = File(context.cacheDir, "replaced_${System.currentTimeMillis()}.pdf")
            PDDocument.load(tempInputFile as File).use { mainDoc ->
                val replacePagesList = PDDocument.load(tempReplaceFile as File).use { replaceDoc ->
                    (0 until replaceDoc.numberOfPages).map { replaceDoc.getPage(it) }
                }
                val validStart = startPage.coerceIn(0, mainDoc.numberOfPages - 1)
                for (i in replacePagesList.indices) {
                    val targetIdx = validStart + i
                    if (targetIdx < mainDoc.numberOfPages) {
                        val targetPage = mainDoc.getPage(targetIdx)
                        mainDoc.importPage(replacePagesList[i])
                        val imported = mainDoc.getPage(mainDoc.numberOfPages - 1)
                        mainDoc.removePage(mainDoc.numberOfPages - 1)
                        mainDoc.pages.insertBefore(imported, targetPage)
                        mainDoc.removePage(targetPage)
                    }
                }
                FileOutputStream(tempOutputFile).use { mainDoc.save(it) }
            }
            val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".pdf")
            val outName = customFilename ?: "${originalName}_replaced.pdf"
            val bytes = tempOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/pdf", "PDF")
                ?: throw Exception("Failed to save PDF.")
            registerRecentFile(savedUri, outName, "application/pdf", tempOutputFile.length())
            if (tempInputFile.exists()) tempInputFile.delete()
            if (tempReplaceFile.exists()) tempReplaceFile.delete()
            if (tempOutputFile.exists()) tempOutputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun cacheUriToFile(uri: Uri): File? = uriCacheUtils.cacheUriToFile(uri)

    suspend fun editBookmarks(inputUri: Uri, bookmarks: List<Triple<String, Int, Int>>, customFilename: String? = null): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri) ?: throw Exception("Could not open source PDF file.")
            val tempOutputFile = File(context.cacheDir, "bookmarks_${System.currentTimeMillis()}.pdf")
            PDDocument.load(tempInputFile as File).use { document ->
                val outline = com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline()
                for ((title, pageIdx, yPos) in bookmarks) {
                    if (pageIdx in 0 until document.numberOfPages) {
                        val bookmark = com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination()
                        bookmark.page = document.getPage(pageIdx)
                        bookmark.top = yPos
                        val outlineItem = com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem()
                        outlineItem.title = title
                        outlineItem.destination = bookmark
                        outline.addLast(outlineItem)
                    }
                }
                document.documentCatalog.documentOutline = outline
                FileOutputStream(tempOutputFile).use { document.save(it) }
            }
            val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".pdf")
            val outName = customFilename ?: "${originalName}_bookmarks.pdf"
            val bytes = tempOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/pdf", "PDF") ?: throw Exception("Failed to save PDF.")
            registerRecentFile(savedUri, outName, "application/pdf", tempOutputFile.length())
            if (tempInputFile.exists()) tempInputFile.delete()
            if (tempOutputFile.exists()) tempOutputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun extractPasswordZip(inputUri: Uri, password: String): Result<List<Uri>> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri) ?: throw Exception("Could not open ZIP file.")
            val savedUris = mutableListOf<Uri>()
            val zipFile = net.lingala.zip4j.ZipFile(tempInputFile)
            zipFile.setPassword(password.toCharArray())
            val fileHeaders = zipFile.fileHeaders
            for (header in fileHeaders) {
                if (!header.isDirectory) {
                    val baseFileName = header.fileName.substringAfterLast('/')
                    val tempExtractedName = "extracted_${System.currentTimeMillis()}_${(0..9999).random()}_$baseFileName"
                    val outFile = File(context.cacheDir, tempExtractedName)
                    zipFile.extractFile(header, context.cacheDir.path, tempExtractedName)
                    if (outFile.exists()) {
                        val bytes = outFile.readBytes()
                        val ext = baseFileName.substringAfterLast('.', "").lowercase()
                        val mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
                        val savedUri = fileOutputManager.saveToDefault(bytes, baseFileName, mimeType, "Archive") ?: continue
                        savedUris.add(savedUri)
                        registerRecentFile(savedUri, baseFileName, mimeType, outFile.length())
                        outFile.delete()
                    }
                }
            }
            if (savedUris.isEmpty()) throw Exception("No files could be extracted. Please check the password.")
            if (tempInputFile.exists()) tempInputFile.delete()
            Result.success(savedUris)
        } catch (e: Exception) { Result.failure(e) }
    }







    suspend fun encryptFile(inputUri: Uri, password: String, customFilename: String? = null): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri) ?: throw Exception("Could not open source file.")
            val tempOutputFile = File(context.cacheDir, "encrypted_${System.currentTimeMillis()}.enc")
            val key = java.security.MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8))
            val secretKey = javax.crypto.spec.SecretKeySpec(key, "AES")
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(tempInputFile.readBytes())
            val outputBytes = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, outputBytes, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, outputBytes, iv.size, encryptedBytes.size)
            tempOutputFile.writeBytes(outputBytes)
            val originalName = getFileNameFromUri(inputUri) ?: "file"
            val outName = customFilename ?: "${originalName}.enc"
            val bytes = tempOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/octet-stream", "Documents") ?: throw Exception("Failed to save.")
            registerRecentFile(savedUri, outName, "application/octet-stream", tempOutputFile.length())
            if (tempInputFile.exists()) tempInputFile.delete()
            if (tempOutputFile.exists()) tempOutputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun decryptFile(inputUri: Uri, password: String, customFilename: String? = null): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri) ?: throw Exception("Could not open encrypted file.")
            val tempOutputFile = File(context.cacheDir, "decrypted_${System.currentTimeMillis()}")
            val key = java.security.MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8))
            val secretKey = javax.crypto.spec.SecretKeySpec(key, "AES")
            val inputBytes = tempInputFile.readBytes()
            val iv = inputBytes.copyOfRange(0, 12)
            val encryptedBytes = inputBytes.copyOfRange(12, inputBytes.size)
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey, javax.crypto.spec.GCMParameterSpec(128, iv))
            tempOutputFile.writeBytes(cipher.doFinal(encryptedBytes))
            val outName = customFilename ?: "decrypted_file"
            val bytes = tempOutputFile.readBytes()
            val savedUri = fileOutputManager.saveToDefault(bytes, outName, "*/*", "Documents") ?: throw Exception("Failed to save.")
            registerRecentFile(savedUri, outName, "*/*", tempOutputFile.length())
            if (tempInputFile.exists()) tempInputFile.delete()
            if (tempOutputFile.exists()) tempOutputFile.delete()
            Result.success(savedUri)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun extractPdfImagesSelective(inputUri: Uri, selectedIndices: List<Int>): Result<List<Uri>> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri) ?: throw Exception("Could not open source PDF file.")
            val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".pdf")
            val savedUris = mutableListOf<Uri>()
            PDDocument.load(tempInputFile).use { document ->
                var imageIndex = 0
                for (pageNum in 0 until document.numberOfPages) {
                    val resources = document.getPage(pageNum).resources ?: continue
                    for (name in resources.xObjectNames) {
                        if (resources.isImageXObject(name)) {
                            if (selectedIndices.contains(imageIndex)) {
                                val xObject = resources.getXObject(name)
                                if (xObject is PDImageXObject) {
                                    val image = xObject.image ?: continue
                                    val stream = java.io.ByteArrayOutputStream()
                                    if (image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)) {
                                        val bytes = stream.toByteArray()
                                        val savedUri = fileOutputManager.saveToDefault(bytes, "${originalName}_img${imageIndex + 1}.png", "image/png", "Images") ?: continue
                                        savedUris.add(savedUri)
                                        registerRecentFile(savedUri, "${originalName}_img${imageIndex + 1}.png", "image/png", bytes.size.toLong())
                                    }
                                    image.recycle()
                                }
                            }
                            imageIndex++
                        }
                    }
                }
            }
            if (savedUris.isEmpty()) throw Exception("No images selected.")
            if (tempInputFile.exists()) tempInputFile.delete()
            Result.success(savedUris)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun extractPdfImagesAllPages(inputUri: Uri): Result<List<Uri>> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri) ?: throw Exception("Could not open source PDF file.")
            val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".pdf")
            val savedUris = mutableListOf<Uri>()
            PDDocument.load(tempInputFile).use { document ->
                for (pageNum in 0 until document.numberOfPages) {
                    val page = document.getPage(pageNum)
                    val width = page.mediaBox.width.toInt().coerceIn(1, 4096)
                    val height = page.mediaBox.height.toInt().coerceIn(1, 4096)
                    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    val renderer = com.tom_roush.pdfbox.rendering.PDFRenderer(document)
                    val renderedImage = renderer.renderImage(pageNum, 2f, com.tom_roush.pdfbox.rendering.ImageType.RGB)
                    val stream = java.io.ByteArrayOutputStream()
                    renderedImage.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                    val bytes = stream.toByteArray()
                    val savedUri = fileOutputManager.saveToDefault(bytes, "${originalName}_page${pageNum + 1}.png", "image/png", "Images") ?: continue
                    savedUris.add(savedUri)
                    registerRecentFile(savedUri, "${originalName}_page${pageNum + 1}.png", "image/png", bytes.size.toLong())
                    bitmap.recycle()
                    renderedImage.recycle()
                }
            }
            if (savedUris.isEmpty()) throw Exception("No pages rendered.")
            if (tempInputFile.exists()) tempInputFile.delete()
            Result.success(savedUris)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getFileChecksum(inputUri: Uri, algorithm: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri) ?: throw Exception("Could not open source file.")
            val digest = java.security.MessageDigest.getInstance(algorithm)
            tempInputFile.inputStream().use { fis ->
                val buffer = ByteArray(8192)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) digest.update(buffer, 0, read)
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (tempInputFile.exists()) tempInputFile.delete()
            Result.success(hash)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun compareText(text1: String, text2: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val lines1 = text1.lines()
            val lines2 = text2.lines()
            val diff = StringBuilder()
            diff.appendLine("=== Text Comparison ===")
            diff.appendLine("Text 1: ${lines1.size} lines, Text 2: ${lines2.size} lines")
            diff.appendLine()
            val maxLines = maxOf(lines1.size, lines2.size)
            var differences = 0
            for (i in 0 until maxLines) {
                val line1 = lines1.getOrElse(i) { "" }
                val line2 = lines2.getOrElse(i) { "" }
                if (line1 != line2) {
                    differences++
                    diff.appendLine("Line ${i + 1}:")
                    diff.appendLine("  Text1: $line1")
                    diff.appendLine("  Text2: $line2")
                    diff.appendLine()
                }
            }
            diff.appendLine("Total differences: $differences lines")
            Result.success(diff.toString())
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun readPdfBookmarks(inputUri: Uri): Result<List<Triple<String, Int, Int>>> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri) ?: throw Exception("Could not open PDF file.")
            val bookmarks = mutableListOf<Triple<String, Int, Int>>()
            PDDocument.load(tempInputFile).use { document ->
                val outline = document.documentCatalog.documentOutline ?: return@use
                fun traverse(item: com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem, depth: Int = 0) {
                    var current = item
                    while (current != null) {
                        val title = "  ".repeat(depth) + current.title
                        val dest = current.destination
                        if (dest is com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination) {
                            val pageIdx = document.pages.indexOf(dest.page)
                            val yPos = if (dest is com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination) 700 else 0
                            if (pageIdx >= 0) bookmarks.add(Triple(title, pageIdx, yPos))
                        }
                        if (current.firstChild != null) traverse(current.firstChild, depth + 1)
                        current = current.nextSibling
                    }
                }
                if (outline.firstChild != null) traverse(outline.firstChild)
            }
            if (tempInputFile.exists()) tempInputFile.delete()
            Result.success(bookmarks)
        } catch (e: Exception) { Result.failure(e) }
    }



    fun convertPdfToWordEnhanced(inputUri: Uri, customFilename: String? = null): Result<Uri> {
        return kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri) ?: throw Exception("Could not open PDF file.")
                val tempOutputFile = java.io.File(context.cacheDir, "enhanced_word_${System.currentTimeMillis()}.docx")
                org.apache.poi.xwpf.usermodel.XWPFDocument().use { doc ->
                    PDDocument.load(tempInputFile as File).use { pdfDoc ->
                        val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                        for (pageNum in 0 until pdfDoc.numberOfPages) {
                            stripper.startPage = pageNum + 1
                            stripper.endPage = pageNum + 1
                            val pageText = stripper.getText(pdfDoc)
                            val paragraph = doc.createParagraph()
                            val run = paragraph.createRun()
                            run.fontSize = 11
                            run.fontFamily = "Calibri"
                            run.setText(pageText)
                        }
                    }
                    java.io.FileOutputStream(tempOutputFile).use { doc.write(it) }
                }
                val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".pdf")
                val outName = customFilename ?: "${originalName}_enhanced.docx"
                val bytes = tempOutputFile.readBytes()
                val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Documents")
                    ?: throw Exception("Failed to save DOCX.")
                registerRecentFile(savedUri, outName, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", tempOutputFile.length())
                if (tempInputFile.exists()) tempInputFile.delete()
                if (tempOutputFile.exists()) tempOutputFile.delete()
                Result.success(savedUri)
            } catch (e: Exception) { Result.failure(e) }
        }
    }

    fun convertMarkdownToPdfEnhanced(inputUri: Uri, customFilename: String? = null): Result<Uri> {
        return kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri) ?: throw Exception("Could not open MD file.")
                val tempOutputFile = java.io.File(context.cacheDir, "md_enhanced_${System.currentTimeMillis()}.pdf")
                val mdText = tempInputFile.readText(Charsets.UTF_8)
                PDDocument().use { doc ->
                    val font = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA
                    val fontBold = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD
                    val margin = 50f
                    val lineHeight = 14f
                    val pageWidth = com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4.width
                    var page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
                    doc.addPage(page)
                    var contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page)
                    var yOffset = page.mediaBox.height - margin
                    val lines = mdText.split("\n")
                    for (line in lines) {
                        if (yOffset < margin + lineHeight) {
                            contentStream.close()
                            page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
                            doc.addPage(page)
                            contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page)
                            yOffset = page.mediaBox.height - margin
                        }
                        val trimmed = line.trim()
                        when {
                            trimmed.startsWith("### ") -> {
                                contentStream.beginText()
                                contentStream.setFont(fontBold, 14f)
                                contentStream.newLineAtOffset(margin, yOffset)
                                contentStream.showText(trimmed.removePrefix("### "))
                                contentStream.endText()
                                yOffset -= lineHeight + 4
                            }
                            trimmed.startsWith("## ") -> {
                                contentStream.beginText()
                                contentStream.setFont(fontBold, 16f)
                                contentStream.newLineAtOffset(margin, yOffset)
                                contentStream.showText(trimmed.removePrefix("## "))
                                contentStream.endText()
                                yOffset -= lineHeight + 6
                            }
                            trimmed.startsWith("# ") -> {
                                contentStream.beginText()
                                contentStream.setFont(fontBold, 20f)
                                contentStream.newLineAtOffset(margin, yOffset)
                                contentStream.showText(trimmed.removePrefix("# "))
                                contentStream.endText()
                                yOffset -= lineHeight + 8
                            }
                            trimmed.startsWith("```") -> {
                                yOffset -= lineHeight
                            }
                            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                                contentStream.beginText()
                                contentStream.setFont(font, 11f)
                                contentStream.newLineAtOffset(margin + 15f, yOffset)
                                contentStream.showText("• ${trimmed.drop(2)}")
                                contentStream.endText()
                                yOffset -= lineHeight
                            }
                            trimmed.isNotEmpty() -> {
                                contentStream.beginText()
                                contentStream.setFont(font, 11f)
                                contentStream.newLineAtOffset(margin, yOffset)
                                contentStream.showText(trimmed)
                                contentStream.endText()
                                yOffset -= lineHeight
                            }
                            else -> { yOffset -= lineHeight / 2 }
                        }
                    }
                    contentStream.close()
                    java.io.FileOutputStream(tempOutputFile).use { doc.save(it) }
                }
                val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".md")
                val outName = customFilename ?: "${originalName}_enhanced.pdf"
                val bytes = tempOutputFile.readBytes()
                val savedUri = fileOutputManager.saveToDefault(bytes, outName, "application/pdf", "PDF")
                    ?: throw Exception("Failed to save PDF.")
                registerRecentFile(savedUri, outName, "application/pdf", tempOutputFile.length())
                if (tempInputFile.exists()) tempInputFile.delete()
                if (tempOutputFile.exists()) tempOutputFile.delete()
                Result.success(savedUri)
            } catch (e: Exception) { Result.failure(e) }
        }
    }



    fun getWordCount(text: String): Map<String, Any> {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val lines = text.lines()
        val chars = text.length
        val charsNoSpaces = text.replace("\\s".toRegex(), "").length
        val paragraphs = text.split("\n\n").filter { it.isNotBlank() }
        val readingTime = words.size / 200
        val speakingTime = words.size / 150
        return mapOf(
            "words" to words.size,
            "lines" to lines.size,
            "characters" to chars,
            "charactersNoSpaces" to charsNoSpaces,
            "paragraphs" to paragraphs.size,
            "readingTime" to readingTime,
            "speakingTime" to speakingTime
        )
    }
}
