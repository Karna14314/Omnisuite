package com.karnadigital.omnisuite.core.repository

import androidx.room.Database
import androidx.room.RoomDatabase
import com.karnadigital.omnisuite.core.model.RecentFile

/**
 * Main Room Database configuration for OmniSuite.
 * Manages local persistence for file access histories and scanning indexes offline.
 */
@Database(
    entities = [RecentFile::class],
    version = 1,
    exportSchema = false
)
abstract class OmniDatabase : RoomDatabase() {

    /**
     * Resolves operations for recent files cache management.
     */
    abstract fun recentFileDao(): RecentFileDao
}
