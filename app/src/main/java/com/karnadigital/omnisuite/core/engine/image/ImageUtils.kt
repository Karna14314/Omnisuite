package com.karnadigital.omnisuite.core.engine.image

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
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
     * Applies filter and adjustments (brightness, contrast, saturation) to a Bitmap.
     * @param brightness value from -100f to 100f (default 0f)
     * @param contrast value from 0.5f to 2.0f (default 1.0f)
     * @param saturation value from 0.0f to 2.0f (default 1.0f)
     * @param filterType one of "Normal", "Grayscale", "Sepia", "Inverted", "Vintage", "Cool"
     */
    fun applyFilterAndAdjustments(
        bitmap: Bitmap,
        brightness: Float,
        contrast: Float,
        saturation: Float,
        filterType: String
    ): Bitmap {
        val cm = ColorMatrix()

        // 1. Apply filter first
        when (filterType) {
            "Grayscale" -> {
                cm.setSaturation(0f)
            }
            "Sepia" -> {
                val sepiaMatrix = ColorMatrix(floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f,     0f,     0f,     1f, 0f
                ))
                cm.set(sepiaMatrix)
            }
            "Inverted" -> {
                val invertMatrix = ColorMatrix(floatArrayOf(
                    -1f,  0f,  0f, 0f, 255f,
                     0f, -1f,  0f, 0f, 255f,
                     0f,  0f, -1f, 0f, 255f,
                     0f,  0f,  0f, 1f,   0f
                ))
                cm.set(invertMatrix)
            }
            "Vintage" -> {
                val vintageMatrix = ColorMatrix(floatArrayOf(
                    0.9f, 0f, 0f, 0f, 50f,
                    0f, 0.9f, 0f, 0f, 30f,
                    0f, 0f, 0.8f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.set(vintageMatrix)
            }
            "Cool" -> {
                val coolMatrix = ColorMatrix(floatArrayOf(
                    0.8f, 0f, 0f, 0f, 0f,
                    0f, 0.9f, 0f, 0f, 10f,
                    0f, 0f, 0.9f, 0f, 50f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.set(coolMatrix)
            }
            else -> {
                // Normal/None - do nothing, keep identity
            }
        }

        // 2. Apply Saturation (if not grayscale)
        if (filterType != "Grayscale") {
            val satMatrix = ColorMatrix().apply { setSaturation(saturation) }
            cm.postConcat(satMatrix)
        }

        // 3. Apply Contrast
        if (contrast != 1.0f) {
            val contrastMatrix = ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, 128f * (1f - contrast),
                0f, contrast, 0f, 0f, 128f * (1f - contrast),
                0f, 0f, contrast, 0f, 128f * (1f - contrast),
                0f, 0f, 0f, 1f, 0f
            ))
            cm.postConcat(contrastMatrix)
        }

        // 4. Apply Brightness
        if (brightness != 0f) {
            val brightnessMatrix = ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, brightness,
                0f, 1f, 0f, 0f, brightness,
                0f, 0f, 1f, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.postConcat(brightnessMatrix)
        }

        // Create new output bitmap
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
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
