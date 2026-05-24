package com.karnadigital.omnisuite.feature.tools

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.core.repository.RecentFileRepository
import com.karnadigital.omnisuite.core.util.FileOutputManager
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
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

data class SelectedFile(
    val uri: Uri,
    val name: String,
    val size: Long
)

sealed class ZipMakerState {
    object Idle : ZipMakerState()
    object Compressing : ZipMakerState()
    data class Success(val savedUri: Uri, val fileName: String) : ZipMakerState()
    data class Error(val message: String) : ZipMakerState()
}

@HiltViewModel
class ZipMakerViewModel @Inject constructor(
    private val repository: RecentFileRepository
) : ViewModel() {

    private val _selectedFiles = MutableStateFlow<List<SelectedFile>>(emptyList())
    val selectedFiles: StateFlow<List<SelectedFile>> = _selectedFiles.asStateFlow()

    private val _zipState = MutableStateFlow<ZipMakerState>(ZipMakerState.Idle)
    val zipState: StateFlow<ZipMakerState> = _zipState.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    fun addFiles(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            val list = _selectedFiles.value.toMutableList()
            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    if (list.none { it.uri == uri }) {
                        val name = getFileName(context, uri) ?: "file_${System.currentTimeMillis()}"
                        val size = getFileSize(context, uri)
                        list.add(SelectedFile(uri, name, size))
                    }
                }
            }
            _selectedFiles.value = list
        }
    }

    fun removeFile(file: SelectedFile) {
        val list = _selectedFiles.value.toMutableList()
        list.remove(file)
        _selectedFiles.value = list
    }

    fun clearFiles() {
        _selectedFiles.value = emptyList()
        _zipState.value = ZipMakerState.Idle
    }

    fun compressFiles(context: Context, zipFileName: String) {
        if (_selectedFiles.value.isEmpty()) {
            _zipState.value = ZipMakerState.Error("No files selected to compress")
            return
        }
        val filename = if (zipFileName.lowercase().endsWith(".zip")) zipFileName else "$zipFileName.zip"

        _zipState.value = ZipMakerState.Compressing

        viewModelScope.launch {
            try {
                val tempZipFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.zip")
                var bytesLength = 0L
                val resultUri = withContext(Dispatchers.IO) {
                    ZipOutputStream(FileOutputStream(tempZipFile)).use { zos ->
                        _selectedFiles.value.forEach { selected ->
                            context.contentResolver.openInputStream(selected.uri)?.use { inputStream ->
                                val zipEntry = ZipEntry(selected.name)
                                zos.putNextEntry(zipEntry)
                                inputStream.copyTo(zos)
                                zos.closeEntry()
                            }
                        }
                    }

                    // Save output bytes to default storage path
                    val bytes = tempZipFile.readBytes()
                    bytesLength = bytes.size.toLong()
                    val uri = FileOutputManager.saveToDefault(
                        context = context,
                        bytes = bytes,
                        filename = filename,
                        mimeType = "application/zip",
                        subfolder = "Archives"
                    )
                    
                    if (tempZipFile.exists()) {
                        tempZipFile.delete()
                    }
                    uri
                }

                if (resultUri != null) {
                    _zipState.value = ZipMakerState.Success(resultUri, filename)
                    // Log to SQLite Recents
                    repository.insertRecentFile(
                        RecentFile(
                            fileUri = resultUri.toString(),
                            fileName = filename,
                            mimeType = "application/zip",
                            fileSize = bytesLength,
                            lastOpened = System.currentTimeMillis()
                        )
                    )
                    _toastMessage.emit("ZIP Archive created successfully!")
                    _selectedFiles.value = emptyList()
                } else {
                    _zipState.value = ZipMakerState.Error("Failed to write ZIP file to default storage")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _zipState.value = ZipMakerState.Error(e.localizedMessage ?: "Unknown compression error occurred")
            }
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) name = cursor.getString(index)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (name == null) {
            name = uri.path?.substringAfterLast('/')
        }
        return name
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        var size = 0L
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (index != -1) size = cursor.getLong(index)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return size
    }
}
