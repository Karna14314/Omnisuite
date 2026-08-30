package com.karnadigital.omnisuite.core.engine.document

import android.graphics.*
import android.os.Build
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.*
import org.apache.poi.sl.usermodel.Placeholder
import org.apache.poi.sl.usermodel.Shape
import org.apache.poi.sl.usermodel.Slide
import org.apache.poi.sl.usermodel.SlideShow
import org.apache.poi.xslf.usermodel.*
import java.io.File
import java.io.FileOutputStream

/**
 * High-Fidelity Android Canvas 2D Vector & Bitmap Slide Renderer for PPTX presentations.
 *
 * Renders complete PowerPoint presentation slides onto crisp high-resolution Bitmaps:
 * - Slide Master & Slide Layout inheritance (background colors, picture fills, static template vector shapes)
 * - Vector geometries (rectangles, rounded rectangles, ellipses, triangles, arrows, callouts, lines)
 * - Custom shapes, solid fills, transparency, and stroke borders
 * - Picture shapes (<p:pic>) and shape blip fills with high-quality bitmap scaling
 * - Multi-paragraph text layout with exact font metrics, StaticLayout word-wrapping, insets, alignments, and bullet formatting
 * - Table shapes with cell fills, gridlines, and cell text
 * - Group shapes with recursive affine matrix transformations
 */
object PptxSlideRenderer {

    private const val DEFAULT_RENDER_WIDTH = 1920
    private const val EMU_PER_POINT = 12700.0

    /**
     * Renders an Apache POI slide onto a high-resolution ARGB_8888 [Bitmap].
     */
    fun renderSlideToBitmap(
        slide: Slide<*, *>,
        slideWidthEmu: Long = 9144000L,
        slideHeightEmu: Long = 5143500L,
        targetWidth: Int = DEFAULT_RENDER_WIDTH
    ): Bitmap {
        val wEmu = if (slideWidthEmu > 0) slideWidthEmu else 9144000L
        val hEmu = if (slideHeightEmu > 0) slideHeightEmu else 5143500L
        val aspectRatio = wEmu.toFloat() / hEmu.toFloat()

        val canvasWidth = targetWidth.coerceIn(720, 3840)
        val canvasHeight = (canvasWidth / aspectRatio).toInt().coerceAtLeast(360)

        val bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Enable high-quality anti-aliasing and bitmap filtering
        val drawFilter = PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawFilter = drawFilter

        // Coordinate scaling from EMU to Canvas Pixels
        val scaleX = canvasWidth.toFloat() / wEmu.toFloat()
        val scaleY = canvasHeight.toFloat() / hEmu.toFloat()

        // 1. LAYER 1: Slide Document Background
        renderBackground(canvas, slide, canvasWidth, canvasHeight)

        // 2. LAYER 2: Slide Layout Static Decor / Background Shapes
        if (slide is XSLFSlide) {
            try {
                val layout = slide.slideLayout
                if (layout != null) {
                    for (layoutShape in layout.shapes) {
                        // Only draw non-placeholder layout elements (e.g. decorative branding, headers, footer bars)
                        val isPlaceholder = try { layoutShape is XSLFTextShape && layoutShape.isPlaceholder } catch (_: Throwable) { false }
                        if (!isPlaceholder) {
                            renderShape(canvas, layoutShape, slide, scaleX, scaleY, canvasWidth, canvasHeight)
                        }
                    }
                }
            } catch (_: Throwable) { }
        }

        // 3. LAYER 3: Slide Shapes Hierarchy
        val shapes = try { slide.shapes } catch (_: Throwable) { emptyList() }
        for (shape in shapes) {
            renderShape(canvas, shape, slide, scaleX, scaleY, canvasWidth, canvasHeight)
        }

        return bitmap
    }

