package com.karnadigital.omnisuite.core.util

/**
 * Shared DrawingML custom-geometry (`<a:custGeom>`) model + parser.
 *
 * Single source of truth for both the PPTX viewer (Compose [GenericShape]) and
 * the PPTX-to-PDF renderer ([android.graphics.Path]). Pure string parsing —
 * same regex approach as the picture-crop extractor, no POI dependency.
 */

/** Single drawing command inside a DrawingML `<a:path>`. */
sealed class GeomCmd {
    data class MoveTo(val x: Float, val y: Float) : GeomCmd()
    data class LineTo(val x: Float, val y: Float) : GeomCmd()
    data class CubicTo(
        val x1: Float, val y1: Float,
        val x2: Float, val y2: Float,
        val x3: Float, val y3: Float
    ) : GeomCmd()
    data class QuadTo(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : GeomCmd()
    object Close : GeomCmd()
}

/**
 * Parsed `<a:custGeom>` outline in its native coordinate space (`w`/`h`).
 * Callers scale commands into their own pixel/dp box.
 */
data class CustomGeomPath(
    val commands: List<GeomCmd>,
    val viewW: Float,
    val viewH: Float
)

/**
 * Parses the first `<a:path>` of a `<a:custGeom>` block from shape XML text.
 * Supports moveTo/lnTo/cubicBezTo/quadBezTo/close with literal coordinates.
 * Returns null for formula-driven paths or unparseable content (caller falls
 * back to rectangle).
 */
fun parseCustomGeomPath(shapeXml: String): CustomGeomPath? {
    return try {
        if (!shapeXml.contains("custGeom")) return null
        val pathMatch = Regex(
            """<[\w:]*path\b([^>]*)>(.*?)</[\w:]*path>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(shapeXml) ?: return null
        val attrs = pathMatch.groupValues[1]
        val body = pathMatch.groupValues[2]
        fun attr(name: String): Float? {
            val m = Regex("""$name=["'](-?\d+)["']""", RegexOption.IGNORE_CASE).find(attrs)
            return m?.groupValues?.get(1)?.toFloatOrNull()
        }
        val vw = attr("w") ?: return null
        val vh = attr("h") ?: return null
        if (vw <= 0f || vh <= 0f) return null
        fun pts(seg: String): List<Pair<Float, Float>> {
            return Regex("""<[\w:]*pt\b[^>]*x=["'](-?\d+)["'][^>]*y=["'](-?\d+)["']""")
                .findAll(seg)
                .map { m -> m.groupValues[1].toFloat() to m.groupValues[2].toFloat() }
                .toList()
        }
        val cmds = mutableListOf<GeomCmd>()
        val tagRe = Regex(
            """<[\w:]*((?:moveTo|lnTo|cubicBezTo|quadBezTo|close))\b[^>]*(/>|>)""",
            RegexOption.IGNORE_CASE
        )
        for (m in tagRe.findAll(body)) {
            if (cmds.size > 400) break
            val kind = m.groupValues[1].lowercase()
            val selfClosed = m.groupValues[2] == "/>"
            val inner = if (selfClosed) "" else {
                val start = m.range.last + 1
                val endTag = Regex("""</[\w:]*$kind>""", RegexOption.IGNORE_CASE).find(body, start)
                    ?: break
                body.substring(start, endTag.range.first)
            }
            when (kind) {
                "moveto" -> pts(inner).firstOrNull()?.let { cmds.add(GeomCmd.MoveTo(it.first, it.second)) }
                "lnto" -> pts(inner).firstOrNull()?.let { cmds.add(GeomCmd.LineTo(it.first, it.second)) }
                "cubicbezto" -> {
                    val p = pts(inner)
                    if (p.size >= 3) cmds.add(
                        GeomCmd.CubicTo(
                            p[0].first, p[0].second,
                            p[1].first, p[1].second,
                            p[2].first, p[2].second
                        )
                    )
                }
                "quadbezto" -> {
                    val p = pts(inner)
                    if (p.size >= 2) cmds.add(
                        GeomCmd.QuadTo(p[0].first, p[0].second, p[1].first, p[1].second)
                    )
                }
                "close" -> cmds.add(GeomCmd.Close)
            }
        }
        if (cmds.isEmpty()) null else CustomGeomPath(cmds, vw, vh)
    } catch (_: Throwable) {
        null
    }
}

/** Builds an [android.graphics.Path] for [geom] inside the given pixel box. */
fun CustomGeomPath.toAndroidPath(px: Float, py: Float, pw: Float, ph: Float): android.graphics.Path {
    val sx = if (viewW > 0f) pw / viewW else 1f
    val sy = if (viewH > 0f) ph / viewH else 1f
    return android.graphics.Path().apply {
        for (cmd in commands) {
            when (cmd) {
                is GeomCmd.MoveTo -> moveTo(px + cmd.x * sx, py + cmd.y * sy)
                is GeomCmd.LineTo -> lineTo(px + cmd.x * sx, py + cmd.y * sy)
                is GeomCmd.CubicTo -> cubicTo(
                    px + cmd.x1 * sx, py + cmd.y1 * sy,
                    px + cmd.x2 * sx, py + cmd.y2 * sy,
                    px + cmd.x3 * sx, py + cmd.y3 * sy
                )
                is GeomCmd.QuadTo -> quadTo(
                    px + cmd.x1 * sx, py + cmd.y1 * sy,
                    px + cmd.x2 * sx, py + cmd.y2 * sy
                )
                is GeomCmd.Close -> close()
            }
        }
    }
}
