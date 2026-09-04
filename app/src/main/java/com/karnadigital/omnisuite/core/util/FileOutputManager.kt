package com.karnadigital.omnisuite.core.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileOutputManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Saves a byte array as a file under the default Documents/OmniSuite/<subfolder> directory.
     * Works on Android 10+ (API 29+) using Scoped Storage MediaStore without explicit permissions.
     */
    fun saveFileToDefault(
        file: java.io.File,
        filename: String,
        mimeType: String,
        subfolder: String
    ): Uri? {
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/OmniSuite/$subfolder")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val contentUri = MediaStore.Files.getContentUri("external")
            val uri = resolver.insert(contentUri, contentValues) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input ->
                    input.copyTo(out)
                }
                out.flush()
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val updateValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                    put(MediaStore.MediaColumns.SIZE, file.length())
                }
                resolver.update(uri, updateValues, null, null)
            }
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveToDefault(
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
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val contentUri = MediaStore.Files.getContentUri("external")
            val uri = resolver.insert(contentUri, contentValues) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
                out.flush()
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val updateValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                    put(MediaStore.MediaColumns.SIZE, bytes.size.toLong())
                }
                resolver.update(uri, updateValues, null, null)
            }
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
