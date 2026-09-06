package com.karnadigital.omnisuite.feature.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karnadigital.omnisuite.core.engine.SearchResult
import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.core.repository.RecentFileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.sl.usermodel.Placeholder
import org.apache.poi.sl.usermodel.Shape
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.poi.xslf.usermodel.XSLFShape
import org.apache.poi.xslf.usermodel.XSLFPictureData
import org.apache.poi.xslf.usermodel.XSLFTextParagraph
import org.apache.poi.xslf.usermodel.XSLFTextRun
import org.apache.poi.xslf.usermodel.XSLFTextShape
import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject

data class PptxTextRun(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val textColorHex: String? = null,
    val fontSizePt: Float = 14f,
    val fontFamily: String? = null
)

/**
 * Text-box / table-cell padding derived from `<a:bodyPr>` lIns/tIns/rIns/bIns (EMU).
 * Horizontal values (left/right) are stored as a fraction of slide width;
 * vertical values (top/bottom) as a fraction of slide height. The renderer
 * multiplies by the slide size in dp, so no EMU conversion is needed at draw time.
 * Defaults match the OOXML spec defaults (lIns 91440, tIns 45720, rIns 91440,
 * bIns 45720 EMU) for a 16:9 widescreen slide.
 */
data class Insets(
    val left: Float = 0.01f,
    val top: Float = 0.009f,
    val right: Float = 0.01f,
    val bottom: Float = 0.009f
)

/** Autofit mode from `<a:bodyPr>` — shrink text, resize shape, or none. */
enum class AutoFitMode { NONE, NORM_AUTOFIT, SP_AUTO_FIT }

data class PptxParagraph(
    val runs: List<PptxTextRun>,
    val bulletLevel: Int = 0,
    val hasBullet: Boolean = false,
    val bulletChar: String = "•",
    val alignment: String = "LEFT", // "LEFT", "CENTER", "RIGHT", "JUSTIFY"
    val spaceBeforePt: Float = 0f,
    val spaceAfterPt: Float = 0f,
    /** Line-spacing multiplier: 1.0 = single (100% spcPct). From `<a:lnSpc>`. */
    val lineSpacingMul: Float = 1.0f,
    /** Numbering scheme from `<a:buAutoNum type>`, e.g. "arabicPeriod". Null = bullet/none. */
    val numberingType: String? = null
) {
    val fullText: String get() = runs.joinToString("") { it.text }
    val primaryText: String get() = runs.firstOrNull()?.text ?: ""
    val isBold: Boolean get() = runs.firstOrNull()?.isBold ?: false
    val isItalic: Boolean get() = runs.firstOrNull()?.isItalic ?: false
    val isUnderline: Boolean get() = runs.firstOrNull()?.isUnderline ?: false
    val textColorHex: String? get() = runs.firstOrNull()?.textColorHex
    val fontSizePt: Float get() = runs.firstOrNull()?.fontSizePt ?: 14f
}

enum class ShapeGeometryType { RECTANGLE, ROUNDED_RECTANGLE, ELLIPSE, NONE }

data class ShapeBorder(
    val strokeColorHex: String? = null,
    val strokeWidthDp: Float = 1.5f
)

data class PptxTextShape(
    val id: String,
    val isTitle: Boolean = false,
    val paragraphs: List<PptxParagraph> = emptyList(),
    val shapeGeometry: ShapeGeometryType = ShapeGeometryType.RECTANGLE,
    val shapeBorder: ShapeBorder? = null,
    val shapeLeft: Float = 0.05f,
    val shapeTop: Float = 0.05f,
    val shapeWidth: Float = 0.9f,
    val shapeHeight: Float = 0.15f,
    val backgroundColorHex: String? = null,
    val zOrder: Int = 0,
    /** Text-box / cell padding from `<a:bodyPr>` (fraction of slide w/h). */
    val insets: Insets = Insets(),
    /** Autofit mode from `<a:bodyPr>`. */
    val autoFit: AutoFitMode = AutoFitMode.NONE,
    /** normAutofit fontScale in thousandths (e.g. 80000 = 80%). Null = use default. */
    val fontScale: Int? = null,
    /** normAutofit lnSpcReduction in thousandths. Null = no reduction. */
    val lnSpcReduction: Int? = null
) {
    val fullText: String get() = paragraphs.joinToString("\n") { it.fullText }
    val primaryText: String get() = paragraphs.firstOrNull()?.primaryText ?: ""
    val isBold: Boolean get() = paragraphs.firstOrNull()?.isBold ?: false
    val isItalic: Boolean get() = paragraphs.firstOrNull()?.isItalic ?: false
    val isUnderline: Boolean get() = paragraphs.firstOrNull()?.isUnderline ?: false
    val textColorHex: String? get() = paragraphs.firstOrNull()?.textColorHex
    val fontSizePt: Float get() = paragraphs.firstOrNull()?.fontSizePt ?: 14f
}

data class PptxImage(
    val filePath: String,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val zOrder: Int = 0
)

data class PptxSlide(
    val slideNumber: Int,
    val title: PptxTextShape,
    val textShapes: List<PptxTextShape>,
    val images: List<PptxImage> = emptyList(),
    val backgroundImage: PptxImage? = null,
    val speakerNotes: String? = null,
    val bgColorHex: String? = null,
    val aspectRatio: Float = 16f / 9f,
    val slideWidthPt: Float = 960f,
    val slideHeightPt: Float = 540f
)

data class PptxPresentation(val slides: List<PptxSlide>)

sealed class PptxLoadState {
    object Loading : PptxLoadState()
    data class Success(
        val presentation: PptxPresentation,
        val fileName: String,
        val slideBitmaps: List<android.graphics.Bitmap> = emptyList()
    ) : PptxLoadState()
    data class Error(val message: String) : PptxLoadState()
}

