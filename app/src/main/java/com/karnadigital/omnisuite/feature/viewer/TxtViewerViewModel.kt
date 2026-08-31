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

    private val _saveStatus = MutableSharedFlow<Boolean>()
    val saveStatus: SharedFlow<Boolean> = _saveStatus.asSharedFlow()

    private var currentFile: File? = null

    private val maxTextFileSize = 50L * 1024 * 1024

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

                    if (file.length() > maxTextFileSize) {
                        _loadState.value = TxtLoadState.Error(
                            "File is too large to edit (${file.length() / (1024 * 1024)} MB). " +
                            "Maximum supported size is ${maxTextFileSize / (1024 * 1024)} MB."
                        )
                        return@withContext
                    }

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

    fun saveTextFile(content: String) {
        val file = currentFile ?: return
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
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

    fun emitSaveStatus(success: Boolean) {
        viewModelScope.launch {
            _saveStatus.emit(success)
        }
    }

    fun setSearchQuery(query: String, content: String) {
    }

    fun nextMatch() {
    }

    fun prevMatch() {
    }
}
