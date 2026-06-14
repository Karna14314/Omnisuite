package com.karnadigital.omnisuite.feature.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.core.repository.RecentFileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

sealed class TxtLoadState {
    object Loading : TxtLoadState()
    data class Success(val content: String, val fileName: String) : TxtLoadState()
    data class Error(val message: String) : TxtLoadState()
}

@HiltViewModel
class TxtViewerViewModel @Inject constructor(
    private val recentFileRepository: RecentFileRepository
) : ViewModel() {

    private val _loadState = MutableStateFlow<TxtLoadState>(TxtLoadState.Loading)
    val loadState: StateFlow<TxtLoadState> = _loadState.asStateFlow()

    // SharedFlow to trigger one-time UI events like snackbars
    private val _saveStatus = MutableSharedFlow<Boolean>()
    val saveStatus: SharedFlow<Boolean> = _saveStatus.asSharedFlow()

    private var currentFile: File? = null

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Int>>(emptyList())
    val searchResults: StateFlow<List<Int>> = _searchResults.asStateFlow()

    private val _currentMatchIndex = MutableStateFlow(-1)
    val currentMatchIndex: StateFlow<Int> = _currentMatchIndex.asStateFlow()

    /**
     * Safely reads the text file content inside Dispatchers.IO scope using Kotlin buffer streams.
     */
    fun loadTextFile(filePath: String) {
        viewModelScope.launch {
            _loadState.value = TxtLoadState.Loading
            withContext(Dispatchers.IO) {
                try {
                    val file = File(filePath)
                    if (!file.exists() || !file.isFile) {
                        _loadState.value = TxtLoadState.Error("Target text file does not exist or is invalid.")
                        return@withContext
                    }
                    currentFile = file

                    // Safe Kotlin stream buffering
                    val content = file.bufferedReader().use { it.readText() }


                    _loadState.value = TxtLoadState.Success(
                        content = content,
                        fileName = file.name
                    )

                } catch (e: Exception) {
                    e.printStackTrace()
                    _loadState.value = TxtLoadState.Error("Failed to read file: ${e.localizedMessage}")
                }
            }
        }
    }

    /**
     * Writes edited content back to the local file descriptor safely on an IO thread.
     */
    fun saveTextFile(content: String) {
        val file = currentFile ?: return
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    // Safe stream write
                    file.bufferedWriter().use { it.write(content) }


                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
            _saveStatus.emit(success)
        }
    }

    fun setSearchQuery(query: String, content: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _currentMatchIndex.value = -1
            return
        }
        val matches = mutableListOf<Int>()
        var idx = content.indexOf(query, ignoreCase = true)
        while (idx >= 0) {
            matches.add(idx)
            idx = content.indexOf(query, idx + 1, ignoreCase = true)
        }
        _searchResults.value = matches
        if (matches.isNotEmpty()) {
            _currentMatchIndex.value = 0
        } else {
            _currentMatchIndex.value = -1
        }
    }

    fun nextMatch() {
        val matches = _searchResults.value
        if (matches.isEmpty()) return
        _currentMatchIndex.value = (_currentMatchIndex.value + 1) % matches.size
    }

    fun prevMatch() {
        val matches = _searchResults.value
        if (matches.isEmpty()) return
        _currentMatchIndex.value = (_currentMatchIndex.value - 1 + matches.size) % matches.size
    }
}
