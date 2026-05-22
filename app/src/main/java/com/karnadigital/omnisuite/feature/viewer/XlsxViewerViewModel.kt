package com.karnadigital.omnisuite.feature.viewer

import android.content.Context
import android.net.Uri
import com.karnadigital.omnisuite.core.engine.document.OfficeConverter
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

data class ExcelSheet(val name: String, val rows: List<List<String>>)
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

                    fileInputStream = FileInputStream(file)
                    workbook = XSSFWorkbook(fileInputStream)

                    val parsedWb = parseWorkbook(workbook)

                    // Update RecentFiles DB offline logger
                    try {
                        val recentFile = RecentFile(
                            fileUri = file.absolutePath,
                            fileName = file.name,
                            mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            fileSize = file.length(),
                            lastOpened = System.currentTimeMillis()
                        )
                        recentFileRepository.insertRecentFile(recentFile)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

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
                    _loadState.value = XlsxLoadState.Error("Apache POI parser failure: ${e.localizedMessage}")
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

    private fun parseWorkbook(wb: XSSFWorkbook): ExcelWorkbook {
        val sheetList = mutableListOf<ExcelSheet>()
        val numberOfSheets = wb.numberOfSheets

        for (s in 0 until numberOfSheets) {
            val sheet = wb.getSheetAt(s)
            val sheetName = sheet.sheetName ?: "Sheet ${s + 1}"
            val rowList = mutableListOf<List<String>>()

            // Track maximum columns to normalize grid headers
            var maxCols = 0
            val rawRows = mutableListOf<Row>()
            
            val lastRowNum = sheet.lastRowNum
            for (r in 0..lastRowNum) {
                val row = sheet.getRow(r)
                if (row != null) {
                    rawRows.add(row)
                    val lastCellNum = row.lastCellNum.toInt()
                    if (lastCellNum > maxCols) {
                        maxCols = lastCellNum
                     }
                } else {
                    // Add blank indicator row reference to keep spacing integrity
                    rawRows.add(sheet.createRow(r)) 
                }
            }

            // Normalize grid rows to equal length
            for (row in rawRows) {
                val rowCells = mutableListOf<String>()
                for (c in 0 until maxCols) {
                    val cell = row.getCell(c)
                    if (cell == null) {
                        rowCells.add("")
                    } else {
                        rowCells.add(getFormattedCellValue(cell))
                    }
                }
                rowList.add(rowCells)
            }

            sheetList.add(ExcelSheet(sheetName, rowList))
        }
        return ExcelWorkbook(sheetList)
    }

    /**
     * Updates an active Excel cell, converting double values safely, and
     * re-running formulas evaluations downstream instantly.
     */
    fun updateCell(sheetIndex: Int, rowIndex: Int, colIndex: Int, valueString: String) {
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

        val doubleValue = valueString.toDoubleOrNull()
        if (doubleValue != null) {
            cell.setCellValue(doubleValue)
        } else {
            cell.setCellValue(valueString)
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

    override fun onCleared() {
        super.onCleared()
        try {
            activeWorkbook?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
                        cell.dateCellValue?.toString() ?: ""
                    } else {
                        val numeric = cell.numericCellValue
                        // Avoid displaying integers with dynamic decimals (e.g. 10.0 instead of 10)
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
                        // Attempt formula evaluation string extraction
                        cell.stringCellValue ?: ""
                    } catch (e: Exception) {
                        try {
                            cell.numericCellValue.toString()
                        } catch (e2: Exception) {
                            cell.cellFormula ?: ""
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
