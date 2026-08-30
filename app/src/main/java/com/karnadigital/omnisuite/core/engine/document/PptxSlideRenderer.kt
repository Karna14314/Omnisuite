package com.karnadigital.omnisuite.core.engine.document

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// =============================================================================
// COMPOSE RENDERER — single implementation for all views
// =============================================================================

@Composable
fun ParsedSlideView(
    slide: ParsedSlide,
    modifier: Modifier = Modifier,
    minTextSp: Float = 9f,
) {
    BoxWithConstraints(modifier = modifier) {
        val w = maxWidth
        val h = maxHeight

        // Layer 1: background
        DrawBackground(slide.background, w, h)

        // Layer 2: shapes in z-order
        val density = LocalDensity.current
        for (shape in slide.shapes) {
            val leftDp = w * shape.bounds.left
            val topDp = h * shape.bounds.top
            val widthDp = w * shape.bounds.width
            val heightDp = h * shape.bounds.height
            Box(
                modifier = Modifier
                    .offset(x = leftDp, y = topDp)
                    .size(widthDp, heightDp)
            ) {
                DrawShape(shape.content, minTextSp, with(density) { widthDp.toPx() })
            }
        }
    }
}

@Composable
private fun DrawBackground(bg: SlideBackground, w: Dp, h: Dp) {
    when (bg) {
        is BgNone -> Box(modifier = Modifier.size(w, h).background(Color.White))
        is BgSolid -> {
            val color = parseColor(bg.colorHex, Color.White)
            Box(modifier = Modifier.size(w, h).background(color))
        }
        is BgGradient -> {
            val brush = remember(bg.stops) {
                val colorStops = bg.stops.map { (pos, hex) ->
                    pos to parseColor(hex, Color.White)
                }.toTypedArray()
                Brush.linearGradient(colorStops = colorStops)
            }
            Box(modifier = Modifier.size(w, h).background(brush))
        }
        is BgImage -> {
            val bmp = remember(bg.bytes) {
                BitmapFactory.decodeByteArray(bg.bytes, 0, bg.bytes.size)
            }
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.size(w, h),
                )
            } else {
                Box(modifier = Modifier.size(w, h).background(Color.White))
            }
        }
    }
}

@Composable
private fun DrawShape(content: ShapeContent, minTextSp: Float, shapeWidthPx: Float) {
    when (content) {
        is TextContent -> TextShapeLayout(content, minTextSp)
        is ImageContent -> ImageShapeLayout(content)
        is VectorContent -> VectorShapeLayout(content)
        is GroupContent -> GroupShapeLayout(content, minTextSp)
        is TableContent -> TableShapeLayout(content, minTextSp)
        is DecorativeContent -> Unit
    }
}

// -----------------------------------------------------------------------------
// Text
// -----------------------------------------------------------------------------

