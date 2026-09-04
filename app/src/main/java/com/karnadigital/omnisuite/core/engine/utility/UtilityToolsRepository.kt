package com.karnadigital.omnisuite.core.engine.utility

import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.core.repository.RecentFileRepository
import com.karnadigital.omnisuite.core.util.FileOutputManager
import com.karnadigital.omnisuite.core.util.UriCacheUtils
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Singleton
class UtilityToolsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val uriCacheUtils: UriCacheUtils,
    private val fileOutputManager: FileOutputManager,
    private val recentFileRepository: RecentFileRepository
) {

    fun convertUnit(value: Double, fromUnit: String, toUnit: String, category: String): Double {
        if (fromUnit == toUnit) return value
        return when (category) {
            "Length" -> convertLength(value, fromUnit, toUnit)
            "Weight" -> convertWeight(value, fromUnit, toUnit)
            "Temperature" -> convertTemperature(value, fromUnit, toUnit)
            "Area" -> convertArea(value, fromUnit, toUnit)
            "Volume" -> convertVolume(value, fromUnit, toUnit)
            "Speed" -> convertSpeed(value, fromUnit, toUnit)
            "Time" -> convertTime(value, fromUnit, toUnit)
            "Data" -> convertData(value, fromUnit, toUnit)
            else -> value
        }
    }

    private fun convertLength(value: Double, from: String, to: String): Double {
        val meters = when (from) {
            "mm" -> value / 1000.0; "cm" -> value / 100.0; "m" -> value; "km" -> value * 1000.0
            "in" -> value * 0.0254; "ft" -> value * 0.3048; "yd" -> value * 0.9144; "mi" -> value * 1609.344
            else -> value
        }
        return when (to) {
            "mm" -> meters * 1000.0; "cm" -> meters * 100.0; "m" -> meters; "km" -> meters / 1000.0
            "in" -> meters / 0.0254; "ft" -> meters / 0.3048; "yd" -> meters / 0.9144; "mi" -> meters / 1609.344
            else -> meters
        }
    }

    private fun convertWeight(value: Double, from: String, to: String): Double {
        val grams = when (from) {
            "mg" -> value / 1000.0; "g" -> value; "kg" -> value * 1000.0; "t" -> value * 1000000.0
            "oz" -> value * 28.3495; "lb" -> value * 453.592; "st" -> value * 6350.29
            else -> value
        }
        return when (to) {
            "mg" -> grams * 1000.0; "g" -> grams; "kg" -> grams / 1000.0; "t" -> grams / 1000000.0
            "oz" -> grams / 28.3495; "lb" -> grams / 453.592; "st" -> grams / 6350.29
            else -> grams
        }
    }

    private fun convertTemperature(value: Double, from: String, to: String): Double {
        val celsius = when (from) {
            "C" -> value; "F" -> (value - 32) * 5.0 / 9.0; "K" -> value - 273.15
            else -> value
        }
        return when (to) {
            "C" -> celsius; "F" -> celsius * 9.0 / 5.0 + 32; "K" -> celsius + 273.15
            else -> celsius
        }
    }

    private fun convertArea(value: Double, from: String, to: String): Double {
        val sqMeters = when (from) {
            "sqmm" -> value / 1000000.0; "sqcm" -> value / 10000.0; "sqm" -> value
            "sqkm" -> value * 1000000.0; "sqin" -> value * 0.00064516; "sqft" -> value * 0.092903
            "acre" -> value * 4046.86; "ha" -> value * 10000.0
            else -> value
        }
        return when (to) {
            "sqmm" -> sqMeters * 1000000.0; "sqcm" -> sqMeters * 10000.0; "sqm" -> sqMeters
            "sqkm" -> sqMeters / 1000000.0; "sqin" -> sqMeters / 0.00064516; "sqft" -> sqMeters / 0.092903
            "acre" -> sqMeters / 4046.86; "ha" -> sqMeters / 10000.0
            else -> sqMeters
        }
    }

    private fun convertVolume(value: Double, from: String, to: String): Double {
        val liters = when (from) {
            "ml" -> value / 1000.0; "l" -> value; "m3" -> value * 1000.0
            "tsp" -> value * 0.00492892; "tbsp" -> value * 0.0147868; "cup" -> value * 0.236588
            "pt" -> value * 0.473176; "qt" -> value * 0.946353; "gal" -> value * 3.78541
            else -> value
        }
        return when (to) {
            "ml" -> liters * 1000.0; "l" -> liters; "m3" -> liters / 1000.0
            "tsp" -> liters / 0.00492892; "tbsp" -> liters / 0.0147868; "cup" -> liters / 0.236588
            "pt" -> liters / 0.473176; "qt" -> liters / 0.946353; "gal" -> liters / 3.78541
            else -> liters
        }
    }

    private fun convertSpeed(value: Double, from: String, to: String): Double {
        val mps = when (from) {
            "mps" -> value; "kmh" -> value / 3.6; "mph" -> value * 0.44704
            "knot" -> value * 0.514444; "fts" -> value * 0.3048
            else -> value
        }
        return when (to) {
            "mps" -> mps; "kmh" -> mps * 3.6; "mph" -> mps / 0.44704
            "knot" -> mps / 0.514444; "fts" -> mps / 0.3048
            else -> mps
        }
    }

    private fun convertTime(value: Double, from: String, to: String): Double {
        val seconds = when (from) {
            "ms" -> value / 1000.0; "s" -> value; "min" -> value * 60.0; "hr" -> value * 3600.0
            "day" -> value * 86400.0; "week" -> value * 604800.0; "month" -> value * 2592000.0; "year" -> value * 31536000.0
            else -> value
        }
        return when (to) {
            "ms" -> seconds * 1000.0; "s" -> seconds; "min" -> seconds / 60.0; "hr" -> seconds / 3600.0
            "day" -> seconds / 86400.0; "week" -> seconds / 604800.0; "month" -> seconds / 2592000.0; "year" -> seconds / 31536000.0
            else -> seconds
        }
    }

    private fun convertData(value: Double, from: String, to: String): Double {
        val bytes = when (from) {
            "bit" -> value / 8.0; "B" -> value; "KB" -> value * 1024.0; "MB" -> value * 1048576.0
            "GB" -> value * 1073741824.0; "TB" -> value * 1099511627776.0
            else -> value
        }
        return when (to) {
            "bit" -> bytes * 8.0; "B" -> bytes; "KB" -> bytes / 1024.0; "MB" -> bytes / 1048576.0
            "GB" -> bytes / 1073741824.0; "TB" -> bytes / 1099511627776.0
            else -> bytes
        }
    }

    fun getUnitCategories(): Map<String, List<Pair<String, String>>> {
        return mapOf(
            "Length" to listOf("mm" to "Millimeter", "cm" to "Centimeter", "m" to "Meter", "km" to "Kilometer", "in" to "Inch", "ft" to "Foot", "yd" to "Yard", "mi" to "Mile"),
            "Weight" to listOf("mg" to "Milligram", "g" to "Gram", "kg" to "Kilogram", "t" to "Metric Ton", "oz" to "Ounce", "lb" to "Pound", "st" to "Stone"),
            "Temperature" to listOf("C" to "Celsius", "F" to "Fahrenheit", "K" to "Kelvin"),
            "Area" to listOf("sqmm" to "Sq Millimeter", "sqcm" to "Sq Centimeter", "sqm" to "Sq Meter", "sqkm" to "Sq Kilometer", "sqin" to "Sq Inch", "sqft" to "Sq Foot", "acre" to "Acre", "ha" to "Hectare"),
            "Volume" to listOf("ml" to "Milliliter", "l" to "Liter", "m3" to "Cubic Meter", "tsp" to "Teaspoon", "tbsp" to "Tablespoon", "cup" to "Cup", "pt" to "Pint", "qt" to "Quart", "gal" to "Gallon"),
            "Speed" to listOf("mps" to "Meters/sec", "kmh" to "Km/hour", "mph" to "Miles/hour", "knot" to "Knot", "fts" to "Feet/sec"),
            "Time" to listOf("ms" to "Millisecond", "s" to "Second", "min" to "Minute", "hr" to "Hour", "day" to "Day", "week" to "Week", "month" to "Month", "year" to "Year"),
            "Data" to listOf("bit" to "Bit", "B" to "Byte", "KB" to "Kilobyte", "MB" to "Megabyte", "GB" to "Gigabyte", "TB" to "Terabyte")
        )
    }

    suspend fun shredFile(inputUri: Uri, passes: Int = 3): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val file = File(inputUri.path ?: throw Exception("Invalid file path"))
            if (!file.exists()) throw Exception("File not found")
            val length = file.length()
            val random = SecureRandom()
            for (i in 0 until passes) {
                val data = ByteArray(length.toInt())
                random.nextBytes(data)
                FileOutputStream(file).use { it.write(data) }
            }
            file.delete()
            Result.success(true)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun splitFile(inputUri: Uri, chunkSizeBytes: Long): Result<List<File>> = withContext(Dispatchers.IO) {
        try {
            val file = File(inputUri.path ?: throw Exception("Invalid file path"))
            if (!file.exists()) throw Exception("File not found")
            val chunks = mutableListOf<File>()
            val buffer = ByteArray(8192)
            var chunkIndex = 0
            file.inputStream().use { fis ->
                var remaining = file.length()
                while (remaining > 0) {
                    val chunkFile = File(context.cacheDir, "split_${System.currentTimeMillis()}_$chunkIndex.part")
                    FileOutputStream(chunkFile).use { fos ->
                        var written = 0L
                        val toWrite = minOf(chunkSizeBytes, remaining)
                        while (written < toWrite) {
                            val toRead = minOf(buffer.size.toLong(), toWrite - written).toInt()
                            val read = fis.read(buffer, 0, toRead)
                            if (read == -1) break
                            fos.write(buffer, 0, read)
                            written += read
                        }
                    }
                    chunks.add(chunkFile)
                    remaining -= chunkSizeBytes
                    chunkIndex++
                }
            }
            Result.success(chunks)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun joinFiles(files: List<File>, outputFile: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            FileOutputStream(outputFile).use { fos ->
                for (file in files) {
                    file.inputStream().use { it.copyTo(fos) }
                }
            }
            Result.success(outputFile)
        } catch (e: Exception) { Result.failure(e) }
    }

    fun pickColorFromImage(bitmap: Bitmap, x: Int, y: Int): Int {
        val safeX = x.coerceIn(0, bitmap.width - 1)
        val safeY = y.coerceIn(0, bitmap.height - 1)
        return bitmap.getPixel(safeX, safeY)
    }

    fun autoEnhance(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)
        var minR = 255; var maxR = 0
        var minG = 255; var maxG = 0
        var minB = 255; var maxB = 0
        for (pixel in pixels) {
            val r = Color.red(pixel); val g = Color.green(pixel); val b = Color.blue(pixel)
            minR = min(minR, r); maxR = max(maxR, r)
            minG = min(minG, g); maxG = max(maxG, g)
            minB = min(minB, b); maxB = max(maxB, b)
        }
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val a = Color.alpha(pixel)
            val r = ((Color.red(pixel) - minR) * 255 / max(1, maxR - minR)).coerceIn(0, 255)
            val g = ((Color.green(pixel) - minG) * 255 / max(1, maxG - minG)).coerceIn(0, 255)
            val b = ((Color.blue(pixel) - minB) * 255 / max(1, maxB - minB)).coerceIn(0, 255)
            pixels[i] = Color.argb(a, r, g, b)
        }
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    fun removeBackground(bitmap: Bitmap, threshold: Int = 30): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        val bgColor = bitmap.getPixel(0, 0)
        val bgR = Color.red(bgColor); val bgG = Color.green(bgColor); val bgB = Color.blue(bgColor)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel); val g = Color.green(pixel); val b = Color.blue(pixel)
            val diff = abs(r - bgR) + abs(g - bgG) + abs(b - bgB)
            if (diff < threshold * 3) {
                pixels[i] = Color.TRANSPARENT
            }
        }
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    fun applyBlur(bitmap: Bitmap, radius: Int = 5): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val resultPixels = IntArray(width * height)
        val size = radius * 2 + 1
        for (y in 0 until height) {
            for (x in 0 until width) {
                var rSum = 0; var gSum = 0; var bSum = 0; var aSum = 0; var count = 0
                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val nx = (x + dx).coerceIn(0, width - 1)
                        val ny = (y + dy).coerceIn(0, height - 1)
                        val pixel = pixels[ny * width + nx]
                        aSum += Color.alpha(pixel); rSum += Color.red(pixel)
                        gSum += Color.green(pixel); bSum += Color.blue(pixel); count++
                    }
                }
                resultPixels[y * width + x] = Color.argb(aSum / count, rSum / count, gSum / count, bSum / count)
            }
        }
        result.setPixels(resultPixels, 0, width, 0, 0, width, height)
        return result
    }

    fun applySharpen(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val resultPixels = IntArray(width * height)
        val kernel = intArrayOf(0, -1, 0, -1, 5, -1, 0, -1, 0)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var rSum = 0; var gSum = 0; var bSum = 0
                var ki = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val pixel = pixels[(y + dy) * width + (x + dx)]
                        val weight = kernel[ki++]
                        rSum += Color.red(pixel) * weight; gSum += Color.green(pixel) * weight; bSum += Color.blue(pixel) * weight
                    }
                }
                val a = Color.alpha(pixels[y * width + x])
                resultPixels[y * width + x] = Color.argb(a, rSum.coerceIn(0, 255), gSum.coerceIn(0, 255), bSum.coerceIn(0, 255))
            }
        }
        result.setPixels(resultPixels, 0, width, 0, 0, width, height)
        return result
    }

    fun removeRedEye(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel); val g = Color.green(pixel); val b = Color.blue(pixel)
            if (r > 150 && r > g * 2 && r > b * 2) {
                val gray = (g + b) / 2
                pixels[i] = Color.argb(Color.alpha(pixel), gray, gray, gray)
            }
        }
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    fun createCollage(bitmaps: List<Bitmap>, layout: Int = 0, padding: Int = 4): Bitmap {
        if (bitmaps.isEmpty()) return Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val size = 800
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { isAntiAlias = true }
        val count = bitmaps.size
        when (layout) {
            0 -> {
                val cols = if (count <= 2) 1 else 2
                val rows = (count + cols - 1) / cols
                val cellW = (size - padding * (cols + 1)) / cols
                val cellH = (size - padding * (rows + 1)) / rows
                for (i in bitmaps.indices) {
                    val col = i % cols; val row = i / cols
                    val x = padding + col * (cellW + padding).toFloat()
                    val y = padding + row * (cellH + padding).toFloat()
                    val scaled = Bitmap.createScaledBitmap(bitmaps[i], cellW, cellH, true)
                    canvas.drawBitmap(scaled, x, y, paint as android.graphics.Paint?)
                }
            }
            1 -> {
                val cellSize = (size - padding * 3) / 2
                for (i in 0 until minOf(count, 4)) {
                    val col = i % 2; val row = i / 2
                    val x = padding + col * (cellSize + padding).toFloat()
                    val y = padding + row * (cellSize + padding).toFloat()
                    val scaled = Bitmap.createScaledBitmap(bitmaps[i], cellSize, cellSize, true)
                    canvas.drawBitmap(scaled, x, y, paint as android.graphics.Paint?)
                }
            }
            2 -> {
                val cellSize = (size - padding * 3) / 3
                for (i in 0 until minOf(count, 9)) {
                    val col = i % 3; val row = i / 3
                    val x = padding + col * (cellSize + padding).toFloat()
                    val y = padding + row * (cellSize + padding).toFloat()
                    val scaled = Bitmap.createScaledBitmap(bitmaps[i], cellSize, cellSize, true)
                    canvas.drawBitmap(scaled, x, y, paint as android.graphics.Paint?)
                }
            }
        }
        return result
    }

    fun createMeme(bitmap: Bitmap, topText: String, bottomText: String): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = bitmap.width / 10f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            setShadowLayer(3f, 2f, 2f, Color.BLACK)
        }
        if (topText.isNotBlank()) {
            canvas.drawText(topText.uppercase(), bitmap.width / 2f, paint.textSize + 10f, paint)
        }
        if (bottomText.isNotBlank()) {
            canvas.drawText(bottomText.uppercase(), bitmap.width / 2f, bitmap.height - 20f, paint)
        }
        return result
    }

    fun resizeExact(bitmap: Bitmap, targetWidth: Int, targetHeight: Int, keepAspectRatio: Boolean = false, bgColor: Int = Color.TRANSPARENT): Bitmap {
        if (!keepAspectRatio) {
            return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        }
        val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val targetRatio = targetWidth.toFloat() / targetHeight.toFloat()
        val (newWidth, newHeight) = if (aspectRatio > targetRatio) {
            Pair(targetWidth, (targetWidth / aspectRatio).toInt())
        } else {
            Pair((targetHeight * aspectRatio).toInt(), targetHeight)
        }
        val scaled = Bitmap.createScaledBitmap(bitmap, newWidth.coerceAtLeast(1), newHeight.coerceAtLeast(1), true)
        val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        canvas.drawColor(bgColor)
        val x = (targetWidth - scaled.width) / 2
        val y = (targetHeight - scaled.height) / 2
        canvas.drawBitmap(scaled, x.toFloat(), y.toFloat(), null)
        return result
    }

    fun addTextToImage(bitmap: Bitmap, text: String, x: Float, y: Float, size: Float = 48f, color: Int = Color.WHITE, bold: Boolean = true): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(result)
        val paint = android.graphics.Paint().apply {
            this.color = color
            textSize = size
            typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
            isAntiAlias = true
            setShadowLayer(3f, 2f, 2f, Color.BLACK)
        }
        canvas.drawText(text, x, y, paint)
        return result
    }

    suspend fun encryptFile(inputUri: Uri, pass: String, customFilename: String? = null): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val file = uriCacheUtils.cacheUriToFile(inputUri) ?: throw Exception("Could not open source file.")
            val tempOutputFile = File(context.cacheDir, "encrypted_${System.currentTimeMillis()}.enc")

            val salt = ByteArray(16)
            SecureRandom().nextBytes(salt)

            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec = PBEKeySpec(pass.toCharArray(), salt, 65536, 256)
            val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

            val iv = ByteArray(12)
            SecureRandom().nextBytes(iv)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))

            FileOutputStream(tempOutputFile).use { fos ->
                fos.write(salt)
                fos.write(iv)
                CipherOutputStream(fos, cipher).use { cos ->
                    file.inputStream().use { fis ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (fis.read(buffer).also { read = it } >= 0) {
                            cos.write(buffer, 0, read)
                        }
                    }
                }
            }

            val originalName = (getFileNameFromUri(inputUri) ?: "file").removeSuffix(".enc")
            val outName = customFilename ?: "$originalName.enc"
            val savedUri = fileOutputManager.saveFileToDefault(tempOutputFile, outName, "application/octet-stream", "Encrypted")
                ?: throw Exception("Failed to save encrypted file.")

            recentFileRepository.insertRecentFile(
                RecentFile(
                    fileUri = savedUri.toString(),
                    fileName = outName,
                    mimeType = "application/octet-stream",
                    fileSize = tempOutputFile.length(),
                    lastOpened = System.currentTimeMillis(),
                    isOperation = true
                )
            )

            if (tempOutputFile.exists()) tempOutputFile.delete()
            if (file.exists()) file.delete()

            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun decryptFile(inputUri: Uri, pass: String, customFilename: String? = null): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val file = uriCacheUtils.cacheUriToFile(inputUri) ?: throw Exception("Could not open source file.")
            val tempOutputFile = File(context.cacheDir, "decrypted_${System.currentTimeMillis()}")

            val salt = ByteArray(16)
            val iv = ByteArray(12)

            file.inputStream().use { fis ->
                if (fis.read(salt) != 16) throw Exception("Invalid encrypted file header.")
                if (fis.read(iv) != 12) throw Exception("Invalid encrypted file header.")

                val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                val spec = PBEKeySpec(pass.toCharArray(), salt, 65536, 256)
                val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))

                CipherInputStream(fis, cipher).use { cis ->
                    FileOutputStream(tempOutputFile).use { fos ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (cis.read(buffer).also { read = it } >= 0) {
                            fos.write(buffer, 0, read)
                        }
                    }
                }
            }

            val originalName = (getFileNameFromUri(inputUri) ?: "file").removeSuffix(".enc")
            val outName = customFilename ?: originalName
            val savedUri = fileOutputManager.saveFileToDefault(tempOutputFile, outName, "application/octet-stream", "Decrypted")
                ?: throw Exception("Failed to save decrypted file.")

            recentFileRepository.insertRecentFile(
                RecentFile(
                    fileUri = savedUri.toString(),
                    fileName = outName,
                    mimeType = "application/octet-stream",
                    fileSize = tempOutputFile.length(),
                    lastOpened = System.currentTimeMillis(),
                    isOperation = true
                )
            )

            if (tempOutputFile.exists()) tempOutputFile.delete()
            if (file.exists()) file.delete()

            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFileChecksum(inputUri: Uri, algorithm: String = "SHA-256"): Result<String> = withContext(Dispatchers.IO) {
        try {
            val file = uriCacheUtils.cacheUriToFile(inputUri) ?: throw Exception("Could not open file.")
            val digest = MessageDigest.getInstance(algorithm)
            file.inputStream().use { fis ->
                val buffer = ByteArray(8192)
                var read: Int
                while (fis.read(buffer).also { read = it } >= 0) {
                    digest.update(buffer, 0, read)
                }
            }
            if (file.exists()) file.delete()
            val hashBytes = digest.digest()
            val hexString = hashBytes.joinToString("") { "%02x".format(it) }
            Result.success(hexString)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun compareText(text1: String, text2: String): Result<String> {
        return try {
            val lines1 = text1.lines()
            val lines2 = text2.lines()
            val sb = StringBuilder()
            sb.appendLine("=== Text Comparison Diff ===")
            sb.appendLine("Text 1 lines: ${lines1.size}, Text 2 lines: ${lines2.size}")
            sb.appendLine()
            val maxLines = maxOf(lines1.size, lines2.size)
            var diffCount = 0
            for (i in 0 until maxLines) {
                val l1 = lines1.getOrElse(i) { "" }
                val l2 = lines2.getOrElse(i) { "" }
                if (l1 != l2) {
                    diffCount++
                    sb.appendLine("Line ${i + 1}:")
                    if (l1.isNotBlank()) sb.appendLine(" - Text 1: $l1")
                    if (l2.isNotBlank()) sb.appendLine(" + Text 2: $l2")
                }
            }
            sb.appendLine()
            sb.appendLine("Total line differences: $diffCount")
            Result.success(sb.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getWordCount(text: String): Map<String, Any> {
        val words = if (text.isBlank()) 0 else text.trim().split(Regex("""\s+""")).size
        val characters = text.length
        val lines = if (text.isBlank()) 0 else text.lines().size
        val readingTimeMinutes = (words / 200.0).coerceAtLeast(0.0)
        val readingTimeStr = if (readingTimeMinutes < 1.0) "< 1 min" else "${readingTimeMinutes.toInt()} min"
        return mapOf(
            "words" to words,
            "characters" to characters,
            "lines" to lines,
            "readingTime" to readingTimeStr
        )
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) name = it.getString(index)
                }
            }
        }
        if (name == null) {
            name = uri.path
            val cut = name?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                name = name?.substring(cut + 1)
            }
        }
        return name
    }
}