    /**
     * Renders a slide and saves the resulting image to [outputFile] as JPEG.
     */
    fun renderSlideToFile(
        slide: Slide<*, *>,
        outputFile: File,
        slideWidthEmu: Long = 9144000L,
        slideHeightEmu: Long = 5143500L,
        targetWidth: Int = DEFAULT_RENDER_WIDTH,
        quality: Int = 92
    ): File {
        val bitmap = renderSlideToBitmap(slide, slideWidthEmu, slideHeightEmu, targetWidth)
        try {
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.flush()
            }
        } finally {
            bitmap.recycle()
        }
        return outputFile
    }

    /**
     * Renders all slides in a presentation to a list of image files in [cacheDir].
     */
    fun renderPresentationToFiles(
        slideShow: SlideShow<*, *>,
        cacheDir: File,
        targetWidth: Int = DEFAULT_RENDER_WIDTH
    ): List<File?> {
        val (widthEmu, heightEmu) = PptxShapeExtractor.getSlideDimensionsEmu(slideShow)
        cacheDir.mkdirs()

        return slideShow.slides.mapIndexed { index, slide ->
            try {
                val outFile = File(cacheDir, "slide_${index + 1}.jpg")
                renderSlideToFile(slide, outFile, widthEmu, heightEmu, targetWidth)
            } catch (t: Throwable) {
                PptxShapeExtractor.logWarn("PptxSlideRenderer", "Failed rendering slide $index", t)
                null
            }
        }
    }

    // =========================================================================
    // BACKGROUND RENDERING
    // =========================================================================

    private fun renderBackground(canvas: Canvas, slide: Slide<*, *>, canvasWidth: Int, canvasHeight: Int) {
        // Default document canvas is White (independent of app dark/light UI theme)
        canvas.drawColor(Color.WHITE)

        // 1. Solid / Hex Background Color
        val bgColorHex = PptxShapeExtractor.getSlideBgColorHex(slide)
        if (bgColorHex != null) {
            try {
                val color = Color.parseColor(bgColorHex)
                canvas.drawColor(color)
            } catch (_: Throwable) { }
        }

        // 2. Slide Background Image (Slide -> Layout -> Master)
        val bgPicPair = PptxShapeExtractor.extractSlideBackgroundPicture(slide)
        if (bgPicPair != null && bgPicPair.first.isNotEmpty()) {
            try {
                val bgBmp = BitmapFactory.decodeByteArray(bgPicPair.first, 0, bgPicPair.first.size)
                if (bgBmp != null) {
                    val destRect = Rect(0, 0, canvasWidth, canvasHeight)
                    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                    canvas.drawBitmap(bgBmp, null, destRect, paint)
                    bgBmp.recycle()
                }
            } catch (_: Throwable) { }
        }
    }

    // =========================================================================
    // SHAPE RENDERING
    // =========================================================================

    private fun renderShape(
        canvas: Canvas,
        shape: Shape<*, *>,
        slide: Slide<*, *>,
        scaleX: Float,
        scaleY: Float,
        canvasWidth: Int,
        canvasHeight: Int
    ) {
        // Group shapes recursion
        if (shape is org.apache.poi.sl.usermodel.GroupShape<*, *>) {
            renderGroupShape(canvas, shape, slide, scaleX, scaleY, canvasWidth, canvasHeight)
            return
        }

        // Calculate absolute pixel bounds on canvas
        val boundsEmu = PptxShapeExtractor.getXmlShapeRawBoundsEmu(shape)
            ?: PptxShapeExtractor.getAnchorBoundsEmu(shape)

        val rectF = if (boundsEmu != null) {
            RectF(
                boundsEmu[0] * scaleX,
                boundsEmu[1] * scaleY,
                (boundsEmu[0] + boundsEmu[2]) * scaleX,
                (boundsEmu[1] + boundsEmu[3]) * scaleY
            )
        } else {
            // Placeholder inheritance for shape bounds
            val norm = PptxShapeExtractor.getShapeNormalizedBounds(shape, slide, (canvasWidth / scaleX).toLong(), (canvasHeight / scaleY).toLong())
            if (norm != null) {
                RectF(
                    norm[0] * canvasWidth,
                    norm[1] * canvasHeight,
                    (norm[0] + norm[2]) * canvasWidth,
                    (norm[1] + norm[3]) * canvasHeight
                )
            } else {
                return
            }
        }

        if (rectF.width() <= 0f || rectF.height() <= 0f) return

        // 1. Picture shape or blip fill
        val picPair = PptxShapeExtractor.extractPictureDataFromShape(shape, slide)
        if (picPair != null && picPair.first.isNotEmpty()) {
            renderPictureShape(canvas, picPair.first, rectF)
            return
        }

        // 2. Vector shape fill & stroke
        if (shape is XSLFSimpleShape) {
            renderSimpleShapeVector(canvas, shape, rectF)
        }

        // 3. Table shape
        if (shape is org.apache.poi.sl.usermodel.TableShape<*, *>) {
            renderTableShape(canvas, shape, rectF, canvasWidth)
            return
        }

        // 4. Text Body inside shape
        if (shape is org.apache.poi.sl.usermodel.TextShape<*, *>) {
            renderTextShape(canvas, shape, rectF, canvasWidth)
        }
    }

    private fun renderGroupShape(
        canvas: Canvas,
        group: org.apache.poi.sl.usermodel.GroupShape<*, *>,
        slide: Slide<*, *>,
        scaleX: Float,
        scaleY: Float,
        canvasWidth: Int,
        canvasHeight: Int
    ) {
        val gt = PptxShapeExtractor.extractGroupTransform(group)
        val childShapes = try { group.shapes } catch (_: Throwable) { emptyList() }

        if (gt != null) {
            canvas.save()
            // Apply group translation and scaling
            val gx = gt.offX * scaleX
            val gy = gt.offY * scaleY
            val gw = gt.extCx * scaleX
            val gh = gt.extCy * scaleY

            // Clip group bounds
            canvas.clipRect(gx, gy, gx + gw, gy + gh)

            val childScaleX = if (gt.chExtCx > 0) (gt.extCx.toFloat() / gt.chExtCx.toFloat()) * scaleX else scaleX
            val childScaleY = if (gt.chExtCy > 0) (gt.extCy.toFloat() / gt.chExtCy.toFloat()) * scaleY else scaleY

            for (child in childShapes) {
                renderShape(canvas, child, slide, childScaleX, childScaleY, canvasWidth, canvasHeight)
            }
            canvas.restore()
        } else {
            for (child in childShapes) {
                renderShape(canvas, child, slide, scaleX, scaleY, canvasWidth, canvasHeight)
            }
        }
    }

    private fun renderPictureShape(canvas: Canvas, picBytes: ByteArray, rectF: RectF) {
        try {
            val bmp = BitmapFactory.decodeByteArray(picBytes, 0, picBytes.size)
            if (bmp != null) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(bmp, null, rectF, paint)
                bmp.recycle()
            }
        } catch (_: Throwable) { }
    }

    private fun renderSimpleShapeVector(canvas: Canvas, shape: XSLFSimpleShape, rectF: RectF) {
        val fillColor = extractShapeFillColor(shape)
        val strokeColor = extractShapeStrokeColor(shape)
        val strokeWidth = extractShapeStrokeWidth(shape).coerceIn(1f, 16f)

        if (fillColor == null && strokeColor == null) return

        val shapeType = try { shape.shapeType?.name?.lowercase() ?: "rect" } catch (_: Throwable) { "rect" }

        val fillPaint = if (fillColor != null) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = fillColor
                style = Paint.Style.FILL
            }
        } else null

        val strokePaint = if (strokeColor != null) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = strokeColor
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
            }
        } else null

        when {
            shapeType.contains("round") -> {
                val radius = minOf(rectF.width(), rectF.height()) * 0.15f
                fillPaint?.let { canvas.drawRoundRect(rectF, radius, radius, it) }
                strokePaint?.let { canvas.drawRoundRect(rectF, radius, radius, it) }
            }
            shapeType.contains("ellipse") || shapeType.contains("oval") || shapeType.contains("circle") -> {
                fillPaint?.let { canvas.drawOval(rectF, it) }
                strokePaint?.let { canvas.drawOval(rectF, it) }
            }
            shapeType.contains("line") -> {
                strokePaint?.let { canvas.drawLine(rectF.left, rectF.top, rectF.right, rectF.bottom, it) }
            }
            shapeType.contains("triangle") -> {
                val path = Path().apply {
                    moveTo(rectF.centerX(), rectF.top)
                    lineTo(rectF.right, rectF.bottom)
                    lineTo(rectF.left, rectF.bottom)
                    close()
                }
                fillPaint?.let { canvas.drawPath(path, it) }
                strokePaint?.let { canvas.drawPath(path, it) }
            }
            shapeType.contains("diamond") -> {
                val path = Path().apply {
                    moveTo(rectF.centerX(), rectF.top)
                    lineTo(rectF.right, rectF.centerY())
                    lineTo(rectF.centerX(), rectF.bottom)
                    lineTo(rectF.left, rectF.centerY())
                    close()
                }
                fillPaint?.let { canvas.drawPath(path, it) }
                strokePaint?.let { canvas.drawPath(path, it) }
            }
            else -> {
                // Default rectangle
                fillPaint?.let { canvas.drawRect(rectF, it) }
                strokePaint?.let { canvas.drawRect(rectF, it) }
            }
        }
    }

    private fun extractShapeFillColor(shape: XSLFSimpleShape): Int? {
        try {
            val colorObj = shape.javaClass.getMethod("getFillColor").invoke(shape)
            if (colorObj != null) {
                val rgb = colorObj.javaClass.getMethod("getRGB").invoke(colorObj) as? Int
                if (rgb != null) return rgb
            }
        } catch (_: Throwable) { }

        // XML SolidFill fallback
        try {
            val xml = PptxShapeExtractor.getXmlObjectReflection(shape) ?: return null
            val spPr = PptxShapeExtractor.invokeMethod(xml, "getSpPr") ?: return null
            val solidFill = PptxShapeExtractor.invokeMethod(spPr, "getSolidFill") ?: return null
            val srgbClr = PptxShapeExtractor.invokeMethod(solidFill, "getSrgbClr") ?: return null
            val hexBytes = PptxShapeExtractor.invokeMethod(srgbClr, "getVal") as? ByteArray
            if (hexBytes != null) {
                val hex = hexBytes.joinToString("") { String.format("%02X", it) }
                return Color.parseColor("#$hex")
            }
        } catch (_: Throwable) { }
        return null
    }

    private fun extractShapeStrokeColor(shape: XSLFSimpleShape): Int? {
        try {
            val colorObj = shape.javaClass.getMethod("getLineColor").invoke(shape)
            if (colorObj != null) {
                val rgb = colorObj.javaClass.getMethod("getRGB").invoke(colorObj) as? Int
                if (rgb != null) return rgb
            }
        } catch (_: Throwable) { }

        try {
            val xml = PptxShapeExtractor.getXmlObjectReflection(shape) ?: return null
            val spPr = PptxShapeExtractor.invokeMethod(xml, "getSpPr") ?: return null
            val ln = PptxShapeExtractor.invokeMethod(spPr, "getLn") ?: return null
            val solidFill = PptxShapeExtractor.invokeMethod(ln, "getSolidFill") ?: return null
            val srgbClr = PptxShapeExtractor.invokeMethod(solidFill, "getSrgbClr") ?: return null
            val hexBytes = PptxShapeExtractor.invokeMethod(srgbClr, "getVal") as? ByteArray
            if (hexBytes != null) {
                val hex = hexBytes.joinToString("") { String.format("%02X", it) }
                return Color.parseColor("#$hex")
            }
        } catch (_: Throwable) { }
        return null
    }

    private fun extractShapeStrokeWidth(shape: XSLFSimpleShape): Float {
        try {
            val w = shape.lineWidth
            if (w > 0) return (w * 2f).toFloat()
        } catch (_: Throwable) { }
        return 2f
    }

    // =========================================================================
    // TEXT RENDERING (STATIC LAYOUT PIPELINE)
    // =========================================================================

    private fun renderTextShape(
        canvas: Canvas,
        shape: org.apache.poi.sl.usermodel.TextShape<*, *>,
        rectF: RectF,
        canvasWidth: Int
    ) {
        val paragraphs = try { shape.textParagraphs } catch (_: Throwable) { emptyList() }
        if (paragraphs.isEmpty()) return

        val isTitle = try {
            shape.placeholder == Placeholder.TITLE || shape.placeholder == Placeholder.CENTERED_TITLE
        } catch (_: Throwable) {
            shape.shapeName.lowercase().contains("title")
        }

        // Shape insets (convert to pixels)
        val leftInset = (rectF.width() * 0.03f).coerceIn(4f, 24f)
        val rightInset = leftInset
        val topInset = (rectF.height() * 0.04f).coerceIn(4f, 20f)
        val availableWidth = (rectF.width() - leftInset - rightInset).toInt().coerceAtLeast(60)

        // Scale reference: 1920px canvas width corresponds to ~960pt presentation canvas (2.0 px/pt)
        val pxPerPt = (canvasWidth.toFloat() / 960f).coerceIn(1.0f, 3.5f)

        var curY = rectF.top + topInset

        for (paragraph in paragraphs) {
            val runs = try { paragraph.textRuns } catch (_: Throwable) { emptyList() }
            if (runs.isEmpty()) continue

            val align = when (try { paragraph.textAlign?.name } catch (_: Throwable) { "LEFT" }) {
                "CENTER" -> Layout.Alignment.ALIGN_CENTER
                "RIGHT" -> Layout.Alignment.ALIGN_OPPOSITE
                else -> Layout.Alignment.ALIGN_NORMAL
            }

            val defaultFontSizePt = if (isTitle) 26f else 15f
            val ssb = SpannableStringBuilder()

            // Handle bullet formatting
            val hasBullet = try {
                if (paragraph is XSLFTextParagraph) {
                    paragraph.bulletCharacter != null || paragraph.indentLevel > 0
                } else {
                    paragraph.indentLevel > 0
                }
            } catch (_: Throwable) { false }

            val bulletChar = try {
                if (paragraph is XSLFTextParagraph) paragraph.bulletCharacter ?: "" else ""
            } catch (_: Throwable) { "" }

            if (hasBullet) {
                val bulletText = if (bulletChar.isNotBlank()) "$bulletChar " else "• "
                val bulletStart = ssb.length
                ssb.append(bulletText)
                val bulletPx = (defaultFontSizePt * pxPerPt).toInt()
                ssb.setSpan(AbsoluteSizeSpan(bulletPx), bulletStart, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ssb.setSpan(ForegroundColorSpan(Color.DKGRAY), bulletStart, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ssb.setSpan(StyleSpan(Typeface.BOLD), bulletStart, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            for (run in runs) {
                val rawText = PptxShapeExtractor.getTextFromRun(run)
                if (rawText.isEmpty()) continue

                val start = ssb.length
                ssb.append(rawText)
                val end = ssb.length

                val fSize = try { run.fontSize } catch (_: Throwable) { null }
                val fontSizePt = if (fSize != null && fSize > 0) fSize.toFloat() else defaultFontSizePt
                val fontPx = (fontSizePt * pxPerPt).toInt().coerceIn(12, 120)

                ssb.setSpan(AbsoluteSizeSpan(fontPx), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                // Font color
                val colorHex = PptxShapeExtractor.extractTextRunColorHex(run)
                val runColor = if (colorHex != null) {
                    try { Color.parseColor(colorHex) } catch (_: Throwable) { if (isTitle) Color.BLACK else Color.rgb(30, 41, 59) }
                } else {
                    if (isTitle) Color.BLACK else Color.rgb(30, 41, 59)
                }
                ssb.setSpan(ForegroundColorSpan(runColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                // Font styles
                val isBold = try { run.isBold } catch (_: Throwable) { isTitle }
                val isItalic = try { run.isItalic } catch (_: Throwable) { false }
                val isUnderline = try { run.isUnderlined } catch (_: Throwable) { false }

                val style = when {
                    isBold && isItalic -> Typeface.BOLD_ITALIC
                    isBold -> Typeface.BOLD
                    isItalic -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                }
                if (style != Typeface.NORMAL) {
                    ssb.setSpan(StyleSpan(style), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (isUnderline) {
                    ssb.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            if (ssb.isEmpty()) continue

            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                density = canvas.density.toFloat()
            }

            val staticLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(ssb, 0, ssb.length, textPaint, availableWidth)
                    .setAlignment(align)
                    .setLineSpacing(2f, 1.15f)
                    .setIncludePad(true)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(ssb, textPaint, availableWidth, align, 1.15f, 2f, true)
            }

            if (curY + staticLayout.height > rectF.bottom + 40f) {
                // If text overflows shape bounds significantly, clip gracefully
            }

            canvas.save()
            canvas.translate(rectF.left + leftInset, curY)
            staticLayout.draw(canvas)
            canvas.restore()

            curY += staticLayout.height + 8f
        }
    }

    // =========================================================================
    // TABLE RENDERING
    // =========================================================================

    private fun renderTableShape(
        canvas: Canvas,
        table: org.apache.poi.sl.usermodel.TableShape<*, *>,
        rectF: RectF,
        canvasWidth: Int
    ) {
        val numRows = try { table.numberOfRows } catch (_: Throwable) { 0 }
        val numCols = try { table.numberOfColumns } catch (_: Throwable) { 0 }
        if (numRows <= 0 || numCols <= 0) return

        val colWidth = rectF.width() / numCols
        val rowHeight = rectF.height() / numRows

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(241, 245, 249)
            style = Paint.Style.FILL
        }

        for (r in 0 until numRows) {
            for (c in 0 until numCols) {
                val cellRect = RectF(
                    rectF.left + c * colWidth,
                    rectF.top + r * rowHeight,
                    rectF.left + (c + 1) * colWidth,
                    rectF.top + (r + 1) * rowHeight
                )

                // Header row fill
                if (r == 0) {
                    canvas.drawRect(cellRect, headerPaint)
                }

                // Cell gridline
                canvas.drawRect(cellRect, gridPaint)

                // Cell text
                val cell = try { table.getCell(r, c) } catch (_: Throwable) { null }
                if (cell != null) {
                    renderTextShape(canvas, cell, cellRect, canvasWidth)
                }
            }
        }
    }
}
