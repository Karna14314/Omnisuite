package com.karnadigital.omnisuite.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karnadigital.omnisuite.core.repository.RecentFileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel managing dynamic settings operations off the main UI thread.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val recentFileRepository: RecentFileRepository
) : ViewModel() {

    /**
     * Wipes the SQLite Room database history logs in the background.
     */
    fun clearAllRecentFiles(onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                recentFileRepository.clearAllRecentFiles()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            onComplete()
        }
    }
}
