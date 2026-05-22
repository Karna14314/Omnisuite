package com.karnadigital.omnisuite.core.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.karnadigital.omnisuite.core.model.RecentFile
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for executing persistence operations on [RecentFile] cache tables.
 */
@Dao
interface RecentFileDao {

    /**
     * Inserts a new recent file entry or updates an existing entry (resolved by URI uniqueness checks).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentFile(recentFile: RecentFile)

    /**
     * Deletes a specific recent file entry.
     */
    @Delete
    suspend fun deleteRecentFile(recentFile: RecentFile)

    /**
     * Deletes a recent file reference using its fileUri path.
     */
    @Query("DELETE FROM recent_files WHERE fileUri = :fileUri")
    suspend fun deleteRecentFileByUri(fileUri: String)

    /**
     * Reactive flow returning the top 10 most recently accessed files, ordered by their
     * access timestamp in descending order.
     */
    @Query("SELECT * FROM recent_files ORDER BY lastOpened DESC LIMIT 10")
    fun getRecentFilesFlow(): Flow<List<RecentFile>>

    /**
     * Synchronous/one-shot retrieval of the top 10 most recently accessed files.
     */
    @Query("SELECT * FROM recent_files ORDER BY lastOpened DESC LIMIT 10")
    suspend fun getRecentFilesList(): List<RecentFile>

    /**
     * Clears all recent file data.
     */
    @Query("DELETE FROM recent_files")
    suspend fun clearAllRecentFiles()
}
