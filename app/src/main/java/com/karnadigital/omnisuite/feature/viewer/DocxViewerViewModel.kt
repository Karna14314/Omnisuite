package com.karnadigital.omnisuite.feature.viewer

import android.content.Context
import android.net.Uri
import com.karnadigital.omnisuite.core.engine.document.OfficeConverter
import com.karnadigital.omnisuite.core.engine.DocumentSearchEngine
import com.karnadigital.omnisuite.core.engine.SearchResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.core.repository.RecentFileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject

data class DocxRun(
    val text: String,
    val isBold: Boolean,
    val isItalic: Boolean,
    val isUnderline: Boolean,
    val isStrike: Boolean
)

data class DocxParagraph(
    val runs: List<DocxRun>,
    val alignment: String, // "LEFT", "CENTER", "RIGHT", "JUSTIFY"
    val isHeading: Boolean
)

data class DocxDocument(val paragraphs: List<DocxParagraph>)

sealed class DocxLoadState {
    object Loading : DocxLoadState()
    data class Success(val document: DocxDocument, val fileName: String) : DocxLoadState()
    data class Error(val message: String) : DocxLoadState()
}

@HiltViewModel
class DocxViewerViewModel @Inject constructor(
    private val recentFileRepository: RecentFileRepository
) : ViewModel() {

    private val _loadState = MutableStateFlow<DocxLoadState>(DocxLoadState.Loading)
    val loadState: StateFlow<DocxLoadState> = _loadState.asStateFlow()

    private val _saveStatus = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    val saveStatus = _saveStatus.asSharedFlow()

    private var activeDocument: XWPFDocument? = null
    private var activeFilePath: String? = null

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _currentMatchIndex = MutableStateFlow(-1)
    val currentMatchIndex: StateFlow<Int> = _currentMatchIndex.asStateFlow()

    /**
     * Safely reads DOCX paragraphs inside coroutines using Apache POI,
     * translating typography run formats, and updating Room DB logs.
     */
    fun loadWordFile(filePath: String) {
        viewModelScope.launch {
            _loadState.value = DocxLoadState.Loading
            withContext(Dispatchers.IO) {
                // Free previous document reference if any
                try {
                    activeDocument?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                activeDocument = null
                activeFilePath = null

                var fileInputStream: FileInputStream? = null
                var doc: XWPFDocument? = null
                try {
                    val file = File(filePath)
                    if (!file.exists() || !file.isFile) {
                        _loadState.value = DocxLoadState.Error("Target Word document does not exist or is corrupted.")
                        return@withContext
                    }

                    fileInputStream = FileInputStream(file)
                    doc = XWPFDocument(fileInputStream)

                    val parsedDoc = parseDocument(doc)

                    // Update RecentFiles DB offline logger
                    try {
                        val recentFile = RecentFile(
                            fileUri = file.absolutePath,
                            fileName = file.name,
                            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            fileSize = file.length(),
                            lastOpened = System.currentTimeMillis()
                        )
                        recentFileRepository.insertRecentFile(recentFile)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    activeDocument = doc
                    activeFilePath = filePath

                    _loadState.value = DocxLoadState.Success(
                        document = parsedDoc,
                        fileName = file.name
                    )

                } catch (e: Exception) {
                    e.printStackTrace()
                    try {
                        doc?.close()
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                    _loadState.value = DocxLoadState.Error("Apache POI word parser failure: ${e.localizedMessage}")
                } finally {
                    try {
                        fileInputStream?.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun parseDocument(doc: XWPFDocument): DocxDocument {
        val paragraphs = mutableListOf<DocxParagraph>()
        for (paragraph in doc.paragraphs) {
            val runs = mutableListOf<DocxRun>()
            for (run in paragraph.runs) {
                val text = run.getText(0) ?: ""
                val isBold = run.isBold
                val isItalic = run.isItalic
                val isUnderline = run.underline != org.apache.poi.xwpf.usermodel.UnderlinePatterns.NONE
                val isStrike = run.isStrikeThrough
                
                runs.add(DocxRun(text, isBold, isItalic, isUnderline, isStrike))
            }

            val alignment = when (paragraph.alignment) {
                ParagraphAlignment.CENTER -> "CENTER"
                ParagraphAlignment.RIGHT -> "RIGHT"
                ParagraphAlignment.BOTH -> "JUSTIFY"
                else -> "LEFT"
            }

            val isHeading = paragraph.styleID?.lowercase()?.contains("heading") == true ||
                    paragraph.runs.firstOrNull()?.fontSize ?: 0 > 14

            paragraphs.add(DocxParagraph(runs, alignment, isHeading))
        }
        return DocxDocument(paragraphs)
    }

    /**
     * Replaces the text of the paragraph at index by creating a single run.
     */
    fun updateParagraph(index: Int, newText: String) {
        val doc = activeDocument ?: return
        val paragraphs = doc.paragraphs
        if (index in paragraphs.indices) {
            val p = paragraphs[index]
            // Overwrite all run content
            while (p.runs.isNotEmpty()) {
                p.removeRun(0)
            }
            p.createRun().setText(newText)

            // Re-parse and update screen state
            val parsedDoc = parseDocument(doc)
            _loadState.value = DocxLoadState.Success(parsedDoc, File(activeFilePath!!).name)
        }
    }

    /**
     * Appends a new paragraph to the document.
     */
    fun appendParagraph(text: String) {
        val doc = activeDocument ?: return
        val newP = doc.createParagraph()
        newP.createRun().setText(text)

        // Re-parse and update screen state
        val parsedDoc = parseDocument(doc)
        _loadState.value = DocxLoadState.Success(parsedDoc, File(activeFilePath!!).name)
    }

    /**
     * Commits all in-memory changes back to the offline storage path.
     */
    fun commitChanges() {
        viewModelScope.launch {
            val doc = activeDocument
            val filePath = activeFilePath
            if (doc == null || filePath == null) {
                _saveStatus.emit("No active document loaded.")
                return@launch
            }

            withContext(Dispatchers.IO) {
                var fileOutputStream: java.io.FileOutputStream? = null
                try {
                    fileOutputStream = java.io.FileOutputStream(File(filePath))
                    doc.write(fileOutputStream)
                    _saveStatus.emit("Word document changes committed successfully!")
                } catch (e: Exception) {
                    e.printStackTrace()
                    _saveStatus.emit("Failed to save changes: ${e.localizedMessage}")
                } finally {
                    try {
                        fileOutputStream?.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    /**
     * Converts the current active Word document directly to PDF and writes it to a SAF URI.
     */
    fun exportToPdf(
        context: Context,
        outputUri: Uri,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch {
            val docxPath = activeFilePath
            if (docxPath == null) {
                onFailure("No active document loaded.")
                return@launch
            }
            withContext(Dispatchers.IO) {
                val tempPdfFile = File(context.cacheDir, "temp_export_${System.currentTimeMillis()}.pdf")
                try {
                    OfficeConverter.convertDocxToPdf(context, File(docxPath), tempPdfFile)
                    context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                        tempPdfFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        onSuccess()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        onFailure(e.localizedMessage ?: "Conversion failed")
                    }
                } finally {
                    if (tempPdfFile.exists()) {
                        tempPdfFile.delete()
                    }
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _currentMatchIndex.value = -1
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val path = activeFilePath ?: return@withContext
                val results = DocumentSearchEngine.searchDocx(path, query)
                _searchResults.value = results
                if (results.isNotEmpty()) {
                    _currentMatchIndex.value = 0
                } else {
                    _currentMatchIndex.value = -1
                }
            }
        }
    }

    fun nextMatch() {
        val results = _searchResults.value
        if (results.isEmpty()) return
        val nextIndex = (_currentMatchIndex.value + 1) % results.size
        _currentMatchIndex.value = nextIndex
    }

    fun prevMatch() {
        val results = _searchResults.value
        if (results.isEmpty()) return
        val prevIndex = (_currentMatchIndex.value - 1 + results.size) % results.size
        _currentMatchIndex.value = prevIndex
    }

    override fun onCleared() {
        super.onCleared()
        try {
            activeDocument?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
