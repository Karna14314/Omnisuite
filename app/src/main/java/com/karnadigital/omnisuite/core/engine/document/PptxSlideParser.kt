package com.karnadigital.omnisuite.core.engine.document

import android.util.Log
import org.apache.poi.sl.usermodel.Placeholder
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFPictureData
import org.apache.poi.xslf.usermodel.XSLFShape
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.poi.xslf.usermodel.XSLFSlideLayout
import org.apache.poi.xslf.usermodel.XSLFSlideMaster
import org.apache.poi.xslf.usermodel.XSLFTextParagraph
import org.apache.poi.xslf.usermodel.XSLFTextRun
import javax.inject.Inject
import javax.inject.Singleton

// =============================================================================
// DATA MODEL — independent of Apache POI types
// =============================================================================

/** Normalized slide-space rectangle: all values in 0.0..1.0 of slide width/height. */
data class NormRect(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

enum class TextAlignment { LEFT, CENTER, RIGHT, JUSTIFY }

data class ParsedTextRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    /** Null means "inherit" — renderer applies its own default dark text. */
    val colorHex: String? = null,
    /** Font size in points; 0f means "inherit from placeholder style". */
    val sizePt: Float = 0f,
    val fontFamily: String? = null,
)

data class ParsedParagraph(
    val runs: List<ParsedTextRun>,
    val alignment: TextAlignment = TextAlignment.LEFT,
    val bulletLevel: Int = 0,
    val bulletChar: String = "",
)

sealed interface ShapeContent

data class TextContent(val paragraphs: List<ParsedParagraph>) : ShapeContent

data class ImageContent(val bytes: ByteArray, val contentType: String?) : ShapeContent {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImageContent) return false
        return bytes.contentEquals(other.bytes) && contentType == other.contentType
    }
    override fun hashCode(): Int = 31 * bytes.contentHashCode() + (contentType?.hashCode() ?: 0)
}

enum class VectorKind { RECTANGLE, ROUNDED_RECTANGLE, ELLIPSE, TRIANGLE, DIAMOND, LINE, OTHER }

data class VectorContent(
    val kind: VectorKind,
    val fillHex: String? = null,
    val strokeHex: String? = null,
    val strokeWidthPt: Float = 0f,
) : ShapeContent

data class GroupContent(val children: List<ParsedShape>) : ShapeContent

data class ParsedTableCell(val text: String, val fillHex: String? = null)
data class TableContent(val rows: List<List<ParsedTableCell>>) : ShapeContent

data object DecorativeContent : ShapeContent

data class ParsedShape(
    val bounds: NormRect,
    val content: ShapeContent,
    val zIndex: Int,
)

sealed interface SlideBackground
data object BgNone : SlideBackground
data class BgSolid(val colorHex: String) : SlideBackground
data class BgGradient(val stops: List<Pair<Float, String>>) : SlideBackground
data class BgImage(val bytes: ByteArray, val contentType: String?) : SlideBackground {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BgImage) return false
        return bytes.contentEquals(other.bytes) && contentType == other.contentType
    }
    override fun hashCode(): Int = 31 * bytes.contentHashCode() + (contentType?.hashCode() ?: 0)
}

data class ParsedSlide(
    val index: Int,
    val background: SlideBackground,
    val shapes: List<ParsedShape>,
    val speakerNotes: String? = null,
)

data class ParsedPresentation(
    val slides: List<ParsedSlide>,
    val widthEmu: Long,
    val heightEmu: Long,
) {
    val aspectRatio: Float
        get() = if (heightEmu > 0) widthEmu.toFloat() / heightEmu.toFloat() else 16f / 9f
}

// =============================================================================
// PARSER
// =============================================================================

@Singleton
class PptxSlideParser @Inject constructor() {

    companion object {
        private const val TAG = "PptxSlideParser"
        private const val DEFAULT_W = 9144000L
        private const val DEFAULT_H = 5143500L
    }

    fun parse(slideShow: XMLSlideShow): ParsedPresentation {
        val (w, h) = readSlideDimensions(slideShow)
        val slides = slideShow.slides.mapIndexed { i, slide ->
            parseSlide(slide, i, w, h)
        }
        return ParsedPresentation(slides, w, h)
    }

    // -------------------------------------------------------------------------
    // Slide dimensions
    // -------------------------------------------------------------------------

    private fun readSlideDimensions(slideShow: XMLSlideShow): Pair<Long, Long> {
        // Prefer the public XMLBeans API (no AWT). CTSlideSize.getCx() returns long.
        try {
            val ct = slideShow.ctPresentation
            val sz = ct.sldSz
            val cx = sz.cx
            val cy = sz.cy
            if (cx > 0 && cy > 0) {
                Log.d(TAG, "Slide dimensions from XML API: ${cx}x$cy EMU")
                return Pair(cx, cy)
            }
        } catch (_: Throwable) { }

        // Reflection fallback for unusual POI builds.
        try {
            val ct = invokeMethod(slideShow, "getCTPresentation") ?: return Pair(DEFAULT_W, DEFAULT_H)
            val sz = invokeMethod(ct, "getSldSz") ?: return Pair(DEFAULT_W, DEFAULT_H)
            val cx = extractLong(invokeMethod(sz, "getCx"))
            val cy = extractLong(invokeMethod(sz, "getCy"))
            if (cx != null && cy != null && cx > 0 && cy > 0) {
                Log.d(TAG, "Slide dimensions from reflection: ${cx}x$cy EMU")
                return Pair(cx, cy)
            }
        } catch (_: Throwable) { }
        return Pair(DEFAULT_W, DEFAULT_H)
    }

