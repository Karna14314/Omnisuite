package com.karnadigital.omnisuite.core.engine.document

import android.util.Log
import org.apache.poi.sl.usermodel.Placeholder
import org.apache.poi.sl.usermodel.Shape
import org.apache.poi.sl.usermodel.Slide
import org.apache.poi.sl.usermodel.SlideShow
import org.apache.poi.sl.usermodel.TextRun
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFPictureData
import org.apache.poi.xslf.usermodel.XSLFShape
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.poi.xslf.usermodel.XSLFSlideLayout
import org.apache.poi.xslf.usermodel.XSLFSlideMaster
import org.apache.poi.xslf.usermodel.XSLFTextParagraph
import org.apache.poi.xslf.usermodel.XSLFTextRun

/**
 * Decoupled Kotlin data model for PPTX presentations independent of Apache POI and Java AWT.
 */
data class NormalizedBounds(
    val left: Float,   // 0.0f..1.0f
    val top: Float,    // 0.0f..1.0f
    val width: Float,  // 0.001f..1.0f
    val height: Float  // 0.001f..1.0f
)

sealed class ParsedBackground {
    object DefaultWhite : ParsedBackground()
    data class SolidColor(val colorHex: String) : ParsedBackground()
    data class ImageFill(val imageBytes: ByteArray, val contentType: String? = null) : ParsedBackground() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ImageFill
            if (!imageBytes.contentEquals(other.imageBytes)) return false
            if (contentType != other.contentType) return false
            return true
        }
        override fun hashCode(): Int {
            var result = imageBytes.contentHashCode()
            result = 31 * result + (contentType?.hashCode() ?: 0)
            return result
        }
    }
}

enum class TextAlignment { LEFT, CENTER, RIGHT, JUSTIFY }

data class ParsedTextRun(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val textColorHex: String? = null,
    val fontSizePt: Float = 14f,
    val fontFamily: String? = null
)

data class ParsedParagraph(
    val runs: List<ParsedTextRun> = emptyList(),
    val bulletLevel: Int = 0,
    val hasBullet: Boolean = false,
    val bulletChar: String = "",
    val alignment: TextAlignment = TextAlignment.LEFT
) {
    val fullText: String get() = runs.joinToString("") { it.text }
}

sealed class ParsedShape {
    abstract val id: String
    abstract val bounds: NormalizedBounds
    abstract val zIndex: Int

    data class TextShape(
        override val id: String,
        override val bounds: NormalizedBounds,
        override val zIndex: Int,
        val isTitle: Boolean = false,
        val paragraphs: List<ParsedParagraph> = emptyList(),
        val backgroundColorHex: String? = null
    ) : ParsedShape() {
        val fullText: String get() = paragraphs.joinToString("\n") { it.fullText }
        val primaryText: String get() = paragraphs.firstOrNull()?.fullText ?: ""
        val isBold: Boolean get() = paragraphs.firstOrNull()?.runs?.firstOrNull()?.isBold ?: isTitle
        val isItalic: Boolean get() = paragraphs.firstOrNull()?.runs?.firstOrNull()?.isItalic ?: false
        val isUnderline: Boolean get() = paragraphs.firstOrNull()?.runs?.firstOrNull()?.isUnderline ?: false
        val textColorHex: String? get() = paragraphs.firstOrNull()?.runs?.firstOrNull()?.textColorHex
        val fontSizePt: Float get() = paragraphs.firstOrNull()?.runs?.firstOrNull()?.fontSizePt ?: (if (isTitle) 24f else 14f)
    }

    data class ImageShape(
        override val id: String,
        override val bounds: NormalizedBounds,
        override val zIndex: Int,
        val imageBytes: ByteArray,
        val contentType: String? = null
    ) : ParsedShape() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ImageShape
            if (id != other.id) return false
            if (bounds != other.bounds) return false
            if (zIndex != other.zIndex) return false
            if (!imageBytes.contentEquals(other.imageBytes)) return false
            if (contentType != other.contentType) return false
            return true
        }

        override fun hashCode(): Int {
            var result = id.hashCode()
            result = 31 * result + bounds.hashCode()
            result = 31 * result + zIndex
            result = 31 * result + imageBytes.contentHashCode()
            result = 31 * result + (contentType?.hashCode() ?: 0)
            return result
        }
    }

    data class VectorShape(
        override val id: String,
        override val bounds: NormalizedBounds,
        override val zIndex: Int,
        val shapeType: String = "rect",
        val fillColorHex: String? = null,
        val strokeColorHex: String? = null,
        val strokeWidthDp: Float = 1f
    ) : ParsedShape()

    data class TableShape(
        override val id: String,
        override val bounds: NormalizedBounds,
        override val zIndex: Int,
        val rows: Int,
        val cols: Int,
        val cells: List<List<ParsedTableCell>>
    ) : ParsedShape()

    data class GroupShape(
        override val id: String,
        override val bounds: NormalizedBounds,
        override val zIndex: Int,
        val children: List<ParsedShape>
    ) : ParsedShape()
}

data class ParsedTableCell(
    val textShape: ParsedShape.TextShape?,
    val backgroundColorHex: String? = null
)

data class ParsedSlide(
    val slideNumber: Int,
    val background: ParsedBackground = ParsedBackground.DefaultWhite,
    val shapes: List<ParsedShape> = emptyList(),
    val speakerNotes: String? = null,
    val aspectRatio: Float = 16f / 9f
) {
    val title: ParsedShape.TextShape
        get() = shapes.filterIsInstance<ParsedShape.TextShape>().firstOrNull { it.isTitle }
            ?: shapes.filterIsInstance<ParsedShape.TextShape>().firstOrNull()
            ?: ParsedShape.TextShape(
                id = "empty_title",
                bounds = NormalizedBounds(0f, 0f, 0f, 0f),
                zIndex = 0,
                isTitle = false
            )

    val textShapes: List<ParsedShape.TextShape>
        get() = shapes.filterIsInstance<ParsedShape.TextShape>()
}

data class ParsedPresentation(
    val slides: List<ParsedSlide>,
    val slideWidthEmu: Long = 9144000L,
    val slideHeightEmu: Long = 5143500L
) {
    val aspectRatio: Float
        get() = if (slideHeightEmu > 0) slideWidthEmu.toFloat() / slideHeightEmu.toFloat() else (16f / 9f)
}

/**
 * Shared engine for OOXML PPTX shape coordinate transforms, image extraction,
 * and text styling without compile-time java.awt dependencies.
 */
object PptxShapeExtractor {

    data class GroupTransform(
        val offX: Long,
        val offY: Long,
        val extCx: Long,
        val extCy: Long,
        val chOffX: Long,
        val chOffY: Long,
        val chExtCx: Long,
        val chExtCy: Long
    ) {
        fun transformRect(childX: Long, childY: Long, childCx: Long, childCy: Long): LongArray {
            val safeChExtCx = if (chExtCx != 0L) chExtCx else 1L
            val safeChExtCy = if (chExtCy != 0L) chExtCy else 1L

            val transX = offX + ((childX - chOffX).toDouble() * extCx.toDouble() / safeChExtCx.toDouble()).toLong()
            val transY = offY + ((childY - chOffY).toDouble() * extCy.toDouble() / safeChExtCy.toDouble()).toLong()
            val transCx = ((childCx.toDouble() * extCx.toDouble()) / safeChExtCx.toDouble()).toLong()
            val transCy = ((childCy.toDouble() * extCy.toDouble()) / safeChExtCy.toDouble()).toLong()

            return longArrayOf(transX, transY, transCx, transCy)
        }
    }

