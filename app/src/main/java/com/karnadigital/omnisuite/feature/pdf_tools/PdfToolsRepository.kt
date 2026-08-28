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
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

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

    suspend fun saveBytesAndRegister(bytes: ByteArray, fileName: String, mimeType: String, subfolder: String): Uri {
        val savedUri = fileOutputManager.saveToDefault(bytes, fileName, mimeType, subfolder)
            ?: throw Exception("Failed to save file.")
        registerRecentFile(savedUri, fileName, mimeType, bytes.size.toLong())
        return savedUri
    }

    private suspend fun registerRecentFile(savedUri: Uri, fileName: String, mimeType: String, fileSize: Long) {
        recentFileRepository.insertRecentFile(
            RecentFile(
                fileUri = savedUri.toString(),
                fileName = fileName,
                mimeType = mimeType,
                fileSize = fileSize,
                lastOpened = System.currentTimeMillis()
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
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open Word document.")
            val tempOutputFile = File(context.cacheDir, "docx_converted_${System.currentTimeMillis()}.pdf")
            officeConverter.convertDocxToPdf(tempInputFile, tempOutputFile)
            val originalName = (getFileNameFromUri(inputUri) ?: "document").removeSuffix(".docx").removeSuffix(".doc")
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
            val batchUriString = savedUris.joinToString("|") { it.toString() }
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

    suspend fun convertPdfToXlsx(uri: Uri): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val outputUri = reverseOfficeConverter.convertPdfToXlsx(uri)
                ?: throw Exception("Failed to convert PDF to XLSX.")
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

    suspend fun rotatePdfPages(inputUri: Uri, rotations: Map<Int, Int>): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val tempInputFile = uriCacheUtils.cacheUriToFile(inputUri)
                ?: throw Exception("Could not open source PDF file.")
            val tempOutputFile = File(context.cacheDir, "rotated_${System.currentTimeMillis()}.pdf")
            PDDocument.load(tempInputFile).use { document ->
                val pages = document.pages
                rotations.forEach { (pageIdx, angle) ->
                    if (pageIdx in 0 until pages.count) {
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
            java.io.FileInputStream(tempInputFile).use { fis ->
                org.apache.poi.xwpf.usermodel.XWPFDocument(fis).use { document ->
                    for (para in document.paragraphs) textBuilder.append(para.text).append("\n")
                    for (table in document.tables) {
                        for (row in table.rows) {
                            textBuilder.append(row.tableCells.joinToString("\t") { it.text }).append("\n")
                        }
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
}
