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

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import com.karnadigital.omnisuite.core.engine.EncodingDetector

sealed class TxtLoadState {
    object Loading : TxtLoadState()
    data class Success(val content: String, val fileName: String) : TxtLoadState()
    data class Error(val message: String) : TxtLoadState()
}

@HiltViewModel
class TxtViewerViewModel internal constructor(
    private val recentFileRepository: RecentFileRepository,
    private val context: Context?
) : ViewModel() {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        recentFileRepository: RecentFileRepository
    ) : this(recentFileRepository, context)

    // Overload for testing without an Android environment
    constructor(recentFileRepository: RecentFileRepository) : this(recentFileRepository, null)

    private val _loadState = MutableStateFlow<TxtLoadState>(TxtLoadState.Loading)
    val loadState: StateFlow<TxtLoadState> = _loadState.asStateFlow()

    private val _saveStatus = MutableSharedFlow<Boolean>()
    val saveStatus: SharedFlow<Boolean> = _saveStatus.asSharedFlow()

    private var currentFile: File? = null

    private val maxTextFileSize = 50L * 1024 * 1024

    // Undo / Redo Stacks for Text Document State
    private val undoStack = java.util.ArrayDeque<String>()
    private val redoStack = java.util.ArrayDeque<String>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    fun updateContent(newContent: String) {
        val current = (_loadState.value as? TxtLoadState.Success)?.content
        if (current != null && current != newContent) {
            undoStack.push(current)
            if (undoStack.size > 50) undoStack.removeLast()
            redoStack.clear()
            _canUndo.value = undoStack.isNotEmpty()
            _canRedo.value = false
        }
        val name = currentFile?.name ?: "Document.txt"
        _loadState.value = TxtLoadState.Success(content = newContent, fileName = name)
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = (_loadState.value as? TxtLoadState.Success)?.content ?: return
        redoStack.push(current)
        val previous = undoStack.pop()
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        val name = currentFile?.name ?: "Document.txt"
        _loadState.value = TxtLoadState.Success(content = previous, fileName = name)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = (_loadState.value as? TxtLoadState.Success)?.content ?: return
        undoStack.push(current)
        val next = redoStack.pop()
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        val name = currentFile?.name ?: "Document.txt"
        _loadState.value = TxtLoadState.Success(content = next, fileName = name)
    }

    fun loadTextFile(filePath: String) {
        viewModelScope.launch {
            _loadState.value = TxtLoadState.Loading
            withContext(Dispatchers.IO) {
                try {
                    var content: String? = null
                    var name = "Document.txt"

                    val isContentUri = filePath.startsWith("content://")
                    val isFileUri = filePath.startsWith("file://")

                    if (isContentUri) {
                        val uri = Uri.parse(filePath)
                        context?.contentResolver?.openInputStream(uri)?.use { stream ->
                            content = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        }
                        name = uri.lastPathSegment?.substringAfterLast('/') ?: "Document.txt"
                    } else {
                        val cleanPath = if (isFileUri) filePath.removePrefix("file://") else filePath
                        val file = File(cleanPath)
                        if (file.exists() && file.isFile) {
                            currentFile = file
                            name = file.name
                            if (file.length() > maxTextFileSize) {
                                _loadState.value = TxtLoadState.Error("File is too large to edit (${file.length() / (1024 * 1024)} MB).")
                                return@withContext
                            }
                            content = try {
                                val encoding = EncodingDetector.detectEncoding(file)
                                EncodingDetector.readTextWithEncoding(file, encoding.charset)
                            } catch (_: Throwable) {
                                file.bufferedReader(Charsets.UTF_8).use { it.readText() }
                            }
                        }
                    }

                    if (content != null) {
                        _loadState.value = TxtLoadState.Success(content = content!!, fileName = name)
                    } else {
                        _loadState.value = TxtLoadState.Error("Target text file could not be opened or is empty.")
                    }
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
                    recentFileRepository.insertRecentFile(
                        RecentFile(
                            fileUri = android.net.Uri.fromFile(file).toString(),
                            fileName = file.name,
                            mimeType = "text/plain",
                            fileSize = file.length(),
                            lastOpened = System.currentTimeMillis(),
                            isOperation = true
                        )
                    )
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
