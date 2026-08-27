package com.karnadigital.omnisuite.feature.utility

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.core.repository.RecentFileRepository
import com.karnadigital.omnisuite.core.util.FileOutputManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel managing real-time scanner operations and persisting barcode scan results.
 */
@HiltViewModel
class BarcodeScannerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: RecentFileRepository,
    private val fileOutputManager: FileOutputManager
) : ViewModel() {

    var scanResultFileUri by mutableStateOf<Uri?>(null)
    var scanResultFileName by mutableStateOf<String?>(null)
    var scanResultSize by mutableStateOf(0L)
    var scanResultText by mutableStateOf<String?>(null)
    var isSaving by mutableStateOf(false)

    /**
     * Registers a scanned barcode/QR payload into a physical file and Room persistence logs.
     */
    fun logScannedBarcode(barcode: String) {
        if (barcode.isBlank()) return
        scanResultText = barcode
        isSaving = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val bytes = barcode.toByteArray()
                    val fileName = "Scan_${System.currentTimeMillis()}.txt"
                    val savedUri = fileOutputManager.saveToDefault(
                        bytes = bytes,
                        filename = fileName,
                        mimeType = "text/plain",
                        subfolder = "Scanned"
                    )
                    if (savedUri != null) {
                        scanResultFileUri = savedUri
                        scanResultFileName = fileName
                        scanResultSize = bytes.size.toLong()
                        repository.insertRecentFile(
                            RecentFile(
                                fileUri = savedUri.toString(),
                                fileName = fileName,
                                mimeType = "text/plain",
                                fileSize = bytes.size.toLong(),
                                lastOpened = System.currentTimeMillis(),
                                isOperation = true
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isSaving = false
                }
            }
        }
    }

    fun clearResult() {
        scanResultText = null
        scanResultFileUri = null
        scanResultFileName = null
        scanResultSize = 0L
    }
}
