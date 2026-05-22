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
     * Registers a successfully generated QR code log into the SQLite persistence layer.
     */
    fun logQrCodeGeneration(content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            repository.insertRecentFile(
                RecentFile(
                    fileUri = content,
                    fileName = "Generated QR: ${content.take(30)}${if (content.length > 30) "..." else ""}",
                    mimeType = "application/x-qrcode",
                    fileSize = content.length.toLong(),
                    lastOpened = System.currentTimeMillis()
                )
            )
        }
    }
}