    // -------------------------------------------------------------------------
    // Per-slide parsing
    // -------------------------------------------------------------------------

    private fun parseSlide(slide: XSLFSlide, index: Int, w: Long, h: Long): ParsedSlide {
        val bg = extractBackground(slide)
        val shapes = walkShapes(slide.shapes, slide, w, h, emptyList())
        val notes = extractNotes(slide)
        return ParsedSlide(index, bg, shapes, notes)
    }

    private fun extractNotes(slide: XSLFSlide): String? {
        return try {
            val notes = slide.notes ?: return null
            val textShapes = notes.shapes.filterIsInstance<org.apache.poi.sl.usermodel.TextShape<*, *>>()
            val sb = StringBuilder()
            for (ts in textShapes) {
                val t = try { ts.text } catch (_: Throwable) { null }
                if (!t.isNullOrBlank()) {
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append(t.trim())
                }
            }
            sb.toString().ifBlank { null }
        } catch (_: Throwable) { null }
    }

    // -------------------------------------------------------------------------
    // Shape tree walk (handles nested groups)
    // -------------------------------------------------------------------------

    private fun walkShapes(
        shapes: List<XSLFShape>,
        slide: XSLFSlide,
        w: Long,
        h: Long,
        groupAncestors: List<GroupTransform>,
    ): List<ParsedShape> {
        val out = mutableListOf<ParsedShape>()
        var z = 0
        for (shape in shapes) {
            try {
                if (shape is org.apache.poi.sl.usermodel.GroupShape<*, *>) {
                    // Flatten: recurse with composed group transform; children carry resolved slide-space bounds.
                    val gt = extractGroupTransform(shape)
                    val nextAncestors = if (gt != null) groupAncestors + gt else groupAncestors
                    val childShapes = try { shape.shapes } catch (_: Throwable) { emptyList() }
                    val children = walkShapes(childShapes, slide, w, h, nextAncestors)
                    for (c in children) {
                        out.add(c.copy(zIndex = z++))
                    }
                } else {
                    val parsed = parseLeafShape(shape, slide, w, h, groupAncestors, z)
                    if (parsed != null) out.add(parsed)
                    z++
                }
            } catch (_: Throwable) { }
        }
        return out
    }

    private fun parseLeafShape(
        shape: XSLFShape,
        slide: XSLFSlide,
        w: Long,
        h: Long,
        groupAncestors: List<GroupTransform>,
        z: Int,
    ): ParsedShape? {
        val bounds = resolveBounds(shape, slide, w, h, groupAncestors) ?: return null
        val content = extractContent(shape, slide)
        return ParsedShape(bounds, content, z)
    }

    // -------------------------------------------------------------------------
    // Bounds resolution chain: own xfrm → layout placeholder → master placeholder → omit
    // -------------------------------------------------------------------------

    private fun resolveBounds(
        shape: XSLFShape,
        slide: XSLFSlide,
        w: Long,
        h: Long,
        groupAncestors: List<GroupTransform>,
    ): NormRect? {
        if (w <= 0 || h <= 0) return null

        // Strategy 1: own <a:xfrm> + group ancestry composition
        val raw = readOwnXfrm(shape)
        if (raw != null) {
            var (x, y, cx, cy) = raw
            for (gt in groupAncestors.asReversed()) {
                val t = gt.transform(x, y, cx, cy)
                x = t[0]; y = t[1]; cx = t[2]; cy = t[3]
            }
            if (cx > 0 && cy > 0) {
                return NormRect(
                    (x.toFloat() / w.toFloat()).coerceIn(0f, 1f),
                    (y.toFloat() / h.toFloat()).coerceIn(0f, 1f),
                    (cx.toFloat() / w.toFloat()).coerceIn(0.001f, 1f),
                    (cy.toFloat() / h.toFloat()).coerceIn(0.001f, 1f),
                )
            }
        }

        // Strategy 2: placeholder inheritance chain (layout → master)
        val phInfo = extractPlaceholderInfo(shape)
        if (phInfo != null) {
            val resolved = resolvePlaceholderBounds(phInfo, slide, w, h)
            if (resolved != null) {
                var (x, y, cx, cy) = resolved
                for (gt in groupAncestors.asReversed()) {
                    val t = gt.transform(x, y, cx, cy)
                    x = t[0]; y = t[1]; cx = t[2]; cy = t[3]
                }
                if (cx > 0 && cy > 0) {
                    return NormRect(
                        (x.toFloat() / w.toFloat()).coerceIn(0f, 1f),
                        (y.toFloat() / h.toFloat()).coerceIn(0f, 1f),
                        (cx.toFloat() / w.toFloat()).coerceIn(0.001f, 1f),
                        (cy.toFloat() / h.toFloat()).coerceIn(0.001f, 1f),
                    )
                }
            }
        }

        // No fallback. Unresolved → omit.
        return null
    }

    private fun readOwnXfrm(shape: XSLFShape): LongArray? {
        val xml = getXmlObject(shape) ?: return null
        return readXfrmFromXml(xml)
    }

