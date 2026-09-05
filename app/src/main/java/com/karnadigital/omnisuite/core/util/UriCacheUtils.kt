package com.karnadigital.omnisuite.core.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Suspending engine utility that isolates files opened via Storage Access Framework (SAF)
 * [Uri]s and caches them safely inside `context.cacheDir`.
 *
 * This provides local file descriptor references that are compatible with file-parsing 
 * engines (such as Apache POI and PDFBox Android) which require absolute file paths 
 * rather than direct content resolver streams.
 */
@Singleton
class UriCacheUtils @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Returns true when the given URI [scheme] is allowed for offline-only caching.
     *
     * OmniSuite is strictly offline: only `content://` and `file://` schemes
     * (and a null scheme treated as a bare file path) are permitted. Network
     * schemes such as `http`/`https` are rejected.
     */
    fun isOfflineScheme(scheme: String?): Boolean = UriSchemeUtils.isOfflineScheme(scheme)

    /**
     * Copies a Content Uri's data into a temporary file in `cacheDir` and returns the file handle.
     *
     * Runs strictly on [Dispatchers.IO] to guarantee non-blocking asynchronous storage ops.
     *
     * @param uri The incoming Storage Access Framework (SAF) Uri.
     * @return The local cached [File], or null if the read/write operation fails or the
     *         scheme is unsupported (e.g. network URIs are intentionally rejected).
     */
    /**
     * Attempts to obtain persistable read/write URI permission if the content URI supports it.
     */
    fun takePersistablePermission(uri: Uri) {
        if (uri.scheme == "content") {
            try {
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (e: Exception) {
                // Ignore if URI grant is temporary or does not support persistable permissions
            }
        }
    }

    /**
     * Gets or creates a handle for a persistent internal backup copy for history files.
     */
    fun getPersistentBackupFile(uri: Uri, suggestedName: String? = null): File {
        val recentDir = File(context.filesDir, "recent_files").apply { if (!exists()) mkdirs() }
        val hash = Math.abs(uri.toString().hashCode())
        val existing = recentDir.listFiles()?.firstOrNull { it.name.startsWith("${hash}_") && it.length() > 0 }
        if (existing != null) {
            return existing
        }
        val name = suggestedName ?: getFileName(uri) ?: "recent_${uri.hashCode()}"
        val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(recentDir, "${hash}_$safeName")
    }

    /**
     * Copies a Content Uri's data into a temporary file in `cacheDir` and returns the file handle.
     * Maintains a persistent backup copy in internal storage so files opened externally remain accessible
     * even if temporary SAF URI permissions expire when reopened later from History.
     *
     * Runs strictly on [Dispatchers.IO] to guarantee non-blocking asynchronous storage ops.
     *
     * @param uri The incoming Storage Access Framework (SAF) Uri.
     * @return The local cached [File], or null if the read/write operation fails or the
     *         scheme is unsupported (e.g. network URIs are intentionally rejected).
     */
    suspend fun cacheUriToFile(uri: Uri): File? = withContext(Dispatchers.IO) {
        val scheme = uri.scheme?.lowercase()
        if (!isOfflineScheme(scheme)) {
            // Reject network schemes to honor the strict offline-only spec.
            return@withContext null
        }

        takePersistablePermission(uri)

        if (scheme == "file" || scheme == null) {
            val path = uri.path
            if (path != null) {
                val file = File(path)
                if (file.exists() && file.isFile) {
                    return@withContext file
                }
            }
        }

        val persistentBackup = getPersistentBackupFile(uri, null)
        val hash = Math.abs(uri.toString().hashCode())
        val fileName = if (persistentBackup.exists() && persistentBackup.length() > 0) {
            persistentBackup.name.substringAfter("${hash}_")
        } else {
            getFileName(uri) ?: "omnisuite_temp_${System.currentTimeMillis()}"
        }
        val cacheFile = File(context.cacheDir, fileName)

        var streamCopied = false
        try {
            if (cacheFile.exists()) {
                cacheFile.delete()
            }

            val pfd = try {
                context.contentResolver.openFileDescriptor(uri, "r")
            } catch (e: Exception) {
                null
            }

            if (pfd != null) {
                java.io.FileInputStream(pfd.fileDescriptor).use { inputStream ->
                    FileOutputStream(cacheFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                pfd.close()
                streamCopied = cacheFile.exists() && cacheFile.length() > 0
            } else {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        FileOutputStream(cacheFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    streamCopied = cacheFile.exists() && cacheFile.length() > 0
                } catch (e: Exception) {
                    // Ignore stream exception when permission expired, fallback will take over
                }
            }

            if (streamCopied) {
                try {
                    cacheFile.copyTo(persistentBackup, overwrite = true)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                pruneCache(keepFile = cacheFile)
                return@withContext cacheFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback Stage 1: Check if persistent internal backup copy exists
        if (persistentBackup.exists() && persistentBackup.length() > 0) {
            try {
                persistentBackup.copyTo(cacheFile, overwrite = true)
                return@withContext cacheFile
            } catch (e: Exception) {
                return@withContext persistentBackup
            }
        }

        // Fallback Stage 2: Direct file path check
        val rawPath = uri.path
        if (!rawPath.isNullOrBlank()) {
            val directFile = File(rawPath)
            if (directFile.exists() && directFile.isFile && directFile.length() > 0) {
                return@withContext directFile
            }
        }

        null
    }

    /**
     * Checks the cache directory's size and deletes the oldest cached files if the size exceeds maxCacheSize (default 50MB).
     */
    private fun pruneCache(maxCacheSize: Long = 50 * 1024 * 1024L, keepFile: File? = null) {
        try {
            val files = context.cacheDir.listFiles()?.filter { it.isFile && it.absolutePath != keepFile?.absolutePath } ?: return
            var totalSize = files.sumOf { it.length() }
            if (totalSize <= maxCacheSize) return

            // Sort by last modified time, oldest first
            val sortedFiles = files.sortedBy { it.lastModified() }
            for (file in sortedFiles) {
                if (totalSize <= maxCacheSize) break
                val fileSize = file.length()
                if (file.delete()) {
                    totalSize -= fileSize
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Extracts the user-facing display name of a Content Uri.
     */
    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            name = cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (name == null) {
            name = uri.path
            val lastSlash = name?.lastIndexOf('/') ?: -1
            if (lastSlash != -1) {
                name = name?.substring(lastSlash + 1)
            }
        }
        return name
    }

    /**
     * Recursively clears all files inside `context.cacheDir` to maintain size discipline.
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        try {
            context.cacheDir.listFiles()?.forEach { file ->
                file.deleteRecursively()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}