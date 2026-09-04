package com.karnadigital.omnisuite.feature.imposition

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karnadigital.omnisuite.core.engine.imposition.*
import com.karnadigital.omnisuite.core.util.FileOutputManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class ImpositionUiState(
    val fileUri: Uri? = null,
    val fileName: String = "",
    val pageCount: Int = 0,
    val sourceWidthPt: Float = 595.28f,
    val sourceHeightPt: Float = 841.89f,
    val config: ImpositionConfig = ImpositionConfig(),
    val calculatedSheets: List<SheetLayout> = emptyList(),
    val isExporting: Boolean = false,
    val exportProgressMessage: String = "",
    val exportedFile: File? = null,
    val exportedUri: Uri? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class PrintImpositionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileOutputManager: FileOutputManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImpositionUiState())
    val uiState: StateFlow<ImpositionUiState> = _uiState.asStateFlow()

    fun setSelectedFile(uri: Uri, name: String) {
        viewModelScope.launch {
            try {
                var count = 0
                var width = 595.28f
                var height = 841.89f

                withContext(Dispatchers.IO) {
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    pfd?.use { descriptor ->
                        val renderer = PdfRenderer(descriptor)
                        count = renderer.pageCount
                        if (count > 0) {
                            val firstPage = renderer.openPage(0)
                            width = firstPage.width.toFloat()
                            height = firstPage.height.toFloat()
                            firstPage.close()
                        }
                        renderer.close()
                    }
                }

                _uiState.update { state ->
                    val updatedState = state.copy(
                        fileUri = uri,
                        fileName = name,
                        pageCount = count,
                        sourceWidthPt = width,
                        sourceHeightPt = height,
                        errorMessage = null
                    )
                    val sheets = ImpositionEngine.calculateLayout(
                        pageCount = count,
                        sourceWidthPt = width,
                        sourceHeightPt = height,
                        config = updatedState.config
                    )
                    updatedState.copy(calculatedSheets = sheets)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to load PDF metadata: ${e.message}") }
            }
        }
    }

    fun updateConfig(update: (ImpositionConfig) -> ImpositionConfig) {
        _uiState.update { state ->
            val newConfig = update(state.config)
            val sheets = ImpositionEngine.calculateLayout(
                pageCount = state.pageCount,
                sourceWidthPt = state.sourceWidthPt,
                sourceHeightPt = state.sourceHeightPt,
                config = newConfig
            )
            state.copy(config = newConfig, calculatedSheets = sheets)
        }
    }

    fun exportImposedPdf() {
        val state = _uiState.value
        val uri = state.fileUri ?: return
        if (state.calculatedSheets.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportProgressMessage = "Generating imposed PDF sheets...", errorMessage = null) }
            try {
                val tempOut = File(context.cacheDir, "imposed_output_${System.currentTimeMillis()}.pdf")
                val exported = ImpositionPdfExporter.exportImposedPdf(
                    context = context,
                    inputUri = uri,
                    sheetLayouts = state.calculatedSheets,
                    outputFile = tempOut
                )

                // Save to public Documents/OmniSuite/Print_Studio folder via FileOutputManager
                val outName = "Imposed_${state.fileName.ifBlank { "document" }}"
                val savedUri = fileOutputManager.saveFileToDefault(
                    file = exported,
                    filename = if (outName.endsWith(".pdf")) outName else "$outName.pdf",
                    mimeType = "application/pdf",
                    subfolder = "Print_Studio"
                )

                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportedFile = exported,
                        exportedUri = savedUri,
                        exportProgressMessage = "Export complete!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        errorMessage = "Export failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearExportResult() {
        _uiState.update { it.copy(exportedFile = null, errorMessage = null) }
    }
}
