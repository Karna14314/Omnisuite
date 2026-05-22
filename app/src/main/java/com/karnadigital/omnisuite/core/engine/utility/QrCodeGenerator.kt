package com.karnadigital.omnisuite.core.engine.utility

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

/**
 * Highly optimized, offline-first generator that produces sharp [Bitmap] representations of 
 * QR Codes and traditional Barcodes using ZXing's [MultiFormatWriter].
 */
object QrCodeGenerator {

    /**
     * Generates a QR Code as an offline Android [Bitmap].
     *
     * @param content The raw text or URL payload to be encoded.
     * @param width The target pixel width of the output image. Default is 512.
     * @param height The target pixel height of the output image. Default is 512.
     * @param qrColor The foreground color of the QR module pixel blocks. Default is [Color.BLACK].
     * @param backgroundColor The background padding color. Default is [Color.WHITE].
     * @param errorCorrection The error resilience tier. Default is [ErrorCorrectionLevel.M].
     * @param margin The quiet zone border margin size in modules. Default is 1 module.
     * @return A pristine [Bitmap] of the requested parameters, or null if generation fails.
     */
    fun generateQrCode(
        content: String,
        width: Int = 512,
        height: Int = 512,
        qrColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE,
        errorCorrection: ErrorCorrectionLevel = ErrorCorrectionLevel.M,
        margin: Int = 1
    ): Bitmap? {
        if (content.isEmpty()) return null

        return try {
            val hints: MutableMap<EncodeHintType, Any> = EnumMap(EncodeHintType::class.java)
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[EncodeHintType.ERROR_CORRECTION] = errorCorrection
            hints[EncodeHintType.MARGIN] = margin

            val bitMatrix = MultiFormatWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                width,
                height,
                hints
            )

            val matrixWidth = bitMatrix.width
            val matrixHeight = bitMatrix.height
            val pixels = IntArray(matrixWidth * matrixHeight)

            for (y in 0 until matrixHeight) {
                val offset = y * matrixWidth
                for (x in 0 until matrixWidth) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) qrColor else backgroundColor
                }
            }

            Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, matrixWidth, 0, 0, matrixWidth, matrixHeight)
            }
        } catch (e: WriterException) {
            e.printStackTrace()
            null
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Generates a 1D Barcode (e.g. CODE_128) as an offline Android [Bitmap].
     *
     * @param content The alphanumeric content to encode in the barcode.
     * @param format The targeted Barcode family format. Must be a 1D format (e.g., [BarcodeFormat.CODE_128]).
     * @param width The target pixel width of the output barcode.
     * @param height The target pixel height of the output barcode.
     * @param barcodeColor The color of the structural barcode bars. Default is [Color.BLACK].
     * @param backgroundColor The background padding color. Default is [Color.WHITE].
     */
    fun generate1DBarcode(
        content: String,
        format: BarcodeFormat = BarcodeFormat.CODE_128,
        width: Int = 600,
        height: Int = 200,
        barcodeColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE
    ): Bitmap? {
        if (content.isEmpty()) return null

        return try {
            val hints: MutableMap<EncodeHintType, Any> = EnumMap(EncodeHintType::class.java)
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"

            val bitMatrix = MultiFormatWriter().encode(
                content,
                format,
                width,
                height,
                hints
            )

            val matrixWidth = bitMatrix.width
            val matrixHeight = bitMatrix.height
            val pixels = IntArray(matrixWidth * matrixHeight)

            for (y in 0 until matrixHeight) {
                val offset = y * matrixWidth
                for (x in 0 until matrixWidth) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) barcodeColor else backgroundColor
                }
            }

            Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, matrixWidth, 0, 0, matrixWidth, matrixHeight)
            }
        } catch (e: WriterException) {
            e.printStackTrace()
            null
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            null
        }
    }
}