@Composable
private fun TextShapeLayout(content: TextContent, minTextSp: Float) {
    Column(modifier = Modifier.fillMaxSize().padding(4.dp)) {
        for (para in content.paragraphs) {
            val annotated = buildParagraphAnnotated(para, minTextSp)
            val align = when (para.alignment) {
                TextAlignment.CENTER -> TextAlign.Center
                TextAlignment.RIGHT -> TextAlign.End
                TextAlignment.JUSTIFY -> TextAlign.Justify
                else -> TextAlign.Start
            }
            Text(
                text = annotated,
                textAlign = align,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun buildParagraphAnnotated(para: ParsedParagraph, minTextSp: Float): AnnotatedString {
    return buildAnnotatedString {
        val bullet = if (para.bulletLevel > 0 || para.bulletChar.isNotBlank()) {
            para.bulletChar.ifBlank { "•" }
        } else ""
        if (bullet.isNotBlank()) {
            append("$bullet ")
        }
        for (run in para.runs) {
            val sizeSp = run.sizePt.let { pt ->
                if (pt > 0f) (pt * 0.75f).coerceAtLeast(minTextSp) else (minTextSp * 1.2f)
            }
            val color = run.colorHex?.let { parseColor(it, null) } ?: Color(0xFF1E293B)
            val weight = when {
                run.bold && run.italic -> FontWeight.Bold
                run.bold -> FontWeight.Bold
                else -> FontWeight.Normal
            }
            val style = SpanStyle(
                color = color,
                fontSize = sizeSp.sp,
                fontWeight = weight,
                fontStyle = if (run.italic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = if (run.underline) TextDecoration.Underline else TextDecoration.None,
                fontFamily = run.fontFamily?.let { FontFamily.Default } ?: FontFamily.Default,
            )
            withStyle(style) { append(run.text) }
        }
    }
}

// -----------------------------------------------------------------------------
// Image
// -----------------------------------------------------------------------------

@Composable
private fun ImageShapeLayout(content: ImageContent) {
    val bmp = remember(content.bytes) {
        BitmapFactory.decodeByteArray(content.bytes, 0, content.bytes.size)
    }
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// -----------------------------------------------------------------------------
// Vector
// -----------------------------------------------------------------------------

@Composable
private fun VectorShapeLayout(content: VectorContent) {
    val fillColor = content.fillHex?.let { parseColor(it, null) }
    val strokeColor = content.strokeHex?.let { parseColor(it, null) }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cw = size.width
        val ch = size.height
        when (content.kind) {
            VectorKind.ELLIPSE -> {
                fillColor?.let { drawOval(it, topLeft = Offset.Zero, size = size) }
                strokeColor?.let { drawOval(it, topLeft = Offset.Zero, size = size, style = Stroke(width = content.strokeWidthPt.coerceAtLeast(1f))) }
            }
            VectorKind.TRIANGLE -> {
                val path = Path().apply {
                    moveTo(cw / 2f, 0f); lineTo(cw, ch); lineTo(0f, ch); close()
                }
                fillColor?.let { drawPath(path, it) }
                strokeColor?.let { drawPath(path, it, style = Stroke(width = content.strokeWidthPt.coerceAtLeast(1f))) }
            }
            VectorKind.DIAMOND -> {
                val path = Path().apply {
                    moveTo(cw / 2f, 0f); lineTo(cw, ch / 2f); lineTo(cw / 2f, ch); lineTo(0f, ch / 2f); close()
                }
                fillColor?.let { drawPath(path, it) }
                strokeColor?.let { drawPath(path, it, style = Stroke(width = content.strokeWidthPt.coerceAtLeast(1f))) }
            }
            VectorKind.LINE -> {
                strokeColor?.let {
                    drawLine(it, Offset(0f, ch), Offset(cw, 0f), strokeWidth = content.strokeWidthPt.coerceAtLeast(2f))
                }
            }
            VectorKind.ROUNDED_RECTANGLE -> {
                fillColor?.let { drawRoundRect(it, topLeft = Offset.Zero, size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(kotlin.math.min(cw, ch) * 0.15f)) }
                strokeColor?.let { drawRoundRect(it, topLeft = Offset.Zero, size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(kotlin.math.min(cw, ch) * 0.15f), style = Stroke(width = content.strokeWidthPt.coerceAtLeast(1f))) }
            }
            else -> { // RECTANGLE + OTHER
                fillColor?.let { drawRect(it, topLeft = Offset.Zero, size = size) }
                strokeColor?.let { drawRect(it, topLeft = Offset.Zero, size = size, style = Stroke(width = content.strokeWidthPt.coerceAtLeast(1f))) }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Group — children already carry slide-space bounds via the parent walk;
// here we only need to fill the group extent (children positioned by parent).
// -----------------------------------------------------------------------------

@Composable
private fun GroupShapeLayout(content: GroupContent, minTextSp: Float) {
    // Children are rendered by the parent ParsedSlideView walk since they are
    // flat in the shape list with resolved slide-space bounds. The group shape
    // itself carries its extent; nothing additional to draw here.
}

// -----------------------------------------------------------------------------
// Table
// -----------------------------------------------------------------------------

@Composable
private fun TableShapeLayout(content: TableContent, minTextSp: Float) {
    if (content.rows.isEmpty()) return
    val numCols = content.rows.maxOf { it.size }
    if (numCols == 0) return
    Column(modifier = Modifier.fillMaxSize()) {
        for (row in content.rows) {
            androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                for (c in 0 until numCols) {
                    val cell = row.getOrNull(c)
                    val bg = cell?.fillHex?.let { parseColor(it, Color.White) } ?: Color.White
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(bg)
                            .padding(2.dp)
                    ) {
                        if (cell != null) {
                            Text(
                                text = cell.text,
                                fontSize = (minTextSp * 0.9f).coerceAtLeast(7f).sp,
                                color = Color(0xFF1E293B),
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Color helper
// -----------------------------------------------------------------------------

private fun parseColor(hex: String?, fallback: Color): Color {
    if (hex.isNullOrBlank()) return fallback
    val formatted = if (hex.trim().startsWith("#")) hex.trim() else "#${hex.trim()}"
    return try {
        Color(android.graphics.Color.parseColor(formatted))
    } catch (_: Throwable) { fallback }
}
