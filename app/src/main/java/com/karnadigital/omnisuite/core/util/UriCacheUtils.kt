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
        val fileName = getFileName(context, uri) ?: "omnisuite_temp_${System.currentTimeMillis()}"
        val cacheFile = File(context.cacheDir, fileName)

        try {
            // Delete old temp file with the same name if it exists to avoid overlapping streams
            if (cacheFile.exists()) {
                cacheFile.delete()
            }

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(cacheFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            cacheFile
        } catch (e: IOException) {
            e.printStackTrace()
            null
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