    private fun readXfrmFromXml(xml: Any): LongArray? {
        try {
            var xfrm: Any? = invokeMethod(xml, "getXfrm")
            if (xfrm == null) {
                val spPr = invokeMethod(xml, "getSpPr")
                if (spPr != null) xfrm = invokeMethod(spPr, "getXfrm")
            }
            if (xfrm == null) return null

            val off = invokeMethod(xfrm, "getOff")
            val ext = invokeMethod(xfrm, "getExt")
            val x = extractLong(invokeMethod(off, "getX"))
            val y = extractLong(invokeMethod(off, "getY"))
            val cx = extractLong(invokeMethod(ext, "getCx"))
            val cy = extractLong(invokeMethod(ext, "getCy"))
            if (x != null && y != null && cx != null && cy != null && cx > 0 && cy > 0) {
                return longArrayOf(x, y, cx, cy)
            }
        } catch (_: Throwable) { }
        return null
    }

    // -------------------------------------------------------------------------
    // Placeholder matching & inheritance
    // -------------------------------------------------------------------------

    data class PlaceholderInfo(
        val typeName: String?,
        val placeholder: Placeholder?,
        val idx: Long?,
        val hasExplicitType: Boolean,
        val hasExplicitIdx: Boolean,
    )

    private fun extractPlaceholderInfo(shape: XSLFShape): PlaceholderInfo? {
        var phEnum: Placeholder? = null
        var phTypeName: String? = null
        var phIdx: Long? = null
        var hasExplicitType = false
        var hasExplicitIdx = false

        // 1. POI PlaceholderDetails
        try {
            val phDetails = shape.placeholderDetails
            val p = try { invokeMethod(phDetails, "getPlaceholder") as? Placeholder } catch (_: Throwable) { null }
            if (p != null) {
                phEnum = p; phTypeName = p.name.lowercase(); hasExplicitType = true
            }
            val rawIdx = try { invokeMethod(phDetails, "getIndex") as? Number } catch (_: Throwable) { null }
            if (rawIdx != null && rawIdx.toInt() >= 0) {
                phIdx = rawIdx.toLong(); hasExplicitIdx = true
            }
        } catch (_: Throwable) { }

        // 2. XML nvPr → ph
        try {
            val xml = getXmlObject(shape)
            if (xml != null) {
                var nvPr: Any? = invokeMethod(xml, "getNvPr")
                if (nvPr == null) {
                    for (m in listOf("getNvSpPr", "getNvPicPr", "getNvGraphicFramePr", "getNvGrpSpPr", "getNvCxnSpPr")) {
                        val pr = invokeMethod(xml, m)
                        if (pr != null) { nvPr = invokeMethod(pr, "getNvPr") ?: pr; break }
                    }
                }
                val ph = if (nvPr != null) invokeMethod(nvPr, "getPh") else invokeMethod(xml, "getPh")
                if (ph != null) {
                    val rawType = invokeMethod(ph, "getType")?.toString()
                    val isSetType = (invokeMethod(ph, "isSetType") as? Boolean) ?: (rawType != null)
                    val rawIdx = extractLong(invokeMethod(ph, "getIdx"))
                    val isSetIdx = (invokeMethod(ph, "isSetIdx") as? Boolean) ?: (rawIdx != null)
                    if (isSetType && !rawType.isNullOrBlank()) {
                        phTypeName = rawType
                        phEnum = mapTypeNameToPlaceholder(rawType) ?: phEnum
                        hasExplicitType = true
                    }
                    if (isSetIdx && rawIdx != null && rawIdx >= 0) {
                        phIdx = rawIdx; hasExplicitIdx = true
                    }
                } else {
                    // Regex fallback on XML string
                    val xmlStr = xml.toString()
                    val phMatch = Regex("""<(?:[a-zA-Z0-9]+:)?ph\b([^>]*)/?>""").find(xmlStr)
                    if (phMatch != null) {
                        val attrs = phMatch.groupValues[1]
                        val typeMatch = Regex("""type=["']([^"']+)["']""").find(attrs)
                        val idxMatch = Regex("""idx=["'](\d+)["']""").find(attrs)
                        if (typeMatch != null) {
                            phTypeName = typeMatch.groupValues[1]
                            phEnum = mapTypeNameToPlaceholder(phTypeName) ?: phEnum
                            hasExplicitType = true
                        }
                        if (idxMatch != null) {
                            val i = idxMatch.groupValues[1].toLongOrNull()
                            if (i != null) { phIdx = i; hasExplicitIdx = true }
                        }
                    }
                }
            }
        } catch (_: Throwable) { }

        if (phEnum == null && hasExplicitIdx) {
            phEnum = Placeholder.BODY; phTypeName = "body"
        }

        if (phEnum != null || phTypeName != null || hasExplicitIdx || hasExplicitType) {
            return PlaceholderInfo(phTypeName, phEnum, phIdx, hasExplicitType, hasExplicitIdx)
        }
        return null
    }

