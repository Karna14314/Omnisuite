package com.karnadigital.omnisuite.core.engine.image

import android.graphics.Bitmap
import android.graphics.Matrix
import java.io.ByteArrayOutputStream

/**
 * Zero-overhead local offline image manipulation utilities using native Android graphics tools.
 */
object ImageUtils {

    /**
     * Resizes a Bitmap safely by scaling dimensions using a multiplier ratio.
     */
    fun resize(bitmap: Bitmap, scaleFactor: Float): Bitmap {
        if (scaleFactor == 1.0f || scaleFactor <= 0.0f) return bitmap
        val width = (bitmap.width * scaleFactor).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scaleFactor).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    /**
     * Rotates a Bitmap using a geometric transformation Matrix.
     */
    fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees % 360f == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Encodes a Bitmap to a byte array in the selected target format with a quality parameter.
     */
    fun compressAndEncode(
        bitmap: Bitmap,
        format: OutputFormat,
        quality: Int
    ): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val compressFormat = when (format) {
            OutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
            OutputFormat.PNG -> Bitmap.CompressFormat.PNG
            OutputFormat.WEBP -> {
                // Since minSdk is 30, WEBP_LOSSY is fully available and standardized
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            }
        }
        val coercedQuality = quality.coerceIn(0, 100)
        bitmap.compress(compressFormat, coercedQuality, outputStream)
        return outputStream.toByteArray()
    }
}

/**
 * Supported offline image compression format extensions.
 */
enum class OutputFormat {
    PNG, JPEG, WEBP
}
