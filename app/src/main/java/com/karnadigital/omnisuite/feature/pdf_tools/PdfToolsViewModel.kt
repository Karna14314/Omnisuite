package com.karnadigital.omnisuite.feature.pdf_tools

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
    var extractInputUri by mutableStateOf<Uri?>(null)
    var deleteInputUri by mutableStateOf<Uri?>(null)

    var docxToTxtInputUri by mutableStateOf<Uri?>(null)
    var csvToXlsxInputUri by mutableStateOf<Uri?>(null)
    var xlsxToCsvInputUri by mutableStateOf<Uri?>(null)
    var pptxToTxtInputUri by mutableStateOf<Uri?>(null)

    var tarInputUris by mutableStateOf<List<Uri>>(emptyList())
    var tarExtractUri by mutableStateOf<Uri?>(null)

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
            val result = pdfToolsRepository.compressPdf(inputUri, compressQuality)
            result.onSuccess { uri ->
                successUri = uri
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

    fun rotatePdfPages(rotations: Map<Int, Int>) {
        val inputUri = rotateInputUri ?: run {
            errorMessage = "Please select a PDF document first."
            return
        }
        isProcessing = true
        resetStatus()
        viewModelScope.launch {
            val result = pdfToolsRepository.rotatePdfPages(inputUri, rotations)
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
}
