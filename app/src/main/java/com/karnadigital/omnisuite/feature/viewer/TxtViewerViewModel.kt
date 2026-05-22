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

                    // Log this file opening to our Room RecentFiles history
                    try {
                        val recentFile = RecentFile(
                            fileUri = file.absolutePath,
                            fileName = file.name,
                            mimeType = "text/plain",
                            fileSize = file.length(),
                            lastOpened = System.currentTimeMillis()
                        )
                        recentFileRepository.insertRecentFile(recentFile)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

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

                    // Update RecentFiles size/time metrics in Room DB
                    try {
                        val recentFile = RecentFile(
                            fileUri = file.absolutePath,
                            fileName = file.name,
                            mimeType = "text/plain",
                            fileSize = file.length(),
                            lastOpened = System.currentTimeMillis()
                        )
                        recentFileRepository.insertRecentFile(recentFile)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
            _saveStatus.emit(success)
        }
    }
}