    private fun mapTypeNameToPlaceholder(typeName: String?): Placeholder? {
        if (typeName == null) return null
        return when (typeName.trim().lowercase()) {
            "title", "verticaltitle", "vertical_title" -> Placeholder.TITLE
            "ctrtitle", "centeredtitle", "centered_title" -> Placeholder.CENTERED_TITLE
            "subtitle", "sub_title" -> Placeholder.SUBTITLE
            "body", "verticalbody", "vertical_body" -> Placeholder.BODY
            "dt", "datetime", "date_time" -> Placeholder.DATETIME
            "ftr", "footer" -> Placeholder.FOOTER
            "sldnum", "slidenumber", "slide_num", "slide_number" -> Placeholder.SLIDE_NUMBER
            "hdr", "header" -> Placeholder.HEADER
            "obj", "content" -> Placeholder.CONTENT
            "chart" -> Placeholder.CHART
            "tbl", "table" -> Placeholder.TABLE
            "clipart", "clip_art" -> Placeholder.CLIP_ART
            "dgm", "diagram" -> Placeholder.DGM
            "media" -> Placeholder.MEDIA
            "sldimg", "slideimage", "slide_image" -> Placeholder.SLIDE_IMAGE
            "pic", "picture" -> Placeholder.PICTURE
            else -> try { Placeholder.valueOf(typeName.uppercase()) } catch (_: Throwable) { null }
        }
    }

    private fun areCompatibleTypes(a: Placeholder?, b: Placeholder?): Boolean {
        if (a == null || b == null) return false
        if (a == b) return true
        if (a in setOf(Placeholder.TITLE, Placeholder.CENTERED_TITLE) && b in setOf(Placeholder.TITLE, Placeholder.CENTERED_TITLE)) return true
        if (a in setOf(Placeholder.BODY, Placeholder.SUBTITLE, Placeholder.CONTENT) && b in setOf(Placeholder.BODY, Placeholder.SUBTITLE, Placeholder.CONTENT)) return true
        return false
    }

    private fun matchScore(target: PlaceholderInfo, cand: PlaceholderInfo, isMaster: Boolean): Int {
        val tPh = target.placeholder ?: mapTypeNameToPlaceholder(target.typeName)
        val cPh = cand.placeholder ?: mapTypeNameToPlaceholder(cand.typeName)
        val tIdx = target.idx
        val cIdx = cand.idx

        if (!isMaster) {
            if (target.hasExplicitIdx && cand.hasExplicitIdx && tIdx != null && tIdx == cIdx) {
                return when {
                    tPh != null && tPh == cPh -> 100
                    tPh == null || cPh == null -> 95
                    areCompatibleTypes(tPh, cPh) -> 90
                    else -> 80
                }
            }
            if (tPh != null && tPh == cPh) {
                return when {
                    tIdx == cIdx -> 90
                    !target.hasExplicitIdx && !cand.hasExplicitIdx -> 85
                    !target.hasExplicitIdx && cand.hasExplicitIdx -> 75
                    target.hasExplicitIdx && !cand.hasExplicitIdx -> 70
                    else -> 60
                }
            }
            if (tPh != null && cPh != null && areCompatibleTypes(tPh, cPh)) {
                return when {
                    tIdx != null && tIdx == cIdx -> 80
                    !target.hasExplicitIdx && !cand.hasExplicitIdx -> 75
                    else -> 60
                }
            }
            if (target.hasExplicitIdx && (cPh == Placeholder.BODY || cPh == Placeholder.CONTENT || cPh == null)) {
                if (tIdx == cIdx) return 70
            }
            return 0
        } else {
            if (tPh == Placeholder.TITLE || tPh == Placeholder.CENTERED_TITLE) {
                if (cPh == Placeholder.TITLE || cPh == Placeholder.CENTERED_TITLE) return 95
            }
            if (tPh == Placeholder.BODY || tPh == Placeholder.SUBTITLE || tPh == Placeholder.CONTENT) {
                if (cPh == Placeholder.BODY || cPh == Placeholder.CONTENT) {
                    if (target.hasExplicitIdx && cand.hasExplicitIdx && tIdx == cIdx) return 95
                    if (cIdx == null || cIdx == 0L || cIdx == 1L) return 90
                    return 85
                }
            }
            if (target.hasExplicitIdx && cand.hasExplicitIdx && tIdx != null && tIdx == cIdx) return 85
            if (tPh != null && tPh == cPh) return 90
            if (tPh == null && target.hasExplicitIdx && (cPh == Placeholder.BODY || cPh == Placeholder.CONTENT)) return 80
            return 0
        }
    }

    private fun resolvePlaceholderBounds(
        targetInfo: PlaceholderInfo,
        slide: XSLFSlide,
        w: Long,
        h: Long,
    ): LongArray? {
        val layout: XSLFSlideLayout? = try { slide.slideLayout } catch (_: Throwable) { null }
        val master: XSLFSlideMaster? = try { layout?.slideMaster } catch (_: Throwable) { null }

        val layoutShapes: List<XSLFShape> = try { layout?.shapes ?: emptyList() } catch (_: Throwable) { emptyList() }
        val masterShapes: List<XSLFShape> = try { master?.shapes ?: emptyList() } catch (_: Throwable) { emptyList() }

        // Search layout for best match that HAS bounds
        var bestLShape: XSLFShape? = null
        var bestLScore = 0
        var bestLInfo: PlaceholderInfo? = null
        for (ls in layoutShapes) {
            val li = extractPlaceholderInfo(ls) ?: continue
            val score = matchScore(targetInfo, li, isMaster = false)
            if (score > bestLScore) { bestLScore = score; bestLShape = ls; bestLInfo = li }
        }

        if (bestLShape != null && bestLScore > 0) {
            val b = readOwnXfrm(bestLShape)
            if (b != null) return b
            // Layout match has no xfrm — fall through to master
        }

        // Search master
        if (masterShapes.isNotEmpty()) {
            val masterTarget = bestLInfo ?: targetInfo
            var bestMShape: XSLFShape? = null
            var bestMScore = 0
            for (ms in masterShapes) {
                val mi = extractPlaceholderInfo(ms) ?: continue
                val score = matchScore(masterTarget, mi, isMaster = true)
                if (score > bestMScore) { bestMScore = score; bestMShape = ms }
            }
            if (bestMShape != null && bestMScore > 0) {
                return readOwnXfrm(bestMShape)
            }
        }
        return null
    }

