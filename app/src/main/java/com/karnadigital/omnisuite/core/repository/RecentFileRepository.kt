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
     * Inserts a new recent file reference or updates the timestamp if the file already exists.
     * Each file has only one unique record identified by its URI.
     * Cleans up any existing duplicates before insert/update.
     */
    suspend fun insertRecentFile(recentFile: RecentFile) {
        val mime = recentFile.mimeType.lowercase()
        val name = recentFile.fileName.lowercase()
        val isOp = recentFile.isOperation ||
                mime.contains("barcode") ||
                mime.contains("qrcode") ||
                name.contains("barcode scan:") ||
                name.contains("scanned barcode:") ||
                name.contains("generated qr:") ||
                name.startsWith("stitched_") ||
                name.startsWith("id_template_") ||
                name.startsWith("watermarked_") ||
                name.startsWith("compressed_") ||
                name.startsWith("resized_") ||
                name.startsWith("transcoded_") ||
                name.startsWith("merged_") ||
                name.startsWith("split_") ||
                name.startsWith("encrypted_") ||
                name.startsWith("decrypted_") ||
                name.startsWith("signed_") ||
                name.startsWith("converted_") ||
                name.startsWith("images_compiled") ||
                mime.startsWith("application/x-")

        val modified = recentFile.copy(isOperation = isOp)

        // Delete all existing records for this URI to clean up duplicates
        recentFileDao.deleteRecentFileByUri(modified.fileUri)
        // Insert fresh record (always single entry per URI)
        recentFileDao.insertRecentFile(modified)
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
