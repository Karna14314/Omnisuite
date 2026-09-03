package com.karnadigital.omnisuite.feature.pdf_tools

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.core.repository.RecentFileRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PdfToolsViewModel @Inject constructor(
    private val recentFileRepository: RecentFileRepository,
    @ApplicationContext private val context: Context,
    private val pdfToolsRepository: PdfToolsRepository
) : ViewModel() {

    init {
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
    var pptRenderMode by mutableStateOf("image")

    var docInputUri by mutableStateOf<Uri?>(null)

    var scanInputUri by mutableStateOf<Uri?>(null)

    var pdfToImagesInputUri by mutableStateOf<Uri?>(null)

    var compressInputUri by mutableStateOf<Uri?>(null)
    var compressQuality by mutableStateOf(0.5f)
    var compressMode by mutableStateOf("HYBRID")
    var targetSizeKbText by mutableStateOf("")
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

    // New state variables
    var decryptInputUri by mutableStateOf<Uri?>(null)
    var decryptPassword by mutableStateOf("")

    var rotateInputUri by mutableStateOf<Uri?>(null)
    var rotateDegrees by mutableIntStateOf(90)
    var extractInputUri by mutableStateOf<Uri?>(null)
    var extractPageRange by mutableStateOf("")
    var deleteInputUri by mutableStateOf<Uri?>(null)
    var deletePageRange by mutableStateOf("")

    var docxToTxtInputUri by mutableStateOf<Uri?>(null)
    var csvToXlsxInputUri by mutableStateOf<Uri?>(null)
    var xlsxToCsvInputUri by mutableStateOf<Uri?>(null)
    var pptxToTxtInputUri by mutableStateOf<Uri?>(null)

    var tarInputUris by mutableStateOf<List<Uri>>(emptyList())
    var tarExtractUri by mutableStateOf<Uri?>(null)

    var pageNumberInputUri by mutableStateOf<Uri?>(null)
    var pageNumberStart by mutableStateOf("1")
    var pageNumberPosition by mutableStateOf("bottom-center")
    var pageNumberFontSize by mutableStateOf(12)

    var reorderInputUri by mutableStateOf<Uri?>(null)
    var reorderPageOrder by mutableStateOf<List<Int>>(emptyList())

    var extractImagesInputUri by mutableStateOf<Uri?>(null)

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

    fun mergePdfs(customFilename: String? = null) {
        if (selectedMergeUris.size < 2) {
            errorMessage = "Please select at least 2 PDF documents to merge."
            return
        }
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.mergePdfs(selectedMergeUris, customFilename)
            result.onSuccess { uri ->
                successUri = uri
                successName = customFilename ?: "merged_${System.currentTimeMillis()}.pdf"
                successMessage = "PDF documents merged successfully!"
                selectedMergeUris = emptyList()
            }.onFailure { e ->
                errorMessage = "Error during merge: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun splitPdf() {
        val inputUri = splitInputUri ?: run {
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
            val result = pdfToolsRepository.splitPdf(inputUri, splitRanges)
            result.onSuccess { uri ->
                successUri = uri
                successMessage = "PDF split operation completed successfully!"
                splitInputUri = null
            }.onFailure { e ->
                errorMessage = "Error during split: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun encryptPdf(customFilename: String? = null) {
        val inputUri = lockInputUri ?: run {
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
            val result = pdfToolsRepository.encryptPdf(inputUri, lockPassword, customFilename)
            result.onSuccess { uri ->
                successUri = uri
                successName = customFilename ?: "secured_${System.currentTimeMillis()}.pdf"
                successMessage = "Password Lock applied successfully!"
                lockInputUri = null
                lockPassword = ""
            }.onFailure { e ->
                errorMessage = "Error applying encryption: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun saveToCustomLocation(targetUri: Uri) {
        val bytes = lastOutputBytes ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(targetUri)?.use { it.write(bytes) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun convertDocToPdf() {
        val inputUri = docInputUri ?: run {
            errorMessage = "Please select a Word document first."
            return
        }
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.convertDocToPdf(inputUri)
            result.onSuccess { uri ->
                successUri = uri
                successMessage = "Word document converted to PDF successfully!"
                docInputUri = null
            }.onFailure { e ->
                errorMessage = "Error during Word to PDF conversion: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun convertPptToPdf() {
        val inputUri = pptInputUri ?: run {
            errorMessage = "Please select a PowerPoint document first."
            return
        }
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.convertPptToPdf(inputUri, pptRenderMode)
            result.onSuccess { uri ->
                successUri = uri
                successMessage = "PowerPoint converted to PDF successfully!"
                pptInputUri = null
            }.onFailure { e ->
                errorMessage = "Error during PowerPoint to PDF conversion: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun saveScannedPdf(scannedFile: java.io.File) {
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.saveScannedPdf(scannedFile)
            result.onSuccess { uri ->
                successUri = uri
                successMessage = "Scanned document saved and indexed successfully!"
            }.onFailure { e ->
                errorMessage = "Failed to save scan: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun convertPdfToImages() {
        val inputUri = pdfToImagesInputUri ?: run {
            errorMessage = "Please select a PDF document first."
            return
        }
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.convertPdfToImages(inputUri)
            result.onSuccess { uris ->
                successUris = uris
                successUri = uris.firstOrNull()
                successMessage = "PDF pages successfully converted to images under Documents/OmniSuite/Images/!"
                pdfToImagesInputUri = null
            }.onFailure { e ->
                errorMessage = "Error during PDF to Image extraction: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun convertPdfToDocx() {
        val uri = pdfToWordInputUri ?: return
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.convertPdfToDocx(uri)
            result.onSuccess { outputUri ->
                successUri = outputUri
                successMessage = "PDF converted to DOCX successfully!"
                pdfToWordInputUri = null
            }.onFailure { e ->
                errorMessage = "Error: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun convertPdfToPptx() {
        val uri = pdfToPptInputUri ?: return
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.convertPdfToPptx(uri)
            result.onSuccess { outputUri ->
                successUri = outputUri
                successMessage = "PDF converted to PPTX successfully!"
                pdfToPptInputUri = null
            }.onFailure { e ->
                errorMessage = "Error: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun convertPdfToXlsx() {
        val uri = pdfToExcelInputUri ?: return
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.convertPdfToXlsx(uri)
            result.onSuccess { outputUri ->
                successUri = outputUri
                successMessage = "PDF converted to XLSX successfully!"
                pdfToExcelInputUri = null
            }.onFailure { e ->
                errorMessage = "Error: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun fillPdfForm(formData: Map<String, String>) {
        val uri = pdfFormInputUri ?: return
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.fillPdfForm(uri, formData)
            result.onSuccess { outputUri ->
                successUri = outputUri
                successMessage = "Interactive PDF Form filled successfully!"
                pdfFormInputUri = null
            }.onFailure { e ->
                errorMessage = "Error: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun compressPdf() {
        val inputUri = compressInputUri ?: run {
            errorMessage = "Please select a source PDF document first."
            return
        }
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val targetBytes = if (compressMode == "TARGET_SIZE") {
                targetSizeKbText.toDoubleOrNull()?.let { (it * 1024L).toLong().takeIf { b -> b > 0 } }
            } else null

            val result = pdfToolsRepository.compressPdf(inputUri, compressQuality, targetBytes)
            result.onSuccess { uri ->
                successUri = uri
                successName = (inputUri.lastPathSegment ?: "document").removeSuffix(".pdf") + "_compressed.pdf"
                successMessage = "PDF compressed successfully!"
                compressInputUri = null
            }.onFailure { e ->
                errorMessage = "Error compressing PDF: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun flattenPdf() {
        val inputUri = flattenInputUri ?: run {
            errorMessage = "Please select a source PDF document first."
            return
        }
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.flattenPdf(inputUri)
            result.onSuccess { uri ->
                successUri = uri
                successMessage = "PDF form fields flattened successfully!"
                flattenInputUri = null
            }.onFailure { e ->
                errorMessage = "Error flattening PDF: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun convertXlsToPdf() {
        val inputUri = xlsInputUri ?: run {
            errorMessage = "Please select an Excel sheet first."
            return
        }
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.convertXlsToPdf(inputUri)
            result.onSuccess { uri ->
                successUri = uri
                successMessage = "Excel sheet converted to PDF successfully!"
                xlsInputUri = null
            }.onFailure { e ->
                errorMessage = "Error during Excel to PDF conversion: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun decryptPdf() {
        val inputUri = decryptInputUri ?: run {
            errorMessage = "Please select a locked PDF document."
            return
        }
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.decryptPdf(inputUri, decryptPassword)
            result.onSuccess { uri ->
                successUri = uri
                successMessage = "Password security removed successfully!"
                decryptInputUri = null
                decryptPassword = ""
            }.onFailure { e ->
                errorMessage = "Failed to decrypt: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    var rotateTargetMode by mutableStateOf("ALL")
    var rotatePageRange by mutableStateOf("")

    fun rotatePdfPages(rotations: Map<Int, Int>? = null) {
        val inputUri = rotateInputUri ?: run {
            errorMessage = "Please select a PDF document first."
            return
        }
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = if (rotations != null && rotations.isNotEmpty()) {
                pdfToolsRepository.rotatePdfPages(inputUri, rotations = rotations)
            } else {
                pdfToolsRepository.rotatePdfPages(
                    inputUri = inputUri,
                    defaultDegrees = rotateDegrees,
                    targetMode = rotateTargetMode,
                    customRange = rotatePageRange
                )
            }
            result.onSuccess { uri ->
                successUri = uri
                successMessage = "Selected PDF pages rotated successfully!"
                rotateInputUri = null
            }.onFailure { e ->
                errorMessage = "Failed to rotate pages: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun extractPdfPages(selectedPages: Set<Int>) {
        val inputUri = extractInputUri ?: run {
            errorMessage = "Please select a PDF document first."
            return
        }
        if (selectedPages.isEmpty()) {
            errorMessage = "Please select at least one page to extract."
            return
        }
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.extractPdfPages(inputUri, selectedPages)
            result.onSuccess { uri ->
                successUri = uri
                successMessage = "Selected pages extracted successfully!"
                extractInputUri = null
            }.onFailure { e ->
                errorMessage = "Failed to extract pages: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun deletePdfPages(selectedPages: Set<Int>) {
        val inputUri = deleteInputUri ?: run {
            errorMessage = "Please select a PDF document first."
            return
        }
        if (selectedPages.isEmpty()) {
            errorMessage = "Please select at least one page to delete."
            return
        }
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.deletePdfPages(inputUri, selectedPages)
            result.onSuccess { uri ->
                successUri = uri
                successMessage = "Selected pages deleted successfully!"
                deleteInputUri = null
            }.onFailure { e ->
                errorMessage = "Failed to delete pages: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun convertDocxToTxt() {
        val inputUri = docxToTxtInputUri ?: run {
            errorMessage = "Please select a Word document first."
            return
        }
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.convertDocxToTxt(inputUri)
            result.onSuccess { uri ->
                successUri = uri
                successMessage = "Word document converted to TXT successfully!"
                docxToTxtInputUri = null
            }.onFailure { e ->
                errorMessage = "Failed to convert to TXT: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun convertCsvToXlsx() {
        val inputUri = csvToXlsxInputUri ?: run {
            errorMessage = "Please select a CSV file first."
            return
        }
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.convertCsvToXlsx(inputUri)
            result.onSuccess { uri ->
                successUri = uri
                successMessage = "CSV file converted to Excel successfully!"
                csvToXlsxInputUri = null
            }.onFailure { e ->
                errorMessage = "Failed to convert CSV: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun convertXlsxToCsv() {
        val inputUri = xlsxToCsvInputUri ?: run {
            errorMessage = "Please select an Excel document first."
            return
        }
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.convertXlsxToCsv(inputUri)
            result.onSuccess { uri ->
                successUri = uri
                successMessage = "Excel workbook converted to CSV successfully!"
                xlsxToCsvInputUri = null
            }.onFailure { e ->
                errorMessage = "Failed to convert: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun convertPptxToTxt() {
        val inputUri = pptxToTxtInputUri ?: run {
            errorMessage = "Please select a PowerPoint presentation first."
            return
        }
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.convertPptxToTxt(inputUri)
            result.onSuccess { uri ->
                successUri = uri
                successMessage = "PowerPoint slides converted to TXT successfully!"
                pptxToTxtInputUri = null
            }.onFailure { e ->
                errorMessage = "Failed to convert PPTX: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun convertMarkdownToPdf(markdownText: String, filename: String) {
        if (markdownText.isBlank()) {
            errorMessage = "Markdown text is empty."
            return
        }
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.convertMarkdownToPdf(markdownText, filename)
            result.onSuccess { uri ->
                successUri = uri
                successMessage = "Markdown compiled to PDF successfully!"
            }.onFailure { e ->
                errorMessage = "Failed to compile Markdown: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun packTarArchive(outputName: String) {
        if (tarInputUris.isEmpty()) {
            errorMessage = "Please select files to pack into TAR archive."
            return
        }
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.packTarArchive(tarInputUris, outputName)
            result.onSuccess { uri ->
                successUri = uri
                successMessage = "Files packed to TAR archive successfully!"
                tarInputUris = emptyList()
            }.onFailure { e ->
                errorMessage = "Failed to create TAR: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun unpackTarArchive() {
        val inputUri = tarExtractUri ?: run {
            errorMessage = "Please select a TAR file to unpack."
            return
        }
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.unpackTarArchive(inputUri)
            result.onSuccess { uri ->
                successUri = uri
                successMessage = "TAR archive unpacked successfully! Files saved to Documents/OmniSuite/Archive/"
                tarExtractUri = null
            }.onFailure { e ->
                errorMessage = "Failed to unpack TAR: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun printWebViewToPdf(htmlContent: String?, webUrl: String?, filename: String) {
        isProcessing = true
        resetStatus()
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val webView = android.webkit.WebView(context)
                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                val tempOutputFile = java.io.File(context.cacheDir, "webview_printed_${System.currentTimeMillis()}.pdf")
                val onPageLoaded = {
                    val printAdapter = webView.createPrintDocumentAdapter("Print")
                    val printAttributes = PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build()
                    val pfd = ParcelFileDescriptor.open(tempOutputFile, ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE)
                    android.print.PrintAdapterHelper.print(printAdapter, printAttributes, pfd) { success, error ->
                        try { pfd.close() } catch (e: Exception) {}
                        if (success) {
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    val bytes = tempOutputFile.readBytes()
                                    val savedUri = pdfToolsRepository.saveBytesAndRegister(bytes, filename, "application/pdf", "PDF")
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
                        webView.postDelayed({ onPageLoaded() }, 500)
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

    fun addPageNumbers(customFilename: String? = null) {
        val inputUri = pageNumberInputUri ?: run {
            errorMessage = "Please select a PDF document first."
            return
        }
        val startNum = pageNumberStart.toIntOrNull() ?: 1
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.addPageNumbers(inputUri, startNum, pageNumberPosition, pageNumberFontSize, customFilename)
            result.onSuccess { uri ->
                successUri = uri
                successName = customFilename ?: "numbered_${System.currentTimeMillis()}.pdf"
                successMessage = "Page numbers added successfully!"
                pageNumberInputUri = null
            }.onFailure { e ->
                errorMessage = "Failed to add page numbers: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun reorderPdfPages() {
        val inputUri = reorderInputUri ?: run {
            errorMessage = "Please select a PDF document first."
            return
        }
        if (reorderPageOrder.isEmpty()) {
            errorMessage = "Please specify the new page order."
            return
        }
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.reorderPdfPages(inputUri, reorderPageOrder)
            result.onSuccess { uri ->
                successUri = uri
                successMessage = "Pages reordered successfully!"
                reorderInputUri = null
                reorderPageOrder = emptyList()
            }.onFailure { e ->
                errorMessage = "Failed to reorder pages: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun extractImagesFromPdf() {
        val inputUri = extractImagesInputUri ?: run {
            errorMessage = "Please select a PDF document first."
            return
        }
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.extractImagesFromPdf(inputUri)
            result.onSuccess { uris ->
                successUris = uris
                successUri = uris.firstOrNull()
                successMessage = "Extracted ${uris.size} images from PDF!"
                extractImagesInputUri = null
            }.onFailure { e ->
                errorMessage = "Failed to extract images: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    var txtToPdfInputUri by mutableStateOf<Uri?>(null)
    var csvToPdfInputUri by mutableStateOf<Uri?>(null)
    var pdfToTxtInputUri by mutableStateOf<Uri?>(null)
    var imagesToPdfInputUris by mutableStateOf<List<Uri>>(emptyList())
    var imagesToPdfLayout by mutableIntStateOf(1)

    fun convertTxtToPdf(customFilename: String? = null) {
        val inputUri = txtToPdfInputUri ?: run {
            errorMessage = "Please select a text file first."
            return
        }
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.convertTxtToPdf(inputUri, 12, customFilename)
            result.onSuccess { uri ->
                successUri = uri
                successMessage = "Text file converted to PDF successfully!"
                txtToPdfInputUri = null
            }.onFailure { e ->
                errorMessage = "Failed to convert: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun convertCsvToPdf(customFilename: String? = null) {
        val inputUri = csvToPdfInputUri ?: run {
            errorMessage = "Please select a CSV file first."
            return
        }
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.convertCsvToPdf(inputUri, 10, customFilename)
            result.onSuccess { uri ->
                successUri = uri
                successMessage = "CSV file converted to PDF successfully!"
                csvToPdfInputUri = null
            }.onFailure { e ->
                errorMessage = "Failed to convert: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun convertPdfToTxt(customFilename: String? = null) {
        val inputUri = pdfToTxtInputUri ?: run {
            errorMessage = "Please select a PDF file first."
            return
        }
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.convertPdfToTxt(inputUri, customFilename)
            result.onSuccess { uri ->
                successUri = uri
                successMessage = "PDF converted to text successfully!"
                pdfToTxtInputUri = null
            }.onFailure { e ->
                errorMessage = "Failed to convert: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun convertImagesToPdfWithLayout(customFilename: String? = null) {
        if (imagesToPdfInputUris.isEmpty()) {
            errorMessage = "Please select at least one image."
            return
        }
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.convertImagesToPdfWithLayout(imagesToPdfInputUris, imagesToPdfLayout, customFilename)
            result.onSuccess { uri ->
                successUri = uri
                successMessage = "Images compiled to PDF successfully!"
                imagesToPdfInputUris = emptyList()
            }.onFailure { e ->
                errorMessage = "Failed to compile images: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    var headerFooterInputUri by mutableStateOf<Uri?>(null)
    var headerFooterHeaderText by mutableStateOf("")
    var headerFooterFooterText by mutableStateOf("")
    var headerFooterFontSize by mutableIntStateOf(10)

    var resizeInputUri by mutableStateOf<Uri?>(null)
    var resizeTargetSize by mutableStateOf("A4")

    fun addHeaderFooter(customFilename: String? = null) {
        val inputUri = headerFooterInputUri ?: run {
            errorMessage = "Please select a PDF document first."
            return
        }
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.addHeaderFooter(inputUri, headerFooterHeaderText, headerFooterFooterText, headerFooterFontSize, customFilename)
            result.onSuccess { uri ->
                successUri = uri
                successMessage = "Header/Footer added successfully!"
                headerFooterInputUri = null
                headerFooterHeaderText = ""
                headerFooterFooterText = ""
            }.onFailure { e ->
                errorMessage = "Failed to add header/footer: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun resizePdfPages(customFilename: String? = null) {
        val inputUri = resizeInputUri ?: run {
            errorMessage = "Please select a PDF document first."
            return
        }
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.resizePdfPages(inputUri, resizeTargetSize, customFilename)
            result.onSuccess { uri ->
                successUri = uri
                successMessage = "Pages resized to $resizeTargetSize successfully!"
                resizeInputUri = null
            }.onFailure { e ->
                errorMessage = "Failed to resize pages: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    var passwordZipInputUris by mutableStateOf<List<Uri>>(emptyList())
    var passwordZipOutputName by mutableStateOf("")
    var passwordZipPassword by mutableStateOf("")

    var passwordZipExtractUri by mutableStateOf<Uri?>(null)
    var passwordZipExtractPassword by mutableStateOf("")

    var tgzInputUris by mutableStateOf<List<Uri>>(emptyList())
    var tgzOutputName by mutableStateOf("")

    fun createPasswordZip() {
        if (passwordZipInputUris.isEmpty()) {
            errorMessage = "Please select files to compress."
            return
        }
        if (passwordZipPassword.isBlank()) {
            errorMessage = "Password cannot be empty."
            return
        }
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.createPasswordZip(passwordZipInputUris, passwordZipOutputName, passwordZipPassword)
            result.onSuccess { uri ->
                successUri = uri
                successMessage = "Password-protected ZIP created successfully!"
                passwordZipInputUris = emptyList()
                passwordZipOutputName = ""
                passwordZipPassword = ""
            }.onFailure { e ->
                errorMessage = "Failed to create ZIP: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun extractPasswordZip() {
        val inputUri = passwordZipExtractUri ?: run {
            errorMessage = "Please select a ZIP file."
            return
        }
        if (passwordZipExtractPassword.isBlank()) {
            errorMessage = "Password cannot be empty."
            return
        }
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.extractPasswordZip(inputUri, passwordZipExtractPassword)
            result.onSuccess { uris ->
                successUris = uris
                successUri = uris.firstOrNull()
                successMessage = "Extracted ${uris.size} files successfully!"
                passwordZipExtractUri = null
                passwordZipExtractPassword = ""
            }.onFailure { e ->
                errorMessage = "Failed to extract ZIP: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun createTgzArchive() {
        if (tgzInputUris.isEmpty()) {
            errorMessage = "Please select a file to compress."
            return
        }
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.createGzipArchive(tgzInputUris, tgzOutputName)
            result.onSuccess { uri ->
                successUri = uri
                successMessage = "TGZ archive created successfully!"
                tgzInputUris = emptyList()
                tgzOutputName = ""
            }.onFailure { e ->
                errorMessage = "Failed to create TGZ: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    var metadataInputUri by mutableStateOf<Uri?>(null)
    var metadataTitle by mutableStateOf("")
    var metadataAuthor by mutableStateOf("")
    var metadataSubject by mutableStateOf("")
    var metadataKeywords by mutableStateOf("")
    var cropInputUri by mutableStateOf<Uri?>(null)
    var cropTop by mutableFloatStateOf(20f)
    var cropBottom by mutableFloatStateOf(20f)
    var cropLeft by mutableFloatStateOf(20f)
    var cropRight by mutableFloatStateOf(20f)
    var redactInputUri by mutableStateOf<Uri?>(null)
    var redactPage by mutableIntStateOf(0)
    var redactX by mutableFloatStateOf(50f)
    var redactY by mutableFloatStateOf(50f)
    var redactWidth by mutableFloatStateOf(100f)
    var redactHeight by mutableFloatStateOf(20f)
    var compareUri1 by mutableStateOf<Uri?>(null)
    var compareUri2 by mutableStateOf<Uri?>(null)
    var compareResult by mutableStateOf<String?>(null)
    var insertMainUri by mutableStateOf<Uri?>(null)
    var insertInsertUri by mutableStateOf<Uri?>(null)
    var insertAtPage by mutableIntStateOf(0)
    var replaceMainUri by mutableStateOf<Uri?>(null)
    var replaceReplaceUri by mutableStateOf<Uri?>(null)
    var replaceStartPage by mutableIntStateOf(0)

    fun editMetadata(customFilename: String? = null) {
        val inputUri = metadataInputUri ?: run { errorMessage = "Please select a PDF file."; return }
        isProcessing = true; resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.editPdfMetadata(inputUri, metadataTitle, metadataAuthor, metadataSubject, metadataKeywords, customFilename)
            result.onSuccess { uri -> successUri = uri; successMessage = "Metadata updated successfully!"; metadataInputUri = null }
                .onFailure { e -> errorMessage = "Failed: ${e.localizedMessage}" }
            isProcessing = false
        }
    }

    fun cropPdfMargins(customFilename: String? = null) {
        val inputUri = cropInputUri ?: run { errorMessage = "Please select a PDF file."; return }
        isProcessing = true; resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.cropPdfMargins(inputUri, cropTop, cropBottom, cropLeft, cropRight, customFilename)
            result.onSuccess { uri -> successUri = uri; successMessage = "Margins cropped successfully!"; cropInputUri = null }
                .onFailure { e -> errorMessage = "Failed: ${e.localizedMessage}" }
            isProcessing = false
        }
    }

    fun redactPdf(customFilename: String? = null) {
        val inputUri = redactInputUri ?: run { errorMessage = "Please select a PDF file."; return }
        isProcessing = true; resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.redactPdf(inputUri, redactPage, redactX, redactY, redactWidth, redactHeight, customFilename)
            result.onSuccess { uri -> successUri = uri; successMessage = "Content redacted successfully!"; redactInputUri = null }
                .onFailure { e -> errorMessage = "Failed: ${e.localizedMessage}" }
            isProcessing = false
        }
    }

    fun comparePdf() {
        val uri1 = compareUri1 ?: run { errorMessage = "Please select first PDF."; return }
        val uri2 = compareUri2 ?: run { errorMessage = "Please select second PDF."; return }
        isProcessing = true; resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.comparePdfText(uri1, uri2)
            result.onSuccess { diff -> compareResult = diff; successMessage = "Comparison complete!" }
                .onFailure { e -> errorMessage = "Failed: ${e.localizedMessage}" }
            isProcessing = false
        }
    }

    fun insertPages() {
        val mainUri = insertMainUri ?: run { errorMessage = "Please select main PDF."; return }
        val insertUri = insertInsertUri ?: run { errorMessage = "Please select PDF to insert."; return }
        isProcessing = true; resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.insertPages(mainUri, insertUri, insertAtPage)
            result.onSuccess { uri -> successUri = uri; successMessage = "Pages inserted successfully!"; insertMainUri = null; insertInsertUri = null }
                .onFailure { e -> errorMessage = "Failed: ${e.localizedMessage}" }
            isProcessing = false
        }
    }

    fun replacePages() {
        val mainUri = replaceMainUri ?: run { errorMessage = "Please select main PDF."; return }
        val replaceUri = replaceReplaceUri ?: run { errorMessage = "Please select replacement PDF."; return }
        isProcessing = true; resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.replacePages(mainUri, replaceUri, replaceStartPage)
            result.onSuccess { uri -> successUri = uri; successMessage = "Pages replaced successfully!"; replaceMainUri = null; replaceReplaceUri = null }
                .onFailure { e -> errorMessage = "Failed: ${e.localizedMessage}" }
            isProcessing = false
        }
    }

    var bookmarkInputUri by mutableStateOf<Uri?>(null)
    var bookmarkTitles by mutableStateOf("")
    var bookmarkPages by mutableStateOf("")
    var splitByBookmarksInputUri by mutableStateOf<Uri?>(null)
    var underlayBaseUri by mutableStateOf<Uri?>(null)
    var underlayUnderlayUri by mutableStateOf<Uri?>(null)
    var underlayPage by mutableIntStateOf(0)
    var formCreationInputUri by mutableStateOf<Uri?>(null)
    var fileEncryptInputUri by mutableStateOf<Uri?>(null)
    var fileEncryptPassword by mutableStateOf("")
    var fileDecryptInputUri by mutableStateOf<Uri?>(null)
    var fileDecryptPassword by mutableStateOf("")
    var selectiveImageInputUri by mutableStateOf<Uri?>(null)
    var selectiveImageIndices by mutableStateOf("")
    var allPagesImageInputUri by mutableStateOf<Uri?>(null)
    var checksumInputUri by mutableStateOf<Uri?>(null)
    var checksumAlgorithm by mutableStateOf("SHA-256")
    var checksumResult by mutableStateOf<String?>(null)
    var textCompare1 by mutableStateOf("")
    var textCompare2 by mutableStateOf("")
    var textCompareResult by mutableStateOf<String?>(null)

    fun editBookmarks(customFilename: String? = null) {
        val inputUri = bookmarkInputUri ?: run { errorMessage = "Please select a PDF file."; return }
        val titles = bookmarkTitles.lines().filter { it.isNotBlank() }
        val pages = bookmarkPages.lines().filter { it.isNotBlank() }.mapNotNull { it.trim().toIntOrNull()?.minus(1) }
        if (titles.isEmpty() || pages.isEmpty() || titles.size != pages.size) { errorMessage = "Titles and pages must match."; return }
        val bookmarks = titles.mapIndexed { i, title -> Triple(title.trim(), pages.getOrElse(i) { 0 }, 700) }
        isProcessing = true; resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.editBookmarks(inputUri, bookmarks, customFilename)
            result.onSuccess { uri -> successUri = uri; successMessage = "Bookmarks added!"; bookmarkInputUri = null }
                .onFailure { e -> errorMessage = "Failed: ${e.localizedMessage}" }
            isProcessing = false
        }
    }

    fun encryptFile(customFilename: String? = null) {
        val inputUri = fileEncryptInputUri ?: run { errorMessage = "Please select a file."; return }
        if (fileEncryptPassword.isBlank()) { errorMessage = "Password required."; return }
        isProcessing = true; resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.encryptFile(inputUri, fileEncryptPassword, customFilename)
            result.onSuccess { uri -> successUri = uri; successMessage = "File encrypted!"; fileEncryptInputUri = null; fileEncryptPassword = "" }
                .onFailure { e -> errorMessage = "Failed: ${e.localizedMessage}" }
            isProcessing = false
        }
    }

    fun decryptFile(customFilename: String? = null) {
        val inputUri = fileDecryptInputUri ?: run { errorMessage = "Please select an encrypted file."; return }
        if (fileDecryptPassword.isBlank()) { errorMessage = "Password required."; return }
        isProcessing = true; resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.decryptFile(inputUri, fileDecryptPassword, customFilename)
            result.onSuccess { uri -> successUri = uri; successMessage = "File decrypted!"; fileDecryptInputUri = null; fileDecryptPassword = "" }
                .onFailure { e -> errorMessage = "Failed: ${e.localizedMessage}" }
            isProcessing = false
        }
    }

    fun extractSelectiveImages() {
        val inputUri = selectiveImageInputUri ?: run { errorMessage = "Please select a PDF file."; return }
        val indices = selectiveImageIndices.lines().filter { it.isNotBlank() }.mapNotNull { it.trim().toIntOrNull()?.minus(1) }
        if (indices.isEmpty()) { errorMessage = "Enter image numbers (1-indexed)."; return }
        isProcessing = true; resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.extractPdfImagesSelective(inputUri, indices)
            result.onSuccess { uris -> successUris = uris; successUri = uris.firstOrNull(); successMessage = "Extracted ${uris.size} images!"; selectiveImageInputUri = null }
                .onFailure { e -> errorMessage = "Failed: ${e.localizedMessage}" }
            isProcessing = false
        }
    }

    fun extractAllPagesAsImages() {
        val inputUri = allPagesImageInputUri ?: run { errorMessage = "Please select a PDF file."; return }
        isProcessing = true; resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.extractPdfImagesAllPages(inputUri)
            result.onSuccess { uris -> successUris = uris; successUri = uris.firstOrNull(); successMessage = "Rendered ${uris.size} pages!"; allPagesImageInputUri = null }
                .onFailure { e -> errorMessage = "Failed: ${e.localizedMessage}" }
            isProcessing = false
        }
    }

    fun getFileChecksum() {
        val inputUri = checksumInputUri ?: run { errorMessage = "Please select a file."; return }
        isProcessing = true; resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.getFileChecksum(inputUri, checksumAlgorithm)
            result.onSuccess { hash -> checksumResult = "$checksumAlgorithm: $hash"; successMessage = "Checksum calculated!" }
                .onFailure { e -> errorMessage = "Failed: ${e.localizedMessage}" }
            isProcessing = false
        }
    }

    fun compareText() {
        if (textCompare1.isBlank() && textCompare2.isBlank()) { errorMessage = "Enter text to compare."; return }
        isProcessing = true; resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.compareText(textCompare1, textCompare2)
            result.onSuccess { diff -> textCompareResult = diff; successMessage = "Comparison complete!" }
                .onFailure { e -> errorMessage = "Failed: ${e.localizedMessage}" }
            isProcessing = false
        }
    }

    fun lockPdf(customFilename: String? = null) = encryptPdf(customFilename)

    fun getWordCount(text: String): Map<String, Any> = pdfToolsRepository.getWordCount(text)
}