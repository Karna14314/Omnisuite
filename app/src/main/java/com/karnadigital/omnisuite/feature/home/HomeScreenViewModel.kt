package com.karnadigital.omnisuite.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.core.repository.RecentFileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State representation for the Recent Files listing.
 */
sealed interface RecentFilesUiState {
    object Loading : RecentFilesUiState
    object Empty : RecentFilesUiState
    data class Success(val files: List<RecentFile>) : RecentFilesUiState
}

/**
 * ViewModel coordinates UI state and actions for the OmniSuite dashboard.
 */
@HiltViewModel
class HomeScreenViewModel @Inject constructor(
    private val repository: RecentFileRepository
) : ViewModel() {

    /**
     * Reactive state stream pulling recently opened files from Room.
     */
    val uiState: StateFlow<RecentFilesUiState> = repository.recentFiles
        .map { files ->
            if (files.isEmpty()) {
                RecentFilesUiState.Empty
            } else {
                RecentFilesUiState.Success(files)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = RecentFilesUiState.Loading
        )

    /**
     * Adds a file entry or updates its access timestamp inside the database cache.
     */
    fun addRecentFile(fileUri: String, fileName: String, mimeType: String, fileSize: Long) {
        viewModelScope.launch {
            repository.insertRecentFile(
                RecentFile(
                    fileUri = fileUri,
                    fileName = fileName,
                    mimeType = mimeType,
                    fileSize = fileSize,
                    lastOpened = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Dispatches recent files clear operations.
     */
    fun clearRecents() {
        viewModelScope.launch {
            repository.clearAllRecentFiles()
        }
    }
}
