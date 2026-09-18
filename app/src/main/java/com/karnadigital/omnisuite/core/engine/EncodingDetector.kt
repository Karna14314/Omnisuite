package com.karnadigital.omnisuite.core.engine

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

data class EncodingResult(
    val charset: Charset,
    val confidence: Float,
    val hasBom: Boolean
)

object EncodingDetector {

    private val BOM_UTF8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val BOM_UTF16_LE = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val BOM_UTF16_BE = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
    private val BOM_UTF32_LE = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x00)
    private val BOM_UTF32_BE = byteArrayOf(0x00, 0x00, 0xFE.toByte(), 0xFF.toByte())

    val SUPPORTED_ENCODINGS = listOf(
        "UTF-8",
        "UTF-16LE",
        "UTF-16BE",
        "UTF-32LE",
        "UTF-32BE",
        "ASCII",
        "ISO-8859-1",
        "Windows-1252",
        "Shift_JIS",
        "GBK",
        "Big5",
        "EUC-KR",
        "EUC-JP",
        "ISO-8859-2",
        "ISO-8859-15",
        "KOI8-R"
    )

    fun detectEncoding(bytes: ByteArray): EncodingResult {
        if (bytes.isEmpty()) {
            return EncodingResult(StandardCharsets.UTF_8, 1.0f, false)
        }
        val header = bytes.copyOf(minOf(bytes.size, 8192))
        detectBom(header)?.let { return it }
        if (isAscii(header)) {
            return EncodingResult(StandardCharsets.US_ASCII, 0.95f, false)
        }
        if (isUtf8(header)) {
            return EncodingResult(StandardCharsets.UTF_8, 0.9f, false)
        }
        val detected = detectByHeuristics(header)
        return EncodingResult(detected, 0.6f, false)
    }

    fun detectEncoding(file: File): EncodingResult {
        if (!file.exists() || file.length() == 0L) {
            return EncodingResult(StandardCharsets.UTF_8, 1.0f, false)
        }

        val bytes = ByteArray(minOf(file.length().toInt(), 8192))
        var bytesRead = 0
        FileInputStream(file).use { fis ->
            bytesRead = fis.read(bytes)
        }
        if (bytesRead <= 0) {
            return EncodingResult(StandardCharsets.UTF_8, 1.0f, false)
        }

        return detectEncoding(bytes.copyOf(bytesRead))
    }

    private fun detectBom(header: ByteArray): EncodingResult? {
        if (header.size >= 4) {
            if (header[0] == BOM_UTF32_LE[0] && header[1] == BOM_UTF32_LE[1] &&
                header[2] == BOM_UTF32_LE[2] && header[3] == BOM_UTF32_LE[3]) {
                return EncodingResult(Charset.forName("UTF-32LE"), 1.0f, true)
            }
            if (header[0] == BOM_UTF32_BE[0] && header[1] == BOM_UTF32_BE[1] &&
                header[2] == BOM_UTF32_BE[2] && header[3] == BOM_UTF32_BE[3]) {
                return EncodingResult(Charset.forName("UTF-32BE"), 1.0f, true)
            }
        }
        if (header.size >= 3) {
            if (header[0] == BOM_UTF8[0] && header[1] == BOM_UTF8[1] && header[2] == BOM_UTF8[2]) {
                return EncodingResult(StandardCharsets.UTF_8, 1.0f, true)
            }
        }
        if (header.size >= 2) {
            if (header[0] == BOM_UTF16_LE[0] && header[1] == BOM_UTF16_LE[1]) {
                return EncodingResult(StandardCharsets.UTF_16LE, 1.0f, true)
            }
            if (header[0] == BOM_UTF16_BE[0] && header[1] == BOM_UTF16_BE[1]) {
                return EncodingResult(StandardCharsets.UTF_16BE, 1.0f, true)
            }
        }
        return null
    }

    private fun isAscii(bytes: ByteArray): Boolean {
        for (b in bytes) {
            if (b.toInt() and 0xFF > 0x7F) return false
        }
        return true
    }

    private fun isUtf8(bytes: ByteArray): Boolean {
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            if (b <= 0x7F) {
                i++
            } else if (b in 0xC2..0xDF) {
                if (i + 1 >= bytes.size) return false
                val b2 = bytes[i + 1].toInt() and 0xFF
                if (b2 !in 0x80..0xBF) return false
                i += 2
            } else if (b in 0xE0..0xEF) {
                if (i + 2 >= bytes.size) return false
                val b2 = bytes[i + 1].toInt() and 0xFF
                val b3 = bytes[i + 2].toInt() and 0xFF
                if (b2 !in 0x80..0xBF || b3 !in 0x80..0xBF) return false
                i += 3
            } else if (b in 0xF0..0xF4) {
                if (i + 3 >= bytes.size) return false
                val b2 = bytes[i + 1].toInt() and 0xFF
                val b3 = bytes[i + 2].toInt() and 0xFF
                val b4 = bytes[i + 3].toInt() and 0xFF
                if (b2 !in 0x80..0xBF || b3 !in 0x80..0xBF || b4 !in 0x80..0xBF) return false
                i += 4
            } else {
                return false
            }
        }
        return true
    }

    private fun detectByHeuristics(bytes: ByteArray): Charset {
        var nullEven = 0
        var nullOdd = 0
        var highBytes = 0
        var validUtf8Sequences = 0
        var invalidUtf8Sequences = 0

        for (i in bytes.indices) {
            val b = bytes[i].toInt() and 0xFF
            if (b == 0) {
                if (i % 2 == 0) nullEven++ else nullOdd++
            }
            if (b in 0x80..0xFF) highBytes++
        }

        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            if (b in 0xC0..0xDF && i + 1 < bytes.size) {
                if ((bytes[i + 1].toInt() and 0xC0) == 0x80) validUtf8Sequences++ else invalidUtf8Sequences++
                i += 2
            } else if (b in 0xE0..0xEF && i + 2 < bytes.size) {
                if ((bytes[i + 1].toInt() and 0xC0) == 0x80 && (bytes[i + 2].toInt() and 0xC0) == 0x80) {
                    validUtf8Sequences++
                } else {
                    invalidUtf8Sequences++
                }
                i += 3
            } else {
                i++
            }
        }

        if (nullEven > 0 && nullOdd == 0 && nullEven > bytes.size / 20) {
            return StandardCharsets.UTF_16BE
        }
        if (nullOdd > 0 && nullEven == 0 && nullOdd > bytes.size / 20) {
            return StandardCharsets.UTF_16LE
        }

        if (validUtf8Sequences > 0 && invalidUtf8Sequences == 0) {
            return StandardCharsets.UTF_8
        }

        return detectSingleByteEncoding(bytes)
    }

    private fun detectSingleByteEncoding(bytes: ByteArray): Charset {
        val freq = IntArray(256)
        for (b in bytes) {
            freq[b.toInt() and 0xFF]++
        }

        var c1Bytes = 0
        for (i in 0x80..0xFF) {
            c1Bytes += freq[i]
        }

        if (c1Bytes == 0) return StandardCharsets.ISO_8859_1

        var commonLatin = 0
        var common1252 = 0
        for (i in 0xA0..0xFF) {
            commonLatin += freq[i]
        }
        val special1252 = intArrayOf(0x80, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89, 0x8A,
            0x8B, 0x8C, 0x8E, 0x91, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9A,
            0x9B, 0x9C, 0x9E, 0x9F)
        for (idx in special1252) {
            common1252 += freq[idx]
        }

        if (common1252 > 0) return Charset.forName("Windows-1252")

        return StandardCharsets.ISO_8859_1
    }

    fun readTextWithEncoding(file: File, charset: Charset): String {
        return file.inputStream().use { inputStream ->
            inputStream.bufferedReader(charset).use { reader ->
                reader.readText()
            }
        }
    }
}
