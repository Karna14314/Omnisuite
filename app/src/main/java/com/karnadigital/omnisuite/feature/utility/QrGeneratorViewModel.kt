package com.karnadigital.omnisuite.feature.utility

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.core.repository.RecentFileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel responsible for managing QR code generation actions and logging history offline.
 */
@HiltViewModel
class QrGeneratorViewModel @Inject constructor(
    private val repository: RecentFileRepository
) : ViewModel() {

    /**
     * Registers a successfully generated QR/Barcode file path log into the SQLite database.
     */
    fun logQrCodeGeneration(fileUri: String, fileName: String, fileSize: Long) {
        if (fileUri.isBlank()) return
        viewModelScope.launch {
            repository.insertRecentFile(
                RecentFile(
                    fileUri = fileUri,
                    fileName = fileName,
                    mimeType = "image/png",
                    fileSize = fileSize,
                    lastOpened = System.currentTimeMillis(),
                    isOperation = true
                )
            )
        }
    }
}
