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
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject

data class CellData(
    val text: String,                    // displayed value
    val formulaString: String? = null,   // raw formula like "=SUM(A1:A10)", null if not a formula
    val colorHex: String? = null,        // background fill color
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val textColorHex: String? = null,
    val fontSizePt: Int = 10,
    val comment: String? = null,
    val hyperlinkUrl: String? = null,    // ADD: URL from cell.hyperlink
    val mergeColSpan: Int = 1,           // ADD: columns this cell spans (1 = no merge)
    val mergeRowSpan: Int = 1,           // ADD: rows this cell spans (1 = no merge)
    val isMergeAnchor: Boolean = true,   // ADD: false if this cell is covered by a merge (should be invisible)
    val horizontalAlign: String = "LEFT" // ADD: "LEFT", "CENTER", "RIGHT"
)

data class ExcelSheet(
    val name: String,
    val rows: List<List<CellData>>,
    val columnWidthsDp: List<Float>,     // ADD: width per column in dp (derived from POI column width)
    val rowHeightsDp: List<Float>,       // ADD: height per row in dp (derived from POI row height)
    val frozenRowCount: Int = 0,         // ADD: rows to freeze (from paneInformation)
    val frozenColCount: Int = 0,         // ADD: cols to freeze (from paneInformation)
    val charts: List<SheetChart> = emptyList() // ADD: extracted charts
)

data class SheetChart(
    val title: String,
    val chartType: String,   // "BAR", "PIE", "LINE", "AREA", "SCATTER", "UNKNOWN"
    val series: List<ChartSeries>,
    val anchorRow: Int,
    val anchorCol: Int
)

data class ChartSeries(
    val name: String,
    val labels: List<String>,
    val values: List<Double>
)

data class ExcelWorkbook(val sheets: List<ExcelSheet>)

sealed class XlsxLoadState {
    object Loading : XlsxLoadState()
    data class Success(val workbook: ExcelWorkbook, val fileName: String) : XlsxLoadState()
    data class Error(val message: String) : XlsxLoadState()
}