    fun invokeMethod(target: Any?, methodName: String, vararg args: Any): Any? {
        if (target == null) return null
        var clazz: Class<*>? = target.javaClass
        while (clazz != null && clazz != Any::class.java) {
            for (m in clazz.declaredMethods) {
                if (m.name == methodName && m.parameterTypes.size == args.size) {
                    try {
                        m.isAccessible = true
                        return m.invoke(target, *args)
                    } catch (_: Throwable) {}
                }
            }
            for (iface in clazz.interfaces) {
                for (m in iface.declaredMethods) {
                    if (m.name == methodName && m.parameterTypes.size == args.size) {
                        try {
                            m.isAccessible = true
                            return m.invoke(target, *args)
                        } catch (_: Throwable) {}
                    }
                }
            }
            clazz = clazz.superclass
        }
        return null
    }

    fun getXmlObjectReflection(obj: Any): Any? {
        val direct = invokeMethod(obj, "getXmlObject")
        if (direct != null) return direct
        return invokeMethod(obj, "fetchXmlObject")
    }

    fun extractLongValue(obj: Any?): Long? {
        if (obj == null) return null
        if (obj is Number) return obj.toLong()
        try {
            val v = invokeMethod(obj, "getLongValue")
            if (v is Number) return v.toLong()
        } catch (_: Throwable) { }
        try {
            val str = obj.toString().trim()
            val num = str.toLongOrNull()
            if (num != null) return num
            val doubleNum = str.toDoubleOrNull()
            if (doubleNum != null) return doubleNum.toLong()
        } catch (_: Throwable) { }
        return null
    }

    fun extractGroupTransform(groupShape: Any): GroupTransform? {
        try {
            val xml = getXmlObjectReflection(groupShape) ?: return null
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

            val offX = extractLongValue(invokeMethod(off, "getX")) ?: 0L
            val offY = extractLongValue(invokeMethod(off, "getY")) ?: 0L
            val extCx = extractLongValue(invokeMethod(ext, "getCx")) ?: 0L
            val extCy = extractLongValue(invokeMethod(ext, "getCy")) ?: 0L

            val chOffX = extractLongValue(invokeMethod(chOff, "getX")) ?: 0L
            val chOffY = extractLongValue(invokeMethod(chOff, "getY")) ?: 0L
            val chExtCx = extractLongValue(invokeMethod(chExt, "getCx")) ?: (if (extCx > 0) extCx else 1L)
            val chExtCy = extractLongValue(invokeMethod(chExt, "getCy")) ?: (if (extCy > 0) extCy else 1L)

            if (extCx > 0 && extCy > 0) {
                return GroupTransform(offX, offY, extCx, extCy, chOffX, chOffY, chExtCx, chExtCy)
            }
        } catch (_: Throwable) { }
        return null
    }

    fun logDebug(tag: String, message: String) {
        try {
            Log.d(tag, message)
        } catch (_: Throwable) { }
    }

    fun logWarn(tag: String, message: String, throwable: Throwable? = null) {
        try {
            if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
        } catch (_: Throwable) { }
    }

    fun getSlideDimensionsEmu(ppt: SlideShow<*, *>): Pair<Long, Long> {
        if (ppt is XMLSlideShow) {
            try {
                val ctPresentation = getXmlObjectReflection(ppt)
                    ?: try { ppt.javaClass.getMethod("getCTPresentation").invoke(ppt) } catch (_: Throwable) { null }
                if (ctPresentation != null) {
                    val sldSz = invokeMethod(ctPresentation, "getSldSz")
                    if (sldSz != null) {
                        val cx = extractLongValue(invokeMethod(sldSz, "getCx")) ?: 9144000L
                        val cy = extractLongValue(invokeMethod(sldSz, "getCy")) ?: 5143500L
                        logDebug("PptxShapeExtractor", "Resolved slide dimensions from XML: ${cx}x${cy} EMU")
                        return Pair(cx, cy)
                    }
                }
            } catch (t: Throwable) {
                logWarn("PptxShapeExtractor", "Error getting slide dimensions from XML", t)
            }
        }
        return Pair(9144000L, 5143500L)
    }

    fun getXmlShapeRawBoundsEmu(shape: Any): LongArray? {
        try {
            val xml = getXmlObjectReflection(shape) ?: return null

            var xfrm: Any? = invokeMethod(xml, "getXfrm")
            if (xfrm == null) {
                val spPr = invokeMethod(xml, "getSpPr")
                if (spPr != null) xfrm = invokeMethod(spPr, "getXfrm")
            }
            if (xfrm == null) {
                val grpSpPr = invokeMethod(xml, "getGrpSpPr")
                if (grpSpPr != null) xfrm = invokeMethod(grpSpPr, "getXfrm")
            }
            if (xfrm == null) {
                for (methodName in listOf("getCxnSpPr", "getNvSpPr", "getNvPicPr", "getNvCxnSpPr", "getPicPr")) {
                    val pr = invokeMethod(xml, methodName)
                    if (pr != null) {
                        xfrm = invokeMethod(pr, "getXfrm")
                        if (xfrm != null) break
                    }
                }
            }

            if (xfrm == null) return null

            val off = invokeMethod(xfrm, "getOff")
            val ext = invokeMethod(xfrm, "getExt")

            val rawX = invokeMethod(off, "getX")
            val rawY = invokeMethod(off, "getY")
            val rawCx = invokeMethod(ext, "getCx")
            val rawCy = invokeMethod(ext, "getCy")

            val x = extractLongValue(rawX)
            val y = extractLongValue(rawY)
            val cx = extractLongValue(rawCx)
            val cy = extractLongValue(rawCy)

            if (x != null && y != null && cx != null && cy != null && cx > 0 && cy > 0) {
                return longArrayOf(x, y, cx, cy)
            }
        } catch (_: Throwable) { }
        return null
    }

    data class PlaceholderInfo(
        val typeName: String?,
        val placeholder: Placeholder?,
        val idx: Long?,
        val hasExplicitType: Boolean,
        val hasExplicitIdx: Boolean
    )

