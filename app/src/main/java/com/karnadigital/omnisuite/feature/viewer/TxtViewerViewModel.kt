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
    private var currentUri: Uri? = null

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
        val name = (_loadState.value as? TxtLoadState.Success)?.fileName ?: currentFile?.name ?: "Document.txt"
        _loadState.value = TxtLoadState.Success(content = newContent, fileName = name)
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = (_loadState.value as? TxtLoadState.Success)?.content ?: return
        redoStack.push(current)
        val previous = undoStack.pop()
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        val name = (_loadState.value as? TxtLoadState.Success)?.fileName ?: currentFile?.name ?: "Document.txt"
        _loadState.value = TxtLoadState.Success(content = previous, fileName = name)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = (_loadState.value as? TxtLoadState.Success)?.content ?: return
        undoStack.push(current)
        val next = redoStack.pop()
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        val name = (_loadState.value as? TxtLoadState.Success)?.fileName ?: currentFile?.name ?: "Document.txt"
        _loadState.value = TxtLoadState.Success(content = next, fileName = name)
    }

    private fun getDisplayNameFromUri(uri: Uri): String? {
        if (context == null) return null
        return try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun loadTextFile(filePath: String, originalUriString: String? = null) {
        viewModelScope.launch {
            _loadState.value = TxtLoadState.Loading
            withContext(Dispatchers.IO) {
                try {
                    var content: String? = null
                    var name = "Document.txt"

                    val isContentUri = filePath.startsWith("content://")
                    val isFileUri = filePath.startsWith("file://")

                    if (originalUriString?.startsWith("content://") == true) {
                        currentUri = Uri.parse(originalUriString)
                    }

                    if (isContentUri) {
                        val uri = Uri.parse(filePath)
                        currentUri = uri
                        context?.contentResolver?.openInputStream(uri)?.use { stream ->
                            val bytes = stream.readBytes()
                            val encoding = try {
                                EncodingDetector.detectEncoding(bytes)
                            } catch (_: Throwable) {
                                null
                            }
                            val charset = encoding?.charset ?: Charsets.UTF_8
                            content = String(bytes, charset)
                        }
                        name = getDisplayNameFromUri(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Document.txt"
                    } else {
                        val cleanPath = if (isFileUri) filePath.removePrefix("file://") else filePath
                        val file = File(cleanPath)
                        if (file.exists() && file.isFile) {
                            currentFile = file
                            name = file.name
                            if (currentUri != null) {
                                val originalDisplayName = getDisplayNameFromUri(currentUri!!)
                                if (originalDisplayName != null) name = originalDisplayName
                            }
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

    fun saveTextFile(content: String, encoding: String = "UTF-8") {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val charset = try {
                        java.nio.charset.Charset.forName(encoding)
                    } catch (_: Exception) {
                        Charsets.UTF_8
                    }

                    var savedAtLeastOnce = false

                    // 1. If local cached file exists, update it
                    val file = currentFile
                    if (file != null) {
                        file.bufferedWriter(charset).use { it.write(content) }
                        savedAtLeastOnce = true
                    }

                    // 2. If original/current Uri is a content:// URI, stream back to SAF
                    val uri = currentUri
                    if (uri != null && uri.scheme == "content" && context != null) {
                        try {
                            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                                out.write(content.toByteArray(charset))
                                out.flush()
                            }
                            savedAtLeastOnce = true
                        } catch (e: Exception) {
                            try {
                                context.contentResolver.openOutputStream(uri, "w")?.use { out ->
                                    out.write(content.toByteArray(charset))
                                    out.flush()
                                }
                                savedAtLeastOnce = true
                            } catch (e2: Exception) {
                                e2.printStackTrace()
                            }
                        }
                    }

                    if (savedAtLeastOnce) {
                        val recordUri = uri?.toString() ?: file?.let { Uri.fromFile(it).toString() } ?: ""
                        val recordName = (_loadState.value as? TxtLoadState.Success)?.fileName ?: file?.name ?: "Document.txt"
                        val recordSize = file?.length() ?: content.toByteArray(charset).size.toLong()
                        recentFileRepository.insertRecentFile(
                            RecentFile(
                                fileUri = recordUri,
                                fileName = recordName,
                                mimeType = "text/plain",
                                fileSize = recordSize,
                                lastOpened = System.currentTimeMillis(),
                                isOperation = true
                            )
                        )
                    }

                    savedAtLeastOnce
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
