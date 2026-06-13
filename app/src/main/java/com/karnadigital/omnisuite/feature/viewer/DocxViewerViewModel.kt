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
import org.apache.poi.xwpf.usermodel.IBodyElement
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject

data class DocxRun(
    val text: String,
    val isBold: Boolean,
    val isItalic: Boolean,
    val isUnderline: Boolean,
    val isStrike: Boolean,
    val color: String? = null,
    val fontFamily: String? = null,
    val fontSizePt: Int? = null,
    val hyperlinkUrl: String? = null,
    val imageUrl: String? = null,
    val widthEmu: Long? = null,
    val heightEmu: Long? = null
)

data class DocxParagraph(
    val runs: List<DocxRun>,
    val alignment: String, // "LEFT", "CENTER", "RIGHT", "JUSTIFY"
    val headingLevel: Int, // 0 = body, 1-6 = heading level
    val isHeading: Boolean,
    val comment: String? = null,
    val spacingAfterPt: Int = 10,
    val spacingBeforePt: Int = 0
)

data class DocxTableCell(val paragraphs: List<DocxParagraph>)
data class DocxTableRow(val cells: List<DocxTableCell>)

sealed class DocxBodyElement {
    data class Para(val paragraph: DocxParagraph) : DocxBodyElement()
    data class Table(val rows: List<DocxTableRow>) : DocxBodyElement()
}

data class DocxDocument(val elements: List<DocxBodyElement>)

sealed class DocxLoadState {
    object Loading : DocxLoadState()
    data class Success(val document: DocxDocument, val fileName: String) : DocxLoadState()
    data class Error(val message: String) : DocxLoadState()
}

