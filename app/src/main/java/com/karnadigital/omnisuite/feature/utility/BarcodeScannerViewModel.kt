package com.karnadigital.omnisuite.feature.utility

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.core.repository.RecentFileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel managing real-time scanner operations and persisting barcode scan results.
 */
@HiltViewModel
class BarcodeScannerViewModel @Inject constructor(
    private val repository: RecentFileRepository
) : ViewModel() {

    /**
     * Registers a scanned barcode/QR payload into the SQLite Room persistence history logs.
     */
    fun logScannedBarcode(barcode: String) {
        if (barcode.isBlank()) return
        viewModelScope.launch {
            repository.insertRecentFile(
                RecentFile(
                    fileUri = barcode,
                    fileName = "Scanned Barcode: ${barcode.take(30)}${if (barcode.length > 30) "..." else ""}",
                    mimeType = "application/x-barcode",
                    fileSize = barcode.length.toLong(),
                    lastOpened = System.currentTimeMillis()
                )
            )
        }
    }
}
