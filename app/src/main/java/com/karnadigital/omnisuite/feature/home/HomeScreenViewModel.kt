package com.karnadigital.omnisuite.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.core.repository.RecentFileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HistoryFilter(val displayName: String) {
    ALL("All"),
    DOCUMENTS("Documents"),
    TOOLS("Tool Exports"),
    MEDIA("Media")
}

/**
 * State representation for the Recent Files listing.
 */
sealed interface RecentFilesUiState {
    object Loading : RecentFilesUiState
    object Empty : RecentFilesUiState
    data class Success(
        val allFiles: List<RecentFile>,
        val filteredFiles: List<RecentFile>,
        val currentFilter: HistoryFilter,
        val searchQuery: String
    ) : RecentFilesUiState
}

/**
 * ViewModel coordinates UI state and actions for the OmniSuite dashboard.
 */
@HiltViewModel
class HomeScreenViewModel @Inject constructor(
    private val repository: RecentFileRepository
) : ViewModel() {

    private val _activeFilter = MutableStateFlow(HistoryFilter.ALL)
    val activeFilter: StateFlow<HistoryFilter> = _activeFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * Reactive state stream pulling recently opened files from Room and applying current filters.
     */
    val uiState: StateFlow<RecentFilesUiState> = combine(
        repository.recentFiles,
        _activeFilter,
        _searchQuery
    ) { files, filter, query ->
        if (files.isEmpty()) {
            RecentFilesUiState.Empty
        } else {
            val filtered = files.filter { file ->
                val matchesFilter = when (filter) {
                    HistoryFilter.ALL -> true
                    HistoryFilter.DOCUMENTS -> {
                        val mime = file.mimeType.lowercase()
                        !file.isOperation && (
                            mime.contains("pdf") ||
                            mime.contains("word") ||
                            mime.contains("document") ||
                            mime.contains("sheet") ||
                            mime.contains("excel") ||
                            mime.contains("presentation") ||
                            mime.contains("powerpoint") ||
                            mime.contains("text") ||
                            mime.contains("csv") ||
                            file.fileName.endsWith(".pdf", ignoreCase = true) ||
                            file.fileName.endsWith(".docx", ignoreCase = true) ||
                            file.fileName.endsWith(".xlsx", ignoreCase = true) ||
                            file.fileName.endsWith(".pptx", ignoreCase = true) ||
                            file.fileName.endsWith(".txt", ignoreCase = true)
                        )
                    }
                    HistoryFilter.TOOLS -> {
                        file.isOperation ||
                        file.mimeType.contains("barcode") ||
                        file.mimeType.contains("qrcode") ||
                        file.fileName.startsWith("stitched_", ignoreCase = true) ||
                        file.fileName.startsWith("id_template_", ignoreCase = true) ||
                        file.fileName.startsWith("watermarked_", ignoreCase = true) ||
                        file.fileName.startsWith("compressed_", ignoreCase = true) ||
                        file.fileName.startsWith("resized_", ignoreCase = true) ||
                        file.fileName.startsWith("transcoded_", ignoreCase = true) ||
                        file.fileName.startsWith("merged_", ignoreCase = true) ||
                        file.fileName.startsWith("split_", ignoreCase = true) ||
                        file.fileName.startsWith("encrypted_", ignoreCase = true) ||
                        file.fileName.startsWith("decrypted_", ignoreCase = true) ||
                        file.fileName.startsWith("signed_", ignoreCase = true) ||
                        file.fileName.startsWith("converted_", ignoreCase = true) ||
                        file.fileName.startsWith("images_compiled", ignoreCase = true)
                    }
                    HistoryFilter.MEDIA -> {
                        file.mimeType.startsWith("image/", ignoreCase = true) ||
                        file.mimeType.startsWith("video/", ignoreCase = true) ||
                        file.fileUri.contains("|||") ||
                        file.fileName.endsWith(".jpg", ignoreCase = true) ||
                        file.fileName.endsWith(".png", ignoreCase = true) ||
                        file.fileName.endsWith(".webp", ignoreCase = true)
                    }
                }
                val matchesQuery = query.isBlank() ||
                    file.fileName.contains(query, ignoreCase = true) ||
                    file.mimeType.contains(query, ignoreCase = true)
                matchesFilter && matchesQuery
            }
            RecentFilesUiState.Success(
                allFiles = files,
                filteredFiles = filtered,
                currentFilter = filter,
                searchQuery = query
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RecentFilesUiState.Loading
    )

    fun setFilter(filter: HistoryFilter) {
        _activeFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Adds a file entry or updates its access timestamp inside the database cache.
     */
    fun addRecentFile(fileUri: String, fileName: String, mimeType: String, fileSize: Long, isOperation: Boolean = false) {
        viewModelScope.launch {
            repository.insertRecentFile(
                RecentFile(
                    fileUri = fileUri,
                    fileName = fileName,
                    mimeType = mimeType,
                    fileSize = fileSize,
                    lastOpened = System.currentTimeMillis(),
                    isOperation = isOperation
                )
            )
        }
    }

    /**
     * Deletes an individual history file.
     */
    fun deleteRecentFile(file: RecentFile) {
        viewModelScope.launch {
            repository.deleteRecentFile(file)
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
