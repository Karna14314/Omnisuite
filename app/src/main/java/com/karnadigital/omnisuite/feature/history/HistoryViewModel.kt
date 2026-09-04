package com.karnadigital.omnisuite.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.core.repository.RecentFileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State representation for the History listing.
 */
sealed interface HistoryUiState {
    object Loading : HistoryUiState
    object Empty : HistoryUiState
    data class Success(val items: List<RecentFile>) : HistoryUiState
}

/**
 * ViewModel coordinating presentation and actions for the History dashboard.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: RecentFileRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _activeTab = MutableStateFlow(0) // 0: Opened Documents, 1: Operations Done
    val activeTab: StateFlow<Int> = _activeTab

    private val _subFilter = MutableStateFlow("all") // "all", "documents", "images"
    val subFilter: StateFlow<String> = _subFilter

    /**
     * Search-filtered reactive state stream pulling historical logs from Room database.
     */
    val uiState: StateFlow<HistoryUiState> = combine(
        repository.recentFiles,
        _searchQuery,
        _activeTab,
        _subFilter
    ) { files, query, tab, subFilter ->
        val filteredByTab = files.filter { file ->
            if (tab == 0) {
                !file.isOperation
            } else {
                file.isOperation
            }
        }

        val filteredBySub = filteredByTab.filter { file ->
            if (tab == 0) {
                when (subFilter) {
                    "documents" -> {
                        !file.mimeType.startsWith("image/", ignoreCase = true) && !file.mimeType.contains("image", ignoreCase = true)
                    }
                    "images" -> {
                        file.mimeType.startsWith("image/", ignoreCase = true) || file.mimeType.contains("image", ignoreCase = true)
                    }
                    else -> true
                }
            } else {
                true
            }
        }

        val filtered = if (query.isBlank()) {
            filteredBySub
        } else {
            filteredBySub.filter {
                it.fileName.contains(query, ignoreCase = true) ||
                it.mimeType.contains(query, ignoreCase = true) ||
                it.fileUri.contains(query, ignoreCase = true)
            }
        }

        if (filtered.isEmpty()) {
            HistoryUiState.Empty
        } else {
            HistoryUiState.Success(filtered)
        }
    }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState.Loading
    )

    /**
     * Updates the active historical search query filter.
     */
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun setActiveTab(tab: Int) {
        _activeTab.value = tab
    }

    fun setSubFilter(filter: String) {
        _subFilter.value = filter
    }

    /**
     * Renames a specific historical file entry.
     */
    fun renameItem(item: RecentFile, newName: String) {
        viewModelScope.launch {
            repository.renameFile(item.fileUri, newName)
        }
    }

    /**
     * Deletes a specific historical record.
     */
    fun deleteItem(item: RecentFile) {
        viewModelScope.launch {
            repository.deleteRecentFile(item)
        }
    }

    /**
     * Clears all database historical logs offline.
     */
    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAllRecentFiles()
        }
    }
}