    // -------------------------------------------------------------------------
    // Group transform
    // -------------------------------------------------------------------------

    data class GroupTransform(
        val offX: Long, val offY: Long, val extCx: Long, val extCy: Long,
        val chOffX: Long, val chOffY: Long, val chExtCx: Long, val chExtCy: Long,
    ) {
        fun transform(childX: Long, childY: Long, childCx: Long, childCy: Long): LongArray {
            val safeChExtCx = if (chExtCx != 0L) chExtCx else 1L
            val safeChExtCy = if (chExtCy != 0L) chExtCy else 1L
            val tx = offX + ((childX - chOffX).toDouble() * extCx.toDouble() / safeChExtCx.toDouble()).toLong()
            val ty = offY + ((childY - chOffY).toDouble() * extCy.toDouble() / safeChExtCy.toDouble()).toLong()
            val tcx = ((childCx.toDouble() * extCx.toDouble()) / safeChExtCx.toDouble()).toLong()
            val tcy = ((childCy.toDouble() * extCy.toDouble()) / safeChExtCy.toDouble()).toLong()
            return longArrayOf(tx, ty, tcx, tcy)
        }
    }

    private fun extractGroupTransform(groupShape: XSLFShape): GroupTransform? {
        try {
            val xml = getXmlObject(groupShape) ?: return null
            var xfrm: Any? = invokeMethod(xml, "getXfrm")
            if (xfrm == null) {
                val grpSpPr = invokeMethod(xml, "getGrpSpPr")
                if (grpSpPr != null) xfrm = invokeMethod(grpSpPr, "getXfrm")
            }
            if (xfrm == null) return null

            val off = invokeMethod(xfrm, "getOff")
            val ext = invokeMethod(xfrm, "getExt")
            val chOff = invokeMethod(xfrm, "getChOff")
            val chExt = invokeMethod(xfrm, "getChExt")

            val offX = extractLong(invokeMethod(off, "getX")) ?: 0L
            val offY = extractLong(invokeMethod(off, "getY")) ?: 0L
            val extCx = extractLong(invokeMethod(ext, "getCx")) ?: 0L
            val extCy = extractLong(invokeMethod(ext, "getCy")) ?: 0L
            val chOffX = extractLong(invokeMethod(chOff, "getX")) ?: 0L
            val chOffY = extractLong(invokeMethod(chOff, "getY")) ?: 0L
            val chExtCx = extractLong(invokeMethod(chExt, "getCx")) ?: (if (extCx > 0) extCx else 1L)
            val chExtCy = extractLong(invokeMethod(chExt, "getCy")) ?: (if (extCy > 0) extCy else 1L)

            if (extCx > 0 && extCy > 0) {
                return GroupTransform(offX, offY, extCx, extCy, chOffX, chOffY, chExtCx, chExtCy)
            }
        } catch (_: Throwable) { }
        return null
    }

    // -------------------------------------------------------------------------
    // Content extraction
    // -------------------------------------------------------------------------

    private fun extractContent(shape: XSLFShape, slide: XSLFSlide): ShapeContent {
        // Image first (picture shape or blip fill)
        val img = extractImage(shape, slide)
        if (img != null) return img

        // Text
        if (shape is org.apache.poi.sl.usermodel.TextShape<*, *>) {
            val tc = extractText(shape)
            if (tc != null) return tc
        }

        // Table
        if (shape is org.apache.poi.sl.usermodel.TableShape<*, *>) {
            val tbl = extractTable(shape)
            if (tbl != null) return tbl
        }

        // Vector simple shape
        if (shape is org.apache.poi.xslf.usermodel.XSLFSimpleShape) {
            return extractVector(shape)
        }

        return DecorativeContent
    }

    private fun extractImage(shape: XSLFShape, slide: XSLFSlide): ImageContent? {
        // 1. Direct picture shape
        if (shape is org.apache.poi.sl.usermodel.PictureShape<*, *>) {
            try {
                val pd = shape.pictureData
                if (pd != null && pd.data.isNotEmpty()) return ImageContent(pd.data, pd.contentType)
            } catch (_: Throwable) { }
        }

        // 2. getPictureData reflection
        val pd = invokeMethod(shape, "getPictureData")
        if (pd != null) {
            val data = invokeMethod(pd, "getData") as? ByteArray
            if (data != null && data.isNotEmpty()) {
                val ct = invokeMethod(pd, "getContentType") as? String
                return ImageContent(data, ct)
            }
        }

        // 3. getBlipId
        val blipId = invokeMethod(shape, "getBlipId") as? String
        if (!blipId.isNullOrBlank()) {
            val resolved = resolvePictureBytes(slide, blipId)
            if (resolved != null) return resolved
        }

        // 4. XML blip extraction
        val xml = getXmlObject(shape)
        if (xml != null) {
            val xmlBlipId = extractBlipEmbedId(xml)
            if (!xmlBlipId.isNullOrBlank()) {
                val resolved = resolvePictureBytes(slide, xmlBlipId)
                if (resolved != null) return resolved
            }
        }
        return null
    }

