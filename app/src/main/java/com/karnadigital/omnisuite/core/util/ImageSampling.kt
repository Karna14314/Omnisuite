package com.karnadigital.omnisuite.core.util

/**
 * Pure, testable image sampling math shared by the image viewer and tools.
 *
 * Kept free of Android imports so it can be unit-tested on the JVM.
 */
object ImageSampling {

    /**
     * Computes the largest `inSampleSize` (a power of two) that keeps both dimensions
     * at least as large as the requested maximums. This is the standard Android approach
     * to downsample a high-resolution image on decode so that a 4000x3000 photo no longer
     * allocates ~48MB of heap for a preview/edit operation.
     *
     * @param width actual pixel width of the source image
     * @param height actual pixel height of the source image
     * @param maxWidth maximum desired decoded width
     * @param maxHeight maximum desired decoded height
     * @return a power-of-two sample size (1 = no downsampling)
     */
    fun calculateInSampleSize(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Int {
        if (width <= 0 || height <= 0 || maxWidth <= 0 || maxHeight <= 0) return 1
        var inSampleSize = 1
        val halfWidth = width / 2
        val halfHeight = height / 2
        while (halfWidth / inSampleSize >= maxWidth && halfHeight / inSampleSize >= maxHeight) {
            inSampleSize *= 2
        }
        return inSampleSize
    }
}
