package com.karnadigital.omnisuite.core.engine.document

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.os.Build

/**
 * Canvas rasterizer over the SAME [ParsedSlide] model used by the Compose renderer.
 * Used for PDF export. Walks background + shapes in identical order so the exported
 * bitmap visually matches the on-screen Compose view.
 */
object PptxSlideRasterizer {

    fun renderToBitmap(slide: ParsedSlide, width: Int, height: Int): Bitmap {
        val w = width.coerceAtLeast(360)
        val h = height.coerceAtLeast(200)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawFilter = android.graphics.PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        drawBackground(canvas, slide.background, w, h)

        for (shape in slide.shapes) {
            val left = shape.bounds.left * w
            val top = shape.bounds.top * h
            val right = shape.bounds.right * w
            val bottom = shape.bounds.bottom * h
            drawShape(canvas, shape.content, left, top, right, bottom, w)
        }
        return bitmap
    }

    private fun drawBackground(canvas: Canvas, bg: SlideBackground, w: Int, h: Int) {
        when (bg) {
            is BgNone -> canvas.drawColor(AndroidColor.WHITE)
            is BgSolid -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.color = parseColorInt(bg.colorHex, AndroidColor.WHITE)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }
            is BgGradient -> {
                canvas.drawColor(AndroidColor.WHITE)
                if (bg.stops.isNotEmpty()) {
                    val colors = bg.stops.map { parseColorInt(it.second, AndroidColor.WHITE) }.toIntArray()
                    val positions = bg.stops.map { it.first }.toFloatArray()
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                    paint.shader = android.graphics.LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), colors, positions, android.graphics.Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                }
            }
            is BgImage -> {
                val bmp = BitmapFactory.decodeByteArray(bg.bytes, 0, bg.bytes.size)
                if (bmp != null) {
                    canvas.drawBitmap(bmp, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), null)
                    bmp.recycle()
                } else {
                    canvas.drawColor(AndroidColor.WHITE)
                }
            }
        }
    }

    private fun drawShape(canvas: Canvas, content: ShapeContent, l: Float, t: Float, r: Float, b: Float, canvasW: Int) {
        when (content) {
            is TextContent -> drawText(canvas, content, l, t, r, b, canvasW)
            is ImageContent -> drawImage(canvas, content, l, t, r, b)
            is VectorContent -> drawVector(canvas, content, l, t, r, b)
            is TableContent -> drawTable(canvas, content, l, t, r, b, canvasW)
            is GroupContent -> Unit // children rendered flat by caller
            is DecorativeContent -> Unit
        }
    }

    private fun drawText(canvas: Canvas, content: TextContent, l: Float, t: Float, r: Float, b: Float, canvasW: Int) {
        if (content.paragraphs.isEmpty()) return
        val pw = (r - l).toInt().coerceAtLeast(20)
        val pxPerPt = (canvasW.toFloat() / 960f).coerceIn(1.0f, 3.0f)
        var curY = t + 6f

        for (para in content.paragraphs) {
            if (para.runs.isEmpty()) continue
            val align = when (para.alignment) {
                TextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
                TextAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                TextAlignment.JUSTIFY -> Layout.Alignment.ALIGN_NORMAL
                else -> Layout.Alignment.ALIGN_NORMAL
            }
            val ssb = android.text.SpannableStringBuilder()
            for (run in para.runs) {
                val start = ssb.length
                ssb.append(run.text)
                val end = ssb.length
                val sizePt = if (run.sizePt > 0f) run.sizePt else 14f
                val fontPx = (sizePt * pxPerPt).toInt().coerceIn(10, 120)
                ssb.setSpan(AbsoluteSizeSpan(fontPx), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                val color = run.colorHex?.let { parseColorInt(it, null) } ?: 0xFF1E293B.toInt()
                ssb.setSpan(ForegroundColorSpan(color), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                val style = when {
                    run.bold && run.italic -> android.graphics.Typeface.BOLD_ITALIC
                    run.bold -> android.graphics.Typeface.BOLD
                    run.italic -> android.graphics.Typeface.ITALIC
                    else -> null
                }
                if (style != null) ssb.setSpan(StyleSpan(style), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (run.underline) ssb.setSpan(UnderlineSpan(), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (ssb.isEmpty()) continue

            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
            val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(ssb, 0, ssb.length, paint, pw)
                    .setAlignment(align).setLineSpacing(2f, 1.15f).setIncludePad(true).build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(ssb, paint, pw, align, 1.15f, 2f, true)
            }
            canvas.save()
            canvas.translate(l, curY)
            layout.draw(canvas)
            canvas.restore()
            curY += layout.height + 6f
        }
    }

    private fun drawImage(canvas: Canvas, content: ImageContent, l: Float, t: Float, r: Float, b: Float) {
        val bmp = BitmapFactory.decodeByteArray(content.bytes, 0, content.bytes.size)
        if (bmp != null) {
            canvas.drawBitmap(bmp, null, RectF(l, t, r, b), null)
            bmp.recycle()
        }
    }

    private fun drawVector(canvas: Canvas, content: VectorContent, l: Float, t: Float, r: Float, b: Float) {
        val fill = content.fillHex?.let { parseColorInt(it, null) }
        val stroke = content.strokeHex?.let { parseColorInt(it, null) }
        val rect = RectF(l, t, r, b)
        fill?.let { c ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c; style = Paint.Style.FILL }
            paintVectorShape(canvas, content.kind, rect, p)
        }
        stroke?.let { c ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c; style = Paint.Style.STROKE; strokeWidth = content.strokeWidthPt.coerceAtLeast(1f) }
            paintVectorShape(canvas, content.kind, rect, p)
        }
    }

    private fun paintVectorShape(canvas: Canvas, kind: VectorKind, rect: RectF, paint: Paint) {
        val path = android.graphics.Path()
        when (kind) {
            VectorKind.ELLIPSE -> canvas.drawOval(rect, paint)
            VectorKind.TRIANGLE -> {
                path.moveTo(rect.centerX(), rect.top); path.lineTo(rect.right, rect.bottom); path.lineTo(rect.left, rect.bottom); path.close()
                canvas.drawPath(path, paint)
            }
            VectorKind.DIAMOND -> {
                path.moveTo(rect.centerX(), rect.top); path.lineTo(rect.right, rect.centerY()); path.lineTo(rect.centerX(), rect.bottom); path.lineTo(rect.left, rect.centerY()); path.close()
                canvas.drawPath(path, paint)
            }
            VectorKind.LINE -> canvas.drawLine(rect.left, rect.bottom, rect.right, rect.top, paint)
            VectorKind.ROUNDED_RECTANGLE -> canvas.drawRoundRect(rect, kotlin.math.min(rect.width(), rect.height()) * 0.15f, kotlin.math.min(rect.width(), rect.height()) * 0.15f, paint)
            else -> canvas.drawRect(rect, paint)
        }
    }

    private fun drawTable(canvas: Canvas, content: TableContent, l: Float, t: Float, r: Float, b: Float, canvasW: Int) {
        val rows = content.rows
        if (rows.isEmpty()) return
        val cols = rows.maxOf { it.size }
        if (cols == 0) return
        val cw = (r - l) / cols
        val ch = (b - t) / rows.size
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 1.5f }
        for (ri in rows.indices) {
            for (ci in 0 until cols) {
                val cr = RectF(l + ci * cw, t + ri * ch, l + (ci + 1) * cw, t + (ri + 1) * ch)
                val cell = rows[ri].getOrNull(ci)
                val bg = cell?.fillHex?.let { parseColorInt(it, null) }
                if (bg != null) {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg; style = Paint.Style.FILL }
                    canvas.drawRect(cr, p)
                }
                canvas.drawRect(cr, gridPaint)
            }
        }
    }

    private fun parseColorInt(hex: String?, fallback: Int): Int {
        if (hex.isNullOrBlank()) return fallback
        val formatted = if (hex.trim().startsWith("#")) hex.trim() else "#${hex.trim()}"
        return try { android.graphics.Color.parseColor(formatted) } catch (_: Throwable) { fallback }
    }
}