    private fun extractBlipEmbedId(xml: Any): String? {
        val blip = invokeMethod(xml, "getBlip")
            ?: invokeMethod(invokeMethod(xml, "getBlipFill"), "getBlip")
            ?: invokeMethod(invokeMethod(invokeMethod(xml, "getSpPr"), "getBlipFill"), "getBlip")
            ?: invokeMethod(invokeMethod(invokeMethod(xml, "getBgPr"), "getBlipFill"), "getBlip")
            ?: invokeMethod(invokeMethod(xml, "getBgPr"), "getBlip")
        if (blip != null) {
            val embed = invokeMethod(blip, "getEmbed") as? String
            if (!embed.isNullOrBlank()) return embed
            val link = invokeMethod(blip, "getLink") as? String
            if (!link.isNullOrBlank()) return link
        }
        try {
            val xmlStr = xml.toString()
            val match = Regex("""(?:embed|link)=["']([^"']+)["']""").find(xmlStr)
            if (match != null) {
                val id = match.groupValues[1]
                if (id.isNotBlank()) return id
            }
        } catch (_: Throwable) { }
        return null
    }

    private fun resolvePictureBytes(slide: XSLFSlide, blipId: String): ImageContent? {
        val candidates = mutableListOf<Any>()
        candidates.add(slide)
        try { slide.slideLayout?.let { candidates.add(it) } } catch (_: Throwable) { }
        try { slide.slideMaster?.let { candidates.add(it) } } catch (_: Throwable) { }

        for (candidate in candidates) {
            try {
                val relPart = if (candidate is XSLFSlide) candidate.getRelationPartById(blipId)
                else invokeMethod(candidate, "getRelationPartById", blipId)
                if (relPart != null) {
                    val docPart = invokeMethod(relPart, "getDocumentPart")
                        ?: try { val f = relPart.javaClass.getDeclaredField("documentPart"); f.isAccessible = true; f.get(relPart) } catch (_: Throwable) { null }
                    if (docPart is XSLFPictureData) return ImageContent(docPart.data, docPart.contentType)
                }
            } catch (_: Throwable) { }

            try {
                val relDoc = if (candidate is XSLFSlide) candidate.getRelationById(blipId)
                else invokeMethod(candidate, "getRelationById", blipId)
                if (relDoc is XSLFPictureData) return ImageContent(relDoc.data, relDoc.contentType)
            } catch (_: Throwable) { }

            try {
                val packagePart = if (candidate is XSLFSlide) candidate.packagePart
                else invokeMethod(candidate, "getPackagePart") as? org.apache.poi.openxml4j.opc.PackagePart
                if (packagePart != null) {
                    val rel = packagePart.getRelationship(blipId)
                    if (rel != null) {
                        val part = packagePart.getRelatedPart(rel) ?: packagePart.getPackage().getPart(rel)
                        val bytes = part?.inputStream?.use { it.readBytes() }
                        if (bytes != null && bytes.isNotEmpty()) return ImageContent(bytes, part.contentType)
                    }
                }
            } catch (_: Throwable) { }
        }

        try {
            val slideShow = slide.slideShow
            for (pd in slideShow.pictureData) {
                val pdPartName = pd.packagePart?.partName?.toString() ?: ""
                if (pdPartName.contains(blipId, ignoreCase = true)) {
                    return ImageContent(pd.data, pd.contentType)
                }
            }
        } catch (_: Throwable) { }
        return null
    }

    // -------------------------------------------------------------------------
    // Text extraction
    // -------------------------------------------------------------------------

    private fun extractText(shape: org.apache.poi.sl.usermodel.TextShape<*, *>): TextContent? {
        val paragraphs = try { shape.textParagraphs } catch (_: Throwable) { return null }
        if (paragraphs.isEmpty()) return null
        val out = mutableListOf<ParsedParagraph>()
        for (p in paragraphs) {
            val runs = try { p.textRuns } catch (_: Throwable) { emptyList() }
            val parsedRuns = mutableListOf<ParsedTextRun>()
            for (r in runs) {
                val text = getTextFromRun(r)
                if (text.isNotBlank()) {
                    val bold = try { r.isBold } catch (_: Throwable) { false }
                    val italic = try { r.isItalic } catch (_: Throwable) { false }
                    val underline = try { r.isUnderlined } catch (_: Throwable) { false }
                    val colorHex = extractRunColor(r)
                    val size = try { r.fontSize } catch (_: Throwable) { 0.0 }
                    val fontFamily = try { r.fontFamily } catch (_: Throwable) { null }
                    parsedRuns.add(ParsedTextRun(text, bold, italic, underline, colorHex, size.toFloat(), fontFamily))
                }
            }
            if (parsedRuns.isNotEmpty()) {
                val align = when (try { p.textAlign?.name } catch (_: Throwable) { "LEFT" }) {
                    "CENTER" -> TextAlignment.CENTER
                    "RIGHT" -> TextAlignment.RIGHT
                    "JUSTIFY" -> TextAlignment.JUSTIFY
                    else -> TextAlignment.LEFT
                }
                val bulletLevel = try { p.indentLevel } catch (_: Throwable) { 0 }
                val hasBullet = try {
                    if (p is XSLFTextParagraph) p.bulletCharacter != null || p.indentLevel > 0
                    else p.indentLevel > 0
                } catch (_: Throwable) { false }
                val bulletChar = try { if (p is XSLFTextParagraph) p.bulletCharacter ?: "" else "" } catch (_: Throwable) { "" }
                out.add(ParsedParagraph(parsedRuns, align, bulletLevel, bulletChar))
            }
        }
        return if (out.isNotEmpty()) TextContent(out) else null
    }

