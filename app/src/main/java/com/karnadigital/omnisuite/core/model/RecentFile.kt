package com.karnadigital.omnisuite.core.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Data entity representing a recently opened document in OmniSuite.
 * Used by the Room persistence layer to show the "Recent Files" list offline.
 *
 * @property id The auto-incrementing unique identifier.
 * @property fileUri The Storage Access Framework (SAF) URI string targeting the file.
 * @property fileName The human-readable filename resolved via openable columns.
 * @property mimeType The resolved document MIME type (e.g. "application/pdf").
 * @property fileSize The size of the document in bytes.
 * @property lastOpened The millisecond epoch timestamp when this document was last opened.
 */
@Entity(tableName = "recent_files")
data class RecentFile(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val fileUri: String,
    val fileName: String,
    val mimeType: String,
    val fileSize: Long,
    val lastOpened: Long
)
