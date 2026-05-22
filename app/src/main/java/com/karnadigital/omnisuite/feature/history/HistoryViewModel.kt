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

    /**
     * Search-filtered reactive state stream pulling historical logs from Room database.
     */
    val uiState: StateFlow<HistoryUiState> = combine(
        repository.recentFiles,
        _searchQuery
    ) { files, query ->
        val filtered = if (query.isBlank()) {
            files
        } else {
            files.filter {
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