    private fun getTextFromRun(run: org.apache.poi.sl.usermodel.TextRun): String {
        var text = try { run.rawText ?: "" } catch (_: Throwable) { "" }
        if (text.startsWith("org.apache.poi") || text.startsWith("org.apache.xmlbeans") ||
            (text.startsWith("<") && text.endsWith(">"))) return ""
        return cleanRunText(text)
    }

    private fun cleanRunText(raw: String): String {
        if (raw.isBlank()) return raw
        var s = raw
        for (c in listOf('\uF0A7', '\uF0B7', '\uF06C', '\uF0D8', '\uF076', '\u2022', '\u25CF', '\u25CB', '\u25AA', '\u25A0', '\u25BA', '\u25B8', '\u25B6')) {
            s = s.replace(c.toString(), "")
        }
        s = s.replace(Regex("""^[\'`'\u2018\u2019\-–—•·*▪▫◦●■□►▸→✓✔]\s*"""), "")
        s = s.replace(Regex("""^[-–—]\s+"""), "")
        return s
    }

    private fun extractRunColor(run: org.apache.poi.sl.usermodel.TextRun): String? {
        if (run is XSLFTextRun) {
            try {
                val xmlRun = getXmlObject(run) ?: return null
                val rPr = invokeMethod(xmlRun, "getRPr") ?: return null
                val solidFill = invokeMethod(rPr, "getSolidFill") ?: return null
                val srgb = invokeMethod(solidFill, "getSrgbClr") ?: return null
                val hexBytes = invokeMethod(srgb, "getVal") as? ByteArray
                val hex = hexBytes?.joinToString("") { String.format("%02X", it) }
                if (!hex.isNullOrBlank()) return "#$hex"
            } catch (_: Throwable) { }
        }
        return null
    }

    // -------------------------------------------------------------------------
    // Table extraction
    // -------------------------------------------------------------------------

    private fun extractTable(shape: org.apache.poi.sl.usermodel.TableShape<*, *>): TableContent? {
        val rows = try { shape.numberOfRows } catch (_: Throwable) { 0 }
        val cols = try { shape.numberOfColumns } catch (_: Throwable) { 0 }
        if (rows <= 0 || cols <= 0) return null
        val tableRows = mutableListOf<List<ParsedTableCell>>()
        for (r in 0 until rows) {
            val cells = mutableListOf<ParsedTableCell>()
            for (c in 0 until cols) {
                val cell = try { shape.getCell(r, c) } catch (_: Throwable) { null }
                val text = try { cell?.text?.trim() ?: "" } catch (_: Throwable) { "" }
                cells.add(ParsedTableCell(text))
            }
            tableRows.add(cells)
        }
        return TableContent(tableRows)
    }

    // -------------------------------------------------------------------------
    // Vector shape extraction
    // -------------------------------------------------------------------------

    private fun extractVector(shape: org.apache.poi.xslf.usermodel.XSLFSimpleShape): ShapeContent {
        val kind = try {
            when (shape.shapeType?.name?.lowercase()) {
                "roundrect", "round_rect" -> VectorKind.ROUNDED_RECTANGLE
                "ellipse", "oval", "circle" -> VectorKind.ELLIPSE
                "triangle" -> VectorKind.TRIANGLE
                "diamond" -> VectorKind.DIAMOND
                "line" -> VectorKind.LINE
                "rect" -> VectorKind.RECTANGLE
                else -> VectorKind.OTHER
            }
        } catch (_: Throwable) { VectorKind.RECTANGLE }
        val fill = extractFillColor(shape)
        val stroke = extractStrokeColor(shape)
        val strokeW = try { shape.lineWidth.toFloat() } catch (_: Throwable) { 0f }
        return VectorContent(kind, fill, stroke, strokeW)
    }

    private fun extractFillColor(shape: org.apache.poi.xslf.usermodel.XSLFSimpleShape): String? {
        try {
            val xml = getXmlObject(shape) ?: return null
            val spPr = invokeMethod(xml, "getSpPr") ?: return null
            val solidFill = invokeMethod(spPr, "getSolidFill") ?: return null
            val srgb = invokeMethod(solidFill, "getSrgbClr") ?: return null
            val hexBytes = invokeMethod(srgb, "getVal") as? ByteArray
            val hex = hexBytes?.joinToString("") { String.format("%02X", it) }
            if (!hex.isNullOrBlank()) return "#$hex"
        } catch (_: Throwable) { }
        return null
    }

    private fun extractStrokeColor(shape: org.apache.poi.xslf.usermodel.XSLFSimpleShape): String? {
        try {
            val xml = getXmlObject(shape) ?: return null
            val spPr = invokeMethod(xml, "getSpPr") ?: return null
            val ln = invokeMethod(spPr, "getLn") ?: return null
            val solidFill = invokeMethod(ln, "getSolidFill") ?: return null
            val srgb = invokeMethod(solidFill, "getSrgbClr") ?: return null
            val hexBytes = invokeMethod(srgb, "getVal") as? ByteArray
            val hex = hexBytes?.joinToString("") { String.format("%02X", it) }
            if (!hex.isNullOrBlank()) return "#$hex"
        } catch (_: Throwable) { }
        return null
    }

