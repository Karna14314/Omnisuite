package com.karnadigital.omnisuite.core.engine.document

/**
 * Shared slide-geometry normalisation for PPTX.
 *
 * Historically the viewer screen path and the PDF/bitmap path each clamped a shape rect
 * independently. The ViewModel clamped the *rect* (shrinking width to `1f - cx`), while
 * OfficeConverter clamped each *edge* separately, so a shape at x = 0.98 with w = 0.5 was
 * drawn 1.48x the slide width on export while the screen showed it clipped. Screen output and
 * print / PPTX-to-PDF output therefore disagreed for the same file, which is the structural
 * reason the PPTX rendering work had to be reverted once.
 *
 * One implementation, called from every path, removes that class of disagreement.
 */
internal object PptxGeometry {

    /** Largest normalised origin we accept, so a shape always keeps visible width/height. */
    private const val MAX_ORIGIN = 0.99f
    private const val MIN_EXTENT = 0.001f

    /**
     * Clamps a normalised rect (x, y, w, h in 0..1) so it lies entirely inside the slide.
     * Returns [x, y, w, h] with w and h shrunk to whatever room remains after the origin.
     */
    fun clampRect(x: Float, y: Float, w: Float, h: Float): FloatArray {
        val cx = x.coerceIn(0f, MAX_ORIGIN)
        val cy = y.coerceIn(0f, MAX_ORIGIN)
        val cw = w.coerceIn(MIN_EXTENT, 1f - cx)
        val ch = h.coerceIn(MIN_EXTENT, 1f - cy)
        return floatArrayOf(cx, cy, cw, ch)
    }
}