@HiltViewModel
class PptxViewerViewModel @Inject constructor(
    private val recentFileRepository: RecentFileRepository,
    private val officeConverter: com.karnadigital.omnisuite.core.engine.document.OfficeConverter
) : ViewModel() {

    private val _loadState = MutableStateFlow<PptxLoadState>(PptxLoadState.Loading)
    val loadState: StateFlow<PptxLoadState> = _loadState.asStateFlow()

    private val _saveStatus = MutableSharedFlow<String>()
    val saveStatus = _saveStatus.asSharedFlow()

    private val undoStack = java.util.ArrayDeque<ByteArray>()
    private val redoStack = java.util.ArrayDeque<ByteArray>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private var activePresentation: org.apache.poi.sl.usermodel.SlideShow<*, *>? = null
    private var activeFilePath: String? = null

    private val tempImageCache = mutableMapOf<String, File>()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _currentMatchIndex = MutableStateFlow(-1)
    val currentMatchIndex: StateFlow<Int> = _currentMatchIndex.asStateFlow()

    private fun getXmlObjectReflection(obj: Any): Any? {
        return try {
            obj.javaClass.getMethod("getXmlObject").invoke(obj)
        } catch (t: Throwable) {
            try {
                val method = obj.javaClass.getDeclaredMethod("fetchXmlObject")
                method.isAccessible = true
                method.invoke(obj)
            } catch (t2: Throwable) {
                null
            }
        }
    }

    private fun extractLongValue(obj: Any?): Long? {
        if (obj == null) return null
        if (obj is Number) return obj.toLong()
        try {
            val longValMethod = obj.javaClass.getMethod("getLongValue")
            val v = longValMethod.invoke(obj)
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

    /**
     * Extracts normalized shape bounds (left, top, width, height: 0.0f..1.0f).
     * 1. Checks XMLBeans on shape itself.
     * 2. If placeholder without direct xfrm, resolves from Slide Layout or Slide Master.
     * 3. Falls back to reflection getAnchor() if available.
     */
    private fun getShapeNormalizedBounds(
        shape: Any,
        slide: Any?,
        slideWidthEmu: Long,
        slideHeightEmu: Long
    ): FloatArray? {
        // Strategy 1: XML direct EMU extraction
        val directXml = getXmlShapeBoundsNormalized(shape, slideWidthEmu, slideHeightEmu)
        if (directXml != null) return directXml

        // Strategy 2: If placeholder, resolve from Slide Layout / Master
        if (slide is XSLFSlide && shape is XSLFShape) {
            try {
                val phDetails = try { shape.placeholderDetails } catch (_: Throwable) { null }
                val phType = phDetails?.placeholder
                if (phType != null) {
                    // Check slide layout
                    val layoutShapes: List<XSLFShape> = try { slide.slideLayout?.shapes ?: emptyList() } catch (_: Throwable) { emptyList() }
                    for (lShape in layoutShapes) {
                        if (lShape.placeholderDetails?.placeholder == phType) {
                            val lBounds = getXmlShapeBoundsNormalized(lShape, slideWidthEmu, slideHeightEmu)
                            if (lBounds != null) return lBounds
                        }
                    }
                    // Check slide master
                    val masterShapes: List<XSLFShape> = try { slide.slideLayout?.slideMaster?.shapes ?: emptyList() } catch (_: Throwable) { emptyList() }
                    for (mShape in masterShapes) {
                        if (mShape.placeholderDetails?.placeholder == phType) {
                            val mBounds = getXmlShapeBoundsNormalized(mShape, slideWidthEmu, slideHeightEmu)
                            if (mBounds != null) return mBounds
                        }
                    }
                }
            } catch (_: Throwable) { }
        }

        // Strategy 3: Try getAnchor() via reflection
        try {
            val anchor = shape.javaClass.getMethod("getAnchor").invoke(shape)
            if (anchor != null) {
                val x = (anchor.javaClass.getMethod("getX").invoke(anchor) as? Number)?.toDouble()
                val y = (anchor.javaClass.getMethod("getY").invoke(anchor) as? Number)?.toDouble()
                val w = (anchor.javaClass.getMethod("getWidth").invoke(anchor) as? Number)?.toDouble()
                val h = (anchor.javaClass.getMethod("getHeight").invoke(anchor) as? Number)?.toDouble()

                val slideWPt = if (slideWidthEmu > 0) slideWidthEmu / 12700.0 else 720.0
                val slideHPt = if (slideHeightEmu > 0) slideHeightEmu / 12700.0 else 540.0

                if (x != null && y != null && w != null && h != null && w > 0 && h > 0) {
                    return floatArrayOf(
                        (x / slideWPt).toFloat().coerceIn(0f, 1f),
                        (y / slideHPt).toFloat().coerceIn(0f, 1f),
                        (w / slideWPt).toFloat().coerceIn(0.01f, 1f),
                        (h / slideHPt).toFloat().coerceIn(0.01f, 1f)
                    )
                }
            }
        } catch (_: Throwable) { }

        return null
    }

    /**
     * Extracts shape bounds from XMLBeans, trying multiple XML paths:
     * - CTShape/CTPicture → getSpPr() → getXfrm()
     * - CTGroupShape children → getGrpSpPr()
     * - Direct xfrm search via getNvSpPr parent traversal
     */
    private fun getXmlShapeBoundsNormalized(
        shape: Any,
        slideWidthEmu: Long,
        slideHeightEmu: Long
    ): FloatArray? {
        if (slideWidthEmu <= 0 || slideHeightEmu <= 0) return null
        try {
            val xml = getXmlObjectReflection(shape) ?: return null

            var xfrm: Any? = null
            xfrm = tryGetXfrm(xml, "getSpPr")
            if (xfrm == null) xfrm = tryGetXfrm(xml, "getGrpSpPr")
            if (xfrm == null) {
                for (methodName in listOf("getCxnSpPr", "getNvSpPr", "getNvPicPr", "getNvCxnSpPr")) {
                    xfrm = tryGetXfrm(xml, methodName)
                    if (xfrm != null) break
                }
            }
            if (xfrm == null) {
                xfrm = try { xml.javaClass.getMethod("getXfrm").invoke(xml) } catch (_: Throwable) { null }
            }

            if (xfrm == null) return null

            val off = try { xfrm.javaClass.getMethod("getOff").invoke(xfrm) } catch (_: Throwable) { null }
            val ext = try { xfrm.javaClass.getMethod("getExt").invoke(xfrm) } catch (_: Throwable) { null }

            val rawX = try { off?.javaClass?.getMethod("getX")?.invoke(off) } catch (_: Throwable) { null }
            val rawY = try { off?.javaClass?.getMethod("getY")?.invoke(off) } catch (_: Throwable) { null }
            val rawCx = try { ext?.javaClass?.getMethod("getCx")?.invoke(ext) } catch (_: Throwable) { null }
            val rawCy = try { ext?.javaClass?.getMethod("getCy")?.invoke(ext) } catch (_: Throwable) { null }

            val rawXVal = extractLongValue(rawX)
            val rawYVal = extractLongValue(rawY)
            val rawCxVal = extractLongValue(rawCx)
            val rawCyVal = extractLongValue(rawCy)

            if (rawXVal != null && rawYVal != null && rawCxVal != null && rawCyVal != null && rawCxVal > 0 && rawCyVal > 0) {
                var curX: Long = rawXVal
                var curY: Long = rawYVal
                var curCx: Long = rawCxVal
                var curCy: Long = rawCyVal

                // Apply parent group transforms recursively if shape is nested in a group shape
                var parentShape = (shape as? XSLFShape)?.parent
                while (parentShape is org.apache.poi.xslf.usermodel.XSLFGroupShape) {
                    val groupXml = getXmlObjectReflection(parentShape)
                    if (groupXml != null) {
                        val grpXfrm = tryGetXfrm(groupXml, "getGrpSpPr")
                            ?: try { groupXml.javaClass.getMethod("getXfrm").invoke(groupXml) } catch (_: Throwable) { null }
                        if (grpXfrm != null) {
                            val gOff = try { grpXfrm.javaClass.getMethod("getOff").invoke(grpXfrm) } catch (_: Throwable) { null }
                            val gExt = try { grpXfrm.javaClass.getMethod("getExt").invoke(grpXfrm) } catch (_: Throwable) { null }
                            val gChOff = try { grpXfrm.javaClass.getMethod("getChOff").invoke(grpXfrm) } catch (_: Throwable) { null }
                            val gChExt = try { grpXfrm.javaClass.getMethod("getChExt").invoke(grpXfrm) } catch (_: Throwable) { null }

                            val gX = extractLongValue(try { gOff?.javaClass?.getMethod("getX")?.invoke(gOff) } catch (_: Throwable) { null }) ?: 0L
                            val gY = extractLongValue(try { gOff?.javaClass?.getMethod("getY")?.invoke(gOff) } catch (_: Throwable) { null }) ?: 0L
                            val gCx = extractLongValue(try { gExt?.javaClass?.getMethod("getCx")?.invoke(gExt) } catch (_: Throwable) { null }) ?: slideWidthEmu
                            val gCy = extractLongValue(try { gExt?.javaClass?.getMethod("getCy")?.invoke(gExt) } catch (_: Throwable) { null }) ?: slideHeightEmu

                            val chX = extractLongValue(try { gChOff?.javaClass?.getMethod("getX")?.invoke(gChOff) } catch (_: Throwable) { null }) ?: gX
                            val chY = extractLongValue(try { gChOff?.javaClass?.getMethod("getY")?.invoke(gChOff) } catch (_: Throwable) { null }) ?: gY
                            val chCx = extractLongValue(try { gChExt?.javaClass?.getMethod("getCx")?.invoke(gChExt) } catch (_: Throwable) { null }) ?: gCx
                            val chCy = extractLongValue(try { gChExt?.javaClass?.getMethod("getCy")?.invoke(gChExt) } catch (_: Throwable) { null }) ?: gCy

                            val scaleX = if (chCx > 0) gCx.toDouble() / chCx.toDouble() else 1.0
                            val scaleY = if (chCy > 0) gCy.toDouble() / chCy.toDouble() else 1.0

                            curX = (gX + (curX - chX) * scaleX).toLong()
                            curY = (gY + (curY - chY) * scaleY).toLong()
                            curCx = (curCx * scaleX).toLong()
                            curCy = (curCy * scaleY).toLong()
                        }
                    }
                    parentShape = parentShape.parent
                }

                return floatArrayOf(
                    (curX.toFloat() / slideWidthEmu.toFloat()).coerceIn(0f, 1f),
                    (curY.toFloat() / slideHeightEmu.toFloat()).coerceIn(0f, 1f),
                    (curCx.toFloat() / slideWidthEmu.toFloat()).coerceIn(0.001f, 1f),
                    (curCy.toFloat() / slideHeightEmu.toFloat()).coerceIn(0.001f, 1f)
                )
            }
        } catch (_: Throwable) { }
        return null
    }

    /** Helper: tries parentObj.getMethodName().getXfrm() via reflection */
    private fun tryGetXfrm(parentObj: Any, prMethodName: String): Any? {
        return try {
            val pr = parentObj.javaClass.getMethod(prMethodName).invoke(parentObj) ?: return null
            pr.javaClass.getMethod("getXfrm").invoke(pr)
        } catch (_: Throwable) { null }
    }

    /**
     * Extracts a long attribute (e.g. inset EMU) from an XML object via reflection.
     * Returns null if the getter is absent or returns null.
     */
    private fun extractLongAttr(obj: Any?, getterName: String): Long? {
        if (obj == null) return null
        return try {
            val v = obj.javaClass.getMethod(getterName).invoke(obj)
            extractLongValue(v)
        } catch (_: Throwable) { null }
    }

    /**
     * Extracts text-box padding (bodyPr lIns/tIns/rIns/bIns) and autofit mode from a
     * text shape's `<a:txBody>/<a:bodyPr>`. Insets are converted to a fraction of the
     * slide width/height so the renderer can multiply by the slide size in dp.
     * Falls back to OOXML spec defaults (lIns 91440, tIns 45720, rIns 91440, bIns 45720
     * EMU) when the source omits them.
     */
    private fun extractBodyPr(
        shape: Any,
        slideWidthEmu: Long,
        slideHeightEmu: Long
    ): BodyPrResult {
        val w = if (slideWidthEmu > 0) slideWidthEmu.toFloat() else 9144000f
        val h = if (slideHeightEmu > 0) slideHeightEmu.toFloat() else 5143500f
        var insets = Insets()
        var autoFit = AutoFitMode.NONE
        var fontScale: Int? = null
        var lnSpcReduction: Int? = null
        try {
            val xml = getXmlObjectReflection(shape) ?: return BodyPrResult(insets, autoFit, fontScale, lnSpcReduction)
            val txBody = try { xml.javaClass.getMethod("getTxBody").invoke(xml) } catch (_: Throwable) { null }
                ?: return BodyPrResult(insets, autoFit, fontScale, lnSpcReduction)
            val bodyPr = try { txBody.javaClass.getMethod("getBodyPr").invoke(txBody) } catch (_: Throwable) { null }
                ?: return BodyPrResult(insets, autoFit, fontScale, lnSpcReduction)

            val lIns = extractLongAttr(bodyPr, "getLIns")
            val tIns = extractLongAttr(bodyPr, "getTIns")
            val rIns = extractLongAttr(bodyPr, "getRIns")
            val bIns = extractLongAttr(bodyPr, "getBIns")
            insets = Insets(
                left = ((lIns ?: 91440L).toFloat() / w).coerceAtLeast(0f),
                top = ((tIns ?: 45720L).toFloat() / h).coerceAtLeast(0f),
                right = ((rIns ?: 91440L).toFloat() / w).coerceAtLeast(0f),
                bottom = ((bIns ?: 45720L).toFloat() / h).coerceAtLeast(0f)
            )

            // normAutofit (shrink text on overflow)
            val normAuto = try { bodyPr.javaClass.getMethod("getNormAutofit").invoke(bodyPr) } catch (_: Throwable) { null }
            if (normAuto != null) {
                autoFit = AutoFitMode.NORM_AUTOFIT
                fontScale = try {
                    val v = normAuto.javaClass.getMethod("getFontScale").invoke(normAuto)
                    (v as? Number)?.toInt()
                } catch (_: Throwable) { null }
                lnSpcReduction = try {
                    val v = normAuto.javaClass.getMethod("getLnSpcReduction").invoke(normAuto)
                    (v as? Number)?.toInt()
                } catch (_: Throwable) { null }
            }

            // spAutoFit (resize shape to fit text) — only if normAutofit not present
            val spAuto = try { bodyPr.javaClass.getMethod("getSpAutoFit").invoke(bodyPr) } catch (_: Throwable) { null }
            if (spAuto != null && autoFit == AutoFitMode.NONE) {
                autoFit = AutoFitMode.SP_AUTO_FIT
            }
        } catch (_: Throwable) { }
        return BodyPrResult(insets, autoFit, fontScale, lnSpcReduction)
    }

    private data class BodyPrResult(
        val insets: Insets,
        val autoFit: AutoFitMode,
        val fontScale: Int?,
        val lnSpcReduction: Int?
    )

    /**
     * Extracts the line-spacing multiplier from a paragraph's `<a:pPr>/<a:lnSpc>`.
     * spcPct (e.g. 100000 = 100% = single → 1.0) takes priority; spcPts (exact leading in
     * hundredths of a point) is converted relative to [refFontPt]. Returns 1.0 (single)
     * when unspecified.
     */
    private fun extractLineSpacingMul(pPr: Any?, refFontPt: Float): Float {
        if (pPr == null) return 1.0f
        return try {
            val lnSpc = try { pPr.javaClass.getMethod("getLnSpc").invoke(pPr) } catch (_: Throwable) { null } ?: return 1.0f
            // spcPct: percentage in 1/1000th of a percent (100000 = 100%)
            val spcPct = try {
                val pct = lnSpc.javaClass.getMethod("getSpcPct").invoke(lnSpc)
                val v = try { pct?.javaClass?.getMethod("getVal")?.invoke(pct) } catch (_: Throwable) { null }
                extractLongValue(v)
            } catch (_: Throwable) { null }
            if (spcPct != null) {
                (spcPct.toFloat() / 100000f).coerceAtLeast(0.2f)
            } else {
                // spcPts: exact leading in hundredths of a point
                val spcPts = try {
                    val pts = lnSpc.javaClass.getMethod("getSpcPts").invoke(lnSpc)
                    val v = try { pts?.javaClass?.getMethod("getVal")?.invoke(pts) } catch (_: Throwable) { null }
                    extractLongValue(v)
                } catch (_: Throwable) { null }
                if (spcPts != null) {
                    val leadingPt = spcPts.toFloat() / 100f
                    val ref = if (refFontPt > 0) refFontPt else 14f
                    (leadingPt / ref).coerceAtLeast(0.2f)
                } else 1.0f
            }
        } catch (_: Throwable) { 1.0f }
    }

    /**
     * Extracts the numbering scheme from a paragraph's `<a:pPr>/<a:buAutoNum type>`.
     * Returns the scheme string (e.g. "arabicPeriod", "alphaLcParenR") or null.
     */
    private fun extractNumberingType(pPr: Any?): String? {
        if (pPr == null) return null
        return try {
            val autoNum = try { pPr.javaClass.getMethod("getBuAutoNum").invoke(pPr) } catch (_: Throwable) { null } ?: return null
            val typeObj = try { autoNum.javaClass.getMethod("getType").invoke(autoNum) } catch (_: Throwable) { null }
            val s = typeObj?.toString()
            if (s.isNullOrBlank() || s.startsWith("org.apache")) null else s
        } catch (_: Throwable) { null }
    }

    /**
     * Extracts table geometry (gridCol widths, row heights, per-cell margins) from a
     * `<a:tbl>`. Widths/heights are in EMU; cell margins are converted to a fraction of
     * the slide size. Returns null if the table XML cannot be read.
     */
    private fun extractTableGeometry(
        shape: Any,
        slideWidthEmu: Long,
        slideHeightEmu: Long
    ): TableGeometry? {
        val w = if (slideWidthEmu > 0) slideWidthEmu.toFloat() else 9144000f
        val h = if (slideHeightEmu > 0) slideHeightEmu.toFloat() else 5143500f
        try {
            val xml = getXmlObjectReflection(shape) ?: return null
            val tbl = try { xml.javaClass.getMethod("getTbl").invoke(xml) } catch (_: Throwable) { null } ?: return null

            val gridCols = try {
                @Suppress("UNCHECKED_CAST")
                val list = tbl.javaClass.getMethod("getGridColList").invoke(tbl) as? List<*>
                list?.map { col -> extractLongAttr(col, "getW") ?: 0L } ?: emptyList()
            } catch (_: Throwable) { emptyList() }

            val rows = try {
                @Suppress("UNCHECKED_CAST")
                val rowList = tbl.javaClass.getMethod("getTrList").invoke(tbl) as? List<*>
                rowList?.map { tr ->
                    val trObj = tr ?: return@map TableRow(0L, emptyList())
                    val rowH = extractLongAttr(trObj, "getH") ?: 0L
                    val cells = try {
                        @Suppress("UNCHECKED_CAST")
                        val tcList = trObj.javaClass.getMethod("getTcList").invoke(trObj) as? List<*>
                        tcList?.map { tc ->
                            val tcPr = try { tc?.javaClass?.getMethod("getTcPr")?.invoke(tc) } catch (_: Throwable) { null }
                            CellMargins(
                                left = ((extractLongAttr(tcPr, "getMarL") ?: 45720L).toFloat() / w),
                                top = ((extractLongAttr(tcPr, "getMarT") ?: 45720L).toFloat() / h),
                                right = ((extractLongAttr(tcPr, "getMarR") ?: 45720L).toFloat() / w),
                                bottom = ((extractLongAttr(tcPr, "getMarB") ?: 45720L).toFloat() / h)
                            )
                        } ?: emptyList()
                    } catch (_: Throwable) { emptyList<CellMargins>() }
                    TableRow(rowH, cells)
                } ?: emptyList()
            } catch (_: Throwable) { emptyList<TableRow>() }

            return TableGeometry(gridCols, rows)
        } catch (_: Throwable) { return null }
    }

    private data class CellMargins(val left: Float, val top: Float, val right: Float, val bottom: Float)
    private data class TableRow(val heightEmu: Long, val cells: List<CellMargins>)
    private data class TableGeometry(val gridColWidthsEmu: List<Long>, val rows: List<TableRow>)

    /**
     * Extracts blipId (e.g. "rId2") from XML structure for picture extraction.
     * Prioritizes modern vector <asvg:svgBlip> over legacy raster fallbacks.
     */
    private fun extractBlipEmbedId(xml: Any): String? {
        val xmlStr = try { xml.toString() } catch (_: Throwable) { "" }

        // Priority 1: Check for SVG vector blip (asvg:svgBlip) in XML string or extLst
        if (xmlStr.isNotBlank()) {
            val svgMatch = Regex("""(?:asvg:svgBlip|svgBlip)[^>]*(?:r:embed|embed|r:link|link)=["'](rId\d+)["']""", RegexOption.IGNORE_CASE).find(xmlStr)
            if (svgMatch != null) {
                return svgMatch.groupValues[1]
            }
        }

        // Priority 2: Standard OOXML blip extraction
        try {
            var blipFill: Any? = null
            try { blipFill = xml.javaClass.getMethod("getBlipFill").invoke(xml) } catch (_: Throwable) {}
            if (blipFill == null) {
                try {
                    val spPr = xml.javaClass.getMethod("getSpPr").invoke(xml)
                    if (spPr != null) blipFill = spPr.javaClass.getMethod("getBlipFill").invoke(spPr)
                } catch (_: Throwable) {}
            }
            if (blipFill != null) {
                val blip = try { blipFill.javaClass.getMethod("getBlip").invoke(blipFill) } catch (_: Throwable) { null }
                if (blip != null) {
                    val blipXmlStr = try { blip.toString() } catch (_: Throwable) { "" }
                    if (blipXmlStr.isNotBlank()) {
                        val svgMatch = Regex("""(?:asvg:svgBlip|svgBlip)[^>]*(?:r:embed|embed|r:link|link)=["'](rId\d+)["']""", RegexOption.IGNORE_CASE).find(blipXmlStr)
                        if (svgMatch != null) {
                            return svgMatch.groupValues[1]
                        }
                    }
                    val embed = try { blip.javaClass.getMethod("getEmbed").invoke(blip) as? String } catch (_: Throwable) { null }
                    if (!embed.isNullOrBlank()) return embed
                }
            }
        } catch (_: Throwable) { }

        // Priority 3: Any embed/link
        if (xmlStr.isNotBlank()) {
            val match = Regex("""(?:r:embed|embed|r:link|link)=["'](rId\d+)["']""", RegexOption.IGNORE_CASE).find(xmlStr)
            if (match != null) {
                return match.groupValues[1]
            }
        }

        return null
    }

    /**
     * Resolves raw image bytes and contentType from a blip relationship ID on the slide.
     */
    private fun resolvePictureBytesFromBlipId(slide: Any, blipId: String): Pair<ByteArray, String?>? {
        if (slide !is XSLFSlide) return null
        try {
            val relDoc = slide.getRelationById(blipId)
            if (relDoc is XSLFPictureData) {
                return Pair(relDoc.data, relDoc.contentType)
            }
        } catch (_: Throwable) { }

        try {
            val sheetPart = slide.packagePart
            val rel = sheetPart?.getRelationship(blipId)
            if (rel != null) {
                val part = sheetPart.getRelatedPart(rel) ?: sheetPart.getPackage()?.getPart(rel)
                val bytes = part?.inputStream?.use { stream -> stream.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    return Pair(bytes, part.contentType)
                }
            }
        } catch (_: Throwable) { }

        try {
            val slideShow = slide.slideShow
            for (pd in slideShow.pictureData) {
                if (pd.packagePart?.partName?.name?.contains(blipId, ignoreCase = true) == true) {
                    return Pair(pd.data, pd.contentType)
                }
            }
        } catch (_: Throwable) { }

        return null
    }

    data class PicCrop(val l: Float = 0f, val t: Float = 0f, val r: Float = 0f, val b: Float = 0f)

    private fun extractBlipCrop(xml: Any): PicCrop? {
        val xmlStr = try { xml.toString() } catch (_: Throwable) { "" }
        if (xmlStr.isBlank()) return null
        val match = Regex("""<[^>]*srcRect[^>]*>""", RegexOption.IGNORE_CASE).find(xmlStr)
        if (match != null) {
            val tag = match.value
            fun getAttr(attr: String): Float {
                val m = Regex("""$attr=["'](\d+)["']""", RegexOption.IGNORE_CASE).find(tag)
                val v = m?.groupValues?.get(1)?.toIntOrNull() ?: 0
                return (v.toFloat() / 100000f).coerceIn(0f, 0.99f)
            }
            val l = getAttr("l")
            val t = getAttr("t")
            val r = getAttr("r")
            val b = getAttr("b")
            if (l > 0f || t > 0f || r > 0f || b > 0f) {
                return PicCrop(l, t, r, b)
            }
        }
        return null
    }

    /**
     * Extracts picture data from ANY shape (PictureShape, Shape with blipFill, etc.).
     * Prioritizes modern vector graphics (SVG) in XML before falling back to POI raster PictureData.
     */
    private fun extractPictureDataFromShape(shape: Any, slide: Any): Triple<ByteArray, String?, PicCrop?>? {
        // Priority 1: Check XML for <asvg:svgBlip> or modern vector graphic embed ID FIRST
        try {
            val xml = getXmlObjectReflection(shape)
            if (xml != null) {
                val crop = extractBlipCrop(xml)
                val blipId = extractBlipEmbedId(xml)
                if (!blipId.isNullOrBlank()) {
                    val resolved = resolvePictureBytesFromBlipId(slide, blipId)
                    if (resolved != null) return Triple(resolved.first, resolved.second, crop)
                }
            }
        } catch (_: Throwable) { }

        // Priority 2: POI PictureShape pictureData
        if (shape is org.apache.poi.sl.usermodel.PictureShape<*, *>) {
            try {
                val pd = shape.pictureData
                val data = pd?.data
                if (data != null && data.isNotEmpty()) {
                    val xml = getXmlObjectReflection(shape)
                    val crop = if (xml != null) extractBlipCrop(xml) else null
                    return Triple(data, pd.contentType, crop)
                }
            } catch (_: Throwable) { }
        }

        try {
            val method = shape.javaClass.getMethod("getPictureData")
            val pd = method.invoke(shape)
            if (pd != null) {
                val data = pd.javaClass.getMethod("getData").invoke(pd) as? ByteArray
                if (data != null && data.isNotEmpty()) {
                    val ct = try { pd.javaClass.getMethod("getContentType").invoke(pd) as? String } catch (_: Throwable) { null }
                    val xml = getXmlObjectReflection(shape)
                    val crop = if (xml != null) extractBlipCrop(xml) else null
                    return Triple(data, ct, crop)
                }
            }
        } catch (_: Throwable) { }

        return null
    }

    private fun savePicBytesToTempFile(slideIndex: Int, dataBytes: ByteArray, contentType: String?, crop: PicCrop? = null): File {
        val cropSuffix = if (crop != null) "_c_${crop.l}_${crop.t}_${crop.r}_${crop.b}" else ""
        val hash = dataBytes.contentHashCode().toString() + cropSuffix
        val cacheKey = "${slideIndex}_$hash"
        val cachedFile = tempImageCache[cacheKey]
        if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
            return cachedFile
        }
        val suggestExt = contentType?.substringAfter("/")?.substringBefore("+")?.lowercase() ?: "png"
        val tempFile = File.createTempFile("pptx_img_", ".$suggestExt")

        if (crop != null && (suggestExt == "png" || suggestExt == "jpeg" || suggestExt == "jpg" || suggestExt == "webp")) {
            try {
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(dataBytes, 0, dataBytes.size)
                if (bitmap != null) {
                    val origW = bitmap.width
                    val origH = bitmap.height
                    val startX = (crop.l * origW).toInt().coerceIn(0, origW - 1)
                    val startY = (crop.t * origH).toInt().coerceIn(0, origH - 1)
                    val cropW = ((1f - crop.l - crop.r) * origW).toInt().coerceIn(1, origW - startX)
                    val cropH = ((1f - crop.t - crop.b) * origH).toInt().coerceIn(1, origH - startY)
                    val cropped = android.graphics.Bitmap.createBitmap(bitmap, startX, startY, cropW, cropH)
                    tempFile.outputStream().use { out ->
                        cropped.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                    }
                    tempImageCache[cacheKey] = tempFile
                    return tempFile
                }
            } catch (_: Throwable) {}
        }

        tempFile.outputStream().use { it.write(dataBytes) }
        tempImageCache[cacheKey] = tempFile
        return tempFile
    }

    private fun cleanTextRunString(raw: String): String {
        return raw
            .replace("\uF0A7", "") // Wingdings square bullet
            .replace("\uF0B7", "") // Symbol bullet
            .replace("\uF06C", "") // Wingdings circle bullet
            .replace("\uF0D8", "")
            .replace("\uF076", "")
            .replace(Regex("""^[\'`‘’-]\s*"""), "")
    }

    private var currentThemeColors: MutableMap<String, String>? = null

    private fun getThemeColors(): MutableMap<String, String> {
        var map = currentThemeColors
        if (map == null) {
            map = mutableMapOf()
            currentThemeColors = map
        }
        return map
    }

    private fun extractThemeColorScheme(slide: org.apache.poi.sl.usermodel.Slide<*, *>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (slide !is XSLFSlide) return map
        try {
            val theme = try { slide.theme } catch (_: Throwable) { null }
                ?: try { slide.slideLayout?.slideMaster?.theme } catch (_: Throwable) { null }
            if (theme != null) {
                val ctTheme = getXmlObjectReflection(theme)
                if (ctTheme != null) {
                    val themeElements = try { ctTheme.javaClass.getMethod("getThemeElements").invoke(ctTheme) } catch (_: Throwable) { null }
                    val clrScheme = try { themeElements?.javaClass?.getMethod("getClrScheme")?.invoke(themeElements) } catch (_: Throwable) { null }
                    if (clrScheme != null) {
                        val methods = listOf(
                            "getDk1", "getLt1", "getDk2", "getLt2",
                            "getAccent1", "getAccent2", "getAccent3",
                            "getAccent4", "getAccent5", "getAccent6",
                            "getHlink", "getFolHlink"
                        )
                        for (mName in methods) {
                            try {
                                val colorObj = clrScheme.javaClass.getMethod(mName).invoke(clrScheme)
                                val hex = extractColorFromSolidFill(colorObj)
                                if (hex != null) {
                                    val key = mName.removePrefix("get").lowercase()
                                    map[key] = hex
                                }
                            } catch (_: Throwable) {}
                        }
                    }
                }
            }
        } catch (_: Throwable) {}
        return map
    }

    private fun resolveSchemeColor(schemeName: String): String? {
        val s = schemeName.lowercase()
        for ((key, hex) in getThemeColors()) {
            if (s.contains(key)) return hex
        }
        return when {
            s.contains("accent1") -> "#1E40AF" // Blue
            s.contains("accent2") -> "#EA580C" // Orange
            s.contains("accent3") -> "#0D9488" // Teal
            s.contains("accent4") -> "#7C3AED" // Purple
            s.contains("accent5") -> "#16A34A" // Green
            s.contains("accent6") -> "#E11D48" // Rose/Red
            s.contains("tx1") || s.contains("dk1") -> "#0F172A" // Dark slate
            s.contains("tx2") || s.contains("dk2") -> "#334155" // Slate
            s.contains("bg1") || s.contains("lt1") -> "#FFFFFF"
            s.contains("bg2") || s.contains("lt2") -> "#F8FAFC"
            s.contains("hlink") -> "#2563EB"
            else -> null
        }
    }

    private fun extractColorFromSolidFill(solidFill: Any?): String? {
        if (solidFill == null) return null
        try {
            val srgb = try { solidFill.javaClass.getMethod("getSrgbClr").invoke(solidFill) } catch (_: Throwable) { null }
            if (srgb != null) {
                val hexBytes = try { srgb.javaClass.getMethod("getVal").invoke(srgb) as? ByteArray } catch (_: Throwable) { null }
                val hex = hexBytes?.joinToString("") { String.format("%02X", it) }
                if (!hex.isNullOrBlank()) return "#$hex"
            }
            val schemeClr = try { solidFill.javaClass.getMethod("getSchemeClr").invoke(solidFill) } catch (_: Throwable) { null }
            if (schemeClr != null) {
                val valObj = try { schemeClr.javaClass.getMethod("getVal").invoke(schemeClr) } catch (_: Throwable) { null }
                val valStr = valObj?.toString() ?: ""
                val resolved = resolveSchemeColor(valStr)
                if (resolved != null) return resolved
            }
        } catch (_: Throwable) {}
        return null
    }

    /**
     * Safely reads slide background color in hex from XMLBeans without touching java.awt.Color.
     */
    private fun getSlideBgColorHex(slide: org.apache.poi.sl.usermodel.Slide<*, *>): String? {
        if (slide !is XSLFSlide) return null
        try {
            val ctSlide = getXmlObjectReflection(slide) ?: return null
            val cSld = try { ctSlide.javaClass.getMethod("getCSld").invoke(ctSlide) } catch (t: Throwable) { null } ?: return null
            val bg = try { cSld.javaClass.getMethod("getBg").invoke(cSld) } catch (t: Throwable) { null } ?: return null

            // Try bgPr (background properties) first
            val bgPr = try { bg.javaClass.getMethod("getBgPr").invoke(bg) } catch (t: Throwable) { null }
            if (bgPr != null) {
                val solidFill = try { bgPr.javaClass.getMethod("getSolidFill").invoke(bgPr) } catch (t: Throwable) { null }
                if (solidFill != null) {
                    val color = extractColorFromSolidFill(solidFill)
                    if (color != null) return color
                }
                // Try bgGradFill (gradient fill)
                val gradFill = try { bgPr.javaClass.getMethod("getGradFill").invoke(bgPr) } catch (t: Throwable) { null }
                if (gradFill != null) {
                    val gsLst = try { gradFill.javaClass.getMethod("getGsLst").invoke(gradFill) } catch (t: Throwable) { null }
                    if (gsLst != null && gsLst is org.apache.xmlbeans.XmlObject) {
                        val gsArray = try { gsLst.selectChildren(javax.xml.namespace.QName("http://schemas.openxmlformats.org/drawingml/2006/main", "gs")) } catch (t: Throwable) { null }
                        if (gsArray != null && gsArray.isNotEmpty()) {
                            val firstGs = gsArray[0]
                            val solidFill2 = try { firstGs.javaClass.getMethod("getSolidFill").invoke(firstGs) } catch (t: Throwable) { null }
                            if (solidFill2 != null) {
                                val color = extractColorFromSolidFill(solidFill2)
                                if (color != null) return color
                            }
                        }
                    }
                }
            }

            // Try bgRef (background reference - theme-based)
            val bgRef = try { bg.javaClass.getMethod("getBgRef").invoke(bg) } catch (t: Throwable) { null }
            if (bgRef != null) {
                val idx = try { bgRef.javaClass.getMethod("getVal").invoke(bgRef) } catch (t: Throwable) { null }
                if (idx is Int && idx > 0) {
                    val themeColors = getThemeColors()
                    val themeColorKeys = listOf("lt1", "dk1", "lt2", "dk2", "accent1", "accent2", "accent3", "accent4", "accent5", "accent6", "hlink", "folHlink")
                    val keyIndex = idx / 100 - 1
                    if (keyIndex >= 0 && keyIndex < themeColorKeys.size) {
                        return themeColors[themeColorKeys[keyIndex]]
                    }
                }
            }
        } catch (t: Throwable) {
            // Safe fallback
        }
        return null
    }

    /**
     * Safely extracts text run color without touching java.awt.Color.
     */
    private fun extractTextRunColorHex(run: org.apache.poi.sl.usermodel.TextRun): String? {
        if (run is XSLFTextRun) {
            try {
                val xmlRun = getXmlObjectReflection(run) ?: return null
                val rPr = try { xmlRun.javaClass.getMethod("getRPr").invoke(xmlRun) } catch (t: Throwable) { null } ?: return null
                val solidFill = try { rPr.javaClass.getMethod("getSolidFill").invoke(rPr) } catch (t: Throwable) { null } ?: return null
                return extractColorFromSolidFill(solidFill)
            } catch (t: Throwable) {
                // Ignore
            }
        }
        return null
    }

    /**
     * Extracts typeface name from text run properties or XML latin tag.
     */
    private fun extractRunTypeface(r: org.apache.poi.sl.usermodel.TextRun): String? {
        val family = try { r.fontFamily } catch (_: Throwable) { null }
        if (!family.isNullOrBlank() && !family.startsWith("org.apache.poi") && !family.startsWith("org.apache.xmlbeans") && !family.startsWith("+m")) {
            return family
        }
        if (r is XSLFTextRun) {
            try {
                val xmlRun = getXmlObjectReflection(r)
                if (xmlRun != null) {
                    val rPr = try { xmlRun.javaClass.getMethod("getRPr").invoke(xmlRun) } catch (_: Throwable) { null }
                    if (rPr != null) {
                        val latin = try { rPr.javaClass.getMethod("getLatin").invoke(rPr) } catch (_: Throwable) { null }
                        val typeface = try { latin?.javaClass?.getMethod("getTypeface")?.invoke(latin) as? String } catch (_: Throwable) { null }
                        if (!typeface.isNullOrBlank() && !typeface.startsWith("+m")) return typeface
                    }
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    /**
     * Extracts shape preset geometry (ellipse, roundRect, etc.), border outline, and fill color.
     */
    private fun extractShapeGeometryAndBorder(shape: Any): Triple<ShapeGeometryType, ShapeBorder?, String?> {
        var geometry = ShapeGeometryType.RECTANGLE
        var border: ShapeBorder? = null
        var bgColor: String? = null

        try {
            // 1. POI shape type
            if (shape is org.apache.poi.sl.usermodel.SimpleShape<*, *>) {
                val st = try { shape.shapeType } catch (_: Throwable) { null }
                if (st != null) {
                    val stName = st.name.lowercase()
                    if (stName.contains("ellipse") || stName.contains("oval") || stName.contains("circle")) {
                        geometry = ShapeGeometryType.ELLIPSE
                    } else if (stName.contains("round") && stName.contains("rect")) {
                        geometry = ShapeGeometryType.ROUNDED_RECTANGLE
                    }
                }
            }

            // 2. XML SpPr
            val xml = getXmlObjectReflection(shape)
            if (xml != null) {
                val spPr = try { xml.javaClass.getMethod("getSpPr").invoke(xml) } catch (_: Throwable) { null }
                if (spPr != null) {
                    // Check prstGeom
                    val prstGeom = try { spPr.javaClass.getMethod("getPrstGeom").invoke(spPr) } catch (_: Throwable) { null }
                    if (prstGeom != null) {
                        val prst = try { prstGeom.javaClass.getMethod("getPrst").invoke(prstGeom)?.toString()?.lowercase() } catch (_: Throwable) { null }
                        if (prst != null) {
                            if (prst.contains("ellipse") || prst.contains("oval") || prst.contains("circle")) {
                                geometry = ShapeGeometryType.ELLIPSE
                            } else if (prst.contains("roundrect")) {
                                geometry = ShapeGeometryType.ROUNDED_RECTANGLE
                            }
                        }
                    }

                    // Check background fill: solidFill
                    val solidFill = try { spPr.javaClass.getMethod("getSolidFill").invoke(spPr) } catch (_: Throwable) { null }
                    if (solidFill != null) {
                        bgColor = extractColorFromSolidFill(solidFill)
                    }

                    // Check line outline: ln
                    val ln = try { spPr.javaClass.getMethod("getLn").invoke(spPr) } catch (_: Throwable) { null }
                    if (ln != null) {
                        val noFill = try { ln.javaClass.getMethod("getNoFill").invoke(ln) } catch (_: Throwable) { null }
                        val lnFill = try { ln.javaClass.getMethod("getSolidFill").invoke(ln) } catch (_: Throwable) { null }
                        val lnColor = if (lnFill != null) extractColorFromSolidFill(lnFill) else null
                        val lnW = extractLongAttr(ln, "getW")
                        val widthDp = if (lnW != null && lnW > 0) (lnW.toFloat() / 12700f).coerceIn(0.5f, 4f) else 0.75f
                        if (noFill == null && lnColor != null) {
                            border = ShapeBorder(strokeColorHex = lnColor, strokeWidthDp = widthDp)
                        } else if (geometry == ShapeGeometryType.ELLIPSE && noFill == null) {
                            val strokeHex = lnColor ?: resolveSchemeColor("tx1") ?: "#334155"
                            border = ShapeBorder(strokeColorHex = strokeHex, strokeWidthDp = 0.75f)
                        }
                    } else if (geometry == ShapeGeometryType.ELLIPSE) {
                        val strokeHex = resolveSchemeColor("tx1") ?: "#334155"
                        border = ShapeBorder(strokeColorHex = strokeHex, strokeWidthDp = 0.75f)
                    }
                }
            }
        } catch (_: Throwable) {}

        return Triple(geometry, border, bgColor)
    }

    private fun extractParagraphRunsWithLineBreaks(
        p: org.apache.poi.sl.usermodel.TextParagraph<*, *, *>,
        isTitle: Boolean
    ): List<PptxTextRun> {
        val runs = mutableListOf<PptxTextRun>()
        val pRuns: List<org.apache.poi.sl.usermodel.TextRun> = try { p.textRuns } catch (t: Throwable) { emptyList() }
        val pText = try { (p as? XSLFTextParagraph)?.text ?: pRuns.joinToString("") { it.rawText ?: "" } } catch (t: Throwable) { "" }

        // Check if paragraph XML contains <a:br> line breaks
        val xmlPara = getXmlObjectReflection(p)
        val domNode = try {
            val method = xmlPara?.javaClass?.getMethod("getDomNode")
            method?.invoke(xmlPara) as? org.w3c.dom.Node
        } catch (_: Throwable) { null }

        if (domNode != null) {
            val childNodes = domNode.childNodes
            var runIdx = 0
            for (i in 0 until childNodes.length) {
                val child = childNodes.item(i) ?: continue
                val nodeName = child.nodeName.lowercase()
                if (nodeName == "a:br" || nodeName == "br") {
                    if (runs.isNotEmpty()) {
                        val last = runs.removeAt(runs.size - 1)
                        runs.add(last.copy(text = last.text + "\n"))
                    } else {
                        runs.add(PptxTextRun(text = "\n", isBold = isTitle, fontSizePt = if (isTitle) 24f else 14f))
                    }
                } else if (nodeName == "a:r" || nodeName == "r") {
                    if (runIdx < pRuns.size) {
                        val r = pRuns[runIdx++]
                        var rText = try { r.rawText ?: "" } catch (t: Throwable) { "" }
                        if (rText.isBlank()) {
                            rText = try {
                                val method = r.javaClass.getMethod("getText")
                                val t = method.invoke(r) as? String
                                if (t != null && !t.startsWith("org.apache.poi") && !t.startsWith("org.apache.xmlbeans")) t else ""
                            } catch (t: Throwable) { "" }
                        }
                        if (rText.startsWith("org.apache.poi") || rText.startsWith("org.apache.xmlbeans") || (rText.startsWith("<") && rText.endsWith(">"))) {
                            continue
                        }
                        rText = cleanTextRunString(rText)
                        if (rText.isNotEmpty()) {
                            val isBold = try { r.isBold } catch (t: Throwable) { isTitle }
                            val isItalic = try { r.isItalic } catch (t: Throwable) { false }
                            val isUnderline = try { r.isUnderlined } catch (t: Throwable) { false }
                            val colorHex = extractTextRunColorHex(r)
                            val fSize: Double? = try { r.fontSize } catch (t: Throwable) { null }
                            val fontSizePt = if (fSize != null && fSize > 0.0) fSize.toFloat() else (if (isTitle) 24f else 14f)
                            val family = extractRunTypeface(r)
                            runs.add(PptxTextRun(rText, isBold, isItalic, isUnderline, colorHex, fontSizePt, family))
                        }
                    }
                }
            }
        }

        // If runs is still empty, parse from pRuns directly
        if (runs.isEmpty()) {
            for (r in pRuns) {
                var rText = try { r.rawText ?: "" } catch (t: Throwable) { "" }
                if (rText.isBlank()) {
                    rText = try {
                        val method = r.javaClass.getMethod("getText")
                        val t = method.invoke(r) as? String
                        if (t != null && !t.startsWith("org.apache.poi") && !t.startsWith("org.apache.xmlbeans")) t else ""
                    } catch (t: Throwable) { "" }
                }
                if (rText.startsWith("org.apache.poi") || rText.startsWith("org.apache.xmlbeans") || (rText.startsWith("<") && rText.endsWith(">"))) {
                    continue
                }
                rText = cleanTextRunString(rText)
                if (rText.isNotEmpty()) {
                    val isBold = try { r.isBold } catch (t: Throwable) { isTitle }
                    val isItalic = try { r.isItalic } catch (t: Throwable) { false }
                    val isUnderline = try { r.isUnderlined } catch (t: Throwable) { false }
                    val colorHex = extractTextRunColorHex(r)
                    val fSize: Double? = try { r.fontSize } catch (t: Throwable) { null }
                    val fontSizePt = if (fSize != null && fSize > 0.0) fSize.toFloat() else (if (isTitle) 24f else 14f)
                    val family = extractRunTypeface(r)
                    runs.add(PptxTextRun(rText, isBold, isItalic, isUnderline, colorHex, fontSizePt, family))
                }
            }
        }

        // Final fallback if runs is empty but paragraph text is non-empty
        if (runs.isEmpty()) {
            val cleaned = cleanTextRunString(pText)
            if (cleaned.isNotBlank() &&
                !cleaned.startsWith("org.apache.poi") &&
                !cleaned.startsWith("org.apache.xmlbeans") &&
                !(cleaned.startsWith("<") && cleaned.endsWith(">"))
            ) {
                runs.add(PptxTextRun(text = cleaned, isBold = isTitle, fontSizePt = if (isTitle) 24f else 14f))
            }
        }

        return runs
    }

    private fun setRunProperties(r: XSLFTextRun, textColorHex: String?, fontSizePt: Float) {
        try {
            r.fontSize = fontSizePt.toDouble()
        } catch (t: Throwable) {}

        val xmlRun = getXmlObjectReflection(r) ?: return
        try {
            val rPr = try {
                xmlRun.javaClass.getMethod("getRPr").invoke(xmlRun)
                    ?: xmlRun.javaClass.getMethod("addNewRPr").invoke(xmlRun)
            } catch (t: Throwable) {
                try { xmlRun.javaClass.getMethod("addNewRPr").invoke(xmlRun) } catch (t2: Throwable) { null }
            }

            if (rPr != null) {
                if (textColorHex != null) {
                    try { rPr.javaClass.getMethod("unsetSolidFill").invoke(rPr) } catch (t: Throwable) {}
                    val solidFill = rPr.javaClass.getMethod("addNewSolidFill").invoke(rPr)
                    val srgbClr = solidFill.javaClass.getMethod("addNewSrgbClr").invoke(solidFill)
                    val color = android.graphics.Color.parseColor(textColorHex)
                    val rgbBytes = byteArrayOf(
                        ((color shr 16) and 0xFF).toByte(),
                        ((color shr 8) and 0xFF).toByte(),
                        (color and 0xFF).toByte()
                    )
                    srgbClr.javaClass.getMethod("setVal", ByteArray::class.java).invoke(srgbClr, rgbBytes)
                } else {
                    try { rPr.javaClass.getMethod("unsetSolidFill").invoke(rPr) } catch (t: Throwable) {}
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private fun setSlideBgColorHex(slide: org.apache.poi.sl.usermodel.Slide<*, *>, colorHex: String) {
        if (slide is XSLFSlide) {
            try {
                val color = android.graphics.Color.parseColor(colorHex)
                val rgbBytes = byteArrayOf(
                    ((color shr 16) and 0xFF).toByte(),
                    ((color shr 8) and 0xFF).toByte(),
                    (color and 0xFF).toByte()
                )
                val ctSlide = getXmlObjectReflection(slide)
                if (ctSlide != null) {
                    val cSld = try {
                        ctSlide.javaClass.getMethod("getCSld").invoke(ctSlide)
                            ?: ctSlide.javaClass.getMethod("addNewCSld").invoke(ctSlide)
                    } catch (t: Throwable) {
                        try { ctSlide.javaClass.getMethod("addNewCSld").invoke(ctSlide) } catch (t2: Throwable) { null }
                    }
                    if (cSld != null) {
                        val bg = try {
                            cSld.javaClass.getMethod("getBg").invoke(cSld)
                                ?: cSld.javaClass.getMethod("addNewBg").invoke(cSld)
                        } catch (t: Throwable) {
                            try { cSld.javaClass.getMethod("addNewBg").invoke(cSld) } catch (t2: Throwable) { null }
                        }
                        if (bg != null) {
                            try {
                                val isSetBgPr = bg.javaClass.getMethod("isSetBgPr").invoke(bg) as Boolean
                                if (isSetBgPr) {
                                    val bgPr = bg.javaClass.getMethod("getBgPr").invoke(bg)
                                    if (bgPr != null) {
                                        val unsetMethods = listOf("unsetSolidFill", "unsetGradFill", "unsetBlipFill", "unsetPattFill", "unsetGrpFill")
                                        unsetMethods.forEach { m ->
                                            try { bgPr.javaClass.getMethod(m).invoke(bgPr) } catch (t: Throwable) {}
                                        }
                                    }
                                }
                            } catch (t: Throwable) {}
                            val bgPr = try {
                                bg.javaClass.getMethod("getBgPr").invoke(bg)
                                    ?: bg.javaClass.getMethod("addNewBgPr").invoke(bg)
                            } catch (t: Throwable) {
                                try { bg.javaClass.getMethod("addNewBgPr").invoke(bg) } catch (t2: Throwable) { null }
                            }
                            if (bgPr != null) {
                                val solidFill = bgPr.javaClass.getMethod("addNewSolidFill").invoke(bgPr)
                                val srgbClr = solidFill.javaClass.getMethod("addNewSrgbClr").invoke(solidFill)
                                srgbClr.javaClass.getMethod("setVal", ByteArray::class.java).invoke(srgbClr, rgbBytes)
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    /**
     * Extracts slide dimensions in EMUs without calling ppt.getPageSize() (which returns java.awt.Dimension).
     */
    private fun getSlideDimensionsEmu(ppt: org.apache.poi.sl.usermodel.SlideShow<*, *>): Pair<Long, Long> {
        if (ppt is XMLSlideShow) {
            try {
                val ctPresentation = getXmlObjectReflection(ppt)
                    ?: try { ppt.javaClass.getMethod("getCTPresentation").invoke(ppt) } catch (t: Throwable) { null }
                if (ctPresentation != null) {
                    val sldSz = ctPresentation.javaClass.getMethod("getSldSz").invoke(ctPresentation)
                    if (sldSz != null) {
                        val cx = (sldSz.javaClass.getMethod("getCx").invoke(sldSz) as? Number)?.toLong() ?: 9144000L
                        val cy = (sldSz.javaClass.getMethod("getCy").invoke(sldSz) as? Number)?.toLong() ?: 5143500L
                        return Pair(cx, cy)
                    }
                }
            } catch (t: Throwable) {
                // Fall back to standard 16:9 widescreen in EMUs (10 inches x 5.625 inches)
            }
        }
        // Default: 9144000 x 5143500 (10 x 5.625 in = 16:9 standard PPTX)
        return Pair(9144000L, 5143500L)
    }

    private fun parseAllSlides(ppt: org.apache.poi.sl.usermodel.SlideShow<*, *>): List<PptxSlide> {
        val (slideWidthEmu, slideHeightEmu) = getSlideDimensionsEmu(ppt)
        val slideAspectRatio = if (slideHeightEmu > 0) slideWidthEmu.toFloat() / slideHeightEmu.toFloat() else (16f / 9f)
        val slideWidthPt = if (slideWidthEmu > 0) (slideWidthEmu / 12700f) else 960f
        val slideHeightPt = if (slideHeightEmu > 0) (slideHeightEmu / 12700f) else 540f
        val slides = mutableListOf<PptxSlide>()

        getThemeColors().clear()
        if (ppt.slides.isNotEmpty()) {
            getThemeColors().putAll(extractThemeColorScheme(ppt.slides[0]))
        }

        for ((index, slide) in ppt.slides.withIndex()) {
            val textShapes = mutableListOf<PptxTextShape>()
            val images = mutableListOf<PptxImage>()
            var backgroundImage: PptxImage? = null
            val bgColorHex = getSlideBgColorHex(slide)

            // Extract Slide Background Picture if present
            try {
                if (slide is XSLFSlide) {
                    val ctSlide = getXmlObjectReflection(slide)
                    if (ctSlide != null) {
                        val cSld = try { ctSlide.javaClass.getMethod("getCSld").invoke(ctSlide) } catch (_: Throwable) { null }
                        val bg = try { cSld?.javaClass?.getMethod("getBg")?.invoke(cSld) } catch (_: Throwable) { null }
                        if (bg != null) {
                            val bgBlipId = extractBlipEmbedId(bg)
                            if (!bgBlipId.isNullOrBlank()) {
                                val resolved = resolvePictureBytesFromBlipId(slide, bgBlipId)
                                if (resolved != null) {
                                    val bgFile = savePicBytesToTempFile(index, resolved.first, resolved.second)
                                    backgroundImage = PptxImage(bgFile.absolutePath, 0f, 0f, 1f, 1f)
                                }
                            }
                        }
                    }
                }
            } catch (_: Throwable) { }

            // Extract Speaker Notes safely
            val speakerNotes: String? = try {
                val notesObj = try { slide.notes } catch (t: Throwable) { null }
                if (notesObj != null) {
                    val shapes = try { notesObj.shapes } catch (t: Throwable) { emptyList() }
                    var noteText: String? = null
                    for (sh in shapes) {
                        if (sh is org.apache.poi.sl.usermodel.TextShape<*, *>) {
                            val t = try { sh.text } catch (t2: Throwable) { null }
                            if (!t.isNullOrBlank()) {
                                noteText = t
                                break
                            }
                        }
                    }
                    noteText
                } else null
            } catch (t: Throwable) {
                null
            }

            var titleShape: PptxTextShape? = null
            var bodyCount = 0

            // Helper function to recursively flatten group shapes and collect all shapes
            val allShapes = mutableListOf<org.apache.poi.sl.usermodel.Shape<*, *>>()
            fun collectShapes(shapeList: List<org.apache.poi.sl.usermodel.Shape<*, *>>, isMasterOrLayout: Boolean = false) {
                for (sh in shapeList) {
                    if (isMasterOrLayout && sh is org.apache.poi.sl.usermodel.SimpleShape<*, *> && sh.isPlaceholder) {
                        // Skip master/layout placeholders so placeholder prompt text doesn't render
                        continue
                    }
                    if (sh is org.apache.poi.sl.usermodel.GroupShape<*, *>) {
                        val nested = try { sh.shapes } catch (t: Throwable) { emptyList() }
                        collectShapes(nested, isMasterOrLayout)
                    } else {
                        allShapes.add(sh)
                    }
                }
            }
            val showMaster = try {
                if (slide is XSLFSlide) {
                    (slide.javaClass.getMethod("getDisplayMasterShapes").invoke(slide) as? Boolean) ?: true
                } else true
            } catch (_: Throwable) { true }
            if (showMaster && slide is XSLFSlide) {
                try {
                    val masterShapes = slide.slideLayout?.slideMaster?.shapes ?: emptyList()
                    collectShapes(masterShapes, isMasterOrLayout = true)
                } catch (_: Throwable) { }
                try {
                    val layoutShapes = slide.slideLayout?.shapes ?: emptyList()
                    collectShapes(layoutShapes, isMasterOrLayout = true)
                } catch (_: Throwable) { }
            }
            val rootShapes = try { slide.shapes } catch (t: Throwable) { emptyList() }
            collectShapes(rootShapes, isMasterOrLayout = false)

            // Z-order counter: shapes are processed in document order, which matches z-order
            // Use the index in allShapes as the z-order to ensure correct layering
            for ((shapeIndex, shape) in allShapes.withIndex()) {
                val shapeZOrder = shapeIndex
                try {
                    // 1. Check if shape has picture data (PictureShape, blipFill on AutoShape/SimpleShape, etc.)
                    val picTriple = extractPictureDataFromShape(shape, slide)
                    if (picTriple != null && picTriple.first.isNotEmpty()) {
                        val file = savePicBytesToTempFile(index, picTriple.first, picTriple.second, picTriple.third)
                        val bounds = getShapeNormalizedBounds(shape, slide, slideWidthEmu, slideHeightEmu)
                        val left = bounds?.get(0) ?: 0.05f
                        val top = bounds?.get(1) ?: 0.3f
                        val width = bounds?.get(2) ?: 0.6f
                        val height = bounds?.get(3) ?: 0.4f

                        val imageArea = width * height
                        val isBackground = imageArea >= 0.85f && left <= 0.05f && top <= 0.05f

                        val imgZOrder = shapeZOrder
                        if (isBackground && backgroundImage == null) {
                            backgroundImage = PptxImage(file.absolutePath, 0f, 0f, 1f, 1f, imgZOrder)
                        } else {
                            images.add(PptxImage(file.absolutePath, left, top, width, height, imgZOrder))
                        }
                    }

                    // 2. Process text shape if present
                    if (shape is org.apache.poi.sl.usermodel.TextShape<*, *>) {
                        val shapeText = try { shape.text ?: "" } catch (t: Throwable) { "" }
                        if (shapeText.isNotBlank()) {
                            val isTitle = try {
                                shape.placeholder == Placeholder.TITLE || shape.placeholder == Placeholder.CENTERED_TITLE
                            } catch (t: Throwable) {
                                shape.shapeName.lowercase().contains("title")
                            }

                            val bounds = getShapeNormalizedBounds(shape, slide, slideWidthEmu, slideHeightEmu)
                            val shapeLeft = bounds?.get(0) ?: 0.05f
                            val shapeTop = bounds?.get(1) ?: (if (isTitle) 0.05f else (0.22f + bodyCount * 0.12f).coerceAtMost(0.85f))
                            val shapeWidthVal = bounds?.get(2) ?: 0.9f
                            val shapeHeightVal = bounds?.get(3) ?: (if (isTitle) 0.15f else 0.35f)

                            val paragraphs = try { shape.textParagraphs } catch (t: Throwable) { emptyList() }
                            val shapeParagraphs = mutableListOf<PptxParagraph>()

                            if (paragraphs.isNotEmpty()) {
                                for (p in paragraphs) {
                                    val paragraphRuns = extractParagraphRunsWithLineBreaks(p, isTitle)

                                    if (paragraphRuns.isNotEmpty()) {
                                        val bulletLevel = try { p.indentLevel } catch (t: Throwable) { 0 }
                                        val rawBulletChar = try {
                                            if (p is XSLFTextParagraph) p.bulletCharacter
                                            else (p.javaClass.getMethod("getBulletCharacter").invoke(p) as? String)
                                        } catch (_: Throwable) { null }

                                        val defaultFontSize = if (isTitle) 26f else 14f
                                        val refFontPt = paragraphRuns.maxOfOrNull {
                                            if (it.fontSizePt > 0) it.fontSizePt else defaultFontSize
                                        } ?: defaultFontSize

                                        val pPr = try {
                                            val xmlPara = getXmlObjectReflection(p)
                                            if (xmlPara != null) {
                                                try { xmlPara.javaClass.getMethod("getPPr").invoke(xmlPara) } catch (_: Throwable) { null }
                                            } else null
                                        } catch (_: Throwable) { null }

                                        val hasBulletFromXml = if (rawBulletChar.isNullOrBlank() && bulletLevel == 0) {
                                            try {
                                                if (pPr != null) {
                                                    val autoNumScheme = try {
                                                        pPr.javaClass.getMethod("getBuAutoNum").invoke(pPr)
                                                    } catch (_: Throwable) { null }
                                                    val buChar = try {
                                                        pPr.javaClass.getMethod("getBuChar").invoke(pPr)
                                                    } catch (_: Throwable) { null }
                                                    autoNumScheme != null || buChar != null
                                                } else false
                                            } catch (_: Throwable) { false }
                                        } else false

                                        val hasBullet = bulletLevel > 0 || !rawBulletChar.isNullOrBlank() || hasBulletFromXml
                                        val numberingType = extractNumberingType(pPr)
                                        val bulletChar = when {
                                            numberingType != null -> ""
                                            rawBulletChar.isNullOrBlank() -> if (hasBullet) "•" else ""
                                            rawBulletChar in listOf("•", "○", "▪", "▫", "-", "–", "—", ">", "→") -> rawBulletChar
                                            rawBulletChar.contains("\uF0A7") || rawBulletChar.contains("\uF0B7") || rawBulletChar.contains("\uF06C") -> "•"
                                            else -> "•"
                                        }
                                        val alignment = try {
                                            when (p.textAlign) {
                                                org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER -> "CENTER"
                                                org.apache.poi.sl.usermodel.TextParagraph.TextAlign.RIGHT -> "RIGHT"
                                                org.apache.poi.sl.usermodel.TextParagraph.TextAlign.JUSTIFY -> "JUSTIFY"
                                                org.apache.poi.sl.usermodel.TextParagraph.TextAlign.LEFT -> if (isTitle && shapeWidthVal >= 0.4f) "CENTER" else "LEFT"
                                                else -> if (isTitle && shapeWidthVal >= 0.4f) "CENTER" else "LEFT"
                                            }
                                        } catch (t: Throwable) { if (isTitle && shapeWidthVal >= 0.4f) "CENTER" else "LEFT" }

                                         val spaceBefore = try { (p.spaceBefore ?: 0.0).toFloat().coerceAtLeast(0f) } catch (t: Throwable) { 0f }
                                         val spaceAfter = try { (p.spaceAfter ?: 0.0).toFloat().coerceAtLeast(0f) } catch (t: Throwable) { 0f }
                                         val lineSpacingMul = extractLineSpacingMul(pPr, refFontPt)

                                         shapeParagraphs.add(
                                             PptxParagraph(
                                                 runs = paragraphRuns,
                                                 bulletLevel = bulletLevel,
                                                 hasBullet = hasBullet,
                                                 bulletChar = bulletChar,
                                                 alignment = alignment,
                                                 spaceBeforePt = spaceBefore,
                                                 spaceAfterPt = spaceAfter,
                                                 lineSpacingMul = lineSpacingMul,
                                                 numberingType = numberingType
                                             )
                                         )
                                     }
                                 }
                             }

                            // If no paragraphs parsed but shape has text
                            if (shapeParagraphs.isEmpty() &&
                                !shapeText.startsWith("org.apache.poi") &&
                                !shapeText.startsWith("org.apache.xmlbeans") &&
                                !(shapeText.startsWith("<") && shapeText.endsWith(">"))
                            ) {
                                shapeParagraphs.add(
                                    PptxParagraph(
                                        runs = listOf(
                                            PptxTextRun(
                                                text = cleanTextRunString(shapeText).trim(),
                                                isBold = isTitle,
                                                fontSizePt = if (isTitle) 24f else 14f
                                            )
                                        ),
                                        bulletLevel = 0,
                                        hasBullet = false,
                                        bulletChar = "",
                                        alignment = "LEFT"
                                    )
                                )
                            }

                            if (shapeParagraphs.isNotEmpty()) {
                                val isDistinctTitle = isTitle && titleShape == null
                                val textZOrder = shapeZOrder
                                val bodyPr = extractBodyPr(shape, slideWidthEmu, slideHeightEmu)
                                val (shapeGeom, shapeBorder, shapeBg) = extractShapeGeometryAndBorder(shape)
                                val clampedWidth = shapeWidthVal.coerceAtMost((1f - shapeLeft).coerceAtLeast(0.05f))
                                val parsedShape = PptxTextShape(
                                    id = if (isDistinctTitle) "title" else "body_$bodyCount",
                                    isTitle = isTitle,
                                    paragraphs = shapeParagraphs,
                                    shapeGeometry = shapeGeom,
                                    shapeBorder = shapeBorder,
                                    shapeLeft = shapeLeft,
                                    shapeTop = shapeTop,
                                    shapeWidth = clampedWidth,
                                    shapeHeight = shapeHeightVal,
                                    backgroundColorHex = shapeBg,
                                    zOrder = textZOrder,
                                    insets = bodyPr.insets,
                                    autoFit = bodyPr.autoFit,
                                    fontScale = bodyPr.fontScale,
                                    lnSpcReduction = bodyPr.lnSpcReduction
                                )

                                if (isDistinctTitle) {
                                    titleShape = parsedShape
                                } else {
                                    textShapes.add(parsedShape)
                                    bodyCount++
                                }
                            }
                        }
                    } else if (shape is org.apache.poi.sl.usermodel.TableShape<*, *>) {
                        val numRows = try { shape.numberOfRows } catch (t: Throwable) { 0 }
                        val numCols = try { shape.numberOfColumns } catch (t: Throwable) { 0 }
                        val tableBounds = getShapeNormalizedBounds(shape, slide, slideWidthEmu, slideHeightEmu)
                        val tLeft = tableBounds?.get(0) ?: 0.05f
                        val tTop = tableBounds?.get(1) ?: 0.35f
                        val tWidth = tableBounds?.get(2) ?: 0.9f
                        val tHeight = tableBounds?.get(3) ?: 0.5f

                        val tableGeo = extractTableGeometry(shape, slideWidthEmu, slideHeightEmu)

                        val colWidths = tableGeo?.gridColWidthsEmu?.takeIf { it.size == numCols }
                            ?: List(numCols) { (slideWidthEmu / numCols) }
                        val totalGridW = colWidths.sum().toFloat().coerceAtLeast(1f)
                        val colOffset = FloatArray(numCols + 1)
                        for (c in 0 until numCols) {
                            colOffset[c + 1] = colOffset[c] + colWidths[c].toFloat() / totalGridW
                        }

                        val rowHeights = tableGeo?.rows?.map { it.heightEmu }?.takeIf { it.size == numRows }
                            ?: List(numRows) { (slideHeightEmu / numRows) }
                        val totalGridH = rowHeights.sum().toFloat().coerceAtLeast(1f)
                        val rowOffset = FloatArray(numRows + 1)
                        for (r in 0 until numRows) {
                            rowOffset[r + 1] = rowOffset[r] + rowHeights[r].toFloat() / totalGridH
                        }

                        for (r in 0 until numRows) {
                            for (c in 0 until numCols) {
                                val cell = try { shape.getCell(r, c) } catch (t: Throwable) { null } ?: continue
                                val text = try { cell.text ?: "" } catch (t: Throwable) { "" }
                                if (text.isNotBlank()) {
                                    val cellLeft = (tLeft + colOffset[c] * tWidth).coerceIn(0f, 1f)
                                    val cellTop = (tTop + rowOffset[r] * tHeight).coerceIn(0f, 1f)
                                    val cellW = ((colOffset[c + 1] - colOffset[c]) * tWidth).coerceIn(0.05f, 1f)
                                    val cellH = ((rowOffset[r + 1] - rowOffset[r]) * tHeight).coerceIn(0.05f, 1f)

                                    val cellParas = try { cell.textParagraphs } catch (_: Throwable) { emptyList() }
                                    val cellShapeParas = mutableListOf<PptxParagraph>()
                                    
                                    if (cellParas.isNotEmpty()) {
                                        for (cp in cellParas) {
                                            val cpRuns = try { cp.textRuns } catch (_: Throwable) { emptyList() }
                                            val cRuns = mutableListOf<PptxTextRun>()
                                            for (cr in cpRuns) {
                                                val crText = cleanTextRunString(try { cr.rawText ?: "" } catch (_: Throwable) { "" })
                                                if (crText.isNotEmpty()) {
                                                    val isB = try { cr.isBold } catch (_: Throwable) { false }
                                                    val isI = try { cr.isItalic } catch (_: Throwable) { false }
                                                    val isU = try { cr.isUnderlined } catch (_: Throwable) { false }
                                                    val cHex = extractTextRunColorHex(cr)
                                                    val fS = try { cr.fontSize?.toFloat() } catch (_: Throwable) { null } ?: 14f
                                                    cRuns.add(PptxTextRun(crText, isB, isI, isU, cHex, fS))
                                                }
                                            }
                                            if (cRuns.isNotEmpty()) {
                                                cellShapeParas.add(
                                                    PptxParagraph(
                                                        runs = cRuns,
                                                        bulletLevel = cp.indentLevel,
                                                        hasBullet = cp.indentLevel > 0,
                                                        bulletChar = if (cp.indentLevel > 0) "•" else "",
                                                        alignment = "LEFT"
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    
                                    if (cellShapeParas.isEmpty()) {
                                        val firstParagraph = try { cell.textParagraphs.firstOrNull() } catch (t: Throwable) { null }
                                        val firstRun = try { firstParagraph?.textRuns?.firstOrNull() } catch (t: Throwable) { null }
                                        val isBold = try { firstRun?.isBold ?: false } catch (t: Throwable) { false }
                                        val isItalic = try { firstRun?.isItalic ?: false } catch (t: Throwable) { false }
                                        val isUnderline = try { firstRun?.isUnderlined ?: false } catch (t: Throwable) { false }
                                        val colorHex = firstRun?.let { extractTextRunColorHex(it) }
                                        val fSize = try { firstRun?.fontSize } catch (t: Throwable) { null }
                                        val fontSizePt = if (fSize != null && fSize > 0) fSize.toFloat() else 14f
                                        cellShapeParas.add(
                                            PptxParagraph(
                                                runs = listOf(
                                                    PptxTextRun(
                                                        text = text.trim(),
                                                        isBold = isBold,
                                                        isItalic = isItalic,
                                                        isUnderline = isUnderline,
                                                        textColorHex = colorHex,
                                                        fontSizePt = fontSizePt
                                                    )
                                                ),
                                                bulletLevel = 0,
                                                hasBullet = false,
                                                bulletChar = "",
                                                alignment = "LEFT"
                                            )
                                        )
                                    }

                                    val cellMargins = tableGeo?.rows?.getOrNull(r)?.cells?.getOrNull(c)
                                        ?: CellMargins(
                                            left = 45720f / (if (slideWidthEmu > 0) slideWidthEmu.toFloat() else 9144000f),
                                            top = 45720f / (if (slideHeightEmu > 0) slideHeightEmu.toFloat() else 5143500f),
                                            right = 45720f / (if (slideWidthEmu > 0) slideWidthEmu.toFloat() else 9144000f),
                                            bottom = 45720f / (if (slideHeightEmu > 0) slideHeightEmu.toFloat() else 5143500f)
                                        )

                                    textShapes.add(
                                        PptxTextShape(
                                            id = "table_cell_${r}_${c}",
                                            isTitle = false,
                                            paragraphs = cellShapeParas,
                                            shapeGeometry = ShapeGeometryType.RECTANGLE,
                                            shapeBorder = ShapeBorder(strokeColorHex = "#CBD5E1", strokeWidthDp = 1f),
                                            shapeLeft = cellLeft,
                                            shapeTop = cellTop,
                                            shapeWidth = cellW,
                                            shapeHeight = cellH,
                                            zOrder = shapeZOrder,
                                            insets = Insets(
                                                left = cellMargins.left,
                                                top = cellMargins.top,
                                                right = cellMargins.right,
                                                bottom = cellMargins.bottom
                                            )
                                        )
                                    )
                                    bodyCount++
                                }
                            }
                        }
                    }
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }

            val finalTitle = titleShape ?: textShapes.firstOrNull { it.isTitle } ?: PptxTextShape(
                id = "empty_title",
                isTitle = false,
                paragraphs = emptyList(),
                shapeLeft = 0f,
                shapeTop = 0f,
                shapeWidth = 0f,
                shapeHeight = 0f
            )

            slides.add(
                PptxSlide(
                    slideNumber = index + 1,
                    title = finalTitle,
                    textShapes = textShapes,
                    images = images,
                    backgroundImage = backgroundImage,
                    speakerNotes = speakerNotes,
                    bgColorHex = bgColorHex,
                    aspectRatio = slideAspectRatio,
                    slideWidthPt = slideWidthPt,
                    slideHeightPt = slideHeightPt
                )
            )
        }
        return slides
    }

    fun loadPptxFile(filePath: String) {
        android.util.Log.d("PptxViewModel", "loadPptxFile called with: $filePath")
        viewModelScope.launch {
            _loadState.value = PptxLoadState.Loading
            withContext(Dispatchers.IO) {
                try {
                    activePresentation?.close()
                } catch (t: Throwable) {}
                activePresentation = null
                activeFilePath = null
                undoStack.clear()
                redoStack.clear()
                _canUndo.value = false
                _canRedo.value = false

                var fileInputStream: FileInputStream? = null
                var ppt: org.apache.poi.sl.usermodel.SlideShow<*, *>? = null
                try {
                    val file = File(filePath)
                    if (!file.exists() || !file.isFile) {
                        android.util.Log.e("PptxViewModel", "File does not exist: $filePath")
                        _loadState.value = PptxLoadState.Error("Target presentation does not exist or is corrupted.")
                        return@withContext
                    }

                    android.util.Log.d("PptxViewModel", "File exists, size: ${file.length()} bytes")
                    fileInputStream = FileInputStream(file)
                    ppt = if (filePath.endsWith(".ppt", ignoreCase = true)) {
                        org.apache.poi.hslf.usermodel.HSLFSlideShow(fileInputStream)
                    } else {
                        XMLSlideShow(fileInputStream)
                    }

                    android.util.Log.d("PptxViewModel", "Parsed PPTX, slides: ${ppt.slides.size}")
                    val slides = parseAllSlides(ppt)
                    android.util.Log.d("PptxViewModel", "Parsed slides: ${slides.size}")

                    activePresentation = ppt
                    activeFilePath = filePath

                    // Emit Success immediately so presentation opens without waiting for all bitmaps to render
                    _loadState.value = PptxLoadState.Success(
                        presentation = PptxPresentation(slides),
                        fileName = file.name,
                        slideBitmaps = emptyList()
                    )

                    // Render slides to bitmaps progressively in background
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            android.util.Log.d("PptxViewModel", "Rendering bitmaps in background...")
                            val result = officeConverter.renderPptxToBitmaps(file)
                            android.util.Log.d("PptxViewModel", "Rendered ${result.size} bitmaps")
                            val cur = _loadState.value
                            if (cur is PptxLoadState.Success) {
                                _loadState.value = cur.copy(slideBitmaps = result)
                            }
                        } catch (e: Throwable) {
                            android.util.Log.e("PptxViewModel", "Bitmap background rendering failed: ${e.message}")
                        }
                    }

                } catch (e: Throwable) {
                    e.printStackTrace()
                    _loadState.value = PptxLoadState.Error("PowerPoint presentation parser failure: ${e.localizedMessage}")
                } finally {
                    try {
                        fileInputStream?.close()
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }
            }
        }
    }

    private fun takeSnapshotBytes(ppt: org.apache.poi.sl.usermodel.SlideShow<*, *>): ByteArray? {
        return try {
            val bos = java.io.ByteArrayOutputStream()
            ppt.write(bos)
            bos.toByteArray()
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }

    private fun pushUndoState() {
        val ppt = activePresentation ?: return
        val bytes = takeSnapshotBytes(ppt) ?: return
        if (undoStack.size >= 30) {
            undoStack.removeFirst()
        }
        undoStack.addLast(bytes)
        redoStack.clear()
        _canUndo.value = true
        _canRedo.value = false
    }

    private fun restoreSnapshot(snapshotBytes: ByteArray) {
        viewModelScope.launch {
            _loadState.value = PptxLoadState.Loading
            withContext(Dispatchers.IO) {
                try {
                    val bis = java.io.ByteArrayInputStream(snapshotBytes)
                    val newPpt = XMLSlideShow(bis)
                    try {
                        activePresentation?.close()
                    } catch (_: Throwable) {}
                    activePresentation = newPpt
                    val slides = parseAllSlides(newPpt)
                    _loadState.value = PptxLoadState.Success(
                        presentation = PptxPresentation(slides),
                        fileName = activeFilePath?.let { File(it).name } ?: "presentation.pptx"
                    )
                } catch (t: Throwable) {
                    t.printStackTrace()
                    _saveStatus.emit("Failed to restore history snapshot: ${t.localizedMessage}")
                }
            }
        }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val ppt = activePresentation ?: return
        val currentBytes = takeSnapshotBytes(ppt) ?: return
        val previousBytes = undoStack.removeLast()
        if (redoStack.size >= 30) {
            redoStack.removeFirst()
        }
        redoStack.addLast(currentBytes)
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = true
        restoreSnapshot(previousBytes)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val ppt = activePresentation ?: return
        val currentBytes = takeSnapshotBytes(ppt) ?: return
        val nextBytes = redoStack.removeLast()
        if (undoStack.size >= 30) {
            undoStack.removeFirst()
        }
        undoStack.addLast(currentBytes)
        _canUndo.value = true
        _canRedo.value = redoStack.isNotEmpty()
        restoreSnapshot(nextBytes)
    }

    fun moveSlide(fromIndex: Int, toIndex: Int) {
        if (activePresentation is org.apache.poi.hslf.usermodel.HSLFSlideShow) {
            viewModelScope.launch {
                _saveStatus.emit("Editing is not supported for legacy PowerPoint (.ppt) documents. Please save as .pptx format to edit.")
            }
            return
        }
        val ppt = activePresentation as? XMLSlideShow ?: return
        val slides = ppt.slides
        if (fromIndex !in slides.indices || toIndex !in slides.indices || fromIndex == toIndex) return

        pushUndoState()
        try {
            val slide = slides[fromIndex]
            ppt.setSlideOrder(slide, toIndex)
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        viewModelScope.launch {
            _loadState.value = PptxLoadState.Success(
                presentation = PptxPresentation(parseAllSlides(ppt)),
                fileName = File(activeFilePath!!).name
            )
        }
    }

    fun updateSlideTextShape(
        slideIndex: Int,
        isTitle: Boolean,
        blockIndex: Int,
        newText: String,
        isBold: Boolean = false,
        isItalic: Boolean = false,
        isUnderline: Boolean = false,
        textColorHex: String? = null,
        comment: String? = null,
        fontSizePt: Float = 18f
    ) {
        if (activePresentation is org.apache.poi.hslf.usermodel.HSLFSlideShow) {
            viewModelScope.launch {
                _saveStatus.emit("Editing is not supported for legacy PowerPoint (.ppt) documents. Please save as .pptx format to edit.")
            }
            return
        }
        val ppt = activePresentation as? XMLSlideShow ?: return
        val slides = ppt.slides
        if (slideIndex in slides.indices) {
            pushUndoState()
            val slide = slides[slideIndex]
            var blockIdx = 0

            for (shape in slide.shapes) {
                if (shape is XSLFTextShape) {
                    val isShapeTitle = shape.isPlaceholder && (shape.textType == Placeholder.TITLE || shape.textType == Placeholder.CENTERED_TITLE)
                    val targetMatch = if (isTitle) isShapeTitle else (!isShapeTitle && blockIdx == blockIndex)

                    if (targetMatch) {
                        val paras = shape.textParagraphs
                        if (paras.isNotEmpty()) {
                            val p = paras[0]
                            while (p.textRuns.size > 1) {
                                try {
                                    val rXml = getXmlObjectReflection(p.textRuns[1])
                                    val pXml = getXmlObjectReflection(p)
                                    val rListObj = pXml?.javaClass?.getMethod("getRList")?.invoke(pXml) as? MutableList<*>
                                    rListObj?.remove(rXml)
                                } catch (t: Throwable) {}
                            }

                            val r = p.textRuns.firstOrNull() ?: p.addNewTextRun()
                            r.setText(newText)
                            r.isBold = isBold
                            r.isItalic = isItalic
                            r.isUnderlined = isUnderline
                            setRunProperties(r, textColorHex, fontSizePt)

                            while (shape.textParagraphs.size > 1) {
                                try {
                                    val paraXml = getXmlObjectReflection(shape.textParagraphs[1])
                                    val shapeXml = getXmlObjectReflection(shape)
                                    val txBody = shapeXml?.javaClass?.getMethod("getTxBody")?.invoke(shapeXml)
                                    val pListObj = txBody?.javaClass?.getMethod("getPList")?.invoke(txBody) as? MutableList<*>
                                    pListObj?.remove(paraXml)
                                } catch (t: Throwable) {}
                            }
                        } else {
                            val p = shape.addNewTextParagraph()
                            val r = p.addNewTextRun()
                            r.setText(newText)
                            r.isBold = isBold
                            r.isItalic = isItalic
                            r.isUnderlined = isUnderline
                            setRunProperties(r, textColorHex, fontSizePt)
                        }
                        break
                    }
                    if (!isShapeTitle) {
                        blockIdx++
                    }
                }
            }

            if (comment != null) {
                try {
                    val notesObj = try {
                        slide.javaClass.getMethod("getNotes").invoke(slide)
                            ?: slide.javaClass.getMethod("createNotes").invoke(slide)
                    } catch (t: Throwable) {
                        try { slide.javaClass.getMethod("createNotes").invoke(slide) } catch (t2: Throwable) { null }
                    }

                    if (notesObj != null) {
                        val shapesList = notesObj.javaClass.getMethod("getShapes").invoke(notesObj) as? List<*>
                        var notesShape = shapesList?.filterIsInstance<XSLFTextShape>()?.firstOrNull {
                            it.placeholder == Placeholder.BODY
                        } ?: shapesList?.filterIsInstance<XSLFTextShape>()?.firstOrNull()

                        if (notesShape == null) {
                            notesShape = notesObj.javaClass.getMethod("createTextBox").invoke(notesObj) as? XSLFTextShape
                            try {
                                notesShape?.javaClass?.getMethod("setPlaceholder", Placeholder::class.java)?.invoke(notesShape, Placeholder.BODY)
                            } catch (t: Throwable) {}
                        }
                        notesShape?.clearText()
                        notesShape?.setText(comment)
                    }
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }

            viewModelScope.launch {
                _loadState.value = PptxLoadState.Success(
                    presentation = PptxPresentation(parseAllSlides(ppt)),
                    fileName = File(activeFilePath!!).name
                )
            }
        }
    }

    fun setSlideBackground(slideIndex: Int, colorHex: String) {
        val ppt = activePresentation as? XMLSlideShow ?: return
        val slides = ppt.slides
        if (slideIndex in slides.indices) {
            pushUndoState()
            val slide = slides[slideIndex]
            setSlideBgColorHex(slide, colorHex)
            viewModelScope.launch {
                _loadState.value = PptxLoadState.Success(
                    presentation = PptxPresentation(parseAllSlides(ppt)),
                    fileName = File(activeFilePath!!).name
                )
            }
        }
    }

    fun insertImageIntoSlide(slideIndex: Int, imagePath: String) {
        if (activePresentation is org.apache.poi.hslf.usermodel.HSLFSlideShow || activeFilePath?.endsWith(".ppt", ignoreCase = true) == true) {
            viewModelScope.launch {
                _saveStatus.emit("Editing is not supported for legacy PowerPoint (.ppt) documents. Please save as .pptx format to edit.")
            }
            return
        }
        val ppt = activePresentation as? XMLSlideShow ?: return
        val slides = ppt.slides
        if (slideIndex in slides.indices) {
            pushUndoState()
            val slide = slides[slideIndex]
            try {
                val imageBytes = File(imagePath).readBytes()
                val pictureData = ppt.addPicture(imageBytes, org.apache.poi.sl.usermodel.PictureData.PictureType.PNG)
                val pictureShape = slide.createPicture(pictureData)

                val sp = getXmlObjectReflection(pictureShape)
                if (sp != null) {
                    val spPr = try {
                        sp.javaClass.getMethod("getSpPr").invoke(sp)
                            ?: sp.javaClass.getMethod("addNewSpPr").invoke(sp)
                    } catch (t: Throwable) {
                        try { sp.javaClass.getMethod("addNewSpPr").invoke(sp) } catch (t2: Throwable) { null }
                    }
                    if (spPr != null) {
                        val xfrm = try {
                            spPr.javaClass.getMethod("getXfrm").invoke(spPr)
                                ?: spPr.javaClass.getMethod("addNewXfrm").invoke(spPr)
                        } catch (t: Throwable) {
                            try { spPr.javaClass.getMethod("addNewXfrm").invoke(spPr) } catch (t2: Throwable) { null }
                        }
                        if (xfrm != null) {
                            val off = try {
                                xfrm.javaClass.getMethod("getOff").invoke(xfrm)
                                    ?: xfrm.javaClass.getMethod("addNewOff").invoke(xfrm)
                            } catch (t: Throwable) {
                                try { xfrm.javaClass.getMethod("addNewOff").invoke(xfrm) } catch (t2: Throwable) { null }
                            }
                            if (off != null) {
                                off.javaClass.getMethod("setX", Long::class.javaPrimitiveType).invoke(off, 914400L * 1)
                                off.javaClass.getMethod("setY", Long::class.javaPrimitiveType).invoke(off, 914400L * 1)
                            }
                            val ext = try {
                                xfrm.javaClass.getMethod("getExt").invoke(xfrm)
                                    ?: xfrm.javaClass.getMethod("addNewExt").invoke(xfrm)
                            } catch (t: Throwable) {
                                try { xfrm.javaClass.getMethod("addNewExt").invoke(xfrm) } catch (t2: Throwable) { null }
                            }
                            if (ext != null) {
                                ext.javaClass.getMethod("setCx", Long::class.javaPrimitiveType).invoke(ext, 914400L * 5)
                                ext.javaClass.getMethod("setCy", Long::class.javaPrimitiveType).invoke(ext, 914400L * 4)
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }

            viewModelScope.launch {
                _loadState.value = PptxLoadState.Success(
                    presentation = PptxPresentation(parseAllSlides(ppt)),
                    fileName = File(activeFilePath!!).name
                )
            }
        }
    }

    fun addSlide(afterIndex: Int) {
        if (activePresentation is org.apache.poi.hslf.usermodel.HSLFSlideShow) {
            viewModelScope.launch {
                _saveStatus.emit("Editing is not supported for legacy PowerPoint (.ppt) documents. Please save as .pptx format to edit.")
            }
            return
        }
        val ppt = activePresentation as? XMLSlideShow ?: return
        val layoutsList = try {
            ppt.javaClass.getMethod("getSlideLayouts").invoke(ppt) as? List<*>
        } catch (t: Throwable) {
            null
        }
        val layout = layoutsList?.firstOrNull() ?: return
        pushUndoState()
        try {
            val newSlide = ppt.javaClass.getMethod("createSlide", layout.javaClass).invoke(ppt, layout)
            ppt.setSlideOrder(newSlide as? XSLFSlide, (afterIndex + 1).coerceIn(0, ppt.slides.size))
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        viewModelScope.launch {
            _loadState.value = PptxLoadState.Success(
                presentation = PptxPresentation(parseAllSlides(ppt)),
                fileName = File(activeFilePath!!).name
            )
        }
    }

    fun deleteSlide(index: Int) {
        if (activePresentation is org.apache.poi.hslf.usermodel.HSLFSlideShow) {
            viewModelScope.launch {
                _saveStatus.emit("Editing is not supported for legacy PowerPoint (.ppt) documents. Please save as .pptx format to edit.")
            }
            return
        }
        val ppt = activePresentation as? XMLSlideShow ?: return
        val slides = ppt.slides
        if (index in slides.indices) {
            pushUndoState()
            try {
                ppt.removeSlide(index)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
            viewModelScope.launch {
                _loadState.value = PptxLoadState.Success(
                    presentation = PptxPresentation(parseAllSlides(ppt)),
                    fileName = File(activeFilePath!!).name
                )
            }
        }
    }

    fun duplicateSlide(index: Int) {
        if (activePresentation is org.apache.poi.hslf.usermodel.HSLFSlideShow) {
            viewModelScope.launch {
                _saveStatus.emit("Editing is not supported for legacy PowerPoint (.ppt) documents. Please save as .pptx format to edit.")
            }
            return
        }
        val ppt = activePresentation as? XMLSlideShow ?: return
        val slides = ppt.slides
        if (index in slides.indices) {
            pushUndoState()
            try {
                val originalSlide = slides[index]
                val layout = originalSlide.slideLayout
                val newSlide = ppt.javaClass.getMethod("createSlide", layout.javaClass).invoke(ppt, layout) as XSLFSlide

                val originalBgColor = getSlideBgColorHex(originalSlide)
                if (originalBgColor != null) {
                    setSlideBgColorHex(newSlide, originalBgColor)
                }

                originalSlide.shapes.forEach { shape ->
                    try {
                        if (shape is XSLFTextShape) {
                            val newShape = newSlide.createTextBox()
                            newShape.clearText()
                            newShape.setText(shape.text)
                            // Preserve font formatting from original runs
                            val origParas = shape.textParagraphs
                            val newParas = newShape.textParagraphs
                            if (origParas.isNotEmpty() && newParas.isNotEmpty()) {
                                val origRuns = origParas[0].textRuns
                                val newRuns = newParas[0].textRuns
                                if (origRuns.isNotEmpty()) {
                                    newShape.clearText()
                                    val newPara = newShape.addNewTextParagraph()
                                    origRuns.forEach { run ->
                                        val newRun = newPara.addNewTextRun()
                                        newRun.setText(run.rawText)
                                        newRun.isBold = run.isBold
                                        newRun.isItalic = run.isItalic
                                        newRun.isUnderlined = run.isUnderlined
                                        setRunProperties(newRun, extractTextRunColorHex(run), run.fontSize.toFloat())
                                    }
                                }
                            }
                            // Preserve shape position and size
                            val xmlBounds = getXmlShapeBoundsNormalized(
                                shape,
                                getSlideDimensionsEmu(ppt).first,
                                getSlideDimensionsEmu(ppt).second
                            )
                            if (xmlBounds != null) {
                                val newSp = getXmlObjectReflection(newShape)
                                val newSpPr = try {
                                    newSp?.javaClass?.getMethod("getSpPr")?.invoke(newSp)
                                        ?: newSp?.javaClass?.getMethod("addNewSpPr")?.invoke(newSp)
                                } catch (t: Throwable) { null }
                                val newXfrm = try {
                                    newSpPr?.javaClass?.getMethod("getXfrm")?.invoke(newSpPr)
                                        ?: newSpPr?.javaClass?.getMethod("addNewXfrm")?.invoke(newSpPr)
                                } catch (t: Throwable) { null }
                                if (newXfrm != null) {
                                    val (slideW, slideH) = getSlideDimensionsEmu(ppt)
                                    val off = try {
                                        newXfrm.javaClass.getMethod("getOff").invoke(newXfrm)
                                            ?: newXfrm.javaClass.getMethod("addNewOff").invoke(newXfrm)
                                    } catch (t: Throwable) { null }
                                    val ext = try {
                                        newXfrm.javaClass.getMethod("getExt").invoke(newXfrm)
                                            ?: newXfrm.javaClass.getMethod("addNewExt").invoke(newXfrm)
                                    } catch (t: Throwable) { null }
                                    if (off != null) {
                                        off.javaClass.getMethod("setX", Long::class.javaPrimitiveType).invoke(off, (xmlBounds[0] * slideW).toLong())
                                        off.javaClass.getMethod("setY", Long::class.javaPrimitiveType).invoke(off, (xmlBounds[1] * slideH).toLong())
                                    }
                                    if (ext != null) {
                                        ext.javaClass.getMethod("setCx", Long::class.javaPrimitiveType).invoke(ext, (xmlBounds[2] * slideW).toLong())
                                        ext.javaClass.getMethod("setCy", Long::class.javaPrimitiveType).invoke(ext, (xmlBounds[3] * slideH).toLong())
                                    }
                                }
                            }
                        } else if (shape is org.apache.poi.sl.usermodel.PictureShape<*, *>) {
                            try {
                                val picData = shape.pictureData
                                val imageBytes = picData.data
                                if (imageBytes != null && imageBytes.isNotEmpty()) {
                                    val newPicData = ppt.addPicture(imageBytes, picData.type)
                                    val newPicShape = newSlide.createPicture(newPicData)
                                    val xmlBounds = getXmlShapeBoundsNormalized(
                                        shape,
                                        getSlideDimensionsEmu(ppt).first,
                                        getSlideDimensionsEmu(ppt).second
                                    )
                                    if (xmlBounds != null) {
                                        val newSp = getXmlObjectReflection(newPicShape)
                                        val newSpPr = try {
                                            newSp?.javaClass?.getMethod("getSpPr")?.invoke(newSp)
                                                ?: newSp?.javaClass?.getMethod("addNewSpPr")?.invoke(newSp)
                                        } catch (t: Throwable) { null }
                                        val newXfrm = try {
                                            newSpPr?.javaClass?.getMethod("getXfrm")?.invoke(newSpPr)
                                                ?: newSpPr?.javaClass?.getMethod("addNewXfrm")?.invoke(newSpPr)
                                        } catch (t: Throwable) { null }
                                        if (newXfrm != null) {
                                            val (slideW, slideH) = getSlideDimensionsEmu(ppt)
                                            val off = try {
                                                newXfrm.javaClass.getMethod("getOff").invoke(newXfrm)
                                                    ?: newXfrm.javaClass.getMethod("addNewOff").invoke(newXfrm)
                                            } catch (t: Throwable) { null }
                                            val ext = try {
                                                newXfrm.javaClass.getMethod("getExt").invoke(newXfrm)
                                                    ?: newXfrm.javaClass.getMethod("addNewExt").invoke(newXfrm)
                                            } catch (t: Throwable) { null }
                                            if (off != null) {
                                                off.javaClass.getMethod("setX", Long::class.javaPrimitiveType).invoke(off, (xmlBounds[0] * slideW).toLong())
                                                off.javaClass.getMethod("setY", Long::class.javaPrimitiveType).invoke(off, (xmlBounds[1] * slideH).toLong())
                                            }
                                            if (ext != null) {
                                                ext.javaClass.getMethod("setCx", Long::class.javaPrimitiveType).invoke(ext, (xmlBounds[2] * slideW).toLong())
                                                ext.javaClass.getMethod("setCy", Long::class.javaPrimitiveType).invoke(ext, (xmlBounds[3] * slideH).toLong())
                                            }
                                        }
                                    }
                                }
                            } catch (t: Throwable) { t.printStackTrace() }
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }

            viewModelScope.launch {
                _loadState.value = PptxLoadState.Success(
                    presentation = PptxPresentation(parseAllSlides(ppt)),
                    fileName = File(activeFilePath!!).name
                )
            }
        }
    }

    fun commitChanges() {
        viewModelScope.launch {
            if (activePresentation is org.apache.poi.hslf.usermodel.HSLFSlideShow) {
                _saveStatus.emit("Editing is not supported for legacy PowerPoint (.ppt) documents. Please save as .pptx format to edit.")
                return@launch
            }
            val ppt = activePresentation
            val filePath = activeFilePath
            if (ppt == null || filePath == null) {
                _saveStatus.emit("No active presentation loaded.")
                return@launch
            }

            withContext(Dispatchers.IO) {
                var fileOutputStream: FileOutputStream? = null
                try {
                    val file = File(filePath)
                    fileOutputStream = FileOutputStream(file)
                    ppt.write(fileOutputStream)
                    recentFileRepository.insertRecentFile(
                        com.karnadigital.omnisuite.core.model.RecentFile(
                            fileUri = android.net.Uri.fromFile(file).toString(),
                            fileName = file.name,
                            mimeType = "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                            fileSize = file.length(),
                            lastOpened = System.currentTimeMillis(),
                            isOperation = true
                        )
                    )
                    _saveStatus.emit("Presentation changes committed successfully!")
                } catch (t: Throwable) {
                    t.printStackTrace()
                    _saveStatus.emit("Failed to save changes: ${t.localizedMessage}")
                } finally {
                    try {
                        fileOutputStream?.close()
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _currentMatchIndex.value = -1
            return
        }
        // Search the already-loaded in-memory presentation instead of re-parsing the file.
        val presentation = (_loadState.value as? PptxLoadState.Success)?.presentation
        if (presentation == null) {
            _searchResults.value = emptyList()
            _currentMatchIndex.value = -1
            return
        }
        val results = PptxSearchEngine.search(presentation, query)
        _searchResults.value = results
        _currentMatchIndex.value = if (results.isNotEmpty()) 0 else -1
    }

    fun nextMatch() {
        val results = _searchResults.value
        if (results.isEmpty()) return
        val nextIndex = (_currentMatchIndex.value + 1) % results.size
        _currentMatchIndex.value = nextIndex
    }

    fun prevMatch() {
        val results = _searchResults.value
        if (results.isEmpty()) return
        val prevIndex = (_currentMatchIndex.value - 1 + results.size) % results.size
        _currentMatchIndex.value = prevIndex
    }

    override fun onCleared() {
        super.onCleared()
        try {
            activePresentation?.close()
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        // Clean up temporary image files
        tempImageCache.values.forEach { file ->
            try { if (file.exists()) file.delete() } catch (t: Throwable) {}
        }
        tempImageCache.clear()
    }
}