@HiltViewModel
class DocxViewerViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
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

    private fun loadParagraphComments(filePath: String): Map<Int, String> {
        val commentsFile = File("$filePath.comments")
        if (!commentsFile.exists()) return emptyMap()
        val map = mutableMapOf<Int, String>()
        try {
            commentsFile.readLines().forEach { line ->
                val idx = line.indexOf(':')
                if (idx != -1) {
                    val pIdx = line.substring(0, idx).toIntOrNull()
                    val commentText = line.substring(idx + 1)
                    if (pIdx != null) {
                        map[pIdx] = commentText
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    private fun saveParagraphComments(filePath: String, comments: Map<Int, String>) {
        val commentsFile = File("$filePath.comments")
        try {
            val lines = comments.filter { it.value.isNotBlank() }
                .map { "${it.key}:${it.value}" }
            commentsFile.writeText(lines.joinToString("\n"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseDocument(doc: XWPFDocument): DocxDocument {
        val elements = mutableListOf<DocxBodyElement>()
        val commentsMap = activeFilePath?.let { loadParagraphComments(it) } ?: emptyMap()
        var globalParaIndex = 0

        for (bodyElement in doc.bodyElements) {
            when (bodyElement) {
                is org.apache.poi.xwpf.usermodel.XWPFParagraph -> {
                    val parsed = parseParagraph(bodyElement, commentsMap[globalParaIndex])
                    elements.add(DocxBodyElement.Para(parsed))
                    globalParaIndex++
                }
                is org.apache.poi.xwpf.usermodel.XWPFTable -> {
                    val rows = mutableListOf<DocxTableRow>()
                    for (row in bodyElement.rows) {
                        val cells = mutableListOf<DocxTableCell>()
                        for (cell in row.tableCells) {
                            val cellParas = cell.paragraphs.map { parseParagraph(it, null) }
                            cells.add(DocxTableCell(cellParas))
                        }
                        rows.add(DocxTableRow(cells))
                    }
                    elements.add(DocxBodyElement.Table(rows))
                }
            }
        }
        return DocxDocument(elements)
    }

    private fun parseParagraph(
        paragraph: org.apache.poi.xwpf.usermodel.XWPFParagraph,
        comment: String?
    ): DocxParagraph {
        val runs = mutableListOf<DocxRun>()
        for (run in paragraph.runs) {
            val text = run.getText(0) ?: ""
            val isBold = run.isBold
            val isItalic = run.isItalic
            val isUnderline = run.underline != org.apache.poi.xwpf.usermodel.UnderlinePatterns.NONE
            val isStrike = run.isStrikeThrough
            val color = run.color
            val fontFamily = run.fontFamily
            val fontSize = run.fontSize  // in half-points; divide by 2 for pt
            val hyperlinkUrl = if (run is org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun) {
                run.getHyperlink(paragraph.document)?.url
            } else null

            var imageUrl: String? = null
            var emuWidth: Long? = null
            var emuHeight: Long? = null
            val pictures = run.embeddedPictures
            if (pictures.isNotEmpty()) {
                try {
                    val pic = pictures[0]
                    val picData = pic.pictureData.data
                    val ext = pic.pictureData.suggestFileExtension() ?: "png"
                    try {
                        val ctPic = pic.javaClass.getMethod("getCTPic").invoke(pic)
                        val spPr = ctPic.javaClass.getMethod("getSpPr").invoke(ctPic)
                        val xfrm = spPr.javaClass.getMethod("getXfrm").invoke(spPr)
                        val extVal = xfrm.javaClass.getMethod("getExt").invoke(xfrm)
                        emuWidth = (extVal.javaClass.getMethod("getCx").invoke(extVal) as Number).toLong()
                        emuHeight = (extVal.javaClass.getMethod("getCy").invoke(extVal) as Number).toLong()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    val tempPicFile = File(context.cacheDir, "docx_img_${System.currentTimeMillis()}_${pic.hashCode()}.$ext")
                    tempPicFile.writeBytes(picData)
                    imageUrl = tempPicFile.absolutePath
                } catch (e: Exception) { e.printStackTrace() }
            }

            runs.add(DocxRun(
                text = text, isBold = isBold, isItalic = isItalic,
                isUnderline = isUnderline, isStrike = isStrike,
                color = color, fontFamily = fontFamily,
                fontSizePt = if (fontSize > 0) fontSize / 2 else null,
                hyperlinkUrl = hyperlinkUrl, imageUrl = imageUrl,
                widthEmu = emuWidth, heightEmu = emuHeight
            ))
        }

        val alignment = when (paragraph.alignment) {
            org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER -> "CENTER"
            org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT -> "RIGHT"
            org.apache.poi.xwpf.usermodel.ParagraphAlignment.BOTH -> "JUSTIFY"
            else -> "LEFT"
        }

        val styleId = paragraph.styleID?.lowercase() ?: ""
        val headingLevel = when {
            styleId.contains("heading1") || styleId == "title" -> 1
            styleId.contains("heading2") -> 2
            styleId.contains("heading3") -> 3
            styleId.contains("heading4") -> 4
            styleId.contains("heading5") || styleId.contains("heading6") -> 5
            else -> 0
        }



        return DocxParagraph(
            runs = runs,
            alignment = alignment,
            headingLevel = headingLevel,
            isHeading = headingLevel > 0,
            comment = comment,
            spacingAfterPt = paragraph.spacingAfter.takeIf { it >= 0 }?.div(20) ?: 6,
            spacingBeforePt = paragraph.spacingBefore.takeIf { it >= 0 }?.div(20) ?: 2
        )
    }

    /**
     * Replaces the text and formatting of the paragraph at index.
     */
    fun updateParagraph(
        index: Int,
        newText: String,
        isBold: Boolean = false,
        isItalic: Boolean = false,
        isUnderline: Boolean = false,
        colorHex: String? = null,
        comment: String? = null
    ) {
        val doc = activeDocument ?: return
        val elements = (loadState.value as? DocxLoadState.Success)?.document?.elements ?: return
        if (index < 0 || index >= elements.size) return
        if (elements[index] !is DocxBodyElement.Para) return
        val paraIndex = elements.take(index).count { it is DocxBodyElement.Para }
        val paragraphs = doc.paragraphs
        if (paraIndex in paragraphs.indices) {
            val p = paragraphs[paraIndex]
            val runCount = p.runs.size
            for (i in runCount - 1 downTo 0) {
                try { p.removeRun(i) } catch (e: Exception) { }
            }
            val run = p.createRun()
            run.setText(newText)
            run.isBold = isBold
            run.isItalic = isItalic
            run.underline = if (isUnderline) org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE
                            else org.apache.poi.xwpf.usermodel.UnderlinePatterns.NONE
            if (!colorHex.isNullOrBlank()) {
                run.setColor(colorHex.replace("#", ""))
            }

            val filePath = activeFilePath
            if (filePath != null) {
                val commentsMap = loadParagraphComments(filePath).toMutableMap()
                if (comment.isNullOrBlank()) commentsMap.remove(paraIndex) else commentsMap[paraIndex] = comment
                saveParagraphComments(filePath, commentsMap)
            }

            val parsedDoc = parseDocument(doc)
            _loadState.value = DocxLoadState.Success(parsedDoc, File(activeFilePath!!).name)
        }
    }

    fun insertImageIntoParagraph(index: Int, imagePath: String) {
        val doc = activeDocument ?: return
        val elements = (loadState.value as? DocxLoadState.Success)?.document?.elements ?: return
        if (index < 0 || index >= elements.size) return
        if (elements[index] !is DocxBodyElement.Para) return
        val paraIndex = elements.take(index).count { it is DocxBodyElement.Para }
        val paragraphs = doc.paragraphs
        if (paraIndex in paragraphs.indices) {
            val p = paragraphs[paraIndex]
            val run = p.createRun()
            var fis: java.io.FileInputStream? = null
            try {
                val imgFile = File(imagePath)
                if (imgFile.exists() && imgFile.isFile) {
                    fis = java.io.FileInputStream(imgFile)
                    run.addPicture(
                        fis,
                        org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG,
                        imgFile.name,
                        org.apache.poi.util.Units.toEMU(300.0),
                        org.apache.poi.util.Units.toEMU(200.0)
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try { fis?.close() } catch(e: Exception) {}
            }

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
