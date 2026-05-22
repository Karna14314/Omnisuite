package com.karnadigital.omnisuite.core.repository

import com.karnadigital.omnisuite.core.model.RecentFile
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline repository pattern implementation managing recently accessed files.
 */
@Singleton
class RecentFileRepository @Inject constructor(
    private val recentFileDao: RecentFileDao
) {
    /**
     * Reactive stream of recently accessed document metadata elements.
     */
    val recentFiles: Flow<List<RecentFile>> = recentFileDao.getRecentFilesFlow()

    /**
     * Inserts or replaces a recent file reference in the persistence cache.
     */
    suspend fun insertRecentFile(recentFile: RecentFile) {
        recentFileDao.insertRecentFile(recentFile)
    }

    /**
     * Deletes a specific file reference from the persistence cache.
     */
    suspend fun deleteRecentFile(recentFile: RecentFile) {
        recentFileDao.deleteRecentFile(recentFile)
    }

    /**
     * Deletes a recent file record utilizing its Storage Access Framework content URI.
     */
    suspend fun deleteRecentFileByUri(fileUri: String) {
        recentFileDao.deleteRecentFileByUri(fileUri)
    }

    /**
     * One-shot synchronous query for the list of recent files.
     */
    suspend fun getRecentFilesList(): List<RecentFile> {
        return recentFileDao.getRecentFilesList()
    }

    /**
     * Clears all document access history references.
     */
    suspend fun clearAllRecentFiles() {
        recentFileDao.clearAllRecentFiles()
    }
}
