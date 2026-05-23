package com.karnadigital.omnisuite.core.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

object FileOutputManager {

    /**
     * Saves a byte array as a file under the default Documents/OmniSuite/<subfolder> directory.
     * Works on Android 10+ (API 29+) using Scoped Storage MediaStore without explicit permissions.
     */
    fun saveToDefault(
        context: Context,
        bytes: ByteArray,
        filename: String,
        mimeType: String,
        subfolder: String
    ): Uri? {
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/OmniSuite/$subfolder")
            }
            val resolver = context.contentResolver
            val contentUri = MediaStore.Files.getContentUri("external")
            val uri = resolver.insert(contentUri, contentValues) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
            }
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
