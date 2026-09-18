package com.karnadigital.omnisuite.feature.home

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.core.repository.RecentFileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject

data class StorageVolumeInfo(
    val name: String,
    val path: String,
    val file: File,
    val isPrimary: Boolean,
    val totalSpace: Long,
    val freeSpace: Long,
    val type: String // "Internal" or "SD Card" or "USB"
)

enum class FileSortOrder {
    NAME_ASC, NAME_DESC, SIZE_ASC, SIZE_DESC, DATE_DESC, DATE_ASC
}

sealed interface CategoryState {
    object Idle : CategoryState
    object Loading : CategoryState
    data class Success(val files: List<File>) : CategoryState
    data class Error(val message: String) : CategoryState
}

@HiltViewModel
class FilesViewModel @Inject constructor(
    private val recentFileRepository: RecentFileRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // Storage permission state
    private val _hasStoragePermission = MutableStateFlow(false)
    val hasStoragePermission: StateFlow<Boolean> = _hasStoragePermission.asStateFlow()

    // Mounted storage volumes list
    private val _storageVolumes = MutableStateFlow<List<StorageVolumeInfo>>(emptyList())
    val storageVolumes: StateFlow<List<StorageVolumeInfo>> = _storageVolumes.asStateFlow()

    // Active Category state
    private val _activeCategory = MutableStateFlow<String?>(null) // "images", "videos", "audio", "documents", "archives"
    val activeCategory: StateFlow<String?> = _activeCategory.asStateFlow()

    // Active Document Sub-Tab filter
    private val _documentFilter = MutableStateFlow("all") // "all", "pdf", "word", "excel", "slides", "txt"
    val documentFilter: StateFlow<String> = _documentFilter.asStateFlow()

    // Category files list loading state
    private val _categoryState = MutableStateFlow<CategoryState>(CategoryState.Idle)
    val categoryState: StateFlow<CategoryState> = _categoryState.asStateFlow()

    // Current folder path navigation state (null = root dashboard view)
    private val _currentDirectoryPath = MutableStateFlow<String?>(null)
    val currentDirectoryPath: StateFlow<String?> = _currentDirectoryPath.asStateFlow()

    // Files list inside the current directory
    private val _currentDirectoryFiles = MutableStateFlow<List<File>>(emptyList())
    val currentDirectoryFiles: StateFlow<List<File>> = _currentDirectoryFiles.asStateFlow()

    // Sort order
    private val _sortOrder = MutableStateFlow(FileSortOrder.NAME_ASC)
    val sortOrder: StateFlow<FileSortOrder> = _sortOrder.asStateFlow()

    // Search query filter
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Recent files list mapped from repository
    val recentFiles: StateFlow<List<RecentFile>> = recentFileRepository.recentFiles
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        checkPermissionAndDiscover()
    }

    /**
     * Inspects permission state and refreshes storage drives.
     */
    fun checkPermissionAndDiscover() {
        val permissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
        _hasStoragePermission.value = permissionGranted
        
        viewModelScope.launch {
            discoverStorageVolumes()
            refreshCurrentDirectory()
            if (permissionGranted) {
                reloadActiveCategory()
            }
        }
    }

    /**
     * Offline discovery of SD cards and emulated partitions.
     */
    private suspend fun discoverStorageVolumes() = withContext(Dispatchers.IO) {
        val volumes = mutableListOf<StorageVolumeInfo>()
        try {
            // 1. Primary Internal Storage emulated root
            val primaryFile = Environment.getExternalStorageDirectory()
            val primaryTotal = primaryFile.totalSpace
            val primaryFree = primaryFile.freeSpace
            volumes.add(
                StorageVolumeInfo(
                    name = "Internal Storage",
                    path = primaryFile.absolutePath,
                    file = primaryFile,
                    isPrimary = true,
                    totalSpace = primaryTotal,
                    freeSpace = primaryFree,
                    type = "Internal"
                )
            )

            // 2. Discover secondary volumes (MicroSD Cards, USB drives)
            val externalDirs = context.getExternalFilesDirs(null)
            if (externalDirs != null) {
                for (dir in externalDirs) {
                    if (dir == null) continue
                    if (Environment.isExternalStorageEmulated(dir)) continue // Already added

                    val path = dir.absolutePath
                    val androidIndex = path.indexOf("/Android/")
                    if (androidIndex != -1) {
                        val rootPath = path.substring(0, androidIndex)
                        val rootFile = File(rootPath)
                        if (rootFile.exists() && rootFile.isDirectory) {
                            val volumeName = if (rootPath.contains("self") || rootPath.contains("emulated")) {
                                "Internal Storage"
                            } else {
                                val label = rootPath.substringAfterLast('/')
                                "SD Card ($label)"
                            }

                            if (volumes.none { it.path == rootPath }) {
                                volumes.add(
                                    StorageVolumeInfo(
                                        name = volumeName,
                                        path = rootPath,
                                        file = rootFile,
                                        isPrimary = false,
                                        totalSpace = rootFile.totalSpace,
                                        freeSpace = rootFile.freeSpace,
                                        type = "SD Card"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _storageVolumes.value = volumes
    }

    /**
     * Category selection toggles.
     */
    fun selectCategory(category: String?) {
        _activeCategory.value = category
        _documentFilter.value = "all"
        if (category != null) {
            _currentDirectoryPath.value = null // Close explorer when viewing category
            reloadActiveCategory()
        } else {
            _categoryState.value = CategoryState.Idle
        }
    }

    fun setDocumentFilter(filter: String) {
        _documentFilter.value = filter
        reloadActiveCategory()
    }

    fun setSortOrder(order: FileSortOrder) {
        _sortOrder.value = order
        viewModelScope.launch {
            refreshCurrentDirectory()
            reloadActiveCategory()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            refreshCurrentDirectory()
            reloadActiveCategory()
        }
    }

    /**
     * Refreshes category files in the background using direct MediaStore ContentResolver queries.
     */
    private fun reloadActiveCategory() {
        val category = _activeCategory.value ?: return
        _categoryState.value = CategoryState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val filesList = mutableListOf<File>()
                val resolver = context.contentResolver

                when (category) {
                    "images" -> {
                        val projection = arrayOf(MediaStore.Images.Media.DATA)
                        resolver.query(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            projection, null, null, null
                        )?.use { cursor ->
                            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                            while (cursor.moveToNext()) {
                                val path = cursor.getString(dataCol)
                                if (path != null) {
                                    val file = File(path)
                                    if (file.exists() && file.isFile) filesList.add(file)
                                }
                            }
                        }
                    }
                    "videos" -> {
                        val projection = arrayOf(MediaStore.Video.Media.DATA)
                        resolver.query(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            projection, null, null, null
                        )?.use { cursor ->
                            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                            while (cursor.moveToNext()) {
                                val path = cursor.getString(dataCol)
                                if (path != null) {
                                    val file = File(path)
                                    if (file.exists() && file.isFile) filesList.add(file)
                                }
                            }
                        }
                    }
                    "audio" -> {
                        val projection = arrayOf(MediaStore.Audio.Media.DATA)
                        resolver.query(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            projection, null, null, null
                        )?.use { cursor ->
                            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                            while (cursor.moveToNext()) {
                                val path = cursor.getString(dataCol)
                                if (path != null) {
                                    val file = File(path)
                                    if (file.exists() && file.isFile) filesList.add(file)
                                }
                            }
                        }
                    }
                    "documents", "archives" -> {
                        val projection = arrayOf(MediaStore.Files.FileColumns.DATA)
                        val uri = MediaStore.Files.getContentUri("external")
                        resolver.query(uri, projection, null, null, null)?.use { cursor ->
                            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                            while (cursor.moveToNext()) {
                                val path = cursor.getString(dataCol)
                                if (path != null) {
                                    val file = File(path)
                                    if (file.exists() && file.isFile) {
                                        val name = file.name.lowercase()
                                        if (category == "documents") {
                                            val docFilter = _documentFilter.value
                                            val isCode = name.substringAfterLast('.', "") in listOf(
                                                "py", "kt", "java", "json", "xml", "html", "css", "js", "gradle", "sh", "bat", "cpp", "c", "h", "properties",
                                                "dart", "ts", "tsx", "jsx", "hpp", "cs", "php", "sql", "yaml", "yml", "ini", "cfg", "conf", "log", "tsv", "bash", "rb", "go", "rs", "swift", "scala", "r", "lua"
                                            )
                                            val isTextOrCode = name.endsWith(".txt") || name.endsWith(".md") || isCode
                                            val isDoc = name.endsWith(".pdf") || name.endsWith(".docx") || 
                                                    name.endsWith(".doc") || name.endsWith(".xlsx") || 
                                                    name.endsWith(".xls") || name.endsWith(".csv") || 
                                                    name.endsWith(".pptx") || name.endsWith(".ppt") || 
                                                    isTextOrCode
                                            if (isDoc) {
                                                val matchesFilter = when (docFilter) {
                                                    "pdf" -> name.endsWith(".pdf")
                                                    "word" -> name.endsWith(".docx") || name.endsWith(".doc")
                                                    "excel" -> name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".csv")
                                                    "slides" -> name.endsWith(".pptx") || name.endsWith(".ppt")
                                                    "txt" -> isTextOrCode
                                                    else -> true
                                                }
                                                if (matchesFilter) filesList.add(file)
                                            }
                                        } else { // archives
                                            val isZip = name.endsWith(".zip") || name.endsWith(".rar") || 
                                                    name.endsWith(".7z") || name.endsWith(".tar") || 
                                                    name.endsWith(".gz")
                                            if (isZip) filesList.add(file)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Apply Search & Sort
                val query = _searchQuery.value.trim().lowercase()
                val filteredList = if (query.isNotEmpty()) {
                    filesList.filter { it.name.lowercase().contains(query) }
                } else {
                    filesList
                }

                val sortedList = sortFiles(filteredList)
                _categoryState.value = CategoryState.Success(sortedList)

            } catch (e: Exception) {
                e.printStackTrace()
                _categoryState.value = CategoryState.Error("Error scanning category files: ${e.localizedMessage}")
            }
        }
    }

    /**
     * File Explorer folder-level navigation.
     */
    fun navigateToDirectory(path: String?) {
        _currentDirectoryPath.value = path
        _activeCategory.value = null // Close category view when entering folder
        _categoryState.value = CategoryState.Idle
        viewModelScope.launch {
            refreshCurrentDirectory()
        }
    }

    fun navigateUp() {
        val currentPath = _currentDirectoryPath.value ?: return
        val currentDir = File(currentPath)
        val parent = currentDir.parentFile

        // Determine if parent is above our storage volume limits
        val volumes = _storageVolumes.value
        val isParentStorageRoot = volumes.any { it.path == currentPath }
        if (isParentStorageRoot || parent == null) {
            // Go back to the storage root selection dashboard
            _currentDirectoryPath.value = null
            _currentDirectoryFiles.value = emptyList()
        } else {
            _currentDirectoryPath.value = parent.absolutePath
            viewModelScope.launch {
                refreshCurrentDirectory()
            }
        }
    }

    /**
     * Reloads and sorts files/folders inside the active directory.
     */
    suspend fun refreshCurrentDirectory() = withContext(Dispatchers.IO) {
        val path = _currentDirectoryPath.value ?: return@withContext
        try {
            val directory = File(path)
            if (directory.exists() && directory.isDirectory) {
                val list = directory.listFiles() ?: emptyArray()
                val filesOnly = list.filter { !it.isDirectory }
                val dirsOnly = list.filter { it.isDirectory && !it.name.startsWith(".") }

                // Search query
                val query = _searchQuery.value.trim().lowercase()
                val filteredFiles = if (query.isNotEmpty()) {
                    filesOnly.filter { it.name.lowercase().contains(query) }
                } else {
                    filesOnly
                }

                val filteredDirs = if (query.isNotEmpty()) {
                    dirsOnly.filter { it.name.lowercase().contains(query) }
                } else {
                    dirsOnly
                }

                // Sort directories A-Z, then sort files by selected order
                val sortedDirs = filteredDirs.sortedWith(compareBy { it.name.lowercase() })
                val sortedFiles = sortFiles(filteredFiles)

                // Directories first, then files
                _currentDirectoryFiles.value = sortedDirs + sortedFiles
            } else {
                _currentDirectoryFiles.value = emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _currentDirectoryFiles.value = emptyList()
        }
    }

    /**
     * Helper to sort files according to standard parameters.
     */
    private fun sortFiles(files: List<File>): List<File> {
        return when (_sortOrder.value) {
            FileSortOrder.NAME_ASC -> files.sortedWith(compareBy { it.name.lowercase() })
            FileSortOrder.NAME_DESC -> files.sortedWith(compareByDescending { it.name.lowercase() })
            FileSortOrder.SIZE_ASC -> files.sortedWith(compareBy { it.length() })
            FileSortOrder.SIZE_DESC -> files.sortedWith(compareByDescending { it.length() })
            FileSortOrder.DATE_DESC -> files.sortedWith(compareByDescending { it.lastModified() })
            FileSortOrder.DATE_ASC -> files.sortedWith(compareBy { it.lastModified() })
        }
    }

    /**
     * Rename file logic.
     */
    fun renameFile(file: File, newName: String, onCompleted: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (newName.isBlank()) {
                    withContext(Dispatchers.Main) { onCompleted(false, "Name cannot be empty.") }
                    return@launch
                }
                val ext = file.extension
                val nameWithExt = if (ext.isNotEmpty() && !newName.endsWith(".$ext", ignoreCase = true)) {
                    "$newName.$ext"
                } else {
                    newName
                }

                val target = File(file.parentFile, nameWithExt)
                if (target.exists()) {
                    withContext(Dispatchers.Main) { onCompleted(false, "A file with this name already exists.") }
                    return@launch
                }

                val success = file.renameTo(target)
                if (success) {
                    // Force refresh view
                    refreshCurrentDirectory()
                    reloadActiveCategory()
                    
                    // Update index in MediaStore if content resolver supports it
                    try {
                        val values = android.content.ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, nameWithExt)
                            put(MediaStore.MediaColumns.DATA, target.absolutePath)
                        }
                        context.contentResolver.update(
                            MediaStore.Files.getContentUri("external"),
                            values,
                            "${MediaStore.MediaColumns.DATA} = ?",
                            arrayOf(file.absolutePath)
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // Update RecentFile Room database entry if renaming a history item
                    try {
                        recentFileRepository.deleteRecentFileByUri(Uri.fromFile(file).toString())
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    withContext(Dispatchers.Main) { onCompleted(true, "File renamed successfully.") }
                } else {
                    withContext(Dispatchers.Main) { onCompleted(false, "Rename operation failed. Storage might be write-protected.") }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { onCompleted(false, "Error: ${e.localizedMessage}") }
            }
        }
    }

    /**
     * Delete file logic.
     */
    fun deleteFile(file: File, onCompleted: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val success = if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    file.delete()
                }

                if (success) {
                    refreshCurrentDirectory()
                    reloadActiveCategory()

                    // Remove from MediaStore
                    try {
                        context.contentResolver.delete(
                            MediaStore.Files.getContentUri("external"),
                            "${MediaStore.MediaColumns.DATA} = ?",
                            arrayOf(file.absolutePath)
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // Remove from RecentFile history database
                    try {
                        recentFileRepository.deleteRecentFileByUri(Uri.fromFile(file).toString())
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    withContext(Dispatchers.Main) { onCompleted(true, "File deleted successfully.") }
                } else {
                    withContext(Dispatchers.Main) { onCompleted(false, "Delete operation failed.") }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { onCompleted(false, "Error: ${e.localizedMessage}") }
            }
        }
    }
}