    // -------------------------------------------------------------------------
    // Background extraction
    // -------------------------------------------------------------------------

    private fun extractBackground(slide: XSLFSlide): SlideBackground {
        // 1. POI background picture
        val bgCandidates = listOfNotNull(
            try { slide.background } catch (_: Throwable) { null },
            try { slide.slideLayout?.background } catch (_: Throwable) { null },
            try { slide.slideMaster?.background } catch (_: Throwable) { null },
        )
        for (bg in bgCandidates) {
            try {
                val pd = invokeMethod(bg, "getPictureData") as? XSLFPictureData
                if (pd != null && pd.data.isNotEmpty()) return BgImage(pd.data, pd.contentType)
            } catch (_: Throwable) { }
        }

        // 2. XML <p:bg> picture fill
        val sheets = listOfNotNull(
            slide,
            try { slide.slideLayout } catch (_: Throwable) { null },
            try { slide.slideMaster } catch (_: Throwable) { null },
        )
        for (sheet in sheets) {
            try {
                val ct = getXmlObject(sheet) ?: continue
                val cSld = invokeMethod(ct, "getCSld") ?: ct
                val bg = invokeMethod(cSld, "getBg") ?: invokeMethod(ct, "getBg")
                if (bg != null) {
                    val blipId = extractBlipEmbedId(bg)
                    if (!blipId.isNullOrBlank()) {
                        val resolved = resolvePictureBytes(slide, blipId)
                        if (resolved != null) return BgImage(resolved.bytes, resolved.contentType)
                    }
                    // Solid fill bg
                    val bgPr = invokeMethod(bg, "getBgPr")
                    if (bgPr != null) {
                        val solidFill = invokeMethod(bgPr, "getSolidFill")
                        if (solidFill != null) {
                            val srgb = invokeMethod(solidFill, "getSrgbClr")
                            val hexBytes = srgb?.let { invokeMethod(it, "getVal") as? ByteArray }
                            val hex = hexBytes?.joinToString("") { String.format("%02X", it) }
                            if (!hex.isNullOrBlank()) return BgSolid("#$hex")
                        }
                    }
                }
            } catch (_: Throwable) { }
        }

        // 3. Solid bg color via direct XML
        val solidColor = extractSolidBgColor(slide)
        if (solidColor != null) return BgSolid(solidColor)

        return BgNone
    }

    private fun extractSolidBgColor(slide: XSLFSlide): String? {
        val sheets = listOfNotNull(slide, try { slide.slideLayout } catch (_: Throwable) { null }, try { slide.slideMaster } catch (_: Throwable) { null })
        for (sheet in sheets) {
            try {
                val ct = getXmlObject(sheet) ?: continue
                val cSld = invokeMethod(ct, "getCSld") ?: continue
                val bg = invokeMethod(cSld, "getBg") ?: continue
                val bgPr = invokeMethod(bg, "getBgPr") ?: continue
                val solidFill = invokeMethod(bgPr, "getSolidFill") ?: continue
                val srgb = invokeMethod(solidFill, "getSrgbClr") ?: continue
                val hexBytes = invokeMethod(srgb, "getVal") as? ByteArray
                val hex = hexBytes?.joinToString("") { String.format("%02X", it) }
                if (!hex.isNullOrBlank()) return "#$hex"
            } catch (_: Throwable) { }
        }
        return null
    }

    // -------------------------------------------------------------------------
    // Reflection helpers
    // -------------------------------------------------------------------------

    private fun getXmlObject(obj: Any): Any? {
        return try {
            val m = obj.javaClass.getMethod("getXmlObject")
            m.invoke(obj)
        } catch (_: Throwable) {
            invokeMethod(obj, "getXmlObject") ?: invokeMethod(obj, "fetchXmlObject")
        }
    }

    private fun invokeMethod(target: Any?, methodName: String, vararg args: Any): Any? {
        if (target == null) return null
        var clazz: Class<*>? = target.javaClass
        while (clazz != null && clazz != Any::class.java) {
            for (m in clazz.declaredMethods) {
                if (m.name == methodName && m.parameterTypes.size == args.size) {
                    try { m.isAccessible = true; return m.invoke(target, *args) } catch (_: Throwable) { }
                }
            }
            for (iface in clazz.interfaces) {
                for (m in iface.declaredMethods) {
                    if (m.name == methodName && m.parameterTypes.size == args.size) {
                        try { m.isAccessible = true; return m.invoke(target, *args) } catch (_: Throwable) { }
                    }
                }
            }
            clazz = clazz.superclass
        }
        return null
    }

    private fun extractLong(obj: Any?): Long? {
        if (obj == null) return null
        if (obj is Number) return obj.toLong()
        try {
            val v = invokeMethod(obj, "getLongValue")
            if (v is Number) return v.toLong()
        } catch (_: Throwable) { }
        try {
            val str = obj.toString().trim()
            str.toLongOrNull()?.let { return it }
            str.toDoubleOrNull()?.let { return it.toLong() }
        } catch (_: Throwable) { }
        return null
    }
}
