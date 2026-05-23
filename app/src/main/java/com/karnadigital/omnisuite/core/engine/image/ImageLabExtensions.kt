package com.karnadigital.omnisuite.core.engine.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import com.karnadigital.omnisuite.core.util.FileOutputManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageLabExtensions @Inject constructor(
    private val context: Context,
    private val fileOutputManager: FileOutputManager
) {

    suspend fun stitchImagesVertically(uris: List<Uri>): Uri? = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext null
        try {
            val bitmaps = uris.mapNotNull { uri ->
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }
            if (bitmaps.isEmpty()) return@withContext null

            val width = bitmaps.maxOf { it.width }
            val height = bitmaps.sumOf { it.height }
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)

            var currentHeight = 0f
            for (bitmap in bitmaps) {
                canvas.drawBitmap(bitmap, 0f, currentHeight, null)
                currentHeight += bitmap.height
            }

            val outStream = ByteArrayOutputStream()
            result.compress(Bitmap.CompressFormat.JPEG, 90, outStream)

            fileOutputManager.saveToDefault(
                context = context,
                bytes = outStream.toByteArray(),
                filename = "stitched_${System.currentTimeMillis()}.jpg",
                mimeType = "image/jpeg",
                subfolder = "Images"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun extractMediaFromDocument(uri: Uri): List<Uri> = withContext(Dispatchers.IO) {
        val extractedUris = mutableListOf<Uri>()
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val zis = ZipInputStream(stream)
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.contains("media/") && (entry.name.endsWith(".jpeg") || entry.name.endsWith(".jpg") || entry.name.endsWith(".png"))) {
                        val outStream = ByteArrayOutputStream()
                        zis.copyTo(outStream)
                        val ext = if (entry.name.endsWith(".png")) "png" else "jpg"
                        val mime = if (ext == "png") "image/png" else "image/jpeg"

                        fileOutputManager.saveToDefault(
                            context = context,
                            bytes = outStream.toByteArray(),
                            filename = "extracted_${System.currentTimeMillis()}.$ext",
                            mimeType = mime,
                            subfolder = "Images"
                        )?.let { extractedUris.add(it) }
                    }
                    entry = zis.nextEntry
                }
            }
            extractedUris
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun createIdCardPrintTemplate(frontUri: Uri, backUri: Uri): Uri? = withContext(Dispatchers.IO) {
        try {
            val frontBitmap = context.contentResolver.openInputStream(frontUri)?.use { BitmapFactory.decodeStream(it) } ?: return@withContext null
            val backBitmap = context.contentResolver.openInputStream(backUri)?.use { BitmapFactory.decodeStream(it) } ?: return@withContext null

            // A4 dimensions at 300 DPI
            val a4Width = 2480
            val a4Height = 3508
            val result = Bitmap.createBitmap(a4Width, a4Height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            canvas.drawColor(Color.WHITE)

            // ID card dimensions at 300 DPI (approx 85x54mm -> ~1004x638 pixels)
            val idWidth = 1004
            val idHeight = 638

            val scaledFront = Bitmap.createScaledBitmap(frontBitmap, idWidth, idHeight, true)
            val scaledBack = Bitmap.createScaledBitmap(backBitmap, idWidth, idHeight, true)

            // Position front and back on the A4 canvas
            val xOffset = (a4Width - idWidth) / 2f
            val yOffsetFront = a4Height / 4f - idHeight / 2f
            val yOffsetBack = a4Height * 3f / 4f - idHeight / 2f

            canvas.drawBitmap(scaledFront, xOffset, yOffsetFront, null)
            canvas.drawBitmap(scaledBack, xOffset, yOffsetBack, null)

            val outStream = ByteArrayOutputStream()
            result.compress(Bitmap.CompressFormat.JPEG, 90, outStream)

            fileOutputManager.saveToDefault(
                context = context,
                bytes = outStream.toByteArray(),
                filename = "id_template_${System.currentTimeMillis()}.jpg",
                mimeType = "image/jpeg",
                subfolder = "Images"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun addCustomWatermark(uri: Uri, text: String, size: Float, alpha: Int, rotation: Float): Uri? = withContext(Dispatchers.IO) {
        try {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@withContext null
            val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(result)

            val paint = Paint().apply {
                color = Color.argb(alpha, 128, 128, 128) // Gray watermark
                textSize = size
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }

            canvas.save()
            canvas.rotate(rotation, canvas.width / 2f, canvas.height / 2f)
            canvas.drawText(text, canvas.width / 2f, canvas.height / 2f, paint)
            canvas.restore()

            val outStream = ByteArrayOutputStream()
            result.compress(Bitmap.CompressFormat.JPEG, 90, outStream)

            fileOutputManager.saveToDefault(
                context = context,
                bytes = outStream.toByteArray(),
                filename = "watermarked_${System.currentTimeMillis()}.jpg",
                mimeType = "image/jpeg",
                subfolder = "Images"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