@HiltViewModel
class XlsxViewerViewModel @Inject constructor(
    private val recentFileRepository: RecentFileRepository
) : ViewModel() {

    private val _loadState = MutableStateFlow<XlsxLoadState>(XlsxLoadState.Loading)
    val loadState: StateFlow<XlsxLoadState> = _loadState.asStateFlow()

    private val _saveStatus = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    val saveStatus = _saveStatus.asSharedFlow()

    private var activeWorkbook: XSSFWorkbook? = null
    private var activeFilePath: String? = null

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _currentMatchIndex = MutableStateFlow(-1)
    val currentMatchIndex: StateFlow<Int> = _currentMatchIndex.asStateFlow()

    /**
     * Safely reads XLSX content off the main thread using Apache POI, formatting
     * cell values properly, and updating Room DB logs.
     */
    fun loadExcelFile(filePath: String) {
        viewModelScope.launch {
            _loadState.value = XlsxLoadState.Loading
            withContext(Dispatchers.IO) {
                // Free previous workbook reference if any
                try {
                    activeWorkbook?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                activeWorkbook = null
                activeFilePath = null

                var fileInputStream: FileInputStream? = null
                var workbook: XSSFWorkbook? = null
                try {
                    val file = File(filePath)
                    if (!file.exists() || !file.isFile) {
                        _loadState.value = XlsxLoadState.Error("Target spreadsheet does not exist or is corrupted.")
                        return@withContext
                    }

                    val isCsv = file.name.endsWith(".csv", ignoreCase = true) || !isZipFile(file)
                    workbook = if (isCsv) {
                        loadCsvAsWorkbook(file)
                    } else {
                        fileInputStream = FileInputStream(file)
                        XSSFWorkbook(fileInputStream)
                    }

                    val parsedWb = parseWorkbook(workbook)

                    activeWorkbook = workbook
                    activeFilePath = filePath

                    _loadState.value = XlsxLoadState.Success(
                        workbook = parsedWb,
                        fileName = file.name
                    )

                } catch (e: Exception) {
                    e.printStackTrace()
                    try {
                        workbook?.close()
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                    _loadState.value = XlsxLoadState.Error("Spreadsheet parser failure: ${e.localizedMessage}")
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

    private fun isZipFile(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return try {
            val bytes = ByteArray(4)
            FileInputStream(file).use { fis ->
                fis.read(bytes)
            }
            bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
        } catch (e: Exception) {
            false
        }
    }

    private fun loadCsvAsWorkbook(file: File): XSSFWorkbook {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("CSV Document")
        val lines = file.readLines()
        for (r in lines.indices) {
            val row = sheet.createRow(r)
            val line = lines[r]
            val cells = parseCsvLine(line)
            for (c in cells.indices) {
                val cell = row.createCell(c)
                val cellVal = cells[c]
                val doubleVal = cellVal.toDoubleOrNull()
                if (doubleVal != null) {
                    cell.setCellValue(doubleVal)
                } else {
                    cell.setCellValue(cellVal)
                }
            }
        }
        return workbook
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var curVal = StringBuilder()
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

    private fun parseWorkbook(wb: XSSFWorkbook): ExcelWorkbook {
        val dataFormatter = org.apache.poi.ss.usermodel.DataFormatter()
        val evaluator = try { wb.creationHelper.createFormulaEvaluator() } catch (e: Exception) { null }
        val sheetList = mutableListOf<ExcelSheet>()
        val EXTRA_ROWS = 50   // empty extension rows beyond data
        val EXTRA_COLS = 10   // empty extension cols beyond data

        for (s in 0 until wb.numberOfSheets) {
            val sheet = wb.getSheetAt(s) as org.apache.poi.xssf.usermodel.XSSFSheet
            val sheetName = sheet.sheetName ?: "Sheet ${s + 1}"

            // --- Column widths ---
            val lastRowNum = sheet.lastRowNum.coerceAtLeast(0)
            var maxCols = 0
            for (r in 0..lastRowNum) {
                val row = sheet.getRow(r) ?: continue
                maxCols = maxOf(maxCols, row.lastCellNum.toInt())
            }
            maxCols = maxCols.coerceAtLeast(1)
            val totalCols = maxCols + EXTRA_COLS

            // POI column width is in 1/256th character units; 1 char ≈ 7px at 96dpi ≈ 5.25dp
            val columnWidthsDp = (0 until totalCols).map { c ->
                val poiWidth = sheet.getColumnWidth(c) // 1/256th char units
                (poiWidth / 256f * 8f).coerceIn(30f, 300f)  // convert to dp, clamp
            }

            // --- Merged regions lookup ---
            // Map of "row,col" -> MergeInfo for anchors, null for covered cells
            data class MergeInfo(val colSpan: Int, val rowSpan: Int)
            val mergeAnchorMap = mutableMapOf<String, MergeInfo>()
            val coveredCells = mutableSetOf<String>()
            for (region in sheet.mergedRegions) {
                val key = "${region.firstRow},${region.firstColumn}"
                mergeAnchorMap[key] = MergeInfo(
                    colSpan = region.lastColumn - region.firstColumn + 1,
                    rowSpan = region.lastRow - region.firstRow + 1
                )
                for (r in region.firstRow..region.lastRow) {
                    for (c in region.firstColumn..region.lastColumn) {
                        if (r != region.firstRow || c != region.firstColumn) {
                            coveredCells.add("$r,$c")
                        }
                    }
                }
            }

            // --- Freeze pane ---
            val paneInfo = sheet.paneInformation
            // getHorizontalSplitPosition() = number of frozen rows for a freeze-pane
            // getVerticalSplitPosition()   = number of frozen columns for a freeze-pane
            val frozenRows = if (paneInfo?.isFreezePane == true) paneInfo.horizontalSplitPosition.toInt() else 0
            val frozenCols = if (paneInfo?.isFreezePane == true) paneInfo.verticalSplitPosition.toInt() else 0

            // --- Row data ---
            val totalRows = lastRowNum + 1 + EXTRA_ROWS
            val rowList = mutableListOf<List<CellData>>()
            val rowHeightsDp = mutableListOf<Float>()

            for (r in 0 until totalRows) {
                val row = sheet.getRow(r)
                // Height: POI uses 1/20th of a point; 1pt ≈ 1.33dp
                val rowHeightDp = if (row != null && row.height > 0) {
                    (row.height / 20f * 1.33f).coerceIn(20f, 120f)
                } else 24f
                rowHeightsDp.add(rowHeightDp)

                val rowCells = mutableListOf<CellData>()
                for (c in 0 until totalCols) {
                    val coordKey = "$r,$c"
                    if (coordKey in coveredCells) {
                        rowCells.add(CellData(text = "", isMergeAnchor = false))
                        continue
                    }
                    val cell = row?.getCell(c)
                    if (cell == null || r > lastRowNum || c >= maxCols) {
                        // Extension cells (beyond data range) — empty but interactive
                        rowCells.add(CellData(text = ""))
                        continue
                    }

                    // --- Style ---
                    val style = cell.cellStyle as? org.apache.poi.xssf.usermodel.XSSFCellStyle
                    var colorHex: String? = null
                    var isBold = false
                    var isItalic = false
                    var isUnderline = false
                    var textColorHex: String? = null
                    var fontSizePt = 10
                    var hAlign = "LEFT"

                    if (style != null) {
                        val fgColor = style.fillForegroundXSSFColor
                        if (fgColor != null && style.fillPattern != org.apache.poi.ss.usermodel.FillPatternType.NO_FILL) {
                            val rgb = fgColor.argbHex
                            if (rgb != null && rgb.length >= 6 && !rgb.endsWith("000000") && !rgb.endsWith("FFFFFF") && !rgb.endsWith("ffffff")) {
                                colorHex = "#" + rgb.substring(rgb.length - 6)
                            }
                        }
                        val font = wb.getFontAt(style.fontIndexAsInt)
                        isBold = font.bold
                        isItalic = font.italic
                        isUnderline = font.underline != org.apache.poi.ss.usermodel.Font.U_NONE
                        fontSizePt = if (font.fontHeightInPoints > 0) font.fontHeightInPoints.toInt() else 10
                        val xssfFont = font as? org.apache.poi.xssf.usermodel.XSSFFont
                        xssfFont?.xssfColor?.argbHex?.let { rgb ->
                            if (rgb.length >= 6) textColorHex = "#" + rgb.substring(rgb.length - 6)
                        }
                        hAlign = when (style.alignment) {
                            org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER -> "CENTER"
                            org.apache.poi.ss.usermodel.HorizontalAlignment.RIGHT -> "RIGHT"
                            else -> "LEFT"
                        }
                    }

                    // --- Value ---
                    val formulaString = if (cell.cellType == org.apache.poi.ss.usermodel.CellType.FORMULA) {
                        "=${cell.cellFormula}"
                    } else null

                    val displayText = try {
                        if (cell.cellType == org.apache.poi.ss.usermodel.CellType.FORMULA && evaluator != null) {
                            val evaluated = evaluator.evaluate(cell)
                            when (evaluated?.cellType) {
                                org.apache.poi.ss.usermodel.CellType.NUMERIC -> {
                                    if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                                        dataFormatter.formatCellValue(cell, evaluator)
                                    } else {
                                        val n = evaluated.numberValue
                                        if (n == n.toLong().toDouble()) n.toLong().toString() else n.toString()
                                    }
                                }
                                org.apache.poi.ss.usermodel.CellType.STRING -> evaluated.stringValue ?: ""
                                org.apache.poi.ss.usermodel.CellType.BOOLEAN -> evaluated.booleanValue.toString()
                                else -> dataFormatter.formatCellValue(cell, evaluator)
                            }
                        } else {
                            dataFormatter.formatCellValue(cell)
                        }
                    } catch (e: Exception) {
                        try { cell.toString() } catch (e2: Exception) { "" }
                    }

                    // --- Hyperlink ---
                    val hyperlinkUrl = try { cell.hyperlink?.address } catch (e: Exception) { null }

                    // --- Merge ---
                    val mergeInfo = mergeAnchorMap[coordKey]
                    val cellComment = try { cell.cellComment?.string?.string } catch (e: Exception) { null }

                    rowCells.add(CellData(
                        text = displayText,
                        formulaString = formulaString,
                        colorHex = colorHex,
                        isBold = isBold,
                        isItalic = isItalic,
                        isUnderline = isUnderline,
                        textColorHex = textColorHex,
                        fontSizePt = fontSizePt,
                        comment = cellComment,
                        hyperlinkUrl = hyperlinkUrl,
                        mergeColSpan = mergeInfo?.colSpan ?: 1,
                        mergeRowSpan = mergeInfo?.rowSpan ?: 1,
                        isMergeAnchor = true,
                        horizontalAlign = hAlign
                    ))
                }
                rowList.add(rowCells)
            }

            // --- Charts ---
            val charts = extractCharts(sheet)

            sheetList.add(ExcelSheet(
                name = sheetName,
                rows = rowList,
                columnWidthsDp = columnWidthsDp,
                rowHeightsDp = rowHeightsDp,
                frozenRowCount = frozenRows,
                frozenColCount = frozenCols,
                charts = charts
            ))
        }
        return ExcelWorkbook(sheetList)
    }

    /**
     * Extracts chart metadata from the sheet's drawing patriarch using safe XML-level access.
     * Fully wrapped in try/catch so any unsupported POI-Android API path is silently ignored.
     */
    private fun extractCharts(sheet: org.apache.poi.xssf.usermodel.XSSFSheet): List<SheetChart> {
        val charts = mutableListOf<SheetChart>()
        try {
            val drawing = sheet.drawingPatriarch ?: return charts
            // drawing.charts returns List<XSSFChart> on full POI; on poi-ooxml-lite it may be empty
            val rawCharts: List<*> = try {
                val method = drawing.javaClass.getMethod("getCharts")
                @Suppress("UNCHECKED_CAST")
                method.invoke(drawing) as? List<*> ?: emptyList<Any>()
            } catch (e: Exception) { emptyList<Any>() }

            for (rawChart in rawCharts) {
                try {
                    if (rawChart == null) continue

                    // --- Anchor ---
                    val anchorRow: Int
                    val anchorCol: Int
                    try {
                        val graphicFrameMethod = rawChart.javaClass.getMethod("getGraphicFrame")
                        val frame = graphicFrameMethod.invoke(rawChart)
                        val anchorMethod = frame.javaClass.getMethod("getAnchor")
                        val anchor = anchorMethod.invoke(frame) as? org.apache.poi.xssf.usermodel.XSSFClientAnchor
                        anchorRow = anchor?.row1?.toInt() ?: 0
                        anchorCol = anchor?.col1?.toInt() ?: 0
                    } catch (e: Exception) {
                        // If we can't get anchor, place at 0,0
                        charts.add(SheetChart("Chart", "UNKNOWN", emptyList(), 0, 0))
                        continue
                    }

                    // --- Chart XML access ---
                    // Use reflection to call getCTChart() -> CTChart -> plotArea
                    val ctChart = try {
                        val m = rawChart.javaClass.getMethod("getCTChart")
                        m.invoke(rawChart)
                    } catch (e: Exception) { null }

                    val plotArea = try {
                        ctChart?.javaClass?.getMethod("getPlotArea")?.invoke(ctChart)
                    } catch (e: Exception) { null }

                    // Determine chart type by checking which series list is non-empty
                    val chartType: String = if (plotArea == null) "UNKNOWN" else try {
                        val barList = try { (plotArea.javaClass.getMethod("getBarChartList").invoke(plotArea) as? List<*>)?.size ?: 0 } catch (e: Exception) { 0 }
                        val pieList = try { (plotArea.javaClass.getMethod("getPieChartList").invoke(plotArea) as? List<*>)?.size ?: 0 } catch (e: Exception) { 0 }
                        val pie3dList = try { (plotArea.javaClass.getMethod("getPie3DChartList").invoke(plotArea) as? List<*>)?.size ?: 0 } catch (e: Exception) { 0 }
                        val lineList = try { (plotArea.javaClass.getMethod("getLineChartList").invoke(plotArea) as? List<*>)?.size ?: 0 } catch (e: Exception) { 0 }
                        val areaList = try { (plotArea.javaClass.getMethod("getAreaChartList").invoke(plotArea) as? List<*>)?.size ?: 0 } catch (e: Exception) { 0 }
                        val scatterList = try { (plotArea.javaClass.getMethod("getScatterChartList").invoke(plotArea) as? List<*>)?.size ?: 0 } catch (e: Exception) { 0 }
                        when {
                            barList > 0 -> "BAR"
                            pieList > 0 || pie3dList > 0 -> "PIE"
                            lineList > 0 -> "LINE"
                            areaList > 0 -> "AREA"
                            scatterList > 0 -> "SCATTER"
                            else -> "UNKNOWN"
                        }
                    } catch (e: Exception) { "UNKNOWN" }

                    // Chart title
                    val titleText: String = try {
                        val titleObj = ctChart?.javaClass?.getMethod("getTitle")?.invoke(ctChart)
                        val txObj = titleObj?.javaClass?.getMethod("getTx")?.invoke(titleObj)
                        val richObj = txObj?.javaClass?.getMethod("getRich")?.invoke(txObj)
                        val pList = richObj?.javaClass?.getMethod("getPList")?.invoke(richObj) as? List<*>
                        val firstP = pList?.firstOrNull()
                        val rList = firstP?.javaClass?.getMethod("getRList")?.invoke(firstP) as? List<*>
                        val firstR = rList?.firstOrNull()
                        firstR?.javaClass?.getMethod("getT")?.invoke(firstR)?.toString() ?: chartType
                    } catch (e: Exception) { chartType }

                    charts.add(SheetChart(titleText, chartType, emptyList(), anchorRow, anchorCol))
                } catch (t: Throwable) { t.printStackTrace() }
            }
        } catch (t: Throwable) { t.printStackTrace() }
        return charts
    }

    /**
     * Updates an active Excel cell, converting double values safely, and
     * re-running formulas evaluations downstream instantly.
     */
    fun updateCell(
        sheetIndex: Int,
        rowIndex: Int,
        colIndex: Int,
        valueString: String,
        colorHex: String? = null,
        isBold: Boolean = false,
        isItalic: Boolean = false,
        isUnderline: Boolean = false,
        textColorHex: String? = null,
        commentText: String? = null,
        dataFormat: String? = null
    ) {
        val wb = activeWorkbook ?: return
        val sheet = wb.getSheetAt(sheetIndex) ?: return
        var row = sheet.getRow(rowIndex)
        if (row == null) {
            row = sheet.createRow(rowIndex)
        }
        var cell = row.getCell(colIndex)
        if (cell == null) {
            cell = row.createCell(colIndex)
        }

        if (valueString.startsWith("=")) {
            try {
                cell.cellFormula = valueString.substring(1)
            } catch (e: Exception) {
                cell.setCellValue(valueString)
            }
        } else {
            val doubleValue = valueString.toDoubleOrNull()
            if (doubleValue != null) {
                cell.setCellValue(doubleValue)
            } else {
                cell.setCellValue(valueString)
            }
        }

        // Create style or get existing to merge formatting
        val style = wb.createCellStyle()
        
        // Background Color Fill
        if (colorHex != null) {
            style.fillPattern = org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND
            val xssfColor = org.apache.poi.xssf.usermodel.XSSFColor(
                byteArrayOf(
                    Integer.parseInt(colorHex.substring(1, 3), 16).toByte(),
                    Integer.parseInt(colorHex.substring(3, 5), 16).toByte(),
                    Integer.parseInt(colorHex.substring(5, 7), 16).toByte()
                ),
                null
            )
            (style as org.apache.poi.xssf.usermodel.XSSFCellStyle).setFillForegroundColor(xssfColor)
        } else {
            // Keep default style
            val cellStyle = cell.cellStyle as? org.apache.poi.xssf.usermodel.XSSFCellStyle
            if (cellStyle != null) {
                style.cloneStyleFrom(cellStyle)
            }
        }

        // Font formatting (Bold, Italic, Underline, Text Color)
        val font = wb.createFont()
        font.bold = isBold
        font.italic = isItalic
        font.underline = if (isUnderline) org.apache.poi.ss.usermodel.Font.U_SINGLE else org.apache.poi.ss.usermodel.Font.U_NONE
        if (textColorHex != null) {
            val xssfFont = font as? org.apache.poi.xssf.usermodel.XSSFFont
            if (xssfFont != null) {
                val colorBytes = byteArrayOf(
                    Integer.parseInt(textColorHex.substring(1, 3), 16).toByte(),
                    Integer.parseInt(textColorHex.substring(3, 5), 16).toByte(),
                    Integer.parseInt(textColorHex.substring(5, 7), 16).toByte()
                )
                val xColor = org.apache.poi.xssf.usermodel.XSSFColor(colorBytes, null)
                xssfFont.setColor(xColor)
            }
        }
        style.setFont(font)

        // Apply custom data format
        if (dataFormat != null) {
            try {
                val format = wb.createDataFormat()
                style.dataFormat = format.getFormat(dataFormat)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        cell.cellStyle = style

        // Cell Comment Annotation patriarch drawing
        if (commentText != null) {
            try {
                cell.removeCellComment()
                if (commentText.isNotBlank()) {
                    var patriarch = sheet.drawingPatriarch
                    if (patriarch == null) {
                        patriarch = sheet.createDrawingPatriarch()
                    }
                    val anchor = wb.creationHelper.createClientAnchor()
                    anchor.setCol1(colIndex)
                    anchor.setCol2(colIndex + 2)
                    anchor.row1 = rowIndex
                    anchor.row2 = rowIndex + 2
                    val cellComment = patriarch.createCellComment(anchor)
                    cellComment.string = wb.creationHelper.createRichTextString(commentText)
                    cell.cellComment = cellComment
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Instantly force downstream formula recalculations
        try {
            val evaluator = wb.creationHelper.createFormulaEvaluator()
            evaluator.evaluateAll()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Re-parse and update the screen representation state
        val updatedWb = parseWorkbook(wb)
        _loadState.value = XlsxLoadState.Success(updatedWb, File(activeFilePath!!).name)
    }

    fun insertRow(sheetIndex: Int, atRowIndex: Int, above: Boolean = true) {
        val wb = activeWorkbook ?: return
        val sheet = wb.getSheetAt(sheetIndex) ?: return
        val insertAt = if (above) atRowIndex else atRowIndex + 1
        if (sheet.lastRowNum >= insertAt) {
            sheet.shiftRows(insertAt, sheet.lastRowNum, 1, true, false)
        }
        sheet.createRow(insertAt)
        refreshState()
    }

    fun deleteRow(sheetIndex: Int, rowIndex: Int) {
        val wb = activeWorkbook ?: return
        val sheet = wb.getSheetAt(sheetIndex) ?: return
        val row = sheet.getRow(rowIndex)
        if (row != null) sheet.removeRow(row)
        if (rowIndex < sheet.lastRowNum) {
            sheet.shiftRows(rowIndex + 1, sheet.lastRowNum, -1, true, false)
        }
        refreshState()
    }

    fun insertColumn(sheetIndex: Int, atColIndex: Int, left: Boolean = true) {
        val wb = activeWorkbook ?: return
        val sheet = wb.getSheetAt(sheetIndex) ?: return
        val insertAt = if (left) atColIndex else atColIndex + 1
        // POI doesn't have a native shiftColumns; shift manually
        for (r in 0..sheet.lastRowNum) {
            val row = sheet.getRow(r) ?: continue
            val lastCell = row.lastCellNum.toInt()
            for (c in lastCell downTo insertAt + 1) {
                val oldCell = row.getCell(c - 1)
                val newCell = row.createCell(c)
                if (oldCell != null) {
                    newCell.setCellValue(getFormattedCellValue(oldCell))
                    newCell.cellStyle = oldCell.cellStyle
                }
            }
            row.getCell(insertAt)?.let { row.removeCell(it) }
            row.createCell(insertAt)
        }
        refreshState()
    }

    fun deleteColumn(sheetIndex: Int, colIndex: Int) {
        val wb = activeWorkbook ?: return
        val sheet = wb.getSheetAt(sheetIndex) ?: return
        for (r in 0..sheet.lastRowNum) {
            val row = sheet.getRow(r) ?: continue
            val lastCell = row.lastCellNum.toInt()
            for (c in colIndex until lastCell - 1) {
                val nextCell = row.getCell(c + 1)
                val currCell = row.createCell(c)
                if (nextCell != null) {
                    currCell.setCellValue(getFormattedCellValue(nextCell))
                    currCell.cellStyle = nextCell.cellStyle
                }
            }
            if (lastCell > 0) row.getCell(lastCell - 1)?.let { row.removeCell(it) }
        }
        refreshState()
    }

    fun setColumnWidth(sheetIndex: Int, colIndex: Int, widthDp: Float) {
        val wb = activeWorkbook ?: return
        val sheet = wb.getSheetAt(sheetIndex) ?: return
        // Convert dp back to POI 1/256th char units (1 char ≈ 8dp)
        val poiWidth = ((widthDp / 8f) * 256f).toInt().coerceIn(256, 25600)
        sheet.setColumnWidth(colIndex, poiWidth)
        refreshState()
    }

    fun setColumnBestFit(sheetIndex: Int, colIndex: Int) {
        val wb = activeWorkbook ?: return
        val sheet = wb.getSheetAt(sheetIndex) ?: return
        try {
            sheet.autoSizeColumn(colIndex)
        } catch (e: Exception) {
            // Fallback: estimate based on cell text length
            val successState = loadState.value as? XlsxLoadState.Success
            val rows = successState?.workbook?.sheets?.getOrNull(sheetIndex)?.rows
            if (rows != null) {
                var maxLen = 4
                for (r in rows.indices) {
                    val cellText = rows[r].getOrNull(colIndex)?.text ?: ""
                    maxLen = maxOf(maxLen, cellText.length)
                }
                val estimatedWidthDp = (maxLen * 8f + 16f).coerceIn(40f, 300f)
                setColumnWidth(sheetIndex, colIndex, estimatedWidthDp)
                return
            }
        }
        refreshState()
    }

    fun setRowHeight(sheetIndex: Int, rowIndex: Int, heightDp: Float) {
        val wb = activeWorkbook ?: return
        val sheet = wb.getSheetAt(sheetIndex) ?: return
        val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
        // Convert dp back to POI 1/20th point units (1 dp ≈ 1.33pt)
        row.height = ((heightDp / 1.33f) * 20f).toInt().toShort()
        refreshState()
    }

    fun setCellHyperlink(sheetIndex: Int, rowIndex: Int, colIndex: Int, url: String) {
        val wb = activeWorkbook ?: return
        val sheet = wb.getSheetAt(sheetIndex) ?: return
        val row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
        val cell = row.getCell(colIndex) ?: row.createCell(colIndex)
        
        if (url.isBlank()) {
            try {
                cell.removeHyperlink()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            try {
                val helper = wb.creationHelper
                val hyperlink = helper.createHyperlink(org.apache.poi.common.usermodel.HyperlinkType.URL)
                hyperlink.address = url
                cell.hyperlink = hyperlink
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        refreshState()
    }

    fun sortByColumn(sheetIndex: Int, colIndex: Int, ascending: Boolean) {
        val wb = activeWorkbook ?: return
        val sheet = wb.getSheetAt(sheetIndex) ?: return
        val lastRow = sheet.lastRowNum
        if (lastRow < 1) return

        // Collect rows as list of (rowIndex, cellValue for sort col)
        val rowsData = (1..lastRow).mapNotNull { r -> // skip header row (0)
            val row = sheet.getRow(r) ?: return@mapNotNull null
            val cell = row.getCell(colIndex)
            val sortKey = if (cell != null) getFormattedCellValue(cell) else ""
            Pair(r, sortKey)
        }.sortedWith(compareBy { if (ascending) it.second else "\uffff" + it.second })

        // Re-create rows in sorted order (copy cell values)
        val snapshotValues = rowsData.map { (origIdx, _) ->
            val row = sheet.getRow(origIdx) ?: return@map emptyList<String>()
            (0 until row.lastCellNum).map { c -> 
                row.getCell(c)?.let { getFormattedCellValue(it) } ?: ""
            }
        }
        rowsData.forEachIndexed { newPos, (_, _) ->
            val targetRowIdx = newPos + 1
            val targetRow = sheet.getRow(targetRowIdx) ?: sheet.createRow(targetRowIdx)
            val srcValues = snapshotValues[newPos]
            srcValues.forEachIndexed { c, value ->
                val cell = targetRow.getCell(c) ?: targetRow.createCell(c)
                cell.setCellValue(value)
            }
        }
        refreshState()
    }

    private fun refreshState() {
        val wb = activeWorkbook ?: return
        val filePath = activeFilePath ?: return
        val updatedWb = parseWorkbook(wb)
        _loadState.value = XlsxLoadState.Success(updatedWb, File(filePath).name)
    }

    /**
     * Commits workbook modifications back to the disk.
     */
    fun commitChanges() {
        viewModelScope.launch {
            val wb = activeWorkbook
            val filePath = activeFilePath
            if (wb == null || filePath == null) {
                _saveStatus.emit("No active spreadsheet loaded.")
                return@launch
            }

            withContext(Dispatchers.IO) {
                if (filePath.endsWith(".csv", ignoreCase = true)) {
                    try {
                        val sheet = wb.getSheetAt(0)
                        val stringBuilder = StringBuilder()
                        for (r in 0..sheet.lastRowNum) {
                            val row = sheet.getRow(r)
                            val rowCells = mutableListOf<String>()
                            if (row != null) {
                                for (c in 0 until row.lastCellNum) {
                                    val cell = row.getCell(c)
                                    val valStr = if (cell != null) getFormattedCellValue(cell) else ""
                                    val escaped = if (valStr.contains(",") || valStr.contains("\n") || valStr.contains("\"")) {
                                        "\"" + valStr.replace("\"", "\"\"") + "\""
                                    } else {
                                        valStr
                                    }
                                    rowCells.add(escaped)
                                }
                            }
                            stringBuilder.append(rowCells.joinToString(",")).append("\n")
                        }
                        File(filePath).writeText(stringBuilder.toString())
                        _saveStatus.emit("CSV changes committed successfully!")
                    } catch (e: Exception) {
                        e.printStackTrace()
                        _saveStatus.emit("Failed to save CSV: ${e.localizedMessage}")
                    }
                } else {
                    var fileOutputStream: java.io.FileOutputStream? = null
                    try {
                        fileOutputStream = java.io.FileOutputStream(File(filePath))
                        wb.write(fileOutputStream)
                        _saveStatus.emit("Spreadsheet changes committed successfully!")
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
    }

    /**
     * Converts the current active Excel workbook directly to PDF and writes it to a SAF URI.
     */
    fun exportToPdf(
        context: Context,
        outputUri: Uri,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch {
            val xlsxPath = activeFilePath
            if (xlsxPath == null) {
                onFailure("No active spreadsheet loaded.")
                return@launch
            }
            withContext(Dispatchers.IO) {
                val tempPdfFile = File(context.cacheDir, "temp_export_${System.currentTimeMillis()}.pdf")
                try {
                    OfficeConverter.convertXlsxToPdf(context, File(xlsxPath), tempPdfFile)
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
                val results = DocumentSearchEngine.searchXlsx(path, query)
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
            activeWorkbook?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Resolves and formats cell values based on active POI Cell types.
     */
    private fun getFormattedCellValue(cell: Cell): String {
        return try {
            when (cell.cellType) {
                CellType.STRING -> cell.stringCellValue ?: ""
                CellType.NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        try {
                            val dataFormatter = org.apache.poi.ss.usermodel.DataFormatter()
                            dataFormatter.formatCellValue(cell)
                        } catch (e: Exception) {
                            cell.dateCellValue?.toString() ?: ""
                        }
                    } else {
                        val numeric = cell.numericCellValue
                        if (numeric == numeric.toLong().toDouble()) {
                            numeric.toLong().toString()
                        } else {
                            numeric.toString()
                        }
                    }
                }
                CellType.BOOLEAN -> cell.booleanCellValue.toString()
                CellType.FORMULA -> {
                    try {
                        val evaluator = cell.sheet.workbook.creationHelper.createFormulaEvaluator()
                        val cv = evaluator.evaluate(cell)
                        when (cv.cellType) {
                            CellType.NUMERIC -> {
                                if (DateUtil.isCellDateFormatted(cell)) {
                                    val dataFormatter = org.apache.poi.ss.usermodel.DataFormatter()
                                    dataFormatter.formatCellValue(cell, evaluator)
                                } else {
                                    val numeric = cv.numberValue
                                    if (numeric == numeric.toLong().toDouble()) {
                                        numeric.toLong().toString()
                                    } else {
                                        numeric.toString()
                                    }
                                }
                            }
                            CellType.STRING -> cv.stringValue ?: ""
                            CellType.BOOLEAN -> cv.booleanValue.toString()
                            else -> cell.cellFormula ?: ""
                        }
                    } catch (e: Exception) {
                        try {
                            cell.stringCellValue ?: ""
                        } catch (e2: Exception) {
                            try {
                                cell.numericCellValue.toString()
                            } catch (e3: Exception) {
                                cell.cellFormula ?: ""
                            }
                        }
                    }
                }
                CellType.BLANK -> ""
                else -> cell.toString()
            }
        } catch (e: Exception) {
            ""
        }
    }
}
