package com.karnadigital.omnisuite.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karnadigital.omnisuite.core.repository.RecentFileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel managing dynamic settings operations off the main UI thread.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recentFileRepository: RecentFileRepository
) : ViewModel() {

    /**
     * Wipes the SQLite Room database history logs in the background.
     */
    fun clearAllRecentFiles(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                recentFileRepository.clearAllRecentFiles()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    /**
     * Purges temporary cache directories in the background and returns the new size.
     */
    fun clearCache(onComplete: (newSizeStr: String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.cacheDir.listFiles()?.forEach { file ->
                    file.deleteRecursively()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val newSize = calculateCacheSize()
            withContext(Dispatchers.Main) {
                onComplete(newSize)
            }
        }
    }

    /**
     * Calculates the total size of cache files formatted as a readable string.
     */
    fun calculateCacheSize(): String {
        val bytes = getFolderSize(context.cacheDir)
        return formatFileSize(bytes)
    }

    private fun getFolderSize(file: File?): Long {
        if (file == null || !file.exists()) return 0L
        var size = 0L
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                size += getFolderSize(child)
            }
        } else {
            size += file.length()
        }
        return size
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0.0 B"
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
    }
}