    fun mapTypeNameToPlaceholder(typeName: String?): Placeholder? {
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
            else -> {
                try {
                    Placeholder.valueOf(typeName.uppercase())
                } catch (_: Throwable) {
                    null
                }
            }
        }
    }

    fun getShapeName(shape: Any): String {
        return try {
            (invokeMethod(shape, "getShapeName") as? String)
                ?: (invokeMethod(shape, "getName") as? String)
                ?: shape.javaClass.simpleName
        } catch (_: Throwable) {
            shape.javaClass.simpleName
        }
    }

    fun extractPlaceholderInfo(shape: Any): PlaceholderInfo? {
        var phEnum: Placeholder? = null
        var phTypeName: String? = null
        var phIdx: Long? = null
        var hasExplicitType = false
        var hasExplicitIdx = false

        // 1. Query Apache POI PlaceholderDetails
        try {
            val phDetails = if (shape is XSLFShape) {
                try { shape.placeholderDetails } catch (_: Throwable) { null }
            } else {
                invokeMethod(shape, "getPlaceholderDetails")
            }
            if (phDetails != null) {
                val p = invokeMethod(phDetails, "getPlaceholder") as? Placeholder
                if (p != null) {
                    phEnum = p
                    phTypeName = p.name.lowercase()
                    hasExplicitType = true
                }
                val rawIdx = invokeMethod(phDetails, "getIndex") as? Number
                if (rawIdx != null && rawIdx.toInt() >= 0) {
                    phIdx = rawIdx.toLong()
                    hasExplicitIdx = true
                }
            }
        } catch (_: Throwable) { }

        // 2. Query underlying XML nvPr -> ph
        try {
            val xml = getXmlObjectReflection(shape)
            if (xml != null) {
                var nvPr: Any? = invokeMethod(xml, "getNvPr")
                if (nvPr == null) {
                    for (m in listOf("getNvSpPr", "getNvPicPr", "getNvGraphicFramePr", "getNvGrpSpPr", "getNvCxnSpPr")) {
                        val pr = invokeMethod(xml, m)
                        if (pr != null) {
                            nvPr = invokeMethod(pr, "getNvPr") ?: pr
                            break
                        }
                    }
                }

                val ph = if (nvPr != null) invokeMethod(nvPr, "getPh") else invokeMethod(xml, "getPh")
                if (ph != null) {
                    val rawTypeObj = invokeMethod(ph, "getType")
                    val rawTypeStr = rawTypeObj?.toString()
                    val isSetType = (invokeMethod(ph, "isSetType") as? Boolean) ?: (rawTypeStr != null)

                    val rawIdxObj = invokeMethod(ph, "getIdx")
                    val parsedIdx = extractLongValue(rawIdxObj)
                    val isSetIdx = (invokeMethod(ph, "isSetIdx") as? Boolean) ?: (parsedIdx != null)

                    if (isSetType && !rawTypeStr.isNullOrBlank()) {
                        phTypeName = rawTypeStr
                        phEnum = mapTypeNameToPlaceholder(rawTypeStr) ?: phEnum
                        hasExplicitType = true
                    }
                    if (isSetIdx && parsedIdx != null && parsedIdx >= 0) {
                        phIdx = parsedIdx
                        hasExplicitIdx = true
                    }
                } else {
                    // String / Regex fallback on shape XML string
                    val xmlStr = xml.toString()
                    val phMatch = Regex("""<(?:[a-zA-Z0-9]+:)?ph\b([^>]*)/?>""").find(xmlStr)
                    if (phMatch != null) {
                        val attrs = phMatch.groupValues[1]
                        val typeMatch = Regex("""type=["']([^"']+)["']""").find(attrs)
                        val idxMatch = Regex("""idx=["'](\d+)["']""").find(attrs)

                        if (typeMatch != null) {
                            val t = typeMatch.groupValues[1]
                            phTypeName = t
                            phEnum = mapTypeNameToPlaceholder(t) ?: phEnum
                            hasExplicitType = true
                        }
                        if (idxMatch != null) {
                            val i = idxMatch.groupValues[1].toLongOrNull()
                            if (i != null) {
                                phIdx = i
                                hasExplicitIdx = true
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) { }

        // If idx is present without explicit type, default to BODY in OOXML
        if (phEnum == null && hasExplicitIdx) {
            phEnum = Placeholder.BODY
            phTypeName = "body"
        }

        if (phEnum != null || phTypeName != null || hasExplicitIdx || hasExplicitType) {
            return PlaceholderInfo(
                typeName = phTypeName,
                placeholder = phEnum,
                idx = phIdx,
                hasExplicitType = hasExplicitType,
                hasExplicitIdx = hasExplicitIdx
            )
        }
        return null
    }

    private fun areCompatibleTypes(a: Placeholder?, b: Placeholder?): Boolean {
        if (a == null || b == null) return false
        if (a == b) return true
        val titles = setOf(Placeholder.TITLE, Placeholder.CENTERED_TITLE)
        if (a in titles && b in titles) return true
        val bodies = setOf(Placeholder.BODY, Placeholder.SUBTITLE, Placeholder.CONTENT)
        if (a in bodies && b in bodies) return true
        return false
    }

    fun matchPlaceholderScore(target: PlaceholderInfo, candidate: PlaceholderInfo, isMaster: Boolean = false): Int {
        val targetPh = target.placeholder ?: mapTypeNameToPlaceholder(target.typeName)
        val candPh = candidate.placeholder ?: mapTypeNameToPlaceholder(candidate.typeName)

        val targetIdx = target.idx
        val candIdx = candidate.idx

        if (!isMaster) {
            // SLIDE -> SLIDELAYOUT MATCHING (OOXML standard)
            // 1. Exact idx match
            if (target.hasExplicitIdx && candidate.hasExplicitIdx && targetIdx != null && targetIdx == candIdx) {
                return when {
                    targetPh != null && targetPh == candPh -> 100 // Exact type & exact idx
                    targetPh == null || candPh == null -> 95      // Exact idx, defaulted type
                    areCompatibleTypes(targetPh, candPh) -> 90    // Exact idx, compatible types
                    else -> 80                                   // Exact idx takes precedence
                }
            }

            // 2. Exact type match
            if (targetPh != null && targetPh == candPh) {
                return when {
                    targetIdx == candIdx -> 90
                    !target.hasExplicitIdx && !candidate.hasExplicitIdx -> 85
                    !target.hasExplicitIdx && candidate.hasExplicitIdx -> 75
                    target.hasExplicitIdx && !candidate.hasExplicitIdx -> 70
                    else -> 60
                }
            }

            // 3. Compatible type match (e.g. TITLE vs CENTERED_TITLE, SUBTITLE vs BODY)
            if (targetPh != null && candPh != null && areCompatibleTypes(targetPh, candPh)) {
                return when {
                    targetIdx != null && targetIdx == candIdx -> 80
                    !target.hasExplicitIdx && !candidate.hasExplicitIdx -> 75
                    else -> 60
                }
            }

            // 4. Target has explicit idx, candidate is standard body/content with matching idx
            if (target.hasExplicitIdx && (candPh == Placeholder.BODY || candPh == Placeholder.CONTENT || candPh == null)) {
                if (targetIdx == candIdx) return 70
            }

            return 0
        } else {
            // SLIDELAYOUT / SLIDE -> SLIDEMASTER MATCHING (OOXML standard)
            // Master shapes provide default formatting/bounds for master title, master body, etc.

            // 1. Title matching
            if (targetPh == Placeholder.TITLE || targetPh == Placeholder.CENTERED_TITLE) {
                if (candPh == Placeholder.TITLE || candPh == Placeholder.CENTERED_TITLE) return 95
            }

            // 2. Body / Content / Subtitle matching
            if (targetPh == Placeholder.BODY || targetPh == Placeholder.SUBTITLE || targetPh == Placeholder.CONTENT) {
                if (candPh == Placeholder.BODY || candPh == Placeholder.CONTENT) {
                    if (target.hasExplicitIdx && candidate.hasExplicitIdx && targetIdx == candIdx) return 95
                    if (candIdx == null || candIdx == 0L || candIdx == 1L) return 90
                    return 85
                }
            }

            // 3. Direct Idx match on master
            if (target.hasExplicitIdx && candidate.hasExplicitIdx && targetIdx != null && targetIdx == candIdx) {
                return 85
            }

            // 4. Exact standard placeholder types (Date, Footer, Slide Number, Header)
            if (targetPh != null && targetPh == candPh) {
                return 90
            }

            // 5. If target has no type but has idx -> maps to master body
            if (targetPh == null && target.hasExplicitIdx && (candPh == Placeholder.BODY || candPh == Placeholder.CONTENT)) {
                return 80
            }

            return 0
        }
    }

    fun getAnchorBoundsEmu(shape: Any): LongArray? {
        try {
            val anchor = invokeMethod(shape, "getAnchor") ?: return null
            val x = (invokeMethod(anchor, "getX") as? Number)?.toDouble()
            val y = (invokeMethod(anchor, "getY") as? Number)?.toDouble()
            val w = (invokeMethod(anchor, "getWidth") as? Number)?.toDouble()
            val h = (invokeMethod(anchor, "getHeight") as? Number)?.toDouble()

            if (x != null && y != null && w != null && h != null && w > 0 && h > 0) {
                return longArrayOf(
                    (x * 12700.0).toLong(),
                    (y * 12700.0).toLong(),
                    (w * 12700.0).toLong(),
                    (h * 12700.0).toLong()
                )
            }
        } catch (_: Throwable) { }
        return null
    }

    /**
     * Extracts normalized shape bounds (left, top, width, height: 0.0f..1.0f).
     * 1. Checks XML on shape itself and composes any group ancestry transforms.
     * 2. If placeholder without direct xfrm, resolves from Slide Layout -> Slide Master chain.
     * 3. Falls back to getAnchor() reflection with group transform application.
     */
    fun getShapeNormalizedBounds(
        shape: Any,
        slide: Any?,
        slideWidthEmu: Long,
        slideHeightEmu: Long,
        groupAncestors: List<GroupTransform> = emptyList()
    ): FloatArray? {
        if (slideWidthEmu <= 0 || slideHeightEmu <= 0) return null

        // Strategy 1: XML direct EMU extraction + apply group ancestry transform
        val rawEmu = getXmlShapeRawBoundsEmu(shape)
        if (rawEmu != null) {
            var current: LongArray = rawEmu
            for (gt in groupAncestors.asReversed()) {
                current = gt.transformRect(current[0], current[1], current[2], current[3])
            }
            return floatArrayOf(
                (current[0].toFloat() / slideWidthEmu.toFloat()).coerceIn(0f, 1f),
                (current[1].toFloat() / slideHeightEmu.toFloat()).coerceIn(0f, 1f),
                (current[2].toFloat() / slideWidthEmu.toFloat()).coerceIn(0.001f, 1f),
                (current[3].toFloat() / slideHeightEmu.toFloat()).coerceIn(0.001f, 1f)
            )
        }

        // Strategy 2: If placeholder, resolve through the full inheritance chain (Slide -> Layout -> Master)
        val targetInfo = extractPlaceholderInfo(shape)
        val shapeName = getShapeName(shape)

        if (targetInfo != null && slide != null) {
            val targetTypeDesc = targetInfo.placeholder?.name ?: targetInfo.typeName ?: "unspecified"
            val targetIdxDesc = targetInfo.idx?.toString() ?: "unspecified"

            logDebug(
                "PptxShapeExtractor",
                "Placeholder resolution for shape '$shapeName': type=$targetTypeDesc (explicit=${targetInfo.hasExplicitType}), idx=$targetIdxDesc (explicit=${targetInfo.hasExplicitIdx})"
            )

            // Resolve slideLayout and slideMaster
            val slideLayout = if (slide is XSLFSlide) {
                try { slide.slideLayout } catch (_: Throwable) { null }
            } else {
                invokeMethod(slide, "getSlideLayout")
            }

            val slideMaster = if (slideLayout is XSLFSlideLayout) {
                try { slideLayout.slideMaster } catch (_: Throwable) { null }
            } else if (slideLayout != null) {
                invokeMethod(slideLayout, "getSlideMaster")
            } else if (slide is XSLFSlide) {
                try { slide.slideMaster } catch (_: Throwable) { null }
            } else {
                invokeMethod(slide, "getSlideMaster")
            }

            val layoutShapes: List<Any> = if (slideLayout is XSLFSlideLayout) {
                try { slideLayout.shapes } catch (_: Throwable) { emptyList() }
            } else if (slideLayout != null) {
                (invokeMethod(slideLayout, "getShapes") as? List<*>)?.filterNotNull() ?: emptyList()
            } else {
                emptyList()
            }

            val masterShapes: List<Any> = if (slideMaster is XSLFSlideMaster) {
                try { slideMaster.shapes } catch (_: Throwable) { emptyList() }
            } else if (slideMaster != null) {
                (invokeMethod(slideMaster, "getShapes") as? List<*>)?.filterNotNull() ?: emptyList()
            } else {
                emptyList()
            }

            val layoutName = if (slideLayout != null) getShapeName(slideLayout) else "none"
            logDebug("PptxShapeExtractor", "Searching slideLayout '$layoutName' (${layoutShapes.size} shapes):")

            var bestLShape: Any? = null
            var bestLScore = 0
            var bestLInfo: PlaceholderInfo? = null

            for (lShape in layoutShapes) {
                val lInfo = extractPlaceholderInfo(lShape)
                val lName = getShapeName(lShape)
                val lHasXfrm = getXmlShapeRawBoundsEmu(lShape) != null
                val lTypeDesc = lInfo?.placeholder?.name ?: lInfo?.typeName ?: "none"
                val lIdxDesc = lInfo?.idx?.toString() ?: "none"

                logDebug(
                    "PptxShapeExtractor",
                    "  [Layout Candidate] shape='$lName', type=$lTypeDesc, idx=$lIdxDesc, hasXfrm=$lHasXfrm"
                )

                if (lInfo != null) {
                    val score = matchPlaceholderScore(targetInfo, lInfo, isMaster = false)
                    if (score > bestLScore) {
                        bestLScore = score
                        bestLShape = lShape
                        bestLInfo = lInfo
                    }
                }
            }

            var resolvedBoundsEmu: LongArray? = null

            if (bestLShape != null && bestLScore > 0) {
                val bestLName = getShapeName(bestLShape)
                logDebug("PptxShapeExtractor", "Matched layout shape '$bestLName' (score=$bestLScore)")

                // Check if layout shape has direct xfrm
                resolvedBoundsEmu = getXmlShapeRawBoundsEmu(bestLShape) ?: getAnchorBoundsEmu(bestLShape)
                if (resolvedBoundsEmu != null) {
                    logDebug("PptxShapeExtractor", "Resolved bounds from slideLayout '$bestLName': [${resolvedBoundsEmu.joinToString()}]")
                } else {
                    logDebug("PptxShapeExtractor", "Matched layout shape '$bestLName' has no explicit xfrm. Falling back to slideMaster in chain.")
                }
            } else {
                logDebug("PptxShapeExtractor", "No matching shape found in slideLayout. Falling back to slideMaster in chain.")
            }

            // Step 2 Fallback: If layout resolution didn't provide bounds, search slideMaster
            if (resolvedBoundsEmu == null && masterShapes.isNotEmpty()) {
                val masterTargetInfo = bestLInfo ?: targetInfo
                val mTargetType = masterTargetInfo.placeholder?.name ?: masterTargetInfo.typeName
                logDebug("PptxShapeExtractor", "Searching slideMaster (${masterShapes.size} shapes) for type=$mTargetType, idx=${masterTargetInfo.idx}:")

                var bestMShape: Any? = null
                var bestMScore = 0

                for (mShape in masterShapes) {
                    val mInfo = extractPlaceholderInfo(mShape)
                    val mName = getShapeName(mShape)
                    val mHasXfrm = getXmlShapeRawBoundsEmu(mShape) != null
                    val mTypeDesc = mInfo?.placeholder?.name ?: mInfo?.typeName ?: "none"
                    val mIdxDesc = mInfo?.idx?.toString() ?: "none"

                    logDebug(
                        "PptxShapeExtractor",
                        "  [Master Candidate] shape='$mName', type=$mTypeDesc, idx=$mIdxDesc, hasXfrm=$mHasXfrm"
                    )

                    if (mInfo != null) {
                        val score = matchPlaceholderScore(masterTargetInfo, mInfo, isMaster = true)
                        if (score > bestMScore) {
                            bestMScore = score
                            bestMShape = mShape
                        }
                    }
                }

                if (bestMShape != null && bestMScore > 0) {
                    val bestMName = getShapeName(bestMShape)
                    logDebug("PptxShapeExtractor", "Matched master shape '$bestMName' (score=$bestMScore)")
                    resolvedBoundsEmu = getXmlShapeRawBoundsEmu(bestMShape) ?: getAnchorBoundsEmu(bestMShape)
                    if (resolvedBoundsEmu != null) {
                        logDebug("PptxShapeExtractor", "Resolved bounds from slideMaster '$bestMName': [${resolvedBoundsEmu.joinToString()}]")
                    } else {
                        logWarn("PptxShapeExtractor", "Matched master shape '$bestMName' has no bounds.")
                    }
                } else {
                    logWarn("PptxShapeExtractor", "No matching placeholder found in slideMaster for type=$targetTypeDesc, idx=$targetIdxDesc")
                }
            }

            if (resolvedBoundsEmu != null) {
                var current: LongArray = resolvedBoundsEmu
                for (gt in groupAncestors.asReversed()) {
                    current = gt.transformRect(current[0], current[1], current[2], current[3])
                }
                return floatArrayOf(
                    (current[0].toFloat() / slideWidthEmu.toFloat()).coerceIn(0f, 1f),
                    (current[1].toFloat() / slideHeightEmu.toFloat()).coerceIn(0f, 1f),
                    (current[2].toFloat() / slideWidthEmu.toFloat()).coerceIn(0.001f, 1f),
                    (current[3].toFloat() / slideHeightEmu.toFloat()).coerceIn(0.001f, 1f)
                )
            }
        }

        // Strategy 3: Try getAnchor() via reflection on the shape itself
        val anchorEmu = getAnchorBoundsEmu(shape)
        if (anchorEmu != null) {
            var current: LongArray = anchorEmu
            for (gt in groupAncestors.asReversed()) {
                current = gt.transformRect(current[0], current[1], current[2], current[3])
            }
            return floatArrayOf(
                (current[0].toFloat() / slideWidthEmu.toFloat()).coerceIn(0f, 1f),
                (current[1].toFloat() / slideHeightEmu.toFloat()).coerceIn(0f, 1f),
                (current[2].toFloat() / slideWidthEmu.toFloat()).coerceIn(0.001f, 1f),
                (current[3].toFloat() / slideHeightEmu.toFloat()).coerceIn(0.001f, 1f)
            )
        }

        return null
    }

    /**
     * Complete presentation parser that produces a pure Kotlin [ParsedPresentation] model.
     * Decouples presentation document parsing from rendering.
     */
    fun parsePresentation(slideShow: SlideShow<*, *>): ParsedPresentation {
        val (widthEmu, heightEmu) = getSlideDimensionsEmu(slideShow)
        val slideAspectRatio = if (heightEmu > 0) widthEmu.toFloat() / heightEmu.toFloat() else (16f / 9f)

        val parsedSlides = slideShow.slides.mapIndexed { index, slide ->
            parseSlide(slide, index + 1, widthEmu, heightEmu, slideAspectRatio)
        }

        return ParsedPresentation(parsedSlides, widthEmu, heightEmu)
    }

    /**
     * Parses an individual slide into a decoupled [ParsedSlide] instance.
     */
    fun parseSlide(
        slide: Slide<*, *>,
        slideNumber: Int,
        slideWidthEmu: Long,
        slideHeightEmu: Long,
        aspectRatio: Float
    ): ParsedSlide {
        // 1. Background parsing
        var background: ParsedBackground = ParsedBackground.DefaultWhite

        val bgPicPair = extractSlideBackgroundPicture(slide)
        if (bgPicPair != null && bgPicPair.first.isNotEmpty()) {
            background = ParsedBackground.ImageFill(bgPicPair.first, bgPicPair.second)
        } else {
            val bgColorHex = getSlideBgColorHex(slide)
            if (bgColorHex != null) {
                background = ParsedBackground.SolidColor(bgColorHex)
            }
        }

        // 2. Speaker Notes parsing
        val speakerNotes: String? = try {
            val notesObj = try { slide.notes } catch (_: Throwable) { null }
            if (notesObj != null) {
                val shapes = try { notesObj.shapes } catch (_: Throwable) { emptyList() }
                var noteText: String? = null
                for (sh in shapes) {
                    if (sh is org.apache.poi.sl.usermodel.TextShape<*, *>) {
                        val t = try { sh.text } catch (_: Throwable) { null }
                        if (!t.isNullOrBlank()) {
                            noteText = t
                            break
                        }
                    }
                }
                noteText
            } else null
        } catch (_: Throwable) { null }

        // 3. Shape Tree parsing (Recursive group handling & failure mode bounds enforcement)
        val zIndexCounter = java.util.concurrent.atomic.AtomicInteger(0)
        val rootShapes = try { slide.shapes } catch (_: Throwable) { emptyList() }
        val parsedShapes = parseShapesRecursive(rootShapes, slide, slideWidthEmu, slideHeightEmu, emptyList(), zIndexCounter)

        return ParsedSlide(
            slideNumber = slideNumber,
            background = background,
            shapes = parsedShapes,
            speakerNotes = speakerNotes,
            aspectRatio = aspectRatio
        )
    }

    private fun parseShapesRecursive(
        shapes: List<Any>,
        slide: Any,
        slideWidthEmu: Long,
        slideHeightEmu: Long,
        groupAncestors: List<GroupTransform>,
        zIndexCounter: java.util.concurrent.atomic.AtomicInteger
    ): List<ParsedShape> {
        val result = mutableListOf<ParsedShape>()

        for (shape in shapes) {
            val zIndex = zIndexCounter.getAndIncrement()
            val shapeName = getShapeName(shape)

            // Group shape recursion
            if (shape is org.apache.poi.sl.usermodel.GroupShape<*, *>) {
                val gt = extractGroupTransform(shape)
                val nextAncestry = if (gt != null) groupAncestors + gt else groupAncestors
                val childShapes = try { shape.shapes } catch (_: Throwable) { emptyList() }

                val groupBoundsArray = getShapeNormalizedBounds(shape, slide, slideWidthEmu, slideHeightEmu, groupAncestors)
                val groupBounds = if (groupBoundsArray != null) {
                    NormalizedBounds(groupBoundsArray[0], groupBoundsArray[1], groupBoundsArray[2], groupBoundsArray[3])
                } else {
                    NormalizedBounds(0f, 0f, 1f, 1f)
                }

                val children = parseShapesRecursive(childShapes, slide, slideWidthEmu, slideHeightEmu, nextAncestry, zIndexCounter)
                if (children.isNotEmpty()) {
                    result.add(ParsedShape.GroupShape("group_$zIndex", groupBounds, zIndex, children))
                }
                continue
            }

            // FAILURE MODE POLICY: Strictly omit any shape whose bounds cannot be resolved through full inheritance chain
            val normBoundsArray = getShapeNormalizedBounds(shape, slide, slideWidthEmu, slideHeightEmu, groupAncestors)
                ?: continue // OMIT SHAPE, NO HARDCODED FALLBACK RECTS

            val bounds = NormalizedBounds(normBoundsArray[0], normBoundsArray[1], normBoundsArray[2], normBoundsArray[3])

            // 1. Picture shape / blip fill
            val picPair = extractPictureDataFromShape(shape, slide)
            if (picPair != null && picPair.first.isNotEmpty()) {
                result.add(
                    ParsedShape.ImageShape(
                        id = "img_$zIndex",
                        bounds = bounds,
                        zIndex = zIndex,
                        imageBytes = picPair.first,
                        contentType = picPair.second
                    )
                )
                continue
            }

            // 2. Table shape
            if (shape is org.apache.poi.sl.usermodel.TableShape<*, *>) {
                val numRows = try { shape.numberOfRows } catch (_: Throwable) { 0 }
                val numCols = try { shape.numberOfColumns } catch (_: Throwable) { 0 }
                if (numRows > 0 && numCols > 0) {
                    val cells = mutableListOf<List<ParsedTableCell>>()
                    for (r in 0 until numRows) {
                        val rowCells = mutableListOf<ParsedTableCell>()
                        for (c in 0 until numCols) {
                            val cell = try { shape.getCell(r, c) } catch (_: Throwable) { null }
                            val cellTextShape = if (cell != null) {
                                val cellParagraphs = extractParagraphsFromTextShape(cell, false)
                                if (cellParagraphs.isNotEmpty()) {
                                    ParsedShape.TextShape(
                                        id = "cell_${r}_${c}_$zIndex",
                                        bounds = bounds,
                                        zIndex = zIndex,
                                        isTitle = false,
                                        paragraphs = cellParagraphs
                                    )
                                } else null
                            } else null
                            rowCells.add(ParsedTableCell(cellTextShape))
                        }
                        cells.add(rowCells)
                    }
                    result.add(ParsedShape.TableShape("table_$zIndex", bounds, zIndex, numRows, numCols, cells))
                    continue
                }
            }

            // 3. Text shape
            if (shape is org.apache.poi.sl.usermodel.TextShape<*, *>) {
                val shapeText = try { shape.text ?: "" } catch (_: Throwable) { "" }
                if (shapeText.isNotBlank()) {
                    val isTitle = try {
                        shape.placeholder == Placeholder.TITLE || shape.placeholder == Placeholder.CENTERED_TITLE
                    } catch (_: Throwable) {
                        shapeName.lowercase().contains("title")
                    }

                    val paragraphs = extractParagraphsFromTextShape(shape, isTitle)
                    if (paragraphs.isNotEmpty()) {
                        result.add(
                            ParsedShape.TextShape(
                                id = "text_$zIndex",
                                bounds = bounds,
                                zIndex = zIndex,
                                isTitle = isTitle,
                                paragraphs = paragraphs
                            )
                        )
                        continue
                    }
                }
            }

            // 4. Simple vector shape
            if (shape is org.apache.poi.xslf.usermodel.XSLFSimpleShape) {
                val shapeType = try { shape.shapeType?.name?.lowercase() ?: "rect" } catch (_: Throwable) { "rect" }
                val fillColor = extractShapeFillColorHex(shape)
                val strokeColor = extractShapeStrokeColorHex(shape)
                val strokeW = try { (shape.lineWidth * 2f).toFloat() } catch (_: Throwable) { 1f }

                if (fillColor != null || strokeColor != null) {
                    result.add(
                        ParsedShape.VectorShape(
                            id = "vector_$zIndex",
                            bounds = bounds,
                            zIndex = zIndex,
                            shapeType = shapeType,
                            fillColorHex = fillColor,
                            strokeColorHex = strokeColor,
                            strokeWidthDp = strokeW
                        )
                    )
                }
            }
        }

        return result
    }

    private fun extractParagraphsFromTextShape(
        textShape: org.apache.poi.sl.usermodel.TextShape<*, *>,
        isTitle: Boolean
    ): List<ParsedParagraph> {
        val paragraphs = try { textShape.textParagraphs } catch (_: Throwable) { emptyList() }
        val result = mutableListOf<ParsedParagraph>()

        for (p in paragraphs) {
            val runs = try { p.textRuns } catch (_: Throwable) { emptyList() }
            val parsedRuns = mutableListOf<ParsedTextRun>()

            for (r in runs) {
                val text = getTextFromRun(r)
                if (text.isNotBlank()) {
                    val isBold = try { r.isBold } catch (_: Throwable) { false }
                    val isItalic = try { r.isItalic } catch (_: Throwable) { false }
                    val isUnderline = try { r.isUnderlined } catch (_: Throwable) { false }
                    val colorHex = extractTextRunColorHex(r)
                    val fSize = try { r.fontSize } catch (_: Throwable) { null }
                    val fontSizePt = if (fSize != null && fSize > 0) fSize.toFloat() else (if (isTitle) 24f else 14f)
                    val fontFam = try { r.fontFamily } catch (_: Throwable) { null }

                    parsedRuns.add(ParsedTextRun(text, isBold, isItalic, isUnderline, colorHex, fontSizePt, fontFam))
                }
            }

            if (parsedRuns.isNotEmpty()) {
                val alignStr = try { p.textAlign?.name ?: "LEFT" } catch (_: Throwable) { "LEFT" }
                val alignment = when (alignStr.uppercase()) {
                    "CENTER" -> TextAlignment.CENTER
                    "RIGHT" -> TextAlignment.RIGHT
                    "JUSTIFY" -> TextAlignment.JUSTIFY
                    else -> TextAlignment.LEFT
                }

                val bulletLevel = try { p.indentLevel } catch (_: Throwable) { 0 }
                val hasBullet = try {
                    if (p is XSLFTextParagraph) {
                        p.bulletCharacter != null || p.indentLevel > 0
                    } else {
                        p.indentLevel > 0
                    }
                } catch (_: Throwable) { false }

                val bulletChar = try {
                    if (p is XSLFTextParagraph) p.bulletCharacter ?: "" else ""
                } catch (_: Throwable) { "" }

                result.add(ParsedParagraph(parsedRuns, bulletLevel, hasBullet, bulletChar, alignment))
            }
        }
        return result
    }

    private fun extractShapeFillColorHex(shape: org.apache.poi.xslf.usermodel.XSLFSimpleShape): String? {
        try {
            val colorObj = shape.javaClass.getMethod("getFillColor").invoke(shape)
            if (colorObj != null) {
                val rgb = colorObj.javaClass.getMethod("getRGB").invoke(colorObj) as? Int
                if (rgb != null) return String.format("#%06X", 0xFFFFFF and rgb)
            }
        } catch (_: Throwable) { }

        try {
            val xml = getXmlObjectReflection(shape) ?: return null
            val spPr = invokeMethod(xml, "getSpPr") ?: return null
            val solidFill = invokeMethod(spPr, "getSolidFill") ?: return null
            val srgbClr = invokeMethod(solidFill, "getSrgbClr") ?: return null
            val hexBytes = invokeMethod(srgbClr, "getVal") as? ByteArray
            if (hexBytes != null && hexBytes.size >= 3) {
                val hex = hexBytes.joinToString("") { String.format("%02X", it) }
                return "#$hex"
            }
        } catch (_: Throwable) { }
        return null
    }

    private fun extractShapeStrokeColorHex(shape: org.apache.poi.xslf.usermodel.XSLFSimpleShape): String? {
        try {
            val colorObj = shape.javaClass.getMethod("getLineColor").invoke(shape)
            if (colorObj != null) {
                val rgb = colorObj.javaClass.getMethod("getRGB").invoke(colorObj) as? Int
                if (rgb != null) return String.format("#%06X", 0xFFFFFF and rgb)
            }
        } catch (_: Throwable) { }

        try {
            val xml = getXmlObjectReflection(shape) ?: return null
            val spPr = invokeMethod(xml, "getSpPr") ?: return null
            val ln = invokeMethod(spPr, "getLn") ?: return null
            val solidFill = invokeMethod(ln, "getSolidFill") ?: return null
            val srgbClr = invokeMethod(solidFill, "getSrgbClr") ?: return null
            val hexBytes = invokeMethod(srgbClr, "getVal") as? ByteArray
            if (hexBytes != null && hexBytes.size >= 3) {
                val hex = hexBytes.joinToString("") { String.format("%02X", it) }
                return "#$hex"
            }
        } catch (_: Throwable) { }
        return null
    }

    /**
     * Extracts blipId (e.g. "rId2") from XML structure for picture extraction.
     */
    fun extractBlipEmbedId(xml: Any): String? {
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

        // XML String Regex fallback
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

    fun resolvePictureBytesFromBlipId(slideOrSheet: Any, blipId: String): Pair<ByteArray, String?>? {
        val candidates = mutableListOf<Any>()
        candidates.add(slideOrSheet)
        if (slideOrSheet is XSLFSlide) {
            try { slideOrSheet.slideLayout?.let { candidates.add(it) } } catch (_: Throwable) { }
            try { slideOrSheet.slideMaster?.let { candidates.add(it) } } catch (_: Throwable) { }
        } else if (slideOrSheet is XSLFSlideLayout) {
            try { slideOrSheet.slideMaster?.let { candidates.add(it) } } catch (_: Throwable) { }
        }

        for (candidate in candidates) {
            // Strategy 1: getRelationPartById
            try {
                val relPart = if (candidate is XSLFSlide) {
                    candidate.getRelationPartById(blipId)
                } else {
                    invokeMethod(candidate, "getRelationPartById", blipId)
                }
                if (relPart != null) {
                    val docPart = invokeMethod(relPart, "getDocumentPart")
                        ?: try {
                            val f = relPart.javaClass.getDeclaredField("documentPart")
                            f.isAccessible = true
                            f.get(relPart)
                        } catch (_: Throwable) { null }
                    if (docPart is XSLFPictureData) {
                        return Pair(docPart.data, docPart.contentType)
                    }
                }
            } catch (_: Throwable) { }

            // Strategy 2: getRelationById
            try {
                val relDoc = if (candidate is XSLFSlide) {
                    candidate.getRelationById(blipId)
                } else {
                    invokeMethod(candidate, "getRelationById", blipId)
                }
                if (relDoc is XSLFPictureData) {
                    return Pair(relDoc.data, relDoc.contentType)
                }
            } catch (_: Throwable) { }

            // Strategy 3: packagePart relationship
            try {
                val packagePart = if (candidate is XSLFSlide) candidate.packagePart else invokeMethod(candidate, "getPackagePart") as? org.apache.poi.openxml4j.opc.PackagePart
                if (packagePart != null) {
                    val rel = packagePart.getRelationship(blipId)
                    if (rel != null) {
                        val part = packagePart.getRelatedPart(rel) ?: packagePart.getPackage().getPart(rel)
                        val bytes = part?.inputStream?.use { stream -> stream.readBytes() }
                        if (bytes != null && bytes.isNotEmpty()) {
                            return Pair(bytes, part.contentType)
                        }
                    }
                }
            } catch (_: Throwable) { }
        }

        // Strategy 4: match against all pictureData in presentation
        try {
            val slideShow = if (slideOrSheet is XSLFSlide) {
                slideOrSheet.slideShow
            } else {
                invokeMethod(slideOrSheet, "getSlideShow") as? org.apache.poi.xslf.usermodel.XMLSlideShow
            }
            if (slideShow != null) {
                for (pd in slideShow.pictureData) {
                    val pdPartName = pd.packagePart?.partName?.toString() ?: ""
                    if (pdPartName.contains(blipId, ignoreCase = true)) {
                        return Pair(pd.data, pd.contentType)
                    }
                }
            }
        } catch (_: Throwable) { }

        return null
    }

    /**
     * Extracts slide background image bytes across Slide -> SlideLayout -> SlideMaster inheritance chain.
     */
    fun extractSlideBackgroundPicture(slide: Slide<*, *>): Pair<ByteArray, String?>? {
        if (slide !is XSLFSlide) return null

        // Strategy 1: POI background shape pictureData
        val bgCandidates = listOfNotNull(
            try { slide.background } catch (_: Throwable) { null },
            try { slide.slideLayout?.background } catch (_: Throwable) { null },
            try { slide.slideMaster?.background } catch (_: Throwable) { null }
        )

        for (bgShape in bgCandidates) {
            try {
                val pd = invokeMethod(bgShape, "getPictureData") as? XSLFPictureData
                if (pd != null && pd.data.isNotEmpty()) {
                    return Pair(pd.data, pd.contentType)
                }
            } catch (_: Throwable) { }
        }

        // Strategy 2: Direct XML <p:bg> -> <p:bgPr> -> <a:blipFill> -> <a:blip r:embed="..."/>
        val sheetCandidates = listOfNotNull(
            slide,
            try { slide.slideLayout } catch (_: Throwable) { null },
            try { slide.slideMaster } catch (_: Throwable) { null }
        )

        for (sheet in sheetCandidates) {
            try {
                val ct = getXmlObjectReflection(sheet) ?: continue
                val cSld = invokeMethod(ct, "getCSld") ?: ct
                val bg = invokeMethod(cSld, "getBg") ?: invokeMethod(ct, "getBg")
                if (bg != null) {
                    val blipId = extractBlipEmbedId(bg)
                    if (!blipId.isNullOrBlank()) {
                        val resolved = resolvePictureBytesFromBlipId(sheet, blipId)
                        if (resolved != null && resolved.first.isNotEmpty()) {
                            return resolved
                        }
                    }
                }
            } catch (_: Throwable) { }
        }

        return null
    }

    fun extractPictureDataFromShape(shape: Any, slide: Any): Pair<ByteArray, String?>? {
        // Strategy 1: Direct PictureShape
        if (shape is org.apache.poi.sl.usermodel.PictureShape<*, *>) {
            try {
                val pd = shape.pictureData
                val data = pd?.data
                if (data != null && data.isNotEmpty()) {
                    return Pair(data, pd.contentType)
                }
            } catch (_: Throwable) { }
        }

        // Strategy 2: getPictureData method reflection
        val pd = invokeMethod(shape, "getPictureData")
        if (pd != null) {
            val data = invokeMethod(pd, "getData") as? ByteArray
            if (data != null && data.isNotEmpty()) {
                val ct = invokeMethod(pd, "getContentType") as? String
                return Pair(data, ct)
            }
        }

        // Strategy 3: getBlipId property on shape
        val blipId = invokeMethod(shape, "getBlipId") as? String
        if (!blipId.isNullOrBlank()) {
            val resolved = resolvePictureBytesFromBlipId(slide, blipId)
            if (resolved != null) return resolved
        }

        // Strategy 4: XML blip extraction
        val xml = getXmlObjectReflection(shape)
        if (xml != null) {
            val xmlBlipId = extractBlipEmbedId(xml)
            if (!xmlBlipId.isNullOrBlank()) {
                val resolved = resolvePictureBytesFromBlipId(slide, xmlBlipId)
                if (resolved != null) return resolved
            }
        }

        return null
    }

    fun cleanTextRunString(raw: String): String {
        if (raw.isBlank()) return raw
        var cleaned = raw
            .replace("\uF0A7", "")
            .replace("\uF0B7", "")
            .replace("\uF06C", "")
            .replace("\uF0D8", "")
            .replace("\uF076", "")
            .replace("\u2022", "")
            .replace("\u25CF", "")
            .replace("\u25CB", "")
            .replace("\u25AA", "")
            .replace("\u25A0", "")
            .replace("\u25BA", "")
            .replace("\u25B8", "")
            .replace("\u25B6", "")

        cleaned = cleaned.replace(Regex("""^[\'`‘’\-–—•·*▪▫◦●■□►▸→✓✔]\s*"""), "")
        cleaned = cleaned.replace(Regex("""^[-–—]\s+"""), "")

        return cleaned
    }

    fun getTextFromRun(run: TextRun): String {
        var rText = try { run.rawText ?: "" } catch (_: Throwable) { "" }
        if (rText.isBlank()) {
            rText = try {
                val method = try { run.javaClass.getMethod("getText") } catch (_: Throwable) { null }
                val t = method?.invoke(run) as? String
                if (t != null && !t.startsWith("org.apache.poi") && !t.startsWith("org.apache.xmlbeans")) t else ""
            } catch (_: Throwable) { "" }
        }
        if (rText.startsWith("org.apache.poi") ||
            rText.startsWith("org.apache.xmlbeans") ||
            (rText.startsWith("<") && rText.endsWith(">"))
        ) {
            return ""
        }
        return cleanTextRunString(rText)
    }

    fun extractTextRunColorHex(run: TextRun): String? {
        if (run is XSLFTextRun) {
            try {
                val xmlRun = getXmlObjectReflection(run) ?: return null
                val rPr = try { xmlRun.javaClass.getMethod("getRPr").invoke(xmlRun) } catch (_: Throwable) { null } ?: return null
                val solidFill = try { rPr.javaClass.getMethod("getSolidFill").invoke(rPr) } catch (_: Throwable) { null } ?: return null
                val srgb = try { solidFill.javaClass.getMethod("getSrgbClr").invoke(solidFill) } catch (_: Throwable) { null }
                if (srgb != null) {
                    val hexBytes = try { srgb.javaClass.getMethod("getVal").invoke(srgb) as? ByteArray } catch (_: Throwable) { null }
                    val hex = hexBytes?.joinToString("") { String.format("%02X", it) }
                    if (!hex.isNullOrBlank()) return "#$hex"
                }
            } catch (_: Throwable) { }
        }
        return null
    }

    fun getSlideBgColorHex(slide: Slide<*, *>): String? {
        if (slide !is XSLFSlide) return null
        try {
            val ctSlide = getXmlObjectReflection(slide) ?: return null
            val cSld = try { ctSlide.javaClass.getMethod("getCSld").invoke(ctSlide) } catch (_: Throwable) { null } ?: return null
            val bg = try { cSld.javaClass.getMethod("getBg").invoke(cSld) } catch (_: Throwable) { null } ?: return null
            val bgPr = try { bg.javaClass.getMethod("getBgPr").invoke(bg) } catch (_: Throwable) { null } ?: return null
            val solidFill = try { bgPr.javaClass.getMethod("getSolidFill").invoke(bgPr) } catch (_: Throwable) { null } ?: return null
            val srgbClr = try { solidFill.javaClass.getMethod("getSrgbClr").invoke(solidFill) } catch (_: Throwable) { null }
            if (srgbClr != null) {
                val hexBytes = try { srgbClr.javaClass.getMethod("getVal").invoke(srgbClr) as? ByteArray } catch (_: Throwable) { null }
                val hex = hexBytes?.joinToString("") { String.format("%02X", it) }
                if (!hex.isNullOrBlank()) return "#$hex"
            }
        } catch (_: Throwable) { }
        return null
    }
}
