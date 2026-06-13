package com.karnadigital.omnisuite.core.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Suspending engine utility that isolates files opened via Storage Access Framework (SAF)
 * [Uri]s and caches them safely inside `context.cacheDir`.
 *
 * This provides local file descriptor references that are compatible with file-parsing 
 * engines (such as Apache POI and PDFBox Android) which require absolute file paths 
 * rather than direct content resolver streams.
 */
object UriCacheUtils {

    /**
     * Copies a Content Uri's data into a temporary file in `cacheDir` and returns the file handle.
     *
     * Runs strictly on [Dispatchers.IO] to guarantee non-blocking asynchronous storage ops.
     *
     * @param context The Android context.
     * @param uri The incoming Storage Access Framework (SAF) Uri.
     * @return The local cached [File], or null if the read/write operation fails.
     */
    suspend fun cacheUriToFile(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        val scheme = uri.scheme?.lowercase()
        if (scheme == "file" || scheme == null) {
            val path = uri.path
            if (path != null) {
                val file = File(path)
                if (file.exists()) {
                    return@withContext file
                }
            }
        }

        val fileName = getFileName(context, uri) ?: "omnisuite_temp_${System.currentTimeMillis()}"
        val cacheFile = File(context.cacheDir, fileName)

        try {
            // Delete old temp file with the same name if it exists to avoid overlapping streams
            if (cacheFile.exists()) {
                cacheFile.delete()
            }

            if (scheme == "http" || scheme == "https") {
                val url = java.net.URL(uri.toString())
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                connection.connect()
                
                if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                    connection.inputStream.use { inputStream ->
                        FileOutputStream(cacheFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    pruneCache(context, keepFile = cacheFile)
                    cacheFile
                } else {
                    null
                }
            } else {
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
                } else {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        FileOutputStream(cacheFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
                pruneCache(context, keepFile = cacheFile)
                cacheFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Checks the cache directory's size and deletes the oldest cached files if the size exceeds maxCacheSize (default 50MB).
     */
    private fun pruneCache(context: Context, maxCacheSize: Long = 50 * 1024 * 1024L, keepFile: File? = null) {
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
    private fun getFileName(context: Context, uri: Uri): String? {
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
    suspend fun clearCache(context: Context) = withContext(Dispatchers.IO) {
        try {
            context.cacheDir.listFiles()?.forEach { file ->
                file.deleteRecursively()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
